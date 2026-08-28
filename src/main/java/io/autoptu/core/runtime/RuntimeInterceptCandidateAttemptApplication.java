package io.autoptu.core.runtime;

/**
 * Applies the per-candidate interception attempt boundary in pinned-Python order.
 *
 * <p>Python consumes the d20 first, then mutates the selected candidate's interception resources
 * before it branches on the resulting success flag. This boundary keeps that ordering explicit so
 * a failed check still leaves the same authoritative resource/RNG state as the oracle. Position
 * commits and melee forced movement remain outside this slice.</p>
 */
public final class RuntimeInterceptCandidateAttemptApplication {
    private RuntimeInterceptCandidateAttemptApplication() {}

    public record Result(
            InterceptCheckResolution.Result check,
            RuntimeInterceptResourceApplication.Result resources
    ) {
        public Result {
            if (check == null) throw new IllegalArgumentException("check is required");
            if (resources == null) throw new IllegalArgumentException("resources are required");
        }
    }

    static Result apply(
            BattleRuntimeState state,
            InterceptCandidateDiscoveryResolution.Candidate candidate,
            RuntimeInterceptCheckApplication.Input checkInput
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (candidate == null) throw new IllegalArgumentException("candidate is required");
        if (checkInput == null) throw new IllegalArgumentException("intercept check input is required");

        InterceptCheckResolution.Result check = RuntimeInterceptCheckApplication.resolve(state, checkInput);
        RuntimeInterceptResourceApplication.Result resources = RuntimeInterceptResourceApplication.apply(state, candidate);
        return new Result(check, resources);
    }
}
