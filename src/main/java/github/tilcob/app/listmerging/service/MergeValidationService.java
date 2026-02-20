package github.tilcob.app.listmerging.service;

import github.tilcob.app.listmerging.model.AggregationResult;
import github.tilcob.app.listmerging.model.HeaderDefinition;
import github.tilcob.app.listmerging.model.ValidationContext;
import github.tilcob.app.listmerging.model.ValidationIssue;
import github.tilcob.app.listmerging.model.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MergeValidationService {
    private static final Logger log = LoggerFactory.getLogger(MergeValidationService.class);

    public ValidationReport validate(Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged,
                                     ValidationContext context) {
        ValidationContext effectiveContext = context == null
                ? new ValidationContext(null, null, null, 2)
                : context;
        List<ValidationIssue> issues = new ArrayList<>();

        if (merged == null || merged.isEmpty()) {
            log.warn("Merge validation called with empty merged result.");
            issues.add(new ValidationIssue("EMPTY_MERGED_DATA", "Keine aggregierten Daten für die Validierung vorhanden."));
            return ValidationReport.invalid(issues);
        }

        log.info("Start merge validation for {} header groups.", merged.size());

        for (Map.Entry<HeaderDefinition, Map<List<String>, AggregationResult>> entry : merged.entrySet()) {
            HeaderDefinition header = entry.getKey();
            Map<List<String>, AggregationResult> groupedRows = entry.getValue();

            String headerName = header == null ? null : header.name();
            if (header == null || headerName == null || headerName.isBlank()) {
                log.warn("Invalid header group detected: header or header name is missing.");
                issues.add(new ValidationIssue(
                        "INVALID_HEADER",
                        "Header darf nicht null sein und muss einen Namen enthalten.",
                        headerName,
                        "header=" + header
                ));
                continue;
            }

            if (groupedRows == null) {
                log.warn("Header '{}' has null grouped rows.", headerName);
                issues.add(new ValidationIssue(
                        "EMPTY_GROUP",
                        "Für den Header wurden keine Gruppendaten gefunden.",
                        headerName,
                        "groupedRows=null"
                ));
                continue;
            }

            int actualRowCount = 0;
            BigDecimal actualSum = BigDecimal.ZERO;

            for (Map.Entry<List<String>, AggregationResult> groupedEntry : groupedRows.entrySet()) {
                List<String> key = groupedEntry.getKey();
                AggregationResult aggregation = groupedEntry.getValue();

                if (aggregation == null) {
                    log.warn("Header '{}' has null aggregation for key {}.", headerName, key);
                    issues.add(new ValidationIssue(
                            "NULL_AGGREGATION",
                            "AggregationResult darf nicht null sein.",
                            headerName,
                            "key=" + key
                    ));
                    continue;
                }

                if (aggregation.rowCount() < 1) {
                    log.warn("Header '{}' has invalid rowCount {} for key {}.", headerName, aggregation.rowCount(), key);
                    issues.add(new ValidationIssue(
                            "INVALID_ROW_COUNT",
                            "AggregationResult.rowCount muss >= 1 sein.",
                            headerName,
                            "key=" + key + ", rowCount=" + aggregation.rowCount()
                    ));
                }

                if (aggregation.sumValue() == null) {
                    log.warn("Header '{}' has null sumValue for key {}.", headerName, key);
                    issues.add(new ValidationIssue(
                            "NULL_SUM_VALUE",
                            "AggregationResult.sumValue darf nicht null sein.",
                            headerName,
                            "key=" + key
                    ));
                }

                actualRowCount += Math.max(aggregation.rowCount(), 0);
                if (aggregation.sumValue() != null) {
                    actualSum = actualSum.add(aggregation.sumValue());
                }
            }

            validateRowCount(headerName, actualRowCount, effectiveContext, issues);
            validateSum(header, headerName, actualSum, effectiveContext, issues);
        }

        boolean valid = issues.isEmpty();
        log.info("Merge validation finished. valid={}, issues={}", valid, issues.size());
        return valid ? ValidationReport.valid() : ValidationReport.invalid(issues);
    }

    private void validateRowCount(String headerName,
                                  int actualRowCount,
                                  ValidationContext context,
                                  List<ValidationIssue> issues) {
        int expectedRowCount = context.expectedRowsFor(headerName);
        if (expectedRowCount < 0) {
            log.debug("No expected row count configured for header '{}'. Skipping count check.", headerName);
            return;
        }

        if (actualRowCount != expectedRowCount) {
            log.warn("Row count mismatch for header '{}': expected={}, actual={}",
                    headerName,
                    expectedRowCount,
                    actualRowCount);
            issues.add(new ValidationIssue(
                    ValidationIssue.COUNT_MISMATCH,
                    "Datenzeilen-Anzahl entspricht nicht dem erwarteten Wert.",
                    headerName,
                    "expected=" + expectedRowCount + ", actual=" + actualRowCount
            ));
            return;
        }

        log.debug("Row count check passed for header '{}': {}", headerName, actualRowCount);
    }

    private void validateSum(HeaderDefinition header,
                             String headerName,
                             BigDecimal actualSum,
                             ValidationContext context,
                             List<ValidationIssue> issues) {
        if (header.sumColumn() == null || header.sumColumn().isBlank()) {
            log.debug("Header '{}' has no sumColumn configured. Skipping sum check.", headerName);
            return;
        }

        BigDecimal expectedSum = context.expectedSumFor(headerName);
        if (expectedSum == null) {
            log.debug("No expected sum configured for header '{}'. Skipping sum check.", headerName);
            return;
        }

        BigDecimal scaledActual = actualSum.setScale(context.sumScale(), RoundingMode.HALF_UP);
        BigDecimal scaledExpected = expectedSum.setScale(context.sumScale(), RoundingMode.HALF_UP);
        BigDecimal delta = scaledActual.subtract(scaledExpected).abs();

        if (delta.compareTo(context.sumTolerance()) > 0) {
            log.warn("Sum mismatch for header '{}': expected={}, actual={}, tolerance={}, delta={}",
                    headerName,
                    scaledExpected,
                    scaledActual,
                    context.sumTolerance(),
                    delta);
            issues.add(new ValidationIssue(
                    ValidationIssue.SUM_MISMATCH,
                    "Summenprüfung fehlgeschlagen.",
                    headerName,
                    "expected=" + scaledExpected
                            + ", actual=" + scaledActual
                            + ", tolerance=" + context.sumTolerance()
                            + ", delta=" + delta
            ));
            return;
        }

        log.debug("Sum check passed for header '{}': expected={}, actual={}, tolerance={}",
                headerName,
                scaledExpected,
                scaledActual,
                context.sumTolerance());
    }
}
