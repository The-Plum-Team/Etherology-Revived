package ru.feytox.etherology.forge;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PrimoShardRegistryResourcesTest {

    private static final String CLASS_PREFIX = "ru/feytox/etherology/";
    private static final String SHARED_ITEMS =
            CLASS_PREFIX + "registry/item/SharedPrimoShardItems.class";
    private static final String SHARED_ITEMS_OWNER =
            CLASS_PREFIX + "registry/item/SharedPrimoShardItems";
    private static final String PRIMO_SHARD =
            CLASS_PREFIX + "item/PrimoShard.class";
    private static final String PRIMO_SHARD_OWNER =
            CLASS_PREFIX + "item/PrimoShard";
    private static final String SEAL_TYPE_OWNER =
            CLASS_PREFIX + "magic/seal/SealType";
    private static final String SHARED_DEFERRED_REGISTER_OWNER =
            CLASS_PREFIX + "registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String LEGACY_ITEMS =
            CLASS_PREFIX + "registry/item/EItems.class";
    private static final String LEGACY_ITEMS_OWNER =
            CLASS_PREFIX + "registry/item/EItems";
    private static final String COMMON_BOOTSTRAP =
            CLASS_PREFIX + "bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            CLASS_PREFIX + "Etherology.class";
    private static final String ENGLISH_LANGUAGE =
            "assets/etherology/lang/en_us.json";
    private static final String RUSSIAN_LANGUAGE =
            "assets/etherology/lang/ru_ru.json";

    private static final Map<String, PrimoShardResource> PRIMO_SHARDS =
            primoShards();

    @Test
    void everyArtifactContainsOneSharedCatalogAndOneCanonicalSubtype()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                assertEquals(
                        1,
                        entries.stream().filter(SHARED_ITEMS::equals).count(),
                        artifact.description()
                );
                assertEquals(
                        1,
                        entries.stream().filter(PRIMO_SHARD::equals).count(),
                        artifact.description()
                );
                assertSharedCatalog(
                        artifact.description(),
                        classReader(jar, SHARED_ITEMS)
                );
                assertLoaderNeutral(
                        artifact.description() + ":shared registry",
                        classReader(jar, SHARED_ITEMS)
                );
                assertLoaderNeutral(
                        artifact.description() + ":PrimoShard",
                        classReader(jar, PRIMO_SHARD)
                );
                assertOnlySharedCatalogConstructsPrimoShards(
                        artifact,
                        jar,
                        entries
                );

                if (artifact.fabricApplication()) {
                    assertEquals(
                            1,
                            entries.stream().filter(LEGACY_ITEMS::equals).count(),
                            artifact.description()
                    );
                    assertExactFabricAliases(
                            artifact.description(),
                            classReader(jar, LEGACY_ITEMS)
                    );
                } else {
                    assertFalse(entries.contains(LEGACY_ITEMS), artifact.description());
                }
            }
        }
    }

    @Test
    void eachLoaderAttachesTheCatalogOnceAtTheExactCompatibilityBoundary()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                String initializer = artifact.fabricApplication()
                        ? FABRIC_INITIALIZER
                        : COMMON_BOOTSTRAP;
                List<String> calls = methodInvocations(
                        classReader(jar, initializer),
                        "initialize"
                );
                String lensRegistration = CLASS_PREFIX
                        + "registry/item/SharedLensItems#register()V";
                String primoShardRegistration = SHARED_ITEMS_OWNER + "#register()V";
                String alchemyRegistration = CLASS_PREFIX
                        + "registry/misc/SharedAlchemyRecipes#register()V";
                int primoShardIndex = calls.indexOf(primoShardRegistration);

                assertTrue(primoShardIndex > 0, artifact.description());
                assertEquals(
                        lensRegistration,
                        calls.get(primoShardIndex - 1),
                        artifact.description()
                );
                assertEquals(
                        alchemyRegistration,
                        calls.get(primoShardIndex + 1),
                        artifact.description()
                );
                assertEquals(
                        1,
                        calls.stream().filter(primoShardRegistration::equals).count(),
                        artifact.description()
                );
            }
        }
    }

    @Test
    void canonicalSourceMovedToCommonWithoutASecondFqnOwner()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.primoShards.repositoryRoot");
        Path commonSource = repositoryRoot.resolve(
                "common/src/main/java/ru/feytox/etherology/item/PrimoShard.java"
        );
        Path legacySource = repositoryRoot.resolve(
                "src/main/java/ru/feytox/etherology/item/PrimoShard.java"
        );
        requireRegularFile(commonSource);
        assertFalse(
                Files.exists(legacySource, LinkOption.NOFOLLOW_LINKS),
                legacySource.toString()
        );
    }

    @Test
    void bothLoaderApplicationsPackageExactModelsTexturesNamesAndLore()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.primoShards.repositoryRoot");
        Path modelRoot = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/models/item"
        );
        Path textureRoot = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/textures/item"
        );
        Path englishLanguage = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/lang/en_us.json"
        );
        Path russianLanguage = repositoryRoot.resolve(
                "src/main/generated/assets/etherology/lang/ru_ru.json"
        );
        requireRegularFile(englishLanguage);
        requireRegularFile(russianLanguage);
        assertLanguageFile(englishLanguage, true);
        assertLanguageFile(russianLanguage, false);
        assertEquals(
                PRIMO_SHARDS.keySet(),
                matchingPrimoShardFileIds(modelRoot, ".json"),
                modelRoot.toString()
        );
        assertEquals(
                PRIMO_SHARDS.keySet(),
                matchingPrimoShardFileIds(textureRoot, ".png"),
                textureRoot.toString()
        );

        for (PrimoShardResource shard : PRIMO_SHARDS.values()) {
            Path model = modelRoot.resolve(shard.id() + ".json");
            Path texture = textureRoot.resolve(shard.id() + ".png");
            requireRegularFile(model);
            requireRegularFile(texture);
            assertEquals(shard.modelSha256(), sha256(Files.readAllBytes(model)));
            assertEquals(shard.textureSha256(), sha256(Files.readAllBytes(texture)));
            assertExactModel(model, shard.id());
        }

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                for (PrimoShardResource shard : PRIMO_SHARDS.values()) {
                    assertResource(
                            artifact,
                            jar,
                            "assets/etherology/models/item/" + shard.id() + ".json",
                            modelRoot.resolve(shard.id() + ".json")
                    );
                    assertResource(
                            artifact,
                            jar,
                            "assets/etherology/textures/item/" + shard.id() + ".png",
                            textureRoot.resolve(shard.id() + ".png")
                    );
                }
                assertPackagedLanguage(artifact, jar, ENGLISH_LANGUAGE, true);
                assertPackagedLanguage(artifact, jar, RUSSIAN_LANGUAGE, false);
            }
        }
    }

    @Test
    void everyArtifactKeepsTheExactCommonEtherAndAspectValues()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.primoShards.repositoryRoot");
        Path etherSources = repositoryRoot.resolve(
                "common/src/main/resources/data/etherology/ether_sources/default.json"
        );
        Path aspects = repositoryRoot.resolve(
                "common/src/main/resources/data/etherology/etherology/aspects/"
                        + "etherology.json"
        );
        requireRegularFile(etherSources);
        requireRegularFile(aspects);

        JsonObject etherSourceValues = parseObject(Files.readString(etherSources));
        JsonObject aspectValues = parseObject(Files.readString(aspects));
        for (PrimoShardResource shard : PRIMO_SHARDS.values()) {
            String itemId = "etherology:" + shard.id();
            assertEquals(4, etherSourceValues.get(itemId).getAsInt(), itemId);
            JsonObject itemAspects = aspectValues.getAsJsonObject(itemId);
            assertEquals(
                    Set.of(shard.sealType().toLowerCase(Locale.ROOT)),
                    itemAspects.keySet(),
                    itemId
            );
            assertEquals(
                    4,
                    itemAspects.get(shard.sealType().toLowerCase(Locale.ROOT))
                            .getAsInt(),
                    itemId
            );
        }

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                assertAlwaysPackagedResource(
                        artifact,
                        jar,
                        "data/etherology/ether_sources/default.json",
                        etherSources
                );
                assertAlwaysPackagedResource(
                        artifact,
                        jar,
                        "data/etherology/etherology/aspects/etherology.json",
                        aspects
                );
            }
        }
    }

    private static void assertSharedCatalog(
        String description,
        ClassReader reader
    ) {
        List<String> fields = new ArrayList<>();
        Map<String, RegistrationInfo> registrations = new LinkedHashMap<>();
        List<String> unsafeCalls = new ArrayList<>();
        Map<String, FactoryInfo> factories = new LinkedHashMap<>();
        int[] deferredRegistrations = {0};
        int[] attachments = {0};

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.add(name);
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
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;
                    private String pendingFactory;
                    private String sealType;
                    private int constructions;
                    private int constructorCalls;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (name.equals("<clinit>")
                                && value instanceof String id
                                && PRIMO_SHARDS.containsKey(id)) {
                            pendingId = id;
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW && type.equals(PRIMO_SHARD_OWNER)) {
                            constructions++;
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String invokedName,
                            String invokedDescriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        if (!name.equals("<clinit>")) return;
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle
                                    && handle.getOwner().equals(SHARED_ITEMS_OWNER)
                                    && handle.getName().startsWith("lambda$")) {
                                pendingFactory = methodKey(handle);
                            }
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        if (name.equals("<clinit>")
                                && opcode == Opcodes.PUTSTATIC
                                && owner.equals(SHARED_ITEMS_OWNER)
                                && pendingId != null) {
                            registrations.put(
                                    fieldName,
                                    new RegistrationInfo(
                                            pendingId,
                                            pendingFactory
                                    )
                            );
                            pendingId = null;
                            pendingFactory = null;
                        }
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SEAL_TYPE_OWNER)
                                && PRIMO_SHARDS.values().stream()
                                .anyMatch(shard -> shard.sealType().equals(fieldName))) {
                            sealType = fieldName;
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
                        if (owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            unsafeCalls.add(owner + "#" + name);
                        }
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && name.equals("register")) {
                            deferredRegistrations[0]++;
                        }
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && name.equals("attach")) {
                            attachments[0]++;
                        }
                        if (isDirectRegistryOwner(owner)) {
                            unsafeCalls.add(owner + "#" + name);
                        }
                        if (owner.equals(PRIMO_SHARD_OWNER)
                                && name.equals("<init>")) {
                            constructorCalls++;
                        }
                    }

                    @Override
                    public void visitEnd() {
                        if (name.startsWith("lambda$")
                                && (sealType != null
                                || constructions != 0
                                || constructorCalls != 0)) {
                            factories.put(
                                    name + descriptor,
                                    new FactoryInfo(
                                            sealType,
                                            constructions,
                                            constructorCalls
                                    )
                            );
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        List<String> expectedFields = new ArrayList<>();
        expectedFields.add("ITEMS");
        expectedFields.addAll(PRIMO_SHARDS.values().stream()
                .map(PrimoShardResource::fieldName)
                .toList());
        assertEquals(expectedFields, fields, description);
        assertEquals(
                PRIMO_SHARDS.values().stream()
                        .map(PrimoShardResource::fieldName)
                        .toList(),
                new ArrayList<>(registrations.keySet()),
                description
        );
        assertEquals(List.of(), unsafeCalls, description);
        assertEquals(4, deferredRegistrations[0], description);
        assertEquals(1, attachments[0], description);

        Set<String> usedFactories = new LinkedHashSet<>();
        for (PrimoShardResource shard : PRIMO_SHARDS.values()) {
            RegistrationInfo registration = registrations.get(shard.fieldName());
            assertNotNull(registration, description + ":" + shard.fieldName());
            assertEquals(shard.id(), registration.id(), description);
            assertNotNull(registration.factoryMethod(), description);
            FactoryInfo factory = factories.get(registration.factoryMethod());
            assertNotNull(factory, description + ":" + shard.fieldName());
            assertEquals(shard.sealType(), factory.sealType(), description);
            assertEquals(1, factory.constructions(), description);
            assertEquals(1, factory.constructorCalls(), description);
            usedFactories.add(registration.factoryMethod());
        }
        assertEquals(factories.keySet(), usedFactories, description);
    }

    private static void assertOnlySharedCatalogConstructsPrimoShards(
            Artifact artifact,
            JarFile jar,
            List<String> entries
    ) throws IOException {
        Set<String> constructionOwners = new LinkedHashSet<>();
        for (String entry : entries) {
            if (!entry.startsWith(CLASS_PREFIX) || !entry.endsWith(".class")) {
                continue;
            }
            boolean[] constructs = {false};
            classReader(jar, entry).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW
                                    && type.equals(PRIMO_SHARD_OWNER)) {
                                constructs[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (constructs[0]) {
                constructionOwners.add(
                        entry.substring(0, entry.length() - ".class".length())
                );
            }
        }
        assertEquals(
                Set.of(SHARED_ITEMS_OWNER),
                constructionOwners,
                artifact.description()
        );
    }

    private static void assertExactFabricAliases(
            String description,
            ClassReader reader
    ) {
        List<String> fields = new ArrayList<>();
        List<String> events = new ArrayList<>();
        Set<String> duplicateIds = new LinkedHashSet<>();
        int[] constructions = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (PRIMO_SHARDS.values().stream()
                        .anyMatch(shard -> shard.fieldName().equals(name))) {
                    assertEquals(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            access,
                            description + ":" + name
                    );
                    fields.add(name);
                }
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
                if (!name.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingAlias;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (PRIMO_SHARDS.containsKey(value)) {
                            duplicateIds.add((String) value);
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW && type.equals(PRIMO_SHARD_OWNER)) {
                            constructions[0]++;
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_ITEMS_OWNER)
                                && PRIMO_SHARDS.values().stream().anyMatch(
                                shard -> shard.fieldName().equals(fieldName)
                        )) {
                            pendingAlias = fieldName;
                            events.add("SharedPrimoShardItems#" + fieldName);
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(LEGACY_ITEMS_OWNER)
                                && fieldName.equals(pendingAlias)) {
                            events.add("EItems#" + fieldName);
                            pendingAlias = null;
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
                        if (pendingAlias != null
                                && owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            events.add("RegistrySupplier#get");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                PRIMO_SHARDS.values().stream()
                        .map(PrimoShardResource::fieldName)
                        .toList(),
                fields,
                description
        );
        List<String> expectedEvents = new ArrayList<>();
        PRIMO_SHARDS.values().forEach(shard -> {
            expectedEvents.add("SharedPrimoShardItems#" + shard.fieldName());
            expectedEvents.add("RegistrySupplier#get");
            expectedEvents.add("EItems#" + shard.fieldName());
        });
        assertEquals(expectedEvents, events, description);
        assertEquals(Set.of(), duplicateIds, description);
        assertEquals(0, constructions[0], description);
    }

    private static void assertLoaderNeutral(
            String description,
            ClassReader reader
    ) {
        Set<String> forbidden = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
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

            private void check(String reference) {
                if (reference != null && (reference.contains("net/fabricmc/")
                        || reference.contains("net/minecraftforge/")
                        || reference.contains(CLASS_PREFIX + "client/"))) {
                    forbidden.add(reference);
                }
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(Set.of(), forbidden, description);
    }

    private static void assertExactModel(Path model, String id)
            throws IOException {
        JsonObject json = parseObject(Files.readString(model));
        assertEquals(Set.of("parent", "textures"), json.keySet(), model.toString());
        assertEquals("item/generated", json.get("parent").getAsString());
        JsonObject textures = json.getAsJsonObject("textures");
        assertEquals(Set.of("layer0"), textures.keySet(), model.toString());
        assertEquals(
                "etherology:item/" + id,
                textures.get("layer0").getAsString(),
                model.toString()
        );
    }

    private static void assertLanguageFile(Path languageFile, boolean english)
            throws IOException {
        JsonObject language = parseObject(Files.readString(languageFile));
        assertLanguageEntries(language, english, languageFile.toString());
    }

    private static void assertPackagedLanguage(
            Artifact artifact,
            JarFile jar,
            String entryName,
            boolean english
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        if (!artifact.includesAssets()) {
            assertEquals(
                    0,
                    jar.stream().filter(candidate -> candidate.getName()
                            .equals(entryName)).count(),
                    artifact.description() + ":" + entryName
            );
            assertEquals(null, entry, artifact.description() + ":" + entryName);
            return;
        }
        assertEquals(
                1,
                jar.stream().filter(candidate -> candidate.getName()
                        .equals(entryName)).count(),
                artifact.description() + ":" + entryName
        );
        assertNotNull(entry, artifact.description() + ":" + entryName);
        JsonObject language = parseObject(new String(
                jar.getInputStream(entry).readAllBytes(),
                StandardCharsets.UTF_8
        ));
        assertLanguageEntries(language, english, artifact.description());
    }

    private static void assertLanguageEntries(
            JsonObject language,
            boolean english,
            String description
    ) {
        Set<String> expectedKeys = PRIMO_SHARDS.keySet().stream()
                .map(id -> "item.etherology." + id)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new
                ));
        Set<String> actualKeys = language.keySet().stream()
                .filter(key -> key.startsWith("item.etherology.primoshard_"))
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new
                ));
        assertEquals(expectedKeys, actualKeys, description);
        for (PrimoShardResource shard : PRIMO_SHARDS.values()) {
            assertEquals(
                    english ? "Primoshard" : "Первичный осколок",
                    language.get("item.etherology." + shard.id()).getAsString(),
                    description + ":" + shard.id()
            );
        }
        assertEquals(
                "%s",
                language.get("lore.etherology.primoshard").getAsString(),
                description
        );
    }

    private static void assertResource(
            Artifact artifact,
            JarFile jar,
            String entryName,
            Path canonicalFile
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        if (!artifact.includesAssets()) {
            assertEquals(
                    0,
                    jar.stream().filter(candidate -> candidate.getName()
                            .equals(entryName)).count(),
                    artifact.description() + ":" + entryName
            );
            assertEquals(null, entry, artifact.description() + ":" + entryName);
            return;
        }
        assertEquals(
                1,
                jar.stream().filter(candidate -> candidate.getName()
                        .equals(entryName)).count(),
                artifact.description() + ":" + entryName
        );
        assertNotNull(entry, artifact.description() + ":" + entryName);
        assertArrayEquals(
                Files.readAllBytes(canonicalFile),
                jar.getInputStream(entry).readAllBytes(),
                artifact.description() + ":" + entryName
        );
    }

    private static void assertAlwaysPackagedResource(
            Artifact artifact,
            JarFile jar,
            String entryName,
            Path canonicalFile
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        assertEquals(
                1,
                jar.stream().filter(candidate -> candidate.getName()
                        .equals(entryName)).count(),
                artifact.description() + ":" + entryName
        );
        assertNotNull(entry, artifact.description() + ":" + entryName);
        assertArrayEquals(
                Files.readAllBytes(canonicalFile),
                jar.getInputStream(entry).readAllBytes(),
                artifact.description() + ":" + entryName
        );
    }

    private static List<String> methodInvocations(
            ClassReader reader,
            String methodName
    ) {
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
                        invocations.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static Set<String> matchingPrimoShardFileIds(
            Path directory,
            String suffix
    ) throws IOException {
        try (var files = Files.list(directory)) {
            return files
                    .filter(path -> Files.isRegularFile(
                            path,
                            LinkOption.NOFOLLOW_LINKS
                    ))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("primoshard_")
                            && name.endsWith(suffix))
                    .map(name -> name.substring(0, name.length() - suffix.length()))
                    .collect(java.util.stream.Collectors.toCollection(
                            LinkedHashSet::new
                    ));
        }
    }

    private static boolean isDirectRegistryOwner(String owner) {
        return owner.equals("net/minecraft/registry/Registry")
                || owner.equals("net/minecraft/class_2378")
                || owner.equals("net/minecraft/core/Registry")
                || owner.startsWith("net/minecraftforge/registries/");
    }

    private static String methodKey(Handle handle) {
        return handle.getName() + handle.getDesc();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        assertTrue(element.isJsonObject());
        return element.getAsJsonObject();
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
            boolean includesAssets,
            boolean fabricApplication
    ) throws IOException {
        Path path = requiredPath("etherology.primoShards." + suffix);
        requireRegularFile(path);
        return new Artifact(path, description, includesAssets, fabricApplication);
    }

    private static Map<String, PrimoShardResource> primoShards() {
        Map<String, PrimoShardResource> shards = new LinkedHashMap<>();
        add(
                shards,
                "PRIMOSHARD_KETA",
                "primoshard_keta",
                "KETA",
                "de5533fa3f763c692c83ef6aa17471ea7cbb3bf4e21bff31003aa6e010ab82e2",
                "e504e33bc2f46174fd289d957873eb4a02b28e16270593c5f95ee5092feeaa08"
        );
        add(
                shards,
                "PRIMOSHARD_RELLA",
                "primoshard_rella",
                "RELLA",
                "c3822c874f240012079d1dca5520dad8872e8f03469612d426fd6bcd0fce21e1",
                "3c8dbf3aba832811f4f9d7d76034b37797dd6acbe86da8da7dc46498bebdfae6"
        );
        add(
                shards,
                "PRIMOSHARD_CLOS",
                "primoshard_clos",
                "CLOS",
                "256300bf5e386b42df4cca2a31dbbf3aabe7494ca28dde679c16c441273e3aec",
                "0f91b819f4996648741a79c14ce54cb28ef6db728c271121d839e28ee0fd88d9"
        );
        add(
                shards,
                "PRIMOSHARD_VIA",
                "primoshard_via",
                "VIA",
                "3dbf049e0f1cdd6592b119b9838440de55dcda5350e2c6a42506de5a66b6a96d",
                "332ee08bd51f1380e747acaec4566b771f84cd6f42b95632c0dc1e145bdfd24b"
        );
        return Collections.unmodifiableMap(shards);
    }

    private static void add(
            Map<String, PrimoShardResource> shards,
            String fieldName,
            String id,
            String sealType,
            String modelSha256,
            String textureSha256
    ) {
        shards.put(
                id,
                new PrimoShardResource(
                        fieldName,
                        id,
                        sealType,
                        modelSha256,
                        textureSha256
                )
        );
    }

    private record FactoryInfo(
            String sealType,
            int constructions,
            int constructorCalls
    ) {
    }

    private record RegistrationInfo(String id, String factoryMethod) {
    }

    private record PrimoShardResource(
            String fieldName,
            String id,
            String sealType,
            String modelSha256,
            String textureSha256
    ) {
    }

    private record Artifact(
            Path path,
            String description,
            boolean includesAssets,
            boolean fabricApplication
    ) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
