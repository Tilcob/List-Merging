package github.tilcob.app.listmerging.service;

import com.opencsv.exceptions.CsvException;
import github.tilcob.app.listmerging.model.AggregationResult;
import github.tilcob.app.listmerging.model.HeaderDefinition;
import github.tilcob.app.listmerging.model.ValidationContext;
import github.tilcob.app.listmerging.model.ValidationIssue;
import github.tilcob.app.listmerging.model.ValidationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeValidationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCompareWithReferencePathWhenEnabled() throws IOException, CsvException {
        File csv = createCsv("Name;Amount\nAlice;10\nBob;5\n");
        HeaderDefinition header = new HeaderDefinition(
                "Main",
                List.of("Name", "Amount"),
                null,
                HeaderDefinition.HeaderPosition.FIRST,
                "Amount",
                "(\\d+[\\.,]?\\d*)"
        );

        Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged = new HashMap<>();
        merged.put(header, Map.of(
                List.of("Alice"), new AggregationResult(1, new BigDecimal("10")),
                List.of("Bob"), new AggregationResult(1, new BigDecimal("5"))
        ));

        ValidationContext context = new ValidationContext(Map.of(), new BigDecimal("0.01"), 2, true, true);
        ValidationReport report = new MergeValidationService().validate(merged, context, List.of(csv), List.of(header));

        assertTrue(report.valid());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    void shouldReportDetailedDiffsWhenReferenceDoesNotMatch() throws IOException, CsvException {
        File csv = createCsv("Name;Amount\nAlice;10\nBob;5\n");
        HeaderDefinition header = new HeaderDefinition(
                "Main",
                List.of("Name", "Amount"),
                null,
                HeaderDefinition.HeaderPosition.FIRST,
                "Amount",
                "(\\d+[\\.,]?\\d*)"
        );

        Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged = new HashMap<>();
        merged.put(header, Map.of(
                List.of("Alice"), new AggregationResult(2, new BigDecimal("10"))
        ));

        ValidationContext context = new ValidationContext(Map.of(), new BigDecimal("0.01"), 2, true, true);
        ValidationReport report = new MergeValidationService().validate(merged, context, List.of(csv), List.of(header));

        assertFalse(report.valid());
        assertTrue(report.issues().stream().map(ValidationIssue::code).anyMatch("REFERENCE_COUNT_MISMATCH"::equals));
        assertTrue(report.issues().stream().map(ValidationIssue::code).anyMatch("REFERENCE_MISSING_KEY"::equals));
        assertTrue(report.issues().stream().map(ValidationIssue::details).anyMatch(details -> details != null && details.contains("key=")));
    }

    private File createCsv(String content) throws IOException {
        Path file = tempDir.resolve("sample.csv");
        Files.writeString(file, content);
        return file.toFile();
    }
}
