package com.xiangqi.overlay;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * v2.3：棋子识别小模型（纯 Java 推理，无任何第三方依赖）。
 * <p>
 * 背景：模板匹配（NCC）在 JJ 象棋「立体瓷砖」皮肤 + 亚像素错位下会崩
 * （离线实测：错位扰动下 NCC 基线准确率 0.449，本模型 0.850）。
 * 训练数据来自真机开局截图（开局阵型已知 → 自动标注，零人工），在 PC 端
 * 用 PyTorch 训练后把权重导出为 assets/ml_weights.bin，本类负责前向推理。
 * <p>
 * 结构：3x32x32 RGB → conv3x3(8) → ReLU → maxpool2 → conv3x3(16) → ReLU
 * → maxpool2 → fc(48) → ReLU → fc(15)。
 * 15 类 = 14 种棋子（小写黑 / 大写红，与 Position 一致）+ 空位。
 * <p>
 * 输入约定：棋盘 canonical（480x540，格 60px）上以交叉点为中心取 60x60 窗口，
 * 边界外复制，双线性缩放到 32x32，归一化 v/127.5-1。
 */
public final class PieceNet {

    public static final int IN = 32;
    public static final int CH1 = 8;
    public static final int CH2 = 16;
    public static final int NCLS = 15;
    /** 与 Python 训练脚本中的 CLASSES 顺序严格一致；下标 14 = 空位。 */
    private static final String CLASSES = "rnbakcp" + "RNBAKCP" + "E";
    private static final float IN_WIN = 60f;   // 输入窗口（canonical 像素，正好一格）

    // 权重（float32，行优先，布局与 torch state_dict 一致）
    private final float[] w1, b1, w2, b2, w3, b3, w4, b4;   // conv1 / conv2 / fc1 / fc2
    private final float mean, std;

    // 推理暂存（识别在单线程循环里跑，加锁只为防标定线程偶发并发）
    private final float[] in = new float[3 * IN * IN];
    private final float[] c1 = new float[CH1 * IN * IN];
    private final float[] p1 = new float[CH1 * (IN / 2) * (IN / 2)];
    private final float[] c2 = new float[CH2 * (IN / 2) * (IN / 2)];
    private final float[] p2 = new float[CH2 * (IN / 4) * (IN / 4)];
    private final float[] h1 = new float[48];
    private final float[] logits = new float[NCLS];

    private PieceNet(float[] w1, float[] b1, float[] w2, float[] b2,
                     float[] w3, float[] b3, float[] w4, float[] b4,
                     float mean, float std) {
        this.w1 = w1; this.b1 = b1; this.w2 = w2; this.b2 = b2;
        this.w3 = w3; this.b3 = b3; this.w4 = w4; this.b4 = b4;
        this.mean = mean; this.std = std;
    }

    /** 从 assets/ml_weights.bin 加载；失败返回 null（reason 带原因）。 */
    public static PieceNet load(Context ctx, StringBuilder reason) {
        byte[] raw = readAsset(ctx, "ml_weights.bin");
        if (raw == null) {
            reason.append("找不到 ml_weights.bin");
            return null;
        }
        try {
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            if (bb.get() != 'X' || bb.get() != 'Q' || bb.get() != 'M' || bb.get() != 'L') {
                reason.append("权重文件头不对");
                return null;
            }
            int inCh = bb.getInt(), ch1 = bb.getInt(), ch2 = bb.getInt(), ncls = bb.getInt();
            float mean = bb.getFloat(), std = bb.getFloat();
            if (inCh != 3 || ch1 != CH1 || ch2 != CH2 || ncls != NCLS || std <= 0) {
                reason.append("权重版本不匹配");
                return null;
            }
            float[] w1 = f32(bb, CH1 * 3 * 3 * 3), b1 = f32(bb, CH1);
            float[] w2 = f32(bb, CH2 * CH1 * 3 * 3), b2 = f32(bb, CH2);
            float[] w3 = f32(bb, 48 * CH2 * 64), b3 = f32(bb, 48);
            float[] w4 = f32(bb, NCLS * 48), b4 = f32(bb, NCLS);
            if (bb.hasRemaining()) {
                reason.append("权重文件有多余字节");
                return null;
            }
            return new PieceNet(w1, b1, w2, b2, w3, b3, w4, b4, mean, std);
        } catch (Throwable t) {
            reason.append("解析失败 ").append(t.getClass().getSimpleName());
            return null;
        }
    }

    private static float[] f32(ByteBuffer bb, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = bb.getFloat();
        }
        return a;
    }

    private static byte[] readAsset(Context ctx, String name) {
        try (InputStream is = ctx.getAssets().open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            return bo.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 从彩色 canonical 图上取一个交叉点窗口并识别。
     *
     * @param colorCanon 0xRRGGBB 行优先，尺寸 canonW x canonH
     * @param cx, cy     交叉点在 canonical 中的像素坐标
     * @return softmax 后的最大概率，同时把类别字符写进 outChar（1 个元素）
     */
    public synchronized float classify(int[] colorCanon, int canonW, int canonH,
                                       float cx, float cy, char[] outChar) {
        prepareInput(colorCanon, canonW, canonH, cx, cy);
        forward();
        int arg = argmax();
        float conf = softmax(arg);
        outChar[0] = charOf(arg);
        return conf;
    }

    /** 取以 (cx,cy) 为中心、边长 {@link #IN_WIN} 的窗口，缩放到 32x32 并归一化。 */
    private void prepareInput(int[] src, int W, int H, float cx, float cy) {
        final float scale = IN_WIN / IN;                 // 1.875
        float half = IN_WIN / 2f;
        float x0 = cx - half, y0 = cy - half;
        int k = 0;
        for (int oy = 0; oy < IN; oy++) {
            float sy = y0 + (oy + 0.5f) * scale - 0.5f;
            int yA = clamp((int) Math.floor(sy), 0, H - 1);
            int yB = clamp(yA + 1, 0, H - 1);
            float ty = sy - Math.floor(sy);
            if (ty < 0) ty = 0;
            for (int ox = 0; ox < IN; ox++) {
                float sx = x0 + (ox + 0.5f) * scale - 0.5f;
                int xA = clamp((int) Math.floor(sx), 0, W - 1);
                int xB = clamp(xA + 1, 0, W - 1);
                float tx = sx - Math.floor(sx);
                if (tx < 0) tx = 0;
                int c00 = src[yA * W + xA], c10 = src[yA * W + xB];
                int c01 = src[yB * W + xA], c11 = src[yB * W + xB];
                for (int ch = 0; ch < 3; ch++) {          // R, G, B
                    int sh = 16 - 8 * ch;
                    float v00 = (c00 >> sh) & 0xFF, v10 = (c10 >> sh) & 0xFF;
                    float v01 = (c01 >> sh) & 0xFF, v11 = (c11 >> sh) & 0xFF;
                    float top = v00 + (v10 - v00) * tx;
                    float bot = v01 + (v11 - v01) * tx;
                    float v = top + (bot - top) * ty;
                    in[k++] = v / 255f;
                }
            }
        }
        normalizeIn();
    }

    /** 直接喂一张 32x32 RGB（HWC uint8）——自检用。 */
    private void setRawPatch(byte[] rgbHWC) {
        int k = 0;
        for (int i = 0; i < IN * IN; i++) {
            for (int ch = 0; ch < 3; ch++) {
                int v = rgbHWC[i * 3 + ch] & 0xFF;
                in[k++] = v / 255f;
            }
        }
        normalizeIn();
    }

    private void normalizeIn() {
        // in[] 此刻已是 0..1；训练时归一化为 (x - mean) / std，mean=0.5 std=0.5
        for (int i = 0; i < in.length; i++) {
            in[i] = (in[i] - mean) / std;
        }
    }

    private void forward() {
        // conv1 3x3 pad1 + ReLU
        for (int co = 0; co < CH1; co++) {
            float bias = b1[co];
            for (int y = 0; y < IN; y++) {
                int row = co * IN * IN + y * IN;
                for (int x = 0; x < IN; x++) {
                    float s = bias;
                    for (int ic = 0; ic < 3; ic++) {
                        int inBase = ic * IN * IN;
                        int wBase = ((co * 3 + ic) * 3) * 3;
                        for (int ky = 0; ky < 3; ky++) {
                            int yy = y + ky - 1;
                            if (yy < 0 || yy >= IN) continue;
                            for (int kx = 0; kx < 3; kx++) {
                                int xx = x + kx - 1;
                                if (xx < 0 || xx >= IN) continue;
                                s += w1[wBase + ky * 3 + kx] * in[inBase + yy * IN + xx];
                            }
                        }
                    }
                    float v = s > 0 ? s : 0;
                    c1[row + x] = v;
                }
            }
        }
        // maxpool 2x2 → p1 (8 x 16 x 16)
        pool2(c1, p1, CH1, IN);
        // conv2 3x3 pad1 + ReLU（输入 16x16）
        final int S1 = IN / 2;
        for (int co = 0; co < CH2; co++) {
            float bias = b2[co];
            for (int y = 0; y < S1; y++) {
                int row = co * S1 * S1 + y * S1;
                for (int x = 0; x < S1; x++) {
                    float s = bias;
                    for (int ic = 0; ic < CH1; ic++) {
                        int inBase = ic * S1 * S1;
                        int wBase = ((co * CH1 + ic) * 3) * 3;
                        for (int ky = 0; ky < 3; ky++) {
                            int yy = y + ky - 1;
                            if (yy < 0 || yy >= S1) continue;
                            for (int kx = 0; kx < 3; kx++) {
                                int xx = x + kx - 1;
                                if (xx < 0 || xx >= S1) continue;
                                s += w2[wBase + ky * 3 + kx] * p1[inBase + yy * S1 + xx];
                            }
                        }
                    }
                    float v = s > 0 ? s : 0;
                    c2[row + x] = v;
                }
            }
        }
        // maxpool 2x2 → p2 (16 x 8 x 8)，flatten 顺序与 torch 一致：ch*64 + y*8 + x
        pool2(c2, p2, CH2, S1);
        // fc1
        final int F1 = 48, F0 = CH2 * 64;
        for (int i = 0; i < F1; i++) {
            float s = b3[i];
            int wBase = i * F0;
            for (int j = 0; j < F0; j++) {
                s += w3[wBase + j] * p2[j];
            }
            h1[i] = s > 0 ? s : 0;
        }
        // fc2
        for (int i = 0; i < NCLS; i++) {
            float s = b4[i];
            int wBase = i * F1;
            for (int j = 0; j < F1; j++) {
                s += w4[wBase + j] * h1[j];
            }
            logits[i] = s;
        }
    }

    private static void pool2(float[] src, float[] dst, int ch, int size) {
        int o = 0;
        for (int c = 0; c < ch; c++) {
            int base = c * size * size;
            for (int y = 0; y < size; y += 2) {
                for (int x = 0; x < size; x += 2) {
                    int i0 = base + y * size + x;
                    float a = src[i0], b = src[i0 + 1];
                    float d = src[i0 + size], e = src[i0 + size + 1];
                    float m = a > b ? a : b;
                    if (d > m) m = d;
                    if (e > m) m = e;
                    dst[o++] = m;
                }
            }
        }
    }

    private int argmax() {
        int best = 0;
        for (int i = 1; i < NCLS; i++) {
            if (logits[i] > logits[best]) best = i;
        }
        return best;
    }

    private float softmax(int cls) {
        float max = logits[0];
        for (int i = 1; i < NCLS; i++) {
            if (logits[i] > max) max = logits[i];
        }
        double sum = 0, target = 0;
        for (int i = 0; i < NCLS; i++) {
            double e = Math.exp((logits[i] - max) / 8f);   // 缩小数值范围
            sum += e;
            if (i == cls) target = e;
        }
        return (float) (target / sum);
    }

    /** 类别下标 → Position 字符（14 = 空位）。 */
    public static char charOf(int cls) {
        char c = CLASSES.charAt(cls);
        return c == 'E' ? Position.EMPTY : c;
    }

    // ---------------------------------------------------------------- 自检

    /**
     * 读 assets/ml_selftest.bin，跑一遍推理并与 PC 端记录的 logits 比对。
     * 返回给诊断面板的结论字符串；模型未加载时返回 null。
     */
    public static String selfTest(Context ctx, PieceNet net) {
        if (net == null) {
            return null;
        }
        byte[] raw = readAsset(ctx, "ml_selftest.bin");
        if (raw == null || raw.length < 4 + 12 + 4 + 60) {
            return "自检文件缺失";
        }
        try {
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            if (bb.get() != 'X' || bb.get() != 'Q' || bb.get() != 'S' || bb.get() != 'T') {
                return "自检文件头不对";
            }
            int h = bb.getInt(), w = bb.getInt(), ch = bb.getInt();
            int argExp = bb.getInt();
            float[] lgExp = new float[NCLS];
            for (int i = 0; i < NCLS; i++) {
                lgExp[i] = bb.getFloat();
            }
            byte[] patch = new byte[h * w * ch];
            bb.get(patch);
            net.setRawPatch(patch);
            net.forward();
            float maxDiff = 0;
            for (int i = 0; i < NCLS; i++) {
                float d = Math.abs(net.logits[i] - lgExp[i]);
                if (d > maxDiff) maxDiff = d;
            }
            if (maxDiff < 0.02f && net.argmax() == argExp) {
                return String.format("通过 类别=%s 偏差=%.4f", CLASSES.charAt(argExp), maxDiff);
            }
            return String.format("失败 偏差=%.4f", maxDiff);
        } catch (Throwable t) {
            return "自检异常 " + t.getClass().getSimpleName();
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
