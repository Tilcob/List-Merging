package github.tilcob.app.listmerging.controller;

import github.tilcob.app.listmerging.service.HeaderLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

public class MainController {
    @FXML
    private Label outputLabel;

    @FXML
    protected void onButtonMergeClick() {
        FileChooser fileChooser = createFileChooser("Select files to merge");
        List<File> files = fileChooser.showOpenMultipleDialog(getCurrentWindow());
        if (Objects.isNull(files) || files.isEmpty()) {
            outputLabel.setText("No files selected");
            return;
        }
        try {
            HeaderLoader loader = new HeaderLoader();
        } catch (IOException e) {
            outputLabel.setText("Error loading headers");
            throw new UncheckedIOException(e);
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
