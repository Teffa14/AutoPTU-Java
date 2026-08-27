package io.autoptu.core.runtime;

/**
 * Materializes Python BattleState._chronicler_accuracy_bonus() entirely from canonical runtime state.
 *
 * <p>targeted_profiling entries belong to the attacking Pokemon, while profile archives belong to
 * the source Trainer referenced by each profiling entry. Target name/species/controller identity
 * is read from the authoritative battle snapshot. Minecraft/Cobblemon never supplies a precomputed
 * Chronicler bonus or eligibility flag.</p>
 */
final class RuntimeChroniclerAccuracyBonusInputs {
    private RuntimeChroniclerAccuracyBonusInputs() {
    }

    static int resolve(BattleRuntimeState state, String attackerId, String defenderId) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        RuntimeCombatantState defender = state.requireCombatant(defenderId);
        if (!state.hasCanonicalTrainer(attackerId)) return 0;

        String attackerControllerId = state.controllerId(attackerId);
        ChroniclerProfileMatchResolution.TargetProfile target = targetProfile(state, defenderId, defender);
        return ChroniclerAccuracyBonusResolution.resolve(
                attacker.temporaryEffects(),
                state.currentRound(),
                attackerControllerId,
                sourceControllerId -> matchesSourceTrainer(state, sourceControllerId, target)
        );
    }

    private static boolean matchesSourceTrainer(
            BattleRuntimeState state,
            String sourceControllerId,
            ChroniclerProfileMatchResolution.TargetProfile target
    ) {
        if (sourceControllerId == null || sourceControllerId.isBlank()) return false;
        TrainerRuntimeState sourceTrainer;
        try {
            sourceTrainer = state.requireTrainer(sourceControllerId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return ChroniclerProfileMatchResolution.matches(sourceTrainer.chroniclerProfileMetadata(), target);
    }

    private static ChroniclerProfileMatchResolution.TargetProfile targetProfile(
            BattleRuntimeState state,
            String defenderId,
            RuntimeCombatantState defender
    ) {
        String controllerTrainerName = "";
        if (state.hasCanonicalTrainer(defenderId)) {
            controllerTrainerName = state.requireTrainerForCombatant(defenderId).trainerName();
        }
        CombatantProfileIdentity identity = defender.profileIdentity();
        return new ChroniclerProfileMatchResolution.TargetProfile(
                identity.name(),
                identity.species(),
                controllerTrainerName
        );
    }
}
