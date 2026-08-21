package io.autoptu.core.runtime;

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

class RuntimeAuthoritativeDelayedHitTest {
    @Test
    void maturedDelayedHitReDerivesCombatInputsFromCurrentRuntimeState() {
        MoveOption move = move();
        BattleRuntimeState first = state(move);
        BattleRuntimeState second = state(move);
        spendSchedulingResources(first.requireCombatant("actor"), move);
        spendSchedulingResources(second.requireCombatant("actor"), move);

        DelayedHitEntry entry = new DelayedHitEntry(
                "actor", move.moveId(), "target", null, 3, "future_sight"
        );
        DelayedHitBinding firstBinding = DelayedHitBindingResolver.bind(first, entry);
        DelayedHitBinding secondBinding = DelayedHitBindingResolver.bind(second, entry);

        MoveResolutionInput forged = new MoveResolutionInput(
                19, 99, -6, 1, true, true, true,
                1, 999, 1, true, 1.0,
                List.of(new io.autoptu.core.model.AttackModifier("forged", 500, 9.0))
        );
        MoveResolutionInput neutralLegacy = new MoveResolutionInput(
                2, 0, 0, 20, false, false, false,
                8, 30, 12, false, 1.0, List.of()
        );

        AppliedActionResult forgedResult = RuntimeMoveResolution.applyDelayedUsingAuthoritativeCombatState(
                first, firstBinding, "Delayed", new PythonRandom(7), forged, false, false
        );
        AppliedActionResult neutralResult = RuntimeMoveResolution.applyDelayedUsingAuthoritativeCombatState(
                second, secondBinding, "Delayed", new PythonRandom(7), neutralLegacy, false, false
        );

        MoveResolvedEvent forgedEvent = (MoveResolvedEvent) forgedResult.events().getLast();
        MoveResolvedEvent neutralEvent = (MoveResolvedEvent) neutralResult.events().getLast();
        assertEquals(neutralEvent.stableKey(), forgedEvent.stableKey());
        assertEquals(second.requireCombatant("target").hp(), first.requireCombatant("target").hp());

        RuntimeCombatantState actor = first.requireCombatant("actor");
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses(move.moveId()));
        assertTrue(first.requireCombatant("target").hp() < 100);
        assertEquals(
                100 - first.requireCombatant("target").hp(),
                first.damageHistory().damageReceivedThisRound().get("target")
        );
    }

    private static void spendSchedulingResources(RuntimeCombatantState actor, MoveOption move) {
        assertTrue(actor.actionBudget().consume(ActionType.STANDARD, move.moveId()));
        actor.moveFrequencyUsage().recordUse(move);
    }

    private static BattleRuntimeState state(MoveOption move) {
        CombatantStatProfile actorStats = profile(30, 8, 18, 8, 10);
        CombatantStatProfile targetStats = profile(12, 12, 12, 12, 10);
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 60, actorStats);
        RuntimeCombatantState target = combatant("target", new GridCoord(4, 1), 100, targetStats);
        return new BattleRuntimeState(
                new MovementGrid(20, 20, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of("actor", List.of(move))
        );
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            int hp,
            CombatantStatProfile stats
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                hp,
                100,
                new ActionBudget(),
                stats,
                new EvasionProfile(stats, 0, 0, 0, false, false)
        );
    }

    private static CombatantStatProfile profile(
            int attack,
            int defense,
            int specialAttack,
            int specialDefense,
            int speed
    ) {
        return new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, attack,
                        CombatStat.DEF, defense,
                        CombatStat.SPATK, specialAttack,
                        CombatStat.SPDEF, specialDefense,
                        CombatStat.SPD, speed
                ),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveOption move() {
        return new MoveOption(
                "future-sight",
                new MoveSpec("Ranged", "Ranged", 20, 20, null, null, "Ranged"),
                ActionType.STANDARD,
                true,
                new MoveCombatProfile(2, 8, 20, "physical"),
                "Scene x1"
        );
    }
}
