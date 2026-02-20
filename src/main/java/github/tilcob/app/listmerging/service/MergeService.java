package github.tilcob.app.listmerging.service;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import github.tilcob.app.listmerging.model.HeaderDefinition;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.IntStream;

public class MergeService {
    private static final Logger log = LoggerFactory.getLogger(MergeService.class);

    public Map<HeaderDefinition, Map<List<String>, Integer>> merge(List<File> files, List<HeaderDefinition> headers)
            throws IOException, CsvException {
        Map<HeaderDefinition, Map<List<String>, Integer>> result = new LinkedHashMap<>();

        for (File file : files) {
            FileType fileType = detect(file);
            FileReadResult fileResult = switch (fileType) {
                case EXCEL -> readExcel(file, headers);
                case CSV -> readCsv(file, headers);
            };
            Map<List<String>, Integer> bucket =
                    result.computeIfAbsent(fileResult.header(), k -> new HashMap<>());

            fileResult.counts().forEach((row, count) -> bucket.merge(row, count, Integer::sum));
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

            Map<List<String>, Integer> counts = new HashMap<>();
            for (int r = firstRowIndex; r <= lastRowIndex; r++) {
                if (r == headerIndex) continue;
                Row row = sheet.getRow(r);
                if (row == null) continue;

                List<String> cells = toStringRow(row, fmt);
                if (isBlankRow(cells)) continue;

                counts.merge(List.copyOf(cells), 1, Integer::sum);
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

            Map<List<String>, Integer> counts = new HashMap<>();
            for (int i = firstRowIndex; i <= lastRowIndex; i++) {
                if (i == headerIndex) continue;

                List<String> row = rows.get(i);
                if (isBlankRow(row)) continue;

                counts.merge(List.copyOf(row), 1, Integer::sum);
            }

            return new FileReadResult(chosen, counts);
        }
    }

    private FileType detect(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) return FileType.EXCEL;
        if (name.endsWith(".csv")) return FileType.CSV;
        throw new IllegalArgumentException("Unsupported file type: " + name);
    }

    private List<String> normalizeRow(List<String> row) {
        return row.stream()
                .map(s -> s == null ? "" : s.trim().toLowerCase(Locale.ROOT))
                .toList();
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

        int len = firstRow.size();
        List<HeaderDefinition> sameLen = headers.stream()
                .filter(h -> h.headers().size() == len)
                .filter(h -> h.headerPosition() == HeaderDefinition.HeaderPosition.FIRST)
                .toList();

        if (sameLen.size() == 1) return sameLen.get(0);
        return new HeaderDefinition("Unknown_" + len, List.of());
    }

    public enum FileType {
        EXCEL, CSV
    }

    private record FileReadResult(HeaderDefinition header, Map<List<String>, Integer> counts) {
    }
}
