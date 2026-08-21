package io.autoptu.core.rules;

import io.autoptu.core.model.InitiativeEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Composes the already parity-tested initiative-entry primitives into the ordering
 * portion of Python BattleState._build_initiative_order().
 *
 * Trainer and Pokemon base entries are authoritative core values. This boundary owns
 * Pokemon participation filtering, round-scoped initiative modifiers, cleanup requests,
 * Trick Room ordering, and League trainer-before-Pokemon ordering. Minecraft/Cobblemon
 * must never assemble or sort the battle initiative order.
 */
public final class InitiativeOrderAssembly {
    private InitiativeOrderAssembly() {
    }

    public static InitiativeOrderAssemblyResult resolve(
            List<InitiativeEntry> trainerEntries,
            List<InitiativePokemonCandidate> pokemonCandidates,
            int currentRound,
            boolean trickRoom,
            boolean leagueBattle
    ) {
        if (currentRound < 0) {
            throw new IllegalArgumentException("currentRound cannot be negative");
        }

        List<InitiativeEntry> safeTrainerEntries = copyTrainerEntries(trainerEntries);
        List<InitiativeEntry> pokemonEntries = new ArrayList<>();
        LinkedHashMap<String, List<String>> cleanupByActor = new LinkedHashMap<>();

        for (InitiativePokemonCandidate candidate : pokemonCandidates == null
                ? List.<InitiativePokemonCandidate>of()
                : pokemonCandidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("pokemon candidate cannot be null");
            }
            if (candidate.fainted() || !candidate.active() || candidate.parentalBondChild()) {
                continue;
            }
            InitiativeEntry baseEntry = candidate.baseEntry();
            if (baseEntry == null) {
                continue;
            }
            if (baseEntry.actorId().isBlank()) {
                throw new IllegalArgumentException("Pokemon initiative entry actorId is required");
            }

            InitiativeRoundModifierResult modified = InitiativeRoundModifierResolution.resolve(
                    baseEntry,
                    currentRound,
                    candidate.temporaryEffects(),
                    candidate.abilities()
            );
            pokemonEntries.add(modified.entry());
            if (!modified.temporaryEffectsToClear().isEmpty()) {
                cleanupByActor.put(baseEntry.actorId(), modified.temporaryEffectsToClear());
            }
        }

        ArrayList<InitiativeEntry> entries = new ArrayList<>(safeTrainerEntries.size() + pokemonEntries.size());
        entries.addAll(safeTrainerEntries);
        entries.addAll(pokemonEntries);
        Set<String> trainerActorIds = safeTrainerEntries.stream()
                .map(InitiativeEntry::actorId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<InitiativeEntry> ordered = InitiativeOrder.sort(entries, trainerActorIds, trickRoom, leagueBattle);
        return new InitiativeOrderAssemblyResult(ordered, cleanupByActor);
    }

    private static List<InitiativeEntry> copyTrainerEntries(List<InitiativeEntry> trainerEntries) {
        if (trainerEntries == null || trainerEntries.isEmpty()) {
            return List.of();
        }
        ArrayList<InitiativeEntry> copied = new ArrayList<>(trainerEntries.size());
        LinkedHashSet<String> actorIds = new LinkedHashSet<>();
        for (InitiativeEntry entry : trainerEntries) {
            if (entry == null) {
                throw new IllegalArgumentException("trainer initiative entry cannot be null");
            }
            if (entry.actorId().isBlank()) {
                throw new IllegalArgumentException("trainer initiative entry actorId is required");
            }
            if (!actorIds.add(entry.actorId())) {
                throw new IllegalArgumentException("duplicate trainer initiative actorId: " + entry.actorId());
            }
            copied.add(entry);
        }
        return List.copyOf(copied);
    }
}
