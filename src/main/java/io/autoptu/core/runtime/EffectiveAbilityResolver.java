package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-owned projection of the abilities currently presented by one combatant.
 *
 * <p>Python Transform/Impostor state represents a copied ability through the
 * {@code entrained_ability} temporary-effect family. While at least one valid copied
 * ability is present, that temporary projection replaces the combatant's canonical
 * ability list for trigger resolution. The permanent list remains unchanged.</p>
 */
public final class EffectiveAbilityResolver {
    private EffectiveAbilityResolver() {
    }

    public static List<String> resolve(RuntimeCombatantState combatant) {
        if (combatant == null) return List.of();

        ArrayList<String> copied = new ArrayList<>();
        for (TemporaryEffectEntry entry : combatant.temporaryEffects().getAll(TransformationStateResolver.ENTRAINED_ABILITY)) {
            Object value = entry.payload().get("ability");
            if (value == null) continue;
            String name = String.valueOf(value).strip();
            if (!name.isEmpty()) copied.add(name);
        }
        if (!copied.isEmpty()) return List.copyOf(copied);
        return combatant.abilities();
    }

    public static boolean hasExact(RuntimeCombatantState combatant, String abilityName) {
        if (abilityName == null || abilityName.isBlank()) return false;
        String target = abilityName.strip();
        for (String ability : resolve(combatant)) {
            if (ability.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    /**
     * Matches one effective ability against an explicit set of oracle-equivalent spellings.
     *
     * <p>The returned effective ability names remain untouched. This method only widens trigger
     * identity matching where the pinned Python content/runtime accepts more than one spelling.</p>
     */
    public static boolean hasAnyExact(RuntimeCombatantState combatant, String... abilityNames) {
        if (abilityNames == null || abilityNames.length == 0) return false;
        for (String abilityName : abilityNames) {
            if (hasExact(combatant, abilityName)) return true;
        }
        return false;
    }
}
