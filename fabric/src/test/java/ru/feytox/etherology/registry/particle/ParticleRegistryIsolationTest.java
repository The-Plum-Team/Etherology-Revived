package ru.feytox.etherology.registry.particle;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
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
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ParticleRegistryIsolationTest {

    private static final String SHARED_PARTICLES =
            "ru/feytox/etherology/registry/particle/SharedParticleTypes";
    private static final String SHARED_PARTICLES_CLASS = SHARED_PARTICLES + ".class";
    private static final String LEGACY_PARTICLES =
            "ru/feytox/etherology/registry/particle/EtherParticleTypes.class";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String FABRIC_ENTRYPOINT =
            "ru/feytox/etherology/EtherologyFabric.class";
    private static final String CANONICAL_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String CLIENT_FACTORIES =
            "ru/feytox/etherology/client/registry/ClientParticleRegistry.class";

    private static final List<String> MOVED_CLASSES = List.of(
            SHARED_PARTICLES_CLASS,
            "ru/feytox/etherology/magic/seal/SealType.class",
            "ru/feytox/etherology/util/misc/RGBColor.class",
            "ru/feytox/etherology/particle/effects/misc/FeyParticleEffect.class",
            "ru/feytox/etherology/particle/effects/misc/FeyParticleType.class",
            "ru/feytox/etherology/particle/effects/ElectricityParticleEffect.class",
            "ru/feytox/etherology/particle/effects/ItemParticleEffect.class",
            "ru/feytox/etherology/particle/effects/LightParticleEffect.class",
            "ru/feytox/etherology/particle/effects/MovingParticleEffect.class",
            "ru/feytox/etherology/particle/effects/ScalableParticleEffect.class",
            "ru/feytox/etherology/particle/effects/SealParticleEffect.class",
            "ru/feytox/etherology/particle/effects/SimpleParticleEffect.class",
            "ru/feytox/etherology/particle/effects/SparkParticleEffect.class",
            "ru/feytox/etherology/particle/subtype/ElectricitySubtype.class",
            "ru/feytox/etherology/particle/subtype/LightSubtype.class",
            "ru/feytox/etherology/particle/subtype/SparkSubtype.class"
    );

    @Test
    void commonArtifactIsTheSingleClasspathOwnerOfEveryMovedClass()
            throws IOException {
        URL sharedOwner = singleClasspathResource(SHARED_PARTICLES_CLASS);
        assertEquals("jar", sharedOwner.getProtocol());

        JarURLConnection connection = (JarURLConnection) sharedOwner.openConnection();
        connection.setUseCaches(false);
        try (JarFile commonArtifact = connection.getJarFile()) {
            for (String movedClass : MOVED_CLASSES) {
                assertNotNull(commonArtifact.getJarEntry(movedClass), movedClass);
                assertSameArtifact(sharedOwner, singleClasspathResource(movedClass));
            }
            assertNull(commonArtifact.getJarEntry(LEGACY_PARTICLES));
            assertFalse(commonArtifact.stream().anyMatch(entry ->
                    entry.getName().startsWith("ru/feytox/etherology/client/")
            ));
        }

        assertNull(classLoader().getResource(LEGACY_PARTICLES));
    }

    @Test
    void fabricEntrypointAttachesExactlyOnceBeforeCanonicalInitialization()
            throws IOException {
        List<String> invocations = methodInvocations(
                classpathClass(FABRIC_ENTRYPOINT),
                "onInitialize"
        );
        assertEquals(
                List.of(
                        "ru/feytox/etherology/recipes/RecipeResultComponents#bind"
                                + "(Lru/feytox/etherology/recipes/"
                                + "RecipeResultComponentBackend;)V",
                        "ru/feytox/etherology/item/LensRuntime#bind"
                                + "(Lru/feytox/etherology/item/LensRuntimeBackend;)V",
                        "ru/feytox/etherology/block/pedestal/"
                                + "PedestalBlockEntityRemoval#bind"
                                + "(Lru/feytox/etherology/block/pedestal/"
                                + "PedestalBlockEntityRemovalBackend;)V",
                        SHARED_PARTICLES + "#register()V",
                        "ru/feytox/etherology/Etherology#initialize()V"
                ),
                invocations
        );
        assertEquals(
                0,
                invocationCount(
                        classpathClass(CANONICAL_INITIALIZER),
                        "initialize",
                        SHARED_PARTICLES,
                        "register"
                )
        );
        assertEquals(
                0,
                ownerReferenceCount(
                        classpathClass(FABRIC_ENTRYPOINT),
                        "ru/feytox/etherology/bootstrap/EtherologyBootstrap"
                )
        );
    }

    @Test
    void everyKnownProductionAndClientConsumerResolvesAtUseTime()
            throws IOException {
        Map<String, List<String>> expectedConsumers = expectedConsumers();
        for (Map.Entry<String, List<String>> entry : expectedConsumers.entrySet()) {
            ConsumerUsage usage = consumerUsage(classpathClass(entry.getKey()));
            assertEquals(entry.getValue(), usage.fieldReads(), entry.getKey());
            assertEquals(entry.getValue(), usage.resolvedFields(), entry.getKey());
            assertEquals(List.of(), usage.unresolvedFields(), entry.getKey());
            assertEquals(List.of(), usage.classInitializerFields(), entry.getKey());
            assertEquals(0, usage.legacyOwnerReferences(), entry.getKey());
        }
    }

    @Test
    void clientFactoryRegistryRetainsTheExactTwentyTwoMappings()
            throws IOException {
        assertEquals(
                expectedClientFactories(),
                clientFactoryMappings(classpathClass(CLIENT_FACTORIES))
        );
    }

    private static Map<String, List<String>> expectedConsumers() {
        Map<String, List<String>> consumers = new LinkedHashMap<>();
        consumers.put(
                "ru/feytox/etherology/particle/effects/ElectricityParticleEffect.class",
                List.of("ELECTRICITY1", "ELECTRICITY2")
        );
        consumers.put(
                "ru/feytox/etherology/client/block/brewingCauldron/"
                        + "BrewingCauldronClient.class",
                List.of("HAZE")
        );
        consumers.put(
                "ru/feytox/etherology/client/block/channel/EtherealChannelClient.class",
                List.of("ETHER_STAR", "ETHER_DOT")
        );
        consumers.put(
                "ru/feytox/etherology/client/block/etherealSocket/"
                        + "EtherealSocketClient.class",
                List.of("GLINT")
        );
        consumers.put(
                "ru/feytox/etherology/client/block/generators/"
                        + "AbstractGeneratorClient.class",
                List.of("LIGHT")
        );
        consumers.put(
                "ru/feytox/etherology/client/block/matrix/MatrixBlockClient.class",
                List.of("ARMILLARY_SPHERE", "VITAL")
        );
        consumers.put(
                "ru/feytox/etherology/client/block/sedimentary/"
                        + "SedimentaryBlockClient.class",
                List.of("SEAL")
        );
        consumers.put(
                "ru/feytox/etherology/client/block/tuningFork/TuningForkClient.class",
                List.of("RESONATION")
        );
        consumers.put(
                "ru/feytox/etherology/client/network/S2CHandlers.class",
                List.of("REDSTONE_FLASH", "REDSTONE_STREAM")
        );
        consumers.put(CLIENT_FACTORIES, particleFields());
        consumers.put(
                "ru/feytox/etherology/block/brewingCauldron/"
                        + "BrewingCauldronBlockEntity.class",
                List.of("ALCHEMY", "STEAM")
        );
        consumers.put(
                "ru/feytox/etherology/block/jewelryTable/JewelryBlockEntity.class",
                List.of("SPARK", "HAZE")
        );
        consumers.put(
                "ru/feytox/etherology/block/levitator/LevitatorBlockEntity.class",
                List.of("LIGHT")
        );
        consumers.put(
                "ru/feytox/etherology/block/matrix/MatrixBlockEntity.class",
                List.of("ITEM", "LIGHT", "SPARK")
        );
        consumers.put(
                "ru/feytox/etherology/block/sedimentary/"
                        + "SedimentaryStoneBlockEntity.class",
                List.of("SPARK")
        );
        consumers.put(
                "ru/feytox/etherology/item/BroadSwordItem.class",
                List.of("SCALABLE_SWEEP")
        );
        consumers.put(
                "ru/feytox/etherology/util/misc/ShockwaveUtil.class",
                List.of("RESONATION", "LIGHTNING_BOLT")
        );
        return consumers;
    }

    private static List<String> particleFields() {
        return List.of(
                "LIGHT",
                "STEAM",
                "SPARK",
                "ELECTRICITY1",
                "ELECTRICITY2",
                "ITEM",
                "RISING",
                "VITAL",
                "SHOCKWAVE",
                "GLINT",
                "ENERGY_ABSORPTION",
                "ARMILLARY_SPHERE",
                "HAZE",
                "ALCHEMY",
                "ETHER_STAR",
                "ETHER_DOT",
                "RESONATION",
                "LIGHTNING_BOLT",
                "SCALABLE_SWEEP",
                "REDSTONE_FLASH",
                "REDSTONE_STREAM",
                "SEAL"
        );
    }

    private static Map<String, String> expectedClientFactories() {
        String particles = "ru/feytox/etherology/client/particle/";
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("LIGHT", particles + "LightParticle");
        mappings.put("STEAM", particles + "SteamParticle");
        mappings.put("SPARK", particles + "SparkParticle");
        mappings.put("ELECTRICITY1", particles + "ElectricityParticle");
        mappings.put("ELECTRICITY2", particles + "ElectricityParticle");
        mappings.put("ITEM", particles + "ItemParticle");
        mappings.put("RISING", particles + "RisingParticle");
        mappings.put("VITAL", particles + "VitalParticle");
        mappings.put("SHOCKWAVE", particles + "ShockwaveParticle");
        mappings.put("GLINT", particles + "GlintParticle");
        mappings.put("ENERGY_ABSORPTION", particles + "EnergyAbsorptionParticle");
        mappings.put("ARMILLARY_SPHERE", particles + "SphereParticle");
        mappings.put("HAZE", particles + "HazeParticle");
        mappings.put("ALCHEMY", particles + "AlchemyParticle");
        mappings.put("ETHER_STAR", particles + "EtherParticle$EtherStarParticle");
        mappings.put("ETHER_DOT", particles + "EtherParticle$EtherDotParticle");
        mappings.put("RESONATION", particles + "ResonationParticle");
        mappings.put("LIGHTNING_BOLT", particles + "LightningBoltParticle");
        mappings.put("SCALABLE_SWEEP", particles + "ScalableSweepParticle");
        mappings.put("REDSTONE_FLASH", particles + "RedstoneFlashParticle");
        mappings.put("REDSTONE_STREAM", particles + "RedstoneStreamParticle");
        mappings.put("SEAL", particles + "SealParticle");
        return mappings;
    }

    private static ConsumerUsage consumerUsage(ClassReader reader) {
        List<String> fieldReads = new ArrayList<>();
        List<String> resolvedFields = new ArrayList<>();
        List<String> unresolvedFields = new ArrayList<>();
        List<String> classInitializerFields = new ArrayList<>();
        int[] legacyOwnerReferences = {0};

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String methodName,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingField;

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (owner.equals(LEGACY_PARTICLES.substring(
                                0,
                                LEGACY_PARTICLES.length() - ".class".length()
                        ))) {
                            legacyOwnerReferences[0]++;
                        }
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_PARTICLES)) {
                            if (pendingField != null) {
                                unresolvedFields.add(pendingField);
                            }
                            pendingField = name;
                            fieldReads.add(name);
                            if (methodName.equals("<clinit>")) {
                                classInitializerFields.add(name);
                            }
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
                        if (owner.equals(LEGACY_PARTICLES.substring(
                                0,
                                LEGACY_PARTICLES.length() - ".class".length()
                        ))) {
                            legacyOwnerReferences[0]++;
                        }
                        if (pendingField == null) {
                            return;
                        }
                        if (owner.equals(REGISTRY_SUPPLIER) && name.equals("get")) {
                            resolvedFields.add(pendingField);
                        } else {
                            unresolvedFields.add(pendingField);
                        }
                        pendingField = null;
                    }

                    @Override
                    public void visitEnd() {
                        if (pendingField != null) {
                            unresolvedFields.add(pendingField);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return new ConsumerUsage(
                fieldReads,
                resolvedFields,
                unresolvedFields,
                classInitializerFields,
                legacyOwnerReferences[0]
        );
    }

    private static Map<String, String> clientFactoryMappings(ClassReader reader) {
        Map<String, String> mappings = new LinkedHashMap<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("registerAll")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingField;

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_PARTICLES)) {
                            pendingField = name;
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String name,
                            String descriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        if (pendingField == null) {
                            return;
                        }
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle
                                    && handle.getTag() == Opcodes.H_NEWINVOKESPECIAL
                                    && handle.getName().equals("<init>")) {
                                mappings.put(pendingField, handle.getOwner());
                                pendingField = null;
                                return;
                            }
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return mappings;
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
            if (invocation.startsWith(
                    expectedOwner + "#" + expectedInvocationName + "("
            )) {
                count++;
            }
        }
        return count;
    }

    private static int ownerReferenceCount(ClassReader reader, String expectedOwner) {
        int[] count = {0};
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
                        if (owner.equals(expectedOwner)) {
                            count[0]++;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count[0];
    }

    private static ClassReader classpathClass(String classResource) throws IOException {
        InputStream classStream = classLoader().getResourceAsStream(classResource);
        assertNotNull(classStream, "Missing classpath class " + classResource);
        try (classStream) {
            return new ClassReader(classStream);
        }
    }

    private static URL singleClasspathResource(String classResource) throws IOException {
        Enumeration<URL> resources = classLoader().getResources(classResource);
        assertTrue(resources.hasMoreElements(), "Missing classpath class " + classResource);
        URL resource = resources.nextElement();
        assertFalse(resources.hasMoreElements(), "Multiple owners provide " + classResource);
        return resource;
    }

    private static void assertSameArtifact(URL expectedOwner, URL actualOwner)
            throws IOException {
        JarURLConnection expected = (JarURLConnection) expectedOwner.openConnection();
        JarURLConnection actual = (JarURLConnection) actualOwner.openConnection();
        expected.setUseCaches(false);
        actual.setUseCaches(false);
        assertEquals(expected.getJarFileURL(), actual.getJarFileURL());
    }

    private static ClassLoader classLoader() {
        return ParticleRegistryIsolationTest.class.getClassLoader();
    }

    private record ConsumerUsage(
            List<String> fieldReads,
            List<String> resolvedFields,
            List<String> unresolvedFields,
            List<String> classInitializerFields,
            int legacyOwnerReferences
    ) {
    }
}
