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
 * <p>
 * 性能上有一条硬要求：<b>不能每帧都 new 一个整屏 Bitmap</b>。
 * 1080×2400 的 ARGB_8888 一帧就是 10MB，按每秒十几帧算，
 * 光这一项就会产生每秒上百 MB 的垃圾，在手机上必然触发频繁 GC 甚至卡死。
 * 所以这里直接从 ImageReader 的字节缓冲里逐像素取数，全程不建 Bitmap。
 */
public final class ScreenCapture {

    /** 一帧画面：灰度图（识别用）+ ARGB 数组（判断行棋方、标定用）。 */
    public static final class Snapshot {
        public GrayImage gray;
        public int[] argb;
        public int width;
        public int height;
        /** ARGB 数组的行距（单位：int 个数）。这里恒等于 width。 */
        public int rowStride;
        /** 截到的是黑屏（对方 App 开了防截屏，或系统权限没给全）。 */
        public boolean black;
    }

    private MediaProjection projection;
    private MediaProjection.Callback callback;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private HandlerThread thread;
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

    private static String shortMsg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.length() == 0) ? t.getClass().getSimpleName() : m;
    }

    /**
     * 启动截屏。
     *
     * @return null 表示成功，否则为可直接展示给用户的失败原因
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
        } catch (Throwable e) {
            projection = null;
            return "获取录屏授权失败：" + shortMsg(e);
        }
        if (projection == null) {
            return "未获得录屏授权";
        }

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return "无法获取窗口服务";
        }
        DisplayMetrics dm = new DisplayMetrics();
        Display display = wm.getDefaultDisplay();
        display.getRealMetrics(dm);
        // 锁定竖屏：短边为宽、长边为高
        width = Math.min(dm.widthPixels, dm.heightPixels);
        height = Math.max(dm.widthPixels, dm.heightPixels);
        dpi = dm.densityDpi;
        if (width < 200 || height < 200) {
            return "读到的屏幕尺寸异常（" + width + "×" + height + "）";
        }

        thread = new HandlerThread("screen-capture");
        thread.start();
        final Handler handler = new Handler(thread.getLooper());

        // ---------------------------------------------------------------
        // Android 14（API 34）起有一条硬性要求：必须**先注册回调**，
        // 才能创建 VirtualDisplay，否则 createVirtualDisplay 直接抛异常，
        // 整个服务启动失败 —— 表现就是「给了权限但悬浮窗永远不出现」。
        // 顺序不能颠倒。
        // ---------------------------------------------------------------
        callback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                running = false;
                releaseSurface();
            }
        };
        try {
            projection.registerCallback(callback, handler);
        } catch (Throwable e) {
            stop();
            return "注册录屏回调失败：" + shortMsg(e);
        }

        try {
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
            virtualDisplay = projection.createVirtualDisplay("xiangqi-capture",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, handler);
        } catch (Throwable e) {
            String msg = shortMsg(e);
            releaseSurface();
            if (projection != null) {
                try {
                    projection.stop();
                } catch (Throwable ignored) {
                }
                projection = null;
            }
            return "创建录屏画面失败：" + msg;
        }

        running = true;
        return null;
    }

    /** 释放 VirtualDisplay 与 ImageReader（可重复调用）。 */
    private synchronized void releaseSurface() {
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Throwable ignored) {
            }
            virtualDisplay = null;
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (Throwable ignored) {
            }
            reader = null;
        }
    }

    public void stop() {
        running = false;
        releaseSurface();
        if (projection != null) {
            try {
                if (callback != null) {
                    projection.unregisterCallback(callback);
                }
            } catch (Throwable ignored) {
            }
            try {
                projection.stop();
            } catch (Throwable ignored) {
            }
            projection = null;
        }
        callback = null;
        if (thread != null) {
            try {
                thread.quitSafely();
            } catch (Throwable ignored) {
            }
            thread = null;
        }
    }

    /**
     * 抓一帧。没有新帧时返回 null。
     * 输出的 gray 与 argb 会复用已分配的数组，减少内存抖动。
     */
    public synchronized Snapshot grab(Snapshot reuse) {
        if (!running || reader == null) {
            return null;
        }
        Image img = null;
        try {
            img = reader.acquireLatestImage();
        } catch (Throwable e) {
            img = null;
        }
        if (img == null) {
            return null;
        }

        Snapshot out = reuse != null ? reuse : new Snapshot();
        try {
            int n = width * height;
            if (out.argb == null || out.argb.length != n) {
                out.argb = new int[n];
            }
            if (!readPixels(img, out.argb)) {
                return null;
            }
            out.width = width;
            out.height = height;
            out.rowStride = width;

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
        } catch (Throwable e) {
            return null;
        } finally {
            try {
                img.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 把 ImageReader 的 RGBA_8888 缓冲直接读成 0xAARRGGBB 数组。
     * 常规情况下（pixelStride == 4）走零分配的快速路径。
     */
    private boolean readPixels(Image img, int[] out) {
        Image.Plane[] planes = img.getPlanes();
        if (planes == null || planes.length == 0) {
            return false;
        }
        Image.Plane plane = planes[0];
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        if (pixelStride != 4 || rowStride < width * 4) {
            return readPixelsSlow(img, out);
        }
        ByteBuffer buf = plane.getBuffer();
        int limit = buf.limit();
        for (int y = 0; y < height; y++) {
            int rowStart = y * rowStride;
            if (rowStart + width * 4 > limit) {
                return false;
            }
            int o = y * width;
            for (int x = 0; x < width; x++) {
                int i = rowStart + (x << 2);
                int r = buf.get(i) & 0xFF;
                int g = buf.get(i + 1) & 0xFF;
                int b = buf.get(i + 2) & 0xFF;
                int a = buf.get(i + 3) & 0xFF;
                out[o + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return true;
    }

    /** 兜底路径：只在 pixelStride 不是 4 的罕见机型上使用。 */
    private boolean readPixelsSlow(Image img, int[] out) {
        Image.Plane plane = img.getPlanes()[0];
        int pixelStride = Math.max(1, plane.getPixelStride());
        int rowStride = plane.getRowStride();
        int fullWidth = rowStride / pixelStride;
        ByteBuffer buf = plane.getBuffer();
        buf.rewind();
        Bitmap full = Bitmap.createBitmap(fullWidth, height, Bitmap.Config.ARGB_8888);
        full.copyPixelsFromBuffer(buf);
        full.getPixels(out, 0, width, 0, 0, width, height);
        full.recycle();
        return true;
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
