package io.autoptu.core.rules;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Generic defender-side prevention for move-caused forced movement. */
public final class ForcedMovementPreventionResolution {
    private record AbilityRule(String abilityName, Set<ForcedMovementInstruction.Kind> blockedKinds) {}
    private record StatusRule(String statusName, Set<ForcedMovementInstruction.Kind> blockedKinds) {}
    private record TemporaryRule(String effectName, Set<ForcedMovementInstruction.Kind> blockedKinds) {}

    /** Language-neutral temporary-effect projection used by runtime state adapters inside the core. */
    public record TemporaryEffect(String name, Integer expiresRound, String source) {
        public TemporaryEffect {
            name = normalize(name);
            source = source == null || source.isBlank() ? name : source.strip();
        }

        public boolean activeAt(int currentRound) {
            return expiresRound == null || currentRound <= expiresRound;
        }
    }

    private static final List<AbilityRule> ABILITY_RULES = List.of(
            new AbilityRule("Suction Cups", Set.of(ForcedMovementInstruction.Kind.PUSH)),
            new AbilityRule("Sumo Stance", Set.of(ForcedMovementInstruction.Kind.PUSH))
    );
    private static final List<StatusRule> STATUS_RULES = List.of(
            new StatusRule("Ingrain", Set.of(
                    ForcedMovementInstruction.Kind.PUSH,
                    ForcedMovementInstruction.Kind.PULL
            ))
    );
    private static final List<TemporaryRule> TEMPORARY_RULES = List.of(
            new TemporaryRule("push_immunity", Set.of(ForcedMovementInstruction.Kind.PUSH))
    );

    private ForcedMovementPreventionResolution() {}

    /** Compatibility boundary for ability-only callers. */
    public static boolean prevented(
            ForcedMovementInstruction instruction,
            List<String> defenderAbilities,
            boolean abilitiesSuppressed
    ) {
        return preventedByAbility(instruction, defenderAbilities, abilitiesSuppressed);
    }

    public static boolean preventedByAbility(
            ForcedMovementInstruction instruction,
            List<String> defenderAbilities,
            boolean abilitiesSuppressed
    ) {
        if (instruction == null || abilitiesSuppressed) return false;
        for (AbilityRule rule : ABILITY_RULES) {
            if (!rule.blockedKinds().contains(instruction.kind())) continue;
            if (AbilityIdentityResolution.matchesRegistration(defenderAbilities, rule.abilityName())) return true;
        }
        return false;
    }

    public static boolean preventedByState(
            ForcedMovementInstruction instruction,
            Set<String> defenderStatuses,
            List<TemporaryEffect> temporaryEffects,
            int currentRound
    ) {
        if (instruction == null) return false;
        Set<String> statuses = defenderStatuses == null ? Set.of() : defenderStatuses;
        for (StatusRule rule : STATUS_RULES) {
            if (!rule.blockedKinds().contains(instruction.kind())) continue;
            if (containsNormalized(statuses, rule.statusName())) return true;
        }
        List<TemporaryEffect> effects = temporaryEffects == null ? List.of() : temporaryEffects;
        for (TemporaryRule rule : TEMPORARY_RULES) {
            if (!rule.blockedKinds().contains(instruction.kind())) continue;
            for (TemporaryEffect effect : effects) {
                if (effect == null || !effect.activeAt(currentRound)) continue;
                if (effect.name().equals(normalize(rule.effectName()))) return true;
            }
        }
        return false;
    }

    private static boolean containsNormalized(Set<String> values, String expected) {
        String wanted = normalize(expected);
        for (String value : values) {
            if (value != null && normalize(value).equals(wanted)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
