package io.autoptu.core.runtime;

import java.util.Objects;

/**
 * Server-owned battle affiliation used by AI/action-space target selection.
 *
 * Minecraft/Cobblemon entity teams, scoreboards, passengers, or client packets are
 * presentation/runtime details and must not redefine PTU battle sides mid-battle.
 */
public final class CombatantAffiliationState {
    private final String teamId;
    private boolean active;

    public CombatantAffiliationState(String teamId, boolean active) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId is required");
        }
        this.teamId = teamId.strip();
        this.active = active;
    }

    public static CombatantAffiliationState active(String teamId) {
        return new CombatantAffiliationState(teamId, true);
    }

    public String teamId() {
        return teamId;
    }

    public boolean active() {
        return active;
    }

    /** Runtime-package mutation boundary for authoritative switch/replacement transitions. */
    void setActiveFromRuntime(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CombatantAffiliationState that)) return false;
        return active == that.active && teamId.equals(that.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, active);
    }

    @Override
    public String toString() {
        return "CombatantAffiliationState[teamId=" + teamId + ", active=" + active + "]";
    }
}
