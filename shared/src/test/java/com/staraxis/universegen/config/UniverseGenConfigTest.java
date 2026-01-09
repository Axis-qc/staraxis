package com.staraxis.universegen.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UniverseGenConfigTest {

    @Test
    void load_from_json_overwrites_defaults() throws IOException {
        // Create a temporary JSON file to test loading
        String json = """
        {
          "seed": 999,
          "galaxyRadiusR": 10,
          "hexRadiusLy": 1.0,
          "starToDeepSpaceRatio": 0.5,
          "contentRatios": {
            "star-system": 0.8,
            "deep_space": 0.2
          },
          "contentTypeRegistry": {
            "definitions": {
              "star-system": { "typeId": "star-system", "displayNameZh": "恒星系" },
              "deep_space": { "typeId": "deep_space", "displayNameZh": "真空" }
            }
          },
          "galaxyPresets": []
        }
        """;
        Path tempFile = Files.createTempFile("test-config", ".json");
        Files.writeString(tempFile, json);

        try {
            // The validation requires ratio sum to be 1.0, so this test will fail if validation is on.
            // Let's adjust the test to pass validation.
            // Re-write the same JSON (确保包含 starToDeepSpaceRatio / contentRatios / registry)。
            Files.writeString(tempFile, json);

            UniverseGenConfig config = UniverseGenConfig.load(tempFile.toFile(), null);

            assertEquals(999, config.getSeed());
            assertEquals(10, config.getGalaxyRadiusR());
            assertEquals(2, config.getContentRatios().size());
            assertEquals(0.8, config.getContentRatios().get("star-system"));
            assertEquals(2, config.getContentTypeRegistry().getDefinitions().size());
            assertEquals("真空", config.getContentTypeRegistry().getDefinitions().get("deep_space").getDisplayNameZh());

        } finally {
            Files.delete(tempFile);
        }
    }
}
