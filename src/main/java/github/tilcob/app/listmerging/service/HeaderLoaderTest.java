package github.tilcob.app.listmerging.service;

import github.tilcob.app.listmerging.model.HeaderDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HeaderLoaderTest {

    @Test
    void canStartWhenExternalHeaderFolderDoesNotExist() {
        assertDoesNotThrow(() -> new HeaderLoader(Path.of("does-not-exist")));
    }

    @Test
    void externalHeadersOverrideBundledHeadersWithSameName(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("override.json"), """
                {
                  \"name\": \"Test\",
                  \"headers\": [\"External A\", \"External B\"]
                }
                """);

        HeaderLoader loader = new HeaderLoader(tempDir);

        HeaderDefinition testHeader = loader.getHeaders().stream()
                .filter(h -> h.name().equals("Test"))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("External A", "External B"), testHeader.headers());
    }
}