"""Static source screen: how much a preset's picture depends on 1 px lines.

Scores every bundled preset from its saved settings (no rendering): visible main wave (not dots),
enabled custom waves (not dots), enabled shape outlines and motion vectors, weighted up when the
feedback decay keeps what the lines draw. Per-frame code that changes these values is not modelled;
the screen only orders presets for rendering, the renders decide.

usage: screen.py OUT.json
"""
import json
import re
import sys
from pathlib import Path

PRESETS = Path("/Users/jneerdael/Scripts/Projectm-TV/core/src/main/assets/presets")


def number(values, key, default=0.0):
    try:
        return float(values.get(key.lower(), default))
    except ValueError:
        return default


rows = []
for path in sorted(PRESETS.glob("*.milk")):
    values = {}
    for line in path.read_text(errors="replace").splitlines():
        match = re.match(r"\s*([A-Za-z_0-9]+)\s*=\s*([-+0-9.eE]+)", line)
        if match and match.group(1).lower() not in values:
            values[match.group(1).lower()] = match.group(2)
    main = 0.0 if number(values, "bWaveDots") else min(max(number(values, "fWaveAlpha", 0.8), 0.0), 1.0)
    custom = sum(min(max(number(values, f"wavecode_{i}_a", 1.0), 0.0), 1.0)
                 for i in range(4)
                 if number(values, f"wavecode_{i}_enabled") and not number(values, f"wavecode_{i}_bUseDots"))
    outlines = sum(min(max(number(values, f"shapecode_{i}_border_a", 0.1), 0.0), 1.0)
                   for i in range(4) if number(values, f"shapecode_{i}_enabled"))
    vectors = min(max(number(values, "mv_a"), 0.0), 1.0) if number(values, "nMotionVectorsX") > 0 else 0.0
    decay = min(max(number(values, "fDecay", 0.98), 0.0), 1.0)
    persistence = 1.0 + 20.0 * max(decay - 0.95, 0.0)  # 1 at decay <= 0.95, 2 at decay 1
    rows.append({"preset": path.name, "score": (main + custom + outlines + vectors) * persistence,
                 "main": main, "custom": custom, "outlines": outlines, "vectors": vectors, "decay": decay})

rows.sort(key=lambda r: -r["score"])
Path(sys.argv[1]).write_text(json.dumps(rows, indent=1))
print(len(rows), "scored; top:", [r["preset"] for r in rows[:5]])
