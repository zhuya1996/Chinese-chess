package com.xiangqi.overlay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 主界面。
 * <p>
 * 界面设计上的两条教训（都是真机反馈后改的）：
 * <ul>
 *   <li><b>按钮绝不用「检测结果」来禁用。</b> 国产 ROM 上权限检测会误报，
 *       一旦据此禁用按钮，用户就会遇到「明明授权了却点不动、也不知道为什么」。</li>
 *   <li><b>不把操作顺序甩给用户。</b> 标定本来必须先启动服务，现在点「标定」
 *       会自动先把服务拉起来，用户不需要理解这个先后关系。</li>
 * </ul>
 */
public final class MainActivity extends Activity {

    private static final int REQ_PROJECTION = 1001;
    private static final int REQ_NOTIF = 1003;

    private Button btnOverlay;
    private Button btnNotif;
    private Button btnBattery;
    private Button btnCalibrate;
    private Button btnStart;
    private Button btnStop;
    private Button btnDiag;
    private TextView tvRuntime;
    private TextView tvAbiWarning;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = this::refresh;
    private boolean showing;

    /** 用户点「标定」但服务还没起来时置位，服务起来后给出引导提示。 */
    private boolean pendingCalibration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnOverlay = findViewById(R.id.btnOverlay);
        btnNotif = findViewById(R.id.btnNotif);
        btnBattery = findViewById(R.id.btnBattery);
        btnCalibrate = findViewById(R.id.btnCalibrate);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnDiag = findViewById(R.id.btnDiag);
        tvRuntime = findViewById(R.id.tvRuntime);
        tvAbiWarning = findViewById(R.id.tvAbiWarning);

        // 权限按钮始终可点：即使检测显示已开启，也允许再进设置页确认一次。
        btnOverlay.setOnClickListener(v -> {
            if (DeviceCompat.canDrawOverlays(this)) {
                toast("检测显示悬浮窗权限已开启。若悬浮窗仍不出现，"
                        + "请检查系统的「后台弹出界面」开关（小米/红米需要）。");
            }
            DeviceCompat.openOverlaySettings(this);
        });

        btnNotif.setOnClickListener(v -> requestNotificationPermission());
        btnBattery.setOnClickListener(v ->
                DeviceCompat.requestIgnoreBatteryOptimizations(this));

        btnCalibrate.setOnClickListener(v -> calibrate());
        btnStart.setOnClickListener(v -> startAssist());
        btnStop.setOnClickListener(v -> {
            OverlayService s = OverlayService.get();
            if (s != null) {
                s.stopAssist();
            }
            refresh();
        });
        btnDiag.setOnClickListener(v -> showDiagnostics());

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showing = true;
        OverlayService.setUiForeground(true);
        refresh();
    }

    @Override
    protected void onPause() {
        OverlayService.setUiForeground(false);
        super.onPause();
        showing = false;
        main.removeCallbacks(refreshTask);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PROJECTION) {
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            pendingCalibration = false;
            toast("未授权录屏，无法识别棋局。悬浮窗辅助需要录屏权限才能读取棋盘。");
            return;
        }
        Intent svc = new Intent(this, OverlayService.class);
        svc.putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(OverlayService.EXTRA_DATA, data);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Throwable t) {
            toast("启动后台服务失败：" + t.getMessage());
            pendingCalibration = false;
            return;
        }
        if (pendingCalibration) {
            pendingCalibration = false;
            toast("辅助已启动。请切到象棋 App 打开一盘棋、让棋盘显示两三秒，"
                    + "再点悬浮窗面板上的「标定」按钮。");
        } else {
            toast(getString(R.string.toast_started));
        }
        refresh();
    }

    // ---------------------------------------------------------------- 启动辅助

    private void startAssist() {
        if (!DeviceCompat.engineSupported()) {
            new AlertDialog.Builder(this)
                    .setTitle("当前系统暂不支持")
                    .setMessage("本机是 " + DeviceCompat.androidVersion() + "。\n\n"
                            + "随 App 打包的象棋引擎要求 Android 10 及以上，"
                            + "所以这台手机暂时跑不起来。\n\n"
                            + "App 本身支持更老的系统，只要之后重新编译一版引擎即可。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        if (!DeviceCompat.canDrawOverlays(this)) {
            // 注意：这里只是「提示」，不是「拦截」。
            // 检测本身可能不准，所以给用户一个「仍然继续」的出口。
            new AlertDialog.Builder(this)
                    .setTitle("悬浮窗权限可能未开启")
                    .setMessage("检测结果说悬浮窗权限未开启。\n\n"
                            + DeviceCompat.overlayHint()
                            + "\n\n如果你确定已经开启，可以直接选「仍然继续」——"
                            + "能不能显示以实际结果为准。")
                    .setPositiveButton("去开启", (d, w) -> DeviceCompat.openOverlaySettings(this))
                    .setNegativeButton("仍然继续", (d, w) -> requestProjection())
                    .setNeutralButton("取消", null)
                    .show();
            return;
        }
        requestProjection();
    }

    private void requestProjection() {
        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mgr == null) {
            toast("系统不支持录屏，无法识别棋局");
            return;
        }
        try {
            startActivityForResult(mgr.createScreenCaptureIntent(), REQ_PROJECTION);
        } catch (Throwable t) {
            toast("无法打开录屏授权窗口：" + t.getMessage());
        }
    }

    // ---------------------------------------------------------------- 标定

    private void calibrate() {
        OverlayService s = OverlayService.get();
        if (s != null && s.isActive() && s.isCaptureRunning()) {
            // 标定层是悬浮窗的一部分（不启动 Activity），入口在悬浮窗面板上。
            toast("请在象棋 App 的棋盘界面停留两三秒，然后点悬浮窗面板上的「标定」按钮。");
            return;
        }
        // 服务还没跑起来：自动把服务拉起来，起来后引导去悬浮窗点「标定」。
        pendingCalibration = true;
        toast("需要先获得一次录屏授权，请在系统窗口里点「开始录制」");
        startAssist();
    }

    // ---------------------------------------------------------------- 诊断

    private void showDiagnostics() {
        StringBuilder sb = new StringBuilder(DeviceCompat.diagnostics(this));
        String crash = CrashLogger.lastCrash();
        if (crash != null) {
            sb.append("\n\n== 最近一次崩溃（反馈时请务必带上）==\n");
            sb.append(crash);
        } else {
            sb.append("\n\n最近崩溃：无");
        }
        final String text = sb.toString();
        new AlertDialog.Builder(this)
                .setTitle("诊断信息")
                .setMessage(text)
                .setPositiveButton("复制", (d, w) -> copyToClipboard(text))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("诊断信息", text));
                toast("已复制，可直接粘贴发给开发者");
            }
        } catch (Throwable t) {
            toast("复制失败：" + t.getMessage());
        }
    }

    // ---------------------------------------------------------------- 状态刷新

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            toast("此系统版本无需通知权限");
            return;
        }
        if (DeviceCompat.hasNotificationPermission(this)) {
            toast("通知权限已开启");
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
    }

    private void refresh() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        boolean overlayOk = DeviceCompat.canDrawOverlays(this);
        btnOverlay.setText(overlayOk ? R.string.action_granted : R.string.action_grant);
        btnOverlay.setEnabled(true);

        boolean notifOk = DeviceCompat.hasNotificationPermission(this);
        btnNotif.setText(notifOk ? R.string.action_granted : R.string.action_grant);
        btnNotif.setEnabled(true);

        boolean batteryOk = DeviceCompat.ignoringBatteryOptimizations(this);
        btnBattery.setText(batteryOk ? R.string.action_granted : R.string.action_grant);
        btnBattery.setEnabled(true);

        boolean engineOk = DeviceCompat.engineSupported();
        boolean abiOk = !DeviceCompat.is32BitOnly() && engineOk;
        tvAbiWarning.setVisibility(abiOk ? TextView.GONE : TextView.VISIBLE);
        if (!abiOk) {
            tvAbiWarning.setText(DeviceCompat.is32BitOnly()
                    ? getString(R.string.abort_abi)
                    : "此系统（" + DeviceCompat.androidVersion() + "）低于引擎要求的最低版本"
                            + "（Android 10）。App 功能正常，但引擎无法启动；"
                            + "需要用 NDK 重新编译一版引擎才能在这台手机上使用。");
        }

        OverlayService s = OverlayService.get();
        boolean active = s != null && s.isActive();
        // 按钮始终可点，避免「点不动又不知道为什么」。
        btnStart.setEnabled(engineOk && !active);
        btnStop.setEnabled(active);

        StringBuilder sb = new StringBuilder();
        if (!active) {
            sb.append("未启动。\n");
            sb.append("步骤：① 开悬浮窗权限 → ② 点「启动悬浮窗辅助」→ "
                    + "③ 切到象棋 App 打开一盘棋 → ④ 点悬浮窗上的「标定」框住棋盘四角 → ⑤ 开始下棋。");
            sb.append("\n\n提示：直接点「标定棋盘四角」也可以，会自动先帮你启动辅助。");
        } else {
            sb.append("运行中。\n");
            sb.append("· 截屏：").append(s.isCaptureRunning() ? "正常" : "未运行");
            sb.append("\n· 引擎：").append(s.isEngineReady() ? "就绪" : "未启动");
            sb.append("\n· 标定：").append(s.isCalibrated() ? "已完成" : "尚未完成");
            sb.append("\n· 悬浮窗：").append(s.isOverlayShown() ? "已显示" : "未显示");
            if (!s.isOverlayShown()) {
                sb.append("  ← 请点「诊断信息」查看原因");
            }
            String err = s.getLastError();
            if (err != null && err.length() > 0) {
                sb.append("\n\n最近提示：").append(err);
            }
            sb.append("\n\n").append(DeviceCompat.keepAliveHint());
        }
        tvRuntime.setText(sb.toString());

        main.removeCallbacks(refreshTask);
        if (showing) {
            main.postDelayed(refreshTask, 1000);
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
