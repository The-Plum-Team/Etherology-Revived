package ru.feytox.etherology.forge;

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

final class FoodItemRegistryResourcesTest {

    private static final String FOOD_ID = "forest_lantern_crumb";
    private static final String FOOD_FIELD = "FOREST_LANTERN_CRUMB";
    private static final String SHARED_FOOD_COMPONENTS =
            "ru/feytox/etherology/registry/item/SharedFoodComponents.class";
    private static final String SHARED_FOOD_COMPONENTS_OWNER =
            "ru/feytox/etherology/registry/item/SharedFoodComponents";
    private static final String SHARED_FOOD_ITEMS =
            "ru/feytox/etherology/registry/item/SharedFoodItems.class";
    private static final String SHARED_FOOD_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedFoodItems";
    private static final String SHARED_DEFERRED_REGISTER_OWNER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String SHARED_DEFERRED_REGISTER_DESCRIPTOR =
            "Lru/feytox/etherology/registry/SharedDeferredRegister;";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";
    private static final String LEGACY_E_ITEMS =
            "ru/feytox/etherology/registry/item/EItems.class";
    private static final String LEGACY_E_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/EItems";
    private static final String LEGACY_FOOD_COMPONENTS =
            "ru/feytox/etherology/registry/item/EFoodComponents.class";
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

    private static final String ITEM_OWNER = "net/minecraft/item/Item";
    private static final String ITEM_SETTINGS_OWNER =
            "net/minecraft/item/Item$Settings";
    private static final String FOOD_COMPONENT_BUILDER_OWNER =
            "net/minecraft/item/FoodComponent$Builder";

    private static final String MODEL_ENTRY =
            "assets/etherology/models/item/forest_lantern_crumb.json";
    private static final String TEXTURE_ENTRY =
            "assets/etherology/textures/item/forest_lantern_crumb.png";
    private static final String ENGLISH_LANGUAGE_ENTRY =
            "assets/etherology/lang/en_us.json";
    private static final String RUSSIAN_LANGUAGE_ENTRY =
            "assets/etherology/lang/ru_ru.json";
    private static final String MODEL_SHA256 =
            "6ba61590386580a2f70526313d501eec44cd88ff9d86cd1d13d9092b41a42fbe";
    private static final String TEXTURE_SHA256 =
            "44f9d92ccf36c3555d21ace9eea0268e43eb4a8e95f1e81b74f22977d4928d65";

    @Test
    void everyArtifactHasOneExactDeferredFoodOwner() throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entries = jar.stream().map(JarEntry::getName).toList();
                assertEquals(1, count(entries, SHARED_FOOD_COMPONENTS), artifact.description());
                assertEquals(1, count(entries, SHARED_FOOD_ITEMS), artifact.description());
                assertFalse(entries.contains(LEGACY_FOOD_COMPONENTS), artifact.description());

                ClassReader components = classReader(jar, SHARED_FOOD_COMPONENTS);
                ClassReader items = classReader(jar, SHARED_FOOD_ITEMS);
                assertExactFoodComponentOwner(artifact.description(), components);
                assertExactFoodItemOwner(artifact.description(), items);
                assertNoLoaderSpecificReferences(artifact.description(), components);
                assertNoLoaderSpecificReferences(artifact.description(), items);
                assertOnlyExpectedRegistrationOwner(artifact, jar, entries);
            }
        }
    }

    @Test
    void canonicalCommonBytecodeEncodesTheExactVanillaFoodContract()
            throws IOException {
        Artifact common = artifact("commonJar", "common JAR", false, false, false);
        try (JarFile jar = common.open()) {
            FoodComponentSemantics component = foodComponentSemantics(
                    classReader(jar, SHARED_FOOD_COMPONENTS)
            );
            assertEquals(List.of(3), component.integerConstants());
            assertEquals(List.of(2.0f), component.floatConstants());
            assertEquals(
                    List.of(
                            "<init>()V",
                            "hunger(I)Lnet/minecraft/item/FoodComponent$Builder;",
                            "saturationModifier(F)Lnet/minecraft/item/FoodComponent$Builder;",
                            "build()Lnet/minecraft/item/FoodComponent;"
                    ),
                    component.builderInvocations()
            );
            assertEquals(1, component.foodFieldAssignments());

            FoodItemSemantics item = foodItemSemantics(classReader(jar, SHARED_FOOD_ITEMS));
            assertEquals(
                    List.of(ITEM_OWNER, ITEM_SETTINGS_OWNER),
                    item.instantiatedTypes()
            );
            assertEquals(
                    List.of(
                            "<init>()V",
                            "food(Lnet/minecraft/item/FoodComponent;)"
                                    + "Lnet/minecraft/item/Item$Settings;"
                    ),
                    item.settingsInvocations()
            );
            assertEquals(
                    List.of("<init>(Lnet/minecraft/item/Item$Settings;)V"),
                    item.itemInvocations()
            );
            assertEquals(1, item.foodComponentReads());
        }
    }

    @Test
    void fabricLegacyAliasUsesTheSharedSupplierWithoutOwningTheFoodId()
            throws IOException {
        for (Artifact artifact : artifacts()) {
            if (!artifact.fabricApplication()) continue;
            try (JarFile jar = artifact.open()) {
                LegacyAlias alias = legacyAlias(classReader(jar, LEGACY_E_ITEMS));
                assertEquals(1, alias.legacyFields(), artifact.description());
                assertEquals(0, alias.foodIdConstants(), artifact.description());
                assertEquals(1, alias.sharedSupplierReads(), artifact.description());
                assertEquals(1, alias.supplierGetInvocations(), artifact.description());
                assertEquals(1, alias.legacyFieldAssignments(), artifact.description());
                assertTrue(
                        alias.sharedSupplierReadIndex() < alias.supplierGetIndex(),
                        artifact.description()
                );
                assertTrue(
                        alias.supplierGetIndex() < alias.legacyFieldAssignmentIndex(),
                        artifact.description()
                );
            }
        }
    }

    @Test
    void eachLoaderAttachesTheSharedFoodCatalogExactlyOnce() throws IOException {
        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                String initializer = artifact.fabricApplication()
                        ? FABRIC_INITIALIZER
                        : COMMON_BOOTSTRAP;
                List<String> invocations = methodInvocations(
                        classReader(jar, initializer),
                        "initialize"
                );
                String foodRegistration = SHARED_FOOD_ITEMS_OWNER + "#register()V";
                String materialRegistration =
                        "ru/feytox/etherology/registry/item/SharedMaterialItems#register()V";
                assertInvocationOnce(
                        artifact.description(),
                        invocations,
                        materialRegistration
                );
                assertInvocationOnce(
                        artifact.description(),
                        invocations,
                        foodRegistration
                );
                assertTrue(
                        invocations.indexOf(materialRegistration)
                                < invocations.indexOf(foodRegistration),
                        artifact.description()
                );

                if (artifact.fabricApplication()) {
                    String legacyRegistration = LEGACY_E_ITEMS_OWNER + "#registerItems()V";
                    assertInvocationOnce(
                            artifact.description(),
                            invocations,
                            legacyRegistration
                    );
                    assertTrue(
                            invocations.indexOf(foodRegistration)
                                    < invocations.indexOf(legacyRegistration),
                            artifact.description()
                    );
                    assertEntrypointDelegatesOnce(
                            artifact,
                            jar,
                            FABRIC_ENTRYPOINT,
                            "onInitialize",
                            FABRIC_INITIALIZER_OWNER + "#initialize()V",
                            foodRegistration
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
                            invocations.indexOf(foodRegistration)
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
                                foodRegistration
                        );
                    }
                }
            }
        }
    }

    @Test
    void packagedAssetsAndBothNamesAreExact() throws IOException {
        Path repositoryRoot = requiredPath("etherology.foodItems.repositoryRoot");
        Path model = repositoryRoot.resolve(
                "src/main/generated/assets/etherology/models/item/forest_lantern_crumb.json"
        );
        Path texture = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/textures/item/forest_lantern_crumb.png"
        );
        Path englishLanguage = repositoryRoot.resolve(
                "src/client/resources/assets/etherology/lang/en_us.json"
        );
        Path russianLanguage = repositoryRoot.resolve(
                "src/main/generated/assets/etherology/lang/ru_ru.json"
        );
        requireRegularFile(model);
        requireRegularFile(texture);
        requireRegularFile(englishLanguage);
        requireRegularFile(russianLanguage);
        assertEquals(MODEL_SHA256, sha256(Files.readAllBytes(model)));
        assertEquals(TEXTURE_SHA256, sha256(Files.readAllBytes(texture)));
        assertLanguageName(englishLanguage, "Mushroom Crumb");
        assertLanguageName(russianLanguage, "Грибной мякиш");

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                assertResource(artifact, jar, MODEL_ENTRY, model, MODEL_SHA256);
                assertResource(artifact, jar, TEXTURE_ENTRY, texture, TEXTURE_SHA256);
                assertPackagedLanguage(
                        artifact,
                        jar,
                        ENGLISH_LANGUAGE_ENTRY,
                        "Mushroom Crumb"
                );
                assertPackagedLanguage(
                        artifact,
                        jar,
                        RUSSIAN_LANGUAGE_ENTRY,
                        "Грибной мякиш"
                );
            }
        }
    }

    private static void assertExactFoodComponentOwner(
            String description,
            ClassReader reader
    ) {
        ClassShape shape = classShape(reader);
        assertTrue((shape.access() & Opcodes.ACC_PUBLIC) != 0, description);
        assertTrue((shape.access() & Opcodes.ACC_FINAL) != 0, description);
        assertEquals(List.of(FOOD_FIELD), new ArrayList<>(shape.fields().keySet()), description);
        assertEquals(1, shape.privateConstructors(), description);
    }

    private static void assertExactFoodItemOwner(String description, ClassReader reader) {
        ClassShape shape = classShape(reader);
        assertTrue((shape.access() & Opcodes.ACC_PUBLIC) != 0, description);
        assertTrue((shape.access() & Opcodes.ACC_FINAL) != 0, description);
        assertEquals(
                List.of("ITEMS", FOOD_FIELD),
                new ArrayList<>(shape.fields().keySet()),
                description
        );
        assertEquals(
                SHARED_DEFERRED_REGISTER_DESCRIPTOR,
                shape.fields().get("ITEMS"),
                description
        );
        assertEquals(
                REGISTRY_SUPPLIER_DESCRIPTOR,
                shape.fields().get(FOOD_FIELD),
                description
        );
        assertEquals(1, shape.privateConstructors(), description);

        FoodOwnerTrace trace = foodOwnerTrace(reader);
        assertEquals(List.of(FOOD_ID), trace.registrationIds(), description);
        assertEquals(1, trace.deferredRegisterCalls(), description);
        assertEquals(1, trace.attachCalls(), description);
        assertEquals(0, trace.supplierGetCalls(), description);
        assertEquals(Set.of(), trace.directRegistryOwners(), description);
    }

    private static ClassShape classShape(ClassReader reader) {
        int[] access = {-1};
        Map<String, String> fields = new LinkedHashMap<>();
        int[] privateConstructors = {0};
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
                access[0] = classAccess;
            }

            @Override
            public FieldVisitor visitField(
                    int fieldAccess,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.put(name, descriptor);
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
                    privateConstructors[0]++;
                }
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassShape(access[0], fields, privateConstructors[0]);
    }

    private static FoodOwnerTrace foodOwnerTrace(ClassReader reader) {
        List<String> registrationIds = new ArrayList<>();
        int[] deferredRegisterCalls = {0};
        int[] attachCalls = {0};
        int[] supplierGetCalls = {0};
        Set<String> directRegistryOwners = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String methodName,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (methodName.equals("<clinit>") && FOOD_ID.equals(value)) {
                            registrationIds.add(FOOD_ID);
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
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && invokedName.equals("register")) {
                            deferredRegisterCalls[0]++;
                        }
                        if (owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                && invokedName.equals("attach")) {
                            attachCalls[0]++;
                        }
                        if (owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && invokedName.equals("get")) {
                            supplierGetCalls[0]++;
                        }
                        if (isDirectRegistryOwner(owner)) {
                            directRegistryOwners.add(owner + "#" + invokedName);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new FoodOwnerTrace(
                registrationIds,
                deferredRegisterCalls[0],
                attachCalls[0],
                supplierGetCalls[0],
                directRegistryOwners
        );
    }

    private static void assertOnlyExpectedRegistrationOwner(
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
            if (!ownership.foodIdPresent()) continue;
            String owner = entry.substring(0, entry.length() - ".class".length());
            if (ownership.sharedDeferredRegistration()) deferredOwners.add(owner);
            if (ownership.directRegistryRegistration()) directOwners.add(owner);
        }
        assertEquals(
                Set.of(SHARED_FOOD_ITEMS_OWNER),
                deferredOwners,
                artifact.description()
        );
        assertEquals(Set.of(), directOwners, artifact.description());
    }

    private static RegistrationOwnership registrationOwnership(ClassReader reader) {
        boolean[] foodIdPresent = {false};
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
                        if (FOOD_ID.equals(value)) foodIdPresent[0] = true;
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
                foodIdPresent[0],
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

    private static FoodComponentSemantics foodComponentSemantics(ClassReader reader) {
        List<Integer> integerConstants = new ArrayList<>();
        List<Float> floatConstants = new ArrayList<>();
        List<String> builderInvocations = new ArrayList<>();
        int[] foodFieldAssignments = {0};
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
                    public void visitInsn(int opcode) {
                        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                            integerConstants.add(opcode - Opcodes.ICONST_0);
                        } else if (opcode == Opcodes.FCONST_0) {
                            floatConstants.add(0.0f);
                        } else if (opcode == Opcodes.FCONST_1) {
                            floatConstants.add(1.0f);
                        } else if (opcode == Opcodes.FCONST_2) {
                            floatConstants.add(2.0f);
                        }
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                            integerConstants.add(operand);
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Integer integer) {
                            integerConstants.add(integer);
                        } else if (value instanceof Float floating) {
                            floatConstants.add(floating);
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
                        if (owner.equals(FOOD_COMPONENT_BUILDER_OWNER)) {
                            builderInvocations.add(invokedName + invokedDescriptor);
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(SHARED_FOOD_COMPONENTS_OWNER)
                                && name.equals(FOOD_FIELD)) {
                            foodFieldAssignments[0]++;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new FoodComponentSemantics(
                integerConstants,
                floatConstants,
                builderInvocations,
                foodFieldAssignments[0]
        );
    }

    private static FoodItemSemantics foodItemSemantics(ClassReader reader) {
        List<String> instantiatedTypes = new ArrayList<>();
        List<String> settingsInvocations = new ArrayList<>();
        List<String> itemInvocations = new ArrayList<>();
        int[] foodComponentReads = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.startsWith("lambda$")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW) instantiatedTypes.add(type);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals(ITEM_SETTINGS_OWNER)) {
                            settingsInvocations.add(invokedName + invokedDescriptor);
                        } else if (owner.equals(ITEM_OWNER)) {
                            itemInvocations.add(invokedName + invokedDescriptor);
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
                                && owner.equals(SHARED_FOOD_COMPONENTS_OWNER)
                                && name.equals(FOOD_FIELD)) {
                            foodComponentReads[0]++;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new FoodItemSemantics(
                instantiatedTypes,
                settingsInvocations,
                itemInvocations,
                foodComponentReads[0]
        );
    }

    private static LegacyAlias legacyAlias(ClassReader reader) {
        int[] legacyFields = {0};
        int[] foodIdConstants = {0};
        int[] sharedSupplierReads = {0};
        int[] supplierGetInvocations = {0};
        int[] legacyFieldAssignments = {0};
        int[] instructionIndex = {0};
        int[] sharedSupplierReadIndex = {-1};
        int[] supplierGetIndex = {-1};
        int[] legacyFieldAssignmentIndex = {-1};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals(FOOD_FIELD)) legacyFields[0]++;
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
                    private boolean recordingFoodAlias;

                    @Override
                    public void visitLdcInsn(Object value) {
                        instructionIndex[0]++;
                        if (FOOD_ID.equals(value)) foodIdConstants[0]++;
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
                                && owner.equals(SHARED_FOOD_ITEMS_OWNER)
                                && name.equals(FOOD_FIELD)) {
                            recordingFoodAlias = true;
                            sharedSupplierReads[0]++;
                            sharedSupplierReadIndex[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(LEGACY_E_ITEMS_OWNER)
                                && name.equals(FOOD_FIELD)) {
                            legacyFieldAssignments[0]++;
                            legacyFieldAssignmentIndex[0] = instructionIndex[0];
                            recordingFoodAlias = false;
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
                        if (recordingFoodAlias
                                && owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            supplierGetInvocations[0]++;
                            supplierGetIndex[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new LegacyAlias(
                legacyFields[0],
                foodIdConstants[0],
                sharedSupplierReads[0],
                supplierGetInvocations[0],
                legacyFieldAssignments[0],
                sharedSupplierReadIndex[0],
                supplierGetIndex[0],
                legacyFieldAssignmentIndex[0]
        );
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
                checkDescriptor(signature);
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
                checkDescriptor(descriptor);
                checkDescriptor(signature);
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
                checkDescriptor(descriptor);
                checkDescriptor(signature);
                if (exceptions != null) {
                    for (String exception : exceptions) check(exception);
                }
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
                        checkDescriptor(descriptor);
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
                        checkDescriptor(descriptor);
                    }
                };
            }

            private void check(String owner) {
                if (owner != null && (owner.startsWith("net/fabricmc/")
                        || owner.startsWith("net/minecraftforge/")
                        || owner.startsWith("ru/feytox/etherology/client/"))) {
                    forbidden.add(owner);
                }
            }

            private void checkDescriptor(String descriptor) {
                if (descriptor != null && (descriptor.contains("Lnet/fabricmc/")
                        || descriptor.contains("Lnet/minecraftforge/")
                        || descriptor.contains("Lru/feytox/etherology/client/"))) {
                    forbidden.add(descriptor);
                }
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(Set.of(), forbidden, description);
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
        List<String> invocations = methodInvocations(classReader(jar, entrypoint), methodName);
        assertInvocationOnce(artifact.description(), invocations, delegateInvocation);
        assertEquals(
                0,
                invocations.stream().filter(forbiddenDirectInvocation::equals).count(),
                artifact.description() + ":" + forbiddenDirectInvocation
        );
    }

    private static void assertLanguageName(Path languageFile, String expectedName)
            throws IOException {
        JsonObject language = parseObject(Files.readString(languageFile));
        assertEquals(
                expectedName,
                language.get("item.etherology." + FOOD_ID).getAsString(),
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
                language.get("item.etherology." + FOOD_ID).getAsString(),
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
        if (!artifact.includesAssets()) {
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

    private static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        assertTrue(element.isJsonObject());
        return element.getAsJsonObject();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static long count(List<String> entries, String expectedEntry) {
        return entries.stream().filter(expectedEntry::equals).count();
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
        Path path = requiredPath("etherology.foodItems." + suffix);
        requireRegularFile(path);
        return new Artifact(
                path,
                description,
                includesAssets,
                fabricApplication,
                forgeApplication
        );
    }

    private record ClassShape(
            int access,
            Map<String, String> fields,
            int privateConstructors
    ) {
    }

    private record FoodOwnerTrace(
            List<String> registrationIds,
            int deferredRegisterCalls,
            int attachCalls,
            int supplierGetCalls,
            Set<String> directRegistryOwners
    ) {
    }

    private record FoodComponentSemantics(
            List<Integer> integerConstants,
            List<Float> floatConstants,
            List<String> builderInvocations,
            int foodFieldAssignments
    ) {
    }

    private record RegistrationOwnership(
            boolean foodIdPresent,
            boolean sharedDeferredRegistration,
            boolean directRegistryRegistration
    ) {
    }

    private record FoodItemSemantics(
            List<String> instantiatedTypes,
            List<String> settingsInvocations,
            List<String> itemInvocations,
            int foodComponentReads
    ) {
    }

    private record LegacyAlias(
            int legacyFields,
            int foodIdConstants,
            int sharedSupplierReads,
            int supplierGetInvocations,
            int legacyFieldAssignments,
            int sharedSupplierReadIndex,
            int supplierGetIndex,
            int legacyFieldAssignmentIndex
    ) {
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
