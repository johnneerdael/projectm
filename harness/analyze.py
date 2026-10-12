"""Summarizes the render run and builds comparison images for presets with clear differences.

usage: analyze.py RUNS OUT
"""
import json
import statistics
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

RUNS, OUT = Path(sys.argv[1]), Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)

rows = []
for line in (RUNS / "results.jsonl").read_text().splitlines():
    record = json.loads(line)
    if "error" in record:
        rows.append(record)
        continue
    rows.append(record)


def luma(record, role):
    return record["roles"].get(role, {}).get("mean_luma", -1.0)


def ok(record):
    return "roles" in record and all(v.get("exit") == 0 and v.get("gl_error_frames", 1) == 0
                                     for v in record["roles"].values())


def image(record, role):
    return np.asarray(Image.open(RUNS / f"{record['index']:04d}" / f"{role}.jpg").convert("RGB"), dtype=np.float32)


summary = {"presets": len(rows), "failed": [], "identity_mismatch": [], "repeat_mismatch": [],
           "repeat_checked": 0, "identity_checked": 0}
measured = []
for record in rows:
    if not ok(record):
        summary["failed"].append({"preset": record.get("preset"), "detail": record.get("error") or
                                  {k: v.get("stderr") for k, v in record["roles"].items() if v.get("exit")}})
        continue
    roles = record["roles"]
    summary["identity_checked"] += 1
    if roles["master-1080"]["frame239_sha256"] != roles["branchoff-1080"]["frame239_sha256"]:
        summary["identity_mismatch"].append(record["preset"])
    if "master-1080-repeat" in roles:
        summary["repeat_checked"] += 1
        if roles["master-1080"]["frame239_sha256"] != roles["master-1080-repeat"]["frame239_sha256"]:
            summary["repeat_mismatch"].append(record["preset"])
    authored = luma(record, "authored-665")
    if authored < 0.02:  # nearly black at the authored size: brightness ratios are meaningless
        continue
    master4k, branch4k = luma(record, "master-2160"), luma(record, "branch-2160")
    master1080, branch1080 = luma(record, "master-1080"), luma(record, "branch-1080")
    difference = float(np.mean(np.abs(image(record, "master-2160") - image(record, "branch-2160")))) / 255
    measured.append({"index": record["index"], "preset": record["preset"], "authored": authored,
                     "master_2160": master4k, "branch_2160": branch4k,
                     "master_1080": master1080, "branch_1080": branch1080,
                     "ratio_master_2160": master4k / authored, "ratio_branch_2160": branch4k / authored,
                     "ratio_master_1080": master1080 / authored, "ratio_branch_1080": branch1080 / authored,
                     "mean_abs_difference_2160": difference})


def within(values, tolerance=0.10):
    return sum(abs(v - 1) <= tolerance for v in values)


def stats(key):
    values = [m[key] for m in measured]
    return {"median": statistics.median(values), "within_10pct": within(values),
            "below_0.5": sum(v < 0.5 for v in values), "above_1.5": sum(v > 1.5 for v in values)}


summary["measured"] = len(measured)
for key in ("ratio_master_2160", "ratio_branch_2160", "ratio_master_1080", "ratio_branch_1080"):
    summary[key] = stats(key)
summary["closer_to_authored_2160"] = sum(abs(m["ratio_branch_2160"] - 1) < abs(m["ratio_master_2160"] - 1) for m in measured)
summary["further_from_authored_2160"] = sum(abs(m["ratio_branch_2160"] - 1) > abs(m["ratio_master_2160"] - 1) for m in measured)
(OUT / "summary.json").write_text(json.dumps(summary, indent=1))
(OUT / "measured.json").write_text(json.dumps(measured, indent=1))

# Candidates for the gallery: a clear brightness change at 4K and a clear pixel difference.
candidates = [m for m in measured
              if m["mean_abs_difference_2160"] >= 0.04
              and max(m["branch_2160"], m["master_2160"]) / max(min(m["branch_2160"], m["master_2160"]), 1e-4) >= 1.4]
candidates.sort(key=lambda m: -m["mean_abs_difference_2160"])
(OUT / "candidates.json").write_text(json.dumps(candidates, indent=1))

try:
    font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 22)
    small = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 18)
except OSError:
    font = small = ImageFont.load_default()

labels = [("master-2160", "upstream master · 3840×2160"),
          ("branch-2160", "this PR · 3840×2160 · reference 1024×768"),
          ("authored-665", "upstream master · 1182×665 (Milkdrop's 1024×768 area)")]
sheet_dir = OUT / "comparisons"
sheet_dir.mkdir(exist_ok=True)
for m in candidates:
    header, gap = 64, 8
    canvas = Image.new("RGB", (3 * 960 + 2 * gap, 540 + header), (24, 24, 24))
    draw = ImageDraw.Draw(canvas)
    draw.text((10, 6), m["preset"], fill=(255, 255, 255), font=font)
    for column, (role, text) in enumerate(labels):
        x = column * (960 + gap)
        value = {"master-2160": m["master_2160"], "branch-2160": m["branch_2160"], "authored-665": m["authored"]}[role]
        draw.text((x + 10, 36), f"{text} · mean luma {value:.3f}", fill=(200, 200, 200), font=small)
        canvas.paste(Image.open(RUNS / f"{m['index']:04d}" / f"{role}.jpg"), (x, header))
    canvas.save(sheet_dir / f"{m['index']:04d}.jpg", quality=88)

print(json.dumps({k: summary[k] for k in summary if not isinstance(summary[k], list)}, indent=1))
print("failed", len(summary["failed"]), "identity mismatches", len(summary["identity_mismatch"]),
      "repeat mismatches", len(summary["repeat_mismatch"]), "candidates", len(candidates))
