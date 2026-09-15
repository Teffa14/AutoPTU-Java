package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;

import java.util.Objects;
import java.util.Optional;

/** Resolves a committed reaction window into an executable core instruction. */
public final class RuntimeReactionInstructionResolver {
    private final BattleRuntimeState battleState;

    public RuntimeReactionInstructionResolver(BattleRuntimeState battleState) {
        this.battleState = Objects.requireNonNull(battleState, "battleState");
    }

    /**
     * Resolves only successful commits and only from the canonical server-owned moveset.
     * No RNG, damage, targeting roll, action-bucket mutation or adapter behavior occurs here.
     */
    public Optional<RuntimeReactionInstruction> resolve(
            RuntimeReactionWindowResolver.Candidate candidate,
            RuntimeReactionWindowCommitter.CommitResult commitResult
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(commitResult, "commitResult");
        if (!commitResult.committed()) return Optional.empty();

        RuntimeReactionWindow window = candidate.window();
        if (!window.reactorId().equals(candidate.reactorId())) {
            throw new IllegalArgumentException("candidate reactor does not match reaction window");
        }
        if (window.round() != battleState.currentRound()) {
            throw new IllegalStateException("committed reaction window is no longer in the current round");
        }
        if (!battleState.hasCanonicalMoves(window.reactorId())) {
            throw new IllegalStateException("committed reactor has no canonical moveset");
        }

        MoveOption reactionMove = battleState.moveOptions(window.reactorId()).stream()
                .filter(move -> MoveReactionOwnershipSource.normalizeKey(move.moveId()).equals(window.reactionKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("committed reaction is absent from canonical moveset"));

        battleState.requireCombatant(window.triggeringActorId());
        return Optional.of(RuntimeReactionInstruction.from(window, reactionMove));
    }
}
