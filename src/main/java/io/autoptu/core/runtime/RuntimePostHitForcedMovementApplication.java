package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.rules.ForcedMovementAbilityModifierResolution;
import io.autoptu.core.rules.ForcedMovementInstruction;
import io.autoptu.core.rules.ForcedMovementInstructionResolution;
import io.autoptu.core.rules.ForcedMovementPreventionResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runtime-only forced-movement seam for a move whose hit has already been resolved.
 *
 * <p>The ordinary move declaration is revalidated before accuracy/damage execution. By the time
 * Python consumes forced movement, damage outcome bookkeeping has already occurred, so re-running
 * ordinary action-economy validation would reject the same move after its action was spent. This
 * package-private seam therefore accepts only the already-authoritative runtime choice, resolves
 * move metadata again from the server-owned moveset, preserves the Python hit gate, composes
 * source modifiers and defender prevention, and delegates spatial mutation to the shared
 * forced-displacement engine.</p>
 */
final class RuntimePostHitForcedMovementApplication {
    /**
     * Language-neutral runtime outcome. Prevention provenance survives orchestration so later
     * semantic-event adapters never have to re-evaluate the PTU rule that stopped displacement.
     */
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
            return new Resolution(
                    Optional.empty(),
                    ForcedMovementPreventionResolution.Prevention.none()
            );
        }
    }

    private RuntimePostHitForcedMovementApplication() {}

    static Optional<RuntimeForcedMovementMoveApplication.Result> apply(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit
    ) {
        return resolve(state, choice, hit, BattleRuntimeDependencies.empty()).movement();
    }

    /**
     * Shared composition boundary for runtime rule dependencies. The dependency snapshot selects
     * canonical defender data; dedicated resolvers remain authoritative for PTU conclusions.
     */
    static Optional<RuntimeForcedMovementMoveApplication.Result> apply(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            BattleRuntimeDependencies dependencies
    ) {
        return resolve(state, choice, hit, dependencies).movement();
    }

    static Resolution resolve(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            BattleRuntimeDependencies dependencies
    ) {
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");
        if (choice == null) throw new IllegalArgumentException("move choice is required");
        return resolve(
                state,
                choice,
                hit,
                dependencies.combatantRuleContent().require(choice.targetId())
        );
    }

    /** Compatibility boundary for callers that already own the canonical content registry. */
    static Optional<RuntimeForcedMovementMoveApplication.Result> apply(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            CombatantRuleContentRegistry ruleContentRegistry
    ) {
        if (ruleContentRegistry == null) throw new IllegalArgumentException("rule content registry is required");
        return resolve(state, choice, hit, new BattleRuntimeDependencies(ruleContentRegistry)).movement();
    }

    /**
     * Content-aware core seam used when canonical rule content has already been materialized.
     * The content snapshot is data, not a pre-resolved PTU conclusion; the prevention resolver
     * remains the single authority for composite Feature/capability rules.
     */
    static Optional<RuntimeForcedMovementMoveApplication.Result> apply(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit,
            CombatantRuleContent targetRuleContent
    ) {
        return resolve(state, choice, hit, targetRuleContent).movement();
    }

    /**
     * Resolve one post-hit forced movement attempt while preserving the exact first blocker.
     * Python precedence is Feature/capability, temporary push immunity, Ability, then Ingrain.
     */
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
                move.spec().keywords(),
                move.spec().effectsText()
        );
        String damageCategory = move.combatProfile() == null
                ? ""
                : move.combatProfile().damageCategory();
        instruction = ForcedMovementAbilityModifierResolution.resolve(
                instruction,
                damageCategory,
                source.abilities(),
                source.abilitiesSuppressed()
        );
        if (instruction.isEmpty()) return Resolution.none();
        ForcedMovementInstruction resolved = instruction.orElseThrow();

        ForcedMovementPreventionResolution.Prevention prevention =
                ForcedMovementPreventionResolution.resolveByContent(
                        resolved,
                        targetRuleContent.trainerFeatures(),
                        targetRuleContent.capabilities()
                );
        if (prevention.prevented()) return prevented(prevention);

        prevention = ForcedMovementPreventionResolution.resolveByTemporaryEffects(
                resolved,
                activePushImmunities(target, state.currentRound()),
                state.currentRound()
        );
        if (prevention.prevented()) return prevented(prevention);

        prevention = ForcedMovementPreventionResolution.resolveByAbility(
                resolved,
                target.abilities(),
                target.abilitiesSuppressed()
        );
        if (prevention.prevented()) return prevented(prevention);

        prevention = ForcedMovementPreventionResolution.resolveByStatus(
                resolved,
                state.statuses(choice.targetId())
        );
        if (prevention.prevented()) return prevented(prevention);

        ForcedDisplacementResolution.Result displacement = ForcedMovementApplication.apply(
                state,
                choice.actorId(),
                choice.targetId(),
                resolved
        );
        return new Resolution(
                Optional.of(new RuntimeForcedMovementMoveApplication.Result(
                        move.moveId(), resolved, displacement
                )),
                ForcedMovementPreventionResolution.Prevention.none()
        );
    }

    private static Resolution prevented(ForcedMovementPreventionResolution.Prevention prevention) {
        return new Resolution(Optional.empty(), prevention);
    }

    private static List<ForcedMovementPreventionResolution.TemporaryEffect> activePushImmunities(
            RuntimeCombatantState target,
            int currentRound
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
            active.add(new ForcedMovementPreventionResolution.TemporaryEffect(
                    entry.name(), expiresRound, source
            ));
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
            BattleRuntimeState state,
            String sourceCombatantId,
            String moveId
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
