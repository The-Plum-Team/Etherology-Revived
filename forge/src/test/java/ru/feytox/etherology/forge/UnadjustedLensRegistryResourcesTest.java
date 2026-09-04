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
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UnadjustedLensRegistryResourcesTest {

    private static final String ITEM_ID = "unadjusted_lens";
    private static final String ITEM_FIELD = "UNADJUSTED_LENS";
    private static final String CLASS_PREFIX = "ru/feytox/etherology/";
    private static final String SHARED_LENS_ITEMS =
            CLASS_PREFIX + "registry/item/SharedLensItems.class";
    private static final String SHARED_LENS_ITEMS_OWNER =
            CLASS_PREFIX + "registry/item/SharedLensItems";
    private static final String SHARED_DEFERRED_REGISTER_OWNER =
            CLASS_PREFIX + "registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String UNADJUSTED_LENS =
            CLASS_PREFIX + "item/UnadjustedLens.class";
    private static final String UNADJUSTED_LENS_OWNER =
            CLASS_PREFIX + "item/UnadjustedLens";
    private static final String LEGACY_ITEMS =
            CLASS_PREFIX + "registry/item/EItems.class";
    private static final String LEGACY_ITEMS_OWNER =
            CLASS_PREFIX + "registry/item/EItems";
    private static final String COMMON_BOOTSTRAP =
            CLASS_PREFIX + "bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            CLASS_PREFIX + "Etherology.class";
    private static final String FABRIC_ENTRYPOINT =
            CLASS_PREFIX + "EtherologyFabric.class";
    private static final String FORGE_ENTRYPOINT =
            CLASS_PREFIX + "forge/EtherologyForge.class";
    private static final String FABRIC_ADAPTER =
            CLASS_PREFIX + "item/FabricLensRuntimeBackend.class";

    private static final String BASE_MODEL_ENTRY =
            "assets/etherology/models/item/unadjusted_lens.json";
    private static final String CRACKED_MODEL_ENTRY =
            "assets/etherology/models/item/unadjusted_cracked_lens.json";
    private static final String BASE_TEXTURE_ENTRY =
            "assets/etherology/textures/item/unadjusted_lens.png";
    private static final String CRACKED_TEXTURE_ENTRY =
            "assets/etherology/textures/item/unadjusted_cracked_lens.png";
    private static final String ENGLISH_LANGUAGE_ENTRY =
            "assets/etherology/lang/en_us.json";
    private static final String RUSSIAN_LANGUAGE_ENTRY =
            "assets/etherology/lang/ru_ru.json";
    private static final String RECIPE_ENTRY =
            "data/etherology/recipes/unadjusted_lens.json";

    private static final String BASE_MODEL_SHA256 =
            "41c6ff7a496142db4e2a1f1f9257b37b8845d9e51cbe07df6c63ab628602cc3d";
    private static final String CRACKED_MODEL_SHA256 =
            "c7ba880cea34340994e412b540b9cc3212ee71d45a4b0247b9ed757fa218a131";
    private static final String BASE_TEXTURE_SHA256 =
            "4ed13c1009e0483661322e5e28f3c739b3ec0adc50bc3d1afe1d4b0829497428";
    private static final String CRACKED_TEXTURE_SHA256 =
            "888f4bc59a71701650d60d1b31132efd01266366cecde0e85e29b4ea3a528c4b";
    private static final String RECIPE_SHA256 =
            "3064a40722ced9c7ede45a8bc5654528ed79ce124e009cc19316d4ced0f57795";

    @Test
    void everyArtifactContainsOneSharedOwnerAndOneCanonicalSubtype()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                assertEquals(
                        1,
                        entries.stream().filter(SHARED_LENS_ITEMS::equals).count(),
                        artifact.description()
                );
                assertEquals(
                        1,
                        entries.stream().filter(UNADJUSTED_LENS::equals).count(),
                        artifact.description()
                );
                assertSharedOwnerShape(
                        artifact.description(),
                        classReader(jar, SHARED_LENS_ITEMS)
                );
                assertOnlyExpectedRegistrationAndConstructionOwner(
                        artifact,
                        jar,
                        entries
                );

                if (artifact.fabricApplication()) {
                    assertEquals(1, entries.stream().filter(LEGACY_ITEMS::equals).count());
                    assertExactFabricAlias(
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
    void canonicalCommonOwnerHasTheExactTypedFieldAndFactory()
            throws IOException {
        Artifact common = artifact("commonJar", "common JAR", false, false, false);
        try (JarFile jar = common.open()) {
            List<FieldInfo> fields = new ArrayList<>();
            List<String> factoryEvents = new ArrayList<>();
            classReader(jar, SHARED_LENS_ITEMS).accept(
                    new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public FieldVisitor visitField(
                                int access,
                                String name,
                                String descriptor,
                                String signature,
                                Object value
                        ) {
                            fields.add(new FieldInfo(
                                    access,
                                    name,
                                    descriptor,
                                    signature
                            ));
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
                                @Override
                                public void visitTypeInsn(int opcode, String type) {
                                    if (opcode == Opcodes.NEW
                                            && type.equals(UNADJUSTED_LENS_OWNER)) {
                                        factoryEvents.add("NEW:UnadjustedLens");
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
                                    if (owner.equals(UNADJUSTED_LENS_OWNER)
                                            && name.equals("<init>")) {
                                        factoryEvents.add("UnadjustedLens#<init>"
                                                + descriptor);
                                    }
                                }
                            };
                        }
                    },
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
            );

            assertEquals(
                    List.of(
                            new FieldInfo(
                                    Opcodes.ACC_PRIVATE
                                            | Opcodes.ACC_STATIC
                                            | Opcodes.ACC_FINAL,
                                    "ITEMS",
                                    "Lru/feytox/etherology/registry/"
                                            + "SharedDeferredRegister;",
                                    "Lru/feytox/etherology/registry/"
                                            + "SharedDeferredRegister"
                                            + "<Lnet/minecraft/item/Item;>;"
                            ),
                            new FieldInfo(
                                    Opcodes.ACC_PUBLIC
                                            | Opcodes.ACC_STATIC
                                            | Opcodes.ACC_FINAL,
                                    ITEM_FIELD,
                                    "Ldev/architectury/registry/registries/"
                                            + "RegistrySupplier;",
                                    "Ldev/architectury/registry/registries/"
                                            + "RegistrySupplier"
                                            + "<Lru/feytox/etherology/item/"
                                            + "UnadjustedLens;>;"
                            )
                    ),
                    fields
            );
            assertEquals(
                    List.of(
                            "NEW:UnadjustedLens",
                            "UnadjustedLens#<init>()V"
                    ),
                    factoryEvents
            );
        }
    }

    @Test
    void eachLoaderAttachesTheOwnerOnceAtTheExactCompatibilityBoundary()
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
                String toolRegistration = CLASS_PREFIX
                        + "registry/item/SharedToolItems#register()V";
                String lensRegistration = SHARED_LENS_ITEMS_OWNER + "#register()V";
                String successor = artifact.fabricApplication()
                        ? LEGACY_ITEMS_OWNER + "#registerItems()V"
                        : CLASS_PREFIX
                                + "registry/block/SharedBlockEntities#register()V";
                int lensIndex = calls.indexOf(lensRegistration);

                assertTrue(lensIndex > 0, artifact.description());
                assertEquals(toolRegistration, calls.get(lensIndex - 1),
                        artifact.description());
                assertEquals(successor, calls.get(lensIndex + 1),
                        artifact.description());
                assertEquals(
                        1,
                        calls.stream().filter(lensRegistration::equals).count(),
                        artifact.description()
                );

                if (artifact.fabricApplication()) {
                    List<String> entrypointCalls = methodInvocations(
                            classReader(jar, FABRIC_ENTRYPOINT),
                            "onInitialize"
                    );
                    assertEquals(
                            1,
                            entrypointCalls.stream()
                                    .filter((CLASS_PREFIX
                                            + "Etherology#initialize()V")::equals)
                                    .count(),
                            artifact.description()
                    );
                    assertEquals(
                            0,
                            entrypointCalls.stream()
                                    .filter(lensRegistration::equals)
                                    .count(),
                            artifact.description()
                    );
                }

                if (artifact.forgeApplication()) {
                    List<String> forgeCalls = methodInvocations(
                            classReader(jar, FORGE_ENTRYPOINT),
                            "<init>"
                    );
                    assertEquals(
                            1,
                            forgeCalls.stream()
                                    .filter((CLASS_PREFIX
                                            + "bootstrap/EtherologyBootstrap#initialize"
                                            + "(Lru/feytox/etherology/bootstrap/"
                                            + "PlatformRegistrar;)V")::equals)
                                    .count(),
                            artifact.description()
                    );
                    assertEquals(
                            0,
                            forgeCalls.stream()
                                    .filter(call -> call.equals(CLASS_PREFIX
                                            + "item/LensRuntime#bind"
                                            + "(Lru/feytox/etherology/item/"
                                            + "LensRuntimeBackend;)V"))
                                    .count(),
                            artifact.description()
                    );
                    assertEquals(null, jar.getJarEntry(FABRIC_ADAPTER));
                }
            }
        }
    }

    @Test
    void bothLoadersPackageTheExactStaticModelsTexturesAndNames()
            throws IOException {
        Path root = requiredPath("etherology.unadjustedLens.repositoryRoot");
        Path baseModel = root.resolve(
                "src/client/resources/assets/etherology/models/item/"
                        + "unadjusted_lens.json"
        );
        Path crackedModel = root.resolve(
                "src/client/resources/assets/etherology/models/item/"
                        + "unadjusted_cracked_lens.json"
        );
        Path baseTexture = root.resolve(
                "src/client/resources/assets/etherology/textures/item/"
                        + "unadjusted_lens.png"
        );
        Path crackedTexture = root.resolve(
                "src/client/resources/assets/etherology/textures/item/"
                        + "unadjusted_cracked_lens.png"
        );
        requireRegularFile(baseModel);
        requireRegularFile(crackedModel);
        requireRegularFile(baseTexture);
        requireRegularFile(crackedTexture);
        assertHash(baseModel, BASE_MODEL_SHA256);
        assertHash(crackedModel, CRACKED_MODEL_SHA256);
        assertHash(baseTexture, BASE_TEXTURE_SHA256);
        assertHash(crackedTexture, CRACKED_TEXTURE_SHA256);
        assertExactModels(baseModel, crackedModel);

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                assertResource(artifact, jar, BASE_MODEL_ENTRY, baseModel);
                assertResource(artifact, jar, CRACKED_MODEL_ENTRY, crackedModel);
                assertResource(artifact, jar, BASE_TEXTURE_ENTRY, baseTexture);
                assertResource(artifact, jar, CRACKED_TEXTURE_ENTRY, crackedTexture);
                assertLanguage(
                        artifact,
                        jar,
                        ENGLISH_LANGUAGE_ENTRY,
                        "Unadjusted Lens"
                );
                assertLanguage(
                        artifact,
                        jar,
                        RUSSIAN_LANGUAGE_ENTRY,
                        "Ненастроенная линза"
                );
            }
        }
    }

    @Test
    void alchemyRecipeRemainsExactAndFabricOnlyUntilForgeRegistersItsType()
            throws IOException {
        Path root = requiredPath("etherology.unadjustedLens.repositoryRoot");
        Path recipe = root.resolve(
                "src/main/generated/data/etherology/recipes/unadjusted_lens.json"
        );
        requireRegularFile(recipe);
        assertHash(recipe, RECIPE_SHA256);
        assertExactRecipe(recipe);

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                JarEntry entry = jar.getJarEntry(RECIPE_ENTRY);
                if (artifact.fabricApplication()) {
                    assertNotNull(entry, artifact.description());
                    assertArrayEquals(
                            Files.readAllBytes(recipe),
                            jar.getInputStream(entry).readAllBytes(),
                            artifact.description()
                    );
                } else {
                    assertEquals(null, entry, artifact.description());
                }
            }
        }
    }

    private static void assertSharedOwnerShape(
            String description,
            ClassReader reader
    ) {
        int[] classAccess = {-1};
        int[] privateConstructors = {0};
        int[] registrations = {0};
        int[] attachments = {0};
        int[] supplierGets = {0};
        List<String> fields = new ArrayList<>();
        List<String> ids = new ArrayList<>();
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
                fields.add(name);
                checkForbidden(descriptor, forbidden);
                checkForbidden(signature, forbidden);
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
                if (name.equals("<init>")
                        && descriptor.equals("()V")
                        && (access & Opcodes.ACC_PRIVATE) != 0) {
                    privateConstructors[0]++;
                }
                checkForbidden(descriptor, forbidden);
                checkForbidden(signature, forbidden);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (name.equals("<clinit>") && value.equals(ITEM_ID)) {
                            ids.add(ITEM_ID);
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        checkForbidden(type, forbidden);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        checkForbidden(owner, forbidden);
                        checkForbidden(descriptor, forbidden);
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && name.equals("register")) {
                            registrations[0]++;
                        }
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && name.equals("attach")) {
                            attachments[0]++;
                        }
                        if (owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            supplierGets[0]++;
                        }
                        if (isDirectRegistryOwner(owner)) {
                            forbidden.add(owner + "#" + name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess[0] & Opcodes.ACC_PUBLIC) != 0, description);
        assertTrue((classAccess[0] & Opcodes.ACC_FINAL) != 0, description);
        assertEquals(List.of("ITEMS", ITEM_FIELD), fields, description);
        assertEquals(1, privateConstructors[0], description);
        assertEquals(List.of(ITEM_ID), ids, description);
        assertEquals(1, registrations[0], description);
        assertEquals(1, attachments[0], description);
        assertEquals(0, supplierGets[0], description);
        assertEquals(Set.of(), forbidden, description);
    }

    private static void assertOnlyExpectedRegistrationAndConstructionOwner(
            Artifact artifact,
            JarFile jar,
            List<String> entries
    ) throws IOException {
        Set<String> idOwners = new LinkedHashSet<>();
        Set<String> registrationOwners = new LinkedHashSet<>();
        Set<String> constructionOwners = new LinkedHashSet<>();
        for (String entry : entries) {
            if (!entry.startsWith(CLASS_PREFIX) || !entry.endsWith(".class")) {
                continue;
            }
            Ownership ownership = ownership(classReader(jar, entry));
            String owner = entry.substring(0, entry.length() - ".class".length());
            if (ownership.idPresent()) idOwners.add(owner);
            if (ownership.registration()) registrationOwners.add(owner);
            if (ownership.construction()) constructionOwners.add(owner);
        }

        assertEquals(Set.of(SHARED_LENS_ITEMS_OWNER), idOwners,
                artifact.description());
        assertEquals(Set.of(SHARED_LENS_ITEMS_OWNER), registrationOwners,
                artifact.description());
        assertEquals(Set.of(SHARED_LENS_ITEMS_OWNER), constructionOwners,
                artifact.description());
    }

    private static Ownership ownership(ClassReader reader) {
        boolean[] idPresent = {false};
        boolean[] registration = {false};
        boolean[] construction = {false};
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
                        if (value.equals(ITEM_ID)) idPresent[0] = true;
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW
                                && type.equals(UNADJUSTED_LENS_OWNER)) {
                            construction[0] = true;
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
                                && name.equals("register")
                                && idPresent[0]) {
                            registration[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new Ownership(idPresent[0], registration[0], construction[0]);
    }

    private static void assertExactFabricAlias(
            String description,
            ClassReader reader
    ) {
        int[] fields = {0};
        int[] ids = {0};
        int[] constructions = {0};
        List<String> events = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals(ITEM_FIELD)) {
                    fields[0]++;
                    assertEquals(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            access,
                            description
                    );
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
                if (!name.equals("<clinit>")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean recording;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value.equals(ITEM_ID)) ids[0]++;
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW
                                && type.equals(UNADJUSTED_LENS_OWNER)) {
                            constructions[0]++;
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
                                && owner.equals(SHARED_LENS_ITEMS_OWNER)
                                && name.equals(ITEM_FIELD)) {
                            recording = true;
                            events.add("SharedLensItems#UNADJUSTED_LENS");
                        }
                        if (recording
                                && opcode == Opcodes.PUTSTATIC
                                && owner.equals(LEGACY_ITEMS_OWNER)
                                && name.equals(ITEM_FIELD)) {
                            events.add("EItems#UNADJUSTED_LENS");
                            recording = false;
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
                        if (recording
                                && owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            events.add("RegistrySupplier#get");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(1, fields[0], description);
        assertEquals(0, ids[0], description);
        assertEquals(0, constructions[0], description);
        assertEquals(
                List.of(
                        "SharedLensItems#UNADJUSTED_LENS",
                        "RegistrySupplier#get",
                        "EItems#UNADJUSTED_LENS"
                ),
                events,
                description
        );
    }

    private static void assertExactModels(Path base, Path cracked)
            throws IOException {
        JsonObject baseJson = parseObject(Files.readString(base));
        assertEquals(Set.of("parent", "textures", "overrides"), baseJson.keySet());
        assertEquals("item/generated", baseJson.get("parent").getAsString());
        assertEquals(
                "etherology:item/unadjusted_lens",
                baseJson.getAsJsonObject("textures").get("layer0").getAsString()
        );
        JsonArray overrides = baseJson.getAsJsonArray("overrides");
        assertEquals(2, overrides.size());
        assertOverride(
                overrides.get(0).getAsJsonObject(),
                0.0,
                "etherology:item/unadjusted_lens"
        );
        assertOverride(
                overrides.get(1).getAsJsonObject(),
                1.0,
                "etherology:item/unadjusted_cracked_lens"
        );

        JsonObject crackedJson = parseObject(Files.readString(cracked));
        assertEquals(Set.of("parent", "textures"), crackedJson.keySet());
        assertEquals("item/generated", crackedJson.get("parent").getAsString());
        assertEquals(
                "etherology:item/unadjusted_cracked_lens",
                crackedJson.getAsJsonObject("textures").get("layer0").getAsString()
        );
    }

    private static void assertOverride(
            JsonObject override,
            double predicate,
            String model
    ) {
        assertEquals(Set.of("predicate", "model"), override.keySet());
        JsonObject predicates = override.getAsJsonObject("predicate");
        assertEquals(Set.of("lens_cracked"), predicates.keySet());
        assertEquals(predicate, predicates.get("lens_cracked").getAsDouble());
        assertEquals(model, override.get("model").getAsString());
    }

    private static void assertExactRecipe(Path recipe) throws IOException {
        JsonObject json = parseObject(Files.readString(recipe));
        assertEquals(
                Set.of("type", "inputAmount", "inputAspects", "inputItem", "result"),
                json.keySet()
        );
        assertEquals("etherology:alchemy_recipe", json.get("type").getAsString());
        assertEquals(1, json.get("inputAmount").getAsInt());
        JsonObject aspects = json.getAsJsonObject("inputAspects");
        assertEquals(Set.of("frado", "hendall", "vibra"), aspects.keySet());
        assertEquals(5, aspects.get("frado").getAsInt());
        assertEquals(5, aspects.get("hendall").getAsInt());
        assertEquals(3, aspects.get("vibra").getAsInt());
        assertEquals(
                "etherology:slitherite",
                json.getAsJsonObject("inputItem").get("item").getAsString()
        );
        JsonObject result = json.getAsJsonObject("result");
        assertEquals(Set.of("count", "id"), result.keySet());
        assertEquals(1, result.get("count").getAsInt());
        assertEquals("etherology:unadjusted_lens", result.get("id").getAsString());
    }

    private static void assertLanguage(
            Artifact artifact,
            JarFile jar,
            String entryName,
            String expectedName
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        if (!artifact.includesAssets()) {
            assertEquals(null, entry, artifact.description() + ":" + entryName);
            return;
        }
        assertNotNull(entry, artifact.description() + ":" + entryName);
        JsonObject language = parseObject(new String(
                jar.getInputStream(entry).readAllBytes(),
                StandardCharsets.UTF_8
        ));
        assertEquals(
                expectedName,
                language.get("item.etherology.unadjusted_lens").getAsString(),
                artifact.description()
        );
    }

    private static void assertResource(
            Artifact artifact,
            JarFile jar,
            String entryName,
            Path canonical
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        if (!artifact.includesAssets()) {
            assertEquals(null, entry, artifact.description() + ":" + entryName);
            return;
        }
        assertNotNull(entry, artifact.description() + ":" + entryName);
        assertArrayEquals(
                Files.readAllBytes(canonical),
                jar.getInputStream(entry).readAllBytes(),
                artifact.description() + ":" + entryName
        );
    }

    private static List<String> methodInvocations(
            ClassReader reader,
            String methodName
    ) {
        List<String> calls = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        calls.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }

    private static void checkForbidden(String reference, Set<String> forbidden) {
        if (reference != null && (reference.contains("net/fabricmc/")
                || reference.contains("net/minecraftforge/")
                || reference.contains(CLASS_PREFIX + "client/"))) {
            forbidden.add(reference);
        }
    }

    private static boolean isDirectRegistryOwner(String owner) {
        return owner.equals("net/minecraft/registry/Registry")
                || owner.equals("net/minecraft/class_2378")
                || owner.equals("net/minecraft/core/Registry")
                || owner.startsWith("net/minecraftforge/registries/");
    }

    private static void assertHash(Path path, String expected) throws IOException {
        assertEquals(expected, sha256(Files.readAllBytes(path)), path.toString());
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
        JsonElement parsed = JsonParser.parseString(json);
        assertTrue(parsed.isJsonObject());
        return parsed.getAsJsonObject();
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
                        false,
                        true
                ),
                artifact(
                        "fabricProductionJar",
                        "Fabric remapped production JAR",
                        true,
                        false,
                        true
                ),
                artifact(
                        "forgeShadowJar",
                        "Forge shadow JAR",
                        false,
                        true,
                        true
                )
        );
    }

    private static Artifact artifact(
            String suffix,
            String description,
            boolean fabricApplication,
            boolean forgeApplication,
            boolean includesAssets
    ) throws IOException {
        Path path = requiredPath("etherology.unadjustedLens." + suffix);
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), path.toString());
        assertFalse(Files.isSymbolicLink(path), path.toString());
        return new Artifact(
                path,
                description,
                fabricApplication,
                forgeApplication,
                includesAssets
        );
    }

    private record FieldInfo(
            int access,
            String name,
            String descriptor,
            String signature
    ) {
    }

    private record Ownership(
            boolean idPresent,
            boolean registration,
            boolean construction
    ) {
    }

    private record Artifact(
            Path path,
            String description,
            boolean fabricApplication,
            boolean forgeApplication,
            boolean includesAssets
    ) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
