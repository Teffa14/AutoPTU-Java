package io.autoptu.core.runtime;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Server-side request emitted by field lifecycle when an expiring field effect removes statuses globally. */
public record FieldStatusCleanupRequest(Set<String> statusNames) {
    public FieldStatusCleanupRequest {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (statusNames != null) {
            for (String status : statusNames) {
                if (status == null || status.isBlank()) continue;
                normalized.add(status.strip().toLowerCase(Locale.ROOT));
            }
        }
        if (normalized.isEmpty()) throw new IllegalArgumentException("statusNames are required");
        statusNames = Set.copyOf(normalized);
    }
}
