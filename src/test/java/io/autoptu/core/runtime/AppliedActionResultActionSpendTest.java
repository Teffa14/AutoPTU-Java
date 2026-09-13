package io.autoptu.core.runtime;

import io.autoptu.core.event.RoundStartedEvent;
import io.autoptu.core.rules.ActionSpendResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppliedActionResultActionSpendTest {
    @Test
    void legacyConstructorKeepsSpendMetadataAbsent() {
        AppliedActionResult result = new AppliedActionResult(List.of());

        assertTrue(result.actionSpendResult().isEmpty());
    }

    @Test
    void runtimeResultCanRetainExactNamedExtraGrantWithoutAddingAnEvent() {
        ActionSpendResult spend = ActionSpendResult.extra("feature:quick-switch");

        AppliedActionResult result = new AppliedActionResult(List.of(), spend);

        assertEquals(spend, result.actionSpendResult().orElseThrow());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void runtimeResultRetainsStandardConversionSource() {
        ActionSpendResult spend = ActionSpendResult.standardConversion();

        AppliedActionResult result = new AppliedActionResult(List.of(), spend);

        assertEquals(ActionSpendResult.Source.STANDARD_CONVERSION,
                result.actionSpendResult().orElseThrow().source());
    }

    @Test
    void prependingSemanticEventsRetainsExactSpendProvenanceAndOrder() {
        ActionSpendResult spend = ActionSpendResult.extra("feature:quick-switch");
        RoundStartedEvent before = new RoundStartedEvent(3, List.of(), "Clear", List.of());
        RoundStartedEvent existing = new RoundStartedEvent(4, List.of(), "Rain", List.of());
        AppliedActionResult result = new AppliedActionResult(List.of(existing), spend);

        AppliedActionResult composed = result.prependEvents(List.of(before));

        assertEquals(List.of(before, existing), composed.events());
        assertEquals(spend, composed.actionSpendResult().orElseThrow());
    }

    @Test
    void prependingEventsRejectsNullEntriesWithoutLosingOriginalResult() {
        ActionSpendResult spend = ActionSpendResult.standardConversion();
        AppliedActionResult result = new AppliedActionResult(List.of(), spend);

        assertThrows(IllegalArgumentException.class,
                () -> result.prependEvents(java.util.Arrays.asList((io.autoptu.core.event.BattleEvent) null)));
        assertEquals(spend, result.actionSpendResult().orElseThrow());
        assertTrue(result.events().isEmpty());
    }
}
