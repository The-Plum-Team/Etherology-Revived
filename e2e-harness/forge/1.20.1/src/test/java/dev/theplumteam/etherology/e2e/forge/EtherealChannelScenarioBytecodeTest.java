package dev.theplumteam.etherology.e2e.forge;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EtherealChannelScenarioBytecodeTest {

    private static final String SCENARIO_CLASS =
            "dev/theplumteam/etherology/e2e/forge/EtherealChannelScenario.class";
    private static final String HARNESS_CLASS =
            "dev/theplumteam/etherology/e2e/forge/ForgeE2eHarness.class";
    private static final String SCENARIO =
            "dev/theplumteam/etherology/e2e/forge/EtherealChannelScenario";

    @Test
    void remainsUnlinkedFromProductionClasses() throws IOException {
        byte[] bytes = classBytes(SCENARIO_CLASS);
        String classContent = new String(bytes, StandardCharsets.ISO_8859_1);

        assertFalse(
                classContent.contains("ru/feytox/etherology/"),
                "The packaged harness must resolve production only through registry ids"
        );
        assertFalse(
                classContent.contains("ru.feytox.etherology."),
                "The packaged harness must not reflectively load production classes"
        );
        assertTrue(classContent.contains("net/minecraft/block/RepeaterBlock"));
        assertTrue(classContent.contains("net/minecraft/block/LeverBlock"));
        assertTrue(classContent.contains("net/minecraft/block/enums/WallMountLocation"));
        assertTrue(classContent.contains("REDSTONE_BLOCK"));
    }

    @Test
    void packagesExactRegistryNbtAndScreenshotContracts() throws IOException {
        ClassFacts facts = facts(SCENARIO_CLASS);

        assertTrue(facts.constants().contains("etherology"));
        assertTrue(facts.constants().contains("ethereal_channel"));
        assertTrue(facts.constants().contains("ethereal_channel_block_entity"));
        assertTrue(facts.constants().contains("ethereal_storage"));
        assertTrue(facts.constants().contains("ethereal_storage_block_entity"));
        assertTrue(facts.constants().contains("storage_ether"));
        assertTrue(facts.constants().contains("stored_ether"));
        assertTrue(facts.constants().contains("evaporating"));
        assertTrue(facts.constants().contains("cross_evaporating"));
        assertTrue(facts.constants().contains("activated"));
        assertTrue(facts.constants().contains("facing"));
        assertTrue(facts.constants().contains("east"));
        assertTrue(facts.constants().contains("up"));
        assertTrue(facts.constants().contains("out"));
        assertTrue(facts.constants().contains("in"));
        assertTrue(facts.constants().contains("prepowered_placement"));
        assertTrue(facts.constants().contains("fixture_positions_distinct"));
        assertTrue(facts.constants().contains("lever_support_topology"));
        assertTrue(facts.constants().contains(
                "lever=minecraft:lever;survived=true;can_place=true;face=wall;"
                        + "facing=north;channel=etherology:ethereal_channel;"
                        + "channel_block_entity=etherology:ethereal_channel_block_entity;"
                        + "channel_facing=east;north=in;east=out;west=empty;cross=true"
        ));
        assertTrue(facts.constants().contains("ethereal-channel-gated.png"));
        assertTrue(facts.constants().contains("ethereal-channel-transferred.png"));
        assertTrue(facts.constants().contains("ethereal-channel-reopened.png"));
    }

    @Test
    void drivesForcedChunkCadenceSyncAndRestartThroughVanillaContracts()
            throws IOException {
        ClassFacts facts = facts(SCENARIO_CLASS);

        assertInvocation(facts, "net/minecraft/server/world/ServerWorld", "setChunkForced");
        assertInvocation(facts, "net/minecraft/server/world/ServerWorld", "getForcedChunks");
        assertInvocation(facts, "net/minecraft/block/entity/BlockEntity", "createFromNbt");
        assertInvocation(facts, "net/minecraft/block/entity/BlockEntity", "toUpdatePacket");
        assertInvocation(
                facts,
                "net/minecraft/block/entity/BlockEntity",
                "toInitialChunkDataNbt"
        );
        assertInvocation(
                facts,
                "net/minecraft/server/integrated/IntegratedServer",
                "saveAll"
        );
        assertInvocation(facts, "net/minecraft/client/world/ClientWorld", "disconnect");
        assertInvocation(facts, "net/minecraft/client/MinecraftClient", "disconnect");
        assertInvocation(
                facts,
                "net/minecraft/server/integrated/IntegratedServerLoader",
                "start"
        );
        assertTrue(facts.methodNames().contains("inspectGatedTransfer"));
        assertTrue(facts.methodNames().contains("inspectReleasedTransfer"));
        assertTrue(facts.methodNames().contains("inspectNoReverseMovement"));
        assertTrue(facts.methodNames().contains("inspectEvaporation"));
        assertTrue(facts.methodNames().contains("inspectQuiescentEvaporationState"));
        assertTrue(facts.constants().contains(5L));
        assertTrue(facts.constants().contains(0.2f));
        assertTrue(facts.constants().contains(0.8f));
    }

    @Test
    void dispatcherConstructsBothControllersAndRegistersExactlyOne() throws IOException {
        ClassFacts facts = facts(HARNESS_CLASS);
        long registrations = facts.invocations().stream()
                .filter(invocation -> invocation.owner().equals(
                        "net/minecraftforge/eventbus/api/IEventBus"
                ))
                .filter(invocation -> invocation.name().equals("register"))
                .count();

        assertEquals(1L, registrations);
        assertTrue(facts.constructedTypes().contains(
                "dev/theplumteam/etherology/e2e/forge/EtherealStorageScenario"
        ));
        assertTrue(facts.constructedTypes().contains(SCENARIO));
    }

    @Test
    void observesScheduledStrongPrepowerWithoutAnyManualNeighborRefresh()
            throws IOException {
        assertTrue(EtherealChannelScenario.hasDistinctFixturePositions());
        List<Invocation> placementInvocations = methodInvocations(
                "placePrePoweredChannel"
        );
        Invocation setBlockState = new Invocation(
                "net/minecraft/server/world/ServerWorld",
                "setBlockState"
        );
        Invocation getStrongPower = new Invocation(
                "net/minecraft/server/world/ServerWorld",
                "getReceivedStrongRedstonePower"
        );
        List<Integer> placementIndexes = invocationIndexes(
                placementInvocations,
                setBlockState
        );

        assertEquals(4, placementIndexes.size());
        assertEquals(1L, placementInvocations.stream().filter(getStrongPower::equals).count());
        assertTrue(
                placementIndexes.get(2) < placementInvocations.indexOf(getStrongPower)
                        && placementInvocations.indexOf(getStrongPower)
                        < placementIndexes.get(3)
        );
        assertTrue(placementInvocations.contains(
                new Invocation(SCENARIO, "poweredRepeaterState")
        ));
        assertTrue(placementInvocations.contains(
                new Invocation(SCENARIO, "hasPoweredRepeater")
        ));

        List<Invocation> setupInvocations = methodInvocations("setupChannelFixture");
        assertEquals(
                2L,
                setupInvocations.stream()
                        .filter(new Invocation(SCENARIO, "placePrePoweredChannel")::equals)
                        .count()
        );
        assertTrue(setupInvocations.stream().anyMatch(
                invocation -> invocation.name().equals("getTime")
        ));

        List<Invocation> scheduledInvocations = methodInvocations("inspectScheduledSetup");
        assertTrue(scheduledInvocations.stream().anyMatch(
                invocation -> invocation.name().equals("getTime")
        ));
        assertEquals(
                2L,
                scheduledInvocations.stream()
                        .filter(new Invocation(SCENARIO, "hasPoweredRepeater")::equals)
                        .count()
        );
        assertEquals(
                2L,
                scheduledInvocations.stream()
                        .filter(invocation -> invocation.name().equals(
                                "getReceivedStrongRedstonePower"
                        ))
                        .count()
        );
        assertTrue(scheduledInvocations.contains(
                new Invocation(SCENARIO, "captureSnapshot")
        ));
        List<Integer> serializedStateIndexes = invocationIndexes(
                scheduledInvocations,
                new Invocation(SCENARIO, "statePropertyName")
        );
        List<Integer> stringComparisonIndexes = invocationIndexes(
                scheduledInvocations,
                new Invocation("java/lang/String", "equals")
        );
        assertEquals(2, serializedStateIndexes.size());
        assertEquals(2, stringComparisonIndexes.size());
        assertTrue(
                serializedStateIndexes.get(0) < stringComparisonIndexes.get(0)
                        && serializedStateIndexes.get(1) < stringComparisonIndexes.get(1)
        );
        assertTrue(scheduledInvocations.stream().anyMatch(
                invocation -> invocation.name().equals("placementWorldTime")
        ));
        assertTrue(methodInvocations("onServerTick").contains(
                new Invocation(SCENARIO, "inspectScheduledSetup")
        ));

        ClassFacts facts = facts(SCENARIO_CLASS);
        assertFalse(facts.methodNames().contains("refreshChannelPower"));
        assertFalse(
                facts.invocations().stream().anyMatch(
                        invocation -> invocation.name().equals("updateNeighbor")
                ),
                "The harness must observe natural NOTIFY_ALL propagation"
        );
    }

    @Test
    void validatesNativeWallLeverSupportAfterNaturalPropagationAndRestart()
            throws IOException {
        List<Invocation> setupInvocations = methodInvocations("setupChannelFixture");
        assertTrue(setupInvocations.contains(
                new Invocation(SCENARIO, "attachedLeverState")
        ));

        List<Invocation> observationInvocations = methodInvocations(
                "observeLeverSupport"
        );
        assertEquals(
                1L,
                observationInvocations.stream()
                        .filter(new Invocation(
                                "net/minecraft/block/BlockState",
                                "canPlaceAt"
                        )::equals)
                        .count()
        );
        assertTrue(observationInvocations.contains(
                new Invocation(
                        "net/minecraft/world/WorldView",
                        "getBlockEntity"
                )
        ));
        assertEquals(
                6L,
                observationInvocations.stream()
                        .filter(new Invocation(SCENARIO, "statePropertyName")::equals)
                        .count()
        );
        assertTrue(methodInvocations("statePropertyName").contains(
                new Invocation("net/minecraft/state/property/Property", "name")
        ));
        assertFalse(observationInvocations.contains(
                new Invocation("java/lang/String", "valueOf")
        ));

        assertTrue(methodInvocations("inspectScheduledSetup").contains(
                new Invocation(SCENARIO, "observeLeverSupport")
        ));
        assertTrue(methodInvocations("captureSnapshot").contains(
                new Invocation(SCENARIO, "observeLeverSupport")
        ));
        assertTrue(methodInvocations("hasInitialClientMirror").contains(
                new Invocation(SCENARIO, "observeLeverSupport")
        ));
        assertTrue(methodInvocations("hasReopenedClientMirror").contains(
                new Invocation(SCENARIO, "observeLeverSupport")
        ));
        assertTrue(methodInvocations("samePersistentState").contains(
                new Invocation(
                        SCENARIO + "$LeverSupportResult",
                        "equals"
                )
        ));
        ClassFacts facts = facts(SCENARIO_CLASS);
        assertFalse(
                facts.invocations().stream().anyMatch(
                        invocation -> invocation.name().equals("neighborUpdate")
                                || invocation.name().equals("updateNeighbors")
                ),
                "The lever probe must rely on natural NOTIFY_ALL propagation"
        );
    }

    @Test
    void requiresExactClientStateForLongConsecutiveRenderWindows()
            throws IOException {
        assertEquals(120, fieldConstant("REQUIRED_GATED_RENDERS"));
        assertEquals(120, fieldConstant("REQUIRED_TRANSFERRED_RENDERS"));
        assertEquals(120, fieldConstant("REQUIRED_REOPENED_RENDERS"));

        List<Invocation> renderInvocations = methodInvocations("onWorldRendered");
        assertEquals(
                3L,
                renderInvocations.stream()
                        .filter(new Invocation(
                                SCENARIO,
                                "captureAfterStableRenders"
                        )::equals)
                        .count()
        );

        List<String> mirrorMethods = List.of(
                "hasGatedClientMirror",
                "hasTransferredClientMirror",
                "hasReopenedClientMirror"
        );
        List<Invocation> mirrorDispatchInvocations = methodInvocations(
                "hasCaptureClientMirror"
        );
        for (String mirrorMethod : mirrorMethods) {
            Invocation mirrorInvocation = new Invocation(SCENARIO, mirrorMethod);
            assertEquals(
                    1L,
                    mirrorDispatchInvocations.stream().filter(mirrorInvocation::equals).count()
            );
            assertEquals(
                    1L,
                    methodInvocations(mirrorMethod).stream()
                            .filter(new Invocation(
                                    SCENARIO,
                                    "hasCaptureFixtureBlocks"
                            )::equals)
                            .count()
            );
        }
        List<Long> activationChecks = new ArrayList<>();
        for (String mirrorMethod : mirrorMethods) {
            activationChecks.add(
                    methodInvocations(mirrorMethod).stream()
                            .filter(new Invocation(SCENARIO, "isActivated")::equals)
                            .count()
            );
        }
        assertEquals(
                List.of(2L, 2L, 2L),
                activationChecks,
                "Every capture role must prove its exact main and evaporation activation state"
        );
        for (String mirrorMethod : mirrorMethods) {
            List<Invocation> mirrorInvocations = methodInvocations(mirrorMethod);
            assertEquals(
                    2L,
                    mirrorInvocations.stream()
                            .filter(new Invocation(SCENARIO, "clientBlockEntity")::equals)
                            .count()
            );
            assertEquals(
                    2L,
                    mirrorInvocations.stream()
                            .filter(new Invocation(SCENARIO, "isEvaporating")::equals)
                            .count()
            );
            assertEquals(
                    2L,
                    mirrorInvocations.stream()
                            .filter(new Invocation(SCENARIO, "isCrossEvaporating")::equals)
                            .count()
            );
        }

        List<Invocation> fixtureInvocations = methodInvocations(
                "hasCaptureFixtureBlocks"
        );
        assertEquals(
                1L,
                fixtureInvocations.stream()
                        .filter(new Invocation(SCENARIO, "hasFixtureBlocks")::equals)
                        .count()
        );
        assertEquals(
                2L,
                fixtureInvocations.stream()
                        .filter(new Invocation(SCENARIO, "clientBlockEntity")::equals)
                        .count(),
                "Every capture must retain both storage block entities for their renderers"
        );
        assertEquals(
                2L,
                fixtureInvocations.stream()
                        .filter(new Invocation(SCENARIO, "stateProperty")::equals)
                        .count()
        );
        assertEquals(
                3L,
                fixtureInvocations.stream()
                        .filter(new Invocation(SCENARIO, "statePropertyName")::equals)
                        .count()
        );
        assertEquals(
                1L,
                fixtureInvocations.stream()
                        .filter(new Invocation(SCENARIO, "observeLeverSupport")::equals)
                        .count()
        );
        assertEquals(
                4L,
                fixtureInvocations.stream()
                        .filter(new Invocation(SCENARIO, "hasPoweredRepeater")::equals)
                        .count()
        );
        assertEquals(
                2L,
                fixtureInvocations.stream()
                        .filter(new Invocation(
                                "net/minecraft/block/BlockState",
                                "isAir"
                        )::equals)
                        .count(),
                "Transferred and reopened captures must retain the restored evaporation repeater"
        );

        List<Invocation> exactStateInvocations = methodInvocations("isCaptureStateExact");
        assertEquals(
                List.of(
                        new Invocation(SCENARIO, "hasCaptureClientMirror"),
                        new Invocation(SCENARIO, "hasExpectedFramebuffer"),
                        new Invocation(SCENARIO, "isFixtureRenderReady"),
                        new Invocation(SCENARIO, "hasExpectedCameraPose")
                ),
                exactStateInvocations
        );
        assertEquals(
                1L,
                methodFieldAccesses("isCaptureStateExact").stream()
                        .filter(field -> field.owner().equals(
                                "net/minecraft/client/MinecraftClient"
                        ))
                        .filter(field -> field.name().equals("currentScreen"))
                        .filter(field -> field.opcode() == Opcodes.GETFIELD)
                        .count()
        );

        List<Invocation> rendererInvocations = methodInvocations("isFixtureRenderReady");
        assertEquals(
                1L,
                rendererInvocations.stream()
                        .filter(new Invocation(
                                "net/minecraft/client/render/WorldRenderer",
                                "isTerrainRenderComplete"
                        )::equals)
                        .count()
        );
        assertEquals(
                1L,
                rendererInvocations.stream()
                        .filter(new Invocation(
                                "net/minecraft/client/render/WorldRenderer",
                                "isRenderingReady"
                        )::equals)
                        .count()
        );
        assertTrue(
                methodFieldAccesses("isFixtureRenderReady").stream().anyMatch(
                        field -> field.opcode() == Opcodes.GETSTATIC
                                && field.owner().equals(SCENARIO)
                                && field.name().equals("FIXTURE_POSITIONS")
                )
        );
        Set<String> cameraInvocationNames = methodInvocations("hasExpectedCameraPose")
                .stream()
                .map(Invocation::name)
                .collect(Collectors.toSet());
        assertTrue(
                cameraInvocationNames.containsAll(Set.of(
                        "getCameraEntity",
                        "getPerspective",
                        "isFirstPerson",
                        "isOnGround",
                        "getX",
                        "getY",
                        "getZ",
                        "getYaw",
                        "getPitch",
                        "wrapDegrees",
                        "abs"
                )),
                "The capture camera check must bind position, rotation, perspective, and player"
        );

        List<Invocation> captureInvocations = methodInvocations(
                "captureAfterStableRenders"
        );
        assertEquals(
                1L,
                captureInvocations.stream()
                        .filter(new Invocation(SCENARIO, "isCaptureStateExact")::equals)
                        .count()
        );
        assertEquals(
                2L,
                methodFieldAccesses("captureAfterStableRenders").stream()
                        .filter(field -> field.owner().equals(SCENARIO))
                        .filter(field -> field.name().equals("completedRenders"))
                        .filter(field -> field.opcode() == Opcodes.PUTFIELD)
                        .count(),
                "The render streak must reset on an invalid frame before counting again"
        );

        List<Invocation> screenshotInvocations = methodInvocations("saveScreenshot");
        int mirrorIndex = screenshotInvocations.indexOf(
                new Invocation(SCENARIO, "hasCaptureClientMirror")
        );
        int renderReadyIndex = screenshotInvocations.indexOf(
                new Invocation(SCENARIO, "isFixtureRenderReady")
        );
        int cameraIndex = screenshotInvocations.indexOf(
                new Invocation(SCENARIO, "hasExpectedCameraPose")
        );
        int framebufferIndex = screenshotInvocations.indexOf(
                new Invocation(SCENARIO, "hasExpectedFramebuffer")
        );
        int latchIndex = screenshotInvocations.indexOf(
                new Invocation(SCENARIO, "latchCaptureState")
        );
        int recorderIndex = screenshotInvocations.indexOf(
                new Invocation(
                        "net/minecraft/client/util/ScreenshotRecorder",
                        "saveScreenshot"
                )
        );
        assertTrue(
                mirrorIndex >= 0
                        && mirrorIndex < renderReadyIndex
                        && renderReadyIndex < cameraIndex
                        && cameraIndex < framebufferIndex
                        && framebufferIndex < latchIndex
                        && latchIndex < recorderIndex,
                "Capture state must be rechecked and latched before ScreenshotRecorder"
        );

        Set<String> latchWrites = new HashSet<>();
        for (FieldAccess field : methodFieldAccesses("latchCaptureState")) {
            if (field.opcode() == Opcodes.PUTFIELD && field.owner().equals(SCENARIO)) {
                latchWrites.add(field.name());
            }
        }
        assertEquals(
                Set.of(
                        "gatedCaptureMirror",
                        "transferredCaptureMirror",
                        "reopenedCaptureMirror",
                        "gatedCaptureRenderReady",
                        "transferredCaptureRenderReady",
                        "reopenedCaptureRenderReady",
                        "gatedCaptureCameraExact",
                        "transferredCaptureCameraExact",
                        "reopenedCaptureCameraExact"
                ),
                latchWrites
        );

        ClassFacts facts = facts(SCENARIO_CLASS);
        assertTrue(facts.constants().contains("gated_capture_mirror"));
        assertTrue(facts.constants().contains("transferred_capture_mirror"));
        assertTrue(facts.constants().contains("reopened_capture_mirror"));
        assertTrue(facts.constants().contains("capture_render_ready"));
        assertTrue(facts.constants().contains("capture_camera_exact"));
        Set<String> reportCaptureReads = new HashSet<>();
        for (FieldAccess field : methodFieldAccesses("createReport")) {
            if (field.opcode() == Opcodes.GETFIELD
                    && field.owner().equals(SCENARIO)
                    && latchWrites.contains(field.name())) {
                reportCaptureReads.add(field.name());
            }
        }
        assertEquals(latchWrites, reportCaptureReads);
    }

    private static List<Integer> invocationIndexes(
            List<Invocation> invocations,
            Invocation expected
    ) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < invocations.size(); index++) {
            if (expected.equals(invocations.get(index))) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private static void assertInvocation(ClassFacts facts, String owner, String name) {
        assertTrue(
                facts.invocations().contains(new Invocation(owner, name)),
                () -> "Missing invocation " + owner + '#' + name
        );
    }

    private static ClassFacts facts(String resourceName) throws IOException {
        byte[] bytes = classBytes(resourceName);
        Set<Object> constants = new HashSet<>();
        Set<String> methodNames = new HashSet<>();
        Set<String> constructedTypes = new HashSet<>();
        List<Invocation> invocations = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value
            ) {
                if (value != null) {
                    constants.add(value);
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
                methodNames.add(name);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        constants.add(value);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW) {
                            constructedTypes.add(type);
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
                        invocations.add(new Invocation(owner, name));
                        constants.add(Type.getMethodType(descriptor).getDescriptor());
                    }
                };
            }
        }, 0);
        return new ClassFacts(constants, methodNames, constructedTypes, invocations);
    }

    private static Object fieldConstant(String fieldName) throws IOException {
        Object[] value = new Object[1];
        new ClassReader(classBytes(SCENARIO_CLASS)).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public FieldVisitor visitField(
                            int access,
                            String name,
                            String descriptor,
                            String signature,
                            Object fieldValue
                    ) {
                        if (name.equals(fieldName)) {
                            value[0] = fieldValue;
                        }
                        return null;
                    }
                },
                0
        );
        assertNotNull(value[0], "The compiled field is missing: " + fieldName);
        return value[0];
    }

    private static byte[] classBytes(String resourceName) throws IOException {
        try (InputStream input = EtherealChannelScenarioBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {
            assertNotNull(input, "The compiled Forge E2E class is missing: " + resourceName);
            return input.readAllBytes();
        }
    }

    private static List<Invocation> methodInvocations(String methodName)
            throws IOException {
        List<Invocation> invocations = new ArrayList<>();
        new ClassReader(classBytes(SCENARIO_CLASS)).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                            int access,
                            String name,
                            String descriptor,
                            String signature,
                            String[] exceptions
                    ) {
                        if (!name.equals(methodName)) {
                            return null;
                        }
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(
                                    int opcode,
                                    String owner,
                                    String name,
                                    String descriptor,
                                    boolean isInterface
                            ) {
                                invocations.add(new Invocation(owner, name));
                            }
                        };
                    }
                },
                0
        );
        return invocations;
    }

    private static List<FieldAccess> methodFieldAccesses(String methodName)
            throws IOException {
        List<FieldAccess> fieldAccesses = new ArrayList<>();
        new ClassReader(classBytes(SCENARIO_CLASS)).accept(
                new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                            int access,
                            String name,
                            String descriptor,
                            String signature,
                            String[] exceptions
                    ) {
                        if (!name.equals(methodName)) {
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
                                fieldAccesses.add(new FieldAccess(opcode, owner, name));
                            }
                        };
                    }
                },
                0
        );
        return fieldAccesses;
    }

    private record Invocation(String owner, String name) {
    }

    private record FieldAccess(int opcode, String owner, String name) {
    }

    private record ClassFacts(
            Set<Object> constants,
            Set<String> methodNames,
            Set<String> constructedTypes,
            List<Invocation> invocations
    ) {
    }
}
