# Pokémon creation primitives

`PokemonCreation` owns the typed PTU creation calculation, with no Minecraft dependency.
Input: positive species base stats, level 1–100, build-profile move metadata, source nature
definitions, ability tier pools and `PythonRandom`. Output: nature, allocated points,
post-nature stats, base maximum HP and selected abilities.

The explicit policy is Python `CsvRandomCampaignBuilder`'s objective-driven minmax spread:
level + 10 points, five candidate spreads, HP floor, deterministic scoring/tie order.
This is the existing project's generation policy, not a new mandatory tabletop allocation
rule. Allocation precedes nature application; nature clamps each final stat to at least 1.
HP is the **base** `level + 3 * hp_stat + 10`, before battle passives/injuries/temporary effects.

Nature selection and ability tier choice retain Python's RNG consumption and fallback order.
No move acquisition, native stat conversion, capture, evolution, XP or combat effect execution
is implied. Move profiles guide allocation only and do not grant those moves.

Regenerate the checked-in 3,600-case fixture using the actual Python implementation:

```text
python tools/export_pokemon_creation_oracle.py --python-root ../AutoPTU --output src/test/resources/pokemon-creation
gradle test --tests '*PokemonCreationTest'
```

The exporter records the upstream revision and source hashes. Tests cover all levels,
six base stat profiles, six move profiles, sparse/duplicate ability tiers, nature application,
allocated point totals, exact final output and the next RNG word. Missing fixtures fail tests.
