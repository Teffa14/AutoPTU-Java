package io.autoptu.core.hook;

/**
 * Stable lifecycle seams where authoritative battle rules may run.
 *
 * The enum is intentionally broader than the first integration slice so later
 * status, terrain, ability, item, Trainer Feature and temporary-effect ports can
 * share one dispatch contract instead of adding bespoke controller branches.
 */
public enum LifecycleHookPoint {
    ROUND_START,
    ROUND_START_POST_INITIATIVE,
    ROUND_END,
    TURN_START,
    PHASE_CHANGE,
    TURN_END
}
