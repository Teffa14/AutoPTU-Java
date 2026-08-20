package io.autoptu.core.hook;

import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.List;
import java.util.Locale;

/**
 * Shared post-result contract for Python's _aura_break_errata_adjust().
 *
 * Aura Break [Errata] does not own the original bonus. It inspects the attacker's
 * server-owned temporary effects for one matching the ability that produced a positive
 * bonus, then inverts that bonus. Expired entries request clearing the canonical
 * aura_break_errata effects. The scan still uses the original snapshot, matching the
 * Python list(...) iteration semantics.
 */
public record AuraBreakErrataAdjustment(
        int adjustedBonus,
        boolean emitAuraBreakEvent,
        String sourceId,
        boolean clearAuraBreakEffects
) {
    public static AuraBreakErrataAdjustment unchanged(int bonus) {
        return new AuraBreakErrataAdjustment(bonus, false, "", false);
    }

    /** Transitional helper for parity contracts that already resolved the inversion flag. */
    public static AuraBreakErrataAdjustment fromResolvedInversion(int bonus, boolean inverted) {
        if (bonus <= 0 || !inverted) {
            return unchanged(bonus);
        }
        return new AuraBreakErrataAdjustment(-bonus, true, "", false);
    }

    public static AuraBreakErrataAdjustment resolve(
            String abilityName,
            int bonus,
            int currentRound,
            List<TemporaryEffectEntry> effects
    ) {
        if (bonus <= 0) {
            return unchanged(bonus);
        }
        if (abilityName == null || abilityName.isBlank()) {
            throw new IllegalArgumentException("abilityName is required");
        }
        if (currentRound < 0) {
            throw new IllegalArgumentException("currentRound cannot be negative");
        }

        String target = normalize(abilityName);
        boolean clearEffects = false;
        TemporaryEffectEntry matched = null;
        for (TemporaryEffectEntry effect : effects == null ? List.<TemporaryEffectEntry>of() : effects) {
            if (effect == null || !"aura_break_errata".equals(effect.name())) {
                continue;
            }
            Integer expiresRound = integerPayload(effect, "expires_round");
            if (expiresRound != null && currentRound > expiresRound) {
                clearEffects = true;
                continue;
            }
            String affectedAbility = stringPayload(effect, "ability");
            if (normalize(affectedAbility).equals(target)) {
                matched = effect;
                break;
            }
        }

        if (matched == null) {
            return new AuraBreakErrataAdjustment(bonus, false, "", clearEffects);
        }
        return new AuraBreakErrataAdjustment(
                -bonus,
                true,
                stringPayload(matched, "source_id").strip(),
                clearEffects
        );
    }

    private static Integer integerPayload(TemporaryEffectEntry effect, String key) {
        Object value = effect.payload().get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String string && !string.isBlank()) return Integer.parseInt(string.strip());
        throw new IllegalArgumentException(key + " must be numeric");
    }

    private static String stringPayload(TemporaryEffectEntry effect, String key) {
        Object value = effect.payload().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
