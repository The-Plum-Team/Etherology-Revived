package dev.theplumteam.etherology.baseline.fabric;

import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SlitheriteBlockRegistryScenarioBytecodeTest {

    private static final String SCENARIO =
            "dev/theplumteam/etherology/baseline/fabric/"
                    + "SlitheriteBlockRegistryScenario";
    private static final String REGISTRY_PROBE = SCENARIO + "$RegistryProbe";
    private static final String TAG_SNAPSHOT = SCENARIO + "$TagSnapshot";

    @Test
    void definitionGalleryClassesStatesAndNetworkCardinalityAreExact()
            throws Exception {
        assertEquals(
                new ScenarioDefinition(
                        "slitherite-block-registry",
                        "slitherite-block-registry-initial.png",
                        "etherology-original-slitherite-block-registry-world",
                        "Etherology Original 0.1.7 Slitherite Blocks",
                        0x455448534c495430L
                ),
                SlitheriteBlockRegistryScenario.DEFINITION
        );
        List<?> fixtures = staticList("FIXTURES");
        assertEquals(17, fixtures.size());
        assertEquals(
                List.of(
                        "etherology:slitherite",
                        "etherology:slitherite_stairs",
                        "etherology:slitherite_slab",
                        "etherology:slitherite_wall",
                        "etherology:polished_slitherite",
                        "etherology:polished_slitherite_stairs",
                        "etherology:polished_slitherite_slab",
                        "etherology:polished_slitherite_wall",
                        "etherology:polished_slitherite_button",
                        "etherology:polished_slitherite_pressure_plate",
                        "etherology:polished_slitherite_bricks",
                        "etherology:polished_slitherite_brick_stairs",
                        "etherology:polished_slitherite_brick_slab",
                        "etherology:polished_slitherite_brick_wall",
                        "etherology:chiseled_polished_slitherite",
                        "etherology:chiseled_polished_slitherite_bricks",
                        "etherology:cracked_polished_slitherite_bricks"
                ),
                fixtureValues(fixtures, "id")
        );
        assertEquals(
                List.of(
                        Block.class,
                        StairsBlock.class,
                        SlabBlock.class,
                        WallBlock.class,
                        Block.class,
                        StairsBlock.class,
                        SlabBlock.class,
                        WallBlock.class,
                        ButtonBlock.class,
                        PressurePlateBlock.class,
                        Block.class,
                        StairsBlock.class,
                        SlabBlock.class,
                        WallBlock.class,
                        Block.class,
                        Block.class,
                        Block.class
                ),
                fixtureValues(fixtures, "blockClass")
        );
        List<Object> stateCounts = fixtureValues(fixtures, "stateCount");
        assertEquals(
                List.of(1, 80, 6, 324, 1, 80, 6, 324, 24, 2,
                        1, 80, 6, 324, 1, 1, 1),
                stateCounts
        );
        assertEquals(
                SlitheriteBlockRegistryScenario.EXPECTED_AGGREGATE_STATE_COUNT,
                stateCounts.stream().mapToInt(value -> (int) value).sum()
        );
    }

    @Test
    void resourcesRecipesAdvancementsAndAssertionsHaveExactInventories()
            throws Exception {
        List<String> resources = SlitheriteBlockRegistryScenario.REQUIRED_RESOURCES
                .stream()
                .map(Identifier::toString)
                .toList();
        assertEquals(80, resources.size());
        assertEquals(resources.size(), new HashSet<>(resources).size());
        assertEquals("minecraft:texts/splashes.txt", resources.getFirst());
        assertEquals(
                17L,
                resources.stream().filter(value -> value.contains(":blockstates/")).count()
        );
        assertEquals(
                38L,
                resources.stream().filter(value -> value.contains(":models/block/")).count()
        );
        assertEquals(
                17L,
                resources.stream().filter(value -> value.contains(":models/item/")).count()
        );
        assertEquals(
                7L,
                resources.stream().filter(value -> value.contains(":textures/block/")).count()
        );

        assertEquals(29, staticList("OWNED_RECIPES").size());
        assertEquals(29, staticList("OWNED_ADVANCEMENTS").size());
        assertEquals(5, staticList("RELATED_RECIPES").size());
        assertEquals(185, SlitheriteBlockRegistryScenario.ASSERTION_NAMES.size());
        assertEquals(
                List.of(
                        "slitherite-block-registry-initial.png",
                        "slitherite-block-registry-reopened.png"
                ),
                SlitheriteBlockRegistryScenario.SCREENSHOT_FILE_NAMES
        );
        assertTrue(SlitheriteBlockRegistryScenario.ASSERTION_NAMES.containsAll(List.of(
                "slitherite_loot_tables_exact",
                "slitherite_double_slab_drops_x1_exact",
                "slitherite_owned_recipes_exact",
                "slitherite_owned_advancements_exact",
                "slitherite_related_recipes_recorded_not_owned",
                "slitherite_button_pulse_reset_exact",
                "slitherite_pressure_plate_entities_exact",
                "client_arena_chunks_loaded_before_setup",
                "client_arena_light_payloads_applied_before_setup",
                "capture_lighting_ready:initial",
                "capture_lighting_ready:reopened",
                "restart_fixture_persistence_exact"
        )));
    }

    @Test
    void nativePlacementBehaviorRestartLightingAndCaptureCallsArePresent()
            throws IOException {
        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("ru/feytox/etherology/"));
        assertTrue(constants.contains("related_recipes_recorded_not_owned"));
        assertTrue(constants.contains("net/minecraft/entity/ItemEntity"));
        assertTrue(constants.contains("net/minecraft/entity/passive/PigEntity"));
        assertFalse(constants.contains(
                "net/minecraft/entity/decoration/ArmorStandEntity"
        ));

        assertTrue(invocationNames("placeAllBlockItems").contains("useOnBlock"));
        assertTrue(invocationNames("startNativeBehaviorProbe").containsAll(Set.of(
                "interactBlock",
                "isQueued",
                "spawnEntity"
        )));
        assertTrue(invocationNames("advanceBehaviorProbe").containsAll(Set.of(
                "shouldTickEntity",
                "serverEntityAdvanced",
                "deadlineReached",
                "spawnLivingProbe"
        )));
        assertTrue(invocationNamesWithPrefix("lambda$submitSave$").contains("saveAll"));
        assertTrue(invocationNames("restartWorld").contains("start"));
        assertTrue(invocationNames("localLightingReady").containsAll(Set.of(
                "capture",
                "exact"
        )));
        assertFalse(invocationNames("localLightingReady").contains("hasUpdates"));
        assertTrue(invocationNames("captureLightingEvidence").contains("hasUpdates"));
        assertTrue(invocationNames("requestServerLightingSample").contains("execute"));
        assertFalse(invocationNames("requestServerLightingSample").contains("checkBlock"));
        assertTrue(invocationNames("captureCurrentPhase").contains("saveScreenshot"));
        assertTrue(fieldAccessNames("onEndClientTick").contains("serverFailure"));
        assertTrue(invocationNames("onEndClientTick").contains("fail"));
        assertTrue(fieldAccessNames("tickWaitingForBehavior").containsAll(Set.of(
                "entityTickFailure",
                "serverFailure"
        )));
        assertTrue(fieldAccessNames("isCaptureStateExact").containsAll(Set.of(
                "serverFailure",
                "serverLightingInspectionInFlight"
        )));
    }

    @Test
    void pressurePlateProbeUsesTickableChunkAndBoundedNativeEntityTicks()
            throws IOException {
        assertEquals(100L, staticLong("MAXIMUM_ENTITY_TICK_GATE_TICKS"));
        assertEquals(20L, staticLong("MAXIMUM_PROBE_ENTITY_TICK_TICKS"));
        assertEquals(20L, staticLong("MAXIMUM_LIVING_ACTIVATION_TICKS"));
        assertEquals(25L, staticLong("PRESSURE_PLATE_RESET_TICKS"));
        assertEquals(
                List.of(
                        "WAITING_FOR_ENTITY_TICKING",
                        "WAITING_FOR_ITEM_TICK",
                        "WAITING_FOR_LIVING_ACTIVATION",
                        "WAITING_FOR_RESET"
                ),
                behaviorPhaseNames()
        );

        Set<String> advanceCalls = invocationNames("advanceBehaviorProbe");
        assertTrue(advanceCalls.containsAll(Set.of(
                "shouldTickEntity",
                "serverEntityAdvanced",
                "deadlineReached",
                "startNativeBehaviorProbe",
                "spawnLivingProbe",
                "beginBehaviorReset",
                "recordEntityTickFailure",
                "discard"
        )));
        List<String> advanceSequence = invocationSequence("advanceBehaviorProbe");
        assertTrue(
                advanceSequence.indexOf("shouldTickEntity")
                        < advanceSequence.indexOf("startNativeBehaviorProbe")
        );
        assertTrue(invocationNames("serverEntityAdvanced").containsAll(Set.of(
                "getEntityById",
                "getId",
                "entityTickAdvanced"
        )));
        assertTrue(fieldAccessKeys("serverEntityAdvanced").contains(
                "net/minecraft/entity/Entity.age"
        ));
        assertTrue(SlitheriteBlockRegistryScenario.entityTickAdvanced(true, 0, 1));
        assertFalse(SlitheriteBlockRegistryScenario.entityTickAdvanced(false, 0, 1));
        assertFalse(SlitheriteBlockRegistryScenario.entityTickAdvanced(true, 1, 1));
        assertFalse(SlitheriteBlockRegistryScenario.deadlineReached(19L, 20L));
        assertTrue(SlitheriteBlockRegistryScenario.deadlineReached(20L, 20L));
    }

    @Test
    void pressurePlateEntitiesEnterThroughGravityWithPigAiEnabledAndNoDirectTriggers()
            throws IOException {
        Set<String> itemCalls = invocationNames("startNativeBehaviorProbe");
        assertTrue(itemCalls.containsAll(Set.of(
                "setVelocity",
                "setOnGround",
                "setNoGravity",
                "spawnEntity"
        )));
        assertTrue(numericConstants("startNativeBehaviorProbe").contains(0.5D));
        assertTrue(numericConstants("startNativeBehaviorProbe").contains(-0.3D));
        List<String> itemSequence = invocationSequence("startNativeBehaviorProbe");
        assertTrue(itemSequence.indexOf("setVelocity")
                < itemSequence.indexOf("setOnGround"));
        assertTrue(itemSequence.indexOf("setOnGround")
                < itemSequence.indexOf("setNoGravity"));
        assertTrue(itemSequence.indexOf("setNoGravity")
                < itemSequence.lastIndexOf("spawnEntity"));

        Set<String> livingCalls = invocationNames("spawnLivingProbe");
        assertTrue(livingCalls.containsAll(Set.of(
                "create",
                "setInvulnerable",
                "refreshPositionAndAngles",
                "setVelocity",
                "setOnGround",
                "setNoGravity",
                "spawnEntity",
                "position"
        )));
        assertFalse(livingCalls.contains("setAiDisabled"));
        assertFalse(livingCalls.contains("setPosition"));
        assertTrue(fieldAccessKeys("spawnLivingProbe").contains(
                "net/minecraft/entity/EntityType.PIG"
        ));
        assertTrue(numericConstants("spawnLivingProbe").contains(0.5D));
        assertTrue(numericConstants("spawnLivingProbe").contains(-0.3D));
        List<String> livingSequence = invocationSequence("spawnLivingProbe");
        assertTrue(livingSequence.indexOf("refreshPositionAndAngles")
                < livingSequence.indexOf("setVelocity"));
        assertTrue(livingSequence.indexOf("setVelocity")
                < livingSequence.indexOf("setOnGround"));
        assertTrue(livingSequence.indexOf("setOnGround")
                < livingSequence.indexOf("setNoGravity"));
        assertTrue(livingSequence.indexOf("setNoGravity")
                < livingSequence.indexOf("spawnEntity"));

        Set<String> behaviorCalls = new HashSet<>();
        for (String methodName : List.of(
                "initializeBehaviorProbe",
                "startNativeBehaviorProbe",
                "advanceBehaviorProbe",
                "spawnLivingProbe",
                "beginBehaviorReset",
                "serverEntityAdvanced"
        )) {
            behaviorCalls.addAll(invocationNames(methodName));
        }
        assertTrue(Set.of(
                "setAiDisabled",
                "onEntityCollision",
                "updatePlateState",
                "scheduledTick",
                "tickEntity",
                "setBlockState",
                "with"
        ).stream().noneMatch(behaviorCalls::contains));
        assertTrue(fieldWriteKeys(List.of(
                "initializeBehaviorProbe",
                "startNativeBehaviorProbe",
                "advanceBehaviorProbe",
                "spawnLivingProbe",
                "beginBehaviorReset"
        )).stream().noneMatch(key -> key.endsWith(".POWERED")));
    }

    @Test
    void titlePreflightExcludesOnlyUnloadedTagsAndLaterStagesRequireFullExact()
            throws IOException {
        String registryOnly = REGISTRY_PROBE + ".blockItemRegistryExact";
        String fullRegistry = REGISTRY_PROBE + ".exact";
        String tagExact = TAG_SNAPSHOT + ".exact";

        Set<String> titleCalls = invocationKeys(SCENARIO, "tickWaitingForTitle");
        assertTrue(titleCalls.contains(registryOnly));
        assertFalse(titleCalls.contains(fullRegistry));

        Set<String> setupCalls = invocationKeys(
                SCENARIO,
                "tickWaitingForServerSetup"
        );
        assertTrue(setupCalls.contains(fullRegistry));
        assertFalse(setupCalls.contains(registryOnly));

        Set<String> reopenCalls = invocationKeys(
                SCENARIO,
                "tickWaitingForRestartInspection"
        );
        assertTrue(reopenCalls.contains(fullRegistry));
        assertFalse(reopenCalls.contains(registryOnly));

        Set<String> fullRegistryCalls = invocationKeys(REGISTRY_PROBE, "exact");
        assertTrue(fullRegistryCalls.contains(registryOnly));
        assertTrue(fullRegistryCalls.contains(tagExact));
        assertFalse(
                invocationKeys(REGISTRY_PROBE, "blockItemRegistryExact")
                        .contains(tagExact)
        );
    }

    @Test
    void lightingReadinessResetsAndRequiresExactLevels() {
        assertEquals(
                0,
                SlitheriteBlockRegistryScenario.nextLightingReadyClientTickCount(
                        19,
                        false
                )
        );
        assertEquals(
                20,
                SlitheriteBlockRegistryScenario.nextLightingReadyClientTickCount(
                        19,
                        true
                )
        );
        assertEquals(
                20,
                SlitheriteBlockRegistryScenario.nextLightingReadyClientTickCount(
                        20,
                        true
                )
        );
        assertTrue(
                SlitheriteBlockRegistryScenario.requiresAnotherServerLightingSample(19)
        );
        assertFalse(
                SlitheriteBlockRegistryScenario.requiresAnotherServerLightingSample(20)
        );
        assertTrue(SlitheriteBlockRegistryScenario.isExpectedSkyLight(15));
        assertFalse(SlitheriteBlockRegistryScenario.isExpectedSkyLight(14));
        assertTrue(SlitheriteBlockRegistryScenario.isExpectedBlockLight(14));
        assertFalse(SlitheriteBlockRegistryScenario.isExpectedBlockLight(0));
        assertTrue(SlitheriteBlockRegistryScenario.localLightingSamplesExact(
                java.util.Collections.nCopies(18, 15),
                java.util.Collections.nCopies(2, 14),
                java.util.Collections.nCopies(18, 15),
                java.util.Collections.nCopies(2, 14)
        ));
        assertFalse(SlitheriteBlockRegistryScenario.localLightingSamplesExact(
                java.util.Collections.nCopies(18, 15),
                java.util.Collections.nCopies(2, 14),
                java.util.Collections.nCopies(18, 0),
                java.util.Collections.nCopies(2, 14)
        ));
    }

    @Test
    void preSetupLightGateRequiresExactClientPayloadReadinessBeforeFixtureSetup()
            throws IOException {
        assertEquals(20, staticInt("REQUIRED_PRE_SETUP_LIGHT_READY_CLIENT_TICKS"));
        assertEquals(
                List.of(
                        "-2,-2",
                        "-2,-1",
                        "-2,0",
                        "-1,-2",
                        "-1,-1",
                        "-1,0",
                        "0,-2",
                        "0,-1",
                        "0,0",
                        "1,-2",
                        "1,-1",
                        "1,0"
                ),
                SlitheriteBlockRegistryScenario.ARENA_CHUNK_DESCRIPTIONS
        );
        assertEquals(
                java.util.Collections.nCopies(18, 15),
                SlitheriteBlockRegistryScenario.EXPECTED_PRE_SETUP_SKY_LIGHT_LEVELS
        );
        assertEquals(
                0,
                SlitheriteBlockRegistryScenario.nextPreSetupLightReadyClientTickCount(
                        19,
                        false
                )
        );
        assertEquals(
                20,
                SlitheriteBlockRegistryScenario.nextPreSetupLightReadyClientTickCount(
                        19,
                        true
                )
        );
        assertEquals(
                20,
                SlitheriteBlockRegistryScenario.nextPreSetupLightReadyClientTickCount(
                        20,
                        true
                )
        );

        SlitheriteBlockRegistryScenario.PreSetupLightingEvidence ready =
                preSetupLightingEvidence(true, true, false, true, 15)
                        .withStableClientTicks(20);
        assertTrue(ready.currentTickReady());
        assertTrue(ready.exact());
        assertFalse(preSetupLightingEvidence(
                false,
                true,
                false,
                true,
                15
        ).currentTickReady());
        assertFalse(preSetupLightingEvidence(
                true,
                false,
                false,
                true,
                15
        ).currentTickReady());
        assertFalse(preSetupLightingEvidence(
                true,
                true,
                true,
                true,
                15
        ).currentTickReady());
        assertFalse(preSetupLightingEvidence(
                true,
                true,
                false,
                false,
                15
        ).currentTickReady());
        assertFalse(preSetupLightingEvidence(
                true,
                true,
                false,
                true,
                0
        ).currentTickReady());

        assertTrue(invocationNames("tickWaitingForWorld").containsAll(Set.of(
                "execute",
                "transition"
        )));
        assertTrue(invocationNamesWithPrefix(
                "lambda$tickWaitingForWorld$"
        ).contains("prepareArenaView"));
        assertTrue(invocationNames("prepareArenaView").containsAll(Set.of(
                "loadArenaChunks",
                "setInvulnerable",
                "teleport"
        )));
        assertTrue(Set.of(
                "clearArena",
                "buildArena",
                "placeAllBlockItems",
                "setupFixture",
                "setBlockState"
        ).stream().noneMatch(invocationNames("prepareArenaView")::contains));
        assertTrue(invocationNames("tickWaitingForClientLightPayload").containsAll(
                Set.of(
                        "capturePreSetupLightingEvidence",
                        "nextPreSetupLightReadyClientTickCount",
                        "currentTickReady",
                        "exact",
                        "execute",
                        "transition"
                )
        ));
        assertEquals(
                2,
                fieldWriteCount(
                        "tickWaitingForClientLightPayload",
                        "preSetupLightReadyClientTicks"
                )
        );
        assertTrue(invocationNamesWithPrefix(
                "lambda$tickWaitingForClientLightPayload$"
        ).contains("setupFixture"));
        assertTrue(invocationNames("capturePreSetupLightingEvidence").containsAll(
                Set.of(
                        "getLightingProvider",
                        "hasNoChunkUpdaters",
                        "hasUpdates",
                        "isLightingEnabled"
                )
        ));
        assertFalse(invocationNames(
                "capturePreSetupLightingEvidence"
        ).contains("getStatus"));
        assertTrue(invocationNamesWithPrefix(
                "lambda$capturePreSetupLightingEvidence$"
        ).contains("getLightLevel"));

        Set<String> scenarioCalls = invocationNamesWithPrefix("");
        assertFalse(scenarioCalls.contains("checkBlock"));
        assertFalse(scenarioCalls.contains("doLightUpdates"));
        String constants = new String(classBytes(), StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("ChunkDataS2CPacket"));
        assertFalse(constants.contains("LightUpdateS2CPacket"));
    }

    @SuppressWarnings("unchecked")
    private static List<?> staticList(String fieldName) throws Exception {
        Field field = SlitheriteBlockRegistryScenario.class
                .getDeclaredField(fieldName);
        field.setAccessible(true);
        return (List<?>) field.get(null);
    }

    private static List<Object> fixtureValues(List<?> fixtures, String accessor)
            throws Exception {
        List<Object> values = new ArrayList<>();
        for (Object fixture : fixtures) {
            Method method = fixture.getClass().getDeclaredMethod(accessor);
            method.setAccessible(true);
            Object value = method.invoke(fixture);
            values.add(value instanceof Identifier identifier
                    ? identifier.toString()
                    : value);
        }
        return List.copyOf(values);
    }

    private static long staticLong(String fieldName) throws IOException {
        try {
            Field field = SlitheriteBlockRegistryScenario.class
                    .getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getLong(null);
        } catch (ReflectiveOperationException exception) {
            throw new IOException("Cannot read scenario constant " + fieldName, exception);
        }
    }

    private static int staticInt(String fieldName) throws IOException {
        try {
            Field field = SlitheriteBlockRegistryScenario.class
                    .getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException exception) {
            throw new IOException("Cannot read scenario constant " + fieldName, exception);
        }
    }

    private static SlitheriteBlockRegistryScenario.PreSetupLightingEvidence
            preSetupLightingEvidence(
                    boolean chunksLoaded,
                    boolean chunkUpdatersEmpty,
                    boolean pendingUpdates,
                    boolean columnsEnabled,
                    int skyLevel
            ) {
        int chunkCount = SlitheriteBlockRegistryScenario.ARENA_CHUNKS.size();
        return new SlitheriteBlockRegistryScenario.PreSetupLightingEvidence(
                0,
                chunksLoaded,
                chunkUpdatersEmpty,
                pendingUpdates,
                java.util.Collections.nCopies(chunkCount, columnsEnabled),
                java.util.Collections.nCopies(18, skyLevel)
        );
    }

    private static List<String> behaviorPhaseNames() throws IOException {
        try {
            Class<?> phaseClass = Class.forName(SCENARIO.replace('/', '.')
                    + "$BehaviorPhase");
            Object[] constants = phaseClass.getEnumConstants();
            if (constants == null) throw new IOException("BehaviorPhase is not an enum");
            return java.util.Arrays.stream(constants)
                    .map(Object::toString)
                    .toList();
        } catch (ClassNotFoundException exception) {
            throw new IOException("BehaviorPhase is missing", exception);
        }
    }

    private static Set<String> invocationNames(String methodName) throws IOException {
        return invocationNames(methodName, false);
    }

    private static Set<String> invocationNamesWithPrefix(String methodNamePrefix)
            throws IOException {
        return invocationNames(methodNamePrefix, true);
    }

    private static Set<String> invocationNames(
            String methodName,
            boolean prefixMatch
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
                if (prefixMatch ? !name.startsWith(methodName) : !methodName.equals(name)) {
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
                        names.add(name);
                    }
                };
            }
        }, 0);
        return names;
    }

    private static List<String> invocationSequence(String methodName)
            throws IOException {
        List<String> names = new ArrayList<>();
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
                        names.add(name);
                    }
                };
            }
        }, 0);
        return List.copyOf(names);
    }

    private static Set<String> invocationKeys(
            String className,
            String methodName
    ) throws IOException {
        Set<String> keys = new HashSet<>();
        new ClassReader(classBytes(className)).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        keys.add(owner + "." + name);
                    }
                };
            }
        }, 0);
        return keys;
    }

    private static Set<String> fieldAccessNames(String methodName) throws IOException {
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
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (SCENARIO.equals(owner)) names.add(name);
                    }
                };
            }
        }, 0);
        return names;
    }

    private static Set<String> fieldAccessKeys(String methodName) throws IOException {
        Set<String> keys = new HashSet<>();
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
                        keys.add(owner + "." + name);
                    }
                };
            }
        }, 0);
        return keys;
    }

    private static Set<String> fieldWriteKeys(List<String> methodNames)
            throws IOException {
        Set<String> keys = new HashSet<>();
        new ClassReader(classBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (!methodNames.contains(name)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor
                    ) {
                        if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
                            keys.add(owner + "." + name);
                        }
                    }
                };
            }
        }, 0);
        return keys;
    }

    private static int fieldWriteCount(String methodName, String fieldName)
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
                        if (opcode == Opcodes.PUTFIELD
                                && SCENARIO.equals(owner)
                                && fieldName.equals(name)) count[0]++;
                    }
                };
            }
        }, 0);
        return count[0];
    }

    private static Set<Number> numericConstants(String methodName)
            throws IOException {
        Set<Number> constants = new HashSet<>();
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
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Number number) constants.add(number);
                    }
                };
            }
        }, 0);
        return constants;
    }

    private static byte[] classBytes() throws IOException {
        return classBytes(SCENARIO);
    }

    private static byte[] classBytes(String className) throws IOException {
        try (InputStream input = SlitheriteBlockRegistryScenarioBytecodeTest.class
                .getClassLoader()
                .getResourceAsStream(className + ".class")) {
            if (input == null) throw new IOException("Scenario class is missing");
            return input.readAllBytes();
        }
    }
}
