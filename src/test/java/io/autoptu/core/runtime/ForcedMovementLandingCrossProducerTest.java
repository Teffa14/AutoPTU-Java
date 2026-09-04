package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.TerrainHazardEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForcedMovementLandingCrossProducerTest {
    private static final GridCoord DIRECT_LANDING = new GridCoord(4, 1);
    private static final GridCoord MULTI_LANDING = new GridCoord(4, 1);
    private static final GridCoord DELAYED_LANDING = new GridCoord(6, 1);

    @Test
    void directMovePublishesLandingHazardAfterMoveAndCommitsFinalState() {
        MoveOption move = directPushMove();
        RuntimeCombatantState actor = simpleCombatant("actor", new GridCoord(1, 1), 50, 50);
        RuntimeCombatantState target = simpleCombatant("target", new GridCoord(2, 1), 35, 100);
        BattleRuntimeState state = simpleState(actor, target, move, 8, 4);
        addStickyTrap(state, DIRECT_LANDING);

        AppliedActionResult result = BattleRuntime.applyAuthoritativeMove(
                state,
                combatantChoice(move, target),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                legacyInput(5),
                BattleRuntimeDependencies.empty()
        );

        assertLandingContract(result.events(), state, target, DIRECT_LANDING);
    }

    @Test
    void multiTargetMovePublishesTheSameLandingContractForSuccessfulPush() {
        MoveOption move = areaPushMove();
        RuntimeCombatantState actor = profiledCombatant("actor", new GridCoord(0, 1), 100, profile(20, 10, 20, 10, 10));
        RuntimeCombatantState target = profiledCombatant("target", new GridCoord(2, 1), 100, profile(12, 12, 12, 12, 10));
        LinkedHashMap<String, CombatantAffiliationState> affiliation = new LinkedHashMap<>();
        affiliation.put("actor", CombatantAffiliationState.active("alpha"));
        affiliation.put("target", CombatantAffiliationState.active("beta"));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of(),
                Map.of(),
                Map.of(),
                affiliation,
                Map.of("actor", List.of(move))
        );
        addStickyTrap(state, MULTI_LANDING);
        MoveChoice choice = new MoveChoice(
                "actor",
                move.moveId(),
                ChoiceTargetMode.TILE,
                "",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );

        MultiTargetAppliedActionResult result = RuntimeMoveResolution.applyAreaUsingAuthoritativeCombatState(
                state,
                choice,
                "AI",
                new PythonRandom(73),
                legacyInput(99),
                false,
                false,
                BattleRuntimeDependencies.empty()
        );

        assertEquals(List.of("target"), result.targetIds());
        assertLandingContract(result.events(), state, target, MULTI_LANDING);
    }

    @Test
    void delayedMovePublishesTheSameLandingContractWhenTheHitMatures() {
        MoveOption move = delayedPushMove();
        RuntimeCombatantState actor = profiledCombatant("actor", new GridCoord(1, 1), 60, profile(30, 8, 18, 8, 10));
        RuntimeCombatantState target = profiledCombatant("target", new GridCoord(4, 1), 100, profile(12, 12, 12, 12, 10));
        BattleRuntimeState state = simpleState(actor, target, move, 20, 20);
        addStickyTrap(state, DELAYED_LANDING);
        assertTrue(actor.actionBudget().consume(ActionType.STANDARD, move.moveId()));
        actor.moveFrequencyUsage().recordUse(move);
        DelayedHitEntry entry = new DelayedHitEntry(
                "actor", move.moveId(), "target", null, 3, "future_sight"
        );
        DelayedHitBinding binding = DelayedHitBindingResolver.bind(state, entry);

        AppliedActionResult result = RuntimeMoveResolution.applyDelayedUsingAuthoritativeCombatState(
                state,
                binding,
                "Delayed",
                new PythonRandom(7),
                legacyInput(2),
                false,
                false,
                BattleRuntimeDependencies.empty()
        );

        assertLandingContract(result.events(), state, target, DELAYED_LANDING);
    }

    private static void assertLandingContract(
            List<? extends BattleEvent> events,
            BattleRuntimeState state,
            RuntimeCombatantState target,
            GridCoord landing
    ) {
        int moveIndex = indexOf(events, MoveResolvedEvent.class);
        int hazardIndex = indexOf(events, TerrainHazardEvent.class);
        assertTrue(moveIndex >= 0, "producer must expose its resolved move event");
        assertTrue(hazardIndex > moveIndex, "landing hazard must follow the resolved move event");

        MoveResolvedEvent move = assertInstanceOf(MoveResolvedEvent.class, events.get(moveIndex));
        TerrainHazardEvent hazard = assertInstanceOf(TerrainHazardEvent.class, events.get(hazardIndex));
        assertTrue(move.hit());
        assertEquals(target.combatantId(), move.targetId());
        assertEquals(landing, target.position());
        assertTrue(state.hasStatus(target.combatantId(), "Slowed"));
        assertTrue(state.tileTrapsAt(landing).isEmpty());
        assertEquals("trigger", hazard.effect());
        assertEquals(target.combatantId(), hazard.actorId());
        assertEquals("sticky_trap", hazard.trapKey());
        assertEquals("Sticky Trap", hazard.trapName());
        assertEquals("trap-source", hazard.sourceId());
        assertEquals(target.hp(), hazard.targetHp());
        assertEquals(landing, hazard.coordinate());
        assertEquals(Set.of("forest"), hazard.terrains());
    }

    private static int indexOf(List<? extends BattleEvent> events, Class<? extends BattleEvent> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i))) return i;
        }
        return -1;
    }

    private static void addStickyTrap(BattleRuntimeState state, GridCoord landing) {
        state.putTileTrapFromRuntime(
                landing,
                new TileEntryTrapResolution.TrapLayer(
                        "sticky_trap", 1, "trap-source", "red", Set.of("forest"), "Sticky Trap"
                )
        );
    }

    private static MoveChoice combatantChoice(MoveOption move, RuntimeCombatantState target) {
        return new MoveChoice(
                "actor",
                move.moveId(),
                ChoiceTargetMode.COMBATANT,
                target.combatantId(),
                target.position(),
                ActionType.STANDARD
        );
    }

    private static BattleRuntimeState simpleState(
            RuntimeCombatantState actor,
            RuntimeCombatantState target,
            MoveOption move,
            int width,
            int height
    ) {
        return new BattleRuntimeState(
                new MovementGrid(width, height, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("actor", List.of(move))
        );
    }

    private static RuntimeCombatantState simpleCombatant(
            String id,
            GridCoord position,
            int hp,
            int maxHp
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 6),
                hp,
                maxHp,
                new ActionBudget()
        );
    }

    private static RuntimeCombatantState profiledCombatant(
            String id,
            GridCoord position,
            int hp,
            CombatantStatProfile stats
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 6),
                hp,
                hp,
                new ActionBudget(),
                stats,
                new EvasionProfile(stats, 0, 0, 0, false, false)
        );
    }

    private static CombatantStatProfile profile(
            int attack,
            int defense,
            int specialAttack,
            int specialDefense,
            int speed
    ) {
        return new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, attack,
                        CombatStat.DEF, defense,
                        CombatStat.SPATK, specialAttack,
                        CombatStat.SPDEF, specialDefense,
                        CombatStat.SPD, speed
                ),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveOption directPushMove() {
        return new MoveOption(
                "ram",
                new MoveSpec(
                        "Melee", "Melee", 1, 1, null, null, "Melee",
                        List.of("push 2"), "Push the target 2 meters."
                ),
                ActionType.STANDARD,
                true
        );
    }

    private static MoveOption areaPushMove() {
        return new MoveOption(
                "push-burst",
                new MoveSpec(
                        "Ranged", "Ranged", 6, 6, "Burst", 1, "Burst 1",
                        List.of("push 2"), "Push each target 2 meters."
                ),
                ActionType.STANDARD,
                false,
                new MoveCombatProfile(null, 6, 20, "physical", "Normal"),
                "Scene x1"
        );
    }

    private static MoveOption delayedPushMove() {
        return new MoveOption(
                "future-sight-push",
                new MoveSpec(
                        "Ranged", "Ranged", 20, 20, null, null, "Ranged",
                        List.of("push 2"), "Push the target 2 meters."
                ),
                ActionType.STANDARD,
                true,
                new MoveCombatProfile(2, 8, 20, "physical"),
                "Scene x1"
        );
    }

    private static MoveResolutionInput legacyInput(int moveAc) {
        return new MoveResolutionInput(
                moveAc,
                0,
                0,
                20,
                false,
                false,
                false,
                8,
                30,
                12,
                false,
                1.0,
                List.of()
        );
    }
}
