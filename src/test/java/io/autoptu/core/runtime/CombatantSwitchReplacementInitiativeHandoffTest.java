package io.autoptu.core.runtime;

import io.autoptu.core.hook.BuiltinLifecycleHooks;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CombatantSwitchReplacementInitiativeHandoffTest {
    @Test
    void executorPreservesNoReplacementTurnPolicy() {
        assertPolicy(false, false);
    }

    @Test
    void executorPreservesReplacementTurnWithoutImmediatePolicy() {
        assertPolicy(true, false);
    }

    @Test
    void executorPreservesImmediateReplacementTurnPolicy() {
        assertPolicy(true, true);
    }

    private static void assertPolicy(boolean allowReplacementTurn, boolean allowImmediate) {
        GridCoord outgoingPosition = new GridCoord(4, 3);
        RuntimeCombatantState outgoing = combatant("a-1", outgoingPosition);
        RuntimeCombatantState replacement = combatant("a-2", new GridCoord(0, 0));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(outgoing, replacement),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "a-1", CombatantAffiliationState.active("players"),
                        "a-2", new CombatantAffiliationState("players", false)
                )
        );
        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(Map.of("a-1", outgoingPosition));

        boolean[] observed = {false};
        CombatantSwitchPostEntryDispatcher dispatcher = CombatantSwitchPostEntryDispatcher.empty()
                .withHandler(CombatantSwitchPostEntryPlan.Stage.INSERT_REPLACEMENT_INITIATIVE, context -> {
                    observed[0] = true;
                    assertEquals(allowReplacementTurn, context.allowReplacementTurn());
                    assertEquals(allowImmediate, context.allowImmediate());
                    assertEquals("a-2", context.replacementId());
                    assertTrue(context.state().isActive("a-2"));
                    return List.of();
                });

        CombatantSwitchExecutor.ExecutionResult result = CombatantSwitchExecutor.execute(
                state,
                presence,
                BuiltinLifecycleHooks.registry(),
                dispatcher,
                event -> {},
                "a-1",
                "a-2",
                allowReplacementTurn,
                allowImmediate
        );

        assertTrue(observed[0]);
        assertFalse(state.isActive("a-1"));
        assertTrue(state.isActive("a-2"));
        assertEquals(
                CombatantSwitchPostEntryDispatcher.StageStatus.EXECUTED,
                result.postEntryDispatchResult().stages().stream()
                        .filter(stage -> stage.stage() == CombatantSwitchPostEntryPlan.Stage.INSERT_REPLACEMENT_INITIATIVE)
                        .findFirst()
                        .orElseThrow()
                        .status()
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                20,
                20,
                new ActionBudget()
        );
    }
}
