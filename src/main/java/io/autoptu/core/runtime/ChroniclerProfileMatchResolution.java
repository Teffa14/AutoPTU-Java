package io.autoptu.core.runtime;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure contract for the pinned Python BattleState._chronicler_profile_matches() helper.
 *
 * <p>Profile archives and target identity are supplied from server-owned runtime state. This
 * resolver deliberately contains no Minecraft/Cobblemon lookup.</p>
 */
final class ChroniclerProfileMatchResolution {
    private ChroniclerProfileMatchResolution() {
    }

    static boolean matches(ChroniclerProfileMetadata metadata, TargetProfile target) {
        if (target == null) return false;
        ChroniclerProfileMetadata resolvedMetadata = metadata == null
                ? ChroniclerProfileMetadata.empty()
                : metadata;
        if (!resolvedMetadata.hasArchive("profile")) return false;

        Set<String> recordKeys = new LinkedHashSet<>();
        for (String value : resolvedMetadata.records("profile")) {
            recordKeys.add(normalize(value));
        }
        if (recordKeys.isEmpty()) return false;

        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalize(target.name()));
        candidates.add(normalize(target.species()));
        if (target.controllerTrainerName() != null) {
            candidates.add(normalize(target.controllerTrainerName()));
        }
        return candidates.stream().anyMatch(candidate -> !candidate.isEmpty() && recordKeys.contains(candidate));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    record TargetProfile(String name, String species, String controllerTrainerName) {
    }
}
