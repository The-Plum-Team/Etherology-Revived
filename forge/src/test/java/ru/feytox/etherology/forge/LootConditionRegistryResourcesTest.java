package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LootConditionRegistryResourcesTest {

    private static final String SHARED_LOOT_CONDITIONS =
            "ru/feytox/etherology/registry/misc/SharedLootConditions.class";
    private static final String SHARED_LOOT_CONDITIONS_OWNER =
            "ru/feytox/etherology/registry/misc/SharedLootConditions";
    private static final String LEGACY_LOOT_CONDITIONS =
            "ru/feytox/etherology/registry/misc/LootConditions.class";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String RANDOM_CHANCE_CONDITION =
            "ru/feytox/etherology/util/misc/RandomChanceWithFortuneCondition.class";
    private static final String RANDOM_CHANCE_SERIALIZER =
            "ru/feytox/etherology/util/misc/RandomChanceWithFortuneConditionSerializer.class";
    private static final String SHARED_DEFERRED_REGISTER_OWNER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String DIRECT_DEFERRED_REGISTER_OWNER =
            "dev/architectury/registry/registries/DeferredRegister";
    private static final Set<String> DIRECT_VANILLA_REGISTRY_OWNERS = Set.of(
            "net/minecraft/registry/Registry",
            "net/minecraft/class_2378",
            "net/minecraft/core/Registry"
    );
    private static final String CONDITION_ID = "random_chance_with_fortune";
    private static final String ATTRAHITE_LOOT_TABLE =
            "data/etherology/loot_tables/blocks/attrahite.json";

    @Test
    void everyProductionBoundaryHasOneExactSharedOwner() throws IOException {
        for (Artifact artifact : artifacts()) {
            assertArtifactOwnership(artifact);
        }
    }

    @Test
    void onlyFabricPackagesTheUnportedAttrahiteConsumer() throws IOException {
        Path canonicalLootTable = requiredPath(
                "etherology.lootConditions.attrahiteLootTable"
        );
        assertTrue(Files.isRegularFile(canonicalLootTable));
        assertFalse(Files.isSymbolicLink(canonicalLootTable));
        byte[] exactLootTable = Files.readAllBytes(canonicalLootTable);
        String lootTableText = new String(exactLootTable, StandardCharsets.UTF_8);
        assertEquals(1, count(lootTableText, "etherology:" + CONDITION_ID));
        assertEquals(1, count(lootTableText, "\"chance\": 0.05"));
        assertEquals(1, count(lootTableText, "\"fortune_multiplier\": 0.05"));

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entryNames = jar.stream().map(JarEntry::getName).toList();
                int resourceCount = (int) entryNames.stream()
                        .filter(ATTRAHITE_LOOT_TABLE::equals)
                        .count();
                int expectedCount = artifact.includesCanonicalFabricResources() ? 1 : 0;
                assertEquals(expectedCount, resourceCount, artifact.description());
                if (expectedCount == 1) {
                    try (InputStream resource = jar.getInputStream(
                            jar.getJarEntry(ATTRAHITE_LOOT_TABLE)
                    )) {
                        assertArrayEquals(exactLootTable, resource.readAllBytes(),
                                artifact.description());
                    }
                }
            }
        }
    }

    @Test
    void forgeClasspathDoesNotExposeTheLegacyOwner() {
        assertNull(LootConditionRegistryResourcesTest.class
                .getClassLoader()
                .getResource(LEGACY_LOOT_CONDITIONS));
        assertNotNull(LootConditionRegistryResourcesTest.class
                .getClassLoader()
                .getResource(SHARED_LOOT_CONDITIONS));
    }

    private static void assertArtifactOwnership(Artifact artifact) throws IOException {
        try (JarFile jar = artifact.open()) {
            List<String> entryNames = jar.stream().map(JarEntry::getName).toList();
            assertEquals(1, count(entryNames, SHARED_LOOT_CONDITIONS), artifact.description());
            assertEquals(1, count(entryNames, RANDOM_CHANCE_CONDITION), artifact.description());
            assertEquals(1, count(entryNames, RANDOM_CHANCE_SERIALIZER), artifact.description());
            assertEquals(0, count(entryNames, LEGACY_LOOT_CONDITIONS), artifact.description());

            ClassReader sharedOwner = classReader(jar, SHARED_LOOT_CONDITIONS);
            String lootConditionTypeOwner = assertExactSharedOwner(
                    artifact.description(),
                    sharedOwner
            );

            Set<String> registrationOwners = new LinkedHashSet<>();
            Set<String> constructionOwners = new LinkedHashSet<>();
            Set<String> supplierFieldMethods = new LinkedHashSet<>();
            AtomicInteger constructionCount = new AtomicInteger();
            AtomicInteger supplierResolutionCount = new AtomicInteger();

            for (String classEntry : entryNames) {
                if (!classEntry.endsWith(".class")) {
                    continue;
                }
                ClassReader reader = classReader(jar, classEntry);
                ClassUsage usage = classUsage(reader, lootConditionTypeOwner);
                if (usage.stringConstants().contains(CONDITION_ID)
                        && usage.registrationInvocationCount() > 0) {
                    registrationOwners.add(classEntry);
                }
                if (usage.lootConditionTypeConstructionCount() > 0) {
                    constructionOwners.add(classEntry);
                    constructionCount.addAndGet(
                            usage.lootConditionTypeConstructionCount()
                    );
                }
                for (Map.Entry<String, Integer> method :
                        usage.sharedSupplierReferencesByMethod().entrySet()) {
                    if (method.getValue() > 0) {
                        supplierFieldMethods.add(classEntry + "#" + method.getKey());
                    }
                }
                supplierResolutionCount.addAndGet(usage.sharedSupplierResolutionCount());
            }

            assertEquals(Set.of(SHARED_LOOT_CONDITIONS), registrationOwners,
                    artifact.description());
            assertEquals(Set.of(SHARED_LOOT_CONDITIONS), constructionOwners,
                    artifact.description());
            assertEquals(1, constructionCount.get(), artifact.description());
            assertEquals(
                    Set.of(RANDOM_CHANCE_CONDITION + "#"
                            + lootConditionTypeMethodName(lootConditionTypeOwner)
                            + "()" + lootTypeDescriptor(lootConditionTypeOwner)),
                    supplierFieldMethods,
                    artifact.description()
            );
            assertEquals(1, supplierResolutionCount.get(), artifact.description());

            assertEquals(
                    1,
                    invocationCount(
                            classReader(jar, COMMON_BOOTSTRAP),
                            "initialize",
                            SHARED_LOOT_CONDITIONS_OWNER,
                            "register"
                    ),
                    artifact.description()
            );
            if (artifact.fabricApplication()) {
                assertFabricInitializerOrder(artifact.description(), jar);
            }
        }
    }

    private static String assertExactSharedOwner(String description, ClassReader reader) {
        AtomicInteger classAccess = new AtomicInteger();
        Map<String, Integer> fieldAccess = new LinkedHashMap<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        Map<String, String> fieldSignatures = new LinkedHashMap<>();
        Map<String, Integer> methodAccess = new LinkedHashMap<>();
        List<String> publicMethods = new ArrayList<>();
        Map<String, List<String>> stringsByMethod = new LinkedHashMap<>();
        Map<String, List<String>> invocationsByMethod = new LinkedHashMap<>();
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
                classAccess.set(access);
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
                fieldSignatures.put(name, signature);
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
                String methodKey = name + descriptor;
                methodAccess.put(methodKey, access);
                if ((access & Opcodes.ACC_PUBLIC) != 0) {
                    publicMethods.add(methodKey);
                }
                List<String> strings = new ArrayList<>();
                List<String> invocations = new ArrayList<>();
                stringsByMethod.put(methodKey, strings);
                invocationsByMethod.put(methodKey, invocations);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            strings.add(stringValue);
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
                        invocations.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        String lootConditionTypeOwner = registeredTypeOwner(reader);
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                classAccess.get(),
                description
        );
        assertEquals(Set.of("LOOT_CONDITIONS", "RANDOM_CHANCE_WITH_FORTUNE"),
                fieldDescriptors.keySet(), description);
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("LOOT_CONDITIONS"),
                description
        );
        assertEquals(
                "L" + SHARED_DEFERRED_REGISTER_OWNER + ";",
                fieldDescriptors.get("LOOT_CONDITIONS"),
                description
        );
        assertEquals(
                "L" + SHARED_DEFERRED_REGISTER_OWNER + "<L"
                        + lootConditionTypeOwner + ";>;",
                fieldSignatures.get("LOOT_CONDITIONS"),
                description
        );
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("RANDOM_CHANCE_WITH_FORTUNE"),
                description
        );
        assertEquals(
                "L" + REGISTRY_SUPPLIER_OWNER + ";",
                fieldDescriptors.get("RANDOM_CHANCE_WITH_FORTUNE"),
                description
        );
        assertEquals(
                "L" + REGISTRY_SUPPLIER_OWNER + "<L" + lootConditionTypeOwner + ";>;",
                fieldSignatures.get("RANDOM_CHANCE_WITH_FORTUNE"),
                description
        );
        assertEquals(Opcodes.ACC_PRIVATE, methodAccess.get("<init>()V"), description);
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                methodAccess.get("register()V"),
                description
        );
        assertEquals(List.of("register()V"), publicMethods, description);
        assertEquals(List.of(CONDITION_ID), stringsByMethod.get("<clinit>()V"), description);

        List<String> allInvocations = invocationsByMethod.values().stream()
                .flatMap(List::stream)
                .toList();
        assertEquals(
                1,
                allInvocations.stream().filter(invocation -> invocation.startsWith(
                        SHARED_DEFERRED_REGISTER_OWNER + "#create("
                )).count(),
                description
        );
        assertEquals(
                1,
                allInvocations.stream().filter(invocation -> invocation.startsWith(
                        SHARED_DEFERRED_REGISTER_OWNER + "#register("
                )).count(),
                description
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER_OWNER + "#attach()V"),
                invocationsByMethod.get("register()V"),
                description
        );
        assertEquals(
                0,
                allInvocations.stream().filter(invocation -> invocation.startsWith(
                        REGISTRY_SUPPLIER_OWNER + "#get("
                )).count(),
                description
        );
        assertEquals(
                0,
                allInvocations.stream().filter(invocation -> invocation.startsWith(
                        DIRECT_DEFERRED_REGISTER_OWNER + "#register("
                )).count(),
                description
        );
        assertEquals(
                0,
                allInvocations.stream().filter(invocation ->
                        DIRECT_VANILLA_REGISTRY_OWNERS.stream().anyMatch(owner ->
                                invocation.startsWith(owner + "#register(")
                        )
                ).count(),
                description
        );
        return lootConditionTypeOwner;
    }

    private static String registeredTypeOwner(ClassReader sharedOwner) {
        AtomicReference<String> signature = new AtomicReference<>();
        sharedOwner.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String genericSignature,
                    Object value
            ) {
                if (name.equals("RANDOM_CHANCE_WITH_FORTUNE")) {
                    signature.set(genericSignature);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        String genericSignature = signature.get();
        assertNotNull(genericSignature, "Missing loot-condition supplier signature");
        int typeStart = genericSignature.indexOf("<L");
        assertTrue(typeStart >= 0, "Malformed loot-condition supplier signature");
        typeStart += 2;
        int typeEnd = genericSignature.indexOf(';', typeStart);
        assertTrue(typeEnd > typeStart, "Malformed loot-condition supplier type");
        return genericSignature.substring(typeStart, typeEnd);
    }

    private static ClassUsage classUsage(ClassReader reader, String lootConditionTypeOwner) {
        Set<String> stringConstants = new LinkedHashSet<>();
        Map<String, Integer> sharedSupplierReferences = new LinkedHashMap<>();
        AtomicInteger registrationInvocations = new AtomicInteger();
        AtomicInteger lootConditionTypeConstructions = new AtomicInteger();
        AtomicInteger sharedSupplierResolutions = new AtomicInteger();

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                String methodKey = name + descriptor;
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean referencesSharedSupplier;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            stringConstants.add(stringValue);
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
                                && owner.equals(SHARED_LOOT_CONDITIONS_OWNER)
                                && name.equals("RANDOM_CHANCE_WITH_FORTUNE")) {
                            referencesSharedSupplier = true;
                            sharedSupplierReferences.merge(methodKey, 1, Integer::sum);
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
                        if (name.equals("register") && (
                                owner.equals(SHARED_DEFERRED_REGISTER_OWNER)
                                        || owner.equals(DIRECT_DEFERRED_REGISTER_OWNER)
                                        || DIRECT_VANILLA_REGISTRY_OWNERS.contains(owner)
                        )) {
                            registrationInvocations.incrementAndGet();
                        }
                        if (opcode == Opcodes.INVOKESPECIAL
                                && owner.equals(lootConditionTypeOwner)
                                && name.equals("<init>")) {
                            lootConditionTypeConstructions.incrementAndGet();
                        }
                        if (referencesSharedSupplier
                                && owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            sharedSupplierResolutions.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return new ClassUsage(
                stringConstants,
                registrationInvocations.get(),
                lootConditionTypeConstructions.get(),
                sharedSupplierReferences,
                sharedSupplierResolutions.get()
        );
    }

    private static void assertFabricInitializerOrder(
            String description,
            JarFile artifact
    ) throws IOException {
        List<String> invocations = methodInvocations(
                classReader(artifact, FABRIC_INITIALIZER),
                "initialize"
        );
        int gameEventIndex = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedGameEvents#register"
        );
        int lootConditionIndex = invocations.indexOf(
                SHARED_LOOT_CONDITIONS_OWNER + "#register"
        );
        int frequencyHookIndex = invocations.indexOf(
                "ru/feytox/etherology/FabricGameEventHooks#registerSculkSensorFrequency"
        );
        assertTrue(gameEventIndex >= 0, description);
        assertEquals(gameEventIndex + 1, lootConditionIndex, description);
        assertEquals(lootConditionIndex + 1, frequencyHookIndex, description);
        assertEquals(1, count(invocations, SHARED_LOOT_CONDITIONS_OWNER + "#register"),
                description);
    }

    private static List<String> methodInvocations(
            ClassReader reader,
            String expectedMethodName
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
                if (!name.equals(expectedMethodName)) {
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
                        invocations.add(owner + "#" + name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static int invocationCount(
            ClassReader reader,
            String expectedMethodName,
            String expectedOwner,
            String expectedInvocationName
    ) {
        AtomicInteger count = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(expectedMethodName)) {
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
                        if (owner.equals(expectedOwner) && name.equals(expectedInvocationName)) {
                            count.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count.get();
    }

    private static ClassReader classReader(JarFile artifact, String classEntry)
            throws IOException {
        JarEntry entry = artifact.getJarEntry(classEntry);
        assertNotNull(entry, "Missing packaged class " + classEntry);
        try (InputStream classStream = artifact.getInputStream(entry)) {
            return new ClassReader(classStream);
        }
    }

    private static List<Artifact> artifacts() {
        return List.of(
                new Artifact(
                        "Common JAR",
                        requiredPath("etherology.lootConditions.commonJar"),
                        false,
                        false
                ),
                new Artifact(
                        "Fabric-transformed common JAR",
                        requiredPath(
                                "etherology.lootConditions.fabricTransformedCommonJar"
                        ),
                        false,
                        false
                ),
                new Artifact(
                        "Forge-transformed common JAR",
                        requiredPath(
                                "etherology.lootConditions.forgeTransformedCommonJar"
                        ),
                        false,
                        false
                ),
                new Artifact(
                        "Fabric development JAR",
                        requiredPath("etherology.lootConditions.fabricDevelopmentJar"),
                        true,
                        true
                ),
                new Artifact(
                        "Fabric remapped production JAR",
                        requiredPath("etherology.lootConditions.fabricProductionJar"),
                        true,
                        true
                ),
                new Artifact(
                        "Forge shadow JAR",
                        requiredPath("etherology.lootConditions.forgeShadowJar"),
                        false,
                        false
                )
        );
    }

    private static Path requiredPath(String propertyName) {
        String propertyValue = System.getProperty(propertyName);
        assertNotNull(propertyValue, "Missing Gradle test property " + propertyName);
        return Path.of(propertyValue);
    }

    private static String lootTypeDescriptor(String internalName) {
        return "L" + internalName + ";";
    }

    private static String lootConditionTypeMethodName(String internalName) {
        if (internalName.equals("net/minecraft/class_5342")) {
            return "method_29325";
        }
        return "getType";
    }

    private static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }

    private static int count(String value, String needle) {
        int occurrences = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            occurrences++;
            offset += needle.length();
        }
        return occurrences;
    }

    private record Artifact(
            String description,
            Path path,
            boolean fabricApplication,
            boolean includesCanonicalFabricResources
    ) {

        private JarFile open() throws IOException {
            assertTrue(Files.isRegularFile(path), description + " is missing");
            assertFalse(Files.isSymbolicLink(path), description + " is linked");
            return new JarFile(path.toFile());
        }
    }

    private record ClassUsage(
            Set<String> stringConstants,
            int registrationInvocationCount,
            int lootConditionTypeConstructionCount,
            Map<String, Integer> sharedSupplierReferencesByMethod,
            int sharedSupplierResolutionCount
    ) {
    }
}
