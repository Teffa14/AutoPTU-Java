package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies geometry-approved interception candidates in pinned-Python attempt order.
 *
 * <p>Candidate ordering and geometry stay outside this boundary. For every candidate that reaches
 * this sequence, the authoritative runtime consumes one d20 and that candidate's interception
 * resources before checking success. A failed candidate therefore mutates battle state and the
 * sequence continues. The first successful candidate becomes the replacement attack target and
 * later candidates are untouched.</p>
 *
 * <p>Position commits and melee forced movement intentionally remain outside this slice so those
 * mutations can be composed against the frozen Python geometry/movement contract separately.</p>
 */
public final class RuntimeInterceptCandidateSequenceApplication {
    private RuntimeInterceptCandidateSequenceApplication() {}

    public record Attempt(
            InterceptCandidateDiscoveryResolution.Candidate candidate,
            RuntimeInterceptCheckApplication.Input checkInput
    ) {
        public Attempt {
            if (candidate == null) throw new IllegalArgumentException("candidate is required");
            if (checkInput == null) throw new IllegalArgumentException("checkInput is required");
        }
    }

    public record AttemptResult(
            String interceptorId,
            RuntimeInterceptCandidateAttemptApplication.Result attempt
    ) {
        public AttemptResult {
            if (interceptorId == null || interceptorId.isBlank()) {
                throw new IllegalArgumentException("interceptorId is required");
            }
            interceptorId = interceptorId.strip();
            if (attempt == null) throw new IllegalArgumentException("attempt is required");
        }
    }

    public record Result(
            String originalTargetId,
            String replacementTargetId,
            List<AttemptResult> attemptedCandidates
    ) {
        public Result {
            if (originalTargetId == null || originalTargetId.isBlank()) {
                throw new IllegalArgumentException("originalTargetId is required");
            }
            if (replacementTargetId == null || replacementTargetId.isBlank()) {
                throw new IllegalArgumentException("replacementTargetId is required");
            }
            originalTargetId = originalTargetId.strip();
            replacementTargetId = replacementTargetId.strip();
            attemptedCandidates = attemptedCandidates == null ? List.of() : List.copyOf(attemptedCandidates);
        }

        public boolean intercepted() {
            return !replacementTargetId.equals(originalTargetId);
        }
    }

    static Result apply(
            BattleRuntimeState state,
            String originalTargetId,
            List<Attempt> attempts
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (originalTargetId == null || originalTargetId.isBlank()) {
            throw new IllegalArgumentException("originalTargetId is required");
        }
        String targetId = originalTargetId.strip();
        state.requireCombatant(targetId);

        ArrayList<AttemptResult> applied = new ArrayList<>();
        for (Attempt requested : attempts == null ? List.<Attempt>of() : attempts) {
            if (requested == null) continue;
            InterceptCandidateDiscoveryResolution.Candidate candidate = requested.candidate();
            RuntimeInterceptCandidateAttemptApplication.Result attempt =
                    RuntimeInterceptCandidateAttemptApplication.apply(state, candidate, requested.checkInput());
            applied.add(new AttemptResult(candidate.combatantId(), attempt));
            if (attempt.check().success()) {
                return new Result(targetId, candidate.combatantId(), applied);
            }
        }
        return new Result(targetId, targetId, applied);
    }
}
