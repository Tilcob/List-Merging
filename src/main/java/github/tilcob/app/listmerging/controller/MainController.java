package github.tilcob.app.listmerging.controller;

import com.opencsv.exceptions.CsvValidationException;
import github.tilcob.app.listmerging.service.HeaderLoader;
import github.tilcob.app.listmerging.service.MergeService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML
    private Label outputLabel;

    @FXML
    protected void onButtonMergeClick() {
        FileChooser fileChooser = createFileChooser("Select files to merge");
        MergeService mergeService = new MergeService();
        Map<List<String>, Integer> merged;

        List<File> files = fileChooser.showOpenMultipleDialog(getCurrentWindow());

        if (Objects.isNull(files) || files.isEmpty()) {
            outputLabel.setText("No files selected");
            return;
        }
        try {
            merged = Map.copyOf(mergeService.merge(files));
        } catch (Exception e) {
            outputLabel.setText("Error loading headers");
            log.error("Error loading headers: ", e);
        }
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
}
