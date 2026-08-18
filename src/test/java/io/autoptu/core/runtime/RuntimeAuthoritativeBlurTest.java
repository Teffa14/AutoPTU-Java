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

class RuntimeAuthoritativeBlurTest {
    @Test
    void blurComesFromTargetRuntimeStateForAutomaticMoves() {
        BattleRuntimeState derivedState = state(true);
        BattleRuntimeState explicitState = state(false);

        MoveResolvedEvent derived = resolveAuthoritative(derivedState, input(false));
        MoveResolvedEvent explicit = resolveExplicit(explicitState, input(true));

        assertTrue(derivedState.requireCombatant("enemy").blur());
        assertEquals(explicit.stableKey(), derived.stableKey());
        assertEquals(derived.targetHp(), derivedState.requireCombatant("enemy").hp());
    }

    @Test
    void adapterCannotForgeBlurWhenTargetDoesNotHaveIt() {
        BattleRuntimeState derivedState = state(false);
        BattleRuntimeState explicitState = state(false);

        MoveResolvedEvent derived = resolveAuthoritative(derivedState, input(true));
        MoveResolvedEvent explicit = resolveExplicit(explicitState, input(false));

        assertFalse(derivedState.requireCombatant("enemy").blur());
        assertEquals(explicit.stableKey(), derived.stableKey());
    }

    @Test
    void legacyConstructorsDefaultBlurToFalse() {
        CombatantStatProfile stats = stats(10, 10);
        RuntimeCombatantState combatant = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3),
                50, 100, new ActionBudget(), stats,
                new EvasionProfile(stats, 0, 0, 0, false, false), 0, false, false
        );

        assertFalse(combatant.blur());
    }

    private static MoveResolvedEvent resolveAuthoritative(BattleRuntimeState state, MoveResolutionInput input) {
        return (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state, choice(), move(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(4), input, false, false
        ).events().getFirst();
    }

    private static MoveResolvedEvent resolveExplicit(BattleRuntimeState state, MoveResolutionInput input) {
        return (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeEvasion(
                state, choice(), move(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(4), input, false, false
        ).events().getFirst();
    }

    private static BattleRuntimeState state(boolean blur) {
        CombatantStatProfile actorStats = stats(20, 8);
        CombatantStatProfile targetStats = stats(12, 20);
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3),
                50, 100, new ActionBudget(), actorStats, null, 0, false, false, false
        );
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3),
                100, 100, new ActionBudget(), targetStats,
                new EvasionProfile(targetStats, 5, 0, 0, false, false), 0, false, false, blur
        );
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }

    private static CombatantStatProfile stats(int attack, int defense) {
        return new CombatantStatProfile(
                Map.of(CombatStat.ATK, attack, CombatStat.DEF, defense),
                Map.of(), Map.of(), Set.of()
        );
    }

    private static MoveResolutionInput input(boolean blurApplies) {
        return new MoveResolutionInput(
                null, -99, 0, 20, false, blurApplies, false,
                1, 999, 999, false, 1.0, List.of()
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor", "sure-hit", ChoiceTargetMode.COMBATANT, "enemy",
                new GridCoord(2, 1), ActionType.STANDARD
        );
    }

    private static MoveOption move() {
        return MoveOption.standard(
                "sure-hit",
                new MoveSpec("Ranged", "Ranged", 1, 1, null, null, "Ranged"),
                new MoveCombatProfile(null, 5, 20, "physical")
        );
    }
}
