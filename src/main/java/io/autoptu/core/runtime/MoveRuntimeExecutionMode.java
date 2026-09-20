package io.autoptu.core.runtime;

/**
 * Explicit ownership policy for the authoritative move resolver.
 *
 * <p>Execution identity must not be inferred from whether ordinary action resources are spent.
 * Area targets, delayed hits, and committed reactions can all bypass ordinary resource spending
 * while requiring different declaration validation and reaction behavior.</p>
 */
public enum MoveRuntimeExecutionMode {
    ORDINARY(true, true, false, DeclarationValidation.ORDINARY),
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
}
