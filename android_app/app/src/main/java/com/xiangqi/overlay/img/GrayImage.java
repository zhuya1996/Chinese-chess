package com.xiangqi.overlay.img;

/**
 * 极简灰度图容器。
 * <p>
 * 替代 OpenCV 的 Mat（单通道 8 位），配合 {@link ImageOps} 完成本项目所需的
 * 全部图像处理。使用 int 存灰度（0-255），避免符号问题，单帧 480x540 只有约 1MB。
 */
public final class GrayImage {

    public final int width;
    public final int height;
    /** 行优先存储，data[y * width + x]，取值 0-255。 */
    public final int[] data;

    public GrayImage(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid size " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.data = new int[width * height];
    }

    public int get(int x, int y) {
        return data[y * width + x];
    }

    public void set(int x, int y, int v) {
        data[y * width + x] = v;
    }

    public int size() {
        return width * height;
    }

    /** 旋转 180 度（用于黑方在下的棋盘）。 */
    public GrayImage rotate180() {
        GrayImage out = new GrayImage(width, height);
        for (int y = 0; y < height; y++) {
            int sy = height - 1 - y;
            for (int x = 0; x < width; x++) {
                out.data[y * width + x] = data[sy * width + (width - 1 - x)];
            }
        }
        return out;
    }

    /** 裁剪一个子区域（越界部分用 0 填充）。 */
    public GrayImage crop(int left, int top, int w, int h) {
        GrayImage out = new GrayImage(w, h);
        for (int y = 0; y < h; y++) {
            int sy = y + top;
            for (int x = 0; x < w; x++) {
                int sx = x + left;
                if (sx >= 0 && sx < width && sy >= 0 && sy < height) {
                    out.data[y * w + x] = data[sy * width + sx];
                }
            }
        }
        return out;
    }
}
