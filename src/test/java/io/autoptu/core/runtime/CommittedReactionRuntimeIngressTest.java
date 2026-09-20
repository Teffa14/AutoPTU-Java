package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommittedReactionRuntimeIngressTest {
    @Test
    void delegatesExactlyOnceWithValidatedNoDoubleSpendFlags() {
        CommittedReactionRuntimeExecutionPlan plan = CommittedReactionRuntimeExecutionPlanTestFixture.plan();
        AtomicInteger calls = new AtomicInteger();

        String result = CommittedReactionRuntimeIngress.resolve(plan, (received, spend, preDamage, validated) -> {
            calls.incrementAndGet();
            assertSame(plan, received);
            assertFalse(spend);
            assertTrue(preDamage);
            assertTrue(validated);
            return "resolved";
        });

        assertEquals("resolved", result);
        assertEquals(1, calls.get());
    }
}
