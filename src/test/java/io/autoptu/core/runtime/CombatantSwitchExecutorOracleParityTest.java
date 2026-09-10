package io.autoptu.core.runtime;

import io.autoptu.core.hook.BuiltinLifecycleHooks;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CombatantSwitchExecutorOracleParityTest {
    @Test
    void executorMatchesPinnedPythonSwitchPrefixFinalStateAndOrder() throws IOException {
        Path stateFixture = Path.of("build/oracle/switch-entry-state.tsv");
        Path orderFixture = Path.of("build/oracle/switch-execution-order.tsv");
        Assumptions.assumeTrue(Files.exists(stateFixture));
        Assumptions.assumeTrue(Files.exists(orderFixture));
        Map<String, String> expectedState = parse(Files.readAllLines(stateFixture));
        Map<String, String> expectedOrder = parse(Files.readAllLines(orderFixture));

        GridCoord outgoingPosition = new GridCoord(4, 3);
        RuntimeCombatantState outgoing = combatant("a-1", outgoingPosition);
        RuntimeCombatantState replacement = combatant("a-2", new GridCoord(0, 0));
        replacement.temporaryEffects().add(CombatantSwitchEntryStatePlan.RECALLED, Map.of("round", 0));
        replacement.temporaryEffects().add(CombatantSwitchEntryStatePlan.RELEASED_FROM_BALL, Map.of("round", 0));

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(outgoing, replacement),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "a-1", CombatantAffiliationState.active("players"),
                        "a-2", new CombatantAffiliationState("players", false)
                )
        );
        state.syncCurrentRoundFromLifecycle(Integer.parseInt(expectedState.get("ROUND")));
        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(Map.of("a-1", outgoingPosition));

        CombatantSwitchExecutor.ExecutionResult result = CombatantSwitchExecutor.execute(
                state,
                presence,
                BuiltinLifecycleHooks.registry(),
                "a-1",
                "a-2"
        );

        assertEquals(
                Arrays.stream(expectedOrder.get("ORDER").split(","))
                        .map(CombatantSwitchExecutionPlan.Stage::valueOf)
                        .toList(),
                result.plan().stages()
        );
        assertEquals("1".equals(expectedState.get("OUTGOING_ACTIVE")), state.isActive("a-1"));
        assertEquals("1".equals(expectedState.get("REPLACEMENT_ACTIVE")), state.isActive("a-2"));
        assertEquals("1".equals(expectedState.get("OUTGOING_POSITION_IS_NONE")), !presence.isOnField("a-1"));
        assertFalse(presence.position("a-1").isPresent());
        assertTrue(presence.isOnField("a-2"));
        assertEquals(expectedState.get("POSITION"), presence.position("a-2")
                .map(position -> position.x() + "," + position.y())
                .orElse(""));
        assertEquals(expectedState.get("POSITION"),
                replacement.position().x() + "," + replacement.position().y(),
                "server-owned tactical position must match the Python replacement position before post-entry hooks run");

        assertEquals(expectedState.get("OUTGOING_RECALLED_COUNT"),
                Integer.toString(outgoing.temporaryEffects().count(CombatantSwitchEntryStatePlan.RECALLED)));
        assertEquals(expectedState.get("REPLACEMENT_RECALLED_COUNT"),
                Integer.toString(replacement.temporaryEffects().count(CombatantSwitchEntryStatePlan.RECALLED)));
        assertEquals(expectedState.get("RELEASED_COUNT"),
                Integer.toString(replacement.temporaryEffects().count(CombatantSwitchEntryStatePlan.RELEASED_FROM_BALL)));
        assertEquals(expectedState.get("JOINED_COUNT"),
                Integer.toString(replacement.temporaryEffects().count(CombatantSwitchEntryStatePlan.JOINED_ROUND)));
        assertEquals(expectedState.get("RELEASED_ROUND"), payloadRound(replacement, CombatantSwitchEntryStatePlan.RELEASED_FROM_BALL));
        assertEquals(expectedState.get("JOINED_ROUND"), payloadRound(replacement, CombatantSwitchEntryStatePlan.JOINED_ROUND));
        assertTrue(result.entryHookResult().events().isEmpty());
    }

    @Test
    void validatesPresenceBeforeMutatingAffiliationOrRuntimePosition() {
        RuntimeCombatantState outgoing = combatant("a-1", new GridCoord(4, 3));
        GridCoord originalReplacementPosition = new GridCoord(0, 0);
        RuntimeCombatantState replacement = combatant("a-2", originalReplacementPosition);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(outgoing, replacement),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "a-1", CombatantAffiliationState.active("players"),
                        "a-2", new CombatantAffiliationState("players", false)
                )
        );
        CombatantFieldPresenceStore invalidPresence = new CombatantFieldPresenceStore(Map.of());

        try {
            CombatantSwitchExecutor.execute(
                    state,
                    invalidPresence,
                    BuiltinLifecycleHooks.registry(),
                    "a-1",
                    "a-2"
            );
        } catch (IllegalArgumentException expected) {
            assertTrue(state.isActive("a-1"));
            assertFalse(state.isActive("a-2"));
            assertEquals(originalReplacementPosition, replacement.position());
            return;
        }
        throw new AssertionError("expected invalid field presence to reject switch");
    }

    private static String payloadRound(RuntimeCombatantState combatant, String effectName) {
        List<TemporaryEffectEntry> entries = combatant.temporaryEffects().getAll(effectName);
        if (entries.isEmpty()) return "";
        Object round = entries.get(entries.size() - 1).payload().get("round");
        return round == null ? "" : round.toString();
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                20,
                20,
                new ActionBudget()
        );
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            int separator = line.indexOf('\t');
            if (separator < 0) throw new IllegalArgumentException("invalid fixture row: " + line);
            values.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return Map.copyOf(values);
    }
}
