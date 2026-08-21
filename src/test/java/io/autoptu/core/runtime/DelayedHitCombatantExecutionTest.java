package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.model.ActionType;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedHitCombatantExecutionTest {
    @Test
    void maturedCombatantHitUsesOrdinaryAttackPipelineWithoutDoubleSpendingResources() {
        MoveCombatProfile profile = new MoveCombatProfile(2, 8, 20, "special");
        MoveOption move = new MoveOption(
                "future-sight",
                new MoveSpec("Ranged", "Ranged", 20, 20, null, null, "Ranged"),
                ActionType.STANDARD,
                true,
                profile,
                "Scene x1"
        );
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 60);
        RuntimeCombatantState target = combatant("target", new GridCoord(5, 1), 100);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(20, 20, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of("actor", List.of(move))
        );

        assertTrue(actor.actionBudget().consume(ActionType.STANDARD, move.moveId()));
        actor.moveFrequencyUsage().recordUse(move);
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses(move.moveId()));

        DelayedHitEntry entry = new DelayedHitEntry(
                "actor", move.moveId(), "target", null, 3, "future_sight"
        );
        DelayedHitBinding binding = DelayedHitBindingResolver.bind(state, entry);
        MoveResolutionInput input = new MoveResolutionInput(
                profile.ac(), 0, 0, profile.critRange(), false, false, false,
                profile.damageBase(), 20, 10, false, 1.0, List.of()
        );

        AppliedActionResult result = BattleRuntime.applyDelayedAuthoritativeMove(
                state,
                binding,
                "Delayed",
                new PythonRandom(7),
                input,
                List.of(),
                PostDamageHookRegistry.builder().build(),
                profile
        );

        MoveResolvedEvent event = assertInstanceOf(MoveResolvedEvent.class, result.events().getLast());
        assertTrue(event.hit());
        assertEquals(target.hp(), event.targetHp());
        assertTrue(target.hp() < 100);
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses(move.moveId()));
        assertEquals(100 - target.hp(), state.damageHistory().damageReceivedThisRound().get("target"));
        assertEquals(Set.of("target"), state.damageHistory().damageThisRound());
        assertEquals(Set.of("actor"), state.damageHistory().damageTakenFromThisRound().get("target"));
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, int hp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                hp,
                hp,
                new ActionBudget()
        );
    }
}
