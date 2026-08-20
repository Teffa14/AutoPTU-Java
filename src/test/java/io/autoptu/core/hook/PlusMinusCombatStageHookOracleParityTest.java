package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatStageMutationOptions;
import io.autoptu.core.runtime.CombatantAffiliationState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.SpatialAbilityQuery;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PlusMinusCombatStageHookOracleParityTest {
    @Test
    void plusMinusRequestsAndEventsMatchPinnedPythonOracle() throws IOException {
        String oracle = System.getProperty("autoptu.combat.stage.hooks.oracle", "").strip();
        assumeTrue(!oracle.isBlank(), "combat-stage hook oracle fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            if (fields.length < 24) continue;
            String reaction = fields[8];
            if (!reaction.equals("minus") && !reaction.equals("plus")) continue;

            String scenario = fields[0];
            int appliedDelta = Integer.parseInt(fields[2]);
            String attackerId = fields[9];
            int recursiveCalls = Integer.parseInt(fields[13]);
            int recursiveDelta = Integer.parseInt(fields[16]);
            String expectedHolder = fields[17];
            int expectedRadius = Integer.parseInt(fields[20]);
            boolean skipGuard = Integer.parseInt(fields[21]) == 1;
            String expectedQueryAbility = fields[22];
            String expectedEventAbility = fields[23];
            int expectedEventCount = Integer.parseInt(fields[5]);
            String expectedEventEffect = fields[6];
            int expectedEventAmount = Integer.parseInt(fields[7]);

            ScenarioState fixture = scenarioState(scenario, reaction);
            BattleRuntimeState state = fixture.state();
            CombatStageMutationOptions options = CombatStageMutationOptions.NONE;
            if (skipGuard) {
                options = options.suppressing(reaction.equals("minus")
                        ? BuiltinCombatStageHooks.MINUS_SWSH_HOOK_ID
                        : BuiltinCombatStageHooks.PLUS_SWSH_HOOK_ID);
            }
            CombatStageHookContext context = new CombatStageHookContext(
                    state, attackerId, "target", "Test Move", CombatStat.DEF,
                    appliedDelta, appliedDelta, "fixture", options
            );

            CombatStageHookResult result = BuiltinCombatStageHooks.registry()
                    .apply(CombatStageHookPhase.POST_APPLY, context);

            if (expectedRadius > 0) {
                assertEquals(10, expectedRadius, scenario + " / oracle radius");
                assertEquals(reaction.equals("minus") ? "Minus [SwSh]" : "Plus [SwSh]",
                        expectedQueryAbility, scenario + " / oracle query ability");
            }
            assertEquals(recursiveCalls == 1 ? recursiveDelta : 0,
                    state.requireCombatant("target").combatStages().get(CombatStat.DEF), scenario + " / stage");
            assertEquals(expectedEventCount, result.events().size(), scenario + " / events");
            if (expectedEventCount > 0) {
                RuleEffectEvent event = (RuleEffectEvent) result.events().getLast();
                assertEquals(expectedHolder, event.actorId(), scenario + " / holder");
                assertEquals(expectedEventAbility, event.sourceName(), scenario + " / ability");
                assertEquals(expectedEventEffect, event.effect(), scenario + " / effect");
                assertEquals((double) expectedEventAmount, event.amount(), scenario + " / amount");
                assertEquals("target", event.targetId(), scenario + " / target");
                assertEquals("Test Move", event.moveId(), scenario + " / original move");
            }
        }
    }

    @Test
    void spatialAbilityQueryUsesCanonicalChebyshevRadiusAndInsertionOrder() {
        RuntimeCombatantState target = combatant("target", new GridCoord(5, 5), List.of());
        RuntimeCombatantState first = combatant("first", new GridCoord(15, 15), List.of("Plus [SwSh]"));
        RuntimeCombatantState second = combatant("second", new GridCoord(10, 5), List.of("Plus [SwSh]"));
        RuntimeCombatantState outside = combatant("outside", new GridCoord(16, 5), List.of("Plus [SwSh]"));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(30, 30, Set.of(), Map.of()),
                List.of(target, first, second, outside)
        );

        assertEquals(List.of("first", "second"),
                SpatialAbilityQuery.holdersInRadius(state, target.position(), "Plus [SwSh]", 10));
        assertEquals(10, SpatialAbilityQuery.chebyshevDistance(target.position(), first.position()));
        assertEquals(11, SpatialAbilityQuery.chebyshevDistance(target.position(), outside.position()));
    }

    private static ScenarioState scenarioState(String scenario, String reaction) {
        String ability = reaction.equals("minus") ? "Minus [SwSh]" : "Plus [SwSh]";
        LinkedHashMap<String, List<String>> abilities = new LinkedHashMap<>();
        abilities.put("actor", List.of());
        abilities.put("target", scenario.equals("plus_target_holder_excluded") ? List.of(ability) : List.of());
        abilities.put("holder", scenario.equals("plus_target_holder_excluded") ? List.of() : List.of(ability));
        abilities.put("holder2", scenario.equals("plus_first_holder_wins") ? List.of(ability) : List.of());

        String targetTeam = "ally";
        String actorTeam = scenario.equals("minus_external_enemy")
                || scenario.equals("minus_same_team_holder")
                || scenario.equals("minus_skip_guard")
                || scenario.equals("minus_positive_change") ? "enemy" : "ally";
        String holderTeam;
        if (scenario.equals("minus_same_team_holder")) holderTeam = "ally";
        else if (scenario.equals("plus_enemy_holder")) holderTeam = "enemy";
        else if (reaction.equals("minus")) holderTeam = "enemy";
        else holderTeam = "ally";

        List<RuntimeCombatantState> combatants = new ArrayList<>();
        combatants.add(combatant("actor", new GridCoord(2, 2), abilities.get("actor")));
        combatants.add(combatant("target", new GridCoord(10, 10), abilities.get("target")));
        combatants.add(combatant("holder", new GridCoord(15, 10), abilities.get("holder")));
        combatants.add(combatant("holder2", new GridCoord(16, 10), abilities.get("holder2")));

        Map<String, CombatantAffiliationState> affiliation = Map.of(
                "actor", CombatantAffiliationState.active(actorTeam),
                "target", CombatantAffiliationState.active(targetTeam),
                "holder", CombatantAffiliationState.active(holderTeam),
                "holder2", CombatantAffiliationState.active(holderTeam)
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(30, 30, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(), affiliation
        );
        return new ScenarioState(state);
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                20, 20, new ActionBudget(), null, null, 0,
                false, false, false, false,
                List.of(), List.of(), abilities
        );
    }

    private record ScenarioState(BattleRuntimeState state) {}
}
