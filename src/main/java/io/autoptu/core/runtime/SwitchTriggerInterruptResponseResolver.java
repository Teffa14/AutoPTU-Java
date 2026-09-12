package io.autoptu.core.runtime;

import io.autoptu.core.hook.SwitchTriggerDecisionPlan;

/**
 * Server-authoritative interpreter for optional interrupt responses produced by adapters or UIs.
 *
 * <p>The response envelope preserves only the Python-observable shape needed by the pinned oracle:
 * truthiness, whether the value is a mapping, optional {@code accept}, and optional {@code choice}.
 * The resolver owns acceptance and replacement fallback policy; adapters do not validate PTU
 * replacement legality or select fallback combatants.</p>
 */
public final class SwitchTriggerInterruptResponseResolver {
    private SwitchTriggerInterruptResponseResolver() {
    }

    public static Resolution resolve(SwitchTriggerDecisionPlan plan, Response response) {
        if (plan == null) throw new IllegalArgumentException("switch trigger plan is required");
        if (response == null) throw new IllegalArgumentException("interrupt response is required");

        if (!response.truthy()) {
            return Resolution.rejected(plan);
        }

        String replacementId = plan.defaultReplacementId();
        if (response.mapping()) {
            if (Boolean.FALSE.equals(response.accept())) {
                return Resolution.rejected(plan);
            }
            String choice = normalizeChoice(response.choice());
            if (!choice.isEmpty() && plan.replacementIds().contains(choice)) {
                replacementId = choice;
            }
        }

        return Resolution.accepted(plan, replacementId);
    }

    private static String normalizeChoice(String value) {
        return value == null ? "" : value.strip();
    }

    /**
     * Language-neutral response envelope. A mapping with {@code accept == null} represents Python's
     * omitted accept key, whose pinned default is true. Choice may be null/blank/invalid; legality and
     * fallback stay inside this resolver.
     */
    public record Response(boolean truthy, boolean mapping, Boolean accept, String choice) {
        public Response {
            if (!mapping && (accept != null || choice != null)) {
                throw new IllegalArgumentException("non-mapping interrupt response cannot carry accept or choice");
            }
        }

        public static Response falsy() {
            return new Response(false, false, null, null);
        }

        public static Response truthyNonMapping() {
            return new Response(true, false, null, null);
        }

        public static Response mapping(Boolean accept, String choice) {
            return new Response(true, true, accept, choice);
        }
    }

    public record Resolution(
            SwitchTriggerDecisionPlan plan,
            boolean accepted,
            String replacementId
    ) {
        public Resolution {
            if (plan == null) throw new IllegalArgumentException("switch trigger plan is required");
            replacementId = replacementId == null ? "" : replacementId.strip();
            if (accepted && replacementId.isEmpty()) {
                throw new IllegalArgumentException("accepted interrupt requires replacementId");
            }
            if (!accepted && !replacementId.isEmpty()) {
                throw new IllegalArgumentException("rejected interrupt cannot select replacementId");
            }
        }

        static Resolution accepted(SwitchTriggerDecisionPlan plan, String replacementId) {
            return new Resolution(plan, true, replacementId);
        }

        static Resolution rejected(SwitchTriggerDecisionPlan plan) {
            return new Resolution(plan, false, "");
        }
    }
}
