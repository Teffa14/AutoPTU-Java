package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Ordered Python-compatible registry for move-specific and global move-special hooks. */
public final class MoveSpecialHookRegistry {
    private final Map<MoveSpecialPhase, List<Registration>> globals;
    private final Map<MoveSpecialPhase, Map<String, List<Registration>>> specifics;

    private MoveSpecialHookRegistry(Builder builder) {
        LinkedHashMap<MoveSpecialPhase, List<Registration>> globalCopy = new LinkedHashMap<>();
        LinkedHashMap<MoveSpecialPhase, Map<String, List<Registration>>> specificCopy = new LinkedHashMap<>();
        for (MoveSpecialPhase phase : MoveSpecialPhase.values()) {
            ArrayList<Registration> globalRegistrations = builder.globals.get(phase);
            globalCopy.put(phase, globalRegistrations == null ? List.of() : List.copyOf(globalRegistrations));
            LinkedHashMap<String, List<Registration>> byMove = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<Registration>> registeredByMove = builder.specifics.get(phase);
            if (registeredByMove != null) {
                for (Map.Entry<String, ArrayList<Registration>> entry : registeredByMove.entrySet()) {
                    byMove.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
            }
            specificCopy.put(phase, Map.copyOf(byMove));
        }
        globals = Map.copyOf(globalCopy);
        specifics = Map.copyOf(specificCopy);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<BattleEvent> dispatch(MoveSpecialHookContext context) {
        Objects.requireNonNull(context, "context");
        if (context.shieldDustBlocksPostDamage()) return List.of();
        List<Registration> global = globals.getOrDefault(context.phase(), List.of());
        List<Registration> specific = specifics.getOrDefault(context.phase(), Map.of())
                .getOrDefault(context.moveName(), List.of());
        ArrayList<BattleEvent> events = new ArrayList<>();
        if (context.phase() == MoveSpecialPhase.POST_DAMAGE) {
            apply(specific, context, events);
            apply(global, context, events);
        } else {
            apply(global, context, events);
            apply(specific, context, events);
        }
        return List.copyOf(events);
    }

    private static void apply(List<Registration> registrations, MoveSpecialHookContext context, List<BattleEvent> events) {
        for (Registration registration : registrations) {
            List<BattleEvent> next = registration.hook().apply(context);
            if (next == null) throw new IllegalStateException("move-special hook returned null: " + registration.id());
            events.addAll(next);
        }
    }

    public record Registration(String id, MoveSpecialHook hook) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("hook id is required");
            id = id.strip();
            hook = Objects.requireNonNull(hook, "hook");
        }
    }

    public static final class Builder {
        private final LinkedHashMap<MoveSpecialPhase, ArrayList<Registration>> globals = new LinkedHashMap<>();
        private final LinkedHashMap<MoveSpecialPhase, LinkedHashMap<String, ArrayList<Registration>>> specifics = new LinkedHashMap<>();
        private final Set<String> ids = new HashSet<>();

        public Builder registerGlobal(String id, MoveSpecialPhase phase, MoveSpecialHook hook) {
            Registration registration = checked(id, hook);
            globals.computeIfAbsent(normalizePhase(phase), ignored -> new ArrayList<>()).add(registration);
            return this;
        }

        public Builder registerMove(String id, MoveSpecialPhase phase, List<String> moveNames, MoveSpecialHook hook) {
            Registration registration = checked(id, hook);
            MoveSpecialPhase resolvedPhase = normalizePhase(phase);
            LinkedHashMap<String, ArrayList<Registration>> byMove = specifics.computeIfAbsent(resolvedPhase, ignored -> new LinkedHashMap<>());
            for (String raw : moveNames == null ? List.<String>of() : moveNames) {
                String name = normalizeMoveName(raw);
                if (!name.isEmpty()) byMove.computeIfAbsent(name, ignored -> new ArrayList<>()).add(registration);
            }
            return this;
        }

        private Registration checked(String id, MoveSpecialHook hook) {
            Registration registration = new Registration(id, hook);
            if (!ids.add(registration.id())) throw new IllegalArgumentException("duplicate move-special hook registration: " + registration.id());
            return registration;
        }

        public MoveSpecialHookRegistry build() {
            return new MoveSpecialHookRegistry(this);
        }
    }

    private static MoveSpecialPhase normalizePhase(MoveSpecialPhase phase) {
        return phase == null ? MoveSpecialPhase.POST_DAMAGE : phase;
    }

    static String normalizeMoveName(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }
}