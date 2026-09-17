package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RuntimeMoveReactionProjectionTest {
    @Test
    void appendsRangedOccurrenceAfterAuthoritativeMoveEvents() {
        AppliedActionResult base = new AppliedActionResult(List.of());
        MoveChoice choice = choice("target-a");

        AppliedActionResult projected = RuntimeMoveReactionProjection.appendCompletedMoveOccurrence(
                base, choice, rangedMove());

        assertEquals(1, projected.events().size());
        ActionResolvedEvent event = (ActionResolvedEvent) projected.events().getFirst();
        assertEquals("actor", event.actorId());
        assertEquals("ranged_attack", event.actionKind());
        assertEquals(List.of("target-a"), event.targetIds());
    }

    @Test
    void preservesAuthoritativeMultiTargetOrder() {
        AppliedActionResult projected = RuntimeMoveReactionProjection.appendCompletedMoveOccurrence(
                new AppliedActionResult(List.of()), choice(""), rangedMove(), List.of("target-b", "target-a"));

        ActionResolvedEvent event = (ActionResolvedEvent) projected.events().getFirst();
        assertEquals(List.of("target-b", "target-a"), event.targetIds());
    }

    @Test
    void leavesNonRangedResultsUntouched() {
        AppliedActionResult base = new AppliedActionResult(List.of());
        MoveOption melee = MoveOption.standard("tackle",
                new MoveSpec("melee", "melee", 1, 1, null, null, "Melee"));

        AppliedActionResult projected = RuntimeMoveReactionProjection.appendCompletedMoveOccurrence(
                base, choice("target-a"), melee);

        assertSame(base, projected);
    }

    private static MoveChoice choice(String targetId) {
        return new MoveChoice("actor", "shot", ChoiceTargetMode.COMBATANT, targetId,
                new GridCoord(3, 0), ActionType.STANDARD);
    }

    private static MoveOption rangedMove() {
        return MoveOption.standard("shot",
                new MoveSpec("combatant", "ranged", 6, 6, null, null, "Ranged 6"));
    }
}
