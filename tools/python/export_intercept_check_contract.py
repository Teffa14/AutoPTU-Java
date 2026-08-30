#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
from dataclasses import dataclass
from pathlib import Path


LexicalScope = ast.Module | ast.ClassDef | ast.FunctionDef


@dataclass(frozen=True)
class ScopedFunction:
    function: ast.FunctionDef
    owner: LexicalScope

    @property
    def qualified_name(self) -> str:
        if isinstance(self.owner, ast.Module):
            return self.function.name
        owner_name = getattr(self.owner, "name", "<scope>")
        return f"{owner_name}.{self.function.name}"


def child_scopes(scope: LexicalScope) -> list[LexicalScope]:
    return [
        node
        for node in scope.body
        if isinstance(node, (ast.ClassDef, ast.FunctionDef))
    ]


def scoped_functions(tree: ast.Module) -> list[ScopedFunction]:
    found: list[ScopedFunction] = []

    def visit(scope: LexicalScope) -> None:
        for node in scope.body:
            if isinstance(node, ast.FunctionDef):
                found.append(ScopedFunction(node, scope))
                visit(node)
            elif isinstance(node, ast.ClassDef):
                visit(node)

    visit(tree)
    return found


def find_scoped_function(tree: ast.Module, name: str) -> ScopedFunction:
    matches = [item for item in scoped_functions(tree) if item.function.name == name]
    if not matches:
        raise RuntimeError(f"missing Python function: {name}")

    owners = {id(item.owner) for item in matches}
    if len(owners) != 1:
        qualified = ", ".join(item.qualified_name for item in matches)
        raise RuntimeError(f"ambiguous Python function {name}: {qualified}")

    # Python class/module bodies execute top-to-bottom. A later def with the same name
    # replaces the earlier binding in that lexical scope, so freeze the runtime-visible def.
    return matches[-1]


def find_function(tree: ast.Module, name: str) -> ast.FunctionDef:
    return find_scoped_function(tree, name).function


def direct_function_index(scope: LexicalScope) -> dict[str, ast.FunctionDef]:
    functions: dict[str, ast.FunctionDef] = {}
    for node in scope.body:
        if not isinstance(node, ast.FunctionDef):
            continue
        # Match Python namespace binding semantics: later definitions shadow earlier ones.
        functions[node.name] = node
    return functions


def normalized(node: ast.AST) -> str:
    return " ".join(ast.unparse(node).lower().split())


def intercept_justified_bonus(function: ast.FunctionDef) -> int:
    for node in ast.walk(function):
        if not isinstance(node, ast.Assign) or len(node.targets) != 1:
            continue
        target = node.targets[0]
        if not isinstance(target, ast.Name) or target.id != "intercept_bonus":
            continue
        value = node.value
        if not isinstance(value, ast.IfExp):
            continue
        condition = normalized(value.test)
        if "has_ability_exact" not in condition or "justified [errata]" not in condition:
            continue
        if not isinstance(value.body, ast.Constant) or not isinstance(value.body.value, int):
            continue
        if not isinstance(value.orelse, ast.Constant) or value.orelse.value != 0:
            continue
        return int(value.body.value)
    raise RuntimeError("missing exact Justified [Errata] intercept bonus assignment")


def calls_in_function(function: ast.FunctionDef) -> list[ast.Call]:
    calls: list[ast.Call] = []

    class CallVisitor(ast.NodeVisitor):
        def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
            if node is function:
                self.generic_visit(node)

        def visit_Lambda(self, node: ast.Lambda) -> None:
            return

        def visit_Call(self, node: ast.Call) -> None:
            calls.append(node)
            self.generic_visit(node)

    CallVisitor().visit(function)
    return calls


def called_function_names(function: ast.FunctionDef) -> list[str]:
    names: set[str] = set()
    for node in calls_in_function(function):
        if isinstance(node.func, ast.Name):
            names.add(node.func.id)
        elif isinstance(node.func, ast.Attribute):
            names.add(node.func.attr)
    return sorted(names)


def resolve_local_call(
    tree: ast.Module,
    scoped: ScopedFunction,
    call: ast.Call,
) -> ScopedFunction | None:
    module_functions = direct_function_index(tree)
    owner = scoped.owner

    if isinstance(call.func, ast.Name):
        # A bare name in a method/function resolves through local/global lexical lookup,
        # not through an arbitrary class member with the same name.
        if isinstance(owner, ast.FunctionDef):
            nested = direct_function_index(owner).get(call.func.id)
            if nested is not None:
                return ScopedFunction(nested, owner)
        module_function = module_functions.get(call.func.id)
        if module_function is not None:
            return ScopedFunction(module_function, tree)
        return None

    if not isinstance(call.func, ast.Attribute) or not isinstance(owner, ast.ClassDef):
        return None

    value = call.func.value
    lexical_owner_call = (
        isinstance(value, ast.Name)
        and value.id in {"self", "cls", owner.name}
    )
    if not lexical_owner_call:
        # obj.foo() must not bind to an unrelated foo() elsewhere in this file.
        return None

    method = direct_function_index(owner).get(call.func.attr)
    if method is None:
        return None
    return ScopedFunction(method, owner)


def local_helper_closure(tree: ast.Module, root: ScopedFunction) -> list[ScopedFunction]:
    pending: list[ScopedFunction] = []
    visited: set[tuple[int, str]] = {(id(root.owner), root.function.name)}
    helpers: list[ScopedFunction] = []

    def enqueue_calls(scoped: ScopedFunction) -> None:
        for call in calls_in_function(scoped.function):
            resolved = resolve_local_call(tree, scoped, call)
            if resolved is None:
                continue
            key = (id(resolved.owner), resolved.function.name)
            if key in visited:
                continue
            visited.add(key)
            pending.append(resolved)

    enqueue_calls(root)
    while pending:
        scoped = pending.pop(0)
        helpers.append(scoped)
        enqueue_calls(scoped)

    helpers.sort(key=lambda item: item.qualified_name)
    simple_names = [item.function.name for item in helpers]
    if len(simple_names) != len(set(simple_names)):
        raise RuntimeError("terrain helper closure contains ambiguous simple names")
    return helpers


def string_literals(function: ast.FunctionDef) -> list[str]:
    return sorted({
        str(node.value)
        for node in ast.walk(function)
        if isinstance(node, ast.Constant) and isinstance(node.value, str)
    })


def integer_literals(function: ast.FunctionDef) -> list[int]:
    return sorted({
        int(node.value)
        for node in ast.walk(function)
        if isinstance(node, ast.Constant)
        and isinstance(node.value, int)
        and not isinstance(node.value, bool)
    })


def write_terrain_contract(tree: ast.Module, output: Path) -> None:
    root = find_scoped_function(tree, "_terrain_skill_check_bonus")
    function = root.function
    helpers = local_helper_closure(tree, root)
    naturewalk_labels = find_scoped_function(tree, "naturewalk_labels")
    species_naturewalk = find_scoped_function(tree, "_species_naturewalk")
    rows = {
        "terrain_skill_check_bonus_source": normalized(function),
        "terrain_skill_check_bonus_calls": "|".join(called_function_names(function)),
        "terrain_skill_check_bonus_strings": "|".join(string_literals(function)),
        "terrain_skill_check_bonus_integers": "|".join(str(value) for value in integer_literals(function)),
        "terrain_skill_check_helper_names": "|".join(helper.function.name for helper in helpers),
        "terrain_skill_check_helper_qualified_names": "|".join(helper.qualified_name for helper in helpers),
        "naturewalk_labels_qualified_name": naturewalk_labels.qualified_name,
        "naturewalk_labels_source": normalized(naturewalk_labels.function),
        "species_naturewalk_qualified_name": species_naturewalk.qualified_name,
        "species_naturewalk_source": normalized(species_naturewalk.function),
    }
    for helper in helpers:
        function = helper.function
        prefix = f"terrain_skill_check_helper_{function.name}"
        rows[f"{prefix}_qualified_name"] = helper.qualified_name
        rows[f"{prefix}_source"] = normalized(function)
        rows[f"{prefix}_calls"] = "|".join(called_function_names(function))
        rows[f"{prefix}_strings"] = "|".join(string_literals(function))
        rows[f"{prefix}_integers"] = "|".join(str(value) for value in integer_literals(function))

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{key}\t{value}" for key, value in rows.items()) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--terrain-output")
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    function = find_function(tree, "_attempt_intercept")
    src = normalized(function)

    # Freeze only the reusable check arithmetic here. Geometry, candidate selection and
    # movement remain separate contracts so Java can compose them without one monolith.
    flags = {
        "uses_d20": "randint(1, 20)" in src,
        "uses_best_acrobatics_athletics": "max(" in src and "acrobatics" in src and "athletics" in src,
        "uses_justified_errata": "justified [errata]" in src,
        "uses_terrain_intercept_bonus": "terrain" in src and "intercept" in src and "bonus" in src,
        "dc_is_distance_times_three": "distance * 3" in src or "3 * distance" in src,
        "coaching_can_force_success": "coaching" in src and "success" in src,
        "success_uses_greater_equal": ">= dc" in src or ">= check_dc" in src,
    }
    values = {
        "justified_errata_bonus": intercept_justified_bonus(function),
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(
            [*(f"{key}\t{1 if value else 0}" for key, value in flags.items()),
             *(f"{key}\t{value}" for key, value in values.items())]
        ) + "\n",
        encoding="utf-8",
    )

    if args.terrain_output:
        write_terrain_contract(tree, Path(args.terrain_output))


if __name__ == "__main__":
    main()
