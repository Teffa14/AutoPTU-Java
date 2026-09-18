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
        registry.register("attack-of-opportunity", executed::add);

        CommittedActionWindowInstruction instruction = instruction("Attack-Of-Opportunity");

        assertTrue(registry.canExecute(instruction));
        registry.execute(instruction);

        assertEquals(List.of(instruction), executed);
        assertEquals(ActionSpendResult.Source.FREE, executed.get(0).spend().source());
        assertEquals("shift:left-threatened-square", executed.get(0).triggerKey());
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
    void rejectsDuplicateHandlersForSameNormalizedActionKey() {
        ActionWindowInstructionExecutorRegistry registry = new ActionWindowInstructionExecutorRegistry();
        registry.register("Attack-Of-Opportunity", ignored -> { });

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(" attack-of-opportunity ", ignored -> { }));
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
