package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeclaredActionRoundLifecycleTest {
    @Test
    void roundStartClearsCanonicalDeclaredActions() {
        BattleRuntimeState state = state();
        state.recordDeclaredActionFromRuntime(Map.of("actor", "actor", "kind", "move"));
        state.recordDeclaredActionFromRuntime(Map.of("actor", "trainer", "kind", "feature"));

        assertEquals(2, state.declaredActions().size());

        new BattleRoundController(state, 3).startRoundWithEvents();

        assertEquals(4, state.currentRound());
        assertTrue(state.declaredActions().isEmpty());
    }

    @Test
    void declaredActionPayloadsAreDefensivelyCopiedBeforeLifecycleOwnsThem() {
        BattleRuntimeState state = state();
        ArrayList<String> targets = new ArrayList<>(List.of("target-a"));
        HashMap<String, Object> declaration = new HashMap<>();
        declaration.put("actor", "actor");
        declaration.put("targets", targets);

        state.recordDeclaredActionFromRuntime(declaration);
        targets.add("target-b");
        declaration.put("actor", "forged");

        Map<String, Object> snapshot = state.declaredActions().getFirst();
        assertEquals("actor", snapshot.get("actor"));
        assertEquals(List.of("target-a"), snapshot.get("targets"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("kind", "forged"));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
        );
    }
}
