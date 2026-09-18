package io.autoptu.core.runtime;

import io.autoptu.core.hook.CommittedActionWindowInstruction;

/**
 * Resolves a committed reaction into canonical server-owned move metadata and target identity.
 * Implementations read battle/content state; adapters must not supply the resulting move choice.
 */
@FunctionalInterface
public interface CommittedReactionMoveResolver {
    CommittedReactionMoveBinding resolve(CommittedActionWindowInstruction instruction);
}
