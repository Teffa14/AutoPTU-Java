package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;
import io.autoptu.core.runtime.TrainerRuntimeState;
import io.autoptu.core.rules.HardenedCritEffectBonusResolution;

import java.util.List;
import java.util.Locale;

/**
 * Builds Python-parity move-special effect-roll inputs from authoritative runtime state.
 *
 * <p>The caller supplies only the already-rolled base value and canonical move identity.
 * Abilities, Trainer Features, temporary effects, Combat Stages, injuries, suppression,
 * roll penalties and Hardened state are read from the battle core. Minecraft/Cobblemon
 * must not precompute any of these modifiers.</p>
 */
public final class MoveSpecialEffectRollRuntimeInputs {
    private MoveSpecialEffectRollRuntimeInputs() {}

    public static MoveSpecialEffectRollResolution.Input fromState(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            MoveOption move,
            int baseRoll
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (attackerId == null || attackerId.isBlank()) {
            throw new IllegalArgumentException("attackerId is required");
        }
        if (move == null) throw new IllegalArgumentException("move is required");

        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        RuntimeCombatantState defender = defenderId == null || defenderId.isBlank()
                ? null
                : state.requireCombatant(defenderId);
        MoveCombatProfile profile = move.requireCombatProfile();
        int currentRound = state.currentRound();

        MoveSpecialEffectRollTemporaryStateResolution.Result temporary =
                MoveSpecialEffectRollTemporaryStateResolution.resolve(
                        attacker.temporaryEffects(),
                        defender == null ? null : defender.temporaryEffects(),
                        move.moveId(),
                        currentRound
                );
        if (temporary.block() == MoveSpecialEffectRollTemporaryStateResolution.Block.IMMUTABLE_MIND) {
            return neutralBlockedInput(baseRoll, true, false);
        }
        if (temporary.block() == MoveSpecialEffectRollTemporaryStateResolution.Block.EFFECT_RANGE) {
            return neutralBlockedInput(baseRoll, false, true);
        }

        boolean abilitiesSuppressed = attacker.abilitiesSuppressed();
        String effectsText = move.spec().effectsText().toLowerCase(Locale.ROOT);
        String moveType = normalize(profile.moveType());
        String category = normalize(profile.damageCategory());
        String targetKind = normalize(move.spec().targetKind());

        boolean sereneGrace = !abilitiesSuppressed && attacker.hasAbilityExact("Serene Grace");
        boolean stench = !abilitiesSuppressed
                && (attacker.hasAbilityExact("Stench") || attacker.hasAbilityExact("Stench [Errata]"));

        TrainerRuntimeState trainer = state.hasCanonicalTrainer(attackerId)
                ? state.requireTrainerForCombatant(attackerId)
                : null;
        boolean firebrand = trainer != null
                && trainer.hasTrainerFeature("Firebrand")
                && moveType.equals("fire")
                && effectsText.contains("burn");
        boolean polishedShine = trainer != null
                && trainer.hasTrainerFeature("Polished Shine")
                && moveType.equals("steel");

        int rollPenalty = MoveSpecialRollPenaltyResolution.resolve(attacker.temporaryEffects(), currentRound);
        boolean mindbreak = moveType.equals("psychic")
                && !category.equals("status")
                && !attacker.temporaryEffects().getAll("mindbreak_bound").isEmpty();
        boolean brutalTraining = !attacker.temporaryEffects().getAll("brutal_training").isEmpty();

        int statStratagemApplications = 0;
        if (!category.equals("status") && targetKind.equals("ranged")) {
            for (TemporaryEffectEntry entry : attacker.temporaryEffects().getAll("stat_stratagem")) {
                if (normalize(entry.payload().get("stat")).equals("spatk")) {
                    statStratagemApplications++;
                }
            }
        }

        int hardenedCritBonus = HardenedCritEffectBonusResolution.resolve(
                currentRound,
                state.injuryHistory().currentInjuries(attackerId),
                attacker.temporaryEffects().entriesInInsertionOrder(),
                trainer != null && trainer.hasTrainerFeature("Press On!"),
                trainer == null ? 0 : trainer.skillRank("Intimidate")
        );

        return new MoveSpecialEffectRollResolution.Input(
                baseRoll,
                false,
                false,
                sereneGrace,
                stench && effectsText.contains("flinch"),
                firebrand,
                rollPenalty,
                mindbreak,
                polishedShine,
                brutalTraining,
                temporary.effectRangeBonuses(),
                statStratagemApplications,
                attacker.combatStages().get(CombatStat.SPATK),
                hardenedCritBonus
        );
    }

    private static MoveSpecialEffectRollResolution.Input neutralBlockedInput(
            int baseRoll,
            boolean immutableMindBlocked,
            boolean effectRangeBlocked
    ) {
        return new MoveSpecialEffectRollResolution.Input(
                baseRoll,
                immutableMindBlocked,
                effectRangeBlocked,
                false,
                false,
                false,
                0,
                false,
                false,
                false,
                List.of(),
                0,
                0,
                0
        );
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }
}
