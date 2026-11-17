# -*- coding: utf-8 -*-
"""生成 Click 自适应图标的前景图层（白色 Click 字标，透明底）"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = r"C:\Users\demon\Desktop\click"
RES = os.path.join(ROOT, "app", "src", "main", "res")
SS = 3


def font(size):
    for p in (r"C:\Windows\Fonts\ariblk.ttf", r"C:\Windows\Fonts\arialbd.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def fit(text, target_w):
    f = font(100)
    d = ImageDraw.Draw(Image.new("RGBA", (10, 10)))
    b = d.textbbox((0, 0), text, font=f)
    w = b[2] - b[0]
    if w <= 0:
        return f
    return font(max(8, int(100 * target_w / w)))


def build(size_px, text="Click", target_ratio=0.60):
    big = size_px * SS
    canvas = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    f = fit(text, big * target_ratio)
    d = ImageDraw.Draw(canvas)
    bbox = d.textbbox((0, 0), text, font=f)
    tx = (big - (bbox[2] - bbox[0])) / 2 - bbox[0]
    ty = (big - (bbox[3] - bbox[1])) / 2 - bbox[1] - big * 0.03

    shadow = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ds = ImageDraw.Draw(shadow)
    ds.text((tx + big * 0.004, ty + big * 0.012), text, font=f, fill=(10, 16, 45, 95))
    shadow = shadow.filter(ImageFilter.GaussianBlur(big * 0.02))
    canvas = Image.alpha_composite(canvas, shadow)

    d = ImageDraw.Draw(canvas)
    d.text((tx, ty), text, font=f, fill=(255, 255, 255, 255))
    return canvas.resize((size_px, size_px), Image.LANCZOS)


DENSITIES = [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]
for name, px in DENSITIES:
    out = os.path.join(RES, "mipmap-" + name, "ic_launcher_fg.png")
    build(px).save(out)
print("foreground ok")
