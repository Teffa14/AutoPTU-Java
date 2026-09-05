package io.autoptu.core.runtime;

import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoveSpecialTargetResultTransportTest {
    @Test
    void capturesAppliedDamageAndPostDamageSnapshotWithoutMutatingOutcome() {
        RuntimeCombatantState target = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        target.setHp(41);
        AppliedActionResult outcome = new AppliedActionResult(List.of(
                new StatusSkipEvent("actor", "move-special", TurnPhase.ACTION, "post_damage")));
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hit", true);
        snapshot.put("marker", "post_damage");

        MoveSpecialTargetResult transported = MoveSpecialTargetResult.fromAppliedOutcome(
                outcome, snapshot, true, 50, target);

        assertEquals(outcome.events(), transported.events());
        assertEquals(9, transported.damageDealt());
        assertEquals("post_damage", transported.resultSnapshot().get("marker"));
        assertEquals(41, target.hp());
        assertThrows(UnsupportedOperationException.class,
                () -> transported.resultSnapshot().put("mutate", true));
    }

    @Test
    void preservesSnapshotAndDamageWhenLaterPipelineStagesReplaceTheActionResult() {
        RuntimeCombatantState target = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        target.setHp(41);
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("marker", "post_damage");
        MoveSpecialTargetResult transported = MoveSpecialTargetResult.fromAppliedOutcome(
                new AppliedActionResult(List.of()), snapshot, true, 50, target);
        AppliedActionResult composed = new AppliedActionResult(List.of(
                new StatusSkipEvent("actor", "forced-movement", TurnPhase.ACTION, "after_damage")));

        MoveSpecialTargetResult recomposed = transported.withActionResult(composed);

        assertEquals(composed.events(), recomposed.events());
        assertEquals(9, recomposed.damageDealt());
        assertEquals("post_damage", recomposed.resultSnapshot().get("marker"));
        assertEquals(41, target.hp());
    }

    @Test
    void composesPreAndPostEventsWithoutLosingEndActionInputs() {
        RuntimeCombatantState target = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        target.setHp(41);
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("marker", "post_damage");
        StatusSkipEvent moveResolved = new StatusSkipEvent(
                "actor", "move-special", TurnPhase.ACTION, "resolved");
        StatusSkipEvent forcedMovement = new StatusSkipEvent(
                "actor", "forced-movement", TurnPhase.ACTION, "after_damage");
        StatusSkipEvent postResult = new StatusSkipEvent(
                "actor", "ability", TurnPhase.ACTION, "post_result");
        StatusSkipEvent preDamage = new StatusSkipEvent(
                "actor", "reaction", TurnPhase.ACTION, "pre_damage");
        StatusSkipEvent preResolution = new StatusSkipEvent(
                "actor", "targeting", TurnPhase.ACTION, "pre_resolution");
        MoveSpecialTargetResult transported = MoveSpecialTargetResult.fromAppliedOutcome(
                new AppliedActionResult(List.of(moveResolved)), snapshot, true, 50, target);

        MoveSpecialTargetResult composed = transported
                .appendEvents(List.of(forcedMovement))
                .prependEvents(List.of(postResult))
                .prependEvents(List.of(preDamage))
                .prependEvents(List.of(preResolution));

        assertEquals(
                List.of(preResolution, preDamage, postResult, moveResolved, forcedMovement),
                composed.events());
        assertEquals(9, composed.damageDealt());
        assertEquals("post_damage", composed.resultSnapshot().get("marker"));
        assertEquals(41, target.hp());
    }

    @Test
    void rejectsNullEventsDuringTransportComposition() {
        MoveSpecialTargetResult transported = new MoveSpecialTargetResult(
                new AppliedActionResult(List.of()), new LinkedHashMap<>(), 0);

        assertThrows(IllegalArgumentException.class,
                () -> transported.prependEvents(java.util.Arrays.asList((StatusSkipEvent) null)));
        assertThrows(IllegalArgumentException.class,
                () -> transported.appendEvents(java.util.Arrays.asList((StatusSkipEvent) null)));
    }

    @Test
    void missCarriesZeroDamageEvenIfTargetHpChangedOutsideTheMove() {
        RuntimeCombatantState target = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        target.setHp(35);

        MoveSpecialTargetResult transported = MoveSpecialTargetResult.fromAppliedOutcome(
                new AppliedActionResult(List.of()), null, false, 50, target);

        assertEquals(0, transported.damageDealt());
        assertEquals(0, transported.resultSnapshot().size());
    }
}
