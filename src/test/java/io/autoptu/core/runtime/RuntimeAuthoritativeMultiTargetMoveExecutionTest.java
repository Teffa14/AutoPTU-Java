package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeMultiTargetMoveExecutionTest {
    @Test
    void resolvesOrderedAreaTargetsWithSharedRngAndSingleResourceSpend() {
        MoveOption burst = burstMove();
        RuntimeCombatantState actor = combatant("actor", 0, 2, 100);
        RuntimeCombatantState first = combatant("first", 3, 2, 100);
        RuntimeCombatantState second = combatant("second", 3, 3, 100);
        BattleRuntimeState state = state(actor, first, second, burst);
        MoveChoice choice = new MoveChoice(
                "actor", "burst", ChoiceTargetMode.TILE, "", new GridCoord(3, 2), ActionType.STANDARD);

        MultiTargetAppliedActionResult result = RuntimeMoveResolution.applyAreaUsingAuthoritativeCombatState(
                state,
                choice,
                "AI",
                new PythonRandom(73),
                legacyInput(),
                false,
                false
        );

        assertEquals(List.of("first", "second"), result.targetIds());
        assertEquals(2, result.events().size());
        MoveResolvedEvent firstEvent = assertInstanceOf(MoveResolvedEvent.class, result.events().get(0));
        MoveResolvedEvent secondEvent = assertInstanceOf(MoveResolvedEvent.class, result.events().get(1));
        assertEquals("first", firstEvent.targetId());
        assertEquals("second", secondEvent.targetId());
        assertTrue(firstEvent.hit());
        assertTrue(secondEvent.hit());
        assertTrue(first.hp() < 100);
        assertTrue(second.hp() < 100);
        assertEquals(100 - first.hp(), state.damageHistory().damageReceivedThisRound().get("first"));
        assertEquals(100 - second.hp(), state.damageHistory().damageReceivedThisRound().get("second"));
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses("burst"));
    }

    @Test
    void areaTargetPreservesForcedMovementPreventionTraceFromSharedDependencies() {
        MoveOption burst = pushBurstMove();
        RuntimeCombatantState actor = combatant("actor", 0, 2, 100);
        RuntimeCombatantState first = combatant("first", 3, 3, 100);
        BattleRuntimeState state = state(actor, first, null, burst);
        MoveChoice choice = new MoveChoice(
                "actor", burst.moveId(), ChoiceTargetMode.TILE, "", new GridCoord(3, 2), ActionType.STANDARD);
        GridCoord targetOrigin = first.position();

        MultiTargetAppliedActionResult result = RuntimeMoveResolution.applyAreaUsingAuthoritativeCombatState(
                state,
                choice,
                "AI",
                new PythonRandom(73),
                legacyInput(),
                false,
                false,
                dependenciesFor("first", insectoidWallclimberContent())
        );

        assertEquals(List.of("first"), result.targetIds());
        assertEquals(2, result.events().size());
        MoveResolvedEvent moveEvent = assertInstanceOf(MoveResolvedEvent.class, result.events().get(0));
        TrainerFeatureEvent prevention = assertInstanceOf(TrainerFeatureEvent.class, result.events().get(1));
        assertTrue(moveEvent.hit());
        assertTrue(moveEvent.damage() > 0);
        assertEquals("first", moveEvent.targetId());
        assertEquals("first", prevention.actorId());
        assertEquals("Insectoid Utility", prevention.feature());
        assertEquals("forced_movement_block", prevention.effect());
        assertEquals("trainer", prevention.trainer());
        assertEquals(moveEvent.targetHp(), prevention.targetHp());
        assertEquals(moveEvent.targetHp(), first.hp());
        assertEquals(targetOrigin, first.position());
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses(burst.moveId()));
    }

    @Test
    void telepathyEscapesADeclaredAreaBeforeDamageWithoutSpendingShift() {
        MoveOption burst = burstMove();
        RuntimeCombatantState actor = combatant("actor", 0, 2, 100);
        RuntimeCombatantState telepath = combatantWithAbilities("telepath", 3, 2, 100, List.of("Telepathy"));
        RuntimeCombatantState enemy = combatant("enemy", 3, 3, 100);
        LinkedHashMap<String, CombatantAffiliationState> affiliation = new LinkedHashMap<>();
        affiliation.put("actor", CombatantAffiliationState.active("alpha"));
        affiliation.put("telepath", CombatantAffiliationState.active("alpha"));
        affiliation.put("enemy", CombatantAffiliationState.active("beta"));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(actor, telepath, enemy),
                Map.of(),
                Map.of(),
                Map.of(),
                affiliation,
                Map.of("actor", List.of(burst))
        );
        MoveChoice choice = new MoveChoice(
                "actor", "burst", ChoiceTargetMode.TILE, "", new GridCoord(3, 2), ActionType.STANDARD);
        GridCoord telepathOrigin = telepath.position();

        MultiTargetAppliedActionResult result = RuntimeMoveResolution.applyAreaUsingAuthoritativeCombatState(
                state,
                choice,
                "AI",
                new PythonRandom(73),
                legacyInput(),
                false,
                false
        );

        assertEquals(List.of("telepath", "enemy"), result.targetIds());
        assertNotEquals(telepathOrigin, telepath.position());
        assertEquals(100, telepath.hp());
        assertTrue(telepath.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertFalse(state.damageHistory().damageReceivedThisRound().containsKey("telepath"));
        assertTrue(enemy.hp() < 100);
        assertEquals(100 - enemy.hp(), state.damageHistory().damageReceivedThisRound().get("enemy"));
        assertTrue(result.events().stream()
                .filter(RuleEffectEvent.class::isInstance)
                .map(RuleEffectEvent.class::cast)
                .anyMatch(event -> event.sourceName().equals("Telepathy")
                        && event.actorId().equals("telepath")
                        && event.effect().equals("shift")));
        MoveResolvedEvent telepathEvent = result.events().stream()
                .filter(MoveResolvedEvent.class::isInstance)
                .map(MoveResolvedEvent.class::cast)
                .filter(event -> event.targetId().equals("telepath"))
                .findFirst()
                .orElseThrow();
        assertFalse(telepathEvent.hit());
        assertEquals(0, telepathEvent.damage());
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses("burst"));
    }

    @Test
    void emptyAreaStillOwnsTheSingleDeclaredActionAndFrequencyUse() {
        MoveOption burst = burstMove();
        RuntimeCombatantState actor = combatant("actor", 0, 2, 100);
        BattleRuntimeState state = state(actor, null, null, burst);
        MoveChoice choice = new MoveChoice(
                "actor", "burst", ChoiceTargetMode.TILE, "", new GridCoord(3, 2), ActionType.STANDARD);

        MultiTargetAppliedActionResult result = RuntimeMoveResolution.applyAreaUsingAuthoritativeCombatState(
                state,
                choice,
                "AI",
                new PythonRandom(73),
                legacyInput(),
                false,
                false
        );

        assertEquals(List.of(), result.targetIds());
        assertEquals(List.of(), result.events());
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses("burst"));
    }

    private static BattleRuntimeState state(
            RuntimeCombatantState actor,
            RuntimeCombatantState first,
            RuntimeCombatantState second,
            MoveOption move
    ) {
        List<RuntimeCombatantState> combatants;
        if (first == null) {
            combatants = List.of(actor);
        } else if (second == null) {
            combatants = List.of(actor, first);
        } else {
            combatants = List.of(actor, first, second);
        }
        LinkedHashMap<String, CombatantAffiliationState> affiliation = new LinkedHashMap<>();
        affiliation.put("actor", CombatantAffiliationState.active("alpha"));
        if (first != null) affiliation.put("first", CombatantAffiliationState.active("beta"));
        if (second != null) affiliation.put("second", CombatantAffiliationState.active("beta"));
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of(),
                Map.of(),
                Map.of(),
                affiliation,
                Map.of("actor", List.of(move))
        );
    }

    private static MoveOption burstMove() {
        return new MoveOption(
                "burst",
                new MoveSpec("Ranged", "Ranged", 6, 6, "Burst", 1, "Burst 1"),
                ActionType.STANDARD,
                false,
                new MoveCombatProfile(null, 6, 20, "physical", "Normal"),
                "Scene x1"
        );
    }

    private static MoveOption pushBurstMove() {
        return new MoveOption(
                "push-burst",
                new MoveSpec(
                        "Ranged", "Ranged", 6, 6, "Burst", 1, "Burst 1",
                        List.of("push 2"), "Push each target 2 meters."
                ),
                ActionType.STANDARD,
                false,
                new MoveCombatProfile(null, 6, 20, "physical", "Normal"),
                "Scene x1"
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y, int hp) {
        return combatantWithAbilities(id, x, y, hp, List.of());
    }

    private static RuntimeCombatantState combatantWithAbilities(
            String id,
            int x,
            int y,
            int hp,
            List<String> abilities
    ) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, 20,
                        CombatStat.DEF, 10,
                        CombatStat.SPATK, 20,
                        CombatStat.SPDEF, 10,
                        CombatStat.SPD, 10
                ),
                Map.of(),
                Map.of(),
                Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 5),
                hp,
                hp,
                new ActionBudget(),
                stats,
                new EvasionProfile(stats, 0, 0, 0, false, false),
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

    private static BattleRuntimeDependencies dependenciesFor(String combatantId, CombatantRuleContent content) {
        return new BattleRuntimeDependencies(
                new CombatantRuleContentRegistry(Map.of(combatantId, content))
        );
    }

    private static CombatantRuleContent insectoidWallclimberContent() {
        return new CombatantRuleContent(
                List.of("Wallclimber"), null, "trainer", Map.of(),
                List.of("Insectoid Utility"), List.of()
        );
    }

    private static MoveResolutionInput legacyInput() {
        return new MoveResolutionInput(
                null, 99, 6, 20, false, false, false,
                1, 1, 1, false, 1.0, List.of()
        );
    }
}
