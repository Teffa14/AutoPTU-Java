package io.autoptu.core.rules;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Generic defender-side prevention for move-caused forced movement. */
public final class ForcedMovementPreventionResolution {
    public enum SourceKind {
        ABILITY,
        STATUS,
        TEMPORARY_EFFECT,
        TRAINER_FEATURE
    }

    /**
     * Language-neutral prevention outcome. Runtime event adapters can use the source metadata
     * without re-deciding the PTU rule that caused forced movement to stop.
     */
    public record Prevention(SourceKind sourceKind, String sourceName) {
        public Prevention {
            sourceName = sourceName == null ? "" : sourceName.strip();
            if (sourceKind != null && sourceName.isBlank()) {
                throw new IllegalArgumentException("prevented outcome requires sourceName");
            }
        }

        public static Prevention none() {
            return new Prevention(null, "");
        }

        public boolean prevented() {
            return sourceKind != null;
        }
    }

    private record AbilityRule(String abilityName, Set<ForcedMovementInstruction.Kind> blockedKinds) {}
    private record StatusRule(String statusName, Set<ForcedMovementInstruction.Kind> blockedKinds) {}
    private record TemporaryRule(String effectName, Set<ForcedMovementInstruction.Kind> blockedKinds) {}
    private record ContentRule(
            String sourceFeature,
            Set<String> requiredTrainerFeatures,
            Set<String> requiredCapabilities,
            Set<ForcedMovementInstruction.Kind> blockedKinds
    ) {}

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
    private static final List<ContentRule> CONTENT_RULES = List.of(
            new ContentRule(
                    "Insectoid Utility",
                    Set.of("Insectoid Utility"),
                    Set.of("Wallclimber"),
                    Set.of(ForcedMovementInstruction.Kind.PUSH)
            )
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

    public static Prevention resolveByAbility(
            ForcedMovementInstruction instruction,
            List<String> defenderAbilities,
            boolean abilitiesSuppressed
    ) {
        if (instruction == null || abilitiesSuppressed) return Prevention.none();
        for (AbilityRule rule : ABILITY_RULES) {
            if (!rule.blockedKinds().contains(instruction.kind())) continue;
            if (AbilityIdentityResolution.matchesRegistration(defenderAbilities, rule.abilityName())) {
                return new Prevention(SourceKind.ABILITY, rule.abilityName());
            }
        }
        return Prevention.none();
    }

    public static boolean preventedByAbility(
            ForcedMovementInstruction instruction,
            List<String> defenderAbilities,
            boolean abilitiesSuppressed
    ) {
        return resolveByAbility(instruction, defenderAbilities, abilitiesSuppressed).prevented();
    }

    public static Prevention resolveByState(
            ForcedMovementInstruction instruction,
            Set<String> defenderStatuses,
            List<TemporaryEffect> temporaryEffects,
            int currentRound
    ) {
        if (instruction == null) return Prevention.none();
        Set<String> statuses = defenderStatuses == null ? Set.of() : defenderStatuses;
        for (StatusRule rule : STATUS_RULES) {
            if (!rule.blockedKinds().contains(instruction.kind())) continue;
            if (containsNormalized(statuses, rule.statusName())) {
                return new Prevention(SourceKind.STATUS, rule.statusName());
            }
        }
        List<TemporaryEffect> effects = temporaryEffects == null ? List.of() : temporaryEffects;
        for (TemporaryRule rule : TEMPORARY_RULES) {
            if (!rule.blockedKinds().contains(instruction.kind())) continue;
            for (TemporaryEffect effect : effects) {
                if (effect == null || !effect.activeAt(currentRound)) continue;
                if (effect.name().equals(normalize(rule.effectName()))) {
                    return new Prevention(SourceKind.TEMPORARY_EFFECT, effect.source());
                }
            }
        }
        return Prevention.none();
    }

    public static boolean preventedByState(
            ForcedMovementInstruction instruction,
            Set<String> defenderStatuses,
            List<TemporaryEffect> temporaryEffects,
            int currentRound
    ) {
        return resolveByState(instruction, defenderStatuses, temporaryEffects, currentRound).prevented();
    }

    /**
     * Declarative composite-content prevention family. A rule applies only when every required
     * Trainer Feature and capability is present, preserving Python guards without placing
     * content-specific branches in the runtime orchestrator.
     */
    public static Prevention resolveByContent(
            ForcedMovementInstruction instruction,
            List<String> defenderTrainerFeatures,
            List<String> defenderCapabilities
    ) {
        if (instruction == null) return Prevention.none();
        List<String> features = defenderTrainerFeatures == null ? List.of() : defenderTrainerFeatures;
        List<String> capabilities = defenderCapabilities == null ? List.of() : defenderCapabilities;
        for (ContentRule rule : CONTENT_RULES) {
            if (!rule.blockedKinds().contains(instruction.kind())) continue;
            if (!containsAllNormalized(features, rule.requiredTrainerFeatures())) continue;
            if (!containsAllNormalized(capabilities, rule.requiredCapabilities())) continue;
            return new Prevention(SourceKind.TRAINER_FEATURE, rule.sourceFeature());
        }
        return Prevention.none();
    }

    public static boolean preventedByContent(
            ForcedMovementInstruction instruction,
            List<String> defenderTrainerFeatures,
            List<String> defenderCapabilities
    ) {
        return resolveByContent(instruction, defenderTrainerFeatures, defenderCapabilities).prevented();
    }

    private static boolean containsAllNormalized(List<String> values, Set<String> required) {
        for (String expected : required) {
            boolean found = false;
            String wanted = normalize(expected);
            for (String value : values) {
                if (value != null && normalize(value).equals(wanted)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
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
