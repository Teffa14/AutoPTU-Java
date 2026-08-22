package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainerFeatureEffectRegistryOracleParityTest {
    @Test
    void healFamilyMatchesPinnedPythonDispatcher() throws IOException {
        Path fixture = Path.of("build/oracle/trainer-feature-effects.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Expected> expected = readFixture(fixture);

        LinkedHashMap<String, EffectCase> cases = new LinkedHashMap<>();
        cases.put("heal_injured_actor", effectCase(
                Map.of(), Map.of("type", "heal", "amount", 7, "target_rules", Map.of("scope", "actor")), "ally"
        ));
        cases.put("heal_active_alias", effectCase(
                Map.of(), Map.of("type", "heal_active", "amount", 3, "target_rules", Map.of("scope", "actor")), "ally"
        ));
        cases.put("heal_full_hp_not_applied", effectCase(
                Map.of(), Map.of("type", "heal", "amount", 7, "target_rules", Map.of("scope", "actor")), "ally_full"
        ));
        cases.put("heal_zero_not_applied", effectCase(
                Map.of(), Map.of("type", "heal", "amount", 0, "target_rules", Map.of("scope", "actor")), "ally"
        ));
        cases.put("heal_negative_not_applied", effectCase(
                Map.of(), Map.of("type", "heal", "amount", -3, "target_rules", Map.of("scope", "actor")), "ally"
        ));
        cases.put("heal_float_string_int_like", effectCase(
                Map.of(), Map.of("type", "heal", "amount", "4.9", "target_rules", Map.of("scope", "actor")), "ally"
        ));
        cases.put("heal_multiple_only_changed_targets", effectCase(
                Map.of(), Map.of("type", "heal", "amount", 5, "target_rules", Map.of("scope", "all_allies")), "ally"
        ));
        cases.put("effect_target_rules_override_feature", effectCase(
                Map.of("target_rules", Map.of("scope", "active_allies")),
                Map.of("type", "heal", "amount", 4, "target_rules", Map.of("scope", "active_enemies")),
                "ally"
        ));
        cases.put("blank_effect_is_log_only", effectCase(Map.of(), Map.of(), "ally"));
        cases.put("unknown_effect_is_applied_scaffold", effectCase(
                Map.of(), Map.of("type", "future_effect"), "ally"
        ));

        assertEquals(expected.keySet(), cases.keySet());
        TrainerFeatureEffectRegistry registry = new TrainerFeatureEffectRegistry();
        for (Map.Entry<String, EffectCase> entry : cases.entrySet()) {
            BattleRuntimeState state = state();
            EffectCase current = entry.getValue();
            TrainerFeatureEffectRegistry.EffectResult result = registry.apply(
                    new TrainerFeatureEffectRegistry.EffectContext(
                            state, "t1", current.actorId(), current.feature(), Map.of()
                    ),
                    current.effect()
            );
            Expected exp = expected.get(entry.getKey());
            assertEquals(exp.applied(), result.applied(), entry.getKey() + " applied");
            assertEquals(exp.effectType(), result.effectType(), entry.getKey() + " type");
            assertEquals(exp.targets(), String.join(",", result.targets()), entry.getKey() + " targets");
            String amount = result.details().containsKey("amount")
                    ? String.valueOf(result.details().get("amount")) : "";
            assertEquals(exp.amount(), amount, entry.getKey() + " amount");
            assertEquals(exp.hpSnapshot(), hpSnapshot(state), entry.getKey() + " hp");
        }
    }

    @Test
    void duplicateEffectHandlerIdsFailClosed() {
        TrainerFeatureEffectRegistry registry = new TrainerFeatureEffectRegistry();
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("heal", (context, effect) ->
                        new TrainerFeatureEffectRegistry.EffectResult(false, "heal", List.of(), Map.of()))
        );
    }

    @Test
    void unknownTrainerCannotExecuteEffect() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrainerFeatureEffectRegistry.EffectContext(state(), "missing", "ally", Map.of(), Map.of())
        );
    }

    private static BattleRuntimeState state() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(
                        mon("ally", new GridCoord(0, 0), 5),
                        mon("ally_full", new GridCoord(1, 0), 20),
                        mon("enemy", new GridCoord(2, 0), 4)
                )
        );
        state.putTrainer(new TrainerRuntimeState("t1", List.of(), 0));
        state.putTrainer(new TrainerRuntimeState("t2", List.of(), 0));
        state.bindController("ally", "t1");
        state.bindController("ally_full", "t1");
        state.bindController("enemy", "t2");
        return state;
    }

    private static RuntimeCombatantState mon(String id, GridCoord position, int hp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                hp,
                20,
                new ActionBudget()
        );
    }

    private static String hpSnapshot(BattleRuntimeState state) {
        return String.join(",",
                "ally=" + state.requireCombatant("ally").hp(),
                "ally_full=" + state.requireCombatant("ally_full").hp(),
                "enemy=" + state.requireCombatant("enemy").hp()
        );
    }

    private static EffectCase effectCase(Map<String, ?> feature, Map<String, ?> effect, String actorId) {
        return new EffectCase(feature, effect, actorId);
    }

    private static Map<String, Expected> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Expected> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            out.put(parts[0], new Expected(
                    "1".equals(parts[1]), parts[2], parts[3], parts[4], parts[5]
            ));
        }
        return out;
    }

    private record EffectCase(Map<String, ?> feature, Map<String, ?> effect, String actorId) {}
    private record Expected(boolean applied, String effectType, String targets, String amount, String hpSnapshot) {}
}
