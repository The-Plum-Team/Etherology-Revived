package dev.theplumteam.etherology.e2e.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DatapackFailureScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class EtherealChannelScenario {

    private static final Logger LOGGER = LoggerFactory.getLogger("EtherologyForgeE2E");
    private static final String SCENARIO_ID = ScenarioSelection.ETHEREAL_CHANNEL;
    private static final String WORLD_DIRECTORY_NAME =
            "etherology-e2e-forge-channel-world";
    private static final String WORLD_DISPLAY_NAME = "Etherology Forge E2E Channel";
    private static final String GATED_SCREENSHOT = "ethereal-channel-gated.png";
    private static final String TRANSFERRED_SCREENSHOT =
            "ethereal-channel-transferred.png";
    private static final String REOPENED_SCREENSHOT = "ethereal-channel-reopened.png";
    private static final String STORAGE_ETHER_KEY = "storage_ether";
    private static final String STORED_ETHER_KEY = "stored_ether";
    private static final String EVAPORATING_KEY = "evaporating";
    private static final String CROSS_EVAPORATING_KEY = "cross_evaporating";
    private static final String ACTIVATED_PROPERTY = "activated";
    private static final String FACING_PROPERTY = "facing";
    private static final String IS_CROSS_PROPERTY = "is_cross";
    private static final String NORTH_CONNECTION_PROPERTY = "north";
    private static final String EAST_CONNECTION_PROPERTY = "east";
    private static final String WEST_CONNECTION_PROPERTY = "west";
    private static final String UP_CONNECTION_PROPERTY = "up";
    private static final String EXPECTED_LEVER_SUPPORT_EVIDENCE =
            "lever=minecraft:lever;survived=true;can_place=true;face=wall;"
                    + "facing=north;channel=etherology:ethereal_channel;"
                    + "channel_block_entity=etherology:ethereal_channel_block_entity;"
                    + "channel_facing=east;north=in;east=out;west=empty;cross=true";
    private static final long WORLD_SEED = 0x45544843484E4CL;
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int ARENA_FLOOR_Y = 120;
    private static final int REQUIRED_GATED_RENDERS = 120;
    private static final int REQUIRED_TRANSFERRED_RENDERS = 120;
    private static final int REQUIRED_REOPENED_RENDERS = 120;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final float INITIAL_NETWORK_ETHER = 1.0f;
    private static final float INITIAL_EVAPORATION_ETHER = 1.0f;
    private static final float EXPECTED_EVAPORATED_ETHER = 0.2f;
    private static final float EXPECTED_REMAINING_EVAPORATION_ETHER = 0.8f;
    private static final float FLOAT_TOLERANCE = 0.0001f;
    private static final double CAMERA_POSE_TOLERANCE = 0.0001;
    private static final BlockPos CHANNEL_POS = new BlockPos(1, ARENA_FLOOR_Y + 1, 2);
    private static final BlockPos SOURCE_STORAGE_POS = CHANNEL_POS.up();
    private static final BlockPos TARGET_STORAGE_POS = CHANNEL_POS.east();
    private static final BlockPos CHANNEL_POWER_POS = CHANNEL_POS.west();
    private static final BlockPos CHANNEL_FEEDER_POS = CHANNEL_POWER_POS.west();
    private static final BlockPos EVAPORATION_CHANNEL_POS =
            new BlockPos(5, ARENA_FLOOR_Y + 1, 2);
    private static final BlockPos EVAPORATION_POWER_POS = EVAPORATION_CHANNEL_POS.west();
    private static final BlockPos EVAPORATION_FEEDER_POS =
            EVAPORATION_POWER_POS.west();
    private static final BlockPos LEVER_SUPPORT_CHANNEL_POS =
            new BlockPos(8, ARENA_FLOOR_Y + 1, 2);
    private static final BlockPos LEVER_POS = LEVER_SUPPORT_CHANNEL_POS.north();
    private static final List<BlockPos> FIXTURE_POSITIONS = List.of(
            SOURCE_STORAGE_POS,
            CHANNEL_POS,
            TARGET_STORAGE_POS,
            CHANNEL_POWER_POS,
            CHANNEL_FEEDER_POS,
            EVAPORATION_CHANNEL_POS,
            EVAPORATION_POWER_POS,
            EVAPORATION_FEEDER_POS,
            LEVER_SUPPORT_CHANNEL_POS,
            LEVER_POS
    );
    private static final BlockPos CAMERA_POS = new BlockPos(2, ARENA_FLOOR_Y + 2, -6);
    private static final Identifier CHANNEL_BLOCK_ID = etherologyId("ethereal_channel");
    private static final Identifier CHANNEL_BLOCK_ENTITY_ID =
            etherologyId("ethereal_channel_block_entity");
    private static final Identifier STORAGE_BLOCK_ID = etherologyId("ethereal_storage");
    private static final Identifier STORAGE_BLOCK_ENTITY_ID =
            etherologyId("ethereal_storage_block_entity");

    private volatile Stage stage = Stage.WAITING_FOR_TITLE;
    private int clientTicks;
    private int stageClientTicks;
    private int completedRenders;
    private String lifecycleFailure = "";
    private ForgeEvidenceLayout evidenceLayout;
    private boolean setupSubmitted;
    private volatile boolean mainPowerReleaseSubmitted;
    private volatile boolean evaporationPowerReleaseSubmitted;
    private boolean saveSubmitted;
    private boolean restartSubmitted;
    private boolean restartInspectionSubmitted;
    private volatile String serverFailure = "";
    private long gatedFirstObservedTime = -1L;
    private volatile PlacementResult placementResult;
    private volatile SetupResult setupResult;
    private volatile GatedResult gatedResult;
    private volatile PowerMutation mainPowerMutation;
    private volatile PowerReleaseResult mainPowerReleaseResult;
    private volatile TransferResult transferResult;
    private volatile StableTransferResult stableTransferResult;
    private volatile PowerMutation evaporationPowerMutation;
    private volatile PowerReleaseResult evaporationPowerReleaseResult;
    private volatile EvaporationObservation evaporationObservation;
    private volatile EvaporationResult evaporationResult;
    private long evaporationActivationObservedTime = -1L;
    private volatile Boolean saveResult;
    private volatile ChannelSnapshot savedSnapshot;
    private volatile RestartResult restartResult;
    private boolean transferredClientMirror;
    private boolean reopenedClientMirror;
    private boolean gatedCaptureMirror;
    private boolean transferredCaptureMirror;
    private boolean reopenedCaptureMirror;
    private boolean gatedCaptureRenderReady;
    private boolean transferredCaptureRenderReady;
    private boolean reopenedCaptureRenderReady;
    private boolean gatedCaptureCameraExact;
    private boolean transferredCaptureCameraExact;
    private boolean reopenedCaptureCameraExact;
    private volatile ScreenshotResult gatedScreenshot;
    private volatile ScreenshotResult transferredScreenshot;
    private volatile ScreenshotResult reopenedScreenshot;

    /**
     * Advances the client-owned world, capture, restart, and publication lifecycle.
     *
     * @param event Forge client tick ending after Minecraft client work
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.COMPLETE) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        clientTicks++;
        stageClientTicks++;
        try {
            tick(client);
        } catch (RuntimeException exception) {
            LOGGER.error("Forge ethereal-channel E2E failed in {}", stage, exception);
            fail(
                    client,
                    stage + " raised " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()
            );
            return;
        }

        if (stage != Stage.COMPLETE && stageClientTicks >= MAXIMUM_STAGE_CLIENT_TICKS) {
            fail(client, "Timed out in " + stage + " after " + stageClientTicks + " ticks");
        }
    }

    /**
     * Observes server state after block-entity ticks so cadence assertions use exact world time.
     *
     * @param event Forge integrated-server tick boundary
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.getServer() instanceof IntegratedServer server)
                || stage == Stage.COMPLETE) {
            return;
        }

        try {
            ServerWorld world = server.getOverworld();
            switch (stage) {
                case WAITING_FOR_SETUP -> inspectScheduledSetup(world);
                case WAITING_FOR_GATED_TRANSFER -> inspectGatedTransfer(world);
                case RELEASING_MAIN_POWER -> inspectNaturalPowerRelease(world, true);
                case WAITING_FOR_TRANSFER -> inspectReleasedTransfer(world);
                case WAITING_FOR_NO_REVERSE -> inspectNoReverseMovement(world);
                case RELEASING_EVAPORATION_POWER ->
                        inspectNaturalPowerRelease(world, false);
                case WAITING_FOR_EVAPORATION -> inspectEvaporation(world);
                default -> {
                }
            }
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    /**
     * Captures only after the deterministic camera has rendered the required stable frames.
     *
     * @param event completed composed framebuffer render
     */
    @SubscribeEvent
    public void onWorldRendered(RenderGuiEvent.Post event) {
        MinecraftClient client = MinecraftClient.getInstance();
        switch (stage) {
            case WAITING_FOR_GATED_RENDERS -> captureAfterStableRenders(
                    client,
                    GATED_SCREENSHOT,
                    REQUIRED_GATED_RENDERS,
                    result -> gatedScreenshot = result,
                    Stage.CAPTURING_GATED
            );
            case WAITING_FOR_TRANSFERRED_RENDERS -> captureAfterStableRenders(
                    client,
                    TRANSFERRED_SCREENSHOT,
                    REQUIRED_TRANSFERRED_RENDERS,
                    result -> transferredScreenshot = result,
                    Stage.CAPTURING_TRANSFERRED
            );
            case WAITING_FOR_REOPENED_RENDERS -> captureAfterStableRenders(
                    client,
                    REOPENED_SCREENSHOT,
                    REQUIRED_REOPENED_RENDERS,
                    result -> reopenedScreenshot = result,
                    Stage.CAPTURING_REOPENED
            );
            default -> {
            }
        }
    }

    private void tick(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "Integrated-server operation failed: " + serverFailure);
            return;
        }

        switch (stage) {
            case WAITING_FOR_TITLE -> tickWaitingForTitle(client);
            case STARTING_WORLD -> startFreshWorld(client);
            case WAITING_FOR_WORLD -> tickWaitingForWorld(client);
            case WAITING_FOR_SETUP -> tickWaitingForSetup(client);
            case WAITING_FOR_GATED_TRANSFER,
                    WAITING_FOR_GATED_RENDERS,
                    WAITING_FOR_TRANSFER,
                    WAITING_FOR_NO_REVERSE,
                    WAITING_FOR_TRANSFERRED_RENDERS,
                    WAITING_FOR_EVAPORATION,
                    WAITING_FOR_REOPENED_RENDERS -> requireWorldLifecycle(client);
            case CAPTURING_GATED -> tickCapturingGated(client);
            case RELEASING_MAIN_POWER -> tickReleasingMainPower(client);
            case CAPTURING_TRANSFERRED -> tickCapturingTransferred(client);
            case RELEASING_EVAPORATION_POWER -> tickReleasingEvaporationPower(client);
            case SAVING -> tickSaving(client);
            case DISCONNECTING -> tickDisconnecting(client);
            case WAITING_FOR_RESTART_TITLE -> tickWaitingForRestartTitle(client);
            case RESTARTING_WORLD -> restartWorld(client);
            case WAITING_FOR_RESTART_WORLD -> tickWaitingForRestartWorld(client);
            case WAITING_FOR_RESTART_INSPECTION -> tickWaitingForRestartInspection(client);
            case CAPTURING_REOPENED -> tickCapturingReopened(client);
            case COMPLETE -> {
            }
        }

        if (stage == Stage.WAITING_FOR_GATED_TRANSFER
                && gatedResult != null
                && hasGatedClientMirror(client)) {
            transition(Stage.WAITING_FOR_GATED_RENDERS);
        } else if (stage == Stage.WAITING_FOR_TRANSFER && transferResult != null) {
            transition(Stage.WAITING_FOR_NO_REVERSE);
        } else if (stage == Stage.WAITING_FOR_NO_REVERSE
                && stableTransferResult != null
                && hasTransferredClientMirror(client)) {
            transferredClientMirror = true;
            transition(Stage.WAITING_FOR_TRANSFERRED_RENDERS);
        } else if (stage == Stage.WAITING_FOR_EVAPORATION && evaporationResult != null) {
            transition(Stage.SAVING);
        }
    }

    private void tickWaitingForTitle(MinecraftClient client) {
        if (client.getOverlay() != null
                || !(client.currentScreen instanceof TitleScreen)
                || !hasExpectedFramebuffer(client)) {
            return;
        }
        validateRegistryAndMods();
        try {
            evidenceLayout = ForgeEvidenceLayout.resolve(client.runDirectory.toPath(), SCENARIO_ID);
            evidenceLayout.requireFreshTargets(
                    GATED_SCREENSHOT,
                    TRANSFERRED_SCREENSHOT,
                    REOPENED_SCREENSHOT
            );
        } catch (IOException exception) {
            fail(client, "Cannot initialize Forge channel evidence: " + exception.getMessage());
            return;
        }
        transition(Stage.STARTING_WORLD);
    }

    private void validateRegistryAndMods() {
        if (!ModList.get().isLoaded("etherology")
                || !ModList.get().isLoaded(ForgeE2eHarness.MOD_ID)
                || !ModList.get().isLoaded("forge")) {
            throw new IllegalStateException("The required Forge E2E mod roots are not loaded");
        }
        requireRegistryId(Registries.BLOCK.getIds(), CHANNEL_BLOCK_ID, "channel block");
        requireRegistryId(Registries.BLOCK.getIds(), STORAGE_BLOCK_ID, "storage block");
        requireRegistryId(
                Registries.BLOCK_ENTITY_TYPE.getIds(),
                CHANNEL_BLOCK_ENTITY_ID,
                "channel block entity"
        );
        requireRegistryId(
                Registries.BLOCK_ENTITY_TYPE.getIds(),
                STORAGE_BLOCK_ENTITY_ID,
                "storage block entity"
        );
        if (Registries.BLOCK.get(CHANNEL_BLOCK_ID) == Blocks.AIR
                || Registries.BLOCK.get(STORAGE_BLOCK_ID) == Blocks.AIR) {
            throw new IllegalStateException("Channel/storage registry ids resolved to air");
        }
    }

    private void startFreshWorld(MinecraftClient client) {
        Path saveDirectory = client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
        if (Files.exists(saveDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(saveDirectory)) {
            fail(client, "Refusing to reuse the Forge channel save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "A world is already active before Forge channel E2E creation");
            return;
        }

        LevelInfo levelInfo = new LevelInfo(
                WORLD_DISPLAY_NAME,
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                controlledGameRules(),
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
            fail(client, "Minecraft rejected Etherology's Forge data packs");
            return;
        }
        IntegratedServer server = client.getServer();
        if (!isWorldReady(client) || server == null || setupSubmitted) {
            return;
        }

        setupSubmitted = true;
        UUID playerId = client.player.getUuid();
        server.execute(() -> setupChannelFixture(server, playerId));
        transition(Stage.WAITING_FOR_SETUP);
    }

    private void tickWaitingForSetup(MinecraftClient client) {
        if (setupResult == null || !hasInitialClientMirror(client)) {
            return;
        }
        transition(Stage.WAITING_FOR_GATED_TRANSFER);
    }

    private void tickCapturingGated(MinecraftClient client) {
        if (!requireSuccessfulScreenshot(client, gatedScreenshot, "gated channel")) {
            return;
        }
        transition(Stage.RELEASING_MAIN_POWER);
    }

    private void tickReleasingMainPower(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        if (mainPowerReleaseResult == null && !mainPowerReleaseSubmitted) {
            mainPowerReleaseSubmitted = true;
            server.execute(() -> releasePowerOnSafeCadence(
                    server.getOverworld(),
                    CHANNEL_POS,
                    CHANNEL_POWER_POS,
                    true
            ));
            return;
        }
        PowerReleaseResult result = mainPowerReleaseResult;
        if (result == null) {
            return;
        }
        if (!result.deactivated()) {
            fail(client, "Removing strong power did not deactivate the main channel");
            return;
        }
        transition(Stage.WAITING_FOR_TRANSFER);
    }

    private void tickCapturingTransferred(MinecraftClient client) {
        if (!requireSuccessfulScreenshot(
                client,
                transferredScreenshot,
                "transferred channel"
        )) {
            return;
        }
        transition(Stage.RELEASING_EVAPORATION_POWER);
    }

    private void tickReleasingEvaporationPower(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        if (evaporationPowerReleaseResult == null && !evaporationPowerReleaseSubmitted) {
            evaporationPowerReleaseSubmitted = true;
            server.execute(() -> releasePowerOnSafeCadence(
                    server.getOverworld(),
                    EVAPORATION_CHANNEL_POS,
                    EVAPORATION_POWER_POS,
                    false
            ));
            return;
        }
        PowerReleaseResult result = evaporationPowerReleaseResult;
        if (result == null) {
            return;
        }
        if (!result.deactivated()) {
            fail(client, "Removing strong power did not deactivate the evaporation channel");
            return;
        }
        transition(Stage.WAITING_FOR_EVAPORATION);
    }

    private void tickSaving(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        if (!saveSubmitted) {
            saveSubmitted = true;
            server.execute(() -> {
                try {
                    savedSnapshot = captureSnapshot(server.getOverworld());
                    saveResult = server.saveAll(false, true, true);
                } catch (RuntimeException exception) {
                    recordServerFailure(exception);
                }
            });
        }
        if (saveResult == null || savedSnapshot == null) {
            return;
        }
        if (!saveResult) {
            fail(client, "The integrated server rejected the forced channel save");
            return;
        }
        transition(Stage.DISCONNECTING);
    }

    private void tickDisconnecting(MinecraftClient client) {
        if (client.world == null) {
            fail(client, "The client world vanished before the channel restart disconnect");
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
        if (restartSubmitted) {
            return;
        }
        restartSubmitted = true;
        client.createIntegratedServerLoader().start(new TitleScreen(), WORLD_DIRECTORY_NAME);
        transition(Stage.WAITING_FOR_RESTART_WORLD);
    }

    private void tickWaitingForRestartWorld(MinecraftClient client) {
        if (client.currentScreen instanceof DatapackFailureScreen) {
            fail(client, "Minecraft rejected the saved Forge data packs on channel restart");
            return;
        }
        if (!isWorldReady(client) || !hasFixtureBlocks(client)) {
            return;
        }
        transition(Stage.WAITING_FOR_RESTART_INSPECTION);
    }

    private void tickWaitingForRestartInspection(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        if (!restartInspectionSubmitted) {
            restartInspectionSubmitted = true;
            server.execute(() -> {
                try {
                    ChannelSnapshot reloaded = captureSnapshot(server.getOverworld());
                    ChannelSnapshot saved = savedSnapshot;
                    restartResult = new RestartResult(
                            reloaded,
                            saved != null && samePersistentState(saved, reloaded),
                            hasExactBlockEntityTypes(reloaded),
                            saved != null
                                    && saved.leverSupport().equals(reloaded.leverSupport())
                                    && reloaded.leverSupport().matchesExpected()
                    );
                } catch (RuntimeException exception) {
                    recordServerFailure(exception);
                }
            });
        }
        if (restartResult == null || !hasReopenedClientMirror(client)) {
            return;
        }
        reopenedClientMirror = true;
        transition(Stage.WAITING_FOR_REOPENED_RENDERS);
    }

    private void tickCapturingReopened(MinecraftClient client) {
        if (!requireSuccessfulScreenshot(client, reopenedScreenshot, "reopened channel")) {
            return;
        }
        publish(client);
    }

    private void setupChannelFixture(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = requireServerPlayer(server, playerId);
            world.setChunkForced(0, 0, true);
            boolean chunkForced = world.getForcedChunks().contains(ChunkPos.toLong(0, 0));
            boolean chunkLoaded = world.getChunkManager()
                    .getChunk(0, 0, ChunkStatus.FULL, true) != null;
            boolean fixturePositionsDistinct = hasDistinctFixturePositions();
            if (!fixturePositionsDistinct) {
                throw new IllegalStateException("The channel fixture positions overlap");
            }
            configureWorld(server, world);
            buildArena(world);

            Block channelBlock = Registries.BLOCK.get(CHANNEL_BLOCK_ID);
            Block storageBlock = Registries.BLOCK.get(STORAGE_BLOCK_ID);
            if (channelBlock == Blocks.AIR || storageBlock == Blocks.AIR) {
                throw new IllegalStateException("The channel fixture blocks resolved to air");
            }
            world.setBlockState(
                    SOURCE_STORAGE_POS,
                    storageBlock.getDefaultState(),
                    Block.NOTIFY_ALL
            );
            world.setBlockState(
                    TARGET_STORAGE_POS,
                    storageBlock.getDefaultState(),
                    Block.NOTIFY_ALL
            );
            BlockState directedChannelState = withStateProperty(
                    channelBlock.getDefaultState(),
                    FACING_PROPERTY,
                    Direction.EAST
            );
            placePrePoweredChannel(
                    world,
                    CHANNEL_POS,
                    CHANNEL_POWER_POS,
                    CHANNEL_FEEDER_POS,
                    directedChannelState
            );
            placePrePoweredChannel(
                    world,
                    EVAPORATION_CHANNEL_POS,
                    EVAPORATION_POWER_POS,
                    EVAPORATION_FEEDER_POS,
                    directedChannelState
            );
            world.setBlockState(
                    LEVER_SUPPORT_CHANNEL_POS,
                    directedChannelState,
                    Block.NOTIFY_ALL
            );
            world.setBlockState(
                    LEVER_POS,
                    attachedLeverState(),
                    Block.NOTIFY_ALL
            );
            player.changeGameMode(GameMode.CREATIVE);
            player.teleport(
                    world,
                    CAMERA_POS.getX() + 0.5,
                    CAMERA_POS.getY(),
                    CAMERA_POS.getZ() + 0.5,
                    0.0f,
                    10.0f
            );
            player.setSpawnPoint(World.OVERWORLD, CAMERA_POS, 0.0f, true, false);
            placementResult = new PlacementResult(
                    world.getTime(),
                    chunkLoaded,
                    chunkForced,
                    fixturePositionsDistinct
            );
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    private void inspectScheduledSetup(ServerWorld world) {
        if (setupResult != null) {
            return;
        }
        PlacementResult placement = placementResult;
        if (placement == null || world.getTime() <= placement.placementWorldTime()) {
            return;
        }

        BlockState channelState = world.getBlockState(CHANNEL_POS);
        BlockState evaporationState = world.getBlockState(EVAPORATION_CHANNEL_POS);
        LeverSupportResult leverSupport = observeLeverSupport(world);
        boolean mainRepeaterPowered = hasPoweredRepeater(world, CHANNEL_POWER_POS);
        boolean evaporationRepeaterPowered = hasPoweredRepeater(
                world,
                EVAPORATION_POWER_POS
        );
        boolean mainFeederPresent = world.getBlockState(CHANNEL_FEEDER_POS)
                .isOf(Blocks.REDSTONE_BLOCK);
        boolean evaporationFeederPresent = world.getBlockState(EVAPORATION_FEEDER_POS)
                .isOf(Blocks.REDSTONE_BLOCK);
        int mainStrongPower = world.getReceivedStrongRedstonePower(CHANNEL_POS);
        int evaporationStrongPower = world.getReceivedStrongRedstonePower(
                EVAPORATION_CHANNEL_POS
        );
        boolean exactStrongPower = mainRepeaterPowered
                && evaporationRepeaterPowered
                && mainFeederPresent
                && evaporationFeederPresent
                && mainStrongPower == 15
                && evaporationStrongPower == 15;
        boolean prePoweredPlacement = exactStrongPower
                && isActivated(channelState)
                && isActivated(evaporationState);
        boolean fixtureTopology = SOURCE_STORAGE_POS.equals(CHANNEL_POS.up())
                && TARGET_STORAGE_POS.equals(CHANNEL_POS.east())
                && Direction.EAST.equals(stateProperty(channelState, FACING_PROPERTY))
                && "out".equals(statePropertyName(channelState, EAST_CONNECTION_PROPERTY))
                && "in".equals(statePropertyName(channelState, UP_CONNECTION_PROPERTY));
        if (!prePoweredPlacement || !fixtureTopology || !leverSupport.matchesExpected()) {
            throw new IllegalStateException(
                    "The first scheduled tick did not resolve the exact channel fixtures: "
                            + "main_repeater=" + mainRepeaterPowered
                            + ";evaporation_repeater=" + evaporationRepeaterPowered
                            + ";main_feeder=" + mainFeederPresent
                            + ";evaporation_feeder=" + evaporationFeederPresent
                            + ";main_strong_power=" + mainStrongPower
                            + ";evaporation_strong_power=" + evaporationStrongPower
                            + ";prepowered=" + prePoweredPlacement
                            + ";fixture_topology=" + fixtureTopology
                            + ";main_state=" + channelState
                            + ";evaporation_state=" + evaporationState
                            + ";lever=" + leverSupport.evidence()
            );
        }

        BlockEntity source = requireStorage(world, SOURCE_STORAGE_POS);
        BlockEntity target = requireStorage(world, TARGET_STORAGE_POS);
        BlockEntity channel = requireChannel(world, CHANNEL_POS);
        BlockEntity evaporationChannel = requireChannel(
                world,
                EVAPORATION_CHANNEL_POS
        );
        if (!sameEther(channelEther(channel), 0.0f)
                || !sameEther(channelEther(evaporationChannel), 0.0f)
                || isEvaporating(channel)
                || isCrossEvaporating(channel)
                || isEvaporating(evaporationChannel)
                || isCrossEvaporating(evaporationChannel)) {
            throw new IllegalStateException(
                    "The scheduled channel block entities were not initially quiescent"
            );
        }

        setStorageEther(source, INITIAL_NETWORK_ETHER);
        setStorageEther(target, 0.0f);
        setChannelState(channel, 0.0f, false, false);
        setChannelState(evaporationChannel, INITIAL_EVAPORATION_ETHER, false, false);

        NbtCompound roundTripNbt = evaporationChannel.createNbtWithId();
        BlockEntity reconstructed = BlockEntity.createFromNbt(
                EVAPORATION_CHANNEL_POS,
                evaporationState,
                roundTripNbt
        );
        boolean nbtReconstructed = reconstructed != null
                && reconstructed.getType() == evaporationChannel.getType()
                && sameEther(
                    channelEther(reconstructed),
                    INITIAL_EVAPORATION_ETHER
                )
                && !isEvaporating(reconstructed)
                && !isCrossEvaporating(reconstructed);
        NbtCompound initialSyncNbt = evaporationChannel.toInitialChunkDataNbt();
        boolean nbtSyncContract = evaporationChannel.toUpdatePacket() != null
                && initialSyncNbt.contains(STORED_ETHER_KEY)
                && initialSyncNbt.contains(EVAPORATING_KEY)
                && initialSyncNbt.contains(CROSS_EVAPORATING_KEY);

        channel.markDirty();
        evaporationChannel.markDirty();
        world.getChunkManager().markForUpdate(CHANNEL_POS);
        world.getChunkManager().markForUpdate(EVAPORATION_CHANNEL_POS);

        ChannelSnapshot initialSnapshot = captureSnapshot(world);
        boolean exactSeededState = sameEther(
                    initialSnapshot.sourceEther(),
                    INITIAL_NETWORK_ETHER
                )
                && sameEther(initialSnapshot.channelEther(), 0.0f)
                && sameEther(initialSnapshot.targetEther(), 0.0f)
                && sameEther(
                    initialSnapshot.evaporationEther(),
                    INITIAL_EVAPORATION_ETHER
                )
                && initialSnapshot.channelActivated()
                && initialSnapshot.evaporationActivated()
                && !initialSnapshot.evaporating()
                && !initialSnapshot.crossEvaporating();
        if (!exactSeededState) {
            throw new IllegalStateException(
                    "The scheduled channel world state and block-entity NBT diverged"
            );
        }
        setupResult = new SetupResult(
                placement.chunkLoaded(),
                placement.chunkForced(),
                placement.fixturePositionsDistinct(),
                true,
                true,
                leverSupport,
                nbtReconstructed,
                nbtSyncContract,
                initialSnapshot
        );
    }

    private void releasePowerOnSafeCadence(
            ServerWorld world,
            BlockPos channelPos,
            BlockPos powerPos,
            boolean mainChannel
    ) {
        try {
            long worldTime = world.getTime();
            if (Math.floorMod(worldTime, 5L) != 1L) {
                if (mainChannel) {
                    mainPowerReleaseSubmitted = false;
                } else {
                    evaporationPowerReleaseSubmitted = false;
                }
                return;
            }

            world.setBlockState(powerPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            requireChannel(world, channelPos);
            if (mainChannel) {
                mainPowerMutation = new PowerMutation(worldTime);
            } else {
                evaporationPowerMutation = new PowerMutation(worldTime);
            }
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    private void inspectNaturalPowerRelease(ServerWorld world, boolean mainChannel) {
        PowerReleaseResult release = mainChannel
                ? mainPowerReleaseResult
                : evaporationPowerReleaseResult;
        if (release != null) {
            return;
        }
        PowerMutation mutation = mainChannel
                ? mainPowerMutation
                : evaporationPowerMutation;
        if (mutation == null || world.getTime() <= mutation.worldTime()) {
            return;
        }

        BlockPos channelPos = mainChannel ? CHANNEL_POS : EVAPORATION_CHANNEL_POS;
        BlockPos powerPos = mainChannel ? CHANNEL_POWER_POS : EVAPORATION_POWER_POS;
        if (!world.getBlockState(powerPos).isAir()
                || world.getReceivedStrongRedstonePower(channelPos) != 0) {
            throw new IllegalStateException(
                    "Removing the powered repeater did not remove exact strong power"
            );
        }
        if (isActivated(world.getBlockState(channelPos))) {
            return;
        }

        PowerReleaseResult result = new PowerReleaseResult(world.getTime(), true);
        if (mainChannel) {
            mainPowerReleaseResult = result;
        } else {
            evaporationPowerReleaseResult = result;
        }
    }

    private void inspectGatedTransfer(ServerWorld world) {
        if (gatedResult != null) {
            return;
        }
        ChannelSnapshot snapshot = captureSnapshot(world);
        if (!sameEther(snapshot.networkTotal(), INITIAL_NETWORK_ETHER)) {
            throw new IllegalStateException("The gated network did not conserve one Ether");
        }
        if (snapshot.targetEther() > FLOAT_TOLERANCE) {
            throw new IllegalStateException("The activated channel forwarded Ether");
        }
        if (!sameEther(snapshot.sourceEther(), 0.0f)
                || !sameEther(snapshot.channelEther(), INITIAL_NETWORK_ETHER)) {
            return;
        }
        if (!snapshot.channelActivated()) {
            throw new IllegalStateException("The receiving channel lost its strong-power gate");
        }

        long worldTime = world.getTime();
        if (gatedFirstObservedTime < 0L) {
            gatedFirstObservedTime = worldTime;
            return;
        }
        if (worldTime >= gatedFirstObservedTime + 5L
                && Math.floorMod(worldTime, 5L) == 0L) {
            gatedResult = new GatedResult(
                    gatedFirstObservedTime,
                    worldTime,
                    snapshot
            );
        }
    }

    private void inspectReleasedTransfer(ServerWorld world) {
        if (transferResult != null) {
            return;
        }
        PowerReleaseResult release = mainPowerReleaseResult;
        if (release == null || world.getTime() <= release.worldTime()) {
            return;
        }

        ChannelSnapshot snapshot = captureSnapshot(world);
        long worldTime = world.getTime();
        if (!sameEther(snapshot.networkTotal(), INITIAL_NETWORK_ETHER)) {
            throw new IllegalStateException("The released network did not conserve one Ether");
        }
        if (Math.floorMod(worldTime, 5L) != 0L) {
            if (!sameEther(snapshot.channelEther(), INITIAL_NETWORK_ETHER)
                    || !sameEther(snapshot.targetEther(), 0.0f)) {
                throw new IllegalStateException("The channel moved Ether outside fifth-tick cadence");
            }
            return;
        }

        boolean exactTransfer = sameEther(snapshot.sourceEther(), 0.0f)
                && sameEther(snapshot.channelEther(), 0.0f)
                && sameEther(snapshot.targetEther(), INITIAL_NETWORK_ETHER);
        transferResult = new TransferResult(
                release.worldTime(),
                worldTime,
                exactTransfer,
                sameEther(snapshot.networkTotal(), INITIAL_NETWORK_ETHER),
                snapshot
        );
    }

    private void inspectNoReverseMovement(ServerWorld world) {
        if (stableTransferResult != null || transferResult == null) {
            return;
        }
        long worldTime = world.getTime();
        if (worldTime < transferResult.transferWorldTime() + 5L
                || Math.floorMod(worldTime, 5L) != 0L) {
            return;
        }

        ChannelSnapshot snapshot = captureSnapshot(world);
        boolean unchanged = sameNetworkDistribution(
                transferResult.snapshot(),
                snapshot
        );
        stableTransferResult = new StableTransferResult(worldTime, unchanged, snapshot);
    }

    private void inspectEvaporation(ServerWorld world) {
        if (evaporationResult != null) {
            return;
        }
        EvaporationObservation observation = evaporationObservation;
        if (observation != null) {
            inspectQuiescentEvaporationState(world, observation);
            return;
        }
        PowerReleaseResult release = evaporationPowerReleaseResult;
        if (release == null || world.getTime() <= release.worldTime()) {
            return;
        }

        ChannelSnapshot snapshot = captureSnapshot(world);
        long worldTime = world.getTime();
        if (Math.floorMod(worldTime, 5L) != 0L) {
            if (!sameEther(snapshot.evaporationEther(), INITIAL_EVAPORATION_ETHER)
                    || snapshot.evaporating()
                    || snapshot.crossEvaporating()) {
                throw new IllegalStateException(
                        "The missing-output channel changed outside fifth-tick cadence"
                );
            }
            return;
        }

        float evaporated = INITIAL_EVAPORATION_ETHER - snapshot.evaporationEther();
        boolean exactLoss = sameEther(evaporated, EXPECTED_EVAPORATED_ETHER)
                && sameEther(
                    snapshot.evaporationEther(),
                    EXPECTED_REMAINING_EVAPORATION_ETHER
                );
        boolean exactFlags = snapshot.evaporating() && !snapshot.crossEvaporating();
        world.setBlockState(
                EVAPORATION_POWER_POS,
                poweredRepeaterState(),
                Block.NOTIFY_ALL
        );
        if (!hasPoweredRepeater(world, EVAPORATION_POWER_POS)
                || world.getReceivedStrongRedstonePower(EVAPORATION_CHANNEL_POS) != 15) {
            throw new IllegalStateException(
                    "The evaporation channel repeater did not restore strong power"
            );
        }
        evaporationObservation = new EvaporationObservation(
                release.worldTime(),
                worldTime,
                evaporated,
                exactLoss,
                exactFlags,
                snapshot
        );
    }

    private void inspectQuiescentEvaporationState(
            ServerWorld world,
            EvaporationObservation observation
    ) {
        long worldTime = world.getTime();
        if (worldTime <= observation.evaporationWorldTime()) {
            return;
        }

        ChannelSnapshot retainedSnapshot = captureSnapshot(world);
        if (!hasPoweredRepeater(world, EVAPORATION_POWER_POS)
                || world.getReceivedStrongRedstonePower(EVAPORATION_CHANNEL_POS) != 15) {
            throw new IllegalStateException(
                    "The restored repeater did not retain exact strong power"
            );
        }
        if (evaporationActivationObservedTime < 0L) {
            if (!retainedSnapshot.evaporationActivated()) {
                return;
            }
            evaporationActivationObservedTime = worldTime;
        }
        if (worldTime < observation.evaporationWorldTime() + 5L
                || Math.floorMod(worldTime, 5L) != 0L) {
            return;
        }
        boolean quiescent = sameEther(
                    retainedSnapshot.evaporationEther(),
                    EXPECTED_REMAINING_EVAPORATION_ETHER
                )
                && retainedSnapshot.evaporationActivated()
                && !retainedSnapshot.evaporating()
                && !retainedSnapshot.crossEvaporating();
        if (!quiescent) {
            throw new IllegalStateException(
                    "The powered evaporation channel did not clear transient flags"
            );
        }
        evaporationResult = new EvaporationResult(
                observation.releaseWorldTime(),
                observation.evaporationWorldTime(),
                observation.evaporatedEther(),
                observation.exactLoss(),
                observation.exactFlags(),
                observation.snapshot(),
                retainedSnapshot
        );
    }

    private ChannelSnapshot captureSnapshot(ServerWorld world) {
        BlockEntity source = requireStorage(world, SOURCE_STORAGE_POS);
        BlockEntity target = requireStorage(world, TARGET_STORAGE_POS);
        BlockEntity channel = requireChannel(world, CHANNEL_POS);
        BlockEntity evaporationChannel = requireChannel(
                world,
                EVAPORATION_CHANNEL_POS
        );
        requireChannel(world, LEVER_SUPPORT_CHANNEL_POS);
        return new ChannelSnapshot(
                world.getTime(),
                storageEther(source),
                channelEther(channel),
                storageEther(target),
                channelEther(evaporationChannel),
                isActivated(world.getBlockState(CHANNEL_POS)),
                isActivated(world.getBlockState(EVAPORATION_CHANNEL_POS)),
                isEvaporating(evaporationChannel),
                isCrossEvaporating(evaporationChannel),
                blockId(world, SOURCE_STORAGE_POS),
                blockEntityId(source),
                blockId(world, CHANNEL_POS),
                blockEntityId(channel),
                blockId(world, TARGET_STORAGE_POS),
                blockEntityId(target),
                blockId(world, EVAPORATION_CHANNEL_POS),
                blockEntityId(evaporationChannel),
                observeLeverSupport(world)
        );
    }

    private void captureAfterStableRenders(
            MinecraftClient client,
            String fileName,
            int requiredRenders,
            Consumer<ScreenshotResult> consumer,
            Stage captureStage
    ) {
        if (!isCaptureStateExact(client, captureStage)) {
            completedRenders = 0;
            return;
        }
        completedRenders++;
        if (completedRenders < requiredRenders) {
            return;
        }
        transition(captureStage);
        saveScreenshot(client, fileName, captureStage, consumer);
    }

    private boolean isCaptureStateExact(MinecraftClient client, Stage captureStage) {
        return client.currentScreen == null
                && hasCaptureClientMirror(client, captureStage)
                && hasExpectedFramebuffer(client)
                && isFixtureRenderReady(client)
                && hasExpectedCameraPose(client);
    }

    private boolean hasCaptureClientMirror(MinecraftClient client, Stage captureStage) {
        return switch (captureStage) {
            case CAPTURING_GATED -> hasGatedClientMirror(client);
            case CAPTURING_TRANSFERRED -> hasTransferredClientMirror(client);
            case CAPTURING_REOPENED -> hasReopenedClientMirror(client);
            default -> throw new IllegalArgumentException(
                    "Not a channel screenshot stage: " + captureStage
            );
        };
    }

    private boolean isFixtureRenderReady(MinecraftClient client) {
        if (!client.worldRenderer.isTerrainRenderComplete()) {
            return false;
        }
        for (BlockPos position : FIXTURE_POSITIONS) {
            if (!client.worldRenderer.isRenderingReady(position)) {
                return false;
            }
        }
        return true;
    }

    private void saveScreenshot(
            MinecraftClient client,
            String fileName,
            Stage captureStage,
            Consumer<ScreenshotResult> consumer
    ) {
        ForgeEvidenceLayout layout = evidenceLayout;
        if (layout == null) {
            consumer.accept(ScreenshotResult.failed("The evidence layout is not initialized"));
            return;
        }
        boolean captureMirror = hasCaptureClientMirror(client, captureStage);
        boolean captureRenderReady = isFixtureRenderReady(client);
        boolean captureCameraExact = hasExpectedCameraPose(client);
        if (client.currentScreen != null
                || !captureMirror
                || !captureRenderReady
                || !captureCameraExact
                || !hasExpectedFramebuffer(client)) {
            consumer.accept(ScreenshotResult.failed(
                    "The exact client fixture was not render-ready at capture time"
            ));
            return;
        }
        latchCaptureState(
                captureStage,
                captureMirror,
                captureRenderReady,
                captureCameraExact
        );
        ScreenshotRecorder.saveScreenshot(
                layout.scenarioRoot().toFile(),
                fileName,
                client.getFramebuffer(),
                message -> consumer.accept(inspectScreenshot(layout.screenshotPath(fileName)))
        );
    }

    private void latchCaptureState(
            Stage captureStage,
            boolean captureMirror,
            boolean captureRenderReady,
            boolean captureCameraExact
    ) {
        switch (captureStage) {
            case CAPTURING_GATED -> {
                gatedCaptureMirror = captureMirror;
                gatedCaptureRenderReady = captureRenderReady;
                gatedCaptureCameraExact = captureCameraExact;
            }
            case CAPTURING_TRANSFERRED -> {
                transferredCaptureMirror = captureMirror;
                transferredCaptureRenderReady = captureRenderReady;
                transferredCaptureCameraExact = captureCameraExact;
            }
            case CAPTURING_REOPENED -> {
                reopenedCaptureMirror = captureMirror;
                reopenedCaptureRenderReady = captureRenderReady;
                reopenedCaptureCameraExact = captureCameraExact;
            }
            default -> throw new IllegalArgumentException(
                    "Not a channel screenshot stage: " + captureStage
            );
        }
    }

    private ScreenshotResult inspectScreenshot(Path path) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return ScreenshotResult.failed("Minecraft did not write a regular PNG");
            }
            long size = Files.size(path);
            if (size <= 0L) {
                return ScreenshotResult.failed("Minecraft wrote an empty PNG");
            }
            return new ScreenshotResult(true, size, ForgeArtifactDigest.sha256(path), "");
        } catch (IOException exception) {
            return ScreenshotResult.failed(exception.getMessage());
        }
    }

    private boolean requireSuccessfulScreenshot(
            MinecraftClient client,
            ScreenshotResult screenshot,
            String label
    ) {
        if (screenshot == null) {
            return false;
        }
        if (!screenshot.passed()) {
            fail(client, "The " + label + " screenshot failed: " + screenshot.failure());
            return false;
        }
        return true;
    }

    private void publish(MinecraftClient client) {
        if (stage == Stage.COMPLETE) {
            return;
        }
        try {
            List<ForgeArtifactDigest> artifacts = List.of(
                    ForgeArtifactDigest.capture("etherology"),
                    ForgeArtifactDigest.capture(ForgeE2eHarness.MOD_ID)
            );
            JsonObject report = createReport(client, artifacts);
            AtomicEvidenceWriter.writeReportThenMarker(evidenceLayout, report);
            LOGGER.info(
                    "Forge ethereal-channel evidence complete: {}",
                    evidenceLayout.reportsDirectory()
            );
        } catch (IOException exception) {
            LOGGER.error("Cannot publish Forge ethereal-channel evidence", exception);
        } finally {
            stage = Stage.COMPLETE;
            client.scheduleStop();
        }
    }

    private JsonObject createReport(
            MinecraftClient client,
            List<ForgeArtifactDigest> artifacts
    ) {
        JsonArray assertions = new JsonArray();
        SetupResult setup = setupResult;
        GatedResult gated = gatedResult;
        TransferResult transferred = transferResult;
        StableTransferResult stable = stableTransferResult;
        EvaporationResult evaporation = evaporationResult;
        ChannelSnapshot saved = savedSnapshot;
        RestartResult restarted = restartResult;

        addAssertion(assertions, "lifecycle", lifecycleFailure.isEmpty(), lifecycleFailure);
        addAssertion(assertions, "forge_loaded", ModList.get().isLoaded("forge"), "forge");
        addAssertion(
                assertions,
                "etherology_loaded",
                ModList.get().isLoaded("etherology"),
                "etherology"
        );
        addAssertion(assertions, "chunk_loaded", setup != null && setup.chunkLoaded(), setup);
        addAssertion(assertions, "chunk_forced", setup != null && setup.chunkForced(), setup);
        addAssertion(
                assertions,
                "fixture_positions_distinct",
                setup != null && setup.fixturePositionsDistinct(),
                setup
        );
        addAssertion(
                assertions,
                "prepowered_placement",
                setup != null && setup.prePoweredPlacement(),
                setup
        );
        addAssertion(
                assertions,
                "channel_block_id",
                setup != null
                        && CHANNEL_BLOCK_ID.toString().equals(
                            setup.initialSnapshot().channelBlockId()
                        ),
                setup
        );
        addAssertion(
                assertions,
                "channel_block_entity_id",
                setup != null
                        && CHANNEL_BLOCK_ENTITY_ID.toString().equals(
                            setup.initialSnapshot().channelBlockEntityId()
                        ),
                setup
        );
        addAssertion(
                assertions,
                "storage_block_id",
                setup != null
                        && STORAGE_BLOCK_ID.toString().equals(
                            setup.initialSnapshot().sourceBlockId()
                        )
                        && STORAGE_BLOCK_ID.toString().equals(
                            setup.initialSnapshot().targetBlockId()
                        ),
                setup
        );
        addAssertion(
                assertions,
                "storage_block_entity_id",
                setup != null
                        && STORAGE_BLOCK_ENTITY_ID.toString().equals(
                            setup.initialSnapshot().sourceBlockEntityId()
                        )
                        && STORAGE_BLOCK_ENTITY_ID.toString().equals(
                            setup.initialSnapshot().targetBlockEntityId()
                        ),
                setup
        );
        addAssertion(
                assertions,
                "fixture_topology",
                setup != null && setup.fixtureTopology(),
                setup
        );
        LeverSupportResult reopenedLeverSupport = restarted == null
                ? null
                : restarted.snapshot().leverSupport();
        addAssertion(
                assertions,
                "lever_support_topology",
                setup != null
                        && setup.leverSupport().matchesExpected()
                        && restarted != null
                        && restarted.leverSupportRetained(),
                reopenedLeverSupport == null ? null : reopenedLeverSupport.evidence()
        );
        addAssertion(
                assertions,
                "channel_nbt_reconstructed",
                setup != null && setup.nbtReconstructed(),
                setup
        );
        addAssertion(
                assertions,
                "channel_nbt_sync_contract",
                setup != null && setup.nbtSyncContract(),
                setup
        );
        addAssertion(
                assertions,
                "gated_activated",
                gated != null && gated.snapshot().channelActivated(),
                gated
        );
        addAssertion(
                assertions,
                "gated_received_one",
                gated != null
                        && sameEther(gated.snapshot().channelEther(), INITIAL_NETWORK_ETHER)
                        && sameEther(gated.snapshot().sourceEther(), 0.0f),
                gated
        );
        addAssertion(
                assertions,
                "gated_retained_without_forwarding",
                gated != null
                        && gated.retainedWorldTime() >= gated.receivedWorldTime() + 5L
                        && sameEther(gated.snapshot().targetEther(), 0.0f),
                gated
        );
        addAssertion(
                assertions,
                "release_deactivated",
                mainPowerReleaseResult != null && mainPowerReleaseResult.deactivated(),
                mainPowerReleaseResult
        );
        addAssertion(
                assertions,
                "transfer_fifth_tick",
                transferred != null
                        && Math.floorMod(transferred.transferWorldTime(), 5L) == 0L,
                transferred
        );
        addAssertion(
                assertions,
                "transfer_exact_one",
                transferred != null && transferred.exactTransfer(),
                transferred
        );
        addAssertion(
                assertions,
                "transfer_total_conserved",
                transferred != null && transferred.totalConserved(),
                transferred
        );
        addAssertion(
                assertions,
                "transfer_no_reverse",
                stable != null && stable.unchanged(),
                stable
        );
        addAssertion(
                assertions,
                "evaporation_fifth_tick",
                evaporation != null
                        && Math.floorMod(evaporation.evaporationWorldTime(), 5L) == 0L,
                evaporation
        );
        addAssertion(
                assertions,
                "evaporation_exact_point_two",
                evaporation != null
                        && evaporation.exactLoss()
                        && sameEther(
                            evaporation.evaporatedEther(),
                            EXPECTED_EVAPORATED_ETHER
                        ),
                evaporation
        );
        addAssertion(
                assertions,
                "evaporation_flags",
                evaporation != null
                        && evaporation.exactFlags()
                        && evaporation.snapshot().evaporationActivated()
                        && !evaporation.snapshot().evaporating()
                        && !evaporation.snapshot().crossEvaporating(),
                evaporation
        );
        addAssertion(
                assertions,
                "client_mirror",
                transferredClientMirror,
                transferredClientMirror
        );
        addAssertion(
                assertions,
                "gated_capture_mirror",
                gatedCaptureMirror,
                gatedCaptureMirror
        );
        addAssertion(
                assertions,
                "transferred_capture_mirror",
                transferredCaptureMirror,
                transferredCaptureMirror
        );
        addAssertion(
                assertions,
                "reopened_capture_mirror",
                reopenedCaptureMirror,
                reopenedCaptureMirror
        );
        boolean captureRenderReady = gatedCaptureRenderReady
                && transferredCaptureRenderReady
                && reopenedCaptureRenderReady;
        addAssertion(
                assertions,
                "capture_render_ready",
                captureRenderReady,
                "gated=" + gatedCaptureRenderReady
                        + ";transferred=" + transferredCaptureRenderReady
                        + ";reopened=" + reopenedCaptureRenderReady
        );
        boolean captureCameraExact = gatedCaptureCameraExact
                && transferredCaptureCameraExact
                && reopenedCaptureCameraExact;
        addAssertion(
                assertions,
                "capture_camera_exact",
                captureCameraExact,
                "gated=" + gatedCaptureCameraExact
                        + ";transferred=" + transferredCaptureCameraExact
                        + ";reopened=" + reopenedCaptureCameraExact
        );
        addAssertion(assertions, "forced_save", Boolean.TRUE.equals(saveResult), saveResult);
        addAssertion(assertions, "saved_snapshot", saved != null, saved);
        addAssertion(
                assertions,
                "restart_exact_state",
                restarted != null && restarted.exactStateRetained(),
                restarted
        );
        addAssertion(
                assertions,
                "restart_block_entity_types",
                restarted != null && restarted.blockEntityTypesRetained(),
                restarted
        );
        addAssertion(
                assertions,
                "restart_client_mirror",
                reopenedClientMirror,
                reopenedClientMirror
        );
        addScreenshotAssertion(assertions, "gated", gatedScreenshot);
        addScreenshotAssertion(assertions, "transferred", transferredScreenshot);
        addScreenshotAssertion(assertions, "reopened", reopenedScreenshot);
        for (ForgeArtifactDigest artifact : artifacts) {
            addAssertion(
                    assertions,
                    "artifact_" + artifact.modId(),
                    artifact.passed(),
                    artifact
            );
        }

        boolean passed = true;
        for (int index = 0; index < assertions.size(); index++) {
            passed &= assertions.get(index).getAsJsonObject().get("passed").getAsBoolean();
        }

        JsonObject report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("scenario", SCENARIO_ID);
        report.addProperty("profile_id", evidenceLayout.profileId());
        report.addProperty("profile_manifest_size", evidenceLayout.profileManifestSize());
        report.addProperty(
                "profile_manifest_sha256",
                evidenceLayout.profileManifestSha256()
        );
        report.addProperty("artifact_node", "forge-1.20.1");
        report.addProperty("minecraft", "1.20.1");
        report.addProperty("loader", "forge");
        report.addProperty("loader_version", "47.4.9");
        report.addProperty("java", 17);
        report.addProperty("passed", passed);
        report.addProperty("client_ticks", clientTicks);
        report.addProperty("framebuffer_width", client.getFramebuffer().textureWidth);
        report.addProperty("framebuffer_height", client.getFramebuffer().textureHeight);
        report.add("assertions", assertions);

        JsonArray artifactJson = new JsonArray();
        for (ForgeArtifactDigest artifact : artifacts) {
            JsonObject value = new JsonObject();
            value.addProperty("mod_id", artifact.modId());
            value.addProperty("passed", artifact.passed());
            value.addProperty("file_name", artifact.fileName());
            value.addProperty("size", artifact.size());
            value.addProperty("sha256", artifact.sha256());
            value.addProperty("failure", artifact.failure());
            artifactJson.add(value);
        }
        report.add("artifacts", artifactJson);
        return report;
    }

    private static void addScreenshotAssertion(
            JsonArray assertions,
            String role,
            ScreenshotResult screenshot
    ) {
        addAssertion(
                assertions,
                role + "_screenshot",
                screenshot != null && screenshot.passed(),
                screenshot
        );
    }

    private static void addAssertion(
            JsonArray assertions,
            String name,
            boolean passed,
            Object actual
    ) {
        JsonObject assertion = new JsonObject();
        assertion.addProperty("name", name);
        assertion.addProperty("passed", passed);
        assertion.addProperty("actual", String.valueOf(actual));
        assertions.add(assertion);
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) {
            return;
        }
        lifecycleFailure = failure == null ? "Unknown Forge channel E2E failure" : failure;
        LOGGER.error("Forge ethereal-channel lifecycle failure: {}", lifecycleFailure);
        if (evidenceLayout == null) {
            stage = Stage.COMPLETE;
            client.scheduleStop();
            return;
        }
        publish(client);
    }

    private void transition(Stage nextStage) {
        stage = nextStage;
        stageClientTicks = 0;
        completedRenders = 0;
    }

    private void recordServerFailure(RuntimeException exception) {
        LOGGER.error("Forge channel server operation failed", exception);
        serverFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private void requireWorldLifecycle(MinecraftClient client) {
        if (!isWorldReady(client)) {
            throw new IllegalStateException("The integrated world became unavailable");
        }
    }

    private boolean isWorldReady(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        return server != null
                && server.isRunning()
                && !server.isStopping()
                && client.world != null
                && client.player != null
                && client.player.networkHandler != null
                && client.interactionManager != null;
    }

    private boolean hasInitialClientMirror(MinecraftClient client) {
        return hasFixtureBlocks(client)
                && clientBlockEntity(client, SOURCE_STORAGE_POS, STORAGE_BLOCK_ENTITY_ID)
                != null
                && clientBlockEntity(client, TARGET_STORAGE_POS, STORAGE_BLOCK_ENTITY_ID)
                != null
                && clientBlockEntity(client, CHANNEL_POS, CHANNEL_BLOCK_ENTITY_ID) != null
                && clientBlockEntity(
                    client,
                    EVAPORATION_CHANNEL_POS,
                    CHANNEL_BLOCK_ENTITY_ID
                ) != null
                && clientBlockEntity(
                    client,
                    LEVER_SUPPORT_CHANNEL_POS,
                    CHANNEL_BLOCK_ENTITY_ID
                ) != null
                && observeLeverSupport(client.world).matchesExpected();
    }

    private boolean hasGatedClientMirror(MinecraftClient client) {
        if (!hasCaptureFixtureBlocks(client, Stage.CAPTURING_GATED)) {
            return false;
        }
        BlockEntity channel = clientBlockEntity(
                client,
                CHANNEL_POS,
                CHANNEL_BLOCK_ENTITY_ID
        );
        BlockEntity evaporationChannel = clientBlockEntity(
                client,
                EVAPORATION_CHANNEL_POS,
                CHANNEL_BLOCK_ENTITY_ID
        );
        return channel != null
                && evaporationChannel != null
                && isActivated(client.world.getBlockState(CHANNEL_POS))
                && isActivated(client.world.getBlockState(EVAPORATION_CHANNEL_POS))
                && sameEther(channelEther(channel), INITIAL_NETWORK_ETHER)
                && sameEther(
                    channelEther(evaporationChannel),
                    INITIAL_EVAPORATION_ETHER
                )
                && !isEvaporating(channel)
                && !isCrossEvaporating(channel)
                && !isEvaporating(evaporationChannel)
                && !isCrossEvaporating(evaporationChannel);
    }

    private boolean hasTransferredClientMirror(MinecraftClient client) {
        if (!hasCaptureFixtureBlocks(client, Stage.CAPTURING_TRANSFERRED)) {
            return false;
        }
        BlockEntity channel = clientBlockEntity(
                client,
                CHANNEL_POS,
                CHANNEL_BLOCK_ENTITY_ID
        );
        BlockEntity evaporationChannel = clientBlockEntity(
                client,
                EVAPORATION_CHANNEL_POS,
                CHANNEL_BLOCK_ENTITY_ID
        );
        return channel != null
                && evaporationChannel != null
                && !isActivated(client.world.getBlockState(CHANNEL_POS))
                && sameEther(channelEther(channel), 0.0f)
                && isActivated(client.world.getBlockState(EVAPORATION_CHANNEL_POS))
                && sameEther(
                    channelEther(evaporationChannel),
                    INITIAL_EVAPORATION_ETHER
                )
                && !isEvaporating(channel)
                && !isCrossEvaporating(channel)
                && !isEvaporating(evaporationChannel)
                && !isCrossEvaporating(evaporationChannel);
    }

    private boolean hasReopenedClientMirror(MinecraftClient client) {
        if (!hasCaptureFixtureBlocks(client, Stage.CAPTURING_REOPENED)) {
            return false;
        }
        BlockEntity channel = clientBlockEntity(
                client,
                CHANNEL_POS,
                CHANNEL_BLOCK_ENTITY_ID
        );
        BlockEntity evaporationChannel = clientBlockEntity(
                client,
                EVAPORATION_CHANNEL_POS,
                CHANNEL_BLOCK_ENTITY_ID
        );
        return channel != null
                && evaporationChannel != null
                && !isActivated(client.world.getBlockState(CHANNEL_POS))
                && sameEther(channelEther(channel), 0.0f)
                && sameEther(
                    channelEther(evaporationChannel),
                    EXPECTED_REMAINING_EVAPORATION_ETHER
                )
                && isActivated(client.world.getBlockState(EVAPORATION_CHANNEL_POS))
                && !isEvaporating(channel)
                && !isCrossEvaporating(channel)
                && !isEvaporating(evaporationChannel)
                && !isCrossEvaporating(evaporationChannel)
                && observeLeverSupport(client.world).matchesExpected();
    }

    private boolean hasCaptureFixtureBlocks(MinecraftClient client, Stage captureStage) {
        BlockState channelState = client.world == null
                ? Blocks.AIR.getDefaultState()
                : client.world.getBlockState(CHANNEL_POS);
        BlockState evaporationState = client.world == null
                ? Blocks.AIR.getDefaultState()
                : client.world.getBlockState(EVAPORATION_CHANNEL_POS);
        if (!hasFixtureBlocks(client)
                || clientBlockEntity(client, SOURCE_STORAGE_POS, STORAGE_BLOCK_ENTITY_ID) == null
                || clientBlockEntity(client, TARGET_STORAGE_POS, STORAGE_BLOCK_ENTITY_ID) == null
                || !Direction.EAST.equals(stateProperty(channelState, FACING_PROPERTY))
                || !"out".equals(statePropertyName(channelState, EAST_CONNECTION_PROPERTY))
                || !"in".equals(statePropertyName(channelState, UP_CONNECTION_PROPERTY))
                || !Direction.EAST.equals(stateProperty(evaporationState, FACING_PROPERTY))
                || !"out".equals(statePropertyName(
                    evaporationState,
                    EAST_CONNECTION_PROPERTY
                ))
                || !client.world.getBlockState(CHANNEL_FEEDER_POS)
                        .isOf(Blocks.REDSTONE_BLOCK)
                || !client.world.getBlockState(EVAPORATION_FEEDER_POS)
                        .isOf(Blocks.REDSTONE_BLOCK)
                || !observeLeverSupport(client.world).matchesExpected()) {
            return false;
        }
        return switch (captureStage) {
            case CAPTURING_GATED -> hasPoweredRepeater(client.world, CHANNEL_POWER_POS)
                    && hasPoweredRepeater(client.world, EVAPORATION_POWER_POS);
            case CAPTURING_TRANSFERRED -> client.world.getBlockState(CHANNEL_POWER_POS)
                        .isAir()
                    && hasPoweredRepeater(client.world, EVAPORATION_POWER_POS);
            case CAPTURING_REOPENED -> client.world.getBlockState(CHANNEL_POWER_POS)
                        .isAir()
                    && hasPoweredRepeater(client.world, EVAPORATION_POWER_POS);
            default -> throw new IllegalArgumentException(
                    "Not a channel screenshot stage: " + captureStage
            );
        };
    }

    private boolean hasFixtureBlocks(MinecraftClient client) {
        return isWorldReady(client)
                && client.world.getChunkManager().isChunkLoaded(0, 0)
                && STORAGE_BLOCK_ID.equals(blockId(client, SOURCE_STORAGE_POS))
                && CHANNEL_BLOCK_ID.equals(blockId(client, CHANNEL_POS))
                && STORAGE_BLOCK_ID.equals(blockId(client, TARGET_STORAGE_POS))
                && CHANNEL_BLOCK_ID.equals(blockId(client, EVAPORATION_CHANNEL_POS))
                && CHANNEL_BLOCK_ID.equals(blockId(client, LEVER_SUPPORT_CHANNEL_POS))
                && Blocks.LEVER == client.world.getBlockState(LEVER_POS).getBlock();
    }

    private boolean hasExpectedFramebuffer(MinecraftClient client) {
        return client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT;
    }

    private boolean hasExpectedCameraPose(MinecraftClient client) {
        if (client.player == null
                || client.getCameraEntity() != client.player
                || !client.options.getPerspective().isFirstPerson()
                || !client.player.isOnGround()) {
            return false;
        }
        return Math.abs(client.player.getX() - (CAMERA_POS.getX() + 0.5))
                        <= CAMERA_POSE_TOLERANCE
                && Math.abs(client.player.getY() - (ARENA_FLOOR_Y + 1.0))
                        <= CAMERA_POSE_TOLERANCE
                && Math.abs(client.player.getZ() - (CAMERA_POS.getZ() + 0.5))
                        <= CAMERA_POSE_TOLERANCE
                && Math.abs(MathHelper.wrapDegrees(client.player.getYaw()))
                        <= CAMERA_POSE_TOLERANCE
                && Math.abs(client.player.getPitch() - 10.0f)
                        <= CAMERA_POSE_TOLERANCE;
    }

    private static void placePrePoweredChannel(
            ServerWorld world,
            BlockPos channelPos,
            BlockPos powerPos,
            BlockPos feederPos,
            BlockState channelState
    ) {
        world.setBlockState(
                channelPos,
                Blocks.SMOOTH_STONE.getDefaultState(),
                Block.NOTIFY_ALL
        );
        world.setBlockState(
                feederPos,
                Blocks.REDSTONE_BLOCK.getDefaultState(),
                Block.NOTIFY_ALL
        );
        world.setBlockState(
                powerPos,
                poweredRepeaterState(),
                Block.NOTIFY_ALL
        );
        if (!world.getBlockState(feederPos).isOf(Blocks.REDSTONE_BLOCK)
                || !hasPoweredRepeater(world, powerPos)
                || world.getReceivedStrongRedstonePower(channelPos) != 15) {
            throw new IllegalStateException(
                    "The temporary support did not receive exact repeater strong power"
            );
        }
        world.setBlockState(channelPos, channelState, Block.NOTIFY_ALL);
    }

    private static BlockState poweredRepeaterState() {
        return Blocks.REPEATER.getDefaultState()
                .with(RepeaterBlock.FACING, Direction.WEST)
                .with(RepeaterBlock.POWERED, true);
    }

    private static BlockState attachedLeverState() {
        return Blocks.LEVER.getDefaultState()
                .with(LeverBlock.FACE, WallMountLocation.WALL)
                .with(LeverBlock.FACING, Direction.NORTH)
                .with(LeverBlock.POWERED, false);
    }

    private static boolean hasPoweredRepeater(WorldView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.REPEATER)
                && Direction.WEST.equals(state.get(RepeaterBlock.FACING))
                && state.get(RepeaterBlock.POWERED);
    }

    static boolean hasDistinctFixturePositions() {
        return Set.copyOf(FIXTURE_POSITIONS).size() == FIXTURE_POSITIONS.size();
    }

    private static GameRules controlledGameRules() {
        GameRules gameRules = new GameRules();
        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        gameRules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        gameRules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        gameRules.get(GameRules.KEEP_INVENTORY).set(true, null);
        gameRules.get(GameRules.DO_IMMEDIATE_RESPAWN).set(true, null);
        return gameRules;
    }

    private static void configureWorld(IntegratedServer server, ServerWorld world) {
        world.setTimeOfDay(6000L);
        world.setWeather(6000, 0, false, false);
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
    }

    private static void buildArena(ServerWorld world) {
        BlockPos start = new BlockPos(-8, ARENA_FLOOR_Y, -8);
        BlockPos end = new BlockPos(10, ARENA_FLOOR_Y + 8, 8);
        for (BlockPos pos : BlockPos.iterate(start, end)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        for (int x = -8; x <= 10; x++) {
            for (int z = -8; z <= 8; z++) {
                Block floor = (x + z) % 7 == 0
                        ? Blocks.SEA_LANTERN
                        : Blocks.SMOOTH_STONE;
                world.setBlockState(
                        new BlockPos(x, ARENA_FLOOR_Y, z),
                        floor.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
        }
    }

    private static ServerPlayerEntity requireServerPlayer(
            IntegratedServer server,
            UUID playerId
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("The integrated server has no matching player");
        }
        return player;
    }

    private static BlockEntity requireStorage(
            ServerWorld world,
            BlockPos pos
    ) {
        return requireBlockEntity(world, pos, STORAGE_BLOCK_ENTITY_ID, "storage");
    }

    private static BlockEntity requireChannel(
            ServerWorld world,
            BlockPos pos
    ) {
        return requireBlockEntity(world, pos, CHANNEL_BLOCK_ENTITY_ID, "channel");
    }

    private static BlockEntity requireBlockEntity(
            ServerWorld world,
            BlockPos pos,
            Identifier expectedId,
            String label
    ) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity == null) {
            throw new IllegalStateException(
                    "The " + label + " fixture block entity is missing at " + pos
            );
        }
        Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
        if (!expectedId.equals(id)) {
            throw new IllegalStateException(
                    "Unexpected " + label + " block entity id: " + id
            );
        }
        return blockEntity;
    }

    private static BlockEntity clientBlockEntity(
            MinecraftClient client,
            BlockPos pos,
            Identifier expectedId
    ) {
        if (!isClientWorldReady(client)) {
            return null;
        }
        BlockEntity blockEntity = client.world.getBlockEntity(pos);
        if (blockEntity == null
                || !expectedId.equals(
                    Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType())
                )) {
            return null;
        }
        return blockEntity;
    }

    private static void setStorageEther(BlockEntity blockEntity, float ether) {
        NbtCompound nbt = blockEntity.createNbt();
        nbt.putFloat(STORAGE_ETHER_KEY, ether);
        blockEntity.readNbt(nbt);
        blockEntity.markDirty();
    }

    private static void setChannelState(
            BlockEntity blockEntity,
            float ether,
            boolean evaporating,
            boolean crossEvaporating
    ) {
        NbtCompound nbt = blockEntity.createNbt();
        nbt.putFloat(STORED_ETHER_KEY, ether);
        nbt.putBoolean(EVAPORATING_KEY, evaporating);
        nbt.putBoolean(CROSS_EVAPORATING_KEY, crossEvaporating);
        blockEntity.readNbt(nbt);
        blockEntity.markDirty();
    }

    private static float storageEther(BlockEntity blockEntity) {
        return blockEntity.createNbt().getFloat(STORAGE_ETHER_KEY);
    }

    private static float channelEther(BlockEntity blockEntity) {
        return blockEntity.createNbt().getFloat(STORED_ETHER_KEY);
    }

    private static boolean isEvaporating(BlockEntity blockEntity) {
        return blockEntity.createNbt().getBoolean(EVAPORATING_KEY);
    }

    private static boolean isCrossEvaporating(BlockEntity blockEntity) {
        return blockEntity.createNbt().getBoolean(CROSS_EVAPORATING_KEY);
    }

    private static boolean isActivated(BlockState state) {
        return Boolean.TRUE.equals(stateProperty(state, ACTIVATED_PROPERTY));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withStateProperty(
            BlockState state,
            String propertyName,
            Comparable value
    ) {
        Property property = state.getBlock().getStateManager().getProperty(propertyName);
        if (property == null || !property.getValues().contains(value)) {
            throw new IllegalStateException(
                    "Missing or incompatible block-state property: " + propertyName
            );
        }
        return state.with(property, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> stateProperty(BlockState state, String propertyName) {
        Property property = state.getBlock().getStateManager().getProperty(propertyName);
        if (property == null) {
            throw new IllegalStateException(
                    "Missing block-state property: " + propertyName
            );
        }
        return state.get(property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String statePropertyName(BlockState state, String propertyName) {
        Property property = state.getBlock().getStateManager().getProperty(propertyName);
        if (property == null) {
            throw new IllegalStateException(
                    "Missing block-state property: " + propertyName
            );
        }
        return property.name(state.get(property));
    }

    private static LeverSupportResult observeLeverSupport(WorldView world) {
        BlockState leverState = world.getBlockState(LEVER_POS);
        BlockState channelState = world.getBlockState(LEVER_SUPPORT_CHANNEL_POS);
        BlockEntity channelBlockEntity = world.getBlockEntity(LEVER_SUPPORT_CHANNEL_POS);
        String leverBlockId = Registries.BLOCK.getId(leverState.getBlock()).toString();
        String channelBlockId = Registries.BLOCK.getId(channelState.getBlock()).toString();
        boolean survived = leverState.isOf(Blocks.LEVER);
        boolean channelPresent = CHANNEL_BLOCK_ID.toString().equals(channelBlockId);

        return new LeverSupportResult(
                leverBlockId,
                survived,
                survived && leverState.canPlaceAt(world, LEVER_POS),
                survived ? statePropertyName(leverState, LeverBlock.FACE.getName()) : "",
                survived ? statePropertyName(leverState, LeverBlock.FACING.getName()) : "",
                channelBlockId,
                channelBlockEntity == null ? "" : blockEntityId(channelBlockEntity),
                channelPresent
                        ? statePropertyName(channelState, FACING_PROPERTY)
                        : "",
                channelPresent
                        ? statePropertyName(channelState, NORTH_CONNECTION_PROPERTY)
                        : "",
                channelPresent
                        ? statePropertyName(channelState, EAST_CONNECTION_PROPERTY)
                        : "",
                channelPresent
                        ? statePropertyName(channelState, WEST_CONNECTION_PROPERTY)
                        : "",
                channelPresent
                        && Boolean.TRUE.equals(
                            stateProperty(channelState, IS_CROSS_PROPERTY)
                        )
        );
    }

    private static boolean isClientWorldReady(MinecraftClient client) {
        return client.world != null && client.player != null;
    }

    private static String blockId(ServerWorld world, BlockPos pos) {
        return Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
    }

    private static Identifier blockId(MinecraftClient client, BlockPos pos) {
        return Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock());
    }

    private static String blockEntityId(BlockEntity blockEntity) {
        return Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()).toString();
    }

    private static boolean sameEther(float left, float right) {
        return Math.abs(left - right) <= FLOAT_TOLERANCE;
    }

    private static boolean sameNetworkDistribution(
            ChannelSnapshot left,
            ChannelSnapshot right
    ) {
        return sameEther(left.sourceEther(), right.sourceEther())
                && sameEther(left.channelEther(), right.channelEther())
                && sameEther(left.targetEther(), right.targetEther())
                && sameEther(left.networkTotal(), right.networkTotal());
    }

    private static boolean samePersistentState(
            ChannelSnapshot saved,
            ChannelSnapshot reloaded
    ) {
        return sameEther(saved.sourceEther(), reloaded.sourceEther())
                && sameEther(saved.channelEther(), reloaded.channelEther())
                && sameEther(saved.targetEther(), reloaded.targetEther())
                && sameEther(saved.evaporationEther(), reloaded.evaporationEther())
                && saved.channelActivated() == reloaded.channelActivated()
                && saved.evaporationActivated() == reloaded.evaporationActivated()
                && saved.evaporating() == reloaded.evaporating()
                && saved.crossEvaporating() == reloaded.crossEvaporating()
                && saved.sourceBlockId().equals(reloaded.sourceBlockId())
                && saved.sourceBlockEntityId().equals(reloaded.sourceBlockEntityId())
                && saved.channelBlockId().equals(reloaded.channelBlockId())
                && saved.channelBlockEntityId().equals(reloaded.channelBlockEntityId())
                && saved.targetBlockId().equals(reloaded.targetBlockId())
                && saved.targetBlockEntityId().equals(reloaded.targetBlockEntityId())
                && saved.evaporationBlockId().equals(reloaded.evaporationBlockId())
                && saved.evaporationBlockEntityId().equals(
                    reloaded.evaporationBlockEntityId()
                )
                && saved.leverSupport().equals(reloaded.leverSupport());
    }

    private static boolean hasExactBlockEntityTypes(ChannelSnapshot snapshot) {
        return STORAGE_BLOCK_ENTITY_ID.toString().equals(snapshot.sourceBlockEntityId())
                && CHANNEL_BLOCK_ENTITY_ID.toString().equals(
                    snapshot.channelBlockEntityId()
                )
                && STORAGE_BLOCK_ENTITY_ID.toString().equals(snapshot.targetBlockEntityId())
                && CHANNEL_BLOCK_ENTITY_ID.toString().equals(
                    snapshot.evaporationBlockEntityId()
                )
                && CHANNEL_BLOCK_ENTITY_ID.toString().equals(
                    snapshot.leverSupport().channelBlockEntityId()
                );
    }

    private static Identifier etherologyId(String path) {
        return Identifier.of("etherology", path);
    }

    private static void requireRegistryId(
            Set<Identifier> ids,
            Identifier expected,
            String label
    ) {
        if (!ids.contains(expected)) {
            throw new IllegalStateException("Missing Etherology " + label + ": " + expected);
        }
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_SETUP,
        WAITING_FOR_GATED_TRANSFER,
        WAITING_FOR_GATED_RENDERS,
        CAPTURING_GATED,
        RELEASING_MAIN_POWER,
        WAITING_FOR_TRANSFER,
        WAITING_FOR_NO_REVERSE,
        WAITING_FOR_TRANSFERRED_RENDERS,
        CAPTURING_TRANSFERRED,
        RELEASING_EVAPORATION_POWER,
        WAITING_FOR_EVAPORATION,
        SAVING,
        DISCONNECTING,
        WAITING_FOR_RESTART_TITLE,
        RESTARTING_WORLD,
        WAITING_FOR_RESTART_WORLD,
        WAITING_FOR_RESTART_INSPECTION,
        WAITING_FOR_REOPENED_RENDERS,
        CAPTURING_REOPENED,
        COMPLETE
    }

    private record PlacementResult(
            long placementWorldTime,
            boolean chunkLoaded,
            boolean chunkForced,
            boolean fixturePositionsDistinct
    ) {
    }

    private record PowerMutation(long worldTime) {
    }

    private record SetupResult(
            boolean chunkLoaded,
            boolean chunkForced,
            boolean fixturePositionsDistinct,
            boolean prePoweredPlacement,
            boolean fixtureTopology,
            LeverSupportResult leverSupport,
            boolean nbtReconstructed,
            boolean nbtSyncContract,
            ChannelSnapshot initialSnapshot
    ) {
    }

    private record GatedResult(
            long receivedWorldTime,
            long retainedWorldTime,
            ChannelSnapshot snapshot
    ) {
    }

    private record PowerReleaseResult(long worldTime, boolean deactivated) {
    }

    private record TransferResult(
            long releaseWorldTime,
            long transferWorldTime,
            boolean exactTransfer,
            boolean totalConserved,
            ChannelSnapshot snapshot
    ) {
    }

    private record StableTransferResult(
            long worldTime,
            boolean unchanged,
            ChannelSnapshot snapshot
    ) {
    }

    private record EvaporationResult(
            long releaseWorldTime,
            long evaporationWorldTime,
            float evaporatedEther,
            boolean exactLoss,
            boolean exactFlags,
            ChannelSnapshot observedSnapshot,
            ChannelSnapshot snapshot
    ) {
    }

    private record EvaporationObservation(
            long releaseWorldTime,
            long evaporationWorldTime,
            float evaporatedEther,
            boolean exactLoss,
            boolean exactFlags,
            ChannelSnapshot snapshot
    ) {
    }

    private record ChannelSnapshot(
            long worldTime,
            float sourceEther,
            float channelEther,
            float targetEther,
            float evaporationEther,
            boolean channelActivated,
            boolean evaporationActivated,
            boolean evaporating,
            boolean crossEvaporating,
            String sourceBlockId,
            String sourceBlockEntityId,
            String channelBlockId,
            String channelBlockEntityId,
            String targetBlockId,
            String targetBlockEntityId,
            String evaporationBlockId,
            String evaporationBlockEntityId,
            LeverSupportResult leverSupport
    ) {

        float networkTotal() {
            return sourceEther + channelEther + targetEther;
        }
    }

    private record RestartResult(
            ChannelSnapshot snapshot,
            boolean exactStateRetained,
            boolean blockEntityTypesRetained,
            boolean leverSupportRetained
    ) {
    }

    private record LeverSupportResult(
            String leverBlockId,
            boolean survived,
            boolean canPlace,
            String face,
            String facing,
            String channelBlockId,
            String channelBlockEntityId,
            String channelFacing,
            String northConnection,
            String eastConnection,
            String westConnection,
            boolean cross
    ) {

        boolean matchesExpected() {
            return EXPECTED_LEVER_SUPPORT_EVIDENCE.equals(evidence());
        }

        String evidence() {
            return "lever=" + leverBlockId
                    + ";survived=" + survived
                    + ";can_place=" + canPlace
                    + ";face=" + face
                    + ";facing=" + facing
                    + ";channel=" + channelBlockId
                    + ";channel_block_entity=" + channelBlockEntityId
                    + ";channel_facing=" + channelFacing
                    + ";north=" + northConnection
                    + ";east=" + eastConnection
                    + ";west=" + westConnection
                    + ";cross=" + cross;
        }
    }

    private record ScreenshotResult(
            boolean passed,
            long size,
            String sha256,
            String failure
    ) {

        static ScreenshotResult failed(String failure) {
            return new ScreenshotResult(false, 0L, "", failure);
        }
    }
}
