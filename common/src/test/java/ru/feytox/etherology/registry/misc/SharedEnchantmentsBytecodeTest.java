package ru.feytox.etherology.registry.misc;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedEnchantmentsBytecodeTest {

    private static final String SHARED_ENCHANTMENTS_CLASS =
            "/ru/feytox/etherology/registry/misc/SharedEnchantments.class";
    private static final String PEAL_ENCHANTMENT_CLASS =
            "/ru/feytox/etherology/registry/misc/PealEnchantment.class";
    private static final String REFLECTION_ENCHANTMENT_CLASS =
            "/ru/feytox/etherology/registry/misc/ReflectionEnchantment.class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String SHARED_ENCHANTMENTS =
            "ru/feytox/etherology/registry/misc/SharedEnchantments";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";
    private static final String REGISTRY_SUPPLIER_SIGNATURE =
            "Ldev/architectury/registry/registries/RegistrySupplier"
                    + "<Lnet/minecraft/enchantment/Enchantment;>;";

    @Test
    void declaresOnlyTheExactCanonicalSuppliers() throws IOException {
        Map<String, Integer> fieldAccess = new LinkedHashMap<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        Map<String, String> fieldSignatures = new LinkedHashMap<>();
        Map<String, String> registrations = new LinkedHashMap<>();
        List<String> publicMethods = new ArrayList<>();
        AtomicInteger classAccess = new AtomicInteger();

        reader(SHARED_ENCHANTMENTS_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        if (owner.equals(SHARED_DEFERRED_REGISTER)
                                && name.equals("register")) {
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
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(SHARED_ENCHANTMENTS)
                                && (name.equals("PEAL") || name.equals("REFLECTION"))) {
                            registrations.put(name, registeredId);
                            loadedId = null;
                            registeredId = null;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(
                List.of("ENCHANTMENTS", "PEAL", "REFLECTION"),
                new ArrayList<>(fieldDescriptors.keySet())
        );
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("ENCHANTMENTS")
        );
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                fieldDescriptors.get("ENCHANTMENTS")
        );
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister"
                        + "<Lnet/minecraft/enchantment/Enchantment;>;",
                fieldSignatures.get("ENCHANTMENTS")
        );
        for (String fieldName : List.of("PEAL", "REFLECTION")) {
            assertEquals(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    fieldAccess.get(fieldName)
            );
            assertEquals(REGISTRY_SUPPLIER_DESCRIPTOR, fieldDescriptors.get(fieldName));
            assertEquals(REGISTRY_SUPPLIER_SIGNATURE, fieldSignatures.get(fieldName));
        }
        assertEquals(
                Map.of("PEAL", "peal", "REFLECTION", "reflection"),
                registrations
        );
        assertEquals(List.of("register()V"), publicMethods);
    }

    @Test
    void defersBothConcreteFactoriesWithoutResolvingThem() throws IOException {
        Map<String, List<String>> invocations = invocationsByMethod(
                SHARED_ENCHANTMENTS_CLASS
        );
        List<String> allInvocations = invocations.values().stream()
                .flatMap(List::stream)
                .toList();

        assertEquals(1, countNamed(allInvocations, SHARED_DEFERRED_REGISTER, "create"));
        assertEquals(2, countNamed(allInvocations, SHARED_DEFERRED_REGISTER, "register"));
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                invocations.get("register()V")
        );
        assertEquals(0, countNamed(allInvocations, null, "get"));
        assertEquals(
                0,
                countNamed(allInvocations, "net/minecraft/registry/Registry", "register")
        );
        assertEquals(
                1,
                countNamed(
                        allInvocations,
                        "ru/feytox/etherology/registry/misc/PealEnchantment",
                        "<init>"
                )
        );
        assertEquals(
                1,
                countNamed(
                        allInvocations,
                        "ru/feytox/etherology/registry/misc/ReflectionEnchantment",
                        "<init>"
                )
        );
        assertReadsEnchantmentRegistryKey();
    }

    @Test
    void preservesTheCanonicalConcreteEnchantmentRules() throws IOException {
        PealEnchantment peal = new PealEnchantment();
        ReflectionEnchantment reflection = new ReflectionEnchantment();

        assertEquals(Enchantment.Rarity.COMMON, peal.getRarity());
        assertSame(EnchantmentTarget.WEAPON, peal.target);
        assertEquals(3, peal.getMaxLevel());
        assertEquals(1, peal.getMinPower(1));
        assertEquals(12, peal.getMinPower(2));
        assertEquals(23, peal.getMinPower(3));
        assertEquals(21, peal.getMaxPower(1));
        assertEquals(32, peal.getMaxPower(2));
        assertEquals(43, peal.getMaxPower(3));

        assertEquals(Enchantment.Rarity.COMMON, reflection.getRarity());
        assertSame(EnchantmentTarget.BREAKABLE, reflection.target);
        assertEquals(1, reflection.getMaxLevel());
        assertEquals(1, reflection.getMinPower(1));
        assertEquals(1, reflection.getMinPower(99));
        assertEquals(21, reflection.getMaxPower(1));
        assertEquals(21, reflection.getMaxPower(99));

        assertEquals(
                List.of(
                        "net/minecraft/enchantment/Enchantment$Rarity#COMMON",
                        "net/minecraft/enchantment/EnchantmentTarget#WEAPON",
                        "net/minecraft/entity/EquipmentSlot#MAINHAND"
                ),
                constructorFieldReads(PEAL_ENCHANTMENT_CLASS)
        );
        assertEquals(
                List.of(
                        "net/minecraft/enchantment/Enchantment$Rarity#COMMON",
                        "net/minecraft/enchantment/EnchantmentTarget#BREAKABLE",
                        "net/minecraft/entity/EquipmentSlot#MAINHAND",
                        "net/minecraft/entity/EquipmentSlot#OFFHAND"
                ),
                constructorFieldReads(REFLECTION_ENCHANTMENT_CLASS)
        );
    }

    @Test
    void attachesBetweenLootConditionsAndParticleTypes() throws IOException {
        List<String> invocations = invocationsByMethod(BOOTSTRAP_CLASS).get(
                "initialize(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );
        int lootConditions = invocations.indexOf(
                "ru/feytox/etherology/registry/misc/SharedLootConditions#register()V"
        );
        int enchantments = invocations.indexOf(
                SHARED_ENCHANTMENTS + "#register()V"
        );
        int particleTypes = invocations.indexOf(
                "ru/feytox/etherology/registry/particle/SharedParticleTypes#register()V"
        );

        assertTrue(lootConditions >= 0);
        assertEquals(lootConditions + 1, enchantments);
        assertEquals(enchantments + 1, particleTypes);
        assertEquals(1, count(invocations, SHARED_ENCHANTMENTS + "#register()V"));
        assertNull(SharedEnchantmentsBytecodeTest.class.getResource(
                "/ru/feytox/etherology/registry/misc/EtherEnchantments.class"
        ));
        assertNotNull(SharedEnchantmentsBytecodeTest.class.getResource(
                PEAL_ENCHANTMENT_CLASS
        ));
        assertNotNull(SharedEnchantmentsBytecodeTest.class.getResource(
                REFLECTION_ENCHANTMENT_CLASS
        ));
    }

    private static void assertReadsEnchantmentRegistryKey() throws IOException {
        AtomicInteger registryKeyReads = new AtomicInteger();
        reader(SHARED_ENCHANTMENTS_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                                && name.equals("ENCHANTMENT")) {
                            registryKeyReads.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, registryKeyReads.get());
    }

    private static List<String> constructorFieldReads(String classResource)
            throws IOException {
        List<String> fields = new ArrayList<>();
        reader(classResource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<init>")) {
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
                            fields.add(owner + "#" + name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return fields;
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

    private static ClassReader reader(String classResource) throws IOException {
        InputStream classStream = SharedEnchantmentsBytecodeTest.class
                .getResourceAsStream(classResource);
        assertNotNull(classStream, "Missing class resource " + classResource);
        try (classStream) {
            return new ClassReader(classStream);
        }
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
}
