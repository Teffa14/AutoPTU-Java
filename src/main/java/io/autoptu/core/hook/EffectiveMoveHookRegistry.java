package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable ordered registry for pure pre-damage effective-move transformations. */
public final class EffectiveMoveHookRegistry {
    private final List<Registration> registrations;

    private EffectiveMoveHookRegistry(List<Registration> registrations) {
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

    public EffectiveMoveHookResult resolve(EffectiveMoveHookContext context) {
        Objects.requireNonNull(context, "context");
        EffectiveMoveHookContext current = context;
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (Registration registration : registrations) {
            EffectiveMoveHookResult result = registration.hook().resolve(current);
            if (result == null) {
                throw new IllegalStateException("effective move hook returned null: " + registration.key());
            }
            events.addAll(result.events());
            current = current.withEffectiveProfile(result.profile());
        }
        return new EffectiveMoveHookResult(current.effectiveProfile(), events);
    }

    public record Registration(String id, HookSource source, int order, EffectiveMoveHook hook) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("hook id is required");
            id = id.strip();
            source = Objects.requireNonNull(source, "source");
            hook = Objects.requireNonNull(hook, "hook");
        }

        public String key() {
            return source.name().toLowerCase(Locale.ROOT) + ":" + id;
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();

        public Builder register(String id, HookSource source, int order, EffectiveMoveHook hook) {
            Registration registration = new Registration(id, source, order, hook);
            if (!keys.add(registration.key())) {
                throw new IllegalArgumentException("duplicate effective move hook registration: " + registration.key());
            }
            registrations.add(registration);
            return this;
        }

        public EffectiveMoveHookRegistry build() {
            return new EffectiveMoveHookRegistry(registrations);
        }
    }
}
