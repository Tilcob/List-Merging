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

    private final MergeValidationService service = new MergeValidationService();

    @Test
    void happyPathShouldBeValid() {
        HeaderDefinition header = header("Main", "Amount");
        Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged = mergedData(
                header,
                Map.of(
                        List.of("Alice"), new AggregationResult(1, new BigDecimal("10.00")),
                        List.of("Bob"), new AggregationResult(1, new BigDecimal("5.00"))
                )
        );

        ValidationContext context = contextFor("Main", 2, "15.00");

        ValidationReport report = service.validate(merged, context);

        assertTrue(report.valid());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    void shouldReturnCountMismatchWhenExpectedRowsDoNotMatch() {
        HeaderDefinition header = header("Main", "Amount");
        Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged = mergedData(
                header,
                Map.of(List.of("Alice"), new AggregationResult(1, new BigDecimal("10.00")))
        );

        ValidationContext context = contextFor("Main", 2, "10.00");

        ValidationReport report = service.validate(merged, context);

        assertFalse(report.valid());
        assertTrue(report.issues().stream().map(ValidationIssue::code).anyMatch(ValidationIssue.COUNT_MISMATCH::equals));
    }

    @Test
    void shouldReturnSumMismatchWhenExpectedSumDiffers() {
        HeaderDefinition header = header("Main", "Amount");
        Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged = mergedData(
                header,
                Map.of(List.of("Alice"), new AggregationResult(1, new BigDecimal("10.00")))
        );

        ValidationContext context = contextFor("Main", 1, "8.00");

        ValidationReport report = service.validate(merged, context);

        assertFalse(report.valid());
        assertTrue(report.issues().stream().map(ValidationIssue::code).anyMatch(ValidationIssue.SUM_MISMATCH::equals));
    }

    @Test
    void shouldReturnHeaderErrorCodeForBlankHeaderName() {
        HeaderDefinition blankHeader = header("   ", "Amount");
        Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged = mergedData(
                blankHeader,
                Map.of(List.of("Alice"), new AggregationResult(1, new BigDecimal("10.00")))
        );

        ValidationReport report = service.validate(merged, ValidationContext.defaults());

        assertFalse(report.valid());
        assertTrue(report.issues().stream().map(ValidationIssue::code).anyMatch("INVALID_HEADER"::equals));
    }

    @Test
    void shouldIgnoreBlankRowsAndValidateReferenceWithEmptyFileAndMissingSumColumn() throws IOException, CsvException {
        HeaderDefinition header = new HeaderDefinition(
                "Main",
                List.of("Name", "Amount"),
                null,
                HeaderDefinition.HeaderPosition.FIRST,
                null,
                "(\\d+[\\.,]?\\d*)"
        );

        File fileWithBlankRows = createCsv("Name;Amount\n\nAlice;10\n  ;   \nBob;5\n");
        File emptyFile = createCsv("", "empty.csv");

        Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged = mergedData(
                header,
                Map.of(
                        List.of("Alice", "10"), new AggregationResult(1, BigDecimal.ZERO),
                        List.of("Bob", "5"), new AggregationResult(1, BigDecimal.ZERO)
                )
        );

        ValidationContext context = new ValidationContext(Map.of(), new BigDecimal("0.01"), 2, true, true);

        ValidationReport report = service.validate(merged, context, List.of(fileWithBlankRows, emptyFile), List.of(header));

        assertTrue(report.valid());
        assertTrue(report.issues().isEmpty());
    }

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
        ValidationReport report = service.validate(merged, context, List.of(csv), List.of(header));

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
        ValidationReport report = service.validate(merged, context, List.of(csv), List.of(header));

        assertFalse(report.valid());
        assertTrue(report.issues().stream().map(ValidationIssue::code).anyMatch("REFERENCE_COUNT_MISMATCH"::equals));
        assertTrue(report.issues().stream().map(ValidationIssue::code).anyMatch("REFERENCE_MISSING_KEY"::equals));
        assertTrue(report.issues().stream().map(ValidationIssue::details).anyMatch(details -> details != null && details.contains("key=")));
    }

    private Map<HeaderDefinition, Map<List<String>, AggregationResult>> mergedData(
            HeaderDefinition header,
            Map<List<String>, AggregationResult> values
    ) {
        return new HashMap<>(Map.of(header, values));
    }

    private HeaderDefinition header(String name, String sumColumn) {
        return new HeaderDefinition(
                name,
                List.of("Name", "Amount"),
                null,
                HeaderDefinition.HeaderPosition.FIRST,
                sumColumn,
                "(\\d+[\\.,]?\\d*)"
        );
    }

    private ValidationContext contextFor(String headerName, int expectedRows, String expectedSum) {
        return new ValidationContext(
                Map.of(headerName, new ValidationContext.ExpectedMetrics(expectedRows, new BigDecimal(expectedSum))),
                new BigDecimal("0.01"),
                2,
                false,
                false
        );
    }

    private File createCsv(String content) throws IOException {
        return createCsv(content, "sample.csv");
    }

    private File createCsv(String content, String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file.toFile();
    }
}
