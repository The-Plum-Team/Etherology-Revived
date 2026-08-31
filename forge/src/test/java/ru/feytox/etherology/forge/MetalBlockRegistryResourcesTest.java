package ru.feytox.etherology.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MetalBlockRegistryResourcesTest {

    private static final String SHARED_METAL_BLOCKS =
            "ru/feytox/etherology/registry/block/SharedMetalBlocks.class";
    private static final String SHARED_METAL_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/SharedMetalBlocks";
    private static final String SHARED_METAL_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/SharedMetalBlockItems.class";
    private static final String SHARED_METAL_BLOCK_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedMetalBlockItems";
    private static final String SHARED_DEFERRED_REGISTER_OWNER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";
    private static final String SHARED_DEFERRED_REGISTER_DESCRIPTOR =
            "Lru/feytox/etherology/registry/SharedDeferredRegister;";
    private static final String LEGACY_DECO_BLOCKS =
            "ru/feytox/etherology/registry/block/DecoBlocks.class";
    private static final String LEGACY_DECO_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/DecoBlockItems.class";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";

    private static final String PICKAXE_TAG =
            "data/minecraft/tags/blocks/mineable/pickaxe.json";
    private static final String NEEDS_IRON_TOOL_TAG =
            "data/minecraft/tags/blocks/needs_iron_tool.json";
    private static final String BEACON_BASE_BLOCKS_TAG =
            "data/minecraft/tags/blocks/beacon_base_blocks.json";

    private static final Map<String, MetalBlock> METALS = metals();

    @Test
    void everyArtifactHasTheExactTwoDeferredOwnersAndNoLegacyMetalFields()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                assertEquals(1, count(entries, SHARED_METAL_BLOCKS), artifact.description());
                assertEquals(
                        1,
                        count(entries, SHARED_METAL_BLOCK_ITEMS),
                        artifact.description()
                );

                ClassReader blocks = classReader(jar, SHARED_METAL_BLOCKS);
                ClassReader items = classReader(jar, SHARED_METAL_BLOCK_ITEMS);
                assertExactBlockOwner(artifact.description(), blocks);
                assertExactBlockItemOwner(artifact.description(), items);
                assertNoLoaderSpecificReferences(artifact.description(), blocks);
                assertNoLoaderSpecificReferences(artifact.description(), items);
                assertOnlyExpectedRegistrationOwners(artifact, jar, entries);

                if (artifact.fabricApplication()) {
                    assertFieldsAbsent(
                            artifact.description(),
                            classReader(jar, LEGACY_DECO_BLOCKS),
                            Set.of("AZEL_BLOCK", "ETHRIL_BLOCK", "EBONY_BLOCK")
                    );
                    assertFieldsAbsent(
                            artifact.description(),
                            classReader(jar, LEGACY_DECO_BLOCK_ITEMS),
                            Set.of(
                                    "AZEL_BLOCK",
                                    "ETHRIL_BLOCK",
                                    "EBONY_BLOCK",
                                    "AZEL_BLOCK_ITEM",
                                    "ETHRIL_BLOCK_ITEM",
                                    "EBONY_BLOCK_ITEM"
                            )
                    );
                } else {
                    assertFalse(entries.contains(LEGACY_DECO_BLOCKS), artifact.description());
                    assertFalse(
                            entries.contains(LEGACY_DECO_BLOCK_ITEMS),
                            artifact.description()
                    );
                }
            }
        }
    }

    @Test
    void eachLoaderAttachesBlocksBeforeTheirItemsAndBeforeLegacyInitialization()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                String initializer = artifact.fabricApplication()
                        ? FABRIC_INITIALIZER
                        : COMMON_BOOTSTRAP;
                List<String> invocations = methodInvocations(
                        classReader(jar, initializer),
                        "initialize"
                );
                String blocks = SHARED_METAL_BLOCKS_OWNER + "#register()V";
                String items = SHARED_METAL_BLOCK_ITEMS_OWNER + "#register()V";
                assertInvocationOnce(artifact.description(), invocations, blocks);
                assertInvocationOnce(artifact.description(), invocations, items);
                assertTrue(
                        invocations.indexOf(blocks) < invocations.indexOf(items),
                        artifact.description()
                );

                if (artifact.fabricApplication()) {
                    String materialItems =
                            "ru/feytox/etherology/registry/item/SharedMaterialItems"
                                    + "#register()V";
                    String legacyItems =
                            "ru/feytox/etherology/registry/item/EItems"
                                    + "#registerItems()V";
                    assertInvocationOnce(
                            artifact.description(),
                            invocations,
                            materialItems
                    );
                    assertInvocationOnce(artifact.description(), invocations, legacyItems);
                    assertTrue(
                            invocations.indexOf(items) < invocations.indexOf(materialItems),
                            artifact.description()
                    );
                    assertTrue(
                            invocations.indexOf(materialItems) < invocations.indexOf(legacyItems),
                            artifact.description()
                    );
                } else {
                    String sharedBlocks =
                            "ru/feytox/etherology/registry/block/SharedBlocks#register()V";
                    String sharedItems =
                            "ru/feytox/etherology/registry/item/SharedItems#register()V";
                    assertInvocationOnce(artifact.description(), invocations, sharedBlocks);
                    assertInvocationOnce(artifact.description(), invocations, sharedItems);
                    assertTrue(
                            invocations.indexOf(sharedBlocks) < invocations.indexOf(blocks),
                            artifact.description()
                    );
                    assertTrue(
                            invocations.indexOf(items) < invocations.indexOf(sharedItems),
                            artifact.description()
                    );
                }
            }
        }
    }

    @Test
    void packagedDedicatedResourcesMatchCanonicalBytesAndTagsKeepExactMembership()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.metalBlocks.repositoryRoot");
        List<ResourceSpec> dedicatedResources = dedicatedResources(repositoryRoot);
        for (ResourceSpec resource : dedicatedResources) {
            requireRegularFile(resource.canonicalFile());
        }

        Path languageFile = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/lang/en_us.json"
        );
        requireRegularFile(languageFile);
        JsonObject language = parseObject(Files.readString(languageFile));
        for (MetalBlock metal : METALS.values()) {
            assertEquals(
                    metal.englishName(),
                    language.get("block.etherology." + metal.id()).getAsString(),
                    metal.id()
            );
        }

        assertTagMembership(
                Files.readString(repositoryRoot.resolve("src/main/generated/" + PICKAXE_TAG)),
                PICKAXE_TAG,
                Set.copyOf(METALS.keySet()),
                Set.of()
        );
        assertOnlyRequiredTagIds(
                Files.readString(repositoryRoot.resolve("src/main/generated/" + PICKAXE_TAG)),
                PICKAXE_TAG,
                Set.of(
                        "etherology:azel_block",
                        "etherology:ebony_block",
                        "etherology:ethereal_channel",
                        "etherology:ethereal_storage",
                        "etherology:ethril_block"
                )
        );
        assertTagMembership(
                Files.readString(
                        repositoryRoot.resolve("src/main/generated/" + NEEDS_IRON_TOOL_TAG)
                ),
                NEEDS_IRON_TOOL_TAG,
                Set.copyOf(METALS.keySet()),
                Set.of()
        );
        assertOnlyRequiredTagIds(
                Files.readString(
                        repositoryRoot.resolve("src/main/generated/" + NEEDS_IRON_TOOL_TAG)
                ),
                NEEDS_IRON_TOOL_TAG,
                namespacedMetalIds()
        );
        assertTagMembership(
                Files.readString(
                        repositoryRoot.resolve("src/main/generated/" + BEACON_BASE_BLOCKS_TAG)
                ),
                BEACON_BASE_BLOCKS_TAG,
                Set.of("ethril_block", "ebony_block"),
                Set.of("azel_block")
        );
        assertOnlyRequiredTagIds(
                Files.readString(
                        repositoryRoot.resolve("src/main/generated/" + BEACON_BASE_BLOCKS_TAG)
                ),
                BEACON_BASE_BLOCKS_TAG,
                Set.of("etherology:ebony_block", "etherology:ethril_block")
        );

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                for (ResourceSpec resource : dedicatedResources) {
                    assertCanonicalResource(artifact, jar, resource);
                }
                assertCanonicalResource(
                        artifact,
                        jar,
                        new ResourceSpec("assets/etherology/lang/en_us.json", languageFile)
                );
                assertPackagedTag(
                        artifact,
                        jar,
                        PICKAXE_TAG,
                        Set.copyOf(METALS.keySet()),
                        Set.of(),
                        Set.of(
                                "etherology:azel_block",
                                "etherology:ebony_block",
                                "etherology:ethereal_channel",
                                "etherology:ethereal_storage",
                                "etherology:ethril_block"
                        )
                );
                assertPackagedTag(
                        artifact,
                        jar,
                        NEEDS_IRON_TOOL_TAG,
                        Set.copyOf(METALS.keySet()),
                        Set.of(),
                        namespacedMetalIds()
                );
                assertPackagedTag(
                        artifact,
                        jar,
                        BEACON_BASE_BLOCKS_TAG,
                        Set.of("ethril_block", "ebony_block"),
                        Set.of("azel_block"),
                        Set.of("etherology:ebony_block", "etherology:ethril_block")
                );
            }
        }
    }

    private static void assertExactBlockOwner(String description, ClassReader reader) {
        ClassShape shape = classShape(reader);
        assertFinalPublicCatalog(description, shape);
        assertEquals(
                List.of("BLOCKS", "AZEL_BLOCK", "ETHRIL_BLOCK", "EBONY_BLOCK"),
                new ArrayList<>(shape.fieldDescriptors().keySet()),
                description
        );
        assertEquals(
                SHARED_DEFERRED_REGISTER_DESCRIPTOR,
                shape.fieldDescriptors().get("BLOCKS"),
                description
        );
        for (MetalBlock metal : METALS.values()) {
            assertEquals(
                    REGISTRY_SUPPLIER_DESCRIPTOR,
                    shape.fieldDescriptors().get(metal.blockField()),
                    description + ":" + metal.blockField()
            );
        }

        Map<String, String> factories = registrationFactories(
                reader,
                SHARED_METAL_BLOCKS_OWNER
        );
        assertEquals(expectedBlockBindings(), blockFieldBindings(reader), description);
        assertEquals(new ArrayList<>(METALS.keySet()), new ArrayList<>(factories.keySet()));
        for (MetalBlock metal : METALS.values()) {
            assertExactBlockFactory(
                    description + ":" + metal.id(),
                    reader,
                    factories.get(metal.id()),
                    metal
            );
        }

        InvocationInventory inventory = invocationInventory(reader);
        assertEquals(3, inventory.count(SHARED_DEFERRED_REGISTER_OWNER, "register"));
        assertEquals(1, inventory.count(SHARED_DEFERRED_REGISTER_OWNER, "attach"));
        assertEquals(0, inventory.count(REGISTRY_SUPPLIER_OWNER, "get"));
        assertEquals(0, inventory.directRegistryCalls(), description);
    }

    private static void assertExactBlockItemOwner(String description, ClassReader reader) {
        ClassShape shape = classShape(reader);
        assertFinalPublicCatalog(description, shape);
        assertEquals(
                List.of(
                        "ITEMS",
                        "AZEL_BLOCK_ITEM",
                        "ETHRIL_BLOCK_ITEM",
                        "EBONY_BLOCK_ITEM"
                ),
                new ArrayList<>(shape.fieldDescriptors().keySet()),
                description
        );
        assertEquals(
                SHARED_DEFERRED_REGISTER_DESCRIPTOR,
                shape.fieldDescriptors().get("ITEMS"),
                description
        );
        for (MetalBlock metal : METALS.values()) {
            assertEquals(
                    REGISTRY_SUPPLIER_DESCRIPTOR,
                    shape.fieldDescriptors().get(metal.itemField()),
                    description + ":" + metal.itemField()
            );
        }

        assertEquals(expectedItemBindings(), blockItemBindings(reader), description);
        List<String> factoryMethods = implementationMethods(
                reader,
                "registerBlockItem",
                SHARED_METAL_BLOCK_ITEMS_OWNER
        );
        assertEquals(1, factoryMethods.size(), description);
        assertExactDeferredBlockItemFactory(description, reader, factoryMethods.get(0));

        InvocationInventory inventory = invocationInventory(reader);
        assertEquals(1, inventory.count(SHARED_DEFERRED_REGISTER_OWNER, "register"));
        assertEquals(1, inventory.count(SHARED_DEFERRED_REGISTER_OWNER, "attach"));
        assertEquals(1, inventory.count(REGISTRY_SUPPLIER_OWNER, "get"));
        assertEquals(
                1,
                inventory.countInMethod(factoryMethods.get(0), REGISTRY_SUPPLIER_OWNER, "get"),
                description
        );
        assertEquals(0, inventory.directRegistryCalls(), description);
    }

    private static void assertFinalPublicCatalog(String description, ClassShape shape) {
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                shape.classAccess() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL),
                description
        );
        assertEquals(List.of("register()V"), shape.publicMethods(), description);
        assertEquals(1, shape.privateConstructorCount(), description);
        for (Map.Entry<String, Integer> field : shape.fieldAccess().entrySet()) {
            int expected = field.getKey().equals("BLOCKS") || field.getKey().equals("ITEMS")
                    ? Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
                    : Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
            assertEquals(expected, field.getValue(), description + ":" + field.getKey());
        }
    }

    private static Map<String, String> registrationFactories(
            ClassReader reader,
            String expectedOwner
    ) {
        Map<String, String> factories = new LinkedHashMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && METALS.containsKey(id)) {
                            pendingId = id;
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String name,
                            String descriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        if (pendingId == null) return;
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle
                                    && handle.getOwner().equals(expectedOwner)) {
                                factories.put(pendingId, handle.getName());
                                pendingId = null;
                                return;
                            }
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return factories;
    }

    private static void assertExactBlockFactory(
            String description,
            ClassReader reader,
            String factoryMethod,
            MetalBlock metal
    ) {
        assertNotNull(factoryMethod, description);
        MethodCode code = methodCode(reader, factoryMethod);
        MinecraftNames names = minecraftNames(code);
        List<FieldReference> expectedFields = new ArrayList<>();
        expectedFields.add(new FieldReference(names.blocksOwner(), names.baseField(metal)));
        if (metal.mapColorField() != null) {
            expectedFields.add(new FieldReference(
                    names.mapColorOwner(),
                    names.mapColorField(metal)
            ));
        }
        assertEquals(expectedFields, code.staticFieldReads(), description);
        assertEquals(
                1,
                code.countInvocation(names.settingsOwner(), names.copyMethod()),
                description
        );
        assertEquals(
                metal.mapColorField() == null ? 0 : 1,
                code.countInvocation(names.settingsOwner(), names.mapColorMethod()),
                description
        );
        assertEquals(1, code.countTypeInstruction(Opcodes.NEW, names.blockOwner()));
        assertEquals(1, code.countInvocation(names.blockOwner(), "<init>"));
    }

    private static List<BlockItemBinding> blockItemBindings(ClassReader reader) {
        List<BlockItemBinding> bindings = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;
                    private String pendingBlockField;
                    private boolean registered;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && METALS.containsKey(id)) {
                            pendingId = id;
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_METAL_BLOCKS_OWNER)) {
                            pendingBlockField = name;
                        } else if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(SHARED_METAL_BLOCK_ITEMS_OWNER)
                                && registered) {
                            bindings.add(new BlockItemBinding(
                                    pendingId,
                                    pendingBlockField,
                                    name
                            ));
                            pendingId = null;
                            pendingBlockField = null;
                            registered = false;
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals(SHARED_METAL_BLOCK_ITEMS_OWNER)
                                && name.equals("registerBlockItem")) {
                            assertNotNull(pendingId);
                            assertNotNull(pendingBlockField);
                            registered = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return bindings;
    }

    private static List<BlockFieldBinding> blockFieldBindings(ClassReader reader) {
        List<BlockFieldBinding> bindings = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;
                    private boolean factoryCreated;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && METALS.containsKey(id)) {
                            pendingId = id;
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String name,
                            String descriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        if (pendingId == null) return;
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle
                                    && handle.getOwner().equals(SHARED_METAL_BLOCKS_OWNER)) {
                                factoryCreated = true;
                            }
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode != Opcodes.PUTSTATIC
                                || !owner.equals(SHARED_METAL_BLOCKS_OWNER)
                                || !factoryCreated) {
                            return;
                        }
                        bindings.add(new BlockFieldBinding(pendingId, name));
                        pendingId = null;
                        factoryCreated = false;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return bindings;
    }

    private static List<String> implementationMethods(
            ClassReader reader,
            String enclosingMethod,
            String expectedOwner
    ) {
        List<String> methods = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(enclosingMethod)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(
                            String name,
                            String descriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle
                                    && handle.getOwner().equals(expectedOwner)) {
                                methods.add(handle.getName());
                            }
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return methods;
    }

    private static void assertExactDeferredBlockItemFactory(
            String description,
            ClassReader reader,
            String factoryMethod
    ) {
        MethodCode code = methodCode(reader, factoryMethod);
        MinecraftNames names = minecraftNames(code);
        assertEquals(
                List.of(new FieldReference(names.itemOwner(), names.blockItemsField())),
                code.staticFieldReads(),
                description
        );
        assertEquals(1, code.countInvocation(REGISTRY_SUPPLIER_OWNER, "get"));
        assertEquals(1, code.countTypeInstruction(Opcodes.NEW, names.blockItemOwner()));
        assertEquals(1, code.countTypeInstruction(Opcodes.NEW, names.itemSettingsOwner()));
        assertEquals(1, code.countInvocation(names.blockItemOwner(), "<init>"));
        assertEquals(
                1,
                code.countInvocation(names.blockItemOwner(), names.appendBlocksMethod()),
                description
        );
    }

    private static ClassShape classShape(ClassReader reader) {
        int[] classAccess = {0};
        int[] privateConstructorCount = {0};
        Map<String, Integer> fieldAccess = new LinkedHashMap<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        List<String> publicMethods = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                classAccess[0] = access;
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fieldAccess.put(name, access);
                fieldDescriptors.put(name, descriptor);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (name.equals("<init>") && (access & Opcodes.ACC_PRIVATE) != 0) {
                    privateConstructorCount[0]++;
                } else if ((access & Opcodes.ACC_PUBLIC) != 0) {
                    publicMethods.add(name + descriptor);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassShape(
                classAccess[0],
                fieldAccess,
                fieldDescriptors,
                publicMethods,
                privateConstructorCount[0]
        );
    }

    private static MethodCode methodCode(ClassReader reader, String targetMethod) {
        List<FieldReference> staticFieldReads = new ArrayList<>();
        List<MethodReference> invocations = new ArrayList<>();
        List<TypeInstruction> typeInstructions = new ArrayList<>();
        int[] methodCount = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(targetMethod)) return null;
                methodCount[0]++;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC) {
                            staticFieldReads.add(new FieldReference(owner, name));
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        invocations.add(new MethodReference(owner, name));
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        typeInstructions.add(new TypeInstruction(opcode, type));
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, methodCount[0], targetMethod);
        return new MethodCode(staticFieldReads, invocations, typeInstructions);
    }

    private static MinecraftNames minecraftNames(MethodCode code) {
        boolean intermediary = code.referencesOwner("net/minecraft/class_2246")
                || code.referencesOwner("net/minecraft/class_1747");
        return intermediary ? MinecraftNames.intermediary() : MinecraftNames.named();
    }

    private static InvocationInventory invocationInventory(ClassReader reader) {
        List<MethodInvocation> invocations = new ArrayList<>();
        int[] directRegistryCalls = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        invocations.add(new MethodInvocation(name, owner, invokedName));
                        if (isDirectRegistryOwner(owner)) directRegistryCalls[0]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new InvocationInventory(invocations, directRegistryCalls[0]);
    }

    private static void assertOnlyExpectedRegistrationOwners(
            Artifact artifact,
            JarFile jar,
            List<String> entries
    ) throws IOException {
        Map<String, Set<String>> deferredOwners = ownerSets();
        Map<String, Set<String>> directOwners = ownerSets();
        for (String entry : entries) {
            if (!entry.startsWith("ru/feytox/etherology/") || !entry.endsWith(".class")) {
                continue;
            }
            ClassOwnership ownership = classOwnership(classReader(jar, entry));
            for (String id : ownership.metalIds()) {
                if (ownership.sharedDeferredRegistration()) {
                    deferredOwners.get(id).add(entry.substring(0, entry.length() - 6));
                }
                if (ownership.directRegistryRegistration()) {
                    directOwners.get(id).add(entry.substring(0, entry.length() - 6));
                }
            }
        }

        Set<String> expectedOwners = Set.of(
                SHARED_METAL_BLOCKS_OWNER,
                SHARED_METAL_BLOCK_ITEMS_OWNER
        );
        for (String id : METALS.keySet()) {
            assertEquals(
                    expectedOwners,
                    deferredOwners.get(id),
                    artifact.description() + ":" + id
            );
            assertEquals(Set.of(), directOwners.get(id), artifact.description() + ":" + id);
        }
    }

    private static ClassOwnership classOwnership(ClassReader reader) {
        Set<String> ids = new LinkedHashSet<>();
        boolean[] sharedDeferredRegistration = {false};
        boolean[] directRegistryRegistration = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && METALS.containsKey(id)) ids.add(id);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && name.equals("register")) {
                            sharedDeferredRegistration[0] = true;
                        }
                        if (isDirectRegistryOwner(owner)) {
                            directRegistryRegistration[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassOwnership(
                ids,
                sharedDeferredRegistration[0],
                directRegistryRegistration[0]
        );
    }

    private static boolean isDirectRegistryOwner(String owner) {
        return owner.equals("net/minecraft/registry/Registry")
                || owner.equals("net/minecraft/class_2378")
                || owner.equals("net/minecraft/core/Registry");
    }

    private static void assertNoLoaderSpecificReferences(
            String description,
            ClassReader reader
    ) {
        Set<String> forbidden = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                check(name);
                check(superName);
                check(signature);
                if (interfaces != null) {
                    for (String interfaceName : interfaces) check(interfaceName);
                }
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                check(descriptor);
                check(signature);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                check(descriptor);
                check(signature);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        check(type);
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        check(owner);
                        check(descriptor);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        check(owner);
                        check(descriptor);
                    }
                };
            }

            private void check(String value) {
                if (value != null && (value.contains("net/fabricmc/")
                        || value.contains("net/minecraftforge/")
                        || value.contains("ru/feytox/etherology/client/"))) {
                    forbidden.add(value);
                }
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(Set.of(), forbidden, description);
    }

    private static void assertFieldsAbsent(
            String description,
            ClassReader reader,
            Set<String> forbiddenFields
    ) {
        Set<String> present = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (forbiddenFields.contains(name)) present.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(Set.of(), present, description);
    }

    private static List<String> methodInvocations(ClassReader reader, String targetMethod) {
        List<String> invocations = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(targetMethod)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        invocations.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static void assertInvocationOnce(
            String description,
            List<String> invocations,
            String expected
    ) {
        assertEquals(
                1,
                invocations.stream().filter(expected::equals).count(),
                description + ":" + expected
        );
    }

    private static List<ResourceSpec> dedicatedResources(Path root) {
        List<ResourceSpec> resources = new ArrayList<>();
        for (MetalBlock metal : METALS.values()) {
            resources.add(resource(
                    root,
                    "assets/etherology/blockstates/" + metal.id() + ".json",
                    "src/main/generated"
            ));
            resources.add(resource(
                    root,
                    "assets/etherology/models/block/" + metal.id() + ".json",
                    "src/main/generated"
            ));
            resources.add(resource(
                    root,
                    "assets/etherology/models/item/" + metal.id() + ".json",
                    "src/main/generated"
            ));
            resources.add(resource(
                    root,
                    "assets/etherology/textures/block/" + metal.id() + ".png",
                    "src/client/resources"
            ));
            resources.add(resource(
                    root,
                    "data/etherology/loot_tables/blocks/" + metal.id() + ".json",
                    "src/main/generated"
            ));
            resources.add(resource(
                    root,
                    "data/etherology/recipes/" + metal.id() + ".json",
                    "src/main/generated"
            ));
            resources.add(resource(
                    root,
                    "data/etherology/recipes/"
                            + metal.id().replace("_block", "_ingot_from_")
                            + metal.id() + ".json",
                    "src/main/generated"
            ));
        }
        return List.copyOf(resources);
    }

    private static ResourceSpec resource(Path root, String entry, String sourceRoot) {
        return new ResourceSpec(entry, root.resolve(sourceRoot).resolve(entry));
    }

    private static void assertCanonicalResource(
            Artifact artifact,
            JarFile jar,
            ResourceSpec resource
    ) throws IOException {
        List<JarEntry> entries = jar.stream()
                .filter(entry -> entry.getName().equals(resource.entry()))
                .toList();
        if (!artifact.includesResources()) {
            assertEquals(List.of(), entries, artifact.description() + ":" + resource.entry());
            return;
        }
        assertEquals(1, entries.size(), artifact.description() + ":" + resource.entry());
        assertArrayEquals(
                Files.readAllBytes(resource.canonicalFile()),
                jar.getInputStream(entries.get(0)).readAllBytes(),
                artifact.description() + ":" + resource.entry()
        );
    }

    private static void assertPackagedTag(
            Artifact artifact,
            JarFile jar,
            String entryName,
            Set<String> requiredIds,
            Set<String> forbiddenIds,
            Set<String> exactRequiredIds
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        if (!artifact.includesResources()) {
            assertEquals(null, entry, artifact.description() + ":" + entryName);
            return;
        }
        assertNotNull(entry, artifact.description() + ":" + entryName);
        String json = new String(
                jar.getInputStream(entry).readAllBytes(),
                StandardCharsets.UTF_8
        );
        assertTagMembership(
                json,
                artifact.description() + ":" + entryName,
                requiredIds,
                forbiddenIds
        );
        assertOnlyRequiredTagIds(
                json,
                artifact.description() + ":" + entryName,
                exactRequiredIds
        );
    }

    private static void assertTagMembership(
            String json,
            String description,
            Set<String> requiredIds,
            Set<String> forbiddenIds
    ) {
        JsonObject object = parseObject(json);
        JsonArray values = object.getAsJsonArray("values");
        assertNotNull(values, description);
        List<TagValue> entries = new ArrayList<>();
        for (JsonElement value : values) {
            if (value.isJsonPrimitive()) {
                entries.add(new TagValue(value.getAsString(), true));
                continue;
            }
            assertTrue(value.isJsonObject(), description);
            JsonObject objectValue = value.getAsJsonObject();
            assertEquals(Set.of("id", "required"), objectValue.keySet(), description);
            entries.add(new TagValue(
                    objectValue.get("id").getAsString(),
                    objectValue.get("required").getAsBoolean()
            ));
        }
        for (String id : requiredIds) {
            String namespacedId = "etherology:" + id;
            assertEquals(
                    1,
                    entries.stream().filter(entry -> entry.id().equals(namespacedId)).count(),
                    description + ":" + namespacedId
            );
            assertTrue(
                    entries.stream().filter(entry -> entry.id().equals(namespacedId))
                            .findFirst().orElseThrow().required(),
                    description + ":" + namespacedId
            );
        }
        for (String id : forbiddenIds) {
            assertFalse(
                    entries.stream().anyMatch(entry ->
                            entry.id().equals("etherology:" + id)),
                    description + ":" + id
            );
        }
    }

    private static void assertOnlyRequiredTagIds(
            String json,
            String description,
            Set<String> expectedRequiredIds
    ) {
        JsonArray values = parseObject(json).getAsJsonArray("values");
        assertNotNull(values, description);
        Set<String> actualRequiredIds = new LinkedHashSet<>();
        for (JsonElement value : values) {
            if (value.isJsonPrimitive()) {
                actualRequiredIds.add(value.getAsString());
                continue;
            }
            JsonObject objectValue = value.getAsJsonObject();
            if (objectValue.get("required").getAsBoolean()) {
                actualRequiredIds.add(objectValue.get("id").getAsString());
            }
        }
        assertEquals(expectedRequiredIds, actualRequiredIds, description);
    }

    private static Set<String> namespacedMetalIds() {
        Set<String> ids = new LinkedHashSet<>();
        METALS.keySet().forEach(id -> ids.add("etherology:" + id));
        return Collections.unmodifiableSet(ids);
    }

    private static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        assertTrue(element.isJsonObject());
        return element.getAsJsonObject();
    }

    private static Map<String, Set<String>> ownerSets() {
        Map<String, Set<String>> owners = new LinkedHashMap<>();
        for (String id : METALS.keySet()) owners.put(id, new LinkedHashSet<>());
        return owners;
    }

    private static List<BlockItemBinding> expectedItemBindings() {
        return METALS.values().stream()
                .map(metal -> new BlockItemBinding(
                        metal.id(),
                        metal.blockField(),
                        metal.itemField()
                ))
                .toList();
    }

    private static List<BlockFieldBinding> expectedBlockBindings() {
        return METALS.values().stream()
                .map(metal -> new BlockFieldBinding(metal.id(), metal.blockField()))
                .toList();
    }

    private static long count(List<String> entries, String expected) {
        return entries.stream().filter(expected::equals).count();
    }

    private static ClassReader classReader(JarFile jar, String entryName) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        assertNotNull(entry, entryName);
        try (var input = jar.getInputStream(entry)) {
            return new ClassReader(input);
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

    private static void requireRegularFile(Path path) {
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
    }

    private static List<Artifact> artifacts() throws IOException {
        return List.of(
                artifact("commonJar", "common JAR", false, false),
                artifact(
                        "fabricTransformedCommonJar",
                        "Fabric-transformed common JAR",
                        false,
                        false
                ),
                artifact(
                        "forgeTransformedCommonJar",
                        "Forge-transformed common JAR",
                        false,
                        false
                ),
                artifact(
                        "fabricDevelopmentJar",
                        "Fabric development JAR",
                        true,
                        true
                ),
                artifact(
                        "fabricProductionJar",
                        "Fabric remapped production JAR",
                        true,
                        true
                ),
                artifact("forgeShadowJar", "Forge shadow JAR", true, false)
        );
    }

    private static Artifact artifact(
            String suffix,
            String description,
            boolean includesResources,
            boolean fabricApplication
    ) throws IOException {
        Path path = requiredPath("etherology.metalBlocks." + suffix);
        requireRegularFile(path);
        return new Artifact(path, description, includesResources, fabricApplication);
    }

    private static Map<String, MetalBlock> metals() {
        Map<String, MetalBlock> metals = new LinkedHashMap<>();
        add(
                metals,
                "AZEL_BLOCK",
                "AZEL_BLOCK_ITEM",
                "azel_block",
                "Azel Block",
                "IRON_BLOCK",
                "LAPIS_BLUE"
        );
        add(
                metals,
                "ETHRIL_BLOCK",
                "ETHRIL_BLOCK_ITEM",
                "ethril_block",
                "Ethril Block",
                "GOLD_BLOCK",
                null
        );
        add(
                metals,
                "EBONY_BLOCK",
                "EBONY_BLOCK_ITEM",
                "ebony_block",
                "Ebony Block",
                "DIAMOND_BLOCK",
                "ORANGE"
        );
        return Collections.unmodifiableMap(metals);
    }

    private static void add(
            Map<String, MetalBlock> metals,
            String blockField,
            String itemField,
            String id,
            String englishName,
            String baseField,
            String mapColorField
    ) {
        metals.put(
                id,
                new MetalBlock(
                        blockField,
                        itemField,
                        id,
                        englishName,
                        baseField,
                        mapColorField
                )
        );
    }

    private record MetalBlock(
            String blockField,
            String itemField,
            String id,
            String englishName,
            String baseField,
            String mapColorField
    ) {
    }

    private record Artifact(
            Path path,
            String description,
            boolean includesResources,
            boolean fabricApplication
    ) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }

    private record ResourceSpec(String entry, Path canonicalFile) {
    }

    private record TagValue(String id, boolean required) {
    }

    private record ClassShape(
            int classAccess,
            Map<String, Integer> fieldAccess,
            Map<String, String> fieldDescriptors,
            List<String> publicMethods,
            int privateConstructorCount
    ) {
    }

    private record BlockItemBinding(String id, String blockField, String itemField) {
    }

    private record BlockFieldBinding(String id, String blockField) {
    }

    private record FieldReference(String owner, String name) {
    }

    private record MethodReference(String owner, String name) {
    }

    private record TypeInstruction(int opcode, String type) {
    }

    private record MethodCode(
            List<FieldReference> staticFieldReads,
            List<MethodReference> invocations,
            List<TypeInstruction> typeInstructions
    ) {

        private long countInvocation(String owner, String name) {
            return invocations.stream()
                    .filter(invocation -> invocation.owner().equals(owner)
                            && invocation.name().equals(name))
                    .count();
        }

        private long countTypeInstruction(int opcode, String type) {
            return typeInstructions.stream()
                    .filter(instruction -> instruction.opcode() == opcode
                            && instruction.type().equals(type))
                    .count();
        }

        private boolean referencesOwner(String owner) {
            return staticFieldReads.stream().anyMatch(field -> field.owner().equals(owner))
                    || invocations.stream().anyMatch(invocation ->
                    invocation.owner().equals(owner))
                    || typeInstructions.stream().anyMatch(instruction ->
                    instruction.type().equals(owner));
        }
    }

    private record MethodInvocation(String method, String owner, String name) {
    }

    private record InvocationInventory(
            List<MethodInvocation> invocations,
            int directRegistryCalls
    ) {

        private long count(String owner, String name) {
            return invocations.stream()
                    .filter(invocation -> invocation.owner().equals(owner)
                            && invocation.name().equals(name))
                    .count();
        }

        private long countInMethod(String method, String owner, String name) {
            return invocations.stream()
                    .filter(invocation -> invocation.method().equals(method)
                            && invocation.owner().equals(owner)
                            && invocation.name().equals(name))
                    .count();
        }
    }

    private record ClassOwnership(
            Set<String> metalIds,
            boolean sharedDeferredRegistration,
            boolean directRegistryRegistration
    ) {
    }

    private record MinecraftNames(
            String blocksOwner,
            String mapColorOwner,
            String settingsOwner,
            String blockOwner,
            String itemOwner,
            String itemSettingsOwner,
            String blockItemOwner,
            String copyMethod,
            String mapColorMethod,
            String appendBlocksMethod,
            String blockItemsField,
            Map<String, String> baseFields,
            Map<String, String> mapColorFields
    ) {

        private static MinecraftNames named() {
            return new MinecraftNames(
                    "net/minecraft/block/Blocks",
                    "net/minecraft/block/MapColor",
                    "net/minecraft/block/AbstractBlock$Settings",
                    "net/minecraft/block/Block",
                    "net/minecraft/item/Item",
                    "net/minecraft/item/Item$Settings",
                    "net/minecraft/item/BlockItem",
                    "copy",
                    "mapColor",
                    "appendBlocks",
                    "BLOCK_ITEMS",
                    Map.of(
                            "IRON_BLOCK", "IRON_BLOCK",
                            "GOLD_BLOCK", "GOLD_BLOCK",
                            "DIAMOND_BLOCK", "DIAMOND_BLOCK"
                    ),
                    Map.of("LAPIS_BLUE", "LAPIS_BLUE", "ORANGE", "ORANGE")
            );
        }

        private static MinecraftNames intermediary() {
            return new MinecraftNames(
                    "net/minecraft/class_2246",
                    "net/minecraft/class_3620",
                    "net/minecraft/class_4970$class_2251",
                    "net/minecraft/class_2248",
                    "net/minecraft/class_1792",
                    "net/minecraft/class_1792$class_1793",
                    "net/minecraft/class_1747",
                    "method_9630",
                    "method_31710",
                    "method_7713",
                    "field_8003",
                    Map.of(
                            "IRON_BLOCK", "field_10085",
                            "GOLD_BLOCK", "field_10205",
                            "DIAMOND_BLOCK", "field_10201"
                    ),
                    Map.of(
                            "LAPIS_BLUE", "field_15980",
                            "ORANGE", "field_15987"
                    )
            );
        }

        private String baseField(MetalBlock metal) {
            return baseFields.get(metal.baseField());
        }

        private String mapColorField(MetalBlock metal) {
            return mapColorFields.get(metal.mapColorField());
        }
    }
}
