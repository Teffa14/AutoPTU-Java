package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;

import java.util.EnumMap;

/**
 * Explicit action-resource semantics boundary.
 *
 * <p>The port defaults to the pinned Python oracle semantics. Normative PTU/Kairos
 * behavior is selected explicitly so compatibility parity is never changed by a
 * rulebook-only correction.</p>
 */
public enum ActionEconomyProfile {
    PYTHON_ORACLE_COMPATIBILITY {
        @Override
        boolean hasBaseActionAvailable(EnumMap<ActionType, String> consumed, ActionType actionType) {
            return !consumed.containsKey(actionType);
        }

        @Override
        void markBaseAction(EnumMap<ActionType, String> consumed, ActionType actionType, String detail) {
            consumed.put(actionType, safeDetail(detail));
        }
    },

    /**
     * Kairos V2.1.25.1 turn-resource semantics for the bounded resource model.
     *
     * <p>Full consumes the base Standard + Shift resources. Standard may convert
     * into one additional Swift or Shift. A Standard-to-Shift conversion cannot
     * fund a second movement after the regular Shift was already used to move.</p>
     */
    KAIROS_2_1_25_1 {
        @Override
        boolean hasBaseActionAvailable(EnumMap<ActionType, String> consumed, ActionType actionType) {
            if (actionType == ActionType.FULL) {
                return !consumed.containsKey(ActionType.STANDARD)
                        && !consumed.containsKey(ActionType.SHIFT);
            }
            return !consumed.containsKey(actionType);
        }

        @Override
        void markBaseAction(EnumMap<ActionType, String> consumed, ActionType actionType, String detail) {
            String safeDetail = safeDetail(detail);
            if (actionType == ActionType.FULL) {
                consumed.put(ActionType.STANDARD, safeDetail);
                consumed.put(ActionType.SHIFT, safeDetail);
                consumed.put(ActionType.FULL, safeDetail);
                return;
            }
            consumed.put(actionType, safeDetail);
        }

        @Override
        boolean allowsStandardConversion(ActionType target) {
            return target == ActionType.SWIFT || target == ActionType.SHIFT;
        }

        @Override
        boolean blocksConvertedMovementAfterRegularMovement() {
            return true;
        }

        @Override
        boolean extraActionCanSatisfy(ActionType actionType) {
            return actionType != ActionType.FULL;
        }
    };

    abstract boolean hasBaseActionAvailable(EnumMap<ActionType, String> consumed, ActionType actionType);

    abstract void markBaseAction(EnumMap<ActionType, String> consumed, ActionType actionType, String detail);

    boolean allowsStandardConversion(ActionType target) {
        return false;
    }

    boolean blocksConvertedMovementAfterRegularMovement() {
        return false;
    }

    boolean extraActionCanSatisfy(ActionType actionType) {
        return true;
    }

    private static String safeDetail(String detail) {
        return detail == null ? "" : detail;
    }
}
