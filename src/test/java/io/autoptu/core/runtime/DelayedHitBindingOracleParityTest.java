package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelayedHitBindingOracleParityTest {
    @Test
    void dueResolutionTargetContractMatchesPinnedPythonCalls() throws IOException {
        String fixturePath = System.getProperty("autoptu.delayed.hit.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());

        List<Fixture> fixtures = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(fixturePath))) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            fixtures.add(new Fixture(
                    parts[1],
                    parts[2],
                    parts[3].isBlank() ? null : parts[3],
                    parts[4].isBlank() ? null : new GridCoord(Integer.parseInt(parts[4]), Integer.parseInt(parts[5])),
                    Integer.parseInt(parts[6]),
                    parts[7]
            ));
        }

        BattleRuntimeState state = stateFor(fixtures);
        for (Fixture fixture : fixtures) {
            DelayedHitEntry entry = new DelayedHitEntry(
                    fixture.attackerId(), fixture.moveId(), fixture.targetId(), fixture.targetPosition(),
                    fixture.triggerRound(), fixture.effect()
            );
            DelayedHitBinding binding = DelayedHitBindingResolver.bind(state, entry);

            assertEquals(fixture.moveId(), binding.move().moveId());
            assertEquals(binding.move().actionType(), binding.choice().actionType());
            if (fixture.targetId() != null) {
                assertEquals(ChoiceTargetMode.COMBATANT, binding.choice().targetMode());
                assertEquals(fixture.targetId(), binding.choice().targetId());
                GridCoord expectedAnchor = fixture.targetPosition() != null
                        ? fixture.targetPosition()
                        : state.requireCombatant(fixture.targetId()).position();
                assertEquals(expectedAnchor, binding.choice().targetAnchor());
            } else {
                assertEquals(ChoiceTargetMode.TILE, binding.choice().targetMode());
                assertEquals("", binding.choice().targetId());
                assertEquals(fixture.targetPosition(), binding.choice().targetAnchor());
            }
        }
    }

    @Test
    void targetIdRemainsAuthoritativeWhenDelayedEntryAlsoCarriesTargetPosition() {
        Fixture fixture = new Fixture(
                "actor",
                "Future Sight",
                "target",
                new GridCoord(7, 9),
                2,
                "future_sight"
        );
        BattleRuntimeState state = stateFor(List.of(fixture));

        DelayedHitBinding binding = DelayedHitBindingResolver.bind(
                state,
                new DelayedHitEntry(
                        fixture.attackerId(),
                        fixture.moveId(),
                        fixture.targetId(),
                        fixture.targetPosition(),
                        fixture.triggerRound(),
                        fixture.effect()
                )
        );

        assertEquals(ChoiceTargetMode.COMBATANT, binding.choice().targetMode());
        assertEquals(fixture.targetId(), binding.choice().targetId());
        assertEquals(fixture.targetPosition(), binding.choice().targetAnchor());
    }

    @Test
    void bindingFailsClosedForMoveOrTargetNotOwnedByCanonicalState() {
        Fixture fixture = new Fixture("actor", "Future Sight", "target", null, 2, "future_sight");
        BattleRuntimeState state = stateFor(List.of(fixture));

        assertThrows(IllegalStateException.class, () -> DelayedHitBindingResolver.bind(
                state,
                new DelayedHitEntry("actor", "Injected Move", "target", null, 2, "forged")
        ));
        assertThrows(IllegalArgumentException.class, () -> DelayedHitBindingResolver.bind(
                state,
                new DelayedHitEntry("actor", "Future Sight", "missing-target", null, 2, "future_sight")
        ));
    }

    private static BattleRuntimeState stateFor(List<Fixture> fixtures) {
        LinkedHashMap<String, RuntimeCombatantState> combatants = new LinkedHashMap<>();
        LinkedHashMap<String, List<MoveOption>> moves = new LinkedHashMap<>();
        for (Fixture fixture : fixtures) {
            combatants.computeIfAbsent(fixture.attackerId(), ignored -> combatant(fixture.attackerId(), new GridCoord(positionFor(fixture.attackerId()), 1)));
            moves.computeIfAbsent(fixture.attackerId(), ignored -> new ArrayList<>())
                    .add(MoveOption.standard(fixture.moveId(), new MoveSpec("Ranged", "Ranged", 10, 10, null, null, "Ranged")));
            if (fixture.targetId() != null) {
                combatants.computeIfAbsent(fixture.targetId(), ignored -> combatant(fixture.targetId(), new GridCoord(positionFor(fixture.targetId()), 3)));
            }
        }
        return new BattleRuntimeState(
                new MovementGrid(20, 20, Set.of(), Map.of()),
                List.copyOf(combatants.values()),
                Map.of(), Map.of(), Map.of(), Map.of(), moves
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                50,
                50,
                new ActionBudget()
        );
    }

    private static int positionFor(String id) {
        return 1 + Math.floorMod(id.hashCode(), 16);
    }

    private record Fixture(
            String attackerId,
            String moveId,
            String targetId,
            GridCoord targetPosition,
            int triggerRound,
            String effect
    ) {}
}
