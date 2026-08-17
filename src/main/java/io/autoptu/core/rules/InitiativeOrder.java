package io.autoptu.core.rules;

import io.autoptu.core.model.DeclaredActionOrder;
import io.autoptu.core.model.InitiativeEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Deterministic initiative/action ordering extracted from Python BattleState. */
public final class InitiativeOrder {
    private InitiativeOrder() {
    }

    public static List<InitiativeEntry> sort(
            List<InitiativeEntry> entries,
            Set<String> trainerActorIds,
            boolean trickRoom,
            boolean leagueBattle
    ) {
        List<InitiativeEntry> safeEntries = new ArrayList<>(entries == null ? List.of() : entries);
        Set<String> trainers = trainerActorIds == null ? Set.of() : trainerActorIds;
        Comparator<InitiativeEntry> comparator = initiativeComparator(trickRoom);

        if (!leagueBattle) {
            safeEntries.sort(comparator);
            return List.copyOf(safeEntries);
        }

        List<InitiativeEntry> trainerEntries = new ArrayList<>();
        List<InitiativeEntry> pokemonEntries = new ArrayList<>();
        for (InitiativeEntry entry : safeEntries) {
            if (trainers.contains(entry.actorId())) {
                trainerEntries.add(entry);
            } else {
                pokemonEntries.add(entry);
            }
        }
        pokemonEntries.sort(comparator);
        trainerEntries.addAll(pokemonEntries);
        return List.copyOf(trainerEntries);
    }

    public static List<DeclaredActionOrder> sortDeclaredActions(List<DeclaredActionOrder> entries) {
        List<DeclaredActionOrder> result = new ArrayList<>(entries == null ? List.of() : entries);
        result.sort(
                Comparator.comparingInt(DeclaredActionOrder::total).reversed()
                        .thenComparing(Comparator.comparingInt(DeclaredActionOrder::roll).reversed())
                        .thenComparing(Comparator.comparingInt(DeclaredActionOrder::speed).reversed())
                        .thenComparing(DeclaredActionOrder::actorId)
        );
        return List.copyOf(result);
    }

    private static Comparator<InitiativeEntry> initiativeComparator(boolean trickRoom) {
        if (trickRoom) {
            return Comparator.comparingInt(InitiativeEntry::total)
                    .thenComparingInt(InitiativeEntry::speed)
                    .thenComparing(InitiativeEntry::actorId);
        }
        return Comparator.comparingInt(InitiativeEntry::total).reversed()
                .thenComparing(Comparator.comparingInt(InitiativeEntry::speed).reversed())
                .thenComparing(InitiativeEntry::actorId);
    }
}
