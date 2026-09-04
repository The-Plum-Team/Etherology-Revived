package ru.feytox.etherology.block.pedestal;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalFabricCompatibilityTest {

    private static final String PEDESTAL_BLOCK =
            "ru/feytox/etherology/block/pedestal/PedestalBlock.class";
    private static final String PEDESTAL_BLOCK_ENTITY =
            "ru/feytox/etherology/block/pedestal/PedestalBlockEntity.class";
    private static final String FABRIC_ENTRYPOINT =
            "ru/feytox/etherology/EtherologyFabric.class";
    private static final String FABRIC_BACKEND =
            "ru/feytox/etherology/block/pedestal/"
                    + "FabricPedestalBlockEntityRemovalBackend.class";
    private static final String REVELATION_MIXIN =
            "ru/feytox/etherology/mixin/"
                    + "PedestalBlockEntityRevelationMixin.class";

    @Test
    void commonJarIsTheOnlyOwnerOfMovedPedestalClasses() throws IOException {
        for (String resource : List.of(PEDESTAL_BLOCK, PEDESTAL_BLOCK_ENTITY)) {
            Enumeration<URL> matches = classLoader().getResources(resource);
            List<URL> urls = Collections.list(matches);
            assertEquals(1, urls.size(), resource);
            assertEquals("jar", urls.get(0).getProtocol(), resource);
        }
    }

    @Test
    void legacyRegistryAliasesBothSharedPedestalOwners() throws IOException {
        String constants = classConstants(
                "ru/feytox/etherology/registry/block/EBlocks.class"
        );
        assertTrue(constants.contains(
                "ru/feytox/etherology/registry/block/SharedPedestalBlocks"
        ));
        assertTrue(constants.contains(
                "ru/feytox/etherology/registry/block/SharedPedestalBlockEntities"
        ));
        assertFalse(constants.contains(
                "ru/feytox/etherology/block/pedestal/PedestalBlock.<init>"
        ));
    }

    @Test
    void fabricBindsRemovalBackendBeforeCanonicalInitialization()
            throws IOException {
        List<String> calls = invocations(FABRIC_ENTRYPOINT, "onInitialize");
        int bindIndex = calls.indexOf(
                "ru/feytox/etherology/block/pedestal/"
                        + "PedestalBlockEntityRemoval#bind"
        );
        int initializeIndex = calls.indexOf(
                "ru/feytox/etherology/Etherology#initialize"
        );
        assertTrue(bindIndex >= 0);
        assertTrue(initializeIndex > bindIndex);

        List<String> backendCalls = invocations(FABRIC_BACKEND, "send");
        assertTrue(backendCalls.contains(
                "net/fabricmc/fabric/api/networking/v1/PlayerLookup#tracking"
        ));
        assertTrue(backendCalls.contains(
                "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking#send"
        ));
        assertTrue(backendCalls.contains(
                "ru/feytox/etherology/network/interaction/RemoveBlockEntityS2C#<init>"
        ));
    }

    @Test
    void sharedPedestalDirectlyRetainsLegacyRevelationLookup() throws IOException {
        List<String> interfaces = interfaces(PEDESTAL_BLOCK_ENTITY);
        assertTrue(interfaces.contains(
                "ru/feytox/etherology/magic/aspects/RevelationAspectProvider"
        ));
        List<String> calls = invocations(
                PEDESTAL_BLOCK_ENTITY,
                "getRevelationAspects"
        );
        assertTrue(calls.contains(
                "ru/feytox/etherology/data/aspects/AspectsLoader#getAspects"
        ));
        assertTrue(calls.contains("java/util/Optional#orElse"));

        InputStream config = classLoader().getResourceAsStream(
                "etherology.mixins.json"
        );
        assertNotNull(config);
        try (config) {
            String content = new String(
                    config.readAllBytes(),
                    StandardCharsets.UTF_8
            );
            assertFalse(content.contains("PedestalBlockEntityRevelationMixin"));
        }
        assertNull(classLoader().getResource(REVELATION_MIXIN));
    }

    @Test
    void existingFabricClientRegistrationsStillSelectPedestal() throws IOException {
        String renderers = classConstants(
                "ru/feytox/etherology/client/registry/BlockRenderersRegistry.class"
        );
        assertTrue(renderers.contains(
                "ru/feytox/etherology/client/block/pedestal/PedestalRenderer"
        ));
        assertTrue(renderers.contains("PEDESTAL_BLOCK_ENTITY"));

        String layers = classConstants(
                "ru/feytox/etherology/client/registry/"
                        + "BlockRenderLayerMapRegistry.class"
        );
        assertTrue(layers.contains("PEDESTAL_BLOCK"));
        assertTrue(layers.contains("registerCutout"));
    }

    private static ClassLoader classLoader() {
        return PedestalFabricCompatibilityTest.class.getClassLoader();
    }

    private static ClassReader reader(String resource) throws IOException {
        InputStream stream = classLoader().getResourceAsStream(resource);
        assertNotNull(stream, resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private static String classConstants(String resource) throws IOException {
        StringBuilder constants = new StringBuilder();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                constants.append(name).append('\n');
                constants.append(superName).append('\n');
                for (String interfaceName : interfaces) {
                    constants.append(interfaceName).append('\n');
                }
            }

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
                    public void visitTypeInsn(int opcode, String type) {
                        constants.append(type).append('\n');
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        constants.append(owner).append('\n');
                        constants.append(fieldName).append('\n');
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String calledName,
                            String calledDescriptor,
                            boolean isInterface
                    ) {
                        constants.append(owner)
                                .append('.')
                                .append(calledName)
                                .append('\n');
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String calledName,
                            String calledDescriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle) {
                                constants.append(handle.getOwner())
                                        .append('.')
                                        .append(handle.getName())
                                        .append('\n');
                            }
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return constants.toString();
    }

    private static List<String> interfaces(String resource) throws IOException {
        List<String> interfaces = new ArrayList<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] implementedInterfaces
            ) {
                Collections.addAll(interfaces, implementedInterfaces);
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return interfaces;
    }

    private static List<String> invocations(
            String resource,
            String methodName
    ) throws IOException {
        List<String> calls = new ArrayList<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        calls.add(owner + "#" + name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }
}
