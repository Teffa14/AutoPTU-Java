package io.autoptu.core.hook;

import java.util.function.Supplier;

/**
 * Thread-confined synchronous execution scope for PRE-damage follow-up moves.
 *
 * <p>The pinned Python oracle re-enters move resolution synchronously from the reaction hook.
 * Keeping the runtime-owned executor in a scoped ThreadLocal lets existing reaction contexts
 * remain immutable while nested redirects reuse the same move/RNG pipeline. The previous scope
 * is always restored, so nested resolutions compose safely on the same battle thread.</p>
 */
public final class PreDamageFollowUpMoveExecutionScope {
    private static final ThreadLocal<PreDamageFollowUpMoveExecutor> CURRENT = new ThreadLocal<>();

    private PreDamageFollowUpMoveExecutionScope() {
    }

    public static <T> T runWith(PreDamageFollowUpMoveExecutor executor, Supplier<T> action) {
        if (executor == null) throw new IllegalArgumentException("executor is required");
        if (action == null) throw new IllegalArgumentException("action is required");
        PreDamageFollowUpMoveExecutor previous = CURRENT.get();
        CURRENT.set(executor);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    static PreDamageFollowUpMoveResult executeCurrent(PreDamageFollowUpMoveRequest request) {
        PreDamageFollowUpMoveExecutor executor = CURRENT.get();
        if (executor == null) {
            throw new UnsupportedOperationException("PRE-damage follow-up move execution is unavailable in this context");
        }
        return executor.execute(request);
    }
}
