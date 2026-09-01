package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMoveResolutionDependenciesContractTest {
    @Test
    void directProductionBoundaryAcceptsRuntimeDependencies() {
        assertDependencyAwareOverload("applyUsingAuthoritativeCombatState");
    }

    @Test
    void areaProductionBoundaryAcceptsRuntimeDependencies() {
        assertDependencyAwareOverload("applyAreaUsingAuthoritativeCombatState");
    }

    @Test
    void delayedProductionBoundaryAcceptsRuntimeDependencies() {
        assertDependencyAwareOverload("applyDelayedUsingAuthoritativeCombatState");
    }

    private static void assertDependencyAwareOverload(String methodName) {
        boolean present = Arrays.stream(RuntimeMoveResolution.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .map(Method::getParameterTypes)
                .anyMatch(parameterTypes -> Arrays.asList(parameterTypes)
                        .contains(BattleRuntimeDependencies.class));

        assertTrue(present, methodName
                + " must accept BattleRuntimeDependencies so the authoritative rule-content snapshot "
                + "cannot be dropped before post-hit forced movement");
    }
}
