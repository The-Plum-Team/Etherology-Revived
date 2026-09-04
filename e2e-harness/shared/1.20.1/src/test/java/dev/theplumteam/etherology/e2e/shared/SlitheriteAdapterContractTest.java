package dev.theplumteam.etherology.e2e.shared;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SlitheriteAdapterContractTest {

    @TempDir
    private Path temporaryDirectory;

    @ParameterizedTest
    @CsvSource({
            "fabric, fabric_mod_loaded:etherology",
            "forge, forge_mod_loaded:etherology"
    })
    void derivesLoaderSpecificPresenceAssertion(
            String loaderId,
            String expectedAssertionName
    ) {
        SlitheriteHarnessIdentity identity = new SlitheriteHarnessIdentity(
                loaderId,
                "etherology_e2e_harness",
                reportFields("loader", loaderId)
        );

        assertEquals(
                expectedAssertionName,
                identity.modLoadedAssertionName("etherology")
        );
    }

    @Test
    void freezesLoaderIdentityAndEvidenceProvenance() {
        JsonObject identityFields = reportFields("loader", "fabric");
        identityFields.addProperty("java", 17);
        SlitheriteHarnessIdentity identity = new SlitheriteHarnessIdentity(
                "fabric",
                "etherology_e2e_harness",
                identityFields
        );
        identityFields.addProperty("loader", "changed");

        JsonObject provenance = reportFields("profile_id", "fresh-profile");
        provenance.addProperty("profile_manifest_size", 3702L);
        SlitheriteEvidenceTarget target = evidenceTarget(provenance);
        provenance.addProperty("profile_id", "changed");

        assertEquals("fabric", identity.reportFields().get("loader").getAsString());
        assertEquals(17, identity.reportFields().get("java").getAsInt());
        assertEquals(
                "fresh-profile",
                target.provenanceFields().get("profile_id").getAsString()
        );
        assertEquals(
                3702L,
                target.provenanceFields().get("profile_manifest_size").getAsLong()
        );
        JsonObject returnedIdentity = identity.reportFields();
        returnedIdentity.addProperty("loader", "returned-change");
        JsonObject returnedProvenance = target.provenanceFields();
        returnedProvenance.addProperty("profile_id", "returned-change");
        assertEquals("fabric", identity.reportFields().get("loader").getAsString());
        assertEquals(
                "fresh-profile",
                target.provenanceFields().get("profile_id").getAsString()
        );
    }

    @Test
    void resolvesOnlyFlatPngNamesInsideScreenshotDirectory() {
        SlitheriteEvidenceTarget target = evidenceTarget(new JsonObject());

        assertEquals(
                temporaryDirectory.resolve("screenshots/capture.png"),
                target.screenshotPath("capture.png")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> target.screenshotPath("../capture.png")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> target.screenshotPath("nested/capture.png")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> target.screenshotPath("nested\\capture.png")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> target.screenshotPath("capture.jpg")
        );
    }

    @Test
    void computesStableSha256ForCapturedFiles() throws IOException {
        Path capturedFile = temporaryDirectory.resolve("capture.bin");
        Files.writeString(capturedFile, "etherology\n");

        assertEquals(
                "6bfb62d334bc5c5e223f3bbc778f6f4ec0b6077323c9c04f16dc84d508b9e947",
                SlitheriteArtifactDigest.sha256(capturedFile)
        );
    }

    @Test
    void copiesTypedAdapterFieldsIntoTheTopLevelReport() {
        JsonObject fields = reportFields("loader", "forge");
        fields.addProperty("java", 17);
        fields.addProperty("profile_manifest_size", 3702L);
        JsonObject report = new JsonObject();

        SlitheriteBlockRegistryScenario.addReportFields(report, fields);
        fields.addProperty("loader", "changed");

        assertEquals("forge", report.get("loader").getAsString());
        assertEquals(17, report.get("java").getAsInt());
        assertEquals(3702L, report.get("profile_manifest_size").getAsLong());
    }

    @Test
    void rejectsAdapterFieldsThatCollideWithTheReport() {
        JsonObject report = new JsonObject();
        report.addProperty("schema", 3);
        JsonObject fields = new JsonObject();
        fields.addProperty("schema", 4);

        assertThrows(
                IllegalArgumentException.class,
                () -> SlitheriteBlockRegistryScenario.addReportFields(
                        report,
                        fields
                )
        );
        assertEquals(3, report.get("schema").getAsInt());
    }

    private SlitheriteEvidenceTarget evidenceTarget(
            JsonObject provenance
    ) {
        return new SlitheriteEvidenceTarget(
                temporaryDirectory,
                temporaryDirectory.resolve("reports"),
                temporaryDirectory.resolve("screenshots"),
                1920,
                1080,
                provenance
        );
    }

    private static JsonObject reportFields(String name, String value) {
        JsonObject fields = new JsonObject();
        fields.addProperty(name, value);
        return fields;
    }
}
