package dev.theplumteam.etherology.e2e.fabric;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StorageUtilitiesScenarioTest {

    @Test
    void packagedScenarioUsesRegistryIdsWithoutProductionClassLinkage() throws IOException {
        String resourceName = StorageUtilitiesScenario.class.getSimpleName() + ".class";
        byte[] classBytes;
        try (InputStream input = StorageUtilitiesScenario.class.getResourceAsStream(resourceName)) {
            assertNotNull(input);
            classBytes = input.readAllBytes();
        }

        String classConstants = new String(classBytes, StandardCharsets.ISO_8859_1);
        assertFalse(classConstants.contains("ru/feytox/etherology/"));
        assertTrue(classConstants.contains(StorageUtilitiesScenario.SCENARIO_ID));
        assertTrue(classConstants.contains("crate"));
        assertTrue(classConstants.contains("shelf_slab"));
        assertTrue(classConstants.contains("spill_barrel"));
        assertTrue(classConstants.contains("tuning_fork"));
    }

    @Test
    void scenarioOwnsDistinctSaveAndBeforeAfterEvidenceNames() {
        assertTrue(StorageUtilitiesScenario.WORLD_DIRECTORY_NAME.contains("storage-utilities"));
        assertTrue(StorageUtilitiesScenario.BEFORE_SCREENSHOT_FILE_NAME.endsWith("-before.png"));
        assertTrue(StorageUtilitiesScenario.AFTER_SCREENSHOT_FILE_NAME.endsWith("-after.png"));
    }
}
