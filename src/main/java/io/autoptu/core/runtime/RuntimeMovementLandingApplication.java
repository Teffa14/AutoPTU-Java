package io.autoptu.core.runtime;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared server-authoritative boundary for consequences caused by an effective movement landing.
 *
 * <p>Movement producers supply only the combatant identity and canonical rule-content snapshot.
 * The core derives the post-movement position, tile hazards, affiliation and Naturewalk state,
 * resolves the generic landing registry, and applies the resulting mutations through the shared
 * status/hazard boundaries. Adapters never decide whether an entry effect triggers.</p>
 */
final class RuntimeMovementLandingApplication {
    private static final MovementLandingHookRegistry LANDING_HOOKS = MovementLandingHookRegistry.standard();

    private RuntimeMovementLandingApplication() {}

    static MovementLandingConsequenceExecutor.ExecutionResult apply(
            BattleRuntimeState state,
            String combatantId,
            CombatantRuleContent ruleContent,
            Consumer<MovementLandingConsequenceExecutor.SemanticEvent> semanticEventSink
    ) {
        return apply(
                state,
                combatantId,
                ruleContent,
                BattleRuntimeDependencies.empty(),
                semanticEventSink
        );
    }

    static MovementLandingConsequenceExecutor.ExecutionResult apply(
            BattleRuntimeState state,
            String combatantId,
            CombatantRuleContent ruleContent,
            BattleRuntimeDependencies dependencies,
            Consumer<MovementLandingConsequenceExecutor.SemanticEvent> semanticEventSink
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(ruleContent, "ruleContent");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(semanticEventSink, "semanticEventSink");
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }

        MovementLandingHookRegistry.LandingContext context =
                RuntimeMovementLandingContext.resolve(state, combatantId, ruleContent);
        return MovementLandingConsequenceExecutor.execute(
                state,
                dependencies.statusApplicationHooks(),
                LANDING_HOOKS.resolve(context),
                semanticEventSink
        );
    }
}
