package github.tilcob.app.listmerging.service;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import github.tilcob.app.listmerging.model.AggregationResult;
import github.tilcob.app.listmerging.model.HeaderDefinition;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class MergeService {
    private static final Logger log = LoggerFactory.getLogger(MergeService.class);
    private static final Pattern DEFAULT_SUM_PATTERN = Pattern.compile("(\\d+[\\.,]?\\d*)");

    public Map<HeaderDefinition, Map<List<String>, AggregationResult>> merge(List<File> files, List<HeaderDefinition> headers)
            throws IOException, CsvException {
        Map<HeaderDefinition, Map<List<String>, AggregationResult>> result = new LinkedHashMap<>();

        for (File file : files) {
            FileType fileType = detect(file);
            FileReadResult fileResult = switch (fileType) {
                case EXCEL -> readExcel(file, headers);
                case CSV -> readCsv(file, headers);
            };
            Map<List<String>, AggregationResult> bucket =
                    result.computeIfAbsent(fileResult.header(), k -> new HashMap<>());

            fileResult.counts().forEach((row, aggregation) ->
                    bucket.merge(row, aggregation, AggregationResult::add));
        }
        return result;
    }

    private FileReadResult readExcel(File file, List<HeaderDefinition> headers) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            int firstRowIndex = 0;
            int lastRowIndex = findLastNonBlankExcelRow(sheet, fmt);
            if (lastRowIndex < 0) {
                return new FileReadResult(new HeaderDefinition("Empty", List.of()), Map.of());
            }

            List<String> firstRow = Optional.ofNullable(sheet.getRow(firstRowIndex))
                    .map(row -> toStringRow(row, fmt))
                    .orElse(List.of());
            List<String> lastRow = Optional.ofNullable(sheet.getRow(lastRowIndex))
                    .map(row -> toStringRow(row, fmt))
                    .orElse(List.of());

            HeaderDefinition chosen = chooseHeader(firstRow, lastRow, headers);
            int headerIndex = chosen.headerPosition() == HeaderDefinition.HeaderPosition.LAST
                    ? lastRowIndex
                    : firstRowIndex;

            SumConfig sumConfig = buildSumConfig(chosen);
            Map<List<String>, AggregationResult> counts = new HashMap<>();
            for (int r = firstRowIndex; r <= lastRowIndex; r++) {
                if (r == headerIndex) continue;
                Row row = sheet.getRow(r);
                if (row == null) continue;

                List<String> cells = toStringRow(row, fmt);
                if (isBlankRow(cells)) continue;

                List<String> key = buildGroupingKey(cells, sumConfig.columnIndex());
                BigDecimal sumValue = parseSumValue(cells, sumConfig);
                counts.merge(key, new AggregationResult(1, sumValue), AggregationResult::add);
            }

            return new FileReadResult(chosen, counts);
        } catch (InvalidFormatException e) {
            throw new IOException("Invalid Excel format: " + file.getName(), e);
        }
    }

    private FileReadResult readCsv(File file, List<HeaderDefinition> headers) throws CsvException, IOException {
        try (var reader = new CSVReaderBuilder(
                Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()
        ) {
            List<List<String>> rows = reader.readAll().stream()
                    .map(arr -> Arrays.stream(arr).map(s -> s == null ? "" : s).toList())
                    .toList();
            if (rows.isEmpty()) {
                return new FileReadResult(new HeaderDefinition("Empty", List.of()), Map.of());
            }

            int firstRowIndex = 0;
            int lastRowIndex = findLastNonBlankCsvRow(rows);
            if (lastRowIndex < 0) {
                return new FileReadResult(new HeaderDefinition("Empty", List.of()), Map.of());
            }

            List<String> firstRow = rows.get(firstRowIndex);
            List<String> lastRow = rows.get(lastRowIndex);
            HeaderDefinition chosen = chooseHeader(firstRow, lastRow, headers);
            int headerIndex = chosen.headerPosition() == HeaderDefinition.HeaderPosition.LAST
                    ? lastRowIndex
                    : firstRowIndex;

            SumConfig sumConfig = buildSumConfig(chosen);
            Map<List<String>, AggregationResult> counts = new HashMap<>();
            for (int i = firstRowIndex; i <= lastRowIndex; i++) {
                if (i == headerIndex) continue;

                List<String> row = rows.get(i);
                if (isBlankRow(row)) continue;

                List<String> key = buildGroupingKey(row, sumConfig.columnIndex());
                BigDecimal sumValue = parseSumValue(row, sumConfig);
                counts.merge(key, new AggregationResult(1, sumValue), AggregationResult::add);
            }

            return new FileReadResult(chosen, counts);
        }
    }

    private SumConfig buildSumConfig(HeaderDefinition header) {
        String sumColumn = header.sumColumn();
        if (sumColumn == null || sumColumn.isBlank() || header.headers() == null || header.headers().isEmpty()) {
            return new SumConfig(-1, DEFAULT_SUM_PATTERN);
        }

        int columnIndex = IntStream.range(0, header.headers().size())
                .filter(i -> sumColumn.equalsIgnoreCase(header.headers().get(i)))
                .findFirst()
                .orElse(-1);

        if (columnIndex < 0) {
            log.warn("Configured sumColumn '{}' not found in header set '{}'.", sumColumn, header.name());
            return new SumConfig(-1, DEFAULT_SUM_PATTERN);
        }

        Pattern pattern = DEFAULT_SUM_PATTERN;
        if (header.sumPattern() != null && !header.sumPattern().isBlank()) {
            pattern = Pattern.compile(header.sumPattern());
        }

        return new SumConfig(columnIndex, pattern);
    }

    private List<String> buildGroupingKey(List<String> row, int sumColumnIndex) {
        if (sumColumnIndex < 0 || sumColumnIndex >= row.size()) {
            return List.copyOf(row);
        }

        List<String> key = new ArrayList<>(row.size() - 1);
        for (int i = 0; i < row.size(); i++) {
            if (i != sumColumnIndex) {
                key.add(row.get(i));
            }
        }
        return List.copyOf(key);
    }

    private BigDecimal parseSumValue(List<String> row, SumConfig sumConfig) {
        int idx = sumConfig.columnIndex();
        if (idx < 0 || idx >= row.size()) {
            return BigDecimal.ZERO;
        }

        String cellValue = row.get(idx);
        if (cellValue == null || cellValue.isBlank()) {
            return BigDecimal.ZERO;
        }

        Matcher matcher = sumConfig.pattern().matcher(cellValue);
        if (!matcher.find()) {
            return BigDecimal.ZERO;
        }

        String normalized = matcher.group(1).replace(',', '.');
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            log.debug("Could not parse sum value '{}' in cell '{}'.", normalized, cellValue);
            return BigDecimal.ZERO;
        }
    }

    private FileType detect(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) return FileType.EXCEL;
        if (name.endsWith(".csv")) return FileType.CSV;
        throw new IllegalArgumentException("Unsupported file type: " + name);
    }

    private List<String> normalizeRow(List<String> row) {
        List<String> normalized = row.stream()
                .map(s -> s == null ? "" : s.trim().toLowerCase(Locale.ROOT))
                .toList();
        int end = normalized.size();
        while (end > 0 && normalized.get(end - 1).isBlank()) {
            end--;
        }
        return normalized.subList(0, end);
    }

    private boolean isBlankRow(List<String> row) {
        return row == null || row.stream().allMatch(s -> s == null || s.isBlank());
    }

    private List<String> toStringRow(Row row, DataFormatter fmt) {
        short first = row.getFirstCellNum();
        short last = row.getLastCellNum();
        if (first < 0 || last <= first) return List.of();

        return IntStream.range(first, last)
                .mapToObj(i -> {
                    Cell c = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    return c == null ? "" : fmt.formatCellValue(c);
                })
                .toList();
    }

    private int findLastNonBlankExcelRow(Sheet sheet, DataFormatter fmt) {
        for (int i = sheet.getLastRowNum(); i >= 0; i--) {
            Row row = sheet.getRow(i);
            if (row != null && !isBlankRow(toStringRow(row, fmt))) {
                return i;
            }
        }
        return -1;
    }

    private int findLastNonBlankCsvRow(List<List<String>> rows) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (!isBlankRow(rows.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private HeaderDefinition chooseHeader(List<String> firstRow, List<String> lastRow, List<HeaderDefinition> headers) {
        List<String> normalizedFirst = normalizeRow(firstRow);
        List<String> normalizedLast = normalizeRow(lastRow);

        for (HeaderDefinition def : headers) {
            List<String> normalizedDef = normalizeRow(def.headers());
            if (def.headerPosition() == HeaderDefinition.HeaderPosition.LAST) {
                if (normalizedDef.equals(normalizedLast)) return def;
                continue;
            }
            if (normalizedDef.equals(normalizedFirst)) return def;
        }

        for (HeaderDefinition def : headers) {
            if (def.headerAliases() == null || def.headerAliases().isEmpty()) continue;

            List<List<String>> normalizedAliases = def.headerAliases().stream()
                    .map(this::normalizeRow)
                    .toList();

            if (def.headerPosition() == HeaderDefinition.HeaderPosition.LAST) {
                if (normalizedAliases.contains(normalizedLast)) return def;
                continue;
            }
            if (normalizedAliases.contains(normalizedFirst)) return def;
        }

        int firstLen = normalizedFirst.size();
        int lastLen = normalizedLast.size();
        List<HeaderDefinition> sameLen = headers.stream()
                .filter(h -> {
                    int headerLen = normalizeRow(h.headers()).size();
                    return h.headerPosition() == HeaderDefinition.HeaderPosition.LAST
                            ? headerLen == lastLen
                            : headerLen == firstLen;
                })
                .toList();

        if (sameLen.size() == 1) return sameLen.get(0);
        return new HeaderDefinition("Unknown_" + firstLen, List.of());
    }

    public enum FileType {
        EXCEL, CSV
    }

    private record FileReadResult(HeaderDefinition header, Map<List<String>, AggregationResult> counts) {
    }

    private record SumConfig(int columnIndex, Pattern pattern) {
    }
}
