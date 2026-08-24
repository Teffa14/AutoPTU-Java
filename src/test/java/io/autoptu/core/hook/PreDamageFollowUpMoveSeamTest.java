package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.runtime.BattleRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PreDamageFollowUpMoveSeamTest {
    @Test
    void followUpExecutesSynchronouslyInsideReactionHook() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()), List.of()
        );
        AtomicBoolean followUpRan = new AtomicBoolean(false);
        GridCoord targetPosition = new GridCoord(2, 1);
        PreDamageFollowUpMoveExecutor executor = request -> {
            assertEquals("attacker", request.attackerId());
            assertEquals("attacker", request.targetId());
            assertEquals(targetPosition, request.targetPosition());
            followUpRan.set(true);
            return PreDamageFollowUpMoveResult.empty();
        };
        PreDamageReactionContext context = new PreDamageReactionContext(
                state,
                "attacker",
                "defender",
                "Slash",
                "Slash",
                "melee",
                List.of(),
                OutOfTurnDecisionGate.allowWhenUnconfigured(),
                executor
        );
        PreDamageReactionHookRegistry registry = PreDamageReactionHookRegistry.builder()
                .register("sway-style", HookSource.ABILITY, 10, (hookContext, current) -> {
                    assertFalse(followUpRan.get());
                    hookContext.resolveFollowUpMove(
                            PreDamageFollowUpMoveRequest.originalMove("attacker", "attacker", targetPosition)
                    );
                    assertTrue(followUpRan.get(), "follow-up resolution must complete before the hook continues");
                    return current.cancelHit(List.of());
                })
                .build();

        PreDamageReactionResult result = registry.resolve(
                context,
                PreDamageReactionResult.of(true, 12, 1.0)
        );

        assertTrue(followUpRan.get());
        assertFalse(result.hit());
        assertEquals(0, result.damage());
    }

    @Test
    void defaultContextFailsClosedWhenFollowUpExecutionIsUnavailable() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(2, 2, Set.of(), Map.of()), List.of()
        );
        PreDamageReactionContext context = PreDamageReactionContext.of(
                state, "attacker", "defender", "Magic Coat", List.of()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> context.resolveFollowUpMove(
                        PreDamageFollowUpMoveRequest.originalMove("defender", "attacker", null)
                )
        );
    }

    @Test
    void requestRejectsMissingCanonicalIdentities() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PreDamageFollowUpMoveRequest.originalMove("", "target", null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PreDamageFollowUpMoveRequest.originalMove("actor", "", null)
        );
    }
}
