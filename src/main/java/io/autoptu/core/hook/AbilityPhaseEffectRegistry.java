package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Ordered server-authoritative registry for phase-scoped ability behavior. */
public final class AbilityPhaseEffectRegistry {
    private final List<Registration> registrations;

    private AbilityPhaseEffectRegistry(List<Registration> registrations) {
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
        requireCombatantPhaseContext(context);
        if (context.actorId().isBlank()) return LifecycleHookResult.empty();
        TurnPhase phase = Objects.requireNonNull(context.phase(), "phase");
        RuntimeCombatantState actor = context.state().requireCombatant(context.actorId());
        if (actor.abilities().isEmpty()) return LifecycleHookResult.empty();

        ArrayList<BattleEvent> events = new ArrayList<>();
        PendingStatusSkipRequest pending = null;
        for (Registration registration : registrations) {
            if (registration.phase() != phase) continue;
            if (!AbilityIdentityResolution.matchesRegistration(actor.abilities(), registration.abilityName())) continue;
            LifecycleHookResult result = registration.effect().apply(context, registration.abilityName());
            if (result == null) {
                throw new IllegalStateException("ability phase effect returned null: " + registration.id());
            }
            events.addAll(result.events());
            if (result.pendingStatusSkip() != null) pending = result.pendingStatusSkip();
        }
        return new LifecycleHookResult(events, pending);
    }

    private static void requireCombatantPhaseContext(LifecycleHookContext context) {
        if (context.point() == LifecycleHookPoint.PHASE_CHANGE) return;
        if (context.point() == LifecycleHookPoint.TURN_START && context.phase() == TurnPhase.START) return;
        throw new IllegalArgumentException("ability phase effects require PHASE_CHANGE or START TURN_START context");
    }

    public record Registration(
            String id,
            String abilityName,
            TurnPhase phase,
            int order,
            AbilityPhaseEffect effect
    ) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("ability phase effect id is required");
            if (abilityName == null || abilityName.isBlank()) throw new IllegalArgumentException("abilityName is required");
            id = id.strip();
            abilityName = abilityName.strip();
            phase = Objects.requireNonNull(phase, "phase");
            effect = Objects.requireNonNull(effect, "effect");
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();

        public Builder register(String id, String abilityName, TurnPhase phase, int order, AbilityPhaseEffect effect) {
            Registration registration = new Registration(id, abilityName, phase, order, effect);
            if (!ids.add(registration.id().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("duplicate ability phase effect id: " + registration.id());
            }
            registrations.add(registration);
            return this;
        }

        public AbilityPhaseEffectRegistry build() {
            return new AbilityPhaseEffectRegistry(registrations);
        }
    }
}
