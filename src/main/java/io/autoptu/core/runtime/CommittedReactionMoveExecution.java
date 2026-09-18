package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.CommittedActionWindowInstruction;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.random.PythonRandom;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Authoritative ordinary-move inputs for one committed reaction attack.
 *
 * <p>Trigger legality and reaction action payment are already frozen by the committed
 * instruction. This record carries the remaining server-owned resolution inputs so the
 * reaction can enter the ordinary move pipeline without an adapter supplying PTU rules.</p>
 */
public record CommittedReactionMoveExecution(
        CommittedReactionMoveBinding binding,
        String actorSize,
        String targetSize,
        Set<GridCoord> lineOfSightBlockers,
        String source,
        PythonRandom rng,
        MoveResolutionInput input,
        List<? extends BattleEvent> preResolutionEvents,
        MoveSpecialHookRegistry moveSpecialHookRegistry,
        MoveCombatProfile effectiveMetadata,
        BattleRuntimeDependencies dependencies
) {
    public CommittedReactionMoveExecution {
        Objects.requireNonNull(binding, "binding");
        actorSize = actorSize == null ? "" : actorSize;
        targetSize = targetSize == null ? "" : targetSize;
        lineOfSightBlockers = lineOfSightBlockers == null ? Set.of() : Set.copyOf(lineOfSightBlockers);
        source = source == null ? "" : source;
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(input, "input");
        preResolutionEvents = preResolutionEvents == null ? List.of() : List.copyOf(preResolutionEvents);
        Objects.requireNonNull(moveSpecialHookRegistry, "moveSpecialHookRegistry");
        Objects.requireNonNull(effectiveMetadata, "effectiveMetadata");
        Objects.requireNonNull(dependencies, "dependencies");
    }

    public void requireCommittedParticipants(CommittedActionWindowInstruction instruction) {
        Objects.requireNonNull(instruction, "instruction");
        if (!instruction.hasTriggeringCombatant()) {
            throw new IllegalArgumentException("committed reaction attack requires a triggering combatant");
        }
        binding.requireParticipants(instruction.reactorId(), instruction.triggeringCombatantId());
    }
}
