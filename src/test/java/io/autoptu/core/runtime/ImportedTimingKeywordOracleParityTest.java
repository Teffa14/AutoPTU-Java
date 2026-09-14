package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportedTimingKeywordOracleParityTest {
    @Test
    void matchesPinnedPythonImportedTimingKeywordsWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_IMPORTED_TIMING_KEYWORD_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) {
            return;
        }

        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            ImportedTimingKeyword parsed = ImportedTimingKeyword.parse(parts[1]).orElseThrow();

            assertEquals(ImportedTimingKeyword.Family.valueOf(parts[2]), parsed.family(), parts[0]);
            assertEquals(Integer.parseInt(parts[3]), parsed.rank(), parts[0]);
        }
    }
}
