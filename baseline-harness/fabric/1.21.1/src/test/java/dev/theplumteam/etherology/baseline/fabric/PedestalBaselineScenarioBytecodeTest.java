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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PedestalBaselineScenarioBytecodeTest {

    private static final String SCENARIO =
            "dev/theplumteam/etherology/baseline/fabric/PedestalBaselineScenario";
    private static final String DROP_SNAPSHOT = SCENARIO + "$DropSnapshot";

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
                "setStack",
                "scheduledTick"
        )));
        assertEquals(1, invocationCount("setupDispensers", "scheduledTick"));
        assertFalse(new String(classBytes(), StandardCharsets.ISO_8859_1)
                .contains("REDSTONE_BLOCK"));
        assertFalse(allInvocations().contains("powerPosition"));
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
                "isAir"
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
        assertTrue(invocations("transition").contains("info"));

        Set<String> transitionMirrorCalls = invocations(
                "refreshTransitionClientEvidence"
        );
        assertTrue(transitionMirrorCalls.containsAll(Set.of(
                "getBlockEntity",
                "isRemoved",
                "getBlockState",
                "isAir"
        )));
        assertFalse(transitionMirrorCalls.contains("capture"));
        assertFalse(transitionMirrorCalls.contains("equals"));
        assertTrue(invocations("recordTransitionClientDrops").containsAll(Set.of(
                "capture",
                "description",
                "equals",
                "info"
        )));
        assertEquals(
                Set.of("tickWaitingForClientMirror", "captureCurrentPhase"),
                invokingMethods(SCENARIO, "recordTransitionClientDrops")
        );
        assertTrue(singleFieldWriteFollowsSingleInvocation(
                "tickWaitingForClientMirror",
                SCENARIO,
                "recordTransitionClientDrops",
                "transitionClientDropsAtMirrorRecorded"
        ));
        assertTrue(singleFieldWriteFollowsSingleInvocation(
                "captureCurrentPhase",
                SCENARIO,
                "recordTransitionClientDrops",
                "transitionClientDropsAtCaptureRecorded"
        ));
        assertEquals(
                Set.of("recordTransitionClientDrops", "createTransitionsReport"),
                invokingMethods(DROP_SNAPSHOT, "equals")
        );
        assertTrue(invocations("createTransitionsReport").containsAll(Set.of(
                "toJson",
                "equals",
                "add"
        )));
        assertEquals(2, fieldReadCount(
                "createTransitionsReport",
                "transitionClientDropsAtMirrorRecorded"
        ));
        assertEquals(2, fieldReadCount(
                "createTransitionsReport",
                "transitionClientDropsAtCaptureRecorded"
        ));

        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertTrue(constants.contains("client ticks; capture_phase="));
        assertTrue(constants.contains("Pedestal stage transition: from="));
        assertTrue(constants.contains("Transition client drop diagnostic: checkpoint="));
        assertTrue(constants.contains("client_drop_diagnostics"));
        assertTrue(constants.contains("mirror_ready_recorded"));
        assertTrue(constants.contains("capture_recorded"));
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

    private static int invocationCount(String methodName, String invokedName)
            throws IOException {
        int[] count = {0};
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
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (invokedName.equals(name)) count[0]++;
                    }
                };
            }
        }, 0);
        return count[0];
    }

    private static Set<String> invokingMethods(
            String invokedOwner,
            String invokedName
    ) throws IOException {
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
                String callerName = name;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (invokedOwner.equals(owner) && invokedName.equals(name)) {
                            names.add(callerName);
                        }
                    }
                };
            }
        }, 0);
        return names;
    }

    private static int fieldReadCount(String methodName, String fieldName)
            throws IOException {
        int[] count = {0};
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
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.GETFIELD
                                && SCENARIO.equals(owner)
                                && fieldName.equals(name)) count[0]++;
                    }
                };
            }
        }, 0);
        return count[0];
    }

    private static boolean singleFieldWriteFollowsSingleInvocation(
            String methodName,
            String invokedOwner,
            String invokedName,
            String fieldName
    ) throws IOException {
        int[] eventIndex = {0};
        int[] invocationIndex = {-1};
        int[] fieldWriteIndex = {-1};
        int[] invocationCount = {0};
        int[] fieldWriteCount = {0};
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
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        if (invokedOwner.equals(owner) && invokedName.equals(name)) {
                            invocationCount[0]++;
                            invocationIndex[0] = eventIndex[0];
                        }
                        eventIndex[0]++;
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.PUTFIELD
                                && SCENARIO.equals(owner)
                                && fieldName.equals(name)) {
                            fieldWriteCount[0]++;
                            fieldWriteIndex[0] = eventIndex[0];
                        }
                        eventIndex[0]++;
                    }
                };
            }
        }, 0);
        return invocationCount[0] == 1
                && fieldWriteCount[0] == 1
                && invocationIndex[0] < fieldWriteIndex[0];
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
