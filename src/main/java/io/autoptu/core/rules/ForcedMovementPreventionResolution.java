package io.autoptu.core.rules;

import java.util.List;
import java.util.Set;

/** Generic defender-side ability prevention for move-caused forced movement. */
public final class ForcedMovementPreventionResolution {
    private record Rule(String abilityName, Set<ForcedMovementInstruction.Kind> blockedKinds) {}

    private static final List<Rule> RULES = List.of(
            new Rule("Suction Cups", Set.of(ForcedMovementInstruction.Kind.PUSH)),
            new Rule("Sumo Stance", Set.of(ForcedMovementInstruction.Kind.PUSH))
    );

    private ForcedMovementPreventionResolution() {}

    public static boolean prevented(
            ForcedMovementInstruction instruction,
            List<String> defenderAbilities,
            boolean abilitiesSuppressed
    ) {
        if (instruction == null || abilitiesSuppressed) return false;
        for (Rule rule : RULES) {
            if (!rule.blockedKinds().contains(instruction.kind())) continue;
            if (AbilityIdentityResolution.matchesRegistration(defenderAbilities, rule.abilityName())) return true;
        }
        return false;
    }
}
