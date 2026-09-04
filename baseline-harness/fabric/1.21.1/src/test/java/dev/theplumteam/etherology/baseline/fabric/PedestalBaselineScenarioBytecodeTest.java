package dev.theplumteam.etherology.baseline.fabric;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalBaselineScenarioBytecodeTest {

    private static final String SCENARIO =
            "dev/theplumteam/etherology/baseline/fabric/PedestalBaselineScenario";

    @Test
    void scenarioUsesOnlyGenericMinecraftContractsForOriginalBehavior()
            throws IOException {
        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("ru/feytox/etherology/"));
        assertTrue(constants.contains(
                "ru.feytox.etherology.block.pedestal.PedestalBlock"
        ));
        assertTrue(constants.contains(
                "ru.feytox.etherology.block.pedestal.PedestalBlockEntity"
        ));
        assertTrue(constants.contains(
                "occupied-carpet-display:up=minecraft:purple_carpetx1->"
        ));
        assertTrue(invocations("nativePlacePedestal").contains("useOnBlock"));
        assertTrue(invocations("interact").contains("interactBlock"));
    }

    @Test
    void dispenserTransitionPersistenceAndCaptureVerticalsAreNative()
            throws IOException {
        assertTrue(invocations("setupDispensers").containsAll(Set.of(
                "setBlockState",
                "setStack"
        )));
        assertTrue(invocations("runTransitionProbe").containsAll(Set.of(
                "nativePlacePedestal",
                "isRemoved"
        )));
        assertTrue(allInvocations().contains("arrangeScene"));
        assertTrue(invocations("tickWaitingForTransitionPrecondition").containsAll(
                Set.of("getBlockEntity", "isRemoved", "submitTransitionMutation")
        ));
        assertTrue(invocations("tickWaitingForClientMirror").contains(
                "refreshTransitionClientEvidence"
        ));
        assertTrue(invocations("isCaptureStateExact").contains(
                "refreshTransitionClientEvidence"
        ));
        assertTrue(invocations("hasExpectedCameraPose").contains(
                "isExactCameraPose"
        ));
        assertTrue(invocations("isExactCameraPose").containsAll(Set.of(
                "compare",
                "wrapDegrees"
        )));
        assertTrue(invocations("refreshTransitionClientEvidence").containsAll(Set.of(
                "getBlockEntity",
                "isRemoved",
                "getBlockState",
                "isAir",
                "capture"
        )));
        assertTrue(allInvocations().containsAll(Set.of(
                "saveAll",
                "start",
                "saveScreenshot",
                "createLink"
        )));
    }

    @Test
    void captureCameraIsStabilizedAndRenderWaitingCannotResetItsWatchdog()
            throws IOException {
        assertTrue(invocations("onEndClientTick").containsAll(Set.of(
                "stabilizeCaptureCamera",
                "cameraPoseDescription"
        )));
        assertTrue(invocations("onGameRenderStarting").contains(
                "stabilizeCaptureCamera"
        ));
        assertTrue(invocations("stabilizeCaptureCamera").containsAll(Set.of(
                "unpressAll",
                "setPerspective",
                "setCameraEntity",
                "updatePositionAndAngles",
                "setVelocity",
                "setOnGround"
        )));

        Set<String> renderWaitCalls = invocations("tickWaitingForRenders");
        assertTrue(renderWaitCalls.containsAll(Set.of(
                "isCaptureStateExact",
                "observe"
        )));
        assertFalse(renderWaitCalls.contains("transition"));

        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertTrue(constants.contains("client ticks; capture_phase="));
    }

    private static Set<String> invocations(String methodName) throws IOException {
        Set<String> names = new HashSet<>();
        new ClassReader(classBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!methodName.equals(name)) return null;
                return invocationCollector(names);
            }
        }, 0);
        return names;
    }

    private static Set<String> allInvocations() throws IOException {
        Set<String> names = new HashSet<>();
        for (String className : Set.of(
                SCENARIO,
                "dev/theplumteam/etherology/baseline/fabric/PedestalEvidenceWriter"
        )) {
            new ClassReader(classBytes(className)).accept(
                    new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(
                                int access,
                                String name,
                                String descriptor,
                                String signature,
                                String[] exceptions
                        ) {
                            return invocationCollector(names);
                        }
                    },
                    0
            );
        }
        return names;
    }

    private static MethodVisitor invocationCollector(Set<String> names) {
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                names.add(name);
            }
        };
    }

    private static byte[] classBytes() throws IOException {
        return classBytes(SCENARIO);
    }

    private static byte[] classBytes(String className) throws IOException {
        try (InputStream input = PedestalBaselineScenarioBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(className + ".class")) {
            if (input == null) throw new IOException("Scenario class is missing");
            return input.readAllBytes();
        }
    }
}
