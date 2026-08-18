package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.AttackModifier;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeDamageModifiersTest {
    @Test
    void adapterCannotForgeDamageModifiersOnPreferredMinecraftBoundary() {
        BattleRuntimeState forgedState = state(List.of());
        BattleRuntimeState cleanState = state(List.of());

        MoveResolvedEvent forged = resolve(
                forgedState,
                List.of(AttackModifier.flat("forged-client-damage", 500)),
                41
        );
        MoveResolvedEvent clean = resolve(cleanState, List.of(), 41);

        assertEquals(clean.stableKey(), forged.stableKey());
        assertEquals(cleanState.requireCombatant("enemy").hp(), forgedState.requireCombatant("enemy").hp());
    }

    @Test
    void serverOwnedDamageModifierAppliesBeforeTypeMultiplier() {
        BattleRuntimeState boostedState = state(List.of(AttackModifier.flat("server-flat", 5)));
        BattleRuntimeState cleanState = state(List.of());

        MoveResolvedEvent boosted = resolve(boostedState, List.of(), 73);
        MoveResolvedEvent clean = resolve(cleanState, List.of(), 73);

        assertEquals(clean.damage() + 5, boosted.damage());
        assertTrue(boostedState.requireCombatant("enemy").hp() < cleanState.requireCombatant("enemy").hp());
    }

    @Test
    void runtimeCopiesResolvedModifierProjectionDefensively() {
        ArrayList<AttackModifier> modifiers = new ArrayList<>();
        modifiers.add(AttackModifier.flat("trusted", 5));
        BattleRuntimeState state = state(modifiers);

        modifiers.clear();

        assertEquals(1, state.requireCombatant("actor").damageModifiers().size());
        assertEquals("trusted", state.requireCombatant("actor").damageModifiers().getFirst().slug());
    }

    private static MoveResolvedEvent resolve(
            BattleRuntimeState state,
            List<AttackModifier> adapterModifiers,
            int seed
    ) {
        return (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state,
                choice(),
                move(),
                "Medium",
                "Medium",
                Set.of(),
                "AI",
                new PythonRandom(seed),
                input(adapterModifiers),
                false,
                false
        ).events().getFirst();
    }

    private static BattleRuntimeState state(List<AttackModifier> authoritativeModifiers) {
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
                List.of("Normal"),
                authoritativeModifiers
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
                List.of("Normal")
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

    private static MoveResolutionInput input(List<AttackModifier> adapterModifiers) {
        return new MoveResolutionInput(
                99,
                -99,
                -6,
                20,
                false,
                false,
                false,
                99,
                999,
                999,
                false,
                1.0,
                adapterModifiers
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor",
                "tackle",
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );
    }

    private static MoveOption move() {
        return MoveOption.standard(
                "tackle",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 6, 20, "physical", "Normal")
        );
    }
}
