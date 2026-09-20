package io.autoptu.core.runtime;

/**
 * Explicit ownership policy for the authoritative move resolver.
 *
 * <p>Execution identity must not be inferred from whether ordinary action resources are spent.
 * Area targets, delayed hits, and committed reactions can all bypass ordinary resource spending
 * while requiring different declaration validation and reaction behavior.</p>
 */
public enum MoveRuntimeExecutionMode {
    ORDINARY(true, true, false),
    AREA_RESOLVED(false, true, true),
    DELAYED(false, false, true),
    COMMITTED_REACTION(false, true, true);

    private final boolean spendOrdinaryMoveResources;
    private final boolean runPreDamageReactions;
    private final boolean declarationAlreadyValidated;

    MoveRuntimeExecutionMode(
            boolean spendOrdinaryMoveResources,
            boolean runPreDamageReactions,
            boolean declarationAlreadyValidated
    ) {
        this.spendOrdinaryMoveResources = spendOrdinaryMoveResources;
        this.runPreDamageReactions = runPreDamageReactions;
        this.declarationAlreadyValidated = declarationAlreadyValidated;
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
}
