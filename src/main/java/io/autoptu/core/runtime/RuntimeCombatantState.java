package io.autoptu.core.runtime;

import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;

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
    private final boolean sniper;
    private final boolean noGuard;
    private final boolean blur;
    private final MoveFrequencyUsage moveFrequencyUsage = new MoveFrequencyUsage();
    private final TemporaryEffectStore temporaryEffects = new TemporaryEffectStore();
    private MovementProfile movementProfile;
    private int hp;
    private int tempHp;
    private boolean probabilityControl;
    private boolean abilitiesSuppressed;
    private List<String> types = List.of();
    private List<AttackModifier> damageModifiers = List.of();
    private List<String> abilities = List.of();
    private CombatantProfileIdentity profileIdentity;

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, null, null, 0, false, false, false, false);
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, null, 0, false, false, false, false);
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, 0, false, false, false, false);
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile, int accuracyStage) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, false, false, false, false);
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile, int accuracyStage, boolean sniper) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, false, false, false);
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile, int accuracyStage, boolean sniper, boolean noGuard) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, noGuard, false, false);
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile, int accuracyStage, boolean sniper, boolean noGuard, boolean blur) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, noGuard, blur, false);
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile, int accuracyStage, boolean sniper, boolean noGuard, boolean blur, boolean probabilityControl) {
        if (combatantId == null || combatantId.isBlank()) throw new IllegalArgumentException("combatantId is required");
        if (movementProfile == null) throw new IllegalArgumentException("movementProfile is required");
        if (maxHp <= 0) throw new IllegalArgumentException("maxHp must be positive");
        if (hp < 0 || hp > maxHp) throw new IllegalArgumentException("hp must be between 0 and maxHp");
        if (actionBudget == null) throw new IllegalArgumentException("actionBudget is required");
        this.combatantId = combatantId;
        this.profileIdentity = CombatantProfileIdentity.fromCombatantId(combatantId);
        this.movementProfile = movementProfile;
        this.hp = hp;
        this.maxHp = maxHp;
        this.actionBudget = actionBudget;
        this.statProfile = statProfile;
        this.combatStages = new CombatStageState(statProfile == null ? Map.of() : statProfile.stages());
        this.combatStages.set(CombatStageStat.ACCURACY, accuracyStage);
        this.evasionProfile = evasionProfile;
        this.sniper = sniper;
        this.noGuard = noGuard;
        this.blur = blur;
        this.probabilityControl = probabilityControl;
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile, int accuracyStage, boolean sniper, boolean noGuard, boolean blur, boolean probabilityControl, List<String> types) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, noGuard, blur, probabilityControl);
        this.types = normalizeNames(types);
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile, int accuracyStage, boolean sniper, boolean noGuard, boolean blur, boolean probabilityControl, List<String> types, List<AttackModifier> damageModifiers) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, noGuard, blur, probabilityControl, types, damageModifiers, List.of());
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile, int accuracyStage, boolean sniper, boolean noGuard, boolean blur, boolean probabilityControl, List<String> types, List<AttackModifier> damageModifiers, List<String> abilities) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, noGuard, blur, probabilityControl, types);
        this.damageModifiers = normalizeDamageModifiers(damageModifiers);
        this.abilities = normalizeNames(abilities);
    }

    public RuntimeCombatantState(String combatantId, MovementProfile movementProfile, int hp, int maxHp, ActionBudget actionBudget, CombatantStatProfile statProfile, EvasionProfile evasionProfile, int accuracyStage, boolean sniper, boolean noGuard, boolean blur, boolean probabilityControl, List<String> types, List<AttackModifier> damageModifiers, List<String> abilities, CombatantProfileIdentity profileIdentity) {
        this(combatantId, movementProfile, hp, maxHp, actionBudget, statProfile, evasionProfile, accuracyStage, sniper, noGuard, blur, probabilityControl, types, damageModifiers, abilities);
        this.profileIdentity = profileIdentity == null ? CombatantProfileIdentity.fromCombatantId(combatantId) : profileIdentity;
    }

    public String combatantId() { return combatantId; }
    public CombatantProfileIdentity profileIdentity() { return profileIdentity; }
    public GridCoord position() { return movementProfile.position(); }
    public MovementProfile movementProfile() { return movementProfile; }
    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public int tempHp() { return tempHp; }
    public ActionBudget actionBudget() { return actionBudget; }
    public MoveFrequencyUsage moveFrequencyUsage() { return moveFrequencyUsage; }
    public TemporaryEffectStore temporaryEffects() { return temporaryEffects; }
    public boolean hasStatProfile() { return statProfile != null; }
    public CombatantStatProfile requireStatProfile() {
        if (statProfile == null) throw new IllegalStateException("combatant " + combatantId + " has no stat profile");
        return statProfile;
    }
    public CombatStageState combatStages() { return combatStages; }
    public CombatantStatProfile effectiveStatProfile() { return requireStatProfile().withStages(combatStages.snapshot()); }
    public boolean hasEvasionProfile() { return evasionProfile != null; }
    public EvasionProfile requireEvasionProfile() {
        if (evasionProfile == null) throw new IllegalStateException("combatant " + combatantId + " has no evasion profile");
        return evasionProfile;
    }
    public int accuracyStage() { return combatStages.get(CombatStageStat.ACCURACY); }
    public int setAccuracyStage(int value) { return combatStages.set(CombatStageStat.ACCURACY, value); }
    public int adjustAccuracyStage(int delta) { return combatStages.adjust(CombatStageStat.ACCURACY, delta); }
    public boolean sniper() { return sniper; }
    public boolean noGuard() { return noGuard; }
    public boolean blur() { return blur; }
    public boolean probabilityControl() { return probabilityControl; }
    public boolean abilitiesSuppressed() { return abilitiesSuppressed; }
    public List<String> types() { return types; }
    public List<AttackModifier> damageModifiers() { return damageModifiers; }
    public List<String> abilities() { return abilities; }

    public boolean hasAbilityExact(String abilityName) {
        if (abilityName == null || abilityName.isBlank()) return false;
        String target = abilityName.strip().toLowerCase(Locale.ROOT);
        for (String ability : abilities) {
            if (ability.toLowerCase(Locale.ROOT).equals(target)) return true;
        }
        return false;
    }

    boolean consumeProbabilityControl() {
        if (!probabilityControl) return false;
        probabilityControl = false;
        return true;
    }

    void setAbilitiesSuppressedFromRuntime(boolean abilitiesSuppressed) { this.abilitiesSuppressed = abilitiesSuppressed; }
    void moveTo(GridCoord destination) { movementProfile = movementProfile.withPosition(destination); }
    void setHp(int nextHp) { hp = Math.max(0, Math.min(maxHp, nextHp)); }

    int addTempHpFromRuntime(int amount) {
        int gained = Math.max(0, amount);
        tempHp += gained;
        return gained;
    }

    /** Runtime-package mutation boundary for damage/healing pipeline composition. */
    void replaceTempHpFromRuntime(int nextTempHp) {
        if (nextTempHp < 0) throw new IllegalArgumentException("temporary HP cannot be negative");
        tempHp = nextTempHp;
    }

    private static List<String> normalizeNames(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            normalized.add(value.strip());
        }
        return List.copyOf(normalized);
    }

    private static List<AttackModifier> normalizeDamageModifiers(List<AttackModifier> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<AttackModifier> normalized = new ArrayList<>(values.size());
        for (AttackModifier value : values) if (value != null) normalized.add(value);
        return List.copyOf(normalized);
    }
}
