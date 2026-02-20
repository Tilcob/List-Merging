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

            Row headerRowPoi = sheet.getRow(0);
            if (headerRowPoi == null) {
                return new FileReadResult(new HeaderDefinition("Empty", List.of()), Map.of());
            }

            List<String> headerRow = toStringRow(headerRowPoi, fmt);
            HeaderDefinition chosen = chooseHeader(headerRow, headers);

            Map<List<String>, Integer> counts = new HashMap<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
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
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                return new FileReadResult(new HeaderDefinition("Empty", List.of()), Map.of());
            }

            List<String> headerRow = Arrays.stream(rows.get(0)).map(s -> s == null ? "" : s).toList();
            HeaderDefinition chosen = chooseHeader(headerRow, headers);

            Map<List<String>, Integer> counts = new HashMap<>();
            rows.stream()
                    .skip(1)
                    .map(arr -> Arrays.stream(arr).map(s -> s == null ? "" : s).toList())
                    .filter(r -> !isBlankRow(r))
                    .map(List::copyOf)
                    .forEach(r -> counts.merge(r, 1, Integer::sum));

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
        short last  = row.getLastCellNum();
        if (first < 0 || last <= first) return List.of();

        return IntStream.range(first, last)
                .mapToObj(i -> {
                    Cell c = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    return c == null ? "" : fmt.formatCellValue(c);
                })
                .toList();
    }

    private HeaderDefinition chooseHeader(List<String> firstRow, List<HeaderDefinition> headers) {
        List<String> nFirst = normalizeRow(firstRow);

        for (HeaderDefinition def : headers) {
            if (normalizeRow(def.headers()).equals(nFirst)) return def;
        }

        int len = firstRow.size();
        List<HeaderDefinition> sameLen = headers.stream()
                .filter(h -> h.headers().size() == len)
                .toList();

        if (sameLen.size() == 1) return sameLen.get(0);
        return new HeaderDefinition("Unknown_" + len, List.of());
    }

    public enum FileType {
        EXCEL, CSV
    }

    private record FileReadResult(HeaderDefinition header, Map<List<String>, Integer> counts) {}
}
