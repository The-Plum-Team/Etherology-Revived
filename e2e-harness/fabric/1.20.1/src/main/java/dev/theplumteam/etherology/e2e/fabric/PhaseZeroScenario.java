package dev.theplumteam.etherology.e2e.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DatapackFailureScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PhaseZeroScenario implements ClientScenario {

    private static final Logger LOGGER = LoggerFactory.getLogger("EtherologyE2EHarness");
    static final String SCENARIO_ID = "phase0-smoke";
    private static final String TITLE_SCREENSHOT_FILE_NAME = "phase0-smoke-title.png";
    private static final String WORLD_SCREENSHOT_FILE_NAME = "phase0-smoke-world.png";
    private static final String WORLD_DIRECTORY_NAME = "etherology-e2e-phase0-world";
    private static final String WORLD_DISPLAY_NAME = "Etherology E2E Phase 0";
    private static final long WORLD_SEED = 0x45544845524f4cL;
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final int REQUIRED_COMPLETED_RENDERS = 2;
    private static final int MAXIMUM_STAGE_CLIENT_TICKS = 6000;
    private static final int ARENA_FLOOR_Y = 120;
    private static final int MINIMUM_CREATIVE_STACKS = 100;
    private static final BlockPos CAMERA_BLOCK_POS = new BlockPos(0, ARENA_FLOOR_Y + 1, -8);
    private static final Identifier MINECRAFT_TITLE_RESOURCE =
            new Identifier("minecraft", "texts/splashes.txt");
    private static final Identifier ETHEROLOGY_TITLE_RESOURCE =
            new Identifier("etherology", "models/item/oculus.json");
    private static final Identifier ETHEROLOGY_ITEM_GROUP = etherologyId("etherology_items");
    private static final List<PlacedBlockExpectation> PLACED_BLOCKS = List.of(
            placedBlock("brewing_cauldron", -3, 2),
            placedBlock("empowerment_table", 0, 2),
            placedBlock("ethereal_storage", 3, 2),
            placedBlock("armillary_sphere", 0, 5)
    );
    private static final List<Identifier> EXPECTED_CREATIVE_ITEMS = List.of(
            etherologyId("teldecore"),
            etherologyId("oculus"),
            etherologyId("staff"),
            etherologyId("brewing_cauldron"),
            etherologyId("ethereal_storage"),
            etherologyId("armillary_sphere")
    );
    private static final RegistryExpectation[] REGISTRY_EXPECTATIONS = {
            item("teldecore"),
            item("oculus"),
            item("staff"),
            item("primoshard_keta"),
            item("redstone_lens"),
            block("brewing_cauldron"),
            block("empowerment_table"),
            block("armillary_sphere"),
            block("ethereal_storage"),
            block("levitator"),
            new RegistryExpectation("item_group", Registries.ITEM_GROUP, ETHEROLOGY_ITEM_GROUP),
            new RegistryExpectation(
                    "block_entity_type",
                    Registries.BLOCK_ENTITY_TYPE,
                    etherologyId("brewing_cauldron_block_entity")
            ),
            new RegistryExpectation(
                    "block_entity_type",
                    Registries.BLOCK_ENTITY_TYPE,
                    etherologyId("armillary_sphere_block_entity")
            ),
            new RegistryExpectation(
                    "screen_handler",
                    Registries.SCREEN_HANDLER,
                    etherologyId("empower_table_screen_handler")
            ),
            new RegistryExpectation(
                    "screen_handler",
                    Registries.SCREEN_HANDLER,
                    etherologyId("ethereal_storage_screen_handler")
            ),
            new RegistryExpectation(
                    "recipe_serializer",
                    Registries.RECIPE_SERIALIZER,
                    etherologyId("alchemy_recipe")
            ),
            new RegistryExpectation(
                    "recipe_serializer",
                    Registries.RECIPE_SERIALIZER,
                    etherologyId("empower_recipe")
            ),
            new RegistryExpectation(
                    "recipe_serializer",
                    Registries.RECIPE_SERIALIZER,
                    etherologyId("matrix_recipe")
            ),
            new RegistryExpectation("entity_type", Registries.ENTITY_TYPE, etherologyId("redstone_charge")),
            new RegistryExpectation("status_effect", Registries.STATUS_EFFECT, etherologyId("devastation")),
            new RegistryExpectation("status_effect", Registries.STATUS_EFFECT, etherologyId("vital_energy"))
    };

    private Stage stage = Stage.WAITING_FOR_TITLE;
    private int clientTicks;
    private int stageClientTicks;
    private int completedTitleRenders;
    private int completedWorldRenders;
    private int titleFramebufferWidth;
    private int titleFramebufferHeight;
    private int worldFramebufferWidth;
    private int worldFramebufferHeight;
    private boolean resourcesReady;
    private boolean clientMirrorReady;
    private boolean setupSubmitted;
    private boolean saveSubmitted;
    private String lifecycleFailure = "";
    private EvidenceLayout evidenceLayout;
    private volatile ScreenshotResult titleScreenshotResult;
    private volatile ScreenshotResult worldScreenshotResult;
    private volatile ServerSetupResult serverSetupResult;
    private volatile String serverSetupFailure = "";
    private volatile Boolean saveResult;
    private volatile String saveFailure = "";
    private CreativeGroupSnapshot creativeGroupSnapshot;
    private BlockStateRegistrySnapshot blockStateRegistrySnapshot;

    @Override
    public void onEndClientTick(MinecraftClient client) {
        if (stage == Stage.COMPLETE) return;

        clientTicks++;
        stageClientTicks++;
        try {
            switch (stage) {
                case WAITING_FOR_TITLE -> tickWaitingForTitle(client);
                case CAPTURING_TITLE -> tickCapturingTitle(client);
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
            LOGGER.error("Etherology phase0-smoke failed while in {}", stage, exception);
            fail(client, stage + " raised " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
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
        if (screen instanceof TitleScreen) {
            ScreenEvents.afterRender(screen).register(this::onTitleScreenRendered);
        }
    }

    @Override
    public void onGameRenderCompleted() {
        if (stage != Stage.WAITING_FOR_WORLD_RENDERS) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (!isWorldViewReady(client)) {
            completedWorldRenders = 0;
            return;
        }

        completedWorldRenders++;
        if (completedWorldRenders < REQUIRED_COMPLETED_RENDERS) return;

        captureWorld(client);
    }

    private void onTitleScreenRendered(
            Screen screen,
            DrawContext drawContext,
            int mouseX,
            int mouseY,
            float tickDelta
    ) {
        if (stage != Stage.WAITING_FOR_TITLE) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (!isReadyTitleScreen(client, screen)) {
            completedTitleRenders = 0;
            return;
        }

        completedTitleRenders++;
        if (completedTitleRenders < REQUIRED_COMPLETED_RENDERS) return;

        captureTitle(client);
    }

    private void tickWaitingForTitle(MinecraftClient client) {
        if (client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) {
            resourcesReady = false;
            completedTitleRenders = 0;
            return;
        }

        resourcesReady = hasRequiredResources(client);
        if (!resourcesReady) {
            fail(client, "The loaded title resources do not include Etherology");
            return;
        }

        if (blockStateRegistrySnapshot == null) {
            blockStateRegistrySnapshot = inspectEtherologyBlockStates();
        }
        if (!blockStateRegistrySnapshot.missingStates().isEmpty()) {
            fail(
                    client,
                    "Etherology has block states missing from Minecraft's network ID list: "
                            + blockStateRegistrySnapshot.missingStates()
            );
        }
    }

    private void tickCapturingTitle(MinecraftClient client) {
        ScreenshotResult result = titleScreenshotResult;
        if (result == null) return;
        if (!result.passed()) {
            fail(client, "The title screenshot failed: " + result.failure());
            return;
        }

        transition(Stage.STARTING_WORLD);
    }

    private void startWorld(MinecraftClient client) {
        Path saveDirectory = client.runDirectory.toPath()
                .resolve("saves")
                .resolve(WORLD_DIRECTORY_NAME);
        if (Files.exists(saveDirectory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(saveDirectory)) {
            fail(client, "Refusing to reuse the phase-zero save: " + saveDirectory);
            return;
        }
        if (client.getServer() != null || client.world != null || client.player != null) {
            fail(client, "The client already has a world before phase-zero creation");
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
        if (client.currentScreen instanceof DatapackFailureScreen) {
            fail(client, "Minecraft rejected Etherology's server data packs");
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

        ServerPlayerEntity serverPlayer = server.getPlayerManager().getPlayer(client.player.getUuid());
        if (serverPlayer == null) return;
        if (setupSubmitted) return;

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

        creativeGroupSnapshot = captureCreativeGroup(client);
        transition(Stage.WAITING_FOR_WORLD_RENDERS);
    }

    private void tickWaitingForWorldRenders(MinecraftClient client) {
        if (!isWorldLifecycleReady(client)) {
            fail(client, "The integrated world became unavailable before capture");
        }
    }

    private void tickCapturingWorld(MinecraftClient client) {
        ScreenshotResult result = worldScreenshotResult;
        if (result == null) return;
        if (!result.passed()) {
            fail(client, "The world screenshot failed: " + result.failure());
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

    private void captureTitle(MinecraftClient client) {
        if (stage != Stage.WAITING_FOR_TITLE) return;

        try {
            ensureEvidenceLayout(client);
        } catch (IOException exception) {
            LOGGER.error("Cannot use the isolated Etherology E2E evidence layout", exception);
            lifecycleFailure = exception.getMessage();
            stage = Stage.COMPLETE;
            client.scheduleStop();
            return;
        }

        titleFramebufferWidth = client.getFramebuffer().textureWidth;
        titleFramebufferHeight = client.getFramebuffer().textureHeight;
        transition(Stage.CAPTURING_TITLE);
        saveScreenshot(client, TITLE_SCREENSHOT_FILE_NAME, result -> titleScreenshotResult = result);
    }

    private void captureWorld(MinecraftClient client) {
        if (stage != Stage.WAITING_FOR_WORLD_RENDERS) return;

        worldFramebufferWidth = client.getFramebuffer().textureWidth;
        worldFramebufferHeight = client.getFramebuffer().textureHeight;
        transition(Stage.CAPTURING_WORLD);
        saveScreenshot(client, WORLD_SCREENSHOT_FILE_NAME, result -> worldScreenshotResult = result);
    }

    private void saveScreenshot(
            MinecraftClient client,
            String fileName,
            java.util.function.Consumer<ScreenshotResult> resultConsumer
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
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
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
            boolean allBlocksPlaced = true;
            boolean allBlockEntitiesPresent = true;
            for (PlacedBlockExpectation expectation : PLACED_BLOCKS) {
                Block block = Registries.BLOCK.get(expectation.id());
                world.setBlockState(expectation.pos(), block.getDefaultState(), 3);

                String actualBlockId = Registries.BLOCK.getId(world.getBlockState(expectation.pos()).getBlock())
                        .toString();
                placedBlockIds.add(expectation.pos().toShortString() + "=" + actualBlockId);
                allBlocksPlaced &= expectation.id().toString().equals(actualBlockId);
                allBlockEntitiesPresent &= world.getBlockEntity(expectation.pos()) != null;
            }

            PlacedBlockExpectation roundTripExpectation = PLACED_BLOCKS.get(1);
            BlockState roundTripState = world.getBlockState(roundTripExpectation.pos());
            BlockEntity originalBlockEntity = world.getBlockEntity(roundTripExpectation.pos());
            boolean blockEntityRoundTrip = false;
            String serializedBlockEntityId = "";
            if (originalBlockEntity != null) {
                NbtCompound nbt = originalBlockEntity.createNbtWithId();
                serializedBlockEntityId = nbt.getString("id");
                BlockEntity restoredBlockEntity = BlockEntity.createFromNbt(
                        roundTripExpectation.pos(),
                        roundTripState,
                        nbt
                );
                blockEntityRoundTrip = restoredBlockEntity != null
                        && restoredBlockEntity.getType() == originalBlockEntity.getType();
            }

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

            serverSetupResult = new ServerSetupResult(
                    chunkLoaded,
                    playerCreative,
                    allBlocksPlaced,
                    allBlockEntitiesPresent,
                    blockEntityRoundTrip,
                    serializedBlockEntityId,
                    List.copyOf(placedBlockIds)
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Cannot arrange the phase-zero integrated-server fixture", exception);
            serverSetupFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
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
                Block floorBlock = (x + z) % 7 == 0 ? Blocks.SEA_LANTERN : Blocks.SMOOTH_STONE;
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
            if (client.world.getBlockEntity(expectation.pos()) == null) return false;
        }
        return true;
    }

    private CreativeGroupSnapshot captureCreativeGroup(MinecraftClient client) {
        ItemGroup itemGroup = Registries.ITEM_GROUP.get(ETHEROLOGY_ITEM_GROUP);
        ItemGroup.DisplayContext displayContext = new ItemGroup.DisplayContext(
                client.world.getEnabledFeatures(),
                true,
                client.world.getRegistryManager()
        );
        itemGroup.updateEntries(displayContext);

        Set<String> itemIds = new LinkedHashSet<>();
        for (ItemStack stack : itemGroup.getDisplayStacks()) {
            itemIds.add(Registries.ITEM.getId(stack.getItem()).toString());
        }
        boolean expectedItemsPresent = EXPECTED_CREATIVE_ITEMS.stream()
                .map(Identifier::toString)
                .allMatch(itemIds::contains);
        return new CreativeGroupSnapshot(
                itemGroup.getDisplayStacks().size(),
                expectedItemsPresent,
                List.copyOf(itemIds)
        );
    }

    private boolean isReadyTitleScreen(MinecraftClient client, Screen screen) {
        return resourcesReady
                && client.getOverlay() == null
                && client.currentScreen == screen
                && screen instanceof TitleScreen
                && hasExpectedFramebuffer(client);
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
                && clientMirrorReady
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

    private boolean hasRequiredResources(MinecraftClient client) {
        return client.getResourceManager().getResource(MINECRAFT_TITLE_RESOURCE).isPresent()
                && client.getResourceManager().getResource(ETHEROLOGY_TITLE_RESOURCE).isPresent();
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
                LOGGER.error("Cannot force-save the phase-zero integrated world", exception);
                saveFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
        });
        transition(Stage.SAVING_WORLD);
    }

    private void fail(MinecraftClient client, String failure) {
        if (stage == Stage.COMPLETE) return;

        lifecycleFailure = failure == null ? "Unknown lifecycle failure" : failure;
        LOGGER.error("Etherology phase0-smoke lifecycle failure: {}", lifecycleFailure);
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
            LOGGER.info("Etherology phase0-smoke evidence is complete: {}", evidenceLayout.reportsDirectory());
        } catch (IOException exception) {
            LOGGER.error("Cannot atomically publish Etherology phase0-smoke evidence", exception);
        } finally {
            stage = Stage.COMPLETE;
            client.scheduleStop();
        }
    }

    private void ensureEvidenceLayout(MinecraftClient client) throws IOException {
        if (evidenceLayout != null) return;

        EvidenceLayout layout = EvidenceLayout.resolve(client.runDirectory.toPath(), SCENARIO_ID);
        layout.requireFreshTargets(TITLE_SCREENSHOT_FILE_NAME, WORLD_SCREENSHOT_FILE_NAME);
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
                "every registered Etherology block state has a non-negative raw ID",
                blockStates == null
                        ? "not inspected"
                        : blockStates.registeredStates() + " inspected; missing=" + blockStates.missingStates()
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

        boolean titleDimensionsMatch = titleFramebufferWidth == FRAMEBUFFER_WIDTH
                && titleFramebufferHeight == FRAMEBUFFER_HEIGHT;
        passed &= addAssertion(
                assertions,
                "title_framebuffer_dimensions",
                titleDimensionsMatch,
                FRAMEBUFFER_WIDTH + "x" + FRAMEBUFFER_HEIGHT,
                titleFramebufferWidth + "x" + titleFramebufferHeight
        );
        boolean worldDimensionsMatch = worldFramebufferWidth == FRAMEBUFFER_WIDTH
                && worldFramebufferHeight == FRAMEBUFFER_HEIGHT;
        passed &= addAssertion(
                assertions,
                "world_framebuffer_dimensions",
                worldDimensionsMatch,
                FRAMEBUFFER_WIDTH + "x" + FRAMEBUFFER_HEIGHT,
                worldFramebufferWidth + "x" + worldFramebufferHeight
        );
        passed &= addAssertion(
                assertions,
                "completed_title_renders_before_capture",
                completedTitleRenders >= REQUIRED_COMPLETED_RENDERS,
                Integer.toString(REQUIRED_COMPLETED_RENDERS),
                Integer.toString(completedTitleRenders)
        );
        passed &= addAssertion(
                assertions,
                "completed_world_renders_before_capture",
                completedWorldRenders >= REQUIRED_COMPLETED_RENDERS,
                Integer.toString(REQUIRED_COMPLETED_RENDERS),
                Integer.toString(completedWorldRenders)
        );
        passed &= addScreenshotAssertion(assertions, "title", titleScreenshotResult);
        passed &= addScreenshotAssertion(assertions, "world", worldScreenshotResult);

        boolean integratedWorldReady = isWorldLifecycleReady(client);
        passed &= addAssertion(
                assertions,
                "integrated_world_joined",
                integratedWorldReady,
                "running server and connected client",
                integratedWorldReady ? "joined" : "not joined"
        );
        passed &= addAssertion(
                assertions,
                "client_world_mirrors_server_fixture",
                clientMirrorReady,
                "all four blocks and block entities mirrored",
                clientMirrorReady ? "mirrored" : "not mirrored"
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
                setup == null ? "missing setup" : Boolean.toString(setup.allBlockEntitiesPresent())
        );
        passed &= addAssertion(
                assertions,
                "block_entity_nbt_round_trip",
                setup != null && setup.blockEntityRoundTrip(),
                "matching reconstructed block-entity type",
                setup == null ? "missing setup" : setup.serializedBlockEntityId()
        );

        CreativeGroupSnapshot creative = creativeGroupSnapshot;
        passed &= addAssertion(
                assertions,
                "creative_inventory_population",
                creative != null && creative.stackCount() >= MINIMUM_CREATIVE_STACKS,
                "at least " + MINIMUM_CREATIVE_STACKS + " displayed stacks",
                creative == null ? "missing" : Integer.toString(creative.stackCount())
        );
        passed &= addAssertion(
                assertions,
                "creative_inventory_expected_items",
                creative != null && creative.expectedItemsPresent(),
                EXPECTED_CREATIVE_ITEMS.toString(),
                creative == null ? "missing" : creative.itemIds().toString()
        );
        passed &= addAssertion(
                assertions,
                "forced_world_save",
                Boolean.TRUE.equals(saveResult),
                "true",
                saveResult == null ? "not attempted" : saveResult.toString()
        );

        Path saveDirectory = client.runDirectory.toPath().resolve("saves").resolve(WORLD_DIRECTORY_NAME);
        boolean saveDirectoryPresent = Files.isDirectory(saveDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(saveDirectory);
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
        resources.add(MINECRAFT_TITLE_RESOURCE.toString());
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
                "resource-loaded-title-screen",
                TITLE_SCREENSHOT_FILE_NAME,
                titleFramebufferWidth,
                titleFramebufferHeight,
                completedTitleRenders,
                titleScreenshotResult
        );
        addScreenshot(
                screenshots,
                "integrated-world-fixture",
                WORLD_SCREENSHOT_FILE_NAME,
                worldFramebufferWidth,
                worldFramebufferHeight,
                completedWorldRenders,
                worldScreenshotResult
        );
        report.add("screenshots", screenshots);
        return report;
    }

    private boolean addScreenshotAssertion(
            JsonArray assertions,
            String step,
            ScreenshotResult result
    ) {
        return addAssertion(
                assertions,
                "native_screenshot_written:" + step,
                result != null && result.passed(),
                "non-empty PNG",
                result == null ? "missing" : result.size() + " bytes, sha256=" + result.sha256()
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

    private static RegistryExpectation item(String path) {
        return new RegistryExpectation("item", Registries.ITEM, etherologyId(path));
    }

    private static RegistryExpectation block(String path) {
        return new RegistryExpectation("block", Registries.BLOCK, etherologyId(path));
    }

    private static PlacedBlockExpectation placedBlock(String path, int x, int z) {
        return new PlacedBlockExpectation(
                etherologyId(path),
                new BlockPos(x, ARENA_FLOOR_Y + 1, z)
        );
    }

    private static Identifier etherologyId(String path) {
        return new Identifier("etherology", path);
    }

    private enum Stage {
        WAITING_FOR_TITLE,
        CAPTURING_TITLE,
        STARTING_WORLD,
        WAITING_FOR_WORLD,
        WAITING_FOR_SERVER_SETUP,
        WAITING_FOR_CLIENT_MIRROR,
        WAITING_FOR_WORLD_RENDERS,
        CAPTURING_WORLD,
        SAVING_WORLD,
        COMPLETE
    }

    private record PlacedBlockExpectation(Identifier id, BlockPos pos) {
    }

    private record ScreenshotResult(boolean passed, long size, String sha256, String failure) {

        private static ScreenshotResult failed(String failure) {
            return new ScreenshotResult(false, 0L, "", failure == null ? "unknown error" : failure);
        }
    }

    private record ServerSetupResult(
            boolean chunkLoaded,
            boolean playerCreative,
            boolean allBlocksPlaced,
            boolean allBlockEntitiesPresent,
            boolean blockEntityRoundTrip,
            String serializedBlockEntityId,
            List<String> placedBlockIds
    ) {
    }

    private record CreativeGroupSnapshot(
            int stackCount,
            boolean expectedItemsPresent,
            List<String> itemIds
    ) {
    }

    private record BlockStateRegistrySnapshot(int registeredStates, List<String> missingStates) {
    }
}
