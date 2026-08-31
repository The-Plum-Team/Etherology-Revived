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

final class ForestLanternScenarioTest {

    @Test
    void assertionInventoryIsExactAndOrdered() {
        List<String> expected = new ArrayList<>(List.of(
                "fabric_mod_loaded:etherology",
                "registry:block:etherology:forest_lantern",
                "registry:item:etherology:forest_lantern",
                "block_item_mapping",
                "default_state_exact",
                "state_inventory_exact",
                "state_network_ids_exact",
                "client_render_resources",
                "client_render_layer_cutout",
                "client_models_baked",
                "default_luminance",
                "packaged_root_jar:etherology",
                "packaged_root_jar:etherology_e2e_harness",
                "integrated_world_joined",
                "server_arena_chunks_loaded",
                "checker_backings_exact",
                "forced_render_state_matrix_exact",
                "stage_support_validation",
                "unsupported_block_item_rejected",
                "real_block_item_placement:north",
                "real_block_item_placement:east",
                "real_block_item_placement:south",
                "real_block_item_placement:west",
                "forced_world_save",
                "restart_exact_state"
        ));
        for (String phase : List.of(
                "empty",
                "stages",
                "facing-north",
                "facing-east",
                "facing-south",
                "facing-west",
                "reopened"
        )) {
            expected.add("capture_mirror_exact:" + phase);
            expected.add("capture_render_ready:" + phase);
            expected.add("capture_camera_exact:" + phase);
            expected.add("capture_consecutive_stable_renders:" + phase);
            expected.add("capture_framebuffer_dimensions:" + phase);
            expected.add("native_screenshot_written:" + phase);
        }
        expected.add("isolated_save_directory_present");

        assertEquals(expected, ForestLanternScenario.ASSERTION_NAMES);
        assertEquals(68, ForestLanternScenario.ASSERTION_NAMES.size());
    }

    @Test
    void readyResourcesAreExactAndOrdered() {
        assertEquals(
                List.of(
                        "etherology:blockstates/forest_lantern.json",
                        "etherology:models/block/forest_lantern_0.json",
                        "etherology:models/block/forest_lantern_1.json",
                        "etherology:models/block/forest_lantern_2.json",
                        "etherology:models/block/forest_lantern_3.json",
                        "etherology:models/block/forest_lantern.json",
                        "etherology:models/item/forest_lantern.json",
                        "etherology:textures/block/forest_lantern_0.png",
                        "etherology:textures/block/forest_lantern_1.png",
                        "etherology:textures/block/forest_lantern_2.png",
                        "etherology:textures/block/forest_lantern_3.png",
                        "etherology:textures/block/forest_lantern.png",
                        "etherology:textures/item/forest_lantern.png"
                ),
                ForestLanternScenario.READY_RESOURCES.stream()
                        .map(Object::toString)
                        .toList()
        );
    }

    @Test
    void renderedFixtureCoversEveryAgeFacingStateAtOneUniquePosition() {
        List<String> expectedStates = new ArrayList<>();
        List<String> expectedForcedStates = new ArrayList<>();
        List<String> expectedFixtures = new ArrayList<>();
        List<String> facings = List.of("north", "east", "south", "west");
        int[] yCoordinates = {122, 124, 126, 128};
        int[] xCoordinates = {-8, -4, 0, 4, 8};
        for (int facingIndex = 0; facingIndex < facings.size(); facingIndex++) {
            String facing = facings.get(facingIndex);
            int y = yCoordinates[facingIndex];
            for (int age = 0; age <= 4; age++) {
                int x = xCoordinates[age];
                expectedStates.add("age=" + age + ",facing=" + facing);
                if (age < 4) {
                    expectedForcedStates.add("age=" + age + ",facing=" + facing);
                }
                String support = switch (facing) {
                    case "north" -> x + "," + y + ",3";
                    case "east" -> (x - 1) + "," + y + ",2";
                    case "south" -> x + "," + y + ",1";
                    case "west" -> (x + 1) + "," + y + ",2";
                    default -> throw new IllegalStateException(facing);
                };
                expectedFixtures.add(
                        "age=" + age + ",facing=" + facing
                                + ",position=" + x + "," + y + ",2"
                                + ",support=" + support
                );
            }
        }

        assertEquals(expectedStates, ForestLanternScenario.RENDERED_STATE_INVENTORY);
        assertEquals(expectedForcedStates, ForestLanternScenario.FORCED_STATE_INVENTORY);
        assertEquals(expectedFixtures, ForestLanternScenario.stageFixtureDescriptions());
        assertEquals(20, ForestLanternScenario.RENDERED_STATE_INVENTORY.size());
        assertEquals(20L, ForestLanternScenario.RENDERED_STATE_INVENTORY.stream()
                .distinct()
                .count());
        assertEquals(16, ForestLanternScenario.FORCED_STATE_INVENTORY.size());
        assertTrue(ForestLanternScenario.FORCED_STATE_INVENTORY.stream()
                .noneMatch(state -> state.startsWith("age=4,")));
    }

    @Test
    void stableRenderCounterRequiresConsecutiveExactCallbacks() {
        int completedRenders = 0;
        for (int index = 0; index < ForestLanternScenario.REQUIRED_COMPLETED_RENDERS; index++) {
            completedRenders = ForestLanternScenario.nextStableRenderCount(
                    completedRenders,
                    true
            );
        }
        assertEquals(ForestLanternScenario.REQUIRED_COMPLETED_RENDERS, completedRenders);
        assertEquals(
                0,
                ForestLanternScenario.nextStableRenderCount(completedRenders, false)
        );
        assertEquals(1, ForestLanternScenario.nextStableRenderCount(0, true));
    }

    @Test
    void packagedScenarioUsesVanillaRegistryAndPropertyApisOnly() throws IOException {
        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);

        assertFalse(constants.contains("ru/feytox/etherology/"));
        assertFalse(constants.contains("getCommandManager"));
        assertFalse(constants.contains("executeCommand"));
        assertFalse(constants.contains("sendCommand"));
        assertFalse(constants.contains("clickCreativeStack"));
        assertTrue(constants.contains("forest_lantern"));
        assertTrue(constants.contains("peach_log"));
        assertTrue(constants.contains("stripped_peach_log"));
        assertTrue(constants.contains("polished_andesite"));
        assertTrue(constants.contains("iron_bars"));
        assertTrue(constants.contains("age"));
        assertTrue(constants.contains("facing"));
        assertTrue(constants.contains(
                "2e73ec0f61ca32b3e5331c2410362ae2df599d41a5d1499cf2fc84de5dcf74e3"
        ));
        assertTrue(constants.contains("getInputStream"));
        assertTrue(constants.contains("SHA-256"));
    }

    @Test
    void scenarioUsesRealBlockItemAndFullRestartPath() throws IOException {
        List<String> placementCalls = methodCallsContaining("attemptPlacement");
        List<String> disconnectCalls = methodCallsContaining("tickDisconnecting");
        List<String> restartCalls = methodCallsContaining("restartWorld");

        assertTrue(placementCalls.contains("useOnBlock"));
        assertTrue(placementCalls.contains("isAccepted"));
        assertFalse(placementCalls.contains("setBlockState"));
        assertTrue(disconnectCalls.contains("disconnect"));
        assertTrue(restartCalls.contains("start"));
    }

    @Test
    void scenarioOwnsFreshWorldAndSevenDistinctNativeCaptures() throws IOException {
        assertEquals(
                "etherology-e2e-forest-lantern-world",
                ForestLanternScenario.WORLD_DIRECTORY_NAME
        );
        assertEquals(
                "Etherology E2E Forest Lantern",
                ForestLanternScenario.WORLD_DISPLAY_NAME
        );
        assertEquals(77306496635732L, ForestLanternScenario.WORLD_SEED);
        assertEquals(120, ForestLanternScenario.REQUIRED_COMPLETED_RENDERS);
        assertEquals(
                List.of(
                        "forest-lantern-empty.png",
                        "forest-lantern-stages.png",
                        "forest-lantern-facing-north.png",
                        "forest-lantern-facing-east.png",
                        "forest-lantern-facing-south.png",
                        "forest-lantern-facing-west.png",
                        "forest-lantern-reopened.png"
                ),
                ForestLanternScenario.SCREENSHOT_FILE_NAMES
        );
        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertTrue(constants.contains("saveAll"));
        assertTrue(constants.contains("scheduleStop"));
    }

    private byte[] classBytes() throws IOException {
        String resourceName = ForestLanternScenario.class.getSimpleName() + ".class";
        try (InputStream input = ForestLanternScenario.class.getResourceAsStream(resourceName)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private List<String> methodCallsContaining(String containingMethodName) throws IOException {
        List<String> calls = new ArrayList<>();
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
