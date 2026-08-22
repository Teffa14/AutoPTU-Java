package io.autoptu.core.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Language-neutral Trainer-target selection for generic Trainer Feature effects.
 *
 * Mirrors Python TrainerFeatureDispatcher._resolve_trainer_targets(). The ordered
 * trainer id list is authoritative battle state; Minecraft/Cobblemon may request
 * an effect but does not choose which Trainer identities satisfy its scope.
 */
public final class TrainerFeatureTrainerTargetResolution {
    private TrainerFeatureTrainerTargetResolution() {}

    public static List<String> resolve(
            String sourceTrainerId,
            List<String> orderedTrainerIds,
            Map<String, ?> effect
    ) {
        if (sourceTrainerId == null || sourceTrainerId.isBlank()) {
            throw new IllegalArgumentException("sourceTrainerId is required");
        }
        Objects.requireNonNull(orderedTrainerIds, "orderedTrainerIds");
        Map<String, ?> safeEffect = effect == null ? Map.of() : effect;

        ArrayList<String> trainers = new ArrayList<>();
        for (String trainerId : orderedTrainerIds) {
            if (trainerId == null || trainerId.isBlank()) continue;
            String canonical = trainerId.strip();
            if (!trainers.contains(canonical)) trainers.add(canonical);
        }
        String source = sourceTrainerId.strip();
        if (!trainers.contains(source)) {
            throw new IllegalArgumentException("source Trainer is not present in authoritative trainer state: " + source);
        }

        Object rawSelector = pythonTruthy(safeEffect.get("trainer_scope"))
                ? safeEffect.get("trainer_scope")
                : pythonTruthy(safeEffect.get("trainer")) ? safeEffect.get("trainer") : "self";
        String selector = normalize(rawSelector);

        if (selector.equals("self")
                || selector.equals("ally")
                || selector.equals("allies")
                || selector.equals("self_team")
                || selector.equals("own")) {
            return List.of(source);
        }
        if (selector.equals("enemy") || selector.equals("foe") || selector.equals("opponent")) {
            return trainers.stream().filter(id -> !id.equals(source)).toList();
        }
        if (selector.equals("all") || selector.equals("any")) {
            return List.copyOf(trainers);
        }
        if (trainers.contains(selector)) {
            return List.of(selector);
        }
        return List.of(source);
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }

    /** Python truthiness for the scalar selector values accepted by JSON/YAML Feature data. */
    private static boolean pythonTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0.0;
        if (value instanceof CharSequence chars) return chars.length() > 0;
        if (value instanceof java.util.Collection<?> collection) return !collection.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return true;
    }
}
