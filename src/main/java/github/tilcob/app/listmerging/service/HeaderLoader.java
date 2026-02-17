package github.tilcob.app.listmerging.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.tilcob.app.listmerging.model.HeaderDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeaderLoader {
    private static final Logger log = LoggerFactory.getLogger(HeaderLoader.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<HeaderDefinition> headers = new ArrayList<>();

    public HeaderLoader() {
        this(Path.of("src/main/resources/headers"));
    }

    public HeaderLoader(Path indexPath) {
        try {
            load(indexPath);
        } catch (IOException e) {
            log.error("Error loading headers", e);
            throw new RuntimeException(e);
        }
    }

    private void load(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) throw new IllegalArgumentException("Path is not a directory: " + folder);

        try (var stream = Files.list(folder)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            HeaderDefinition def = mapper.readValue(p.toFile(), HeaderDefinition.class);
                            if (def.name() == null || def.name().isBlank())
                                throw new IllegalArgumentException("Missing name in " + p.getFileName());
                            if (def.headers() == null || def.headers().isEmpty())
                                throw new IllegalArgumentException("Missing headers in " + p.getFileName());
                            headers.add(new HeaderDefinition(def.name(), List.copyOf(def.headers())));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    public List<HeaderDefinition> getHeaders() {
        return Collections.unmodifiableList(headers);
    }
}
