package io.autoptu.core.runtime;

/**
 * Server-owned battle affiliation used by AI/action-space target selection.
 *
 * Minecraft/Cobblemon entity teams, scoreboards, passengers, or client packets are
 * presentation/runtime details and must not redefine PTU battle sides mid-battle.
 */
public record CombatantAffiliationState(String teamId, boolean active) {
    public CombatantAffiliationState {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId is required");
        }
        teamId = teamId.strip();
    }

    public static CombatantAffiliationState active(String teamId) {
        return new CombatantAffiliationState(teamId, true);
    }
}
