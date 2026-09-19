package io.autoptu.core.runtime;

import io.autoptu.core.hook.ActionWindowInstructionExecutionContext;
import io.autoptu.core.hook.CommittedActionWindowInstruction;

import java.util.Objects;
import java.util.function.Function;

/**
 * Binds one committed reaction instruction to its frozen authoritative move execution.
 *
 * <p>The action-window layer has already committed trigger legality and resource payment.
 * This context revalidates the frozen participants before the supplied runtime executor can
 * consume RNG or mutate battle state. The executor therefore receives only the server-owned
 * {@link CommittedReactionMoveExecution}; adapters cannot substitute actor, target, or move.</p>
 */
public final class CommittedReactionInstructionExecutionContext implements ActionWindowInstructionExecutionContext {
    private final CommittedReactionMoveExecution execution;
    private final Function<CommittedReactionMoveExecution, AppliedActionResult> runtimeExecutor;

    public CommittedReactionInstructionExecutionContext(
            CommittedReactionMoveExecution execution,
            Function<CommittedReactionMoveExecution, AppliedActionResult> runtimeExecutor
    ) {
        this.execution = Objects.requireNonNull(execution, "execution");
        this.runtimeExecutor = Objects.requireNonNull(runtimeExecutor, "runtimeExecutor");
    }

    @Override
    public AppliedActionResult executeCommittedAction(CommittedActionWindowInstruction instruction) {
        execution.requireCommittedParticipants(Objects.requireNonNull(instruction, "instruction"));
        return Objects.requireNonNull(runtimeExecutor.apply(execution), "authoritative reaction result");
    }
}
