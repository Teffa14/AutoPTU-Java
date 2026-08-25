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

class BattleRuntimeMoveSpecialPreDamagePipelineTest {
    @Test
    void moveSpecialMutatesOrdinaryDamageBeforeDefenderReactionAndHpHistory() {
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

        MoveSpecialHookRegistry moveSpecials = MoveSpecialHookRegistry.builder()
                .registerGlobal("set-damage", MoveSpecialPhase.PRE_DAMAGE, context -> {
                    assertTrue(context.hit());
                    assertEquals(13, ((Number) context.result().get("roll")).intValue());
                    assertTrue(((Number) context.result().get("damage")).intValue() > 0);
                    context.result().put("damage", 3);
                    context.result().put("type_multiplier", 0.5d);
                    return List.of(new RuleEffectEvent(
                            "move_special", "Test Special", "actor", "target",
                            "strike", "set_damage", 3.0d, 100
                    ));
                })
                .build();

        AtomicBoolean reactionSawSpecialResult = new AtomicBoolean(false);
        PreDamageReactionHookRegistry reactions = PreDamageReactionHookRegistry.builder()
                .register("observe-special", HookSource.SYSTEM, 10, (context, current) -> {
                    reactionSawSpecialResult.set(current.damage() == 3 && current.typeMultiplier() == 0.5d);
                    return new PreDamageReactionResult(
                            current.hit(), current.damage(), current.typeMultiplier(),
                            List.of(new RuleEffectEvent(
                                    "system", "Test Reaction", "target", "actor",
                                    "strike", "observe_damage", current.damage(), 100
                            ))
                    );
                })
                .build();

        MoveOption move = MoveOption.standard(
                "strike",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(null, 6, 20, "physical", "Normal")
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
                move.combatProfile()
        );

        assertTrue(reactionSawSpecialResult.get());
        assertEquals(3, result.events().size());
        RuleEffectEvent special = assertInstanceOf(RuleEffectEvent.class, result.events().get(0));
        RuleEffectEvent reaction = assertInstanceOf(RuleEffectEvent.class, result.events().get(1));
        MoveResolvedEvent resolved = assertInstanceOf(MoveResolvedEvent.class, result.events().get(2));
        assertEquals("Test Special", special.sourceName());
        assertEquals("Test Reaction", reaction.sourceName());
        assertTrue(resolved.hit());
        assertEquals(3, resolved.damage());
        assertEquals(97, resolved.targetHp());
        assertEquals(97, target.hp());
        assertEquals(3, state.damageHistory().damageReceivedThisRound().get("target").intValue());
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertTrue(target.actionBudget().hasActionAvailable(ActionType.SHIFT));
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
