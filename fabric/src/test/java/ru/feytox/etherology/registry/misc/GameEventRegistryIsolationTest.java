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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GameEventRegistryIsolationTest {

    private static final String LEGACY_EVENTS_REGISTRY =
            "ru/feytox/etherology/registry/misc/EventsRegistry.class";
    private static final String COMMON_BOOTSTRAP =
            "ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String FABRIC_ENTRYPOINT =
            "ru/feytox/etherology/EtherologyFabric.class";
    private static final String FABRIC_GAME_EVENT_HOOKS =
            "ru/feytox/etherology/FabricGameEventHooks.class";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String GAME_EVENT_TAG_GENERATION =
            "ru/feytox/etherology/client/datagen/GameEventTagGeneration.class";
    private static final String SHARED_GAME_EVENTS =
            "ru/feytox/etherology/registry/misc/SharedGameEvents.class";
    private static final String TUNING_FORK_BLOCK_ENTITY =
            "ru/feytox/etherology/block/tuningFork/TuningForkBlockEntity.class";
    private static final String FABRIC_FREQUENCY_OWNER =
            "net/fabricmc/fabric/api/registry/SculkSensorFrequencyRegistry";
    private static final String SHARED_GAME_EVENTS_OWNER =
            "ru/feytox/etherology/registry/misc/SharedGameEvents";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void commonArtifactIsTheSoleSharedDeclarationOwner() throws IOException {
        URL sharedOwnerResource = singleClasspathResource(SHARED_GAME_EVENTS);
        assertEquals("jar", sharedOwnerResource.getProtocol());

        JarURLConnection connection = (JarURLConnection) sharedOwnerResource.openConnection();
        connection.setUseCaches(false);
        try (JarFile commonArtifact = connection.getJarFile()) {
            assertNotNull(commonArtifact.getJarEntry(SHARED_GAME_EVENTS));
            assertNotNull(commonArtifact.getJarEntry(COMMON_BOOTSTRAP));
            assertNull(commonArtifact.getJarEntry(FABRIC_GAME_EVENT_HOOKS));
            assertNull(commonArtifact.getJarEntry(LEGACY_EVENTS_REGISTRY));

            ClassReader sharedOwner = artifactClass(commonArtifact, SHARED_GAME_EVENTS);
            assertEquals(
                    List.of("etherology_resonance"),
                    methodStringConstants(sharedOwner, "<clinit>")
            );
            assertEquals(List.of(16), methodIntegerConstants(sharedOwner, "<clinit>"));
            assertEquals(
                    1,
                    invocationCount(
                            artifactClass(commonArtifact, COMMON_BOOTSTRAP),
                            "initialize",
                            SHARED_GAME_EVENTS_OWNER,
                            "register"
                    )
            );
        }
    }

    @Test
    void fabricInitializerAttachesAndHooksTheSharedEventInOrder() throws IOException {
        ClassReader initializer = classpathClass(FABRIC_INITIALIZER);
        List<String> initializationInvocations = methodInvocations(
                initializer,
                "initialize"
        );
        int soundAttachment = initializationInvocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedSounds#register()V"
        );
        int gameEventAttachment = initializationInvocations.indexOf(
                SHARED_GAME_EVENTS_OWNER + "#register()V"
        );
        int lootConditionAttachment = initializationInvocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedLootConditions#register()V"
        );
        int frequencyHook = initializationInvocations.indexOf(
                "ru/feytox/etherology/FabricGameEventHooks"
                        + "#registerSculkSensorFrequency()V"
        );

        assertTrue(soundAttachment >= 0);
        assertEquals(soundAttachment + 1, gameEventAttachment);
        assertEquals(gameEventAttachment + 1, lootConditionAttachment);
        assertEquals(lootConditionAttachment + 1, frequencyHook);
        assertEquals(1, count(
                initializationInvocations,
                SHARED_GAME_EVENTS_OWNER + "#register()V"
        ));
        assertEquals(1, count(
                initializationInvocations,
                "ru/feytox/etherology/FabricGameEventHooks"
                        + "#registerSculkSensorFrequency()V"
        ));
        assertEquals(
                1,
                invocationCount(
                        classpathClass(FABRIC_ENTRYPOINT),
                        "onInitialize",
                        "ru/feytox/etherology/Etherology",
                        "initialize"
                )
        );
        assertEquals(
                0,
                ownerReferenceCount(
                        initializer,
                        "ru/feytox/etherology/bootstrap/EtherologyBootstrap"
                )
        );
    }

    @Test
    void fabricHookBindsOnlySharedResonanceAtFrequencyTen() throws IOException {
        ClassReader hook = classpathClass(FABRIC_GAME_EVENT_HOOKS);
        AtomicInteger classAccess = new AtomicInteger();
        List<String> methods = new ArrayList<>();

        hook.accept(new ClassVisitor(Opcodes.ASM9) {
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
                methods.add(access + ":" + name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertFalse((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(
                List.of(
                        Opcodes.ACC_PRIVATE + ":<init>()V",
                        Opcodes.ACC_STATIC + ":registerSculkSensorFrequency()V"
                ),
                methods
        );
        assertEquals(
                1,
                fieldReferenceCount(
                        hook,
                        "registerSculkSensorFrequency",
                        SHARED_GAME_EVENTS_OWNER,
                        "RESONANCE"
                )
        );
        assertEquals(
                List.of(
                        REGISTRY_SUPPLIER_OWNER + "#get()Ljava/lang/Object;",
                        FABRIC_FREQUENCY_OWNER
                                + "#register(Lnet/minecraft/world/event/GameEvent;I)V"
                ),
                methodInvocations(hook, "registerSculkSensorFrequency")
        );
        assertEquals(
                List.of(10),
                methodIntegerConstants(hook, "registerSculkSensorFrequency")
        );
        assertEquals(0, ownerReferenceCount(hook, "net/minecraft/registry/Registry"));
    }

    @Test
    void canonicalConsumersResolveTheSharedSupplierAtUseTime() throws IOException {
        ClassReader tuningFork = classpathClass(TUNING_FORK_BLOCK_ENTITY);
        assertEquals(
                2,
                fieldReferenceCount(
                        tuningFork,
                        null,
                        SHARED_GAME_EVENTS_OWNER,
                        "RESONANCE"
                )
        );
        assertEquals(
                2,
                supplierResolutionCount(tuningFork, null)
        );
        assertEquals(
                0,
                invocationCount(
                        tuningFork,
                        "getRange",
                        "net/minecraft/world/event/GameEvent",
                        "getRange"
                )
        );
        assertEquals(List.of(16), methodIntegerConstants(tuningFork, "getRange"));

        ClassReader tagGeneration = classpathClass(GAME_EVENT_TAG_GENERATION);
        assertEquals(
                1,
                fieldReferenceCount(
                        tagGeneration,
                        "configure",
                        SHARED_GAME_EVENTS_OWNER,
                        "RESONANCE"
                )
        );
        assertEquals(
                1,
                supplierResolutionCount(tagGeneration, "configure")
        );
        assertEquals(
                0,
                ownerReferenceCount(
                        tuningFork,
                        "ru/feytox/etherology/registry/misc/EventsRegistry"
                )
        );
        assertEquals(
                0,
                ownerReferenceCount(
                        tagGeneration,
                        "ru/feytox/etherology/registry/misc/EventsRegistry"
                )
        );
    }

    @Test
    void legacyEagerRegistryHasNoFabricClasspathOwner() {
        assertNull(
                GameEventRegistryIsolationTest.class
                        .getClassLoader()
                        .getResource(LEGACY_EVENTS_REGISTRY)
        );
    }

    private static ClassReader classpathClass(String classResource) throws IOException {
        InputStream classStream = GameEventRegistryIsolationTest.class
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
        Enumeration<URL> resources = GameEventRegistryIsolationTest.class
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

    private static int fieldReferenceCount(
            ClassReader reader,
            String expectedMethodName,
            String expectedOwner,
            String expectedFieldName
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
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (owner.equals(expectedOwner) && name.equals(expectedFieldName)) {
                            count.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count.get();
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
                if (expectedMethodName != null && !name.equals(expectedMethodName)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean sharedSupplierLoaded;

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        sharedSupplierLoaded = opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_GAME_EVENTS_OWNER)
                                && name.equals("RESONANCE");
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (sharedSupplierLoaded
                                && owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            count.incrementAndGet();
                        }
                        sharedSupplierLoaded = false;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count.get();
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

    private static List<Integer> methodIntegerConstants(
            ClassReader reader,
            String expectedMethodName
    ) {
        List<Integer> constants = new ArrayList<>();
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
                    public void visitIntInsn(int opcode, int operand) {
                        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                            constants.add(operand);
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Integer integerValue) {
                            constants.add(integerValue);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return constants;
    }

    private static int count(List<String> values, String expectedValue) {
        int count = 0;
        for (String value : values) {
            if (value.equals(expectedValue)) {
                count++;
            }
        }
        return count;
    }
}
