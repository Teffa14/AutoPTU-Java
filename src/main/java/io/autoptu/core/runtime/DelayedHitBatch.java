package io.autoptu.core.runtime;

import java.util.List;

/** Ordered delayed hits removed for resolution plus the queue entries still pending. */
public record DelayedHitBatch(List<DelayedHitEntry> due, List<DelayedHitEntry> remaining) {
    public DelayedHitBatch {
        due = List.copyOf(due == null ? List.of() : due);
        remaining = List.copyOf(remaining == null ? List.of() : remaining);
    }
}
