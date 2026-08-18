package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;

/**
 * Mutable server-authoritative state for one combatant.
 *
 * Minecraft entity position is a projection of this state, never its source of truth.
 */
public final class RuntimeCombatantState {
    private final String combatantId;
    private final int maxHp;
    private final ActionBudget actionBudget;
    private final CombatantStatProfile statProfile;
    private final EvasionProfile evasionProfile;
    private MovementProfile movementProfile;
    private int hp;

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, null, null);
    }

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, null);
    }

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile,
            EvasionProfile evasionProfile
    ) {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        if (movementProfile == null) {
            throw new IllegalArgumentException("movementProfile is required");
        }
        if (maxHp <= 0) {
            throw new IllegalArgumentException("maxHp must be positive");
        }
        if (hp < 0 || hp > maxHp) {
            throw new IllegalArgumentException("hp must be between 0 and maxHp");
        }
        if (actionBudget == null) {
            throw new IllegalArgumentException("actionBudget is required");
        }
        this.combatantId = combatantId;
        this.movementProfile = movementProfile;
        this.hp = hp;
        this.maxHp = maxHp;
        this.actionBudget = actionBudget;
        this.statProfile = statProfile;
        this.evasionProfile = evasionProfile;
    }

    public String combatantId() {
        return combatantId;
    }

    public GridCoord position() {
        return movementProfile.position();
    }

    public MovementProfile movementProfile() {
        return movementProfile;
    }

    public int hp() {
        return hp;
    }

    public int maxHp() {
        return maxHp;
    }

    public ActionBudget actionBudget() {
        return actionBudget;
    }

    public boolean hasStatProfile() {
        return statProfile != null;
    }

    public CombatantStatProfile requireStatProfile() {
        if (statProfile == null) {
            throw new IllegalStateException("combatant " + combatantId + " has no stat profile");
        }
        return statProfile;
    }

    public boolean hasEvasionProfile() {
        return evasionProfile != null;
    }

    public EvasionProfile requireEvasionProfile() {
        if (evasionProfile == null) {
            throw new IllegalStateException("combatant " + combatantId + " has no evasion profile");
        }
        return evasionProfile;
    }

    void moveTo(GridCoord destination) {
        movementProfile = movementProfile.withPosition(destination);
    }

    void setHp(int nextHp) {
        hp = Math.max(0, Math.min(maxHp, nextHp));
    }
}
