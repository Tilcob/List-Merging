package github.tilcob.app.listmerging.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.tilcob.app.listmerging.model.*;
import github.tilcob.app.listmerging.service.ExportService;
import github.tilcob.app.listmerging.service.HeaderLoader;
import github.tilcob.app.listmerging.service.MergeService;
import github.tilcob.app.listmerging.service.MergeValidationService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MergeExportTask extends Task<File> {
    private static final Logger log = LoggerFactory.getLogger(MergeExportTask.class);
    private static final int VALIDATION_MESSAGE_LIMIT = 3;

    private final List<File> files;
    private final File outputDir;
    private final HeaderLoader headerLoader;
    private final MergeService mergeService;
    private final ExportService exportService;
    private final MergeValidationService mergeValidationService;
    private final ValidationContext validationContext;
    private final boolean continueOnValidationErrors;
    private final boolean writeValidationReportFile;
    private final ObjectMapper objectMapper;

    private volatile String validationSummary = "";
    private volatile File validationReportFile;

    public MergeExportTask(List<File> files,
                           File outputDir,
                           HeaderLoader headerLoader,
                           MergeService mergeService,
                           ExportService exportService) {
        this(files,
                outputDir,
                headerLoader,
                mergeService,
                exportService,
                new MergeValidationService(),
                ValidationContext.defaults(),
                false,
                false,
                new ObjectMapper());
    }

    public MergeExportTask(List<File> files,
                           File outputDir,
                           HeaderLoader headerLoader,
                           MergeService mergeService,
                           ExportService exportService,
                           MergeValidationService mergeValidationService,
                           ValidationContext validationContext,
                           boolean continueOnValidationErrors,
                           boolean writeValidationReportFile,
                           ObjectMapper objectMapper) {
        this.files = files;
        this.outputDir = outputDir;
        this.headerLoader = headerLoader;
        this.mergeService = mergeService;
        this.exportService = exportService;
        this.mergeValidationService = mergeValidationService;
        this.validationContext = validationContext;
        this.continueOnValidationErrors = continueOnValidationErrors;
        this.writeValidationReportFile = writeValidationReportFile;
        this.objectMapper = objectMapper;
    }

    @Override
    protected File call() throws Exception {
        updateProgress(0, 4);
        updateMessage("Loading headers...");
        List<HeaderDefinition> headers = headerLoader.getHeaders();

        updateProgress(1, 4);
        updateMessage("Merging files...");
        Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged =
                mergeService.merge(files, headers);

        updateProgress(2, 4);
        updateMessage("Validating merge result...");
        ValidationReport validationReport = runValidation(merged, headers);
        validationSummary = toStatusSummary(validationReport);

        if (writeValidationReportFile) {
            validationReportFile = writeValidationReport(outputDir.toPath(), validationReport);
            log.info("Validation report written to {}", validationReportFile.getAbsolutePath());
        }

        if (!validationReport.valid()) {
            if (continueOnValidationErrors) {
                updateMessage(validationSummary + " – continuing in warning mode.");
            } else {
                throw new IllegalStateException(validationSummary + " Export aborted.");
            }
        }

        updateProgress(3, 4);
        updateMessage("Exporting...");
        File outFile = exportService.export(merged, outputDir.getPath());

        updateProgress(4, 4);
        updateMessage("Done: " + outFile.getName() + " | " + validationSummary);
        return outFile;
    }

    public String getValidationSummary() {
        return validationSummary;
    }

    public File getValidationReportFile() {
        return validationReportFile;
    }

    private ValidationReport runValidation(Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged,
                                           List<HeaderDefinition> headers) {
        try {
            ValidationReport report = mergeValidationService.validate(merged, validationContext, files, headers);
            log.info("Merge validation completed: valid={}, issues={}", report.valid(), report.issues().size());
            return report;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Validation failed. Export is aborted for safety reasons.", ex);
        }
    }

    private File writeValidationReport(Path targetDir, ValidationReport report) throws IOException {
        File reportFile = targetDir.resolve("merged.validation.json").toFile();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportFile, report);
        return reportFile;
    }

    private String toStatusSummary(ValidationReport report) {
        if (report == null) {
            return "Validation: no result available.";
        }
        if (report.valid()) {
            return "Validation: OK (0 issues).";
        }

        String topIssues = report.issues().stream()
                .limit(VALIDATION_MESSAGE_LIMIT)
                .map(this::toReadableIssue)
                .collect(Collectors.joining(" | "));
        String suffix = report.issues().size() > VALIDATION_MESSAGE_LIMIT ? " | …" : "";
        return "Validation: ERROR (" + report.issues().size() + " issues). " + topIssues + suffix;
    }

    private String toReadableIssue(ValidationIssue issue) {
        String header = issue.headerName() == null || issue.headerName().isBlank()
                ? ""
                : " [" + issue.headerName() + "]";
        return issue.code() + header + ": " + issue.message();
    }
}
