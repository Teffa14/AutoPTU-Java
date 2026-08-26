package io.autoptu.core.runtime;

import java.util.List;

/**
 * Pure arithmetic contract for Python calculations._temporary_accuracy_bonus().
 *
 * <p>Rule-specific eligibility stays explicit so a later runtime input factory can derive every
 * input from BattleRuntimeState without moving PTU decisions into Minecraft/Cobblemon.</p>
 */
final class TemporaryAccuracyBonusResolution {
    private TemporaryAccuracyBonusResolution() {
    }

    static int resolve(Input input) {
        if (input == null) throw new IllegalArgumentException("input is required");

        int bonus = input.focusedTrainingBonus();
        if (input.compoundEyes()) bonus += 3;
        if (input.keenEye()) bonus += 1;
        if (input.attackerNoGuardErrata()) bonus += 3;
        if (input.defenderNoGuardErrata()) bonus += 3;

        if (input.hustleErrata()) {
            bonus -= 2;
        } else if (input.hustle() && "physical".equals(normalize(input.moveCategory()))) {
            bonus -= 2;
        }

        if (input.friskErrataWithinOne()) bonus += 2;
        if (input.boneWielderApplicable()) bonus += 1;
        if (input.shellCannonApplicable()) bonus += 2;

        String moveType = normalize(input.moveType());
        for (ScopedBonus entry : input.accuracyBonuses()) {
            if (matchesScope(entry.type(), moveType)) bonus += entry.amount();
        }
        if (input.defenderLowerAv()) {
            for (ScopedBonus entry : input.lowerAvBonuses()) {
                if (entry.amount() <= 0 || !matchesScope(entry.type(), moveType)) continue;
                bonus += entry.amount();
            }
        }

        return bonus + input.chroniclerBonus();
    }

    private static boolean matchesScope(String scope, String moveType) {
        String normalized = normalize(scope);
        return normalized.isEmpty() || normalized.equals(moveType);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    record ScopedBonus(String type, int amount) {
        ScopedBonus {
            type = type == null ? "" : type;
        }
    }

    record Input(
            int focusedTrainingBonus,
            boolean compoundEyes,
            boolean keenEye,
            boolean attackerNoGuardErrata,
            boolean defenderNoGuardErrata,
            boolean hustleErrata,
            boolean hustle,
            String moveCategory,
            boolean friskErrataWithinOne,
            boolean boneWielderApplicable,
            boolean shellCannonApplicable,
            String moveType,
            List<ScopedBonus> accuracyBonuses,
            List<ScopedBonus> lowerAvBonuses,
            boolean defenderLowerAv,
            int chroniclerBonus
    ) {
        Input {
            moveCategory = moveCategory == null ? "" : moveCategory;
            moveType = moveType == null ? "" : moveType;
            accuracyBonuses = accuracyBonuses == null ? List.of() : List.copyOf(accuracyBonuses);
            lowerAvBonuses = lowerAvBonuses == null ? List.of() : List.copyOf(lowerAvBonuses);
        }
    }
}
