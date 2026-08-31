package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeForcedMovementAbilityModifierTest {
    @Test
    void thrustCreatesPushOneForPhysicalMoveWithoutBaseInstruction() {
        RuntimeCombatantState source = combatant("source", 1, 1, List.of("Thrust"));
        RuntimeCombatantState target = combatant("target", 2, 1, List.of());
        MoveOption move = move("strike", "physical", List.of(), "Deal damage.");
        BattleRuntimeState state = state(source, target, move);

        RuntimeForcedMovementMoveApplication.Result result = RuntimeForcedMovementMoveApplication.apply(
                state, choice(source, target, move), "Medium", "Medium", Set.of()
        ).orElseThrow();

        assertEquals(ForcedMovementInstruction.Kind.PUSH, result.instruction().kind());
        assertEquals(1, result.instruction().distance());
        assertEquals(new GridCoord(3, 1), target.position());
    }

    @Test
    void thrustAddsOneToExistingPhysicalPush() {
        RuntimeCombatantState source = combatant("source", 1, 1, List.of("Thrust"));
        RuntimeCombatantState target = combatant("target", 2, 1, List.of());
        MoveOption move = move("ram", "physical", List.of("push 2"), "Push the target 2 meters.");
        BattleRuntimeState state = state(source, target, move);

        RuntimeForcedMovementMoveApplication.Result result = RuntimeForcedMovementMoveApplication.apply(
                state, choice(source, target, move), "Medium", "Medium", Set.of()
        ).orElseThrow();

        assertEquals(ForcedMovementInstruction.Kind.PUSH, result.instruction().kind());
        assertEquals(3, result.instruction().distance());
        assertEquals(new GridCoord(5, 1), target.position());
    }

    @Test
    void thrustDoesNotChangePull() {
        RuntimeCombatantState source = combatant("source", 1, 1, List.of("Thrust"));
        RuntimeCombatantState target = combatant("target", 3, 1, List.of());
        MoveOption move = move("hook", "physical", List.of("pull"), "", 2);
        BattleRuntimeState state = state(source, target, move);

        RuntimeForcedMovementMoveApplication.Result result = RuntimeForcedMovementMoveApplication.apply(
                state, choice(source, target, move), "Medium", "Medium", Set.of()
        ).orElseThrow();

        assertEquals(ForcedMovementInstruction.Kind.PULL, result.instruction().kind());
        assertEquals(1, result.instruction().distance());
        assertEquals(new GridCoord(2, 1), target.position());
    }

    @Test
    void thrustRequiresPhysicalCategoryAndActiveAbilities() {
        RuntimeCombatantState source = combatant("source", 1, 1, List.of("Thrust"));
        RuntimeCombatantState target = combatant("target", 2, 1, List.of());
        MoveOption special = move("pulse", "special", List.of(), "Deal damage.");
        BattleRuntimeState specialState = state(source, target, special);

        assertTrue(RuntimeForcedMovementMoveApplication.apply(
                specialState, choice(source, target, special), "Medium", "Medium", Set.of()
        ).isEmpty());
        assertEquals(new GridCoord(2, 1), target.position());

        RuntimeCombatantState suppressedSource = combatant("suppressed", 1, 2, List.of("Thrust"));
        suppressedSource.setAbilitiesSuppressedFromRuntime(true);
        RuntimeCombatantState suppressedTarget = combatant("suppressed-target", 2, 2, List.of());
        MoveOption physical = move("strike", "physical", List.of(), "Deal damage.");
        BattleRuntimeState suppressedState = state(suppressedSource, suppressedTarget, physical);

        assertTrue(RuntimeForcedMovementMoveApplication.apply(
                suppressedState,
                choice(suppressedSource, suppressedTarget, physical),
                "Medium",
                "Medium",
                Set.of()
        ).isEmpty());
        assertEquals(new GridCoord(2, 2), suppressedTarget.position());
    }

    private static MoveOption move(String id, String category, List<String> keywords, String effectsText) {
        return move(id, category, keywords, effectsText, 1);
    }

    private static MoveOption move(
            String id,
            String category,
            List<String> keywords,
            String effectsText,
            int targetRange
    ) {
        return new MoveOption(
                id,
                new MoveSpec("Melee", "Melee", targetRange, targetRange, null, null, "Melee", keywords, effectsText),
                ActionType.STANDARD,
                true,
                new MoveCombatProfile(2, 4, 20, category)
        );
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

    private static BattleRuntimeState state(
            RuntimeCombatantState source,
            RuntimeCombatantState target,
            MoveOption move
    ) {
        return new BattleRuntimeState(
                new MovementGrid(10, 5, Set.of(), Map.of()),
                List.of(source, target),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(source.combatantId(), List.of(move))
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                abilities
        );
    }
}
