package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable ordered registry for combat-stage reaction hooks.
 *
 * Python AutoPTU dispatches these hooks by mutation phase and registration order.
 * Java keeps the same semantics explicit so abilities, items, Features, statuses,
 * terrain, and move specials can share one server-authoritative stage pipeline.
 */
public final class CombatStageHookRegistry {
    private final List<Registration> registrations;

    private CombatStageHookRegistry(List<Registration> registrations) {
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

    public CombatStageHookResult apply(CombatStageHookPhase phase, CombatStageHookContext context) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(context, "context");
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (Registration registration : registrations) {
            if (registration.phase() != phase) continue;
            CombatStageHookResult result = registration.hook().resolve(context);
            if (result == null) {
                throw new IllegalStateException("combat-stage hook returned null: " + registration.key());
            }
            events.addAll(result.events());
        }
        return CombatStageHookResult.events(events);
    }

    public record Registration(
            String id,
            HookSource source,
            CombatStageHookPhase phase,
            int order,
            CombatStageHook hook
    ) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("hook id is required");
            id = id.strip();
            source = Objects.requireNonNull(source, "source");
            phase = Objects.requireNonNull(phase, "phase");
            hook = Objects.requireNonNull(hook, "hook");
        }

        public String key() {
            return source.name().toLowerCase(Locale.ROOT) + ":" + phase.name().toLowerCase(Locale.ROOT) + ":" + id;
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();

        public Builder register(
                String id,
                HookSource source,
                CombatStageHookPhase phase,
                int order,
                CombatStageHook hook
        ) {
            Registration registration = new Registration(id, source, phase, order, hook);
            if (!keys.add(registration.key())) {
                throw new IllegalArgumentException("duplicate combat-stage hook registration: " + registration.key());
            }
            registrations.add(registration);
            return this;
        }

        public CombatStageHookRegistry build() {
            return new CombatStageHookRegistry(registrations);
        }
    }
}
