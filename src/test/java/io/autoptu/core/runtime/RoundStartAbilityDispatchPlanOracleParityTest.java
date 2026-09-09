package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
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

class RoundStartAbilityDispatchPlanOracleParityTest {
    @Test
    void plannerMatchesPinnedPythonRoundStartOrdering() throws IOException {
        Path fixture = Path.of("build/oracle/round-start-ability-dispatch.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        List<String> holders = splitNonBlank(expected.get("AIR_LOCK_HOLDERS"), ",");
        List<RoundStartAbilityDispatchPlan.Combatant> combatants = parseCombatants(expected.get("COMBATANTS"));
        List<RoundStartAbilityDispatchPlan.Invocation> actual = RoundStartAbilityDispatchPlan.plan(
                expected.get("WEATHER"),
                holders,
                combatants
        );

        assertEquals(expected.get("INVOCATIONS"), encode(actual));
        assertEquals(
                "round_start_event,trainer_feature:round_start,ability_query:Air Lock,air_lock:air-two,air_lock:air-one,arena_trap,intimidate:actor-b,impostor:actor-b,intimidate:actor-a,impostor:actor-a",
                expected.get("TIMELINE"),
                "Python must keep round-start abilities after Trainer Features and preserve family/actor order"
        );
    }

    @Test
    void authoritativeRolloverMatchesPinnedPythonRoundAndInitiativeState() throws IOException {
        Path fixture = Path.of("build/oracle/round-start-ability-dispatch.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        RuntimeCombatantState actorB = combatant("actor-b", 20);
        RuntimeCombatantState actorA = combatant("actor-a", 10);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actorB, actorA)
        );
        BattleRoundController controller = new BattleRoundController(state, 0);

        InitiativeTurnAdvanceResult result = controller.advanceInitiativeTurnWithRollover();

        List<String> expectedOrder = splitNonBlank(expected.get("INITIATIVE_ORDER_AFTER"), ",");
        int pythonStartRoundCursor = Integer.parseInt(expected.get("INITIATIVE_INDEX_AFTER_START_ROUND"));
        int expectedFirstActorCursor = pythonStartRoundCursor + 1;

        assertEquals(Integer.parseInt(expected.get("ROUND_AFTER")), controller.round());
        assertEquals(expectedOrder, state.initiativeProgress().orderedActorIds());
        assertEquals(expectedFirstActorCursor, state.initiativeProgress().cursor());
        assertEquals(expectedFirstActorCursor, result.initiativeIndex());
        assertEquals(expectedOrder.get(expectedFirstActorCursor), result.actorId());
        assertEquals(result.actorId(), controller.turnState().currentActorId());
    }

    private static RuntimeCombatantState combatant(String id, int speed) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.SPD, speed),
                Map.of(),
                Map.of(),
                Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 4),
                20,
                20,
                new ActionBudget(),
                stats
        );
    }

    private static String encode(List<RoundStartAbilityDispatchPlan.Invocation> invocations) {
        ArrayList<String> encoded = new ArrayList<>();
        for (RoundStartAbilityDispatchPlan.Invocation invocation : invocations) {
            encoded.add(invocation.family() + "|" + invocation.scope().name() + "|" + invocation.actorId());
        }
        return String.join(";", encoded);
    }

    private static List<RoundStartAbilityDispatchPlan.Combatant> parseCombatants(String value) {
        ArrayList<RoundStartAbilityDispatchPlan.Combatant> result = new ArrayList<>();
        for (String item : splitNonBlank(value, ";")) {
            String[] parts = item.split(":", -1);
            if (parts.length != 3) throw new IllegalArgumentException("invalid combatant row: " + item);
            result.add(new RoundStartAbilityDispatchPlan.Combatant(
                    parts[0],
                    Boolean.parseBoolean(parts[1]),
                    Boolean.parseBoolean(parts[2])
            ));
        }
        return List.copyOf(result);
    }

    private static List<String> splitNonBlank(String value, String delimiter) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(java.util.regex.Pattern.quote(delimiter), -1));
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid fixture row: " + line);
            result.put(parts[0], parts[1]);
        }
        return Map.copyOf(result);
    }
}
