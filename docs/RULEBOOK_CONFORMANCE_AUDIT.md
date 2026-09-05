# AutoPTU-Java Rulebook Conformance Audit

## Purpose

Python parity is necessary for migration safety, but it is not sufficient to prove PTU rules correctness.

Every battle subsystem must be evaluated against three independent evidence layers:

1. **PTU normative rules** — the applicable PTU core/errata source for the project.
2. **Kairos rules** — `PTU Kairos Edition V2.1.25.1` and its companion references where the project intentionally supports Kairos behavior.
3. **Python AutoPTU oracle** — executable compatibility evidence pinned by SHA.

Java must not silently copy a Python behavior that conflicts with an applicable normative rule.

## Acceptance states

For rule-bearing slices use these independent states:

- `JAVA_TESTS_PASS`
- `PYTHON_PARITY_PASS`
- `TRACE_PARITY_PASS` when ordered semantic events/state matter
- `RULEBOOK_CONFORMANCE_PASS`
- `RULEBOOK_DIVERGENCE_DOCUMENTED` when the executable oracle differs from the chosen normative rule
- `SOURCE_REQUIRED` when a requested normative source has not been identified or supplied

A subsystem must not be described as rule-complete from Python parity alone.

## Source hierarchy and conflict handling

Rule source priority is explicit rather than accidental:

1. The rule profile selected for the battle/campaign.
2. Applicable errata/clarifications for that profile.
3. The corresponding core rulebook text.
4. Python AutoPTU as executable compatibility oracle.

Kairos is a distinct rule profile, not a synonym for vanilla PTU. Its corebook states that it compiles PTU Core changes, Game of Throh's, errata and PTU Editation material and also contains homebrew changes. Therefore a Kairos-specific behavior must be tagged as such rather than silently becoming universal PTU behavior.

If Python disagrees with an applicable rule source:

- preserve the disagreement as a regression fixture;
- classify it as a rulebook/oracle divergence;
- implement the selected rule profile intentionally;
- document any compatibility deviation from Python.

## Caelo status

The user requested validation against **PTU, Caelo and Kairos**.

At the time of this audit baseline, no repository file or accessible project-library source identified the exact Caelo rulebook, repository, version, URL or errata set. Caelo is therefore `SOURCE_REQUIRED` and must not be guessed.

Once the source is identified, add its exact title/version and map each relevant rule below to the Caelo profile.

## Verified Kairos combat baseline

Source inspected: `PTU Kairos Edition V2.1.25.1` / internal title `PTU Kairos Homebrew V2.06.25 Edition Corebook`.

The following rules are concrete conformance targets rather than Python-derived assumptions:

### Turn economy and initiative

- Combat uses 10-second rounds.
- Full-contact combatants normally resolve highest Initiative to lowest; ties use a d20 roll-off.
- A combatant may hold to a specified lower Initiative once per round.
- Effects lasting one Full Round expire at the same Initiative Count next round.
- Each participant normally has one Standard Action, one Shift Action and one Swift Action per turn, in any order.
- Free Actions are not numerically capped, but triggered actions can activate only once per trigger.
- A Standard Action can be traded for another Swift Action.
- A Standard Action can be traded for another Shift Action, but not for a second movement Shift after the regular Shift was already used for movement.
- Full Actions consume Standard + Shift.
- Priority, Priority (Limited), Priority (Advanced), and Interrupt have distinct turn-consumption semantics.

### Movement and positioning

- Small/Medium footprint: 1x1; Large: 2x2; Huge: 3x3; Gigantic: 4x4, with body-shape variants permitted.
- Movement uses Shift Actions.
- Maximum movement is the relevant Movement Capability.
- When multiple Movement Capabilities are used in one turn, Kairos specifies averaging those capabilities for the maximum Shift distance.
- A Shift Action cannot be split around another action.
- Jump distance consumes distance from the main Movement Capability or can consume a whole Shift Action.
- Stuck prevents movement Shifts but does not prevent using the Shift Action for non-movement effects.
- Slowed halves movement speed.
- Slow Terrain makes each square meter cost two meters.
- Rough Terrain applies a -2 Accuracy penalty when targeting through it; occupied spaces are Rough Terrain, and foe-occupied squares always count as Rough Terrain.
- Blocking Terrain cannot be Shifted or Targeted through.

### Combat stats, stages, accuracy and evasion

- Attack is added to Physical damage; Defense is subtracted from Physical damage.
- Special Attack is added to Special damage; Special Defense is subtracted from Special damage.
- Speed normally determines Initiative.
- Stat-derived Physical/Special/Speed Evasion is +1 per 5 relevant Stat, capped at +6 from that Stat.
- Accuracy stage is bounded from -6 to +6 and modifies Accuracy Rolls directly.
- Only one of Physical/Special/Speed Evasion is added to one Accuracy Check.
- Stat-derived Evasion is capped at +6; total positive Accuracy Check increase from Evasion is capped at +9.
- Attack, Defense, Special Attack, Special Defense and Speed use Combat Stages; HP does not.
- Combat Stages are bounded at -6/+6.
- Positive stages add 20% of the base Stat per stage; negative stages subtract 10% per stage, rounded down.
- Speed Combat Stages also modify all Movement Speeds by half the stage value rounded down, with negative stages unable to reduce a Movement Speed below 2.
- Default Combat Stages are a separate baseline concept and reset effects may restore the configured default rather than zero.

### Accuracy and attack resolution

- Accuracy Roll is 1d20 plus applicable modifiers.
- Natural 1 always misses; natural 20 always hits.
- Accuracy Check is base AC plus applicable target Evasion.
- Die-face-triggered effects and critical ranges use the unmodified die result unless a rule explicitly changes them.

### Damage pipeline

Kairos gives the following explicit order:

1. Find initial Damage Base.
2. Apply Five/Double-Strike.
3. Apply Damage Base modifiers such as STAB to obtain final DB.
4. Apply Critical Hit modification to the damage roll if applicable.
5. Roll or use set damage.
6. Add the relevant attacking Stat and other damage bonuses.
7. Subtract the relevant defending Stat and Damage Reduction.
8. Apply weakness/resistance multipliers.
9. Subtract final damage from HP and check Injuries/KO.

Additional required behavior:

- A damaging Move sharing a type with its Pokémon user gains STAB of +2 Damage Base.
- Minimum damage after defenses/DR is 1 before type effectiveness.
- Super-effective x1.5; double weakness x2; triple weakness x3.
- Resistance x0.5; double resistance x0.25; triple resistance x0.125.
- Immunity results in 0 damage where applicable.
- HP-loss effects are not ordinary damage: Defense/SpDef do not apply and they do not cause Massive Damage injuries merely by being HP loss.
- Critical Hit adds the damage dice/set-roll component a second time, not the attacking Stat a second time.
- Injury checks include Massive Damage at >=50% max HP from an attack and HP markers described by the rule profile.

### Type/status baseline

Kairos explicitly gives, among others:

- Electric-types immune to Paralysis.
- Fire-types immune to Burn.
- Ghost-types cannot be Spooked, Stuck or Trapped.
- Grass-types immune to Powder Move effects.
- Ice-types immune to Frozen and Frostbite.
- Poison- and Steel-types immune to Poison.

### Struggle baseline

- Struggle is a Standard Action, AC 4, DB 4, Melee, Physical, Normal by default.
- Unmodified Struggle can hit Ghost-types.
- Struggle never receives STAB.
- Struggle is not a Move for effects that alter Moves.
- Expert+ Combat raises baseline Struggle to AC 3 / DB 5.

## Permanent capability audit: normative evidence status

This matrix records whether a normative pass has been started. It does not claim implementation completeness.

| Capability | Kairos normative evidence | Java/Python comparison status | Caelo |
|---|---|---|---|
| Targeting / footprints / range / LOS | PARTIAL: footprint, rough/blocking terrain, range chapter identified | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| Core movement legality | PARTIAL: Shift, capability mixing, jump consumption, Slow/Stuck/terrain identified | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| Complete movement behavior | PARTIAL: interrupt/Shift semantics and hazards visible, forced-movement families not fully audited | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| Core calculations | BASELINE_CAPTURED: stages, Accuracy/Evasion, STAB, type multipliers, damage order | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| Action economy / initiative | BASELINE_CAPTURED | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| Full turn/round lifecycle | PARTIAL: Full Round, initiative/hold/priority semantics captured; full phase lifecycle not exhaustively mapped | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| Full damage pipeline | BASELINE_CAPTURED for ordinary attacks; stateful hooks/reactions remain | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| Status lifecycle | PARTIAL: type immunities identified; full affliction/tick/save/cure lifecycle not audited | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| Terrain / weather / hazards / zones / reactions | PARTIAL | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| Move-specific content parity | SOURCE_SECTIONS_IDENTIFIED | NEEDS_CATALOG_DIFF | SOURCE_REQUIRED |
| Ability parity | SOURCE_SECTIONS_IDENTIFIED | NEEDS_CATALOG_DIFF | SOURCE_REQUIRED |
| Item parity | SOURCE_SECTIONS_IDENTIFIED | NEEDS_CATALOG_DIFF | SOURCE_REQUIRED |
| Trainer Feature/perk combat parity | SOURCE_SECTIONS_IDENTIFIED | NEEDS_CATALOG_DIFF | SOURCE_REQUIRED |
| AI legal-action infrastructure | RULE_DERIVED: must enumerate only actions legal under selected profile | NEEDS_SYSTEMATIC_DIFF | SOURCE_REQUIRED |
| AI tactical scoring/policy | NON_NORMATIVE except legality constraints | N/A_FOR_RULE_CONFORMANCE | SOURCE_REQUIRED |
| Minecraft/Cobblemon/Craftics adapter | NON_NORMATIVE; adapter must preserve authoritative core results | BOUNDARY_ONLY | SOURCE_REQUIRED |

## Audit work required next

Do not replace this with a percentage guessed from current tests. For each category:

1. Identify exact rulebook section/page(s) for the selected profile.
2. Encode small language-neutral conformance fixtures independent of Python.
3. Run those fixtures against Java.
4. Run equivalent scenarios against pinned Python.
5. Classify every result as:
   - `RULEBOOK_AND_PYTHON_AGREE`
   - `JAVA_RULEBOOK_BUG`
   - `PYTHON_RULEBOOK_DIVERGENCE`
   - `PROFILE_DIFFERENCE`
   - `AMBIGUOUS_RULE_REQUIRES_RULING`
6. Add permanent regression tests for every resolved discrepancy.

## Immediate high-value comparison targets

The first systematic diff should cover these cross-cutting rules because errors here contaminate many moves/abilities/features:

- action inventory and Standard-to-Swift/Shift conversion;
- Full Action and Interrupt/Priority consumption;
- mixed movement capability averaging;
- Slow Terrain and Speed-CS movement interaction;
- Accuracy/Evasion caps and natural 1/20 handling;
- Default Combat Stage reset semantics;
- complete ordinary damage order including minimum damage and type multipliers;
- STAB applicability and explicit Struggle exclusion;
- injury threshold/marker handling;
- type-based status immunities.

These checks should precede broad content-count parity work.