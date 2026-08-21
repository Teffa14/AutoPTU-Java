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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedHitLifecycleExecutorTest {
    @Test
    void resolvesDueCombatantHitsFromBattleOwnedStateWithoutDoubleSpendingResources() {
        MoveOption move = move();
        BattleRuntimeState state = state(move, 7L);
        state.syncCurrentRoundFromLifecycle(3);
        RuntimeCombatantState actor = state.requireCombatant("actor");
        assertTrue(actor.actionBudget().consume(ActionType.STANDARD, move.moveId()));
        actor.moveFrequencyUsage().recordUse(move);

        state.scheduleDelayedHitFromRuntime(new DelayedHitEntry(
                "actor", move.moveId(), "target", null, 3, "future_sight"
        ));
        state.scheduleDelayedHitFromRuntime(new DelayedHitEntry(
                "actor", move.moveId(), "target", null, 5, "future_sight"
        ));

        List<BattleEvent> events = DelayedHitLifecycleExecutor.resolveDueCombatantHits(state, 3);

        assertFalse(events.isEmpty());
        assertInstanceOf(MoveResolvedEvent.class, events.getLast());
        assertEquals(1, state.delayedHits().size());
        assertEquals(5, state.delayedHits().getFirst().triggerRound());
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses(move.moveId()));
        assertTrue(state.requireCombatant("target").hp() < 100);
        assertEquals(
                100 - state.requireCombatant("target").hp(),
                state.damageHistory().damageReceivedThisRound().get("target")
        );
    }

    @Test
    void unsupportedDueTileHitFailsBeforeRemovingAnythingFromBattleOwnedQueue() {
        MoveOption move = move();
        BattleRuntimeState state = state(move, 7L);
        state.syncCurrentRoundFromLifecycle(3);
        state.scheduleDelayedHitFromRuntime(new DelayedHitEntry(
                "actor", move.moveId(), null, new GridCoord(4, 1), 3, "future_sight"
        ));

        assertThrows(
                UnsupportedOperationException.class,
                () -> DelayedHitLifecycleExecutor.resolveDueCombatantHits(state, 3)
        );
        assertEquals(1, state.delayedHits().size());
    }

    @Test
    void battleSeedOwnsTheDelayedHitRngStream() {
        MoveOption move = move();
        BattleRuntimeState first = state(move, 91L);
        BattleRuntimeState second = state(move, 91L);
        first.syncCurrentRoundFromLifecycle(2);
        second.syncCurrentRoundFromLifecycle(2);
        DelayedHitEntry entry = new DelayedHitEntry(
                "actor", move.moveId(), "target", null, 2, "future_sight"
        );
        first.scheduleDelayedHitFromRuntime(entry);
        second.scheduleDelayedHitFromRuntime(entry);

        List<BattleEvent> firstEvents = DelayedHitLifecycleExecutor.resolveDueCombatantHits(first, 2);
        List<BattleEvent> secondEvents = DelayedHitLifecycleExecutor.resolveDueCombatantHits(second, 2);

        assertEquals(firstEvents, secondEvents);
        assertEquals(first.requireCombatant("target").hp(), second.requireCombatant("target").hp());
        assertEquals(first.damageHistory().damageReceivedThisRound(), second.damageHistory().damageReceivedThisRound());
    }

    private static BattleRuntimeState state(MoveOption move, long battleSeed) {
        CombatantStatProfile actorStats = profile(30, 8, 18, 8, 10);
        CombatantStatProfile targetStats = profile(12, 12, 12, 12, 10);
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 60, actorStats);
        RuntimeCombatantState target = combatant("target", new GridCoord(4, 1), 100, targetStats);
        return new BattleRuntimeState(
                new MovementGrid(20, 20, Set.of(), Map.of()),
                List.of(actor, target),
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
                true,
                new MoveCombatProfile(2, 8, 20, "physical"),
                "Scene x1"
        );
    }
}
