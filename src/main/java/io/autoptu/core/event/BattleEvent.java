package io.autoptu.core.event;

/**
 * Headless battle event consumed by adapters and renderers.
 *
 * Minecraft must render these events without recalculating PTU rules.
 */
public sealed interface BattleEvent permits MoveResolvedEvent, ShiftResolvedEvent, StatusSkipEvent, TrainerFeatureEvent {
    BattleEventKind kind();

    String stableKey();
}
