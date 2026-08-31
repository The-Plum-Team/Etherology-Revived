package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.DataPackFailureScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.Window;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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

final class PhaseZeroScenario implements ClientScenario {

    static final String SCENARIO_ID = "phase0-smoke";
    static final String SCREENSHOT_FILE_NAME = "phase0-smoke.png";
    static final String WORLD_DIRECTORY_NAME = "etherology-original-phase0-smoke-world";

    private static final Logger LOGGER = LoggerFactory.getLogger("EtherologyOriginalBaselineHarness");
    static final String WORLD_DISPLAY_NAME = "Etherology Original 0.1.7 Phase 0";
    private static final String REFERENCE_ID = "published-0.1.7";
    private static final String HARNESS_MOD_ID = "etherology_original_baseline_harness";
    static final long WORLD_SEED = 0x4554484f303137L;
    private static final int REQUIRED_COMPLETED_RENDERS = 120;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final BlockPos CAMERA_BLOCK_POS = new BlockPos(0, ARENA_FLOOR_Y + 1, -8);
    private static final double CAMERA_X = CAMERA_BLOCK_POS.getX() + 0.5;
    private static final double CAMERA_Y = CAMERA_BLOCK_POS.getY();
    private static final double CAMERA_Z = CAMERA_BLOCK_POS.getZ() + 0.5;
    private static final float CAMERA_YAW = 0.0f;
    private static final float CAMERA_PITCH = 8.0f;
    private static final double CAMERA_POSE_TOLERANCE = 0.0001;
    private static final Identifier MINECRAFT_RESOURCE = Identifier.of(
            "minecraft",
            "texts/splashes.txt"
    );
    private static final Identifier ETHEROLOGY_RESOURCE = Identifier.of(
            "etherology",
            "models/item/oculus.json"
    );
    private static final List<PlacedBlockExpectation> PLACED_BLOCKS = List.of(
            placedBlock(
                    "brewing_cauldron",
                    "brewing_cauldron_block_entity",
                    -3,
                    2
            ),
            placedBlock(
                    "empowerment_table",
                    "empowerment_table_block_entity",
                    0,
                    2
            ),
            placedBlock(
                    "ethereal_storage",
                    "ethereal_storage_block_entity",
                    3,
                    2
            ),
            placedBlock(
                    "armillary_sphere",
                    "armillary_sphere_block_entity",
                    0,
                    5
            )
    );
    private static final List<Identifier> EXPECTED_BLOCK_ENTITY_TYPE_IDS = PLACED_BLOCKS.stream()
            .map(PlacedBlockExpectation::blockEntityTypeId)
            .toList();
    private static final RegistryExpectation[] REGISTRY_EXPECTATIONS = {
            block("brewing_cauldron"),
            block("empowerment_table"),
            block("ethereal_storage"),
            block("armillary_sphere"),
            blockEntityType("brewing_cauldron_block_entity"),
            blockEntityType("empowerment_table_block_entity"),
            blockEntityType("ethereal_storage_block_entity"),
            blockEntityType("armillary_sphere_block_entity")
    };

    private final StableRenderCounter stableWorldRenders =
            new StableRenderCounter(REQUIRED_COMPLETED_RENDERS);
    private Stage stage = Stage.WAITING_FOR_TITLE;
    private int clientTicks;
    private int stageClientTicks;
    private int framebufferWidth;
    private int framebufferHeight;
    private int requestedWindowWidth = -1;
    private int requestedWindowHeight = -1;
    private boolean registryPreflightPassed;
    private boolean resourcesReady;
    private boolean setupSubmitted;
    private boolean clientMirrorReady;
    private boolean captureRenderReady;
    private boolean captureCameraExact;
    private boolean saveSubmitted;
    private String lifecycleFailure = "";
    private EvidenceLayout evidenceLayout;
    private BlockStateRegistrySnapshot blockStateRegistrySnapshot;
    private volatile ScreenshotResult screenshotResult;
    private volatile ServerSetupResult serverSetupResult;
    private volatile String serverSetupFailure = "";
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
                case WAITING_FOR_CLIENT_MIRROR -> tickWaitingForClientMirror(client);
                case WAITING_FOR_WORLD_RENDERS -> tickWaitingForWorldRenders(client);
                case CAPTURING_WORLD -> tickCapturingWorld(client);
                case SAVING_WORLD -> tickSavingWorld(client);
                case COMPLETE -> {
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Original phase0-smoke failed in {}", stage, exception);
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
    public void onGameRenderCompleted() {
        if (stage != Stage.WAITING_FOR_WORLD_RENDERS) return;

        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (!stableWorldRenders.observe(isCaptureStateExact(client))) return;
            captureWorld(client);
        } catch (RuntimeException exception) {
            stableWorldRenders.observe(false);
            LOGGER.error("Original phase0-smoke render callback failed", exception);
            fail(
                    client,
                    "Render callback raised " + exception.getClass().getSimpleName()
            );
        }
    }

    private void tickWaitingForTitle(MinecraftClient client) {
        if (client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) return;

        try {
            ensureEvidenceLayout(client);
        } catch (IOException exception) {
            LOGGER.error("Cannot use the repository-owned original evidence layout", exception);
            lifecycleFailure = exception.getMessage();
            stage = Stage.COMPLETE;
            client.scheduleStop();
            return;
        }
        if (!requestExpectedFramebuffer(client)) return;

        resourcesReady = client.getResourceManager().getResource(MINECRAFT_RESOURCE).isPresent()
                && client.getResourceManager().getResource(ETHEROLOGY_RESOURCE).isPresent();
        if (!resourcesReady) {
            fail(client, "The loaded resources do not include the published Etherology artifact");
            return;
        }

        for (RegistryExpectation expectation : REGISTRY_EXPECTATIONS) {
            if (!expectation.isPresent()) {
                fail(
                        client,
                        "The registry is missing " + expectation.registryName()
                                + " " + expectation.identifier()
                );
                return;
            }
        }
        blockStateRegistrySnapshot = inspectEtherologyBlockStates();
        if (!blockStateRegistrySnapshot.missingStates().isEmpty()) {
            fail(
                    client,
                    "Etherology block states are missing network ids: "
                            + blockStateRegistrySnapshot.missingStates()
            );
            return;
        }
        registryPreflightPassed = true;
        transition(Stage.STARTING_WORLD);
    }

    private void startWorld(MinecraftClient client) {
        Path saveDirectory = client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
        if (Files.exists(saveDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(saveDirectory)) {
            fail(client, "Refusing to reuse the original-baseline save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "The client already has a world before deterministic world creation");
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
                GameMode.CREATIVE,
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

        IntegratedServer server = client.getServer();
        if (server == null
                || !server.isRunning()
                || server.isStopping()
                || client.world == null
                || client.player == null
                || client.player.networkHandler == null
                || client.interactionManager == null) {
            return;
        }
        if (server.getPlayerManager().getPlayer(client.player.getUuid()) == null) return;
        if (setupSubmitted) return;

        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(client.player);
        setupSubmitted = true;
        UUID playerId = client.player.getUuid();
        server.execute(() -> setupServerWorld(server, playerId));
        transition(Stage.WAITING_FOR_SERVER_SETUP);
    }

    private void tickWaitingForServerSetup(MinecraftClient client) {
        if (!serverSetupFailure.isEmpty()) {
            fail(client, "Server fixture setup failed: " + serverSetupFailure);
            return;
        }
        if (serverSetupResult == null) return;
        transition(Stage.WAITING_FOR_CLIENT_MIRROR);
    }

    private void tickWaitingForClientMirror(MinecraftClient client) {
        clientMirrorReady = hasClientMirror(client);
        if (!clientMirrorReady) return;
        transition(Stage.WAITING_FOR_WORLD_RENDERS);
    }

    private void tickWaitingForWorldRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before capture");
        }
    }

    private void tickCapturingWorld(MinecraftClient client) {
        ScreenshotResult result = screenshotResult;
        if (result == null) return;
        if (!result.passed()) {
            fail(client, "The native framebuffer screenshot failed: " + result.failure());
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

    private void captureWorld(MinecraftClient client) {
        if (stage != Stage.WAITING_FOR_WORLD_RENDERS) return;
        if (!isCaptureStateExact(client)) {
            stableWorldRenders.observe(false);
            return;
        }

        EvidenceLayout layout = evidenceLayout;
        if (layout == null) {
            fail(client, "The evidence layout was not initialized");
            return;
        }
        captureRenderReady = true;
        captureCameraExact = true;
        framebufferWidth = client.getFramebuffer().textureWidth;
        framebufferHeight = client.getFramebuffer().textureHeight;
        transition(Stage.CAPTURING_WORLD);
        ScreenshotRecorder.saveScreenshot(
                layout.scenarioRoot().toFile(),
                SCREENSHOT_FILE_NAME,
                client.getFramebuffer(),
                message -> screenshotResult = inspectScreenshot(layout.screenshotPath())
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
            LOGGER.error("Cannot inspect the original phase0-smoke screenshot", exception);
            return ScreenshotResult.failed(
                    "Screenshot inspection raised " + exception.getClass().getSimpleName()
            );
        }
    }

    private void setupServerWorld(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                throw new IllegalStateException("The integrated server has no matching player");
            }

            boolean chunkLoaded = world.getChunkManager()
                    .getChunk(0, 0, ChunkStatus.FULL, true) != null;
            world.setTimeOfDay(6000L);
            world.setWeather(6000, 0, false, false);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);

            clearArena(world);
            buildArenaFloor(world);

            List<String> placedBlockIds = new ArrayList<>();
            List<String> placedBlockEntityTypeIds = new ArrayList<>();
            boolean allBlocksPlaced = true;
            boolean allBlockEntitiesPresent = true;
            boolean allBlockEntityTypesExact = true;
            for (PlacedBlockExpectation expectation : PLACED_BLOCKS) {
                Block block = Registries.BLOCK.get(expectation.id());
                world.setBlockState(expectation.pos(), block.getDefaultState(), 3);
                Identifier actualId = Registries.BLOCK.getId(
                        world.getBlockState(expectation.pos()).getBlock()
                );
                placedBlockIds.add(expectation.pos().toShortString() + "=" + actualId);
                allBlocksPlaced &= expectation.id().equals(actualId);
                BlockEntity blockEntity = world.getBlockEntity(expectation.pos());
                allBlockEntitiesPresent &= blockEntity != null;
                Identifier actualBlockEntityTypeId = blockEntity == null
                        ? null
                        : Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
                BlockEntityType<?> expectedBlockEntityType =
                        Registries.BLOCK_ENTITY_TYPE.get(expectation.blockEntityTypeId());
                placedBlockEntityTypeIds.add(
                        expectation.pos().toShortString() + "=" + actualBlockEntityTypeId
                );
                allBlockEntityTypesExact &= blockEntity != null
                        && blockEntity.getType() == expectedBlockEntityType
                        && expectation.blockEntityTypeId().equals(actualBlockEntityTypeId);
            }

            boolean playerCreative = player.changeGameMode(GameMode.CREATIVE)
                    || player.isCreative();
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
                    playerCreative,
                    allBlocksPlaced,
                    allBlockEntitiesPresent,
                    allBlockEntityTypesExact,
                    List.copyOf(placedBlockIds),
                    List.copyOf(placedBlockEntityTypeIds),
                    server.getSaveProperties().getLevelName(),
                    world.getSeed(),
                    world.getRegistryKey().getValue().toString()
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot arrange the original phase0-smoke fixture", exception);
            serverSetupFailure = exception.getClass().getSimpleName()
                    + ": " + exception.getMessage();
        }
    }

    private void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-9, ARENA_FLOOR_Y, -10);
        BlockPos end = new BlockPos(9, ARENA_FLOOR_Y + 10, 9);
        for (BlockPos pos : BlockPos.iterate(start, end)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }
    }

    private void buildArenaFloor(ServerWorld world) {
        for (int x = -9; x <= 9; x++) {
            for (int z = -10; z <= 9; z++) {
                Block floorBlock = (x + z) % 7 == 0
                        ? Blocks.SEA_LANTERN
                        : Blocks.SMOOTH_STONE;
                world.setBlockState(
                        new BlockPos(x, ARENA_FLOOR_Y, z),
                        floorBlock.getDefaultState(),
                        3
                );
            }
        }
    }

    private boolean hasClientMirror(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) return false;
        if (!client.world.getChunkManager().isChunkLoaded(0, 0)) return false;
        if (!client.player.isCreative()) return false;
        if (client.world.getRegistryKey() != World.OVERWORLD) return false;

        for (PlacedBlockExpectation expectation : PLACED_BLOCKS) {
            Identifier actualId = Registries.BLOCK.getId(
                    client.world.getBlockState(expectation.pos()).getBlock()
            );
            if (!expectation.id().equals(actualId)) return false;
        }
        return hasExactClientBlockEntityTypes(client);
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
        EvidenceLayout layout = evidenceLayout;
        Window window = client.getWindow();
        return layout != null
                && isWorldLifecycleReady(client)
                && clientMirrorReady
                && client.getOverlay() == null
                && client.currentScreen == null
                && client.world.getChunkManager().isChunkLoaded(0, 0)
                && client.getFramebuffer().textureWidth == layout.framebufferWidth()
                && client.getFramebuffer().textureHeight == layout.framebufferHeight()
                && window.getFramebufferWidth() == layout.framebufferWidth()
                && window.getFramebufferHeight() == layout.framebufferHeight();
    }

    private boolean isCaptureStateExact(MinecraftClient client) {
        return isWorldViewReady(client)
                && isFixtureRenderReady(client)
                && hasExpectedCameraPose(client);
    }

    private boolean isFixtureRenderReady(MinecraftClient client) {
        if (!client.worldRenderer.isTerrainRenderComplete()) return false;

        for (PlacedBlockExpectation expectation : PLACED_BLOCKS) {
            if (!client.worldRenderer.isRenderingReady(expectation.pos())) return false;
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

    private boolean hasExactClientBlockEntityTypes(MinecraftClient client) {
        if (client.world == null) return false;

        for (PlacedBlockExpectation expectation : PLACED_BLOCKS) {
            BlockEntity blockEntity = client.world.getBlockEntity(expectation.pos());
            if (blockEntity == null) return false;

            BlockEntityType<?> expectedType =
                    Registries.BLOCK_ENTITY_TYPE.get(expectation.blockEntityTypeId());
            Identifier actualTypeId = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
            if (blockEntity.getType() != expectedType
                    || !expectation.blockEntityTypeId().equals(actualTypeId)) {
                return false;
            }
        }
        return true;
    }

    private List<String> clientBlockEntityTypeIds(MinecraftClient client) {
        List<String> typeIds = new ArrayList<>();
        if (client.world == null) return typeIds;

        for (PlacedBlockExpectation expectation : PLACED_BLOCKS) {
            BlockEntity blockEntity = client.world.getBlockEntity(expectation.pos());
            Identifier typeId = blockEntity == null
                    ? null
                    : Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
            typeIds.add(expectation.pos().toShortString() + "=" + typeId);
        }
        return List.copyOf(typeIds);
    }

    private boolean requestExpectedFramebuffer(MinecraftClient client) {
        EvidenceLayout layout = evidenceLayout;
        if (layout == null) return false;

        Window window = client.getWindow();
        if (client.getFramebuffer().textureWidth == layout.framebufferWidth()
                && client.getFramebuffer().textureHeight == layout.framebufferHeight()
                && window.getFramebufferWidth() == layout.framebufferWidth()
                && window.getFramebufferHeight() == layout.framebufferHeight()) {
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
                layout.framebufferWidth(),
                currentWindowWidth,
                currentFramebufferWidth
        );
        int targetWindowHeight = FramebufferWindowSizing.requestedWindowDimension(
                layout.framebufferHeight(),
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

    private BlockStateRegistrySnapshot inspectEtherologyBlockStates() {
        int registeredStates = 0;
        List<String> missingStates = new ArrayList<>();
        for (Identifier blockId : Registries.BLOCK.getIds()) {
            if (!"etherology".equals(blockId.getNamespace())) continue;

            Block block = Registries.BLOCK.get(blockId);
            for (BlockState state : block.getStateManager().getStates()) {
                registeredStates++;
                if (Block.STATE_IDS.getRawId(state) < 0) {
                    missingStates.add(blockId + "=" + state);
                }
            }
        }
        return new BlockStateRegistrySnapshot(registeredStates, List.copyOf(missingStates));
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
                LOGGER.error("Cannot force-save the original phase0-smoke world", exception);
                saveFailure = exception.getClass().getSimpleName()
                        + ": " + exception.getMessage();
            }
        });
        transition(Stage.SAVING_WORLD);
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;

        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Original phase0-smoke lifecycle failure: {}", lifecycleFailure);
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
            ReportResult reportResult = createReport(client, artifactDigests);
            AtomicEvidenceWriter.writeReportThenMarker(
                    evidenceLayout,
                    reportResult.report(),
                    reportResult.passed()
            );
            LOGGER.info(
                    "Original phase0-smoke evidence published with status {}: {}",
                    reportResult.passed() ? "passed" : "failed",
                    evidenceLayout.reportsDirectory()
            );
        } catch (IOException exception) {
            LOGGER.error("Cannot atomically publish original phase0-smoke evidence", exception);
        } finally {
            stage = Stage.COMPLETE;
            client.scheduleStop();
        }
    }

    private void ensureEvidenceLayout(MinecraftClient client) throws IOException {
        if (evidenceLayout != null) return;

        EvidenceLayout layout = EvidenceLayout.resolve(
                client.runDirectory.toPath(),
                SCENARIO_ID
        );
        layout.requireFreshTargets();
        evidenceLayout = layout;
    }

    private ReportResult createReport(
            MinecraftClient client,
            List<ArtifactDigest> artifactDigests
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
        passed &= addAssertion(
                assertions,
                "published_resources_loaded",
                resourcesReady,
                List.of(MINECRAFT_RESOURCE, ETHEROLOGY_RESOURCE).toString(),
                resourcesReady ? "present" : "missing"
        );
        passed &= addAssertion(
                assertions,
                "registry_preflight",
                registryPreflightPassed,
                "all phase0 registry entries present",
                registryPreflightPassed ? "present" : "not completed"
        );
        for (RegistryExpectation expectation : REGISTRY_EXPECTATIONS) {
            boolean present = expectation.isPresent();
            passed &= addAssertion(
                    assertions,
                    "registry:" + expectation.registryName() + ":" + expectation.identifier(),
                    present,
                    "present",
                    present ? "present" : "missing"
            );
        }
        BlockStateRegistrySnapshot blockStates = blockStateRegistrySnapshot;
        passed &= addAssertion(
                assertions,
                "etherology_block_states_have_network_ids",
                blockStates != null && blockStates.missingStates().isEmpty(),
                "every Etherology block state has a non-negative raw id",
                blockStates == null
                        ? "not inspected"
                        : blockStates.registeredStates() + " inspected; missing="
                                + blockStates.missingStates()
        );
        for (ArtifactDigest digest : artifactDigests) {
            passed &= addAssertion(
                    assertions,
                    "packaged_root_jar:" + digest.modId(),
                    digest.passed(),
                    "one regular root JAR",
                    digest.passed() ? "one regular root JAR" : digest.failure()
            );
        }

        EvidenceLayout layout = evidenceLayout;
        boolean dimensionsMatch = layout != null
                && framebufferWidth == layout.framebufferWidth()
                && framebufferHeight == layout.framebufferHeight();
        passed &= addAssertion(
                assertions,
                "native_framebuffer_dimensions",
                dimensionsMatch,
                layout == null
                        ? "evidence marker unavailable"
                        : layout.framebufferWidth() + "x" + layout.framebufferHeight(),
                framebufferWidth + "x" + framebufferHeight
        );
        passed &= addAssertion(
                assertions,
                "completed_world_renders_before_capture",
                stableWorldRenders.completedRenders() >= REQUIRED_COMPLETED_RENDERS,
                Integer.toString(REQUIRED_COMPLETED_RENDERS),
                Integer.toString(stableWorldRenders.completedRenders())
        );
        passed &= addAssertion(
                assertions,
                "capture_render_ready",
                captureRenderReady,
                "terrain complete and all four fixture positions rendering-ready",
                captureRenderReady ? "ready" : "not latched"
        );
        passed &= addAssertion(
                assertions,
                "capture_camera_exact",
                captureCameraExact,
                expectedCameraPoseDescription(),
                cameraPoseDescription(client)
        );
        ScreenshotResult screenshot = screenshotResult;
        passed &= addAssertion(
                assertions,
                "native_screenshot_written",
                screenshot != null && screenshot.passed(),
                "one non-empty unedited framebuffer PNG",
                screenshot == null
                        ? "missing"
                        : screenshot.size() + " bytes, sha256=" + screenshot.sha256()
        );
        passed &= addAssertion(
                assertions,
                "integrated_world_joined",
                isWorldLifecycleReady(client),
                "running server and connected client",
                isWorldLifecycleReady(client) ? "joined" : "not joined"
        );
        passed &= addAssertion(
                assertions,
                "client_world_mirrors_server_fixture",
                clientMirrorReady,
                "all four blocks and exact block entity types mirrored",
                clientMirrorReady ? "mirrored" : "not mirrored"
        );
        boolean clientBlockEntityTypesExact = hasExactClientBlockEntityTypes(client);
        passed &= addAssertion(
                assertions,
                "client_fixture_block_entity_types_exact",
                clientBlockEntityTypesExact,
                EXPECTED_BLOCK_ENTITY_TYPE_IDS.toString(),
                clientBlockEntityTypeIds(client).toString()
        );

        ServerSetupResult setup = serverSetupResult;
        passed &= addAssertion(
                assertions,
                "server_arena_chunk_loaded",
                setup != null && setup.chunkLoaded(),
                "full chunk",
                setup == null ? "missing setup" : Boolean.toString(setup.chunkLoaded())
        );
        passed &= addAssertion(
                assertions,
                "server_player_creative",
                setup != null && setup.playerCreative(),
                "creative",
                setup == null ? "missing setup" : Boolean.toString(setup.playerCreative())
        );
        passed &= addAssertion(
                assertions,
                "server_fixture_blocks_placed",
                setup != null && setup.allBlocksPlaced(),
                "all expected identifiers",
                setup == null ? "missing setup" : setup.placedBlockIds().toString()
        );
        passed &= addAssertion(
                assertions,
                "server_fixture_block_entities_present",
                setup != null && setup.allBlockEntitiesPresent(),
                "four block entities",
                setup == null
                        ? "missing setup"
                        : Boolean.toString(setup.allBlockEntitiesPresent())
        );
        passed &= addAssertion(
                assertions,
                "server_fixture_block_entity_types_exact",
                setup != null && setup.allBlockEntityTypesExact(),
                EXPECTED_BLOCK_ENTITY_TYPE_IDS.toString(),
                setup == null
                        ? "missing setup"
                        : setup.placedBlockEntityTypeIds().toString()
        );
        String liveWorldIdentity = setup == null
                ? "missing setup"
                : setup.worldDisplayName() + ";" + setup.worldSeed() + ";" + setup.dimensionId();
        String expectedWorldIdentity = WORLD_DISPLAY_NAME + ";" + WORLD_SEED + ";"
                + World.OVERWORLD.getValue();
        passed &= addAssertion(
                assertions,
                "live_world_identity",
                expectedWorldIdentity.equals(liveWorldIdentity),
                expectedWorldIdentity,
                liveWorldIdentity
        );
        passed &= addAssertion(
                assertions,
                "forced_world_save",
                Boolean.TRUE.equals(saveResult),
                "true",
                saveResult == null ? "not attempted" : saveResult.toString()
        );

        Path saveDirectory = client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
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

        JsonObject report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("reference_id", REFERENCE_ID);
        report.addProperty("scenario", SCENARIO_ID);
        report.addProperty("lane", "fabric-1.21.1-original");
        report.addProperty("status", passed ? "passed" : "failed");
        report.addProperty("client_ticks", clientTicks);
        report.addProperty("lifecycle_failure", lifecycleFailure);
        report.add("assertions", assertions);

        JsonObject world = new JsonObject();
        world.addProperty("save_directory", WORLD_DIRECTORY_NAME);
        world.addProperty("display_name", setup == null ? "" : setup.worldDisplayName());
        world.addProperty("seed", setup == null ? Long.MIN_VALUE : setup.worldSeed());
        world.addProperty("dimension", setup == null ? "" : setup.dimensionId());
        world.addProperty("integrated", setup != null && client.getServer() != null);
        report.add("world", world);

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
        report.add("artifacts", artifacts);

        JsonArray screenshots = new JsonArray();
        if (screenshot != null && screenshot.passed()) {
            JsonObject screenshotNode = new JsonObject();
            screenshotNode.addProperty("step", "integrated-world-fixture");
            screenshotNode.addProperty("file", "screenshots/" + SCREENSHOT_FILE_NAME);
            screenshotNode.addProperty("width", framebufferWidth);
            screenshotNode.addProperty("height", framebufferHeight);
            screenshotNode.addProperty("size", screenshot.size());
            screenshotNode.addProperty("sha256", screenshot.sha256());
            screenshotNode.addProperty(
                    "completed_render_count",
                    stableWorldRenders.completedRenders()
            );
            screenshotNode.addProperty("source", "minecraft-framebuffer");
            screenshotNode.addProperty("edited", false);
            screenshots.add(screenshotNode);
        }
        report.add("screenshots", screenshots);
        return new ReportResult(report, passed);
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

    private static RegistryExpectation block(String path) {
        return new RegistryExpectation("block", Registries.BLOCK, etherologyId(path));
    }

    private static RegistryExpectation blockEntityType(String path) {
        return new RegistryExpectation(
                "block_entity_type",
                Registries.BLOCK_ENTITY_TYPE,
                etherologyId(path)
        );
    }

    private static PlacedBlockExpectation placedBlock(
            String path,
            String blockEntityTypePath,
            int x,
            int z
    ) {
        return new PlacedBlockExpectation(
                etherologyId(path),
                etherologyId(blockEntityTypePath),
                new BlockPos(x, ARENA_FLOOR_Y + 1, z)
        );
    }

    private static Identifier etherologyId(String path) {
        return Identifier.of("etherology", path);
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_SERVER_SETUP,
        WAITING_FOR_CLIENT_MIRROR,
        WAITING_FOR_WORLD_RENDERS,
        CAPTURING_WORLD,
        SAVING_WORLD,
        COMPLETE
    }

    private record PlacedBlockExpectation(
            Identifier id,
            Identifier blockEntityTypeId,
            BlockPos pos
    ) {
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
            boolean playerCreative,
            boolean allBlocksPlaced,
            boolean allBlockEntitiesPresent,
            boolean allBlockEntityTypesExact,
            List<String> placedBlockIds,
            List<String> placedBlockEntityTypeIds,
            String worldDisplayName,
            long worldSeed,
            String dimensionId
    ) {
    }

    private record BlockStateRegistrySnapshot(int registeredStates, List<String> missingStates) {
    }

    private record ReportResult(JsonObject report, boolean passed) {
    }
}
