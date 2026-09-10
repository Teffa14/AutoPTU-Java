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

final class CombatantSwitchEntryStatePlanOracleParityTest {
    @Test
    void temporaryEffectPrefixMatchesPinnedPythonApplySwitch() throws IOException {
        Path fixture = Path.of("build/oracle/switch-entry-state.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        RuntimeCombatantState outgoing = combatant("a-1", new GridCoord(4, 3));
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
        state.syncCurrentRoundFromLifecycle(Integer.parseInt(expected.get("ROUND")));

        CombatantSwitchTransitionPlan transition = CombatantSwitchTransitionPlan.resolve(state, "a-1", "a-2");
        CombatantSwitchEntryStatePlan plan = CombatantSwitchEntryStatePlan.resolve(state, transition);

        assertEquals(
                List.of(
                        CombatantSwitchEntryStatePlan.TemporaryEffectOperation.ADD,
                        CombatantSwitchEntryStatePlan.TemporaryEffectOperation.REMOVE_ALL,
                        CombatantSwitchEntryStatePlan.TemporaryEffectOperation.REMOVE_ALL,
                        CombatantSwitchEntryStatePlan.TemporaryEffectOperation.ADD,
                        CombatantSwitchEntryStatePlan.TemporaryEffectOperation.ADD
                ),
                plan.temporaryEffectMutations().stream()
                        .map(CombatantSwitchEntryStatePlan.TemporaryEffectMutation::operation)
                        .toList()
        );
        assertEquals(
                List.of("recalled", "recalled", "released_from_ball", "released_from_ball", "joined_round"),
                plan.temporaryEffectMutations().stream()
                        .map(CombatantSwitchEntryStatePlan.TemporaryEffectMutation::effectName)
                        .toList()
        );

        plan.applyTemporaryEffects(state);

        assertEquals(expected.get("OUTGOING_RECALLED_COUNT"),
                Integer.toString(outgoing.temporaryEffects().count(CombatantSwitchEntryStatePlan.RECALLED)));
        assertEquals(expected.get("REPLACEMENT_RECALLED_COUNT"),
                Integer.toString(replacement.temporaryEffects().count(CombatantSwitchEntryStatePlan.RECALLED)));
        assertEquals(expected.get("RELEASED_COUNT"),
                Integer.toString(replacement.temporaryEffects().count(CombatantSwitchEntryStatePlan.RELEASED_FROM_BALL)));
        assertEquals(expected.get("JOINED_COUNT"),
                Integer.toString(replacement.temporaryEffects().count(CombatantSwitchEntryStatePlan.JOINED_ROUND)));
        assertEquals(expected.get("OUTGOING_RECALLED_ROUND"), payloadRound(outgoing, CombatantSwitchEntryStatePlan.RECALLED));
        assertEquals(expected.get("RELEASED_ROUND"), payloadRound(replacement, CombatantSwitchEntryStatePlan.RELEASED_FROM_BALL));
        assertEquals(expected.get("JOINED_ROUND"), payloadRound(replacement, CombatantSwitchEntryStatePlan.JOINED_ROUND));

        assertEquals(expected.get("POSITION"),
                transition.replacementDestination().x() + "," + transition.replacementDestination().y());
        assertEquals(expected.get("OUTGOING_ACTIVE"), transition.outgoingActiveAfter() ? "1" : "0");
        assertEquals(expected.get("OUTGOING_POSITION_IS_NONE"), transition.outgoingOffFieldAfter() ? "1" : "0");
        assertEquals(expected.get("REPLACEMENT_ACTIVE"), transition.replacementActiveAfter() ? "1" : "0");
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
