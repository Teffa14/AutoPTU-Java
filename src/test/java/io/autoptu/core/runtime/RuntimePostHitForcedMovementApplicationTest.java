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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePostHitForcedMovementApplicationTest {
    @Test
    void appliesAfterOrdinaryActionWasAlreadySpent() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("ram", List.of("push 2"), "Push the target 2 meters.");
        BattleRuntimeState state = state(source, target, move);
        MoveChoice choice = choice(source, target, move);

        assertTrue(source.actionBudget().consume(ActionType.STANDARD, move.moveId()));
        RuntimeForcedMovementMoveApplication.Result result = RuntimePostHitForcedMovementApplication.apply(
                state, choice, true
        ).orElseThrow();

        assertEquals(2, result.instruction().distance());
        assertEquals(new GridCoord(4, 1), target.position());
    }

    @Test
    void preservesPythonHitGate() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("ram", List.of("push"), "");
        BattleRuntimeState state = state(source, target, move);

        assertTrue(RuntimePostHitForcedMovementApplication.apply(
                state, choice(source, target, move), false
        ).isEmpty());
        assertEquals(new GridCoord(2, 1), target.position());
    }

    @Test
    void defenderAbilityCanPreventResolvedPush() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1, List.of("Suction Cups [Errata]"));
        MoveOption move = move("ram", List.of("push 2"), "Push the target 2 meters.");
        BattleRuntimeState state = state(source, target, move);

        assertTrue(RuntimePostHitForcedMovementApplication.apply(
                state, choice(source, target, move), true
        ).isEmpty());
        assertEquals(new GridCoord(2, 1), target.position());
    }

    @Test
    void suppressedDefenderAbilityDoesNotPreventPush() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1, List.of("Suction Cups"));
        target.setAbilitiesSuppressedFromRuntime(true);
        MoveOption move = move("ram", List.of("push"), "");
        BattleRuntimeState state = state(source, target, move);

        assertTrue(RuntimePostHitForcedMovementApplication.apply(
                state, choice(source, target, move), true
        ).isPresent());
        assertEquals(new GridCoord(3, 1), target.position());
    }

    @Test
    void ingrainStatusPreventsResolvedForcedMovement() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("ram", List.of("push 2"), "Push the target 2 meters.");
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(source, target),
                Map.of("target", Set.of("Ingrain")), Map.of(), Map.of(), Map.of(), Map.of("source", List.of(move))
        );

        assertTrue(RuntimePostHitForcedMovementApplication.apply(
                state, choice(source, target, move), true
        ).isEmpty());
        assertEquals(new GridCoord(2, 1), target.position());
    }

    @Test
    void activePushImmunityPreventsPushAtExpiryRound() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        target.temporaryEffects().add("push_immunity", Map.of(
                "expires_round", 4,
                "source", "Anchor Rule"
        ));
        MoveOption move = move("ram", List.of("push"), "");
        BattleRuntimeState state = state(source, target, move);
        state.syncCurrentRoundFromLifecycle(4);

        assertTrue(RuntimePostHitForcedMovementApplication.apply(
                state, choice(source, target, move), true
        ).isEmpty());
        assertEquals(new GridCoord(2, 1), target.position());
        assertEquals(1, target.temporaryEffects().count("push_immunity"));
    }

    @Test
    void expiredPushImmunityIsPrunedAndDoesNotPreventPush() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        target.temporaryEffects().add("push_immunity", Map.of("expires_round", 3));
        MoveOption move = move("ram", List.of("push"), "");
        BattleRuntimeState state = state(source, target, move);
        state.syncCurrentRoundFromLifecycle(4);

        assertTrue(RuntimePostHitForcedMovementApplication.apply(
                state, choice(source, target, move), true
        ).isPresent());
        assertEquals(new GridCoord(3, 1), target.position());
        assertFalse(target.temporaryEffects().has("push_immunity"));
    }

    @Test
    void stillRequiresServerOwnedCanonicalMove() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("ram", List.of("push"), "");
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(source, target),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
        );

        try {
            RuntimePostHitForcedMovementApplication.apply(state, choice(source, target, move), true);
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("no canonical moveset"));
            assertEquals(new GridCoord(2, 1), target.position());
            return;
        }
        throw new AssertionError("expected canonical moveset rejection");
    }

    private static MoveChoice choice(RuntimeCombatantState source, RuntimeCombatantState target, MoveOption move) {
        return new MoveChoice(
                source.combatantId(), move.moveId(), ChoiceTargetMode.COMBATANT,
                target.combatantId(), target.position(), move.actionType()
        );
    }

    private static MoveOption move(String moveId, List<String> keywords, String effectsText) {
        return new MoveOption(
                moveId,
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee", keywords, effectsText),
                ActionType.STANDARD,
                true
        );
    }

    private static BattleRuntimeState state(
            RuntimeCombatantState source,
            RuntimeCombatantState target,
            MoveOption move
    ) {
        return new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(source, target),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of("source", List.of(move))
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return combatant(id, x, y, List.of());
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
