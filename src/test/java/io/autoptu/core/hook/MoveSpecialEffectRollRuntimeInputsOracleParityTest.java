package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TrainerRuntimeState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MoveSpecialEffectRollRuntimeInputsOracleParityTest {
    @Test
    void derivesEffectRollModifiersFromAuthoritativeBattleState() throws IOException {
        String fixturePath = System.getProperty("autoptu.move.special.effect.roll.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Path fixture = Path.of(fixturePath);
        Assumptions.assumeTrue(Files.exists(fixture));

        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            expected.put(parts[0], Integer.parseInt(parts[1]));
        }

        assertScenario(expected, "baseline", state(), move("Normal", "Physical", "Melee", ""));

        BattleRuntimeState immutable = state();
        immutable.requireCombatant("defender").temporaryEffects().add(
                "immutable_mind_block", Map.of("move", "Test", "expires_round", 3));
        assertScenario(expected, "immutable", immutable, move("Normal", "Physical", "Melee", ""));

        BattleRuntimeState rangeBlock = state();
        rangeBlock.requireCombatant("actor").temporaryEffects().add(
                "effect_range_block", Map.of("expires_round", 3));
        assertScenario(expected, "range_block", rangeBlock, move("Normal", "Physical", "Melee", ""));

        BattleRuntimeState serene = state(List.of("Serene Grace"), List.of(), Map.of());
        assertScenario(expected, "serene", serene, move("Normal", "Physical", "Melee", ""));

        BattleRuntimeState stench = state(List.of("Stench"), List.of(), Map.of());
        assertScenario(expected, "stench", stench, move("Normal", "Physical", "Melee", "Flinches on 18+."));

        BattleRuntimeState firebrand = state(List.of(), List.of("Firebrand"), Map.of());
        assertScenario(expected, "firebrand", firebrand, move("Fire", "Physical", "Melee", "Burns on 18+."));

        BattleRuntimeState penalty = state();
        penalty.requireCombatant("actor").temporaryEffects().add("all_roll_penalty", Map.of("amount", 3));
        assertScenario(expected, "roll_penalty", penalty, move("Normal", "Physical", "Melee", ""));

        BattleRuntimeState mindbreak = state();
        mindbreak.requireCombatant("actor").temporaryEffects().add("mindbreak_bound", Map.of());
        assertScenario(expected, "mindbreak", mindbreak, move("Psychic", "Special", "Melee", ""));

        BattleRuntimeState polished = state(List.of(), List.of("Polished Shine"), Map.of());
        assertScenario(expected, "polished", polished, move("Steel", "Physical", "Melee", ""));

        BattleRuntimeState brutal = state();
        brutal.requireCombatant("actor").temporaryEffects().add("brutal_training", Map.of());
        assertScenario(expected, "brutal", brutal, move("Normal", "Physical", "Melee", ""));

        BattleRuntimeState rangeBonus = state();
        rangeBonus.requireCombatant("actor").temporaryEffects().add(
                "effect_range_bonus", Map.of("amount", 2, "expires_round", 3));
        rangeBonus.requireCombatant("actor").temporaryEffects().add(
                "effect_range_bonus", Map.of("amount", -1, "expires_round", 3));
        assertScenario(expected, "range_bonus", rangeBonus, move("Normal", "Physical", "Melee", ""));

        BattleRuntimeState stratagem = state();
        stratagem.requireCombatant("actor").combatStages().set(CombatStat.SPATK, 5);
        stratagem.requireCombatant("actor").temporaryEffects().add("stat_stratagem", Map.of("stat", "spatk"));
        assertScenario(expected, "stat_stratagem", stratagem, move("Normal", "Special", "Ranged", ""));

        BattleRuntimeState stackedStratagem = state();
        stackedStratagem.requireCombatant("actor").combatStages().set(CombatStat.SPATK, 5);
        stackedStratagem.requireCombatant("actor").temporaryEffects().add("stat_stratagem", Map.of("stat", "spatk"));
        stackedStratagem.requireCombatant("actor").temporaryEffects().add("stat_stratagem", Map.of("stat", "atk"));
        stackedStratagem.requireCombatant("actor").temporaryEffects().add("stat_stratagem", Map.of("stat", "spatk"));
        assertScenario(expected, "stat_stratagem_stacked", stackedStratagem, move("Normal", "Special", "Ranged", ""));
    }

    @Test
    void hardenedInputComesFromCanonicalInjuriesTrainerAndTemporaryState() {
        BattleRuntimeState state = state(
                List.of(),
                List.of("Press On!"),
                Map.of("Intimidate", 6)
        );
        state.injuryHistory().setCurrentInjuries("actor", 1);
        state.requireCombatant("actor").temporaryEffects().add("hardened", Map.of());
        state.requireCombatant("actor").temporaryEffects().add("press_on_active", Map.of());

        MoveSpecialEffectRollResolution.Input input = MoveSpecialEffectRollRuntimeInputs.fromState(
                state, "actor", "defender", move("Normal", "Physical", "Melee", ""), 10);

        assertEquals(2, input.hardenedCritBonus());
        assertEquals(12, MoveSpecialEffectRollResolution.resolve(input));
    }

    private static void assertScenario(
            Map<String, Integer> expected,
            String scenario,
            BattleRuntimeState state,
            MoveOption move
    ) {
        MoveSpecialEffectRollResolution.Input input = MoveSpecialEffectRollRuntimeInputs.fromState(
                state, "actor", "defender", move, 10);
        assertEquals(expected.get(scenario), MoveSpecialEffectRollResolution.resolve(input), scenario);
    }

    private static BattleRuntimeState state() {
        return state(List.of(), List.of(), Map.of());
    }

    private static BattleRuntimeState state(
            List<String> abilities,
            List<String> trainerFeatures,
            Map<String, Integer> skillRanks
    ) {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), abilities);
        RuntimeCombatantState defender = combatant("defender", new GridCoord(2, 1), List.of());
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, defender)
        );
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", trainerFeatures, 0, 0, skillRanks);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        return state;
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, List<String> abilities) {
        CombatantStatProfile profile = new CombatantStatProfile(
                Map.of(CombatStat.ATK, 10, CombatStat.DEF, 10, CombatStat.SPATK, 10, CombatStat.SPDEF, 10, CombatStat.SPD, 10),
                Map.of(),
                Map.of(),
                Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                100,
                100,
                new ActionBudget(),
                profile,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of("Normal"),
                List.of(),
                abilities
        );
    }

    private static MoveOption move(String type, String category, String targetKind, String effectsText) {
        return new MoveOption(
                "Test",
                new MoveSpec(targetKind, targetKind, 1, 1, null, null, targetKind, List.of(), effectsText),
                ActionType.STANDARD,
                true,
                new MoveCombatProfile(2, category.equalsIgnoreCase("Status") ? 0 : 5, 20, category, type),
                "Scene x1"
        );
    }
}
