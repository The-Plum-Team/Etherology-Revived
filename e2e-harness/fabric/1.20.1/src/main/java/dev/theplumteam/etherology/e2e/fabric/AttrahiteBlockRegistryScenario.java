package dev.theplumteam.etherology.e2e.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancement.Advancement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DatapackFailureScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.loot.LootDataType;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

final class AttrahiteBlockRegistryScenario implements ClientScenario {

    static final String SCENARIO_ID = "attrahite-block-registry";
    static final String WORLD_DIRECTORY_NAME =
            "etherology-e2e-attrahite-block-registry-world";
    static final String WORLD_DISPLAY_NAME = "Etherology E2E Attrahite Blocks";
    static final String INITIAL_SCREENSHOT_FILE_NAME =
            "attrahite-block-registry-initial.png";
    static final String REOPENED_SCREENSHOT_FILE_NAME =
            "attrahite-block-registry-reopened.png";
    static final long WORLD_SEED = 0x4154545241484954L;
    static final int REQUIRED_COMPLETED_RENDERS = 120;
    static final int REQUIRED_LIGHTING_READY_SERVER_TICKS = 20;
    static final int REQUIRED_LIGHTING_READY_CLIENT_TICKS = 20;
    static final float PLACEMENT_YAW = 180.0F;

    private static final Logger LOGGER = LoggerFactory.getLogger("EtherologyE2EHarness");
    private static final String HARNESS_MOD_ID = "etherology_e2e_harness";
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final double CAMERA_X = 0.5;
    private static final double CAMERA_Y = 121.0;
    private static final double CAMERA_Z = -7.5;
    private static final float CAMERA_YAW = 0.0F;
    private static final float CAMERA_PITCH = 3.0F;
    private static final double CAMERA_POSE_TOLERANCE = 0.0001;
    private static final BlockPos CAMERA_BLOCK_POS =
            new BlockPos(0, ARENA_FLOOR_Y + 1, -8);
    private static final Identifier PEDESTAL_ID =
            new Identifier("minecraft", "polished_andesite");
    private static final List<BlockFixture> FIXTURES = List.of(
            fixture("attrahite", -3),
            fixture("attrahite_bricks", -1),
            fixture("attrahite_brick_slab", 1),
            fixture("attrahite_brick_stairs", 3)
    );
    private static final List<BlockPos> LIGHT_SAMPLE_POSITIONS = List.of(
            CAMERA_BLOCK_POS,
            new BlockPos(0, ARENA_FLOOR_Y + 1, 0),
            new BlockPos(-3, ARENA_FLOOR_Y + 3, 1),
            new BlockPos(-1, ARENA_FLOOR_Y + 3, 1),
            new BlockPos(1, ARENA_FLOOR_Y + 3, 1),
            new BlockPos(3, ARENA_FLOOR_Y + 3, 1)
    );
    static final List<Integer> EXPECTED_SKY_LIGHT_LEVELS =
            List.of(15, 15, 15, 15, 15, 15);
    static final List<Integer> EXPECTED_BLOCK_LIGHT_LEVELS =
            List.of(14, 14, 10, 10, 10, 8);
    static final List<String> EXPECTED_LOOT_TABLE_IDS = List.of(
            "etherology:blocks/attrahite",
            "etherology:blocks/attrahite_brick_slab",
            "etherology:blocks/attrahite_brick_stairs",
            "etherology:blocks/attrahite_bricks"
    );
    static final List<String> EXPECTED_RECIPE_IDS = List.of(
            "etherology:attrahite_brick",
            "etherology:attrahite_brick_slab",
            "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting",
            "etherology:attrahite_brick_stairs",
            "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
            "etherology:attrahite_bricks",
            "etherology:azel_ingot",
            "etherology:azel_ingot_from_blasting",
            "etherology:raw_azel"
    );
    static final List<String> EXPECTED_ADVANCEMENT_IDS = List.of(
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
    );
    private static final List<Long> RAW_LOOT_SEEDS = List.of(
            1L,
            4096L,
            4224L,
            4640L,
            7168L
    );
    static final List<Identifier> READY_RESOURCES = createReadyResources();
    static final List<String> ASSERTION_NAMES = createAssertionNames();
    static final List<String> SCREENSHOT_FILE_NAMES = List.of(
            INITIAL_SCREENSHOT_FILE_NAME,
            REOPENED_SCREENSHOT_FILE_NAME
    );

    private Stage stage = Stage.WAITING_FOR_TITLE;
    private CapturePhase capturePhase = CapturePhase.INITIAL;
    private int clientTicks;
    private int stageClientTicks;
    private int completedRenders;
    private int lightingReadyClientTicks;
    private boolean worldSetupSubmitted;
    private boolean saveSubmitted;
    private boolean restartSubmitted;
    private boolean restartInspectionSubmitted;
    private boolean resourcesReady;
    private boolean persistenceExact;
    private boolean reopenedDataExact;
    private String lifecycleFailure = "";
    private List<Identifier> missingResources = List.of();
    private EvidenceLayout evidenceLayout;
    private RegistryProbe registryProbe;
    private DataProbe dataProbe;
    private DataProbe reopenedDataProbe;
    private ServerSetupResult serverSetupResult;
    private ServerLightingResult currentServerLighting;
    private FixtureSnapshot savedSnapshot;
    private FixtureSnapshot reopenedSnapshot;
    private FixtureSnapshot currentServerSnapshot;
    private final Map<CapturePhase, CaptureEvidence> captureEvidence =
            new LinkedHashMap<>();
    private final Map<CapturePhase, LightingEvidence> lightingDiagnostics =
            new LinkedHashMap<>();
    private volatile String serverFailure = "";
    private volatile ServerSetupResult pendingServerSetupResult;
    private volatile SaveResult pendingSaveResult;
    private volatile ReopenedResult pendingReopenedResult;
    private volatile ScreenshotResult pendingScreenshotResult;
    private volatile CapturePhase serverLightingBarrierPhase;
    private volatile CapturePhase latestServerLightingPhase;
    private volatile int lightingReadyServerTicks;
    private volatile LightSnapshot latestServerLightSnapshot = LightSnapshot.missing();
    private volatile ServerLightingResult initialServerLightingResult;
    private volatile ServerLightingResult reopenedServerLightingResult;

    @Override
    public void onEndClientTick(MinecraftClient client) {
        if (stage == Stage.COMPLETE) return;

        clientTicks++;
        stageClientTicks++;
        try {
            switch (stage) {
                case WAITING_FOR_TITLE -> tickWaitingForTitle(client);
                case STARTING_WORLD -> startWorld(client);
                case WAITING_FOR_WORLD -> tickWaitingForWorld(client);
                case WAITING_FOR_SERVER_SETUP -> tickWaitingForServerSetup(client);
                case WAITING_FOR_CLIENT_MIRROR -> tickWaitingForClientMirror(client);
                case WAITING_FOR_RENDERS -> tickWaitingForRenders(client);
                case CAPTURING -> tickCapturing(client);
                case SAVING_WORLD -> tickSavingWorld(client);
                case DISCONNECTING -> tickDisconnecting(client);
                case WAITING_FOR_RESTART_TITLE -> tickWaitingForRestartTitle(client);
                case RESTARTING_WORLD -> restartWorld(client);
                case WAITING_FOR_RESTART_WORLD -> tickWaitingForRestartWorld(client);
                case WAITING_FOR_RESTART_INSPECTION -> tickWaitingForRestartInspection(client);
                case COMPLETE -> {
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Etherology attrahite-block-registry failed in {}", stage, exception);
            fail(
                    client,
                    stage + " raised " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()
            );
            return;
        }

        if (stage != Stage.COMPLETE && stageClientTicks >= MAXIMUM_STAGE_CLIENT_TICKS) {
            fail(
                    client,
                    "Timed out in " + stage + " after " + stageClientTicks + " client ticks"
            );
        }
    }

    @Override
    public void onScreenInitialized(
            MinecraftClient client,
            Screen screen,
            int scaledWidth,
            int scaledHeight
    ) {
    }

    @Override
    public void onEndServerTick(MinecraftServer server) {
        CapturePhase phase = serverLightingBarrierPhase;
        if (phase == null || !server.isRunning() || server.isStopping()) return;

        try {
            ServerWorld world = server.getOverworld();
            LightSnapshot snapshot = captureLightSnapshot(
                    world,
                    world.getChunkManager().getLightingProvider().hasUpdates()
            );
            latestServerLightSnapshot = snapshot;
            lightingReadyServerTicks = nextLightingReadyServerTickCount(
                    lightingReadyServerTicks,
                    snapshot.hasExpectedSamples()
            );
            if (lightingReadyServerTicks < REQUIRED_LIGHTING_READY_SERVER_TICKS) return;

            ServerLightingResult result = new ServerLightingResult(
                    lightingReadyServerTicks,
                    snapshot
            );
            if (phase == CapturePhase.INITIAL) {
                initialServerLightingResult = result;
            } else {
                reopenedServerLightingResult = result;
            }
            serverLightingBarrierPhase = null;
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
            serverLightingBarrierPhase = null;
        }
    }

    @Override
    public void onGameRenderCompleted() {
        if (stage != Stage.WAITING_FOR_RENDERS) return;

        MinecraftClient client = MinecraftClient.getInstance();
        try {
            completedRenders = nextStableRenderCount(
                    completedRenders,
                    isCaptureStateExact(client)
            );
            if (completedRenders == REQUIRED_COMPLETED_RENDERS) {
                captureCurrentPhase(client);
            }
        } catch (RuntimeException exception) {
            completedRenders = 0;
            LOGGER.error("Attrahite completed-render callback failed", exception);
            fail(
                    client,
                    "Render callback raised " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()
            );
        }
    }

    static int nextStableRenderCount(int completedRenders, boolean exactState) {
        return exactState ? completedRenders + 1 : 0;
    }

    static int nextLightingReadyClientTickCount(int readyClientTicks, boolean ready) {
        return ready
                ? Math.min(readyClientTicks + 1, REQUIRED_LIGHTING_READY_CLIENT_TICKS)
                : 0;
    }

    static int nextLightingReadyServerTickCount(int readyServerTicks, boolean ready) {
        return ready
                ? Math.min(readyServerTicks + 1, REQUIRED_LIGHTING_READY_SERVER_TICKS)
                : 0;
    }

    static List<String> fixtureDescriptions() {
        return FIXTURES.stream()
                .map(fixture -> fixture.id() + "@" + positionDescription(fixture.position()))
                .toList();
    }

    static List<String> lightingSampleDescriptions() {
        return LIGHT_SAMPLE_POSITIONS.stream()
                .map(AttrahiteBlockRegistryScenario::positionDescription)
                .toList();
    }

    static List<String> blockLightingSampleDescriptions() {
        return LIGHT_SAMPLE_POSITIONS.stream()
                .map(AttrahiteBlockRegistryScenario::positionDescription)
                .toList();
    }

    static boolean isExpectedSkyLight(int sampleIndex, int skyLightLevel) {
        return skyLightLevel == EXPECTED_SKY_LIGHT_LEVELS.get(sampleIndex);
    }

    static boolean isExpectedBlockLight(int sampleIndex, int blockLightLevel) {
        return blockLightLevel == EXPECTED_BLOCK_LIGHT_LEVELS.get(sampleIndex);
    }

    private void tickWaitingForTitle(MinecraftClient client) {
        if (client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) return;
        if (!hasExpectedFramebuffer(client)) return;

        missingResources = findMissingResources(client);
        resourcesReady = missingResources.isEmpty();
        if (!resourcesReady) {
            fail(client, "The Attrahite client resources are missing " + missingResources);
            return;
        }
        if (!hasBaseRegistry()) {
            fail(client, "The four Attrahite block/item registry pairs are incomplete");
            return;
        }
        try {
            ensureEvidenceLayout(client);
        } catch (IOException exception) {
            LOGGER.error("Cannot use the isolated Attrahite evidence layout", exception);
            lifecycleFailure = exception.getMessage();
            stage = Stage.COMPLETE;
            client.scheduleStop();
            return;
        }
        transition(Stage.STARTING_WORLD);
    }

    private void startWorld(MinecraftClient client) {
        Path saveDirectory = saveDirectory(client);
        if (Files.exists(saveDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(saveDirectory)) {
            fail(client, "Refusing to reuse the Attrahite save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "The client already has a world before Attrahite world creation");
            return;
        }

        GameRules gameRules = new GameRules();
        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        gameRules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        gameRules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        gameRules.get(GameRules.KEEP_INVENTORY).set(true, null);
        gameRules.get(GameRules.DO_IMMEDIATE_RESPAWN).set(true, null);
        LevelInfo levelInfo = new LevelInfo(
                WORLD_DISPLAY_NAME,
                GameMode.SURVIVAL,
                false,
                Difficulty.PEACEFUL,
                true,
                gameRules,
                DataConfiguration.SAFE_MODE
        );
        client.createIntegratedServerLoader().createAndStart(
                WORLD_DIRECTORY_NAME,
                levelInfo,
                new GeneratorOptions(WORLD_SEED, true, false),
                WorldPresets::createDemoOptions
        );
        transition(Stage.WAITING_FOR_WORLD);
    }

    private void tickWaitingForWorld(MinecraftClient client) {
        if (client.currentScreen instanceof DatapackFailureScreen) {
            fail(client, "Minecraft rejected Etherology's Attrahite server data");
            return;
        }
        if (!isWorldLifecycleReady(client)) return;

        IntegratedServer server = client.getServer();
        ServerPlayerEntity serverPlayer = server.getPlayerManager()
                .getPlayer(client.player.getUuid());
        if (serverPlayer == null || worldSetupSubmitted) return;

        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(client.player);
        worldSetupSubmitted = true;
        UUID playerId = client.player.getUuid();
        server.execute(() -> setupFixture(server, playerId));
        transition(Stage.WAITING_FOR_SERVER_SETUP);
    }

    private void tickWaitingForServerSetup(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "Attrahite server setup failed: " + serverFailure);
            return;
        }
        ServerSetupResult result = pendingServerSetupResult;
        ServerLightingResult lighting = initialServerLightingResult;
        if (result == null || lighting == null) return;

        serverSetupResult = result;
        currentServerLighting = lighting;
        registryProbe = result.registryProbe();
        dataProbe = result.dataProbe();
        currentServerSnapshot = result.snapshot();
        if (!registryProbe.exact()
                || !dataProbe.exact()
                || !result.placements().exact()
                || !currentServerSnapshot.exact()) {
            fail(client, "The initial Attrahite native probe was not exact");
            return;
        }
        lightingReadyClientTicks = 0;
        transition(Stage.WAITING_FOR_CLIENT_MIRROR);
    }

    private void tickWaitingForClientMirror(MinecraftClient client) {
        boolean mirrorReady = isWorldViewReady(client)
                && captureSnapshot(client.world).equals(currentServerSnapshot)
                && currentServerLighting != null;
        updateClientLightingReadiness(client, mirrorReady);
        if (lightingReadyClientTicks < REQUIRED_LIGHTING_READY_CLIENT_TICKS) return;

        transition(Stage.WAITING_FOR_RENDERS);
    }

    private void tickWaitingForRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world vanished before the Attrahite capture");
            return;
        }
        boolean mirrorReady = isWorldViewReady(client)
                && currentServerSnapshot != null
                && currentServerSnapshot.equals(captureSnapshot(client.world));
        updateClientLightingReadiness(client, mirrorReady);
        if (lightingReadyClientTicks < REQUIRED_LIGHTING_READY_CLIENT_TICKS) {
            completedRenders = 0;
        }
    }

    private void tickCapturing(MinecraftClient client) {
        ScreenshotResult screenshot = pendingScreenshotResult;
        if (screenshot == null) return;
        if (!screenshot.passed()) {
            fail(client, "The Attrahite screenshot failed: " + screenshot.failure());
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
            fail(client, "The Attrahite world save failed: " + serverFailure);
            return;
        }
        SaveResult result = pendingSaveResult;
        if (result == null) return;
        if (!result.saved() || !result.snapshot().exact()) {
            fail(client, "The forced Attrahite save did not preserve the exact fixture");
            return;
        }

        savedSnapshot = result.snapshot();
        transition(Stage.DISCONNECTING);
    }

    private void tickDisconnecting(MinecraftClient client) {
        if (client.world == null) {
            fail(client, "The client world vanished before the Attrahite restart");
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
        client.createIntegratedServerLoader().start(new TitleScreen(), WORLD_DIRECTORY_NAME);
        transition(Stage.WAITING_FOR_RESTART_WORLD);
    }

    private void tickWaitingForRestartWorld(MinecraftClient client) {
        if (client.currentScreen instanceof DatapackFailureScreen) {
            fail(client, "Minecraft rejected the saved Attrahite data packs on restart");
            return;
        }
        if (!isWorldLifecycleReady(client)) return;

        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(client.player);
        transition(Stage.WAITING_FOR_RESTART_INSPECTION);
    }

    private void tickWaitingForRestartInspection(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "The reopened Attrahite inspection failed: " + serverFailure);
            return;
        }
        IntegratedServer server = client.getServer();
        if (server == null) return;

        if (!restartInspectionSubmitted) {
            restartInspectionSubmitted = true;
            server.execute(() -> {
                try {
                    ServerWorld world = server.getOverworld();
                    requestServerLightingChecks(world);
                    pendingReopenedResult = new ReopenedResult(
                            RegistryProbe.capture(),
                            DataProbe.capture(server),
                            captureSnapshot(world)
                    );
                    beginServerLightingBarrier(CapturePhase.REOPENED);
                } catch (RuntimeException exception) {
                    recordServerFailure(exception);
                }
            });
        }
        ReopenedResult result = pendingReopenedResult;
        ServerLightingResult lighting = reopenedServerLightingResult;
        if (result == null || lighting == null) return;

        reopenedSnapshot = result.snapshot();
        reopenedDataProbe = result.dataProbe();
        persistenceExact = savedSnapshot != null
                && savedSnapshot.equals(reopenedSnapshot)
                && reopenedSnapshot.exact()
                && result.registryProbe().equals(registryProbe);
        reopenedDataExact = dataProbe != null
                && reopenedDataProbe.exact()
                && dataProbe.sameOutcome(reopenedDataProbe);
        if (!persistenceExact || !reopenedDataExact) {
            fail(client, "The Attrahite fixture or loaded data changed across restart");
            return;
        }

        capturePhase = CapturePhase.REOPENED;
        currentServerSnapshot = reopenedSnapshot;
        currentServerLighting = lighting;
        completedRenders = 0;
        lightingReadyClientTicks = 0;
        transition(Stage.WAITING_FOR_CLIENT_MIRROR);
    }

    private void setupFixture(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                throw new IllegalStateException("The integrated server has no matching player");
            }

            boolean chunksLoaded = loadArenaChunks(world);
            world.setTimeOfDay(6000L);
            world.setWeather(6000, 0, false, false);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
            clearArena(world);
            buildArena(world);

            player.changeGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(true);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            player.teleport(world, CAMERA_X, CAMERA_Y, CAMERA_Z, PLACEMENT_YAW, CAMERA_PITCH);
            PlacementInventory placements = placeAllBlockItems(world, player);
            player.teleport(world, CAMERA_X, CAMERA_Y, CAMERA_Z, CAMERA_YAW, CAMERA_PITCH);
            player.setSpawnPoint(World.OVERWORLD, CAMERA_BLOCK_POS, CAMERA_YAW, true, false);
            requestServerLightingChecks(world);
            FixtureSnapshot snapshot = captureSnapshot(world);
            pendingServerSetupResult = new ServerSetupResult(
                    chunksLoaded,
                    RegistryProbe.capture(),
                    DataProbe.capture(server),
                    placements,
                    snapshot,
                    server.getSaveProperties().getLevelName(),
                    world.getSeed(),
                    world.getRegistryKey().getValue().toString()
            );
            beginServerLightingBarrier(CapturePhase.INITIAL);
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
            Item item = requiredItem(fixture.id());
            Block block = requiredBlock(fixture.id());
            if (!(item instanceof BlockItem blockItem)) {
                throw new IllegalStateException(fixture.id() + " is not a BlockItem");
            }

            ItemStack stack = new ItemStack(item);
            int beforeCount = stack.getCount();
            player.setStackInHand(Hand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(
                    Vec3d.ofCenter(fixture.pedestalPosition()).add(0.0, 0.5, 0.0),
                    Direction.UP,
                    fixture.pedestalPosition(),
                    false
            );
            ActionResult result = blockItem.useOnBlock(
                    new ItemUsageContext(player, Hand.MAIN_HAND, hit)
            );
            BlockState placedState = world.getBlockState(fixture.position());
            placements.put(
                    fixture.id().toString(),
                    new PlacementEvidence(
                            result.name(),
                            result.isAccepted(),
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

    private void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-9, ARENA_FLOOR_Y, -9);
        BlockPos end = new BlockPos(9, ARENA_FLOOR_Y + 7, 9);
        for (BlockPos position : BlockPos.iterate(start, end)) {
            world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private void buildArena(ServerWorld world) {
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                Block floor = (x + z) % 8 == 0 ? Blocks.SEA_LANTERN : Blocks.SMOOTH_STONE;
                world.setBlockState(
                        new BlockPos(x, ARENA_FLOOR_Y, z),
                        floor.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
        }
        for (BlockFixture fixture : FIXTURES) {
            world.setBlockState(
                    fixture.pedestalPosition(),
                    Blocks.POLISHED_ANDESITE.getDefaultState(),
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
        for (int chunkX = -1; chunkX <= 0; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 0; chunkZ++) {
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
            fail(client, "The integrated server stopped before the Attrahite save");
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
            completedRenders = 0;
            return;
        }

        FixtureSnapshot clientSnapshot = captureSnapshot(client.world);
        LightingEvidence lighting = captureLightingEvidence(client);
        if (!lighting.exact()) {
            completedRenders = 0;
            return;
        }
        CaptureEvidence capture = new CaptureEvidence(
                clientSnapshot.equals(currentServerSnapshot),
                isFixtureRenderReady(client),
                lighting,
                hasExpectedCameraPose(client),
                completedRenders,
                client.getFramebuffer().textureWidth,
                client.getFramebuffer().textureHeight,
                cameraPoseDescription(client),
                clientSnapshot,
                null
        );
        captureEvidence.put(capturePhase, capture);
        transition(Stage.CAPTURING);
        saveScreenshot(client, capturePhase.screenshotFileName());
    }

    private void saveScreenshot(MinecraftClient client, String fileName) {
        EvidenceLayout layout = evidenceLayout;
        if (layout == null) {
            pendingScreenshotResult = ScreenshotResult.failed(
                    "The evidence layout was not initialized"
            );
            return;
        }
        ScreenshotRecorder.saveScreenshot(
                layout.scenarioRoot().toFile(),
                fileName,
                client.getFramebuffer(),
                message -> pendingScreenshotResult = inspectScreenshot(
                        layout.screenshotPath(fileName)
                )
        );
    }

    private ScreenshotResult inspectScreenshot(Path path) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return ScreenshotResult.failed("Minecraft did not write one regular PNG");
            }
            long size = Files.size(path);
            if (size <= 0L) {
                return ScreenshotResult.failed("Minecraft wrote an empty PNG");
            }
            return new ScreenshotResult(true, size, ArtifactDigest.sha256(path), "");
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
        return isWorldLifecycleReady(client)
                && client.getOverlay() == null
                && client.currentScreen == null
                && client.world.getRegistryKey() == World.OVERWORLD
                && areClientArenaChunksLoaded(client)
                && hasExpectedFramebuffer(client);
    }

    private boolean areClientArenaChunksLoaded(MinecraftClient client) {
        for (int chunkX = -1; chunkX <= 0; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 0; chunkZ++) {
                if (!client.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    private boolean hasExpectedFramebuffer(MinecraftClient client) {
        return client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT
                && client.getWindow().getFramebufferWidth() == FRAMEBUFFER_WIDTH
                && client.getWindow().getFramebufferHeight() == FRAMEBUFFER_HEIGHT;
    }

    private boolean isFixtureRenderReady(MinecraftClient client) {
        if (!isClientLightingReady(client)) return false;
        if (!client.worldRenderer.isTerrainRenderComplete()) return false;

        for (BlockFixture fixture : FIXTURES) {
            if (!client.worldRenderer.isRenderingReady(fixture.position())
                    || !client.worldRenderer.isRenderingReady(fixture.pedestalPosition())) {
                return false;
            }
        }
        return true;
    }

    private boolean isClientLightingReady(MinecraftClient client) {
        ServerLightingResult serverLighting = currentServerLighting;
        if (serverLighting == null || client.world == null) return false;

        LightSnapshot clientLighting = captureClientLightSnapshot(client);
        return serverLighting.snapshot().hasExpectedSamples()
                && clientLighting.sameSamples(serverLighting.snapshot());
    }

    private LightingEvidence captureLightingEvidence(MinecraftClient client) {
        ServerLightingResult serverLighting = currentServerLighting;
        LightSnapshot serverSnapshot = serverLighting == null
                ? LightSnapshot.missing()
                : serverLighting.snapshot();
        int stableServerTicks = serverLighting == null
                ? 0
                : serverLighting.stableTicks();
        LightSnapshot clientSnapshot = client.world == null
                ? LightSnapshot.missing()
                : captureClientLightSnapshot(client);
        LightingEvidence evidence = new LightingEvidence(
                stableServerTicks,
                lightingReadyClientTicks,
                serverSnapshot,
                clientSnapshot
        );
        lightingDiagnostics.put(capturePhase, evidence);
        return evidence;
    }

    private void requestServerLightingChecks(ServerWorld world) {
        for (BlockPos position : LIGHT_SAMPLE_POSITIONS) {
            world.getChunkManager().getLightingProvider().checkBlock(position.down());
            world.getChunkManager().getLightingProvider().checkBlock(position);
        }
    }

    private void beginServerLightingBarrier(CapturePhase phase) {
        lightingReadyServerTicks = 0;
        latestServerLightSnapshot = LightSnapshot.missing();
        latestServerLightingPhase = phase;
        serverLightingBarrierPhase = phase;
    }

    private void updateClientLightingReadiness(
            MinecraftClient client,
            boolean baseReady
    ) {
        ServerLightingResult serverLighting = currentServerLighting;
        LightSnapshot serverSnapshot = serverLighting == null
                ? LightSnapshot.missing()
                : serverLighting.snapshot();
        LightSnapshot clientSnapshot = client.world == null
                ? LightSnapshot.missing()
                : captureClientLightSnapshot(client);
        boolean samplesMatch = baseReady
                && serverSnapshot.hasExpectedSamples()
                && clientSnapshot.sameSamples(serverSnapshot);
        lightingReadyClientTicks = nextLightingReadyClientTickCount(
                lightingReadyClientTicks,
                samplesMatch
        );
        lightingDiagnostics.put(
                capturePhase,
                new LightingEvidence(
                        serverLighting == null ? 0 : serverLighting.stableTicks(),
                        lightingReadyClientTicks,
                        serverSnapshot,
                        clientSnapshot
                )
        );
    }

    private LightSnapshot captureClientLightSnapshot(MinecraftClient client) {
        return captureLightSnapshot(
                client.world,
                client.world.getChunkManager().getLightingProvider().hasUpdates()
        );
    }

    private static LightSnapshot captureLightSnapshot(
            WorldView world,
            boolean pendingUpdates
    ) {
        List<Integer> skyLevels = new ArrayList<>();
        List<Integer> blockLevels = new ArrayList<>();
        for (BlockPos position : LIGHT_SAMPLE_POSITIONS) {
            skyLevels.add(world.getLightLevel(LightType.SKY, position));
            blockLevels.add(world.getLightLevel(LightType.BLOCK, position));
        }
        return new LightSnapshot(
                true,
                pendingUpdates,
                List.copyOf(skyLevels),
                List.copyOf(blockLevels)
        );
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

    private List<Identifier> findMissingResources(MinecraftClient client) {
        List<Identifier> missing = new ArrayList<>();
        for (Identifier resource : READY_RESOURCES) {
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

    private void recordServerFailure(RuntimeException exception) {
        LOGGER.error("Attrahite integrated-server operation failed", exception);
        serverFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;

        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Attrahite lifecycle failure: {}", lifecycleFailure);
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
            AtomicEvidenceWriter.writeReportThenMarker(evidenceLayout, report);
            LOGGER.info("Attrahite E2E evidence is complete: {}", evidenceLayout.scenarioRoot());
        } catch (IOException exception) {
            LOGGER.error("Cannot atomically publish Attrahite E2E evidence", exception);
        } finally {
            stage = Stage.COMPLETE;
            client.scheduleStop();
        }
    }

    private void ensureEvidenceLayout(MinecraftClient client) throws IOException {
        if (evidenceLayout != null) return;

        EvidenceLayout layout = EvidenceLayout.resolve(client.runDirectory.toPath(), SCENARIO_ID);
        layout.requireFreshTargets(SCREENSHOT_FILE_NAMES.toArray(String[]::new));
        evidenceLayout = layout;
    }

    private Path saveDirectory(MinecraftClient client) {
        return client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
    }

    private JsonObject createReport(
            MinecraftClient client,
            List<ArtifactDigest> artifacts
    ) {
        JsonArray assertions = new JsonArray();
        boolean passed = lifecycleFailure.isEmpty();
        passed &= addAssertion(
                assertions,
                "fabric_mod_loaded:etherology",
                FabricLoader.getInstance().isModLoaded("etherology"),
                "loaded",
                FabricLoader.getInstance().isModLoaded("etherology") ? "loaded" : "missing"
        );
        for (BlockFixture fixture : FIXTURES) {
            RegistryEntry entry = registryProbe == null
                    ? RegistryEntry.missing(fixture.id().toString())
                    : registryProbe.entries().getOrDefault(
                            fixture.id().toString(),
                            RegistryEntry.missing(fixture.id().toString())
                    );
            passed &= addAssertion(assertions, "registry:block:" + fixture.id(),
                    entry.blockPresent(), "present", Boolean.toString(entry.blockPresent()));
            passed &= addAssertion(assertions, "registry:item:" + fixture.id(),
                    entry.itemPresent(), "present", Boolean.toString(entry.itemPresent()));
            passed &= addAssertion(assertions, "runtime:block_class:" + fixture.id(),
                    fixture.blockClass().equals(entry.blockClass()),
                    fixture.blockClass(), entry.blockClass());
            passed &= addAssertion(assertions, "runtime:block_item_class:" + fixture.id(),
                    BlockItem.class.getName().equals(entry.itemClass()),
                    BlockItem.class.getName(), entry.itemClass());
            passed &= addAssertion(assertions, "block_item_mapping:" + fixture.id(),
                    entry.blockItemMapping(), "true", Boolean.toString(entry.blockItemMapping()));
            passed &= addAssertion(assertions, "default_state:" + fixture.id(),
                    fixture.defaultState().equals(entry.defaultState()),
                    fixture.defaultState(), entry.defaultState());
            passed &= addAssertion(assertions, "state_count:" + fixture.id(),
                    fixture.stateCount() == entry.stateCount(),
                    Integer.toString(fixture.stateCount()), Integer.toString(entry.stateCount()));
            passed &= addAssertion(assertions, "default_state_network_id:" + fixture.id(),
                    entry.defaultStateNetworkId() >= 0,
                    "non-negative", Integer.toString(entry.defaultStateNetworkId()));
            passed &= addTagAssertions(assertions, fixture, entry);
        }
        passed &= addAssertion(
                assertions,
                "client_render_resources",
                resourcesReady,
                READY_RESOURCES.toString(),
                resourcesReady ? READY_RESOURCES.toString() : "missing=" + missingResources
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
                "four full chunks",
                serverSetupResult == null
                        ? "missing setup"
                        : Boolean.toString(serverSetupResult.chunksLoaded())
        );
        DataProbe data = dataProbe == null ? DataProbe.missing() : dataProbe;
        passed &= addAssertion(assertions, "loot_tables_exact", data.lootTableIdsExact(),
                EXPECTED_LOOT_TABLE_IDS.toString(), data.lootTableIds().toString());
        passed &= addAssertion(assertions, "standard_block_drops_exact", data.standardLootExact(),
                expectedStandardLoot().toString(), data.standardLoot().toString());
        passed &= addAssertion(assertions, "raw_plain_drops_deterministic", data.plainLootExact(),
                expectedRawPlainLoot(), data.rawPlainLoot());
        passed &= addAssertion(assertions, "raw_silk_touch_drop_exact", data.silkLootExact(),
                "etherology:attrahitex1", data.rawSilkLoot());
        passed &= addAssertion(assertions, "raw_fortune_drops_deterministic", data.fortuneLootExact(),
                expectedRawFortuneLoot().toString(), data.rawFortuneLoot().toString());
        passed &= addAssertion(assertions, "recipes_exact_and_craftable", data.recipesExact(),
                EXPECTED_RECIPE_IDS.toString(), data.recipeIds().toString());
        passed &= addAssertion(assertions, "advancements_exact", data.advancementsExact(),
                EXPECTED_ADVANCEMENT_IDS.toString(), data.advancementIds().toString());
        PlacementInventory placements = serverSetupResult == null
                ? PlacementInventory.missing()
                : serverSetupResult.placements();
        passed &= addAssertion(assertions, "direct_block_item_placements_exact",
                placements.exact(), "four accepted 1->0 default-state placements",
                placements.description());
        passed &= addAssertion(assertions, "initial_server_fixture_exact",
                serverSetupResult != null && serverSetupResult.snapshot().exact(),
                FixtureSnapshot.expectedDescription(),
                serverSetupResult == null
                        ? "missing"
                        : serverSetupResult.snapshot().description());
        passed &= addAssertion(assertions, "forced_world_save",
                pendingSaveResult != null && pendingSaveResult.saved(),
                "true", pendingSaveResult == null
                        ? "not attempted"
                        : Boolean.toString(pendingSaveResult.saved()));
        passed &= addAssertion(assertions, "restart_fixture_persistence_exact",
                persistenceExact, "saved snapshot equals reopened snapshot",
                persistenceExact ? "exact" : "mismatch");
        passed &= addAssertion(assertions, "restart_loaded_data_exact",
                reopenedDataExact, "same exact loaded-data outcome",
                reopenedDataExact ? "exact" : "mismatch");
        for (CapturePhase phase : CapturePhase.values()) {
            CaptureEvidence capture = captureEvidence.get(phase);
            passed &= addCaptureAssertions(assertions, phase, capture);
        }
        boolean saveDirectoryPresent = Files.isDirectory(
                saveDirectory(client),
                LinkOption.NOFOLLOW_LINKS
        ) && !Files.isSymbolicLink(saveDirectory(client));
        passed &= addAssertion(assertions, "isolated_save_directory_present",
                saveDirectoryPresent, WORLD_DIRECTORY_NAME,
                saveDirectoryPresent ? WORLD_DIRECTORY_NAME : "missing or linked");

        if (!assertionNames(assertions).equals(ASSERTION_NAMES)) {
            throw new IllegalStateException("Attrahite assertion inventory drifted");
        }
        JsonObject report = new JsonObject();
        report.addProperty("schema", 3);
        report.addProperty("scenario", SCENARIO_ID);
        report.addProperty("artifact_node", "fabric-1.20.1");
        report.addProperty("minecraft", "1.20.1");
        report.addProperty("loader", "fabric");
        report.addProperty("java", 17);
        report.addProperty("lane", "fabric-1.20.1");
        report.addProperty("role", "host");
        report.addProperty("status", passed ? "passed" : "failed");
        report.addProperty("passed", passed);
        report.addProperty("client_ticks", clientTicks);
        report.addProperty("lifecycle_failure", lifecycleFailure);
        report.add("assertions", assertions);
        report.add("world", createWorldReport(client));
        report.add("ready_resources", stringArray(
                READY_RESOURCES.stream().map(Identifier::toString).toList()
        ));
        report.add("artifacts", createArtifactsReport(artifacts));
        report.add("screenshots", createScreenshotsReport());
        report.add("attrahite", createAttrahiteReport(data, placements));
        return report;
    }

    private boolean addTagAssertions(
            JsonArray assertions,
            BlockFixture fixture,
            RegistryEntry entry
    ) {
        boolean passed = true;
        passed &= addAssertion(assertions, "tag:mineable/pickaxe:" + fixture.id(),
                entry.pickaxeMineable(), "true", Boolean.toString(entry.pickaxeMineable()));
        passed &= addAssertion(assertions, "tag:needs_stone_tool:" + fixture.id(),
                fixture.needsStoneTool() == entry.needsStoneTool(),
                Boolean.toString(fixture.needsStoneTool()),
                Boolean.toString(entry.needsStoneTool()));
        passed &= addAssertion(assertions, "tag:block/slabs:" + fixture.id(),
                fixture.slab() == entry.blockSlab(),
                Boolean.toString(fixture.slab()), Boolean.toString(entry.blockSlab()));
        passed &= addAssertion(assertions, "tag:item/slabs:" + fixture.id(),
                fixture.slab() == entry.itemSlab(),
                Boolean.toString(fixture.slab()), Boolean.toString(entry.itemSlab()));
        passed &= addAssertion(assertions, "tag:block/stairs:" + fixture.id(),
                fixture.stairs() == entry.blockStairs(),
                Boolean.toString(fixture.stairs()), Boolean.toString(entry.blockStairs()));
        passed &= addAssertion(assertions, "tag:item/stairs:" + fixture.id(),
                fixture.stairs() == entry.itemStairs(),
                Boolean.toString(fixture.stairs()), Boolean.toString(entry.itemStairs()));
        return passed;
    }

    private boolean addCaptureAssertions(
            JsonArray assertions,
            CapturePhase phase,
            CaptureEvidence capture
    ) {
        boolean passed = true;
        passed &= addAssertion(assertions, "capture_mirror_exact:" + phase.id(),
                capture != null && capture.mirrorExact(),
                FixtureSnapshot.expectedDescription(),
                capture == null ? "missing" : capture.snapshot().description());
        passed &= addAssertion(assertions, "capture_render_ready:" + phase.id(),
                capture != null && capture.renderReady(), "true",
                capture == null ? "missing" : Boolean.toString(capture.renderReady()));
        passed &= addAssertion(assertions, "capture_lighting_ready:" + phase.id(),
                capture != null && capture.lighting().exact(),
                LightingEvidence.expectedDescription(),
                capture == null
                        ? lightingDiagnosticFor(phase).assertionActual()
                        : capture.lighting().assertionActual());
        passed &= addAssertion(assertions, "capture_camera_exact:" + phase.id(),
                capture != null && capture.cameraExact(), expectedCameraPoseDescription(),
                capture == null ? "missing" : capture.cameraPose());
        passed &= addAssertion(assertions,
                "capture_consecutive_stable_renders:" + phase.id(),
                capture != null && capture.completedRenders() == REQUIRED_COMPLETED_RENDERS,
                Integer.toString(REQUIRED_COMPLETED_RENDERS),
                capture == null ? "0" : Integer.toString(capture.completedRenders()));
        passed &= addAssertion(assertions, "capture_framebuffer_dimensions:" + phase.id(),
                capture != null
                        && capture.width() == FRAMEBUFFER_WIDTH
                        && capture.height() == FRAMEBUFFER_HEIGHT,
                framebufferDescription(FRAMEBUFFER_WIDTH, FRAMEBUFFER_HEIGHT),
                capture == null
                        ? "0x0"
                        : framebufferDescription(capture.width(), capture.height()));
        ScreenshotResult screenshot = capture == null ? null : capture.screenshot();
        passed &= addAssertion(assertions, "native_screenshot_written:" + phase.id(),
                screenshot != null && screenshot.passed(),
                "one non-empty unedited framebuffer PNG",
                screenshot == null
                        ? "missing"
                        : screenshot.size() + " bytes, sha256=" + screenshot.sha256());
        return passed;
    }

    private JsonObject createWorldReport(MinecraftClient client) {
        JsonObject world = new JsonObject();
        world.addProperty("save_directory", WORLD_DIRECTORY_NAME);
        world.addProperty("display_name", serverSetupResult == null
                ? ""
                : serverSetupResult.worldDisplayName());
        world.addProperty("seed", serverSetupResult == null
                ? Long.MIN_VALUE
                : serverSetupResult.worldSeed());
        world.addProperty("dimension", serverSetupResult == null
                ? ""
                : serverSetupResult.dimensionId());
        world.addProperty("integrated", client.getServer() != null);
        world.addProperty("reopened", reopenedSnapshot != null);
        return world;
    }

    private JsonArray createArtifactsReport(List<ArtifactDigest> artifacts) {
        JsonArray report = new JsonArray();
        for (ArtifactDigest artifact : artifacts) {
            JsonObject entry = new JsonObject();
            entry.addProperty("mod_id", artifact.modId());
            entry.addProperty("origin_kind", artifact.originKind());
            entry.addProperty("file_name", artifact.fileName());
            entry.addProperty("size", artifact.size());
            entry.addProperty("sha256", artifact.sha256());
            report.add(entry);
        }
        return report;
    }

    private JsonArray createScreenshotsReport() {
        JsonArray screenshots = new JsonArray();
        for (CapturePhase phase : CapturePhase.values()) {
            CaptureEvidence capture = captureEvidence.get(phase);
            if (capture == null || capture.screenshot() == null
                    || !capture.screenshot().passed()) {
                continue;
            }
            JsonObject screenshot = new JsonObject();
            screenshot.addProperty("step", phase.id());
            screenshot.addProperty("role", "host");
            screenshot.addProperty("file", "screenshots/" + phase.screenshotFileName());
            screenshot.addProperty("width", capture.width());
            screenshot.addProperty("height", capture.height());
            screenshot.addProperty("size", capture.screenshot().size());
            screenshot.addProperty("sha256", capture.screenshot().sha256());
            screenshot.addProperty("completed_render_count", capture.completedRenders());
            screenshot.addProperty("source", "minecraft-framebuffer");
            screenshot.addProperty("edited", false);
            screenshots.add(screenshot);
        }
        return screenshots;
    }

    private JsonObject createAttrahiteReport(DataProbe data, PlacementInventory placements) {
        JsonObject attrahite = new JsonObject();
        attrahite.add("fixtures", stringArray(fixtureDescriptions()));
        attrahite.add("loot_tables", stringArray(data.lootTableIds()));
        attrahite.addProperty("standard_loot", data.standardLoot().toString());
        attrahite.addProperty("raw_plain_loot", data.rawPlainLoot());
        attrahite.addProperty("raw_silk_touch_loot", data.rawSilkLoot());
        attrahite.addProperty("raw_fortune_loot", data.rawFortuneLoot().toString());
        attrahite.add("recipes", stringArray(data.recipeIds()));
        attrahite.add("advancements", stringArray(data.advancementIds()));
        attrahite.addProperty("placements", placements.description());
        attrahite.addProperty("persistence_exact", persistenceExact);
        attrahite.addProperty("reopened_data_exact", reopenedDataExact);
        attrahite.addProperty("required_stable_renders", REQUIRED_COMPLETED_RENDERS);
        JsonObject lighting = new JsonObject();
        for (CapturePhase phase : CapturePhase.values()) {
            lighting.add(phase.id(), lightingReport(lightingDiagnosticFor(phase)));
        }
        attrahite.add("lighting", lighting);
        return attrahite;
    }

    private LightingEvidence lightingDiagnosticFor(CapturePhase phase) {
        LightingEvidence evidence = lightingDiagnostics.get(phase);
        if (evidence != null) return evidence;

        CaptureEvidence capture = captureEvidence.get(phase);
        if (capture != null) return capture.lighting();

        if (latestServerLightingPhase == phase) {
            return new LightingEvidence(
                    lightingReadyServerTicks,
                    0,
                    latestServerLightSnapshot,
                    LightSnapshot.missing()
            );
        }
        return LightingEvidence.missing();
    }

    private static JsonObject lightingReport(LightingEvidence evidence) {
        JsonObject report = new JsonObject();
        report.addProperty("server_stable_ticks", evidence.stableServerTicks());
        report.addProperty("client_stable_ticks", evidence.stableClientTicks());
        report.addProperty("server_observed", evidence.server().observed());
        report.addProperty("client_observed", evidence.client().observed());
        report.addProperty("server_pending", evidence.server().pendingUpdates());
        report.addProperty("client_pending", evidence.client().pendingUpdates());
        report.add("server_sky", lightLevelsReport(evidence.server().skyLevels()));
        report.add("server_block", lightLevelsReport(evidence.server().blockLevels()));
        report.add("client_sky", lightLevelsReport(evidence.client().skyLevels()));
        report.add("client_block", lightLevelsReport(evidence.client().blockLevels()));
        return report;
    }

    private static JsonObject lightLevelsReport(List<Integer> levels) {
        JsonObject report = new JsonObject();
        for (int index = 0; index < Math.min(levels.size(), LIGHT_SAMPLE_POSITIONS.size());
                index++) {
            report.addProperty(
                    positionDescription(LIGHT_SAMPLE_POSITIONS.get(index)),
                    levels.get(index)
            );
        }
        return report;
    }

    private void transition(Stage nextStage) {
        stage = nextStage;
        stageClientTicks = 0;
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
        assertions.forEach(assertion -> names.add(
                assertion.getAsJsonObject().get("name").getAsString()
        ));
        return List.copyOf(names);
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static FixtureSnapshot captureSnapshot(WorldView world) {
        List<BlockObservation> observations = new ArrayList<>();
        for (BlockFixture fixture : FIXTURES) {
            BlockState state = world.getBlockState(fixture.position());
            BlockState pedestal = world.getBlockState(fixture.pedestalPosition());
            observations.add(new BlockObservation(
                    Registries.BLOCK.getId(state.getBlock()).toString(),
                    stateDescription(state),
                    Registries.BLOCK.getId(pedestal.getBlock()).toString()
            ));
        }
        return new FixtureSnapshot(List.copyOf(observations));
    }

    private static String stateDescription(BlockState state) {
        return BlockArgumentParser.stringifyBlockState(state);
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

    private static String itemId(Item item) {
        return Registries.ITEM.getId(item).toString();
    }

    private static String stackDescription(List<ItemStack> stacks) {
        String value = stacks.stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> itemId(stack.getItem()) + "x" + stack.getCount())
                .sorted()
                .collect(Collectors.joining("+"));
        return value.isEmpty() ? "none" : value;
    }

    private static String positionDescription(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static String framebufferDescription(int width, int height) {
        return width + "x" + height;
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
                + ";on_ground=" + client.player.isOnGround()
                + ";tolerance=" + CAMERA_POSE_TOLERANCE;
    }

    private static BlockFixture fixture(String path, int x) {
        boolean slab = path.endsWith("_slab");
        boolean stairs = path.endsWith("_stairs");
        String blockClass = slab
                ? SlabBlock.class.getName()
                : stairs ? StairsBlock.class.getName() : Block.class.getName();
        int stateCount = slab ? 6 : stairs ? 80 : 1;
        String id = "etherology:" + path;
        String defaultState = id;
        if (slab) {
            defaultState += "[type=bottom,waterlogged=false]";
        } else if (stairs) {
            defaultState += "[facing=north,half=bottom,shape=straight,waterlogged=false]";
        }
        return new BlockFixture(
                new Identifier("etherology", path),
                new BlockPos(x, ARENA_FLOOR_Y + 2, 1),
                new BlockPos(x, ARENA_FLOOR_Y + 1, 1),
                blockClass,
                defaultState,
                stateCount,
                "attrahite".equals(path),
                slab,
                stairs
        );
    }

    private static List<Identifier> createReadyResources() {
        return List.of(
                resource("blockstates/attrahite.json"),
                resource("blockstates/attrahite_bricks.json"),
                resource("blockstates/attrahite_brick_slab.json"),
                resource("blockstates/attrahite_brick_stairs.json"),
                resource("models/block/attrahite.json"),
                resource("models/block/attrahite_bricks.json"),
                resource("models/block/attrahite_brick_slab.json"),
                resource("models/block/attrahite_brick_slab_top.json"),
                resource("models/block/attrahite_brick_stairs.json"),
                resource("models/block/attrahite_brick_stairs_inner.json"),
                resource("models/block/attrahite_brick_stairs_outer.json"),
                resource("models/item/attrahite.json"),
                resource("models/item/attrahite_bricks.json"),
                resource("models/item/attrahite_brick_slab.json"),
                resource("models/item/attrahite_brick_stairs.json"),
                resource("textures/block/attrahite.png"),
                resource("textures/block/attrahite_bricks.png")
        );
    }

    private static Identifier resource(String path) {
        return new Identifier("etherology", path);
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
            names.add("tag:mineable/pickaxe:" + fixture.id());
            names.add("tag:needs_stone_tool:" + fixture.id());
            names.add("tag:block/slabs:" + fixture.id());
            names.add("tag:item/slabs:" + fixture.id());
            names.add("tag:block/stairs:" + fixture.id());
            names.add("tag:item/stairs:" + fixture.id());
        }
        names.add("client_render_resources");
        names.add("packaged_root_jar:etherology");
        names.add("packaged_root_jar:" + HARNESS_MOD_ID);
        names.add("integrated_world_joined");
        names.add("server_arena_chunks_loaded");
        names.add("loot_tables_exact");
        names.add("standard_block_drops_exact");
        names.add("raw_plain_drops_deterministic");
        names.add("raw_silk_touch_drop_exact");
        names.add("raw_fortune_drops_deterministic");
        names.add("recipes_exact_and_craftable");
        names.add("advancements_exact");
        names.add("direct_block_item_placements_exact");
        names.add("initial_server_fixture_exact");
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

    private static Map<String, String> expectedStandardLoot() {
        Map<String, String> expected = new TreeMap<>();
        expected.put("etherology:attrahite_brick_slab",
                "etherology:attrahite_brick_slabx1");
        expected.put("etherology:attrahite_brick_stairs",
                "etherology:attrahite_brick_stairsx1");
        expected.put("etherology:attrahite_bricks", "etherology:attrahite_bricksx1");
        return Collections.unmodifiableMap(expected);
    }

    private static String expectedRawPlainLoot() {
        return "1=none,4096=none,4224=none,4640=etherology:enriched_attrahitex1,7168=none";
    }

    private static Map<String, String> expectedRawFortuneLoot() {
        Map<String, String> loot = new TreeMap<>();
        loot.put("1", "1=none,4096=etherology:enriched_attrahitex1,4224=none,"
                + "4640=etherology:enriched_attrahitex1,7168=none");
        loot.put("2", "1=none,4096=etherology:enriched_attrahitex1,"
                + "4224=etherology:enriched_attrahitex1,"
                + "4640=etherology:enriched_attrahitex1,7168=none");
        loot.put("3", "1=none,4096=etherology:enriched_attrahitex1,"
                + "4224=etherology:enriched_attrahitex1,"
                + "4640=etherology:enriched_attrahitex1,"
                + "7168=etherology:enriched_attrahitex1");
        return Collections.unmodifiableMap(loot);
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_SERVER_SETUP,
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

    private record BlockFixture(
            Identifier id,
            BlockPos position,
            BlockPos pedestalPosition,
            String blockClass,
            String defaultState,
            int stateCount,
            boolean needsStoneTool,
            boolean slab,
            boolean stairs
    ) {
    }

    private record RegistryEntry(
            boolean blockPresent,
            boolean itemPresent,
            String blockClass,
            String itemClass,
            boolean blockItemMapping,
            String defaultState,
            int stateCount,
            int defaultStateNetworkId,
            boolean pickaxeMineable,
            boolean needsStoneTool,
            boolean blockSlab,
            boolean itemSlab,
            boolean blockStairs,
            boolean itemStairs
    ) {

        private static RegistryEntry capture(BlockFixture fixture) {
            Block block = Registries.BLOCK.getOrEmpty(fixture.id()).orElse(null);
            Item item = Registries.ITEM.getOrEmpty(fixture.id()).orElse(null);
            if (block == null || item == null) return missing(fixture.id().toString());

            BlockState state = block.getDefaultState();
            ItemStack stack = new ItemStack(item);
            return new RegistryEntry(
                    true,
                    true,
                    block.getClass().getName(),
                    item.getClass().getName(),
                    item instanceof BlockItem blockItem
                            && blockItem.getBlock() == block
                            && block.asItem() == item,
                    stateDescription(state),
                    block.getStateManager().getStates().size(),
                    Block.STATE_IDS.getRawId(state),
                    state.isIn(BlockTags.PICKAXE_MINEABLE),
                    state.isIn(BlockTags.NEEDS_STONE_TOOL),
                    state.isIn(BlockTags.SLABS),
                    stack.isIn(ItemTags.SLABS),
                    state.isIn(BlockTags.STAIRS),
                    stack.isIn(ItemTags.STAIRS)
            );
        }

        private static RegistryEntry missing(String id) {
            return new RegistryEntry(
                    false,
                    false,
                    "",
                    "",
                    false,
                    id,
                    -1,
                    -1,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }
    }

    private record RegistryProbe(Map<String, RegistryEntry> entries) {

        private static RegistryProbe capture() {
            Map<String, RegistryEntry> entries = new LinkedHashMap<>();
            for (BlockFixture fixture : FIXTURES) {
                entries.put(fixture.id().toString(), RegistryEntry.capture(fixture));
            }
            return new RegistryProbe(
                    Collections.unmodifiableMap(new LinkedHashMap<>(entries))
            );
        }

        private boolean exact() {
            if (entries.size() != FIXTURES.size()) return false;
            for (BlockFixture fixture : FIXTURES) {
                RegistryEntry entry = entries.get(fixture.id().toString());
                if (entry == null
                        || !entry.blockPresent()
                        || !entry.itemPresent()
                        || !fixture.blockClass().equals(entry.blockClass())
                        || !BlockItem.class.getName().equals(entry.itemClass())
                        || !entry.blockItemMapping()
                        || !fixture.defaultState().equals(entry.defaultState())
                        || fixture.stateCount() != entry.stateCount()
                        || entry.defaultStateNetworkId() < 0
                        || !entry.pickaxeMineable()
                        || fixture.needsStoneTool() != entry.needsStoneTool()
                        || fixture.slab() != entry.blockSlab()
                        || fixture.slab() != entry.itemSlab()
                        || fixture.stairs() != entry.blockStairs()
                        || fixture.stairs() != entry.itemStairs()) {
                    return false;
                }
            }
            return true;
        }
    }

    private record DataProbe(
            List<String> lootTableIds,
            Map<String, String> standardLoot,
            String rawPlainLoot,
            String rawSilkLoot,
            Map<String, String> rawFortuneLoot,
            List<String> recipeIds,
            boolean recipesCraftable,
            List<String> advancementIds,
            boolean advancementContractsExact,
            String failure
    ) {

        private static DataProbe capture(MinecraftServer server) {
            try {
                List<String> lootIds = server.getLootManager()
                        .getIds(LootDataType.LOOT_TABLES)
                        .stream()
                        .map(Identifier::toString)
                        .filter(id -> id.startsWith("etherology:blocks/attrahite"))
                        .sorted()
                        .toList();
                Map<String, String> standardLoot = new TreeMap<>();
                for (BlockFixture fixture : FIXTURES) {
                    if (fixture.id().getPath().equals("attrahite")) continue;
                    Block block = requiredBlock(fixture.id());
                    standardLoot.put(
                            fixture.id().toString(),
                            generateLoot(
                                    server,
                                    block,
                                    new ItemStack(Items.IRON_PICKAXE),
                                    1L
                            )
                    );
                }
                Block raw = requiredBlock(new Identifier("etherology", "attrahite"));
                String plain = seededLoot(
                        server,
                        raw,
                        new ItemStack(Items.IRON_PICKAXE)
                );
                ItemStack silkTool = new ItemStack(Items.IRON_PICKAXE);
                silkTool.addEnchantment(Enchantments.SILK_TOUCH, 1);
                String silk = generateLoot(server, raw, silkTool, 1L);
                Map<String, String> fortune = new TreeMap<>();
                for (int level = 1; level <= 3; level++) {
                    ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
                    tool.addEnchantment(Enchantments.FORTUNE, level);
                    fortune.put(Integer.toString(level), seededLoot(server, raw, tool));
                }

                List<String> recipeIds = server.getRecipeManager().keys()
                        .map(Identifier::toString)
                        .filter(EXPECTED_RECIPE_IDS::contains)
                        .sorted()
                        .toList();
                boolean recipesCraftable = EXPECTED_RECIPE_IDS.equals(recipeIds);
                for (String id : recipeIds) {
                    Recipe<?> recipe = server.getRecipeManager()
                            .get(new Identifier(id))
                            .orElse(null);
                    recipesCraftable &= recipeMatchesExact(server, id, recipe);
                }

                List<String> advancementIds = server.getAdvancementLoader()
                        .getAdvancements()
                        .stream()
                        .map(Advancement::getId)
                        .map(Identifier::toString)
                        .filter(EXPECTED_ADVANCEMENT_IDS::contains)
                        .sorted()
                        .toList();
                boolean advancementsExact = EXPECTED_ADVANCEMENT_IDS.equals(advancementIds);
                for (String id : advancementIds) {
                    advancementsExact &= advancementExact(
                            id,
                            server.getAdvancementLoader().get(new Identifier(id))
                    );
                }
                return new DataProbe(
                        lootIds,
                        Collections.unmodifiableMap(new TreeMap<>(standardLoot)),
                        plain,
                        silk,
                        Collections.unmodifiableMap(new TreeMap<>(fortune)),
                        recipeIds,
                        recipesCraftable,
                        advancementIds,
                        advancementsExact,
                        ""
                );
            } catch (RuntimeException exception) {
                return new DataProbe(
                        List.of(),
                        Map.of(),
                        "",
                        "",
                        Map.of(),
                        List.of(),
                        false,
                        List.of(),
                        false,
                        exception.getClass().getName() + ": " + exception.getMessage()
                );
            }
        }

        private static DataProbe missing() {
            return new DataProbe(
                    List.of(),
                    Map.of(),
                    "",
                    "",
                    Map.of(),
                    List.of(),
                    false,
                    List.of(),
                    false,
                    "not captured"
            );
        }

        private boolean lootTableIdsExact() {
            return EXPECTED_LOOT_TABLE_IDS.equals(lootTableIds);
        }

        private boolean standardLootExact() {
            return expectedStandardLoot().equals(standardLoot);
        }

        private boolean plainLootExact() {
            return expectedRawPlainLoot().equals(rawPlainLoot);
        }

        private boolean silkLootExact() {
            return "etherology:attrahitex1".equals(rawSilkLoot);
        }

        private boolean fortuneLootExact() {
            return expectedRawFortuneLoot().equals(rawFortuneLoot);
        }

        private boolean recipesExact() {
            return EXPECTED_RECIPE_IDS.equals(recipeIds) && recipesCraftable;
        }

        private boolean advancementsExact() {
            return EXPECTED_ADVANCEMENT_IDS.equals(advancementIds)
                    && advancementContractsExact;
        }

        private boolean exact() {
            return failure.isEmpty()
                    && lootTableIdsExact()
                    && standardLootExact()
                    && plainLootExact()
                    && silkLootExact()
                    && fortuneLootExact()
                    && recipesExact()
                    && advancementsExact();
        }

        private boolean sameOutcome(DataProbe other) {
            return lootTableIds.equals(other.lootTableIds)
                    && standardLoot.equals(other.standardLoot)
                    && rawPlainLoot.equals(other.rawPlainLoot)
                    && rawSilkLoot.equals(other.rawSilkLoot)
                    && rawFortuneLoot.equals(other.rawFortuneLoot)
                    && recipeIds.equals(other.recipeIds)
                    && recipesCraftable == other.recipesCraftable
                    && advancementIds.equals(other.advancementIds)
                    && advancementContractsExact == other.advancementContractsExact;
        }

        private static String seededLoot(
                MinecraftServer server,
                Block block,
                ItemStack tool
        ) {
            return RAW_LOOT_SEEDS.stream()
                    .map(seed -> seed + "=" + generateLoot(server, block, tool, seed))
                    .collect(Collectors.joining(","));
        }

        private static String generateLoot(
                MinecraftServer server,
                Block block,
                ItemStack tool,
                long seed
        ) {
            LootTable table = server.getLootManager().getLootTable(block.getLootTableId());
            LootContextParameterSet parameters = new LootContextParameterSet.Builder(
                    server.getOverworld()
            )
                    .add(LootContextParameters.BLOCK_STATE, block.getDefaultState())
                    .add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                    .add(LootContextParameters.TOOL, tool)
                    .build(LootContextTypes.BLOCK);
            return stackDescription(table.generateLoot(parameters, seed));
        }

        private static boolean recipeMatchesExact(
                MinecraftServer server,
                String id,
                Recipe<?> recipe
        ) {
            if (recipe == null || !id.equals(recipe.getId().toString())) return false;

            ItemStack expectedOutput = expectedOutput(id);
            ItemStack declared = recipe.getOutput(server.getRegistryManager());
            String typeId = Registries.RECIPE_TYPE.getId(recipe.getType()).toString();
            String serializerId = Registries.RECIPE_SERIALIZER
                    .getId(recipe.getSerializer())
                    .toString();
            if (!sameStack(expectedOutput, declared)
                    || !expectedRecipeType(id).equals(typeId)
                    || !expectedRecipeSerializer(id).equals(serializerId)
                    || !recipe.getGroup().isEmpty()
                    || !recipe.showNotification()) {
                return false;
            }
            if (recipe instanceof AbstractCookingRecipe cooking) {
                ItemStack input = new ItemStack(expectedCookingInput(id));
                SimpleInventory inventory = new SimpleInventory(input);
                int expectedTime = id.endsWith("_from_blasting") ? 100 : 200;
                float expectedExperience = id.equals("etherology:attrahite_brick")
                        ? 0.1F
                        : 0.3F;
                return cooking.getIngredients().size() == 1
                        && cooking.getIngredients().get(0).test(input)
                        && !cooking.getIngredients().get(0).test(new ItemStack(Items.STONE))
                        && cooking.matches(inventory, server.getOverworld())
                        && sameStack(
                                expectedOutput,
                                cooking.craft(inventory, server.getRegistryManager())
                        )
                        && cooking.getCookTime() == expectedTime
                        && Float.compare(cooking.getExperience(), expectedExperience) == 0;
            }
            if (recipe instanceof StonecuttingRecipe stonecutting) {
                ItemStack input = new ItemStack(requiredItem(
                        new Identifier("etherology", "attrahite_bricks")
                ));
                SimpleInventory inventory = new SimpleInventory(input);
                return stonecutting.getIngredients().size() == 1
                        && stonecutting.getIngredients().get(0).test(input)
                        && !stonecutting.getIngredients().get(0).test(
                                new ItemStack(Items.STONE)
                        )
                        && stonecutting.matches(inventory, server.getOverworld())
                        && sameStack(
                                expectedOutput,
                                stonecutting.craft(inventory, server.getRegistryManager())
                        );
            }
            if (recipe instanceof ShapedRecipe shaped) {
                CraftingInventory inventory = craftingInventory(id);
                return shaped.getWidth() == inventory.getWidth()
                        && shaped.getHeight() == inventory.getHeight()
                        && shapedIngredientsExact(id, shaped)
                        && shaped.matches(inventory, server.getOverworld())
                        && sameStack(
                                expectedOutput,
                                shaped.craft(inventory, server.getRegistryManager())
                        );
            }
            return false;
        }

        private static CraftingInventory craftingInventory(String id) {
            List<String> ingredients = expectedShapedIngredients(id);
            int width = id.equals("etherology:attrahite_brick_slab")
                    || id.equals("etherology:attrahite_brick_stairs") ? 3 : 2;
            int height = id.equals("etherology:attrahite_brick_slab")
                    ? 1
                    : id.equals("etherology:attrahite_brick_stairs") ? 3 : 2;
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(
                    width * height,
                    ItemStack.EMPTY
            );
            for (int index = 0; index < ingredients.size(); index++) {
                String ingredient = ingredients.get(index);
                if (ingredient.equals("empty")) continue;
                stacks.set(index, new ItemStack(expectedIngredientItem(ingredient)));
            }
            return new CraftingInventory(null, width, height, stacks);
        }

        private static List<String> expectedShapedIngredients(String id) {
            return switch (id) {
                case "etherology:attrahite_bricks" -> List.of(
                        "attrahite_brick", "attrahite_brick",
                        "attrahite_brick", "attrahite_brick"
                );
                case "etherology:attrahite_brick_slab" -> List.of(
                        "attrahite_bricks", "attrahite_bricks", "attrahite_bricks"
                );
                case "etherology:attrahite_brick_stairs" -> List.of(
                        "attrahite_bricks", "empty", "empty",
                        "attrahite_bricks", "attrahite_bricks", "empty",
                        "attrahite_bricks", "attrahite_bricks", "attrahite_bricks"
                );
                case "etherology:raw_azel" -> List.of(
                        "enriched_attrahite", "calcite", "calcite", "enriched_attrahite"
                );
                default -> throw new IllegalArgumentException("Unexpected shaped recipe " + id);
            };
        }

        private static boolean shapedIngredientsExact(String id, ShapedRecipe recipe) {
            List<String> expected = expectedShapedIngredients(id);
            if (recipe.getIngredients().size() != expected.size()) return false;

            for (int index = 0; index < expected.size(); index++) {
                String expectedIngredient = expected.get(index);
                net.minecraft.recipe.Ingredient actual = recipe.getIngredients().get(index);
                if (expectedIngredient.equals("empty")) {
                    if (!actual.isEmpty()) return false;
                    continue;
                }
                if (actual.isEmpty()
                        || !actual.test(new ItemStack(expectedIngredientItem(expectedIngredient)))
                        || actual.test(new ItemStack(Items.DIRT))) {
                    return false;
                }
            }
            return true;
        }

        private static Item expectedIngredientItem(String path) {
            return path.equals("calcite")
                    ? Items.CALCITE
                    : requiredItem(new Identifier("etherology", path));
        }

        private static String expectedRecipeType(String id) {
            if (id.endsWith("_from_blasting")) return "minecraft:blasting";
            if (id.equals("etherology:attrahite_brick")
                    || id.equals("etherology:azel_ingot")) {
                return "minecraft:smelting";
            }
            if (id.endsWith("_stonecutting")) return "minecraft:stonecutting";
            return "minecraft:crafting";
        }

        private static String expectedRecipeSerializer(String id) {
            String type = expectedRecipeType(id);
            return type.equals("minecraft:crafting")
                    ? "minecraft:crafting_shaped"
                    : type;
        }

        private static Item expectedCookingInput(String id) {
            return id.equals("etherology:attrahite_brick")
                    ? requiredItem(new Identifier("etherology", "attrahite"))
                    : requiredItem(new Identifier("etherology", "raw_azel"));
        }

        private static ItemStack expectedOutput(String id) {
            return switch (id) {
                case "etherology:attrahite_brick" -> stack("attrahite_brick", 1);
                case "etherology:attrahite_bricks" -> stack("attrahite_bricks", 1);
                case "etherology:attrahite_brick_slab" -> stack(
                        "attrahite_brick_slab",
                        6
                );
                case "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting" ->
                        stack("attrahite_brick_slab", 2);
                case "etherology:attrahite_brick_stairs" -> stack(
                        "attrahite_brick_stairs",
                        4
                );
                case "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting" ->
                        stack("attrahite_brick_stairs", 1);
                case "etherology:raw_azel" -> stack("raw_azel", 1);
                case "etherology:azel_ingot", "etherology:azel_ingot_from_blasting" ->
                        stack("azel_ingot", 1);
                default -> throw new IllegalArgumentException("Unexpected recipe " + id);
            };
        }

        private static ItemStack stack(String path, int count) {
            return new ItemStack(requiredItem(new Identifier("etherology", path)), count);
        }

        private static boolean sameStack(ItemStack first, ItemStack second) {
            return first.getItem() == second.getItem()
                    && first.getCount() == second.getCount();
        }

        private static boolean advancementExact(String id, Advancement advancement) {
            if (advancement == null || advancement.getParent() == null) return false;

            String recipeId = id
                    .replace("etherology:recipes/building_blocks/", "etherology:")
                    .replace("etherology:recipes/misc/", "etherology:");
            String ingredientCriterion = id.contains("brick_slab")
                    || id.contains("brick_stairs")
                    ? "has_attrahite_bricks"
                    : "has_attrahite";
            Set<String> expectedCriteria = Set.of(ingredientCriterion, "has_the_recipe");
            List<String> rewards = Arrays.stream(advancement.getRewards().getRecipes())
                    .map(Identifier::toString)
                    .sorted()
                    .toList();
            String[][] requirements = advancement.getRequirements();
            return advancement.getId().toString().equals(id)
                    && advancement.getParent().getId().toString()
                            .equals("minecraft:recipes/root")
                    && advancement.getCriteria().keySet().equals(expectedCriteria)
                    && requirements.length == 1
                    && Set.of(requirements[0]).equals(expectedCriteria)
                    && rewards.equals(List.of(recipeId))
                    && !advancement.sendsTelemetryEvent();
        }
    }

    private record BlockObservation(String id, String state, String pedestalId) {

        private boolean exact(BlockFixture fixture) {
            return fixture.id().toString().equals(id)
                    && fixture.defaultState().equals(state)
                    && PEDESTAL_ID.toString().equals(pedestalId);
        }
    }

    private record FixtureSnapshot(List<BlockObservation> observations) {

        private boolean exact() {
            if (observations.size() != FIXTURES.size()) return false;
            for (int index = 0; index < FIXTURES.size(); index++) {
                if (!observations.get(index).exact(FIXTURES.get(index))) return false;
            }
            return true;
        }

        private String description() {
            List<String> entries = new ArrayList<>();
            for (int index = 0; index < observations.size(); index++) {
                BlockObservation observation = observations.get(index);
                entries.add(FIXTURES.get(index).id() + "=" + observation.state()
                        + "|pedestal=" + observation.pedestalId());
            }
            return String.join(";", entries);
        }

        private static String expectedDescription() {
            List<String> entries = new ArrayList<>();
            for (BlockFixture fixture : FIXTURES) {
                entries.add(fixture.id() + "=" + fixture.defaultState()
                        + "|pedestal=" + PEDESTAL_ID);
            }
            return String.join(";", entries);
        }
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

        private boolean exact(BlockFixture fixture) {
            return "CONSUME".equals(actionResult)
                    && accepted
                    && beforeCount == 1
                    && afterCount == 0
                    && blockItemMapping
                    && fixture.id().toString().equals(placedId)
                    && fixture.defaultState().equals(placedState);
        }
    }

    private record PlacementInventory(Map<String, PlacementEvidence> entries) {

        private static PlacementInventory missing() {
            return new PlacementInventory(Map.of());
        }

        private boolean exact() {
            if (entries.size() != FIXTURES.size()) return false;
            for (BlockFixture fixture : FIXTURES) {
                PlacementEvidence placement = entries.get(fixture.id().toString());
                if (placement == null || !placement.exact(fixture)) return false;
            }
            return true;
        }

        private String description() {
            return entries.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(";"));
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

    record LightSnapshot(
            boolean observed,
            boolean pendingUpdates,
            List<Integer> skyLevels,
            List<Integer> blockLevels
    ) {

        LightSnapshot {
            skyLevels = List.copyOf(skyLevels);
            blockLevels = List.copyOf(blockLevels);
        }

        boolean hasExpectedSamples() {
            if (!observed
                    || skyLevels.size() != LIGHT_SAMPLE_POSITIONS.size()
                    || blockLevels.size() != LIGHT_SAMPLE_POSITIONS.size()) {
                return false;
            }
            for (int index = 0; index < LIGHT_SAMPLE_POSITIONS.size(); index++) {
                if (!isExpectedSkyLight(index, skyLevels.get(index))
                        || !isExpectedBlockLight(index, blockLevels.get(index))) {
                    return false;
                }
            }
            return true;
        }

        boolean sameSamples(LightSnapshot other) {
            return observed
                    && other.observed
                    && skyLevels.equals(other.skyLevels)
                    && blockLevels.equals(other.blockLevels);
        }

        String description() {
            return "observed=" + observed
                    + ";pending=" + pendingUpdates
                    + ";sky=" + sampleDescription(skyLevels)
                    + ";block=" + sampleDescription(blockLevels);
        }

        static LightSnapshot missing() {
            return new LightSnapshot(false, false, List.of(), List.of());
        }

        static LightSnapshot expected(boolean pendingUpdates) {
            return new LightSnapshot(
                    true,
                    pendingUpdates,
                    EXPECTED_SKY_LIGHT_LEVELS,
                    EXPECTED_BLOCK_LIGHT_LEVELS
            );
        }

        private static String sampleDescription(List<Integer> levels) {
            List<String> entries = new ArrayList<>();
            for (int index = 0;
                    index < Math.min(levels.size(), LIGHT_SAMPLE_POSITIONS.size());
                    index++) {
                entries.add(positionDescription(LIGHT_SAMPLE_POSITIONS.get(index))
                        + "=" + levels.get(index));
            }
            return "[" + String.join(",", entries) + "]";
        }
    }

    record LightingEvidence(
            int stableServerTicks,
            int stableClientTicks,
            LightSnapshot server,
            LightSnapshot client
    ) {

        boolean exact() {
            return stableServerTicks == REQUIRED_LIGHTING_READY_SERVER_TICKS
                    && stableClientTicks == REQUIRED_LIGHTING_READY_CLIENT_TICKS
                    && server.hasExpectedSamples()
                    && client.sameSamples(server);
        }

        String assertionActual() {
            return exact() ? expectedDescription() : diagnosticDescription();
        }

        String diagnosticDescription() {
            return "stableServerTicks=" + stableServerTicks
                    + ";stableClientTicks=" + stableClientTicks
                    + ";server=" + server.description()
                    + ";client=" + client.description();
        }

        static String expectedDescription() {
            LightSnapshot expected = LightSnapshot.expected(false);
            return "stableServerTicks=" + REQUIRED_LIGHTING_READY_SERVER_TICKS
                    + ";stableClientTicks=" + REQUIRED_LIGHTING_READY_CLIENT_TICKS
                    + ";serverSky=" + LightSnapshot.sampleDescription(expected.skyLevels())
                    + ";serverBlock=" + LightSnapshot.sampleDescription(expected.blockLevels())
                    + ";clientSky=" + LightSnapshot.sampleDescription(expected.skyLevels())
                    + ";clientBlock=" + LightSnapshot.sampleDescription(expected.blockLevels());
        }

        static LightingEvidence missing() {
            return new LightingEvidence(
                    0,
                    0,
                    LightSnapshot.missing(),
                    LightSnapshot.missing()
            );
        }
    }

    private record ScreenshotResult(boolean passed, long size, String sha256, String failure) {

        private static ScreenshotResult failed(String failure) {
            return new ScreenshotResult(
                    false,
                    0L,
                    "",
                    failure == null ? "unknown error" : failure
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

    private record ServerLightingResult(int stableTicks, LightSnapshot snapshot) {
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
