package ru.feytox.etherology.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalDispenserMixinTest {

    private static final String MIXIN_CLASS =
            "ru/feytox/etherology/forge/mixin/PedestalDispenserBlockMixin";
    private static final String PEDESTAL_BEHAVIOR =
            "ru/feytox/etherology/block/pedestal/PedestalDispenserBehavior";
    private static final String DISPENSER_BEHAVIOR_DESCRIPTOR =
            "Lnet/minecraft/block/dispenser/DispenserBehavior;";
    private static final String SHADOW_DESCRIPTOR =
            "(Lnet/minecraft/item/ItemStack;)" + DISPENSER_BEHAVIOR_DESCRIPTOR;
    private static final String REDIRECT_DESCRIPTOR =
            "(Lnet/minecraft/block/DispenserBlock;"
                    + "Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/util/math/BlockPos;)"
                    + DISPENSER_BEHAVIOR_DESCRIPTOR;
    private static final String TARGET_METHOD =
            "dispense(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/util/math/BlockPos;)V";
    private static final String REDIRECT_TARGET =
            "Lnet/minecraft/block/DispenserBlock;"
                    + "getBehaviorForItem"
                    + "(Lnet/minecraft/item/ItemStack;)"
                    + DISPENSER_BEHAVIOR_DESCRIPTOR;

    @Test
    void mixinConfigSelectsTheExactForgeDispenserMixinOnce() throws IOException {
        JsonObject config = JsonParser.parseString(
                PedestalBytecodeAssertions.readTextResource(
                        "etherology.forge.mixins.json"
                )
        ).getAsJsonObject();
        assertEquals("ru.feytox.etherology.forge.mixin", config.get("package").getAsString());
        assertTrue(config.get("required").getAsBoolean());
        assertEquals("JAVA_17", config.get("compatibilityLevel").getAsString());

        JsonArray mixins = config.getAsJsonArray("mixins");
        long pedestalMixinCount = 0;
        for (var mixin : mixins) {
            if (mixin.getAsString().equals("PedestalDispenserBlockMixin")) {
                pedestalMixinCount++;
            }
        }
        assertEquals(1, pedestalMixinCount);
    }

    @Test
    void redirectTargetsTheExactForgeMappedDispenserLookup() throws IOException {
        ClassNode mixinClass = readMixinClass();
        AnnotationNode mixin = PedestalBytecodeAssertions.requireClassAnnotation(
                mixinClass,
                "Lorg/spongepowered/asm/mixin/Mixin;"
        );
        assertEquals(
                List.of(Type.getObjectType("net/minecraft/block/DispenserBlock")),
                PedestalBytecodeAssertions.annotationValue(mixin, "value")
        );

        MethodNode shadow = PedestalBytecodeAssertions.requireMethod(
                mixinClass,
                "getBehaviorForItem",
                SHADOW_DESCRIPTOR
        );
        assertEquals(
                Opcodes.ACC_PROTECTED | Opcodes.ACC_ABSTRACT,
                shadow.access & (
                        Opcodes.ACC_PUBLIC
                                | Opcodes.ACC_PRIVATE
                                | Opcodes.ACC_PROTECTED
                                | Opcodes.ACC_STATIC
                                | Opcodes.ACC_ABSTRACT
                )
        );
        PedestalBytecodeAssertions.requireMethodAnnotation(
                shadow,
                "Lorg/spongepowered/asm/mixin/Shadow;"
        );

        MethodNode redirect = PedestalBytecodeAssertions.requireMethod(
                mixinClass,
                "etherology$selectPedestalBehavior",
                REDIRECT_DESCRIPTOR
        );
        assertEquals(
                Opcodes.ACC_PRIVATE,
                redirect.access & (
                        Opcodes.ACC_PUBLIC
                                | Opcodes.ACC_PRIVATE
                                | Opcodes.ACC_PROTECTED
                                | Opcodes.ACC_STATIC
                )
        );
        AnnotationNode redirectAnnotation =
                PedestalBytecodeAssertions.requireMethodAnnotation(
                        redirect,
                        "Lorg/spongepowered/asm/mixin/injection/Redirect;"
                );
        assertEquals(
                List.of(TARGET_METHOD),
                PedestalBytecodeAssertions.annotationValue(
                        redirectAnnotation,
                        "method"
                )
        );
        AnnotationNode at = (AnnotationNode) PedestalBytecodeAssertions
                .annotationValue(redirectAnnotation, "at");
        assertEquals(
                "Lorg/spongepowered/asm/mixin/injection/At;",
                at.desc
        );
        assertEquals(
                "INVOKE",
                PedestalBytecodeAssertions.annotationValue(at, "value")
        );
        assertEquals(
                REDIRECT_TARGET,
                PedestalBytecodeAssertions.annotationValue(at, "target")
        );
    }

    @Test
    void redirectUsesThePedestalBehaviorAndFallsBackToTheShadowLookup()
            throws IOException {
        MethodNode redirect = PedestalBytecodeAssertions.requireMethod(
                readMixinClass(),
                "etherology$selectPedestalBehavior",
                REDIRECT_DESCRIPTOR
        );
        int pointerConstruction = PedestalBytecodeAssertions.callIndex(
                redirect,
                "net/minecraft/util/math/BlockPointerImpl",
                "<init>",
                "(Lnet/minecraft/server/world/ServerWorld;"
                        + "Lnet/minecraft/util/math/BlockPos;)V"
        );
        int pedestalTest = PedestalBytecodeAssertions.callIndex(
                redirect,
                PEDESTAL_BEHAVIOR,
                "testDispenser",
                "(Lnet/minecraft/util/math/BlockPointer;"
                        + "Lnet/minecraft/item/ItemStack;)Z"
        );
        int pedestalSelection = PedestalBytecodeAssertions.callIndex(
                redirect,
                PEDESTAL_BEHAVIOR,
                "getInstance",
                "()Lru/feytox/etherology/block/pedestal/"
                        + "PedestalDispenserBehavior;"
        );
        int vanillaFallback = PedestalBytecodeAssertions.callIndex(
                redirect,
                MIXIN_CLASS,
                "getBehaviorForItem",
                SHADOW_DESCRIPTOR
        );

        assertTrue(pointerConstruction >= 0);
        assertTrue(pointerConstruction < pedestalTest);
        assertTrue(pedestalTest < pedestalSelection);
        assertTrue(pedestalTest < vanillaFallback);
        assertEquals(
                1,
                PedestalBytecodeAssertions.countCalls(
                        redirect,
                        PEDESTAL_BEHAVIOR,
                        "testDispenser",
                        "(Lnet/minecraft/util/math/BlockPointer;"
                                + "Lnet/minecraft/item/ItemStack;)Z"
                )
        );
        assertEquals(1, PedestalBytecodeAssertions.countOpcodes(
                redirect,
                Opcodes.ARETURN
        ));
    }

    private static ClassNode readMixinClass() throws IOException {
        return PedestalBytecodeAssertions.readClass(MIXIN_CLASS + ".class");
    }
}
