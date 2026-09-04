package ru.feytox.etherology.block.pedestal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalOwnershipBytecodeTest {

    private static final List<String> CLASS_NAMES = List.of(
            "ru/feytox/etherology/block/pedestal/PedestalBlock",
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntity",
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntityRemoval",
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntityRemovalBackend",
            "ru/feytox/etherology/block/pedestal/PedestalDispenserBehavior",
            "ru/feytox/etherology/block/pedestal/PedestalShape",
            "ru/feytox/etherology/util/inventory/ListBackedInventory",
            "ru/feytox/etherology/util/misc/UniqueProvider",
            "ru/feytox/etherology/registry/block/SharedPedestalBlocks",
            "ru/feytox/etherology/registry/item/SharedPedestalBlockItems",
            "ru/feytox/etherology/registry/block/SharedPedestalBlockEntities"
    );

    private static final List<String> FORBIDDEN_DEPENDENCIES = List.of(
            "lombok/",
            "net/fabricmc/",
            "net/minecraftforge/",
            "net/neoforged/",
            "com/llamalad7/mixinextras/",
            "ru/feytox/etherology/registry/block/EBlocks",
            "ru/feytox/etherology/network/interaction/RemoveBlockEntityS2C",
            "ru/feytox/etherology/util/misc/RegistrableBlock",
            "ru/feytox/etherology/util/misc/TickableBlockEntity"
    );

    private static final List<String> OWNED_SOURCES = List.of(
            "ru/feytox/etherology/block/pedestal/PedestalBlock.java",
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntity.java",
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntityRemoval.java",
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntityRemovalBackend.java",
            "ru/feytox/etherology/block/pedestal/PedestalDispenserBehavior.java",
            "ru/feytox/etherology/block/pedestal/PedestalShape.java",
            "ru/feytox/etherology/util/inventory/ListBackedInventory.java",
            "ru/feytox/etherology/util/misc/UniqueProvider.java",
            "ru/feytox/etherology/registry/block/SharedPedestalBlocks.java",
            "ru/feytox/etherology/registry/item/SharedPedestalBlockItems.java",
            "ru/feytox/etherology/registry/block/SharedPedestalBlockEntities.java"
    );

    private static final List<String> PRODUCTION_ROOTS = List.of(
            "common/src/main/java",
            "src/main/java",
            "fabric/src/main/java",
            "forge/src/main/java"
    );

    @Test
    void commonOwnsEachCoreSourceExactlyOnce() {
        Path repositoryRoot = repositoryRoot();

        for (String source : OWNED_SOURCES) {
            List<Path> existingOwners = PRODUCTION_ROOTS.stream()
                    .map(root -> repositoryRoot.resolve(root).resolve(source))
                    .filter(Files::isRegularFile)
                    .toList();

            assertEquals(
                    List.of(repositoryRoot.resolve("common/src/main/java").resolve(source)),
                    existingOwners,
                    source
            );
        }
    }

    @Test
    void sharedSliceHasNoLoaderLombokOrRetiredOwnerDependencies()
            throws IOException {
        for (String className : CLASS_NAMES) {
            String constants = new String(
                    PedestalClassFile.bytes(className),
                    StandardCharsets.ISO_8859_1
            );
            for (String forbiddenDependency : FORBIDDEN_DEPENDENCIES) {
                assertFalse(
                        constants.contains(forbiddenDependency),
                        className + " unexpectedly references " + forbiddenDependency
                );
            }
        }
    }

    @Test
    void sharedSliceContainsNoMixinClassOrAnnotation() throws IOException {
        for (String className : CLASS_NAMES) {
            PedestalClassFile.ClassShape shape = PedestalClassFile.shape(className);
            assertFalse(
                    shape.interfaces().contains("org/spongepowered/asm/mixin/Mixin"),
                    className
            );

            String constants = new String(
                    PedestalClassFile.bytes(className),
                    StandardCharsets.ISO_8859_1
            );
            assertFalse(constants.contains("Lorg/spongepowered/asm/mixin/Mixin;"));
        }
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null
                && !Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }

        assertNotNull(candidate, "Could not find the Etherology repository root");
        assertTrue(Files.isDirectory(candidate.resolve("common/src/main/java")));
        return candidate;
    }
}
