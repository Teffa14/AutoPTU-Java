package io.autoptu.core.runtime;

import java.util.List;

/**
 * Core-owned boundary that rebuilds the canonical initiative order for a new round.
 *
 * BattleRoundController owns when a rebuild happens. Implementations own how the order
 * is derived from authoritative PTU state. Minecraft/Cobblemon adapters must not supply
 * a client-computed order at rollover time.
 */
@FunctionalInterface
public interface InitiativeRoundRebuilder {
    /**
     * Preferred server-authoritative rebuilder. It derives the initiative assembly from
     * {@link BattleRuntimeState}, applies Python-derived cleanup requests, and installs
     * the canonical order before returning it to the lifecycle controller.
     */
    static InitiativeRoundRebuilder authoritative() {
        return AuthoritativeInitiativeRoundRebuilder.INSTANCE;
    }

    /** Return stable actor IDs in the exact initiative order for {@code round}. */
    List<String> rebuildOrder(BattleRuntimeState state, int round);
}
