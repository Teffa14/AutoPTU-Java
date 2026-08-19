package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.RuleEffectEvent;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeMegaLauncherTest {
    @Test
    void canonicalMegaLauncherRaisesPulseMoveDamage() {
        AppliedActionResult boosted = resolve(state(List.of("Mega Launcher")), move("water-pulse"), 733);
        AppliedActionResult clean = resolve(state(List.of()), move("water-pulse"), 733);

        assertTrue(moveEvent(boosted).damage() > moveEvent(clean).damage());
    }

    @Test
    void unrelatedMoveIsNotChangedByMegaLauncher() {
        AppliedActionResult boosted = resolve(state(List.of("Mega Launcher")), move("tackle"), 811);
        AppliedActionResult clean = resolve(state(List.of()), move("tackle"), 811);

        assertEquals(moveEvent(clean).stableKey(), moveEvent(boosted).stableKey());
    }

    @Test
    void abilityRuleEffectPrecedesResolvedMove() {
        AppliedActionResult result = resolve(state(List.of("Mega Launcher")), move("aura-sphere"), 919);

        assertEquals(2, result.events().size());
        RuleEffectEvent effect = assertInstanceOf(RuleEffectEvent.class, result.events().get(0));
        assertEquals("ability", effect.sourceKind());
        assertEquals("Mega Launcher", effect.sourceName());
        assertEquals("db_bonus", effect.effect());
        assertEquals(2.0, effect.amount());
        assertInstanceOf(MoveResolvedEvent.class, result.events().get(1));
    }

    private static AppliedActionResult resolve(BattleRuntimeState state, MoveOption move, int seed) {
        MoveChoice choice = new MoveChoice(
                "actor", move.moveId(), ChoiceTargetMode.COMBATANT, "enemy",
                new GridCoord(2, 1), ActionType.STANDARD);
        return RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state,
                choice,
                move,
                "Medium",
                "Medium",
                Set.of(),
                "AI",
                new PythonRandom(seed),
                input(),
                false,
                false
        );
    }

    private static BattleRuntimeState state(List<String> abilities) {
        CombatantStatProfile actorStats = stats(12, 12, 20, 12);
        CombatantStatProfile targetStats = stats(12, 16, 12, 16);
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
                List.of("Normal"),
                List.of(),
                abilities
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
                List.of("Normal"),
                List.of(),
                List.of()
        );
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy)
        );
    }

    private static CombatantStatProfile stats(int attack, int defense, int specialAttack, int specialDefense) {
        return new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, attack,
                        CombatStat.DEF, defense,
                        CombatStat.SPATK, specialAttack,
                        CombatStat.SPDEF, specialDefense
                ),
                Map.of(), Map.of(), Set.of()
        );
    }

    private static MoveResolutionInput input() {
        return new MoveResolutionInput(
                99, -99, -6, 20, false, false, false,
                99, 999, 999, false, 1.0, List.of()
        );
    }

    private static MoveOption move(String moveId) {
        return MoveOption.standard(
                moveId,
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 6, 20, "special", "Water")
        );
    }

    private static MoveResolvedEvent moveEvent(AppliedActionResult result) {
        for (BattleEvent event : result.events()) {
            if (event instanceof MoveResolvedEvent moveResolved) return moveResolved;
        }
        throw new AssertionError("move resolved event missing");
    }
}
