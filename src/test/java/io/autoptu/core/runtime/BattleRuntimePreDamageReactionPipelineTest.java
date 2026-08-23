package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.hook.PostDamageHookResult;
import io.autoptu.core.hook.PreDamageReactionHookRegistry;
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

class BattleRuntimePreDamageReactionPipelineTest {
    @Test
    void cancelledPreDamageReactionRunsAfterOrdinaryDamageAndBeforeHpPostDamageAndHistory() {
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

        AtomicBoolean sawOrdinaryDamage = new AtomicBoolean(false);
        PreDamageReactionHookRegistry preDamage = PreDamageReactionHookRegistry.builder()
                .register("cancel-after-damage", HookSource.SYSTEM, 10, (context, current) -> {
                    sawOrdinaryDamage.set(current.damage() > 0);
                    return current.cancelHit(List.of(new RuleEffectEvent(
                            "system", "Test Reaction", "target", "actor",
                            "strike", "cancel_hit", 0.0, 100
                    )));
                })
                .build();

        AtomicBoolean postDamageRan = new AtomicBoolean(false);
        PostDamageHookRegistry postDamage = PostDamageHookRegistry.builder()
                .register("must-not-run-after-cancel", HookSource.SYSTEM, 10, context -> {
                    postDamageRan.set(true);
                    return new PostDamageHookResult(500, List.of());
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
                preDamage,
                postDamage,
                move.combatProfile()
        );

        assertTrue(sawOrdinaryDamage.get(), "PRE-damage reaction must observe ordinary resolved damage");
        assertFalse(postDamageRan.get(), "post-result damage hooks must not restore damage after a cancelled hit");
        assertEquals(2, result.events().size());
        RuleEffectEvent reaction = assertInstanceOf(RuleEffectEvent.class, result.events().get(0));
        MoveResolvedEvent resolved = assertInstanceOf(MoveResolvedEvent.class, result.events().get(1));
        assertEquals("Test Reaction", reaction.sourceName());
        assertFalse(resolved.hit());
        assertEquals(0, resolved.damage());
        assertEquals(100, resolved.targetHp());
        assertEquals(100, target.hp());
        assertFalse(state.damageHistory().damageReceivedThisRound().containsKey("target"));
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
