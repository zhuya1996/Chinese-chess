package com.xiangqi.overlay;

import android.content.Context;

import com.xiangqi.overlay.img.GrayImage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标定档案：棋盘四角 + 棋子模板 + 空位模板 + 行棋方指示区。
 * <p>
 * 四角使用<b>相对坐标</b>（0-1，相对整屏宽高），因此换分辨率、换机型都不用重标。
 * 模板以原始灰度字节落盘（每张 52x52 约 2.7KB，共 30 张左右），读写开销可忽略。
 */
public final class BoardProfile {

    public static final String DIR_NAME = "xiangqi_profile";

    /** 四角相对坐标，顺序：左上、右上、右下、左下。 */
    public final float[] cornersRel = new float[8];

    /** 屏幕下方是哪种颜色（即通常意义上「我」所在的一侧）。 */
    public String bottomSide = "red";

    /** 行棋方指示区（相对整屏的 left/top/right/bottom），用于辅助判断轮到谁。 */
    public final float[] turnTop = new float[4];
    public final float[] turnBottom = new float[4];

    public long calibratedAt;

    /** 棋子模板：棋子字符 -> 该类棋子在初始局面各位置的模板列表。 */
    public final Map<String, List<GrayImage>> pieceTemplates = new LinkedHashMap<>();
    public final List<GrayImage> emptyTemplates = new ArrayList<>();

    /**
     * v2.2：交叉点「有没有子」的线性判别模型。
     * <p>
     * 为什么需要它：旧做法是拿每个点与「平均空位模板」比相似度，靠 0.95 阈值判空。
     * 但新皮肤（JJ象棋「立体瓷砖」棋盘）每格都带浮雕边、明暗纹理各异，空位与非空位
     * 的相似度区间直接重叠（实测空格 0.19~0.96、有子 ≤0.42），阈值怎么调都会误判，
     * 表现就是「棋子数量不合法」。改为在标定时从真实像素里学一个二维线性判别：
     * <pre>
     *   feature = (中心窗口平均亮度, 中心窗口平均 R-B)
     *   有子  <=>  occW0 * f0 + occW1 * f1 >= occThreshold
     * </pre>
     * 真机截图实测：有子投影 1.8~9.0、空格 -4.7~-3.5，零误判，且网格整体偏移 8px
     * 仍然零误判（比像素级 NCC 稳得多）。
     */
    public double occW0;
    public double occW1;
    public double occThreshold;
    public boolean occReady;

    public boolean isCalibrated() {
        return calibratedAt > 0 && !pieceTemplates.isEmpty() && !emptyTemplates.isEmpty();
    }

    public void clear() {
        for (int i = 0; i < cornersRel.length; i++) {
            cornersRel[i] = 0;
        }
        pieceTemplates.clear();
        emptyTemplates.clear();
        occReady = false;
        occW0 = occW1 = occThreshold = 0;
        calibratedAt = 0;
    }

    /** 相对角点 → 指定帧尺寸下的绝对像素角点。 */
    public double[] absoluteCorners(int frameW, int frameH) {
        double[] out = new double[8];
        for (int i = 0; i < 4; i++) {
            out[2 * i] = cornersRel[2 * i] * frameW;
            out[2 * i + 1] = cornersRel[2 * i + 1] * frameH;
        }
        return out;
    }

    /** 把指示区相对坐标换算成像素矩形，返回 {left, top, right, bottom}。 */
    public int[] absoluteRect(float[] rel, int frameW, int frameH) {
        return new int[]{
                (int) (rel[0] * frameW),
                (int) (rel[1] * frameH),
                (int) (rel[2] * frameW),
                (int) (rel[3] * frameH)
        };
    }

    // ------------------------------------------------------------ 持久化

    private static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), DIR_NAME);
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public boolean save(Context ctx) {
        try {
            File d = dir(ctx);
            // 先清掉旧的模板文件
            File[] old = d.listFiles();
            if (old != null) {
                for (File f : old) {
                    if (f.getName().endsWith(".raw")) {
                        f.delete();
                    }
                }
            }

            List<String> pieceFiles = new ArrayList<>();
            for (Map.Entry<String, List<GrayImage>> e : pieceTemplates.entrySet()) {
                List<GrayImage> list = e.getValue();
                for (int i = 0; i < list.size(); i++) {
                    String name = "piece_" + e.getKey() + "_" + i + ".raw";
                    writeTemplate(new File(d, name), list.get(i));
                    pieceFiles.add(name);
                }
            }
            List<String> emptyFiles = new ArrayList<>();
            for (int i = 0; i < emptyTemplates.size(); i++) {
                String name = "empty_" + i + ".raw";
                writeTemplate(new File(d, name), emptyTemplates.get(i));
                emptyFiles.add(name);
            }

            JSONObject o = new JSONObject();
            JSONArray c = new JSONArray();
            for (float v : cornersRel) {
                c.put(v);
            }
            o.put("corners", c);
            o.put("bottomSide", bottomSide);
            o.put("turnTop", toJSONArray(turnTop));
            o.put("turnBottom", toJSONArray(turnBottom));
            o.put("calibratedAt", calibratedAt);
            o.put("pieceFiles", new JSONArray(pieceFiles));
            o.put("emptyFiles", new JSONArray(emptyFiles));
            o.put("occ", toJSONArray(new float[]{(float) occW0, (float) occW1, (float) occThreshold}));

            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(new File(d, "profile.json"))));
            out.write(o.toString().getBytes("UTF-8"));
            out.close();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean load(Context ctx) {
        try {
            File d = dir(ctx);
            File json = new File(d, "profile.json");
            if (!json.exists()) {
                return false;
            }
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(json)));
            byte[] buf = new byte[(int) json.length()];
            in.readFully(buf);
            in.close();
            JSONObject o = new JSONObject(new String(buf, "UTF-8"));

            clear();
            JSONArray c = o.optJSONArray("corners");
            if (c != null) {
                for (int i = 0; i < 8 && i < c.length(); i++) {
                    cornersRel[i] = (float) c.optDouble(i, 0);
                }
            }
            bottomSide = o.optString("bottomSide", "red");
            readInto(o.optJSONArray("turnTop"), turnTop);
            readInto(o.optJSONArray("turnBottom"), turnBottom);
            calibratedAt = o.optLong("calibratedAt", 0);
            // v2.2：占用模型（旧存档没有这一项 → occReady=false，自动退回旧做法）
            JSONArray occ = o.optJSONArray("occ");
            if (occ != null && occ.length() >= 3) {
                occW0 = occ.optDouble(0, 0);
                occW1 = occ.optDouble(1, 0);
                occThreshold = occ.optDouble(2, 0);
                occReady = Math.abs(occW0) + Math.abs(occW1) > 1e-6;
            }

            JSONArray pf = o.optJSONArray("pieceFiles");
            if (pf != null) {
                for (int i = 0; i < pf.length(); i++) {
                    String name = pf.optString(i, "");
                    String[] seg = name.split("_");
                    if (seg.length < 3) {
                        continue;
                    }
                    GrayImage g = readTemplate(new File(d, name));
                    if (g != null) {
                        String key = seg[1];
                        List<GrayImage> list = pieceTemplates.get(key);
                        if (list == null) {
                            list = new ArrayList<>();
                            pieceTemplates.put(key, list);
                        }
                        list.add(g);
                    }
                }
            }
            JSONArray ef = o.optJSONArray("emptyFiles");
            if (ef != null) {
                for (int i = 0; i < ef.length(); i++) {
                    GrayImage g = readTemplate(new File(d, ef.optString(i, "")));
                    if (g != null) {
                        emptyTemplates.add(g);
                    }
                }
            }
            return isCalibrated();
        } catch (Exception ex) {
            return false;
        }
    }

    private static JSONArray toJSONArray(float[] v) {
        JSONArray a = new JSONArray();
        for (float x : v) {
            // 注意：必须显式装箱成 Double，走 put(Object) 重载。
            // 若直接传 float，会精确匹配到 put(double)，而这个重载会抛受检的
            // JSONException，导致本方法无法编译（除非再加 try-catch）。
            a.put(Double.valueOf(x));
        }
        return a;
    }

    private static void readInto(JSONArray a, float[] out) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < out.length && i < a.length(); i++) {
            out[i] = (float) a.optDouble(i, 0);
        }
    }

    private static void writeTemplate(File f, GrayImage g) throws Exception {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
        out.writeInt(g.width);
        out.writeInt(g.height);
        for (int i = 0; i < g.data.length; i++) {
            out.writeByte(g.data[i]);
        }
        out.close();
    }

    private static GrayImage readTemplate(File f) {
        if (!f.exists()) {
            return null;
        }
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
            int w = in.readInt();
            int h = in.readInt();
            if (w <= 0 || h <= 0 || w * h > 4_000_000) {
                in.close();
                return null;
            }
            GrayImage g = new GrayImage(w, h);
            for (int i = 0; i < g.data.length; i++) {
                g.data[i] = in.readByte() & 0xFF;
            }
            in.close();
            return g;
        } catch (Exception ex) {
            return null;
        }
    }
}
