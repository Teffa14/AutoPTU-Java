package io.autoptu.core.runtime;

/** Canonical field-effect families advanced during ROUND_START. */
public enum FieldEffectKind {
    TERRAIN("terrain"),
    ZONE("zone"),
    ROOM("room");

    private final String wireName;

    FieldEffectKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
