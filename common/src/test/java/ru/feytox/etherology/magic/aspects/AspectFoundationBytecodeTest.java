package ru.feytox.etherology.magic.aspects;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class AspectFoundationBytecodeTest {

    private static final List<String> RESOURCES = List.of(
            "/ru/feytox/etherology/magic/aspects/Aspect.class",
            "/ru/feytox/etherology/magic/aspects/EtherologyAspect.class",
            "/ru/feytox/etherology/magic/aspects/AspectContainer.class"
    );

    @Test
    void sharedTypesHaveNoLoaderLombokOrLegacyFabricOwnerDependencies()
            throws IOException {
        Set<String> forbiddenReferences = new LinkedHashSet<>();
        for (String resource : RESOURCES) {
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
                    checkReference(signature, forbiddenReferences);
                    checkReference(superName, forbiddenReferences);
                    if (interfaces != null) {
                        for (String interfaceName : interfaces) {
                            checkReference(interfaceName, forbiddenReferences);
                        }
                    }
                }

                @Override
                public FieldVisitor visitField(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        Object value
                ) {
                    checkReference(descriptor, forbiddenReferences);
                    checkReference(signature, forbiddenReferences);
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
                    checkReference(descriptor, forbiddenReferences);
                    checkReference(signature, forbiddenReferences);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            checkReference(type, forbiddenReferences);
                        }

                        @Override
                        public void visitFieldInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor
                        ) {
                            checkReference(owner, forbiddenReferences);
                            checkReference(descriptor, forbiddenReferences);
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            checkReference(owner, forbiddenReferences);
                            checkReference(descriptor, forbiddenReferences);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertEquals(Set.of(), forbiddenReferences);
    }

    @Test
    void containerKeepsIdentityEqualityAndTheOriginalLoggerCategory()
            throws IOException {
        List<String> declaredEqualityMethods = new ArrayList<>();
        List<String> loggerCategories = new ArrayList<>();
        reader(RESOURCES.get(2)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (name.equals("equals") || name.equals("hashCode")) {
                    declaredEqualityMethods.add(name + descriptor);
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value.equals("ru.feytox.etherology.Etherology")) {
                            loggerCategories.add(value.toString());
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(List.of(), declaredEqualityMethods);
        assertEquals(
                List.of("ru.feytox.etherology.Etherology"),
                loggerCategories
        );
    }

    @Test
    void aspectCodecRetainsTheInheritedStringIdentifiableFieldType()
            throws IOException {
        List<FieldInfo> codecFields = new ArrayList<>();
        reader(RESOURCES.get(0)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals("CODEC")) {
                    codecFields.add(new FieldInfo(
                            access,
                            descriptor,
                            signature
                    ));
                }
                return null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                List.of(new FieldInfo(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "Lnet/minecraft/util/StringIdentifiable$Codec;",
                        "Lnet/minecraft/util/StringIdentifiable$Codec"
                                + "<Lru/feytox/etherology/magic/aspects/Aspect;>;"
                )),
                codecFields
        );
    }

    @Test
    void checkReturnValueAnnotationsRemainOnTheOriginalFiveMethods()
            throws IOException {
        List<String> annotatedMethods = new ArrayList<>();
        reader(RESOURCES.get(2)).accept(new ClassVisitor(Opcodes.ASM9) {
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
                                "Lorg/slf4j/helpers/CheckReturnValue;"
                        )) {
                            annotatedMethods.add(name + descriptor);
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                List.of(
                        "add(Lru/feytox/etherology/magic/aspects/AspectContainer;)"
                                + "Lru/feytox/etherology/magic/aspects/AspectContainer;",
                        "subtract(Lru/feytox/etherology/magic/aspects/AspectContainer;)"
                                + "Lru/feytox/etherology/magic/aspects/AspectContainer;",
                        "merge(Lru/feytox/etherology/magic/aspects/AspectContainer;"
                                + "Ljava/util/function/BiFunction;)"
                                + "Lru/feytox/etherology/magic/aspects/AspectContainer;",
                        "map(Ljava/util/function/Function;)"
                                + "Lru/feytox/etherology/magic/aspects/AspectContainer;",
                        "readNbt(Lnet/minecraft/nbt/NbtCompound;)"
                                + "Lru/feytox/etherology/magic/aspects/AspectContainer;"
                ),
                annotatedMethods
        );
    }

    private static void checkReference(
            String reference,
            Set<String> forbiddenReferences
    ) {
        if (reference == null) return;
        if (reference.contains("net/fabricmc/")
                || reference.contains("net/minecraftforge/")
                || reference.contains("lombok/")
                || reference.contains("ru/feytox/etherology/util/misc/EIdentifier")
                || reference.equals("ru/feytox/etherology/Etherology")) {
            forbiddenReferences.add(reference);
        }
    }

    private static ClassReader reader(String resource) throws IOException {
        InputStream stream = AspectFoundationBytecodeTest.class
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record FieldInfo(int access, String descriptor, String signature) {
    }
}
