package io.autoptu.core.runtime;

import io.autoptu.core.rules.InitiativeOrderAssemblyResult;

import java.util.List;

/**
 * Canonical initiative rollover implementation derived entirely from server-owned battle state.
 *
 * The Python oracle rebuilds initiative from battle state during round start. This adapter composes
 * the already parity-tested runtime projection, initiative assembly, cleanup, and installation
 * boundaries without accepting a precomputed order from Minecraft/Cobblemon.
 */
final class AuthoritativeInitiativeRoundRebuilder implements InitiativeRoundRebuilder {
    static final AuthoritativeInitiativeRoundRebuilder INSTANCE = new AuthoritativeInitiativeRoundRebuilder();

    private AuthoritativeInitiativeRoundRebuilder() {
    }

    @Override
    public List<String> rebuildOrder(BattleRuntimeState state, int round) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (round < 0) {
            throw new IllegalArgumentException("round cannot be negative");
        }
        if (state.currentRound() != round) {
            throw new IllegalStateException(
                    "initiative rebuild round does not match canonical state: requested="
                            + round + ", state=" + state.currentRound()
            );
        }

        InitiativeOrderAssemblyResult assembly = RuntimeInitiativeOrderAssembly.fromState(state);
        return InitiativeAssemblyInstaller.install(state, assembly);
    }
}
