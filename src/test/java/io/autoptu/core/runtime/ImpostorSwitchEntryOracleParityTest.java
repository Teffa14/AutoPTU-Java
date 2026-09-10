package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.BuiltinLifecycleHooks;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookResult;
import io.autoptu.core.model.CombatStageStat;
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

final class ImpostorSwitchEntryOracleParityTest {
    @Test
    void entryHookMatchesPinnedPythonSwitchImpostorStateAndEvent() throws IOException {
        Path fixture = Path.of("build/oracle/impostor-switch-entry.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        List<String> entryAbilities = expected.get("EFFECTIVE_BEFORE").isBlank()
                ? List.of()
                : List.of(expected.get("EFFECTIVE_BEFORE").split(","));
        RuntimeCombatantState replacement = combatant("a-2", new GridCoord(2, 2), entryAbilities);
        RuntimeCombatantState target = combatant("b-1", new GridCoord(2, 3), List.of("Pressure"));
        target.combatStages().set(CombatStageStat.ATK, 2);
        target.combatStages().set(CombatStageStat.DEF, -1);

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(replacement, target),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "a-2", CombatantAffiliationState.active("players"),
                        "b-1", CombatantAffiliationState.active("foes")
                ),
                Map.of(), Map.of(), 42L
        );
        state.syncCurrentRoundFromLifecycle(1);

        LifecycleHookResult result = BuiltinLifecycleHooks.registry().resolve(
                LifecycleHookPoint.COMBATANT_ENTRY,
                new LifecycleHookContext(state, LifecycleHookPoint.COMBATANT_ENTRY, 1, 1, "a-2")
        );

        assertEquals(Integer.parseInt(expected.get("ATK_STAGE")), replacement.combatStages().get(CombatStageStat.ATK));
        assertEquals(Integer.parseInt(expected.get("DEF_STAGE")), replacement.combatStages().get(CombatStageStat.DEF));
        assertEquals(expected.get("EFFECTIVE_AFTER"), String.join(",", EffectiveAbilityResolver.resolve(replacement)));
        assertEquals(Integer.parseInt(expected.get("ENTRAINED_COUNT")), replacement.temporaryEffects().count(TransformationStateResolver.ENTRAINED_ABILITY));
        assertEquals(expected.get("ENTRAINED_ABILITY"), String.valueOf(replacement.temporaryEffects()
                .getAll(TransformationStateResolver.ENTRAINED_ABILITY).getFirst().payload().get("ability")));
        assertEquals(Integer.parseInt(expected.get("JOINED_ROUND_COUNT")), roundEffectCount(replacement, ImpostorEffectExecutor.JOINED_ROUND, 1));
        assertEquals(Integer.parseInt(expected.get("USED_COUNT")), roundEffectCount(replacement, ImpostorEffectExecutor.USED, 1));

        List<AbilityEvent> events = result.events().stream()
                .filter(AbilityEvent.class::isInstance)
                .map(AbilityEvent.class::cast)
                .filter(event -> "a-2".equals(event.actorId())
                        && "Impostor".equals(event.ability())
                        && "transform".equals(event.effect()))
                .toList();
        assertEquals(Integer.parseInt(expected.get("EVENT_COUNT")), events.size());
        if (!events.isEmpty()) {
            assertEquals(expected.get("EVENT_TARGET"), events.getFirst().target());
            assertEquals(expected.get("EVENT_PHASE"), String.valueOf(events.getFirst().details().getOrDefault("phase", "")));
        }
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, List<String> abilities) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.SPD, 10), Map.of(), Map.of(), Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
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

    private static int roundEffectCount(RuntimeCombatantState combatant, String effectName, int round) {
        return (int) combatant.temporaryEffects().getAll(effectName).stream()
                .filter(entry -> entry.payload().get("round") instanceof Number number && number.intValue() == round)
                .count();
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
