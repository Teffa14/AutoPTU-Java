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
 * Trainer entries and Pokemon candidates are derived from canonical runtime state. The
 * only remaining explicit inputs are battle-mode rules that do not yet have a canonical
 * BattleRuntimeState representation: Trick Room ordering and League trainer ordering.
 * Minecraft/Cobblemon must not provide initiative entries, Speed totals, round modifiers,
 * participant filters, or a pre-sorted order.
 */
public final class RuntimeInitiativeOrderAssembly {
    private RuntimeInitiativeOrderAssembly() {
    }

    public static InitiativeOrderAssemblyResult fromState(
            BattleRuntimeState state,
            boolean trickRoom,
            boolean leagueBattle
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }

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
