package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MirrorArmorCombatStageOracleParityTest {
    @Test
    void oracleContractIsFrozen() throws IOException {
        String oracle = System.getenv("AUTOPTU_MIRROR_ARMOR_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Mirror Armor fixture not configured");
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Path.of(oracle));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            values.put(fields[0], fields[1]);
        }
        for (String key : List.of(
                "negative_only", "external_only", "suppression_guarded", "mirror_skip_guarded",
                "emits_reflect", "reenters_combat_stage", "suppresses_recursive_mirror",
                "preserves_stat", "preserves_delta", "reflect_event_before_reentry",
                "event_actor_is_target", "event_target_is_attacker", "blocks_original_after_reflection")) {
            assertEquals("1", values.get(key), key);
        }
    }

    @Test
    void externalDropIsReflectedAndOriginalTargetDoesNotLoseStage() {
        BattleRuntimeState state = battle(List.of(), List.of("Mirror Armor"));
        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("attacker", "target", "Growl", CombatStat.ATK, -1, "fixture");

        assertEquals(0, state.requireCombatant("target").combatStages().get(CombatStat.ATK));
        assertEquals(-1, state.requireCombatant("attacker").combatStages().get(CombatStat.ATK));
        assertEquals(0, result.baseAppliedDelta());
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("Mirror Armor", event.sourceName());
        assertEquals("target", event.actorId());
        assertEquals("attacker", event.targetId());
        assertEquals("reflect", event.effect());
        assertEquals(-1, event.amount());
    }

    @Test
    void accuracyDropUsesTheSameRecursiveReflectionPipeline() {
        BattleRuntimeState state = battle(List.of(), List.of("Mirror Armor"));
        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("attacker", "target", "Accuracy Drop", CombatStageStat.ACCURACY, -1, "fixture");

        assertEquals(0, state.requireCombatant("target").accuracyStage());
        assertEquals(-1, state.requireCombatant("attacker").accuracyStage());
        assertEquals(0, result.baseAppliedDelta());
        assertEquals(1, result.events().stream()
                .filter(RuleEffectEvent.class::isInstance)
                .map(RuleEffectEvent.class::cast)
                .filter(event -> "Mirror Armor".equals(event.sourceName()) && "reflect".equals(event.effect()))
                .count());
    }

    @Test
    void recursiveReflectionIsSuppressedEvenWhenBothCombatantsHaveMirrorArmor() {
        BattleRuntimeState state = battle(List.of("Mirror Armor"), List.of("Mirror Armor"));
        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("attacker", "target", "Growl", CombatStat.DEF, -2, "fixture");

        assertEquals(0, state.requireCombatant("target").combatStages().get(CombatStat.DEF));
        assertEquals(-2, state.requireCombatant("attacker").combatStages().get(CombatStat.DEF));
        assertEquals(1, result.events().stream()
                .filter(RuleEffectEvent.class::isInstance)
                .map(RuleEffectEvent.class::cast)
                .filter(event -> "Mirror Armor".equals(event.sourceName()) && "reflect".equals(event.effect()))
                .count());
    }

    @Test
    void selfDropsPositiveChangesAndSuppressedAbilitiesDoNotReflect() {
        BattleRuntimeState self = battle(List.of(), List.of("Mirror Armor"));
        CombatStageMutationService.authoritative(self)
                .apply("target", "target", "Self Drop", CombatStat.SPATK, -1, "fixture");
        assertEquals(-1, self.requireCombatant("target").combatStages().get(CombatStat.SPATK));

        BattleRuntimeState positive = battle(List.of(), List.of("Mirror Armor"));
        CombatStageMutationService.authoritative(positive)
                .apply("attacker", "target", "Boost", CombatStat.SPATK, 1, "fixture");
        assertEquals(1, positive.requireCombatant("target").combatStages().get(CombatStat.SPATK));
        assertEquals(0, positive.requireCombatant("attacker").combatStages().get(CombatStat.SPATK));

        BattleRuntimeState suppressed = battle(List.of(), List.of("Mirror Armor"));
        suppressed.requireCombatant("target").setAbilitiesSuppressedFromRuntime(true);
        CombatStageMutationService.authoritative(suppressed)
                .apply("attacker", "target", "Growl", CombatStat.SPDEF, -1, "fixture");
        assertEquals(-1, suppressed.requireCombatant("target").combatStages().get(CombatStat.SPDEF));
        assertEquals(0, suppressed.requireCombatant("attacker").combatStages().get(CombatStat.SPDEF));
    }

    private static BattleRuntimeState battle(List<String> attackerAbilities, List<String> targetAbilities) {
        RuntimeCombatantState attacker = combatant("attacker", 1, attackerAbilities);
        RuntimeCombatantState target = combatant("target", 2, targetAbilities);
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(attacker, target), Map.of(), Map.of(), Map.of(),
                Map.of(
                        "attacker", CombatantAffiliationState.active("red"),
                        "target", CombatantAffiliationState.active("blue")
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, 1), 3),
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
                List.of(),
                List.of(),
                abilities
        );
    }
}
