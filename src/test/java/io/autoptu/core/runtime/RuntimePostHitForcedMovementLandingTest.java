package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.TerrainHazardEvent;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.StatusApplicationHookRegistry;
import io.autoptu.core.hook.StatusApplicationHookResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePostHitForcedMovementLandingTest {
    @Test
    void successfulForcedMovementAppliesLandingConsequencesAndExposesResolvedHazardEvent() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = pushMove();
        BattleRuntimeState state = state(source, target, move);
        GridCoord landing = new GridCoord(4, 1);
        state.putTileTrapFromRuntime(landing, new TileEntryTrapResolution.TrapLayer("sticky_trap", 1, "trap-source", "red", Set.of("forest"), "Sticky Trap"));
        MoveChoice choice = choice(source, target, move);

        RuntimePostHitForcedMovementApplication.SemanticResolution result = RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(state, choice, true, BattleRuntimeDependencies.empty());

        assertTrue(result.resolution().movement().isPresent());
        assertEquals(landing, target.position());
        assertTrue(state.hasStatus("target", "Slowed"));
        assertTrue(state.tileTrapsAt(landing).isEmpty());
        assertEquals(1, result.events().size());

        TerrainHazardEvent event = assertInstanceOf(TerrainHazardEvent.class, result.events().get(0));
        assertEquals("trigger", event.effect());
        assertEquals("target", event.actorId());
        assertEquals("sticky_trap", event.trapKey());
        assertEquals("Sticky Trap", event.trapName());
        assertEquals("trap-source", event.sourceId());
        assertEquals(20, event.targetHp());
        assertEquals(landing, event.coordinate());
        assertEquals(Set.of("forest"), event.terrains());
    }

    @Test
    void composedHookRegistriesAreUsedByForcedMovementLanding() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = pushMove();
        BattleRuntimeState state = state(source, target, move);
        GridCoord landing = new GridCoord(4, 1);
        state.putTileTrapFromRuntime(landing, new TileEntryTrapResolution.TrapLayer("sticky_trap", 1, "trap-source", "red", Set.of("forest"), "Sticky Trap"));
        AtomicBoolean statusObserved = new AtomicBoolean(false);
        StatusApplicationHookRegistry statusHooks = StatusApplicationHookRegistry.builder()
                .register("integration-probe", HookSource.STATUS, 1, context -> {
                    if (context.targetId().equals("target") && context.status().name().equals("slowed")) statusObserved.set(true);
                    return StatusApplicationHookResult.allow();
                })
                .build();
        AtomicBoolean landingObserved = new AtomicBoolean(false);
        MovementLandingHookRegistry landingHooks = MovementLandingHookRegistry.standard()
                .register(MovementLandingHookRegistry.HookFamily.TILE_TRAP, "integration_probe", context -> {
                    landingObserved.set(true);
                    return List.of();
                });
        BattleRuntimeDependencies dependencies = new BattleRuntimeDependencies(CombatantRuleContentRegistry.empty(), statusHooks, landingHooks);

        RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(state, choice(source, target, move), true, dependencies);

        assertTrue(landingObserved.get());
        assertTrue(statusObserved.get());
        assertTrue(state.hasStatus("target", "Slowed"));
        assertTrue(state.tileTrapsAt(landing).isEmpty());
    }

    @Test
    void naturewalkForcedMovementLandingExposesReducedTrapBlockAndPreservesTrap() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = pushMove();
        BattleRuntimeState state = state(source, target, move);
        GridCoord landing = new GridCoord(4, 1);
        state.putTileTrapFromRuntime(landing, new TileEntryTrapResolution.TrapLayer("sticky_trap", 1, "trap-source", "red", Set.of("forest"), "Sticky Trap"));
        MoveChoice choice = choice(source, target, move);
        CombatantRuleContent naturewalk = new CombatantRuleContent(List.of(), null, "", Map.of(), List.of(), List.of("forest"));
        BattleRuntimeDependencies dependencies = new BattleRuntimeDependencies(new CombatantRuleContentRegistry(Map.of("target", naturewalk)));

        RuntimePostHitForcedMovementApplication.SemanticResolution result = RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(state, choice, true, dependencies);

        assertTrue(result.resolution().movement().isPresent());
        assertEquals(landing, target.position());
        assertFalse(state.hasStatus("target", "Slowed"));
        assertEquals(1, state.tileTrapsAt(landing).size());
        assertEquals(1, result.events().size());

        TerrainHazardEvent event = assertInstanceOf(TerrainHazardEvent.class, result.events().get(0));
        assertEquals("trap_block", event.effect());
        assertEquals("target", event.actorId());
        assertEquals("sticky_trap", event.trapKey());
        assertEquals(20, event.targetHp());
        assertTrue(event.trapName().isBlank());
        assertTrue(event.sourceId().isBlank());
        assertNull(event.coordinate());
        assertTrue(event.terrains().isEmpty());
    }

    private static MoveOption pushMove() {
        return new MoveOption("ram", new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee", List.of("push 2"), "Push the target 2 meters."), ActionType.STANDARD, true);
    }

    private static BattleRuntimeState state(RuntimeCombatantState source, RuntimeCombatantState target, MoveOption move) {
        return new BattleRuntimeState(new MovementGrid(8, 4, Set.of(), Map.of()), List.of(source, target), Map.of(), Map.of(), Map.of(), Map.of(), Map.of("source", List.of(move)));
    }

    private static MoveChoice choice(RuntimeCombatantState source, RuntimeCombatantState target, MoveOption move) {
        return new MoveChoice(source.combatantId(), move.moveId(), ChoiceTargetMode.COMBATANT, target.combatantId(), target.position(), ActionType.STANDARD);
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        GridCoord position = new GridCoord(x, y);
        return new RuntimeCombatantState(id, 20, position, MovementProfile.walking(position, 6), "Medium", new ActionBudget());
    }
}
