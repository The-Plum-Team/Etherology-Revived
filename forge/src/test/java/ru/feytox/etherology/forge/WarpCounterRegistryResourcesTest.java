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

final class WarpCounterRegistryResourcesTest {

    private static final String ITEM_ID = "warp_counter";
    private static final String ITEM_FIELD = "WARP_COUNTER";
    private static final List<String> TOOL_FIELDS = List.of(
            ITEM_FIELD, "EBONY_AXE", "EBONY_PICKAXE", "EBONY_HOE",
            "EBONY_SHOVEL", "EBONY_SWORD"
    );
    private static final List<EbonyTool> EBONY_TOOLS = List.of(
            new EbonyTool("axe", "axes", "Ebony Axe", "Эбонитовый топор",
                    List.of("XX", "X#", " #"),
                    "27c5ade42ef0c8c051b6b07f4845ef6443b9289060a55d171e98b2279a27ca91"),
            new EbonyTool("pickaxe", "pickaxes", "Ebony Pickaxe", "Эбонитовая кирка",
                    List.of("XXX", " # ", " # "),
                    "0818da3d84a80ba7771aae7321e57b2be93cb5d8131b6d8e25c586efb744a50f"),
            new EbonyTool("hoe", "hoes", "Ebony Hoe", "Эбонитовая мотыга",
                    List.of("XX", " #", " #"),
                    "98b83bfd0cb3f63a545288f16271828e553cee0b0c1e3830a8c33b0c32f89ac2"),
            new EbonyTool("shovel", "shovels", "Ebony Shovel", "Эбонитовая лопата",
                    List.of("X", "#", "#"),
                    "4c3fafde584706d730d0a2e88153a62fc0ac4bb230b3d9ceacb0f0e035e8e2e2"),
            new EbonyTool("sword", "swords", "Ebony Sword", "Эбонитовый меч",
                    List.of("X", "X", "#"),
                    "0f65509873ed212fedf2947122ff50e41512227a9ddf7cde3a43f21d7dfbc0c8")
    );
    private static final String SHARED_TOOL_ITEMS =
            "ru/feytox/etherology/registry/item/SharedToolItems.class";
    private static final String SHARED_TOOL_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedToolItems";
    private static final String SHARED_SOUNDS_OWNER =
            "ru/feytox/etherology/registry/misc/SharedSounds";
    private static final String SHARED_DEFERRED_REGISTER_OWNER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String LEGACY_TOOL_ITEMS =
            "ru/feytox/etherology/registry/item/ToolItems.class";
    private static final String LEGACY_TOOL_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/ToolItems";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String COMMON_BOOTSTRAP_OWNER =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String FABRIC_INITIALIZER_OWNER =
            "ru/feytox/etherology/Etherology";
    private static final String FABRIC_ENTRYPOINT =
            "ru/feytox/etherology/EtherologyFabric.class";
    private static final String FORGE_ENTRYPOINT =
            "ru/feytox/etherology/forge/EtherologyForge.class";
    private static final String FABRIC_MODEL_PREDICATES =
            "ru/feytox/etherology/client/registry/ModelPredicates.class";
    private static final String FABRIC_WARP_COUNTER_CLIENT =
            "ru/feytox/etherology/client/item/WarpCounterClient.class";

    private static final String ROOT_MODEL_ENTRY =
            "assets/etherology/models/item/warp_counter.json";
    private static final String ENGLISH_LANGUAGE_ENTRY =
            "assets/etherology/lang/en_us.json";
    private static final String RUSSIAN_LANGUAGE_ENTRY =
            "assets/etherology/lang/ru_ru.json";
    private static final String RECIPE_ENTRY =
            "data/etherology/recipes/warp_counter.json";
    private static final String ADVANCEMENT_ENTRY =
            "data/etherology/advancements/recipes/tools/warp_counter.json";
    private static final String ROOT_MODEL_SHA256 =
            "a1fc8af9c7323151a9675e100d589ceee973c6d70c63dfa60f7e4b3816002463";
    private static final String RECIPE_SHA256 =
            "1b94aa3aa9de55d66183da26ca61c15a92b0f26d7db40e495bcc7a5cd3af0b31";
    private static final String ADVANCEMENT_SHA256 =
            "207acc4ab9f187cbdf51fc011a9ee6f576bb3bb4ffd88609fac3b2189e6c5277";
    private static final List<String> PREDICATE_VALUES = List.of(
            "0.000000",
            "0.066666",
            "0.133333",
            "0.200000",
            "0.266666",
            "0.333333",
            "0.400000",
            "0.466666",
            "0.533333",
            "0.600000",
            "0.666666",
            "0.733333",
            "0.800000",
            "0.866666",
            "0.933333"
    );
    private static final Map<Integer, String> CHILD_MODEL_SHA256 =
            childModelHashes();
    private static final Map<Integer, String> TEXTURE_SHA256 = textureHashes();

    @Test
    void everyArtifactHasOneExactLoaderNeutralRegistrationOwner()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                assertEquals(
                        1,
                        entries.stream().filter(SHARED_TOOL_ITEMS::equals).count(),
                        artifact.description()
                );
                assertSharedOwnerShapeAndOwnership(
                        artifact.description(),
                        classReader(jar, SHARED_TOOL_ITEMS)
                );
                assertOnlyExpectedRegistrationOwner(artifact, jar, entries);

                if (artifact.fabricApplication()) {
                    assertEquals(
                            1,
                            entries.stream().filter(LEGACY_TOOL_ITEMS::equals).count(),
                            artifact.description()
                    );
                    assertExactLegacyAlias(
                            artifact.description(),
                            classReader(jar, LEGACY_TOOL_ITEMS)
                    );
                } else {
                    assertFalse(entries.contains(LEGACY_TOOL_ITEMS), artifact.description());
                }
            }
        }
    }

    @Test
    void canonicalCommonBytecodeEncodesTheExactPlainSingleStackItem()
            throws IOException {
        Artifact common = artifact("commonJar", "common JAR", false, false, false);
        try (JarFile jar = common.open()) {
            assertExactCanonicalFactory(classReader(jar, SHARED_TOOL_ITEMS));
        }
    }

    @Test
    void eachLoaderAttachesTheSharedOwnerExactlyOnceBeforeAnyLegacyRead()
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
                String foodRegistration =
                        "ru/feytox/etherology/registry/item/SharedFoodItems#register()V";
                String toolRegistration = SHARED_TOOL_ITEMS_OWNER + "#register()V";
                assertInvocationOnce(
                        artifact.description(),
                        invocations,
                        foodRegistration
                );
                assertInvocationOnce(
                        artifact.description(),
                        invocations,
                        toolRegistration
                );
                assertTrue(
                        invocations.indexOf(foodRegistration)
                                < invocations.indexOf(toolRegistration),
                        artifact.description()
                );

                if (artifact.fabricApplication()) {
                    String legacyRegistration =
                            "ru/feytox/etherology/registry/item/EItems#registerItems()V";
                    assertInvocationOnce(
                            artifact.description(),
                            invocations,
                            legacyRegistration
                    );
                    assertTrue(
                            invocations.indexOf(toolRegistration)
                                    < invocations.indexOf(legacyRegistration),
                            artifact.description()
                    );
                    assertEntrypointDelegatesOnce(
                            artifact,
                            jar,
                            FABRIC_ENTRYPOINT,
                            "onInitialize",
                            FABRIC_INITIALIZER_OWNER + "#initialize()V",
                            toolRegistration
                    );
                } else {
                    String blockEntityRegistration =
                            "ru/feytox/etherology/registry/block/SharedBlockEntities"
                                    + "#register()V";
                    assertInvocationOnce(
                            artifact.description(),
                            invocations,
                            blockEntityRegistration
                    );
                    assertTrue(
                            invocations.indexOf(toolRegistration)
                                    < invocations.indexOf(blockEntityRegistration),
                            artifact.description()
                    );
                    if (artifact.forgeApplication()) {
                        assertEntrypointDelegatesOnce(
                                artifact,
                                jar,
                                FORGE_ENTRYPOINT,
                                "<init>",
                                COMMON_BOOTSTRAP_OWNER
                                        + "#initialize(Lru/feytox/etherology/bootstrap/"
                                        + "PlatformRegistrar;)V",
                                toolRegistration
                        );
                    }
                }
            }
        }
    }

    @Test
    void packagedFramesAreExactWithoutClaimingTheDeferredRuntimePredicate()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.warpCounter.repositoryRoot");
        Path rootModel = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/models/item/warp_counter.json"
        );
        Path childModelRoot = repositoryRoot.resolve(
                "src/main/generated/assets/etherology/models/item"
        );
        Path textureRoot = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/textures/item"
        );
        requireRegularFile(rootModel);
        assertEquals(ROOT_MODEL_SHA256, sha256(Files.readAllBytes(rootModel)));
        assertExactRootModel(rootModel);
        assertExactFrameInventory(childModelRoot, textureRoot);

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                assertResource(
                        artifact,
                        jar,
                        ROOT_MODEL_ENTRY,
                        rootModel,
                        ROOT_MODEL_SHA256
                );
                for (int frame = 1; frame <= 14; frame++) {
                    String fileName = "warp_counter_" + frame + ".json";
                    assertResource(
                            artifact,
                            jar,
                            "assets/etherology/models/item/" + fileName,
                            childModelRoot.resolve(fileName),
                            CHILD_MODEL_SHA256.get(frame)
                    );
                }
                for (int frame = 0; frame <= 14; frame++) {
                    String fileName = "warp_counter_" + frame + ".png";
                    assertResource(
                            artifact,
                            jar,
                            "assets/etherology/textures/item/" + fileName,
                            textureRoot.resolve(fileName),
                            TEXTURE_SHA256.get(frame)
                    );
                }

                if (artifact.forgeApplication()) {
                    assertEquals(null, jar.getJarEntry(FABRIC_MODEL_PREDICATES));
                    assertEquals(null, jar.getJarEntry(FABRIC_WARP_COUNTER_CLIENT));
                }
            }
        }
    }

    @Test
    void packagedRecipeAdvancementAndBothNamesAreExact() throws IOException {
        Path repositoryRoot = requiredPath("etherology.warpCounter.repositoryRoot");
        Path recipe = repositoryRoot.resolve(
                "src/main/generated/data/etherology/recipes/warp_counter.json"
        );
        Path advancement = repositoryRoot.resolve(
                "src/main/generated/data/etherology/advancements/recipes/tools/"
                        + "warp_counter.json"
        );
        Path englishLanguage = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/lang/en_us.json"
        );
        Path russianLanguage = repositoryRoot.resolve(
                "src/main/generated/assets/etherology/lang/ru_ru.json"
        );
        requireRegularFile(recipe);
        requireRegularFile(advancement);
        requireRegularFile(englishLanguage);
        requireRegularFile(russianLanguage);
        assertEquals(RECIPE_SHA256, sha256(Files.readAllBytes(recipe)));
        assertEquals(ADVANCEMENT_SHA256, sha256(Files.readAllBytes(advancement)));
        assertExactRecipe(recipe);
        assertExactAdvancement(advancement);
        assertLanguageName(englishLanguage, "Warp Counter");
        assertLanguageName(russianLanguage, "Варп счётчик");

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                assertResource(
                        artifact,
                        jar,
                        RECIPE_ENTRY,
                        recipe,
                        RECIPE_SHA256
                );
                assertResource(
                        artifact,
                        jar,
                        ADVANCEMENT_ENTRY,
                        advancement,
                        ADVANCEMENT_SHA256
                );
                assertPackagedLanguage(
                        artifact,
                        jar,
                        ENGLISH_LANGUAGE_ENTRY,
                        "Warp Counter"
                );
                assertPackagedLanguage(
                        artifact,
                        jar,
                        RUSSIAN_LANGUAGE_ENTRY,
                        "Варп счётчик"
                );
            }
        }
    }

    @Test
    void ebonyToolsHaveOneSharedMaterialAndDeclarationOwnerInEveryArtifact()
            throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        EBONY_TOOLS.forEach(tool -> ids.add(tool.id()));
        String materialEntry =
                "ru/feytox/etherology/registry/misc/EtherToolMaterials.class";
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                assertEquals(1, jar.stream()
                        .filter(entry -> entry.getName().equals(materialEntry)).count(),
                        artifact.description());
                Map<String, List<String>> ownersById = new LinkedHashMap<>();
                for (JarEntry entry : jar.stream()
                        .filter(value -> value.getName().endsWith(".class")).toList()) {
                    ClassReader reader = classReader(jar, entry.getName());
                    reader.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(
                                int access, String name, String descriptor,
                                String signature, String[] exceptions
                        ) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitLdcInsn(Object value) {
                                    if (value instanceof String id && ids.contains(id)) {
                                        ownersById.computeIfAbsent(id, ignored -> new ArrayList<>())
                                                .add(reader.getClassName() + "#" + name);
                                    }
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                assertEquals(ids, ownersById.keySet(), artifact.description());
                ownersById.forEach((id, owners) -> assertEquals(
                        List.of(SHARED_TOOL_ITEMS_OWNER + "#<clinit>"),
                        owners, artifact.description() + ":" + id
                ));
            }
        }
    }

    @Test
    void ebonyAssetsCraftingAndToolTagsAreCanonicalAcrossBothLoaders()
            throws IOException {
        Path root = requiredPath("etherology.warpCounter.repositoryRoot");
        for (EbonyTool tool : EBONY_TOOLS) {
            String modelEntry = "assets/etherology/models/item/" + tool.id() + ".json";
            String textureEntry = "assets/etherology/textures/item/" + tool.id() + ".png";
            String recipeEntry = "data/etherology/recipes/" + tool.id() + ".json";
            String advancementEntry =
                    "data/etherology/advancements/recipes/tools/" + tool.id() + ".json";
            String tagEntry = "data/minecraft/tags/items/" + tool.tag() + ".json";
            Map<String, Path> resources = Map.of(
                    modelEntry, root.resolve("src/main/generated/" + modelEntry),
                    textureEntry, root.resolve("src/client/resources/" + textureEntry),
                    recipeEntry, root.resolve("src/main/generated/" + recipeEntry),
                    advancementEntry, root.resolve("src/main/generated/" + advancementEntry),
                    tagEntry, root.resolve("src/main/generated/" + tagEntry)
            );
            JsonObject model = parseObject(Files.readString(resources.get(modelEntry)));
            assertEquals(Set.of("parent", "textures"), model.keySet(), tool.id());
            assertEquals("minecraft:item/handheld", model.get("parent").getAsString());
            assertEquals(Set.of("layer0"), model.getAsJsonObject("textures").keySet());
            assertEquals("etherology:item/" + tool.id(),
                    model.getAsJsonObject("textures").get("layer0").getAsString());
            assertEquals(tool.textureSha256(), sha256(Files.readAllBytes(resources.get(textureEntry))));
            assertEbonyRecipe(tool, parseObject(Files.readString(resources.get(recipeEntry))));
            assertEbonyAdvancement(tool, parseObject(Files.readString(resources.get(advancementEntry))));
            JsonObject tag = parseObject(Files.readString(resources.get(tagEntry)));
            assertFalse(tag.get("replace").getAsBoolean());
            List<String> requiredIds = new ArrayList<>();
            for (JsonElement value : tag.getAsJsonArray("values")) {
                if (value.isJsonPrimitive()) {
                    requiredIds.add(value.getAsString());
                } else {
                    assertFalse(value.getAsJsonObject().get("required").getAsBoolean());
                }
            }
            assertEquals(List.of("etherology:" + tool.id()), requiredIds);

            for (Artifact artifact : artifacts()) {
                try (JarFile jar = artifact.open()) {
                    for (Map.Entry<String, Path> resource : resources.entrySet()) {
                        assertResource(artifact, jar, resource.getKey(), resource.getValue(),
                                sha256(Files.readAllBytes(resource.getValue())));
                    }
                    if (artifact.includesResources()) {
                        assertEbonyName(jar, tool, ENGLISH_LANGUAGE_ENTRY, tool.english());
                        assertEbonyName(jar, tool, RUSSIAN_LANGUAGE_ENTRY, tool.russian());
                    }
                }
            }
        }
    }

    private static void assertEbonyRecipe(EbonyTool tool, JsonObject recipe) {
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("equipment", recipe.get("category").getAsString());
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals(Set.of("#", "X"), key.keySet());
        assertEquals("minecraft:stick", key.getAsJsonObject("#").get("item").getAsString());
        assertEquals("etherology:ebony_ingot", key.getAsJsonObject("X").get("item").getAsString());
        List<String> pattern = new ArrayList<>();
        recipe.getAsJsonArray("pattern").forEach(value -> pattern.add(value.getAsString()));
        assertEquals(tool.pattern(), pattern, tool.id());
        assertEquals(Set.of("item"), recipe.getAsJsonObject("result").keySet());
        assertEquals("etherology:" + tool.id(),
                recipe.getAsJsonObject("result").get("item").getAsString());
    }

    private static void assertEbonyAdvancement(EbonyTool tool, JsonObject advancement) {
        String recipeId = "etherology:" + tool.id();
        assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString());
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        assertEquals(Set.of("has_ebony_ingot", "has_the_recipe"), criteria.keySet());
        JsonObject unlock = criteria.getAsJsonObject("has_the_recipe");
        assertEquals("minecraft:recipe_unlocked", unlock.get("trigger").getAsString());
        assertEquals(recipeId, unlock.getAsJsonObject("conditions").get("recipe").getAsString());
        JsonObject inventory = criteria.getAsJsonObject("has_ebony_ingot");
        assertEquals("minecraft:inventory_changed", inventory.get("trigger").getAsString());
        JsonArray ingredients = inventory.getAsJsonObject("conditions").getAsJsonArray("items");
        assertEquals(1, ingredients.size());
        assertEquals("etherology:ebony_ingot", ingredients.get(0).getAsJsonObject()
                .getAsJsonArray("items").get(0).getAsString());
        JsonArray rewards = advancement.getAsJsonObject("rewards").getAsJsonArray("recipes");
        assertEquals(1, rewards.size());
        assertEquals(recipeId, rewards.get(0).getAsString());
    }

    private static void assertEbonyName(
            JarFile jar, EbonyTool tool, String entry, String expected
    ) throws IOException {
        try (var stream = jar.getInputStream(jar.getJarEntry(entry))) {
            JsonObject language = parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(expected, language.get("item.etherology." + tool.id()).getAsString());
        }
    }

    private record EbonyTool(
            String suffix, String tag, String english, String russian,
            List<String> pattern, String textureSha256
    ) {
        String id() {
            return "ebony_" + suffix;
        }
    }

    private static void assertSharedOwnerShapeAndOwnership(
            String description,
            ClassReader reader
    ) {
        int[] classAccess = {-1};
        int[] privateConstructors = {0};
        int[] deferredRegistrations = {0};
        int[] attachCalls = {0};
        int[] supplierGets = {0};
        Map<String, String> fields = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        Set<String> forbiddenReferences = new LinkedHashSet<>();

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
                checkForbiddenReference(superName, forbiddenReferences);
                checkForbiddenReference(signature, forbiddenReferences);
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.put(name, descriptor);
                checkForbiddenReference(descriptor, forbiddenReferences);
                checkForbiddenReference(signature, forbiddenReferences);
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
                checkForbiddenReference(descriptor, forbiddenReferences);
                checkForbiddenReference(signature, forbiddenReferences);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        checkForbiddenReference(type, forbiddenReferences);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (name.equals("<clinit>") && value.equals(ITEM_ID)) {
                            ids.add(ITEM_ID);
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        checkForbiddenReference(owner, forbiddenReferences);
                        checkForbiddenReference(invokedDescriptor, forbiddenReferences);
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && invokedName.equals("register")) {
                            deferredRegistrations[0]++;
                        }
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && invokedName.equals("attach")) {
                            attachCalls[0]++;
                        }
                        if (owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && invokedName.equals("get")) {
                            supplierGets[0]++;
                        }
                        if (isDirectRegistryOwner(owner)) {
                            forbiddenReferences.add(owner + "#" + invokedName);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess[0] & Opcodes.ACC_PUBLIC) != 0, description);
        assertTrue((classAccess[0] & Opcodes.ACC_FINAL) != 0, description);
        List<String> expectedNames = new ArrayList<>(List.of("ITEMS"));
        expectedNames.addAll(TOOL_FIELDS);
        assertEquals(expectedNames, new ArrayList<>(fields.keySet()),
                description);
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                fields.get("ITEMS"),
                description
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier;",
                fields.get(ITEM_FIELD),
                description
        );
        assertEquals(1, privateConstructors[0], description);
        assertEquals(List.of(ITEM_ID), ids, description);
        assertEquals(6, deferredRegistrations[0], description);
        assertEquals(1, attachCalls[0], description);
        assertEquals(0, supplierGets[0], description);
        assertEquals(Set.of(), forbiddenReferences, description);
    }

    private static void assertExactCanonicalFactory(ClassReader reader) {
        List<FieldInfo> fields = new ArrayList<>();
        Map<String, List<String>> eventsByMethod = new LinkedHashMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.add(new FieldInfo(access, name, descriptor, signature));
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
                List<String> factoryEvents = new ArrayList<>();
                eventsByMethod.put(name + descriptor, factoryEvents);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW
                                && type.equals("net/minecraft/item/Item")) {
                            factoryEvents.add("NEW Item");
                        }
                        if (opcode == Opcodes.NEW
                                && type.equals("net/minecraft/item/Item$Settings")) {
                            factoryEvents.add("NEW Item$Settings");
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ICONST_1) {
                            factoryEvents.add("INT:1");
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals("net/minecraft/item/Item$Settings")) {
                            factoryEvents.add(
                                    "Item$Settings#" + invokedName + invokedDescriptor
                            );
                        }
                        if (owner.equals("net/minecraft/item/Item")
                                && invokedName.equals("<init>")) {
                            factoryEvents.add("Item#<init>" + invokedDescriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(expectedFields(), fields);
        List<List<String>> plainItemFactories = eventsByMethod.values().stream()
                .filter(events -> events.contains("NEW Item"))
                .toList();
        assertEquals(1, plainItemFactories.size());
        assertEquals(
                List.of(
                        "NEW Item",
                        "NEW Item$Settings",
                        "Item$Settings#<init>()V",
                        "INT:1",
                        "Item$Settings#maxCount(I)Lnet/minecraft/item/Item$Settings;",
                        "Item#<init>(Lnet/minecraft/item/Item$Settings;)V"
                ),
                plainItemFactories.get(0)
        );
    }

    private static List<FieldInfo> expectedFields() {
        List<FieldInfo> fields = new ArrayList<>();
        fields.add(
                new FieldInfo(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "ITEMS",
                        "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                        "Lru/feytox/etherology/registry/SharedDeferredRegister"
                                + "<Lnet/minecraft/item/Item;>;"
                )
        );
        for (String field : TOOL_FIELDS) {
            fields.add(new FieldInfo(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        field,
                        "Ldev/architectury/registry/registries/RegistrySupplier;",
                        "Ldev/architectury/registry/registries/RegistrySupplier"
                                + "<Lnet/minecraft/item/Item;>;"
                ));
        }
        return fields;
    }

    private static void assertExactLegacyAlias(
            String description,
            ClassReader reader
    ) {
        int[] fieldCount = {0};
        int[] idConstants = {0};
        int[] sharedReads = {0};
        int[] supplierGets = {0};
        int[] assignments = {0};
        int[] instructionIndex = {0};
        int[] sharedReadIndex = {-1};
        int[] supplierGetIndex = {-1};
        int[] assignmentIndex = {-1};

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
                    fieldCount[0]++;
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
                if (!name.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        instructionIndex[0]++;
                        if (value.equals(ITEM_ID)) {
                            idConstants[0]++;
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_TOOL_ITEMS_OWNER)
                                && name.equals(ITEM_FIELD)) {
                            sharedReads[0]++;
                            sharedReadIndex[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(LEGACY_TOOL_ITEMS_OWNER)
                                && name.equals(ITEM_FIELD)) {
                            assignments[0]++;
                            assignmentIndex[0] = instructionIndex[0];
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
                        instructionIndex[0]++;
                        if (owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            supplierGets[0]++;
                            supplierGetIndex[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(1, fieldCount[0], description);
        assertEquals(0, idConstants[0], description);
        assertEquals(1, sharedReads[0], description);
        assertEquals(1, supplierGets[0], description);
        assertEquals(1, assignments[0], description);
        assertTrue(sharedReadIndex[0] < supplierGetIndex[0], description);
        assertTrue(supplierGetIndex[0] < assignmentIndex[0], description);
    }

    private static void assertOnlyExpectedRegistrationOwner(
            Artifact artifact,
            JarFile jar,
            List<String> entries
    ) throws IOException {
        Set<String> deferredOwners = new LinkedHashSet<>();
        Set<String> directOwners = new LinkedHashSet<>();
        for (String entry : entries) {
            if (!entry.startsWith("ru/feytox/etherology/")
                    || !entry.endsWith(".class")) {
                continue;
            }
            RegistrationOwnership ownership = registrationOwnership(
                    classReader(jar, entry)
            );
            if (!ownership.idPresent()) {
                continue;
            }
            String owner = entry.substring(0, entry.length() - ".class".length());
            if (ownership.sharedDeferredRegistration()) {
                deferredOwners.add(owner);
            }
            if (ownership.directRegistryRegistration()) {
                directOwners.add(owner);
            }
        }
        assertEquals(
                Set.of(SHARED_TOOL_ITEMS_OWNER, SHARED_SOUNDS_OWNER),
                deferredOwners,
                artifact.description()
        );
        assertEquals(Set.of(), directOwners, artifact.description());
    }

    private static RegistrationOwnership registrationOwnership(ClassReader reader) {
        boolean[] idPresent = {false};
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
                        if (value.equals(ITEM_ID)) {
                            idPresent[0] = true;
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
                            sharedDeferredRegistration[0] = true;
                        }
                        if (isDirectRegistryOwner(owner)) {
                            directRegistryRegistration[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new RegistrationOwnership(
                idPresent[0],
                sharedDeferredRegistration[0],
                directRegistryRegistration[0]
        );
    }

    private static boolean isDirectRegistryOwner(String owner) {
        return owner.equals("net/minecraft/registry/Registry")
                || owner.equals("net/minecraft/class_2378")
                || owner.equals("net/minecraft/core/Registry")
                || owner.startsWith("net/minecraftforge/registries/");
    }

    private static void checkForbiddenReference(
            String reference,
            Set<String> forbiddenReferences
    ) {
        if (reference != null && (reference.contains("net/fabricmc/")
                || reference.contains("net/minecraftforge/")
                || reference.contains("ru/feytox/etherology/client/"))) {
            forbiddenReferences.add(reference);
        }
    }

    private static void assertExactFrameInventory(
            Path childModelRoot,
            Path textureRoot
    ) throws IOException {
        Set<String> expectedModels = new LinkedHashSet<>();
        Set<String> expectedTextures = new LinkedHashSet<>();
        for (int frame = 1; frame <= 14; frame++) {
            String fileName = "warp_counter_" + frame + ".json";
            expectedModels.add(fileName);
            Path model = childModelRoot.resolve(fileName);
            requireRegularFile(model);
            assertEquals(CHILD_MODEL_SHA256.get(frame), sha256(Files.readAllBytes(model)));
            assertExactChildModel(model, frame);
        }
        for (int frame = 0; frame <= 14; frame++) {
            String fileName = "warp_counter_" + frame + ".png";
            expectedTextures.add(fileName);
            Path texture = textureRoot.resolve(fileName);
            requireRegularFile(texture);
            assertEquals(TEXTURE_SHA256.get(frame), sha256(Files.readAllBytes(texture)));
        }
        assertEquals(expectedModels, matchingFileNames(childModelRoot, ".json"));
        assertEquals(expectedTextures, matchingFileNames(textureRoot, ".png"));
    }

    private static Set<String> matchingFileNames(Path directory, String suffix)
            throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("warp_counter_") && name.endsWith(suffix);
                    })
                    .map(path -> path.getFileName().toString())
                    .sorted((left, right) -> Integer.compare(
                            frameNumber(left),
                            frameNumber(right)
                    ))
                    .collect(
                            LinkedHashSet::new,
                            LinkedHashSet::add,
                            LinkedHashSet::addAll
                    );
        }
    }

    private static int frameNumber(String fileName) {
        int underscore = fileName.lastIndexOf('_');
        int dot = fileName.lastIndexOf('.');
        return Integer.parseInt(fileName.substring(underscore + 1, dot));
    }

    private static void assertExactRootModel(Path model) throws IOException {
        JsonObject root = parseObject(Files.readString(model));
        assertEquals(Set.of("parent", "textures", "overrides"), root.keySet());
        assertEquals("item/generated", root.get("parent").getAsString());
        JsonObject textures = root.getAsJsonObject("textures");
        assertEquals(Set.of("layer0"), textures.keySet());
        assertEquals(
                "etherology:item/warp_counter_0",
                textures.get("layer0").getAsString()
        );
        JsonArray overrides = root.getAsJsonArray("overrides");
        assertEquals(15, overrides.size());
        for (int frame = 0; frame <= 14; frame++) {
            JsonObject override = overrides.get(frame).getAsJsonObject();
            assertEquals(Set.of("predicate", "model"), override.keySet());
            JsonObject predicate = override.getAsJsonObject("predicate");
            assertEquals(Set.of("warp_counter"), predicate.keySet());
            assertEquals(
                    PREDICATE_VALUES.get(frame),
                    predicate.get("warp_counter").getAsString()
            );
            String expectedModel = frame == 0
                    ? "etherology:item/warp_counter"
                    : "etherology:item/warp_counter_" + frame;
            assertEquals(expectedModel, override.get("model").getAsString());
        }
    }

    private static void assertExactChildModel(Path model, int frame)
            throws IOException {
        JsonObject root = parseObject(Files.readString(model));
        assertEquals(Set.of("parent", "textures"), root.keySet());
        assertEquals("minecraft:item/generated", root.get("parent").getAsString());
        JsonObject textures = root.getAsJsonObject("textures");
        assertEquals(Set.of("layer0"), textures.keySet());
        assertEquals(
                "etherology:item/warp_counter_" + frame,
                textures.get("layer0").getAsString()
        );
    }

    private static void assertExactRecipe(Path recipePath) throws IOException {
        JsonObject recipe = parseObject(Files.readString(recipePath));
        assertEquals(
                Set.of("type", "category", "key", "pattern", "result",
                        "show_notification"),
                recipe.keySet()
        );
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("equipment", recipe.get("category").getAsString());
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals(Set.of("#", "R"), key.keySet());
        assertEquals(
                "etherology:ebony_ingot",
                key.getAsJsonObject("#").get("item").getAsString()
        );
        assertEquals(
                "minecraft:redstone",
                key.getAsJsonObject("R").get("item").getAsString()
        );
        assertEquals(
                List.of(" # ", "#R#", " # "),
                strings(recipe.getAsJsonArray("pattern"))
        );
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals(Set.of("item"), result.keySet());
        assertEquals("etherology:warp_counter", result.get("item").getAsString());
        assertTrue(recipe.get("show_notification").getAsBoolean());
    }

    private static void assertExactAdvancement(Path advancementPath)
            throws IOException {
        JsonObject advancement = parseObject(Files.readString(advancementPath));
        assertEquals(
                Set.of("parent", "criteria", "requirements", "rewards",
                        "sends_telemetry_event"),
                advancement.keySet()
        );
        assertEquals(
                "minecraft:recipes/root",
                advancement.get("parent").getAsString()
        );
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        assertEquals(Set.of("has_ebony_ingot", "has_the_recipe"), criteria.keySet());
        JsonObject hasEbony = criteria.getAsJsonObject("has_ebony_ingot");
        assertEquals("minecraft:inventory_changed", hasEbony.get("trigger").getAsString());
        JsonArray itemPredicates = hasEbony.getAsJsonObject("conditions")
                .getAsJsonArray("items");
        assertEquals(1, itemPredicates.size());
        assertEquals(
                List.of("etherology:ebony_ingot"),
                strings(itemPredicates.get(0).getAsJsonObject().getAsJsonArray("items"))
        );
        JsonObject hasRecipe = criteria.getAsJsonObject("has_the_recipe");
        assertEquals("minecraft:recipe_unlocked", hasRecipe.get("trigger").getAsString());
        assertEquals(
                "etherology:warp_counter",
                hasRecipe.getAsJsonObject("conditions").get("recipe").getAsString()
        );
        JsonArray requirements = advancement.getAsJsonArray("requirements");
        assertEquals(1, requirements.size());
        assertEquals(
                List.of("has_ebony_ingot", "has_the_recipe"),
                strings(requirements.get(0).getAsJsonArray())
        );
        assertEquals(
                List.of("etherology:warp_counter"),
                strings(advancement.getAsJsonObject("rewards").getAsJsonArray("recipes"))
        );
        assertFalse(advancement.get("sends_telemetry_event").getAsBoolean());
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return values;
    }

    private static void assertLanguageName(Path languageFile, String expectedName)
            throws IOException {
        JsonObject language = parseObject(Files.readString(languageFile));
        assertEquals(
                expectedName,
                language.get("item.etherology.warp_counter").getAsString(),
                languageFile.toString()
        );
    }

    private static void assertPackagedLanguage(
            Artifact artifact,
            JarFile jar,
            String entryName,
            String expectedName
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        if (!artifact.includesResources()) {
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
                language.get("item.etherology.warp_counter").getAsString(),
                artifact.description() + ":" + entryName
        );
    }

    private static void assertResource(
            Artifact artifact,
            JarFile jar,
            String entryName,
            Path canonicalFile,
            String expectedSha256
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        if (!artifact.includesResources()) {
            assertEquals(null, entry, artifact.description() + ":" + entryName);
            return;
        }
        assertNotNull(entry, artifact.description() + ":" + entryName);
        byte[] canonicalBytes = Files.readAllBytes(canonicalFile);
        byte[] packagedBytes = jar.getInputStream(entry).readAllBytes();
        assertArrayEquals(
                canonicalBytes,
                packagedBytes,
                artifact.description() + ":" + entryName
        );
        assertEquals(
                expectedSha256,
                sha256(packagedBytes),
                artifact.description() + ":" + entryName
        );
    }

    private static void assertInvocationOnce(
            String description,
            List<String> invocations,
            String invocation
    ) {
        assertEquals(
                1,
                invocations.stream().filter(invocation::equals).count(),
                description + ":" + invocation
        );
    }

    private static void assertEntrypointDelegatesOnce(
            Artifact artifact,
            JarFile jar,
            String entrypoint,
            String methodName,
            String delegateInvocation,
            String forbiddenDirectInvocation
    ) throws IOException {
        List<String> invocations = methodInvocations(
                classReader(jar, entrypoint),
                methodName
        );
        assertInvocationOnce(artifact.description(), invocations, delegateInvocation);
        assertEquals(
                0,
                invocations.stream().filter(forbiddenDirectInvocation::equals).count(),
                artifact.description() + ":" + forbiddenDirectInvocation
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

    private static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        assertTrue(element.isJsonObject());
        return element.getAsJsonObject();
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
                artifact(
                        "forgeShadowJar",
                        "Forge shadow JAR",
                        true,
                        false,
                        true
                )
        );
    }

    private static Artifact artifact(
            String suffix,
            String description,
            boolean includesResources,
            boolean fabricApplication,
            boolean forgeApplication
    ) throws IOException {
        Path path = requiredPath("etherology.warpCounter." + suffix);
        requireRegularFile(path);
        return new Artifact(
                path,
                description,
                includesResources,
                fabricApplication,
                forgeApplication
        );
    }

    private static Map<Integer, String> childModelHashes() {
        Map<Integer, String> hashes = new LinkedHashMap<>();
        hashes.put(1, "4b4e7c6aaa9cf1672bbaf702b0ffe48e417fde492465d8eba0c9b7133a8d1fc6");
        hashes.put(2, "8f8ee8d6c4fbeb0267911ffa543da4310e9533d84277fee6c18f1e74fd4f1e12");
        hashes.put(3, "9c7ad7dabf0fc0084e235276890b91afbfbdc1b1bb86c6fb8f4754e188b1a0e5");
        hashes.put(4, "3a1943ed1fbb13356bacc8644295f772faf7d2664808403bd4eb70c89ee8e4c1");
        hashes.put(5, "5d9bc48ad34683b7500dc958e54273933c3bb8ac53c8331b02983a7305450697");
        hashes.put(6, "2fa489ffcf6718c2039e5dbfcb9cfd969daf22cb0e8c1c566f7eb882db0cf2b5");
        hashes.put(7, "882e8cb6ef3afc9966af6b0684346470bb11f496cc9c94eb748ea6ba7141203a");
        hashes.put(8, "36634929f727509425c668c5a5528704342a691b447a2a8e6c8ca5c7151c5723");
        hashes.put(9, "f7dd0e6f35785b74fe5b17466561a5f5b15aacadcd06584aa1fba4f4d22ad1a6");
        hashes.put(10, "43e6838d9bca344c576a5be961d55804126db2822b793c2b5aebc789461fb94f");
        hashes.put(11, "d62b683d4ce48774603fa656ada4161a4905055d672a91f13e026a88e0aff0a8");
        hashes.put(12, "8d79a551dd76c960967706211e7353cc0fc8e0413b8a803cdd0a93f1845d5017");
        hashes.put(13, "a3c343e7c8e12227d9a7e7ed5b04ae98fd46514b068ef34b801ad0d826b515d1");
        hashes.put(14, "89f28663405eaa01e114bb10b3902eb01168bc12c29059200587e25334a1a90b");
        return Map.copyOf(hashes);
    }

    private static Map<Integer, String> textureHashes() {
        Map<Integer, String> hashes = new LinkedHashMap<>();
        hashes.put(0, "9edbf3661122594049e1d583ee37408624b15aa3322ce4e7b745d8aba3ccc58c");
        hashes.put(1, "6a3be148d2c83a33aada686128223b6a202b2028321b2775fcec4ce211846e42");
        hashes.put(2, "2271c1d1b1c307976b800250399d753780099fd02fa77153d3386ce7a1ba9565");
        hashes.put(3, "38b5a001dfebe8f9b7cfcb3b19338f442506168d93a09c8432e35681bee7c201");
        hashes.put(4, "0814b0185160599f3087db98d91c531b1dd63073ac028f1a5823a4dd0f81eadc");
        hashes.put(5, "8dc07f5157567aa073d93ce8056adedd07f06ec070dee3bbd553721a5d0c7dfb");
        hashes.put(6, "78717e7caa0922e1df85fd578b44a287cb37c79087677ded56269ebc3f811ab4");
        hashes.put(7, "a5c26d325b777f7e8816067af8fcfe6354f88f8129fd24c3e4c9c722b7fd7564");
        hashes.put(8, "e759ad1a700296cc2cd1daecc97134ad0434be83254034a6152b2888ca3e47fe");
        hashes.put(9, "9b4ca48d8c85e2c6c9702ebefc28f961164af9777131a15e64da7a9759ef6e38");
        hashes.put(10, "ecd8e005150e0e9d4b592251185c179f5c76a113a767fb13a9e2866a7ea402f1");
        hashes.put(11, "cd6ac010ca76b7f7fa9f8186fefc1f533e44c690e8737d37eb3cb272922ab11c");
        hashes.put(12, "9d0dbc267cb39fa58759d6c60dbd4fcce472aa9e2a99db8c220b76631b8196d9");
        hashes.put(13, "b9dfcdbcd7bfa4312ccf7991dd95d507266d853436072548c57e7639de3bab27");
        hashes.put(14, "81ffa27d09b1d833dae6878750393a957fd0b098198172214f773f4d93a429da");
        return Map.copyOf(hashes);
    }

    private record FieldInfo(
            int access,
            String name,
            String descriptor,
            String signature
    ) {
    }

    private record RegistrationOwnership(
            boolean idPresent,
            boolean sharedDeferredRegistration,
            boolean directRegistryRegistration
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
}
