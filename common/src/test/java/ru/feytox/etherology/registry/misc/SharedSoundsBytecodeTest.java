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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedSoundsBytecodeTest {

    private static final String SHARED_SOUNDS_CLASS =
            "/ru/feytox/etherology/registry/misc/SharedSounds.class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String SHARED_SOUNDS =
            "ru/feytox/etherology/registry/misc/SharedSounds";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";
    private static final String REGISTRY_SUPPLIER_SIGNATURE =
            "Ldev/architectury/registry/registries/RegistrySupplier"
                    + "<Lnet/minecraft/sound/SoundEvent;>;";

    @Test
    void declaresOnlyTheExactCanonicalPublicSoundSuppliers() throws IOException {
        Map<String, String> expectedRegistrations = expectedRegistrations();
        Map<String, String> publicFields = new LinkedHashMap<>();
        Map<String, Integer> fieldAccess = new LinkedHashMap<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        Map<String, String> fieldSignatures = new LinkedHashMap<>();
        Map<String, String> registrations = new LinkedHashMap<>();
        List<String> registrationOrder = new ArrayList<>();
        List<String> publicMethods = new ArrayList<>();
        AtomicInteger classAccess = new AtomicInteger();

        reader(SHARED_SOUNDS_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                if ((access & Opcodes.ACC_PUBLIC) != 0) {
                    publicFields.put(name, descriptor);
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
                if ((access & Opcodes.ACC_PUBLIC) != 0) {
                    publicMethods.add(name + descriptor);
                }
                if (!name.equals("<clinit>")) {
                    return null;
                }

                return new MethodVisitor(Opcodes.ASM9) {
                    private String loadedId;
                    private String registeredId;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            loadedId = stringValue;
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
                        if (owner.equals(SHARED_SOUNDS)
                                && name.equals("register")
                                && descriptor.equals(
                                        "(Ljava/lang/String;)"
                                                + REGISTRY_SUPPLIER_DESCRIPTOR
                                )) {
                            registeredId = loadedId;
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode != Opcodes.PUTSTATIC
                                || !owner.equals(SHARED_SOUNDS)
                                || !expectedRegistrations.containsKey(name)) {
                            return;
                        }

                        assertNull(
                                registrations.put(name, registeredId),
                                "Duplicate registration assignment for " + name
                        );
                        registrationOrder.add(name);
                        loadedId = null;
                        registeredId = null;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        List<String> expectedFields = new ArrayList<>();
        expectedFields.add("SOUNDS");
        expectedFields.addAll(expectedRegistrations.keySet());
        assertEquals(expectedFields, new ArrayList<>(fieldDescriptors.keySet()));
        assertEquals(expectedRegistrations.keySet(), publicFields.keySet());
        assertTrue(publicFields.values().stream().allMatch(REGISTRY_SUPPLIER_DESCRIPTOR::equals));
        for (String fieldName : expectedRegistrations.keySet()) {
            assertEquals(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    fieldAccess.get(fieldName)
            );
            assertEquals(REGISTRY_SUPPLIER_SIGNATURE, fieldSignatures.get(fieldName));
        }
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("SOUNDS")
        );
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                fieldDescriptors.get("SOUNDS")
        );
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister"
                        + "<Lnet/minecraft/sound/SoundEvent;>;",
                fieldSignatures.get("SOUNDS")
        );
        assertEquals(expectedRegistrations, registrations);
        assertEquals(new ArrayList<>(expectedRegistrations.keySet()), registrationOrder);
        assertEquals(List.of("register()V"), publicMethods);
    }

    @Test
    void usesTheExactDeferredSoundFactoriesWithoutResolvingASupplier() throws IOException {
        Map<String, List<String>> invocations = invocationsByMethod(SHARED_SOUNDS_CLASS);
        List<String> allInvocations = invocations.values().stream()
                .flatMap(List::stream)
                .toList();

        assertEquals(
                14,
                count(
                        invocations.get("<clinit>()V"),
                        SHARED_SOUNDS + "#register(Ljava/lang/String;)"
                                + REGISTRY_SUPPLIER_DESCRIPTOR
                )
        );
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
        assertEquals(0, countNamed(allInvocations, null, "get"));

        String soundFactoryMethod = invocations.entrySet().stream()
                .filter(entry -> entry.getValue().contains(
                        "net/minecraft/util/Identifier#of"
                                + "(Ljava/lang/String;Ljava/lang/String;)"
                                + "Lnet/minecraft/util/Identifier;"
                ))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
        List<String> soundFactoryInvocations = invocations.get(soundFactoryMethod);
        assertEquals(
                List.of(
                        "net/minecraft/util/Identifier#of"
                                + "(Ljava/lang/String;Ljava/lang/String;)"
                                + "Lnet/minecraft/util/Identifier;",
                        "net/minecraft/sound/SoundEvent#of"
                                + "(Lnet/minecraft/util/Identifier;)"
                                + "Lnet/minecraft/sound/SoundEvent;"
                ),
                soundFactoryInvocations
        );
        assertTrue(loadsString(SHARED_SOUNDS_CLASS, soundFactoryMethod, "etherology"));
        assertReadsSoundEventRegistryKey();
    }

    @Test
    void attachesSoundsBetweenMenusAndTheLifecycleHandshake() throws IOException {
        assertEquals(
                List.of(
                        "ru/feytox/etherology/registry/block/SharedBlocks#register()V",
                        "ru/feytox/etherology/registry/item/SharedItems#register()V",
                        "ru/feytox/etherology/registry/block/SharedBlockEntities#register()V",
                        "ru/feytox/etherology/registry/misc/SharedScreenHandlers#register()V",
                        SHARED_SOUNDS + "#register()V",
                        "ru/feytox/etherology/registry/misc/SharedGameEvents#register()V",
                        "ru/feytox/etherology/registry/misc/SharedLootConditions#register()V",
                        "ru/feytox/etherology/registry/misc/SharedEnchantments#register()V",
                        "ru/feytox/etherology/registry/particle/SharedParticleTypes#register()V",
                        "ru/feytox/etherology/registry/misc/ResourceReloaders"
                                + "#registerServerData()V",
                        "ru/feytox/etherology/bootstrap/BootstrapLifecycle#initialize"
                                + "(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
                ),
                invocationsByMethod(BOOTSTRAP_CLASS).get(
                        "initialize(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
                )
        );
    }

    @Test
    void leavesTheCanonicalFabricSoundRegistryOutOfTheCommonArtifact() {
        assertNull(SharedSoundsBytecodeTest.class.getResource(
                "/ru/feytox/etherology/registry/misc/EtherSounds.class"
        ));
    }

    private static Map<String, String> expectedRegistrations() {
        Map<String, String> registrations = new LinkedHashMap<>();
        registrations.put("ELECTRICITY", "electricity_sound");
        registrations.put("MATRIX_WORK", "matrix_idle_sound");
        registrations.put("DEFLECT", "deflect");
        registrations.put("BUBBLES", "bubbles");
        registrations.put("POUF", "pouf");
        registrations.put("RATCHET", "ratchet");
        registrations.put("BREWING_DISSOLUTION", "brewing_dissolution");
        registrations.put("THUNDER_ZAP", "thunder_zap");
        registrations.put("TUNING_MACE", "tuning_mace");
        registrations.put("TUNING_FORK_ACTIVATE", "tuning_fork_activate");
        registrations.put("TUNING_FORK_TUNING", "tuning_fork_tuning");
        registrations.put("TUNING_FORK_RESONANCE", "tuning_fork_resonance");
        registrations.put("BROADSWORD", "broadsword");
        registrations.put("WARP_COUNTER", "warp_counter");
        return registrations;
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

    private static boolean loadsString(
            String classResource,
            String methodKey,
            String expectedValue
    ) throws IOException {
        AtomicInteger matches = new AtomicInteger();
        reader(classResource).accept(new ClassVisitor(Opcodes.ASM9) {
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
                    public void visitLdcInsn(Object value) {
                        if (expectedValue.equals(value)) {
                            matches.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return matches.get() == 1;
    }

    private static void assertReadsSoundEventRegistryKey() throws IOException {
        AtomicInteger registryKeyReads = new AtomicInteger();
        reader(SHARED_SOUNDS_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals("net/minecraft/registry/RegistryKeys")
                                && name.equals("SOUND_EVENT")) {
                            registryKeyReads.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, registryKeyReads.get());
    }

    private static ClassReader reader(String classResource) throws IOException {
        InputStream classStream = SharedSoundsBytecodeTest.class.getResourceAsStream(
                classResource
        );
        assertNotNull(classStream, "Missing class resource " + classResource);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }
}
