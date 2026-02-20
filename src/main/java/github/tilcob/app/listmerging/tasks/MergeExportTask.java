package github.tilcob.app.listmerging.tasks;

import github.tilcob.app.listmerging.model.AggregationResult;
import github.tilcob.app.listmerging.model.HeaderDefinition;
import github.tilcob.app.listmerging.service.ExportService;
import github.tilcob.app.listmerging.service.HeaderLoader;
import github.tilcob.app.listmerging.service.MergeService;
import javafx.concurrent.Task;

import java.io.File;
import java.util.List;
import java.util.Map;

public class MergeExportTask extends Task<File> {
    private final List<File> files;
    private final File outputDir;
    private final HeaderLoader headerLoader;
    private final MergeService mergeService;
    private final ExportService exportService;

    public MergeExportTask(List<File> files,
                           File outputDir,
                           HeaderLoader headerLoader,
                           MergeService mergeService,
                           ExportService exportService) {
        this.files = files;
        this.outputDir = outputDir;
        this.headerLoader = headerLoader;
        this.mergeService = mergeService;
        this.exportService = exportService;
    }

    @Override
    protected File call() throws Exception {
        updateProgress(0, 3);
        updateMessage("Loading headers...");
        List<HeaderDefinition> headers = headerLoader.getHeaders();

        updateProgress(1, 3);
        updateMessage("Merging files...");
        Map<HeaderDefinition, Map<List<String>, AggregationResult>> merged =
                mergeService.merge(files, headers);

        updateProgress(2, 3);
        updateMessage("Exporting...");
        File outFile = exportService.export(merged, outputDir.getPath());

        updateProgress(3, 3);
        updateMessage("Done: " + outFile.getName());
        return outFile;
    }
}
