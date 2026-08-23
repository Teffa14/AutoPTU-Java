package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatStageMutationResult;
import io.autoptu.core.runtime.CombatStageMutationService;
import io.autoptu.core.runtime.CombatantAffiliationState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FlowerVeilCombatStagePreventionOracleParityTest {
    @Test
    void oracleContractIsFrozen() throws IOException {
        String oracle = System.getenv("AUTOPTU_FLOWER_VEIL_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Flower Veil oracle fixture not configured");
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Path.of(oracle));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            values.put(fields[0], fields[1]);
        }
        assertEquals("1", values.get("target_requires_grass"));
        assertEquals("1", values.get("skips_fainted_or_inactive_holders"));
        assertEquals("1", values.get("matches_base_registration"));
        assertEquals("10", values.get("base_radius"));
        assertEquals("5", values.get("errata_radius"));
        assertEquals("1", values.get("no_team_filter"));
        assertEquals("1", values.get("external_drop_only"));
        assertEquals("1", values.get("emits_combat_stage_block"));
        assertEquals("1", values.get("generic_suppression_not_guarding_flower_veil"));
    }

    @Test
    void baseFlowerVeilBlocksExternalGrassDropAtRadiusTen() {
        BattleRuntimeState state = battle("Flower Veil", new GridCoord(10, 0), true, 20, List.of("Grass"));
        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("attacker", "target", "Growl", CombatStat.ATK, -1, "fixture");

        assertEquals(0, result.baseAppliedDelta());
        assertEquals(0, result.finalStage());
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("holder", event.actorId());
        assertEquals("target", event.targetId());
        assertEquals("Flower Veil", event.sourceName());
        assertEquals("combat_stage_block", event.effect());
    }

    @Test
    void errataUsesRadiusFiveAndBaseUsesRadiusTen() {
        BattleRuntimeState errataOutside = battle("Flower Veil [Errata]", new GridCoord(6, 0), true, 20, List.of("Grass"));
        assertEquals(-1, CombatStageMutationService.authoritative(errataOutside)
                .apply("attacker", "target", "Growl", CombatStat.ATK, -1, "fixture").finalStage());

        BattleRuntimeState errataEdge = battle("Flower Veil [Errata]", new GridCoord(5, 0), true, 20, List.of("Grass"));
        CombatStageMutationResult blocked = CombatStageMutationService.authoritative(errataEdge)
                .apply("attacker", "target", "Growl", CombatStat.ATK, -1, "fixture");
        assertEquals(0, blocked.finalStage());
        assertEquals("Flower Veil [Errata]", ((RuleEffectEvent) blocked.events().getFirst()).sourceName());
    }

    @Test
    void nonGrassInactiveFaintedAndSelfDropsAreNotBlocked() {
        BattleRuntimeState nonGrass = battle("Flower Veil", new GridCoord(1, 0), true, 20, List.of("Water"));
        assertEquals(-1, CombatStageMutationService.authoritative(nonGrass)
                .apply("attacker", "target", "Growl", CombatStat.ATK, -1, "fixture").finalStage());

        BattleRuntimeState inactive = battle("Flower Veil", new GridCoord(1, 0), false, 20, List.of("Grass"));
        assertEquals(-1, CombatStageMutationService.authoritative(inactive)
                .apply("attacker", "target", "Growl", CombatStat.ATK, -1, "fixture").finalStage());

        BattleRuntimeState fainted = battle("Flower Veil", new GridCoord(1, 0), true, 0, List.of("Grass"));
        assertEquals(-1, CombatStageMutationService.authoritative(fainted)
                .apply("attacker", "target", "Growl", CombatStat.ATK, -1, "fixture").finalStage());

        BattleRuntimeState selfDrop = battle("Flower Veil", new GridCoord(1, 0), true, 20, List.of("Grass"));
        assertEquals(-1, CombatStageMutationService.authoritative(selfDrop)
                .apply("target", "target", "Self Drop", CombatStat.ATK, -1, "fixture").finalStage());
    }

    @Test
    void firstEligibleHolderWinsWithoutTeamFiltering() {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(20, 20), 20, List.of(), List.of());
        RuntimeCombatantState target = combatant("target", new GridCoord(0, 0), 20, List.of("Grass"), List.of());
        RuntimeCombatantState first = combatant("first", new GridCoord(3, 0), 20, List.of(), List.of("Flower Veil"));
        RuntimeCombatantState second = combatant("second", new GridCoord(2, 0), 20, List.of(), List.of("Flower Veil [Errata]"));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(30, 30, Set.of(), Map.of()),
                List.of(attacker, target, first, second), Map.of(), Map.of(), Map.of(),
                Map.of(
                        "attacker", CombatantAffiliationState.active("red"),
                        "target", CombatantAffiliationState.active("blue"),
                        "first", CombatantAffiliationState.active("red"),
                        "second", CombatantAffiliationState.active("blue")
                )
        );
        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("attacker", "target", "Growl", CombatStat.DEF, -1, "fixture");
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("first", event.actorId());
        assertTrue(result.events().size() == 1);
    }

    private static BattleRuntimeState battle(String holderAbility, GridCoord holderPos, boolean holderActive, int holderHp, List<String> targetTypes) {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(20, 20), 20, List.of(), List.of());
        RuntimeCombatantState target = combatant("target", new GridCoord(0, 0), 20, targetTypes, List.of());
        RuntimeCombatantState holder = combatant("holder", holderPos, holderHp, List.of(), List.of(holderAbility));
        return new BattleRuntimeState(
                new MovementGrid(30, 30, Set.of(), Map.of()),
                List.of(attacker, target, holder), Map.of(), Map.of(), Map.of(),
                Map.of(
                        "attacker", CombatantAffiliationState.active("red"),
                        "target", CombatantAffiliationState.active("blue"),
                        "holder", holderActive ? CombatantAffiliationState.active("red") : new CombatantAffiliationState("red", false)
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, int hp, List<String> types, List<String> abilities) {
        return new RuntimeCombatantState(
                id, MovementProfile.walking(position, 3), hp, 20, new ActionBudget(),
                null, null, 0, false, false, false, false,
                types, List.of(), abilities
        );
    }
}
