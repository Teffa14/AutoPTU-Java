package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.List;
import java.util.Objects;

/**
 * Runtime-only composition boundary that preserves move-special END_ACTION inputs while later
 * target stages add their already-resolved semantic events.
 *
 * <p>The ordering mirrors {@link BattleRuntime}: pre-resolution, move-special PRE_DAMAGE,
 * pre-damage reactions, ordinary post-damage hooks, the committed/post-move-special outcome,
 * then post-hit forced movement. The adapter-facing value remains {@link AppliedActionResult};
 * this helper only keeps the package-private snapshot and applied-damage transport attached until
 * the action-wide finalization boundary consumes it.</p>
 */
final class RuntimeMoveSpecialTargetComposition {
    private RuntimeMoveSpecialTargetComposition() {}

    static MoveSpecialTargetResult compose(
            RuntimeMoveSpecialPostDamageApplication.Result postDamageResult,
            List<? extends BattleEvent> forcedMovementEvents,
            List<? extends BattleEvent> postDamageEvents,
            List<? extends BattleEvent> preDamageReactionEvents,
            List<? extends BattleEvent> moveSpecialPreDamageEvents,
            List<? extends BattleEvent> preResolutionEvents
    ) {
        Objects.requireNonNull(postDamageResult, "postDamageResult");
        return postDamageResult.targetResult()
                .appendEvents(forcedMovementEvents)
                .prependEvents(postDamageEvents)
                .prependEvents(preDamageReactionEvents)
                .prependEvents(moveSpecialPreDamageEvents)
                .prependEvents(preResolutionEvents);
    }
}
