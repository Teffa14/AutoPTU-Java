package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.MoveSpecialEffectRollResolution;
import io.autoptu.core.hook.MoveSpecialEffectRollRuntimeInputs;
import io.autoptu.core.hook.MoveSpecialSecondaryStatusResolution;
import io.autoptu.core.hook.StatusApplicationHookRegistry;
import io.autoptu.core.model.MoveCombatProfile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-only composition boundary for Python's generic text-driven secondary-status family.
 *
 * <p>The accuracy d20 must already be present in the shared move-special result produced by the
 * authoritative attack pipeline. All modifiers are then derived from {@link BattleRuntimeState},
 * parsed into ordered status requests, and committed through the normal status-application hooks.
 * Minecraft/Cobblemon never supplies the final effect roll or prevention outcome.</p>
 */
final class MoveSpecialSecondaryStatusPostDamage {
    private MoveSpecialSecondaryStatusPostDamage() {}

    static Result resolveAndApply(
            BattleRuntimeState state,
            StatusApplicationHookRegistry statusApplicationHooks,
            String attackerId,
            String defenderId,
            MoveOption move,
            Map<String, Object> sharedResult
    ) {
        if (move == null) throw new IllegalArgumentException("move is required");
        return resolveAndApply(
                state,
                statusApplicationHooks,
                attackerId,
                defenderId,
                move,
                move.requireCombatProfile(),
                sharedResult
        );
    }

    /** Uses effective type/category while keeping canonical move identity and effects text. */
    static Result resolveAndApply(
            BattleRuntimeState state,
            StatusApplicationHookRegistry statusApplicationHooks,
            String attackerId,
            String defenderId,
            MoveOption move,
            MoveCombatProfile effectiveProfile,
            Map<String, Object> sharedResult
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(statusApplicationHooks, "statusApplicationHooks");
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(effectiveProfile, "effectiveProfile");
        Objects.requireNonNull(sharedResult, "sharedResult");

        Object rawRoll = sharedResult.get("roll");
        if (!(rawRoll instanceof Number number)) {
            throw new IllegalStateException("move-special shared result requires authoritative accuracy roll");
        }

        int effectRoll = MoveSpecialEffectRollResolution.resolve(
                MoveSpecialEffectRollRuntimeInputs.fromState(
                        state,
                        attackerId,
                        defenderId,
                        move,
                        effectiveProfile,
                        number.intValue()
                )
        );
        List<MoveSpecialSecondaryStatusResolution.StatusRequest> requests =
                MoveSpecialSecondaryStatusResolution.resolve(move.spec().effectsText(), effectRoll);
        MoveSpecialSecondaryStatusApplication.Result application =
                MoveSpecialSecondaryStatusApplication.apply(
                        state,
                        statusApplicationHooks,
                        attackerId,
                        defenderId,
                        move.moveId(),
                        move.moveId(),
                        requests
                );
        return new Result(effectRoll, requests, application.applications(), application.events());
    }

    record Result(
            int effectRoll,
            List<MoveSpecialSecondaryStatusResolution.StatusRequest> requests,
            List<StatusApplicationResult> applications,
            List<BattleEvent> events
    ) {
        Result {
            requests = List.copyOf(requests == null ? List.of() : requests);
            applications = List.copyOf(applications == null ? List.of() : applications);
            events = List.copyOf(events == null ? List.of() : events);
        }

        long appliedCount() {
            return applications.stream().filter(StatusApplicationResult::applied).count();
        }
    }
}
