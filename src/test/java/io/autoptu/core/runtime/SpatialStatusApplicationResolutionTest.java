package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.BuiltinStatusApplicationHooks;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialStatusApplicationResolutionTest {
    @Test
    void aromaVeilBlocksConfusionAtRadiusThreeWithoutInventingTeamFilter() {
        BattleRuntimeState state = state("Aroma Veil", new GridCoord(1, 4), 20, true, "other-team");

        StatusApplicationResult result = apply(state, "Confused");

        assertFalse(result.applied());
        assertFalse(state.hasStatus("target", "confused"));
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("Aroma Veil", event.sourceName());
        assertEquals("veil", event.actorId());
        assertEquals("target", event.targetId());
        assertEquals("status_block", event.effect());
    }

    @Test
    void aromaVeilErrataUsesRadiusOne() {
        BattleRuntimeState outside = state("Aroma Veil [Errata]", new GridCoord(1, 3), 20, true, "target-team");
        assertTrue(apply(outside, "Suppressed").applied());

        BattleRuntimeState adjacent = state("Aroma Veil [Errata]", new GridCoord(1, 2), 20, true, "target-team");
        assertFalse(apply(adjacent, "Enraged").applied());
    }

    @Test
    void pastelAndSweetVeilShareGenericSpatialResolver() {
        BattleRuntimeState pastel = state("Pastel Veil", new GridCoord(4, 1), 20, true, "target-team");
        StatusApplicationResult poison = apply(pastel, "Badly Poisoned");
        assertFalse(poison.applied());
        assertEquals("Pastel Veil", ((RuleEffectEvent) poison.events().getFirst()).sourceName());

        BattleRuntimeState sweet = state("Sweet Veil", new GridCoord(1, 4), 20, true, "target-team");
        StatusApplicationResult sleep = apply(sweet, "Asleep");
        assertFalse(sleep.applied());
        assertEquals("Sweet Veil", ((RuleEffectEvent) sleep.events().getFirst()).sourceName());
    }

    @Test
    void inactiveFaintedAndSuppressedSpatialSourcesDoNotBlock() {
        BattleRuntimeState inactive = state("Sweet Veil", new GridCoord(1, 2), 20, false, "target-team");
        assertTrue(apply(inactive, "Sleep").applied());

        BattleRuntimeState fainted = state("Pastel Veil", new GridCoord(1, 2), 0, true, "target-team");
        assertTrue(apply(fainted, "Poisoned").applied());

        BattleRuntimeState suppressed = state("Aroma Veil", new GridCoord(1, 2), 20, true, "target-team");
        suppressed.requireCombatant("target").setAbilitiesSuppressedFromRuntime(true);
        assertTrue(apply(suppressed, "Confused").applied());
    }

    private static StatusApplicationResult apply(BattleRuntimeState state, String status) {
        return StatusApplicationResolution.apply(
                state,
                BuiltinStatusApplicationHooks.registry(),
                "source",
                "target",
                new StatusEntry(status),
                "move",
                "Test Move",
                "test-move"
        );
    }

    private static BattleRuntimeState state(
            String veilAbility,
            GridCoord veilPosition,
            int veilHp,
            boolean veilActive,
            String veilTeam
    ) {
        RuntimeCombatantState source = combatant("source", new GridCoord(0, 0), 20, List.of());
        RuntimeCombatantState target = combatant("target", new GridCoord(1, 1), 20, List.of());
        RuntimeCombatantState veil = combatant("veil", veilPosition, veilHp, List.of(veilAbility));
        return new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(source, target, veil),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "source", CombatantAffiliationState.active("source-team"),
                        "target", CombatantAffiliationState.active("target-team"),
                        "veil", new CombatantAffiliationState(veilTeam, veilActive)
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, int hp, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                hp,
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
