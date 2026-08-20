package io.autoptu.core.runtime;

import java.util.List;

/**
 * Core-owned boundary that rebuilds the canonical initiative order for a new round.
 *
 * BattleRoundController owns when a rebuild happens. Implementations own how the order
 * is derived from authoritative PTU state. Minecraft/Cobblemon adapters must not supply
 * a client-computed order at rollover time.
 *
 * The first bounded consumer intentionally models only the lifecycle handoff. The full
 * Python initiative-entry formula (trainer slots, Tailwind, Bashed, room effects and
 * other initiative modifiers) remains separate parity work.
 */
@FunctionalInterface
public interface InitiativeRoundRebuilder {
    /** Return stable actor IDs in the exact initiative order for {@code round}. */
    List<String> rebuildOrder(BattleRuntimeState state, int round);
}
