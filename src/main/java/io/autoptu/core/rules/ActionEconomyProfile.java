package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;

import java.util.EnumMap;

/**
 * Explicit action-resource semantics boundary.
 *
 * <p>The port defaults to the pinned Python oracle semantics. Normative PTU/Kairos
 * behavior must be added as an explicit profile rather than silently changing the
 * compatibility model used by the migration parity gates.</p>
 */
public enum ActionEconomyProfile {
    PYTHON_ORACLE_COMPATIBILITY {
        @Override
        boolean hasBaseActionAvailable(EnumMap<ActionType, String> consumed, ActionType actionType) {
            return !consumed.containsKey(actionType);
        }

        @Override
        void markBaseAction(EnumMap<ActionType, String> consumed, ActionType actionType, String detail) {
            consumed.put(actionType, detail == null ? "" : detail);
        }
    };

    abstract boolean hasBaseActionAvailable(EnumMap<ActionType, String> consumed, ActionType actionType);

    abstract void markBaseAction(EnumMap<ActionType, String> consumed, ActionType actionType, String detail);
}
