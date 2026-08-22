package io.autoptu.core.runtime;

/** Immutable server-owned Trainer AP grant with Python-compatible round expiry metadata. */
public record TemporaryApGrant(int amount, int expiresRound, String source) {
    public TemporaryApGrant {
        if (amount <= 0) {
            throw new IllegalArgumentException("temporary AP amount must be positive");
        }
        source = source == null ? "" : source.strip();
    }

    public TemporaryApGrant withAmount(int nextAmount) {
        return new TemporaryApGrant(nextAmount, expiresRound, source);
    }
}
