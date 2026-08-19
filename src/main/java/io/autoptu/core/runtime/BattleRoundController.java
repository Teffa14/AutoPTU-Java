package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.PhaseChangedEvent;
import io.autoptu.core.event.TurnEndedEvent;
import io.autoptu.core.hook.BuiltinLifecycleHooks;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.LifecycleHookResult;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.PhaseSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Server-owned round and turn-phase lifecycle for the headless battle runtime. */
public final class BattleRoundController {
    private final BattleRuntimeState state;
    private final LifecycleHookRegistry lifecycleHooks;
    private final RoundDamageHistoryState damageHistory;
    private final RoundInjuryHistoryState injuryHistory;
    private final BattleTurnState turnState;
    private int round;

    public BattleRoundController(BattleRuntimeState state) { this(state, 0, BuiltinLifecycleHooks.registry()); }
    public BattleRoundController(BattleRuntimeState state, int initialRound) { this(state, initialRound, BuiltinLifecycleHooks.registry()); }
    public BattleRoundController(BattleRuntimeState state, int initialRound, LifecycleHookRegistry lifecycleHooks) {
        this(state, initialRound, lifecycleHooks, canonicalDamageHistory(state), new RoundInjuryHistoryState());
    }
    public BattleRoundController(BattleRuntimeState state, int initialRound, LifecycleHookRegistry lifecycleHooks, RoundDamageHistoryState damageHistory) {
        this(state, initialRound, lifecycleHooks, damageHistory, new RoundInjuryHistoryState());
    }
    public BattleRoundController(BattleRuntimeState state, int initialRound, LifecycleHookRegistry lifecycleHooks, RoundDamageHistoryState damageHistory, RoundInjuryHistoryState injuryHistory) {
        this(state, initialRound, lifecycleHooks, damageHistory, injuryHistory, new BattleTurnState());
    }
    public BattleRoundController(BattleRuntimeState state, int initialRound, LifecycleHookRegistry lifecycleHooks, RoundDamageHistoryState damageHistory, RoundInjuryHistoryState injuryHistory, BattleTurnState turnState) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (initialRound < 0) throw new IllegalArgumentException("initialRound cannot be negative");
        this.state = state;
        this.round = initialRound;
        this.lifecycleHooks = Objects.requireNonNull(lifecycleHooks, "lifecycleHooks");
        this.damageHistory = Objects.requireNonNull(damageHistory, "damageHistory");
        this.injuryHistory = Objects.requireNonNull(injuryHistory, "injuryHistory");
        this.turnState = Objects.requireNonNull(turnState, "turnState");
        if (turnState.currentActorId() != null) state.requireCombatant(turnState.currentActorId());
    }

    public int round() { return round; }
    public RoundDamageHistoryState damageHistory() { return damageHistory; }
    public RoundInjuryHistoryState injuryHistory() { return injuryHistory; }
    public BattleTurnState turnState() { return turnState; }

    public void beginTurn(String actorId) {
        state.requireCombatant(actorId);
        turnState.beginTurn(actorId);
    }

    /** Transitional direct setter; adapters should prefer advancePhase(). */
    public void setPhase(TurnPhase phase) { turnState.setPhase(phase); }

    /**
     * Advance START -> COMMAND -> ACTION -> END using the server-owned actor and phase.
     * END is terminal until endTurn() clears the turn. The semantic phase event is
     * emitted before PHASE_CHANGE hook events, matching Python PhaseController order.
     */
    public List<BattleEvent> advancePhase() {
        String actorId = turnState.currentActorId();
        if (actorId == null) throw new IllegalStateException("No active combatant to advance phase for.");
        state.requireCombatant(actorId);
        TurnPhase current = turnState.phase();
        TurnPhase next = PhaseSequence.next(current);
        if (next == current) return List.of();

        turnState.setPhase(next);
        ArrayList<BattleEvent> events = new ArrayList<>();
        events.add(new PhaseChangedEvent(actorId, round, next));
        LifecycleHookResult hookResult = lifecycleHooks.resolve(
                LifecycleHookPoint.PHASE_CHANGE,
                new LifecycleHookContext(
                        state,
                        damageHistory,
                        injuryHistory,
                        LifecycleHookPoint.PHASE_CHANGE,
                        round,
                        round,
                        actorId,
                        next
                )
        );
        events.addAll(hookResult.events());
        return List.copyOf(events);
    }

    public int startRound() { return startRoundWithEvents().round(); }

    public RoundStartResult startRoundWithEvents() {
        int previousRound = round;
        round += 1;
        LifecycleHookResult result = lifecycleHooks.resolve(
                LifecycleHookPoint.ROUND_START,
                new LifecycleHookContext(state, damageHistory, injuryHistory, LifecycleHookPoint.ROUND_START, previousRound, round, "")
        );
        turnState.clearToStart();
        return new RoundStartResult(round, result.events());
    }

    public List<BattleEvent> endTurn() {
        String actorId = turnState.currentActorId();
        if (actorId == null) return List.of();
        TurnPhase phase = turnState.phase();
        state.requireCombatant(actorId);
        LifecycleHookResult result = lifecycleHooks.resolve(
                LifecycleHookPoint.TURN_END,
                new LifecycleHookContext(state, damageHistory, injuryHistory, LifecycleHookPoint.TURN_END, round, round, actorId)
        );
        ArrayList<BattleEvent> events = new ArrayList<>(result.events().size() + 1);
        events.addAll(result.events());
        events.add(new TurnEndedEvent(actorId, round, phase));
        turnState.clearToStart();
        return List.copyOf(events);
    }

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
