package io.autoptu.core.hook;

/** Semantic event that opened an action window. */
public enum ActionWindowTrigger {
    UNSPECIFIED,
    ADJACENT_FOE_SHIFTS_AWAY,
    ADJACENT_FOE_USES_NON_TARGETING_MANEUVER,
    ADJACENT_FOE_STANDS_UP,
    ADJACENT_FOE_USES_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET,
    ADJACENT_FOE_RETRIEVES_ITEM
}
