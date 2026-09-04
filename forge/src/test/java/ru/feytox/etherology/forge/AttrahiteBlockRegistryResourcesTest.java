package ru.feytox.etherology.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AttrahiteBlockRegistryResourcesTest {

    private static final String SHARED_BLOCKS =
            "ru/feytox/etherology/registry/block/SharedAttrahiteBlocks.class";
    private static final String SHARED_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/SharedAttrahiteBlocks";
    private static final String SHARED_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/SharedAttrahiteBlockItems.class";
    private static final String SHARED_BLOCK_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedAttrahiteBlockItems";
    private static final String SHARED_DEFERRED_REGISTER_OWNER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String SHARED_DEFERRED_REGISTER_DESCRIPTOR =
            "Lru/feytox/etherology/registry/SharedDeferredRegister;";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final Set<String> DIRECT_REGISTRY_OWNERS = Set.of(
            "net/minecraft/registry/Registry",
            "net/minecraft/class_2378",
            "net/minecraft/core/Registry",
            "dev/architectury/registry/registries/DeferredRegister"
    );
    private static final List<AttrahiteBlock> ATTRAHITE_BLOCKS = List.of(
            new AttrahiteBlock(
                    "attrahite",
                    "ATTRAHITE",
                    "ATTRAHITE_ITEM",
                    "Attrahite",
                    "Аттрахит"
            ),
            new AttrahiteBlock(
                    "attrahite_bricks",
                    "ATTRAHITE_BRICKS",
                    "ATTRAHITE_BRICKS_ITEM",
                    "Attrahite Bricks",
                    "Аттрахитовые кирпичи"
            ),
            new AttrahiteBlock(
                    "attrahite_brick_slab",
                    "ATTRAHITE_BRICK_SLAB",
                    "ATTRAHITE_BRICK_SLAB_ITEM",
                    "Attrahite Brick Slab",
                    "Плита из аттрахитовых кирпичей"
            ),
            new AttrahiteBlock(
                    "attrahite_brick_stairs",
                    "ATTRAHITE_BRICK_STAIRS",
                    "ATTRAHITE_BRICK_STAIRS_ITEM",
                    "Attrahite Brick Stairs",
                    "Ступеньки из аттрахитовых кирпичей"
            )
    );
    private static final List<String> ATTRAHITE_IDS = ATTRAHITE_BLOCKS.stream()
            .map(AttrahiteBlock::id)
            .toList();
    private static final String PICKAXE_TAG =
            "data/minecraft/tags/blocks/mineable/pickaxe.json";
    private static final String NEEDS_STONE_TOOL_TAG =
            "data/minecraft/tags/blocks/needs_stone_tool.json";
    private static final String BLOCK_SLABS_TAG =
            "data/minecraft/tags/blocks/slabs.json";
    private static final String BLOCK_STAIRS_TAG =
            "data/minecraft/tags/blocks/stairs.json";
    private static final String ITEM_SLABS_TAG =
            "data/minecraft/tags/items/slabs.json";
    private static final String ITEM_STAIRS_TAG =
            "data/minecraft/tags/items/stairs.json";
    private static final Map<String, Set<String>> EXPECTED_TAG_MEMBERS = Map.of(
            PICKAXE_TAG,
            Set.copyOf(ATTRAHITE_IDS),
            NEEDS_STONE_TOOL_TAG,
            Set.of("attrahite"),
            BLOCK_SLABS_TAG,
            Set.of("attrahite_brick_slab"),
            BLOCK_STAIRS_TAG,
            Set.of("attrahite_brick_stairs"),
            ITEM_SLABS_TAG,
            Set.of("attrahite_brick_slab"),
            ITEM_STAIRS_TAG,
            Set.of("attrahite_brick_stairs")
    );

    @Test
    void everyArtifactHasOneSharedOwnerForEachAttrahiteBlockAndItem()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                assertEquals(1, count(entries, SHARED_BLOCKS), artifact.description());
                assertEquals(1, count(entries, SHARED_BLOCK_ITEMS), artifact.description());

                ClassReader blocks = classReader(jar, SHARED_BLOCKS);
                ClassReader items = classReader(jar, SHARED_BLOCK_ITEMS);
                assertExactCatalogShape(
                        artifact.description(),
                        blocks,
                        "BLOCKS",
                        ATTRAHITE_BLOCKS.stream().map(AttrahiteBlock::blockField).toList()
                );
                assertExactCatalogShape(
                        artifact.description(),
                        items,
                        "ITEMS",
                        ATTRAHITE_BLOCKS.stream().map(AttrahiteBlock::itemField).toList()
                );
                assertEquals(
                        ATTRAHITE_IDS,
                        registrationIds(
                                blocks,
                                SHARED_DEFERRED_REGISTER_OWNER,
                                "register"
                        ),
                        artifact.description()
                );
                assertEquals(
                        ATTRAHITE_IDS,
                        registrationIds(
                                items,
                                SHARED_BLOCK_ITEMS_OWNER,
                                "registerBlockItem"
                        ),
                        artifact.description()
                );
                assertEquals(
                        ATTRAHITE_BLOCKS.stream().map(AttrahiteBlock::blockField).toList(),
                        blockSupplierReads(items),
                        artifact.description()
                );
                assertNoLoaderSpecificReferences(artifact.description(), blocks);
                assertNoLoaderSpecificReferences(artifact.description(), items);
                assertOnlySharedRegistrationOwners(artifact, jar, entries);
                assertBootstrapOrder(artifact, jar);
            }
        }
    }

    @Test
    void canonicalAssetsDataLanguagesAndTagsMatchEveryApplicationArtifact()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.attrahiteBlocks.repositoryRoot");
        for (CanonicalResource resource : canonicalResources()) {
            Path canonical = repositoryRoot.resolve(resource.repositoryPath());
            requireRegularFile(canonical);
            assertEquals(
                    resource.sha256(),
                    sha256(Files.readAllBytes(canonical)),
                    resource.jarEntry()
            );
        }
        assertLanguage(
                repositoryRoot,
                "src/client/resources/assets/etherology/lang/en_us.json",
                false
        );
        assertLanguage(
                repositoryRoot,
                "src/main/generated/assets/etherology/lang/ru_ru.json",
                true
        );
        assertCanonicalTagMembership(repositoryRoot);

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                for (CanonicalResource resource : canonicalResources()) {
                    assertCanonicalResource(repositoryRoot, artifact, jar, resource);
                }
                assertPackagedLanguage(
                        repositoryRoot,
                        artifact,
                        jar,
                        "assets/etherology/lang/en_us.json",
                        "src/client/resources/assets/etherology/lang/en_us.json",
                        false
                );
                assertPackagedLanguage(
                        repositoryRoot,
                        artifact,
                        jar,
                        "assets/etherology/lang/ru_ru.json",
                        "src/main/generated/assets/etherology/lang/ru_ru.json",
                        true
                );
            }
        }
    }

    @Test
    void forgeKeepsWorldgenAndAspectDataOutsideTheBoundedAttrahiteSlice()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            if (!artifact.forgeApplication()) {
                continue;
            }
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                assertFalse(
                        entries.stream().anyMatch(entry ->
                                entry.startsWith("data/etherology/worldgen/")),
                        artifact.description()
                );
                assertFalse(
                        entries.stream().anyMatch(entry ->
                                entry.startsWith("data/etherology/etherology/aspects/")),
                        artifact.description()
                );
            }
        }
    }

    private static void assertExactCatalogShape(
            String description,
            ClassReader reader,
            String ownerField,
            List<String> supplierFields
    ) {
        ClassShape shape = classShape(reader);
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                shape.classAccess() & (Opcodes.ACC_PUBLIC
                        | Opcodes.ACC_FINAL
                        | Opcodes.ACC_INTERFACE
                        | Opcodes.ACC_ABSTRACT),
                description
        );
        List<String> expectedFields = new ArrayList<>();
        expectedFields.add(ownerField);
        expectedFields.addAll(supplierFields);
        assertEquals(expectedFields, new ArrayList<>(shape.fields().keySet()), description);
        assertEquals(
                new FieldShape(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        SHARED_DEFERRED_REGISTER_DESCRIPTOR
                ),
                shape.fields().get(ownerField),
                description
        );
        for (String supplierField : supplierFields) {
            assertEquals(
                    new FieldShape(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            REGISTRY_SUPPLIER_DESCRIPTOR
                    ),
                    shape.fields().get(supplierField),
                    description + ":" + supplierField
            );
        }
        assertEquals(List.of("register()V"), shape.publicMethods(), description);
        assertEquals(1, shape.privateConstructorCount(), description);
    }

    private static ClassShape classShape(ClassReader reader) {
        int[] classAccess = {0};
        Map<String, FieldShape> fields = new LinkedHashMap<>();
        List<String> publicMethods = new ArrayList<>();
        int[] privateConstructorCount = {0};
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
                int relevantAccess = access & (Opcodes.ACC_PUBLIC
                        | Opcodes.ACC_PRIVATE
                        | Opcodes.ACC_PROTECTED
                        | Opcodes.ACC_STATIC
                        | Opcodes.ACC_FINAL);
                fields.put(name, new FieldShape(relevantAccess, descriptor));
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
                Collections.unmodifiableMap(fields),
                List.copyOf(publicMethods),
                privateConstructorCount[0]
        );
    }

    private static List<String> registrationIds(
            ClassReader reader,
            String expectedOwner,
            String expectedMethod
    ) {
        List<String> ids = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && ATTRAHITE_IDS.contains(id)) {
                            pendingId = id;
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
                        if (owner.equals(expectedOwner) && name.equals(expectedMethod)) {
                            assertNotNull(pendingId, expectedOwner + "#" + expectedMethod);
                            ids.add(pendingId);
                            pendingId = null;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return List.copyOf(ids);
    }

    private static List<String> blockSupplierReads(ClassReader reader) {
        List<String> fieldReads = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals(SHARED_BLOCKS_OWNER)) {
                            fieldReads.add(name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return List.copyOf(fieldReads);
    }

    private static void assertNoLoaderSpecificReferences(
            String description,
            ClassReader reader
    ) {
        Set<String> references = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            private void check(String value) {
                if (value != null && (value.contains("net/fabricmc/")
                        || value.contains("net/minecraftforge/"))) {
                    references.add(value);
                }
            }

            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                check(superName);
                if (interfaces != null) {
                    for (String value : interfaces) {
                        check(value);
                    }
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
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(Set.of(), references, description);
    }

    private static void assertOnlySharedRegistrationOwners(
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
            String owner = entry.substring(0, entry.length() - ".class".length());
            for (String id : ownership.attrahiteIds()) {
                if (ownership.sharedDeferredRegistration()) {
                    deferredOwners.get(id).add(owner);
                }
                if (ownership.directRegistration()) {
                    directOwners.get(id).add(owner);
                }
            }
        }

        Set<String> expectedOwners = Set.of(
                SHARED_BLOCKS_OWNER,
                SHARED_BLOCK_ITEMS_OWNER
        );
        for (String id : ATTRAHITE_IDS) {
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
        AtomicBoolean sharedDeferredRegistration = new AtomicBoolean();
        AtomicBoolean directRegistration = new AtomicBoolean();
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
                        if (value instanceof String id && ATTRAHITE_IDS.contains(id)) {
                            ids.add(id);
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
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && name.equals("register")) {
                            sharedDeferredRegistration.set(true);
                        }
                        if (DIRECT_REGISTRY_OWNERS.contains(owner)
                                && name.equals("register")) {
                            directRegistration.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassOwnership(
                Collections.unmodifiableSet(ids),
                sharedDeferredRegistration.get(),
                directRegistration.get()
        );
    }

    private static Map<String, Set<String>> ownerSets() {
        Map<String, Set<String>> owners = new LinkedHashMap<>();
        for (String id : ATTRAHITE_IDS) {
            owners.put(id, new LinkedHashSet<>());
        }
        return owners;
    }

    private static void assertBootstrapOrder(Artifact artifact, JarFile jar)
            throws IOException {
        String initializer = artifact.fabricApplication()
                ? FABRIC_INITIALIZER
                : COMMON_BOOTSTRAP;
        List<String> invocations = methodInvocations(
                classReader(jar, initializer),
                "initialize"
        );
        String attrahiteBlocks = SHARED_BLOCKS_OWNER + "#register()V";
        String attrahiteItems = SHARED_BLOCK_ITEMS_OWNER + "#register()V";
        assertInvocationOnce(artifact.description(), invocations, attrahiteBlocks);
        assertInvocationOnce(artifact.description(), invocations, attrahiteItems);
        assertTrue(
                invocations.indexOf(attrahiteBlocks) < invocations.indexOf(attrahiteItems),
                artifact.description()
        );

        if (artifact.fabricApplication()) {
            String legacyItems =
                    "ru/feytox/etherology/registry/item/EItems#registerItems()V";
            assertInvocationOnce(artifact.description(), invocations, legacyItems);
            assertTrue(
                    invocations.indexOf(attrahiteItems) < invocations.indexOf(legacyItems),
                    artifact.description()
            );
            return;
        }

        List<String> orderedBoundary = List.of(
                "ru/feytox/etherology/registry/block/SharedForestLanternBlocks"
                        + "#register()V",
                attrahiteBlocks,
                "ru/feytox/etherology/registry/item/SharedMetalBlockItems#register()V",
                "ru/feytox/etherology/registry/item/SharedForestLanternBlockItems"
                        + "#register()V",
                attrahiteItems,
                "ru/feytox/etherology/registry/item/SharedItems#register()V"
        );
        for (String invocation : orderedBoundary) {
            assertInvocationOnce(artifact.description(), invocations, invocation);
        }
        for (int index = 1; index < orderedBoundary.size(); index++) {
            assertTrue(
                    invocations.indexOf(orderedBoundary.get(index - 1))
                            < invocations.indexOf(orderedBoundary.get(index)),
                    artifact.description()
            );
        }
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
                if (!name.equals(targetMethod)) {
                    return null;
                }
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
        return List.copyOf(invocations);
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

    private static void assertCanonicalResource(
            Path repositoryRoot,
            Artifact artifact,
            JarFile jar,
            CanonicalResource resource
    ) throws IOException {
        List<JarEntry> entries = jar.stream()
                .filter(entry -> entry.getName().equals(resource.jarEntry()))
                .toList();
        if (!artifact.includesResources()) {
            assertEquals(List.of(), entries, artifact.description() + ":" + resource.jarEntry());
            return;
        }
        assertEquals(1, entries.size(), artifact.description() + ":" + resource.jarEntry());
        byte[] packaged = jar.getInputStream(entries.get(0)).readAllBytes();
        assertArrayEquals(
                Files.readAllBytes(repositoryRoot.resolve(resource.repositoryPath())),
                packaged,
                artifact.description() + ":" + resource.jarEntry()
        );
        assertEquals(
                resource.sha256(),
                sha256(packaged),
                artifact.description() + ":" + resource.jarEntry()
        );
    }

    private static void assertLanguage(
            Path repositoryRoot,
            String repositoryPath,
            boolean russian
    ) throws IOException {
        Path languagePath = repositoryRoot.resolve(repositoryPath);
        requireRegularFile(languagePath);
        JsonObject language = parseObject(Files.readString(languagePath));
        for (AttrahiteBlock block : ATTRAHITE_BLOCKS) {
            String expected = russian ? block.russianName() : block.englishName();
            assertEquals(
                    expected,
                    language.get("block.etherology." + block.id()).getAsString(),
                    block.id()
            );
        }
    }

    private static void assertPackagedLanguage(
            Path repositoryRoot,
            Artifact artifact,
            JarFile jar,
            String jarEntry,
            String repositoryPath,
            boolean russian
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(jarEntry);
        if (!artifact.includesResources()) {
            assertNull(entry, artifact.description() + ":" + jarEntry);
            return;
        }
        assertNotNull(entry, artifact.description() + ":" + jarEntry);
        byte[] packaged = jar.getInputStream(entry).readAllBytes();
        assertArrayEquals(
                Files.readAllBytes(repositoryRoot.resolve(repositoryPath)),
                packaged,
                artifact.description() + ":" + jarEntry
        );
        JsonObject language = parseObject(new String(packaged, StandardCharsets.UTF_8));
        for (AttrahiteBlock block : ATTRAHITE_BLOCKS) {
            String expected = russian ? block.russianName() : block.englishName();
            assertEquals(
                    expected,
                    language.get("block.etherology." + block.id()).getAsString(),
                    artifact.description() + ":" + block.id()
            );
        }
    }

    private static void assertCanonicalTagMembership(Path repositoryRoot)
            throws IOException {
        for (Map.Entry<String, Set<String>> expected : EXPECTED_TAG_MEMBERS.entrySet()) {
            Path path = repositoryRoot.resolve("src/main/generated").resolve(expected.getKey());
            requireRegularFile(path);
            JsonObject tag = parseObject(Files.readString(path));
            assertFalse(tag.get("replace").getAsBoolean(), expected.getKey());
            JsonArray values = tag.getAsJsonArray("values");
            assertNotNull(values, expected.getKey());
            List<String> ids = new ArrayList<>();
            for (JsonElement value : values) {
                if (value.isJsonPrimitive()) {
                    ids.add(value.getAsString());
                } else {
                    ids.add(value.getAsJsonObject().get("id").getAsString());
                }
            }
            List<String> attrahiteIds = new ArrayList<>();
            for (String id : ids) {
                if (id.startsWith("etherology:")
                        && ATTRAHITE_IDS.contains(id.substring("etherology:".length()))) {
                    attrahiteIds.add(id.substring("etherology:".length()));
                }
            }
            assertEquals(
                    attrahiteIds.size(),
                    new LinkedHashSet<>(attrahiteIds).size(),
                    expected.getKey()
            );
            assertEquals(
                    expected.getValue(),
                    new LinkedHashSet<>(attrahiteIds),
                    expected.getKey()
            );
        }
    }

    private static List<CanonicalResource> canonicalResources() {
        return List.of(
                generatedAsset(
                        "blockstates/attrahite.json",
                        "714d5913c7743e6fb9d7a5309d91e375c09686bc9e7563594ef90266fedb8467"
                ),
                generatedAsset(
                        "blockstates/attrahite_bricks.json",
                        "28d7fe3a1fd137e338a210878a7a0e811d570a063201f4a8556f827f4d3cee54"
                ),
                generatedAsset(
                        "blockstates/attrahite_brick_slab.json",
                        "527e541bf9cd76b8d03615157930ea1f6d02e944cfaf210541a042ecc7730d70"
                ),
                generatedAsset(
                        "blockstates/attrahite_brick_stairs.json",
                        "a318e997a883e7cc3b1e1193f654f6f9cf350a9d8c4968d6777558120f6b2c85"
                ),
                generatedAsset(
                        "models/block/attrahite.json",
                        "fa2ef32e5c8a05e97b11f2b7e165420b8c58c37a4a8980acb09eb20567f0cfe4"
                ),
                generatedAsset(
                        "models/block/attrahite_bricks.json",
                        "a3ca96e4d64a795741e6d64edd3633ae0de8fe8e358e40a7c5c083607f4997cb"
                ),
                generatedAsset(
                        "models/block/attrahite_brick_slab.json",
                        "6911228282c04ae043603142b6c0f8aa36510fcef2a16981cac99e87f6f9591c"
                ),
                generatedAsset(
                        "models/block/attrahite_brick_slab_top.json",
                        "df34597225adffecc923a226230f9d21375a6991966d6365ca3a0dd73c494514"
                ),
                generatedAsset(
                        "models/block/attrahite_brick_stairs.json",
                        "d450badc33ce469cf88952432be54744c86205c4dc0d14e35ce8bcd7259d7697"
                ),
                generatedAsset(
                        "models/block/attrahite_brick_stairs_inner.json",
                        "b2824d3f7e59a3cfea4bdabc98f479a5e4d635fc277f3dcc0ec61f02e44ef47a"
                ),
                generatedAsset(
                        "models/block/attrahite_brick_stairs_outer.json",
                        "5ccb32dece7c1b4dc11be5b33dfe68f4f8cbb5eb066603afa43fba331aa9f0f3"
                ),
                generatedAsset(
                        "models/item/attrahite.json",
                        "694825d82227bad82c5fe813b72b0539ca5b25001957d086861d3b723a21c6cf"
                ),
                generatedAsset(
                        "models/item/attrahite_bricks.json",
                        "eacdf3ece38696ceb7d78db1aca1f666b12363c9b50e0972e57afc78c6532d4a"
                ),
                generatedAsset(
                        "models/item/attrahite_brick_slab.json",
                        "420600e00f5848cd9f2759cebc73b4062b603cf1647ee5e00cbce16b32fe21ac"
                ),
                generatedAsset(
                        "models/item/attrahite_brick_stairs.json",
                        "08efcd6a8313310b2102ce53d236e456d1e19f17732fba4ea2481702ddebb16e"
                ),
                asset(
                        "textures/block/attrahite.png",
                        "e206aa66882b20816250a6fbfc7080a66dbca55a885020f2bed09d8087e02825"
                ),
                asset(
                        "textures/block/attrahite_bricks.png",
                        "a92bc03adc772da001d8f5eafbe1aaaad4a498776e3913294c640aafa9172be1"
                ),
                data(
                        "etherology/loot_tables/blocks/attrahite.json",
                        "3b9c895f8a19289759a6c9151a0d9232f5f627d00aed8b0498cb010122f01736"
                ),
                data(
                        "etherology/loot_tables/blocks/attrahite_bricks.json",
                        "1064209f17fd68a9d264ef6ec74c3c1b1f7d7e6a89b31ccc44edaa4789f7e397"
                ),
                data(
                        "etherology/loot_tables/blocks/attrahite_brick_slab.json",
                        "ddebe7a95e8d35925d123845aec013bb01f58e990048536686c6ade9033f6a03"
                ),
                data(
                        "etherology/loot_tables/blocks/attrahite_brick_stairs.json",
                        "b9d9b81591401addd844ca78eefc08fcb9597ec400965ba5fc0c56fb6a64acc0"
                ),
                data(
                        "etherology/recipes/attrahite_brick.json",
                        "8db4918b08df6616bfd29fd417251d6f57e314318836eeb4d0ed5552c214e2da"
                ),
                data(
                        "etherology/recipes/attrahite_bricks.json",
                        "54c72f00ce1fa95bd9ff72949a1885dc693d8f5e1af8b27cb78ad3b937d95b69"
                ),
                data(
                        "etherology/recipes/attrahite_brick_slab.json",
                        "f602873759a35a1e2ec4c1c916d739cd5b9e91e1bd90587642864d724dac1db5"
                ),
                data(
                        "etherology/recipes/attrahite_brick_slab_from_"
                                + "attrahite_bricks_stonecutting.json",
                        "373c0bcc25b467ce651c63c415ab2b3ec6c58c656a81a2768212694d67ea11d0"
                ),
                data(
                        "etherology/recipes/attrahite_brick_stairs.json",
                        "e543829fe1acb5127b959ceb85deaf5400f47afd825f329de32bcd6b4c0a8170"
                ),
                data(
                        "etherology/recipes/attrahite_brick_stairs_from_"
                                + "attrahite_bricks_stonecutting.json",
                        "8bc3cc989cc25c03c3f7a67398ed1ad251ba11375af2684e143907e361cad955"
                ),
                data(
                        "etherology/recipes/raw_azel.json",
                        "fda4c9f4d4ee494a90ada9ddb8028ac0f2795a72a01bc9d1318ffa0784ec3224"
                ),
                data(
                        "etherology/recipes/azel_ingot.json",
                        "4fe3dc8f938ad7b279d13d6d928ef5eb352a2d6e947afa629c93738aa09b9ba7"
                ),
                data(
                        "etherology/recipes/azel_ingot_from_blasting.json",
                        "aac75cf79d7afc5eaa959350fac2402d38456ee73cb67875c86288fd7849dfda"
                ),
                data(
                        "etherology/advancements/recipes/misc/attrahite_brick.json",
                        "4c3b06d4ad8e68d85387489c910a8c748ab85450e16e7e9a661051958858fcc3"
                ),
                data(
                        "etherology/advancements/recipes/building_blocks/"
                                + "attrahite_bricks.json",
                        "76e865765ac306d504d1434a21bda1f71fff55ab8709bd6e090f10bce3ed439f"
                ),
                data(
                        "etherology/advancements/recipes/building_blocks/"
                                + "attrahite_brick_slab.json",
                        "650fd3e4b11a9208c5ed72a3bdb82a550a08eb8fcb9eec2e05e5b9d59935cf79"
                ),
                data(
                        "etherology/advancements/recipes/building_blocks/"
                                + "attrahite_brick_slab_from_attrahite_bricks_stonecutting.json",
                        "6ae9a581d196bd1fa8a780543907cf99ac60803b7cf3c332bce83b91ece0eae3"
                ),
                data(
                        "etherology/advancements/recipes/building_blocks/"
                                + "attrahite_brick_stairs.json",
                        "71cb02f0cc38ff4ebf51b46cc5ce6f533717a18e69048f42a6aaa4da36fc84f7"
                ),
                data(
                        "etherology/advancements/recipes/building_blocks/"
                                + "attrahite_brick_stairs_from_attrahite_bricks_stonecutting.json",
                        "358550e9ff8392e3d38992658cda0e87a41f76b16b3782ebbec3a99e7fc26a96"
                ),
                data(
                        "etherology/advancements/recipes/misc/raw_azel.json",
                        "cee3a50d42c5cc539b5b1ec60d31c147a9abce22197d4af054af9beba1da1409"
                ),
                data(
                        "etherology/advancements/recipes/misc/azel_ingot.json",
                        "d26488e88ae6f7330d1a2b091e0e67df459ad1fa33524e6c46fe97aa6a316d3a"
                ),
                data(
                        "etherology/advancements/recipes/misc/azel_ingot_from_blasting.json",
                        "b9da7d184cf5561f393d074a7b1fdde1cae7e40e569ba269b8abbb5b55ff915c"
                ),
                data(
                        "minecraft/tags/blocks/mineable/pickaxe.json",
                        "632ad6edd689227cdb1ef9c8b62796cbfaeda47726896a84b6138e0a96715866"
                ),
                data(
                        "minecraft/tags/blocks/needs_stone_tool.json",
                        "64a01926e983ba9dc0e1fe6d0924bd5d2e4d82defd35e604f85d0c59a1c839ad"
                ),
                data(
                        "minecraft/tags/blocks/slabs.json",
                        "7db9640210269cd1663f47c471d648902eefefd245c9e58b5fcf408c3a6a89fe"
                ),
                data(
                        "minecraft/tags/blocks/stairs.json",
                        "8eecf3f968b80388d401b7ab39b52bcf593950515570db537bd9902d84cd0b1b"
                ),
                data(
                        "minecraft/tags/items/slabs.json",
                        "7db9640210269cd1663f47c471d648902eefefd245c9e58b5fcf408c3a6a89fe"
                ),
                data(
                        "minecraft/tags/items/stairs.json",
                        "8eecf3f968b80388d401b7ab39b52bcf593950515570db537bd9902d84cd0b1b"
                )
        );
    }

    private static CanonicalResource asset(String path, String sha256) {
        return new CanonicalResource(
                "assets/etherology/" + path,
                "src/client/resources/assets/etherology/" + path,
                sha256
        );
    }

    private static CanonicalResource generatedAsset(String path, String sha256) {
        return new CanonicalResource(
                "assets/etherology/" + path,
                "src/main/generated/assets/etherology/" + path,
                sha256
        );
    }

    private static CanonicalResource data(String path, String sha256) {
        return new CanonicalResource(
                "data/" + path,
                "src/main/generated/data/" + path,
                sha256
        );
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        assertTrue(element.isJsonObject());
        return element.getAsJsonObject();
    }

    private static long count(List<String> entries, String expected) {
        return entries.stream().filter(expected::equals).count();
    }

    private static ClassReader classReader(JarFile jar, String entryName)
            throws IOException {
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
                artifact("commonJar", "common JAR", false, false, false),
                artifact(
                        "fabricTransformedCommonJar",
                        "Fabric-transformed common JAR",
                        false,
                        false,
                        false
                ),
                artifact(
                        "forgeTransformedCommonJar",
                        "Forge-transformed common JAR",
                        false,
                        false,
                        false
                ),
                artifact(
                        "fabricDevelopmentJar",
                        "Fabric development JAR",
                        true,
                        true,
                        false
                ),
                artifact(
                        "fabricProductionJar",
                        "Fabric remapped production JAR",
                        true,
                        true,
                        false
                ),
                artifact("forgeShadowJar", "Forge shadow JAR", true, false, true)
        );
    }

    private static Artifact artifact(
            String suffix,
            String description,
            boolean includesResources,
            boolean fabricApplication,
            boolean forgeApplication
    ) throws IOException {
        Path path = requiredPath("etherology.attrahiteBlocks." + suffix);
        requireRegularFile(path);
        return new Artifact(
                path,
                description,
                includesResources,
                fabricApplication,
                forgeApplication
        );
    }

    private record AttrahiteBlock(
            String id,
            String blockField,
            String itemField,
            String englishName,
            String russianName
    ) {
    }

    private record Artifact(
            Path path,
            String description,
            boolean includesResources,
            boolean fabricApplication,
            boolean forgeApplication
    ) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }

    private record CanonicalResource(
            String jarEntry,
            String repositoryPath,
            String sha256
    ) {
    }

    private record FieldShape(int access, String descriptor) {
    }

    private record ClassShape(
            int classAccess,
            Map<String, FieldShape> fields,
            List<String> publicMethods,
            int privateConstructorCount
    ) {
    }

    private record ClassOwnership(
            Set<String> attrahiteIds,
            boolean sharedDeferredRegistration,
            boolean directRegistration
    ) {
    }
}
