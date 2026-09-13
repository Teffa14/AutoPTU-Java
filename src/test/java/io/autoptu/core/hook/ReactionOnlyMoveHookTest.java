package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionOnlyMoveHookTest {
    @Test
    void discoversOnlyReactionClassifiedMovesInTheCurrentWindow() {
        ReactionOnlyMoveHook hook = new ReactionOnlyMoveHook("reactor", List.of(
                move("move:interrupt", Set.of(ActionWindow.BEFORE_HIT), "Interrupt", "", "", "", ""),
                move("move:after-damage", Set.of(ActionWindow.AFTER_DAMAGE), "Reaction", "", "", "", ""),
                move("move:ordinary", Set.of(ActionWindow.BEFORE_HIT), "Standard", "Melee, 1 Target", "Deal damage.", "", "")
        ));

        List<ActionWindowCandidate> candidates = hook.candidates(
                new ActionWindowContext(ActionWindow.BEFORE_HIT, "actor", "move:attack"));

        assertEquals(1, candidates.size());
        assertEquals(new ActionWindowCandidate("reactor", "move:interrupt", "move:attack"), candidates.getFirst());
    }

    @Test
    void canonicalTriggerTextCanProduceAWindowCandidate() {
        ReactionOnlyMoveHook hook = new ReactionOnlyMoveHook("reactor", List.of(
                move("move:riposte", Set.of(ActionWindow.AFTER_ACTION), "", "", "", "Melee, 1 Target", "Trigger: a foe misses you")
        ));

        List<ActionWindowCandidate> candidates = hook.candidates(
                new ActionWindowContext(ActionWindow.AFTER_ACTION, "foe", "move:missed-attack"));

        assertEquals(List.of(new ActionWindowCandidate("reactor", "move:riposte", "move:missed-attack")), candidates);
    }

    @Test
    void semanticTriggerFiltersAttackOfOpportunityInsideTheSameWindow() {
        ReactionOnlyMoveHook hook = new ReactionOnlyMoveHook("reactor", List.of(
                triggeredMove(
                        "move:attack-of-opportunity",
                        Set.of(ActionWindow.BEFORE_ACTION),
                        Set.of(ActionWindowTrigger.ADJACENT_FOE_SHIFTS_AWAY),
                        "Interrupt",
                        "Self, Trait: Interrupt 1",
                        "You may make a Struggle Attack against the triggering foe as an Interrupt. "
                                + "An adjacent foe Shifts out of a Square adjacent to you.",
                        "",
                        "")
        ));

        assertEquals(
                List.of(new ActionWindowCandidate("reactor", "move:attack-of-opportunity", "shift:foe")),
                hook.candidates(new ActionWindowContext(
                        ActionWindow.BEFORE_ACTION,
                        "foe",
                        "shift:foe",
                        ActionWindowTrigger.ADJACENT_FOE_SHIFTS_AWAY)));
        assertTrue(hook.candidates(new ActionWindowContext(
                ActionWindow.BEFORE_ACTION,
                "foe",
                "shift:foe")).isEmpty());
    }

    @Test
    void registryControlsWhichWindowsInvokeTheMoveDiscoveryHook() {
        ReactionOnlyMoveHook hook = new ReactionOnlyMoveHook("reactor", List.of(
                move("move:interrupt", Set.of(ActionWindow.BEFORE_HIT), "Interrupt", "", "", "", "")
        ));
        ActionWindowHookRegistry registry = ActionWindowHookRegistry.builder()
                .register("reaction-moves", HookSource.MOVE, Set.of(ActionWindow.BEFORE_HIT), 10, hook)
                .build();

        assertEquals(1, registry.candidates(
                new ActionWindowContext(ActionWindow.BEFORE_HIT, "actor", "move:attack")).size());
        assertTrue(registry.candidates(
                new ActionWindowContext(ActionWindow.AFTER_ACTION, "actor", "move:attack")).isEmpty());
    }

    private static ReactionOnlyMoveHook.MoveSpec move(
            String actionKey,
            Set<ActionWindow> windows,
            String activation,
            String rangeText,
            String effectsText,
            String canonicalRangeText,
            String canonicalEffectsText
    ) {
        return new ReactionOnlyMoveHook.MoveSpec(
                actionKey,
                windows,
                activation,
                rangeText,
                effectsText,
                canonicalRangeText,
                canonicalEffectsText);
    }

    private static ReactionOnlyMoveHook.MoveSpec triggeredMove(
            String actionKey,
            Set<ActionWindow> windows,
            Set<ActionWindowTrigger> triggers,
            String activation,
            String rangeText,
            String effectsText,
            String canonicalRangeText,
            String canonicalEffectsText
    ) {
        return new ReactionOnlyMoveHook.MoveSpec(
                actionKey,
                windows,
                triggers,
                activation,
                rangeText,
                effectsText,
                canonicalRangeText,
                canonicalEffectsText);
    }
}
