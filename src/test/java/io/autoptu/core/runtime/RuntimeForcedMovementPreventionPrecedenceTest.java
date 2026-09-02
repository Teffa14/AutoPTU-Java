package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ForcedMovementPreventionResolution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Freezes the first-blocker precedence observed in pinned Python apply_forced_movement(). */
class RuntimeForcedMovementPreventionPrecedenceTest {
    @Test
    void trainerFeatureWinsWhenEveryPythonPushBlockerIsPresent() {
        Fixture fixture = fixture(List.of("Suction Cups"), true, true);

        RuntimePostHitForcedMovementApplication.Resolution resolution =
                RuntimePostHitForcedMovementApplication.resolve(
                        fixture.state(), fixture.choice(), true, insectoidWallclimberContent()
                );

        assertPreventedBy(
                resolution,
                ForcedMovementPreventionResolution.SourceKind.TRAINER_FEATURE,
                "Insectoid Utility"
        );
        assertEquals(new GridCoord(2, 1), fixture.target().position());
    }

    @Test
    void composedWinningTrainerFeatureProvenanceMapsToPinnedPythonSemanticEvent() {
        Fixture fixture = fixture(List.of("Suction Cups"), true, true);
        CombatantRuleContent content = insectoidWallclimberContent();
        BattleRuntimeDependencies dependencies = new BattleRuntimeDependencies(
                new CombatantRuleContentRegistry(Map.of("target", content))
        );

        RuntimePostHitForcedMovementApplication.SemanticResolution semantic =
                RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(
                        fixture.state(), fixture.choice(), true, dependencies
                );

        assertPreventedBy(
                semantic.resolution(),
                ForcedMovementPreventionResolution.SourceKind.TRAINER_FEATURE,
                "Insectoid Utility"
        );
        List<BattleEvent> events = semantic.events();
        assertEquals(1, events.size());
        TrainerFeatureEvent event = (TrainerFeatureEvent) events.getFirst();
        assertEquals("target", event.actorId());
        assertEquals("Insectoid Utility", event.feature());
        assertEquals("forced_movement_block", event.effect());
        assertEquals("source", event.details().get("target"));
        assertEquals("trainer", event.trainer());
        assertEquals("Insectoid Utility's Wallclimber upgrade prevents push effects.", event.description());
        assertEquals(20, event.targetHp());
        assertEquals(new GridCoord(2, 1), fixture.target().position());
    }

    @Test
    void temporaryPushImmunityWinsBeforeAbilityAndIngrain() {
        Fixture fixture = fixture(List.of("Suction Cups"), true, true);

        RuntimePostHitForcedMovementApplication.SemanticResolution semantic =
                RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(
                        fixture.state(), fixture.choice(), true, BattleRuntimeDependencies.empty()
                );

        assertPreventedBy(
                semantic.resolution(),
                ForcedMovementPreventionResolution.SourceKind.TEMPORARY_EFFECT,
                "Anchor Field"
        );
        AbilityEvent event = singleAbilityEvent(semantic.events());
        assertEquals("target", event.actorId());
        assertEquals("Anchor Field", event.ability());
        assertEquals("forced_movement_block", event.effect());
        assertEquals("source", event.target());
        assertEquals("Anchor Field prevents push effects.", event.description());
        assertEquals(20, event.targetHp());
    }

    @Test
    void suctionCupsErrataPreservesPythonAbilityIdentityAndPayload() {
        Fixture fixture = fixture(List.of("Suction Cups [Errata]"), false, true);

        RuntimePostHitForcedMovementApplication.SemanticResolution semantic =
                RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(
                        fixture.state(), fixture.choice(), true, BattleRuntimeDependencies.empty()
                );

        assertPreventedBy(
                semantic.resolution(),
                ForcedMovementPreventionResolution.SourceKind.ABILITY,
                "Suction Cups [Errata]"
        );
        AbilityEvent event = singleAbilityEvent(semantic.events());
        assertEquals("target", event.actorId());
        assertEquals("Suction Cups [Errata]", event.ability());
        assertEquals("forced_movement_block", event.effect());
        assertEquals("source", event.target());
        assertEquals("Suction Cups prevents forced movement.", event.description());
        assertEquals(20, event.targetHp());
    }

    @Test
    void sumoStanceErrataPreservesPythonAbilityIdentityAndPayload() {
        Fixture fixture = fixture(List.of("Sumo Stance [Errata]"), false, true);

        RuntimePostHitForcedMovementApplication.SemanticResolution semantic =
                RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(
                        fixture.state(), fixture.choice(), true, BattleRuntimeDependencies.empty()
                );

        assertPreventedBy(
                semantic.resolution(),
                ForcedMovementPreventionResolution.SourceKind.ABILITY,
                "Sumo Stance [Errata]"
        );
        AbilityEvent event = singleAbilityEvent(semantic.events());
        assertEquals("target", event.actorId());
        assertEquals("Sumo Stance [Errata]", event.ability());
        assertEquals("forced_movement_block", event.effect());
        assertEquals("source", event.target());
        assertEquals("Sumo Stance prevents push effects.", event.description());
        assertEquals(20, event.targetHp());
    }

    @Test
    void ingrainRemainsTheFinalDefenderPreventionFamilyAndEmitsNoAbilityEvent() {
        Fixture fixture = fixture(List.of(), false, true);

        RuntimePostHitForcedMovementApplication.SemanticResolution semantic =
                RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(
                        fixture.state(), fixture.choice(), true, BattleRuntimeDependencies.empty()
                );

        assertPreventedBy(
                semantic.resolution(),
                ForcedMovementPreventionResolution.SourceKind.STATUS,
                "Ingrain"
        );
        assertEquals(List.of(), semantic.events());
    }

    private static AbilityEvent singleAbilityEvent(List<BattleEvent> events) {
        assertEquals(1, events.size());
        return (AbilityEvent) events.getFirst();
    }

    private static void assertPreventedBy(
            RuntimePostHitForcedMovementApplication.Resolution resolution,
            ForcedMovementPreventionResolution.SourceKind sourceKind,
            String sourceName
    ) {
        assertFalse(resolution.movement().isPresent());
        assertEquals(sourceKind, resolution.prevention().sourceKind());
        assertEquals(sourceName, resolution.prevention().sourceName());
    }

    private static Fixture fixture(List<String> abilities, boolean pushImmunity, boolean ingrain) {
        RuntimeCombatantState source = combatant("source", 1, 1, List.of());
        RuntimeCombatantState target = combatant("target", 2, 1, abilities);
        if (pushImmunity) {
            target.temporaryEffects().add("push_immunity", Map.of("source", "Anchor Field"));
        }
        MoveOption move = pushMove();
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(source, target),
                ingrain ? Map.of("target", List.of("Ingrain")) : Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of("source", List.of(move))
        );
        MoveChoice choice = new MoveChoice(
                source.combatantId(), move.moveId(), ChoiceTargetMode.COMBATANT,
                target.combatantId(), target.position(), move.actionType()
        );
        return new Fixture(state, target, choice);
    }

    private static MoveOption pushMove() {
        return new MoveOption(
                "ram",
                new MoveSpec(
                        "Melee", "Melee", 1, 1, null, null, "Melee",
                        List.of("push 2"), "Push the target 2 meters."
                ),
                ActionType.STANDARD,
                true
        );
    }

    private static CombatantRuleContent insectoidWallclimberContent() {
        return new CombatantRuleContent(
                List.of("Wallclimber"), null, "trainer", Map.of(),
                List.of("Insectoid Utility"), List.of()
        );
    }

    private static RuntimeCombatantState combatant(
            String id,
            int x,
            int y,
            List<String> abilities
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                new ActionBudget(),
                null,
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

    private record Fixture(
            BattleRuntimeState state,
            RuntimeCombatantState target,
            MoveChoice choice
    ) {}
}
