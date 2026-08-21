package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.rules.HardenedInitiativeResolution;
import io.autoptu.core.rules.InitiativeAdditionalBonusResolution;
import io.autoptu.core.rules.InitiativePokemonCandidate;
import io.autoptu.core.rules.InitiativeSpeedAbilityResolution;
import io.autoptu.core.rules.PokemonInitiativeEntryResolution;
import io.autoptu.core.rules.RiderAgilityTrainingResolution;
import io.autoptu.core.rules.StatResolution;
import io.autoptu.core.rules.StatusStatResolution;

import java.util.List;

/**
 * Internal adapter from authoritative battle state to the parity-tested Pokemon
 * initiative contracts.
 *
 * Combat stages, statuses, HP, abilities, temporary effects, injuries, Trainer
 * identity/modifier/skills, environment and mounted relationships are read from
 * BattleRuntimeState. No PTU initiative result is accepted from Minecraft/Cobblemon.
 */
public final class RuntimeInitiativePokemonCandidateFactory {
    private RuntimeInitiativePokemonCandidateFactory() {
    }

    /** Preferred server-authoritative boundary. */
    public static InitiativePokemonCandidate fromState(BattleRuntimeState state, String actorId) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }

        RuntimeCombatantState actor = state.requireCombatant(actorId);
        BattleEnvironmentState environment = state.environment();
        CombatantStatProfile statusAwareProfile = StatusStatResolution.apply(
                actor.effectiveStatProfile(),
                state.statuses(actorId)
        );
        int resolvedSpeed = StatResolution.speed(statusAwareProfile);
        resolvedSpeed = InitiativeSpeedAbilityResolution.resolve(
                resolvedSpeed,
                actor.hp(),
                actor.maxHp(),
                environment.weather(),
                environment.terrainName(),
                environment.grounded(actorId),
                actor.abilities()
        );

        boolean agilityTraining = actor.temporaryEffects().has("agility_training");
        boolean parentalBondChild = actor.temporaryEffects().has("parental_bond_child");
        boolean initiativeZeroUntilTurn = actor.temporaryEffects().has("initiative_zero_until_turn");

        TrainerRuntimeState trainer = state.hasCanonicalTrainer(actorId)
                ? state.requireTrainerForCombatant(actorId)
                : null;
        int hardenedInitiativeBonus = HardenedInitiativeResolution.resolve(
                state.currentRound(),
                state.injuryHistory().currentInjuries(actorId),
                actor.temporaryEffects().entriesInInsertionOrder(),
                trainer != null && trainer.hasTrainerFeature("Press On!"),
                trainer == null ? 0 : trainer.skillRank("intimidate")
        );

        List<String> riderFeatureActors = state.combatantIds().stream()
                .filter(id -> state.hasCanonicalTrainer(id))
                .filter(id -> state.requireTrainerForCombatant(id).hasTrainerFeature("Rider"))
                .toList();
        List<String> agilityTrainingActors = state.combatantIds().stream()
                .filter(id -> state.requireCombatant(id).temporaryEffects().has("agility_training"))
                .toList();
        boolean riderAgilityTrainingDoubled = RiderAgilityTrainingResolution.doubled(
                actorId,
                environment.mountedPairs(),
                state.combatantIds(),
                riderFeatureActors,
                agilityTrainingActors
        );

        int additionalBonus = InitiativeAdditionalBonusResolution.resolve(
                resolvedSpeed,
                actor.abilities(),
                agilityTraining,
                riderAgilityTrainingDoubled,
                hardenedInitiativeBonus
        );

        String trainerId = trainer == null ? "" : state.controllerId(actorId);
        int trainerModifier = trainer == null ? 0 : trainer.initiativeModifier();
        InitiativeEntry baseEntry = PokemonInitiativeEntryResolution.resolve(
                actorId,
                trainerId,
                resolvedSpeed,
                trainerModifier,
                state.hasStatus(actorId, "Bashed"),
                environment.tailwindActive(state.teamId(actorId)),
                state.currentRound(),
                actor.temporaryEffects().entriesInInsertionOrder(),
                additionalBonus,
                initiativeZeroUntilTurn
        );

        return new InitiativePokemonCandidate(
                baseEntry,
                state.isActive(actorId),
                actor.hp() <= 0,
                parentalBondChild,
                actor.temporaryEffects().entriesInInsertionOrder(),
                actor.abilities()
        );
    }

    /**
     * Transitional compatibility boundary. All fields in the legacy context are ignored;
     * initiative is derived from BattleRuntimeState only.
     */
    @Deprecated
    public static InitiativePokemonCandidate fromState(
            BattleRuntimeState state,
            String actorId,
            RuntimeInitiativePokemonContext ignoredContext
    ) {
        return fromState(state, actorId);
    }
}
