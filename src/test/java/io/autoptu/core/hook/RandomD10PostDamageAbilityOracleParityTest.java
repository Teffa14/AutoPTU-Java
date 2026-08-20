package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRoundController;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatantAffiliationState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RandomD10PostDamageAbilityOracleParityTest {
    @Test
    void randomPostDamageBonusesMatchPinnedPythonAndRngPosition() throws Exception {
        Path pythonOracle = Path.of("python-oracle");
        if (!Files.isDirectory(pythonOracle)) return;

        Path output = Files.createTempFile("autoptu-random-post-damage-", ".tsv");
        try {
            Process process = new ProcessBuilder(
                    "python3",
                    "tools/python/export_random_post_damage_fixtures.py",
                    "--source-root", pythonOracle.toString(),
                    "--output", output.toString()
            ).inheritIO().start();
            assertEquals(0, process.waitFor(), "Python random post-damage exporter must succeed");

            List<String> lines = Files.readAllLines(output);
            for (int index = 1; index < lines.size(); index++) {
                if (lines.get(index).isBlank()) continue;
                assertCase(lines.get(index));
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static void assertCase(String line) {
        String[] c = line.split("\\t", -1);
        String name = c[0];
        String ability = c[1];
        String moveType = c[2];
        List<String> actorTypes = c[3].isBlank()
                ? List.of()
                : List.copyOf(Arrays.asList(c[3].split("\\|", -1)));
        long seed = Long.parseLong(c[4]);
        int round = Integer.parseInt(c[5]);
        String auraBreakMode = c[6];
        int expectedBonus = Integer.parseInt(c[7]);
        String expectedEvents = c[8];
        int expectedNextRoll = Integer.parseInt(c[9]);
        int expectedRemaining = Integer.parseInt(c[10]);

        RuntimeCombatantState actor = combatant("actor", List.of(ability), actorTypes);
        RuntimeCombatantState target = combatant("target", List.of(), List.of("Normal"));
        BattleRuntimeState state = state(actor, target);
        new BattleRoundController(state, round);

        if ("matching".equals(auraBreakMode)) {
            actor.temporaryEffects().add("aura_break_errata", Map.of(
                    "ability", ability,
                    "source_id", "breaker",
                    "expires_round", round
            ));
        } else if ("expired".equals(auraBreakMode)) {
            actor.temporaryEffects().add("aura_break_errata", Map.of(
                    "ability", ability,
                    "source_id", "breaker",
                    "expires_round", round - 1
            ));
        }

        MoveOption move = MoveOption.standard(
                "oracle-move",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(2, 6, 20, "Special", moveType)
        );
        PythonRandom rng = new PythonRandom(seed);
        PostDamageHookContext context = new PostDamageHookContext(
                state, "actor", "target", actor, target, move, move.requireCombatProfile(), rng);
        RandomD10PostDamageAbility.Rule rule = ability.equalsIgnoreCase("Adaptability [Errata]")
                ? RandomD10PostDamageAbility.Rule.adaptabilityErrata()
                : RandomD10PostDamageAbility.Rule.dampErrata();

        PostDamageHookResult result = RandomD10PostDamageAbility.resolve(context, rule);
        assertEquals(expectedBonus, result.flatDamageBonus(), name + " damage bonus");
        assertEquals(expectedEvents, eventSignatures(result), name + " ordered events");
        assertEquals(expectedNextRoll, rng.randIntInclusive(1, 10), name + " RNG position after hook");
        assertEquals(expectedRemaining, actor.temporaryEffects().count("aura_break_errata"), name + " Aura Break state");
    }

    private static String eventSignatures(PostDamageHookResult result) {
        ArrayList<String> signatures = new ArrayList<>();
        for (var event : result.events()) {
            if (!(event instanceof RuleEffectEvent ruleEvent)) continue;
            signatures.add(String.join(":",
                    ruleEvent.sourceName(),
                    ruleEvent.effect(),
                    Integer.toString((int) ruleEvent.amount()),
                    ruleEvent.actorId(),
                    ruleEvent.targetId()
            ));
        }
        return String.join("|", signatures);
    }

    private static BattleRuntimeState state(RuntimeCombatantState actor, RuntimeCombatantState target) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true)
                ),
                Map.of(), Map.of()
        );
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities, List<String> types) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(id.equals("actor") ? 1 : 2, 1), 4),
                20,
                20,
                new ActionBudget(),
                null, null, 0, false, false, false, false,
                types, List.of(), abilities
        );
    }
}
