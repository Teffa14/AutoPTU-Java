package io.autoptu.core.rules;

import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.List;

/**
 * Pure parity boundary for the stable base of Python BattleState._initiative_entry_for_pokemon().
 *
 * The caller supplies a Speed value already resolved by the authoritative stat pipeline.
 * Weather/terrain ability multipliers, Early Bird, Agility Training, and Hardened Initiative
 * remain separate parity slices. This resolver owns Bashed, trainer modifier, Tailwind,
 * temporary initiative_bonus expiry/parsing, and initiative_zero_until_turn.
 */
public final class PokemonInitiativeEntryResolution {
    private PokemonInitiativeEntryResolution() {
    }

    public static InitiativeEntry resolve(
            String actorId,
            String trainerId,
            int resolvedSpeed,
            int trainerModifier,
            boolean bashed,
            boolean tailwindActive,
            int currentRound,
            List<TemporaryEffectEntry> temporaryEffects,
            int additionalInitiativeBonus,
            boolean initiativeZeroUntilTurn
    ) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (currentRound < 0) {
            throw new IllegalArgumentException("currentRound cannot be negative");
        }

        int speed;
        int total;
        if (bashed) {
            speed = 0;
            total = 0;
        } else {
            speed = resolvedSpeed;
            int tailwindBonus = tailwindActive ? 5 : 0;
            int temporaryBonus = temporaryInitiativeBonus(temporaryEffects, currentRound);
            total = speed + trainerModifier + tailwindBonus + temporaryBonus + additionalInitiativeBonus;
        }

        if (initiativeZeroUntilTurn) {
            total = 0;
        }
        return new InitiativeEntry(actorId, trainerId, speed, trainerModifier, 0, total);
    }

    static int temporaryInitiativeBonus(List<TemporaryEffectEntry> temporaryEffects, int currentRound) {
        if (temporaryEffects == null || temporaryEffects.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (TemporaryEffectEntry entry : temporaryEffects) {
            if (entry == null || !"initiative_bonus".equals(entry.name())) {
                continue;
            }
            Integer expiresRound = pythonIntOrNull(entry.payload().get("expires_round"));
            Object rawExpiry = entry.payload().get("expires_round");
            if (rawExpiry != null && expiresRound != null && currentRound > expiresRound) {
                continue;
            }
            Integer amount = pythonIntOrNull(entry.payload().get("amount"));
            if (amount != null) {
                total += amount;
            }
        }
        return total;
    }

    /** Small scalar subset of Python int(value) used by temporary-effect payloads. */
    private static Integer pythonIntOrNull(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer integer) return integer;
        if (value instanceof Long number) return number.intValue();
        if (value instanceof Double number) return number.intValue();
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
