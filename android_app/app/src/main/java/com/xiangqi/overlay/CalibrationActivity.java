package com.xiangqi.overlay;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 四角标定界面。
 * <p>
 * 屏幕上显示的是<b>之前截到的棋盘画面</b>（来自 {@link OverlayService#takeCalibrationFrame()}），
 * 而不是现场截屏 —— 因为本界面是全屏 Activity，现场截屏只会截到它自己，
 * 棋盘永远不会出现在画面里。详见 OverlayService 中「冻结帧」一节的说明。
 * <p>
 * 推荐的操作路径：在象棋 App 的棋盘界面停留一下 → 点悬浮窗上的「标定」→
 * 拖动 4 个角点框住棋盘最外圈的 4 个交叉点 → 确认。
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
        btnRecapture.setOnClickListener(v -> loadFrame());
        btnConfirm.setOnClickListener(v -> doCalibrate());

        updateSideText();
        loadFrame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 告诉后台：本 App 的界面在前台，这段时间不要采样画面。
        // 否则会把「我们自己」拍下来当成棋盘。
        OverlayService.setUiForeground(true);
        // 用户去象棋 App 停留过再回来时，这里就能自动补上棋盘画面，
        // 不需要他再手动点一次「重新抓取」。
        if (frame == null) {
            loadFrame();
        }
    }

    @Override
    protected void onPause() {
        OverlayService.setUiForeground(false);
        super.onPause();
    }

    private void updateSideText() {
        btnSide.setText(blackBottom
                ? R.string.calib_bottom_black
                : R.string.calib_bottom_red);
    }

    /** 重新取一次冻结帧。用户在象棋 App 里停留过之后，这里就能拿到棋盘。 */
    private void loadFrame() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Bitmap bmp = OverlayService.takeCalibrationFrame();
        if (bmp == null) {
            frame = null;
            calibView.setBitmap(null);
            tvStatus.setText(R.string.calib_no_frame);
            return;
        }
        frame = bmp;
        calibView.setBitmap(bmp);

        OverlayService s = OverlayService.get();
        if (s != null && s.getProfile().isCalibrated()) {
            // 已有标定则恢复角点位置，方便微调
            calibView.setCornersRelative(s.getProfile().cornersRel);
            blackBottom = "black".equals(s.getProfile().bottomSide);
            updateSideText();
        }
        tvStatus.setText(R.string.calib_frame_ready);
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
            tvStatus.setText(R.string.calib_service_gone);
            return;
        }
        working = true;
        btnConfirm.setEnabled(false);
        tvStatus.setText("正在生成识别模板，请稍候…");

        final float[] corners = calibView.cornersRelative();
        final String hint = blackBottom ? "black" : "red";
        final Bitmap bmp = frame;

        new Thread(() -> {
            final BoardProfile profile = service.getProfile();
            XiangqiRecognizer recognizer = new XiangqiRecognizer();
            String error = recognizer.calibrate(profile,
                    XiangqiRecognizer.fromBitmap(bmp), corners, hint);
            final boolean ok = error == null && profile.save(CalibrationActivity.this);
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
