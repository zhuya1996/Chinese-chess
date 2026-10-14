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

    /** 最近一次失败原因，供诊断面板显示。 */
    private volatile String lastError;

    public String getLastError() {
        return lastError;
    }

    // ------------------------------------------------------------- 冻结帧
    // 标定必须在「象棋 App 在前台」时截到的画面上做。但标定界面本身是全屏
    // Activity，一旦它打开，再现场截屏只会截到它自己 —— 自己截自己，
    // 标定永远不可能成功。
    //
    // 所以这里常驻保存「最近一张来自其他 App 的画面」，标定界面直接取用。
    // 标定只关心棋盘几何，所以画面哪怕是几百毫秒前的也完全够用。

    /** 本 App 自己的界面是否在前台。为 true 时不采样冻结帧。 */
    private static volatile boolean uiForeground = true;

    public static void setUiForeground(boolean value) {
        uiForeground = value;
    }

    public static boolean isUiForeground() {
        return uiForeground;
    }

    private final Object frameLock = new Object();
    private int[] frozenArgb;
    private int frozenWidth;
    private int frozenHeight;
    private long frozenAt;

    /** 冻结帧是否已经可用（用于给用户提示该怎么标定）。 */
    public boolean hasFrozenFrame() {
        synchronized (frameLock) {
            return frozenArgb != null;
        }
    }

    /** 记录一帧「其他 App 在前台」时的画面。 */
    private void rememberFrame(int[] argb, int w, int h) {
        int n = w * h;
        synchronized (frameLock) {
            if (frozenArgb == null || frozenArgb.length != n) {
                frozenArgb = new int[n];
                frozenWidth = w;
                frozenHeight = h;
            }
            System.arraycopy(argb, 0, frozenArgb, 0, n);
            frozenAt = System.currentTimeMillis();
        }
    }

    /** 节流采样：最快每 500ms 更新一次，避免无谓的整屏内存拷贝。 */
    private void rememberFrameThrottled(ScreenCapture.Snapshot s) {
        if (uiForeground) {
            return;
        }
        synchronized (frameLock) {
            if (System.currentTimeMillis() - frozenAt < 500) {
                return;
            }
        }
        rememberFrame(s.argb, s.width, s.height);
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
        // v1.7：标定完成后，把展开面板的底边限制在棋盘上沿之上，
        // 防止面板盖住棋盘顶行导致幻影棋子（真机复现过死循环）。
        if (profile.isCalibrated() && overlay != null) {
            float[] c = profile.cornersRel;
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenH = dm.heightPixels;
            float top = Math.min(Math.min(c[1], c[3]), Math.min(c[5], c[7]));
            float bottom = Math.max(Math.max(c[1], c[3]), Math.max(c[5], c[7]));
            // v1.8：棋子以交点为圆心，半径约半个格高——面板必须让开
            // 「上沿 - 0.65 格」以上，否则仍会蹭掉顶行棋子的上半截
            float chPx = (bottom - top) / 9f * screenH;
            int boardTopPx = (int) (top * screenH - 0.65f * chPx);
            final int limit = boardTopPx;
            main.post(() -> {
                if (overlay != null) {
                    overlay.setPanelMaxBottom(limit);
                }
            });
        }
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

    /**
     * 给标定界面用的画面：返回最近一张「来自其他 App（即象棋 App）」的冻结帧。
     * <p>
     * 刻意不在这里现场截屏 —— 标定界面是全屏 Activity，现场截只会截到它自己，
     * 那样棋盘永远不会出现在标定画面上。
     *
     * @return null 表示还没有采到过象棋 App 的画面
     */
    public static Bitmap takeCalibrationFrame() {
        OverlayService s = instance;
        return s == null ? null : s.frozenBitmap();
    }

    private Bitmap frozenBitmap() {
        int[] copy;
        int w;
        int h;
        synchronized (frameLock) {
            if (frozenArgb == null || frozenWidth <= 0 || frozenHeight <= 0) {
                return null;
            }
            copy = frozenArgb;
            w = frozenWidth;
            h = frozenHeight;
        }
        try {
            // createBitmap 会复制数组内容，所以后续覆盖 frozenArgb 不影响它
            return Bitmap.createBitmap(copy, 0, w, w, h, Bitmap.Config.ARGB_8888);
        } catch (Throwable e) {
            return null;
        }
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
        // 顺序很重要：先把悬浮窗挂出来，再启动引擎。
        // 引擎起不来只是「暂时没有建议」，不该连累悬浮窗；
        // 而悬浮窗不显示，用户就彻底没得用了。
        showOverlay();
        startEngineQuietly();
        startLoop();
    }

    /**
     * 启动本地引擎，并吃掉所有异常。
     * <p>
     * 引擎是「功能」而不是「前提」：它失败时，截屏、标定、悬浮窗都应该照常可用，
     * 用户至少能先把棋盘标定好，再看清失败原因。
     */
    private void startEngineQuietly() {
        if (engine.isRunning()) {
            return;
        }
        if (!DeviceCompat.engineSupported()) {
            lastError = "引擎需要 Android 10 及以上，当前系统 "
                    + DeviceCompat.androidVersion() + " 无法运行";
            statusToast(lastError);
            return;
        }
        try {
            String engineErr = engine.start(this);
            if (engineErr != null) {
                lastError = "引擎启动失败：" + engineErr;
                statusToast(lastError);
            }
        } catch (Throwable t) {
            lastError = "引擎启动失败：" + describe(t);
            statusToast(lastError);
        }
    }

    private void showOverlay() {
        if (overlay == null) {
            overlay = new OverlayController(this, new OverlayController.Callback() {
                @Override
                public void onCalibrateClicked() {
                    startCalibration();
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

                @Override
                public void onCalibConfirm(float[] corners, boolean blackBottom) {
                    OverlayService.this.onCalibConfirm(corners, blackBottom);
                }

                @Override
                public void onCalibCancel() {
                    OverlayService.this.onCalibCancel();
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

    // ------------------------------------------------------------ 标定（悬浮窗内完成）
    // 标定不启动 Activity：从后台服务 startActivity 在 Android 10+ 受严格限制，
    // Android 16 + HyperOS 实测直接崩溃（v1.2 真机反馈）。标定层是悬浮窗的一部分。

    /** 标定期间为 true：主循环整段暂停，避免把标定层自己截进冻结帧。 */
    private volatile boolean calibrating;

    /** 当前标定用的冻结帧（与标定层显示的一致）。 */
    private volatile Bitmap calibFrame;

    /** 标定线程正在跑（防「确认」按钮重复点击）。 */
    private volatile boolean calibWorking;

    /** 上次自动识别棋盘的时间（限频用）。 */
    private volatile long lastAutoCalibAttempt;

    /** 连续严重误读（数量不合法/置信度过低）计数，用于自愈收起面板。 */
    private int misreadCount;

    /**
     * 自动识别棋盘并生成模板。在识别线程调用，识别失败静默返回
     * （面板会持续提示，用户也可以走手动标定）。
     */
    private void tryAutoCalibrate(ScreenCapture.Snapshot s) {
        try {
            float[] corners = BoardAutoDetect.detect(s.gray);
            if (corners == null) {
                return;
            }
            XiangqiRecognizer.Frame frame = new XiangqiRecognizer.Frame(
                    s.gray, s.argb, s.width, s.height, s.rowStride);
            String err = recognizer.calibrate(profile, frame, corners, null);
            if (err == null && profile.save(this)) {
                applyProfile();
                statusToast("已自动识别棋盘，无需手动标定。开始为你分析。");
            }
        } catch (Throwable t) {
            // 自动标定只是捷径，失败不影响手动路径
        }
    }

    /** 打开悬浮窗内的标定层。可在任意线程调用。 */
    public void startCalibration() {
        if (overlay == null || !overlay.isShown()) {
            statusToast("悬浮窗未显示，无法标定。请先重启辅助。");
            return;
        }
        if (calibrating) {
            return; // 已在标定中
        }
        Bitmap frame = frozenBitmap();
        if (frame == null) {
            statusToast("还没有采到象棋 App 的画面：请切到象棋 App 打开一盘棋，"
                    + "让棋盘完整显示两三秒，再点「标定」。");
            return;
        }
        calibFrame = frame;
        float[] corners = profile.isCalibrated() ? profile.cornersRel : null;
        boolean blackBottom = "black".equals(profile.bottomSide);
        final Bitmap bmp = frame;
        main.post(() -> {
            if (overlay == null) {
                return;
            }
            boolean ok = overlay.showCalibration(bmp, corners, blackBottom);
            // 成功 → 暂停主循环（标定层盖着屏幕，不能采样）；
            // 失败 → 复位，否则主循环会被永久挂起。
            calibrating = ok;
            if (!ok) {
                calibFrame = null;
            }
        });
    }

    private void onCalibConfirm(float[] corners, boolean blackBottom) {
        if (calibWorking) {
            return;
        }
        final Bitmap bmp = calibFrame;
        final float[] pts = corners;
        final String hint = blackBottom ? "black" : "red";
        if (bmp == null || pts == null || pts.length < 8) {
            main.post(() -> {
                if (overlay != null) {
                    overlay.hideCalibration();
                }
                calibFrame = null;
                calibrating = false;
            });
            return;
        }
        calibWorking = true;
        if (overlay != null) {
            overlay.setCalibStatus("正在生成识别模板，请稍候…");
        }
        new Thread(() -> {
            String error;
            try {
                XiangqiRecognizer rec = new XiangqiRecognizer();
                error = rec.calibrate(profile,
                        XiangqiRecognizer.fromBitmap(bmp), pts, hint);
            } catch (Throwable t) {
                error = "识别模板生成失败：" + t.getClass().getSimpleName()
                        + (t.getMessage() == null ? "" : " " + t.getMessage());
            }
            final String err = error;
            final boolean ok = err == null && profile.save(this);
            final String msg = ok ? null
                    : (err != null ? err : "保存标定文件失败");
            main.post(() -> {
                calibWorking = false;
                if (ok) {
                    applyProfile();
                    calibFrame = null;
                    calibrating = false;
                    if (overlay != null) {
                        overlay.hideCalibration();
                    }
                    statusToast("标定成功，已保存模板");
                } else {
                    // 标定层还开着，保持 calibrating=true（继续暂停采样，
                    // 否则会把标定层自己截进冻结帧）。用户可调整角点重试。
                    if (overlay != null) {
                        overlay.setCalibStatus("标定失败：" + msg
                                + "\n可调整角点后重试，或点「取消」退出。");
                    }
                }
            });
        }, "calibrate").start();
    }

    private void onCalibCancel() {
        calibFrame = null;
        calibrating = false;
        main.post(() -> {
            if (overlay != null) {
                overlay.hideCalibration();
            }
        });
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
                if (calibrating) {
                    // 标定层正盖在屏幕上：此时截屏只会截到标定层自己，
                    // 既污染冻结帧又没有识别价值，整段跳过。
                    Thread.sleep(400);
                    continue;
                }
                if (uiForeground) {
                    // 本 App 自己的界面在前台：截到的会是它自己，既没有识别价值，
                    // 也会污染标定时要用的冻结帧，所以整段跳过。
                    Thread.sleep(500);
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
                // 无论有没有标定，都要留存画面：标定本身就需要这张冻结帧。
                rememberFrameThrottled(s);

                if (!profile.isCalibrated()) {
                    // 优先自动识别棋盘（限频 2.5 秒一次，失败就持续重试，
                    // 直到用户真的摆出初始局面为止）。识别成功的前提是
                    // 「初始局面」校验通过，所以不会在残局上乱建模板。
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - lastAutoCalibAttempt > 2500) {
                        lastAutoCalibAttempt = now;
                        tryAutoCalibrate(s);
                    }
                    if (!profile.isCalibrated()) {
                        updatePanel("正在自动识别棋盘：请开一局新的对局（摆好初始棋子），"
                                + "识别成功会自动提示。也可点面板「标定」手动框选。");
                        Thread.sleep(800);
                    }
                    continue;
                }

                XiangqiRecognizer.Frame frame = new XiangqiRecognizer.Frame(
                        s.gray, s.argb, s.width, s.height, s.rowStride);
                XiangqiRecognizer.Result r = recognizer.recognize(frame);

                if (!r.accepted) {
                    // v1.7 自愈：严重误读（数量不合法/置信度过低）多半是
                    // 自己的面板或箭头挡住了棋盘 —— 清箭头，连续多次则收起面板。
                    if (r.seriousMisread) {
                        misreadCount++;
                        final boolean arrowShown = overlay != null && overlay.isArrowShown();
                        if (arrowShown) {
                            main.post(() -> {
                                if (overlay != null) {
                                    overlay.clearArrow();
                                }
                            });
                        }
                        if (misreadCount >= 8 && overlay != null && overlay.isPanelExpanded()) {
                            misreadCount = 0;
                            main.post(() -> {
                                if (overlay != null) {
                                    overlay.collapsePanel();
                                }
                            });
                            statusToast("面板可能挡住了棋盘，已自动收起；点「展开」可随时查看建议");
                        }
                    } else {
                        misreadCount = 0;
                    }
                    updatePanel(r.rejectReason);
                    Thread.sleep(250);
                    continue;
                }
                misreadCount = 0;

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
