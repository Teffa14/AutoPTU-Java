package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.PreResolutionTargetHookRegistry;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimePreResolutionTargetApplicationTest {
    @Test
    void replacementUsesAuthoritativeCombatantAnchorAndPreservesDeclarationMetadata() {
        BattleRuntimeState state = state();
        PreResolutionTargetHookRegistry registry = PreResolutionTargetHookRegistry.builder()
                .register("intercept", HookSource.REACTION, 10, (context, current) ->
                        current.replaceTarget("interceptor", List.of(new RuleEffectEvent(
                                "reaction", "Intercept", "attacker", "interceptor", "Tackle",
                                "target_redirect", 0.0, 10
                        ))))
                .build();

        MoveChoice declared = new MoveChoice(
                "attacker", "Tackle", ChoiceTargetMode.COMBATANT, "target",
                new GridCoord(5, 1), ActionType.STANDARD
        );
        RuntimePreResolutionTargetApplication.Result result =
                RuntimePreResolutionTargetApplication.resolve(state, declared, registry);

        assertEquals("interceptor", result.effectiveChoice().targetId());
        assertEquals(new GridCoord(3, 1), result.effectiveChoice().targetAnchor());
        assertEquals(declared.actorId(), result.effectiveChoice().actorId());
        assertEquals(declared.moveId(), result.effectiveChoice().moveId());
        assertEquals(declared.actionType(), result.effectiveChoice().actionType());
        assertEquals(1, result.events().size());
        assertEquals("Intercept", ((RuleEffectEvent) result.events().getFirst()).sourceName());
    }

    @Test
    void noReplacementStillRefreshesAnchorFromAuthoritativeState() {
        BattleRuntimeState state = state();
        MoveChoice staleAnchor = new MoveChoice(
                "attacker", "Tackle", ChoiceTargetMode.COMBATANT, "target",
                new GridCoord(4, 1), ActionType.STANDARD
        );

        RuntimePreResolutionTargetApplication.Result result = RuntimePreResolutionTargetApplication.resolve(
                state, staleAnchor, PreResolutionTargetHookRegistry.builder().build()
        );

        assertEquals("target", result.effectiveChoice().targetId());
        assertEquals(new GridCoord(5, 1), result.effectiveChoice().targetAnchor());
        assertEquals(List.of(), result.events());
    }

    @Test
    void adaptersCannotInvokeTargetApplicationBoundaryPublicly() throws Exception {
        Method method = RuntimePreResolutionTargetApplication.class.getDeclaredMethod(
                "resolve", BattleRuntimeState.class, MoveChoice.class, PreResolutionTargetHookRegistry.class
        );
        assertFalse(Modifier.isPublic(method.getModifiers()));
    }

    private static BattleRuntimeState state() {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(
                        combatant("attacker", 1, 1),
                        combatant("target", 5, 1),
                        combatant("interceptor", 3, 1)
                ),
                123L
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                30,
                30,
                new ActionBudget()
        );
    }
}
