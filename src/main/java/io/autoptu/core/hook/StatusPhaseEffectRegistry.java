package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.TurnPhase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Ordered registry for phase-scoped status behavior.
 *
 * Registrations match canonical server-owned status names from BattleRuntimeState.
 * Multiple matching effects execute in explicit order. Their semantic events remain
 * ordered and the last pending skip request wins, matching Python StatusController's
 * single _pending_status_skip slot while it iterates ordered phase events.
 */
public final class StatusPhaseEffectRegistry {
    private final List<Registration> registrations;

    private StatusPhaseEffectRegistry(List<Registration> registrations) {
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
            throw new IllegalArgumentException("status phase effects require PHASE_CHANGE context");
        }
        if (context.actorId().isBlank()) {
            return LifecycleHookResult.empty();
        }
        TurnPhase phase = Objects.requireNonNull(context.phase(), "phase");
        Set<String> canonicalStatuses = context.state().statuses(context.actorId());
        if (canonicalStatuses.isEmpty()) {
            return LifecycleHookResult.empty();
        }

        ArrayList<BattleEvent> events = new ArrayList<>();
        PendingStatusSkipRequest pending = null;
        for (Registration registration : registrations) {
            if (registration.phase() != phase) continue;
            String matchedStatus = registration.firstMatch(canonicalStatuses);
            if (matchedStatus == null) continue;
            LifecycleHookResult result = registration.effect().apply(context, matchedStatus);
            if (result == null) {
                throw new IllegalStateException("status phase effect returned null: " + registration.id());
            }
            events.addAll(result.events());
            if (result.pendingStatusSkip() != null) {
                pending = result.pendingStatusSkip();
            }
        }
        return new LifecycleHookResult(events, pending);
    }

    public record Registration(
            String id,
            Set<String> statusNames,
            TurnPhase phase,
            int order,
            StatusPhaseEffect effect
    ) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("status phase effect id is required");
            id = id.strip();
            statusNames = normalizeStatuses(statusNames);
            if (statusNames.isEmpty()) throw new IllegalArgumentException("at least one status name is required");
            phase = Objects.requireNonNull(phase, "phase");
            effect = Objects.requireNonNull(effect, "effect");
        }

        String firstMatch(Set<String> statuses) {
            for (String status : statusNames) {
                if (statuses.contains(status)) return status;
            }
            return null;
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();

        public Builder register(
                String id,
                Collection<String> statusNames,
                TurnPhase phase,
                int order,
                StatusPhaseEffect effect
        ) {
            Registration registration = new Registration(
                    id,
                    statusNames == null ? Set.of() : new LinkedHashSet<>(statusNames),
                    phase,
                    order,
                    effect
            );
            if (!ids.add(registration.id().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("duplicate status phase effect id: " + registration.id());
            }
            registrations.add(registration);
            return this;
        }

        public StatusPhaseEffectRegistry build() {
            return new StatusPhaseEffectRegistry(registrations);
        }
    }

    private static Set<String> normalizeStatuses(Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String status : statuses) {
            if (status == null || status.isBlank()) continue;
            normalized.add(status.strip().toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(normalized);
    }
}
