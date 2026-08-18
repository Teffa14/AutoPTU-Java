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

class RuntimeAuthoritativeBurnTest {
    @Test
    void canonicalBurnHalvesPhysicalDamage() {
        MoveResolvedEvent burned = resolve(state(Set.of("Burned"), List.of()), "physical", 137);
        MoveResolvedEvent clean = resolve(state(Set.of(), List.of()), "physical", 137);

        assertEquals(clean.damage() / 2, burned.damage());
    }

    @Test
    void canonicalBurnDoesNotReduceSpecialDamage() {
        MoveResolvedEvent burned = resolve(state(Set.of("Burned"), List.of()), "special", 211);
        MoveResolvedEvent clean = resolve(state(Set.of(), List.of()), "special", 211);

        assertEquals(clean.stableKey(), burned.stableKey());
    }

    @Test
    void staleProjectedBurnModifierCannotForgeBurnWithoutStatus() {
        List<AttackModifier> staleProjection = List.of(AttackModifier.scalar("burned", 0.1));
        MoveResolvedEvent stale = resolve(state(Set.of(), staleProjection), "physical", 307);
        MoveResolvedEvent clean = resolve(state(Set.of(), List.of()), "physical", 307);

        assertEquals(clean.stableKey(), stale.stableKey());
    }

    @Test
    void statusSnapshotIsDefensivelyCopied() {
        ArrayList<String> statuses = new ArrayList<>();
        statuses.add("Burned");
        BattleRuntimeState state = state(statuses, List.of());

        statuses.clear();

        assertEquals(Set.of("burned"), state.statuses("actor"));
    }

    private static MoveResolvedEvent resolve(BattleRuntimeState state, String category, int seed) {
        return (MoveResolvedEvent) RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state,
                choice(),
                move(category),
                "Medium",
                "Medium",
                Set.of(),
                "AI",
                new PythonRandom(seed),
                input(),
                false,
                false
        ).events().getFirst();
    }

    private static BattleRuntimeState state(
            java.util.Collection<String> statuses,
            List<AttackModifier> projectedModifiers
    ) {
        CombatantStatProfile actorStats = stats(20, 12, 20, 12);
        CombatantStatProfile targetStats = stats(12, 16, 12, 16);
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                100,
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
                projectedModifiers
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
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy),
                Map.of("actor", statuses)
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

    private static MoveResolutionInput input() {
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
                List.of(AttackModifier.scalar("forged-client-burn", 0.01))
        );
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor",
                "test-move",
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );
    }

    private static MoveOption move(String category) {
        return MoveOption.standard(
                "test-move",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 6, 20, category, "Normal")
        );
    }
}
