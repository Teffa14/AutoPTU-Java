package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
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
        MoveOption move = move("ram", "Melee", 1, List.of(), "Push the target 5 meters.");
        BattleRuntimeState state = state(
                source,
                target,
                Set.of(new GridCoord(5, 1)),
                Map.of("source", List.of(move))
        );

        RuntimeForcedMovementMoveApplication.Result result = RuntimeForcedMovementMoveApplication.apply(
                state,
                choice(source, target, move),
                "Medium",
                "Medium",
                Set.of()
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
    void derivesPullFromAuthoritativeKeywordAfterCurrentChoiceRevalidation() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 4, 1);
        MoveOption move = move("hook", "Ranged", 6, List.of("pull"), "");
        BattleRuntimeState state = state(source, target, Set.of(), Map.of("source", List.of(move)));

        RuntimeForcedMovementMoveApplication.Result result = RuntimeForcedMovementMoveApplication.apply(
                state,
                choice(source, target, move),
                "Medium",
                "Medium",
                Set.of()
        ).orElseThrow();

        assertEquals(ForcedMovementInstruction.Kind.PULL, result.instruction().kind());
        assertEquals(1, result.instruction().distance());
        assertEquals(new GridCoord(3, 1), target.position());
        assertEquals(1, result.displacement().movedDistance());
    }

    @Test
    void rejectsOutOfRangeTargetBeforeForcedMovementMutatesPosition() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 4, 1);
        MoveOption move = move("ram", "Melee", 1, List.of("push"), "");
        BattleRuntimeState state = state(source, target, Set.of(), Map.of("source", List.of(move)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeForcedMovementMoveApplication.apply(
                        state,
                        choice(source, target, move),
                        "Medium",
                        "Medium",
                        Set.of()
                )
        );

        assertTrue(error.getMessage().contains("no longer legal"));
        assertEquals(new GridCoord(4, 1), target.position());
    }

    @Test
    void rejectsStaleTargetAnchorBeforeForcedMovementMutatesPosition() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("ram", "Melee", 1, List.of("push"), "");
        BattleRuntimeState state = state(source, target, Set.of(), Map.of("source", List.of(move)));
        MoveChoice stale = new MoveChoice(
                "source",
                "ram",
                ChoiceTargetMode.COMBATANT,
                "target",
                new GridCoord(3, 1),
                ActionType.STANDARD
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeForcedMovementMoveApplication.apply(
                        state,
                        stale,
                        "Medium",
                        "Medium",
                        Set.of()
                )
        );
        assertEquals(new GridCoord(2, 1), target.position());
    }

    @Test
    void canonicalMoveWithoutForcedMovementLeavesPositionUntouched() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("plain", "Melee", 1, List.of("contact"), "Deal damage.");
        BattleRuntimeState state = state(source, target, Set.of(), Map.of("source", List.of(move)));

        assertTrue(RuntimeForcedMovementMoveApplication.apply(
                state,
                choice(source, target, move),
                "Medium",
                "Medium",
                Set.of()
        ).isEmpty());
        assertEquals(new GridCoord(2, 1), target.position());
    }

    @Test
    void rejectsMoveThatIsNotOwnedBySourceCombatant() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption owned = move("ram", "Melee", 1, List.of("push"), "");
        MoveOption forged = move("forged", "Melee", 1, List.of("push"), "");
        BattleRuntimeState state = state(source, target, Set.of(), Map.of("source", List.of(owned)));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeForcedMovementMoveApplication.apply(
                        state,
                        choice(source, target, forged),
                        "Medium",
                        "Medium",
                        Set.of()
                )
        );
        assertTrue(error.getMessage().contains("authoritative combatant moveset"));
        assertEquals(new GridCoord(2, 1), target.position());
    }

    @Test
    void requiresServerOwnedMovesetInsteadOfAdapterSuppliedMetadata() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("ram", "Melee", 1, List.of("push"), "");
        BattleRuntimeState state = state(source, target, Set.of(), Map.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> RuntimeForcedMovementMoveApplication.apply(
                        state,
                        choice(source, target, move),
                        "Medium",
                        "Medium",
                        Set.of()
                )
        );
        assertTrue(error.getMessage().contains("no canonical moveset"));
        assertFalse(state.hasCanonicalMoves("source"));
    }

    private static MoveChoice choice(RuntimeCombatantState source, RuntimeCombatantState target, MoveOption move) {
        return new MoveChoice(
                source.combatantId(),
                move.moveId(),
                ChoiceTargetMode.COMBATANT,
                target.combatantId(),
                target.position(),
                move.actionType()
        );
    }

    private static MoveOption move(
            String moveId,
            String targetKind,
            int range,
            List<String> keywords,
            String effectsText
    ) {
        return new MoveOption(
                moveId,
                new MoveSpec(targetKind, targetKind, range, range, null, null, targetKind, keywords, effectsText),
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
