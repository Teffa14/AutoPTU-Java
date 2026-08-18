package io.autoptu.core.model;

/**
 * Battle-state independent inputs for PTU evasion resolution.
 *
 * Ability, item, trainer-feature, status, and terrain hooks collapse their
 * non-stat contributions into category bonuses before this pure resolver runs.
 * Minecraft adapters must not calculate the final evasion value.
 */
public record EvasionProfile(
        CombatantStatProfile stats,
        int physicalBonus,
        int specialBonus,
        int statusBonus,
        boolean suppressPositiveBonuses,
        boolean ignoreNonStatBonuses
) {
    public EvasionProfile {
        if (stats == null) {
            throw new IllegalArgumentException("stats are required");
        }
    }

    public int bonusFor(String category) {
        String normalized = category == null ? "special" : category.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "physical" -> physicalBonus;
            case "status" -> statusBonus;
            default -> specialBonus;
        };
    }
}
