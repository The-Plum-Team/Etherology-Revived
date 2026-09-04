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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedAlchemyRecipesBytecodeTest {

    private static final String SHARED_ALCHEMY_RECIPES =
            "/ru/feytox/etherology/registry/misc/SharedAlchemyRecipes.class";
    private static final String BOOTSTRAP =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String OWNER =
            "ru/feytox/etherology/registry/misc/SharedAlchemyRecipes";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String ALCHEMY_SERIALIZER =
            "ru/feytox/etherology/recipes/alchemy/AlchemyRecipeSerializer";

    @Test
    void ownsOneCanonicalSerializerAndTypeWithoutResolvingSuppliers()
            throws IOException {
        AtomicInteger classAccess = new AtomicInteger();
        AtomicInteger privateConstructors = new AtomicInteger();
        AtomicInteger deferredRegisters = new AtomicInteger();
        AtomicInteger supplierGets = new AtomicInteger();
        AtomicInteger serializerReads = new AtomicInteger();
        AtomicInteger typeReads = new AtomicInteger();
        List<FieldInfo> fields = new ArrayList<>();
        List<String> registryKeys = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Set<String> forbiddenOwners = new LinkedHashSet<>();

        classReader(SHARED_ALCHEMY_RECIPES).accept(
                new ClassVisitor(Opcodes.ASM9) {
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
                        fields.add(new FieldInfo(access, name, descriptor, value));
                        checkOwner(descriptor, forbiddenOwners);
                        checkOwner(signature, forbiddenOwners);
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
                        if (name.equals("<init>")
                                && descriptor.equals("()V")
                                && (access & Opcodes.ACC_PRIVATE) != 0) {
                            privateConstructors.incrementAndGet();
                        }
                        checkOwner(descriptor, forbiddenOwners);
                        checkOwner(signature, forbiddenOwners);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitLdcInsn(Object value) {
                                if (value.equals("alchemy_recipe")) {
                                    ids.add(value.toString());
                                }
                            }

                            @Override
                            public void visitFieldInsn(
                                    int opcode,
                                    String owner,
                                    String name,
                                    String descriptor
                            ) {
                                checkOwner(owner, forbiddenOwners);
                                checkOwner(descriptor, forbiddenOwners);
                                if (opcode == Opcodes.GETSTATIC
                                        && owner.equals(
                                        "net/minecraft/registry/RegistryKeys"
                                )) {
                                    registryKeys.add(name);
                                }
                                if (opcode == Opcodes.GETSTATIC
                                        && owner.equals(ALCHEMY_SERIALIZER)
                                        && name.equals("INSTANCE")) {
                                    serializerReads.incrementAndGet();
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
                                checkOwner(owner, forbiddenOwners);
                                checkOwner(descriptor, forbiddenOwners);
                                if (owner.equals(SHARED_DEFERRED_REGISTER)
                                        && name.equals("register")) {
                                    deferredRegisters.incrementAndGet();
                                }
                                if (owner.equals(REGISTRY_SUPPLIER)
                                        && name.equals("get")) {
                                    supplierGets.incrementAndGet();
                                }
                                if (owner.equals(ALCHEMY_SERIALIZER)
                                        && name.equals("getRecipeType")) {
                                    typeReads.incrementAndGet();
                                }
                                if (isDirectRegistryOwner(owner)) {
                                    forbiddenOwners.add(owner + "#" + name);
                                }
                            }
                        };
                    }
                },
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
        );

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(expectedFields(), fields);
        assertEquals(1, privateConstructors.get());
        assertEquals(List.of("RECIPE_SERIALIZER", "RECIPE_TYPE"), registryKeys);
        assertEquals(List.of("alchemy_recipe", "alchemy_recipe"), ids);
        assertEquals(2, deferredRegisters.get());
        assertEquals(0, supplierGets.get());
        assertEquals(2, serializerReads.get());
        assertEquals(1, typeReads.get());
        assertEquals(Set.of(), forbiddenOwners);
    }

    @Test
    void attachesSerializerBeforeTypeBetweenLensAndBlockEntities()
            throws IOException {
        assertEquals(
                List.of(
                        "RECIPE_SERIALIZERS",
                        SHARED_DEFERRED_REGISTER + "#attach()V",
                        "RECIPE_TYPES",
                        SHARED_DEFERRED_REGISTER + "#attach()V"
                ),
                attachmentEvents()
        );

        List<String> invocations = methodInvocations(
                classReader(BOOTSTRAP),
                "initialize"
        );
        String lensRegistration =
                "ru/feytox/etherology/registry/item/SharedLensItems#register()V";
        String alchemyRegistration = OWNER + "#register()V";
        String blockEntityRegistration =
                "ru/feytox/etherology/registry/block/SharedBlockEntities#register()V";
        int alchemyIndex = invocations.indexOf(alchemyRegistration);

        assertTrue(alchemyIndex > 0);
        assertEquals(lensRegistration, invocations.get(alchemyIndex - 1));
        assertEquals(blockEntityRegistration, invocations.get(alchemyIndex + 1));
        assertEquals(
                1,
                invocations.stream().filter(alchemyRegistration::equals).count()
        );
    }

    private static List<FieldInfo> expectedFields() {
        String deferredDescriptor =
                "Lru/feytox/etherology/registry/SharedDeferredRegister;";
        String supplierDescriptor =
                "Ldev/architectury/registry/registries/RegistrySupplier;";
        return List.of(
                new FieldInfo(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "RECIPE_ID",
                        "Ljava/lang/String;",
                        "alchemy_recipe"
                ),
                new FieldInfo(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "RECIPE_SERIALIZERS",
                        deferredDescriptor,
                        null
                ),
                new FieldInfo(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "RECIPE_TYPES",
                        deferredDescriptor,
                        null
                ),
                new FieldInfo(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "ALCHEMY_RECIPE_SERIALIZER",
                        supplierDescriptor,
                        null
                ),
                new FieldInfo(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "ALCHEMY_RECIPE_TYPE",
                        supplierDescriptor,
                        null
                )
        );
    }

    private static List<String> attachmentEvents() throws IOException {
        List<String> events = new ArrayList<>();
        classReader(SHARED_ALCHEMY_RECIPES).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                            int access,
                            String name,
                            String descriptor,
                            String signature,
                            String[] exceptions
                    ) {
                        if (!name.equals("register")) {
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
                                if (opcode == Opcodes.GETSTATIC
                                        && owner.equals(OWNER)) {
                                    events.add(name);
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
                                        && name.equals("attach")) {
                                    events.add(owner + "#" + name + descriptor);
                                }
                            }
                        };
                    }
                },
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
        );
        return events;
    }

    private static List<String> methodInvocations(
            ClassReader reader,
            String methodName
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
                if (!name.equals(methodName)) {
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

    private static void checkOwner(String owner, Set<String> forbiddenOwners) {
        if (owner != null && (owner.contains("net/fabricmc/")
                || owner.contains("net/minecraftforge/")
                || owner.contains("ru/feytox/etherology/client/"))) {
            forbiddenOwners.add(owner);
        }
    }

    private static boolean isDirectRegistryOwner(String owner) {
        return owner.equals("net/minecraft/registry/Registry")
                || owner.equals("net/minecraft/class_2378")
                || owner.equals("net/minecraft/core/Registry")
                || owner.startsWith("net/minecraftforge/registries/");
    }

    private static ClassReader classReader(String resourceName)
            throws IOException {
        InputStream input = SharedAlchemyRecipesBytecodeTest.class
                .getResourceAsStream(resourceName);
        assertNotNull(input, resourceName);
        try (input) {
            return new ClassReader(input);
        }
    }

    private record FieldInfo(
            int access,
            String name,
            String descriptor,
            Object value
    ) {
    }
}
