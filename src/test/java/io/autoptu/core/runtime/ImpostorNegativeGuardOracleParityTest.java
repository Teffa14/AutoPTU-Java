package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImpostorNegativeGuardOracleParityTest {
    @Test
    void productionRolloverMatchesPinnedPythonNegativeGuards() throws IOException {
        Path fixture = Path.of("build/oracle/impostor-negative-guards.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        RuntimeCombatantState noTargetHolder = combatant("holder", 20, List.of("Impostor"));
        BattleRuntimeState noTarget = state(
                List.of(noTargetHolder),
                Map.of("holder", CombatantAffiliationState.active("players")),
                42L
        );
        InitiativeTurnAdvanceResult noTargetResult = new BattleRoundController(noTarget, 0)
                .advanceInitiativeTurnWithRollover();
        assertEquals(Integer.parseInt(expected.get("NO_TARGET_EVENT_COUNT")), impostorEventCount(noTargetResult.events(), "holder"));
        assertEquals(Integer.parseInt(expected.get("NO_TARGET_USED_COUNT")), roundEffectCount(noTargetHolder, ImpostorEffectExecutor.USED, 1));
        assertEquals(Integer.parseInt(expected.get("NO_TARGET_ENTRAINED_COUNT")), noTargetHolder.temporaryEffects().getAll(TransformationStateResolver.ENTRAINED_ABILITY).size());

        RuntimeCombatantState noAbilityHolder = combatant("holder", 20, List.of("Impostor"));
        RuntimeCombatantState noAbilityTarget = combatant("target", 10, List.of());
        BattleRuntimeState noAbility = state(
                List.of(noAbilityHolder, noAbilityTarget),
                Map.of(
                        "holder", CombatantAffiliationState.active("players"),
                        "target", CombatantAffiliationState.active("foes")
                ),
                42L
        );
        InitiativeTurnAdvanceResult noAbilityResult = new BattleRoundController(noAbility, 0)
                .advanceInitiativeTurnWithRollover();
        assertEquals(Integer.parseInt(expected.get("NO_ABILITY_EVENT_COUNT")), impostorEventCount(noAbilityResult.events(), "holder"));
        assertEquals(Integer.parseInt(expected.get("NO_ABILITY_USED_COUNT")), roundEffectCount(noAbilityHolder, ImpostorEffectExecutor.USED, 1));
        assertEquals(Integer.parseInt(expected.get("NO_ABILITY_ENTRAINED_COUNT")), noAbilityHolder.temporaryEffects().getAll(TransformationStateResolver.ENTRAINED_ABILITY).size());

        RuntimeCombatantState transformedHolder = combatant("holder", 20, List.of("Impostor"));
        transformedHolder.temporaryEffects().add(
                TransformationStateResolver.ENTRAINED_ABILITY,
                Map.of("ability", "Pressure", "source", "Transform")
        );
        RuntimeCombatantState transformedTarget = combatant("target", 10, List.of("Levitate"));
        BattleRuntimeState transformed = state(
                List.of(transformedHolder, transformedTarget),
                Map.of(
                        "holder", CombatantAffiliationState.active("players"),
                        "target", CombatantAffiliationState.active("foes")
                ),
                42L
        );
        InitiativeTurnAdvanceResult transformedResult = new BattleRoundController(transformed, 0)
                .advanceInitiativeTurnWithRollover();
        assertEquals(Integer.parseInt(expected.get("TRANSFORMED_EVENT_COUNT")), impostorEventCount(transformedResult.events(), "holder"));
        assertEquals(Integer.parseInt(expected.get("TRANSFORMED_USED_COUNT")), roundEffectCount(transformedHolder, ImpostorEffectExecutor.USED, 1));
        assertEquals(expected.get("TRANSFORMED_EFFECTIVE_ABILITIES"), String.join(",", EffectiveAbilityResolver.resolve(transformedHolder)));

        RuntimeCombatantState repeatHolder = combatant("holder", 20, List.of("Impostor"));
        RuntimeCombatantState repeatTarget = combatant("target", 10, List.of("Levitate", "Pressure", "Blaze"));
        BattleRuntimeState repeated = state(
                List.of(repeatHolder, repeatTarget),
                Map.of(
                        "holder", CombatantAffiliationState.active("players"),
                        "target", CombatantAffiliationState.active("foes")
                ),
                42L
        );
        InitiativeTurnAdvanceResult repeatResult = new BattleRoundController(repeated, 0)
                .advanceInitiativeTurnWithRollover();
        int beforeEvents = impostorEventCount(repeatResult.events(), "holder");
        var rng = repeated.delayedHitStateFromRuntime().randomFromRuntime();
        double expectedNext = rng.random();

        List<BattleEvent> secondEvents = ImpostorEffectExecutor.apply(repeated, "holder");
        double actualNext = rng.random();

        assertEquals(Integer.parseInt(expected.get("REPEAT_EVENT_DELTA")), impostorEventCount(secondEvents, "holder"));
        assertEquals(1, beforeEvents);
        assertEquals(Integer.parseInt(expected.get("REPEAT_USED_COUNT")), roundEffectCount(repeatHolder, ImpostorEffectExecutor.USED, 1));
        assertEquals(Integer.parseInt(expected.get("REPEAT_ENTRAINED_COUNT")), repeatHolder.temporaryEffects().getAll(TransformationStateResolver.ENTRAINED_ABILITY).size());
        assertEquals("1".equals(expected.get("REPEAT_RNG_UNCHANGED")), expectedNext != actualNext);
    }

    private static BattleRuntimeState state(
            List<RuntimeCombatantState> combatants,
            Map<String, CombatantAffiliationState> affiliations,
            long seed
    ) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(), affiliations,
                Map.of(), Map.of(), seed
        );
    }

    private static RuntimeCombatantState combatant(String id, int speed, List<String> abilities) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.SPD, speed), Map.of(), Map.of(), Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(2, 2), 4),
                20,
                20,
                new ActionBudget(),
                stats,
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

    private static int impostorEventCount(List<BattleEvent> events, String actorId) {
        return (int) events.stream()
                .filter(AbilityEvent.class::isInstance)
                .map(AbilityEvent.class::cast)
                .filter(event -> actorId.equals(event.actorId()) && "Impostor".equals(event.ability()))
                .count();
    }

    private static int roundEffectCount(RuntimeCombatantState combatant, String effectName, int round) {
        return (int) combatant.temporaryEffects().getAll(effectName).stream()
                .filter(entry -> entry.payload().get("round") instanceof Number number && number.intValue() == round)
                .count();
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
