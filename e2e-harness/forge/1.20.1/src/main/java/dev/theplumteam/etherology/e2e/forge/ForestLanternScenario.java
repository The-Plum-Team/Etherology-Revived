package dev.theplumteam.etherology.e2e.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DatapackFailureScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.Resource;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
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
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class ForestLanternScenario {

    static final String SCENARIO_ID = "forest-lantern";
    static final String WORLD_DIRECTORY_NAME = "etherology-e2e-forest-lantern-world";
    static final String WORLD_DISPLAY_NAME = "Etherology E2E Forest Lantern";
    static final long WORLD_SEED = 0x464F52455354L;
    static final int REQUIRED_COMPLETED_RENDERS = 120;
    static final List<Identifier> READY_RESOURCES = createReadyResources();
    static final List<String> ASSERTION_NAMES = createAssertionNames();
    static final List<String> SCREENSHOT_FILE_NAMES = createScreenshotFileNames();

    private static final Logger LOGGER = LoggerFactory.getLogger("EtherologyE2EHarness");
    private static final String HARNESS_MOD_ID = "etherology_e2e_harness";
    private static final Identifier FOREST_LANTERN_ID =
            Identifier.of("etherology", "forest_lantern");
    private static final Identifier STAGE_SUPPORT_A_ID =
            Identifier.of("minecraft", "oak_log");
    private static final Identifier STAGE_SUPPORT_B_ID =
            Identifier.of("minecraft", "stripped_oak_log");
    private static final Identifier AIR_ID = Identifier.of("minecraft", "air");
    private static final Identifier PLACEMENT_SUPPORT_ID =
            Identifier.of("minecraft", "polished_andesite");
    private static final Identifier UNSUPPORTED_SUPPORT_ID =
            Identifier.of("minecraft", "iron_bars");
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final double CAMERA_X = 9.5;
    private static final double CAMERA_Y = 121.0;
    private static final double CAMERA_Z = -10.5;
    private static final float CAMERA_YAW = 38.0F;
    private static final float CAMERA_PITCH = -6.0F;
    private static final double CAMERA_POSE_TOLERANCE = 0.0001;
    private static final BlockPos CAMERA_BLOCK_POS =
            new BlockPos(9, ARENA_FLOOR_Y + 1, -11);
    private static final List<StageFixture> STAGE_FIXTURES = createStageFixtures();
    private static final List<PlacementFixture> PLACEMENT_FIXTURES = List.of(
            placementFixture(Direction.NORTH, -6),
            placementFixture(Direction.EAST, -2),
            placementFixture(Direction.SOUTH, 2),
            placementFixture(Direction.WEST, 6)
    );
    private static final BlockPos UNSUPPORTED_POSITION =
            new BlockPos(9, ARENA_FLOOR_Y + 5, 2);
    private static final BlockPos UNSUPPORTED_SUPPORT_POSITION =
            UNSUPPORTED_POSITION.offset(Direction.SOUTH);

    private Stage stage = Stage.WAITING_FOR_TITLE;
    private CapturePhase capturePhase = CapturePhase.EMPTY;
    private int clientTicks;
    private int stageClientTicks;
    private int completedRenders;
    private boolean worldSetupSubmitted;
    private boolean mutationSubmitted;
    private boolean saveSubmitted;
    private boolean restartSubmitted;
    private boolean restartInspectionSubmitted;
    private String lifecycleFailure = "";
    private ForgeEvidenceLayout evidenceLayout;
    private RegistryProbe registryProbe;
    private ServerSetupResult serverSetupResult;
    private FixtureSnapshot savedSnapshot;
    private FixtureSnapshot reopenedSnapshot;
    private FixtureSnapshot currentServerSnapshot;
    private FixtureSnapshot currentClientSnapshot;
    private PlacementEvidence unsupportedPlacementEvidence;
    private boolean persistenceExact;
    private final Map<Direction, PlacementEvidence> placementEvidence =
            new EnumMap<>(Direction.class);
    private final Map<CapturePhase, CaptureEvidence> captureEvidence =
            new EnumMap<>(CapturePhase.class);
    private final Map<CapturePhase, FixtureSnapshot> serverSnapshots =
            new EnumMap<>(CapturePhase.class);
    private final Map<CapturePhase, FixtureSnapshot> clientSnapshots =
            new EnumMap<>(CapturePhase.class);
    private volatile String serverFailure = "";
    private volatile ServerSetupResult pendingServerSetupResult;
    private volatile MutationResult pendingMutationResult;
    private volatile SaveResult pendingSaveResult;
    private volatile FixtureSnapshot pendingReopenedSnapshot;
    private volatile ScreenshotResult pendingScreenshotResult;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.COMPLETE) return;

        MinecraftClient client = MinecraftClient.getInstance();
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
                case WAITING_FOR_MUTATION -> tickWaitingForMutation(client);
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
            LOGGER.error("Etherology forest-lantern failed while in {}", stage, exception);
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

    @SubscribeEvent
    public void onWorldRendered(RenderGuiEvent.Post event) {
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
            LOGGER.error("Etherology forest-lantern render callback failed", exception);
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

        registryProbe = inspectRegistry(client);
        if (!registryProbe.exact()) {
            fail(client, "The Forest Lantern registry/render probe failed: " + registryProbe);
            return;
        }
        try {
            ensureForgeEvidenceLayout(client);
        } catch (IOException exception) {
            LOGGER.error("Cannot use the isolated forest-lantern evidence layout", exception);
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
            fail(client, "Refusing to reuse the forest-lantern save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "The client already has a world before Forest Lantern creation");
            return;
        }

        GameRules gameRules = new GameRules();
        gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        gameRules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        gameRules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, null);
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
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(client.player.getUuid());
        if (player == null || worldSetupSubmitted) return;

        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(client.player);
        worldSetupSubmitted = true;
        UUID playerId = client.player.getUuid();
        server.execute(() -> setupInitialFixture(server, playerId));
        transition(Stage.WAITING_FOR_SERVER_SETUP);
    }

    private void tickWaitingForServerSetup(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "Initial Forest Lantern server setup failed: " + serverFailure);
            return;
        }
        ServerSetupResult result = pendingServerSetupResult;
        if (result == null) return;

        serverSetupResult = result;
        currentServerSnapshot = result.snapshot();
        serverSnapshots.put(CapturePhase.EMPTY, currentServerSnapshot);
        if (!result.exact() || !currentServerSnapshot.matchesPhase(CapturePhase.EMPTY)) {
            fail(client, "The empty Forest Lantern fixture is not exact");
            return;
        }
        transition(Stage.WAITING_FOR_CLIENT_MIRROR);
    }

    private void tickWaitingForClientMirror(MinecraftClient client) {
        if (!isWorldViewReady(client)) return;

        FixtureSnapshot clientSnapshot = captureSnapshot(client.world);
        if (!clientSnapshot.equals(currentServerSnapshot)) return;
        if (!clientSnapshot.matchesPhase(capturePhase)) {
            fail(client, "The client Forest Lantern fixture has an unexpected phase state");
            return;
        }
        currentClientSnapshot = clientSnapshot;
        clientSnapshots.put(capturePhase, clientSnapshot);
        completedRenders = 0;
        transition(Stage.WAITING_FOR_RENDERS);
    }

    private void tickWaitingForRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before a Forest Lantern capture");
        }
    }

    private void tickCapturing(MinecraftClient client) {
        ScreenshotResult screenshot = pendingScreenshotResult;
        if (screenshot == null) return;
        if (!screenshot.passed()) {
            fail(client, capturePhase.id() + " screenshot failed: " + screenshot.failure());
            return;
        }

        CaptureEvidence previous = captureEvidence.get(capturePhase);
        captureEvidence.put(capturePhase, previous.withScreenshot(screenshot));
        pendingScreenshotResult = null;
        switch (capturePhase) {
            case EMPTY -> submitStageFixture(client);
            case STAGES -> submitPlacement(client, Direction.NORTH);
            case FACING_NORTH -> submitPlacement(client, Direction.EAST);
            case FACING_EAST -> submitPlacement(client, Direction.SOUTH);
            case FACING_SOUTH -> submitPlacement(client, Direction.WEST);
            case FACING_WEST -> submitSave(client);
            case REOPENED -> publish(client);
        }
    }

    private void tickWaitingForMutation(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "Forest Lantern server mutation failed: " + serverFailure);
            return;
        }
        MutationResult result = pendingMutationResult;
        if (result == null) return;

        pendingMutationResult = null;
        mutationSubmitted = false;
        if (result.unsupportedPlacement() != null) {
            unsupportedPlacementEvidence = result.unsupportedPlacement();
            if (!unsupportedPlacementEvidence.exactRejected()) {
                fail(client, "The unsupported mature BlockItem placement was not rejected");
                return;
            }
        }
        if (result.placement() != null) {
            placementEvidence.put(result.placement().direction(), result.placement());
            if (!result.placement().exactAccepted()) {
                fail(client, "The real BlockItem placement was not exact: " + result.placement());
                return;
            }
        }
        capturePhase = result.phase();
        currentServerSnapshot = result.snapshot();
        serverSnapshots.put(capturePhase, currentServerSnapshot);
        if (!currentServerSnapshot.matchesPhase(capturePhase)) {
            fail(client, "The server Forest Lantern fixture did not reach " + capturePhase.id());
            return;
        }
        transition(Stage.WAITING_FOR_CLIENT_MIRROR);
    }

    private void tickSavingWorld(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "The Forest Lantern world save failed: " + serverFailure);
            return;
        }
        SaveResult result = pendingSaveResult;
        if (result == null) return;
        if (!result.saved()) {
            fail(client, "The integrated server rejected the Forest Lantern forced save");
            return;
        }
        savedSnapshot = result.snapshot();
        if (!savedSnapshot.matchesPhase(CapturePhase.FACING_WEST)) {
            fail(client, "The saved Forest Lantern fixture was not exact");
            return;
        }
        transition(Stage.DISCONNECTING);
    }

    private void tickDisconnecting(MinecraftClient client) {
        if (client.world == null) {
            fail(client, "The client world vanished before the Forest Lantern restart");
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
            fail(client, "Minecraft rejected the saved data packs on Forest Lantern restart");
            return;
        }
        if (!isWorldLifecycleReady(client)) return;

        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(client.player);
        transition(Stage.WAITING_FOR_RESTART_INSPECTION);
    }

    private void tickWaitingForRestartInspection(MinecraftClient client) {
        if (!serverFailure.isEmpty()) {
            fail(client, "The reopened Forest Lantern inspection failed: " + serverFailure);
            return;
        }
        IntegratedServer server = client.getServer();
        if (server == null) return;

        if (!restartInspectionSubmitted) {
            restartInspectionSubmitted = true;
            server.execute(() -> {
                try {
                    pendingReopenedSnapshot = captureSnapshot(server.getOverworld());
                } catch (RuntimeException exception) {
                    recordServerFailure(exception);
                }
            });
        }
        FixtureSnapshot snapshot = pendingReopenedSnapshot;
        if (snapshot == null) return;

        reopenedSnapshot = snapshot;
        persistenceExact = savedSnapshot != null
                && savedSnapshot.equals(reopenedSnapshot)
                && reopenedSnapshot.matchesPhase(CapturePhase.REOPENED);
        if (!persistenceExact) {
            fail(client, "The Forest Lantern fixture changed across save/reopen");
            return;
        }
        capturePhase = CapturePhase.REOPENED;
        currentServerSnapshot = reopenedSnapshot;
        serverSnapshots.put(capturePhase, currentServerSnapshot);
        transition(Stage.WAITING_FOR_CLIENT_MIRROR);
    }

    private RegistryProbe inspectRegistry(MinecraftClient client) {
        boolean blockPresent = Registries.BLOCK.containsId(FOREST_LANTERN_ID);
        boolean itemPresent = Registries.ITEM.containsId(FOREST_LANTERN_ID);
        if (!blockPresent || !itemPresent) {
            return RegistryProbe.failed(blockPresent, itemPresent);
        }

        Block block = Registries.BLOCK.get(FOREST_LANTERN_ID);
        Item item = Registries.ITEM.get(FOREST_LANTERN_ID);
        boolean blockItemMapping = item instanceof BlockItem blockItem
                && blockItem.getBlock() == block;
        BlockState defaultState = block.getDefaultState();
        List<String> stateInventory = new ArrayList<>();
        List<String> renderableStateInventory = new ArrayList<>();
        List<Integer> rawStateIds = new ArrayList<>();
        boolean bakedModelsComplete = true;
        for (BlockState state : block.getStateManager().getStates()) {
            stateInventory.add(canonicalState(state));
            int rawId = Block.STATE_IDS.getRawId(state);
            rawStateIds.add(rawId);
            BakedModel bakedModel = client.getBlockRenderManager().getModel(state);
            boolean modelPresent = bakedModel
                    != client.getBakedModelManager().getMissingModel();
            bakedModelsComplete &= rawId >= 0 && modelPresent;
            if (modelPresent && hasBakedGeometry(bakedModel, state)) {
                renderableStateInventory.add(canonicalState(state));
            }
        }
        stateInventory.sort(String::compareTo);
        renderableStateInventory.sort(String::compareTo);
        rawStateIds.sort(Integer::compareTo);
        boolean itemModelPresent = client.getItemRenderer().getModel(
                new ItemStack(item),
                null,
                null,
                0
        ) != client.getBakedModelManager().getMissingModel();
        ResourceDigestProbe resourceDigests = inspectResources(client);
        boolean cutout = client.getBlockRenderManager()
                .getModel(defaultState)
                .getRenderTypes(defaultState, Random.create(42L), ModelData.EMPTY)
                .asList()
                .equals(List.of(RenderLayer.getCutout()));
        boolean stateInventoryExact = stateInventory.equals(expectedStateInventory());
        boolean rawIdsExact = rawStateIds.size() == 20
                && rawStateIds.stream().allMatch(rawId -> rawId >= 0)
                && rawStateIds.stream().distinct().count() == 20L;
        return new RegistryProbe(
                blockPresent,
                itemPresent,
                blockItemMapping,
                canonicalState(defaultState),
                stateInventory,
                renderableStateInventory,
                rawStateIds,
                resourceDigests.invalidResources(),
                resourceDigests.sha256ByResource(),
                cutout,
                bakedModelsComplete && itemModelPresent,
                defaultState.getLuminance(),
                stateInventoryExact,
                renderableStateInventory.equals(expectedStateInventory()),
                rawIdsExact
        );
    }

    private void setupInitialFixture(IntegratedServer server, UUID playerId) {
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
                    CAMERA_YAW,
                    CAMERA_PITCH
            );
            player.setSpawnPoint(World.OVERWORLD, CAMERA_BLOCK_POS, CAMERA_YAW, true, false);
            FixtureSnapshot snapshot = captureSnapshot(world);
            pendingServerSetupResult = new ServerSetupResult(
                    chunksLoaded,
                    supportsExact(snapshot),
                    snapshot,
                    server.getSaveProperties().getLevelName(),
                    world.getSeed(),
                    world.getRegistryKey().getValue().toString()
            );
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
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

    private void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-12, ARENA_FLOOR_Y, -12);
        BlockPos end = new BlockPos(12, ARENA_FLOOR_Y + 8, 7);
        for (BlockPos position : BlockPos.iterate(start, end)) {
            world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private void buildArena(ServerWorld world) {
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 7; z++) {
                Block floorBlock = (x + z) % 2 == 0
                        ? Blocks.SMOOTH_STONE
                        : Blocks.LIGHT_GRAY_CONCRETE;
                world.setBlockState(
                        new BlockPos(x, ARENA_FLOOR_Y, z),
                        floorBlock.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
        }

        Block stageSupportA = requiredBlock(STAGE_SUPPORT_A_ID);
        Block stageSupportB = requiredBlock(STAGE_SUPPORT_B_ID);
        for (StageFixture fixture : STAGE_FIXTURES) {
            Block backing = fixture.age() % 2 == 0 ? stageSupportA : stageSupportB;
            world.setBlockState(
                    fixture.supportPosition(),
                    backing.getDefaultState(),
                    Block.NOTIFY_ALL
            );
        }
        for (PlacementFixture fixture : PLACEMENT_FIXTURES) {
            world.setBlockState(
                    fixture.supportPosition(),
                    Blocks.POLISHED_ANDESITE.getDefaultState(),
                    Block.NOTIFY_ALL
            );
        }
        world.setBlockState(
                UNSUPPORTED_SUPPORT_POSITION,
                Blocks.IRON_BARS.getDefaultState(),
                Block.NOTIFY_ALL
        );
    }

    private void submitStageFixture(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            fail(client, "The integrated server vanished before forced stage setup");
            return;
        }
        submitMutation(client, CapturePhase.STAGES, () -> {
            ServerWorld world = server.getOverworld();
            Block forestLantern = requiredBlock(FOREST_LANTERN_ID);
            for (StageFixture fixture : STAGE_FIXTURES) {
                BlockState state = withStateProperty(
                        forestLantern.getDefaultState(),
                        "age",
                        Integer.toString(fixture.age())
                );
                state = withStateProperty(state, "facing", fixture.direction().asString());
                world.setBlockState(fixture.position(), state, Block.NOTIFY_ALL);
            }
            return new MutationResult(
                    CapturePhase.STAGES,
                    captureSnapshot(world),
                    null,
                    null
            );
        });
    }

    private void submitPlacement(MinecraftClient client, Direction direction) {
        IntegratedServer server = client.getServer();
        if (server == null || client.player == null) {
            fail(client, "The integrated server vanished before BlockItem placement");
            return;
        }
        UUID playerId = client.player.getUuid();
        CapturePhase nextPhase = CapturePhase.forDirection(direction);
        submitMutation(client, nextPhase, () -> {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = server.getPlayerManager()
                    .getPlayer(playerId);
            if (player == null) {
                throw new IllegalStateException("The integrated server lost its player");
            }

            PlacementEvidence unsupported = direction == Direction.NORTH
                    ? attemptPlacement(
                            world,
                            player,
                            Direction.NORTH,
                            UNSUPPORTED_POSITION,
                            UNSUPPORTED_SUPPORT_POSITION
                    )
                    : null;
            PlacementFixture fixture = placementFixture(direction);
            PlacementEvidence placement = attemptPlacement(
                    world,
                    player,
                    direction,
                    fixture.position(),
                    fixture.supportPosition()
            );
            return new MutationResult(
                    nextPhase,
                    captureSnapshot(world),
                    placement,
                    unsupported
            );
        });
    }

    private PlacementEvidence attemptPlacement(
            ServerWorld world,
            ServerPlayerEntity player,
            Direction direction,
            BlockPos target,
            BlockPos support
    ) {
        Block forestLantern = requiredBlock(FOREST_LANTERN_ID);
        Item item = requiredItem(FOREST_LANTERN_ID);
        boolean blockItemMapping = item instanceof BlockItem blockItem
                && blockItem.getBlock() == forestLantern;
        if (!(item instanceof BlockItem blockItem)) {
            throw new IllegalStateException("The Forest Lantern item is not a BlockItem");
        }

        ItemStack stack = new ItemStack(item);
        int beforeCount = stack.getCount();
        player.setStackInHand(Hand.MAIN_HAND, stack);
        Vec3d hitPosition = Vec3d.ofCenter(support).add(
                direction.getOffsetX() * 0.5,
                direction.getOffsetY() * 0.5,
                direction.getOffsetZ() * 0.5
        );
        BlockHitResult hit = new BlockHitResult(hitPosition, direction, support, false);
        ActionResult result = blockItem.useOnBlock(
                new ItemUsageContext(player, Hand.MAIN_HAND, hit)
        );
        int afterCount = stack.getCount();
        BlockObservation observation = observe(world, target);
        boolean supportValid = forestLantern.getDefaultState()
                .with(findDirectionProperty(forestLantern), direction)
                .canPlaceAt(world, target);
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        return new PlacementEvidence(
                direction,
                result.name(),
                result.isAccepted(),
                beforeCount,
                afterCount,
                blockItemMapping,
                supportValid,
                observation,
                Registries.BLOCK.getId(world.getBlockState(support).getBlock()).toString()
        );
    }

    private void submitMutation(
            MinecraftClient client,
            CapturePhase nextPhase,
            MutationOperation operation
    ) {
        if (mutationSubmitted) return;

        IntegratedServer server = client.getServer();
        if (server == null || !server.isRunning() || server.isStopping()) {
            fail(client, "The integrated server stopped before " + nextPhase.id());
            return;
        }
        mutationSubmitted = true;
        server.execute(() -> {
            try {
                pendingMutationResult = operation.run();
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            }
        });
        transition(Stage.WAITING_FOR_MUTATION);
    }

    private void submitSave(MinecraftClient client) {
        if (saveSubmitted) return;

        IntegratedServer server = client.getServer();
        if (server == null || !server.isRunning() || server.isStopping()) {
            fail(client, "The integrated server stopped before the Forest Lantern save");
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

        FixtureSnapshot snapshot = captureSnapshot(client.world);
        currentClientSnapshot = snapshot;
        clientSnapshots.put(capturePhase, snapshot);
        CaptureEvidence evidence = new CaptureEvidence(
                snapshot.equals(currentServerSnapshot),
                isFixtureRenderReady(client),
                hasExpectedCameraPose(client),
                completedRenders,
                client.getFramebuffer().textureWidth,
                client.getFramebuffer().textureHeight,
                cameraPoseDescription(client),
                null
        );
        captureEvidence.put(capturePhase, evidence);
        transition(Stage.CAPTURING);
        saveScreenshot(client, capturePhase.screenshotFileName());
    }

    private void saveScreenshot(MinecraftClient client, String fileName) {
        ForgeEvidenceLayout layout = evidenceLayout;
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
            return new ScreenshotResult(true, size, ForgeArtifactDigest.sha256(path), "");
        } catch (IOException exception) {
            return ScreenshotResult.failed(exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot inspect a Forest Lantern screenshot", exception);
            return ScreenshotResult.failed(
                    "Screenshot inspection raised " + exception.getClass().getSimpleName()
            );
        }
    }

    private boolean isCaptureStateExact(MinecraftClient client) {
        if (!isWorldViewReady(client)
                || currentServerSnapshot == null
                || !currentServerSnapshot.matchesPhase(capturePhase)
                || !isFixtureRenderReady(client)
                || !hasExpectedCameraPose(client)) {
            return false;
        }
        FixtureSnapshot clientSnapshot = captureSnapshot(client.world);
        return clientSnapshot.equals(currentServerSnapshot)
                && clientSnapshot.matchesPhase(capturePhase);
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

        for (StageFixture fixture : STAGE_FIXTURES) {
            if (!client.worldRenderer.isRenderingReady(fixture.position())
                    || !client.worldRenderer.isRenderingReady(fixture.supportPosition())) {
                return false;
            }
        }
        for (PlacementFixture fixture : PLACEMENT_FIXTURES) {
            if (!client.worldRenderer.isRenderingReady(fixture.position())
                    || !client.worldRenderer.isRenderingReady(fixture.supportPosition())) {
                return false;
            }
        }
        return client.worldRenderer.isRenderingReady(UNSUPPORTED_POSITION)
                && client.worldRenderer.isRenderingReady(UNSUPPORTED_SUPPORT_POSITION);
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

    private FixtureSnapshot captureSnapshot(WorldView world) {
        List<BlockObservation> stages = new ArrayList<>();
        List<String> stageSupports = new ArrayList<>();
        for (StageFixture fixture : STAGE_FIXTURES) {
            stages.add(observe(world, fixture.position()));
            stageSupports.add(
                    Registries.BLOCK.getId(
                            world.getBlockState(fixture.supportPosition()).getBlock()
                    ).toString()
            );
        }

        List<BlockObservation> placements = new ArrayList<>();
        List<String> placementSupports = new ArrayList<>();
        for (PlacementFixture fixture : PLACEMENT_FIXTURES) {
            placements.add(observe(world, fixture.position()));
            placementSupports.add(
                    Registries.BLOCK.getId(
                            world.getBlockState(fixture.supportPosition()).getBlock()
                    ).toString()
            );
        }
        return new FixtureSnapshot(
                List.copyOf(stages),
                List.copyOf(stageSupports),
                List.copyOf(placements),
                List.copyOf(placementSupports),
                observe(world, UNSUPPORTED_POSITION),
                Registries.BLOCK.getId(
                        world.getBlockState(UNSUPPORTED_SUPPORT_POSITION).getBlock()
                ).toString()
        );
    }

    private BlockObservation observe(WorldView world, BlockPos position) {
        BlockState state = world.getBlockState(position);
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        if (!FOREST_LANTERN_ID.toString().equals(blockId)) {
            return new BlockObservation(
                    positionDescription(position),
                    blockId,
                    "",
                    "",
                    false
            );
        }
        return new BlockObservation(
                positionDescription(position),
                blockId,
                statePropertyValue(state, "age"),
                statePropertyValue(state, "facing"),
                state.canPlaceAt(world, position)
        );
    }

    private boolean supportsExact(FixtureSnapshot snapshot) {
        List<String> expectedStageSupports = STAGE_FIXTURES.stream()
                .map(fixture -> fixture.age() % 2 == 0
                        ? STAGE_SUPPORT_A_ID.toString()
                        : STAGE_SUPPORT_B_ID.toString())
                .toList();
        return snapshot.stageSupportIds().equals(expectedStageSupports)
                && snapshot.placementSupportIds().stream()
                .allMatch(PLACEMENT_SUPPORT_ID.toString()::equals)
                && UNSUPPORTED_SUPPORT_ID.toString().equals(snapshot.unsupportedSupportId());
    }

    private ResourceDigestProbe inspectResources(MinecraftClient client) {
        List<Identifier> invalid = new ArrayList<>();
        Map<String, String> sha256ByResource = new LinkedHashMap<>();
        for (Identifier resource : READY_RESOURCES) {
            Resource resolved = client.getResourceManager().getResource(resource).orElse(null);
            if (resolved == null) {
                invalid.add(resource);
                continue;
            }
            try (InputStream input = resolved.getInputStream()) {
                String digest = sha256(input);
                sha256ByResource.put(resource.toString(), digest);
                if (!expectedResourceSha256(resource).equals(digest)) {
                    invalid.add(resource);
                }
            } catch (IOException exception) {
                invalid.add(resource);
            }
        }
        return new ResourceDigestProbe(
                List.copyOf(invalid),
                Map.copyOf(sha256ByResource)
        );
    }

    private static String sha256(InputStream input) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "This Java runtime has no SHA-256 implementation",
                    exception
            );
        }
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void recordServerFailure(RuntimeException exception) {
        LOGGER.error("Forest Lantern server operation failed", exception);
        serverFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;

        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Etherology forest-lantern lifecycle failure: {}", lifecycleFailure);
        publish(client);
    }

    private void publish(MinecraftClient client) {
        if (stage == Stage.COMPLETE) return;

        try {
            ensureForgeEvidenceLayout(client);
            List<ForgeArtifactDigest> artifactDigests = List.of(
                    ForgeArtifactDigest.capture("etherology"),
                    ForgeArtifactDigest.capture(HARNESS_MOD_ID)
            );
            JsonObject report = createReport(client, artifactDigests);
            AtomicEvidenceWriter.writeReportThenMarker(evidenceLayout, report);
            LOGGER.info(
                    "Etherology forest-lantern evidence is complete: {}",
                    evidenceLayout.reportsDirectory()
            );
        } catch (IOException exception) {
            LOGGER.error("Cannot atomically publish Forest Lantern evidence", exception);
        } finally {
            stage = Stage.COMPLETE;
            client.scheduleStop();
        }
    }

    private void ensureForgeEvidenceLayout(MinecraftClient client) throws IOException {
        if (evidenceLayout != null) return;

        ForgeEvidenceLayout layout = ForgeEvidenceLayout.resolve(
                client.runDirectory.toPath(),
                SCENARIO_ID
        );
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
            List<ForgeArtifactDigest> artifactDigests
    ) {
        JsonArray assertions = new JsonArray();
        boolean passed = lifecycleFailure.isEmpty();
        RegistryProbe probe = registryProbe;
        passed &= addAssertion(
                assertions,
                "forge_mod_loaded:etherology",
                ModList.get().isLoaded("etherology"),
                "loaded",
                ModList.get().isLoaded("etherology") ? "loaded" : "missing"
        );
        passed &= addAssertion(
                assertions,
                "registry:block:etherology:forest_lantern",
                probe != null && probe.blockPresent(),
                "present",
                probe != null && probe.blockPresent() ? "present" : "missing"
        );
        passed &= addAssertion(
                assertions,
                "registry:item:etherology:forest_lantern",
                probe != null && probe.itemPresent(),
                "present",
                probe != null && probe.itemPresent() ? "present" : "missing"
        );
        passed &= addAssertion(
                assertions,
                "block_item_mapping",
                probe != null && probe.blockItemMapping(),
                "vanilla BlockItem mapped to etherology:forest_lantern",
                probe != null && probe.blockItemMapping()
                        ? "vanilla BlockItem mapped to etherology:forest_lantern"
                        : "mismatch"
        );
        passed &= addAssertion(
                assertions,
                "default_state_exact",
                probe != null && "age=4,facing=north".equals(probe.defaultState()),
                "age=4,facing=north",
                probe == null ? "not inspected" : probe.defaultState()
        );
        passed &= addAssertion(
                assertions,
                "state_inventory_exact",
                probe != null && probe.stateInventoryExact(),
                expectedStateInventory().toString(),
                probe == null ? "not inspected" : probe.stateInventory().toString()
        );
        passed &= addAssertion(
                assertions,
                "state_network_ids_exact",
                probe != null && probe.rawIdsExact(),
                "20 unique non-negative raw IDs",
                probe == null ? "not inspected" : probe.rawStateIds().toString()
        );
        passed &= addAssertion(
                assertions,
                "client_render_resources",
                probe != null && probe.invalidResources().isEmpty(),
                "13 exact resources with canonical SHA-256 digests",
                probe == null || !probe.invalidResources().isEmpty()
                        ? "invalid=" + (probe == null ? "not inspected" : probe.invalidResources())
                        : "13 exact resources with canonical SHA-256 digests"
        );
        passed &= addAssertion(
                assertions,
                "client_render_layer_cutout",
                probe != null && probe.cutout(),
                "cutout",
                probe != null && probe.cutout() ? "cutout" : "not cutout"
        );
        passed &= addAssertion(
                assertions,
                "client_models_baked",
                probe != null
                        && probe.bakedModelsComplete()
                        && probe.renderableStateInventoryExact(),
                "20 block states with non-empty baked geometry and one item model",
                probe != null
                        && probe.bakedModelsComplete()
                        && probe.renderableStateInventoryExact()
                        ? "20 block states with non-empty baked geometry and one item model"
                        : "missing model"
        );
        passed &= addAssertion(
                assertions,
                "default_luminance",
                probe != null && probe.luminance() == 8,
                "8",
                probe == null ? "not inspected" : Integer.toString(probe.luminance())
        );
        for (ForgeArtifactDigest digest : artifactDigests) {
            passed &= addAssertion(
                    assertions,
                    "packaged_root_jar:" + digest.modId(),
                    digest.passed(),
                    "one regular root JAR",
                    digest.passed() ? "one regular root JAR" : digest.failure()
            );
        }

        ServerSetupResult setup = serverSetupResult;
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
                setup != null && setup.chunksLoaded(),
                "four full chunks",
                setup == null ? "missing setup" : Boolean.toString(setup.chunksLoaded())
        );
        passed &= addAssertion(
                assertions,
                "checker_backings_exact",
                setup != null && setup.supportsExact(),
                "alternating vanilla logs, four polished-andesite supports, "
                        + "one iron-bars rejection support",
                setup != null && setup.supportsExact() ? "exact" : "mismatch"
        );
        FixtureSnapshot stagesSnapshot = serverSnapshots.get(CapturePhase.STAGES);
        passed &= addAssertion(
                assertions,
                "forced_stage_ages_exact",
                stagesSnapshot != null && stagesSnapshot.matchesPhase(CapturePhase.STAGES),
                "forced ages 0,1,2,3 across north,east,south,west",
                stagesSnapshot == null ? "missing" : stagesSnapshot.stageSummary()
        );
        passed &= addAssertion(
                assertions,
                "forced_immature_support_contract",
                stagesSnapshot != null
                        && stagesSnapshot.hasExpectedStageSupportValidity(),
                "all 16 forced age/facing states invalid without deferred peach logs",
                stagesSnapshot == null
                        ? "missing"
                        : Boolean.toString(
                                stagesSnapshot.hasExpectedStageSupportValidity()
                        )
        );
        passed &= addAssertion(
                assertions,
                "unsupported_block_item_rejected",
                unsupportedPlacementEvidence != null
                        && unsupportedPlacementEvidence.exactRejected(),
                "not accepted; stack 1->1; target air; support invalid",
                unsupportedPlacementEvidence == null
                        ? "missing"
                        : unsupportedPlacementEvidence.summary()
        );
        for (PlacementFixture fixture : PLACEMENT_FIXTURES) {
            PlacementEvidence placement = placementEvidence.get(fixture.direction());
            passed &= addAssertion(
                    assertions,
                    "real_block_item_placement:" + fixture.direction().asString(),
                    placement != null && placement.exactAccepted(),
                    "accepted BlockItem; stack 1->0; mature exact facing; support valid",
                    placement == null ? "missing" : placement.summary()
            );
        }
        FixtureSnapshot facingWestServer = serverSnapshots.get(CapturePhase.FACING_WEST);
        FixtureSnapshot facingWestClient = clientSnapshots.get(CapturePhase.FACING_WEST);
        FixtureSnapshot reopenedServer = serverSnapshots.get(CapturePhase.REOPENED);
        FixtureSnapshot reopenedClient = clientSnapshots.get(CapturePhase.REOPENED);
        boolean nativeStateMatrixExact = facingWestServer != null
                && facingWestServer.hasExactNativeStateInventory()
                && facingWestServer.equals(facingWestClient)
                && reopenedServer != null
                && reopenedServer.hasExactNativeStateInventory()
                && reopenedServer.equals(reopenedClient)
                && facingWestServer.equals(reopenedServer);
        passed &= addAssertion(
                assertions,
                "native_twenty_state_matrix_exact",
                nativeStateMatrixExact,
                "20 unique native server/client age/facing states before save and after reopen",
                reopenedServer == null
                        ? "missing"
                        : reopenedServer.nativeStateInventory().toString()
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
                "restart_exact_state",
                persistenceExact,
                "saved snapshot equals reopened snapshot",
                persistenceExact ? "saved snapshot equals reopened snapshot" : "mismatch"
        );

        for (CapturePhase phase : CapturePhase.values()) {
            CaptureEvidence capture = captureEvidence.get(phase);
            FixtureSnapshot serverSnapshot = serverSnapshots.get(phase);
            FixtureSnapshot clientSnapshot = clientSnapshots.get(phase);
            boolean mirrorExact = capture != null
                    && capture.mirrorExact()
                    && serverSnapshot != null
                    && serverSnapshot.equals(clientSnapshot)
                    && serverSnapshot.matchesPhase(phase);
            passed &= addAssertion(
                    assertions,
                    "capture_mirror_exact:" + phase.id(),
                    mirrorExact,
                    phase.expectedSummary(),
                    clientSnapshot == null ? "missing" : clientSnapshot.summary()
            );
            passed &= addAssertion(
                    assertions,
                    "capture_render_ready:" + phase.id(),
                    capture != null && capture.renderReady(),
                    "terrain complete and all fixture positions rendering-ready",
                    capture != null && capture.renderReady() ? "ready" : "not ready"
            );
            passed &= addAssertion(
                    assertions,
                    "capture_camera_exact:" + phase.id(),
                    capture != null && capture.cameraExact(),
                    expectedCameraPoseDescription(),
                    capture == null ? "not captured" : capture.cameraPose()
            );
            passed &= addAssertion(
                    assertions,
                    "capture_consecutive_stable_renders:" + phase.id(),
                    capture != null
                            && capture.stableRenders() == REQUIRED_COMPLETED_RENDERS,
                    Integer.toString(REQUIRED_COMPLETED_RENDERS),
                    capture == null ? "0" : Integer.toString(capture.stableRenders())
            );
            passed &= addAssertion(
                    assertions,
                    "capture_framebuffer_dimensions:" + phase.id(),
                    capture != null
                            && capture.width() == FRAMEBUFFER_WIDTH
                            && capture.height() == FRAMEBUFFER_HEIGHT,
                    framebufferDescription(FRAMEBUFFER_WIDTH, FRAMEBUFFER_HEIGHT),
                    capture == null
                            ? "0x0"
                            : framebufferDescription(capture.width(), capture.height())
            );
            passed &= addScreenshotAssertion(
                    assertions,
                    "native_screenshot_written:" + phase.id(),
                    capture == null ? null : capture.screenshot()
            );
        }

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

        if (!assertionNames(assertions).equals(ASSERTION_NAMES)) {
            throw new IllegalStateException("Forest Lantern assertion inventory drifted");
        }
        JsonObject report = new JsonObject();
        report.addProperty("schema", 2);
        report.addProperty("scenario", SCENARIO_ID);
        report.addProperty("profile_id", evidenceLayout.profileId());
        report.addProperty("profile_manifest_size", evidenceLayout.profileManifestSize());
        report.addProperty("profile_manifest_sha256", evidenceLayout.profileManifestSha256());
        report.addProperty("artifact_node", "forge-1.20.1");
        report.addProperty("minecraft", "1.20.1");
        report.addProperty("loader", "forge");
        report.addProperty("loader_version", "47.4.9");
        report.addProperty("java", 17);
        report.addProperty("lane", "forge-1.20.1");
        report.addProperty("role", "host");
        report.addProperty("status", passed ? "passed" : "failed");
        report.addProperty("passed", passed);
        report.addProperty("client_ticks", clientTicks);
        report.addProperty("framebuffer_width", client.getFramebuffer().textureWidth);
        report.addProperty("framebuffer_height", client.getFramebuffer().textureHeight);
        report.addProperty("lifecycle_failure", lifecycleFailure);
        report.add("assertions", assertions);
        report.add("world", createWorldReport(client, setup));
        report.add("ready_resources", createReadyResourcesReport());
        report.add("artifacts", createArtifactsReport(artifactDigests));
        report.add("screenshots", createScreenshotsReport());
        report.add("forest_lantern", createForestLanternReport());
        return report;
    }

    private JsonObject createWorldReport(
            MinecraftClient client,
            ServerSetupResult setup
    ) {
        JsonObject world = new JsonObject();
        world.addProperty("save_directory", WORLD_DIRECTORY_NAME);
        world.addProperty("display_name", setup == null ? "" : setup.worldDisplayName());
        world.addProperty("seed", setup == null ? Long.MIN_VALUE : setup.worldSeed());
        world.addProperty("dimension", setup == null ? "" : setup.dimensionId());
        world.addProperty("integrated", setup != null && client.getServer() != null);
        world.addProperty("reopened", reopenedSnapshot != null);
        return world;
    }

    private JsonArray createReadyResourcesReport() {
        JsonArray resources = new JsonArray();
        for (Identifier resource : READY_RESOURCES) {
            resources.add(resource.toString());
        }
        return resources;
    }

    private JsonArray createArtifactsReport(List<ForgeArtifactDigest> artifactDigests) {
        JsonArray artifacts = new JsonArray();
        for (ForgeArtifactDigest digest : artifactDigests) {
            JsonObject artifact = new JsonObject();
            artifact.addProperty("mod_id", digest.modId());
            artifact.addProperty("passed", digest.passed());
            artifact.addProperty("file_name", digest.fileName());
            artifact.addProperty("size", digest.size());
            artifact.addProperty("sha256", digest.sha256());
            artifact.addProperty("failure", digest.failure());
            artifacts.add(artifact);
        }
        return artifacts;
    }

    private JsonArray createScreenshotsReport() {
        JsonArray screenshots = new JsonArray();
        for (CapturePhase phase : CapturePhase.values()) {
            CaptureEvidence capture = captureEvidence.get(phase);
            if (capture == null || capture.screenshot() == null
                    || !capture.screenshot().passed()) {
                continue;
            }
            ScreenshotResult screenshotResult = capture.screenshot();
            JsonObject screenshot = new JsonObject();
            screenshot.addProperty("step", phase.id());
            screenshot.addProperty("role", "host");
            screenshot.addProperty(
                    "file",
                    "screenshots/" + phase.screenshotFileName()
            );
            screenshot.addProperty("width", capture.width());
            screenshot.addProperty("height", capture.height());
            screenshot.addProperty("size", screenshotResult.size());
            screenshot.addProperty("sha256", screenshotResult.sha256());
            screenshot.addProperty("completed_render_count", capture.stableRenders());
            screenshot.addProperty("source", "minecraft-framebuffer");
            screenshot.addProperty("edited", false);
            screenshots.add(screenshot);
        }
        return screenshots;
    }

    private JsonObject createForestLanternReport() {
        JsonObject forestLantern = new JsonObject();
        RegistryProbe probe = registryProbe;
        forestLantern.addProperty("registry_id", FOREST_LANTERN_ID.toString());
        forestLantern.addProperty("item_id", FOREST_LANTERN_ID.toString());
        forestLantern.addProperty("block_item_mapping", probe != null && probe.blockItemMapping());
        forestLantern.addProperty("default_state", probe == null ? "" : probe.defaultState());
        forestLantern.addProperty("state_count", probe == null ? 0 : probe.stateInventory().size());
        JsonArray stateInventory = new JsonArray();
        if (probe != null) {
            for (String state : probe.stateInventory()) stateInventory.add(state);
        }
        forestLantern.add("state_inventory", stateInventory);
        JsonArray rawStateIds = new JsonArray();
        if (probe != null) {
            for (int rawId : probe.rawStateIds()) rawStateIds.add(rawId);
        }
        forestLantern.add("raw_state_ids", rawStateIds);
        forestLantern.addProperty("render_layer", probe != null && probe.cutout()
                ? "cutout"
                : "unexpected");
        forestLantern.addProperty(
                "models_baked",
                probe != null
                        && probe.bakedModelsComplete()
                        && probe.renderableStateInventoryExact()
        );
        JsonArray renderableStateInventory = new JsonArray();
        if (probe != null) {
            for (String state : probe.renderableStateInventory()) {
                renderableStateInventory.add(state);
            }
        }
        forestLantern.add("renderable_state_inventory", renderableStateInventory);
        forestLantern.addProperty("luminance", probe == null ? -1 : probe.luminance());
        JsonObject assetDigests = new JsonObject();
        if (probe != null) {
            for (Identifier resource : READY_RESOURCES) {
                assetDigests.addProperty(
                        resource.toString(),
                        probe.resourceSha256().getOrDefault(resource.toString(), "")
                );
            }
        }
        forestLantern.add("asset_sha256", assetDigests);
        JsonArray forcedAges = new JsonArray();
        for (int age = 0; age < 4; age++) forcedAges.add(age);
        forestLantern.add("forced_stage_ages", forcedAges);
        forestLantern.add("stage_fixtures", createStageFixturesReport());
        forestLantern.add("placement_fixtures", createPlacementFixturesReport());
        forestLantern.add(
                "unsupported_placement",
                createPlacementEvidenceReport(unsupportedPlacementEvidence)
        );
        JsonObject placements = new JsonObject();
        for (PlacementFixture fixture : PLACEMENT_FIXTURES) {
            placements.add(
                    fixture.direction().asString(),
                    createPlacementEvidenceReport(placementEvidence.get(fixture.direction()))
            );
        }
        forestLantern.add("placements", placements);
        forestLantern.addProperty("persistence_exact", persistenceExact);
        JsonObject camera = new JsonObject();
        camera.addProperty("x", CAMERA_X);
        camera.addProperty("y", CAMERA_Y);
        camera.addProperty("z", CAMERA_Z);
        camera.addProperty("yaw", CAMERA_YAW);
        camera.addProperty("pitch", CAMERA_PITCH);
        camera.addProperty("first_person", true);
        camera.addProperty("on_ground", true);
        forestLantern.add("camera", camera);
        forestLantern.addProperty("required_stable_renders", REQUIRED_COMPLETED_RENDERS);
        JsonObject captures = new JsonObject();
        for (CapturePhase phase : CapturePhase.values()) {
            captures.add(phase.id(), createCaptureReport(phase));
        }
        forestLantern.add("captures", captures);
        return forestLantern;
    }

    private JsonArray createStageFixturesReport() {
        JsonArray fixtures = new JsonArray();
        for (StageFixture fixture : STAGE_FIXTURES) {
            JsonObject row = new JsonObject();
            row.addProperty("age", fixture.age());
            row.addProperty("forced", true);
            row.addProperty("facing", fixture.direction().asString());
            row.addProperty("position", positionDescription(fixture.position()));
            row.addProperty("support_position", positionDescription(fixture.supportPosition()));
            row.addProperty(
                    "support_id",
                    fixture.age() % 2 == 0
                            ? STAGE_SUPPORT_A_ID.toString()
                            : STAGE_SUPPORT_B_ID.toString()
            );
            fixtures.add(row);
        }
        return fixtures;
    }

    private JsonArray createPlacementFixturesReport() {
        JsonArray fixtures = new JsonArray();
        for (PlacementFixture fixture : PLACEMENT_FIXTURES) {
            JsonObject row = new JsonObject();
            row.addProperty("facing", fixture.direction().asString());
            row.addProperty("position", positionDescription(fixture.position()));
            row.addProperty("support_position", positionDescription(fixture.supportPosition()));
            row.addProperty("support_id", PLACEMENT_SUPPORT_ID.toString());
            fixtures.add(row);
        }
        return fixtures;
    }

    private JsonObject createPlacementEvidenceReport(PlacementEvidence evidence) {
        JsonObject placement = new JsonObject();
        if (evidence == null) {
            placement.addProperty("direction", "");
            placement.addProperty("action_result", "");
            placement.addProperty("accepted", false);
            placement.addProperty("stack_before", 0);
            placement.addProperty("stack_after", 0);
            placement.addProperty("block_item_mapping", false);
            placement.addProperty("support_valid", false);
            placement.add("observation", createObservationReport(null));
            placement.addProperty("support_id", "");
            return placement;
        }
        placement.addProperty("direction", evidence.direction().asString());
        placement.addProperty("action_result", evidence.actionResult());
        placement.addProperty("accepted", evidence.accepted());
        placement.addProperty("stack_before", evidence.stackBefore());
        placement.addProperty("stack_after", evidence.stackAfter());
        placement.addProperty("block_item_mapping", evidence.blockItemMapping());
        placement.addProperty("support_valid", evidence.supportValid());
        placement.add("observation", createObservationReport(evidence.observation()));
        placement.addProperty("support_id", evidence.supportId());
        return placement;
    }

    private JsonObject createCaptureReport(CapturePhase phase) {
        CaptureEvidence capture = captureEvidence.get(phase);
        JsonObject report = new JsonObject();
        report.addProperty("mirror_exact", capture != null && capture.mirrorExact());
        report.addProperty("render_ready", capture != null && capture.renderReady());
        report.addProperty("camera_exact", capture != null && capture.cameraExact());
        report.addProperty("stable_renders", capture == null ? 0 : capture.stableRenders());
        report.addProperty(
                "framebuffer",
                capture == null
                        ? "0x0"
                        : framebufferDescription(capture.width(), capture.height())
        );
        report.add("server_snapshot", createSnapshotReport(serverSnapshots.get(phase)));
        report.add("client_snapshot", createSnapshotReport(clientSnapshots.get(phase)));
        return report;
    }

    private JsonObject createSnapshotReport(FixtureSnapshot snapshot) {
        JsonObject report = new JsonObject();
        JsonArray stages = new JsonArray();
        JsonArray placements = new JsonArray();
        JsonArray stageSupports = new JsonArray();
        JsonArray placementSupports = new JsonArray();
        if (snapshot != null) {
            for (BlockObservation observation : snapshot.stages()) {
                stages.add(createObservationReport(observation));
            }
            for (BlockObservation observation : snapshot.placements()) {
                placements.add(createObservationReport(observation));
            }
            for (String id : snapshot.stageSupportIds()) stageSupports.add(id);
            for (String id : snapshot.placementSupportIds()) placementSupports.add(id);
        }
        report.add("stages", stages);
        report.add("stage_support_ids", stageSupports);
        report.add("placements", placements);
        report.add("placement_support_ids", placementSupports);
        report.add(
                "unsupported_target",
                createObservationReport(snapshot == null ? null : snapshot.unsupportedTarget())
        );
        report.addProperty(
                "unsupported_support_id",
                snapshot == null ? "" : snapshot.unsupportedSupportId()
        );
        return report;
    }

    private JsonObject createObservationReport(BlockObservation observation) {
        JsonObject report = new JsonObject();
        report.addProperty("position", observation == null ? "" : observation.position());
        report.addProperty("block_id", observation == null ? "" : observation.blockId());
        report.addProperty("age", observation == null ? "" : observation.age());
        report.addProperty("facing", observation == null ? "" : observation.facing());
        report.addProperty(
                "can_place_at",
                observation != null && observation.canPlaceAt()
        );
        return report;
    }

    private boolean addScreenshotAssertion(
            JsonArray assertions,
            String name,
            ScreenshotResult result
    ) {
        return addAssertion(
                assertions,
                name,
                result != null && result.passed(),
                "one non-empty unedited framebuffer PNG",
                result == null
                        ? "missing"
                        : result.size() + " bytes, sha256=" + result.sha256()
        );
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
        assertions.forEach(element -> names.add(
                element.getAsJsonObject().get("name").getAsString()
        ));
        return List.copyOf(names);
    }

    private void transition(Stage nextStage) {
        stage = nextStage;
        stageClientTicks = 0;
    }

    private static String canonicalState(BlockState state) {
        return "age=" + statePropertyValue(state, "age")
                + ",facing=" + statePropertyValue(state, "facing");
    }

    private static BlockState withStateProperty(
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

    private static <T extends Comparable<T>> BlockState withParsedStateProperty(
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

    @SuppressWarnings("unchecked")
    private static Property<Direction> findDirectionProperty(Block block) {
        Property<?> property = block.getStateManager().getProperty("facing");
        if (property == null || !property.getValues().contains(Direction.NORTH)) {
            throw new IllegalStateException("Forest Lantern has no horizontal facing property");
        }
        return (Property<Direction>) property;
    }

    private static String statePropertyValue(BlockState state, String propertyName) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName())) {
                return state.get(property).toString();
            }
        }
        throw new IllegalStateException("Block state has no property named " + propertyName);
    }

    private static Block requiredBlock(Identifier id) {
        if (!Registries.BLOCK.containsId(id)) {
            throw new IllegalStateException("Missing required block " + id);
        }
        return Registries.BLOCK.get(id);
    }

    private static Item requiredItem(Identifier id) {
        if (!Registries.ITEM.containsId(id)) {
            throw new IllegalStateException("Missing required item " + id);
        }
        return Registries.ITEM.get(id);
    }

    private static PlacementFixture placementFixture(Direction direction) {
        return PLACEMENT_FIXTURES.stream()
                .filter(fixture -> fixture.direction() == direction)
                .findFirst()
                .orElseThrow();
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

    private static List<StageFixture> createStageFixtures() {
        List<StageFixture> fixtures = new ArrayList<>();
        List<Direction> directions = List.of(
                Direction.NORTH,
                Direction.EAST,
                Direction.SOUTH,
                Direction.WEST
        );
        List<Integer> yOffsets = List.of(2, 4, 6, 8);
        List<Integer> xPositions = List.of(-6, -2, 2, 6);
        for (int directionIndex = 0; directionIndex < directions.size(); directionIndex++) {
            Direction direction = directions.get(directionIndex);
            int y = ARENA_FLOOR_Y + yOffsets.get(directionIndex);
            for (int age = 0; age < 4; age++) {
                BlockPos position = new BlockPos(xPositions.get(age), y, 2);
                fixtures.add(new StageFixture(
                        age,
                        direction,
                        position,
                        position.offset(direction.getOpposite())
                ));
            }
        }
        return List.copyOf(fixtures);
    }

    private static PlacementFixture placementFixture(Direction direction, int x) {
        BlockPos position = new BlockPos(x, ARENA_FLOOR_Y + 5, 2);
        return new PlacementFixture(
                direction,
                position,
                position.offset(direction.getOpposite())
        );
    }

    private static List<String> expectedStateInventory() {
        List<String> states = new ArrayList<>();
        for (int age = 0; age <= 4; age++) {
            for (String facing : List.of("east", "north", "south", "west")) {
                states.add("age=" + age + ",facing=" + facing);
            }
        }
        states.sort(String::compareTo);
        return List.copyOf(states);
    }

    private static boolean hasBakedGeometry(BakedModel model, BlockState state) {
        Random random = Random.create(WORLD_SEED);
        if (!model.getQuads(state, null, random).isEmpty()) return true;

        for (Direction direction : Direction.values()) {
            random.setSeed(WORLD_SEED);
            if (!model.getQuads(state, direction, random).isEmpty()) return true;
        }
        return false;
    }

    static List<String> expectedFixtureStateInventory() {
        List<String> states = new ArrayList<>();
        for (StageFixture fixture : STAGE_FIXTURES) {
            states.add(
                    "age=" + fixture.age() + ",facing=" + fixture.direction().asString()
            );
        }
        for (PlacementFixture fixture : PLACEMENT_FIXTURES) {
            states.add("age=4,facing=" + fixture.direction().asString());
        }
        states.sort(String::compareTo);
        return List.copyOf(states);
    }

    private static List<Identifier> createReadyResources() {
        return List.of(
                Identifier.of("etherology", "blockstates/forest_lantern.json"),
                Identifier.of("etherology", "models/block/forest_lantern_0.json"),
                Identifier.of("etherology", "models/block/forest_lantern_1.json"),
                Identifier.of("etherology", "models/block/forest_lantern_2.json"),
                Identifier.of("etherology", "models/block/forest_lantern_3.json"),
                Identifier.of("etherology", "models/block/forest_lantern.json"),
                Identifier.of("etherology", "models/item/forest_lantern.json"),
                Identifier.of("etherology", "textures/block/forest_lantern_0.png"),
                Identifier.of("etherology", "textures/block/forest_lantern_1.png"),
                Identifier.of("etherology", "textures/block/forest_lantern_2.png"),
                Identifier.of("etherology", "textures/block/forest_lantern_3.png"),
                Identifier.of("etherology", "textures/block/forest_lantern.png"),
                Identifier.of("etherology", "textures/item/forest_lantern.png")
        );
    }

    private static String expectedResourceSha256(Identifier resource) {
        return switch (resource.getPath()) {
            case "blockstates/forest_lantern.json" ->
                    "2e73ec0f61ca32b3e5331c2410362ae2df599d41a5d1499cf2fc84de5dcf74e3";
            case "models/block/forest_lantern_0.json" ->
                    "ff1afef383ebf725c102a11c36a4bd7671632841bfbdcd788a81824dcbb3af44";
            case "models/block/forest_lantern_1.json" ->
                    "026b26bfba8c8cec6de47c6dc6e3b097b66e07be30204ff3a7cad420cd9c349d";
            case "models/block/forest_lantern_2.json" ->
                    "a16895bea5f381c16c9454b349d172e527d3be2d639d2d8b05505d9a37a6e709";
            case "models/block/forest_lantern_3.json" ->
                    "6d57d90558f8c270a6a6f00ffb43e4f66f69d2042bc0bb18cd34de38285dde8d";
            case "models/block/forest_lantern.json" ->
                    "c45939dd725b11d2034406e3ec8b9040a97d6fa9c7c83d3782444a22e7cfd90c";
            case "models/item/forest_lantern.json" ->
                    "d57283548233724975a2f7d9aeeee41a00df0c0d73b02c314da8829aa6ab3e34";
            case "textures/block/forest_lantern_0.png" ->
                    "c850de55787124203c0176cf43364ad7459418ee401523cb71b733e58eff97a2";
            case "textures/block/forest_lantern_1.png" ->
                    "bc9ce2c1e3c310e81c324326b5828924319ebb5c7d23e64fbb9fbf883e930c7f";
            case "textures/block/forest_lantern_2.png" ->
                    "5e5d5df51ad75e06b99d3dd84bca8516c6b17b90948217a7a9624321ad1a9d1c";
            case "textures/block/forest_lantern_3.png" ->
                    "f4a76d42b1f7ca0106698ea56ee5045012b33c1a1aa1d433e202b8f1433756cd";
            case "textures/block/forest_lantern.png" ->
                    "5ab9532f8b9090492a84479b404c4c3c4ac3733a6d867e50534f54fcc8f310b6";
            case "textures/item/forest_lantern.png" ->
                    "38bd46a7cb5b35dd28ee2b6ff718c190596f1a2b95a3138f90b6fa19fce7d143";
            default -> throw new IllegalArgumentException(
                    "Unexpected Forest Lantern resource " + resource
            );
        };
    }

    private static List<String> createAssertionNames() {
        List<String> names = new ArrayList<>(List.of(
                "forge_mod_loaded:etherology",
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
                "forced_stage_ages_exact",
                "forced_immature_support_contract",
                "unsupported_block_item_rejected",
                "real_block_item_placement:north",
                "real_block_item_placement:east",
                "real_block_item_placement:south",
                "real_block_item_placement:west",
                "native_twenty_state_matrix_exact",
                "forced_world_save",
                "restart_exact_state"
        ));
        for (CapturePhase phase : CapturePhase.values()) {
            names.add("capture_mirror_exact:" + phase.id());
            names.add("capture_render_ready:" + phase.id());
            names.add("capture_camera_exact:" + phase.id());
            names.add("capture_consecutive_stable_renders:" + phase.id());
            names.add("capture_framebuffer_dimensions:" + phase.id());
            names.add("native_screenshot_written:" + phase.id());
        }
        names.add("isolated_save_directory_present");
        return List.copyOf(names);
    }

    private static List<String> createScreenshotFileNames() {
        List<String> names = new ArrayList<>();
        for (CapturePhase phase : CapturePhase.values()) {
            names.add(phase.screenshotFileName());
        }
        return List.copyOf(names);
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_SERVER_SETUP,
        WAITING_FOR_CLIENT_MIRROR,
        WAITING_FOR_RENDERS,
        CAPTURING,
        WAITING_FOR_MUTATION,
        SAVING_WORLD,
        DISCONNECTING,
        WAITING_FOR_RESTART_TITLE,
        RESTARTING_WORLD,
        WAITING_FOR_RESTART_WORLD,
        WAITING_FOR_RESTART_INSPECTION,
        COMPLETE
    }

    private enum CapturePhase {
        EMPTY("empty", "forest-lantern-empty.png", 0),
        STAGES("stages", "forest-lantern-stages.png", 0),
        FACING_NORTH("facing-north", "forest-lantern-facing-north.png", 1),
        FACING_EAST("facing-east", "forest-lantern-facing-east.png", 2),
        FACING_SOUTH("facing-south", "forest-lantern-facing-south.png", 3),
        FACING_WEST("facing-west", "forest-lantern-facing-west.png", 4),
        REOPENED("reopened", "forest-lantern-reopened.png", 4);

        private final String id;
        private final String screenshotFileName;
        private final int placedCount;

        CapturePhase(String id, String screenshotFileName, int placedCount) {
            this.id = id;
            this.screenshotFileName = screenshotFileName;
            this.placedCount = placedCount;
        }

        private String id() {
            return id;
        }

        private String screenshotFileName() {
            return screenshotFileName;
        }

        private int placedCount() {
            return placedCount;
        }

        private boolean hasStages() {
            return this != EMPTY;
        }

        private String expectedSummary() {
            return "stages=" + (hasStages()
                    ? "ages0-3:north,east,south,west"
                    : "air")
                    + ";mature_placements=" + placedCount
                    + ";unsupported=air";
        }

        private static CapturePhase forDirection(Direction direction) {
            return switch (direction) {
                case NORTH -> FACING_NORTH;
                case EAST -> FACING_EAST;
                case SOUTH -> FACING_SOUTH;
                case WEST -> FACING_WEST;
                default -> throw new IllegalArgumentException(
                        "Forest Lantern placement is not horizontal: " + direction
                );
            };
        }
    }

    private record StageFixture(
            int age,
            Direction direction,
            BlockPos position,
            BlockPos supportPosition
    ) {
    }

    private record PlacementFixture(
            Direction direction,
            BlockPos position,
            BlockPos supportPosition
    ) {
    }

    private record RegistryProbe(
            boolean blockPresent,
            boolean itemPresent,
            boolean blockItemMapping,
            String defaultState,
            List<String> stateInventory,
            List<String> renderableStateInventory,
            List<Integer> rawStateIds,
            List<Identifier> invalidResources,
            Map<String, String> resourceSha256,
            boolean cutout,
            boolean bakedModelsComplete,
            int luminance,
            boolean stateInventoryExact,
            boolean renderableStateInventoryExact,
            boolean rawIdsExact
    ) {

        private boolean exact() {
            return blockPresent
                    && itemPresent
                    && blockItemMapping
                    && "age=4,facing=north".equals(defaultState)
                    && invalidResources.isEmpty()
                    && cutout
                    && bakedModelsComplete
                    && renderableStateInventoryExact
                    && luminance == 8
                    && stateInventoryExact
                    && rawIdsExact;
        }

        private static RegistryProbe failed(boolean blockPresent, boolean itemPresent) {
            return new RegistryProbe(
                    blockPresent,
                    itemPresent,
                    false,
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    READY_RESOURCES,
                    Map.of(),
                    false,
                    false,
                    -1,
                    false,
                    false,
                    false
            );
        }
    }

    private record ResourceDigestProbe(
            List<Identifier> invalidResources,
            Map<String, String> sha256ByResource
    ) {
    }

    private record BlockObservation(
            String position,
            String blockId,
            String age,
            String facing,
            boolean canPlaceAt
    ) {

        private boolean matchesAir() {
            return AIR_ID.toString().equals(blockId)
                    && age.isEmpty()
                    && facing.isEmpty()
                    && !canPlaceAt;
        }

        private boolean matchesLantern(
                int expectedAge,
                Direction expectedFacing,
                boolean expectedCanPlaceAt
        ) {
            return FOREST_LANTERN_ID.toString().equals(blockId)
                    && Integer.toString(expectedAge).equals(age)
                    && expectedFacing.asString().equals(facing)
                    && canPlaceAt == expectedCanPlaceAt;
        }

        private String stateSummary() {
            if (matchesAir()) return position + "=minecraft:air";
            return position + "=" + blockId + "[age=" + age + ",facing=" + facing
                    + ",can_place_at=" + canPlaceAt + "]";
        }
    }

    private record FixtureSnapshot(
            List<BlockObservation> stages,
            List<String> stageSupportIds,
            List<BlockObservation> placements,
            List<String> placementSupportIds,
            BlockObservation unsupportedTarget,
            String unsupportedSupportId
    ) {

        private boolean matchesPhase(CapturePhase phase) {
            if (stages.size() != STAGE_FIXTURES.size()
                    || placements.size() != PLACEMENT_FIXTURES.size()
                    || stageSupportIds.size() != STAGE_FIXTURES.size()
                    || placementSupportIds.size() != PLACEMENT_FIXTURES.size()) {
                return false;
            }
            for (int index = 0; index < stages.size(); index++) {
                StageFixture fixture = STAGE_FIXTURES.get(index);
                boolean matches = phase.hasStages()
                        ? stages.get(index).matchesLantern(
                                fixture.age(),
                                fixture.direction(),
                                false
                        )
                        : stages.get(index).matchesAir();
                if (!matches) return false;
            }
            for (int index = 0; index < placements.size(); index++) {
                boolean matches = index < phase.placedCount()
                        ? placements.get(index).matchesLantern(
                                4,
                                PLACEMENT_FIXTURES.get(index).direction(),
                                true
                        )
                        : placements.get(index).matchesAir();
                if (!matches) return false;
            }
            return unsupportedTarget.matchesAir()
                    && stageSupportIds.equals(expectedStageSupportIds())
                    && placementSupportIds.stream()
                    .allMatch(PLACEMENT_SUPPORT_ID.toString()::equals)
                    && UNSUPPORTED_SUPPORT_ID.toString().equals(unsupportedSupportId);
        }

        private String stageSummary() {
            return stages.stream()
                    .map(BlockObservation::stateSummary)
                    .reduce((left, right) -> left + ";" + right)
                    .orElse("");
        }

        private boolean hasExpectedStageSupportValidity() {
            if (stages.size() != STAGE_FIXTURES.size()) {
                return false;
            }
            return stages.stream().noneMatch(BlockObservation::canPlaceAt);
        }

        private List<String> nativeStateInventory() {
            List<String> states = new ArrayList<>();
            for (BlockObservation observation : stages) {
                if (FOREST_LANTERN_ID.toString().equals(observation.blockId())) {
                    states.add("age=" + observation.age() + ",facing=" + observation.facing());
                }
            }
            for (BlockObservation observation : placements) {
                if (FOREST_LANTERN_ID.toString().equals(observation.blockId())) {
                    states.add("age=" + observation.age() + ",facing=" + observation.facing());
                }
            }
            states.sort(String::compareTo);
            return List.copyOf(states);
        }

        private boolean hasExactNativeStateInventory() {
            return expectedFixtureStateInventory().equals(expectedStateInventory())
                    && nativeStateInventory().equals(expectedFixtureStateInventory());
        }

        private String summary() {
            return "stages=" + stageSummary()
                    + "|placements=" + placements.stream()
                    .map(BlockObservation::stateSummary)
                    .reduce((left, right) -> left + ";" + right)
                    .orElse("")
                    + "|unsupported=" + unsupportedTarget.stateSummary();
        }

        private static List<String> expectedStageSupportIds() {
            return STAGE_FIXTURES.stream()
                    .map(fixture -> fixture.age() % 2 == 0
                            ? STAGE_SUPPORT_A_ID.toString()
                            : STAGE_SUPPORT_B_ID.toString())
                    .toList();
        }
    }

    private record PlacementEvidence(
            Direction direction,
            String actionResult,
            boolean accepted,
            int stackBefore,
            int stackAfter,
            boolean blockItemMapping,
            boolean supportValid,
            BlockObservation observation,
            String supportId
    ) {

        private boolean exactAccepted() {
            return "CONSUME".equals(actionResult)
                    && accepted
                    && stackBefore == 1
                    && stackAfter == 0
                    && blockItemMapping
                    && supportValid
                    && observation.matchesLantern(4, direction, true)
                    && PLACEMENT_SUPPORT_ID.toString().equals(supportId);
        }

        private boolean exactRejected() {
            return "FAIL".equals(actionResult)
                    && !accepted
                    && stackBefore == 1
                    && stackAfter == 1
                    && blockItemMapping
                    && !supportValid
                    && observation.matchesAir()
                    && UNSUPPORTED_SUPPORT_ID.toString().equals(supportId);
        }

        private String summary() {
            return "accepted=" + accepted
                    + ";action=" + actionResult
                    + ";stack=" + stackBefore + "->" + stackAfter
                    + ";block_item_mapping=" + blockItemMapping
                    + ";support_valid=" + supportValid
                    + ";state=" + observation.stateSummary()
                    + ";support=" + supportId;
        }
    }

    private record CaptureEvidence(
            boolean mirrorExact,
            boolean renderReady,
            boolean cameraExact,
            int stableRenders,
            int width,
            int height,
            String cameraPose,
            ScreenshotResult screenshot
    ) {

        private CaptureEvidence withScreenshot(ScreenshotResult result) {
            return new CaptureEvidence(
                    mirrorExact,
                    renderReady,
                    cameraExact,
                    stableRenders,
                    width,
                    height,
                    cameraPose,
                    result
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
            boolean supportsExact,
            FixtureSnapshot snapshot,
            String worldDisplayName,
            long worldSeed,
            String dimensionId
    ) {

        private boolean exact() {
            return chunksLoaded
                    && supportsExact
                    && WORLD_DISPLAY_NAME.equals(worldDisplayName)
                    && worldSeed == WORLD_SEED
                    && "minecraft:overworld".equals(dimensionId);
        }
    }

    private record MutationResult(
            CapturePhase phase,
            FixtureSnapshot snapshot,
            PlacementEvidence placement,
            PlacementEvidence unsupportedPlacement
    ) {
    }

    private record SaveResult(boolean saved, FixtureSnapshot snapshot) {
    }

    @FunctionalInterface
    private interface MutationOperation {
        MutationResult run();
    }
}
