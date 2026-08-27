package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChroniclerRuntimeOwnershipTest {
    @Test
    void canonicalTrainerAndCombatantIdentityFeedProfileMatching() {
        ChroniclerProfileMetadata metadata = new ChroniclerProfileMetadata(
                List.of("Profile Album"),
                Map.of("profile", List.of("Pikachu"))
        );
        TrainerRuntimeState trainer = new TrainerRuntimeState(
                "trainer-1",
                List.of("Chronicler"),
                0,
                0,
                Map.of(),
                null,
                "",
                Map.of(),
                Map.of(),
                "Misty",
                metadata
        );
        RuntimeCombatantState target = combatant(
                "target-1",
                new CombatantProfileIdentity("Sparky", "Pikachu")
        );

        assertEquals("Misty", trainer.trainerName());
        assertEquals(metadata, trainer.chroniclerProfileMetadata());
        assertEquals("Sparky", target.profileIdentity().name());
        assertEquals("Pikachu", target.profileIdentity().species());
        assertTrue(ChroniclerProfileMatchResolution.matches(
                trainer.chroniclerProfileMetadata(),
                new ChroniclerProfileMatchResolution.TargetProfile(
                        target.profileIdentity().name(),
                        target.profileIdentity().species(),
                        trainer.trainerName()
                )
        ));
    }

    @Test
    void legacyConstructorsFailClosedWithoutInventingProfileIdentityOrArchives() {
        RuntimeCombatantState target = new RuntimeCombatantState(
                "legacy-mon",
                MovementProfile.walking(new GridCoord(1, 1), 5),
                10,
                10,
                new ActionBudget()
        );
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer-legacy", List.of(), 0);

        assertEquals("", target.profileIdentity().name());
        assertEquals("", target.profileIdentity().species());
        assertEquals("trainer-legacy", trainer.trainerName());
        assertEquals(ChroniclerProfileMetadata.empty(), trainer.chroniclerProfileMetadata());
        assertFalse(ChroniclerProfileMatchResolution.matches(
                trainer.chroniclerProfileMetadata(),
                new ChroniclerProfileMatchResolution.TargetProfile(
                        target.profileIdentity().name(),
                        target.profileIdentity().species(),
                        trainer.trainerName()
                )
        ));
    }

    @Test
    void trainerNameIsAnIndependentProfileCandidate() {
        ChroniclerProfileMetadata metadata = new ChroniclerProfileMetadata(
                List.of("profile"),
                Map.of("profile", List.of("Professor Oak"))
        );
        TrainerRuntimeState trainer = new TrainerRuntimeState(
                "trainer-oak",
                List.of("Chronicler"),
                0,
                0,
                Map.of(),
                null,
                "",
                Map.of(),
                Map.of(),
                "Professor Oak",
                metadata
        );
        RuntimeCombatantState target = combatant(
                "target-2",
                new CombatantProfileIdentity("Buddy", "Bulbasaur")
        );

        assertTrue(ChroniclerProfileMatchResolution.matches(
                trainer.chroniclerProfileMetadata(),
                new ChroniclerProfileMatchResolution.TargetProfile(
                        target.profileIdentity().name(),
                        target.profileIdentity().species(),
                        trainer.trainerName()
                )
        ));
    }

    private static RuntimeCombatantState combatant(String id, CombatantProfileIdentity identity) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 5),
                10,
                10,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                identity
        );
    }
}
