package io.autoptu.core.runtime;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.rules.ActionBudget;

import java.util.Locale;

/**
 * Applies the server-owned resource mutations caused by one successful interception.
 *
 * <p>This boundary intentionally runs after candidate selection and a successful intercept check.
 * It does not choose an interceptor, roll RNG, move combatants, or resolve damage. Minecraft and
 * Cobblemon therefore never decide which PTU resources are spent.</p>
 */
public final class RuntimeInterceptResourceApplication {
    private RuntimeInterceptResourceApplication() {}

    public enum SourceKind {
        PREPARED,
        SENTINEL_STANCE,
        WEAPONIZE
    }

    public record Result(
            SourceKind sourceKind,
            boolean interceptReadyConsumed,
            boolean coachingConsumed,
            boolean baseShiftConsumed,
            boolean extraShiftConsumed,
            int damageReduction,
            String damageReductionSource
    ) {
        public Result {
            if (sourceKind == null) throw new IllegalArgumentException("sourceKind is required");
            if (damageReduction < 0) throw new IllegalArgumentException("damageReduction cannot be negative");
            damageReductionSource = damageReductionSource == null ? "" : damageReductionSource.strip();
        }
    }

    /**
     * Classifies the source identity emitted by {@link InterceptCandidateDiscoveryResolution}.
     * Python uses canonical source strings for Weaponize and Sentinel Stance; every other source
     * comes from an intercept_ready entry and therefore consumes that prepared token on success.
     */
    public static SourceKind classify(InterceptCandidateDiscoveryResolution.Candidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        String source = candidate.source() == null ? "" : candidate.source().strip().toLowerCase(Locale.ROOT);
        if (source.equals("weaponize")) return SourceKind.WEAPONIZE;
        if (source.equals("sentinel stance")) return SourceKind.SENTINEL_STANCE;
        return SourceKind.PREPARED;
    }

    /** Apply exactly the resource mutations for a successful interception. */
    public static Result apply(
            BattleRuntimeState state,
            InterceptCandidateDiscoveryResolution.Candidate candidate
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (candidate == null) throw new IllegalArgumentException("candidate is required");

        RuntimeCombatantState interceptor = state.requireCombatant(candidate.combatantId());
        TemporaryEffectStore effects = interceptor.temporaryEffects();
        ActionBudget actions = interceptor.actionBudget();
        SourceKind sourceKind = classify(candidate);

        // Validate the source-specific resource before any mutation so stale callers fail atomically.
        if (sourceKind == SourceKind.PREPARED && !effects.has("intercept_ready")) {
            throw new IllegalStateException("prepared interceptor has no intercept_ready resource: " + candidate.combatantId());
        }
        if (sourceKind == SourceKind.SENTINEL_STANCE
                && !actions.hasActionAvailable(ActionType.SHIFT)
                && actions.extraCount(ActionType.SHIFT) <= 0) {
            throw new IllegalStateException("Sentinel Stance interceptor has no SHIFT resource: " + candidate.combatantId());
        }

        boolean interceptReadyConsumed = false;
        boolean baseShiftConsumed = false;
        boolean extraShiftConsumed = false;
        int damageReduction = 0;
        String damageReductionSource = "";

        if (sourceKind == SourceKind.PREPARED) {
            // Python remove_temporary_effect(name) removes only the first family occurrence.
            interceptReadyConsumed = effects.removeFirst("intercept_ready");
        } else if (sourceKind == SourceKind.SENTINEL_STANCE) {
            boolean baseShiftAvailable = actions.hasActionAvailable(ActionType.SHIFT);
            int extraBefore = actions.extraCount(ActionType.SHIFT);
            boolean consumed = actions.consume(ActionType.SHIFT, "Sentinel Stance intercept");
            if (!consumed) {
                throw new IllegalStateException("failed to consume validated Sentinel Stance SHIFT resource");
            }
            baseShiftConsumed = baseShiftAvailable;
            extraShiftConsumed = !baseShiftAvailable && actions.extraCount(ActionType.SHIFT) == extraBefore - 1;
            damageReduction = 5;
            damageReductionSource = "Sentinel Stance";
            // Sentinel Stance itself is intentionally retained; Python does not remove it here.
        }

        // Coaching is a one-shot successful-intercept modifier independent of the source family.
        boolean coachingConsumed = effects.removeFirst("coaching_intercept");

        return new Result(
                sourceKind,
                interceptReadyConsumed,
                coachingConsumed,
                baseShiftConsumed,
                extraShiftConsumed,
                damageReduction,
                damageReductionSource
        );
    }
}
