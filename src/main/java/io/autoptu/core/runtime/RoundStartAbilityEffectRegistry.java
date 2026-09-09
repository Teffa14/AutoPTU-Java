package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Server-owned registry for ability families dispatched by {@link RoundStartAbilityDispatchPlan}.
 *
 * <p>The registry may be populated incrementally while the Python port is incomplete. Missing
 * families remain explicit unhandled results in the executor rather than silently acquiring
 * guessed Java behavior.</p>
 */
public final class RoundStartAbilityEffectRegistry {
    @FunctionalInterface
    public interface Handler {
        List<BattleEvent> apply(RoundStartAbilityDispatchPlan.Invocation invocation, BattleRuntimeState state);
    }

    private final LinkedHashMap<String, Handler> handlers = new LinkedHashMap<>();

    public RoundStartAbilityEffectRegistry register(String family, Handler handler) {
        String key = normalizeFamily(family);
        if (key.isEmpty()) throw new IllegalArgumentException("family is required");
        handlers.put(key, Objects.requireNonNull(handler, "handler"));
        return this;
    }

    public boolean supports(String family) {
        return handlers.containsKey(normalizeFamily(family));
    }

    public List<BattleEvent> apply(
            RoundStartAbilityDispatchPlan.Invocation invocation,
            BattleRuntimeState state
    ) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(state, "state");
        Handler handler = handlers.get(normalizeFamily(invocation.family()));
        if (handler == null) return List.of();
        List<BattleEvent> events = handler.apply(invocation, state);
        if (events == null || events.isEmpty()) return List.of();
        ArrayList<BattleEvent> copied = new ArrayList<>();
        for (BattleEvent event : events) {
            if (event == null) throw new IllegalArgumentException("ability handler returned null event");
            copied.add(event);
        }
        return List.copyOf(copied);
    }

    /** Frozen Python families that need only battle state. */
    public static RoundStartAbilityEffectRegistry pythonParityBuiltins() {
        return new RoundStartAbilityEffectRegistry()
                .register(
                        RoundStartAbilityDispatchPlan.AIR_LOCK,
                        RoundStartAbilityEffectRegistry::applyAirLock
                )
                .register(
                        RoundStartAbilityDispatchPlan.INTIMIDATE,
                        RoundStartAbilityEffectRegistry::applyIntimidate
                )
                .register(
                        RoundStartAbilityDispatchPlan.IMPOSTOR,
                        RoundStartAbilityEffectRegistry::applyImpostor
                );
    }

    /**
     * Frozen Python families with access to canonical server-owned rule content.
     *
     * <p>Arena Trap needs generic PTU capabilities in addition to battle state. The supplied
     * context is captured by the registry rather than threaded through Minecraft/Cobblemon or
     * added as an ability-specific controller parameter.</p>
     */
    public static RoundStartAbilityEffectRegistry pythonParityBuiltins(
            RoundStartAbilityExecutionContext context
    ) {
        Objects.requireNonNull(context, "context");
        return pythonParityBuiltins().register(
                RoundStartAbilityDispatchPlan.ARENA_TRAP,
                (invocation, state) -> applyArenaTrap(invocation, state, context)
        );
    }

    private static List<BattleEvent> applyAirLock(
            RoundStartAbilityDispatchPlan.Invocation invocation,
            BattleRuntimeState state
    ) {
        if (invocation.scope() != RoundStartAbilityDispatchPlan.Scope.ABILITY_HOLDER) {
            throw new IllegalArgumentException("Air Lock requires ABILITY_HOLDER scope");
        }
        String weather = state.environment().weather();
        return List.of(new AbilityEvent(
                invocation.actorId(),
                "Air Lock",
                "weather_suppress",
                Map.of(
                        "weather", weather,
                        "description", "Air Lock suppresses the active weather."
                )
        ));
    }

    private static List<BattleEvent> applyIntimidate(
            RoundStartAbilityDispatchPlan.Invocation invocation,
            BattleRuntimeState state
    ) {
        if (invocation.scope() != RoundStartAbilityDispatchPlan.Scope.ACTIVE_ACTOR) {
            throw new IllegalArgumentException("Intimidate requires ACTIVE_ACTOR scope");
        }
        return IntimidateEffectExecutor.apply(
                state,
                invocation.actorId(),
                IntimidateTriggerContract.plan(state, invocation.actorId())
        );
    }

    private static List<BattleEvent> applyImpostor(
            RoundStartAbilityDispatchPlan.Invocation invocation,
            BattleRuntimeState state
    ) {
        if (invocation.scope() != RoundStartAbilityDispatchPlan.Scope.ACTIVE_ACTOR) {
            throw new IllegalArgumentException("Impostor requires ACTIVE_ACTOR scope");
        }
        return ImpostorEffectExecutor.apply(state, invocation.actorId());
    }

    private static List<BattleEvent> applyArenaTrap(
            RoundStartAbilityDispatchPlan.Invocation invocation,
            BattleRuntimeState state,
            RoundStartAbilityExecutionContext context
    ) {
        if (invocation.scope() != RoundStartAbilityDispatchPlan.Scope.GLOBAL) {
            throw new IllegalArgumentException("Arena Trap requires GLOBAL scope");
        }
        if (context.state() != state) {
            throw new IllegalArgumentException("Arena Trap execution context must own the supplied battle state");
        }

        ArrayList<BattleEvent> events = new ArrayList<>();
        for (String holderId : ActiveAbilityHolderResolver.resolve(state, ArenaTrapEffectPlan.ABILITY)) {
            List<ArenaTrapTargetingContract.Candidate> candidates =
                    ArenaTrapRuntimeCandidateProjection.candidatesForHolder(
                            state,
                            context.ruleContent(),
                            holderId
                    );
            List<String> targets = ArenaTrapTargetingContract.regularTargets(
                    state.teamId(holderId),
                    candidates
            );
            events.addAll(StatusEffectMutationExecutor.apply(
                    state,
                    ArenaTrapEffectPlan.statusInstructionsForTargets(holderId, targets)
            ));
        }
        return List.copyOf(events);
    }

    private static String normalizeFamily(String family) {
        return family == null ? "" : family.strip().toLowerCase(Locale.ROOT);
    }
}
