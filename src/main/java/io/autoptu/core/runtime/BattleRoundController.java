package io.autoptu.core.runtime;

import io.autoptu.core.hook.BuiltinLifecycleHooks;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.LifecycleHookResult;

import java.util.Objects;

/**
 * Server-owned round lifecycle for the headless battle runtime.
 *
 * Python increments the battle round and runs a sequence of round-start rule
 * families before play continues. Minecraft/Cobblemon must never drive those
 * transitions independently. The ordered lifecycle registry is the seam for
 * porting terrain, zones, delayed hits, temporary expiry, abilities and Trainer
 * Features without growing this controller into another battle-state monolith.
 */
public final class BattleRoundController {
    private final BattleRuntimeState state;
    private final LifecycleHookRegistry lifecycleHooks;
    private int round;

    public BattleRoundController(BattleRuntimeState state) {
        this(state, 0, BuiltinLifecycleHooks.registry());
    }

    public BattleRoundController(BattleRuntimeState state, int initialRound) {
        this(state, initialRound, BuiltinLifecycleHooks.registry());
    }

    public BattleRoundController(
            BattleRuntimeState state,
            int initialRound,
            LifecycleHookRegistry lifecycleHooks
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (initialRound < 0) throw new IllegalArgumentException("initialRound cannot be negative");
        this.state = state;
        this.round = initialRound;
        this.lifecycleHooks = Objects.requireNonNull(lifecycleHooks, "lifecycleHooks");
    }

    public int round() {
        return round;
    }

    /** Backwards-compatible round transition for callers that do not consume events yet. */
    public int startRound() {
        return startRoundWithEvents().round();
    }

    /**
     * Begin the next authoritative round and return ordered semantic playback events.
     *
     * Python does not globally reset Pokemon action buckets at round start. That
     * remains a per-combatant turn responsibility. The current built-in lifecycle
     * hook clears only round-scoped move frequency; later parity slices can add
     * terrain, room, delayed-hit, temporary-effect and trigger families in Python order.
     */
    public RoundStartResult startRoundWithEvents() {
        int previousRound = round;
        round += 1;
        LifecycleHookResult result = lifecycleHooks.resolve(
                LifecycleHookPoint.ROUND_START,
                new LifecycleHookContext(
                        state,
                        LifecycleHookPoint.ROUND_START,
                        previousRound,
                        round,
                        ""
                )
        );
        return new RoundStartResult(round, result.events());
    }
}
