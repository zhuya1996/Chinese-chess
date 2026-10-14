package com.xiangqi.overlay.img;

import android.graphics.Bitmap;

/**
 * 纯 Java 实现本项目所需的图像运算，替代 OpenCV。
 *
 * 覆盖的能力：
 *  - 彩色转灰度            （对应 cv2.cvtColor BGR2GRAY）
 *  - 3x3 高斯模糊          （对应 cv2.GaussianBlur (3,3)）
 *  - 任意缩放（双线性）     （对应 cv2.resize）
 *  - 归一化互相关           （对应 cv2.matchTemplate TM_CCOEFF_NORMED）
 *  - Sobel 边缘占比         （近似 cv2.Canny 后的统计，用于占位判定）
 *
 * 全部针对识别场景做了尺寸限制（棋盘 480x540、模板 52x52），
 * 因此纯 Java 足够快，实测单帧全盘 90 点分类在毫秒级。
 */
public final class ImageOps {

    private ImageOps() {
    }

    // ---------------------------------------------------------------- 灰度

    public static GrayImage toGray(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        GrayImage g = new GrayImage(w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = (c >> 16) & 0xFF;
            int gr = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            g.data[i] = (int) (0.299 * r + 0.587 * gr + 0.114 * b);
        }
        return g;
    }

    // ---------------------------------------------------------------- 滤波

    /** 3x3 高斯模糊，核 [1,2,1;2,4,2;1,2,1]/16，边缘用 clamp。 */
    public static GrayImage gaussianBlur3(GrayImage in) {
        GrayImage out = new GrayImage(in.width, in.height);
        int w = in.width;
        int h = in.height;
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - 1);
            int y1 = y;
            int y2 = Math.min(h - 1, y + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - 1);
                int x1 = x;
                int x2 = Math.min(w - 1, x + 1);
                int sum = in.get(x0, y0) + 2 * in.get(x1, y0) + in.get(x2, y0)
                        + 2 * in.get(x0, y1) + 4 * in.get(x1, y1) + 2 * in.get(x2, y1)
                        + in.get(x0, y2) + 2 * in.get(x1, y2) + in.get(x2, y2);
                out.set(x, y, sum / 16);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 几何

    /** 双线性缩放到指定大小。 */
    public static GrayImage resample(GrayImage src, int outW, int outH) {
        if (src.width == outW && src.height == outH) {
            return src;
        }
        GrayImage out = new GrayImage(outW, outH);
        double sx = (double) src.width / outW;
        double sy = (double) src.height / outH;
        for (int y = 0; y < outH; y++) {
            double fy = (y + 0.5) * sy - 0.5;
            if (fy < 0) {
                fy = 0;
            }
            if (fy > src.height - 1) {
                fy = src.height - 1;
            }
            int y0 = (int) fy;
            int y1 = Math.min(src.height - 1, y0 + 1);
            double ty = fy - y0;
            for (int x = 0; x < outW; x++) {
                double fx = (x + 0.5) * sx - 0.5;
                if (fx < 0) {
                    fx = 0;
                }
                if (fx > src.width - 1) {
                    fx = src.width - 1;
                }
                int x0 = (int) fx;
                int x1 = Math.min(src.width - 1, x0 + 1);
                double tx = fx - x0;

                double v00 = src.get(x0, y0);
                double v10 = src.get(x1, y0);
                double v01 = src.get(x0, y1);
                double v11 = src.get(x1, y1);
                double top = v00 + (v10 - v00) * tx;
                double bot = v01 + (v11 - v01) * tx;
                out.set(x, y, (int) Math.round(top + (bot - top) * ty));
            }
        }
        return out;
    }

    /**
     * 从源图上以 (cx, cy) 为中心裁 size x size 的方块。
     * <p>
     * 越界部分先裁掉，再拉伸回 size x size —— 与 Python 原版
     * {@code crop_patch} 行为一致，保证模板与识别阶段处理方式相同。
     */
    public static GrayImage cropPatch(GrayImage src, int cx, int cy, int size) {
        int half = size / 2;
        int left = Math.max(0, cx - half);
        int top = Math.max(0, cy - half);
        int right = Math.min(src.width, cx + half);
        int bottom = Math.min(src.height, cy + half);
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) {
            return new GrayImage(size, size);
        }
        GrayImage part = src.crop(left, top, w, h);
        if (w == size && h == size) {
            return part;
        }
        return resample(part, size, size);
    }

    // ---------------------------------------------------------------- 匹配

    /**
     * 归一化互相关，等价于 cv2.matchTemplate(..., TM_CCOEFF_NORMED) 在单一位置上的取值。
     * 返回 -1 ~ 1，越接近 1 越相似。
     */
    public static double ncc(GrayImage a, GrayImage b) {
        if (a.data.length != b.data.length) {
            return 0.0;
        }
        int n = a.data.length;
        double ma = 0, mb = 0;
        for (int i = 0; i < n; i++) {
            ma += a.data[i];
            mb += b.data[i];
        }
        ma /= n;
        mb /= n;

        double num = 0, da = 0, db = 0;
        for (int i = 0; i < n; i++) {
            double va = a.data[i] - ma;
            double vb = b.data[i] - mb;
            num += va * vb;
            da += va * va;
            db += vb * vb;
        }
        double den = Math.sqrt(da * db);
        if (den <= 1e-9) {
            return 0.0;
        }
        double r = num / den;
        if (r > 1) {
            r = 1;
        }
        if (r < -1) {
            r = -1;
        }
        return r;
    }

    // ---------------------------------------------------------------- 统计

    /** 两图逐像素平均绝对差，归一化到 0-1。 */
    public static double meanAbsDiff(GrayImage a, GrayImage b) {
        if (a.data.length != b.data.length) {
            return 1.0;
        }
        double sum = 0;
        for (int i = 0; i < a.data.length; i++) {
            sum += Math.abs(a.data[i] - b.data[i]);
        }
        return sum / a.data.length / 255.0;
    }

    /** 中心 1/2 区域的灰度标准差，归一化到 0-1（棋子中心纹理丰富，空位平坦）。 */
    public static double centerStdDev(GrayImage g) {
        int x0 = g.width / 4, x1 = g.width * 3 / 4;
        int y0 = g.height / 4, y1 = g.height * 3 / 4;
        if (x1 <= x0 || y1 <= y0) {
            return 0;
        }
        double sum = 0, sq = 0;
        int n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double v = g.get(x, y);
                sum += v;
                sq += v * v;
                n++;
            }
        }
        if (n == 0) {
            return 0;
        }
        double mean = sum / n;
        double var = sq / n - mean * mean;
        double sd = var > 0 ? Math.sqrt(var) : 0;
        return sd / 255.0;
    }

    /**
     * Sobel 边缘像素占比（近似 Canny 后的统计，用于「这个点上有没有棋子」判定）。
     */
    public static double edgeRatio(GrayImage g, int threshold) {
        int w = g.width, h = g.height;
        if (w < 3 || h < 3) {
            return 0;
        }
        int count = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int gx = -g.get(x - 1, y - 1) - 2 * g.get(x - 1, y) - g.get(x - 1, y + 1)
                        + g.get(x + 1, y - 1) + 2 * g.get(x + 1, y) + g.get(x + 1, y + 1);
                int gy = -g.get(x - 1, y - 1) - 2 * g.get(x, y - 1) - g.get(x + 1, y - 1)
                        + g.get(x - 1, y + 1) + 2 * g.get(x, y + 1) + g.get(x + 1, y + 1);
                int mag = Math.abs(gx) + Math.abs(gy);
                if (mag > threshold) {
                    count++;
                }
            }
        }
        return (double) count / ((w - 2) * (h - 2));
    }

    // ------------------------------------------------------------ 彩色统计

    /** 一块彩色区域的「活跃度」统计，用于猜测界面上的行棋方指示。 */
    public static final class ColorStats {
        public double saturation;
        public double value;
        public double colorfulRatio;
        public double brightRatio;
    }

    /**
     * 对 ARGB 像素块做近似的 HSV 统计。
     * V = max(r,g,b)，S = (V - min) / V。
     */
    public static ColorStats colorStats(int[] argb, int offset, int rowStride,
                                        int left, int top, int right, int bottom) {
        ColorStats st = new ColorStats();
        long sumS = 0, sumV = 0;
        int colorful = 0, bright = 0, n = 0;
        for (int y = top; y < bottom; y++) {
            int rowBase = offset + y * rowStride;
            for (int x = left; x < right; x++) {
                int c = argb[rowBase + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int v = max;
                int s = max > 0 ? (max - min) * 255 / max : 0;
                sumS += s;
                sumV += v;
                if (s > 70 && v > 110) {
                    colorful++;
                }
                if (v > 150) {
                    bright++;
                }
                n++;
            }
        }
        if (n == 0) {
            return st;
        }
        st.saturation = (sumS / (double) n) / 255.0;
        st.value = (sumV / (double) n) / 255.0;
        st.colorfulRatio = colorful / (double) n;
        st.brightRatio = bright / (double) n;
        return st;
    }
}
