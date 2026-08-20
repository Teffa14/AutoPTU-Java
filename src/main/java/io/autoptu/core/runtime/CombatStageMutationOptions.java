package io.autoptu.core.runtime;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Internal controls for recursive combat-stage mutations.
 *
 * Python uses per-reaction skip flags for recursive hooks such as Plus/Minus [SwSh].
 * Java keeps that behavior generic by suppressing hook IDs instead of adding one
 * boolean field for every future reaction. This is server-owned rule state and is
 * never populated from Minecraft/Cobblemon action input.
 */
public record CombatStageMutationOptions(Set<String> suppressedHookIds) {
    public static final CombatStageMutationOptions NONE = new CombatStageMutationOptions(Set.of());

    public CombatStageMutationOptions {
        if (suppressedHookIds == null || suppressedHookIds.isEmpty()) {
            suppressedHookIds = Set.of();
        } else {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String hookId : suppressedHookIds) {
                if (hookId == null || hookId.isBlank()) continue;
                normalized.add(normalize(hookId));
            }
            suppressedHookIds = Set.copyOf(normalized);
        }
    }

    public boolean suppresses(String hookId) {
        return hookId != null && suppressedHookIds.contains(normalize(hookId));
    }

    public CombatStageMutationOptions suppressing(String hookId) {
        if (hookId == null || hookId.isBlank()) return this;
        LinkedHashSet<String> next = new LinkedHashSet<>(suppressedHookIds);
        next.add(normalize(hookId));
        return new CombatStageMutationOptions(next);
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
