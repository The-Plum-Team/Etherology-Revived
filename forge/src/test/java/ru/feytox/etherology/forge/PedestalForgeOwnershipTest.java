package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalForgeOwnershipTest {

    private static final String PEDESTAL_PACKAGE =
            "ru/feytox/etherology/block/pedestal/";
    private static final String SHARED_BLOCKS =
            "ru/feytox/etherology/registry/block/SharedPedestalBlocks";
    private static final String SHARED_ITEMS =
            "ru/feytox/etherology/registry/item/SharedPedestalBlockItems";
    private static final String SHARED_BLOCK_ENTITIES =
            "ru/feytox/etherology/registry/block/SharedPedestalBlockEntities";
    private static final String SHARED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final List<String> MOVED_COMMON_CLASSES = List.of(
            PEDESTAL_PACKAGE + "PedestalBlock.class",
            PEDESTAL_PACKAGE + "PedestalBlockEntity.class",
            PEDESTAL_PACKAGE + "PedestalBlockEntityRemoval.class",
            PEDESTAL_PACKAGE + "PedestalBlockEntityRemovalBackend.class",
            PEDESTAL_PACKAGE + "PedestalDispenserBehavior.class",
            PEDESTAL_PACKAGE + "PedestalShape.class",
            "ru/feytox/etherology/util/inventory/ListBackedInventory.class",
            "ru/feytox/etherology/util/misc/UniqueProvider.class",
            SHARED_BLOCKS + ".class",
            SHARED_ITEMS + ".class",
            SHARED_BLOCK_ENTITIES + ".class"
    );
    private static final List<String> LEGACY_SOURCE_PATHS = List.of(
            "src/main/java/ru/feytox/etherology/block/pedestal/PedestalBlock.java",
            "src/main/java/ru/feytox/etherology/block/pedestal/PedestalBlockEntity.java",
            "src/main/java/ru/feytox/etherology/block/pedestal/PedestalDispenserBehavior.java",
            "src/main/java/ru/feytox/etherology/block/pedestal/PedestalShape.java",
            "src/main/java/ru/feytox/etherology/util/inventory/ListBackedInventory.java",
            "src/main/java/ru/feytox/etherology/util/misc/UniqueProvider.java",
            "forge/src/main/java/ru/feytox/etherology/block/pedestal/PedestalBlock.java",
            "forge/src/main/java/ru/feytox/etherology/block/pedestal/PedestalBlockEntity.java",
            "forge/src/main/java/ru/feytox/etherology/block/pedestal/PedestalDispenserBehavior.java",
            "forge/src/main/java/ru/feytox/etherology/block/pedestal/PedestalShape.java"
    );
    private static final List<String> COMMON_SOURCE_PATHS = List.of(
            "common/src/main/java/ru/feytox/etherology/block/pedestal/PedestalBlock.java",
            "common/src/main/java/ru/feytox/etherology/block/pedestal/PedestalBlockEntity.java",
            "common/src/main/java/ru/feytox/etherology/block/pedestal/"
                    + "PedestalBlockEntityRemoval.java",
            "common/src/main/java/ru/feytox/etherology/block/pedestal/"
                    + "PedestalBlockEntityRemovalBackend.java",
            "common/src/main/java/ru/feytox/etherology/block/pedestal/"
                    + "PedestalDispenserBehavior.java",
            "common/src/main/java/ru/feytox/etherology/block/pedestal/PedestalShape.java",
            "common/src/main/java/ru/feytox/etherology/util/inventory/"
                    + "ListBackedInventory.java",
            "common/src/main/java/ru/feytox/etherology/util/misc/UniqueProvider.java",
            "common/src/main/java/ru/feytox/etherology/registry/block/"
                    + "SharedPedestalBlocks.java",
            "common/src/main/java/ru/feytox/etherology/registry/item/"
                    + "SharedPedestalBlockItems.java",
            "common/src/main/java/ru/feytox/etherology/registry/block/"
                    + "SharedPedestalBlockEntities.java"
    );

    @Test
    void forgeClasspathHasExactlyOneCommonOwnerForEveryMovedPedestalType()
            throws IOException {
        for (String classResource : MOVED_COMMON_CLASSES) {
            List<URL> locations = PedestalBytecodeAssertions.resourceLocations(
                    classResource
            );
            assertEquals(1, locations.size(), classResource + ":" + locations);
            assertEquals("jar", locations.get(0).getProtocol(), classResource);
            assertTrue(
                    locations.get(0).toExternalForm().contains("Common"),
                    classResource + ":" + locations.get(0)
            );
        }

        assertTrue(PedestalBytecodeAssertions.resourceLocations(
                "ru/feytox/etherology/registry/block/EBlocks.class"
        ).isEmpty());
    }

    @Test
    void sharedRegistriesExposeTheExactForgeVisiblePedestalAliases()
            throws IOException {
        assertRegistryOwner(
                SHARED_BLOCKS,
                "BLOCKS",
                "PEDESTAL",
                "pedestal"
        );
        assertRegistryOwner(
                SHARED_ITEMS,
                "ITEMS",
                "PEDESTAL_ITEM",
                "pedestal"
        );
        assertRegistryOwner(
                SHARED_BLOCK_ENTITIES,
                "BLOCK_ENTITIES",
                "PEDESTAL",
                "pedestal_block_entity"
        );

        MethodNode itemFactory = PedestalBytecodeAssertions.requireMethod(
                PedestalBytecodeAssertions.readClass(SHARED_ITEMS + ".class"),
                "lambda$static$0",
                "()Lnet/minecraft/item/BlockItem;"
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countFieldAccesses(
                        itemFactory,
                        SHARED_BLOCKS,
                        "PEDESTAL",
                        Opcodes.GETSTATIC
                )
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        itemFactory,
                        "net/minecraft/item/BlockItem",
                        "appendBlocks",
                        "(Ljava/util/Map;Lnet/minecraft/item/Item;)V"
                )
        );

        MethodNode blockEntityFactory = PedestalBytecodeAssertions.requireMethod(
                PedestalBytecodeAssertions.readClass(
                        SHARED_BLOCK_ENTITIES + ".class"
                ),
                "lambda$static$0",
                "()Lnet/minecraft/block/entity/BlockEntityType;"
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countFieldAccesses(
                        blockEntityFactory,
                        SHARED_BLOCKS,
                        "PEDESTAL",
                        Opcodes.GETSTATIC
                )
        );
        assertTrue(PedestalBytecodeAssertions.methodHandles(blockEntityFactory).contains(
                PEDESTAL_PACKAGE + "PedestalBlockEntity#<init>"
                        + "(Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/block/BlockState;)V"
        ));

        MethodNode entityConstructor = PedestalBytecodeAssertions.requireMethod(
                PedestalBytecodeAssertions.readClass(
                        PEDESTAL_PACKAGE + "PedestalBlockEntity.class"
                ),
                "<init>",
                "(Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/block/BlockState;)V"
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countFieldAccesses(
                        entityConstructor,
                        SHARED_BLOCK_ENTITIES,
                        "PEDESTAL",
                        Opcodes.GETSTATIC
                )
        );

        MethodNode shapeLookup = PedestalBytecodeAssertions.requireMethod(
                PedestalBytecodeAssertions.readClass(
                        PEDESTAL_PACKAGE + "PedestalShape.class"
                ),
                "getShape",
                "(Lnet/minecraft/block/BlockState;"
                        + "Lnet/minecraft/block/BlockState;)"
                        + "Lru/feytox/etherology/block/pedestal/PedestalShape;"
        );
        assertEquals(
                2,
                PedestalBytecodeAssertions.countFieldAccesses(
                        shapeLookup,
                        SHARED_BLOCKS,
                        "PEDESTAL",
                        Opcodes.GETSTATIC
                )
        );
    }

    @Test
    void commonBootstrapAttachesBlockThenItemThenBlockEntityForForge()
            throws IOException {
        MethodNode initialize = PedestalBytecodeAssertions.requireMethod(
                PedestalBytecodeAssertions.readClass(
                        "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class"
                ),
                "initialize",
                "(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );
        List<String> pedestalCalls = PedestalBytecodeAssertions.calls(initialize)
                .stream()
                .filter(call -> call.contains("SharedPedestal"))
                .toList();
        assertEquals(
                List.of(
                        SHARED_BLOCKS + "#register()V",
                        SHARED_ITEMS + "#register()V",
                        SHARED_BLOCK_ENTITIES + "#register()V"
                ),
                pedestalCalls
        );
    }

    @Test
    void legacySourcesAreGoneAndTheRemainingAliasesReadSharedOwners()
            throws IOException {
        Path repositoryRoot = PedestalBytecodeAssertions.repositoryRoot();
        for (String legacySource : LEGACY_SOURCE_PATHS) {
            assertFalse(
                    Files.exists(
                            repositoryRoot.resolve(legacySource),
                            LinkOption.NOFOLLOW_LINKS
                    ),
                    legacySource
            );
        }
        for (String commonSource : COMMON_SOURCE_PATHS) {
            PedestalBytecodeAssertions.requireRegularFile(
                    repositoryRoot.resolve(commonSource)
            );
        }

        Path aliasesPath = PedestalBytecodeAssertions.requireRegularFile(
                repositoryRoot.resolve(
                        "src/main/java/ru/feytox/etherology/registry/block/EBlocks.java"
                )
        );
        String aliases = Files.readString(aliasesPath);
        assertTrue(Pattern.compile(
                "PEDESTAL_BLOCK\\s*=\\s*SharedPedestalBlocks\\.PEDESTAL\\.get\\(\\)",
                Pattern.MULTILINE
        ).matcher(aliases).find());
        assertTrue(Pattern.compile(
                "PEDESTAL_BLOCK_ENTITY\\s*=\\s*"
                        + "SharedPedestalBlockEntities\\.PEDESTAL\\.get\\(\\)",
                Pattern.MULTILINE
        ).matcher(aliases).find());
        assertFalse(Pattern.compile("new\\s+PedestalBlock\\s*\\(")
                .matcher(aliases)
                .find());
    }

    @Test
    void forgeEntrypointBindsItsNetworkBackendBeforeSharedRegistration()
            throws IOException {
        MethodNode constructor = PedestalBytecodeAssertions.requireMethod(
                PedestalBytecodeAssertions.readClass(
                        "ru/feytox/etherology/forge/EtherologyForge.class"
                ),
                "<init>",
                "(Lnet/minecraftforge/fml/javafmlmod/"
                        + "FMLJavaModLoadingContext;)V"
        );
        int networkRegistration = PedestalBytecodeAssertions.callIndex(
                constructor,
                "ru/feytox/etherology/forge/network/ForgePedestalNetwork",
                "register",
                "()V"
        );
        int backendRead = PedestalBytecodeAssertions.fieldAccessIndex(
                constructor,
                "ru/feytox/etherology/forge/block/pedestal/"
                        + "ForgePedestalBlockEntityRemovalBackend",
                "INSTANCE",
                Opcodes.GETSTATIC
        );
        int backendBinding = PedestalBytecodeAssertions.callIndex(
                constructor,
                PEDESTAL_PACKAGE + "PedestalBlockEntityRemoval",
                "bind",
                "(Lru/feytox/etherology/block/pedestal/"
                        + "PedestalBlockEntityRemovalBackend;)V"
        );
        int sharedBootstrap = PedestalBytecodeAssertions.callIndex(
                constructor,
                "ru/feytox/etherology/bootstrap/EtherologyBootstrap",
                "initialize",
                "(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );
        assertTrue(networkRegistration >= 0);
        assertTrue(networkRegistration < backendRead);
        assertTrue(backendRead < backendBinding);
        assertTrue(backendBinding < sharedBootstrap);

        String constants = PedestalBytecodeAssertions.classConstants(
                "ru/feytox/etherology/forge/EtherologyForge.class"
        );
        assertFalse(constants.contains("ru/feytox/etherology/registry/block/EBlocks"));
        assertFalse(constants.contains("ru/feytox/etherology/forge/client/"));
        assertFalse(constants.contains("net/minecraft/client/"));
    }

    private static void assertRegistryOwner(
            String className,
            String ownerField,
            String supplierField,
            String registrationId
    ) throws IOException {
        ClassNode registry = PedestalBytecodeAssertions.readClass(className + ".class");
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                registry.access & (
                        Opcodes.ACC_PUBLIC
                                | Opcodes.ACC_FINAL
                                | Opcodes.ACC_INTERFACE
                                | Opcodes.ACC_ABSTRACT
                ),
                className
        );
        assertEquals(
                List.of(ownerField, supplierField),
                registry.fields.stream().map(field -> field.name).toList(),
                className
        );

        FieldNode deferredOwner = registry.fields.stream()
                .filter(field -> field.name.equals(ownerField))
                .findFirst()
                .orElseThrow();
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                deferredOwner.access,
                className
        );
        assertEquals("L" + SHARED_REGISTER + ";", deferredOwner.desc, className);

        FieldNode supplier = registry.fields.stream()
                .filter(field -> field.name.equals(supplierField))
                .findFirst()
                .orElseThrow();
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                supplier.access,
                className
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier;",
                supplier.desc,
                className
        );

        MethodNode classInitializer = PedestalBytecodeAssertions.requireMethod(
                registry,
                "<clinit>",
                "()V"
        );
        assertEquals(
                List.of(registrationId),
                PedestalBytecodeAssertions.stringConstants(classInitializer),
                className
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        classInitializer,
                        SHARED_REGISTER,
                        "register",
                        "(Ljava/lang/String;Ljava/util/function/Supplier;)"
                                + "Ldev/architectury/registry/registries/RegistrySupplier;"
                ),
                className
        );

        MethodNode register = PedestalBytecodeAssertions.requireMethod(
                registry,
                "register",
                "()V"
        );
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        register,
                        SHARED_REGISTER,
                        "attach",
                        "()V"
                ),
                className
        );
    }
}
