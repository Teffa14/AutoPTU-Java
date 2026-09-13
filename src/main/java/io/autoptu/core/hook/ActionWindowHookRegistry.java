package io.autoptu.core.hook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Ordered registry for Priority/Interrupt/Trigger candidate discovery. */
public final class ActionWindowHookRegistry {
    private final List<Registration> registrations;

    private ActionWindowHookRegistry(List<Registration> registrations) {
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

    public List<ActionWindowCandidate> candidates(ActionWindowContext context) {
        Objects.requireNonNull(context, "context");
        ArrayList<ActionWindowCandidate> result = new ArrayList<>();
        for (Registration registration : registrations) {
            if (!registration.windows().contains(context.window())) continue;
            List<ActionWindowCandidate> candidates = Objects.requireNonNull(
                    registration.hook().candidates(context),
                    "action-window hook returned null: " + registration.key()
            );
            for (ActionWindowCandidate candidate : candidates) {
                result.add(Objects.requireNonNull(candidate,
                        "action-window hook returned null candidate: " + registration.key()));
            }
        }
        return List.copyOf(result);
    }

    public record Registration(
            String id,
            HookSource source,
            Set<ActionWindow> windows,
            int order,
            ActionWindowHook hook
    ) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("hook id is required");
            id = id.strip();
            source = Objects.requireNonNull(source, "source");
            windows = Set.copyOf(Objects.requireNonNull(windows, "windows"));
            if (windows.isEmpty()) throw new IllegalArgumentException("at least one action window is required");
            hook = Objects.requireNonNull(hook, "hook");
        }

        public String key() {
            return source.name().toLowerCase(Locale.ROOT) + ":" + id;
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();

        public Builder register(String id, HookSource source, Set<ActionWindow> windows, int order, ActionWindowHook hook) {
            Registration registration = new Registration(id, source, windows, order, hook);
            if (!keys.add(registration.key())) {
                throw new IllegalArgumentException("duplicate action-window hook registration: " + registration.key());
            }
            registrations.add(registration);
            return this;
        }

        public ActionWindowHookRegistry build() {
            return new ActionWindowHookRegistry(registrations);
        }
    }
}
