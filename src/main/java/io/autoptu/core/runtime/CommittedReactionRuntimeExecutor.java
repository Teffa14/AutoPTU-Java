package io.autoptu.core.runtime;

import java.util.Objects;

/**
 * Authoritative runtime boundary for executing one frozen committed reaction move.
 *
 * <p>The action-window layer owns trigger commitment and reaction resource payment. Implementations
 * must route the supplied execution through the ordinary move-resolution pipeline without spending
 * ordinary move action economy or move frequency a second time. Minecraft/Cobblemon adapters must
 * not implement this contract.</p>
 */
@FunctionalInterface
public interface CommittedReactionRuntimeExecutor {
    AppliedActionResult execute(BattleRuntimeState state, CommittedReactionMoveExecution execution);

    static CommittedReactionRuntimeExecutor require(CommittedReactionRuntimeExecutor executor) {
        return Objects.requireNonNull(executor, "committed reaction runtime executor");
    }
}
