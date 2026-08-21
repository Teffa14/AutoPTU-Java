package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeInitiativeTrainerEntryFactoryTest {
    @Test
    void explicitTrainerSpeedWinsAndCanonicalTeamControlsTailwind() {
        RuntimeCombatantState actor = combatant("actor", 99, 20);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("actor", CombatantAffiliationState.active("pokemon-team"))
        );
        state.putTrainer(new TrainerRuntimeState(
                "trainer",
                List.of(),
                3,
                -2,
                Map.of(),
                7,
                "trainer-team"
        ));
        state.bindController("actor", "trainer");
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState(
                "",
                "",
                Set.of("trainer-team"),
                Map.of()
        ));

        InitiativeEntry entry = RuntimeInitiativeTrainerEntryFactory.fromState(state, "trainer");

        assertEquals("trainer", entry.actorId());
        assertEquals("trainer", entry.trainerId());
        assertEquals(7, entry.speed());
        assertEquals(-2, entry.trainerModifier());
        assertEquals(0, entry.roll());
        assertEquals(10, entry.total());
    }

    @Test
    void fastestActiveControlledPokemonProvidesFallbackSpeed() {
        RuntimeCombatantState slow = combatant("slow", 6, 20);
        RuntimeCombatantState fast = combatant("fast", 14, 20);
        RuntimeCombatantState fainted = combatant("fainted", 30, 0);
        BattleRuntimeState state = state(List.of(slow, fast, fainted));
        state.putTrainer(new TrainerRuntimeState("trainer", List.of(), 3, 1));
        state.bindController("slow", "trainer");
        state.bindController("fast", "trainer");
        state.bindController("fainted", "trainer");

        InitiativeEntry entry = RuntimeInitiativeTrainerEntryFactory.fromState(state, "trainer");

        assertEquals(14, entry.speed());
        assertEquals(15, entry.total());
    }

    @Test
    void rosterFallbackIncludesInactiveAndFaintedPokemonWhenNoActiveCandidateExists() {
        RuntimeCombatantState inactive = combatant("inactive", 18, 20);
        RuntimeCombatantState fainted = combatant("fainted", 25, 0);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(inactive, fainted),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "inactive", new CombatantAffiliationState("team-a", false),
                        "fainted", CombatantAffiliationState.active("team-a")
                )
        );
        state.putTrainer(new TrainerRuntimeState("trainer", List.of(), 3));
        state.bindController("inactive", "trainer");
        state.bindController("fainted", "trainer");

        InitiativeEntry entry = RuntimeInitiativeTrainerEntryFactory.fromState(state, "trainer");

        assertEquals(25, entry.speed());
        assertEquals(25, entry.total());
    }

    @Test
    void blankTrainerTeamUsesTrainerIdentifierForTailwind() {
        BattleRuntimeState state = state(List.of());
        state.putTrainer(new TrainerRuntimeState(
                "trainer",
                List.of(),
                3,
                0,
                Map.of(),
                4,
                ""
        ));
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState("", "", Set.of("trainer"), Map.of()));

        InitiativeEntry entry = RuntimeInitiativeTrainerEntryFactory.fromState(state, "trainer");

        assertEquals(9, entry.total());
    }

    private static BattleRuntimeState state(List<RuntimeCombatantState> combatants) {
        return new BattleRuntimeState(new MovementGrid(4, 4, Set.of(), Map.of()), combatants);
    }

    private static RuntimeCombatantState combatant(String id, int speed, int hp) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.SPD, speed),
                Map.of(),
                Map.of(),
                Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 4),
                hp,
                20,
                new ActionBudget(),
                stats
        );
    }
}
