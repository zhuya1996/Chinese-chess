package com.xiangqi.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮窗管理：两个窗口配合，既能在棋盘上画箭头，又不挡住用户的操作。
 *
 * <ul>
 *   <li><b>箭头层</b>：全屏、FLAG_NOT_TOUCHABLE —— 触摸完全穿透，只负责画标记</li>
 *   <li><b>面板层</b>：wrap_content、FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCH_MODAL
 *       —— 只有面板本身接收触摸（可拖动、可点按钮），面板以外的触摸全部穿透</li>
 * </ul>
 * 这样用户在象棋 App 里点棋子、拖棋子都不会被打断。
 */
public final class OverlayController {

    public interface Callback {
        void onCalibrateClicked();

        void onPauseClicked();

        void onCloseClicked();

        /** 标定层上点了「确认标定」。corners 为 0-1 相对坐标 ×8。 */
        void onCalibConfirm(float[] corners, boolean blackBottom);

        /** 标定层上点了「取消」或「重新抓取」。 */
        void onCalibCancel();
    }

    private final Context context;
    private final Callback callback;
    private WindowManager wm;
    private View panel;
    private ArrowView arrow;
    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams arrowParams;
    private boolean shown;

    private TextView tvStatus;
    private TextView tvOpponent;
    private TextView tvSuggest;
    private TextView tvScore;
    private TextView tvCandidates;
    private TextView tvToggle;
    private Button btnPause;
    private View body;
    private boolean collapsed;
    private int dragOffsetX;
    private int dragOffsetY;
    /** 展开面板的底边不得超过的屏幕 y 坐标（棋盘上沿-边距）；<=0 表示不限制。
     *  v1.7：面板展开时盖住棋盘顶行，白色面板边缘会污染识别图块，
     *  把空位认成幻影棋子（真机已复现「棋子数量不合法」死循环）。 */
    private int maxPanelBottom = -1;

    // ---- 标定层：全屏可触摸悬浮窗，内嵌 CalibrationView ----
    private View calibLayer;
    private WindowManager.LayoutParams calibParams;
    private CalibrationView calibView;
    private TextView tvCalibStatus;
    private Button btnCalibSide;
    private Button btnCalibOk;
    private boolean calibBlackBottom;
    private boolean calibrating;

    public OverlayController(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean isShown() {
        return shown;
    }

    public boolean isCalibrating() {
        return calibrating;
    }

    /** 显示悬浮窗。返回 null 成功，否则为需要告知用户的原因。 */
    public String show() {
        if (shown) {
            return null;
        }
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return "无法获取窗口服务，悬浮窗无法显示";
        }

        int type = DeviceCompat.overlayWindowType();
        int common = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        Context themed = new ContextThemeWrapper(context, R.style.AppTheme);
        LayoutInflater inflater = LayoutInflater.from(themed);

        // ---- 箭头层：全屏、触摸完全穿透，只负责画落点标记 ----
        // 这一层失败不算致命：面板还能给文字建议，所以只记下原因、不中断。
        String arrowError = null;
        arrow = new ArrowView(themed);
        arrowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                common | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        try {
            wm.addView(arrow, arrowParams);
        } catch (Throwable e) {
            arrow = null;
            arrowError = describeFailure("箭头层", e);
        }

        // ---- 面板层：可拖动、不抢焦点，面板以外触摸穿透 ----
        // 这一层是核心，失败就意味着用户什么都看不到。
        panel = inflater.inflate(R.layout.overlay_panel, null);
        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                common | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = 24;
        panelParams.y = 140;
        try {
            wm.addView(panel, panelParams);
        } catch (Throwable e) {
            panel = null;
            if (arrow != null) {
                try {
                    wm.removeView(arrow);
                } catch (Throwable ignored) {
                }
                arrow = null;
            }
            return describeFailure("主面板", e);
        }

        bindViews();
        setupDrag();
        shown = true;
        return arrowError;
    }

    /**
     * 把添加窗口时的异常翻译成用户能照着做的提示。
     * <p>
     * 这里之所以要区分异常类型，是因为「悬浮窗不显示」在国产 ROM 上有好几种
     * 完全不同的原因，笼统说一句"权限未开启"会让用户无从下手。
     */
    private static String describeFailure(String layer, Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append("悬浮窗").append(layer).append("未能显示");
        if (e instanceof WindowManager.BadTokenException) {
            sb.append("：被系统拒绝。请确认已开启「显示在其他应用上层」");
            if (DeviceCompat.isXiaomi()) {
                sb.append("；小米/红米还需额外打开「后台弹出界面」");
            }
            sb.append("。可点下方「诊断信息」查看检测结果。");
        } else if (e instanceof SecurityException) {
            sb.append("：系统拒绝添加窗口，悬浮窗权限未真正生效。");
        } else {
            String msg = e.getMessage();
            sb.append("：").append(e.getClass().getSimpleName());
            if (msg != null && msg.length() > 0) {
                sb.append(' ').append(msg);
            }
        }
        return sb.toString();
    }

    private void bindViews() {
        tvStatus = panel.findViewById(R.id.tvStatus);
        tvOpponent = panel.findViewById(R.id.tvOpponent);
        tvSuggest = panel.findViewById(R.id.tvSuggest);
        tvScore = panel.findViewById(R.id.tvScore);
        tvCandidates = panel.findViewById(R.id.tvCandidates);
        tvToggle = panel.findViewById(R.id.tvPanelToggle);
        btnPause = panel.findViewById(R.id.btnTogglePause);
        body = panel.findViewById(R.id.panelRoot);

        panel.findViewById(R.id.btnOverlayCalibrate).setOnClickListener(v -> {
            if (callback != null) {
                callback.onCalibrateClicked();
            }
        });
        btnPause.setOnClickListener(v -> {
            if (callback != null) {
                callback.onPauseClicked();
            }
        });
        panel.findViewById(R.id.btnOverlayClose).setOnClickListener(v -> {
            if (callback != null) {
                callback.onCloseClicked();
            }
        });
        tvToggle.setOnClickListener(v -> setCollapsed(!collapsed));
    }

    /** 按住标题栏拖动面板。 */
    private void setupDrag() {
        View header = panel.findViewById(R.id.panelHeader);
        header.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragOffsetX = (int) event.getRawX() - panelParams.x;
                    dragOffsetY = (int) event.getRawY() - panelParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    panelParams.x = (int) event.getRawX() - dragOffsetX;
                    panelParams.y = (int) event.getRawY() - dragOffsetY;
                    clampPanelBottom();
                    try {
                        wm.updateViewLayout(panel, panelParams);
                    } catch (Exception ignored) {
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    public void setPaused(boolean paused) {
        if (btnPause != null) {
            btnPause.setText(paused ? R.string.overlay_btn_resume : R.string.overlay_btn_pause);
        }
    }

    public void setCollapsed(boolean value) {
        collapsed = value;
        if (tvStatus != null) {
            tvStatus.setVisibility(value ? View.GONE : View.VISIBLE);
        }
        if (tvOpponent != null) {
            tvOpponent.setVisibility(value ? View.GONE : View.VISIBLE);
        }
        if (tvSuggest != null) {
            tvSuggest.setVisibility(value ? View.GONE : View.VISIBLE);
        }
        if (tvScore != null) {
            tvScore.setVisibility(value ? View.GONE : View.VISIBLE);
        }
        if (tvCandidates != null) {
            tvCandidates.setVisibility(value ? View.GONE : View.VISIBLE);
        }
        View buttons = panel.findViewById(R.id.panelButtons);
        if (buttons != null) {
            buttons.setVisibility(value ? View.GONE : View.VISIBLE);
        }
        if (tvToggle != null) {
            tvToggle.setText(value ? "展开" : "收起");
        }
        if (!value) {
            // 展开：布局完成后把底边压回棋盘上沿之内
            if (panel != null) {
                panel.post(this::clampPanelBottom);
            }
        }
        relayout();
    }

    private void relayout() {
        if (panel != null && wm != null) {
            try {
                wm.updateViewLayout(panel, panelParams);
            } catch (Exception ignored) {
            }
        }
    }

    /** 设置展开面板底边的最高允许位置（屏幕 y）。棋盘标定后由服务调用。 */
    public void setPanelMaxBottom(int y) {
        maxPanelBottom = y;
        if (y <= 0 || panel == null) {
            return;
        }
        // 面板必须 post 到布局完成后才知道自己的高度
        panel.post(() -> {
            if (panel == null) {
                return;
            }
            int h = panel.getHeight();
            if (h > 0 && maxPanelBottom > 0 && maxPanelBottom < h + 60 && !collapsed) {
                // 上方空间放不下完整面板：自动收起，避免遮挡棋盘
                setCollapsed(true);
                return;
            }
            clampPanelBottom();
        });
    }

    /** 把面板底边压回 maxPanelBottom 之内（拖动/布局变化时调用）。 */
    private void clampPanelBottom() {
        if (maxPanelBottom <= 0 || panel == null || collapsed) {
            return;
        }
        int h = panel.getHeight();
        if (h <= 0) {
            return;
        }
        int maxY = Math.max(0, maxPanelBottom - h);
        if (panelParams.y > maxY) {
            panelParams.y = maxY;
        }
    }

    public boolean isPanelExpanded() {
        return shown && !collapsed;
    }

    /** 自动收起面板（识别自愈用）。 */
    public void collapsePanel() {
        if (!collapsed) {
            setCollapsed(true);
        }
    }

    public boolean isArrowShown() {
        return arrow != null && arrow.hasMarks();
    }

    /** 更新面板文字。必须在主线程调用。 */
    public void update(String status, String opponent, String suggest, String score, String candidates) {
        if (!shown || panel == null) {
            return;
        }
        if (tvStatus != null) {
            tvStatus.setText(status == null ? "" : status);
        }
        if (tvOpponent != null) {
            boolean show = opponent != null && opponent.length() > 0;
            tvOpponent.setVisibility(show ? View.VISIBLE : View.GONE);
            tvOpponent.setText(opponent == null ? "" : opponent);
        }
        if (tvSuggest != null) {
            tvSuggest.setText(suggest == null ? "" : suggest);
        }
        if (tvScore != null) {
            tvScore.setText(score == null ? "" : score);
        }
        if (tvCandidates != null) {
            boolean show = candidates != null && candidates.length() > 0;
            tvCandidates.setVisibility(show ? View.VISIBLE : View.GONE);
            tvCandidates.setText(candidates == null ? "" : candidates);
        }
    }

    /** 在棋盘上画出建议着法的起止箭头。必须在主线程调用。 */
    public void showArrow(float fromX, float fromY, float toX, float toY, String text) {
        if (arrow == null) {
            return;
        }
        ArrowView.Mark m = new ArrowView.Mark();
        m.x0 = fromX;
        m.y0 = fromY;
        m.x1 = toX;
        m.y1 = toY;
        m.label = text;
        List<ArrowView.Mark> list = new ArrayList<>(1);
        list.add(m);
        arrow.setMarks(list);
    }

    public void clearArrow() {
        if (arrow != null) {
            arrow.clearMarks();
        }
    }

    // ------------------------------------------------------------ 标定层

    /**
     * 打开标定层。必须在主线程调用；frame 为冻结的棋盘画面，可为 null
     * （此时层内会提示用户先去象棋 App 停留几秒）。
     * <p>
     * 界面用纯代码构建，不走 XML inflate —— v1.3 真机（Android 16）上
     * inflate + findViewById 曾返回 null 导致崩溃，代码构建消除这类问题。
     * 整个方法体兜底 try/catch：标定层任何失败都绝不能拖垮进程。
     *
     * @return true 表示标定层已挂上；false 表示失败（调用方应复位状态）
     */
    public boolean showCalibration(android.graphics.Bitmap frame, float[] cornersRel,
                                   boolean blackBottom) {
        if (!shown || wm == null) {
            return false;
        }
        if (calibLayer != null) {
            hideCalibration();
        }
        try {
            Context themed = new ContextThemeWrapper(context, R.style.AppTheme);

            android.widget.LinearLayout root = new android.widget.LinearLayout(themed);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            root.setBackgroundColor(0xF2111111);

            // 顶部：标题 + 说明
            TextView title = new TextView(themed);
            title.setText(R.string.calib_layer_title);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(16);
            title.setPadding(dip(12), dip(10), dip(12), dip(2));
            root.addView(title, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView instr = new TextView(themed);
            instr.setText(R.string.calib_instruction);
            instr.setTextColor(0xFFDDDDDD);
            instr.setTextSize(13);
            instr.setPadding(dip(12), dip(2), dip(12), dip(2));
            root.addView(instr, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView zoom = new TextView(themed);
            zoom.setText(R.string.calib_zoom_hint);
            zoom.setTextColor(0xFF999999);
            zoom.setTextSize(12);
            zoom.setPadding(dip(12), dip(0), dip(12), dip(6));
            root.addView(zoom, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            // 中部：棋盘画面 + 角点
            CalibrationView view = new CalibrationView(themed);
            calibView = view;
            android.widget.LinearLayout.LayoutParams midParams =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            root.addView(view, midParams);

            // 底部：状态 + 按钮
            android.widget.LinearLayout bottom = new android.widget.LinearLayout(themed);
            bottom.setOrientation(android.widget.LinearLayout.VERTICAL);
            bottom.setBackgroundColor(0xE6000000);
            bottom.setPadding(dip(10), dip(8), dip(10), dip(10));

            TextView status = new TextView(themed);
            status.setTextColor(0xFFFFCC80);
            status.setTextSize(13);
            tvCalibStatus = status;
            bottom.addView(status, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            android.widget.LinearLayout row1 = new android.widget.LinearLayout(themed);
            row1.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            Button btnSide = new Button(themed);
            btnSide.setTextSize(13);
            Button btnRecapture = new Button(themed);
            btnRecapture.setTextSize(13);
            btnRecapture.setText(R.string.calib_recapture);
            row1.addView(btnSide, rowWeight());
            row1.addView(btnRecapture, rowWeight());
            bottom.addView(row1, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            android.widget.LinearLayout row2 = new android.widget.LinearLayout(themed);
            row2.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            Button btnCancel = new Button(themed);
            btnCancel.setTextSize(14);
            btnCancel.setText(R.string.calib_cancel);
            Button btnOk = new Button(themed);
            btnOk.setTextSize(15);
            btnOk.setText(R.string.calib_confirm);
            android.widget.LinearLayout.LayoutParams pCancel = rowWeight();
            android.widget.LinearLayout.LayoutParams pOk = rowWeight();
            pOk.weight = 2;
            row2.addView(btnCancel, pCancel);
            row2.addView(btnOk, pOk);
            bottom.addView(row2, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            root.addView(bottom, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            calibBlackBottom = blackBottom;
            updateSideText();
            btnCalibSide = btnSide;
            btnCalibOk = btnOk;

            btnSide.setOnClickListener(v -> {
                calibBlackBottom = !calibBlackBottom;
                updateSideText();
            });
            btnOk.setOnClickListener(v -> {
                if (callback != null && calibView != null && calibView.hasBitmap()) {
                    callback.onCalibConfirm(calibView.cornersRelative(), calibBlackBottom);
                } else if (callback != null) {
                    // 没有画面时点确认：当作取消，回到正常循环重新取帧
                    callback.onCalibCancel();
                }
            });
            btnRecapture.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onCalibCancel();
                }
            });
            btnCancel.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onCalibCancel();
                }
            });

            view.setBitmap(frame);
            if (frame != null && cornersRel != null && cornersRel.length >= 8) {
                view.setCornersRelative(cornersRel);
            }
            setCalibStatus(frame == null
                    ? "还没有棋盘画面。请点「取消」，先去象棋 App 打开一盘棋、停留两三秒，再点悬浮窗的「标定」。"
                    : "画面来自你点标定前的象棋 App。拖动角点对准棋盘交叉点后确认。");

            calibParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    DeviceCompat.overlayWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | FLAG_LAYOUT,
                    PixelFormat.TRANSLUCENT);
            wm.addView(root, calibParams);
            calibLayer = root;
            calibrating = true;
            return true;
        } catch (Throwable e) {
            // 标定失败绝不能拖垮进程（v1.3 的教训）
            calibLayer = null;
            calibView = null;
            tvCalibStatus = null;
            btnCalibSide = null;
            btnCalibOk = null;
            if (tvStatus != null) {
                tvStatus.setText("标定层未能显示：" + describeFailure("", e));
            }
            return false;
        }
    }

    private int dip(int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private android.widget.LinearLayout.LayoutParams rowWeight() {
        android.widget.LinearLayout.LayoutParams p =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dip(3), dip(2), dip(3), 0);
        return p;
    }

    private static final int FLAG_LAYOUT = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

    private void updateSideText() {
        if (btnCalibSide != null) {
            btnCalibSide.setText(calibBlackBottom
                    ? R.string.calib_bottom_black : R.string.calib_bottom_red);
        }
    }

    /** 更新标定层上的状态文字。必须在主线程调用。 */
    public void setCalibStatus(String text) {
        if (tvCalibStatus != null) {
            tvCalibStatus.setText(text == null ? "" : text);
        }
    }

    /** 关闭标定层。必须在主线程调用。 */
    public void hideCalibration() {
        if (calibLayer != null && wm != null) {
            try {
                wm.removeView(calibLayer);
            } catch (Throwable ignored) {
            }
        }
        calibLayer = null;
        calibView = null;
        tvCalibStatus = null;
        btnCalibSide = null;
        btnCalibOk = null;
        calibrating = false;
    }

    public void hide() {
        hideCalibration();
        if (panel != null && wm != null) {
            try {
                wm.removeView(panel);
            } catch (Exception ignored) {
            }
            panel = null;
        }
        if (arrow != null && wm != null) {
            try {
                wm.removeView(arrow);
            } catch (Exception ignored) {
            }
            arrow = null;
        }
        shown = false;
    }
}
