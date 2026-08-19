package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.AttackModifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    public DamageModifierHookResult resolve(DamageModifierHookContext context) {
        Objects.requireNonNull(context, "context");
        ArrayList<AttackModifier> modifiers = new ArrayList<>();
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (Registration registration : registrations) {
            DamageModifierHookResult result = registration.hook().resolve(context);
            if (result == null) {
                throw new IllegalStateException("damage hook returned null: " + registration.key());
            }
            modifiers.addAll(result.modifiers());
            events.addAll(result.events());
        }
        return DamageModifierHookResult.of(modifiers, events);
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
            return source.name().toLowerCase(Locale.ROOT) + ":" + id;
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
