# 生成设置页钟表素材：复古做旧表盘 + 三根指针
# 表盘四角透明，只有圆形盘面有内容，直接透出外层 bg_page 的紫色渐变
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = r"C:\Users\Administrator\Desktop\tvbox-src-20261016-1740\app\src\leanback\res\drawable-nodpi"

CHAMPAGNE = (201, 162, 99, 255)      # 香槟金外圈
CHAMPAGNE_D = (146, 110, 58, 255)     # 香槟金暗部
CHAMPAGNE_L = (246, 219, 152, 255)    # 香槟金高光
GOLD = (232, 199, 102, 255)           # 亮金：刻度、数字、指针
GOLD_D = (176, 140, 62, 255)
ROSE = (198, 122, 132, 255)           # 玫瑰金：只给秒针
ROSE_D = (152, 82, 92, 255)
NAVY = (14, 20, 44, 216)              # 内圈深蓝近黑，和紫底拉开层次又不发灰
INK = (232, 199, 102, 255)            # 刻度/数字统一用金


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

    # ---- 外圈：窄香槟金环，金属靠"渐变"而不是"斑驳" ----
    ring = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    rp = ImageDraw.Draw(ring)
    # 外暗内亮，做出圆柱感
    for i in range(int((R - R * 0.845) * 1.0)):
        t = i / max(1.0, (R - R * 0.845))
        col = tuple(int(CHAMPAGNE_D[k] + (CHAMPAGNE_L[k] - CHAMPAGNE_D[k]) * t) for k in range(3)) + (255,)
        rr = R - i
        rp.ellipse([c - rr, c - rr, c + rr, c + rr], outline=col, width=2)
    img.alpha_composite(ring)

    # 左上高光弧 + 右下反光，金属才有"受光"而不是一片色
    hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(hl).arc([c - R * 0.985, c - R * 0.985, c + R * 0.985, c + R * 0.985],
                           start=160, end=300, fill=(255, 244, 214, 200), width=int(S * 0.010))
    hl = hl.filter(ImageFilter.GaussianBlur(S * 0.006))
    img.alpha_composite(hl)
    rl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(rl).arc([c - R * 0.99, c - R * 0.99, c + R * 0.99, c + R * 0.99],
                          start=340, end=120, fill=(255, 236, 190, 90), width=int(S * 0.008))
    rl = rl.filter(ImageFilter.GaussianBlur(S * 0.007))
    img.alpha_composite(rl)

    d = ImageDraw.Draw(img)
    # 金环内侧一道细暗线，把"环"和"盘"切开
    d.ellipse([c - R * 0.845, c - R * 0.845, c + R * 0.845, c + R * 0.845],
              outline=CHAMPAGNE_D, width=int(S * 0.005))

    # ---- 内圈：深蓝近黑，和紫底拉开层次，金色刻度才跳得出来 ----
    inner = R * 0.838
    navy = Image.new("RGBA", (S, S), NAVY)
    nmask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(nmask).ellipse([c - inner, c - inner, c + inner, c + inner], fill=255)
    img.paste(navy, (0, 0), nmask)
    d = ImageDraw.Draw(img)

    # ---- 刻度：细长金色，12 点加粗成方块 ----
    for i in range(60):
        ang = math.radians(i * 6)
        hour = (i % 5 == 0)
        r1 = inner * (0.88 if hour else 0.925)
        r2 = inner * 0.985
        w = (S * 0.0130) if hour else (S * 0.0050)
        col = GOLD if hour else GOLD_D
        pts = []
        for (rr, ww) in ((r1, w), (r1, -w), (r2, -w), (r2, w)):
            pts.append((c + rr * math.sin(ang) + ww * math.cos(ang),
                        c - rr * math.cos(ang) + ww * math.sin(ang)))
        d.polygon(pts, fill=col)

    # ---- 数字：亮金粗体 ----
    f = bold_font(int(S * 0.120))
    for i in range(1, 13):
        ang = math.radians(i * 30 - 90)
        tr = inner * 0.70
        x = c + tr * math.cos(ang)
        y = c + tr * math.sin(ang)
        bb = d.textbbox((0, 0), str(i), font=f)
        d.text((x - (bb[2] - bb[0]) / 2 - bb[0], y - (bb[3] - bb[1]) / 2 - bb[1]),
               str(i), font=f, fill=GOLD)

    # ---- 中心：金色轴帽 ----
    rr = S * 0.026
    d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=GOLD)
    d.ellipse([c - rr * 0.45, c - rr * 0.45, c + rr * 0.45, c + rr * 0.45], fill=GOLD_D)

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
make_hand("ic_clock_hour.png",   length=int(INNER * 0.50), width=int(S * 0.034), color=GOLD)
make_hand("ic_clock_minute.png", length=int(INNER * 0.72), width=int(S * 0.023), color=GOLD)
make_hand("ic_clock_second.png", length=int(INNER * 0.82), width=int(S * 0.011),
          color=ROSE, tail=int(S * 0.07))
print("done ->", OUT)
