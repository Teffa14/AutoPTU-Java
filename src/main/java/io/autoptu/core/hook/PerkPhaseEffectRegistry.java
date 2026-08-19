package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.TurnPhase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Ordered registry for phase-scoped Trainer Feature/perk behavior. */
public final class PerkPhaseEffectRegistry {
    private final List<Registration> registrations;

    private PerkPhaseEffectRegistry(List<Registration> registrations) {
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

    /**
     * Resolves hooks against a server-owned Trainer Feature projection.
     * The feature collection is an explicit core contract for this bounded slice;
     * the next integration step will bind it to canonical BattleRuntimeState.
     */
    public LifecycleHookResult resolve(
            LifecycleHookContext context,
            Collection<String> trainerFeatures
    ) {
        Objects.requireNonNull(context, "context");
        if (context.point() != LifecycleHookPoint.PHASE_CHANGE) {
            throw new IllegalArgumentException("perk phase effects require PHASE_CHANGE context");
        }
        if (context.actorId().isBlank()) return LifecycleHookResult.empty();
        TurnPhase phase = Objects.requireNonNull(context.phase(), "phase");
        Set<String> normalizedFeatures = normalizeFeatures(trainerFeatures);

        ArrayList<BattleEvent> events = new ArrayList<>();
        PendingStatusSkipRequest pending = null;
        for (Registration registration : registrations) {
            if (registration.phase() != phase) continue;
            if (registration.perkName() != null
                    && !normalizedFeatures.contains(registration.perkName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            LifecycleHookResult result = registration.effect().apply(context, registration.perkName());
            if (result == null) {
                throw new IllegalStateException("perk phase effect returned null: " + registration.id());
            }
            events.addAll(result.events());
            if (result.pendingStatusSkip() != null) pending = result.pendingStatusSkip();
        }
        return new LifecycleHookResult(events, pending);
    }

    private static Set<String> normalizeFeatures(Collection<String> trainerFeatures) {
        if (trainerFeatures == null || trainerFeatures.isEmpty()) return Set.of();
        HashSet<String> normalized = new HashSet<>();
        for (String feature : trainerFeatures) {
            if (feature != null && !feature.isBlank()) {
                normalized.add(feature.strip().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(normalized);
    }

    public record Registration(
            String id,
            String perkName,
            TurnPhase phase,
            int order,
            PerkPhaseEffect effect
    ) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("perk phase effect id is required");
            id = id.strip();
            perkName = perkName == null || perkName.isBlank() ? null : perkName.strip();
            phase = Objects.requireNonNull(phase, "phase");
            effect = Objects.requireNonNull(effect, "effect");
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();

        public Builder register(String id, String perkName, TurnPhase phase, int order, PerkPhaseEffect effect) {
            Registration registration = new Registration(id, perkName, phase, order, effect);
            if (!ids.add(registration.id().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("duplicate perk phase effect id: " + registration.id());
            }
            registrations.add(registration);
            return this;
        }

        public PerkPhaseEffectRegistry build() {
            return new PerkPhaseEffectRegistry(registrations);
        }
    }
}
