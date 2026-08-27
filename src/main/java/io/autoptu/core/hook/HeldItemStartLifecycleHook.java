package io.autoptu.core.hook;

import io.autoptu.core.runtime.HeldItemRuleCatalog;
import io.autoptu.core.runtime.HeldItemStartRuleProfile;
import io.autoptu.core.runtime.HeldItemStartTemporaryEffectResolution;
import io.autoptu.core.runtime.HeldItemState;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.Objects;

/**
 * Applies the generic server-owned held-item START rule families for one combatant.
 *
 * <p>Equipped item identity comes from BattleRuntimeState and PTU rule metadata comes
 * from HeldItemRuleCatalog. Adapters do not provide parsed effect maps or temporary
 * effects to this hook.</p>
 */
public final class HeldItemStartLifecycleHook implements LifecycleHook {
    private final HeldItemRuleCatalog catalog;

    public HeldItemStartLifecycleHook(HeldItemRuleCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "held-item rule catalog");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        if (context.actorId().isBlank()) {
            return LifecycleHookResult.empty();
        }

        RuntimeCombatantState combatant = context.state().requireCombatant(context.actorId());
        for (HeldItemState item : context.state().heldItems(context.actorId())) {
            HeldItemStartRuleProfile profile = catalog.find(item).orElse(null);
            if (profile == null) {
                continue;
            }
            HeldItemStartTemporaryEffectResolution.apply(
                    combatant.temporaryEffects(),
                    profile.forHeldItem(item)
            );
        }
        return LifecycleHookResult.empty();
    }
}
