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

    public OverlayController(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean isShown() {
        return shown;
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

    public void hide() {
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
