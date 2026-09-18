package io.autoptu.core.hook;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.rules.ActionSpendResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionWindowInstructionExecutorRegistryTest {

    @Test
    void dispatchesCommittedInstructionWithoutChangingFrozenProvenance() {
        ActionWindowInstructionExecutorRegistry registry = new ActionWindowInstructionExecutorRegistry();
        List<CommittedActionWindowInstruction> executed = new ArrayList<>();
        registry.register("attack-of-opportunity", instruction -> {
            executed.add(instruction);
            return ActionWindowExecutionResult.empty();
        });

        CommittedActionWindowInstruction instruction = instruction("Attack-Of-Opportunity");

        assertTrue(registry.canExecute(instruction));
        ActionWindowExecutionResult result = registry.execute(instruction);

        assertEquals(List.of(instruction), executed);
        assertEquals(ActionSpendResult.Source.FREE, executed.get(0).spend().source());
        assertEquals("shift:left-threatened-square", executed.get(0).triggerKey());
        assertEquals(List.of(), result.events());
    }

    @Test
    void refusesUnknownCommittedActionInsteadOfDelegatingRulesOutsideCore() {
        ActionWindowInstructionExecutorRegistry registry = new ActionWindowInstructionExecutorRegistry();
        CommittedActionWindowInstruction instruction = instruction("unregistered-reaction");

        assertFalse(registry.canExecute(instruction));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.execute(instruction));
        assertTrue(error.getMessage().contains("unregistered-reaction"));
    }

    @Test
    void rejectsHandlerThatReturnsNoAuthoritativeResult() {
        ActionWindowInstructionExecutorRegistry registry = new ActionWindowInstructionExecutorRegistry();
        registry.register("attack-of-opportunity", ignored -> null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> registry.execute(instruction("attack-of-opportunity"))
        );
        assertTrue(error.getMessage().contains("no execution result"));
    }

    @Test
    void rejectsDuplicateHandlersForSameNormalizedActionKey() {
        ActionWindowInstructionExecutorRegistry registry = new ActionWindowInstructionExecutorRegistry();
        registry.register("Attack-Of-Opportunity", ignored -> ActionWindowExecutionResult.empty());

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(
                        " attack-of-opportunity ",
                        ignored -> ActionWindowExecutionResult.empty()
                ));
    }

    private static CommittedActionWindowInstruction instruction(String actionKey) {
        return new CommittedActionWindowInstruction(
                "reactor-1",
                actionKey,
                "shift:left-threatened-square",
                ActionType.FREE,
                "Attack of Opportunity",
                ActionSpendResult.free()
        );
    }
}
