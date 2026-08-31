package dev.theplumteam.etherology.e2e.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReloadDataPackWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesTheExactBoundedReloadPackOnce() throws IOException {
        Path datapacks = Files.createDirectory(temporaryDirectory.resolve("datapacks"));

        ReloadDataPackWriter.WrittenPack writtenPack =
                ReloadDataPackWriter.writeToDatapacks(datapacks);

        assertEquals(ReloadDataPackWriter.PACK_DIRECTORY_NAME, writtenPack.directoryName());
        assertEquals(
                "file/etherology-e2e-ether-source-reload",
                ReloadDataPackWriter.ENABLED_PACK_NAME
        );
        assertEquals(ReloadDataPackWriter.RESOURCE_PATHS, writtenPack.resourcePaths());
        Path pack = datapacks.resolve(ReloadDataPackWriter.PACK_DIRECTORY_NAME);
        List<String> actualFiles;
        try (var paths = Files.walk(pack)) {
            actualFiles = paths.filter(Files::isRegularFile)
                    .map(pack::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
        assertEquals(ReloadDataPackWriter.RESOURCE_PATHS.stream().sorted().toList(), actualFiles);

        JsonObject metadata = parse(pack.resolve("pack.mcmeta"));
        assertEquals(15, metadata.getAsJsonObject("pack").get("pack_format").getAsInt());
        JsonObject override = parse(
                pack.resolve("data/etherology/ether_sources/default.json")
        );
        JsonObject addition = parse(
                pack.resolve("data/etherology/ether_sources/probe_addition.json")
        );
        assertEquals(23, override.size());
        assertEquals(9.5F, override.get("minecraft:redstone").getAsFloat());
        assertEquals(4.0F, override.get("etherology:primoshard_rella").getAsFloat());
        assertFalse(override.has("etherology:primoshard_rela"));
        assertEquals(1, addition.size());
        assertEquals(13.0F, addition.get("minecraft:diamond").getAsFloat());

        assertThrows(
                IOException.class,
                () -> ReloadDataPackWriter.writeToDatapacks(datapacks)
        );
    }

    @Test
    void rejectsAMissingOrLinkedDatapacksDirectory() throws IOException {
        Path missing = temporaryDirectory.resolve("missing");
        assertThrows(
                IOException.class,
                () -> ReloadDataPackWriter.writeToDatapacks(missing)
        );

        Path target = Files.createDirectory(temporaryDirectory.resolve("target"));
        Path linked = temporaryDirectory.resolve("linked");
        Files.createSymbolicLink(linked, target);
        assertThrows(
                IOException.class,
                () -> ReloadDataPackWriter.writeToDatapacks(linked)
        );
    }

    private static JsonObject parse(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
