package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Ordered fail-closed registry for combat-stage blockers that run before mutation. */
public final class CombatStagePreventionHookRegistry {
    private final List<Registration> registrations;

    private CombatStagePreventionHookRegistry(List<Registration> registrations) {
        ArrayList<Registration> ordered = new ArrayList<>(registrations);
        ordered.sort(Comparator.comparingInt(Registration::order));
        this.registrations = List.copyOf(ordered);
    }

    public static Builder builder() { return new Builder(); }
    public static CombatStagePreventionHookRegistry empty() { return builder().build(); }
    public List<Registration> registrations() { return registrations; }

    public CombatStagePreventionResult apply(CombatStagePreventionContext context) {
        Objects.requireNonNull(context, "context");
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (Registration registration : registrations) {
            CombatStagePreventionResult result = registration.hook().resolve(context);
            if (result == null) throw new IllegalStateException("combat-stage prevention hook returned null: " + registration.key());
            events.addAll(result.events());
            if (result.blocked()) return CombatStagePreventionResult.block(events);
        }
        return new CombatStagePreventionResult(false, events);
    }

    public record Registration(String id, HookSource source, int order, CombatStagePreventionHook hook) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("hook id is required");
            id = id.strip();
            source = Objects.requireNonNull(source, "source");
            hook = Objects.requireNonNull(hook, "hook");
        }
        public String key() { return source.name().toLowerCase(Locale.ROOT) + ":pre_apply:" + id; }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();
        public Builder register(String id, HookSource source, int order, CombatStagePreventionHook hook) {
            Registration registration = new Registration(id, source, order, hook);
            if (!keys.add(registration.key())) throw new IllegalArgumentException("duplicate combat-stage prevention hook: " + registration.key());
            registrations.add(registration);
            return this;
        }
        public CombatStagePreventionHookRegistry build() { return new CombatStagePreventionHookRegistry(registrations); }
    }
}
