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

class RuntimeMovementLandingContextTest {
    @Test
    void derivesLandingInputOnlyFromAuthoritativeStateAndRuleContent() {
        GridCoord position = new GridCoord(3, 2);
        RuntimeCombatantState target = new RuntimeCombatantState(
                "target",
                MovementProfile.walking(position, 5),
                42,
                60,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                new CombatantProfileIdentity("Target Name", "Species")
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(target),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("target", CombatantAffiliationState.active("blue"))
        );
        var landingTrap = new TileEntryTrapResolution.TrapLayer(
                "sticky_trap",
                1,
                "source",
                "red",
                Set.of("forest"),
                "Sticky Trap"
        );
        state.putTileTrapFromRuntime(position, landingTrap);
        state.putTileTrapFromRuntime(
                new GridCoord(4, 2),
                new TileEntryTrapResolution.TrapLayer(
                        "other_trap",
                        1,
                        "source-2",
                        "red",
                        Set.of("cave"),
                        "Other Trap"
                )
        );
        CombatantRuleContent content = new CombatantRuleContent(
                List.of("Naturewalk (Tundra)"),
                null,
                "",
                Map.of(),
                List.of(),
                List.of("Forest")
        );

        MovementLandingHookRegistry.LandingContext context =
                RuntimeMovementLandingContext.resolve(state, "target", content);

        assertEquals("target", context.tileEntryContext().actorId());
        assertEquals("Target Name", context.tileEntryContext().actorName());
        assertEquals("blue", context.tileEntryContext().actorTeamId());
        assertEquals(42, context.tileEntryContext().actorHp());
        assertEquals(position, context.tileEntryContext().coordinate());
        assertEquals(Set.of("forest", "tundra"), context.tileEntryContext().naturewalkTerrains());
        assertEquals(List.of(landingTrap), context.tileTraps());
    }

    @Test
    void fallsBackToCombatantIdWhenCanonicalDisplayNameIsMissing() {
        GridCoord position = new GridCoord(1, 1);
        RuntimeCombatantState target = new RuntimeCombatantState(
                "legacy-target",
                MovementProfile.walking(position, 4),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(target)
        );

        MovementLandingHookRegistry.LandingContext context = RuntimeMovementLandingContext.resolve(
                state,
                "legacy-target",
                CombatantRuleContent.empty()
        );

        assertEquals("legacy-target", context.tileEntryContext().actorName());
        assertEquals("legacy-target", context.tileEntryContext().actorTeamId());
        assertEquals(List.of(), context.tileTraps());
    }
}
