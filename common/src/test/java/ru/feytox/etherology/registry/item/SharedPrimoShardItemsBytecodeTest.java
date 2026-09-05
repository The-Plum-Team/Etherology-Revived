package ru.feytox.etherology.registry.item;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedPrimoShardItemsBytecodeTest {

    private static final String RESOURCE =
            "/ru/feytox/etherology/registry/item/SharedPrimoShardItems.class";
    private static final String BOOTSTRAP =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String OWNER =
            "ru/feytox/etherology/registry/item/SharedPrimoShardItems";
    private static final String PRIMO_SHARD =
            "ru/feytox/etherology/item/PrimoShard";
    private static final String SEAL_TYPE =
            "ru/feytox/etherology/magic/seal/SealType";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    private static final Map<String, String> EXACT_ITEMS = exactItems();

    @Test
    void ownsExactlyFourLazyTypedRegistrationsInCanonicalOrder()
            throws IOException {
        AtomicInteger classAccess = new AtomicInteger();
        AtomicInteger privateConstructors = new AtomicInteger();
        AtomicInteger deferredRegistrations = new AtomicInteger();
        AtomicInteger supplierGets = new AtomicInteger();
        List<FieldInfo> fields = new ArrayList<>();
        Map<String, String> registrations = new LinkedHashMap<>();
        Map<String, String> registrationFactories = new LinkedHashMap<>();
        Map<String, FactoryInfo> factories = new LinkedHashMap<>();
        Set<String> forbiddenOwners = new LinkedHashSet<>();

        classReader(RESOURCE).accept(new ClassVisitor(Opcodes.ASM9) {
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
                checkForbidden(descriptor, forbiddenOwners);
                checkForbidden(signature, forbiddenOwners);
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
                checkForbidden(descriptor, forbiddenOwners);
                checkForbidden(signature, forbiddenOwners);
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;
                    private String pendingFactory;
                    private String sealType;
                    private int constructions;
                    private int constructorCalls;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (name.equals("<clinit>")
                                && value instanceof String id
                                && id.startsWith("primoshard_")) {
                            pendingId = id;
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        checkForbidden(type, forbiddenOwners);
                        if (opcode == Opcodes.NEW && type.equals(PRIMO_SHARD)) {
                            constructions++;
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String invokedName,
                            String invokedDescriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        checkForbidden(invokedDescriptor, forbiddenOwners);
                        checkForbidden(
                                bootstrapMethodHandle.getOwner(),
                                forbiddenOwners
                        );
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle) {
                                checkForbidden(handle.getOwner(), forbiddenOwners);
                                checkForbidden(handle.getDesc(), forbiddenOwners);
                                if (name.equals("<clinit>")
                                        && handle.getOwner().equals(OWNER)
                                        && handle.getName().startsWith("lambda$")) {
                                    pendingFactory = methodKey(handle);
                                }
                            }
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String visitedOwner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        checkForbidden(visitedOwner, forbiddenOwners);
                        checkForbidden(fieldDescriptor, forbiddenOwners);
                        if (name.equals("<clinit>")
                                && opcode == Opcodes.PUTSTATIC
                                && visitedOwner.equals(OWNER)
                                && pendingId != null) {
                            registrations.put(fieldName, pendingId);
                            registrationFactories.put(fieldName, pendingFactory);
                            pendingId = null;
                            pendingFactory = null;
                        }
                        if (opcode == Opcodes.GETSTATIC
                                && visitedOwner.equals(SEAL_TYPE)
                                && isExpectedSealType(fieldName)) {
                            sealType = fieldName;
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String visitedOwner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        checkForbidden(visitedOwner, forbiddenOwners);
                        checkForbidden(invokedDescriptor, forbiddenOwners);
                        if (visitedOwner.equals(SHARED_DEFERRED_REGISTER)
                                && invokedName.equals("register")) {
                            deferredRegistrations.incrementAndGet();
                        }
                        if (visitedOwner.equals(REGISTRY_SUPPLIER)
                                && invokedName.equals("get")) {
                            supplierGets.incrementAndGet();
                        }
                        if (visitedOwner.equals(PRIMO_SHARD)
                                && invokedName.equals("<init>")
                                && invokedDescriptor.equals("(L" + SEAL_TYPE + ";)V")) {
                            constructorCalls++;
                        }
                        if (isDirectRegistryOwner(visitedOwner)) {
                            forbiddenOwners.add(visitedOwner + "#" + invokedName);
                        }
                    }

                    @Override
                    public void visitEnd() {
                        if (name.startsWith("lambda$")
                                && (sealType != null
                                || constructions != 0
                                || constructorCalls != 0)) {
                            factories.put(
                                    name + descriptor,
                                    new FactoryInfo(
                                            sealType,
                                            constructions,
                                            constructorCalls
                                    )
                            );
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(expectedFields(), fields);
        assertEquals(1, privateConstructors.get());
        assertEquals(
                new ArrayList<>(EXACT_ITEMS.entrySet()),
                new ArrayList<>(registrations.entrySet())
        );
        assertEquals(4, deferredRegistrations.get());
        assertEquals(0, supplierGets.get());
        assertEquals(Set.of(), forbiddenOwners);

        assertEquals(EXACT_ITEMS.keySet(), registrationFactories.keySet());
        Set<String> usedFactories = new LinkedHashSet<>();
        for (String fieldName : EXACT_ITEMS.keySet()) {
            String factoryMethod = registrationFactories.get(fieldName);
            assertNotNull(factoryMethod, fieldName);
            FactoryInfo factory = factories.get(factoryMethod);
            assertNotNull(factory, fieldName + " factory " + factoryMethod);
            assertEquals(expectedSealType(fieldName), factory.sealType());
            assertEquals(1, factory.constructions());
            assertEquals(1, factory.constructorCalls());
            usedFactories.add(factoryMethod);
        }
        assertEquals(factories.keySet(), usedFactories);
    }

    @Test
    void attachesOnceBetweenLensAndAlchemyDuringCommonBootstrap()
            throws IOException {
        List<String> invocations = methodInvocations(
                classReader(BOOTSTRAP),
                "initialize"
        );
        String lensRegistration =
                "ru/feytox/etherology/registry/item/SharedLensItems#register()V";
        String primoShardRegistration = OWNER + "#register()V";
        String alchemyRegistration =
                "ru/feytox/etherology/registry/misc/SharedAlchemyRecipes"
                        + "#register()V";
        int primoShardIndex = invocations.indexOf(primoShardRegistration);

        assertTrue(primoShardIndex > 0);
        assertEquals(lensRegistration, invocations.get(primoShardIndex - 1));
        assertEquals(alchemyRegistration, invocations.get(primoShardIndex + 1));
        assertEquals(
                1,
                invocations.stream().filter(primoShardRegistration::equals).count()
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                methodInvocations(classReader(RESOURCE), "register")
        );
    }

    private static Map<String, String> exactItems() {
        Map<String, String> items = new LinkedHashMap<>();
        items.put("PRIMOSHARD_KETA", "primoshard_keta");
        items.put("PRIMOSHARD_RELLA", "primoshard_rella");
        items.put("PRIMOSHARD_CLOS", "primoshard_clos");
        items.put("PRIMOSHARD_VIA", "primoshard_via");
        return items;
    }

    private static boolean isExpectedSealType(String fieldName) {
        return EXACT_ITEMS.containsKey("PRIMOSHARD_" + fieldName);
    }

    private static String expectedSealType(String fieldName) {
        return fieldName.substring("PRIMOSHARD_".length());
    }

    private static String methodKey(Handle handle) {
        return handle.getName() + handle.getDesc();
    }

    private static List<FieldInfo> expectedFields() {
        List<FieldInfo> fields = new ArrayList<>();
        fields.add(new FieldInfo(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "ITEMS",
                "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                "Lru/feytox/etherology/registry/SharedDeferredRegister"
                        + "<Lnet/minecraft/item/Item;>;"
        ));
        EXACT_ITEMS.keySet().forEach(name -> fields.add(new FieldInfo(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                name,
                "Ldev/architectury/registry/registries/RegistrySupplier;",
                "Ldev/architectury/registry/registries/RegistrySupplier"
                        + "<Lru/feytox/etherology/item/PrimoShard;>;"
        )));
        return fields;
    }

    private static void checkForbidden(
            String reference,
            Set<String> forbiddenOwners
    ) {
        if (reference != null && (reference.contains("net/fabricmc/")
                || reference.contains("net/minecraftforge/")
                || reference.contains("ru/feytox/etherology/client/"))) {
            forbiddenOwners.add(reference);
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
        InputStream stream = SharedPrimoShardItemsBytecodeTest.class
                .getResourceAsStream(resource);
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

    private record FactoryInfo(
            String sealType,
            int constructions,
            int constructorCalls
    ) {
    }
}
