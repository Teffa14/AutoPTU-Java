package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.CombatStageChangedEvent;
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

class IntimidateEffectExecutionOracleParityTest {
    @Test
    void baselineStageMutationMarkerAndOrderedEventsMatchPinnedPython() throws IOException {
        Path fixture = Path.of("build/oracle/intimidate-effect.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Expected expected = parse(Files.readAllLines(fixture));

        BattleRuntimeState state = scenario();
        IntimidateTriggerContract.Plan plan = IntimidateTriggerContract.plan(state, "holder");
        List<BattleEvent> events = IntimidateEffectExecutor.apply(state, "holder", plan);

        boolean used = state.requireCombatant("holder").temporaryEffects()
                .getAll(IntimidateTriggerContract.USED).stream()
                .anyMatch(entry -> entry.payload().get("round") instanceof Number number
                        && number.intValue() == state.currentRound());
        assertEquals(expected.used(), used);

        LinkedHashMap<String, Integer> stages = new LinkedHashMap<>();
        for (String id : List.of("near-first", "near-second", "far")) {
            stages.put(id, state.requireCombatant(id).combatStages().get(CombatStageStat.ATK));
        }
        assertEquals(expected.stages(), stages);
        assertEquals(expected.events(), normalize(events));
    }

    private static BattleRuntimeState scenario() {
        List<RuntimeCombatantState> combatants = List.of(
                combatant("holder", new GridCoord(2, 2), "Intimidate"),
                combatant("near-first", new GridCoord(2, 3), null),
                combatant("near-second", new GridCoord(3, 3), null),
                combatant("far", new GridCoord(4, 2), null)
        );
        Map<String, CombatantAffiliationState> affiliations = new LinkedHashMap<>();
        affiliations.put("holder", CombatantAffiliationState.active("players"));
        affiliations.put("near-first", CombatantAffiliationState.active("foes"));
        affiliations.put("near-second", CombatantAffiliationState.active("foes"));
        affiliations.put("far", CombatantAffiliationState.active("foes"));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(), affiliations
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
                ability == null ? List.of() : List.of(ability)
        );
    }

    private static List<String> normalize(List<BattleEvent> events) {
        ArrayList<String> rows = new ArrayList<>();
        for (BattleEvent event : events) {
            if (event instanceof CombatStageChangedEvent stage) {
                rows.add(String.join("\t",
                        "EVENT", "combat_stage", stage.actorId(), stage.targetId(),
                        stage.stat().name().toLowerCase(), stage.effect(),
                        Integer.toString(stage.amount()), Integer.toString(stage.newStage()),
                        stage.description()));
            } else if (event instanceof AbilityEvent ability && ability.ability().equals("Intimidate")) {
                rows.add(String.join("\t",
                        "EVENT", "ability", ability.actorId(), ability.target(), ability.effect(),
                        String.valueOf(ability.details().getOrDefault("move", "")),
                        Integer.toString(ability.targetHp()),
                        String.valueOf(ability.details().getOrDefault("round", 0)),
                        String.valueOf(ability.details().getOrDefault("phase", ""))));
            }
        }
        return List.copyOf(rows);
    }

    private static Expected parse(List<String> lines) {
        boolean used = false;
        LinkedHashMap<String, Integer> stages = new LinkedHashMap<>();
        ArrayList<String> events = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            switch (parts[0]) {
                case "USED" -> used = Boolean.parseBoolean(parts[1]);
                case "STAGE" -> stages.put(parts[1], Integer.parseInt(parts[2]));
                case "EVENT" -> events.add(line);
                default -> throw new IllegalArgumentException("invalid fixture row: " + line);
            }
        }
        return new Expected(used, Map.copyOf(stages), List.copyOf(events));
    }

    private record Expected(boolean used, Map<String, Integer> stages, List<String> events) {}
}
