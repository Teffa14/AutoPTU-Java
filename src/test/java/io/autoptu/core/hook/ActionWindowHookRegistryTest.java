package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionWindowHookRegistryTest {
    @Test
    void resolvesMatchingWindowByExplicitOrderAndPreservesTieOrder() {
        ActionWindowHookRegistry registry = ActionWindowHookRegistry.builder()
                .register("late-one", HookSource.ITEM, Set.of(ActionWindow.BEFORE_ACTION), 20,
                        context -> List.of(candidate("item")))
                .register("early", HookSource.ABILITY, Set.of(ActionWindow.BEFORE_ACTION), 10,
                        context -> List.of(candidate("ability")))
                .register("late-two", HookSource.TRAINER_FEATURE, Set.of(ActionWindow.BEFORE_ACTION), 20,
                        context -> List.of(candidate("feature")))
                .register("wrong-window", HookSource.STATUS, Set.of(ActionWindow.AFTER_ACTION), 1,
                        context -> List.of(candidate("status")))
                .build();

        List<String> actionKeys = registry.candidates(context(ActionWindow.BEFORE_ACTION)).stream()
                .map(ActionWindowCandidate::actionKey)
                .toList();

        assertEquals(List.of("ability", "item", "feature"), actionKeys);
    }

    @Test
    void oneRegistrationCanServeMultipleWindows() {
        ActionWindowHookRegistry registry = ActionWindowHookRegistry.builder()
                .register("reaction", HookSource.REACTION,
                        Set.of(ActionWindow.BEFORE_HIT, ActionWindow.BEFORE_DAMAGE), 5,
                        context -> List.of(candidate(context.window().name().toLowerCase())))
                .build();

        assertEquals("before_hit", registry.candidates(context(ActionWindow.BEFORE_HIT)).getFirst().actionKey());
        assertEquals("before_damage", registry.candidates(context(ActionWindow.BEFORE_DAMAGE)).getFirst().actionKey());
    }

    @Test
    void rejectsDuplicateSourceAndId() {
        ActionWindowHookRegistry.Builder builder = ActionWindowHookRegistry.builder()
                .register("same", HookSource.ABILITY, Set.of(ActionWindow.BEFORE_ACTION), 1,
                        context -> List.of());

        assertThrows(IllegalArgumentException.class,
                () -> builder.register("same", HookSource.ABILITY, Set.of(ActionWindow.AFTER_ACTION), 2,
                        context -> List.of()));
    }

    @Test
    void rejectsNullHookResultsAndCandidates() {
        ActionWindowHookRegistry nullResult = ActionWindowHookRegistry.builder()
                .register("broken-result", HookSource.SYSTEM, Set.of(ActionWindow.BEFORE_ACTION), 1,
                        context -> null)
                .build();
        assertThrows(NullPointerException.class,
                () -> nullResult.candidates(context(ActionWindow.BEFORE_ACTION)));

        ActionWindowHookRegistry nullCandidate = ActionWindowHookRegistry.builder()
                .register("broken-candidate", HookSource.SYSTEM, Set.of(ActionWindow.BEFORE_ACTION), 1,
                        context -> java.util.Arrays.asList((ActionWindowCandidate) null))
                .build();
        assertThrows(NullPointerException.class,
                () -> nullCandidate.candidates(context(ActionWindow.BEFORE_ACTION)));
    }

    private static ActionWindowContext context(ActionWindow window) {
        return new ActionWindowContext(window, "actor", "move:test");
    }

    private static ActionWindowCandidate candidate(String actionKey) {
        return new ActionWindowCandidate("reactor", actionKey, "move:test");
    }
}
