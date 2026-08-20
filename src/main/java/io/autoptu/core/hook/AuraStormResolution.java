package io.autoptu.core.hook;

/**
 * Pure, language-neutral Aura Storm post-damage contract frozen against the
 * Python AutoPTU oracle before wiring the ability into the live registry.
 *
 * Normal Aura Storm requires an Aura-keyword move and is suppressed by the
 * battle's Aura Break blocker query. The errata form scales only with injuries;
 * an Aura Break [Errata] temporary effect inverts that bonus instead of
 * suppressing it.
 */
public record AuraStormResolution(
        int damageBonus,
        boolean emitAuraStormEvent,
        boolean emitAuraBreakEvent
) {
    public static final AuraStormResolution NONE = new AuraStormResolution(0, false, false);

    public static AuraStormResolution normal(
            boolean hasAuraStorm,
            boolean moveHasAuraKeyword,
            int injuries,
            boolean auraBreakBlocked
    ) {
        requireInjuries(injuries);
        if (!hasAuraStorm || !moveHasAuraKeyword || auraBreakBlocked) {
            return NONE;
        }
        return new AuraStormResolution(5 + 2 * injuries, true, false);
    }

    public static AuraStormResolution errata(
            boolean hasAuraStormErrata,
            int injuries,
            boolean auraBreakErrataInverts
    ) {
        requireInjuries(injuries);
        if (!hasAuraStormErrata || injuries <= 0) {
            return NONE;
        }
        int bonus = 3 * injuries;
        AuraBreakErrataAdjustment adjusted = AuraBreakErrataAdjustment.fromResolvedInversion(
                bonus,
                auraBreakErrataInverts
        );
        return new AuraStormResolution(
                adjusted.adjustedBonus(),
                true,
                adjusted.emitAuraBreakEvent()
        );
    }

    private static void requireInjuries(int injuries) {
        if (injuries < 0) {
            throw new IllegalArgumentException("injuries cannot be negative");
        }
    }
}
