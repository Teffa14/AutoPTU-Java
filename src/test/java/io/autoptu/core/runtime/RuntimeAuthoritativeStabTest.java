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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeStabTest {
    @Test
    void adapterCannotForgeEffectiveDamageBaseWhenAuthoritativeTypingExists() {
        BattleRuntimeState forgedState = state(List.of("Fire"));
        BattleRuntimeState cleanState = state(List.of("Fire"));

        MoveResolvedEvent forged = resolve(forgedState, 99, 31);
        MoveResolvedEvent clean = resolve(cleanState, 0, 31);

        assertEquals(clean.stableKey(), forged.stableKey());
        assertEquals(cleanState.requireCombatant("enemy").hp(), forgedState.requireCombatant("enemy").hp());
    }

    @Test
    void matchingAuthoritativeTypeAddsTwoDamageBaseBeforeDamageResolution() {
        BattleRuntimeState stabState = state(List.of("Fire"));
        BattleRuntimeState offTypeState = state(List.of("Water"));

        MoveResolvedEvent stab = resolve(stabState, 1, 47);
        MoveResolvedEvent offType = resolve(offTypeState, 1, 47);

        assertTrue(stab.damage() > offType.damage());
        assertTrue(stabState.requireCombatant("enemy").hp() < offTypeState.requireCombatant("enemy").hp());
    }

    private static MoveResolvedEvent resolve(BattleRuntimeState state, int forgedDb, int seed) {
        return (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state, choice(), fireMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(seed), input(forgedDb), false, false
        ).events().getFirst();
    }

    private static BattleRuntimeState state(List<String> actorTypes) {
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
                0,
                false,
                false,
                false,
                false,
                actorTypes
        );
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy",
                MovementProfile.walking(new GridCoord(2, 1), 3),
                100,
                100,
                new ActionBudget(),
                targetStats,
                new EvasionProfile(targetStats, 0, 0, 0, false, false),
                0,
                false,
                false,
                false,
                false,
                List.of("Normal")
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

    private static MoveResolutionInput input(int forgedDb) {
        return new MoveResolutionInput(
                99,
                -99,
                -6,
                20,
                false,
                false,
                false,
                forgedDb,
                999,
                999,
                false,
                1.0,
                List.of()
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor", "flame-strike", ChoiceTargetMode.COMBATANT, "enemy",
                new GridCoord(2, 1), ActionType.STANDARD
        );
    }

    private static MoveOption fireMove() {
        return MoveOption.standard(
                "flame-strike",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 6, 20, "physical", "Fire")
        );
    }
}
