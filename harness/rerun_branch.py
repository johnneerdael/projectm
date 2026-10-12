"""Re-renders the branch roles of an existing run (after a branch change) into a copy of it.

usage: rerun_branch.py OLD_RUNS NEW_RUNS
"""
import concurrent.futures as cf
import hashlib, json, shutil, subprocess, sys
from pathlib import Path
from PIL import Image
sys.path.insert(0, str(Path(__file__).resolve().parent))
import drive

OLD, NEW = Path(sys.argv[1]), Path(sys.argv[2])
BRANCH = [r for r in drive.ROLES if r[1] == "render-branch"]
records = [json.loads(l) for l in (OLD / "results.jsonl").read_text().splitlines()]
NEW.mkdir(parents=True, exist_ok=True)

def redo(record):
    if "roles" not in record:
        return record
    old, new = OLD / f"{record['index']:04d}", NEW / f"{record['index']:04d}"
    shutil.copytree(old, new, dirs_exist_ok=True)
    for role, binary, size, reference in BRANCH:
        prefix = new / role
        command = [str(drive.HERE / binary), "--preset", str(drive.PRESETS / record["preset"]), "--textures",
                   str(drive.TEXTURES), "--pcm", str(drive.PCM), "--size", size, "--frames", "240",
                   "--capture", "239", "--luma-from", "120", "--out", str(prefix), "--line-reference", reference]
        process = subprocess.run(command, capture_output=True, text=True, timeout=600)
        entry = {"exit": process.returncode, "stderr": process.stderr.strip()[-400:]}
        summary = prefix.with_suffix(".json")
        raw = Path(f"{prefix}-f239.rgb")
        if not summary.exists() or not raw.exists():
            record["roles"][role] = entry
            continue
        entry.update(json.loads(summary.read_text()))
        width, height = map(int, size.split("x"))
        data = raw.read_bytes()
        entry["frame239_sha256"] = hashlib.sha256(data).hexdigest()
        Image.frombytes("RGB", (width, height), data).resize((960, 540), Image.LANCZOS).save(new / f"{role}.jpg", quality=88)
        raw.unlink()
        record["roles"][role] = entry
    (new / "result.json").write_text(json.dumps(record))
    return record

with cf.ThreadPoolExecutor(6) as pool:
    out = list(pool.map(redo, records))
(NEW / "results.jsonl").write_text("".join(json.dumps(r) + "\n" for r in out))
print(len(out))
