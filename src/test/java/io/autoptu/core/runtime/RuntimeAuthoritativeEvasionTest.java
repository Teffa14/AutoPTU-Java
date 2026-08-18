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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeEvasionTest {
    @Test
    void ignoresAdapterEvasionAndUsesTargetRuntimeProfile() {
        BattleRuntimeState derivedState = state(true);
        BattleRuntimeState explicitState = state(true);

        MoveResolutionInput forged = input(-99);
        MoveResolutionInput expected = input(6);

        MoveResolvedEvent derived = (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeEvasion(
                derivedState, choice(), move(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(7), forged, false, false
        ).events().getFirst();

        MoveResolvedEvent explicit = (MoveResolvedEvent) RuntimeMoveResolution.applyUsingStateStatsAndMoveMetadata(
                explicitState, choice(), move(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(7), expected, false, false
        ).events().getFirst();

        assertEquals(explicit.stableKey(), derived.stableKey());
        assertEquals(derived.targetHp(), derivedState.requireCombatant("enemy").hp());
    }

    @Test
    void rejectsMissingEvasionProfileBeforeActionOrHpMutation() {
        BattleRuntimeState state = state(false);

        assertThrows(IllegalStateException.class, () -> RuntimeMoveResolution.applyUsingAuthoritativeEvasion(
                state, choice(), move(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(7), input(0), false, false
        ));

        assertEquals(100, state.requireCombatant("enemy").hp());
        assertTrue(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    private static BattleRuntimeState state(boolean includeEvasion) {
        CombatantStatProfile actorStats = stats(20, 8);
        CombatantStatProfile targetStats = stats(12, 20);
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 50, actorStats, null);
        EvasionProfile evasion = includeEvasion
                ? new EvasionProfile(targetStats, 2, 0, 0, false, false)
                : null;
        RuntimeCombatantState enemy = combatant("enemy", new GridCoord(2, 1), 100, targetStats, evasion);
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            int hp,
            CombatantStatProfile stats,
            EvasionProfile evasion
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                hp,
                100,
                new ActionBudget(),
                stats,
                evasion
        );
    }

    private static CombatantStatProfile stats(int attack, int defense) {
        return new CombatantStatProfile(
                Map.of(CombatStat.ATK, attack, CombatStat.DEF, defense),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveResolutionInput input(int evasion) {
        return new MoveResolutionInput(
                19,
                evasion,
                0,
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
