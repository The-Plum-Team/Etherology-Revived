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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ForestLanternBlockResourcesTest {

    private static final String FOREST_LANTERN_ID = "forest_lantern";
    private static final String SHARED_BLOCKS =
            "ru/feytox/etherology/registry/block/SharedForestLanternBlocks.class";
    private static final String SHARED_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/SharedForestLanternBlocks";
    private static final String SHARED_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/SharedForestLanternBlockItems.class";
    private static final String SHARED_BLOCK_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedForestLanternBlockItems";
    private static final String FOREST_LANTERN_BLOCK =
            "ru/feytox/etherology/block/forestLantern/ForestLanternBlock.class";
    private static final String FOREST_LANTERN_BLOCK_OWNER =
            "ru/feytox/etherology/block/forestLantern/ForestLanternBlock";
    private static final String SHARED_TAGS =
            "ru/feytox/etherology/data/SharedForestLanternBlockTags.class";
    private static final String PLAYER_JUMP_CALLBACK =
            "ru/feytox/etherology/util/event/PlayerJumpCallback.class";
    private static final String PLAYER_JUMP_CALLBACK_OWNER =
            "ru/feytox/etherology/util/event/PlayerJumpCallback";
    private static final String SHARED_DEFERRED_REGISTER_OWNER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String FORGE_JUMP_EVENTS =
            "ru/feytox/etherology/forge/ForgeForestLanternEvents.class";
    private static final String FORGE_CLIENT_EVENTS =
            "ru/feytox/etherology/forge/client/ForgeClientEvents.class";
    private static final String ENGLISH_LANGUAGE =
            "assets/etherology/lang/en_us.json";
    private static final String RUSSIAN_LANGUAGE =
            "assets/etherology/lang/ru_ru.json";
    private static final String UNSAFE_ITEM_PEACH_LOG_TAG =
            "data/etherology/tags/items/peach_logs.json";

    @Test
    void everyArtifactHasExactlyOneSharedBlockItemAndMechanicsOwner()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                for (String commonClass : List.of(
                        SHARED_BLOCKS,
                        SHARED_BLOCK_ITEMS,
                        FOREST_LANTERN_BLOCK,
                        SHARED_TAGS,
                        PLAYER_JUMP_CALLBACK
                )) {
                    assertEquals(
                            1,
                            entries.stream().filter(commonClass::equals).count(),
                            artifact.description() + ":" + commonClass
                    );
                    assertNoLoaderSpecificReferences(
                            artifact.description(),
                            classReader(jar, commonClass)
                    );
                }

                assertSharedRegistryShape(
                        artifact.description(),
                        classReader(jar, SHARED_BLOCKS),
                        "BLOCKS",
                        "FOREST_LANTERN"
                );
                assertSharedRegistryShape(
                        artifact.description(),
                        classReader(jar, SHARED_BLOCK_ITEMS),
                        "ITEMS",
                        "FOREST_LANTERN_ITEM"
                );
                assertOnlyExpectedRegistrationOwners(artifact, jar, entries);
                assertBootstrapOrder(artifact, jar);

                if (artifact.forgeApplication()) {
                    assertForgeJumpAndCutoutBridges(artifact, jar, entries);
                }
            }
        }
    }

    @Test
    void canonicalCommonBytecodeKeepsTheLazyCustomFactoryAndMappedBlockItem()
            throws IOException {
        Artifact common = artifact("commonJar", "common JAR", false, false, false);
        try (JarFile jar = common.open()) {
            RegistrationTrace block = registrationTrace(
                    classReader(jar, SHARED_BLOCKS),
                    SHARED_BLOCKS_OWNER
            );
            assertEquals(List.of(FOREST_LANTERN_ID), block.ids());
            assertEquals(1, block.deferredRegistrationCalls());
            assertEquals(0, block.supplierGetCalls());
            assertEquals(
                    List.of(FOREST_LANTERN_BLOCK_OWNER + "#<init>()V"),
                    block.factoryHandles()
            );

            RegistrationTrace item = registrationTrace(
                    classReader(jar, SHARED_BLOCK_ITEMS),
                    SHARED_BLOCK_ITEMS_OWNER
            );
            assertEquals(List.of(FOREST_LANTERN_ID), item.ids());
            assertEquals(1, item.deferredRegistrationCalls());
            assertEquals(0, item.supplierGetCalls());

            ItemFactoryTrace factory = itemFactoryTrace(
                    classReader(jar, SHARED_BLOCK_ITEMS)
            );
            assertEquals(1, factory.sharedBlockReads());
            assertEquals(1, factory.supplierGets());
            assertEquals(1, factory.blockItemConstructions());
            assertEquals(1, factory.appendBlocksCalls());
            assertEquals(1, factory.blockItemMapReads());
            assertTrue(factory.sharedBlockReadIndex() < factory.supplierGetIndex());
            assertTrue(factory.supplierGetIndex() < factory.blockItemConstructionIndex());
            assertTrue(factory.blockItemConstructionIndex() < factory.appendBlocksIndex());
        }
    }

    @Test
    void blockstateDefinesExactlyFiveAgesAcrossFourHorizontalFacings()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.forestLantern.repositoryRoot");
        JsonObject variants = parseObject(Files.readString(repositoryRoot.resolve(
                "src/client/resources/assets/etherology/blockstates/forest_lantern.json"
        ))).getAsJsonObject("variants");
        assertEquals(20, variants.size());

        Map<String, Integer> rotations = Map.of(
                "north", 0,
                "east", 90,
                "south", 180,
                "west", 270
        );
        for (int age = 0; age <= 4; age++) {
            String model = age == 4
                    ? "etherology:block/forest_lantern"
                    : "etherology:block/forest_lantern_" + age;
            for (Map.Entry<String, Integer> facing : rotations.entrySet()) {
                String key = "age=" + age + ",facing=" + facing.getKey();
                JsonObject variant = variants.getAsJsonObject(key);
                assertNotNull(variant, key);
                assertEquals(model, variant.get("model").getAsString(), key);
                if (facing.getValue() == 0) {
                    assertFalse(variant.has("y"), key);
                } else {
                    assertEquals(facing.getValue(), variant.get("y").getAsInt(), key);
                }
            }
        }
    }

    @Test
    void everyPackagedVisualAndDataFileMatchesTheCanonicalBytes()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.forestLantern.repositoryRoot");
        for (CanonicalResource resource : canonicalResources()) {
            Path canonical = repositoryRoot.resolve(resource.repositoryPath());
            requireRegularFile(canonical);
            assertEquals(resource.sha256(), sha256(Files.readAllBytes(canonical)));
        }

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                for (CanonicalResource resource : canonicalResources()) {
                    JarEntry entry = jar.getJarEntry(resource.jarEntry());
                    if (!artifact.includesAssets()) {
                        assertNull(
                                entry,
                                artifact.description() + ":" + resource.jarEntry()
                        );
                        continue;
                    }
                    assertNotNull(
                            entry,
                            artifact.description() + ":" + resource.jarEntry()
                    );
                    byte[] canonical = Files.readAllBytes(
                            repositoryRoot.resolve(resource.repositoryPath())
                    );
                    byte[] packaged = jar.getInputStream(entry).readAllBytes();
                    assertArrayEquals(
                            canonical,
                            packaged,
                            artifact.description() + ":" + resource.jarEntry()
                    );
                    assertEquals(
                            resource.sha256(),
                            sha256(packaged),
                            artifact.description() + ":" + resource.jarEntry()
                    );
                }

                assertLanguage(
                        artifact,
                        jar,
                        ENGLISH_LANGUAGE,
                        "Forest Lantern"
                );
                assertLanguage(
                        artifact,
                        jar,
                        RUSSIAN_LANGUAGE,
                        "Лесной фонарь"
                );
                if (artifact.forgeApplication()) {
                    assertNull(
                            jar.getJarEntry(UNSAFE_ITEM_PEACH_LOG_TAG),
                            artifact.description() + ":" + UNSAFE_ITEM_PEACH_LOG_TAG
                    );
                }
            }
        }
    }

    @Test
    void lootDropsOnlyAStageFourForestLantern() throws IOException {
        Path root = requiredPath("etherology.forestLantern.repositoryRoot");
        JsonObject loot = parseObject(Files.readString(root.resolve(
                "src/main/generated/data/etherology/loot_tables/blocks/"
                        + "forest_lantern.json"
        )));
        assertEquals("minecraft:block", loot.get("type").getAsString());
        JsonArray pools = loot.getAsJsonArray("pools");
        assertEquals(1, pools.size());
        JsonObject pool = pools.get(0).getAsJsonObject();
        assertEquals(1.0F, pool.get("rolls").getAsFloat());
        assertEquals(0.0F, pool.get("bonus_rolls").getAsFloat());
        assertEquals(
                "minecraft:survives_explosion",
                onlyObject(pool.getAsJsonArray("conditions")).get("condition").getAsString()
        );

        JsonObject entry = onlyObject(pool.getAsJsonArray("entries"));
        assertEquals("minecraft:item", entry.get("type").getAsString());
        assertEquals("etherology:forest_lantern", entry.get("name").getAsString());
        JsonObject maturity = onlyObject(entry.getAsJsonArray("conditions"));
        assertEquals(
                "minecraft:block_state_property",
                maturity.get("condition").getAsString()
        );
        assertEquals(
                "etherology:forest_lantern",
                maturity.get("block").getAsString()
        );
        assertEquals("4", maturity.getAsJsonObject("properties").get("age").getAsString());
    }

    @Test
    void sanitizedBlockTagsKeepMissingPeachWoodOptionalAndLanternMineable()
            throws IOException {
        Path root = requiredPath("etherology.forestLantern.repositoryRoot");
        JsonObject peachLogs = parseObject(Files.readString(root.resolve(
                "src/main/generated/data/etherology/tags/blocks/peach_logs.json"
        )));
        assertFalse(peachLogs.get("replace").getAsBoolean());
        assertEquals(
                List.of(
                        new OptionalTagValue("etherology:peach_log", false),
                        new OptionalTagValue("etherology:peach_wood", false),
                        new OptionalTagValue("etherology:stripped_peach_log", false),
                        new OptionalTagValue("etherology:stripped_peach_wood", false)
                ),
                optionalTagValues(peachLogs.getAsJsonArray("values"))
        );

        JsonObject hoeMineable = parseObject(Files.readString(root.resolve(
                "src/main/generated/data/minecraft/tags/blocks/mineable/hoe.json"
        )));
        assertFalse(hoeMineable.get("replace").getAsBoolean());
        JsonArray values = hoeMineable.getAsJsonArray("values");
        assertEquals(2, values.size());
        assertEquals(
                new OptionalTagValue("etherology:peach_leaves", false),
                optionalTagValue(values.get(0).getAsJsonObject())
        );
        assertEquals("etherology:forest_lantern", values.get(1).getAsString());
    }

    @Test
    void exactCookingAndLeatherRecipesRetainInputsOutputsTimesAndUnlocks()
            throws IOException {
        Path root = requiredPath("etherology.forestLantern.repositoryRoot");
        Map<String, CookingRecipe> cookingRecipes = Map.of(
                "forest_lantern_crumb",
                new CookingRecipe("minecraft:smelting", 200),
                "forest_lantern_crumb_from_smoking",
                new CookingRecipe("minecraft:smoking", 100),
                "forest_lantern_crumb_from_campfire",
                new CookingRecipe("minecraft:campfire_cooking", 600)
        );
        for (Map.Entry<String, CookingRecipe> expected : cookingRecipes.entrySet()) {
            JsonObject recipe = recipe(root, expected.getKey());
            assertEquals(expected.getValue().type(), recipe.get("type").getAsString());
            assertEquals("food", recipe.get("category").getAsString());
            assertEquals(expected.getValue().cookingTime(), recipe.get("cookingtime").getAsInt());
            assertEquals(0.35F, recipe.get("experience").getAsFloat());
            assertEquals(
                    "etherology:forest_lantern",
                    recipe.getAsJsonObject("ingredient").get("item").getAsString()
            );
            assertEquals(
                    "etherology:forest_lantern_crumb",
                    recipe.get("result").getAsString()
            );
            assertRecipeAdvancement(root, "food", expected.getKey());
        }

        JsonObject leather = recipe(root, "leather");
        assertEquals("minecraft:crafting_shaped", leather.get("type").getAsString());
        assertEquals("misc", leather.get("category").getAsString());
        assertEquals(
                List.of("SFS", " F ", "SFS"),
                strings(leather.getAsJsonArray("pattern"))
        );
        assertEquals(
                Map.of(
                        "F", "etherology:forest_lantern",
                        "S", "minecraft:string"
                ),
                shapedKey(leather.getAsJsonObject("key"))
        );
        assertEquals(
                "minecraft:leather",
                leather.getAsJsonObject("result").get("item").getAsString()
        );
        assertTrue(leather.get("show_notification").getAsBoolean());
        assertRecipeAdvancement(root, "misc", "leather");
    }

    private static void assertSharedRegistryShape(
            String description,
            ClassReader reader,
            String privateField,
            String publicField
    ) {
        AtomicInteger access = new AtomicInteger();
        Map<String, FieldShape> fields = new LinkedHashMap<>();
        AtomicInteger privateConstructors = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int classAccess,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                access.set(classAccess);
            }

            @Override
            public FieldVisitor visitField(
                    int fieldAccess,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.put(name, new FieldShape(fieldAccess, descriptor));
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int methodAccess,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (name.equals("<init>")
                        && descriptor.equals("()V")
                        && (methodAccess & Opcodes.ACC_PRIVATE) != 0) {
                    privateConstructors.incrementAndGet();
                }
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((access.get() & Opcodes.ACC_PUBLIC) != 0, description);
        assertTrue((access.get() & Opcodes.ACC_FINAL) != 0, description);
        assertEquals(List.of(privateField, publicField), new ArrayList<>(fields.keySet()));
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                fields.get(privateField).descriptor(),
                description
        );
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fields.get(privateField).access(),
                description
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier;",
                fields.get(publicField).descriptor(),
                description
        );
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fields.get(publicField).access(),
                description
        );
        assertEquals(1, privateConstructors.get(), description);
    }

    private static RegistrationTrace registrationTrace(
            ClassReader reader,
            String classOwner
    ) {
        List<String> ids = new ArrayList<>();
        List<String> factoryHandles = new ArrayList<>();
        AtomicInteger deferredRegistrationCalls = new AtomicInteger();
        AtomicInteger supplierGetCalls = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
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
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (FOREST_LANTERN_ID.equals(value)) {
                            ids.add(FOREST_LANTERN_ID);
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String name,
                            String descriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle
                                    && handle.getOwner().equals(FOREST_LANTERN_BLOCK_OWNER)
                                    && handle.getName().equals("<init>")) {
                                factoryHandles.add(
                                        handle.getOwner()
                                                + "#"
                                                + handle.getName()
                                                + handle.getDesc()
                                );
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
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && name.equals("register")) {
                            deferredRegistrationCalls.incrementAndGet();
                        }
                        if (owner.equals(REGISTRY_SUPPLIER_OWNER) && name.equals("get")) {
                            supplierGetCalls.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new RegistrationTrace(
                ids,
                factoryHandles,
                deferredRegistrationCalls.get(),
                supplierGetCalls.get()
        );
    }

    private static ItemFactoryTrace itemFactoryTrace(ClassReader reader) {
        List<ItemFactoryTrace> traces = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                AtomicInteger instructionIndex = new AtomicInteger();
                AtomicInteger sharedBlockReads = new AtomicInteger();
                AtomicInteger supplierGets = new AtomicInteger();
                AtomicInteger constructions = new AtomicInteger();
                AtomicInteger appendCalls = new AtomicInteger();
                AtomicInteger blockItemMapReads = new AtomicInteger();
                AtomicInteger sharedBlockReadIndex = new AtomicInteger(-1);
                AtomicInteger supplierGetIndex = new AtomicInteger(-1);
                AtomicInteger constructionIndex = new AtomicInteger(-1);
                AtomicInteger appendIndex = new AtomicInteger(-1);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        int index = instructionIndex.incrementAndGet();
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_BLOCKS_OWNER)
                                && name.equals("FOREST_LANTERN")) {
                            sharedBlockReads.incrementAndGet();
                            sharedBlockReadIndex.set(index);
                        }
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals("net/minecraft/item/Item")
                                && name.equals("BLOCK_ITEMS")) {
                            blockItemMapReads.incrementAndGet();
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
                        int index = instructionIndex.incrementAndGet();
                        if (owner.equals(REGISTRY_SUPPLIER_OWNER) && name.equals("get")) {
                            supplierGets.incrementAndGet();
                            supplierGetIndex.set(index);
                        }
                        if (owner.equals("net/minecraft/item/BlockItem")
                                && name.equals("<init>")) {
                            constructions.incrementAndGet();
                            constructionIndex.set(index);
                        }
                        if (owner.equals("net/minecraft/item/BlockItem")
                                && name.equals("appendBlocks")) {
                            appendCalls.incrementAndGet();
                            appendIndex.set(index);
                        }
                    }

                    @Override
                    public void visitEnd() {
                        if (constructions.get() == 1) {
                            traces.add(new ItemFactoryTrace(
                                    sharedBlockReads.get(),
                                    supplierGets.get(),
                                    constructions.get(),
                                    appendCalls.get(),
                                    blockItemMapReads.get(),
                                    sharedBlockReadIndex.get(),
                                    supplierGetIndex.get(),
                                    constructionIndex.get(),
                                    appendIndex.get()
                            ));
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, traces.size());
        return traces.get(0);
    }

    private static void assertOnlyExpectedRegistrationOwners(
            Artifact artifact,
            JarFile jar,
            List<String> entries
    ) throws IOException {
        Set<String> deferredOwners = new LinkedHashSet<>();
        Set<String> directOwners = new LinkedHashSet<>();
        for (String entry : entries) {
            if (!entry.startsWith("ru/feytox/etherology/") || !entry.endsWith(".class")) {
                continue;
            }
            RegistrationOwnership ownership = registrationOwnership(classReader(jar, entry));
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
                Set.of(SHARED_BLOCKS_OWNER, SHARED_BLOCK_ITEMS_OWNER),
                deferredOwners,
                artifact.description()
        );
        assertEquals(Set.of(), directOwners, artifact.description());
    }

    private static RegistrationOwnership registrationOwnership(ClassReader reader) {
        AtomicBoolean idPresent = new AtomicBoolean();
        AtomicBoolean deferredRegistration = new AtomicBoolean();
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
                        if (FOREST_LANTERN_ID.equals(value)) {
                            idPresent.set(true);
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
                            deferredRegistration.set(true);
                        }
                        if (isDirectRegistryOwner(owner)) {
                            directRegistration.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new RegistrationOwnership(
                idPresent.get(),
                deferredRegistration.get(),
                directRegistration.get()
        );
    }

    private static boolean isDirectRegistryOwner(String owner) {
        return owner.equals("net/minecraft/registry/Registry")
                || owner.equals("net/minecraft/class_2378")
                || owner.equals("net/minecraft/core/Registry")
                || owner.startsWith("net/minecraftforge/registries/");
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
        String blockRegistration = SHARED_BLOCKS_OWNER + "#register()V";
        String itemRegistration = SHARED_BLOCK_ITEMS_OWNER + "#register()V";
        String jumpRegistration = FOREST_LANTERN_BLOCK_OWNER + "#registerJumpEvent()V";
        assertEquals(1, count(invocations, blockRegistration), artifact.description());
        assertEquals(1, count(invocations, itemRegistration), artifact.description());
        assertEquals(1, count(invocations, jumpRegistration), artifact.description());
        assertTrue(
                invocations.indexOf(blockRegistration) < invocations.indexOf(itemRegistration),
                artifact.description()
        );
        assertTrue(
                invocations.indexOf(itemRegistration) < invocations.indexOf(jumpRegistration),
                artifact.description()
        );
    }

    private static void assertForgeJumpAndCutoutBridges(
            Artifact artifact,
            JarFile jar,
            List<String> entries
    ) throws IOException {
        assertEquals(1, entries.stream().filter(FORGE_JUMP_EVENTS::equals).count());
        assertEquals(1, entries.stream().filter(FORGE_CLIENT_EVENTS::equals).count());

        List<String> jumpInvocations = methodInvocations(
                classReader(jar, FORGE_JUMP_EVENTS),
                "onLivingJump"
        );
        assertTrue(containsInvocationNamed(jumpInvocations, "getEntity"));
        assertTrue(containsInvocationNamed(jumpInvocations, "invoker"));
        assertTrue(containsInvocationNamed(jumpInvocations, "beforeJump"));
        assertTrue(readsField(
                classReader(jar, FORGE_JUMP_EVENTS),
                "onLivingJump",
                PLAYER_JUMP_CALLBACK_OWNER,
                "BEFORE_JUMP"
        ));

        List<String> clientInvocations = methodInvocations(
                classReader(jar, FORGE_CLIENT_EVENTS),
                "registerClientContent"
        );
        assertTrue(containsInvocationNamed(clientInvocations, "getCutout"));
        assertTrue(clientInvocations.stream().anyMatch(
                invocation -> invocation.startsWith(
                        "dev/architectury/registry/client/rendering/RenderTypeRegistry"
                                + "#register("
                )
        ));
        assertTrue(readsField(
                classReader(jar, FORGE_CLIENT_EVENTS),
                "registerClientContent",
                SHARED_BLOCKS_OWNER,
                "FOREST_LANTERN"
        ));
    }

    private static List<String> methodInvocations(ClassReader reader, String methodName) {
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

    private static boolean readsField(
            ClassReader reader,
            String methodName,
            String expectedOwner,
            String expectedName
    ) {
        AtomicBoolean found = new AtomicBoolean();
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
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (owner.equals(expectedOwner) && name.equals(expectedName)) {
                            found.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found.get();
    }

    private static boolean containsInvocationNamed(
            List<String> invocations,
            String methodName
    ) {
        return invocations.stream().anyMatch(
                invocation -> invocation.contains("#" + methodName + "(")
        );
    }

    private static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
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
                check(superName);
                check(signature);
                if (interfaces != null) {
                    for (String interfaceName : interfaces) {
                        check(interfaceName);
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
                if (value != null
                        && (value.contains("net/fabricmc/")
                        || value.contains("net/minecraftforge/")
                        || value.contains("ru/feytox/etherology/client/"))) {
                    forbidden.add(value);
                }
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(Set.of(), forbidden, description);
    }

    private static void assertLanguage(
            Artifact artifact,
            JarFile jar,
            String entryName,
            String expectedName
    ) throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        if (!artifact.includesAssets()) {
            assertNull(entry, artifact.description() + ":" + entryName);
            return;
        }
        assertNotNull(entry, artifact.description() + ":" + entryName);
        JsonObject language = parseObject(new String(
                jar.getInputStream(entry).readAllBytes(),
                StandardCharsets.UTF_8
        ));
        assertEquals(
                expectedName,
                language.get("block.etherology.forest_lantern").getAsString(),
                artifact.description() + ":" + entryName
        );
    }

    private static JsonObject recipe(Path root, String id) throws IOException {
        return parseObject(Files.readString(root.resolve(
                "src/main/generated/data/etherology/recipes/" + id + ".json"
        )));
    }

    private static void assertRecipeAdvancement(
            Path root,
            String category,
            String id
    ) throws IOException {
        JsonObject advancement = parseObject(Files.readString(root.resolve(
                "src/main/generated/data/etherology/advancements/recipes/"
                        + category
                        + "/"
                        + id
                        + ".json"
        )));
        assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString());
        assertFalse(advancement.get("sends_telemetry_event").getAsBoolean());
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        assertEquals(Set.of("has_forest_lantern", "has_the_recipe"), criteria.keySet());
        JsonObject lanternCriterion = criteria.getAsJsonObject("has_forest_lantern");
        assertEquals(
                "minecraft:inventory_changed",
                lanternCriterion.get("trigger").getAsString()
        );
        JsonArray itemGroups = lanternCriterion
                .getAsJsonObject("conditions")
                .getAsJsonArray("items");
        JsonArray items = onlyObject(itemGroups).getAsJsonArray("items");
        assertEquals(List.of("etherology:forest_lantern"), strings(items));

        String recipeId = "etherology:" + id;
        JsonObject recipeCriterion = criteria.getAsJsonObject("has_the_recipe");
        assertEquals("minecraft:recipe_unlocked", recipeCriterion.get("trigger").getAsString());
        assertEquals(
                recipeId,
                recipeCriterion.getAsJsonObject("conditions").get("recipe").getAsString()
        );
        assertEquals(
                List.of("has_forest_lantern", "has_the_recipe"),
                strings(advancement.getAsJsonArray("requirements").get(0).getAsJsonArray())
        );
        assertEquals(
                List.of(recipeId),
                strings(advancement.getAsJsonObject("rewards").getAsJsonArray("recipes"))
        );
    }

    private static Map<String, String> shapedKey(JsonObject key) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String symbol : key.keySet()) {
            values.put(symbol, key.getAsJsonObject(symbol).get("item").getAsString());
        }
        return values;
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return values;
    }

    private static List<OptionalTagValue> optionalTagValues(JsonArray array) {
        List<OptionalTagValue> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(optionalTagValue(element.getAsJsonObject()));
        }
        return values;
    }

    private static OptionalTagValue optionalTagValue(JsonObject value) {
        return new OptionalTagValue(
                value.get("id").getAsString(),
                value.get("required").getAsBoolean()
        );
    }

    private static JsonObject onlyObject(JsonArray array) {
        assertEquals(1, array.size());
        return array.get(0).getAsJsonObject();
    }

    private static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        assertTrue(element.isJsonObject());
        return element.getAsJsonObject();
    }

    private static List<CanonicalResource> canonicalResources() {
        return List.of(
                asset(
                        "blockstates/forest_lantern.json",
                        "2e73ec0f61ca32b3e5331c2410362ae2df599d41a5d1499cf2fc84de5dcf74e3"
                ),
                asset(
                        "models/block/forest_lantern.json",
                        "c45939dd725b11d2034406e3ec8b9040a97d6fa9c7c83d3782444a22e7cfd90c"
                ),
                asset(
                        "models/block/forest_lantern_0.json",
                        "ff1afef383ebf725c102a11c36a4bd7671632841bfbdcd788a81824dcbb3af44"
                ),
                asset(
                        "models/block/forest_lantern_1.json",
                        "026b26bfba8c8cec6de47c6dc6e3b097b66e07be30204ff3a7cad420cd9c349d"
                ),
                asset(
                        "models/block/forest_lantern_2.json",
                        "a16895bea5f381c16c9454b349d172e527d3be2d639d2d8b05505d9a37a6e709"
                ),
                asset(
                        "models/block/forest_lantern_3.json",
                        "6d57d90558f8c270a6a6f00ffb43e4f66f69d2042bc0bb18cd34de38285dde8d"
                ),
                asset(
                        "textures/block/forest_lantern.png",
                        "5ab9532f8b9090492a84479b404c4c3c4ac3733a6d867e50534f54fcc8f310b6"
                ),
                asset(
                        "textures/block/forest_lantern_0.png",
                        "c850de55787124203c0176cf43364ad7459418ee401523cb71b733e58eff97a2"
                ),
                asset(
                        "textures/block/forest_lantern_1.png",
                        "bc9ce2c1e3c310e81c324326b5828924319ebb5c7d23e64fbb9fbf883e930c7f"
                ),
                asset(
                        "textures/block/forest_lantern_2.png",
                        "5e5d5df51ad75e06b99d3dd84bca8516c6b17b90948217a7a9624321ad1a9d1c"
                ),
                asset(
                        "textures/block/forest_lantern_3.png",
                        "f4a76d42b1f7ca0106698ea56ee5045012b33c1a1aa1d433e202b8f1433756cd"
                ),
                generatedAsset(
                        "models/item/forest_lantern.json",
                        "d57283548233724975a2f7d9aeeee41a00df0c0d73b02c314da8829aa6ab3e34"
                ),
                asset(
                        "textures/item/forest_lantern.png",
                        "38bd46a7cb5b35dd28ee2b6ff718c190596f1a2b95a3138f90b6fa19fce7d143"
                ),
                data(
                        "etherology/loot_tables/blocks/forest_lantern.json",
                        "bc5d6dafff947bc7ac9745f29974197d8dfc2d39c37c5513b732e7fc87b8610c"
                ),
                data(
                        "etherology/recipes/forest_lantern_crumb.json",
                        "8456f6a8c8bc91b98d85c529117f2ed93de5f1928a0a8d9e148492193ad3e655"
                ),
                data(
                        "etherology/recipes/forest_lantern_crumb_from_campfire.json",
                        "7510bd1ef984fad8a38bf8473f73380b910d2901bbe13a1fe67f9abf405052e4"
                ),
                data(
                        "etherology/recipes/forest_lantern_crumb_from_smoking.json",
                        "7cbbe84e7debcf2fa960c47322906759d11955437a9c4d002ce580c97b21fcae"
                ),
                data(
                        "etherology/recipes/leather.json",
                        "bca7dc011f1ed4696ad50440386d52375c8d20a361fc9e0c9e026abbf1e75ede"
                ),
                data(
                        "etherology/advancements/recipes/food/forest_lantern_crumb.json",
                        "44fb1208ccc0ffa51fa849fe33063e98ae727a3abd2dc4206163f387bdf90405"
                ),
                data(
                        "etherology/advancements/recipes/food/"
                                + "forest_lantern_crumb_from_campfire.json",
                        "7af83d5ffaf7ee7a971c1ee96c156a3c4a2fc80da163918a47aef59008483ffe"
                ),
                data(
                        "etherology/advancements/recipes/food/"
                                + "forest_lantern_crumb_from_smoking.json",
                        "893f226527f330ec8dbd820ef2bbe2d08336a516af7e1654c5e345243ea03417"
                ),
                data(
                        "etherology/advancements/recipes/misc/leather.json",
                        "a11b75a8852658dea993d03d56eb1d955ba993e1882988694c281c90e9dd403d"
                ),
                data(
                        "etherology/tags/blocks/peach_logs.json",
                        "3c8c02ce6aff5f5f4affd2fdc25cfd0f15acd614822a4074d2d213b57d4b898a"
                ),
                data(
                        "minecraft/tags/blocks/mineable/hoe.json",
                        "8e36de3567e2ae321f07adaacc3e7b7fcd0611fb0a1a73f62b17003edb545e79"
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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
            boolean includesAssets,
            boolean fabricApplication,
            boolean forgeApplication
    ) throws IOException {
        Path path = requiredPath("etherology.forestLantern." + suffix);
        requireRegularFile(path);
        return new Artifact(
                path,
                description,
                includesAssets,
                fabricApplication,
                forgeApplication
        );
    }

    private static ClassReader classReader(JarFile jar, String entryName)
            throws IOException {
        JarEntry entry = jar.getJarEntry(entryName);
        assertNotNull(entry, entryName);
        try (var input = jar.getInputStream(entry)) {
            return new ClassReader(input);
        }
    }

    private record FieldShape(int access, String descriptor) {
    }

    private record RegistrationTrace(
            List<String> ids,
            List<String> factoryHandles,
            int deferredRegistrationCalls,
            int supplierGetCalls
    ) {
    }

    private record ItemFactoryTrace(
            int sharedBlockReads,
            int supplierGets,
            int blockItemConstructions,
            int appendBlocksCalls,
            int blockItemMapReads,
            int sharedBlockReadIndex,
            int supplierGetIndex,
            int blockItemConstructionIndex,
            int appendBlocksIndex
    ) {
    }

    private record RegistrationOwnership(
            boolean idPresent,
            boolean sharedDeferredRegistration,
            boolean directRegistryRegistration
    ) {
    }

    private record CanonicalResource(
            String jarEntry,
            String repositoryPath,
            String sha256
    ) {
    }

    private record OptionalTagValue(String id, boolean required) {
    }

    private record CookingRecipe(String type, int cookingTime) {
    }

    private record Artifact(
            Path path,
            String description,
            boolean includesAssets,
            boolean fabricApplication,
            boolean forgeApplication
    ) {

        private JarFile open() throws IOException {
            return new JarFile(path.toFile());
        }
    }
}
