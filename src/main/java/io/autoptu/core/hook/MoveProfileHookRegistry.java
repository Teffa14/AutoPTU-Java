package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.MoveCombatProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable ordered registry for authoritative effective-move transformations. */
public final class MoveProfileHookRegistry {
    private final List<Registration> registrations;

    private MoveProfileHookRegistry(List<Registration> registrations) {
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

    public MoveProfileHookResult resolve(MoveProfileHookContext context) {
        Objects.requireNonNull(context, "context");
        MoveCombatProfile profile = context.profile();
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (Registration registration : registrations) {
            MoveProfileHookResult result = registration.hook().resolve(context.withProfile(profile));
            if (result == null) {
                throw new IllegalStateException("move profile hook returned null: " + registration.key());
            }
            profile = result.profile();
            events.addAll(result.events());
        }
        return MoveProfileHookResult.of(profile, events);
    }

    public record Registration(String id, HookSource source, int order, MoveProfileHook hook) {
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

        public Builder register(String id, HookSource source, int order, MoveProfileHook hook) {
            Registration registration = new Registration(id, source, order, hook);
            if (!keys.add(registration.key())) {
                throw new IllegalArgumentException("duplicate move profile hook registration: " + registration.key());
            }
            registrations.add(registration);
            return this;
        }

        public MoveProfileHookRegistry build() {
            return new MoveProfileHookRegistry(registrations);
        }
    }
}
