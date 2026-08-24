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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerceptionErrataPreDamageReactionTest {
    @Test
    void exactErrataVariantDisengagesOneTileFromAlliedAreaAttack() {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(0, 0), List.of());
        RuntimeCombatantState defender = combatant(
                "defender", new GridCoord(1, 1), List.of("Perception [Errata]")
        );
        BattleRuntimeState state = state(attacker, defender, "A", "A");
        GridCoord origin = defender.position();

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
        assertFalse(defender.position().equals(origin));
        assertTrue(chebyshev(origin, defender.position()) <= 1);
        assertFalse(List.of(new GridCoord(1, 1), new GridCoord(2, 1)).contains(defender.position()));
        assertTrue(defender.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertFalse(defender.temporaryEffects().has("perception_ready"));
        assertFalse(defender.temporaryEffects().has("perception_used"));

        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("ability", event.sourceKind());
        assertEquals("Perception [Errata]", event.sourceName());
        assertEquals("defender", event.actorId());
        assertEquals("attacker", event.targetId());
        assertEquals("Oracle Area Move", event.moveId());
        assertEquals("disengage", event.effect());
    }

    @Test
    void basePerceptionDoesNotSatisfyExactErrataVariant() {
        RuntimeCombatantState attacker = combatant("attacker", new GridCoord(0, 0), List.of());
        RuntimeCombatantState defender = combatant("defender", new GridCoord(1, 1), List.of("Perception"));
        BattleRuntimeState state = state(attacker, defender, "A", "A");

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                PreDamageReactionContext.of(
                        state, "attacker", "defender", "Area", List.of(new GridCoord(1, 1))
                ),
                PreDamageReactionResult.of(true, 7, 1.0)
        );

        assertTrue(result.hit());
        assertEquals(new GridCoord(1, 1), defender.position());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void enemyAttackerAndAbilitySuppressionBlockErrataReaction() {
        RuntimeCombatantState enemyAttacker = combatant("attacker", new GridCoord(0, 0), List.of());
        RuntimeCombatantState defender = combatant(
                "defender", new GridCoord(1, 1), List.of("Perception [Errata]")
        );
        BattleRuntimeState enemyState = state(enemyAttacker, defender, "B", "A");

        PreDamageReactionResult enemyResult = BuiltinPreDamageReactionHooks.registry().resolve(
                PreDamageReactionContext.of(
                        enemyState, "attacker", "defender", "Area", List.of(new GridCoord(1, 1))
                ),
                PreDamageReactionResult.of(true, 7, 1.0)
        );
        assertTrue(enemyResult.hit());
        assertEquals(new GridCoord(1, 1), defender.position());

        RuntimeCombatantState allyAttacker = combatant("attacker", new GridCoord(0, 0), List.of());
        RuntimeCombatantState suppressed = combatant(
                "defender", new GridCoord(1, 1), List.of("Perception [Errata]")
        );
        suppressed.setAbilitiesSuppressed(true);
        BattleRuntimeState suppressedState = state(allyAttacker, suppressed, "A", "A");

        PreDamageReactionResult suppressedResult = BuiltinPreDamageReactionHooks.registry().resolve(
                PreDamageReactionContext.of(
                        suppressedState, "attacker", "defender", "Area", List.of(new GridCoord(1, 1))
                ),
                PreDamageReactionResult.of(true, 7, 1.0)
        );
        assertTrue(suppressedResult.hit());
        assertEquals(new GridCoord(1, 1), suppressed.position());
    }

    private static int chebyshev(GridCoord a, GridCoord b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
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
