package ru.feytox.etherology.registry.misc;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnchantmentRegistryIsolationTest {

    private static final String SHARED_ENCHANTMENTS =
            "ru/feytox/etherology/registry/misc/SharedEnchantments.class";
    private static final String PEAL_ENCHANTMENT =
            "ru/feytox/etherology/registry/misc/PealEnchantment.class";
    private static final String REFLECTION_ENCHANTMENT =
            "ru/feytox/etherology/registry/misc/ReflectionEnchantment.class";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String CANONICAL_POLICY =
            "ru/feytox/etherology/registry/misc/EtherEnchantments.class";
    private static final String SHOCKWAVE_UTIL =
            "ru/feytox/etherology/util/misc/ShockwaveUtil.class";
    private static final String ENCHANTMENT_TAG_GENERATION =
            "ru/feytox/etherology/client/datagen/EnchantmentTagGeneration.class";
    private static final String RU_LANG_GENERATION =
            "ru/feytox/etherology/client/datagen/RuLangGeneration.class";
    private static final String SHARED_ENCHANTMENTS_OWNER =
            "ru/feytox/etherology/registry/misc/SharedEnchantments";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void commonArtifactSolelyOwnsTheDeferredRegistryAndConcreteTypes()
            throws IOException {
        URL sharedOwnerResource = singleClasspathResource(SHARED_ENCHANTMENTS);
        assertEquals("jar", sharedOwnerResource.getProtocol());

        JarURLConnection connection = (JarURLConnection) sharedOwnerResource.openConnection();
        connection.setUseCaches(false);
        try (JarFile commonArtifact = connection.getJarFile()) {
            assertNotNull(commonArtifact.getJarEntry(SHARED_ENCHANTMENTS));
            assertNotNull(commonArtifact.getJarEntry(PEAL_ENCHANTMENT));
            assertNotNull(commonArtifact.getJarEntry(REFLECTION_ENCHANTMENT));
            assertNotNull(commonArtifact.getJarEntry(COMMON_BOOTSTRAP));
            assertFalse(commonArtifact.stream().anyMatch(
                    entry -> entry.getName().equals(CANONICAL_POLICY)
            ));
            assertEquals(
                    List.of("peal", "reflection"),
                    methodStringConstants(
                            artifactClass(commonArtifact, SHARED_ENCHANTMENTS),
                            "<clinit>"
                    )
            );
            assertEquals(
                    1,
                    invocationCount(
                            artifactClass(commonArtifact, COMMON_BOOTSTRAP),
                            "initialize",
                            SHARED_ENCHANTMENTS_OWNER,
                            "register"
                    )
            );
        }

        assertSameArtifact(sharedOwnerResource, singleClasspathResource(PEAL_ENCHANTMENT));
        assertSameArtifact(
                sharedOwnerResource,
                singleClasspathResource(REFLECTION_ENCHANTMENT)
        );
    }

    @Test
    void fabricInitializerDirectlyAttachesEnchantmentsInSharedRegistryOrder()
            throws IOException {
        List<String> invocations = methodInvocations(
                classpathClass(FABRIC_INITIALIZER),
                "initialize"
        );
        int lootConditions = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedLootConditions#register()V"
        );
        int enchantments = invocations.indexOf(
                SHARED_ENCHANTMENTS_OWNER + "#register()V"
        );
        int frequencyHook = invocations.indexOf(
                "ru/feytox/etherology/FabricGameEventHooks"
                        + "#registerSculkSensorFrequency()V"
        );

        assertTrue(lootConditions >= 0);
        assertEquals(lootConditions + 1, enchantments);
        assertEquals(enchantments + 1, frequencyHook);
        assertEquals(
                1,
                invocationCount(
                        classpathClass(FABRIC_INITIALIZER),
                        "initialize",
                        SHARED_ENCHANTMENTS_OWNER,
                        "register"
                )
        );
        assertEquals(
                0,
                ownerReferenceCount(
                        classpathClass(FABRIC_INITIALIZER),
                        "ru/feytox/etherology/bootstrap/EtherologyBootstrap"
                )
        );
    }

    @Test
    void canonicalPolicyRetainsNoEagerRegistryOwnership() throws IOException {
        ClassReader policy = classpathClass(CANONICAL_POLICY);
        List<String> fieldNames = fieldNames(policy);

        assertEquals(List.of("BANNED_ENCHANTMENTS"), fieldNames);
        assertEquals(0, ownerReferenceCount(policy, "net/minecraft/registry/Registry"));
        assertEquals(0, ownerReferenceCount(policy, "net/minecraft/registry/Registries"));
        assertEquals(
                Map.of("PEAL", 1, "REFLECTION", 1),
                sharedFieldReads(policy, "isAcceptableItem")
        );
        assertEquals(2, supplierResolutionCount(policy, "isAcceptableItem"));
        assertEquals(
                Map.of("REFLECTION", 1),
                sharedFieldReads(policy, "applyReflection")
        );
        assertEquals(1, supplierResolutionCount(policy, "applyReflection"));
        assertEquals(Map.of(), sharedFieldReads(policy, "<clinit>"));
        assertEquals(0, supplierResolutionCount(policy, "<clinit>"));
    }

    @Test
    void runtimeAndDatagenConsumersResolveSuppliersOnlyInsideUseMethods()
            throws IOException {
        assertUseTimeResolution(
                SHOCKWAVE_UTIL,
                "trySchedulePeal",
                Map.of("PEAL", 1)
        );
        assertUseTimeResolution(
                ENCHANTMENT_TAG_GENERATION,
                "configure",
                Map.of("PEAL", 1, "REFLECTION", 1)
        );
        assertUseTimeResolution(
                RU_LANG_GENERATION,
                "generateTranslations",
                Map.of("PEAL", 1, "REFLECTION", 1)
        );
    }

    private static void assertUseTimeResolution(
            String classResource,
            String useMethod,
            Map<String, Integer> expectedFieldReads
    ) throws IOException {
        ClassReader reader = classpathClass(classResource);
        assertEquals(expectedFieldReads, sharedFieldReads(reader, useMethod));
        assertEquals(
                expectedFieldReads.values().stream().mapToInt(Integer::intValue).sum(),
                supplierResolutionCount(reader, useMethod)
        );
        assertEquals(Map.of(), sharedFieldReads(reader, "<clinit>"));
        assertEquals(0, supplierResolutionCount(reader, "<clinit>"));
    }

    private static void assertSameArtifact(URL expectedOwner, URL actualOwner) {
        JarURLConnection expectedConnection = (JarURLConnection) open(expectedOwner);
        JarURLConnection actualConnection = (JarURLConnection) open(actualOwner);
        assertEquals(
                expectedConnection.getJarFileURL(),
                actualConnection.getJarFileURL()
        );
    }

    private static java.net.URLConnection open(URL resource) {
        try {
            return resource.openConnection();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ClassReader classpathClass(String classResource) throws IOException {
        InputStream classStream = EnchantmentRegistryIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(classResource);
        assertNotNull(classStream, "Missing classpath class " + classResource);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }

    private static ClassReader artifactClass(JarFile artifact, String classResource)
            throws IOException {
        InputStream classStream = artifact.getInputStream(artifact.getJarEntry(classResource));
        try (classStream) {
            return new ClassReader(classStream);
        }
    }

    private static URL singleClasspathResource(String classResource) throws IOException {
        Enumeration<URL> resources = EnchantmentRegistryIsolationTest.class
                .getClassLoader()
                .getResources(classResource);
        assertTrue(resources.hasMoreElements(), "Missing classpath class " + classResource);
        URL resource = resources.nextElement();
        assertFalse(
                resources.hasMoreElements(),
                "Multiple classpath owners provide " + classResource
        );
        return resource;
    }

    private static List<String> fieldNames(ClassReader reader) {
        List<String> names = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                names.add(name);
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return names;
    }

    private static Map<String, Integer> sharedFieldReads(
            ClassReader reader,
            String expectedMethodName
    ) {
        Map<String, Integer> reads = new LinkedHashMap<>();
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
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_ENCHANTMENTS_OWNER)) {
                            reads.merge(name, 1, Integer::sum);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return reads;
    }

    private static int supplierResolutionCount(
            ClassReader reader,
            String expectedMethodName
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
                    private boolean sharedEnchantmentLoaded;

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        sharedEnchantmentLoaded = opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_ENCHANTMENTS_OWNER);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (sharedEnchantmentLoaded
                                && owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            count.incrementAndGet();
                        }
                        sharedEnchantmentLoaded = false;
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
                        invocations.add(owner + "#" + name + descriptor);
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
        int count = 0;
        for (String invocation : methodInvocations(reader, expectedMethodName)) {
            int ownerSeparator = invocation.indexOf('#');
            int descriptorStart = invocation.indexOf('(', ownerSeparator);
            String owner = invocation.substring(0, ownerSeparator);
            String name = invocation.substring(ownerSeparator + 1, descriptorStart);
            if (owner.equals(expectedOwner) && name.equals(expectedInvocationName)) {
                count++;
            }
        }
        return count;
    }

    private static int ownerReferenceCount(ClassReader reader, String expectedOwner) {
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
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (owner.equals(expectedOwner)) {
                            count.incrementAndGet();
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
                        if (owner.equals(expectedOwner)) {
                            count.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count.get();
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
}
