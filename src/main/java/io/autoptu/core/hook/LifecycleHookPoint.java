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
    /**
     * Python start_round effects that run after initiative/history setup and the round-start
     * state snapshot boundary, but before the first initiative actor is selected. This is the
     * shared seam for round-start Trainer Features and abilities such as Air Lock, Arena Trap,
     * Intimidate and Impostor as those families are ported.
     */
    ROUND_START_EFFECTS,
    ROUND_END,
    TURN_START,
    PHASE_CHANGE,
    TURN_END
}
