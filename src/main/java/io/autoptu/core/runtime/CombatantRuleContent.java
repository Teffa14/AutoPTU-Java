package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable server-owned rule content that is not derived from Minecraft/Cobblemon entities.
 *
 * <p>Capabilities, Loyalty and PTU skill ranks are generic content used by multiple rule
 * families. Keeping them in one content snapshot prevents adapters from supplying
 * rule-specific conclusions such as an already-resolved interception bonus.</p>
 */
public record CombatantRuleContent(
        List<String> capabilities,
        Integer loyalty,
        String controllerId,
        Map<String, Integer> skillRanks
) {
    public CombatantRuleContent {
        capabilities = normalize(capabilities);
        if (loyalty != null) loyalty = Math.max(0, loyalty);
        controllerId = controllerId == null ? "" : controllerId.strip();
        skillRanks = normalizeSkillRanks(skillRanks);
    }

    public CombatantRuleContent(List<String> capabilities, Integer loyalty, String controllerId) {
        this(capabilities, loyalty, controllerId, Map.of());
    }

    public CombatantRuleContent(List<String> capabilities, Integer loyalty) {
        this(capabilities, loyalty, "", Map.of());
    }

    public static CombatantRuleContent empty() {
        return new CombatantRuleContent(List.of(), null, "", Map.of());
    }

    public boolean hasCapability(String capability) {
        if (capability == null || capability.isBlank()) return false;
        String expected = capability.strip().toLowerCase(Locale.ROOT);
        for (String value : capabilities) {
            if (value.toLowerCase(Locale.ROOT).equals(expected)) return true;
        }
        return false;
    }

    /** Returns the server-owned PTU rank for a skill, defaulting missing skills to zero. */
    public int skillRank(String skillName) {
        if (skillName == null || skillName.isBlank()) return 0;
        return skillRanks.getOrDefault(skillName.strip().toLowerCase(Locale.ROOT), 0);
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        ArrayList<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) normalized.add(value.strip());
        }
        return List.copyOf(normalized);
    }

    private static Map<String, Integer> normalizeSkillRanks(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) return Map.of();
        LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            String key = entry.getKey();
            Integer rank = entry.getValue();
            if (key == null || key.isBlank() || rank == null) continue;
            normalized.put(key.strip().toLowerCase(Locale.ROOT), Math.max(0, rank));
        }
        return Map.copyOf(normalized);
    }
}
