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

final class AlchemyRecipeFoundationCrossArtifactTest {

    private static final String CLASS_PREFIX = "ru/feytox/etherology/";
    private static final Set<String> SHARED_FOUNDATION_CLASSES = Set.of(
            CLASS_PREFIX + "recipes/FeyInputRecipe.class",
            CLASS_PREFIX + "recipes/FeyRecipe.class",
            CLASS_PREFIX + "recipes/FeyRecipeSerializer.class",
            CLASS_PREFIX + "recipes/FeyRecipeSerializer$1.class",
            CLASS_PREFIX + "recipes/FeyRecipeJsonProvider.class",
            CLASS_PREFIX + "recipes/RecipeResultComponentBackend.class",
            CLASS_PREFIX + "recipes/RecipeResultComponents.class",
            CLASS_PREFIX + "recipes/RecipeResultComponents$UnavailableBackend.class",
            CLASS_PREFIX + "recipes/alchemy/AlchemyRecipe.class",
            CLASS_PREFIX + "recipes/alchemy/AlchemyRecipeInventory.class",
            CLASS_PREFIX + "recipes/alchemy/AlchemyRecipeSerializer.class"
    );
    private static final String FABRIC_COMPONENT_BACKEND =
            CLASS_PREFIX + "recipes/FabricRecipeResultComponentBackend.class";
    private static final String FABRIC_ENTRYPOINT =
            CLASS_PREFIX + "EtherologyFabric.class";
    private static final String FABRIC_LEGACY_ALCHEMY_BUILDER =
            CLASS_PREFIX + "recipes/alchemy/AlchemyRecipeBuilder.class";
    private static final List<String> LEGACY_SOURCE_PATHS = List.of(
            "src/main/java/ru/feytox/etherology/recipes/FeyInputRecipe.java",
            "src/main/java/ru/feytox/etherology/recipes/FeyRecipe.java",
            "src/main/java/ru/feytox/etherology/recipes/FeyRecipeSerializer.java",
            "src/main/java/ru/feytox/etherology/recipes/FeyRecipeJsonProvider.java",
            "src/main/java/ru/feytox/etherology/recipes/alchemy/AlchemyRecipe.java",
            "src/main/java/ru/feytox/etherology/recipes/alchemy/AlchemyRecipeInventory.java",
            "src/main/java/ru/feytox/etherology/recipes/alchemy/AlchemyRecipeSerializer.java"
    );
    private static final List<String> COMMON_SOURCE_PATHS = List.of(
            "common/src/main/java/ru/feytox/etherology/recipes/FeyInputRecipe.java",
            "common/src/main/java/ru/feytox/etherology/recipes/FeyRecipe.java",
            "common/src/main/java/ru/feytox/etherology/recipes/FeyRecipeSerializer.java",
            "common/src/main/java/ru/feytox/etherology/recipes/FeyRecipeJsonProvider.java",
            "common/src/main/java/ru/feytox/etherology/recipes/"
                    + "RecipeResultComponentBackend.java",
            "common/src/main/java/ru/feytox/etherology/recipes/"
                    + "RecipeResultComponents.java",
            "common/src/main/java/ru/feytox/etherology/recipes/alchemy/"
                    + "AlchemyRecipe.java",
            "common/src/main/java/ru/feytox/etherology/recipes/alchemy/"
                    + "AlchemyRecipeInventory.java",
            "common/src/main/java/ru/feytox/etherology/recipes/alchemy/"
                    + "AlchemyRecipeSerializer.java"
    );
    private static final List<String> FORBIDDEN_SHARED_CONSTANTS = List.of(
            "net/fabricmc/",
            "net/minecraftforge/",
            "lombok/",
            CLASS_PREFIX + "Etherology",
            CLASS_PREFIX + "util/misc/EIdentifier",
            CLASS_PREFIX + "magic/staff/StaffComponent",
            CLASS_PREFIX + "registry/misc/ComponentTypes"
    );

    @Test
    void everyArtifactContainsOneExactSharedAlchemyFoundation()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                for (String foundationClass : SHARED_FOUNDATION_CLASSES) {
                    assertEquals(
                            1,
                            entries.stream().filter(foundationClass::equals).count(),
                            artifact.description() + ":" + foundationClass
                    );
                }
                assertEquals(
                        SHARED_FOUNDATION_CLASSES,
                        entries.stream()
                                .filter(AlchemyRecipeFoundationCrossArtifactTest
                                        ::isFoundationClass)
                                .collect(java.util.stream.Collectors.toSet()),
                        artifact.description()
                );
            }
        }
    }

    @Test
    void sharedFoundationStaysLoaderAndLegacyComponentNeutral()
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
                                        + ":"
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
    void onlyFabricApplicationsContainTheComponentBackendAndLegacyBuilder()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                long backendCount = entries.stream()
                        .filter(FABRIC_COMPONENT_BACKEND::equals)
                        .count();
                long entrypointCount = entries.stream()
                        .filter(FABRIC_ENTRYPOINT::equals)
                        .count();
                long builderCount = entries.stream()
                        .filter(FABRIC_LEGACY_ALCHEMY_BUILDER::equals)
                        .count();
                if (artifact.fabricApplication()) {
                    assertEquals(1, backendCount, artifact.description());
                    assertEquals(1, entrypointCount, artifact.description());
                    assertEquals(1, builderCount, artifact.description());
                } else {
                    assertEquals(0, backendCount, artifact.description());
                    assertEquals(0, entrypointCount, artifact.description());
                    assertEquals(0, builderCount, artifact.description());
                }
            }
        }
    }

    @Test
    void sourceOwnershipIsCommonWithOneNarrowFabricBackend()
            throws IOException {
        Path repositoryRoot = requiredPath(
                "etherology.alchemyRecipeFoundation.repositoryRoot"
        );
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
        for (String relativePath : COMMON_SOURCE_PATHS) {
            Path commonPath = repositoryRoot.resolve(relativePath).normalize();
            assertTrue(commonPath.startsWith(repositoryRoot), commonPath.toString());
            assertTrue(
                    Files.isRegularFile(commonPath, LinkOption.NOFOLLOW_LINKS),
                    commonPath.toString()
            );
            assertFalse(Files.isSymbolicLink(commonPath), commonPath.toString());
        }

        Path fabricBackend = repositoryRoot.resolve(
                "fabric/src/main/java/ru/feytox/etherology/recipes/"
                        + "FabricRecipeResultComponentBackend.java"
        ).normalize();
        assertTrue(fabricBackend.startsWith(repositoryRoot), fabricBackend.toString());
        assertTrue(
                Files.isRegularFile(fabricBackend, LinkOption.NOFOLLOW_LINKS),
                fabricBackend.toString()
        );
        assertFalse(Files.isSymbolicLink(fabricBackend), fabricBackend.toString());
    }

    private static boolean isFoundationClass(String entry) {
        return entry.equals(CLASS_PREFIX + "recipes/FeyInputRecipe.class")
                || entry.equals(CLASS_PREFIX + "recipes/FeyRecipe.class")
                || entry.equals(CLASS_PREFIX + "recipes/FeyRecipeJsonProvider.class")
                || entry.equals(CLASS_PREFIX
                        + "recipes/RecipeResultComponentBackend.class")
                || entry.equals(CLASS_PREFIX + "recipes/FeyRecipeSerializer.class")
                || entry.startsWith(CLASS_PREFIX + "recipes/FeyRecipeSerializer$")
                || entry.equals(CLASS_PREFIX + "recipes/RecipeResultComponents.class")
                || entry.startsWith(CLASS_PREFIX + "recipes/RecipeResultComponents$")
                || entry.equals(CLASS_PREFIX
                        + "recipes/alchemy/AlchemyRecipe.class")
                || entry.startsWith(CLASS_PREFIX
                        + "recipes/alchemy/AlchemyRecipe$")
                || entry.equals(CLASS_PREFIX
                        + "recipes/alchemy/AlchemyRecipeInventory.class")
                || entry.startsWith(CLASS_PREFIX
                        + "recipes/alchemy/AlchemyRecipeInventory$")
                || entry.equals(CLASS_PREFIX
                        + "recipes/alchemy/AlchemyRecipeSerializer.class")
                || entry.startsWith(CLASS_PREFIX
                        + "recipes/alchemy/AlchemyRecipeSerializer$");
    }

    private static byte[] readEntry(JarFile jar, String entryName)
            throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        assertNotNull(entry, entryName);
        try (var input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static Path requiredPath(String propertyName) throws IOException {
        String value = System.getProperty(propertyName);
        assertNotNull(value, propertyName);
        Path path = Path.of(value);
        assertTrue(Files.exists(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
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
                artifact(
                        "fabricProductionJar",
                        "Fabric remapped production JAR",
                        true
                ),
                artifact("forgeShadowJar", "Forge shadow JAR", false)
        );
    }

    private static Artifact artifact(
            String suffix,
            String description,
            boolean fabricApplication
    ) throws IOException {
        Path path = requiredPath(
                "etherology.alchemyRecipeFoundation." + suffix
        );
        assertTrue(
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                path.toString()
        );
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return new Artifact(path, description, fabricApplication);
    }

    private record Artifact(
            Path path,
            String description,
            boolean fabricApplication
    ) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
