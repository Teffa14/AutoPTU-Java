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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMoveSpecialSecondaryStatusIntegrationTest {
    @Test
    void authoritativeAccuracyRollAndSereneGraceApplyBurnThroughStatusPipeline() {
        BattleRuntimeState state = state(List.of("Serene Grace"), List.of());
        MoveOption move = move("flame-test", "Fire", "Burns the target on 15+");

        var result = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state,
                choice("flame-test"),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "AI",
                new PythonRandom(41),
                legacyInput(),
                false,
                false
        );

        assertTrue(state.hasStatus("target", "burned"));
        assertFalse(state.requireCombatant("source").actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertInstanceOf(MoveResolvedEvent.class, result.events().getLast());
    }

    @Test
    void canonicalImmunityBlocksParsedPoisonAfterRealAccuracyRoll() {
        BattleRuntimeState state = state(List.of(), List.of("Immunity"));
        MoveOption move = move("poison-test", "Poison", "Poisons the target on 13+");

        var result = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state,
                choice("poison-test"),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "AI",
                new PythonRandom(41),
                legacyInput(),
                false,
                false
        );

        assertFalse(state.hasStatus("target", "poisoned"));
        RuleEffectEvent block = result.events().stream()
                .filter(RuleEffectEvent.class::isInstance)
                .map(RuleEffectEvent.class::cast)
                .filter(event -> event.sourceName().equals("Immunity"))
                .findFirst()
                .orElseThrow();
        assertEquals("status_block", block.effect());
        assertInstanceOf(MoveResolvedEvent.class, result.events().getLast());
    }

    private static BattleRuntimeState state(List<String> sourceAbilities, List<String> targetAbilities) {
        CombatantStatProfile sourceStats = stats(18, 12, 20, 12);
        CombatantStatProfile targetStats = stats(12, 14, 12, 14);
        RuntimeCombatantState source = combatant("source", new GridCoord(1, 1), sourceStats, sourceAbilities, false);
        RuntimeCombatantState target = combatant("target", new GridCoord(1, 2), targetStats, targetAbilities, true);
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(source, target),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "source", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true)
                )
        );
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            CombatantStatProfile stats,
            List<String> abilities,
            boolean withEvasion
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                100,
                100,
                new ActionBudget(),
                stats,
                withEvasion ? new EvasionProfile(stats, 0, 0, 0, false, false) : null,
                0,
                false,
                false,
                false,
                false,
                List.of("Normal"),
                List.of(),
                abilities
        );
    }

    private static CombatantStatProfile stats(int attack, int defense, int specialAttack, int specialDefense) {
        return new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, attack,
                        CombatStat.DEF, defense,
                        CombatStat.SPATK, specialAttack,
                        CombatStat.SPDEF, specialDefense
                ),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveOption move(String id, String type, String effectsText) {
        return MoveOption.standard(
                id,
                new MoveSpec(
                        "Ranged", "Ranged", 6, 6, null, null, "6",
                        List.of(), effectsText
                ),
                new MoveCombatProfile(2, 6, 20, "special", type)
        );
    }

    private static MoveChoice choice(String moveId) {
        return new MoveChoice(
                "source",
                moveId,
                ChoiceTargetMode.COMBATANT,
                "target",
                new GridCoord(1, 2),
                ActionType.STANDARD
        );
    }

    private static MoveResolutionInput legacyInput() {
        return new MoveResolutionInput(
                99, -99, -6, 1, false, false, false,
                1, 999, 999, false, 1.0, List.of()
        );
    }
}
