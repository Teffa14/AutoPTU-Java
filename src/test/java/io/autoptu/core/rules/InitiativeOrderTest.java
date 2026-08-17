package io.autoptu.core.rules;

import io.autoptu.core.model.DeclaredActionOrder;
import io.autoptu.core.model.InitiativeEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InitiativeOrderTest {
    private static InitiativeEntry entry(String actor, int total, int speed, int roll) {
        return new InitiativeEntry(actor, "trainer", speed, 0, roll, total);
    }

    @Test
    void normalInitiativeSortsTotalThenSpeedDescendingThenActorId() {
        List<InitiativeEntry> result = InitiativeOrder.sort(
                List.of(
                        entry("zeta", 10, 20, 0),
                        entry("beta", 12, 15, 0),
                        entry("alpha", 12, 15, 0),
                        entry("gamma", 12, 10, 0)
                ),
                Set.of(),
                false,
                false
        );
        assertEquals(List.of("alpha", "beta", "gamma", "zeta"), actors(result));
    }

    @Test
    void trickRoomSortsTotalThenSpeedAscendingThenActorId() {
        List<InitiativeEntry> result = InitiativeOrder.sort(
                List.of(
                        entry("zeta", 10, 20, 0),
                        entry("beta", 8, 15, 0),
                        entry("alpha", 8, 15, 0),
                        entry("gamma", 8, 10, 0)
                ),
                Set.of(),
                true,
                false
        );
        assertEquals(List.of("gamma", "alpha", "beta", "zeta"), actors(result));
    }

    @Test
    void leagueBattlePreservesTrainerInsertionOrderBeforeSortedPokemon() {
        InitiativeEntry trainerB = new InitiativeEntry("trainer-b", "trainer-b", 5, 0, 0, 5);
        InitiativeEntry pokemonLow = entry("pokemon-low", 10, 10, 0);
        InitiativeEntry trainerA = new InitiativeEntry("trainer-a", "trainer-a", 99, 0, 0, 99);
        InitiativeEntry pokemonHigh = entry("pokemon-high", 20, 20, 0);

        List<InitiativeEntry> result = InitiativeOrder.sort(
                List.of(trainerB, pokemonLow, trainerA, pokemonHigh),
                Set.of("trainer-a", "trainer-b"),
                false,
                true
        );
        assertEquals(
                List.of("trainer-b", "trainer-a", "pokemon-high", "pokemon-low"),
                actors(result)
        );
    }

    @Test
    void declaredActionsSortTotalRollSpeedDescendingThenActorId() {
        List<DeclaredActionOrder> result = InitiativeOrder.sortDeclaredActions(List.of(
                new DeclaredActionOrder("zeta", 12, 5, 20),
                new DeclaredActionOrder("beta", 12, 7, 10),
                new DeclaredActionOrder("alpha", 12, 7, 10),
                new DeclaredActionOrder("gamma", 10, 99, 99),
                new DeclaredActionOrder("speed", 12, 7, 30)
        ));
        assertEquals(
                List.of("speed", "alpha", "beta", "zeta", "gamma"),
                result.stream().map(DeclaredActionOrder::actorId).toList()
        );
    }

    private static List<String> actors(List<InitiativeEntry> entries) {
        return entries.stream().map(InitiativeEntry::actorId).toList();
    }
}
