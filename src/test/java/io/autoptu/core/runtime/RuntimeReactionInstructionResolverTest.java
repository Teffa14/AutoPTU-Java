package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.hook.ReactionEligibilityPolicy;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeReactionInstructionResolverTest {
    private static final String REACTION = "attack_of_opportunity";

    @Test
    void successfulCommitFreezesCanonicalReactionAndTriggerTargetWithoutExecutingIt() {
        BattleRuntimeState state = battle();
        RuntimeReactionWindowResolver.Candidate candidate = candidate(state);
        RuntimeReactionWindowCommitter.CommitResult committed = new RuntimeReactionWindowCommitter(state)
                .commit(candidate, ReactionEligibilityPolicy.attackOfOpportunity());

        RuntimeReactionInstruction instruction = new RuntimeReactionInstructionResolver(state)
                .resolve(candidate, committed)
                .orElseThrow();

        assertEquals(REACTION, instruction.reactionKey());
        assertEquals("reactor", instruction.reactorId());
        assertEquals("actor", instruction.targetCombatantId());
        assertEquals(candidate.window().windowKey(), instruction.windowKey());
        assertEquals(candidate.window().triggeringEventKey(), instruction.triggeringEventKey());
        assertSame(state.moveOptions("reactor").getFirst(), instruction.move());
        assertEquals(20, state.requireCombatant("actor").hp());
    }

    @Test
    void rejectedCommitProducesNoInstruction() {
        BattleRuntimeState state = battle();
        RuntimeReactionWindowResolver.Candidate candidate = candidate(state);
        RuntimeReactionWindowCommitter committer = new RuntimeReactionWindowCommitter(state);
        assertTrue(committer.commit(candidate, ReactionEligibilityPolicy.attackOfOpportunity()).committed());
        RuntimeReactionWindowCommitter.CommitResult replay = committer.commit(
                candidate,
                ReactionEligibilityPolicy.attackOfOpportunity()
        );

        Optional<RuntimeReactionInstruction> instruction = new RuntimeReactionInstructionResolver(state)
                .resolve(candidate, replay);

        assertFalse(instruction.isPresent());
        assertEquals(20, state.requireCombatant("actor").hp());
    }

    private static RuntimeReactionWindowResolver.Candidate candidate(BattleRuntimeState state) {
        return new RuntimeReactionWindowResolver(state).discoverShiftWindows(
                REACTION,
                new BattleEventOccurrence(
                        7,
                        new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0))
                ),
                ReactionEligibilityPolicy.attackOfOpportunity()
        ).eligible().getFirst();
    }

    private static BattleRuntimeState battle() {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(
                        combatant("reactor", new GridCoord(0, 0)),
                        combatant("actor", new GridCoord(2, 0))
                ),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "reactor", CombatantAffiliationState.active("blue"),
                        "actor", CombatantAffiliationState.active("red")
                ),
                Map.of(
                        "reactor", List.of(move("Attack of Opportunity")),
                        "actor", List.of(move("Tackle"))
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 6),
                20,
                20,
                new ActionBudget()
        );
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }
}
