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
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedLootConditionsBytecodeTest {

    private static final String SHARED_LOOT_CONDITIONS =
            "ru/feytox/etherology/registry/misc/SharedLootConditions";
    private static final String SHARED_LOOT_CONDITIONS_CLASS =
            "/" + SHARED_LOOT_CONDITIONS + ".class";
    private static final String CONDITION =
            "ru/feytox/etherology/util/misc/RandomChanceWithFortuneCondition";
    private static final String CONDITION_CLASS = "/" + CONDITION + ".class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String SHARED_DEFERRED_REGISTER_DESCRIPTOR =
            "L" + SHARED_DEFERRED_REGISTER + ";";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";
    private static final String LOOT_CONDITION_TYPE =
            "net/minecraft/loot/condition/LootConditionType";
    private static final String LOOT_CONDITION_TYPE_SIGNATURE =
            "L" + LOOT_CONDITION_TYPE + ";";

    @Test
    void declaresTheSingleCanonicalDeferredLootCondition() throws IOException {
        AtomicInteger classAccess = new AtomicInteger();
        Map<String, Integer> fieldAccess = new LinkedHashMap<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        Map<String, String> fieldSignatures = new LinkedHashMap<>();
        Map<String, Integer> methodAccess = new LinkedHashMap<>();
        List<String> publicMethods = new ArrayList<>();
        AtomicInteger registryKeyReads = new AtomicInteger();
        AtomicInteger canonicalIdLoads = new AtomicInteger();
        reader(SHARED_LOOT_CONDITIONS_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals("net/minecraft/registry/RegistryKeys")
                                && fieldName.equals("LOOT_CONDITION_TYPE")) {
                            registryKeyReads.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if ("random_chance_with_fortune".equals(value)) {
                            canonicalIdLoads.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                classAccess.get()
        );
        assertEquals(
                List.of("LOOT_CONDITIONS", "RANDOM_CHANCE_WITH_FORTUNE"),
                new ArrayList<>(fieldDescriptors.keySet())
        );
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("LOOT_CONDITIONS")
        );
        assertEquals(
                SHARED_DEFERRED_REGISTER_DESCRIPTOR,
                fieldDescriptors.get("LOOT_CONDITIONS")
        );
        assertEquals(
                "L" + SHARED_DEFERRED_REGISTER + "<" + LOOT_CONDITION_TYPE_SIGNATURE + ">;",
                fieldSignatures.get("LOOT_CONDITIONS")
        );
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("RANDOM_CHANCE_WITH_FORTUNE")
        );
        assertEquals(
                REGISTRY_SUPPLIER_DESCRIPTOR,
                fieldDescriptors.get("RANDOM_CHANCE_WITH_FORTUNE")
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier<"
                        + LOOT_CONDITION_TYPE_SIGNATURE + ">;",
                fieldSignatures.get("RANDOM_CHANCE_WITH_FORTUNE")
        );
        assertEquals(Opcodes.ACC_PRIVATE, methodAccess.get("<init>()V"));
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                methodAccess.get("register()V")
        );
        assertEquals(List.of("register()V"), publicMethods);
        assertEquals(1, registryKeyReads.get());
        assertEquals(1, canonicalIdLoads.get());

        Map<String, List<String>> invocations = invocationsByMethod(
                SHARED_LOOT_CONDITIONS_CLASS
        );
        List<String> allInvocations = invocations.values().stream()
                .flatMap(List::stream)
                .toList();
        assertEquals(1, countNamed(allInvocations, SHARED_DEFERRED_REGISTER, "create"));
        assertEquals(1, countNamed(allInvocations, SHARED_DEFERRED_REGISTER, "register"));
        assertEquals(1, countNamed(
                allInvocations,
                LOOT_CONDITION_TYPE,
                "<init>"
        ));
        assertEquals(1, countNamed(
                allInvocations,
                "ru/feytox/etherology/util/misc/RandomChanceWithFortuneConditionSerializer",
                "<init>"
        ));
        assertEquals(0, countNamed(allInvocations, null, "get"));
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                invocations.get("register()V")
        );
    }

    @Test
    void resolvesTheSupplierOnlyWhenTheConditionReportsItsType() throws IOException {
        Map<String, List<String>> invocations = invocationsByMethod(CONDITION_CLASS);
        List<String> supplierReads = invocations.values().stream()
                .flatMap(List::stream)
                .filter(invocation -> invocation.equals(
                        "dev/architectury/registry/registries/RegistrySupplier#get()Ljava/lang/Object;"
                ))
                .toList();

        assertEquals(1, supplierReads.size());
        assertTrue(invocations.get(
                "getType()Lnet/minecraft/loot/condition/LootConditionType;"
        ).contains(supplierReads.get(0)));
    }

    @Test
    void attachesAfterGameEventsAndBeforeEnchantments() throws IOException {
        List<String> invocations = invocationsByMethod(BOOTSTRAP_CLASS).get(
                "initialize(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );
        int gameEvents = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedGameEvents#register()V"
        );
        int lootConditions = invocations.indexOf(
                SHARED_LOOT_CONDITIONS + "#register()V"
        );
        int enchantments = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedEnchantments#register()V"
        );
        int resourceReloaders = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/ResourceReloaders"
                        + "#registerServerData()V"
        );
        int lifecycle = invocations.indexOf(
                "ru/feytox/etherology/bootstrap/BootstrapLifecycle#initialize"
                        + "(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );

        assertTrue(gameEvents >= 0);
        assertEquals(gameEvents + 1, lootConditions);
        assertEquals(lootConditions + 1, enchantments);
        assertEquals(enchantments + 1, resourceReloaders);
        assertEquals(resourceReloaders + 1, lifecycle);
        assertEquals(1, count(invocations, SHARED_LOOT_CONDITIONS + "#register()V"));
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
                            String methodName,
                            String methodDescriptor,
                            boolean isInterface
                    ) {
                        methodInvocations.add(owner + "#" + methodName + methodDescriptor);
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

    private static ClassReader reader(String classResource) throws IOException {
        InputStream classStream = SharedLootConditionsBytecodeTest.class.getResourceAsStream(
                classResource
        );
        assertNotNull(classStream, "Missing class resource " + classResource);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }
}
