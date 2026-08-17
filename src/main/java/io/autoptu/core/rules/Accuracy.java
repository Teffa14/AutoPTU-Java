package io.autoptu.core.rules;

import io.autoptu.core.model.AccuracyCheck;
import io.autoptu.core.model.AccuracyResult;

/**
 * Pure PTU d20 accuracy resolution extracted from calculations.attack_hits and
 * calculations.hit_probability after stateful bonuses/evasion have been resolved.
 */
public final class Accuracy {
    private Accuracy() {
    }

    public static AccuracyResult resolve(AccuracyCheck check) {
        if (check == null) {
            throw new IllegalArgumentException("check is required");
        }

        int critThreshold = check.critRange() == 0 ? 20 : check.critRange();
        int accuracyStage = Calculations.accuracyStageValue(check.accuracyStage());

        // Python attack_hits: AC=None is automatic unless Blur turns it into a
        // normal check with base AC 2 and half evasion.
        if (check.moveAc() == null && !check.blurApplies()) {
            return new AccuracyResult(
                    true,
                    check.roll() >= critThreshold,
                    check.roll(),
                    1
            );
        }

        int evasion;
        int baseAc;
        if (check.moveAc() == null) {
            baseAc = 2;
            evasion = (int) Math.floor(check.evasion() / 2.0);
        } else {
            baseAc = check.moveAc();
            evasion = check.meleeNoGuard() ? 0 : check.evasion();
        }

        int needed = Math.max(2, baseAc + evasion - accuracyStage);
        AccuracyResult first = evaluateRoll(check.roll(), needed, critThreshold);
        if (first.hit() || check.reroll() == null) {
            return first;
        }
        return evaluateRoll(check.reroll(), needed, critThreshold);
    }

    /** Mirror calculations.hit_probability for the same resolved inputs. */
    public static double hitProbability(AccuracyCheck check) {
        if (check == null) {
            throw new IllegalArgumentException("check is required");
        }
        if (check.moveAc() == null && !check.blurApplies()) {
            return 1.0;
        }

        int accuracyStage = Calculations.accuracyStageValue(check.accuracyStage());
        int evasion;
        int baseAc;
        if (check.moveAc() == null) {
            baseAc = 2;
            evasion = (int) Math.floor(check.evasion() / 2.0);
        } else {
            baseAc = check.moveAc();
            evasion = check.meleeNoGuard() ? 0 : check.evasion();
        }

        int needed = Math.max(2, baseAc + evasion - accuracyStage);
        if (needed <= 2) {
            return 0.95;
        }
        if (needed > 20) {
            return 1.0 / 20.0;
        }
        int successFaces = Math.max(0, 21 - needed);
        double probability = successFaces / 20.0;
        return Math.max(0.0, Math.min(0.95, probability));
    }

    private static AccuracyResult evaluateRoll(int roll, int needed, int critThreshold) {
        // Natural 1 is always a miss for normal checks. Natural 20 is always a hit.
        if (roll == 1) {
            return new AccuracyResult(false, false, roll, needed);
        }
        boolean hit = roll == 20 || roll >= needed;
        boolean crit = hit && roll >= critThreshold;
        return new AccuracyResult(hit, crit, roll, needed);
    }
}
