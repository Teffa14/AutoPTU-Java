package io.autoptu.core.runtime;

import io.autoptu.core.rules.ActionBudget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mutable server-owned trainer state shared by combatants controlled by one trainer.
 *
 * Trainer Feature ownership, skills, AP, action state, initiative inputs, feature resources,
 * and feature usage are PTU rule state. Minecraft/Cobblemon may render them, but adapters
 * must not supply these values while battle rules are resolving.
 */
public final class TrainerRuntimeState {
    private final String trainerId;
    private final LinkedHashMap<String, String> featuresByNormalizedName = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> skillRanksByNormalizedName = new LinkedHashMap<>();
    private final ActionBudget actionBudget = new ActionBudget();
    private final List<TemporaryApGrant> temporaryAp = new ArrayList<>();
    private final LinkedHashMap<String, Object> featureResources = new LinkedHashMap<>();
    private final LinkedHashMap<String, Map<String, Object>> featureUsage = new LinkedHashMap<>();
    private final int initiativeModifier;
    private final Integer explicitInitiativeSpeed;
    private final String teamId;
    private int ap;

    /** Backwards-compatible trainer state with the Python default initiative modifier of zero. */
    public TrainerRuntimeState(String trainerId, Collection<String> trainerFeatures, int ap) {
        this(trainerId, trainerFeatures, ap, 0, Map.of(), null, "", Map.of(), Map.of());
    }

    public TrainerRuntimeState(
            String trainerId,
            Collection<String> trainerFeatures,
            int ap,
            int initiativeModifier
    ) {
        this(trainerId, trainerFeatures, ap, initiativeModifier, Map.of(), null, "", Map.of(), Map.of());
    }

    public TrainerRuntimeState(
            String trainerId,
            Collection<String> trainerFeatures,
            int ap,
            int initiativeModifier,
            Map<String, Integer> skillRanks
    ) {
        this(trainerId, trainerFeatures, ap, initiativeModifier, skillRanks, null, "", Map.of(), Map.of());
    }

    /**
     * Full server-owned Trainer initiative profile.
     *
     * explicitInitiativeSpeed mirrors Python TrainerState.speed: null means derive the
     * value from the Trainer's Pokemon. teamId mirrors TrainerState.team; blank uses the
     * Python identifier fallback when checking Tailwind.
     */
    public TrainerRuntimeState(
            String trainerId,
            Collection<String> trainerFeatures,
            int ap,
            int initiativeModifier,
            Map<String, Integer> skillRanks,
            Integer explicitInitiativeSpeed,
            String teamId
    ) {
        this(
                trainerId, trainerFeatures, ap, initiativeModifier, skillRanks,
                explicitInitiativeSpeed, teamId, Map.of(), Map.of()
        );
    }

    /**
     * Full server-owned Trainer state including generic Feature resources and usage.
     *
     * featureResources mirrors Python TrainerState.feature_resources. featureUsage mirrors
     * TrainerState.feature_usage and retains arbitrary bookkeeping fields because the Python
     * dispatcher stores per-feature counters using dynamic keys such as uses_round_N,
     * actor_round_ACTOR_N, last_round and cooldown_until.
     */
    public TrainerRuntimeState(
            String trainerId,
            Collection<String> trainerFeatures,
            int ap,
            int initiativeModifier,
            Map<String, Integer> skillRanks,
            Integer explicitInitiativeSpeed,
            String teamId,
            Map<String, ?> featureResources,
            Map<String, ? extends Map<String, ?>> featureUsage
    ) {
        if (trainerId == null || trainerId.isBlank()) {
            throw new IllegalArgumentException("trainerId is required");
        }
        if (ap < 0) {
            throw new IllegalArgumentException("trainer AP cannot be negative");
        }
        this.trainerId = trainerId.strip();
        if (trainerFeatures != null) {
            for (String feature : trainerFeatures) {
                if (feature == null || feature.isBlank()) continue;
                String canonical = feature.strip();
                String key = normalize(canonical);
                if (featuresByNormalizedName.putIfAbsent(key, canonical) != null) {
                    throw new IllegalArgumentException("duplicate Trainer Feature: " + canonical);
                }
            }
        }
        if (skillRanks != null) {
            for (Map.Entry<String, Integer> entry : skillRanks.entrySet()) {
                String name = entry.getKey();
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("Trainer skill name is required");
                }
                int rank = entry.getValue() == null ? 0 : entry.getValue();
                skillRanksByNormalizedName.put(normalize(name), rank);
            }
        }
        this.ap = ap;
        this.initiativeModifier = initiativeModifier;
        this.explicitInitiativeSpeed = explicitInitiativeSpeed;
        this.teamId = teamId == null ? "" : teamId.strip();
        replaceFeatureBookkeeping(featureResources, featureUsage);
    }

    public String trainerId() {
        return trainerId;
    }

    /** Canonical feature names in deterministic insertion order. */
    public List<String> trainerFeatures() {
        return List.copyOf(new ArrayList<>(featuresByNormalizedName.values()));
    }

    public boolean hasTrainerFeature(String featureName) {
        if (featureName == null || featureName.isBlank()) return false;
        return featuresByNormalizedName.containsKey(normalize(featureName));
    }

    /** Python TrainerState.skill_rank() semantics: case-insensitive lookup, missing => 0. */
    public int skillRank(String skillName) {
        if (skillName == null || skillName.isBlank()) return 0;
        return skillRanksByNormalizedName.getOrDefault(normalize(skillName), 0);
    }

    public Map<String, Integer> skillRanks() {
        return Map.copyOf(skillRanksByNormalizedName);
    }

    /** Server-owned Trainer action buckets, mirroring Python TrainerState.actions_taken. */
    public ActionBudget actionBudget() {
        return actionBudget;
    }

    /** Python TrainerState.reset_actions(): clear only the current action-use map. */
    public void resetActions() {
        actionBudget.resetConsumedActions();
    }

    public int ap() {
        return ap;
    }

    /** Read-only temporary AP grants in deterministic grant order. */
    public List<TemporaryApGrant> temporaryApGrants() {
        return List.copyOf(temporaryAp);
    }

    /**
     * Python TrainerState.grant_temporary_ap(): positive grants increase AP immediately
     * and retain round/source metadata for later expiry or spending.
     */
    public void grantTemporaryAp(int amount, int expiresRound, String source) {
        if (amount <= 0) return;
        temporaryAp.add(new TemporaryApGrant(amount, expiresRound, source));
        ap += amount;
    }

    /**
     * Python TrainerState.expire_temporary_ap(): expire only when currentRound is strictly
     * greater than expiresRound, then subtract the expired amount without allowing AP below zero.
     */
    public int expireTemporaryAp(int currentRound) {
        int expired = 0;
        ArrayList<TemporaryApGrant> remaining = new ArrayList<>();
        for (TemporaryApGrant grant : temporaryAp) {
            if (currentRound > grant.expiresRound()) {
                expired += grant.amount();
            } else {
                remaining.add(grant);
            }
        }
        temporaryAp.clear();
        temporaryAp.addAll(remaining);
        if (expired > 0) {
            ap = Math.max(0, ap - expired);
        }
        return expired;
    }

    /** Raw Python TrainerState.initiative_modifier used by Pokemon and Trainer entries. */
    public int initiativeModifier() {
        return initiativeModifier;
    }

    /** Null means Python must derive Trainer initiative Speed from controlled Pokemon. */
    public Integer explicitInitiativeSpeed() {
        return explicitInitiativeSpeed;
    }

    /** Python TrainerState.team. Blank means use trainerId when checking Tailwind. */
    public String teamId() {
        return teamId;
    }

    /** Server-owned generic Trainer Feature resources. */
    public Map<String, Object> featureResources() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(featureResources));
    }

    /** Server-owned generic Trainer Feature usage/cooldown bookkeeping. */
    public Map<String, Map<String, Object>> featureUsage() {
        return immutableFeatureUsage(featureUsage);
    }

    /**
     * Atomically replace generic Trainer Feature resources and usage after a successful
     * dispatcher transaction. Inputs are copied before either live map is mutated.
     */
    public void replaceFeatureBookkeeping(
            Map<String, ?> resources,
            Map<String, ? extends Map<String, ?>> usage
    ) {
        LinkedHashMap<String, Object> resourceCopy = copyFeatureResources(resources);
        LinkedHashMap<String, Map<String, Object>> usageCopy = copyFeatureUsage(usage);
        featureResources.clear();
        featureResources.putAll(resourceCopy);
        featureUsage.clear();
        featureUsage.putAll(usageCopy);
    }

    /**
     * Spend AP atomically. Python consumes temporary grants first in insertion order so
     * later expiry cannot subtract already-spent temporary AP a second time.
     */
    public boolean spendAp(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("AP spend must be positive");
        }
        if (ap < amount) return false;

        int remainingSpend = amount;
        ArrayList<TemporaryApGrant> updatedTemporary = new ArrayList<>();
        for (TemporaryApGrant grant : temporaryAp) {
            if (remainingSpend <= 0) {
                updatedTemporary.add(grant);
                continue;
            }
            int used = Math.min(grant.amount(), remainingSpend);
            remainingSpend -= used;
            int leftover = grant.amount() - used;
            if (leftover > 0) {
                updatedTemporary.add(grant.withAmount(leftover));
            }
        }
        temporaryAp.clear();
        temporaryAp.addAll(updatedTemporary);
        ap -= amount;
        return true;
    }

    public void restoreAp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("AP restore cannot be negative");
        }
        ap += amount;
    }

    private static LinkedHashMap<String, Object> copyFeatureResources(Map<String, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (source != null) result.putAll(source);
        return result;
    }

    private static LinkedHashMap<String, Map<String, Object>> copyFeatureUsage(
            Map<String, ? extends Map<String, ?>> source
    ) {
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (source == null) return result;
        for (Map.Entry<String, ? extends Map<String, ?>> entry : source.entrySet()) {
            LinkedHashMap<String, Object> info = new LinkedHashMap<>();
            if (entry.getValue() != null) info.putAll(entry.getValue());
            result.put(entry.getKey(), Collections.unmodifiableMap(info));
        }
        return result;
    }

    private static Map<String, Map<String, Object>> immutableFeatureUsage(
            Map<String, ? extends Map<String, ?>> source
    ) {
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends Map<String, ?>> entry : source.entrySet()) {
            LinkedHashMap<String, Object> info = new LinkedHashMap<>();
            if (entry.getValue() != null) info.putAll(entry.getValue());
            result.put(entry.getKey(), Collections.unmodifiableMap(info));
        }
        return Collections.unmodifiableMap(result);
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
