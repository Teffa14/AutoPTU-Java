package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.MoveSpecialSecondaryStatusResolution;
import io.autoptu.core.hook.StatusApplicationHookRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-authoritative bridge from generic move-special status requests to the normal
 * status-application/prevention pipeline.
 *
 * <p>The text parser decides which conditions are requested. This boundary decides
 * whether each request actually enters canonical battle state. It deliberately reuses
 * {@link StatusApplicationResolution} so moves do not bypass ability, Safeguard, or
 * spatial prevention hooks.</p>
 */
public final class MoveSpecialSecondaryStatusApplication {
    private MoveSpecialSecondaryStatusApplication() {}

    public static Result apply(
            BattleRuntimeState state,
            StatusApplicationHookRegistry hooks,
            String attackerId,
            String defenderId,
            String moveName,
            String moveId,
            List<MoveSpecialSecondaryStatusResolution.StatusRequest> requests
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(hooks, "hooks");
        state.requireCombatant(attackerId);
        state.requireCombatant(defenderId);

        String canonicalMoveName = moveName == null ? "" : moveName.strip();
        String canonicalMoveId = moveId == null ? "" : moveId.strip();
        ArrayList<StatusApplicationResult> applications = new ArrayList<>();
        ArrayList<BattleEvent> events = new ArrayList<>();

        for (MoveSpecialSecondaryStatusResolution.StatusRequest request
                : requests == null ? List.<MoveSpecialSecondaryStatusResolution.StatusRequest>of() : requests) {
            if (request == null) continue;
            StatusEntry status = statusEntry(state, request);
            StatusApplicationResult application = StatusApplicationResolution.apply(
                    state,
                    hooks,
                    attackerId,
                    defenderId,
                    status,
                    "move",
                    canonicalMoveName,
                    canonicalMoveId
            );
            applications.add(application);
            events.addAll(application.events());
        }

        return new Result(applications, events);
    }

    private static StatusEntry statusEntry(
            BattleRuntimeState state,
            MoveSpecialSecondaryStatusResolution.StatusRequest request
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        if (request.remaining() != null) {
            payload.put("remaining", request.remaining());
        }
        if (request.statusName().equalsIgnoreCase("Flinched")) {
            payload.put("applied_round", state.currentRound());
        }
        return new StatusEntry(request.statusName(), payload);
    }

    public record Result(List<StatusApplicationResult> applications, List<BattleEvent> events) {
        public Result {
            applications = applications == null ? List.of() : List.copyOf(applications);
            events = events == null ? List.of() : List.copyOf(events);
        }

        public long appliedCount() {
            return applications.stream().filter(StatusApplicationResult::applied).count();
        }
    }
}
