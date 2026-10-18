# 生成设置页钟表素材：表盘 + 时针/分针/秒针（透明背景 PNG）
# 表盘数字刻度必须精确，所以用脚本画而不是 AI 生图
import math
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = r"C:\Users\Administrator\Desktop\tvbox-src-20261016-1740\app\src\leanback\res\drawable-nodpi"

GOLD = (200, 160, 42, 255)
GOLD_D = (150, 112, 22, 255)
GOLD_L = (232, 199, 102, 255)
GREEN = (46, 125, 50, 255)
GREEN_D = (24, 82, 28, 255)
DIAL = (253, 251, 243, 255)
DIAL_D = (226, 218, 196, 255)
INK = (26, 26, 26, 255)
RED = (229, 57, 53, 255)


def font(size, bold=True):
    for name in ("msyhbd.ttc" if bold else "msyh.ttc", "simhei.ttf", "arialbd.ttf", "arial.ttf"):
        for root in (r"C:\Windows\Fonts", r"C:\Windows\Fonts\PowerShell"):
            p = os.path.join(root, name)
            if os.path.exists(p):
                try:
                    return ImageFont.truetype(p, size)
                except Exception:
                    pass
    return ImageFont.load_default()


def vertical_gradient(size, top, bottom, radius_ratio=1.0):
    """表盘的径向渐变（中心亮、边缘暗）"""
    w, h = size
    grad = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = grad.load()
    cx, cy = w / 2, h / 2
    maxd = math.hypot(cx, cy)
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy) / maxd
            t = min(1.0, d / radius_ratio)
            r = int(top[0] + (bottom[0] - top[0]) * t)
            g = int(top[1] + (bottom[1] - top[1]) * t)
            b = int(top[2] + (bottom[2] - top[2]) * t)
            px[x, y] = (r, g, b, 255)
    return grad


def make_dial(S=1024):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = S / 2
    R = S * 0.48

    # 投影（柔和外阴影）
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse([c - R, c - R + S * 0.02, c + R, c + R + S * 0.02], fill=(0, 0, 0, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(S * 0.02))
    img.alpha_composite(shadow)

    # 金色外环（双层：外粗内细）
    d.ellipse([c - R, c - R, c + R, c + R], fill=GOLD)
    d.ellipse([c - R * 0.965, c - R * 0.965, c + R * 0.965, c + R * 0.965], fill=GOLD_D)
    # 绿色内衬圈
    d.ellipse([c - R * 0.945, c - R * 0.945, c + R * 0.945, c + R * 0.945], fill=GREEN)
    d.ellipse([c - R * 0.925, c - R * 0.925, c + R * 0.925, c + R * 0.925], fill=GREEN_D)

    # 米白盘面（径向渐变）
    inner = R * 0.90
    dial = vertical_gradient((S, S), DIAL, DIAL_D, 0.85)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([c - inner, c - inner, c + inner, c + inner], fill=255)
    img.paste(dial, (0, 0), mask)

    d = ImageDraw.Draw(img)

    # 高光弧（左上）
    hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(hl).arc([c - inner * 0.94, c - inner * 0.94, c + inner * 0.94, c + inner * 0.94],
                           start=150, end=330, fill=(255, 255, 255, 90), width=int(S * 0.012))
    hl = hl.filter(ImageFilter.GaussianBlur(S * 0.006))
    img.alpha_composite(hl)
    d = ImageDraw.Draw(img)

    # 刻度：12 长 + 48 短
    for i in range(60):
        ang = math.radians(i * 6)
        hour = (i % 5 == 0)
        r1 = inner * (0.80 if hour else 0.86)
        r2 = inner * 0.92
        wdt = int(S * (0.020 if hour else 0.008))
        d.line([(c + r1 * math.sin(ang), c - r1 * math.cos(ang)),
                (c + r2 * math.sin(ang), c - r2 * math.cos(ang))],
               fill=INK if hour else (120, 120, 120, 255), width=wdt)

    # 数字 1-12
    f = font(int(S * 0.115))
    for i in range(1, 13):
        ang = math.radians(i * 30 - 90)
        tr = inner * 0.68
        x = c + tr * math.cos(ang)
        y = c + tr * math.sin(ang)
        bb = d.textbbox((0, 0), str(i), font=f)
        d.text((x - (bb[2] - bb[0]) / 2 - bb[0], y - (bb[3] - bb[1]) / 2 - bb[1]),
               str(i), font=f, fill=INK)

    # 中心轴帽
    d.ellipse([c - S * 0.022, c - S * 0.022, c + S * 0.022, c + S * 0.022], fill=GOLD)
    d.ellipse([c - S * 0.012, c - S * 0.012, c + S * 0.012, c + S * 0.012], fill=INK)

    img.save(os.path.join(OUT, "ic_clock_dial.png"))
    print("ic_clock_dial.png", img.size)


def make_hand(name, length, width, color, tail=0.0, cap=True):
    """竖直朝上的指针，旋转点在图片底部中心；length/width 相对表盘半径的比例由调用方给像素值"""
    W = int(max(width * 3, 24))
    H = int(length + width * 2)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = W / 2
    base_y = H - width  # 旋转点
    tip_y = base_y - length

    # 主体：圆角长条 + 箭头尖
    d.rounded_rectangle([cx - width / 2, tip_y, cx + width / 2, base_y],
                        radius=width / 2, fill=color)
    d.polygon([(cx - width * 0.62, tip_y + width * 0.7),
               (cx + width * 0.62, tip_y + width * 0.7),
               (cx, tip_y - width * 0.55)], fill=color)

    # 尾巴
    if tail > 0:
        d.rounded_rectangle([cx - width * 0.28, base_y, cx + width * 0.28, base_y + tail],
                            radius=width * 0.2, fill=color)

    # 中段装饰环（让指针有金属感）
    if cap:
        r = width * 0.95
        d.ellipse([cx - r, base_y - length * 0.30 - r, cx + r, base_y - length * 0.30 + r],
                  outline=(255, 255, 255, 120), width=max(1, int(width * 0.12)))

    # 底部轴帽
    r = width * 1.15
    d.ellipse([cx - r, base_y - r, cx + r, base_y + r], fill=color)

    img.save(os.path.join(OUT, name))
    print(name, img.size)


S = 1024
R = S * 0.48          # 表盘外半径
INNER = R * 0.90      # 盘面半径

make_dial(S)

# 指针长度以「盘面内可画长度」为基准，和旧版代码比例一致
make_hand("ic_clock_hour.png",   length=int(INNER * 0.50), width=int(S * 0.030), color=INK)
make_hand("ic_clock_minute.png", length=int(INNER * 0.72), width=int(S * 0.021), color=INK)
make_hand("ic_clock_second.png", length=int(INNER * 0.82), width=int(S * 0.010),
          color=RED, tail=int(S * 0.075))
print("done ->", OUT)
