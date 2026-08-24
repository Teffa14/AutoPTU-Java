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

final class ParryPreDamageReactionTest {
    @Test
    void readyParryAvoidsMeleeAndRecordsRoundUsage() {
        RuntimeCombatantState attacker = combatant("attacker", List.of());
        RuntimeCombatantState defender = combatant("defender", List.of("Parry"));
        defender.temporaryEffects().add("parry_ready", Map.of());
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                new PreDamageReactionContext(
                        state, "attacker", "defender", "Slash", "Slash", "melee",
                        List.of(), OutOfTurnDecisionGate.allowWhenUnconfigured()
                ),
                PreDamageReactionResult.of(true, 11, 1.0)
        );

        assertFalse(result.hit());
        assertEquals(0, result.damage());
        assertEquals(0.0, result.typeMultiplier());
        assertFalse(defender.temporaryEffects().has("parry_ready"));
        assertTrue(defender.temporaryEffects().has("parry_used"));
        assertEquals(state.currentRound(), defender.temporaryEffects().getAll("parry_used").getFirst().payload().get("round"));
        assertTrue(defender.actionBudget().hasActionAvailable(ActionType.SHIFT));

        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("ability", event.sourceKind());
        assertEquals("Parry", event.sourceName());
        assertEquals("defender", event.actorId());
        assertEquals("attacker", event.targetId());
        assertEquals("avoid", event.effect());
    }

    @Test
    void acceptedDecisionConsumesReadyBeforeNonMeleeGuard() {
        RuntimeCombatantState attacker = combatant("attacker", List.of());
        RuntimeCombatantState defender = combatant("defender", List.of("Parry"));
        defender.temporaryEffects().add("parry_ready", Map.of());
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                new PreDamageReactionContext(
                        state, "attacker", "defender", "Water Gun", "Water Gun", "ranged",
                        List.of(), OutOfTurnDecisionGate.allowWhenUnconfigured()
                ),
                PreDamageReactionResult.of(true, 7, 1.0)
        );

        assertTrue(result.hit());
        assertFalse(defender.temporaryEffects().has("parry_ready"));
        assertFalse(defender.temporaryEffects().has("parry_used"));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void declinedDecisionPreservesReadiness() {
        RuntimeCombatantState attacker = combatant("attacker", List.of());
        RuntimeCombatantState defender = combatant("defender", List.of("Parry"));
        defender.temporaryEffects().add("parry_ready", Map.of());
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                new PreDamageReactionContext(
                        state, "attacker", "defender", "Slash", "Slash", "melee",
                        List.of(), request -> false
                ),
                PreDamageReactionResult.of(true, 9, 1.0)
        );

        assertTrue(result.hit());
        assertTrue(defender.temporaryEffects().has("parry_ready"));
        assertFalse(defender.temporaryEffects().has("parry_used"));
    }

    @Test
    void currentRoundUsageBlocksSecondParryAfterReadinessIsConsumed() {
        RuntimeCombatantState attacker = combatant("attacker", List.of());
        RuntimeCombatantState defender = combatant("defender", List.of("Parry"));
        defender.temporaryEffects().add("parry_ready", Map.of());
        defender.temporaryEffects().add("parry_used", Map.of("round", 0));
        BattleRuntimeState state = state(attacker, defender);

        PreDamageReactionResult result = BuiltinPreDamageReactionHooks.registry().resolve(
                new PreDamageReactionContext(
                        state, "attacker", "defender", "Slash", "Slash", "melee",
                        List.of(), OutOfTurnDecisionGate.allowWhenUnconfigured()
                ),
                PreDamageReactionResult.of(true, 9, 1.0)
        );

        assertTrue(result.hit());
        assertFalse(defender.temporaryEffects().has("parry_ready"));
        assertEquals(1, defender.temporaryEffects().getAll("parry_used").size());
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
                List.of(attacker, defender),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "attacker", CombatantAffiliationState.active("A"),
                        "defender", CombatantAffiliationState.active("B")
                )
        );
    }
}
