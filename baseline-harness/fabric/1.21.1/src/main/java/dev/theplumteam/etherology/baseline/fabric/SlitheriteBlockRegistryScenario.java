package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.DataPackFailureScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.Window;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Captures the published-0.1.7 Slitherite family without linking to mod classes.
 */
final class SlitheriteBlockRegistryScenario implements ClientScenario {

    static final String SCENARIO_ID = "slitherite-block-registry";
    static final String INITIAL_SCREENSHOT_FILE_NAME =
            "slitherite-block-registry-initial.png";
    static final String REOPENED_SCREENSHOT_FILE_NAME =
            "slitherite-block-registry-reopened.png";
    static final String WORLD_DIRECTORY_NAME =
            "etherology-original-slitherite-block-registry-world";
    static final String WORLD_DISPLAY_NAME =
            "Etherology Original 0.1.7 Slitherite Blocks";
    static final long WORLD_SEED = 0x455448534c495430L;
    static final int REQUIRED_COMPLETED_RENDERS = 120;
    static final int REQUIRED_LIGHTING_READY_CLIENT_TICKS = 20;
    static final int EXPECTED_SKY_LIGHT_LEVEL = 15;
    static final int EXPECTED_BLOCK_LIGHT_LEVEL = 14;
    static final int EXPECTED_AGGREGATE_STATE_COUNT = 1262;
    static final int EXPECTED_VISUAL_ASSET_COUNT = 79;
    static final ScenarioDefinition DEFINITION =
            ScenarioDefinitions.SLITHERITE_BLOCK_REGISTRY;

    private static final Logger LOGGER = LoggerFactory.getLogger(
            "EtherologyOriginalBaselineHarness"
    );
    private static final String REFERENCE_ID = "published-0.1.7";
    private static final String HARNESS_MOD_ID =
            "etherology_original_baseline_harness";
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final double CAMERA_X = 0.5;
    private static final double CAMERA_Y = ARENA_FLOOR_Y + 1.0;
    private static final double CAMERA_Z = -14.5;
    private static final float CAMERA_YAW = 0.0F;
    private static final float CAMERA_PITCH = 8.0F;
    private static final float PLACEMENT_YAW = 180.0F;
    private static final double CAMERA_POSE_TOLERANCE = 0.0001;
    private static final BlockPos CAMERA_BLOCK_POS =
            new BlockPos(0, ARENA_FLOOR_Y + 1, -15);
    private static final BlockPos LOOT_PROBE_POS =
            new BlockPos(14, ARENA_FLOOR_Y + 2, -8);
    private static final List<BlockFixture> FIXTURES = List.of(
            fixture("slitherite", Block.class, 0, Map.of(), 1),
            fixture("slitherite_stairs", StairsBlock.class, 1, stairsState(), 80),
            fixture("slitherite_slab", SlabBlock.class, 2, slabState(), 6),
            fixture("slitherite_wall", WallBlock.class, 3, wallState(), 324),
            fixture("polished_slitherite", Block.class, 4, Map.of(), 1),
            fixture(
                    "polished_slitherite_stairs",
                    StairsBlock.class,
                    5,
                    stairsState(),
                    80
            ),
            fixture("polished_slitherite_slab", SlabBlock.class, 6, slabState(), 6),
            fixture("polished_slitherite_wall", WallBlock.class, 7, wallState(), 324),
            fixture(
                    "polished_slitherite_button",
                    ButtonBlock.class,
                    8,
                    buttonState(),
                    24
            ),
            fixture(
                    "polished_slitherite_pressure_plate",
                    PressurePlateBlock.class,
                    9,
                    Map.of("powered", "false"),
                    2
            ),
            fixture("polished_slitherite_bricks", Block.class, 10, Map.of(), 1),
            fixture(
                    "polished_slitherite_brick_stairs",
                    StairsBlock.class,
                    11,
                    stairsState(),
                    80
            ),
            fixture(
                    "polished_slitherite_brick_slab",
                    SlabBlock.class,
                    12,
                    slabState(),
                    6
            ),
            fixture(
                    "polished_slitherite_brick_wall",
                    WallBlock.class,
                    13,
                    wallState(),
                    324
            ),
            fixture("chiseled_polished_slitherite", Block.class, 14, Map.of(), 1),
            fixture(
                    "chiseled_polished_slitherite_bricks",
                    Block.class,
                    15,
                    Map.of(),
                    1
            ),
            fixture(
                    "cracked_polished_slitherite_bricks",
                    Block.class,
                    16,
                    Map.of(),
                    1
            )
    );
    private static final List<Identifier> BLOCK_IDS = FIXTURES.stream()
            .map(BlockFixture::id)
            .toList();
    private static final List<BlockPos> RENDER_POSITIONS = FIXTURES.stream()
            .flatMap(fixture -> List.of(
                    fixture.position(),
                    fixture.supportPosition()
            ).stream())
            .distinct()
            .toList();
    private static final List<BlockPos> SKY_LIGHT_SAMPLE_POSITIONS =
            createSkyLightSamplePositions();
    private static final List<BlockPos> BLOCK_LIGHT_SAMPLE_POSITIONS = List.of(
            CAMERA_BLOCK_POS,
            new BlockPos(0, ARENA_FLOOR_Y + 1, -8)
    );
    static final List<Identifier> REQUIRED_RESOURCES = createRequiredResources();
    static final List<RecipeExpectation> OWNED_RECIPES = createOwnedRecipes();
    static final List<Identifier> OWNED_ADVANCEMENTS = createOwnedAdvancements();
    static final List<RecipeExpectation> RELATED_RECIPES = List.of(
            recipe("comparator", "minecraft:crafting", "minecraft:comparator", 1),
            recipe("repeater", "minecraft:crafting", "minecraft:repeater", 1),
            recipe("stonecutter", "minecraft:crafting", "minecraft:stonecutter", 1),
            recipe("pedestal", "minecraft:crafting", "etherology:pedestal", 2),
            recipe(
                    "unadjusted_lens",
                    "etherology:alchemy_recipe",
                    "etherology:unadjusted_lens",
                    1
            )
    );
    static final List<String> ASSERTION_NAMES = createAssertionNames();
    static final List<String> SCREENSHOT_FILE_NAMES = List.of(
            INITIAL_SCREENSHOT_FILE_NAME,
            REOPENED_SCREENSHOT_FILE_NAME
    );

    private Stage stage = Stage.WAITING_FOR_TITLE;
    private CapturePhase capturePhase = CapturePhase.INITIAL;
    private StableRenderCounter stableWorldRenders =
            new StableRenderCounter(REQUIRED_COMPLETED_RENDERS);
    private int clientTicks;
    private int stageClientTicks;
    private int lightingReadyClientTicks;
    private int observedServerLightingGeneration = -1;
    private int requestedWindowWidth = -1;
    private int requestedWindowHeight = -1;
    private boolean resourcesReady;
    private boolean setupSubmitted;
    private boolean behaviorInspectionInFlight;
    private boolean saveSubmitted;
    private boolean restartSubmitted;
    private boolean restartInspectionSubmitted;
    private boolean persistenceExact;
    private boolean reopenedDataExact;
    private String lifecycleFailure = "";
    private List<Identifier> missingResources = List.of();
    private EvidenceLayout evidenceLayout;
    private RegistryProbe registryProbe;
    private DataProbe dataProbe;
    private DataProbe reopenedDataProbe;
    private ServerSetupResult serverSetupResult;
    private FixtureSnapshot currentServerSnapshot;
    private FixtureSnapshot savedSnapshot;
    private FixtureSnapshot reopenedSnapshot;
    private BehaviorSequence serverBehaviorSequence;
    private BehaviorProbe behaviorProbe;
    private final Map<CapturePhase, CaptureEvidence> captureEvidence =
            new LinkedHashMap<>();
    private volatile String serverFailure = "";
    private volatile ServerSetupResult pendingServerSetupResult;
    private volatile BehaviorProbe pendingBehaviorProbe;
    private volatile SaveResult pendingSaveResult;
    private volatile ReopenedResult pendingReopenedResult;
    private volatile ScreenshotResult pendingScreenshotResult;
    private volatile boolean serverLightingInspectionInFlight;
    private volatile int serverLightingGeneration;
    private volatile ServerLightingEvidence latestServerLighting;

    @Override
    public void onEndClientTick(MinecraftClient client) {
        if (stage == Stage.COMPLETE) return;

        String pendingServerFailure = serverFailure;
        if (!pendingServerFailure.isEmpty()) {
            fail(
                    client,
                    "Slitherite server operation failed: " + pendingServerFailure
            );
            return;
        }

        clientTicks++;
        stageClientTicks++;
        try {
            switch (stage) {
                case WAITING_FOR_TITLE -> tickWaitingForTitle(client);
                case STARTING_WORLD -> startWorld(client);
                case WAITING_FOR_WORLD -> tickWaitingForWorld(client);
                case WAITING_FOR_SERVER_SETUP -> tickWaitingForServerSetup(client);
                case WAITING_FOR_BEHAVIOR -> tickWaitingForBehavior(client);
                case WAITING_FOR_CLIENT_MIRROR -> tickWaitingForClientMirror(client);
                case WAITING_FOR_RENDERS -> tickWaitingForRenders(client);
                case CAPTURING -> tickCapturing(client);
                case SAVING_WORLD -> tickSavingWorld(client);
                case DISCONNECTING -> tickDisconnecting(client);
                case WAITING_FOR_RESTART_TITLE -> tickWaitingForRestartTitle(client);
                case RESTARTING_WORLD -> restartWorld(client);
                case WAITING_FOR_RESTART_WORLD -> tickWaitingForRestartWorld(client);
                case WAITING_FOR_RESTART_INSPECTION ->
                        tickWaitingForRestartInspection(client);
                case COMPLETE -> {
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Original Slitherite scenario failed in {}", stage, exception);
            fail(
                    client,
                    stage + " raised " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()
            );
            return;
        }

        if (stage != Stage.COMPLETE
                && stageClientTicks >= MAXIMUM_STAGE_CLIENT_TICKS) {
            fail(
                    client,
                    "Timed out in " + stage + " after " + stageClientTicks
                            + " client ticks; last lighting="
                            + lightingDiagnostic(client)
            );
        }
    }

    @Override
    public void onGameRenderCompleted() {
        if (stage != Stage.WAITING_FOR_RENDERS) return;

        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (!stableWorldRenders.observe(isCaptureStateExact(client))) return;
            captureCurrentPhase(client);
        } catch (RuntimeException exception) {
            stableWorldRenders.observe(false);
            LOGGER.error("Original Slitherite render callback failed", exception);
            fail(
                    client,
                    "Render callback raised " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()
            );
        }
    }

    static int nextLightingReadyClientTickCount(int readyClientTicks, boolean ready) {
        return ready
                ? Math.min(readyClientTicks + 1, REQUIRED_LIGHTING_READY_CLIENT_TICKS)
                : 0;
    }

    static boolean requiresAnotherServerLightingSample(int readyClientTicks) {
        return readyClientTicks < REQUIRED_LIGHTING_READY_CLIENT_TICKS;
    }

    static boolean isExpectedSkyLight(int lightLevel) {
        return lightLevel == EXPECTED_SKY_LIGHT_LEVEL;
    }

    static boolean isExpectedBlockLight(int lightLevel) {
        return lightLevel == EXPECTED_BLOCK_LIGHT_LEVEL;
    }

    static boolean localLightingSamplesExact(
            List<Integer> clientSky,
            List<Integer> clientBlock,
            List<Integer> serverSky,
            List<Integer> serverBlock
    ) {
        return clientSky.size() == SKY_LIGHT_SAMPLE_POSITIONS.size()
                && serverSky.size() == SKY_LIGHT_SAMPLE_POSITIONS.size()
                && clientBlock.size() == BLOCK_LIGHT_SAMPLE_POSITIONS.size()
                && serverBlock.size() == BLOCK_LIGHT_SAMPLE_POSITIONS.size()
                && clientSky.stream().allMatch(
                        SlitheriteBlockRegistryScenario::isExpectedSkyLight
                )
                && serverSky.stream().allMatch(
                        SlitheriteBlockRegistryScenario::isExpectedSkyLight
                )
                && clientBlock.stream().allMatch(
                        SlitheriteBlockRegistryScenario::isExpectedBlockLight
                )
                && serverBlock.stream().allMatch(
                        SlitheriteBlockRegistryScenario::isExpectedBlockLight
                );
    }

    private void tickWaitingForTitle(MinecraftClient client) {
        if (client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) {
            return;
        }
        try {
            ensureEvidenceLayout(client);
        } catch (IOException exception) {
            LOGGER.error("Cannot use the isolated Slitherite evidence layout", exception);
            lifecycleFailure = exception.getMessage();
            stage = Stage.COMPLETE;
            client.scheduleStop();
            return;
        }
        if (evidenceLayout.framebufferWidth() != FRAMEBUFFER_WIDTH
                || evidenceLayout.framebufferHeight() != FRAMEBUFFER_HEIGHT) {
            fail(client, "Slitherite evidence capture is not exactly 1920x1080");
            return;
        }
        if (!requestExpectedFramebuffer(client)) return;

        missingResources = findMissingResources(client);
        resourcesReady = missingResources.isEmpty();
        if (!resourcesReady) {
            fail(client, "Published Slitherite visual assets are missing: "
                    + missingResources);
            return;
        }
        if (!hasBaseRegistry()) {
            fail(client, "The 17 Slitherite block/item registry pairs are incomplete");
            return;
        }
        registryProbe = RegistryProbe.capture();
        if (!registryProbe.blockItemRegistryExact()) {
            fail(client, "The Slitherite registry preflight was not exact");
            return;
        }
        transition(Stage.STARTING_WORLD);
    }

    private void startWorld(MinecraftClient client) {
        Path saveDirectory = saveDirectory(client);
        if (Files.exists(saveDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(saveDirectory)) {
            fail(client, "Refusing to reuse the Slitherite save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "The client already has a world before deterministic creation");
            return;
        }

        GameRules gameRules = new GameRules();
        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        gameRules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        gameRules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        gameRules.get(GameRules.KEEP_INVENTORY).set(true, null);
        gameRules.get(GameRules.DO_IMMEDIATE_RESPAWN).set(true, null);
        gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, null);
        LevelInfo levelInfo = new LevelInfo(
                WORLD_DISPLAY_NAME,
                GameMode.SURVIVAL,
                false,
                Difficulty.PEACEFUL,
                true,
                gameRules,
                net.minecraft.resource.DataConfiguration.SAFE_MODE
        );
        client.createIntegratedServerLoader().createAndStart(
                WORLD_DIRECTORY_NAME,
                levelInfo,
                new GeneratorOptions(WORLD_SEED, true, false),
                WorldPresets::createDemoOptions,
                client.currentScreen
        );
        transition(Stage.WAITING_FOR_WORLD);
    }

    private void tickWaitingForWorld(MinecraftClient client) {
        if (client.currentScreen instanceof DataPackFailureScreen) {
            fail(client, "Minecraft rejected the published Etherology server data");
            return;
        }
        if (!isWorldLifecycleReady(client)) return;

        IntegratedServer server = client.getServer();
        ServerPlayerEntity serverPlayer = server.getPlayerManager()
                .getPlayer(client.player.getUuid());
        if (serverPlayer == null || setupSubmitted) return;

        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(client.player);
        setupSubmitted = true;
        UUID playerId = client.player.getUuid();
        server.execute(() -> setupFixture(server, playerId));
        transition(Stage.WAITING_FOR_SERVER_SETUP);
    }

    private void tickWaitingForServerSetup(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "Slitherite server setup failed: " + serverFailure);
            return;
        }
        ServerSetupResult result = pendingServerSetupResult;
        if (result == null) return;

        serverSetupResult = result;
        registryProbe = result.registryProbe();
        dataProbe = result.dataProbe();
        currentServerSnapshot = result.snapshot();
        if (!registryProbe.exact()
                || !dataProbe.exact()
                || !result.placements().exact()
                || !currentServerSnapshot.exact()
                || !result.chunksLoaded()) {
            fail(client, "The initial Slitherite native probe was not exact");
            return;
        }
        transition(Stage.WAITING_FOR_BEHAVIOR);
    }

    private void tickWaitingForBehavior(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "Slitherite behavior probe failed: " + serverFailure);
            return;
        }
        BehaviorProbe result = pendingBehaviorProbe;
        if (result != null) {
            behaviorProbe = result;
            if (!result.exact()) {
                fail(client, "The native button/pressure-plate behavior changed");
                return;
            }
            currentServerSnapshot = result.snapshot();
            resetLightingReadiness();
            transition(Stage.WAITING_FOR_CLIENT_MIRROR);
            return;
        }
        IntegratedServer server = client.getServer();
        if (server == null || behaviorInspectionInFlight) return;

        behaviorInspectionInFlight = true;
        server.execute(() -> {
            try {
                advanceBehaviorProbe(server.getOverworld());
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            } finally {
                behaviorInspectionInFlight = false;
            }
        });
    }

    private void tickWaitingForClientMirror(MinecraftClient client) {
        boolean ready = isWorldViewReady(client)
                && currentServerSnapshot != null
                && currentServerSnapshot.equals(captureSnapshot(client.world));
        observeLightingReadiness(client, ready);
        if (requiresAnotherServerLightingSample(lightingReadyClientTicks)
                || serverLightingInspectionInFlight) {
            return;
        }
        transition(Stage.WAITING_FOR_RENDERS);
    }

    private void tickWaitingForRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world vanished before Slitherite capture");
            return;
        }
        observeLightingReadiness(client, true);
        if (lightingReadyClientTicks < REQUIRED_LIGHTING_READY_CLIENT_TICKS) {
            stableWorldRenders.observe(false);
        }
    }

    private void tickCapturing(MinecraftClient client) {
        ScreenshotResult screenshot = pendingScreenshotResult;
        if (screenshot == null) return;
        if (!screenshot.passed()) {
            fail(client, "The Slitherite screenshot failed: " + screenshot.failure());
            return;
        }
        CaptureEvidence capture = captureEvidence.get(capturePhase);
        captureEvidence.put(capturePhase, capture.withScreenshot(screenshot));
        pendingScreenshotResult = null;
        if (capturePhase == CapturePhase.INITIAL) {
            submitSave(client);
        } else {
            publish(client);
        }
    }

    private void tickSavingWorld(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "The Slitherite world save failed: " + serverFailure);
            return;
        }
        SaveResult result = pendingSaveResult;
        if (result == null) return;
        if (!result.saved() || !result.snapshot().exact()) {
            fail(client, "The forced Slitherite save did not preserve the fixture");
            return;
        }
        savedSnapshot = result.snapshot();
        transition(Stage.DISCONNECTING);
    }

    private void tickDisconnecting(MinecraftClient client) {
        if (client.world == null) {
            fail(client, "The client world vanished before Slitherite restart");
            return;
        }
        client.world.disconnect();
        client.disconnect(new TitleScreen());
        transition(Stage.WAITING_FOR_RESTART_TITLE);
    }

    private void tickWaitingForRestartTitle(MinecraftClient client) {
        if (client.getServer() != null
                || client.world != null
                || client.player != null
                || !(client.currentScreen instanceof TitleScreen)) {
            return;
        }
        transition(Stage.RESTARTING_WORLD);
    }

    private void restartWorld(MinecraftClient client) {
        if (restartSubmitted) return;
        restartSubmitted = true;
        client.createIntegratedServerLoader().start(
                WORLD_DIRECTORY_NAME,
                () -> fail(client, "Minecraft aborted the Slitherite restart")
        );
        transition(Stage.WAITING_FOR_RESTART_WORLD);
    }

    private void tickWaitingForRestartWorld(MinecraftClient client) {
        if (client.currentScreen instanceof DataPackFailureScreen) {
            fail(client, "Minecraft rejected saved Slitherite data packs on restart");
            return;
        }
        if (!isWorldLifecycleReady(client)) return;
        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(client.player);
        transition(Stage.WAITING_FOR_RESTART_INSPECTION);
    }

    private void tickWaitingForRestartInspection(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "Reopened Slitherite inspection failed: " + serverFailure);
            return;
        }
        IntegratedServer server = client.getServer();
        if (server == null) return;
        if (!restartInspectionSubmitted) {
            restartInspectionSubmitted = true;
            UUID playerId = client.player.getUuid();
            server.execute(() -> inspectReopenedWorld(server, playerId));
        }
        ReopenedResult result = pendingReopenedResult;
        if (result == null) return;

        reopenedSnapshot = result.snapshot();
        reopenedDataProbe = result.dataProbe();
        persistenceExact = savedSnapshot != null
                && savedSnapshot.equals(reopenedSnapshot)
                && reopenedSnapshot.exact()
                && result.registryProbe().exact()
                && result.registryProbe().equals(registryProbe);
        reopenedDataExact = dataProbe != null
                && reopenedDataProbe.exact()
                && dataProbe.sameOutcome(reopenedDataProbe);
        if (!persistenceExact || !reopenedDataExact) {
            fail(client, "The Slitherite fixture or loaded data changed across restart");
            return;
        }
        currentServerSnapshot = reopenedSnapshot;
        capturePhase = CapturePhase.REOPENED;
        stableWorldRenders = new StableRenderCounter(REQUIRED_COMPLETED_RENDERS);
        resetLightingReadiness();
        transition(Stage.WAITING_FOR_CLIENT_MIRROR);
    }

    private void setupFixture(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                throw new IllegalStateException("The integrated server has no player");
            }

            boolean chunksLoaded = loadArenaChunks(world);
            world.setTimeOfDay(6000L);
            world.setWeather(6000, 0, false, false);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
            world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, server);
            clearArena(world);
            buildArena(world);

            player.changeGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(true);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            player.teleport(
                    world,
                    CAMERA_X,
                    CAMERA_Y,
                    CAMERA_Z,
                    PLACEMENT_YAW,
                    CAMERA_PITCH
            );
            PlacementInventory placements = placeAllBlockItems(world, player);
            player.teleport(
                    world,
                    CAMERA_X,
                    CAMERA_Y,
                    CAMERA_Z,
                    CAMERA_YAW,
                    CAMERA_PITCH
            );
            player.setSpawnPoint(
                    World.OVERWORLD,
                    CAMERA_BLOCK_POS,
                    CAMERA_YAW,
                    true,
                    false
            );

            RegistryProbe setupRegistry = RegistryProbe.capture();
            DataProbe setupData = DataProbe.capture(server, world, player);
            FixtureSnapshot snapshot = captureSnapshot(world);
            initializeBehaviorProbe(server, world, player);
            requestServerLightingChecks(world);
            pendingServerSetupResult = new ServerSetupResult(
                    chunksLoaded,
                    setupRegistry,
                    setupData,
                    placements,
                    snapshot,
                    server.getSaveProperties().getLevelName(),
                    world.getSeed(),
                    world.getRegistryKey().getValue().toString()
            );
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    private PlacementInventory placeAllBlockItems(
            ServerWorld world,
            ServerPlayerEntity player
    ) {
        Map<String, PlacementEvidence> placements = new LinkedHashMap<>();
        for (BlockFixture fixture : FIXTURES) {
            Block block = requiredBlock(fixture.id());
            Item item = requiredItem(fixture.id());
            if (!(item instanceof BlockItem blockItem)) {
                throw new IllegalStateException(fixture.id() + " is not a BlockItem");
            }

            ItemStack stack = new ItemStack(item);
            int beforeCount = stack.getCount();
            player.setStackInHand(Hand.MAIN_HAND, stack);
            BlockHitResult hit = placementHit(fixture);
            ActionResult actionResult = blockItem.useOnBlock(
                    new ItemUsageContext(player, Hand.MAIN_HAND, hit)
            );
            BlockState placedState = world.getBlockState(fixture.position());
            placements.put(
                    fixture.id().toString(),
                    new PlacementEvidence(
                            actionResult.name(),
                            actionResult.isAccepted(),
                            beforeCount,
                            stack.getCount(),
                            blockItem.getBlock() == block,
                            Registries.BLOCK.getId(placedState.getBlock()).toString(),
                            stateDescription(placedState)
                    )
            );
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
        return new PlacementInventory(
                Collections.unmodifiableMap(new LinkedHashMap<>(placements))
        );
    }

    private BlockHitResult placementHit(BlockFixture fixture) {
        if (fixture.button()) {
            return new BlockHitResult(
                    Vec3d.ofCenter(fixture.supportPosition()).add(0.0, 0.0, -0.5),
                    Direction.NORTH,
                    fixture.supportPosition(),
                    false
            );
        }
        return new BlockHitResult(
                Vec3d.ofCenter(fixture.supportPosition()).add(0.0, 0.5, 0.0),
                Direction.UP,
                fixture.supportPosition(),
                false
        );
    }

    private void initializeBehaviorProbe(
            IntegratedServer server,
            ServerWorld world,
            ServerPlayerEntity player
    ) {
        BlockFixture buttonFixture = fixtureByPath("polished_slitherite_button");
        Block button = requiredBlock(buttonFixture.id());
        BlockState buttonBefore = world.getBlockState(buttonFixture.position());
        BlockHitResult buttonHit = new BlockHitResult(
                Vec3d.ofCenter(buttonFixture.position()),
                Direction.NORTH,
                buttonFixture.position(),
                false
        );
        ActionResult buttonAction = server.getPlayerInteractionManager(player)
                .interactBlock(
                        player,
                        world,
                        ItemStack.EMPTY,
                        Hand.MAIN_HAND,
                        buttonHit
                );
        BlockState buttonPowered = world.getBlockState(buttonFixture.position());
        boolean buttonActivated = buttonAction.isAccepted()
                && !buttonBefore.get(Properties.POWERED)
                && buttonPowered.get(Properties.POWERED);
        boolean buttonResetScheduled = world.getBlockTickScheduler().isQueued(
                buttonFixture.position(),
                button
        );

        BlockFixture pressureFixture = fixtureByPath(
                "polished_slitherite_pressure_plate"
        );
        ItemEntity itemEntity = new ItemEntity(
                world,
                pressureFixture.position().getX() + 0.5,
                pressureFixture.position().getY() + 0.05,
                pressureFixture.position().getZ() + 0.5,
                new ItemStack(Items.COBBLESTONE)
        );
        itemEntity.setNoGravity(true);
        itemEntity.setPickupDelayInfinite();
        if (!world.spawnEntity(itemEntity)) {
            throw new IllegalStateException("Cannot spawn the pressure-plate item probe");
        }
        long now = world.getTime();
        serverBehaviorSequence = new BehaviorSequence(
                BehaviorPhase.WAITING_FOR_ITEM,
                now,
                now + 3L,
                buttonActivated,
                buttonResetScheduled,
                itemEntity,
                null,
                false,
                false
        );
    }

    private void advanceBehaviorProbe(ServerWorld world) {
        BehaviorSequence sequence = serverBehaviorSequence;
        if (sequence == null || pendingBehaviorProbe != null) return;
        if (world.getTime() < sequence.deadline()) return;

        BlockFixture pressureFixture = fixtureByPath(
                "polished_slitherite_pressure_plate"
        );
        switch (sequence.phase()) {
            case WAITING_FOR_ITEM -> {
                boolean itemIgnored = !world.getBlockState(pressureFixture.position())
                        .get(Properties.POWERED);
                sequence.itemEntity().discard();
                PigEntity pigEntity = EntityType.PIG.create(world);
                if (pigEntity == null) {
                    throw new IllegalStateException(
                            "Cannot create the pressure-plate living probe"
                    );
                }
                pigEntity.setAiDisabled(true);
                pigEntity.setInvulnerable(true);
                pigEntity.setPosition(
                        pressureFixture.position().getX() + 0.5,
                        pressureFixture.position().getY() + 0.1,
                        pressureFixture.position().getZ() + 0.5
                );
                if (!world.spawnEntity(pigEntity)) {
                    throw new IllegalStateException(
                            "Cannot spawn the pressure-plate living probe"
                    );
                }
                serverBehaviorSequence = new BehaviorSequence(
                        BehaviorPhase.WAITING_FOR_LIVING,
                        sequence.buttonActivationTick(),
                        world.getTime() + 3L,
                        sequence.buttonActivated(),
                        sequence.buttonResetScheduled(),
                        sequence.itemEntity(),
                        pigEntity,
                        itemIgnored,
                        false
                );
            }
            case WAITING_FOR_LIVING -> {
                boolean livingActivated = world.getBlockState(
                        pressureFixture.position()
                ).get(Properties.POWERED);
                sequence.pigEntity().discard();
                serverBehaviorSequence = new BehaviorSequence(
                        BehaviorPhase.WAITING_FOR_RESET,
                        sequence.buttonActivationTick(),
                        world.getTime() + 25L,
                        sequence.buttonActivated(),
                        sequence.buttonResetScheduled(),
                        sequence.itemEntity(),
                        sequence.pigEntity(),
                        sequence.itemIgnored(),
                        livingActivated
                );
            }
            case WAITING_FOR_RESET -> {
                BlockFixture buttonFixture = fixtureByPath(
                        "polished_slitherite_button"
                );
                boolean buttonReset = !world.getBlockState(buttonFixture.position())
                        .get(Properties.POWERED);
                boolean pressureReset = !world.getBlockState(pressureFixture.position())
                        .get(Properties.POWERED);
                long elapsed = world.getTime() - sequence.buttonActivationTick();
                FixtureSnapshot snapshot = captureSnapshot(world);
                pendingBehaviorProbe = new BehaviorProbe(
                        sequence.buttonActivated(),
                        sequence.buttonResetScheduled(),
                        buttonReset,
                        elapsed,
                        sequence.itemIgnored(),
                        sequence.livingActivated(),
                        pressureReset,
                        snapshot
                );
            }
        }
    }

    private void inspectReopenedWorld(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                throw new IllegalStateException("The reopened server has no player");
            }
            player.teleport(
                    world,
                    CAMERA_X,
                    CAMERA_Y,
                    CAMERA_Z,
                    CAMERA_YAW,
                    CAMERA_PITCH
            );
            pendingReopenedResult = new ReopenedResult(
                    RegistryProbe.capture(),
                    DataProbe.capture(server, world, player),
                    captureSnapshot(world)
            );
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    private void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-18, ARENA_FLOOR_Y, -18);
        BlockPos end = new BlockPos(18, ARENA_FLOOR_Y + 8, 12);
        for (BlockPos position : BlockPos.iterate(start, end)) {
            world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private void buildArena(ServerWorld world) {
        for (int x = -18; x <= 18; x++) {
            for (int z = -18; z <= 12; z++) {
                Block floor = (x + z) % 8 == 0
                        ? Blocks.SEA_LANTERN
                        : Blocks.SMOOTH_STONE;
                world.setBlockState(
                        new BlockPos(x, ARENA_FLOOR_Y, z),
                        floor.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
        }
        world.setBlockState(
                CAMERA_BLOCK_POS.down(),
                Blocks.SEA_LANTERN.getDefaultState(),
                Block.NOTIFY_ALL
        );
        world.setBlockState(
                new BlockPos(0, ARENA_FLOOR_Y, -8),
                Blocks.SEA_LANTERN.getDefaultState(),
                Block.NOTIFY_ALL
        );
        for (BlockFixture fixture : FIXTURES) {
            world.setBlockState(
                    fixture.supportPosition(),
                    fixture.button()
                            ? Blocks.SMOOTH_STONE.getDefaultState()
                            : Blocks.POLISHED_ANDESITE.getDefaultState(),
                    Block.NOTIFY_ALL
            );
            world.setBlockState(
                    fixture.position(),
                    Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_ALL
            );
        }
    }

    private boolean loadArenaChunks(ServerWorld world) {
        boolean loaded = true;
        for (int chunkX = -2; chunkX <= 1; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 0; chunkZ++) {
                loaded &= world.getChunkManager()
                        .getChunk(chunkX, chunkZ, ChunkStatus.FULL, true) != null;
            }
        }
        return loaded;
    }

    private void submitSave(MinecraftClient client) {
        if (saveSubmitted) return;
        IntegratedServer server = client.getServer();
        if (server == null || !server.isRunning() || server.isStopping()) {
            fail(client, "The integrated server stopped before the forced save");
            return;
        }
        saveSubmitted = true;
        server.execute(() -> {
            try {
                FixtureSnapshot snapshot = captureSnapshot(server.getOverworld());
                pendingSaveResult = new SaveResult(
                        server.saveAll(false, true, true),
                        snapshot
                );
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            }
        });
        transition(Stage.SAVING_WORLD);
    }

    private void captureCurrentPhase(MinecraftClient client) {
        if (stage != Stage.WAITING_FOR_RENDERS || !isCaptureStateExact(client)) {
            stableWorldRenders.observe(false);
            return;
        }
        FixtureSnapshot clientSnapshot = captureSnapshot(client.world);
        LightingEvidence lighting = captureLightingEvidence(client);
        if (!lighting.exact()) {
            stableWorldRenders.observe(false);
            return;
        }
        CaptureEvidence capture = new CaptureEvidence(
                clientSnapshot.equals(currentServerSnapshot),
                isFixtureRenderReady(client),
                lighting,
                hasExpectedCameraPose(client),
                stableWorldRenders.completedRenders(),
                client.getFramebuffer().textureWidth,
                client.getFramebuffer().textureHeight,
                cameraPoseDescription(client),
                clientSnapshot,
                null
        );
        captureEvidence.put(capturePhase, capture);
        transition(Stage.CAPTURING);
        ScreenshotRecorder.saveScreenshot(
                evidenceLayout.scenarioRoot().toFile(),
                capturePhase.screenshotFileName(),
                client.getFramebuffer(),
                message -> pendingScreenshotResult = inspectScreenshot(
                        evidenceLayout.screenshotPath(
                                capturePhase.screenshotFileName()
                        )
                )
        );
    }

    private ScreenshotResult inspectScreenshot(Path path) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return ScreenshotResult.failed(
                        "Minecraft did not write one regular PNG"
                );
            }
            long size = Files.size(path);
            if (size <= 0L) {
                return ScreenshotResult.failed("Minecraft wrote an empty PNG");
            }
            return new ScreenshotResult(
                    true,
                    size,
                    ArtifactDigest.sha256(path),
                    ""
            );
        } catch (IOException exception) {
            return ScreenshotResult.failed(exception.getMessage());
        }
    }

    private boolean isCaptureStateExact(MinecraftClient client) {
        return isWorldViewReady(client)
                && currentServerSnapshot != null
                && currentServerSnapshot.exact()
                && currentServerSnapshot.equals(captureSnapshot(client.world))
                && lightingReadyClientTicks >= REQUIRED_LIGHTING_READY_CLIENT_TICKS
                && serverFailure.isEmpty()
                && !serverLightingInspectionInFlight
                && localLightingReady(client)
                && isFixtureRenderReady(client)
                && hasExpectedCameraPose(client);
    }

    private boolean isWorldLifecycleReady(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        return server != null
                && server.isRunning()
                && !server.isStopping()
                && client.world != null
                && client.player != null
                && client.player.networkHandler != null
                && client.interactionManager != null;
    }

    private boolean isWorldViewReady(MinecraftClient client) {
        Window window = client.getWindow();
        return isWorldLifecycleReady(client)
                && client.getOverlay() == null
                && client.currentScreen == null
                && client.world.getChunkManager().isChunkLoaded(0, 0)
                && client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT
                && window.getFramebufferWidth() == FRAMEBUFFER_WIDTH
                && window.getFramebufferHeight() == FRAMEBUFFER_HEIGHT;
    }

    private boolean isFixtureRenderReady(MinecraftClient client) {
        if (!localLightingReady(client)) return false;
        if (!client.worldRenderer.isTerrainRenderComplete()) return false;
        for (BlockPos position : RENDER_POSITIONS) {
            if (!client.worldRenderer.isRenderingReady(position)) return false;
        }
        return true;
    }

    private boolean localLightingReady(MinecraftClient client) {
        if (client.world == null || latestServerLighting == null) return false;
        return LocalLightingSamples.capture(client.world).exact()
                && latestServerLighting.samples().exact();
    }

    private void observeLightingReadiness(
            MinecraftClient client,
            boolean prerequisiteReady
    ) {
        if (!prerequisiteReady || client.world == null) {
            lightingReadyClientTicks = 0;
            requestServerLightingSample(client);
            return;
        }
        ServerLightingEvidence serverLighting = latestServerLighting;
        if (serverLighting != null
                && serverLighting.generation() != observedServerLightingGeneration) {
            observedServerLightingGeneration = serverLighting.generation();
            boolean locallyReady = LocalLightingSamples.capture(client.world).exact()
                    && serverLighting.samples().exact();
            lightingReadyClientTicks = nextLightingReadyClientTickCount(
                    lightingReadyClientTicks,
                    locallyReady
            );
        }
        if (requiresAnotherServerLightingSample(lightingReadyClientTicks)) {
            requestServerLightingSample(client);
        }
    }

    private void requestServerLightingSample(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null || serverLightingInspectionInFlight) return;
        serverLightingInspectionInFlight = true;
        server.execute(() -> {
            try {
                ServerWorld world = server.getOverworld();
                requestServerLightingChecks(world);
                latestServerLighting = new ServerLightingEvidence(
                        ++serverLightingGeneration,
                        LocalLightingSamples.capture(world)
                );
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            } finally {
                serverLightingInspectionInFlight = false;
            }
        });
    }

    private void resetLightingReadiness() {
        lightingReadyClientTicks = 0;
        observedServerLightingGeneration = -1;
        latestServerLighting = null;
    }

    private String lightingDiagnostic(MinecraftClient client) {
        if (client.world == null) {
            return "stableClientTicks=" + lightingReadyClientTicks
                    + ";client=missing;server=" + latestServerLighting;
        }
        return captureLightingEvidence(client).description();
    }

    private LightingEvidence captureLightingEvidence(MinecraftClient client) {
        boolean pendingUpdates = client.world.getChunkManager()
                .getLightingProvider()
                .hasUpdates();
        LocalLightingSamples clientSamples = LocalLightingSamples.capture(client.world);
        ServerLightingEvidence serverLighting = latestServerLighting;
        return new LightingEvidence(
                lightingReadyClientTicks,
                pendingUpdates,
                clientSamples,
                serverLighting == null ? -1 : serverLighting.generation(),
                serverLighting == null
                        ? LocalLightingSamples.missing()
                        : serverLighting.samples()
        );
    }

    private void requestServerLightingChecks(ServerWorld world) {
        for (BlockPos position : SKY_LIGHT_SAMPLE_POSITIONS) {
            world.getChunkManager().getLightingProvider().checkBlock(position);
        }
        for (BlockPos position : BLOCK_LIGHT_SAMPLE_POSITIONS) {
            world.getChunkManager().getLightingProvider().checkBlock(position.down());
            world.getChunkManager().getLightingProvider().checkBlock(position);
        }
    }

    private boolean hasExpectedCameraPose(MinecraftClient client) {
        if (client.player == null
                || client.getCameraEntity() != client.player
                || !client.options.getPerspective().isFirstPerson()
                || !client.player.isOnGround()) {
            return false;
        }
        return Math.abs(client.player.getX() - CAMERA_X) <= CAMERA_POSE_TOLERANCE
                && Math.abs(client.player.getY() - CAMERA_Y) <= CAMERA_POSE_TOLERANCE
                && Math.abs(client.player.getZ() - CAMERA_Z) <= CAMERA_POSE_TOLERANCE
                && Math.abs(MathHelper.wrapDegrees(client.player.getYaw() - CAMERA_YAW))
                        <= CAMERA_POSE_TOLERANCE
                && Math.abs(client.player.getPitch() - CAMERA_PITCH)
                        <= CAMERA_POSE_TOLERANCE;
    }

    private boolean requestExpectedFramebuffer(MinecraftClient client) {
        Window window = client.getWindow();
        if (client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT
                && window.getFramebufferWidth() == FRAMEBUFFER_WIDTH
                && window.getFramebufferHeight() == FRAMEBUFFER_HEIGHT) {
            return true;
        }
        if (client.options.getFullscreen().getValue()) {
            client.options.getFullscreen().setValue(false);
        }
        if (window.isFullscreen()) {
            requestedWindowWidth = -1;
            requestedWindowHeight = -1;
            window.toggleFullscreen();
            return false;
        }
        int currentWindowWidth = window.getWidth();
        int currentWindowHeight = window.getHeight();
        int currentFramebufferWidth = window.getFramebufferWidth();
        int currentFramebufferHeight = window.getFramebufferHeight();
        if (currentWindowWidth <= 0
                || currentWindowHeight <= 0
                || currentFramebufferWidth <= 0
                || currentFramebufferHeight <= 0) {
            return false;
        }
        int targetWindowWidth = FramebufferWindowSizing.requestedWindowDimension(
                FRAMEBUFFER_WIDTH,
                currentWindowWidth,
                currentFramebufferWidth
        );
        int targetWindowHeight = FramebufferWindowSizing.requestedWindowDimension(
                FRAMEBUFFER_HEIGHT,
                currentWindowHeight,
                currentFramebufferHeight
        );
        if (targetWindowWidth != requestedWindowWidth
                || targetWindowHeight != requestedWindowHeight
                || stageClientTicks % 20 == 0) {
            requestedWindowWidth = targetWindowWidth;
            requestedWindowHeight = targetWindowHeight;
            window.setWindowedSize(targetWindowWidth, targetWindowHeight);
        }
        return false;
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;
        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Original Slitherite lifecycle failure: {}", lifecycleFailure);
        publish(client);
    }

    private void publish(MinecraftClient client) {
        if (stage == Stage.COMPLETE) return;
        try {
            ensureEvidenceLayout(client);
            List<ArtifactDigest> artifacts = List.of(
                    ArtifactDigest.capture("etherology"),
                    ArtifactDigest.capture(HARNESS_MOD_ID)
            );
            JsonObject report = createReport(client, artifacts);
            AtomicEvidenceWriter.writeReportThenMarker(
                    evidenceLayout,
                    report,
                    report.get("passed").getAsBoolean()
            );
            LOGGER.info(
                    "Original Slitherite evidence published with status {}: {}",
                    report.get("status").getAsString(),
                    evidenceLayout.scenarioRoot()
            );
        } catch (IOException exception) {
            LOGGER.error("Cannot atomically publish Slitherite evidence", exception);
        } finally {
            stage = Stage.COMPLETE;
            client.scheduleStop();
        }
    }

    private void ensureEvidenceLayout(MinecraftClient client) throws IOException {
        if (evidenceLayout != null) return;
        EvidenceLayout layout = EvidenceLayout.resolve(
                client.runDirectory.toPath(),
                DEFINITION
        );
        layout.requireFreshTargets(
                INITIAL_SCREENSHOT_FILE_NAME,
                REOPENED_SCREENSHOT_FILE_NAME
        );
        evidenceLayout = layout;
    }

    private JsonObject createReport(
            MinecraftClient client,
            List<ArtifactDigest> artifacts
    ) {
        JsonArray assertions = new JsonArray();
        boolean passed = lifecycleFailure.isEmpty();
        boolean etherologyLoaded = FabricLoader.getInstance().isModLoaded("etherology");
        passed &= addAssertion(
                assertions,
                "fabric_mod_loaded:etherology",
                etherologyLoaded,
                "loaded",
                etherologyLoaded ? "loaded" : "missing"
        );
        RegistryProbe registry = registryProbe == null
                ? RegistryProbe.missing()
                : registryProbe;
        for (BlockFixture fixture : FIXTURES) {
            RegistryEntry entry = registry.entries().getOrDefault(
                    fixture.id().toString(),
                    RegistryEntry.missing(fixture.id().toString())
            );
            passed &= addAssertion(assertions, "registry:block:" + fixture.id(),
                    entry.blockPresent(), "present",
                    entry.blockPresent() ? "present" : "missing");
            passed &= addAssertion(assertions, "registry:item:" + fixture.id(),
                    entry.itemPresent(), "present",
                    entry.itemPresent() ? "present" : "missing");
            passed &= addAssertion(assertions, "runtime:block_class:" + fixture.id(),
                    fixture.blockClass().getName().equals(entry.blockClass()),
                    fixture.blockClass().getName(), entry.blockClass());
            passed &= addAssertion(
                    assertions,
                    "runtime:block_item_class:" + fixture.id(),
                    BlockItem.class.getName().equals(entry.itemClass()),
                    BlockItem.class.getName(),
                    entry.itemClass()
            );
            passed &= addAssertion(assertions, "block_item_mapping:" + fixture.id(),
                    entry.blockItemMapping(), "true",
                    Boolean.toString(entry.blockItemMapping()));
            passed &= addAssertion(assertions, "default_state:" + fixture.id(),
                    fixture.defaultProperties().equals(entry.defaultProperties()),
                    fixture.defaultProperties().toString(),
                    entry.defaultProperties().toString());
            passed &= addAssertion(assertions, "state_count:" + fixture.id(),
                    fixture.stateCount() == entry.stateCount(),
                    Integer.toString(fixture.stateCount()),
                    Integer.toString(entry.stateCount()));
            passed &= addAssertion(
                    assertions,
                    "default_state_network_id:" + fixture.id(),
                    entry.defaultStateNetworkId() >= 0,
                    "non-negative",
                    Integer.toString(entry.defaultStateNetworkId())
            );
        }

        passed &= addAssertion(
                assertions,
                "slitherite_canonical_resources_exact",
                resourcesReady,
                REQUIRED_RESOURCES.toString(),
                resourcesReady ? REQUIRED_RESOURCES.toString() : missingResources.toString()
        );
        passed &= addAssertion(
                assertions,
                "slitherite_state_network_ids_exact",
                registry.networkIdsExact(),
                EXPECTED_AGGREGATE_STATE_COUNT + " unique non-negative raw ids",
                registry.networkIdDescription()
        );
        for (ArtifactDigest artifact : artifacts) {
            passed &= addAssertion(
                    assertions,
                    "packaged_root_jar:" + artifact.modId(),
                    artifact.passed(),
                    "one regular root JAR",
                    artifact.passed() ? "one regular root JAR" : artifact.failure()
            );
        }
        passed &= addAssertion(
                assertions,
                "integrated_world_joined",
                isWorldLifecycleReady(client),
                "running server and connected client",
                isWorldLifecycleReady(client) ? "joined" : "not joined"
        );
        passed &= addAssertion(
                assertions,
                "server_arena_chunks_loaded",
                serverSetupResult != null && serverSetupResult.chunksLoaded(),
                "twelve full chunks",
                serverSetupResult == null
                        ? "missing"
                        : Boolean.toString(serverSetupResult.chunksLoaded())
        );
        DataProbe data = dataProbe == null ? DataProbe.missing() : dataProbe;
        passed &= addTagAssertions(assertions, registry.tags());
        passed &= addAssertion(
                assertions,
                "slitherite_loot_tables_exact",
                data.lootTablesExact(),
                expectedLootTableIds().toString(),
                data.lootTableIds().toString()
        );
        passed &= addAssertion(
                assertions,
                "slitherite_self_drops_exact",
                data.selfDropsExact(),
                expectedSelfDrops().toString(),
                data.selfDrops().toString()
        );
        passed &= addAssertion(
                assertions,
                "slitherite_double_slab_drops_x1_exact",
                data.doubleSlabDropsExact(),
                expectedDoubleSlabDrops().toString(),
                data.doubleSlabDrops().toString()
        );
        passed &= addAssertion(
                assertions,
                "slitherite_owned_recipes_exact",
                data.ownedRecipesExact(),
                recipeDescriptions(OWNED_RECIPES).toString(),
                data.ownedRecipes().toString()
        );
        passed &= addAssertion(
                assertions,
                "slitherite_owned_advancements_exact",
                data.ownedAdvancementsExact(),
                OWNED_ADVANCEMENTS.toString(),
                data.ownedAdvancements().toString()
        );
        passed &= addAssertion(
                assertions,
                "slitherite_related_recipes_recorded_not_owned",
                data.relatedRecipesExact(),
                recipeDescriptions(RELATED_RECIPES).toString(),
                data.relatedRecipes().toString()
        );
        PlacementInventory placements = serverSetupResult == null
                ? PlacementInventory.missing()
                : serverSetupResult.placements();
        passed &= addAssertion(
                assertions,
                "direct_block_item_placements_exact",
                placements.exact(),
                "17 accepted 1->0 BlockItem placements bound to registered blocks",
                placements.description()
        );
        BehaviorProbe behavior = behaviorProbe == null
                ? BehaviorProbe.missing()
                : behaviorProbe;
        passed &= addAssertion(
                assertions,
                "slitherite_button_pulse_reset_exact",
                behavior.buttonExact(),
                "powered=true;scheduled=true;elapsed>=20;reset=true",
                behavior.buttonDescription()
        );
        passed &= addAssertion(
                assertions,
                "slitherite_pressure_plate_entities_exact",
                behavior.pressurePlateExact(),
                "item=false;living=true;reset=true",
                behavior.pressurePlateDescription()
        );
        passed &= addAssertion(
                assertions,
                "initial_server_fixture_exact",
                behavior.snapshot().exact(),
                "17 exact Slitherite states on deterministic supports",
                behavior.snapshot().description()
        );

        String expectedWorldIdentity = WORLD_DISPLAY_NAME + ";" + WORLD_SEED + ";"
                + World.OVERWORLD.getValue();
        String actualWorldIdentity = serverSetupResult == null
                ? "missing"
                : serverSetupResult.worldDisplayName() + ";"
                        + serverSetupResult.worldSeed() + ";"
                        + serverSetupResult.dimensionId();
        passed &= addAssertion(
                assertions,
                "live_world_identity",
                expectedWorldIdentity.equals(actualWorldIdentity),
                expectedWorldIdentity,
                actualWorldIdentity
        );
        passed &= addAssertion(
                assertions,
                "forced_world_save",
                pendingSaveResult != null && pendingSaveResult.saved(),
                "true",
                pendingSaveResult == null
                        ? "not attempted"
                        : Boolean.toString(pendingSaveResult.saved())
        );
        passed &= addAssertion(
                assertions,
                "restart_fixture_persistence_exact",
                persistenceExact,
                "saved snapshot equals reopened snapshot",
                persistenceExact ? "exact" : "mismatch"
        );
        passed &= addAssertion(
                assertions,
                "restart_loaded_data_exact",
                reopenedDataExact,
                "same registry, tags, loot, recipes, and advancements",
                reopenedDataExact ? "exact" : "mismatch"
        );
        for (CapturePhase phase : CapturePhase.values()) {
            passed &= addCaptureAssertions(
                    assertions,
                    phase,
                    captureEvidence.get(phase)
            );
        }
        Path saveDirectory = saveDirectory(client);
        boolean saveDirectoryPresent = Files.isDirectory(
                saveDirectory,
                LinkOption.NOFOLLOW_LINKS
        ) && !Files.isSymbolicLink(saveDirectory);
        passed &= addAssertion(
                assertions,
                "isolated_save_directory_present",
                saveDirectoryPresent,
                WORLD_DIRECTORY_NAME,
                saveDirectoryPresent ? WORLD_DIRECTORY_NAME : "missing or linked"
        );
        if (!assertionNames(assertions).equals(ASSERTION_NAMES)) {
            throw new IllegalStateException("Slitherite assertion inventory drifted");
        }

        JsonObject report = new JsonObject();
        report.addProperty("schema", 3);
        report.addProperty("reference_id", REFERENCE_ID);
        report.addProperty("scenario", SCENARIO_ID);
        report.addProperty("lane", "fabric-1.21.1-original");
        report.addProperty("status", passed ? "passed" : "failed");
        report.addProperty("passed", passed);
        report.addProperty("client_ticks", clientTicks);
        report.addProperty("lifecycle_failure", lifecycleFailure);
        report.add("assertions", assertions);
        report.add("world", createWorldReport(client));
        report.add("artifacts", createArtifactsReport(artifacts));
        report.add("screenshots", createScreenshotsReport());
        report.add("slitherite", createSlitheriteReport(registry, data, placements, behavior));
        return report;
    }

    private boolean addTagAssertions(JsonArray assertions, TagSnapshot tags) {
        boolean passed = true;
        List<TagExpectation> expectations = List.of(
                new TagExpectation(
                        "tag:mineable/pickaxe",
                        BLOCK_IDS,
                        tags.pickaxeMineable()
                ),
                new TagExpectation(
                        "tag:needs_stone_tool",
                        List.of(),
                        tags.needsStoneTool()
                ),
                new TagExpectation("tag:block/slabs", slabIds(), tags.blockSlabs()),
                new TagExpectation("tag:item/slabs", slabIds(), tags.itemSlabs()),
                new TagExpectation("tag:block/stairs", stairIds(), tags.blockStairs()),
                new TagExpectation("tag:item/stairs", stairIds(), tags.itemStairs()),
                new TagExpectation("tag:block/walls", wallIds(), tags.blockWalls()),
                new TagExpectation("tag:item/walls", wallIds(), tags.itemWalls()),
                new TagExpectation(
                        "tag:block/stone_bricks",
                        stoneBrickIds(),
                        tags.blockStoneBricks()
                ),
                new TagExpectation(
                        "tag:block/stone_pressure_plates",
                        List.of(etherologyId("polished_slitherite_pressure_plate")),
                        tags.blockStonePressurePlates()
                ),
                new TagExpectation(
                        "tag:item/buttons",
                        List.of(),
                        tags.itemButtons()
                )
        );
        for (TagExpectation expectation : expectations) {
            passed &= addAssertion(
                    assertions,
                    expectation.assertionName(),
                    expectation.expected().equals(expectation.actual()),
                    expectation.expected().toString(),
                    expectation.actual().toString()
            );
        }
        return passed;
    }

    private boolean addCaptureAssertions(
            JsonArray assertions,
            CapturePhase phase,
            CaptureEvidence capture
    ) {
        boolean passed = true;
        passed &= addAssertion(
                assertions,
                "capture_mirror_exact:" + phase.id(),
                capture != null && capture.mirrorExact(),
                "client snapshot equals server snapshot",
                capture != null && capture.mirrorExact() ? "exact" : "missing or mismatch"
        );
        passed &= addAssertion(
                assertions,
                "capture_render_ready:" + phase.id(),
                capture != null && capture.renderReady(),
                "terrain and all 17 fixtures rendering-ready",
                capture != null && capture.renderReady() ? "ready" : "missing or not ready"
        );
        passed &= addAssertion(
                assertions,
                "capture_lighting_ready:" + phase.id(),
                capture != null && capture.lighting().exact(),
                LightingEvidence.expectedDescription(),
                capture == null ? "missing" : capture.lighting().description()
        );
        passed &= addAssertion(
                assertions,
                "capture_camera_exact:" + phase.id(),
                capture != null && capture.cameraExact(),
                expectedCameraPoseDescription(),
                capture == null ? "missing" : capture.cameraPose()
        );
        passed &= addAssertion(
                assertions,
                "capture_consecutive_stable_renders:" + phase.id(),
                capture != null
                        && capture.completedRenders() == REQUIRED_COMPLETED_RENDERS,
                Integer.toString(REQUIRED_COMPLETED_RENDERS),
                capture == null ? "0" : Integer.toString(capture.completedRenders())
        );
        passed &= addAssertion(
                assertions,
                "capture_framebuffer_dimensions:" + phase.id(),
                capture != null
                        && capture.width() == FRAMEBUFFER_WIDTH
                        && capture.height() == FRAMEBUFFER_HEIGHT,
                FRAMEBUFFER_WIDTH + "x" + FRAMEBUFFER_HEIGHT,
                capture == null ? "0x0" : capture.width() + "x" + capture.height()
        );
        ScreenshotResult screenshot = capture == null ? null : capture.screenshot();
        passed &= addAssertion(
                assertions,
                "native_screenshot_written:" + phase.id(),
                screenshot != null && screenshot.passed(),
                "one non-empty unedited 1920x1080 framebuffer PNG",
                screenshot == null
                        ? "missing"
                        : screenshot.size() + " bytes, sha256=" + screenshot.sha256()
        );
        return passed;
    }

    private JsonObject createWorldReport(MinecraftClient client) {
        JsonObject world = new JsonObject();
        world.addProperty("save_directory", WORLD_DIRECTORY_NAME);
        world.addProperty(
                "display_name",
                serverSetupResult == null ? "" : serverSetupResult.worldDisplayName()
        );
        world.addProperty(
                "seed",
                serverSetupResult == null ? Long.MIN_VALUE : serverSetupResult.worldSeed()
        );
        world.addProperty(
                "dimension",
                serverSetupResult == null ? "" : serverSetupResult.dimensionId()
        );
        world.addProperty("integrated", client.getServer() != null);
        world.addProperty("reopened", reopenedSnapshot != null);
        return world;
    }

    private JsonArray createArtifactsReport(List<ArtifactDigest> artifacts) {
        JsonArray array = new JsonArray();
        for (ArtifactDigest artifact : artifacts) {
            JsonObject entry = new JsonObject();
            entry.addProperty("mod_id", artifact.modId());
            entry.addProperty("origin_kind", artifact.originKind());
            entry.addProperty("file_name", artifact.fileName());
            entry.addProperty("size", artifact.size());
            entry.addProperty("sha256", artifact.sha256());
            array.add(entry);
        }
        return array;
    }

    private JsonArray createScreenshotsReport() {
        JsonArray array = new JsonArray();
        for (CapturePhase phase : CapturePhase.values()) {
            CaptureEvidence capture = captureEvidence.get(phase);
            if (capture == null
                    || capture.screenshot() == null
                    || !capture.screenshot().passed()) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("step", phase.id());
            entry.addProperty("file", "screenshots/" + phase.screenshotFileName());
            entry.addProperty("width", capture.width());
            entry.addProperty("height", capture.height());
            entry.addProperty("size", capture.screenshot().size());
            entry.addProperty("sha256", capture.screenshot().sha256());
            entry.addProperty("completed_render_count", capture.completedRenders());
            entry.addProperty("source", "minecraft-framebuffer");
            entry.addProperty("edited", false);
            array.add(entry);
        }
        return array;
    }

    private JsonObject createSlitheriteReport(
            RegistryProbe registry,
            DataProbe data,
            PlacementInventory placements,
            BehaviorProbe behavior
    ) {
        JsonObject slitherite = new JsonObject();
        slitherite.add("block_ids", identifierArray(BLOCK_IDS));
        slitherite.add("registry", stringArray(registry.descriptions()));
        slitherite.addProperty("aggregate_state_count", registry.aggregateStateCount());
        slitherite.add("canonical_resources", identifierArray(REQUIRED_RESOURCES));
        slitherite.add("tags", stringArray(registry.tags().descriptions()));
        slitherite.add("loot_tables", stringArray(data.lootTableIds()));
        slitherite.add("self_drops", stringMap(data.selfDrops()));
        slitherite.add("double_slab_drops", stringMap(data.doubleSlabDrops()));
        slitherite.add("owned_recipes", stringArray(data.ownedRecipes()));
        slitherite.add("owned_advancements", identifierArray(data.ownedAdvancements()));
        slitherite.add(
                "related_recipes_recorded_not_owned",
                stringArray(data.relatedRecipes())
        );
        slitherite.addProperty("placements", placements.description());
        slitherite.addProperty("button_behavior", behavior.buttonDescription());
        slitherite.addProperty(
                "pressure_plate_behavior",
                behavior.pressurePlateDescription()
        );
        slitherite.addProperty(
                "initial_snapshot",
                savedSnapshot == null ? "" : savedSnapshot.description()
        );
        slitherite.addProperty(
                "reopened_snapshot",
                reopenedSnapshot == null ? "" : reopenedSnapshot.description()
        );
        slitherite.addProperty("persistence_exact", persistenceExact);
        slitherite.addProperty("reopened_data_exact", reopenedDataExact);
        slitherite.addProperty(
                "required_stable_renders",
                REQUIRED_COMPLETED_RENDERS
        );
        slitherite.addProperty(
                "required_lighting_ready_client_ticks",
                REQUIRED_LIGHTING_READY_CLIENT_TICKS
        );
        return slitherite;
    }

    private static JsonArray identifierArray(List<Identifier> values) {
        JsonArray array = new JsonArray();
        values.forEach(value -> array.add(value.toString()));
        return array;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonObject stringMap(Map<String, String> values) {
        JsonObject object = new JsonObject();
        values.forEach(object::addProperty);
        return object;
    }

    private void transition(Stage nextStage) {
        stage = nextStage;
        stageClientTicks = 0;
    }

    private void recordServerFailure(RuntimeException exception) {
        LOGGER.error("Original Slitherite server operation failed", exception);
        serverFailure = exception.getClass().getSimpleName()
                + ": " + exception.getMessage();
    }

    private static boolean addAssertion(
            JsonArray assertions,
            String name,
            boolean passed,
            String expected,
            String actual
    ) {
        JsonObject assertion = new JsonObject();
        assertion.addProperty("name", name);
        assertion.addProperty("passed", passed);
        assertion.addProperty("expected", expected);
        assertion.addProperty("actual", actual);
        assertions.add(assertion);
        return passed;
    }

    private static List<String> assertionNames(JsonArray assertions) {
        List<String> names = new ArrayList<>();
        assertions.forEach(value -> names.add(
                value.getAsJsonObject().get("name").getAsString()
        ));
        return List.copyOf(names);
    }

    private List<Identifier> findMissingResources(MinecraftClient client) {
        List<Identifier> missing = new ArrayList<>();
        for (Identifier resource : REQUIRED_RESOURCES) {
            if (client.getResourceManager().getResource(resource).isEmpty()) {
                missing.add(resource);
            }
        }
        return List.copyOf(missing);
    }

    private boolean hasBaseRegistry() {
        return FIXTURES.stream().allMatch(fixture ->
                Registries.BLOCK.containsId(fixture.id())
                        && Registries.ITEM.containsId(fixture.id()));
    }

    private static FixtureSnapshot captureSnapshot(WorldView world) {
        List<BlockObservation> observations = new ArrayList<>();
        for (BlockFixture fixture : FIXTURES) {
            BlockState state = world.getBlockState(fixture.position());
            BlockState support = world.getBlockState(fixture.supportPosition());
            observations.add(new BlockObservation(
                    fixture.id().toString(),
                    Registries.BLOCK.getId(state.getBlock()).toString(),
                    stateDescription(state),
                    Registries.BLOCK.getId(support.getBlock()).toString()
            ));
        }
        return new FixtureSnapshot(List.copyOf(observations));
    }

    private static String expectedCameraPoseDescription() {
        return "first_person=true;x=" + CAMERA_X
                + ";y=" + CAMERA_Y
                + ";z=" + CAMERA_Z
                + ";yaw=" + CAMERA_YAW
                + ";pitch=" + CAMERA_PITCH
                + ";on_ground=true;tolerance=" + CAMERA_POSE_TOLERANCE;
    }

    private static String cameraPoseDescription(MinecraftClient client) {
        if (client.player == null) return "missing player";
        return "first_person=" + client.options.getPerspective().isFirstPerson()
                + ";x=" + client.player.getX()
                + ";y=" + client.player.getY()
                + ";z=" + client.player.getZ()
                + ";yaw=" + MathHelper.wrapDegrees(client.player.getYaw())
                + ";pitch=" + client.player.getPitch()
                + ";on_ground=" + client.player.isOnGround();
    }

    private static String stateDescription(BlockState state) {
        return BlockArgumentParser.stringifyBlockState(state);
    }

    private static Map<String, String> defaultProperties(BlockState state) {
        Map<String, String> properties = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), propertyValueName(state, property));
        }
        return Collections.unmodifiableMap(properties);
    }

    private static <T extends Comparable<T>> String propertyValueName(
            BlockState state,
            Property<T> property
    ) {
        return property.name(state.get(property));
    }

    private static Block requiredBlock(Identifier id) {
        return Registries.BLOCK.getOrEmpty(id).orElseThrow(
                () -> new IllegalStateException("Missing block " + id)
        );
    }

    private static Item requiredItem(Identifier id) {
        return Registries.ITEM.getOrEmpty(id).orElseThrow(
                () -> new IllegalStateException("Missing item " + id)
        );
    }

    private static Path saveDirectory(MinecraftClient client) {
        return client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
    }

    private static BlockFixture fixture(
            String path,
            Class<? extends Block> blockClass,
            int index,
            Map<String, String> defaultProperties,
            int stateCount
    ) {
        int x = index < 9 ? -8 + index * 2 : -7 + (index - 9) * 2;
        int z = index < 9 ? 2 : 6;
        BlockPos position = new BlockPos(x, ARENA_FLOOR_Y + 2, z);
        boolean button = path.endsWith("_button");
        BlockPos support = button ? position.south() : position.down();
        return new BlockFixture(
                etherologyId(path),
                position,
                support,
                blockClass,
                Map.copyOf(defaultProperties),
                stateCount,
                path.endsWith("_slab"),
                path.endsWith("_stairs"),
                path.endsWith("_wall"),
                path.equals("polished_slitherite_bricks")
                        || path.equals("chiseled_polished_slitherite_bricks")
                        || path.equals("cracked_polished_slitherite_bricks"),
                button,
                path.endsWith("_pressure_plate")
        );
    }

    private static Map<String, String> stairsState() {
        return Map.of(
                "facing", "north",
                "half", "bottom",
                "shape", "straight",
                "waterlogged", "false"
        );
    }

    private static Map<String, String> slabState() {
        return Map.of("type", "bottom", "waterlogged", "false");
    }

    private static Map<String, String> wallState() {
        return Map.of(
                "east", "none",
                "north", "none",
                "south", "none",
                "up", "true",
                "waterlogged", "false",
                "west", "none"
        );
    }

    private static Map<String, String> buttonState() {
        return Map.of(
                "face", "wall",
                "facing", "north",
                "powered", "false"
        );
    }

    private static BlockFixture fixtureByPath(String path) {
        Identifier id = etherologyId(path);
        return FIXTURES.stream()
                .filter(fixture -> fixture.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing fixture " + id));
    }

    private static Identifier etherologyId(String path) {
        return Identifier.of("etherology", path);
    }

    private static Identifier identifier(String value) {
        return Identifier.of(value);
    }

    private static RecipeExpectation recipe(
            String path,
            String type,
            String result,
            int resultCount
    ) {
        return new RecipeExpectation(
                etherologyId(path),
                identifier(type),
                identifier(result),
                resultCount
        );
    }

    private static List<Identifier> slabIds() {
        return FIXTURES.stream()
                .filter(BlockFixture::slab)
                .map(BlockFixture::id)
                .toList();
    }

    private static List<Identifier> stairIds() {
        return FIXTURES.stream()
                .filter(BlockFixture::stairs)
                .map(BlockFixture::id)
                .toList();
    }

    private static List<Identifier> wallIds() {
        return FIXTURES.stream()
                .filter(BlockFixture::wall)
                .map(BlockFixture::id)
                .toList();
    }

    private static List<Identifier> stoneBrickIds() {
        return FIXTURES.stream()
                .filter(BlockFixture::stoneBrick)
                .map(BlockFixture::id)
                .toList();
    }

    private static List<BlockPos> createSkyLightSamplePositions() {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(CAMERA_BLOCK_POS);
        for (BlockFixture fixture : FIXTURES) {
            positions.add(fixture.position().up());
        }
        return List.copyOf(positions);
    }

    private static List<Identifier> createRequiredResources() {
        List<Identifier> resources = new ArrayList<>();
        resources.add(Identifier.of("minecraft", "texts/splashes.txt"));
        for (BlockFixture fixture : FIXTURES) {
            resources.add(etherologyId(
                    "blockstates/" + fixture.id().getPath() + ".json"
            ));
        }
        for (String path : List.of(
                "chiseled_polished_slitherite",
                "chiseled_polished_slitherite_bricks",
                "cracked_polished_slitherite_bricks",
                "polished_slitherite",
                "polished_slitherite_brick_slab",
                "polished_slitherite_brick_slab_top",
                "polished_slitherite_brick_stairs",
                "polished_slitherite_brick_stairs_inner",
                "polished_slitherite_brick_stairs_outer",
                "polished_slitherite_brick_wall_inventory",
                "polished_slitherite_brick_wall_post",
                "polished_slitherite_brick_wall_side",
                "polished_slitherite_brick_wall_side_tall",
                "polished_slitherite_bricks",
                "polished_slitherite_button",
                "polished_slitherite_button_inventory",
                "polished_slitherite_button_pressed",
                "polished_slitherite_pressure_plate",
                "polished_slitherite_pressure_plate_down",
                "polished_slitherite_slab",
                "polished_slitherite_slab_top",
                "polished_slitherite_stairs",
                "polished_slitherite_stairs_inner",
                "polished_slitherite_stairs_outer",
                "polished_slitherite_wall_inventory",
                "polished_slitherite_wall_post",
                "polished_slitherite_wall_side",
                "polished_slitherite_wall_side_tall",
                "slitherite",
                "slitherite_slab",
                "slitherite_slab_top",
                "slitherite_stairs",
                "slitherite_stairs_inner",
                "slitherite_stairs_outer",
                "slitherite_wall_inventory",
                "slitherite_wall_post",
                "slitherite_wall_side",
                "slitherite_wall_side_tall"
        )) {
            resources.add(etherologyId("models/block/" + path + ".json"));
        }
        for (BlockFixture fixture : FIXTURES) {
            resources.add(etherologyId(
                    "models/item/" + fixture.id().getPath() + ".json"
            ));
        }
        for (String path : List.of(
                "chiseled_polished_slitherite",
                "chiseled_polished_slitherite_bricks_side",
                "chiseled_polished_slitherite_bricks_top",
                "cracked_polished_slitherite_bricks",
                "polished_slitherite",
                "polished_slitherite_bricks",
                "slitherite"
        )) {
            resources.add(etherologyId("textures/block/" + path + ".png"));
        }
        if (resources.size() != EXPECTED_VISUAL_ASSET_COUNT + 1) {
            throw new IllegalStateException("Slitherite visual resource count drifted");
        }
        return List.copyOf(resources);
    }

    private static List<RecipeExpectation> createOwnedRecipes() {
        return List.of(
                recipe(
                        "chiseled_polished_slitherite",
                        "minecraft:crafting",
                        "etherology:chiseled_polished_slitherite",
                        1
                ),
                recipe(
                        "chiseled_polished_slitherite_bricks",
                        "minecraft:crafting",
                        "etherology:chiseled_polished_slitherite_bricks",
                        1
                ),
                recipe(
                        "chiseled_polished_slitherite_bricks_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:chiseled_polished_slitherite_bricks",
                        1
                ),
                recipe(
                        "chiseled_polished_slitherite_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:chiseled_polished_slitherite",
                        1
                ),
                recipe(
                        "cracked_polished_slitherite_bricks",
                        "minecraft:smelting",
                        "etherology:cracked_polished_slitherite_bricks",
                        1
                ),
                recipe(
                        "polished_slitherite",
                        "minecraft:crafting",
                        "etherology:polished_slitherite",
                        4
                ),
                recipe(
                        "polished_slitherite_brick_slab",
                        "minecraft:crafting",
                        "etherology:polished_slitherite_brick_slab",
                        6
                ),
                recipe(
                        "polished_slitherite_brick_slab_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:polished_slitherite_brick_slab",
                        2
                ),
                recipe(
                        "polished_slitherite_brick_stairs",
                        "minecraft:crafting",
                        "etherology:polished_slitherite_brick_stairs",
                        4
                ),
                recipe(
                        "polished_slitherite_brick_stairs_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:polished_slitherite_brick_stairs",
                        1
                ),
                recipe(
                        "polished_slitherite_brick_wall",
                        "minecraft:crafting",
                        "etherology:polished_slitherite_brick_wall",
                        6
                ),
                recipe(
                        "polished_slitherite_brick_wall_from_"
                                + "polished_slitherite_bricks_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:polished_slitherite_brick_wall",
                        1
                ),
                recipe(
                        "polished_slitherite_bricks",
                        "minecraft:crafting",
                        "etherology:polished_slitherite_bricks",
                        4
                ),
                recipe(
                        "polished_slitherite_bricks_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:polished_slitherite_bricks",
                        1
                ),
                recipe(
                        "polished_slitherite_button",
                        "minecraft:crafting",
                        "etherology:polished_slitherite_button",
                        1
                ),
                recipe(
                        "polished_slitherite_from_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:polished_slitherite",
                        1
                ),
                recipe(
                        "polished_slitherite_pressure_plate",
                        "minecraft:crafting",
                        "etherology:polished_slitherite_pressure_plate",
                        1
                ),
                recipe(
                        "polished_slitherite_slab",
                        "minecraft:crafting",
                        "etherology:polished_slitherite_slab",
                        6
                ),
                recipe(
                        "polished_slitherite_slab_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:polished_slitherite_slab",
                        2
                ),
                recipe(
                        "polished_slitherite_stairs",
                        "minecraft:crafting",
                        "etherology:polished_slitherite_stairs",
                        4
                ),
                recipe(
                        "polished_slitherite_stairs_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:polished_slitherite_stairs",
                        1
                ),
                recipe(
                        "polished_slitherite_wall",
                        "minecraft:crafting",
                        "etherology:polished_slitherite_wall",
                        6
                ),
                recipe(
                        "polished_slitherite_wall_from_"
                                + "polished_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:polished_slitherite_wall",
                        1
                ),
                recipe(
                        "slitherite_slab",
                        "minecraft:crafting",
                        "etherology:slitherite_slab",
                        6
                ),
                recipe(
                        "slitherite_slab_from_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:slitherite_slab",
                        2
                ),
                recipe(
                        "slitherite_stairs",
                        "minecraft:crafting",
                        "etherology:slitherite_stairs",
                        4
                ),
                recipe(
                        "slitherite_stairs_from_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:slitherite_stairs",
                        1
                ),
                recipe(
                        "slitherite_wall",
                        "minecraft:crafting",
                        "etherology:slitherite_wall",
                        6
                ),
                recipe(
                        "slitherite_wall_from_slitherite_stonecutting",
                        "minecraft:stonecutting",
                        "etherology:slitherite_wall",
                        1
                )
        );
    }

    private static List<Identifier> createOwnedAdvancements() {
        return List.of(
                etherologyId("recipes/building_blocks/chiseled_polished_slitherite"),
                etherologyId(
                        "recipes/building_blocks/chiseled_polished_slitherite_bricks"
                ),
                etherologyId(
                        "recipes/building_blocks/chiseled_polished_slitherite_bricks_"
                                + "from_polished_slitherite_bricks_stonecutting"
                ),
                etherologyId(
                        "recipes/building_blocks/chiseled_polished_slitherite_"
                                + "from_polished_slitherite_stonecutting"
                ),
                etherologyId(
                        "recipes/building_blocks/cracked_polished_slitherite_bricks"
                ),
                etherologyId("recipes/building_blocks/polished_slitherite"),
                etherologyId("recipes/building_blocks/polished_slitherite_brick_slab"),
                etherologyId(
                        "recipes/building_blocks/polished_slitherite_brick_slab_"
                                + "from_polished_slitherite_bricks_stonecutting"
                ),
                etherologyId("recipes/building_blocks/polished_slitherite_brick_stairs"),
                etherologyId(
                        "recipes/building_blocks/polished_slitherite_brick_stairs_"
                                + "from_polished_slitherite_bricks_stonecutting"
                ),
                etherologyId("recipes/building_blocks/polished_slitherite_bricks"),
                etherologyId(
                        "recipes/building_blocks/polished_slitherite_bricks_"
                                + "from_polished_slitherite_stonecutting"
                ),
                etherologyId(
                        "recipes/building_blocks/polished_slitherite_"
                                + "from_slitherite_stonecutting"
                ),
                etherologyId("recipes/building_blocks/polished_slitherite_slab"),
                etherologyId(
                        "recipes/building_blocks/polished_slitherite_slab_"
                                + "from_polished_slitherite_stonecutting"
                ),
                etherologyId("recipes/building_blocks/polished_slitherite_stairs"),
                etherologyId(
                        "recipes/building_blocks/polished_slitherite_stairs_"
                                + "from_polished_slitherite_stonecutting"
                ),
                etherologyId("recipes/building_blocks/slitherite_slab"),
                etherologyId(
                        "recipes/building_blocks/slitherite_slab_"
                                + "from_slitherite_stonecutting"
                ),
                etherologyId("recipes/building_blocks/slitherite_stairs"),
                etherologyId(
                        "recipes/building_blocks/slitherite_stairs_"
                                + "from_slitherite_stonecutting"
                ),
                etherologyId("recipes/decorations/polished_slitherite_brick_wall"),
                etherologyId(
                        "recipes/decorations/polished_slitherite_brick_wall_"
                                + "from_polished_slitherite_bricks_stonecutting"
                ),
                etherologyId("recipes/decorations/polished_slitherite_wall"),
                etherologyId(
                        "recipes/decorations/polished_slitherite_wall_"
                                + "from_polished_slitherite_stonecutting"
                ),
                etherologyId("recipes/decorations/slitherite_wall"),
                etherologyId(
                        "recipes/decorations/slitherite_wall_"
                                + "from_slitherite_stonecutting"
                ),
                etherologyId("recipes/redstone/polished_slitherite_button"),
                etherologyId("recipes/redstone/polished_slitherite_pressure_plate")
        );
    }

    private static List<String> createAssertionNames() {
        List<String> names = new ArrayList<>();
        names.add("fabric_mod_loaded:etherology");
        for (BlockFixture fixture : FIXTURES) {
            names.add("registry:block:" + fixture.id());
            names.add("registry:item:" + fixture.id());
            names.add("runtime:block_class:" + fixture.id());
            names.add("runtime:block_item_class:" + fixture.id());
            names.add("block_item_mapping:" + fixture.id());
            names.add("default_state:" + fixture.id());
            names.add("state_count:" + fixture.id());
            names.add("default_state_network_id:" + fixture.id());
        }
        names.add("slitherite_canonical_resources_exact");
        names.add("slitherite_state_network_ids_exact");
        names.add("packaged_root_jar:etherology");
        names.add("packaged_root_jar:" + HARNESS_MOD_ID);
        names.add("integrated_world_joined");
        names.add("server_arena_chunks_loaded");
        names.add("tag:mineable/pickaxe");
        names.add("tag:needs_stone_tool");
        names.add("tag:block/slabs");
        names.add("tag:item/slabs");
        names.add("tag:block/stairs");
        names.add("tag:item/stairs");
        names.add("tag:block/walls");
        names.add("tag:item/walls");
        names.add("tag:block/stone_bricks");
        names.add("tag:block/stone_pressure_plates");
        names.add("tag:item/buttons");
        names.add("slitherite_loot_tables_exact");
        names.add("slitherite_self_drops_exact");
        names.add("slitherite_double_slab_drops_x1_exact");
        names.add("slitherite_owned_recipes_exact");
        names.add("slitherite_owned_advancements_exact");
        names.add("slitherite_related_recipes_recorded_not_owned");
        names.add("direct_block_item_placements_exact");
        names.add("slitherite_button_pulse_reset_exact");
        names.add("slitherite_pressure_plate_entities_exact");
        names.add("initial_server_fixture_exact");
        names.add("live_world_identity");
        names.add("forced_world_save");
        names.add("restart_fixture_persistence_exact");
        names.add("restart_loaded_data_exact");
        for (CapturePhase phase : CapturePhase.values()) {
            names.add("capture_mirror_exact:" + phase.id());
            names.add("capture_render_ready:" + phase.id());
            names.add("capture_lighting_ready:" + phase.id());
            names.add("capture_camera_exact:" + phase.id());
            names.add("capture_consecutive_stable_renders:" + phase.id());
            names.add("capture_framebuffer_dimensions:" + phase.id());
            names.add("native_screenshot_written:" + phase.id());
        }
        names.add("isolated_save_directory_present");
        return List.copyOf(names);
    }

    private static List<String> recipeDescriptions(
            List<RecipeExpectation> expectations
    ) {
        return expectations.stream()
                .map(RecipeExpectation::description)
                .toList();
    }

    private static List<String> expectedLootTableIds() {
        return BLOCK_IDS.stream()
                .map(id -> "etherology:blocks/" + id.getPath())
                .sorted()
                .toList();
    }

    private static Map<String, String> expectedSelfDrops() {
        Map<String, String> expected = new TreeMap<>();
        for (Identifier id : BLOCK_IDS) {
            expected.put(id.toString(), id + "x1");
        }
        return Collections.unmodifiableMap(expected);
    }

    private static Map<String, String> expectedDoubleSlabDrops() {
        Map<String, String> expected = new TreeMap<>();
        for (Identifier id : slabIds()) {
            expected.put(id.toString(), id + "x1");
        }
        return Collections.unmodifiableMap(expected);
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_SERVER_SETUP,
        WAITING_FOR_BEHAVIOR,
        WAITING_FOR_CLIENT_MIRROR,
        WAITING_FOR_RENDERS,
        CAPTURING,
        SAVING_WORLD,
        DISCONNECTING,
        WAITING_FOR_RESTART_TITLE,
        RESTARTING_WORLD,
        WAITING_FOR_RESTART_WORLD,
        WAITING_FOR_RESTART_INSPECTION,
        COMPLETE
    }

    private enum CapturePhase {
        INITIAL("initial", INITIAL_SCREENSHOT_FILE_NAME),
        REOPENED("reopened", REOPENED_SCREENSHOT_FILE_NAME);

        private final String id;
        private final String screenshotFileName;

        CapturePhase(String id, String screenshotFileName) {
            this.id = id;
            this.screenshotFileName = screenshotFileName;
        }

        private String id() {
            return id;
        }

        private String screenshotFileName() {
            return screenshotFileName;
        }
    }

    private enum BehaviorPhase {
        WAITING_FOR_ITEM,
        WAITING_FOR_LIVING,
        WAITING_FOR_RESET
    }

    private record BlockFixture(
            Identifier id,
            BlockPos position,
            BlockPos supportPosition,
            Class<? extends Block> blockClass,
            Map<String, String> defaultProperties,
            int stateCount,
            boolean slab,
            boolean stairs,
            boolean wall,
            boolean stoneBrick,
            boolean button,
            boolean pressurePlate
    ) {
    }

    private record RecipeExpectation(
            Identifier id,
            Identifier typeId,
            Identifier resultId,
            int resultCount
    ) {

        private String description() {
            return id + "=" + typeId + "->" + resultId + "x" + resultCount;
        }
    }

    private record TagExpectation(
            String assertionName,
            List<Identifier> expected,
            List<Identifier> actual
    ) {
    }

    private record PlacementEvidence(
            String actionResult,
            boolean accepted,
            int beforeCount,
            int afterCount,
            boolean blockItemMapping,
            String placedId,
            String placedState
    ) {
    }

    private record PlacementInventory(Map<String, PlacementEvidence> entries) {

        private static PlacementInventory missing() {
            return new PlacementInventory(Map.of());
        }

        private boolean exact() {
            if (entries.size() != FIXTURES.size()) return false;
            for (BlockFixture fixture : FIXTURES) {
                PlacementEvidence entry = entries.get(fixture.id().toString());
                if (entry == null
                        || !entry.accepted()
                        || entry.beforeCount() != 1
                        || entry.afterCount() != 0
                        || !entry.blockItemMapping()
                        || !fixture.id().toString().equals(entry.placedId())
                        || !entry.placedState().startsWith(fixture.id().toString())) {
                    return false;
                }
            }
            return true;
        }

        private String description() {
            return entries.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(";"));
        }
    }

    private record RegistryEntry(
            String id,
            boolean blockPresent,
            boolean itemPresent,
            String blockClass,
            String itemClass,
            boolean blockItemMapping,
            Map<String, String> defaultProperties,
            int stateCount,
            int defaultStateNetworkId,
            int uniqueNetworkIdCount,
            boolean networkIdsExact
    ) {

        private static RegistryEntry missing(String id) {
            return new RegistryEntry(
                    id,
                    false,
                    false,
                    "missing",
                    "missing",
                    false,
                    Map.of(),
                    0,
                    -1,
                    0,
                    false
            );
        }

        private String description() {
            return id + "=block_class:" + blockClass
                    + ",item_class:" + itemClass
                    + ",default:" + defaultProperties
                    + ",states:" + stateCount
                    + ",default_raw_id:" + defaultStateNetworkId
                    + ",raw_ids:" + uniqueNetworkIdCount;
        }
    }

    private record RegistryProbe(
            Map<String, RegistryEntry> entries,
            TagSnapshot tags,
            int aggregateStateCount,
            int aggregateUniqueNetworkIdCount,
            boolean networkIdsExact,
            List<String> descriptions
    ) {

        private static RegistryProbe capture() {
            Map<String, RegistryEntry> entries = new LinkedHashMap<>();
            Set<Integer> aggregateRawIds = new HashSet<>();
            int stateCount = 0;
            boolean allNetworkIdsExact = true;
            List<String> descriptions = new ArrayList<>();
            for (BlockFixture fixture : FIXTURES) {
                boolean blockPresent = Registries.BLOCK.containsId(fixture.id());
                boolean itemPresent = Registries.ITEM.containsId(fixture.id());
                if (!blockPresent || !itemPresent) {
                    RegistryEntry missing = RegistryEntry.missing(
                            fixture.id().toString()
                    );
                    entries.put(fixture.id().toString(), missing);
                    descriptions.add(missing.description());
                    allNetworkIdsExact = false;
                    continue;
                }

                Block block = Registries.BLOCK.get(fixture.id());
                Item item = Registries.ITEM.get(fixture.id());
                Set<Integer> blockRawIds = new HashSet<>();
                boolean blockNetworkIdsExact = true;
                for (BlockState state : block.getStateManager().getStates()) {
                    int rawId = Block.STATE_IDS.getRawId(state);
                    blockNetworkIdsExact &= rawId >= 0;
                    blockRawIds.add(rawId);
                    aggregateRawIds.add(rawId);
                }
                int defaultRawId = Block.STATE_IDS.getRawId(block.getDefaultState());
                blockNetworkIdsExact &= defaultRawId >= 0
                        && blockRawIds.size() == fixture.stateCount();
                RegistryEntry entry = new RegistryEntry(
                        fixture.id().toString(),
                        true,
                        true,
                        block.getClass().getName(),
                        item.getClass().getName(),
                        item instanceof BlockItem blockItem
                                && blockItem.getBlock() == block,
                        defaultProperties(block.getDefaultState()),
                        block.getStateManager().getStates().size(),
                        defaultRawId,
                        blockRawIds.size(),
                        blockNetworkIdsExact
                );
                entries.put(fixture.id().toString(), entry);
                descriptions.add(entry.description());
                stateCount += entry.stateCount();
                allNetworkIdsExact &= entry.networkIdsExact();
            }
            allNetworkIdsExact &= stateCount == EXPECTED_AGGREGATE_STATE_COUNT
                    && aggregateRawIds.size() == EXPECTED_AGGREGATE_STATE_COUNT;
            return new RegistryProbe(
                    Collections.unmodifiableMap(new LinkedHashMap<>(entries)),
                    TagSnapshot.capture(),
                    stateCount,
                    aggregateRawIds.size(),
                    allNetworkIdsExact,
                    List.copyOf(descriptions)
            );
        }

        private static RegistryProbe missing() {
            return new RegistryProbe(
                    Map.of(),
                    TagSnapshot.missing(),
                    0,
                    0,
                    false,
                    List.of()
            );
        }

        private boolean blockItemRegistryExact() {
            if (entries.size() != FIXTURES.size()
                    || aggregateStateCount != EXPECTED_AGGREGATE_STATE_COUNT
                    || !networkIdsExact) {
                return false;
            }
            for (BlockFixture fixture : FIXTURES) {
                RegistryEntry entry = entries.get(fixture.id().toString());
                if (entry == null
                        || !entry.blockPresent()
                        || !entry.itemPresent()
                        || !fixture.blockClass().getName().equals(entry.blockClass())
                        || !BlockItem.class.getName().equals(entry.itemClass())
                        || !entry.blockItemMapping()
                        || !fixture.defaultProperties().equals(
                                entry.defaultProperties()
                        )
                        || fixture.stateCount() != entry.stateCount()
                        || !entry.networkIdsExact()) {
                    return false;
                }
            }
            return true;
        }

        private boolean exact() {
            return blockItemRegistryExact() && tags.exact();
        }

        private String networkIdDescription() {
            return aggregateUniqueNetworkIdCount
                    + " unique non-negative raw ids";
        }
    }

    private record TagSnapshot(
            List<Identifier> pickaxeMineable,
            List<Identifier> needsStoneTool,
            List<Identifier> blockSlabs,
            List<Identifier> itemSlabs,
            List<Identifier> blockStairs,
            List<Identifier> itemStairs,
            List<Identifier> blockWalls,
            List<Identifier> itemWalls,
            List<Identifier> blockStoneBricks,
            List<Identifier> blockStonePressurePlates,
            List<Identifier> itemButtons
    ) {

        private static TagSnapshot capture() {
            List<Identifier> pickaxe = new ArrayList<>();
            List<Identifier> needsStone = new ArrayList<>();
            List<Identifier> blockSlabs = new ArrayList<>();
            List<Identifier> itemSlabs = new ArrayList<>();
            List<Identifier> blockStairs = new ArrayList<>();
            List<Identifier> itemStairs = new ArrayList<>();
            List<Identifier> blockWalls = new ArrayList<>();
            List<Identifier> itemWalls = new ArrayList<>();
            List<Identifier> stoneBricks = new ArrayList<>();
            List<Identifier> pressurePlates = new ArrayList<>();
            List<Identifier> buttons = new ArrayList<>();
            for (BlockFixture fixture : FIXTURES) {
                Block block = requiredBlock(fixture.id());
                BlockState state = block.getDefaultState();
                ItemStack stack = requiredItem(fixture.id()).getDefaultStack();
                if (state.isIn(BlockTags.PICKAXE_MINEABLE)) pickaxe.add(fixture.id());
                if (state.isIn(BlockTags.NEEDS_STONE_TOOL)) needsStone.add(fixture.id());
                if (state.isIn(BlockTags.SLABS)) blockSlabs.add(fixture.id());
                if (stack.isIn(ItemTags.SLABS)) itemSlabs.add(fixture.id());
                if (state.isIn(BlockTags.STAIRS)) blockStairs.add(fixture.id());
                if (stack.isIn(ItemTags.STAIRS)) itemStairs.add(fixture.id());
                if (state.isIn(BlockTags.WALLS)) blockWalls.add(fixture.id());
                if (stack.isIn(ItemTags.WALLS)) itemWalls.add(fixture.id());
                if (state.isIn(BlockTags.STONE_BRICKS)) stoneBricks.add(fixture.id());
                if (state.isIn(BlockTags.STONE_PRESSURE_PLATES)) {
                    pressurePlates.add(fixture.id());
                }
                if (stack.isIn(ItemTags.BUTTONS)) buttons.add(fixture.id());
            }
            return new TagSnapshot(
                    List.copyOf(pickaxe),
                    List.copyOf(needsStone),
                    List.copyOf(blockSlabs),
                    List.copyOf(itemSlabs),
                    List.copyOf(blockStairs),
                    List.copyOf(itemStairs),
                    List.copyOf(blockWalls),
                    List.copyOf(itemWalls),
                    List.copyOf(stoneBricks),
                    List.copyOf(pressurePlates),
                    List.copyOf(buttons)
            );
        }

        private static TagSnapshot missing() {
            return new TagSnapshot(
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of()
            );
        }

        private boolean exact() {
            return pickaxeMineable.equals(BLOCK_IDS)
                    && needsStoneTool.isEmpty()
                    && blockSlabs.equals(slabIds())
                    && itemSlabs.equals(slabIds())
                    && blockStairs.equals(stairIds())
                    && itemStairs.equals(stairIds())
                    && blockWalls.equals(wallIds())
                    && itemWalls.equals(wallIds())
                    && blockStoneBricks.equals(stoneBrickIds())
                    && blockStonePressurePlates.equals(List.of(
                            etherologyId("polished_slitherite_pressure_plate")
                    ))
                    && itemButtons.isEmpty();
        }

        private List<String> descriptions() {
            return List.of(
                    "mineable/pickaxe=" + pickaxeMineable,
                    "needs_stone_tool=" + needsStoneTool,
                    "block/slabs=" + blockSlabs,
                    "item/slabs=" + itemSlabs,
                    "block/stairs=" + blockStairs,
                    "item/stairs=" + itemStairs,
                    "block/walls=" + blockWalls,
                    "item/walls=" + itemWalls,
                    "block/stone_bricks=" + blockStoneBricks,
                    "block/stone_pressure_plates=" + blockStonePressurePlates,
                    "item/buttons=" + itemButtons
            );
        }
    }

    private record DataProbe(
            List<String> lootTableIds,
            Map<String, String> selfDrops,
            Map<String, String> doubleSlabDrops,
            List<String> ownedRecipes,
            List<Identifier> ownedAdvancements,
            List<String> relatedRecipes,
            String failure
    ) {

        private static DataProbe capture(
                IntegratedServer server,
                ServerWorld world,
                ServerPlayerEntity player
        ) {
            try {
                Set<String> expectedLootIds = Set.copyOf(expectedLootTableIds());
                List<String> lootIds = server.getReloadableRegistries()
                        .getIds(RegistryKeys.LOOT_TABLE)
                        .stream()
                        .map(Identifier::toString)
                        .filter(expectedLootIds::contains)
                        .sorted()
                        .toList();

                ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
                Map<String, String> drops = new TreeMap<>();
                Map<String, String> doubleDrops = new TreeMap<>();
                for (BlockFixture fixture : FIXTURES) {
                    Block block = requiredBlock(fixture.id());
                    drops.put(
                            fixture.id().toString(),
                            generateLoot(
                                    world,
                                    player,
                                    block.getDefaultState(),
                                    pickaxe
                            )
                    );
                    if (fixture.slab()) {
                        BlockState doubleState = block.getDefaultState().with(
                                Properties.SLAB_TYPE,
                                SlabType.DOUBLE
                        );
                        doubleDrops.put(
                                fixture.id().toString(),
                                generateLoot(world, player, doubleState, pickaxe)
                        );
                    }
                }
                world.setBlockState(
                        LOOT_PROBE_POS,
                        Blocks.AIR.getDefaultState(),
                        Block.NOTIFY_ALL
                );

                List<String> recipes = inspectRecipes(server, OWNED_RECIPES);
                List<Identifier> advancements = server.getAdvancementLoader()
                        .getAdvancements()
                        .stream()
                        .map(entry -> entry.id())
                        .filter(id -> id.getNamespace().equals("etherology"))
                        .filter(id -> id.getPath().contains("slitherite"))
                        .sorted()
                        .toList();
                List<String> related = inspectRecipes(server, RELATED_RECIPES);
                return new DataProbe(
                        lootIds,
                        Collections.unmodifiableMap(new TreeMap<>(drops)),
                        Collections.unmodifiableMap(new TreeMap<>(doubleDrops)),
                        recipes,
                        advancements,
                        related,
                        ""
                );
            } catch (RuntimeException exception) {
                return new DataProbe(
                        List.of(),
                        Map.of(),
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        exception.getClass().getSimpleName()
                                + ": " + exception.getMessage()
                );
            }
        }

        private static DataProbe missing() {
            return new DataProbe(
                    List.of(),
                    Map.of(),
                    Map.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "not captured"
            );
        }

        private static List<String> inspectRecipes(
                IntegratedServer server,
                List<RecipeExpectation> expectations
        ) {
            List<String> descriptions = new ArrayList<>();
            for (RecipeExpectation expectation : expectations) {
                RecipeEntry<?> entry = server.getRecipeManager()
                        .get(expectation.id())
                        .orElseThrow(() -> new IllegalStateException(
                                "Missing recipe " + expectation.id()
                        ));
                Identifier typeId = Registries.RECIPE_TYPE.getId(
                        entry.value().getType()
                );
                ItemStack result = entry.value().getResult(
                        server.getRegistryManager()
                );
                Identifier resultId = Registries.ITEM.getId(result.getItem());
                RecipeExpectation actual = new RecipeExpectation(
                        entry.id(),
                        typeId,
                        resultId,
                        result.getCount()
                );
                descriptions.add(actual.description());
            }
            return List.copyOf(descriptions);
        }

        private static String generateLoot(
                ServerWorld world,
                ServerPlayerEntity player,
                BlockState state,
                ItemStack tool
        ) {
            world.setBlockState(LOOT_PROBE_POS, state, Block.NOTIFY_ALL);
            List<ItemStack> stacks = Block.getDroppedStacks(
                    state,
                    world,
                    LOOT_PROBE_POS,
                    null,
                    player,
                    tool
            );
            String result = stacks.stream()
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> Registries.ITEM.getId(stack.getItem())
                            + "x" + stack.getCount())
                    .sorted()
                    .collect(Collectors.joining("+"));
            return result.isEmpty() ? "none" : result;
        }

        private boolean lootTablesExact() {
            return expectedLootTableIds().equals(lootTableIds);
        }

        private boolean selfDropsExact() {
            return expectedSelfDrops().equals(selfDrops);
        }

        private boolean doubleSlabDropsExact() {
            return expectedDoubleSlabDrops().equals(doubleSlabDrops);
        }

        private boolean ownedRecipesExact() {
            return recipeDescriptions(OWNED_RECIPES).equals(ownedRecipes);
        }

        private boolean ownedAdvancementsExact() {
            return OWNED_ADVANCEMENTS.equals(ownedAdvancements);
        }

        private boolean relatedRecipesExact() {
            return recipeDescriptions(RELATED_RECIPES).equals(relatedRecipes);
        }

        private boolean exact() {
            return failure.isEmpty()
                    && lootTablesExact()
                    && selfDropsExact()
                    && doubleSlabDropsExact()
                    && ownedRecipesExact()
                    && ownedAdvancementsExact()
                    && relatedRecipesExact();
        }

        private boolean sameOutcome(DataProbe other) {
            return lootTableIds.equals(other.lootTableIds)
                    && selfDrops.equals(other.selfDrops)
                    && doubleSlabDrops.equals(other.doubleSlabDrops)
                    && ownedRecipes.equals(other.ownedRecipes)
                    && ownedAdvancements.equals(other.ownedAdvancements)
                    && relatedRecipes.equals(other.relatedRecipes)
                    && failure.equals(other.failure);
        }
    }

    private record BlockObservation(
            String expectedId,
            String placedId,
            String state,
            String supportId
    ) {

        private boolean exact() {
            return expectedId.equals(placedId)
                    && state.startsWith(expectedId)
                    && (supportId.equals("minecraft:polished_andesite")
                    || supportId.equals("minecraft:smooth_stone"));
        }

        private String description() {
            return expectedId + "=" + state + "|support=" + supportId;
        }
    }

    private record FixtureSnapshot(List<BlockObservation> observations) {

        private boolean exact() {
            return observations.size() == FIXTURES.size()
                    && observations.stream().allMatch(BlockObservation::exact);
        }

        private String description() {
            return observations.stream()
                    .map(BlockObservation::description)
                    .collect(Collectors.joining(";"));
        }
    }

    private record BehaviorSequence(
            BehaviorPhase phase,
            long buttonActivationTick,
            long deadline,
            boolean buttonActivated,
            boolean buttonResetScheduled,
            ItemEntity itemEntity,
            PigEntity pigEntity,
            boolean itemIgnored,
            boolean livingActivated
    ) {
    }

    private record BehaviorProbe(
            boolean buttonActivated,
            boolean buttonResetScheduled,
            boolean buttonReset,
            long buttonElapsedTicks,
            boolean itemIgnored,
            boolean livingActivated,
            boolean pressurePlateReset,
            FixtureSnapshot snapshot
    ) {

        private static BehaviorProbe missing() {
            return new BehaviorProbe(
                    false,
                    false,
                    false,
                    0L,
                    false,
                    false,
                    false,
                    new FixtureSnapshot(List.of())
            );
        }

        private boolean buttonExact() {
            return buttonActivated
                    && buttonResetScheduled
                    && buttonReset
                    && buttonElapsedTicks >= 20L;
        }

        private boolean pressurePlateExact() {
            return itemIgnored && livingActivated && pressurePlateReset;
        }

        private boolean exact() {
            return buttonExact() && pressurePlateExact() && snapshot.exact();
        }

        private String buttonDescription() {
            return "powered=" + buttonActivated
                    + ";scheduled=" + buttonResetScheduled
                    + ";elapsed=" + buttonElapsedTicks
                    + ";reset=" + buttonReset;
        }

        private String pressurePlateDescription() {
            return "item=" + !itemIgnored
                    + ";living=" + livingActivated
                    + ";reset=" + pressurePlateReset;
        }
    }

    private record LightSample(BlockPos position, int lightLevel) {

        private String description() {
            return position.getX() + "," + position.getY() + ","
                    + position.getZ() + "=" + lightLevel;
        }
    }

    private record LocalLightingSamples(
            List<LightSample> skySamples,
            List<LightSample> blockSamples
    ) {

        private static LocalLightingSamples capture(WorldView world) {
            List<LightSample> skySamples = SKY_LIGHT_SAMPLE_POSITIONS.stream()
                    .map(position -> new LightSample(
                            position,
                            world.getLightLevel(LightType.SKY, position)
                    ))
                    .toList();
            List<LightSample> blockSamples = BLOCK_LIGHT_SAMPLE_POSITIONS.stream()
                    .map(position -> new LightSample(
                            position,
                            world.getLightLevel(LightType.BLOCK, position)
                    ))
                    .toList();
            return new LocalLightingSamples(skySamples, blockSamples);
        }

        private static LocalLightingSamples missing() {
            return new LocalLightingSamples(List.of(), List.of());
        }

        private boolean exact() {
            return skySamples.size() == SKY_LIGHT_SAMPLE_POSITIONS.size()
                    && blockSamples.size() == BLOCK_LIGHT_SAMPLE_POSITIONS.size()
                    && skySamples.stream().allMatch(
                            sample -> isExpectedSkyLight(sample.lightLevel())
                    )
                    && blockSamples.stream().allMatch(
                            sample -> isExpectedBlockLight(sample.lightLevel())
                    );
        }

        private String description() {
            return "sky=" + skySamples.stream()
                    .map(LightSample::description)
                    .collect(Collectors.joining(",", "[", "]"))
                    + ";block=" + blockSamples.stream()
                    .map(LightSample::description)
                    .collect(Collectors.joining(",", "[", "]"));
        }
    }

    private record ServerLightingEvidence(
            int generation,
            LocalLightingSamples samples
    ) {
    }

    private record LightingEvidence(
            int stableClientTicks,
            boolean clientPendingUpdates,
            LocalLightingSamples clientSamples,
            int serverGeneration,
            LocalLightingSamples serverSamples
    ) {

        private boolean exact() {
            if (stableClientTicks != REQUIRED_LIGHTING_READY_CLIENT_TICKS
                    || serverGeneration < 0) return false;
            return localLightingSamplesExact(
                    clientSamples.skySamples().stream()
                            .map(LightSample::lightLevel)
                            .toList(),
                    clientSamples.blockSamples().stream()
                            .map(LightSample::lightLevel)
                            .toList(),
                    serverSamples.skySamples().stream()
                            .map(LightSample::lightLevel)
                            .toList(),
                    serverSamples.blockSamples().stream()
                            .map(LightSample::lightLevel)
                            .toList()
            );
        }

        private String description() {
            return "stableClientTicks=" + stableClientTicks
                    + ";clientPending=" + clientPendingUpdates
                    + ";client:" + clientSamples.description()
                    + ";serverGeneration=" + serverGeneration
                    + ";server:" + serverSamples.description();
        }

        private static String expectedDescription() {
            return REQUIRED_LIGHTING_READY_CLIENT_TICKS
                    + " fresh paired local server/client samples;sky="
                    + EXPECTED_SKY_LIGHT_LEVEL
                    + ";block=" + EXPECTED_BLOCK_LIGHT_LEVEL
                    + ";globalPending=diagnostic-only";
        }
    }

    private record ScreenshotResult(
            boolean passed,
            long size,
            String sha256,
            String failure
    ) {

        private static ScreenshotResult failed(String failure) {
            return new ScreenshotResult(false, 0L, "", failure);
        }
    }

    private record CaptureEvidence(
            boolean mirrorExact,
            boolean renderReady,
            LightingEvidence lighting,
            boolean cameraExact,
            int completedRenders,
            int width,
            int height,
            String cameraPose,
            FixtureSnapshot snapshot,
            ScreenshotResult screenshot
    ) {

        private CaptureEvidence withScreenshot(ScreenshotResult result) {
            return new CaptureEvidence(
                    mirrorExact,
                    renderReady,
                    lighting,
                    cameraExact,
                    completedRenders,
                    width,
                    height,
                    cameraPose,
                    snapshot,
                    result
            );
        }
    }

    private record ServerSetupResult(
            boolean chunksLoaded,
            RegistryProbe registryProbe,
            DataProbe dataProbe,
            PlacementInventory placements,
            FixtureSnapshot snapshot,
            String worldDisplayName,
            long worldSeed,
            String dimensionId
    ) {
    }

    private record SaveResult(boolean saved, FixtureSnapshot snapshot) {
    }

    private record ReopenedResult(
            RegistryProbe registryProbe,
            DataProbe dataProbe,
            FixtureSnapshot snapshot
    ) {
    }
}
