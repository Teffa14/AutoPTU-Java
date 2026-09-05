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

class RuntimeTickDamageTest {
    @Test
    void derivesOneTickFromMaximumHpUsingPythonFloorSemantics() {
        BattleRuntimeState state = state(combatant("target", 37, 37));

        assertEquals(3, RuntimeTickDamage.tickValue(state, "target"));
        assertEquals(3, RuntimeTickDamage.apply(state, "target", 1));
        assertEquals(34, state.requireCombatant("target").hp());
    }

    @Test
    void clampsTickValueToOneForSmallMaximumHp() {
        BattleRuntimeState state = state(combatant("target", 7, 7));

        assertEquals(1, RuntimeTickDamage.tickValue(state, "target"));
        assertEquals(2, RuntimeTickDamage.apply(state, "target", 2));
        assertEquals(5, state.requireCombatant("target").hp());
    }

    @Test
    void returnsActualHpLostWhenRequestedTicksExceedRemainingHp() {
        BattleRuntimeState state = state(combatant("target", 4, 40));

        assertEquals(4, RuntimeTickDamage.apply(state, "target", 2));
        assertEquals(0, state.requireCombatant("target").hp());
    }

    @Test
    void zeroTicksAndFaintedTargetsAreNoOps() {
        BattleRuntimeState healthy = state(combatant("target", 20, 20));
        BattleRuntimeState fainted = state(combatant("target", 0, 20));

        assertEquals(0, RuntimeTickDamage.apply(healthy, "target", 0));
        assertEquals(20, healthy.requireCombatant("target").hp());
        assertEquals(0, RuntimeTickDamage.apply(fainted, "target", 1));
        assertEquals(0, fainted.requireCombatant("target").hp());
    }

    @Test
    void rejectsNegativeTickCounts() {
        BattleRuntimeState state = state(combatant("target", 20, 20));

        assertThrows(IllegalArgumentException.class, () -> RuntimeTickDamage.apply(state, "target", -1));
        assertEquals(20, state.requireCombatant("target").hp());
    }

    private static BattleRuntimeState state(RuntimeCombatantState combatant) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(combatant)
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
