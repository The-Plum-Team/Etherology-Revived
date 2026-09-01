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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class SharedSlitheriteRegistryBytecodeTest {

    private static final String BLOCK_CLASS =
            "/ru/feytox/etherology/registry/block/SharedSlitheriteBlocks.class";
    private static final String ITEM_CLASS =
            "/ru/feytox/etherology/registry/item/SharedSlitheriteBlockItems.class";
    private static final String BOOTSTRAP_CLASS =
            "/ru/feytox/etherology/bootstrap/EtherologyBootstrap.class";
    private static final String BLOCK_OWNER =
            "ru/feytox/etherology/registry/block/SharedSlitheriteBlocks";
    private static final String ITEM_OWNER =
            "ru/feytox/etherology/registry/item/SharedSlitheriteBlockItems";
    private static final String SHARED_DEFERRED_REGISTER =
            "ru/feytox/etherology/registry/SharedDeferredRegister";
    private static final String REGISTRY_SUPPLIER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final List<String> BLOCK_FIELDS = List.of(
            "SLITHERITE",
            "SLITHERITE_STAIRS",
            "SLITHERITE_SLAB",
            "SLITHERITE_WALL"
    );
    private static final List<String> ITEM_FIELDS = List.of(
            "SLITHERITE_ITEM",
            "SLITHERITE_STAIRS_ITEM",
            "SLITHERITE_SLAB_ITEM",
            "SLITHERITE_WALL_ITEM"
    );
    private static final List<String> IDS = List.of(
            "slitherite",
            "slitherite_stairs",
            "slitherite_slab",
            "slitherite_wall"
    );

    @Test
    void declaresExactlyFourLazyCanonicalBlockFactories() throws IOException {
        Map<String, FieldDefinition> fields = fields(BLOCK_CLASS);
        assertEquals(
                List.of(
                        "BLOCKS",
                        "SLITHERITE",
                        "SLITHERITE_STAIRS",
                        "SLITHERITE_SLAB",
                        "SLITHERITE_WALL"
                ),
                new ArrayList<>(fields.keySet())
        );
        assertPublicSupplier(fields.get("SLITHERITE"), "net/minecraft/block/Block");
        assertPublicSupplier(
                fields.get("SLITHERITE_STAIRS"),
                "net/minecraft/block/StairsBlock"
        );
        assertPublicSupplier(
                fields.get("SLITHERITE_SLAB"),
                "net/minecraft/block/SlabBlock"
        );
        assertPublicSupplier(
                fields.get("SLITHERITE_WALL"),
                "net/minecraft/block/WallBlock"
        );

        Map<String, BlockRegistration> registrations = blockRegistrations();
        assertEquals(BLOCK_FIELDS, new ArrayList<>(registrations.keySet()));
        assertEquals(
                IDS,
                registrations.values().stream().map(BlockRegistration::id).toList()
        );
        assertEquals(0, invocationCount(BLOCK_CLASS, "<clinit>", REGISTRY_SUPPLIER, "get"));
        assertEquals(Set.of(), directRegistryInvocations(BLOCK_CLASS));

        assertEquals(
                List.of("Blocks#STONE", "Settings#copy", "Block#<init>"),
                blockFactoryEvents(registrations.get("SLITHERITE").factoryMethod())
        );
        assertEquals(
                List.of(
                        "SharedSlitheriteBlocks#SLITHERITE",
                        "RegistrySupplier#get",
                        "Block#getDefaultState",
                        "Settings#copy",
                        "StairsBlock#<init>"
                ),
                blockFactoryEvents(registrations.get("SLITHERITE_STAIRS").factoryMethod())
        );
        assertEquals(
                List.of("Blocks#STONE_STAIRS", "Settings#copy", "SlabBlock#<init>"),
                blockFactoryEvents(registrations.get("SLITHERITE_SLAB").factoryMethod())
        );
        assertEquals(
                List.of(
                        "Blocks#STONE_BRICK_WALL",
                        "Settings#copy",
                        "WallBlock#<init>"
                ),
                blockFactoryEvents(registrations.get("SLITHERITE_WALL").factoryMethod())
        );
    }

    @Test
    void bindsExactlyFourLazyBlockItemsToCanonicalBlocks() throws IOException {
        Map<String, FieldDefinition> fields = fields(ITEM_CLASS);
        assertEquals(
                List.of(
                        "ITEMS",
                        "SLITHERITE_ITEM",
                        "SLITHERITE_STAIRS_ITEM",
                        "SLITHERITE_SLAB_ITEM",
                        "SLITHERITE_WALL_ITEM"
                ),
                new ArrayList<>(fields.keySet())
        );
        for (String field : ITEM_FIELDS) {
            assertPublicSupplier(fields.get(field), "net/minecraft/item/BlockItem");
        }

        ItemRegistrationTrace trace = itemRegistrations();
        assertEquals(IDS, trace.ids());
        assertEquals(BLOCK_FIELDS, trace.blockFields());
        assertEquals(0, invocationCount(ITEM_CLASS, "<clinit>", REGISTRY_SUPPLIER, "get"));
        assertEquals(Set.of(), directRegistryInvocations(ITEM_CLASS));
    }

    @Test
    void attachesBlocksBeforeItems() throws IOException {
        assertEquals(
                List.of(BLOCK_OWNER + "#register", ITEM_OWNER + "#register"),
                ownerInvocations(BOOTSTRAP_CLASS, "initialize", Set.of(BLOCK_OWNER, ITEM_OWNER))
        );
    }

    private static void assertPublicSupplier(FieldDefinition field, String suppliedType) {
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

    private static Map<String, FieldDefinition> fields(String resource) throws IOException {
        Map<String, FieldDefinition> fields = new LinkedHashMap<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
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

    private static Map<String, BlockRegistration> blockRegistrations() throws IOException {
        Map<String, BlockRegistration> registrations = new LinkedHashMap<>();
        reader(BLOCK_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) return null;

                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;
                    private String pendingFactory;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && IDS.contains(id)) pendingId = id;
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
                        if (opcode != Opcodes.PUTSTATIC
                                || !owner.equals(BLOCK_OWNER)
                                || !BLOCK_FIELDS.contains(name)) {
                            return;
                        }
                        assertNotNull(pendingId, name);
                        assertNotNull(pendingFactory, name);
                        registrations.put(
                                name,
                                new BlockRegistration(pendingId, pendingFactory)
                        );
                        pendingId = null;
                        pendingFactory = null;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return registrations;
    }

    private static ItemRegistrationTrace itemRegistrations() throws IOException {
        List<String> ids = new ArrayList<>();
        List<String> blockFields = new ArrayList<>();
        reader(ITEM_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("<clinit>")) return null;

                return new MethodVisitor(Opcodes.ASM9) {
                    private String pendingId;
                    private String pendingBlock;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && IDS.contains(id)) pendingId = id;
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
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (!owner.equals(ITEM_OWNER) || !name.equals("registerBlockItem")) {
                            return;
                        }
                        assertNotNull(pendingId);
                        assertNotNull(pendingBlock);
                        ids.add(pendingId);
                        blockFields.add(pendingBlock);
                        pendingId = null;
                        pendingBlock = null;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ItemRegistrationTrace(List.copyOf(ids), List.copyOf(blockFields));
    }

    private static List<String> blockFactoryEvents(String factoryMethod) throws IOException {
        List<String> events = new ArrayList<>();
        reader(BLOCK_CLASS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(factoryMethod)) return null;

                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode != Opcodes.GETSTATIC) return;
                        if (owner.equals("net/minecraft/block/Blocks")) {
                            events.add("Blocks#" + name);
                        } else if (owner.equals(BLOCK_OWNER)) {
                            events.add("SharedSlitheriteBlocks#" + name);
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
                        } else if (owner.equals("net/minecraft/block/Block")) {
                            events.add("Block#" + name);
                        } else if (owner.equals("net/minecraft/block/StairsBlock")) {
                            events.add("StairsBlock#" + name);
                        } else if (owner.equals("net/minecraft/block/SlabBlock")) {
                            events.add("SlabBlock#" + name);
                        } else if (owner.equals("net/minecraft/block/WallBlock")) {
                            events.add("WallBlock#" + name);
                        } else if (owner.equals(REGISTRY_SUPPLIER) && name.equals("get")) {
                            events.add("RegistrySupplier#get");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return List.copyOf(events);
    }

    private static int invocationCount(
            String resource,
            String method,
            String targetOwner,
            String targetMethod
    ) throws IOException {
        int[] count = {0};
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(method)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals(targetOwner) && name.equals(targetMethod)) count[0]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return count[0];
    }

    private static Set<String> directRegistryInvocations(String resource) throws IOException {
        java.util.LinkedHashSet<String> invocations = new java.util.LinkedHashSet<>();
        Set<String> owners = Set.of(
                "net/minecraft/registry/Registry",
                "dev/architectury/registry/registries/DeferredRegister"
        );
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        if (owners.contains(owner)) invocations.add(owner + "#" + name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return Set.copyOf(invocations);
    }

    private static List<String> ownerInvocations(
            String resource,
            String method,
            Set<String> owners
    ) throws IOException {
        List<String> invocations = new ArrayList<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(method)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (owners.contains(owner)) invocations.add(owner + "#" + name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return List.copyOf(invocations);
    }

    private static ClassReader reader(String resource) throws IOException {
        try (InputStream input = SharedSlitheriteRegistryBytecodeTest.class
                .getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new ClassReader(input);
        }
    }

    private record FieldDefinition(int access, String descriptor, String signature) {
    }

    private record BlockRegistration(String id, String factoryMethod) {
    }

    private record ItemRegistrationTrace(List<String> ids, List<String> blockFields) {
    }
}
