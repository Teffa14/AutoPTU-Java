package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Language-neutral ordering contract for the ability work performed after Trainer Features
 * during Python PhaseController.start_round().
 *
 * This planner intentionally describes orchestration only. Ability effects remain owned by
 * server-side registries/executors and are not implemented here or in adapters.
 */
public final class RoundStartAbilityDispatchPlan {
    public static final String AIR_LOCK = "air_lock";
    public static final String ARENA_TRAP = "arena_trap";
    public static final String INTIMIDATE = "intimidate";
    public static final String IMPOSTOR = "impostor";

    private RoundStartAbilityDispatchPlan() {}

    public enum Scope {
        ABILITY_HOLDER,
        GLOBAL,
        ACTIVE_ACTOR
    }

    public record Combatant(String actorId, boolean active, boolean fainted) {
        public Combatant {
            if (actorId == null || actorId.isBlank()) {
                throw new IllegalArgumentException("actorId is required");
            }
            actorId = actorId.strip();
        }
    }

    public record Invocation(String family, Scope scope, String actorId) {
        public Invocation {
            if (family == null || family.isBlank()) {
                throw new IllegalArgumentException("family is required");
            }
            family = family.strip().toLowerCase(Locale.ROOT);
            if (scope == null) throw new IllegalArgumentException("scope is required");
            actorId = actorId == null ? "" : actorId.strip();
            if (scope == Scope.GLOBAL && !actorId.isEmpty()) {
                throw new IllegalArgumentException("global invocation cannot have actorId");
            }
            if (scope != Scope.GLOBAL && actorId.isEmpty()) {
                throw new IllegalArgumentException("actor-scoped invocation requires actorId");
            }
        }
    }

    public static List<Invocation> plan(
            String weather,
            List<String> airLockHolders,
            List<Combatant> combatants
    ) {
        List<String> holders = airLockHolders == null ? List.of() : airLockHolders;
        List<Combatant> orderedCombatants = combatants == null ? List.of() : combatants;
        ArrayList<Invocation> result = new ArrayList<>();

        if (!weatherIsUnsuppressedBaseline(weather)) {
            for (String holder : holders) {
                if (holder == null || holder.isBlank()) {
                    throw new IllegalArgumentException("Air Lock holder id is required");
                }
                result.add(new Invocation(AIR_LOCK, Scope.ABILITY_HOLDER, holder));
            }
        }

        result.add(new Invocation(ARENA_TRAP, Scope.GLOBAL, ""));

        for (Combatant combatant : orderedCombatants) {
            if (combatant == null) throw new IllegalArgumentException("combatant is required");
            if (!combatant.active() || combatant.fainted()) continue;
            result.add(new Invocation(INTIMIDATE, Scope.ACTIVE_ACTOR, combatant.actorId()));
            result.add(new Invocation(IMPOSTOR, Scope.ACTIVE_ACTOR, combatant.actorId()));
        }
        return List.copyOf(result);
    }

    private static boolean weatherIsUnsuppressedBaseline(String weather) {
        String normalized = weather == null ? "" : weather.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("clear") || normalized.equals("normal");
    }
}
