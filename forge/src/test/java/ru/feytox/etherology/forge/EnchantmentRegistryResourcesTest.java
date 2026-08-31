package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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

final class EnchantmentRegistryResourcesTest {

    private static final String SHARED_ENCHANTMENTS =
            "ru/feytox/etherology/registry/misc/SharedEnchantments.class";
    private static final String SHARED_ENCHANTMENTS_OWNER =
            "ru/feytox/etherology/registry/misc/SharedEnchantments";
    private static final String PEAL_ENCHANTMENT =
            "ru/feytox/etherology/registry/misc/PealEnchantment.class";
    private static final String PEAL_ENCHANTMENT_OWNER =
            "ru/feytox/etherology/registry/misc/PealEnchantment";
    private static final String REFLECTION_ENCHANTMENT =
            "ru/feytox/etherology/registry/misc/ReflectionEnchantment.class";
    private static final String REFLECTION_ENCHANTMENT_OWNER =
            "ru/feytox/etherology/registry/misc/ReflectionEnchantment";
    private static final String FABRIC_ENCHANTMENT_POLICY =
            "ru/feytox/etherology/registry/misc/EtherEnchantments.class";
    private static final String FABRIC_ENCHANTMENT_POLICY_OWNER =
            "ru/feytox/etherology/registry/misc/EtherEnchantments";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
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
    private static final List<String> ENCHANTMENT_IDS = List.of(
            "peal",
            "reflection"
    );
    private static final String NON_TREASURE_TAG =
            "data/minecraft/tags/enchantment/non_treasure.json";
    private static final byte[] EXACT_NON_TREASURE_TAG = (
            "{\n"
                    + "  \"replace\": false,\n"
                    + "  \"values\": [\n"
                    + "    \"etherology:peal\",\n"
                    + "    \"etherology:reflection\"\n"
                    + "  ]\n"
                    + "}"
    ).getBytes(StandardCharsets.UTF_8);

    @Test
    void everyProductionBoundaryHasOneExactDeferredOwner() throws IOException {
        for (Artifact artifact : artifacts()) {
            assertArtifactOwnership(artifact);
        }
    }

    @Test
    void fabricAndForgePackageTheExactCanonicalNonTreasureTag() throws IOException {
        Path canonicalTag = requiredPath("etherology.enchantments.nonTreasureTag");
        assertTrue(Files.isRegularFile(canonicalTag));
        assertFalse(Files.isSymbolicLink(canonicalTag));
        assertArrayEquals(EXACT_NON_TREASURE_TAG, Files.readAllBytes(canonicalTag));

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entryNames = jar.stream().map(JarEntry::getName).toList();
                int expectedCount = artifact.includesCanonicalTag() ? 1 : 0;
                assertEquals(
                        expectedCount,
                        count(entryNames, NON_TREASURE_TAG),
                        artifact.description()
                );
                if (expectedCount == 1) {
                    try (InputStream resource = jar.getInputStream(
                            jar.getJarEntry(NON_TREASURE_TAG)
                    )) {
                        assertArrayEquals(
                                EXACT_NON_TREASURE_TAG,
                                resource.readAllBytes(),
                                artifact.description()
                        );
                    }
                }
            }
        }
    }

    @Test
    void legacyConcreteSourcesAreRemovedFromTheCanonicalFabricTree() {
        Path repositoryRoot = requiredPath("etherology.enchantments.repositoryRoot");
        for (String legacyPath : List.of(
                "src/main/java/ru/feytox/etherology/registry/misc/PealEnchantment.java",
                "src/main/java/ru/feytox/etherology/registry/misc/ReflectionEnchantment.java"
        )) {
            assertFalse(
                    Files.exists(repositoryRoot.resolve(legacyPath), LinkOption.NOFOLLOW_LINKS),
                    legacyPath
            );
        }
    }

    @Test
    void forgeClasspathExposesSharedEnchantmentsWithoutTheFabricPolicyUtility() {
        assertNotNull(classLoaderResource(SHARED_ENCHANTMENTS));
        assertNotNull(classLoaderResource(PEAL_ENCHANTMENT));
        assertNotNull(classLoaderResource(REFLECTION_ENCHANTMENT));
        assertNull(classLoaderResource(FABRIC_ENCHANTMENT_POLICY));
    }

    private static void assertArtifactOwnership(Artifact artifact) throws IOException {
        try (JarFile jar = artifact.open()) {
            List<String> entryNames = jar.stream().map(JarEntry::getName).toList();
            assertEquals(1, count(entryNames, SHARED_ENCHANTMENTS), artifact.description());
            assertEquals(1, count(entryNames, PEAL_ENCHANTMENT), artifact.description());
            assertEquals(1, count(entryNames, REFLECTION_ENCHANTMENT), artifact.description());
            assertEquals(
                    artifact.fabricApplication() ? 1 : 0,
                    count(entryNames, FABRIC_ENCHANTMENT_POLICY),
                    artifact.description()
            );

            String enchantmentTypeOwner = assertExactSharedOwner(
                    artifact.description(),
                    classReader(jar, SHARED_ENCHANTMENTS)
            );
            assertConcreteEnchantment(
                    artifact.description(),
                    classReader(jar, PEAL_ENCHANTMENT),
                    enchantmentTypeOwner
            );
            assertConcreteEnchantment(
                    artifact.description(),
                    classReader(jar, REFLECTION_ENCHANTMENT),
                    enchantmentTypeOwner
            );
            assertSoleRegistrationAndConstructionOwner(
                    artifact.description(),
                    jar,
                    entryNames,
                    enchantmentTypeOwner
            );
            assertCommonBootstrapOrder(artifact.description(), jar);

            if (artifact.fabricApplication()) {
                assertFabricPolicyUtility(artifact.description(), jar);
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

        String enchantmentTypeOwner = genericTypeOwner(
                fieldSignatures.get("PEAL"),
                "Missing PEAL supplier signature"
        );
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                classAccess.get(),
                description
        );
        assertEquals(
                List.of("ENCHANTMENTS", "PEAL", "REFLECTION"),
                new ArrayList<>(fieldDescriptors.keySet()),
                description
        );
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("ENCHANTMENTS"),
                description
        );
        assertEquals(
                "L" + SHARED_DEFERRED_REGISTER_OWNER + ";",
                fieldDescriptors.get("ENCHANTMENTS"),
                description
        );
        assertEquals(
                "L" + SHARED_DEFERRED_REGISTER_OWNER + "<L"
                        + enchantmentTypeOwner + ";>;",
                fieldSignatures.get("ENCHANTMENTS"),
                description
        );
        for (String supplierField : List.of("PEAL", "REFLECTION")) {
            assertEquals(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    fieldAccess.get(supplierField),
                    description + ": " + supplierField
            );
            assertEquals(
                    "L" + REGISTRY_SUPPLIER_OWNER + ";",
                    fieldDescriptors.get(supplierField),
                    description + ": " + supplierField
            );
            assertEquals(
                    "L" + REGISTRY_SUPPLIER_OWNER + "<L"
                            + enchantmentTypeOwner + ";>;",
                    fieldSignatures.get(supplierField),
                    description + ": " + supplierField
            );
        }
        assertEquals(Opcodes.ACC_PRIVATE, methodAccess.get("<init>()V"), description);
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                methodAccess.get("register()V"),
                description
        );
        assertEquals(List.of("register()V"), publicMethods, description);
        assertEquals(ENCHANTMENT_IDS, stringsByMethod.get("<clinit>()V"), description);

        List<String> allInvocations = invocationsByMethod.values().stream()
                .flatMap(List::stream)
                .toList();
        assertEquals(
                1,
                countInvocations(allInvocations, SHARED_DEFERRED_REGISTER_OWNER, "create"),
                description
        );
        assertEquals(
                ENCHANTMENT_IDS.size(),
                countInvocations(allInvocations, SHARED_DEFERRED_REGISTER_OWNER, "register"),
                description
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER_OWNER + "#attach()V"),
                invocationsByMethod.get("register()V"),
                description
        );
        assertEquals(
                0,
                countInvocations(allInvocations, REGISTRY_SUPPLIER_OWNER, "get"),
                description
        );
        assertEquals(
                0,
                countInvocations(allInvocations, DIRECT_DEFERRED_REGISTER_OWNER, "register"),
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
        return enchantmentTypeOwner;
    }

    private static void assertConcreteEnchantment(
            String description,
            ClassReader reader,
            String enchantmentTypeOwner
    ) {
        AtomicInteger classAccess = new AtomicInteger();
        AtomicReference<String> superName = new AtomicReference<>();
        Map<String, Integer> fieldAccess = new LinkedHashMap<>();
        Map<String, Integer> constructors = new LinkedHashMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String classSuperName,
                    String[] interfaces
            ) {
                classAccess.set(access);
                superName.set(classSuperName);
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
                if (name.equals("<init>")) {
                    constructors.put(descriptor, access);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, classAccess.get(), description);
        assertEquals(enchantmentTypeOwner, superName.get(), description);
        assertEquals(Map.of(), fieldAccess, description);
        assertEquals(Map.of("()V", 0), constructors, description);
    }

    private static void assertSoleRegistrationAndConstructionOwner(
            String description,
            JarFile jar,
            List<String> entryNames,
            String enchantmentTypeOwner
    ) throws IOException {
        Set<String> registrationOwners = new LinkedHashSet<>();
        Set<String> directRegistrationOwners = new LinkedHashSet<>();
        Map<String, Map<String, Integer>> constructionOwners = new LinkedHashMap<>();

        for (String classEntry : entryNames) {
            if (!classEntry.endsWith(".class")) {
                continue;
            }
            ClassUsage usage = classUsage(classReader(jar, classEntry));
            if (!usage.enchantmentIds().isEmpty()
                    && usage.sharedRegistrationCount() > 0) {
                registrationOwners.add(classEntry);
            }
            if (!usage.enchantmentIds().isEmpty()
                    && usage.directRegistrationCount() > 0) {
                directRegistrationOwners.add(classEntry);
            }
            if (!usage.constructions().isEmpty()) {
                constructionOwners.put(classEntry, usage.constructions());
            }
        }

        assertEquals(Set.of(SHARED_ENCHANTMENTS), registrationOwners, description);
        assertEquals(Set.of(), directRegistrationOwners, description);
        assertEquals(
                Map.of(
                        SHARED_ENCHANTMENTS,
                        Map.of(
                                PEAL_ENCHANTMENT_OWNER, 1,
                                REFLECTION_ENCHANTMENT_OWNER, 1
                        )
                ),
                constructionOwners,
                description
        );
        assertTrue(
                Set.of(
                        "net/minecraft/enchantment/Enchantment",
                        "net/minecraft/class_1887",
                        "net/minecraft/world/item/enchantment/Enchantment"
                ).contains(enchantmentTypeOwner),
                description + ": " + enchantmentTypeOwner
        );
    }

    private static void assertFabricPolicyUtility(String description, JarFile jar)
            throws IOException {
        ClassReader reader = classReader(jar, FABRIC_ENCHANTMENT_POLICY);
        Set<String> fieldNames = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fieldNames.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertFalse(fieldNames.contains("PEAL"), description);
        assertFalse(fieldNames.contains("REFLECTION"), description);

        ClassUsage usage = classUsage(reader);
        assertEquals(Set.of(), usage.enchantmentIds(), description);
        assertEquals(0, usage.sharedRegistrationCount(), description);
        assertEquals(0, usage.directRegistrationCount(), description);
        assertEquals(Map.of(), usage.constructions(), description);
        assertEquals(Set.of("PEAL", "REFLECTION"), usage.sharedSupplierFields(), description);
        assertEquals(0, methodCount(usage.supplierResolutions(), "<clinit>"), description);
        assertEquals(0, methodCount(usage.supplierResolutions(), "registerAll"), description);
        assertTrue(
                usage.supplierResolutions().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum() > 0,
                description
        );
    }

    private static void assertCommonBootstrapOrder(String description, JarFile jar)
            throws IOException {
        List<String> invocations = methodInvocations(
                classReader(jar, COMMON_BOOTSTRAP),
                "initialize"
        );
        String lootConditions =
                "ru/feytox/etherology/registry/misc/SharedLootConditions#register()V";
        String enchantments = SHARED_ENCHANTMENTS_OWNER + "#register()V";
        String particleTypes =
                "ru/feytox/etherology/registry/particle/SharedParticleTypes#register()V";
        String reloaders =
                "ru/feytox/etherology/registry/misc/ResourceReloaders#registerServerData()V";
        int lootConditionIndex = invocations.indexOf(lootConditions);
        int enchantmentIndex = invocations.indexOf(enchantments);
        int particleTypeIndex = invocations.indexOf(particleTypes);
        int reloaderIndex = invocations.indexOf(reloaders);

        assertTrue(lootConditionIndex >= 0, description);
        assertEquals(lootConditionIndex + 1, enchantmentIndex, description);
        assertEquals(enchantmentIndex + 1, particleTypeIndex, description);
        assertEquals(particleTypeIndex + 1, reloaderIndex, description);
        assertEquals(1, count(invocations, enchantments), description);
    }

    private static void assertFabricInitializerOrder(String description, JarFile jar)
            throws IOException {
        List<String> invocations = methodInvocations(
                classReader(jar, FABRIC_INITIALIZER),
                "initialize"
        );
        String lootConditions =
                "ru/feytox/etherology/registry/misc/SharedLootConditions#register()V";
        String enchantments = SHARED_ENCHANTMENTS_OWNER + "#register()V";
        String frequencyHook =
                "ru/feytox/etherology/FabricGameEventHooks#registerSculkSensorFrequency()V";
        String items = "ru/feytox/etherology/registry/item/EItems#registerItems()V";
        String policy = FABRIC_ENCHANTMENT_POLICY_OWNER + "#registerAll()V";

        int lootConditionIndex = invocations.indexOf(lootConditions);
        int enchantmentIndex = invocations.indexOf(enchantments);
        int frequencyIndex = invocations.indexOf(frequencyHook);
        int itemIndex = invocations.indexOf(items);
        int policyIndex = invocations.indexOf(policy);
        assertTrue(lootConditionIndex >= 0, description);
        assertEquals(lootConditionIndex + 1, enchantmentIndex, description);
        assertEquals(enchantmentIndex + 1, frequencyIndex, description);
        assertTrue(itemIndex >= 0, description);
        assertEquals(itemIndex + 1, policyIndex, description);
        assertEquals(1, count(invocations, enchantments), description);
        assertEquals(
                0,
                countInvocations(invocations, REGISTRY_SUPPLIER_OWNER, "get"),
                description
        );
    }

    private static ClassUsage classUsage(ClassReader reader) {
        Set<String> enchantmentIds = new LinkedHashSet<>();
        Map<String, Integer> constructions = new LinkedHashMap<>();
        Set<String> sharedSupplierFields = new LinkedHashSet<>();
        Map<String, Integer> supplierResolutions = new LinkedHashMap<>();
        AtomicInteger sharedRegistrations = new AtomicInteger();
        AtomicInteger directRegistrations = new AtomicInteger();

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
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue
                                && ENCHANTMENT_IDS.contains(stringValue)) {
                            enchantmentIds.add(stringValue);
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
                                && owner.equals(SHARED_ENCHANTMENTS_OWNER)
                                && (name.equals("PEAL") || name.equals("REFLECTION"))) {
                            sharedSupplierFields.add(name);
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
                            sharedRegistrations.incrementAndGet();
                        }
                        if (name.equals("register") && (
                                owner.equals(DIRECT_DEFERRED_REGISTER_OWNER)
                                        || DIRECT_VANILLA_REGISTRY_OWNERS.contains(owner)
                        )) {
                            directRegistrations.incrementAndGet();
                        }
                        if (opcode == Opcodes.INVOKESPECIAL
                                && name.equals("<init>")
                                && isConcreteEnchantment(owner)) {
                            constructions.merge(owner, 1, Integer::sum);
                        }
                        if (owner.equals(REGISTRY_SUPPLIER_OWNER) && name.equals("get")) {
                            supplierResolutions.merge(methodKey, 1, Integer::sum);
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
                                    && handle.getTag() == Opcodes.H_NEWINVOKESPECIAL
                                    && isConcreteEnchantment(handle.getOwner())) {
                                constructions.merge(handle.getOwner(), 1, Integer::sum);
                            }
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return new ClassUsage(
                enchantmentIds,
                sharedRegistrations.get(),
                directRegistrations.get(),
                constructions,
                sharedSupplierFields,
                supplierResolutions
        );
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
                        invocations.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static String genericTypeOwner(String signature, String failureMessage) {
        assertNotNull(signature, failureMessage);
        int typeStart = signature.indexOf("<L");
        assertTrue(typeStart >= 0, failureMessage);
        typeStart += 2;
        int typeEnd = signature.indexOf(';', typeStart);
        assertTrue(typeEnd > typeStart, failureMessage);
        return signature.substring(typeStart, typeEnd);
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
                        requiredPath("etherology.enchantments.commonJar"),
                        false,
                        false
                ),
                new Artifact(
                        "Fabric-transformed common JAR",
                        requiredPath(
                                "etherology.enchantments.fabricTransformedCommonJar"
                        ),
                        false,
                        false
                ),
                new Artifact(
                        "Forge-transformed common JAR",
                        requiredPath(
                                "etherology.enchantments.forgeTransformedCommonJar"
                        ),
                        false,
                        false
                ),
                new Artifact(
                        "Fabric development JAR",
                        requiredPath("etherology.enchantments.fabricDevelopmentJar"),
                        true,
                        true
                ),
                new Artifact(
                        "Fabric remapped production JAR",
                        requiredPath("etherology.enchantments.fabricProductionJar"),
                        true,
                        true
                ),
                new Artifact(
                        "Forge shadow JAR",
                        requiredPath("etherology.enchantments.forgeShadowJar"),
                        false,
                        true
                )
        );
    }

    private static Path requiredPath(String propertyName) {
        String propertyValue = System.getProperty(propertyName);
        assertNotNull(propertyValue, "Missing Gradle test property " + propertyName);
        return Path.of(propertyValue);
    }

    private static java.net.URL classLoaderResource(String entry) {
        return EnchantmentRegistryResourcesTest.class.getClassLoader().getResource(entry);
    }

    private static boolean isConcreteEnchantment(String owner) {
        return owner.equals(PEAL_ENCHANTMENT_OWNER)
                || owner.equals(REFLECTION_ENCHANTMENT_OWNER);
    }

    private static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }

    private static long countInvocations(
            List<String> invocations,
            String expectedOwner,
            String expectedMethod
    ) {
        return invocations.stream().filter(invocation -> invocation.startsWith(
                expectedOwner + "#" + expectedMethod + "("
        )).count();
    }

    private static int methodCount(Map<String, Integer> methods, String methodName) {
        return methods.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(methodName + "("))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    private record Artifact(
            String description,
            Path path,
            boolean fabricApplication,
            boolean includesCanonicalTag
    ) {

        private JarFile open() throws IOException {
            assertTrue(Files.isRegularFile(path), description + " is missing");
            assertFalse(Files.isSymbolicLink(path), description + " is linked");
            return new JarFile(path.toFile());
        }
    }

    private record ClassUsage(
            Set<String> enchantmentIds,
            int sharedRegistrationCount,
            int directRegistrationCount,
            Map<String, Integer> constructions,
            Set<String> sharedSupplierFields,
            Map<String, Integer> supplierResolutions
    ) {
    }
}
