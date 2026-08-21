package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.InitiativeEntry;
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
 * Combat stages, statuses, HP, abilities, temporary effects, participation, trainer
 * identity/modifier and environmental inputs are read from BattleRuntimeState. The
 * transitional context only remains authoritative for rider doubling and Hardened
 * Initiative until their upstream state is represented canonically.
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

        int additionalBonus = InitiativeAdditionalBonusResolution.resolve(
                resolvedSpeed,
                actor.abilities(),
                agilityTraining,
                context.riderAgilityTrainingDoubled(),
                context.hardenedInitiativeBonus()
        );

        String trainerId = state.hasCanonicalTrainer(actorId) ? state.controllerId(actorId) : "";
        int trainerModifier = state.hasCanonicalTrainer(actorId)
                ? state.requireTrainerForCombatant(actorId).initiativeModifier()
                : 0;
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
