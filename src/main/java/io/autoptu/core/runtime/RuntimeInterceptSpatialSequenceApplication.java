package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Composes the authoritative interception attempt sequence with server-owned spatial resolution.
 *
 * <p>Callers provide candidate identity, canonical combatant rule content and the PTU attack-line
 * cells. For each candidate the runtime derives a legal intercept position from authoritative
 * Shift legality before materializing the check input. Candidates that cannot reach any attack-line
 * cell are skipped before RNG or interception resources are consumed. Only the first successful
 * resolved candidate commits movement. Melee interceptions reuse
 * {@link InterceptMovementApplication#applyMelee} so Push 1, collisions and partial stops remain
 * owned by the shared forced-movement engine.</p>
 */
public final class RuntimeInterceptSpatialSequenceApplication {
    private RuntimeInterceptSpatialSequenceApplication() {}

    public record Attempt(
            InterceptCandidateDiscoveryResolution.Candidate candidate,
            CombatantRuleContent interceptorContent
    ) {
        public Attempt {
            if (candidate == null) throw new IllegalArgumentException("candidate is required");
            if (interceptorContent == null) {
                throw new IllegalArgumentException("interceptor rule content is required");
            }
        }
    }

    private record ResolvedAttempt(
            Attempt requested,
            GridCoord interceptPosition,
            RuntimeInterceptCandidateSequenceApplication.Attempt sequenceAttempt
    ) {}

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
            Collection<GridCoord> attackLine,
            List<Attempt> attempts
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (originalTargetId == null || originalTargetId.isBlank()) {
            throw new IllegalArgumentException("originalTargetId is required");
        }

        Collection<GridCoord> line = attackLine == null ? List.of() : List.copyOf(attackLine);
        List<Attempt> requested = attempts == null ? List.of() : List.copyOf(attempts);
        ArrayList<ResolvedAttempt> resolved = new ArrayList<>();
        ArrayList<RuntimeInterceptCandidateSequenceApplication.Attempt> sequenceAttempts = new ArrayList<>();
        for (Attempt attempt : requested) {
            if (attempt == null) throw new IllegalArgumentException("attempt entries are required");
            GridCoord interceptPosition = RuntimeInterceptPositionResolver.resolve(
                    state,
                    attempt.candidate().combatantId(),
                    line
            );
            if (interceptPosition == null) {
                continue;
            }
            RuntimeInterceptCheckApplication.Input checkInput = RuntimeInterceptCheckInputFactory.fromState(
                    state,
                    attempt.candidate().combatantId(),
                    attempt.interceptorContent(),
                    interceptPosition
            );
            RuntimeInterceptCandidateSequenceApplication.Attempt sequenceAttempt =
                    new RuntimeInterceptCandidateSequenceApplication.Attempt(attempt.candidate(), checkInput);
            resolved.add(new ResolvedAttempt(attempt, interceptPosition, sequenceAttempt));
            sequenceAttempts.add(sequenceAttempt);
        }

        RuntimeInterceptCandidateSequenceApplication.Result sequence =
                RuntimeInterceptCandidateSequenceApplication.apply(state, originalTargetId, sequenceAttempts);
        if (!sequence.intercepted()) {
            return new Result(sequence, null, null);
        }

        int winnerIndex = sequence.attemptedCandidates().size() - 1;
        if (winnerIndex < 0 || winnerIndex >= resolved.size()) {
            throw new IllegalStateException("successful intercept sequence has no matching spatial attempt");
        }
        ResolvedAttempt winner = resolved.get(winnerIndex);
        RuntimeInterceptCandidateSequenceApplication.AttemptResult winnerResult =
                sequence.attemptedCandidates().get(winnerIndex);
        if (!winner.requested().candidate().combatantId().equals(winnerResult.interceptorId())
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
