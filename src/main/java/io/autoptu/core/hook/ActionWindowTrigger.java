package io.autoptu.core.hook;

/** Semantic event that opened an action window. */
public enum ActionWindowTrigger {
    UNSPECIFIED,
    ACTION_DECLARED,
    HIT_CONFIRMED,
    DIRECT_DAMAGING_HIT,
    MISS_CONFIRMED,
    DAMAGE_APPLIED,
    TURN_STARTED,
    TURN_ENDED
}
