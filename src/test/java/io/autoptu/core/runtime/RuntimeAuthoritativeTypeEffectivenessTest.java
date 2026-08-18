package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeTypeEffectivenessTest {
    @Test
    void ignoresAdapterMultiplierAndDerivesDualTypeEffectiveness() {
        BattleRuntimeState derivedState = state(List.of("Grass", "Steel"));
        BattleRuntimeState explicitState = state(List.of());

        MoveResolutionInput forged = input(0.25);
        MoveResolutionInput expected = input(2.0);

        MoveResolvedEvent derived = (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                derivedState, choice(), fireMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(19), forged, false, false
        ).events().getFirst();

        MoveResolvedEvent explicit = (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeEvasion(
                explicitState, choice(), fireMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(19), expected, false, false
        ).events().getFirst();

        assertEquals(List.of("Grass", "Steel"), derivedState.requireCombatant("enemy").types());
        assertEquals(explicit.stableKey(), derived.stableKey());
        assertEquals(derived.targetHp(), derivedState.requireCombatant("enemy").hp());
    }

    @Test
    void authoritativeTypingCanForceImmunityDespiteForgedDamageMultiplier() {
        BattleRuntimeState derivedState = state(List.of("Ground"));
        BattleRuntimeState explicitState = state(List.of());

        MoveResolutionInput forged = input(2.0);
        MoveResolutionInput expected = input(0.0);

        MoveResolvedEvent derived = (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                derivedState, choice(), electricMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(23), forged, false, false
        ).events().getFirst();

        MoveResolvedEvent explicit = (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeEvasion(
                explicitState, choice(), electricMove(), "Medium", "Medium", Set.of(), "AI",
                new PythonRandom(23), expected, false, false
        ).events().getFirst();

        assertEquals(explicit.stableKey(), derived.stableKey());
        assertEquals(100, derivedState.requireCombatant("enemy").hp());
    }

    @Test
    void legacyCombatantsDefaultToNoAuthoritativeTypes() {
        CombatantStatProfile stats = stats(10, 10);
        RuntimeCombatantState combatant = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                50,
                100,
                new ActionBudget(),
                stats
        );

        assertTrue(combatant.types().isEmpty());
    }

    private static BattleRuntimeState state(List<String> targetTypes) {
        CombatantStatProfile actorStats = stats(20, 8);
        CombatantStatProfile targetStats = stats(12, 20);
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                50,
                100,
                new ActionBudget(),
                actorStats,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of("Fire")
        );
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy",
                MovementProfile.walking(new GridCoord(2, 1), 3),
                100,
                100,
                new ActionBudget(),
                targetStats,
                new EvasionProfile(targetStats, 0, 0, 0, false, false),
                0,
                false,
                false,
                false,
                false,
                targetTypes
        );
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }

    private static CombatantStatProfile stats(int attack, int defense) {
        return new CombatantStatProfile(
                Map.of(CombatStat.ATK, attack, CombatStat.DEF, defense),
                Map.of(),
                Map.of(),
                Set.of()
        );
    }

    private static MoveResolutionInput input(double typeMultiplier) {
        return new MoveResolutionInput(
                99,
                -99,
                -6,
                20,
                false,
                false,
                false,
                6,
                999,
                999,
                false,
                typeMultiplier,
                List.of()
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor", "typed-shot", ChoiceTargetMode.COMBATANT, "enemy",
                new GridCoord(2, 1), ActionType.STANDARD
        );
    }

    private static MoveOption fireMove() {
        return MoveOption.standard(
                "typed-shot",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 6, 20, "physical", "Fire")
        );
    }

    private static MoveOption electricMove() {
        return MoveOption.standard(
                "typed-shot",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 6, 20, "physical", "Electric")
        );
    }
}
