package io.autoptu.core.hook;

/**
 * Ordered families used by PokemonState.handle_phase_effects in the Python oracle.
 *
 * Keep this order stable: status effects run first, then ability phase hooks, then
 * perk effects. New implementations plug into a family instead of changing the
 * lifecycle order.
 */
public enum CombatantPhaseEffectFamily {
    STATUS,
    ABILITY,
    PERK
}
