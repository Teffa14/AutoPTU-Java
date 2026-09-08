package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusEffectMutationExecutorTest {
    @Test
    void appliesArenaTrapStatusMetadataAndOrderedSemanticEvents() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(12, 12, Set.of(), Map.of()),
                List.of(
                        combatant("trap-holder", 2, 2),
                        combatant("foe-b", 2, 3),
                        combatant("foe-a", 3, 3)
                )
        );

        List<BattleEvent> events = StatusEffectMutationExecutor.apply(
                state,
                ArenaTrapEffectPlan.statusInstructionsForTargets(
                        "trap-holder",
                        List.of("foe-b", "foe-a")
                )
        );

        assertStatus(state, "foe-b");
        assertStatus(state, "foe-a");
        assertEquals(List.of("foe-b", "foe-a"), events.stream()
                .map(event -> ((AbilityEvent) event).target())
                .toList());
        for (BattleEvent event : events) {
            AbilityEvent abilityEvent = (AbilityEvent) event;
            assertEquals("trap-holder", abilityEvent.actorId());
            assertEquals("Arena Trap", abilityEvent.ability());
            assertEquals("slowed", abilityEvent.effect());
            assertEquals("Arena Trap slows nearby foes.", abilityEvent.description());
        }
    }

    private static void assertStatus(BattleRuntimeState state, String targetId) {
        StatusEntry entry = state.statusEntry(targetId, "Slowed").orElseThrow();
        assertEquals("slowed", entry.name());
        assertEquals(1, entry.intPayload("remaining").orElseThrow());
        assertEquals("Arena Trap", entry.stringPayload("source").orElseThrow());
        assertEquals("trap-holder", entry.stringPayload("source_id").orElseThrow());
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 4),
                20,
                20,
                new ActionBudget()
        );
    }
}
