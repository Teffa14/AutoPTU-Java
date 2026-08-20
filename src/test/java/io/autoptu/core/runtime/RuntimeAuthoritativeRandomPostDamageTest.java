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

class RuntimeAuthoritativeRandomPostDamageTest {
    @Test
    void adaptabilityConsumesItsD10AfterOrdinaryDamageAndBeforeHpHistory() {
        long seed = 137L;
        BattleRuntimeState baseState = state(List.of());
        BattleRuntimeState adaptabilityState = state(List.of("Adaptability [Errata]"));
        PythonRandom baseRng = new PythonRandom(seed);
        PythonRandom abilityRng = new PythonRandom(seed);

        AppliedActionResult baseResult = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                baseState, choice(), fireMove(), "Medium", "Medium", Set.of(), "AI",
                baseRng, input(), false, false
        );
        int expectedBonus = baseRng.randIntInclusive(1, 10);
        int expectedNextRoll = baseRng.randIntInclusive(1, 10);

        AppliedActionResult abilityResult = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                adaptabilityState, choice(), fireMove(), "Medium", "Medium", Set.of(), "AI",
                abilityRng, input(), false, false
        );

        MoveResolvedEvent base = assertInstanceOf(MoveResolvedEvent.class, baseResult.events().getLast());
        RuleEffectEvent ability = assertInstanceOf(RuleEffectEvent.class, abilityResult.events().get(0));
        MoveResolvedEvent resolved = assertInstanceOf(MoveResolvedEvent.class, abilityResult.events().getLast());

        assertEquals("Adaptability [Errata]", ability.sourceName());
        assertEquals(expectedBonus, ability.amount());
        assertEquals(base.damage() + expectedBonus, resolved.damage());
        assertEquals(resolved.damage(), adaptabilityState.damageHistory().damageReceivedThisRound().get("target"));
        assertEquals(200 - resolved.damage(), adaptabilityState.requireCombatant("target").hp());
        assertEquals(expectedNextRoll, abilityRng.randIntInclusive(1, 10));
    }

    private static BattleRuntimeState state(List<String> abilities) {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 100, stats(18, 10), abilities, List.of("Fire"));
        RuntimeCombatantState target = combatant("target", new GridCoord(1, 0), 200, stats(10, 10), List.of(), List.of("Normal"));
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
            String id, GridCoord position, int hp, CombatantStatProfile stats,
            List<String> abilities, List<String> types
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
                types, List.of(), abilities
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
                "actor", "fire-strike", ChoiceTargetMode.COMBATANT, "target",
                new GridCoord(1, 0), ActionType.STANDARD
        );
    }

    private static MoveOption fireMove() {
        return MoveOption.standard(
                "fire-strike",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(null, 6, 20, "physical", "Fire")
        );
    }

    private static MoveResolutionInput input() {
        return new MoveResolutionInput(
                99, -99, -6, 20, false, false, false,
                1, 999, 999, false, 7.0, List.of()
        );
    }
}
