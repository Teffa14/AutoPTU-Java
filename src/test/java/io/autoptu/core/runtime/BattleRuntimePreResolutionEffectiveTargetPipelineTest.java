package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.hook.PreDamageReactionHookRegistry;
import io.autoptu.core.hook.PreResolutionTargetHookRegistry;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimePreResolutionEffectiveTargetPipelineTest {
    @Test
    void replacementBecomesEffectiveDefenderBeforeAccuracyAndOwnsDownstreamOutcomeOnce() {
        BattleRuntimeState state = state();
        MoveOption move = fireMove();
        MoveChoice declared = new MoveChoice(
                "attacker", move.moveId(), ChoiceTargetMode.COMBATANT, "protected",
                new GridCoord(5, 1), ActionType.STANDARD
        );

        PreResolutionTargetHookRegistry targets = PreResolutionTargetHookRegistry.builder()
                .register("intercept", HookSource.REACTION, 10, (context, current) ->
                        current.replaceTarget("interceptor", List.of(new RuleEffectEvent(
                                "reaction", "Intercept", "attacker", "interceptor", move.moveId(),
                                "target_redirect", 0.0, 10
                        ))))
                .build();

        AtomicReference<String> reactionDefender = new AtomicReference<>();
        AtomicReference<Double> reactionTypeMultiplier = new AtomicReference<>();
        PreDamageReactionHookRegistry preDamage = PreDamageReactionHookRegistry.builder()
                .register("observe-effective-target", HookSource.SYSTEM, 10, (context, current) -> {
                    reactionDefender.set(context.defenderId());
                    reactionTypeMultiplier.set(current.typeMultiplier());
                    return current;
                })
                .build();

        AppliedActionResult result = BattleRuntime.applyAuthoritativeMoveWithPreResolutionTargets(
                state,
                declared,
                move,
                "Medium",
                "Medium",
                Set.of(),
                "test",
                new PythonRandom(41),
                legacyInput(),
                targets,
                MoveSpecialHookRegistry.builder().build(),
                preDamage,
                PostDamageHookRegistry.builder().build(),
                false,
                false
        );

        assertEquals("interceptor", reactionDefender.get());
        assertEquals(2.0, reactionTypeMultiplier.get());
        assertEquals(40, state.requireCombatant("protected").hp());
        assertTrue(state.requireCombatant("interceptor").hp() < 40);
        assertFalse(state.damageHistory().damageReceivedThisRound().containsKey("protected"));
        assertTrue(state.damageHistory().damageReceivedThisRound().containsKey("interceptor"));

        assertInstanceOf(RuleEffectEvent.class, result.events().getFirst());
        RuleEffectEvent intercept = (RuleEffectEvent) result.events().getFirst();
        assertEquals("Intercept", intercept.sourceName());
        MoveResolvedEvent resolved = assertInstanceOf(MoveResolvedEvent.class, result.events().getLast());
        assertEquals("interceptor", resolved.targetId());
        assertTrue(resolved.hit());

        RuntimeCombatantState attacker = state.requireCombatant("attacker");
        assertFalse(attacker.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, attacker.moveFrequencyUsage().battleUses(move.moveId()));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState attacker = combatant(
                "attacker", 1, 1, stats(20, 10), List.of("Fire")
        );
        RuntimeCombatantState protectedTarget = combatant(
                "protected", 5, 1, stats(10, 5), List.of("Water")
        );
        RuntimeCombatantState interceptor = combatant(
                "interceptor", 8, 8, stats(10, 30), List.of("Grass")
        );
        return new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(attacker, protectedTarget, interceptor),
                41L
        );
    }

    private static RuntimeCombatantState combatant(
            String id,
            int x,
            int y,
            CombatantStatProfile stats,
            List<String> types
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                40,
                40,
                new ActionBudget(),
                stats,
                new EvasionProfile(stats, 0, 0, 0, false, false),
                0,
                false,
                false,
                false,
                false,
                types
        );
    }

    private static CombatantStatProfile stats(int attack, int defense) {
        return new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, attack,
                        CombatStat.DEF, defense,
                        CombatStat.SPATK, attack,
                        CombatStat.SPDEF, defense,
                        CombatStat.SPD, 10
                ),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveOption fireMove() {
        MoveSpec spec = new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged");
        return new MoveOption(
                "Flame Shot",
                spec,
                ActionType.STANDARD,
                true,
                new MoveCombatProfile(1, 6, 20, "physical", "Fire"),
                "Scene"
        );
    }

    private static MoveResolutionInput legacyInput() {
        return new MoveResolutionInput(
                99, -99, -6, 20, false, false, false,
                1, 1, 999, false, 0.25, List.of()
        );
    }
}
