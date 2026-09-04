package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensFoundationCrossArtifactTest {

    private static final String CLASS_PREFIX = "ru/feytox/etherology/";
    private static final Set<String> SHARED_FOUNDATION_CLASSES = Set.of(
            CLASS_PREFIX + "magic/staff/StaffPattern.class",
            CLASS_PREFIX + "magic/staff/StaffPattern$EmptyPattern.class",
            CLASS_PREFIX + "magic/staff/StaffLenses.class",
            CLASS_PREFIX + "magic/lens/LensComponent.class",
            CLASS_PREFIX + "magic/lens/LensDataKeys.class",
            CLASS_PREFIX + "item/LensRuntimeBackend.class",
            CLASS_PREFIX + "item/LensRuntime.class",
            CLASS_PREFIX + "item/LensRuntime$UnavailableBackend.class",
            CLASS_PREFIX + "item/LensItem.class",
            CLASS_PREFIX + "item/LensItem$1.class",
            CLASS_PREFIX + "item/UnadjustedLens.class"
    );
    private static final String FABRIC_ADAPTER =
            CLASS_PREFIX + "item/FabricLensRuntimeBackend.class";
    private static final String FABRIC_COMPONENT_TYPES =
            CLASS_PREFIX + "registry/misc/ComponentTypes.class";
    private static final String FABRIC_ENTRYPOINT =
            CLASS_PREFIX + "EtherologyFabric.class";

    private static final List<String> LEGACY_SOURCE_PATHS = List.of(
            "src/main/java/ru/feytox/etherology/magic/staff/StaffPattern.java",
            "src/main/java/ru/feytox/etherology/magic/staff/StaffLenses.java",
            "src/main/java/ru/feytox/etherology/magic/lens/LensComponent.java",
            "src/main/java/ru/feytox/etherology/item/LensItem.java",
            "src/main/java/ru/feytox/etherology/item/UnadjustedLens.java"
    );

    private static final List<String> FORBIDDEN_SHARED_CONSTANTS = List.of(
            "net/fabricmc/",
            "net/minecraftforge/",
            "dev/onyxstudios/",
            "ru/feytox/etherology/magic/ether/EtherComponent",
            "ru/feytox/etherology/item/StaffItem",
            "ru/feytox/etherology/magic/staff/StaffComponent",
            "ru/feytox/etherology/magic/staff/StaffPartInfo",
            "ru/feytox/etherology/registry/misc/ComponentTypes",
            "ru/feytox/etherology/util/misc/ItemComponent"
    );

    @Test
    void everyArtifactContainsOneExactSharedFoundationInventory()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                for (String foundationClass : SHARED_FOUNDATION_CLASSES) {
                    assertEquals(
                            1,
                            entries.stream().filter(foundationClass::equals).count(),
                            artifact.description() + ": " + foundationClass
                    );
                }

                Set<String> actualInventory = entries.stream()
                        .filter(LensFoundationCrossArtifactTest::isFoundationClass)
                        .collect(java.util.stream.Collectors.toSet());
                assertEquals(
                        SHARED_FOUNDATION_CLASSES,
                        actualInventory,
                        artifact.description()
                );
            }
        }
    }

    @Test
    void sharedFoundationStaysLoaderNeutralInEveryArtifact()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                for (String classEntry : SHARED_FOUNDATION_CLASSES) {
                    String constants = new String(
                            readEntry(jar, classEntry),
                            StandardCharsets.ISO_8859_1
                    );
                    for (String forbidden : FORBIDDEN_SHARED_CONSTANTS) {
                        assertFalse(
                                constants.contains(forbidden),
                                artifact.description()
                                        + ": "
                                        + classEntry
                                        + " references "
                                        + forbidden
                        );
                    }
                }
            }
        }
    }

    @Test
    void onlyFabricArtifactsPackageTheAdapterAndLegacyAliases()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                long adapterCount = entries.stream().filter(FABRIC_ADAPTER::equals).count();
                long componentTypesCount = entries.stream()
                        .filter(FABRIC_COMPONENT_TYPES::equals)
                        .count();
                long entrypointCount = entries.stream()
                        .filter(FABRIC_ENTRYPOINT::equals)
                        .count();

                if (artifact.fabricApplication()) {
                    assertEquals(1, adapterCount, artifact.description());
                    assertEquals(1, componentTypesCount, artifact.description());
                    assertEquals(1, entrypointCount, artifact.description());
                } else {
                    assertEquals(0, adapterCount, artifact.description());
                    assertEquals(0, componentTypesCount, artifact.description());
                    assertEquals(0, entrypointCount, artifact.description());
                }
            }
        }
    }

    @Test
    void legacySourceRootNoLongerOwnsAnyMovedFqn() throws IOException {
        Path repositoryRoot = requiredPath("etherology.lensFoundation.repositoryRoot");
        assertTrue(Files.isDirectory(repositoryRoot, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(repositoryRoot));

        for (String relativePath : LEGACY_SOURCE_PATHS) {
            Path legacyPath = repositoryRoot.resolve(relativePath).normalize();
            assertTrue(legacyPath.startsWith(repositoryRoot), legacyPath.toString());
            assertFalse(
                    Files.exists(legacyPath, LinkOption.NOFOLLOW_LINKS),
                    legacyPath.toString()
            );
        }
    }

    private static boolean isFoundationClass(String entry) {
        return entry.equals(CLASS_PREFIX + "magic/staff/StaffPattern.class")
                || entry.startsWith(CLASS_PREFIX + "magic/staff/StaffPattern$")
                || entry.equals(CLASS_PREFIX + "magic/staff/StaffLenses.class")
                || entry.equals(CLASS_PREFIX + "magic/lens/LensComponent.class")
                || entry.equals(CLASS_PREFIX + "magic/lens/LensDataKeys.class")
                || entry.equals(CLASS_PREFIX + "item/LensRuntimeBackend.class")
                || entry.equals(CLASS_PREFIX + "item/LensRuntime.class")
                || entry.startsWith(CLASS_PREFIX + "item/LensRuntime$")
                || entry.equals(CLASS_PREFIX + "item/LensItem.class")
                || entry.startsWith(CLASS_PREFIX + "item/LensItem$")
                || entry.equals(CLASS_PREFIX + "item/UnadjustedLens.class");
    }

    private static byte[] readEntry(JarFile jar, String entryName) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        assertNotNull(entry, entryName);
        try (var input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static List<Artifact> artifacts() throws IOException {
        return List.of(
                artifact("commonJar", "common JAR", false),
                artifact(
                        "fabricTransformedCommonJar",
                        "Fabric-transformed common JAR",
                        false
                ),
                artifact(
                        "forgeTransformedCommonJar",
                        "Forge-transformed common JAR",
                        false
                ),
                artifact("fabricDevelopmentJar", "Fabric development JAR", true),
                artifact("fabricProductionJar", "Fabric remapped production JAR", true),
                artifact("forgeShadowJar", "Forge shadow JAR", false)
        );
    }

    private static Artifact artifact(
            String suffix,
            String description,
            boolean fabricApplication
    ) throws IOException {
        Path path = requiredPath("etherology.lensFoundation." + suffix);
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return new Artifact(path, description, fabricApplication);
    }

    private static Path requiredPath(String propertyName) throws IOException {
        String value = System.getProperty(propertyName);
        assertNotNull(value, propertyName);
        Path path = Path.of(value);
        assertTrue(Files.exists(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private record Artifact(Path path, String description, boolean fabricApplication) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
