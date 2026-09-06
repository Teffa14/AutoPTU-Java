package io.autoptu.core.runtime;

/**
 * Pure PTU Tick-size contract frozen against the pinned Python oracle.
 *
 * <p>A Tick is one tenth of maximum HP using integer floor division, with a minimum of one.
 * Damage, healing, injury, status, ability, item, and Trainer Feature families should reuse
 * this boundary instead of reimplementing Tick arithmetic.</p>
 */
public final class TickValueResolution {
    private TickValueResolution() {}

    public static int resolve(int maxHp) {
        if (maxHp <= 0) {
            throw new IllegalArgumentException("maxHp must be positive");
        }
        return Math.max(1, maxHp / 10);
    }
}
