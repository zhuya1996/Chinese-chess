package com.xiangqi.overlay;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 四角标定界面。
 * <p>
 * 流程：先在象棋 App 里进入对局、摆好标准初始局面 → 回到本 App 点「标定」
 * → 这里会显示刚截到的画面 → 拖动 4 个角点框住棋盘最外圈的 4 个交叉点
 * → 确认。系统会自动校验画面确实是初始局面，并据此生成识别模板。
 */
public final class CalibrationActivity extends Activity {

    private CalibrationView calibView;
    private TextView tvStatus;
    private Button btnSide;
    private Button btnRecapture;
    private Button btnConfirm;

    private boolean blackBottom = false;
    private Bitmap frame;
    private boolean working;
    private int retries;

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        calibView = findViewById(R.id.calibView);
        tvStatus = findViewById(R.id.tvCalibStatus);
        btnSide = findViewById(R.id.btnSide);
        btnRecapture = findViewById(R.id.btnRecapture);
        btnConfirm = findViewById(R.id.btnConfirmCalib);

        btnSide.setOnClickListener(v -> {
            blackBottom = !blackBottom;
            updateSideText();
        });
        btnRecapture.setOnClickListener(v -> {
            retries = 0;
            loadFrame();
        });
        btnConfirm.setOnClickListener(v -> doCalibrate());

        updateSideText();
        loadFrame();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void updateSideText() {
        btnSide.setText(blackBottom
                ? R.string.calib_bottom_black
                : R.string.calib_bottom_red);
    }

    /**
     * 取一帧画面。
     * <p>
     * 录屏管线刚建好时 ImageReader 往往还没有可用帧，所以这里必须重试，
     * 否则用户一进标定界面就看到「没有画面」，会误以为服务没启动。
     */
    private void loadFrame() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Bitmap bmp = OverlayService.takeCalibrationFrame();
        if (bmp == null) {
            if (retries++ < 12) {
                tvStatus.setText("正在等待截屏画面…（第 " + retries + " 次）");
                main.postDelayed(this::loadFrame, 400);
                return;
            }
            OverlayService s = OverlayService.get();
            String why = (s == null)
                    ? "后台服务没有运行"
                    : (s.getLastError() != null ? s.getLastError() : "截屏没有产生画面");
            tvStatus.setText("拿不到画面：" + why
                    + "\n请回到主界面确认「悬浮窗辅助」处于运行中，"
                    + "或点「诊断信息」查看详情。");
            calibView.setBitmap(null);
            return;
        }
        retries = 0;
        frame = bmp;
        calibView.setBitmap(bmp);

        OverlayService s = OverlayService.get();
        if (s != null && s.getProfile().isCalibrated()) {
            // 已有标定则恢复角点位置，方便微调
            calibView.setCornersRelative(s.getProfile().cornersRel);
            blackBottom = "black".equals(s.getProfile().bottomSide);
            updateSideText();
        }
        tvStatus.setText("已截取画面，请拖动 4 个角点框住棋盘最外圈的交叉点");
    }

    private void doCalibrate() {
        if (working) {
            return;
        }
        if (frame == null) {
            tvStatus.setText(R.string.calib_need_frame);
            return;
        }
        final OverlayService service = OverlayService.get();
        if (service == null) {
            finish();
            return;
        }
        working = true;
        btnConfirm.setEnabled(false);
        tvStatus.setText("正在生成识别模板，请稍候…");

        final float[] corners = calibView.cornersRelative();
        final String hint = blackBottom ? "black" : "red";
        final Bitmap bmp = frame;

        new Thread(() -> {
            BoardProfile profile = service.getProfile();
            XiangqiRecognizer recognizer = new XiangqiRecognizer();
            String error = recognizer.calibrate(profile,
                    XiangqiRecognizer.fromBitmap(bmp), corners, hint);
            boolean ok = error == null && profile.save(CalibrationActivity.this);
            final String message = ok ? null : (error != null ? error : "保存标定失败");
            runOnUiThread(() -> {
                working = false;
                btnConfirm.setEnabled(true);
                if (ok) {
                    service.applyProfile();
                    Toast.makeText(CalibrationActivity.this,
                            R.string.calib_ok, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    tvStatus.setText(message);
                }
            });
        }, "calibrate").start();
    }
}
