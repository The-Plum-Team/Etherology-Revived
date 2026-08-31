package ru.feytox.etherology.registry.particle;

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

final class SharedParticleTypesBytecodeTest {

    private static final String SHARED_PARTICLE_TYPES_CLASS =
            "/ru/feytox/etherology/registry/particle/SharedParticleTypes.class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String SHARED_PARTICLE_TYPES =
            "ru/feytox/etherology/registry/particle/SharedParticleTypes";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String FEY_PARTICLE_TYPE =
            "ru/feytox/etherology/particle/effects/misc/FeyParticleType";
    private static final String EFFECTS =
            "ru/feytox/etherology/particle/effects/";
    private static final String ITEM_EFFECT_CLASS =
            "/" + EFFECTS + "ItemParticleEffect.class";
    private static final String REGISTRY_SUPPLIER_DESCRIPTOR =
            "Ldev/architectury/registry/registries/RegistrySupplier;";

    private static final List<ParticleDeclaration> DECLARATIONS = List.of(
            new ParticleDeclaration("LIGHT", "light", "LightParticleEffect"),
            new ParticleDeclaration("STEAM", "steam", "SimpleParticleEffect"),
            new ParticleDeclaration("SPARK", "spark", "SparkParticleEffect"),
            new ParticleDeclaration(
                    "ELECTRICITY1",
                    "electricity1",
                    "ElectricityParticleEffect"
            ),
            new ParticleDeclaration(
                    "ELECTRICITY2",
                    "electricity2",
                    "ElectricityParticleEffect"
            ),
            new ParticleDeclaration("ITEM", "item", "ItemParticleEffect"),
            new ParticleDeclaration("RISING", "rising", "SimpleParticleEffect"),
            new ParticleDeclaration("VITAL", "vital", "MovingParticleEffect"),
            new ParticleDeclaration("SHOCKWAVE", "shockwave", "SimpleParticleEffect"),
            new ParticleDeclaration("GLINT", "glint_particle", "MovingParticleEffect"),
            new ParticleDeclaration(
                    "ENERGY_ABSORPTION",
                    "energy_absorption",
                    "SimpleParticleEffect"
            ),
            new ParticleDeclaration(
                    "ARMILLARY_SPHERE",
                    "armillary_sphere",
                    "MovingParticleEffect"
            ),
            new ParticleDeclaration("HAZE", "haze", "SimpleParticleEffect"),
            new ParticleDeclaration("ALCHEMY", "alchemy", "SimpleParticleEffect"),
            new ParticleDeclaration("ETHER_STAR", "ether_star", "MovingParticleEffect"),
            new ParticleDeclaration("ETHER_DOT", "ether_dot", "MovingParticleEffect"),
            new ParticleDeclaration(
                    "RESONATION",
                    "resonation",
                    "ScalableParticleEffect"
            ),
            new ParticleDeclaration(
                    "LIGHTNING_BOLT",
                    "lightning_bolt",
                    "ScalableParticleEffect"
            ),
            new ParticleDeclaration(
                    "SCALABLE_SWEEP",
                    "scalable_sweep",
                    "ScalableParticleEffect"
            ),
            new ParticleDeclaration(
                    "REDSTONE_FLASH",
                    "redstone_flash",
                    "SimpleParticleEffect"
            ),
            new ParticleDeclaration(
                    "REDSTONE_STREAM",
                    "redstone_stream",
                    "SimpleParticleEffect"
            ),
            new ParticleDeclaration("SEAL", "seal", "SealParticleEffect")
    );

    @Test
    void declaresTheExactOrderedCanonicalSupplierCatalog() throws IOException {
        Map<String, Integer> fieldAccess = new LinkedHashMap<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        Map<String, String> fieldSignatures = new LinkedHashMap<>();
        Map<String, String> registrations = new LinkedHashMap<>();
        List<String> publicMethods = new ArrayList<>();
        AtomicInteger classAccess = new AtomicInteger();

        reader(SHARED_PARTICLE_TYPES_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        if (owner.equals(SHARED_PARTICLE_TYPES)
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
                                && owner.equals(SHARED_PARTICLE_TYPES)
                                && !name.equals("PARTICLE_TYPES")) {
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

        List<String> expectedFields = new ArrayList<>();
        expectedFields.add("PARTICLE_TYPES");
        expectedFields.addAll(DECLARATIONS.stream()
                .map(ParticleDeclaration::fieldName)
                .toList());
        assertEquals(expectedFields, new ArrayList<>(fieldDescriptors.keySet()));
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get("PARTICLE_TYPES")
        );
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                fieldDescriptors.get("PARTICLE_TYPES")
        );
        assertEquals(
                "Lru/feytox/etherology/registry/SharedDeferredRegister"
                        + "<Lnet/minecraft/particle/ParticleType<*>;>;",
                fieldSignatures.get("PARTICLE_TYPES")
        );

        for (ParticleDeclaration declaration : DECLARATIONS) {
            String fieldName = declaration.fieldName();
            assertEquals(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    fieldAccess.get(fieldName),
                    fieldName
            );
            assertEquals(
                    REGISTRY_SUPPLIER_DESCRIPTOR,
                    fieldDescriptors.get(fieldName),
                    fieldName
            );
            assertEquals(
                    supplierSignature(declaration.effectType()),
                    fieldSignatures.get(fieldName),
                    fieldName
            );
        }

        Map<String, String> expectedRegistrations = new LinkedHashMap<>();
        for (ParticleDeclaration declaration : DECLARATIONS) {
            expectedRegistrations.put(declaration.fieldName(), declaration.id());
        }
        assertEquals(expectedRegistrations, registrations);
        assertEquals(List.of("register()V"), publicMethods);
    }

    @Test
    void defersEveryFactoryWithoutResolvingASupplier() throws IOException {
        Map<String, List<String>> invocations = invocationsByMethod(
                SHARED_PARTICLE_TYPES_CLASS
        );
        List<String> allInvocations = invocations.values().stream()
                .flatMap(List::stream)
                .toList();

        assertEquals(1, countNamed(allInvocations, SHARED_DEFERRED_REGISTER, "create"));
        assertEquals(
                DECLARATIONS.size(),
                countNamed(allInvocations, SHARED_PARTICLE_TYPES, "register")
        );
        assertEquals(1, countNamed(allInvocations, SHARED_DEFERRED_REGISTER, "register"));
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                invocations.get("register()V")
        );
        assertEquals(0, countNamed(allInvocations, REGISTRY_SUPPLIER, "get"));
        assertEquals(
                0,
                countNamed(allInvocations, "net/minecraft/registry/Registry", "register")
        );
        assertReadsParticleTypeRegistryKey();
        assertConstructsOnlyHiddenParticles();
    }

    @Test
    void attachesAfterEnchantmentsAndBeforeServerDataReloaders() throws IOException {
        List<String> invocations = invocationsByMethod(BOOTSTRAP_CLASS).get(
                "initialize(Lru/feytox/etherology/bootstrap/PlatformRegistrar;)V"
        );
        String enchantments =
                "ru/feytox/etherology/registry/misc/SharedEnchantments#register()V";
        String particles = SHARED_PARTICLE_TYPES + "#register()V";
        String reloaders =
                "ru/feytox/etherology/registry/misc/ResourceReloaders"
                        + "#registerServerData()V";

        int enchantmentIndex = invocations.indexOf(enchantments);
        int particleIndex = invocations.indexOf(particles);
        int reloaderIndex = invocations.indexOf(reloaders);
        assertTrue(enchantmentIndex >= 0);
        assertEquals(enchantmentIndex + 1, particleIndex);
        assertEquals(particleIndex + 1, reloaderIndex);
        assertEquals(1, count(invocations, particles));
    }

    @Test
    void preservesEveryCodecFieldName() throws IOException {
        Map<String, List<String>> expectedStrings = Map.of(
                "ElectricityParticleEffect", List.of("electricity_type"),
                "ItemParticleEffect", List.of("item", "moveVec"),
                "LightParticleEffect", List.of("lightType", "moveVec"),
                "MovingParticleEffect", List.of("moveVec"),
                "ScalableParticleEffect", List.of("scale"),
                "SealParticleEffect", List.of("zoneType", "endPos"),
                "SimpleParticleEffect", List.of(),
                "SparkParticleEffect", List.of("moveVec", "sparkType")
        );

        for (Map.Entry<String, List<String>> entry : expectedStrings.entrySet()) {
            String resource = "/" + EFFECTS + entry.getKey() + ".class";
            assertEquals(
                    entry.getValue(),
                    codecStringConstants(reader(resource)),
                    entry.getKey()
            );
        }
    }

    @Test
    void itemCommandParserConsumesNamespacedIdentifiersDirectly() throws IOException {
        List<String> invocations = invocationsByMethod(ITEM_EFFECT_CLASS).get(
                "read(Lnet/minecraft/particle/ParticleType;"
                        + "Lcom/mojang/brigadier/StringReader;)"
                        + "Lru/feytox/etherology/particle/effects/ItemParticleEffect;"
        );
        assertEquals(
                1,
                countNamed(
                        invocations,
                        "net/minecraft/util/Identifier",
                        "fromCommandInput"
                )
        );
        assertEquals(
                0,
                countNamed(invocations, "com/mojang/brigadier/StringReader", "readString")
        );
        assertEquals(
                0,
                countNamed(invocations, "net/minecraft/util/Identifier", "tryParse")
        );
    }

    private static void assertReadsParticleTypeRegistryKey() throws IOException {
        AtomicInteger reads = new AtomicInteger();
        reader(SHARED_PARTICLE_TYPES_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                                && name.equals("PARTICLE_TYPE")) {
                            reads.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, reads.get());
    }

    private static void assertConstructsOnlyHiddenParticles() throws IOException {
        List<Integer> booleanArguments = new ArrayList<>();
        reader(SHARED_PARTICLE_TYPES_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private Integer lastBoolean;

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ICONST_0) {
                            lastBoolean = 0;
                        } else if (opcode == Opcodes.ICONST_1) {
                            lastBoolean = 1;
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
                        if (opcode == Opcodes.INVOKESPECIAL
                                && owner.equals(FEY_PARTICLE_TYPE)
                                && name.equals("<init>")) {
                            booleanArguments.add(lastBoolean);
                        }
                        lastBoolean = null;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(List.of(0), booleanArguments);
    }

    private static List<String> codecStringConstants(ClassReader reader) {
        List<String> strings = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("createCodec")
                        && !name.startsWith("lambda$createCodec$")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            strings.add(stringValue);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return strings;
    }

    private static Map<String, List<String>> invocationsByMethod(String resource)
            throws IOException {
        Map<String, List<String>> invocations = new LinkedHashMap<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
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

    private static ClassReader reader(String resource) throws IOException {
        InputStream classStream = SharedParticleTypesBytecodeTest.class
                .getResourceAsStream(resource);
        assertNotNull(classStream, "Missing class resource " + resource);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }

    private static String supplierSignature(String effectType) {
        return "Ldev/architectury/registry/registries/RegistrySupplier"
                + "<Lru/feytox/etherology/particle/effects/misc/FeyParticleType"
                + "<L" + EFFECTS + effectType + ";>;>;";
    }

    private static int count(List<String> values, String expected) {
        return (int) values.stream().filter(expected::equals).count();
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
            if (owner.equals(expectedOwner) && name.equals(expectedName)) {
                count++;
            }
        }
        return count;
    }

    private record ParticleDeclaration(String fieldName, String id, String effectType) {
    }
}
