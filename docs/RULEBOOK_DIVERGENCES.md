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

## EV-001 — Evasion bypasses staged Defense / Special Defense

**Classification:** `PYTHON_RULEBOOK_DIVERGENCE`

**Affected profiles:** PTU 1.05, Kairos

**Normative behavior:** Physical, Special, and Speed Evasion derive from the effective Defense, Special Defense, and Speed Stats respectively. Combat Stages modify those Stats before Evasion is derived. Stat-derived Evasion is +1 per 5 points of the effective Stat and is capped at +6. Negative Evasion may erase positive Evasion but cannot make an attack easier than its base AC, and total positive Evasion may raise an Accuracy Check by at most +9.

**Source evidence:** Kairos corebook pp.385-386 states that Evasion is derived from the relevant Stat, caps Stat-derived Evasion at +6, caps the Accuracy Check increase from Evasion at +9, and explicitly explains that Combat Stages affect Defense/Special Defense/Speed Evasion through the changed Stat.

**Old Python oracle behavior:** `calculations.evasion_value` computes Physical and Special Stat Evasion from unstaged `Defense // 5` and `Special Defense // 5`. Speed Evasion computes `Speed // 5` and then adds the raw Speed Combat Stage. Therefore all three paths can disagree with `floor(effective_staged_stat / 5)`.

**Old Java behavior:** `EvasionResolution` copied the same split logic: raw Defense/SpDef for Physical/Special, and raw Speed Evasion plus Speed stage for Status.

**Resolution in progress:**

- Python issue `Teffa14/AutoPTU#239` tracks the oracle correction.
- Java branch `rulebook/evasion-stage-projection` routes Evasion through the shared `StatResolution` projection, then applies the PTU +6 Stat-Evasion cap and 0..+9 final contribution bounds.
- Dedicated rulebook conformance tests cover staged Stats that are not multiples of five so the incorrect raw-stage shortcut cannot accidentally pass.
- Existing Python-oracle parity tests are expected to disagree until the Python oracle is corrected and repinned.

**Cross-cutting impact:** Accuracy, status moves, physical/special attacks, Burn/Poison/Paralysis interactions, Power Shift/Power Trick/Wonder Room stat-source changes, abilities/items/Features that modify Stats or Evasion, legal-action expected value, and AI scoring.

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
