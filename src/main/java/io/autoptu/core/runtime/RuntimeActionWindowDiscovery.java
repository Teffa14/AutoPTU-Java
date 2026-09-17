package io.autoptu.core.runtime;

import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.hook.ActionWindow;
import io.autoptu.core.hook.ActionWindowCandidate;
import io.autoptu.core.hook.ActionWindowContext;
import io.autoptu.core.hook.ActionWindowHookRegistry;
import io.autoptu.core.hook.ActionWindowTrigger;

import java.util.List;
import java.util.Objects;

/**
 * Bridges authoritative semantic action completion into generic reaction-window discovery.
 *
 * <p>Trigger classification remains a separate rules concern because some trigger families
 * require reactor-specific geometry or action-economy facts. Once the core has classified a
 * trigger, this boundary constructs the canonical AFTER_ACTION context and asks the shared
 * registry for ordered candidates. Adapters must consume the candidates rather than infer
 * reactions from rendered Minecraft/Cobblemon state.</p>
 */
public final class RuntimeActionWindowDiscovery {
    private RuntimeActionWindowDiscovery() {
    }

    public static List<ActionWindowCandidate> afterAction(
            ActionResolvedEvent occurrence,
            ActionWindowTrigger trigger,
            ActionWindowHookRegistry registry
    ) {
        Objects.requireNonNull(occurrence, "occurrence");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(registry, "registry");
        if (trigger == ActionWindowTrigger.UNSPECIFIED) {
            throw new IllegalArgumentException("classified trigger is required");
        }

        ActionWindowContext context = new ActionWindowContext(
                ActionWindow.AFTER_ACTION,
                occurrence.actorId(),
                occurrence.stableKey(),
                trigger
        );
        return registry.candidates(context);
    }
}
