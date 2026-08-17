package io.autoptu.core.model;

/** Typed Java counterpart of Python calculations.AttackModifier. */
public record AttackModifier(
        String slug,
        String kind,
        double value,
        ModifierTiming timing,
        String source
) {
    public AttackModifier {
        slug = slug == null ? "" : slug;
        kind = kind == null ? "" : kind;
        timing = timing == null ? ModifierTiming.PRE_DAMAGE : timing;
        source = source == null ? "" : source;
    }

    public static AttackModifier flat(String slug, double value) {
        return new AttackModifier(slug, "damage_flat", value, ModifierTiming.PRE_DAMAGE, "");
    }

    public static AttackModifier scalar(String slug, double value) {
        return new AttackModifier(slug, "damage_scalar", value, ModifierTiming.PRE_DAMAGE, "");
    }
}
