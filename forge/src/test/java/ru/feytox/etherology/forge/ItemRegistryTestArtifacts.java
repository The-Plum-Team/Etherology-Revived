package ru.feytox.etherology.forge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static ru.feytox.etherology.forge.PedestalBytecodeAssertions.requireRegularFile;

final class ItemRegistryTestArtifacts {

    private ItemRegistryTestArtifacts() {
    }

    static List<Artifact> load(String propertyPrefix) throws IOException {
        return List.of(
                artifact(propertyPrefix, "commonJar", false, false),
                artifact(propertyPrefix, "fabricTransformedCommonJar", false, false),
                artifact(propertyPrefix, "forgeTransformedCommonJar", false, false),
                artifact(propertyPrefix, "fabricDevelopmentJar", true, true),
                artifact(propertyPrefix, "fabricProductionJar", true, true),
                artifact(propertyPrefix, "forgeShadowJar", true, false)
        );
    }

    private static Artifact artifact(
            String prefix, String suffix, boolean includesAssets, boolean fabricApplication
    ) throws IOException {
        String property = prefix + "." + suffix;
        String value = System.getProperty(property);
        assertNotNull(value, property);
        return new Artifact(
                requireRegularFile(Path.of(value)), suffix, includesAssets, fabricApplication
        );
    }

    record Artifact(
            Path path, String description, boolean includesAssets, boolean fabricApplication
    ) {
        JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
