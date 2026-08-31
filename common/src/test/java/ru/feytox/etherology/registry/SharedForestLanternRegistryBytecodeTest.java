package ru.feytox.etherology.registry;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedForestLanternRegistryBytecodeTest {

    private static final String BLOCK_CLASS =
            "/ru/feytox/etherology/registry/block/SharedForestLanternBlocks.class";
    private static final String ITEM_CLASS =
            "/ru/feytox/etherology/registry/item/SharedForestLanternBlockItems.class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String BLOCK_OWNER =
            "ru/feytox/etherology/registry/block/SharedForestLanternBlocks";
    private static final String ITEM_OWNER =
            "ru/feytox/etherology/registry/item/SharedForestLanternBlockItems";
    private static final String FOREST_LANTERN_BLOCK =
            "ru/feytox/etherology/block/forestLantern/ForestLanternBlock";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";

    @Test
    void declaresOneLazyCustomBlockOwnedByTheSharedRegistry() throws IOException {
        assertEquals(
                Map.of(
                        "BLOCKS",
                        "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                        "FOREST_LANTERN",
                        "Ldev/architectury/registry/registries/RegistrySupplier;"
                ),
                fields(BLOCK_CLASS)
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier"
                        + "<Lru/feytox/etherology/block/forestLantern/"
                        + "ForestLanternBlock;>;",
                fieldSignatures(BLOCK_CLASS).get("FOREST_LANTERN")
        );

        RegistrationTrace trace = registrationTrace(BLOCK_CLASS, BLOCK_OWNER);
        assertEquals(List.of("forest_lantern"), trace.ids());
        assertEquals(List.of("FOREST_LANTERN"), trace.assignedFields());
        assertEquals(1, trace.deferredRegistrationCalls());
        assertEquals(0, trace.supplierGetCalls());
        assertEquals(Set.of(), trace.directRegistryCalls());
        assertEquals(
                List.of(FOREST_LANTERN_BLOCK + "#<init>()V"),
                trace.factoryHandles()
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                invocations(BLOCK_CLASS, "register")
        );
        assertNoLoaderSpecificReferences(BLOCK_CLASS);
    }

    @Test
    void declaresOneLazyMappedBlockItemWithoutResolvingTheBlockEarly()
            throws IOException {
        assertEquals(
                Map.of(
                        "ITEMS",
                        "Lru/feytox/etherology/registry/SharedDeferredRegister;",
                        "FOREST_LANTERN_ITEM",
                        "Ldev/architectury/registry/registries/RegistrySupplier;"
                ),
                fields(ITEM_CLASS)
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier"
                        + "<Lnet/minecraft/item/BlockItem;>;",
                fieldSignatures(ITEM_CLASS).get("FOREST_LANTERN_ITEM")
        );

        RegistrationTrace trace = registrationTrace(ITEM_CLASS, ITEM_OWNER);
        assertEquals(List.of("forest_lantern"), trace.ids());
        assertEquals(List.of("FOREST_LANTERN_ITEM"), trace.assignedFields());
        assertEquals(1, trace.deferredRegistrationCalls());
        assertEquals(0, trace.supplierGetCalls());
        assertEquals(Set.of(), trace.directRegistryCalls());
        assertEquals(
                List.of(),
                fieldsReadInMethod(ITEM_CLASS, "<clinit>", BLOCK_OWNER)
        );

        BlockItemTrace item = blockItemTrace();
        assertEquals(1, item.blockSupplierReads());
        assertEquals(1, item.blockSupplierGets());
        assertEquals(1, item.blockItemConstructions());
        assertEquals(1, item.defaultSettingsConstructions());
        assertEquals(1, item.appendBlocksCalls());
        assertEquals(1, item.blockItemMapReads());
        assertTrue(item.supplierGetIndex() < item.blockItemConstructionIndex());
        assertTrue(item.blockItemConstructionIndex() < item.appendBlocksIndex());
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach()V"),
                invocations(ITEM_CLASS, "register")
        );
        assertNoLoaderSpecificReferences(ITEM_CLASS);
    }

    @Test
    void attachesTheBlockBeforeItsItemExactlyOnceInTheSharedBootstrap()
            throws IOException {
        List<String> invocations = invocations(BOOTSTRAP_CLASS, "initialize");
        String blockRegistration = BLOCK_OWNER + "#register()V";
        String itemRegistration = ITEM_OWNER + "#register()V";

        assertEquals(1, count(invocations, blockRegistration));
        assertEquals(1, count(invocations, itemRegistration));
        assertTrue(invocations.indexOf(blockRegistration) < invocations.indexOf(itemRegistration));
        assertTrue(
                invocations.indexOf(itemRegistration)
                        < invocations.indexOf(
                                "ru/feytox/etherology/registry/item/SharedItems#register()V"
                        )
        );
    }

    private static Map<String, String> fields(String resource) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.put(name, descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return fields;
    }

    private static Map<String, String> fieldSignatures(String resource)
            throws IOException {
        Map<String, String> signatures = new LinkedHashMap<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                signatures.put(name, signature);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return signatures;
    }

    private static RegistrationTrace registrationTrace(String resource, String classOwner)
            throws IOException {
        List<String> ids = new ArrayList<>();
        List<String> assignedFields = new ArrayList<>();
        List<String> factoryHandles = new ArrayList<>();
        AtomicInteger deferredRegistrationCalls = new AtomicInteger();
        AtomicInteger supplierGetCalls = new AtomicInteger();
        Set<String> directRegistryCalls = new LinkedHashSet<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String methodName,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!methodName.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id) {
                            ids.add(id);
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                            String name,
                            String descriptor,
                            Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments
                    ) {
                        for (Object argument : bootstrapMethodArguments) {
                            if (argument instanceof Handle handle
                                    && handle.getOwner().equals(FOREST_LANTERN_BLOCK)
                                    && handle.getName().equals("<init>")) {
                                factoryHandles.add(
                                        handle.getOwner()
                                                + "#"
                                                + handle.getName()
                                                + handle.getDesc()
                                );
                            }
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(classOwner)
                                && !name.equals("BLOCKS")
                                && !name.equals("ITEMS")) {
                            assignedFields.add(name);
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
                                && name.equals("register")) {
                            deferredRegistrationCalls.incrementAndGet();
                        }
                        if (owner.equals(REGISTRY_SUPPLIER) && name.equals("get")) {
                            supplierGetCalls.incrementAndGet();
                        }
                        if (isDirectRegistryOwner(owner)) {
                            directRegistryCalls.add(owner + "#" + name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new RegistrationTrace(
                ids,
                assignedFields,
                factoryHandles,
                deferredRegistrationCalls.get(),
                supplierGetCalls.get(),
                directRegistryCalls
        );
    }

    private static BlockItemTrace blockItemTrace() throws IOException {
        List<BlockItemTrace> traces = new ArrayList<>();
        classReader(ITEM_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                AtomicInteger instructionIndex = new AtomicInteger();
                AtomicInteger supplierReads = new AtomicInteger();
                AtomicInteger supplierGets = new AtomicInteger();
                AtomicInteger constructions = new AtomicInteger();
                AtomicInteger settingsConstructions = new AtomicInteger();
                AtomicInteger appendCalls = new AtomicInteger();
                AtomicInteger blockItemMapReads = new AtomicInteger();
                AtomicInteger supplierGetIndex = new AtomicInteger(-1);
                AtomicInteger constructionIndex = new AtomicInteger(-1);
                AtomicInteger appendIndex = new AtomicInteger(-1);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        instructionIndex.incrementAndGet();
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals("net/minecraft/item/Item")
                                && name.equals("BLOCK_ITEMS")) {
                            blockItemMapReads.incrementAndGet();
                        }
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(BLOCK_OWNER)
                                && name.equals("FOREST_LANTERN")) {
                            supplierReads.incrementAndGet();
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
                        int index = instructionIndex.incrementAndGet();
                        if (owner.equals(REGISTRY_SUPPLIER) && name.equals("get")) {
                            supplierGets.incrementAndGet();
                            supplierGetIndex.set(index);
                        }
                        if (owner.equals("net/minecraft/item/BlockItem")
                                && name.equals("<init>")) {
                            constructions.incrementAndGet();
                            constructionIndex.set(index);
                        }
                        if (owner.equals("net/minecraft/item/Item$Settings")
                                && name.equals("<init>")) {
                            settingsConstructions.incrementAndGet();
                        }
                        if (owner.equals("net/minecraft/item/BlockItem")
                                && name.equals("appendBlocks")) {
                            appendCalls.incrementAndGet();
                            appendIndex.set(index);
                        }
                    }

                    @Override
                    public void visitEnd() {
                        if (constructions.get() == 1) {
                            traces.add(new BlockItemTrace(
                                    supplierReads.get(),
                                    supplierGets.get(),
                                    constructions.get(),
                                    settingsConstructions.get(),
                                    appendCalls.get(),
                                    blockItemMapReads.get(),
                                    supplierGetIndex.get(),
                                    constructionIndex.get(),
                                    appendIndex.get()
                            ));
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, traces.size());
        return traces.get(0);
    }

    private static List<String> fieldsReadInMethod(
            String resource,
            String methodName,
            String expectedOwner
    ) throws IOException {
        List<String> fields = new ArrayList<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
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
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals(expectedOwner)) {
                            fields.add(name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return fields;
    }

    private static List<String> invocations(String resource, String methodName)
            throws IOException {
        List<String> invocations = new ArrayList<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
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

    private static long count(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }

    private static boolean isDirectRegistryOwner(String owner) {
        return owner.equals("net/minecraft/registry/Registry")
                || owner.equals("net/minecraft/class_2378")
                || owner.equals("net/minecraft/core/Registry")
                || owner.startsWith("net/minecraftforge/registries/");
    }

    private static void assertNoLoaderSpecificReferences(String resource)
            throws IOException {
        Set<String> forbiddenOwners = new LinkedHashSet<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                check(superName);
                check(signature);
                if (interfaces != null) {
                    for (String interfaceName : interfaces) {
                        check(interfaceName);
                    }
                }
            }

            @Override
            public org.objectweb.asm.FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                check(descriptor);
                check(signature);
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
                check(descriptor);
                check(signature);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        check(type);
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        check(owner);
                        check(descriptor);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        check(owner);
                        check(descriptor);
                    }
                };
            }

            private void check(String value) {
                if (value != null
                        && (value.contains("net/fabricmc/")
                        || value.contains("net/minecraftforge/")
                        || value.contains("ru/feytox/etherology/client/"))) {
                    forbiddenOwners.add(value);
                }
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertFalse(forbiddenOwners.size() > 0, forbiddenOwners.toString());
    }

    private static ClassReader classReader(String resource) throws IOException {
        InputStream stream = SharedForestLanternRegistryBytecodeTest.class
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record RegistrationTrace(
            List<String> ids,
            List<String> assignedFields,
            List<String> factoryHandles,
            int deferredRegistrationCalls,
            int supplierGetCalls,
            Set<String> directRegistryCalls
    ) {
    }

    private record BlockItemTrace(
            int blockSupplierReads,
            int blockSupplierGets,
            int blockItemConstructions,
            int defaultSettingsConstructions,
            int appendBlocksCalls,
            int blockItemMapReads,
            int supplierGetIndex,
            int blockItemConstructionIndex,
            int appendBlocksIndex
    ) {
    }
}
