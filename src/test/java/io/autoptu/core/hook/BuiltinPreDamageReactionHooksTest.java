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

final class BuiltinPreDamageReactionHooksTest {
    @Test
    void telepathyMovesAlliedDefenderAndCancelsIncomingAreaHit() {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(0, 0), 2, List.of());
        RuntimeCombatantState defender = combatant("defender", new GridCoord(1, 1), 3, List.of("Telepathy"));
        BattleRuntimeState state = state(attacker, defender, "A", "A");

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                PreDamageReactionContext.of(
                        state,
                        "attacker",
                        "defender",
                        "Oracle Area Move",
                        List.of(new GridCoord(1, 1), new GridCoord(2, 1))
                ),
                PreDamageReactionResult.of(true, 9, 1.0)
        );

        assertFalse(result.hit());
        assertEquals(0, result.damage());
        assertEquals(0.0, result.typeMultiplier());
        assertEquals(new GridCoord(1, 4), defender.position());
        assertTrue(defender.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("ability", event.sourceKind());
        assertEquals("Telepathy", event.sourceName());
        assertEquals("defender", event.actorId());
        assertEquals("attacker", event.targetId());
        assertEquals("Oracle Area Move", event.moveId());
        assertEquals("shift", event.effect());
        assertEquals(20, event.actorHp());
    }

    @Test
    void optionalDecisionCanRejectTelepathyBeforeMovement() {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(0, 0), 2, List.of());
        RuntimeCombatantState defender = combatant("defender", new GridCoord(1, 1), 3, List.of("Telepathy"));
        BattleRuntimeState state = state(attacker, defender, "A", "A");
        AtomicBoolean invoked = new AtomicBoolean(false);

        PreDamageReactionContext context = new PreDamageReactionContext(
                state,
                "attacker",
                "defender",
                "Original Move",
                "Effective Move",
                List.of(new GridCoord(1, 1), new GridCoord(2, 1)),
                request -> {
                    invoked.set(true);
                    assertEquals("defender", request.actorId());
                    assertEquals("Telepathy", request.label());
                    assertEquals("Original Move", request.moveName());
                    assertEquals("Effective Move", request.triggerMoveName());
                    assertTrue(request.optional());
                    return false;
                }
        );

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                context,
                PreDamageReactionResult.of(true, 9, 1.0)
        );

        assertTrue(invoked.get());
        assertTrue(result.hit());
        assertEquals(9, result.damage());
        assertEquals(new GridCoord(1, 1), defender.position());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void enemyAttackerAndMoldBreakerSuppressTelepathy() {
        RuntimeCombatantState enemyAttacker = combatant("attacker", new GridCoord(0, 0), 2, List.of());
        RuntimeCombatantState defender = combatant("defender", new GridCoord(1, 1), 3, List.of("Telepathy"));
        BattleRuntimeState enemyState = state(enemyAttacker, defender, "B", "A");

        PreDamageReactionResult enemyResult = BuiltinPreDamageReactionHooks.registry().resolve(
                PreDamageReactionContext.of(
                        enemyState, "attacker", "defender", "Area", List.of(new GridCoord(1, 1))
                ),
                PreDamageReactionResult.of(true, 7, 1.0)
        );
        assertTrue(enemyResult.hit());
        assertEquals(new GridCoord(1, 1), defender.position());

        RuntimeCombatantState moldBreakerAttacker = combatant(
                "attacker", new GridCoord(0, 0), 2, List.of("Mold Breaker")
        );
        RuntimeCombatantState telepathyDefender = combatant(
                "defender", new GridCoord(1, 1), 3, List.of("Telepathy")
        );
        BattleRuntimeState moldBreakerState = state(moldBreakerAttacker, telepathyDefender, "A", "A");

        PreDamageReactionResult moldBreakerResult = BuiltinPreDamageReactionHooks.registry().resolve(
                PreDamageReactionContext.of(
                        moldBreakerState, "attacker", "defender", "Area", List.of(new GridCoord(1, 1))
                ),
                PreDamageReactionResult.of(true, 7, 1.0)
        );
        assertTrue(moldBreakerResult.hit());
        assertEquals(new GridCoord(1, 1), telepathyDefender.position());
    }

    @Test
    void telepathyLeavesOutcomeUntouchedWhenNoSafeTileExists() {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(0, 0), 2, List.of());
        RuntimeCombatantState defender = combatant("defender", new GridCoord(1, 1), 1, List.of("Telepathy"));
        BattleRuntimeState state = state(attacker, defender, "A", "A");

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                PreDamageReactionContext.of(
                        state,
                        "attacker",
                        "defender",
                        "Area",
                        List.of(
                                new GridCoord(1, 1),
                                new GridCoord(0, 1),
                                new GridCoord(2, 1),
                                new GridCoord(1, 0),
                                new GridCoord(1, 2)
                        )
                ),
                PreDamageReactionResult.of(true, 7, 1.0)
        );

        assertTrue(result.hit());
        assertEquals(7, result.damage());
        assertEquals(1.0, result.typeMultiplier());
        assertEquals(new GridCoord(1, 1), defender.position());
        assertTrue(result.events().isEmpty());
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            int overland,
            List<String> abilities
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, overland),
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

    private static BattleRuntimeState state(
            RuntimeCombatantState attacker,
            RuntimeCombatantState defender,
            String attackerTeam,
            String defenderTeam
    ) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(attacker, defender),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        attacker.combatantId(), CombatantAffiliationState.active(attackerTeam),
                        defender.combatantId(), CombatantAffiliationState.active(defenderTeam)
                )
        );
    }
}
