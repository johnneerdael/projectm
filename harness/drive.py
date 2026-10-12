"""Renders the preset sample with upstream master and the quad-line branch.

Per preset: upstream 3840x2160, branch 3840x2160 (reference 1024x768), upstream 1182x665 (authored
look), upstream 1920x1080, branch 1920x1080 (reference 1024x768) and branch 1920x1080 with the
reference off (must equal upstream). Frame 239 of each run is kept as a downscaled JPEG; raw frames
are deleted. Results append to results.jsonl.
"""
import concurrent.futures as cf
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
PRESETS = Path("/Users/jneerdael/Scripts/Projectm-TV/core/src/main/assets/presets")
TEXTURES = Path("/Users/jneerdael/Scripts/Projectm-TV/core/src/main/assets/textures")
PCM = HERE / "bass-0.30.f32"
OUT = Path(sys.argv[1])
FIXED = [
    "$$$ Royal - Mashup (103).milk", "$$$ Royal - Mashup (162).milk", "$$$ Royal - Mashup (191).milk",
    "$$$ Royal - Mashup (397).milk", "Flexi - alien complex 03.milk", "Fvese-mvfun2.milk",
    "Goody's Trichromatic Mind Games.milk", "Hexcollie - Virtual LSD (martin shader) newborns of satan.milk",
    "Serge + 158 002.milk", "Serge circles005b.milk", "TonyMilkdrop - I Like Cartoon --- Isosceles edit.milk",
    "TonyMilkdrop - Nuclear [Flexi - help out + alien complex].milk", "fat cancer tour meant t nz+.milk",
    "rce-ordinary - want.milk", "shifter - spincycle(remix) - love at home - q load.milk",
    "suksma - bleuneycombinatoriccitensor.milk", "suksma - penattrition - geiss crossfire shaders nz+.milk",
    "suksma - yin - 395 - Artificial Inspiration (brainstorming - outward) + martin + flexi - elephant mating.milk",
]
ROLES = [
    ("master-2160", "render-master", "3840x2160", None),
    ("branch-2160", "render-branch", "3840x2160", "1024x768"),
    ("authored-665", "render-master", "1182x665", None),
    ("master-1080", "render-master", "1920x1080", None),
    ("branch-1080", "render-branch", "1920x1080", "1024x768"),
    ("branchoff-1080", "render-branch", "1920x1080", "0x0"),
]


def sample():
    names = sorted(p.name for p in PRESETS.glob("*.milk"))
    chosen = names[::8]
    for name in FIXED:
        if name not in chosen:
            chosen.append(name)
    return chosen


def run(index, name):
    folder = OUT / f"{index:04d}"
    done = folder / "result.json"
    if done.exists():
        return json.loads(done.read_text())
    folder.mkdir(parents=True, exist_ok=True)
    preset = PRESETS / name
    result = {"index": index, "preset": name,
              "preset_sha256": hashlib.sha256(preset.read_bytes()).hexdigest(), "roles": {}}
    roles = list(ROLES)
    if index % 10 == 0:
        roles.append(("master-1080-repeat", "render-master", "1920x1080", None))
    for role, binary, size, reference in roles:
        prefix = folder / role
        command = [str(HERE / binary), "--preset", str(preset), "--textures", str(TEXTURES),
                   "--pcm", str(PCM), "--size", size, "--frames", "240", "--capture", "239",
                   "--luma-from", "120", "--out", str(prefix)]
        if reference:
            command += ["--line-reference", reference]
        process = subprocess.run(command, capture_output=True, text=True, timeout=600)
        entry = {"exit": process.returncode, "stderr": process.stderr.strip()[-400:]}
        summary = prefix.with_suffix(".json")
        if summary.exists():
            entry.update(json.loads(summary.read_text()))
        raw = Path(f"{prefix}-f239.rgb")
        if raw.exists():
            width, height = map(int, size.split("x"))
            data = raw.read_bytes()
            entry["frame239_sha256"] = hashlib.sha256(data).hexdigest()
            image = Image.frombytes("RGB", (width, height), data)
            image.resize((960, 540), Image.LANCZOS).save(folder / f"{role}.jpg", quality=88)
            raw.unlink()
        result["roles"][role] = entry
    done.write_text(json.dumps(result))
    return result


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    chosen = sample()
    (OUT / "sample.json").write_text(json.dumps(chosen, indent=1))
    jobs = int(os.environ.get("JOBS", "4"))
    with cf.ThreadPoolExecutor(jobs) as pool, open(OUT / "results.jsonl", "a") as log:
        futures = {pool.submit(run, i, n): n for i, n in enumerate(chosen)}
        for count, future in enumerate(cf.as_completed(futures), 1):
            try:
                log.write(json.dumps(future.result()) + "\n")
            except Exception as error:  # keep going; record the failure
                log.write(json.dumps({"preset": futures[future], "error": repr(error)}) + "\n")
            log.flush()
            if count % 25 == 0:
                print(f"{count}/{len(chosen)}", flush=True)


if __name__ == "__main__":
    main()
