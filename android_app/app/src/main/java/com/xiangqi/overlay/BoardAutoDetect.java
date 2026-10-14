package com.xiangqi.overlay;

import com.xiangqi.overlay.img.GrayImage;

/**
 * 自动识别棋盘 v2：行指纹法（在用户 3 张真机截图上用 Python 验证后移植）。
 * <p>
 * 旧版（逐行差分能量 + 等间距穷举）被真机证伪：屏幕下方的按钮条、
 * 装饰元素会产生更强的周期性边缘，把网格"吸"过去（实测 3 张截图全找错）。
 * <p>
 * 新版利用初始局面的先验 —— 每行棋子数是已知的：
 * <ul>
 *   <li><b>行指纹</b>：row0/row9 整行 9 子 → 网格线被棋子盖住（线能量低）、
 *       棋子边缘密度高；row1/4/5/8 整行为空 → 网格线完整可见（线能量高）、
 *       无棋子边缘。这个"高低交替"指纹是按钮区/背景伪造不出来的。</li>
 *   <li><b>列定位</b>：row1/row8 整行无棋子，9 条竖线完整可见，
 *       只在这两条窄带里统计竖线能量，穷举 (L, cw) 使 9 峰全命中。</li>
 *   <li><b>峰吸附 + 最小二乘回归</b>：粗定位后把每条线吸附到 ±3px 内的能量峰，
 *       回归出亚像素精度的 (L, T, cw, ch)。真机截图实测误差 &lt; 5px。</li>
 *   <li>格子为正方形（真机三图 cw≈ch），用作最终形状校验。</li>
 * </ul>
 * 即使这里漏网，calibrate() 的"初始局面校验"仍会兜底：
 * 只有真的认对了棋盘，模板才会生成。
 * <p>
 * 手动标定也走 {@link #refineNear}：用户只需大致框住棋盘，自动精确对齐。
 */
public final class BoardAutoDetect {

    private BoardAutoDetect() {
    }

    /** 网格线的梯度阈值（3x3 模糊后）。 */
    private static final int LINE_THR = 25;
    /** 棋子边缘的梯度幅值阈值。 */
    private static final int EDGE_THR = 90;
    /** 行指纹最小对比度 / 列峰最小强度。 */
    private static final double MIN_ROW_CONTRAST = 0.10;
    private static final double MIN_COL_PEAK = 0.10;

    /** 检测结果：左上角 (L,T) 与格距 (cw,ch)，单位像素。 */
    private static final class Grid {
        final double L, T, cw, ch;

        Grid(double L, double T, double cw, double ch) {
            this.L = L;
            this.T = T;
            this.cw = cw;
            this.ch = ch;
        }

        float[] toCorners(int w, int h) {
            return new float[]{
                    (float) (L / w), (float) (T / h),
                    (float) ((L + 8 * cw) / w), (float) (T / h),
                    (float) ((L + 8 * cw) / w), (float) ((T + 9 * ch) / h),
                    (float) (L / w), (float) ((T + 9 * ch) / h),
            };
        }
    }

    /**
     * 全局自动识别（免标定主路径）。
     *
     * @return 8 个相对坐标（左上/右上/右下/左下）；失败返回 null
     */
    public static float[] detect(GrayImage gray) {
        try {
            Grid g = search(gray, null);
            return g == null ? null : g.toCorners(gray.width, gray.height);
        } catch (Throwable t) {
            return null;   // 任何意外都不能拖垮识别循环
        }
    }

    /**
     * 手动标定吸附：用户只需大致框住棋盘，这里在粗略四角附近重新找网格。
     * 找不到就返回 null（调用方沿用用户原角点）。
     */
    public static float[] refineNear(GrayImage gray, float[] cornersRel) {
        try {
            if (gray == null || cornersRel == null || cornersRel.length < 8) {
                return null;
            }
            final int w = gray.width;
            final int h = gray.height;
            double L0 = (cornersRel[0] + cornersRel[6]) / 2.0 * w;
            double T0 = (cornersRel[1] + cornersRel[3]) / 2.0 * h;
            double R0 = (cornersRel[2] + cornersRel[4]) / 2.0 * w;
            double B0 = (cornersRel[5] + cornersRel[7]) / 2.0 * h;
            double cw0 = (R0 - L0) / 8.0;
            double ch0 = (B0 - T0) / 9.0;
            if (cw0 < 10 || ch0 < 10 || cw0 > w || ch0 > h) {
                return null;
            }
            Grid g = search(gray, new double[]{L0, T0, cw0, ch0});
            return g == null ? null : g.toCorners(w, h);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------ 主流程

    /**
     * @param near null=全局搜索；否则 {L,T,cw,ch} 表示只在用户粗略估计附近找
     */
    private static Grid search(GrayImage gray, double[] near) {
        final int W = gray.width;
        final int H = gray.height;
        if (W < 200 || H < 300) {
            return null;
        }

        GrayImage b = blur3(gray);

        // ---- 行剖面：网格线能量 + 棋子边缘密度（只统计中间 70% 宽度，避开两侧背景）----
        final int x0 = W * 15 / 100;
        final int x1 = W * 85 / 100;
        final float[] lineE = new float[H];
        final float[] edgeR = new float[H];
        for (int y = 1; y < H - 1; y++) {
            int cLine = 0;
            int cEdge = 0;
            final int base = y * W;
            for (int x = x0; x < x1; x++) {
                int dyv = Math.abs(b.data[base + W + x] - b.data[base - W + x]);
                int dxv = Math.abs(b.data[base + x + 1] - b.data[base + x - 1]);
                if (dyv > LINE_THR) {
                    cLine++;
                }
                if (dyv + dxv > EDGE_THR) {
                    cEdge++;
                }
            }
            lineE[y] = cLine / (float) (x1 - x0);
            edgeR[y] = cEdge / (float) (x1 - x0);
        }
        smooth(lineE);
        smooth(edgeR);

        // ---- 行指纹搜索（满行 0/9 vs 空行 1/4/5/8），保留 top5 ----
        final int[] FULL = {0, 9};
        final int[] EMPTY = {1, 4, 5, 8};
        double chLo = H * 0.020;
        double chHi = H * 0.095;
        double tLo = H * 0.10;
        double tHi = H * 0.78;
        if (near != null) {
            chLo = Math.max(4, near[3] * 0.75);
            chHi = Math.min(H / 9.0, near[3] * 1.30);
            tLo = Math.max(1, near[1] - 1.2 * near[3]);
            tHi = Math.min(H - 9 * chLo - 2, near[1] + 1.2 * near[3]);
        }
        final double[] bScore = {-1e9, -1e9, -1e9, -1e9, -1e9};
        final double[] bT = new double[5];
        final double[] bCh = new double[5];
        for (double ch = chLo; ch <= chHi; ch += 1.0) {
            for (double T = tLo; T <= tHi; T += 2.0) {
                if (T + 9 * ch > H - 4) {
                    break;
                }
                double loLine = 1e9;
                for (int j : EMPTY) {
                    loLine = Math.min(loLine, sample(lineE, T + j * ch));
                }
                double hiLine = 0;
                double hiEdge = 1e9;
                for (int j : FULL) {
                    hiLine = Math.max(hiLine, sample(lineE, T + j * ch));
                    hiEdge = Math.min(hiEdge, sample(edgeR, T + j * ch));
                }
                double loEdge = 0;
                for (int j : EMPTY) {
                    loEdge = Math.max(loEdge, sample(edgeR, T + j * ch));
                }
                double sc = (loLine - hiLine) + (hiEdge - loEdge);
                insertTop5(bScore, bT, bCh, sc, T, ch);
            }
        }
        if (bScore[0] < MIN_ROW_CONTRAST) {
            return null;
        }

        // ---- 对每个行候选做列定位，取第一个可行解（行候选已按对比度排序）----
        for (int k = 0; k < 5; k++) {
            if (bScore[k] < 0) {
                break;
            }
            Grid g = locateColumns(b, lineE, W, H, bT[k], bCh[k], near);
            if (g != null) {
                return g;
            }
        }
        return null;
    }

    /** 在行候选 (T0,ch0) 基础上定位列，并做峰吸附回归。 */
    private static Grid locateColumns(GrayImage b, float[] lineE, int W, int H,
                                      double T0, double ch0, double[] near) {
        // ---- 列剖面：第 1、8 行整行无棋子，竖线完整可见 ----
        final int d = Math.max(3, (int) (ch0 * 0.30));
        final int r1 = (int) Math.round(T0 + ch0);
        final int r8 = (int) Math.round(T0 + 8 * ch0);
        final float[] colE = new float[W];
        int bandRows = 0;
        for (int y = Math.max(1, r1 - d); y <= Math.min(H - 2, r1 + d); y++) {
            bandRows++;
        }
        for (int y = Math.max(1, r8 - d); y <= Math.min(H - 2, r8 + d); y++) {
            bandRows++;
        }
        if (bandRows < 8) {
            return null;
        }
        for (int x = 1; x < W - 1; x++) {
            int cnt = 0;
            for (int y = Math.max(1, r1 - d); y <= Math.min(H - 2, r1 + d); y++) {
                if (Math.abs(b.data[y * W + x + 1] - b.data[y * W + x - 1]) > LINE_THR) {
                    cnt++;
                }
            }
            for (int y = Math.max(1, r8 - d); y <= Math.min(H - 2, r8 + d); y++) {
                if (Math.abs(b.data[y * W + x + 1] - b.data[y * W + x - 1]) > LINE_THR) {
                    cnt++;
                }
            }
            colE[x] = cnt / (float) bandRows;
        }
        smooth(colE);

        double cwLo = W * 0.030;
        double cwHi = W * 0.135;
        double lLo = W * 0.01;
        double lHi = W * 0.62;
        if (near != null) {
            cwLo = Math.max(4, near[2] * 0.75);
            cwHi = Math.min(W / 8.0, near[2] * 1.30);
            lLo = Math.max(1, near[0] - 1.2 * near[2]);
            lHi = Math.min(W - 8 * cwLo - 2, near[0] + 1.2 * near[2]);
        }
        double bestSc = -1;
        double bestL = -1;
        double bestCw = -1;
        for (double cw = cwLo; cw <= cwHi; cw += 1.0) {
            for (double L = lLo; L <= lHi; L += 2.0) {
                if (L + 8 * cw > W - 3) {
                    break;
                }
                double v = 1e9;
                for (int i = 0; i < 9; i++) {
                    v = Math.min(v, sample(colE, L + i * cw));
                }
                if (v > bestSc) {
                    bestSc = v;
                    bestL = L;
                    bestCw = cw;
                }
            }
        }
        if (bestSc < MIN_COL_PEAK) {
            return null;
        }

        // ---- 列峰吸附 + 回归：c_i ≈ L + cw·i ----
        double[] cs = new double[9];
        for (int i = 0; i < 9; i++) {
            int p = argmaxAround(colE, (int) Math.round(bestL + i * bestCw), 3);
            if (p < 0 || colE[p] < MIN_COL_PEAK) {
                return null;
            }
            cs[i] = p;
        }
        double[] lc = leastSquares(cs);
        if (lc == null) {
            return null;
        }
        double L = lc[0];
        double cw = lc[1];

        // ---- 行峰吸附 + 回归：p_j ≈ T + ch·j（j=1..8 这些行的线可见）----
        double[] ps = new double[9];
        int cnt = 0;
        for (int j = 1; j <= 8; j++) {
            int p = argmaxAround(lineE, (int) Math.round(T0 + j * ch0), 3);
            if (p > 0 && lineE[p] > MIN_COL_PEAK) {
                ps[j] = p;
                cnt++;
            }
        }
        double T = T0;
        double ch = ch0;
        if (cnt >= 6) {
            double[] tc = leastSquaresFit(ps, 1, 8);
            if (tc != null) {
                T = tc[0];
                ch = tc[1];
            }
        }

        // ---- 形状与边界校验（格子为正方形）----
        if (cw < 15 || ch < 15) {
            return null;
        }
        if (Math.abs(ch - cw) > 0.15 * cw) {
            return null;
        }
        if (L < 2 || T < 2 || L + 8 * cw > W - 2 || T + 9 * ch > H - 2) {
            return null;
        }
        return new Grid(L, T, cw, ch);
    }

    // ------------------------------------------------------------ 工具

    /** 在 [c-span, c+span] 内找能量最大的位置（越界收缩）；无效返回 -1。 */
    private static int argmaxAround(float[] e, int c, int span) {
        if (c < 1 || c >= e.length - 1) {
            return -1;
        }
        int lo = Math.max(1, c - span);
        int hi = Math.min(e.length - 2, c + span);
        int best = lo;
        for (int i = lo + 1; i <= hi; i++) {
            if (e[i] > e[best]) {
                best = i;
            }
        }
        return best;
    }

    /** 取 arr[y-2..y+2] 的最大值（越界收缩）。 */
    private static double sample(float[] arr, double y) {
        int c = (int) Math.round(y);
        int lo = Math.max(1, c - 2);
        int hi = Math.min(arr.length - 2, c + 2);
        double m = arr[lo];
        for (int i = lo + 1; i <= hi; i++) {
            m = Math.max(m, arr[i]);
        }
        return m;
    }

    /** [1,2,1]/4 平滑（原地）。 */
    private static void smooth(float[] e) {
        int n = e.length;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float a = i > 0 ? e[i - 1] : e[i];
            float b2 = i < n - 1 ? e[i + 1] : e[i];
            out[i] = (a + 2 * e[i] + b2) / 4f;
        }
        System.arraycopy(out, 0, e, 0, n);
    }

    /** top-5 大顶维护。 */
    private static void insertTop5(double[] s, double[] t, double[] c, double sc, double T, double ch) {
        for (int k = 0; k < 5; k++) {
            if (sc > s[k]) {
                for (int m = 4; m > k; m--) {
                    s[m] = s[m - 1];
                    t[m] = t[m - 1];
                    c[m] = c[m - 1];
                }
                s[k] = sc;
                t[k] = T;
                c[k] = ch;
                return;
            }
        }
    }

    /** v[j] ≈ a + b·j，j ∈ [j0, j1]，v[j]<=0 的点跳过。返回 {a, b}；数据不足返回 null。 */
    private static double[] leastSquaresFit(double[] v, int j0, int j1) {
        int n = j1 - j0 + 1;
        if (n < 3) {
            return null;
        }
        double sj = 0, sy = 0, sjj = 0, sjy = 0;
        int used = 0;
        for (int j = j0; j <= j1; j++) {
            double y = v[j];
            if (y <= 0) {
                continue;
            }
            sj += j;
            sy += y;
            sjj += (double) j * j;
            sjy += (double) j * y;
            used++;
        }
        if (used < 3) {
            return null;
        }
        double den = used * sjj - sj * sj;
        if (Math.abs(den) < 1e-9) {
            return null;
        }
        double b = (used * sjy - sj * sy) / den;
        double a = (sy - b * sj) / used;
        return new double[]{a, b};
    }

    /** 等距最小二乘：v_i ≈ a + b·i（i=0..n-1）。 */
    private static double[] leastSquares(double[] v) {
        return leastSquaresFit(v, 0, v.length - 1);
    }

    /** 3x3 高斯模糊 [1,2,1;2,4,2;1,2,1]/16。 */
    private static GrayImage blur3(GrayImage src) {
        final int w = src.width;
        final int h = src.height;
        GrayImage out = new GrayImage(w, h);
        for (int y = 0; y < h; y++) {
            final int yUp = y > 0 ? y - 1 : 0;
            final int yDn = y < h - 1 ? y + 1 : h - 1;
            final int base = y * w;
            final int up = yUp * w;
            final int dn = yDn * w;
            for (int x = 0; x < w; x++) {
                final int xL = x > 0 ? x - 1 : 0;
                final int xR = x < w - 1 ? x + 1 : w - 1;
                int v = src.data[up + xL] + 2 * src.data[up + x] + src.data[up + xR]
                        + 2 * src.data[base + xL] + 4 * src.data[base + x] + 2 * src.data[base + xR]
                        + src.data[dn + xL] + 2 * src.data[dn + x] + src.data[dn + xR];
                out.data[base + x] = v / 16;
            }
        }
        return out;
    }
}
