package ru.feytox.etherology.registry.item;

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

final class SharedLensItemsBytecodeTest {

    private static final String SHARED_LENS_ITEMS =
            "/ru/feytox/etherology/registry/item/SharedLensItems.class";
    private static final String BOOTSTRAP =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String OWNER =
            "ru/feytox/etherology/registry/item/SharedLensItems";
    private static final String UNADJUSTED_LENS =
            "ru/feytox/etherology/item/UnadjustedLens";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void ownsOneLazyCanonicalUnadjustedLensSubtype() throws IOException {
        AtomicInteger classAccess = new AtomicInteger();
        AtomicInteger privateConstructors = new AtomicInteger();
        AtomicInteger deferredRegistrations = new AtomicInteger();
        AtomicInteger supplierGets = new AtomicInteger();
        AtomicInteger lensConstructions = new AtomicInteger();
        AtomicInteger lensConstructorCalls = new AtomicInteger();
        List<FieldInfo> fields = new ArrayList<>();
        List<String> registrationIds = new ArrayList<>();
        Set<String> forbiddenOwners = new LinkedHashSet<>();

        classReader(SHARED_LENS_ITEMS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                fields.add(new FieldInfo(access, name, descriptor, signature));
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
                    public void visitTypeInsn(int opcode, String type) {
                        checkOwner(type, forbiddenOwners);
                        if (opcode == Opcodes.NEW && type.equals(UNADJUSTED_LENS)) {
                            lensConstructions.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (name.equals("<clinit>")
                                && value.equals("unadjusted_lens")) {
                            registrationIds.add("unadjusted_lens");
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        checkOwner(owner, forbiddenOwners);
                        checkOwner(invokedDescriptor, forbiddenOwners);
                        if (owner.equals(SHARED_DEFERRED_REGISTER)
                                && invokedName.equals("register")) {
                            deferredRegistrations.incrementAndGet();
                        }
                        if (owner.equals(REGISTRY_SUPPLIER)
                                && invokedName.equals("get")) {
                            supplierGets.incrementAndGet();
                        }
                        if (owner.equals(UNADJUSTED_LENS)
                                && invokedName.equals("<init>")
                                && invokedDescriptor.equals("()V")) {
                            lensConstructorCalls.incrementAndGet();
                        }
                        if (isDirectRegistryOwner(owner)) {
                            forbiddenOwners.add(owner + "#" + invokedName);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(expectedFields(), fields);
        assertEquals(1, privateConstructors.get());
        assertEquals(List.of("unadjusted_lens"), registrationIds);
        assertEquals(1, deferredRegistrations.get());
        assertEquals(0, supplierGets.get());
        assertEquals(1, lensConstructions.get());
        assertEquals(1, lensConstructorCalls.get());
        assertEquals(Set.of(), forbiddenOwners);
    }

    @Test
    void attachesOnceAfterToolItemsAndBeforePrimoShardItems() throws IOException {
        List<String> invocations = methodInvocations(
                classReader(BOOTSTRAP),
                "initialize"
        );
        String toolRegistration =
                "ru/feytox/etherology/registry/item/SharedToolItems#register()V";
        String lensRegistration = OWNER + "#register()V";
        String primoShardRegistration =
                "ru/feytox/etherology/registry/item/SharedPrimoShardItems"
                        + "#register()V";
        int lensIndex = invocations.indexOf(lensRegistration);

        assertTrue(lensIndex > 0);
        assertEquals(toolRegistration, invocations.get(lensIndex - 1));
        assertEquals(primoShardRegistration, invocations.get(lensIndex + 1));
        assertEquals(
                1,
                invocations.stream().filter(lensRegistration::equals).count()
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                methodInvocations(classReader(SHARED_LENS_ITEMS), "register")
        );
    }

    private static List<FieldInfo> expectedFields() {
        return List.of(
                new FieldInfo(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "ITEMS",
                        "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                        "Lru/feytox/etherology/registry/SharedDeferredRegister"
                                + "<Lnet/minecraft/item/Item;>;"
                ),
                new FieldInfo(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "UNADJUSTED_LENS",
                        "Ldev/architectury/registry/registries/RegistrySupplier;",
                        "Ldev/architectury/registry/registries/RegistrySupplier"
                                + "<Lru/feytox/etherology/item/UnadjustedLens;>;"
                )
        );
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

    private static ClassReader classReader(String resource) throws IOException {
        InputStream stream = SharedLensItemsBytecodeTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record FieldInfo(
            int access,
            String name,
            String descriptor,
            String signature
    ) {
    }
}
