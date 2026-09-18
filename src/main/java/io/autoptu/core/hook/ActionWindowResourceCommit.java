package io.autoptu.core.hook;

import io.autoptu.core.model.ActionType;

/** Resource requirement frozen from the reaction source contract at commit time. */
public record ActionWindowResourceCommit(ActionType actionType, String detail) {
    public ActionWindowResourceCommit {
        if (actionType == null) {
            throw new IllegalArgumentException("action type is required");
        }
        detail = detail == null ? "" : detail.strip();
    }
}
