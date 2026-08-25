package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.MoveSpecialPhase;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.DamageDice;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMoveSpecialPostDamageApplicationTest {
    @Test
    void postDamageObservesCommittedHpAndHistoryWithoutRetroactiveDamage() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 100);
        RuntimeCombatantState target = combatant("target", new GridCoord(1, 2), 100);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, target)
        );
        MoveChoice choice = new MoveChoice(
                "actor", "strike", ChoiceTargetMode.COMBATANT, "target",
                new GridCoord(1, 2), ActionType.STANDARD
        );
        AccuracyResult accuracy = new AccuracyResult(true, false, 12, 1);
        DamageResult damage = new DamageResult(
                new DamageDice(1, 6, 0), 4, 0, 4, 7, 7, 7
        );

        int hpBefore = target.hp();
        AppliedActionResult resolved = BattleRuntime.applyResolvedMoveOutcome(
                state, choice, "AI", accuracy, damage);

        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerMove("post", MoveSpecialPhase.POST_DAMAGE, List.of("strike"), context -> {
                    assertEquals(7, context.damageDealt());
                    assertEquals(93, context.defender().hp());
                    assertEquals(7, state.damageHistory().damageReceivedThisRound().get("target").intValue());
                    assertEquals(7, ((Number) context.result().get("damage")).intValue());
                    assertEquals("from-pre", context.result().get("marker"));
                    context.result().put("damage", 999);
                    context.result().put("post_marker", "seen");
                    return List.of(new RuleEffectEvent(
                            "move_special", "Post Special", "actor", "target",
                            "strike", "post_damage", 7.0d, target.hp()
                    ));
                })
                .build();

        LinkedHashMap<String, Object> sharedResult = new LinkedHashMap<>();
        sharedResult.put("hit", true);
        sharedResult.put("crit", false);
        sharedResult.put("damage", 7);
        sharedResult.put("type_multiplier", 1.0d);
        sharedResult.put("marker", "from-pre");

        RuntimeMoveSpecialPostDamageApplication.Result result =
                RuntimeMoveSpecialPostDamageApplication.resolveAfterAppliedOutcome(
                        registry,
                        state,
                        choice,
                        "strike",
                        "physical",
                        sharedResult,
                        true,
                        hpBefore,
                        resolved
                );

        assertEquals(7, result.damageDealt());
        assertEquals(2, result.events().size());
        RuleEffectEvent post = assertInstanceOf(RuleEffectEvent.class, result.events().get(0));
        MoveResolvedEvent move = assertInstanceOf(MoveResolvedEvent.class, result.events().get(1));
        assertEquals("Post Special", post.sourceName());
        assertEquals(7, move.damage());
        assertEquals(93, move.targetHp());
        assertEquals(999, ((Number) result.resultSnapshot().get("damage")).intValue());
        assertEquals("seen", result.resultSnapshot().get("post_marker"));

        assertEquals(93, target.hp());
        assertEquals(7, state.damageHistory().damageReceivedThisRound().get("target").intValue());
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
