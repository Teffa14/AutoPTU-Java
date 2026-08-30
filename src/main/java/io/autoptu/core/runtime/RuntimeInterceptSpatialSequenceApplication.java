package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes the authoritative interception attempt sequence with the spatial mutations
 * that Python performs only for the first successful candidate.
 *
 * <p>RNG, PTU check conclusions and interception resources are owned by the runtime.
 * Callers provide candidate identity, canonical combatant rule content and the already-resolved
 * legal intercept position; this boundary materializes the check input itself before delegating
 * ordered attempt/resource resolution. Only after that sequence finds a winner does this boundary
 * commit the winner's intercept position. Melee interceptions reuse
 * {@link InterceptMovementApplication#applyMelee} so Push 1, collisions and partial stops remain
 * owned by the shared forced-movement engine.</p>
 */
public final class RuntimeInterceptSpatialSequenceApplication {
    private RuntimeInterceptSpatialSequenceApplication() {}

    public record Attempt(
            InterceptCandidateDiscoveryResolution.Candidate candidate,
            CombatantRuleContent interceptorContent,
            GridCoord interceptPosition
    ) {
        public Attempt {
            if (candidate == null) throw new IllegalArgumentException("candidate is required");
            if (interceptorContent == null) {
                throw new IllegalArgumentException("interceptor rule content is required");
            }
            if (interceptPosition == null) throw new IllegalArgumentException("interceptPosition is required");
        }

        RuntimeInterceptCandidateSequenceApplication.Attempt sequenceAttempt(BattleRuntimeState state) {
            RuntimeInterceptCheckApplication.Input checkInput = RuntimeInterceptCheckInputFactory.fromState(
                    state,
                    candidate.combatantId(),
                    interceptorContent,
                    interceptPosition
            );
            return new RuntimeInterceptCandidateSequenceApplication.Attempt(candidate, checkInput);
        }
    }

    public record Result(
            RuntimeInterceptCandidateSequenceApplication.Result sequence,
            InterceptMovementApplication.Result interceptMovement,
            InterceptMovementApplication.MeleeResult meleeMovement
    ) {
        public Result {
            if (sequence == null) throw new IllegalArgumentException("sequence is required");
            if (!sequence.intercepted() && (interceptMovement != null || meleeMovement != null)) {
                throw new IllegalArgumentException("failed sequence cannot contain spatial movement");
            }
        }

        public String replacementTargetId() {
            return sequence.replacementTargetId();
        }

        public boolean intercepted() {
            return sequence.intercepted();
        }
    }

    static Result apply(
            BattleRuntimeState state,
            String originalTargetId,
            boolean melee,
            List<Attempt> attempts
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (originalTargetId == null || originalTargetId.isBlank()) {
            throw new IllegalArgumentException("originalTargetId is required");
        }

        List<Attempt> requested = attempts == null ? List.of() : List.copyOf(attempts);
        ArrayList<RuntimeInterceptCandidateSequenceApplication.Attempt> sequenceAttempts = new ArrayList<>();
        for (Attempt attempt : requested) {
            if (attempt == null) throw new IllegalArgumentException("attempt entries are required");
            sequenceAttempts.add(attempt.sequenceAttempt(state));
        }

        RuntimeInterceptCandidateSequenceApplication.Result sequence =
                RuntimeInterceptCandidateSequenceApplication.apply(state, originalTargetId, sequenceAttempts);
        if (!sequence.intercepted()) {
            return new Result(sequence, null, null);
        }

        int winnerIndex = sequence.attemptedCandidates().size() - 1;
        if (winnerIndex < 0 || winnerIndex >= requested.size()) {
            throw new IllegalStateException("successful intercept sequence has no matching spatial attempt");
        }
        Attempt winner = requested.get(winnerIndex);
        RuntimeInterceptCandidateSequenceApplication.AttemptResult winnerResult =
                sequence.attemptedCandidates().get(winnerIndex);
        if (!winner.candidate().combatantId().equals(winnerResult.interceptorId())
                || !winnerResult.attempt().check().success()) {
            throw new IllegalStateException("intercept winner does not match ordered spatial attempt");
        }

        if (melee) {
            InterceptMovementApplication.MeleeResult movement = InterceptMovementApplication.applyMelee(
                    state,
                    winnerResult.interceptorId(),
                    sequence.originalTargetId(),
                    winner.interceptPosition(),
                    winnerResult.attempt().check()
            );
            return new Result(sequence, movement.interceptMovement(), movement);
        }

        InterceptMovementApplication.Result movement = InterceptMovementApplication.apply(
                state,
                winnerResult.interceptorId(),
                winner.interceptPosition(),
                winnerResult.attempt().check()
        );
        return new Result(sequence, movement, null);
    }
}
