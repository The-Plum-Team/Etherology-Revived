package ru.feytox.etherology.registry.misc;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedGameEventsBytecodeTest {

    private static final String SHARED_GAME_EVENTS_CLASS =
            "/ru/feytox/etherology/registry/misc/SharedGameEvents.class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String SHARED_GAME_EVENTS =
            "ru/feytox/etherology/registry/misc/SharedGameEvents";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";
    private static final String REGISTRY_SUPPLIER_SIGNATURE =
            "Ldev/architectury/registry/registries/RegistrySupplier"
                    + "<Lnet/minecraft/world/event/GameEvent;>;";

    @Test
    void declaresOnlyTheCanonicalResonanceSupplier() throws IOException {
        Map<String, Integer> fieldAccess = new LinkedHashMap<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        Map<String, String> fieldSignatures = new LinkedHashMap<>();
        List<String> publicMethods = new ArrayList<>();
        AtomicInteger classAccess = new AtomicInteger();

        reader(SHARED_GAME_EVENTS_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
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
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(List.of("GAME_EVENTS", "RESONANCE"),
                new ArrayList<>(fieldDescriptors.keySet()));
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
        assertEquals(REGISTRY_SUPPLIER_DESCRIPTOR, fieldDescriptors.get("RESONANCE"));
        assertEquals(REGISTRY_SUPPLIER_SIGNATURE, fieldSignatures.get("RESONANCE"));
        assertEquals(List.of("register()V"), publicMethods);
    }

    @Test
    void defersTheExactRegistryPathInternalIdAndRange() throws IOException {
        AtomicReference<String> registrationPath = new AtomicReference<>();
        AtomicInteger registrationRange = new AtomicInteger(-1);
        AtomicInteger registryKeyReads = new AtomicInteger();

        reader(SHARED_GAME_EVENTS_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    private String loadedString;
                    private int loadedInteger = -1;

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals("net/minecraft/registry/RegistryKeys")
                                && name.equals("GAME_EVENT")) {
                            registryKeyReads.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            loadedString = stringValue;
                        }
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                            loadedInteger = operand;
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
                        if (owner.equals(SHARED_GAME_EVENTS)
                                && name.equals("register")
                                && descriptor.equals(
                                        "(Ljava/lang/String;I)"
                                                + REGISTRY_SUPPLIER_DESCRIPTOR
                                )) {
                            registrationPath.set(loadedString);
                            registrationRange.set(loadedInteger);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(1, registryKeyReads.get());
        assertEquals("etherology_resonance", registrationPath.get());
        assertEquals(16, registrationRange.get());

        Map<String, List<String>> invocations = invocationsByMethod(
                SHARED_GAME_EVENTS_CLASS
        );
        List<String> allInvocations = invocations.values().stream()
                .flatMap(List::stream)
                .toList();
        assertEquals(
                1,
                countNamed(allInvocations, SHARED_DEFERRED_REGISTER, "create")
        );
        assertEquals(
                1,
                countNamed(allInvocations, SHARED_DEFERRED_REGISTER, "register")
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                invocations.get("register()V")
        );
        assertEquals(
                List.of(
                        "net/minecraft/world/event/GameEvent#<init>"
                                + "(Ljava/lang/String;I)V"
                ),
                invocations.get(
                        "lambda$register$0(Ljava/lang/String;I)"
                                + "Lnet/minecraft/world/event/GameEvent;"
                )
        );
        assertEquals(
                List.of("ALOAD:0", "ALOAD:0", "ILOAD:1"),
                methodVariableLoads(
                        SHARED_GAME_EVENTS_CLASS,
                        "register(Ljava/lang/String;I)"
                                + REGISTRY_SUPPLIER_DESCRIPTOR
                )
        );
        assertEquals(
                List.of("ALOAD:0", "ILOAD:1"),
                methodVariableLoads(
                        SHARED_GAME_EVENTS_CLASS,
                        "lambda$register$0(Ljava/lang/String;I)"
                                + "Lnet/minecraft/world/event/GameEvent;"
                )
        );
        assertFalse(methodLoadsConstants(
                SHARED_GAME_EVENTS_CLASS,
                "lambda$register$0(Ljava/lang/String;I)"
                        + "Lnet/minecraft/world/event/GameEvent;"
        ));
        assertEquals(0, countNamed(allInvocations, null, "get"));
        assertEquals(0, countNamed(
                allInvocations,
                "net/minecraft/registry/Registry",
                "register"
        ));
    }

    @Test
    void attachesAfterSoundsAndBeforeLootConditions() throws IOException {
        List<String> invocations = invocationsByMethod(BOOTSTRAP_CLASS).get(
                "initialize(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );
        int soundAttachment = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedSounds#register()V"
        );
        int gameEventAttachment = invocations.indexOf(
                SHARED_GAME_EVENTS + "#register()V"
        );
        int lootConditionAttachment = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedLootConditions#register()V"
        );

        assertTrue(soundAttachment >= 0);
        assertEquals(soundAttachment + 1, gameEventAttachment);
        assertEquals(gameEventAttachment + 1, lootConditionAttachment);
        assertEquals(1, count(invocations, SHARED_GAME_EVENTS + "#register()V"));
    }

    @Test
    void leavesTheLegacyFabricRegistryOutOfTheCommonArtifact() {
        assertNull(SharedGameEventsBytecodeTest.class.getResource(
                "/ru/feytox/etherology/registry/misc/EventsRegistry.class"
        ));
        assertFalse(classReferencesOwner(
                SHARED_GAME_EVENTS_CLASS,
                "net/fabricmc/fabric/api/registry/SculkSensorFrequencyRegistry"
        ));
    }

    private static Map<String, List<String>> invocationsByMethod(String classResource)
            throws IOException {
        Map<String, List<String>> invocations = new LinkedHashMap<>();
        reader(classResource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                List<String> methodInvocations = new ArrayList<>();
                invocations.put(name + descriptor, methodInvocations);
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

    private static int count(List<String> invocations, String expectedInvocation) {
        int count = 0;
        for (String invocation : invocations) {
            if (invocation.equals(expectedInvocation)) {
                count++;
            }
        }
        return count;
    }

    private static int countNamed(
            List<String> invocations,
            String expectedOwner,
            String expectedName
    ) {
        int count = 0;
        for (String invocation : invocations) {
            int ownerSeparator = invocation.indexOf('#');
            int descriptorStart = invocation.indexOf('(', ownerSeparator);
            String owner = invocation.substring(0, ownerSeparator);
            String name = invocation.substring(ownerSeparator + 1, descriptorStart);
            if ((expectedOwner == null || owner.equals(expectedOwner))
                    && name.equals(expectedName)) {
                count++;
            }
        }
        return count;
    }

    private static boolean classReferencesOwner(
            String classResource,
            String expectedOwner
    ) {
        AtomicInteger references = new AtomicInteger();
        try {
            reader(classResource).accept(new ClassVisitor(Opcodes.ASM9) {
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
                            if (owner.equals(expectedOwner)) {
                                references.incrementAndGet();
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        return references.get() > 0;
    }

    private static List<String> methodVariableLoads(
            String classResource,
            String expectedMethodKey
    ) throws IOException {
        List<String> variableLoads = new ArrayList<>();
        reader(classResource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!(name + descriptor).equals(expectedMethodKey)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitVarInsn(int opcode, int variableIndex) {
                        if (opcode == Opcodes.ALOAD) {
                            variableLoads.add("ALOAD:" + variableIndex);
                        } else if (opcode == Opcodes.ILOAD) {
                            variableLoads.add("ILOAD:" + variableIndex);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return variableLoads;
    }

    private static boolean methodLoadsConstants(
            String classResource,
            String expectedMethodKey
    ) throws IOException {
        AtomicInteger constants = new AtomicInteger();
        reader(classResource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!(name + descriptor).equals(expectedMethodKey)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                            constants.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        constants.incrementAndGet();
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        constants.incrementAndGet();
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return constants.get() > 0;
    }

    private static ClassReader reader(String classResource) throws IOException {
        InputStream classStream = SharedGameEventsBytecodeTest.class.getResourceAsStream(
                classResource
        );
        assertNotNull(classStream, "Missing class resource " + classResource);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }
}
