package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeReactionInstructionExecutorTest {
    @Test
    void executesThroughOrdinaryMovePipelineWithoutSpendingOrdinaryTurnActions() {
        BattleRuntimeState state = battle();
        RuntimeReactionInstruction instruction = instruction(state);

        AppliedActionResult result = RuntimeReactionInstructionExecutor.execute(
                state,
                instruction,
                new PythonRandom(7),
                legacyInput(),
                BattleRuntimeDependencies.empty()
        );

        assertTrue(state.requireCombatant("actor").hp() < 100);
        assertTrue(state.requireCombatant("reactor").actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertTrue(state.requireCombatant("reactor").actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertTrue(state.requireCombatant("reactor").actionBudget().hasActionAvailable(ActionType.SWIFT));
        assertTrue(result.events().stream().anyMatch(event -> event.stableKey().contains("actor")));
    }

    @Test
    void rejectsStaleInstructionBeforeRngOrDamage() {
        BattleRuntimeState state = battle();
        RuntimeReactionInstruction current = instruction(state);
        RuntimeReactionInstruction stale = new RuntimeReactionInstruction(
                current.instructionKey(), current.windowKey(), current.reactionKey(), 1,
                current.reactorId(), current.targetCombatantId(), current.triggerKind(),
                current.triggeringEventKey(), current.move()
        );

        assertThrows(IllegalStateException.class, () -> RuntimeReactionInstructionExecutor.execute(
                state, stale, new PythonRandom(7), legacyInput(), BattleRuntimeDependencies.empty()
        ));
        assertEquals(100, state.requireCombatant("actor").hp());
    }

    private static RuntimeReactionInstruction instruction(BattleRuntimeState state) {
        return new RuntimeReactionInstruction(
                "reaction|window-1",
                "window-1",
                "attack_of_opportunity",
                state.currentRound(),
                "reactor",
                "actor",
                RuntimeReactionTriggerRegistry.TriggerKind.SHIFT_OUT_OF_ADJACENCY,
                "shift|actor|1,0|2,0",
                state.moveOptions("reactor").getFirst()
        );
    }

    private static BattleRuntimeState battle() {
        MoveOption reaction = MoveOption.standard(
                "Attack of Opportunity",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(2, 6, 20, "physical")
        );
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(
                        combatant("reactor", new GridCoord(0, 0), 100, profile(20, 8)),
                        combatant("actor", new GridCoord(1, 0), 100, profile(10, 5))
                ),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "reactor", CombatantAffiliationState.active("blue"),
                        "actor", CombatantAffiliationState.active("red")
                ),
                Map.of(
                        "reactor", List.of(reaction),
                        "actor", List.of()
                )
        );
    }

    private static RuntimeCombatantState combatant(
            String id, GridCoord position, int hp, CombatantStatProfile profile
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 6),
                hp,
                100,
                new ActionBudget(),
                profile,
                new EvasionProfile(profile, 0, 0, 0, false, false)
        );
    }

    private static CombatantStatProfile profile(int attack, int defense) {
        return new CombatantStatProfile(
                Map.of(CombatStat.ATK, attack, CombatStat.DEF, defense),
                Map.of(), Map.of(), Set.of()
        );
    }

    private static MoveResolutionInput legacyInput() {
        return new MoveResolutionInput(
                null, 0, 0, 20, false, false, false,
                0, 0, 0, false, 1.0, List.of()
        );
    }
}
