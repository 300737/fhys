# 生成设置页钟表素材：复古做旧表盘 + 三根指针
# 表盘四角透明，只有圆形盘面有内容，直接透出外层 bg_page 的紫色渐变
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = r"C:\Users\Administrator\Desktop\tvbox-src-20261016-1740\app\src\leanback\res\drawable-nodpi"

CHAMPAGNE = (226, 197, 150, 255)      # 香槟金外圈
CHAMPAGNE_D = (183, 148, 98, 255)     # 香槟金暗部
CHAMPAGNE_L = (248, 232, 198, 255)    # 香槟金高光
ROSE = (183, 110, 121, 255)           # 玫瑰金（指针）
ROSE_D = (148, 82, 94, 255)
ROSE_L = (214, 152, 160, 255)
FROST = (244, 240, 236, 178)          # 内圈磨砂：半透明浅色，深蓝会透上来
INK = (62, 48, 40, 255)               # 深褐刻度/数字


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

    # ---- 外圈：香槟金（带做旧斑驳与暗角，金属不至于太"塑料"）----
    gold = paper_layer(S, CHAMPAGNE, seed=11, blotch=0.14, vig=0.16)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([c - R, c - R, c + R, c + R], fill=255)
    img.paste(gold, (0, 0), mask)

    d = ImageDraw.Draw(img)
    # 外圈内缘压一道暗金，做出"圈"的厚度
    d.ellipse([c - R * 0.955, c - R * 0.955, c + R * 0.955, c + R * 0.955],
              fill=CHAMPAGNE_D)
    # 高光环：左上受光
    hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(hl).arc([c - R * 0.97, c - R * 0.97, c + R * 0.97, c + R * 0.97],
                           start=150, end=330, fill=CHAMPAGNE_L, width=int(S * 0.014))
    hl = hl.filter(ImageFilter.GaussianBlur(S * 0.008))
    img.alpha_composite(hl)
    d = ImageDraw.Draw(img)

    # ---- 内圈：磨砂半透明浅色，深蓝背景会透上来 ----
    inner = R * 0.845
    frost = Image.new("RGBA", (S, S), FROST)
    # 磨砂颗粒
    rng = np.random.default_rng(7)
    noise = (rng.random((S, S)) * 26).astype("uint8")
    frost.putalpha(Image.fromarray(noise, "L").point(lambda v: FROST[3]))
    # 用噪点调制 alpha，得到不均匀的磨砂质感
    fa = np.asarray(frost.split()[3], dtype=np.float32) + (np.asarray(noise, dtype=np.float32) - 13)
    frost.putalpha(Image.fromarray(np.clip(fa, 0, 255).astype("uint8"), "L"))
    fmask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(fmask).ellipse([c - inner, c - inner, c + inner, c + inner], fill=255)
    img.paste(frost, (0, 0), fmask)
    d = ImageDraw.Draw(img)

    # ---- 刻度带：深褐 + 玫瑰金相间 ----
    for i in range(60):
        ang = math.radians(i * 6)
        hour = (i % 5 == 0)
        r1 = inner * (0.86 if hour else 0.92)
        r2 = inner * 0.995
        w = (S * 0.0125) if hour else (S * 0.0072)
        col = ROSE_D if hour else INK
        pts = []
        for (rr, ww) in ((r1, w), (r1, -w), (r2, -w), (r2, w)):
            pts.append((c + rr * math.sin(ang) + ww * math.cos(ang),
                        c - rr * math.cos(ang) + ww * math.sin(ang)))
        d.polygon(pts, fill=col)

    # ---- 数字 1-12：深褐粗体 ----
    f = bold_font(int(S * 0.125))
    for i in range(1, 13):
        ang = math.radians(i * 30 - 90)
        tr = inner * 0.70
        x = c + tr * math.cos(ang)
        y = c + tr * math.sin(ang)
        bb = d.textbbox((0, 0), str(i), font=f)
        d.text((x - (bb[2] - bb[0]) / 2 - bb[0], y - (bb[3] - bb[1]) / 2 - bb[1]),
               str(i), font=f, fill=INK)

    # ---- 中心：玫瑰金轴帽 ----
    rr = S * 0.028
    d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=ROSE)
    d.ellipse([c - rr * 0.45, c - rr * 0.45, c + rr * 0.45, c + rr * 0.45], fill=ROSE_D)

    img.save(os.path.join(OUT, "ic_clock_dial.png"))
    print("ic_clock_dial.png", img.size, "四角透明，内圈 alpha=178 半透明")


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
make_hand("ic_clock_hour.png",   length=int(INNER * 0.50), width=int(S * 0.026), color=ROSE_D)
make_hand("ic_clock_minute.png", length=int(INNER * 0.72), width=int(S * 0.018), color=ROSE)
make_hand("ic_clock_second.png", length=int(INNER * 0.82), width=int(S * 0.0115),
          color=ROSE_D, tail=int(S * 0.07))
print("done ->", OUT)
