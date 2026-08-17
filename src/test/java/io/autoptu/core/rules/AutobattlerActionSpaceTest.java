package io.autoptu.core.rules;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.action.TargetCandidate;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutobattlerActionSpaceTest {
    @Test
    void shiftChoicesExcludeOriginAndAreDeterministicallySorted() {
        MovementGrid grid = grid(5, 5);
        MovementProfile actor = MovementProfile.walking(new GridCoord(2, 2), 1);

        List<ShiftChoice> choices = AutobattlerActionSpace.legalShiftChoices(
                "actor", grid, actor, new ActionBudget(), 0, ignored -> true
        );

        assertEquals(
                List.of(
                        "shift|actor|1,2",
                        "shift|actor|2,1",
                        "shift|actor|2,3",
                        "shift|actor|3,2"
                ),
                choices.stream().map(ShiftChoice::stableKey).toList()
        );
        assertFalse(choices.stream().anyMatch(choice -> choice.destination().equals(actor.position())));
    }

    @Test
    void exhaustedShiftBudgetRemovesMovementChoices() {
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.SHIFT, "already moved");

        assertTrue(AutobattlerActionSpace.legalShiftChoices(
                "actor",
                grid(5, 5),
                MovementProfile.walking(new GridCoord(2, 2), 2),
                budget,
                0,
                ignored -> true
        ).isEmpty());

        budget.grantExtra(ActionType.SHIFT);
        assertFalse(AutobattlerActionSpace.legalShiftChoices(
                "actor",
                grid(5, 5),
                MovementProfile.walking(new GridCoord(2, 2), 1),
                budget,
                0,
                ignored -> true
        ).isEmpty());
    }

    @Test
    void directMovesEnumerateOnlyCombatantsInRangeAndLineOfSight() {
        GridCoord actor = new GridCoord(1, 1);
        MoveOption ranged = MoveOption.standard("water-gun", move("Ranged", 3, null, null));
        List<TargetCandidate> targets = List.of(
                new TargetCandidate("near", new GridCoord(3, 1), "Medium"),
                new TargetCandidate("blocked", new GridCoord(1, 3), "Medium"),
                new TargetCandidate("far", new GridCoord(5, 5), "Medium")
        );

        List<MoveChoice> choices = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Medium",
                grid(7, 7),
                actor,
                new ActionBudget(),
                List.of(ranged),
                targets,
                Set.of(new GridCoord(1, 2))
        );

        assertEquals(1, choices.size());
        assertEquals("near", choices.getFirst().targetId());
        assertEquals(ChoiceTargetMode.COMBATANT, choices.getFirst().targetMode());
    }

    @Test
    void footprintsAreUsedForDirectMeleeRange() {
        MoveOption melee = MoveOption.standard("tackle", move("Melee", 1, null, null));
        List<MoveChoice> choices = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Large",
                grid(8, 8),
                new GridCoord(0, 0),
                new ActionBudget(),
                List.of(melee),
                List.of(
                        new TargetCandidate("touching", new GridCoord(2, 0), "Medium"),
                        new TargetCandidate("too-far", new GridCoord(3, 0), "Medium")
                ),
                Set.of()
        );

        assertEquals(List.of("touching"), choices.stream().map(MoveChoice::targetId).toList());
    }

    @Test
    void areaMovesExposeTileAimSoAiCanAimAtEmptyMinecraftTiles() {
        MoveOption line = MoveOption.standard("flamethrower", move("Self", 0, "Line", 2));

        List<MoveChoice> choices = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Medium",
                grid(5, 5),
                new GridCoord(2, 2),
                new ActionBudget(),
                List.of(line),
                List.of(),
                Set.of()
        );

        assertFalse(choices.isEmpty());
        assertTrue(choices.stream().allMatch(choice -> choice.targetMode() == ChoiceTargetMode.TILE));
        assertTrue(choices.stream().anyMatch(choice -> choice.targetAnchor().equals(new GridCoord(4, 2))));
        assertTrue(choices.stream().noneMatch(choice -> choice.targetAnchor().equals(new GridCoord(2, 2))));
    }

    @Test
    void selfAndFieldMovesProduceSingleCanonicalChoices() {
        List<MoveChoice> choices = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Medium",
                grid(4, 4),
                new GridCoord(1, 1),
                new ActionBudget(),
                List.of(
                        MoveOption.standard("focus", move("Self", 0, null, null)),
                        MoveOption.standard("weather", move("Field", null, null, null))
                ),
                List.of(),
                Set.of()
        );

        assertEquals(2, choices.size());
        assertEquals(List.of(ChoiceTargetMode.SELF, ChoiceTargetMode.FIELD),
                choices.stream().map(MoveChoice::targetMode).toList());
    }

    @Test
    void standardBudgetRemovesStandardMovesButFreeMovesRemain() {
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.STANDARD, "used");
        MoveSpec spec = move("Ranged", 3, null, null);

        List<MoveChoice> choices = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Medium",
                grid(4, 4),
                new GridCoord(0, 0),
                budget,
                List.of(
                        MoveOption.standard("standard", spec),
                        new MoveOption("free", spec, ActionType.FREE, true)
                ),
                List.of(new TargetCandidate("target", new GridCoord(1, 0), "Medium")),
                Set.of()
        );

        assertEquals(List.of("free"), choices.stream().map(MoveChoice::moveId).toList());
    }

    @Test
    void combinedChoicesHaveStableOrderForAutobattlerIndexes() {
        List<BattleChoice> choices = AutobattlerActionSpace.legalChoices(
                "actor",
                "Medium",
                grid(5, 5),
                MovementProfile.walking(new GridCoord(2, 2), 1),
                new ActionBudget(),
                List.of(MoveOption.standard("tackle", move("Melee", 1, null, null))),
                List.of(new TargetCandidate("enemy", new GridCoord(3, 2), "Medium")),
                Set.of(),
                0,
                ignored -> true
        );

        List<String> keys = choices.stream().map(BattleChoice::stableKey).toList();
        assertEquals(keys.stream().sorted().toList(), keys);
    }

    private static MovementGrid grid(int width, int height) {
        return new MovementGrid(width, height, Set.of(), Map.of());
    }

    private static MoveSpec move(
            String targetKind,
            Integer targetRange,
            String areaKind,
            Integer areaValue
    ) {
        return new MoveSpec(
                targetKind,
                targetKind,
                targetRange,
                targetRange,
                areaKind,
                areaValue,
                targetKind
        );
    }
}
