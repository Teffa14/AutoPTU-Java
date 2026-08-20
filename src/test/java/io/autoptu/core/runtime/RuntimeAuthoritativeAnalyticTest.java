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

class RuntimeAuthoritativeAnalyticTest {
    @Test
    void targetActionHistoryEnablesAnalyticWithoutAdapterInput() {
        long seed = 211L;
        BattleRuntimeState baseState = state(false, false, false);
        BattleRuntimeState analyticState = state(true, true, false);

        MoveResolvedEvent base = resolve(baseState, seed);
        AppliedActionResult analyticResult = resolveResult(analyticState, seed);
        RuleEffectEvent ability = assertInstanceOf(RuleEffectEvent.class, analyticResult.events().get(0));
        MoveResolvedEvent resolved = assertInstanceOf(MoveResolvedEvent.class, analyticResult.events().getLast());

        assertEquals("Analytic", ability.sourceName());
        assertEquals(5, ability.amount());
        assertEquals(base.damage() + 5, resolved.damage());
        assertEquals(resolved.damage(), analyticState.damageHistory().damageReceivedThisRound().get("target"));
    }

    @Test
    void initiativeCursorMustPassTargetBeforeAnalyticApplies() {
        long seed = 223L;
        BattleRuntimeState baseState = state(false, false, false);
        BattleRuntimeState equalCursorState = state(true, false, false);
        BattleRuntimeState passedCursorState = state(true, false, false);

        setInitiative(equalCursorState, 0);
        setInitiative(passedCursorState, 1);

        MoveResolvedEvent base = resolve(baseState, seed);
        MoveResolvedEvent equalCursor = resolve(equalCursorState, seed);
        AppliedActionResult passedResult = resolveResult(passedCursorState, seed);
        RuleEffectEvent analytic = assertInstanceOf(RuleEffectEvent.class, passedResult.events().get(0));
        MoveResolvedEvent passed = assertInstanceOf(MoveResolvedEvent.class, passedResult.events().getLast());

        assertEquals(base.damage(), equalCursor.damage());
        assertEquals(base.damage() + 5, passed.damage());
        assertEquals("Analytic", analytic.sourceName());
    }

    @Test
    void auraBreakErrataInvertsLiveAnalyticBonusBeforeHpAndHistory() {
        long seed = 227L;
        BattleRuntimeState baseState = state(false, false, false);
        BattleRuntimeState invertedState = state(true, true, true);

        MoveResolvedEvent base = resolve(baseState, seed);
        AppliedActionResult invertedResult = resolveResult(invertedState, seed);
        RuleEffectEvent auraBreak = assertInstanceOf(RuleEffectEvent.class, invertedResult.events().get(0));
        RuleEffectEvent analytic = assertInstanceOf(RuleEffectEvent.class, invertedResult.events().get(1));
        MoveResolvedEvent resolved = assertInstanceOf(MoveResolvedEvent.class, invertedResult.events().getLast());

        assertEquals("Aura Break [Errata]", auraBreak.sourceName());
        assertEquals(-5, auraBreak.amount());
        assertEquals("Analytic", analytic.sourceName());
        assertEquals(-5, analytic.amount());
        assertEquals(base.damage() - 5, resolved.damage());
        assertEquals(resolved.damage(), invertedState.damageHistory().damageReceivedThisRound().get("target"));
        assertEquals(200 - resolved.damage(), invertedState.requireCombatant("target").hp());
    }

    private static void setInitiative(BattleRuntimeState state, int cursor) {
        BattleRoundController controller = new BattleRoundController(state);
        controller.replaceInitiativeOrder(List.of("target", "actor"));
        controller.setInitiativeCursor(cursor);
    }

    private static MoveResolvedEvent resolve(BattleRuntimeState state, long seed) {
        return assertInstanceOf(MoveResolvedEvent.class, resolveResult(state, seed).events().getLast());
    }

    private static AppliedActionResult resolveResult(BattleRuntimeState state, long seed) {
        return RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state, choice(), normalMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(seed), input(), false, false
        );
    }

    private static BattleRuntimeState state(boolean analytic, boolean targetActed, boolean auraBreak) {
        RuntimeCombatantState actor = combatant(
                "actor", new GridCoord(1, 1), 100, stats(18, 10),
                analytic ? List.of("Analytic") : List.of());
        RuntimeCombatantState target = combatant(
                "target", new GridCoord(1, 0), 200, stats(10, 10), List.of());
        if (targetActed) {
            target.actionBudget().markAction(ActionType.STANDARD, "previous action");
        }
        if (auraBreak) {
            actor.temporaryEffects().add("aura_break_errata", Map.of(
                    "ability", "Analytic",
                    "source_id", "breaker",
                    "expires_round", 0
            ));
        }
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true)
                ),
                Map.of(), Map.of()
        );
    }

    private static RuntimeCombatantState combatant(
            String id, GridCoord position, int hp, CombatantStatProfile stats, List<String> abilities
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                hp,
                hp,
                new ActionBudget(),
                stats,
                new EvasionProfile(stats, 0, 0, 0, false, false),
                0, false, false, false, false,
                List.of("Normal"), List.of(), abilities
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
                "actor", "normal-strike", ChoiceTargetMode.COMBATANT, "target",
                new GridCoord(1, 0), ActionType.STANDARD
        );
    }

    private static MoveOption normalMove() {
        return MoveOption.standard(
                "normal-strike",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(null, 6, 20, "physical", "Normal")
        );
    }

    private static MoveResolutionInput input() {
        return new MoveResolutionInput(
                99, -99, -6, 20, false, false, false,
                1, 999, 999, false, 7.0, List.of()
        );
    }
}
