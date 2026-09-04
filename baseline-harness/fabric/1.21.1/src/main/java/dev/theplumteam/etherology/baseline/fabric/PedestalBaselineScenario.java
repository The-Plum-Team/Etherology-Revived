package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.DataPackFailureScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.Window;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Captures the published-0.1.7 Pedestal entirely through Minecraft contracts.
 */
final class PedestalBaselineScenario implements ClientScenario {

    static final String SCENARIO_ID = PedestalBaselineContract.SCENARIO_ID;
    static final String WORLD_DIRECTORY_NAME =
            PedestalBaselineContract.WORLD_DIRECTORY_NAME;
    static final String WORLD_DISPLAY_NAME =
            PedestalBaselineContract.WORLD_DISPLAY_NAME;
    static final long WORLD_SEED = PedestalBaselineContract.WORLD_SEED;
    static final String FIRST_SCREENSHOT_FILE_NAME =
            PedestalBaselineContract.SCREENSHOT_FILE_NAMES.getFirst();
    static final ScenarioDefinition DEFINITION = new ScenarioDefinition(
            SCENARIO_ID,
            FIRST_SCREENSHOT_FILE_NAME,
            WORLD_DIRECTORY_NAME,
            WORLD_DISPLAY_NAME,
            WORLD_SEED
    );
    static final List<String> ASSERTION_NAMES =
            PedestalBaselineContract.assertionNames();

    private static final Logger LOGGER = LoggerFactory.getLogger(
            "EtherologyOriginalBaselineHarness"
    );
    private static final String REFERENCE_ID = "published-0.1.7";
    private static final String HARNESS_MOD_ID =
            "etherology_original_baseline_harness";
    private static final Identifier PEDESTAL_ID = etherologyId("pedestal");
    private static final Identifier PEDESTAL_BLOCK_ENTITY_ID =
            etherologyId("pedestal_block_entity");
    private static final Identifier PEDESTAL_RECIPE_ID = etherologyId("pedestal");
    private static final Identifier PEDESTAL_ADVANCEMENT_ID =
            etherologyId("recipes/decorations/pedestal");
    private static final Identifier PEDESTAL_LOOT_TABLE_ID =
            etherologyId("blocks/pedestal");
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final int FIXTURE_Y = ARENA_FLOOR_Y + 1;
    private static final double CAMERA_X = 0.5;
    private static final double CAMERA_Y = FIXTURE_Y;
    private static final double CAMERA_Z = -15.5;
    private static final float CAMERA_YAW = 0.0F;
    private static final float CAMERA_PITCH = 10.0F;
    private static final BlockPos CAMERA_BLOCK_POS =
            new BlockPos(0, FIXTURE_Y, -16);
    private static final BlockPos INTERACTION_POS =
            new BlockPos(12, FIXTURE_Y, 12);
    private static final BlockPos NATIVE_PLACEMENT_POS =
            new BlockPos(10, FIXTURE_Y, 12);
    private static final BlockPos WATERLOGGED_PLACEMENT_POS =
            new BlockPos(8, FIXTURE_Y, 12);
    private static final BlockPos LOOT_PROBE_POS =
            new BlockPos(14, FIXTURE_Y, 12);
    private static final BlockPos TWO_PEDESTAL_PROBE_POS =
            new BlockPos(-14, FIXTURE_Y, 8);
    private static final BlockPos THREE_PEDESTAL_PROBE_POS =
            new BlockPos(-10, FIXTURE_Y, 8);
    private static final BlockPos STACK_TRANSITION_POS =
            new BlockPos(-4, FIXTURE_Y, 3);
    private static final BlockPos REPLACEMENT_TRANSITION_POS =
            new BlockPos(4, FIXTURE_Y, 3);
    private static final Box TRANSITION_DROP_BOX =
            new Box(-8, FIXTURE_Y - 1, -1, 8, FIXTURE_Y + 5, 8);
    private static final Box DISPENSER_ENTITY_BOX =
            new Box(-24, FIXTURE_Y - 2, -2, 24, FIXTURE_Y + 8, 24);
    private static final List<ChunkPos> ARENA_CHUNKS = List.of(
            new ChunkPos(-1, -1),
            new ChunkPos(-1, 0),
            new ChunkPos(0, -1),
            new ChunkPos(0, 0)
    );
    private static final List<PedestalFixture> GALLERY_FIXTURES = List.of(
            fixture(-8, 0, "full", true, "red", "north",
                    false, "minecraft:diamondx1", "minecraft:red_carpetx1", true),
            fixture(-4, 0, "bottom", false, "white", "north",
                    false, "empty", "empty", false),
            fixture(-4, 1, "top", true, "blue", "north",
                    false, "minecraft:emeraldx1", "minecraft:blue_carpetx1", true),
            fixture(0, 0, "bottom", false, "white", "north",
                    false, "empty", "empty", false),
            fixture(0, 1, "middle", false, "white", "north",
                    false, "empty", "empty", false),
            fixture(0, 2, "top", true, "yellow", "north",
                    false, "minecraft:gold_ingotx1", "minecraft:yellow_carpetx1", true),
            fixture(4, 0, "full", true, "lime", "north",
                    false, "minecraft:amethyst_shardx1", "minecraft:lime_carpetx1", true),
            fixture(8, 0, "full", false, "white", "north",
                    true, "empty", "empty", true)
    );
    private static final List<PedestalFixture> TRANSITION_FIXTURES = List.of(
            fixtureAt(STACK_TRANSITION_POS, "bottom", false, "white", "north",
                    false, "empty", "empty", false),
            fixtureAt(STACK_TRANSITION_POS.up(), "top", false, "white", "north",
                    false, "empty", "empty", true)
    );
    private static final List<PedestalFixture> TRANSITION_PRECONDITION_FIXTURES =
            List.of(
                    fixtureAt(STACK_TRANSITION_POS, "full", true, "red", "north",
                            false, "minecraft:diamondx1",
                            "minecraft:red_carpetx1", true),
                    fixtureAt(REPLACEMENT_TRANSITION_POS, "full", true, "blue",
                            "north", false, "minecraft:emeraldx1",
                            "minecraft:blue_carpetx1", true)
            );
    private static final List<PedestalFixture> PERSISTENCE_FIXTURES = List.of(
            fixture(-6, 0, "full", true, "red", "north",
                    false, "minecraft:diamondx1", "minecraft:red_carpetx1", true),
            fixture(0, 0, "bottom", false, "white", "north",
                    false, "empty", "empty", false),
            fixture(0, 1, "top", true, "blue", "north",
                    false, "minecraft:emeraldx1", "minecraft:blue_carpetx1", true),
            fixture(6, 0, "bottom", false, "white", "north",
                    false, "empty", "empty", false),
            fixture(6, 1, "middle", false, "white", "north",
                    false, "empty", "empty", false),
            fixture(6, 2, "top", true, "yellow", "north",
                    false, "minecraft:gold_ingotx1", "minecraft:yellow_carpetx1", true)
    );
    private static final List<DispenserFixture> DISPENSER_FIXTURES =
            createDispenserFixtures();

    private Stage stage = Stage.WAITING_FOR_TITLE;
    private CapturePhase capturePhase = CapturePhase.GALLERY;
    private StableRenderCounter stableWorldRenders = new StableRenderCounter(
            PedestalBaselineContract.REQUIRED_COMPLETED_RENDERS
    );
    private int clientTicks;
    private int stageClientTicks;
    private int lightReadyClientTicks;
    private int requestedWindowWidth = -1;
    private int requestedWindowHeight = -1;
    private boolean initialSetupSubmitted;
    private volatile boolean dispenserInspectionInFlight;
    private boolean phaseSetupSubmitted;
    private boolean saveSubmitted;
    private boolean restartSubmitted;
    private boolean restartInspectionSubmitted;
    private boolean fullRestartCompleted;
    private boolean persistenceExact;
    private int transitionPreconditionClientTicks;
    private BlockEntity stackTransitionClientReference;
    private BlockEntity replacementTransitionClientReference;
    private boolean stackTransitionClientLookupAbsent;
    private boolean stackTransitionClientReferenceRemoved;
    private boolean replacementTransitionClientLookupAbsent;
    private boolean replacementTransitionClientReferenceRemoved;
    private boolean replacementTransitionClientAir;
    private String lifecycleFailure = "";
    private EvidenceLayout evidenceLayout;
    private ResourceProbe clientResourceProbe = ResourceProbe.missing();
    private RegistryProbe registryProbe = RegistryProbe.missing();
    private DataProbe dataProbe = DataProbe.missing();
    private PlacementProbe placementProbe = PlacementProbe.missing();
    private ShapeProbe shapeProbe = ShapeProbe.missing();
    private InteractionProbe interactionProbe = InteractionProbe.missing();
    private InventoryProbe inventoryProbe = InventoryProbe.missing();
    private DispenserProbe dispenserProbe = DispenserProbe.missing();
    private TransitionProbe transitionProbe = TransitionProbe.missing();
    private SceneSnapshot currentServerSnapshot = SceneSnapshot.missing();
    private SceneSnapshot persistenceInitialSnapshot = SceneSnapshot.missing();
    private SceneSnapshot reopenedSnapshot = SceneSnapshot.missing();
    private volatile SaveResult saveResult = SaveResult.missing();
    private final Map<CapturePhase, CaptureEvidence> captureEvidence =
            new LinkedHashMap<>();
    private volatile InitialSetupResult pendingInitialSetup;
    private volatile SceneSnapshot pendingTransitionPrecondition;
    private volatile SceneSetupResult pendingSceneSetup;
    private volatile ReopenedResult pendingReopenedResult;
    private volatile ScreenshotResult pendingScreenshotResult;
    private volatile String serverFailure = "";

    @Override
    public void onEndClientTick(MinecraftClient client) {
        if (stage == Stage.COMPLETE) return;
        if (!serverFailure.isEmpty()) {
            fail(client, "Pedestal server operation failed: " + serverFailure);
            return;
        }

        clientTicks++;
        stageClientTicks++;
        try {
            switch (stage) {
                case WAITING_FOR_TITLE -> tickWaitingForTitle(client);
                case STARTING_WORLD -> startWorld(client);
                case WAITING_FOR_WORLD -> tickWaitingForWorld(client);
                case WAITING_FOR_INITIAL_SETUP -> tickWaitingForInitialSetup(client);
                case WAITING_FOR_DISPENSERS -> tickWaitingForDispensers(client);
                case WAITING_FOR_TRANSITION_PRECONDITION ->
                        tickWaitingForTransitionPrecondition(client);
                case WAITING_FOR_SCENE_SETUP -> tickWaitingForSceneSetup(client);
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
            LOGGER.error("Original Pedestal scenario failed in {}", stage, exception);
            fail(client, stage + " raised " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
            return;
        }

        if (stage != Stage.COMPLETE
                && stage != Stage.CAPTURING
                && stageClientTicks >= MAXIMUM_STAGE_CLIENT_TICKS) {
            fail(client, "Timed out in " + stage + " after " + stageClientTicks
                    + " client ticks");
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
            LOGGER.error("Original Pedestal render callback failed", exception);
            fail(client, "Render callback raised "
                    + exception.getClass().getSimpleName() + ": "
                    + exception.getMessage());
        }
    }

    private void tickWaitingForTitle(MinecraftClient client) {
        if (client.getOverlay() != null
                || !(client.currentScreen instanceof TitleScreen)) return;
        try {
            ensureEvidenceLayout(client);
        } catch (IOException exception) {
            LOGGER.error("Cannot use the isolated Pedestal evidence layout", exception);
            lifecycleFailure = exception.getMessage();
            stage = Stage.COMPLETE;
            client.scheduleStop();
            return;
        }
        if (!requestExpectedFramebuffer(client)) return;

        clientResourceProbe = ResourceProbe.captureClient(client.getResourceManager());
        registryProbe = RegistryProbe.capture();
        if (!clientResourceProbe.clientExact()) {
            fail(client, "Published Pedestal client resources differ from their pins");
            return;
        }
        if (!registryProbe.exact()) {
            fail(client, "Published Pedestal registry preflight is not exact");
            return;
        }
        transition(Stage.STARTING_WORLD);
    }

    private void startWorld(MinecraftClient client) {
        Path saveDirectory = saveDirectory(client);
        if (Files.exists(saveDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(saveDirectory)) {
            fail(client, "Refusing to reuse the Pedestal save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "The client already has a world before Pedestal creation");
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
            fail(client, "Minecraft rejected the published Pedestal server data");
            return;
        }
        if (!isWorldLifecycleReady(client) || initialSetupSubmitted) return;

        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(client.player);
        initialSetupSubmitted = true;
        IntegratedServer server = client.getServer();
        UUID playerId = client.player.getUuid();
        server.execute(() -> setupInitialWorld(server, playerId));
        transition(Stage.WAITING_FOR_INITIAL_SETUP);
    }

    private void tickWaitingForInitialSetup(MinecraftClient client) {
        InitialSetupResult result = pendingInitialSetup;
        if (result == null) return;
        if (!result.exact()) {
            fail(client, "The initial Pedestal native probes were not exact");
            return;
        }
        dataProbe = result.dataProbe();
        placementProbe = result.placementProbe();
        shapeProbe = result.shapeProbe();
        interactionProbe = result.interactionProbe();
        inventoryProbe = result.inventoryProbe();
        transition(Stage.WAITING_FOR_DISPENSERS);
    }

    private void tickWaitingForDispensers(MinecraftClient client) {
        InitialSetupResult setup = pendingInitialSetup;
        IntegratedServer server = client.getServer();
        if (pendingSceneSetup != null) {
            transition(Stage.WAITING_FOR_SCENE_SETUP);
            return;
        }
        if (setup == null || server == null || dispenserInspectionInFlight) return;
        if (stageClientTicks % 4 != 0) return;
        dispenserInspectionInFlight = true;
        UUID playerId = client.player.getUuid();
        server.execute(() -> {
            try {
                ServerWorld world = server.getOverworld();
                if (world.getTime() < setup.dispenserReadyTick()) {
                    dispenserInspectionInFlight = false;
                    return;
                }
                DispenserProbe inspected = inspectDispensers(world);
                if (!inspected.exact()) {
                    throw new IllegalStateException(
                            "Directional dispenser probe did not match the contract: "
                                    + inspected.description()
                    );
                }
                dispenserProbe = inspected;
                clearTransientProbeArea(world);
                SceneSnapshot snapshot = arrangeScene(
                        world,
                        requirePlayer(server, playerId),
                        GALLERY_FIXTURES
                );
                pendingSceneSetup = new SceneSetupResult(
                        CapturePhase.GALLERY,
                        snapshot,
                        DropSnapshot.empty(),
                        null
                );
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            }
        });
    }

    private void tickWaitingForSceneSetup(MinecraftClient client) {
        SceneSetupResult result = pendingSceneSetup;
        if (result == null || result.phase() != capturePhase) return;
        if (!result.snapshot().exact(capturePhase.fixtures())) {
            fail(client, "The " + capturePhase.id()
                    + " Pedestal server scene was not exact");
            return;
        }
        currentServerSnapshot = result.snapshot();
        if (capturePhase == CapturePhase.TRANSITION_DROPS) {
            transitionProbe = result.transitionProbe() == null
                    ? TransitionProbe.missing()
                    : result.transitionProbe();
            if (!transitionProbe.exact()) {
                fail(client, "Pedestal transition/drop observations were not exact");
                return;
            }
        }
        if (capturePhase == CapturePhase.PERSISTENCE_INITIAL) {
            persistenceInitialSnapshot = result.snapshot();
        }
        resetClientReadiness();
        transition(Stage.WAITING_FOR_CLIENT_MIRROR);
    }

    private void tickWaitingForClientMirror(MinecraftClient client) {
        if (!isWorldViewReady(client)) {
            lightReadyClientTicks = 0;
            return;
        }
        SceneSnapshot clientSnapshot = SceneSnapshot.capture(
                client.world,
                capturePhase.fixtures()
        );
        boolean snapshotExact = clientSnapshot.equals(currentServerSnapshot)
                && clientSnapshot.exact(capturePhase.fixtures());
        if (capturePhase == CapturePhase.TRANSITION_DROPS) {
            snapshotExact &= refreshTransitionClientEvidence(client);
        }
        boolean lightReady = snapshotExact && captureLightReady(client);
        lightReadyClientTicks = lightReady
                ? Math.min(
                        lightReadyClientTicks + 1,
                        PedestalBaselineContract.REQUIRED_LIGHT_READY_CLIENT_TICKS
                )
                : 0;
        if (lightReadyClientTicks
                < PedestalBaselineContract.REQUIRED_LIGHT_READY_CLIENT_TICKS) return;
        stableWorldRenders = new StableRenderCounter(
                PedestalBaselineContract.REQUIRED_COMPLETED_RENDERS
        );
        transition(Stage.WAITING_FOR_RENDERS);
    }

    private void tickWaitingForRenders(MinecraftClient client) {
        if (!isCaptureStateExact(client)) {
            stableWorldRenders.observe(false);
            transition(Stage.WAITING_FOR_CLIENT_MIRROR);
        }
    }

    private void tickCapturing(MinecraftClient client) {
        ScreenshotResult screenshot = pendingScreenshotResult;
        if (screenshot == null) return;
        if (!screenshot.written()) {
            fail(client, "Minecraft did not publish the " + capturePhase.id()
                    + " Pedestal screenshot: " + screenshot.failure());
            return;
        }
        CaptureEvidence capture = captureEvidence.get(capturePhase);
        if (capture == null) {
            fail(client, "The " + capturePhase.id()
                    + " Pedestal capture record vanished");
            return;
        }
        captureEvidence.put(capturePhase, capture.withScreenshot(screenshot));
        pendingScreenshotResult = null;

        switch (capturePhase) {
            case GALLERY -> submitTransitionPrecondition(client);
            case TRANSITION_DROPS -> submitPersistenceScene(client);
            case PERSISTENCE_INITIAL -> submitSave(client);
            case PERSISTENCE_REOPENED -> publish(client);
        }
    }

    private void tickSavingWorld(MinecraftClient client) {
        if (!saveResult.completed()) return;
        if (!saveResult.saved()
                || !saveResult.snapshot().equals(persistenceInitialSnapshot)
                || !saveResult.snapshot().exact(PERSISTENCE_FIXTURES)) {
            fail(client, "The forced Pedestal save did not preserve its fixture");
            return;
        }
        transition(Stage.DISCONNECTING);
    }

    private void tickDisconnecting(MinecraftClient client) {
        if (client.world == null) {
            fail(client, "The client world vanished before Pedestal restart");
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
                || !(client.currentScreen instanceof TitleScreen)) return;
        transition(Stage.RESTARTING_WORLD);
    }

    private void restartWorld(MinecraftClient client) {
        if (restartSubmitted) return;
        restartSubmitted = true;
        client.createIntegratedServerLoader().start(
                WORLD_DIRECTORY_NAME,
                () -> fail(client, "Minecraft aborted the Pedestal restart")
        );
        transition(Stage.WAITING_FOR_RESTART_WORLD);
    }

    private void tickWaitingForRestartWorld(MinecraftClient client) {
        if (client.currentScreen instanceof DataPackFailureScreen) {
            fail(client, "Minecraft rejected saved Pedestal data on restart");
            return;
        }
        if (!isWorldLifecycleReady(client)) return;
        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.setCameraEntity(client.player);
        transition(Stage.WAITING_FOR_RESTART_INSPECTION);
    }

    private void tickWaitingForRestartInspection(MinecraftClient client) {
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
        fullRestartCompleted = result.worldIdentityExact();
        persistenceExact = fullRestartCompleted
                && reopenedSnapshot.equals(persistenceInitialSnapshot)
                && reopenedSnapshot.exact(PERSISTENCE_FIXTURES)
                && result.dataProbe().sameOutcome(dataProbe);
        if (!persistenceExact) {
            fail(client, "Pedestal state, inventory, or data changed across restart");
            return;
        }
        currentServerSnapshot = reopenedSnapshot;
        capturePhase = CapturePhase.PERSISTENCE_REOPENED;
        pendingSceneSetup = new SceneSetupResult(
                capturePhase,
                reopenedSnapshot,
                DropSnapshot.empty(),
                null
        );
        phaseSetupSubmitted = false;
        resetClientReadiness();
        transition(Stage.WAITING_FOR_CLIENT_MIRROR);
    }

    private void setupInitialWorld(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = requirePlayer(server, playerId);
            if (!loadArenaChunks(world)) {
                throw new IllegalStateException("The Pedestal arena chunks did not load");
            }
            configureWorld(server, world, player);
            clearArena(world);
            buildArenaFloor(world);

            ResourceProbe resources = clientResourceProbe.withServerData(
                    ResourceProbe.captureData(server.getResourceManager())
            );
            DataProbe initialData = DataProbe.capture(server, world, player, resources);
            PlacementProbe placements = PlacementProbe.capture(world, player);
            ShapeProbe shapes = ShapeProbe.capture(world);
            InteractionResult interactions = runInteractionProbe(world, player);
            setupDispensers(world, player);
            pendingInitialSetup = new InitialSetupResult(
                    resources,
                    initialData,
                    placements,
                    shapes,
                    interactions.interactions(),
                    interactions.inventory(),
                    world.getTime() + 20L
            );
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    private void configureWorld(
            IntegratedServer server,
            ServerWorld world,
            ServerPlayerEntity player
    ) {
        world.setTimeOfDay(6000L);
        world.setWeather(6000, 0, false, false);
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, server);
        player.changeGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        movePlayerToCamera(player, world);
        player.setSpawnPoint(
                World.OVERWORLD,
                CAMERA_BLOCK_POS,
                CAMERA_YAW,
                true,
                false
        );
    }

    private void setupDispensers(
            ServerWorld world,
            ServerPlayerEntity player
    ) {
        for (DispenserFixture fixture : DISPENSER_FIXTURES) {
            world.setBlockState(
                    fixture.target(),
                    requiredPedestal().getDefaultState(),
                    Block.NOTIFY_ALL
            );
            preloadDispenserTarget(world, player, fixture);
            BlockState dispenserState = Blocks.DISPENSER.getDefaultState()
                    .with(DispenserBlock.FACING, fixture.direction());
            world.setBlockState(
                    fixture.dispenser(),
                    dispenserState,
                    Block.NOTIFY_ALL
            );
            BlockEntity rawDispenser = world.getBlockEntity(fixture.dispenser());
            if (!(rawDispenser instanceof DispenserBlockEntity dispenser)) {
                throw new IllegalStateException("Missing dispenser block entity at "
                        + fixture.dispenser());
            }
            Item input = Registries.ITEM.getOrEmpty(identifier(fixture.inputId()))
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing dispenser fixture item " + fixture.inputId()
                    ));
            dispenser.setStack(0, new ItemStack(input, 2));
            dispenser.markDirty();
            world.setBlockState(
                    fixture.power(),
                    Blocks.REDSTONE_BLOCK.getDefaultState(),
                    Block.NOTIFY_ALL
            );
        }
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        movePlayerToCamera(player, world);
    }

    private static void preloadDispenserTarget(
            ServerWorld world,
            ServerPlayerEntity player,
            DispenserFixture fixture
    ) {
        if (!fixture.preloadCarpetId().isEmpty()) {
            interact(
                    world,
                    player,
                    fixture.target(),
                    new ItemStack(requiredItem(fixture.preloadCarpetId()), 2)
            );
        }
        if (!fixture.preloadItemId().isEmpty()) {
            interact(
                    world,
                    player,
                    fixture.target(),
                    new ItemStack(requiredItem(fixture.preloadItemId()), 2)
            );
        }
    }

    private DispenserProbe inspectDispensers(ServerWorld world) {
        List<DispenserObservation> observations = new ArrayList<>();
        for (DispenserFixture fixture : DISPENSER_FIXTURES) {
            BlockEntity rawDispenser = world.getBlockEntity(fixture.dispenser());
            BlockEntity rawPedestal = world.getBlockEntity(fixture.target());
            Inventory dispenser = rawDispenser instanceof Inventory value ? value : null;
            Inventory pedestal = rawPedestal instanceof Inventory value ? value : null;
            BlockState state = world.getBlockState(fixture.target());
            observations.add(new DispenserObservation(
                    fixture.kind(),
                    fixture.direction().getName(),
                    dispenser == null ? "missing" : stackDescription(dispenser.getStack(0)),
                    pedestal == null ? "missing" : stackDescription(pedestal.getStack(0)),
                    pedestal == null ? "missing" : stackDescription(pedestal.getStack(1)),
                    propertyValue(state, "decoration"),
                    propertyValue(state, "cloth_color"),
                    propertyValue(state, "facing")
            ));
        }
        List<String> ejectedItems = world.getEntitiesByClass(
                        ItemEntity.class,
                        DISPENSER_ENTITY_BOX,
                        entity -> true
                ).stream()
                .map(entity -> stackDescription(entity.getStack()))
                .sorted()
                .toList();
        int arrowProjectiles = world.getEntitiesByClass(
                ArrowEntity.class,
                DISPENSER_ENTITY_BOX,
                entity -> true
        ).size();
        return new DispenserProbe(
                List.copyOf(observations),
                PedestalBaselineContract.GUARDED_CARPET_DISPENSER_DIRECTIONS,
                PedestalBaselineContract.VERTICAL_CARPET_LIMITATION,
                ejectedItems,
                arrowProjectiles
        );
    }

    private InteractionResult runInteractionProbe(
            ServerWorld world,
            ServerPlayerEntity player
    ) {
        clearBlockColumn(world, INTERACTION_POS, 3);
        player.teleport(
                world,
                INTERACTION_POS.getX() + 0.5,
                FIXTURE_Y,
                INTERACTION_POS.getZ() - 2.5,
                0.0F,
                0.0F
        );
        nativePlacePedestal(world, player, INTERACTION_POS, false);
        List<InteractionStep> steps = new ArrayList<>();

        steps.add(interactAndObserve(
                "red-carpet-stack-place", world, player, INTERACTION_POS,
                new ItemStack(Items.RED_CARPET, 2)
        ));
        steps.add(interactAndObserve(
                "different-carpet-stack-noop", world, player, INTERACTION_POS,
                new ItemStack(Items.BLUE_CARPET, 2)
        ));
        steps.add(interactAndObserve(
                "single-carpet-swap", world, player, INTERACTION_POS,
                new ItemStack(Items.BLUE_CARPET, 1)
        ));
        steps.add(interactAndObserve(
                "same-carpet-retrieve", world, player, INTERACTION_POS,
                new ItemStack(Items.BLUE_CARPET, 1)
        ));
        steps.add(interactAndObserve(
                "diamond-place", world, player, INTERACTION_POS,
                new ItemStack(Items.DIAMOND, 2)
        ));
        steps.add(interactAndObserve(
                "different-item-noop", world, player, INTERACTION_POS,
                new ItemStack(Items.EMERALD, 2)
        ));
        steps.add(interactAndObserve(
                "full-same-item-noop", world, player, INTERACTION_POS,
                new ItemStack(Items.DIAMOND, 64)
        ));
        steps.add(interactAndObserve(
                "same-item-retrieve", world, player, INTERACTION_POS,
                new ItemStack(Items.DIAMOND, 1)
        ));

        interact(world, player, INTERACTION_POS, new ItemStack(Items.RED_CARPET, 2));
        interact(world, player, INTERACTION_POS, new ItemStack(Items.DIAMOND, 2));
        InventoryProbe inventory = InventoryProbe.capture(
                world,
                INTERACTION_POS,
                player
        );
        steps.add(interactAndObserve(
                "empty-hand-item-first", world, player, INTERACTION_POS,
                ItemStack.EMPTY
        ));
        steps.add(interactAndObserve(
                "empty-hand-carpet-second", world, player, INTERACTION_POS,
                ItemStack.EMPTY
        ));

        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        movePlayerToCamera(player, world);
        return new InteractionResult(new InteractionProbe(List.copyOf(steps)), inventory);
    }

    private static InteractionStep interactAndObserve(
            String id,
            ServerWorld world,
            ServerPlayerEntity player,
            BlockPos position,
            ItemStack handStack
    ) {
        String before = stackDescription(handStack);
        ActionResult result = interact(world, player, position, handStack);
        BlockEntity blockEntity = world.getBlockEntity(position);
        Inventory inventory = blockEntity instanceof Inventory value ? value : null;
        BlockState state = world.getBlockState(position);
        return new InteractionStep(
                id,
                result.name(),
                before,
                stackDescription(player.getStackInHand(Hand.MAIN_HAND)),
                inventory == null ? "missing" : stackDescription(inventory.getStack(0)),
                inventory == null ? "missing" : stackDescription(inventory.getStack(1)),
                propertyValue(state, "decoration"),
                propertyValue(state, "cloth_color"),
                propertyValue(state, "facing")
        );
    }

    private static ActionResult interact(
            ServerWorld world,
            ServerPlayerEntity player,
            BlockPos position,
            ItemStack handStack
    ) {
        player.setStackInHand(Hand.MAIN_HAND, handStack);
        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(position),
                Direction.UP,
                position,
                false
        );
        return player.interactionManager.interactBlock(
                player,
                world,
                player.getStackInHand(Hand.MAIN_HAND),
                Hand.MAIN_HAND,
                hit
        );
    }

    private void submitTransitionPrecondition(MinecraftClient client) {
        if (phaseSetupSubmitted) return;
        phaseSetupSubmitted = true;
        pendingTransitionPrecondition = null;
        IntegratedServer server = client.getServer();
        UUID playerId = client.player.getUuid();
        server.execute(() -> {
            try {
                ServerWorld world = server.getOverworld();
                clearScene(world);
                pendingTransitionPrecondition = arrangeScene(
                        world,
                        requirePlayer(server, playerId),
                        TRANSITION_PRECONDITION_FIXTURES
                );
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            }
        });
        transition(Stage.WAITING_FOR_TRANSITION_PRECONDITION);
    }

    private void tickWaitingForTransitionPrecondition(MinecraftClient client) {
        SceneSnapshot serverSnapshot = pendingTransitionPrecondition;
        if (serverSnapshot == null || !isWorldViewReady(client)) {
            transitionPreconditionClientTicks = 0;
            return;
        }
        SceneSnapshot clientSnapshot = SceneSnapshot.capture(
                client.world,
                TRANSITION_PRECONDITION_FIXTURES
        );
        BlockEntity stackReference = client.world.getBlockEntity(
                STACK_TRANSITION_POS
        );
        BlockEntity replacementReference = client.world.getBlockEntity(
                REPLACEMENT_TRANSITION_POS
        );
        boolean exact = serverSnapshot.exact(TRANSITION_PRECONDITION_FIXTURES)
                && clientSnapshot.equals(serverSnapshot)
                && clientSnapshot.exact(TRANSITION_PRECONDITION_FIXTURES)
                && stackReference != null
                && !stackReference.isRemoved()
                && replacementReference != null
                && !replacementReference.isRemoved();
        transitionPreconditionClientTicks = exact
                ? Math.min(
                        transitionPreconditionClientTicks + 1,
                        PedestalBaselineContract.REQUIRED_LIGHT_READY_CLIENT_TICKS
                )
                : 0;
        if (transitionPreconditionClientTicks
                < PedestalBaselineContract.REQUIRED_LIGHT_READY_CLIENT_TICKS) return;
        stackTransitionClientReference = stackReference;
        replacementTransitionClientReference = replacementReference;
        submitTransitionMutation(client, serverSnapshot);
    }

    private void submitTransitionMutation(
            MinecraftClient client,
            SceneSnapshot precondition
    ) {
        capturePhase = CapturePhase.TRANSITION_DROPS;
        CapturePhase submittedPhase = capturePhase;
        pendingSceneSetup = null;
        IntegratedServer server = client.getServer();
        UUID playerId = client.player.getUuid();
        server.execute(() -> {
            try {
                ServerWorld world = server.getOverworld();
                TransitionProbe result = runTransitionProbe(
                        world,
                        requirePlayer(server, playerId),
                        precondition
                );
                SceneSnapshot snapshot = SceneSnapshot.capture(
                        world,
                        TRANSITION_FIXTURES
                );
                pendingSceneSetup = new SceneSetupResult(
                        submittedPhase,
                        snapshot,
                        result.combinedDrops(),
                        result
                );
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            }
        });
        transition(Stage.WAITING_FOR_SCENE_SETUP);
    }

    private TransitionProbe runTransitionProbe(
            ServerWorld world,
            ServerPlayerEntity player,
            SceneSnapshot precondition
    ) {
        SceneSnapshot actualPrecondition = SceneSnapshot.capture(
                world,
                TRANSITION_PRECONDITION_FIXTURES
        );
        if (!actualPrecondition.equals(precondition)
                || !actualPrecondition.exact(TRANSITION_PRECONDITION_FIXTURES)) {
            throw new IllegalStateException(
                    "Pedestal transition precondition changed before mutation"
            );
        }
        movePlayerToCamera(player, world);
        BlockEntity stackedOldReference = world.getBlockEntity(STACK_TRANSITION_POS);
        nativePlacePedestal(world, player, STACK_TRANSITION_POS.up(), false);
        DropSnapshot stackedDrops = DropSnapshot.capture(
                world,
                new Box(STACK_TRANSITION_POS).expand(3.0)
        );

        BlockEntity replacedOldReference =
                world.getBlockEntity(REPLACEMENT_TRANSITION_POS);
        world.setBlockState(
                REPLACEMENT_TRANSITION_POS,
                Blocks.AIR.getDefaultState(),
                Block.NOTIFY_ALL
        );
        DropSnapshot replacementDrops = DropSnapshot.capture(
                world,
                new Box(REPLACEMENT_TRANSITION_POS).expand(3.0)
        );
        DropSnapshot combined = DropSnapshot.capture(world, TRANSITION_DROP_BOX);
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        movePlayerToCamera(player, world);
        return new TransitionProbe(
                stateDescription(world.getBlockState(STACK_TRANSITION_POS)),
                stateDescription(world.getBlockState(STACK_TRANSITION_POS.up())),
                world.getBlockEntity(STACK_TRANSITION_POS) == null,
                world.getBlockEntity(STACK_TRANSITION_POS.up()) != null,
                stackedOldReference != null && stackedOldReference.isRemoved(),
                world.getBlockState(REPLACEMENT_TRANSITION_POS).isAir(),
                world.getBlockEntity(REPLACEMENT_TRANSITION_POS) == null,
                replacedOldReference != null && replacedOldReference.isRemoved(),
                stackedDrops,
                replacementDrops,
                combined
        );
    }

    private void submitPersistenceScene(MinecraftClient client) {
        if (!phaseSetupSubmitted) return;
        phaseSetupSubmitted = false;
        capturePhase = CapturePhase.PERSISTENCE_INITIAL;
        CapturePhase submittedPhase = capturePhase;
        pendingSceneSetup = null;
        IntegratedServer server = client.getServer();
        UUID playerId = client.player.getUuid();
        server.execute(() -> {
            try {
                ServerWorld world = server.getOverworld();
                clearScene(world);
                SceneSnapshot snapshot = arrangeScene(
                        world,
                        requirePlayer(server, playerId),
                        PERSISTENCE_FIXTURES
                );
                pendingSceneSetup = new SceneSetupResult(
                        submittedPhase,
                        snapshot,
                        DropSnapshot.empty(),
                        null
                );
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            }
        });
        transition(Stage.WAITING_FOR_SCENE_SETUP);
    }

    private void submitSave(MinecraftClient client) {
        if (saveSubmitted) return;
        IntegratedServer server = client.getServer();
        if (server == null || !server.isRunning() || server.isStopping()) {
            fail(client, "The integrated server stopped before Pedestal save");
            return;
        }
        saveSubmitted = true;
        server.execute(() -> {
            try {
                SceneSnapshot snapshot = SceneSnapshot.capture(
                        server.getOverworld(),
                        PERSISTENCE_FIXTURES
                );
                saveResult = new SaveResult(
                        true,
                        server.saveAll(false, true, true),
                        snapshot
                );
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            }
        });
        transition(Stage.SAVING_WORLD);
    }

    private void inspectReopenedWorld(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = requirePlayer(server, playerId);
            if (!loadArenaChunks(world)) {
                throw new IllegalStateException("Reopened Pedestal chunks did not load");
            }
            configureWorld(server, world, player);
            SceneSnapshot snapshot = SceneSnapshot.capture(
                    world,
                    PERSISTENCE_FIXTURES
            );
            ResourceProbe resources = clientResourceProbe.withServerData(
                    ResourceProbe.captureData(server.getResourceManager())
            );
            DataProbe reopenedData = DataProbe.capture(server, world, player, resources);
            pendingReopenedResult = new ReopenedResult(
                    snapshot,
                    reopenedData,
                    WORLD_DISPLAY_NAME.equals(server.getSaveProperties().getLevelName())
                            && world.getSeed() == WORLD_SEED
                            && world.getRegistryKey() == World.OVERWORLD
            );
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    private SceneSnapshot arrangeScene(
            ServerWorld world,
            ServerPlayerEntity player,
            List<PedestalFixture> fixtures
    ) {
        clearScene(world);
        Set<BlockPos> positions = fixtures.stream()
                .map(PedestalFixture::position)
                .collect(Collectors.toSet());
        List<PedestalFixture> ordered = fixtures.stream()
                .sorted(Comparator.comparingInt(value -> value.position().getY()))
                .toList();
        for (PedestalFixture fixture : ordered) {
            if (world.getBlockState(fixture.position()).isOf(requiredPedestal())) continue;
            if (fixture.waterlogged()) {
                world.setBlockState(
                        fixture.position(),
                        Blocks.WATER.getDefaultState(),
                        Block.NOTIFY_ALL
                );
            }
            nativePlacePedestal(world, player, fixture.position(), fixture.waterlogged());
        }
        for (PedestalFixture fixture : fixtures) {
            if (!fixture.blockEntityPresent()) continue;
            if (!"empty".equals(fixture.carpet())) {
                interact(world, player, fixture.position(), stackFromDescription(
                        fixture.carpet(), 2
                ));
            }
            if (!"empty".equals(fixture.item())) {
                interact(world, player, fixture.position(), stackFromDescription(
                        fixture.item(), 2
                ));
            }
        }
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        movePlayerToCamera(player, world);
        SceneSnapshot snapshot = SceneSnapshot.capture(world, fixtures);
        if (!snapshot.exact(fixtures) || positions.size() != fixtures.size()) {
            throw new IllegalStateException("Pedestal scene arrangement drifted: "
                    + snapshot.description());
        }
        return snapshot;
    }

    private static ItemStack stackFromDescription(String description, int count) {
        String id = description.substring(0, description.indexOf('x'));
        Item item = Registries.ITEM.getOrEmpty(identifier(id)).orElseThrow(
                () -> new IllegalStateException("Missing fixture item " + id)
        );
        return new ItemStack(item, count);
    }

    private void captureCurrentPhase(MinecraftClient client) {
        if (!isCaptureStateExact(client)) {
            stableWorldRenders.observe(false);
            return;
        }
        SceneSnapshot clientSnapshot = SceneSnapshot.capture(
                client.world,
                capturePhase.fixtures()
        );
        CaptureEvidence capture = new CaptureEvidence(
                clientSnapshot.equals(currentServerSnapshot),
                isRenderReady(client),
                hasExpectedCameraPose(client),
                stableWorldRenders.completedRenders(),
                client.getFramebuffer().textureWidth,
                client.getFramebuffer().textureHeight,
                cameraPoseDescription(client),
                currentServerSnapshot,
                ScreenshotResult.missing()
        );
        captureEvidence.put(capturePhase, capture);
        transition(Stage.CAPTURING);
        String temporaryName = PedestalEvidenceWriter.temporaryScreenshotPath(
                evidenceLayout,
                capturePhase.screenshotFileName()
        ).getFileName().toString();
        ScreenshotRecorder.saveScreenshot(
                evidenceLayout.scenarioRoot().toFile(),
                temporaryName,
                client.getFramebuffer(),
                message -> pendingScreenshotResult = inspectAndPublishScreenshot(
                        capturePhase.screenshotFileName()
                )
        );
    }

    private ScreenshotResult inspectAndPublishScreenshot(String fileName) {
        try {
            PedestalEvidenceWriter.publishScreenshot(evidenceLayout, fileName);
            Path path = evidenceLayout.screenshotPath(fileName);
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return ScreenshotResult.failed("Published PNG is missing or linked");
            }
            long size = Files.size(path);
            if (size <= 0L) return ScreenshotResult.failed("Published PNG is empty");
            return new ScreenshotResult(true, size, ArtifactDigest.sha256(path), "");
        } catch (IOException | RuntimeException exception) {
            return ScreenshotResult.failed(exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
        }
    }

    private boolean isCaptureStateExact(MinecraftClient client) {
        if (!isWorldViewReady(client)
                || lightReadyClientTicks
                < PedestalBaselineContract.REQUIRED_LIGHT_READY_CLIENT_TICKS
                || !captureLightReady(client)
                || !isRenderReady(client)
                || !hasExpectedCameraPose(client)) return false;
        SceneSnapshot snapshot = SceneSnapshot.capture(
                client.world,
                capturePhase.fixtures()
        );
        if (!snapshot.equals(currentServerSnapshot)
                || !snapshot.exact(capturePhase.fixtures())) return false;
        if (capturePhase == CapturePhase.TRANSITION_DROPS) {
            return refreshTransitionClientEvidence(client);
        }
        return true;
    }

    private boolean refreshTransitionClientEvidence(MinecraftClient client) {
        if (client.world == null) return false;
        stackTransitionClientLookupAbsent =
                client.world.getBlockEntity(STACK_TRANSITION_POS) == null;
        stackTransitionClientReferenceRemoved =
                stackTransitionClientReference != null
                        && stackTransitionClientReference.isRemoved();
        replacementTransitionClientLookupAbsent =
                client.world.getBlockEntity(REPLACEMENT_TRANSITION_POS) == null;
        replacementTransitionClientReferenceRemoved =
                replacementTransitionClientReference != null
                        && replacementTransitionClientReference.isRemoved();
        replacementTransitionClientAir = client.world.getBlockState(
                REPLACEMENT_TRANSITION_POS
        ).isAir();
        return stackTransitionClientLookupAbsent
                && stackTransitionClientReferenceRemoved
                && replacementTransitionClientLookupAbsent
                && replacementTransitionClientReferenceRemoved
                && replacementTransitionClientAir
                && DropSnapshot.capture(client.world, TRANSITION_DROP_BOX)
                .equals(transitionProbe.combinedDrops());
    }

    private boolean isRenderReady(MinecraftClient client) {
        if (client.world == null) return false;
        for (PedestalFixture fixture : capturePhase.fixtures()) {
            if (!client.world.getBlockState(fixture.position()).isOf(requiredPedestal())) {
                return false;
            }
            if (!fixture.blockEntityPresent()) continue;
            BlockEntity blockEntity = client.world.getBlockEntity(fixture.position());
            if (!(blockEntity instanceof Inventory inventory)
                    || !stackDescription(inventory.getStack(0)).equals(fixture.item())
                    || !stackDescription(inventory.getStack(1)).equals(fixture.carpet())) {
                return false;
            }
        }
        return true;
    }

    private boolean captureLightReady(MinecraftClient client) {
        if (client.world == null
                || client.world.getChunkManager().getLightingProvider().hasUpdates()) {
            return false;
        }
        for (PedestalFixture fixture : capturePhase.fixtures()) {
            BlockPos position = fixture.position().up();
            if (!client.world.getChunkManager().isChunkLoaded(
                    position.getX() >> 4,
                    position.getZ() >> 4
            ) || client.world.getLightLevel(LightType.SKY, position) != 15) {
                return false;
            }
        }
        return true;
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
                && client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT
                && window.getFramebufferWidth() == FRAMEBUFFER_WIDTH
                && window.getFramebufferHeight() == FRAMEBUFFER_HEIGHT;
    }

    private boolean requestExpectedFramebuffer(MinecraftClient client) {
        Window window = client.getWindow();
        if (client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT
                && window.getFramebufferWidth() == FRAMEBUFFER_WIDTH
                && window.getFramebufferHeight() == FRAMEBUFFER_HEIGHT) return true;
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
                || currentFramebufferHeight <= 0) return false;
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

    private boolean hasExpectedCameraPose(MinecraftClient client) {
        if (client.player == null
                || client.getCameraEntity() != client.player
                || !client.options.getPerspective().isFirstPerson()
                || !client.player.isOnGround()) return false;
        return isExactCameraPose(
                client.player.getX(),
                client.player.getY(),
                client.player.getZ(),
                client.player.getYaw(),
                client.player.getPitch()
        );
    }

    static boolean isExactCameraPose(
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        return Double.compare(x, CAMERA_X) == 0
                && Double.compare(y, CAMERA_Y) == 0
                && Double.compare(z, CAMERA_Z) == 0
                && Float.compare(MathHelper.wrapDegrees(yaw), CAMERA_YAW) == 0
                && Float.compare(pitch, CAMERA_PITCH) == 0;
    }

    private static void movePlayerToCamera(
            ServerPlayerEntity player,
            ServerWorld world
    ) {
        player.teleport(
                world,
                CAMERA_X,
                CAMERA_Y,
                CAMERA_Z,
                CAMERA_YAW,
                CAMERA_PITCH
        );
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

    private void resetClientReadiness() {
        lightReadyClientTicks = 0;
        stableWorldRenders = new StableRenderCounter(
                PedestalBaselineContract.REQUIRED_COMPLETED_RENDERS
        );
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;
        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Original Pedestal lifecycle failure: {}", lifecycleFailure);
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
            PedestalEvidenceWriter.writeReportThenMarker(
                    evidenceLayout,
                    report,
                    report.get("passed").getAsBoolean()
            );
            LOGGER.info(
                    "Original Pedestal evidence published with status {}: {}",
                    report.get("status").getAsString(),
                    evidenceLayout.scenarioRoot()
            );
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Cannot exclusively publish Pedestal evidence", exception);
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
        PedestalEvidenceWriter.requireFreshLayout(
                layout,
                PedestalBaselineContract.SCREENSHOT_FILE_NAMES
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
        passed &= addAssertion(assertions, "fabric_mod_loaded:etherology",
                etherologyLoaded, "loaded", etherologyLoaded ? "loaded" : "missing");
        passed &= addAssertion(assertions, "pedestal_resources_exact",
                dataProbe.resources().exact(), "64 byte-pinned resources",
                dataProbe.resources().description());
        passed &= addAssertion(assertions,
                "pedestal_blockstate_multipart_count_exact",
                dataProbe.resources().multipartClauseCount()
                        == PedestalBaselineContract.EXPECTED_MULTIPART_CLAUSE_COUNT,
                Integer.toString(PedestalBaselineContract.EXPECTED_MULTIPART_CLAUSE_COUNT),
                Integer.toString(dataProbe.resources().multipartClauseCount()));
        passed &= addAssertion(assertions, "registry:block:etherology:pedestal",
                registryProbe.blockPresent(), "present",
                registryProbe.blockPresent() ? "present" : "missing");
        passed &= addAssertion(assertions, "registry:item:etherology:pedestal",
                registryProbe.itemPresent(), "present",
                registryProbe.itemPresent() ? "present" : "missing");
        passed &= addAssertion(assertions,
                "registry:block_entity_type:etherology:pedestal_block_entity",
                registryProbe.blockEntityTypePresent(), "present",
                registryProbe.blockEntityTypePresent() ? "present" : "missing");
        passed &= addAssertion(assertions, "pedestal_runtime_block_class_exact",
                "ru.feytox.etherology.block.pedestal.PedestalBlock".equals(
                        registryProbe.blockClass()),
                "ru.feytox.etherology.block.pedestal.PedestalBlock",
                registryProbe.blockClass());
        passed &= addAssertion(assertions, "pedestal_runtime_block_entity_class_exact",
                inventoryProbe.blockEntityClass().equals(
                        "ru.feytox.etherology.block.pedestal.PedestalBlockEntity"),
                "ru.feytox.etherology.block.pedestal.PedestalBlockEntity",
                inventoryProbe.blockEntityClass());
        passed &= addAssertion(assertions, "pedestal_block_item_mapping_exact",
                registryProbe.blockItemMapping(), "true",
                Boolean.toString(registryProbe.blockItemMapping()));
        passed &= addAssertion(assertions, "pedestal_translation_exact",
                "Pedestal".equals(registryProbe.translation()), "Pedestal",
                registryProbe.translation());
        passed &= addAssertion(assertions, "pedestal_default_properties_exact",
                registryProbe.defaultProperties().equals(expectedDefaultProperties()),
                expectedDefaultProperties().toString(),
                registryProbe.defaultProperties().toString());
        passed &= addAssertion(assertions, "pedestal_state_count_exact",
                registryProbe.stateCount()
                        == PedestalBaselineContract.EXPECTED_STATE_COUNT,
                Integer.toString(PedestalBaselineContract.EXPECTED_STATE_COUNT),
                Integer.toString(registryProbe.stateCount()));
        passed &= addAssertion(assertions, "pedestal_state_network_ids_exact",
                registryProbe.networkIdsExact(), "1024 unique non-negative raw ids",
                registryProbe.uniqueNetworkIds()
                        + " unique;default=" + registryProbe.defaultNetworkId());
        passed &= addAssertion(assertions, "pedestal_horizontal_facing_values_exact",
                registryProbe.horizontalFacings().equals(
                        List.of("east", "north", "south", "west")),
                "[east, north, south, west]",
                registryProbe.horizontalFacings().toString());
        passed &= addAssertion(assertions, "pedestal_pickaxe_tag_exact",
                dataProbe.pickaxeMineable(), "true",
                Boolean.toString(dataProbe.pickaxeMineable()));
        passed &= addAssertion(assertions, "pedestal_recipe_exact",
                dataProbe.recipe().equals(
                        "etherology:pedestal=minecraft:crafting->etherology:pedestalx2"),
                "etherology:pedestal=minecraft:crafting->etherology:pedestalx2",
                dataProbe.recipe());
        passed &= addAssertion(assertions, "pedestal_advancement_exact",
                dataProbe.advancementPresent(), PEDESTAL_ADVANCEMENT_ID.toString(),
                dataProbe.advancementPresent()
                        ? PEDESTAL_ADVANCEMENT_ID.toString() : "missing");
        passed &= addAssertion(assertions, "pedestal_loot_table_exact",
                dataProbe.lootTablePresent(), PEDESTAL_LOOT_TABLE_ID.toString(),
                dataProbe.lootTablePresent()
                        ? PEDESTAL_LOOT_TABLE_ID.toString() : "missing");
        passed &= addAssertion(assertions, "pedestal_self_drop_exact",
                "etherology:pedestalx1".equals(dataProbe.selfDrop()),
                "etherology:pedestalx1", dataProbe.selfDrop());
        passed &= addAssertion(assertions, "pedestal_native_standalone_placement_exact",
                placementProbe.standalone().exact(false), "accepted;count=1->0;shape=full",
                placementProbe.standalone().description());
        passed &= addAssertion(assertions,
                "pedestal_native_waterlogged_placement_exact",
                placementProbe.waterlogged().exact(true),
                "accepted;count=1->0;shape=full;waterlogged=true",
                placementProbe.waterlogged().description());
        passed &= addAssertion(assertions, "pedestal_outline_shapes_exact",
                shapeProbe.exact(), ShapeProbe.expectedDescription(),
                shapeProbe.description());
        passed &= addAssertion(assertions, "pedestal_stack_shape_transitions_exact",
                placementProbe.stackShapesExact(),
                "standalone=full;two=bottom,top;three=bottom,middle,top",
                placementProbe.stackShapeDescription());
        passed &= addAssertion(assertions,
                "pedestal_block_entity_presence_by_shape_exact",
                placementProbe.blockEntityPresenceExact(),
                "full=true;bottom=false;middle=false;top=true",
                placementProbe.blockEntityDescription());
        passed &= addAssertion(assertions, "pedestal_interaction_sequence_exact",
                interactionProbe.exact(), InteractionProbe.expectedDescription(),
                interactionProbe.description());
        passed &= addAssertion(assertions,
                "pedestal_inventory_two_max_one_slots_exact",
                inventoryProbe.size() == 2 && inventoryProbe.maxCount() == 1,
                "size=2;max=1",
                "size=" + inventoryProbe.size() + ";max=" + inventoryProbe.maxCount());
        passed &= addAssertion(assertions, "pedestal_sided_inventory_closed_exact",
                inventoryProbe.sidedClosed(), "all six sides expose zero slots and deny IO",
                inventoryProbe.sidedDescription());
        passed &= addAssertion(assertions, "pedestal_nbt_items_exact",
                inventoryProbe.nbtItemCount() == 2
                        && inventoryProbe.nbtKeys().contains("Items"),
                "Items=2", "Items=" + inventoryProbe.nbtItemCount()
                        + ";keys=" + inventoryProbe.nbtKeys());
        passed &= addAssertion(assertions, "pedestal_nbt_removed_flag_exact",
                inventoryProbe.nbtRemovedPresent() && !inventoryProbe.nbtRemoved(),
                "present=true;value=false", "present=" + inventoryProbe.nbtRemovedPresent()
                        + ";value=" + inventoryProbe.nbtRemoved());
        passed &= addAssertion(assertions,
                "pedestal_item_dispenser_all_six_directions_exact",
                dispenserProbe.itemDirectionsExact(),
                PedestalBaselineContract.ITEM_DISPENSER_DIRECTIONS.toString(),
                dispenserProbe.itemDirections().toString());
        passed &= addAssertion(assertions,
                "pedestal_carpet_dispenser_horizontal_directions_exact",
                dispenserProbe.carpetDirectionsExact(),
                PedestalBaselineContract.CARPET_DISPENSER_DIRECTIONS.toString(),
                dispenserProbe.carpetDirections().toString());
        passed &= addAssertion(assertions,
                "pedestal_occupied_carpet_falls_through_to_display_exact",
                dispenserProbe.occupiedCarpetExact(),
                DispenserProbe.OCCUPIED_CARPET_EXPECTED,
                dispenserProbe.occupiedCarpetDescription());
        passed &= addAssertion(assertions,
                "pedestal_full_target_falls_through_to_generic_item_ejection_exact",
                dispenserProbe.fullTargetFallbackExact(),
                DispenserProbe.FULL_TARGET_FALLBACK_EXPECTED,
                dispenserProbe.fullTargetFallbackDescription());
        passed &= addAssertion(assertions, "pedestal_stack_transition_drops_exact",
                transitionProbe.stackDropsExact(),
                "minecraft:diamondx1+minecraft:red_carpetx1",
                transitionProbe.stackDrops().description());
        passed &= addAssertion(assertions,
                "pedestal_stack_transition_stale_block_entity_removed",
                transitionProbe.stackOldReferenceRemoved()
                        && transitionProbe.stackWorldBlockEntityAbsent(),
                "old_removed=true;world_absent=true",
                "old_removed=" + transitionProbe.stackOldReferenceRemoved()
                        + ";world_absent="
                        + transitionProbe.stackWorldBlockEntityAbsent());
        passed &= addAssertion(assertions,
                "pedestal_stack_transition_client_block_entity_removed",
                stackTransitionClientLookupAbsent
                        && stackTransitionClientReferenceRemoved,
                "lookup_absent=true;retained_removed=true",
                "lookup_absent=" + stackTransitionClientLookupAbsent
                        + ";retained_removed="
                        + stackTransitionClientReferenceRemoved);
        passed &= addAssertion(assertions, "pedestal_replacement_drops_exact",
                transitionProbe.replacementDropsExact(),
                "minecraft:blue_carpetx1+minecraft:emeraldx1",
                transitionProbe.replacementDrops().description());
        passed &= addAssertion(assertions,
                "pedestal_replacement_stale_block_entity_removed",
                transitionProbe.replacementOldReferenceRemoved()
                        && transitionProbe.replacementWorldBlockEntityAbsent(),
                "old_removed=true;world_absent=true",
                "old_removed=" + transitionProbe.replacementOldReferenceRemoved()
                        + ";world_absent="
                        + transitionProbe.replacementWorldBlockEntityAbsent());
        passed &= addAssertion(assertions,
                "pedestal_replacement_client_block_entity_removed",
                replacementTransitionClientLookupAbsent
                        && replacementTransitionClientReferenceRemoved
                        && replacementTransitionClientAir,
                "air=true;lookup_absent=true;retained_removed=true",
                "air=" + replacementTransitionClientAir
                        + ";lookup_absent=" + replacementTransitionClientLookupAbsent
                        + ";retained_removed="
                        + replacementTransitionClientReferenceRemoved);
        passed &= addAssertion(assertions, "pedestal_gallery_server_snapshot_exact",
                captureServerSnapshotExact(CapturePhase.GALLERY), "exact",
                captureServerSnapshotDescription(CapturePhase.GALLERY));
        passed &= addAssertion(assertions, "pedestal_transition_server_snapshot_exact",
                captureServerSnapshotExact(CapturePhase.TRANSITION_DROPS), "exact",
                captureServerSnapshotDescription(CapturePhase.TRANSITION_DROPS));
        passed &= addAssertion(assertions,
                "pedestal_persistence_initial_server_snapshot_exact",
                persistenceInitialSnapshot.exact(PERSISTENCE_FIXTURES), "exact",
                persistenceInitialSnapshot.description());
        passed &= addAssertion(assertions, "pedestal_forced_world_save_exact",
                saveResult.completed() && saveResult.saved(), "true",
                Boolean.toString(saveResult.saved()));
        passed &= addAssertion(assertions, "pedestal_full_restart_completed",
                fullRestartCompleted, "true", Boolean.toString(fullRestartCompleted));
        passed &= addAssertion(assertions, "pedestal_reopened_server_snapshot_exact",
                reopenedSnapshot.exact(PERSISTENCE_FIXTURES), "exact",
                reopenedSnapshot.description());
        passed &= addAssertion(assertions, "pedestal_restart_persistence_exact",
                persistenceExact, "true", Boolean.toString(persistenceExact));
        for (ArtifactDigest artifact : artifacts) {
            passed &= addAssertion(assertions, "packaged_root_jar:" + artifact.modId(),
                    artifact.passed(), "regular path JAR with SHA-256",
                    artifact.passed()
                            ? artifact.fileName() + ":" + artifact.sha256()
                            : artifact.failure());
        }
        String worldIdentity = WORLD_DISPLAY_NAME + ";" + WORLD_SEED
                + ";minecraft:overworld";
        boolean worldIdentityExact = fullRestartCompleted
                && client.getServer() != null
                && WORLD_DISPLAY_NAME.equals(
                client.getServer().getSaveProperties().getLevelName())
                && client.getServer().getOverworld().getSeed() == WORLD_SEED
                && client.world != null
                && client.world.getRegistryKey() == World.OVERWORLD;
        passed &= addAssertion(assertions, "live_world_identity",
                worldIdentityExact, worldIdentity,
                worldIdentityExact ? worldIdentity : "unavailable");
        Path saveDirectory = saveDirectory(client);
        boolean savePresent = Files.isDirectory(saveDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(saveDirectory);
        passed &= addAssertion(assertions, "isolated_save_directory_present",
                savePresent, WORLD_DIRECTORY_NAME, savePresent
                        ? saveDirectory.getFileName().toString() : "missing");

        for (CapturePhase phase : CapturePhase.values()) {
            CaptureEvidence capture = captureEvidence.getOrDefault(
                    phase,
                    CaptureEvidence.missing()
            );
            passed &= addAssertion(assertions, "client_snapshot_exact:" + phase.id(),
                    capture.clientSnapshotMatchesServer(), "true",
                    Boolean.toString(capture.clientSnapshotMatchesServer()));
            passed &= addAssertion(assertions,
                    "native_framebuffer_dimensions:" + phase.id(),
                    capture.width() == FRAMEBUFFER_WIDTH
                            && capture.height() == FRAMEBUFFER_HEIGHT,
                    FRAMEBUFFER_WIDTH + "x" + FRAMEBUFFER_HEIGHT,
                    capture.width() + "x" + capture.height());
            passed &= addAssertion(assertions,
                    "completed_world_renders_before_capture:" + phase.id(),
                    capture.completedRenders()
                            >= PedestalBaselineContract.REQUIRED_COMPLETED_RENDERS,
                    Integer.toString(PedestalBaselineContract.REQUIRED_COMPLETED_RENDERS),
                    Integer.toString(capture.completedRenders()));
            passed &= addAssertion(assertions, "capture_render_ready:" + phase.id(),
                    capture.renderReady(), "true",
                    Boolean.toString(capture.renderReady()));
            passed &= addAssertion(assertions, "capture_camera_exact:" + phase.id(),
                    capture.cameraExact(), expectedCameraPoseDescription(),
                    capture.cameraDescription());
            passed &= addAssertion(assertions, "native_screenshot_written:" + phase.id(),
                    capture.screenshot().written(), phase.screenshotFileName(),
                    capture.screenshot().written()
                            ? phase.screenshotFileName() : capture.screenshot().failure());
        }

        if (!assertionNames(assertions).equals(ASSERTION_NAMES)) {
            lifecycleFailure = lifecycleFailure.isEmpty()
                    ? "Pedestal assertion inventory drifted"
                    : lifecycleFailure;
            passed = false;
        }

        JsonObject report = new JsonObject();
        report.addProperty("schema", 4);
        report.addProperty("reference_id", REFERENCE_ID);
        report.addProperty("scenario", SCENARIO_ID);
        report.addProperty("lane", "fabric-1.21.1-original");
        report.addProperty("status", passed ? "passed" : "failed");
        report.addProperty("passed", passed);
        report.addProperty("client_ticks", clientTicks);
        report.addProperty("lifecycle_failure", lifecycleFailure);
        report.add("assertions", assertions);
        report.add("world", createWorldReport());
        report.add("artifacts", createArtifactsReport(artifacts));
        report.add("pedestal", createPedestalReport());
        report.add("screenshots", createScreenshotsReport());
        return report;
    }

    private JsonObject createWorldReport() {
        JsonObject world = new JsonObject();
        world.addProperty("save_directory", WORLD_DIRECTORY_NAME);
        world.addProperty("display_name", WORLD_DISPLAY_NAME);
        world.addProperty("seed", WORLD_SEED);
        world.addProperty("dimension", "minecraft:overworld");
        world.addProperty("integrated", true);
        world.addProperty("reopened", fullRestartCompleted);
        return world;
    }

    private JsonArray createArtifactsReport(List<ArtifactDigest> artifacts) {
        JsonArray result = new JsonArray();
        for (ArtifactDigest artifact : artifacts) {
            JsonObject value = new JsonObject();
            value.addProperty("mod_id", artifact.modId());
            value.addProperty("origin_kind", artifact.originKind());
            value.addProperty("file_name", artifact.fileName());
            value.addProperty("size", artifact.size());
            value.addProperty("sha256", artifact.sha256());
            result.add(value);
        }
        return result;
    }

    private JsonObject createPedestalReport() {
        JsonObject pedestal = new JsonObject();
        pedestal.add("resource_pins", dataProbe.resources().toJson());
        pedestal.addProperty("multipart_clause_count",
                dataProbe.resources().multipartClauseCount());
        pedestal.add("registry", registryProbe.toJson());
        pedestal.add("data", dataProbe.toJson());
        pedestal.add("placement", placementProbe.toJson());
        pedestal.add("shapes", shapeProbe.toJson());
        pedestal.add("interactions", interactionProbe.toJson());
        pedestal.add("inventory", inventoryProbe.toJson());
        pedestal.add("dispensers", dispenserProbe.toJson());
        pedestal.add("transitions", createTransitionsReport());
        pedestal.add("gallery_snapshot",
                captureServerSnapshot(CapturePhase.GALLERY).toJson());
        pedestal.add("transition_snapshot",
                captureServerSnapshot(CapturePhase.TRANSITION_DROPS).toJson());
        pedestal.add("persistence_initial_snapshot",
                persistenceInitialSnapshot.toJson());
        pedestal.add("persistence_reopened_snapshot", reopenedSnapshot.toJson());
        pedestal.addProperty("forced_save", saveResult.saved());
        pedestal.addProperty("full_restart", fullRestartCompleted);
        pedestal.addProperty("persistence_exact", persistenceExact);
        pedestal.addProperty("required_stable_renders",
                PedestalBaselineContract.REQUIRED_COMPLETED_RENDERS);
        pedestal.addProperty("required_light_ready_client_ticks",
                PedestalBaselineContract.REQUIRED_LIGHT_READY_CLIENT_TICKS);
        JsonArray limitations = new JsonArray();
        limitations.add(PedestalBaselineContract.VERTICAL_CARPET_LIMITATION);
        pedestal.add("limitations", limitations);
        return pedestal;
    }

    private JsonObject createTransitionsReport() {
        JsonObject transitions = transitionProbe.toJson();
        transitions.addProperty(
                "stack_client_lookup_absent",
                stackTransitionClientLookupAbsent
        );
        transitions.addProperty(
                "stack_client_retained_reference_removed",
                stackTransitionClientReferenceRemoved
        );
        transitions.addProperty(
                "replacement_client_lookup_absent",
                replacementTransitionClientLookupAbsent
        );
        transitions.addProperty(
                "replacement_client_retained_reference_removed",
                replacementTransitionClientReferenceRemoved
        );
        transitions.addProperty(
                "replacement_client_air",
                replacementTransitionClientAir
        );
        return transitions;
    }

    private JsonArray createScreenshotsReport() {
        JsonArray screenshots = new JsonArray();
        for (CapturePhase phase : CapturePhase.values()) {
            CaptureEvidence capture = captureEvidence.getOrDefault(
                    phase,
                    CaptureEvidence.missing()
            );
            JsonObject value = new JsonObject();
            value.addProperty("step", phase.id());
            value.addProperty("file", "screenshots/" + phase.screenshotFileName());
            value.addProperty("width", capture.width());
            value.addProperty("height", capture.height());
            value.addProperty("size", capture.screenshot().size());
            value.addProperty("sha256", capture.screenshot().sha256());
            value.addProperty("completed_render_count", capture.completedRenders());
            value.addProperty("source", "minecraft-framebuffer");
            value.addProperty("edited", false);
            screenshots.add(value);
        }
        return screenshots;
    }

    private boolean captureServerSnapshotExact(CapturePhase phase) {
        return captureServerSnapshot(phase).exact(phase.fixtures());
    }

    private String captureServerSnapshotDescription(CapturePhase phase) {
        return captureServerSnapshot(phase).description();
    }

    private SceneSnapshot captureServerSnapshot(CapturePhase phase) {
        CaptureEvidence capture = captureEvidence.get(phase);
        if (capture == null) return SceneSnapshot.missing();
        if (phase == CapturePhase.PERSISTENCE_REOPENED) return reopenedSnapshot;
        if (phase == CapturePhase.PERSISTENCE_INITIAL) {
            return persistenceInitialSnapshot;
        }
        return capture.serverSnapshot();
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

    private static String expectedCameraPoseDescription() {
        return "first_person=true;x=" + CAMERA_X
                + ";y=" + CAMERA_Y
                + ";z=" + CAMERA_Z
                + ";yaw=" + CAMERA_YAW
                + ";pitch=" + CAMERA_PITCH
                + ";on_ground=true";
    }

    private static ServerPlayerEntity requirePlayer(
            IntegratedServer server,
            UUID playerId
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("The integrated server has no player");
        }
        return player;
    }

    private static Block requiredPedestal() {
        return Registries.BLOCK.getOrEmpty(PEDESTAL_ID).orElseThrow(
                () -> new IllegalStateException("Missing block " + PEDESTAL_ID)
        );
    }

    private static Item requiredPedestalItem() {
        return Registries.ITEM.getOrEmpty(PEDESTAL_ID).orElseThrow(
                () -> new IllegalStateException("Missing item " + PEDESTAL_ID)
        );
    }

    private static Item requiredItem(String id) {
        return Registries.ITEM.getOrEmpty(identifier(id)).orElseThrow(
                () -> new IllegalStateException("Missing item " + id)
        );
    }

    private static PlacementEvidence nativePlacePedestal(
            ServerWorld world,
            ServerPlayerEntity player,
            BlockPos position,
            boolean waterlogged
    ) {
        Item item = requiredPedestalItem();
        if (!(item instanceof BlockItem blockItem)) {
            throw new IllegalStateException("Pedestal item is not a BlockItem");
        }
        if (waterlogged) {
            world.setBlockState(position, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
        }
        ItemStack stack = new ItemStack(item);
        int before = stack.getCount();
        player.setStackInHand(Hand.MAIN_HAND, stack);
        BlockPos support = position.down();
        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(support).add(0.0, 0.5, 0.0),
                Direction.UP,
                support,
                false
        );
        ActionResult result = blockItem.useOnBlock(
                new ItemUsageContext(player, Hand.MAIN_HAND, hit)
        );
        BlockState state = world.getBlockState(position);
        PlacementEvidence evidence = new PlacementEvidence(
                result.name(),
                result.isAccepted(),
                before,
                stack.getCount(),
                blockItem.getBlock() == requiredPedestal(),
                Registries.BLOCK.getId(state.getBlock()).toString(),
                stateDescription(state)
        );
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        return evidence;
    }

    private static boolean loadArenaChunks(ServerWorld world) {
        boolean loaded = true;
        for (ChunkPos chunk : ARENA_CHUNKS) {
            loaded &= world.getChunkManager()
                    .getChunk(chunk.x, chunk.z, ChunkStatus.FULL, true) != null;
        }
        return loaded;
    }

    private static void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-16, ARENA_FLOOR_Y, -18);
        BlockPos end = new BlockPos(16, ARENA_FLOOR_Y + 8, 15);
        for (BlockPos position : BlockPos.iterate(start, end)) {
            world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        world.getEntitiesByClass(
                ItemEntity.class,
                new Box(
                        start.getX(),
                        start.getY(),
                        start.getZ(),
                        end.getX() + 1,
                        end.getY() + 1,
                        end.getZ() + 1
                ),
                entity -> true
        ).forEach(ItemEntity::discard);
    }

    private static void buildArenaFloor(ServerWorld world) {
        for (int x = -16; x <= 16; x++) {
            for (int z = -18; z <= 15; z++) {
                Block floor = (x + z) % 6 == 0
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
    }

    private static void clearScene(ServerWorld world) {
        for (BlockPos position : BlockPos.iterate(
                new BlockPos(-10, FIXTURE_Y, -1),
                new BlockPos(10, FIXTURE_Y + 6, 10)
        )) {
            world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        world.getEntitiesByClass(ItemEntity.class, TRANSITION_DROP_BOX, entity -> true)
                .forEach(ItemEntity::discard);
    }

    private static void clearTransientProbeArea(ServerWorld world) {
        for (DispenserFixture fixture : DISPENSER_FIXTURES) {
            world.setBlockState(fixture.power(), Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_ALL);
            world.setBlockState(fixture.dispenser(), Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_ALL);
            world.setBlockState(fixture.target(), Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_ALL);
        }
        world.getEntitiesByClass(
                ItemEntity.class,
                DISPENSER_ENTITY_BOX,
                entity -> true
        ).forEach(ItemEntity::discard);
        world.getEntitiesByClass(
                ArrowEntity.class,
                DISPENSER_ENTITY_BOX,
                entity -> true
        ).forEach(ArrowEntity::discard);
        clearBlockColumn(world, INTERACTION_POS, 4);
        clearBlockColumn(world, NATIVE_PLACEMENT_POS, 4);
        clearBlockColumn(world, WATERLOGGED_PLACEMENT_POS, 4);
        clearBlockColumn(world, LOOT_PROBE_POS, 4);
        clearBlockColumn(world, TWO_PEDESTAL_PROBE_POS, 4);
        clearBlockColumn(world, THREE_PEDESTAL_PROBE_POS, 4);
    }

    private static void clearBlockColumn(
            ServerWorld world,
            BlockPos position,
            int height
    ) {
        for (int offset = 0; offset < height; offset++) {
            world.setBlockState(position.up(offset), Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_ALL);
        }
    }

    private static String stateDescription(BlockState state) {
        return BlockArgumentParser.stringifyBlockState(state);
    }

    private static Map<String, String> stateProperties(BlockState state) {
        Map<String, String> values = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            values.put(property.getName(), propertyValue(state, property));
        }
        return Collections.unmodifiableMap(values);
    }

    private static Map<String, String> expectedDefaultProperties() {
        Map<String, String> values = new TreeMap<>();
        values.put("cloth_color", "white");
        values.put("decoration", "false");
        values.put("facing", "north");
        values.put("shape", "full");
        values.put("waterlogged", "false");
        return Collections.unmodifiableMap(values);
    }

    private static String propertyValue(BlockState state, String propertyName) {
        Property<?> property = state.getBlock().getStateManager()
                .getProperty(propertyName);
        if (property == null || !state.contains(property)) return "missing";
        return propertyValue(state, property);
    }

    private static <T extends Comparable<T>> String propertyValue(
            BlockState state,
            Property<T> property
    ) {
        return property.name(state.get(property));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(
            BlockState state,
            String propertyName,
            String valueName
    ) {
        Property property = state.getBlock().getStateManager().getProperty(propertyName);
        if (property == null) {
            throw new IllegalStateException("Missing Pedestal property " + propertyName);
        }
        Comparable value = (Comparable) property.parse(valueName).orElse(null);
        if (value == null) {
            throw new IllegalStateException("Missing Pedestal property value "
                    + propertyName + "=" + valueName);
        }
        return state.with(property, value);
    }

    private static String stackDescription(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        return Registries.ITEM.getId(stack.getItem()) + "x" + stack.getCount();
    }

    private static Map<String, Integer> stackCounts(List<ItemStack> stacks) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("This Java runtime has no SHA-256", exception);
        }
    }

    private static Identifier etherologyId(String path) {
        return Identifier.of("etherology", path);
    }

    private static Identifier identifier(String value) {
        return Identifier.of(value);
    }

    private static Path saveDirectory(MinecraftClient client) {
        return client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
    }

    private void transition(Stage nextStage) {
        stage = nextStage;
        stageClientTicks = 0;
    }

    private void recordServerFailure(RuntimeException exception) {
        LOGGER.error("Original Pedestal server operation failed", exception);
        serverFailure = exception.getClass().getSimpleName()
                + ": " + exception.getMessage();
    }

    private static PedestalFixture fixture(
            int x,
            int yOffset,
            String shape,
            boolean decoration,
            String color,
            String facing,
            boolean waterlogged,
            String item,
            String carpet,
            boolean blockEntityPresent
    ) {
        return fixtureAt(
                new BlockPos(x, FIXTURE_Y + yOffset, 4),
                shape,
                decoration,
                color,
                facing,
                waterlogged,
                item,
                carpet,
                blockEntityPresent
        );
    }

    private static PedestalFixture fixtureAt(
            BlockPos position,
            String shape,
            boolean decoration,
            String color,
            String facing,
            boolean waterlogged,
            String item,
            String carpet,
            boolean blockEntityPresent
    ) {
        return new PedestalFixture(
                position,
                shape,
                decoration,
                color,
                facing,
                waterlogged,
                item,
                carpet,
                blockEntityPresent
        );
    }

    private static List<DispenserFixture> createDispenserFixtures() {
        List<DispenserFixture> fixtures = new ArrayList<>();
        List<Direction> directions = List.of(
                Direction.DOWN,
                Direction.UP,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST
        );
        for (int index = 0; index < directions.size(); index++) {
            Direction direction = directions.get(index);
            BlockPos dispenser = new BlockPos(-14 + index * 3, FIXTURE_Y + 3, 14);
            fixtures.add(new DispenserFixture(
                    "item",
                    direction,
                    dispenser,
                    dispenser.offset(direction),
                    powerPosition(dispenser, direction),
                    "minecraft:amethyst_shard",
                    "",
                    "",
                    "white"
            ));
        }
        List<Direction> carpetDirections = List.of(
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST
        );
        for (int index = 0; index < carpetDirections.size(); index++) {
            Direction direction = carpetDirections.get(index);
            BlockPos dispenser = new BlockPos(-8 + index * 5, FIXTURE_Y + 1, 10);
            fixtures.add(new DispenserFixture(
                    "carpet",
                    direction,
                    dispenser,
                    dispenser.offset(direction),
                    powerPosition(dispenser, direction),
                    "minecraft:purple_carpet",
                    "",
                    "",
                    "purple"
            ));
        }
        BlockPos occupiedCarpetDispenser = new BlockPos(-15, FIXTURE_Y, 5);
        fixtures.add(new DispenserFixture(
                "occupied-carpet-display",
                Direction.UP,
                occupiedCarpetDispenser,
                occupiedCarpetDispenser.up(),
                powerPosition(occupiedCarpetDispenser, Direction.UP),
                "minecraft:purple_carpet",
                "",
                "minecraft:red_carpet",
                "red"
        ));
        BlockPos fullTargetDispenser = new BlockPos(15, FIXTURE_Y, 5);
        fixtures.add(new DispenserFixture(
                "full-target-fallback",
                Direction.WEST,
                fullTargetDispenser,
                fullTargetDispenser.west(),
                powerPosition(fullTargetDispenser, Direction.WEST),
                "minecraft:arrow",
                "minecraft:diamond",
                "minecraft:red_carpet",
                "red"
        ));
        return List.copyOf(fixtures);
    }

    private static BlockPos powerPosition(BlockPos dispenser, Direction output) {
        Direction powerDirection = output.getAxis() == Direction.Axis.X
                ? Direction.NORTH
                : Direction.EAST;
        return dispenser.offset(powerDirection);
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_INITIAL_SETUP,
        WAITING_FOR_DISPENSERS,
        WAITING_FOR_TRANSITION_PRECONDITION,
        WAITING_FOR_SCENE_SETUP,
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
        GALLERY("gallery", 0, GALLERY_FIXTURES),
        TRANSITION_DROPS("transition-drops", 1, TRANSITION_FIXTURES),
        PERSISTENCE_INITIAL("persistence-initial", 2, PERSISTENCE_FIXTURES),
        PERSISTENCE_REOPENED("persistence-reopened", 3, PERSISTENCE_FIXTURES);

        private final String id;
        private final int screenshotIndex;
        private final List<PedestalFixture> fixtures;

        CapturePhase(
                String id,
                int screenshotIndex,
                List<PedestalFixture> fixtures
        ) {
            this.id = id;
            this.screenshotIndex = screenshotIndex;
            this.fixtures = fixtures;
        }

        private String id() {
            return id;
        }

        private String screenshotFileName() {
            return PedestalBaselineContract.SCREENSHOT_FILE_NAMES.get(screenshotIndex);
        }

        private List<PedestalFixture> fixtures() {
            return fixtures;
        }
    }

    private record ResourceObservation(long size, String sha256) {

        private JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("size", size);
            value.addProperty("sha256", sha256);
            return value;
        }
    }

    private record ResourceProbe(
            Map<String, ResourceObservation> observations,
            int multipartClauseCount,
            String failure
    ) {

        private static ResourceProbe captureClient(ResourceManager manager) {
            return capture(manager, "assets/", true);
        }

        private static ResourceProbe captureData(ResourceManager manager) {
            return capture(manager, "data/", false);
        }

        private static ResourceProbe capture(
                ResourceManager manager,
                String prefix,
                boolean inspectMultipart
        ) {
            Map<String, ResourceObservation> observations = new LinkedHashMap<>();
            int clauses = inspectMultipart ? -1 : 0;
            try {
                for (Map.Entry<String, PedestalBaselineContract.ResourcePin> entry
                        : PedestalBaselineContract.RESOURCE_PINS.entrySet()) {
                    String path = entry.getKey();
                    if (!path.startsWith(prefix)) continue;
                    String namespaceAndPath = path.substring(prefix.length());
                    int slash = namespaceAndPath.indexOf('/');
                    Identifier id = Identifier.of(
                            namespaceAndPath.substring(0, slash),
                            namespaceAndPath.substring(slash + 1)
                    );
                    Resource resource = manager.getResource(id).orElseThrow(
                            () -> new IOException("Missing resource " + path)
                    );
                    byte[] content;
                    try (InputStream input = resource.getInputStream()) {
                        content = input.readAllBytes();
                    }
                    observations.put(
                            path,
                            new ResourceObservation(content.length, sha256(content))
                    );
                    if (path.equals("assets/etherology/blockstates/pedestal.json")) {
                        JsonObject json = JsonParser.parseString(
                                new String(content, StandardCharsets.UTF_8)
                        ).getAsJsonObject();
                        clauses = json.getAsJsonArray("multipart").size();
                    }
                }
                return new ResourceProbe(
                        Collections.unmodifiableMap(observations),
                        clauses,
                        ""
                );
            } catch (IOException | RuntimeException exception) {
                return new ResourceProbe(
                        Collections.unmodifiableMap(observations),
                        clauses,
                        exception.getClass().getSimpleName()
                                + ": " + exception.getMessage()
                );
            }
        }

        private ResourceProbe withServerData(ResourceProbe data) {
            Map<String, ResourceObservation> combined = new LinkedHashMap<>(observations);
            combined.putAll(data.observations());
            String combinedFailure = failure.isEmpty() ? data.failure() : failure;
            return new ResourceProbe(
                    Collections.unmodifiableMap(combined),
                    multipartClauseCount,
                    combinedFailure
            );
        }

        private static ResourceProbe missing() {
            return new ResourceProbe(Map.of(), -1, "not captured");
        }

        private boolean clientExact() {
            Map<String, PedestalBaselineContract.ResourcePin> clientPins =
                    PedestalBaselineContract.RESOURCE_PINS.entrySet().stream()
                            .filter(entry -> entry.getKey().startsWith("assets/"))
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (left, right) -> left,
                                    LinkedHashMap::new
                            ));
            return failure.isEmpty()
                    && multipartClauseCount
                    == PedestalBaselineContract.EXPECTED_MULTIPART_CLAUSE_COUNT
                    && observationsMatch(clientPins);
        }

        private boolean exact() {
            return failure.isEmpty()
                    && multipartClauseCount
                    == PedestalBaselineContract.EXPECTED_MULTIPART_CLAUSE_COUNT
                    && observationsMatch(PedestalBaselineContract.RESOURCE_PINS);
        }

        private boolean observationsMatch(
                Map<String, PedestalBaselineContract.ResourcePin> pins
        ) {
            if (!observations.keySet().equals(pins.keySet())) return false;
            for (Map.Entry<String, PedestalBaselineContract.ResourcePin> entry
                    : pins.entrySet()) {
                ResourceObservation actual = observations.get(entry.getKey());
                if (actual == null
                        || actual.size() != entry.getValue().size()
                        || !actual.sha256().equals(entry.getValue().sha256())) {
                    return false;
                }
            }
            return true;
        }

        private String description() {
            return observations.size() + " resources;multipart="
                    + multipartClauseCount + ";failure=" + failure;
        }

        private JsonObject toJson() {
            JsonObject result = new JsonObject();
            observations.forEach((path, observation) ->
                    result.add(path, observation.toJson()));
            return result;
        }
    }

    private record RegistryProbe(
            boolean blockPresent,
            boolean itemPresent,
            boolean blockEntityTypePresent,
            String blockClass,
            String itemClass,
            boolean blockItemMapping,
            String translation,
            Map<String, String> defaultProperties,
            int stateCount,
            int defaultNetworkId,
            int uniqueNetworkIds,
            boolean networkIdsExact,
            List<String> horizontalFacings
    ) {

        private static RegistryProbe capture() {
            boolean blockPresent = Registries.BLOCK.containsId(PEDESTAL_ID);
            boolean itemPresent = Registries.ITEM.containsId(PEDESTAL_ID);
            boolean typePresent = Registries.BLOCK_ENTITY_TYPE.containsId(
                    PEDESTAL_BLOCK_ENTITY_ID
            );
            if (!blockPresent || !itemPresent) return missing();
            Block block = Registries.BLOCK.get(PEDESTAL_ID);
            Item item = Registries.ITEM.get(PEDESTAL_ID);
            Set<Integer> rawIds = new HashSet<>();
            boolean rawIdsExact = true;
            for (BlockState state : block.getStateManager().getStates()) {
                int rawId = Block.STATE_IDS.getRawId(state);
                rawIdsExact &= rawId >= 0;
                rawIds.add(rawId);
            }
            int defaultRawId = Block.STATE_IDS.getRawId(block.getDefaultState());
            Property<?> facing = block.getStateManager().getProperty("facing");
            List<String> facings = facing == null
                    ? List.of()
                    : facing.getValues().stream()
                    .map(value -> propertyName(facing, value))
                    .sorted()
                    .toList();
            return new RegistryProbe(
                    true,
                    true,
                    typePresent,
                    block.getClass().getName(),
                    item.getClass().getName(),
                    item instanceof BlockItem blockItem
                            && blockItem.getBlock() == block,
                    block.getName().getString(),
                    stateProperties(block.getDefaultState()),
                    block.getStateManager().getStates().size(),
                    defaultRawId,
                    rawIds.size(),
                    rawIdsExact && defaultRawId >= 0
                            && rawIds.size()
                            == PedestalBaselineContract.EXPECTED_STATE_COUNT,
                    facings
            );
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static String propertyName(Property property, Object value) {
            return property.name((Comparable) value);
        }

        private static RegistryProbe missing() {
            return new RegistryProbe(
                    false, false, false, "missing", "missing", false,
                    "missing", Map.of(), 0, -1, 0, false, List.of()
            );
        }

        private boolean exact() {
            return blockPresent
                    && itemPresent
                    && blockEntityTypePresent
                    && blockClass.equals(
                    "ru.feytox.etherology.block.pedestal.PedestalBlock")
                    && itemClass.equals(BlockItem.class.getName())
                    && blockItemMapping
                    && translation.equals("Pedestal")
                    && defaultProperties.equals(expectedDefaultProperties())
                    && stateCount == PedestalBaselineContract.EXPECTED_STATE_COUNT
                    && networkIdsExact
                    && horizontalFacings.equals(
                    List.of("east", "north", "south", "west"));
        }

        private JsonObject toJson() {
            JsonObject result = new JsonObject();
            result.addProperty("block_present", blockPresent);
            result.addProperty("item_present", itemPresent);
            result.addProperty("block_entity_type_present", blockEntityTypePresent);
            result.addProperty("block_class", blockClass);
            result.addProperty("item_class", itemClass);
            result.addProperty("block_item_mapping", blockItemMapping);
            result.addProperty("translation", translation);
            result.add("default_properties", stringMap(defaultProperties));
            result.addProperty("state_count", stateCount);
            result.addProperty("default_state_raw_id", defaultNetworkId);
            result.addProperty("unique_state_raw_ids", uniqueNetworkIds);
            result.addProperty("network_ids_exact", networkIdsExact);
            result.add("horizontal_facing_values", stringArray(horizontalFacings));
            return result;
        }
    }

    private record DataProbe(
            ResourceProbe resources,
            boolean pickaxeMineable,
            String recipe,
            boolean advancementPresent,
            boolean lootTablePresent,
            String selfDrop,
            String failure
    ) {

        private static DataProbe capture(
                IntegratedServer server,
                ServerWorld world,
                ServerPlayerEntity player,
                ResourceProbe resources
        ) {
            try {
                Block pedestal = requiredPedestal();
                boolean pickaxe = pedestal.getDefaultState().isIn(
                        BlockTags.PICKAXE_MINEABLE
                );
                RecipeEntry<?> entry = server.getRecipeManager()
                        .get(PEDESTAL_RECIPE_ID)
                        .orElseThrow(() -> new IllegalStateException(
                                "Missing recipe " + PEDESTAL_RECIPE_ID
                        ));
                ItemStack result = entry.value().getResult(server.getRegistryManager());
                String recipe = entry.id() + "="
                        + Registries.RECIPE_TYPE.getId(entry.value().getType())
                        + "->" + Registries.ITEM.getId(result.getItem())
                        + "x" + result.getCount();
                boolean advancement = server.getAdvancementLoader()
                        .get(PEDESTAL_ADVANCEMENT_ID) != null;
                boolean lootTable = server.getReloadableRegistries()
                        .getIds(RegistryKeys.LOOT_TABLE)
                        .contains(PEDESTAL_LOOT_TABLE_ID);
                world.setBlockState(
                        LOOT_PROBE_POS,
                        pedestal.getDefaultState(),
                        Block.NOTIFY_ALL
                );
                BlockEntity blockEntity = world.getBlockEntity(LOOT_PROBE_POS);
                List<ItemStack> drops = Block.getDroppedStacks(
                        world.getBlockState(LOOT_PROBE_POS),
                        world,
                        LOOT_PROBE_POS,
                        blockEntity,
                        player,
                        new ItemStack(Items.DIAMOND_PICKAXE)
                );
                String selfDrop = stackCounts(drops).entrySet().stream()
                        .map(drop -> drop.getKey() + "x" + drop.getValue())
                        .collect(Collectors.joining("+"));
                world.setBlockState(
                        LOOT_PROBE_POS,
                        Blocks.AIR.getDefaultState(),
                        Block.NOTIFY_ALL
                );
                return new DataProbe(
                        resources,
                        pickaxe,
                        recipe,
                        advancement,
                        lootTable,
                        selfDrop.isEmpty() ? "none" : selfDrop,
                        ""
                );
            } catch (RuntimeException exception) {
                return new DataProbe(
                        resources,
                        false,
                        "missing",
                        false,
                        false,
                        "none",
                        exception.getClass().getSimpleName()
                                + ": " + exception.getMessage()
                );
            }
        }

        private static DataProbe missing() {
            return new DataProbe(
                    ResourceProbe.missing(), false, "missing", false,
                    false, "none", "not captured"
            );
        }

        private boolean exact() {
            return failure.isEmpty()
                    && resources.exact()
                    && pickaxeMineable
                    && recipe.equals(
                    "etherology:pedestal=minecraft:crafting->etherology:pedestalx2")
                    && advancementPresent
                    && lootTablePresent
                    && selfDrop.equals("etherology:pedestalx1");
        }

        private boolean sameOutcome(DataProbe other) {
            return resources.equals(other.resources)
                    && pickaxeMineable == other.pickaxeMineable
                    && recipe.equals(other.recipe)
                    && advancementPresent == other.advancementPresent
                    && lootTablePresent == other.lootTablePresent
                    && selfDrop.equals(other.selfDrop)
                    && failure.equals(other.failure);
        }

        private JsonObject toJson() {
            JsonObject result = new JsonObject();
            result.addProperty("pickaxe_mineable", pickaxeMineable);
            result.addProperty("recipe", recipe);
            result.addProperty("advancement", advancementPresent
                    ? PEDESTAL_ADVANCEMENT_ID.toString() : "missing");
            result.addProperty("loot_table", lootTablePresent
                    ? PEDESTAL_LOOT_TABLE_ID.toString() : "missing");
            result.addProperty("self_drop", selfDrop);
            result.addProperty("failure", failure);
            return result;
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

        private static PlacementEvidence missing() {
            return new PlacementEvidence(
                    "missing", false, 0, 0, false, "missing", "missing"
            );
        }

        private boolean exact(boolean waterlogged) {
            BlockState expectedState = withProperty(
                    requiredPedestal().getDefaultState(),
                    "waterlogged",
                    Boolean.toString(waterlogged)
            );
            return accepted
                    && beforeCount == 1
                    && afterCount == 0
                    && blockItemMapping
                    && placedId.equals(PEDESTAL_ID.toString())
                    && placedState.equals(stateDescription(expectedState));
        }

        private String description() {
            return actionResult + ";accepted=" + accepted + ";count="
                    + beforeCount + "->" + afterCount + ";mapping="
                    + blockItemMapping + ";state=" + placedState;
        }

        private JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("action_result", actionResult);
            value.addProperty("accepted", accepted);
            value.addProperty("before_count", beforeCount);
            value.addProperty("after_count", afterCount);
            value.addProperty("block_item_mapping", blockItemMapping);
            value.addProperty("placed_id", placedId);
            value.addProperty("placed_state", placedState);
            return value;
        }
    }

    private record PlacementProbe(
            PlacementEvidence standalone,
            PlacementEvidence waterlogged,
            List<String> stackShapes,
            Map<String, Boolean> blockEntityPresence
    ) {

        private static PlacementProbe capture(
                ServerWorld world,
                ServerPlayerEntity player
        ) {
            PlacementEvidence standalone = nativePlacePedestal(
                    world, player, NATIVE_PLACEMENT_POS, false
            );
            PlacementEvidence waterlogged = nativePlacePedestal(
                    world, player, WATERLOGGED_PLACEMENT_POS, true
            );
            BlockPos two = TWO_PEDESTAL_PROBE_POS;
            nativePlacePedestal(world, player, two, false);
            nativePlacePedestal(world, player, two.up(), false);
            BlockPos three = THREE_PEDESTAL_PROBE_POS;
            nativePlacePedestal(world, player, three, false);
            nativePlacePedestal(world, player, three.up(), false);
            nativePlacePedestal(world, player, three.up(2), false);
            List<String> shapes = List.of(
                    propertyValue(world.getBlockState(NATIVE_PLACEMENT_POS), "shape"),
                    propertyValue(world.getBlockState(two), "shape"),
                    propertyValue(world.getBlockState(two.up()), "shape"),
                    propertyValue(world.getBlockState(three), "shape"),
                    propertyValue(world.getBlockState(three.up()), "shape"),
                    propertyValue(world.getBlockState(three.up(2)), "shape")
            );
            Map<String, Boolean> presence = new LinkedHashMap<>();
            presence.put("full", world.getBlockEntity(NATIVE_PLACEMENT_POS) != null);
            presence.put("bottom", world.getBlockEntity(three) != null);
            presence.put("middle", world.getBlockEntity(three.up()) != null);
            presence.put("top", world.getBlockEntity(three.up(2)) != null);
            return new PlacementProbe(
                    standalone,
                    waterlogged,
                    shapes,
                    Collections.unmodifiableMap(presence)
            );
        }

        private static PlacementProbe missing() {
            return new PlacementProbe(
                    PlacementEvidence.missing(),
                    PlacementEvidence.missing(),
                    List.of(),
                    Map.of()
            );
        }

        private boolean stackShapesExact() {
            return stackShapes.equals(List.of(
                    "full", "bottom", "top", "bottom", "middle", "top"
            ));
        }

        private boolean blockEntityPresenceExact() {
            return blockEntityPresence.equals(Map.of(
                    "full", true,
                    "bottom", false,
                    "middle", false,
                    "top", true
            ));
        }

        private boolean exact() {
            return standalone.exact(false)
                    && waterlogged.exact(true)
                    && stackShapesExact()
                    && blockEntityPresenceExact();
        }

        private String stackShapeDescription() {
            return stackShapes.toString();
        }

        private String blockEntityDescription() {
            return blockEntityPresence.toString();
        }

        private JsonObject toJson() {
            JsonObject result = new JsonObject();
            result.add("standalone", standalone.toJson());
            result.add("waterlogged", waterlogged.toJson());
            result.add("stack_shapes", stringArray(stackShapes));
            JsonObject presence = new JsonObject();
            blockEntityPresence.forEach(presence::addProperty);
            result.add("block_entity_presence", presence);
            return result;
        }
    }

    private record ShapeObservation(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            double volume
    ) {

        private static ShapeObservation capture(VoxelShape shape) {
            Box bounds = shape.getBoundingBox();
            double[] volume = {0.0};
            shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                    volume[0] += (maxX - minX) * (maxY - minY) * (maxZ - minZ));
            return new ShapeObservation(
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    volume[0]
            );
        }

        private boolean approximately(ShapeObservation expected) {
            return close(minX, expected.minX)
                    && close(minY, expected.minY)
                    && close(minZ, expected.minZ)
                    && close(maxX, expected.maxX)
                    && close(maxY, expected.maxY)
                    && close(maxZ, expected.maxZ)
                    && close(volume, expected.volume);
        }

        private static boolean close(double left, double right) {
            return Math.abs(left - right) <= 0.000000001;
        }

        private String description() {
            return String.format(
                    Locale.ROOT,
                    "%.9f,%.9f,%.9f->%.9f,%.9f,%.9f;volume=%.9f",
                    minX, minY, minZ, maxX, maxY, maxZ, volume
            );
        }

        private JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("min_x", minX);
            value.addProperty("min_y", minY);
            value.addProperty("min_z", minZ);
            value.addProperty("max_x", maxX);
            value.addProperty("max_y", maxY);
            value.addProperty("max_z", maxZ);
            value.addProperty("volume", volume);
            return value;
        }
    }

    private record ShapeProbe(Map<String, ShapeObservation> shapes) {

        private static ShapeProbe capture(ServerWorld world) {
            Map<String, ShapeObservation> values = new LinkedHashMap<>();
            for (String shapeName : List.of("bottom", "middle", "top", "full")) {
                BlockState state = withProperty(
                        requiredPedestal().getDefaultState(),
                        "shape",
                        shapeName
                );
                values.put(
                        shapeName,
                        ShapeObservation.capture(
                                state.getOutlineShape(world, INTERACTION_POS)
                        )
                );
            }
            return new ShapeProbe(Collections.unmodifiableMap(values));
        }

        private static ShapeProbe missing() {
            return new ShapeProbe(Map.of());
        }

        private static Map<String, ShapeObservation> expected() {
            Map<String, ShapeObservation> values = new LinkedHashMap<>();
            values.put("bottom", new ShapeObservation(
                    0.1875, 0.0, 0.1875, 0.8125, 1.0, 0.8125,
                    0.2763671875
            ));
            values.put("middle", new ShapeObservation(
                    0.25, 0.0, 0.25, 0.75, 1.0, 0.75, 0.25
            ));
            values.put("top", new ShapeObservation(
                    0.125, 0.0, 0.125, 0.875, 1.0, 0.875,
                    0.34765625
            ));
            values.put("full", new ShapeObservation(
                    0.125, 0.0, 0.125, 0.875, 1.0, 0.875,
                    0.3740234375
            ));
            return Collections.unmodifiableMap(values);
        }

        private boolean exact() {
            if (!shapes.keySet().equals(expected().keySet())) return false;
            for (Map.Entry<String, ShapeObservation> entry : expected().entrySet()) {
                if (!shapes.get(entry.getKey()).approximately(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private static String expectedDescription() {
            return expected().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue().description())
                    .collect(Collectors.joining(";"));
        }

        private String description() {
            return shapes.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue().description())
                    .collect(Collectors.joining(";"));
        }

        private JsonObject toJson() {
            JsonObject result = new JsonObject();
            shapes.forEach((name, shape) -> result.add(name, shape.toJson()));
            return result;
        }
    }

    private record InteractionStep(
            String id,
            String actionResult,
            String beforeHand,
            String afterHand,
            String itemSlot,
            String carpetSlot,
            String decoration,
            String color,
            String facing
    ) {

        private boolean matches(
                String expectedId,
                String expectedBefore,
                String expectedAfter,
                String expectedItem,
                String expectedCarpet,
                boolean expectedDecoration,
                String expectedColor
        ) {
            return id.equals(expectedId)
                    && actionResult.equals("CONSUME")
                    && beforeHand.equals(expectedBefore)
                    && afterHand.equals(expectedAfter)
                    && itemSlot.equals(expectedItem)
                    && carpetSlot.equals(expectedCarpet)
                    && decoration.equals(Boolean.toString(expectedDecoration))
                    && color.equals(expectedColor)
                    && facing.equals("north");
        }

        private String description() {
            return id + "=" + actionResult + ";hand=" + beforeHand + "->"
                    + afterHand + ";slots=" + itemSlot + "," + carpetSlot
                    + ";decoration=" + decoration + ";color=" + color
                    + ";facing=" + facing;
        }

        private JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("action_result", actionResult);
            value.addProperty("before_hand", beforeHand);
            value.addProperty("after_hand", afterHand);
            value.addProperty("item_slot", itemSlot);
            value.addProperty("carpet_slot", carpetSlot);
            value.addProperty("decoration", decoration);
            value.addProperty("color", color);
            value.addProperty("facing", facing);
            return value;
        }
    }

    private record InteractionProbe(List<InteractionStep> steps) {

        private static InteractionProbe missing() {
            return new InteractionProbe(List.of());
        }

        private boolean exact() {
            if (steps.size() != 10) return false;
            return steps.get(0).matches(
                    "red-carpet-stack-place", "minecraft:red_carpetx2",
                    "minecraft:red_carpetx1", "empty", "minecraft:red_carpetx1",
                    true, "red")
                    && steps.get(1).matches(
                    "different-carpet-stack-noop", "minecraft:blue_carpetx2",
                    "minecraft:blue_carpetx2", "empty", "minecraft:red_carpetx1",
                    true, "red")
                    && steps.get(2).matches(
                    "single-carpet-swap", "minecraft:blue_carpetx1",
                    "minecraft:red_carpetx1", "empty", "minecraft:blue_carpetx1",
                    true, "blue")
                    && steps.get(3).matches(
                    "same-carpet-retrieve", "minecraft:blue_carpetx1",
                    "minecraft:blue_carpetx2", "empty", "empty",
                    false, "white")
                    && steps.get(4).matches(
                    "diamond-place", "minecraft:diamondx2", "minecraft:diamondx1",
                    "minecraft:diamondx1", "empty", false, "white")
                    && steps.get(5).matches(
                    "different-item-noop", "minecraft:emeraldx2",
                    "minecraft:emeraldx2", "minecraft:diamondx1", "empty",
                    false, "white")
                    && steps.get(6).matches(
                    "full-same-item-noop", "minecraft:diamondx64",
                    "minecraft:diamondx64", "minecraft:diamondx1", "empty",
                    false, "white")
                    && steps.get(7).matches(
                    "same-item-retrieve", "minecraft:diamondx1",
                    "minecraft:diamondx2", "empty", "empty",
                    false, "white")
                    && steps.get(8).matches(
                    "empty-hand-item-first", "empty", "minecraft:diamondx1",
                    "empty", "minecraft:red_carpetx1", true, "red")
                    && steps.get(9).matches(
                    "empty-hand-carpet-second", "empty", "minecraft:red_carpetx1",
                    "empty", "empty", false, "white");
        }

        private static String expectedDescription() {
            return PedestalBaselineContract.INTERACTION_STEPS.toString();
        }

        private String description() {
            return steps.stream()
                    .map(InteractionStep::description)
                    .collect(Collectors.joining("|"));
        }

        private JsonArray toJson() {
            JsonArray result = new JsonArray();
            steps.forEach(step -> result.add(step.toJson()));
            return result;
        }
    }

    private record InventoryProbe(
            String blockEntityType,
            String blockEntityClass,
            int size,
            int maxCount,
            Map<String, Integer> availableSlotCounts,
            boolean insertDenied,
            boolean extractDenied,
            List<String> nbtKeys,
            int nbtItemCount,
            boolean nbtRemovedPresent,
            boolean nbtRemoved
    ) {

        private static InventoryProbe capture(
                ServerWorld world,
                BlockPos position,
                ServerPlayerEntity player
        ) {
            BlockEntity blockEntity = world.getBlockEntity(position);
            if (!(blockEntity instanceof Inventory inventory)
                    || !(blockEntity instanceof SidedInventory sided)) return missing();
            Map<String, Integer> slots = new LinkedHashMap<>();
            boolean insertDenied = true;
            boolean extractDenied = true;
            for (Direction direction : Direction.values()) {
                slots.put(direction.getName(), sided.getAvailableSlots(direction).length);
                insertDenied &= !sided.canInsert(
                        0,
                        new ItemStack(Items.DIAMOND),
                        direction
                );
                extractDenied &= !sided.canExtract(
                        0,
                        inventory.getStack(0),
                        direction
                );
            }
            NbtCompound nbt = blockEntity.createNbtWithIdentifyingData(
                    world.getRegistryManager()
            );
            List<String> keys = nbt.getKeys().stream().sorted().toList();
            return new InventoryProbe(
                    Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()).toString(),
                    blockEntity.getClass().getName(),
                    inventory.size(),
                    inventory.getMaxCountPerStack(),
                    Collections.unmodifiableMap(slots),
                    insertDenied,
                    extractDenied,
                    keys,
                    nbt.getList("Items", NbtElement.COMPOUND_TYPE).size(),
                    nbt.contains("removed", NbtElement.BYTE_TYPE),
                    nbt.getBoolean("removed")
            );
        }

        private static InventoryProbe missing() {
            return new InventoryProbe(
                    "missing", "missing", 0, 0, Map.of(), false, false,
                    List.of(), 0, false, false
            );
        }

        private boolean sidedClosed() {
            return availableSlotCounts.size() == Direction.values().length
                    && availableSlotCounts.values().stream().allMatch(count -> count == 0)
                    && insertDenied
                    && extractDenied;
        }

        private String sidedDescription() {
            return availableSlotCounts + ";insert_denied=" + insertDenied
                    + ";extract_denied=" + extractDenied;
        }

        private boolean exact() {
            return blockEntityType.equals(PEDESTAL_BLOCK_ENTITY_ID.toString())
                    && blockEntityClass.equals(
                    "ru.feytox.etherology.block.pedestal.PedestalBlockEntity")
                    && size == 2
                    && maxCount == 1
                    && sidedClosed()
                    && nbtKeys.equals(List.of(
                            "Items", "id", "removed", "x", "y", "z"
                    ))
                    && nbtItemCount == 2
                    && nbtRemovedPresent
                    && !nbtRemoved;
        }

        private JsonObject toJson() {
            JsonObject result = new JsonObject();
            result.addProperty("block_entity_type", blockEntityType);
            result.addProperty("block_entity_class", blockEntityClass);
            result.addProperty("size", size);
            result.addProperty("max_count_per_stack", maxCount);
            JsonObject slots = new JsonObject();
            availableSlotCounts.forEach(slots::addProperty);
            result.add("available_slot_counts", slots);
            result.addProperty("insert_denied", insertDenied);
            result.addProperty("extract_denied", extractDenied);
            result.add("nbt_keys", stringArray(nbtKeys));
            result.addProperty("nbt_item_count", nbtItemCount);
            result.addProperty("nbt_removed_present", nbtRemovedPresent);
            result.addProperty("nbt_removed", nbtRemoved);
            return result;
        }
    }

    private record InteractionResult(
            InteractionProbe interactions,
            InventoryProbe inventory
    ) {
    }

    private record DispenserFixture(
            String kind,
            Direction direction,
            BlockPos dispenser,
            BlockPos target,
            BlockPos power,
            String inputId,
            String preloadItemId,
            String preloadCarpetId,
            String color
    ) {
    }

    private record DispenserObservation(
            String kind,
            String direction,
            String dispenserSlot,
            String pedestalItemSlot,
            String pedestalCarpetSlot,
            String decoration,
            String color,
            String facing
    ) {

        private boolean exact() {
            if (!dispenserSlot.endsWith("x1")) return false;
            if (kind.equals("item")) {
                return dispenserSlot.equals("minecraft:amethyst_shardx1")
                        && pedestalItemSlot.equals("minecraft:amethyst_shardx1")
                        && pedestalCarpetSlot.equals("empty")
                        && decoration.equals("false")
                        && color.equals("white")
                        && facing.equals("north");
            }
            if (kind.equals("occupied-carpet-display")) {
                return direction.equals("up")
                        && dispenserSlot.equals("minecraft:purple_carpetx1")
                        && pedestalItemSlot.equals("minecraft:purple_carpetx1")
                        && pedestalCarpetSlot.equals("minecraft:red_carpetx1")
                        && decoration.equals("true")
                        && color.equals("red")
                        && facing.equals("north");
            }
            if (kind.equals("full-target-fallback")) {
                return direction.equals("west")
                        && dispenserSlot.equals("minecraft:arrowx1")
                        && pedestalItemSlot.equals("minecraft:diamondx1")
                        && pedestalCarpetSlot.equals("minecraft:red_carpetx1")
                        && decoration.equals("true")
                        && color.equals("red")
                        && facing.equals("north");
            }
            if (!kind.equals("carpet")) return false;
            Direction output = Direction.byName(direction);
            return output != null
                    && output.getAxis().isHorizontal()
                    && dispenserSlot.equals("minecraft:purple_carpetx1")
                    && pedestalItemSlot.equals("empty")
                    && pedestalCarpetSlot.equals("minecraft:purple_carpetx1")
                    && decoration.equals("true")
                    && color.equals("purple")
                    && facing.equals(output.getOpposite().getName());
        }

        private String description() {
            return kind + ":" + direction + "=" + dispenserSlot + "->"
                    + pedestalItemSlot + "," + pedestalCarpetSlot
                    + ";decoration=" + decoration + ";color=" + color
                    + ";facing=" + facing;
        }

        private JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("kind", kind);
            value.addProperty("direction", direction);
            value.addProperty("dispenser_slot", dispenserSlot);
            value.addProperty("pedestal_item_slot", pedestalItemSlot);
            value.addProperty("pedestal_carpet_slot", pedestalCarpetSlot);
            value.addProperty("decoration", decoration);
            value.addProperty("color", color);
            value.addProperty("facing", facing);
            return value;
        }
    }

    private record DispenserProbe(
            List<DispenserObservation> observations,
            List<String> guardedDirections,
            String limitation,
            List<String> ejectedItems,
            int arrowProjectiles
    ) {

        private static final String OCCUPIED_CARPET_EXPECTED =
                "occupied-carpet-display:up=minecraft:purple_carpetx1->"
                        + "minecraft:purple_carpetx1,minecraft:red_carpetx1;"
                        + "decoration=true;color=red;facing=north";
        private static final String FULL_TARGET_FALLBACK_EXPECTED =
                "full-target-fallback:west=minecraft:arrowx1->"
                        + "minecraft:diamondx1,minecraft:red_carpetx1;"
                        + "decoration=true;color=red;"
                        + "facing=north;ejected=[minecraft:arrowx1];"
                        + "arrow_projectiles=0";

        private static DispenserProbe missing() {
            return new DispenserProbe(List.of(), List.of(), "missing", List.of(), -1);
        }

        private List<String> itemDirections() {
            return observations.stream()
                    .filter(value -> value.kind().equals("item"))
                    .map(DispenserObservation::direction)
                    .toList();
        }

        private List<String> carpetDirections() {
            return observations.stream()
                    .filter(value -> value.kind().equals("carpet"))
                    .map(DispenserObservation::direction)
                    .toList();
        }

        private boolean itemDirectionsExact() {
            return itemDirections().equals(
                    PedestalBaselineContract.ITEM_DISPENSER_DIRECTIONS)
                    && observations.stream()
                    .filter(value -> value.kind().equals("item"))
                    .allMatch(DispenserObservation::exact);
        }

        private boolean carpetDirectionsExact() {
            return carpetDirections().equals(
                    PedestalBaselineContract.CARPET_DISPENSER_DIRECTIONS)
                    && observations.stream()
                    .filter(value -> value.kind().equals("carpet"))
                    .allMatch(DispenserObservation::exact);
        }

        private boolean guardExact() {
            return guardedDirections.equals(
                    PedestalBaselineContract.GUARDED_CARPET_DISPENSER_DIRECTIONS)
                    && limitation.equals(PedestalBaselineContract.VERTICAL_CARPET_LIMITATION)
                    && observations.stream().noneMatch(value ->
                    value.kind().equals("carpet")
                            && (value.direction().equals("up")
                            || value.direction().equals("down")));
        }

        private DispenserObservation observation(String kind) {
            List<DispenserObservation> matches = observations.stream()
                    .filter(value -> value.kind().equals(kind))
                    .toList();
            return matches.size() == 1 ? matches.getFirst() : null;
        }

        private boolean occupiedCarpetExact() {
            DispenserObservation observation = observation("occupied-carpet-display");
            return observation != null && observation.exact();
        }

        private String occupiedCarpetDescription() {
            DispenserObservation observation = observation("occupied-carpet-display");
            return observation == null ? "missing" : observation.description();
        }

        private boolean fullTargetFallbackExact() {
            DispenserObservation observation = observation("full-target-fallback");
            return observation != null
                    && observation.exact()
                    && ejectedItems.equals(List.of("minecraft:arrowx1"))
                    && arrowProjectiles == 0;
        }

        private String fullTargetFallbackDescription() {
            DispenserObservation observation = observation("full-target-fallback");
            return (observation == null ? "missing" : observation.description())
                    + ";ejected=" + ejectedItems
                    + ";arrow_projectiles=" + arrowProjectiles;
        }

        private boolean exact() {
            return observations.size() == 12
                    && itemDirectionsExact()
                    && carpetDirectionsExact()
                    && guardExact()
                    && occupiedCarpetExact()
                    && fullTargetFallbackExact();
        }

        private String description() {
            return observations.stream()
                    .map(DispenserObservation::description)
                    .collect(Collectors.joining("|"))
                    + ";guard=" + guardedDirections + ";" + limitation
                    + ";ejected=" + ejectedItems
                    + ";arrow_projectiles=" + arrowProjectiles;
        }

        private JsonObject toJson() {
            JsonObject result = new JsonObject();
            JsonArray values = new JsonArray();
            observations.forEach(value -> values.add(value.toJson()));
            result.add("observations", values);
            result.add("guarded_carpet_directions", stringArray(guardedDirections));
            result.addProperty("vertical_carpet_status", limitation);
            result.add("ejected_items", stringArray(ejectedItems));
            result.addProperty("arrow_projectiles", arrowProjectiles);
            return result;
        }
    }

    private record DropSnapshot(Map<String, Integer> counts) {

        private static DropSnapshot capture(World world, Box box) {
            List<ItemStack> stacks = world.getEntitiesByClass(
                    ItemEntity.class,
                    box,
                    entity -> !entity.isRemoved()
            ).stream().map(ItemEntity::getStack).toList();
            return new DropSnapshot(stackCounts(stacks));
        }

        private static DropSnapshot empty() {
            return new DropSnapshot(Map.of());
        }

        private DropSnapshot minus(DropSnapshot other) {
            Map<String, Integer> difference = new TreeMap<>(counts);
            other.counts.forEach((id, count) -> difference.merge(id, -count, Integer::sum));
            difference.entrySet().removeIf(entry -> entry.getValue() == 0);
            return new DropSnapshot(Collections.unmodifiableMap(difference));
        }

        private String description() {
            if (counts.isEmpty()) return "none";
            return counts.entrySet().stream()
                    .map(entry -> entry.getKey() + "x" + entry.getValue())
                    .collect(Collectors.joining("+"));
        }

        private JsonObject toJson() {
            JsonObject result = new JsonObject();
            counts.forEach(result::addProperty);
            return result;
        }
    }

    private record TransitionProbe(
            String lowerState,
            String upperState,
            boolean stackWorldBlockEntityAbsent,
            boolean topBlockEntityPresent,
            boolean stackOldReferenceRemoved,
            boolean replacementAir,
            boolean replacementWorldBlockEntityAbsent,
            boolean replacementOldReferenceRemoved,
            DropSnapshot stackDrops,
            DropSnapshot replacementDrops,
            DropSnapshot combinedDrops
    ) {

        private static TransitionProbe missing() {
            return new TransitionProbe(
                    "missing", "missing", false, false, false,
                    false, false, false,
                    DropSnapshot.empty(), DropSnapshot.empty(), DropSnapshot.empty()
            );
        }

        private boolean stackDropsExact() {
            return stackDrops.counts().equals(Map.of(
                    "minecraft:diamond", 1,
                    "minecraft:red_carpet", 1
            ));
        }

        private boolean replacementDropsExact() {
            return replacementDrops.counts().equals(Map.of(
                    "minecraft:emerald", 1,
                    "minecraft:blue_carpet", 1
            ));
        }

        private boolean exact() {
            return lowerState.contains("shape=bottom")
                    && upperState.contains("shape=top")
                    && stackWorldBlockEntityAbsent
                    && topBlockEntityPresent
                    && stackOldReferenceRemoved
                    && replacementAir
                    && replacementWorldBlockEntityAbsent
                    && replacementOldReferenceRemoved
                    && stackDropsExact()
                    && replacementDropsExact()
                    && combinedDrops.counts().equals(Map.of(
                    "minecraft:diamond", 1,
                    "minecraft:red_carpet", 1,
                    "minecraft:emerald", 1,
                    "minecraft:blue_carpet", 1
            ));
        }

        private JsonObject toJson() {
            JsonObject result = new JsonObject();
            result.addProperty("lower_state", lowerState);
            result.addProperty("upper_state", upperState);
            result.addProperty("stack_world_block_entity_absent",
                    stackWorldBlockEntityAbsent);
            result.addProperty("top_block_entity_present", topBlockEntityPresent);
            result.addProperty("stack_old_reference_removed", stackOldReferenceRemoved);
            result.addProperty("replacement_air", replacementAir);
            result.addProperty("replacement_world_block_entity_absent",
                    replacementWorldBlockEntityAbsent);
            result.addProperty("replacement_old_reference_removed",
                    replacementOldReferenceRemoved);
            result.add("stack_drops", stackDrops.toJson());
            result.add("replacement_drops", replacementDrops.toJson());
            result.add("combined_drops", combinedDrops.toJson());
            return result;
        }
    }

    private record PedestalFixture(
            BlockPos position,
            String shape,
            boolean decoration,
            String color,
            String facing,
            boolean waterlogged,
            String item,
            String carpet,
            boolean blockEntityPresent
    ) {
    }

    private record PedestalObservation(
            String position,
            String blockId,
            String state,
            String shape,
            boolean decoration,
            String color,
            String facing,
            boolean waterlogged,
            String blockEntityType,
            boolean blockEntityRemoved,
            String item,
            String carpet
    ) {

        private static PedestalObservation capture(World world, PedestalFixture fixture) {
            BlockState state = world.getBlockState(fixture.position());
            BlockEntity blockEntity = world.getBlockEntity(fixture.position());
            Inventory inventory = blockEntity instanceof Inventory value ? value : null;
            return new PedestalObservation(
                    fixture.position().toShortString(),
                    Registries.BLOCK.getId(state.getBlock()).toString(),
                    stateDescription(state),
                    propertyValue(state, "shape"),
                    Boolean.parseBoolean(propertyValue(state, "decoration")),
                    propertyValue(state, "cloth_color"),
                    propertyValue(state, "facing"),
                    Boolean.parseBoolean(propertyValue(state, "waterlogged")),
                    blockEntity == null
                            ? "absent"
                            : Registries.BLOCK_ENTITY_TYPE.getId(
                            blockEntity.getType()).toString(),
                    blockEntity != null && blockEntity.isRemoved(),
                    inventory == null ? "empty" : stackDescription(inventory.getStack(0)),
                    inventory == null ? "empty" : stackDescription(inventory.getStack(1))
            );
        }

        private boolean exact(PedestalFixture fixture) {
            String expectedType = fixture.blockEntityPresent()
                    ? PEDESTAL_BLOCK_ENTITY_ID.toString()
                    : "absent";
            return position.equals(fixture.position().toShortString())
                    && blockId.equals(PEDESTAL_ID.toString())
                    && state.contains("shape=" + fixture.shape())
                    && shape.equals(fixture.shape())
                    && decoration == fixture.decoration()
                    && color.equals(fixture.color())
                    && facing.equals(fixture.facing())
                    && waterlogged == fixture.waterlogged()
                    && blockEntityType.equals(expectedType)
                    && !blockEntityRemoved
                    && item.equals(fixture.item())
                    && carpet.equals(fixture.carpet());
        }

        private String description() {
            return position + "=" + state + ";be=" + blockEntityType
                    + ";removed=" + blockEntityRemoved + ";slots=" + item
                    + "," + carpet;
        }

        private JsonObject toJson() {
            JsonObject value = new JsonObject();
            value.addProperty("position", position);
            value.addProperty("block_id", blockId);
            value.addProperty("state", state);
            value.addProperty("shape", shape);
            value.addProperty("decoration", decoration);
            value.addProperty("color", color);
            value.addProperty("facing", facing);
            value.addProperty("waterlogged", waterlogged);
            value.addProperty("block_entity_type", blockEntityType);
            value.addProperty("block_entity_removed", blockEntityRemoved);
            value.addProperty("item", item);
            value.addProperty("carpet", carpet);
            return value;
        }
    }

    private record SceneSnapshot(List<PedestalObservation> observations) {

        private static SceneSnapshot capture(
                World world,
                List<PedestalFixture> fixtures
        ) {
            return new SceneSnapshot(fixtures.stream()
                    .map(fixture -> PedestalObservation.capture(world, fixture))
                    .toList());
        }

        private static SceneSnapshot missing() {
            return new SceneSnapshot(List.of());
        }

        private boolean exact(List<PedestalFixture> fixtures) {
            if (observations.size() != fixtures.size()) return false;
            for (int index = 0; index < fixtures.size(); index++) {
                if (!observations.get(index).exact(fixtures.get(index))) return false;
            }
            return true;
        }

        private String description() {
            return observations.stream()
                    .map(PedestalObservation::description)
                    .collect(Collectors.joining("|"));
        }

        private JsonArray toJson() {
            JsonArray result = new JsonArray();
            observations.forEach(value -> result.add(value.toJson()));
            return result;
        }
    }

    private record InitialSetupResult(
            ResourceProbe resources,
            DataProbe dataProbe,
            PlacementProbe placementProbe,
            ShapeProbe shapeProbe,
            InteractionProbe interactionProbe,
            InventoryProbe inventoryProbe,
            long dispenserReadyTick
    ) {

        private boolean exact() {
            return resources.exact()
                    && dataProbe.exact()
                    && placementProbe.exact()
                    && shapeProbe.exact()
                    && interactionProbe.exact()
                    && inventoryProbe.exact()
                    && dispenserReadyTick > 0L;
        }
    }

    private record SceneSetupResult(
            CapturePhase phase,
            SceneSnapshot snapshot,
            DropSnapshot drops,
            TransitionProbe transitionProbe
    ) {
    }

    private record SaveResult(
            boolean completed,
            boolean saved,
            SceneSnapshot snapshot
    ) {

        private static SaveResult missing() {
            return new SaveResult(false, false, SceneSnapshot.missing());
        }
    }

    private record ReopenedResult(
            SceneSnapshot snapshot,
            DataProbe dataProbe,
            boolean worldIdentityExact
    ) {
    }

    private record ScreenshotResult(
            boolean written,
            long size,
            String sha256,
            String failure
    ) {

        private static ScreenshotResult missing() {
            return new ScreenshotResult(false, 0L, "", "not captured");
        }

        private static ScreenshotResult failed(String failure) {
            return new ScreenshotResult(false, 0L, "", failure);
        }
    }

    private record CaptureEvidence(
            boolean clientSnapshotMatchesServer,
            boolean renderReady,
            boolean cameraExact,
            int completedRenders,
            int width,
            int height,
            String cameraDescription,
            SceneSnapshot serverSnapshot,
            ScreenshotResult screenshot
    ) {

        private static CaptureEvidence missing() {
            return new CaptureEvidence(
                    false, false, false, 0, 0, 0,
                    "missing", SceneSnapshot.missing(), ScreenshotResult.missing()
            );
        }

        private CaptureEvidence withScreenshot(ScreenshotResult result) {
            return new CaptureEvidence(
                    clientSnapshotMatchesServer,
                    renderReady,
                    cameraExact,
                    completedRenders,
                    width,
                    height,
                    cameraDescription,
                    serverSnapshot,
                    result
            );
        }
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
}
