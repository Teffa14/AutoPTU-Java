package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composes the broad Pokemon phase-effect families in Python-oracle order.
 *
 * Family implementations remain generic lifecycle hooks. This dispatcher owns
 * only cross-family ordering and Python's single pending-status-skip slot: a
 * later family replaces an earlier pending skip while all semantic events stay
 * ordered.
 */
public final class CombatantPhaseEffectDispatcher implements LifecycleHook {
    private final Map<CombatantPhaseEffectFamily, LifecycleHook> families;

    private CombatantPhaseEffectDispatcher(Map<CombatantPhaseEffectFamily, LifecycleHook> families) {
        this.families = Map.copyOf(families);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        List<BattleEvent> events = new ArrayList<>();
        PendingStatusSkipRequest pendingStatusSkip = null;

        for (CombatantPhaseEffectFamily family : CombatantPhaseEffectFamily.values()) {
            LifecycleHook hook = families.get(family);
            if (hook == null) {
                continue;
            }
            LifecycleHookResult result = Objects.requireNonNull(hook.apply(context), "family hook result");
            events.addAll(result.events());
            if (result.pendingStatusSkip() != null) {
                pendingStatusSkip = result.pendingStatusSkip();
            }
        }
        return new LifecycleHookResult(events, pendingStatusSkip);
    }

    public static final class Builder {
        private final EnumMap<CombatantPhaseEffectFamily, LifecycleHook> families =
                new EnumMap<>(CombatantPhaseEffectFamily.class);

        public Builder family(CombatantPhaseEffectFamily family, LifecycleHook hook) {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(hook, "hook");
            if (families.putIfAbsent(family, hook) != null) {
                throw new IllegalArgumentException("duplicate combatant phase family: " + family);
            }
            return this;
        }

        public CombatantPhaseEffectDispatcher build() {
            return new CombatantPhaseEffectDispatcher(families);
        }
    }
}
