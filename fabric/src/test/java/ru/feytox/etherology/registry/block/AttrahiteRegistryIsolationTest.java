package ru.feytox.etherology.registry.block;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AttrahiteRegistryIsolationTest {

    private static final String DECO_BLOCKS =
            "ru/feytox/etherology/registry/block/DecoBlocks.class";
    private static final String DECO_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/DecoBlockItems.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String SHARED_BLOCKS =
            "ru/feytox/etherology/registry/block/SharedAttrahiteBlocks.class";
    private static final String SHARED_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/SharedAttrahiteBlockItems.class";
    private static final String DECO_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/DecoBlocks";
    private static final String SHARED_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/SharedAttrahiteBlocks";
    private static final String SHARED_BLOCK_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedAttrahiteBlockItems";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String AUTO_BLOCK_LOOT_TABLE =
            "ru/feytox/etherology/registry/block/AutoBlockLootTable";
    private static final List<String> FIELDS = List.of(
            "ATTRAHITE",
            "ATTRAHITE_BRICKS",
            "ATTRAHITE_BRICK_SLAB",
            "ATTRAHITE_BRICK_STAIRS"
    );
    private static final List<String> IDS = List.of(
            "attrahite",
            "attrahite_bricks",
            "attrahite_brick_slab",
            "attrahite_brick_stairs"
    );
    private static final List<String> SLITHERITE_AUTO_LOOT_FIELDS = List.of(
            "SLITHERITE",
            "SLITHERITE_STAIRS",
            "SLITHERITE_SLAB",
            "SLITHERITE_WALL"
    );

    @Test
    void decorativeFieldsAliasSharedBlocksBeforeMarkingOnlyGenericDrops()
            throws IOException {
        AliasAndLootTrace trace = aliasAndLootTrace();

        assertEquals(FIELDS, new ArrayList<>(trace.fields().keySet()));
        for (FieldDefinition field : trace.fields().values()) {
            assertEquals(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    field.access()
            );
            assertEquals("Lnet/minecraft/block/Block;", field.descriptor());
        }
        assertEquals(
                List.of(
                        "SharedAttrahiteBlocks#ATTRAHITE",
                        "RegistrySupplier#get",
                        "DecoBlocks#ATTRAHITE",
                        "SharedAttrahiteBlocks#ATTRAHITE_BRICKS",
                        "RegistrySupplier#get",
                        "DecoBlocks#ATTRAHITE_BRICKS",
                        "SharedAttrahiteBlocks#ATTRAHITE_BRICK_SLAB",
                        "RegistrySupplier#get",
                        "DecoBlocks#ATTRAHITE_BRICK_SLAB",
                        "SharedAttrahiteBlocks#ATTRAHITE_BRICK_STAIRS",
                        "RegistrySupplier#get",
                        "DecoBlocks#ATTRAHITE_BRICK_STAIRS"
                ),
                trace.aliasEvents()
        );
        assertEquals(
                List.of(
                        "ATTRAHITE_BRICKS",
                        "ATTRAHITE_BRICK_SLAB",
                        "ATTRAHITE_BRICK_STAIRS"
                ),
                trace.autoLootFields()
        );
        assertFalse(trace.autoLootFields().contains("ATTRAHITE"));
        assertEquals(SLITHERITE_AUTO_LOOT_FIELDS, trace.slitheriteAutoLootFields());
        assertEquals(Set.of(), trace.legacyIds());
        for (String autoLootField : trace.autoLootFields()) {
            assertTrue(
                    trace.aliasWriteIndexes().get(autoLootField)
                            < trace.autoLootIndexes().get(autoLootField),
                    autoLootField
            );
        }
    }

    @Test
    void legacyItemRegistryOwnsNoAttrahiteFieldsOrRegistrationIds()
            throws IOException {
        Set<String> forbiddenFields = new LinkedHashSet<>(FIELDS);
        forbiddenFields.add("ATTRAHITE_ITEM");
        forbiddenFields.add("ATTRAHITE_BRICKS_ITEM");
        forbiddenFields.add("ATTRAHITE_BRICK_SLAB_ITEM");
        forbiddenFields.add("ATTRAHITE_BRICK_STAIRS_ITEM");

        assertEquals(Set.of(), declaredFields(DECO_BLOCK_ITEMS, forbiddenFields));
        assertEquals(Set.of(), exactStringConstants(DECO_BLOCK_ITEMS, Set.copyOf(IDS)));
        assertFalse(referencesOwner(DECO_BLOCK_ITEMS, SHARED_BLOCK_ITEMS_OWNER));
        assertFalse(referencesOwner(DECO_BLOCKS, SHARED_BLOCK_ITEMS_OWNER));
    }

    @Test
    void fabricAttachesSharedBlocksThenItemsBeforeLegacyContent()
            throws IOException {
        assertEquals(
                List.of(
                        SHARED_BLOCKS_OWNER + "#register",
                        SHARED_BLOCK_ITEMS_OWNER + "#register",
                        "ru/feytox/etherology/registry/block/ExtraBlocksRegistry#registerAll",
                        "ru/feytox/etherology/registry/item/EItems#registerItems"
                ),
                referencedMethods(
                        FABRIC_INITIALIZER,
                        "initialize",
                        Set.of(
                                SHARED_BLOCKS_OWNER,
                                SHARED_BLOCK_ITEMS_OWNER,
                                "ru/feytox/etherology/registry/block/ExtraBlocksRegistry",
                                "ru/feytox/etherology/registry/item/EItems"
                        )
                )
        );
    }

    @Test
    void onlySharedOwnersRegisterAttrahiteAcrossCommonAndFabricOutputs()
            throws IOException, URISyntaxException {
        assertEquals(1, resources(SHARED_BLOCKS).size());
        assertEquals(1, resources(SHARED_BLOCK_ITEMS).size());

        Map<String, URL> containers = new LinkedHashMap<>();
        URL sharedBlocks = requiredResource(SHARED_BLOCKS);
        URL decoBlocks = requiredResource(DECO_BLOCKS);
        containers.put(containerKey(sharedBlocks, SHARED_BLOCKS), sharedBlocks);
        containers.put(containerKey(decoBlocks, DECO_BLOCKS), decoBlocks);

        Map<String, Set<String>> ownersById = new LinkedHashMap<>();
        for (String id : IDS) {
            ownersById.put(id, new LinkedHashSet<>());
        }
        for (Map.Entry<String, URL> container : containers.entrySet()) {
            for (ClassReader reader : projectClasses(
                    container.getValue(),
                    container.getKey().equals(containerKey(sharedBlocks, SHARED_BLOCKS))
                            ? SHARED_BLOCKS
                            : DECO_BLOCKS
            )) {
                RegistrationOwnership ownership = registrationOwnership(reader);
                if (!ownership.registersContent()) {
                    continue;
                }
                for (String id : ownership.ids()) {
                    ownersById.get(id).add(ownership.owner());
                }
            }
        }

        for (String id : IDS) {
            assertEquals(
                    Set.of(SHARED_BLOCKS_OWNER, SHARED_BLOCK_ITEMS_OWNER),
                    ownersById.get(id),
                    id
            );
        }
    }

    private static AliasAndLootTrace aliasAndLootTrace() throws IOException {
        Map<String, FieldDefinition> fields = new LinkedHashMap<>();
        List<String> aliasEvents = new ArrayList<>();
        List<String> autoLootFields = new ArrayList<>();
        List<String> slitheriteAutoLootFields = new ArrayList<>();
        Set<String> legacyIds = new LinkedHashSet<>();
        Map<String, Integer> aliasWriteIndexes = new LinkedHashMap<>();
        Map<String, Integer> autoLootIndexes = new LinkedHashMap<>();
        classReader(DECO_BLOCKS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (FIELDS.contains(name)) {
                    fields.put(name, new FieldDefinition(access, descriptor));
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String methodName,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!methodName.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    private int instructionIndex;
                    private String pendingAlias;
                    private String pendingAutoLootField;
                    private String pendingSlitheriteAutoLootField;

                    @Override
                    public void visitLdcInsn(Object value) {
                        instructionIndex++;
                        if (value instanceof String id && IDS.contains(id)) {
                            legacyIds.add(id);
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        instructionIndex++;
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_BLOCKS_OWNER)
                                && FIELDS.contains(name)) {
                            pendingAlias = name;
                            aliasEvents.add("SharedAttrahiteBlocks#" + name);
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(DECO_BLOCKS_OWNER)
                                && name.equals(pendingAlias)) {
                            aliasEvents.add("DecoBlocks#" + name);
                            aliasWriteIndexes.put(name, instructionIndex);
                            pendingAlias = null;
                        }
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(DECO_BLOCKS_OWNER)) {
                            if (FIELDS.contains(name)) {
                                pendingAutoLootField = name;
                                pendingSlitheriteAutoLootField = null;
                            } else if (SLITHERITE_AUTO_LOOT_FIELDS.contains(name)) {
                                pendingAutoLootField = null;
                                pendingSlitheriteAutoLootField = name;
                            } else {
                                pendingAutoLootField = null;
                                pendingSlitheriteAutoLootField = null;
                            }
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
                        instructionIndex++;
                        if (pendingAlias != null
                                && owner.equals(REGISTRY_SUPPLIER)
                                && name.equals("get")) {
                            aliasEvents.add("RegistrySupplier#get");
                        }
                        if (owner.equals(AUTO_BLOCK_LOOT_TABLE)
                                && name.equals("markAsAuto")) {
                            if (pendingAutoLootField != null) {
                                autoLootFields.add(pendingAutoLootField);
                                autoLootIndexes.put(pendingAutoLootField, instructionIndex);
                            } else {
                                assertNotNull(pendingSlitheriteAutoLootField);
                                slitheriteAutoLootFields.add(
                                        pendingSlitheriteAutoLootField
                                );
                            }
                            pendingAutoLootField = null;
                            pendingSlitheriteAutoLootField = null;
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        instructionIndex++;
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        instructionIndex++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new AliasAndLootTrace(
                fields,
                aliasEvents,
                autoLootFields,
                slitheriteAutoLootFields,
                legacyIds,
                aliasWriteIndexes,
                autoLootIndexes
        );
    }

    private static Set<String> declaredFields(
            String resource,
            Set<String> expectedNames
    ) throws IOException {
        Set<String> fields = new LinkedHashSet<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (expectedNames.contains(name)) {
                    fields.add(name);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return fields;
    }

    private static Set<String> exactStringConstants(
            String resource,
            Set<String> expectedValues
    ) throws IOException {
        Set<String> constants = new LinkedHashSet<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        if (value instanceof String text && expectedValues.contains(text)) {
                            constants.add(text);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return constants;
    }

    private static boolean referencesOwner(String resource, String expectedOwner)
            throws IOException {
        boolean[] found = {false};
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
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
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (owner.equals(expectedOwner)) {
                            found[0] = true;
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
                        if (owner.equals(expectedOwner)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    private static List<String> referencedMethods(
            String resource,
            String methodName,
            Set<String> owners
    ) throws IOException {
        List<String> invocations = new ArrayList<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)) {
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
                        if (owners.contains(owner)) {
                            invocations.add(owner + "#" + name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static RegistrationOwnership registrationOwnership(ClassReader reader) {
        Set<String> ids = new LinkedHashSet<>();
        boolean[] registersContent = {false};
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
                        if (value instanceof String id && IDS.contains(id)) {
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
                        if (owner.equals(SHARED_DEFERRED_REGISTER)
                                && name.equals("register")) {
                            registersContent[0] = true;
                        }
                        if (isDirectRegistryOwner(owner) && name.equals("register")) {
                            registersContent[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new RegistrationOwnership(reader.getClassName(), ids, registersContent[0]);
    }

    private static boolean isDirectRegistryOwner(String owner) {
        return owner.equals("net/minecraft/registry/Registry")
                || owner.equals("net/minecraft/class_2378")
                || owner.equals("net/minecraft/core/Registry");
    }

    private static List<ClassReader> projectClasses(URL resource, String resourceName)
            throws IOException, URISyntaxException {
        if (resource.getProtocol().equals("jar")) {
            JarURLConnection connection = (JarURLConnection) resource.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                List<ClassReader> readers = new ArrayList<>();
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (isProjectClass(entry.getName())) {
                        try (InputStream stream = jar.getInputStream(entry)) {
                            readers.add(new ClassReader(stream));
                        }
                    }
                }
                return readers;
            }
        }

        assertEquals("file", resource.getProtocol(), resource.toString());
        Path classFile = Path.of(resource.toURI());
        Path root = classFile;
        for (String ignored : resourceName.split("/")) {
            root = root.getParent();
        }
        Path projectPackage = root.resolve("ru/feytox/etherology");
        if (!Files.isDirectory(projectPackage)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(projectPackage)) {
            List<ClassReader> readers = new ArrayList<>();
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".class"))
                    .toList()) {
                readers.add(new ClassReader(Files.readAllBytes(path)));
            }
            return readers;
        }
    }

    private static boolean isProjectClass(String entry) {
        return entry.startsWith("ru/feytox/etherology/") && entry.endsWith(".class");
    }

    private static String containerKey(URL resource, String resourceName)
            throws IOException {
        if (resource.getProtocol().equals("jar")) {
            JarURLConnection connection = (JarURLConnection) resource.openConnection();
            return connection.getJarFileURL().toExternalForm();
        }
        String external = resource.toExternalForm();
        return external.substring(0, external.length() - resourceName.length());
    }

    private static List<URL> resources(String resource) throws IOException {
        return AttrahiteRegistryIsolationTest.class
                .getClassLoader()
                .resources(resource)
                .toList();
    }

    private static URL requiredResource(String resource) {
        URL url = AttrahiteRegistryIsolationTest.class
                .getClassLoader()
                .getResource(resource);
        assertNotNull(url, "Missing class resource " + resource);
        return url;
    }

    private static ClassReader classReader(String resource) throws IOException {
        InputStream stream = AttrahiteRegistryIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record FieldDefinition(int access, String descriptor) {
    }

    private record AliasAndLootTrace(
            Map<String, FieldDefinition> fields,
            List<String> aliasEvents,
            List<String> autoLootFields,
            List<String> slitheriteAutoLootFields,
            Set<String> legacyIds,
            Map<String, Integer> aliasWriteIndexes,
            Map<String, Integer> autoLootIndexes
    ) {
    }

    private record RegistrationOwnership(
            String owner,
            Set<String> ids,
            boolean registersContent
    ) {
    }
}
