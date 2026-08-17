package io.autoptu.core.runtime;

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
    private MovementProfile movementProfile;
    private int hp;

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget
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

    void moveTo(GridCoord destination) {
        movementProfile = movementProfile.withPosition(destination);
    }

    void setHp(int nextHp) {
        hp = Math.max(0, Math.min(maxHp, nextHp));
    }
}
