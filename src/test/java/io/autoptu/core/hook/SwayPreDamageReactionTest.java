package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatantAffiliationState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SwayPreDamageReactionTest {
    @Test
    void swaySpendsStandardRedirectsSynchronouslyPushesAndCancelsOriginalHit() {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(2, 1), List.of());
        RuntimeCombatantState defender = combatant("defender", new GridCoord(2, 2), List.of("Sway"));
        BattleRuntimeState state = state(attacker, defender);
        AtomicBoolean redirected = new AtomicBoolean(false);

        PreDamageReactionContext context = new PreDamageReactionContext(
                state,
                "attacker",
                "defender",
                "Slash",
                "Slash",
                "melee",
                List.of(),
                OutOfTurnDecisionGate.allowWhenUnconfigured(),
                request -> {
                    redirected.set(true);
                    assertEquals("attacker", request.attackerId());
                    assertEquals("attacker", request.targetId());
                    assertEquals(new GridCoord(2, 1), request.targetPosition());
                    assertTrue(attacker.temporaryEffects().has("sway_redirect"));
                    return new PreDamageFollowUpMoveResult(List.of(new RuleEffectEvent(
                            "move", "Slash", "attacker", "attacker", "Slash", "redirected_hit", 0.0, 15
                    )));
                }
        );

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                context,
                PreDamageReactionResult.of(true, 12, 1.0)
        );

        assertTrue(redirected.get());
        assertFalse(result.hit());
        assertEquals(0, result.damage());
        assertEquals(0.0, result.typeMultiplier());
        assertFalse(defender.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals("Sway", defender.actionBudget().consumedDetail(ActionType.STANDARD).orElseThrow());
        assertEquals(1, defender.temporaryEffects().count("sway_used"));
        assertEquals(1, defender.temporaryEffects().getAll("sway_used").getFirst().payload().get("count"));
        assertFalse(attacker.temporaryEffects().has("sway_redirect"));
        assertEquals(new GridCoord(1, 1), attacker.position());
        assertTrue(attacker.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertTrue(attacker.actionBudget().hasActionAvailable(ActionType.SHIFT));

        assertEquals(3, result.events().size());
        assertEquals("redirect", ((RuleEffectEvent) result.events().get(0)).effect());
        assertEquals("redirected_hit", ((RuleEffectEvent) result.events().get(1)).effect());
        assertEquals("push", ((RuleEffectEvent) result.events().get(2)).effect());
    }

    @Test
    void rejectedSwayDecisionLeavesResourcesAndStateUntouched() {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(2, 1), List.of());
        RuntimeCombatantState defender = combatant("defender", new GridCoord(2, 2), List.of("Sway"));
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionContext context = new PreDamageReactionContext(
                state,
                "attacker",
                "defender",
                "Slash",
                "Slash",
                "melee",
                List.of(),
                request -> false,
                request -> {
                    throw new AssertionError("follow-up move must not execute after rejected Sway decision");
                }
        );

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                context,
                PreDamageReactionResult.of(true, 12, 1.0)
        );

        assertTrue(result.hit());
        assertEquals(12, result.damage());
        assertTrue(result.events().isEmpty());
        assertTrue(defender.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertFalse(defender.temporaryEffects().has("sway_used"));
        assertFalse(attacker.temporaryEffects().has("sway_redirect"));
        assertEquals(new GridCoord(2, 1), attacker.position());
    }

    @Test
    void recursionGuardPreventsSwayFromRedirectingItsOwnFollowUp() {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(2, 1), List.of());
        RuntimeCombatantState defender = combatant("defender", new GridCoord(2, 2), List.of("Sway"));
        attacker.temporaryEffects().add("sway_redirect", Map.of("expires_round", 0));
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                new PreDamageReactionContext(
                        state,
                        "attacker",
                        "defender",
                        "Slash",
                        "Slash",
                        "melee",
                        List.of(),
                        OutOfTurnDecisionGate.allowWhenUnconfigured(),
                        request -> {
                            throw new AssertionError("recursive Sway must not request another follow-up");
                        }
                ),
                PreDamageReactionResult.of(true, 12, 1.0)
        );

        assertTrue(result.hit());
        assertEquals(12, result.damage());
        assertTrue(defender.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(new GridCoord(2, 1), attacker.position());
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
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

    private static BattleRuntimeState state(RuntimeCombatantState attacker, RuntimeCombatantState defender) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(attacker, defender),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        attacker.combatantId(), CombatantAffiliationState.active("A"),
                        defender.combatantId(), CombatantAffiliationState.active("B")
                )
        );
    }
}
