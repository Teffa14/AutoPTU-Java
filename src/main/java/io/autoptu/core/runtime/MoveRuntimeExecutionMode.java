package io.autoptu.core.runtime;

/**
 * Explicit ownership policy for the authoritative move resolver.
 *
 * <p>Execution identity must not be inferred from whether ordinary action resources are spent.
 * Area targets, delayed hits, committed reactions, and pre-resolution target replacement can
 * share individual ownership decisions while requiring different declaration validation.</p>
 */
public enum MoveRuntimeExecutionMode {
    ORDINARY(true, true, false, DeclarationValidation.ORDINARY),
    PRE_RESOLUTION_RESOLVED(true, true, true, DeclarationValidation.ALREADY_VALIDATED),
    AREA_RESOLVED(false, true, true, DeclarationValidation.AREA_RESOLVED),
    DELAYED(false, false, true, DeclarationValidation.DELAYED),
    COMMITTED_REACTION(false, true, true, DeclarationValidation.ALREADY_VALIDATED);

    /**
     * Names the authoritative declaration contract independently from action-resource ownership.
     * BattleRuntime can dispatch validation from this value without reconstructing execution
     * identity from booleans that are shared by unrelated move paths.
     */
    public enum DeclarationValidation {
        ORDINARY,
        AREA_RESOLVED,
        DELAYED,
        ALREADY_VALIDATED
    }

    private final boolean spendOrdinaryMoveResources;
    private final boolean runPreDamageReactions;
    private final boolean declarationAlreadyValidated;
    private final DeclarationValidation declarationValidation;

    MoveRuntimeExecutionMode(
            boolean spendOrdinaryMoveResources,
            boolean runPreDamageReactions,
            boolean declarationAlreadyValidated,
            DeclarationValidation declarationValidation
    ) {
        this.spendOrdinaryMoveResources = spendOrdinaryMoveResources;
        this.runPreDamageReactions = runPreDamageReactions;
        this.declarationAlreadyValidated = declarationAlreadyValidated;
        this.declarationValidation = declarationValidation;
    }

    public boolean spendOrdinaryMoveResources() {
        return spendOrdinaryMoveResources;
    }

    public boolean runPreDamageReactions() {
        return runPreDamageReactions;
    }

    public boolean declarationAlreadyValidated() {
        return declarationAlreadyValidated;
    }

    public DeclarationValidation declarationValidation() {
        return declarationValidation;
    }

    /**
     * Compatibility bridge for historical BattleRuntime tuples while callers migrate to an
     * explicit execution identity. Ambiguous tuples are rejected instead of guessing whether a
     * no-spend reaction is area-resolved or a committed reaction.
     */
    static MoveRuntimeExecutionMode requireLegacyTuple(
            boolean spendOrdinaryMoveResources,
            boolean runPreDamageReactions,
            boolean declarationAlreadyValidated
    ) {
        if (spendOrdinaryMoveResources && runPreDamageReactions && !declaredChoiceAlreadyValidated) {
            return ORDINARY;
        }
        if (spendOrdinaryMoveResources && runPreDamageReactions && declarationAlreadyValidated) {
            return PRE_RESOLUTION_RESOLVED;
        }
        if (!spendOrdinaryMoveResources && !runPreDamageReactions && !declarationAlreadyValidated) {
            return DELAYED;
        }
        if (!spendOrdinaryMoveResources && runPreDamageReactions && !declarationAlreadyValidated) {
            return AREA_RESOLVED;
        }
        if (!spendOrdinaryMoveResources && runPreDamageReactions && declarationAlreadyValidated) {
            throw new IllegalArgumentException(
                    "legacy move tuple is ambiguous between AREA_RESOLVED and COMMITTED_REACTION; pass MoveRuntimeExecutionMode explicitly"
            );
        }
        throw new IllegalArgumentException(
                "unsupported legacy move execution tuple: spendOrdinaryMoveResources="
                        + spendOrdinaryMoveResources
                        + ", runPreDamageReactions=" + runPreDamageReactions
                        + ", declarationAlreadyValidated=" + declarationAlreadyValidated
        );
    }
}
