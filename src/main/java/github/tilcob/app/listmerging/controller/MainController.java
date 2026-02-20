package github.tilcob.app.listmerging.controller;

import github.tilcob.app.listmerging.service.ExportService;
import github.tilcob.app.listmerging.service.HeaderLoader;
import github.tilcob.app.listmerging.service.MergeService;
import github.tilcob.app.listmerging.service.MergeValidationService;
import github.tilcob.app.listmerging.tasks.MergeExportTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class MainController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private HeaderLoader loader;
    private final MergeService mergeService = new MergeService();
    private final ExportService exportService = new ExportService();
    private final MergeValidationService mergeValidationService = new MergeValidationService();

    @FXML
    private Label outputLabel;
    @FXML
    private Button mergeButton;
    @FXML
    private ProgressBar progressBar;

    @FXML
    private void initialize() {
        progressBar.setProgress(0);
        progressBar.setVisible(false);
    }

    @FXML
    protected void onButtonMergeClick() {
        FileChooser chooser = createFileChooser("Select files to merge");
        List<File> files = chooser.showOpenMultipleDialog(getCurrentWindow());

        if (files == null || files.isEmpty()) {
            outputLabel.setText("No files selected");
            return;
        }

        File outDir = files.get(0).getParentFile();
        if (loader == null) loader = new HeaderLoader();
        MergeExportTask task = new MergeExportTask(
                files,
                outDir,
                loader,
                mergeService,
                exportService,
                mergeValidationService,
                null,
                isValidationWarningModeEnabled(),
                shouldWriteValidationReport(),
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        outputLabel.textProperty().bind(task.messageProperty());

        if (mergeButton != null) {
            mergeButton.disableProperty().bind(task.runningProperty());
        }

        task.setOnRunning(e -> {
            progressBar.setVisible(true);
            progressBar.progressProperty().bind(task.progressProperty());
        });

        task.setOnSucceeded(e -> {
            outputLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            progressBar.setVisible(false);

            File exported = task.getValue();
            String reportInfo = task.getValidationReportFile() == null
                    ? ""
                    : " | Report: " + task.getValidationReportFile().getAbsolutePath();
            outputLabel.setText("Export created: " + exported.getAbsolutePath() + " | " + task.getValidationSummary() + reportInfo);
        });

        task.setOnFailed(e -> {
            outputLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            progressBar.setVisible(false);

            Throwable ex = task.getException();
            outputLabel.setText(ex == null
                    ? "Error during merge/export."
                    : ex.getMessage());
            log.error("Merge/export failed", ex);
        });

        Thread t = new Thread(task, "merge-export-task");
        t.setDaemon(true);
        t.start();
    }

    private FileChooser createFileChooser(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel", "*.xls", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV", "*.csv")
        );
        return fileChooser;
    }

    private Window getCurrentWindow() {
        return outputLabel.getScene().getWindow();
    }

    private boolean isValidationWarningModeEnabled() {
        return Boolean.parseBoolean(System.getProperty("listmerging.validation.warn-mode", "false"));
    }

    private boolean shouldWriteValidationReport() {
        return Boolean.parseBoolean(System.getProperty("listmerging.validation.write-report", "true"));
    }
}
