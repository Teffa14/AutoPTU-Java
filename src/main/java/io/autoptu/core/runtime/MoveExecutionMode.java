package io.autoptu.core.runtime;

/**
 * Resource policy for move execution.
 *
 * DECLARED_ACTION represents an ordinary choice made from a combatant's turn and
 * therefore consumes action economy and move frequency. TRIGGERED_EFFECT represents
 * a move resolution initiated by authoritative battle state, such as a delayed hit,
 * reaction, interrupt, ability, item, or Trainer Feature. Triggered effects still
 * revalidate current geometry and resolve through the normal PTU rules/RNG pipeline,
 * but do not spend the combatant's turn action or another use of the source move.
 */
public enum MoveExecutionMode {
    DECLARED_ACTION,
    TRIGGERED_EFFECT
}
