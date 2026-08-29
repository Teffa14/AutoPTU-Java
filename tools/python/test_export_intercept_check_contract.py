#!/usr/bin/env python3
from __future__ import annotations

import ast
import importlib.util
from pathlib import Path
import sys
import unittest


MODULE_PATH = Path(__file__).with_name("export_intercept_check_contract.py")
SPEC = importlib.util.spec_from_file_location("export_intercept_check_contract", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("unable to load intercept contract exporter")
EXPORTER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = EXPORTER
SPEC.loader.exec_module(EXPORTER)


class TerrainHelperClosureScopeTest(unittest.TestCase):
    def test_resolves_only_lexically_owned_helpers(self) -> None:
        tree = ast.parse(
            """
def module_helper():
    return 1

class Target:
    def _terrain_skill_check_bonus(self):
        self.good()
        module_helper()
        foreign.good()

    def good(self):
        self.deep()

    def deep(self):
        return 2

class Foreign:
    def good(self):
        self.wrong()

    def wrong(self):
        return 99
"""
        )

        root = EXPORTER.find_scoped_function(tree, "_terrain_skill_check_bonus")
        helpers = EXPORTER.local_helper_closure(tree, root)

        self.assertEqual(
            ["Target.deep", "Target.good", "module_helper"],
            [helper.qualified_name for helper in helpers],
        )

    def test_rejects_ambiguous_root_function_names(self) -> None:
        tree = ast.parse(
            """
class First:
    def _terrain_skill_check_bonus(self):
        return 1

class Second:
    def _terrain_skill_check_bonus(self):
        return 2
"""
        )

        with self.assertRaisesRegex(RuntimeError, "ambiguous Python function"):
            EXPORTER.find_scoped_function(tree, "_terrain_skill_check_bonus")


if __name__ == "__main__":
    unittest.main()
