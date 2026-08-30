#!/usr/bin/env python3
import argparse
import ast
from pathlib import Path
from typing import Sequence, Tuple


def find_line_cells(tree: ast.AST) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == "_line_cells":
            return node
    raise RuntimeError("_line_cells not found in pinned Python oracle")


def load_oracle_function(source_root: Path):
    path = source_root / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    fn = find_line_cells(tree)
    module = ast.Module(body=[fn], type_ignores=[])
    ast.fix_missing_locations(module)
    namespace = {"Sequence": Sequence, "Tuple": Tuple}
    exec(compile(module, str(path), "exec"), namespace)
    return namespace["_line_cells"]


def encode(cells) -> str:
    return ";".join(f"{int(x)},{int(y)}" for x, y in cells)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    line_cells = load_oracle_function(Path(args.source_root))
    cases = [
        ("same", (2, 2), (2, 2)),
        ("horizontal", (0, 0), (4, 0)),
        ("vertical_reverse", (2, 4), (2, 0)),
        ("shallow", (0, 0), (4, 2)),
        ("steep", (0, 0), (2, 4)),
        ("reverse_tie", (4, 2), (0, 0)),
        ("mixed_sign", (4, 0), (0, 3)),
    ]

    rows = ["case\torigin_x\torigin_y\ttarget_x\ttarget_y\tcells\n"]
    for name, origin, target in cases:
        cells = line_cells(None, origin, target)
        rows.append(
            f"{name}\t{origin[0]}\t{origin[1]}\t{target[0]}\t{target[1]}\t{encode(cells)}\n"
        )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("".join(rows), encoding="utf-8")


if __name__ == "__main__":
    main()
