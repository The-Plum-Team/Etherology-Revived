package ru.feytox.etherology.forge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeResourceAssertions {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89,
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
    };

    private RuntimeResourceAssertions() {
    }

    static void assertTextContains(String path, String... expectedValues) throws IOException {
        try (InputStream resource = requiredResource(path)) {
            String resourceText = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            for (String expectedValue : expectedValues) {
                assertTrue(
                        resourceText.contains(expectedValue),
                        path + " is missing " + expectedValue
                );
            }
        }
    }

    static void assertPng(String path) throws IOException {
        try (InputStream resource = requiredResource(path)) {
            assertArrayEquals(PNG_SIGNATURE, resource.readNBytes(PNG_SIGNATURE.length));
        }
    }

    static void assertAbsent(String path) {
        assertNull(
                RuntimeResourceAssertions.class.getResource(path),
                "Forge packaged an unaccepted runtime resource " + path
        );
    }

    private static InputStream requiredResource(String path) {
        InputStream resource = RuntimeResourceAssertions.class.getResourceAsStream(path);
        assertNotNull(resource, "Missing Forge runtime resource " + path);
        return resource;
    }
}
