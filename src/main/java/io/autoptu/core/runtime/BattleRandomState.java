package io.autoptu.core.runtime;

import io.autoptu.core.random.PythonRandom;

/**
 * Owns the single mutable Python-compatible RNG stream for one battle.
 *
 * The RNG accessor is intentionally package-private. Runtime code may consume the stream,
 * while adapters such as Minecraft/Cobblemon cannot request rolls or advance the stream
 * through the public battle contract.
 */
public final class BattleRandomState {
    private final PythonRandom random;

    public BattleRandomState(long seed) {
        this.random = new PythonRandom(seed);
    }

    PythonRandom random() {
        return random;
    }
}
