package io.autoptu.core.runtime;

import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.rules.InitiativeOrderAssembly;
import io.autoptu.core.rules.InitiativeOrderAssemblyResult;
import io.autoptu.core.rules.InitiativePokemonCandidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal server-authoritative adapter from BattleRuntimeState to the parity-tested
 * initiative-order assembly contract.
 *
 * Trainer entries, Pokemon candidates, Trick Room ordering and League trainer ordering
 * are derived from canonical runtime state. Minecraft/Cobblemon must not provide
 * initiative entries, Speed totals, round modifiers, participant filters, ordering
 * modes, or a pre-sorted order.
 */
public final class RuntimeInitiativeOrderAssembly {
    private RuntimeInitiativeOrderAssembly() {
    }

    /** Preferred server-authoritative boundary. */
    public static InitiativeOrderAssemblyResult fromState(BattleRuntimeState state) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        BattleEnvironmentState environment = state.environment();
        return resolve(
                state,
                environment.trickRoomOrdering(),
                environment.leagueBattleOrdering()
        );
    }

    /**
     * Transitional compatibility overload for callers not yet migrated to canonical
     * battle-environment state. New runtime code must use {@link #fromState(BattleRuntimeState)}.
     */
    @Deprecated
    public static InitiativeOrderAssemblyResult fromState(
            BattleRuntimeState state,
            boolean trickRoom,
            boolean leagueBattle
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        return resolve(state, trickRoom, leagueBattle);
    }

    private static InitiativeOrderAssemblyResult resolve(
            BattleRuntimeState state,
            boolean trickRoom,
            boolean leagueBattle
    ) {
        List<InitiativeEntry> trainerEntries = canonicalTrainerEntries(state);
        List<InitiativePokemonCandidate> pokemonCandidates = state.combatantIds().stream()
                .map(actorId -> RuntimeInitiativePokemonCandidateFactory.fromState(state, actorId))
                .toList();

        return InitiativeOrderAssembly.resolve(
                trainerEntries,
                pokemonCandidates,
                state.currentRound(),
                trickRoom,
                leagueBattle
        );
    }

    private static List<InitiativeEntry> canonicalTrainerEntries(BattleRuntimeState state) {
        List<String> trainerIds = state.trainerIds();
        ArrayList<InitiativeEntry> entries = new ArrayList<>(trainerIds.size());
        for (String trainerId : trainerIds) {
            entries.add(RuntimeInitiativeTrainerEntryFactory.fromState(state, trainerId));
        }
        return List.copyOf(entries);
    }
}
