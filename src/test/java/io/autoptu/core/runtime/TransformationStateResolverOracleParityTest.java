package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.GridCoord;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TransformationStateResolverOracleParityTest {
    @Test
    void copiesPinnedPythonStageSnapshotAndCopiedAbility() throws IOException {
        Path fixture = Path.of("build/oracle/transformation-state.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        RuntimeCombatantState actor = combatant("holder", new GridCoord(2, 2));
        RuntimeCombatantState target = combatant("target", new GridCoord(2, 3));
        setStages(actor, -4, 4, -4, 4, -4, -3, 3);
        setStages(target, 2, -1, 3, -2, 1, 2, -1);

        TransformationStateResolver.Result result = TransformationStateResolver.apply(
                actor,
                target,
                "Levitate",
                "impostor"
        );

        assertEquals("1".equals(expected.get("COPIED_STAGES")), result.copiedStages());
        assertEquals(expected.get("ACTOR_STAGES"), stages(actor));
        List<TemporaryEffectEntry> entrained = actor.temporaryEffects().getAll(
                TransformationStateResolver.ENTRAINED_ABILITY
        );
        assertEquals(Integer.parseInt(expected.get("ENTRAINED_COUNT")), entrained.size());
        assertEquals(expected.get("ABILITY_ASSIGNED"), entrained.getFirst().payload().get("ability"));
        assertEquals(expected.get("ABILITY_SOURCE"), entrained.getFirst().payload().get("source"));
        assertEquals(expected.get("ABILITY_ASSIGNED"), result.abilityAssigned());
    }

    @Test
    void replacesEntireEntrainedAbilityFamily() {
        RuntimeCombatantState actor = combatant("holder", new GridCoord(2, 2));
        RuntimeCombatantState target = combatant("target", new GridCoord(2, 3));
        actor.temporaryEffects().add(
                TransformationStateResolver.ENTRAINED_ABILITY,
                Map.of("ability", "Overgrow", "source", "fixture-old")
        );
        actor.temporaryEffects().add(
                TransformationStateResolver.ENTRAINED_ABILITY,
                Map.of("ability", "Blaze", "source", "fixture-old")
        );

        TransformationStateResolver.apply(actor, target, "Levitate", "impostor");

        List<TemporaryEffectEntry> entrained = actor.temporaryEffects().getAll(
                TransformationStateResolver.ENTRAINED_ABILITY
        );
        assertEquals(1, entrained.size());
        assertEquals("Levitate", entrained.getFirst().payload().get("ability"));
        assertEquals("impostor", entrained.getFirst().payload().get("source"));
    }

    @Test
    void leavesActorStagesUntouchedWhenTargetHasNoModifiedStage() {
        RuntimeCombatantState actor = combatant("holder", new GridCoord(2, 2));
        RuntimeCombatantState target = combatant("target", new GridCoord(2, 3));
        setStages(actor, -2, 1, 0, 0, 0, 0, 0);

        TransformationStateResolver.Result result = TransformationStateResolver.apply(
                actor,
                target,
                null,
                "transform"
        );

        assertEquals(false, result.copiedStages());
        assertEquals(-2, actor.combatStages().get(CombatStageStat.ATK));
        assertEquals(1, actor.combatStages().get(CombatStageStat.DEF));
        assertEquals(1, actor.temporaryEffects().count(TransformationStateResolver.ENTRAINED_ABILITY));
        assertEquals(null, actor.temporaryEffects()
                .getAll(TransformationStateResolver.ENTRAINED_ABILITY)
                .getFirst().payload().get("ability"));
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

    private static void setStages(
            RuntimeCombatantState combatant,
            int atk,
            int def,
            int spatk,
            int spdef,
            int spd,
            int accuracy,
            int evasion
    ) {
        combatant.combatStages().set(CombatStageStat.ATK, atk);
        combatant.combatStages().set(CombatStageStat.DEF, def);
        combatant.combatStages().set(CombatStageStat.SPATK, spatk);
        combatant.combatStages().set(CombatStageStat.SPDEF, spdef);
        combatant.combatStages().set(CombatStageStat.SPD, spd);
        combatant.combatStages().set(CombatStageStat.ACCURACY, accuracy);
        combatant.combatStages().set(CombatStageStat.EVASION, evasion);
    }

    private static String stages(RuntimeCombatantState combatant) {
        return String.join(",",
                "atk:" + combatant.combatStages().get(CombatStageStat.ATK),
                "def:" + combatant.combatStages().get(CombatStageStat.DEF),
                "spatk:" + combatant.combatStages().get(CombatStageStat.SPATK),
                "spdef:" + combatant.combatStages().get(CombatStageStat.SPDEF),
                "spd:" + combatant.combatStages().get(CombatStageStat.SPD),
                "accuracy:" + combatant.combatStages().get(CombatStageStat.ACCURACY),
                "evasion:" + combatant.combatStages().get(CombatStageStat.EVASION)
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
