package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.HeldItemRuleCatalog;
import io.autoptu.core.runtime.HeldItemStartRuleProfile;
import io.autoptu.core.runtime.HeldItemStartTemporaryEffectResolution;
import io.autoptu.core.runtime.HeldItemState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeldItemStartLifecycleHookTest {
    @Test
    void appliesCanonicalProfileToServerOwnedHeldItemAtStart() {
        RuntimeCombatantState actor = actor();
        BattleRuntimeState state = state(actor, List.of(new HeldItemState("slot-0", "X Accuracy")));
        HeldItemRuleCatalog catalog = new HeldItemRuleCatalog(Map.of(
                "x-accuracy",
                new HeldItemStartRuleProfile(
                        List.of(new HeldItemStartTemporaryEffectResolution.StatAmount("atk", 2)),
                        List.of(),
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ));

        HeldItemStartLifecycleHook hook = new HeldItemStartLifecycleHook(catalog);
        LifecycleHookContext context = new LifecycleHookContext(
                state,
                LifecycleHookPoint.TURN_START,
                0,
                1,
                "actor"
        );

        hook.apply(context);
        hook.apply(context);

        assertEquals(
                List.of(new TemporaryEffectEntry("stat_modifier", Map.of(
                        "stat", "atk", "amount", 2, "source", "X Accuracy"
                ))),
                actor.temporaryEffects().getAll("stat_modifier")
        );
        assertEquals(1, actor.temporaryEffects().getAll("accuracy_bonus").size());
        assertEquals("X Accuracy", actor.temporaryEffects().getAll("accuracy_bonus").get(0).payload().get("source"));
    }

    @Test
    void ignoresEquippedItemsWithoutCanonicalRuleProfile() {
        RuntimeCombatantState actor = actor();
        BattleRuntimeState state = state(actor, List.of(new HeldItemState("slot-0", "Unknown Relic")));

        new HeldItemStartLifecycleHook(new HeldItemRuleCatalog(Map.of())).apply(
                new LifecycleHookContext(state, LifecycleHookPoint.TURN_START, 0, 1, "actor")
        );

        assertEquals(Map.of(), actor.temporaryEffects().snapshot());
    }

    private static BattleRuntimeState state(RuntimeCombatantState actor, List<HeldItemState> items) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("actor", items)
        );
    }

    private static RuntimeCombatantState actor() {
        return new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                20,
                20,
                new ActionBudget()
        );
    }
}
