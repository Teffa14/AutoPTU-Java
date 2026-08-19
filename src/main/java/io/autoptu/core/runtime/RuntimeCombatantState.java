package io.autoptu.core.runtime;

import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.Calculations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mutable server-authoritative state for one combatant.
 *
 * Minecraft entity position and combat metadata are projections of this state,
 * never sources of truth for battle resolution.
 */
public final class RuntimeCombatantState {
    private final String combatantId;
    private final int maxHp;
    private final ActionBudget actionBudget;
    private final CombatantStatProfile statProfile;
    private final CombatStageState combatStages;
    private final EvasionProfile evasionProfile;
    private final int accuracyStage;
    private final boolean sniper;
    private final boolean noGuard;
    private final boolean blur;
    private final MoveFrequencyUsage moveFrequencyUsage = new MoveFrequencyUsage();
    private final TemporaryEffectStore temporaryEffects = new TemporaryEffectStore();
    private MovementProfile movementProfile;
    private int hp;
    private boolean probabilityControl;
    private List<String> types = List.of();
    private List<AttackModifier> damageModifiers = List.of();
    private List<String> abilities = List.of();

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, null, null, 0, false, false, false, false);
    }

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, null, 0, false, false, false, false);
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
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, 0, false, false, false, false);
    }

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile,
            EvasionProfile evasionProfile,
            int accuracyStage
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, false, false, false, false);
    }

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile,
            EvasionProfile evasionProfile,
            int accuracyStage,
            boolean sniper
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, false, false, false);
    }

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile,
            EvasionProfile evasionProfile,
            int accuracyStage,
            boolean sniper,
            boolean noGuard
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, noGuard, false, false);
    }

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile,
            EvasionProfile evasionProfile,
            int accuracyStage,
            boolean sniper,
            boolean noGuard,
            boolean blur
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, noGuard, blur, false);
    }

    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile,
            EvasionProfile evasionProfile,
            int accuracyStage,
            boolean sniper,
            boolean noGuard,
            boolean blur,
            boolean probabilityControl
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
        this.combatStages = new CombatStageState(statProfile == null ? Map.of() : statProfile.stages());
        this.evasionProfile = evasionProfile;
        this.accuracyStage = Calculations.accuracyStageValue(accuracyStage);
        this.sniper = sniper;
        this.noGuard = noGuard;
        this.blur = blur;
        this.probabilityControl = probabilityControl;
    }

    /**
     * Full authoritative constructor used by the Minecraft/Cobblemon adapter when
     * materializing battle state from trusted content. Type names preserve Python
     * oracle casing because PTU type lookup is intentionally case-sensitive.
     */
    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile,
            EvasionProfile evasionProfile,
            int accuracyStage,
            boolean sniper,
            boolean noGuard,
            boolean blur,
            boolean probabilityControl,
            List<String> types
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile,
                accuracyStage, sniper, noGuard, blur, probabilityControl);
        this.types = normalizeNames(types);
    }

    /**
     * Transitional authoritative constructor for pre-damage AttackContext modifiers.
     * The list is server-owned and copied defensively. Hook registries can replace
     * this resolved projection without changing the Minecraft-facing move boundary.
     */
    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile,
            EvasionProfile evasionProfile,
            int accuracyStage,
            boolean sniper,
            boolean noGuard,
            boolean blur,
            boolean probabilityControl,
            List<String> types,
            List<AttackModifier> damageModifiers
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile,
                accuracyStage, sniper, noGuard, blur, probabilityControl, types,
                damageModifiers, List.of());
    }

    /**
     * Full current combatant snapshot including canonical ability identities.
     * Ability hooks read this server-owned list; clients may render abilities but
     * cannot grant one by naming it in an action request.
     */
    public RuntimeCombatantState(
            String combatantId,
            MovementProfile movementProfile,
            int hp,
            int maxHp,
            ActionBudget actionBudget,
            CombatantStatProfile statProfile,
            EvasionProfile evasionProfile,
            int accuracyStage,
            boolean sniper,
            boolean noGuard,
            boolean blur,
            boolean probabilityControl,
            List<String> types,
            List<AttackModifier> damageModifiers,
            List<String> abilities
    ) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile,
                accuracyStage, sniper, noGuard, blur, probabilityControl, types);
        this.damageModifiers = normalizeDamageModifiers(damageModifiers);
        this.abilities = normalizeNames(abilities);
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

    public MoveFrequencyUsage moveFrequencyUsage() {
        return moveFrequencyUsage;
    }

    /** Server-owned temporary effects, including multiplicity for repeated Python entries. */
    public TemporaryEffectStore temporaryEffects() {
        return temporaryEffects;
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

    /** Mutable canonical PTU combat stages used by moves, statuses, abilities, items, and Features. */
    public CombatStageState combatStages() {
        return combatStages;
    }

    /** Pure stat profile rebound to the current canonical combat stages. */
    public CombatantStatProfile effectiveStatProfile() {
        return requireStatProfile().withStages(combatStages.snapshot());
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

    public int accuracyStage() {
        return accuracyStage;
    }

    public boolean sniper() {
        return sniper;
    }

    public boolean noGuard() {
        return noGuard;
    }

    public boolean blur() {
        return blur;
    }

    public boolean probabilityControl() {
        return probabilityControl;
    }

    public List<String> types() {
        return types;
    }

    public List<AttackModifier> damageModifiers() {
        return damageModifiers;
    }

    public List<String> abilities() {
        return abilities;
    }

    public boolean hasAbilityExact(String abilityName) {
        if (abilityName == null || abilityName.isBlank()) return false;
        String target = abilityName.strip().toLowerCase(Locale.ROOT);
        for (String ability : abilities) {
            if (ability.toLowerCase(Locale.ROOT).equals(target)) return true;
        }
        return false;
    }

    boolean consumeProbabilityControl() {
        if (!probabilityControl) {
            return false;
        }
        probabilityControl = false;
        return true;
    }

    void moveTo(GridCoord destination) {
        movementProfile = movementProfile.withPosition(destination);
    }

    void setHp(int nextHp) {
        hp = Math.max(0, Math.min(maxHp, nextHp));
    }

    private static List<String> normalizeNames(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.strip());
        }
        return List.copyOf(normalized);
    }

    private static List<AttackModifier> normalizeDamageModifiers(List<AttackModifier> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<AttackModifier> normalized = new ArrayList<>(values.size());
        for (AttackModifier value : values) {
            if (value != null) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }
}
