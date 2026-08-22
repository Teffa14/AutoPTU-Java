package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
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
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedHitStaleTargetExecutionTest {
    @Test
    void staleCombatantIdRetargetsCurrentCombatantAtStoredAnchorWithoutDoubleSpending() {
        MoveOption move = move();
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 60, profile(30, 8, 18, 8, 10));
        RuntimeCombatantState replacement = combatant(
                "replacement", new GridCoord(4, 1), 100, profile(12, 12, 12, 12, 10));
        BattleRuntimeState state = state(move, 7L, List.of(actor, replacement));
        state.syncCurrentRoundFromLifecycle(3);
        assertTrue(actor.actionBudget().consume(ActionType.STANDARD, move.moveId()));
        actor.moveFrequencyUsage().recordUse(move);

        state.scheduleDelayedHitFromRuntime(new DelayedHitEntry(
                "actor",
                move.moveId(),
                "missing-target",
                new GridCoord(4, 1),
                3,
                "future_sight"
        ));

        List<BattleEvent> events = DelayedHitLifecycleExecutor.resolveDueCombatantHits(state, 3);

        assertEquals(1, events.size());
        MoveResolvedEvent resolved = assertInstanceOf(MoveResolvedEvent.class, events.getFirst());
        assertEquals("replacement", resolved.targetId());
        assertTrue(resolved.hit());
        assertTrue(resolved.damage() > 0);
        assertEquals(100 - resolved.damage(), replacement.hp());
        assertEquals(resolved.damage(), state.damageHistory().damageReceivedThisRound().get("replacement"));
        assertTrue(state.delayedHits().isEmpty());
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses(move.moveId()));
    }

    @Test
    void staleCombatantIdWithNobodyAtStoredAnchorConsumesDueEntryWithoutInventingTarget() {
        MoveOption move = move();
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 60, profile(30, 8, 18, 8, 10));
        BattleRuntimeState state = state(move, 7L, List.of(actor));
        state.syncCurrentRoundFromLifecycle(3);
        DelayedHitEntry entry = new DelayedHitEntry(
                "actor",
                move.moveId(),
                "missing-target",
                new GridCoord(4, 1),
                3,
                "future_sight"
        );
        state.scheduleDelayedHitFromRuntime(entry);

        List<BattleEvent> events = DelayedHitLifecycleExecutor.resolveDueCombatantHits(state, 3);

        assertTrue(events.isEmpty());
        assertTrue(state.delayedHits().isEmpty());
        assertEquals(60, actor.hp());
        assertTrue(state.damageHistory().damageReceivedThisRound().isEmpty());
    }

    private static BattleRuntimeState state(
            MoveOption move,
            long battleSeed,
            List<RuntimeCombatantState> combatants
    ) {
        return new BattleRuntimeState(
                new MovementGrid(20, 20, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of("actor", List.of(move)), Map.of(),
                battleSeed
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
                false,
                new MoveCombatProfile(2, 8, 20, "physical"),
                "Scene x1"
        );
    }
}
