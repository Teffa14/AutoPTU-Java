package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.rules.ForcedMovementPreventionResolution;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps already-resolved forced-movement prevention provenance to semantic battle events.
 *
 * <p>This class never decides whether forced movement is legal or prevented. The PTU conclusion
 * belongs to {@link ForcedMovementPreventionResolution}; this adapter only translates the winning
 * provenance into the observable event contract frozen from the pinned Python oracle.</p>
 */
final class RuntimeForcedMovementPreventionSemanticEvents {
    private record TrainerFeatureProfile(String description) {}
    private record AbilityProfile(String description) {}

    private static final Map<String, TrainerFeatureProfile> TRAINER_FEATURE_PROFILES = Map.of(
            "insectoid utility",
            new TrainerFeatureProfile("Insectoid Utility's Wallclimber upgrade prevents push effects.")
    );

    private static final Map<String, AbilityProfile> ABILITY_PROFILES = Map.of(
            "suction cups",
            new AbilityProfile("Suction Cups prevents forced movement."),
            "sumo stance",
            new AbilityProfile("Sumo Stance prevents push effects.")
    );

    private RuntimeForcedMovementPreventionSemanticEvents() {}

    static List<BattleEvent> resolve(
            MoveChoice choice,
            RuntimeCombatantState target,
            CombatantRuleContent targetRuleContent,
            ForcedMovementPreventionResolution.Prevention prevention
    ) {
        if (choice == null) throw new IllegalArgumentException("move choice is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        if (targetRuleContent == null) throw new IllegalArgumentException("target rule content is required");
        if (prevention == null || !prevention.prevented()) return List.of();

        return switch (prevention.sourceKind()) {
            case TRAINER_FEATURE -> trainerFeatureEvent(choice, target, targetRuleContent, prevention);
            case ABILITY -> abilityEvent(choice, target, prevention, false);
            case TEMPORARY_EFFECT -> abilityEvent(choice, target, prevention, true);
            case STATUS -> List.of();
        };
    }

    private static List<BattleEvent> trainerFeatureEvent(
            MoveChoice choice,
            RuntimeCombatantState target,
            CombatantRuleContent targetRuleContent,
            ForcedMovementPreventionResolution.Prevention prevention
    ) {
        TrainerFeatureProfile profile = TRAINER_FEATURE_PROFILES.get(normalize(prevention.sourceName()));
        if (profile == null) return List.of();

        return List.of(new TrainerFeatureEvent(
                target.combatantId(),
                prevention.sourceName(),
                "forced_movement_block",
                Map.of(
                        "target", choice.actorId(),
                        "trainer", targetRuleContent.controllerId(),
                        "description", profile.description(),
                        "targetHp", target.hp()
                )
        ));
    }

    private static List<BattleEvent> abilityEvent(
            MoveChoice choice,
            RuntimeCombatantState target,
            ForcedMovementPreventionResolution.Prevention prevention,
            boolean temporaryEffectSource
    ) {
        String sourceName = prevention.sourceName();
        String description;
        if (temporaryEffectSource) {
            description = sourceName + " prevents push effects.";
        } else {
            AbilityProfile profile = ABILITY_PROFILES.get(baseAbilityName(sourceName));
            if (profile == null) return List.of();
            description = profile.description();
        }

        return List.of(new AbilityEvent(
                target.combatantId(),
                sourceName,
                "forced_movement_block",
                Map.of(
                        "target", choice.actorId(),
                        "description", description,
                        "targetHp", target.hp()
                )
        ));
    }

    private static String baseAbilityName(String value) {
        String normalized = normalize(value);
        return normalized.endsWith(" [errata]")
                ? normalized.substring(0, normalized.length() - " [errata]".length())
                : normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
