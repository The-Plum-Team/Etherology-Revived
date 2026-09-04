package dev.theplumteam.etherology.e2e.shared;

import com.google.gson.JsonObject;

/**
 * Names one loader harness lane and the runtime fields it contributes to evidence.
 *
 * @param loaderId stable lowercase loader id used in assertion names
 * @param harnessModId packaged harness mod whose artifact must be digested
 * @param reportFields loader-owned typed artifact, Minecraft, loader, Java, lane,
 *                     and role fields copied into the top-level report
 */
public record SlitheriteHarnessIdentity(
        String loaderId,
        String harnessModId,
        JsonObject reportFields
) {

    /**
     * Freezes report identity so callers cannot change evidence during a run.
     */
    public SlitheriteHarnessIdentity {
        reportFields = reportFields.deepCopy();
    }

    /**
     * Returns an independent copy so report construction cannot mutate the identity.
     */
    public JsonObject reportFields() {
        return reportFields.deepCopy();
    }

    /**
     * Produces the loader-specific mod-presence assertion expected by verification.
     */
    public String modLoadedAssertionName(String modId) {
        return loaderId + "_mod_loaded:" + modId;
    }
}
