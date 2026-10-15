"""生成模块图标（启动器自适应图标 + 界面按钮图标）。

纯 Pillow 绘制，4 倍超采样后缩放，保证边缘平滑。
重新生成：python tools/gen-icons.py
"""

import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')
SS = 4  # supersample

WHITE = (255, 255, 255, 255)
GREEN_DARK = (11, 90, 56, 255)

DENSITIES = {
    'mdpi': 1.0,
    'hdpi': 1.5,
    'xhdpi': 2.0,
    'xxhdpi': 3.0,
    'xxxhdpi': 4.0,
}


def new_canvas(size_dp):
    px = int(round(size_dp * SS))
    img = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def finish(img, size_dp, out_path):
    px = int(round(size_dp))
    img = img.resize((px, px), Image.LANCZOS)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    img.save(out_path)
    return out_path


def photo_glyph(d, scale, cx, cy, size, stroke, color, with_sun=True):
    """画一个「图片」图标：圆角相框 + 山峰 + 太阳。size = 相框宽度。"""
    s = scale
    half_w = size / 2.0
    half_h = size / 2.0 * 0.82
    x0, y0 = (cx - half_w) * s, (cy - half_h) * s
    x1, y1 = (cx + half_w) * s, (cy + half_h) * s
    radius = size * 0.22 * s
    d.rounded_rectangle([x0, y0, x1, y1], radius=radius, outline=color, width=int(round(stroke * s)))
    inner_x0, inner_y0 = x0 + stroke * s, y0 + stroke * s
    inner_x1, inner_y1 = x1 - stroke * s, y1 - stroke * s

    # 山峰：底边贴住相框内底
    base_y = inner_y1
    peak1 = (inner_x0 + (inner_x1 - inner_x0) * 0.22, inner_y0 + (inner_y1 - inner_y0) * 0.42)
    mid = (inner_x0 + (inner_x1 - inner_x0) * 0.46, inner_y0 + (inner_y1 - inner_y0) * 0.66)
    peak2 = (inner_x0 + (inner_x1 - inner_x0) * 0.62, inner_y0 + (inner_y1 - inner_y0) * 0.46)
    d.polygon([(inner_x0 + (inner_x1 - inner_x0) * 0.06, base_y), peak1, mid, peak2,
               (inner_x1 - (inner_x1 - inner_x0) * 0.04, base_y)], fill=color)

    if with_sun:
        sun_cx = inner_x0 + (inner_x1 - inner_x0) * 0.24
        sun_cy = inner_y0 + (inner_y1 - inner_y0) * 0.22
        r = size * 0.075 * s
        d.ellipse([sun_cx - r, sun_cy - r, sun_cx + r, sun_cy + r], fill=color)


def gradient_background(size_dp):
    px = int(round(size_dp * SS))
    img = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    px_colors = [(23, 114, 75), (8, 61, 40)]
    data = img.load()
    for y in range(px):
        for x in range(px):
            t = (x + y) / (2.0 * (px - 1))
            r = int(px_colors[0][0] + (px_colors[1][0] - px_colors[0][0]) * t)
            g = int(px_colors[0][1] + (px_colors[1][1] - px_colors[0][1]) * t)
            b = int(px_colors[0][2] + (px_colors[1][2] - px_colors[0][2]) * t)
            data[x, y] = (r, g, b, 255)
    return img


def restore_glyph(d, scale, cx, cy, size, stroke, color):
    """「恢复默认」：逆时针圆弧 + 箭头（指向缺口方向）。"""
    s = scale
    r = size / 2.0 * 0.74
    cx_s, cy_s = cx * s, cy * s
    r_s = r * s
    tip_deg = 0.0                     # 箭头尖端在 3 点钟方向
    head_deg = 42.0                   # 箭头在圆周上占据的角度
    head_half_w = size * 0.155

    # 弧：从「箭头根部」顺时针画到缺口另一端
    d.arc([cx_s - r_s, cy_s - r_s, cx_s + r_s, cy_s + r_s],
          start=tip_deg + head_deg, end=300.0,
          fill=color, width=int(round(stroke * s)))

    def pt(deg, radius):
        a = math.radians(deg)
        return (cx_s + radius * math.cos(a) * s, cy_s + radius * math.sin(a) * s)

    tip = pt(tip_deg, r)
    base_l = pt(tip_deg + head_deg, r + head_half_w)
    base_r = pt(tip_deg + head_deg, r - head_half_w)
    d.polygon([tip, base_l, base_r], fill=color)


def build():
    made = []

    # 自适应图标前景 / 背景（108dp 画布）
    for name, dens in DENSITIES.items():
        img, d = new_canvas(108)
        photo_glyph(d, SS, 54, 54, 52, 5.5, WHITE)
        made.append(finish(img, 108 * dens,
                           os.path.join(RES, 'mipmap-%s' % name, 'ic_launcher_foreground.png')))
    for name, dens in DENSITIES.items():
        img = gradient_background(108)
        made.append(finish(img, 108 * dens,
                           os.path.join(RES, 'mipmap-%s' % name, 'ic_launcher_background.png')))

    # 界面按钮图标（24dp）
    for name, dens in DENSITIES.items():
        img, d = new_canvas(24)
        photo_glyph(d, SS, 12, 12, 19.5, 2.0, WHITE)
        made.append(finish(img, 24 * dens,
                           os.path.join(RES, 'drawable-%s' % name, 'ic_pick_image.png')))

    for name, dens in DENSITIES.items():
        img, d = new_canvas(24)
        restore_glyph(d, SS, 12, 12, 18.0, 2.1, WHITE)
        made.append(finish(img, 24 * dens,
                           os.path.join(RES, 'drawable-%s' % name, 'ic_restore.png')))

    # 预览占位图（56dp 显示）
    for name, dens in DENSITIES.items():
        img, d = new_canvas(56)
        photo_glyph(d, SS, 28, 28, 42, 2.4, WHITE, with_sun=True)
        made.append(finish(img, 56 * dens,
                           os.path.join(RES, 'drawable-%s' % name, 'ic_image_placeholder.png')))

    for p in made:
        print(os.path.relpath(p, ROOT))
    print('%d files' % len(made))


if __name__ == '__main__':
    build()
