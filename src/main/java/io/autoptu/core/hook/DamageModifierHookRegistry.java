package io.autoptu.core.hook;

import io.autoptu.core.model.AttackModifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable ordered registry for authoritative damage-modifier hooks.
 *
 * Order is explicit and stable. Java's stable sort preserves registration order
 * when two hooks share the same order, which lets parity slices mirror Python
 * dispatch order without coupling ordering to source category names.
 */
public final class DamageModifierHookRegistry {
    private final List<Registration> registrations;

    private DamageModifierHookRegistry(List<Registration> registrations) {
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

    public List<AttackModifier> resolve(DamageModifierHookContext context) {
        Objects.requireNonNull(context, "context");
        ArrayList<AttackModifier> resolved = new ArrayList<>();
        for (Registration registration : registrations) {
            List<AttackModifier> modifiers = registration.hook().resolve(context);
            if (modifiers == null) {
                throw new IllegalStateException("damage hook returned null: " + registration.key());
            }
            for (AttackModifier modifier : modifiers) {
                if (modifier == null) {
                    throw new IllegalStateException("damage hook returned null modifier: " + registration.key());
                }
                resolved.add(modifier);
            }
        }
        return List.copyOf(resolved);
    }

    public record Registration(
            String id,
            HookSource source,
            int order,
            DamageModifierHook hook
    ) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("hook id is required");
            id = id.strip();
            source = Objects.requireNonNull(source, "source");
            hook = Objects.requireNonNull(hook, "hook");
        }

        public String key() {
            return source.name().toLowerCase() + ":" + id;
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();

        public Builder register(String id, HookSource source, int order, DamageModifierHook hook) {
            Registration registration = new Registration(id, source, order, hook);
            if (!keys.add(registration.key())) {
                throw new IllegalArgumentException("duplicate damage hook registration: " + registration.key());
            }
            registrations.add(registration);
            return this;
        }

        public DamageModifierHookRegistry build() {
            return new DamageModifierHookRegistry(registrations);
        }
    }
}
