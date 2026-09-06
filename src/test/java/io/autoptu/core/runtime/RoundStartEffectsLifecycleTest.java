package io.autoptu.core.runtime;

import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.LifecycleHookResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundStartEffectsLifecycleTest {
    @Test
    @SuppressWarnings("deprecation")
    void roundStartEffectsRunAfterInitiativeSetupAndBeforeFirstActorSelection() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
        );
        ArrayList<String> order = new ArrayList<>();
        LifecycleHookRegistry hooks = LifecycleHookRegistry.builder()
                .register(
                        "post-initiative-probe",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.ROUND_START_POST_INITIATIVE,
                        1,
                        context -> {
                            order.add("post_initiative");
                            assertEquals(List.of("actor"), context.state().initiativeProgress().orderedActorIds());
                            return LifecycleHookResult.empty();
                        }
                )
                .register(
                        "round-start-effects-probe",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.ROUND_START_EFFECTS,
                        1,
                        context -> {
                            order.add("round_start_effects");
                            assertEquals(List.of("actor"), context.state().initiativeProgress().orderedActorIds());
                            assertEquals(-1, context.state().initiativeProgress().cursor());
                            return LifecycleHookResult.empty();
                        }
                )
                .register(
                        "turn-start-probe",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.TURN_START,
                        1,
                        context -> {
                            order.add("turn_start");
                            return LifecycleHookResult.empty();
                        }
                )
                .build();

        BattleRoundController rounds = new BattleRoundController(state, 0, hooks);
        InitiativeTurnAdvanceResult result = rounds.advanceInitiativeTurnWithRollover((runtime, round) -> {
            order.add("initiative_rebuild");
            assertEquals(1, round);
            return List.of("actor");
        });

        assertTrue(result.hasActor());
        assertEquals("actor", result.actorId());
        assertEquals(
                List.of("initiative_rebuild", "post_initiative", "round_start_effects", "turn_start"),
                order
        );
    }
}
