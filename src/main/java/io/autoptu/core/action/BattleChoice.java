package io.autoptu.core.action;

import io.autoptu.core.model.ActionType;

/**
 * One legal decision exposed by the headless battle core.
 *
 * AI, autoplay, human controllers, and Minecraft adapters must choose from this
 * contract instead of mutating battle state directly.
 */
public sealed interface BattleChoice permits ShiftChoice, MoveChoice {
    String actorId();

    ActionType actionType();

    /** Stable deterministic identity used by AI indexes, traces, and parity fixtures. */
    String stableKey();
}
