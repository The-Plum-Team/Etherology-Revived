package ru.feytox.etherology.block.forestLantern;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ForestLanternBlockMechanicsTest {

    private static final String BLOCK_CLASS =
            "ru/feytox/etherology/block/forestLantern/ForestLanternBlock.class";
    private static final String BLOCK_OWNER =
            "ru/feytox/etherology/block/forestLantern/ForestLanternBlock";
    private static final String CALLBACK_CLASS =
            "ru/feytox/etherology/util/event/PlayerJumpCallback.class";
    private static final String CALLBACK_OWNER =
            "ru/feytox/etherology/util/event/PlayerJumpCallback";

    @Test
    void compiledClassOwnsAgeAndFacingWithTheMatureNorthDefault()
            throws IOException {
        ClassShape shape = classShape(BLOCK_CLASS);
        assertEquals("net/minecraft/block/HorizontalFacingBlock", shape.superName());
        assertEquals(
                List.of("net/minecraft/block/Fertilizable"),
                shape.interfaces()
        );
        assertTrue((shape.access() & Opcodes.ACC_PUBLIC) != 0);
        assertFalse((shape.access() & Opcodes.ACC_ABSTRACT) != 0);
        assertEquals(0.4F, shape.fieldConstants().get("BREAK_CHANCE"));
        assertEquals(30, shape.fieldConstants().get("GROW_FREQUENCY"));
        assertEquals(15.0F, shape.fieldConstants().get("SHEARS_MINING_SPEED"));
        assertEquals(4, shape.fieldConstants().get("MAX_AGE"));
        assertEquals(
                "Lnet/minecraft/state/property/IntProperty;",
                shape.fieldDescriptors().get("AGE")
        );

        assertTrue(readsField(
                BLOCK_CLASS,
                "<clinit>",
                "net/minecraft/state/property/Properties",
                "AGE_4"
        ));
        List<String> constructor = invocations(BLOCK_CLASS, "<init>");
        assertEquals(
                List.of(
                        "copy",
                        "notSolid",
                        "sounds",
                        "pistonBehavior",
                        "luminance",
                        "postProcess",
                        "emissiveLighting"
                ),
                namesOwnedBy(constructor, "net/minecraft/block/AbstractBlock$Settings")
        );
        assertEquals(2, countNamed(constructor, "with"));
        assertEquals(1, countNamed(constructor, "setDefaultState"));
        assertTrue(readsField(
                BLOCK_CLASS,
                "<init>",
                "net/minecraft/block/Blocks",
                "BROWN_MUSHROOM_BLOCK"
        ));
        assertTrue(readsField(
                BLOCK_CLASS,
                "<init>",
                "net/minecraft/sound/BlockSoundGroup",
                "GRASS"
        ));
        assertTrue(readsField(
                BLOCK_CLASS,
                "<init>",
                "net/minecraft/block/piston/PistonBehavior",
                "DESTROY"
        ));
        assertTrue(readsField(
                BLOCK_CLASS,
                "<init>",
                "net/minecraft/util/math/Direction",
                "NORTH"
        ));
        assertTrue(anyMethodLoadsInteger(BLOCK_CLASS, "lambda$new$", 8));
    }

    @Test
    void compiledShapeTableRetainsAllTwentyCanonicalBounds() throws IOException {
        assertEquals(expectedCuboids(), cuboidsCreatedInClassInitializer());
        List<String> outline = invocations(BLOCK_CLASS, "getOutlineShape");
        assertTrue(containsInvocationNamed(outline, "get"));
        assertEquals(3, countNamed(outline, "get"));
    }

    @Test
    void compiledTickAndBoneMealPredicatesUseTheCanonicalAgeRules()
            throws IOException {
        assertTrue(readsField(BLOCK_CLASS, "hasRandomTicks", BLOCK_OWNER, "AGE"));
        assertTrue(loadsInteger(BLOCK_CLASS, "hasRandomTicks", 4));
        assertTrue(containsJumpOpcode(BLOCK_CLASS, "hasRandomTicks", Opcodes.IF_ICMPGE));

        assertTrue(readsField(BLOCK_CLASS, "isFertilizable", BLOCK_OWNER, "AGE"));
        assertTrue(loadsInteger(BLOCK_CLASS, "isFertilizable", 4));
        assertTrue(containsJumpOpcode(BLOCK_CLASS, "isFertilizable", Opcodes.IF_ICMPGT));
        assertTrue(returnsConstantTrue(BLOCK_CLASS, "canGrow"));
    }

    @Test
    void compiledGrowthRetainsTheThirtyTickAndTwentySevenPositionRules()
            throws IOException {
        List<String> randomTick = invocations(BLOCK_CLASS, "randomTick");
        assertTrue(randomTick.contains("net/minecraft/util/math/random/Random#nextInt(I)I"));
        assertTrue(randomTick.contains(
                "net/minecraft/server/world/ServerWorld#setBlockState"
                        + "(Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/block/BlockState;)Z"
        ));
        assertTrue(loadsInteger(BLOCK_CLASS, "randomTick", 30));
        assertTrue(readsField(BLOCK_CLASS, "randomTick", BLOCK_OWNER, "AGE"));

        List<String> grow = invocations(BLOCK_CLASS, "grow");
        assertTrue(containsInvocationNamed(grow, "tryPlaceNewLanterns"));
        assertTrue(grow.contains("net/minecraft/util/math/random/Random#nextBoolean()Z"));
        assertTrue(containsInvocationNamed(grow, "setBlockState"));

        List<String> spread = invocations(BLOCK_CLASS, "tryPlaceNewLanterns");
        assertTrue(spread.contains(
                "net/minecraft/util/math/BlockPos#iterateRandomly"
                        + "(Lnet/minecraft/util/math/random/Random;I"
                        + "Lnet/minecraft/util/math/BlockPos;I)Ljava/lang/Iterable;"
        ));
        assertTrue(containsInvocationNamed(spread, "isAir"));
        assertTrue(containsInvocationNamed(spread, "tryPlaceLantern"));
        assertTrue(loadsInteger(BLOCK_CLASS, "tryPlaceNewLanterns", 27));
        assertTrue(loadsInteger(BLOCK_CLASS, "tryPlaceNewLanterns", 1));

        List<String> place = invocations(BLOCK_CLASS, "tryPlaceLantern");
        assertTrue(place.contains("net/minecraft/util/math/Direction#fromHorizontal(I)"
                + "Lnet/minecraft/util/math/Direction;"));
        assertTrue(containsInvocationNamed(place, "canPlaceAt"));
        assertTrue(containsInvocationNamed(place, "getDefaultState"));
        assertTrue(containsInvocationNamed(place, "setBlockState"));
        assertTrue(loadsInteger(BLOCK_CLASS, "tryPlaceLantern", 4));
        assertTrue(loadsInteger(BLOCK_CLASS, "tryPlaceLantern", 0));
    }

    @Test
    void compiledSupportRuleUsesOptionalPeachLogsUntilMaturityThenAnySolidFace()
            throws IOException {
        String descriptor =
                "(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/util/math/Direction;I)Z";
        List<String> support = invocations(BLOCK_CLASS, "canPlaceAt", descriptor);

        assertTrue(containsInvocationNamed(support, "offset"));
        assertTrue(containsInvocationNamed(support, "getBlockState"));
        assertTrue(containsInvocationNamed(support, "isSideSolidFullSquare"));
        assertTrue(containsInvocationNamed(support, "isIn"));
        assertTrue(readsAnyProjectFieldNamed(
                BLOCK_CLASS,
                "canPlaceAt",
                descriptor,
                "PEACH_LOGS"
        ));
        assertFalse(classConstants(BLOCK_CLASS).contains(
                "ru/feytox/etherology/data/EBlockTags"
        ));
    }

    @Test
    void compiledBreakingPathKeepsInstantBudsAndDelegatesMatureStates()
            throws IOException {
        assertTrue(loadsFloat(BLOCK_CLASS, "calcBlockBreakingDelta", 1.0f));
        List<String> breaking = invocations(BLOCK_CLASS, "calcBlockBreakingDelta");
        assertEquals(1, countNamed(breaking, "calcBlockBreakingDelta"));
        assertTrue(containsSuperInvocation(
                BLOCK_CLASS,
                "calcBlockBreakingDelta",
                "calcBlockBreakingDelta"
        ));
    }

    @Test
    void commonJumpCallbackRetainsTheExactBreakChanceSoundAndMatureDrop()
            throws IOException {
        int registerAccess = methodAccess(BLOCK_CLASS, "registerJumpEvent");
        assertTrue((registerAccess & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((registerAccess & Opcodes.ACC_STATIC) != 0);
        assertTrue((registerAccess & Opcodes.ACC_SYNCHRONIZED) != 0);
        assertTrue(readsField(
                BLOCK_CLASS,
                "registerJumpEvent",
                BLOCK_OWNER,
                "jumpEventRegistered"
        ));
        assertTrue(writesField(
                BLOCK_CLASS,
                "registerJumpEvent",
                BLOCK_OWNER,
                "jumpEventRegistered"
        ));
        assertTrue(readsField(
                BLOCK_CLASS,
                "registerJumpEvent",
                CALLBACK_OWNER,
                "BEFORE_JUMP"
        ));
        assertEquals(
                1,
                countNamed(invocations(BLOCK_CLASS, "registerJumpEvent"), "register")
        );

        String jumpHandler = methodInvoking(BLOCK_CLASS, "breakBlock");
        List<String> jump = invocations(BLOCK_CLASS, jumpHandler);
        assertTrue(containsInvocationNamed(jump, "getSteppingPos"));
        assertTrue(containsInvocationNamed(jump, "getBlockState"));
        assertTrue(containsInvocationNamed(jump, "isOf"));
        assertTrue(containsInvocationNamed(jump, "nextFloat"));
        assertTrue(containsInvocationNamed(jump, "playSound"));
        assertTrue(containsInvocationNamed(jump, "breakBlock"));
        assertTrue(loadsFloat(BLOCK_CLASS, jumpHandler, 0.4f));
        assertTrue(loadsFloat(BLOCK_CLASS, jumpHandler, 0.7f));
        assertTrue(loadsFloat(BLOCK_CLASS, jumpHandler, 0.9f));
        assertTrue(readsField(BLOCK_CLASS, jumpHandler, BLOCK_OWNER, "AGE"));
        assertTrue(loadsInteger(BLOCK_CLASS, jumpHandler, 4));
        assertTrue(readsField(
                BLOCK_CLASS,
                jumpHandler,
                "net/minecraft/world/World",
                "isClient"
        ));
        assertTrue(readsField(
                BLOCK_CLASS,
                jumpHandler,
                "ru/feytox/etherology/registry/block/SharedForestLanternBlocks",
                "FOREST_LANTERN"
        ));

        assertTrue((classAccess(CALLBACK_CLASS) & Opcodes.ACC_INTERFACE) != 0);
        assertTrue(readsFieldInAnyMethod(
                CALLBACK_CLASS,
                "net/minecraft/util/ActionResult",
                "PASS"
        ));
        assertTrue(anyMethodInvokes(CALLBACK_CLASS, CALLBACK_OWNER, "beforeJump"));
        assertTrue(anyMethodInvokes(
                CALLBACK_CLASS,
                "dev/architectury/event/EventFactory",
                "of"
        ));
    }

    @Test
    void commonMechanicsContainNoLoaderOrLegacyRegistrationReferences()
            throws IOException {
        String constants = classConstants(BLOCK_CLASS);
        assertFalse(constants.contains("net/fabricmc/"));
        assertFalse(constants.contains("net/minecraftforge/"));
        assertFalse(constants.contains("ru/feytox/etherology/registry/block/DecoBlocks"));
        assertFalse(constants.contains("ru/feytox/etherology/util/misc/RegistrableBlock"));
        assertFalse(constants.contains("net/minecraft/registry/Registry"));
    }

    private static List<List<Double>> expectedCuboids() {
        return List.of(
                cuboid(4, 4, 13, 12, 12, 16),
                cuboid(5.5, 5, 11, 10.5, 11, 16),
                cuboid(5, 5, 9, 11, 12, 16),
                cuboid(4, 5, 6, 12, 14, 16),
                cuboid(2, 4, 4, 14, 16, 16),
                cuboid(4, 4, 0, 12, 12, 3),
                cuboid(5.5, 5, 0, 10.5, 11, 5),
                cuboid(5, 5, 0, 11, 12, 7),
                cuboid(4, 5, 0, 12, 14, 10),
                cuboid(2, 4, 0, 14, 16, 12),
                cuboid(13, 4, 4, 16, 12, 12),
                cuboid(11, 5, 5.5, 16, 11, 10.5),
                cuboid(9, 5, 5, 16, 12, 11),
                cuboid(6, 5, 4, 16, 14, 12),
                cuboid(4, 4, 2, 16, 16, 14),
                cuboid(0, 4, 4, 3, 12, 12),
                cuboid(0, 5, 5.5, 5, 11, 10.5),
                cuboid(0, 5, 5, 7, 12, 11),
                cuboid(0, 5, 4, 10, 14, 12),
                cuboid(0, 4, 2, 12, 16, 14)
        );
    }

    private static List<Double> cuboid(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        return List.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static ClassShape classShape(String resource) throws IOException {
        AtomicInteger access = new AtomicInteger();
        List<String> interfaces = new ArrayList<>();
        Map<String, String> fieldDescriptors = new LinkedHashMap<>();
        Map<String, Object> fieldConstants = new LinkedHashMap<>();
        List<String> superNames = new ArrayList<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int classAccess,
                    String name,
                    String signature,
                    String superName,
                    String[] implementedInterfaces
            ) {
                access.set(classAccess);
                superNames.add(superName);
                interfaces.addAll(Arrays.asList(implementedInterfaces));
            }

            @Override
            public org.objectweb.asm.FieldVisitor visitField(
                    int fieldAccess,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                fieldDescriptors.put(name, descriptor);
                if (value != null) {
                    fieldConstants.put(name, value);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, superNames.size());
        return new ClassShape(
                access.get(),
                superNames.get(0),
                interfaces,
                fieldDescriptors,
                fieldConstants
        );
    }

    private static List<List<Double>> cuboidsCreatedInClassInitializer()
            throws IOException {
        List<List<Double>> cuboids = new ArrayList<>();
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
                    private final List<Double> coordinates = new ArrayList<>();

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.DCONST_0) {
                            coordinates.add(0.0);
                        } else if (opcode == Opcodes.DCONST_1) {
                            coordinates.add(1.0);
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Double coordinate) {
                            coordinates.add(coordinate);
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
                        if (owner.equals("net/minecraft/block/Block")
                                && name.equals("createCuboidShape")) {
                            assertEquals(6, coordinates.size());
                            cuboids.add(List.copyOf(coordinates));
                            coordinates.clear();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return cuboids;
    }

    private static List<String> namesOwnedBy(
            List<String> invocations,
            String expectedOwner
    ) {
        List<String> names = new ArrayList<>();
        String prefix = expectedOwner + "#";
        for (String invocation : invocations) {
            if (!invocation.startsWith(prefix)) {
                continue;
            }
            int descriptorIndex = invocation.indexOf('(');
            names.add(invocation.substring(prefix.length(), descriptorIndex));
        }
        return names;
    }

    private static List<String> invocations(String resource, String methodName)
            throws IOException {
        return invocations(resource, methodName, null);
    }

    private static List<String> invocations(
            String resource,
            String methodName,
            String methodDescriptor
    ) throws IOException {
        List<String> invocations = new ArrayList<>();
        visitMethods(resource, methodName, methodDescriptor, new MethodVisitor(Opcodes.ASM9) {
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

    private static boolean containsInvocationNamed(
            List<String> invocations,
            String methodName
    ) {
        return invocations.stream().anyMatch(
                invocation -> invocation.contains("#" + methodName + "(")
        );
    }

    private static long countNamed(List<String> invocations, String methodName) {
        return invocations.stream()
                .filter(invocation -> invocation.contains("#" + methodName + "("))
                .count();
    }

    private static boolean loadsInteger(String resource, String methodName, int expected)
            throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resource, methodName, null, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (expected >= -1
                        && expected <= 5
                        && opcode == Opcodes.ICONST_0 + expected) {
                    found.set(true);
                }
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                if ((opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)
                        && operand == expected) {
                    found.set(true);
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Integer integer && integer == expected) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean anyMethodLoadsInteger(
            String resource,
            String methodPrefix,
            int expected
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.startsWith(methodPrefix)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (expected >= -1
                                && expected <= 5
                                && opcode == Opcodes.ICONST_0 + expected) {
                            found.set(true);
                        }
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if ((opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)
                                && operand == expected) {
                            found.set(true);
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Integer integer && integer == expected) {
                            found.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found.get();
    }

    private static boolean loadsFloat(String resource, String methodName, float expected)
            throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resource, methodName, null, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (Float.compare(expected, 0.0f) == 0 && opcode == Opcodes.FCONST_0) {
                    found.set(true);
                }
                if (Float.compare(expected, 1.0f) == 0 && opcode == Opcodes.FCONST_1) {
                    found.set(true);
                }
                if (Float.compare(expected, 2.0f) == 0 && opcode == Opcodes.FCONST_2) {
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

    private static boolean readsField(
            String resource,
            String methodName,
            String expectedOwner,
            String expectedName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resource, methodName, null, new MethodVisitor(Opcodes.ASM9) {
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

    private static boolean readsAnyProjectFieldNamed(
            String resource,
            String methodName,
            String methodDescriptor,
            String expectedName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(
                resource,
                methodName,
                methodDescriptor,
                new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETSTATIC
                                && owner.startsWith("ru/feytox/etherology/")
                                && name.equals(expectedName)) {
                            found.set(true);
                        }
                    }
                }
        );
        return found.get();
    }

    private static boolean writesField(
            String resource,
            String methodName,
            String expectedOwner,
            String expectedName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resource, methodName, null, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitFieldInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor
            ) {
                if (opcode == Opcodes.PUTSTATIC
                        && owner.equals(expectedOwner)
                        && name.equals(expectedName)) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean containsJumpOpcode(
            String resource,
            String methodName,
            int expectedOpcode
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resource, methodName, null, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                if (opcode == expectedOpcode) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static boolean returnsConstantTrue(String resource, String methodName)
            throws IOException {
        AtomicBoolean loadedTrue = new AtomicBoolean();
        AtomicBoolean returnedInteger = new AtomicBoolean();
        visitMethods(resource, methodName, null, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.ICONST_1) {
                    loadedTrue.set(true);
                }
                if (loadedTrue.get() && opcode == Opcodes.IRETURN) {
                    returnedInteger.set(true);
                }
            }
        });
        return returnedInteger.get();
    }

    private static boolean containsSuperInvocation(
            String resource,
            String methodName,
            String expectedInvocation
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
        visitMethods(resource, methodName, null, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                if (opcode == Opcodes.INVOKESPECIAL
                        && name.equals(expectedInvocation)
                        && owner.startsWith("net/minecraft/block/")) {
                    found.set(true);
                }
            }
        });
        return found.get();
    }

    private static String methodInvoking(String resource, String expectedInvocation)
            throws IOException {
        List<String> methods = new ArrayList<>();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                String visitedMethodName = name;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                        boolean isInterface
                    ) {
                        if (name.equals(expectedInvocation)) {
                            methods.add(visitedMethodName);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(1, methods.size());
        return methods.get(0);
    }

    private static int classAccess(String resource) throws IOException {
        AtomicInteger access = new AtomicInteger();
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(
                    int version,
                    int classAccess,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces
            ) {
                access.set(classAccess);
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return access.get();
    }

    private static int methodAccess(String resource, String methodName)
            throws IOException {
        AtomicInteger access = new AtomicInteger(-1);
        classReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int methodAccess,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (name.equals(methodName)) {
                    assertEquals(-1, access.get(), resource + "#" + methodName);
                    access.set(methodAccess);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertTrue(access.get() >= 0, resource + "#" + methodName);
        return access.get();
    }

    private static boolean readsFieldInAnyMethod(
            String resource,
            String expectedOwner,
            String expectedName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
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
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found.get();
    }

    private static boolean anyMethodInvokes(
            String resource,
            String expectedOwner,
            String expectedName
    ) throws IOException {
        AtomicBoolean found = new AtomicBoolean();
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
                        if (owner.equals(expectedOwner) && name.equals(expectedName)) {
                            found.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found.get();
    }

    private static String classConstants(String resource) throws IOException {
        InputStream stream = ForestLanternBlockMechanicsTest.class
                .getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static void visitMethods(
            String resource,
            String methodName,
            String methodDescriptor,
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
                if (!name.equals(methodName)
                        || (methodDescriptor != null
                        && !descriptor.equals(methodDescriptor))) {
                    return null;
                }
                matches.incrementAndGet();
                return visitor;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertTrue(matches.get() > 0, resource + "#" + methodName);
    }

    private static ClassReader classReader(String resource) throws IOException {
        InputStream stream = ForestLanternBlockMechanicsTest.class
                .getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return new ClassReader(stream);
        }
    }

    private record ClassShape(
            int access,
            String superName,
            List<String> interfaces,
            Map<String, String> fieldDescriptors,
            Map<String, Object> fieldConstants
    ) {
    }
}
