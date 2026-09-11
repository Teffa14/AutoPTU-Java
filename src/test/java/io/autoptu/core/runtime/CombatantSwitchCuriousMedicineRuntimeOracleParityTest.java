package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.BuiltinLifecycleHooks;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CombatantSwitchCuriousMedicineRuntimeOracleParityTest {
    @Test
    void productionSwitchPathResetsPinnedTargetsAndPublishesPinnedTrace() throws IOException {
        Path oracle = Path.of("build/oracle/curious-medicine-switch.tsv");
        Assumptions.assumeTrue(Files.exists(oracle));
        OracleFixture fixture = readFixture(oracle);

        GridCoord entry = fixture.replacementPosition();
        RuntimeCombatantState outgoing = combatant("a-1", entry, List.of());
        RuntimeCombatantState replacement = combatant("a-2", new GridCoord(0, 0), List.of("Curious Medicine"));
        RuntimeCombatantState adjacent = combatant("a-3", fixture.combatants().get("a-3").position(), List.of());
        RuntimeCombatantState rangeTwo = combatant("a-4", fixture.combatants().get("a-4").position(), List.of());
        RuntimeCombatantState farther = combatant("a-5", fixture.combatants().get("a-5").position(), List.of());
        RuntimeCombatantState enemy = combatant("b-1", fixture.combatants().get("b-1").position(), List.of());
        RuntimeCombatantState inactive = combatant("a-6", new GridCoord(9, 9), List.of());

        Map<String, RuntimeCombatantState> states = Map.of(
                "a-2", replacement,
                "a-3", adjacent,
                "a-4", rangeTwo,
                "a-5", farther,
                "b-1", enemy,
                "a-6", inactive
        );
        for (Map.Entry<String, RuntimeCombatantState> entryState : states.entrySet()) {
            OracleCombatant expected = fixture.combatants().get(entryState.getKey());
            entryState.getValue().combatStages().set(CombatStageStat.ATK, expected.beforeAtk());
            entryState.getValue().combatStages().set(CombatStageStat.SPD, expected.beforeSpd());
        }

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(outgoing, replacement, adjacent, rangeTwo, farther, enemy, inactive),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "a-1", CombatantAffiliationState.active("players"),
                        "a-2", new CombatantAffiliationState("players", false),
                        "a-3", CombatantAffiliationState.active("players"),
                        "a-4", CombatantAffiliationState.active("players"),
                        "a-5", CombatantAffiliationState.active("players"),
                        "b-1", CombatantAffiliationState.active("foes"),
                        "a-6", new CombatantAffiliationState("players", false)
                )
        );
        state.syncCurrentRoundFromLifecycle(1);
        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(
                Map.of(
                        "a-1", entry,
                        "a-3", adjacent.position(),
                        "a-4", rangeTwo.position(),
                        "a-5", farther.position(),
                        "b-1", enemy.position()
                )
        );
        ArrayList<BattleEvent> sink = new ArrayList<>();

        CombatantSwitchExecutor.ExecutionResult result = CombatantSwitchExecutor.execute(
                state,
                presence,
                BuiltinLifecycleHooks.registry(),
                sink::add,
                "a-1",
                "a-2"
        );

        assertEquals(CombatantSwitchPostEntryDispatcher.StageStatus.EXECUTED,
                result.postEntryDispatchResult().stages().get(0).status());
        assertEquals(CombatantSwitchPostEntryDispatcher.StageStatus.EXECUTED,
                result.postEntryDispatchResult().stages().get(1).status());
        assertEquals(List.of(
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING,
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING,
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING),
                result.postEntryDispatchResult().stages().subList(2, 5).stream()
                        .map(CombatantSwitchPostEntryDispatcher.StageResult::status)
                        .toList());
        assertEquals(entry, replacement.position());
        assertEquals(1, replacement.temporaryEffects().count("curious_medicine_used"));

        for (Map.Entry<String, OracleCombatant> expectedEntry : fixture.combatants().entrySet()) {
            RuntimeCombatantState actual = states.get(expectedEntry.getKey());
            OracleCombatant expected = expectedEntry.getValue();
            assertEquals(expected.afterAtk(), actual.combatStages().get(CombatStageStat.ATK), expectedEntry.getKey() + " atk");
            assertEquals(expected.afterSpd(), actual.combatStages().get(CombatStageStat.SPD), expectedEntry.getKey() + " spd");
        }

        assertEquals(fixture.events().size(), sink.size());
        assertEquals(sink, result.postEntryDispatchResult().orderedEvents());
        for (int index = 0; index < fixture.events().size(); index++) {
            OracleEvent expected = fixture.events().get(index);
            AbilityEvent actual = (AbilityEvent) sink.get(index);
            assertEquals(expected.actorId(), actual.actorId());
            assertEquals(expected.targetId(), actual.target());
            assertEquals(expected.ability(), actual.ability());
            assertEquals(expected.effect(), actual.effect());
            assertEquals(expected.description(), actual.description());
            assertEquals(expected.targetHp(), actual.targetHp());
            assertEquals(expected.move(), actual.details().get("move"));
            assertEquals(expected.phase(), actual.details().get("phase"));
            assertEquals(expected.round(), actual.details().get("round"));
        }
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, List<String> abilities) {
        return new RuntimeCombatantState(
                id, MovementProfile.walking(position, 4), 60, 60, new ActionBudget(),
                null, null, 0, false, false, false, false,
                List.of("Normal"), List.of(), abilities
        );
    }

    private static OracleFixture readFixture(Path path) throws IOException {
        GridCoord replacementPosition = null;
        LinkedHashMap<String, OracleCombatant> combatants = new LinkedHashMap<>();
        ArrayList<OracleEvent> events = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("\\t", -1);
            if (parts.length == 3 && parts[0].equals("REPLACEMENT_POSITION")) {
                replacementPosition = new GridCoord(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            } else if (parts.length == 10 && parts[0].equals("COMBATANT")) {
                GridCoord position = parts[4].isBlank() ? null
                        : new GridCoord(Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
                combatants.put(parts[1], new OracleCombatant(
                        position,
                        Integer.parseInt(parts[6]), Integer.parseInt(parts[7]),
                        Integer.parseInt(parts[8]), Integer.parseInt(parts[9])
                ));
            } else if (parts.length == 11 && parts[0].equals("CURIOUS_MEDICINE_EVENT_STRUCT")) {
                events.add(new OracleEvent(
                        Integer.parseInt(parts[1]), parts[2], parts[3], parts[4], parts[5], parts[6],
                        parts[7], Integer.parseInt(parts[8]), parts[9], Integer.parseInt(parts[10])
                ));
            }
        }
        if (replacementPosition == null) throw new IllegalStateException("Missing replacement position fixture");
        events.sort(Comparator.comparingInt(OracleEvent::index));
        return new OracleFixture(replacementPosition, Map.copyOf(combatants), List.copyOf(events));
    }

    private record OracleCombatant(GridCoord position, int beforeAtk, int afterAtk, int beforeSpd, int afterSpd) {}
    private record OracleEvent(
            int index,
            String actorId,
            String targetId,
            String ability,
            String move,
            String effect,
            String description,
            int targetHp,
            String phase,
            int round
    ) {}
    private record OracleFixture(GridCoord replacementPosition, Map<String, OracleCombatant> combatants, List<OracleEvent> events) {}
}
