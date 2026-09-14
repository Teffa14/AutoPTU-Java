package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionTimingContractTest {
    @Test
    void parsesInterruptTraitWithoutSpendingOrdinaryActionBudget() {
        ReactionTimingContract contract = ReactionTimingContract.fromTrait("interrupt-1");

        assertEquals(ReactionTimingContract.Timing.INTERRUPT, contract.timing());
        assertEquals(OptionalInt.of(1), contract.rank());
        assertFalse(contract.spendsOrdinaryActionBudget());
    }

    @Test
    void parsesPriorityTraitAsSeparateTimingFamily() {
        ReactionTimingContract contract = ReactionTimingContract.fromTrait("Priority 20");

        assertEquals(ReactionTimingContract.Timing.PRIORITY, contract.timing());
        assertEquals(OptionalInt.of(20), contract.rank());
        assertFalse(contract.spendsOrdinaryActionBudget());
    }

    @Test
    void unrelatedTraitRemainsOrdinary() {
        ReactionTimingContract contract = ReactionTimingContract.fromTrait("contact");

        assertEquals(ReactionTimingContract.Timing.ORDINARY, contract.timing());
        assertTrue(contract.rank().isEmpty());
        assertTrue(contract.spendsOrdinaryActionBudget());
    }
}
