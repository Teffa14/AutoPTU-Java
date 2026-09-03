package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.rules.ForcedMovementAbilityModifierResolution;
import io.autoptu.core.rules.ForcedMovementInstruction;
import io.autoptu.core.rules.ForcedMovementInstructionResolution;
import io.autoptu.core.rules.ForcedMovementPreventionResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RuntimePostHitForcedMovementApplication {
    record Resolution(
            Optional<RuntimeForcedMovementMoveApplication.Result> movement,
            ForcedMovementPreventionResolution.Prevention prevention
    ) {
        Resolution {
            movement = movement == null ? Optional.empty() : movement;
            prevention = prevention == null
                    ? ForcedMovementPreventionResolution.Prevention.none()
                    : prevention;
            if (movement.isPresent() && prevention.prevented()) {
                throw new IllegalArgumentException("forced movement cannot both move and be prevented");
            }
        }

        static Resolution none() {
            return new Resolution(Optional.empty(), ForcedMovementPreventionResolution.Prevention.none());
        }
    }

    record SemanticResolution(Resolution resolution, List<BattleEvent> events) {
        SemanticResolution {
            if (resolution == null) throw new IllegalArgumentException("resolution is required");
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    private RuntimePostHitForcedMovementApplication() {}

    static Optional<RuntimeForcedMovementMoveApplication.Result> apply(
            BattleRuntimeState state, MoveChoice choice, boolean hit
    ) {
        return resolve(state, choice, hit, BattleRuntimeDependencies.empty()).movement();
    }

    static Optional<RuntimeForcedMovementMoveApplication.Result> apply(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            BattleRuntimeDependencies dependencies
    ) {
        return resolve(state, choice, hit, dependencies).movement();
    }

    static SemanticResolution resolveWithSemanticEvents(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            BattleRuntimeDependencies dependencies
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (choice == null) throw new IllegalArgumentException("move choice is required");
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");

        CombatantRuleContent targetRuleContent = dependencies.combatantRuleContent().require(choice.targetId());
        Resolution resolution = resolve(state, choice, hit, targetRuleContent);
        ArrayList<BattleEvent> events = new ArrayList<>();
        if (resolution.movement().isPresent()) {
            MovementLandingConsequenceExecutor.ExecutionResult landing = RuntimeMovementLandingApplication.apply(
                    state,
                    choice.targetId(),
                    targetRuleContent,
                    ignored -> { }
            );
            events.addAll(landing.orderedEvents());
        }
        events.addAll(RuntimeForcedMovementPreventionSemanticEvents.resolve(
                choice,
                state.requireCombatant(choice.targetId()),
                targetRuleContent,
                resolution.prevention()
        ));
        return new SemanticResolution(resolution, events);
    }

    static Resolution resolve(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            BattleRuntimeDependencies dependencies
    ) {
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");
        if (choice == null) throw new IllegalArgumentException("move choice is required");
        return resolve(state, choice, hit, dependencies.combatantRuleContent().require(choice.targetId()));
    }

    static Optional<RuntimeForcedMovementMoveApplication.Result> apply(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            CombatantRuleContentRegistry ruleContentRegistry
    ) {
        if (ruleContentRegistry == null) throw new IllegalArgumentException("rule content registry is required");
        return resolve(state, choice, hit, new BattleRuntimeDependencies(ruleContentRegistry)).movement();
    }

    static Optional<RuntimeForcedMovementMoveApplication.Result> apply(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            CombatantRuleContent targetRuleContent
    ) {
        return resolve(state, choice, hit, targetRuleContent).movement();
    }

    static Resolution resolve(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            CombatantRuleContent targetRuleContent
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (choice == null) throw new IllegalArgumentException("move choice is required");
        if (targetRuleContent == null) throw new IllegalArgumentException("target rule content is required");
        if (!hit) return Resolution.none();

        MoveOption move = requireCanonicalMove(state, choice.actorId(), choice.moveId());
        RuntimeCombatantState source = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());

        Optional<ForcedMovementInstruction> instruction = ForcedMovementInstructionResolution.resolve(
                move.spec().keywords(), move.spec().effectsText()
        );
        String damageCategory = move.combatProfile() == null ? "" : move.combatProfile().damageCategory();
        instruction = ForcedMovementAbilityModifierResolution.resolve(
                instruction, damageCategory, source.abilities(), source.abilitiesSuppressed()
        );
        if (instruction.isEmpty()) return Resolution.none();
        ForcedMovementInstruction resolved = instruction.orElseThrow();

        ForcedMovementPreventionResolution.Prevention prevention =
                ForcedMovementPreventionResolution.resolveByContent(
                        resolved, targetRuleContent.trainerFeatures(), targetRuleContent.capabilities()
                );
        if (prevention.prevented()) return prevented(prevention);

        prevention = ForcedMovementPreventionResolution.resolveByTemporaryEffects(
                resolved, activePushImmunities(target, state.currentRound()), state.currentRound()
        );
        if (prevention.prevented()) return prevented(prevention);

        prevention = ForcedMovementPreventionResolution.resolveByAbility(
                resolved, target.abilities(), target.abilitiesSuppressed()
        );
        if (prevention.prevented()) return prevented(prevention);

        prevention = ForcedMovementPreventionResolution.resolveByStatus(
                resolved, state.statuses(choice.targetId())
        );
        if (prevention.prevented()) return prevented(prevention);

        ForcedDisplacementResolution.Result displacement = ForcedMovementApplication.apply(
                state, choice.actorId(), choice.targetId(), resolved
        );
        return new Resolution(
                Optional.of(new RuntimeForcedMovementMoveApplication.Result(move.moveId(), resolved, displacement)),
                ForcedMovementPreventionResolution.Prevention.none()
        );
    }

    private static Resolution prevented(ForcedMovementPreventionResolution.Prevention prevention) {
        return new Resolution(Optional.empty(), prevention);
    }

    private static List<ForcedMovementPreventionResolution.TemporaryEffect> activePushImmunities(
            RuntimeCombatantState target, int currentRound
    ) {
        ArrayList<ForcedMovementPreventionResolution.TemporaryEffect> active = new ArrayList<>();
        for (TemporaryEffectEntry entry : target.temporaryEffects().getAll("push_immunity")) {
            Integer expiresRound = integerPayload(entry, "expires_round");
            if (expiresRound != null && currentRound > expiresRound) {
                target.temporaryEffects().removeEntry(entry);
                continue;
            }
            Object sourceValue = entry.payload().get("source");
            String source = sourceValue == null ? "Push Immunity" : String.valueOf(sourceValue);
            active.add(new ForcedMovementPreventionResolution.TemporaryEffect(entry.name(), expiresRound, source));
        }
        return List.copyOf(active);
    }

    private static Integer integerPayload(TemporaryEffectEntry entry, String key) {
        Object value = entry.payload().get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static MoveOption requireCanonicalMove(
            BattleRuntimeState state, String sourceCombatantId, String moveId
    ) {
        if (!state.hasCanonicalMoves(sourceCombatantId)) {
            throw new IllegalStateException("combatant has no canonical moveset: " + sourceCombatantId);
        }
        for (MoveOption move : state.moveOptions(sourceCombatantId)) {
            if (move.moveId().equals(moveId)) return move;
        }
        throw new IllegalArgumentException(
                "move is not in authoritative combatant moveset: " + sourceCombatantId + "/" + moveId
        );
    }
}
