package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeldItemStartRuleProfileParserTest {
    @Test
    void parsesSupportedGenericStartFamiliesWithoutAdapterInputs() {
        HeldItemStartRuleProfile profile = HeldItemStartRuleProfileParser.parse(
                "Base Attack by +5. Base Defense is increased by 20%. Accuracy +2. " +
                "The holder gains +6 Accuracy on attacks targeting creatures with a lower Action Value. " +
                "Increases the power and accuracy of Water attacks by 10%. All Stat Evasions +1. " +
                "Adds +10 to their Initiative. The holder's Speed stat is halved."
        );

        assertEquals(List.of(new HeldItemStartTemporaryEffectResolution.StatAmount("atk", 5)), profile.baseStatChanges());
        assertEquals(List.of(new HeldItemStartTemporaryEffectResolution.StatScalar("def", 1.2)), profile.baseStatScalars());
        assertEquals(2, profile.accuracyBonus());
        assertEquals(1, profile.accuracyBonusVsLowerAv());
        assertEquals(new HeldItemStartTemporaryEffectResolution.TypeAmount("Water", 2), profile.typeAccuracyBonus());
        assertNull(profile.statusEvasionBonus());
        assertEquals(1, profile.allEvasionBonus());
        assertEquals(10, profile.initiativeBonus());
        assertEquals(0.5, profile.speedScalar());
    }

    @Test
    void ignoresUnsupportedHeldItemMechanics() {
        HeldItemStartRuleProfile profile = HeldItemStartRuleProfileParser.parse(
                "Recover 1/8th of max hit points at the end of each turn. Immune to secondary effects."
        );

        assertEquals(List.of(), profile.baseStatChanges());
        assertEquals(List.of(), profile.baseStatScalars());
        assertNull(profile.accuracyBonus());
        assertNull(profile.accuracyBonusVsLowerAv());
        assertNull(profile.typeAccuracyBonus());
        assertNull(profile.statusEvasionBonus());
        assertNull(profile.allEvasionBonus());
        assertNull(profile.initiativeBonus());
        assertNull(profile.speedScalar());
    }
}
