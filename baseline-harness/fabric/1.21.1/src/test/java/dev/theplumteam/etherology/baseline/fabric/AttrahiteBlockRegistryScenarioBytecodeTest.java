package dev.theplumteam.etherology.baseline.fabric;

import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AttrahiteBlockRegistryScenarioBytecodeTest {

    private static final String SCENARIO =
            "dev/theplumteam/etherology/baseline/fabric/AttrahiteBlockRegistryScenario";

    @Test
    void definitionResourcesGalleryAndNineRecipesAreExact() throws Exception {
        assertEquals(
                new ScenarioDefinition(
                        "attrahite-block-registry",
                        "attrahite-block-registry.png",
                        "etherology-original-attrahite-block-registry-world",
                        "Etherology Original 0.1.7 Attrahite Blocks",
                        0x4554484154543031L
                ),
                AttrahiteBlockRegistryScenario.DEFINITION
        );

        assertEquals(
                List.of(
                        "minecraft:texts/splashes.txt",
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
                identifiers("REQUIRED_RESOURCES")
        );

        List<AttrahiteBlockFixture> fixtures = blockFixtures();
        assertEquals(
                List.of(
                        "etherology:attrahite",
                        "etherology:attrahite_bricks",
                        "etherology:attrahite_brick_slab",
                        "etherology:attrahite_brick_stairs"
                ),
                fixtures.stream().map(fixture -> fixture.id().toString()).toList()
        );
        assertEquals(
                List.of(Block.class, Block.class, SlabBlock.class, StairsBlock.class),
                fixtures.stream().map(AttrahiteBlockFixture::blockClass).toList()
        );
        assertEquals(
                List.of(1, 1, 6, 80),
                fixtures.stream().map(AttrahiteBlockFixture::stateCount).toList()
        );
        assertEquals(
                List.of(
                        Map.of(),
                        Map.of(),
                        Map.of("type", "bottom", "waterlogged", "false"),
                        Map.of(
                                "facing", "north",
                                "half", "bottom",
                                "shape", "straight",
                                "waterlogged", "false"
                        )
                ),
                fixtures.stream().map(AttrahiteBlockFixture::defaultProperties).toList()
        );

        assertEquals(
                List.of(
                        "etherology:attrahite_brick=minecraft:smelting"
                                + "->etherology:attrahite_brickx1",
                        "etherology:attrahite_bricks=minecraft:crafting"
                                + "->etherology:attrahite_bricksx1",
                        "etherology:attrahite_brick_slab=minecraft:crafting"
                                + "->etherology:attrahite_brick_slabx6",
                        "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting"
                                + "=minecraft:stonecutting->etherology:attrahite_brick_slabx2",
                        "etherology:attrahite_brick_stairs=minecraft:crafting"
                                + "->etherology:attrahite_brick_stairsx4",
                        "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting"
                                + "=minecraft:stonecutting->etherology:attrahite_brick_stairsx1",
                        "etherology:raw_azel=minecraft:crafting->etherology:raw_azelx1",
                        "etherology:azel_ingot=minecraft:smelting->etherology:azel_ingotx1",
                        "etherology:azel_ingot_from_blasting=minecraft:blasting"
                                + "->etherology:azel_ingotx1"
                ),
                recipeDescriptions()
        );
    }

    @Test
    void scenarioLinksOnlyThroughRegistryAndVanillaFabricApis() throws IOException {
        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("ru/feytox/etherology/"));
        assertTrue(constants.contains("attrahite"));
        assertTrue(constants.contains("enriched_attrahite"));
        assertTrue(constants.contains("raw_azel"));
        assertTrue(constants.contains("azel_ingot_from_blasting"));

        Set<String> registryCalls = invocationNames("inspectRegistry");
        assertTrue(registryCalls.containsAll(Set.of(
                "getClass",
                "getBlock",
                "getDefaultState",
                "getStates",
                "getRawId"
        )));
        Set<String> tagCalls = invocationNames("inspectTags");
        assertTrue(tagCalls.containsAll(Set.of("isIn", "getDefaultStack")));
        assertTrue(invocationNames("inspectRecipes").contains("getRecipeManager"));
        assertFalse(registryCalls.contains("forName"));
        assertFalse(registryCalls.contains("getDeclaredMethod"));
    }

    @Test
    void realLootProbeUsesOneSeedForPlainAndFortuneAndRealEnchantments()
            throws Exception {
        List<Invocation> probeCalls = methodInvocations("runLootProbe");
        assertEquals(
                2L,
                probeCalls.stream().filter(invocation ->
                        "addEnchantment".equals(invocation.name())
                ).count()
        );
        assertEquals(
                4L,
                probeCalls.stream().filter(new Invocation(SCENARIO, "dropStacks")::equals)
                        .count()
        );

        List<Invocation> dropCalls = methodInvocations("dropStacks");
        int setSeed = dropCalls.indexOf(new Invocation(
                "net/minecraft/util/math/random/Random",
                "setSeed"
        ));
        int getDroppedStacks = dropCalls.indexOf(new Invocation(
                "net/minecraft/block/Block",
                "getDroppedStacks"
        ));
        assertTrue(setSeed >= 0 && setSeed < getDroppedStacks);

        Method seedMethod = AttrahiteBlockRegistryScenario.class.getDeclaredMethod(
                "findFortuneDifferenceSeed"
        );
        seedMethod.setAccessible(true);
        long seed = (long) invoke(seedMethod, new AttrahiteBlockRegistryScenario());
        float roll = Random.create(seed).nextFloat();
        assertTrue(roll >= 0.05f);
        assertTrue(roll < 0.20f);
    }

    @Test
    void stableNativeCaptureRechecksReadinessAndRequires1920By1080()
            throws IOException {
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

        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertTrue(constants.contains("one non-empty unedited 1920x1080 framebuffer PNG"));
        assertTrue(constants.contains("attrahite-four-block-gallery"));
    }

    @Test
    void directReportAssertionsHaveOneExactOrder() throws IOException {
        List<String> expected = List.of(
                "fabric_mod_loaded:etherology",
                "attrahite_canonical_resources_exact",
                "attrahite_block_classes_exact",
                "attrahite_block_items_exact",
                "attrahite_default_states_exact",
                "attrahite_state_counts_exact",
                "attrahite_state_network_ids_exact",
                "native_framebuffer_dimensions",
                "completed_world_renders_before_capture",
                "capture_render_ready",
                "capture_camera_exact",
                "native_screenshot_written",
                "integrated_world_joined",
                "server_arena_chunk_loaded",
                "server_player_creative",
                "server_attrahite_default_states_exact",
                "client_attrahite_default_states_exact",
                "server_attrahite_state_network_ids_exact",
                "attrahite_block_tags_exact",
                "attrahite_item_tags_exact",
                "attrahite_loot_shared_seed_roll_exact",
                "loot:etherology:attrahite:silk_touch",
                "loot:etherology:attrahite:no_silk_no_fortune",
                "loot:etherology:attrahite:fortune_iii",
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

    @SuppressWarnings("unchecked")
    private static List<String> identifiers(String fieldName) throws Exception {
        return ((List<Identifier>) staticField(fieldName)).stream()
                .map(Identifier::toString)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<AttrahiteBlockFixture> blockFixtures() throws Exception {
        return (List<AttrahiteBlockFixture>) staticField("BLOCK_FIXTURES");
    }

    @SuppressWarnings("unchecked")
    private static List<String> recipeDescriptions() throws Exception {
        List<AttrahiteRecipeExpectation> recipes =
                (List<AttrahiteRecipeExpectation>) staticField("RECIPE_EXPECTATIONS");
        return recipes.stream()
                .map(expectation -> expectation.id() + "=" + expectation.typeId()
                        + "->" + expectation.resultId() + "x"
                        + expectation.resultCount())
                .toList();
    }

    private static Object staticField(String fieldName) throws Exception {
        Field field = AttrahiteBlockRegistryScenario.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object invoke(Method method, Object target)
            throws InvocationTargetException, IllegalAccessException {
        return method.invoke(target);
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
        String resourceName = AttrahiteBlockRegistryScenario.class.getSimpleName() + ".class";
        try (InputStream input = AttrahiteBlockRegistryScenario.class.getResourceAsStream(
                resourceName
        )) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private record Invocation(String owner, String name) {
    }
}
