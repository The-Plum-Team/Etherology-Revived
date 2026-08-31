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

final class ForestLanternScenarioBytecodeTest {

    private static final String SCENARIO =
            "dev/theplumteam/etherology/baseline/fabric/ForestLanternScenario";

    @Test
    void scenarioLinksOnlyThroughRegistryAndVanillaFabricApis() throws IOException {
        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("ru/feytox/etherology/"));
        assertTrue(constants.contains("forest_lantern"));
        assertTrue(constants.contains("forest_lantern_crumb"));
        assertTrue(constants.contains("peach_log"));

        Set<String> setupCalls = invocationNames("setupServerWorld");
        assertTrue(setupCalls.containsAll(Set.of(
                "getDroppedStacks",
                "getMiningSpeedMultiplier",
                "runDeterministicJumpProbe",
                "teleport"
        )));
        assertTrue(invocationNames("inspectRecipes").contains("getRecipeManager"));
        assertFalse(setupCalls.contains("forName"));
        assertFalse(setupCalls.contains("getDeclaredMethod"));
    }

    @Test
    void fixtureUsesEveryAgeAndHorizontalFacingAsTwentyExactStates()
            throws Exception {
        var field = ForestLanternScenario.class.getDeclaredField("STATE_EXPECTATIONS");
        field.setAccessible(true);
        List<?> expectations = (List<?>) field.get(null);
        assertEquals(20, expectations.size());
        assertEquals(20, new HashSet<>(expectations).size());

        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertTrue(constants.contains("age"));
        assertTrue(constants.contains("facing"));
        assertTrue(constants.contains("20 unique non-negative raw ids"));
        assertTrue(constants.contains("15.0 for all 20 states"));
    }

    @Test
    void jumpProbeSeedsVanillaRandomThenInvokesOneVanillaJump() throws IOException {
        List<Invocation> calls = methodInvocations("runDeterministicJumpProbe");
        int setSeed = calls.indexOf(new Invocation(
                "net/minecraft/util/math/random/Random",
                "setSeed"
        ));
        int jump = calls.indexOf(new Invocation(
                "dev/theplumteam/etherology/baseline/fabric/mixin/PlayerEntityJumpInvoker",
                "etherologyOriginalBaseline$invokeJump"
        ));
        assertTrue(setSeed >= 0 && setSeed < jump);
        assertEquals(
                1L,
                calls.stream().filter(invocation ->
                        "etherologyOriginalBaseline$invokeJump".equals(invocation.name())
                ).count()
        );
        assertTrue(calls.contains(new Invocation(
                "net/minecraft/server/world/ServerWorld",
                "getEntitiesByClass"
        )));
    }

    @Test
    void stableCaptureRechecksAllNativeReadinessBeforeScreenshot() throws IOException {
        List<Invocation> renderCallback = methodInvocations("onGameRenderCompleted");
        int readiness = renderCallback.indexOf(new Invocation(
                SCENARIO,
                "isCaptureStateExact"
        ));
        int counter = renderCallback.indexOf(new Invocation(
                "dev/theplumteam/etherology/baseline/fabric/StableRenderCounter",
                "observe"
        ));
        int capture = renderCallback.indexOf(new Invocation(SCENARIO, "captureWorld"));
        assertTrue(readiness >= 0 && readiness < counter && counter < capture);

        List<Invocation> captureCalls = methodInvocations("captureWorld");
        int finalReadiness = captureCalls.indexOf(new Invocation(
                SCENARIO,
                "isCaptureStateExact"
        ));
        int screenshot = captureCalls.indexOf(new Invocation(
                "net/minecraft/client/util/ScreenshotRecorder",
                "saveScreenshot"
        ));
        assertTrue(finalReadiness >= 0 && finalReadiness < screenshot);
    }

    @Test
    void directReportAssertionsHaveOneExactOrder() throws IOException {
        List<String> expected = List.of(
                "fabric_mod_loaded:etherology",
                "forest_lantern_resources_exact",
                "registry:block:etherology:forest_lantern",
                "registry:item:etherology:forest_lantern",
                "registry:item:etherology:forest_lantern_crumb",
                "forest_lantern_properties_exact",
                "forest_lantern_default_state_exact",
                "forest_lantern_state_count_exact",
                "forest_lantern_state_network_ids_exact",
                "native_framebuffer_dimensions",
                "completed_world_renders_before_capture",
                "capture_render_ready",
                "capture_camera_exact",
                "native_screenshot_written",
                "integrated_world_joined",
                "server_arena_chunk_loaded",
                "server_player_creative",
                "server_forest_lantern_states_exact",
                "client_forest_lantern_states_exact",
                "server_forest_lantern_state_network_ids_exact",
                "forest_lantern_shears_speed_exact",
                "forest_lantern_immature_loot_empty",
                "forest_lantern_mature_loot_exact",
                "forest_lantern_jump_seed_exact",
                "forest_lantern_jump_stepping_position_exact",
                "forest_lantern_jump_break_exact",
                "forest_lantern_jump_drop_exact",
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
        String resourceName = ForestLanternScenario.class.getSimpleName() + ".class";
        try (InputStream input = ForestLanternScenario.class.getResourceAsStream(resourceName)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private record Invocation(String owner, String name) {
    }
}
