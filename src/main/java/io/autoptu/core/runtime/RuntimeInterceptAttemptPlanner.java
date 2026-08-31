package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composes Python-compatible Intercept discovery, expiry cleanup and candidate ordering from
 * authoritative battle/content state.
 *
 * <p>The adapter supplies canonical combatant content, never prepared Intercept candidates. This
 * planner derives eligibility and prepared sources through the shared discovery resolver, commits
 * Python-compatible temporary-effect cleanup, and orders candidates by the shared footprint
 * geometry contract before materializing the internal spatial attempts.</p>
 */
public final class RuntimeInterceptAttemptPlanner {
    private RuntimeInterceptAttemptPlanner() {}

    public record Result(
            InterceptCandidateDiscoveryResolution.Result discovery,
            RuntimeInterceptCandidateCleanupApplication.Result cleanup,
            List<RuntimeInterceptSpatialSequenceApplication.Attempt> attempts
    ) {
        public Result {
            if (discovery == null) throw new IllegalArgumentException("discovery is required");
            if (cleanup == null) throw new IllegalArgumentException("cleanup is required");
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
        }
    }

    public static Result plan(
            BattleRuntimeState state,
            String attackerId,
            String targetId,
            String interceptKind,
            Map<String, CombatantRuleContent> contentByCombatant
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        Map<String, CombatantRuleContent> content = contentByCombatant == null
                ? Map.of()
                : Map.copyOf(contentByCombatant);

        InterceptCandidateDiscoveryResolution.Result discovery = InterceptCandidateDiscoveryResolution.resolve(
                RuntimeInterceptCandidateDiscoveryFactory.build(
                        state,
                        attackerId,
                        targetId,
                        interceptKind,
                        content
                )
        );
        RuntimeInterceptCandidateCleanupApplication.Result cleanup =
                RuntimeInterceptCandidateCleanupApplication.apply(state, attackerId, discovery);
        if (discovery.suppressedByNoIntercept() || discovery.candidates().isEmpty()) {
            return new Result(discovery, cleanup, List.of());
        }

        Map<String, InterceptCandidateDiscoveryResolution.Candidate> candidateById = new LinkedHashMap<>();
        ArrayList<InterceptGeometryResolution.Candidate> geometryCandidates = new ArrayList<>();
        for (InterceptCandidateDiscoveryResolution.Candidate candidate : discovery.candidates()) {
            // A combatant may expose more than one prepared source. Python's stable ordering keeps
            // discovery order for equal footprint distances, so retain every source separately.
            RuntimeCombatantState combatant = state.requireCombatant(candidate.combatantId());
            String key = candidate.combatantId() + "\u0000" + geometryCandidates.size();
            candidateById.put(key, candidate);
            geometryCandidates.add(new InterceptGeometryResolution.Candidate(
                    key,
                    combatant.position(),
                    state.geometry(candidate.combatantId()).sizeLabel()
            ));
        }

        GridCoord targetPosition = state.requireCombatant(targetId).position();
        List<InterceptGeometryResolution.Candidate> ordered =
                InterceptGeometryResolution.orderCandidates(geometryCandidates, targetPosition);
        ArrayList<RuntimeInterceptSpatialSequenceApplication.Attempt> attempts = new ArrayList<>();
        for (InterceptGeometryResolution.Candidate geometry : ordered) {
            InterceptCandidateDiscoveryResolution.Candidate candidate = candidateById.get(geometry.combatantId());
            CombatantRuleContent ruleContent = content.getOrDefault(
                    candidate.combatantId(),
                    CombatantRuleContent.empty()
            );
            attempts.add(new RuntimeInterceptSpatialSequenceApplication.Attempt(candidate, ruleContent));
        }
        return new Result(discovery, cleanup, List.copyOf(attempts));
    }
}
