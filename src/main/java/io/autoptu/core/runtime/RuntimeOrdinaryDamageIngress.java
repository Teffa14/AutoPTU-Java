package io.autoptu.core.runtime;

/**
 * Server-owned ordinary-damage ingress from pending damage into temporary HP and normal HP.
 *
 * <p>This boundary deliberately owns only the state transition already frozen against the
 * Python oracle: clamp pending damage, absorb with temporary HP, then apply the remainder to
 * canonical HP. Substitute, prevention/reactions, injuries, faint prevention, history rotation,
 * semantic events, and source attribution remain separate pipeline stages and must compose
 * around this boundary rather than being duplicated by lifecycle/status/content hooks.</p>
 */
public final class RuntimeOrdinaryDamageIngress {
    private RuntimeOrdinaryDamageIngress() {}

    public static Result apply(BattleRuntimeState state, String combatantId, int incomingDamage) {
        if (state == null) {
            throw new IllegalArgumentException("battle state is required");
        }
        RuntimeCombatantState combatant = state.requireCombatant(combatantId);

        int hpBefore = combatant.hp();
        int tempHpBefore = combatant.tempHp();
        TemporaryHpDamageAbsorption.Result absorption =
                TemporaryHpDamageAbsorption.resolve(tempHpBefore, incomingDamage);

        int hpDamage = Math.min(hpBefore, absorption.remainingDamage());
        int hpAfter = hpBefore - hpDamage;

        combatant.replaceTempHpFromRuntime(absorption.remainingTemporaryHp());
        combatant.setHp(hpAfter);

        return new Result(
                absorption.pendingDamage(),
                absorption.absorbedDamage(),
                hpDamage,
                hpBefore,
                hpAfter,
                tempHpBefore,
                absorption.remainingTemporaryHp()
        );
    }

    public record Result(
            int pendingDamage,
            int absorbedDamage,
            int hpDamage,
            int hpBefore,
            int hpAfter,
            int tempHpBefore,
            int tempHpAfter
    ) {
        public Result {
            if (pendingDamage < 0 || absorbedDamage < 0 || hpDamage < 0
                    || hpBefore < 0 || hpAfter < 0 || tempHpBefore < 0 || tempHpAfter < 0) {
                throw new IllegalArgumentException("damage ingress values cannot be negative");
            }
            if (hpAfter > hpBefore) {
                throw new IllegalArgumentException("ordinary damage cannot increase HP");
            }
            if (tempHpAfter > tempHpBefore) {
                throw new IllegalArgumentException("ordinary damage cannot increase temporary HP");
            }
            if (absorbedDamage != tempHpBefore - tempHpAfter) {
                throw new IllegalArgumentException("temporary HP delta must equal absorbed damage");
            }
            if (hpDamage != hpBefore - hpAfter) {
                throw new IllegalArgumentException("HP delta must equal applied HP damage");
            }
            if (absorbedDamage + hpDamage > pendingDamage) {
                throw new IllegalArgumentException("applied damage cannot exceed pending damage");
            }
        }

        /** Shared post-damage classification for lifecycle and semantic-event ordering. */
        public RuntimePostDamageOutcomeResolution.Result postDamageOutcome() {
            return RuntimePostDamageOutcomeResolution.resolve(hpBefore, hpAfter);
        }
    }
}
