package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.rules.EvasionResolution;
import io.autoptu.core.rules.StatusEvasionResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runtime materialization for Python calculations._temporary_accuracy_bonus().
 *
 * <p>Ordinary ability/item/position/temporary-effect inputs, Focused Training and Chronicler are
 * derived from BattleRuntimeState whenever canonical Trainer ownership exists. Transitional helper
 * contributions remain only for legacy snapshots that predate server-owned Trainer state. This class
 * is package-private so Minecraft/Cobblemon cannot supply those values through the public action
 * boundary.</p>
 */
final class RuntimeTemporaryAccuracyBonusInputs {
    private RuntimeTemporaryAccuracyBonusInputs() {
    }

    static TemporaryAccuracyBonusResolution.Input fromState(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            MoveOption move
    ) {
        return fromState(state, attackerId, defenderId, move, ContextBonuses.NONE);
    }

    static TemporaryAccuracyBonusResolution.Input fromState(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            MoveOption move,
            ContextBonuses contextBonuses
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        contextBonuses = contextBonuses == null ? ContextBonuses.NONE : contextBonuses;

        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        RuntimeCombatantState defender = state.requireCombatant(defenderId);
        var metadata = move.requireCombatProfile();
        String category = normalize(metadata.damageCategory());
        String moveType = normalize(metadata.moveType());
        String moveName = normalize(move.moveId());

        int focusedTrainingBonus = FocusedTrainingAccuracyRuntimeInputs.resolve(state, attackerId, defenderId);
        if (!state.hasCanonicalTrainer(attackerId)
                && attacker.temporaryEffects().has("focused_training")
                && contextBonuses.focusedTrainingBonus() != null) {
            focusedTrainingBonus = contextBonuses.focusedTrainingBonus();
        }
        int chroniclerBonus = state.hasCanonicalTrainer(attackerId)
                ? RuntimeChroniclerAccuracyBonusInputs.resolve(state, attackerId, defenderId)
                : contextBonuses.chroniclerBonus();

        boolean compoundEyes = registration(attacker, "Compound Eyes");
        boolean keenEye = registration(attacker, "Keen Eye");
        boolean attackerNoGuardErrata = exact(attacker, "No Guard [Errata]");
        boolean defenderNoGuardErrata = exact(defender, "No Guard [Errata]");
        boolean hustleErrata = exact(attacker, "Hustle [Errata]");
        boolean hustle = registration(attacker, "Hustle");

        boolean friskErrataWithinOne = exact(attacker, "Frisk [SuMo Errata]")
                && chebyshev(attacker.position().x(), attacker.position().y(), defender.position().x(), defender.position().y()) <= 1;

        boolean thickClub = state.heldItems(attackerId).stream()
                .anyMatch(item -> item.normalizedName().equals("thick club"));
        boolean boneWielderApplicable = registration(attacker, "Bone Wielder")
                && thickClub
                && (moveName.equals("bone club") || moveName.equals("bonemerang") || moveName.equals("bone rush"));

        boolean shellCannonApplicable = registration(attacker, "Shell Cannon")
                && attacker.temporaryEffects().has("shell_cannon_ready")
                && List.of(
                        "aqua jet", "dive", "flash cannon", "hydro cannon", "hydro pump",
                        "tackle", "waterfall", "water gun", "water spout"
                ).contains(moveName);

        List<TemporaryAccuracyBonusResolution.ScopedBonus> accuracyBonuses = scopedBonuses(
                attacker.temporaryEffects().getAll("accuracy_bonus"), "type");
        List<TemporaryAccuracyBonusResolution.ScopedBonus> lowerAvBonuses = scopedBonuses(
                attacker.temporaryEffects().getAll("accuracy_bonus_vs_lower_av"), "type");
        boolean defenderLowerAv = evasion(state, defenderId, category) < evasion(state, attackerId, category);

        return new TemporaryAccuracyBonusResolution.Input(
                focusedTrainingBonus,
                compoundEyes,
                keenEye,
                attackerNoGuardErrata,
                defenderNoGuardErrata,
                hustleErrata,
                hustle,
                category,
                friskErrataWithinOne,
                boneWielderApplicable,
                shellCannonApplicable,
                moveType,
                accuracyBonuses,
                lowerAvBonuses,
                defenderLowerAv,
                chroniclerBonus
        );
    }

    private static List<TemporaryAccuracyBonusResolution.ScopedBonus> scopedBonuses(
            List<TemporaryEffectEntry> entries,
            String scopeKey
    ) {
        ArrayList<TemporaryAccuracyBonusResolution.ScopedBonus> result = new ArrayList<>();
        for (TemporaryEffectEntry entry : entries) {
            Object rawAmount = entry.payload().get("amount");
            Integer amount = intLike(rawAmount);
            if (amount == null) continue;
            Object rawScope = entry.payload().get(scopeKey);
            result.add(new TemporaryAccuracyBonusResolution.ScopedBonus(
                    rawScope == null ? "" : String.valueOf(rawScope), amount));
        }
        return List.copyOf(result);
    }

    private static int evasion(BattleRuntimeState state, String combatantId, String category) {
        RuntimeCombatantState combatant = state.requireCombatant(combatantId);
        EvasionProfile base = combatant.requireEvasionProfile();
        EvasionProfile rebound = new EvasionProfile(
                combatant.effectiveStatProfile(),
                base.physicalBonus(),
                base.specialBonus(),
                base.statusBonus(),
                base.suppressPositiveBonuses(),
                base.ignoreNonStatBonuses()
        );
        return EvasionResolution.resolve(
                StatusEvasionResolution.apply(rebound, state.statuses(combatantId)), category);
    }

    private static boolean registration(RuntimeCombatantState combatant, String name) {
        return AbilityIdentityResolution.matchesRegistration(combatant.abilities(), name);
    }

    private static boolean exact(RuntimeCombatantState combatant, String name) {
        return AbilityIdentityResolution.matchesExact(combatant.abilities(), name);
    }

    private static int chebyshev(int ax, int ay, int bx, int by) {
        return Math.max(Math.abs(ax - bx), Math.abs(ay - by));
    }

    private static Integer intLike(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Transitional core-only helper contributions used only by legacy snapshots without canonical
     * Trainer ownership. New server-owned battle snapshots derive both contributions internally.
     */
    record ContextBonuses(Integer focusedTrainingBonus, int chroniclerBonus) {
        static final ContextBonuses NONE = new ContextBonuses(null, 0);
    }
}
