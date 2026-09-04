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
import java.util.Locale;
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
    private static final List<BlockExpectation> BLOCK_EXPECTATIONS = List.of(
            plainBlock("SLITHERITE", "STONE"),
            stairs("SLITHERITE_STAIRS", "SLITHERITE"),
            slab("SLITHERITE_SLAB", "STONE_STAIRS"),
            wall("SLITHERITE_WALL"),
            plainBlock("POLISHED_SLITHERITE", "SMOOTH_STONE"),
            stairs("POLISHED_SLITHERITE_STAIRS", "POLISHED_SLITHERITE"),
            slab("POLISHED_SLITHERITE_SLAB", "SMOOTH_STONE_SLAB"),
            wall("POLISHED_SLITHERITE_WALL"),
            button("POLISHED_SLITHERITE_BUTTON"),
            pressurePlate("POLISHED_SLITHERITE_PRESSURE_PLATE"),
            plainBlock("POLISHED_SLITHERITE_BRICKS", "STONE_BRICKS"),
            stairs(
                    "POLISHED_SLITHERITE_BRICK_STAIRS",
                    "POLISHED_SLITHERITE_BRICKS"
            ),
            slab("POLISHED_SLITHERITE_BRICK_SLAB", "STONE_BRICKS"),
            wall("POLISHED_SLITHERITE_BRICK_WALL"),
            plainBlock("CHISELED_POLISHED_SLITHERITE", "CHISELED_STONE_BRICKS"),
            plainBlock(
                    "CHISELED_POLISHED_SLITHERITE_BRICKS",
                    "CHISELED_STONE_BRICKS"
            ),
            plainBlock(
                    "CRACKED_POLISHED_SLITHERITE_BRICKS",
                    "CRACKED_STONE_BRICKS"
            )
    );
    private static final List<String> BLOCK_FIELDS = BLOCK_EXPECTATIONS.stream()
            .map(BlockExpectation::field)
            .toList();
    private static final List<String> ITEM_FIELDS = BLOCK_EXPECTATIONS.stream()
            .map(BlockExpectation::itemField)
            .toList();
    private static final List<String> IDS = BLOCK_EXPECTATIONS.stream()
            .map(BlockExpectation::id)
            .toList();

    @Test
    void declaresExactlySeventeenLazyCanonicalBlockFactories() throws IOException {
        Map<String, FieldDefinition> fields = fields(BLOCK_CLASS);
        List<String> expectedFields = new ArrayList<>();
        expectedFields.add("BLOCKS");
        expectedFields.add("POLISHED_SLITHERITE_TYPE");
        expectedFields.addAll(BLOCK_FIELDS);
        assertEquals(
                expectedFields,
                new ArrayList<>(fields.keySet())
        );
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fields.get("BLOCKS").access()
        );
        assertEquals(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fields.get("POLISHED_SLITHERITE_TYPE").access()
        );
        BLOCK_EXPECTATIONS.forEach(expectation -> assertPublicSupplier(
                fields.get(expectation.field()),
                expectation.suppliedType()
        ));

        Map<String, BlockRegistration> registrations = blockRegistrations();
        assertEquals(BLOCK_FIELDS, new ArrayList<>(registrations.keySet()));
        assertEquals(
                IDS,
                registrations.values().stream().map(BlockRegistration::id).toList()
        );
        assertEquals(
                BLOCK_EXPECTATIONS.size(),
                invocationCount(BLOCK_CLASS, SHARED_DEFERRED_REGISTER, "register")
        );
        assertEquals(0, invocationCount(BLOCK_CLASS, "<clinit>", REGISTRY_SUPPLIER, "get"));
        assertEquals(Set.of(), directRegistryInvocations(BLOCK_CLASS));
        for (BlockExpectation expectation : BLOCK_EXPECTATIONS) {
            assertEquals(
                    expectation.factoryEvents(),
                    blockFactoryEvents(
                            registrations.get(expectation.field()).factoryMethod()
                    ),
                    expectation.field()
            );
        }
        assertEquals(
                List.of(
                        "RegistrySupplier#get",
                        "Block#getDefaultState",
                        "Settings#copy",
                        "StairsBlock#<init>"
                ),
                blockFactoryEvents("createStairs")
        );
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach"),
                ownerInvocations(
                        BLOCK_CLASS,
                        "register",
                        Set.of(SHARED_DEFERRED_REGISTER)
                )
        );
    }

    @Test
    void clonesAndRegistersTheCanonicalPolishedSlitheriteBlockSetType()
            throws IOException {
        assertEquals(
                List.of("etherology", "polished_slitherite"),
                stringConstants(BLOCK_CLASS, "registerPolishedSlitheriteType")
        );
        assertEquals(
                List.of(
                        "BlockSetType#STONE",
                        "Identifier#of",
                        "Identifier#toString",
                        "BlockSetType#canOpenByHand",
                        "BlockSetType#soundType",
                        "BlockSetType#doorClose",
                        "BlockSetType#doorOpen",
                        "BlockSetType#trapdoorClose",
                        "BlockSetType#trapdoorOpen",
                        "BlockSetType#pressurePlateClickOff",
                        "BlockSetType#pressurePlateClickOn",
                        "BlockSetType#buttonClickOff",
                        "BlockSetType#buttonClickOn",
                        "BlockSetType#<init>",
                        "BlockSetType#register"
                ),
                blockFactoryEvents("registerPolishedSlitheriteType")
        );
    }

    @Test
    void preservesTheOriginalAggregateBlockStateCardinality() {
        assertEquals(
                List.of(
                        1, 80, 6, 324,
                        1, 80, 6, 324, 24, 2,
                        1, 80, 6, 324,
                        1, 1, 1
                ),
                BLOCK_EXPECTATIONS.stream().map(BlockExpectation::stateCount).toList()
        );
        assertEquals(
                1262,
                BLOCK_EXPECTATIONS.stream()
                        .mapToInt(BlockExpectation::stateCount)
                        .sum()
        );
    }

    @Test
    void bindsExactlySeventeenLazyBlockItemsToCanonicalBlocks() throws IOException {
        Map<String, FieldDefinition> fields = fields(ITEM_CLASS);
        List<String> expectedFields = new ArrayList<>();
        expectedFields.add("ITEMS");
        expectedFields.addAll(ITEM_FIELDS);
        assertEquals(
                expectedFields,
                new ArrayList<>(fields.keySet())
        );
        for (String field : ITEM_FIELDS) {
            assertPublicSupplier(fields.get(field), "net/minecraft/item/BlockItem");
        }

        ItemRegistrationTrace trace = itemRegistrations();
        assertEquals(IDS, trace.ids());
        assertEquals(BLOCK_FIELDS, trace.blockFields());
        assertEquals(
                1,
                invocationCount(ITEM_CLASS, SHARED_DEFERRED_REGISTER, "register")
        );
        assertEquals(0, invocationCount(ITEM_CLASS, "<clinit>", REGISTRY_SUPPLIER, "get"));
        assertEquals(1, invocationCount(ITEM_CLASS, REGISTRY_SUPPLIER, "get"));
        assertEquals(1, invocationCount(ITEM_CLASS, "net/minecraft/item/BlockItem", "<init>"));
        assertEquals(
                1,
                invocationCount(ITEM_CLASS, "net/minecraft/item/BlockItem", "appendBlocks")
        );
        assertEquals(Set.of(), directRegistryInvocations(ITEM_CLASS));
        assertEquals(
                List.of(SHARED_DEFERRED_REGISTER + "#attach"),
                ownerInvocations(
                        ITEM_CLASS,
                        "register",
                        Set.of(SHARED_DEFERRED_REGISTER)
                )
        );
    }

    @Test
    void attachesBlocksBeforeItems() throws IOException {
        assertEquals(
                List.of(BLOCK_OWNER + "#register", ITEM_OWNER + "#register"),
                ownerInvocations(BOOTSTRAP_CLASS, "initialize", Set.of(BLOCK_OWNER, ITEM_OWNER))
        );
    }

    private static BlockExpectation plainBlock(String field, String copiedBlock) {
        return new BlockExpectation(
                field,
                "net/minecraft/block/Block",
                1,
                List.of(
                        "Blocks#" + copiedBlock,
                        "Settings#copy",
                        "Block#<init>"
                )
        );
    }

    private static BlockExpectation stairs(String field, String baseBlockField) {
        return new BlockExpectation(
                field,
                "net/minecraft/block/StairsBlock",
                80,
                List.of(
                        "SharedSlitheriteBlocks#" + baseBlockField,
                        "SharedSlitheriteBlocks#createStairs"
                )
        );
    }

    private static BlockExpectation slab(String field, String copiedBlock) {
        return new BlockExpectation(
                field,
                "net/minecraft/block/SlabBlock",
                6,
                List.of(
                        "Blocks#" + copiedBlock,
                        "Settings#copy",
                        "SlabBlock#<init>"
                )
        );
    }

    private static BlockExpectation wall(String field) {
        return new BlockExpectation(
                field,
                "net/minecraft/block/WallBlock",
                324,
                List.of(
                        "Blocks#STONE_BRICK_WALL",
                        "Settings#copy",
                        "WallBlock#<init>"
                )
        );
    }

    private static BlockExpectation button(String field) {
        return new BlockExpectation(
                field,
                "net/minecraft/block/ButtonBlock",
                24,
                List.of(
                        "Settings#create",
                        "Settings#noCollision",
                        "Settings#strength",
                        "PistonBehavior#DESTROY",
                        "Settings#pistonBehavior",
                        "BlockSetType#STONE",
                        "ButtonBlock#<init>"
                )
        );
    }

    private static BlockExpectation pressurePlate(String field) {
        return new BlockExpectation(
                field,
                "net/minecraft/block/PressurePlateBlock",
                2,
                List.of(
                        "PressurePlateBlock$ActivationRule#MOBS",
                        "Blocks#STONE_PRESSURE_PLATE",
                        "Settings#copy",
                        "SharedSlitheriteBlocks#POLISHED_SLITHERITE_TYPE",
                        "PressurePlateBlock#<init>"
                )
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
                        } else if (owner.equals("net/minecraft/block/BlockSetType")) {
                            events.add("BlockSetType#" + name);
                        } else if (owner.equals("net/minecraft/block/piston/PistonBehavior")) {
                            events.add("PistonBehavior#" + name);
                        } else if (owner.equals(
                                "net/minecraft/block/PressurePlateBlock$ActivationRule"
                        )) {
                            events.add("PressurePlateBlock$ActivationRule#" + name);
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
                        } else if (owner.equals("net/minecraft/block/ButtonBlock")) {
                            events.add("ButtonBlock#" + name);
                        } else if (owner.equals("net/minecraft/block/PressurePlateBlock")) {
                            events.add("PressurePlateBlock#" + name);
                        } else if (owner.equals("net/minecraft/block/BlockSetType")) {
                            events.add("BlockSetType#" + name);
                        } else if (owner.equals("net/minecraft/util/Identifier")) {
                            events.add("Identifier#" + name);
                        } else if (owner.equals(BLOCK_OWNER)) {
                            events.add("SharedSlitheriteBlocks#" + name);
                        } else if (owner.equals(REGISTRY_SUPPLIER) && name.equals("get")) {
                            events.add("RegistrySupplier#get");
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return List.copyOf(events);
    }

    private static List<String> stringConstants(String resource, String method)
            throws IOException {
        List<String> constants = new ArrayList<>();
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
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String constant) constants.add(constant);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return List.copyOf(constants);
    }

    private static int invocationCount(
            String resource,
            String targetOwner,
            String targetMethod
    ) throws IOException {
        return invocationCount(resource, null, targetOwner, targetMethod);
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
                if (method != null && !name.equals(method)) return null;
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

    private record BlockExpectation(
            String field,
            String suppliedType,
            int stateCount,
            List<String> factoryEvents
    ) {

        private String itemField() {
            return field + "_ITEM";
        }

        private String id() {
            return field.toLowerCase(Locale.ROOT);
        }
    }

    private record BlockRegistration(String id, String factoryMethod) {
    }

    private record ItemRegistrationTrace(List<String> ids, List<String> blockFields) {
    }
}
