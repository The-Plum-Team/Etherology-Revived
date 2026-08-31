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
 * Exercises the shared game-event registry and tags in a real dedicated Forge server.
 */
@Mod(GameEventServerProbe.MOD_ID)
public final class GameEventServerProbe {

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
            "tags_updated",
            "server_started",
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

    private MinecraftServer startedServer;
    private GameEvent taggedEvent;
    private String registryEventId = "";
    private String internalEventId = "";
    private String updateCause = "";
    private int eventRange = -1;
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
    private boolean serverStartedModsRechecked;
    private boolean serverStartedRegistryRechecked;
    private boolean serverStartedTagsRechecked;
    private boolean stopRequestedWithoutRestart;
    private boolean stoppingServerMatched;
    private boolean stoppedServerMatched;

    /**
     * Rejects every non-dedicated distribution before registering lifecycle callbacks.
     */
    public GameEventServerProbe() {
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
        lifecycle.add("tags_updated");
        updateCause = event.getUpdateCause().name();
        shouldUpdateStaticData = event.shouldUpdateStaticData();
        taggedEvent = Registries.GAME_EVENT.getOrEmpty(EVENT_ID).orElse(null);
        captureRegistryState(taggedEvent);
        registryEtherologyEventIds = collectRegistryEtherologyEventIds();
        vibrationsContainsEvent = isInTag(taggedEvent, GameEventTags.VIBRATIONS);
        wardenCanListenContainsEvent = isInTag(taggedEvent, GameEventTags.WARDEN_CAN_LISTEN);
        captureEtherologyTagMemberships();
        LOGGER.info("[EtherologyServerProbe] tags_updated");
    }

    /**
     * Rechecks the loaded mods and accepted state before requesting a normal server stop.
     *
     * @param event the ready dedicated server
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        lifecycle.add("server_started");
        LOGGER.info("[EtherologyServerProbe] server_started");
        startedServer = event.getServer();
        tagsBeforeServerStarted = lifecycle.equals(List.of("tags_updated", "server_started"));
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
        stopRequestedWithoutRestart = true;
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
                "1",
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
        report.addProperty("schema", 1);
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
        return registry;
    }

    private JsonObject buildTags() {
        JsonObject tags = new JsonObject();
        tags.addProperty("update_cause", updateCause);
        tags.addProperty("should_update_static_data", shouldUpdateStaticData);
        tags.addProperty("update_count", tagUpdateCount);
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

    private static String loadedState(boolean loaded) {
        return loaded ? "loaded" : "missing";
    }

    private static String presentState(boolean present) {
        return present ? "present" : "missing";
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
