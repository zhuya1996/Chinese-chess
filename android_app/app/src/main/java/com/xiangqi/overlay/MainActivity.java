package com.xiangqi.overlay;

import android.Manifest;
import android.app.Activity;
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
 * 主界面：按「① 开权限 → ② 标定 → ③ 启动」的顺序引导用户完成配置。
 */
public final class MainActivity extends Activity {

    private static final int REQ_PROJECTION = 1001;
    private static final int REQ_OVERLAY = 1002;
    private static final int REQ_NOTIF = 1003;
    private static final int REQ_BATTERY = 1004;

    private Button btnOverlay;
    private Button btnNotif;
    private Button btnBattery;
    private Button btnCalibrate;
    private Button btnStart;
    private Button btnStop;
    private TextView tvRuntime;
    private TextView tvAbiWarning;
    private TextView tvSetupHint;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = this::refresh;
    private boolean running;

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
        tvRuntime = findViewById(R.id.tvRuntime);
        tvAbiWarning = findViewById(R.id.tvAbiWarning);
        tvSetupHint = findViewById(R.id.tvSetupHint);

        btnOverlay.setOnClickListener(v -> {
            if (DeviceCompat.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivityForResult(DeviceCompat.overlaySettingsIntent(this), REQ_OVERLAY);
        });
        btnNotif.setOnClickListener(v -> requestNotificationPermission());
        btnBattery.setOnClickListener(v -> {
            Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(i, REQ_BATTERY);
            } catch (Exception e) {
                startActivity(DeviceCompat.appDetailsIntent(this));
            }
        });

        btnCalibrate.setOnClickListener(v -> {
            OverlayService s = OverlayService.get();
            if (s == null || !s.isActive()) {
                Toast.makeText(this, "请先点「启动悬浮窗辅助」，授权录屏后才能截图标定",
                        Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, CalibrationActivity.class));
        });

        btnStart.setOnClickListener(v -> requestProjectionAndStart());
        btnStop.setOnClickListener(v -> {
            OverlayService s = OverlayService.get();
            if (s != null) {
                s.stopAssist();
            }
            refresh();
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        main.postDelayed(refreshTask, 500);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        main.removeCallbacks(refreshTask);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROJECTION) {
            if (resultCode != RESULT_OK || data == null) {
                Toast.makeText(this, "未授权录屏，无法识别棋局", Toast.LENGTH_LONG).show();
                return;
            }
            Intent svc = new Intent(this, OverlayService.class);
            svc.putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode);
            svc.putExtra(OverlayService.EXTRA_DATA, data);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
            Toast.makeText(this, R.string.toast_started, Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void requestProjectionAndStart() {
        if (DeviceCompat.is32BitOnly()) {
            Toast.makeText(this, R.string.abort_abi, Toast.LENGTH_LONG).show();
            return;
        }
        if (!DeviceCompat.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_need_overlay, Toast.LENGTH_LONG).show();
            startActivityForResult(DeviceCompat.overlaySettingsIntent(this), REQ_OVERLAY);
            return;
        }
        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mgr == null) {
            Toast.makeText(this, "系统不支持录屏", Toast.LENGTH_LONG).show();
            return;
        }
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "此系统版本无需通知权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "通知权限已开启", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
    }

    // ------------------------------------------------------------ 状态刷新

    private void refresh() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        boolean overlayOk = DeviceCompat.canDrawOverlays(this);
        btnOverlay.setText(overlayOk ? R.string.action_granted : R.string.action_grant);
        btnOverlay.setEnabled(!overlayOk);

        boolean notifOk = hasNotificationPermission();
        btnNotif.setText(notifOk ? R.string.action_granted : R.string.action_grant);
        btnNotif.setEnabled(!notifOk);

        boolean batteryOk = DeviceCompat.ignoringBatteryOptimizations(this);
        btnBattery.setText(batteryOk ? R.string.action_granted : R.string.action_grant);
        btnBattery.setEnabled(!batteryOk);

        boolean abiOk = !DeviceCompat.is32BitOnly() && DeviceCompat.engineSupported();
        tvAbiWarning.setVisibility(abiOk ? TextView.GONE : TextView.VISIBLE);
        if (!abiOk) {
            tvAbiWarning.setText(DeviceCompat.is32BitOnly()
                    ? getString(R.string.abort_abi)
                    : "当前引擎只支持 Android 10 及以上，你的系统是 "
                            + DeviceCompat.androidVersion()
                            + "。App 代码本身支持更老系统，需用 NDK 重编引擎后才能运行。");
        }

        OverlayService s = OverlayService.get();
        boolean active = s != null && s.isActive();
        btnStart.setEnabled(abiOk && overlayOk && !active);
        btnStop.setEnabled(active);

        String runtime;
        if (!active) {
            runtime = getString(R.string.status_idle);
        } else {
            String engine = s.isEngineReady() ? "引擎就绪" : "引擎未启动";
            String calib = s.isCalibrated() ? "已标定" : "未标定（请点「标定棋盘四角」）";
            runtime = "状态：" + s.getStatusText() + "\n" + engine + " · " + calib
                    + "\n\n后台保活提示：" + DeviceCompat.keepAliveHint();
        }
        tvRuntime.setText(runtime);

        tvSetupHint.setText(getString(R.string.setup_hint));
        main.removeCallbacks(refreshTask);
        if (running) {
            main.postDelayed(refreshTask, 1000);
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
