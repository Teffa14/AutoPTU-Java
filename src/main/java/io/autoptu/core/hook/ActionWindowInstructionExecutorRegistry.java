package io.autoptu.core.hook;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Server-authoritative dispatcher for committed action-window instructions.
 *
 * <p>The registry is keyed by the committed action key so reaction families can share
 * the same commit/execution boundary without one dispatcher class per move, ability,
 * item, Feature, status, or field effect.</p>
 */
public final class ActionWindowInstructionExecutorRegistry {
    private final Map<String, ActionWindowInstructionHandler> handlers = new LinkedHashMap<>();

    public ActionWindowInstructionExecutorRegistry register(
            String actionKey,
            ActionWindowInstructionHandler handler
    ) {
        String key = normalize(actionKey);
        if (handler == null) {
            throw new IllegalArgumentException("handler is required");
        }
        if (handlers.putIfAbsent(key, handler) != null) {
            throw new IllegalArgumentException("handler already registered for action key: " + actionKey);
        }
        return this;
    }

    public boolean canExecute(CommittedActionWindowInstruction instruction) {
        requireInstruction(instruction);
        return handlers.containsKey(normalize(instruction.actionKey()));
    }

    public ActionWindowExecutionResult execute(CommittedActionWindowInstruction instruction) {
        requireInstruction(instruction);
        ActionWindowInstructionHandler handler = handlers.get(normalize(instruction.actionKey()));
        if (handler == null) {
            throw new IllegalStateException(
                    "no authoritative handler registered for committed action key: " + instruction.actionKey());
        }
        ActionWindowExecutionResult result = handler.execute(instruction);
        if (result == null) {
            throw new IllegalStateException(
                    "authoritative handler returned no execution result for committed action key: "
                            + instruction.actionKey());
        }
        return result;
    }

    private static void requireInstruction(CommittedActionWindowInstruction instruction) {
        if (instruction == null) {
            throw new IllegalArgumentException("committed instruction is required");
        }
    }

    private static String normalize(String actionKey) {
        if (actionKey == null || actionKey.isBlank()) {
            throw new IllegalArgumentException("action key is required");
        }
        return actionKey.strip().toLowerCase(Locale.ROOT);
    }
}
