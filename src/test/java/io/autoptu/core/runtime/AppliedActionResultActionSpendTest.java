package io.autoptu.core.runtime;

import io.autoptu.core.rules.ActionSpendResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
