package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.TurnEndedEvent;
import io.autoptu.core.hook.BuiltinLifecycleHooks;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.LifecycleHookResult;
import io.autoptu.core.model.TurnPhase;

import java.util.ArrayList;
import java.util.List;
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
    private final RoundDamageHistoryState damageHistory;
    private final RoundInjuryHistoryState injuryHistory;
    private final BattleTurnState turnState;
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
        this(
                state,
                initialRound,
                lifecycleHooks,
                canonicalDamageHistory(state),
                new RoundInjuryHistoryState()
        );
    }

    public BattleRoundController(
            BattleRuntimeState state,
            int initialRound,
            LifecycleHookRegistry lifecycleHooks,
            RoundDamageHistoryState damageHistory
    ) {
        this(state, initialRound, lifecycleHooks, damageHistory, new RoundInjuryHistoryState());
    }

    public BattleRoundController(
            BattleRuntimeState state,
            int initialRound,
            LifecycleHookRegistry lifecycleHooks,
            RoundDamageHistoryState damageHistory,
            RoundInjuryHistoryState injuryHistory
    ) {
        this(state, initialRound, lifecycleHooks, damageHistory, injuryHistory, new BattleTurnState());
    }

    public BattleRoundController(
            BattleRuntimeState state,
            int initialRound,
            LifecycleHookRegistry lifecycleHooks,
            RoundDamageHistoryState damageHistory,
            RoundInjuryHistoryState injuryHistory,
            BattleTurnState turnState
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (initialRound < 0) throw new IllegalArgumentException("initialRound cannot be negative");
        this.state = state;
        this.round = initialRound;
        this.lifecycleHooks = Objects.requireNonNull(lifecycleHooks, "lifecycleHooks");
        this.damageHistory = Objects.requireNonNull(damageHistory, "damageHistory");
        this.injuryHistory = Objects.requireNonNull(injuryHistory, "injuryHistory");
        this.turnState = Objects.requireNonNull(turnState, "turnState");
        if (turnState.currentActorId() != null) {
            state.requireCombatant(turnState.currentActorId());
        }
    }

    public int round() {
        return round;
    }

    /** Server-owned damage history shared by move resolution and lifecycle hooks. */
    public RoundDamageHistoryState damageHistory() {
        return damageHistory;
    }

    /** Server-owned injury history shared by lifecycle hooks and downstream rule families. */
    public RoundInjuryHistoryState injuryHistory() {
        return injuryHistory;
    }

    /** Server-owned active actor and phase pointer. */
    public BattleTurnState turnState() {
        return turnState;
    }

    /** Begin an authoritative combatant turn at Python's START phase. */
    public void beginTurn(String actorId) {
        state.requireCombatant(actorId);
        turnState.beginTurn(actorId);
    }

    /**
     * Advance the server-owned phase pointer without exposing mutable state to adapters.
     * Phase effects themselves remain separate bounded lifecycle slices.
     */
    public void setPhase(TurnPhase phase) {
        turnState.setPhase(phase);
    }

    /** Backwards-compatible round transition for callers that do not consume events yet. */
    public int startRound() {
        return startRoundWithEvents().round();
    }

    /**
     * Begin the next authoritative round and return ordered semantic playback events.
     *
     * Python does not globally reset Pokemon action buckets at round start. That
     * remains a per-combatant turn responsibility. Ordered lifecycle hooks own
     * round-scoped resets, temporary expiry, history rotation and later terrain,
     * delayed-hit and trigger families in Python order.
     */
    public RoundStartResult startRoundWithEvents() {
        int previousRound = round;
        round += 1;
        LifecycleHookResult result = lifecycleHooks.resolve(
                LifecycleHookPoint.ROUND_START,
                new LifecycleHookContext(
                        state,
                        damageHistory,
                        injuryHistory,
                        LifecycleHookPoint.ROUND_START,
                        previousRound,
                        round,
                        ""
                )
        );
        // Python start_round always re-enters START with no active combatant.
        turnState.clearToStart();
        return new RoundStartResult(round, result.events());
    }

    /**
     * Close the currently active combatant turn using only server-owned identity/phase.
     *
     * Python clears actor-scoped temporary state, logs turn_end, then clears
     * current_actor_id and resets phase to START. The preferred Minecraft-facing
     * boundary therefore has no actor or phase parameters that an adapter can spoof.
     */
    public List<BattleEvent> endTurn() {
        String actorId = turnState.currentActorId();
        if (actorId == null) return List.of();
        TurnPhase phase = turnState.phase();
        state.requireCombatant(actorId);

        LifecycleHookResult result = lifecycleHooks.resolve(
                LifecycleHookPoint.TURN_END,
                new LifecycleHookContext(
                        state,
                        damageHistory,
                        injuryHistory,
                        LifecycleHookPoint.TURN_END,
                        round,
                        round,
                        actorId
                )
        );
        ArrayList<BattleEvent> events = new ArrayList<>(result.events().size() + 1);
        events.addAll(result.events());
        events.add(new TurnEndedEvent(actorId, round, phase));
        turnState.clearToStart();
        return List.copyOf(events);
    }

    /**
     * Transitional compatibility boundary for older callers/tests.
     * New adapters should seed/advance the authoritative turn state and call endTurn().
     */
    public List<BattleEvent> endTurn(String actorId, TurnPhase phase) {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (phase == null) throw new IllegalArgumentException("phase is required");
        state.requireCombatant(actorId);
        turnState.setActiveTurn(actorId, phase);
        return endTurn();
    }

    private static RoundDamageHistoryState canonicalDamageHistory(BattleRuntimeState state) {
        if (state == null) throw new IllegalArgumentException("state is required");
        return state.damageHistory();
    }
}
