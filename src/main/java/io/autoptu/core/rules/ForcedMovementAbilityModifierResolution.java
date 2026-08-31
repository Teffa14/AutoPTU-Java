package io.autoptu.core.rules;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Generic server-owned ability modifiers for move-caused forced movement.
 *
 * <p>Rules are declared as data so additional Python ability families can join this resolver
 * without creating one Java class per ability. Ability identity matching reuses the shared
 * Python-compatible registration matcher.</p>
 */
public final class ForcedMovementAbilityModifierResolution {
    private record Rule(
            String abilityName,
            Set<String> moveCategories,
            ForcedMovementInstruction.Kind kind,
            int baseDistance,
            int sameKindBonus
    ) {}

    private static final List<Rule> RULES = List.of(
            new Rule("Thrust", Set.of("physical"), ForcedMovementInstruction.Kind.PUSH, 1, 1)
    );

    private ForcedMovementAbilityModifierResolution() {}

    public static Optional<ForcedMovementInstruction> resolve(
            Optional<ForcedMovementInstruction> baseInstruction,
            String moveCategory,
            List<String> abilities,
            boolean abilitiesSuppressed
    ) {
        Optional<ForcedMovementInstruction> resolved = baseInstruction == null
                ? Optional.empty()
                : baseInstruction;
        if (abilitiesSuppressed) return resolved;

        String category = normalize(moveCategory);
        for (Rule rule : RULES) {
            if (!rule.moveCategories().contains(category)) continue;
            if (!AbilityIdentityResolution.matchesRegistration(abilities, rule.abilityName())) continue;

            if (resolved.isEmpty()) {
                resolved = Optional.of(new ForcedMovementInstruction(rule.kind(), rule.baseDistance()));
                continue;
            }
            ForcedMovementInstruction current = resolved.orElseThrow();
            if (current.kind() == rule.kind()) {
                resolved = Optional.of(new ForcedMovementInstruction(
                        current.kind(),
                        current.distance() + rule.sameKindBonus()
                ));
            }
        }
        return resolved;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
