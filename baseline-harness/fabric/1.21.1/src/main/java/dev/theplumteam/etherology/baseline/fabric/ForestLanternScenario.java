package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.theplumteam.etherology.baseline.fabric.mixin.PlayerEntityJumpInvoker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.DataPackFailureScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.Window;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ForestLanternScenario implements ClientScenario {

    static final String SCENARIO_ID = "forest-lantern";
    static final String SCREENSHOT_FILE_NAME = "forest-lantern.png";
    static final String WORLD_DIRECTORY_NAME =
            "etherology-original-forest-lantern-world";
    static final String WORLD_DISPLAY_NAME =
            "Etherology Original 0.1.7 Forest Lantern";
    static final long WORLD_SEED = 0x455448464c303137L;
    static final ScenarioDefinition DEFINITION = ScenarioDefinitions.FOREST_LANTERN;

    private static final Logger LOGGER = LoggerFactory.getLogger(
            "EtherologyOriginalBaselineHarness"
    );
    private static final String REFERENCE_ID = "published-0.1.7";
    private static final String HARNESS_MOD_ID =
            "etherology_original_baseline_harness";
    private static final Identifier FOREST_LANTERN_ID = etherologyId("forest_lantern");
    private static final Identifier FOREST_LANTERN_CRUMB_ID =
            etherologyId("forest_lantern_crumb");
    private static final Identifier PEACH_LOG_ID = etherologyId("peach_log");
    private static final List<Identifier> REQUIRED_RESOURCES = List.of(
            Identifier.of("minecraft", "texts/splashes.txt"),
            etherologyId("blockstates/forest_lantern.json"),
            etherologyId("models/block/forest_lantern.json"),
            etherologyId("models/block/forest_lantern_0.json"),
            etherologyId("models/block/forest_lantern_1.json"),
            etherologyId("models/block/forest_lantern_2.json"),
            etherologyId("models/block/forest_lantern_3.json"),
            etherologyId("models/item/forest_lantern.json"),
            etherologyId("textures/block/forest_lantern.png"),
            etherologyId("textures/block/forest_lantern_0.png"),
            etherologyId("textures/block/forest_lantern_1.png"),
            etherologyId("textures/block/forest_lantern_2.png"),
            etherologyId("textures/block/forest_lantern_3.png"),
            etherologyId("textures/item/forest_lantern.png")
    );
    private static final List<RecipeExpectation> RECIPE_EXPECTATIONS = List.of(
            recipe("forest_lantern_crumb", "minecraft:smelting", FOREST_LANTERN_CRUMB_ID),
            recipe(
                    "forest_lantern_crumb_from_smoking",
                    "minecraft:smoking",
                    FOREST_LANTERN_CRUMB_ID
            ),
            recipe(
                    "forest_lantern_crumb_from_campfire",
                    "minecraft:campfire_cooking",
                    FOREST_LANTERN_CRUMB_ID
            ),
            recipe("leather", "minecraft:crafting", Identifier.ofVanilla("leather"))
    );
    private static final List<Direction> FACINGS = List.of(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    );
    private static final int MAX_AGE = 4;
    private static final int EXPECTED_STATE_COUNT = 20;
    private static final int REQUIRED_COMPLETED_RENDERS = 120;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final int FIXTURE_Y = ARENA_FLOOR_Y + 1;
    private static final BlockPos CAMERA_FLOOR_POS = new BlockPos(0, 127, -18);
    private static final double CAMERA_X = CAMERA_FLOOR_POS.getX() + 0.5;
    private static final double CAMERA_Y = CAMERA_FLOOR_POS.getY() + 1.0;
    private static final double CAMERA_Z = CAMERA_FLOOR_POS.getZ() + 0.5;
    private static final float CAMERA_YAW = 0.0f;
    private static final float CAMERA_PITCH = 23.0f;
    private static final double CAMERA_POSE_TOLERANCE = 0.0001;
    private static final long MAXIMUM_JUMP_SEED_SEARCH = 4097L;
    private static final float JUMP_BREAK_CHANCE = 0.4f;
    private static final List<StateExpectation> STATE_EXPECTATIONS =
            createStateExpectations();
    private static final List<BlockPos> RENDER_POSITIONS = STATE_EXPECTATIONS.stream()
            .map(StateExpectation::position)
            .toList();

    private final StableRenderCounter stableWorldRenders =
            new StableRenderCounter(REQUIRED_COMPLETED_RENDERS);
    private Stage stage = Stage.WAITING_FOR_TITLE;
    private int clientTicks;
    private int stageClientTicks;
    private int framebufferWidth;
    private int framebufferHeight;
    private int requestedWindowWidth = -1;
    private int requestedWindowHeight = -1;
    private boolean resourcesReady;
    private boolean registryPreflightPassed;
    private boolean setupSubmitted;
    private boolean clientMirrorReady;
    private boolean captureRenderReady;
    private boolean captureCameraExact;
    private boolean saveSubmitted;
    private String lifecycleFailure = "";
    private EvidenceLayout evidenceLayout;
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
            LOGGER.error("Original forest-lantern failed in {}", stage, exception);
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
                            + " client ticks"
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
            LOGGER.error("Original forest-lantern render callback failed", exception);
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

        resourcesReady = REQUIRED_RESOURCES.stream()
                .allMatch(identifier -> client.getResourceManager().getResource(identifier).isPresent());
        if (!resourcesReady) {
            fail(client, "The loaded resources lack the exact Forest Lantern client assets");
            return;
        }
        if (!Registries.BLOCK.containsId(FOREST_LANTERN_ID)
                || !Registries.BLOCK.containsId(PEACH_LOG_ID)
                || !Registries.ITEM.containsId(FOREST_LANTERN_ID)
                || !Registries.ITEM.containsId(FOREST_LANTERN_CRUMB_ID)) {
            fail(client, "The registries lack a required Forest Lantern fixture id");
            return;
        }

        Block forestLantern = Registries.BLOCK.get(FOREST_LANTERN_ID);
        RegistryInspection registryInspection = inspectRegistry(forestLantern);
        if (!registryInspection.passed()) {
            fail(client, "Forest Lantern registry contract failed: " + registryInspection);
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
        gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, null);

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
            LOGGER.error("Cannot inspect the original forest-lantern screenshot", exception);
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
            world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, server);

            clearArena(world);
            buildArenaFloor(world);
            Block forestLantern = Registries.BLOCK.get(FOREST_LANTERN_ID);
            Block peachLog = Registries.BLOCK.get(PEACH_LOG_ID);
            Item forestLanternItem = Registries.ITEM.get(FOREST_LANTERN_ID);

            List<String> placedStates = new ArrayList<>();
            List<String> stateNetworkIds = new ArrayList<>();
            List<String> shearsSpeeds = new ArrayList<>();
            boolean allStatesPlaced = true;
            boolean allStateNetworkIdsPresent = true;
            boolean allShearsSpeedsExact = true;
            for (StateExpectation expectation : STATE_EXPECTATIONS) {
                BlockState expectedState = forestLanternState(
                        forestLantern,
                        expectation.age(),
                        expectation.facing()
                );
                BlockPos supportPosition = expectation.position()
                        .offset(expectation.facing().getOpposite());
                world.setBlockState(supportPosition, peachLog.getDefaultState(), 3);
                world.setBlockState(expectation.position(), expectedState, 3);
                BlockState actualState = world.getBlockState(expectation.position());
                String description = stateDescription(expectation.position(), actualState);
                placedStates.add(description);
                allStatesPlaced &= actualState.equals(expectedState);
                int rawId = Block.STATE_IDS.getRawId(actualState);
                stateNetworkIds.add(description + "#" + rawId);
                allStateNetworkIdsPresent &= rawId >= 0;
                float speed = Items.SHEARS.getDefaultStack()
                        .getMiningSpeedMultiplier(actualState);
                shearsSpeeds.add(description + "=" + speed);
                allShearsSpeedsExact &= Float.floatToIntBits(speed)
                        == Float.floatToIntBits(15.0f);
            }

            List<String> lootByAge = new ArrayList<>();
            boolean immatureLootEmpty = true;
            boolean matureLootExact = true;
            BlockPos lootPosition = new BlockPos(14, FIXTURE_Y, -5);
            for (int age = 0; age <= MAX_AGE; age++) {
                BlockState state = forestLanternState(forestLantern, age, Direction.NORTH);
                List<ItemStack> drops = Block.getDroppedStacks(
                        state,
                        world,
                        lootPosition,
                        null,
                        player,
                        ItemStack.EMPTY
                );
                List<String> dropDescriptions = itemStackDescriptions(drops);
                lootByAge.add(age + "=" + dropDescriptions);
                if (age < MAX_AGE) {
                    immatureLootEmpty &= drops.isEmpty();
                } else {
                    matureLootExact &= drops.size() == 1
                            && drops.getFirst().isOf(forestLanternItem)
                            && drops.getFirst().getCount() == 1;
                }
            }

            List<String> recipes = inspectRecipes(server);
            boolean recipesExact = recipes.size() == RECIPE_EXPECTATIONS.size();

            JumpProbeResult jumpProbe = runDeterministicJumpProbe(
                    world,
                    player,
                    forestLantern,
                    peachLog,
                    forestLanternItem
            );

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
            player.setSpawnPoint(
                    World.OVERWORLD,
                    CAMERA_FLOOR_POS.up(),
                    CAMERA_YAW,
                    true,
                    false
            );

            serverSetupResult = new ServerSetupResult(
                    chunkLoaded,
                    playerCreative,
                    allStatesPlaced,
                    allStateNetworkIdsPresent,
                    allShearsSpeedsExact,
                    immatureLootEmpty,
                    matureLootExact,
                    recipesExact,
                    List.copyOf(placedStates),
                    List.copyOf(stateNetworkIds),
                    List.copyOf(shearsSpeeds),
                    List.copyOf(lootByAge),
                    List.copyOf(recipes),
                    jumpProbe,
                    server.getSaveProperties().getLevelName(),
                    world.getSeed(),
                    world.getRegistryKey().getValue().toString()
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot arrange the original forest-lantern fixture", exception);
            serverSetupFailure = exception.getClass().getSimpleName()
                    + ": " + exception.getMessage();
        }
    }

    private JumpProbeResult runDeterministicJumpProbe(
            ServerWorld world,
            ServerPlayerEntity player,
            Block forestLantern,
            Block peachLog,
            Item forestLanternItem
    ) {
        BlockPos standingFloor = new BlockPos(14, ARENA_FLOOR_Y, -12);
        world.setBlockState(standingFloor, Blocks.SMOOTH_STONE.getDefaultState(), 3);
        player.teleport(
                world,
                standingFloor.getX() + 0.5,
                standingFloor.getY() + 1.0,
                standingFloor.getZ() + 0.5,
                0.0f,
                0.0f
        );
        player.setOnGround(true);
        BlockPos steppingPosition = player.getSteppingPos();
        Direction facing = Direction.NORTH;
        world.setBlockState(
                steppingPosition.offset(facing.getOpposite()),
                peachLog.getDefaultState(),
                3
        );
        world.setBlockState(
                steppingPosition,
                forestLanternState(forestLantern, MAX_AGE, facing),
                3
        );
        boolean steppingPositionExact = player.getSteppingPos().equals(steppingPosition)
                && world.getBlockState(steppingPosition).isOf(forestLantern);

        long seed = findBreakingSeed();
        Random prediction = Random.create(seed);
        float predictedRoll = prediction.nextFloat();
        Box dropBox = new Box(steppingPosition).expand(2.0);
        Set<UUID> existingItemIds = new HashSet<>();
        for (ItemEntity entity : world.getEntitiesByClass(
                ItemEntity.class,
                dropBox,
                entity -> true
        )) {
            existingItemIds.add(entity.getUuid());
        }
        world.getRandom().setSeed(seed);
        ((PlayerEntityJumpInvoker) player).etherologyOriginalBaseline$invokeJump();

        List<ItemStack> newDrops = new ArrayList<>();
        for (ItemEntity entity : world.getEntitiesByClass(
                ItemEntity.class,
                dropBox,
                candidate -> !existingItemIds.contains(candidate.getUuid())
        )) {
            newDrops.add(entity.getStack().copy());
        }
        boolean removed = world.getBlockState(steppingPosition).isAir();
        boolean dropExact = newDrops.size() == 1
                && newDrops.getFirst().isOf(forestLanternItem)
                && newDrops.getFirst().getCount() == 1;
        return new JumpProbeResult(
                steppingPositionExact,
                seed,
                predictedRoll,
                removed,
                dropExact,
                itemStackDescriptions(newDrops),
                steppingPosition.toShortString()
        );
    }

    private long findBreakingSeed() {
        for (long seed = 0L; seed < MAXIMUM_JUMP_SEED_SEARCH; seed++) {
            if (Random.create(seed).nextFloat() <= JUMP_BREAK_CHANCE) return seed;
        }
        throw new IllegalStateException("No deterministic Forest Lantern break seed found");
    }

    private List<String> inspectRecipes(IntegratedServer server) {
        List<String> recipes = new ArrayList<>();
        for (RecipeExpectation expectation : RECIPE_EXPECTATIONS) {
            RecipeEntry<?> entry = server.getRecipeManager()
                    .get(expectation.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing recipe " + expectation.id()
                    ));
            Identifier typeId = Registries.RECIPE_TYPE.getId(entry.value().getType());
            ItemStack result = entry.value().getResult(server.getRegistryManager());
            Identifier resultId = Registries.ITEM.getId(result.getItem());
            String description = entry.id() + "=" + typeId + "->" + resultId
                    + "x" + result.getCount();
            if (!expectation.typeId().equals(typeId)
                    || !expectation.resultId().equals(resultId)
                    || result.getCount() != 1) {
                throw new IllegalStateException("Recipe contract changed: " + description);
            }
            recipes.add(description);
        }
        return recipes;
    }

    private void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-17, ARENA_FLOOR_Y, -21);
        BlockPos end = new BlockPos(17, CAMERA_FLOOR_POS.getY() + 3, 18);
        for (BlockPos position : BlockPos.iterate(start, end)) {
            world.setBlockState(position, Blocks.AIR.getDefaultState(), 3);
        }
    }

    private void buildArenaFloor(ServerWorld world) {
        for (int x = -17; x <= 17; x++) {
            for (int z = -21; z <= 18; z++) {
                Block block = (x + z) % 9 == 0
                        ? Blocks.OCHRE_FROGLIGHT
                        : Blocks.POLISHED_DEEPSLATE;
                world.setBlockState(
                        new BlockPos(x, ARENA_FLOOR_Y, z),
                        block.getDefaultState(),
                        3
                );
            }
        }
        world.setBlockState(CAMERA_FLOOR_POS, Blocks.BARRIER.getDefaultState(), 3);
        for (StateExpectation expectation : STATE_EXPECTATIONS) {
            Block marker = switch (expectation.age()) {
                case 0 -> Blocks.WHITE_CONCRETE;
                case 1 -> Blocks.LIGHT_GRAY_CONCRETE;
                case 2 -> Blocks.YELLOW_CONCRETE;
                case 3 -> Blocks.ORANGE_CONCRETE;
                case 4 -> Blocks.RED_CONCRETE;
                default -> throw new IllegalStateException("Unexpected fixture age");
            };
            world.setBlockState(expectation.position().down(), marker.getDefaultState(), 3);
        }
    }

    private boolean hasClientMirror(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) return false;
        if (!client.world.getChunkManager().isChunkLoaded(0, 0)) return false;
        if (!client.player.isCreative()) return false;
        if (client.world.getRegistryKey() != World.OVERWORLD) return false;

        Block forestLantern = Registries.BLOCK.get(FOREST_LANTERN_ID);
        for (StateExpectation expectation : STATE_EXPECTATIONS) {
            BlockState expectedState = forestLanternState(
                    forestLantern,
                    expectation.age(),
                    expectation.facing()
            );
            if (!client.world.getBlockState(expectation.position()).equals(expectedState)) {
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
        for (BlockPos position : RENDER_POSITIONS) {
            if (!client.worldRenderer.isRenderingReady(position)) return false;
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

    private RegistryInspection inspectRegistry(Block forestLantern) {
        Property<?> ageProperty = forestLantern.getStateManager().getProperty("age");
        Property<?> facingProperty = forestLantern.getStateManager().getProperty("facing");
        Set<String> propertyNames = new HashSet<>();
        for (Property<?> property : forestLantern.getStateManager().getProperties()) {
            propertyNames.add(property.getName());
        }
        List<BlockState> states = forestLantern.getStateManager().getStates();
        Set<Integer> rawIds = new HashSet<>();
        boolean networkIdsExact = true;
        for (BlockState state : states) {
            int rawId = Block.STATE_IDS.getRawId(state);
            networkIdsExact &= rawId >= 0;
            rawIds.add(rawId);
        }
        BlockState defaultState = forestLantern.getDefaultState();
        boolean defaultExact = ageProperty instanceof IntProperty
                && facingProperty instanceof DirectionProperty
                && integerPropertyValue(defaultState, (IntProperty) ageProperty) == MAX_AGE
                && directionPropertyValue(defaultState, (DirectionProperty) facingProperty)
                        == Direction.NORTH;
        return new RegistryInspection(
                propertyNames.equals(Set.of("age", "facing")),
                defaultExact,
                states.size() == EXPECTED_STATE_COUNT,
                networkIdsExact && rawIds.size() == EXPECTED_STATE_COUNT,
                propertyNames,
                states.size(),
                rawIds.size()
        );
    }

    private static BlockState forestLanternState(
            Block block,
            int age,
            Direction facing
    ) {
        Property<?> rawAge = block.getStateManager().getProperty("age");
        Property<?> rawFacing = block.getStateManager().getProperty("facing");
        if (!(rawAge instanceof IntProperty ageProperty)
                || !(rawFacing instanceof DirectionProperty facingProperty)) {
            throw new IllegalStateException("Forest Lantern age/facing properties are missing");
        }
        return block.getDefaultState()
                .with(ageProperty, age)
                .with(facingProperty, facing);
    }

    private static int integerPropertyValue(BlockState state, IntProperty property) {
        return state.get(property);
    }

    private static Direction directionPropertyValue(
            BlockState state,
            DirectionProperty property
    ) {
        return state.get(property);
    }

    private static String stateDescription(BlockPos position, BlockState state) {
        Property<?> rawAge = state.getBlock().getStateManager().getProperty("age");
        Property<?> rawFacing = state.getBlock().getStateManager().getProperty("facing");
        if (!(rawAge instanceof IntProperty ageProperty)
                || !(rawFacing instanceof DirectionProperty facingProperty)) {
            return position.toShortString() + "=invalid";
        }
        return position.toShortString() + "=age=" + state.get(ageProperty)
                + ",facing=" + state.get(facingProperty).asString();
    }

    private static List<String> itemStackDescriptions(List<ItemStack> stacks) {
        List<String> descriptions = new ArrayList<>();
        for (ItemStack stack : stacks) {
            descriptions.add(Registries.ITEM.getId(stack.getItem()) + "x" + stack.getCount());
        }
        return List.copyOf(descriptions);
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
                LOGGER.error("Cannot force-save the original forest-lantern world", exception);
                saveFailure = exception.getClass().getSimpleName()
                        + ": " + exception.getMessage();
            }
        });
        transition(Stage.SAVING_WORLD);
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;
        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Original forest-lantern lifecycle failure: {}", lifecycleFailure);
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
                    "Original forest-lantern evidence published with status {}: {}",
                    reportResult.passed() ? "passed" : "failed",
                    evidenceLayout.reportsDirectory()
            );
        } catch (IOException exception) {
            LOGGER.error("Cannot atomically publish original forest-lantern evidence", exception);
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
                "forest_lantern_resources_exact",
                resourcesReady,
                REQUIRED_RESOURCES.toString(),
                resourcesReady ? REQUIRED_RESOURCES.toString() : "missing"
        );
        passed &= addAssertion(
                assertions,
                "registry:block:etherology:forest_lantern",
                Registries.BLOCK.containsId(FOREST_LANTERN_ID),
                "present",
                Registries.BLOCK.containsId(FOREST_LANTERN_ID) ? "present" : "missing"
        );
        passed &= addAssertion(
                assertions,
                "registry:item:etherology:forest_lantern",
                Registries.ITEM.containsId(FOREST_LANTERN_ID),
                "present",
                Registries.ITEM.containsId(FOREST_LANTERN_ID) ? "present" : "missing"
        );
        passed &= addAssertion(
                assertions,
                "registry:item:etherology:forest_lantern_crumb",
                Registries.ITEM.containsId(FOREST_LANTERN_CRUMB_ID),
                "present",
                Registries.ITEM.containsId(FOREST_LANTERN_CRUMB_ID) ? "present" : "missing"
        );
        RegistryInspection registry = inspectRegistry(Registries.BLOCK.get(FOREST_LANTERN_ID));
        passed &= addAssertion(
                assertions,
                "forest_lantern_properties_exact",
                registry.propertiesExact(),
                "[age, facing]",
                registry.propertyNames().stream().sorted().toList().toString()
        );
        passed &= addAssertion(
                assertions,
                "forest_lantern_default_state_exact",
                registry.defaultStateExact(),
                "age=4,facing=north",
                stateDescription(
                        BlockPos.ORIGIN,
                        Registries.BLOCK.get(FOREST_LANTERN_ID).getDefaultState()
                ).substring(BlockPos.ORIGIN.toShortString().length() + 1)
        );
        passed &= addAssertion(
                assertions,
                "forest_lantern_state_count_exact",
                registry.stateCountExact(),
                Integer.toString(EXPECTED_STATE_COUNT),
                Integer.toString(registry.stateCount())
        );
        passed &= addAssertion(
                assertions,
                "forest_lantern_state_network_ids_exact",
                registry.networkIdsExact(),
                "20 unique non-negative raw ids",
                registry.rawIdCount() + " unique non-negative raw ids"
        );
        passed &= registryPreflightPassed;
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
                "terrain complete and all 20 Forest Lantern positions rendering-ready",
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
                "server_forest_lantern_states_exact",
                setup != null && setup.allStatesPlaced(),
                expectedStateDescriptions().toString(),
                setup == null ? "missing setup" : setup.placedStates().toString()
        );
        passed &= addAssertion(
                assertions,
                "client_forest_lantern_states_exact",
                clientMirrorReady,
                "all 20 exact age/facing states mirrored",
                clientMirrorReady ? "mirrored" : "not mirrored"
        );
        passed &= addAssertion(
                assertions,
                "server_forest_lantern_state_network_ids_exact",
                setup != null && setup.allStateNetworkIdsPresent(),
                "20 placed states with non-negative raw ids",
                setup == null ? "missing setup" : setup.stateNetworkIds().toString()
        );
        passed &= addAssertion(
                assertions,
                "forest_lantern_shears_speed_exact",
                setup != null && setup.allShearsSpeedsExact(),
                "15.0 for all 20 states",
                setup == null ? "missing setup" : setup.shearsSpeeds().toString()
        );
        passed &= addAssertion(
                assertions,
                "forest_lantern_immature_loot_empty",
                setup != null && setup.immatureLootEmpty(),
                "ages 0..3=[]",
                setup == null ? "missing setup" : setup.lootByAge().subList(0, 4).toString()
        );
        passed &= addAssertion(
                assertions,
                "forest_lantern_mature_loot_exact",
                setup != null && setup.matureLootExact(),
                "age 4=[etherology:forest_lanternx1]",
                setup == null ? "missing setup" : setup.lootByAge().get(MAX_AGE)
        );
        for (int index = 0; index < RECIPE_EXPECTATIONS.size(); index++) {
            RecipeExpectation expectation = RECIPE_EXPECTATIONS.get(index);
            String expectedRecipe = expectation.id() + "=" + expectation.typeId()
                    + "->" + expectation.resultId() + "x1";
            String actualRecipe = setup == null || setup.recipes().size() <= index
                    ? "missing"
                    : setup.recipes().get(index);
            passed &= addAssertion(
                    assertions,
                    "recipe:" + expectation.id(),
                    setup != null && setup.recipesExact()
                            && expectedRecipe.equals(actualRecipe),
                    expectedRecipe,
                    actualRecipe
            );
        }
        JumpProbeResult jump = setup == null ? null : setup.jumpProbe();
        passed &= addAssertion(
                assertions,
                "forest_lantern_jump_seed_exact",
                jump != null && jump.predictedRoll() <= JUMP_BREAK_CHANCE,
                "first vanilla world-random roll <= 0.4",
                jump == null
                        ? "missing setup"
                        : "seed=" + jump.seed() + ",roll=" + jump.predictedRoll()
        );
        passed &= addAssertion(
                assertions,
                "forest_lantern_jump_stepping_position_exact",
                jump != null && jump.steppingPositionExact(),
                "player stepping position contains mature Forest Lantern",
                jump == null ? "missing setup" : jump.steppingPosition()
        );
        passed &= addAssertion(
                assertions,
                "forest_lantern_jump_break_exact",
                jump != null && jump.removed(),
                "mature Forest Lantern removed by one seeded vanilla jump",
                jump != null && jump.removed() ? "removed" : "not removed"
        );
        passed &= addAssertion(
                assertions,
                "forest_lantern_jump_drop_exact",
                jump != null && jump.dropExact(),
                "[etherology:forest_lanternx1]",
                jump == null ? "missing setup" : jump.drops().toString()
        );

        String actualWorldIdentity = setup == null
                ? "missing setup"
                : setup.worldDisplayName() + ";" + setup.worldSeed() + ";"
                        + setup.dimensionId();
        String expectedWorldIdentity = WORLD_DISPLAY_NAME + ";" + WORLD_SEED + ";"
                + World.OVERWORLD.getValue();
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
        report.addProperty("schema", 2);
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

        JsonObject mechanics = new JsonObject();
        mechanics.addProperty("fixture_state_count", EXPECTED_STATE_COUNT);
        mechanics.addProperty("ages", "0,1,2,3,4");
        mechanics.addProperty("facings", "north,east,south,west");
        mechanics.addProperty("jump_probe", "seeded vanilla PlayerEntity.jump invoker");
        mechanics.add("limitations", new JsonArray());
        report.add("mechanics", mechanics);

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
            screenshotNode.addProperty("step", "forest-lantern-age-facing-gallery");
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

    private static List<StateExpectation> createStateExpectations() {
        List<StateExpectation> expectations = new ArrayList<>();
        int[] xCoordinates = {-12, -4, 4, 12};
        for (int facingIndex = 0; facingIndex < FACINGS.size(); facingIndex++) {
            Direction facing = FACINGS.get(facingIndex);
            for (int age = 0; age <= MAX_AGE; age++) {
                expectations.add(new StateExpectation(
                        age,
                        facing,
                        new BlockPos(xCoordinates[facingIndex], FIXTURE_Y, age * 3)
                ));
            }
        }
        return List.copyOf(expectations);
    }

    private static List<String> expectedStateDescriptions() {
        List<String> descriptions = new ArrayList<>();
        for (StateExpectation expectation : STATE_EXPECTATIONS) {
            descriptions.add(
                    expectation.position().toShortString() + "=age=" + expectation.age()
                            + ",facing=" + expectation.facing().asString()
            );
        }
        return List.copyOf(descriptions);
    }

    private static RecipeExpectation recipe(
            String path,
            String type,
            Identifier result
    ) {
        return new RecipeExpectation(etherologyId(path), Identifier.of(type), result);
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

    private record StateExpectation(int age, Direction facing, BlockPos position) {
    }

    private record RecipeExpectation(
            Identifier id,
            Identifier typeId,
            Identifier resultId
    ) {
    }

    private record RegistryInspection(
            boolean propertiesExact,
            boolean defaultStateExact,
            boolean stateCountExact,
            boolean networkIdsExact,
            Set<String> propertyNames,
            int stateCount,
            int rawIdCount
    ) {

        private boolean passed() {
            return propertiesExact && defaultStateExact && stateCountExact && networkIdsExact;
        }
    }

    private record JumpProbeResult(
            boolean steppingPositionExact,
            long seed,
            float predictedRoll,
            boolean removed,
            boolean dropExact,
            List<String> drops,
            String steppingPosition
    ) {
    }

    private record ServerSetupResult(
            boolean chunkLoaded,
            boolean playerCreative,
            boolean allStatesPlaced,
            boolean allStateNetworkIdsPresent,
            boolean allShearsSpeedsExact,
            boolean immatureLootEmpty,
            boolean matureLootExact,
            boolean recipesExact,
            List<String> placedStates,
            List<String> stateNetworkIds,
            List<String> shearsSpeeds,
            List<String> lootByAge,
            List<String> recipes,
            JumpProbeResult jumpProbe,
            String worldDisplayName,
            long worldSeed,
            String dimensionId
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

    private record ReportResult(JsonObject report, boolean passed) {
    }
}
