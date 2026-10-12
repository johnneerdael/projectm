"""Full-resolution comparisons and 100% crops for the most affected presets.

usage: crops.py ANALYSIS OUT [COUNT]
Re-renders frame 239 at 3840x2160 with upstream master and with this PR (reference 1024x768), plus
upstream at 1182x665, then writes per preset:
  NN-full.jpg  three frames side by side (downscaled to 1280x720 each) with labels above the pixels
  NN-crop.png  the 640x360 4K region with the largest upstream/PR difference, unscaled, side by side
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
PRESETS = Path("/Users/jneerdael/Scripts/Projectm-TV/core/src/main/assets/presets")
TEXTURES = Path("/Users/jneerdael/Scripts/Projectm-TV/core/src/main/assets/textures")
ANALYSIS, OUT = Path(sys.argv[1]), Path(sys.argv[2])
COUNT = int(sys.argv[3]) if len(sys.argv) > 3 else 30
OUT.mkdir(parents=True, exist_ok=True)
font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 26)
small = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 20)

measured = json.loads((ANALYSIS / "measured.json").read_text())
measured.sort(key=lambda m: -m["mean_abs_difference_2160"])
chosen = measured[:COUNT]


def render(binary, preset, size, reference, prefix):
    command = [str(HERE / binary), "--preset", str(PRESETS / preset), "--textures", str(TEXTURES),
               "--pcm", str(HERE / "bass-0.30.f32"), "--size", size, "--frames", "240",
               "--capture", "239", "--out", str(prefix)]
    if reference:
        command += ["--line-reference", reference]
    subprocess.run(command, check=True, capture_output=True)
    width, height = map(int, size.split("x"))
    raw = Path(f"{prefix}-f239.rgb")
    image = Image.frombytes("RGB", (width, height), raw.read_bytes())
    raw.unlink()
    return image


manifest = []
for rank, m in enumerate(chosen, 1):
    work = OUT / "work"
    work.mkdir(exist_ok=True)
    upstream = render("render-master", m["preset"], "3840x2160", None, work / "m")
    branch = render("render-branch", m["preset"], "3840x2160", "1024x768", work / "b")
    authored = render("render-master", m["preset"], "1182x665", None, work / "a")

    # Full frames, labelled outside the pixels.
    header, gap, w, h = 76, 10, 1280, 720
    sheet = Image.new("RGB", (3 * w + 2 * gap, h + header), (24, 24, 24))
    draw = ImageDraw.Draw(sheet)
    draw.text((12, 8), m["preset"], fill=(255, 255, 255), font=font)
    panels = [(upstream, f"upstream master · 3840×2160 · mean luma {m['master_2160']:.3f}"),
              (branch, f"this PR · 3840×2160, reference 1024×768 · mean luma {m['branch_2160']:.3f}"),
              (authored, f"upstream master · 1182×665 (1024×768 area) · mean luma {m['authored']:.3f}")]
    for column, (image, text) in enumerate(panels):
        x = column * (w + gap)
        draw.text((x + 12, 44), text, fill=(205, 205, 205), font=small)
        sheet.paste(image.resize((w, h), Image.LANCZOS), (x, header))
    sheet.save(OUT / f"{rank:02d}-full.jpg", quality=90)

    # 100 % crop where the two 4K frames differ most (640x360 window on a 64 px grid).
    a = np.asarray(upstream, dtype=np.float32)
    b = np.asarray(branch, dtype=np.float32)
    difference = np.abs(a - b).mean(axis=2)
    cw, ch = 640, 360
    integral = difference.cumsum(0).cumsum(1)
    best, best_xy = -1.0, (0, 0)
    for y in range(0, 2160 - ch + 1, 64):
        for x in range(0, 3840 - cw + 1, 64):
            y2, x2 = y + ch - 1, x + cw - 1
            total = integral[y2, x2] - (integral[y - 1, x2] if y else 0) - (integral[y2, x - 1] if x else 0) \
                + (integral[y - 1, x - 1] if x and y else 0)
            if total > best:
                best, best_xy = total, (x, y)
    x, y = best_xy
    crop_header = 60
    crop = Image.new("RGB", (2 * cw + gap, ch + crop_header), (24, 24, 24))
    draw = ImageDraw.Draw(crop)
    draw.text((8, 4), f"{m['preset']} · 100% crop at ({x}, {y}) of 3840×2160", fill=(255, 255, 255), font=small)
    draw.text((8, 32), "upstream master", fill=(205, 205, 205), font=small)
    draw.text((cw + gap + 8, 32), "this PR (reference 1024×768)", fill=(205, 205, 205), font=small)
    crop.paste(upstream.crop((x, y, x + cw, y + ch)), (0, crop_header))
    crop.paste(branch.crop((x, y, x + cw, y + ch)), (cw + gap, crop_header))
    crop.save(OUT / f"{rank:02d}-crop.png")

    manifest.append(dict(m, rank=rank, crop_origin=[x, y], crop_size=[cw, ch]))
    print(rank, m["preset"], flush=True)

(OUT / "manifest.json").write_text(json.dumps(manifest, indent=1))
