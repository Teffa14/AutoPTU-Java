package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainContextLabelResolverTest {
    @Test
    void preservesPythonBaseAliasThenDistinctTileOrder() {
        RuntimeCombatantState actor = combatant();
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "Wetlands"));
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "Dense Forest"));
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "wetlands"));
        BattleRuntimeState state = state(actor, "Rocky Mountain", "Desert Dunes");

        assertEquals(
                List.of("mountain", "wetlands", "forest", "desert"),
                TerrainContextLabelResolver.resolve(state, "actor")
        );
    }

    @Test
    void fallsBackToTileWhenGlobalTerrainIsEmptyAndDoesNotDuplicateIt() {
        RuntimeCombatantState actor = combatant();
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "Urban Streets"));
        BattleRuntimeState state = state(actor, "", "Forest Floor");

        assertEquals(
                List.of("forest", "urban"),
                TerrainContextLabelResolver.resolve(state, "actor")
        );
    }

    @Test
    void ignoresBlankAliasesAndNormalizesNonCanonicalLabelsLikePython() {
        RuntimeCombatantState actor = combatant();
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "  "));
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "Crystal---Fields"));
        BattleRuntimeState state = state(actor, "", "");

        assertEquals(
                List.of("crystal fields"),
                TerrainContextLabelResolver.resolve(state, "actor")
        );
        assertEquals("ocean", TerrainContextLabelResolver.normalizeTerrainName("Deep Ocean Water"));
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

    private static BattleRuntimeState state(
            RuntimeCombatantState actor,
            String terrainName,
            String tileType
    ) {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of(new GridCoord(1, 1), tileType)),
                List.of(actor)
        );
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState(
                "",
                terrainName,
                Set.of(),
                Map.of()
        ));
        return state;
    }
}
