package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.rules.HardenedInitiativeResolution;
import io.autoptu.core.rules.InitiativeAdditionalBonusResolution;
import io.autoptu.core.rules.InitiativePokemonCandidate;
import io.autoptu.core.rules.InitiativeSpeedAbilityResolution;
import io.autoptu.core.rules.PokemonInitiativeEntryResolution;
import io.autoptu.core.rules.StatResolution;
import io.autoptu.core.rules.StatusStatResolution;

/**
 * Internal adapter from authoritative battle state to the parity-tested Pokemon
 * initiative contracts.
 *
 * Combat stages, statuses, HP, abilities, temporary effects, injuries, Trainer
 * identity/modifier/skills and environmental inputs are read from BattleRuntimeState.
 * The transitional context remains authoritative only for rider Agility Training
 * doubling until mount/rider relationships are represented canonically.
 */
public final class RuntimeInitiativePokemonCandidateFactory {
    private RuntimeInitiativePokemonCandidateFactory() {
    }

    public static InitiativePokemonCandidate fromState(
            BattleRuntimeState state,
            String actorId,
            RuntimeInitiativePokemonContext context
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("initiative context is required");
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

        int additionalBonus = InitiativeAdditionalBonusResolution.resolve(
                resolvedSpeed,
                actor.abilities(),
                agilityTraining,
                context.riderAgilityTrainingDoubled(),
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
}
