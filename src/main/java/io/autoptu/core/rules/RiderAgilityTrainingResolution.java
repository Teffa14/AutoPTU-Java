package io.autoptu.core.rules;

import java.util.Collection;
import java.util.Map;

/**
 * Resolves the PTU Rider interaction that doubles Agility Training for a mounted pair.
 *
 * <p>The relationship is semantic battle state. Minecraft/Cobblemon adapters must not
 * decide this bonus from entity passengers or visual attachment state while rules are
 * resolving. The caller supplies the server-owned mounted-pair projection and canonical
 * Rider/Agility Training ownership.</p>
 */
public final class RiderAgilityTrainingResolution {
    private RiderAgilityTrainingResolution() {
    }

    public static boolean doubled(
            String actorId,
            Map<String, String> mountedPairs,
            Collection<String> existingActorIds,
            Collection<String> riderFeatureActorIds,
            Collection<String> agilityTrainingActorIds
    ) {
        String actorKey = clean(actorId);
        if (actorKey.isEmpty() || mountedPairs == null || mountedPairs.isEmpty()) {
            return false;
        }

        String riderId = mountedRiderId(actorKey, mountedPairs);
        if (riderId == null) {
            return false;
        }
        String mountId = clean(mountedPairs.get(riderId));
        if (mountId.isEmpty()) {
            return false;
        }

        return contains(existingActorIds, riderId)
                && contains(existingActorIds, mountId)
                && contains(riderFeatureActorIds, riderId)
                && contains(agilityTrainingActorIds, mountId);
    }

    static String mountedRiderId(String actorId, Map<String, String> mountedPairs) {
        String actorKey = clean(actorId);
        if (actorKey.isEmpty()) {
            return null;
        }
        if (mountedPairs.containsKey(actorKey)) {
            return actorKey;
        }
        for (Map.Entry<String, String> entry : mountedPairs.entrySet()) {
            if (clean(entry.getValue()).equals(actorKey)) {
                String riderId = clean(entry.getKey());
                return riderId.isEmpty() ? null : riderId;
            }
        }
        return null;
    }

    private static boolean contains(Collection<String> values, String expected) {
        if (values == null || expected == null || expected.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (clean(value).equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
