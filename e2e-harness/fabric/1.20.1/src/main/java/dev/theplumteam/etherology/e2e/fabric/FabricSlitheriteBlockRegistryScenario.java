package dev.theplumteam.etherology.e2e.fabric;

import com.google.gson.JsonObject;
import dev.theplumteam.etherology.e2e.shared.SlitheriteArtifactDigest;
import dev.theplumteam.etherology.e2e.shared.SlitheriteBlockRegistryScenario;
import dev.theplumteam.etherology.e2e.shared.SlitheriteEvidenceTarget;
import dev.theplumteam.etherology.e2e.shared.SlitheriteHarnessIdentity;
import dev.theplumteam.etherology.e2e.shared.SlitheriteScenarioAdapter;
import dev.theplumteam.etherology.e2e.shared.SlitheriteScenarioDefinition;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Connects the shared Slitherite scenario to the existing Fabric client callbacks.
 */
public final class FabricSlitheriteBlockRegistryScenario
        implements ClientScenario, SlitheriteScenarioAdapter {

    static final String SCENARIO_ID =
            SlitheriteBlockRegistryScenario.definition().id();

    private static final String HARNESS_MOD_ID = "etherology_e2e_harness";
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final SlitheriteHarnessIdentity IDENTITY = createIdentity();

    private final SlitheriteBlockRegistryScenario scenario =
            new SlitheriteBlockRegistryScenario(this);

    @Override
    public void onEndClientTick(MinecraftClient client) {
        scenario.onEndClientTick(client);
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
        scenario.onGameRenderCompleted();
    }

    @Override
    public void onEndServerTick(MinecraftServer server) {
        scenario.onEndServerTick(server);
    }

    @Override
    public SlitheriteHarnessIdentity identity() {
        return IDENTITY;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public SlitheriteEvidenceTarget resolveEvidence(
            Path gameDirectory,
            SlitheriteScenarioDefinition definition
    ) throws IOException {
        EvidenceLayout layout = EvidenceLayout.resolve(gameDirectory, definition.id());
        layout.requireFreshTargets(
                definition.initialScreenshotFileName(),
                definition.reopenedScreenshotFileName()
        );
        JsonObject provenance = new JsonObject();
        provenance.addProperty("profile_id", layout.profileId());
        return new SlitheriteEvidenceTarget(
                layout.scenarioRoot(),
                layout.reportsDirectory(),
                layout.screenshotsDirectory(),
                FRAMEBUFFER_WIDTH,
                FRAMEBUFFER_HEIGHT,
                provenance
        );
    }

    @Override
    public List<SlitheriteArtifactDigest> captureArtifactDigests(
            List<String> modIds
    ) {
        return modIds.stream()
                .map(ArtifactDigest::capture)
                .map(FabricSlitheriteBlockRegistryScenario::toSharedDigest)
                .toList();
    }

    @Override
    public void publishAtomically(
            SlitheriteEvidenceTarget target,
            JsonObject report
    ) throws IOException {
        AtomicEvidenceWriter.writeReportThenMarker(target.reportsDirectory(), report);
    }

    @Override
    public void shutdown(MinecraftClient client) {
        client.scheduleStop();
    }

    private static SlitheriteHarnessIdentity createIdentity() {
        JsonObject fields = new JsonObject();
        fields.addProperty("artifact_node", "fabric-1.20.1");
        fields.addProperty("minecraft", "1.20.1");
        fields.addProperty("loader", "fabric");
        fields.addProperty("loader_version", "0.17.3");
        fields.addProperty("java", 17);
        fields.addProperty("lane", "fabric-1.20.1");
        fields.addProperty("role", "host");
        return new SlitheriteHarnessIdentity("fabric", HARNESS_MOD_ID, fields);
    }

    private static SlitheriteArtifactDigest toSharedDigest(ArtifactDigest digest) {
        return new SlitheriteArtifactDigest(
                digest.modId(),
                digest.passed(),
                digest.originKind(),
                digest.fileName(),
                digest.size(),
                digest.sha256(),
                digest.failure()
        );
    }
}
