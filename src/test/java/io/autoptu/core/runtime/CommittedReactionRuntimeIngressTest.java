package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CommittedReactionRuntimeIngressTest {
    @Test
    void rejectsMissingValidatedPlanBeforeResolverRuns() {
        assertThrows(NullPointerException.class, () ->
                CommittedReactionRuntimeIngress.resolve(null, (plan, spend, preDamage, validated) -> "unreachable")
        );
    }
}
