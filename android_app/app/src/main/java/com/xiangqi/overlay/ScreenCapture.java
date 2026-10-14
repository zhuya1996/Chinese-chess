package com.xiangqi.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.xiangqi.overlay.img.GrayImage;

import java.nio.ByteBuffer;

/**
 * 屏幕截取。
 * <p>
 * 使用系统提供的 MediaProjection（用户在弹窗里点一次「开始录制/投放」即可），
 * 配合 ImageReader 主动拉帧 —— 相比旧方案通过 ADB 每秒截一张图，
 * 延迟从 1 秒级降到几十毫秒，而且完全不需要电脑。
 */
public final class ScreenCapture {

    /** 一帧画面（同时带 Bitmap，供标定界面显示）。 */
    public static final class Snapshot {
        public GrayImage gray;
        public int[] argb;
        public int width;
        public int height;
        public int rowStride;
        public Bitmap bitmap;
        /** 截到的是黑屏（对方 App 开了防截屏，或系统权限没给全）。 */
        public boolean black;
    }

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private int width;
    private int height;
    private int dpi;
    private volatile boolean running;

    public boolean isRunning() {
        return running;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * 启动截屏。
     *
     * @return null 表示成功，否则为失败原因
     */
    public String start(Context context, int resultCode, Intent data) {
        stop();
        MediaProjectionManager mgr =
                (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mgr == null) {
            return "系统不支持录屏";
        }
        try {
            projection = mgr.getMediaProjection(resultCode, data);
        } catch (Exception e) {
            projection = null;
            return "获取录屏授权失败：" + e.getMessage();
        }
        if (projection == null) {
            return "未获得录屏授权";
        }

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getRealMetrics(dm);
        // 锁定竖屏：短边为宽、长边为高
        width = Math.min(dm.widthPixels, dm.heightPixels);
        height = Math.max(dm.widthPixels, dm.heightPixels);
        dpi = dm.densityDpi;
        if (width < 100 || height < 100) {
            return "屏幕尺寸异常";
        }

        HandlerThread thread = new HandlerThread("screen-capture");
        thread.start();
        Handler handler = new Handler(thread.getLooper());

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = projection.createVirtualDisplay("xiangqi-capture",
                width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, handler);

        running = true;
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                running = false;
            }
        }, handler);
        return null;
    }

    public void stop() {
        running = false;
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception ignored) {
            }
            virtualDisplay = null;
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
            reader = null;
        }
        if (projection != null) {
            try {
                projection.stop();
            } catch (Exception ignored) {
            }
            projection = null;
        }
    }

    /**
     * 抓一帧。没有新帧时返回 null。
     * 输出的 gray 与 argb 会尽量复用已分配的数组，减少内存抖动。
     */
    public synchronized Snapshot grab(Snapshot reuse) {
        if (!running || reader == null) {
            return null;
        }
        Image img = null;
        try {
            img = reader.acquireLatestImage();
        } catch (Exception e) {
            img = null;
        }
        if (img == null) {
            return null;
        }

        Snapshot out = reuse != null ? reuse : new Snapshot();
        try {
            Image.Plane plane = img.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();
            int rowPadding = rowStride - pixelStride * width;
            int bmpWidth = width + rowPadding / pixelStride;

            Bitmap full = Bitmap.createBitmap(bmpWidth, height, Bitmap.Config.ARGB_8888);
            buffer.rewind();
            full.copyPixelsFromBuffer(buffer);
            Bitmap cropped = width == bmpWidth
                    ? full
                    : Bitmap.createBitmap(full, 0, 0, width, height);
            if (cropped != full) {
                full.recycle();
            }

            out.width = width;
            out.height = height;
            out.rowStride = width;
            out.bitmap = cropped;

            int n = width * height;
            if (out.argb == null || out.argb.length != n) {
                out.argb = new int[n];
            }
            cropped.getPixels(out.argb, 0, width, 0, 0, width, height);

            if (out.gray == null || out.gray.width != width || out.gray.height != height) {
                out.gray = new GrayImage(width, height);
            }
            for (int i = 0; i < n; i++) {
                int c = out.argb[i];
                out.gray.data[i] = (int) (0.299 * ((c >> 16) & 0xFF)
                        + 0.587 * ((c >> 8) & 0xFF)
                        + 0.114 * (c & 0xFF));
            }

            out.black = looksBlack(out.argb, n);
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                img.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 抽样判断是否截到黑屏（防截屏保护的典型表现）。 */
    private static boolean looksBlack(int[] argb, int n) {
        int samples = Math.min(n, 4096);
        int step = Math.max(1, n / samples);
        long sum = 0;
        int count = 0;
        for (int i = 0; i < n && count < samples; i += step) {
            int c = argb[i];
            sum += ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
            count++;
        }
        if (count == 0) {
            return true;
        }
        return (sum / count / 3.0) < 4.0;
    }
}
