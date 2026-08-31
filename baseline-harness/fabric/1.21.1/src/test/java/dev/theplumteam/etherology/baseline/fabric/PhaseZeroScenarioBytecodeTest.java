package dev.theplumteam.etherology.baseline.fabric;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhaseZeroScenarioBytecodeTest {

    private static final String SCENARIO =
            "dev/theplumteam/etherology/baseline/fabric/PhaseZeroScenario";

    @Test
    void stableCaptureUsesExactNativeReadinessAndRechecksBeforeScreenshot() throws IOException {
        List<Invocation> renderCallback = methodInvocations("onGameRenderCompleted");
        int readinessIndex = renderCallback.indexOf(new Invocation(SCENARIO, "isCaptureStateExact"));
        int counterIndex = renderCallback.indexOf(new Invocation(
                "dev/theplumteam/etherology/baseline/fabric/StableRenderCounter",
                "observe"
        ));
        int captureIndex = renderCallback.indexOf(new Invocation(SCENARIO, "captureWorld"));
        assertTrue(readinessIndex >= 0 && readinessIndex < counterIndex && counterIndex < captureIndex);
        assertTrue(renderCallback.contains(new Invocation(SCENARIO, "fail")));
        assertTrue(methodCaughtTypes("onGameRenderCompleted").contains(
                "java/lang/RuntimeException"
        ));
        assertTrue(methodCaughtTypes("inspectScreenshot").contains(
                "java/lang/RuntimeException"
        ));

        List<Invocation> readiness = methodInvocations("isFixtureRenderReady");
        assertEquals(
                1L,
                readiness.stream().filter(new Invocation(
                        "net/minecraft/client/render/WorldRenderer",
                        "isTerrainRenderComplete"
                )::equals).count()
        );
        assertEquals(
                1L,
                readiness.stream().filter(new Invocation(
                        "net/minecraft/client/render/WorldRenderer",
                        "isRenderingReady"
                )::equals).count()
        );

        List<Invocation> capture = methodInvocations("captureWorld");
        int finalReadinessIndex = capture.indexOf(new Invocation(SCENARIO, "isCaptureStateExact"));
        int recorderIndex = capture.indexOf(new Invocation(
                "net/minecraft/client/util/ScreenshotRecorder",
                "saveScreenshot"
        ));
        assertTrue(finalReadinessIndex >= 0 && finalReadinessIndex < recorderIndex);
        assertTrue(capture.contains(new Invocation(
                "dev/theplumteam/etherology/baseline/fabric/StableRenderCounter",
                "observe"
        )));
    }

    @Test
    void cameraAndWindowContractsAreNativeAndExact() throws IOException {
        Set<String> cameraCalls = invocationNames("hasExpectedCameraPose");
        assertTrue(cameraCalls.containsAll(Set.of(
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
        )));

        List<Invocation> setup = methodInvocations("setupServerWorld");
        assertTrue(setup.contains(new Invocation(
                "net/minecraft/server/network/ServerPlayerEntity",
                "teleport"
        )));
        assertFalse(setup.stream().anyMatch(invocation -> "requestTeleport".equals(invocation.name())));

        List<Invocation> sizing = methodInvocations("requestExpectedFramebuffer");
        assertEquals(
                1L,
                sizing.stream().filter(new Invocation(
                        "net/minecraft/client/util/Window",
                        "toggleFullscreen"
                )::equals).count()
        );
        assertTrue(sizing.contains(new Invocation(
                "net/minecraft/client/util/Window",
                "setWindowedSize"
        )));
        assertEquals(
                2L,
                sizing.stream().filter(new Invocation(
                        "dev/theplumteam/etherology/baseline/fabric/FramebufferWindowSizing",
                        "requestedWindowDimension"
                )::equals).count()
        );

        List<Invocation> worldView = methodInvocations("isWorldViewReady");
        assertTrue(worldView.contains(new Invocation(
                "net/minecraft/client/util/Window",
                "getFramebufferWidth"
        )));
        assertTrue(worldView.contains(new Invocation(
                "net/minecraft/client/util/Window",
                "getFramebufferHeight"
        )));
    }

    @Test
    void fixtureBindsAllPublishedBlockEntityTypesAndLiveWorldIdentity() throws IOException {
        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertTrue(constants.contains("brewing_cauldron_block_entity"));
        assertTrue(constants.contains("empowerment_table_block_entity"));
        assertTrue(constants.contains("ethereal_storage_block_entity"));
        assertTrue(constants.contains("armillary_sphere_block_entity"));

        List<Invocation> setup = methodInvocations("setupServerWorld");
        assertTrue(setup.contains(new Invocation(
                "net/minecraft/block/entity/BlockEntity",
                "getType"
        )));
        assertTrue(setup.contains(new Invocation(
                "net/minecraft/server/integrated/IntegratedServer",
                "getSaveProperties"
        )));
        assertTrue(setup.contains(new Invocation(
                "net/minecraft/world/SaveProperties",
                "getLevelName"
        )));
        assertTrue(setup.contains(new Invocation(
                "net/minecraft/server/world/ServerWorld",
                "getSeed"
        )));
        assertTrue(setup.contains(new Invocation(
                "net/minecraft/server/world/ServerWorld",
                "getRegistryKey"
        )));
    }

    @Test
    void directReportAssertionsHaveOneExactOrder() throws IOException {
        List<String> expected = List.of(
                "fabric_mod_loaded:etherology",
                "published_resources_loaded",
                "registry_preflight",
                "etherology_block_states_have_network_ids",
                "native_framebuffer_dimensions",
                "completed_world_renders_before_capture",
                "capture_render_ready",
                "capture_camera_exact",
                "native_screenshot_written",
                "integrated_world_joined",
                "client_world_mirrors_server_fixture",
                "client_fixture_block_entity_types_exact",
                "server_arena_chunk_loaded",
                "server_player_creative",
                "server_fixture_blocks_placed",
                "server_fixture_block_entities_present",
                "server_fixture_block_entity_types_exact",
                "live_world_identity",
                "forced_world_save",
                "isolated_save_directory_present"
        );
        Set<String> expectedSet = new HashSet<>(expected);
        List<String> actual = methodStringConstants("createReport").stream()
                .filter(expectedSet::contains)
                .toList();

        assertEquals(expected, actual);
    }

    private static Set<String> invocationNames(String methodName) throws IOException {
        Set<String> names = new HashSet<>();
        for (Invocation invocation : methodInvocations(methodName)) {
            names.add(invocation.name());
        }
        return names;
    }

    private static List<Invocation> methodInvocations(String methodName) throws IOException {
        List<Invocation> invocations = new ArrayList<>();
        visitMethod(methodName, new MethodVisitor(Opcodes.ASM9) {
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
        });
        return invocations;
    }

    private static List<String> methodStringConstants(String methodName) throws IOException {
        List<String> constants = new ArrayList<>();
        visitMethod(methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof String string) {
                    constants.add(string);
                }
            }
        });
        return constants;
    }

    private static Set<String> methodCaughtTypes(String methodName) throws IOException {
        Set<String> caughtTypes = new HashSet<>();
        visitMethod(methodName, new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitTryCatchBlock(
                    org.objectweb.asm.Label start,
                    org.objectweb.asm.Label end,
                    org.objectweb.asm.Label handler,
                    String type
            ) {
                if (type != null) {
                    caughtTypes.add(type);
                }
            }
        });
        return caughtTypes;
    }

    private static void visitMethod(String methodName, MethodVisitor visitor) throws IOException {
        ClassReader reader = new ClassReader(classBytes());
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                return methodName.equals(name) ? visitor : null;
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static byte[] classBytes() throws IOException {
        String resourceName = PhaseZeroScenario.class.getSimpleName() + ".class";
        try (InputStream input = PhaseZeroScenario.class.getResourceAsStream(resourceName)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private record Invocation(String owner, String name) {
    }
}
