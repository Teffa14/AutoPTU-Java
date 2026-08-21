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
 * The caller supplies only semantic trainer/rider inputs that are not yet canonical
 * battle state. Combat stages, statuses, HP, abilities, temporary effects,
 * participation, trainer identity and environmental inputs are read from
 * BattleRuntimeState.
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

        int additionalBonus = InitiativeAdditionalBonusResolution.resolve(
                resolvedSpeed,
                actor.abilities(),
                context.agilityTraining(),
                context.riderAgilityTrainingDoubled(),
                context.hardenedInitiativeBonus()
        );

        String trainerId = state.hasCanonicalTrainer(actorId) ? state.controllerId(actorId) : "";
        InitiativeEntry baseEntry = PokemonInitiativeEntryResolution.resolve(
                actorId,
                trainerId,
                resolvedSpeed,
                context.trainerModifier(),
                state.hasStatus(actorId, "Bashed"),
                environment.tailwindActive(state.teamId(actorId)),
                state.currentRound(),
                actor.temporaryEffects().entriesInInsertionOrder(),
                additionalBonus,
                context.initiativeZeroUntilTurn()
        );

        return new InitiativePokemonCandidate(
                baseEntry,
                state.isActive(actorId),
                actor.hp() <= 0,
                context.parentalBondChild(),
                actor.temporaryEffects().entriesInInsertionOrder(),
                actor.abilities()
        );
    }
}
