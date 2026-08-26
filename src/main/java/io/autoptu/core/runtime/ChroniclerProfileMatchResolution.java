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

    static boolean matches(ChroniclerProfileMetadata metadata, String targetId, TargetProfile target) {
        if (targetId == null) return false;
        ChroniclerProfileMetadata resolvedMetadata = metadata == null
                ? ChroniclerProfileMetadata.empty()
                : metadata;
        if (!resolvedMetadata.hasArchive("profile")) return false;

        Set<String> recordKeys = new LinkedHashSet<>();
        for (String value : resolvedMetadata.records("profile")) {
            String key = normalize(value);
            if (!key.isEmpty()) recordKeys.add(key);
        }
        if (recordKeys.isEmpty()) return false;
        if (target == null) return false;

        String name = normalize(target.name());
        String species = normalize(target.species());
        String controllerTrainerName = normalize(target.controllerTrainerName());
        return (!name.isEmpty() && recordKeys.contains(name))
                || (!species.isEmpty() && recordKeys.contains(species))
                || (!controllerTrainerName.isEmpty() && recordKeys.contains(controllerTrainerName));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    record TargetProfile(String name, String species, String controllerTrainerName) {
    }
}
