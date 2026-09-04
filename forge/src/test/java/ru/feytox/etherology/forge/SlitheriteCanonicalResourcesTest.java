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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SlitheriteCanonicalResourcesTest {

    private static final String SHARED_BLOCKS =
            "ru/feytox/etherology/registry/block/SharedSlitheriteBlocks.class";
    private static final String SHARED_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/SharedSlitheriteBlocks";
    private static final String SHARED_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/SharedSlitheriteBlockItems.class";
    private static final String SHARED_BLOCK_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedSlitheriteBlockItems";
    private static final String SHARED_DEFERRED_REGISTER_OWNER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String SHARED_DEFERRED_REGISTER_DESCRIPTOR =
            "Lru/feytox/etherology/registry/SharedDeferredRegister;";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";
    private static final String LEGACY_DECO_BLOCKS =
            "ru/feytox/etherology/registry/block/DecoBlocks.class";
    private static final String LEGACY_DECO_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/DecoBlockItems.class";
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
    private static final List<SlitheriteBlock> BLOCKS = List.of(
            block("slitherite", "SLITHERITE", "Slitherite", "Слизерит"),
            block(
                    "slitherite_stairs",
                    "SLITHERITE_STAIRS",
                    "Slitherite Stairs",
                    "Слизеритовые ступеньки"
            ),
            block(
                    "slitherite_slab",
                    "SLITHERITE_SLAB",
                    "Slitherite Slab",
                    "Слизеритовая плита"
            ),
            block(
                    "slitherite_wall",
                    "SLITHERITE_WALL",
                    "Slitherite Wall",
                    "Слизеритовая ограда"
            ),
            block(
                    "polished_slitherite",
                    "POLISHED_SLITHERITE",
                    "Polished Slitherite",
                    "Полированный слизерит"
            ),
            block(
                    "polished_slitherite_stairs",
                    "POLISHED_SLITHERITE_STAIRS",
                    "Polished Slitherite Stairs",
                    "Ступеньки из полированного слизерита"
            ),
            block(
                    "polished_slitherite_slab",
                    "POLISHED_SLITHERITE_SLAB",
                    "Polished Slitherite Slab",
                    "Плита из полированного слизерита"
            ),
            block(
                    "polished_slitherite_wall",
                    "POLISHED_SLITHERITE_WALL",
                    "Polished Slitherite Wall",
                    "Ограда из полированного слизерита"
            ),
            block(
                    "polished_slitherite_button",
                    "POLISHED_SLITHERITE_BUTTON",
                    "Polished Slitherite Button",
                    "Кнопка из полированного слизерита"
            ),
            block(
                    "polished_slitherite_pressure_plate",
                    "POLISHED_SLITHERITE_PRESSURE_PLATE",
                    "Polished Slitherite Pressure Plate",
                    "Нажимная плита из полированного слизерита"
            ),
            block(
                    "polished_slitherite_bricks",
                    "POLISHED_SLITHERITE_BRICKS",
                    "Polished Slitherite Bricks",
                    "Полированные слизеритовые кирпичи"
            ),
            block(
                    "polished_slitherite_brick_stairs",
                    "POLISHED_SLITHERITE_BRICK_STAIRS",
                    "Polished Slitherite Brick Stairs",
                    "Ступеньки из полированного слизеритового кирпича"
            ),
            block(
                    "polished_slitherite_brick_slab",
                    "POLISHED_SLITHERITE_BRICK_SLAB",
                    "Polished Slitherite Brick Slab",
                    "Плита из полированного слизеритового кирпича"
            ),
            block(
                    "polished_slitherite_brick_wall",
                    "POLISHED_SLITHERITE_BRICK_WALL",
                    "Polished Slitherite Brick Wall",
                    "Ограда из полированного слизеритового кирпича"
            ),
            block(
                    "chiseled_polished_slitherite",
                    "CHISELED_POLISHED_SLITHERITE",
                    "Chiseled Polished Slitherite",
                    "Резной полированный слизерит"
            ),
            block(
                    "chiseled_polished_slitherite_bricks",
                    "CHISELED_POLISHED_SLITHERITE_BRICKS",
                    "Chiseled Polished Slitherite Bricks",
                    "Резные полированные слизеритовые кирпичи"
            ),
            block(
                    "cracked_polished_slitherite_bricks",
                    "CRACKED_POLISHED_SLITHERITE_BRICKS",
                    "Cracked Polished Slitherite Bricks",
                    "Потрескавшиеся полированные слизеритовые кирпичи"
            )
    );
    private static final List<String> BLOCK_IDS = BLOCKS.stream()
            .map(SlitheriteBlock::id)
            .toList();
    private static final List<RecipeSpec> RECIPES = recipes();
    private static final Map<String, Set<String>> TAG_MEMBERS = tagMembers();
    private static final List<String> BLOCK_MODELS = List.of(
            "chiseled_polished_slitherite",
            "chiseled_polished_slitherite_bricks",
            "cracked_polished_slitherite_bricks",
            "polished_slitherite",
            "polished_slitherite_brick_slab",
            "polished_slitherite_brick_slab_top",
            "polished_slitherite_brick_stairs",
            "polished_slitherite_brick_stairs_inner",
            "polished_slitherite_brick_stairs_outer",
            "polished_slitherite_brick_wall_inventory",
            "polished_slitherite_brick_wall_post",
            "polished_slitherite_brick_wall_side",
            "polished_slitherite_brick_wall_side_tall",
            "polished_slitherite_bricks",
            "polished_slitherite_button",
            "polished_slitherite_button_inventory",
            "polished_slitherite_button_pressed",
            "polished_slitherite_pressure_plate",
            "polished_slitherite_pressure_plate_down",
            "polished_slitherite_slab",
            "polished_slitherite_slab_top",
            "polished_slitherite_stairs",
            "polished_slitherite_stairs_inner",
            "polished_slitherite_stairs_outer",
            "polished_slitherite_wall_inventory",
            "polished_slitherite_wall_post",
            "polished_slitherite_wall_side",
            "polished_slitherite_wall_side_tall",
            "slitherite",
            "slitherite_slab",
            "slitherite_slab_top",
            "slitherite_stairs",
            "slitherite_stairs_inner",
            "slitherite_stairs_outer",
            "slitherite_wall_inventory",
            "slitherite_wall_post",
            "slitherite_wall_side",
            "slitherite_wall_side_tall"
    );
    private static final List<String> TEXTURES = List.of(
            "chiseled_polished_slitherite",
            "chiseled_polished_slitherite_bricks_side",
            "chiseled_polished_slitherite_bricks_top",
            "cracked_polished_slitherite_bricks",
            "polished_slitherite",
            "polished_slitherite_bricks",
            "slitherite"
    );
    private static final Set<String> CLIENT_VISUAL_ENTRIES = Set.of(
            "blockstates/chiseled_polished_slitherite_bricks.json",
            "models/block/chiseled_polished_slitherite_bricks.json",
            "models/item/chiseled_polished_slitherite_bricks.json",
            "textures/block/chiseled_polished_slitherite.png",
            "textures/block/chiseled_polished_slitherite_bricks_side.png",
            "textures/block/chiseled_polished_slitherite_bricks_top.png",
            "textures/block/cracked_polished_slitherite_bricks.png",
            "textures/block/polished_slitherite.png",
            "textures/block/polished_slitherite_bricks.png",
            "textures/block/slitherite.png"
    );

    @Test
    void everyArtifactHasOneSharedOwnerForEachSlitheriteBlockAndItem()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                assertEquals(1, count(entries, SHARED_BLOCKS), artifact.description());
                assertEquals(1, count(entries, SHARED_BLOCK_ITEMS), artifact.description());

                ClassReader blocks = classReader(jar, SHARED_BLOCKS);
                ClassReader items = classReader(jar, SHARED_BLOCK_ITEMS);
                assertCatalog(
                        artifact.description(),
                        blocks,
                        "BLOCKS",
                        BLOCKS.stream().map(SlitheriteBlock::blockField).toList()
                );
                assertCatalog(
                        artifact.description(),
                        items,
                        "ITEMS",
                        BLOCKS.stream().map(SlitheriteBlock::itemField).toList()
                );
                assertEquals(
                        BLOCK_IDS,
                        registrationIds(
                                blocks,
                                SHARED_DEFERRED_REGISTER_OWNER,
                                "register"
                        ),
                        artifact.description()
                );
                assertEquals(
                        BLOCK_IDS,
                        registrationIds(
                                items,
                                SHARED_BLOCK_ITEMS_OWNER,
                                "registerBlockItem"
                        ),
                        artifact.description()
                );
                assertEquals(
                        BLOCKS.stream().map(SlitheriteBlock::blockField).toList(),
                        blockSupplierReads(items),
                        artifact.description()
                );
                assertNoLoaderSpecificReferences(artifact.description(), blocks);
                assertNoLoaderSpecificReferences(artifact.description(), items);
                assertOnlySharedRegistrationOwners(artifact, jar, entries);
                assertBootstrapOrder(artifact, jar);
                assertLegacyAliases(artifact, jar, entries);
            }
        }
    }

    @Test
    void exactVisualDataAndLanguageResourcesReachEveryApplicationArtifact()
            throws IOException {
        Path repositoryRoot = requiredPath("etherology.slitheriteBlocks.repositoryRoot");
        List<CanonicalResource> resources = canonicalResources();
        assertEquals(79, visualResources().size());
        assertEquals(17 + 29 + 29 + 11, dataResources().size());
        assertEquals(resources.size(), new LinkedHashSet<>(resources).size());
        for (CanonicalResource resource : resources) {
            requireRegularFile(repositoryRoot.resolve(resource.repositoryPath()));
        }
        assertCanonicalOwnedResourceInventory(repositoryRoot, resources);

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

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                assertPackagedOwnedResourceInventory(artifact, jar, resources);
                for (CanonicalResource resource : resources) {
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
    void canonicalLootRecipesAdvancementsAndTagsMatchTheOriginalContract()
            throws IOException {
        Path dataRoot = requiredPath("etherology.slitheriteBlocks.repositoryRoot")
                .resolve("src/main/generated/data");
        for (String id : BLOCK_IDS) {
            JsonObject loot = readObject(
                    dataRoot.resolve("etherology/loot_tables/blocks/" + id + ".json")
            );
            assertEquals("minecraft:block", loot.get("type").getAsString(), id);
            JsonArray pools = loot.getAsJsonArray("pools");
            assertEquals(1, pools.size(), id);
            JsonObject pool = pools.get(0).getAsJsonObject();
            assertEquals(1.0F, pool.get("rolls").getAsFloat(), id);
            assertEquals(0.0F, pool.get("bonus_rolls").getAsFloat(), id);
            assertEquals(1, pool.getAsJsonArray("entries").size(), id);
            JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("minecraft:item", entry.get("type").getAsString(), id);
            assertEquals("etherology:" + id, entry.get("name").getAsString(), id);
        }

        for (RecipeSpec recipe : RECIPES) {
            JsonObject recipeJson = readObject(
                    dataRoot.resolve("etherology/recipes/" + recipe.id() + ".json")
            );
            assertEquals(recipe.type(), recipeJson.get("type").getAsString(), recipe.id());
            assertEquals(recipe.result(), recipeResult(recipeJson), recipe.id());
            assertEquals(recipe.count(), recipeCount(recipeJson), recipe.id());

            JsonObject advancement = readObject(dataRoot.resolve(
                    "etherology/advancements/recipes/" + recipe.advancementCategory()
                            + "/" + recipe.id() + ".json"
            ));
            assertEquals(
                    "minecraft:recipes/root",
                    advancement.get("parent").getAsString(),
                    recipe.id()
            );
            assertTrue(
                    advancement.getAsJsonObject("criteria").has("has_the_recipe"),
                    recipe.id()
            );
            assertEquals(
                    "etherology:" + recipe.id(),
                    advancement.getAsJsonObject("rewards")
                            .getAsJsonArray("recipes")
                            .get(0)
                            .getAsString(),
                    recipe.id()
            );
            assertFalse(
                    advancement.get("sends_telemetry_event").getAsBoolean(),
                    recipe.id()
            );
        }

        for (Map.Entry<String, Set<String>> expected : TAG_MEMBERS.entrySet()) {
            JsonObject tag = readObject(dataRoot.resolve(expected.getKey()));
            assertFalse(tag.get("replace").getAsBoolean(), expected.getKey());
            List<TagValue> members = slitheriteMembers(tag);
            assertEquals(
                    members.size(),
                    new LinkedHashSet<>(members.stream().map(TagValue::id).toList()).size(),
                    expected.getKey()
            );
            assertEquals(
                    expected.getValue(),
                    new LinkedHashSet<>(members.stream().map(TagValue::id).toList()),
                    expected.getKey()
            );
            assertTrue(
                    members.stream().allMatch(TagValue::required),
                    expected.getKey()
            );
        }
    }

    private static void assertCatalog(
            String description,
            ClassReader reader,
            String registryField,
            List<String> expectedSupplierFields
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
        assertEquals(
                new FieldShape(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        SHARED_DEFERRED_REGISTER_DESCRIPTOR
                ),
                shape.fields().get(registryField),
                description
        );
        List<String> publicFields = shape.fields().entrySet().stream()
                .filter(entry -> (entry.getValue().access() & Opcodes.ACC_PUBLIC) != 0)
                .map(Map.Entry::getKey)
                .toList();
        assertEquals(expectedSupplierFields, publicFields, description);
        for (String field : expectedSupplierFields) {
            assertEquals(
                    new FieldShape(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            REGISTRY_SUPPLIER_DESCRIPTOR
                    ),
                    shape.fields().get(field),
                    description + ":" + field
            );
        }
        assertEquals(List.of("register()V"), shape.publicMethods(), description);
        assertEquals(1, shape.privateConstructorCount(), description);
    }

    private static ClassShape classShape(ClassReader reader) {
        int[] classAccess = {0};
        int[] privateConstructorCount = {0};
        Map<String, FieldShape> fields = new LinkedHashMap<>();
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
                if (!name.equals("<clinit>")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && BLOCK_IDS.contains(id)) {
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
        List<String> fields = new ArrayList<>();
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
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals(SHARED_BLOCKS_OWNER)) {
                            fields.add(name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return List.copyOf(fields);
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
                    for (String value : interfaces) check(value);
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
            for (String id : ownership.ids()) {
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
        for (String id : BLOCK_IDS) {
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
        boolean[] directRegistration = {false};
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
                        if (value instanceof String id && BLOCK_IDS.contains(id)) ids.add(id);
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
                        if (DIRECT_REGISTRY_OWNERS.contains(owner)
                                && name.equals("register")) {
                            directRegistration[0] = true;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassOwnership(
                Collections.unmodifiableSet(ids),
                sharedDeferredRegistration[0],
                directRegistration[0]
        );
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
        String blocks = SHARED_BLOCKS_OWNER + "#register()V";
        String items = SHARED_BLOCK_ITEMS_OWNER + "#register()V";
        assertInvocationOnce(artifact.description(), invocations, blocks);
        assertInvocationOnce(artifact.description(), invocations, items);
        assertTrue(invocations.indexOf(blocks) < invocations.indexOf(items));

        if (artifact.fabricApplication()) {
            String legacyItems =
                    "ru/feytox/etherology/registry/item/EItems#registerItems()V";
            assertInvocationOnce(artifact.description(), invocations, legacyItems);
            assertTrue(invocations.indexOf(items) < invocations.indexOf(legacyItems));
            return;
        }

        List<String> boundary = List.of(
                "ru/feytox/etherology/registry/block/SharedAttrahiteBlocks#register()V",
                blocks,
                "ru/feytox/etherology/registry/item/SharedAttrahiteBlockItems#register()V",
                items,
                "ru/feytox/etherology/registry/item/SharedItems#register()V"
        );
        assertOrderedBoundary(artifact.description(), invocations, boundary);
    }

    private static void assertLegacyAliases(
            Artifact artifact,
            JarFile jar,
            List<String> entries
    ) throws IOException {
        if (!artifact.fabricApplication()) {
            assertFalse(entries.contains(LEGACY_DECO_BLOCKS), artifact.description());
            assertFalse(entries.contains(LEGACY_DECO_BLOCK_ITEMS), artifact.description());
            return;
        }
        ClassReader aliases = classReader(jar, LEGACY_DECO_BLOCKS);
        Map<String, String> actual = sharedAliases(aliases);
        Map<String, String> expected = new LinkedHashMap<>();
        for (SlitheriteBlock block : BLOCKS) {
            expected.put(block.blockField(), block.blockField());
        }
        assertEquals(expected, actual, artifact.description());
    }

    private static Map<String, String> sharedAliases(ClassReader reader) {
        Map<String, String> aliases = new LinkedHashMap<>();
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
                    private String sharedField;

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals(SHARED_BLOCKS_OWNER)) {
                            sharedField = name;
                        } else if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(LEGACY_DECO_BLOCKS.substring(
                                        0,
                                        LEGACY_DECO_BLOCKS.length() - ".class".length()
                                ))
                                && sharedField != null) {
                            aliases.put(name, sharedField);
                            sharedField = null;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return Collections.unmodifiableMap(aliases);
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
        return List.copyOf(invocations);
    }

    private static void assertOrderedBoundary(
            String description,
            List<String> invocations,
            List<String> boundary
    ) {
        for (String invocation : boundary) {
            assertInvocationOnce(description, invocations, invocation);
        }
        for (int index = 1; index < boundary.size(); index++) {
            assertTrue(
                    invocations.indexOf(boundary.get(index - 1))
                            < invocations.indexOf(boundary.get(index)),
                    description
            );
        }
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
        assertArrayEquals(
                Files.readAllBytes(repositoryRoot.resolve(resource.repositoryPath())),
                jar.getInputStream(entries.get(0)).readAllBytes(),
                artifact.description() + ":" + resource.jarEntry()
        );
    }

    private static void assertCanonicalOwnedResourceInventory(
            Path repositoryRoot,
            List<CanonicalResource> resources
    ) throws IOException {
        Set<String> expected = ownedResourceEntries(resources);
        List<String> actual = new ArrayList<>();
        for (String sourceRoot : List.of(
                "src/main/generated/assets/etherology",
                "src/client/resources/assets/etherology",
                "src/main/generated/data"
        )) {
            Path root = repositoryRoot.resolve(sourceRoot);
            try (var paths = Files.walk(root)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .map(path -> root.relativize(path).toString().replace('\\', '/'))
                        .map(path -> sourceRoot.endsWith("/data")
                                ? "data/" + path
                                : "assets/etherology/" + path)
                        .filter(SlitheriteCanonicalResourcesTest::isOwnedResourceEntry)
                        .forEach(actual::add);
            }
        }
        assertEquals(actual.size(), new LinkedHashSet<>(actual).size());
        assertEquals(expected, new LinkedHashSet<>(actual));
    }

    private static void assertPackagedOwnedResourceInventory(
            Artifact artifact,
            JarFile jar,
            List<CanonicalResource> resources
    ) {
        Set<String> expected = artifact.includesResources()
                ? ownedResourceEntries(resources)
                : Set.of();
        List<String> actual = jar.stream()
                .map(JarEntry::getName)
                .filter(SlitheriteCanonicalResourcesTest::isOwnedResourceEntry)
                .toList();
        assertEquals(actual.size(), new LinkedHashSet<>(actual).size());
        assertEquals(expected, new LinkedHashSet<>(actual), artifact.description());
    }

    private static Set<String> ownedResourceEntries(List<CanonicalResource> resources) {
        Set<String> entries = new LinkedHashSet<>();
        for (CanonicalResource resource : resources) {
            if (isOwnedResourceEntry(resource.jarEntry())) {
                entries.add(resource.jarEntry());
            }
        }
        return Collections.unmodifiableSet(entries);
    }

    private static boolean isOwnedResourceEntry(String entry) {
        if (!entry.contains("slitherite")) return false;
        return entry.startsWith("assets/etherology/blockstates/")
                || entry.startsWith("assets/etherology/models/block/")
                || entry.startsWith("assets/etherology/models/item/")
                || entry.startsWith("assets/etherology/textures/block/")
                || entry.startsWith("data/etherology/loot_tables/blocks/")
                || entry.startsWith("data/etherology/recipes/")
                || entry.startsWith("data/etherology/advancements/recipes/");
    }

    private static void assertLanguage(
            Path repositoryRoot,
            String repositoryPath,
            boolean russian
    ) throws IOException {
        JsonObject language = readObject(repositoryRoot.resolve(repositoryPath));
        Map<String, String> actual = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : language.entrySet()) {
            if (entry.getKey().startsWith("block.etherology.")
                    && entry.getKey().contains("slitherite")) {
                actual.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        assertEquals(expectedLanguage(russian), actual, repositoryPath);
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
        JsonObject language = JsonParser.parseString(
                new String(packaged, StandardCharsets.UTF_8)
        ).getAsJsonObject();
        for (Map.Entry<String, String> expected : expectedLanguage(russian).entrySet()) {
            assertEquals(
                    expected.getValue(),
                    language.get(expected.getKey()).getAsString(),
                    artifact.description() + ":" + expected.getKey()
            );
        }
    }

    private static Map<String, String> expectedLanguage(boolean russian) {
        Map<String, String> expected = new LinkedHashMap<>();
        for (SlitheriteBlock block : BLOCKS) {
            expected.put(
                    "block.etherology." + block.id(),
                    russian ? block.russianName() : block.englishName()
            );
        }
        return Collections.unmodifiableMap(expected);
    }

    private static List<TagValue> slitheriteMembers(JsonObject tag) {
        List<TagValue> members = new ArrayList<>();
        for (JsonElement value : tag.getAsJsonArray("values")) {
            String id;
            boolean required;
            if (value.isJsonPrimitive()) {
                id = value.getAsString();
                required = true;
            } else {
                JsonObject object = value.getAsJsonObject();
                id = object.get("id").getAsString();
                required = !object.has("required") || object.get("required").getAsBoolean();
            }
            if (id.startsWith("etherology:")
                    && BLOCK_IDS.contains(id.substring("etherology:".length()))) {
                members.add(new TagValue(
                        id.substring("etherology:".length()),
                        required
                ));
            }
        }
        return List.copyOf(members);
    }

    private static String recipeResult(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        return result.isJsonPrimitive()
                ? result.getAsString()
                : result.getAsJsonObject().get("item").getAsString();
    }

    private static int recipeCount(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result.isJsonObject() && result.getAsJsonObject().has("count")) {
            return result.getAsJsonObject().get("count").getAsInt();
        }
        return recipe.has("count") ? recipe.get("count").getAsInt() : 1;
    }

    private static List<CanonicalResource> canonicalResources() {
        List<CanonicalResource> resources = new ArrayList<>(visualResources());
        resources.addAll(dataResources());
        return List.copyOf(resources);
    }

    private static List<CanonicalResource> visualResources() {
        List<String> entries = new ArrayList<>();
        for (String id : BLOCK_IDS) {
            entries.add("blockstates/" + id + ".json");
        }
        for (String id : BLOCK_MODELS) {
            entries.add("models/block/" + id + ".json");
        }
        for (String id : BLOCK_IDS) {
            entries.add("models/item/" + id + ".json");
        }
        for (String id : TEXTURES) {
            entries.add("textures/block/" + id + ".png");
        }
        assertEquals(79, entries.size());
        assertEquals(entries.size(), new LinkedHashSet<>(entries).size());
        return entries.stream().map(SlitheriteCanonicalResourcesTest::visual).toList();
    }

    private static CanonicalResource visual(String path) {
        String source = CLIENT_VISUAL_ENTRIES.contains(path)
                ? "src/client/resources/assets/etherology/"
                : "src/main/generated/assets/etherology/";
        return new CanonicalResource("assets/etherology/" + path, source + path);
    }

    private static List<CanonicalResource> dataResources() {
        List<CanonicalResource> resources = new ArrayList<>();
        for (String id : BLOCK_IDS) {
            resources.add(data("etherology/loot_tables/blocks/" + id + ".json"));
        }
        for (RecipeSpec recipe : RECIPES) {
            resources.add(data("etherology/recipes/" + recipe.id() + ".json"));
            resources.add(data(
                    "etherology/advancements/recipes/" + recipe.advancementCategory()
                            + "/" + recipe.id() + ".json"
            ));
        }
        for (String tag : TAG_MEMBERS.keySet()) {
            resources.add(data(tag));
        }
        return List.copyOf(resources);
    }

    private static CanonicalResource data(String path) {
        return new CanonicalResource(
                "data/" + path,
                "src/main/generated/data/" + path
        );
    }

    private static Map<String, Set<String>> tagMembers() {
        Map<String, Set<String>> tags = new LinkedHashMap<>();
        tags.put("minecraft/tags/blocks/mineable/pickaxe.json", Set.copyOf(BLOCK_IDS));
        tags.put("minecraft/tags/blocks/needs_stone_tool.json", Set.of());
        tags.put("minecraft/tags/blocks/slabs.json", Set.of(
                "slitherite_slab",
                "polished_slitherite_slab",
                "polished_slitherite_brick_slab"
        ));
        tags.put("minecraft/tags/items/slabs.json", tags.get(
                "minecraft/tags/blocks/slabs.json"
        ));
        tags.put("minecraft/tags/blocks/stairs.json", Set.of(
                "slitherite_stairs",
                "polished_slitherite_stairs",
                "polished_slitherite_brick_stairs"
        ));
        tags.put("minecraft/tags/items/stairs.json", tags.get(
                "minecraft/tags/blocks/stairs.json"
        ));
        tags.put("minecraft/tags/blocks/walls.json", Set.of(
                "slitherite_wall",
                "polished_slitherite_wall",
                "polished_slitherite_brick_wall"
        ));
        tags.put("minecraft/tags/items/walls.json", tags.get(
                "minecraft/tags/blocks/walls.json"
        ));
        tags.put("minecraft/tags/blocks/stone_bricks.json", Set.of(
                "polished_slitherite_bricks",
                "chiseled_polished_slitherite_bricks",
                "cracked_polished_slitherite_bricks"
        ));
        tags.put("minecraft/tags/blocks/stone_pressure_plates.json", Set.of(
                "polished_slitherite_pressure_plate"
        ));
        tags.put("minecraft/tags/items/buttons.json", Set.of());
        return Collections.unmodifiableMap(tags);
    }

    private static List<RecipeSpec> recipes() {
        return List.of(
                recipe(
                        "chiseled_polished_slitherite",
                        "minecraft:crafting_shaped",
                        "chiseled_polished_slitherite",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "chiseled_polished_slitherite_bricks",
                        "minecraft:crafting_shaped",
                        "chiseled_polished_slitherite_bricks",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "chiseled_polished_slitherite_bricks_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "minecraft:stonecutting",
                        "chiseled_polished_slitherite_bricks",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "chiseled_polished_slitherite_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "chiseled_polished_slitherite",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "cracked_polished_slitherite_bricks",
                        "minecraft:smelting",
                        "cracked_polished_slitherite_bricks",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite",
                        "minecraft:crafting_shaped",
                        "polished_slitherite",
                        4,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_brick_slab",
                        "minecraft:crafting_shaped",
                        "polished_slitherite_brick_slab",
                        6,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_brick_slab_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "minecraft:stonecutting",
                        "polished_slitherite_brick_slab",
                        2,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_brick_stairs",
                        "minecraft:crafting_shaped",
                        "polished_slitherite_brick_stairs",
                        4,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_brick_stairs_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "minecraft:stonecutting",
                        "polished_slitherite_brick_stairs",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_brick_wall",
                        "minecraft:crafting_shaped",
                        "polished_slitherite_brick_wall",
                        6,
                        "decorations"
                ),
                recipe(
                        "polished_slitherite_brick_wall_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "minecraft:stonecutting",
                        "polished_slitherite_brick_wall",
                        1,
                        "decorations"
                ),
                recipe(
                        "polished_slitherite_bricks",
                        "minecraft:crafting_shaped",
                        "polished_slitherite_bricks",
                        4,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_bricks_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "polished_slitherite_bricks",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_button",
                        "minecraft:crafting_shapeless",
                        "polished_slitherite_button",
                        1,
                        "redstone"
                ),
                recipe(
                        "polished_slitherite_from_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "polished_slitherite",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_pressure_plate",
                        "minecraft:crafting_shaped",
                        "polished_slitherite_pressure_plate",
                        1,
                        "redstone"
                ),
                recipe(
                        "polished_slitherite_slab",
                        "minecraft:crafting_shaped",
                        "polished_slitherite_slab",
                        6,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_slab_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "polished_slitherite_slab",
                        2,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_stairs",
                        "minecraft:crafting_shaped",
                        "polished_slitherite_stairs",
                        4,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_stairs_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "polished_slitherite_stairs",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "polished_slitherite_wall",
                        "minecraft:crafting_shaped",
                        "polished_slitherite_wall",
                        6,
                        "decorations"
                ),
                recipe(
                        "polished_slitherite_wall_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "polished_slitherite_wall",
                        1,
                        "decorations"
                ),
                recipe(
                        "slitherite_slab",
                        "minecraft:crafting_shaped",
                        "slitherite_slab",
                        6,
                        "building_blocks"
                ),
                recipe(
                        "slitherite_slab_from_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "slitherite_slab",
                        2,
                        "building_blocks"
                ),
                recipe(
                        "slitherite_stairs",
                        "minecraft:crafting_shaped",
                        "slitherite_stairs",
                        4,
                        "building_blocks"
                ),
                recipe(
                        "slitherite_stairs_from_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "slitherite_stairs",
                        1,
                        "building_blocks"
                ),
                recipe(
                        "slitherite_wall",
                        "minecraft:crafting_shaped",
                        "slitherite_wall",
                        6,
                        "decorations"
                ),
                recipe(
                        "slitherite_wall_from_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "slitherite_wall",
                        1,
                        "decorations"
                )
        );
    }

    private static SlitheriteBlock block(
            String id,
            String blockField,
            String englishName,
            String russianName
    ) {
        return new SlitheriteBlock(
                id,
                blockField,
                blockField + "_ITEM",
                englishName,
                russianName
        );
    }

    private static RecipeSpec recipe(
            String id,
            String type,
            String result,
            int count,
            String advancementCategory
    ) {
        return new RecipeSpec(
                id,
                type,
                "etherology:" + result,
                count,
                advancementCategory
        );
    }

    private static Map<String, Set<String>> ownerSets() {
        Map<String, Set<String>> owners = new LinkedHashMap<>();
        for (String id : BLOCK_IDS) owners.put(id, new LinkedHashSet<>());
        return owners;
    }

    private static JsonObject readObject(Path path) throws IOException {
        requireRegularFile(path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
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
        Path path = requiredPath("etherology.slitheriteBlocks." + suffix);
        requireRegularFile(path);
        return new Artifact(path, description, includesResources, fabricApplication);
    }

    private record SlitheriteBlock(
            String id,
            String blockField,
            String itemField,
            String englishName,
            String russianName
    ) {
    }

    private record RecipeSpec(
            String id,
            String type,
            String result,
            int count,
            String advancementCategory
    ) {
    }

    private record TagValue(String id, boolean required) {
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

    private record CanonicalResource(String jarEntry, String repositoryPath) {
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
            Set<String> ids,
            boolean sharedDeferredRegistration,
            boolean directRegistration
    ) {
    }
}
