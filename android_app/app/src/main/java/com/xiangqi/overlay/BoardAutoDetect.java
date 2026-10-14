package com.xiangqi.overlay;

import com.xiangqi.overlay.img.GrayImage;
import com.xiangqi.overlay.img.ImageOps;

/**
 * 自动识别棋盘：从截屏里找出 9×10 棋盘网格，返回四个角的相对坐标。
 * <p>
 * 算法（已用真机截图验证）：
 * <ol>
 *   <li>水平网格线定位：逐行统计上下差分能量 → 对「等间距 10 条线」
 *       做（间距 × 相位）全局穷举评分。棋子压线会让个别线能量变弱，
 *       但 10 条线整体的等距结构不会被破坏，全局评分比单峰提取稳得多。</li>
 *   <li>全分辨率细化 + 最小二乘回归：把每条线吸附到能量峰上，再用回归消除抖动。</li>
 *   <li>竖线同理，但列能量只在棋盘带内统计（排除背景干扰）。</li>
 *   <li>校验：线间距均匀、高宽比接近 9:8、四角在画面内。
 *       即使这里有漏网，后续 calibrate() 的「初始局面校验」也会兜底 ——
 *       只有真的认对了棋盘，模板才会生成。</li>
 * </ol>
 */
public final class BoardAutoDetect {

    private BoardAutoDetect() {
    }

    /**
     * 检测棋盘四角。
     *
     * @param gray 全分辨率灰度图
     * @return 8 个浮点数（0-1 相对坐标）：左上、右上、右下、左下；识别失败返回 null
     */
    public static float[] detect(GrayImage gray) {
        try {
            return detectInner(gray);
        } catch (Throwable t) {
            return null;   // 任何意外都不能拖垮识别循环
        }
    }

    private static float[] detectInner(GrayImage gray) {
        final int w = gray.width;
        final int h = gray.height;
        if (w < 200 || h < 300) {
            return null;
        }

        // ---------- 粗定位（降采样）：找水平线 ----------
        final int f = Math.max(1, w / 216);
        GrayImage small = ImageOps.resample(gray, w / f, h / f);
        int cw = small.width;
        int ch = small.height;

        float[] rowE = new float[ch - 1];
        for (int y = 0; y < ch - 1; y++) {
            int cnt = 0;
            for (int x = 0; x < cw; x++) {
                if (Math.abs(small.get(x, y + 1) - small.get(x, y)) > 18) {
                    cnt++;
                }
            }
            rowE[y] = cnt / (float) cw;
        }
        float[] rowS = smooth(rowE);
        if (rowS.length < 20) {
            return null;
        }

        int kMin = Math.max(4, (int) (ch * 0.20 / 9));
        int kMax = Math.max(kMin, (int) (ch * 0.60 / 9));
        long bestScore = Long.MIN_VALUE;
        int bestY0 = -1;
        int bestK = 0;
        for (int k = kMin; k <= kMax; k++) {
            float boost = 1.0f + 0.02f * (k - kMin) / Math.max(1, kMax - kMin);
            for (int y0 = 0; y0 + 9 * k < rowS.length; y0++) {
                float s = 0;
                for (int j = 0; j < 10; j++) {
                    s += rowS[y0 + j * k];
                }
                long score = (long) (s * boost * 1e6);
                if (score > bestScore) {
                    bestScore = score;
                    bestY0 = y0;
                    bestK = k;
                }
            }
        }
        if (bestY0 < 0 || bestK < 4) {
            return null;
        }

        // ---------- 全分辨率细化：水平线 ----------
        float[] rowE1 = new float[h - 1];
        for (int y = 0; y < h - 1; y++) {
            int cnt = 0;
            int base = y * w;
            for (int x = 0; x < w; x++) {
                if (Math.abs(gray.data[base + x + w] - gray.data[base + x]) > 20) {
                    cnt++;
                }
            }
            rowE1[y] = cnt / (float) w;
        }
        int[] hRaw = new int[10];
        for (int j = 0; j < 10; j++) {
            int c = (bestY0 + j * bestK) * f;
            hRaw[j] = argmaxAround(rowE1, c, 2 * f);
        }
        int[] hLines = regress(hRaw);
        if (!regular(hLines) || !inBounds(hLines[0], hLines[9], 0, h)) {
            return null;
        }

        // ---------- 竖线：列能量只在棋盘带内统计 ----------
        int bandTop = hLines[0];
        int bandBot = hLines[9];
        int bandH = bandBot - bandTop;
        if (bandH < 100) {
            return null;
        }
        float[] colE = new float[w - 1];
        for (int x = 0; x < w - 1; x++) {
            int cnt = 0;
            for (int y = bandTop; y < bandBot; y++) {
                if (Math.abs(gray.data[y * w + x + 1] - gray.data[y * w + x]) > 20) {
                    cnt++;
                }
            }
            colE[x] = cnt / (float) bandH;
        }
        float[] colS = smooth(colE);

        int kvMin = Math.max(4, (int) (w * 0.50 / 8));
        int kvMax = Math.max(kvMin, (int) (w * 0.95 / 8));
        long bestScoreV = Long.MIN_VALUE;
        int bestX0 = -1;
        int bestKx = 0;
        for (int kx = kvMin; kx <= kvMax; kx++) {
            float boost = 1.0f + 0.02f * (kx - kvMin) / Math.max(1, kvMax - kvMin);
            for (int x0 = 0; x0 + 8 * kx < colS.length; x0++) {
                float s = 0;
                for (int i = 0; i < 9; i++) {
                    s += colS[x0 + i * kx];
                }
                long score = (long) (s * boost * 1e6);
                if (score > bestScoreV) {
                    bestScoreV = score;
                    bestX0 = x0;
                    bestKx = kx;
                }
            }
        }
        if (bestX0 < 0 || bestKx < 4) {
            return null;
        }

        int[] vRaw = new int[9];
        for (int i = 0; i < 9; i++) {
            int c = bestX0 + i * bestKx;
            vRaw[i] = argmaxAround(colE, c, 2 * f);
        }
        int[] vLines = regress(vRaw);
        if (!regular(vLines) || !inBounds(vLines[0], vLines[8], 0, w)) {
            return null;
        }

        // ---------- 结构校验：高宽比应接近 9:8（0.85 ~ 1.35 容差） ----------
        float bw = vLines[8] - vLines[0];
        float bh = hLines[9] - hLines[0];
        float ratio = bh / bw;
        if (ratio < 0.85f || ratio > 1.35f) {
            return null;
        }

        return new float[]{
                vLines[0] / (float) w, hLines[0] / (float) h,
                vLines[8] / (float) w, hLines[0] / (float) h,
                vLines[8] / (float) w, hLines[9] / (float) h,
                vLines[0] / (float) w, hLines[9] / (float) h,
        };
    }

    /** [1,2,1]/4 平滑。 */
    private static float[] smooth(float[] e) {
        float[] out = new float[e.length];
        for (int i = 0; i < e.length; i++) {
            float a = i > 0 ? e[i - 1] : e[i];
            float b = i < e.length - 1 ? e[i + 1] : e[i];
            out[i] = (a + 2 * e[i] + b) / 4f;
        }
        return out;
    }

    /** 在 [c-span, c+span] 内找能量最大的位置，越界自动收缩。 */
    private static int argmaxAround(float[] e, int c, int span) {
        int lo = Math.max(1, c - span);
        int hi = Math.min(e.length - 1, c + span);
        int best = lo;
        for (int i = lo + 1; i <= hi; i++) {
            if (e[i] > e[best]) {
                best = i;
            }
        }
        return best;
    }

    /** 最小二乘回归：pos ≈ a + b·j，消除逐线抖动。 */
    private static int[] regress(int[] raw) {
        int n = raw.length;
        double sj = 0;
        double sy = 0;
        double sjj = 0;
        double sjy = 0;
        for (int j = 0; j < n; j++) {
            sj += j;
            sy += raw[j];
            sjj += (double) j * j;
            sjy += (double) j * raw[j];
        }
        double den = n * sjj - sj * sj;
        if (Math.abs(den) < 1e-9) {
            return raw.clone();
        }
        double b = (n * sjy - sj * sy) / den;
        double a = (sy - b * sj) / n;
        int[] out = new int[n];
        for (int j = 0; j < n; j++) {
            out[j] = (int) Math.round(a + b * j);
        }
        return out;
    }

    /** 线间距是否均匀（标准差 < 均值的 8%）。 */
    private static boolean regular(int[] lines) {
        if (lines.length < 3) {
            return false;
        }
        double sum = 0;
        int n = lines.length - 1;
        for (int i = 0; i < n; i++) {
            sum += lines[i + 1] - lines[i];
        }
        double mean = sum / n;
        if (mean < 3) {
            return false;
        }
        double sq = 0;
        for (int i = 0; i < n; i++) {
            double d = lines[i + 1] - lines[i] - mean;
            sq += d * d;
        }
        return Math.sqrt(sq / n) / mean < 0.08;
    }

    private static boolean inBounds(int a, int b, int min, int max) {
        return a >= min && b >= a && b < max;
    }
}
