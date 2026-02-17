package github.tilcob.app.listmerging.service;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.IntStream;

public class MergeService {
    private static final Logger log = LoggerFactory.getLogger(MergeService.class);

    public Map<List<String>, Integer> merge(List<File> files) throws IOException, CsvException {
        Map<List<String>, Integer> result = new HashMap<>();

        for (File file : files) {
            FileType fileType = detect(file);
            Map<List<String>, Integer> fileResult = switch (fileType) {
                case EXCEL -> readExcel(file);
                case CSV -> readCsv(file);
            };
            mergeMaps(result, fileResult);
        }
        return result;
    }

    private static <K> void mergeMaps(Map<K, Integer> into, Map<K, Integer> from) {
        from.forEach((key, value) -> into.merge(key, value, Integer::sum));
    }

    private Map<List<String>, Integer> readExcel(File file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file)) {
            return mergeRows(workbook);
        }
    }

    private Map<List<String>, Integer> readCsv(File file) throws CsvException, IOException {
        Map<List<String>, Integer> result = new HashMap<>();
        try (var reader = new CSVReaderBuilder(
                Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
                .withCSVParser(new CSVParserBuilder()
                        .withSeparator(';').build()).build()
        ) {
            List<String[]> rows = reader.readAll();
            rows.stream()
                    .skip(1)
                    .map(Arrays::asList)
                    .map(List::copyOf)
                    .filter(row -> row.stream().anyMatch(s -> !s.isBlank()))
                    .forEach(row -> result.merge(row, 1, Integer::sum));

            return result;
        }
    }

    private Map<List<String>, Integer> mergeRows(Workbook workbook) {
        Map<List<String>, Integer> result = new HashMap<>();
        Sheet sheet = workbook.getSheetAt(0);
        DataFormatter fmt = new DataFormatter();

        for (Row row : sheet) {
            short first = row.getFirstCellNum();
            short last  = row.getLastCellNum();
            if (first < 0 || last <= first) {
                log.debug("Skipping empty row (mergeRows) row={} in sheet={}", row.getRowNum(), sheet.getSheetName());
                continue;
            }
            if (row.getRowNum() == 0) continue;

            List<String> cells = IntStream.range(first, last)
                    .mapToObj(i -> {
                        Cell c = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        return c == null ? "" : fmt.formatCellValue(c);
                    })
                    .toList();
            result.merge(List.copyOf(cells), 1, Integer::sum);
        }
        return result;
    }

    private FileType detect(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) return FileType.EXCEL;
        if (name.endsWith(".csv")) return FileType.CSV;
        throw new IllegalArgumentException("Unsupported file type: " + name);
    }

    public enum FileType {
        EXCEL, CSV
    }
}
