package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.rules.StatResolution;
import io.autoptu.core.rules.StatusStatResolution;
import io.autoptu.core.rules.TrainerInitiativeEntryResolution;
import io.autoptu.core.rules.TrainerInitiativeSpeedResolution;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects one server-owned Trainer into the already parity-tested Trainer initiative
 * contracts. Minecraft/Cobblemon cannot supply Trainer Speed, initiative bonus, team,
 * controlled-Pokemon Speed values, or the resulting initiative entry.
 */
public final class RuntimeInitiativeTrainerEntryFactory {
    private RuntimeInitiativeTrainerEntryFactory() {
    }

    public static InitiativeEntry fromState(BattleRuntimeState state, String trainerId) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        TrainerRuntimeState trainer = state.requireTrainer(trainerId);

        List<Integer> activePokemonSpeeds = new ArrayList<>();
        List<Integer> rosterPokemonSpeeds = new ArrayList<>();
        for (String combatantId : state.combatantIds()) {
            if (!state.hasCanonicalTrainer(combatantId)) {
                continue;
            }
            if (!state.controllerId(combatantId).equals(trainer.trainerId())) {
                continue;
            }

            RuntimeCombatantState combatant = state.requireCombatant(combatantId);
            CombatantStatProfile profile = combatant.effectiveStatProfile();
            if (profile == null) {
                throw new IllegalStateException(
                        "trainer initiative Speed requires authoritative stat profile: " + combatantId
                );
            }
            int speed = StatResolution.speed(StatusStatResolution.apply(profile, state.statuses(combatantId)));
            rosterPokemonSpeeds.add(speed);
            if (state.isActive(combatantId) && combatant.hp() > 0) {
                activePokemonSpeeds.add(speed);
            }
        }

        int speed = TrainerInitiativeSpeedResolution.resolve(
                trainer.explicitInitiativeSpeed(),
                activePokemonSpeeds,
                rosterPokemonSpeeds
        );
        String tailwindKey = trainer.teamId().isBlank() ? trainer.trainerId() : trainer.teamId();
        return TrainerInitiativeEntryResolution.resolve(
                trainer.trainerId(),
                speed,
                trainer.initiativeModifier(),
                state.environment().tailwindActive(tailwindKey)
        );
    }
}
