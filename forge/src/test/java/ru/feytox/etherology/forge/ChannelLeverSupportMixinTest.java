package ru.feytox.etherology.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChannelLeverSupportMixinTest {

    private static final String MIXIN_CLASS =
            "ru/feytox/etherology/forge/mixin/ChannelLeverSupportMixin";
    private static final String FOREST_LANTERN_SHEARS_MIXIN_CLASS =
            "ru/feytox/etherology/forge/mixin/ForestLanternShearsItemMixin";
    private static final String WALL_MOUNTED_BLOCK =
            "net/minecraft/block/WallMountedBlock";
    private static final String LEVER_BLOCK = "net/minecraft/block/LeverBlock";
    private static final String TARGET_METHOD =
            "canPlaceAt(Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/WorldView;"
                    + "Lnet/minecraft/util/math/BlockPos;)Z";
    private static final String INJECTION_DESCRIPTOR =
            "(Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/WorldView;"
                    + "Lnet/minecraft/util/math/BlockPos;"
                    + "Lorg/spongepowered/asm/mixin/injection/callback/"
                    + "CallbackInfoReturnable;)V";

    @Test
    void packagesTheExactMixinTargetShadowAndHeadInjection() throws IOException {
        RuntimeResourceAssertions.assertTextContains(
                "/etherology.forge.mixins.json",
                "ru.feytox.etherology.forge.mixin",
                "ChannelLeverSupportMixin"
        );

        ClassNode mixinClass = readMixinClass();
        AnnotationNode mixin = requireAnnotation(
                classAnnotations(mixinClass),
                "Lorg/spongepowered/asm/mixin/Mixin;"
        );
        assertEquals(
                List.of(Type.getObjectType(WALL_MOUNTED_BLOCK)),
                annotationValue(mixin, "value")
        );

        MethodNode shadow = requireMethod(
                mixinClass,
                "getDirection",
                "(Lnet/minecraft/block/BlockState;)Lnet/minecraft/util/math/Direction;"
        );
        assertEquals(
                Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC,
                shadow.access & (
                        Opcodes.ACC_PUBLIC
                                | Opcodes.ACC_PRIVATE
                                | Opcodes.ACC_PROTECTED
                                | Opcodes.ACC_STATIC
                )
        );
        requireAnnotation(
                methodAnnotations(shadow),
                "Lorg/spongepowered/asm/mixin/Shadow;"
        );
        assertEquals(
                1,
                countTypeInstructions(shadow, Opcodes.NEW, "java/lang/AssertionError")
        );
        assertEquals(1, countOpcodes(shadow, Opcodes.ATHROW));

        MethodNode injection = requireMethod(
                mixinClass,
                "etherology$allowLeverOnChannel",
                INJECTION_DESCRIPTOR
        );
        assertEquals(
                Opcodes.ACC_PRIVATE,
                injection.access & (
                        Opcodes.ACC_PUBLIC
                                | Opcodes.ACC_PRIVATE
                                | Opcodes.ACC_PROTECTED
                                | Opcodes.ACC_STATIC
                )
        );
        AnnotationNode inject = requireAnnotation(
                methodAnnotations(injection),
                "Lorg/spongepowered/asm/mixin/injection/Inject;"
        );
        assertEquals(List.of(TARGET_METHOD), annotationValue(inject, "method"));
        assertEquals(Boolean.TRUE, annotationValue(inject, "cancellable"));
        List<?> injectionPoints = (List<?>) annotationValue(inject, "at");
        assertEquals(1, injectionPoints.size());
        AnnotationNode injectionPoint = (AnnotationNode) injectionPoints.get(0);
        assertEquals(
                "Lorg/spongepowered/asm/mixin/injection/At;",
                injectionPoint.desc
        );
        assertEquals("HEAD", annotationValue(injectionPoint, "value"));
    }

    @Test
    void allowsOnlyVanillaLeversBackedByTheSharedChannel() throws IOException {
        MethodNode injection = requireMethod(
                readMixinClass(),
                "etherology$allowLeverOnChannel",
                INJECTION_DESCRIPTOR
        );

        assertEquals(1, countTypeInstructions(injection, Opcodes.INSTANCEOF, LEVER_BLOCK));
        assertEquals(
                1,
                countInvocations(
                        injection,
                        MIXIN_CLASS,
                        "getDirection",
                        "(Lnet/minecraft/block/BlockState;)"
                                + "Lnet/minecraft/util/math/Direction;"
                )
        );
        assertEquals(
                1,
                countInvocations(
                        injection,
                        "net/minecraft/util/math/Direction",
                        "getOpposite",
                        "()Lnet/minecraft/util/math/Direction;"
                )
        );
        assertEquals(
                1,
                countInvocations(
                        injection,
                        "net/minecraft/util/math/BlockPos",
                        "offset",
                        "(Lnet/minecraft/util/math/Direction;)"
                                + "Lnet/minecraft/util/math/BlockPos;"
                )
        );
        assertEquals(
                1,
                countInvocations(
                        injection,
                        "net/minecraft/world/WorldView",
                        "getBlockState",
                        "(Lnet/minecraft/util/math/BlockPos;)"
                                + "Lnet/minecraft/block/BlockState;"
                )
        );
        assertEquals(
                1,
                countFieldAccesses(
                        injection,
                        "ru/feytox/etherology/registry/block/SharedBlocks",
                        "ETHEREAL_CHANNEL",
                        Opcodes.GETSTATIC
                )
        );
        assertEquals(
                1,
                countInvocations(
                        injection,
                        "net/minecraft/block/BlockState",
                        "isOf",
                        "(Lnet/minecraft/block/Block;)Z"
                )
        );
        assertEquals(
                1,
                countInvocations(
                        injection,
                        "org/spongepowered/asm/mixin/injection/callback/"
                                + "CallbackInfoReturnable",
                        "setReturnValue",
                        "(Ljava/lang/Object;)V"
                )
        );
        assertEquals(
                0,
                countTypeInstructions(injection, Opcodes.NEW, "java/lang/AssertionError")
        );
        assertTrue(countOpcodes(injection, Opcodes.RETURN) >= 2);

        int leverGuard = typeInstructionIndex(injection, Opcodes.INSTANCEOF, LEVER_BLOCK);
        int directionLookup = invocationIndex(
                injection,
                MIXIN_CLASS,
                "getDirection"
        );
        int oppositeLookup = invocationIndex(
                injection,
                "net/minecraft/util/math/Direction",
                "getOpposite"
        );
        int supportOffset = invocationIndex(
                injection,
                "net/minecraft/util/math/BlockPos",
                "offset"
        );
        int supportState = invocationIndex(
                injection,
                "net/minecraft/world/WorldView",
                "getBlockState"
        );
        int channelIdentity = fieldAccessIndex(
                injection,
                "ru/feytox/etherology/registry/block/SharedBlocks",
                "ETHEREAL_CHANNEL",
                Opcodes.GETSTATIC
        );
        int exactBlockCheck = invocationIndex(
                injection,
                "net/minecraft/block/BlockState",
                "isOf"
        );
        int successfulCancellation = invocationIndex(
                injection,
                "org/spongepowered/asm/mixin/injection/callback/"
                        + "CallbackInfoReturnable",
                "setReturnValue"
        );
        assertTrue(
                leverGuard < directionLookup
                        && directionLookup < oppositeLookup
                        && oppositeLookup < supportOffset
                        && supportOffset < supportState
                        && supportState < channelIdentity
                        && channelIdentity < exactBlockCheck
                        && exactBlockCheck < successfulCancellation
        );
    }

    @Test
    void appliesTheCanonicalShearsSpeedToTheSharedForestLantern()
            throws IOException {
        RuntimeResourceAssertions.assertTextContains(
                "/etherology.forge.mixins.json",
                "ForestLanternShearsItemMixin"
        );

        ClassNode mixinClass = readMixinClass(FOREST_LANTERN_SHEARS_MIXIN_CLASS);
        AnnotationNode mixin = requireAnnotation(
                classAnnotations(mixinClass),
                "Lorg/spongepowered/asm/mixin/Mixin;"
        );
        assertEquals(
                List.of(Type.getObjectType("net/minecraft/item/ShearsItem")),
                annotationValue(mixin, "value")
        );

        MethodNode injection = requireMethod(
                mixinClass,
                "etherology$useForestLanternSpeed",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                        + "Lorg/spongepowered/asm/mixin/injection/callback/"
                        + "CallbackInfoReturnable;)V"
        );
        AnnotationNode inject = requireAnnotation(
                methodAnnotations(injection),
                "Lorg/spongepowered/asm/mixin/injection/Inject;"
        );
        assertEquals(
                List.of("getMiningSpeedMultiplier"),
                annotationValue(inject, "method")
        );
        assertEquals(Boolean.TRUE, annotationValue(inject, "cancellable"));
        List<?> injectionPoints = (List<?>) annotationValue(inject, "at");
        assertEquals(1, injectionPoints.size());
        assertEquals(
                "HEAD",
                annotationValue((AnnotationNode) injectionPoints.get(0), "value")
        );
        assertEquals(
                1,
                countFieldAccesses(
                        injection,
                        "ru/feytox/etherology/registry/block/SharedForestLanternBlocks",
                        "FOREST_LANTERN",
                        Opcodes.GETSTATIC
                )
        );
        assertEquals(
                1,
                countInvocations(
                        injection,
                        "net/minecraft/block/BlockState",
                        "isOf",
                        "(Lnet/minecraft/block/Block;)Z"
                )
        );
        assertEquals(1, countLoadedConstants(injection, 15.0F));
        assertEquals(
                1,
                countInvocations(
                        injection,
                        "org/spongepowered/asm/mixin/injection/callback/"
                                + "CallbackInfoReturnable",
                        "setReturnValue",
                        "(Ljava/lang/Object;)V"
                )
        );
    }

    private static ClassNode readMixinClass() throws IOException {
        return readMixinClass(MIXIN_CLASS);
    }

    private static ClassNode readMixinClass(String className) throws IOException {
        InputStream classStream = ChannelLeverSupportMixinTest.class.getResourceAsStream(
                "/" + className + ".class"
        );
        assertNotNull(classStream);
        try (classStream) {
            ClassNode classNode = new ClassNode();
            new ClassReader(classStream).accept(
                    classNode,
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
            );
            return classNode;
        }
    }

    private static MethodNode requireMethod(
            ClassNode owner,
            String methodName,
            String descriptor
    ) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(methodName))
                .filter(method -> method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    private static List<AnnotationNode> classAnnotations(ClassNode owner) {
        return combineAnnotations(owner.visibleAnnotations, owner.invisibleAnnotations);
    }

    private static List<AnnotationNode> methodAnnotations(MethodNode owner) {
        return combineAnnotations(owner.visibleAnnotations, owner.invisibleAnnotations);
    }

    private static List<AnnotationNode> combineAnnotations(
            List<AnnotationNode> visible,
            List<AnnotationNode> invisible
    ) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (visible != null) {
            annotations.addAll(visible);
        }
        if (invisible != null) {
            annotations.addAll(invisible);
        }
        return annotations;
    }

    private static AnnotationNode requireAnnotation(
            List<AnnotationNode> annotations,
            String descriptor
    ) {
        return annotations.stream()
                .filter(annotation -> annotation.desc.equals(descriptor))
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        assertNotNull(annotation.values);
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (annotation.values.get(index).equals(name)) {
                return annotation.values.get(index + 1);
            }
        }
        throw new AssertionError("Missing annotation value " + name);
    }

    private static int countTypeInstructions(
            MethodNode method,
            int opcode,
            String type
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode typeInstruction
                    && typeInstruction.getOpcode() == opcode
                    && typeInstruction.desc.equals(type)) {
                count++;
            }
        }
        return count;
    }

    private static int countLoadedConstants(MethodNode method, Object value) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant
                    && constant.cst.equals(value)) {
                count++;
            }
        }
        return count;
    }

    private static int countInvocations(
            MethodNode method,
            String owner,
            String methodName,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.owner.equals(owner)
                    && invocation.name.equals(methodName)
                    && invocation.desc.equals(descriptor)) {
                count++;
            }
        }
        return count;
    }

    private static int countFieldAccesses(
            MethodNode method,
            String owner,
            String fieldName,
            int opcode
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode fieldAccess
                    && fieldAccess.owner.equals(owner)
                    && fieldAccess.name.equals(fieldName)
                    && fieldAccess.getOpcode() == opcode) {
                count++;
            }
        }
        return count;
    }

    private static int countOpcodes(MethodNode method, int opcode) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == opcode) {
                count++;
            }
        }
        return count;
    }

    private static int typeInstructionIndex(
            MethodNode method,
            int opcode,
            String type
    ) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode typeInstruction
                    && typeInstruction.getOpcode() == opcode
                    && typeInstruction.desc.equals(type)) {
                return index;
            }
            index++;
        }
        throw new AssertionError("Missing type instruction " + type);
    }

    private static int invocationIndex(
            MethodNode method,
            String owner,
            String methodName
    ) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.owner.equals(owner)
                    && invocation.name.equals(methodName)) {
                return index;
            }
            index++;
        }
        throw new AssertionError("Missing invocation " + owner + '#' + methodName);
    }

    private static int fieldAccessIndex(
            MethodNode method,
            String owner,
            String fieldName,
            int opcode
    ) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode fieldAccess
                    && fieldAccess.owner.equals(owner)
                    && fieldAccess.name.equals(fieldName)
                    && fieldAccess.getOpcode() == opcode) {
                return index;
            }
            index++;
        }
        throw new AssertionError("Missing field access " + owner + '#' + fieldName);
    }
}
