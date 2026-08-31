package dev.theplumteam.etherology.e2e.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DatapackFailureScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

final class MetalBlockRegistryScenario implements ClientScenario {

    static final String SCENARIO_ID = "metal-block-registry";
    static final String WORLD_DIRECTORY_NAME = "etherology-e2e-metal-block-registry-world";
    static final String WORLD_DISPLAY_NAME = "Etherology E2E Metal Blocks";
    static final String BEFORE_SCREENSHOT_FILE_NAME = "metal-block-registry-before.png";
    static final String AFTER_SCREENSHOT_FILE_NAME = "metal-block-registry-after.png";
    static final long WORLD_SEED = 0x4D4554414CL;
    static final int REQUIRED_COMPLETED_RENDERS = 120;

    private static final Logger LOGGER = LoggerFactory.getLogger("EtherologyE2EHarness");
    private static final String HARNESS_MOD_ID = "etherology_e2e_harness";
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final double CAMERA_X = 0.5;
    private static final double CAMERA_Y = 121.0;
    private static final double CAMERA_Z = -5.5;
    private static final float CAMERA_YAW = 0.0f;
    private static final float CAMERA_PITCH = 2.0f;
    private static final double CAMERA_POSE_TOLERANCE = 0.0001;
    private static final Identifier AIR_ID = new Identifier("minecraft", "air");
    private static final Identifier PEDESTAL_BLOCK_ID =
            new Identifier("minecraft", "polished_andesite");
    private static final BlockPos CAMERA_BLOCK_POS = new BlockPos(0, ARENA_FLOOR_Y + 1, -6);
    private static final List<MetalBlockFixture> METAL_BLOCKS = List.of(
            fixture("azel_block", -2),
            fixture("ethril_block", 0),
            fixture("ebony_block", 2)
    );
    static final List<Identifier> READY_RESOURCES = createReadyResources();
    static final List<String> ASSERTION_NAMES = createAssertionNames();

    private Stage stage = Stage.WAITING_FOR_TITLE;
    private int clientTicks;
    private int stageClientTicks;
    private int completedBeforeRenders;
    private int completedAfterRenders;
    private int beforeFramebufferWidth;
    private int beforeFramebufferHeight;
    private int afterFramebufferWidth;
    private int afterFramebufferHeight;
    private boolean resourcesReady;
    private boolean worldSetupSubmitted;
    private boolean placementSubmitted;
    private boolean saveSubmitted;
    private boolean beforeFixtureExact;
    private boolean afterClientFixtureExact;
    private boolean beforeCaptureRenderReady;
    private boolean beforeCaptureCameraExact;
    private boolean afterCaptureRenderReady;
    private boolean afterCaptureCameraExact;
    private String beforeCaptureCameraPose = "not captured";
    private String afterCaptureCameraPose = "not captured";
    private String lifecycleFailure = "";
    private List<Identifier> missingResources = List.of();
    private EvidenceLayout evidenceLayout;
    private DefaultStateIdSnapshot defaultStateIdSnapshot;
    private FixtureIdSnapshot beforeClientIds;
    private FixtureIdSnapshot beforeClientPedestalIds;
    private FixtureIdSnapshot afterClientIds;
    private volatile ScreenshotResult beforeScreenshotResult;
    private volatile ScreenshotResult afterScreenshotResult;
    private volatile ServerSetupResult serverSetupResult;
    private volatile String serverSetupFailure = "";
    private volatile PlacementResult placementResult;
    private volatile String placementFailure = "";
    private volatile Boolean saveResult;
    private volatile String saveFailure = "";

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
                case WAITING_FOR_BEFORE_CLIENT_MIRROR -> tickWaitingForBeforeClientMirror(client);
                case WAITING_FOR_BEFORE_RENDERS -> tickWaitingForBeforeRenders(client);
                case CAPTURING_BEFORE -> tickCapturingBefore(client);
                case WAITING_FOR_PLACEMENT -> tickWaitingForPlacement(client);
                case WAITING_FOR_AFTER_CLIENT_MIRROR -> tickWaitingForAfterClientMirror(client);
                case WAITING_FOR_AFTER_RENDERS -> tickWaitingForAfterRenders(client);
                case CAPTURING_AFTER -> tickCapturingAfter(client);
                case SAVING_WORLD -> tickSavingWorld(client);
                case COMPLETE -> {
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Etherology metal-block-registry failed while in {}", stage, exception);
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
    public void onGameRenderCompleted() {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (stage == Stage.WAITING_FOR_BEFORE_RENDERS) {
                completedBeforeRenders = nextStableRenderCount(
                        completedBeforeRenders,
                        isBeforeCaptureStateExact(client)
                );
                if (completedBeforeRenders == REQUIRED_COMPLETED_RENDERS) {
                    captureBefore(client);
                }
                return;
            }
            if (stage != Stage.WAITING_FOR_AFTER_RENDERS) return;

            completedAfterRenders = nextStableRenderCount(
                    completedAfterRenders,
                    isAfterCaptureStateExact(client)
            );
            if (completedAfterRenders == REQUIRED_COMPLETED_RENDERS) {
                captureAfter(client);
            }
        } catch (RuntimeException exception) {
            if (stage == Stage.WAITING_FOR_BEFORE_RENDERS) {
                completedBeforeRenders = 0;
            } else if (stage == Stage.WAITING_FOR_AFTER_RENDERS) {
                completedAfterRenders = 0;
            }
            LOGGER.error("Etherology metal-block-registry render callback failed", exception);
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

    private void tickWaitingForTitle(MinecraftClient client) {
        if (client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) return;
        if (!hasExpectedFramebuffer(client)) return;

        missingResources = findMissingResources(client);
        resourcesReady = missingResources.isEmpty();
        if (!resourcesReady) {
            fail(client, "The loaded resources are missing " + missingResources);
            return;
        }

        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            if (!Registries.BLOCK.containsId(fixture.id())) {
                fail(client, "The registry is missing block " + fixture.id());
                return;
            }
        }
        defaultStateIdSnapshot = inspectDefaultStateNetworkIds();
        if (!defaultStateIdSnapshot.allNonNegative()) {
            fail(
                    client,
                    "Metal-block default states are missing network IDs: "
                            + defaultStateIdSnapshot.description()
            );
            return;
        }

        try {
            ensureEvidenceLayout(client);
        } catch (IOException exception) {
            LOGGER.error("Cannot use the isolated metal-block-registry evidence layout", exception);
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
            fail(client, "Refusing to reuse the metal-block-registry save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "The client already has a world before metal-block-registry creation");
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
            fail(client, "Minecraft rejected Etherology's server data packs");
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
        server.execute(() -> setupInitialFixture(server, playerId));
        transition(Stage.WAITING_FOR_SERVER_SETUP);
    }

    private void tickWaitingForServerSetup(MinecraftClient client) {
        if (!serverSetupFailure.isEmpty()) {
            fail(client, "Initial server fixture setup failed: " + serverSetupFailure);
            return;
        }
        if (serverSetupResult == null) return;

        transition(Stage.WAITING_FOR_BEFORE_CLIENT_MIRROR);
    }

    private void tickWaitingForBeforeClientMirror(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) return;
        if (!hasClientDisplayIds(client, false) || !hasClientPedestals(client)) return;

        beforeClientIds = captureClientDisplayIds(client);
        beforeClientPedestalIds = captureClientPedestalIds(client);
        ServerSetupResult setup = serverSetupResult;
        beforeFixtureExact = setup != null
                && setup.beforeDisplayIds().matchesAir()
                && setup.pedestalIds().matchesPedestals()
                && beforeClientIds.matchesAir()
                && beforeClientPedestalIds.matchesPedestals();
        if (!beforeFixtureExact) {
            fail(client, "The empty pedestal fixture does not match its exact IDs");
            return;
        }

        transition(Stage.WAITING_FOR_BEFORE_RENDERS);
    }

    private void tickWaitingForBeforeRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before the empty capture");
        }
    }

    private void tickCapturingBefore(MinecraftClient client) {
        ScreenshotResult result = beforeScreenshotResult;
        if (result == null) return;
        if (!result.passed()) {
            fail(client, "The empty pedestal screenshot failed: " + result.failure());
            return;
        }

        submitMetalBlockPlacement(client);
    }

    private void tickWaitingForPlacement(MinecraftClient client) {
        if (!placementFailure.isEmpty()) {
            fail(client, "Server metal-block placement failed: " + placementFailure);
            return;
        }
        if (placementResult == null) return;

        transition(Stage.WAITING_FOR_AFTER_CLIENT_MIRROR);
    }

    private void tickWaitingForAfterClientMirror(MinecraftClient client) {
        if (!isWorldLifecycleReady(client) || !hasClientDisplayIds(client, true)) return;

        afterClientIds = captureClientDisplayIds(client);
        afterClientFixtureExact = afterClientIds.matchesMetalBlocks();
        if (!afterClientFixtureExact) {
            fail(client, "The placed metal blocks do not match the exact client IDs");
            return;
        }
        transition(Stage.WAITING_FOR_AFTER_RENDERS);
    }

    private void tickWaitingForAfterRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before the placed capture");
        }
    }

    private void tickCapturingAfter(MinecraftClient client) {
        ScreenshotResult result = afterScreenshotResult;
        if (result == null) return;
        if (!result.passed()) {
            fail(client, "The placed metal-block screenshot failed: " + result.failure());
            return;
        }

        submitSave(client);
    }

    private void tickSavingWorld(MinecraftClient client) {
        if (!saveFailure.isEmpty()) {
            fail(client, "The integrated-world save failed: " + saveFailure);
            return;
        }
        if (saveResult == null) return;

        publish(client);
    }

    private void captureBefore(MinecraftClient client) {
        if (stage != Stage.WAITING_FOR_BEFORE_RENDERS) return;
        if (!isBeforeCaptureStateExact(client)) {
            completedBeforeRenders = 0;
            return;
        }

        beforeClientIds = captureClientDisplayIds(client);
        beforeClientPedestalIds = captureClientPedestalIds(client);
        beforeCaptureRenderReady = isFixtureRenderReady(client);
        beforeCaptureCameraExact = hasExpectedCameraPose(client);
        beforeCaptureCameraPose = cameraPoseDescription(client);
        beforeFramebufferWidth = client.getFramebuffer().textureWidth;
        beforeFramebufferHeight = client.getFramebuffer().textureHeight;
        transition(Stage.CAPTURING_BEFORE);
        saveScreenshot(
                client,
                BEFORE_SCREENSHOT_FILE_NAME,
                result -> beforeScreenshotResult = result
        );
    }

    private void captureAfter(MinecraftClient client) {
        if (stage != Stage.WAITING_FOR_AFTER_RENDERS) return;
        if (!isAfterCaptureStateExact(client)) {
            completedAfterRenders = 0;
            return;
        }

        afterClientIds = captureClientDisplayIds(client);
        afterClientFixtureExact = afterClientIds.matchesMetalBlocks();
        afterCaptureRenderReady = isFixtureRenderReady(client);
        afterCaptureCameraExact = hasExpectedCameraPose(client);
        afterCaptureCameraPose = cameraPoseDescription(client);
        afterFramebufferWidth = client.getFramebuffer().textureWidth;
        afterFramebufferHeight = client.getFramebuffer().textureHeight;
        transition(Stage.CAPTURING_AFTER);
        saveScreenshot(
                client,
                AFTER_SCREENSHOT_FILE_NAME,
                result -> afterScreenshotResult = result
        );
    }

    private void saveScreenshot(
            MinecraftClient client,
            String fileName,
            Consumer<ScreenshotResult> resultConsumer
    ) {
        EvidenceLayout layout = evidenceLayout;
        if (layout == null) {
            resultConsumer.accept(
                    ScreenshotResult.failed("The evidence layout was not initialized")
            );
            return;
        }

        ScreenshotRecorder.saveScreenshot(
                layout.scenarioRoot().toFile(),
                fileName,
                client.getFramebuffer(),
                message -> resultConsumer.accept(inspectScreenshot(layout.screenshotPath(fileName)))
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
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot inspect a metal-block-registry screenshot", exception);
            return ScreenshotResult.failed(
                    "Screenshot inspection raised " + exception.getClass().getSimpleName()
            );
        }
    }

    private void setupInitialFixture(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                throw new IllegalStateException("The integrated server has no matching player");
            }

            boolean chunkLoaded = loadArenaChunks(world);
            world.setTimeOfDay(6000L);
            world.setWeather(6000, 0, false, false);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);

            clearArena(world);
            buildArena(world);
            FixtureIdSnapshot beforeDisplayIds = captureServerDisplayIds(world);
            FixtureIdSnapshot pedestalIds = captureServerPedestalIds(world);

            player.changeGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(true);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            player.teleport(
                    world,
                    CAMERA_X,
                    CAMERA_Y,
                    CAMERA_Z,
                    CAMERA_YAW,
                    CAMERA_PITCH
            );
            player.setSpawnPoint(World.OVERWORLD, CAMERA_BLOCK_POS, CAMERA_YAW, true, false);

            serverSetupResult = new ServerSetupResult(
                    chunkLoaded,
                    beforeDisplayIds,
                    pedestalIds,
                    server.getSaveProperties().getLevelName(),
                    world.getSeed(),
                    world.getRegistryKey().getValue().toString()
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot arrange the empty metal-block-registry fixture", exception);
            serverSetupFailure = exception.getClass().getSimpleName()
                    + ": " + exception.getMessage();
        }
    }

    private void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-8, ARENA_FLOOR_Y, -8);
        BlockPos end = new BlockPos(8, ARENA_FLOOR_Y + 8, 8);
        for (BlockPos position : BlockPos.iterate(start, end)) {
            world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
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

    private void buildArena(ServerWorld world) {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                Block floorBlock = (x + z) % 7 == 0
                        ? Blocks.SEA_LANTERN
                        : Blocks.SMOOTH_STONE;
                world.setBlockState(
                        new BlockPos(x, ARENA_FLOOR_Y, z),
                        floorBlock.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
        }
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            world.setBlockState(
                    fixture.pedestalPosition(),
                    Blocks.POLISHED_ANDESITE.getDefaultState(),
                    Block.NOTIFY_ALL
            );
            world.setBlockState(
                    fixture.displayPosition(),
                    Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_ALL
            );
        }
    }

    private void submitMetalBlockPlacement(MinecraftClient client) {
        if (placementSubmitted) return;

        IntegratedServer server = client.getServer();
        if (server == null || !server.isRunning() || server.isStopping()) {
            fail(client, "The integrated server stopped before metal-block placement");
            return;
        }

        placementSubmitted = true;
        server.execute(() -> placeMetalBlocks(server));
        transition(Stage.WAITING_FOR_PLACEMENT);
    }

    private void placeMetalBlocks(IntegratedServer server) {
        try {
            ServerWorld world = server.getOverworld();
            for (MetalBlockFixture fixture : METAL_BLOCKS) {
                Block block = Registries.BLOCK.get(fixture.id());
                world.setBlockState(
                        fixture.displayPosition(),
                        block.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
            placementResult = new PlacementResult(captureServerDisplayIds(world));
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot place the registered metal blocks", exception);
            placementFailure = exception.getClass().getSimpleName()
                    + ": " + exception.getMessage();
        }
    }

    private boolean isBeforeCaptureStateExact(MinecraftClient client) {
        return isWorldViewReady(client)
                && beforeFixtureExact
                && hasClientDisplayIds(client, false)
                && hasClientPedestals(client)
                && isFixtureRenderReady(client)
                && hasExpectedCameraPose(client);
    }

    private boolean isAfterCaptureStateExact(MinecraftClient client) {
        PlacementResult placement = placementResult;
        return isWorldViewReady(client)
                && placement != null
                && placement.serverDisplayIds().matchesMetalBlocks()
                && hasClientDisplayIds(client, true)
                && hasClientPedestals(client)
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
        if (!client.worldRenderer.isTerrainRenderComplete()) return false;

        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            if (!client.worldRenderer.isRenderingReady(fixture.pedestalPosition())
                    || !client.worldRenderer.isRenderingReady(fixture.displayPosition())) {
                return false;
            }
        }
        return true;
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

    private boolean hasClientDisplayIds(MinecraftClient client, boolean placed) {
        if (client.world == null) return false;

        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            Identifier actualId = Registries.BLOCK.getId(
                    client.world.getBlockState(fixture.displayPosition()).getBlock()
            );
            Identifier expectedId = placed ? fixture.id() : AIR_ID;
            if (!expectedId.equals(actualId)) return false;
        }
        return true;
    }

    private boolean hasClientPedestals(MinecraftClient client) {
        if (client.world == null) return false;

        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            Identifier actualId = Registries.BLOCK.getId(
                    client.world.getBlockState(fixture.pedestalPosition()).getBlock()
            );
            if (!PEDESTAL_BLOCK_ID.equals(actualId)) return false;
        }
        return true;
    }

    private FixtureIdSnapshot captureServerDisplayIds(ServerWorld world) {
        List<Identifier> ids = new ArrayList<>();
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            ids.add(Registries.BLOCK.getId(
                    world.getBlockState(fixture.displayPosition()).getBlock()
            ));
        }
        return new FixtureIdSnapshot(List.copyOf(ids));
    }

    private FixtureIdSnapshot captureServerPedestalIds(ServerWorld world) {
        List<Identifier> ids = new ArrayList<>();
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            ids.add(Registries.BLOCK.getId(
                    world.getBlockState(fixture.pedestalPosition()).getBlock()
            ));
        }
        return new FixtureIdSnapshot(List.copyOf(ids));
    }

    private FixtureIdSnapshot captureClientDisplayIds(MinecraftClient client) {
        List<Identifier> ids = new ArrayList<>();
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            ids.add(Registries.BLOCK.getId(
                    client.world.getBlockState(fixture.displayPosition()).getBlock()
            ));
        }
        return new FixtureIdSnapshot(List.copyOf(ids));
    }

    private FixtureIdSnapshot captureClientPedestalIds(MinecraftClient client) {
        List<Identifier> ids = new ArrayList<>();
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            ids.add(Registries.BLOCK.getId(
                    client.world.getBlockState(fixture.pedestalPosition()).getBlock()
            ));
        }
        return new FixtureIdSnapshot(List.copyOf(ids));
    }

    private DefaultStateIdSnapshot inspectDefaultStateNetworkIds() {
        List<Integer> rawIds = new ArrayList<>();
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            Block block = Registries.BLOCK.get(fixture.id());
            rawIds.add(Block.STATE_IDS.getRawId(block.getDefaultState()));
        }
        return new DefaultStateIdSnapshot(List.copyOf(rawIds));
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
                saveResult = server.saveAll(false, true, true);
            } catch (RuntimeException exception) {
                LOGGER.error("Cannot force-save the metal-block-registry world", exception);
                saveFailure = exception.getClass().getSimpleName()
                        + ": " + exception.getMessage();
            }
        });
        transition(Stage.SAVING_WORLD);
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;

        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Etherology metal-block-registry lifecycle failure: {}", lifecycleFailure);
        publish(client);
    }

    private void publish(MinecraftClient client) {
        if (stage == Stage.COMPLETE) return;

        try {
            ensureEvidenceLayout(client);
            List<ArtifactDigest> artifactDigests = List.of(
                    ArtifactDigest.capture("etherology"),
                    ArtifactDigest.capture(HARNESS_MOD_ID)
            );
            JsonObject report = createReport(client, artifactDigests);
            AtomicEvidenceWriter.writeReportThenMarker(evidenceLayout, report);
            LOGGER.info(
                    "Etherology metal-block-registry evidence is complete: {}",
                    evidenceLayout.reportsDirectory()
            );
        } catch (IOException exception) {
            LOGGER.error(
                    "Cannot atomically publish Etherology metal-block-registry evidence",
                    exception
            );
        } finally {
            stage = Stage.COMPLETE;
            client.scheduleStop();
        }
    }

    private void ensureEvidenceLayout(MinecraftClient client) throws IOException {
        if (evidenceLayout != null) return;

        EvidenceLayout layout = EvidenceLayout.resolve(client.runDirectory.toPath(), SCENARIO_ID);
        layout.requireFreshTargets(
                BEFORE_SCREENSHOT_FILE_NAME,
                AFTER_SCREENSHOT_FILE_NAME
        );
        evidenceLayout = layout;
    }

    private Path saveDirectory(MinecraftClient client) {
        return client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
    }

    private JsonObject createReport(
            MinecraftClient client,
            List<ArtifactDigest> artifactDigests
    ) {
        JsonArray assertions = new JsonArray();
        boolean passed = lifecycleFailure.isEmpty();
        int assertionIndex = 0;
        boolean etherologyLoaded = FabricLoader.getInstance().isModLoaded("etherology");

        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                etherologyLoaded,
                "loaded",
                etherologyLoaded ? "loaded" : "missing"
        );
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            boolean present = Registries.BLOCK.containsId(fixture.id());
            passed &= addAssertion(
                    assertions,
                    ASSERTION_NAMES.get(assertionIndex++),
                    present,
                    "present",
                    present ? "present" : "missing"
            );
        }
        DefaultStateIdSnapshot stateIds = defaultStateIdSnapshot;
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                stateIds != null && stateIds.allNonNegative(),
                "three non-negative raw IDs",
                stateIds == null ? "not inspected" : stateIds.description()
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                resourcesReady,
                READY_RESOURCES.toString(),
                resourcesReady ? READY_RESOURCES.toString() : "missing=" + missingResources
        );
        for (ArtifactDigest digest : artifactDigests) {
            passed &= addAssertion(
                    assertions,
                    ASSERTION_NAMES.get(assertionIndex++),
                    digest.passed(),
                    "one regular root JAR",
                    digest.passed() ? "one regular root JAR" : digest.failure()
            );
        }

        boolean integratedWorldReady = isWorldLifecycleReady(client);
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                integratedWorldReady,
                "running server and connected client",
                integratedWorldReady ? "joined" : "not joined"
        );
        ServerSetupResult setup = serverSetupResult;
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                setup != null && setup.chunkLoaded(),
                "full chunk",
                setup == null ? "missing setup" : Boolean.toString(setup.chunkLoaded())
        );

        String expectedBeforeFixture = beforeFixtureDescription(
                expectedAirIds(),
                expectedAirIds(),
                expectedPedestalIds()
        );
        String actualBeforeFixture = beforeFixtureDescription(
                setup == null ? null : setup.beforeDisplayIds(),
                beforeClientIds,
                beforeClientPedestalIds
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                beforeFixtureExact,
                expectedBeforeFixture,
                actualBeforeFixture
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                beforeCaptureRenderReady,
                "terrain complete and six fixture positions rendering-ready",
                beforeCaptureRenderReady ? "ready" : "not latched"
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                beforeCaptureCameraExact,
                expectedCameraPoseDescription(),
                beforeCaptureCameraPose
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                completedBeforeRenders == REQUIRED_COMPLETED_RENDERS,
                Integer.toString(REQUIRED_COMPLETED_RENDERS),
                Integer.toString(completedBeforeRenders)
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                hasExactDimensions(beforeFramebufferWidth, beforeFramebufferHeight),
                framebufferDescription(FRAMEBUFFER_WIDTH, FRAMEBUFFER_HEIGHT),
                framebufferDescription(beforeFramebufferWidth, beforeFramebufferHeight)
        );
        passed &= addScreenshotAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                beforeScreenshotResult
        );

        PlacementResult placement = placementResult;
        FixtureIdSnapshot serverAfterIds = placement == null
                ? null
                : placement.serverDisplayIds();
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                serverAfterIds != null && serverAfterIds.matchesMetalBlocks(),
                canonicalIds(expectedMetalBlockIds(), false),
                canonicalIds(serverAfterIds, false)
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                afterClientFixtureExact,
                canonicalIds(expectedMetalBlockIds(), false),
                canonicalIds(afterClientIds, false)
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                afterCaptureRenderReady,
                "terrain complete and six fixture positions rendering-ready",
                afterCaptureRenderReady ? "ready" : "not latched"
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                afterCaptureCameraExact,
                expectedCameraPoseDescription(),
                afterCaptureCameraPose
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                completedAfterRenders == REQUIRED_COMPLETED_RENDERS,
                Integer.toString(REQUIRED_COMPLETED_RENDERS),
                Integer.toString(completedAfterRenders)
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                hasExactDimensions(afterFramebufferWidth, afterFramebufferHeight),
                framebufferDescription(FRAMEBUFFER_WIDTH, FRAMEBUFFER_HEIGHT),
                framebufferDescription(afterFramebufferWidth, afterFramebufferHeight)
        );
        passed &= addScreenshotAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                afterScreenshotResult
        );
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex++),
                Boolean.TRUE.equals(saveResult),
                "true",
                saveResult == null ? "not attempted" : saveResult.toString()
        );

        boolean saveDirectoryPresent = Files.isDirectory(
                saveDirectory(client),
                LinkOption.NOFOLLOW_LINKS
        ) && !Files.isSymbolicLink(saveDirectory(client));
        passed &= addAssertion(
                assertions,
                ASSERTION_NAMES.get(assertionIndex),
                saveDirectoryPresent,
                WORLD_DIRECTORY_NAME,
                saveDirectoryPresent ? WORLD_DIRECTORY_NAME : "missing or linked"
        );

        JsonObject report = new JsonObject();
        report.addProperty("schema", 2);
        report.addProperty("scenario", SCENARIO_ID);
        report.addProperty("lane", "fabric-1.20.1");
        report.addProperty("role", "host");
        report.addProperty("status", passed ? "passed" : "failed");
        report.addProperty("client_ticks", clientTicks);
        report.addProperty("lifecycle_failure", lifecycleFailure);
        report.add("assertions", assertions);
        report.add("world", createWorldReport(client, setup));
        report.add("ready_resources", createReadyResourcesReport());
        report.add("artifacts", createArtifactsReport(artifactDigests));
        report.add("screenshots", createScreenshotsReport());
        report.add("metal_blocks", createMetalBlocksReport(setup, serverAfterIds));
        return report;
    }

    private JsonObject createWorldReport(
            MinecraftClient client,
            ServerSetupResult setup
    ) {
        JsonObject world = new JsonObject();
        world.addProperty("save_directory", WORLD_DIRECTORY_NAME);
        world.addProperty(
                "display_name",
                setup == null ? "" : setup.worldDisplayName()
        );
        world.addProperty("seed", setup == null ? Long.MIN_VALUE : setup.worldSeed());
        world.addProperty("dimension", setup == null ? "" : setup.dimensionId());
        world.addProperty("integrated", setup != null && client.getServer() != null);
        return world;
    }

    private JsonArray createReadyResourcesReport() {
        JsonArray resources = new JsonArray();
        for (Identifier resource : READY_RESOURCES) {
            resources.add(resource.toString());
        }
        return resources;
    }

    private JsonArray createArtifactsReport(List<ArtifactDigest> artifactDigests) {
        JsonArray artifacts = new JsonArray();
        for (ArtifactDigest digest : artifactDigests) {
            JsonObject artifact = new JsonObject();
            artifact.addProperty("mod_id", digest.modId());
            artifact.addProperty("origin_kind", digest.originKind());
            artifact.addProperty("file_name", digest.fileName());
            artifact.addProperty("size", digest.size());
            artifact.addProperty("sha256", digest.sha256());
            artifacts.add(artifact);
        }
        return artifacts;
    }

    private JsonArray createScreenshotsReport() {
        JsonArray screenshots = new JsonArray();
        addScreenshot(
                screenshots,
                "empty-display-fixture",
                BEFORE_SCREENSHOT_FILE_NAME,
                beforeFramebufferWidth,
                beforeFramebufferHeight,
                completedBeforeRenders,
                beforeScreenshotResult
        );
        addScreenshot(
                screenshots,
                "placed-metal-blocks",
                AFTER_SCREENSHOT_FILE_NAME,
                afterFramebufferWidth,
                afterFramebufferHeight,
                completedAfterRenders,
                afterScreenshotResult
        );
        return screenshots;
    }

    private JsonObject createMetalBlocksReport(
            ServerSetupResult setup,
            FixtureIdSnapshot serverAfterIds
    ) {
        JsonObject metalBlocks = new JsonObject();
        JsonArray registryIds = new JsonArray();
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            registryIds.add(fixture.id().toString());
        }
        metalBlocks.add("registry_ids", registryIds);
        metalBlocks.add("display_positions", createPositionsReport(false));
        metalBlocks.add("pedestal_positions", createPositionsReport(true));
        metalBlocks.add(
                "before_server_ids",
                createFixtureIdsReport(setup == null ? null : setup.beforeDisplayIds())
        );
        metalBlocks.add("before_client_ids", createFixtureIdsReport(beforeClientIds));
        metalBlocks.add("after_server_ids", createFixtureIdsReport(serverAfterIds));
        metalBlocks.add("after_client_ids", createFixtureIdsReport(afterClientIds));

        JsonObject camera = new JsonObject();
        camera.addProperty("x", CAMERA_X);
        camera.addProperty("y", CAMERA_Y);
        camera.addProperty("z", CAMERA_Z);
        camera.addProperty("yaw", CAMERA_YAW);
        camera.addProperty("pitch", CAMERA_PITCH);
        camera.addProperty("first_person", true);
        camera.addProperty("on_ground", true);
        metalBlocks.add("camera", camera);
        metalBlocks.addProperty("required_stable_renders", REQUIRED_COMPLETED_RENDERS);
        metalBlocks.add(
                "before",
                createCaptureReport(
                        beforeCaptureRenderReady,
                        beforeCaptureCameraExact,
                        completedBeforeRenders,
                        beforeFramebufferWidth,
                        beforeFramebufferHeight
                )
        );
        metalBlocks.add(
                "after",
                createCaptureReport(
                        afterCaptureRenderReady,
                        afterCaptureCameraExact,
                        completedAfterRenders,
                        afterFramebufferWidth,
                        afterFramebufferHeight
                )
        );
        return metalBlocks;
    }

    private JsonObject createPositionsReport(boolean pedestal) {
        JsonObject positions = new JsonObject();
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            BlockPos position = pedestal
                    ? fixture.pedestalPosition()
                    : fixture.displayPosition();
            positions.addProperty(fixture.id().toString(), positionDescription(position));
        }
        return positions;
    }

    private JsonObject createFixtureIdsReport(FixtureIdSnapshot snapshot) {
        JsonObject ids = new JsonObject();
        for (int index = 0; index < METAL_BLOCKS.size(); index++) {
            MetalBlockFixture fixture = METAL_BLOCKS.get(index);
            String id = snapshot == null ? "" : snapshot.ids().get(index).toString();
            ids.addProperty(fixture.id().toString(), id);
        }
        return ids;
    }

    private JsonObject createCaptureReport(
            boolean renderReady,
            boolean cameraExact,
            int stableRenders,
            int width,
            int height
    ) {
        JsonObject capture = new JsonObject();
        capture.addProperty("render_ready", renderReady);
        capture.addProperty("camera_exact", cameraExact);
        capture.addProperty("stable_renders", stableRenders);
        capture.addProperty("framebuffer", framebufferDescription(width, height));
        return capture;
    }

    private boolean addScreenshotAssertion(
            JsonArray assertions,
            String assertionName,
            ScreenshotResult result
    ) {
        return addAssertion(
                assertions,
                assertionName,
                result != null && result.passed(),
                "one non-empty unedited framebuffer PNG",
                result == null
                        ? "missing"
                        : result.size() + " bytes, sha256=" + result.sha256()
        );
    }

    private void addScreenshot(
            JsonArray screenshots,
            String step,
            String fileName,
            int width,
            int height,
            int completedRenders,
            ScreenshotResult result
    ) {
        if (result == null || !result.passed()) return;

        JsonObject screenshot = new JsonObject();
        screenshot.addProperty("step", step);
        screenshot.addProperty("role", "host");
        screenshot.addProperty("file", "screenshots/" + fileName);
        screenshot.addProperty("width", width);
        screenshot.addProperty("height", height);
        screenshot.addProperty("size", result.size());
        screenshot.addProperty("sha256", result.sha256());
        screenshot.addProperty("completed_render_count", completedRenders);
        screenshot.addProperty("source", "minecraft-framebuffer");
        screenshot.addProperty("edited", false);
        screenshots.add(screenshot);
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

    private static boolean hasExactDimensions(int width, int height) {
        return width == FRAMEBUFFER_WIDTH && height == FRAMEBUFFER_HEIGHT;
    }

    private static String framebufferDescription(int width, int height) {
        return width + "x" + height;
    }

    private static String beforeFixtureDescription(
            FixtureIdSnapshot serverIds,
            FixtureIdSnapshot clientIds,
            FixtureIdSnapshot pedestalIds
    ) {
        return "server=" + canonicalIds(serverIds, false)
                + "|client=" + canonicalIds(clientIds, false)
                + "|pedestals=" + canonicalIds(pedestalIds, true);
    }

    private static String canonicalIds(FixtureIdSnapshot snapshot, boolean pedestal) {
        if (snapshot == null) return "missing";

        StringBuilder description = new StringBuilder();
        for (int index = 0; index < METAL_BLOCKS.size(); index++) {
            if (index > 0) description.append(';');
            MetalBlockFixture fixture = METAL_BLOCKS.get(index);
            BlockPos position = pedestal
                    ? fixture.pedestalPosition()
                    : fixture.displayPosition();
            description.append(positionDescription(position));
            description.append('=');
            description.append(snapshot.ids().get(index));
        }
        return description.toString();
    }

    private static String positionDescription(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
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

    private static MetalBlockFixture fixture(String path, int x) {
        return new MetalBlockFixture(
                new Identifier("etherology", path),
                new BlockPos(x, ARENA_FLOOR_Y + 2, 1),
                new BlockPos(x, ARENA_FLOOR_Y + 1, 1)
        );
    }

    private static List<Identifier> createReadyResources() {
        List<Identifier> resources = new ArrayList<>();
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            String path = fixture.id().getPath();
            resources.add(new Identifier("etherology", "blockstates/" + path + ".json"));
            resources.add(new Identifier("etherology", "models/block/" + path + ".json"));
            resources.add(new Identifier("etherology", "textures/block/" + path + ".png"));
        }
        return List.copyOf(resources);
    }

    private static List<String> createAssertionNames() {
        List<String> names = new ArrayList<>();
        names.add("fabric_mod_loaded:etherology");
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            names.add("registry:block:" + fixture.id());
        }
        names.add("default_state_network_ids");
        names.add("client_render_resources");
        names.add("packaged_root_jar:etherology");
        names.add("packaged_root_jar:" + HARNESS_MOD_ID);
        names.add("integrated_world_joined");
        names.add("server_arena_chunk_loaded");
        names.add("before_fixture_exact");
        names.add("before_capture_render_ready");
        names.add("before_capture_camera_exact");
        names.add("before_consecutive_stable_renders");
        names.add("before_framebuffer_dimensions");
        names.add("native_screenshot_written:before");
        names.add("server_fixture_ids_exact");
        names.add("after_capture_client_fixture_ids_exact");
        names.add("after_capture_render_ready");
        names.add("after_capture_camera_exact");
        names.add("after_consecutive_stable_renders");
        names.add("after_framebuffer_dimensions");
        names.add("native_screenshot_written:after");
        names.add("forced_world_save");
        names.add("isolated_save_directory_present");
        return List.copyOf(names);
    }

    private static FixtureIdSnapshot expectedAirIds() {
        return new FixtureIdSnapshot(List.of(AIR_ID, AIR_ID, AIR_ID));
    }

    private static FixtureIdSnapshot expectedPedestalIds() {
        return new FixtureIdSnapshot(List.of(
                PEDESTAL_BLOCK_ID,
                PEDESTAL_BLOCK_ID,
                PEDESTAL_BLOCK_ID
        ));
    }

    private static FixtureIdSnapshot expectedMetalBlockIds() {
        List<Identifier> ids = new ArrayList<>();
        for (MetalBlockFixture fixture : METAL_BLOCKS) {
            ids.add(fixture.id());
        }
        return new FixtureIdSnapshot(List.copyOf(ids));
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_SERVER_SETUP,
        WAITING_FOR_BEFORE_CLIENT_MIRROR,
        WAITING_FOR_BEFORE_RENDERS,
        CAPTURING_BEFORE,
        WAITING_FOR_PLACEMENT,
        WAITING_FOR_AFTER_CLIENT_MIRROR,
        WAITING_FOR_AFTER_RENDERS,
        CAPTURING_AFTER,
        SAVING_WORLD,
        COMPLETE
    }

    private record MetalBlockFixture(
            Identifier id,
            BlockPos displayPosition,
            BlockPos pedestalPosition
    ) {
    }

    private record FixtureIdSnapshot(List<Identifier> ids) {

        private boolean matchesAir() {
            return ids.size() == METAL_BLOCKS.size()
                    && ids.stream().allMatch(AIR_ID::equals);
        }

        private boolean matchesPedestals() {
            return ids.size() == METAL_BLOCKS.size()
                    && ids.stream().allMatch(PEDESTAL_BLOCK_ID::equals);
        }

        private boolean matchesMetalBlocks() {
            if (ids.size() != METAL_BLOCKS.size()) return false;

            for (int index = 0; index < METAL_BLOCKS.size(); index++) {
                if (!METAL_BLOCKS.get(index).id().equals(ids.get(index))) return false;
            }
            return true;
        }
    }

    private record DefaultStateIdSnapshot(List<Integer> rawIds) {

        private boolean allNonNegative() {
            return rawIds.size() == METAL_BLOCKS.size()
                    && rawIds.stream().allMatch(rawId -> rawId >= 0);
        }

        private String description() {
            StringBuilder description = new StringBuilder();
            for (int index = 0; index < METAL_BLOCKS.size(); index++) {
                if (index > 0) description.append(';');
                description.append(METAL_BLOCKS.get(index).id());
                description.append('=');
                description.append(rawIds.get(index));
            }
            return description.toString();
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
            boolean chunkLoaded,
            FixtureIdSnapshot beforeDisplayIds,
            FixtureIdSnapshot pedestalIds,
            String worldDisplayName,
            long worldSeed,
            String dimensionId
    ) {
    }

    private record PlacementResult(FixtureIdSnapshot serverDisplayIds) {
    }
}
