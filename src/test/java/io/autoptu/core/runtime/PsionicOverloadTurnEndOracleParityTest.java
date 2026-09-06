package io.autoptu.core.runtime;

import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.hook.BuiltinTurnEndEffects;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PsionicOverloadTurnEndOracleParityTest {
    @Test
    void matchesPinnedPythonStateAndOrderedSemanticEvent() throws IOException {
        String oracle = System.getenv("AUTOPTU_PSIONIC_OVERLOAD_TURN_END_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Psionic Overload fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        assertEquals(
                "case\tmax_hp\thp_before\ttemp_hp_before\tlifted\tfainted_before\tbinding_sources\thp_after\ttemp_hp_after\tbindings_after\tevent_actor\tevent_amount\tevent_target_hp\tevent_order\tfainted_after",
                lines.getFirst()
        );
        assertEquals(6, lines.size(), "Expected header plus five frozen oracle scenarios");

        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            String caseName = fields[0];
            int maxHp = Integer.parseInt(fields[1]);
            int hpBefore = Integer.parseInt(fields[2]);
            int tempHpBefore = Integer.parseInt(fields[3]);
            boolean lifted = fields[4].equals("1");
            List<String> sources = fields[6].isBlank() ? List.of() : List.of(fields[6].split(","));
            int expectedHp = Integer.parseInt(fields[7]);
            int expectedTempHp = Integer.parseInt(fields[8]);
            int expectedBindings = Integer.parseInt(fields[9]);
            String expectedActor = fields[10];
            int expectedAmount = Integer.parseInt(fields[11]);
            int expectedTargetHp = Integer.parseInt(fields[12]);
            String expectedOrder = fields[13];
            boolean expectedFainted = fields[14].equals("1");

            RuntimeCombatantState actor = combatant("actor", maxHp, maxHp);
            RuntimeCombatantState target = combatant("target", hpBefore, maxHp);
            target.replaceTempHpFromRuntime(tempHpBefore);
            for (String source : sources) {
                target.temporaryEffects().add("psionic_overload_telekinesis", Map.of("source_id", source));
            }
            BattleRuntimeState state = state(actor, target, lifted);

            LifecycleHookResult result = BuiltinTurnEndEffects.registry().resolve(context(state));

            assertEquals(expectedHp, target.hp(), caseName + " hp");
            assertEquals(expectedTempHp, target.tempHp(), caseName + " temp hp");
            assertEquals(expectedBindings, target.temporaryEffects().count("psionic_overload_telekinesis"), caseName + " bindings");
            assertEquals(expectedFainted, target.hp() <= 0, caseName + " fainted projection");

            List<String> normalizedOrder = new ArrayList<>();
            TrainerFeatureEvent featureEvent = null;
            for (var event : result.events()) {
                if (event instanceof TrainerFeatureEvent trainerFeature
                        && trainerFeature.feature().equals("Psionic Overload")
                        && trainerFeature.effect().equals("telekinesis_tick")) {
                    normalizedOrder.add("telekinesis_tick");
                    featureEvent = trainerFeature;
                }
            }
            normalizedOrder.add("turn_end");
            assertEquals(expectedOrder, String.join(",", normalizedOrder), caseName + " event order");

            if (expectedActor.isBlank()) {
                assertEquals(null, featureEvent, caseName + " feature event");
            } else {
                TrainerFeatureEvent event = assertInstanceOf(TrainerFeatureEvent.class, featureEvent, caseName);
                assertEquals(expectedActor, event.actorId(), caseName + " event actor");
                assertEquals(expectedAmount, event.amount(), caseName + " event amount");
                assertEquals(expectedTargetHp, event.targetHp(), caseName + " event target hp");
                assertEquals("target", event.details().get("target"), caseName + " event target");
            }
        }
    }

    private static LifecycleHookContext context(BattleRuntimeState state) {
        return new LifecycleHookContext(
                state,
                state.damageHistory(),
                state.injuryHistory(),
                LifecycleHookPoint.TURN_END,
                2,
                2,
                "actor",
                TurnPhase.END
        );
    }

    private static BattleRuntimeState state(
            RuntimeCombatantState actor,
            RuntimeCombatantState target,
            boolean lifted
    ) {
        Map<String, List<String>> statuses = lifted
                ? Map.of("target", List.of("Lifted"))
                : Map.of();
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(actor, target),
                statuses
        );
    }

    private static RuntimeCombatantState combatant(String id, int hp, int maxHp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 1),
                hp,
                maxHp,
                new ActionBudget()
        );
    }
}
