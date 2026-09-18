package io.autoptu.core.hook;

import io.autoptu.core.runtime.AppliedActionResult;

/**
 * Authoritative execution boundary for a committed action-window instruction.
 *
 * <p>The committed instruction already owns trigger legality and action-economy payment.
 * The executor supplied here owns the remaining PTU resolution, RNG consumption, state
 * mutation, and ordered semantic events. Action-window handlers must delegate through this
 * boundary instead of rebuilding battle rules or asking an adapter to execute them.</p>
 */
@FunctionalInterface
public interface ActionWindowInstructionExecutionContext {
    AppliedActionResult executeCommittedAction(CommittedActionWindowInstruction instruction);
}
