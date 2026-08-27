package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Python-compatible discovery policy for prepared interception sources.
 *
 * <p>This contract deliberately stops before candidate-distance ordering, skill checks, RNG,
 * or movement. Runtime callers must derive team, HP, ability/capability, temporary-effect,
 * loyalty and action-budget inputs from {@link BattleRuntimeState}. Minecraft/Cobblemon must
 * never mark an interceptor as prepared or eligible.</p>
 */
public final class InterceptCandidateDiscoveryResolution {
    private InterceptCandidateDiscoveryResolution() {}

    public record NoInterceptEntry(Integer expiresRound) {}

    public record ReadyEntry(String allyId, String interceptKind, String source) {
        public ReadyEntry {
            allyId = allyId == null ? "" : allyId.strip();
            interceptKind = normalize(interceptKind);
            source = source == null || source.isBlank() ? "Intercept" : source.strip();
        }
    }

    public record SentinelEntry(Integer expiresRound) {}

    public record CombatantInput(
            String combatantId,
            boolean living,
            boolean sameTeamAsTarget,
            boolean weaponizeAbility,
            boolean livingWeaponCapability,
            boolean controllerIsProtectedTarget,
            boolean interceptEligibilityAllowed,
            boolean loyaltyAllowsIntercept,
            List<ReadyEntry> interceptReadyEntries,
            List<SentinelEntry> sentinelEntries,
            boolean shiftAvailable,
            int extraShiftCount
    ) {
        public CombatantInput {
            if (combatantId == null || combatantId.isBlank()) {
                throw new IllegalArgumentException("combatantId is required");
            }
            combatantId = combatantId.strip();
            interceptReadyEntries = interceptReadyEntries == null ? List.of() : List.copyOf(interceptReadyEntries);
            sentinelEntries = sentinelEntries == null ? List.of() : List.copyOf(sentinelEntries);
            extraShiftCount = Math.max(0, extraShiftCount);
        }
    }

    public record Input(
            String targetId,
            String interceptKind,
            int currentRound,
            List<NoInterceptEntry> attackerNoInterceptEntries,
            List<CombatantInput> combatants
    ) {
        public Input {
            if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
            targetId = targetId.strip();
            interceptKind = normalize(interceptKind);
            if (!interceptKind.equals("melee") && !interceptKind.equals("ranged")) {
                throw new IllegalArgumentException("interceptKind must be melee or ranged");
            }
            if (currentRound < 0) throw new IllegalArgumentException("currentRound cannot be negative");
            attackerNoInterceptEntries = attackerNoInterceptEntries == null ? List.of() : List.copyOf(attackerNoInterceptEntries);
            combatants = combatants == null ? List.of() : List.copyOf(combatants);
        }
    }

    public record Candidate(String combatantId, String source, boolean usesShift) {}

    public record Result(
            boolean suppressedByNoIntercept,
            int attackerNoInterceptRemovalCount,
            List<Candidate> candidates,
            Map<String, Integer> sentinelRemovalCountByCombatant
    ) {}

    public static Result resolve(Input input) {
        if (input == null) throw new IllegalArgumentException("input is required");

        int noInterceptRemovals = 0;
        for (NoInterceptEntry entry : input.attackerNoInterceptEntries()) {
            if (entry == null) continue;
            Integer expiresRound = entry.expiresRound();
            if (expiresRound != null && input.currentRound() > expiresRound) {
                noInterceptRemovals += 1;
                continue;
            }
            return new Result(true, noInterceptRemovals, List.of(), Map.of());
        }

        ArrayList<Candidate> candidates = new ArrayList<>();
        LinkedHashMap<String, Integer> sentinelRemovals = new LinkedHashMap<>();
        for (CombatantInput combatant : input.combatants()) {
            if (combatant == null) continue;
            if (combatant.combatantId().equals(input.targetId())) continue;
            if (!combatant.living() || !combatant.sameTeamAsTarget()) continue;

            if (combatant.weaponizeAbility()
                    && combatant.livingWeaponCapability()
                    && combatant.controllerIsProtectedTarget()) {
                if (combatant.interceptEligibilityAllowed() && combatant.loyaltyAllowsIntercept()) {
                    candidates.add(new Candidate(combatant.combatantId(), "Weaponize", false));
                    // Python continues to the next combatant after a qualifying Weaponize source.
                    continue;
                }
            }

            for (ReadyEntry entry : combatant.interceptReadyEntries()) {
                if (entry == null) continue;
                if (!entry.allyId().equals(input.targetId())) continue;
                if (!entry.interceptKind().equals(input.interceptKind())) continue;
                if (!combatant.interceptEligibilityAllowed() || !combatant.loyaltyAllowsIntercept()) continue;
                candidates.add(new Candidate(combatant.combatantId(), entry.source(), false));
            }

            for (SentinelEntry entry : combatant.sentinelEntries()) {
                if (entry == null) continue;
                Integer expiresRound = entry.expiresRound();
                if (expiresRound != null && input.currentRound() > expiresRound) {
                    sentinelRemovals.merge(combatant.combatantId(), 1, Integer::sum);
                    continue;
                }
                if (!combatant.interceptEligibilityAllowed() || !combatant.loyaltyAllowsIntercept()) continue;
                if (!combatant.shiftAvailable() && combatant.extraShiftCount() <= 0) continue;
                candidates.add(new Candidate(combatant.combatantId(), "Sentinel Stance", true));
            }
        }

        return new Result(
                false,
                noInterceptRemovals,
                List.copyOf(candidates),
                Map.copyOf(sentinelRemovals)
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
