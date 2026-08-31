package dev.theplumteam.etherology.baseline.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.DataPackFailureScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.Window;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

final class AttrahiteBlockRegistryScenario implements ClientScenario {

    static final String SCENARIO_ID = "attrahite-block-registry";
    static final String SCREENSHOT_FILE_NAME = "attrahite-block-registry.png";
    static final String WORLD_DIRECTORY_NAME =
            "etherology-original-attrahite-block-registry-world";
    static final String WORLD_DISPLAY_NAME =
            "Etherology Original 0.1.7 Attrahite Blocks";
    static final long WORLD_SEED = 0x4554484154543031L;
    static final ScenarioDefinition DEFINITION = ScenarioDefinitions.ATTRAHITE_BLOCK_REGISTRY;

    private static final Logger LOGGER = LoggerFactory.getLogger(
            "EtherologyOriginalBaselineHarness"
    );
    private static final String REFERENCE_ID = "published-0.1.7";
    private static final String HARNESS_MOD_ID =
            "etherology_original_baseline_harness";
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int REQUIRED_COMPLETED_RENDERS = 120;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final int FIXTURE_Y = ARENA_FLOOR_Y + 1;
    private static final BlockPos CAMERA_FLOOR_POS = new BlockPos(0, 127, -16);
    private static final BlockPos LOOT_PROBE_POS = new BlockPos(14, FIXTURE_Y, -8);
    private static final double CAMERA_X = CAMERA_FLOOR_POS.getX() + 0.5;
    private static final double CAMERA_Y = CAMERA_FLOOR_POS.getY() + 1.0;
    private static final double CAMERA_Z = CAMERA_FLOOR_POS.getZ() + 0.5;
    private static final float CAMERA_YAW = 0.0f;
    private static final float CAMERA_PITCH = 23.0f;
    private static final double CAMERA_POSE_TOLERANCE = 0.0001;
    private static final float BASE_DROP_CHANCE = 0.05f;
    private static final float FORTUNE_MULTIPLIER = 0.05f;
    private static final int FORTUNE_LEVEL = 3;
    private static final float FORTUNE_THREE_DROP_CHANCE =
            BASE_DROP_CHANCE + FORTUNE_LEVEL * FORTUNE_MULTIPLIER;
    private static final long MAXIMUM_LOOT_SEED_SEARCH = 4097L;
    private static final Identifier ATTRAHITE_ID = etherologyId("attrahite");
    private static final Identifier ATTRAHITE_BRICKS_ID = etherologyId("attrahite_bricks");
    private static final Identifier ATTRAHITE_BRICK_SLAB_ID =
            etherologyId("attrahite_brick_slab");
    private static final Identifier ATTRAHITE_BRICK_STAIRS_ID =
            etherologyId("attrahite_brick_stairs");
    private static final Identifier ENRICHED_ATTRAHITE_ID =
            etherologyId("enriched_attrahite");
    private static final Identifier RAW_AZEL_ID = etherologyId("raw_azel");
    private static final Identifier ATTRAHITE_BRICK_ID = etherologyId("attrahite_brick");
    private static final Identifier AZEL_INGOT_ID = etherologyId("azel_ingot");
    private static final List<Identifier> REQUIRED_ITEM_IDS = List.of(
            ENRICHED_ATTRAHITE_ID,
            RAW_AZEL_ID,
            ATTRAHITE_BRICK_ID,
            AZEL_INGOT_ID
    );
    private static final List<AttrahiteBlockFixture> BLOCK_FIXTURES = List.of(
            fixture("attrahite", Block.class, -6, Map.of(), 1),
            fixture("attrahite_bricks", Block.class, -2, Map.of(), 1),
            fixture(
                    "attrahite_brick_slab",
                    SlabBlock.class,
                    2,
                    Map.of("type", "bottom", "waterlogged", "false"),
                    6
            ),
            fixture(
                    "attrahite_brick_stairs",
                    StairsBlock.class,
                    6,
                    Map.of(
                            "facing", "north",
                            "half", "bottom",
                            "shape", "straight",
                            "waterlogged", "false"
                    ),
                    80
            )
    );
    private static final List<BlockPos> RENDER_POSITIONS = BLOCK_FIXTURES.stream()
            .map(AttrahiteBlockFixture::position)
            .toList();
    private static final List<Identifier> REQUIRED_RESOURCES = List.of(
            Identifier.of("minecraft", "texts/splashes.txt"),
            etherologyId("blockstates/attrahite.json"),
            etherologyId("blockstates/attrahite_bricks.json"),
            etherologyId("blockstates/attrahite_brick_slab.json"),
            etherologyId("blockstates/attrahite_brick_stairs.json"),
            etherologyId("models/block/attrahite.json"),
            etherologyId("models/block/attrahite_bricks.json"),
            etherologyId("models/block/attrahite_brick_slab.json"),
            etherologyId("models/block/attrahite_brick_slab_top.json"),
            etherologyId("models/block/attrahite_brick_stairs.json"),
            etherologyId("models/block/attrahite_brick_stairs_inner.json"),
            etherologyId("models/block/attrahite_brick_stairs_outer.json"),
            etherologyId("models/item/attrahite.json"),
            etherologyId("models/item/attrahite_bricks.json"),
            etherologyId("models/item/attrahite_brick_slab.json"),
            etherologyId("models/item/attrahite_brick_stairs.json"),
            etherologyId("textures/block/attrahite.png"),
            etherologyId("textures/block/attrahite_bricks.png")
    );
    private static final List<AttrahiteRecipeExpectation> RECIPE_EXPECTATIONS = List.of(
            recipe("attrahite_brick", "minecraft:smelting", ATTRAHITE_BRICK_ID, 1),
            recipe("attrahite_bricks", "minecraft:crafting", ATTRAHITE_BRICKS_ID, 1),
            recipe(
                    "attrahite_brick_slab",
                    "minecraft:crafting",
                    ATTRAHITE_BRICK_SLAB_ID,
                    6
            ),
            recipe(
                    "attrahite_brick_slab_from_attrahite_bricks_stonecutting",
                    "minecraft:stonecutting",
                    ATTRAHITE_BRICK_SLAB_ID,
                    2
            ),
            recipe(
                    "attrahite_brick_stairs",
                    "minecraft:crafting",
                    ATTRAHITE_BRICK_STAIRS_ID,
                    4
            ),
            recipe(
                    "attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
                    "minecraft:stonecutting",
                    ATTRAHITE_BRICK_STAIRS_ID,
                    1
            ),
            recipe("raw_azel", "minecraft:crafting", RAW_AZEL_ID, 1),
            recipe("azel_ingot", "minecraft:smelting", AZEL_INGOT_ID, 1),
            recipe(
                    "azel_ingot_from_blasting",
                    "minecraft:blasting",
                    AZEL_INGOT_ID,
                    1
            )
    );

    private final StableRenderCounter stableWorldRenders =
            new StableRenderCounter(REQUIRED_COMPLETED_RENDERS);
    private AttrahiteScenarioStage stage = AttrahiteScenarioStage.WAITING_FOR_TITLE;
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
    private List<Identifier> missingResources = List.of();
    private EvidenceLayout evidenceLayout;
    private AttrahiteRegistrySnapshot registrySnapshot;
    private volatile AttrahiteScreenshotResult screenshotResult;
    private volatile AttrahiteServerSetupResult serverSetupResult;
    private volatile String serverSetupFailure = "";
    private volatile Boolean saveResult;
    private volatile String saveFailure = "";

    @Override
    public void onEndClientTick(MinecraftClient client) {
        if (stage == AttrahiteScenarioStage.COMPLETE) return;

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
            LOGGER.error("Original attrahite-block-registry failed in {}", stage, exception);
            fail(
                    client,
                    stage + " raised " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()
            );
            return;
        }

        if (stage != AttrahiteScenarioStage.COMPLETE
                && stageClientTicks >= MAXIMUM_STAGE_CLIENT_TICKS) {
            fail(
                    client,
                    "Timed out in " + stage + " after " + stageClientTicks
                            + " client ticks"
            );
        }
    }

    @Override
    public void onGameRenderCompleted() {
        if (stage != AttrahiteScenarioStage.WAITING_FOR_WORLD_RENDERS) return;

        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (!stableWorldRenders.observe(isCaptureStateExact(client))) return;
            captureWorld(client);
        } catch (RuntimeException exception) {
            stableWorldRenders.observe(false);
            LOGGER.error("Original attrahite-block-registry render callback failed", exception);
            fail(
                    client,
                    "Render callback raised " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()
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
            stage = AttrahiteScenarioStage.COMPLETE;
            client.scheduleStop();
            return;
        }
        if (evidenceLayout.framebufferWidth() != FRAMEBUFFER_WIDTH
                || evidenceLayout.framebufferHeight() != FRAMEBUFFER_HEIGHT) {
            fail(client, "Attrahite evidence capture is not exactly 1920x1080");
            return;
        }
        if (!requestExpectedFramebuffer(client)) return;

        missingResources = findMissingResources(client);
        resourcesReady = missingResources.isEmpty();
        if (!resourcesReady) {
            fail(client, "The loaded resources lack exact Attrahite client assets: "
                    + missingResources);
            return;
        }
        if (!requiredRegistryIdsPresent()) {
            fail(client, "The registries lack a required Attrahite fixture id");
            return;
        }

        registrySnapshot = inspectRegistry();
        if (!registrySnapshot.passed()) {
            fail(client, "Attrahite registry contract failed: " + registrySnapshot);
            return;
        }
        registryPreflightPassed = true;
        transition(AttrahiteScenarioStage.STARTING_WORLD);
    }

    private void startWorld(MinecraftClient client) {
        Path saveDirectory = saveDirectory(client);
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
        transition(AttrahiteScenarioStage.WAITING_FOR_WORLD);
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
        transition(AttrahiteScenarioStage.WAITING_FOR_SERVER_SETUP);
    }

    private void tickWaitingForServerSetup(MinecraftClient client) {
        if (!serverSetupFailure.isEmpty()) {
            fail(client, "Server fixture setup failed: " + serverSetupFailure);
            return;
        }
        if (serverSetupResult == null) return;
        transition(AttrahiteScenarioStage.WAITING_FOR_CLIENT_MIRROR);
    }

    private void tickWaitingForClientMirror(MinecraftClient client) {
        clientMirrorReady = hasClientMirror(client);
        if (!clientMirrorReady) return;
        transition(AttrahiteScenarioStage.WAITING_FOR_WORLD_RENDERS);
    }

    private void tickWaitingForWorldRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before capture");
        }
    }

    private void tickCapturingWorld(MinecraftClient client) {
        AttrahiteScreenshotResult result = screenshotResult;
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
        if (stage != AttrahiteScenarioStage.WAITING_FOR_WORLD_RENDERS) return;
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
        transition(AttrahiteScenarioStage.CAPTURING_WORLD);
        ScreenshotRecorder.saveScreenshot(
                layout.scenarioRoot().toFile(),
                SCREENSHOT_FILE_NAME,
                client.getFramebuffer(),
                message -> screenshotResult = inspectScreenshot(layout.screenshotPath())
        );
    }

    private AttrahiteScreenshotResult inspectScreenshot(Path path) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return AttrahiteScreenshotResult.failed(
                        "Minecraft did not write one regular PNG"
                );
            }
            long size = Files.size(path);
            if (size <= 0L) {
                return AttrahiteScreenshotResult.failed("Minecraft wrote an empty PNG");
            }
            return new AttrahiteScreenshotResult(
                    true,
                    size,
                    ArtifactDigest.sha256(path),
                    ""
            );
        } catch (IOException exception) {
            return AttrahiteScreenshotResult.failed(exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot inspect the original Attrahite screenshot", exception);
            return AttrahiteScreenshotResult.failed(
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

            List<String> placedStates = new ArrayList<>();
            List<String> placedStateNetworkIds = new ArrayList<>();
            boolean blocksPlaced = true;
            boolean placedStateNetworkIdsExact = true;
            for (AttrahiteBlockFixture fixture : BLOCK_FIXTURES) {
                Block block = Registries.BLOCK.get(fixture.id());
                BlockState expectedState = block.getDefaultState();
                world.setBlockState(fixture.position(), expectedState, 3);
                BlockState actualState = world.getBlockState(fixture.position());
                String description = stateDescription(fixture.id(), actualState);
                placedStates.add(description);
                blocksPlaced &= actualState.equals(expectedState);
                int rawId = Block.STATE_IDS.getRawId(actualState);
                placedStateNetworkIds.add(description + "#" + rawId);
                placedStateNetworkIdsExact &= rawId >= 0;
            }

            AttrahiteTagSnapshot tags = inspectTags();
            List<String> recipes = inspectRecipes(server);
            boolean recipesExact = recipes.size() == RECIPE_EXPECTATIONS.size();
            AttrahiteLootProbeResult loot = runLootProbe(world, player);

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

            serverSetupResult = new AttrahiteServerSetupResult(
                    chunkLoaded,
                    playerCreative,
                    blocksPlaced,
                    placedStateNetworkIdsExact,
                    recipesExact,
                    List.copyOf(placedStates),
                    List.copyOf(placedStateNetworkIds),
                    List.copyOf(recipes),
                    tags,
                    loot,
                    server.getSaveProperties().getLevelName(),
                    world.getSeed(),
                    world.getRegistryKey().getValue().toString()
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot arrange the original Attrahite fixture", exception);
            serverSetupFailure = exception.getClass().getSimpleName()
                    + ": " + exception.getMessage();
        }
    }

    private AttrahiteLootProbeResult runLootProbe(
            ServerWorld world,
            ServerPlayerEntity player
    ) {
        Registry<Enchantment> enchantments = world.getRegistryManager().get(
                RegistryKeys.ENCHANTMENT
        );
        ItemStack silkTouchTool = new ItemStack(Items.DIAMOND_PICKAXE);
        silkTouchTool.addEnchantment(enchantments.entryOf(Enchantments.SILK_TOUCH), 1);
        ItemStack plainTool = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack fortuneThreeTool = new ItemStack(Items.DIAMOND_PICKAXE);
        fortuneThreeTool.addEnchantment(
                enchantments.entryOf(Enchantments.FORTUNE),
                FORTUNE_LEVEL
        );

        long sharedSeed = findFortuneDifferenceSeed();
        float sharedRoll = Random.create(sharedSeed).nextFloat();
        Block attrahite = Registries.BLOCK.get(ATTRAHITE_ID);
        List<ItemStack> silkTouchDrops = dropStacks(
                world,
                player,
                attrahite.getDefaultState(),
                silkTouchTool,
                sharedSeed
        );
        List<ItemStack> plainToolDrops = dropStacks(
                world,
                player,
                attrahite.getDefaultState(),
                plainTool,
                sharedSeed
        );
        List<ItemStack> fortuneThreeDrops = dropStacks(
                world,
                player,
                attrahite.getDefaultState(),
                fortuneThreeTool,
                sharedSeed
        );

        Item attrahiteItem = Registries.ITEM.get(ATTRAHITE_ID);
        Item enrichedAttrahite = Registries.ITEM.get(ENRICHED_ATTRAHITE_ID);
        boolean silkTouchExact = isOne(silkTouchDrops, attrahiteItem, 1);
        boolean plainToolEmpty = plainToolDrops.isEmpty();
        boolean fortuneThreeExact = isOne(fortuneThreeDrops, enrichedAttrahite, 1);

        Map<Identifier, List<String>> dropsByBlock = new LinkedHashMap<>();
        for (AttrahiteBlockFixture fixture : BLOCK_FIXTURES) {
            if (fixture.id().equals(ATTRAHITE_ID)) continue;
            Block block = Registries.BLOCK.get(fixture.id());
            List<ItemStack> drops = dropStacks(
                    world,
                    player,
                    block.getDefaultState(),
                    plainTool,
                    sharedSeed
            );
            dropsByBlock.put(fixture.id(), itemStackDescriptions(drops));
        }
        world.setBlockState(LOOT_PROBE_POS, Blocks.AIR.getDefaultState(), 3);

        return new AttrahiteLootProbeResult(
                sharedSeed,
                sharedRoll,
                silkTouchExact,
                plainToolEmpty,
                fortuneThreeExact,
                Map.copyOf(dropsByBlock),
                itemStackDescriptions(silkTouchDrops),
                itemStackDescriptions(plainToolDrops),
                itemStackDescriptions(fortuneThreeDrops)
        );
    }

    private List<ItemStack> dropStacks(
            ServerWorld world,
            ServerPlayerEntity player,
            BlockState state,
            ItemStack tool,
            long seed
    ) {
        world.setBlockState(LOOT_PROBE_POS, state, 3);
        world.getRandom().setSeed(seed);
        return Block.getDroppedStacks(
                state,
                world,
                LOOT_PROBE_POS,
                null,
                player,
                tool
        );
    }

    private long findFortuneDifferenceSeed() {
        for (long seed = 0L; seed < MAXIMUM_LOOT_SEED_SEARCH; seed++) {
            float roll = Random.create(seed).nextFloat();
            if (roll >= BASE_DROP_CHANCE && roll < FORTUNE_THREE_DROP_CHANCE) {
                return seed;
            }
        }
        throw new IllegalStateException("No deterministic Attrahite Fortune seed found");
    }

    private List<String> inspectRecipes(IntegratedServer server) {
        List<String> recipes = new ArrayList<>();
        for (AttrahiteRecipeExpectation expectation : RECIPE_EXPECTATIONS) {
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
                    || result.getCount() != expectation.resultCount()) {
                throw new IllegalStateException("Recipe contract changed: " + description);
            }
            recipes.add(description);
        }
        return recipes;
    }

    private AttrahiteTagSnapshot inspectTags() {
        List<String> descriptions = new ArrayList<>();
        boolean blockTagsExact = true;
        boolean itemTagsExact = true;
        for (AttrahiteBlockFixture fixture : BLOCK_FIXTURES) {
            Block block = Registries.BLOCK.get(fixture.id());
            BlockState state = block.getDefaultState();
            boolean pickaxe = state.isIn(BlockTags.PICKAXE_MINEABLE);
            boolean needsStone = state.isIn(BlockTags.NEEDS_STONE_TOOL);
            boolean blockSlab = state.isIn(BlockTags.SLABS);
            boolean blockStairs = state.isIn(BlockTags.STAIRS);
            boolean itemSlab = block.asItem().getDefaultStack().isIn(ItemTags.SLABS);
            boolean itemStairs = block.asItem().getDefaultStack().isIn(ItemTags.STAIRS);
            descriptions.add(
                    fixture.id() + "=pickaxe:" + pickaxe
                            + ",needs_stone:" + needsStone
                            + ",block_slab:" + blockSlab
                            + ",block_stairs:" + blockStairs
                            + ",item_slab:" + itemSlab
                            + ",item_stairs:" + itemStairs
            );

            blockTagsExact &= pickaxe;
            blockTagsExact &= needsStone == fixture.id().equals(ATTRAHITE_ID);
            blockTagsExact &= blockSlab == fixture.id().equals(ATTRAHITE_BRICK_SLAB_ID);
            blockTagsExact &= blockStairs == fixture.id().equals(ATTRAHITE_BRICK_STAIRS_ID);
            itemTagsExact &= itemSlab == fixture.id().equals(ATTRAHITE_BRICK_SLAB_ID);
            itemTagsExact &= itemStairs == fixture.id().equals(ATTRAHITE_BRICK_STAIRS_ID);
        }
        return new AttrahiteTagSnapshot(
                blockTagsExact,
                itemTagsExact,
                List.copyOf(descriptions)
        );
    }

    private void clearArena(ServerWorld world) {
        BlockPos start = new BlockPos(-17, ARENA_FLOOR_Y, -20);
        BlockPos end = new BlockPos(17, CAMERA_FLOOR_POS.getY() + 3, 14);
        for (BlockPos position : BlockPos.iterate(start, end)) {
            world.setBlockState(position, Blocks.AIR.getDefaultState(), 3);
        }
    }

    private void buildArenaFloor(ServerWorld world) {
        for (int x = -17; x <= 17; x++) {
            for (int z = -20; z <= 14; z++) {
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
        List<Block> markers = List.of(
                Blocks.ORANGE_CONCRETE,
                Blocks.YELLOW_CONCRETE,
                Blocks.LIGHT_BLUE_CONCRETE,
                Blocks.PURPLE_CONCRETE
        );
        for (int index = 0; index < BLOCK_FIXTURES.size(); index++) {
            world.setBlockState(
                    BLOCK_FIXTURES.get(index).position().down(),
                    markers.get(index).getDefaultState(),
                    3
            );
        }
    }

    private boolean hasClientMirror(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) return false;
        if (!client.world.getChunkManager().isChunkLoaded(0, 0)) return false;
        if (!client.player.isCreative()) return false;
        if (client.world.getRegistryKey() != World.OVERWORLD) return false;

        for (AttrahiteBlockFixture fixture : BLOCK_FIXTURES) {
            BlockState expectedState = Registries.BLOCK.get(fixture.id()).getDefaultState();
            if (!client.world.getBlockState(fixture.position()).equals(expectedState)) {
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
                && client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT
                && window.getFramebufferWidth() == FRAMEBUFFER_WIDTH
                && window.getFramebufferHeight() == FRAMEBUFFER_HEIGHT;
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
        if (client.getFramebuffer().textureWidth == FRAMEBUFFER_WIDTH
                && client.getFramebuffer().textureHeight == FRAMEBUFFER_HEIGHT
                && window.getFramebufferWidth() == FRAMEBUFFER_WIDTH
                && window.getFramebufferHeight() == FRAMEBUFFER_HEIGHT) {
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

    private boolean requiredRegistryIdsPresent() {
        for (AttrahiteBlockFixture fixture : BLOCK_FIXTURES) {
            if (!Registries.BLOCK.containsId(fixture.id())
                    || !Registries.ITEM.containsId(fixture.id())) {
                return false;
            }
        }
        for (Identifier itemId : REQUIRED_ITEM_IDS) {
            if (!Registries.ITEM.containsId(itemId)) return false;
        }
        return true;
    }

    private AttrahiteRegistrySnapshot inspectRegistry() {
        boolean classesExact = true;
        boolean blockItemsExact = true;
        boolean defaultStatesExact = true;
        boolean stateCountsExact = true;
        boolean networkIdsExact = true;
        Set<Integer> allRawIds = new HashSet<>();
        int expectedRawIdCount = 0;
        List<String> descriptions = new ArrayList<>();

        for (AttrahiteBlockFixture fixture : BLOCK_FIXTURES) {
            Block block = Registries.BLOCK.get(fixture.id());
            Item item = Registries.ITEM.get(fixture.id());
            Map<String, String> actualProperties = defaultProperties(block.getDefaultState());
            List<BlockState> states = block.getStateManager().getStates();
            Set<Integer> blockRawIds = new HashSet<>();
            boolean blockNetworkIdsExact = true;
            for (BlockState state : states) {
                int rawId = Block.STATE_IDS.getRawId(state);
                blockNetworkIdsExact &= rawId >= 0;
                blockRawIds.add(rawId);
                allRawIds.add(rawId);
            }

            boolean classExact = block.getClass().equals(fixture.blockClass());
            boolean blockItemExact = item.getClass().equals(BlockItem.class)
                    && ((BlockItem) item).getBlock() == block;
            boolean defaultStateExact = actualProperties.equals(
                    fixture.defaultProperties()
            );
            boolean stateCountExact = states.size() == fixture.stateCount();
            boolean rawIdsExact = blockNetworkIdsExact
                    && blockRawIds.size() == fixture.stateCount();
            classesExact &= classExact;
            blockItemsExact &= blockItemExact;
            defaultStatesExact &= defaultStateExact;
            stateCountsExact &= stateCountExact;
            networkIdsExact &= rawIdsExact;
            expectedRawIdCount += fixture.stateCount();
            descriptions.add(
                    fixture.id() + "=block_class:" + block.getClass().getName()
                            + ",item_class:" + item.getClass().getName()
                            + ",default:" + actualProperties
                            + ",states:" + states.size()
                            + ",raw_ids:" + blockRawIds.size()
            );
        }
        networkIdsExact &= allRawIds.size() == expectedRawIdCount;
        return new AttrahiteRegistrySnapshot(
                classesExact,
                blockItemsExact,
                defaultStatesExact,
                stateCountsExact,
                networkIdsExact,
                List.copyOf(descriptions)
        );
    }

    private List<Identifier> findMissingResources(MinecraftClient client) {
        List<Identifier> missing = new ArrayList<>();
        for (Identifier identifier : REQUIRED_RESOURCES) {
            if (client.getResourceManager().getResource(identifier).isEmpty()) {
                missing.add(identifier);
            }
        }
        return List.copyOf(missing);
    }

    private static Map<String, String> defaultProperties(BlockState state) {
        Map<String, String> properties = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), propertyValueName(state, property));
        }
        return Map.copyOf(properties);
    }

    private static <T extends Comparable<T>> String propertyValueName(
            BlockState state,
            Property<T> property
    ) {
        return property.name(state.get(property));
    }

    private static String stateDescription(Identifier id, BlockState state) {
        return id + "=" + defaultProperties(state);
    }

    private static List<String> itemStackDescriptions(List<ItemStack> stacks) {
        List<String> descriptions = new ArrayList<>();
        for (ItemStack stack : stacks) {
            descriptions.add(Registries.ITEM.getId(stack.getItem()) + "x" + stack.getCount());
        }
        return List.copyOf(descriptions);
    }

    private static boolean isOne(List<ItemStack> stacks, Item item, int count) {
        return stacks.size() == 1
                && stacks.getFirst().isOf(item)
                && stacks.getFirst().getCount() == count;
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
                LOGGER.error("Cannot force-save the original Attrahite world", exception);
                saveFailure = exception.getClass().getSimpleName()
                        + ": " + exception.getMessage();
            }
        });
        transition(AttrahiteScenarioStage.SAVING_WORLD);
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == AttrahiteScenarioStage.COMPLETE) return;
        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Original Attrahite lifecycle failure: {}", lifecycleFailure);
        publish(client);
    }

    private void publish(MinecraftClient client) {
        if (stage == AttrahiteScenarioStage.COMPLETE) return;

        try {
            ensureEvidenceLayout(client);
            List<ArtifactDigest> artifactDigests = List.of(
                    ArtifactDigest.capture("etherology"),
                    ArtifactDigest.capture(HARNESS_MOD_ID)
            );
            AttrahiteReportResult reportResult = createReport(client, artifactDigests);
            AtomicEvidenceWriter.writeReportThenMarker(
                    evidenceLayout,
                    reportResult.report(),
                    reportResult.passed()
            );
            LOGGER.info(
                    "Original Attrahite evidence published with status {}: {}",
                    reportResult.passed() ? "passed" : "failed",
                    evidenceLayout.reportsDirectory()
            );
        } catch (IOException exception) {
            LOGGER.error("Cannot atomically publish original Attrahite evidence", exception);
        } finally {
            stage = AttrahiteScenarioStage.COMPLETE;
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

    private AttrahiteReportResult createReport(
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
                "attrahite_canonical_resources_exact",
                resourcesReady,
                REQUIRED_RESOURCES.toString(),
                resourcesReady ? REQUIRED_RESOURCES.toString() : missingResources.toString()
        );
        for (AttrahiteBlockFixture fixture : BLOCK_FIXTURES) {
            boolean blockPresent = Registries.BLOCK.containsId(fixture.id());
            passed &= addAssertion(
                    assertions,
                    "registry:block:" + fixture.id(),
                    blockPresent,
                    "present",
                    blockPresent ? "present" : "missing"
            );
            boolean itemPresent = Registries.ITEM.containsId(fixture.id());
            passed &= addAssertion(
                    assertions,
                    "registry:item:" + fixture.id(),
                    itemPresent,
                    "present",
                    itemPresent ? "present" : "missing"
            );
        }

        AttrahiteRegistrySnapshot registry = registrySnapshot;
        String registryDescription = registry == null
                ? "missing preflight"
                : registry.descriptions().toString();
        passed &= addAssertion(
                assertions,
                "attrahite_block_classes_exact",
                registry != null && registry.classesExact(),
                "[Block, Block, SlabBlock, StairsBlock]",
                registryDescription
        );
        passed &= addAssertion(
                assertions,
                "attrahite_block_items_exact",
                registry != null && registry.blockItemsExact(),
                "four exact BlockItem instances bound to their registered blocks",
                registryDescription
        );
        passed &= addAssertion(
                assertions,
                "attrahite_default_states_exact",
                registry != null && registry.defaultStatesExact(),
                "raw/bricks={}, slab={type=bottom,waterlogged=false}, "
                        + "stairs={facing=north,half=bottom,shape=straight,waterlogged=false}",
                registryDescription
        );
        passed &= addAssertion(
                assertions,
                "attrahite_state_counts_exact",
                registry != null && registry.stateCountsExact(),
                "[1, 1, 6, 80]",
                registryDescription
        );
        passed &= addAssertion(
                assertions,
                "attrahite_state_network_ids_exact",
                registry != null && registry.networkIdsExact(),
                "88 unique non-negative raw ids",
                registryDescription
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

        boolean dimensionsMatch = framebufferWidth == FRAMEBUFFER_WIDTH
                && framebufferHeight == FRAMEBUFFER_HEIGHT;
        passed &= addAssertion(
                assertions,
                "native_framebuffer_dimensions",
                dimensionsMatch,
                FRAMEBUFFER_WIDTH + "x" + FRAMEBUFFER_HEIGHT,
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
                "terrain complete and all four Attrahite positions rendering-ready",
                captureRenderReady ? "ready" : "not latched"
        );
        passed &= addAssertion(
                assertions,
                "capture_camera_exact",
                captureCameraExact,
                expectedCameraPoseDescription(),
                cameraPoseDescription(client)
        );
        AttrahiteScreenshotResult screenshot = screenshotResult;
        passed &= addAssertion(
                assertions,
                "native_screenshot_written",
                screenshot != null && screenshot.passed(),
                "one non-empty unedited 1920x1080 framebuffer PNG",
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

        AttrahiteServerSetupResult setup = serverSetupResult;
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
                "server_attrahite_default_states_exact",
                setup != null && setup.blocksPlaced(),
                expectedPlacedStates().toString(),
                setup == null ? "missing setup" : setup.placedStates().toString()
        );
        passed &= addAssertion(
                assertions,
                "client_attrahite_default_states_exact",
                clientMirrorReady,
                "all four exact default states mirrored",
                clientMirrorReady ? "mirrored" : "not mirrored"
        );
        passed &= addAssertion(
                assertions,
                "server_attrahite_state_network_ids_exact",
                setup != null && setup.placedStateNetworkIdsExact(),
                "four placed states with non-negative raw ids",
                setup == null ? "missing setup" : setup.placedStateNetworkIds().toString()
        );

        AttrahiteTagSnapshot tags = setup == null ? null : setup.tags();
        passed &= addAssertion(
                assertions,
                "attrahite_block_tags_exact",
                tags != null && tags.blockTagsExact(),
                "pickaxe=all four; needs_stone=raw; slabs=slab; stairs=stairs",
                tags == null ? "missing setup" : tags.descriptions().toString()
        );
        passed &= addAssertion(
                assertions,
                "attrahite_item_tags_exact",
                tags != null && tags.itemTagsExact(),
                "slabs=slab item; stairs=stairs item",
                tags == null ? "missing setup" : tags.descriptions().toString()
        );

        for (int index = 0; index < RECIPE_EXPECTATIONS.size(); index++) {
            AttrahiteRecipeExpectation expectation = RECIPE_EXPECTATIONS.get(index);
            String expectedRecipe = recipeDescription(expectation);
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

        AttrahiteLootProbeResult loot = setup == null ? null : setup.loot();
        boolean sharedRollExact = loot != null
                && loot.sharedRoll() >= BASE_DROP_CHANCE
                && loot.sharedRoll() < FORTUNE_THREE_DROP_CHANCE;
        passed &= addAssertion(
                assertions,
                "attrahite_loot_shared_seed_roll_exact",
                sharedRollExact,
                "0.05 <= first roll < 0.20",
                loot == null
                        ? "missing setup"
                        : "seed=" + loot.sharedSeed() + ",roll=" + loot.sharedRoll()
        );
        passed &= addAssertion(
                assertions,
                "loot:etherology:attrahite:silk_touch",
                loot != null && loot.silkTouchExact(),
                "[etherology:attrahitex1]",
                loot == null ? "missing setup" : loot.silkTouchDrops().toString()
        );
        passed &= addAssertion(
                assertions,
                "loot:etherology:attrahite:no_silk_no_fortune",
                loot != null && loot.plainToolEmpty(),
                "[]",
                loot == null ? "missing setup" : loot.plainToolDrops().toString()
        );
        passed &= addAssertion(
                assertions,
                "loot:etherology:attrahite:fortune_iii",
                loot != null && loot.fortuneThreeExact(),
                "[etherology:enriched_attrahitex1]",
                loot == null ? "missing setup" : loot.fortuneThreeDrops().toString()
        );
        for (AttrahiteBlockFixture fixture : BLOCK_FIXTURES) {
            if (fixture.id().equals(ATTRAHITE_ID)) continue;
            List<String> expectedDrops = List.of(fixture.id() + "x1");
            List<String> actualDrops = loot == null
                    ? List.of()
                    : loot.dropsByBlock().getOrDefault(fixture.id(), List.of());
            passed &= addAssertion(
                    assertions,
                    "loot:" + fixture.id(),
                    expectedDrops.equals(actualDrops),
                    expectedDrops.toString(),
                    actualDrops.toString()
            );
        }

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

        Path saveDirectory = saveDirectory(client);
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
        mechanics.addProperty("gallery_block_count", BLOCK_FIXTURES.size());
        mechanics.addProperty("recipe_count", RECIPE_EXPECTATIONS.size());
        mechanics.addProperty("loot_table_count", BLOCK_FIXTURES.size());
        mechanics.addProperty("fortune_level", FORTUNE_LEVEL);
        mechanics.addProperty("base_drop_chance", BASE_DROP_CHANCE);
        mechanics.addProperty("fortune_multiplier", FORTUNE_MULTIPLIER);
        mechanics.addProperty(
                "loot_probe",
                "same seeded first roll for plain and Fortune III diamond pickaxes"
        );
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
            screenshotNode.addProperty("step", "attrahite-four-block-gallery");
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
        return new AttrahiteReportResult(report, passed);
    }

    private void transition(AttrahiteScenarioStage nextStage) {
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

    private static List<String> expectedPlacedStates() {
        List<String> descriptions = new ArrayList<>();
        for (AttrahiteBlockFixture fixture : BLOCK_FIXTURES) {
            descriptions.add(fixture.id() + "=" + fixture.defaultProperties());
        }
        return List.copyOf(descriptions);
    }

    private static String recipeDescription(AttrahiteRecipeExpectation expectation) {
        return expectation.id() + "=" + expectation.typeId() + "->"
                + expectation.resultId() + "x" + expectation.resultCount();
    }

    private static AttrahiteBlockFixture fixture(
            String path,
            Class<? extends Block> blockClass,
            int x,
            Map<String, String> defaultProperties,
            int stateCount
    ) {
        return new AttrahiteBlockFixture(
                etherologyId(path),
                blockClass,
                new BlockPos(x, FIXTURE_Y, 0),
                Map.copyOf(defaultProperties),
                stateCount
        );
    }

    private static AttrahiteRecipeExpectation recipe(
            String path,
            String type,
            Identifier result,
            int resultCount
    ) {
        return new AttrahiteRecipeExpectation(
                etherologyId(path),
                Identifier.of(type),
                result,
                resultCount
        );
    }

    private static Identifier etherologyId(String path) {
        return Identifier.of("etherology", path);
    }

    private static Path saveDirectory(MinecraftClient client) {
        return client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
    }
}
