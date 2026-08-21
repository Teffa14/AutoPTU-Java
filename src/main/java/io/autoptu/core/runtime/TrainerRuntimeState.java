package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mutable server-owned trainer state shared by combatants controlled by one trainer.
 *
 * Trainer Feature ownership, skills, AP, and initiative inputs are PTU rule state.
 * Minecraft/Cobblemon may render them, but adapters must not supply these values while
 * battle rules are resolving.
 */
public final class TrainerRuntimeState {
    private final String trainerId;
    private final LinkedHashMap<String, String> featuresByNormalizedName = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> skillRanksByNormalizedName = new LinkedHashMap<>();
    private final int initiativeModifier;
    private final Integer explicitInitiativeSpeed;
    private final String teamId;
    private int ap;

    /** Backwards-compatible trainer state with the Python default initiative modifier of zero. */
    public TrainerRuntimeState(String trainerId, Collection<String> trainerFeatures, int ap) {
        this(trainerId, trainerFeatures, ap, 0, Map.of(), null, "");
    }

    public TrainerRuntimeState(
            String trainerId,
            Collection<String> trainerFeatures,
            int ap,
            int initiativeModifier
    ) {
        this(trainerId, trainerFeatures, ap, initiativeModifier, Map.of(), null, "");
    }

    public TrainerRuntimeState(
            String trainerId,
            Collection<String> trainerFeatures,
            int ap,
            int initiativeModifier,
            Map<String, Integer> skillRanks
    ) {
        this(trainerId, trainerFeatures, ap, initiativeModifier, skillRanks, null, "");
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

    public int ap() {
        return ap;
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

    /** Spend AP atomically; returns false without mutation when insufficient. */
    public boolean spendAp(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("AP spend must be positive");
        }
        if (ap < amount) return false;
        ap -= amount;
        return true;
    }

    public void restoreAp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("AP restore cannot be negative");
        }
        ap += amount;
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
