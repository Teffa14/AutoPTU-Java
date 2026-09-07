package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Language-neutral target eligibility contract for Python Arena Trap.
 *
 * <p>This isolates the oracle's target semantics from adapters and from the eventual status
 * mutation handler. The caller must supply server-owned type, ability, capability, movement,
 * affiliation, activity, and distance projections.</p>
 */
public final class ArenaTrapTargetingContract {
    private ArenaTrapTargetingContract() {}

    public record Candidate(
            String actorId,
            String teamId,
            boolean active,
            boolean fainted,
            int distance,
            List<String> types,
            List<String> abilities,
            List<String> capabilities,
            int skySpeed,
            int burrowSpeed
    ) {
        public Candidate {
            if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
            actorId = actorId.strip();
            teamId = teamId == null ? "" : teamId.strip();
            distance = Math.max(0, distance);
            types = normalized(types);
            abilities = normalized(abilities);
            capabilities = normalized(capabilities);
            skySpeed = Math.max(0, skySpeed);
            burrowSpeed = Math.max(0, burrowSpeed);
        }
    }

    /** Regular Arena Trap is global in the pinned Python oracle. */
    public static List<String> regularTargets(String holderTeamId, List<Candidate> candidates) {
        String team = holderTeamId == null ? "" : holderTeamId.strip();
        ArrayList<String> targets = new ArrayList<>();
        for (Candidate candidate : safe(candidates)) {
            if (eligible(team, candidate, false)) targets.add(candidate.actorId());
        }
        return List.copyOf(targets);
    }

    /** Arena Trap [Errata] uses the same immunity family but only reaches foes within 5 meters. */
    public static List<String> errataTargets(String holderTeamId, boolean errataActive, List<Candidate> candidates) {
        if (!errataActive) return List.of();
        String team = holderTeamId == null ? "" : holderTeamId.strip();
        ArrayList<String> targets = new ArrayList<>();
        for (Candidate candidate : safe(candidates)) {
            if (eligible(team, candidate, true)) targets.add(candidate.actorId());
        }
        return List.copyOf(targets);
    }

    private static boolean eligible(String holderTeamId, Candidate candidate, boolean rangeLimited) {
        Objects.requireNonNull(candidate, "candidate");
        if (!candidate.active() || candidate.fainted()) return false;
        if (candidate.teamId().equals(holderTeamId)) return false;
        if (rangeLimited && candidate.distance() > 5) return false;
        return !immune(candidate);
    }

    private static boolean immune(Candidate candidate) {
        Set<String> types = Set.copyOf(candidate.types());
        Set<String> abilities = Set.copyOf(candidate.abilities());
        Set<String> capabilities = Set.copyOf(candidate.capabilities());
        return types.contains("flying")
                || abilities.contains("levitate")
                || capabilities.contains("levitate")
                || candidate.skySpeed() >= 4
                || candidate.burrowSpeed() >= 4;
    }

    private static List<Candidate> safe(List<Candidate> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> normalized(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        ArrayList<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            normalized.add(value.strip().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }
}
