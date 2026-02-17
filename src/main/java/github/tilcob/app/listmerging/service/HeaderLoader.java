package github.tilcob.app.listmerging.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HeaderLoader {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Integer, List<String>> headers = new HashMap<>();

    public HeaderLoader() throws IOException {
        this(Path.of("src/main/resources/headers"));
    }

    public HeaderLoader(Path indexPath) throws IOException {
        load(indexPath);
    }

    private void load(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) throw new IllegalArgumentException("Path is not a directory: " + folder);

        try (var stream = Files.list(folder)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .filter(path -> !path.getFileName().toString().equalsIgnoreCase("index.json"))
                .forEach(path -> {
                    try {
                        List<String> value = mapper.readValue(
                                path.toFile(),
                                new TypeReference<>() {}
                        );
                        headers.put(value.size(), value);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
    }

    public List<String> getHeaders(int index) {
        return headers.containsKey(index)
                ? Collections.unmodifiableList(headers.get(index))
                : List.of();
    }
}
