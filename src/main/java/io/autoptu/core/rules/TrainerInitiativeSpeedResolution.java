package io.autoptu.core.rules;

import java.util.List;

/**
 * Pure parity boundary for Python BattleState._trainer_initiative_speed().
 *
 * Trainer initiative Speed is authoritative battle state. An explicit trainer Speed
 * wins. Otherwise the fastest active, non-fainted controlled Pokemon is used; when
 * none are available, the fastest Pokemon anywhere in that trainer's battle roster
 * is used; an empty roster resolves to zero.
 */
public final class TrainerInitiativeSpeedResolution {
    private TrainerInitiativeSpeedResolution() {
    }

    public static int resolve(
            Integer explicitTrainerSpeed,
            List<Integer> activePokemonSpeeds,
            List<Integer> rosterPokemonSpeeds
    ) {
        if (explicitTrainerSpeed != null) {
            return explicitTrainerSpeed;
        }
        Integer activeMax = maxOrNull(activePokemonSpeeds);
        if (activeMax != null) {
            return activeMax;
        }
        Integer rosterMax = maxOrNull(rosterPokemonSpeeds);
        return rosterMax == null ? 0 : rosterMax;
    }

    private static Integer maxOrNull(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Integer max = null;
        for (Integer value : values) {
            if (value == null) {
                throw new IllegalArgumentException("initiative Speed values cannot contain null");
            }
            if (max == null || value > max) {
                max = value;
            }
        }
        return max;
    }
}
