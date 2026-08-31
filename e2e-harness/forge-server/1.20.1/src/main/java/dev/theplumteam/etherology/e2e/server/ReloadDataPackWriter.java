package dev.theplumteam.etherology.e2e.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Installs the bounded probe-only datapack after the server's initial data load.
 */
final class ReloadDataPackWriter {

    static final String PACK_DIRECTORY_NAME = "etherology-e2e-ether-source-reload";
    static final String ENABLED_PACK_NAME = "file/" + PACK_DIRECTORY_NAME;
    static final String RESOURCE_ROOT = "probe-inputs/ether-source-reload-pack/";
    static final List<String> RESOURCE_PATHS = List.of(
            "pack.mcmeta",
            "data/etherology/ether_sources/default.json",
            "data/etherology/ether_sources/probe_addition.json"
    );
    private static final OpenOption[] EXCLUSIVE_WRITE_OPTIONS = {
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
    };
    private static final int MAXIMUM_RESOURCE_SIZE = 64 * 1024;

    private ReloadDataPackWriter() {
    }

    static WrittenPack write(MinecraftServer server) throws IOException {
        return writeToDatapacks(server.getSavePath(WorldSavePath.DATAPACKS));
    }

    static WrittenPack writeToDatapacks(Path datapacksDirectory) throws IOException {
        Path normalizedDatapacksDirectory = datapacksDirectory.toAbsolutePath().normalize();
        requireUnlinkedDirectory(normalizedDatapacksDirectory, "world datapacks directory");

        Path packDirectory = normalizedDatapacksDirectory.resolve(PACK_DIRECTORY_NAME);
        if (Files.exists(packDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(packDirectory)) {
            throw new IOException("The reload datapack already exists: " + packDirectory);
        }
        Files.createDirectory(packDirectory);

        for (String relativeName : RESOURCE_PATHS) {
            Path relativePath = Path.of(relativeName);
            if (relativePath.isAbsolute() || relativePath.startsWith("..")) {
                throw new IOException("Unsafe reload datapack resource: " + relativeName);
            }
            Path target = packDirectory.resolve(relativePath).normalize();
            if (!target.startsWith(packDirectory)) {
                throw new IOException("Reload datapack resource escaped its pack: " + relativeName);
            }
            Path parent = target.getParent();
            if (parent == null) {
                throw new IOException("Reload datapack resource has no parent: " + relativeName);
            }
            Files.createDirectories(parent);
            Files.write(target, readResource(relativeName), EXCLUSIVE_WRITE_OPTIONS);
        }
        return new WrittenPack(PACK_DIRECTORY_NAME, List.copyOf(RESOURCE_PATHS));
    }

    private static byte[] readResource(String relativeName) throws IOException {
        String resourceName = "/" + RESOURCE_ROOT + relativeName;
        try (InputStream input = ReloadDataPackWriter.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Missing reload datapack resource: " + resourceName);
            }
            byte[] content = input.readNBytes(MAXIMUM_RESOURCE_SIZE + 1);
            if (content.length == 0 || content.length > MAXIMUM_RESOURCE_SIZE) {
                throw new IOException("Invalid reload datapack resource size: " + resourceName);
            }
            return content;
        }
    }

    private static void requireUnlinkedDirectory(Path path, String label) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("The " + label + " is missing or linked: " + path);
        }
    }

    record WrittenPack(String directoryName, List<String> resourcePaths) {
    }
}
