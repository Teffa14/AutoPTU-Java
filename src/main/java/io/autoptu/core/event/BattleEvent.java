package io.autoptu.core.event;

/**
 * Headless battle event consumed by adapters and renderers.
 *
 * Minecraft must render these events without recalculating PTU rules.
 */
public sealed interface BattleEvent permits AbilityEvent, MoveResolvedEvent, ShiftResolvedEvent, StatusSkipEvent, TrainerFeatureEvent, RuleEffectEvent, FieldEffectEndedEvent, TerrainHazardEvent, PhaseChangedEvent, TurnStartedEvent, BorrowMoveEndedEvent, TurnEndedEvent {
    BattleEventKind kind();

    String stableKey();
}
