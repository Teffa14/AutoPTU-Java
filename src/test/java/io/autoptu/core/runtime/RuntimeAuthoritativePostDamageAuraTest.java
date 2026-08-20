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
        BattleRuntimeState boostedState = aquaState(true);
        BattleRuntimeState baseState = aquaState(false);

        AppliedActionResult boostedResult = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                boostedState, choice("water-strike"), waterMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(41), input(), false, false
        );
        AppliedActionResult baseResult = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                baseState, choice("water-strike"), waterMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(41), input(), false, false
        );

        assertEquals(2, boostedResult.events().size());
        RuleEffectEvent auraEvent = assertInstanceOf(RuleEffectEvent.class, boostedResult.events().get(0));
        MoveResolvedEvent boosted = assertInstanceOf(MoveResolvedEvent.class, boostedResult.events().get(1));
        MoveResolvedEvent base = assertInstanceOf(MoveResolvedEvent.class, baseResult.events().getLast());

        assertEquals("Aqua Boost", auraEvent.sourceName());
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

    @Test
    void auraStormUsesCanonicalInjuriesAndGlobalEnemyAuraBreakSuppressesIt() {
        BattleRuntimeState baseState = auraStormState(false, false);
        BattleRuntimeState stormState = auraStormState(true, false);
        BattleRuntimeState blockedState = auraStormState(true, true);
        baseState.injuryHistory().setCurrentInjuries("actor", 2);
        stormState.injuryHistory().setCurrentInjuries("actor", 2);
        blockedState.injuryHistory().setCurrentInjuries("actor", 2);

        AppliedActionResult baseResult = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                baseState, choice("aura-strike"), auraMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(77), input(), false, false
        );
        AppliedActionResult stormResult = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                stormState, choice("aura-strike"), auraMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(77), input(), false, false
        );
        AppliedActionResult blockedResult = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                blockedState, choice("aura-strike"), auraMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(77), input(), false, false
        );

        MoveResolvedEvent base = assertInstanceOf(MoveResolvedEvent.class, baseResult.events().getLast());
        RuleEffectEvent auraEvent = assertInstanceOf(RuleEffectEvent.class, stormResult.events().get(0));
        MoveResolvedEvent storm = assertInstanceOf(MoveResolvedEvent.class, stormResult.events().getLast());
        MoveResolvedEvent blocked = assertInstanceOf(MoveResolvedEvent.class, blockedResult.events().getLast());

        assertEquals("Aura Storm", auraEvent.sourceName());
        assertEquals("actor", auraEvent.actorId());
        assertEquals(9, auraEvent.amount());
        assertEquals(base.damage() + 9, storm.damage());
        assertEquals(base.damage(), blocked.damage());
        assertEquals(1, blockedResult.events().size());
        assertEquals(storm.damage(), stormState.damageHistory().damageReceivedThisRound().get("target"));
        assertEquals(blocked.damage(), blockedState.damageHistory().damageReceivedThisRound().get("target"));
    }

    private static BattleRuntimeState aquaState(boolean withAura) {
        CombatantStatProfile actorStats = stats(18, 10);
        CombatantStatProfile targetStats = stats(10, 10);
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 100, 100, actorStats, List.of());
        RuntimeCombatantState target = combatant("target", new GridCoord(1, 0), 200, 200, targetStats, List.of());
        RuntimeCombatantState ally = combatant(
                "ally", new GridCoord(2, 1), 100, 100, null, List.of("Aqua Boost"));

        List<RuntimeCombatantState> combatants = withAura ? List.of(actor, target, ally) : List.of(actor, target);
        Map<String, CombatantAffiliationState> affiliations = withAura
                ? Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true),
                        "ally", new CombatantAffiliationState("A", true))
                : Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true));

        return battleState(combatants, affiliations);
    }

    private static BattleRuntimeState auraStormState(boolean withAuraStorm, boolean withAuraBreak) {
        CombatantStatProfile actorStats = stats(18, 10);
        CombatantStatProfile targetStats = stats(10, 10);
        RuntimeCombatantState actor = combatant(
                "actor", new GridCoord(1, 1), 100, 100, actorStats,
                withAuraStorm ? List.of("Aura Storm") : List.of());
        RuntimeCombatantState target = combatant("target", new GridCoord(1, 0), 200, 200, targetStats, List.of());
        RuntimeCombatantState breaker = combatant(
                "breaker", new GridCoord(5, 5), 100, 100, null, List.of("Aura Break"));

        List<RuntimeCombatantState> combatants = withAuraBreak
                ? List.of(actor, target, breaker)
                : List.of(actor, target);
        Map<String, CombatantAffiliationState> affiliations = withAuraBreak
                ? Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true),
                        "breaker", new CombatantAffiliationState("B", true))
                : Map.of(
                        "actor", new CombatantAffiliationState("A", true),
                        "target", new CombatantAffiliationState("B", true));
        return battleState(combatants, affiliations);
    }

    private static BattleRuntimeState battleState(
            List<RuntimeCombatantState> combatants,
            Map<String, CombatantAffiliationState> affiliations
    ) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(), affiliations, Map.of(), Map.of()
        );
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            int hp,
            int maxHp,
            CombatantStatProfile stats,
            List<String> abilities
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                hp,
                maxHp,
                new ActionBudget(),
                stats,
                stats == null ? null : new EvasionProfile(stats, 0, 0, 0, false, false),
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

    private static MoveChoice choice(String moveId) {
        return new MoveChoice(
                "actor", moveId, ChoiceTargetMode.COMBATANT, "target",
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

    private static MoveOption auraMove() {
        return MoveOption.standard(
                "aura-strike",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee", List.of("Aura")),
                new MoveCombatProfile(null, 6, 20, "physical", "Psychic")
        );
    }

    private static MoveResolutionInput input() {
        return new MoveResolutionInput(
                99, -99, -6, 20, false, false, false,
                1, 999, 999, false, 7.0, List.of()
        );
    }
}