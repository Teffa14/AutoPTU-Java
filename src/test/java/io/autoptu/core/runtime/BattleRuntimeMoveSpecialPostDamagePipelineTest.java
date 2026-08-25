package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.MoveSpecialPhase;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.hook.PreDamageReactionHookRegistry;
import io.autoptu.core.hook.PreDamageReactionResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeMoveSpecialPostDamagePipelineTest {
    @Test
    void postDamageSpecialObservesCommittedStateAndSharedReactionAdjustedResult() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 100);
        RuntimeCombatantState target = combatant("target", new GridCoord(1, 2), 100);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true)
                )
        );

        AtomicBoolean postObservedCommittedState = new AtomicBoolean(false);
        AtomicBoolean globalObservedSpecificMutation = new AtomicBoolean(false);
        MoveSpecialHookRegistry moveSpecials = MoveSpecialHookRegistry.builder()
                .registerGlobal("pre-seed", MoveSpecialPhase.PRE_DAMAGE, context -> {
                    context.result().put("damage", 8);
                    context.result().put("pre_marker", "survives");
                    return List.of(new RuleEffectEvent(
                            "move_special", "PRE Special", "actor", "target",
                            "strike", "seed_result", 8.0d, 100
                    ));
                })
                .registerMove("post-specific", MoveSpecialPhase.POST_DAMAGE, List.of("strike"), context -> {
                    assertEquals(95, context.defender().hp());
                    assertEquals(5, state.damageHistory().damageReceivedThisRound().get("target").intValue());
                    assertEquals(5, context.damageDealt());
                    assertEquals(5, ((Number) context.result().get("damage")).intValue());
                    assertEquals("survives", context.result().get("pre_marker"));
                    postObservedCommittedState.set(true);
                    context.result().put("damage", 999);
                    context.result().put("post_marker", "specific");
                    return List.of(new RuleEffectEvent(
                            "move_special", "POST Specific", "actor", "target",
                            "strike", "observe_applied", context.damageDealt(), context.defender().hp()
                    ));
                })
                .registerGlobal("post-global", MoveSpecialPhase.POST_DAMAGE, context -> {
                    globalObservedSpecificMutation.set(
                            ((Number) context.result().get("damage")).intValue() == 999
                                    && "specific".equals(context.result().get("post_marker"))
                    );
                    return List.of(new RuleEffectEvent(
                            "move_special", "POST Global", "actor", "target",
                            "strike", "observe_shared_result", context.damageDealt(), context.defender().hp()
                    ));
                })
                .build();

        PreDamageReactionHookRegistry reactions = PreDamageReactionHookRegistry.builder()
                .register("reduce-special", HookSource.SYSTEM, 10, (context, current) -> new PreDamageReactionResult(
                        current.hit(), 5, current.typeMultiplier(),
                        List.of(new RuleEffectEvent(
                                "system", "Defender Reaction", "target", "actor",
                                "strike", "reduce_damage", 5.0d, 100
                        ))
                ))
                .build();

        MoveCombatProfile profile = new MoveCombatProfile(null, 6, 20, "physical", "Normal");
        MoveOption move = new MoveOption(
                "strike",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                ActionType.STANDARD,
                true,
                profile,
                "Scene x1"
        );
        MoveChoice choice = new MoveChoice(
                "actor", "strike", ChoiceTargetMode.COMBATANT, "target",
                new GridCoord(1, 2), ActionType.STANDARD
        );

        AppliedActionResult result = BattleRuntime.applyAuthoritativeMove(
                state,
                choice,
                move,
                "Medium",
                "Medium",
                Set.of(),
                "AI",
                new PythonRandom(41),
                new MoveResolutionInput(
                        null, 0, 0, 20, false, false, false,
                        6, 18, 10, false, 1.0, List.of()
                ),
                List.of(),
                moveSpecials,
                reactions,
                PostDamageHookRegistry.builder().build(),
                profile
        );

        assertTrue(postObservedCommittedState.get());
        assertTrue(globalObservedSpecificMutation.get());
        assertEquals(5, result.events().size());
        assertEquals("PRE Special", assertInstanceOf(RuleEffectEvent.class, result.events().get(0)).sourceName());
        assertEquals("Defender Reaction", assertInstanceOf(RuleEffectEvent.class, result.events().get(1)).sourceName());
        assertEquals("POST Specific", assertInstanceOf(RuleEffectEvent.class, result.events().get(2)).sourceName());
        assertEquals("POST Global", assertInstanceOf(RuleEffectEvent.class, result.events().get(3)).sourceName());
        MoveResolvedEvent resolved = assertInstanceOf(MoveResolvedEvent.class, result.events().get(4));
        assertTrue(resolved.hit());
        assertEquals(5, resolved.damage());
        assertEquals(95, resolved.targetHp());
        assertEquals(95, target.hp());
        assertEquals(5, state.damageHistory().damageReceivedThisRound().get("target").intValue());
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, actor.moveFrequencyUsage().battleUses("strike"));
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, int hp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                hp,
                hp,
                new ActionBudget()
        );
    }
}
