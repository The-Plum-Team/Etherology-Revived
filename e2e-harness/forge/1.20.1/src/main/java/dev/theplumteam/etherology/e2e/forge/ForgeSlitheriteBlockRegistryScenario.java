package dev.theplumteam.etherology.e2e.forge;

import com.google.gson.JsonObject;
import dev.theplumteam.etherology.e2e.shared.SlitheriteArtifactDigest;
import dev.theplumteam.etherology.e2e.shared.SlitheriteBlockRegistryScenario;
import dev.theplumteam.etherology.e2e.shared.SlitheriteEvidenceTarget;
import dev.theplumteam.etherology.e2e.shared.SlitheriteHarnessIdentity;
import dev.theplumteam.etherology.e2e.shared.SlitheriteScenarioAdapter;
import dev.theplumteam.etherology.e2e.shared.SlitheriteScenarioDefinition;
import net.minecraft.client.MinecraftClient;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Connects the shared Slitherite scenario to Forge client lifecycle events.
 */
public final class ForgeSlitheriteBlockRegistryScenario
        implements SlitheriteScenarioAdapter {

    private static final String FORGE_MOD_FILE_ORIGIN_KIND = "MOD_FILE";
    private static final int FRAMEBUFFER_WIDTH = 1920;
    private static final int FRAMEBUFFER_HEIGHT = 1080;
    private static final SlitheriteHarnessIdentity IDENTITY = createIdentity();

    private final SlitheriteBlockRegistryScenario scenario =
            new SlitheriteBlockRegistryScenario(this);

    /**
     * Advances the shared client state machine at Forge's end-client-tick boundary.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            scenario.onEndClientTick(MinecraftClient.getInstance());
        }
    }

    /**
     * Advances native behavior at Forge's integrated-server end-tick boundary.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            scenario.onEndServerTick(event.getServer());
        }
    }

    /**
     * Counts a completed composed frame after Forge renders the GUI.
     */
    @SubscribeEvent
    public void onWorldRendered(RenderGuiEvent.Post event) {
        scenario.onGameRenderCompleted();
    }

    @Override
    public SlitheriteHarnessIdentity identity() {
        return IDENTITY;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public SlitheriteEvidenceTarget resolveEvidence(
            Path gameDirectory,
            SlitheriteScenarioDefinition definition
    ) throws IOException {
        ForgeEvidenceLayout layout = ForgeEvidenceLayout.resolve(
                gameDirectory,
                definition.id()
        );
        layout.requireFreshTargets(
                definition.initialScreenshotFileName(),
                definition.reopenedScreenshotFileName()
        );
        JsonObject provenance = new JsonObject();
        provenance.addProperty("profile_id", layout.profileId());
        provenance.addProperty(
                "profile_manifest_size",
                layout.profileManifestSize()
        );
        provenance.addProperty(
                "profile_manifest_sha256",
                layout.profileManifestSha256()
        );
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
                .map(ForgeArtifactDigest::capture)
                .map(ForgeSlitheriteBlockRegistryScenario::toSharedDigest)
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
        fields.addProperty("artifact_node", "forge-1.20.1");
        fields.addProperty("minecraft", "1.20.1");
        fields.addProperty("loader", "forge");
        fields.addProperty("loader_version", "47.4.9");
        fields.addProperty("java", 17);
        fields.addProperty("lane", "forge-1.20.1");
        fields.addProperty("role", "host");
        return new SlitheriteHarnessIdentity(
                "forge",
                ForgeE2eHarness.MOD_ID,
                fields
        );
    }

    private static SlitheriteArtifactDigest toSharedDigest(
            ForgeArtifactDigest digest
    ) {
        return new SlitheriteArtifactDigest(
                digest.modId(),
                digest.passed(),
                digest.passed() ? FORGE_MOD_FILE_ORIGIN_KIND : "missing",
                digest.fileName(),
                digest.size(),
                digest.sha256(),
                digest.failure()
        );
    }
}
