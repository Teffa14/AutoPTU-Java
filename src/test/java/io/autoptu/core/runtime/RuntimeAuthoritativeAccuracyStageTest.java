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

class RuntimeAuthoritativeAccuracyStageTest {
    @Test
    void ignoresAdapterAccuracyStageAndUsesClampedActorRuntimeStage() {
        BattleRuntimeState derivedState = state(99);
        BattleRuntimeState explicitState = state(0);

        MoveResolutionInput forged = input(-6);
        MoveResolutionInput expected = input(6);

        MoveResolvedEvent derived = (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                derivedState, choice(), move(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(7), forged, false, false
        ).events().getFirst();

        MoveResolvedEvent explicit = (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeEvasion(
                explicitState, choice(), move(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(7), expected, false, false
        ).events().getFirst();

        assertEquals(6, derivedState.requireCombatant("actor").accuracyStage());
        assertEquals(explicit.stableKey(), derived.stableKey());
        assertEquals(derived.targetHp(), derivedState.requireCombatant("enemy").hp());
    }

    @Test
    void legacyConstructorsDefaultAccuracyStageToZero() {
        CombatantStatProfile stats = stats(10, 10);
        RuntimeCombatantState combatant = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                50,
                100,
                new ActionBudget(),
                stats,
                new EvasionProfile(stats, 0, 0, 0, false, false)
        );

        assertEquals(0, combatant.accuracyStage());
    }

    private static BattleRuntimeState state(int actorAccuracyStage) {
        CombatantStatProfile actorStats = stats(20, 8);
        CombatantStatProfile targetStats = stats(12, 20);
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                50,
                100,
                new ActionBudget(),
                actorStats,
                null,
                actorAccuracyStage
        );
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy",
                MovementProfile.walking(new GridCoord(2, 1), 3),
                100,
                100,
                new ActionBudget(),
                targetStats,
                new EvasionProfile(targetStats, 2, 0, 0, false, false)
        );
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }

    private static CombatantStatProfile stats(int attack, int defense) {
        return new CombatantStatProfile(
                Map.of(CombatStat.ATK, attack, CombatStat.DEF, defense),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveResolutionInput input(int accuracyStage) {
        return new MoveResolutionInput(
                19,
                -99,
                accuracyStage,
                1,
                false,
                false,
                false,
                1,
                999,
                999,
                false,
                1.0,
                List.of()
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor", "tackle", ChoiceTargetMode.COMBATANT, "enemy",
                new GridCoord(2, 1), ActionType.STANDARD
        );
    }

    private static MoveOption move() {
        return MoveOption.standard(
                "tackle",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(5, 10, 20, "physical")
        );
    }
}
