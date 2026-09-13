package io.autoptu.core.hook;

/**
 * Authoritative decision windows where triggered or out-of-turn actions may be offered.
 *
 * <p>This enum defines timing only. It deliberately does not encode Priority/Interrupt
 * resource consumption; those profile-specific rules belong to action economy.</p>
 */
public enum ActionWindow {
    BEFORE_ACTION,
    BEFORE_HIT,
    BEFORE_DAMAGE,
    AFTER_DAMAGE,
    AFTER_ACTION,
    TURN_START,
    TURN_END
}
