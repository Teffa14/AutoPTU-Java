package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.MoveSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CanonicalMoveSetRemovalTest {
    private static final MoveSpec SPEC = new MoveSpec("1 Target", "Melee", 1, 1, null, null, "Melee");

    @Test
    void removesEveryMatchingMoveAndPreservesStableOrder() {
        MoveOption tackle = MoveOption.standard("Tackle", SPEC);
        MoveOption confusion = MoveOption.standard("Confusion", SPEC);
        MoveOption psybeam = MoveOption.standard("Psybeam", SPEC);
        MoveOption growl = MoveOption.standard("Growl", SPEC);

        CanonicalMoveSetRemoval.Result result = CanonicalMoveSetRemoval.resolve(
                List.of(tackle, confusion, psybeam, growl),
                List.of("psybeam", "confusion")
        );

        assertEquals(List.of(tackle, growl), result.kept());
        assertEquals(List.of(confusion, psybeam), result.removed());
    }

    @Test
    void matchesPythonTrimAndCaseFoldSemanticsAndIgnoresBlankKeys() {
        MoveOption confusion = MoveOption.standard("Confusion", SPEC);
        MoveOption tackle = MoveOption.standard("Tackle", SPEC);

        CanonicalMoveSetRemoval.Result result = CanonicalMoveSetRemoval.resolve(
                List.of(confusion, tackle),
                List.of("  CONFUSION  ", "", "   ")
        );

        assertEquals(List.of(tackle), result.kept());
        assertEquals(List.of(confusion), result.removed());
    }

    @Test
    void duplicateRemovalKeysDoNotDuplicateRemovedMoves() {
        MoveOption confusion = MoveOption.standard("Confusion", SPEC);

        CanonicalMoveSetRemoval.Result result = CanonicalMoveSetRemoval.resolve(
                List.of(confusion),
                List.of("Confusion", " confusion ", "CONFUSION")
        );

        assertEquals(List.of(), result.kept());
        assertEquals(List.of(confusion), result.removed());
    }

    @Test
    void absentRemovalKeysLeaveCanonicalMovesUntouched() {
        MoveOption tackle = MoveOption.standard("Tackle", SPEC);
        MoveOption growl = MoveOption.standard("Growl", SPEC);

        CanonicalMoveSetRemoval.Result result = CanonicalMoveSetRemoval.resolve(
                List.of(tackle, growl),
                List.of("Psybeam")
        );

        assertEquals(List.of(tackle, growl), result.kept());
        assertEquals(List.of(), result.removed());
    }

    @Test
    void resultSnapshotsAreImmutable() {
        MoveOption tackle = MoveOption.standard("Tackle", SPEC);
        CanonicalMoveSetRemoval.Result result = CanonicalMoveSetRemoval.resolve(List.of(tackle), List.of());

        assertThrows(UnsupportedOperationException.class, () -> result.kept().add(tackle));
        assertThrows(UnsupportedOperationException.class, () -> result.removed().add(tackle));
    }
}
