package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.InitiativeAdditionalBonusResolution;
import io.autoptu.core.rules.InitiativePokemonCandidate;
import io.autoptu.core.rules.InitiativeSpeedAbilityResolution;
import io.autoptu.core.rules.PokemonInitiativeEntryResolution;
import io.autoptu.core.rules.StatResolution;
import io.autoptu.core.rules.StatusStatResolution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInitiativePokemonCandidateFactoryTest {
    @Test
    void projectsCanonicalRuntimeStateThroughParityTestedInitiativeResolvers() {
        RuntimeCombatantState actor = combatant(
                "actor",
                12,
                List.of("Slush Rush", "Early Bird [Errata]")
        );
        actor.temporaryEffects().add("initiative_bonus", Map.of("amount", 3, "expires_round", 2));

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor),
                Map.of("actor", List.of("Paralyzed"))
        );
        state.syncCurrentRoundFromLifecycle(2);
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState(
                "Hail",
                "",
                Set.of("actor"),
                Map.of("actor", true)
        ));
        state.putTrainer(new TrainerRuntimeState("trainer", List.of(), 3, 2));
        state.bindController("actor", "trainer");

        RuntimeInitiativePokemonContext context = new RuntimeInitiativePokemonContext(
                999,
                false,
                "Clear",
                "Electric Terrain",
                false,
                true,
                false,
                0,
                true,
                true
        );

        InitiativePokemonCandidate actual = RuntimeInitiativePokemonCandidateFactory.fromState(
                state,
                "actor",
                context
        );

        int statSpeed = StatResolution.speed(StatusStatResolution.apply(
                actor.effectiveStatProfile(),
                state.statuses("actor")
        ));
        int abilitySpeed = InitiativeSpeedAbilityResolution.resolve(
                statSpeed,
                actor.hp(),
                actor.maxHp(),
                state.environment().weather(),
                state.environment().terrainName(),
                state.environment().grounded("actor"),
                actor.abilities()
        );
        int additional = InitiativeAdditionalBonusResolution.resolve(
                abilitySpeed,
                actor.abilities(),
                false,
                false,
                0
        );
        var expected = PokemonInitiativeEntryResolution.resolve(
                "actor",
                "trainer",
                abilitySpeed,
                2,
                false,
                true,
                2,
                actor.temporaryEffects().entriesInInsertionOrder(),
                additional,
                false
        );

        assertEquals(expected, actual.baseEntry());
        assertTrue(actual.active());
        assertFalse(actual.fainted());
        assertFalse(actual.parentalBondChild());
        assertEquals(actor.abilities(), actual.abilities());
        assertEquals(actor.temporaryEffects().entriesInInsertionOrder(), actual.temporaryEffects());
    }

    @Test
    void ignoresForgedExternalEnvironmentAndUsesCanonicalBattleEnvironment() {
        RuntimeCombatantState actor = combatant(
                "actor",
                10,
                List.of("Slush Rush", "Surge Surfer")
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("actor", CombatantAffiliationState.active("team-a"))
        );
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState(
                "Clear",
                "Electric Terrain",
                Set.of("team-a"),
                Map.of("actor", false)
        ));

        RuntimeInitiativePokemonContext forgedContext = new RuntimeInitiativePokemonContext(
                0,
                false,
                "Hail",
                "Electric Terrain",
                true,
                false,
                false,
                0,
                false,
                false
        );

        InitiativePokemonCandidate candidate = RuntimeInitiativePokemonCandidateFactory.fromState(
                state,
                "actor",
                forgedContext
        );

        assertEquals(10, candidate.baseEntry().speed());
        assertEquals(15, candidate.baseEntry().total());
    }

    @Test
    void derivesInitiativeTemporaryEffectsAndTrainerModifierFromCanonicalState() {
        RuntimeCombatantState actor = combatant("actor", 10, List.of());
        actor.temporaryEffects().add("agility_training");
        actor.temporaryEffects().add("parental_bond_child");
        actor.temporaryEffects().add("initiative_zero_until_turn", Map.of("expires_round", 3));

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
        );
        state.syncCurrentRoundFromLifecycle(2);
        state.putTrainer(new TrainerRuntimeState("trainer", List.of(), 1, -3));
        state.bindController("actor", "trainer");

        RuntimeInitiativePokemonContext forgedContext = new RuntimeInitiativePokemonContext(
                999,
                false,
                false,
                0,
                false,
                false
        );

        InitiativePokemonCandidate candidate = RuntimeInitiativePokemonCandidateFactory.fromState(
                state,
                "actor",
                forgedContext
        );

        assertEquals(-3, candidate.baseEntry().trainerModifier());
        assertEquals(0, candidate.baseEntry().total());
        assertTrue(candidate.parentalBondChild());

        int additional = InitiativeAdditionalBonusResolution.resolve(10, List.of(), true, false, 0);
        assertEquals(4, additional);
    }

    @Test
    void forgedLegacyInitiativeFlagsCannotCreateCanonicalEffects() {
        RuntimeCombatantState actor = combatant("actor", 10, List.of());
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
        );
        state.putTrainer(new TrainerRuntimeState("trainer", List.of(), 1, 3));
        state.bindController("actor", "trainer");

        RuntimeInitiativePokemonContext forged = new RuntimeInitiativePokemonContext(
                999,
                true,
                true,
                0,
                true,
                true
        );
        InitiativePokemonCandidate candidate = RuntimeInitiativePokemonCandidateFactory.fromState(
                state,
                "actor",
                forged
        );

        assertEquals(13, candidate.baseEntry().total());
        assertEquals(3, candidate.baseEntry().trainerModifier());
        assertFalse(candidate.parentalBondChild());
    }

    @Test
    void environmentGroundedStateRejectsUnknownCombatantsBeforeProjection() {
        RuntimeCombatantState actor = combatant("actor", 10, List.of());
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> state.syncEnvironmentFromRuntime(new BattleEnvironmentState(
                        "",
                        "",
                        Set.of(),
                        Map.of("forged", false)
                ))
        );
        assertEquals(BattleEnvironmentState.neutral().weather(), state.environment().weather());
        assertTrue(state.environment().grounded("actor"));
    }

    @Test
    void derivesBashedFaintedAndParentalBondFilteringInputsFromCanonicalState() {
        RuntimeCombatantState actor = combatant("actor", 10, List.of());
        actor.setHp(0);
        actor.temporaryEffects().add("parental_bond_child");
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor),
                Map.of("actor", List.of("Bashed")),
                Map.of(),
                Map.of(),
                Map.of("actor", CombatantAffiliationState.active("team-a"))
        );

        RuntimeInitiativePokemonContext context = new RuntimeInitiativePokemonContext(
                99, true, "Hail", "Electric Terrain", true,
                true, true, 99, false, true
        );
        InitiativePokemonCandidate candidate = RuntimeInitiativePokemonCandidateFactory.fromState(
                state,
                "actor",
                context
        );

        assertEquals(0, candidate.baseEntry().speed());
        assertEquals(0, candidate.baseEntry().total());
        assertTrue(candidate.fainted());
        assertTrue(candidate.parentalBondChild());
    }

    @Test
    void missingAuthoritativeStatProfileFailsBeforeProducingCandidate() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 4),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
        );

        assertThrows(
                IllegalStateException.class,
                () -> RuntimeInitiativePokemonCandidateFactory.fromState(
                        state,
                        "actor",
                        RuntimeInitiativePokemonContext.neutral()
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, int speed, List<String> abilities) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.SPD, speed),
                Map.of(),
                Map.of(),
                Set.of()
        );
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 4),
                20,
                20,
                new ActionBudget(),
                stats,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                abilities
        );
    }
}
