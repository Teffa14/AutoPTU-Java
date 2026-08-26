package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TemporaryAccuracyBonusOracleParityTest {
    @Test
    void matchesPinnedPythonTemporaryAccuracyBonusFixtures() throws IOException {
        String oracle = System.getenv("AUTOPTU_TEMPORARY_ACCURACY_BONUS_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "temporary Accuracy bonus fixture not configured");

        Map<String, Integer> expected = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Path.of(oracle));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            expected.put(fields[0], Integer.parseInt(fields[1]));
        }

        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            assertEquals(entry.getValue().intValue(), TemporaryAccuracyBonusResolution.resolve(inputFor(entry.getKey())), entry.getKey());
        }
    }

    private static TemporaryAccuracyBonusResolution.Input inputFor(String name) {
        InputBuilder builder = switch (name) {
            case "baseline" -> input();
            case "focused_default" -> input().withFocusedTrainingBonus(1);
            case "focused_helper" -> input().withFocusedTrainingBonus(3);
            case "compound_eyes" -> input().withCompoundEyes(true);
            case "keen_eye" -> input().withKeenEye(true);
            case "attacker_no_guard_errata" -> input().withAttackerNoGuardErrata(true);
            case "defender_no_guard_errata" -> input().withDefenderNoGuardErrata(true);
            case "hustle_errata_status" -> input().withHustleErrata(true).withMoveCategory("Status");
            case "hustle_base_physical" -> input().withHustle(true).withMoveCategory("Physical");
            case "hustle_base_special" -> input().withHustle(true).withMoveCategory("Special");
            case "hustle_errata_precedence" -> input().withHustleErrata(true).withHustle(true).withMoveCategory("Physical");
            case "frisk_near" -> input().withFriskErrataWithinOne(true);
            case "frisk_far" -> input();
            case "bone_wielder" -> input().withBoneWielderApplicable(true);
            case "shell_cannon" -> input().withShellCannonApplicable(true);
            case "typed_accuracy_bonus" -> input()
                    .withMoveType("Water")
                    .withAccuracyBonuses(List.of(
                            scoped("Water", 2), scoped("", 1), scoped("Fire", 5)));
            case "lower_av_bonus" -> input()
                    .withMoveType("Water")
                    .withDefenderLowerAv(true)
                    .withLowerAvBonuses(List.of(
                            scoped("Water", 2), scoped("Water", -1), scoped("Fire", 9)));
            case "lower_av_not_lower" -> input()
                    .withMoveType("Water")
                    .withDefenderLowerAv(false)
                    .withLowerAvBonuses(List.of(scoped("Water", 2)));
            case "chronicler" -> input().withChroniclerBonus(4);
            case "combined" -> input()
                    .withFocusedTrainingBonus(1)
                    .withCompoundEyes(true)
                    .withKeenEye(true)
                    .withAttackerNoGuardErrata(true)
                    .withDefenderNoGuardErrata(true)
                    .withHustleErrata(true)
                    .withHustle(true)
                    .withMoveCategory("Physical")
                    .withFriskErrataWithinOne(true)
                    .withBoneWielderApplicable(true)
                    .withMoveType("Water")
                    .withAccuracyBonuses(List.of(scoped("Water", 2), scoped("", 1)))
                    .withLowerAvBonuses(List.of(scoped("Water", 2)))
                    .withDefenderLowerAv(true)
                    .withChroniclerBonus(4);
            default -> throw new IllegalArgumentException("unknown fixture: " + name);
        };
        return builder.build();
    }

    private static TemporaryAccuracyBonusResolution.ScopedBonus scoped(String type, int amount) {
        return new TemporaryAccuracyBonusResolution.ScopedBonus(type, amount);
    }

    private static InputBuilder input() {
        return new InputBuilder();
    }

    private static final class InputBuilder {
        private int focusedTrainingBonus;
        private boolean compoundEyes;
        private boolean keenEye;
        private boolean attackerNoGuardErrata;
        private boolean defenderNoGuardErrata;
        private boolean hustleErrata;
        private boolean hustle;
        private String moveCategory = "Physical";
        private boolean friskErrataWithinOne;
        private boolean boneWielderApplicable;
        private boolean shellCannonApplicable;
        private String moveType = "Normal";
        private List<TemporaryAccuracyBonusResolution.ScopedBonus> accuracyBonuses = List.of();
        private List<TemporaryAccuracyBonusResolution.ScopedBonus> lowerAvBonuses = List.of();
        private boolean defenderLowerAv;
        private int chroniclerBonus;

        InputBuilder withFocusedTrainingBonus(int value) { focusedTrainingBonus = value; return this; }
        InputBuilder withCompoundEyes(boolean value) { compoundEyes = value; return this; }
        InputBuilder withKeenEye(boolean value) { keenEye = value; return this; }
        InputBuilder withAttackerNoGuardErrata(boolean value) { attackerNoGuardErrata = value; return this; }
        InputBuilder withDefenderNoGuardErrata(boolean value) { defenderNoGuardErrata = value; return this; }
        InputBuilder withHustleErrata(boolean value) { hustleErrata = value; return this; }
        InputBuilder withHustle(boolean value) { hustle = value; return this; }
        InputBuilder withMoveCategory(String value) { moveCategory = value; return this; }
        InputBuilder withFriskErrataWithinOne(boolean value) { friskErrataWithinOne = value; return this; }
        InputBuilder withBoneWielderApplicable(boolean value) { boneWielderApplicable = value; return this; }
        InputBuilder withShellCannonApplicable(boolean value) { shellCannonApplicable = value; return this; }
        InputBuilder withMoveType(String value) { moveType = value; return this; }
        InputBuilder withAccuracyBonuses(List<TemporaryAccuracyBonusResolution.ScopedBonus> value) { accuracyBonuses = value; return this; }
        InputBuilder withLowerAvBonuses(List<TemporaryAccuracyBonusResolution.ScopedBonus> value) { lowerAvBonuses = value; return this; }
        InputBuilder withDefenderLowerAv(boolean value) { defenderLowerAv = value; return this; }
        InputBuilder withChroniclerBonus(int value) { chroniclerBonus = value; return this; }

        TemporaryAccuracyBonusResolution.Input build() {
            return new TemporaryAccuracyBonusResolution.Input(
                    focusedTrainingBonus,
                    compoundEyes,
                    keenEye,
                    attackerNoGuardErrata,
                    defenderNoGuardErrata,
                    hustleErrata,
                    hustle,
                    moveCategory,
                    friskErrataWithinOne,
                    boneWielderApplicable,
                    shellCannonApplicable,
                    moveType,
                    accuracyBonuses,
                    lowerAvBonuses,
                    defenderLowerAv,
                    chroniclerBonus
            );
        }
    }
}
