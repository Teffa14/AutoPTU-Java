package io.autoptu.core.hook;

import io.autoptu.core.runtime.AppliedActionResult;

import java.util.Objects;

/**
 * First concrete committed reaction handler.
 *
 * <p>Attack of Opportunity reaches this handler only after discovery, revalidation, trigger
 * claim, and resource payment. This class deliberately contains no targeting, accuracy,
 * damage, RNG, or state-mutation rules. Those remain in the authoritative battle executor.</p>
 */
public final class AttackOfOpportunityInstructionHandler implements ActionWindowInstructionHandler {
    public static final String ACTION_KEY = "attack-of-opportunity";

    private final ActionWindowInstructionExecutionContext executionContext;

    public AttackOfOpportunityInstructionHandler(ActionWindowInstructionExecutionContext executionContext) {
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
    }

    @Override
    public ActionWindowExecutionResult execute(CommittedActionWindowInstruction instruction) {
        Objects.requireNonNull(instruction, "instruction");
        if (!ACTION_KEY.equalsIgnoreCase(instruction.actionKey().strip())) {
            throw new IllegalArgumentException("Attack of Opportunity handler cannot execute action key: " + instruction.actionKey());
        }
        if (!instruction.hasTriggeringCombatant()) {
            throw new IllegalArgumentException("Attack of Opportunity requires a triggering combatant");
        }

        AppliedActionResult result = Objects.requireNonNull(
                executionContext.executeCommittedAction(instruction),
                "authoritative committed action result"
        );
        return new ActionWindowExecutionResult(result.events());
    }
}
