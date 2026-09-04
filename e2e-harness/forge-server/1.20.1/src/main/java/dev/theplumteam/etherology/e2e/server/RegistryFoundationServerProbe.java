package dev.theplumteam.etherology.e2e.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.GameEventTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.event.GameEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Exercises the shared registry foundation in a real dedicated Forge server.
 */
@Mod(RegistryFoundationServerProbe.MOD_ID)
public final class RegistryFoundationServerProbe {

    /**
     * Identifies the isolated server-only probe mod.
     */
    public static final String MOD_ID = "etherology_e2e_server_probe";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROFILE_PROPERTY = "etherology.serverProbe.profileId";
    private static final String SCENARIO_PROPERTY = "etherology.serverProbe.scenario";
    private static final String RUNTIME_KIND_PROPERTY = "etherology.serverProbe.runtimeKind";
    private static final String FORBIDDEN_MOD_IDS_PROPERTY =
            "etherology.serverProbe.forbiddenModIds";
    private static final String EVIDENCE_ROOT_PROPERTY = "etherology.serverProbe.evidenceRoot";
    private static final Identifier EVENT_ID = Identifier.of(
            "etherology",
            "etherology_resonance"
    );
    private static final String INTERNAL_EVENT_ID = "etherology_resonance";
    private static final int EVENT_RANGE = 16;
    static final long MAXIMUM_SLITHERITE_ENTITY_TICK_GATE_TICKS = 100L;
    static final long MAXIMUM_SLITHERITE_PROBE_ENTITY_TICK_TICKS = 20L;
    static final long MAXIMUM_SLITHERITE_LIVING_ACTIVATION_TICKS = 20L;
    static final long SLITHERITE_PRESSURE_PLATE_RESET_TICKS = 25L;
    private static final UUID SLITHERITE_PLAYER_UUID = UUID.fromString(
            "69aac04b-baf3-4c06-b6c8-0f74f456107a"
    );
    private static final String SLITHERITE_PLAYER_NAME = "SlitheriteProbe";
    private static final List<String> EXPECTED_ETHERLOGY_TAG_IDS = List.of(
            "minecraft:vibrations",
            "minecraft:warden_can_listen"
    );
    private static final List<String> EXPECTED_LIFECYCLE = List.of(
            "tags_updated_initial",
            "server_started",
            "reload_requested",
            "tags_updated_reload",
            "reload_command_returned",
            "stop_requested",
            "server_stopping",
            "server_stopped"
    );

    private final Path evidenceRoot;
    private final List<String> forbiddenModIds;
    private final List<String> lifecycle = new ArrayList<>();
    private final String profileId;
    private final ServerProbeProcessTerminator processTerminator;
    private final String runtimeKind;
    private final String scenarioId;

    private EtherSourceProbeState initialEtherSourceState =
            EtherSourceProbeState.failed("not captured");
    private EtherSourceProbeState reloadedEtherSourceState =
            EtherSourceProbeState.failed("not captured");
    private EtherSourceProbeState serverStartedEtherSourceState =
            EtherSourceProbeState.failed("not captured");
    private EnchantmentProbeState initialEnchantmentState =
            EnchantmentProbeState.missing();
    private EnchantmentProbeState reloadedEnchantmentState =
            EnchantmentProbeState.missing();
    private EnchantmentProbeState serverStartedEnchantmentState =
            EnchantmentProbeState.missing();
    private ParticleProbeState initialParticleState = ParticleProbeState.missing();
    private ParticleProbeState reloadedParticleState = ParticleProbeState.missing();
    private ParticleProbeState serverStartedParticleState = ParticleProbeState.missing();
    private MaterialItemProbeState initialMaterialItemState = MaterialItemProbeState.missing();
    private MaterialItemProbeState reloadedMaterialItemState = MaterialItemProbeState.missing();
    private MaterialItemProbeState serverStartedMaterialItemState =
            MaterialItemProbeState.missing();
    private FoodItemProbeState initialFoodItemState = FoodItemProbeState.missing();
    private FoodItemProbeState reloadedFoodItemState = FoodItemProbeState.missing();
    private FoodItemProbeState serverStartedFoodItemState = FoodItemProbeState.missing();
    private FoodItemProbeState.FoodConsumptionState serverStartedFoodConsumption =
            FoodItemProbeState.FoodConsumptionState.missing(
                    FoodItemProbeState.ConsumptionPhase.SERVER_STARTED
            );
    private FoodItemProbeState.FoodConsumptionState reloadedFoodConsumption =
            FoodItemProbeState.FoodConsumptionState.missing(
                    FoodItemProbeState.ConsumptionPhase.RELOADED
            );
    private ForestLanternProbeState initialForestLanternState =
            ForestLanternProbeState.missing();
    private ForestLanternProbeState reloadedForestLanternState =
            ForestLanternProbeState.missing();
    private ForestLanternProbeState serverStartedForestLanternState =
            ForestLanternProbeState.missing();
    private ForestLanternProbeState.WorldMechanics initialForestLanternMechanics =
            ForestLanternProbeState.WorldMechanics.missing(
                    ForestLanternProbeState.MechanicsPhase.SERVER_STARTED
            );
    private ForestLanternProbeState.WorldMechanics reloadedForestLanternMechanics =
            ForestLanternProbeState.WorldMechanics.missing(
                    ForestLanternProbeState.MechanicsPhase.RELOADED
            );
    private MetalBlockProbeState initialMetalBlockState = MetalBlockProbeState.missing();
    private MetalBlockProbeState reloadedMetalBlockState = MetalBlockProbeState.missing();
    private MetalBlockProbeState serverStartedMetalBlockState =
            MetalBlockProbeState.missing();
    private MetalBlockProbeState.MetalBlockPlacementState initialMetalBlockPlacement =
            MetalBlockProbeState.MetalBlockPlacementState.missing();
    private MetalBlockProbeState.MetalBlockPlacementState reloadedMetalBlockPlacement =
            MetalBlockProbeState.MetalBlockPlacementState.missing();
    private AttrahiteBlockProbeState tagLoadedAttrahiteBlockState =
            AttrahiteBlockProbeState.missing();
    private AttrahiteBlockProbeState initialAttrahiteBlockState =
            AttrahiteBlockProbeState.missing();
    private AttrahiteBlockProbeState reloadedAttrahiteBlockState =
            AttrahiteBlockProbeState.missing();
    private AttrahiteBlockProbeState serverStartedAttrahiteBlockState =
            AttrahiteBlockProbeState.missing();
    private AttrahiteBlockProbeState.PlacementState initialAttrahiteBlockPlacement =
            AttrahiteBlockProbeState.PlacementState.missing();
    private AttrahiteBlockProbeState.PlacementState reloadedAttrahiteBlockPlacement =
            AttrahiteBlockProbeState.PlacementState.missing();
    private SlitheriteBlockProbeState tagLoadedSlitheriteBlockState =
            SlitheriteBlockProbeState.missing();
    private SlitheriteBlockProbeState initialSlitheriteBlockState =
            SlitheriteBlockProbeState.missing();
    private SlitheriteBlockProbeState reloadedSlitheriteBlockState =
            SlitheriteBlockProbeState.missing();
    private SlitheriteBlockProbeState serverStartedSlitheriteBlockState =
            SlitheriteBlockProbeState.missing();
    private SlitheriteBlockProbeState.PlacementState initialSlitheriteBlockPlacement =
            SlitheriteBlockProbeState.PlacementState.missing();
    private SlitheriteBlockProbeState.PlacementState savedSlitheriteBlockPlacement =
            SlitheriteBlockProbeState.PlacementState.missing();
    private SlitheriteBlockProbeState.PlacementState reloadedSlitheriteBlockPlacement =
            SlitheriteBlockProbeState.PlacementState.missing();
    private SlitheriteNativePlacementState initialSlitheriteNativePlacement =
            SlitheriteNativePlacementState.missing();
    private SlitheriteBehaviorSequence slitheriteBehaviorSequence;
    private SlitheriteBehaviorProbe slitheriteBehaviorProbe =
            SlitheriteBehaviorProbe.missing();
    private LootConditionProbeState lootConditionState = LootConditionProbeState.missing();
    private MinecraftServer startedServer;
    private GameEvent taggedEvent;
    private String reloadFailure = "not requested";
    private String attrahiteWorldSaveFailure = "not requested";
    private String slitheriteWorldSaveFailure = "not requested";
    private String reloadPackDirectory = "";
    private List<String> reloadPackResourcePaths = List.of();
    private List<String> enabledDataPackNamesAfterReload = List.of();
    private String registryEventId = "";
    private String internalEventId = "";
    private String updateCause = "";
    private String reloadUpdateCause = "";
    private int eventRange = -1;
    private int reloadCommandResult = -1;
    private int tagUpdateCount;
    private List<String> registryEtherologyEventIds = List.of();
    private List<String> loadedModIds = List.of();
    private List<String> forbiddenModIdsLoaded = List.of();
    private List<String> serverStartedLoadedModIds = List.of();
    private boolean shouldUpdateStaticData;
    private boolean vibrationsContainsEvent;
    private boolean wardenCanListenContainsEvent;
    private List<String> vibrationsEtherologyEventIds = List.of();
    private List<String> wardenCanListenEtherologyEventIds = List.of();
    private List<String> etherologyTagIds = List.of();
    private boolean tagsBeforeServerStarted;
    private boolean etherSourceCapturedAfterServerDataLoad;
    private boolean serverStartedModsRechecked;
    private boolean serverStartedRegistryRechecked;
    private boolean serverStartedTagsRechecked;
    private boolean serverStartedEtherSourcesRechecked;
    private boolean lootConditionCapturedAfterServerDataLoad;
    private boolean enchantmentsCapturedAfterServerDataLoad;
    private boolean particlesCapturedAfterServerDataLoad;
    private boolean materialItemsCapturedAfterServerDataLoad;
    private boolean foodItemsCapturedAfterServerDataLoad;
    private boolean forestLanternCapturedAfterServerDataLoad;
    private boolean metalBlocksCapturedAfterServerDataLoad;
    private boolean attrahiteBlocksCapturedAtInitialTagLoad;
    private boolean attrahiteBlocksCapturedAfterServerDataLoad;
    private boolean slitheriteBlocksCapturedAtInitialTagLoad;
    private boolean slitheriteBlocksCapturedAfterServerDataLoad;
    private boolean serverStartedLootConditionRechecked;
    private boolean serverStartedEnchantmentsRechecked;
    private boolean serverStartedParticlesRechecked;
    private boolean serverStartedMaterialItemsRechecked;
    private boolean serverStartedFoodItemsRechecked;
    private boolean serverStartedForestLanternRechecked;
    private boolean serverStartedMetalBlocksRechecked;
    private boolean serverStartedAttrahiteBlocksRechecked;
    private boolean serverStartedSlitheriteBlocksRechecked;
    private boolean reloadRequested;
    private boolean reloadTagsObserved;
    private boolean reloadCompleted;
    private boolean enabledDataPacksExact;
    private boolean reloadShouldUpdateStaticData;
    private boolean registryStableAfterReload;
    private boolean tagsStableAfterReload;
    private boolean lootConditionStableAfterReload;
    private boolean lootTableInstanceReplacedAfterReload;
    private boolean enchantmentRegistryStableAfterReload;
    private boolean enchantmentPropertiesStableAfterReload;
    private boolean enchantmentTagStableAfterReload;
    private boolean particleRegistryStableAfterReload;
    private boolean particleTypeContractStableAfterReload;
    private boolean particleWireContractStableAfterReload;
    private boolean materialItemRegistryStableAfterReload;
    private boolean materialItemPropertiesStableAfterReload;
    private boolean materialItemStackNbtStableAfterReload;
    private boolean foodItemRegistryStableAfterReload;
    private boolean foodItemPropertiesStableAfterReload;
    private boolean foodItemStackNbtStableAfterReload;
    private boolean foodConsumptionFreshPlayerAfterReload;
    private boolean foodConsumptionStableAfterReload;
    private boolean forestLanternRegistryStableAfterReload;
    private boolean forestLanternStatesStableAfterReload;
    private boolean forestLanternTagsStableAfterReload;
    private boolean forestLanternLoadedDataStableAfterReload;
    private boolean forestLanternLoadedDataFreshAfterReload;
    private boolean forestLanternMechanicsFreshPlayersAfterReload;
    private boolean forestLanternMechanicsStableAfterReload;
    private boolean metalBlockRegistryStableAfterReload;
    private boolean metalBlockPropertiesStableAfterReload;
    private boolean metalBlockTagsStableAfterReload;
    private boolean metalBlockStackNbtStableAfterReload;
    private boolean metalBlockPlacementStableAfterReload;
    private boolean attrahiteBlockRegistryStableAfterReload;
    private boolean attrahiteBlockPropertiesStableAfterReload;
    private boolean attrahiteBlockTagsStableAfterReload;
    private boolean attrahiteBlockStackNbtStableAfterReload;
    private boolean attrahiteBlockLoadedDataStableAfterReload;
    private boolean attrahiteBlockLoadedDataFreshAfterReload;
    private boolean attrahiteBlockPlacementStableAfterReload;
    private boolean attrahiteWorldSavedAfterPlacement;
    private boolean slitheriteBlockRegistryStableAfterReload;
    private boolean slitheriteBlockDefaultStatesStableAfterReload;
    private boolean slitheriteBlockTagsStableAfterReload;
    private boolean slitheriteBlockLoadedDataStableAfterReload;
    private boolean slitheriteBlockLoadedDataFreshAfterReload;
    private boolean slitheriteBlockPlacementSavedAfterForceSave;
    private boolean slitheriteBlockPlacementStableAfterReload;
    private boolean slitheriteWorldSavedAfterPlacement;
    private boolean serverStartedContinuationStarted;
    private boolean stopRequestedAfterReload;
    private boolean stopRequestedWithoutRestart;
    private boolean stoppingServerMatched;
    private boolean stoppedServerMatched;

    /**
     * Rejects every non-dedicated distribution before registering lifecycle callbacks.
     */
    public RegistryFoundationServerProbe() {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            throw new IllegalStateException(
                    "The Etherology server probe requires DEDICATED_SERVER, found "
                            + FMLEnvironment.dist
            );
        }

        ServerProbeMemoryHandoff.publishAndAwaitAcknowledgement();

        profileId = requireSystemProperty(PROFILE_PROPERTY);
        scenarioId = requireSystemProperty(SCENARIO_PROPERTY);
        runtimeKind = requireSystemProperty(RUNTIME_KIND_PROPERTY);
        processTerminator = ServerProbeProcessTerminator.forLoomUserdev(runtimeKind);
        forbiddenModIds = ServerProbeModInventory.parseDeclaredIds(
                System.getProperty(FORBIDDEN_MOD_IDS_PROPERTY)
        );
        try {
            evidenceRoot = ServerProbeReportWriter.resolveEvidenceRoot(
                    System.getProperty(EVIDENCE_ROOT_PROPERTY)
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "The server-probe evidence layout is invalid",
                    exception
            );
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Captures the registry and tag state after the server data-pack load binds static tags.
     *
     * @param event the Forge tag-reload notification
     */
    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        tagUpdateCount++;
        if (tagUpdateCount == 1 && startedServer == null) {
            lifecycle.add("tags_updated_initial");
            updateCause = event.getUpdateCause().name();
            shouldUpdateStaticData = event.shouldUpdateStaticData();
            taggedEvent = Registries.GAME_EVENT.getOrEmpty(EVENT_ID).orElse(null);
            captureRegistryState(taggedEvent);
            registryEtherologyEventIds = collectRegistryEtherologyEventIds();
            vibrationsContainsEvent = isInTag(taggedEvent, GameEventTags.VIBRATIONS);
            wardenCanListenContainsEvent = isInTag(
                    taggedEvent,
                    GameEventTags.WARDEN_CAN_LISTEN
            );
            captureEtherologyTagMemberships();
            initialEtherSourceState = EtherSourceProbeState.capture();
            initialEnchantmentState = EnchantmentProbeState.capture();
            initialParticleState = ParticleProbeState.capture();
            initialMaterialItemState = MaterialItemProbeState.capture();
            initialFoodItemState = FoodItemProbeState.capture();
            initialMetalBlockState = MetalBlockProbeState.capture();
            tagLoadedAttrahiteBlockState = AttrahiteBlockProbeState.captureTagLoaded();
            attrahiteBlocksCapturedAtInitialTagLoad = tagLoadedAttrahiteBlockState
                    .hasExactCoreContract();
            tagLoadedSlitheriteBlockState =
                    SlitheriteBlockProbeState.captureTagLoaded();
            slitheriteBlocksCapturedAtInitialTagLoad =
                    tagLoadedSlitheriteBlockState.hasExactCoreContract();
            LOGGER.info("[EtherologyServerProbe] tags_updated_initial");
            return;
        }

        lifecycle.add("tags_updated_reload");
        reloadUpdateCause = event.getUpdateCause().name();
        reloadShouldUpdateStaticData = event.shouldUpdateStaticData();
        reloadedEtherSourceState = EtherSourceProbeState.capture();
        reloadedEnchantmentState = EnchantmentProbeState.capture();
        reloadedParticleState = ParticleProbeState.capture();
        reloadedMaterialItemState = MaterialItemProbeState.capture();
        reloadedFoodItemState = FoodItemProbeState.capture();
        reloadedMetalBlockState = MetalBlockProbeState.capture();
        reloadedAttrahiteBlockState = startedServer == null
                ? AttrahiteBlockProbeState.missing()
                : AttrahiteBlockProbeState.capture(startedServer);
        reloadedSlitheriteBlockState = startedServer == null
                ? SlitheriteBlockProbeState.missing()
                : SlitheriteBlockProbeState.capture(startedServer);
        reloadedForestLanternState = startedServer == null
                ? ForestLanternProbeState.missing()
                : ForestLanternProbeState.capture(startedServer);
        reloadedFoodConsumption = startedServer == null
                ? FoodItemProbeState.FoodConsumptionState.missing(
                        FoodItemProbeState.ConsumptionPhase.RELOADED
                )
                : FoodItemProbeState.FoodConsumptionState.consume(
                        startedServer,
                        FoodItemProbeState.ConsumptionPhase.RELOADED
                );
        reloadTagsObserved = tagUpdateCount == 2 && startedServer != null && reloadRequested;
        GameEvent reloadedEvent = Registries.GAME_EVENT.getOrEmpty(EVENT_ID).orElse(null);
        registryStableAfterReload = reloadTagsObserved
                && reloadedEvent != null
                && reloadedEvent == taggedEvent
                && EVENT_ID.equals(Registries.GAME_EVENT.getId(reloadedEvent))
                && INTERNAL_EVENT_ID.equals(reloadedEvent.getId())
                && reloadedEvent.getRange() == EVENT_RANGE
                && collectRegistryEtherologyEventIds().equals(registryEtherologyEventIds);
        tagsStableAfterReload = reloadTagsObserved
                && isInTag(reloadedEvent, GameEventTags.VIBRATIONS)
                && isInTag(reloadedEvent, GameEventTags.WARDEN_CAN_LISTEN)
                && hasExactEtherologyTagMemberships(collectEtherologyTagMemberships());
        LootConditionProbeState reloadedLootConditionState = startedServer == null
                ? LootConditionProbeState.missing()
                : LootConditionProbeState.capture(startedServer);
        lootConditionStableAfterReload = reloadTagsObserved
                && lootConditionState.hasSameRegistryAndBehavior(
                        reloadedLootConditionState
                );
        lootTableInstanceReplacedAfterReload = reloadTagsObserved
                && lootConditionState.hasReplacedProbeTableInstanceAfterReload(
                        reloadedLootConditionState
                );
        enchantmentRegistryStableAfterReload = reloadTagsObserved
                && initialEnchantmentState.hasSameRegistry(reloadedEnchantmentState);
        enchantmentPropertiesStableAfterReload = reloadTagsObserved
                && initialEnchantmentState.hasSameProperties(reloadedEnchantmentState);
        enchantmentTagStableAfterReload = reloadTagsObserved
                && initialEnchantmentState.hasSameTagMembership(
                        reloadedEnchantmentState
                );
        particleRegistryStableAfterReload = reloadTagsObserved
                && initialParticleState.hasSameRegistry(reloadedParticleState);
        particleTypeContractStableAfterReload = reloadTagsObserved
                && initialParticleState.hasSameTypeContract(reloadedParticleState);
        particleWireContractStableAfterReload = reloadTagsObserved
                && initialParticleState.hasSameWireContract(reloadedParticleState);
        materialItemRegistryStableAfterReload = reloadTagsObserved
                && initialMaterialItemState.hasSameRegistry(reloadedMaterialItemState);
        materialItemPropertiesStableAfterReload = reloadTagsObserved
                && initialMaterialItemState.hasSameProperties(reloadedMaterialItemState);
        materialItemStackNbtStableAfterReload = reloadTagsObserved
                && initialMaterialItemState.hasSameStackNbt(reloadedMaterialItemState);
        foodItemRegistryStableAfterReload = reloadTagsObserved
                && initialFoodItemState.hasSameRegistry(reloadedFoodItemState);
        foodItemPropertiesStableAfterReload = reloadTagsObserved
                && initialFoodItemState.hasSameProperties(reloadedFoodItemState);
        foodItemStackNbtStableAfterReload = reloadTagsObserved
                && initialFoodItemState.hasSameStackNbt(reloadedFoodItemState);
        foodConsumptionFreshPlayerAfterReload = reloadTagsObserved
                && reloadedFoodConsumption.isFreshPlayerComparedWith(
                        serverStartedFoodConsumption
                );
        foodConsumptionStableAfterReload = reloadTagsObserved
                && serverStartedFoodConsumption.hasExactConsumption()
                && reloadedFoodConsumption.hasExactConsumption()
                && serverStartedFoodConsumption.hasSameOutcome(reloadedFoodConsumption)
                && foodConsumptionFreshPlayerAfterReload;
        reloadedForestLanternMechanics = startedServer == null
                ? ForestLanternProbeState.WorldMechanics.missing(
                        ForestLanternProbeState.MechanicsPhase.RELOADED
                )
                : ForestLanternProbeState.exerciseWorld(
                        startedServer,
                        ForestLanternProbeState.MechanicsPhase.RELOADED
                );
        forestLanternRegistryStableAfterReload = reloadTagsObserved
                && initialForestLanternState.hasSameRegistry(
                        reloadedForestLanternState
                );
        forestLanternStatesStableAfterReload = reloadTagsObserved
                && initialForestLanternState.hasSameStatesAndProperties(
                        reloadedForestLanternState
                );
        forestLanternTagsStableAfterReload = reloadTagsObserved
                && initialForestLanternState.hasSameTags(
                        reloadedForestLanternState
                );
        forestLanternLoadedDataStableAfterReload = reloadTagsObserved
                && initialForestLanternState.hasReloadedDataOutcome(
                        reloadedForestLanternState
                );
        forestLanternLoadedDataFreshAfterReload = reloadTagsObserved
                && initialForestLanternState.hasFreshReloadedData(
                        reloadedForestLanternState
                );
        forestLanternMechanicsFreshPlayersAfterReload = reloadTagsObserved
                && initialForestLanternMechanics.hasFreshPlayers(
                        reloadedForestLanternMechanics
                );
        forestLanternMechanicsStableAfterReload = reloadTagsObserved
                && initialForestLanternMechanics.hasExactContract()
                && reloadedForestLanternMechanics.hasExactContract()
                && initialForestLanternMechanics.sameOutcome(
                        reloadedForestLanternMechanics
                )
                && forestLanternMechanicsFreshPlayersAfterReload;
        metalBlockRegistryStableAfterReload = reloadTagsObserved
                && initialMetalBlockState.hasSameRegistry(reloadedMetalBlockState);
        metalBlockPropertiesStableAfterReload = reloadTagsObserved
                && initialMetalBlockState.hasSameProperties(reloadedMetalBlockState);
        metalBlockTagsStableAfterReload = reloadTagsObserved
                && initialMetalBlockState.hasSameTags(reloadedMetalBlockState);
        metalBlockStackNbtStableAfterReload = reloadTagsObserved
                && initialMetalBlockState.hasSameStackNbt(reloadedMetalBlockState);
        reloadedMetalBlockPlacement = startedServer == null
                ? MetalBlockProbeState.MetalBlockPlacementState.missing()
                : MetalBlockProbeState.capturePlacement(startedServer.getOverworld());
        metalBlockPlacementStableAfterReload = reloadTagsObserved
                && initialMetalBlockPlacement.samePlacement(reloadedMetalBlockPlacement)
                && reloadedMetalBlockPlacement.hasExactPlacement();
        attrahiteBlockRegistryStableAfterReload = reloadTagsObserved
                && initialAttrahiteBlockState.hasSameRegistry(
                        reloadedAttrahiteBlockState
                );
        attrahiteBlockPropertiesStableAfterReload = reloadTagsObserved
                && initialAttrahiteBlockState.hasSameProperties(
                        reloadedAttrahiteBlockState
                );
        attrahiteBlockTagsStableAfterReload = reloadTagsObserved
                && initialAttrahiteBlockState.hasSameTags(
                        reloadedAttrahiteBlockState
                );
        attrahiteBlockStackNbtStableAfterReload = reloadTagsObserved
                && initialAttrahiteBlockState.hasSameStackNbt(
                        reloadedAttrahiteBlockState
                );
        attrahiteBlockLoadedDataStableAfterReload = reloadTagsObserved
                && initialAttrahiteBlockState.hasReloadedDataOutcome(
                        reloadedAttrahiteBlockState
                );
        attrahiteBlockLoadedDataFreshAfterReload = reloadTagsObserved
                && initialAttrahiteBlockState.hasFreshReloadedData(
                        reloadedAttrahiteBlockState
                );
        reloadedAttrahiteBlockPlacement = startedServer == null
                ? AttrahiteBlockProbeState.PlacementState.missing()
                : AttrahiteBlockProbeState.capturePlacement(
                        startedServer.getOverworld()
                );
        attrahiteBlockPlacementStableAfterReload = reloadTagsObserved
                && initialAttrahiteBlockPlacement.samePlacement(
                        reloadedAttrahiteBlockPlacement
                )
                && reloadedAttrahiteBlockPlacement.hasExactPlacement();
        slitheriteBlockRegistryStableAfterReload = reloadTagsObserved
                && initialSlitheriteBlockState.hasSameRegistry(
                        reloadedSlitheriteBlockState
                );
        slitheriteBlockDefaultStatesStableAfterReload = reloadTagsObserved
                && initialSlitheriteBlockState.hasSameDefaultStatesAndRawIds(
                        reloadedSlitheriteBlockState
                );
        slitheriteBlockTagsStableAfterReload = reloadTagsObserved
                && initialSlitheriteBlockState.hasSameTags(
                        reloadedSlitheriteBlockState
                );
        slitheriteBlockLoadedDataStableAfterReload = reloadTagsObserved
                && initialSlitheriteBlockState.hasReloadedDataOutcome(
                        reloadedSlitheriteBlockState
                );
        slitheriteBlockLoadedDataFreshAfterReload = reloadTagsObserved
                && initialSlitheriteBlockState.hasFreshReloadedData(
                        reloadedSlitheriteBlockState
                );
        reloadedSlitheriteBlockPlacement = startedServer == null
                ? SlitheriteBlockProbeState.PlacementState.missing()
                : SlitheriteBlockProbeState.capturePlacement(
                        startedServer.getOverworld()
                );
        slitheriteBlockPlacementStableAfterReload = reloadTagsObserved
                && savedSlitheriteBlockPlacement.samePlacement(
                        reloadedSlitheriteBlockPlacement
                )
                && reloadedSlitheriteBlockPlacement.hasExactPlacement();
        LOGGER.info("[EtherologyServerProbe] tags_updated_reload");
    }

    /**
     * Evaluates the loaded probe table once server data and the overworld are both available.
     *
     * @param event the starting dedicated server after its server-data load
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        lootConditionCapturedAfterServerDataLoad = tagUpdateCount == 1
                && lifecycle.equals(List.of("tags_updated_initial"));
        etherSourceCapturedAfterServerDataLoad = lootConditionCapturedAfterServerDataLoad
                && initialEtherSourceState.hasExactInitialEntries();
        enchantmentsCapturedAfterServerDataLoad = lootConditionCapturedAfterServerDataLoad
                && initialEnchantmentState.hasExactRegistry()
                && initialEnchantmentState.hasExactProperties()
                && initialEnchantmentState.hasExactTagMembership();
        particlesCapturedAfterServerDataLoad = lootConditionCapturedAfterServerDataLoad
                && initialParticleState.hasExactRegistry()
                && initialParticleState.hasExactTypeContract()
                && initialParticleState.hasExactWireContract();
        materialItemsCapturedAfterServerDataLoad = lootConditionCapturedAfterServerDataLoad
                && initialMaterialItemState.hasExactRegistry()
                && initialMaterialItemState.hasExactContract();
        foodItemsCapturedAfterServerDataLoad = lootConditionCapturedAfterServerDataLoad
                && initialFoodItemState.hasExactRegistry()
                && initialFoodItemState.hasExactContract();
        initialForestLanternState = ForestLanternProbeState.capture(event.getServer());
        forestLanternCapturedAfterServerDataLoad = lootConditionCapturedAfterServerDataLoad
                && initialForestLanternState.hasExactContract();
        metalBlocksCapturedAfterServerDataLoad = lootConditionCapturedAfterServerDataLoad
                && initialMetalBlockState.hasExactRegistry()
                && initialMetalBlockState.hasExactContract();
        initialAttrahiteBlockState = AttrahiteBlockProbeState.capture(
                event.getServer()
        );
        attrahiteBlocksCapturedAfterServerDataLoad =
                lootConditionCapturedAfterServerDataLoad
                        && attrahiteBlocksCapturedAtInitialTagLoad
                        && tagLoadedAttrahiteBlockState.sameCoreState(
                                initialAttrahiteBlockState
                        )
                        && initialAttrahiteBlockState.hasExactContract();
        initialSlitheriteBlockState = SlitheriteBlockProbeState.capture(
                event.getServer()
        );
        slitheriteBlocksCapturedAfterServerDataLoad =
                lootConditionCapturedAfterServerDataLoad
                        && slitheriteBlocksCapturedAtInitialTagLoad
                        && tagLoadedSlitheriteBlockState.sameCoreState(
                                initialSlitheriteBlockState
                        )
                        && initialSlitheriteBlockState.hasExactContract();
        lootConditionState = LootConditionProbeState.capture(event.getServer());
        LOGGER.info("[EtherologyServerProbe] registry_foundation_checked");
    }

    /**
     * Rechecks initial state, installs a fresh datapack, and invokes the real reload command.
     *
     * @param event the ready dedicated server
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        lifecycle.add("server_started");
        LOGGER.info("[EtherologyServerProbe] server_started");
        startedServer = event.getServer();
        tagsBeforeServerStarted = lifecycle.equals(List.of(
                "tags_updated_initial",
                "server_started"
        ));
        serverStartedLoadedModIds = collectLoadedModIds();
        serverStartedModsRechecked = serverStartedLoadedModIds.contains("etherology")
                && serverStartedLoadedModIds.contains(MOD_ID)
                && ServerProbeModInventory.sortedIntersection(
                        serverStartedLoadedModIds,
                        forbiddenModIds
                ).isEmpty();

        GameEvent startedEvent = Registries.GAME_EVENT.getOrEmpty(EVENT_ID).orElse(null);
        List<String> startedRegistryEtherologyEventIds = collectRegistryEtherologyEventIds();
        serverStartedRegistryRechecked = startedEvent != null
                && startedEvent == taggedEvent
                && EVENT_ID.equals(Registries.GAME_EVENT.getId(startedEvent))
                && INTERNAL_EVENT_ID.equals(startedEvent.getId())
                && startedEvent.getRange() == EVENT_RANGE
                && registryEtherologyEventIds.equals(List.of(EVENT_ID.toString()))
                && startedRegistryEtherologyEventIds.equals(registryEtherologyEventIds);
        Map<String, List<String>> startedMemberships = collectEtherologyTagMemberships();
        serverStartedTagsRechecked = isInTag(startedEvent, GameEventTags.VIBRATIONS)
                && isInTag(startedEvent, GameEventTags.WARDEN_CAN_LISTEN)
                && vibrationsContainsEvent
                && wardenCanListenContainsEvent
                && hasExactEtherologyTagMemberships(startedMemberships)
                && hasExactCapturedEtherologyTagMemberships();
        LootConditionProbeState startedLootConditionState = LootConditionProbeState.capture(
                event.getServer()
        );
        serverStartedLootConditionRechecked = lootConditionState.sameStateAtServerStarted(
                startedLootConditionState
        );
        serverStartedEtherSourceState = EtherSourceProbeState.capture();
        serverStartedEtherSourcesRechecked = initialEtherSourceState.hasExactInitialEntries()
                && initialEtherSourceState.sameEntries(serverStartedEtherSourceState);
        serverStartedEnchantmentState = EnchantmentProbeState.capture();
        serverStartedEnchantmentsRechecked = initialEnchantmentState
                .sameStateAtServerStarted(serverStartedEnchantmentState);
        serverStartedParticleState = ParticleProbeState.capture();
        serverStartedParticlesRechecked = initialParticleState.sameStateAtServerStarted(
                serverStartedParticleState
        );
        serverStartedMaterialItemState = MaterialItemProbeState.capture();
        serverStartedMaterialItemsRechecked = initialMaterialItemState
                .sameStateAtServerStarted(serverStartedMaterialItemState);
        serverStartedFoodItemState = FoodItemProbeState.capture();
        serverStartedFoodItemsRechecked = initialFoodItemState
                .sameStateAtServerStarted(serverStartedFoodItemState);
        serverStartedFoodConsumption = FoodItemProbeState.FoodConsumptionState.consume(
                event.getServer(),
                FoodItemProbeState.ConsumptionPhase.SERVER_STARTED
        );
        serverStartedForestLanternState = ForestLanternProbeState.capture(
                event.getServer()
        );
        serverStartedForestLanternRechecked = initialForestLanternState
                .sameStateAtServerStarted(serverStartedForestLanternState);
        initialForestLanternMechanics = ForestLanternProbeState.exerciseWorld(
                event.getServer(),
                ForestLanternProbeState.MechanicsPhase.SERVER_STARTED
        );
        serverStartedMetalBlockState = MetalBlockProbeState.capture();
        serverStartedMetalBlocksRechecked = initialMetalBlockState
                .sameStateAtServerStarted(serverStartedMetalBlockState);
        initialMetalBlockPlacement = MetalBlockProbeState.placeIn(
                event.getServer().getOverworld()
        );
        serverStartedAttrahiteBlockState = AttrahiteBlockProbeState.capture(
                event.getServer()
        );
        serverStartedAttrahiteBlocksRechecked = initialAttrahiteBlockState
                .sameStateAtServerStarted(serverStartedAttrahiteBlockState);
        initialAttrahiteBlockPlacement = AttrahiteBlockProbeState.placeIn(
                event.getServer().getOverworld()
        );
        serverStartedSlitheriteBlockState = SlitheriteBlockProbeState.capture(
                event.getServer()
        );
        serverStartedSlitheriteBlocksRechecked = initialSlitheriteBlockState
                .sameStateAtServerStarted(serverStartedSlitheriteBlockState);
        try {
            SlitheritePlacementSetup setup = placeSlitheriteBlockItems(
                    event.getServer(),
                    event.getServer().getOverworld()
            );
            initialSlitheriteNativePlacement = setup.placement();
            initialSlitheriteBlockPlacement =
                    SlitheriteBlockProbeState.capturePlacement(
                            event.getServer().getOverworld()
                    );
            initializeSlitheriteBehaviorProbe(
                    event.getServer().getOverworld(),
                    setup.player()
            );
        } catch (RuntimeException exception) {
            initialSlitheriteNativePlacement =
                    SlitheriteNativePlacementState.failed(
                            exception.getClass().getName()
                    );
            slitheriteBehaviorProbe = SlitheriteBehaviorProbe.failed(
                    exception.getClass().getName()
            );
            continueServerStartedProbe(event.getServer());
        }
    }

    /**
     * Advances the tick-dependent Slitherite button and pressure-plate checks.
     *
     * @param event the Forge server tick notification
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.getServer() != startedServer
                || serverStartedContinuationStarted
                || slitheriteBehaviorSequence == null) {
            return;
        }
        try {
            advanceSlitheriteBehaviorProbe(event.getServer().getOverworld());
        } catch (RuntimeException exception) {
            discardSlitheriteProbeEntities();
            slitheriteBehaviorProbe = SlitheriteBehaviorProbe.failed(
                    exception.getClass().getName()
            );
        }
        if (slitheriteBehaviorProbe.completed()) {
            continueServerStartedProbe(event.getServer());
        }
    }

    private void continueServerStartedProbe(MinecraftServer server) {
        if (serverStartedContinuationStarted) {
            return;
        }
        serverStartedContinuationStarted = true;
        try {
            boolean saved = server.save(true, true, false);
            attrahiteWorldSavedAfterPlacement = saved;
            slitheriteWorldSavedAfterPlacement = saved;
            attrahiteWorldSaveFailure = "";
            slitheriteWorldSaveFailure = "";
            savedSlitheriteBlockPlacement =
                    SlitheriteBlockProbeState.capturePlacement(
                            server.getOverworld()
                    );
            slitheriteBlockPlacementSavedAfterForceSave = saved
                    && initialSlitheriteBlockPlacement.samePlacement(
                            savedSlitheriteBlockPlacement
                    )
                    && savedSlitheriteBlockPlacement.hasExactPlacement();
        } catch (RuntimeException exception) {
            String failure = exception.getClass().getName();
            attrahiteWorldSaveFailure = failure;
            slitheriteWorldSaveFailure = failure;
        }

        try {
            ReloadDataPackWriter.WrittenPack writtenPack = ReloadDataPackWriter.write(
                    server
            );
            reloadPackDirectory = writtenPack.directoryName();
            reloadPackResourcePaths = writtenPack.resourcePaths();
            reloadFailure = "";
            reloadRequested = true;
            lifecycle.add("reload_requested");
            LOGGER.info("[EtherologyServerProbe] reload_requested");
            reloadCommandResult = server.getCommandManager().executeWithPrefix(
                    server.getCommandSource(),
                    "reload"
            );
            enabledDataPackNamesAfterReload = server.getDataPackManager()
                    .getEnabledNames()
                    .stream()
                    .sorted()
                    .toList();
            enabledDataPacksExact = enabledDataPackNamesAfterReload.equals(
                    expectedEnabledDataPackNames()
            );
            reloadCompleted = reloadTagsObserved && tagUpdateCount == 2;
            lifecycle.add("reload_command_returned");
            LOGGER.info("[EtherologyServerProbe] reload_command_returned");
        } catch (IOException | RuntimeException exception) {
            reloadFailure = exception.getClass().getName();
        }
        lifecycle.add("stop_requested");
        stopRequestedAfterReload = reloadCompleted;
        stopRequestedWithoutRestart = true;
        LOGGER.info("[EtherologyServerProbe] stop_requested");
        server.stop(false);
    }

    private SlitheritePlacementSetup placeSlitheriteBlockItems(
            MinecraftServer server,
            ServerWorld world
    ) {
        ServerPlayerEntity player = new ServerPlayerEntity(
                server,
                world,
                new GameProfile(SLITHERITE_PLAYER_UUID, SLITHERITE_PLAYER_NAME)
        );
        player.changeGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
        player.refreshPositionAndAngles(64.5, 201.0, 20.5, 180.0F, 0.0F);
        Map<String, SlitheriteNativePlacementEntry> entries = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (int index = 0;
                index < SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS.size();
                index++) {
            String id = SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS.get(index);
            SlitheriteBlockProbeState.SlitheriteBlockSpec spec =
                    SlitheriteBlockProbeState.EXPECTED_BLOCKS.get(id);
            BlockPos position = slitheritePlacementPosition(index);
            BlockPos supportPosition = slitheriteSupportPosition(position, spec);
            try {
                forceSlitheriteChunk(world, position);
                Block block = registeredSlitheriteBlock(id);
                Item item = registeredSlitheriteItem(id);
                if (!(item instanceof BlockItem blockItem)) {
                    throw new IllegalStateException(id + " is not a BlockItem");
                }
                Block support = spec.button()
                        ? Blocks.SMOOTH_STONE
                        : Blocks.POLISHED_ANDESITE;
                world.setBlockState(
                        supportPosition,
                        support.getDefaultState(),
                        Block.NOTIFY_ALL
                );
                world.setBlockState(
                        position,
                        Blocks.AIR.getDefaultState(),
                        Block.NOTIFY_ALL
                );
                ItemStack stack = new ItemStack(item);
                int beforeCount = stack.getCount();
                player.setStackInHand(Hand.MAIN_HAND, stack);
                ActionResult result = blockItem.useOnBlock(
                        new ItemUsageContext(
                                player,
                                Hand.MAIN_HAND,
                                slitheritePlacementHit(position, supportPosition, spec)
                        )
                );
                BlockState placedState = world.getBlockState(position);
                entries.put(
                        id,
                        new SlitheriteNativePlacementEntry(
                                result.name(),
                                result.isAccepted(),
                                beforeCount,
                                stack.getCount(),
                                blockItem.getBlock() == block,
                                registeredBlockId(placedState.getBlock()),
                                BlockArgumentParser.stringifyBlockState(placedState)
                        )
                );
            } catch (RuntimeException exception) {
                entries.put(id, SlitheriteNativePlacementEntry.failed());
                errors.add(id + "=" + exception.getClass().getName());
            } finally {
                player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
        return new SlitheritePlacementSetup(
                new SlitheriteNativePlacementState(
                        String.join(",", errors),
                        Collections.unmodifiableMap(entries)
                ),
                player
        );
    }

    private void initializeSlitheriteBehaviorProbe(
            ServerWorld world,
            ServerPlayerEntity player
    ) {
        long now = world.getTime();
        slitheriteBehaviorSequence = new SlitheriteBehaviorSequence(
                SlitheriteBehaviorPhase.WAITING_FOR_ENTITY_TICKING,
                player,
                now + MAXIMUM_SLITHERITE_ENTITY_TICK_GATE_TICKS,
                -1L,
                false,
                false,
                false,
                null,
                -1,
                false,
                false,
                null,
                -1,
                false,
                false
        );
    }

    private void advanceSlitheriteBehaviorProbe(ServerWorld world) {
        SlitheriteBehaviorSequence sequence = slitheriteBehaviorSequence;
        if (sequence == null || slitheriteBehaviorProbe.completed()) {
            return;
        }
        BlockPos pressurePosition = slitheritePlacementPosition(9);
        boolean deadline = deadlineReached(world.getTime(), sequence.deadline());
        switch (sequence.phase()) {
            case WAITING_FOR_ENTITY_TICKING -> {
                boolean tickable = world.shouldTickEntity(pressurePosition);
                SlitheriteBehaviorPhase next = advanceSlitheriteBehaviorPhase(
                        sequence.phase(),
                        tickable,
                        deadline
                );
                if (next == SlitheriteBehaviorPhase.FAILED) {
                    failSlitheriteBehaviorProbe("entity_tick_gate_timeout");
                } else if (next == SlitheriteBehaviorPhase.WAITING_FOR_ITEM_TICK) {
                    startSlitheriteBehaviorProbe(world, sequence);
                }
            }
            case WAITING_FOR_ITEM_TICK -> advanceSlitheriteItemProbe(
                    world,
                    sequence,
                    deadline
            );
            case WAITING_FOR_LIVING_ACTIVATION -> advanceSlitheriteLivingProbe(
                    world,
                    sequence,
                    deadline
            );
            case WAITING_FOR_RESET -> finishSlitheriteBehaviorProbe(
                    world,
                    sequence,
                    deadline
            );
            case COMPLETE, FAILED -> {
            }
        }
    }

    private void startSlitheriteBehaviorProbe(
            ServerWorld world,
            SlitheriteBehaviorSequence sequence
    ) {
        BlockPos buttonPosition = slitheritePlacementPosition(8);
        Block button = registeredSlitheriteBlock(
                "etherology:polished_slitherite_button"
        );
        BlockState buttonBefore = world.getBlockState(buttonPosition);
        BlockHitResult buttonHit = new BlockHitResult(
                Vec3d.ofCenter(buttonPosition),
                Direction.NORTH,
                buttonPosition,
                false
        );
        ActionResult result = sequence.player().interactionManager.interactBlock(
                sequence.player(),
                world,
                ItemStack.EMPTY,
                Hand.MAIN_HAND,
                buttonHit
        );
        BlockState buttonAfter = world.getBlockState(buttonPosition);
        boolean buttonActivated = !buttonBefore.get(Properties.POWERED)
                && buttonAfter.get(Properties.POWERED);
        boolean buttonResetScheduled = world.getBlockTickScheduler().isQueued(
                buttonPosition,
                button
        );

        BlockPos pressurePosition = slitheritePlacementPosition(9);
        ItemEntity itemEntity = new ItemEntity(
                world,
                pressurePosition.getX() + 0.5,
                pressurePosition.getY() + 0.5,
                pressurePosition.getZ() + 0.5,
                new ItemStack(Items.COBBLESTONE)
        );
        itemEntity.setVelocity(0.0, -0.3, 0.0);
        itemEntity.setOnGround(false);
        itemEntity.setNoGravity(false);
        itemEntity.setPickupDelayInfinite();
        if (!world.spawnEntity(itemEntity)) {
            throw new IllegalStateException("Cannot spawn Slitherite item probe");
        }
        long now = world.getTime();
        slitheriteBehaviorSequence = new SlitheriteBehaviorSequence(
                SlitheriteBehaviorPhase.WAITING_FOR_ITEM_TICK,
                sequence.player(),
                now + MAXIMUM_SLITHERITE_PROBE_ENTITY_TICK_TICKS,
                now,
                result.isAccepted(),
                buttonActivated,
                buttonResetScheduled,
                itemEntity,
                itemEntity.age,
                false,
                false,
                null,
                -1,
                false,
                false
        );
    }

    private void advanceSlitheriteItemProbe(
            ServerWorld world,
            SlitheriteBehaviorSequence sequence,
            boolean deadline
    ) {
        boolean itemAdvanced = serverEntityAdvanced(
                world,
                sequence.itemEntity(),
                sequence.itemInitialAge()
        );
        SlitheriteBehaviorPhase next = advanceSlitheriteBehaviorPhase(
                sequence.phase(),
                itemAdvanced,
                deadline
        );
        if (next == SlitheriteBehaviorPhase.FAILED) {
            failSlitheriteBehaviorProbe("item_entity_tick_timeout");
            return;
        }
        if (next != SlitheriteBehaviorPhase.WAITING_FOR_LIVING_ACTIVATION) {
            return;
        }
        BlockPos pressurePosition = slitheritePlacementPosition(9);
        boolean itemIgnored = !world.getBlockState(pressurePosition)
                .get(Properties.POWERED);
        sequence.itemEntity().discard();
        PigEntity pigEntity = EntityType.PIG.create(world);
        if (pigEntity == null) {
            throw new IllegalStateException("Cannot create Slitherite living probe");
        }
        pigEntity.setInvulnerable(true);
        pigEntity.refreshPositionAndAngles(
                pressurePosition.getX() + 0.5,
                pressurePosition.getY() + 0.5,
                pressurePosition.getZ() + 0.5,
                0.0F,
                0.0F
        );
        pigEntity.setVelocity(0.0, -0.3, 0.0);
        pigEntity.setOnGround(false);
        pigEntity.setNoGravity(false);
        if (!world.spawnEntity(pigEntity)) {
            throw new IllegalStateException("Cannot spawn Slitherite living probe");
        }
        long now = world.getTime();
        slitheriteBehaviorSequence = new SlitheriteBehaviorSequence(
                SlitheriteBehaviorPhase.WAITING_FOR_LIVING_ACTIVATION,
                sequence.player(),
                now + MAXIMUM_SLITHERITE_LIVING_ACTIVATION_TICKS,
                sequence.buttonActivationTick(),
                sequence.buttonInteractionAccepted(),
                sequence.buttonActivated(),
                sequence.buttonResetScheduled(),
                sequence.itemEntity(),
                sequence.itemInitialAge(),
                true,
                itemIgnored,
                pigEntity,
                pigEntity.age,
                false,
                false
        );
    }

    private void advanceSlitheriteLivingProbe(
            ServerWorld world,
            SlitheriteBehaviorSequence sequence,
            boolean deadline
    ) {
        boolean livingAdvanced = serverEntityAdvanced(
                world,
                sequence.pigEntity(),
                sequence.pigInitialAge()
        );
        boolean livingActivated = world.getBlockState(
                slitheritePlacementPosition(9)
        ).get(Properties.POWERED);
        if (deadline && !livingAdvanced) {
            failSlitheriteBehaviorProbe("living_entity_tick_timeout");
            return;
        }
        SlitheriteBehaviorPhase next = advanceSlitheriteBehaviorPhase(
                sequence.phase(),
                livingAdvanced && livingActivated,
                deadline
        );
        if (next != SlitheriteBehaviorPhase.WAITING_FOR_RESET) {
            return;
        }
        sequence.pigEntity().discard();
        long now = world.getTime();
        slitheriteBehaviorSequence = new SlitheriteBehaviorSequence(
                SlitheriteBehaviorPhase.WAITING_FOR_RESET,
                sequence.player(),
                now + SLITHERITE_PRESSURE_PLATE_RESET_TICKS,
                sequence.buttonActivationTick(),
                sequence.buttonInteractionAccepted(),
                sequence.buttonActivated(),
                sequence.buttonResetScheduled(),
                sequence.itemEntity(),
                sequence.itemInitialAge(),
                sequence.itemEntityAdvanced(),
                sequence.itemIgnored(),
                sequence.pigEntity(),
                sequence.pigInitialAge(),
                livingAdvanced,
                livingActivated
        );
    }

    private void finishSlitheriteBehaviorProbe(
            ServerWorld world,
            SlitheriteBehaviorSequence sequence,
            boolean deadline
    ) {
        SlitheriteBehaviorPhase next = advanceSlitheriteBehaviorPhase(
                sequence.phase(),
                false,
                deadline
        );
        if (next != SlitheriteBehaviorPhase.COMPLETE) {
            return;
        }
        boolean buttonReset = !world.getBlockState(
                slitheritePlacementPosition(8)
        ).get(Properties.POWERED);
        boolean pressureReset = !world.getBlockState(
                slitheritePlacementPosition(9)
        ).get(Properties.POWERED);
        long elapsed = world.getTime() - sequence.buttonActivationTick();
        SlitheriteBlockProbeState.PlacementState resetPlacement =
                SlitheriteBlockProbeState.capturePlacement(world);
        slitheriteBehaviorProbe = SlitheriteBehaviorProbe.complete(
                sequence,
                buttonReset,
                elapsed,
                pressureReset,
                resetPlacement.hasExactPlacement()
        );
        slitheriteBehaviorSequence = sequence.withPhase(
                SlitheriteBehaviorPhase.COMPLETE
        );
    }

    private void failSlitheriteBehaviorProbe(String failure) {
        discardSlitheriteProbeEntities();
        slitheriteBehaviorProbe = SlitheriteBehaviorProbe.failed(failure);
        if (slitheriteBehaviorSequence != null) {
            slitheriteBehaviorSequence = slitheriteBehaviorSequence.withPhase(
                    SlitheriteBehaviorPhase.FAILED
            );
        }
    }

    private void discardSlitheriteProbeEntities() {
        if (slitheriteBehaviorSequence == null) {
            return;
        }
        if (slitheriteBehaviorSequence.itemEntity() != null) {
            slitheriteBehaviorSequence.itemEntity().discard();
        }
        if (slitheriteBehaviorSequence.pigEntity() != null) {
            slitheriteBehaviorSequence.pigEntity().discard();
        }
    }

    static SlitheriteBehaviorPhase advanceSlitheriteBehaviorPhase(
            SlitheriteBehaviorPhase phase,
            boolean milestoneReached,
            boolean deadlineReached
    ) {
        return switch (phase) {
            case WAITING_FOR_ENTITY_TICKING -> milestoneReached
                    ? SlitheriteBehaviorPhase.WAITING_FOR_ITEM_TICK
                    : deadlineReached ? SlitheriteBehaviorPhase.FAILED : phase;
            case WAITING_FOR_ITEM_TICK -> milestoneReached
                    ? SlitheriteBehaviorPhase.WAITING_FOR_LIVING_ACTIVATION
                    : deadlineReached ? SlitheriteBehaviorPhase.FAILED : phase;
            case WAITING_FOR_LIVING_ACTIVATION ->
                    milestoneReached || deadlineReached
                            ? SlitheriteBehaviorPhase.WAITING_FOR_RESET
                            : phase;
            case WAITING_FOR_RESET -> deadlineReached
                    ? SlitheriteBehaviorPhase.COMPLETE
                    : phase;
            case COMPLETE, FAILED -> phase;
        };
    }

    static boolean entityTickAdvanced(
            boolean serverLookupExact,
            int initialAge,
            int currentAge
    ) {
        return serverLookupExact && currentAge > initialAge;
    }

    static boolean deadlineReached(long currentTick, long deadline) {
        return currentTick >= deadline;
    }

    private static boolean serverEntityAdvanced(
            ServerWorld world,
            Entity entity,
            int initialAge
    ) {
        return entity != null && entityTickAdvanced(
                world.getEntityById(entity.getId()) == entity,
                initialAge,
                entity.age
        );
    }

    private static void forceSlitheriteChunk(ServerWorld world, BlockPos position) {
        world.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true);
    }

    private static Block registeredSlitheriteBlock(String id) {
        return Registries.BLOCK.getOrEmpty(Identifier.parse(id)).orElseThrow(
                () -> new IllegalStateException("Missing block " + id)
        );
    }

    private static Item registeredSlitheriteItem(String id) {
        return Registries.ITEM.getOrEmpty(Identifier.parse(id)).orElseThrow(
                () -> new IllegalStateException("Missing item " + id)
        );
    }

    private static String registeredBlockId(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        return id == null ? "" : id.toString();
    }

    private static BlockPos slitheritePlacementPosition(int index) {
        return new BlockPos(64 + index * 2, 200, 24);
    }

    private static BlockPos slitheriteSupportPosition(
            BlockPos position,
            SlitheriteBlockProbeState.SlitheriteBlockSpec spec
    ) {
        return spec.button() ? position.south() : position.down();
    }

    private static BlockHitResult slitheritePlacementHit(
            BlockPos position,
            BlockPos supportPosition,
            SlitheriteBlockProbeState.SlitheriteBlockSpec spec
    ) {
        if (spec.button()) {
            return new BlockHitResult(
                    Vec3d.ofCenter(supportPosition).add(0.0, 0.0, -0.5),
                    Direction.NORTH,
                    supportPosition,
                    false
            );
        }
        return new BlockHitResult(
                Vec3d.ofCenter(supportPosition).add(0.0, 0.5, 0.0),
                Direction.UP,
                supportPosition,
                false
        );
    }

    /**
     * Records that the stop request reached Forge's pre-stop lifecycle phase.
     *
     * @param event the stopping dedicated server
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        lifecycle.add("server_stopping");
        stoppingServerMatched = event.getServer() == startedServer;
        LOGGER.info("[EtherologyServerProbe] server_stopping");
    }

    /**
     * Publishes the complete report after Forge records the terminal lifecycle phase.
     *
     * @param event the stopped dedicated server
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        lifecycle.add("server_stopped");
        stoppedServerMatched = event.getServer() == startedServer;
        loadedModIds = collectLoadedModIds();
        forbiddenModIdsLoaded = ServerProbeModInventory.sortedIntersection(
                loadedModIds,
                forbiddenModIds
        );
        serverStartedModsRechecked = serverStartedModsRechecked
                && loadedModIds.equals(serverStartedLoadedModIds)
                && forbiddenModIdsLoaded.isEmpty();
        LOGGER.info("[EtherologyServerProbe] server_stopped");
        JsonObject report;
        try {
            report = buildReport();
            ServerProbeReportWriter.write(evidenceRoot, report);
        } catch (IOException | RuntimeException exception) {
            scheduleProcessExit(
                    Thread.currentThread(),
                    ServerProbeProcessTerminator.FAILURE_EXIT_STATUS
            );
            throw new IllegalStateException(
                    "The server-probe report could not be published",
                    exception
            );
        }
        LOGGER.info("[EtherologyServerProbe] report_published");
        scheduleProcessExit(
                Thread.currentThread(),
                ServerProbeProcessTerminator.exitStatusForReport(report)
        );
    }

    private JsonObject buildReport() {
        String distribution = FMLEnvironment.dist.name();
        boolean etherologyLoaded = loadedModIds.contains("etherology");
        boolean probeLoaded = loadedModIds.contains(MOD_ID);
        boolean lifecycleIdentity = stoppingServerMatched && stoppedServerMatched;
        String lifecycleActual = String.join(">", lifecycle);

        JsonArray assertions = new JsonArray();
        addAssertion(
                assertions,
                "distribution_dedicated_server",
                "DEDICATED_SERVER",
                distribution
        );
        addAssertion(
                assertions,
                "runtime_kind_loom_userdev",
                "loom-userdev",
                runtimeKind
        );
        addAssertion(
                assertions,
                "mod_loaded:etherology",
                "loaded",
                loadedState(etherologyLoaded)
        );
        addAssertion(
                assertions,
                "mod_loaded:" + MOD_ID,
                "loaded",
                loadedState(probeLoaded)
        );
        forbiddenModIds.forEach(forbiddenModId -> addAssertion(
                assertions,
                "mod_absent:" + forbiddenModId,
                "absent",
                loadedModIds.contains(forbiddenModId) ? "loaded" : "absent"
        ));
        addAssertion(
                assertions,
                "mods_forbidden_intersection_empty",
                "none",
                forbiddenModIdsLoaded.isEmpty()
                        ? "none"
                        : String.join(",", forbiddenModIdsLoaded)
        );
        addAssertion(
                assertions,
                "registry:game_event:etherology:etherology_resonance",
                "present",
                presentState(taggedEvent != null)
        );
        addAssertion(
                assertions,
                "registry:game_event_etherology_ids_exact",
                EVENT_ID.toString(),
                String.join(",", registryEtherologyEventIds)
        );
        addAssertion(
                assertions,
                "registry_internal_id",
                INTERNAL_EVENT_ID,
                internalEventId
        );
        addAssertion(assertions, "registry_range", Integer.toString(EVENT_RANGE),
                Integer.toString(eventRange));
        addAssertion(
                assertions,
                "registry:enchantment:etherology:peal",
                "present",
                presentState(initialEnchantmentState.pealIdentity() != null)
        );
        addAssertion(
                assertions,
                "registry:enchantment:etherology:reflection",
                "present",
                presentState(initialEnchantmentState.reflectionIdentity() != null)
        );
        addAssertion(
                assertions,
                "registry:enchantment_etherology_ids_exact",
                String.join(",", EnchantmentProbeState.EXPECTED_ENCHANTMENT_IDS),
                String.join(",", initialEnchantmentState.etherologyEnchantmentIds())
        );
        addAssertion(
                assertions,
                "enchantment:peal_class",
                EnchantmentProbeState.PEAL_CLASS,
                initialEnchantmentState.pealClass()
        );
        addAssertion(
                assertions,
                "enchantment:reflection_class",
                EnchantmentProbeState.REFLECTION_CLASS,
                initialEnchantmentState.reflectionClass()
        );
        addAssertion(
                assertions,
                "enchantment:peal_max_level",
                "3",
                Integer.toString(initialEnchantmentState.pealMaxLevel())
        );
        addAssertion(
                assertions,
                "enchantment:peal_min_power_level_1",
                "1",
                initialEnchantmentState.pealMinPower(1)
        );
        addAssertion(
                assertions,
                "enchantment:peal_min_power_level_2",
                "12",
                initialEnchantmentState.pealMinPower(2)
        );
        addAssertion(
                assertions,
                "enchantment:peal_min_power_level_3",
                "23",
                initialEnchantmentState.pealMinPower(3)
        );
        addAssertion(
                assertions,
                "enchantment:peal_max_power_level_1",
                "21",
                initialEnchantmentState.pealMaxPower(1)
        );
        addAssertion(
                assertions,
                "enchantment:peal_max_power_level_2",
                "32",
                initialEnchantmentState.pealMaxPower(2)
        );
        addAssertion(
                assertions,
                "enchantment:peal_max_power_level_3",
                "43",
                initialEnchantmentState.pealMaxPower(3)
        );
        addAssertion(
                assertions,
                "enchantment:reflection_max_level",
                "1",
                Integer.toString(initialEnchantmentState.reflectionMaxLevel())
        );
        addAssertion(
                assertions,
                "enchantment:reflection_min_power_level_1",
                "1",
                initialEnchantmentState.reflectionMinPower(1)
        );
        addAssertion(
                assertions,
                "enchantment:reflection_max_power_level_1",
                "21",
                initialEnchantmentState.reflectionMaxPower(1)
        );
        addBooleanAssertion(
                assertions,
                "tag:enchantment_non_treasure_contains_peal",
                initialEnchantmentState.pealInNonTreasure()
        );
        addBooleanAssertion(
                assertions,
                "tag:enchantment_non_treasure_contains_reflection",
                initialEnchantmentState.reflectionInNonTreasure()
        );
        addAssertion(
                assertions,
                "tag:enchantment_non_treasure_etherology_entries_exact",
                String.join(",", EnchantmentProbeState.EXPECTED_ENCHANTMENT_IDS),
                String.join(",", initialEnchantmentState
                        .nonTreasureEtherologyEnchantmentIds())
        );
        addBooleanAssertion(
                assertions,
                "enchantments_captured_after_server_data_load",
                enchantmentsCapturedAfterServerDataLoad
        );
        addBooleanAssertion(
                assertions,
                "server_started_enchantments_rechecked",
                serverStartedEnchantmentsRechecked
        );
        addBooleanAssertion(
                assertions,
                "enchantment_registry_stable_after_reload",
                enchantmentRegistryStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "enchantment_properties_stable_after_reload",
                enchantmentPropertiesStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "enchantment_tag_stable_after_reload",
                enchantmentTagStableAfterReload
        );
        ParticleProbeState.EXPECTED_PARTICLES.forEach((path, spec) -> addAssertion(
                assertions,
                "registry:particle_type:" + spec.id(),
                "present",
                presentState(
                        initialParticleState.entries().containsKey(path)
                                && initialParticleState.entries()
                                .get(path)
                                .typeIdentity() != null
                )
        ));
        addAssertion(
                assertions,
                "registry:particle_type_etherology_ids_exact",
                String.join(",", ParticleProbeState.EXPECTED_PARTICLE_IDS),
                String.join(",", initialParticleState.etherologyParticleIds())
        );
        addAssertion(
                assertions,
                "particle_capture_error",
                "none",
                errorState(initialParticleState.captureError())
        );
        addAssertion(
                assertions,
                "particle_payload_families_exact",
                String.join(",", ParticleProbeState.EXPECTED_PAYLOAD_FAMILIES),
                String.join(",", initialParticleState.payloadFamilies())
        );
        addBooleanAssertion(
                assertions,
                "particle_type_classes_exact",
                initialParticleState.hasExactTypeClasses()
        );
        addBooleanAssertion(
                assertions,
                "particle_should_always_spawn_false_exact",
                initialParticleState.hasExactAlwaysSpawnPolicy()
        );
        addBooleanAssertion(
                assertions,
                "particle_codecs_present_exact",
                initialParticleState.hasAllCodecs()
        );
        addBooleanAssertion(
                assertions,
                "particle_parameters_factories_present_exact",
                initialParticleState.hasAllParametersFactories()
        );
        addBooleanAssertion(
                assertions,
                "particle_factory_sample_effect_classes_exact",
                initialParticleState.hasExactFactorySampleEffectClasses()
        );
        addBooleanAssertion(
                assertions,
                "particle_factory_sample_types_exact",
                initialParticleState.hasExactFactorySampleTypes()
        );
        addBooleanAssertion(
                assertions,
                "particle_factory_sample_as_strings_exact",
                initialParticleState.hasExactFactorySampleStrings()
        );
        addBooleanAssertion(
                assertions,
                "particle_packet_round_trips_exact",
                initialParticleState.hasExactPacketRoundTrips()
        );
        addBooleanAssertion(
                assertions,
                "particle_codec_round_trips_exact",
                initialParticleState.hasExactCodecRoundTrips()
        );
        addBooleanAssertion(
                assertions,
                "seal_type_order_exact",
                initialParticleState.hasExactSealTypeOrder()
        );
        addBooleanAssertion(
                assertions,
                "seal_type_codec_round_trips_exact",
                initialParticleState.hasExactSealTypeCodec()
        );
        addBooleanAssertion(
                assertions,
                "seal_type_colors_exact",
                initialParticleState.hasExactSealTypeColors()
        );
        addBooleanAssertion(
                assertions,
                "seal_type_textures_exact",
                initialParticleState.hasExactSealTypeTextures()
        );
        addBooleanAssertion(
                assertions,
                "particles_captured_after_server_data_load",
                particlesCapturedAfterServerDataLoad
        );
        addBooleanAssertion(
                assertions,
                "server_started_particles_rechecked",
                serverStartedParticlesRechecked
        );
        addBooleanAssertion(
                assertions,
                "particle_registry_stable_after_reload",
                particleRegistryStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "particle_type_contract_stable_after_reload",
                particleTypeContractStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "particle_wire_contract_stable_after_reload",
                particleWireContractStableAfterReload
        );
        MaterialItemProbeState.EXPECTED_ITEM_IDS.forEach(id -> addAssertion(
                assertions,
                "registry:item:" + id,
                "present",
                presentState(
                        initialMaterialItemState.entries().containsKey(id)
                                && initialMaterialItemState.entries()
                                .get(id)
                                .itemIdentity() != null
                )
        ));
        addAssertion(
                assertions,
                "registry:material_item_ids_exact",
                String.join(",", MaterialItemProbeState.EXPECTED_ITEM_IDS),
                String.join(",", initialMaterialItemState.materialItemIds())
        );
        addAssertion(
                assertions,
                "material_item_capture_error",
                "none",
                errorState(initialMaterialItemState.captureError())
        );
        addAssertion(
                assertions,
                "material_item_runtime_class_exact",
                MaterialItemProbeState.VANILLA_ITEM_CLASS,
                initialMaterialItemState.runtimeClassSummary()
        );
        addAssertion(
                assertions,
                "material_item_max_counts_exact",
                MaterialItemProbeState.expectedCanonicalMaxCounts(),
                initialMaterialItemState.canonicalMaxCounts()
        );
        addBooleanAssertion(
                assertions,
                "material_item_stack_nbt_round_trips_exact",
                initialMaterialItemState.hasExactStackNbtRoundTrips()
        );
        addAssertion(
                assertions,
                "material_item_save_representations_exact",
                MaterialItemProbeState.expectedCanonicalSaveRepresentations(),
                initialMaterialItemState.canonicalSaveRepresentations()
        );
        addBooleanAssertion(
                assertions,
                "material_items_captured_after_server_data_load",
                materialItemsCapturedAfterServerDataLoad
        );
        addBooleanAssertion(
                assertions,
                "server_started_material_items_rechecked",
                serverStartedMaterialItemsRechecked
        );
        addBooleanAssertion(
                assertions,
                "material_item_registry_stable_after_reload",
                materialItemRegistryStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "material_item_properties_stable_after_reload",
                materialItemPropertiesStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "material_item_stack_nbt_stable_after_reload",
                materialItemStackNbtStableAfterReload
        );
        MetalBlockProbeState.EXPECTED_BLOCK_IDS.forEach(id -> {
            MetalBlockProbeState.MetalBlockEntry entry =
                    initialMetalBlockState.entries().get(id);
            addAssertion(
                    assertions,
                    "registry:block:" + id,
                    "present",
                    presentState(entry != null && entry.blockIdentity() != null)
            );
            addAssertion(
                    assertions,
                    "registry:block_item:" + id,
                    "present",
                    presentState(entry != null && entry.itemIdentity() != null)
            );
        });
        addAssertion(
                assertions,
                "registry:metal_block_ids_exact",
                String.join(",", MetalBlockProbeState.EXPECTED_BLOCK_IDS),
                String.join(",", initialMetalBlockState.metalBlockIds())
        );
        addAssertion(
                assertions,
                "registry:metal_block_item_ids_exact",
                String.join(",", MetalBlockProbeState.EXPECTED_BLOCK_IDS),
                String.join(",", initialMetalBlockState.metalBlockItemIds())
        );
        addAssertion(
                assertions,
                "metal_block_capture_error",
                "none",
                errorState(initialMetalBlockState.captureError())
        );
        addBooleanAssertion(
                assertions,
                "metal_block_runtime_classes_exact",
                initialMetalBlockState.hasExactRuntimeClasses()
        );
        addBooleanAssertion(
                assertions,
                "metal_block_item_mappings_exact",
                initialMetalBlockState.hasExactBlockItemMappings()
        );
        addAssertion(
                assertions,
                "metal_block_properties_exact",
                MetalBlockProbeState.expectedCanonicalProperties(),
                initialMetalBlockState.canonicalProperties()
        );
        addBooleanAssertion(
                assertions,
                "metal_block_tags_exact",
                initialMetalBlockState.hasExactTagMemberships()
        );
        addBooleanAssertion(
                assertions,
                "metal_block_stack_nbt_round_trips_exact",
                initialMetalBlockState.hasExactStackNbtRoundTrips()
        );
        addAssertion(
                assertions,
                "metal_block_save_representations_exact",
                MetalBlockProbeState.expectedCanonicalSaveRepresentations(),
                initialMetalBlockState.canonicalSaveRepresentations()
        );
        addBooleanAssertion(
                assertions,
                "metal_blocks_captured_after_server_data_load",
                metalBlocksCapturedAfterServerDataLoad
        );
        addBooleanAssertion(
                assertions,
                "server_started_metal_blocks_rechecked",
                serverStartedMetalBlocksRechecked
        );
        addAssertion(
                assertions,
                "metal_block_placement_positions_exact",
                MetalBlockProbeState.MetalBlockPlacementState
                        .expectedCanonicalPositions(),
                initialMetalBlockPlacement.canonicalPositions()
        );
        addAssertion(
                assertions,
                "metal_block_placed_ids_exact",
                MetalBlockProbeState.MetalBlockPlacementState
                        .expectedCanonicalPlacedBlockIds(),
                initialMetalBlockPlacement.canonicalPlacedBlockIds()
        );
        addBooleanAssertion(
                assertions,
                "metal_block_placement_exact",
                initialMetalBlockPlacement.hasExactPlacement()
        );
        addBooleanAssertion(
                assertions,
                "metal_block_registry_stable_after_reload",
                metalBlockRegistryStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "metal_block_properties_stable_after_reload",
                metalBlockPropertiesStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "metal_block_tags_stable_after_reload",
                metalBlockTagsStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "metal_block_stack_nbt_stable_after_reload",
                metalBlockStackNbtStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "metal_block_placement_stable_after_reload",
                metalBlockPlacementStableAfterReload
        );
        AttrahiteBlockProbeState.EXPECTED_BLOCK_IDS.forEach(id -> {
            AttrahiteBlockProbeState.AttrahiteBlockEntry entry =
                    initialAttrahiteBlockState.entries().get(id);
            addAssertion(
                    assertions,
                    "registry:block:" + id,
                    "present",
                    presentState(entry != null && entry.blockIdentity() != null)
            );
            addAssertion(
                    assertions,
                    "registry:block_item:" + id,
                    "present",
                    presentState(entry != null && entry.itemIdentity() != null)
            );
        });
        addAssertion(
                assertions,
                "registry:attrahite_block_ids_exact",
                String.join(",", AttrahiteBlockProbeState.EXPECTED_BLOCK_IDS),
                String.join(",", initialAttrahiteBlockState.blockIds())
        );
        addAssertion(
                assertions,
                "registry:attrahite_block_item_ids_exact",
                String.join(",", AttrahiteBlockProbeState.EXPECTED_BLOCK_IDS),
                String.join(",", initialAttrahiteBlockState.blockItemIds())
        );
        addAssertion(
                assertions,
                "attrahite_block_capture_error",
                "none",
                errorState(initialAttrahiteBlockState.captureError())
        );
        addBooleanAssertion(
                assertions,
                "attrahite_block_runtime_classes_exact",
                initialAttrahiteBlockState.hasExactRuntimeClasses()
        );
        addBooleanAssertion(
                assertions,
                "attrahite_block_item_mappings_exact",
                initialAttrahiteBlockState.hasExactBlockItemMappings()
        );
        addAssertion(
                assertions,
                "attrahite_block_properties_exact",
                AttrahiteBlockProbeState.expectedCanonicalProperties(),
                initialAttrahiteBlockState.canonicalProperties()
        );
        addAssertion(
                assertions,
                "attrahite_block_tags_exact",
                AttrahiteBlockProbeState.expectedCanonicalTags(),
                initialAttrahiteBlockState.canonicalTags()
        );
        addBooleanAssertion(
                assertions,
                "attrahite_block_stack_nbt_round_trips_exact",
                initialAttrahiteBlockState.hasExactStackNbtRoundTrips()
        );
        addAssertion(
                assertions,
                "attrahite_block_save_representations_exact",
                AttrahiteBlockProbeState.expectedCanonicalSaveRepresentations(),
                initialAttrahiteBlockState.canonicalSaveRepresentations()
        );
        addBooleanAssertion(
                assertions,
                "attrahite_blocks_captured_at_initial_tag_load",
                attrahiteBlocksCapturedAtInitialTagLoad
        );
        addBooleanAssertion(
                assertions,
                "attrahite_blocks_captured_after_server_data_load",
                attrahiteBlocksCapturedAfterServerDataLoad
        );
        addBooleanAssertion(
                assertions,
                "server_started_attrahite_blocks_rechecked",
                serverStartedAttrahiteBlocksRechecked
        );
        addAssertion(
                assertions,
                "attrahite_block_placement_positions_exact",
                AttrahiteBlockProbeState.PlacementState.expectedCanonicalPositions(),
                initialAttrahiteBlockPlacement.canonicalPositions()
        );
        addAssertion(
                assertions,
                "attrahite_block_placed_ids_exact",
                AttrahiteBlockProbeState.PlacementState
                        .expectedCanonicalPlacedBlockIds(),
                initialAttrahiteBlockPlacement.canonicalPlacedBlockIds()
        );
        addAssertion(
                assertions,
                "attrahite_block_placed_states_exact",
                AttrahiteBlockProbeState.PlacementState.expectedCanonicalPlacedStates(),
                initialAttrahiteBlockPlacement.canonicalPlacedStates()
        );
        addBooleanAssertion(
                assertions,
                "attrahite_block_placement_exact",
                initialAttrahiteBlockPlacement.hasExactPlacement()
        );
        addAssertion(
                assertions,
                "attrahite_world_save_failure",
                "none",
                errorState(attrahiteWorldSaveFailure)
        );
        addBooleanAssertion(
                assertions,
                "attrahite_world_saved_after_placement",
                attrahiteWorldSavedAfterPlacement
        );
        addAssertion(
                assertions,
                "attrahite_loaded_data_capture_error",
                "none",
                errorState(initialAttrahiteBlockState.loadedData().captureError())
        );
        addAssertion(
                assertions,
                "attrahite_loot_table_ids_exact",
                String.join(",", AttrahiteBlockProbeState.LoadedData
                        .EXPECTED_LOOT_TABLE_IDS),
                String.join(",", initialAttrahiteBlockState.loadedData().lootTableIds())
        );
        addAssertion(
                assertions,
                "attrahite_standard_loot_exact",
                AttrahiteBlockProbeState.LoadedData.expectedCanonicalStandardLoot(),
                initialAttrahiteBlockState.loadedData().canonicalStandardLoot()
        );
        addAssertion(
                assertions,
                "attrahite_raw_silk_touch_loot_exact",
                AttrahiteBlockProbeState.LoadedData.EXPECTED_RAW_SILK_TOUCH_LOOT,
                initialAttrahiteBlockState.loadedData().rawSilkTouchLoot()
        );
        addAssertion(
                assertions,
                "attrahite_raw_fortune_scaled_loot_exact",
                AttrahiteBlockProbeState.LoadedData.expectedCanonicalRawFortuneLoot(),
                initialAttrahiteBlockState.loadedData().canonicalRawFortuneLoot()
        );
        addAssertion(
                assertions,
                "attrahite_recipe_ids_exact",
                String.join(",", AttrahiteBlockProbeState.LoadedData.EXPECTED_RECIPE_IDS),
                String.join(",", initialAttrahiteBlockState.loadedData().recipeIds())
        );
        addAssertion(
                assertions,
                "attrahite_recipes_exact",
                AttrahiteBlockProbeState.LoadedData.expectedCanonicalRecipes(),
                initialAttrahiteBlockState.loadedData().canonicalRecipes()
        );
        addBooleanAssertion(
                assertions,
                "attrahite_recipes_match_and_craft_exact",
                initialAttrahiteBlockState.loadedData().recipeMatchesAndCraftsExact()
        );
        addAssertion(
                assertions,
                "attrahite_advancement_ids_exact",
                String.join(",", AttrahiteBlockProbeState.LoadedData
                        .EXPECTED_ADVANCEMENT_IDS),
                String.join(",", initialAttrahiteBlockState.loadedData().advancementIds())
        );
        addAssertion(
                assertions,
                "attrahite_advancements_exact",
                AttrahiteBlockProbeState.LoadedData.expectedCanonicalAdvancements(),
                initialAttrahiteBlockState.loadedData().canonicalAdvancements()
        );
        addBooleanAssertion(
                assertions,
                "attrahite_loaded_data_contract_exact",
                initialAttrahiteBlockState.loadedData().hasExactContract()
        );
        addBooleanAssertion(
                assertions,
                "attrahite_block_registry_stable_after_reload",
                attrahiteBlockRegistryStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "attrahite_block_properties_stable_after_reload",
                attrahiteBlockPropertiesStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "attrahite_block_tags_stable_after_reload",
                attrahiteBlockTagsStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "attrahite_block_stack_nbt_stable_after_reload",
                attrahiteBlockStackNbtStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "attrahite_loaded_data_stable_after_reload",
                attrahiteBlockLoadedDataStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "attrahite_loaded_data_fresh_after_reload",
                attrahiteBlockLoadedDataFreshAfterReload
        );
        addBooleanAssertion(
                assertions,
                "attrahite_block_placement_stable_after_reload",
                attrahiteBlockPlacementStableAfterReload
        );
        addSlitheriteAssertions(assertions);
        addAssertion(
                assertions,
                "registry:item:" + FoodItemProbeState.ITEM_ID,
                "present",
                presentState(
                        initialFoodItemState.entries()
                                .getOrDefault(
                                        FoodItemProbeState.ITEM_ID,
                                        FoodItemProbeState.FoodItemEntry.failed()
                                )
                                .itemIdentity() != null
                )
        );
        addAssertion(
                assertions,
                "registry:food_item_ids_exact",
                String.join(",", FoodItemProbeState.EXPECTED_ITEM_IDS),
                String.join(",", initialFoodItemState.foodItemIds())
        );
        addAssertion(
                assertions,
                "food_item_capture_error",
                "none",
                errorState(initialFoodItemState.captureError())
        );
        addAssertion(
                assertions,
                "food_item_runtime_class_exact",
                FoodItemProbeState.VANILLA_ITEM_CLASS,
                initialFoodItemState.runtimeClassSummary()
        );
        addAssertion(
                assertions,
                "food_item_properties_exact",
                FoodItemProbeState.expectedCanonicalProperties(),
                initialFoodItemState.canonicalProperties()
        );
        addBooleanAssertion(
                assertions,
                "food_item_stack_nbt_round_trip_exact",
                initialFoodItemState.hasExactStackNbtRoundTrip()
        );
        addAssertion(
                assertions,
                "food_item_save_representation_exact",
                FoodItemProbeState.expectedCanonicalSaveRepresentations(),
                initialFoodItemState.canonicalSaveRepresentations()
        );
        addBooleanAssertion(
                assertions,
                "food_item_contract_exact",
                initialFoodItemState.hasExactContract()
        );
        addBooleanAssertion(
                assertions,
                "food_items_captured_after_server_data_load",
                foodItemsCapturedAfterServerDataLoad
        );
        addBooleanAssertion(
                assertions,
                "server_started_food_items_rechecked",
                serverStartedFoodItemsRechecked
        );
        addBooleanAssertion(
                assertions,
                "food_item_registry_stable_after_reload",
                foodItemRegistryStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "food_item_properties_stable_after_reload",
                foodItemPropertiesStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "food_item_stack_nbt_stable_after_reload",
                foodItemStackNbtStableAfterReload
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_capture_error",
                "none",
                errorState(serverStartedFoodConsumption.captureError())
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_player_class",
                FoodItemProbeState.FoodConsumptionState.PLAYER_CLASS,
                serverStartedFoodConsumption.playerClass()
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_player_uuid",
                FoodItemProbeState.ConsumptionPhase.SERVER_STARTED.uuid().toString(),
                serverStartedFoodConsumption.playerUuid()
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_player_name",
                FoodItemProbeState.ConsumptionPhase.SERVER_STARTED.playerName(),
                serverStartedFoodConsumption.playerName()
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_item_id",
                FoodItemProbeState.ITEM_ID,
                serverStartedFoodConsumption.itemId()
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_result_item_id",
                FoodItemProbeState.ITEM_ID,
                serverStartedFoodConsumption.resultItemId()
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_initial_hunger",
                "10",
                Integer.toString(serverStartedFoodConsumption.initialHunger())
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_initial_saturation",
                "0.0",
                Float.toString(serverStartedFoodConsumption.initialSaturation())
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_initial_stack_count",
                "2",
                Integer.toString(serverStartedFoodConsumption.initialStackCount())
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_result_hunger",
                "13",
                Integer.toString(serverStartedFoodConsumption.resultHunger())
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_result_saturation",
                "12.0",
                Float.toString(serverStartedFoodConsumption.resultSaturation())
        );
        addAssertion(
                assertions,
                "server_started_food_consumption_result_stack_count",
                "1",
                Integer.toString(serverStartedFoodConsumption.resultStackCount())
        );
        addBooleanAssertion(
                assertions,
                "server_started_food_consumption_same_stack_instance",
                serverStartedFoodConsumption.sameStackInstance()
        );
        addBooleanAssertion(
                assertions,
                "server_started_food_consumption_exact",
                serverStartedFoodConsumption.hasExactConsumption()
        );
        addAssertion(
                assertions,
                "reloaded_food_consumption_capture_error",
                "none",
                errorState(reloadedFoodConsumption.captureError())
        );
        addBooleanAssertion(
                assertions,
                "reloaded_food_consumption_exact",
                reloadedFoodConsumption.hasExactConsumption()
        );
        addBooleanAssertion(
                assertions,
                "food_consumption_fresh_player_after_reload",
                foodConsumptionFreshPlayerAfterReload
        );
        addBooleanAssertion(
                assertions,
                "food_consumption_stable_after_reload",
                foodConsumptionStableAfterReload
        );
        addAssertion(
                assertions,
                "registry:block:" + ForestLanternProbeState.BLOCK_ID,
                "present",
                presentState(initialForestLanternState.blockIdentity() != null)
        );
        addAssertion(
                assertions,
                "registry:block_item:" + ForestLanternProbeState.BLOCK_ID,
                "present",
                presentState(initialForestLanternState.itemIdentity() != null)
        );
        addAssertion(
                assertions,
                "forest_lantern_capture_error",
                "none",
                errorState(initialForestLanternState.captureError())
        );
        addAssertion(
                assertions,
                "forest_lantern_block_class_exact",
                ForestLanternProbeState.BLOCK_CLASS,
                initialForestLanternState.blockClass()
        );
        addAssertion(
                assertions,
                "forest_lantern_item_class_exact",
                ForestLanternProbeState.ITEM_CLASS,
                initialForestLanternState.itemClass()
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_block_item_mapping_exact",
                initialForestLanternState.blockItemMapsToBlock()
                        && initialForestLanternState.blockAsItemMatches()
        );
        addAssertion(
                assertions,
                "forest_lantern_default_state_exact",
                ForestLanternProbeState.EXPECTED_DEFAULT_STATE,
                initialForestLanternState.defaultState()
        );
        addAssertion(
                assertions,
                "forest_lantern_state_count_exact",
                Integer.toString(ForestLanternProbeState.EXPECTED_STATE_COUNT),
                Integer.toString(initialForestLanternState.states().size())
        );
        addAssertion(
                assertions,
                "forest_lantern_states_exact",
                ForestLanternProbeState.expectedCanonicalStates(),
                initialForestLanternState.canonicalStates()
        );
        addAssertion(
                assertions,
                "forest_lantern_state_network_ids_exact",
                "20 unique non-negative raw IDs",
                initialForestLanternState.hasExactStateNetworkIds()
                        ? "20 unique non-negative raw IDs"
                        : initialForestLanternState.stateNetworkIds().toString()
        );
        addAssertion(
                assertions,
                "forest_lantern_outline_shapes_exact",
                ForestLanternProbeState.expectedCanonicalOutlineShapes(),
                initialForestLanternState.canonicalOutlineShapes()
        );
        addAssertion(
                assertions,
                "forest_lantern_properties_exact",
                ForestLanternProbeState.EXPECTED_PROPERTIES,
                initialForestLanternState.properties()
        );
        addBooleanAssertion(
                assertions,
                "tag:hoe_mineable_contains_forest_lantern",
                initialForestLanternState.hoeMineable()
        );
        addAssertion(
                assertions,
                "tag:peach_logs_entries_exact",
                "none",
                initialForestLanternState.peachLogIds().isEmpty()
                        ? "none"
                        : String.join(",", initialForestLanternState.peachLogIds())
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_registry_contract_exact",
                initialForestLanternState.hasExactRegistry()
                        && initialForestLanternState.hasExactStatesAndProperties()
                        && initialForestLanternState.hasExactTags()
        );
        addAssertion(
                assertions,
                "forest_lantern_loaded_data_capture_error",
                "none",
                errorState(initialForestLanternState.loadedData().captureError())
        );
        addAssertion(
                assertions,
                "forest_lantern_loot_table_id_exact",
                "etherology:blocks/forest_lantern",
                initialForestLanternState.loadedData().lootTableId()
        );
        addAssertion(
                assertions,
                "forest_lantern_loot_by_age_exact",
                ForestLanternProbeState.canonicalStringMap(
                        ForestLanternProbeState.LoadedData.EXPECTED_LOOT_BY_AGE
                ),
                ForestLanternProbeState.canonicalStringMap(
                        initialForestLanternState.loadedData().lootByAge()
                )
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_loot_contract_exact",
                initialForestLanternState.loadedData().hasExactLoot()
        );
        addAssertion(
                assertions,
                "forest_lantern_recipe_ids_exact",
                String.join(
                        ",",
                        ForestLanternProbeState.LoadedData.EXPECTED_RECIPE_IDS
                ),
                String.join(",", initialForestLanternState.loadedData().recipeIds())
        );
        addAssertion(
                assertions,
                "forest_lantern_recipes_exact",
                ForestLanternProbeState.canonicalStringMap(
                        ForestLanternProbeState.LoadedData.EXPECTED_RECIPES
                ),
                ForestLanternProbeState.canonicalStringMap(
                        initialForestLanternState.loadedData().recipes()
                )
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_recipes_match_and_craft_exact",
                initialForestLanternState.loadedData().recipeMatchesAndCraftsExact()
        );
        addAssertion(
                assertions,
                "forest_lantern_advancement_ids_exact",
                String.join(
                        ",",
                        ForestLanternProbeState.LoadedData.EXPECTED_ADVANCEMENT_IDS
                ),
                String.join(",", initialForestLanternState.loadedData().advancementIds())
        );
        addAssertion(
                assertions,
                "forest_lantern_advancements_exact",
                ForestLanternProbeState.canonicalStringMap(
                        ForestLanternProbeState.LoadedData.EXPECTED_ADVANCEMENTS
                ),
                ForestLanternProbeState.canonicalStringMap(
                        initialForestLanternState.loadedData().advancements()
                )
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_loaded_data_contract_exact",
                initialForestLanternState.loadedData().hasExactContract()
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_captured_after_server_data_load",
                forestLanternCapturedAfterServerDataLoad
        );
        addBooleanAssertion(
                assertions,
                "server_started_forest_lantern_rechecked",
                serverStartedForestLanternRechecked
        );
        addAssertion(
                assertions,
                "forest_lantern_placement_exact",
                ForestLanternProbeState.canonicalStringMap(
                        ForestLanternProbeState.PlacementResult.EXPECTED_PLACEMENTS
                ),
                ForestLanternProbeState.canonicalStringMap(
                        initialForestLanternMechanics.placement().placements()
                )
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_support_removal_exact",
                initialForestLanternMechanics.placement().supportsRemoved()
        );
        addAssertion(
                assertions,
                "forest_lantern_shears_speeds_exact",
                ForestLanternProbeState.canonicalStringMap(
                        ForestLanternProbeState.ShearsResult.EXPECTED_SPEEDS
                ),
                ForestLanternProbeState.canonicalStringMap(
                        initialForestLanternMechanics.shears().speeds()
                )
        );
        addAssertion(
                assertions,
                "forest_lantern_shears_deltas_exact",
                ForestLanternProbeState.canonicalStringMap(
                        ForestLanternProbeState.ShearsResult.EXPECTED_DELTAS
                ),
                ForestLanternProbeState.canonicalStringMap(
                        initialForestLanternMechanics.shears().deltas()
                )
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_shears_contract_exact",
                initialForestLanternMechanics.shears().exact()
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_jump_retain_exact",
                initialForestLanternMechanics.retainJump().exact()
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_jump_single_callback_guard_exact",
                initialForestLanternMechanics.retainJump()
                        .singleCallbackGuardExact()
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_jump_break_exact",
                initialForestLanternMechanics.breakJump().exact()
        );
        addAssertion(
                assertions,
                "forest_lantern_jump_break_drop_exact",
                ForestLanternProbeState.BLOCK_ID + "x1",
                String.join(",", initialForestLanternMechanics.breakJump().newDrops())
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_server_mechanics_contract_exact",
                initialForestLanternMechanics.hasExactContract()
        );
        addAssertion(
                assertions,
                "reloaded_forest_lantern_capture_error",
                "none",
                errorState(reloadedForestLanternState.captureError())
        );
        addAssertion(
                assertions,
                "reloaded_forest_lantern_mechanics_capture_error",
                "none",
                errorState(reloadedForestLanternMechanics.captureError())
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_registry_stable_after_reload",
                forestLanternRegistryStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_states_stable_after_reload",
                forestLanternStatesStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_tags_stable_after_reload",
                forestLanternTagsStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_loaded_data_stable_after_reload",
                forestLanternLoadedDataStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_loaded_data_fresh_after_reload",
                forestLanternLoadedDataFreshAfterReload
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_mechanics_fresh_players_after_reload",
                forestLanternMechanicsFreshPlayersAfterReload
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_mechanics_stable_after_reload",
                forestLanternMechanicsStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "forest_lantern_contract_exact",
                initialForestLanternState.hasExactContract()
                        && initialForestLanternMechanics.hasExactContract()
                        && reloadedForestLanternState.hasExactContract()
                        && reloadedForestLanternMechanics.hasExactContract()
                        && forestLanternRegistryStableAfterReload
                        && forestLanternStatesStableAfterReload
                        && forestLanternTagsStableAfterReload
                        && forestLanternLoadedDataStableAfterReload
                        && forestLanternLoadedDataFreshAfterReload
                        && forestLanternMechanicsStableAfterReload
        );
        addAssertion(
                assertions,
                "registry:loot_condition:etherology:random_chance_with_fortune",
                "present",
                presentState(lootConditionState.conditionTypeIdentity() != null)
        );
        addAssertion(
                assertions,
                "registry:loot_condition_etherology_ids_exact",
                LootConditionProbeState.CONDITION_ID.toString(),
                String.join(",", lootConditionState.etherologyConditionIds())
        );
        addAssertion(
                assertions,
                "registry:loot_condition_serializer_class",
                LootConditionProbeState.EXPECTED_SERIALIZER_CLASS,
                lootConditionState.serializerClass()
        );
        addAssertion(
                assertions,
                "loot_table:probe_table_loaded",
                LootConditionProbeState.PROBE_TABLE_ID.toString(),
                lootConditionState.probeTableId()
        );
        addAssertion(
                assertions,
                "loot_table:empty_tool_items_exact",
                String.join(",", LootConditionProbeState.EXPECTED_EMPTY_TOOL_ITEMS),
                String.join(",", lootConditionState.emptyToolItems())
        );
        addAssertion(
                assertions,
                "loot_table:fortune_one_items_exact",
                String.join(",", LootConditionProbeState.EXPECTED_FORTUNE_ONE_ITEMS),
                String.join(",", lootConditionState.fortuneOneItems())
        );
        addBooleanAssertion(
                assertions,
                "loot_condition_captured_after_server_data_load",
                lootConditionCapturedAfterServerDataLoad
        );
        addAssertion(
                assertions,
                "ether_source_listener_class",
                EtherSourceProbeState.LOADER_CLASS_NAME,
                EtherSourceProbeState.LOADER_CLASS_NAME
        );
        addAssertion(
                assertions,
                "ether_source_resource_directory",
                EtherSourceProbeState.RESOURCE_DIRECTORY,
                EtherSourceProbeState.RESOURCE_DIRECTORY
        );
        addAssertion(
                assertions,
                "ether_source_initial_capture_error",
                "none",
                errorState(initialEtherSourceState.captureError())
        );
        addAssertion(
                assertions,
                "ether_source_initial_entry_count",
                "23",
                Integer.toString(initialEtherSourceState.entries().size())
        );
        addAssertion(
                assertions,
                "ether_source_initial_entries_exact",
                EtherSourceProbeState.canonicalEntries(
                        EtherSourceProbeState.EXPECTED_INITIAL_ENTRIES
                ),
                initialEtherSourceState.canonicalEntries()
        );
        addAssertion(
                assertions,
                "ether_source_initial_rella_value",
                "4.0",
                initialEtherSourceState.value("etherology:primoshard_rella")
        );
        addAssertion(
                assertions,
                "ether_source_initial_legacy_rela_absent",
                "absent",
                initialEtherSourceState.value("etherology:primoshard_rela")
        );
        addAssertion(
                assertions,
                "ether_source_initial_redstone_value",
                "2.0",
                initialEtherSourceState.value("minecraft:redstone")
        );
        addBooleanAssertion(
                assertions,
                "ether_source_captured_after_server_data_load",
                etherSourceCapturedAfterServerDataLoad
        );
        addBooleanAssertion(
                assertions,
                "server_started_ether_sources_rechecked",
                serverStartedEtherSourcesRechecked
        );
        addAssertion(
                assertions,
                "reload_pack_directory",
                ReloadDataPackWriter.PACK_DIRECTORY_NAME,
                reloadPackDirectory
        );
        addAssertion(
                assertions,
                "reload_pack_resources_exact",
                String.join(",", ReloadDataPackWriter.RESOURCE_PATHS),
                String.join(",", reloadPackResourcePaths)
        );
        addAssertion(
                assertions,
                "reload_pack_enabled",
                ReloadDataPackWriter.ENABLED_PACK_NAME,
                enabledDataPackNamesAfterReload.stream()
                        .filter(ReloadDataPackWriter.ENABLED_PACK_NAME::equals)
                        .count() == 1
                        ? ReloadDataPackWriter.ENABLED_PACK_NAME
                        : String.join(",", enabledDataPackNamesAfterReload)
        );
        addBooleanAssertion(
                assertions,
                "enabled_data_packs_exact",
                enabledDataPacksExact
        );
        addAssertion(assertions, "reload_failure", "none", errorState(reloadFailure));
        addAssertion(assertions, "reload_command", "reload", reloadRequested ? "reload" : "none");
        addAssertion(
                assertions,
                "reload_command_result",
                "0",
                Integer.toString(reloadCommandResult)
        );
        addBooleanAssertion(assertions, "reload_completed", reloadCompleted);
        addAssertion(
                assertions,
                "reload_update_cause",
                TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD.name(),
                reloadUpdateCause
        );
        addBooleanAssertion(
                assertions,
                "reload_static_data",
                reloadShouldUpdateStaticData
        );
        addAssertion(
                assertions,
                "ether_source_reloaded_capture_error",
                "none",
                errorState(reloadedEtherSourceState.captureError())
        );
        addAssertion(
                assertions,
                "ether_source_reloaded_entry_count",
                "24",
                Integer.toString(reloadedEtherSourceState.entries().size())
        );
        addAssertion(
                assertions,
                "ether_source_reloaded_entries_exact",
                EtherSourceProbeState.canonicalEntries(
                        EtherSourceProbeState.EXPECTED_RELOADED_ENTRIES
                ),
                reloadedEtherSourceState.canonicalEntries()
        );
        addAssertion(
                assertions,
                "ether_source_reloaded_redstone_value",
                "9.5",
                reloadedEtherSourceState.value("minecraft:redstone")
        );
        addAssertion(
                assertions,
                "ether_source_reloaded_diamond_value",
                "13.0",
                reloadedEtherSourceState.value("minecraft:diamond")
        );
        addAssertion(
                assertions,
                "ether_source_reloaded_rella_value",
                "4.0",
                reloadedEtherSourceState.value("etherology:primoshard_rella")
        );
        addAssertion(
                assertions,
                "ether_source_reloaded_legacy_rela_absent",
                "absent",
                reloadedEtherSourceState.value("etherology:primoshard_rela")
        );
        addBooleanAssertion(
                assertions,
                "ether_source_map_changed_after_reload",
                !initialEtherSourceState.sameEntries(reloadedEtherSourceState)
        );
        addBooleanAssertion(
                assertions,
                "registry_stable_after_reload",
                registryStableAfterReload
        );
        addBooleanAssertion(assertions, "tags_stable_after_reload", tagsStableAfterReload);
        addBooleanAssertion(
                assertions,
                "loot_condition_registry_and_behavior_stable_after_reload",
                lootConditionStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "loot_table_instance_replaced_after_reload",
                lootTableInstanceReplacedAfterReload
        );
        addBooleanAssertion(
                assertions,
                "server_stop_requested_after_reload",
                stopRequestedAfterReload
        );
        addAssertion(
                assertions,
                "tags_update_cause",
                TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD.name(),
                updateCause
        );
        addAssertion(
                assertions,
                "tags_static_data",
                "true",
                Boolean.toString(shouldUpdateStaticData)
        );
        addAssertion(
                assertions,
                "tags_update_count",
                "2",
                Integer.toString(tagUpdateCount)
        );
        addBooleanAssertion(
                assertions,
                "tag:vibrations_contains_resonance",
                vibrationsContainsEvent
        );
        addAssertion(
                assertions,
                "tag:vibrations_etherology_entries_exact",
                EVENT_ID.toString(),
                String.join(",", vibrationsEtherologyEventIds)
        );
        addBooleanAssertion(
                assertions,
                "tag:warden_can_listen_contains_resonance",
                wardenCanListenContainsEvent
        );
        addAssertion(
                assertions,
                "tag:warden_can_listen_etherology_entries_exact",
                EVENT_ID.toString(),
                String.join(",", wardenCanListenEtherologyEventIds)
        );
        addAssertion(
                assertions,
                "tags:etherology_tag_ids_exact",
                String.join(",", EXPECTED_ETHERLOGY_TAG_IDS),
                String.join(",", etherologyTagIds)
        );
        addBooleanAssertion(assertions, "tags_before_server_started", tagsBeforeServerStarted);
        addBooleanAssertion(
                assertions,
                "server_started_mods_rechecked",
                serverStartedModsRechecked
        );
        addBooleanAssertion(
                assertions,
                "server_started_registry_rechecked",
                serverStartedRegistryRechecked
        );
        addBooleanAssertion(
                assertions,
                "server_started_tags_rechecked",
                serverStartedTagsRechecked
        );
        addBooleanAssertion(
                assertions,
                "server_started_loot_condition_rechecked",
                serverStartedLootConditionRechecked
        );
        addAssertion(
                assertions,
                "server_stop_requested_without_restart",
                "stop(false)",
                stopRequestedWithoutRestart ? "stop(false)" : "not requested"
        );
        addBooleanAssertion(assertions, "server_lifecycle_identity", lifecycleIdentity);
        addAssertion(
                assertions,
                "lifecycle",
                String.join(">", EXPECTED_LIFECYCLE),
                lifecycleActual
        );

        JsonObject report = new JsonObject();
        report.addProperty("schema", 12);
        report.addProperty("profile_id", profileId);
        report.addProperty("scenario", scenarioId);
        report.addProperty("status", assertionsPassed(assertions) ? "passed" : "failed");
        report.addProperty("minecraft", SharedConstants.getGameVersion().getName());
        report.addProperty("loader", "forge");
        report.addProperty("loader_version", ForgeVersion.getVersion());
        report.addProperty("java", Runtime.version().feature());
        report.addProperty("distribution", distribution);
        report.addProperty("runtime_kind", runtimeKind);
        report.add("loaded_mod_ids", buildStringArray(loadedModIds));
        report.add("forbidden_mod_ids_loaded", buildStringArray(forbiddenModIdsLoaded));
        report.add("mods", buildMods(etherologyLoaded, probeLoaded));
        report.add("registry", buildRegistry());
        report.add("enchantments", buildEnchantments());
        report.add("particles", buildParticles());
        report.add("material_items", buildMaterialItems());
        report.add("food_items", buildFoodItems());
        report.add("food_consumption", buildFoodConsumption());
        report.add("forest_lantern", buildForestLantern());
        report.add("metal_blocks", buildMetalBlocks());
        report.add("attrahite_blocks", buildAttrahiteBlocks());
        report.add("slitherite_blocks", buildSlitheriteBlocks());
        report.add("loot_condition", buildLootCondition());
        report.add("ether_sources", buildEtherSources());
        report.add("reload", buildReload());
        report.add("tags", buildTags());
        report.add("lifecycle", buildLifecycle());
        report.add("assertions", assertions);
        return report;
    }

    private void addSlitheriteAssertions(JsonArray assertions) {
        SlitheriteBlockProbeState.EXPECTED_BLOCKS.forEach((id, spec) -> {
            SlitheriteBlockProbeState.SlitheriteBlockEntry entry =
                    initialSlitheriteBlockState.entries().getOrDefault(
                            id,
                            SlitheriteBlockProbeState.SlitheriteBlockEntry.failed(id)
                    );
            addAssertion(
                    assertions,
                    "registry:block:" + id,
                    "present",
                    presentState(entry.blockIdentity() != null)
            );
            addAssertion(
                    assertions,
                    "registry:item:" + id,
                    "present",
                    presentState(entry.itemIdentity() != null)
            );
            addAssertion(
                    assertions,
                    "runtime:block_class:" + id,
                    spec.blockClass(),
                    entry.blockClass()
            );
            addAssertion(
                    assertions,
                    "runtime:block_item_class:" + id,
                    SlitheriteBlockProbeState.BLOCK_ITEM_CLASS,
                    entry.itemClass()
            );
            addBooleanAssertion(
                    assertions,
                    "block_item_mapping:" + id,
                    entry.blockItem()
                            && entry.blockItemMapsToBlock()
                            && entry.blockAsItemMatches()
            );
            addAssertion(
                    assertions,
                    "default_state:" + id,
                    spec.defaultState(),
                    entry.defaultState()
            );
            addAssertion(
                    assertions,
                    "state_count:" + id,
                    Integer.toString(spec.stateCount()),
                    Integer.toString(entry.stateCount())
            );
            addAssertion(
                    assertions,
                    "default_state_network_id:" + id,
                    "non-negative",
                    entry.defaultStateRawId() >= 0
                            ? "non-negative"
                            : Integer.toString(entry.defaultStateRawId())
            );
            addBooleanAssertion(
                    assertions,
                    "slitherite_state_raw_ids_exact:" + id,
                    entry.hasExactRawIds(spec.stateCount())
            );
        });
        addAssertion(
                assertions,
                "registry:slitherite_block_ids_exact",
                String.join(",", SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS),
                String.join(",", initialSlitheriteBlockState.blockIds())
        );
        addAssertion(
                assertions,
                "registry:slitherite_block_item_ids_exact",
                String.join(",", SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS),
                String.join(",", initialSlitheriteBlockState.blockItemIds())
        );
        addAssertion(
                assertions,
                "slitherite_block_capture_error",
                "none",
                errorState(initialSlitheriteBlockState.captureError())
        );
        addBooleanAssertion(
                assertions,
                "slitherite_block_runtime_classes_exact",
                initialSlitheriteBlockState.hasExactRuntimeClasses()
        );
        addBooleanAssertion(
                assertions,
                "slitherite_block_item_mappings_exact",
                initialSlitheriteBlockState.hasExactBlockItemMappings()
        );
        addAssertion(
                assertions,
                "slitherite_block_properties_exact",
                SlitheriteBlockProbeState.expectedCanonicalProperties(),
                initialSlitheriteBlockState.canonicalProperties()
        );
        addAssertion(
                assertions,
                "slitherite_aggregate_state_count_exact",
                Integer.toString(
                        SlitheriteBlockProbeState.EXPECTED_AGGREGATE_STATE_COUNT
                ),
                Integer.toString(initialSlitheriteBlockState.aggregateStateCount())
        );
        addAssertion(
                assertions,
                "slitherite_aggregate_unique_raw_id_count_exact",
                Integer.toString(
                        SlitheriteBlockProbeState.EXPECTED_AGGREGATE_STATE_COUNT
                ),
                Integer.toString(
                        initialSlitheriteBlockState.aggregateUniqueRawIdCount()
                )
        );
        addBooleanAssertion(
                assertions,
                "slitherite_state_raw_ids_aggregate_exact",
                initialSlitheriteBlockState.hasExactRawIds()
        );
        addBooleanAssertion(
                assertions,
                "slitherite_state_network_ids_exact",
                initialSlitheriteBlockState.hasExactRawIds()
        );
        addAssertion(
                assertions,
                "slitherite_block_tags_exact",
                SlitheriteBlockProbeState.expectedCanonicalTags(),
                initialSlitheriteBlockState.canonicalTags()
        );
        addSlitheriteTagAssertions(assertions);
        addBooleanAssertion(
                assertions,
                "slitherite_blocks_captured_at_initial_tag_load",
                slitheriteBlocksCapturedAtInitialTagLoad
        );
        addBooleanAssertion(
                assertions,
                "slitherite_blocks_captured_after_server_data_load",
                slitheriteBlocksCapturedAfterServerDataLoad
        );
        addBooleanAssertion(
                assertions,
                "server_started_slitherite_blocks_rechecked",
                serverStartedSlitheriteBlocksRechecked
        );
        addAssertion(
                assertions,
                "slitherite_native_placement_capture_error",
                "none",
                errorState(initialSlitheriteNativePlacement.captureError())
        );
        addAssertion(
                assertions,
                "slitherite_native_block_item_placements_exact",
                SlitheriteNativePlacementState.expectedCanonical(),
                initialSlitheriteNativePlacement.canonical()
        );
        addBooleanAssertion(
                assertions,
                "slitherite_native_block_item_placement_contract_exact",
                initialSlitheriteNativePlacement.hasExactPlacement()
        );
        addBooleanAssertion(
                assertions,
                "direct_block_item_placements_exact",
                initialSlitheriteNativePlacement.hasExactPlacement()
        );
        addAssertion(
                assertions,
                "slitherite_fixture_capture_error",
                "none",
                errorState(initialSlitheriteBlockPlacement.captureError())
        );
        addAssertion(
                assertions,
                "slitherite_fixture_positions_exact",
                SlitheriteBlockProbeState.PlacementState.expectedPositions().toString(),
                initialSlitheriteBlockPlacement.positions().toString()
        );
        addAssertion(
                assertions,
                "slitherite_fixture_support_positions_exact",
                SlitheriteBlockProbeState.PlacementState.expectedSupports().toString(),
                initialSlitheriteBlockPlacement.supportPositions().toString()
        );
        addAssertion(
                assertions,
                "slitherite_fixture_placed_ids_exact",
                SlitheriteBlockProbeState.PlacementState.expectedBlockIds().toString(),
                initialSlitheriteBlockPlacement.placedBlockIds().toString()
        );
        addAssertion(
                assertions,
                "slitherite_fixture_placed_states_exact",
                SlitheriteBlockProbeState.PlacementState.expectedStates().toString(),
                initialSlitheriteBlockPlacement.placedStates().toString()
        );
        addAssertion(
                assertions,
                "slitherite_fixture_support_ids_exact",
                SlitheriteBlockProbeState.PlacementState.expectedSupportIds().toString(),
                initialSlitheriteBlockPlacement.supportBlockIds().toString()
        );
        addBooleanAssertion(
                assertions,
                "slitherite_fixture_placement_exact",
                initialSlitheriteBlockPlacement.hasExactPlacement()
        );
        addBooleanAssertion(
                assertions,
                "initial_server_fixture_exact",
                initialSlitheriteBlockPlacement.hasExactPlacement()
        );
        addAssertion(
                assertions,
                "slitherite_behavior_capture_error",
                "none",
                errorState(slitheriteBehaviorProbe.captureError())
        );
        addBooleanAssertion(
                assertions,
                "slitherite_button_pulse_reset_exact",
                slitheriteBehaviorProbe.buttonExact()
        );
        addBooleanAssertion(
                assertions,
                "slitherite_pressure_plate_entities_exact",
                slitheriteBehaviorProbe.pressurePlateExact()
        );
        addBooleanAssertion(
                assertions,
                "slitherite_behavior_fixture_reset_exact",
                slitheriteBehaviorProbe.fixtureResetExact()
        );
        addBooleanAssertion(
                assertions,
                "slitherite_behavior_contract_exact",
                slitheriteBehaviorProbe.exact()
        );
        addAssertion(
                assertions,
                "slitherite_world_save_failure",
                "none",
                errorState(slitheriteWorldSaveFailure)
        );
        addBooleanAssertion(
                assertions,
                "slitherite_world_saved_after_placement",
                slitheriteWorldSavedAfterPlacement
        );
        addBooleanAssertion(
                assertions,
                "forced_world_save",
                slitheriteWorldSavedAfterPlacement
        );
        addBooleanAssertion(
                assertions,
                "slitherite_fixture_saved_after_force_save",
                slitheriteBlockPlacementSavedAfterForceSave
        );
        addSlitheriteLoadedDataAssertions(assertions);
        addBooleanAssertion(
                assertions,
                "slitherite_block_registry_stable_after_reload",
                slitheriteBlockRegistryStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "slitherite_block_default_states_stable_after_reload",
                slitheriteBlockDefaultStatesStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "slitherite_block_tags_stable_after_reload",
                slitheriteBlockTagsStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "slitherite_loaded_data_stable_after_reload",
                slitheriteBlockLoadedDataStableAfterReload
        );
        addBooleanAssertion(
                assertions,
                "slitherite_loaded_data_fresh_after_reload",
                slitheriteBlockLoadedDataFreshAfterReload
        );
        addBooleanAssertion(
                assertions,
                "slitherite_fixture_stable_after_reload",
                slitheriteBlockPlacementStableAfterReload
        );
    }

    private void addSlitheriteTagAssertions(JsonArray assertions) {
        addSlitheriteTagAssertion(
                assertions,
                "tag:mineable/pickaxe",
                SlitheriteBlockProbeState.EXPECTED_BLOCK_IDS,
                SlitheriteBlockProbeState.SlitheriteBlockEntry::pickaxeMineable
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:needs_stone_tool",
                List.of(),
                SlitheriteBlockProbeState.SlitheriteBlockEntry::needsStoneTool
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:block/slabs",
                expectedSlitheriteIds(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::slab
                ),
                SlitheriteBlockProbeState.SlitheriteBlockEntry::blockSlab
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:item/slabs",
                expectedSlitheriteIds(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::slab
                ),
                SlitheriteBlockProbeState.SlitheriteBlockEntry::itemSlab
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:block/stairs",
                expectedSlitheriteIds(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::stairs
                ),
                SlitheriteBlockProbeState.SlitheriteBlockEntry::blockStairs
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:item/stairs",
                expectedSlitheriteIds(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::stairs
                ),
                SlitheriteBlockProbeState.SlitheriteBlockEntry::itemStairs
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:block/walls",
                expectedSlitheriteIds(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::wall
                ),
                SlitheriteBlockProbeState.SlitheriteBlockEntry::blockWall
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:item/walls",
                expectedSlitheriteIds(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::wall
                ),
                SlitheriteBlockProbeState.SlitheriteBlockEntry::itemWall
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:block/stone_bricks",
                expectedSlitheriteIds(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::stoneBrick
                ),
                SlitheriteBlockProbeState.SlitheriteBlockEntry::blockStoneBrick
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:block/stone_pressure_plates",
                expectedSlitheriteIds(
                        SlitheriteBlockProbeState.SlitheriteBlockSpec::pressurePlate
                ),
                SlitheriteBlockProbeState.SlitheriteBlockEntry
                        ::blockStonePressurePlate
        );
        addSlitheriteTagAssertion(
                assertions,
                "tag:item/buttons",
                List.of(),
                SlitheriteBlockProbeState.SlitheriteBlockEntry::itemButton
        );
    }

    private void addSlitheriteTagAssertion(
            JsonArray assertions,
            String name,
            List<String> expected,
            Predicate<SlitheriteBlockProbeState.SlitheriteBlockEntry> selector
    ) {
        List<String> actual = initialSlitheriteBlockState.entries()
                .entrySet()
                .stream()
                .filter(entry -> selector.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        addAssertion(assertions, name, expected.toString(), actual.toString());
    }

    private static List<String> expectedSlitheriteIds(
            Predicate<SlitheriteBlockProbeState.SlitheriteBlockSpec> selector
    ) {
        return SlitheriteBlockProbeState.EXPECTED_BLOCKS.entrySet()
                .stream()
                .filter(entry -> selector.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private void addSlitheriteLoadedDataAssertions(JsonArray assertions) {
        SlitheriteBlockProbeState.LoadedData loadedData =
                initialSlitheriteBlockState.loadedData();
        addAssertion(
                assertions,
                "slitherite_loaded_data_capture_error",
                "none",
                errorState(loadedData.captureError())
        );
        addAssertion(
                assertions,
                "slitherite_loot_tables_exact",
                String.join(",", SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_LOOT_TABLE_IDS),
                String.join(",", loadedData.lootTableIds())
        );
        addAssertion(
                assertions,
                "slitherite_self_drops_exact",
                SlitheriteBlockProbeState.LoadedData.expectedCanonicalSelfDrops(),
                loadedData.canonicalSelfDrops()
        );
        addAssertion(
                assertions,
                "slitherite_double_slab_drops_x1_exact",
                SlitheriteBlockProbeState.LoadedData
                        .expectedCanonicalDoubleSlabDrops(),
                loadedData.canonicalDoubleSlabDrops()
        );
        addAssertion(
                assertions,
                "slitherite_owned_recipe_ids_exact",
                String.join(",", SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_RECIPE_IDS),
                String.join(",", loadedData.recipeIds())
        );
        addAssertion(
                assertions,
                "slitherite_owned_recipes_exact",
                SlitheriteBlockProbeState.LoadedData.expectedCanonicalRecipes(),
                loadedData.canonicalRecipes()
        );
        addAssertion(
                assertions,
                "slitherite_owned_advancements_exact",
                String.join(",", SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_ADVANCEMENT_IDS),
                String.join(",", loadedData.advancementIds())
        );
        addAssertion(
                assertions,
                "slitherite_related_recipe_ids_exact",
                String.join(",", SlitheriteBlockProbeState.LoadedData
                        .EXPECTED_RELATED_RECIPE_IDS),
                String.join(",", loadedData.relatedRecipeIds())
        );
        addAssertion(
                assertions,
                "slitherite_related_recipes_recorded_not_owned",
                SlitheriteBlockProbeState.LoadedData
                        .expectedCanonicalRelatedRecipes(),
                loadedData.canonicalRelatedRecipes()
        );
        addBooleanAssertion(
                assertions,
                "slitherite_loaded_data_contract_exact",
                loadedData.hasExactContract()
        );
    }

    private void scheduleProcessExit(Thread serverThread, int exitStatus) {
        processTerminator.schedule(serverThread, exitStatus);
        LOGGER.info(
                "[EtherologyServerProbe] loom_userdev_exit_scheduled "
                        + "status={} server_thread_join_timeout_ms={}",
                exitStatus,
                ServerProbeProcessTerminator.SERVER_THREAD_JOIN_TIMEOUT_MILLIS
        );
    }

    private void captureRegistryState(GameEvent gameEvent) {
        if (gameEvent == null) {
            registryEventId = "";
            internalEventId = "";
            eventRange = -1;
            return;
        }
        Identifier identifier = Registries.GAME_EVENT.getId(gameEvent);
        registryEventId = identifier == null ? "" : identifier.toString();
        internalEventId = gameEvent.getId();
        eventRange = gameEvent.getRange();
    }

    private void captureEtherologyTagMemberships() {
        Map<String, List<String>> memberships = collectEtherologyTagMemberships();
        etherologyTagIds = List.copyOf(memberships.keySet());
        vibrationsEtherologyEventIds = List.copyOf(
                memberships.getOrDefault("minecraft:vibrations", List.of())
        );
        wardenCanListenEtherologyEventIds = List.copyOf(
                memberships.getOrDefault("minecraft:warden_can_listen", List.of())
        );
    }

    private JsonObject buildMods(
            boolean etherologyLoaded,
            boolean probeLoaded
    ) {
        JsonObject mods = new JsonObject();
        mods.add("etherology", buildLoadedMod(etherologyLoaded));
        mods.add(MOD_ID, buildLoadedMod(probeLoaded));
        forbiddenModIds.forEach(forbiddenModId ->
                mods.add(forbiddenModId, buildLoadedMod(loadedModIds.contains(forbiddenModId)))
        );
        return mods;
    }

    private JsonObject buildRegistry() {
        JsonObject registry = new JsonObject();
        registry.addProperty("registry_id", "minecraft:game_event");
        registry.addProperty("event_id", registryEventId);
        JsonArray etherologyEventIdsArray = new JsonArray();
        registryEtherologyEventIds.forEach(etherologyEventIdsArray::add);
        registry.add("etherology_event_ids", etherologyEventIdsArray);
        registry.addProperty("internal_id", internalEventId);
        registry.addProperty("range", eventRange);
        registry.addProperty(
                "same_instance_at_server_started",
                serverStartedRegistryRechecked
        );
        registry.addProperty("stable_after_reload", registryStableAfterReload);
        return registry;
    }

    private JsonObject buildEnchantments() {
        JsonObject enchantments = new JsonObject();
        enchantments.addProperty(
                "registry_id",
                EnchantmentProbeState.ENCHANTMENT_REGISTRY_ID
        );
        enchantments.addProperty(
                "non_treasure_tag_id",
                EnchantmentProbeState.NON_TREASURE_TAG_ID
        );
        enchantments.add(
                "etherology_enchantment_ids",
                buildStringArray(initialEnchantmentState.etherologyEnchantmentIds())
        );
        enchantments.add(
                "peal",
                buildEnchantment(
                        initialEnchantmentState.pealId(),
                        initialEnchantmentState.pealClass(),
                        initialEnchantmentState.pealMaxLevel(),
                        initialEnchantmentState.pealMinPowers(),
                        initialEnchantmentState.pealMaxPowers(),
                        initialEnchantmentState.pealInNonTreasure()
                )
        );
        enchantments.add(
                "reflection",
                buildEnchantment(
                        initialEnchantmentState.reflectionId(),
                        initialEnchantmentState.reflectionClass(),
                        initialEnchantmentState.reflectionMaxLevel(),
                        initialEnchantmentState.reflectionMinPowers(),
                        initialEnchantmentState.reflectionMaxPowers(),
                        initialEnchantmentState.reflectionInNonTreasure()
                )
        );
        enchantments.add(
                "non_treasure_etherology_enchantment_ids",
                buildStringArray(
                        initialEnchantmentState.nonTreasureEtherologyEnchantmentIds()
                )
        );
        enchantments.addProperty(
                "same_state_at_server_started",
                serverStartedEnchantmentsRechecked
        );
        enchantments.addProperty(
                "registry_stable_after_reload",
                enchantmentRegistryStableAfterReload
        );
        enchantments.addProperty(
                "properties_stable_after_reload",
                enchantmentPropertiesStableAfterReload
        );
        enchantments.addProperty(
                "tag_stable_after_reload",
                enchantmentTagStableAfterReload
        );
        return enchantments;
    }

    private JsonObject buildParticles() {
        JsonObject particles = new JsonObject();
        particles.addProperty(
                "registry_id",
                ParticleProbeState.PARTICLE_REGISTRY_ID
        );
        particles.addProperty("capture_error", initialParticleState.captureError());
        particles.add(
                "etherology_particle_ids",
                buildStringArray(initialParticleState.etherologyParticleIds())
        );
        particles.add(
                "payload_families",
                buildStringArray(initialParticleState.payloadFamilies())
        );
        JsonObject entries = new JsonObject();
        initialParticleState.entries().forEach((path, entry) ->
                entries.add(path, buildParticle(entry))
        );
        particles.add("entries", entries);
        JsonObject sealTypes = new JsonObject();
        sealTypes.add(
                "order",
                buildStringArray(initialParticleState.sealTypeOrder())
        );
        sealTypes.addProperty(
                "codec_round_trips_exact",
                initialParticleState.sealTypeCodecRoundTripsExact()
        );
        JsonObject sealTypeEntries = new JsonObject();
        initialParticleState.sealTypes().forEach((name, entry) ->
                sealTypeEntries.add(name, buildSealType(entry))
        );
        sealTypes.add("entries", sealTypeEntries);
        particles.add("seal_types", sealTypes);
        particles.addProperty(
                "same_state_at_server_started",
                serverStartedParticlesRechecked
        );
        particles.addProperty(
                "registry_stable_after_reload",
                particleRegistryStableAfterReload
        );
        particles.addProperty(
                "type_contract_stable_after_reload",
                particleTypeContractStableAfterReload
        );
        particles.addProperty(
                "wire_contract_stable_after_reload",
                particleWireContractStableAfterReload
        );
        return particles;
    }

    private JsonObject buildMaterialItems() {
        JsonObject materialItems = new JsonObject();
        materialItems.addProperty(
                "registry_id",
                MaterialItemProbeState.ITEM_REGISTRY_ID
        );
        materialItems.addProperty(
                "capture_error",
                initialMaterialItemState.captureError()
        );
        materialItems.add(
                "material_item_ids",
                buildStringArray(initialMaterialItemState.materialItemIds())
        );
        materialItems.addProperty(
                "vanilla_item_class",
                MaterialItemProbeState.VANILLA_ITEM_CLASS
        );
        materialItems.addProperty(
                "max_counts",
                initialMaterialItemState.canonicalMaxCounts()
        );
        materialItems.addProperty(
                "save_representations",
                initialMaterialItemState.canonicalSaveRepresentations()
        );
        JsonObject entries = new JsonObject();
        initialMaterialItemState.entries().forEach((id, entry) ->
                entries.add(id, buildMaterialItem(entry))
        );
        materialItems.add("entries", entries);
        materialItems.addProperty(
                "same_state_at_server_started",
                serverStartedMaterialItemsRechecked
        );
        materialItems.addProperty(
                "registry_stable_after_reload",
                materialItemRegistryStableAfterReload
        );
        materialItems.addProperty(
                "properties_stable_after_reload",
                materialItemPropertiesStableAfterReload
        );
        materialItems.addProperty(
                "stack_nbt_stable_after_reload",
                materialItemStackNbtStableAfterReload
        );
        return materialItems;
    }

    private JsonObject buildFoodItems() {
        JsonObject foodItems = new JsonObject();
        foodItems.addProperty("registry_id", FoodItemProbeState.ITEM_REGISTRY_ID);
        foodItems.addProperty("capture_error", initialFoodItemState.captureError());
        foodItems.add(
                "food_item_ids",
                buildStringArray(initialFoodItemState.foodItemIds())
        );
        foodItems.addProperty(
                "vanilla_item_class",
                FoodItemProbeState.VANILLA_ITEM_CLASS
        );
        foodItems.addProperty(
                "properties",
                initialFoodItemState.canonicalProperties()
        );
        foodItems.addProperty(
                "save_representations",
                initialFoodItemState.canonicalSaveRepresentations()
        );
        JsonObject entries = new JsonObject();
        initialFoodItemState.entries().forEach((id, entry) ->
                entries.add(id, buildFoodItem(entry))
        );
        foodItems.add("entries", entries);
        foodItems.addProperty(
                "same_state_at_server_started",
                serverStartedFoodItemsRechecked
        );
        foodItems.addProperty(
                "registry_stable_after_reload",
                foodItemRegistryStableAfterReload
        );
        foodItems.addProperty(
                "properties_stable_after_reload",
                foodItemPropertiesStableAfterReload
        );
        foodItems.addProperty(
                "stack_nbt_stable_after_reload",
                foodItemStackNbtStableAfterReload
        );
        return foodItems;
    }

    private JsonObject buildFoodConsumption() {
        JsonObject foodConsumption = new JsonObject();
        foodConsumption.add(
                "server_started",
                buildFoodConsumptionCapture(serverStartedFoodConsumption)
        );
        foodConsumption.add(
                "reloaded",
                buildFoodConsumptionCapture(reloadedFoodConsumption)
        );
        foodConsumption.addProperty(
                "fresh_player_after_reload",
                foodConsumptionFreshPlayerAfterReload
        );
        foodConsumption.addProperty(
                "stable_after_reload",
                foodConsumptionStableAfterReload
        );
        return foodConsumption;
    }

    private JsonObject buildForestLantern() {
        JsonObject forestLantern = new JsonObject();
        forestLantern.addProperty(
                "block_registry_id",
                ForestLanternProbeState.BLOCK_REGISTRY_ID
        );
        forestLantern.addProperty(
                "item_registry_id",
                ForestLanternProbeState.ITEM_REGISTRY_ID
        );
        forestLantern.addProperty("block_id", initialForestLanternState.blockId());
        forestLantern.addProperty("item_id", initialForestLanternState.itemId());
        forestLantern.addProperty(
                "capture_error",
                initialForestLanternState.captureError()
        );
        forestLantern.addProperty(
                "block_class",
                initialForestLanternState.blockClass()
        );
        forestLantern.addProperty(
                "item_class",
                initialForestLanternState.itemClass()
        );
        forestLantern.addProperty(
                "block_item_maps_to_block",
                initialForestLanternState.blockItemMapsToBlock()
        );
        forestLantern.addProperty(
                "block_as_item_matches",
                initialForestLanternState.blockAsItemMatches()
        );
        JsonObject itemStack = new JsonObject();
        itemStack.addProperty("max_count", initialForestLanternState.itemMaxCount());
        itemStack.addProperty(
                "serialized_id",
                initialForestLanternState.serializedItemId()
        );
        itemStack.addProperty(
                "serialized_count",
                initialForestLanternState.serializedItemCount()
        );
        itemStack.add(
                "serialized_keys",
                buildStringArray(initialForestLanternState.serializedItemKeys())
        );
        itemStack.addProperty(
                "round_trip_exact",
                initialForestLanternState.itemNbtRoundTripExact()
        );
        forestLantern.add("item_stack", itemStack);
        forestLantern.addProperty(
                "default_state",
                initialForestLanternState.defaultState()
        );
        forestLantern.addProperty(
                "state_count",
                initialForestLanternState.states().size()
        );
        forestLantern.add(
                "states",
                buildStringArray(initialForestLanternState.states())
        );
        forestLantern.add(
                "state_network_ids",
                buildIntegerArray(initialForestLanternState.stateNetworkIds())
        );
        forestLantern.add(
                "outline_shapes",
                buildStringMap(initialForestLanternState.outlineShapes())
        );
        forestLantern.addProperty(
                "properties",
                initialForestLanternState.properties()
        );
        JsonObject tags = new JsonObject();
        tags.addProperty("hoe_mineable", initialForestLanternState.hoeMineable());
        tags.addProperty(
                "peach_logs_tag_id",
                ForestLanternProbeState.PEACH_LOGS_TAG_ID
        );
        tags.add(
                "peach_log_ids",
                buildStringArray(initialForestLanternState.peachLogIds())
        );
        forestLantern.add("tags", tags);
        JsonObject loadedData = new JsonObject();
        loadedData.add(
                "initial",
                buildForestLanternLoadedData(initialForestLanternState.loadedData())
        );
        loadedData.add(
                "reloaded",
                buildForestLanternLoadedData(reloadedForestLanternState.loadedData())
        );
        loadedData.addProperty(
                "stable_after_reload",
                forestLanternLoadedDataStableAfterReload
        );
        loadedData.addProperty(
                "fresh_instances_after_reload",
                forestLanternLoadedDataFreshAfterReload
        );
        forestLantern.add("loaded_data", loadedData);
        JsonObject mechanics = new JsonObject();
        mechanics.add(
                "server_started",
                buildForestLanternWorldMechanics(initialForestLanternMechanics)
        );
        mechanics.add(
                "reloaded",
                buildForestLanternWorldMechanics(reloadedForestLanternMechanics)
        );
        mechanics.addProperty(
                "fresh_players_after_reload",
                forestLanternMechanicsFreshPlayersAfterReload
        );
        mechanics.addProperty(
                "stable_after_reload",
                forestLanternMechanicsStableAfterReload
        );
        forestLantern.add("mechanics", mechanics);
        forestLantern.addProperty(
                "same_state_at_server_started",
                serverStartedForestLanternRechecked
        );
        forestLantern.addProperty(
                "registry_stable_after_reload",
                forestLanternRegistryStableAfterReload
        );
        forestLantern.addProperty(
                "states_stable_after_reload",
                forestLanternStatesStableAfterReload
        );
        forestLantern.addProperty(
                "tags_stable_after_reload",
                forestLanternTagsStableAfterReload
        );
        forestLantern.addProperty(
                "contract_exact",
                initialForestLanternState.hasExactContract()
                        && initialForestLanternMechanics.hasExactContract()
                        && reloadedForestLanternState.hasExactContract()
                        && reloadedForestLanternMechanics.hasExactContract()
                        && forestLanternLoadedDataStableAfterReload
                        && forestLanternLoadedDataFreshAfterReload
                        && forestLanternMechanicsStableAfterReload
        );
        return forestLantern;
    }

    private static JsonObject buildForestLanternLoadedData(
            ForestLanternProbeState.LoadedData loadedData
    ) {
        JsonObject result = new JsonObject();
        result.addProperty("capture_error", loadedData.captureError());
        result.addProperty("loot_table_id", loadedData.lootTableId());
        result.add("loot_by_age", buildStringMap(loadedData.lootByAge()));
        result.add("recipe_ids", buildStringArray(loadedData.recipeIds()));
        result.add("recipes", buildStringMap(loadedData.recipes()));
        result.add(
                "advancement_ids",
                buildStringArray(loadedData.advancementIds())
        );
        result.add(
                "advancements",
                buildStringMap(loadedData.advancements())
        );
        result.addProperty(
                "recipe_matches_and_crafts_exact",
                loadedData.recipeMatchesAndCraftsExact()
        );
        result.addProperty("loot_exact", loadedData.hasExactLoot());
        result.addProperty("recipes_exact", loadedData.hasExactRecipes());
        result.addProperty(
                "advancements_exact",
                loadedData.hasExactAdvancements()
        );
        result.addProperty("contract_exact", loadedData.hasExactContract());
        return result;
    }

    private static JsonObject buildForestLanternWorldMechanics(
            ForestLanternProbeState.WorldMechanics mechanics
    ) {
        JsonObject result = new JsonObject();
        result.addProperty("phase", mechanics.phase().name());
        result.addProperty("capture_error", mechanics.captureError());
        JsonObject placement = new JsonObject();
        placement.addProperty(
                "capture_error",
                mechanics.placement().captureError()
        );
        placement.add(
                "facings",
                buildStringMap(mechanics.placement().placements())
        );
        placement.addProperty("exact", mechanics.placement().exact());
        placement.addProperty(
                "supports_removed",
                mechanics.placement().supportsRemoved()
        );
        result.add("placement", placement);

        ForestLanternProbeState.ShearsResult shears = mechanics.shears();
        JsonObject shearsResult = new JsonObject();
        shearsResult.addProperty("capture_error", shears.captureError());
        shearsResult.addProperty("player_uuid", shears.playerUuid());
        shearsResult.addProperty("player_name", shears.playerName());
        shearsResult.addProperty("tool_id", shears.toolId());
        shearsResult.addProperty("on_ground", shears.onGround());
        shearsResult.addProperty("can_harvest", shears.canHarvest());
        shearsResult.add("speeds", buildStringMap(shears.speeds()));
        shearsResult.add("deltas", buildStringMap(shears.deltas()));
        shearsResult.addProperty("exact", shears.exact());
        result.add("shears", shearsResult);
        result.add("retain_jump", buildForestLanternJump(mechanics.retainJump()));
        result.add("break_jump", buildForestLanternJump(mechanics.breakJump()));
        result.addProperty("contract_exact", mechanics.hasExactContract());
        return result;
    }

    private static JsonObject buildForestLanternJump(
            ForestLanternProbeState.JumpResult jump
    ) {
        JsonObject result = new JsonObject();
        result.addProperty("capture_error", jump.captureError());
        result.addProperty("player_uuid", jump.playerUuid());
        result.addProperty("player_name", jump.playerName());
        result.addProperty("kind", jump.kind().name());
        result.addProperty("seed", jump.seed());
        result.addProperty(
                "predicted_first_roll",
                Float.toString(jump.predictedFirstRoll())
        );
        result.addProperty(
                "predicted_second_roll",
                Float.toString(jump.predictedSecondRoll())
        );
        result.addProperty(
                "next_roll_after_jump",
                Float.toString(jump.nextRollAfterJump())
        );
        result.addProperty(
                "stepping_position_exact",
                jump.steppingPositionExact()
        );
        result.addProperty("block_removed", jump.blockRemoved());
        result.addProperty("new_item_entity_count", jump.newItemEntityCount());
        result.add("new_drops", buildStringArray(jump.newDrops()));
        result.addProperty(
                "single_callback_guard_exact",
                jump.singleCallbackGuardExact()
        );
        result.addProperty("exact", jump.exact());
        return result;
    }

    private JsonObject buildMetalBlocks() {
        JsonObject metalBlocks = new JsonObject();
        metalBlocks.addProperty(
                "block_registry_id",
                MetalBlockProbeState.BLOCK_REGISTRY_ID
        );
        metalBlocks.addProperty(
                "item_registry_id",
                MetalBlockProbeState.ITEM_REGISTRY_ID
        );
        metalBlocks.addProperty("capture_error", initialMetalBlockState.captureError());
        metalBlocks.add(
                "metal_block_ids",
                buildStringArray(initialMetalBlockState.metalBlockIds())
        );
        metalBlocks.add(
                "metal_block_item_ids",
                buildStringArray(initialMetalBlockState.metalBlockItemIds())
        );
        metalBlocks.addProperty(
                "vanilla_block_class",
                MetalBlockProbeState.VANILLA_BLOCK_CLASS
        );
        metalBlocks.addProperty(
                "block_item_class",
                MetalBlockProbeState.BLOCK_ITEM_CLASS
        );
        metalBlocks.addProperty(
                "properties",
                initialMetalBlockState.canonicalProperties()
        );
        metalBlocks.addProperty(
                "save_representations",
                initialMetalBlockState.canonicalSaveRepresentations()
        );
        JsonObject entries = new JsonObject();
        initialMetalBlockState.entries().forEach((id, entry) ->
                entries.add(id, buildMetalBlock(entry))
        );
        metalBlocks.add("entries", entries);
        JsonObject placement = new JsonObject();
        placement.addProperty("capture_error", initialMetalBlockPlacement.captureError());
        placement.add("positions", buildStringMap(initialMetalBlockPlacement.positions()));
        placement.add(
                "placed_block_ids",
                buildStringMap(initialMetalBlockPlacement.placedBlockIds())
        );
        placement.addProperty("exact", initialMetalBlockPlacement.hasExactPlacement());
        placement.addProperty(
                "stable_after_reload",
                metalBlockPlacementStableAfterReload
        );
        metalBlocks.add("placement", placement);
        metalBlocks.addProperty(
                "same_state_at_server_started",
                serverStartedMetalBlocksRechecked
        );
        metalBlocks.addProperty(
                "registry_stable_after_reload",
                metalBlockRegistryStableAfterReload
        );
        metalBlocks.addProperty(
                "properties_stable_after_reload",
                metalBlockPropertiesStableAfterReload
        );
        metalBlocks.addProperty(
                "tags_stable_after_reload",
                metalBlockTagsStableAfterReload
        );
        metalBlocks.addProperty(
                "stack_nbt_stable_after_reload",
                metalBlockStackNbtStableAfterReload
        );
        return metalBlocks;
    }

    private JsonObject buildAttrahiteBlocks() {
        JsonObject attrahiteBlocks = new JsonObject();
        attrahiteBlocks.addProperty(
                "block_registry_id",
                AttrahiteBlockProbeState.BLOCK_REGISTRY_ID
        );
        attrahiteBlocks.addProperty(
                "item_registry_id",
                AttrahiteBlockProbeState.ITEM_REGISTRY_ID
        );
        attrahiteBlocks.addProperty(
                "capture_error",
                initialAttrahiteBlockState.captureError()
        );
        attrahiteBlocks.add(
                "block_ids",
                buildStringArray(initialAttrahiteBlockState.blockIds())
        );
        attrahiteBlocks.add(
                "block_item_ids",
                buildStringArray(initialAttrahiteBlockState.blockItemIds())
        );
        attrahiteBlocks.addProperty(
                "properties",
                initialAttrahiteBlockState.canonicalProperties()
        );
        attrahiteBlocks.addProperty(
                "tags",
                initialAttrahiteBlockState.canonicalTags()
        );
        attrahiteBlocks.addProperty(
                "save_representations",
                initialAttrahiteBlockState.canonicalSaveRepresentations()
        );
        JsonObject entries = new JsonObject();
        initialAttrahiteBlockState.entries().forEach((id, entry) ->
                entries.add(id, buildAttrahiteBlock(entry))
        );
        attrahiteBlocks.add("entries", entries);

        JsonObject placement = new JsonObject();
        placement.addProperty(
                "capture_error",
                initialAttrahiteBlockPlacement.captureError()
        );
        placement.add(
                "positions",
                buildStringMap(initialAttrahiteBlockPlacement.positions())
        );
        placement.add(
                "placed_block_ids",
                buildStringMap(initialAttrahiteBlockPlacement.placedBlockIds())
        );
        placement.add(
                "placed_states",
                buildStringMap(initialAttrahiteBlockPlacement.placedStates())
        );
        placement.addProperty(
                "exact",
                initialAttrahiteBlockPlacement.hasExactPlacement()
        );
        placement.addProperty("world_save_failure", attrahiteWorldSaveFailure);
        placement.addProperty(
                "world_saved_after_placement",
                attrahiteWorldSavedAfterPlacement
        );
        placement.addProperty(
                "stable_after_reload",
                attrahiteBlockPlacementStableAfterReload
        );
        attrahiteBlocks.add("placement", placement);

        AttrahiteBlockProbeState.LoadedData loadedDataState =
                initialAttrahiteBlockState.loadedData();
        JsonObject loadedData = new JsonObject();
        loadedData.addProperty("capture_error", loadedDataState.captureError());
        loadedData.add(
                "loot_table_ids",
                buildStringArray(loadedDataState.lootTableIds())
        );
        loadedData.add(
                "standard_loot",
                buildStringMap(loadedDataState.standardLoot())
        );
        loadedData.addProperty(
                "raw_silk_touch_loot",
                loadedDataState.rawSilkTouchLoot()
        );
        loadedData.add(
                "raw_fortune_loot",
                buildStringMap(loadedDataState.rawFortuneLoot())
        );
        loadedData.add(
                "recipe_ids",
                buildStringArray(loadedDataState.recipeIds())
        );
        loadedData.add("recipes", buildStringMap(loadedDataState.recipes()));
        loadedData.addProperty(
                "recipes_match_and_craft_exact",
                loadedDataState.recipeMatchesAndCraftsExact()
        );
        loadedData.add(
                "advancement_ids",
                buildStringArray(loadedDataState.advancementIds())
        );
        loadedData.add(
                "advancements",
                buildStringMap(loadedDataState.advancements())
        );
        loadedData.addProperty("exact", loadedDataState.hasExactContract());
        loadedData.addProperty(
                "stable_after_reload",
                attrahiteBlockLoadedDataStableAfterReload
        );
        loadedData.addProperty(
                "fresh_instances_after_reload",
                attrahiteBlockLoadedDataFreshAfterReload
        );
        attrahiteBlocks.add("loaded_data", loadedData);

        attrahiteBlocks.addProperty(
                "captured_at_initial_tag_load",
                attrahiteBlocksCapturedAtInitialTagLoad
        );
        attrahiteBlocks.addProperty(
                "captured_after_server_data_load",
                attrahiteBlocksCapturedAfterServerDataLoad
        );
        attrahiteBlocks.addProperty(
                "same_state_at_server_started",
                serverStartedAttrahiteBlocksRechecked
        );
        attrahiteBlocks.addProperty(
                "registry_stable_after_reload",
                attrahiteBlockRegistryStableAfterReload
        );
        attrahiteBlocks.addProperty(
                "properties_stable_after_reload",
                attrahiteBlockPropertiesStableAfterReload
        );
        attrahiteBlocks.addProperty(
                "tags_stable_after_reload",
                attrahiteBlockTagsStableAfterReload
        );
        attrahiteBlocks.addProperty(
                "stack_nbt_stable_after_reload",
                attrahiteBlockStackNbtStableAfterReload
        );
        attrahiteBlocks.addProperty(
                "exact",
                initialAttrahiteBlockState.hasExactContract()
                        && reloadedAttrahiteBlockState.hasExactContract()
                        && serverStartedAttrahiteBlocksRechecked
                        && attrahiteBlockRegistryStableAfterReload
                        && attrahiteBlockPropertiesStableAfterReload
                        && attrahiteBlockTagsStableAfterReload
                        && attrahiteBlockStackNbtStableAfterReload
                        && attrahiteBlockLoadedDataStableAfterReload
                        && attrahiteBlockLoadedDataFreshAfterReload
                        && attrahiteBlockPlacementStableAfterReload
                        && attrahiteWorldSavedAfterPlacement
        );
        return attrahiteBlocks;
    }

    private JsonObject buildSlitheriteBlocks() {
        JsonObject slitheriteBlocks = new JsonObject();
        slitheriteBlocks.addProperty(
                "block_registry_id",
                SlitheriteBlockProbeState.BLOCK_REGISTRY_ID
        );
        slitheriteBlocks.addProperty(
                "item_registry_id",
                SlitheriteBlockProbeState.ITEM_REGISTRY_ID
        );
        slitheriteBlocks.addProperty(
                "capture_error",
                initialSlitheriteBlockState.captureError()
        );
        slitheriteBlocks.add(
                "block_ids",
                buildStringArray(initialSlitheriteBlockState.blockIds())
        );
        slitheriteBlocks.add(
                "block_item_ids",
                buildStringArray(initialSlitheriteBlockState.blockItemIds())
        );
        slitheriteBlocks.addProperty(
                "aggregate_state_count",
                initialSlitheriteBlockState.aggregateStateCount()
        );
        slitheriteBlocks.addProperty(
                "aggregate_unique_raw_id_count",
                initialSlitheriteBlockState.aggregateUniqueRawIdCount()
        );
        slitheriteBlocks.addProperty(
                "registry",
                initialSlitheriteBlockState.canonicalRegistry()
        );
        slitheriteBlocks.addProperty(
                "properties",
                initialSlitheriteBlockState.canonicalProperties()
        );
        slitheriteBlocks.addProperty(
                "tags",
                initialSlitheriteBlockState.canonicalTags()
        );
        JsonObject entries = new JsonObject();
        initialSlitheriteBlockState.entries().forEach((id, entry) ->
                entries.add(id, buildSlitheriteBlock(entry))
        );
        slitheriteBlocks.add("entries", entries);

        JsonObject nativePlacement = new JsonObject();
        nativePlacement.addProperty(
                "capture_error",
                initialSlitheriteNativePlacement.captureError()
        );
        nativePlacement.addProperty(
                "canonical",
                initialSlitheriteNativePlacement.canonical()
        );
        JsonObject nativeEntries = new JsonObject();
        initialSlitheriteNativePlacement.entries().forEach((id, entry) ->
                nativeEntries.add(id, buildSlitheriteNativePlacement(entry, id))
        );
        nativePlacement.add("entries", nativeEntries);
        nativePlacement.addProperty(
                "exact",
                initialSlitheriteNativePlacement.hasExactPlacement()
        );
        slitheriteBlocks.add("native_placement", nativePlacement);

        JsonObject placement = new JsonObject();
        placement.add(
                "initial",
                buildSlitheritePlacement(initialSlitheriteBlockPlacement)
        );
        placement.add(
                "saved",
                buildSlitheritePlacement(savedSlitheriteBlockPlacement)
        );
        placement.add(
                "reloaded",
                buildSlitheritePlacement(reloadedSlitheriteBlockPlacement)
        );
        placement.addProperty("world_save_failure", slitheriteWorldSaveFailure);
        placement.addProperty(
                "world_saved_after_placement",
                slitheriteWorldSavedAfterPlacement
        );
        placement.addProperty(
                "saved_after_force_save",
                slitheriteBlockPlacementSavedAfterForceSave
        );
        placement.addProperty(
                "stable_after_reload",
                slitheriteBlockPlacementStableAfterReload
        );
        slitheriteBlocks.add("placement", placement);

        SlitheriteBlockProbeState.LoadedData loadedDataState =
                initialSlitheriteBlockState.loadedData();
        JsonObject loadedData = new JsonObject();
        loadedData.addProperty("capture_error", loadedDataState.captureError());
        loadedData.add(
                "loot_table_ids",
                buildStringArray(loadedDataState.lootTableIds())
        );
        loadedData.add("self_drops", buildStringMap(loadedDataState.selfDrops()));
        loadedData.add(
                "double_slab_drops",
                buildStringMap(loadedDataState.doubleSlabDrops())
        );
        loadedData.add(
                "recipe_ids",
                buildStringArray(loadedDataState.recipeIds())
        );
        loadedData.add("recipes", buildStringMap(loadedDataState.recipes()));
        loadedData.add(
                "advancement_ids",
                buildStringArray(loadedDataState.advancementIds())
        );
        loadedData.add(
                "related_recipe_ids",
                buildStringArray(loadedDataState.relatedRecipeIds())
        );
        loadedData.add(
                "related_recipes",
                buildStringMap(loadedDataState.relatedRecipes())
        );
        loadedData.addProperty("exact", loadedDataState.hasExactContract());
        loadedData.addProperty(
                "stable_after_reload",
                slitheriteBlockLoadedDataStableAfterReload
        );
        loadedData.addProperty(
                "fresh_instances_after_reload",
                slitheriteBlockLoadedDataFreshAfterReload
        );
        slitheriteBlocks.add("loaded_data", loadedData);

        JsonObject behavior = new JsonObject();
        behavior.addProperty("capture_error", slitheriteBehaviorProbe.captureError());
        behavior.addProperty("completed", slitheriteBehaviorProbe.completed());
        behavior.addProperty(
                "button_interaction_accepted",
                slitheriteBehaviorProbe.buttonInteractionAccepted()
        );
        behavior.addProperty(
                "button_activated",
                slitheriteBehaviorProbe.buttonActivated()
        );
        behavior.addProperty(
                "button_reset_scheduled",
                slitheriteBehaviorProbe.buttonResetScheduled()
        );
        behavior.addProperty("button_reset", slitheriteBehaviorProbe.buttonReset());
        behavior.addProperty(
                "button_elapsed_ticks",
                slitheriteBehaviorProbe.buttonElapsedTicks()
        );
        behavior.addProperty(
                "item_entity_advanced",
                slitheriteBehaviorProbe.itemEntityAdvanced()
        );
        behavior.addProperty("item_ignored", slitheriteBehaviorProbe.itemIgnored());
        behavior.addProperty(
                "living_entity_advanced",
                slitheriteBehaviorProbe.livingEntityAdvanced()
        );
        behavior.addProperty(
                "living_activated",
                slitheriteBehaviorProbe.livingActivated()
        );
        behavior.addProperty(
                "pressure_plate_reset",
                slitheriteBehaviorProbe.pressurePlateReset()
        );
        behavior.addProperty(
                "fixture_reset_exact",
                slitheriteBehaviorProbe.fixtureResetExact()
        );
        behavior.addProperty(
                "button_description",
                slitheriteBehaviorProbe.buttonDescription()
        );
        behavior.addProperty(
                "pressure_plate_description",
                slitheriteBehaviorProbe.pressurePlateDescription()
        );
        behavior.addProperty("exact", slitheriteBehaviorProbe.exact());
        slitheriteBlocks.add("behavior", behavior);

        slitheriteBlocks.addProperty(
                "captured_at_initial_tag_load",
                slitheriteBlocksCapturedAtInitialTagLoad
        );
        slitheriteBlocks.addProperty(
                "captured_after_server_data_load",
                slitheriteBlocksCapturedAfterServerDataLoad
        );
        slitheriteBlocks.addProperty(
                "same_state_at_server_started",
                serverStartedSlitheriteBlocksRechecked
        );
        slitheriteBlocks.addProperty(
                "registry_stable_after_reload",
                slitheriteBlockRegistryStableAfterReload
        );
        slitheriteBlocks.addProperty(
                "default_states_stable_after_reload",
                slitheriteBlockDefaultStatesStableAfterReload
        );
        slitheriteBlocks.addProperty(
                "tags_stable_after_reload",
                slitheriteBlockTagsStableAfterReload
        );
        slitheriteBlocks.addProperty(
                "exact",
                initialSlitheriteBlockState.hasExactContract()
                        && reloadedSlitheriteBlockState.hasExactContract()
                        && serverStartedSlitheriteBlocksRechecked
                        && initialSlitheriteNativePlacement.hasExactPlacement()
                        && initialSlitheriteBlockPlacement.hasExactPlacement()
                        && slitheriteBehaviorProbe.exact()
                        && slitheriteWorldSavedAfterPlacement
                        && slitheriteBlockPlacementSavedAfterForceSave
                        && slitheriteBlockRegistryStableAfterReload
                        && slitheriteBlockDefaultStatesStableAfterReload
                        && slitheriteBlockTagsStableAfterReload
                        && slitheriteBlockLoadedDataStableAfterReload
                        && slitheriteBlockLoadedDataFreshAfterReload
                        && slitheriteBlockPlacementStableAfterReload
        );
        return slitheriteBlocks;
    }

    private static JsonObject buildSlitheritePlacement(
            SlitheriteBlockProbeState.PlacementState state
    ) {
        JsonObject placement = new JsonObject();
        placement.addProperty("capture_error", state.captureError());
        placement.add("positions", buildStringMap(state.positions()));
        placement.add(
                "support_positions",
                buildStringMap(state.supportPositions())
        );
        placement.add(
                "placed_block_ids",
                buildStringMap(state.placedBlockIds())
        );
        placement.add("placed_states", buildStringMap(state.placedStates()));
        placement.add(
                "support_block_ids",
                buildStringMap(state.supportBlockIds())
        );
        placement.addProperty("exact", state.hasExactPlacement());
        return placement;
    }

    private static JsonObject buildSlitheriteNativePlacement(
            SlitheriteNativePlacementEntry entry,
            String id
    ) {
        JsonObject placement = new JsonObject();
        placement.addProperty("action_result", entry.actionResult());
        placement.addProperty("accepted", entry.accepted());
        placement.addProperty("before_count", entry.beforeCount());
        placement.addProperty("after_count", entry.afterCount());
        placement.addProperty("block_item_mapping", entry.blockItemMapping());
        placement.addProperty("placed_id", entry.placedId());
        placement.addProperty("placed_state", entry.placedState());
        placement.addProperty("exact", entry.exact(id));
        return placement;
    }

    private JsonObject buildLootCondition() {
        JsonObject lootCondition = new JsonObject();
        lootCondition.addProperty(
                "registry_id",
                LootConditionProbeState.LOOT_CONDITION_REGISTRY_ID
        );
        lootCondition.addProperty("condition_id", lootConditionState.conditionId());
        lootCondition.add(
                "etherology_condition_ids",
                buildStringArray(lootConditionState.etherologyConditionIds())
        );
        lootCondition.addProperty("serializer_class", lootConditionState.serializerClass());
        lootCondition.addProperty("probe_table_id", lootConditionState.probeTableId());
        lootCondition.add(
                "empty_tool_items",
                buildStringArray(lootConditionState.emptyToolItems())
        );
        lootCondition.add(
                "fortune_one_items",
                buildStringArray(lootConditionState.fortuneOneItems())
        );
        lootCondition.addProperty(
                "same_state_at_server_started",
                serverStartedLootConditionRechecked
        );
        lootCondition.addProperty(
                "registry_and_behavior_stable_after_reload",
                lootConditionStableAfterReload
        );
        lootCondition.addProperty(
                "probe_table_instance_replaced_after_reload",
                lootTableInstanceReplacedAfterReload
        );
        return lootCondition;
    }

    private JsonObject buildEtherSources() {
        JsonObject etherSources = new JsonObject();
        etherSources.addProperty("listener_class", EtherSourceProbeState.LOADER_CLASS_NAME);
        etherSources.addProperty(
                "resource_directory",
                EtherSourceProbeState.RESOURCE_DIRECTORY
        );
        etherSources.add("initial", buildEtherSourceCapture(initialEtherSourceState));
        etherSources.add(
                "server_started",
                buildEtherSourceCapture(serverStartedEtherSourceState)
        );
        etherSources.add("reloaded", buildEtherSourceCapture(reloadedEtherSourceState));
        etherSources.addProperty(
                "same_at_server_started",
                serverStartedEtherSourcesRechecked
        );
        etherSources.addProperty(
                "changed_after_reload",
                !initialEtherSourceState.sameEntries(reloadedEtherSourceState)
        );
        return etherSources;
    }

    private JsonObject buildReload() {
        JsonObject reload = new JsonObject();
        reload.addProperty("pack_directory", reloadPackDirectory);
        reload.add("pack_resources", buildStringArray(reloadPackResourcePaths));
        reload.addProperty("enabled_pack_name", ReloadDataPackWriter.ENABLED_PACK_NAME);
        reload.add(
                "enabled_data_pack_names",
                buildStringArray(enabledDataPackNamesAfterReload)
        );
        reload.addProperty("enabled_data_packs_exact", enabledDataPacksExact);
        reload.addProperty("command", reloadRequested ? "reload" : "");
        reload.addProperty("command_result", reloadCommandResult);
        reload.addProperty("failure", reloadFailure);
        reload.addProperty("completed", reloadCompleted);
        reload.addProperty("update_cause", reloadUpdateCause);
        reload.addProperty("should_update_static_data", reloadShouldUpdateStaticData);
        reload.addProperty("registry_stable", registryStableAfterReload);
        reload.addProperty("tags_stable", tagsStableAfterReload);
        reload.addProperty(
                "loot_condition_registry_and_behavior_stable",
                lootConditionStableAfterReload
        );
        reload.addProperty(
                "loot_table_instance_replaced",
                lootTableInstanceReplacedAfterReload
        );
        reload.addProperty(
                "enchantment_registry_stable",
                enchantmentRegistryStableAfterReload
        );
        reload.addProperty(
                "enchantment_properties_stable",
                enchantmentPropertiesStableAfterReload
        );
        reload.addProperty(
                "enchantment_tag_stable",
                enchantmentTagStableAfterReload
        );
        reload.addProperty(
                "particle_registry_stable",
                particleRegistryStableAfterReload
        );
        reload.addProperty(
                "particle_type_contract_stable",
                particleTypeContractStableAfterReload
        );
        reload.addProperty(
                "particle_wire_contract_stable",
                particleWireContractStableAfterReload
        );
        reload.addProperty(
                "material_item_registry_stable",
                materialItemRegistryStableAfterReload
        );
        reload.addProperty(
                "material_item_properties_stable",
                materialItemPropertiesStableAfterReload
        );
        reload.addProperty(
                "material_item_stack_nbt_stable",
                materialItemStackNbtStableAfterReload
        );
        reload.addProperty(
                "food_item_registry_stable",
                foodItemRegistryStableAfterReload
        );
        reload.addProperty(
                "food_item_properties_stable",
                foodItemPropertiesStableAfterReload
        );
        reload.addProperty(
                "food_item_stack_nbt_stable",
                foodItemStackNbtStableAfterReload
        );
        reload.addProperty(
                "food_consumption_stable",
                foodConsumptionStableAfterReload
        );
        reload.addProperty(
                "forest_lantern_registry_stable",
                forestLanternRegistryStableAfterReload
        );
        reload.addProperty(
                "forest_lantern_states_stable",
                forestLanternStatesStableAfterReload
        );
        reload.addProperty(
                "forest_lantern_tags_stable",
                forestLanternTagsStableAfterReload
        );
        reload.addProperty(
                "forest_lantern_loaded_data_stable",
                forestLanternLoadedDataStableAfterReload
        );
        reload.addProperty(
                "forest_lantern_loaded_data_fresh",
                forestLanternLoadedDataFreshAfterReload
        );
        reload.addProperty(
                "forest_lantern_mechanics_stable",
                forestLanternMechanicsStableAfterReload
        );
        reload.addProperty(
                "metal_block_registry_stable",
                metalBlockRegistryStableAfterReload
        );
        reload.addProperty(
                "metal_block_properties_stable",
                metalBlockPropertiesStableAfterReload
        );
        reload.addProperty(
                "metal_block_tags_stable",
                metalBlockTagsStableAfterReload
        );
        reload.addProperty(
                "metal_block_stack_nbt_stable",
                metalBlockStackNbtStableAfterReload
        );
        reload.addProperty(
                "metal_block_placement_stable",
                metalBlockPlacementStableAfterReload
        );
        reload.addProperty(
                "attrahite_block_registry_stable",
                attrahiteBlockRegistryStableAfterReload
        );
        reload.addProperty(
                "attrahite_block_properties_stable",
                attrahiteBlockPropertiesStableAfterReload
        );
        reload.addProperty(
                "attrahite_block_tags_stable",
                attrahiteBlockTagsStableAfterReload
        );
        reload.addProperty(
                "attrahite_block_stack_nbt_stable",
                attrahiteBlockStackNbtStableAfterReload
        );
        reload.addProperty(
                "attrahite_block_loaded_data_stable",
                attrahiteBlockLoadedDataStableAfterReload
        );
        reload.addProperty(
                "attrahite_block_loaded_data_fresh",
                attrahiteBlockLoadedDataFreshAfterReload
        );
        reload.addProperty(
                "attrahite_block_placement_stable",
                attrahiteBlockPlacementStableAfterReload
        );
        reload.addProperty(
                "slitherite_block_registry_stable",
                slitheriteBlockRegistryStableAfterReload
        );
        reload.addProperty(
                "slitherite_block_default_states_stable",
                slitheriteBlockDefaultStatesStableAfterReload
        );
        reload.addProperty(
                "slitherite_block_tags_stable",
                slitheriteBlockTagsStableAfterReload
        );
        reload.addProperty(
                "slitherite_block_loaded_data_stable",
                slitheriteBlockLoadedDataStableAfterReload
        );
        reload.addProperty(
                "slitherite_block_loaded_data_fresh",
                slitheriteBlockLoadedDataFreshAfterReload
        );
        reload.addProperty(
                "slitherite_block_placement_stable",
                slitheriteBlockPlacementStableAfterReload
        );
        reload.addProperty("stop_requested_after_completion", stopRequestedAfterReload);
        return reload;
    }

    private JsonObject buildTags() {
        JsonObject tags = new JsonObject();
        tags.addProperty("update_cause", updateCause);
        tags.addProperty("should_update_static_data", shouldUpdateStaticData);
        tags.addProperty("update_count", tagUpdateCount);
        tags.addProperty("reload_update_cause", reloadUpdateCause);
        tags.addProperty("reload_should_update_static_data", reloadShouldUpdateStaticData);
        tags.add(
                "vibrations",
                buildTag(
                        "minecraft:vibrations",
                        vibrationsContainsEvent,
                        vibrationsEtherologyEventIds
                )
        );
        tags.add(
                "warden_can_listen",
                buildTag(
                        "minecraft:warden_can_listen",
                        wardenCanListenContainsEvent,
                        wardenCanListenEtherologyEventIds
                )
        );
        JsonArray etherologyTagIdsArray = new JsonArray();
        etherologyTagIds.forEach(etherologyTagIdsArray::add);
        tags.add("etherology_tag_ids", etherologyTagIdsArray);
        tags.addProperty(
                "same_membership_at_server_started",
                serverStartedTagsRechecked
        );
        tags.addProperty("stable_after_reload", tagsStableAfterReload);
        return tags;
    }

    private JsonArray buildLifecycle() {
        return buildStringArray(lifecycle);
    }

    private static JsonObject buildLoadedMod(boolean loaded) {
        JsonObject mod = new JsonObject();
        mod.addProperty("loaded", loaded);
        return mod;
    }

    private static JsonObject buildTag(
            String id,
            boolean containsEvent,
            List<String> etherologyEventIds
    ) {
        JsonObject tag = new JsonObject();
        tag.addProperty("id", id);
        tag.addProperty("contains_event", containsEvent);
        JsonArray etherologyEventIdsArray = new JsonArray();
        etherologyEventIds.forEach(etherologyEventIdsArray::add);
        tag.add("etherology_event_ids", etherologyEventIdsArray);
        return tag;
    }

    private static JsonObject buildEnchantment(
            String id,
            String className,
            int maxLevel,
            List<Integer> minPowers,
            List<Integer> maxPowers,
            boolean inNonTreasure
    ) {
        JsonObject enchantment = new JsonObject();
        enchantment.addProperty("id", id);
        enchantment.addProperty("class", className);
        enchantment.addProperty("max_level", maxLevel);
        enchantment.add("min_powers", buildIntegerArray(minPowers));
        enchantment.add("max_powers", buildIntegerArray(maxPowers));
        enchantment.addProperty("in_non_treasure", inNonTreasure);
        return enchantment;
    }

    private static JsonObject buildParticle(
            ParticleProbeState.ParticleEntry entry
    ) {
        JsonObject particle = new JsonObject();
        particle.addProperty("id", entry.id());
        particle.addProperty("family", entry.family());
        particle.addProperty("type_class", entry.typeClass());
        particle.addProperty("should_always_spawn", entry.shouldAlwaysSpawn());
        particle.addProperty("codec_present", entry.codecPresent());
        particle.addProperty(
                "parameters_factory_present",
                entry.parametersFactoryPresent()
        );
        particle.addProperty(
                "factory_sample_effect_class",
                entry.factorySampleEffectClass()
        );
        particle.addProperty(
                "factory_sample_type_matches",
                entry.factorySampleTypeMatches()
        );
        particle.addProperty(
                "factory_sample_as_string",
                entry.factorySampleAsString()
        );
        particle.addProperty(
                "packet_round_trip_exact",
                entry.packetRoundTripExact()
        );
        particle.addProperty(
                "codec_round_trip_exact",
                entry.codecRoundTripExact()
        );
        return particle;
    }

    private static JsonObject buildMaterialItem(
            MaterialItemProbeState.MaterialItemEntry entry
    ) {
        JsonObject materialItem = new JsonObject();
        materialItem.addProperty("id", entry.id());
        materialItem.addProperty("runtime_class", entry.runtimeClass());
        materialItem.addProperty("max_count", entry.maxCount());
        materialItem.addProperty("serialized_id", entry.serializedId());
        materialItem.addProperty("serialized_count", entry.serializedCount());
        materialItem.add("serialized_keys", buildStringArray(entry.serializedKeys()));
        materialItem.addProperty("round_trip_exact", entry.roundTripExact());
        materialItem.addProperty("save_representation", entry.saveRepresentation());
        return materialItem;
    }

    private static JsonObject buildFoodItem(FoodItemProbeState.FoodItemEntry entry) {
        JsonObject foodItem = new JsonObject();
        foodItem.addProperty("id", entry.id());
        foodItem.addProperty("runtime_class", entry.runtimeClass());
        foodItem.addProperty("max_count", entry.maxCount());
        foodItem.addProperty("is_food", entry.food());
        foodItem.addProperty("hunger", entry.hunger());
        foodItem.addProperty("saturation_modifier", entry.saturationModifier());
        foodItem.addProperty("always_edible", entry.alwaysEdible());
        foodItem.addProperty("status_effect_count", entry.statusEffectCount());
        foodItem.addProperty("has_recipe_remainder", entry.hasRecipeRemainder());
        foodItem.addProperty("recipe_remainder_id", entry.recipeRemainderId());
        foodItem.addProperty("serialized_id", entry.serializedId());
        foodItem.addProperty("serialized_count", entry.serializedCount());
        foodItem.add("serialized_keys", buildStringArray(entry.serializedKeys()));
        foodItem.addProperty("round_trip_exact", entry.roundTripExact());
        foodItem.addProperty("save_representation", entry.saveRepresentation());
        return foodItem;
    }

    private static JsonObject buildFoodConsumptionCapture(
            FoodItemProbeState.FoodConsumptionState state
    ) {
        JsonObject capture = new JsonObject();
        capture.addProperty("capture_error", state.captureError());
        capture.addProperty("player_class", state.playerClass());
        capture.addProperty("player_uuid", state.playerUuid());
        capture.addProperty("player_name", state.playerName());
        capture.addProperty("item_id", state.itemId());
        capture.addProperty("result_item_id", state.resultItemId());
        capture.addProperty("initial_hunger", state.initialHunger());
        capture.addProperty("initial_saturation", state.initialSaturation());
        capture.addProperty("initial_stack_count", state.initialStackCount());
        capture.addProperty("result_hunger", state.resultHunger());
        capture.addProperty("result_saturation", state.resultSaturation());
        capture.addProperty("result_stack_count", state.resultStackCount());
        capture.addProperty("same_stack_instance", state.sameStackInstance());
        capture.addProperty("exact", state.hasExactConsumption());
        return capture;
    }

    private static JsonObject buildMetalBlock(
            MetalBlockProbeState.MetalBlockEntry entry
    ) {
        JsonObject metalBlock = new JsonObject();
        metalBlock.addProperty("block_id", entry.blockId());
        metalBlock.addProperty("item_id", entry.itemId());
        metalBlock.addProperty("block_class", entry.blockClass());
        metalBlock.addProperty("item_class", entry.itemClass());
        metalBlock.addProperty("block_item", entry.blockItem());
        metalBlock.addProperty(
                "block_item_maps_to_block",
                entry.blockItemMapsToBlock()
        );
        metalBlock.addProperty(
                "block_as_item_matches",
                entry.blockAsItemMatches()
        );
        metalBlock.addProperty("hardness", entry.hardness());
        metalBlock.addProperty("blast_resistance", entry.blastResistance());
        metalBlock.addProperty("map_color_id", entry.mapColorId());
        metalBlock.addProperty("metal_sound_group", entry.metalSoundGroup());
        metalBlock.addProperty("tool_required", entry.toolRequired());
        metalBlock.addProperty("luminance", entry.luminance());
        metalBlock.addProperty("opaque", entry.opaque());
        metalBlock.addProperty("full_cube", entry.fullCube());
        metalBlock.addProperty("pickaxe_mineable", entry.pickaxeMineable());
        metalBlock.addProperty("needs_iron_tool", entry.needsIronTool());
        metalBlock.addProperty("beacon_base", entry.beaconBase());
        metalBlock.addProperty("max_count", entry.maxCount());
        metalBlock.addProperty("serialized_id", entry.serializedId());
        metalBlock.addProperty("serialized_count", entry.serializedCount());
        metalBlock.add("serialized_keys", buildStringArray(entry.serializedKeys()));
        metalBlock.addProperty("round_trip_exact", entry.roundTripExact());
        metalBlock.addProperty("save_representation", entry.saveRepresentation());
        return metalBlock;
    }

    private static JsonObject buildAttrahiteBlock(
            AttrahiteBlockProbeState.AttrahiteBlockEntry entry
    ) {
        JsonObject attrahiteBlock = new JsonObject();
        attrahiteBlock.addProperty("block_id", entry.blockId());
        attrahiteBlock.addProperty("item_id", entry.itemId());
        attrahiteBlock.addProperty("block_class", entry.blockClass());
        attrahiteBlock.addProperty("item_class", entry.itemClass());
        attrahiteBlock.addProperty("block_item", entry.blockItem());
        attrahiteBlock.addProperty(
                "block_item_maps_to_block",
                entry.blockItemMapsToBlock()
        );
        attrahiteBlock.addProperty(
                "block_as_item_matches",
                entry.blockAsItemMatches()
        );
        attrahiteBlock.addProperty("hardness", entry.hardness());
        attrahiteBlock.addProperty("blast_resistance", entry.blastResistance());
        attrahiteBlock.addProperty("map_color_id", entry.mapColorId());
        attrahiteBlock.addProperty("sound_group", entry.soundGroup());
        attrahiteBlock.addProperty("tool_required", entry.toolRequired());
        attrahiteBlock.addProperty("luminance", entry.luminance());
        attrahiteBlock.addProperty("opaque", entry.opaque());
        attrahiteBlock.addProperty("full_cube", entry.fullCube());
        attrahiteBlock.addProperty("transparent", entry.transparent());
        attrahiteBlock.addProperty("piston_behavior", entry.pistonBehavior());
        attrahiteBlock.addProperty("state_count", entry.stateCount());
        attrahiteBlock.addProperty("default_state", entry.defaultState());
        attrahiteBlock.addProperty("pickaxe_mineable", entry.pickaxeMineable());
        attrahiteBlock.addProperty("needs_stone_tool", entry.needsStoneTool());
        attrahiteBlock.addProperty("block_slab", entry.blockSlab());
        attrahiteBlock.addProperty("item_slab", entry.itemSlab());
        attrahiteBlock.addProperty("block_stairs", entry.blockStairs());
        attrahiteBlock.addProperty("item_stairs", entry.itemStairs());
        attrahiteBlock.addProperty("max_count", entry.maxCount());
        attrahiteBlock.addProperty("serialized_id", entry.serializedId());
        attrahiteBlock.addProperty("serialized_count", entry.serializedCount());
        attrahiteBlock.add(
                "serialized_keys",
                buildStringArray(entry.serializedKeys())
        );
        attrahiteBlock.addProperty("round_trip_exact", entry.roundTripExact());
        attrahiteBlock.addProperty(
                "save_representation",
                entry.saveRepresentation()
        );
        return attrahiteBlock;
    }

    private static JsonObject buildSlitheriteBlock(
            SlitheriteBlockProbeState.SlitheriteBlockEntry entry
    ) {
        JsonObject slitheriteBlock = new JsonObject();
        slitheriteBlock.addProperty("block_id", entry.blockId());
        slitheriteBlock.addProperty("item_id", entry.itemId());
        slitheriteBlock.addProperty("block_class", entry.blockClass());
        slitheriteBlock.addProperty("item_class", entry.itemClass());
        slitheriteBlock.addProperty("block_item", entry.blockItem());
        slitheriteBlock.addProperty(
                "block_item_maps_to_block",
                entry.blockItemMapsToBlock()
        );
        slitheriteBlock.addProperty(
                "block_as_item_matches",
                entry.blockAsItemMatches()
        );
        slitheriteBlock.add(
                "default_properties",
                buildStringMap(entry.defaultProperties())
        );
        slitheriteBlock.addProperty("default_state", entry.defaultState());
        slitheriteBlock.addProperty("state_count", entry.stateCount());
        slitheriteBlock.addProperty(
                "default_state_raw_id",
                entry.defaultStateRawId()
        );
        slitheriteBlock.add(
                "state_raw_ids",
                buildIntegerArray(entry.stateRawIds())
        );
        slitheriteBlock.addProperty("pickaxe_mineable", entry.pickaxeMineable());
        slitheriteBlock.addProperty("needs_stone_tool", entry.needsStoneTool());
        slitheriteBlock.addProperty("block_slab", entry.blockSlab());
        slitheriteBlock.addProperty("item_slab", entry.itemSlab());
        slitheriteBlock.addProperty("block_stairs", entry.blockStairs());
        slitheriteBlock.addProperty("item_stairs", entry.itemStairs());
        slitheriteBlock.addProperty("block_wall", entry.blockWall());
        slitheriteBlock.addProperty("item_wall", entry.itemWall());
        slitheriteBlock.addProperty("block_stone_brick", entry.blockStoneBrick());
        slitheriteBlock.addProperty(
                "block_stone_pressure_plate",
                entry.blockStonePressurePlate()
        );
        slitheriteBlock.addProperty("item_button", entry.itemButton());
        return slitheriteBlock;
    }

    private static JsonObject buildSealType(
            ParticleProbeState.SealTypeEntry entry
    ) {
        JsonObject sealType = new JsonObject();
        sealType.addProperty("enum_name", entry.enumName());
        sealType.addProperty("as_string", entry.asString());
        sealType.addProperty("start_color", entry.startColor());
        sealType.addProperty("end_color", entry.endColor());
        sealType.addProperty("texture_id", entry.textureId());
        sealType.addProperty("texture_light_id", entry.textureLightId());
        return sealType;
    }

    private static JsonObject buildEtherSourceCapture(EtherSourceProbeState state) {
        JsonObject capture = new JsonObject();
        capture.addProperty("capture_error", state.captureError());
        JsonObject entries = new JsonObject();
        state.entries().forEach(entries::addProperty);
        capture.add("entries", entries);
        return capture;
    }

    enum SlitheriteBehaviorPhase {
        WAITING_FOR_ENTITY_TICKING,
        WAITING_FOR_ITEM_TICK,
        WAITING_FOR_LIVING_ACTIVATION,
        WAITING_FOR_RESET,
        COMPLETE,
        FAILED
    }

    record SlitheriteNativePlacementEntry(
            String actionResult,
            boolean accepted,
            int beforeCount,
            int afterCount,
            boolean blockItemMapping,
            String placedId,
            String placedState
    ) {

        static SlitheriteNativePlacementEntry failed() {
            return new SlitheriteNativePlacementEntry(
                    "",
                    false,
                    -1,
                    -1,
                    false,
                    "",
                    ""
            );
        }

        boolean exact(String id) {
            SlitheriteBlockProbeState.SlitheriteBlockSpec spec =
                    SlitheriteBlockProbeState.EXPECTED_BLOCKS.get(id);
            return spec != null
                    && "CONSUME".equals(actionResult)
                    && accepted
                    && beforeCount == 1
                    && afterCount == 0
                    && blockItemMapping
                    && id.equals(placedId)
                    && spec.defaultState().equals(placedState);
        }

        String description() {
            return "action=" + actionResult
                    + "|accepted=" + accepted
                    + "|before=" + beforeCount
                    + "|after=" + afterCount
                    + "|mapping=" + blockItemMapping
                    + "|placed=" + placedId
                    + "|state=" + placedState;
        }
    }

    record SlitheriteNativePlacementState(
            String captureError,
            Map<String, SlitheriteNativePlacementEntry> entries
    ) {

        SlitheriteNativePlacementState {
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }

        static SlitheriteNativePlacementState missing() {
            return failed("not captured");
        }

        static SlitheriteNativePlacementState failed(String failure) {
            return new SlitheriteNativePlacementState(failure, Map.of());
        }

        boolean hasExactPlacement() {
            return captureError.isEmpty()
                    && entries.keySet().equals(
                            SlitheriteBlockProbeState.EXPECTED_BLOCKS.keySet()
                    )
                    && entries.entrySet().stream().allMatch(entry ->
                            entry.getValue().exact(entry.getKey())
                    );
        }

        String canonical() {
            return entries.entrySet().stream()
                    .map(entry -> entry.getKey() + "="
                            + entry.getValue().description())
                    .collect(java.util.stream.Collectors.joining(","));
        }

        static String expectedCanonical() {
            return SlitheriteBlockProbeState.EXPECTED_BLOCKS.entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + "=action=CONSUME"
                            + "|accepted=true|before=1|after=0|mapping=true"
                            + "|placed=" + entry.getKey()
                            + "|state=" + entry.getValue().defaultState())
                    .collect(java.util.stream.Collectors.joining(","));
        }
    }

    private record SlitheritePlacementSetup(
            SlitheriteNativePlacementState placement,
            ServerPlayerEntity player
    ) {
    }

    private record SlitheriteBehaviorSequence(
            SlitheriteBehaviorPhase phase,
            ServerPlayerEntity player,
            long deadline,
            long buttonActivationTick,
            boolean buttonInteractionAccepted,
            boolean buttonActivated,
            boolean buttonResetScheduled,
            ItemEntity itemEntity,
            int itemInitialAge,
            boolean itemEntityAdvanced,
            boolean itemIgnored,
            PigEntity pigEntity,
            int pigInitialAge,
            boolean livingEntityAdvanced,
            boolean livingActivated
    ) {

        SlitheriteBehaviorSequence withPhase(SlitheriteBehaviorPhase replacement) {
            return new SlitheriteBehaviorSequence(
                    replacement,
                    player,
                    deadline,
                    buttonActivationTick,
                    buttonInteractionAccepted,
                    buttonActivated,
                    buttonResetScheduled,
                    itemEntity,
                    itemInitialAge,
                    itemEntityAdvanced,
                    itemIgnored,
                    pigEntity,
                    pigInitialAge,
                    livingEntityAdvanced,
                    livingActivated
            );
        }
    }

    record SlitheriteBehaviorProbe(
            String captureError,
            boolean completed,
            boolean buttonInteractionAccepted,
            boolean buttonActivated,
            boolean buttonResetScheduled,
            boolean buttonReset,
            long buttonElapsedTicks,
            boolean itemEntityAdvanced,
            boolean itemIgnored,
            boolean livingEntityAdvanced,
            boolean livingActivated,
            boolean pressurePlateReset,
            boolean fixtureResetExact
    ) {

        static SlitheriteBehaviorProbe missing() {
            return new SlitheriteBehaviorProbe(
                    "not captured",
                    false,
                    false,
                    false,
                    false,
                    false,
                    0L,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }

        static SlitheriteBehaviorProbe failed(String failure) {
            return new SlitheriteBehaviorProbe(
                    failure,
                    true,
                    false,
                    false,
                    false,
                    false,
                    0L,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }

        private static SlitheriteBehaviorProbe complete(
                SlitheriteBehaviorSequence sequence,
                boolean buttonReset,
                long buttonElapsedTicks,
                boolean pressurePlateReset,
                boolean fixtureResetExact
        ) {
            return new SlitheriteBehaviorProbe(
                    "",
                    true,
                    sequence.buttonInteractionAccepted(),
                    sequence.buttonActivated(),
                    sequence.buttonResetScheduled(),
                    buttonReset,
                    buttonElapsedTicks,
                    sequence.itemEntityAdvanced(),
                    sequence.itemIgnored(),
                    sequence.livingEntityAdvanced(),
                    sequence.livingActivated(),
                    pressurePlateReset,
                    fixtureResetExact
            );
        }

        boolean buttonExact() {
            return captureError.isEmpty()
                    && completed
                    && buttonInteractionAccepted
                    && buttonActivated
                    && buttonResetScheduled
                    && buttonReset
                    && buttonElapsedTicks >= 20L;
        }

        boolean pressurePlateExact() {
            return captureError.isEmpty()
                    && completed
                    && itemEntityAdvanced
                    && itemIgnored
                    && livingEntityAdvanced
                    && livingActivated
                    && pressurePlateReset;
        }

        boolean exact() {
            return buttonExact() && pressurePlateExact() && fixtureResetExact;
        }

        String buttonDescription() {
            return "accepted=" + buttonInteractionAccepted
                    + "|powered=" + buttonActivated
                    + "|scheduled=" + buttonResetScheduled
                    + "|elapsed=" + buttonElapsedTicks
                    + "|reset=" + buttonReset;
        }

        String pressurePlateDescription() {
            return "item_advanced=" + itemEntityAdvanced
                    + "|item_powered=" + !itemIgnored
                    + "|living_advanced=" + livingEntityAdvanced
                    + "|living_powered=" + livingActivated
                    + "|reset=" + pressurePlateReset;
        }
    }

    private static Map<String, List<String>> collectEtherologyTagMemberships() {
        Map<String, List<String>> memberships = new TreeMap<>();
        Registries.GAME_EVENT.streamTagsAndEntries().forEach(pair -> {
            List<String> etherologyEventIds = pair.getSecond().stream()
                    .map(entry -> Registries.GAME_EVENT.getId(entry.value()))
                    .filter(identifier -> identifier != null
                            && "etherology".equals(identifier.getNamespace()))
                    .map(Identifier::toString)
                    .sorted()
                    .toList();
            if (!etherologyEventIds.isEmpty()) {
                memberships.put(pair.getFirst().id().toString(), etherologyEventIds);
            }
        });
        return memberships;
    }

    private static List<String> collectRegistryEtherologyEventIds() {
        return Registries.GAME_EVENT.getIds().stream()
                .filter(identifier -> "etherology".equals(identifier.getNamespace()))
                .map(Identifier::toString)
                .sorted()
                .toList();
    }

    private static List<String> collectLoadedModIds() {
        List<String> observedModIds = ModList.get().getMods().stream()
                .map(modInfo -> modInfo.getModId())
                .toList();
        return ServerProbeModInventory.sortedUniqueIds(observedModIds);
    }

    private List<String> expectedEnabledDataPackNames() {
        List<String> expectedNames = new ArrayList<>();
        serverStartedLoadedModIds.forEach(modId -> expectedNames.add(
                "minecraft".equals(modId) ? "vanilla" : "mod:" + modId
        ));
        expectedNames.add(ReloadDataPackWriter.ENABLED_PACK_NAME);
        expectedNames.sort(String::compareTo);
        return List.copyOf(expectedNames);
    }

    private static boolean hasExactEtherologyTagMemberships(
            Map<String, List<String>> memberships
    ) {
        return List.copyOf(memberships.keySet()).equals(EXPECTED_ETHERLOGY_TAG_IDS)
                && memberships.get("minecraft:vibrations").equals(List.of(EVENT_ID.toString()))
                && memberships.get("minecraft:warden_can_listen")
                        .equals(List.of(EVENT_ID.toString()));
    }

    private boolean hasExactCapturedEtherologyTagMemberships() {
        return etherologyTagIds.equals(EXPECTED_ETHERLOGY_TAG_IDS)
                && vibrationsEtherologyEventIds.equals(List.of(EVENT_ID.toString()))
                && wardenCanListenEtherologyEventIds.equals(List.of(EVENT_ID.toString()));
    }

    private static boolean isInTag(
            GameEvent gameEvent,
            TagKey<GameEvent> tag
    ) {
        return gameEvent != null && gameEvent.isIn(tag);
    }

    private static JsonArray buildStringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonObject buildStringMap(Map<String, String> values) {
        JsonObject object = new JsonObject();
        values.forEach(object::addProperty);
        return object;
    }

    private static JsonArray buildIntegerArray(List<Integer> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static String loadedState(boolean loaded) {
        return loaded ? "loaded" : "missing";
    }

    private static String presentState(boolean present) {
        return present ? "present" : "missing";
    }

    private static String errorState(String error) {
        return error.isEmpty() ? "none" : error;
    }

    private static void addBooleanAssertion(
            JsonArray assertions,
            String name,
            boolean actual
    ) {
        addAssertion(assertions, name, "true", Boolean.toString(actual));
    }

    private static void addAssertion(
            JsonArray assertions,
            String name,
            String expected,
            String actual
    ) {
        JsonObject assertion = new JsonObject();
        assertion.addProperty("name", name);
        assertion.addProperty("passed", expected.equals(actual));
        assertion.addProperty("expected", expected);
        assertion.addProperty("actual", actual);
        assertions.add(assertion);
    }

    private static boolean assertionsPassed(JsonArray assertions) {
        for (int index = 0; index < assertions.size(); index++) {
            if (!assertions.get(index).getAsJsonObject().get("passed").getAsBoolean()) {
                return false;
            }
        }
        return true;
    }

    private static String requireSystemProperty(String propertyName) {
        String actual = System.getProperty(propertyName);
        if (actual == null || !actual.matches("[a-z0-9][a-z0-9.-]*")) {
            throw new IllegalStateException("System property " + propertyName + " is unsafe");
        }
        return actual;
    }
}
