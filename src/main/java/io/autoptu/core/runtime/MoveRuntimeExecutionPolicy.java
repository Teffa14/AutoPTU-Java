package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;

import java.util.Objects;
import java.util.Set;

/**
 * Single authoritative projection of move execution identity into runtime ownership decisions.
 * BattleRuntime should consume this policy instead of carrying independent resource, reaction,
 * and declaration-validation booleans.
 */
final class MoveRuntimeExecutionPolicy {
    private final MoveRuntimeExecutionMode mode;

    private MoveRuntimeExecutionPolicy(MoveRuntimeExecutionMode mode) {
        this.mode = Objects.requireNonNull(mode, "execution mode is required");
    }

    static MoveRuntimeExecutionPolicy of(MoveRuntimeExecutionMode mode) {
        return new MoveRuntimeExecutionPolicy(mode);
    }

    MoveRuntimeExecutionMode mode() {
        return mode;
    }

    /**
     * Legacy compatibility projection for resolver call sites that have not yet migrated to split
     * action-spend and move-frequency ownership.
     */
    @Deprecated
    boolean spendOrdinaryMoveResources() {
        return mode.spendOrdinaryMoveResources();
    }

    boolean ownsActionSpend() {
        return mode.ownsActionSpend();
    }

    boolean ownsMoveFrequency() {
        return mode.ownsMoveFrequency();
    }

    boolean runPreDamageReactions() {
        return mode.runPreDamageReactions();
    }

    MoveRuntimeExecutionMode.DeclarationValidation declarationValidation() {
        return mode.declarationValidation();
    }

    boolean declarationAlreadyValidated() {
        return mode.declarationAlreadyValidated();
    }

    void requireValidDeclaration(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers
    ) {
        MoveRuntimeDeclarationValidator.requireValid(
                mode,
                state,
                choice,
                move,
                actorSize,
                targetSize,
                lineOfSightBlockers
        );
    }
}
