package io.autoptu.core.hook;

import java.util.List;

@FunctionalInterface
public interface ActionWindowHook {
    List<ActionWindowCandidate> candidates(ActionWindowContext context);
}
