package com.xiangqi.overlay.img;

/**
 * 纯 Java 实现透视变换，对应 OpenCV 的 getPerspectiveTransform + warpPerspective。
 * <p>
 * 用途：把手机屏幕上斜着的棋盘（用户标定的 4 个角）拉伸成一张
 * 横平竖直的标准棋盘图，这样每个交叉点的位置就能直接用公式算出来。
 */
public final class Perspective {

    private Perspective() {
    }

    /**
     * 求 3x3 单应矩阵（h33 固定为 1）。
     *
     * @param src 8 个数：x0,y0,x1,y1,x2,y2,x3,y3，顺序为 左上、右上、右下、左下
     * @param dst 同上，为目标坐标系里的对应点
     * @return 长度 9 的矩阵，行优先：H[0..2] 为第一行；失败返回 null
     */
    public static double[] findHomography(double[] src, double[] dst) {
        double[][] a = new double[8][8];
        double[] b = new double[8];
        for (int i = 0; i < 4; i++) {
            double x = src[2 * i], y = src[2 * i + 1];
            double u = dst[2 * i], v = dst[2 * i + 1];
            int r1 = 2 * i, r2 = 2 * i + 1;

            a[r1][0] = x; a[r1][1] = y; a[r1][2] = 1;
            a[r1][3] = 0; a[r1][4] = 0; a[r1][5] = 0;
            a[r1][6] = -u * x; a[r1][7] = -u * y;
            b[r1] = u;

            a[r2][0] = 0; a[r2][1] = 0; a[r2][2] = 0;
            a[r2][3] = x; a[r2][4] = y; a[r2][5] = 1;
            a[r2][6] = -v * x; a[r2][7] = -v * y;
            b[r2] = v;
        }
        double[] h = solve(a, b);
        if (h == null) {
            return null;
        }
        return new double[]{h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0};
    }

    /**
     * 用 H 把目标坐标系映射回源图，逐像素双线性采样，得到矫正后的图。
     *
     * @param H 由 {@link #findHomography(dstPts, srcPts)} 得到（注意方向：dst -> src）
     */
    public static GrayImage warp(GrayImage src, double[] H, int outW, int outH) {
        GrayImage out = new GrayImage(outW, outH);
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                double denom = H[6] * x + H[7] * y + H[8];
                if (Math.abs(denom) < 1e-12) {
                    continue;
                }
                double u = (H[0] * x + H[1] * y + H[2]) / denom;
                double v = (H[3] * x + H[4] * y + H[5]) / denom;

                int x0 = (int) Math.floor(u);
                int y0 = (int) Math.floor(v);
                if (x0 < 0 || y0 < 0 || x0 >= src.width - 1 || y0 >= src.height - 1) {
                    continue;
                }
                double tx = u - x0;
                double ty = v - y0;

                double v00 = src.get(x0, y0);
                double v10 = src.get(x0 + 1, y0);
                double v01 = src.get(x0, y0 + 1);
                double v11 = src.get(x0 + 1, y0 + 1);

                double top = v00 + (v10 - v00) * tx;
                double bottom = v01 + (v11 - v01) * tx;
                out.set(x, y, (int) Math.round(top + (bottom - top) * ty));
            }
        }
        return out;
    }

    /**
     * 彩色版透视变换（v2.3，供棋子识别 CNN 使用）。
     * <p>
     * 采样逻辑与 {@link #warp} 完全一致，只是对 RGB 三个通道分别做双线性插值。
     *
     * @param srcArgb 源图 ARGB 像素（行优先）
     * @param out 复用的输出数组（长度须为 outW*outH，为 null 时新分配），存放 0xRRGGBB
     */
    public static int[] warpColor(int[] srcArgb, int srcW, int srcH, double[] H,
                                  int outW, int outH, int[] out) {
        if (out == null || out.length != outW * outH) {
            out = new int[outW * outH];
        }
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                double denom = H[6] * x + H[7] * y + H[8];
                if (Math.abs(denom) < 1e-12) {
                    continue;
                }
                double u = (H[0] * x + H[1] * y + H[2]) / denom;
                double v = (H[3] * x + H[4] * y + H[5]) / denom;

                int x0 = (int) Math.floor(u);
                int y0 = (int) Math.floor(v);
                if (x0 < 0 || y0 < 0 || x0 >= srcW - 1 || y0 >= srcH - 1) {
                    continue;
                }
                double tx = u - x0;
                double ty = v - y0;
                int o = y * outW + x;
                int c00 = srcArgb[y0 * srcW + x0];
                int c10 = srcArgb[y0 * srcW + x0 + 1];
                int c01 = srcArgb[(y0 + 1) * srcW + x0];
                int c11 = srcArgb[(y0 + 1) * srcW + x0 + 1];
                int rgb = 0;
                for (int shift = 16; shift >= 0; shift -= 8) {   // R, G, B
                    double v00 = (c00 >> shift) & 0xFF;
                    double v10 = (c10 >> shift) & 0xFF;
                    double v01 = (c01 >> shift) & 0xFF;
                    double v11 = (c11 >> shift) & 0xFF;
                    double top = v00 + (v10 - v00) * tx;
                    double bottom = v01 + (v11 - v01) * tx;
                    int ch = (int) Math.round(top + (bottom - top) * ty);
                    rgb |= (ch < 0 ? 0 : ch > 255 ? 255 : ch) << shift;
                }
                out[o] = rgb;
            }
        }
        return out;
    }

    /** 高斯消元求解 8x8 线性方程组（带列主元）。 */
    private static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        for (int col = 0; col < n; col++) {
            int pivot = col;
            double best = Math.abs(a[col][col]);
            for (int row = col + 1; row < n; row++) {
                double v = Math.abs(a[row][col]);
                if (v > best) {
                    best = v;
                    pivot = row;
                }
            }
            if (best < 1e-10) {
                return null;
            }
            if (pivot != col) {
                double[] tmp = a[pivot];
                a[pivot] = a[col];
                a[col] = tmp;
                double tb = b[pivot];
                b[pivot] = b[col];
                b[col] = tb;
            }
            for (int row = col + 1; row < n; row++) {
                double f = a[row][col] / a[col][col];
                if (f == 0) {
                    continue;
                }
                for (int k = col; k < n; k++) {
                    a[row][k] -= f * a[col][k];
                }
                b[row] -= f * b[col];
            }
        }
        double[] x = new double[n];
        for (int row = n - 1; row >= 0; row--) {
            double sum = b[row];
            for (int k = row + 1; k < n; k++) {
                sum -= a[row][k] * x[k];
            }
            x[row] = sum / a[row][row];
        }
        return x;
    }
}
