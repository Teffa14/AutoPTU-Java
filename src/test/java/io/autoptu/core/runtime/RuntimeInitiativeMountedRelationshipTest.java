package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.InitiativePokemonCandidate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeInitiativeMountedRelationshipTest {
    @Test
    void mountedRiderFeatureDoublesMountAgilityTrainingFromCanonicalState() {
        RuntimeCombatantState rider = combatant("rider", 10);
        RuntimeCombatantState mount = combatant("mount", 10);
        mount.temporaryEffects().add("agility_training");

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(rider, mount)
        );
        state.putTrainer(new TrainerRuntimeState("trainer", List.of("Rider"), 1));
        state.bindController("rider", "trainer");
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState(
                "",
                "",
                Set.of(),
                Map.of(),
                Map.of("rider", "mount")
        ));

        InitiativePokemonCandidate mountCandidate = RuntimeInitiativePokemonCandidateFactory.fromState(state, "mount");
        InitiativePokemonCandidate riderCandidate = RuntimeInitiativePokemonCandidateFactory.fromState(state, "rider");

        assertEquals(18, mountCandidate.baseEntry().total());
        assertEquals(10, riderCandidate.baseEntry().total());
    }

    @Test
    void forgedLegacyRiderFlagCannotCreateMountedBonus() {
        RuntimeCombatantState mount = combatant("mount", 10);
        mount.temporaryEffects().add("agility_training");
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(mount)
        );

        RuntimeInitiativePokemonContext forged = new RuntimeInitiativePokemonContext(
                999,
                true,
                true,
                999,
                true,
                true
        );

        InitiativePokemonCandidate candidate = RuntimeInitiativePokemonCandidateFactory.fromState(
                state,
                "mount",
                forged
        );

        assertEquals(14, candidate.baseEntry().total());
    }

    @Test
    void mountedPairsAreNormalizedCopiedAndKeepInsertionOrder() {
        LinkedHashMap<String, String> input = new LinkedHashMap<>();
        input.put(" rider-1 ", " mount-1 ");
        input.put("rider-2", "mount-2");
        BattleEnvironmentState environment = new BattleEnvironmentState(
                "",
                "",
                Set.of(),
                Map.of(),
                input
        );
        input.clear();

        assertEquals(
                List.of("rider-1=mount-1", "rider-2=mount-2"),
                environment.mountedPairs().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .toList()
        );
    }

    private static RuntimeCombatantState combatant(String id, int speed) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.SPD, speed),
                Map.of(),
                Map.of(),
                Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 4),
                20,
                20,
                new ActionBudget(),
                stats,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
