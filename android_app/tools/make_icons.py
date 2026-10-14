#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 Android 应用图标：红底白色「象」字棋子。

用法: python tools/make_icons.py
输出: app/src/main/res/ 下的 mipmap-* 与 drawable-nodpi
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "app", "src", "main", "res")

RED = (176, 58, 46, 255)
RED_DARK = (123, 43, 34, 255)
CREAM = (250, 245, 235, 255)

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\simsun.ttc",
]


def load_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


def draw_piece(size, round_icon=False):
    """画一个棋子：圆形底 + 白字「象」。"""
    ss = 4  # 超采样，边缘更平滑
    s = size * ss
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if round_icon:
        # 圆形图标：整个画布画一个圆
        d.ellipse([0, 0, s - 1, s - 1], fill=RED_DARK)
        d.ellipse([s * 0.06, s * 0.06, s * 0.94, s * 0.94], fill=RED)
    else:
        # 方形图标：圆角矩形 + 内部圆环，模拟棋子
        r = int(s * 0.22)
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=r, fill=RED_DARK)
        d.ellipse([s * 0.09, s * 0.09, s * 0.91, s * 0.91], fill=RED)
        d.ellipse([s * 0.14, s * 0.14, s * 0.86, s * 0.86], outline=CREAM, width=max(2, int(s * 0.018)))

    # 中央「象」字
    font = load_font(int(s * 0.52))
    text = "象"
    try:
        bbox = d.textbbox((0, 0), text, font=font)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        ox, oy = bbox[0], bbox[1]
    except Exception:
        tw, th, ox, oy = font.getsize(text)[0], font.getsize(text)[1], 0, 0
    d.text(((s - tw) / 2 - ox, (s - th) / 2 - oy), text, font=font, fill=CREAM)

    return img.resize((size, size), Image.LANCZOS)


def ensure(path):
    os.makedirs(path, exist_ok=True)
    return path


def main():
    # 传统图标（API < 26 使用）
    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    for name, px in densities.items():
        folder = ensure(os.path.join(RES, "mipmap-" + name))
        draw_piece(px, round_icon=False).save(os.path.join(folder, "ic_launcher.png"))
        draw_piece(px, round_icon=True).save(os.path.join(folder, "ic_launcher_round.png"))
        print("生成 mipmap-%s/ic_launcher(.round).png  %dpx" % (name, px))

    # 自适应图标前景（API 26+）：108dp 画布，内容限制在中间 72dp 安全区
    fg_size = 432
    fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    inner = draw_piece(int(fg_size * 0.62), round_icon=True)
    off = (fg_size - inner.size[0]) // 2
    fg.paste(inner, (off, off), inner)
    nodpi = ensure(os.path.join(RES, "drawable-nodpi"))
    fg.save(os.path.join(nodpi, "ic_launcher_foreground.png"))
    print("生成 drawable-nodpi/ic_launcher_foreground.png %dpx" % fg_size)


if __name__ == "__main__":
    main()
