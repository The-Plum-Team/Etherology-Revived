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

final class SharedAttrahiteRegistryBytecodeTest {

    private static final String BLOCK_CLASS =
            "/ru/feytox/etherology/registry/block/SharedAttrahiteBlocks.class";
    private static final String ITEM_CLASS =
            "/ru/feytox/etherology/registry/item/SharedAttrahiteBlockItems.class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String BLOCK_OWNER =
            "ru/feytox/etherology/registry/block/SharedAttrahiteBlocks";
    private static final String ITEM_OWNER =
            "ru/feytox/etherology/registry/item/SharedAttrahiteBlockItems";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final List<String> BLOCK_FIELDS = List.of(
            "ATTRAHITE",
            "ATTRAHITE_BRICKS",
            "ATTRAHITE_BRICK_SLAB",
            "ATTRAHITE_BRICK_STAIRS"
    );
    private static final List<String> ITEM_FIELDS = List.of(
            "ATTRAHITE_ITEM",
            "ATTRAHITE_BRICKS_ITEM",
            "ATTRAHITE_BRICK_SLAB_ITEM",
            "ATTRAHITE_BRICK_STAIRS_ITEM"
    );
    private static final List<String> IDS = List.of(
            "attrahite",
            "attrahite_bricks",
            "attrahite_brick_slab",
            "attrahite_brick_stairs"
    );

    @Test
    void declaresExactlyFourLazyCanonicalBlockFactories() throws IOException {
        Map<String, FieldDefinition> fields = fields(BLOCK_CLASS);
        assertEquals(
                List.of(
                        "BLOCKS",
                        "ATTRAHITE",
                        "ATTRAHITE_BRICKS",
                        "ATTRAHITE_BRICK_SLAB",
                        "ATTRAHITE_BRICK_STAIRS"
                ),
                new ArrayList<>(fields.keySet())
        );
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fields.get("BLOCKS").access()
        );
        assertPublicSupplier(fields.get("ATTRAHITE"), "net/minecraft/block/Block");
        assertPublicSupplier(
                fields.get("ATTRAHITE_BRICKS"),
                "net/minecraft/block/Block"
        );
        assertPublicSupplier(
                fields.get("ATTRAHITE_BRICK_SLAB"),
                "net/minecraft/block/SlabBlock"
        );
        assertPublicSupplier(
                fields.get("ATTRAHITE_BRICK_STAIRS"),
                "net/minecraft/block/StairsBlock"
        );

        Map<String, BlockRegistration> registrations = blockRegistrations();
        assertEquals(BLOCK_FIELDS, new ArrayList<>(registrations.keySet()));
        assertEquals(
                IDS,
                registrations.values().stream().map(BlockRegistration::id).toList()
        );
        assertEquals(4, countInvocations(BLOCK_CLASS, SHARED_DEFERRED_REGISTER, "register"));
        assertEquals(0, countInvocations(BLOCK_CLASS, "<clinit>", REGISTRY_SUPPLIER, "get"));
        assertEquals(Set.of(), directRegistryInvocations(BLOCK_CLASS));

        assertEquals(
                List.of(
                        "Blocks#STONE",
                        "Settings#copy",
                        "BlockSoundGroup#GILDED_BLACKSTONE",
                        "Settings#sounds",
                        "Block#<init>"
                ),
                blockFactoryEvents(registrations.get("ATTRAHITE").factoryMethod())
        );
        assertEquals(
                List.of(
                        "Blocks#STONE_BRICKS",
                        "Settings#copy",
                        "Block#<init>"
                ),
                blockFactoryEvents(registrations.get("ATTRAHITE_BRICKS").factoryMethod())
        );
        assertEquals(
                List.of(
                        "Blocks#STONE_SLAB",
                        "Settings#copy",
                        "SlabBlock#<init>"
                ),
                blockFactoryEvents(
                        registrations.get("ATTRAHITE_BRICK_SLAB").factoryMethod()
                )
        );
        assertEquals(
                List.of(
                        "SharedAttrahiteBlocks#ATTRAHITE_BRICKS",
                        "RegistrySupplier#get",
                        "Block#getDefaultState",
                        "Settings#copy",
                        "StairsBlock#<init>"
                ),
                blockFactoryEvents(
                        registrations.get("ATTRAHITE_BRICK_STAIRS").factoryMethod()
                )
        );
        assertEquals(
                1,
                countInvocations(
                        BLOCK_CLASS,
                        registrations.get("ATTRAHITE_BRICK_STAIRS").factoryMethod(),
                        REGISTRY_SUPPLIER,
                        "get"
                )
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach"),
                methodInvocations(BLOCK_CLASS, "register", Set.of(SHARED_DEFERRED_REGISTER))
        );
        assertNoLoaderSpecificReferences(BLOCK_CLASS);
    }

    @Test
    void bindsExactlyFourLazyBlockItemsToTheCanonicalBlocks() throws IOException {
        Map<String, FieldDefinition> fields = fields(ITEM_CLASS);
        assertEquals(
                List.of(
                        "ITEMS",
                        "ATTRAHITE_ITEM",
                        "ATTRAHITE_BRICKS_ITEM",
                        "ATTRAHITE_BRICK_SLAB_ITEM",
                        "ATTRAHITE_BRICK_STAIRS_ITEM"
                ),
                new ArrayList<>(fields.keySet())
        );
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fields.get("ITEMS").access()
        );
        for (String itemField : ITEM_FIELDS) {
            assertPublicSupplier(fields.get(itemField), "net/minecraft/item/BlockItem");
        }

        Map<String, ItemBinding> bindings = itemBindings();
        assertEquals(ITEM_FIELDS, new ArrayList<>(bindings.keySet()));
        assertEquals(IDS, bindings.values().stream().map(ItemBinding::id).toList());
        assertEquals(
                BLOCK_FIELDS,
                bindings.values().stream().map(ItemBinding::blockField).toList()
        );
        assertEquals(0, countInvocations(ITEM_CLASS, "<clinit>", REGISTRY_SUPPLIER, "get"));
        assertEquals(4, countInvocations(ITEM_CLASS, "<clinit>", ITEM_OWNER, "registerBlockItem"));
        assertEquals(1, countInvocations(ITEM_CLASS, SHARED_DEFERRED_REGISTER, "register"));
        assertEquals(Set.of(), directRegistryInvocations(ITEM_CLASS));
        assertEquals(
                List.of(
                        "RegistrySupplier#get",
                        "Item.Settings#<init>",
                        "BlockItem#<init>",
                        "Item#BLOCK_ITEMS",
                        "BlockItem#appendBlocks",
                        "ARETURN"
                ),
                blockItemFactoryEvents()
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach"),
                methodInvocations(ITEM_CLASS, "register", Set.of(SHARED_DEFERRED_REGISTER))
        );
        assertNoLoaderSpecificReferences(ITEM_CLASS);
    }

    @Test
    void attachesBlocksThenItemsExactlyOnceBeforeGeneralSharedItems()
            throws IOException {
        List<String> invocations = methodInvocations(
                BOOTSTRAP_CLASS,
                "initialize",
                Set.of(
                        BLOCK_OWNER,
                        ITEM_OWNER,
                        "ru/feytox/etherology/registry/item/SharedItems"
                )
        );

        assertEquals(
                List.of(
                        BLOCK_OWNER + "#register",
                        ITEM_OWNER + "#register",
                        "ru/feytox/etherology/registry/item/SharedItems#register"
                ),
                invocations
        );
    }

    private static void assertPublicSupplier(
            FieldDefinition field,
            String suppliedType
    ) {
        assertNotNull(field);
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                field.access()
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier;",
                field.descriptor()
        );
        assertEquals(
                "Ldev/architectury/registry/registries/RegistrySupplier<L"
                        + suppliedType
                        + ";>;",
                field.signature()
        );
    }

    private static Map<String, FieldDefinition> fields(String resource)
            throws IOException {
        Map<String, FieldDefinition> fields = new LinkedHashMap<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fields.put(name, new FieldDefinition(access, descriptor, signature));
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
                        if (value instanceof String id && IDS.contains(id)) {
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
                                && BLOCK_FIELDS.contains(name)) {
                            assertNotNull(pendingId, name);
                            assertNotNull(pendingFactory, name);
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

    private static List<String> blockFactoryEvents(String factoryMethod)
            throws IOException {
        List<String> events = new ArrayList<>();
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
                        if (opcode != Opcodes.GETSTATIC) {
                            return;
                        }
                        if (owner.equals("net/minecraft/block/Blocks")) {
                            events.add("Blocks#" + name);
                        }
                        if (owner.equals("net/minecraft/sound/BlockSoundGroup")) {
                            events.add("BlockSoundGroup#" + name);
                        }
                        if (owner.equals(BLOCK_OWNER)) {
                            events.add("SharedAttrahiteBlocks#" + name);
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
                            events.add("Settings#" + name);
                        }
                        if (owner.equals("net/minecraft/block/Block")) {
                            events.add("Block#" + name);
                        }
                        if (owner.equals("net/minecraft/block/SlabBlock")) {
                            events.add("SlabBlock#" + name);
                        }
                        if (owner.equals("net/minecraft/block/StairsBlock")) {
                            events.add("StairsBlock#" + name);
                        }
                        if (owner.equals(REGISTRY_SUPPLIER) && name.equals("get")) {
                            events.add("RegistrySupplier#get");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return events;
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
                        if (value instanceof String id && IDS.contains(id)) {
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
                                && ITEM_FIELDS.contains(name)) {
                            assertNotNull(pendingId, name);
                            assertNotNull(pendingBlock, name);
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

    private static List<String> blockItemFactoryEvents() throws IOException {
        List<List<String>> candidates = new ArrayList<>();
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
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals("net/minecraft/item/Item")
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
                        if (owner.equals(REGISTRY_SUPPLIER) && name.equals("get")) {
                            events.add("RegistrySupplier#get");
                        }
                        if (owner.equals("net/minecraft/item/Item$Settings")
                                && name.equals("<init>")) {
                            events.add("Item.Settings#<init>");
                        }
                        if (owner.equals("net/minecraft/item/BlockItem")
                                && name.equals("<init>")) {
                            events.add("BlockItem#<init>");
                        }
                        if (owner.equals("net/minecraft/item/BlockItem")
                                && name.equals("appendBlocks")) {
                            events.add("BlockItem#appendBlocks");
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN && !events.isEmpty()) {
                            events.add("ARETURN");
                        }
                    }

                    @Override
                    public void visitEnd() {
                        if (events.contains("BlockItem#appendBlocks")) {
                            candidates.add(events);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, candidates.size());
        return candidates.get(0);
    }

    private static int countInvocations(
            String resource,
            String expectedOwner,
            String expectedMethod
    ) throws IOException {
        return countInvocations(resource, null, expectedOwner, expectedMethod);
    }

    private static int countInvocations(
            String resource,
            String expectedContainingMethod,
            String expectedOwner,
            String expectedMethod
    ) throws IOException {
        AtomicInteger count = new AtomicInteger();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (expectedContainingMethod != null
                        && !name.equals(expectedContainingMethod)) {
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
                        if (owner.equals(expectedOwner) && name.equals(expectedMethod)) {
                            count.incrementAndGet();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count.get();
    }

    private static Set<String> directRegistryInvocations(String resource)
            throws IOException {
        Set<String> invocations = new LinkedHashSet<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
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
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (isDirectRegistryOwner(owner)) {
                            invocations.add(owner + "#" + name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
    }

    private static List<String> methodInvocations(
            String resource,
            String methodName,
            Set<String> owners
    ) throws IOException {
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
                        if (owners.contains(owner)) {
                            invocations.add(owner + "#" + name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invocations;
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
        InputStream stream = SharedAttrahiteRegistryBytecodeTest.class
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record FieldDefinition(int access, String descriptor, String signature) {
    }

    private record BlockRegistration(String id, String factoryMethod) {
    }

    private record ItemBinding(String id, String blockField) {
    }
}
