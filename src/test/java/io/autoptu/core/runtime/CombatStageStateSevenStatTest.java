package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.CombatStat;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CombatStageStateSevenStatTest {
    @Test
    void storesAllSevenCombatStagesWhilePreservingFiveStatCompatibility() {
        CombatStageState state = new CombatStageState(Map.of(
                CombatStat.ATK, 2,
                CombatStat.SPDEF, -3
        ));

        assertEquals(2, state.get(CombatStageStat.ATK));
        assertEquals(-3, state.get(CombatStageStat.SPDEF));
        assertEquals(0, state.get(CombatStageStat.ACCURACY));
        assertEquals(0, state.get(CombatStageStat.EVASION));

        state.set(CombatStageStat.ACCURACY, 4);
        state.adjust(CombatStageStat.EVASION, -2);
        state.adjust(CombatStat.ATK, 1);

        assertEquals(4, state.get(CombatStageStat.ACCURACY));
        assertEquals(-2, state.get(CombatStageStat.EVASION));
        assertEquals(3, state.get(CombatStat.ATK));
        assertEquals(3, state.snapshot().get(CombatStat.ATK));
        assertEquals(7, state.fullSnapshot().size());
        assertEquals(4, state.fullSnapshot().get(CombatStageStat.ACCURACY));
        assertEquals(-2, state.fullSnapshot().get(CombatStageStat.EVASION));
    }

    @Test
    void clampsAccuracyAndEvasionUsingTheSameCombatStageBounds() {
        CombatStageState state = new CombatStageState();

        assertEquals(6, state.set(CombatStageStat.ACCURACY, 99));
        assertEquals(-6, state.set(CombatStageStat.EVASION, -99));
        assertEquals(6, state.adjust(CombatStageStat.ACCURACY, 5));
        assertEquals(-6, state.adjust(CombatStageStat.EVASION, -5));
    }

    @Test
    void combatStageIdentityKeepsAccuracyAndEvasionOutsideCoreCombatStatArithmetic() {
        assertEquals(CombatStageStat.ATK, CombatStageStat.fromCombatStat(CombatStat.ATK));
        assertEquals(CombatStat.SPD, CombatStageStat.SPD.requireCombatStat());
        assertEquals(false, CombatStageStat.ACCURACY.isCoreCombatStat());
        assertEquals(false, CombatStageStat.EVASION.isCoreCombatStat());
        assertThrows(IllegalStateException.class, CombatStageStat.ACCURACY::requireCombatStat);
        assertThrows(IllegalStateException.class, CombatStageStat.EVASION::requireCombatStat);
    }
}
