package ru.feytox.etherology.item;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensFoundationBytecodeTest {

    private static final String LENS_ITEM =
            "/ru/feytox/etherology/item/LensItem.class";
    private static final String LENS_RUNTIME =
            "/ru/feytox/etherology/item/LensRuntime.class";
    private static final String UNAVAILABLE_BACKEND =
            "/ru/feytox/etherology/item/LensRuntime$UnavailableBackend.class";
    private static final String LENS_COMPONENT =
            "/ru/feytox/etherology/magic/lens/LensComponent.class";
    private static final String STAFF_LENSES =
            "/ru/feytox/etherology/magic/staff/StaffLenses.class";
    private static final String RUNTIME_OWNER =
            "ru/feytox/etherology/item/LensRuntime";

    private static final List<String> SHARED_CLASS_RESOURCES = List.of(
            "/ru/feytox/etherology/magic/staff/StaffPattern.class",
            "/ru/feytox/etherology/magic/staff/StaffPattern$EmptyPattern.class",
            STAFF_LENSES,
            LENS_COMPONENT,
            "/ru/feytox/etherology/magic/lens/LensDataKeys.class",
            "/ru/feytox/etherology/item/LensRuntimeBackend.class",
            LENS_RUNTIME,
            UNAVAILABLE_BACKEND,
            LENS_ITEM,
            "/ru/feytox/etherology/item/UnadjustedLens.class"
    );

    @Test
    void sharedClassesHaveNoLoaderOrLegacyGameplayDependencies() throws IOException {
        List<String> forbiddenConstants = List.of(
                "net/fabricmc/",
                "net/minecraftforge/",
                "dev/onyxstudios/",
                "ru/feytox/etherology/magic/ether/EtherComponent",
                "ru/feytox/etherology/item/StaffItem",
                "ru/feytox/etherology/magic/staff/StaffComponent",
                "ru/feytox/etherology/magic/staff/StaffPartInfo",
                "ru/feytox/etherology/registry/misc/ComponentTypes",
                "ru/feytox/etherology/util/misc/ItemComponent"
        );

        for (String resource : SHARED_CLASS_RESOURCES) {
            String constants = new String(classBytes(resource), StandardCharsets.ISO_8859_1);
            for (String forbidden : forbiddenConstants) {
                assertFalse(
                        constants.contains(forbidden),
                        resource + " unexpectedly references " + forbidden
                );
            }
        }
    }

    @Test
    void lensItemRoutesOnlyTheFivePlatformOperationsThroughTheBridge()
            throws IOException {
        Map<String, List<String>> calls = new LinkedHashMap<>();
        reader(LENS_ITEM).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                List<String> methodCalls = new ArrayList<>();
                calls.put(name + descriptor, methodCalls);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String calledName,
                            String calledDescriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals(RUNTIME_OWNER)) {
                            methodCalls.add(calledName + calledDescriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(
                List.of(
                        "decrementEther(Lnet/minecraft/entity/LivingEntity;"
                                + "F)Z"
                ),
                calls.get(
                        "decrementEther(Lnet/minecraft/entity/LivingEntity;"
                                + "Lnet/minecraft/item/ItemStack;"
                                + "Lru/feytox/etherology/magic/lens/LensComponent;)Z"
                )
        );
        assertEquals(
                List.of(
                        "isStaff(Lnet/minecraft/item/ItemStack;)Z",
                        "placeStaffLens(Lnet/minecraft/item/ItemStack;"
                                + "Lnet/minecraft/item/ItemStack;"
                                + "Lru/feytox/etherology/magic/staff/StaffLenses;)V"
                ),
                calls.get(
                        "placeLensOnStaff(Lnet/minecraft/item/ItemStack;"
                                + "Lnet/minecraft/item/ItemStack;)V"
                )
        );
        assertEquals(
                List.of(
                        "isStaff(Lnet/minecraft/item/ItemStack;)Z",
                        "takeStaffLens(Lnet/minecraft/item/ItemStack;)"
                                + "Lnet/minecraft/item/ItemStack;"
                ),
                calls.get(
                        "takeLensFromStaff(Lnet/minecraft/item/ItemStack;)"
                                + "Lnet/minecraft/item/ItemStack;"
                )
        );
        assertEquals(
                List.of(
                        "getStaffLens(Lnet/minecraft/item/ItemStack;)"
                                + "Lnet/minecraft/item/ItemStack;"
                ),
                calls.get(
                        "getStaffLens(Lnet/minecraft/item/ItemStack;)"
                                + "Lnet/minecraft/item/ItemStack;"
                )
        );
    }

    @Test
    void canonicalTypeChecksAndSharedDataKeysRemainExplicit() throws IOException {
        assertMethodContainsTypeCheck(
                LENS_COMPONENT,
                "get",
                "ru/feytox/etherology/item/LensItem"
        );
        assertMethodContainsTypeCheck(
                STAFF_LENSES,
                "getLens",
                "ru/feytox/etherology/item/LensItem"
        );

        String lensItemConstants = new String(
                classBytes(LENS_ITEM),
                StandardCharsets.ISO_8859_1
        );
        String lensComponentConstants = new String(
                classBytes(LENS_COMPONENT),
                StandardCharsets.ISO_8859_1
        );
        assertTrue(lensItemConstants.contains("LensDataKeys"));
        assertTrue(lensItemConstants.contains("PSEUDO_DAMAGE"));
        assertTrue(lensComponentConstants.contains("LensDataKeys"));
        assertTrue(lensComponentConstants.contains("LENS"));
    }

    @Test
    void runtimeBindingAndUnavailableBackendAreFailClosed() throws IOException {
        AtomicInteger bindAccess = new AtomicInteger(-1);
        List<String> nullGuardCalls = new ArrayList<>();
        List<String> backendFields = new ArrayList<>();
        reader(LENS_RUNTIME).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (name.equals("UNAVAILABLE") || name.equals("backend")) {
                    backendFields.add(name + ":" + access + ":" + descriptor);
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
                if (!name.equals("bind")
                        || !descriptor.equals(
                                "(Lru/feytox/etherology/item/LensRuntimeBackend;)V"
                        )) {
                    return null;
                }
                bindAccess.set(access);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String calledName,
                            String calledDescriptor,
                            boolean isInterface
                    ) {
                        if (owner.equals("java/util/Objects")) {
                            nullGuardCalls.add(calledName + calledDescriptor);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        int requiredAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED;
        assertEquals(requiredAccess, bindAccess.get() & requiredAccess);
        assertEquals(
                List.of("requireNonNull(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"),
                nullGuardCalls
        );
        assertEquals(
                List.of(
                        "UNAVAILABLE:"
                                + (Opcodes.ACC_PRIVATE
                                | Opcodes.ACC_STATIC
                                | Opcodes.ACC_FINAL)
                                + ":Lru/feytox/etherology/item/LensRuntimeBackend;",
                        "backend:"
                                + (Opcodes.ACC_PRIVATE
                                | Opcodes.ACC_STATIC
                                | Opcodes.ACC_VOLATILE)
                                + ":Lru/feytox/etherology/item/LensRuntimeBackend;"
                ),
                backendFields
        );
        assertEquals(
                List.of(
                        "NEW:ru/feytox/etherology/item/LensRuntime$UnavailableBackend",
                        "CALL:ru/feytox/etherology/item/LensRuntime$UnavailableBackend#<init>()V",
                        "PUTSTATIC:ru/feytox/etherology/item/LensRuntime#UNAVAILABLE",
                        "GETSTATIC:ru/feytox/etherology/item/LensRuntime#UNAVAILABLE",
                        "PUTSTATIC:ru/feytox/etherology/item/LensRuntime#backend",
                        "RETURN"
                ),
                methodTrace(LENS_RUNTIME, "<clinit>", "()V")
        );

        Map<String, List<Integer>> opcodes = methodOpcodes(UNAVAILABLE_BACKEND);
        assertEquals(
                List.of(Opcodes.ICONST_0, Opcodes.IRETURN),
                opcodes.get("decrementEther(Lnet/minecraft/entity/LivingEntity;F)Z")
        );
        assertEquals(
                List.of(Opcodes.ICONST_0, Opcodes.IRETURN),
                opcodes.get("isStaff(Lnet/minecraft/item/ItemStack;)Z")
        );
        assertEquals(
                List.of(Opcodes.RETURN),
                opcodes.get(
                        "placeStaffLens(Lnet/minecraft/item/ItemStack;"
                                + "Lnet/minecraft/item/ItemStack;"
                                + "Lru/feytox/etherology/magic/staff/StaffLenses;)V"
                )
        );
        assertEquals(
                List.of(Opcodes.ACONST_NULL, Opcodes.ARETURN),
                opcodes.get(
                        "takeStaffLens(Lnet/minecraft/item/ItemStack;)"
                                + "Lnet/minecraft/item/ItemStack;"
                )
        );
        assertEquals(
                List.of(Opcodes.ACONST_NULL, Opcodes.ARETURN),
                opcodes.get(
                        "getStaffLens(Lnet/minecraft/item/ItemStack;)"
                                + "Lnet/minecraft/item/ItemStack;"
                )
        );
    }

    @Test
    void constantsAndPublicSurfaceNeededByCanonicalCallersRemainPresent()
            throws Exception {
        assertEquals(100, LensItem.CHARGE_LIMIT);
        assertEquals(100, LensItem.MAX_DAMAGE);
        assertTrue(java.lang.reflect.Modifier.isProtected(
                LensItem.class.getDeclaredConstructor(
                        ru.feytox.etherology.magic.staff.StaffLenses.class,
                        net.minecraft.util.Rarity.class,
                        float.class,
                        float.class
                ).getModifiers()
        ));
        assertEquals(
                ru.feytox.etherology.magic.staff.StaffLenses.class,
                LensItem.class.getMethod("getLensType").getReturnType()
        );
        assertTrue(java.lang.reflect.Modifier.isPublic(
                UnadjustedLens.class.getDeclaredConstructor().getModifiers()
        ));
        assertTrue(java.lang.reflect.Modifier.isAbstract(LensItem.class.getModifiers()));
    }

    @Test
    void itemSettingsModifierMathAndUnadjustedOverridesStayExact()
            throws IOException {
        List<String> constructor = methodTrace(
                LENS_ITEM,
                "<init>",
                "(Lru/feytox/etherology/magic/staff/StaffLenses;"
                        + "Lnet/minecraft/util/Rarity;FF)V"
        );
        assertInOrder(
                constructor,
                "NEW:net/minecraft/item/Item$Settings",
                "CALL:net/minecraft/item/Item$Settings#<init>()V",
                "CALL:net/minecraft/item/Item$Settings#rarity"
                        + "(Lnet/minecraft/util/Rarity;)Lnet/minecraft/item/Item$Settings;",
                "ICONST_1",
                "CALL:net/minecraft/item/Item$Settings#maxCount"
                        + "(I)Lnet/minecraft/item/Item$Settings;",
                "CALL:net/minecraft/item/Item#<init>"
                        + "(Lnet/minecraft/item/Item$Settings;)V",
                "PUTFIELD:ru/feytox/etherology/item/LensItem#lensType",
                "PUTFIELD:ru/feytox/etherology/item/LensItem#streamCost",
                "PUTFIELD:ru/feytox/etherology/item/LensItem#chargeCost"
        );

        assertInOrder(
                methodTrace(
                        LENS_ITEM,
                        "getEtherCost",
                        "(Lru/feytox/etherology/magic/lens/LensComponent;F)F"
                ),
                "GETSTATIC:ru/feytox/etherology/magic/lens/LensModifier#SAVING",
                "FCONST_1",
                "LDC:0.1",
                "LDC:0.75",
                "CALL:ru/feytox/etherology/magic/lens/LensComponent#calcValue"
                        + "(Lru/feytox/etherology/magic/lens/LensModifier;FFF)F",
                "FMUL",
                "FRETURN"
        );
        assertInOrder(
                methodTrace(
                        LENS_ITEM,
                        "getStreamCooldown",
                        "(Lru/feytox/etherology/magic/lens/LensComponent;)I"
                ),
                "GETSTATIC:ru/feytox/etherology/magic/lens/LensModifier#STREAM",
                "INT:16",
                "ICONST_1",
                "LDC:0.67",
                "CALL:ru/feytox/etherology/magic/lens/LensComponent#calcRoundValue"
                        + "(Lru/feytox/etherology/magic/lens/LensModifier;IIF)I",
                "IRETURN"
        );
        assertInOrder(
                methodTrace(
                        LENS_ITEM,
                        "getChargeTime",
                        "(Lru/feytox/etherology/magic/lens/LensComponent;I)I"
                ),
                "INT:100",
                "GETSTATIC:ru/feytox/etherology/magic/lens/LensModifier#CHARGE",
                "FCONST_1",
                "LDC:4.0",
                "LDC:0.8",
                "CALL:ru/feytox/etherology/magic/lens/LensComponent#calcValue"
                        + "(Lru/feytox/etherology/magic/lens/LensModifier;FFF)F",
                "FMUL",
                "CALL:java/lang/Math#round(F)I",
                "CALL:java/lang/Math#min(II)I",
                "IRETURN"
        );

        String unadjusted =
                "/ru/feytox/etherology/item/UnadjustedLens.class";
        assertInOrder(
                methodTrace(unadjusted, "<init>", "()V"),
                "ACONST_NULL",
                "GETSTATIC:net/minecraft/util/Rarity#COMMON",
                "FCONST_0",
                "FCONST_0",
                "CALL:ru/feytox/etherology/item/LensItem#<init>"
                        + "(Lru/feytox/etherology/magic/staff/StaffLenses;"
                        + "Lnet/minecraft/util/Rarity;FF)V",
                "RETURN"
        );
        Map<String, List<Integer>> unadjustedOpcodes = methodOpcodes(unadjusted);
        String useDescriptor = "(Lnet/minecraft/world/World;"
                + "Lnet/minecraft/entity/LivingEntity;"
                + "Lru/feytox/etherology/util/misc/ItemData;"
                + "Lnet/minecraft/item/ItemStack;ZLjava/util/function/Supplier;)Z";
        assertEquals(
                List.of(Opcodes.ICONST_0, Opcodes.IRETURN),
                unadjustedOpcodes.get("onStreamUse" + useDescriptor)
        );
        assertEquals(
                List.of(Opcodes.ICONST_0, Opcodes.IRETURN),
                unadjustedOpcodes.get("onChargeUse" + useDescriptor)
        );
        assertEquals(
                List.of(Opcodes.ICONST_1, Opcodes.IRETURN),
                unadjustedOpcodes.get("isUnadjusted()Z")
        );
    }

    @Test
    void damageVisualAndStaffFacadeOrderingStayCanonical() throws IOException {
        List<String> damage = methodTrace(
                LENS_ITEM,
                "damageLens",
                "(Lnet/minecraft/server/world/ServerWorld;"
                        + "Lnet/minecraft/item/ItemStack;I)Z"
        );
        assertInOrder(
                damage,
                "INSTANCEOF:ru/feytox/etherology/item/LensItem",
                "CALL:net/minecraft/server/world/ServerWorld#getRandom"
                        + "()Lnet/minecraft/util/math/random/Random;",
                "CALL:ru/feytox/etherology/item/LensItem#getDamageChance"
                        + "(Lnet/minecraft/item/ItemStack;)F",
                "CALL:net/minecraft/item/ItemStack#isEmpty()Z",
                "CALL:net/minecraft/util/math/random/Random#nextFloat()F",
                "CALL:ru/feytox/etherology/item/LensItem#getDamage"
                        + "(Lnet/minecraft/item/ItemStack;)I",
                "INT:100",
                "CALL:net/minecraft/item/ItemStack#decrement(I)V",
                "CALL:ru/feytox/etherology/item/LensItem#setDamage"
                        + "(Lnet/minecraft/item/ItemStack;I)V",
                "IINC:6:1"
        );

        List<String> placement = methodTrace(
                LENS_ITEM,
                "placeLensOnStaff",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V"
        );
        assertInOrder(
                placement,
                "CALL:ru/feytox/etherology/item/LensRuntime#isStaff"
                        + "(Lnet/minecraft/item/ItemStack;)Z",
                "INSTANCEOF:ru/feytox/etherology/item/LensItem",
                "CALL:ru/feytox/etherology/item/LensItem#isUnadjusted()Z",
                "CALL:ru/feytox/etherology/magic/staff/StaffLenses#getLens"
                        + "(Lnet/minecraft/item/ItemStack;)"
                        + "Lru/feytox/etherology/magic/staff/StaffLenses;",
                "CALL:net/minecraft/item/ItemStack#copy()Lnet/minecraft/item/ItemStack;",
                "CALL:ru/feytox/etherology/item/LensRuntime#placeStaffLens"
                        + "(Lnet/minecraft/item/ItemStack;"
                        + "Lnet/minecraft/item/ItemStack;"
                        + "Lru/feytox/etherology/magic/staff/StaffLenses;)V",
                "CALL:net/minecraft/item/ItemStack#decrement(I)V"
        );

        String constants = new String(classBytes(LENS_ITEM), StandardCharsets.ISO_8859_1);
        assertTrue(constants.contains("enchantment.level."));
        assertTrue(constants.contains("ENTITY_ITEM_BREAK"));
        assertTrue(constants.contains("SoundCategory"));
        assertTrue(constants.contains("ItemStackParticleEffect"));
        assertTrue(constants.contains("spawnParticles"));
        assertTrue(constants.contains("hsvToRgb"));
        assertTrue(constants.contains("PSEUDO_DAMAGE"));
    }

    private static void assertMethodContainsTypeCheck(
            String resource,
            String methodName,
            String expectedType
    ) throws IOException {
        List<String> checkedTypes = new ArrayList<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.INSTANCEOF) checkedTypes.add(type);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertEquals(List.of(expectedType), checkedTypes);
    }

    private static Map<String, List<Integer>> methodOpcodes(String resource)
            throws IOException {
        Map<String, List<Integer>> opcodes = new LinkedHashMap<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                List<Integer> instructions = new ArrayList<>();
                opcodes.put(name + descriptor, instructions);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInsn(int opcode) {
                        instructions.add(opcode);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return opcodes;
    }

    private static List<String> methodTrace(
            String resource,
            String methodName,
            String methodDescriptor
    ) throws IOException {
        List<String> trace = new ArrayList<>();
        reader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.equals(methodName) || !descriptor.equals(methodDescriptor)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInsn(int opcode) {
                        String name = simpleOpcodeName(opcode);
                        if (name != null) trace.add(name);
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        trace.add("INT:" + operand);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        trace.add("LDC:" + value);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW) trace.add("NEW:" + type);
                        if (opcode == Opcodes.INSTANCEOF) {
                            trace.add("INSTANCEOF:" + type);
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        String prefix = switch (opcode) {
                            case Opcodes.GETSTATIC -> "GETSTATIC:";
                            case Opcodes.PUTSTATIC -> "PUTSTATIC:";
                            case Opcodes.GETFIELD -> "GETFIELD:";
                            case Opcodes.PUTFIELD -> "PUTFIELD:";
                            default -> "FIELD:";
                        };
                        trace.add(prefix + owner + "#" + name);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        trace.add("CALL:" + owner + "#" + name + descriptor);
                    }

                    @Override
                    public void visitIincInsn(int varIndex, int increment) {
                        trace.add("IINC:" + varIndex + ":" + increment);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        assertFalse(trace.isEmpty(), methodName + methodDescriptor);
        return trace;
    }

    private static String simpleOpcodeName(int opcode) {
        return switch (opcode) {
            case Opcodes.ACONST_NULL -> "ACONST_NULL";
            case Opcodes.ICONST_0 -> "ICONST_0";
            case Opcodes.ICONST_1 -> "ICONST_1";
            case Opcodes.FCONST_0 -> "FCONST_0";
            case Opcodes.FCONST_1 -> "FCONST_1";
            case Opcodes.FMUL -> "FMUL";
            case Opcodes.IRETURN -> "IRETURN";
            case Opcodes.FRETURN -> "FRETURN";
            case Opcodes.ARETURN -> "ARETURN";
            case Opcodes.RETURN -> "RETURN";
            default -> null;
        };
    }

    private static void assertInOrder(List<String> trace, String... expectedEvents) {
        int previousIndex = -1;
        for (String event : expectedEvents) {
            int index = trace.subList(previousIndex + 1, trace.size()).indexOf(event);
            assertTrue(index >= 0, "Missing event " + event + " in " + trace);
            previousIndex += index + 1;
        }
    }

    private static ClassReader reader(String resource) throws IOException {
        return new ClassReader(classBytes(resource));
    }

    private static byte[] classBytes(String resource) throws IOException {
        InputStream stream = LensFoundationBytecodeTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "Missing class resource " + resource);
        try (stream) {
            return stream.readAllBytes();
        }
    }
}
