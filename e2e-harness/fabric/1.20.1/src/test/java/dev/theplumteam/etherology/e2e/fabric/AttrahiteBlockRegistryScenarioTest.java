package dev.theplumteam.etherology.e2e.fabric;

import net.minecraft.util.math.Direction;
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

final class AttrahiteBlockRegistryScenarioTest {

    private static final List<String> BLOCK_IDS = List.of(
            "etherology:attrahite",
            "etherology:attrahite_bricks",
            "etherology:attrahite_brick_slab",
            "etherology:attrahite_brick_stairs"
    );

    @Test
    void assertionInventoryIsExactAndOrdered() {
        List<String> expected = new ArrayList<>();
        expected.add("fabric_mod_loaded:etherology");
        for (String id : BLOCK_IDS) {
            expected.add("registry:block:" + id);
            expected.add("registry:item:" + id);
            expected.add("runtime:block_class:" + id);
            expected.add("runtime:block_item_class:" + id);
            expected.add("block_item_mapping:" + id);
            expected.add("default_state:" + id);
            expected.add("state_count:" + id);
            expected.add("default_state_network_id:" + id);
            expected.add("tag:mineable/pickaxe:" + id);
            expected.add("tag:needs_stone_tool:" + id);
            expected.add("tag:block/slabs:" + id);
            expected.add("tag:item/slabs:" + id);
            expected.add("tag:block/stairs:" + id);
            expected.add("tag:item/stairs:" + id);
        }
        expected.addAll(List.of(
                "client_render_resources",
                "packaged_root_jar:etherology",
                "packaged_root_jar:etherology_e2e_harness",
                "integrated_world_joined",
                "server_arena_chunks_loaded",
                "loot_tables_exact",
                "standard_block_drops_exact",
                "raw_plain_drops_deterministic",
                "raw_silk_touch_drop_exact",
                "raw_fortune_drops_deterministic",
                "recipes_exact_and_craftable",
                "advancements_exact",
                "direct_block_item_placements_exact",
                "initial_server_fixture_exact",
                "forced_world_save",
                "restart_fixture_persistence_exact",
                "restart_loaded_data_exact"
        ));
        for (String phase : List.of("initial", "reopened")) {
            expected.add("capture_mirror_exact:" + phase);
            expected.add("capture_render_ready:" + phase);
            expected.add("capture_lighting_ready:" + phase);
            expected.add("capture_camera_exact:" + phase);
            expected.add("capture_consecutive_stable_renders:" + phase);
            expected.add("capture_framebuffer_dimensions:" + phase);
            expected.add("native_screenshot_written:" + phase);
        }
        expected.add("isolated_save_directory_present");

        assertEquals(expected, AttrahiteBlockRegistryScenario.ASSERTION_NAMES);
        assertEquals(89, AttrahiteBlockRegistryScenario.ASSERTION_NAMES.size());
    }

    @Test
    void readyResourceInventoryIsExactAndOrdered() {
        assertEquals(
                List.of(
                        "etherology:blockstates/attrahite.json",
                        "etherology:blockstates/attrahite_bricks.json",
                        "etherology:blockstates/attrahite_brick_slab.json",
                        "etherology:blockstates/attrahite_brick_stairs.json",
                        "etherology:models/block/attrahite.json",
                        "etherology:models/block/attrahite_bricks.json",
                        "etherology:models/block/attrahite_brick_slab.json",
                        "etherology:models/block/attrahite_brick_slab_top.json",
                        "etherology:models/block/attrahite_brick_stairs.json",
                        "etherology:models/block/attrahite_brick_stairs_inner.json",
                        "etherology:models/block/attrahite_brick_stairs_outer.json",
                        "etherology:models/item/attrahite.json",
                        "etherology:models/item/attrahite_bricks.json",
                        "etherology:models/item/attrahite_brick_slab.json",
                        "etherology:models/item/attrahite_brick_stairs.json",
                        "etherology:textures/block/attrahite.png",
                        "etherology:textures/block/attrahite_bricks.png"
                ),
                AttrahiteBlockRegistryScenario.READY_RESOURCES.stream()
                        .map(Object::toString)
                        .toList()
        );
    }

    @Test
    void galleryAndCaptureInventoryAreStable() {
        assertEquals(
                List.of(
                        "etherology:attrahite@-3,122,1",
                        "etherology:attrahite_bricks@-1,122,1",
                        "etherology:attrahite_brick_slab@1,122,1",
                        "etherology:attrahite_brick_stairs@3,122,1"
                ),
                AttrahiteBlockRegistryScenario.fixtureDescriptions()
        );
        assertEquals(
                List.of(
                        "attrahite-block-registry-initial.png",
                        "attrahite-block-registry-reopened.png"
                ),
                AttrahiteBlockRegistryScenario.SCREENSHOT_FILE_NAMES
        );
        assertEquals(120, AttrahiteBlockRegistryScenario.REQUIRED_COMPLETED_RENDERS);
        assertEquals(
                "etherology-e2e-attrahite-block-registry-world",
                AttrahiteBlockRegistryScenario.WORLD_DIRECTORY_NAME
        );
        assertEquals(
                "Etherology E2E Attrahite Blocks",
                AttrahiteBlockRegistryScenario.WORLD_DISPLAY_NAME
        );
        assertEquals(
                0x4154545241484954L,
                AttrahiteBlockRegistryScenario.WORLD_SEED
        );
        assertEquals(
                Direction.NORTH,
                Direction.fromRotation(AttrahiteBlockRegistryScenario.PLACEMENT_YAW)
        );
    }

    @Test
    void loadedDataInventoryIsExactAndOrdered() {
        assertEquals(
                List.of(
                        "etherology:blocks/attrahite",
                        "etherology:blocks/attrahite_brick_slab",
                        "etherology:blocks/attrahite_brick_stairs",
                        "etherology:blocks/attrahite_bricks"
                ),
                AttrahiteBlockRegistryScenario.EXPECTED_LOOT_TABLE_IDS
        );
        assertEquals(
                List.of(
                        "etherology:attrahite_brick",
                        "etherology:attrahite_brick_slab",
                        "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting",
                        "etherology:attrahite_brick_stairs",
                        "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
                        "etherology:attrahite_bricks",
                        "etherology:azel_ingot",
                        "etherology:azel_ingot_from_blasting",
                        "etherology:raw_azel"
                ),
                AttrahiteBlockRegistryScenario.EXPECTED_RECIPE_IDS
        );
        assertEquals(
                List.of(
                        "etherology:recipes/building_blocks/attrahite_brick_slab",
                        "etherology:recipes/building_blocks/"
                                + "attrahite_brick_slab_from_attrahite_bricks_stonecutting",
                        "etherology:recipes/building_blocks/attrahite_brick_stairs",
                        "etherology:recipes/building_blocks/"
                                + "attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
                        "etherology:recipes/building_blocks/attrahite_bricks",
                        "etherology:recipes/misc/attrahite_brick",
                        "etherology:recipes/misc/azel_ingot",
                        "etherology:recipes/misc/azel_ingot_from_blasting",
                        "etherology:recipes/misc/raw_azel"
                ),
                AttrahiteBlockRegistryScenario.EXPECTED_ADVANCEMENT_IDS
        );
    }

    @Test
    void stableRenderCounterRequiresConsecutiveExactCallbacks() {
        int completedRenders = 0;
        for (int index = 0;
                index < AttrahiteBlockRegistryScenario.REQUIRED_COMPLETED_RENDERS;
                index++) {
            completedRenders = AttrahiteBlockRegistryScenario.nextStableRenderCount(
                    completedRenders,
                    true
            );
        }
        assertEquals(
                AttrahiteBlockRegistryScenario.REQUIRED_COMPLETED_RENDERS,
                completedRenders
        );
        assertEquals(
                0,
                AttrahiteBlockRegistryScenario.nextStableRenderCount(completedRenders, false)
        );
    }

    @Test
    void lightingReadinessCoversOpenSkyAndRequiresMaximumSkylight() {
        assertEquals(
                List.of(
                        "0,121,-8",
                        "0,121,0",
                        "-3,123,1",
                        "-1,123,1",
                        "1,123,1",
                        "3,123,1"
                ),
                AttrahiteBlockRegistryScenario.lightingSampleDescriptions()
        );
        assertEquals(
                List.of(
                        "0,121,-8",
                        "0,121,0",
                        "-3,123,1",
                        "-1,123,1",
                        "1,123,1",
                        "3,123,1"
                ),
                AttrahiteBlockRegistryScenario.blockLightingSampleDescriptions()
        );
        assertEquals(
                List.of(15, 15, 15, 15, 15, 15),
                AttrahiteBlockRegistryScenario.EXPECTED_SKY_LIGHT_LEVELS
        );
        assertEquals(
                List.of(14, 14, 6, 4, 10, 8),
                AttrahiteBlockRegistryScenario.EXPECTED_BLOCK_LIGHT_LEVELS
        );
        assertTrue(AttrahiteBlockRegistryScenario.isExpectedSkyLight(0, 15));
        assertFalse(AttrahiteBlockRegistryScenario.isExpectedSkyLight(0, 14));
        assertTrue(AttrahiteBlockRegistryScenario.isExpectedBlockLight(2, 6));
        assertFalse(AttrahiteBlockRegistryScenario.isExpectedBlockLight(2, 14));
    }

    @Test
    void lightingReadinessRequiresConsecutiveClientTicks() {
        int readyClientTicks = 0;
        for (int index = 0;
                index < AttrahiteBlockRegistryScenario.REQUIRED_LIGHTING_READY_CLIENT_TICKS;
                index++) {
            readyClientTicks = AttrahiteBlockRegistryScenario
                    .nextLightingReadyClientTickCount(readyClientTicks, true);
        }
        assertEquals(
                AttrahiteBlockRegistryScenario.REQUIRED_LIGHTING_READY_CLIENT_TICKS,
                readyClientTicks
        );
        assertEquals(
                readyClientTicks,
                AttrahiteBlockRegistryScenario.nextLightingReadyClientTickCount(
                        readyClientTicks,
                        true
                )
        );
        assertEquals(
                0,
                AttrahiteBlockRegistryScenario.nextLightingReadyClientTickCount(
                        readyClientTicks,
                        false
                )
        );
    }

    @Test
    void lightingReadinessRequiresConsecutiveServerTicks() {
        int readyServerTicks = 0;
        for (int index = 0;
                index < AttrahiteBlockRegistryScenario.REQUIRED_LIGHTING_READY_SERVER_TICKS;
                index++) {
            readyServerTicks = AttrahiteBlockRegistryScenario
                    .nextLightingReadyServerTickCount(readyServerTicks, true);
        }
        assertEquals(
                AttrahiteBlockRegistryScenario.REQUIRED_LIGHTING_READY_SERVER_TICKS,
                readyServerTicks
        );
        assertEquals(
                0,
                AttrahiteBlockRegistryScenario.nextLightingReadyServerTickCount(
                        readyServerTicks,
                        false
                )
        );
    }

    @Test
    void pendingGlobalLightingWorkDoesNotRejectExactStableSamples() {
        AttrahiteBlockRegistryScenario.LightSnapshot server =
                AttrahiteBlockRegistryScenario.LightSnapshot.expected(true);
        AttrahiteBlockRegistryScenario.LightSnapshot client =
                AttrahiteBlockRegistryScenario.LightSnapshot.expected(true);
        AttrahiteBlockRegistryScenario.LightingEvidence evidence =
                new AttrahiteBlockRegistryScenario.LightingEvidence(
                        AttrahiteBlockRegistryScenario.REQUIRED_LIGHTING_READY_SERVER_TICKS,
                        AttrahiteBlockRegistryScenario.REQUIRED_LIGHTING_READY_CLIENT_TICKS,
                        server,
                        client
                );

        assertTrue(evidence.exact());
        assertEquals(
                AttrahiteBlockRegistryScenario.LightingEvidence.expectedDescription(),
                evidence.assertionActual()
        );
    }

    @Test
    void clientSampleMismatchResetsTheConsecutiveCounter() {
        AttrahiteBlockRegistryScenario.LightSnapshot server =
                AttrahiteBlockRegistryScenario.LightSnapshot.expected(false);
        List<Integer> mismatchedBlock = new ArrayList<>(
                AttrahiteBlockRegistryScenario.EXPECTED_BLOCK_LIGHT_LEVELS
        );
        mismatchedBlock.set(5, 7);
        AttrahiteBlockRegistryScenario.LightSnapshot client =
                new AttrahiteBlockRegistryScenario.LightSnapshot(
                        true,
                        false,
                        AttrahiteBlockRegistryScenario.EXPECTED_SKY_LIGHT_LEVELS,
                        mismatchedBlock
                );

        assertFalse(client.sameSamples(server));
        assertEquals(
                0,
                AttrahiteBlockRegistryScenario.nextLightingReadyClientTickCount(19, false)
        );
    }

    @Test
    void timeoutDiagnosticIncludesCountersPendingFlagsAndLatestSamples() {
        AttrahiteBlockRegistryScenario.LightingEvidence evidence =
                new AttrahiteBlockRegistryScenario.LightingEvidence(
                        7,
                        3,
                        AttrahiteBlockRegistryScenario.LightSnapshot.expected(true),
                        AttrahiteBlockRegistryScenario.LightSnapshot.missing()
                );

        assertFalse(evidence.exact());
        assertTrue(evidence.assertionActual().contains("stableServerTicks=7"));
        assertTrue(evidence.assertionActual().contains("stableClientTicks=3"));
        assertTrue(evidence.assertionActual().contains("server=observed=true;pending=true"));
        assertTrue(evidence.assertionActual().contains("client=observed=false;pending=false"));
        assertTrue(evidence.assertionActual().contains("3,123,1=8"));
    }

    @Test
    void packagedScenarioUsesNativeApisAndFullRestartPath() throws IOException {
        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        String dataConstants = new String(
                nestedClassBytes("DataProbe"),
                StandardCharsets.ISO_8859_1
        );
        String placementConstants = new String(
                nestedClassBytes("PlacementEvidence"),
                StandardCharsets.ISO_8859_1
        );
        List<String> placementCalls = methodCallsContaining("placeAllBlockItems");
        List<String> setupCalls = methodCallsContaining("setupFixture");
        List<String> saveCalls = methodCallsContaining("submitSave");
        List<String> disconnectCalls = methodCallsContaining("tickDisconnecting");
        List<String> restartCalls = methodCallsContaining("restartWorld");
        List<String> lightingCalls = methodCallsContaining("captureLightSnapshot");
        List<String> clientLightingCalls = methodCallsContaining(
                "captureClientLightSnapshot"
        );
        List<String> serverLightingCalls = methodCallsContaining(
                "requestServerLightingChecks"
        );
        List<String> serverTickCalls = methodCallsContaining("onEndServerTick");

        assertFalse(constants.contains("ru/feytox/etherology/"));
        assertFalse(constants.contains("executeCommand"));
        assertTrue(constants.contains("attrahite_brick_slab"));
        assertTrue(constants.contains("azel_ingot_from_blasting"));
        assertTrue(constants.contains("enriched_attrahite"));
        assertTrue(placementCalls.contains("useOnBlock"));
        assertTrue(placementCalls.contains("isAccepted"));
        assertFalse(placementCalls.contains("setBlockState"));
        assertTrue(placementConstants.contains("CONSUME"));
        assertTrue(setupCalls.contains("requestServerLightingChecks"));
        assertTrue(saveCalls.contains("saveAll"));
        assertTrue(disconnectCalls.contains("disconnect"));
        assertTrue(restartCalls.contains("start"));
        assertTrue(lightingCalls.contains("getLightLevel"));
        assertFalse(lightingCalls.contains("doLightUpdates"));
        assertTrue(clientLightingCalls.contains("hasUpdates"));
        assertTrue(clientLightingCalls.contains("captureLightSnapshot"));
        assertFalse(clientLightingCalls.contains("doLightUpdates"));
        assertTrue(serverLightingCalls.contains("checkBlock"));
        assertFalse(serverLightingCalls.contains("doLightUpdates"));
        assertTrue(serverTickCalls.contains("getOverworld"));
        assertTrue(serverTickCalls.contains("hasUpdates"));
        assertTrue(serverTickCalls.contains("captureLightSnapshot"));
        assertFalse(serverTickCalls.contains("doLightUpdates"));
        assertTrue(constants.contains("scheduleStop"));
        assertTrue(dataConstants.contains("generateLoot"));
        assertTrue(dataConstants.contains("getAdvancements"));
    }

    private byte[] classBytes() throws IOException {
        String resourceName = AttrahiteBlockRegistryScenario.class.getSimpleName() + ".class";
        try (InputStream input = AttrahiteBlockRegistryScenario.class
                .getResourceAsStream(resourceName)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private byte[] nestedClassBytes(String simpleName) throws IOException {
        String resourceName = AttrahiteBlockRegistryScenario.class.getSimpleName()
                + "$" + simpleName + ".class";
        try (InputStream input = AttrahiteBlockRegistryScenario.class
                .getResourceAsStream(resourceName)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private List<String> methodCallsContaining(String containingName) throws IOException {
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
                if (!name.contains(containingName)) return null;

                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface
                    ) {
                        calls.add(name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }
}
