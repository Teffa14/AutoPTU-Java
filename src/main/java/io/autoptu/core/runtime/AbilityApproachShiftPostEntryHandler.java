package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable post-entry handler for abilities whose effect is a legal approach Shift toward the
 * released combatant.
 *
 * <p>The handler owns only the family-level composition: resolve and apply the legal Shift through
 * {@link AbilityApproachShiftEffectExecutor}, add the configured temporary marker, then materialize
 * the semantic ability event from server-authoritative state. Individual abilities are data passed
 * to this handler rather than separate rule classes.</p>
 */
public final class AbilityApproachShiftPostEntryHandler
        implements CombatantSwitchPostEntryDispatcher.StageHandler {
    private final String abilityName;
    private final String temporaryEffectName;
    private final String description;

    public AbilityApproachShiftPostEntryHandler(
            String abilityName,
            String temporaryEffectName,
            String description
    ) {
        if (abilityName == null || abilityName.isBlank()) {
            throw new IllegalArgumentException("abilityName is required");
        }
        if (temporaryEffectName == null || temporaryEffectName.isBlank()) {
            throw new IllegalArgumentException("temporaryEffectName is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        this.abilityName = abilityName.strip();
        this.temporaryEffectName = temporaryEffectName.strip();
        this.description = description.strip();
    }

    @Override
    public List<? extends BattleEvent> handle(CombatantSwitchPostEntryDispatcher.DispatchContext context) {
        if (context == null) throw new IllegalArgumentException("post-entry context is required");

        List<AbilityApproachShiftEffectExecutor.EffectResult> effects =
                AbilityApproachShiftEffectExecutor.execute(
                        context.state(),
                        abilityName,
                        context.replacementId(),
                        temporaryEffectName
                );
        ArrayList<AbilityEvent> events = new ArrayList<>();
        for (AbilityApproachShiftEffectExecutor.EffectResult effect : effects) {
            int actorHp = context.state().requireCombatant(effect.actorId()).hp();
            events.add(effect.toAbilityEvent(
                    context.phase(),
                    context.round(),
                    description,
                    actorHp
            ));
        }
        return List.copyOf(events);
    }
}
