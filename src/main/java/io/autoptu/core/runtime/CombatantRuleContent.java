package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable server-owned rule content that is not derived from Minecraft/Cobblemon entities.
 *
 * <p>Capabilities, Loyalty, PTU skill ranks, Trainer Features, and Naturewalk labels are generic
 * content used by multiple rule families. Keeping them in one content snapshot prevents adapters
 * from supplying rule-specific conclusions such as an already-resolved interception bonus.</p>
 */
public record CombatantRuleContent(
        List<String> capabilities,
        Integer loyalty,
        String controllerId,
        Map<String, Integer> skillRanks,
        List<String> trainerFeatures,
        List<String> naturewalkLabels
) {
    public CombatantRuleContent {
        capabilities = normalize(capabilities);
        if (loyalty != null) loyalty = Math.max(0, loyalty);
        controllerId = controllerId == null ? "" : controllerId.strip();
        skillRanks = normalizeSkillRanks(skillRanks);
        trainerFeatures = normalize(trainerFeatures);
        naturewalkLabels = normalize(naturewalkLabels);
    }

    public CombatantRuleContent(
            List<String> capabilities,
            Integer loyalty,
            String controllerId,
            Map<String, Integer> skillRanks
    ) {
        this(capabilities, loyalty, controllerId, skillRanks, List.of(), List.of());
    }

    public CombatantRuleContent(List<String> capabilities, Integer loyalty, String controllerId) {
        this(capabilities, loyalty, controllerId, Map.of(), List.of(), List.of());
    }

    public CombatantRuleContent(List<String> capabilities, Integer loyalty) {
        this(capabilities, loyalty, "", Map.of(), List.of(), List.of());
    }

    public static CombatantRuleContent empty() {
        return new CombatantRuleContent(List.of(), null, "", Map.of(), List.of(), List.of());
    }

    public boolean hasCapability(String capability) {
        return containsIgnoreCase(capabilities, capability);
    }

    /** Returns whether canonical server-owned content grants the exact Trainer Feature. */
    public boolean hasTrainerFeature(String featureName) {
        return containsIgnoreCase(trainerFeatures, featureName);
    }

    /** Returns the server-owned PTU rank for a skill, defaulting missing skills to zero. */
    public int skillRank(String skillName) {
        if (skillName == null || skillName.isBlank()) return 0;
        return skillRanks.getOrDefault(skillName.strip().toLowerCase(Locale.ROOT), 0);
    }

    private static boolean containsIgnoreCase(List<String> values, String expectedValue) {
        if (expectedValue == null || expectedValue.isBlank()) return false;
        String expected = expectedValue.strip().toLowerCase(Locale.ROOT);
        for (String value : values) {
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
