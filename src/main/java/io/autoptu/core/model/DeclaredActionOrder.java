package io.autoptu.core.model;

/** Minimal deterministic metadata used to order actions declared before resolution. */
public record DeclaredActionOrder(
        String actorId,
        int total,
        int roll,
        int speed
) {
    public DeclaredActionOrder {
        actorId = actorId == null ? "" : actorId;
    }
}
