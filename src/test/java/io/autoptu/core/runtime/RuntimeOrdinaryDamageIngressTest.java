package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RuntimeOrdinaryDamageIngressTest {
    @Test
    void persistsPartialTemporaryHpAbsorptionWithoutChangingNormalHp() {
        BattleRuntimeState state = state(combatant("actor", 20, 20));
        state.requireCombatant("actor").addTempHpFromRuntime(7);

        RuntimeOrdinaryDamageIngress.Result result = RuntimeOrdinaryDamageIngress.apply(state, "actor", 4);

        assertEquals(20, state.requireCombatant("actor").hp());
        assertEquals(3, state.requireCombatant("actor").tempHp());
        assertEquals(new RuntimeOrdinaryDamageIngress.Result(4, 4, 0, 20, 20, 7, 3), result);
    }

    @Test
    void appliesOverflowToCanonicalHpAfterTemporaryHpIsExhausted() {
        BattleRuntimeState state = state(combatant("actor", 20, 20));
        state.requireCombatant("actor").addTempHpFromRuntime(7);

        RuntimeOrdinaryDamageIngress.Result result = RuntimeOrdinaryDamageIngress.apply(state, "actor", 12);

        assertEquals(15, state.requireCombatant("actor").hp());
        assertEquals(0, state.requireCombatant("actor").tempHp());
        assertEquals(new RuntimeOrdinaryDamageIngress.Result(12, 7, 5, 20, 15, 7, 0), result);
    }

    @Test
    void clampsOverkillAtZeroHpWithoutInventingAppliedDamage() {
        BattleRuntimeState state = state(combatant("actor", 3, 20));
        state.requireCombatant("actor").addTempHpFromRuntime(2);

        RuntimeOrdinaryDamageIngress.Result result = RuntimeOrdinaryDamageIngress.apply(state, "actor", 50);

        assertEquals(0, state.requireCombatant("actor").hp());
        assertEquals(0, state.requireCombatant("actor").tempHp());
        assertEquals(new RuntimeOrdinaryDamageIngress.Result(50, 2, 3, 3, 0, 2, 0), result);
    }

    @Test
    void nonPositiveDamageIsStatePreserving() {
        BattleRuntimeState state = state(combatant("actor", 11, 20));
        state.requireCombatant("actor").addTempHpFromRuntime(6);

        RuntimeOrdinaryDamageIngress.Result result = RuntimeOrdinaryDamageIngress.apply(state, "actor", -4);

        assertEquals(11, state.requireCombatant("actor").hp());
        assertEquals(6, state.requireCombatant("actor").tempHp());
        assertEquals(new RuntimeOrdinaryDamageIngress.Result(0, 0, 0, 11, 11, 6, 6), result);
    }

    @Test
    void mutationIsIsolatedToRequestedCombatant() {
        RuntimeCombatantState actor = combatant("actor", 20, 20);
        RuntimeCombatantState other = combatant("other", 18, 20);
        BattleRuntimeState state = state(actor, other);
        actor.addTempHpFromRuntime(4);
        other.addTempHpFromRuntime(9);

        RuntimeOrdinaryDamageIngress.apply(state, "actor", 7);

        assertEquals(17, actor.hp());
        assertEquals(0, actor.tempHp());
        assertEquals(18, other.hp());
        assertEquals(9, other.tempHp());
    }

    @Test
    void rejectsUnknownCombatantBeforeMutation() {
        RuntimeCombatantState actor = combatant("actor", 20, 20);
        BattleRuntimeState state = state(actor);
        actor.addTempHpFromRuntime(5);

        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOrdinaryDamageIngress.apply(state, "missing", 8)
        );
        assertEquals(20, actor.hp());
        assertEquals(5, actor.tempHp());
    }

    private static BattleRuntimeState state(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(combatants)
        );
    }

    private static RuntimeCombatantState combatant(String id, int hp, int maxHp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 1),
                hp,
                maxHp,
                new ActionBudget()
        );
    }
}
