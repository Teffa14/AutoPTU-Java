"""Freeze creation inputs/results by calling the actual Python oracle, without modifying it."""
import argparse
import hashlib
import json
from pathlib import Path
import random
import subprocess
import sys
from types import SimpleNamespace

parser = argparse.ArgumentParser()
parser.add_argument("--python-root", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
sys.path.insert(0, str(args.python_root.resolve()))
from auto_ptu.random_campaign import CsvRandomCampaignBuilder
from auto_ptu.natures import load_nature_table, pick_random_nature_name, apply_nature_to_spec
from auto_ptu.foundry_loader import pick_abilities_for_level
from auto_ptu.ptu_engine import Pokemon

keys = ["hp_stat", "atk", "defense", "spatk", "spdef", "spd"]
table = load_nature_table(args.python_root / "files")
builder = object.__new__(CsvRandomCampaignBuilder)
profiles = [[], [("Physical", 6, "At-Will")], [("Special", 8, "EOT")],
            [("Physical", 5, "Scene"), ("Special", 7, "EOT"), ("Status", 0, "Daily")],
            [("Status", 0, "At-Will"), ("Physical", 0, "Free")],
            [("Physical", 6, "Scene"), ("Physical", 7, "Daily"), ("Special", 8, "Shift")]]
bases = [[5, 5, 5, 7, 7, 5], [4, 5, 4, 6, 5, 7], [5, 5, 7, 5, 6, 4],
         [1, 1, 1, 1, 1, 1], [9, 12, 8, 4, 6, 13], [15, 3, 15, 4, 14, 2]]
pools = [dict(starting=[], basic=["Confidence", "Photosynthesis"], advanced=["Chlorophyll"], high=["Life Force"]),
         dict(starting=["Start", "Start"], basic=["Basic", "Start"], advanced=["Basic", "Advanced"], high=["High"]),
         dict(starting=[], basic=[], advanced=["Fallback"], high=["Last"]),
         dict(starting=[], basic=[], advanced=[], high=[])]

def numbers(values): return ",".join(str(value) for value in values)
def pack_pool(pool): return "/".join(",".join(pool.get(key, [])) for key in ["starting", "basic", "advanced", "high"])

rows = []
case = 0
for level in range(1, 101):
    for base_id, base in enumerate(bases):
        for profile_id, profile in enumerate(profiles):
            seed = case * 71 - 191
            rng = random.Random(seed)
            nature = pick_random_nature_name(rng, root=args.python_root / "files")
            pool = pools[case % len(pools)]
            abilities = pick_abilities_for_level(pool, level, rng)[0]
            mon = SimpleNamespace(level=level, moves=[SimpleNamespace(category=c, db=db, freq=f) for c, db, f in profile],
                                  nature=nature, **dict(zip(keys, base)))
            builder._apply_level_up_stats(mon)
            allocation = [getattr(mon, key) - value for key, value in zip(keys, base)]
            apply_nature_to_spec(mon, root=args.python_root / "files")
            final = [getattr(mon, key) for key in keys]
            # Actual primitive: deliberately excludes battle-only passives and temporary effects.
            hp = Pokemon.max_hp(SimpleNamespace(level=level, hp_stat=mon.hp_stat))
            rows.append("\t".join([str(case), str(seed), str(level), numbers(base),
                                  ";".join(f"{c},{db},{f}" for c, db, f in profile), pack_pool(pool),
                                  nature, numbers(allocation), numbers(final), str(hp), ",".join(abilities), str(rng.getrandbits(32))]))
            case += 1
args.output.mkdir(parents=True, exist_ok=True)
(args.output / "creation.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
(args.output / "natures.tsv").write_text("\n".join(
    entry["name"] + "\t" + numbers(entry["modifiers"][key] for key in keys)
    for entry in sorted(table.values(), key=lambda entry: entry["name"])) + "\n", encoding="utf-8")
sources = ["auto_ptu/random_campaign.py", "auto_ptu/natures.py", "auto_ptu/foundry_loader.py", "auto_ptu/ptu_engine.py",
           "files/Copia de Fancy PTU 1.05 Sheet - Version Hisui - Nature_Type Data.csv"]
manifest = dict(source_revision=subprocess.check_output(["git", "-C", str(args.python_root), "rev-parse", "HEAD"], text=True).strip(),
                cases=case, sources={name: hashlib.sha256((args.python_root / name).read_bytes()).hexdigest() for name in sources})
(args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print(f"Exported {case} creation cases and {len(table)} natures")
