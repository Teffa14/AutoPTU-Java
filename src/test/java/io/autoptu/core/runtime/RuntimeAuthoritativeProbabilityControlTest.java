package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeProbabilityControlTest {
    @Test
    void probabilityControlComesFromActorStateAndIsConsumedAfterMiss() {
        BattleRuntimeState derivedState = state(true);
        BattleRuntimeState explicitState = state(false);

        MoveResolvedEvent derived = resolveAuthoritative(derivedState, input(false), hardMove());
        MoveResolvedEvent explicit = resolveExplicit(explicitState, input(true), hardMove());

        assertEquals(explicit.stableKey(), derived.stableKey());
        assertFalse(derivedState.requireCombatant("actor").probabilityControl());
    }

    @Test
    void probabilityControlIsNotConsumedWhenFirstRollHits() {
        BattleRuntimeState state = state(true);
        resolveAuthoritative(state, input(false), easyMove());
        assertTrue(state.requireCombatant("actor").probabilityControl());
    }

    @Test
    void adapterCannotForgeProbabilityControl() {
        BattleRuntimeState derivedState = state(false);
        BattleRuntimeState explicitState = state(false);

        MoveResolvedEvent derived = resolveAuthoritative(derivedState, input(true), hardMove());
        MoveResolvedEvent explicit = resolveExplicit(explicitState, input(false), hardMove());

        assertEquals(explicit.stableKey(), derived.stableKey());
        assertFalse(derivedState.requireCombatant("actor").probabilityControl());
    }

    private static MoveResolvedEvent resolveAuthoritative(BattleRuntimeState state, MoveResolutionInput input, MoveOption move) {
        return (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state, choice(move.moveId()), move, "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(4), input, false, false
        ).events().getFirst();
    }

    private static MoveResolvedEvent resolveExplicit(BattleRuntimeState state, MoveResolutionInput input, MoveOption move) {
        return (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeEvasion(
                state, choice(move.moveId()), move, "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(4), input, false, false
        ).events().getFirst();
    }

    private static BattleRuntimeState state(boolean probabilityControl) {
        CombatantStatProfile actorStats = stats(20, 8);
        CombatantStatProfile targetStats = stats(12, 20);
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3), 50, 100,
                new ActionBudget(), actorStats, null, 0, false, false, false, probabilityControl
        );
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 100, 100,
                new ActionBudget(), targetStats,
                new EvasionProfile(targetStats, 0, 0, 0, false, false), 0, false, false, false, false
        );
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }

    private static CombatantStatProfile stats(int attack, int defense) {
        return new CombatantStatProfile(Map.of(CombatStat.ATK, attack, CombatStat.DEF, defense), Map.of(), Map.of(), Set.of());
    }

    private static MoveResolutionInput input(boolean rerollOnMiss) {
        return new MoveResolutionInput(2, 0, 0, 20, false, false, rerollOnMiss, 5, 999, 999, false, 1.0, List.of());
    }

    private static MoveChoice choice(String moveId) {
        return new MoveChoice("actor", moveId, ChoiceTargetMode.COMBATANT, "enemy", new GridCoord(2, 1), ActionType.STANDARD);
    }

    private static MoveOption hardMove() {
        return MoveOption.standard("hard-hit", new MoveSpec("Ranged", "Ranged", 1, 1, null, null, "Ranged"), new MoveCombatProfile(25, 5, 20, "physical"));
    }

    private static MoveOption easyMove() {
        return MoveOption.standard("easy-hit", new MoveSpec("Ranged", "Ranged", 1, 1, null, null, "Ranged"), new MoveCombatProfile(2, 5, 20, "physical"));
    }
}
