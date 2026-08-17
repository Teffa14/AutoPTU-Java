package io.autoptu.core.model;

/** PTU action economy buckets mirrored from Python battle_state.ActionType. */
public enum ActionType {
    STANDARD("standard"),
    SHIFT("shift"),
    SWIFT("swift"),
    FULL("full"),
    FREE("free");

    private final String value;

    ActionType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ActionType fromValue(String value) {
        for (ActionType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown action type: " + value);
    }
}
