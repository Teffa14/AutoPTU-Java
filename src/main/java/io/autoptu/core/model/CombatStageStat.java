package io.autoptu.core.model;

/**
 * Canonical PTU Combat Stage identities.
 *
 * CombatStat remains the five battle stats used by deterministic stat arithmetic.
 * Accuracy and Evasion are Combat Stages too, but are not CombatStat values.
 */
public enum CombatStageStat {
    ATK,
    DEF,
    SPATK,
    SPDEF,
    SPD,
    ACCURACY,
    EVASION;

    public static CombatStageStat fromCombatStat(CombatStat stat) {
        if (stat == null) {
            throw new IllegalArgumentException("combat stat is required");
        }
        return switch (stat) {
            case ATK -> ATK;
            case DEF -> DEF;
            case SPATK -> SPATK;
            case SPDEF -> SPDEF;
            case SPD -> SPD;
        };
    }

    public boolean isCoreCombatStat() {
        return switch (this) {
            case ATK, DEF, SPATK, SPDEF, SPD -> true;
            case ACCURACY, EVASION -> false;
        };
    }

    public CombatStat requireCombatStat() {
        return switch (this) {
            case ATK -> CombatStat.ATK;
            case DEF -> CombatStat.DEF;
            case SPATK -> CombatStat.SPATK;
            case SPDEF -> CombatStat.SPDEF;
            case SPD -> CombatStat.SPD;
            case ACCURACY, EVASION -> throw new IllegalStateException(this + " is not a core CombatStat");
        };
    }
}
