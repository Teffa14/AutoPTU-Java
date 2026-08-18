package io.autoptu.core.event;

/** Stable semantic event kinds emitted by the headless battle runtime. */
public enum BattleEventKind {
    MOVE_RESOLVED("move_resolved"),
    SHIFT_RESOLVED("shift_resolved"),
    STATUS_SKIP("status_skip"),
    TRAINER_FEATURE("trainer_feature");

    private final String value;

    BattleEventKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
