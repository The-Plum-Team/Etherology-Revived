package dev.theplumteam.etherology.e2e.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
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

final class EtherNetworkScenario implements ClientScenario {

    static final String SCENARIO_ID = "ether-network";
    static final String WORLD_DIRECTORY_NAME = "etherology-e2e-ether-network-world";
    static final String BEFORE_SCREENSHOT_FILE_NAME = "ether-network-before.png";
    static final String AFTER_SCREENSHOT_FILE_NAME = "ether-network-after.png";
    static final String FORCE_PROBE_ENTITY_NAME = "Ether-network load";
    static final String FORCE_PROBE_ENTITY_UUID_STRING =
            "c3c4a834-54dc-4d2e-93c3-a69755fb942d";

    private static final Logger LOGGER = LoggerFactory.getLogger("EtherologyE2EHarness");
    private static final UUID FORCE_PROBE_ENTITY_UUID = UUID.fromString(
            FORCE_PROBE_ENTITY_UUID_STRING
    );
    private static final String WORLD_DISPLAY_NAME = "Etherology E2E Ether Network";
    private static final long WORLD_SEED = 0x45544845524cL;
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int REQUIRED_COMPLETED_RENDERS = 2;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int FIRST_UNIT_LOSS_GRACE_SERVER_TICKS = 40;
    private static final int MAXIMUM_NETWORK_DIAGNOSTIC_HISTORY = 24;
    private static final int ARENA_FLOOR_Y = 120;
    private static final int EXPECTED_GENERATOR_CYCLES = 4;
    private static final double MINIMUM_ARMOR_STAND_DISPLACEMENT = 0.75;
    private static final float ETHER_TOLERANCE = 0.0001f;
    private static final Identifier ETHEROLOGY_TITLE_RESOURCE =
            new Identifier("etherology", "models/item/oculus.json");
    private static final BlockPos CAMERA_BLOCK_POS = new BlockPos(0, ARENA_FLOOR_Y + 1, -9);
    private static final BlockPos REDSTONE_GATE_POS = new BlockPos(0, ARENA_FLOOR_Y + 2, 3);
    private static final BlockPos FORCE_BARRIER_POS = new BlockPos(1, ARENA_FLOOR_Y + 2, 2);
    private static final int FORCE_TRACK_START_X = 1;
    private static final int FORCE_TRACK_END_X = 10;
    private static final int FORCE_TRACK_SUPPORT_Y = ARENA_FLOOR_Y + 1;
    private static final int FORCE_TRACK_Z = 2;
    private static final Vec3d ARMOR_STAND_INITIAL_POS = new Vec3d(
            2.5,
            ARENA_FLOOR_Y + 2,
            2.5
    );
    private static final FixtureBlock GENERATOR = fixture(
            "spinner",
            "spinner",
            "spinner_block_entity",
            -2,
            ARENA_FLOOR_Y + 4,
            2
    );
    private static final FixtureBlock SOURCE_CHANNEL = fixture(
            "source_channel",
            "ethereal_channel",
            "ethereal_channel_block_entity",
            -1,
            ARENA_FLOOR_Y + 4,
            2
    );
    private static final FixtureBlock STORAGE = fixture(
            "storage",
            "ethereal_storage",
            "ethereal_storage_block_entity",
            0,
            ARENA_FLOOR_Y + 4,
            2
    );
    private static final FixtureBlock OUTPUT_CHANNEL = fixture(
            "output_channel",
            "ethereal_channel",
            "ethereal_channel_block_entity",
            0,
            ARENA_FLOOR_Y + 3,
            2
    );
    private static final FixtureBlock LEVITATOR = fixture(
            "levitator",
            "levitator",
            "levitator_block_entity",
            0,
            ARENA_FLOOR_Y + 2,
            2
    );
    private static final List<FixtureBlock> INITIAL_FIXTURE_BLOCKS = List.of(
            GENERATOR,
            SOURCE_CHANNEL,
            STORAGE,
            LEVITATOR
    );
    private static final List<FixtureBlock> FINAL_FIXTURE_BLOCKS = List.of(
            GENERATOR,
            SOURCE_CHANNEL,
            STORAGE,
            OUTPUT_CHANNEL,
            LEVITATOR
    );
    private static final List<ChunkPos> FIXTURE_CHUNKS = List.of(
            new ChunkPos(GENERATOR.pos()),
            new ChunkPos(STORAGE.pos())
    );
    private static final RegistryExpectation[] REGISTRY_EXPECTATIONS = {
            block("spinner"),
            block("ethereal_channel"),
            block("ethereal_storage"),
            block("levitator"),
            blockEntityType("spinner_block_entity"),
            blockEntityType("ethereal_channel_block_entity"),
            blockEntityType("ethereal_storage_block_entity"),
            blockEntityType("levitator_block_entity")
    };

    private Stage stage = Stage.WAITING_FOR_TITLE;
    private int clientTicks;
    private int stageClientTicks;
    private int completedBeforeRenders;
    private int completedAfterRenders;
    private int beforeFramebufferWidth;
    private int beforeFramebufferHeight;
    private int afterFramebufferWidth;
    private int afterFramebufferHeight;
    private boolean worldSetupSubmitted;
    private boolean activationSubmitted;
    private volatile boolean saveSubmitted;
    private boolean registryPreflightPassed;
    private volatile boolean serverProbeSubmitted;
    private volatile int networkStep;
    private volatile long networkStepStartedWorldTime;
    private volatile long networkStepElapsedTicks;
    private volatile int completedGeneratorCycles;
    private volatile int fuelAtFirstConsumption;
    private volatile double displacementAtFirstConsumption;
    private volatile float maximumObservedOutputChannelEther;
    private volatile float maximumObservedLevitatorEther;
    private volatile int maximumObservedLevitatorFuel;
    private volatile double maximumObservedArmorStandDisplacement;
    private volatile List<String> networkDiagnosticHistory = List.of();
    private String lastNetworkDiagnosticPhase = "";
    private String lifecycleFailure = "";
    private String clientProgressDiagnostic = "client mirror not inspected";
    private EvidenceLayout evidenceLayout;
    private ClientNetworkSnapshot beforeClientSnapshot;
    private ClientNetworkSnapshot afterClientSnapshot;
    private volatile ScreenshotResult beforeScreenshotResult;
    private volatile ScreenshotResult afterScreenshotResult;
    private volatile InitialSetupResult initialSetupResult;
    private volatile String initialSetupFailure = "";
    private volatile SourceChargeResult sourceChargeResult;
    private volatile String networkFailure = "";
    private volatile String serverProgressDiagnostic = "server fixture not arranged";
    private volatile NetworkResult networkResult;
    private volatile Boolean saveResult;
    private volatile PostSaveResult postSaveResult;
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
                case WAITING_FOR_INITIAL_SETUP -> tickWaitingForInitialSetup(client);
                case WAITING_FOR_SOURCE_CHARGE -> tickWaitingForSourceCharge(client);
                case WAITING_FOR_BEFORE_CLIENT_MIRROR -> tickWaitingForBeforeClientMirror(client);
                case WAITING_FOR_BEFORE_RENDERS -> tickWaitingForBeforeRenders(client);
                case CAPTURING_BEFORE -> tickCapturingBefore(client);
                case WAITING_FOR_NETWORK -> tickWaitingForNetwork(client);
                case WAITING_FOR_AFTER_CLIENT_MIRROR -> tickWaitingForAfterClientMirror(client);
                case WAITING_FOR_AFTER_RENDERS -> tickWaitingForAfterRenders(client);
                case CAPTURING_AFTER -> tickCapturingAfter(client);
                case SAVING_WORLD -> tickSavingWorld(client);
                case COMPLETE -> {
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Etherology ether-network failed while in {}", stage, exception);
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
                    "Timed out in " + stage + " after " + stageClientTicks
                            + " client ticks; server=" + serverProgressDiagnostic
                            + "; client=" + clientProgressDiagnostic
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
        if (stage == Stage.WAITING_FOR_BEFORE_RENDERS) {
            if (!isWorldViewReady(client, beforeClientSnapshot)) {
                completedBeforeRenders = 0;
                return;
            }

            completedBeforeRenders++;
            if (completedBeforeRenders >= REQUIRED_COMPLETED_RENDERS) {
                captureBefore(client);
            }
            return;
        }

        if (stage != Stage.WAITING_FOR_AFTER_RENDERS) return;
        if (!isWorldViewReady(client, afterClientSnapshot)) {
            completedAfterRenders = 0;
            return;
        }

        completedAfterRenders++;
        if (completedAfterRenders >= REQUIRED_COMPLETED_RENDERS) {
            captureAfter(client);
        }
    }

    private void tickWaitingForTitle(MinecraftClient client) {
        if (client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) return;
        if (!hasExpectedFramebuffer(client)) return;
        if (client.getResourceManager().getResource(ETHEROLOGY_TITLE_RESOURCE).isEmpty()) {
            fail(client, "The loaded resources do not include Etherology");
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
        for (FixtureBlock fixtureBlock : FINAL_FIXTURE_BLOCKS) {
            Block block = Registries.BLOCK.get(fixtureBlock.blockId());
            for (BlockState state : block.getStateManager().getStates()) {
                if (Block.STATE_IDS.getRawId(state) < 0) {
                    fail(
                            client,
                            "Fixture block state has no network id: " + fixtureBlock.blockId()
                                    + "=" + state
                    );
                    return;
                }
            }
        }
        registryPreflightPassed = true;

        try {
            ensureEvidenceLayout(client);
        } catch (IOException exception) {
            LOGGER.error("Cannot use the isolated ether-network evidence layout", exception);
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
            fail(client, "Refusing to reuse the ether-network save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "The client already has a world before ether-network creation");
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
                WorldPresets::createDemoOptions
        );
        transition(Stage.WAITING_FOR_WORLD);
    }

    private void tickWaitingForWorld(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (!isWorldLifecycleReady(client)
                || server == null
                || server.getPlayerManager().getPlayer(client.player.getUuid()) == null) {
            return;
        }
        if (worldSetupSubmitted) return;

        worldSetupSubmitted = true;
        UUID playerId = client.player.getUuid();
        server.execute(() -> setupInitialFixture(server, playerId));
        transition(Stage.WAITING_FOR_INITIAL_SETUP);
    }

    private void tickWaitingForInitialSetup(MinecraftClient client) {
        if (!initialSetupFailure.isEmpty()) {
            fail(client, "Initial ether-network fixture setup failed: " + initialSetupFailure);
            return;
        }
        if (initialSetupResult == null) return;

        transition(Stage.WAITING_FOR_SOURCE_CHARGE);
    }

    private void tickWaitingForSourceCharge(MinecraftClient client) {
        if (!networkFailure.isEmpty()) {
            fail(client, "Ether-network server progression failed: " + networkFailure);
            return;
        }
        if (sourceChargeResult != null) {
            transition(Stage.WAITING_FOR_BEFORE_CLIENT_MIRROR);
            return;
        }

        submitSourceChargeProbe(client);
    }

    private void tickWaitingForBeforeClientMirror(MinecraftClient client) {
        InitialSetupResult setup = initialSetupResult;
        if (setup == null) return;

        ClientNetworkSnapshot snapshot = captureClientNetwork(
                client,
                setup.armorStandUuid()
        );
        if (snapshot == null || !snapshot.matchesChargedStorageState()) return;

        beforeClientSnapshot = snapshot;
        transition(Stage.WAITING_FOR_BEFORE_RENDERS);
    }

    private void tickWaitingForBeforeRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before the charged capture");
        }
    }

    private void tickCapturingBefore(MinecraftClient client) {
        ScreenshotResult result = beforeScreenshotResult;
        if (result == null) return;
        if (!result.passed()) {
            fail(client, "The charged ether-network screenshot failed: " + result.failure());
            return;
        }

        submitNetworkActivation(client);
    }

    private void tickWaitingForNetwork(MinecraftClient client) {
        InitialSetupResult setup = initialSetupResult;
        if (setup != null) {
            captureClientNetwork(client, setup.armorStandUuid());
        }
        if (!networkFailure.isEmpty()) {
            fail(client, "Ether-network server progression failed: " + networkFailure);
            return;
        }
        if (networkResult != null) {
            transition(Stage.WAITING_FOR_AFTER_CLIENT_MIRROR);
            return;
        }

        submitNetworkProbe(client);
    }

    private void tickWaitingForAfterClientMirror(MinecraftClient client) {
        InitialSetupResult setup = initialSetupResult;
        if (setup == null) return;

        ClientNetworkSnapshot snapshot = captureClientNetwork(
                client,
                setup.armorStandUuid()
        );
        if (snapshot == null || !snapshot.matchesRetainedNetworkState()) return;

        afterClientSnapshot = snapshot;
        transition(Stage.WAITING_FOR_AFTER_RENDERS);
    }

    private void tickWaitingForAfterRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before the network capture");
        }
    }

    private void tickCapturingAfter(MinecraftClient client) {
        ScreenshotResult result = afterScreenshotResult;
        if (result == null) return;
        if (!result.passed()) {
            fail(client, "The completed ether-network screenshot failed: " + result.failure());
            return;
        }

        submitSave(client);
    }

    private void tickSavingWorld(MinecraftClient client) {
        if (!saveFailure.isEmpty()) {
            fail(client, "The integrated-world save failed: " + saveFailure);
            return;
        }
        if (!saveSubmitted) {
            submitSave(client);
            return;
        }
        if (saveResult == null || postSaveResult == null) return;

        publish(client);
    }

    private void setupInitialFixture(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = requireServerPlayer(server, playerId);
            boolean fixtureChunksForced = forceFixtureChunks(world);
            boolean fixtureChunksLoaded = loadFixtureChunks(world);
            serverProgressDiagnostic = "chunk setup: forced=" + fixtureChunksForced
                    + ", loaded=" + fixtureChunksLoaded
                    + ", chunks=" + FIXTURE_CHUNKS;
            if (!fixtureChunksForced || !fixtureChunksLoaded) {
                throw new IllegalStateException(
                        "The complete ether-network fixture is not force-loaded: "
                                + serverProgressDiagnostic
                );
            }

            world.setTimeOfDay(6000L);
            world.setWeather(6000, 0, false, false);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);

            clearArena(world);
            buildArena(world);
            world.setBlockState(
                    REDSTONE_GATE_POS,
                    Blocks.REDSTONE_BLOCK.getDefaultState(),
                    Block.NOTIFY_ALL
            );
            placeInitialFixtureBlocks(world);

            ArmorStandEntity armorStand = new ArmorStandEntity(
                    world,
                    ARMOR_STAND_INITIAL_POS.x,
                    ARMOR_STAND_INITIAL_POS.y,
                    ARMOR_STAND_INITIAL_POS.z
            );
            armorStand.setUuid(FORCE_PROBE_ENTITY_UUID);
            armorStand.setCustomName(Text.literal(FORCE_PROBE_ENTITY_NAME));
            armorStand.setCustomNameVisible(true);
            armorStand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            armorStand.equipStack(
                    EquipmentSlot.CHEST,
                    new ItemStack(Items.DIAMOND_CHESTPLATE)
            );
            boolean armorStandSpawned = world.spawnEntity(armorStand);
            if (!armorStandSpawned) {
                throw new IllegalStateException(
                        "The server rejected the fixture armor stand with UUID "
                                + FORCE_PROBE_ENTITY_UUID
                );
            }
            ArmorStandEntity spawnedArmorStand = requireFixtureArmorStand(
                    armorStand,
                    FORCE_PROBE_ENTITY_UUID
            );

            List<String> placedBlockIds = new ArrayList<>();
            List<String> blockEntityIds = new ArrayList<>();
            boolean allBlocksPlaced = true;
            boolean allBlockEntitiesPresent = true;
            for (FixtureBlock fixtureBlock : INITIAL_FIXTURE_BLOCKS) {
                Identifier actualBlockId = Registries.BLOCK.getId(
                        world.getBlockState(fixtureBlock.pos()).getBlock()
                );
                placedBlockIds.add(fixtureBlock.pos().toShortString() + "=" + actualBlockId);
                allBlocksPlaced &= fixtureBlock.blockId().equals(actualBlockId);

                BlockEntity blockEntity = world.getBlockEntity(fixtureBlock.pos());
                if (blockEntity == null) {
                    allBlockEntitiesPresent = false;
                    blockEntityIds.add(fixtureBlock.pos().toShortString() + "=missing");
                    continue;
                }

                Identifier actualBlockEntityId = Registries.BLOCK_ENTITY_TYPE.getId(
                        blockEntity.getType()
                );
                blockEntityIds.add(fixtureBlock.pos().toShortString() + "=" + actualBlockEntityId);
                allBlockEntitiesPresent &= fixtureBlock.blockEntityTypeId()
                        .equals(actualBlockEntityId);
            }

            NetworkState initialState = captureNetworkState(world, spawnedArmorStand);
            boolean outputPathAbsent = world.getBlockState(OUTPUT_CHANNEL.pos()).isAir()
                    && world.getBlockEntity(OUTPUT_CHANNEL.pos()) == null;

            player.getInventory().clear();
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
            boolean playerCreative = player.changeGameMode(GameMode.CREATIVE) || player.isCreative();
            player.teleport(
                    world,
                    CAMERA_BLOCK_POS.getX() + 0.5,
                    CAMERA_BLOCK_POS.getY(),
                    CAMERA_BLOCK_POS.getZ() + 0.5,
                    0.0f,
                    -10.0f
            );
            player.setSpawnPoint(World.OVERWORLD, CAMERA_BLOCK_POS, 0.0f, true, false);

            primeGenerator(world);
            completedGeneratorCycles = 1;
            markFixtureForUpdate(world, INITIAL_FIXTURE_BLOCKS);
            serverProgressDiagnostic = "initial setup: forced_chunks=" + fixtureChunksForced
                    + ", loaded_chunks=" + fixtureChunksLoaded
                    + ", " + initialState.summary();
            initialSetupResult = new InitialSetupResult(
                    fixtureChunksLoaded,
                    fixtureChunksForced,
                    playerCreative,
                    allBlocksPlaced,
                    allBlockEntitiesPresent,
                    outputPathAbsent,
                    armorStandSpawned,
                    spawnedArmorStand.getUuid(),
                    initialState,
                    List.copyOf(placedBlockIds),
                    List.copyOf(blockEntityIds)
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot arrange the ether-network initial fixture", exception);
            initialSetupFailure = describe(exception);
            serverProgressDiagnostic = "initial setup error: " + initialSetupFailure;
        }
    }

    private void placeInitialFixtureBlocks(ServerWorld world) {
        Block generatorBlock = Registries.BLOCK.get(GENERATOR.blockId());
        BlockState generatorState = withStateProperty(
                generatorBlock.getDefaultState(),
                "facing",
                "west"
        );
        generatorState = withStateProperty(generatorState, "stalled", "false");
        world.setBlockState(GENERATOR.pos(), generatorState, Block.NOTIFY_ALL);

        Block sourceChannelBlock = Registries.BLOCK.get(SOURCE_CHANNEL.blockId());
        world.setBlockState(
                SOURCE_CHANNEL.pos(),
                channelState(sourceChannelBlock, "east", "west", "east"),
                Block.NOTIFY_ALL
        );

        Block storageBlock = Registries.BLOCK.get(STORAGE.blockId());
        world.setBlockState(
                STORAGE.pos(),
                withStateProperty(storageBlock.getDefaultState(), "facing", "north"),
                Block.NOTIFY_ALL
        );

        Block levitatorBlock = Registries.BLOCK.get(LEVITATOR.blockId());
        BlockState levitatorState = levitatorBlock.getDefaultState();
        levitatorState = withStateProperty(levitatorState, "facing", "west");
        levitatorState = withStateProperty(levitatorState, "pushing", "true");
        levitatorState = withStateProperty(levitatorState, "with_fuel", "false");
        levitatorState = withStateProperty(levitatorState, "power", "15");
        levitatorState = withStateProperty(levitatorState, "powered", "true");
        world.setBlockState(LEVITATOR.pos(), levitatorState, Block.NOTIFY_ALL);

        world.setBlockState(OUTPUT_CHANNEL.pos(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(FORCE_BARRIER_POS, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(
                SOURCE_CHANNEL.pos(),
                channelState(sourceChannelBlock, "east", "west", "east"),
                Block.NOTIFY_ALL
        );
    }

    private void submitSourceChargeProbe(MinecraftClient client) {
        if (serverProbeSubmitted) return;

        IntegratedServer server = requireRunningServer(client);
        InitialSetupResult setup = initialSetupResult;
        if (setup == null) return;

        serverProbeSubmitted = true;
        server.execute(() -> {
            try {
                ServerWorld world = server.getOverworld();
                ArmorStandEntity armorStand = findServerArmorStand(
                        world,
                        setup.armorStandUuid()
                );
                if (armorStand == null) {
                    serverProgressDiagnostic = "source charge: force-probe UUID "
                            + setup.armorStandUuid() + " is not currently loaded";
                    return;
                }

                NetworkState state = captureNetworkState(world, armorStand);
                int nextGenerationTime = requireBlockEntity(world, GENERATOR)
                        .createNbtWithId()
                        .getInt("next_gen_time");
                serverProgressDiagnostic = "source charge: cycles=" + completedGeneratorCycles
                        + ", next_gen_time=" + nextGenerationTime
                        + ", " + state.summary();
                if (!state.matchesChargedStorageState()) return;

                boolean generatorPaused = pauseGenerator(world);
                if (!generatorPaused) {
                    throw new IllegalStateException(
                            "The Spinner did not enter its canonical stalled state"
                    );
                }
                markFixtureForUpdate(world, INITIAL_FIXTURE_BLOCKS);
                serverProgressDiagnostic = "source charged and generator paused: cycles="
                        + completedGeneratorCycles
                        + ", next_gen_time=" + nextGenerationTime
                        + ", " + state.summary();
                sourceChargeResult = new SourceChargeResult(
                        state,
                        nextGenerationTime,
                        completedGeneratorCycles,
                        generatorPaused
                );
            } catch (RuntimeException exception) {
                LOGGER.error("Cannot inspect the charged ether storage", exception);
                networkFailure = describe(exception);
                serverProgressDiagnostic = "source charge error: " + networkFailure;
            } finally {
                serverProbeSubmitted = false;
            }
        });
    }

    private void submitNetworkActivation(MinecraftClient client) {
        if (activationSubmitted) return;

        IntegratedServer server = requireRunningServer(client);
        activationSubmitted = true;
        server.execute(() -> {
            try {
                ServerWorld world = server.getOverworld();
                Block channelBlock = Registries.BLOCK.get(OUTPUT_CHANNEL.blockId());
                world.setBlockState(
                        OUTPUT_CHANNEL.pos(),
                        channelState(channelBlock, "down", "up", "down"),
                        Block.NOTIFY_ALL
                );
                markFixtureForUpdate(world, FINAL_FIXTURE_BLOCKS);
                resetNetworkDiagnostics(world);
                String activationDiagnostic = "network step=0, step_ticks=0, activation, "
                        + networkFixtureStateSummary(world);
                appendNetworkDiagnostic(activationDiagnostic);
                serverProgressDiagnostic = activationDiagnostic;
            } catch (RuntimeException exception) {
                LOGGER.error("Cannot open the ether-network output path", exception);
                networkFailure = describe(exception);
                serverProgressDiagnostic = "network activation error: " + networkFailure;
            }
        });
        transition(Stage.WAITING_FOR_NETWORK);
    }

    private void submitNetworkProbe(MinecraftClient client) {
        if (serverProbeSubmitted) return;

        IntegratedServer server = requireRunningServer(client);
        InitialSetupResult setup = initialSetupResult;
        if (setup == null) return;

        serverProbeSubmitted = true;
        server.execute(() -> advanceNetwork(server.getOverworld(), setup.armorStandUuid()));
    }

    private void advanceNetwork(ServerWorld world, UUID armorStandUuid) {
        try {
            ArmorStandEntity armorStand = findServerArmorStand(world, armorStandUuid);
            if (armorStand == null) {
                networkStepElapsedTicks = Math.max(
                        0L,
                        world.getTime() - networkStepStartedWorldTime
                );
                serverProgressDiagnostic = "network step " + networkStep
                        + ", step_ticks=" + networkStepElapsedTicks
                        + ": force-probe UUID " + armorStandUuid
                        + " is not currently loaded; "
                        + networkFixtureStateSummary(world) + ", "
                        + networkMaximaSummary() + ", history="
                        + networkDiagnosticHistory;
                return;
            }

            NetworkState state = captureNetworkState(world, armorStand);
            String progressDiagnostic = observeNetworkProgress(world, state);
            serverProgressDiagnostic = progressDiagnostic;
            if (firstUnitIsGoneAfterGrace(state)) {
                networkFailure = "The first ether unit is absent with zero Levitator fuel after "
                        + networkStepElapsedTicks + " server ticks in network step 0; current="
                        + progressDiagnostic + "; history=" + networkDiagnosticHistory;
                serverProgressDiagnostic = "network step 0 fail-fast: " + networkFailure;
                return;
            }
            switch (networkStep) {
                case 0 -> advanceAfterFirstConsumption(world, armorStand, state);
                case 1 -> advanceAfterSecondGeneration(world, state);
                case 2 -> advanceAfterThirdGeneration(world, state);
                case 3 -> completeAfterFourthGeneration(world, state);
                default -> throw new IllegalStateException(
                        "Unexpected ether-network progression step " + networkStep
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot advance the ether-network fixture", exception);
            networkFailure = describe(exception);
            serverProgressDiagnostic = "network step " + networkStep
                    + " error: " + networkFailure + ", "
                    + networkMaximaSummary() + ", history="
                    + networkDiagnosticHistory;
        } finally {
            serverProbeSubmitted = false;
        }
    }

    private void advanceAfterFirstConsumption(
            ServerWorld world,
            ArmorStandEntity armorStand,
            NetworkState state
    ) {
        if (!state.firstUnitConsumed()) return;

        fuelAtFirstConsumption = state.levitatorFuel();
        displacementAtFirstConsumption = state.armorStandDisplacement();
        world.setBlockState(
                REDSTONE_GATE_POS,
                Blocks.AIR.getDefaultState(),
                Block.NOTIFY_ALL
        );
        world.setBlockState(
                FORCE_BARRIER_POS,
                Blocks.BARRIER.getDefaultState(),
                Block.NOTIFY_ALL
        );
        armorStand.setVelocity(Vec3d.ZERO);
        primeGenerator(world);
        completedGeneratorCycles = 2;
        beginNetworkStep(world, 1);
    }

    private void advanceAfterSecondGeneration(ServerWorld world, NetworkState state) {
        if (!state.secondUnitRetainedByLevitator()) return;

        primeGenerator(world);
        completedGeneratorCycles = 3;
        beginNetworkStep(world, 2);
    }

    private void advanceAfterThirdGeneration(ServerWorld world, NetworkState state) {
        if (!state.thirdUnitRetainedByOutputChannel()) return;

        primeGenerator(world);
        completedGeneratorCycles = 4;
        beginNetworkStep(world, 3);
    }

    private void completeAfterFourthGeneration(
            ServerWorld world,
            NetworkState state
    ) {
        if (!state.matchesRetainedNetworkState()) return;

        List<RoundTripResult> roundTrips = List.of(
                roundTripGenerator(world),
                roundTripChannel(world, SOURCE_CHANNEL),
                roundTripStorage(world),
                roundTripChannel(world, OUTPUT_CHANNEL),
                roundTripLevitator(world)
        );
        markFixtureForUpdate(world, FINAL_FIXTURE_BLOCKS);
        networkResult = new NetworkResult(
                state,
                fuelAtFirstConsumption,
                displacementAtFirstConsumption,
                completedGeneratorCycles,
                Registries.BLOCK.getId(world.getBlockState(OUTPUT_CHANNEL.pos()).getBlock()),
                roundTrips
        );
    }

    private void primeGenerator(ServerWorld world) {
        BlockEntity generator = requireBlockEntity(world, GENERATOR);
        NbtCompound generatorNbt = generator.createNbtWithId();
        generatorNbt.putFloat("stored_ether", 0.0f);
        generatorNbt.putInt("next_gen_time", 0);
        generatorNbt.putBoolean("is_mess", false);
        generator.readNbt(generatorNbt);
        generator.markDirty();

        BlockState generatorState = withStateProperty(
                world.getBlockState(GENERATOR.pos()),
                "stalled",
                "false"
        );
        world.setBlockState(GENERATOR.pos(), generatorState, Block.NOTIFY_ALL);
        world.getChunkManager().markForUpdate(GENERATOR.pos());
    }

    private boolean pauseGenerator(ServerWorld world) {
        BlockState generatorState = withStateProperty(
                world.getBlockState(GENERATOR.pos()),
                "stalled",
                "true"
        );
        world.setBlockState(GENERATOR.pos(), generatorState, Block.NOTIFY_ALL);
        world.getChunkManager().markForUpdate(GENERATOR.pos());
        return Boolean.parseBoolean(statePropertyValue(
                world.getBlockState(GENERATOR.pos()),
                "stalled"
        ));
    }

    private NetworkState captureNetworkState(World world, ArmorStandEntity armorStand) {
        NbtCompound generatorNbt = requireBlockEntity(world, GENERATOR).createNbtWithId();
        NbtCompound sourceChannelNbt = requireBlockEntity(world, SOURCE_CHANNEL).createNbtWithId();
        NbtCompound storageNbt = requireBlockEntity(world, STORAGE).createNbtWithId();
        NbtCompound levitatorNbt = requireBlockEntity(world, LEVITATOR).createNbtWithId();
        BlockState outputChannelState = world.getBlockState(OUTPUT_CHANNEL.pos());
        Identifier outputBlockId = Registries.BLOCK.getId(outputChannelState.getBlock());
        boolean outputChannelPresent = OUTPUT_CHANNEL.blockId().equals(outputBlockId);
        float outputChannelEther = outputChannelPresent
                ? requireBlockEntity(world, OUTPUT_CHANNEL)
                        .createNbtWithId()
                        .getFloat("stored_ether")
                : 0.0f;
        BlockState levitatorState = world.getBlockState(LEVITATOR.pos());
        return new NetworkState(
                generatorNbt.getFloat("stored_ether"),
                sourceChannelNbt.getFloat("stored_ether"),
                storageNbt.getFloat("storage_ether"),
                outputChannelEther,
                levitatorNbt.getFloat("stored_ether"),
                levitatorNbt.getInt("fuel"),
                outputChannelPresent,
                outputBlockId,
                outputChannelStateSummary(outputChannelState, outputChannelPresent),
                Boolean.parseBoolean(statePropertyValue(levitatorState, "with_fuel")),
                Integer.parseInt(statePropertyValue(levitatorState, "power")),
                Boolean.parseBoolean(statePropertyValue(levitatorState, "powered")),
                statePropertyValue(levitatorState, "facing"),
                Boolean.parseBoolean(statePropertyValue(levitatorState, "pushing")),
                armorStand.getX(),
                armorStand.getY(),
                armorStand.getZ()
        );
    }

    private String outputChannelStateSummary(
            BlockState outputChannelState,
            boolean outputChannelPresent
    ) {
        if (!outputChannelPresent) return "absent";

        return "facing=" + statePropertyValue(outputChannelState, "facing")
                + ", activated=" + statePropertyValue(outputChannelState, "activated")
                + ", north=" + statePropertyValue(outputChannelState, "north")
                + ", south=" + statePropertyValue(outputChannelState, "south")
                + ", east=" + statePropertyValue(outputChannelState, "east")
                + ", west=" + statePropertyValue(outputChannelState, "west")
                + ", up=" + statePropertyValue(outputChannelState, "up")
                + ", down=" + statePropertyValue(outputChannelState, "down");
    }

    private String networkFixtureStateSummary(World world) {
        BlockState outputState = world.getBlockState(OUTPUT_CHANNEL.pos());
        Identifier outputBlockId = Registries.BLOCK.getId(outputState.getBlock());
        boolean outputPresent = OUTPUT_CHANNEL.blockId().equals(outputBlockId);
        BlockState levitatorState = world.getBlockState(LEVITATOR.pos());
        return "output_block=" + outputBlockId
                + ", output_state={"
                + outputChannelStateSummary(outputState, outputPresent) + "}"
                + ", levitator_facing="
                + statePropertyValue(levitatorState, "facing")
                + ", levitator_pushing="
                + statePropertyValue(levitatorState, "pushing");
    }

    private void resetNetworkDiagnostics(ServerWorld world) {
        maximumObservedOutputChannelEther = 0.0f;
        maximumObservedLevitatorEther = 0.0f;
        maximumObservedLevitatorFuel = 0;
        maximumObservedArmorStandDisplacement = 0.0;
        networkDiagnosticHistory = List.of();
        beginNetworkStep(world, 0);
    }

    private void beginNetworkStep(ServerWorld world, int step) {
        networkStep = step;
        networkStepStartedWorldTime = world.getTime();
        networkStepElapsedTicks = 0;
        lastNetworkDiagnosticPhase = "";
    }

    private String observeNetworkProgress(ServerWorld world, NetworkState state) {
        networkStepElapsedTicks = Math.max(0L, world.getTime() - networkStepStartedWorldTime);
        maximumObservedOutputChannelEther = Math.max(
                maximumObservedOutputChannelEther,
                state.outputChannelEther()
        );
        maximumObservedLevitatorEther = Math.max(
                maximumObservedLevitatorEther,
                state.levitatorEther()
        );
        maximumObservedLevitatorFuel = Math.max(
                maximumObservedLevitatorFuel,
                state.levitatorFuel()
        );
        maximumObservedArmorStandDisplacement = Math.max(
                maximumObservedArmorStandDisplacement,
                state.armorStandDisplacement()
        );

        String diagnostic = "network step=" + networkStep
                + ", step_ticks=" + networkStepElapsedTicks
                + ", cycles=" + completedGeneratorCycles
                + ", " + state.summary()
                + ", " + networkMaximaSummary();
        String phase = networkStep + "|" + state.diagnosticPhase();
        if (!phase.equals(lastNetworkDiagnosticPhase)) {
            appendNetworkDiagnostic(diagnostic);
            lastNetworkDiagnosticPhase = phase;
        }
        return diagnostic;
    }

    private boolean firstUnitIsGoneAfterGrace(NetworkState state) {
        return networkStep == 0
                && networkStepElapsedTicks >= FIRST_UNIT_LOSS_GRACE_SERVER_TICKS
                && approximately(state.totalEther(), 0.0f)
                && state.levitatorFuel() == 0;
    }

    private String networkMaximaSummary() {
        return "max_output_channel_ether=" + maximumObservedOutputChannelEther
                + ", max_levitator_ether=" + maximumObservedLevitatorEther
                + ", max_levitator_fuel=" + maximumObservedLevitatorFuel
                + ", max_displacement=" + maximumObservedArmorStandDisplacement;
    }

    private synchronized void appendNetworkDiagnostic(String diagnostic) {
        List<String> previous = networkDiagnosticHistory;
        int retainedStart = Math.max(
                0,
                previous.size() - MAXIMUM_NETWORK_DIAGNOSTIC_HISTORY + 1
        );
        List<String> next = new ArrayList<>(
                previous.subList(retainedStart, previous.size())
        );
        next.add(diagnostic);
        networkDiagnosticHistory = List.copyOf(next);
    }

    private ClientNetworkSnapshot captureClientNetwork(
            MinecraftClient client,
            UUID armorStandUuid
    ) {
        if (!isWorldLifecycleReady(client)) {
            clientProgressDiagnostic = "world lifecycle is not ready";
            return null;
        }
        ChunkPos missingChunk = findMissingClientFixtureChunk(client.world);
        if (missingChunk != null) {
            clientProgressDiagnostic = "fixture chunk " + missingChunk + " is not loaded";
            return null;
        }
        if (!client.player.isCreative()) {
            clientProgressDiagnostic = "client player is not creative";
            return null;
        }

        for (FixtureBlock fixtureBlock : INITIAL_FIXTURE_BLOCKS) {
            Identifier actualBlockId = Registries.BLOCK.getId(
                    client.world.getBlockState(fixtureBlock.pos()).getBlock()
            );
            if (!fixtureBlock.blockId().equals(actualBlockId)) {
                clientProgressDiagnostic = fixtureBlock.name() + " block is " + actualBlockId;
                return null;
            }
            if (client.world.getBlockEntity(fixtureBlock.pos()) == null) {
                clientProgressDiagnostic = fixtureBlock.name() + " block entity is missing";
                return null;
            }
        }

        ArmorStandEntity armorStand = findClientArmorStand(client.world, armorStandUuid);
        if (armorStand == null) {
            clientProgressDiagnostic = "force-probe UUID " + armorStandUuid
                    + " is not currently loaded";
            return null;
        }

        NetworkState state = captureNetworkState(client.world, armorStand);
        Identifier outputBlockId = Registries.BLOCK.getId(
                client.world.getBlockState(OUTPUT_CHANNEL.pos()).getBlock()
        );
        clientProgressDiagnostic = "output=" + outputBlockId + ", " + state.summary();
        return new ClientNetworkSnapshot(state, outputBlockId);
    }

    private RoundTripResult roundTripGenerator(ServerWorld world) {
        return roundTrip(
                world,
                GENERATOR,
                (serialized, restored) -> approximately(
                        restored.getFloat("stored_ether"),
                        0.0f
                )
                        && restored.getInt("next_gen_time")
                        == serialized.getInt("next_gen_time")
                        && restored.getBoolean("is_mess")
                        == serialized.getBoolean("is_mess"),
                nbt -> "stored_ether=" + nbt.getFloat("stored_ether")
                        + ", next_gen_time=" + nbt.getInt("next_gen_time")
                        + ", is_mess=" + nbt.getBoolean("is_mess")
        );
    }

    private RoundTripResult roundTripChannel(ServerWorld world, FixtureBlock channel) {
        float expectedEther = channel == OUTPUT_CHANNEL ? 1.0f : 0.0f;
        return roundTrip(
                world,
                channel,
                (serialized, restored) -> approximately(
                        restored.getFloat("stored_ether"),
                        expectedEther
                )
                        && restored.getBoolean("evaporating")
                        == serialized.getBoolean("evaporating")
                        && restored.getBoolean("cross_evaporating")
                        == serialized.getBoolean("cross_evaporating"),
                nbt -> "stored_ether=" + nbt.getFloat("stored_ether")
                        + ", evaporating=" + nbt.getBoolean("evaporating")
                        + ", cross_evaporating=" + nbt.getBoolean("cross_evaporating")
        );
    }

    private RoundTripResult roundTripStorage(ServerWorld world) {
        return roundTrip(
                world,
                STORAGE,
                (serialized, restored) -> approximately(
                        restored.getFloat("storage_ether"),
                        1.0f
                ),
                nbt -> "storage_ether=" + nbt.getFloat("storage_ether")
        );
    }

    private RoundTripResult roundTripLevitator(ServerWorld world) {
        return roundTrip(
                world,
                LEVITATOR,
                (serialized, restored) -> approximately(
                        restored.getFloat("stored_ether"),
                        1.0f
                ) && restored.getInt("fuel") == serialized.getInt("fuel"),
                nbt -> "stored_ether=" + nbt.getFloat("stored_ether")
                        + ", fuel=" + nbt.getInt("fuel")
        );
    }

    private RoundTripResult roundTrip(
            ServerWorld world,
            FixtureBlock fixtureBlock,
            java.util.function.BiPredicate<NbtCompound, NbtCompound> retainedState,
            java.util.function.Function<NbtCompound, String> describeState
    ) {
        BlockEntity original = requireBlockEntity(world, fixtureBlock);
        NbtCompound serialized = original.createNbtWithId();
        BlockEntity restored = BlockEntity.createFromNbt(
                fixtureBlock.pos(),
                world.getBlockState(fixtureBlock.pos()),
                serialized
        );
        if (restored == null) {
            return new RoundTripResult(
                    fixtureBlock.name(),
                    false,
                    serialized.getString("id"),
                    "not restored"
            );
        }

        NbtCompound restoredNbt = restored.createNbtWithId();
        boolean typeMatches = restored.getType() == original.getType();
        boolean stateRetained = retainedState.test(serialized, restoredNbt);
        return new RoundTripResult(
                fixtureBlock.name(),
                typeMatches && stateRetained,
                restoredNbt.getString("id"),
                "matching_type=" + typeMatches + ", " + describeState.apply(restoredNbt)
        );
    }

    private void captureBefore(MinecraftClient client) {
        if (stage != Stage.WAITING_FOR_BEFORE_RENDERS) return;

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
            resultConsumer.accept(ScreenshotResult.failed("The evidence layout was not initialized"));
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
                return ScreenshotResult.failed("Minecraft did not write a regular PNG");
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

    private void submitSave(MinecraftClient client) {
        if (saveSubmitted) return;

        IntegratedServer server = requireRunningServer(client);
        InitialSetupResult setup = initialSetupResult;
        if (setup == null) {
            throw new IllegalStateException("The initial fixture result is missing before save");
        }

        saveSubmitted = true;
        server.execute(() -> {
            try {
                ServerWorld world = server.getOverworld();
                ArmorStandEntity armorStand = findServerArmorStand(
                        world,
                        setup.armorStandUuid()
                );
                if (armorStand == null) {
                    serverProgressDiagnostic = "save: force-probe UUID "
                            + setup.armorStandUuid() + " is not currently loaded";
                    saveSubmitted = false;
                    return;
                }

                serverProgressDiagnostic = "save: force-probe resolved, saving world";
                saveResult = server.saveAll(false, true, true);
                NetworkState retainedState = captureNetworkState(world, armorStand);
                serverProgressDiagnostic = "save complete=" + saveResult
                        + ", " + retainedState.summary();
                postSaveResult = new PostSaveResult(
                        retainedState.matchesRetainedNetworkState(),
                        retainedState
                );
            } catch (RuntimeException exception) {
                LOGGER.error("Cannot force-save the ether-network integrated world", exception);
                saveFailure = describe(exception);
                serverProgressDiagnostic = "save error: " + saveFailure;
            }
        });
        if (stage != Stage.SAVING_WORLD) {
            transition(Stage.SAVING_WORLD);
        }
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;

        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Etherology ether-network lifecycle failure: {}", lifecycleFailure);
        publish(client);
    }

    private void publish(MinecraftClient client) {
        if (stage == Stage.COMPLETE) return;

        try {
            ensureEvidenceLayout(client);
            List<ArtifactDigest> artifactDigests = List.of(
                    ArtifactDigest.capture("etherology"),
                    ArtifactDigest.capture("etherology_e2e_harness")
            );
            JsonObject report = createReport(client, artifactDigests);
            AtomicEvidenceWriter.writeReportThenMarker(evidenceLayout, report);
            LOGGER.info(
                    "Etherology ether-network evidence is complete: {}",
                    evidenceLayout.reportsDirectory()
            );
        } catch (IOException exception) {
            LOGGER.error("Cannot atomically publish ether-network evidence", exception);
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

    private JsonObject createReport(MinecraftClient client, List<ArtifactDigest> artifactDigests) {
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
                "fixture_registry_preflight",
                registryPreflightPassed,
                "all fixture registries and state network ids present",
                Boolean.toString(registryPreflightPassed)
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
        for (ArtifactDigest digest : artifactDigests) {
            passed &= addAssertion(
                    assertions,
                    "packaged_root_jar:" + digest.modId(),
                    digest.passed(),
                    "one regular root JAR",
                    digest.passed() ? "one regular root JAR" : digest.failure()
            );
        }

        InitialSetupResult setup = initialSetupResult;
        passed &= addAssertion(
                assertions,
                "server_fixture_chunks_loaded",
                setup != null && setup.fixtureChunksLoaded(),
                "full chunks " + FIXTURE_CHUNKS,
                setup == null
                        ? "missing setup"
                        : Boolean.toString(setup.fixtureChunksLoaded())
        );
        passed &= addAssertion(
                assertions,
                "server_fixture_chunks_forced",
                setup != null && setup.fixtureChunksForced(),
                "forced chunks " + FIXTURE_CHUNKS,
                setup == null
                        ? "missing setup"
                        : Boolean.toString(setup.fixtureChunksForced())
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
                "server_initial_fixture_blocks_placed",
                setup != null && setup.allBlocksPlaced(),
                "all expected identifiers",
                setup == null ? "missing setup" : setup.placedBlockIds().toString()
        );
        passed &= addAssertion(
                assertions,
                "server_initial_fixture_block_entities_present",
                setup != null && setup.allBlockEntitiesPresent(),
                "all expected block-entity identifiers",
                setup == null ? "missing setup" : setup.blockEntityIds().toString()
        );
        passed &= addAssertion(
                assertions,
                "server_initial_network_state",
                setup != null && setup.matchesInitialState(),
                "empty network, powered unfueled Levitator, output path absent",
                setup == null ? "missing setup" : setup.summary()
        );
        passed &= addAssertion(
                assertions,
                "server_force_probe_entity_spawned",
                setup != null
                        && setup.armorStandSpawned()
                        && FORCE_PROBE_ENTITY_UUID.equals(setup.armorStandUuid()),
                "minecraft:armor_stand named '" + FORCE_PROBE_ENTITY_NAME
                        + "' with UUID " + FORCE_PROBE_ENTITY_UUID,
                setup == null
                        ? "missing setup"
                        : "spawned=" + setup.armorStandSpawned()
                                + ", uuid=" + setup.armorStandUuid()
        );

        SourceChargeResult sourceCharge = sourceChargeResult;
        passed &= addAssertion(
                assertions,
                "spinner_generated_first_ether_unit",
                sourceCharge != null
                        && sourceCharge.generatorCycles() == 1
                        && sourceCharge.nextGenerationTime() > 0,
                "one completed generation and reset cooldown",
                sourceCharge == null ? "missing" : sourceCharge.summary()
        );
        passed &= addAssertion(
                assertions,
                "directional_channel_charged_storage",
                sourceCharge != null && sourceCharge.state().matchesChargedStorageState(),
                "1 ether in storage, no ether elsewhere, output path absent",
                sourceCharge == null ? "missing" : sourceCharge.state().summary()
        );
        passed &= addAssertion(
                assertions,
                "spinner_paused_after_source_charge",
                sourceCharge != null && sourceCharge.generatorPaused(),
                "stalled after exactly one generated unit",
                sourceCharge == null
                        ? "missing"
                        : Boolean.toString(sourceCharge.generatorPaused())
        );
        passed &= addAssertion(
                assertions,
                "client_charged_storage_mirror",
                beforeClientSnapshot != null && beforeClientSnapshot.matchesChargedStorageState(),
                "server charged-storage state",
                beforeClientSnapshot == null ? "missing" : beforeClientSnapshot.summary()
        );

        NetworkResult network = networkResult;
        passed &= addAssertion(
                assertions,
                "levitator_consumed_ether",
                network != null && network.fuelAtFirstConsumption() > 0,
                "positive fuel after the first transported unit",
                network == null ? "missing" : Integer.toString(network.fuelAtFirstConsumption())
        );
        passed &= addAssertion(
                assertions,
                "levitator_applied_real_force",
                network != null
                        && network.displacementAtFirstConsumption()
                        >= MINIMUM_ARMOR_STAND_DISPLACEMENT,
                ">=" + MINIMUM_ARMOR_STAND_DISPLACEMENT + " blocks on +X",
                network == null
                        ? "missing"
                        : Double.toString(network.displacementAtFirstConsumption())
        );
        passed &= addAssertion(
                assertions,
                "redstone_gate_closed_after_force_probe",
                network != null
                        && network.state().levitatorPower() == 0
                        && !network.state().levitatorPowered(),
                "power=0, powered=false",
                network == null ? "missing" : network.state().redstoneSummary()
        );
        passed &= addAssertion(
                assertions,
                "generator_cycles_completed",
                network != null && network.generatorCycles() == EXPECTED_GENERATOR_CYCLES,
                Integer.toString(EXPECTED_GENERATOR_CYCLES),
                network == null ? "missing" : Integer.toString(network.generatorCycles())
        );
        passed &= addAssertion(
                assertions,
                "retained_ether_distribution",
                network != null && network.state().matchesRetainedNetworkState(),
                "storage=1, output channel=1, Levitator=1, upstream empty",
                network == null ? "missing" : network.state().summary()
        );
        passed &= addAssertion(
                assertions,
                "output_channel_created_by_activation",
                network != null && OUTPUT_CHANNEL.blockId().equals(network.outputBlockId()),
                OUTPUT_CHANNEL.blockId().toString(),
                network == null ? "missing" : network.outputBlockId().toString()
        );
        passed &= addAssertion(
                assertions,
                "client_retained_network_mirror",
                afterClientSnapshot != null && afterClientSnapshot.matchesRetainedNetworkState(),
                "server retained-network state and moved armor stand",
                afterClientSnapshot == null ? "missing" : afterClientSnapshot.summary()
        );
        for (FixtureBlock fixtureBlock : FINAL_FIXTURE_BLOCKS) {
            RoundTripResult roundTrip = network == null
                    ? null
                    : network.roundTripFor(fixtureBlock.name());
            passed &= addAssertion(
                    assertions,
                    "block_entity_nbt_round_trip:" + fixtureBlock.name(),
                    roundTrip != null && roundTrip.passed(),
                    "matching type and retained mechanic state",
                    roundTrip == null ? "missing" : roundTrip.summary()
            );
        }

        boolean integratedWorldReady = isWorldLifecycleReady(client);
        passed &= addAssertion(
                assertions,
                "integrated_world_joined",
                integratedWorldReady,
                "running server and connected client",
                integratedWorldReady ? "joined" : "not joined"
        );
        passed &= addRenderAndScreenshotAssertions(
                assertions,
                "before",
                completedBeforeRenders,
                beforeFramebufferWidth,
                beforeFramebufferHeight,
                beforeScreenshotResult
        );
        passed &= addRenderAndScreenshotAssertions(
                assertions,
                "after",
                completedAfterRenders,
                afterFramebufferWidth,
                afterFramebufferHeight,
                afterScreenshotResult
        );
        boolean screenshotsDiffer = beforeScreenshotResult != null
                && beforeScreenshotResult.passed()
                && afterScreenshotResult != null
                && afterScreenshotResult.passed()
                && !beforeScreenshotResult.sha256().equals(afterScreenshotResult.sha256());
        passed &= addAssertion(
                assertions,
                "before_after_native_screenshots_differ",
                screenshotsDiffer,
                "different SHA-256 digests",
                beforeScreenshotResult == null || afterScreenshotResult == null
                        ? "missing screenshot"
                        : beforeScreenshotResult.sha256() + " != " + afterScreenshotResult.sha256()
        );

        passed &= addAssertion(
                assertions,
                "forced_world_save",
                Boolean.TRUE.equals(saveResult),
                "true",
                saveResult == null ? "not attempted" : saveResult.toString()
        );
        PostSaveResult retained = postSaveResult;
        passed &= addAssertion(
                assertions,
                "ether_network_retained_after_forced_save",
                retained != null && retained.retained(),
                "retained non-zero storage, transport, machine ether and moved entity",
                retained == null ? "missing" : retained.state().summary()
        );
        boolean saveDirectoryPresent = Files.isDirectory(
                saveDirectory(client),
                LinkOption.NOFOLLOW_LINKS
        ) && !Files.isSymbolicLink(saveDirectory(client));
        passed &= addAssertion(
                assertions,
                "isolated_save_directory_present",
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

        JsonObject networkDiagnostics = new JsonObject();
        networkDiagnostics.addProperty("step", networkStep);
        networkDiagnostics.addProperty("step_ticks", networkStepElapsedTicks);
        networkDiagnostics.addProperty(
                "max_output_channel_ether",
                maximumObservedOutputChannelEther
        );
        networkDiagnostics.addProperty("max_levitator_ether", maximumObservedLevitatorEther);
        networkDiagnostics.addProperty("max_levitator_fuel", maximumObservedLevitatorFuel);
        networkDiagnostics.addProperty(
                "max_displacement",
                maximumObservedArmorStandDisplacement
        );
        JsonArray networkHistory = new JsonArray();
        for (String diagnostic : networkDiagnosticHistory) {
            networkHistory.add(diagnostic);
        }
        networkDiagnostics.add("history", networkHistory);
        report.add("network_diagnostics", networkDiagnostics);

        JsonObject world = new JsonObject();
        world.addProperty("save_directory", WORLD_DIRECTORY_NAME);
        world.addProperty("display_name", WORLD_DISPLAY_NAME);
        world.addProperty("seed", WORLD_SEED);
        world.addProperty("dimension", World.OVERWORLD.getValue().toString());
        world.addProperty("integrated", true);
        report.add("world", world);

        JsonArray resources = new JsonArray();
        resources.add(ETHEROLOGY_TITLE_RESOURCE.toString());
        report.add("ready_resources", resources);

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
        addScreenshot(
                screenshots,
                "ether-network-storage-charged",
                BEFORE_SCREENSHOT_FILE_NAME,
                beforeFramebufferWidth,
                beforeFramebufferHeight,
                completedBeforeRenders,
                beforeScreenshotResult
        );
        addScreenshot(
                screenshots,
                "ether-network-transport-and-machine-retained",
                AFTER_SCREENSHOT_FILE_NAME,
                afterFramebufferWidth,
                afterFramebufferHeight,
                completedAfterRenders,
                afterScreenshotResult
        );
        report.add("screenshots", screenshots);
        return report;
    }

    private boolean addRenderAndScreenshotAssertions(
            JsonArray assertions,
            String step,
            int completedRenders,
            int width,
            int height,
            ScreenshotResult screenshotResult
    ) {
        boolean passed = addAssertion(
                assertions,
                "completed_world_renders_before_capture:" + step,
                completedRenders >= REQUIRED_COMPLETED_RENDERS,
                Integer.toString(REQUIRED_COMPLETED_RENDERS),
                Integer.toString(completedRenders)
        );
        passed &= addAssertion(
                assertions,
                "framebuffer_dimensions:" + step,
                width == FRAMEBUFFER_WIDTH && height == FRAMEBUFFER_HEIGHT,
                FRAMEBUFFER_WIDTH + "x" + FRAMEBUFFER_HEIGHT,
                width + "x" + height
        );
        passed &= addAssertion(
                assertions,
                "native_screenshot_written:" + step,
                screenshotResult != null && screenshotResult.passed(),
                "non-empty PNG",
                screenshotResult == null
                        ? "missing"
                        : screenshotResult.size() + " bytes, sha256=" + screenshotResult.sha256()
        );
        return passed;
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
        screenshots.add(screenshot);
    }

    private boolean forceFixtureChunks(ServerWorld world) {
        for (ChunkPos chunkPos : FIXTURE_CHUNKS) {
            world.setChunkForced(chunkPos.x, chunkPos.z, true);
        }
        for (ChunkPos chunkPos : FIXTURE_CHUNKS) {
            if (!world.getForcedChunks().contains(chunkPos.toLong())) return false;
        }
        return true;
    }

    private boolean loadFixtureChunks(ServerWorld world) {
        for (ChunkPos chunkPos : FIXTURE_CHUNKS) {
            if (world.getChunkManager().getChunk(
                    chunkPos.x,
                    chunkPos.z,
                    ChunkStatus.FULL,
                    true
            ) == null) {
                return false;
            }
        }
        return true;
    }

    private void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-10, ARENA_FLOOR_Y, -11);
        BlockPos end = new BlockPos(10, ARENA_FLOOR_Y + 9, 10);
        for (BlockPos pos : BlockPos.iterate(start, end)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private void buildArena(ServerWorld world) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -11; z <= 10; z++) {
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

        for (int x = -7; x <= 7; x++) {
            for (int y = ARENA_FLOOR_Y + 1; y <= ARENA_FLOOR_Y + 7; y++) {
                Block backdrop = (x + y) % 5 == 0
                        ? Blocks.QUARTZ_PILLAR
                        : Blocks.SMOOTH_QUARTZ;
                world.setBlockState(
                        new BlockPos(x, y, 7),
                        backdrop.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
        }

        buildForceTrack(world);
    }

    private void buildForceTrack(ServerWorld world) {
        for (int x = FORCE_TRACK_START_X; x <= FORCE_TRACK_END_X; x++) {
            world.setBlockState(
                    new BlockPos(x, FORCE_TRACK_SUPPORT_Y, FORCE_TRACK_Z),
                    Blocks.SMOOTH_STONE.getDefaultState(),
                    Block.NOTIFY_ALL
            );
        }
    }

    private void markFixtureForUpdate(ServerWorld world, List<FixtureBlock> fixtureBlocks) {
        for (FixtureBlock fixtureBlock : fixtureBlocks) {
            if (world.getBlockEntity(fixtureBlock.pos()) != null) {
                world.getChunkManager().markForUpdate(fixtureBlock.pos());
            }
        }
    }

    private boolean isWorldLifecycleReady(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        return server != null
                && server.isRunning()
                && !server.isStopping()
                && client.world != null
                && client.player != null
                && client.player.networkHandler != null
                && client.interactionManager != null
                && client.world.getRegistryKey() == World.OVERWORLD;
    }

    private boolean isWorldViewReady(
            MinecraftClient client,
            ClientNetworkSnapshot snapshot
    ) {
        return isWorldLifecycleReady(client)
                && snapshot != null
                && client.getOverlay() == null
                && client.currentScreen == null
                && client.getCameraEntity() == client.player
                && findMissingClientFixtureChunk(client.world) == null
                && hasExpectedFramebuffer(client);
    }

    private boolean hasExpectedFramebuffer(MinecraftClient client) {
        return client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT;
    }

    private IntegratedServer requireRunningServer(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null || !server.isRunning() || server.isStopping()) {
            throw new IllegalStateException("The integrated server is not running");
        }
        if (client.player == null) {
            throw new IllegalStateException("The client player is unavailable");
        }
        return server;
    }

    private ServerPlayerEntity requireServerPlayer(IntegratedServer server, UUID playerId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("The integrated server has no matching player");
        }
        return player;
    }

    private BlockEntity requireBlockEntity(World world, FixtureBlock fixtureBlock) {
        BlockEntity blockEntity = world.getBlockEntity(fixtureBlock.pos());
        if (blockEntity == null) {
            throw new IllegalStateException(
                    "The fixture has no block entity at " + fixtureBlock.pos().toShortString()
            );
        }
        return blockEntity;
    }

    private ArmorStandEntity findServerArmorStand(ServerWorld world, UUID expectedUuid) {
        Entity entity = world.getEntity(expectedUuid);
        return entity == null ? null : requireFixtureArmorStand(entity, expectedUuid);
    }

    private ChunkPos findMissingClientFixtureChunk(ClientWorld world) {
        for (ChunkPos chunkPos : FIXTURE_CHUNKS) {
            if (!world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                return chunkPos;
            }
        }
        return null;
    }

    private ArmorStandEntity findClientArmorStand(ClientWorld world, UUID expectedUuid) {
        for (Entity entity : world.getEntities()) {
            if (expectedUuid.equals(entity.getUuid())) {
                return requireFixtureArmorStand(entity, expectedUuid);
            }
        }
        return null;
    }

    private ArmorStandEntity requireFixtureArmorStand(Entity entity, UUID expectedUuid) {
        if (!expectedUuid.equals(entity.getUuid())) {
            throw new IllegalStateException(
                    "The force-probe entity has UUID " + entity.getUuid()
                            + " instead of " + expectedUuid
            );
        }
        if (entity.getType() != EntityType.ARMOR_STAND
                || !(entity instanceof ArmorStandEntity armorStand)) {
            throw new IllegalStateException(
                    "The force-probe entity is " + EntityType.getId(entity.getType())
                            + " instead of minecraft:armor_stand"
            );
        }
        Text customName = armorStand.getCustomName();
        String actualName = customName == null ? "" : customName.getString();
        if (!FORCE_PROBE_ENTITY_NAME.equals(actualName)) {
            throw new IllegalStateException(
                    "The force-probe armor stand is named '" + actualName
                            + "' instead of '" + FORCE_PROBE_ENTITY_NAME + "'"
            );
        }
        if (armorStand.isRemoved() || !armorStand.isAlive()) {
            throw new IllegalStateException("The force-probe armor stand is not active");
        }
        return armorStand;
    }

    private BlockState channelState(
            Block block,
            String facing,
            String inputSide,
            String outputSide
    ) {
        BlockState state = block.getDefaultState();
        state = withStateProperty(state, "facing", facing);
        state = withStateProperty(state, "activated", "false");
        state = withStateProperty(state, "is_cross", "false");
        for (String side : List.of("north", "south", "east", "west", "up", "down")) {
            state = withStateProperty(state, side, "empty");
        }
        state = withStateProperty(state, inputSide, "in");
        return withStateProperty(state, outputSide, "out");
    }

    private BlockState withStateProperty(
            BlockState state,
            String propertyName,
            String serializedValue
    ) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName())) {
                return withParsedStateProperty(state, property, serializedValue);
            }
        }
        throw new IllegalStateException("Block state has no property named " + propertyName);
    }

    private <T extends Comparable<T>> BlockState withParsedStateProperty(
            BlockState state,
            Property<T> property,
            String serializedValue
    ) {
        T value = property.parse(serializedValue).orElseThrow(
                () -> new IllegalStateException(
                        "Property " + property.getName() + " rejects " + serializedValue
                )
        );
        return state.with(property, value);
    }

    private String statePropertyValue(BlockState state, String propertyName) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName())) {
                return state.get(property).toString();
            }
        }
        throw new IllegalStateException("Block state has no property named " + propertyName);
    }

    private Path saveDirectory(MinecraftClient client) {
        return client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
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

    private static FixtureBlock fixture(
            String name,
            String blockPath,
            String blockEntityTypePath,
            int x,
            int y,
            int z
    ) {
        return new FixtureBlock(
                name,
                etherologyId(blockPath),
                etherologyId(blockEntityTypePath),
                new BlockPos(x, y, z)
        );
    }

    private static Identifier etherologyId(String path) {
        return new Identifier("etherology", path);
    }

    private static boolean approximately(float value, float expected) {
        return Math.abs(value - expected) <= ETHER_TOLERANCE;
    }

    private static String describe(RuntimeException exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_INITIAL_SETUP,
        WAITING_FOR_SOURCE_CHARGE,
        WAITING_FOR_BEFORE_CLIENT_MIRROR,
        WAITING_FOR_BEFORE_RENDERS,
        CAPTURING_BEFORE,
        WAITING_FOR_NETWORK,
        WAITING_FOR_AFTER_CLIENT_MIRROR,
        WAITING_FOR_AFTER_RENDERS,
        CAPTURING_AFTER,
        SAVING_WORLD,
        COMPLETE
    }

    private record FixtureBlock(
            String name,
            Identifier blockId,
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

    private record InitialSetupResult(
            boolean fixtureChunksLoaded,
            boolean fixtureChunksForced,
            boolean playerCreative,
            boolean allBlocksPlaced,
            boolean allBlockEntitiesPresent,
            boolean outputPathAbsent,
            boolean armorStandSpawned,
            UUID armorStandUuid,
            NetworkState initialState,
            List<String> placedBlockIds,
            List<String> blockEntityIds
    ) {

        private boolean matchesInitialState() {
            return outputPathAbsent
                    && initialState.matchesEmptyPoweredState();
        }

        private String summary() {
            return "fixture_chunks_loaded=" + fixtureChunksLoaded
                    + ", fixture_chunks_forced=" + fixtureChunksForced
                    + ", output_path_absent=" + outputPathAbsent
                    + ", armor_stand_spawned=" + armorStandSpawned
                    + ", armor_stand_uuid=" + armorStandUuid
                    + ", " + initialState.summary();
        }
    }

    private record SourceChargeResult(
            NetworkState state,
            int nextGenerationTime,
            int generatorCycles,
            boolean generatorPaused
    ) {

        private String summary() {
            return "cycles=" + generatorCycles
                    + ", next_gen_time=" + nextGenerationTime
                    + ", generator_paused=" + generatorPaused
                    + ", " + state.summary();
        }
    }

    private record NetworkResult(
            NetworkState state,
            int fuelAtFirstConsumption,
            double displacementAtFirstConsumption,
            int generatorCycles,
            Identifier outputBlockId,
            List<RoundTripResult> roundTrips
    ) {

        private RoundTripResult roundTripFor(String fixtureName) {
            for (RoundTripResult roundTrip : roundTrips) {
                if (fixtureName.equals(roundTrip.fixtureName())) return roundTrip;
            }
            return null;
        }
    }

    private record RoundTripResult(
            String fixtureName,
            boolean passed,
            String blockEntityId,
            String detail
    ) {

        private String summary() {
            return blockEntityId + ", " + detail;
        }
    }

    private record ClientNetworkSnapshot(NetworkState state, Identifier outputBlockId) {

        private boolean matchesChargedStorageState() {
            return outputBlockId.equals(Registries.BLOCK.getId(Blocks.AIR))
                    && state.matchesChargedStorageState();
        }

        private boolean matchesRetainedNetworkState() {
            return OUTPUT_CHANNEL.blockId().equals(outputBlockId)
                    && state.matchesRetainedNetworkState();
        }

        private String summary() {
            return "output=" + outputBlockId + ", " + state.summary();
        }
    }

    private record NetworkState(
            float generatorEther,
            float sourceChannelEther,
            float storageEther,
            float outputChannelEther,
            float levitatorEther,
            int levitatorFuel,
            boolean outputChannelPresent,
            Identifier outputBlockId,
            String outputChannelState,
            boolean levitatorWithFuel,
            int levitatorPower,
            boolean levitatorPowered,
            String levitatorFacing,
            boolean levitatorPushing,
            double armorStandX,
            double armorStandY,
            double armorStandZ
    ) {

        private boolean matchesEmptyPoweredState() {
            return totalEther() <= ETHER_TOLERANCE
                    && !outputChannelPresent
                    && levitatorFuel == 0
                    && !levitatorWithFuel
                    && levitatorPower > 0
                    && levitatorPowered
                    && Math.abs(armorStandDisplacement()) <= ETHER_TOLERANCE
                    && armorStandOnForceTrack();
        }

        private boolean matchesChargedStorageState() {
            return approximately(generatorEther, 0.0f)
                    && approximately(sourceChannelEther, 0.0f)
                    && approximately(storageEther, 1.0f)
                    && approximately(outputChannelEther, 0.0f)
                    && approximately(levitatorEther, 0.0f)
                    && !outputChannelPresent
                    && levitatorFuel == 0
                    && !levitatorWithFuel
                    && levitatorPower > 0
                    && levitatorPowered
                    && Math.abs(armorStandDisplacement()) <= ETHER_TOLERANCE
                    && armorStandOnForceTrack();
        }

        private boolean firstUnitConsumed() {
            return approximately(totalEther(), 0.0f)
                    && outputChannelPresent
                    && levitatorFuel > 0
                    && levitatorWithFuel
                    && levitatorPower > 0
                    && levitatorPowered
                    && armorStandDisplacement() >= MINIMUM_ARMOR_STAND_DISPLACEMENT
                    && armorStandOnForceTrack();
        }

        private boolean secondUnitRetainedByLevitator() {
            return approximately(generatorEther, 0.0f)
                    && approximately(sourceChannelEther, 0.0f)
                    && approximately(storageEther, 0.0f)
                    && approximately(outputChannelEther, 0.0f)
                    && approximately(levitatorEther, 1.0f)
                    && levitatorPower == 0
                    && !levitatorPowered;
        }

        private boolean thirdUnitRetainedByOutputChannel() {
            return approximately(generatorEther, 0.0f)
                    && approximately(sourceChannelEther, 0.0f)
                    && approximately(storageEther, 0.0f)
                    && approximately(outputChannelEther, 1.0f)
                    && approximately(levitatorEther, 1.0f)
                    && levitatorPower == 0
                    && !levitatorPowered;
        }

        private boolean matchesRetainedNetworkState() {
            return approximately(generatorEther, 0.0f)
                    && approximately(sourceChannelEther, 0.0f)
                    && approximately(storageEther, 1.0f)
                    && approximately(outputChannelEther, 1.0f)
                    && approximately(levitatorEther, 1.0f)
                    && outputChannelPresent
                    && levitatorWithFuel
                    && levitatorPower == 0
                    && !levitatorPowered
                    && armorStandDisplacement() >= MINIMUM_ARMOR_STAND_DISPLACEMENT
                    && armorStandOnForceTrack();
        }

        private float totalEther() {
            return generatorEther
                    + sourceChannelEther
                    + storageEther
                    + outputChannelEther
                    + levitatorEther;
        }

        private double armorStandDisplacement() {
            return armorStandX - ARMOR_STAND_INITIAL_POS.x;
        }

        private boolean armorStandOnForceTrack() {
            return Math.abs(armorStandY - ARMOR_STAND_INITIAL_POS.y) <= 0.1
                    && Math.abs(armorStandZ - ARMOR_STAND_INITIAL_POS.z) <= 0.1;
        }

        private String diagnosticPhase() {
            int displacementPhase;
            if (Math.abs(armorStandDisplacement()) <= ETHER_TOLERANCE) {
                displacementPhase = 0;
            } else if (armorStandDisplacement() < MINIMUM_ARMOR_STAND_DISPLACEMENT) {
                displacementPhase = 1;
            } else {
                displacementPhase = 2;
            }
            return generatorEther + "|" + sourceChannelEther + "|" + storageEther
                    + "|" + outputChannelEther + "|" + levitatorEther
                    + "|fuel_positive=" + (levitatorFuel > 0)
                    + "|with_fuel=" + levitatorWithFuel
                    + "|output=" + outputBlockId + "|" + outputChannelState
                    + "|power=" + levitatorPower + "|powered=" + levitatorPowered
                    + "|facing=" + levitatorFacing + "|pushing=" + levitatorPushing
                    + "|displacement_phase=" + displacementPhase;
        }

        private String redstoneSummary() {
            return "power=" + levitatorPower + ", powered=" + levitatorPowered;
        }

        private String summary() {
            return "generator=" + generatorEther
                    + ", source_channel=" + sourceChannelEther
                    + ", storage=" + storageEther
                    + ", output_channel=" + outputChannelEther
                    + ", levitator=" + levitatorEther
                    + ", fuel=" + levitatorFuel
                    + ", output_present=" + outputChannelPresent
                    + ", output_block=" + outputBlockId
                    + ", output_state={" + outputChannelState + "}"
                    + ", with_fuel=" + levitatorWithFuel
                    + ", power=" + levitatorPower
                    + ", powered=" + levitatorPowered
                    + ", levitator_facing=" + levitatorFacing
                    + ", levitator_pushing=" + levitatorPushing
                    + ", armor_stand_x=" + armorStandX
                    + ", armor_stand_y=" + armorStandY
                    + ", armor_stand_z=" + armorStandZ
                    + ", displacement=" + armorStandDisplacement();
        }
    }

    private record PostSaveResult(boolean retained, NetworkState state) {
    }
}
