package ru.feytox.etherology.registry.block;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ForestLanternRegistryIsolationTest {

    private static final String DECO_BLOCKS =
            "ru/feytox/etherology/registry/block/DecoBlocks.class";
    private static final String DECO_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/DecoBlocks";
    private static final String DECO_BLOCK_ITEMS =
            "ru/feytox/etherology/registry/item/DecoBlockItems.class";
    private static final String E_BLOCK_TAGS =
            "ru/feytox/etherology/data/EBlockTags.class";
    private static final String E_BLOCK_TAGS_OWNER =
            "ru/feytox/etherology/data/EBlockTags";
    private static final String SHARED_BLOCKS_OWNER =
            "ru/feytox/etherology/registry/block/SharedForestLanternBlocks";
    private static final String SHARED_BLOCK_ITEMS_OWNER =
            "ru/feytox/etherology/registry/item/SharedForestLanternBlockItems";
    private static final String SHARED_TAGS_OWNER =
            "ru/feytox/etherology/data/SharedForestLanternBlockTags";
    private static final String REGISTRY_SUPPLIER_OWNER =
            "dev/architectury/registry/registries/RegistrySupplier";
    private static final String FOREST_LANTERN_BLOCK_OWNER =
            "ru/feytox/etherology/block/forestLantern/ForestLanternBlock";
    private static final String FABRIC_INITIALIZER =
            "ru/feytox/etherology/Etherology.class";
    private static final String PLAYER_MIXIN =
            "ru/feytox/etherology/mixin/PlayerEntityMixin.class";
    private static final String SHEARS_MIXIN =
            "ru/feytox/etherology/mixin/ShearsItemMixin.class";
    private static final String PLAYER_JUMP_CALLBACK_OWNER =
            "ru/feytox/etherology/util/event/PlayerJumpCallback";

    @Test
    void legacyDecorativeFieldIsOnlyAnAliasOfTheSharedBlockSupplier()
            throws IOException {
        AtomicInteger fieldAccess = new AtomicInteger(-1);
        List<String> aliasEvents = new ArrayList<>();
        AtomicInteger idLoads = new AtomicInteger();
        classReader(DECO_BLOCKS).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals("FOREST_LANTERN")) {
                    fieldAccess.set(access);
                    assertEquals(
                            "Lru/feytox/etherology/block/forestLantern/"
                                    + "ForestLanternBlock;",
                            descriptor
                    );
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String methodName,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean recordingAlias;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value.equals("forest_lantern")) {
                            idLoads.incrementAndGet();
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (methodName.equals("<clinit>")
                                && opcode == Opcodes.GETSTATIC
                                && owner.equals(SHARED_BLOCKS_OWNER)
                                && name.equals("FOREST_LANTERN")) {
                            recordingAlias = true;
                            aliasEvents.add("SharedForestLanternBlocks#FOREST_LANTERN");
                        }
                        if (recordingAlias
                                && opcode == Opcodes.PUTSTATIC
                                && owner.equals(DECO_BLOCKS_OWNER)
                                && name.equals("FOREST_LANTERN")) {
                            aliasEvents.add("DecoBlocks#FOREST_LANTERN");
                            recordingAlias = false;
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
                        if (recordingAlias
                                && owner.equals(REGISTRY_SUPPLIER_OWNER)
                                && name.equals("get")) {
                            aliasEvents.add("RegistrySupplier#get");
                        }
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (recordingAlias
                                && opcode == Opcodes.CHECKCAST
                                && type.equals(FOREST_LANTERN_BLOCK_OWNER)) {
                            aliasEvents.add("CHECKCAST ForestLanternBlock");
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
                        "SharedForestLanternBlocks#FOREST_LANTERN",
                        "RegistrySupplier#get",
                        "CHECKCAST ForestLanternBlock",
                        "DecoBlocks#FOREST_LANTERN"
                ),
                aliasEvents
        );
        assertEquals(0, idLoads.get());
        assertFalse(declaresField(DECO_BLOCK_ITEMS, "FOREST_LANTERN"));
        assertFalse(declaresField(DECO_BLOCK_ITEMS, "FOREST_LANTERN_ITEM"));
    }

    @Test
    void fabricAttachesTheSharedBlockAndItemBeforeLegacyContent()
            throws IOException {
        Set<String> owners = Set.of(
                SHARED_BLOCKS_OWNER,
                SHARED_BLOCK_ITEMS_OWNER,
                "ru/feytox/etherology/registry/item/EItems",
                FOREST_LANTERN_BLOCK_OWNER
        );

        assertEquals(
                List.of(
                        SHARED_BLOCKS_OWNER + "#register",
                        SHARED_BLOCK_ITEMS_OWNER + "#register",
                        "ru/feytox/etherology/registry/item/EItems#registerItems",
                        FOREST_LANTERN_BLOCK_OWNER + "#registerJumpEvent"
                ),
                referencedMethods(FABRIC_INITIALIZER, "initialize", owners)
        );
    }

    @Test
    void legacyPeachLogTagAliasesTheSharedTagWithoutOwningItsId()
            throws IOException {
        assertEquals(
                List.of(
                        "SharedForestLanternBlockTags#PEACH_LOGS",
                        "EBlockTags#PEACH_LOGS"
                ),
                fieldAliasEvents(
                        E_BLOCK_TAGS,
                        E_BLOCK_TAGS_OWNER,
                        SHARED_TAGS_OWNER,
                        "PEACH_LOGS"
                )
        );
        assertFalse(classConstants(E_BLOCK_TAGS).contains("peach_logs"));
    }

    @Test
    void fabricJumpMixinOnlyPublishesTheSharedCallbackAtJumpHead()
            throws IOException {
        List<String> invocations = methodInvocations(
                PLAYER_MIXIN,
                "injectBeforeJumpEvent"
        );

        assertTrue(readsField(
                PLAYER_MIXIN,
                "injectBeforeJumpEvent",
                PLAYER_JUMP_CALLBACK_OWNER,
                "BEFORE_JUMP"
        ));
        assertEquals(1, countNamed(invocations, "invoker"));
        assertEquals(1, countNamed(invocations, "beforeJump"));
        String constants = classConstants(PLAYER_MIXIN);
        assertFalse(constants.contains(SHARED_BLOCKS_OWNER));
        assertFalse(constants.contains("forest_lantern"));
    }

    @Test
    void fabricShearsMixinUsesTheSharedForestLanternAndCanonicalSpeed()
            throws IOException {
        assertFalse(declaresMethod(SHEARS_MIXIN, "injectForestLanternSpeed"));
        assertFalse(readsField(
                SHEARS_MIXIN,
                null,
                DECO_BLOCKS_OWNER,
                "FOREST_LANTERN"
        ));
        assertTrue(declaresMethod(
                SHEARS_MIXIN,
                "etherology$useForestLanternSpeed"
        ));
        assertTrue(readsField(
                SHEARS_MIXIN,
                "etherology$useForestLanternSpeed",
                SHARED_BLOCKS_OWNER,
                "FOREST_LANTERN"
        ));
        assertTrue(loadsFloat(SHEARS_MIXIN, 15.0F));
        assertTrue(readsField(
                SHEARS_MIXIN,
                "injectLightelet",
                DECO_BLOCKS_OWNER,
                "LIGHTELET"
        ));
    }

    private static List<String> fieldAliasEvents(
            String resource,
            String aliasOwner,
            String sharedOwner,
            String fieldName
    ) throws IOException {
        List<String> events = new ArrayList<>();
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
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.equals(sharedOwner)
                                && name.equals(fieldName)) {
                            events.add("SharedForestLanternBlockTags#" + fieldName);
                        }
                        if (opcode == Opcodes.PUTSTATIC
                                && owner.equals(aliasOwner)
                                && name.equals(fieldName)) {
                            events.add("EBlockTags#" + fieldName);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return events;
    }

    private static boolean declaresField(String resource, String expectedField)
            throws IOException {
        AtomicBoolean declared = new AtomicBoolean();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals(expectedField)) {
                    declared.set(true);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return declared.get();
    }

    private static boolean declaresMethod(String resource, String expectedMethod)
            throws IOException {
        AtomicBoolean declared = new AtomicBoolean();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (name.equals(expectedMethod)) {
                    declared.set(true);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return declared.get();
    }

    private static List<String> referencedMethods(
            String resource,
            String methodName,
            Set<String> owners
    ) throws IOException {
        List<String> invocations = new ArrayList<>();
        visitMethods(resource, methodName, new MethodVisitor(Opcodes.ASM9) {
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
        });
        return invocations;
    }

    private static List<String> methodInvocations(String resource, String methodName)
            throws IOException {
        List<String> invocations = new ArrayList<>();
        visitMethods(resource, methodName, new MethodVisitor(Opcodes.ASM9) {
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
        });
        return invocations;
    }

    private static long countNamed(List<String> invocations, String methodName) {
        return invocations.stream()
                .filter(invocation -> invocation.contains("#" + methodName + "("))
                .count();
    }

    private static boolean readsField(
            String resource,
            String methodName,
            String expectedOwner,
            String expectedName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resource, methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitFieldInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor
            ) {
                if (owner.equals(expectedOwner) && name.equals(expectedName)) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean loadsFloat(String resource, float expected)
            throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resource, null, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (Float.compare(expected, 0.0F) == 0 && opcode == Opcodes.FCONST_0) {
                    found.set(true);
                }
                if (Float.compare(expected, 1.0F) == 0 && opcode == Opcodes.FCONST_1) {
                    found.set(true);
                }
                if (Float.compare(expected, 2.0F) == 0 && opcode == Opcodes.FCONST_2) {
                    found.set(true);
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Float floating
                        && Float.compare(floating, expected) == 0) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static void visitMethods(
            String resource,
            String methodName,
            MethodVisitor visitor
    ) throws IOException {
        AtomicInteger matches = new AtomicInteger();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (methodName != null && !name.equals(methodName)) {
                    return null;
                }
                matches.incrementAndGet();
                return visitor;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertTrue(matches.get() > 0, resource + "#" + methodName);
    }

    private static String classConstants(String resource) throws IOException {
        InputStream stream = ForestLanternRegistryIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static ClassReader classReader(String resource) throws IOException {
        InputStream stream = ForestLanternRegistryIsolationTest.class
                .getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }
}
