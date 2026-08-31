package dev.theplumteam.etherology.e2e.forge;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

record ForgeArtifactDigest(
        String modId,
        boolean passed,
        String fileName,
        long size,
        String sha256,
        String failure
) {

    private static final int BUFFER_SIZE = 16 * 1024;

    static ForgeArtifactDigest capture(String modId) {
        IModInfo modInfo = ModList.get().getMods().stream()
                .filter(candidate -> modId.equals(candidate.getModId()))
                .findFirst()
                .orElse(null);
        if (modInfo == null) {
            return failed(modId, "The mod is not loaded");
        }

        Path path = modInfo.getOwningFile().getFile().getFilePath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !path.getFileName().toString().endsWith(".jar")) {
            return failed(modId, "The mod origin is not one regular JAR");
        }

        try {
            return new ForgeArtifactDigest(
                    modId,
                    true,
                    path.getFileName().toString(),
                    Files.size(path),
                    sha256(path),
                    ""
            );
        } catch (IOException exception) {
            return failed(modId, exception.getMessage());
        }
    }

    private static ForgeArtifactDigest failed(String modId, String failure) {
        return new ForgeArtifactDigest(modId, false, "", 0L, "", failure);
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
