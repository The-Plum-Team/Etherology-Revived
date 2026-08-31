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
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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

final class StorageUtilitiesScenario implements ClientScenario {

    static final String SCENARIO_ID = "storage-utilities";
    static final String WORLD_DIRECTORY_NAME = "etherology-e2e-storage-utilities-world";
    static final String BEFORE_SCREENSHOT_FILE_NAME = "storage-utilities-before.png";
    static final String AFTER_SCREENSHOT_FILE_NAME = "storage-utilities-after.png";

    private static final Logger LOGGER = LoggerFactory.getLogger("EtherologyE2EHarness");
    private static final String WORLD_DISPLAY_NAME = "Etherology E2E Storage Utilities";
    private static final long WORLD_SEED = 0x53544f52414745L;
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int REQUIRED_COMPLETED_RENDERS = 2;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final int CRATE_ITEM_COUNT = 5;
    private static final int SHELF_ITEM_COUNT = 3;
    private static final int TUNING_FORK_NOTE = 12;
    private static final Identifier ETHEROLOGY_TITLE_RESOURCE =
            new Identifier("etherology", "models/item/oculus.json");
    private static final Identifier CRATE_SCREEN_HANDLER_ID = etherologyId("crate_screen_handler");
    private static final BlockPos CAMERA_BLOCK_POS = new BlockPos(0, ARENA_FLOOR_Y + 1, -8);
    private static final FixtureBlock CRATE = fixture(
            "crate",
            "crate_block_entity",
            -5,
            2
    );
    private static final FixtureBlock SHELF = fixture(
            "shelf_slab",
            "furniture_block_entity",
            -2,
            2
    );
    private static final FixtureBlock SPILL_BARREL = fixture(
            "spill_barrel",
            "spill_barrel_block_entity",
            2,
            2
    );
    private static final FixtureBlock TUNING_FORK = fixture(
            "tuning_fork",
            "tuning_fork_block_entity",
            5,
            2
    );
    private static final List<FixtureBlock> FIXTURE_BLOCKS = List.of(
            CRATE,
            SHELF,
            SPILL_BARREL,
            TUNING_FORK
    );
    private static final RegistryExpectation[] REGISTRY_EXPECTATIONS = {
            block("crate"),
            block("shelf_slab"),
            block("spill_barrel"),
            block("tuning_fork"),
            blockEntityType("crate_block_entity"),
            blockEntityType("furniture_block_entity"),
            blockEntityType("spill_barrel_block_entity"),
            blockEntityType("tuning_fork_block_entity"),
            new RegistryExpectation(
                    "screen_handler",
                    Registries.SCREEN_HANDLER,
                    CRATE_SCREEN_HANDLER_ID
            )
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
    private boolean crateScreenSubmitted;
    private boolean interactionsSubmitted;
    private boolean saveSubmitted;
    private boolean registryPreflightPassed;
    private String lifecycleFailure = "";
    private EvidenceLayout evidenceLayout;
    private ClientFixtureSnapshot initialClientSnapshot;
    private ClientFixtureSnapshot configuredClientSnapshot;
    private volatile ScreenshotResult beforeScreenshotResult;
    private volatile ScreenshotResult afterScreenshotResult;
    private volatile InitialSetupResult initialSetupResult;
    private volatile String initialSetupFailure = "";
    private volatile CrateScreenResult crateScreenResult;
    private volatile String crateScreenFailure = "";
    private volatile InteractionResult interactionResult;
    private volatile String interactionFailure = "";
    private volatile Boolean saveResult;
    private volatile PostSaveResult postSaveResult;
    private volatile String saveFailure = "";
    private String clientCrateScreenHandlerId = "";

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
                case WAITING_FOR_INITIAL_CLIENT_MIRROR -> tickWaitingForInitialClientMirror(client);
                case WAITING_FOR_BEFORE_RENDERS -> tickWaitingForBeforeRenders(client);
                case CAPTURING_BEFORE -> tickCapturingBefore(client);
                case WAITING_FOR_CRATE_SCREEN -> tickWaitingForCrateScreen(client);
                case WAITING_FOR_INTERACTIONS -> tickWaitingForInteractions(client);
                case WAITING_FOR_CONFIGURED_CLIENT_MIRROR -> tickWaitingForConfiguredClientMirror(client);
                case WAITING_FOR_AFTER_RENDERS -> tickWaitingForAfterRenders(client);
                case CAPTURING_AFTER -> tickCapturingAfter(client);
                case SAVING_WORLD -> tickSavingWorld(client);
                case COMPLETE -> {
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Etherology storage-utilities failed while in {}", stage, exception);
            fail(
                    client,
                    stage + " raised " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()
            );
            return;
        }

        if (stage != Stage.COMPLETE && stageClientTicks >= MAXIMUM_STAGE_CLIENT_TICKS) {
            fail(client, "Timed out in " + stage + " after " + stageClientTicks + " client ticks");
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
            if (!isWorldViewReady(client, initialClientSnapshot)) {
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
        if (!isWorldViewReady(client, configuredClientSnapshot)) {
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
        for (FixtureBlock fixtureBlock : FIXTURE_BLOCKS) {
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
            LOGGER.error("Cannot use the isolated storage-utilities evidence layout", exception);
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
            fail(client, "Refusing to reuse the storage-utilities save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "The client already has a world before storage-utilities creation");
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
            fail(client, "Initial server fixture setup failed: " + initialSetupFailure);
            return;
        }
        if (initialSetupResult == null) return;

        transition(Stage.WAITING_FOR_INITIAL_CLIENT_MIRROR);
    }

    private void tickWaitingForInitialClientMirror(MinecraftClient client) {
        ClientFixtureSnapshot snapshot = captureClientFixture(client, false);
        if (snapshot == null || !snapshot.matchesInitialState()) return;

        initialClientSnapshot = snapshot;
        transition(Stage.WAITING_FOR_BEFORE_RENDERS);
    }

    private void tickWaitingForBeforeRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before the initial capture");
        }
    }

    private void tickCapturingBefore(MinecraftClient client) {
        ScreenshotResult result = beforeScreenshotResult;
        if (result == null) return;
        if (!result.passed()) {
            fail(client, "The initial fixture screenshot failed: " + result.failure());
            return;
        }

        submitCrateScreenOpen(client);
    }

    private void tickWaitingForCrateScreen(MinecraftClient client) {
        if (!crateScreenFailure.isEmpty()) {
            fail(client, "Crate screen interaction failed: " + crateScreenFailure);
            return;
        }
        CrateScreenResult result = crateScreenResult;
        if (result == null || !result.openedExpectedScreen()) return;
        if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) return;

        Identifier screenHandlerId = Registries.SCREEN_HANDLER.getId(
                handledScreen.getScreenHandler().getType()
        );
        if (!CRATE_SCREEN_HANDLER_ID.equals(screenHandlerId)) return;

        clientCrateScreenHandlerId = screenHandlerId.toString();
        submitInteractions(client);
    }

    private void tickWaitingForInteractions(MinecraftClient client) {
        if (!interactionFailure.isEmpty()) {
            fail(client, "Storage-utilities server interactions failed: " + interactionFailure);
            return;
        }
        if (interactionResult == null) return;

        transition(Stage.WAITING_FOR_CONFIGURED_CLIENT_MIRROR);
    }

    private void tickWaitingForConfiguredClientMirror(MinecraftClient client) {
        if (client.currentScreen != null) return;

        ClientFixtureSnapshot snapshot = captureClientFixture(client, true);
        if (snapshot == null || !snapshot.matchesConfiguredState()) return;

        configuredClientSnapshot = snapshot;
        transition(Stage.WAITING_FOR_AFTER_RENDERS);
    }

    private void tickWaitingForAfterRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before the configured capture");
        }
    }

    private void tickCapturingAfter(MinecraftClient client) {
        ScreenshotResult result = afterScreenshotResult;
        if (result == null) return;
        if (!result.passed()) {
            fail(client, "The configured fixture screenshot failed: " + result.failure());
            return;
        }

        submitSave(client);
    }

    private void tickSavingWorld(MinecraftClient client) {
        if (!saveFailure.isEmpty()) {
            fail(client, "The integrated-world save failed: " + saveFailure);
            return;
        }
        if (saveResult == null || postSaveResult == null) return;

        publish(client);
    }

    private void setupInitialFixture(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = requireServerPlayer(server, playerId);
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
            List<String> blockEntityIds = new ArrayList<>();
            boolean allBlocksPlaced = true;
            boolean allBlockEntitiesPresent = true;
            for (FixtureBlock fixtureBlock : FIXTURE_BLOCKS) {
                Block block = Registries.BLOCK.get(fixtureBlock.blockId());
                world.setBlockState(fixtureBlock.pos(), block.getDefaultState(), Block.NOTIFY_ALL);

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
                blockEntityIds.add(
                        fixtureBlock.pos().toShortString() + "=" + actualBlockEntityId
                );
                allBlockEntitiesPresent &= fixtureBlock.blockEntityTypeId()
                        .equals(actualBlockEntityId);
            }

            Inventory crateInventory = requireInventory(world, CRATE);
            Inventory barrelInventory = requireInventory(world, SPILL_BARREL);
            NbtCompound shelfNbt = requireBlockEntity(world, SHELF).createNbtWithId();
            boolean crateEmpty = crateInventory.isEmpty();
            boolean shelfEmpty = shelfInventoryCount(shelfNbt, Items.BOOK) == 0;
            boolean barrelEmpty = barrelInventory.isEmpty();
            String tuningNote = statePropertyValue(
                    world.getBlockState(TUNING_FORK.pos()),
                    "note"
            );
            String barrelFrame = statePropertyValue(
                    world.getBlockState(SPILL_BARREL.pos()),
                    "with_frame"
            );

            player.getInventory().clear();
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            boolean playerCreative = player.changeGameMode(GameMode.CREATIVE) || player.isCreative();
            player.teleport(
                    world,
                    CAMERA_BLOCK_POS.getX() + 0.5,
                    CAMERA_BLOCK_POS.getY(),
                    CAMERA_BLOCK_POS.getZ() + 0.5,
                    0.0f,
                    8.0f
            );
            player.setSpawnPoint(World.OVERWORLD, CAMERA_BLOCK_POS, 0.0f, true, false);

            initialSetupResult = new InitialSetupResult(
                    chunkLoaded,
                    playerCreative,
                    allBlocksPlaced,
                    allBlockEntitiesPresent,
                    crateEmpty,
                    shelfEmpty,
                    barrelEmpty,
                    tuningNote,
                    barrelFrame,
                    List.copyOf(placedBlockIds),
                    List.copyOf(blockEntityIds)
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot arrange the storage-utilities initial fixture", exception);
            initialSetupFailure = describe(exception);
        }
    }

    private void submitCrateScreenOpen(MinecraftClient client) {
        if (crateScreenSubmitted) return;

        IntegratedServer server = requireRunningServer(client);
        crateScreenSubmitted = true;
        UUID playerId = client.player.getUuid();
        server.execute(() -> openCrateScreen(server, playerId));
        transition(Stage.WAITING_FOR_CRATE_SCREEN);
    }

    private void openCrateScreen(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = requireServerPlayer(server, playerId);
            ActionResult useResult = useBlock(
                    world,
                    CRATE.pos(),
                    player,
                    Direction.NORTH,
                    0.5,
                    0.5,
                    0.0
            );
            Identifier handlerId = Registries.SCREEN_HANDLER.getId(
                    player.currentScreenHandler.getType()
            );
            crateScreenResult = new CrateScreenResult(
                    useResult.isAccepted(),
                    CRATE_SCREEN_HANDLER_ID.equals(handlerId),
                    handlerId.toString()
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot open the crate screen on the integrated server", exception);
            crateScreenFailure = describe(exception);
        }
    }

    private void submitInteractions(MinecraftClient client) {
        if (interactionsSubmitted) return;

        IntegratedServer server = requireRunningServer(client);
        interactionsSubmitted = true;
        UUID playerId = client.player.getUuid();
        server.execute(() -> executeInteractions(server, playerId));
        transition(Stage.WAITING_FOR_INTERACTIONS);
    }

    private void executeInteractions(IntegratedServer server, UUID playerId) {
        try {
            ServerWorld world = server.getOverworld();
            ServerPlayerEntity player = requireServerPlayer(server, playerId);
            player.closeHandledScreen();

            BlockEntity crateBlockEntity = requireBlockEntity(world, CRATE);
            Inventory crateInventory = requireInventory(crateBlockEntity, CRATE);
            crateInventory.setStack(0, new ItemStack(Items.DIAMOND, CRATE_ITEM_COUNT));
            crateBlockEntity.markDirty();
            world.getChunkManager().markForUpdate(CRATE.pos());

            player.setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.BOOK, SHELF_ITEM_COUNT)
            );
            ActionResult shelfUseResult = useBlock(
                    world,
                    SHELF.pos(),
                    player,
                    Direction.NORTH,
                    0.25,
                    0.25,
                    0.0
            );
            boolean shelfHandEmptied = player.getStackInHand(Hand.MAIN_HAND).isEmpty();

            boolean survivalForExchange = player.changeGameMode(GameMode.SURVIVAL)
                    || !player.isCreative();
            ItemStack healingPotion = PotionUtil.setPotion(
                    new ItemStack(Items.POTION),
                    Potions.HEALING
            );
            player.setStackInHand(Hand.MAIN_HAND, healingPotion);
            ActionResult barrelUseResult = useBlock(
                    world,
                    SPILL_BARREL.pos(),
                    player,
                    Direction.NORTH,
                    0.5,
                    0.5,
                    0.0
            );
            boolean barrelReturnedBottle = player.getStackInHand(Hand.MAIN_HAND)
                    .isOf(Items.GLASS_BOTTLE);
            world.getChunkManager().markForUpdate(SPILL_BARREL.pos());

            world.setBlockState(
                    SPILL_BARREL.pos().down(),
                    Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_ALL
            );

            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            boolean everyTuningUseAccepted = true;
            for (int use = 0; use < TUNING_FORK_NOTE; use++) {
                ActionResult tuningUseResult = useBlock(
                        world,
                        TUNING_FORK.pos(),
                        player,
                        Direction.UP,
                        0.5,
                        1.0,
                        0.5
                );
                everyTuningUseAccepted &= tuningUseResult.isAccepted();
            }
            boolean creativeRestored = player.changeGameMode(GameMode.CREATIVE)
                    || player.isCreative();

            int crateCount = inventoryCount(requireInventory(world, CRATE), Items.DIAMOND);
            NbtCompound shelfNbt = requireBlockEntity(world, SHELF).createNbtWithId();
            int shelfCount = shelfInventoryCount(shelfNbt, Items.BOOK);
            Inventory barrelInventory = requireInventory(world, SPILL_BARREL);
            int barrelPotionCount = inventoryCount(barrelInventory, Items.POTION);
            boolean barrelContainsHealing = inventoryContainsPotion(
                    barrelInventory,
                    Potions.HEALING
            );
            String barrelFrame = statePropertyValue(
                    world.getBlockState(SPILL_BARREL.pos()),
                    "with_frame"
            );
            String tuningNote = statePropertyValue(
                    world.getBlockState(TUNING_FORK.pos()),
                    "note"
            );

            RoundTripResult crateRoundTrip = roundTripInventory(
                    world,
                    CRATE,
                    Items.DIAMOND,
                    CRATE_ITEM_COUNT
            );
            RoundTripResult shelfRoundTrip = roundTripShelf(world);
            RoundTripResult barrelRoundTrip = roundTripBarrel(world);
            RoundTripResult tuningRoundTrip = roundTripTuningFork(world);

            interactionResult = new InteractionResult(
                    crateCount,
                    shelfUseResult.isAccepted(),
                    shelfHandEmptied,
                    shelfCount,
                    survivalForExchange,
                    barrelUseResult.isAccepted(),
                    barrelReturnedBottle,
                    barrelPotionCount,
                    barrelContainsHealing,
                    barrelFrame,
                    everyTuningUseAccepted,
                    tuningNote,
                    creativeRestored,
                    crateRoundTrip,
                    shelfRoundTrip,
                    barrelRoundTrip,
                    tuningRoundTrip
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot execute the storage-utilities interactions", exception);
            interactionFailure = describe(exception);
        }
    }

    private RoundTripResult roundTripInventory(
            ServerWorld world,
            FixtureBlock fixtureBlock,
            Item expectedItem,
            int expectedCount
    ) {
        BlockEntity original = requireBlockEntity(world, fixtureBlock);
        NbtCompound serialized = original.createNbtWithId();
        BlockEntity restored = BlockEntity.createFromNbt(
                fixtureBlock.pos(),
                world.getBlockState(fixtureBlock.pos()),
                serialized
        );
        if (!(restored instanceof Inventory inventory)) {
            return new RoundTripResult(false, serialized.getString("id"), 0, "not inventory");
        }

        int restoredCount = inventoryCount(inventory, expectedItem);
        boolean typeMatches = restored.getType() == original.getType();
        return new RoundTripResult(
                typeMatches && restoredCount == expectedCount,
                serialized.getString("id"),
                restoredCount,
                typeMatches ? "matching type" : "different type"
        );
    }

    private RoundTripResult roundTripShelf(ServerWorld world) {
        BlockEntity original = requireBlockEntity(world, SHELF);
        NbtCompound serialized = original.createNbtWithId();
        BlockEntity restored = BlockEntity.createFromNbt(
                SHELF.pos(),
                world.getBlockState(SHELF.pos()),
                serialized
        );
        if (restored == null) {
            return new RoundTripResult(false, serialized.getString("id"), 0, "not restored");
        }

        int restoredCount = shelfInventoryCount(restored.createNbtWithId(), Items.BOOK);
        boolean typeMatches = restored.getType() == original.getType();
        return new RoundTripResult(
                typeMatches && restoredCount == SHELF_ITEM_COUNT,
                serialized.getString("id"),
                restoredCount,
                typeMatches ? "matching type" : "different type"
        );
    }

    private RoundTripResult roundTripBarrel(ServerWorld world) {
        BlockEntity original = requireBlockEntity(world, SPILL_BARREL);
        NbtCompound serialized = original.createNbtWithId();
        BlockEntity restored = BlockEntity.createFromNbt(
                SPILL_BARREL.pos(),
                world.getBlockState(SPILL_BARREL.pos()),
                serialized
        );
        if (!(restored instanceof Inventory inventory)) {
            return new RoundTripResult(false, serialized.getString("id"), 0, "not inventory");
        }

        int restoredCount = inventoryCount(inventory, Items.POTION);
        boolean retainedHealingPotion = inventoryContainsPotion(inventory, Potions.HEALING);
        boolean typeMatches = restored.getType() == original.getType();
        return new RoundTripResult(
                typeMatches && restoredCount == 1 && retainedHealingPotion,
                serialized.getString("id"),
                restoredCount,
                "matching_type=" + typeMatches + ", healing=" + retainedHealingPotion
        );
    }

    private RoundTripResult roundTripTuningFork(ServerWorld world) {
        BlockEntity original = requireBlockEntity(world, TUNING_FORK);
        NbtCompound serialized = original.createNbtWithId();
        serialized.putInt("reloading_ticks", 4);
        serialized.putInt("delay", 7);
        serialized.putInt("received_note", TUNING_FORK_NOTE);
        BlockEntity restored = BlockEntity.createFromNbt(
                TUNING_FORK.pos(),
                world.getBlockState(TUNING_FORK.pos()),
                serialized
        );
        if (restored == null) {
            return new RoundTripResult(false, serialized.getString("id"), 0, "not restored");
        }

        NbtCompound restoredNbt = restored.createNbtWithId();
        boolean retained = restored.getType() == original.getType()
                && restoredNbt.getInt("reloading_ticks") == 4
                && restoredNbt.getInt("delay") == 7
                && restoredNbt.getInt("received_note") == TUNING_FORK_NOTE;
        return new RoundTripResult(
                retained,
                serialized.getString("id"),
                restoredNbt.getInt("received_note"),
                "delay=" + restoredNbt.getInt("delay")
                        + ", reloading_ticks=" + restoredNbt.getInt("reloading_ticks")
        );
    }

    private ClientFixtureSnapshot captureClientFixture(
            MinecraftClient client,
            boolean configured
    ) {
        if (!isWorldLifecycleReady(client)) return null;
        if (!client.world.getChunkManager().isChunkLoaded(0, 0)) return null;
        if (!client.player.isCreative()) return null;

        for (FixtureBlock fixtureBlock : FIXTURE_BLOCKS) {
            Identifier actualBlockId = Registries.BLOCK.getId(
                    client.world.getBlockState(fixtureBlock.pos()).getBlock()
            );
            if (!fixtureBlock.blockId().equals(actualBlockId)) return null;
            if (client.world.getBlockEntity(fixtureBlock.pos()) == null) return null;
        }

        Inventory crateInventory = requireInventory(
                client.world.getBlockEntity(CRATE.pos()),
                CRATE
        );
        Inventory barrelInventory = requireInventory(
                client.world.getBlockEntity(SPILL_BARREL.pos()),
                SPILL_BARREL
        );
        BlockEntity shelfBlockEntity = client.world.getBlockEntity(SHELF.pos());
        if (shelfBlockEntity == null) return null;

        int crateCount = inventoryCount(crateInventory, Items.DIAMOND);
        int shelfCount = shelfInventoryCount(shelfBlockEntity.createNbtWithId(), Items.BOOK);
        int barrelPotionCount = inventoryCount(barrelInventory, Items.POTION);
        boolean barrelContainsHealing = inventoryContainsPotion(
                barrelInventory,
                Potions.HEALING
        );
        String barrelFrame = statePropertyValue(
                client.world.getBlockState(SPILL_BARREL.pos()),
                "with_frame"
        );
        String tuningNote = statePropertyValue(
                client.world.getBlockState(TUNING_FORK.pos()),
                "note"
        );
        return new ClientFixtureSnapshot(
                configured,
                crateCount,
                shelfCount,
                barrelPotionCount,
                barrelContainsHealing,
                barrelFrame,
                tuningNote
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
        saveSubmitted = true;
        server.execute(() -> {
            try {
                saveResult = server.saveAll(false, true, true);
                postSaveResult = inspectPostSave(server.getOverworld());
            } catch (RuntimeException exception) {
                LOGGER.error("Cannot force-save the storage-utilities integrated world", exception);
                saveFailure = describe(exception);
            }
        });
        transition(Stage.SAVING_WORLD);
    }

    private PostSaveResult inspectPostSave(ServerWorld world) {
        int crateCount = inventoryCount(requireInventory(world, CRATE), Items.DIAMOND);
        int shelfCount = shelfInventoryCount(
                requireBlockEntity(world, SHELF).createNbtWithId(),
                Items.BOOK
        );
        Inventory barrelInventory = requireInventory(world, SPILL_BARREL);
        int barrelPotionCount = inventoryCount(barrelInventory, Items.POTION);
        String barrelFrame = statePropertyValue(
                world.getBlockState(SPILL_BARREL.pos()),
                "with_frame"
        );
        String tuningNote = statePropertyValue(
                world.getBlockState(TUNING_FORK.pos()),
                "note"
        );
        boolean retained = crateCount == CRATE_ITEM_COUNT
                && shelfCount == SHELF_ITEM_COUNT
                && barrelPotionCount == 1
                && inventoryContainsPotion(barrelInventory, Potions.HEALING)
                && "true".equals(barrelFrame)
                && Integer.toString(TUNING_FORK_NOTE).equals(tuningNote);
        return new PostSaveResult(
                retained,
                crateCount,
                shelfCount,
                barrelPotionCount,
                barrelFrame,
                tuningNote
        );
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;

        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Etherology storage-utilities lifecycle failure: {}", lifecycleFailure);
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
                    "Etherology storage-utilities evidence is complete: {}",
                    evidenceLayout.reportsDirectory()
            );
        } catch (IOException exception) {
            LOGGER.error("Cannot atomically publish storage-utilities evidence", exception);
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
                "all expected block-entity identifiers",
                setup == null ? "missing setup" : setup.blockEntityIds().toString()
        );
        passed &= addAssertion(
                assertions,
                "server_fixture_initial_state",
                setup != null && setup.matchesInitialState(),
                "empty inventories, note=0, with_frame=false",
                setup == null ? "missing setup" : setup.stateSummary()
        );
        passed &= addAssertion(
                assertions,
                "client_fixture_initial_mirror",
                initialClientSnapshot != null && initialClientSnapshot.matchesInitialState(),
                "empty inventories, note=0, with_frame=false",
                initialClientSnapshot == null ? "missing" : initialClientSnapshot.summary()
        );

        CrateScreenResult screenResult = crateScreenResult;
        passed &= addAssertion(
                assertions,
                "server_crate_screen_opened",
                screenResult != null && screenResult.openedExpectedScreen(),
                CRATE_SCREEN_HANDLER_ID.toString(),
                screenResult == null ? "missing" : screenResult.handlerId()
        );
        passed &= addAssertion(
                assertions,
                "client_crate_screen_mirrored",
                CRATE_SCREEN_HANDLER_ID.toString().equals(clientCrateScreenHandlerId),
                CRATE_SCREEN_HANDLER_ID.toString(),
                clientCrateScreenHandlerId.isEmpty() ? "missing" : clientCrateScreenHandlerId
        );

        InteractionResult interaction = interactionResult;
        passed &= addAssertion(
                assertions,
                "crate_inventory_interaction",
                interaction != null && interaction.crateCount() == CRATE_ITEM_COUNT,
                CRATE_ITEM_COUNT + " diamonds",
                interaction == null ? "missing" : interaction.crateCount() + " diamonds"
        );
        passed &= addAssertion(
                assertions,
                "shelf_real_block_use",
                interaction != null
                        && interaction.shelfUseAccepted()
                        && interaction.shelfHandEmptied()
                        && interaction.shelfCount() == SHELF_ITEM_COUNT,
                SHELF_ITEM_COUNT + " books retained and hand emptied",
                interaction == null ? "missing" : interaction.shelfSummary()
        );
        passed &= addAssertion(
                assertions,
                "spill_barrel_real_block_use",
                interaction != null
                        && interaction.survivalForExchange()
                        && interaction.barrelUseAccepted()
                        && interaction.barrelReturnedBottle()
                        && interaction.barrelPotionCount() == 1
                        && interaction.barrelContainsHealing(),
                "survival exchange retained one healing potion and returned a glass bottle",
                interaction == null ? "missing" : interaction.barrelSummary()
        );
        passed &= addAssertion(
                assertions,
                "spill_barrel_frame_neighbor_transition",
                interaction != null && "true".equals(interaction.barrelFrame()),
                "true",
                interaction == null ? "missing" : interaction.barrelFrame()
        );
        passed &= addAssertion(
                assertions,
                "tuning_fork_real_block_uses",
                interaction != null
                        && interaction.everyTuningUseAccepted()
                        && interaction.creativeRestored()
                        && Integer.toString(TUNING_FORK_NOTE).equals(interaction.tuningNote()),
                TUNING_FORK_NOTE + " accepted uses, note=" + TUNING_FORK_NOTE
                        + ", creative restored",
                interaction == null ? "missing" : interaction.tuningSummary()
        );
        passed &= addRoundTripAssertion(assertions, "crate", interaction, 0);
        passed &= addRoundTripAssertion(assertions, "shelf", interaction, 1);
        passed &= addRoundTripAssertion(assertions, "spill_barrel", interaction, 2);
        passed &= addRoundTripAssertion(assertions, "tuning_fork", interaction, 3);

        passed &= addAssertion(
                assertions,
                "client_fixture_configured_mirror",
                configuredClientSnapshot != null && configuredClientSnapshot.matchesConfiguredState(),
                "configured inventories and block states",
                configuredClientSnapshot == null ? "missing" : configuredClientSnapshot.summary()
        );
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
                "configured_state_retained_after_forced_save",
                retained != null && retained.retained(),
                "all configured inventory and block state retained",
                retained == null ? "missing" : retained.summary()
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
                "storage-utilities-before-interactions",
                BEFORE_SCREENSHOT_FILE_NAME,
                beforeFramebufferWidth,
                beforeFramebufferHeight,
                completedBeforeRenders,
                beforeScreenshotResult
        );
        addScreenshot(
                screenshots,
                "storage-utilities-after-interactions",
                AFTER_SCREENSHOT_FILE_NAME,
                afterFramebufferWidth,
                afterFramebufferHeight,
                completedAfterRenders,
                afterScreenshotResult
        );
        report.add("screenshots", screenshots);
        return report;
    }

    private boolean addRoundTripAssertion(
            JsonArray assertions,
            String mechanic,
            InteractionResult interaction,
            int roundTripIndex
    ) {
        RoundTripResult result = interaction == null
                ? null
                : interaction.roundTrips().get(roundTripIndex);
        return addAssertion(
                assertions,
                mechanic + "_block_entity_nbt_round_trip",
                result != null && result.passed(),
                "matching type and retained mechanic state",
                result == null ? "missing" : result.summary()
        );
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

    private void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-9, ARENA_FLOOR_Y, -10);
        BlockPos end = new BlockPos(9, ARENA_FLOOR_Y + 8, 9);
        for (BlockPos pos : BlockPos.iterate(start, end)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
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
                        Block.NOTIFY_ALL
                );
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
            ClientFixtureSnapshot snapshot
    ) {
        return isWorldLifecycleReady(client)
                && snapshot != null
                && client.getOverlay() == null
                && client.currentScreen == null
                && client.getCameraEntity() == client.player
                && client.world.getChunkManager().isChunkLoaded(0, 0)
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

    private BlockEntity requireBlockEntity(ServerWorld world, FixtureBlock fixtureBlock) {
        BlockEntity blockEntity = world.getBlockEntity(fixtureBlock.pos());
        if (blockEntity == null) {
            throw new IllegalStateException(
                    "The fixture has no block entity at " + fixtureBlock.pos().toShortString()
            );
        }
        return blockEntity;
    }

    private Inventory requireInventory(ServerWorld world, FixtureBlock fixtureBlock) {
        return requireInventory(requireBlockEntity(world, fixtureBlock), fixtureBlock);
    }

    private Inventory requireInventory(BlockEntity blockEntity, FixtureBlock fixtureBlock) {
        if (!(blockEntity instanceof Inventory inventory)) {
            throw new IllegalStateException(
                    "The fixture block entity is not an inventory at "
                            + fixtureBlock.pos().toShortString()
            );
        }
        return inventory;
    }

    private ActionResult useBlock(
            ServerWorld world,
            BlockPos pos,
            ServerPlayerEntity player,
            Direction side,
            double x,
            double y,
            double z
    ) {
        BlockState state = world.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(
                new Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z),
                side,
                pos,
                false
        );
        return state.getBlock().onUse(state, world, pos, player, Hand.MAIN_HAND, hit);
    }

    private int shelfInventoryCount(NbtCompound blockEntityNbt, Item item) {
        NbtCompound shelfData = blockEntityNbt
                .getCompound("bottom")
                .getCompound("data");
        return inventoryCount(shelfData, 2, item);
    }

    private int inventoryCount(NbtCompound nbt, int inventorySize, Item item) {
        DefaultedList<ItemStack> inventory = DefaultedList.ofSize(
                inventorySize,
                ItemStack.EMPTY
        );
        Inventories.readNbt(nbt, inventory);
        int count = 0;
        for (ItemStack stack : inventory) {
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int inventoryCount(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean inventoryContainsPotion(Inventory inventory, net.minecraft.potion.Potion potion) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(Items.POTION) && PotionUtil.getPotion(stack) == potion) {
                return true;
            }
        }
        return false;
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
            String blockPath,
            String blockEntityTypePath,
            int x,
            int z
    ) {
        return new FixtureBlock(
                etherologyId(blockPath),
                etherologyId(blockEntityTypePath),
                new BlockPos(x, ARENA_FLOOR_Y + 1, z)
        );
    }

    private static Identifier etherologyId(String path) {
        return new Identifier("etherology", path);
    }

    private static String describe(RuntimeException exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_INITIAL_SETUP,
        WAITING_FOR_INITIAL_CLIENT_MIRROR,
        WAITING_FOR_BEFORE_RENDERS,
        CAPTURING_BEFORE,
        WAITING_FOR_CRATE_SCREEN,
        WAITING_FOR_INTERACTIONS,
        WAITING_FOR_CONFIGURED_CLIENT_MIRROR,
        WAITING_FOR_AFTER_RENDERS,
        CAPTURING_AFTER,
        SAVING_WORLD,
        COMPLETE
    }

    private record FixtureBlock(
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
            boolean chunkLoaded,
            boolean playerCreative,
            boolean allBlocksPlaced,
            boolean allBlockEntitiesPresent,
            boolean crateEmpty,
            boolean shelfEmpty,
            boolean barrelEmpty,
            String tuningNote,
            String barrelFrame,
            List<String> placedBlockIds,
            List<String> blockEntityIds
    ) {

        private boolean matchesInitialState() {
            return crateEmpty
                    && shelfEmpty
                    && barrelEmpty
                    && "0".equals(tuningNote)
                    && "false".equals(barrelFrame);
        }

        private String stateSummary() {
            return "crate_empty=" + crateEmpty
                    + ", shelf_empty=" + shelfEmpty
                    + ", barrel_empty=" + barrelEmpty
                    + ", note=" + tuningNote
                    + ", with_frame=" + barrelFrame;
        }
    }

    private record CrateScreenResult(
            boolean useAccepted,
            boolean expectedHandler,
            String handlerId
    ) {

        private boolean openedExpectedScreen() {
            return useAccepted && expectedHandler;
        }
    }

    private record InteractionResult(
            int crateCount,
            boolean shelfUseAccepted,
            boolean shelfHandEmptied,
            int shelfCount,
            boolean survivalForExchange,
            boolean barrelUseAccepted,
            boolean barrelReturnedBottle,
            int barrelPotionCount,
            boolean barrelContainsHealing,
            String barrelFrame,
            boolean everyTuningUseAccepted,
            String tuningNote,
            boolean creativeRestored,
            RoundTripResult crateRoundTrip,
            RoundTripResult shelfRoundTrip,
            RoundTripResult barrelRoundTrip,
            RoundTripResult tuningRoundTrip
    ) {

        private List<RoundTripResult> roundTrips() {
            return List.of(
                    crateRoundTrip,
                    shelfRoundTrip,
                    barrelRoundTrip,
                    tuningRoundTrip
            );
        }

        private String shelfSummary() {
            return "accepted=" + shelfUseAccepted
                    + ", hand_empty=" + shelfHandEmptied
                    + ", books=" + shelfCount;
        }

        private String barrelSummary() {
            return "accepted=" + barrelUseAccepted
                    + ", survival=" + survivalForExchange
                    + ", returned_bottle=" + barrelReturnedBottle
                    + ", potions=" + barrelPotionCount
                    + ", healing=" + barrelContainsHealing;
        }

        private String tuningSummary() {
            return "all_accepted=" + everyTuningUseAccepted
                    + ", note=" + tuningNote
                    + ", creative_restored=" + creativeRestored;
        }
    }

    private record RoundTripResult(
            boolean passed,
            String blockEntityId,
            int retainedCount,
            String detail
    ) {

        private String summary() {
            return blockEntityId + ", retained=" + retainedCount + ", " + detail;
        }
    }

    private record ClientFixtureSnapshot(
            boolean configured,
            int crateCount,
            int shelfCount,
            int barrelPotionCount,
            boolean barrelContainsHealing,
            String barrelFrame,
            String tuningNote
    ) {

        private boolean matchesInitialState() {
            return !configured
                    && crateCount == 0
                    && shelfCount == 0
                    && barrelPotionCount == 0
                    && !barrelContainsHealing
                    && "false".equals(barrelFrame)
                    && "0".equals(tuningNote);
        }

        private boolean matchesConfiguredState() {
            return configured
                    && crateCount == CRATE_ITEM_COUNT
                    && shelfCount == SHELF_ITEM_COUNT
                    && barrelPotionCount == 1
                    && barrelContainsHealing
                    && "true".equals(barrelFrame)
                    && Integer.toString(TUNING_FORK_NOTE).equals(tuningNote);
        }

        private String summary() {
            return "configured=" + configured
                    + ", diamonds=" + crateCount
                    + ", books=" + shelfCount
                    + ", potions=" + barrelPotionCount
                    + ", healing=" + barrelContainsHealing
                    + ", with_frame=" + barrelFrame
                    + ", note=" + tuningNote;
        }
    }

    private record PostSaveResult(
            boolean retained,
            int crateCount,
            int shelfCount,
            int barrelPotionCount,
            String barrelFrame,
            String tuningNote
    ) {

        private String summary() {
            return "diamonds=" + crateCount
                    + ", books=" + shelfCount
                    + ", potions=" + barrelPotionCount
                    + ", with_frame=" + barrelFrame
                    + ", note=" + tuningNote;
        }
    }
}
