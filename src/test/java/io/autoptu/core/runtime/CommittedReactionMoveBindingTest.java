package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommittedReactionMoveBindingTest {
    @Test
    void acceptsServerBoundReactorMoveAndTriggeringTarget() {
        CommittedReactionMoveBinding binding = binding("reactor-1", "triggering-foe", "struggle");
        assertDoesNotThrow(() -> binding.requireParticipants("reactor-1", "triggering-foe"));
    }

    @Test
    void rejectsActorOrTargetSubstitutionAfterCommit() {
        CommittedReactionMoveBinding binding = binding("reactor-1", "triggering-foe", "struggle");
        assertThrows(IllegalArgumentException.class, () -> binding.requireParticipants("other-reactor", "triggering-foe"));
        assertThrows(IllegalArgumentException.class, () -> binding.requireParticipants("reactor-1", "other-foe"));
    }

    @Test
    void rejectsMoveIdentitySubstitution() {
        MoveChoice choice = choice("reactor-1", "triggering-foe", "struggle");
        MoveOption otherMove = MoveOption.standard("tackle", spec());
        assertThrows(IllegalArgumentException.class, () -> new CommittedReactionMoveBinding(otherMove, choice));
    }

    private static CommittedReactionMoveBinding binding(String actorId, String targetId, String moveId) {
        return new CommittedReactionMoveBinding(MoveOption.standard(moveId, spec()), choice(actorId, targetId, moveId));
    }

    private static MoveChoice choice(String actorId, String targetId, String moveId) {
        return new MoveChoice(
                actorId,
                moveId,
                ChoiceTargetMode.COMBATANT,
                targetId,
                new GridCoord(1, 0),
                ActionType.FREE
        );
    }

    private static MoveSpec spec() {
        return new MoveSpec("1 Target", "Melee", 1, 1, null, null, "Melee");
    }
}
