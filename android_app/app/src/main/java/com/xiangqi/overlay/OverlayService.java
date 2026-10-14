package com.xiangqi.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import java.util.List;

/**
 * 核心前台服务：串起 截屏 → 识别 → 引擎 → 悬浮窗 的完整循环。
 * <p>
 * 必须是前台服务，原因有两个：
 * 1. Android 14 起强制要求：MediaProjection 只能在前台服务（类型 mediaProjection）里使用
 * 2. 后台服务随时会被系统回收，识别就会中断
 */
public final class OverlayService extends Service {

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_DATA = "data";

    private static final String CHANNEL_ID = "assist";
    private static final int NOTIF_ID = 1001;

    private static volatile OverlayService instance;

    public static OverlayService get() {
        return instance;
    }

    private ScreenCapture capture;
    private final BoardProfile profile = new BoardProfile();
    private final XiangqiRecognizer recognizer = new XiangqiRecognizer();
    private final PikafishEngine engine = new PikafishEngine();
    private OverlayController overlay;
    private Thread worker;
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile boolean active;
    private volatile boolean paused;

    /** 先浅后深：先快速给出一个能用的着法，再后台加深。 */
    private volatile int quickDepth = 10;
    private volatile int deepDepth = 16;
    private volatile String analyzingFen;
    private volatile boolean deepened;
    private volatile Position analyzingPosition;
    private volatile String analyzingOpponent = "";

    /** 给标定界面用的最新截图。 */
    private volatile Bitmap calibFrame;

    /** 最近一次失败原因，供诊断面板显示。 */
    private volatile String lastError;

    public String getLastError() {
        return lastError;
    }

    /** 截屏管线是否真的在跑（区别于「服务对象存在」）。 */
    public boolean isCaptureRunning() {
        return capture != null && capture.isRunning();
    }

    /** 悬浮窗是否真的挂到屏幕上了。 */
    public boolean isOverlayShown() {
        return overlay != null && overlay.isShown();
    }

    public boolean isCalibrated() {
        return profile.isCalibrated();
    }

    public BoardProfile getProfile() {
        return profile;
    }

    /** 标定完成后重新加载档案（模板、四角、我方颜色）。 */
    public void applyProfile() {
        recognizer.setProfile(profile);
    }

    public boolean isEngineReady() {
        return engine.isRunning();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getStatusText() {
        return recognizer.lastRejectReason;
    }

    /** 给标定界面截一帧。服务未运行时返回 null。 */
    public static Bitmap takeCalibrationFrame() {
        OverlayService s = instance;
        if (s == null || s.capture == null || !s.capture.isRunning()) {
            return null;
        }
        ScreenCapture.Snapshot snap = s.capture.grab(null);
        return snap == null ? null : snap.bitmap;
    }

    // ------------------------------------------------------------ 生命周期

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        profile.load(this);
        if (profile.isCalibrated()) {
            recognizer.setProfile(profile);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 系统把服务拉起来但没带录屏授权（START_STICKY 重启的情形）时，
        // 这个实例其实什么都做不了。必须主动退出，否则会留下一个
        // "对象存在、状态却不是运行中" 的幽灵实例，让主界面误判成已启动。
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_CODE)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForegroundCompat();
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            beginCapture(code, data);
        } catch (Throwable t) {
            fail("服务启动失败：" + describe(t));
        }
        return START_NOT_STICKY;
    }

    /** 统一的失败出口：记录原因、提示用户、并把服务关掉。 */
    private void fail(String reason) {
        lastError = reason;
        statusToast(reason);
        stopSelf();
    }

    private static String describe(Throwable t) {
        String msg = t.getMessage();
        String name = t.getClass().getSimpleName();
        return msg == null ? name : name + "：" + msg;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        active = false;
        paused = false;
        if (overlay != null) {
            main.post(overlay::hide);
        }
        engine.shutdown();
        if (capture != null) {
            capture.stop();
            capture = null;
        }
        instance = null;
        super.onDestroy();
    }

    // ------------------------------------------------------------ 启动流程

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification notification;
        if (Build.VERSION.SDK_INT >= 26) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.notif_title))
                    .setContentText(getString(R.string.notif_text))
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setOngoing(true)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle(getString(R.string.notif_title))
                    .setContentText(getString(R.string.notif_text))
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setOngoing(true)
                    .build();
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void beginCapture(int resultCode, Intent data) {
        if (capture == null) {
            capture = new ScreenCapture();
        }
        String err = capture.start(this, resultCode, data);
        if (err != null) {
            fail(err);
            return;
        }
        if (!engine.isRunning()) {
            if (!DeviceCompat.engineSupported()) {
                lastError = "引擎需要 Android 10 及以上，当前系统 "
                        + DeviceCompat.androidVersion() + " 无法运行";
                statusToast(lastError);
            } else {
                String engineErr = engine.start(this);
                if (engineErr != null) {
                    lastError = "引擎启动失败：" + engineErr;
                    statusToast(lastError);
                }
            }
        }
        showOverlay();
        startLoop();
    }

    private void showOverlay() {
        if (overlay == null) {
            overlay = new OverlayController(this, new OverlayController.Callback() {
                @Override
                public void onCalibrateClicked() {
                    openCalibration();
                }

                @Override
                public void onPauseClicked() {
                    paused = !paused;
                    overlay.setPaused(paused);
                }

                @Override
                public void onCloseClicked() {
                    stopAssist();
                }
            });
        }
        if (!overlay.isShown()) {
            String err = overlay.show();
            if (err != null) {
                // 悬浮窗没挂上：这不是致命错误（截屏和识别仍在跑），
                // 但用户什么都看不到，所以必须明确告知并记入诊断。
                lastError = err;
                statusToast(err);
            }
        }
    }

    public void stopAssist() {
        active = false;
        stopForeground(true);
        stopSelf();
    }

    private void openCalibration() {
        Intent i = new Intent(this, CalibrationActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void statusToast(String text) {
        main.post(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show());
    }

    // ------------------------------------------------------------ 主循环

    private void startLoop() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        active = true;
        worker = new Thread(this::loop, "recognize-loop");
        worker.setDaemon(true);
        worker.start();
    }

    private void loop() {
        ScreenCapture.Snapshot snapshot = null;
        while (active) {
            try {
                if (paused) {
                    Thread.sleep(400);
                    continue;
                }
                if (capture == null || !capture.isRunning()) {
                    updatePanel("录屏已停止，请重新启动辅助");
                    Thread.sleep(1200);
                    continue;
                }
                ScreenCapture.Snapshot s = capture.grab(snapshot);
                if (s == null) {
                    Thread.sleep(150);
                    continue;
                }
                snapshot = s;

                if (s.black) {
                    updatePanel("截到黑屏：对方应用可能开启了防截屏，或录屏授权不完整");
                    Thread.sleep(1000);
                    continue;
                }
                if (!profile.isCalibrated()) {
                    updatePanel("请先点「标定」框住棋盘四角");
                    Thread.sleep(700);
                    continue;
                }

                calibFrame = s.bitmap;
                XiangqiRecognizer.Frame frame = new XiangqiRecognizer.Frame(
                        s.gray, s.argb, s.width, s.height, s.rowStride);
                XiangqiRecognizer.Result r = recognizer.recognize(frame);

                if (!r.accepted) {
                    updatePanel(r.rejectReason);
                    Thread.sleep(250);
                    continue;
                }

                if (isMyTurn(r)) {
                    analyze(r);
                } else {
                    final String opp = r.lastMoveText;
                    main.post(() -> {
                        if (overlay != null) {
                            overlay.update("对手走棋中，稍等…", opp, "等待对手落子", "", "");
                            overlay.clearArrow();
                        }
                    });
                }
                Thread.sleep(250);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                updatePanel("识别出错：" + e.getMessage());
                try {
                    Thread.sleep(900);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    private void updatePanel(final String status) {
        main.post(() -> {
            if (overlay != null) {
                overlay.update(status, null, "—", "", "");
            }
        });
    }

    /** 我方在屏幕下方，识别结果的 mySide 即我方颜色。 */
    private static boolean isMyTurn(XiangqiRecognizer.Result r) {
        String mine = "red".equals(r.mySide) ? "w" : "b";
        return mine.equals(r.sideToMove);
    }

    // ------------------------------------------------------------ 引擎分析

    private void analyze(XiangqiRecognizer.Result r) {
        if (!engine.isRunning()) {
            updatePanel("引擎未就绪");
            return;
        }
        boolean isNewPosition = !r.fen.equals(analyzingFen);
        if (isNewPosition) {
            analyzingFen = r.fen;
            analyzingPosition = r.position;
            analyzingOpponent = r.lastMoveText;
            deepened = false;
        }
        if (engine.isSearching()) {
            return;   // 已经在算这个局面，等它自己出结果
        }
        engine.analyze(r.fen, deepened ? deepDepth : quickDepth, 3, listener);
    }

    private final PikafishEngine.Listener listener = new PikafishEngine.Listener() {
        @Override
        public void onCandidates(List<PikafishEngine.Candidate> candidates, int depth) {
            if (candidates == null || candidates.isEmpty()) {
                return;
            }
            Position pos = analyzingPosition;
            String opp = analyzingOpponent;
            if (pos != null) {
                for (PikafishEngine.Candidate c : candidates) {
                    if (c.uci != null && c.uci.length() >= 4) {
                        c.moveText = MoveTranslator.toChinese(pos, c.uci);
                    }
                }
            }
            PikafishEngine.Candidate best = candidates.get(0);
            final String suggest = best.moveText != null && best.moveText.length() > 0
                    ? best.moveText : best.uci;
            final String score = formatScore(best) + "　深度 " + depth;
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < candidates.size() && i < 3; i++) {
                PikafishEngine.Candidate c = candidates.get(i);
                if (i > 0) {
                    sb.append("　");
                }
                sb.append(i + 1).append(". ").append(c.moveText);
            }
            final String candText = sb.toString();
            final PikafishEngine.Candidate arrowSource = best;
            main.post(() -> {
                if (overlay != null) {
                    overlay.update("轮到你走", opp, suggest, score, candText);
                    drawArrow(arrowSource);
                }
            });
            // 先浅后深：浅层结果出来后，再算一层更深的
            if (!deepened && depth >= quickDepth) {
                deepened = true;
                main.post(() -> {
                    String fen = analyzingFen;
                    if (fen != null && !engine.isSearching()) {
                        engine.analyze(fen, deepDepth, 3, this);
                    }
                });
            }
        }

        @Override
        public void onError(String message) {
            updatePanel(message);
        }
    };

    private static String formatScore(PikafishEngine.Candidate c) {
        if (c.mate) {
            if (c.mateIn == 0) {
                return "绝杀";
            }
            return c.mateIn > 0 ? "我方 " + Math.abs(c.mateIn) + " 步杀"
                    : "对方 " + Math.abs(c.mateIn) + " 步杀";
        }
        int cp = c.scoreCp;
        if (cp > 0) {
            return "我方优 +" + (cp / 100.0);
        }
        if (cp < 0) {
            return "对方优 " + (cp / 100.0);
        }
        return "均势";
    }

    private void drawArrow(PikafishEngine.Candidate best) {
        if (best == null || best.uci == null || best.uci.length() < 4 || overlay == null) {
            return;
        }
        int fromCol = best.uci.charAt(0) - 'a';
        int fromRow = 9 - (best.uci.charAt(1) - '0');
        int toCol = best.uci.charAt(2) - 'a';
        int toRow = 9 - (best.uci.charAt(3) - '0');
        float[] a = recognizer.screenPointOf(fromRow, fromCol);
        float[] b = recognizer.screenPointOf(toRow, toCol);
        if (a == null || b == null) {
            overlay.clearArrow();
            return;
        }
        overlay.showArrow(a[0], a[1], b[0], b[1], "");
    }
}
