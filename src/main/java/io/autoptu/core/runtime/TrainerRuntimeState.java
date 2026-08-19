package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Mutable server-owned trainer state shared by combatants controlled by one trainer.
 *
 * Trainer Feature ownership and AP are PTU rule state. Minecraft/Cobblemon may render
 * them, but adapters must not supply either value while a phase hook is resolving.
 */
public final class TrainerRuntimeState {
    private final String trainerId;
    private final LinkedHashMap<String, String> featuresByNormalizedName = new LinkedHashMap<>();
    private int ap;

    public TrainerRuntimeState(String trainerId, Collection<String> trainerFeatures, int ap) {
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
        this.ap = ap;
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

    public int ap() {
        return ap;
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
