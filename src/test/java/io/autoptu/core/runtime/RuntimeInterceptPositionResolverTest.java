package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class RuntimeInterceptPositionResolverTest {
    @Test
    void keepsCurrentPositionWhenAlreadyOnAttackLine() {
        BattleRuntimeState state = stateWithActor(new GridCoord(2, 2), 2);

        GridCoord selected = RuntimeInterceptPositionResolver.resolve(
                state,
                "interceptor",
                List.of(new GridCoord(1, 1), new GridCoord(2, 2), new GridCoord(3, 3))
        );

        assertEquals(new GridCoord(2, 2), selected);
    }

    @Test
    void choosesNearestAttackLineTileFromServerOwnedShiftDestinations() {
        BattleRuntimeState state = stateWithActor(new GridCoord(1, 2), 2);

        GridCoord selected = RuntimeInterceptPositionResolver.resolve(
                state,
                "interceptor",
                List.of(new GridCoord(3, 2), new GridCoord(2, 2), new GridCoord(4, 2))
        );

        assertEquals(new GridCoord(2, 2), selected);
    }

    @Test
    void returnsNullWhenAttackLineCannotBeReachedByLegalShift() {
        RuntimeCombatantState actor = combatant(new GridCoord(1, 2), 2);
        MovementGrid grid = new MovementGrid(
                6,
                6,
                Set.of(new GridCoord(2, 2)),
                Map.of()
        );
        BattleRuntimeState state = new BattleRuntimeState(grid, List.of(actor));

        GridCoord selected = RuntimeInterceptPositionResolver.resolve(
                state,
                "interceptor",
                List.of(new GridCoord(3, 2), new GridCoord(4, 2))
        );

        assertNull(selected);
    }

    private static BattleRuntimeState stateWithActor(GridCoord position, int overland) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(combatant(position, overland))
        );
    }

    private static RuntimeCombatantState combatant(GridCoord position, int overland) {
        return new RuntimeCombatantState(
                "interceptor",
                MovementProfile.walking(position, overland),
                10,
                10,
                new ActionBudget()
        );
    }
}
