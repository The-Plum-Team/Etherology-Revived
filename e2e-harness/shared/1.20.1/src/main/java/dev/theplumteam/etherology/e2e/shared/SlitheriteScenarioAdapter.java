package dev.theplumteam.etherology.e2e.shared;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Supplies the loader-owned boundaries used by the shared Slitherite scenario.
 */
public interface SlitheriteScenarioAdapter {

    /**
     * Returns stable assertion identity and typed top-level runtime report fields.
     */
    SlitheriteHarnessIdentity identity();

    /**
     * Reports whether the active loader has the requested mod in its loaded set.
     */
    boolean isModLoaded(String modId);

    /**
     * Resolves fresh repository-owned paths and typed profile-marker provenance.
     */
    SlitheriteEvidenceTarget resolveEvidence(
            Path gameDirectory,
            SlitheriteScenarioDefinition scenario
    ) throws IOException;

    /**
     * Captures one immutable digest per mod id, preserving the requested order.
     */
    List<SlitheriteArtifactDigest> captureArtifactDigests(List<String> modIds);

    /**
     * Publishes the report and its completion marker without replacing either target.
     */
    void publishAtomically(
            SlitheriteEvidenceTarget target,
            JsonObject report
    ) throws IOException;

    /**
     * Requests shutdown through the loader's established client lifecycle path.
     */
    void shutdown(MinecraftClient client);
}
