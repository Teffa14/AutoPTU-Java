package io.autoptu.core.runtime;

import io.autoptu.core.event.RoundStartedEvent;
import io.autoptu.core.rules.ActionSpendResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveSpecialTargetSpendTransportTest {
    @Test
    void targetCompositionRetainsSpendAcrossPrependAndAppend() {
        ActionSpendResult spend = ActionSpendResult.extra("feature:quick-switch");
        RoundStartedEvent before = new RoundStartedEvent(2, List.of(), "Clear", List.of());
        RoundStartedEvent resolved = new RoundStartedEvent(3, List.of(), "Rain", List.of());
        RoundStartedEvent after = new RoundStartedEvent(4, List.of(), "Clear", List.of());
        MoveSpecialTargetResult target = new MoveSpecialTargetResult(
                new AppliedActionResult(List.of(resolved), spend),
                Map.of("hit", true),
                7
        );

        MoveSpecialTargetResult composed = target
                .prependEvents(List.of(before))
                .appendEvents(List.of(after));

        assertEquals(List.of(before, resolved, after), composed.events());
        assertEquals(spend, composed.actionResult().actionSpendResult().orElseThrow());
        assertEquals(Map.of("hit", true), composed.resultSnapshot());
        assertEquals(7, composed.damageDealt());
    }

    @Test
    void emptyCompositionKeepsSameSpendReceipt() {
        ActionSpendResult spend = ActionSpendResult.standardConversion();
        MoveSpecialTargetResult target = new MoveSpecialTargetResult(
                new AppliedActionResult(List.of(), spend),
                Map.of(),
                0
        );

        MoveSpecialTargetResult composed = target
                .prependEvents(List.of())
                .appendEvents(List.of());

        assertEquals(spend, composed.actionResult().actionSpendResult().orElseThrow());
        assertEquals(List.of(), composed.events());
    }
}
