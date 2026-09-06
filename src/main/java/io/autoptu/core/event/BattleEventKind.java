package io.autoptu.core.event;

/** Stable semantic event kinds emitted by the headless battle runtime. */
public enum BattleEventKind {
    MOVE_RESOLVED("move_resolved"),
    SHIFT_RESOLVED("shift_resolved"),
    STATUS_SKIP("status_skip"),
    TRAINER_FEATURE("trainer_feature"),
    ABILITY("ability"),
    RULE_EFFECT("rule_effect"),
    FIELD_EFFECT("field_effect"),
    TERRAIN_HAZARD("terrain_hazard"),
    PHASE_CHANGE("phase"),
    TURN_START("turn_start"),
    BORROW_MOVE_END("borrow_move_end"),
    TURN_END("turn_end");

    private final String value;

    BattleEventKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
