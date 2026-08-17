package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
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

class RuntimeMoveResolutionTest {
    @Test
    void derivesAttackAndDefenseFromAuthoritativeCombatantProfiles() {
        BattleRuntimeState derivedState = stateWithStats();
        BattleRuntimeState explicitState = stateWithStats();
        MoveResolutionInput untrustedStats = input(999, 999);
        MoveResolutionInput expectedStats = input(20, 10);

        MoveResolvedEvent derived = (MoveResolvedEvent) RuntimeMoveResolution.applyUsingStateStats(
                derivedState,
                choice(),
                move(),
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                untrustedStats,
                "physical",
                false,
                false
        ).events().getFirst();

        MoveResolvedEvent explicit = (MoveResolvedEvent) BattleRuntime.applyAuthoritativeMove(
                explicitState,
                choice(),
                move(),
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                expectedStats
        ).events().getFirst();

        assertEquals(explicit.stableKey(), derived.stableKey());
        assertEquals(derived.targetHp(), derivedState.requireCombatant("enemy").hp());
    }

    @Test
    void rejectsMissingAuthoritativeStatProfileBeforeMutation() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                50,
                50,
                new ActionBudget()
        );
        RuntimeCombatantState enemy = combatant("enemy", new GridCoord(2, 1), 100, profile(10, 10));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy)
        );

        assertThrows(IllegalStateException.class, () -> RuntimeMoveResolution.applyUsingStateStats(
                state,
                choice(),
                move(),
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                input(20, 10),
                "physical",
                false,
                false
        ));
        assertEquals(100, state.requireCombatant("enemy").hp());
        assertEquals(true, state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    private static BattleRuntimeState stateWithStats() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 50, profile(20, 8));
        RuntimeCombatantState enemy = combatant("enemy", new GridCoord(2, 1), 100, profile(12, 10));
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy)
        );
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            int hp,
            CombatantStatProfile profile
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                hp,
                100,
                new ActionBudget(),
                profile
        );
    }

    private static CombatantStatProfile profile(int attack, int defense) {
        return new CombatantStatProfile(
                Map.of(CombatStat.ATK, attack, CombatStat.DEF, defense),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveResolutionInput input(int attack, int defense) {
        return new MoveResolutionInput(
                5,
                0,
                0,
                20,
                false,
                false,
                false,
                10,
                attack,
                defense,
                false,
                1.0,
                List.of()
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor",
                "tackle",
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );
    }

    private static MoveOption move() {
        return MoveOption.standard(
                "tackle",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee")
        );
    }
}
