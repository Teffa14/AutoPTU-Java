package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShellShieldPreDamageReactionTest {
    @Test
    void acceptedShellShieldConsumesReadyAddsWithdrawnAndRaisesDefense() {
        RuntimeCombatantState attacker = combatant("attacker", List.of());
        RuntimeCombatantState defender = combatant("defender", List.of("Shell Shield"));
        defender.temporaryEffects().add("shell_shield_ready", Map.of("ability", "Shell Shield [Fixture]"));
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                new PreDamageReactionContext(
                        state, "attacker", "defender", "Tackle", "Tackle", "melee",
                        List.of(), OutOfTurnDecisionGate.allowWhenUnconfigured()
                ),
                PreDamageReactionResult.of(true, 12, 1.0)
        );

        assertTrue(result.hit());
        assertEquals(12, result.damage());
        assertEquals(1.0, result.typeMultiplier());
        assertFalse(defender.temporaryEffects().has("shell_shield_ready"));
        assertTrue(state.hasStatus("defender", "Withdrawn"));
        assertEquals(1, defender.combatStages().get(CombatStat.DEF));

        RuleEffectEvent event = (RuleEffectEvent) result.events().getLast();
        assertEquals("ability", event.sourceKind());
        assertEquals("Shell Shield [Fixture]", event.sourceName());
        assertEquals("defender", event.actorId());
        assertEquals("attacker", event.targetId());
        assertEquals("withdraw", event.effect());
    }

    @Test
    void declinedDecisionPreservesReadyAndLeavesStateUntouched() {
        RuntimeCombatantState attacker = combatant("attacker", List.of());
        RuntimeCombatantState defender = combatant("defender", List.of("Shell Shield"));
        defender.temporaryEffects().add("shell_shield_ready", Map.of());
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                new PreDamageReactionContext(
                        state, "attacker", "defender", "Tackle", "Tackle", "melee",
                        List.of(), request -> false
                ),
                PreDamageReactionResult.of(true, 8, 1.0)
        );

        assertTrue(result.hit());
        assertTrue(defender.temporaryEffects().has("shell_shield_ready"));
        assertFalse(state.hasStatus("defender", "Withdrawn"));
        assertEquals(0, defender.combatStages().get(CombatStat.DEF));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void missingReadinessDoesNothing() {
        RuntimeCombatantState attacker = combatant("attacker", List.of());
        RuntimeCombatantState defender = combatant("defender", List.of("Shell Shield"));
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                new PreDamageReactionContext(
                        state, "attacker", "defender", "Tackle", "Tackle", "melee",
                        List.of(), OutOfTurnDecisionGate.allowWhenUnconfigured()
                ),
                PreDamageReactionResult.of(true, 8, 1.0)
        );

        assertEquals(0, defender.combatStages().get(CombatStat.DEF));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void moldBreakerSuppressesShellShieldBeforeReadinessIsConsumed() {
        RuntimeCombatantState attacker = combatant("attacker", List.of("Mold Breaker"));
        RuntimeCombatantState defender = combatant("defender", List.of("Shell Shield"));
        defender.temporaryEffects().add("shell_shield_ready", Map.of());
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                new PreDamageReactionContext(
                        state, "attacker", "defender", "Tackle", "Tackle", "melee",
                        List.of(), OutOfTurnDecisionGate.allowWhenUnconfigured()
                ),
                PreDamageReactionResult.of(true, 8, 1.0)
        );

        assertTrue(defender.temporaryEffects().has("shell_shield_ready"));
        assertFalse(state.hasStatus("defender", "Withdrawn"));
        assertEquals(0, defender.combatStages().get(CombatStat.DEF));
        assertTrue(result.events().isEmpty());
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(id.equals("attacker") ? 0 : 1, 0), 3),
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
                List.of(attacker, defender)
        );
    }
}
