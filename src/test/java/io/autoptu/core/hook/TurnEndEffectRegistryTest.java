package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BorrowMoveEndedEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnEndEffectRegistryTest {
    private static final MoveSpec MOVE_SPEC = new MoveSpec("1 Target", "Melee", 1, 1, null, null, "Melee");

    @Test
    void resolvesActorAndGlobalEffectsInRegistrationOrderWithStableRosterTraversal() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        RuntimeCombatantState other = combatant("other", new GridCoord(1, 0));
        BattleRuntimeState state = state(actor, other);
        List<String> calls = new ArrayList<>();

        TurnEndEffectRegistry registry = TurnEndEffectRegistry.builder()
                .register("global-second", TurnEndEffectRegistry.Scope.ALL_COMBATANTS, 20, (context, combatantId) -> {
                    calls.add("global:" + combatantId);
                    return LifecycleHookResult.empty();
                })
                .register("actor-first", TurnEndEffectRegistry.Scope.ACTOR, 10, (context, combatantId) -> {
                    calls.add("actor:" + combatantId);
                    return LifecycleHookResult.empty();
                })
                .build();

        registry.resolve(context(state, "actor", LifecycleHookPoint.TURN_END));

        assertEquals(List.of("actor:actor", "global:actor", "global:other"), calls);
        assertEquals(List.of("actor-first", "global-second"),
                registry.registrations().stream().map(TurnEndEffectRegistry.Registration::id).toList());
    }

    @Test
    void actorScopedEffectRequiresActorIdentity() {
        TurnEndEffectRegistry registry = TurnEndEffectRegistry.builder()
                .register("actor-only", TurnEndEffectRegistry.Scope.ACTOR, 10,
                        (context, combatantId) -> LifecycleHookResult.empty())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(context(state(combatant("actor", new GridCoord(0, 0))), "", LifecycleHookPoint.TURN_END)));
    }

    @Test
    void rejectsNonTurnEndLifecycleContext() {
        TurnEndEffectRegistry registry = TurnEndEffectRegistry.builder().build();

        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(context(state(combatant("actor", new GridCoord(0, 0))), "actor", LifecycleHookPoint.PHASE_CHANGE)));
    }

    @Test
    void rejectsDuplicateIdsCaseInsensitively() {
        TurnEndEffectRegistry.Builder builder = TurnEndEffectRegistry.builder()
                .register("adaptive", TurnEndEffectRegistry.Scope.ACTOR, 10,
                        (context, combatantId) -> LifecycleHookResult.empty());

        assertThrows(IllegalArgumentException.class,
                () -> builder.register("ADAPTIVE", TurnEndEffectRegistry.Scope.ACTOR, 20,
                        (context, combatantId) -> LifecycleHookResult.empty()));
    }

    @Test
    void builtinTurnEndBoundaryFollowsCleanupAndRefresh() {
        List<LifecycleHookRegistry.Registration> hooks = BuiltinLifecycleHooks.registry().registrations().stream()
                .filter(registration -> registration.point() == LifecycleHookPoint.TURN_END)
                .toList();

        assertEquals(List.of(
                        "turn-extra-action-cleanup",
                        "turn-last-turn-round-refresh",
                        "turn-end-effects"
                ), hooks.stream().map(LifecycleHookRegistry.Registration::id).toList());
        assertEquals(List.of(490, 500, 510), hooks.stream().map(LifecycleHookRegistry.Registration::order).toList());
    }

    @Test
    void builtinAdaptiveGeographyRemovesOnlyActorsFeatureOwnedTerrainAlias() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        RuntimeCombatantState other = combatant("other", new GridCoord(1, 0));
        actor.temporaryEffects().add("terrain_alias", Map.of("feature", "Adaptive Geography", "terrain", "forest"));
        actor.temporaryEffects().add("terrain_alias", Map.of("feature", "Other Feature", "terrain", "urban"));
        actor.temporaryEffects().add("psychic_residue", Map.of("source", "Suggestion"));
        other.temporaryEffects().add("terrain_alias", Map.of("feature", "Adaptive Geography", "terrain", "tundra"));
        BattleRuntimeState state = state(actor, other);

        LifecycleHookResult result = BuiltinTurnEndEffects.registry()
                .resolve(context(state, "actor", LifecycleHookPoint.TURN_END));

        assertEquals(1, actor.temporaryEffects().count("terrain_alias"));
        assertEquals("Other Feature", actor.temporaryEffects().getAll("terrain_alias").get(0).payload().get("feature"));
        assertEquals(1, actor.temporaryEffects().count("psychic_residue"));
        assertEquals(1, other.temporaryEffects().count("terrain_alias"));
        assertEquals(List.of(), result.events());
    }

    @Test
    void builtinAdaptiveGeographyCleanupUsesPythonMetadataNormalization() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        actor.temporaryEffects().add("terrain_alias", Map.of("feature", "  adaptive geography  ", "terrain", "forest"));
        actor.temporaryEffects().add("terrain_alias", Map.of("terrain", "cave"));
        BattleRuntimeState state = state(actor);

        BuiltinTurnEndEffects.registry().resolve(context(state, "actor", LifecycleHookPoint.TURN_END));

        assertEquals(1, actor.temporaryEffects().count("terrain_alias"));
        assertEquals("cave", actor.temporaryEffects().getAll("terrain_alias").get(0).payload().get("terrain"));
    }

    @Test
    void builtinPsionicSpongeExpiresBorrowedMovesAndEmitsSortedSemanticEvent() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        RuntimeCombatantState other = combatant("other", new GridCoord(1, 0));
        actor.temporaryEffects().add("psionic_sponge_move", Map.of("name", " Psybeam "));
        actor.temporaryEffects().add("psionic_sponge_move", Map.of("name", "CONFUSION"));
        actor.temporaryEffects().add("psionic_sponge_move", Map.of("name", "confusion"));
        actor.temporaryEffects().add("psionic_sponge_move", Map.of("name", ""));
        other.temporaryEffects().add("psionic_sponge_move", Map.of("name", "Confusion"));
        MoveOption tackle = move("Tackle");
        MoveOption confusion = move("Confusion");
        MoveOption psybeam = move("Psybeam");
        BattleRuntimeState state = stateWithMoves(
                Map.of(
                        "actor", List.of(tackle, confusion, psybeam),
                        "other", List.of(move("Confusion"))
                ),
                actor,
                other
        );

        LifecycleHookResult result = BuiltinTurnEndEffects.registry()
                .resolve(context(state, "actor", LifecycleHookPoint.TURN_END));

        assertEquals(List.of(tackle), state.moveOptions("actor"));
        assertEquals(0, actor.temporaryEffects().count("psionic_sponge_move"));
        assertEquals(1, other.temporaryEffects().count("psionic_sponge_move"));
        assertEquals(1, result.events().size());
        BorrowMoveEndedEvent event = assertInstanceOf(BorrowMoveEndedEvent.class, result.events().get(0));
        assertEquals("actor", event.combatantId());
        assertEquals(List.of("confusion", "psybeam"), event.moves());
    }

    @Test
    void builtinPsionicSpongeDrainsMetadataOnlyEntriesWithoutInventingEvent() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        actor.temporaryEffects().add("psionic_sponge_move", Map.of("source", "Psionic Sponge"));
        actor.temporaryEffects().add("psionic_sponge_move", Map.of("name", "   "));
        MoveOption tackle = move("Tackle");
        BattleRuntimeState state = stateWithMoves(Map.of("actor", List.of(tackle)), actor);

        LifecycleHookResult result = BuiltinTurnEndEffects.registry()
                .resolve(context(state, "actor", LifecycleHookPoint.TURN_END));

        assertEquals(List.of(tackle), state.moveOptions("actor"));
        assertEquals(0, actor.temporaryEffects().count("psionic_sponge_move"));
        assertEquals(List.of(), result.events());
    }

    @Test
    void builtinTurnEndEffectCatalogMatchesPythonFamilyOrder() {
        assertEquals(List.of(
                        "adaptive-geography-terrain-alias-cleanup",
                        "psionic-sponge-borrowed-move-cleanup"
                ), BuiltinTurnEndEffects.registry().registrations().stream()
                        .map(TurnEndEffectRegistry.Registration::id)
                        .toList());
        assertEquals(List.of(10, 20),
                BuiltinTurnEndEffects.registry().registrations().stream()
                        .map(TurnEndEffectRegistry.Registration::order)
                        .toList());
    }

    private static LifecycleHookContext context(
            BattleRuntimeState state,
            String actorId,
            LifecycleHookPoint point
    ) {
        return new LifecycleHookContext(
                state,
                state.damageHistory(),
                state.injuryHistory(),
                point,
                4,
                4,
                actorId,
                TurnPhase.END
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 1),
                20,
                20,
                new ActionBudget()
        );
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, MOVE_SPEC);
    }

    private static BattleRuntimeState state(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(combatants),
                Map.of()
        );
    }

    private static BattleRuntimeState stateWithMoves(
            Map<String, ? extends List<MoveOption>> moves,
            RuntimeCombatantState... combatants
    ) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(combatants),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                moves
        );
    }
}
