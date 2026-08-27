package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Immutable server-owned rule content that is not derived from Minecraft/Cobblemon entities.
 *
 * <p>Capabilities and Loyalty are generic PTU content used by multiple rule families. Keeping
 * them in one content snapshot avoids feature-specific booleans such as livingWeapon=true from
 * crossing the adapter boundary.</p>
 */
public record CombatantRuleContent(List<String> capabilities, Integer loyalty, String controllerId) {
    public CombatantRuleContent {
        capabilities = normalize(capabilities);
        if (loyalty != null) loyalty = Math.max(0, loyalty);
        controllerId = controllerId == null ? "" : controllerId.strip();
    }

    public CombatantRuleContent(List<String> capabilities, Integer loyalty) {
        this(capabilities, loyalty, "");
    }

    public static CombatantRuleContent empty() {
        return new CombatantRuleContent(List.of(), null, "");
    }

    public boolean hasCapability(String capability) {
        if (capability == null || capability.isBlank()) return false;
        String expected = capability.strip().toLowerCase(Locale.ROOT);
        for (String value : capabilities) {
            if (value.toLowerCase(Locale.ROOT).equals(expected)) return true;
        }
        return false;
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        ArrayList<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) normalized.add(value.strip());
        }
        return List.copyOf(normalized);
    }
}
