package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Ordered authoritative interception point for status application and prevention. */
public final class StatusApplicationHookRegistry {
    private final List<Registration> registrations;

    private StatusApplicationHookRegistry(List<Registration> registrations) {
        ArrayList<Registration> ordered = new ArrayList<>(registrations);
        ordered.sort(Comparator.comparingInt(Registration::order));
        this.registrations = List.copyOf(ordered);
    }

    public static Builder builder() { return new Builder(); }

    public List<Registration> registrations() { return registrations; }

    public StatusApplicationHookResult resolve(StatusApplicationContext context) {
        Objects.requireNonNull(context, "context");
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (Registration registration : registrations) {
            StatusApplicationHookResult result = registration.hook().resolve(context);
            if (result == null) throw new IllegalStateException("status application hook returned null: " + registration.key());
            events.addAll(result.events());
            if (result.blocked()) return StatusApplicationHookResult.block(events);
        }
        return StatusApplicationHookResult.allow(events);
    }

    public record Registration(String id, HookSource source, int order, StatusApplicationHook hook) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("hook id is required");
            id = id.strip();
            source = Objects.requireNonNull(source, "source");
            hook = Objects.requireNonNull(hook, "hook");
        }
        public String key() { return source.name().toLowerCase(Locale.ROOT) + ":" + id; }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();
        public Builder register(String id, HookSource source, int order, StatusApplicationHook hook) {
            Registration registration = new Registration(id, source, order, hook);
            if (!keys.add(registration.key())) throw new IllegalArgumentException("duplicate status application hook registration: " + registration.key());
            registrations.add(registration);
            return this;
        }
        public StatusApplicationHookRegistry build() { return new StatusApplicationHookRegistry(registrations); }
    }
}
