package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.MoveSpecialSecondaryCombatStageResolution;
import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MoveSpecialSecondaryCombatStageApplicationTest {
    @Test
    void appliesUserMultiStatRequestsThroughCanonicalMutationService() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of());
        BattleRuntimeState state = state(actor, target);

        List<MoveSpecialSecondaryCombatStageResolution.StageRequest> requests =
                MoveSpecialSecondaryCombatStageResolution.resolve(
                        "Raises the user's Attack / Speed by +1 Combat Stage.", 1);
        MoveSpecialSecondaryCombatStageApplication.Result result =
                MoveSpecialSecondaryCombatStageApplication.apply(
                        state, "actor", "target", "Agility Fixture", requests);

        assertEquals(1, actor.combatStages().get(CombatStat.ATK));
        assertEquals(1, actor.combatStages().get(CombatStat.SPD));
        assertEquals(2, result.applications().size());
        assertEquals(List.of(), result.events());
    }

    @Test
    void targetDropPreservesAbilityPreventionAndEvents() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of("Clear Body"));
        BattleRuntimeState state = state(actor, target);

        List<MoveSpecialSecondaryCombatStageResolution.StageRequest> requests =
                MoveSpecialSecondaryCombatStageResolution.resolve(
                        "Lowers the target's Defense by -1 CS.", 1);
        MoveSpecialSecondaryCombatStageApplication.Result result =
                MoveSpecialSecondaryCombatStageApplication.apply(
                        state, "actor", "target", "Drop Fixture", requests);

        assertEquals(0, target.combatStages().get(CombatStat.DEF));
        assertEquals(1, result.applications().size());
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("Clear Body", event.sourceName());
    }

    @Test
    void targetRaisePreservesPostApplyReactions() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of("Simple"));
        BattleRuntimeState state = state(actor, target);

        List<MoveSpecialSecondaryCombatStageResolution.StageRequest> requests =
                MoveSpecialSecondaryCombatStageResolution.resolve(
                        "Raises the target's Attack by +1 Combat Stage.", 1);
        MoveSpecialSecondaryCombatStageApplication.apply(
                state, "actor", "target", "Raise Fixture", requests);

        assertEquals(2, target.combatStages().get(CombatStat.ATK));
    }

    @Test
    void accuracyUsesCanonicalSevenStageStateAndPostApplyReactions() {
        RuntimeCombatantState actor = combatant("actor", List.of("Simple"));
        RuntimeCombatantState target = combatant("target", List.of());
        BattleRuntimeState state = state(actor, target);
        List<MoveSpecialSecondaryCombatStageResolution.StageRequest> requests = List.of(
                new MoveSpecialSecondaryCombatStageResolution.StageRequest(
                        MoveSpecialSecondaryCombatStageResolution.TargetRole.USER, "accuracy", 1)
        );

        MoveSpecialSecondaryCombatStageApplication.Result result =
                MoveSpecialSecondaryCombatStageApplication.apply(
                        state, "actor", "target", "Accuracy Fixture", requests);

        assertEquals(2, actor.accuracyStage());
        assertEquals(2, actor.combatStages().get(CombatStageStat.ACCURACY));
        assertEquals(CombatStageStat.ACCURACY, result.applications().getFirst().stat());
        assertEquals(1, result.events().size());
        assertEquals("Simple", ((RuleEffectEvent) result.events().getFirst()).sourceName());
    }

    @Test
    void evasionDropUsesTheSameAbilityPreventionPipeline() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of("Clear Body"));
        BattleRuntimeState state = state(actor, target);
        List<MoveSpecialSecondaryCombatStageResolution.StageRequest> requests = List.of(
                new MoveSpecialSecondaryCombatStageResolution.StageRequest(
                        MoveSpecialSecondaryCombatStageResolution.TargetRole.TARGET, "evasion", -1)
        );

        MoveSpecialSecondaryCombatStageApplication.Result result =
                MoveSpecialSecondaryCombatStageApplication.apply(
                        state, "actor", "target", "Evasion Fixture", requests);

        assertEquals(0, target.combatStages().get(CombatStageStat.EVASION));
        assertEquals(CombatStageStat.EVASION, result.applications().getFirst().stat());
        assertEquals(1, result.events().size());
        assertEquals("Clear Body", ((RuleEffectEvent) result.events().getFirst()).sourceName());
    }

    @Test
    void invalidStatFailsBeforeEarlierSupportedMutation() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of());
        BattleRuntimeState state = state(actor, target);
        List<MoveSpecialSecondaryCombatStageResolution.StageRequest> requests = List.of(
                new MoveSpecialSecondaryCombatStageResolution.StageRequest(
                        MoveSpecialSecondaryCombatStageResolution.TargetRole.USER, "atk", 1),
                new MoveSpecialSecondaryCombatStageResolution.StageRequest(
                        MoveSpecialSecondaryCombatStageResolution.TargetRole.USER, "unknown", 1)
        );

        assertThrows(IllegalArgumentException.class, () ->
                MoveSpecialSecondaryCombatStageApplication.apply(
                        state, "actor", "target", "Mixed Fixture", requests));
        assertEquals(0, actor.combatStages().get(CombatStat.ATK));
        assertEquals(0, actor.accuracyStage());
    }

    private static BattleRuntimeState state(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(
                new MovementGrid(5, 5, Set.of(), Map.of()),
                List.of(combatants)
        );
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(id.equals("actor") ? 1 : 2, 1), 3),
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
