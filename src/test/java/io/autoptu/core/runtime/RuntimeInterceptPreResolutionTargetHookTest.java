package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.PreResolutionTargetContext;
import io.autoptu.core.hook.PreResolutionTargetResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInterceptPreResolutionTargetHookTest {
    @Test
    void authoritativeDiscoveryBecomesGenericPreTargetReplacement() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 0, 6);
        RuntimeCombatantState target = combatant("target", 3, 1, 6);
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1, 6);
        interceptor.temporaryEffects().add("intercept_ready", Map.of(
                "ally", "target", "intercept_kind", "ranged", "source", "Intercept"
        ));
        interceptor.temporaryEffects().add("coaching_intercept");
        BattleRuntimeState state = state(List.of(attacker, target, interceptor), 991L);
        Map<String, CombatantRuleContent> content = alliedContent("target", "interceptor");

        RuntimeInterceptPreResolutionTargetHook hook = new RuntimeInterceptPreResolutionTargetHook(
                (context, currentTargetId) -> new RuntimeInterceptPreResolutionTargetHook.Plan("ranged", content)
        );

        PreResolutionTargetResult result = hook.resolve(
                new PreResolutionTargetContext(state, "attacker", "Ember", "target", target.position()),
                PreResolutionTargetResult.initial("target")
        );

        assertEquals("interceptor", result.targetId());
        assertEquals(new GridCoord(1, 0), interceptor.position());
        assertEquals(new GridCoord(3, 1), target.position());
        assertEquals(1, result.events().size());
        RuleEffectEvent event = assertInstanceOf(RuleEffectEvent.class, result.events().get(0));
        assertEquals("reaction", event.sourceKind());
        assertEquals("Intercept", event.sourceName());
        assertEquals("interceptor", event.actorId());
        assertEquals("target", event.targetId());
        assertEquals("Ember", event.moveId());
        assertEquals("target_replaced", event.effect());
    }

    @Test
    void unreachableDiscoveredCandidateLeavesCurrentTargetPositionAndResource() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 0, 6);
        RuntimeCombatantState target = combatant("target", 3, 1, 6);
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1, 0);
        interceptor.temporaryEffects().add("intercept_ready", Map.of(
                "ally", "target", "intercept_kind", "ranged", "source", "Intercept"
        ));
        BattleRuntimeState state = state(List.of(attacker, target, interceptor), 992L);
        Map<String, CombatantRuleContent> content = alliedContent("target", "interceptor");

        RuntimeInterceptPreResolutionTargetHook hook = new RuntimeInterceptPreResolutionTargetHook(
                (context, currentTargetId) -> new RuntimeInterceptPreResolutionTargetHook.Plan("ranged", content)
        );

        PreResolutionTargetResult result = hook.resolve(
                new PreResolutionTargetContext(state, "attacker", "Ember", "target", target.position()),
                PreResolutionTargetResult.initial("target")
        );

        assertEquals("target", result.targetId());
        assertEquals(List.of(), result.events());
        assertEquals(new GridCoord(1, 1), interceptor.position());
        assertTrue(interceptor.temporaryEffects().has("intercept_ready"));
    }

    @Test
    void activeNoInterceptIsAppliedByInternalPlannerBeforeSpatialSequence() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 0, 6);
        RuntimeCombatantState target = combatant("target", 3, 0, 6);
        RuntimeCombatantState interceptor = combatant("interceptor", 2, 1, 6);
        attacker.temporaryEffects().add("no_intercept", Map.of());
        interceptor.temporaryEffects().add("intercept_ready", Map.of(
                "ally", "target", "intercept_kind", "melee", "source", "Intercept"
        ));
        BattleRuntimeState state = state(List.of(attacker, target, interceptor), 993L);

        RuntimeInterceptPreResolutionTargetHook hook = new RuntimeInterceptPreResolutionTargetHook(
                (context, currentTargetId) -> new RuntimeInterceptPreResolutionTargetHook.Plan(
                        "melee", alliedContent("target", "interceptor")
                )
        );

        PreResolutionTargetResult result = hook.resolve(
                new PreResolutionTargetContext(state, "attacker", "Tackle", "target", target.position()),
                PreResolutionTargetResult.initial("target")
        );

        assertEquals("target", result.targetId());
        assertEquals(List.of(), result.events());
        assertTrue(interceptor.temporaryEffects().has("intercept_ready"));
    }

    @Test
    void adaptersCannotConstructHookOrInjectPreparedAttemptsOrAttackLinePublicly() {
        assertFalse(Modifier.isPublic(RuntimeInterceptPreResolutionTargetHook.class.getModifiers()));
        assertFalse(Modifier.isPublic(RuntimeInterceptPreResolutionTargetHook.AttemptPlanner.class.getModifiers()));
        assertTrue(Arrays.stream(RuntimeInterceptPreResolutionTargetHook.Plan.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(GridCoord.class)));
        assertTrue(Arrays.stream(RuntimeInterceptPreResolutionTargetHook.Plan.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(List.class)));
        assertEquals(2, RuntimeInterceptPreResolutionTargetHook.Plan.class.getRecordComponents().length);
    }

    private static RuntimeCombatantState combatant(String id, int x, int y, int overland) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), overland),
                20,
                20,
                new ActionBudget()
        );
    }

    private static Map<String, CombatantRuleContent> alliedContent(String targetId, String interceptorId) {
        return Map.of(
                targetId, new CombatantRuleContent(List.of(), 5, "trainer-a"),
                interceptorId, new CombatantRuleContent(List.of(), 5, "trainer-a")
        );
    }

    private static BattleRuntimeState state(List<RuntimeCombatantState> combatants, long seed) {
        Map<String, CombatantAffiliationState> affiliations = new java.util.LinkedHashMap<>();
        for (RuntimeCombatantState combatant : combatants) {
            affiliations.put(
                    combatant.combatantId(),
                    CombatantAffiliationState.active(combatant.combatantId().equals("attacker") ? "red" : "blue")
            );
        }
        return new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                combatants,
                Map.of(),
                Map.of(),
                Map.of(),
                affiliations,
                seed
        );
    }
}
