package dev.theplumteam.etherology.e2e.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.SharedConstants;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.GameEventTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.event.GameEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
    private LootConditionProbeState lootConditionState = LootConditionProbeState.missing();
    private MinecraftServer startedServer;
    private GameEvent taggedEvent;
    private String reloadFailure = "not requested";
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
    private boolean serverStartedLootConditionRechecked;
    private boolean serverStartedEnchantmentsRechecked;
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
            LOGGER.info("[EtherologyServerProbe] tags_updated_initial");
            return;
        }

        lifecycle.add("tags_updated_reload");
        reloadUpdateCause = event.getUpdateCause().name();
        reloadShouldUpdateStaticData = event.shouldUpdateStaticData();
        reloadedEtherSourceState = EtherSourceProbeState.capture();
        reloadedEnchantmentState = EnchantmentProbeState.capture();
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

        try {
            ReloadDataPackWriter.WrittenPack writtenPack = ReloadDataPackWriter.write(
                    event.getServer()
            );
            reloadPackDirectory = writtenPack.directoryName();
            reloadPackResourcePaths = writtenPack.resourcePaths();
            reloadFailure = "";
            reloadRequested = true;
            lifecycle.add("reload_requested");
            LOGGER.info("[EtherologyServerProbe] reload_requested");
            reloadCommandResult = event.getServer().getCommandManager().executeWithPrefix(
                    event.getServer().getCommandSource(),
                    "reload"
            );
            enabledDataPackNamesAfterReload = event.getServer().getDataPackManager()
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
        event.getServer().stop(false);
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
        report.addProperty("schema", 5);
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
        report.add("loot_condition", buildLootCondition());
        report.add("ether_sources", buildEtherSources());
        report.add("reload", buildReload());
        report.add("tags", buildTags());
        report.add("lifecycle", buildLifecycle());
        report.add("assertions", assertions);
        return report;
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

    private static JsonObject buildEtherSourceCapture(EtherSourceProbeState state) {
        JsonObject capture = new JsonObject();
        capture.addProperty("capture_error", state.captureError());
        JsonObject entries = new JsonObject();
        state.entries().forEach(entries::addProperty);
        capture.add("entries", entries);
        return capture;
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
