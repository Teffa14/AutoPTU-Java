package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.LifecycleHookResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleRoundControllerHookTest {
    @Test
    void roundStartExecutesStatefulHooksAndReturnsOrderedPlaybackEvents() {
        RuntimeCombatantState actor = combatant();
        BattleRuntimeState state = state(actor);
        MoveOption eot = MoveOption.standardWithFrequency("eot", move(), "EOT");
        actor.moveFrequencyUsage().recordUse(eot);

        LifecycleHookRegistry hooks = LifecycleHookRegistry.builder()
                .register("round-frequency", HookSource.SYSTEM, LifecycleHookPoint.ROUND_START, 10, context -> {
                    BattleRuntime.resetRoundMoveFrequency(context.state());
                    return LifecycleHookResult.empty();
                })
                .register("playback", HookSource.ABILITY, LifecycleHookPoint.ROUND_START, 20, context ->
                        LifecycleHookResult.events(List.of(new RuleEffectEvent(
                                "ability", "Round Test", "actor", "", "", "round_start", context.round(), actor.hp()
                        )))
                )
                .build();

        BattleRoundController controller = new BattleRoundController(state, 4, hooks);
        RoundStartResult result = controller.startRoundWithEvents();

        assertEquals(5, result.round());
        assertEquals(0, actor.moveFrequencyUsage().roundUses("eot"));
        assertEquals(1, result.events().size());
        assertEquals("round_start", ((RuleEffectEvent) result.events().getFirst()).effect());
        assertEquals(5.0, ((RuleEffectEvent) result.events().getFirst()).amount());
    }

    private static BattleRuntimeState state(RuntimeCombatantState actor) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
        );
    }

    private static RuntimeCombatantState combatant() {
        return new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
    }

    private static MoveSpec move() {
        return new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee");
    }
}
