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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntimidateStageReactionOracleParityTest {
    @Test
    void defiantAndCompetitiveMatchPinnedPythonFinalStateAndEventOrder() throws IOException {
        Path fixture = Path.of("build/oracle/intimidate-stage-reactions.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        List<String> oracle = Files.readAllLines(fixture).stream().filter(line -> !line.isBlank()).toList();

        ArrayList<String> actual = new ArrayList<>();
        for (String ability : List.of("Defiant", "Competitive")) {
            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(10, 10, Set.of(), Map.of()),
                    List.of(
                            combatant("holder", new GridCoord(2, 2), List.of("Intimidate")),
                            combatant("target", new GridCoord(2, 3), List.of(ability))
                    ),
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

            IntimidateTriggerContract.Plan plan = IntimidateTriggerContract.plan(state, "holder");
            List<BattleEvent> events = IntimidateEffectExecutor.apply(state, "holder", plan);
            boolean used = state.requireCombatant("holder").temporaryEffects()
                    .getAll(IntimidateTriggerContract.USED).stream()
                    .anyMatch(entry -> entry.payload().get("round") instanceof Number number && number.intValue() == 3);

            actual.add(String.join("\t", "CASE", ability));
            actual.add(String.join("\t",
                    "STATE",
                    Integer.toString(state.requireCombatant("target").combatStages().get(CombatStageStat.ATK)),
                    Integer.toString(state.requireCombatant("target").combatStages().get(CombatStageStat.SPATK)),
                    used ? "1" : "0"
            ));
            for (BattleEvent event : events) {
                if (event instanceof CombatStageChangedEvent stage) {
                    actual.add(String.join("\t",
                            "STAGE", stage.actorId(), stage.targetId(), stage.moveId(),
                            stage.stat().name().toLowerCase(Locale.ROOT), stage.effect(),
                            Integer.toString(stage.amount()), Integer.toString(stage.newStage())));
                } else if (event instanceof AbilityEvent eventAbility && "Intimidate".equals(eventAbility.ability())) {
                    actual.add(String.join("\t",
                            "INTIMIDATE", eventAbility.actorId(), eventAbility.target(), eventAbility.effect(),
                            String.valueOf(eventAbility.details().getOrDefault("move", ""))));
                }
            }
            assertTrue(plan.shouldTrigger());
        }

        assertEquals(oracle, actual);
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, List<String> abilities) {
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
                abilities
        );
    }
}
