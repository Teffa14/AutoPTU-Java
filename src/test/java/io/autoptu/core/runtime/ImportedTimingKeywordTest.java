package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportedTimingKeywordTest {
    @Test
    void parsesInterruptFamilyAndRank() {
        assertEquals(
                new ImportedTimingKeyword(ImportedTimingKeyword.Family.INTERRUPT, 1),
                ImportedTimingKeyword.parse("interrupt-1").orElseThrow()
        );
        assertEquals(
                new ImportedTimingKeyword(ImportedTimingKeyword.Family.INTERRUPT, 2),
                ImportedTimingKeyword.parse("Interrupt 2").orElseThrow()
        );
    }

    @Test
    void parsesPriorityFamilyAndRankWithoutAssigningSemanticTiming() {
        assertEquals(
                new ImportedTimingKeyword(ImportedTimingKeyword.Family.PRIORITY, 2),
                ImportedTimingKeyword.parse("priority-2").orElseThrow()
        );
        assertEquals(
                new ImportedTimingKeyword(ImportedTimingKeyword.Family.PRIORITY, 20),
                ImportedTimingKeyword.parse("Priority_20").orElseThrow()
        );
    }

    @Test
    void unrelatedKeywordRemainsUnresolved() {
        assertTrue(ImportedTimingKeyword.parse("contact").isEmpty());
    }

    @Test
    void rejectsNonPositiveRankWhenConstructedDirectly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImportedTimingKeyword(ImportedTimingKeyword.Family.INTERRUPT, 0)
        );
    }
}
