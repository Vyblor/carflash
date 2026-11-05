"""플래시몹 커버 합성기. 테마 배경(수식으로 그림) + 해치 포즈(마스코트 3D 렌더).

창업자 요청(2026-11-05): "각 플래시몹 아이템 커버 이미지도 해치 캐릭터를 넣어서 테마에 맞게 제작해서 넣어줘"

입력: 해치 포즈 1024px 투명 PNG 폴더(ideas/carflash-mascot/out/poses-1024, sheet.html mode=one으로 렌더)
출력: app/src/main/assets/covers/<id>.jpg      (해치 포함, 카드·목록·준비 화면)
      app/src/main/assets/covers/<id>_bg.jpg   (배경만, 홈 큰 타일 — 그 위에 움직이는 해치가 올라간다)

사용: python3 tools/make_covers.py <poses-1024 폴더>
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "covers")
W, H = 1024, 768


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def vgrad(top, bottom, w=W, h=H):
    a, b = hexrgb(top), hexrgb(bottom)
    im = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)))
    return im


def glow(im, xy, r, color, alpha=160):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    x, y = xy
    d.ellipse([x - r, y - r, x + r, y + r], fill=hexrgb(color) + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(r * 0.6))
    im.alpha_composite(layer)


def stars(im, n, rng, area=(0, 0, W, H * 0.6)):
    d = ImageDraw.Draw(im)
    for _ in range(n):
        x = rng.uniform(area[0], area[2]); y = rng.uniform(area[1], area[3])
        r = rng.choice([1, 1, 1.5, 2, 2.5])
        a = rng.randint(120, 255)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, a))


def heart(d, cx, cy, s, fill):
    pts = []
    for i in range(60):
        t = i / 60 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * s, cy - y * s))
    d.polygon(pts, fill=fill)


def bg_camp(rng):
    im = vgrad("#1a1240", "#5a2a3a").convert("RGBA")
    stars(im, 140, rng)
    glow(im, (300, 650), 260, "#ff8a00", 150)
    d = ImageDraw.Draw(im)
    # 산 능선
    d.polygon([(0, 560), (180, 470), (330, 540), (520, 430), (720, 530), (880, 470), (1024, 520), (1024, 768), (0, 768)], fill=(28, 20, 44, 255))
    # 텐트
    for x, s, c in [(120, 1.0, (255, 138, 0)), (420, 0.8, (160, 92, 255))]:
        d.polygon([(x, 700), (x + 120 * s, 560 - 10 * s), (x + 240 * s, 700)], fill=c + (255,))
        d.polygon([(x + 100 * s, 700), (x + 120 * s, 610), (x + 140 * s, 700)], fill=(40, 24, 30, 255))
    # 모닥불
    for i, (c, r) in enumerate([("#ff2d55", 46), ("#ff8a00", 34), ("#ffd60a", 20)]):
        d.polygon([(330 - r, 720), (330, 720 - r * 2.4), (330 + r, 720)], fill=hexrgb(c) + (255,))
    # 전구 줄
    for k in range(2):
        y0 = 120 + k * 70
        pts = [(x, y0 + 60 * math.sin(math.pi * x / W)) for x in range(0, W + 1, 8)]
        d.line(pts, fill=(255, 255, 255, 90), width=2)
        cols = ["#ff2d55", "#ffd60a", "#30d158", "#00e5ff", "#a05cff", "#ff4fd8"]
        for j, x in enumerate(range(30, W, 70)):
            y = y0 + 60 * math.sin(math.pi * x / W) + 10
            glow(im, (x, y), 16, cols[(j + k) % len(cols)], 200)
            d.ellipse([x - 6, y - 6, x + 6, y + 6], fill=hexrgb(cols[(j + k) % len(cols)]) + (255,))
    return im


def bg_romance(rng):
    im = vgrad("#2a1450", "#ff4fd8").convert("RGBA")
    for _ in range(18):
        glow(im, (rng.uniform(0, W), rng.uniform(250, 520)), rng.uniform(20, 50), rng.choice(["#ffd60a", "#ff8a00", "#ffffff", "#ff2d55"]), 110)
    d = ImageDraw.Draw(im)
    # 길(원근)
    d.polygon([(380, 520), (644, 520), (1024, 768), (0, 768)], fill=(30, 22, 52, 255))
    for i in range(6):
        t0 = i / 6; t1 = t0 + 0.07
        y0 = 520 + 248 * t0 ** 1.6; y1 = 520 + 248 * t1 ** 1.6
        w0 = 4 + 18 * t0; w1 = 4 + 18 * t1
        d.polygon([(512 - w0, y0), (512 + w0, y0), (512 + w1, y1), (512 - w1, y1)], fill=(255, 214, 10, 220))
    d.line([(0, 520), (W, 520)], fill=(255, 255, 255, 60), width=2)
    for _ in range(14):
        heart(d, rng.uniform(40, W - 40), rng.uniform(40, 440), rng.uniform(1.2, 3.2), (255, 255, 255, rng.randint(90, 200)))
    return im


def bg_night(rng):
    im = vgrad("#070b24", "#2f2a6b").convert("RGBA")
    stars(im, 220, rng, (0, 0, W, H * 0.8))
    glow(im, (250, 200), 170, "#a05cff", 90)
    d = ImageDraw.Draw(im)
    # 초승달
    moon = Image.new("RGBA", im.size, (0, 0, 0, 0))
    md = ImageDraw.Draw(moon)
    md.ellipse([170, 110, 350, 290], fill=(255, 246, 214, 255))
    md.ellipse([215, 90, 395, 270], fill=(0, 0, 0, 0))
    im.alpha_composite(moon)
    # 도시 실루엣과 창불
    x = 0
    while x < W:
        bw = rng.randint(50, 110); bh = rng.randint(80, 220)
        d.rectangle([x, H - bh, x + bw, H], fill=(14, 14, 34, 255))
        for wy in range(H - bh + 14, H - 10, 22):
            for wx in range(x + 8, x + bw - 8, 18):
                if rng.random() < 0.28:
                    d.rectangle([wx, wy, wx + 7, wy + 9], fill=(255, 214, 120, 200))
        x += bw + 6
    return im


def bg_birthday(rng):
    im = vgrad("#ff2d55", "#ffb347").convert("RGBA")
    glow(im, (760, 260), 300, "#ffd60a", 120)
    d = ImageDraw.Draw(im)
    cols = ["#ffd60a", "#30d158", "#00e5ff", "#a05cff", "#ffffff", "#2f6bff"]
    # 풍선
    for x, y, c in [(110, 210, "#a05cff"), (210, 150, "#00e5ff"), (300, 240, "#ffd60a"), (880, 160, "#30d158"), (960, 260, "#2f6bff")]:
        d.line([(x, y + 70), (x + rng.uniform(-30, 30), y + 300)], fill=(255, 255, 255, 180), width=2)
        d.ellipse([x - 52, y - 66, x + 52, y + 70], fill=hexrgb(c) + (255,))
        d.ellipse([x - 30, y - 46, x - 12, y - 20], fill=(255, 255, 255, 110))
    # 꽃가루
    for _ in range(170):
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        a = rng.uniform(0, math.pi)
        l, w = rng.uniform(8, 18), rng.uniform(4, 7)
        dx, dy = math.cos(a) * l / 2, math.sin(a) * l / 2
        nx, ny = -math.sin(a) * w / 2, math.cos(a) * w / 2
        d.polygon([(x - dx - nx, y - dy - ny), (x + dx - nx, y + dy - ny), (x + dx + nx, y + dy + ny), (x - dx + nx, y - dy + ny)],
                  fill=hexrgb(rng.choice(cols)) + (230,))
    return im


def bg_rain(rng):
    im = vgrad("#0b2a4a", "#0e6f8f").convert("RGBA")
    for _ in range(10):
        glow(im, (rng.uniform(0, W), rng.uniform(120, 420)), rng.uniform(30, 70), rng.choice(["#00e5ff", "#2f6bff", "#30d158", "#ffffff"]), 90)
    d = ImageDraw.Draw(im)
    # 주차장 바닥과 선
    d.rectangle([0, 560, W, H], fill=(16, 40, 60, 255))
    for x in range(60, W, 200):
        d.polygon([(x, 768), (x + 12, 768), (x + 60, 560), (x + 52, 560)], fill=(255, 255, 255, 120))
    # 물웅덩이
    pud = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(pud).ellipse([300, 640, 760, 720], fill=(120, 230, 255, 60))
    im.alpha_composite(pud.filter(ImageFilter.GaussianBlur(6)))
    d = ImageDraw.Draw(im)
    # 빗줄기
    for _ in range(260):
        x, y = rng.uniform(-100, W), rng.uniform(-50, H)
        l = rng.uniform(18, 40)
        d.line([(x, y), (x + l * 0.25, y + l)], fill=(220, 245, 255, rng.randint(70, 160)), width=2)
    return im


COVERS = [
    ("p_camp", bg_camp, "dance", (560, 70), 0.78),
    ("p_romance", bg_romance, "hello", (590, 90), 0.72),
    ("p_night", bg_night, "rest", (560, 130), 0.70),
    ("p_birthday", bg_birthday, "cheer", (460, 100), 0.74),
    ("p_rain", bg_rain, "wait", (560, 110), 0.72),
]


def main(poses):
    os.makedirs(OUT, exist_ok=True)
    for cid, fn, pose, (px, py), scale in COVERS:
        rng = random.Random(cid)
        bg = fn(rng)
        bg.convert("RGB").save(os.path.join(OUT, f"{cid}_bg.jpg"), quality=86)
        hc = Image.open(os.path.join(poses, f"{pose}.png")).convert("RGBA")
        hc = hc.crop(hc.getbbox())
        s = int(H * scale) / hc.height
        hc = hc.resize((int(hc.width * s), int(hc.height * s)), Image.LANCZOS)
        # 바닥 그림자
        sh = Image.new("RGBA", bg.size, (0, 0, 0, 0))
        sd = ImageDraw.Draw(sh)
        cx = px + hc.width // 2
        sd.ellipse([cx - hc.width * 0.32, py + hc.height - 26, cx + hc.width * 0.32, py + hc.height + 8], fill=(0, 0, 0, 120))
        sh = sh.filter(ImageFilter.GaussianBlur(10))
        full = bg.copy()
        full.alpha_composite(sh)
        full.alpha_composite(hc, (px, py))
        full.convert("RGB").save(os.path.join(OUT, f"{cid}.jpg"), quality=86)
        print(cid, pose, file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1])
