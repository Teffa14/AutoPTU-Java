package io.autoptu.core.hook;

/**
 * Authoritative execution hook for an already committed action-window instruction.
 *
 * <p>Implementations execute the action represented by the instruction and return the ordered
 * battle events produced by that execution. They must not re-spend action economy or re-decide
 * whether the trigger was legal: those decisions are frozen by {@link ActionWindowCommitRegistry}
 * before this hook is reached.</p>
 */
@FunctionalInterface
public interface ActionWindowInstructionHandler {
    ActionWindowExecutionResult execute(CommittedActionWindowInstruction instruction);
}
