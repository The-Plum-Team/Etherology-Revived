package dev.theplumteam.etherology.e2e.shared;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Records the loader-verified packaged origin of one loaded mod.
 *
 * @param modId loaded mod whose root origin was inspected
 * @param passed whether the origin was exactly one regular packaged JAR
 * @param originKind loader-specific origin classification
 * @param fileName root JAR file name when capture passed
 * @param size root JAR size in bytes when capture passed
 * @param sha256 lowercase root JAR SHA-256 when capture passed
 * @param failure diagnostic when capture did not pass
 */
public record SlitheriteArtifactDigest(
        String modId,
        boolean passed,
        String originKind,
        String fileName,
        long size,
        String sha256,
        String failure
) {

    private static final int BUFFER_SIZE = 16 * 1024;

    /**
     * Computes the digest used to bind screenshots and packaged artifacts.
     */
    public static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "This Java runtime has no SHA-256 implementation",
                    exception
            );
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(path)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
