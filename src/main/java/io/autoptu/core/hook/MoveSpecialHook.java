package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

@FunctionalInterface
public interface MoveSpecialHook {
    List<BattleEvent> apply(MoveSpecialHookContext context);
}