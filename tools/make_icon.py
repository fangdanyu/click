# -*- coding: utf-8 -*-
"""生成 Click 的启动图标：渐变底 + 白色 Click 字标"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = r"C:\Users\demon\Desktop\click"
RES = os.path.join(ROOT, "app", "src", "main", "res")

S = 1024
SS = 4  # 超采样倍数

C_TOP = (0, 224, 255)      # #00E0FF
C_MID = (64, 156, 255)     # #409CFF
C_BOT = (124, 92, 255)     # #7C5CFF


def font(size):
    for p in (r"C:\Windows\Fonts\ariblk.ttf", r"C:\Windows\Fonts\arialbd.ttf",
              r"C:\Windows\Fonts\seguisb.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def gradient(size):
    w = size // 16 + 2
    grad = Image.new("RGB", (w, w))
    px = grad.load()
    for y in range(w):
        for x in range(w):
            t = (x + y) / (2.0 * (w - 1))
            if t < 0.5:
                k = t / 0.5
                c = tuple(int(C_TOP[i] + (C_MID[i] - C_TOP[i]) * k) for i in range(3))
            else:
                k = (t - 0.5) / 0.5
                c = tuple(int(C_MID[i] + (C_BOT[i] - C_MID[i]) * k) for i in range(3))
            px[x, y] = c
    return grad.resize((size, size), Image.BICUBIC)


def build(size_px, circular=False):
    big = size_px * SS
    base = gradient(big).convert("RGBA")

    # 左上柔光
    sheen = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(sheen)
    d.ellipse([-big // 3, -big // 2, big // 2, big // 2], fill=110)
    sheen = sheen.filter(ImageFilter.GaussianBlur(big // 8))
    white = Image.new("RGBA", (big, big), (255, 255, 255, 0))
    white.putalpha(sheen)
    base = Image.alpha_composite(base, white)

    # 文字
    f = font(int(big * 0.30))
    text = "Click"
    tw = d.getsize if False else None
    tmp = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    dd = ImageDraw.Draw(tmp)
    bbox = dd.textbbox((0, 0), text, font=f)
    tx = (big - (bbox[2] - bbox[0])) / 2 - bbox[0]
    ty = (big - (bbox[3] - bbox[1])) / 2 - bbox[1] - big * 0.02

    shadow = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ds = ImageDraw.Draw(shadow)
    ds.text((tx + big * 0.004, ty + big * 0.008), text, font=f, fill=(12, 18, 45, 85))
    shadow = shadow.filter(ImageFilter.GaussianBlur(big * 0.022))
    base = Image.alpha_composite(base, shadow)

    dd.text((tx, ty), text, font=f, fill=(255, 255, 255, 255))
    base = Image.alpha_composite(base, tmp)

    # 遮罩
    mask = Image.new("L", (big, big), 0)
    dm = ImageDraw.Draw(mask)
    if circular:
        dm.ellipse([0, 0, big - 1, big - 1], fill=255)
    else:
        dm.rounded_rectangle([0, 0, big - 1, big - 1], radius=int(big * 0.23), fill=255)
    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out.resize((size_px, size_px), Image.LANCZOS)


DENSITIES = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]

for name, px in DENSITIES:
    d = os.path.join(RES, "mipmap-" + name)
    os.makedirs(d, exist_ok=True)
    build(px).save(os.path.join(d, "ic_launcher.png"))
    build(px, circular=True).save(os.path.join(d, "ic_launcher_round.png"))

# 商店 / 文档用大图
build(512).save(os.path.join(ROOT, "icon-512.png"))

# 应用内页头用图（固定位图，避免自适应图标被拉伸）
hdr = os.path.join(RES, "drawable-nodpi")
os.makedirs(hdr, exist_ok=True)
build(256).save(os.path.join(hdr, "ic_header.png"))
print("icons ok")
