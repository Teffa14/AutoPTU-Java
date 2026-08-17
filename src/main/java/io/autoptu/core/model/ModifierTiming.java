package io.autoptu.core.model;

/** Timing labels mirrored from Python calculations.ModifierTiming. */
public enum ModifierTiming {
    PRE_ACCURACY("pre_accuracy"),
    POST_ACCURACY("post_accuracy"),
    PRE_DAMAGE("pre_damage"),
    POST_DAMAGE("post_damage");

    private final String value;

    ModifierTiming(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
