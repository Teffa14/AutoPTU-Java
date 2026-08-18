package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.RuleEffectEvent;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RuntimeAuthoritativePinkPearlTest {
    @Test
    void canonicalPinkPearlAddsFiveToPsychicDamage() {
        AppliedActionResult withPearl = resolve(state(true), move("confusion", "Psychic", "special"), 401);
        AppliedActionResult clean = resolve(state(false), move("confusion", "Psychic", "special"), 401);

        assertEquals(moveEvent(clean).damage() + 5, moveEvent(withPearl).damage());
    }

    @Test
    void pinkPearlDoesNotModifyNonPsychicMoves() {
        AppliedActionResult withPearl = resolve(state(true), move("water-gun", "Water", "special"), 509);
        AppliedActionResult clean = resolve(state(false), move("water-gun", "Water", "special"), 509);

        assertEquals(moveEvent(clean).stableKey(), moveEvent(withPearl).stableKey());
    }

    @Test
    void itemEffectEventPrecedesResolvedMoveEvent() {
        AppliedActionResult result = resolve(state(true), move("confusion", "Psychic", "special"), 613);

        assertEquals(2, result.events().size());
        RuleEffectEvent itemEvent = assertInstanceOf(RuleEffectEvent.class, result.events().get(0));
        assertEquals("item", itemEvent.sourceKind());
        assertEquals("Pink Pearl", itemEvent.sourceName());
        assertEquals("damage_flat", itemEvent.effect());
        assertEquals(5.0, itemEvent.amount());
        assertInstanceOf(MoveResolvedEvent.class, result.events().get(1));
    }

    private static AppliedActionResult resolve(BattleRuntimeState state, MoveOption move, int seed) {
        MoveChoice choice = new MoveChoice(
                "actor",
                move.moveId(),
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );
        return RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state,
                choice,
                move,
                "Medium",
                "Medium",
                Set.of(),
                "AI",
                new PythonRandom(seed),
                input(),
                false,
                false
        );
    }

    private static BattleRuntimeState state(boolean withPinkPearl) {
        CombatantStatProfile actorStats = stats(12, 12, 20, 12);
        CombatantStatProfile targetStats = stats(12, 16, 12, 16);
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                100,
                100,
                new ActionBudget(),
                actorStats,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of("Normal")
        );
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy",
                MovementProfile.walking(new GridCoord(2, 1), 3),
                100,
                100,
                new ActionBudget(),
                targetStats,
                new EvasionProfile(targetStats, 0, 0, 0, false, false),
                0,
                false,
                false,
                false,
                false,
                List.of("Normal")
        );
        Map<String, List<HeldItemState>> heldItems = withPinkPearl
                ? Map.of("actor", List.of(new HeldItemState("actor-item-0", "Pink Pearl")))
                : Map.of("actor", List.of());
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                heldItems
        );
    }

    private static CombatantStatProfile stats(int attack, int defense, int specialAttack, int specialDefense) {
        return new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, attack,
                        CombatStat.DEF, defense,
                        CombatStat.SPATK, specialAttack,
                        CombatStat.SPDEF, specialDefense
                ),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveResolutionInput input() {
        return new MoveResolutionInput(
                99,
                -99,
                -6,
                20,
                false,
                false,
                false,
                99,
                999,
                999,
                false,
                1.0,
                List.of()
        );
    }

    private static MoveOption move(String moveId, String type, String category) {
        return MoveOption.standard(
                moveId,
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 4, 20, category, type)
        );
    }

    private static MoveResolvedEvent moveEvent(AppliedActionResult result) {
        for (BattleEvent event : result.events()) {
            if (event instanceof MoveResolvedEvent moveResolved) {
                return moveResolved;
            }
        }
        throw new AssertionError("move resolved event missing");
    }
}
