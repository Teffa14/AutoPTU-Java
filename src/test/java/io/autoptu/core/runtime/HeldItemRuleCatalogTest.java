package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldItemRuleCatalogTest {
    @Test
    void resolvesExactNormalizedNameBeforeCompactFallback() {
        HeldItemStartRuleProfile exact = profile(1);
        HeldItemStartRuleProfile fallback = profile(2);
        LinkedHashMap<String, HeldItemStartRuleProfile> profiles = new LinkedHashMap<>();
        profiles.put("X Accuracy", exact);
        profiles.put("X-Accuracy", fallback);
        HeldItemRuleCatalog catalog = new HeldItemRuleCatalog(profiles);

        assertEquals(exact, catalog.findByName("  x accuracy ").orElseThrow());
        assertEquals(fallback, catalog.findByName("x-accuracy").orElseThrow());
    }

    @Test
    void usesCompactPunctuationFallbackWhenExactNameIsMissing() {
        HeldItemStartRuleProfile profile = profile(3);
        HeldItemRuleCatalog catalog = new HeldItemRuleCatalog(Map.of("X-Accuracy", profile));

        assertEquals(profile, catalog.findByName("X Accuracy").orElseThrow());
        assertEquals(profile, catalog.find(new HeldItemState("slot-1", "x_accuracy")).orElseThrow());
    }

    @Test
    void bindsRuntimeDisplayNameOnlyWhenMaterializingTemporaryEffects() {
        HeldItemStartRuleProfile profile = profile(4);
        HeldItemStartTemporaryEffectResolution.Input input = profile.forHeldItem(
                new HeldItemState("slot-2", "Wide Lens")
        );

        assertEquals("Wide Lens", input.itemName());
        assertEquals(4, input.accuracyBonus());
        assertTrue(input.baseStatChanges().isEmpty());
    }

    private static HeldItemStartRuleProfile profile(int accuracy) {
        return new HeldItemStartRuleProfile(
                List.of(), List.of(), accuracy, null, null, null, null, null, null
        );
    }
}
