package io.autoptu.core.hook;

import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.rules.ActionSpendResult;
import io.autoptu.core.runtime.AppliedActionResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttackOfOpportunityInstructionHandlerTest {
    @Test
    void delegatesCommittedAoOOnceAndPreservesAuthoritativeEventOrder() {
        List<CommittedActionWindowInstruction> executed = new ArrayList<>();
        List<BattleEvent> authoritativeEvents = List.of(
                new ActionResolvedEvent("reactor-1", "attack-of-opportunity", "attack_started"),
                ActionResolvedEvent.targeted("reactor-1", "attack-of-opportunity", List.of("triggering-foe"))
        );
        AttackOfOpportunityInstructionHandler handler = new AttackOfOpportunityInstructionHandler(instruction -> {
            executed.add(instruction);
            return new AppliedActionResult(authoritativeEvents);
        });
        CommittedActionWindowInstruction instruction = instruction("triggering-foe");

        ActionWindowExecutionResult result = handler.execute(instruction);

        assertEquals(List.of(instruction), executed);
        assertEquals(authoritativeEvents, result.events());
        assertEquals(ActionSpendResult.Source.FREE, instruction.spend().source());
    }

    @Test
    void refusesAoOWithoutFrozenTriggeringCombatant() {
        AttackOfOpportunityInstructionHandler handler = new AttackOfOpportunityInstructionHandler(
                ignored -> new AppliedActionResult(List.of())
        );
        assertThrows(IllegalArgumentException.class, () -> handler.execute(instruction("")));
    }

    @Test
    void refusesWrongActionKeyBeforeCallingAuthoritativeExecutor() {
        List<CommittedActionWindowInstruction> executed = new ArrayList<>();
        AttackOfOpportunityInstructionHandler handler = new AttackOfOpportunityInstructionHandler(instruction -> {
            executed.add(instruction);
            return new AppliedActionResult(List.of());
        });
        CommittedActionWindowInstruction wrong = new CommittedActionWindowInstruction(
                "reactor-1", "interrupt", "shift:left-threatened-square", "triggering-foe",
                ActionType.FREE, "Attack of Opportunity", ActionSpendResult.free()
        );

        assertThrows(IllegalArgumentException.class, () -> handler.execute(wrong));
        assertEquals(List.of(), executed);
    }

    private static CommittedActionWindowInstruction instruction(String triggeringCombatantId) {
        return new CommittedActionWindowInstruction(
                "reactor-1",
                AttackOfOpportunityInstructionHandler.ACTION_KEY,
                "shift:left-threatened-square",
                triggeringCombatantId,
                ActionType.FREE,
                "Attack of Opportunity",
                ActionSpendResult.free()
        );
    }
}
