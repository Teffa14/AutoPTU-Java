package io.autoptu.core.runtime;

/**
 * Pure PTU ordinary-damage ingress contract for temporary-HP absorption.
 *
 * <p>The pinned Python oracle clamps incoming ordinary damage to a non-negative pending value,
 * resolves Substitute first, then spends temporary HP before any remaining damage reaches normal
 * HP. This resolver freezes only the temporary-HP stage. It performs no HP mutation, injury
 * processing, faint prevention, history recording, or adapter-side rule evaluation.</p>
 */
public final class TemporaryHpDamageAbsorption {
    private TemporaryHpDamageAbsorption() {}

    /** Resolve one temporary-HP absorption step without mutating battle state. */
    public static Result resolve(int temporaryHp, int incomingDamage) {
        if (temporaryHp < 0) {
            throw new IllegalArgumentException("temporaryHp cannot be negative");
        }

        int pendingDamage = Math.max(0, incomingDamage);
        int absorbedDamage = Math.min(temporaryHp, pendingDamage);
        int remainingDamage = pendingDamage - absorbedDamage;
        int remainingTemporaryHp = temporaryHp - absorbedDamage;

        return new Result(
                pendingDamage,
                absorbedDamage,
                remainingDamage,
                remainingTemporaryHp
        );
    }

    /** Immutable language-neutral result suitable for server-owned runtime composition. */
    public record Result(
            int pendingDamage,
            int absorbedDamage,
            int remainingDamage,
            int remainingTemporaryHp
    ) {
        public Result {
            if (pendingDamage < 0
                    || absorbedDamage < 0
                    || remainingDamage < 0
                    || remainingTemporaryHp < 0) {
                throw new IllegalArgumentException("damage absorption values cannot be negative");
            }
            if (absorbedDamage + remainingDamage != pendingDamage) {
                throw new IllegalArgumentException("absorbed and remaining damage must conserve pending damage");
            }
        }
    }
}
