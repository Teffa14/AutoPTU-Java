package io.autoptu.core.hook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Ordered PRE-damage reaction registry.
 *
 * <p>Hooks continue running after an earlier hook cancels the hit, matching Python's
 * ability-hook registry, which mutates the shared result and still visits later hooks.</p>
 */
public final class PreDamageReactionHookRegistry {
    private final List<Registration> registrations;

    private PreDamageReactionHookRegistry(List<Registration> registrations) {
        ArrayList<Registration> ordered = new ArrayList<>(registrations);
        ordered.sort(Comparator.comparingInt(Registration::order));
        this.registrations = List.copyOf(ordered);
    }

    public static Builder builder() {
        return new Builder();
    }

    public PreDamageReactionResult resolve(PreDamageReactionContext context, PreDamageReactionResult initial) {
        Objects.requireNonNull(context, "context");
        PreDamageReactionResult current = Objects.requireNonNull(initial, "initial");
        for (Registration registration : registrations) {
            current = Objects.requireNonNull(
                    registration.hook().resolve(context, current),
                    "pre-damage reaction hook returned null: " + registration.key()
            );
        }
        return current;
    }

    public record Registration(String id, HookSource source, int order, PreDamageReactionHook hook) {
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

        public Builder register(String id, HookSource source, int order, PreDamageReactionHook hook) {
            Registration registration = new Registration(id, source, order, hook);
            if (!keys.add(registration.key())) {
                throw new IllegalArgumentException("duplicate pre-damage reaction hook registration: " + registration.key());
            }
            registrations.add(registration);
            return this;
        }

        public PreDamageReactionHookRegistry build() {
            return new PreDamageReactionHookRegistry(registrations);
        }
    }
}
