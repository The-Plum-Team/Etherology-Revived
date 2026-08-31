package dev.theplumteam.etherology.baseline.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ArtifactDigestTest {

    @Test
    void hashesTheExactFileBytes(@TempDir Path temporaryDirectory) throws Exception {
        Path artifact = temporaryDirectory.resolve("artifact.jar");
        Files.write(artifact, new byte[]{0, 1, 2, 3, 4});

        assertEquals(
                "08bb5e5d6eaac1049ede0893d30ed022b1a4d9b5b48db414871f51c9cb35283d",
                ArtifactDigest.sha256(artifact)
        );
    }
}
