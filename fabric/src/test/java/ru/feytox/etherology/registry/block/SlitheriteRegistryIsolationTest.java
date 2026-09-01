package ru.feytox.etherology.registry.block;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
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

final class SlitheriteRegistryIsolationTest {

    private static final String DECO_BLOCKS =
            "/ru/feytox/etherology/registry/block/DecoBlocks.class";
    private static final String FABRIC_INITIALIZER =
            "/ru/feytox/etherology/Etherology.class";
    private static final String DECO_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/DecoBlocks";
    private static final String SHARED_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/SharedSlitheriteBlocks";
    private static final String SHARED_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedSlitheriteBlockItems";
    private static final String AUTO_BLOCK_LOOT_TABLE =
            "ru/feytox/etherology/registry/block/AutoBlockLootTable";
    private static final List<String> FIELDS = List.of(
            "SLITHERITE",
            "SLITHERITE_STAIRS",
            "SLITHERITE_SLAB",
            "SLITHERITE_WALL"
    );
    private static final List<String> IDS = List.of(
            "slitherite",
            "slitherite_stairs",
            "slitherite_slab",
            "slitherite_wall"
    );
    private static final List<String> DEFERRED_IDS = List.of(
            "polished_slitherite",
            "polished_slitherite_stairs",
            "polished_slitherite_slab",
            "polished_slitherite_wall",
            "polished_slitherite_button",
            "polished_slitherite_pressure_plate",
            "polished_slitherite_bricks",
            "polished_slitherite_brick_stairs",
            "polished_slitherite_brick_slab",
            "polished_slitherite_brick_wall",
            "chiseled_polished_slitherite",
            "chiseled_polished_slitherite_bricks",
            "cracked_polished_slitherite_bricks"
    );
    private static final List<String> DEFERRED_FIELDS = List.of(
            "POLISHED_SLITHERITE",
            "POLISHED_SLITHERITE_STAIRS",
            "POLISHED_SLITHERITE_SLAB",
            "POLISHED_SLITHERITE_WALL",
            "POLISHED_SLITHERITE_BUTTON",
            "POLISHED_SLITHERITE_PRESSURE_PLATE",
            "POLISHED_SLITHERITE_BRICKS",
            "POLISHED_SLITHERITE_BRICK_STAIRS",
            "POLISHED_SLITHERITE_BRICK_SLAB",
            "POLISHED_SLITHERITE_BRICK_WALL",
            "CHISELED_POLISHED_SLITHERITE",
            "CHISELED_POLISHED_SLITHERITE_BRICKS",
            "CRACKED_POLISHED_SLITHERITE_BRICKS"
    );

    @Test
    void aliasesExactlyFourSharedBlocksAndMarksTheirCanonicalDrops()
            throws IOException {
        AliasTrace trace = aliasTrace();

        assertEquals(
                Map.of(
                        "SLITHERITE", "SLITHERITE",
                        "SLITHERITE_STAIRS", "SLITHERITE_STAIRS",
                        "SLITHERITE_SLAB", "SLITHERITE_SLAB",
                        "SLITHERITE_WALL", "SLITHERITE_WALL"
                ),
                trace.aliases()
        );
        assertEquals(FIELDS, trace.autoLootFields());
        assertEquals(List.of(), trace.legacyIds());
    }

    @Test
    void preservesAllThirteenDeferredPolishedRegistrations() throws IOException {
        assertEquals(DEFERRED_IDS, deferredIds());
    }

    @Test
    void attachesSharedBlocksThenItemsBeforeLegacyBlockInitialization()
            throws IOException {
        assertEquals(
                List.of(
                        SHARED_BLOCKS_OWNER + "#register",
                        SHARED_ITEMS_OWNER + "#register",
                        "ru/feytox/etherology/registry/block/EBlocks#registerAll"
                ),
                initializerInvocations()
        );
    }

    private static AliasTrace aliasTrace() throws IOException {
        Map<String, String> aliases = new LinkedHashMap<>();
        List<String> autoLootFields = new ArrayList<>();
        List<String> legacyIds = new ArrayList<>();
        reader(DECO_BLOCKS).accept(new ClassVisitor(Opcodes.ASM9) {
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
                    private String pendingSharedField;
                    private String pendingDecoField;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && IDS.contains(id)) legacyIds.add(id);
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals(SHARED_BLOCKS_OWNER)) {
                            pendingSharedField = name;
                        }
                        if (opcode == Opcodes.GETSTATIC && owner.equals(DECO_BLOCKS_OWNER)) {
                            pendingDecoField = FIELDS.contains(name) ? name : null;
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(DECO_BLOCKS_OWNER)
                                && FIELDS.contains(name)) {
                            assertNotNull(pendingSharedField, name);
                            aliases.put(name, pendingSharedField);
                            pendingSharedField = null;
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
                        if (owner.equals(AUTO_BLOCK_LOOT_TABLE)
                                && name.equals("markAsAuto")
                                && pendingDecoField != null) {
                            autoLootFields.add(pendingDecoField);
                            pendingDecoField = null;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new AliasTrace(
                Map.copyOf(aliases),
                List.copyOf(autoLootFields),
                List.copyOf(legacyIds)
        );
    }

    private static List<String> deferredIds() throws IOException {
        List<String> ids = new ArrayList<>();
        reader(DECO_BLOCKS).accept(new ClassVisitor(Opcodes.ASM9) {
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

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String id && DEFERRED_IDS.contains(id)) {
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
                        if (opcode != Opcodes.PUTSTATIC
                                || !owner.equals(DECO_BLOCKS_OWNER)
                                || !DEFERRED_FIELDS.contains(name)) {
                            return;
                        }
                        assertNotNull(pendingId, name);
                        ids.add(pendingId);
                        pendingId = null;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return List.copyOf(ids);
    }

    private static List<String> initializerInvocations() throws IOException {
        List<String> invocations = new ArrayList<>();
        Set<String> owners = Set.of(
                SHARED_BLOCKS_OWNER,
                SHARED_ITEMS_OWNER,
                "ru/feytox/etherology/registry/block/EBlocks"
        );
        reader(FABRIC_INITIALIZER).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals("initialize")) return null;
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
        try (InputStream input = SlitheriteRegistryIsolationTest.class
                .getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new ClassReader(input);
        }
    }

    private record AliasTrace(
            Map<String, String> aliases,
            List<String> autoLootFields,
            List<String> legacyIds
    ) {
    }
}
