package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeSwayFollowUpMoveIntegrationTest {
    @Test
    void swayRedirectsOriginalMoveThroughLiveRuntimeWithoutSecondResourceSpend() {
        MoveOption slash = move();
        RuntimeCombatantState attacker = combatant(
                "attacker", new GridCoord(2, 1), List.of(), true);
        RuntimeCombatantState defender = combatant(
                "defender", new GridCoord(2, 2), List.of("Sway"), false);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(attacker, defender),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("attacker", List.of(slash)),
                Map.of()
        );

        AppliedActionResult result = RuntimeMoveResolutionWithFollowUps.applyUsingAuthoritativeCombatState(
                state,
                new MoveChoice(
                        "attacker",
                        "slash",
                        ChoiceTargetMode.COMBATANT,
                        "defender",
                        defender.position(),
                        ActionType.STANDARD
                ),
                slash,
                "Medium",
                "Medium",
                Set.of(),
                "test",
                new PythonRandom(7),
                legacyInput(),
                false,
                false
        );

        assertTrue(attacker.hp() < 100, "redirected Slash must damage the original attacker");
        assertEquals(100, defender.hp(), "the original Sway target must take no damage");
        assertFalse(attacker.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertFalse(defender.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertTrue(attacker.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertTrue(defender.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, attacker.moveFrequencyUsage().battleUses("slash"));
        assertEquals(new GridCoord(1, 1), attacker.position());
        assertFalse(attacker.temporaryEffects().has("sway_redirect"));
        assertEquals(1, defender.temporaryEffects().count("sway_used"));

        assertEquals(4, result.events().size());
        assertEquals("redirect", ((RuleEffectEvent) result.events().get(0)).effect());
        MoveResolvedEvent redirected = (MoveResolvedEvent) result.events().get(1);
        assertEquals("attacker", redirected.actorId());
        assertEquals("attacker", redirected.targetId());
        assertTrue(redirected.hit());
        assertEquals("push", ((RuleEffectEvent) result.events().get(2)).effect());
        MoveResolvedEvent original = (MoveResolvedEvent) result.events().get(3);
        assertEquals("attacker", original.actorId());
        assertEquals("defender", original.targetId());
        assertFalse(original.hit());

        assertTrue(state.damageHistory().damageThisRound().get("attacker") > 0);
        assertFalse(state.damageHistory().damageThisRound().containsKey("defender"));
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            List<String> abilities,
            boolean noGuard
    ) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, 20,
                        CombatStat.DEF, 10,
                        CombatStat.SPATK, 20,
                        CombatStat.SPDEF, 10,
                        CombatStat.SPEED, 10
                ),
                Map.of(),
                Map.of(),
                Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                100,
                100,
                new ActionBudget(),
                stats,
                new EvasionProfile(stats, 0, 0, 0, false, false),
                0,
                false,
                noGuard,
                false,
                false,
                List.of(),
                List.of(),
                abilities
        );
    }

    private static MoveOption move() {
        return new MoveOption(
                "slash",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                ActionType.STANDARD,
                true,
                new MoveCombatProfile(2, 6, 20, "physical"),
                "Scene x1"
        );
    }

    private static MoveResolutionInput legacyInput() {
        return new MoveResolutionInput(
                99,
                99,
                6,
                1,
                false,
                true,
                true,
                1,
                999,
                999,
                true,
                1.0,
                List.of()
        );
    }
}
