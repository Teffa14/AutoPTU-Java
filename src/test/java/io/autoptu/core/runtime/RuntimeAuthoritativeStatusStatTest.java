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
import io.autoptu.core.model.StatFlag;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeStatusStatTest {
    @Test
    void targetBurnLowersPhysicalDefenseFromCanonicalStatus() {
        MoveResolvedEvent burned = resolve(state(Set.of(), Set.of("Burned"), Set.of()), "Physical", 331);
        MoveResolvedEvent clean = resolve(state(Set.of(), Set.of(), Set.of()), "Physical", 331);

        assertTrue(burned.damage() > clean.damage());
    }

    @Test
    void targetPoisonLowersSpecialDefenseFromCanonicalStatus() {
        MoveResolvedEvent poisoned = resolve(state(Set.of(), Set.of("Poisoned"), Set.of()), "Special", 419);
        MoveResolvedEvent clean = resolve(state(Set.of(), Set.of(), Set.of()), "Special", 419);

        assertTrue(poisoned.damage() > clean.damage());
    }

    @Test
    void staleTargetBurnFlagCannotForgePenaltyWithoutCanonicalStatus() {
        MoveResolvedEvent stale = resolve(state(Set.of(), Set.of(), Set.of(StatFlag.BURNED)), "Physical", 503);
        MoveResolvedEvent clean = resolve(state(Set.of(), Set.of(), Set.of()), "Physical", 503);

        assertEquals(clean.stableKey(), stale.stableKey());
    }

    private static MoveResolvedEvent resolve(BattleRuntimeState state, String category, int seed) {
        return (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state,
                choice(),
                move(category),
                "Medium",
                "Medium",
                Set.of(),
                "AI",
                new PythonRandom(seed),
                input(),
                false,
                false
        ).events().getFirst();
    }

    private static BattleRuntimeState state(
            Set<String> actorStatuses,
            Set<String> targetStatuses,
            Set<StatFlag> targetFlags
    ) {
        CombatantStatProfile actorStats = stats(24, 12, 24, 12, Set.of());
        CombatantStatProfile targetStats = stats(12, 18, 12, 20, targetFlags);
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                100,
                100,
                new ActionBudget(),
                actorStats,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of("Normal")
        );
        RuntimeCombatantState target = new RuntimeCombatantState(
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
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of("actor", actorStatuses, "enemy", targetStatuses)
        );
    }

    private static CombatantStatProfile stats(
            int attack,
            int defense,
            int specialAttack,
            int specialDefense,
            Set<StatFlag> flags
    ) {
        return new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, attack,
                        CombatStat.DEF, defense,
                        CombatStat.SPATK, specialAttack,
                        CombatStat.SPDEF, specialDefense,
                        CombatStat.SPD, 16
                ),
                Map.of(),
                Map.of(),
                flags
        );
    }

    private static MoveResolutionInput input() {
        return new MoveResolutionInput(
                99,
                -99,
                -6,
                20,
                false,
                false,
                false,
                99,
                999,
                1,
                false,
                1.0,
                List.of()
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor",
                "test-move",
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );
    }

    private static MoveOption move(String category) {
        return MoveOption.standard(
                "test-move",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 6, 20, category, "Normal")
        );
    }
}
