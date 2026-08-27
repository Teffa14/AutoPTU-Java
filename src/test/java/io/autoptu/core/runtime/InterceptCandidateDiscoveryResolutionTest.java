package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptCandidateDiscoveryResolutionTest {
    @Test
    void activeNoInterceptSuppressesDiscoveryAfterCleaningExpiredEntries() {
        InterceptCandidateDiscoveryResolution.Result result = InterceptCandidateDiscoveryResolution.resolve(
                new InterceptCandidateDiscoveryResolution.Input(
                        "target",
                        "melee",
                        4,
                        List.of(
                                new InterceptCandidateDiscoveryResolution.NoInterceptEntry(3),
                                new InterceptCandidateDiscoveryResolution.NoInterceptEntry(4)
                        ),
                        List.of(ready("interceptor", "target", "melee"))
                )
        );

        assertTrue(result.suppressedByNoIntercept());
        assertEquals(1, result.attackerNoInterceptRemovalCount());
        assertEquals(List.of(), result.candidates());
    }

    @Test
    void discoversWeaponizeThenSkipsOtherSourcesForSameCombatant() {
        InterceptCandidateDiscoveryResolution.CombatantInput weaponize = new InterceptCandidateDiscoveryResolution.CombatantInput(
                "weapon",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                List.of(new InterceptCandidateDiscoveryResolution.ReadyEntry("target", "melee", "Prepared")),
                List.of(new InterceptCandidateDiscoveryResolution.SentinelEntry(null)),
                true,
                0
        );

        InterceptCandidateDiscoveryResolution.Result result = resolve(List.of(weaponize));

        assertEquals(List.of(
                new InterceptCandidateDiscoveryResolution.Candidate("weapon", "Weaponize", false)
        ), result.candidates());
    }

    @Test
    void preservesPreparedEntryMultiplicityAndFiltersAllyAndKind() {
        InterceptCandidateDiscoveryResolution.CombatantInput combatant = new InterceptCandidateDiscoveryResolution.CombatantInput(
                "prepared",
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                List.of(
                        new InterceptCandidateDiscoveryResolution.ReadyEntry("target", "melee", "First"),
                        new InterceptCandidateDiscoveryResolution.ReadyEntry("other", "melee", "Wrong Ally"),
                        new InterceptCandidateDiscoveryResolution.ReadyEntry("target", "ranged", "Wrong Kind"),
                        new InterceptCandidateDiscoveryResolution.ReadyEntry("target", "melee", "Second")
                ),
                List.of(),
                true,
                0
        );

        InterceptCandidateDiscoveryResolution.Result result = resolve(List.of(combatant));

        assertEquals(List.of(
                new InterceptCandidateDiscoveryResolution.Candidate("prepared", "First", false),
                new InterceptCandidateDiscoveryResolution.Candidate("prepared", "Second", false)
        ), result.candidates());
    }

    @Test
    void sentinelExpiresStrictlyAfterRoundAndAcceptsExtraShift() {
        InterceptCandidateDiscoveryResolution.CombatantInput combatant = new InterceptCandidateDiscoveryResolution.CombatantInput(
                "sentinel",
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                List.of(),
                List.of(
                        new InterceptCandidateDiscoveryResolution.SentinelEntry(3),
                        new InterceptCandidateDiscoveryResolution.SentinelEntry(4)
                ),
                false,
                1
        );

        InterceptCandidateDiscoveryResolution.Result result = InterceptCandidateDiscoveryResolution.resolve(
                new InterceptCandidateDiscoveryResolution.Input("target", "ranged", 4, List.of(), List.of(combatant))
        );

        assertFalse(result.suppressedByNoIntercept());
        assertEquals(1, result.sentinelRemovalCountByCombatant().get("sentinel"));
        assertEquals(List.of(
                new InterceptCandidateDiscoveryResolution.Candidate("sentinel", "Sentinel Stance", true)
        ), result.candidates());
    }

    @Test
    void filtersTargetFaintedEnemyAndIneligibleCandidates() {
        InterceptCandidateDiscoveryResolution.Result result = resolve(List.of(
                ready("target", "target", "melee"),
                withFlags("fainted", false, true, true, true),
                withFlags("enemy", true, false, true, true),
                withFlags("blocked", true, true, false, true),
                withFlags("loyalty", true, true, true, false),
                ready("valid", "target", "melee")
        ));

        assertEquals(List.of(
                new InterceptCandidateDiscoveryResolution.Candidate("valid", "Intercept", false)
        ), result.candidates());
    }

    private static InterceptCandidateDiscoveryResolution.Result resolve(
            List<InterceptCandidateDiscoveryResolution.CombatantInput> combatants
    ) {
        return InterceptCandidateDiscoveryResolution.resolve(
                new InterceptCandidateDiscoveryResolution.Input("target", "melee", 4, List.of(), combatants)
        );
    }

    private static InterceptCandidateDiscoveryResolution.CombatantInput ready(String id, String ally, String kind) {
        return new InterceptCandidateDiscoveryResolution.CombatantInput(
                id, true, true, false, false, false, true, true,
                List.of(new InterceptCandidateDiscoveryResolution.ReadyEntry(ally, kind, "Intercept")),
                List.of(), true, 0
        );
    }

    private static InterceptCandidateDiscoveryResolution.CombatantInput withFlags(
            String id,
            boolean living,
            boolean sameTeam,
            boolean eligible,
            boolean loyalty
    ) {
        return new InterceptCandidateDiscoveryResolution.CombatantInput(
                id, living, sameTeam, false, false, false, eligible, loyalty,
                List.of(new InterceptCandidateDiscoveryResolution.ReadyEntry("target", "melee", "Intercept")),
                List.of(), true, 0
        );
    }
}
