package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.StatFlag;
import io.autoptu.core.model.StatModifier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatantIntrinsicAccuracyProfileTest {
    @Test
    void preservesIntrinsicAccuracyAcrossStageAndFlagRebinding() {
        CombatantStatProfile profile = profile(3);

        assertEquals(3, profile.intrinsicAccuracyCs());
        assertEquals(3, profile.withStages(Map.of(CombatStat.ATK, 2)).intrinsicAccuracyCs());
        assertEquals(3, profile.withFlag(StatFlag.BURNED, true).intrinsicAccuracyCs());
    }

    @Test
    void keepsIntrinsicAccuracySeparateFromMutableAccuracyProjection() {
        CombatantStatProfile profile = profile(2);

        assertEquals(2, profile.intrinsicAccuracyCs());
        assertEquals(5, EffectiveAccuracyStageProjection.resolve(3, profile.intrinsicAccuracyCs(), 0));
        assertEquals(2, EffectiveAccuracyStageProjection.resolve(0, profile.intrinsicAccuracyCs(), 0));
    }

    @Test
    void legacyProfilesDefaultIntrinsicAccuracyToZero() {
        EnumMap<CombatStat, Integer> bases = bases();
        CombatantStatProfile profile = new CombatantStatProfile(bases, Map.of(), Map.of(), Set.of());

        assertEquals(0, profile.intrinsicAccuracyCs());
    }

    private static CombatantStatProfile profile(int intrinsicAccuracyCs) {
        return new CombatantStatProfile(bases(), Map.of(), Map.<CombatStat, StatModifier>of(), Set.of(), intrinsicAccuracyCs);
    }

    private static EnumMap<CombatStat, Integer> bases() {
        EnumMap<CombatStat, Integer> bases = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) {
            bases.put(stat, 10);
        }
        return bases;
    }
}
