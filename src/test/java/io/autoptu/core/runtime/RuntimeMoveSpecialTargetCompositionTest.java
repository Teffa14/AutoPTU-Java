package io.autoptu.core.runtime;

import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.model.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeMoveSpecialTargetCompositionTest {
    @Test
    void preservesEndActionInputsAcrossBattleRuntimeEventOrdering() {
        StatusSkipEvent resolved = event("move-special", "resolved");
        StatusSkipEvent forcedMovement = event("forced-movement", "after_damage");
        StatusSkipEvent postDamage = event("ability", "post_damage");
        StatusSkipEvent reaction = event("reaction", "pre_damage");
        StatusSkipEvent moveSpecialPreDamage = event("move-special", "pre_damage");
        StatusSkipEvent preResolution = event("targeting", "pre_resolution");
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("marker", "post_damage");

        RuntimeMoveSpecialPostDamageApplication.Result postDamageResult =
                new RuntimeMoveSpecialPostDamageApplication.Result(
                        new AppliedActionResult(List.of(resolved)), snapshot, 13);

        MoveSpecialTargetResult composed = RuntimeMoveSpecialTargetComposition.compose(
                postDamageResult,
                List.of(forcedMovement),
                List.of(postDamage),
                List.of(reaction),
                List.of(moveSpecialPreDamage),
                List.of(preResolution));

        assertEquals(
                List.of(preResolution, moveSpecialPreDamage, reaction, postDamage, resolved, forcedMovement),
                composed.events());
        assertEquals(13, composed.damageDealt());
        assertEquals("post_damage", composed.resultSnapshot().get("marker"));
        assertThrows(UnsupportedOperationException.class,
                () -> composed.resultSnapshot().put("mutate", true));
    }

    @Test
    void emptyLaterStagesDoNotDiscardPostDamageTransport() {
        StatusSkipEvent resolved = event("move-special", "resolved");
        RuntimeMoveSpecialPostDamageApplication.Result postDamageResult =
                new RuntimeMoveSpecialPostDamageApplication.Result(
                        new AppliedActionResult(List.of(resolved)),
                        new LinkedHashMap<>(java.util.Map.of("hit", true)),
                        7);

        MoveSpecialTargetResult composed = RuntimeMoveSpecialTargetComposition.compose(
                postDamageResult, null, List.of(), null, List.of(), null);

        assertEquals(List.of(resolved), composed.events());
        assertEquals(7, composed.damageDealt());
        assertEquals(true, composed.resultSnapshot().get("hit"));
    }

    private static StatusSkipEvent event(String status, String reason) {
        return new StatusSkipEvent("actor", status, TurnPhase.ACTION, reason);
    }
}
