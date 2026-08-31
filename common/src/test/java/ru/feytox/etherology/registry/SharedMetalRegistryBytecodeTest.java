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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class SharedMetalRegistryBytecodeTest {

    private static final String BLOCK_CLASS =
            "/ru/feytox/etherology/registry/block/SharedMetalBlocks.class";
    private static final String ITEM_CLASS =
            "/ru/feytox/etherology/registry/item/SharedMetalBlockItems.class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String BLOCK_OWNER =
            "ru/feytox/etherology/registry/block/SharedMetalBlocks";
    private static final String ITEM_OWNER =
            "ru/feytox/etherology/registry/item/SharedMetalBlockItems";

    @Test
    void declaresExactlyTheThreeCanonicalMetalBlockFactories() throws IOException {
        Map<String, BlockRegistration> registrations = blockRegistrations();

        assertEquals(
                List.of("AZEL_BLOCK", "ETHRIL_BLOCK", "EBONY_BLOCK"),
                new ArrayList<>(publicRegistrySuppliers(BLOCK_CLASS).keySet())
        );
        assertEquals(
                List.of("azel_block", "ethril_block", "ebony_block"),
                registrations.values().stream().map(BlockRegistration::id).toList()
        );
        assertBlockFactory(
                registrations.get("AZEL_BLOCK").factoryMethod(),
                List.of("Blocks#IRON_BLOCK", "MapColor#LAPIS_BLUE"),
                List.of("Settings#copy", "Settings#mapColor", "Block#<init>")
        );
        assertBlockFactory(
                registrations.get("ETHRIL_BLOCK").factoryMethod(),
                List.of("Blocks#GOLD_BLOCK"),
                List.of("Settings#copy", "Block#<init>")
        );
        assertBlockFactory(
                registrations.get("EBONY_BLOCK").factoryMethod(),
                List.of("Blocks#DIAMOND_BLOCK", "MapColor#ORANGE"),
                List.of("Settings#copy", "Settings#mapColor", "Block#<init>")
        );
        assertEquals(0, countSupplierGetsInClassInitializer(BLOCK_CLASS));
    }

    @Test
    void bindsExactlyOnePlaceableItemToEachSharedMetalBlock() throws IOException {
        assertEquals(
                List.of(
                        "AZEL_BLOCK_ITEM",
                        "ETHRIL_BLOCK_ITEM",
                        "EBONY_BLOCK_ITEM"
                ),
                new ArrayList<>(publicRegistrySuppliers(ITEM_CLASS).keySet())
        );
        assertEquals(
                Map.of(
                        "AZEL_BLOCK_ITEM", new ItemBinding("azel_block", "AZEL_BLOCK"),
                        "ETHRIL_BLOCK_ITEM", new ItemBinding("ethril_block", "ETHRIL_BLOCK"),
                        "EBONY_BLOCK_ITEM", new ItemBinding("ebony_block", "EBONY_BLOCK")
                ),
                itemBindings()
        );
        assertEquals(
                List.of(
                        "RegistrySupplier#get",
                        "BlockItem#<init>",
                        "Item#BLOCK_ITEMS",
                        "BlockItem#appendBlocks",
                        "ARETURN"
                ),
                blockItemBindingEvents()
        );
        assertEquals(0, countSupplierGetsInClassInitializer(ITEM_CLASS));
    }

    @Test
    void attachesBlocksThenTheirItemsBeforeOtherSharedItems() throws IOException {
        List<String> invocations = methodInvocations(BOOTSTRAP_CLASS, "initialize");

        assertEquals(
                List.of(
                        "ru/feytox/etherology/registry/block/SharedBlocks#register()V",
                        BLOCK_OWNER + "#register()V",
                        ITEM_OWNER + "#register()V",
                        "ru/feytox/etherology/registry/item/SharedItems#register()V",
                        "ru/feytox/etherology/registry/item/SharedMaterialItems#register()V"
                ),
                invocations.subList(0, 5)
        );
    }

    private static Map<String, String> publicRegistrySuppliers(String resource)
            throws IOException {
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
                int requiredAccess = Opcodes.ACC_PUBLIC
                        | Opcodes.ACC_STATIC
                        | Opcodes.ACC_FINAL;
                if ((access & requiredAccess) == requiredAccess
                        && descriptor.equals(
                                "Ldev/architectury/registry/registries/RegistrySupplier;"
                        )) {
                    fields.put(name, signature);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return fields;
    }

    private static Map<String, BlockRegistration> blockRegistrations()
            throws IOException {
        Map<String, BlockRegistration> registrations = new LinkedHashMap<>();
        classReader(BLOCK_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;
                    private String pendingFactory;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id) {
                            pendingId = id;
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
                                    && handle.getOwner().equals(BLOCK_OWNER)) {
                                pendingFactory = handle.getName();
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
                                && owner.equals(BLOCK_OWNER)
                                && pendingId != null
                                && pendingFactory != null) {
                            registrations.put(
                                    name,
                                    new BlockRegistration(pendingId, pendingFactory)
                            );
                            pendingId = null;
                            pendingFactory = null;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return registrations;
    }

    private static void assertBlockFactory(
            String factoryMethod,
            List<String> expectedFields,
            List<String> expectedInvocations
    ) throws IOException {
        List<String> fields = new ArrayList<>();
        List<String> invocations = new ArrayList<>();
        classReader(BLOCK_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(factoryMethod)) {
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
                        if (owner.equals("net/minecraft/block/Blocks")) {
                            fields.add("Blocks#" + name);
                        }
                        if (owner.equals("net/minecraft/block/MapColor")) {
                            fields.add("MapColor#" + name);
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
                        if (owner.equals("net/minecraft/block/AbstractBlock$Settings")) {
                            invocations.add("Settings#" + name);
                        }
                        if (owner.equals("net/minecraft/block/Block")) {
                            invocations.add("Block#" + name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(expectedFields, fields);
        assertEquals(expectedInvocations, invocations);
    }

    private static Map<String, ItemBinding> itemBindings() throws IOException {
        Map<String, ItemBinding> bindings = new LinkedHashMap<>();
        classReader(ITEM_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;
                    private String pendingBlock;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id) {
                            pendingId = id;
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals(BLOCK_OWNER)) {
                            pendingBlock = name;
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(ITEM_OWNER)
                                && pendingId != null
                                && pendingBlock != null) {
                            bindings.put(name, new ItemBinding(pendingId, pendingBlock));
                            pendingId = null;
                            pendingBlock = null;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return bindings;
    }

    private static List<String> blockItemBindingEvents() throws IOException {
        Map<String, List<String>> eventsByMethod = new LinkedHashMap<>();
        AtomicInteger appendCalls = new AtomicInteger();
        classReader(ITEM_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                List<String> events = new ArrayList<>();
                eventsByMethod.put(name + descriptor, events);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (owner.equals("net/minecraft/item/Item")
                                && name.equals("BLOCK_ITEMS")) {
                            events.add("Item#BLOCK_ITEMS");
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
                        if (owner.equals(
                                "dev/architectury/registry/registries/RegistrySupplier"
                        ) && name.equals("get")) {
                            events.add("RegistrySupplier#get");
                        }
                        if (owner.equals("net/minecraft/item/BlockItem")
                                && name.equals("<init>")) {
                            events.add("BlockItem#<init>");
                        }
                        if (owner.equals("net/minecraft/item/BlockItem")
                                && name.equals("appendBlocks")) {
                            events.add("BlockItem#appendBlocks");
                            appendCalls.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN && !events.isEmpty()) {
                            events.add("ARETURN");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(1, appendCalls.get());
        return eventsByMethod.values().stream()
                .filter(events -> events.contains("BlockItem#appendBlocks"))
                .findFirst()
                .orElseThrow();
    }

    private static int countSupplierGetsInClassInitializer(String resource)
            throws IOException {
        AtomicInteger supplierGets = new AtomicInteger();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) {
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
                        if (owner.equals(
                                "dev/architectury/registry/registries/RegistrySupplier"
                        ) && name.equals("get")) {
                            supplierGets.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return supplierGets.get();
    }

    private static List<String> methodInvocations(String resource, String methodName)
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

    private static ClassReader classReader(String resource) throws IOException {
        InputStream stream = SharedMetalRegistryBytecodeTest.class
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record BlockRegistration(String id, String factoryMethod) {
    }

    private record ItemBinding(String id, String blockField) {
    }
}
