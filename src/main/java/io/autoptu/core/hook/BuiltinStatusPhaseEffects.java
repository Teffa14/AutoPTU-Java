package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.TurnPhase;

import java.util.List;
import java.util.Set;

/** Built-in phase-scoped status rules frozen from the Python AutoPTU oracle. */
public final class BuiltinStatusPhaseEffects {
    private BuiltinStatusPhaseEffects() {}

    /**
     * Registers the base Flinch/Flinched START effect.
     *
     * Python emits a semantic status event with effect=flinch and skip_turn=true;
     * StatusController later consumes that pending skip after all phase effects run.
     * Status expiry/immunity/Steadfast branches remain separate bounded slices.
     */
    public static StatusPhaseEffectRegistry flinchRegistry() {
        return StatusPhaseEffectRegistry.builder()
                .register(
                        "status.flinch.start",
                        Set.of("flinch", "flinched"),
                        TurnPhase.START,
                        100,
                        (context, status) -> {
                            String actorId = context.actorId();
                            int hp = context.state().requireCombatant(actorId).hp();
                            RuleEffectEvent event = new RuleEffectEvent(
                                    "status",
                                    status,
                                    actorId,
                                    "",
                                    "",
                                    "flinch",
                                    0.0,
                                    hp
                            );
                            PendingStatusSkipRequest pending = new PendingStatusSkipRequest(
                                    status,
                                    TurnPhase.START,
                                    "flinch"
                            );
                            return new LifecycleHookResult(List.of(event), pending);
                        }
                )
                .build();
    }
}
