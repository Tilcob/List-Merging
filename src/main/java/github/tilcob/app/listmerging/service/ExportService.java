package github.tilcob.app.listmerging.service;

import github.tilcob.app.listmerging.model.AggregationResult;
import github.tilcob.app.listmerging.model.HeaderDefinition;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class ExportService {
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    public File export(Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged,
                       String outputPath) throws IOException {

        File exportFile = new File(outputPath, "merged.xlsx");

        try (Workbook workbook = new XSSFWorkbook();
             OutputStream out = new FileOutputStream(exportFile)) {

            var groups = merged.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().name(), String.CASE_INSENSITIVE_ORDER))
                    .toList();

            for (var group : groups) {
                HeaderDefinition headerDef = group.getKey();
                Map<List<String>, AggregationResult> rows = group.getValue();

                String sheetName = safeSheetName(headerDef.name());
                Sheet sheet = workbook.createSheet(sheetName);
                int sumColumnIndex = findSumColumnIndex(headerDef);

                int r = 0;
                if (headerDef.headers() != null && !headerDef.headers().isEmpty()) {
                    Row headerRow = sheet.createRow(r++);
                    int c = 0;
                    for (int i = 0; i < headerDef.headers().size(); i++) {
                        if (i == sumColumnIndex) {
                            continue;
                        }
                        String h = headerDef.headers().get(i);
                        headerRow.createCell(c++).setCellValue(h == null ? "" : h);
                    }
                    headerRow.createCell(c++).setCellValue("Count");
                    if (sumColumnIndex >= 0) {
                        headerRow.createCell(c).setCellValue(headerDef.sumColumn());
                    }
                }

                var sortedRows = rows.entrySet().stream()
                        .sorted((a, b) -> b.getValue().sumValue().compareTo(a.getValue().sumValue()))
                        .toList();

                for (var rowEntry : sortedRows) {
                    List<String> values = rowEntry.getKey();
                    AggregationResult aggregation = rowEntry.getValue();

                    Row row = sheet.createRow(r++);
                    int c = 0;
                    for (String v : values) {
                        row.createCell(c++).setCellValue(v == null ? "" : v);
                    }
                    row.createCell(c++).setCellValue(aggregation.rowCount());
                    if (sumColumnIndex >= 0) {
                        row.createCell(c).setCellValue(aggregation.sumValue().doubleValue());
                    }
                }
            }

            workbook.write(out);
        }
        return exportFile;
    }

    private int findSumColumnIndex(HeaderDefinition headerDefinition) {
        if (headerDefinition.sumColumn() == null || headerDefinition.sumColumn().isBlank() ||
                headerDefinition.headers() == null) {
            return -1;
        }

        return IntStream.range(0, headerDefinition.headers().size())
                .filter(i -> headerDefinition.sumColumn().equalsIgnoreCase(headerDefinition.headers().get(i)))
                .findFirst()
                .orElse(-1);
    }

    private static String safeSheetName(String name) {
        String cleaned = (name == null ? "" : name).trim();
        cleaned = cleaned.replaceAll("[:\\\\/?*\\[\\]]", "_");
        if (cleaned.isBlank()) cleaned = "Sheet";
        if (cleaned.length() > 31) cleaned = cleaned.substring(0, 31);
        return cleaned;
    }
}
