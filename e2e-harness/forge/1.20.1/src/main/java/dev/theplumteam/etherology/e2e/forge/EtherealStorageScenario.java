package dev.theplumteam.etherology.e2e.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DatapackFailureScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class EtherealStorageScenario {

    private static final Logger LOGGER = LoggerFactory.getLogger("EtherologyForgeE2E");
    private static final String SCENARIO_ID = ScenarioSelection.ETHEREAL_STORAGE;
    private static final String WORLD_DIRECTORY_NAME = "etherology-e2e-forge-storage-world";
    private static final String WORLD_DISPLAY_NAME = "Etherology Forge E2E Storage";
    private static final String CLOSED_SCREENSHOT = "ethereal-storage-closed.png";
    private static final String OPEN_SCREENSHOT = "ethereal-storage-open.png";
    private static final String CLOSED_AGAIN_SCREENSHOT = "ethereal-storage-closed-again.png";
    private static final String MENU_SCREENSHOT = "ethereal-storage-menu.png";
    private static final String REOPENED_SCREENSHOT = "ethereal-storage-reopened.png";
    private static final String COMPONENTS_KEY = "etherology:components";
    private static final String STORED_ETHER_KEY = "stored_ether";
    private static final String STORAGE_ETHER_KEY = "storage_ether";
    private static final long WORLD_SEED = 0x45544846524745L;
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int ARENA_FLOOR_Y = 120;
    private static final int REQUIRED_INITIAL_CLOSED_RENDERS = 16;
    private static final int REQUIRED_OPEN_RENDERS = 48;
    private static final int REQUIRED_CLOSED_AGAIN_RENDERS = 60;
    private static final int REQUIRED_MENU_RENDERS = 2;
    private static final int ITEM_HANDLER_SLOT_COUNT = 3;
    private static final int DISPLAY_SLOT = ITEM_HANDLER_SLOT_COUNT;
    private static final int INVENTORY_SIZE = DISPLAY_SLOT + 1;
    private static final int TRANSFER_WAIT_TICKS = 30;
    private static final int QUIESCENCE_POLL_CLIENT_TICKS = 20;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final float INITIAL_INTERNAL_ETHER = 64.0f;
    private static final float INITIAL_GLINT_ETHER = 64.0f;
    private static final float EXPECTED_TOTAL_ETHER =
            INITIAL_INTERNAL_ETHER + INITIAL_GLINT_ETHER;
    private static final BlockPos STORAGE_POS = new BlockPos(0, ARENA_FLOOR_Y + 1, 2);
    private static final BlockPos CAMERA_POS = new BlockPos(0, ARENA_FLOOR_Y + 1, -5);
    private static final Identifier STORAGE_BLOCK_ID = etherologyId("ethereal_storage");
    private static final Identifier STORAGE_BLOCK_ENTITY_ID =
            etherologyId("ethereal_storage_block_entity");
    private static final Identifier STORAGE_SCREEN_ID =
            etherologyId("ethereal_storage_screen_handler");
    private static final Identifier GLINT_ID = etherologyId("glint_shard");
    private static final Identifier ETHER_ID = etherologyId("ether");

    private Stage stage = Stage.WAITING_FOR_TITLE;
    private int clientTicks;
    private int stageClientTicks;
    private int completedRenders;
    private String lifecycleFailure = "";
    private ForgeEvidenceLayout evidenceLayout;
    private boolean setupSubmitted;
    private boolean transferInspectionSubmitted;
    private boolean openLifecycleSubmitted;
    private boolean closeLifecycleSubmitted;
    private boolean menuOpenSubmitted;
    private boolean quiescenceInspectionSubmitted;
    private boolean saveSubmitted;
    private boolean restartSubmitted;
    private boolean restartInspectionSubmitted;
    private boolean reopenedMenuSubmitted;
    private volatile String serverFailure = "";
    private volatile SetupResult setupResult;
    private volatile StorageSnapshot transferSnapshot;
    private volatile Boolean openLifecycleResult;
    private volatile Boolean closeLifecycleResult;
    private volatile Boolean menuOpenResult;
    private boolean menuClientTypeMatched;
    private volatile StorageSnapshot quiescenceSnapshot;
    private volatile Boolean saveResult;
    private volatile StorageSnapshot savedSnapshot;
    private volatile RestartResult restartResult;
    private volatile Boolean reopenedMenuResult;
    private boolean reopenedMenuClientTypeMatched;
    private volatile ScreenshotResult closedScreenshot;
    private volatile ScreenshotResult openScreenshot;
    private volatile ScreenshotResult closedAgainScreenshot;
    private volatile ScreenshotResult menuScreenshot;
    private volatile ScreenshotResult reopenedScreenshot;

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
            LOGGER.error("Forge ethereal-storage E2E failed in {}", stage, exception);
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

    @SubscribeEvent
    public void onWorldRendered(RenderGuiEvent.Post event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || !isWorldReady(client)) {
            return;
        }

        switch (stage) {
            case WAITING_FOR_CLOSED_RENDERS -> captureAfterStableRenders(
                    client,
                    CLOSED_SCREENSHOT,
                    REQUIRED_INITIAL_CLOSED_RENDERS,
                    result -> closedScreenshot = result,
                    Stage.CAPTURING_CLOSED
            );
            case WAITING_FOR_OPEN_RENDERS -> captureAfterStableRenders(
                    client,
                    OPEN_SCREENSHOT,
                    REQUIRED_OPEN_RENDERS,
                    result -> openScreenshot = result,
                    Stage.CAPTURING_OPEN
            );
            case WAITING_FOR_CLOSE_RENDERS -> captureAfterStableRenders(
                    client,
                    CLOSED_AGAIN_SCREENSHOT,
                    REQUIRED_CLOSED_AGAIN_RENDERS,
                    result -> closedAgainScreenshot = result,
                    Stage.CAPTURING_CLOSED_AGAIN
            );
            default -> {
            }
        }
    }

    @SubscribeEvent
    public void onScreenRendered(ScreenEvent.Render.Post event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != event.getScreen()
                || !(event.getScreen() instanceof HandledScreen<?>)) {
            return;
        }

        if (stage == Stage.WAITING_FOR_MENU_RENDERS) {
            captureMenuAfterStableRenders(
                    client,
                    MENU_SCREENSHOT,
                    result -> menuScreenshot = result,
                    Stage.CAPTURING_MENU
            );
        } else if (stage == Stage.WAITING_FOR_REOPENED_RENDERS) {
            captureMenuAfterStableRenders(
                    client,
                    REOPENED_SCREENSHOT,
                    result -> reopenedScreenshot = result,
                    Stage.CAPTURING_REOPENED
            );
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
            case WAITING_FOR_TRANSFER -> tickWaitingForTransfer(client);
            case WAITING_FOR_CLOSED_RENDERS,
                    WAITING_FOR_OPEN_RENDERS,
                    WAITING_FOR_CLOSE_RENDERS,
                    WAITING_FOR_MENU_RENDERS,
                    WAITING_FOR_REOPENED_RENDERS -> requireWorldLifecycle(client);
            case CAPTURING_CLOSED -> tickCapturingClosed(client);
            case OPENING_LIFECYCLE -> tickOpeningLifecycle(client);
            case CAPTURING_OPEN -> tickCapturingOpen(client);
            case CLOSING_LIFECYCLE -> tickClosingLifecycle(client);
            case CAPTURING_CLOSED_AGAIN -> tickCapturingClosedAgain(client);
            case OPENING_MENU -> tickOpeningMenu(client);
            case CAPTURING_MENU -> tickCapturingMenu(client);
            case WAITING_FOR_QUIESCENT_STORAGE -> tickWaitingForQuiescentStorage(client);
            case SAVING -> tickSaving(client);
            case DISCONNECTING -> tickDisconnecting(client);
            case WAITING_FOR_RESTART_TITLE -> tickWaitingForRestartTitle(client);
            case RESTARTING_WORLD -> restartWorld(client);
            case WAITING_FOR_RESTART_WORLD -> tickWaitingForRestartWorld(client);
            case WAITING_FOR_RESTART_INSPECTION -> tickWaitingForRestartInspection(client);
            case REOPENING_MENU -> tickReopeningMenu(client);
            case CAPTURING_REOPENED -> tickCapturingReopened(client);
            case COMPLETE -> {
            }
        }
    }

    private void tickWaitingForTitle(MinecraftClient client) {
        if (client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) {
            return;
        }
        if (!hasExpectedFramebuffer(client)) {
            return;
        }
        validateRegistryAndMods();
        try {
            evidenceLayout = ForgeEvidenceLayout.resolve(client.runDirectory.toPath(), SCENARIO_ID);
            evidenceLayout.requireFreshTargets(
                    CLOSED_SCREENSHOT,
                    OPEN_SCREENSHOT,
                    CLOSED_AGAIN_SCREENSHOT,
                    MENU_SCREENSHOT,
                    REOPENED_SCREENSHOT
            );
        } catch (IOException exception) {
            fail(client, "Cannot initialize Forge evidence: " + exception.getMessage());
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
        requireRegistryId(Registries.BLOCK.getIds(), STORAGE_BLOCK_ID, "block");
        requireRegistryId(Registries.BLOCK_ENTITY_TYPE.getIds(), STORAGE_BLOCK_ENTITY_ID, "block entity");
        requireRegistryId(Registries.SCREEN_HANDLER.getIds(), STORAGE_SCREEN_ID, "screen handler");
        requireRegistryId(Registries.ITEM.getIds(), GLINT_ID, "glint item");
        requireRegistryId(Registries.ITEM.getIds(), ETHER_ID, "Ether item");
    }

    private void startFreshWorld(MinecraftClient client) {
        Path saveDirectory = client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
        if (Files.exists(saveDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(saveDirectory)) {
            fail(client, "Refusing to reuse the Forge storage save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "A world is already active before Forge E2E creation");
            return;
        }

        GameRules gameRules = controlledGameRules();
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
        server.execute(() -> setupStorageFixture(server, playerId));
        transition(Stage.WAITING_FOR_SETUP);
    }

    private void tickWaitingForSetup(MinecraftClient client) {
        if (setupResult == null) {
            return;
        }
        if (!hasClientStorageMirror(client)) {
            return;
        }
        transition(Stage.WAITING_FOR_TRANSFER);
    }

    private void tickWaitingForTransfer(MinecraftClient client) {
        requireWorldLifecycle(client);
        if (stageClientTicks < TRANSFER_WAIT_TICKS) {
            return;
        }

        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        if (!transferInspectionSubmitted) {
            transferInspectionSubmitted = true;
            server.execute(() -> {
                try {
                    transferSnapshot = captureStorageSnapshot(server.getOverworld());
                } catch (RuntimeException exception) {
                    recordServerFailure(exception);
                }
            });
        }
        if (transferSnapshot == null) {
            return;
        }
        transition(Stage.WAITING_FOR_CLOSED_RENDERS);
    }

    private void tickCapturingClosed(MinecraftClient client) {
        if (!requireSuccessfulScreenshot(client, closedScreenshot, "closed storage")) {
            return;
        }
        transition(Stage.OPENING_LIFECYCLE);
    }

    private void tickOpeningLifecycle(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        if (!openLifecycleSubmitted) {
            openLifecycleSubmitted = true;
            UUID playerId = client.player.getUuid();
            server.execute(() -> setViewerLifecycle(server, playerId, true));
        }
        if (openLifecycleResult == null) {
            return;
        }
        transition(Stage.WAITING_FOR_OPEN_RENDERS);
    }

    private void tickCapturingOpen(MinecraftClient client) {
        if (!requireSuccessfulScreenshot(client, openScreenshot, "open storage")) {
            return;
        }
        transition(Stage.CLOSING_LIFECYCLE);
    }

    private void tickClosingLifecycle(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        if (!closeLifecycleSubmitted) {
            closeLifecycleSubmitted = true;
            UUID playerId = client.player.getUuid();
            server.execute(() -> setViewerLifecycle(server, playerId, false));
        }
        if (closeLifecycleResult == null) {
            return;
        }
        transition(Stage.WAITING_FOR_CLOSE_RENDERS);
    }

    private void tickCapturingClosedAgain(MinecraftClient client) {
        if (!requireSuccessfulScreenshot(client, closedAgainScreenshot, "closed-again storage")) {
            return;
        }
        transition(Stage.OPENING_MENU);
    }

    private void tickOpeningMenu(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        if (!menuOpenSubmitted) {
            menuOpenSubmitted = true;
            UUID playerId = client.player.getUuid();
            server.execute(() -> openStorageMenu(server, playerId, false));
        }
        if (menuOpenResult == null || !(client.currentScreen instanceof HandledScreen<?>)) {
            return;
        }
        menuClientTypeMatched = hasExpectedScreenHandler(client);
        transition(Stage.WAITING_FOR_MENU_RENDERS);
    }

    private void tickCapturingMenu(MinecraftClient client) {
        if (!requireSuccessfulScreenshot(client, menuScreenshot, "storage menu")) {
            return;
        }
        if (client.player == null) {
            fail(client, "The client player vanished before closing the storage menu");
            return;
        }
        client.player.closeHandledScreen();
        transition(Stage.WAITING_FOR_QUIESCENT_STORAGE);
    }

    private void tickWaitingForQuiescentStorage(MinecraftClient client) {
        requireWorldLifecycle(client);
        StorageSnapshot inspected = quiescenceSnapshot;
        if (inspected != null) {
            if (hasQuiescentPersistenceState(inspected)) {
                transition(Stage.SAVING);
                return;
            }
            quiescenceSnapshot = null;
            quiescenceInspectionSubmitted = false;
        }
        if (quiescenceInspectionSubmitted
                || (stageClientTicks != 1
                    && stageClientTicks % QUIESCENCE_POLL_CLIENT_TICKS != 0)) {
            return;
        }

        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        quiescenceInspectionSubmitted = true;
        server.execute(() -> {
            try {
                quiescenceSnapshot = captureStorageSnapshot(server.getOverworld());
            } catch (RuntimeException exception) {
                recordServerFailure(exception);
            }
        });
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
                    savedSnapshot = captureStorageSnapshot(server.getOverworld());
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
            fail(client, "The integrated server rejected the forced save");
            return;
        }
        transition(Stage.DISCONNECTING);
    }

    private void tickDisconnecting(MinecraftClient client) {
        if (client.world == null) {
            fail(client, "The client world vanished before the restart disconnect");
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
            fail(client, "Minecraft rejected the saved Forge data packs on restart");
            return;
        }
        if (!isWorldReady(client) || !hasClientStorageMirror(client)) {
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
                    StorageSnapshot reloaded = captureStorageSnapshot(server.getOverworld());
                    StorageSnapshot saved = savedSnapshot;
                    restartResult = new RestartResult(
                            reloaded,
                            saved != null && samePersistedEtherState(saved, reloaded),
                            saved != null && saved.inputs().equals(reloaded.inputs()),
                            saved != null && saved.blockEntityId().equals(reloaded.blockEntityId())
                    );
                } catch (RuntimeException exception) {
                    recordServerFailure(exception);
                }
            });
        }
        if (restartResult == null) {
            return;
        }
        transition(Stage.REOPENING_MENU);
    }

    private void tickReopeningMenu(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return;
        }
        if (!reopenedMenuSubmitted) {
            reopenedMenuSubmitted = true;
            UUID playerId = client.player.getUuid();
            server.execute(() -> openStorageMenu(server, playerId, true));
        }
        if (reopenedMenuResult == null
                || !(client.currentScreen instanceof HandledScreen<?>)) {
            return;
        }
        reopenedMenuClientTypeMatched = hasExpectedScreenHandler(client);
        transition(Stage.WAITING_FOR_REOPENED_RENDERS);
    }

    private void tickCapturingReopened(MinecraftClient client) {
        if (!requireSuccessfulScreenshot(client, reopenedScreenshot, "reopened storage menu")) {
            return;
        }
        publish(client);
    }

    private void setupStorageFixture(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = requireServerPlayer(server, playerId);
            boolean chunkLoaded = world.getChunkManager().getChunk(0, 0, ChunkStatus.FULL, true) != null;
            configureWorld(server, world);
            buildArena(world);

            Block storageBlock = Registries.BLOCK.get(STORAGE_BLOCK_ID);
            if (storageBlock == Blocks.AIR) {
                throw new IllegalStateException("The storage block resolved to air");
            }
            world.setBlockState(STORAGE_POS, storageBlock.getDefaultState(), 3);
            BlockEntity blockEntity = requireStorageBlockEntity(world);
            if (!(blockEntity instanceof Inventory inventory)) {
                throw new IllegalStateException("The storage block entity is not an Inventory");
            }

            NbtCompound initialNbt = blockEntity.createNbt();
            initialNbt.putFloat(STORAGE_ETHER_KEY, INITIAL_INTERNAL_ETHER);
            blockEntity.readNbt(initialNbt);

            ItemStack chargedGlint = new ItemStack(Registries.ITEM.get(GLINT_ID));
            setGlintEther(chargedGlint, INITIAL_GLINT_ETHER);
            CapabilityResult capabilityResult = inspectAndUseCapability(
                    blockEntity,
                    inventory,
                    chargedGlint
            );
            inventory.markDirty();

            NbtCompound roundTripNbt = blockEntity.createNbtWithId();
            BlockEntity reconstructed = BlockEntity.createFromNbt(
                    STORAGE_POS,
                    world.getBlockState(STORAGE_POS),
                    roundTripNbt
            );
            boolean nbtReconstructed = reconstructed != null
                    && reconstructed.getType() == blockEntity.getType();

            player.changeGameMode(GameMode.CREATIVE);
            player.teleport(
                    world,
                    CAMERA_POS.getX() + 0.5,
                    CAMERA_POS.getY(),
                    CAMERA_POS.getZ() + 0.5,
                    0.0f,
                    8.0f
            );
            player.setSpawnPoint(World.OVERWORLD, CAMERA_POS, 0.0f, true, false);

            setupResult = new SetupResult(
                    chunkLoaded,
                    nbtReconstructed,
                    roundTripNbt.getString("id"),
                    inventory.size(),
                    capabilityResult
            );
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    private CapabilityResult inspectAndUseCapability(
            BlockEntity blockEntity,
            Inventory inventory,
            ItemStack chargedGlint
    ) {
        List<String> threeSlotFailureViews = new ArrayList<>();
        List<String> glintValidityFailureViews = new ArrayList<>();
        List<String> simulatedInsertionFailureViews = new ArrayList<>();
        List<String> liveInsertionFailureViews = new ArrayList<>();
        List<String> extractionFailureViews = new ArrayList<>();
        List<String> displaySlotFailureViews = new ArrayList<>();

        inspectCapabilityView(
                blockEntity,
                inventory,
                chargedGlint,
                null,
                threeSlotFailureViews,
                glintValidityFailureViews,
                simulatedInsertionFailureViews,
                liveInsertionFailureViews,
                extractionFailureViews,
                displaySlotFailureViews
        );
        for (Direction direction : Direction.values()) {
            inspectCapabilityView(
                    blockEntity,
                    inventory,
                    chargedGlint,
                    direction,
                    threeSlotFailureViews,
                    glintValidityFailureViews,
                    simulatedInsertionFailureViews,
                    liveInsertionFailureViews,
                    extractionFailureViews,
                    displaySlotFailureViews
            );
        }
        seedSingleChargedGlint(inventory, chargedGlint);

        return new CapabilityResult(
                List.copyOf(threeSlotFailureViews),
                List.copyOf(glintValidityFailureViews),
                List.copyOf(simulatedInsertionFailureViews),
                List.copyOf(liveInsertionFailureViews),
                List.copyOf(extractionFailureViews),
                List.copyOf(displaySlotFailureViews)
        );
    }

    private void inspectCapabilityView(
            BlockEntity blockEntity,
            Inventory inventory,
            ItemStack chargedGlint,
            Direction direction,
            List<String> threeSlotFailureViews,
            List<String> glintValidityFailureViews,
            List<String> simulatedInsertionFailureViews,
            List<String> liveInsertionFailureViews,
            List<String> extractionFailureViews,
            List<String> displaySlotFailureViews
    ) {
        String viewName = capabilityViewName(direction);
        CapabilityViewResult result = probeCapabilityView(
                blockEntity,
                inventory,
                chargedGlint,
                direction,
                viewName
        );
        recordFailedView(threeSlotFailureViews, viewName, result.threeSlots());
        recordFailedView(glintValidityFailureViews, viewName, result.validGlint());
        recordFailedView(
                simulatedInsertionFailureViews,
                viewName,
                result.simulatedInsertionWithoutMutation()
        );
        recordFailedView(liveInsertionFailureViews, viewName, result.liveInsertion());
        recordFailedView(extractionFailureViews, viewName, result.extractionBlocked());
        recordFailedView(displaySlotFailureViews, viewName, result.displaySlotHidden());
    }

    private CapabilityViewResult probeCapabilityView(
            BlockEntity blockEntity,
            Inventory inventory,
            ItemStack chargedGlint,
            Direction direction,
            String viewName
    ) {
        resetCapabilityProbeInventory(inventory);
        Optional<IItemHandler> resolvedHandler = resolveHandler(blockEntity, direction);
        if (resolvedHandler.isEmpty()) {
            return CapabilityViewResult.failed();
        }

        IItemHandler handler = resolvedHandler.orElseThrow();
        boolean threeSlots = probeThreeSlots(handler, viewName);
        boolean validGlint = probeGlintValidity(handler, chargedGlint, viewName);
        boolean displaySlotHidden = probeDisplaySlotHidden(handler, inventory, viewName);
        boolean simulatedInsertion = probeSimulatedInsertion(
                handler,
                inventory,
                chargedGlint,
                viewName
        );

        resetCapabilityProbeInventory(inventory);
        boolean liveInsertion = probeLiveInsertion(
                handler,
                inventory,
                chargedGlint,
                viewName
        );

        seedSingleChargedGlint(inventory, chargedGlint);
        boolean extractionBlocked = probeBlockedExtraction(
                handler,
                inventory,
                chargedGlint,
                viewName
        );
        return new CapabilityViewResult(
                threeSlots,
                validGlint,
                simulatedInsertion,
                liveInsertion,
                extractionBlocked,
                displaySlotHidden
        );
    }

    private boolean probeThreeSlots(IItemHandler handler, String viewName) {
        try {
            return handler.getSlots() == ITEM_HANDLER_SLOT_COUNT;
        } catch (RuntimeException exception) {
            logCapabilityProbeFailure(viewName, "slot count", exception);
            return false;
        }
    }

    private boolean probeGlintValidity(
            IItemHandler handler,
            ItemStack chargedGlint,
            String viewName
    ) {
        try {
            if (handler.getSlots() != ITEM_HANDLER_SLOT_COUNT) {
                return false;
            }
            for (int slot = 0; slot < ITEM_HANDLER_SLOT_COUNT; slot++) {
                if (!handler.isItemValid(slot, chargedGlint)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            logCapabilityProbeFailure(viewName, "Glint validity", exception);
            return false;
        }
    }

    private boolean probeDisplaySlotHidden(
            IItemHandler handler,
            Inventory inventory,
            String viewName
    ) {
        try {
            if (inventory.size() != INVENTORY_SIZE
                    || handler.getSlots() != ITEM_HANDLER_SLOT_COUNT) {
                return false;
            }
            ItemStack displayStack = inventory.getStack(DISPLAY_SLOT);
            if (displayStack.isEmpty() || !displayStack.isOf(Registries.ITEM.get(ETHER_ID))) {
                return false;
            }
            for (int slot = 0; slot < ITEM_HANDLER_SLOT_COUNT; slot++) {
                ItemStack visibleStack = handler.getStackInSlot(slot);
                if (!ItemStack.areEqual(visibleStack, inventory.getStack(slot))
                        || ItemStack.areEqual(visibleStack, displayStack)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            logCapabilityProbeFailure(viewName, "display-slot isolation", exception);
            return false;
        }
    }

    private boolean probeSimulatedInsertion(
            IItemHandler handler,
            Inventory inventory,
            ItemStack chargedGlint,
            String viewName
    ) {
        try {
            List<ItemStack> inventoryBefore = copyInventory(inventory);
            ItemStack offeredStack = chargedGlint.copy();
            ItemStack offeredStackBefore = offeredStack.copy();
            ItemStack remainder = handler.insertItem(0, offeredStack, true);
            return remainder.isEmpty()
                    && ItemStack.areEqual(offeredStackBefore, offeredStack)
                    && inventoryMatches(inventory, inventoryBefore);
        } catch (RuntimeException exception) {
            logCapabilityProbeFailure(viewName, "simulated insertion", exception);
            return false;
        }
    }

    private boolean probeLiveInsertion(
            IItemHandler handler,
            Inventory inventory,
            ItemStack chargedGlint,
            String viewName
    ) {
        try {
            ItemStack displayBefore = inventory.getStack(DISPLAY_SLOT).copy();
            ItemStack remainder = handler.insertItem(0, chargedGlint.copy(), false);
            return remainder.isEmpty()
                    && hasExactlyOneChargedInput(inventory, chargedGlint)
                    && ItemStack.areEqual(handler.getStackInSlot(0), chargedGlint)
                    && ItemStack.areEqual(inventory.getStack(DISPLAY_SLOT), displayBefore);
        } catch (RuntimeException exception) {
            logCapabilityProbeFailure(viewName, "live insertion", exception);
            return false;
        }
    }

    private boolean probeBlockedExtraction(
            IItemHandler handler,
            Inventory inventory,
            ItemStack chargedGlint,
            String viewName
    ) {
        try {
            List<ItemStack> inventoryBefore = copyInventory(inventory);
            ItemStack extracted = handler.extractItem(0, 1, false);
            return extracted.isEmpty()
                    && inventoryMatches(inventory, inventoryBefore)
                    && ItemStack.areEqual(handler.getStackInSlot(0), chargedGlint);
        } catch (RuntimeException exception) {
            logCapabilityProbeFailure(viewName, "blocked extraction", exception);
            return false;
        }
    }

    private static void resetCapabilityProbeInventory(Inventory inventory) {
        inventory.clear();
        if (inventory.size() != INVENTORY_SIZE) {
            throw new IllegalStateException(
                    "Storage inventory size changed during ITEM_HANDLER probing"
            );
        }
        for (int slot = 0; slot < ITEM_HANDLER_SLOT_COUNT; slot++) {
            if (!inventory.getStack(slot).isEmpty()) {
                throw new IllegalStateException(
                        "Storage input " + slot + " survived the authoritative reset"
                );
            }
        }
    }

    private static void seedSingleChargedGlint(
            Inventory inventory,
            ItemStack chargedGlint
    ) {
        resetCapabilityProbeInventory(inventory);
        inventory.setStack(0, chargedGlint.copy());
        if (!hasExactlyOneChargedInput(inventory, chargedGlint)) {
            throw new IllegalStateException(
                    "Storage did not retain exactly one charged Glint after reset"
            );
        }
    }

    private static boolean hasExactlyOneChargedInput(
            Inventory inventory,
            ItemStack chargedGlint
    ) {
        if (!ItemStack.areEqual(inventory.getStack(0), chargedGlint)) {
            return false;
        }
        for (int slot = 1; slot < ITEM_HANDLER_SLOT_COUNT; slot++) {
            if (!inventory.getStack(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> copyInventory(Inventory inventory) {
        List<ItemStack> copy = new ArrayList<>(inventory.size());
        for (int slot = 0; slot < inventory.size(); slot++) {
            copy.add(inventory.getStack(slot).copy());
        }
        return List.copyOf(copy);
    }

    private static boolean inventoryMatches(
            Inventory inventory,
            List<ItemStack> expected
    ) {
        if (inventory.size() != expected.size()) {
            return false;
        }
        for (int slot = 0; slot < expected.size(); slot++) {
            if (!ItemStack.areEqual(inventory.getStack(slot), expected.get(slot))) {
                return false;
            }
        }
        return true;
    }

    private static void recordFailedView(
            List<String> failureViews,
            String viewName,
            boolean passed
    ) {
        if (!passed) {
            failureViews.add(viewName);
        }
    }

    private static String capabilityViewName(Direction direction) {
        return direction == null ? "unsided" : direction.getName();
    }

    private static List<String> capabilityViewNames() {
        List<String> names = new ArrayList<>(Direction.values().length + 1);
        names.add(capabilityViewName(null));
        for (Direction direction : Direction.values()) {
            names.add(capabilityViewName(direction));
        }
        return List.copyOf(names);
    }

    private static void logCapabilityProbeFailure(
            String viewName,
            String operation,
            RuntimeException exception
    ) {
        LOGGER.error(
                "Forge ITEM_HANDLER {} view failed its {} probe",
                viewName,
                operation,
                exception
        );
    }

    private Optional<IItemHandler> resolveHandler(
            BlockEntity blockEntity,
            Direction direction
    ) {
        LazyOptional<IItemHandler> optional = blockEntity.getCapability(
                ForgeCapabilities.ITEM_HANDLER,
                direction
        );
        return optional.resolve();
    }

    private void setViewerLifecycle(IntegratedServer server, UUID playerId, boolean open) {
        try {
            ServerPlayerEntity player = requireServerPlayer(server, playerId);
            BlockEntity blockEntity = requireStorageBlockEntity(server.getOverworld());
            if (!(blockEntity instanceof Inventory inventory)) {
                throw new IllegalStateException("Storage lost its Inventory contract");
            }
            if (open) {
                inventory.onOpen(player);
                openLifecycleResult = true;
            } else {
                inventory.onClose(player);
                closeLifecycleResult = true;
            }
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    private void openStorageMenu(IntegratedServer server, UUID playerId, boolean reopened) {
        try {
            ServerPlayerEntity player = requireServerPlayer(server, playerId);
            BlockEntity blockEntity = requireStorageBlockEntity(server.getOverworld());
            if (!(blockEntity instanceof NamedScreenHandlerFactory factory)) {
                throw new IllegalStateException("Storage has no menu factory after load");
            }
            boolean opened = player.openHandledScreen(factory).isPresent();
            if (reopened) {
                reopenedMenuResult = opened;
            } else {
                menuOpenResult = opened;
            }
        } catch (RuntimeException exception) {
            recordServerFailure(exception);
        }
    }

    private StorageSnapshot captureStorageSnapshot(ServerWorld world) {
        BlockEntity blockEntity = requireStorageBlockEntity(world);
        if (!(blockEntity instanceof Inventory inventory)) {
            throw new IllegalStateException("Storage lost its Inventory contract");
        }

        NbtCompound nbt = blockEntity.createNbtWithId();
        float internalEther = nbt.getFloat(STORAGE_ETHER_KEY);
        float glintEther = 0.0f;
        int occupiedInputSlots = 0;
        List<InputSnapshot> inputs = new ArrayList<>(ITEM_HANDLER_SLOT_COUNT);
        for (int slot = 0; slot < ITEM_HANDLER_SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                inputs.add(new InputSnapshot(true, "minecraft:air", 0, 0.0f));
                continue;
            }
            occupiedInputSlots++;
            float storedEther = getGlintEther(stack);
            glintEther += storedEther;
            inputs.add(new InputSnapshot(
                    false,
                    Registries.ITEM.getId(stack.getItem()).toString(),
                    stack.getCount(),
                    storedEther
            ));
        }
        ItemStack displayStack = inventory.getStack(DISPLAY_SLOT);
        String displayItemId = Registries.ITEM.getId(displayStack.getItem()).toString();
        return new StorageSnapshot(
                internalEther,
                glintEther,
                internalEther + glintEther,
                occupiedInputSlots,
                List.copyOf(inputs),
                displayStack.getCount(),
                displayItemId,
                nbt.getString("id")
        );
    }

    private void captureAfterStableRenders(
            MinecraftClient client,
            String fileName,
            int requiredRenders,
            Consumer<ScreenshotResult> consumer,
            Stage captureStage
    ) {
        completedRenders++;
        if (completedRenders < requiredRenders || !hasExpectedFramebuffer(client)) {
            return;
        }
        transition(captureStage);
        saveScreenshot(client, fileName, consumer);
    }

    private void captureMenuAfterStableRenders(
            MinecraftClient client,
            String fileName,
            Consumer<ScreenshotResult> consumer,
            Stage captureStage
    ) {
        completedRenders++;
        if (completedRenders < REQUIRED_MENU_RENDERS || !hasExpectedFramebuffer(client)) {
            return;
        }
        transition(captureStage);
        saveScreenshot(client, fileName, consumer);
    }

    private void saveScreenshot(
            MinecraftClient client,
            String fileName,
            Consumer<ScreenshotResult> consumer
    ) {
        ForgeEvidenceLayout layout = evidenceLayout;
        if (layout == null) {
            consumer.accept(ScreenshotResult.failed("The evidence layout is not initialized"));
            return;
        }
        ScreenshotRecorder.saveScreenshot(
                layout.scenarioRoot().toFile(),
                fileName,
                client.getFramebuffer(),
                message -> consumer.accept(inspectScreenshot(layout.screenshotPath(fileName)))
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
            LOGGER.info("Forge ethereal-storage evidence complete: {}", evidenceLayout.reportsDirectory());
        } catch (IOException exception) {
            LOGGER.error("Cannot publish Forge ethereal-storage evidence", exception);
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
        StorageSnapshot transferred = transferSnapshot;
        StorageSnapshot saved = savedSnapshot;
        RestartResult restarted = restartResult;

        addAssertion(assertions, "lifecycle", lifecycleFailure.isEmpty(), lifecycleFailure);
        addAssertion(assertions, "forge_loaded", ModList.get().isLoaded("forge"), "forge");
        addAssertion(assertions, "etherology_loaded", ModList.get().isLoaded("etherology"), "etherology");
        addAssertion(assertions, "chunk_loaded", setup != null && setup.chunkLoaded(), setup);
        addAssertion(assertions, "storage_nbt_reconstructed", setup != null && setup.nbtReconstructed(), setup);
        addAssertion(
                assertions,
                "storage_block_entity_id",
                setup != null && STORAGE_BLOCK_ENTITY_ID.toString().equals(setup.blockEntityId()),
                setup == null ? null : setup.blockEntityId()
        );
        addAssertion(
                assertions,
                "inventory_size_four",
                setup != null && setup.inventorySize() == INVENTORY_SIZE,
                setup
        );

        CapabilityResult capability = setup == null ? null : setup.capability();
        List<String> unavailableCapabilityViews = capability == null
                ? capabilityViewNames()
                : List.of();
        addAssertion(
                assertions,
                "item_handler_all_sides",
                capability != null && capability.threeSlots(),
                capability == null
                        ? unavailableCapabilityViews
                        : capability.threeSlotFailureViews()
        );
        addAssertion(
                assertions,
                "item_handler_glint_valid",
                capability != null && capability.validGlint(),
                capability == null
                        ? unavailableCapabilityViews
                        : capability.glintValidityFailureViews()
        );
        addAssertion(
                assertions,
                "item_handler_simulated_insert",
                capability != null && capability.simulatedInsertion(),
                capability == null
                        ? unavailableCapabilityViews
                        : capability.simulatedInsertionFailureViews()
        );
        addAssertion(
                assertions,
                "item_handler_live_insert",
                capability != null && capability.inserted(),
                capability == null
                        ? unavailableCapabilityViews
                        : capability.liveInsertionFailureViews()
        );
        addAssertion(
                assertions,
                "item_handler_extraction_blocked",
                capability != null && capability.extractionBlocked(),
                capability == null
                        ? unavailableCapabilityViews
                        : capability.extractionFailureViews()
        );
        addAssertion(
                assertions,
                "item_handler_display_hidden",
                capability != null && capability.displaySlotHidden(),
                capability == null
                        ? unavailableCapabilityViews
                        : capability.displaySlotFailureViews()
        );
        addAssertion(
                assertions,
                "glint_transfer_preserves_total",
                transferred != null
                        && sameTotalEther(EXPECTED_TOTAL_ETHER, transferred.totalEther()),
                transferred
        );
        addAssertion(
                assertions,
                "glint_transfer_moved_ether",
                transferred != null
                        && transferred.internalEther() < INITIAL_INTERNAL_ETHER
                        && transferred.glintEther() > INITIAL_GLINT_ETHER,
                transferred
        );
        addAssertion(
                assertions,
                "viewer_open_invoked",
                Boolean.TRUE.equals(openLifecycleResult),
                openLifecycleResult
        );
        addAssertion(
                assertions,
                "viewer_close_invoked",
                Boolean.TRUE.equals(closeLifecycleResult),
                closeLifecycleResult
        );
        addAssertion(assertions, "menu_opened", Boolean.TRUE.equals(menuOpenResult), menuOpenResult);
        addAssertion(
                assertions,
                "menu_client_type",
                menuClientTypeMatched,
                STORAGE_SCREEN_ID
        );
        addAssertion(assertions, "forced_save", Boolean.TRUE.equals(saveResult), saveResult);
        addAssertion(assertions, "saved_snapshot", saved != null, saved);
        addAssertion(
                assertions,
                "restart_ether_distribution",
                restarted != null && restarted.etherDistributionRetained(),
                restarted
        );
        addAssertion(
                assertions,
                "restart_input_inventory",
                restarted != null && restarted.inputInventoryRetained(),
                restarted
        );
        addAssertion(
                assertions,
                "restart_block_entity_type",
                restarted != null && restarted.blockEntityTypeRetained(),
                restarted
        );
        addAssertion(assertions, "menu_reopened", Boolean.TRUE.equals(reopenedMenuResult), reopenedMenuResult);
        addAssertion(
                assertions,
                "reopened_menu_client_type",
                reopenedMenuClientTypeMatched,
                STORAGE_SCREEN_ID
        );
        addAssertion(
                assertions,
                "display_slot_tracks_internal_only",
                transferred != null
                        && ETHER_ID.toString().equals(transferred.displayItemId())
                        && transferred.displayCount()
                        == (int) Math.floor(transferred.internalEther()),
                transferred
        );
        addScreenshotAssertions(assertions, "closed", closedScreenshot);
        addScreenshotAssertions(assertions, "open", openScreenshot);
        addScreenshotAssertions(assertions, "closed_again", closedAgainScreenshot);
        addScreenshotAssertions(assertions, "menu", menuScreenshot);
        addScreenshotAssertions(assertions, "reopened", reopenedScreenshot);
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

    private static void addScreenshotAssertions(
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
        lifecycleFailure = failure == null ? "Unknown Forge E2E failure" : failure;
        LOGGER.error("Forge ethereal-storage lifecycle failure: {}", lifecycleFailure);
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
        LOGGER.error("Forge storage server operation failed", exception);
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

    private boolean hasClientStorageMirror(MinecraftClient client) {
        return isWorldReady(client)
                && client.world.getChunkManager().isChunkLoaded(0, 0)
                && STORAGE_BLOCK_ID.equals(
                        Registries.BLOCK.getId(client.world.getBlockState(STORAGE_POS).getBlock())
                )
                && client.world.getBlockEntity(STORAGE_POS) != null;
    }

    private boolean hasExpectedScreenHandler(MinecraftClient client) {
        if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            return false;
        }
        Identifier handlerId = Registries.SCREEN_HANDLER.getId(
                handledScreen.getScreenHandler().getType()
        );
        return STORAGE_SCREEN_ID.equals(handlerId);
    }

    private boolean hasExpectedFramebuffer(MinecraftClient client) {
        return client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT;
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
        BlockPos end = new BlockPos(8, ARENA_FLOOR_Y + 8, 8);
        for (BlockPos pos : BlockPos.iterate(start, end)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                Block floor = (x + z) % 7 == 0 ? Blocks.SEA_LANTERN : Blocks.SMOOTH_STONE;
                world.setBlockState(new BlockPos(x, ARENA_FLOOR_Y, z), floor.getDefaultState(), 3);
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

    private static BlockEntity requireStorageBlockEntity(ServerWorld world) {
        BlockEntity blockEntity = world.getBlockEntity(STORAGE_POS);
        if (blockEntity == null) {
            throw new IllegalStateException("The storage block entity is missing");
        }
        Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
        if (!STORAGE_BLOCK_ENTITY_ID.equals(id)) {
            throw new IllegalStateException("Unexpected storage block entity id: " + id);
        }
        return blockEntity;
    }

    private static void setGlintEther(ItemStack stack, float ether) {
        stack.getOrCreateSubNbt(COMPONENTS_KEY).putFloat(STORED_ETHER_KEY, ether);
    }

    private static float getGlintEther(ItemStack stack) {
        NbtCompound components = stack.getSubNbt(COMPONENTS_KEY);
        return components == null ? 0.0f : components.getFloat(STORED_ETHER_KEY);
    }

    private static boolean sameTotalEther(float left, float right) {
        return Math.abs(left - right) < 0.001f;
    }

    private static boolean samePersistedEtherState(
            StorageSnapshot saved,
            StorageSnapshot reloaded
    ) {
        return Float.compare(saved.internalEther(), reloaded.internalEther()) == 0
                && Float.compare(saved.glintEther(), reloaded.glintEther()) == 0
                && Float.compare(saved.totalEther(), reloaded.totalEther()) == 0
                && saved.displayCount() == reloaded.displayCount()
                && saved.displayItemId().equals(reloaded.displayItemId());
    }

    private static boolean hasQuiescentPersistenceState(StorageSnapshot snapshot) {
        return Float.compare(snapshot.internalEther(), 0.0f) == 0
                && Float.compare(snapshot.glintEther(), EXPECTED_TOTAL_ETHER) == 0
                && Float.compare(snapshot.totalEther(), EXPECTED_TOTAL_ETHER) == 0
                && snapshot.occupiedInputSlots() == 1;
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
        WAITING_FOR_TRANSFER,
        WAITING_FOR_CLOSED_RENDERS,
        CAPTURING_CLOSED,
        OPENING_LIFECYCLE,
        WAITING_FOR_OPEN_RENDERS,
        CAPTURING_OPEN,
        CLOSING_LIFECYCLE,
        WAITING_FOR_CLOSE_RENDERS,
        CAPTURING_CLOSED_AGAIN,
        OPENING_MENU,
        WAITING_FOR_MENU_RENDERS,
        CAPTURING_MENU,
        WAITING_FOR_QUIESCENT_STORAGE,
        SAVING,
        DISCONNECTING,
        WAITING_FOR_RESTART_TITLE,
        RESTARTING_WORLD,
        WAITING_FOR_RESTART_WORLD,
        WAITING_FOR_RESTART_INSPECTION,
        REOPENING_MENU,
        WAITING_FOR_REOPENED_RENDERS,
        CAPTURING_REOPENED,
        COMPLETE
    }

    private record CapabilityResult(
            List<String> threeSlotFailureViews,
            List<String> glintValidityFailureViews,
            List<String> simulatedInsertionFailureViews,
            List<String> liveInsertionFailureViews,
            List<String> extractionFailureViews,
            List<String> displaySlotFailureViews
    ) {

        boolean threeSlots() {
            return threeSlotFailureViews.isEmpty();
        }

        boolean validGlint() {
            return glintValidityFailureViews.isEmpty();
        }

        boolean simulatedInsertion() {
            return simulatedInsertionFailureViews.isEmpty();
        }

        boolean inserted() {
            return liveInsertionFailureViews.isEmpty();
        }

        boolean extractionBlocked() {
            return extractionFailureViews.isEmpty();
        }

        boolean displaySlotHidden() {
            return displaySlotFailureViews.isEmpty();
        }
    }

    private record CapabilityViewResult(
            boolean threeSlots,
            boolean validGlint,
            boolean simulatedInsertionWithoutMutation,
            boolean liveInsertion,
            boolean extractionBlocked,
            boolean displaySlotHidden
    ) {

        static CapabilityViewResult failed() {
            return new CapabilityViewResult(false, false, false, false, false, false);
        }
    }

    private record SetupResult(
            boolean chunkLoaded,
            boolean nbtReconstructed,
            String blockEntityId,
            int inventorySize,
            CapabilityResult capability
    ) {
    }

    private record StorageSnapshot(
            float internalEther,
            float glintEther,
            float totalEther,
            int occupiedInputSlots,
            List<InputSnapshot> inputs,
            int displayCount,
            String displayItemId,
            String blockEntityId
    ) {
    }

    private record InputSnapshot(
            boolean empty,
            String itemId,
            int count,
            float storedEther
    ) {
    }

    private record RestartResult(
            StorageSnapshot snapshot,
            boolean etherDistributionRetained,
            boolean inputInventoryRetained,
            boolean blockEntityTypeRetained
    ) {
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
