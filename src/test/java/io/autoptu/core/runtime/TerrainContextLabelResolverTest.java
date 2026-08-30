package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainContextLabelResolverTest {
    @Test
    void preservesPythonBaseAliasThenDistinctTileOrder() {
        RuntimeCombatantState actor = combatant();
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "Wetlands"));
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "Dense Forest"));
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "wetlands"));
        BattleRuntimeState state = state(actor, environment("Rocky Mountain"), "Desert Dunes");

        assertEquals(
                List.of("mountain", "wetlands", "forest", "desert"),
                TerrainContextLabelResolver.resolve(state, "actor")
        );
    }

    @Test
    void fallsBackToTileWhenGlobalTerrainIsEmptyAndDoesNotDuplicateIt() {
        RuntimeCombatantState actor = combatant();
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "Urban Streets"));
        BattleRuntimeState state = state(actor, environment(""), "Forest Floor");

        assertEquals(
                List.of("forest", "urban"),
                TerrainContextLabelResolver.resolve(state, "actor")
        );
    }

    @Test
    void canonicalDurationBearingTerrainOverridesLegacyTerrainName() {
        RuntimeCombatantState actor = combatant();
        BattleEnvironmentState environment = environment("Desert")
                .withFieldEffects(
                        new FieldEffectEntry(FieldEffectKind.TERRAIN, "Grassy Terrain", 3),
                        List.of(),
                        List.of()
                );
        BattleRuntimeState state = state(actor, environment, "Forest Floor");

        assertEquals(
                List.of("grassy terrain", "forest"),
                TerrainContextLabelResolver.resolve(state, "actor")
        );
    }

    @Test
    void ignoresBlankAliasesAndNormalizesNonCanonicalLabelsLikePython() {
        RuntimeCombatantState actor = combatant();
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "  "));
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "Crystal---Fields"));
        BattleRuntimeState state = state(actor, environment(""), "");

        assertEquals(
                List.of("crystal fields"),
                TerrainContextLabelResolver.resolve(state, "actor")
        );
        assertEquals("ocean", TerrainContextLabelResolver.normalizeTerrainName("Deep Ocean Water"));
    }

    @Test
    void pinnedTerrainOracleStillContainsTheContextSemanticsThisResolverImplements() throws IOException {
        String oraclePath = System.getProperty("autoptu.intercept.terrain.oracle", "").strip();
        if (oraclePath.isEmpty()) return;

        Map<String, String> oracle = readContract(Path.of(oraclePath));
        String helperNames = oracle.getOrDefault("terrain_skill_check_helper_names", "");
        String contextSource = oracle.getOrDefault(
                "terrain_skill_check_helper__terrain_context_labels_source",
                ""
        );
        String currentTerrainSource = oracle.getOrDefault(
                "terrain_skill_check_helper__current_terrain_label_source",
                ""
        );

        assertTrue(helperNames.contains("_terrain_context_labels"), helperNames);
        assertTrue(helperNames.contains("_current_terrain_label"), helperNames);
        assertTrue(contextSource.contains("terrain_alias"), contextSource);
        assertTrue(contextSource.contains("_current_terrain_label"), contextSource);
        assertTrue(contextSource.contains("terrain"), contextSource);
        assertFalse(currentTerrainSource.isBlank(), "pinned oracle must freeze _current_terrain_label");
    }

    private static Map<String, String> readContract(Path path) throws IOException {
        LinkedHashMap<String, String> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            int separator = line.indexOf('\t');
            if (separator <= 0) continue;
            rows.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return rows;
    }

    private static RuntimeCombatantState combatant() {
        return new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 6),
                20,
                20,
                new ActionBudget()
        );
    }

    private static BattleEnvironmentState environment(String terrainName) {
        return new BattleEnvironmentState("", terrainName, Set.of(), Map.of());
    }

    private static BattleRuntimeState state(
            RuntimeCombatantState actor,
            BattleEnvironmentState environment,
            String tileType
    ) {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of(new GridCoord(1, 1), tileType)),
                List.of(actor)
        );
        state.syncEnvironmentFromRuntime(environment);
        return state;
    }
}
