package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AspectDataRegistryBridgeTest {

    private static final String REGISTRATION =
            "ru/feytox/etherology/forge/ForgeAspectRegistryEvents";
    private static final String RELOAD =
            "ru/feytox/etherology/forge/ForgeAspectReloadEvents";
    private static final String SHARED_REGISTRIES =
            "ru/feytox/etherology/registry/misc/SharedAspectRegistries";
    private static final String REGISTRY_PART =
            "ru/feytox/etherology/magic/aspects/AspectRegistryPart";
    private static final List<String> MOVED_SOURCES = List.of(
            "data/aspects/AspectsLoader.java",
            "magic/aspects/AspectContainerId.java",
            "magic/aspects/AspectContainerType.java",
            "magic/aspects/AspectEntry.java",
            "magic/aspects/AspectRegistryPart.java",
            "magic/aspects/RevelationAspectProvider.java",
            "registry/misc/SharedAspectRegistries.java"
    );

    @Test
    void eventSubscribersSelectTheRequiredForgeBusesAndEvents()
            throws IOException {
        assertEquals(
                new SubscriberTrace(
                        "etherology",
                        "MOD",
                        List.of(
                                "registerRegistries"
                                        + "(Lnet/minecraftforge/registries/"
                                        + "DataPackRegistryEvent$NewRegistry;)V"
                        )
                ),
                subscriberTrace(REGISTRATION)
        );
        assertEquals(
                new SubscriberTrace(
                        "etherology",
                        "FORGE",
                        List.of(
                                "onTagsUpdated"
                                        + "(Lnet/minecraftforge/event/"
                                        + "TagsUpdatedEvent;)V"
                        )
                ),
                subscriberTrace(RELOAD)
        );
    }

    @Test
    void forgeRegistersOneSharedRegistryWithARequiredNetworkCodec()
            throws IOException {
        MethodTrace trace = trace(REGISTRATION, "registerRegistries");
        assertEquals(
                List.of(
                        SHARED_REGISTRIES + "#ASPECTS",
                        REGISTRY_PART + "#CODEC",
                        REGISTRY_PART + "#CODEC"
                ),
                trace.fields()
        );
        assertEquals(
                List.of(
                        "net/minecraftforge/registries/DataPackRegistryEvent$NewRegistry"
                                + "#dataPackRegistry"
                                + "(Lnet/minecraft/registry/RegistryKey;"
                                + "Lcom/mojang/serialization/Codec;"
                                + "Lcom/mojang/serialization/Codec;)V"
                ),
                trace.calls()
        );
    }

    @Test
    void forgeInvalidatesOnlyForTheAuthoritativeTagUpdate()
            throws IOException {
        MethodTrace trace = trace(RELOAD, "onTagsUpdated");
        assertEquals(
                List.of(
                        "net/minecraftforge/event/TagsUpdatedEvent"
                                + "#shouldUpdateStaticData()Z",
                        "ru/feytox/etherology/data/aspects/AspectsLoader"
                                + "#clearCache()V"
                ),
                trace.calls()
        );
        assertEquals(List.of(Opcodes.IFEQ), trace.jumps());
    }

    @Test
    void commonRemainsTheOnlySourceOwnerOfTheMovedAspectBridge()
            throws IOException {
        Path repositoryRoot = repositoryRoot();
        for (String source : MOVED_SOURCES) {
            Path common = repositoryRoot.resolve(
                    "common/src/main/java/ru/feytox/etherology/" + source
            );
            assertTrue(Files.isRegularFile(common), common.toString());
            assertFalse(Files.exists(repositoryRoot.resolve(
                    "src/main/java/ru/feytox/etherology/" + source
            )));
            assertFalse(Files.exists(repositoryRoot.resolve(
                    "fabric/src/main/java/ru/feytox/etherology/" + source
            )));
            assertFalse(Files.exists(repositoryRoot.resolve(
                    "forge/src/main/java/ru/feytox/etherology/" + source
            )));
        }
        assertFalse(Files.exists(repositoryRoot.resolve(
                "src/main/java/ru/feytox/etherology/mixin/"
                        + "PedestalBlockEntityRevelationMixin.java"
        )));
    }

    @Test
    void forgeSeesTheSharedPedestalRevelationImplementation()
            throws IOException {
        ClassNode pedestal = PedestalBytecodeAssertions.readClass(
                "ru/feytox/etherology/block/pedestal/PedestalBlockEntity.class"
        );
        assertTrue(pedestal.interfaces.contains(
                "ru/feytox/etherology/magic/aspects/RevelationAspectProvider"
        ));
        MethodNode revelation = PedestalBytecodeAssertions.requireMethod(
                pedestal,
                "getRevelationAspects",
                "(Lnet/minecraft/world/World;)"
                        + "Lru/feytox/etherology/magic/aspects/AspectContainer;"
        );
        assertTrue(PedestalBytecodeAssertions.calls(revelation).contains(
                "ru/feytox/etherology/data/aspects/AspectsLoader#getAspects"
                        + "(Lnet/minecraft/world/World;"
                        + "Lnet/minecraft/item/ItemStack;ZZ)"
                        + "Ljava/util/Optional;"
        ));
    }

    @Test
    void buildAcceptsOnlyTheTwoCommonAspectRegistryResources()
            throws IOException {
        Path repositoryRoot = repositoryRoot();
        String build = Files.readString(
                repositoryRoot.resolve("forge/build.gradle.kts"),
                StandardCharsets.UTF_8
        );
        assertTrue(build.contains(
                "val commonAspectRegistryDataEntries = setOf(\n"
                        + "    \"etherology/etherology/aspects/etherology.json\",\n"
                        + "    \"etherology/etherology/aspects/vanilla.json\",\n"
                        + ")"
        ));
        assertTrue(build.contains(
                "acceptedForgeDirectDataEntries + commonEtherSourceDataEntry +\n"
                        + "        commonAspectRegistryDataEntries"
        ));

        for (String name : List.of("etherology.json", "vanilla.json")) {
            String resource = "data/etherology/etherology/aspects/" + name;
            List<URL> owners = Collections.list(
                    getClass().getClassLoader().getResources(resource)
            );
            assertEquals(1, owners.size(), resource + ":" + owners);
            try (InputStream packaged = owners.get(0).openStream()) {
                assertEquals(
                        Files.readString(
                                repositoryRoot.resolve(
                                        "common/src/main/resources/" + resource
                                ),
                                StandardCharsets.UTF_8
                        ),
                        new String(packaged.readAllBytes(), StandardCharsets.UTF_8),
                        resource
                );
            }
        }
    }

    private static MethodTrace trace(String className, String methodName)
            throws IOException {
        List<String> fields = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        List<Integer> jumps = new ArrayList<>();
        try (InputStream stream = AspectDataRegistryBridgeTest.class
                .getResourceAsStream("/" + className + ".class")) {
            assertNotNull(stream, className);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        public void visitFieldInsn(
                                int opcode,
                                String owner,
                                String fieldName,
                                String fieldDescriptor
                        ) {
                            fields.add(owner + "#" + fieldName);
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String calledName,
                                String descriptor,
                                boolean isInterface
                        ) {
                            calls.add(owner + "#" + calledName + descriptor);
                        }

                        @Override
                        public void visitJumpInsn(
                                int opcode,
                                org.objectweb.asm.Label label
                        ) {
                            jumps.add(opcode);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return new MethodTrace(fields, calls, jumps);
    }

    private static SubscriberTrace subscriberTrace(String className)
            throws IOException {
        String[] modid = new String[1];
        String[] bus = new String[1];
        List<String> subscribedMethods = new ArrayList<>();
        try (InputStream stream = AspectDataRegistryBridgeTest.class
                .getResourceAsStream("/" + className + ".class")) {
            assertNotNull(stream, className);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(
                        String descriptor,
                        boolean visible
                ) {
                    if (!descriptor.equals(
                            "Lnet/minecraftforge/fml/common/"
                                    + "Mod$EventBusSubscriber;"
                    )) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if (name.equals("modid")) modid[0] = value.toString();
                        }

                        @Override
                        public void visitEnum(
                                String name,
                                String descriptor,
                                String value
                        ) {
                            if (name.equals("bus")) bus[0] = value;
                        }
                    };
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
                        public AnnotationVisitor visitAnnotation(
                                String annotationDescriptor,
                                boolean visible
                        ) {
                            if (annotationDescriptor.equals(
                                    "Lnet/minecraftforge/eventbus/api/"
                                            + "SubscribeEvent;"
                            )) {
                                subscribedMethods.add(name + descriptor);
                            }
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                    | ClassReader.SKIP_FRAMES);
        }
        return new SubscriberTrace(modid[0], bus[0], subscribedMethods);
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null
                && !Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }

        assertNotNull(candidate, "Could not find the Etherology repository root");
        return candidate;
    }

    private record MethodTrace(
            List<String> fields,
            List<String> calls,
            List<Integer> jumps
    ) {
    }

    private record SubscriberTrace(
            String modid,
            String bus,
            List<String> subscribedMethods
    ) {
    }
}
