package ru.feytox.etherology.registry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClassFileAssertions {

    private ClassFileAssertions() {
    }

    static void assertContains(String classResource, String... expectedConstants)
            throws IOException {
        String classConstants = readClassConstants(classResource);
        for (String expectedConstant : expectedConstants) {
            assertTrue(
                    classConstants.contains(expectedConstant),
                    classResource + " is missing constant " + expectedConstant
            );
        }
    }

    static void assertDoesNotContain(String classResource, String... excludedConstants)
            throws IOException {
        String classConstants = readClassConstants(classResource);
        for (String excludedConstant : excludedConstants) {
            assertFalse(
                    classConstants.contains(excludedConstant),
                    classResource + " unexpectedly contains constant " + excludedConstant
            );
        }
    }

    static void assertAbsent(String classResource) {
        assertNull(ClassFileAssertions.class.getResource(classResource));
    }

    private static String readClassConstants(String classResource) throws IOException {
        InputStream classStream = ClassFileAssertions.class.getResourceAsStream(classResource);
        assertNotNull(classStream, "Missing class resource " + classResource);
        try (classStream) {
            return new String(classStream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
