package com.xiangqi.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Bitmap;
import android.view.MotionEvent;
import android.view.View;

/**
 * 标定视图：显示截取到的屏幕画面，用户拖动 4 个角点框住棋盘。
 * <p>
 * 支持单指拖动角点或平移画面、双指缩放。角点内部以<b>原图像素坐标</b>保存，
 * 对外提供 0-1 的相对坐标，这样换分辨率/换机型都不用重标。
 */
public final class CalibrationView extends View {

    private static final String[] NAMES = {"左上", "右上", "右下", "左下"};

    private Bitmap bitmap;
    private final PointF[] handles = new PointF[4];
    private final Matrix drawMatrix = new Matrix();

    private float scale = 1f;
    private float tx;
    private float ty;

    private int dragging = -1;
    private float lastX;
    private float lastY;
    private float startDist;
    private float startScale;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CalibrationView(Context context) {
        this(context, null);
    }

    /** 从 XML 填充时 Android 会调用这个构造函数，必须提供。 */
    public CalibrationView(Context context, android.util.AttributeSet attrs) {
        super(context);
        linePaint.setColor(0xFFFF5252);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);

        fillPaint.setColor(0xCCFF5252);
        fillPaint.setStyle(Paint.Style.FILL);

        ringPaint.setColor(0xFFFFFFFF);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(34f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(6f, 0, 0, 0x99000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        if (bmp != null) {
            handles[0] = new PointF(bmp.getWidth() * 0.06f, bmp.getHeight() * 0.06f);
            handles[1] = new PointF(bmp.getWidth() * 0.94f, bmp.getHeight() * 0.06f);
            handles[2] = new PointF(bmp.getWidth() * 0.94f, bmp.getHeight() * 0.94f);
            handles[3] = new PointF(bmp.getWidth() * 0.06f, bmp.getHeight() * 0.94f);
            post(this::resetView);
        }
        invalidate();
    }

    public boolean hasBitmap() {
        return bitmap != null;
    }

    private void resetView() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float vw = getWidth();
        float vh = getHeight();
        float base = Math.min(vw / bitmap.getWidth(), vh / bitmap.getHeight());
        scale = base;
        tx = (vw - bitmap.getWidth() * base) / 2f;
        ty = (vh - bitmap.getHeight() * base) / 2f;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetView();
    }

    /** 返回 4 个角点的相对坐标（0-1），顺序：左上、右上、右下、左下。 */
    public float[] cornersRelative() {
        float[] out = new float[8];
        if (bitmap == null) {
            return out;
        }
        for (int i = 0; i < 4; i++) {
            out[2 * i] = handles[i].x / bitmap.getWidth();
            out[2 * i + 1] = handles[i].y / bitmap.getHeight();
        }
        return out;
    }

    public void setCornersRelative(float[] rel) {
        if (bitmap == null || rel == null || rel.length < 8) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            handles[i].set(rel[2 * i] * bitmap.getWidth(), rel[2 * i + 1] * bitmap.getHeight());
        }
        invalidate();
    }

    // ------------------------------------------------------------ 绘制

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF111111);
        if (bitmap == null) {
            return;
        }
        drawMatrix.reset();
        drawMatrix.postScale(scale, scale);
        drawMatrix.postTranslate(tx, ty);
        canvas.drawBitmap(bitmap, drawMatrix, null);

        float[][] v = new float[4][2];
        for (int i = 0; i < 4; i++) {
            v[i][0] = handles[i].x * scale + tx;
            v[i][1] = handles[i].y * scale + ty;
        }

        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            canvas.drawLine(v[i][0], v[i][1], v[j][0], v[j][1], linePaint);
        }

        float r = 34f;
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(v[i][0], v[i][1], r, fillPaint);
            canvas.drawCircle(v[i][0], v[i][1], r, ringPaint);
            canvas.drawText(NAMES[i], v[i][0], v[i][1] + 11f, textPaint);
        }
    }

    // ------------------------------------------------------------ 触摸

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastX = event.getX();
                lastY = event.getY();
                dragging = hitHandle(lastX, lastY);
                startDist = 0;
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (event.getPointerCount() >= 2) {
                    dragging = -1;
                    startDist = distance(event);
                    startScale = scale;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (event.getPointerCount() >= 2) {
                    if (startDist > 0) {
                        float d = distance(event);
                        float ns = startScale * (d / startDist);
                        scale = Math.max(0.2f, Math.min(10f, ns));
                        invalidate();
                    }
                    return true;
                }
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                lastX = event.getX();
                lastY = event.getY();
                if (dragging >= 0) {
                    float bx = (lastX - tx) / scale;
                    float by = (lastY - ty) / scale;
                    handles[dragging].set(
                            Math.max(-bitmap.getWidth() * 0.05f, Math.min(bitmap.getWidth() * 1.05f, bx)),
                            Math.max(-bitmap.getHeight() * 0.05f, Math.min(bitmap.getHeight() * 1.05f, by)));
                } else {
                    tx += dx;
                    ty += dy;
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = -1;
                startDist = 0;
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private int hitHandle(float vx, float vy) {
        float best = Float.MAX_VALUE;
        int idx = -1;
        for (int i = 0; i < 4; i++) {
            float hx = handles[i].x * scale + tx;
            float hy = handles[i].y * scale + ty;
            float d = (float) Math.hypot(vx - hx, vy - hy);
            if (d < 90f && d < best) {
                best = d;
                idx = i;
            }
        }
        return idx;
    }

    private static float distance(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.hypot(dx, dy);
    }
}
