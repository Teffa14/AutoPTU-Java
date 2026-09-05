package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs an actor-oriented ability phase registry across the authoritative battle roster.
 *
 * Python has phase effects whose trigger point is global even though each rule is owned
 * by one combatant (for example Corrosive Toxins during END). This hook preserves stable
 * battle insertion order while reusing the same ability registry contract used by
 * ordinary actor-scoped phase effects.
 */
public final class GlobalAbilityPhaseEffectHook implements LifecycleHook {
    private final AbilityPhaseEffectRegistry registry;

    public GlobalAbilityPhaseEffectHook(AbilityPhaseEffectRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        ArrayList<BattleEvent> events = new ArrayList<>();
        PendingStatusSkipRequest pendingStatusSkip = null;

        for (String combatantId : context.state().combatantIds()) {
            LifecycleHookContext combatantContext = new LifecycleHookContext(
                    context.state(),
                    context.damageHistory(),
                    context.injuryHistory(),
                    context.point(),
                    context.previousRound(),
                    context.round(),
                    combatantId,
                    context.phase()
            );
            LifecycleHookResult result = registry.resolve(combatantContext);
            events.addAll(result.events());
            if (result.pendingStatusSkip() != null) {
                pendingStatusSkip = result.pendingStatusSkip();
            }
        }

        return new LifecycleHookResult(List.copyOf(events), pendingStatusSkip);
    }
}
