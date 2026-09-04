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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedToolItemsBytecodeTest {

    private static final String SHARED_TOOL_ITEMS =
            "/ru/feytox/etherology/registry/item/SharedToolItems.class";
    private static final String BOOTSTRAP =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String OWNER =
            "ru/feytox/etherology/registry/item/SharedToolItems";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void declaresOneLazyPlainItemWithTheCanonicalStackLimit() throws IOException {
        AtomicInteger classAccess = new AtomicInteger();
        AtomicInteger privateConstructors = new AtomicInteger();
        AtomicInteger deferredRegistrations = new AtomicInteger();
        AtomicInteger supplierGets = new AtomicInteger();
        List<FieldInfo> fields = new ArrayList<>();
        List<String> registrationIds = new ArrayList<>();
        Set<String> forbiddenOwners = new LinkedHashSet<>();
        Map<String, List<String>> eventsByMethod = new LinkedHashMap<>();

        classReader(SHARED_TOOL_ITEMS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                checkOwner(superName, forbiddenOwners);
                if (interfaces != null) {
                    for (String interfaceName : interfaces) {
                        checkOwner(interfaceName, forbiddenOwners);
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
                List<String> events = new ArrayList<>();
                eventsByMethod.put(name + descriptor, events);
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean clinitIdLoaded;

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        checkOwner(type, forbiddenOwners);
                        if (opcode == Opcodes.NEW
                                && type.equals("net/minecraft/item/Item")) {
                            events.add("NEW Item");
                        }
                        if (opcode == Opcodes.NEW
                                && type.equals("net/minecraft/item/Item$Settings")) {
                            events.add("NEW Item$Settings");
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ICONST_1) {
                            events.add("INT:1");
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (name.equals("<clinit>") && value.equals("warp_counter")) {
                            registrationIds.add("warp_counter");
                            clinitIdLoaded = true;
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        checkOwner(owner, forbiddenOwners);
                        checkOwner(fieldDescriptor, forbiddenOwners);
                        if (name.equals("<clinit>")
                                && opcode == Opcodes.PUTSTATIC
                                && owner.equals(OWNER)
                                && fieldName.equals("WARP_COUNTER")) {
                            assertTrue(clinitIdLoaded);
                            clinitIdLoaded = false;
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
                        if (isDirectRegistryOwner(owner)) {
                            forbiddenOwners.add(owner + "#" + invokedName);
                        }
                        if (owner.equals("net/minecraft/item/Item$Settings")) {
                            events.add("Item$Settings#" + invokedName + invokedDescriptor);
                        }
                        if (owner.equals("net/minecraft/item/Item")
                                && invokedName.equals("<init>")) {
                            events.add("Item#<init>" + invokedDescriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue((classAccess.get() & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((classAccess.get() & Opcodes.ACC_FINAL) != 0);
        assertEquals(expectedFields(), fields);
        assertEquals(1, privateConstructors.get());
        assertEquals(List.of("warp_counter"), registrationIds);
        assertEquals(1, deferredRegistrations.get());
        assertEquals(0, supplierGets.get());
        assertEquals(Set.of(), forbiddenOwners);

        List<List<String>> itemFactories = eventsByMethod.values().stream()
                .filter(events -> events.contains("NEW Item"))
                .toList();
        assertEquals(1, itemFactories.size());
        assertEquals(
                List.of(
                        "NEW Item",
                        "NEW Item$Settings",
                        "Item$Settings#<init>()V",
                        "INT:1",
                        "Item$Settings#maxCount(I)Lnet/minecraft/item/Item$Settings;",
                        "Item#<init>(Lnet/minecraft/item/Item$Settings;)V"
                ),
                itemFactories.get(0)
        );
    }

    @Test
    void attachesOnceAfterFoodAndBeforeBlockEntities() throws IOException {
        List<String> invocations = methodInvocations(
                classReader(BOOTSTRAP),
                "initialize"
        );
        String foodRegistration =
                "ru/feytox/etherology/registry/item/SharedFoodItems#register()V";
        String toolRegistration = OWNER + "#register()V";
        String blockEntityRegistration =
                "ru/feytox/etherology/registry/block/SharedBlockEntities#register()V";
        int toolIndex = invocations.indexOf(toolRegistration);

        assertTrue(toolIndex > 0);
        assertEquals(foodRegistration, invocations.get(toolIndex - 1));
        assertEquals(blockEntityRegistration, invocations.get(toolIndex + 1));
        assertEquals(
                1,
                invocations.stream().filter(toolRegistration::equals).count()
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                methodInvocations(classReader(SHARED_TOOL_ITEMS), "register")
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
                        "WARP_COUNTER",
                        "Ldev/architectury/registry/registries/RegistrySupplier;",
                        "Ldev/architectury/registry/registries/RegistrySupplier"
                                + "<Lnet/minecraft/item/Item;>;"
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
        InputStream stream = SharedToolItemsBytecodeTest.class.getResourceAsStream(resource);
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
