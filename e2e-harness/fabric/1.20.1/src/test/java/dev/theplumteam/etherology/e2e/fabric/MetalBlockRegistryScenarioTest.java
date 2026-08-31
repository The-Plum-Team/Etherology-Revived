package dev.theplumteam.etherology.e2e.fabric;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MetalBlockRegistryScenarioTest {

    @Test
    void assertionInventoryIsExactAndOrdered() {
        assertEquals(
                List.of(
                        "fabric_mod_loaded:etherology",
                        "registry:block:etherology:azel_block",
                        "registry:block:etherology:ethril_block",
                        "registry:block:etherology:ebony_block",
                        "default_state_network_ids",
                        "client_render_resources",
                        "packaged_root_jar:etherology",
                        "packaged_root_jar:etherology_e2e_harness",
                        "integrated_world_joined",
                        "server_arena_chunk_loaded",
                        "before_fixture_exact",
                        "before_capture_render_ready",
                        "before_capture_camera_exact",
                        "before_consecutive_stable_renders",
                        "before_framebuffer_dimensions",
                        "native_screenshot_written:before",
                        "server_fixture_ids_exact",
                        "after_capture_client_fixture_ids_exact",
                        "after_capture_render_ready",
                        "after_capture_camera_exact",
                        "after_consecutive_stable_renders",
                        "after_framebuffer_dimensions",
                        "native_screenshot_written:after",
                        "forced_world_save",
                        "isolated_save_directory_present"
                ),
                MetalBlockRegistryScenario.ASSERTION_NAMES
        );
    }

    @Test
    void readyResourcesAreExactAndOrdered() {
        assertEquals(
                List.of(
                        "etherology:blockstates/azel_block.json",
                        "etherology:models/block/azel_block.json",
                        "etherology:textures/block/azel_block.png",
                        "etherology:blockstates/ethril_block.json",
                        "etherology:models/block/ethril_block.json",
                        "etherology:textures/block/ethril_block.png",
                        "etherology:blockstates/ebony_block.json",
                        "etherology:models/block/ebony_block.json",
                        "etherology:textures/block/ebony_block.png"
                ),
                MetalBlockRegistryScenario.READY_RESOURCES.stream()
                        .map(Object::toString)
                        .toList()
        );
    }

    @Test
    void stableRenderCounterRequiresConsecutiveExactCallbacks() {
        int completedRenders = 0;
        for (int index = 0; index < MetalBlockRegistryScenario.REQUIRED_COMPLETED_RENDERS; index++) {
            completedRenders = MetalBlockRegistryScenario.nextStableRenderCount(
                    completedRenders,
                    true
            );
        }
        assertEquals(MetalBlockRegistryScenario.REQUIRED_COMPLETED_RENDERS, completedRenders);

        assertEquals(
                0,
                MetalBlockRegistryScenario.nextStableRenderCount(completedRenders, false)
        );
        assertEquals(1, MetalBlockRegistryScenario.nextStableRenderCount(0, true));
    }

    @Test
    void packagedScenarioUsesRegistryIdsWithoutProductionOrCommandLinkage() throws IOException {
        String classConstants = new String(
                packagedScenarioClassBytes(),
                StandardCharsets.ISO_8859_1
        );

        assertFalse(classConstants.contains("ru/feytox/etherology/"));
        assertFalse(classConstants.contains("getCommandManager"));
        assertFalse(classConstants.contains("executeCommand"));
        assertFalse(classConstants.contains("sendCommand"));
        assertFalse(classConstants.contains("clickCreativeStack"));
        assertFalse(classConstants.contains("CREATIVE"));
        assertTrue(classConstants.contains(MetalBlockRegistryScenario.SCENARIO_ID));
        assertTrue(classConstants.contains("azel_block"));
        assertTrue(classConstants.contains("ethril_block"));
        assertTrue(classConstants.contains("ebony_block"));
        assertTrue(classConstants.contains("polished_andesite"));
        assertTrue(classConstants.contains("setBlockState"));
        assertTrue(classConstants.contains("saveAll"));
        assertTrue(classConstants.contains("scheduleStop"));
    }

    @Test
    void placementRunsOnTheServerExecutorAndUsesDirectBlockStateMutation() throws IOException {
        List<String> submissionCalls = methodCallsContaining("submitMetalBlockPlacement");
        List<String> placementCalls = methodCallsContaining("placeMetalBlocks");

        assertTrue(submissionCalls.contains("execute"));
        assertTrue(placementCalls.contains("setBlockState"));
        assertFalse(submissionCalls.contains("sendCommand"));
        assertFalse(placementCalls.contains("sendCommand"));
        assertFalse(placementCalls.contains("interactBlock"));
    }

    @Test
    void renderCallbackChecksBothExactStatesBeforeCapturing() throws IOException {
        List<String> calls = methodCallsContaining("onGameRenderCompleted");

        assertTrue(calls.contains("isBeforeCaptureStateExact"));
        assertTrue(calls.contains("isAfterCaptureStateExact"));
        assertTrue(calls.contains("nextStableRenderCount"));
        assertTrue(calls.contains("captureBefore"));
        assertTrue(calls.contains("captureAfter"));
    }

    @Test
    void scenarioOwnsFreshWorldAndDistinctNativeCaptureNames() {
        assertEquals(
                "etherology-e2e-metal-block-registry-world",
                MetalBlockRegistryScenario.WORLD_DIRECTORY_NAME
        );
        assertEquals(
                "Etherology E2E Metal Blocks",
                MetalBlockRegistryScenario.WORLD_DISPLAY_NAME
        );
        assertEquals(331875631436L, MetalBlockRegistryScenario.WORLD_SEED);
        assertEquals(120, MetalBlockRegistryScenario.REQUIRED_COMPLETED_RENDERS);
        assertEquals(
                "metal-block-registry-before.png",
                MetalBlockRegistryScenario.BEFORE_SCREENSHOT_FILE_NAME
        );
        assertEquals(
                "metal-block-registry-after.png",
                MetalBlockRegistryScenario.AFTER_SCREENSHOT_FILE_NAME
        );
    }

    private byte[] packagedScenarioClassBytes() throws IOException {
        String resourceName = MetalBlockRegistryScenario.class.getSimpleName() + ".class";
        try (InputStream input = MetalBlockRegistryScenario.class.getResourceAsStream(resourceName)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private List<String> methodCallsContaining(String containingMethodName) throws IOException {
        List<String> calls = new ArrayList<>();
        ClassReader reader = new ClassReader(packagedScenarioClassBytes());
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!name.contains(containingMethodName)) return null;

                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        calls.add(invokedName);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }
}
