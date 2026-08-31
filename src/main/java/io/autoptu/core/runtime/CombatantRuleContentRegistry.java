package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable canonical PTU rule-content snapshot keyed by combatant id.
 *
 * <p>This registry is a generic runtime source for capabilities, Loyalty, controllers, skills,
 * Trainer Features and Naturewalk data. Rule families resolve content from this snapshot instead
 * of accepting rule-specific or per-invocation maps from Minecraft/Cobblemon adapters.</p>
 */
public final class CombatantRuleContentRegistry {
    private final Map<String, CombatantRuleContent> contentByCombatant;

    public CombatantRuleContentRegistry(Map<String, CombatantRuleContent> contentByCombatant) {
        LinkedHashMap<String, CombatantRuleContent> copy = new LinkedHashMap<>();
        if (contentByCombatant != null) {
            for (Map.Entry<String, CombatantRuleContent> entry : contentByCombatant.entrySet()) {
                String combatantId = entry.getKey();
                CombatantRuleContent content = entry.getValue();
                if (combatantId == null || combatantId.isBlank()) {
                    throw new IllegalArgumentException("combatantId is required");
                }
                if (content == null) {
                    throw new IllegalArgumentException("combatant rule content is required");
                }
                copy.put(combatantId.strip(), content);
            }
        }
        this.contentByCombatant = Map.copyOf(copy);
    }

    public static CombatantRuleContentRegistry empty() {
        return new CombatantRuleContentRegistry(Map.of());
    }

    public CombatantRuleContent require(String combatantId) {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        return contentByCombatant.getOrDefault(combatantId.strip(), CombatantRuleContent.empty());
    }

    public Map<String, CombatantRuleContent> snapshot() {
        return contentByCombatant;
    }
}
