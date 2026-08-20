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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RuntimeAuthoritativePostDamageAuraTest {
    @Test
    void adjacentAquaBoostAddsFiveAfterOrdinaryDamageAndBeforeHpHistory() {
        BattleRuntimeState boostedState = state(true);
        BattleRuntimeState baseState = state(false);

        AppliedActionResult boostedResult = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                boostedState, choice(), waterMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(41), input(), false, false
        );
        AppliedActionResult baseResult = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                baseState, choice(), waterMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(41), input(), false, false
        );

        assertEquals(2, boostedResult.events().size());
        RuleEffectEvent auraEvent = assertInstanceOf(RuleEffectEvent.class, boostedResult.events().get(0));
        MoveResolvedEvent boosted = assertInstanceOf(MoveResolvedEvent.class, boostedResult.events().get(1));
        MoveResolvedEvent base = assertInstanceOf(MoveResolvedEvent.class, baseResult.events().getLast());

        assertEquals("Aqua Boost", auraEvent.ruleName());
        assertEquals("ally", auraEvent.actorId());
        assertEquals(5, auraEvent.amount());
        assertEquals(base.damage() + 5, boosted.damage());
        assertEquals(base.targetHp() - 5, boosted.targetHp());
        assertEquals(boosted.targetHp(), boostedState.requireCombatant("target").hp());
        assertEquals(
                boosted.damage(),
                boostedState.damageHistory().damageReceivedThisRound().get("target")
        );
    }

    private static BattleRuntimeState state(boolean withAura) {
        CombatantStatProfile actorStats = stats(18, 10);
        CombatantStatProfile targetStats = stats(10, 10);
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 4),
                100,
                100,
                new ActionBudget(),
                actorStats,
                new EvasionProfile(actorStats, 0, 0, 0, false, false),
                0, false, false, false, false,
                List.of("Normal"), List.of(), List.of()
        );
        RuntimeCombatantState target = new RuntimeCombatantState(
                "target",
                MovementProfile.walking(new GridCoord(1, 0), 4),
                200,
                200,
                new ActionBudget(),
                targetStats,
                new EvasionProfile(targetStats, 0, 0, 0, false, false),
                0, false, false, false, false,
                List.of("Normal"), List.of(), List.of()
        );
        RuntimeCombatantState ally = new RuntimeCombatantState(
                "ally",
                MovementProfile.walking(new GridCoord(2, 1), 4),
                100,
                100,
                new ActionBudget(),
                null, null, 0, false, false, false, false,
                List.of("Normal"), List.of(), List.of("Aqua Boost")
        );

        List<RuntimeCombatantState> combatants = withAura ? List.of(actor, target, ally) : List.of(actor, target);
        Map<String, CombatantAffiliationState> affiliations = withAura
                ? Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true),
                        "ally", new CombatantAffiliationState("A", true))
                : Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true));

        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(), affiliations, Map.of(), Map.of()
        );
    }

    private static CombatantStatProfile stats(int attack, int defense) {
        return new CombatantStatProfile(
                Map.of(CombatStat.ATK, attack, CombatStat.DEF, defense),
                Map.of(), Map.of(), Set.of()
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor", "water-strike", ChoiceTargetMode.COMBATANT, "target",
                new GridCoord(1, 0), ActionType.STANDARD
        );
    }

    private static MoveOption waterMove() {
        return MoveOption.standard(
                "water-strike",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(null, 6, 20, "physical", "Water")
        );
    }

    private static MoveResolutionInput input() {
        return new MoveResolutionInput(
                99, -99, -6, 20, false, false, false,
                1, 999, 999, false, 7.0, List.of()
        );
    }
}
