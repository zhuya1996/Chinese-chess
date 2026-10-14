package com.xiangqi.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏透明层，用来在棋盘上画「从哪点走到哪点」的箭头。
 * 这个窗口设置了 FLAG_NOT_TOUCHABLE，所有触摸都会穿透到下面的象棋 App，
 * 因此完全不影响正常下棋。
 */
public final class ArrowView extends View {

    /** 一条标记：起点、终点、可选文字。 */
    public static final class Mark {
        public float x0;
        public float y0;
        public float x1;
        public float y1;
        public String label;
    }

    private final List<Mark> marks = new ArrayList<>();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ArrowView(Context context) {
        super(context);
        line.setColor(0xE6E74C3C);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(9f);
        line.setStrokeCap(Paint.Cap.ROUND);

        fill.setColor(0xE6E74C3C);
        fill.setStyle(Paint.Style.FILL);

        label.setColor(0xFFFFFFFF);
        label.setTextSize(38f);
        label.setTextAlign(Paint.Align.CENTER);
        label.setShadowLayer(6f, 0, 0, 0x99000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setMarks(List<Mark> list) {
        marks.clear();
        if (list != null) {
            marks.addAll(list);
        }
        invalidate();
    }

    public void clearMarks() {
        if (marks.isEmpty()) {
            return;
        }
        marks.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (Mark m : marks) {
            drawArrow(canvas, m.x0, m.y0, m.x1, m.y1);
            if (m.label != null && m.label.length() > 0) {
                canvas.drawText(m.label, m.x1, m.y1 - 24f, label);
            }
        }
    }

    private void drawArrow(Canvas canvas, float x0, float y0, float x1, float y1) {
        canvas.drawLine(x0, y0, x1, y1, line);
        double angle = Math.atan2(y1 - y0, x1 - x0);
        double spread = Math.PI / 7;
        float len = 30f;
        float ax = x1 - len * (float) Math.cos(angle - spread);
        float ay = y1 - len * (float) Math.sin(angle - spread);
        float bx = x1 - len * (float) Math.cos(angle + spread);
        float by = y1 - len * (float) Math.sin(angle + spread);
        canvas.drawLine(x1, y1, ax, ay, line);
        canvas.drawLine(x1, y1, bx, by, line);
        canvas.drawCircle(x0, y0, 12f, fill);
    }
}
