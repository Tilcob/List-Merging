package github.tilcob.app.listmerging.service;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import github.tilcob.app.listmerging.model.AggregationResult;
import github.tilcob.app.listmerging.model.HeaderDefinition;
import github.tilcob.app.listmerging.model.ValidationContext;
import github.tilcob.app.listmerging.model.ValidationIssue;
import github.tilcob.app.listmerging.model.ValidationReport;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class MergeValidationService {
    private static final Logger log = LoggerFactory.getLogger(MergeValidationService.class);
    private static final Pattern DEFAULT_SUM_PATTERN = Pattern.compile("(\\d+[\\.,]?\\d*)");
    private static final String KEY_SEPARATOR = "\u001F";

    public ValidationReport validate(Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged,
                                     ValidationContext context) {
        return validate(merged, context, List.of(), List.of());
    }

    public ValidationReport validate(Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged,
                                     ValidationContext context,
                                     List<File> files,
                                     List<HeaderDefinition> headers) {
        ValidationContext effectiveContext = context == null
                ? ValidationContext.defaults()
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

            validateRowCount(header, headerName, actualRowCount, effectiveContext, issues);
            validateSum(header, headerName, actualSum, effectiveContext, issues);
        }

        if (effectiveContext.enableReferenceAggregation()) {
            compareWithReferencePath(merged, files, headers, effectiveContext, issues);
        }

        boolean valid = issues.isEmpty();
        log.info("Merge validation finished. valid={}, issues={}", valid, issues.size());
        return valid ? ValidationReport.valid() : ValidationReport.invalid(issues);
    }

    private void validateRowCount(HeaderDefinition header,
                                  String headerName,
                                  int actualRowCount,
                                  ValidationContext context,
                                  List<ValidationIssue> issues) {
        String metricsHeaderName = resolveHeaderName(header, headerName);
        Integer expectedRowCount = context.expectedRowsFor(metricsHeaderName).orElse(null);
        if (expectedRowCount == null) {
            handleMissingExpectation(
                    context,
                    issues,
                    "MISSING_EXPECTED_ROW_COUNT",
                    "Kein Sollwert für die erwartete Zeilenanzahl vorhanden.",
                    metricsHeaderName
            );
            return;
        }

        if (actualRowCount != expectedRowCount) {
            log.warn("Row count mismatch for header '{}': expected={}, actual={}",
                    metricsHeaderName,
                    expectedRowCount,
                    actualRowCount);
            issues.add(new ValidationIssue(
                    ValidationIssue.COUNT_MISMATCH,
                    "Datenzeilen-Anzahl entspricht nicht dem erwarteten Wert.",
                    metricsHeaderName,
                    "expected=" + expectedRowCount + ", actual=" + actualRowCount
            ));
            return;
        }

        log.debug("Row count check passed for header '{}': {}", metricsHeaderName, actualRowCount);
    }

    private void validateSum(HeaderDefinition header,
                             String headerName,
                             BigDecimal actualSum,
                             ValidationContext context,
                             List<ValidationIssue> issues) {
        String metricsHeaderName = resolveHeaderName(header, headerName);
        if (header.sumColumn() == null || header.sumColumn().isBlank()) {
            log.debug("Header '{}' has no sumColumn configured. Skipping sum check.", headerName);
            return;
        }

        BigDecimal expectedSum = context.expectedSumFor(metricsHeaderName).orElse(null);
        if (expectedSum == null) {
            handleMissingExpectation(
                    context,
                    issues,
                    "MISSING_EXPECTED_SUM",
                    "Kein Sollwert für die erwartete Summe vorhanden.",
                    metricsHeaderName
            );
            return;
        }

        BigDecimal scaledActual = actualSum.setScale(context.sumScale(), RoundingMode.HALF_UP);
        BigDecimal scaledExpected = expectedSum.setScale(context.sumScale(), RoundingMode.HALF_UP);
        BigDecimal delta = scaledActual.subtract(scaledExpected).abs();

        if (delta.compareTo(context.sumTolerance()) > 0) {
            log.warn("Sum mismatch for header '{}': expected={}, actual={}, tolerance={}, delta={}",
                    metricsHeaderName,
                    scaledExpected,
                    scaledActual,
                    context.sumTolerance(),
                    delta);
            issues.add(new ValidationIssue(
                    ValidationIssue.SUM_MISMATCH,
                    "Summenprüfung fehlgeschlagen.",
                    metricsHeaderName,
                    "expected=" + scaledExpected
                            + ", actual=" + scaledActual
                            + ", tolerance=" + context.sumTolerance()
                            + ", delta=" + delta
            ));
            return;
        }

        log.debug("Sum check passed for header '{}': expected={}, actual={}, tolerance={}",
                metricsHeaderName,
                scaledExpected,
                scaledActual,
                context.sumTolerance());
    }

    private void compareWithReferencePath(Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged,
                                          List<File> files,
                                          List<HeaderDefinition> headers,
                                          ValidationContext context,
                                          List<ValidationIssue> issues) {
        if (files == null || files.isEmpty() || headers == null || headers.isEmpty()) {
            log.warn("Reference aggregation enabled but files/headers are missing. Skipping reference validation.");
            return;
        }

        try {
            Map<String, Map<String, AggregationResult>> referenceByHeader = buildReferenceAggregation(files, headers);
            Map<String, Map<String, AggregationResult>> mergedByHeader = buildMergedAggregationView(merged);
            compareAggregations(mergedByHeader, referenceByHeader, context, issues);
        } catch (IOException | CsvException e) {
            issues.add(new ValidationIssue(
                    "REFERENCE_PATH_ERROR",
                    "Referenz-Aggregation konnte nicht berechnet werden.",
                    null,
                    e.getMessage()
            ));
        }
    }

    private Map<String, Map<String, AggregationResult>> buildMergedAggregationView(
            Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged) {
        Map<String, Map<String, AggregationResult>> result = new LinkedHashMap<>();

        for (Map.Entry<HeaderDefinition, Map<List<String>, AggregationResult>> entry : merged.entrySet()) {
            String headerName = resolveHeaderName(entry.getKey(), null);
            if (headerName == null || headerName.isBlank()) {
                continue;
            }

            Map<String, AggregationResult> targetBucket = result.computeIfAbsent(headerName, ignored -> new HashMap<>());
            for (Map.Entry<List<String>, AggregationResult> bucketEntry : entry.getValue().entrySet()) {
                String key = canonicalKeyFromMerged(bucketEntry.getKey());
                targetBucket.merge(key, bucketEntry.getValue(), AggregationResult::add);
            }
        }

        return result;
    }

    private Map<String, Map<String, AggregationResult>> buildReferenceAggregation(List<File> files,
                                                                                   List<HeaderDefinition> headers)
            throws IOException, CsvException {
        Map<String, Map<String, AggregationResult>> result = new LinkedHashMap<>();

        for (File file : files) {
            FileRows fileRows = readRows(file);
            if (fileRows.rows().isEmpty()) {
                continue;
            }

            HeaderDefinition header = chooseHeader(fileRows.rows(), headers);
            int headerIndex = resolveHeaderIndex(fileRows.rows(), header);
            int sumColumnIndex = resolveSumColumnIndex(header);
            Pattern sumPattern = resolveSumPattern(header);
            String headerName = resolveHeaderName(header, "Unknown");
            Map<String, AggregationResult> bucket = result.computeIfAbsent(headerName, ignored -> new HashMap<>());

            for (int i = 0; i < fileRows.rows().size(); i++) {
                if (i == headerIndex) {
                    continue;
                }

                List<String> row = fileRows.rows().get(i);
                if (isBlankRow(row)) {
                    continue;
                }

                String key = canonicalKeyFromRawRow(row, sumColumnIndex);
                BigDecimal sumValue = parseSum(row, sumColumnIndex, sumPattern);
                bucket.merge(key, new AggregationResult(1, sumValue), AggregationResult::add);
            }
        }

        return result;
    }

    private void compareAggregations(Map<String, Map<String, AggregationResult>> mergedByHeader,
                                     Map<String, Map<String, AggregationResult>> referenceByHeader,
                                     ValidationContext context,
                                     List<ValidationIssue> issues) {
        for (Map.Entry<String, Map<String, AggregationResult>> headerEntry : referenceByHeader.entrySet()) {
            String headerName = headerEntry.getKey();
            Map<String, AggregationResult> referenceBucket = headerEntry.getValue();
            Map<String, AggregationResult> mergedBucket = mergedByHeader.getOrDefault(headerName, Map.of());

            for (Map.Entry<String, AggregationResult> referenceEntry : referenceBucket.entrySet()) {
                String key = referenceEntry.getKey();
                AggregationResult referenceValue = referenceEntry.getValue();
                AggregationResult mergedValue = mergedBucket.get(key);

                if (mergedValue == null) {
                    issues.add(new ValidationIssue(
                            "REFERENCE_MISSING_KEY",
                            "Schlüssel fehlt im Merge-Ergebnis.",
                            headerName,
                            "missingKey=" + key
                    ));
                    continue;
                }

                if (referenceValue.rowCount() != mergedValue.rowCount()) {
                    issues.add(new ValidationIssue(
                            "REFERENCE_COUNT_MISMATCH",
                            "Abweichende Anzahl für denselben Schlüssel.",
                            headerName,
                            "key=" + key
                                    + ", referenceCount=" + referenceValue.rowCount()
                                    + ", mergedCount=" + mergedValue.rowCount()
                    ));
                }

                BigDecimal referenceSum = Objects.requireNonNullElse(referenceValue.sumValue(), BigDecimal.ZERO)
                        .setScale(context.sumScale(), RoundingMode.HALF_UP);
                BigDecimal mergedSum = Objects.requireNonNullElse(mergedValue.sumValue(), BigDecimal.ZERO)
                        .setScale(context.sumScale(), RoundingMode.HALF_UP);
                BigDecimal delta = referenceSum.subtract(mergedSum).abs();

                if (delta.compareTo(context.sumTolerance()) > 0) {
                    issues.add(new ValidationIssue(
                            "REFERENCE_SUM_MISMATCH",
                            "Abweichende Summe für denselben Schlüssel.",
                            headerName,
                            "key=" + key
                                    + ", referenceSum=" + referenceSum
                                    + ", mergedSum=" + mergedSum
                                    + ", delta=" + delta
                    ));
                }
            }
        }
    }

    private FileRows readRows(File file) throws IOException, CsvException {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            try (var reader = new CSVReaderBuilder(
                    Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
                    .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                    .build()) {
                List<List<String>> rows = reader.readAll().stream()
                        .map(cells -> Arrays.stream(cells).map(value -> value == null ? "" : value).toList())
                        .toList();
                return new FileRows(rows);
            }
        }

        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            try (Workbook workbook = WorkbookFactory.create(file)) {
                DataFormatter formatter = new DataFormatter();
                Sheet sheet = workbook.getSheetAt(0);
                List<List<String>> rows = new ArrayList<>();
                for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        rows.add(List.of());
                        continue;
                    }
                    rows.add(toStringRow(row, formatter));
                }
                return new FileRows(rows);
            }
        }

        throw new IOException("Unsupported file type for validation: " + file.getName());
    }

    private HeaderDefinition chooseHeader(List<List<String>> rows, List<HeaderDefinition> headers) {
        if (rows.isEmpty()) {
            return new HeaderDefinition("Empty", List.of());
        }

        int lastNonBlankIndex = -1;
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (!isBlankRow(rows.get(i))) {
                lastNonBlankIndex = i;
                break;
            }
        }

        if (lastNonBlankIndex < 0) {
            return new HeaderDefinition("Empty", List.of());
        }

        List<String> first = normalizeRow(rows.get(0));
        List<String> last = normalizeRow(rows.get(lastNonBlankIndex));

        for (HeaderDefinition def : headers) {
            List<String> normalizedHeader = normalizeRow(def.headers());
            if (def.headerPosition() == HeaderDefinition.HeaderPosition.LAST) {
                if (normalizedHeader.equals(last)) {
                    return def;
                }
            } else if (normalizedHeader.equals(first)) {
                return def;
            }
        }

        for (HeaderDefinition def : headers) {
            List<List<String>> aliases = def.headerAliases() == null ? List.of() : def.headerAliases();
            List<List<String>> normalizedAliases = aliases.stream().map(this::normalizeRow).toList();
            if (def.headerPosition() == HeaderDefinition.HeaderPosition.LAST) {
                if (normalizedAliases.contains(last)) {
                    return def;
                }
            } else if (normalizedAliases.contains(first)) {
                return def;
            }
        }

        return new HeaderDefinition("Unknown_" + first.size(), List.of());
    }

    private int resolveHeaderIndex(List<List<String>> rows, HeaderDefinition header) {
        if (rows.isEmpty()) {
            return -1;
        }
        if (header.headerPosition() == HeaderDefinition.HeaderPosition.LAST) {
            for (int i = rows.size() - 1; i >= 0; i--) {
                if (!isBlankRow(rows.get(i))) {
                    return i;
                }
            }
            return -1;
        }
        return 0;
    }

    private int resolveSumColumnIndex(HeaderDefinition header) {
        if (header.sumColumn() == null || header.sumColumn().isBlank() || header.headers() == null) {
            return -1;
        }
        for (int i = 0; i < header.headers().size(); i++) {
            String cell = header.headers().get(i);
            if (cell != null && cell.equalsIgnoreCase(header.sumColumn())) {
                return i;
            }
        }
        return -1;
    }

    private Pattern resolveSumPattern(HeaderDefinition header) {
        if (header.sumPattern() == null || header.sumPattern().isBlank()) {
            return DEFAULT_SUM_PATTERN;
        }
        return Pattern.compile(header.sumPattern());
    }

    private BigDecimal parseSum(List<String> row, int sumIndex, Pattern pattern) {
        if (sumIndex < 0 || sumIndex >= row.size()) {
            return BigDecimal.ZERO;
        }
        String value = row.get(sumIndex);
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return BigDecimal.ZERO;
        }
        String normalized = matcher.group(1).replace(',', '.');
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String canonicalKeyFromRawRow(List<String> row, int sumColumnIndex) {
        List<String> normalized = normalizeRow(row);
        StringBuilder keyBuilder = new StringBuilder();
        for (int i = 0; i < normalized.size(); i++) {
            if (i == sumColumnIndex) {
                continue;
            }
            if (keyBuilder.length() > 0) {
                keyBuilder.append(KEY_SEPARATOR);
            }
            keyBuilder.append(normalizeCell(normalized.get(i)));
        }
        return keyBuilder.toString();
    }

    private String canonicalKeyFromMerged(List<String> keyParts) {
        return keyParts == null
                ? ""
                : keyParts.stream().map(this::normalizeCell).reduce((left, right) -> left + KEY_SEPARATOR + right).orElse("");
    }

    private String normalizeCell(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeRow(List<String> row) {
        List<String> normalized = row == null
                ? List.of()
                : row.stream().map(this::normalizeCell).toList();
        int end = normalized.size();
        while (end > 0 && normalized.get(end - 1).isBlank()) {
            end--;
        }
        return normalized.subList(0, end);
    }

    private boolean isBlankRow(List<String> row) {
        return row == null || row.stream().allMatch(value -> value == null || value.isBlank());
    }

    private List<String> toStringRow(Row row, DataFormatter formatter) {
        short firstCellIndex = row.getFirstCellNum();
        short lastCellIndex = row.getLastCellNum();
        if (firstCellIndex < 0 || lastCellIndex <= firstCellIndex) {
            return List.of();
        }

        return IntStream.range(firstCellIndex, lastCellIndex)
                .mapToObj(index -> {
                    Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    return cell == null ? "" : formatter.formatCellValue(cell);
                })
                .toList();
    }

    private void handleMissingExpectation(ValidationContext context,
                                          List<ValidationIssue> issues,
                                          String code,
                                          String message,
                                          String headerName) {
        if (context.treatMissingExpectationsAsWarning()) {
            log.warn("{} Header='{}'. Validation continues because missing expectations are configured as warnings.",
                    message,
                    headerName);
            return;
        }

        log.warn("{} Header='{}'. Validation marked as invalid.", message, headerName);
        issues.add(new ValidationIssue(code, message, headerName));
    }

    private String resolveHeaderName(HeaderDefinition header, String fallbackHeaderName) {
        if (header != null && header.name() != null && !header.name().isBlank()) {
            return header.name();
        }
        return fallbackHeaderName;
    }

    private record FileRows(List<List<String>> rows) {
    }
}
