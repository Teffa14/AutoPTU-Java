package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionOnlyMoveClassifierTest {
    @Test
    void classifiesInterruptAndReactionActivations() {
        assertTrue(ReactionOnlyMoveClassifier.isReactionOnly("Interrupt", "", "", "", ""));
        assertTrue(ReactionOnlyMoveClassifier.isReactionOnly(" reaction ", "", "", "", ""));
    }

    @Test
    void classifiesTriggerAndReactionTextFromLocalOrCanonicalMoveData() {
        assertTrue(ReactionOnlyMoveClassifier.isReactionOnly("standard", "", "Trigger: when hit", "", ""));
        assertTrue(ReactionOnlyMoveClassifier.isReactionOnly("standard", "", "", "", "This Move is a Reaction."));
    }

    @Test
    void canonicalTextCanRestoreReactionClassificationMissingFromLocalProjection() {
        assertTrue(ReactionOnlyMoveClassifier.isReactionOnly("", "Melee, 1 Target", "", "Melee, 1 Target", "Trigger: after a foe attacks"));
    }

    @Test
    void ordinaryMoveRemainsProactive() {
        assertFalse(ReactionOnlyMoveClassifier.isReactionOnly("standard", "Melee, 1 Target", "Deal normal damage.", "", ""));
        assertFalse(ReactionOnlyMoveClassifier.isReactionOnly(null, null, null, null, null));
    }
}
