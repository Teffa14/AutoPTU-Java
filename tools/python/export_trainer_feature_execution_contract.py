#!/usr/bin/env python3
import argparse
import ast
from pathlib import Path


def call_name(node):
    if not isinstance(node, ast.Call):
        return ""
    func = node.func
    if isinstance(func, ast.Attribute):
        return func.attr
    if isinstance(func, ast.Name):
        return func.id
    return ""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "trainer_features.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    trigger = None
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == "trigger":
            trigger = node
            break
    if trigger is None:
        raise RuntimeError("TrainerFeatureDispatcher.trigger not found")

    apply_index = -1
    guarded_consume = False
    guarded_mark = False
    sequence = []
    for index, statement in enumerate(trigger.body):
        names = [call_name(node) for node in ast.walk(statement) if isinstance(node, ast.Call)]
        sequence.extend(name for name in names if name)
        if "_apply_feature" in names:
            apply_index = index
        if isinstance(statement, ast.For):
            for nested in ast.walk(statement):
                if not isinstance(nested, ast.If):
                    continue
                test_name = nested.test.id if isinstance(nested.test, ast.Name) else ""
                if test_name != "applied":
                    continue
                body_names = [call_name(node) for body in nested.body for node in ast.walk(body) if isinstance(node, ast.Call)]
                guarded_consume = "_consume_resources" in body_names
                guarded_mark = "_mark_feature_use" in body_names

    apply_pos = sequence.index("_apply_feature") if "_apply_feature" in sequence else -1
    consume_pos = sequence.index("_consume_resources") if "_consume_resources" in sequence else -1
    mark_pos = sequence.index("_mark_feature_use") if "_mark_feature_use" in sequence else -1

    rows = {
        "apply_before_consume": int(apply_pos >= 0 and consume_pos > apply_pos),
        "consume_before_mark": int(consume_pos >= 0 and mark_pos > consume_pos),
        "consume_guarded_by_applied": int(guarded_consume),
        "mark_guarded_by_applied": int(guarded_mark),
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{key}\t{value}" for key, value in rows.items()) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
