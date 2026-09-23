package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;

import java.util.Objects;
import java.util.Set;

/**
 * Typed execution context for the authoritative move resolver.
 *
 * <p>This keeps declaration validation, action spending, move-frequency ownership, and PRE-damage
 * reaction ownership attached to one execution identity. BattleRuntime can carry this value through
 * the ordinary single-target pipeline without reconstructing mode from independent booleans.</p>
 */
final class MoveRuntimeExecutionContext {
    private final MoveRuntimeExecutionPolicy policy;

    private MoveRuntimeExecutionContext(MoveRuntimeExecutionMode mode) {
        this.policy = MoveRuntimeExecutionPolicy.of(Objects.requireNonNull(mode, "execution mode is required"));
    }

    static MoveRuntimeExecutionContext of(MoveRuntimeExecutionMode mode) {
        return new MoveRuntimeExecutionContext(mode);
    }

    static MoveRuntimeExecutionContext ordinary() {
        return of(MoveRuntimeExecutionMode.ORDINARY);
    }

    static MoveRuntimeExecutionContext preResolutionResolved() {
        return of(MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED);
    }

    static MoveRuntimeExecutionContext areaResolved() {
        return of(MoveRuntimeExecutionMode.AREA_RESOLVED);
    }

    static MoveRuntimeExecutionContext delayed() {
        return of(MoveRuntimeExecutionMode.DELAYED);
    }

    static MoveRuntimeExecutionContext committedReaction() {
        return of(MoveRuntimeExecutionMode.COMMITTED_REACTION);
    }

    MoveRuntimeExecutionMode mode() {
        return policy.mode();
    }

    /**
     * Legacy compatibility projection for resolver call sites that have not yet migrated to split
     * action-spend and move-frequency ownership. New wiring must use the two explicit projections.
     */
    @Deprecated
    boolean spendOrdinaryMoveResources() {
        if (ownsActionSpend() != ownsMoveFrequency()) {
            throw new IllegalStateException("legacy resource projection cannot represent split ownership");
        }
        return ownsActionSpend();
    }

    boolean ownsActionSpend() {
        return policy.ownsActionSpend();
    }

    boolean ownsMoveFrequency() {
        return policy.ownsMoveFrequency();
    }

    boolean runPreDamageReactions() {
        return policy.runPreDamageReactions();
    }

    MoveRuntimeExecutionMode.DeclarationValidation declarationValidation() {
        return policy.declarationValidation();
    }

    boolean declarationAlreadyValidated() {
        return policy.declarationAlreadyValidated();
    }

    void requireValidDeclaration(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers
    ) {
        policy.requireValidDeclaration(state, choice, move, actorSize, targetSize, lineOfSightBlockers);
    }
}
