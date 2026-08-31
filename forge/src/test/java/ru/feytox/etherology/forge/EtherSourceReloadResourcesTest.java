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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherSourceReloadResourcesTest {

    private static final String RESOURCE_RELOADERS =
            "ru/feytox/etherology/registry/misc/ResourceReloaders.class";
    private static final String RESOURCE_RELOADERS_OWNER =
            "ru/feytox/etherology/registry/misc/ResourceReloaders";
    private static final String ETHER_SOURCE_LOADER =
            "ru/feytox/etherology/data/ethersource/EtherSourceLoader.class";
    private static final String ETHER_SOURCE_LOADER_OWNER =
            "ru/feytox/etherology/data/ethersource/EtherSourceLoader";
    private static final String ETHER_SOURCES =
            "ru/feytox/etherology/data/ethersource/EtherSources.class";
    private static final String ETHER_SOURCES_DESERIALIZER =
            "ru/feytox/etherology/data/ethersource/EtherSourcesDeserializer.class";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String RELOAD_LISTENER_REGISTRY_OWNER =
            "dev/architectury/registry/ReloadListenerRegistry";
    private static final String DEFAULT_RESOURCE =
            "data/etherology/ether_sources/default.json";
    private static final Set<String> JSON_DATA_LOADER_OWNERS = Set.of(
            "net/minecraft/resource/JsonDataLoader",
            "net/minecraft/class_4309",
            "net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener"
    );
    private static final Set<String> SERVER_DATA_FIELDS = Set.of(
            "net/minecraft/resource/ResourceType.SERVER_DATA",
            "net/minecraft/class_3264.field_14190",
            "net/minecraft/server/packs/PackType.SERVER_DATA"
    );
    private static final List<ExpectedSource> EXPECTED_SOURCES = List.of(
            new ExpectedSource("etherology:primoshard_keta", 4),
            new ExpectedSource("etherology:primoshard_rella", 4),
            new ExpectedSource("etherology:primoshard_clos", 4),
            new ExpectedSource("etherology:primoshard_via", 4),
            new ExpectedSource("minecraft:redstone", 2),
            new ExpectedSource("minecraft:glowstone_dust", 1),
            new ExpectedSource("minecraft:lapis_lazuli", 1),
            new ExpectedSource("minecraft:quartz", 1),
            new ExpectedSource("minecraft:ender_pearl", 4),
            new ExpectedSource("minecraft:ender_eye", 6),
            new ExpectedSource("minecraft:blaze_powder", 2),
            new ExpectedSource("minecraft:ancient_debris", 4),
            new ExpectedSource("minecraft:chorus_fruit", 2),
            new ExpectedSource("minecraft:experience_bottle", 8),
            new ExpectedSource("minecraft:echo_shard", 12),
            new ExpectedSource("minecraft:sculk", 12),
            new ExpectedSource("minecraft:crying_obsidian", 6),
            new ExpectedSource("minecraft:magma_cream", 2),
            new ExpectedSource("minecraft:heart_of_the_sea", 12),
            new ExpectedSource("minecraft:gunpowder", 1),
            new ExpectedSource("minecraft:prismarine_crystals", 1),
            new ExpectedSource("minecraft:ghast_tear", 4),
            new ExpectedSource("minecraft:honeycomb", 1)
    );

    @Test
    void everyProductionBoundaryHasOneExactCommonOwnerAndResource() throws IOException {
        Path canonicalResource = requiredPath("etherology.etherSources.defaultResource");
        assertTrue(Files.isRegularFile(canonicalResource));
        assertFalse(Files.isSymbolicLink(canonicalResource));
        byte[] canonicalBytes = Files.readAllBytes(canonicalResource);

        for (Artifact artifact : artifacts()) {
            try (JarFile jar = artifact.open()) {
                List<String> entryNames = jar.stream().map(JarEntry::getName).toList();
                for (String classEntry : List.of(
                        RESOURCE_RELOADERS,
                        ETHER_SOURCE_LOADER,
                        ETHER_SOURCES,
                        ETHER_SOURCES_DESERIALIZER
                )) {
                    assertEquals(1, count(entryNames, classEntry), artifact.description());
                }
                assertEquals(1, count(entryNames, DEFAULT_RESOURCE), artifact.description());
                try (InputStream resource = jar.getInputStream(
                        jar.getJarEntry(DEFAULT_RESOURCE)
                )) {
                    assertArrayEquals(canonicalBytes, resource.readAllBytes(),
                            artifact.description());
                }

                assertExactListenerOwner(artifact.description(), jar);
                assertExactLoaderDirectory(artifact.description(), jar);
                assertSoleListenerRegistration(artifact.description(), jar, entryNames);
                assertCommonBootstrapOrder(artifact.description(), jar);
                if (artifact.fabricApplication()) {
                    assertFabricApplicationOrder(artifact.description(), jar);
                }
            }
        }
    }

    @Test
    void canonicalDefaultHasTheExactCorrectedTwentyThreeEntryContract()
            throws IOException {
        Path canonicalResource = requiredPath("etherology.etherSources.defaultResource");
        JsonObject sourceValues;
        try (InputStream input = Files.newInputStream(canonicalResource);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            assertTrue(root.isJsonObject());
            sourceValues = root.getAsJsonObject();
        }

        assertEquals(EXPECTED_SOURCES.size(), sourceValues.size());
        assertEquals(
                EXPECTED_SOURCES.stream().map(ExpectedSource::id).toList(),
                new ArrayList<>(sourceValues.keySet())
        );
        for (ExpectedSource expected : EXPECTED_SOURCES) {
            JsonElement actual = sourceValues.get(expected.id());
            assertNotNull(actual, expected.id());
            assertTrue(actual.isJsonPrimitive(), expected.id());
            assertTrue(actual.getAsJsonPrimitive().isNumber(), expected.id());
            assertEquals(Integer.toString(expected.value()), actual.toString(), expected.id());
        }
        assertTrue(sourceValues.has("etherology:primoshard_rella"));
        assertFalse(sourceValues.has("etherology:primoshard_rela"));
    }

    @Test
    void forgeClasspathAndSourceLayoutExposeNoLegacyFabricOwner() throws IOException {
        Enumeration<java.net.URL> owners = EtherSourceReloadResourcesTest.class
                .getClassLoader()
                .getResources(RESOURCE_RELOADERS);
        assertTrue(owners.hasMoreElements());
        assertEquals("jar", owners.nextElement().getProtocol());
        assertFalse(owners.hasMoreElements());

        Path repositoryRoot = requiredPath("etherology.etherSources.repositoryRoot");
        for (String legacyPath : List.of(
                "src/main/java/ru/feytox/etherology/registry/misc/ResourceReloaders.java",
                "src/main/java/ru/feytox/etherology/data/ethersource/EtherSourceLoader.java",
                "src/main/java/ru/feytox/etherology/data/ethersource/EtherSources.java",
                "src/main/java/ru/feytox/etherology/data/ethersource/EtherSourcesDeserializer.java",
                "src/main/resources/data/etherology/ether_sources/default.json"
        )) {
            assertFalse(
                    Files.exists(repositoryRoot.resolve(legacyPath), LinkOption.NOFOLLOW_LINKS),
                    legacyPath
            );
        }
    }

    private static void assertExactListenerOwner(String description, JarFile jar)
            throws IOException {
        ClassReader owner = classReader(jar, RESOURCE_RELOADERS);
        Map<String, Integer> methodAccess = methodAccess(owner);
        assertTrue((methodAccess.get("registerServerData()V")
                & Opcodes.ACC_SYNCHRONIZED) != 0, description);
        assertEquals(
                List.of("etherology", "ether_sources"),
                methodStringConstants(owner, "<clinit>"),
                description
        );

        MethodUsage registration = methodUsage(owner, "registerServerData");
        assertEquals(
                1,
                registration.invocations().stream().filter(invocation -> invocation.startsWith(
                        RELOAD_LISTENER_REGISTRY_OWNER + "#register("
                )).count(),
                description
        );
        assertEquals(
                1,
                registration.fields().stream().filter(field -> field.equals(
                        ETHER_SOURCE_LOADER_OWNER + ".INSTANCE"
                )).count(),
                description
        );
        assertEquals(
                1,
                registration.fields().stream().filter(field -> field.equals(
                        RESOURCE_RELOADERS_OWNER + ".ETHER_SOURCES_ID"
                )).count(),
                description
        );
        assertEquals(
                1,
                registration.fields().stream().filter(SERVER_DATA_FIELDS::contains).count(),
                description
        );
    }

    private static void assertExactLoaderDirectory(String description, JarFile jar)
            throws IOException {
        ClassReader loader = classReader(jar, ETHER_SOURCE_LOADER);
        AtomicReference<String> superName = new AtomicReference<>();
        loader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String classSuperName,
                    String[] interfaces
            ) {
                superName.set(classSuperName);
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue(JSON_DATA_LOADER_OWNERS.contains(superName.get()),
                description + ": " + superName.get());
        assertEquals(List.of("ether_sources"), methodStringConstants(loader, "<init>"),
                description);
        assertEquals(
                1,
                methodUsage(loader, "<init>").invocations().stream()
                        .filter(invocation -> JSON_DATA_LOADER_OWNERS.stream().anyMatch(
                                owner -> invocation.startsWith(owner + "#<init>(")
                        ))
                        .count(),
                description
        );
    }

    private static void assertSoleListenerRegistration(
            String description,
            JarFile jar,
            List<String> entryNames
    ) throws IOException {
        Set<String> registrationMethods = new LinkedHashSet<>();
        AtomicInteger registrationCount = new AtomicInteger();
        for (String classEntry : entryNames) {
            if (!classEntry.endsWith(".class")) {
                continue;
            }
            ClassReader reader = classReader(jar, classEntry);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    String methodKey = classEntry + "#" + name + descriptor;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            if (owner.equals(RELOAD_LISTENER_REGISTRY_OWNER)
                                    && name.equals("register")) {
                                registrationMethods.add(methodKey);
                                registrationCount.incrementAndGet();
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertEquals(
                Set.of(RESOURCE_RELOADERS + "#registerServerData()V"),
                registrationMethods,
                description
        );
        assertEquals(1, registrationCount.get(), description);
    }

    private static void assertCommonBootstrapOrder(String description, JarFile jar)
            throws IOException {
        List<String> invocations = methodInvocations(
                classReader(jar, COMMON_BOOTSTRAP),
                "initialize"
        );
        int lootConditions = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedLootConditions#register()V"
        );
        int enchantments = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedEnchantments#register()V"
        );
        int resourceReloaders = invocations.indexOf(
                RESOURCE_RELOADERS_OWNER + "#registerServerData()V"
        );
        int lifecycle = invocations.indexOf(
                "ru/feytox/etherology/bootstrap/BootstrapLifecycle#initialize"
                        + "(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );

        assertTrue(lootConditions >= 0, description);
        assertEquals(lootConditions + 1, enchantments, description);
        assertEquals(enchantments + 1, resourceReloaders, description);
        assertEquals(resourceReloaders + 1, lifecycle, description);
        assertEquals(1, count(invocations,
                RESOURCE_RELOADERS_OWNER + "#registerServerData()V"), description);
    }

    private static void assertFabricApplicationOrder(String description, JarFile jar)
            throws IOException {
        List<String> invocations = methodInvocations(
                classReader(jar, FABRIC_INITIALIZER),
                "initialize"
        );
        int blocks = invocations.indexOf(
                "ru/feytox/etherology/registry/block/EBlocks#registerAll()V"
        );
        int resourceReloaders = invocations.indexOf(
                RESOURCE_RELOADERS_OWNER + "#registerServerData()V"
        );
        int network = invocations.indexOf(
                "ru/feytox/etherology/network/EtherologyNetwork#registerCommonSide()V"
        );

        assertTrue(blocks >= 0, description);
        assertEquals(blocks + 1, resourceReloaders, description);
        assertEquals(resourceReloaders + 1, network, description);
        assertEquals(1, count(invocations,
                RESOURCE_RELOADERS_OWNER + "#registerServerData()V"), description);
        assertEquals(0, invocations.stream().filter(invocation -> invocation.startsWith(
                "ru/feytox/etherology/bootstrap/EtherologyBootstrap#initialize("
        )).count(), description);
    }

    private static Map<String, Integer> methodAccess(ClassReader reader) {
        Map<String, Integer> methods = new LinkedHashMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                methods.put(name + descriptor, access);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return methods;
    }

    private static List<String> methodStringConstants(
            ClassReader reader,
            String expectedMethodName
    ) {
        List<String> constants = new ArrayList<>();
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
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            constants.add(stringValue);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return constants;
    }

    private static MethodUsage methodUsage(ClassReader reader, String expectedMethodName) {
        List<String> fields = new ArrayList<>();
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
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC) {
                            fields.add(owner + "." + name);
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
        return new MethodUsage(fields, invocations);
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

    private static ClassReader classReader(JarFile jar, String classEntry)
            throws IOException {
        JarEntry entry = jar.getJarEntry(classEntry);
        assertNotNull(entry, "Missing packaged class " + classEntry);
        try (InputStream input = jar.getInputStream(entry)) {
            return new ClassReader(input);
        }
    }

    private static List<Artifact> artifacts() {
        return List.of(
                new Artifact(
                        "Common JAR",
                        requiredPath("etherology.etherSources.commonJar"),
                        false
                ),
                new Artifact(
                        "Fabric-transformed common JAR",
                        requiredPath("etherology.etherSources.fabricTransformedCommonJar"),
                        false
                ),
                new Artifact(
                        "Forge-transformed common JAR",
                        requiredPath("etherology.etherSources.forgeTransformedCommonJar"),
                        false
                ),
                new Artifact(
                        "Fabric development JAR",
                        requiredPath("etherology.etherSources.fabricDevelopmentJar"),
                        true
                ),
                new Artifact(
                        "Fabric remapped production JAR",
                        requiredPath("etherology.etherSources.fabricProductionJar"),
                        true
                ),
                new Artifact(
                        "Forge shadow JAR",
                        requiredPath("etherology.etherSources.forgeShadowJar"),
                        false
                )
        );
    }

    private static Path requiredPath(String propertyName) {
        String value = System.getProperty(propertyName);
        assertNotNull(value, "Missing Gradle test property " + propertyName);
        return Path.of(value);
    }

    private static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }

    private record Artifact(String description, Path path, boolean fabricApplication) {

        private JarFile open() throws IOException {
            assertTrue(Files.isRegularFile(path), description + " is missing");
            assertFalse(Files.isSymbolicLink(path), description + " is linked");
            return new JarFile(path.toFile());
        }
    }

    private record ExpectedSource(String id, int value) {
    }

    private record MethodUsage(List<String> fields, List<String> invocations) {
    }
}
