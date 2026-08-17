package io.autoptu.core.model;

/** PTU turn phases mirrored from Python battle_state.TurnPhase. */
public enum TurnPhase {
    START("start"),
    COMMAND("command"),
    ACTION("action"),
    END("end");

    private final String value;

    TurnPhase(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TurnPhase fromValue(String value) {
        for (TurnPhase phase : values()) {
            if (phase.value.equals(value)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("Unknown turn phase: " + value);
    }
}
