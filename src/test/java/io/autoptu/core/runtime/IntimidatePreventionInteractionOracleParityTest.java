package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntimidatePreventionInteractionOracleParityTest {
    @Test
    void targetOwnedPreventionsMatchPinnedPythonStateAndEventOrder() throws IOException {
        Path fixture = Path.of("build/oracle/intimidate-prevention.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Expected> expected = parse(Files.readAllLines(fixture));

        for (Map.Entry<String, Expected> entry : expected.entrySet()) {
            String scenario = entry.getKey();
            Expected oracle = entry.getValue();
            BattleRuntimeState state = scenario(oracle.blocker());
            IntimidateTriggerContract.Plan plan = IntimidateTriggerContract.plan(state, "holder");
            List<BattleEvent> events = IntimidateEffectExecutor.apply(state, "holder", plan);

            assertEquals(oracle.attackStage(), state.requireCombatant("target").combatStages().get(CombatStageStat.ATK), scenario);
            assertEquals(oracle.events(), normalize(scenario, events), scenario);
        }
    }

    private static BattleRuntimeState scenario(String blocker) {
        List<RuntimeCombatantState> combatants = List.of(
                combatant("holder", new GridCoord(2, 2), "Intimidate"),
                combatant("target", new GridCoord(2, 3), blocker)
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "holder", CombatantAffiliationState.active("players"),
                        "target", CombatantAffiliationState.active("foes")
                )
        );
        state.syncCurrentRoundFromLifecycle(3);
        state.requireCombatant("holder").temporaryEffects().add(
                IntimidateTriggerContract.JOINED_ROUND,
                Map.of("round", 3)
        );
        return state;
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, String ability) {
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
                List.of("Normal"),
                List.of(),
                List.of(ability)
        );
    }

    private static List<String> normalize(String scenario, List<BattleEvent> events) {
        ArrayList<String> rows = new ArrayList<>();
        for (BattleEvent event : events) {
            if (event instanceof RuleEffectEvent rule && "combat_stage_block".equals(rule.effect())) {
                rows.add(String.join("\t",
                        "PREVENT", scenario, rule.sourceName(), rule.actorId(), rule.targetId(), rule.moveId(), rule.effect()));
            } else if (event instanceof AbilityEvent ability && "Intimidate".equals(ability.ability())) {
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
                case "SCENARIO" -> mutable.put(parts[1], new MutableExpected(parts[2], Integer.parseInt(parts[3])));
                case "PREVENT", "INTIMIDATE" -> mutable.get(parts[1]).events.add(line);
                default -> throw new IllegalArgumentException("unknown fixture row: " + line);
            }
        }
        LinkedHashMap<String, Expected> result = new LinkedHashMap<>();
        mutable.forEach((key, value) -> result.put(key, new Expected(value.blocker, value.attackStage, List.copyOf(value.events))));
        return Map.copyOf(result);
    }

    private record Expected(String blocker, int attackStage, List<String> events) {}

    private static final class MutableExpected {
        private final String blocker;
        private final int attackStage;
        private final ArrayList<String> events = new ArrayList<>();

        private MutableExpected(String blocker, int attackStage) {
            this.blocker = blocker;
            this.attackStage = attackStage;
        }
    }
}
