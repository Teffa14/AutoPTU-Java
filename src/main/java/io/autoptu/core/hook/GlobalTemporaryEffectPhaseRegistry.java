package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Ordered server-authoritative registry for phase-scoped temporary effects that are
 * resolved across the whole battle roster.
 *
 * <p>The outer lifecycle context keeps the active turn actor while each registration
 * receives the combatant that owns the temporary effect separately. This distinction
 * is required by Python rules such as Corrosive Toxins, where the effect is stored on
 * the poisoned target but the semantic event actor remains the current turn actor.</p>
 */
public final class GlobalTemporaryEffectPhaseRegistry {
    private final List<Registration> registrations;

    private GlobalTemporaryEffectPhaseRegistry(List<Registration> registrations) {
        ArrayList<Registration> ordered = new ArrayList<>(registrations);
        ordered.sort(Comparator.comparingInt(Registration::order));
        this.registrations = List.copyOf(ordered);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Registration> registrations() {
        return registrations;
    }

    public LifecycleHookResult resolve(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        if (context.point() != LifecycleHookPoint.PHASE_CHANGE) {
            throw new IllegalArgumentException("global temporary effects require PHASE_CHANGE context");
        }
        TurnPhase phase = Objects.requireNonNull(context.phase(), "phase");

        ArrayList<BattleEvent> events = new ArrayList<>();
        PendingStatusSkipRequest pending = null;
        for (String targetId : context.state().combatantIds()) {
            RuntimeCombatantState target = context.state().requireCombatant(targetId);
            for (Registration registration : registrations) {
                if (registration.phase() != phase) continue;
                List<TemporaryEffectEntry> entries = target.temporaryEffects().getAll(registration.effectName());
                if (entries.isEmpty()) continue;

                // Python's phase controller resolves next(iter(get_temporary_effects(name)), None),
                // so only the first matching entry is observed for this target in one traversal.
                LifecycleHookResult result = registration.effect().apply(context, targetId, entries.get(0));
                if (result == null) {
                    throw new IllegalStateException("global temporary effect returned null: " + registration.id());
                }
                events.addAll(result.events());
                if (result.pendingStatusSkip() != null) pending = result.pendingStatusSkip();
            }
        }
        return new LifecycleHookResult(List.copyOf(events), pending);
    }

    @FunctionalInterface
    public interface Effect {
        LifecycleHookResult apply(LifecycleHookContext context, String targetId, TemporaryEffectEntry entry);
    }

    public record Registration(
            String id,
            String effectName,
            TurnPhase phase,
            int order,
            Effect effect
    ) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("global temporary effect id is required");
            if (effectName == null || effectName.isBlank()) throw new IllegalArgumentException("effectName is required");
            id = id.strip();
            effectName = effectName.strip();
            phase = Objects.requireNonNull(phase, "phase");
            effect = Objects.requireNonNull(effect, "effect");
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();

        public Builder register(String id, String effectName, TurnPhase phase, int order, Effect effect) {
            Registration registration = new Registration(id, effectName, phase, order, effect);
            if (!ids.add(registration.id().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("duplicate global temporary effect id: " + registration.id());
            }
            registrations.add(registration);
            return this;
        }

        public GlobalTemporaryEffectPhaseRegistry build() {
            return new GlobalTemporaryEffectPhaseRegistry(registrations);
        }
    }
}
