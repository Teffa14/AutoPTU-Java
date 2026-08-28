package io.autoptu.core.hook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Ordered registry for authoritative target replacement before accuracy and damage. */
public final class PreResolutionTargetHookRegistry {
    private final List<Registration> registrations;

    private PreResolutionTargetHookRegistry(List<Registration> registrations) {
        ArrayList<Registration> ordered = new ArrayList<>(registrations);
        ordered.sort(Comparator.comparingInt(Registration::order));
        this.registrations = List.copyOf(ordered);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return registrations.isEmpty();
    }

    public PreResolutionTargetResult resolve(PreResolutionTargetContext context) {
        Objects.requireNonNull(context, "context");
        PreResolutionTargetResult current = PreResolutionTargetResult.initial(context.originalTargetId());
        for (Registration registration : registrations) {
            current = Objects.requireNonNull(
                    registration.hook().resolve(context, current),
                    "pre-resolution target hook returned null: " + registration.key()
            );
            context.state().requireCombatant(current.targetId());
        }
        return current;
    }

    public record Registration(String id, HookSource source, int order, PreResolutionTargetHook hook) {
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

        public Builder register(String id, HookSource source, int order, PreResolutionTargetHook hook) {
            Registration registration = new Registration(id, source, order, hook);
            if (!keys.add(registration.key())) {
                throw new IllegalArgumentException("duplicate pre-resolution target hook registration: " + registration.key());
            }
            registrations.add(registration);
            return this;
        }

        public PreResolutionTargetHookRegistry build() {
            return new PreResolutionTargetHookRegistry(registrations);
        }
    }
}
