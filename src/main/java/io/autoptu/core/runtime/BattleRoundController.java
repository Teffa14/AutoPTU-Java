package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.PhaseChangedEvent;
import io.autoptu.core.event.TurnEndedEvent;
import io.autoptu.core.event.TurnStartedEvent;
import io.autoptu.core.hook.BuiltinLifecycleHooks;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.LifecycleHookResult;
import io.autoptu.core.hook.PendingStatusSkipRequest;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.PhaseSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        this(state, initialRound, lifecycleHooks, canonicalDamageHistory(state), canonicalInjuryHistory(state));
    }
    public BattleRoundController(BattleRuntimeState state, int initialRound, LifecycleHookRegistry lifecycleHooks, RoundDamageHistoryState damageHistory) {
        this(state, initialRound, lifecycleHooks, damageHistory, canonicalInjuryHistory(state));
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
        state.syncCurrentRoundFromLifecycle(initialRound);
    }

    public int round() { return round; }
    public RoundDamageHistoryState damageHistory() { return damageHistory; }
    public RoundInjuryHistoryState injuryHistory() { return injuryHistory; }
    public BattleTurnState turnState() { return turnState; }
    public InitiativeProgressState initiativeProgress() { return state.initiativeProgress(); }

    /** Server lifecycle boundary for a freshly rebuilt deterministic initiative order. */
    public void replaceInitiativeOrder(List<String> orderedActorIds) {
        state.initiativeProgress().replaceOrderFromLifecycle(orderedActorIds);
    }

    /** Server lifecycle boundary for the current Python-compatible initiative cursor. */
    public void setInitiativeCursor(int cursor) {
        state.initiativeProgress().setCursorFromLifecycle(cursor);
    }

    /**
     * Advance the canonical initiative cursor to the next active, conscious combatant in
     * the current round and open that combatant's START phase.
     *
     * Python increments _initiative_index before reading a slot, skips invalid/inactive
     * combatants, resets only actions_taken for the selected combatant, then assigns the
     * current actor and START phase. Before returning the decision window Python runs the
     * START phase effects and consumes any resulting pending status skip. Java mirrors that
     * order through the generic TURN_START lifecycle seam. Automatic round rollover remains
     * on the explicit core-owned rebuild boundary below because the complete Python
     * initiative-entry formula is not yet ported.
     */
    public InitiativeTurnAdvanceResult advanceInitiativeTurn() {
        if (turnState.currentActorId() != null) {
            throw new IllegalStateException("End the active turn before advancing initiative.");
        }

        InitiativeProgressState progress = state.initiativeProgress();
        List<String> order = progress.orderedActorIds();
        Map<String, RuntimeCombatantState> combatants = state.combatants();
        int candidateIndex = progress.cursor() + 1;

        while (candidateIndex < order.size()) {
            progress.setCursorFromLifecycle(candidateIndex);
            String actorId = order.get(candidateIndex);
            RuntimeCombatantState actor = combatants.get(actorId);
            candidateIndex += 1;

            if (actor == null || actor.hp() <= 0 || !state.isActive(actorId)) {
                continue;
            }

            actor.actionBudget().resetConsumedActions();
            turnState.beginTurn(actorId);
            ArrayList<BattleEvent> events = new ArrayList<>();
            events.add(new TurnStartedEvent(
                    actorId,
                    round,
                    TurnPhase.START,
                    progress.cursor()
            ));

            LifecycleHookResult startResult = lifecycleHooks.resolve(
                    LifecycleHookPoint.TURN_START,
                    new LifecycleHookContext(
                            state,
                            damageHistory,
                            injuryHistory,
                            LifecycleHookPoint.TURN_START,
                            round,
                            round,
                            actorId,
                            TurnPhase.START
                    )
            );
            events.addAll(startResult.events());
            PendingStatusSkipRequest pending = startResult.pendingStatusSkip();
            if (pending != null) {
                events.addAll(BattleRuntime.applyStatusSkip(
                        state,
                        actorId,
                        pending.status(),
                        pending.phase(),
                        pending.reason()
                ).events());
            }
            return InitiativeTurnAdvanceResult.actor(
                    actorId,
                    progress.cursor(),
                    List.copyOf(events)
            );
        }

        progress.setCursorFromLifecycle(order.size());
        return InitiativeTurnAdvanceResult.exhausted(order.size());
    }

    /**
     * Python advance_turn() calls start_round() when the initiative cursor reaches the end,
     * rebuilds initiative as part of that round transition, and then continues selecting the
     * next actor. This boundary mirrors that lifecycle without pretending the full Python
     * initiative formula is already available in Java.
     *
     * The rebuilder is a core rules service. It receives only authoritative BattleRuntimeState
     * after ROUND_START has completed. The current bounded contract accepts combatant IDs only;
     * trainer initiative slots and special initiative entries remain explicit follow-up work.
     */
    public InitiativeTurnAdvanceResult advanceInitiativeTurnWithRollover(
            InitiativeRoundRebuilder rebuilder
    ) {
        Objects.requireNonNull(rebuilder, "rebuilder");
        if (turnState.currentActorId() != null) {
            throw new IllegalStateException("End the active turn before advancing initiative.");
        }

        ArrayList<BattleEvent> accumulatedEvents = new ArrayList<>();
        InitiativeTurnAdvanceResult current = advanceInitiativeTurn();
        accumulatedEvents.addAll(current.events());
        if (current.hasActor()) {
            return InitiativeTurnAdvanceResult.actor(
                    current.actorId(),
                    current.initiativeIndex(),
                    accumulatedEvents
            );
        }

        RoundStartResult roundStart = startRoundWithEvents();
        accumulatedEvents.addAll(roundStart.events());
        List<String> rebuiltOrder = rebuilder.rebuildOrder(state, round);
        if (rebuiltOrder == null) {
            throw new IllegalStateException("initiative rebuilder returned null order");
        }

        for (String actorId : rebuiltOrder) {
            if (actorId == null || actorId.isBlank()) {
                throw new IllegalArgumentException("initiative rebuilder returned blank actor id");
            }
            state.requireCombatant(actorId.strip());
        }
        replaceInitiativeOrder(rebuiltOrder);

        if (rebuiltOrder.isEmpty()) {
            return InitiativeTurnAdvanceResult.exhausted(-1, accumulatedEvents);
        }

        InitiativeTurnAdvanceResult nextRound = advanceInitiativeTurn();
        accumulatedEvents.addAll(nextRound.events());
        if (nextRound.hasActor()) {
            return InitiativeTurnAdvanceResult.actor(
                    nextRound.actorId(),
                    nextRound.initiativeIndex(),
                    accumulatedEvents
            );
        }
        return InitiativeTurnAdvanceResult.exhausted(
                nextRound.initiativeIndex(),
                accumulatedEvents
        );
    }

    public void beginTurn(String actorId) {
        state.requireCombatant(actorId);
        turnState.beginTurn(actorId);
    }

    /** Transitional direct setter; adapters should prefer advancePhase(). */
    public void setPhase(TurnPhase phase) { turnState.setPhase(phase); }

    /**
     * Advance START -> COMMAND -> ACTION -> END using the server-owned actor and phase.
     * END is terminal until endTurn() clears the turn. The semantic phase event is
     * emitted before PHASE_CHANGE hook events. A hook may also emit one pending status
     * skip request; it is resolved only after every phase hook has run, matching Python
     * PhaseController's run_phase_effects() -> consume_pending_status_skip() order.
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
        PendingStatusSkipRequest pending = hookResult.pendingStatusSkip();
        if (pending != null) {
            events.addAll(BattleRuntime.applyStatusSkip(
                    state,
                    actorId,
                    pending.status(),
                    pending.phase(),
                    pending.reason()
            ).events());
        }
        return List.copyOf(events);
    }

    public int startRound() { return startRoundWithEvents().round(); }

    public RoundStartResult startRoundWithEvents() {
        int previousRound = round;
        round += 1;
        state.syncCurrentRoundFromLifecycle(round);
        state.initiativeProgress().resetCursorFromLifecycle();
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

    private static RoundInjuryHistoryState canonicalInjuryHistory(BattleRuntimeState state) {
        if (state == null) throw new IllegalArgumentException("state is required");
        return state.injuryHistory();
    }
}
