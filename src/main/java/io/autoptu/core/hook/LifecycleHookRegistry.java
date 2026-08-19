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
 * Immutable ordered registry for stateful battle-lifecycle hooks.
 *
 * Hooks execute only at their registered point. Order is explicit and stable,
 * preserving registration order when two hooks share the same numeric order.
 * Lifecycle hooks may mutate only the authoritative state exposed by the context;
 * adapters receive resulting semantic events but never execute the rules.
 */
public final class LifecycleHookRegistry {
    private final List<Registration> registrations;

    private LifecycleHookRegistry(List<Registration> registrations) {
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

    public LifecycleHookResult resolve(LifecycleHookPoint point, LifecycleHookContext context) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(context, "context");
        if (context.point() != point) {
            throw new IllegalArgumentException("context point does not match requested lifecycle point");
        }
        ArrayList<BattleEvent> events = new ArrayList<>();
        PendingStatusSkipRequest pendingStatusSkip = null;
        for (Registration registration : registrations) {
            if (registration.point() != point) continue;
            LifecycleHookResult result = registration.hook().apply(context);
            if (result == null) {
                throw new IllegalStateException("lifecycle hook returned null: " + registration.key());
            }
            events.addAll(result.events());
            if (result.pendingStatusSkip() != null) {
                if (pendingStatusSkip != null) {
                    throw new IllegalStateException(
                            "multiple pending status skips emitted at " + point.name().toLowerCase(Locale.ROOT)
                    );
                }
                pendingStatusSkip = result.pendingStatusSkip();
            }
        }
        return new LifecycleHookResult(events, pendingStatusSkip);
    }

    public record Registration(
            String id,
            HookSource source,
            LifecycleHookPoint point,
            int order,
            LifecycleHook hook
    ) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("hook id is required");
            id = id.strip();
            source = Objects.requireNonNull(source, "source");
            point = Objects.requireNonNull(point, "point");
            hook = Objects.requireNonNull(hook, "hook");
        }

        public String key() {
            return point.name().toLowerCase(Locale.ROOT) + ":"
                    + source.name().toLowerCase(Locale.ROOT) + ":" + id;
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();

        public Builder register(
                String id,
                HookSource source,
                LifecycleHookPoint point,
                int order,
                LifecycleHook hook
        ) {
            Registration registration = new Registration(id, source, point, order, hook);
            if (!keys.add(registration.key())) {
                throw new IllegalArgumentException("duplicate lifecycle hook registration: " + registration.key());
            }
            registrations.add(registration);
            return this;
        }

        public LifecycleHookRegistry build() {
            return new LifecycleHookRegistry(registrations);
        }
    }
}
