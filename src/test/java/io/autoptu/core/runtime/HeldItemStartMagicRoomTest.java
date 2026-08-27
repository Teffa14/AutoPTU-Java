package io.autoptu.core.runtime;

import io.autoptu.core.hook.HeldItemStartLifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeldItemStartMagicRoomTest {
    @Test
    void magicRoomSuppressesHeldItemStartMaterialization() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("actor", List.of(new HeldItemState("slot-0", "X Accuracy")))
        );
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState(
                "", "", Set.of(), Map.of(), Map.of(), false, false,
                null,
                List.of(),
                List.of(new FieldEffectEntry(FieldEffectKind.ROOM, "Magic Room", 2))
        ));
        HeldItemRuleCatalog catalog = new HeldItemRuleCatalog(Map.of(
                "x accuracy",
                new HeldItemStartRuleProfile(
                        List.of(), List.of(), 2, null, null, null, null, null, null
                )
        ));

        new HeldItemStartLifecycleHook(catalog).apply(
                new LifecycleHookContext(state, LifecycleHookPoint.TURN_START, 0, 1, "actor")
        );

        assertEquals(Map.of(), actor.temporaryEffects().snapshot());
    }
}
