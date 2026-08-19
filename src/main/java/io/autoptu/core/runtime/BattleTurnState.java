package io.autoptu.core.runtime;

import io.autoptu.core.model.TurnPhase;

/**
 * Server-owned pointer for the active combatant and current PTU turn phase.
 *
 * Minecraft/Cobblemon may render this state, but the battle core owns all
 * transitions. A cleared turn always returns to the Python-compatible
 * {@link TurnPhase#START} phase.
 */
public final class BattleTurnState {
    private String currentActorId;
    private TurnPhase phase;

    public BattleTurnState() {
        this(null, TurnPhase.START);
    }

    public BattleTurnState(String currentActorId, TurnPhase phase) {
        if (phase == null) throw new IllegalArgumentException("phase is required");
        if (currentActorId == null || currentActorId.isBlank()) {
            if (phase != TurnPhase.START) {
                throw new IllegalArgumentException("an inactive turn must be in START phase");
            }
            this.currentActorId = null;
            this.phase = TurnPhase.START;
            return;
        }
        this.currentActorId = currentActorId;
        this.phase = phase;
    }

    public String currentActorId() {
        return currentActorId;
    }

    public TurnPhase phase() {
        return phase;
    }

    public boolean hasCurrentActor() {
        return currentActorId != null;
    }

    public void beginTurn(String actorId) {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        currentActorId = actorId;
        phase = TurnPhase.START;
    }

    public void setPhase(TurnPhase phase) {
        if (phase == null) throw new IllegalArgumentException("phase is required");
        if (currentActorId == null) throw new IllegalStateException("cannot set phase without an active combatant");
        this.phase = phase;
    }

    void setActiveTurn(String actorId, TurnPhase phase) {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (phase == null) throw new IllegalArgumentException("phase is required");
        currentActorId = actorId;
        this.phase = phase;
    }

    public void clearToStart() {
        currentActorId = null;
        phase = TurnPhase.START;
    }
}
