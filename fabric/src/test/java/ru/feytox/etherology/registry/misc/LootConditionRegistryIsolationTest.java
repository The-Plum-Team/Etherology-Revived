package ru.feytox.etherology.registry.misc;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LootConditionRegistryIsolationTest {

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
    private static final String CONDITION_ID = "random_chance_with_fortune";
    private static final String ATTRAHITE_LOOT_TABLE =
            "data/etherology/loot_tables/blocks/attrahite.json";

    @Test
    void commonArtifactOwnsTheExactDeferredCondition() throws IOException {
        URL sharedOwnerResource = singleClasspathResource(SHARED_LOOT_CONDITIONS);
        assertEquals("jar", sharedOwnerResource.getProtocol());

        JarURLConnection connection = (JarURLConnection) sharedOwnerResource.openConnection();
        connection.setUseCaches(false);
        try (JarFile commonArtifact = connection.getJarFile()) {
            assertNotNull(commonArtifact.getJarEntry(SHARED_LOOT_CONDITIONS));
            assertNotNull(commonArtifact.getJarEntry(RANDOM_CHANCE_CONDITION));
            assertNotNull(commonArtifact.getJarEntry(RANDOM_CHANCE_SERIALIZER));
            assertNotNull(commonArtifact.getJarEntry(COMMON_BOOTSTRAP));
            assertNull(commonArtifact.getJarEntry(LEGACY_LOOT_CONDITIONS));

            ClassReader sharedOwner = artifactClass(commonArtifact, SHARED_LOOT_CONDITIONS);
            assertEquals(List.of(CONDITION_ID), methodStringConstants(sharedOwner, "<clinit>"));
            assertEquals(
                    1,
                    invocationCount(
                            sharedOwner,
                            null,
                            SHARED_DEFERRED_REGISTER_OWNER,
                            "register"
                    )
            );
            assertEquals(
                    1,
                    invocationCount(
                            sharedOwner,
                            "register",
                            SHARED_DEFERRED_REGISTER_OWNER,
                            "attach"
                    )
            );
            assertEquals(
                    0,
                    invocationCount(sharedOwner, null, REGISTRY_SUPPLIER_OWNER, "get")
            );
            assertEquals(
                    1,
                    invocationCount(
                            artifactClass(commonArtifact, COMMON_BOOTSTRAP),
                            "initialize",
                            SHARED_LOOT_CONDITIONS_OWNER,
                            "register"
                    )
            );
        }

        assertNull(classLoaderResource(LEGACY_LOOT_CONDITIONS));
    }

    @Test
    void fabricInitializerAttachesLootConditionsBeforeEnchantmentsAndTheEventHook()
            throws IOException {
        List<String> invocations = methodInvocations(
                classpathClass(FABRIC_INITIALIZER),
                "initialize"
        );
        int gameEventIndex = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedGameEvents#register"
        );
        int lootConditionIndex = invocations.indexOf(
                SHARED_LOOT_CONDITIONS_OWNER + "#register"
        );
        int enchantmentIndex = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedEnchantments#register"
        );
        int frequencyHookIndex = invocations.indexOf(
                "ru/feytox/etherology/FabricGameEventHooks#registerSculkSensorFrequency"
        );

        assertTrue(gameEventIndex >= 0);
        assertEquals(gameEventIndex + 1, lootConditionIndex);
        assertEquals(lootConditionIndex + 1, enchantmentIndex);
        assertEquals(enchantmentIndex + 1, frequencyHookIndex);
        assertEquals(
                1,
                count(invocations, SHARED_LOOT_CONDITIONS_OWNER + "#register")
        );
    }

    @Test
    void conditionResolvesItsSupplierOnlyFromGetType() throws IOException {
        ClassReader condition = classpathClass(RANDOM_CHANCE_CONDITION);
        Map<String, List<String>> fieldReferences = fieldReferencesByMethod(condition);
        Map<String, List<String>> invocations = invocationsByMethod(condition);
        List<String> supplierFieldOwners = fieldReferences.entrySet().stream()
                .filter(entry -> entry.getValue().contains(
                        SHARED_LOOT_CONDITIONS_OWNER + ".RANDOM_CHANCE_WITH_FORTUNE"
                ))
                .map(Map.Entry::getKey)
                .toList();

        assertEquals(1, supplierFieldOwners.size());
        String getTypeMethod = supplierFieldOwners.get(0);
        assertTrue(getTypeMethod.startsWith("getType()"));
        assertEquals(
                1,
                invocations.get(getTypeMethod).stream()
                        .filter(invocation -> invocation.startsWith(
                                REGISTRY_SUPPLIER_OWNER + "#get()"
                        ))
                        .count()
        );
        assertEquals(
                1,
                invocations.values().stream()
                        .flatMap(List::stream)
                        .filter(invocation -> invocation.startsWith(
                                REGISTRY_SUPPLIER_OWNER + "#get()"
                        ))
                        .count()
        );
    }

    @Test
    void canonicalFabricLootTableUsesOnlyTheSharedConditionId() throws IOException {
        String lootTable;
        try (InputStream resource = requiredResource(ATTRAHITE_LOOT_TABLE)) {
            lootTable = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertEquals(1, count(lootTable, "etherology:" + CONDITION_ID));
        assertEquals(1, count(lootTable, "\"chance\": 0.05"));
        assertEquals(1, count(lootTable, "\"fortune_multiplier\": 0.05"));
        assertFalse(lootTable.contains("etherology:random_chance_with_looting"));
    }

    private static ClassReader classpathClass(String classResource) throws IOException {
        try (InputStream classStream = requiredResource(classResource)) {
            return new ClassReader(classStream);
        }
    }

    private static ClassReader artifactClass(JarFile artifact, String classResource)
            throws IOException {
        try (InputStream classStream = artifact.getInputStream(
                artifact.getJarEntry(classResource)
        )) {
            return new ClassReader(classStream);
        }
    }

    private static URL singleClasspathResource(String classResource) throws IOException {
        Enumeration<URL> resources = LootConditionRegistryIsolationTest.class
                .getClassLoader()
                .getResources(classResource);
        assertTrue(resources.hasMoreElements(), "Missing classpath class " + classResource);
        URL resource = resources.nextElement();
        assertFalse(resources.hasMoreElements(), "Multiple owners provide " + classResource);
        return resource;
    }

    private static URL classLoaderResource(String resourcePath) {
        return LootConditionRegistryIsolationTest.class
                .getClassLoader()
                .getResource(resourcePath);
    }

    private static InputStream requiredResource(String resourcePath) {
        InputStream resource = LootConditionRegistryIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(resourcePath);
        assertNotNull(resource, "Missing classpath resource " + resourcePath);
        return resource;
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
                if (expectedMethodName != null && !name.equals(expectedMethodName)) {
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

    private static Map<String, List<String>> fieldReferencesByMethod(ClassReader reader) {
        Map<String, List<String>> references = new LinkedHashMap<>();
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
                List<String> methodReferences = new ArrayList<>();
                references.put(methodKey, methodReferences);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        methodReferences.add(owner + "." + name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return references;
    }

    private static Map<String, List<String>> invocationsByMethod(ClassReader reader) {
        Map<String, List<String>> invocations = new LinkedHashMap<>();
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
                List<String> methodInvocations = new ArrayList<>();
                invocations.put(methodKey, methodInvocations);
                return new MethodVisitor(Opcodes.ASM9) {
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
        return invocations;
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
}
