package io.autoptu.core.runtime;

import java.util.List;

/**
 * Typed server-owned rule metadata for the generic held-item START families.
 *
 * The profile carries canonical PTU content. Runtime source identity still comes from
 * the equipped HeldItemState so temporary effects use the same display-name source as
 * the Python oracle.
 */
public record HeldItemStartRuleProfile(
        List<HeldItemStartTemporaryEffectResolution.StatAmount> baseStatChanges,
        List<HeldItemStartTemporaryEffectResolution.StatScalar> baseStatScalars,
        Integer accuracyBonus,
        Integer accuracyBonusVsLowerAv,
        HeldItemStartTemporaryEffectResolution.TypeAmount typeAccuracyBonus,
        Integer statusEvasionBonus,
        Integer allEvasionBonus,
        Integer initiativeBonus,
        Double speedScalar
) {
    public HeldItemStartRuleProfile {
        baseStatChanges = baseStatChanges == null ? List.of() : List.copyOf(baseStatChanges);
        baseStatScalars = baseStatScalars == null ? List.of() : List.copyOf(baseStatScalars);
    }

    public HeldItemStartTemporaryEffectResolution.Input forHeldItem(HeldItemState item) {
        if (item == null) throw new IllegalArgumentException("held item is required");
        return new HeldItemStartTemporaryEffectResolution.Input(
                item.name(),
                baseStatChanges,
                baseStatScalars,
                accuracyBonus,
                accuracyBonusVsLowerAv,
                typeAccuracyBonus,
                statusEvasionBonus,
                allEvasionBonus,
                initiativeBonus,
                speedScalar
        );
    }
}
