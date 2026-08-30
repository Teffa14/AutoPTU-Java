package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.Collection;
import java.util.List;

/**
 * Composes the authoritative interception attempt sequence with server-owned spatial resolution.
 *
 * <p>Callers provide candidate identity, canonical combatant rule content and the PTU attack-line
 * cells. The caller must preserve Python's candidate ordering; the runtime selects only the first
 * candidate, matching Python's {@code interceptors[0]} behavior. It then derives that candidate's
 * legal intercept position from authoritative Shift legality before materializing the check input.
 * If the selected candidate cannot reach an attack-line cell, interception ends before RNG or
 * resources are consumed; later candidates are not tried. Only a successful selected candidate
 * commits movement. Melee interceptions reuse {@link InterceptMovementApplication#applyMelee} so
 * Push 1, collisions and partial stops remain owned by the shared forced-movement engine.</p>
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
        if (requested.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("attempt entries are required");
        }
        if (requested.isEmpty()) {
            RuntimeInterceptCandidateSequenceApplication.Result sequence =
                    RuntimeInterceptCandidateSequenceApplication.apply(state, originalTargetId, List.of());
            return new Result(sequence, null, null);
        }

        Attempt selected = requested.get(0);
        GridCoord interceptPosition = RuntimeInterceptPositionResolver.resolve(
                state,
                selected.candidate().combatantId(),
                line
        );
        if (interceptPosition == null) {
            RuntimeInterceptCandidateSequenceApplication.Result sequence =
                    RuntimeInterceptCandidateSequenceApplication.apply(state, originalTargetId, List.of());
            return new Result(sequence, null, null);
        }

        RuntimeInterceptCheckApplication.Input checkInput = RuntimeInterceptCheckInputFactory.fromState(
                state,
                selected.candidate().combatantId(),
                selected.interceptorContent(),
                interceptPosition
        );
        RuntimeInterceptCandidateSequenceApplication.Attempt sequenceAttempt =
                new RuntimeInterceptCandidateSequenceApplication.Attempt(selected.candidate(), checkInput);
        RuntimeInterceptCandidateSequenceApplication.Result sequence =
                RuntimeInterceptCandidateSequenceApplication.apply(state, originalTargetId, List.of(sequenceAttempt));
        if (!sequence.intercepted()) {
            return new Result(sequence, null, null);
        }

        if (sequence.attemptedCandidates().size() != 1) {
            throw new IllegalStateException("Python-compatible intercept sequence must attempt only the selected candidate");
        }
        RuntimeInterceptCandidateSequenceApplication.AttemptResult winnerResult =
                sequence.attemptedCandidates().get(0);
        if (!selected.candidate().combatantId().equals(winnerResult.interceptorId())
                || !winnerResult.attempt().check().success()) {
            throw new IllegalStateException("intercept winner does not match selected spatial attempt");
        }

        if (melee) {
            InterceptMovementApplication.MeleeResult movement = InterceptMovementApplication.applyMelee(
                    state,
                    winnerResult.interceptorId(),
                    sequence.originalTargetId(),
                    interceptPosition,
                    winnerResult.attempt().check()
            );
            return new Result(sequence, movement.interceptMovement(), movement);
        }

        InterceptMovementApplication.Result movement = InterceptMovementApplication.apply(
                state,
                winnerResult.interceptorId(),
                interceptPosition,
                winnerResult.attempt().check()
        );
        return new Result(sequence, movement, null);
    }
}
