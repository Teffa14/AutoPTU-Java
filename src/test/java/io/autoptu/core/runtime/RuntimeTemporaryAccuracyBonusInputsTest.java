package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RuntimeTemporaryAccuracyBonusInputsTest {
    @Test
    void materializesPinnedPythonTemporaryAccuracyInputsFromAuthoritativeState() throws IOException {
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
            Scenario scenario = scenario(entry.getKey());
            TemporaryAccuracyBonusResolution.Input input = RuntimeTemporaryAccuracyBonusInputs.fromState(
                    scenario.state(), "attacker", "defender", scenario.move(), scenario.contextBonuses());
            assertEquals(
                    entry.getValue().intValue(),
                    TemporaryAccuracyBonusResolution.resolve(input),
                    entry.getKey()
            );
        }
    }

    private static Scenario scenario(String name) {
        ScenarioBuilder builder = new ScenarioBuilder();
        switch (name) {
            case "baseline" -> { }
            case "focused_default" -> builder.focusedTraining();
            case "focused_helper" -> builder.focusedTraining().contextBonuses(3, 0);
            case "compound_eyes" -> builder.attackerAbilities("Compound Eyes");
            case "keen_eye" -> builder.attackerAbilities("Keen Eye");
            case "attacker_no_guard_errata" -> builder.attackerAbilities("No Guard [Errata]");
            case "defender_no_guard_errata" -> builder.defenderAbilities("No Guard [Errata]");
            case "hustle_errata_status" -> builder.attackerAbilities("Hustle [Errata]").move("Tackle", "Status", "Normal");
            case "hustle_base_physical" -> builder.attackerAbilities("Hustle").move("Tackle", "Physical", "Normal");
            case "hustle_base_special" -> builder.attackerAbilities("Hustle").move("Tackle", "Special", "Normal");
            case "hustle_errata_precedence" -> builder.attackerAbilities("Hustle", "Hustle [Errata]").move("Tackle", "Physical", "Normal");
            case "frisk_near" -> builder.attackerAbilities("Frisk [SuMo Errata]").positions(new GridCoord(0, 0), new GridCoord(1, 1));
            case "frisk_far" -> builder.attackerAbilities("Frisk [SuMo Errata]").positions(new GridCoord(0, 0), new GridCoord(2, 0));
            case "bone_wielder" -> builder.attackerAbilities("Bone Wielder").heldItem("Thick Club").move("Bonemerang", "Physical", "Normal");
            case "shell_cannon" -> builder.attackerAbilities("Shell Cannon").shellCannonReady().move("Hydro Pump", "Physical", "Normal");
            case "typed_accuracy_bonus" -> builder
                    .accuracyBonus("Water", 2).accuracyBonus("", 1).accuracyBonus("Fire", 5)
                    .move("Tackle", "Physical", "Water");
            case "lower_av_bonus" -> builder
                    .evasion(4, 2)
                    .lowerAvBonus("Water", 2).lowerAvBonus("Water", -1).lowerAvBonus("Fire", 9)
                    .move("Tackle", "Physical", "Water");
            case "lower_av_not_lower" -> builder
                    .evasion(2, 4)
                    .lowerAvBonus("Water", 2)
                    .move("Tackle", "Physical", "Water");
            case "chronicler" -> builder.contextBonuses(null, 4);
            case "combined" -> builder
                    .attackerAbilities(
                            "Compound Eyes", "Keen Eye", "No Guard [Errata]", "Hustle [Errata]",
                            "Frisk [SuMo Errata]", "Bone Wielder", "Shell Cannon")
                    .defenderAbilities("No Guard [Errata]")
                    .focusedTraining().shellCannonReady().heldItem("Thick Club")
                    .accuracyBonus("Water", 2).accuracyBonus("", 1)
                    .lowerAvBonus("Water", 2)
                    .positions(new GridCoord(0, 0), new GridCoord(1, 0))
                    .evasion(4, 2)
                    .move("Bone Rush", "Physical", "Water")
                    .contextBonuses(1, 4);
            default -> throw new IllegalArgumentException("unknown fixture: " + name);
        }
        return builder.build();
    }

    private static final class ScenarioBuilder {
        private List<String> attackerAbilities = List.of();
        private List<String> defenderAbilities = List.of();
        private GridCoord attackerPosition = new GridCoord(0, 0);
        private GridCoord defenderPosition = new GridCoord(3, 0);
        private int attackerEvasion;
        private int defenderEvasion;
        private String moveName = "Tackle";
        private String moveCategory = "Physical";
        private String moveType = "Normal";
        private String heldItem;
        private boolean focusedTraining;
        private boolean shellCannonReady;
        private final List<TemporaryAccuracyBonusResolution.ScopedBonus> accuracyBonuses = new java.util.ArrayList<>();
        private final List<TemporaryAccuracyBonusResolution.ScopedBonus> lowerAvBonuses = new java.util.ArrayList<>();
        private RuntimeTemporaryAccuracyBonusInputs.ContextBonuses contextBonuses = RuntimeTemporaryAccuracyBonusInputs.ContextBonuses.NONE;

        ScenarioBuilder attackerAbilities(String... values) { attackerAbilities = List.of(values); return this; }
        ScenarioBuilder defenderAbilities(String... values) { defenderAbilities = List.of(values); return this; }
        ScenarioBuilder positions(GridCoord attacker, GridCoord defender) { attackerPosition = attacker; defenderPosition = defender; return this; }
        ScenarioBuilder evasion(int attacker, int defender) { attackerEvasion = attacker; defenderEvasion = defender; return this; }
        ScenarioBuilder move(String name, String category, String type) { moveName = name; moveCategory = category; moveType = type; return this; }
        ScenarioBuilder heldItem(String value) { heldItem = value; return this; }
        ScenarioBuilder focusedTraining() { focusedTraining = true; return this; }
        ScenarioBuilder shellCannonReady() { shellCannonReady = true; return this; }
        ScenarioBuilder accuracyBonus(String type, int amount) { accuracyBonuses.add(new TemporaryAccuracyBonusResolution.ScopedBonus(type, amount)); return this; }
        ScenarioBuilder lowerAvBonus(String type, int amount) { lowerAvBonuses.add(new TemporaryAccuracyBonusResolution.ScopedBonus(type, amount)); return this; }
        ScenarioBuilder contextBonuses(Integer focused, int chronicler) { contextBonuses = new RuntimeTemporaryAccuracyBonusInputs.ContextBonuses(focused, chronicler); return this; }

        Scenario build() {
            CombatantStatProfile attackerStats = stats();
            CombatantStatProfile defenderStats = stats();
            RuntimeCombatantState attacker = combatant("attacker", attackerPosition, attackerStats, attackerEvasion, attackerAbilities);
            RuntimeCombatantState defender = combatant("defender", defenderPosition, defenderStats, defenderEvasion, defenderAbilities);

            if (focusedTraining) attacker.temporaryEffects().add("focused_training");
            if (shellCannonReady) attacker.temporaryEffects().add("shell_cannon_ready");
            for (TemporaryAccuracyBonusResolution.ScopedBonus bonus : accuracyBonuses) {
                attacker.temporaryEffects().add("accuracy_bonus", Map.of("type", bonus.type(), "amount", bonus.amount()));
            }
            for (TemporaryAccuracyBonusResolution.ScopedBonus bonus : lowerAvBonuses) {
                attacker.temporaryEffects().add("accuracy_bonus_vs_lower_av", Map.of("type", bonus.type(), "amount", bonus.amount()));
            }

            Map<String, List<HeldItemState>> heldItems = heldItem == null
                    ? Map.of()
                    : Map.of("attacker", List.of(new HeldItemState("held-1", heldItem)));
            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(12, 12, Set.of(), Map.of()),
                    List.of(attacker, defender),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), heldItems
            );
            MoveOption move = MoveOption.standard(
                    moveName,
                    new MoveSpec("Melee", "Melee", 1, null, null, null, "Melee"),
                    new MoveCombatProfile(2, 4, 20, moveCategory, moveType)
            );
            return new Scenario(state, move, contextBonuses);
        }
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            CombatantStatProfile stats,
            int desiredEvasion,
            List<String> abilities
    ) {
        EvasionProfile evasion = new EvasionProfile(
                stats,
                desiredEvasion - 1,
                desiredEvasion - 1,
                desiredEvasion - 1,
                false,
                false
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 5),
                100,
                100,
                new ActionBudget(),
                stats,
                evasion,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                abilities
        );
    }

    private static CombatantStatProfile stats() {
        EnumMap<CombatStat, Integer> bases = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) bases.put(stat, 5);
        return new CombatantStatProfile(bases, Map.of(), Map.of(), Set.of());
    }

    private record Scenario(
            BattleRuntimeState state,
            MoveOption move,
            RuntimeTemporaryAccuracyBonusInputs.ContextBonuses contextBonuses
    ) { }
}
