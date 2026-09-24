# Rulebook / Oracle Divergences

This register tracks cases where a supported PTU rule profile and the executable Python oracle disagree. These are not ordinary Java parity failures.

## CS-001 — Combat Stage multiplier

**Classification:** `PYTHON_RULEBOOK_DIVERGENCE`

**Affected profiles:** PTU 1.05, Kairos

**Normative behavior:** Combat Stages are bounded to -6..+6. Each positive stage raises the base Stat by 20%; each negative stage lowers the base Stat by 10%, with the resulting Stat rounded down when projected.

| Stage | Multiplier |
|---:|---:|
| -6 | 0.4 |
| -5 | 0.5 |
| -4 | 0.6 |
| -3 | 0.7 |
| -2 | 0.8 |
| -1 | 0.9 |
| 0 | 1.0 |
| +1 | 1.2 |
| +2 | 1.4 |
| +3 | 1.6 |
| +4 | 1.8 |
| +5 | 2.0 |
| +6 | 2.2 |

**Source evidence:**

- PTU 1.05 vendored changelog: `files/rulebook/PTU 1.05/PTU changelog 1.05.txt` states that Combat Stages are now `+20% / -10%`.
- Kairos corebook, Combat Stages section, p.386, gives the same percentages and complete multiplier table.

**Old Python oracle behavior:** `auto_ptu/rules/calculations.py::stage_multiplier` uses `(2 + stage) / 2` for non-negative stages and `2 / (2 - stage)` for negative stages. This is the mainline video-game ratio model and produces materially different PTU results.

**Old Java behavior:** `Calculations.stageMultiplier` copied the Python formula and the unit test explicitly froze it as `stageMultipliersMatchPythonFormula`.

**Resolution:**

- Java branch `rulebook/ptu-combat-stage-formula` now uses the PTU 1.05/Kairos table formula and tests direct normative values.
- Python issue `Teffa14/AutoPTU#238` tracks correction of the executable oracle.
- Do not repin Java or merge the rule fix as parity-complete until the corrected Python oracle has a validated SHA and Java differential fixtures are regenerated.

**Cross-cutting impact:** offensive stat projection, defensive stat projection, Speed projection, Evasion derived from staged stats, damage, initiative/movement interactions, stage-changing abilities/items/Features, and AI evaluation.

## Template for new divergences

For every new entry record:

- stable ID;
- selected rule profile(s);
- exact source section/page;
- Python behavior;
- Java behavior;
- classification;
- resolution/ruling;
- regression tests and affected subsystems.
