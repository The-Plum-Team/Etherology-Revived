package ru.feytox.etherology.magic.aspects;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class AspectFoundationIsolationTest {

    private static final List<String> SHARED_CLASSES = List.of(
            "ru/feytox/etherology/magic/aspects/Aspect.class",
            "ru/feytox/etherology/magic/aspects/EtherologyAspect.class",
            "ru/feytox/etherology/magic/aspects/AspectContainer.class",
            "ru/feytox/etherology/magic/aspects/AspectContainerId.class",
            "ru/feytox/etherology/magic/aspects/AspectContainerType.class",
            "ru/feytox/etherology/magic/aspects/AspectEntry.class",
            "ru/feytox/etherology/magic/aspects/AspectRegistryPart.class",
            "ru/feytox/etherology/magic/aspects/RevelationAspectProvider.class",
            "ru/feytox/etherology/data/aspects/AspectsLoader.class",
            "ru/feytox/etherology/registry/misc/SharedAspectRegistries.class"
    );
    private static final String ALCHEMY_SERIALIZER =
            "ru/feytox/etherology/recipes/alchemy/AlchemyRecipeSerializer.class";
    private static final String ASPECT_OWNER =
            "ru/feytox/etherology/magic/aspects/Aspect";
    private static final String CONTAINER_OWNER =
            "ru/feytox/etherology/magic/aspects/AspectContainer";

    @Test
    void fabricTestRuntimeResolvesExactlyOneCopyOfEverySharedType()
            throws IOException {
        ClassLoader classLoader = AspectFoundationIsolationTest.class.getClassLoader();
        for (String sharedClass : SHARED_CLASSES) {
            List<URL> resources = Collections.list(
                    classLoader.getResources(sharedClass)
            );
            assertEquals(1, resources.size(), sharedClass + ":" + resources);
        }
    }

    @Test
    void canonicalAlchemySerializerStillUsesTheCanonicalPublicSurface()
            throws IOException {
        List<String> calls = new ArrayList<>();
        List<String> typeLiterals = new ArrayList<>();
        reader(ALCHEMY_SERIALIZER).accept(new ClassVisitor(Opcodes.ASM9) {
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
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Type type
                                && type.getInternalName().equals(ASPECT_OWNER)) {
                            typeLiterals.add("Aspect.class");
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
                        if (owner.equals(ASPECT_OWNER)
                                || owner.equals(CONTAINER_OWNER)) {
                            calls.add(owner + "#" + name + descriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(List.of("Aspect.class"), typeLiterals);
        assertEquals(
                List.of(
                        ASPECT_OWNER + "#get(Lnet/minecraft/util/Identifier;)"
                                + "Lru/feytox/etherology/magic/aspects/Aspect;",
                        CONTAINER_OWNER + "#<init>(Ljava/util/Map;)V",
                        CONTAINER_OWNER + "#<init>(Ljava/util/Map;)V",
                        CONTAINER_OWNER + "#getAspects()"
                                + "Lcom/google/common/collect/ImmutableMap;",
                        CONTAINER_OWNER + "#getAspects()"
                                + "Lcom/google/common/collect/ImmutableMap;",
                        ASPECT_OWNER + "#asString()Ljava/lang/String;"
                ),
                calls
        );
    }

    private static ClassReader reader(String resource) throws IOException {
        InputStream stream = AspectFoundationIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }
}
