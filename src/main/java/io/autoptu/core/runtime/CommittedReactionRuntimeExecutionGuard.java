package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;

import java.util.Objects;

/**
 * Server-side guard for the final handoff from a committed reaction to BattleRuntime.
 *
 * <p>The action-window layer has already frozen trigger legality and paid the reaction resource.
 * This guard only revalidates immutable identities against the current authoritative battle state.
 * It deliberately does not spend action economy, move frequency, or resolve any PTU rule.</p>
 */
public final class CommittedReactionRuntimeExecutionGuard {
    private CommittedReactionRuntimeExecutionGuard() {}

    public static Participants requireBoundParticipants(
            BattleRuntimeState state,
            CommittedReactionMoveExecution execution
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(execution, "execution");

        CommittedReactionMoveBinding binding = execution.binding();
        if (binding.choice().targetMode() != ChoiceTargetMode.COMBATANT) {
            throw new IllegalArgumentException("committed reaction runtime execution requires a combatant target");
        }

        RuntimeCombatantState reactor = state.requireCombatant(binding.choice().actorId());
        RuntimeCombatantState target = state.requireCombatant(binding.choice().targetId());
        if (!binding.move().moveId().equals(binding.choice().moveId())) {
            throw new IllegalArgumentException("committed reaction move identity changed before runtime execution");
        }
        return new Participants(reactor, target);
    }

    public record Participants(RuntimeCombatantState reactor, RuntimeCombatantState target) {
        public Participants {
            Objects.requireNonNull(reactor, "reactor");
            Objects.requireNonNull(target, "target");
        }
    }
}
