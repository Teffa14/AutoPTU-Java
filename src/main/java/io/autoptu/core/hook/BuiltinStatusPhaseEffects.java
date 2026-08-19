package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;
import java.util.Set;

/** Built-in phase-scoped status rules frozen from the Python AutoPTU oracle. */
public final class BuiltinStatusPhaseEffects {
    private BuiltinStatusPhaseEffects() {}

    /**
     * Registers the base Flinch/Flinched START effect.
     *
     * Python expires a metadata-bearing Flinch once battle.round advances past
     * applied_round, emitting status_ends without a pending skip. Otherwise it
     * emits effect=flinch and skip_turn=true; StatusController later consumes
     * that pending skip after all phase effects run. Immunity/Steadfast branches
     * remain separate bounded slices.
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
                            var entry = context.state().statusEntry(actorId, status);
                            if (entry.isPresent()) {
                                var appliedRound = entry.get().intPayload("applied_round");
                                if (appliedRound.isPresent() && context.round() > appliedRound.getAsInt()) {
                                    context.state().removeStatus(actorId, status);
                                    RuleEffectEvent event = new RuleEffectEvent(
                                            "status",
                                            status,
                                            actorId,
                                            "",
                                            "",
                                            "status_ends",
                                            0.0,
                                            hp
                                    );
                                    return new LifecycleHookResult(List.of(event), null);
                                }
                            }

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

    /**
     * Registers the Strange Tempo branch of Confusion/Confused START handling.
     *
     * The Python oracle short-circuits normal Confusion handling when Strange Tempo
     * is active, unless Sleep/Asleep or the canonical sleep_blocked temporary effect
     * suppresses Confusion processing first. No pending skip is created in this branch.
     */
    public static StatusPhaseEffectRegistry strangeTempoRegistry() {
        return StatusPhaseEffectRegistry.builder()
                .register(
                        "status.confusion.strange-tempo.start",
                        Set.of("confusion", "confused"),
                        TurnPhase.START,
                        100,
                        (context, status) -> {
                            String actorId = context.actorId();
                            if (context.state().hasStatus(actorId, "sleep")
                                    || context.state().hasStatus(actorId, "asleep")) {
                                return LifecycleHookResult.empty();
                            }
                            RuntimeCombatantState actor = context.state().requireCombatant(actorId);
                            if (actor.temporaryEffects().count("sleep_blocked") > 0) {
                                return LifecycleHookResult.empty();
                            }
                            if (!AbilityIdentityResolution.matchesRegistration(actor.abilities(), "Strange Tempo")) {
                                return LifecycleHookResult.empty();
                            }
                            RuleEffectEvent event = new RuleEffectEvent(
                                    "ability",
                                    "Strange Tempo",
                                    actorId,
                                    "",
                                    "",
                                    "confusion_control",
                                    0.0,
                                    actor.hp()
                            );
                            return new LifecycleHookResult(List.of(event), null);
                        }
                )
                .build();
    }
}
