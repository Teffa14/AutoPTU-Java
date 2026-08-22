package io.autoptu.core.rules;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Python-parity primitive for Trainer Feature discovery and prerequisite gates.
 *
 * This is deliberately narrower than the full TrainerFeatureDispatcher. Frequency,
 * cooldowns, context conditions, resources, AP spending, usage accounting and effect
 * application remain separate contracts. Minecraft/Cobblemon must never decide these
 * eligibility gates.
 */
public final class TrainerFeaturePrerequisiteResolution {
    private TrainerFeaturePrerequisiteResolution() {}

    public record SelectedFeature(String identifier, String runtimeKind) {}

    /**
     * Mirrors TrainerFeatureDispatcher._trainer_features(): features first, then edges,
     * then trainer_class.known_features; duplicate identifiers keep the first entry.
     */
    public static List<SelectedFeature> selectFeatureIdentities(
            Collection<?> features,
            Collection<?> edges,
            Collection<?> knownFeatures
    ) {
        ArrayList<SelectedFeature> selected = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        collect(selected, seen, features, "feature");
        collect(selected, seen, edges, "edge");
        collect(selected, seen, knownFeatures, "feature");
        return List.copyOf(selected);
    }

    /** Mirrors TrainerFeatureDispatcher._feature_prerequisites_met(). */
    public static boolean prerequisitesMet(
            Map<String, ?> trainerClass,
            Map<String, ?> feature,
            Collection<String> knownFeatureIds
    ) {
        Map<String, ?> safeClass = trainerClass == null ? Map.of() : trainerClass;
        Map<String, ?> safeFeature = feature == null ? Map.of() : feature;

        String classId = normalizeToken(first(safeClass, "class_id", "id"));
        String subclassId = normalizeToken(first(safeClass, "subclass_id", "subclass"));
        int classLevel = intLike(safeClass.get("level"), 0);

        int minLevel = intLike(first(safeFeature, "min_trainer_level", "level_required"), 0);
        if (minLevel > 0 && classLevel < minLevel) return false;

        if (!matchesRequiredToken(classId, safeFeature.get("required_classes"))) return false;
        if (!matchesRequiredToken(subclassId, safeFeature.get("required_subclasses"))) return false;
        if (!hasRequiredFeatures(knownFeatureIds, safeFeature.get("required_features"))) return false;

        Object rawPrerequisites = safeFeature.get("prerequisites");
        if (rawPrerequisites instanceof Map<?, ?> prerequisites) {
            int nestedMin = intLike(firstUntyped(prerequisites, "min_trainer_level", "level"), 0);
            if (nestedMin > 0 && classLevel < nestedMin) return false;
            if (!matchesRequiredToken(classId, firstUntyped(prerequisites, "classes", "class"))) return false;
            if (!matchesRequiredToken(subclassId, firstUntyped(prerequisites, "subclasses", "subclass"))) return false;
            if (!hasRequiredFeatures(knownFeatureIds, firstUntyped(prerequisites, "features", "feature"))) return false;
        }
        return true;
    }

    public static String featureIdentifier(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            Object raw = firstUntyped(map, "feature_id", "id", "name");
            return normalizeToken(raw).replace(" ", "-").isBlank()
                    ? "feature"
                    : normalizeToken(raw).replace(" ", "-");
        }
        if (entry instanceof String value) {
            String token = value.strip();
            if (token.isEmpty()) return "";
            return normalizeToken(token).replace(" ", "-");
        }
        return "";
    }

    private static void collect(
            List<SelectedFeature> selected,
            Set<String> seen,
            Collection<?> entries,
            String runtimeKind
    ) {
        if (entries == null) return;
        for (Object entry : entries) {
            String identifier = featureIdentifier(entry);
            if (identifier.isBlank() || !seen.add(identifier)) continue;
            selected.add(new SelectedFeature(identifier, runtimeKind));
        }
    }

    private static boolean matchesRequiredToken(String actual, Object requirements) {
        List<String> required = normalizeTokens(requirements);
        return required.isEmpty() || required.contains(actual);
    }

    private static boolean hasRequiredFeatures(Collection<String> knownFeatureIds, Object requirements) {
        List<String> required = normalizeTokens(requirements);
        if (required.isEmpty()) return true;
        LinkedHashSet<String> known = new LinkedHashSet<>();
        if (knownFeatureIds != null) {
            for (String id : knownFeatureIds) {
                if (id != null && !id.isBlank()) known.add(normalizeToken(id));
            }
        }
        for (String requirement : required) {
            if (!known.contains(requirement)) return false;
        }
        return true;
    }

    private static List<String> normalizeTokens(Object value) {
        if (value == null) return List.of();
        ArrayList<String> out = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object entry : collection) addToken(out, entry);
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) addToken(out, Array.get(value, i));
        } else {
            addToken(out, value);
        }
        return List.copyOf(out);
    }

    private static void addToken(List<String> out, Object value) {
        String token = normalizeToken(value);
        if (!token.isBlank()) out.add(token);
    }

    private static String normalizeToken(Object value) {
        return value == null ? "" : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }

    private static int intLike(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        if (value instanceof Number number) return number.intValue();
        String text = String.valueOf(value).strip();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            try {
                return (int) Double.parseDouble(text);
            } catch (NumberFormatException ignoredAgain) {
                return fallback;
            }
        }
    }

    private static Object first(Map<String, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return value;
        }
        return null;
    }

    private static Object firstUntyped(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return value;
        }
        return null;
    }
}