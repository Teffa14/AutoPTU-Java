package io.autoptu.core.runtime;

/**
 * Server-owned round lifecycle for the headless battle runtime.
 *
 * Python increments the battle round and clears round-scoped usage in start_round(),
 * while Pokemon action buckets are reset when that combatant's next turn begins.
 * Minecraft/Cobblemon must therefore never drive either lifecycle independently.
 */
public final class BattleRoundController {
    private final BattleRuntimeState state;
    private int round;

    public BattleRoundController(BattleRuntimeState state) {
        this(state, 0);
    }

    public BattleRoundController(BattleRuntimeState state, int initialRound) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (initialRound < 0) throw new IllegalArgumentException("initialRound cannot be negative");
        this.state = state;
        this.round = initialRound;
    }

    public int round() {
        return round;
    }

    /**
     * Begin the next authoritative round.
     *
     * Only round-scoped move usage is reset here. Combatant action budgets remain
     * untouched because Python resets Pokemon actions at the beginning of that
     * combatant's next turn, not globally at round start.
     */
    public int startRound() {
        round += 1;
        BattleRuntime.resetRoundMoveFrequency(state);
        return round;
    }
}
