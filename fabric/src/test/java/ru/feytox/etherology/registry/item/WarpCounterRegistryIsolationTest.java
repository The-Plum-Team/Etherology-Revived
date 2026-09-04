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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class WarpCounterRegistryIsolationTest {

    private static final String TOOL_ITEMS =
            "ru/feytox/etherology/registry/item/ToolItems.class";
    private static final String TOOL_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/ToolItems";
    private static final String ETHEROLOGY = "ru/feytox/etherology/Etherology.class";
    private static final String SHARED_TOOL_ITEMS =
            "ru/feytox/etherology/registry/item/SharedToolItems";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void legacyFieldAliasesTheSharedSupplierWithoutOwningTheId()
            throws IOException {
        AtomicInteger fieldAccess = new AtomicInteger(-1);
        AtomicInteger legacyIdLoads = new AtomicInteger();
        AtomicInteger legacyAssignments = new AtomicInteger();
        List<String> aliasEvents = new ArrayList<>();

        classReader(TOOL_ITEMS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals("WARP_COUNTER")) {
                    fieldAccess.set(access);
                    assertEquals("Lnet/minecraft/item/Item;", descriptor);
                }
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
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean recordingAlias;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value.equals("warp_counter")) {
                            legacyIdLoads.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        if (name.equals("<clinit>")
                                && opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_TOOL_ITEMS)
                                && fieldName.equals("WARP_COUNTER")) {
                            recordingAlias = true;
                            aliasEvents.add("SharedToolItems#WARP_COUNTER");
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(TOOL_ITEMS_OWNER)
                                && fieldName.equals("WARP_COUNTER")) {
                            legacyAssignments.incrementAndGet();
                            if (recordingAlias) {
                                aliasEvents.add("ToolItems#WARP_COUNTER");
                                recordingAlias = false;
                            }
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invocationName,
                            String invocationDescriptor,
                            boolean isInterface
                    ) {
                        if (recordingAlias
                                && owner.equals(REGISTRY_SUPPLIER)
                                && invocationName.equals("get")) {
                            aliasEvents.add("RegistrySupplier#get");
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (recordingAlias
                                && opcode == Opcodes.CHECKCAST
                                && type.equals("net/minecraft/item/Item")) {
                            aliasEvents.add("CHECKCAST Item");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldAccess.get()
        );
        assertEquals(
                List.of(
                        "SharedToolItems#WARP_COUNTER",
                        "RegistrySupplier#get",
                        "CHECKCAST Item",
                        "ToolItems#WARP_COUNTER"
                ),
                aliasEvents
        );
        assertEquals(1, legacyAssignments.get());
        assertEquals(0, legacyIdLoads.get());
    }

    @Test
    void fabricAttachesTheSharedToolRegistryAfterFoodAndBeforeLegacyItems()
            throws IOException {
        String foodOwner =
                "ru/feytox/etherology/registry/item/SharedFoodItems";
        String legacyOwner =
                "ru/feytox/etherology/registry/item/EItems";
        Set<String> owners = Set.of(foodOwner, SHARED_TOOL_ITEMS, legacyOwner);

        assertEquals(
                List.of(
                        foodOwner + "#register",
                        SHARED_TOOL_ITEMS + "#register",
                        legacyOwner + "#registerItems"
                ),
                referencedMethods(ETHEROLOGY, "initialize", owners)
        );
    }

    private static List<String> referencedMethods(
            String resourceName,
            String methodName,
            Set<String> owners
    ) throws IOException {
        List<String> invocations = new ArrayList<>();
        ClassReader reader = classReader(resourceName);
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
                        if (owners.contains(owner)) {
                            invocations.add(owner + "#" + name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static ClassReader classReader(String resourceName) throws IOException {
        InputStream stream = WarpCounterRegistryIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(resourceName);
        assertNotNull(stream, "Missing class resource " + resourceName);
        try (stream) {
            return new ClassReader(stream);
        }
    }
}
