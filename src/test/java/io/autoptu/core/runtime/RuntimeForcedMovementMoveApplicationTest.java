package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ForcedMovementInstruction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeForcedMovementMoveApplicationTest {
    @Test
    void derivesPushFromAuthoritativeMoveEffectsAndUsesSharedPartialStop() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("ram", List.of(), "Push the target 5 meters.");
        BattleRuntimeState state = state(
                source,
                target,
                Set.of(new GridCoord(5, 1)),
                Map.of("source", List.of(move))
        );

        RuntimeForcedMovementMoveApplication.Result result = RuntimeForcedMovementMoveApplication.apply(
                state, "source", "target", "ram"
        ).orElseThrow();

        assertEquals(ForcedMovementInstruction.Kind.PUSH, result.instruction().kind());
        assertEquals(5, result.instruction().distance());
        assertEquals(new GridCoord(4, 1), result.displacement().destination());
        assertEquals(2, result.displacement().movedDistance());
        assertTrue(result.displacement().stoppedEarly());
        assertEquals(ForcedDisplacementResolution.StopReason.BLOCKER, result.displacement().stop().reason());
        assertEquals(new GridCoord(4, 1), target.position());
        assertTrue(target.actionBudget().hasActionAvailable(ActionType.SHIFT));
    }

    @Test
    void derivesPullFromAuthoritativeKeywordAndStopsBeforeSourceFootprint() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 4, 1);
        MoveOption move = move("hook", List.of("pull"), "");
        BattleRuntimeState state = state(source, target, Set.of(), Map.of("source", List.of(move)));

        RuntimeForcedMovementMoveApplication.Result result = RuntimeForcedMovementMoveApplication.apply(
                state, "source", "target", "hook"
        ).orElseThrow();

        assertEquals(ForcedMovementInstruction.Kind.PULL, result.instruction().kind());
        assertEquals(1, result.instruction().distance());
        assertEquals(new GridCoord(3, 1), target.position());
        assertEquals(1, result.displacement().movedDistance());
    }

    @Test
    void canonicalMoveWithoutForcedMovementLeavesPositionUntouched() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 3, 1);
        MoveOption move = move("plain", List.of("contact"), "Deal damage.");
        BattleRuntimeState state = state(source, target, Set.of(), Map.of("source", List.of(move)));

        assertTrue(RuntimeForcedMovementMoveApplication.apply(state, "source", "target", "plain").isEmpty());
        assertEquals(new GridCoord(3, 1), target.position());
    }

    @Test
    void rejectsMoveThatIsNotOwnedBySourceCombatant() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 3, 1);
        MoveOption move = move("ram", List.of("push"), "");
        BattleRuntimeState state = state(source, target, Set.of(), Map.of("source", List.of(move)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeForcedMovementMoveApplication.apply(state, "source", "target", "forged")
        );
        assertTrue(error.getMessage().contains("authoritative combatant moveset"));
        assertEquals(new GridCoord(3, 1), target.position());
    }

    @Test
    void requiresServerOwnedMovesetInsteadOfAdapterSuppliedMetadata() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 3, 1);
        BattleRuntimeState state = state(source, target, Set.of(), Map.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> RuntimeForcedMovementMoveApplication.apply(state, "source", "target", "ram")
        );
        assertTrue(error.getMessage().contains("no canonical moveset"));
        assertFalse(state.hasCanonicalMoves("source"));
    }

    private static MoveOption move(String moveId, List<String> keywords, String effectsText) {
        return new MoveOption(
                moveId,
                new MoveSpec("Melee", "Melee, 1 Target", 1, 1, null, null, "Melee, 1 Target", keywords, effectsText),
                ActionType.STANDARD,
                true
        );
    }

    private static BattleRuntimeState state(
            RuntimeCombatantState source,
            RuntimeCombatantState target,
            Set<GridCoord> blockers,
            Map<String, List<MoveOption>> moves
    ) {
        return new BattleRuntimeState(
                new MovementGrid(8, 4, blockers, Map.of()),
                List.of(source, target),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                moves
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                new ActionBudget()
        );
    }
}
