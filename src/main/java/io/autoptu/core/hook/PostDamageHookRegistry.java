package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.DamageResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable ordered registry for effects that alter final damage after damage arithmetic. */
public final class PostDamageHookRegistry {
    private final List<Registration> registrations;

    private PostDamageHookRegistry(List<Registration> registrations) {
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

    public PostDamageHookResult resolve(PostDamageHookContext context) {
        Objects.requireNonNull(context, "context");
        DamageResult currentDamage = context.damage();
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (Registration registration : registrations) {
            PostDamageHookResult result = registration.hook().resolve(context.withDamage(currentDamage));
            if (result == null) {
                throw new IllegalStateException("post-damage hook returned null: " + registration.key());
            }
            currentDamage = result.damage();
            events.addAll(result.events());
        }
        return PostDamageHookResult.of(currentDamage, events);
    }

    public record Registration(String id, HookSource source, int order, PostDamageHook hook) {
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

        public Builder register(String id, HookSource source, int order, PostDamageHook hook) {
            Registration registration = new Registration(id, source, order, hook);
            if (!keys.add(registration.key())) {
                throw new IllegalArgumentException("duplicate post-damage hook registration: " + registration.key());
            }
            registrations.add(registration);
            return this;
        }

        public PostDamageHookRegistry build() {
            return new PostDamageHookRegistry(registrations);
        }
    }
}
