package github.tilcob.app.listmerging.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.tilcob.app.listmerging.model.HeaderDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HeaderLoader {
    private static final Logger log = LoggerFactory.getLogger(HeaderLoader.class);
    private static final String BUNDLED_HEADERS_ROOT = "headers";

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<HeaderDefinition> headers = new ArrayList<>();

    public HeaderLoader() {
        this(Path.of("headers"));
    }

    public HeaderLoader(Path externalFolder) {
        try {
            loadBundledHeaders();
            loadExternalHeaders(externalFolder);
        } catch (IOException e) {
            log.error("Error loading headers", e);
            throw new RuntimeException(e);
        }
    }

    private void loadBundledHeaders() throws IOException {
        String indexResource = BUNDLED_HEADERS_ROOT + "/index.json";
        InputStream indexStream = HeaderLoader.class.getClassLoader().getResourceAsStream(indexResource);
        if (indexStream == null) {
            log.warn("No bundled header index found at {}", indexResource);
            return;
        }

        List<String> headerFiles;
        try (indexStream) {
            headerFiles = mapper.readValue(indexStream, new TypeReference<>() { });
        }

        for (String fileName : headerFiles) {
            String resourcePath = BUNDLED_HEADERS_ROOT + "/" + fileName;
            try (InputStream stream = HeaderLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    log.warn("Bundled header file listed in index but not found: {}", resourcePath);
                    continue;
                }
                HeaderDefinition definition = mapper.readValue(stream, HeaderDefinition.class);
                headers.add(validate(definition, resourcePath));
            }
        }
    }

    private void loadExternalHeaders(Path folder) throws IOException {
        if (folder == null || !Files.isDirectory(folder)) {
            log.info("No external headers directory found at {} (optional)", folder);
            return;
        }

        Map<String, HeaderDefinition> overrides = new LinkedHashMap<>();

        try (var stream = Files.list(folder)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> !p.getFileName().toString().equalsIgnoreCase("index.json"))
                .sorted()
                .forEach(path -> {
                    try {
                        HeaderDefinition definition = mapper.readValue(path.toFile(), HeaderDefinition.class);
                        HeaderDefinition validated = validate(definition, path.toString());
                        overrides.put(validated.name(), validated);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
        if (!overrides.isEmpty()) {
            headers.removeIf(header -> overrides.containsKey(header.name()));
            headers.addAll(overrides.values());
        }
    }

    private HeaderDefinition validate(HeaderDefinition definition, String source) {
        if (definition.name() == null || definition.name().isBlank()) {
            throw new IllegalArgumentException("Missing name in " + source);
        }
        if (definition.headers() == null || definition.headers().isEmpty()) {
            throw new IllegalArgumentException("Missing headers in " + source);
        }
        return new HeaderDefinition(definition.name(), List.copyOf(definition.headers()));
    }

    public List<HeaderDefinition> getHeaders() {
        return Collections.unmodifiableList(headers);
    }
}
