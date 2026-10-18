# 生成设置页钟表素材：复古做旧表盘 + 三根指针
# 表盘四角透明，只有圆形盘面有内容，直接透出外层 bg_page 的紫色渐变
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = r"C:\Users\Administrator\Desktop\tvbox-src-20261016-1740\app\src\leanback\res\drawable-nodpi"

PAPER = (243, 228, 206, 255)      # 米黄做旧纸
PAPER_D = (226, 205, 175, 255)
BLUSH = (232, 196, 196, 255)      # 外圈粉色做旧
BLUSH_D = (214, 168, 168, 255)
CREAM = (250, 244, 228, 255)      # 米白细环
INK = (43, 33, 24, 255)           # 深褐黑


def italic_font(size):
    for name in ("timesi.ttf", "georgiai.ttf", "Gabriola.ttf"):
        p = os.path.join(r"C:\Windows\Fonts", name)
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()


def bold_font(size):
    for name in ("timesbd.ttf", "georgiab.ttf", "msyhbd.ttc", "simhei.ttf"):
        p = os.path.join(r"C:\Windows\Fonts", name)
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()


def grain(size, seed, blur=6, strength=1.0):
    """低频斑驳纹理（做旧的关键）"""
    rng = np.random.default_rng(seed)
    small = rng.random((max(4, size // 16), max(4, size // 16)))
    img = Image.fromarray((small * 255).astype("uint8"), "L")
    img = img.resize((size, size), Image.BICUBIC).filter(ImageFilter.GaussianBlur(blur))
    arr = np.asarray(img, dtype=np.float32) / 255.0
    return arr * strength


def vignette(size, inner=0.72, dark=0.22):
    """边缘暗角"""
    y, x = np.mgrid[0:size, 0:size]
    d = np.hypot(x - size / 2, y - size / 2) / (size / 2)
    t = np.clip((d - inner) / (1.0 - inner), 0, 1)
    return (t * dark).astype(np.float32)


def paper_layer(size, base, seed, blotch=0.16, vig=0.18):
    """米黄做旧纸：底色 + 低频褐斑 + 高频噪点 + 暗角"""
    r, g, b = base[:3]
    dark = grain(size, seed, blur=10, strength=1.0)
    fine = grain(size, seed + 99, blur=1, strength=1.0)
    v = vignette(size, dark=vig)
    shade = dark * blotch + fine * 0.05 + v
    arr = np.zeros((size, size, 3), dtype=np.float32)
    for i, c in enumerate((r, g, b)):
        arr[:, :, i] = np.clip(c * (1.0 - shade) + (c * 0.55) * shade, 0, 255)
    out = Image.fromarray(arr.astype("uint8"), "RGB").convert("RGBA")
    return out


def make_dial(S=1024):
    c = S / 2
    R = S * 0.485
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # ---- 外圈：粉色做旧纸 ----
    blush = paper_layer(S, BLUSH, seed=11, blotch=0.20, vig=0.20)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([c - R, c - R, c + R, c + R], fill=255)
    img.paste(blush, (0, 0), mask)

    # ---- 米白细环 ----
    d = ImageDraw.Draw(img)
    d.ellipse([c - R * 0.945, c - R * 0.945, c + R * 0.945, c + R * 0.945], fill=CREAM)
    d.ellipse([c - R * 0.925, c - R * 0.925, c + R * 0.925, c + R * 0.925], fill=BLUSH_D)

    # ---- 刻度带：黑白相间小竖条 ----
    inner = R * 0.845
    for i in range(60):
        ang = math.radians(i * 6)
        hour = (i % 5 == 0)
        r1 = inner * (0.86 if hour else 0.92)
        r2 = inner * 1.0
        w = (S * 0.0125) if hour else (S * 0.0072)
        col = INK if (i % 2 == 0) else (252, 250, 244, 255)
        pts = []
        for (rr, ww) in ((r1, w), (r1, -w), (r2, -w), (r2, w)):
            pts.append((c + rr * math.sin(ang) + ww * math.cos(ang),
                        c - rr * math.cos(ang) + ww * math.sin(ang)))
        d.polygon(pts, fill=col)

    # ---- 米黄盘面 ----
    dial = paper_layer(S, PAPER, seed=23, blotch=0.15, vig=0.20)
    dmask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(dmask).ellipse([c - inner * 0.86, c - inner * 0.86,
                                   c + inner * 0.86, c + inner * 0.86], fill=255)
    img.paste(dial, (0, 0), dmask)
    d = ImageDraw.Draw(img)

    # ---- 斜体数字 1-12 ----
    f = italic_font(int(S * 0.135))
    for i in range(1, 13):
        ang = math.radians(i * 30 - 90)
        tr = inner * 0.70
        x = c + tr * math.cos(ang)
        y = c + tr * math.sin(ang)
        bb = d.textbbox((0, 0), str(i), font=f)
        # 轻微倾斜，强化参考图的手写斜体感
        d.text((x - (bb[2] - bb[0]) / 2 - bb[0], y - (bb[3] - bb[1]) / 2 - bb[1]),
               str(i), font=f, fill=INK)

    # ---- 中心小圆环 ----
    rr = S * 0.030
    d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=INK, width=int(S * 0.006))
    d.ellipse([c - rr * 0.42, c - rr * 0.42, c + rr * 0.42, c + rr * 0.42], fill=INK)

    img.save(os.path.join(OUT, "ic_clock_dial.png"))
    print("ic_clock_dial.png", img.size, "四角透明")


def make_hand(name, length, width, color, tail=0.0):
    """竖直朝上的指针，旋转点在图片底部中心"""
    W = int(max(width * 3, 24))
    H = int(length + width * 2)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = W / 2
    base_y = H - width
    tip_y = base_y - length

    d.rounded_rectangle([cx - width / 2, tip_y, cx + width / 2, base_y],
                        radius=width / 2, fill=color)
    d.polygon([(cx - width * 0.60, tip_y + width * 0.7),
               (cx + width * 0.60, tip_y + width * 0.7),
               (cx, tip_y - width * 0.50)], fill=color)
    if tail > 0:
        d.rounded_rectangle([cx - width * 0.30, base_y, cx + width * 0.30, base_y + tail],
                            radius=width * 0.2, fill=color)
    r = width * 1.1
    d.ellipse([cx - r, base_y - r, cx + r, base_y + r], fill=color)

    img.save(os.path.join(OUT, name))
    print(name, img.size)


S = 1024
R = S * 0.485
INNER = R * 0.845

make_dial(S)
make_hand("ic_clock_hour.png",   length=int(INNER * 0.50), width=int(S * 0.026), color=INK)
make_hand("ic_clock_minute.png", length=int(INNER * 0.72), width=int(S * 0.018), color=INK)
make_hand("ic_clock_second.png", length=int(INNER * 0.82), width=int(S * 0.009),
          color=INK, tail=int(S * 0.07))
print("done ->", OUT)
