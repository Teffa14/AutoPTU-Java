package io.autoptu.core.rules;

import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.ModifierTiming;

import java.util.Collection;
import java.util.List;

/**
 * Pure resolver for built-in PTU damage modifiers that are derived from
 * authoritative combatant state rather than supplied by Minecraft adapters.
 *
 * This starts with Burn because Python calculations._seed_builtin_modifiers
 * represents Burned + Physical as a 0.5 damage scalar. Additional status,
 * ability, item, terrain, and Trainer Feature hooks can be added in bounded
 * parity-safe slices without changing the Minecraft-facing move boundary.
 */
public final class BuiltinDamageModifierResolution {
    private BuiltinDamageModifierResolution() {
    }

    public static List<AttackModifier> resolve(String damageCategory, Collection<String> statuses) {
        if ("physical".equalsIgnoreCase(normalize(damageCategory)) && containsStatus(statuses, "burned")) {
            return List.of(new AttackModifier(
                    "burned",
                    "damage_scalar",
                    0.5,
                    ModifierTiming.POST_DAMAGE,
                    "Burn halves physical damage"
            ));
        }
        return List.of();
    }

    private static boolean containsStatus(Collection<String> statuses, String expected) {
        if (statuses == null || statuses.isEmpty()) {
            return false;
        }
        for (String status : statuses) {
            if (expected.equalsIgnoreCase(normalize(status))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
