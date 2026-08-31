package dev.theplumteam.etherology.e2e.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

record ArtifactDigest(
        String modId,
        boolean passed,
        String originKind,
        String fileName,
        long size,
        String sha256,
        String failure
) {

    private static final int BUFFER_SIZE = 16 * 1024;

    static ArtifactDigest capture(String modId) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty()) {
            return failed(modId, "missing", "The mod is not loaded");
        }

        ModOrigin origin = container.get().getOrigin();
        if (origin.getKind() != ModOrigin.Kind.PATH) {
            return failed(modId, origin.getKind().name(), "The mod is not a root path artifact");
        }

        List<Path> paths = origin.getPaths();
        if (paths.size() != 1) {
            return failed(modId, origin.getKind().name(), "The mod has more than one origin path");
        }

        Path path = paths.get(0).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !path.getFileName().toString().endsWith(".jar")) {
            return failed(modId, origin.getKind().name(), "The mod origin is not one regular JAR");
        }

        try {
            return new ArtifactDigest(
                    modId,
                    true,
                    origin.getKind().name(),
                    path.getFileName().toString(),
                    Files.size(path),
                    sha256(path),
                    ""
            );
        } catch (IOException exception) {
            return failed(modId, origin.getKind().name(), exception.getMessage());
        }
    }

    private static ArtifactDigest failed(String modId, String originKind, String failure) {
        return new ArtifactDigest(modId, false, originKind, "", 0L, "", failure);
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("This Java runtime has no SHA-256 implementation", exception);
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
