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
 * Ordered server-authoritative registry for TURN_END effects.
 *
 * <p>Python resolves several distinct actor-scoped and battle-global families between
 * temporary-effect refresh and turn_end event emission. This registry exposes that
 * boundary without requiring one lifecycle hook class per move, ability, item, status,
 * terrain rule, or Trainer Feature.</p>
 */
public final class TurnEndEffectRegistry {
    public enum Scope {
        ACTOR,
        ALL_COMBATANTS
    }

    @FunctionalInterface
    public interface Effect {
        LifecycleHookResult apply(LifecycleHookContext context, String combatantId);
    }

    public record Registration(String id, Scope scope, int order, Effect effect) {
        public Registration {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("turn-end effect id is required");
            id = id.strip();
            scope = Objects.requireNonNull(scope, "scope");
            effect = Objects.requireNonNull(effect, "effect");
        }
    }

    private final List<Registration> registrations;

    private TurnEndEffectRegistry(List<Registration> registrations) {
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

    public LifecycleHookResult resolve(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        if (context.point() != LifecycleHookPoint.TURN_END) {
            throw new IllegalArgumentException("turn-end effects require TURN_END context");
        }

        ArrayList<BattleEvent> events = new ArrayList<>();
        PendingStatusSkipRequest pending = null;
        for (Registration registration : registrations) {
            if (registration.scope() == Scope.ACTOR) {
                if (context.actorId() == null || context.actorId().isBlank()) {
                    throw new IllegalArgumentException("ACTOR turn-end effect requires actorId: " + registration.id());
                }
                LifecycleHookResult result = requireResult(registration, context, context.actorId());
                events.addAll(result.events());
                if (result.pendingStatusSkip() != null) pending = result.pendingStatusSkip();
                continue;
            }

            for (String combatantId : context.state().combatantIds()) {
                LifecycleHookResult result = requireResult(registration, context, combatantId);
                events.addAll(result.events());
                if (result.pendingStatusSkip() != null) pending = result.pendingStatusSkip();
            }
        }
        return new LifecycleHookResult(List.copyOf(events), pending);
    }

    private static LifecycleHookResult requireResult(
            Registration registration,
            LifecycleHookContext context,
            String combatantId
    ) {
        LifecycleHookResult result = registration.effect().apply(context, combatantId);
        if (result == null) {
            throw new IllegalStateException("turn-end effect returned null: " + registration.id());
        }
        return result;
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();

        public Builder register(String id, Scope scope, int order, Effect effect) {
            Registration registration = new Registration(id, scope, order, effect);
            if (!ids.add(registration.id().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("duplicate turn-end effect id: " + registration.id());
            }
            registrations.add(registration);
            return this;
        }

        public TurnEndEffectRegistry build() {
            return new TurnEndEffectRegistry(registrations);
        }
    }
}
