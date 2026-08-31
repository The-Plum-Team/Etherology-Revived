package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GameEventRegistryResourcesTest {

    private static final String SHARED_GAME_EVENTS =
            "ru/feytox/etherology/registry/misc/SharedGameEvents.class";
    private static final String LEGACY_EVENTS_REGISTRY =
            "ru/feytox/etherology/registry/misc/EventsRegistry.class";
    private static final String FABRIC_GAME_EVENT_HOOKS =
            "ru/feytox/etherology/FabricGameEventHooks.class";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String SHARED_GAME_EVENTS_OWNER =
            "ru/feytox/etherology/registry/misc/SharedGameEvents";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String FABRIC_GAME_EVENT_HOOKS_OWNER =
            "ru/feytox/etherology/FabricGameEventHooks";
    private static final String FABRIC_SCULK_FREQUENCY_API =
            "net/fabricmc/fabric/api/registry/SculkSensorFrequencyRegistry";
    private static final String DIRECT_GAME_EVENT_FREQUENCY_OWNER =
            "net/minecraft/world/event/Vibrations";
    private static final String RESONANCE_ID = "etherology_resonance";
    private static final Set<String> GAME_EVENT_TAGS = Set.of(
            "data/minecraft/tags/game_events/vibrations.json",
            "data/minecraft/tags/game_events/warden_can_listen.json"
    );
    private static final byte[] EXACT_TAG_BYTES = (
            "{\n"
                    + "  \"replace\": false,\n"
                    + "  \"values\": [\n"
                    + "    \"etherology:etherology_resonance\"\n"
                    + "  ]\n"
                    + "}"
    ).getBytes(StandardCharsets.UTF_8);

    @Test
    void forgeClasspathExposesOnlyTheExactSharedDeferredOwner() throws IOException {
        URL sharedOwnerResource = requiredResourceUrl("/" + SHARED_GAME_EVENTS);
        assertEquals("jar", sharedOwnerResource.getProtocol());

        JarURLConnection connection = (JarURLConnection) sharedOwnerResource.openConnection();
        connection.setUseCaches(false);
        try (JarFile commonArtifact = connection.getJarFile()) {
            assertNotNull(commonArtifact.getJarEntry(SHARED_GAME_EVENTS));
            assertNull(commonArtifact.getJarEntry(LEGACY_EVENTS_REGISTRY));
            assertNull(commonArtifact.getJarEntry(FABRIC_GAME_EVENT_HOOKS));
        }
        assertNull(classLoaderResource(LEGACY_EVENTS_REGISTRY));
        assertNull(classLoaderResource(FABRIC_GAME_EVENT_HOOKS));

        Map<String, Integer> fieldAccess = new LinkedHashMap<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        Map<String, String> fieldSignatures = new LinkedHashMap<>();
        List<String> publicMethods = new ArrayList<>();
        AtomicInteger classAccess = new AtomicInteger();
        ClassReader reader = resourceClassReader("/" + SHARED_GAME_EVENTS);
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
                if ((access & Opcodes.ACC_PUBLIC) != 0) {
                    publicMethods.add(name + descriptor);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                classAccess.get() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL));
        assertEquals(Set.of("GAME_EVENTS", "RESONANCE"), fieldDescriptors.keySet());
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("GAME_EVENTS")
        );
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                fieldDescriptors.get("GAME_EVENTS")
        );
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister"
                        + "<Lnet/minecraft/world/event/GameEvent;>;",
                fieldSignatures.get("GAME_EVENTS")
        );
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("RESONANCE")
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier;",
                fieldDescriptors.get("RESONANCE")
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier"
                        + "<Lnet/minecraft/world/event/GameEvent;>;",
                fieldSignatures.get("RESONANCE")
        );
        assertEquals(List.of("register()V"), publicMethods);

        Set<String> references = classReferences(reader);
        assertTrue(references.contains("string:" + RESONANCE_ID));
        assertTrue(references.contains("integer:16"));
        assertTrue(references.contains(SHARED_DEFERRED_REGISTER + "#create"));
        assertTrue(references.contains(SHARED_DEFERRED_REGISTER + "#register"));
        assertTrue(references.contains(SHARED_DEFERRED_REGISTER + "#attach"));
        assertFalse(references.contains("net/minecraft/registry/Registry#register"));
    }

    @Test
    void keepsTheFrequencyHookInOnlyTheFabricProductionArtifact() throws IOException {
        Map<String, Path> artifacts = artifactPaths();
        for (Map.Entry<String, Path> artifact : artifacts.entrySet()) {
            boolean requireFabricHook = artifact.getKey().equals("Fabric production JAR");
            assertArtifactClassIsolation(
                    artifact.getKey(),
                    artifact.getValue(),
                    requireFabricHook
            );
        }
    }

    @Test
    void packagesTheExactCanonicalTagsInFabricAndForge() throws IOException {
        Map<String, Path> artifacts = artifactPaths();
        assertArtifactTags("Fabric production JAR", artifacts.get("Fabric production JAR"));
        assertArtifactTags("Forge shadow JAR", artifacts.get("Forge shadow JAR"));

        for (String tagPath : GAME_EVENT_TAGS) {
            assertArrayEquals(EXACT_TAG_BYTES, readResource("/" + tagPath));
        }
    }

    @Test
    void fabricDevelopmentClassesContainOnlyTheSupportedFrequencyHook()
            throws IOException {
        Path artifactPath = requiredArtifactPath(
                "etherology.gameEvents.fabricDevelopmentJar"
        );
        assertTrue(Files.isRegularFile(artifactPath));
        assertFalse(Files.isSymbolicLink(artifactPath));

        Set<String> directRegistrationOwners = new LinkedHashSet<>();
        Set<String> directGameEventConstructionOwners = new LinkedHashSet<>();
        Set<String> fabricFrequencyOwners = new LinkedHashSet<>();
        Set<String> directFrequencyMutationOwners = new LinkedHashSet<>();
        try (JarFile artifact = new JarFile(artifactPath.toFile())) {
            for (JarEntry entry : artifact.stream().toList()) {
                String classEntryName = entry.getName();
                if (!classEntryName.startsWith("ru/feytox/etherology/")
                        || !classEntryName.endsWith(".class")) {
                    continue;
                }
                ClassReader reader = artifactClassReader(artifact, classEntryName);
                Set<String> references = classReferences(reader);
                if (references.contains("string:" + RESONANCE_ID)
                        && (references.contains(
                                "dev/architectury/registry/registries/DeferredRegister#register"
                        )
                        || references.contains("net/minecraft/registry/Registry#register"))) {
                    directRegistrationOwners.add(classEntryName);
                }
                if (constructorOwners(reader, "(Ljava/lang/String;I)V")
                        .contains("net/minecraft/world/event/GameEvent")) {
                    directGameEventConstructionOwners.add(classEntryName);
                }
                if (references.contains(FABRIC_SCULK_FREQUENCY_API + "#register")) {
                    fabricFrequencyOwners.add(classEntryName);
                }
                if (references.stream().anyMatch(reference ->
                        reference.startsWith(DIRECT_GAME_EVENT_FREQUENCY_OWNER + "."))) {
                    directFrequencyMutationOwners.add(classEntryName);
                }
            }
        }

        assertEquals(Set.of(), directRegistrationOwners);
        assertEquals(Set.of(), directGameEventConstructionOwners);
        assertEquals(Set.of(FABRIC_GAME_EVENT_HOOKS), fabricFrequencyOwners);
        assertEquals(Set.of(), directFrequencyMutationOwners);
    }

    private static void assertArtifactClassIsolation(
            String description,
            Path artifactPath,
            boolean requireFabricHook
    ) throws IOException {
        assertTrue(Files.isRegularFile(artifactPath), description + " is missing");
        assertFalse(Files.isSymbolicLink(artifactPath), description + " is linked");

        try (JarFile artifact = new JarFile(artifactPath.toFile())) {
            List<String> entryNames = artifact.stream().map(JarEntry::getName).toList();
            assertEquals(1, entryNames.stream().filter(SHARED_GAME_EVENTS::equals).count());
            assertEquals(0, entryNames.stream().filter(LEGACY_EVENTS_REGISTRY::equals).count());
            assertEquals(
                    requireFabricHook ? 1 : 0,
                    entryNames.stream().filter(FABRIC_GAME_EVENT_HOOKS::equals).count()
            );

            ClassReader sharedOwner = artifactClassReader(artifact, SHARED_GAME_EVENTS);
            assertExactTransformedSharedOwner(description, sharedOwner);
            Set<String> mappedGameEventConstructors = constructorOwners(
                    sharedOwner,
                    "(Ljava/lang/String;I)V"
            );
            assertEquals(1, mappedGameEventConstructors.size(), description);

            Set<String> resonanceRegistrationOwners = new LinkedHashSet<>();
            Set<String> gameEventConstructionOwners = new LinkedHashSet<>();
            Set<String> fabricFrequencyOwners = new LinkedHashSet<>();
            Set<String> directFrequencyMutationOwners = new LinkedHashSet<>();
            AtomicInteger gameEventConstructionCount = new AtomicInteger();
            for (String classEntryName : entryNames) {
                if (!classEntryName.endsWith(".class")) {
                    continue;
                }
                ClassReader classReader = artifactClassReader(artifact, classEntryName);
                Set<String> references = classReferences(classReader);
                if (references.contains("string:" + RESONANCE_ID)
                        && (references.contains(SHARED_DEFERRED_REGISTER + "#register")
                        || references.contains(
                                "dev/architectury/registry/registries/DeferredRegister#register"
                        )
                        || references.contains("net/minecraft/registry/Registry#register"))) {
                    resonanceRegistrationOwners.add(classEntryName);
                }
                if (!mappedGameEventConstructors.isEmpty()
                        && !constructorOwners(classReader, "(Ljava/lang/String;I)V")
                        .stream()
                        .filter(mappedGameEventConstructors::contains)
                        .toList()
                        .isEmpty()) {
                    gameEventConstructionOwners.add(classEntryName);
                }
                for (String constructorOwner : mappedGameEventConstructors) {
                    gameEventConstructionCount.addAndGet(constructorInvocationCount(
                            classReader,
                            constructorOwner,
                            "(Ljava/lang/String;I)V"
                    ));
                }
                if (references.contains(FABRIC_SCULK_FREQUENCY_API + "#register")) {
                    fabricFrequencyOwners.add(classEntryName);
                }
                if (references.stream().anyMatch(reference ->
                        reference.startsWith(DIRECT_GAME_EVENT_FREQUENCY_OWNER + "."))) {
                    directFrequencyMutationOwners.add(classEntryName);
                }
            }
            assertEquals(
                    Set.of(SHARED_GAME_EVENTS),
                    resonanceRegistrationOwners,
                    description
            );
            assertEquals(
                    Set.of(SHARED_GAME_EVENTS),
                    gameEventConstructionOwners,
                    description
            );
            assertEquals(1, gameEventConstructionCount.get(), description);
            assertEquals(
                    requireFabricHook ? Set.of(FABRIC_GAME_EVENT_HOOKS) : Set.of(),
                    fabricFrequencyOwners,
                    description
            );
            assertEquals(Set.of(), directFrequencyMutationOwners, description);

            Set<String> bootstrapReferences = classReferences(artifact, COMMON_BOOTSTRAP);
            assertTrue(
                    bootstrapReferences.contains(SHARED_GAME_EVENTS_OWNER + "#register"),
                    description
            );
            if (requireFabricHook) {
                assertExactFabricHook(artifact);
                assertFabricInitializerOrder(artifact);
            }
        }
    }

    private static void assertExactFabricHook(JarFile artifact) throws IOException {
        JarEntry hookEntry = artifact.getJarEntry(FABRIC_GAME_EVENT_HOOKS);
        assertNotNull(hookEntry);
        AtomicInteger classAccess = new AtomicInteger();
        Map<String, Integer> methodAccess = new LinkedHashMap<>();
        ClassReader reader;
        try (InputStream classStream = artifact.getInputStream(hookEntry)) {
            reader = new ClassReader(classStream);
        }
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
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                methodAccess.put(name + descriptor, access);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(0, classAccess.get() & Opcodes.ACC_PUBLIC);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(Set.of("<init>()V", "registerSculkSensorFrequency()V"),
                methodAccess.keySet());
        assertEquals(Opcodes.ACC_PRIVATE, methodAccess.get("<init>()V"));
        assertEquals(
                Opcodes.ACC_STATIC,
                methodAccess.get("registerSculkSensorFrequency()V")
        );

        Set<String> references = classReferences(reader);
        assertTrue(references.contains(SHARED_GAME_EVENTS_OWNER + ".RESONANCE"));
        assertTrue(references.contains(
                "dev/architectury/registry/registries/RegistrySupplier#get"
        ));
        assertTrue(references.contains(FABRIC_SCULK_FREQUENCY_API + "#register"));
        assertTrue(references.contains("integer:10"));
    }

    private static void assertFabricInitializerOrder(JarFile artifact) throws IOException {
        List<String> invocations = methodInvocations(artifact, FABRIC_INITIALIZER, "initialize()V");
        int soundIndex = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedSounds#register"
        );
        int gameEventIndex = invocations.indexOf(SHARED_GAME_EVENTS_OWNER + "#register");
        int lootConditionIndex = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedLootConditions#register"
        );
        int frequencyIndex = invocations.indexOf(
                FABRIC_GAME_EVENT_HOOKS_OWNER + "#registerSculkSensorFrequency"
        );
        assertTrue(soundIndex >= 0);
        assertEquals(soundIndex + 1, gameEventIndex);
        assertEquals(gameEventIndex + 1, lootConditionIndex);
        assertEquals(lootConditionIndex + 1, frequencyIndex);
    }

    private static void assertArtifactTags(String description, Path artifactPath)
            throws IOException {
        assertNotNull(artifactPath, description + " path is missing");
        assertTrue(Files.isRegularFile(artifactPath), description + " is missing");
        assertFalse(Files.isSymbolicLink(artifactPath), description + " is linked");

        try (JarFile artifact = new JarFile(artifactPath.toFile())) {
            List<String> entryNames = artifact.stream().map(JarEntry::getName).toList();
            for (String tagPath : GAME_EVENT_TAGS) {
                assertEquals(
                        1,
                        entryNames.stream().filter(tagPath::equals).count(),
                        description + " has the wrong " + tagPath + " count"
                );
                JarEntry tagEntry = artifact.getJarEntry(tagPath);
                assertNotNull(tagEntry);
                try (InputStream tagStream = artifact.getInputStream(tagEntry)) {
                    assertArrayEquals(
                            EXACT_TAG_BYTES,
                            tagStream.readAllBytes(),
                            description + " changed " + tagPath
                    );
                }
            }
        }
    }

    private static Map<String, Path> artifactPaths() {
        Map<String, Path> artifacts = new LinkedHashMap<>();
        artifacts.put("Common JAR", requiredArtifactPath(
                "etherology.gameEvents.commonJar"
        ));
        artifacts.put("Fabric-transformed common JAR", requiredArtifactPath(
                "etherology.gameEvents.fabricTransformedCommonJar"
        ));
        artifacts.put("Forge-transformed common JAR", requiredArtifactPath(
                "etherology.gameEvents.forgeTransformedCommonJar"
        ));
        artifacts.put("Fabric production JAR", requiredArtifactPath(
                "etherology.gameEvents.fabricProductionJar"
        ));
        artifacts.put("Forge shadow JAR", requiredArtifactPath(
                "etherology.gameEvents.forgeShadowJar"
        ));
        return artifacts;
    }

    private static Path requiredArtifactPath(String propertyName) {
        String propertyValue = System.getProperty(propertyName);
        assertNotNull(propertyValue, "Missing Gradle test property " + propertyName);
        return Path.of(propertyValue);
    }

    private static List<String> methodInvocations(
            JarFile artifact,
            String classEntryName,
            String methodKey
    ) throws IOException {
        JarEntry classEntry = artifact.getJarEntry(classEntryName);
        assertNotNull(classEntry, "Missing packaged class " + classEntryName);
        List<String> invocations = new ArrayList<>();
        try (InputStream classStream = artifact.getInputStream(classEntry)) {
            new ClassReader(classStream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    if (!(name + descriptor).equals(methodKey)) {
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
        }
        return invocations;
    }

    private static Set<String> classReferences(JarFile artifact, String classEntryName)
            throws IOException {
        return classReferences(artifactClassReader(artifact, classEntryName));
    }

    private static ClassReader artifactClassReader(
            JarFile artifact,
            String classEntryName
    ) throws IOException {
        JarEntry classEntry = artifact.getJarEntry(classEntryName);
        assertNotNull(classEntry, "Missing packaged class " + classEntryName);
        try (InputStream classStream = artifact.getInputStream(classEntry)) {
            return new ClassReader(classStream);
        }
    }

    private static void assertExactTransformedSharedOwner(
            String description,
            ClassReader reader
    ) {
        Map<String, List<String>> stringConstants = new LinkedHashMap<>();
        Map<String, List<Integer>> integerConstants = new LinkedHashMap<>();
        Map<String, List<String>> invocations = new LinkedHashMap<>();
        Map<String, List<String>> variableLoads = new LinkedHashMap<>();

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
                List<String> methodStrings = new ArrayList<>();
                List<Integer> methodIntegers = new ArrayList<>();
                List<String> methodInvocations = new ArrayList<>();
                List<String> methodVariableLoads = new ArrayList<>();
                stringConstants.put(methodKey, methodStrings);
                integerConstants.put(methodKey, methodIntegers);
                invocations.put(methodKey, methodInvocations);
                variableLoads.put(methodKey, methodVariableLoads);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            methodStrings.add(stringValue);
                        } else if (value instanceof Integer integerValue) {
                            methodIntegers.add(integerValue);
                        }
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                            methodIntegers.add(operand);
                        }
                    }

                    @Override
                    public void visitVarInsn(int opcode, int variable) {
                        if (opcode == Opcodes.ALOAD) {
                            methodVariableLoads.add("ALOAD:" + variable);
                        } else if (opcode == Opcodes.ILOAD) {
                            methodVariableLoads.add("ILOAD:" + variable);
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
                        methodInvocations.add(owner + "#" + name + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        String privateRegistration =
                "register(Ljava/lang/String;I)"
                        + "Ldev/architectury/registry/registries/RegistrySupplier;";
        String deferredRegistration = SHARED_DEFERRED_REGISTER
                + "#register(Ljava/lang/String;Ljava/util/function/Supplier;)"
                + "Ldev/architectury/registry/registries/RegistrySupplier;";
        String lambdaMethod = invocations.keySet().stream()
                .filter(method -> method.startsWith("lambda$register$"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        description + " has no deferred game-event factory"
                ));
        List<String> allInvocations = invocations.values().stream()
                .flatMap(List::stream)
                .toList();

        assertEquals(List.of(RESONANCE_ID), stringConstants.get("<clinit>()V"), description);
        assertEquals(List.of(16), integerConstants.get("<clinit>()V"), description);
        assertEquals(
                1,
                invocations.get("<clinit>()V").stream()
                        .filter(invocation -> invocation.equals(
                                SHARED_GAME_EVENTS.replace(".class", "")
                                        + "#" + privateRegistration
                        ))
                        .count(),
                description
        );
        assertEquals(
                List.of("ALOAD:0", "ALOAD:0", "ILOAD:1"),
                variableLoads.get(privateRegistration),
                description
        );
        assertEquals(
                1,
                invocations.get(privateRegistration).stream()
                        .filter(deferredRegistration::equals)
                        .count(),
                description
        );
        assertEquals(
                List.of("ALOAD:0", "ILOAD:1"),
                variableLoads.get(lambdaMethod),
                description
        );
        assertEquals(
                1,
                invocations.get(lambdaMethod).stream()
                        .filter(invocation -> invocation.endsWith(
                                "#<init>(Ljava/lang/String;I)V"
                        ))
                        .count(),
                description
        );
        assertEquals(1, invocations.get(lambdaMethod).size(), description);
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                invocations.get("register()V"),
                description
        );
        assertEquals(
                0,
                allInvocations.stream()
                        .filter(invocation -> invocation.startsWith(
                                "dev/architectury/registry/registries/RegistrySupplier#get"
                        ))
                        .count(),
                description
        );
        assertEquals(
                1,
                allInvocations.stream()
                        .filter(deferredRegistration::equals)
                        .count(),
                description
        );
        assertEquals(
                0,
                allInvocations.stream()
                        .filter(invocation -> invocation.startsWith(
                                "dev/architectury/registry/registries/"
                                        + "DeferredRegister#register"
                        ))
                        .count(),
                description
        );
        assertEquals(
                0,
                allInvocations.stream()
                        .filter(invocation -> invocation.startsWith(
                                "net/minecraft/registry/Registry#register"
                        ))
                        .count(),
                description
        );
    }

    private static Set<String> constructorOwners(
            ClassReader reader,
            String expectedDescriptor
    ) {
        Set<String> owners = new LinkedHashSet<>();
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
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (opcode == Opcodes.INVOKESPECIAL
                                && name.equals("<init>")
                                && descriptor.equals(expectedDescriptor)) {
                            owners.add(owner);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return owners;
    }

    private static int constructorInvocationCount(
            ClassReader reader,
            String expectedOwner,
            String expectedDescriptor
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
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (opcode == Opcodes.INVOKESPECIAL
                                && owner.equals(expectedOwner)
                                && name.equals("<init>")
                                && descriptor.equals(expectedDescriptor)) {
                            count.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count.get();
    }

    private static Set<String> classReferences(ClassReader reader) {
        Set<String> references = new LinkedHashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                references.add(name);
                references.add(descriptor);
                if (signature != null) {
                    references.add(signature);
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
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            references.add("string:" + stringValue);
                        } else if (value instanceof Integer integerValue) {
                            references.add("integer:" + integerValue);
                        }
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        references.add("integer:" + operand);
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        references.add(owner + "." + name);
                        references.add(descriptor);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        references.add(owner + "#" + name);
                        references.add(descriptor);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        references.add(type);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return references;
    }

    private static ClassReader resourceClassReader(String resourcePath) throws IOException {
        try (InputStream classStream = requiredResource(resourcePath)) {
            return new ClassReader(classStream);
        }
    }

    private static byte[] readResource(String resourcePath) throws IOException {
        try (InputStream resource = requiredResource(resourcePath)) {
            return resource.readAllBytes();
        }
    }

    private static InputStream requiredResource(String resourcePath) {
        InputStream resource = GameEventRegistryResourcesTest.class
                .getResourceAsStream(resourcePath);
        assertNotNull(resource, "Missing Forge runtime resource " + resourcePath);
        return resource;
    }

    private static URL requiredResourceUrl(String resourcePath) {
        URL resource = GameEventRegistryResourcesTest.class.getResource(resourcePath);
        assertNotNull(resource, "Missing Forge runtime resource " + resourcePath);
        return resource;
    }

    private static URL classLoaderResource(String resourcePath) {
        return GameEventRegistryResourcesTest.class.getClassLoader().getResource(resourcePath);
    }
}
