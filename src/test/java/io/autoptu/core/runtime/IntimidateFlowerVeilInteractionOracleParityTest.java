package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.CombatStageChangedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntimidateFlowerVeilInteractionOracleParityTest {
    @Test
    void flowerVeilSpatialPreventionMatchesPinnedPythonStateAndEventOrder() throws IOException {
        Path fixture = Path.of("build/oracle/intimidate-flower-veil.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Expected> expected = parse(Files.readAllLines(fixture));

        for (Map.Entry<String, Expected> entry : expected.entrySet()) {
            String scenario = entry.getKey();
            Expected oracle = entry.getValue();
            BattleRuntimeState state = scenario(oracle);
            IntimidateTriggerContract.Plan plan = IntimidateTriggerContract.plan(state, "holder");
            List<BattleEvent> events = IntimidateEffectExecutor.apply(state, "holder", plan);

            assertEquals(
                    oracle.attackStage(),
                    state.requireCombatant("target").combatStages().get(CombatStageStat.ATK),
                    scenario
            );
            assertEquals(oracle.events(), normalize(scenario, events), scenario);
        }
    }

    private static BattleRuntimeState scenario(Expected oracle) {
        List<RuntimeCombatantState> combatants = List.of(
                combatant("holder", new GridCoord(2, 2), List.of("Normal"), List.of("Intimidate")),
                combatant("target", new GridCoord(2, 3), List.of(oracle.targetType()), List.of()),
                combatant("veil", new GridCoord(oracle.veilX(), oracle.veilY()), List.of("Normal"), List.of(oracle.veilAbility()))
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(20, 20, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "holder", CombatantAffiliationState.active("players"),
                        "target", CombatantAffiliationState.active("foes"),
                        "veil", CombatantAffiliationState.active("foes")
                )
        );
        state.syncCurrentRoundFromLifecycle(3);
        state.requireCombatant("holder").temporaryEffects().add(
                IntimidateTriggerContract.JOINED_ROUND,
                Map.of("round", 3)
        );
        return state;
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            List<String> types,
            List<String> abilities
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                20,
                20,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                types,
                List.of(),
                abilities
        );
    }

    private static List<String> normalize(String scenario, List<BattleEvent> events) {
        ArrayList<String> rows = new ArrayList<>();
        for (BattleEvent event : events) {
            if (event instanceof RuleEffectEvent rule && "combat_stage_block".equals(rule.effect())) {
                rows.add(String.join("\t",
                        "PREVENT", scenario, rule.sourceName(), rule.actorId(), rule.targetId(), rule.moveId(), rule.effect()));
            } else if (event instanceof CombatStageChangedEvent stage && "target".equals(stage.targetId())) {
                rows.add(String.join("\t",
                        "STAGE", scenario, stage.actorId(), stage.targetId(), stage.moveId(),
                        stage.stat().name().toLowerCase(Locale.ROOT), stage.effect(), Integer.toString(stage.newStage())));
            } else if (event instanceof AbilityEvent ability
                    && "Intimidate".equals(ability.ability())
                    && "target".equals(ability.target())) {
                rows.add(String.join("\t",
                        "INTIMIDATE", scenario, ability.actorId(), ability.target(), ability.effect(),
                        String.valueOf(ability.details().getOrDefault("move", ""))));
            }
        }
        return List.copyOf(rows);
    }

    private static Map<String, Expected> parse(List<String> lines) {
        LinkedHashMap<String, MutableExpected> mutable = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            switch (parts[0]) {
                case "SCENARIO" -> mutable.put(parts[1], new MutableExpected(
                        parts[2],
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        parts[5],
                        Integer.parseInt(parts[6])
                ));
                case "PREVENT", "STAGE", "INTIMIDATE" -> mutable.get(parts[1]).events.add(line);
                default -> throw new IllegalArgumentException("unknown fixture row: " + line);
            }
        }
        LinkedHashMap<String, Expected> result = new LinkedHashMap<>();
        mutable.forEach((key, value) -> result.put(key, new Expected(
                value.veilAbility,
                value.veilX,
                value.veilY,
                value.targetType,
                value.attackStage,
                List.copyOf(value.events)
        )));
        return Map.copyOf(result);
    }

    private record Expected(
            String veilAbility,
            int veilX,
            int veilY,
            String targetType,
            int attackStage,
            List<String> events
    ) {}

    private static final class MutableExpected {
        private final String veilAbility;
        private final int veilX;
        private final int veilY;
        private final String targetType;
        private final int attackStage;
        private final ArrayList<String> events = new ArrayList<>();

        private MutableExpected(String veilAbility, int veilX, int veilY, String targetType, int attackStage) {
            this.veilAbility = veilAbility;
            this.veilX = veilX;
            this.veilY = veilY;
            this.targetType = targetType;
            this.attackStage = attackStage;
        }
    }
}
