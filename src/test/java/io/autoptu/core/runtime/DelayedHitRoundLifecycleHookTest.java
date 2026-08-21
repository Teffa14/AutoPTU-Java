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
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedHitRoundLifecycleHookTest {
    @Test
    void roundStartAutomaticallyMaturesCombatantDelayedHitAfterFieldSlot() {
        MoveOption move = move();
        BattleRuntimeState state = state(move, 17L);
        RuntimeCombatantState actor = state.requireCombatant("actor");
        assertTrue(actor.actionBudget().consume(ActionType.STANDARD, move.moveId()));
        actor.moveFrequencyUsage().recordUse(move);
        state.scheduleDelayedHitFromRuntime(new DelayedHitEntry(
                "actor", move.moveId(), "target", new GridCoord(4, 1), 3, "future_sight"
        ));

        RoundStartResult result = new BattleRoundController(state, 2).startRoundWithEvents();

        assertEquals(3, result.round());
        assertTrue(result.events().stream().anyMatch(MoveResolvedEvent.class::isInstance));
        assertInstanceOf(MoveResolvedEvent.class, result.events().stream()
                .filter(MoveResolvedEvent.class::isInstance)
                .findFirst()
                .orElseThrow());
        assertTrue(state.delayedHits().isEmpty());
        assertTrue(state.requireCombatant("target").hp() < 100);
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses(move.moveId()));
        assertTrue(state.damageHistory().damageLastRound().contains("target"));
        assertTrue(state.damageHistory().damageThisRound().isEmpty());
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
                Map.of(), Map.of(), Set.of()
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
