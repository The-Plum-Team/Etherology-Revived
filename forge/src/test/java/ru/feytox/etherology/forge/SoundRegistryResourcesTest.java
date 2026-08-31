package ru.feytox.etherology.forge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SoundRegistryResourcesTest {

    private static final String CANONICAL_ETHER_SOUNDS =
            "ru/feytox/etherology/registry/misc/EtherSounds.class";
    private static final String SHARED_SOUNDS =
            "ru/feytox/etherology/registry/misc/SharedSounds.class";
    private static final byte[] OGG_CAPTURE_PATTERN = {
            0x4F,
            0x67,
            0x67,
            0x53,
    };
    private static final Map<String, SoundDefinition> EXACT_SOUND_EVENTS = Map.ofEntries(
            Map.entry(
                    "electricity_sound",
                    definition(
                            "subtitles.etherology.small_lightning",
                            sound("etherology:electricity_1"),
                            sound("etherology:electricity_2"),
                            sound("etherology:electricity_3")
                    )
            ),
            Map.entry(
                    "matrix_idle_sound",
                    definition(
                            "subtitles.etherology.armillary_matrix_idle",
                            sound("etherology:armillary_matrix_idle_loop")
                    )
            ),
            Map.entry(
                    "deflect",
                    definition(
                            "subtitles.etherology.deflect",
                            sound("etherology:deflect")
                    )
            ),
            Map.entry(
                    "bubbles",
                    definition(
                            "subtitles.etherology.bubbles",
                            sound("etherology:bubbles_0"),
                            sound("etherology:bubbles_1"),
                            sound("etherology:bubbles_2")
                    )
            ),
            Map.entry(
                    "pouf",
                    definition(
                            "subtitles.etherology.pouf",
                            sound("etherology:pouf_0"),
                            sound("etherology:pouf_1")
                    )
            ),
            Map.entry(
                    "ratchet",
                    definition(
                            "subtitles.etherology.ratchet",
                            sound("etherology:ratchet_0")
                    )
            ),
            Map.entry(
                    "brewing_dissolution",
                    definition(
                            "subtitles.etherology.brewing_dissolution",
                            sound("etherology:brewing_dissolution_0")
                    )
            ),
            Map.entry(
                    "thunder_zap",
                    definition(
                            "subtitles.etherology.thunder_zap",
                            sound("etherology:thunder_zap_0"),
                            sound("etherology:thunder_zap_1"),
                            sound("etherology:thunder_zap_2")
                    )
            ),
            Map.entry(
                    "tuning_mace",
                    definition(
                            "subtitles.etherology.tuning_mace",
                            sound("etherology:tuning_mace")
                    )
            ),
            Map.entry(
                    "tuning_fork_activate",
                    definition(
                            "subtitles.etherology.tuning_fork_activate",
                            sound("etherology:tuning_fork_activate", 7)
                    )
            ),
            Map.entry(
                    "tuning_fork_tuning",
                    definition(
                            "subtitles.etherology.tuning_fork_tuning",
                            sound("etherology:tuning_fork_tuning")
                    )
            ),
            Map.entry(
                    "tuning_fork_resonance",
                    definition(
                            "subtitles.etherology.tuning_fork_resonance",
                            sound("etherology:tuning_fork_resonance", 7)
                    )
            ),
            Map.entry(
                    "broadsword",
                    definition(
                            "subtitles.etherology.broadsword",
                            sound("etherology:broadsword")
                    )
            ),
            Map.entry(
                    "warp_counter",
                    definition(
                            "subtitles.etherology.warp_counter",
                            sound("etherology:warp_counter", 4)
                    )
            )
    );
    private static final Set<String> EXACT_SOUND_FILES = Set.of(
            "etherology:armillary_matrix_idle_loop",
            "etherology:brewing_dissolution_0",
            "etherology:broadsword",
            "etherology:bubbles_0",
            "etherology:bubbles_1",
            "etherology:bubbles_2",
            "etherology:deflect",
            "etherology:electricity_1",
            "etherology:electricity_2",
            "etherology:electricity_3",
            "etherology:pouf_0",
            "etherology:pouf_1",
            "etherology:ratchet_0",
            "etherology:thunder_zap_0",
            "etherology:thunder_zap_1",
            "etherology:thunder_zap_2",
            "etherology:tuning_fork_activate",
            "etherology:tuning_fork_resonance",
            "etherology:tuning_fork_tuning",
            "etherology:tuning_mace",
            "etherology:warp_counter"
    );
    private static final Map<String, String> EXACT_SOUND_SHA256 = Map.ofEntries(
            Map.entry(
                    "etherology:armillary_matrix_idle_loop",
                    "51da10a80a1b6e74ac4f9e443be8b0a6c60714e3aef299b74b1196dc2e2b4a32"
            ),
            Map.entry(
                    "etherology:brewing_dissolution_0",
                    "e855ff5fd0c31d874007b69a0e5868d4714be634142296a390b1b917372303c5"
            ),
            Map.entry(
                    "etherology:broadsword",
                    "06641ff062cb4376325b1e9143d48af40f78feffd5d097f05d57e50832fa00d3"
            ),
            Map.entry(
                    "etherology:bubbles_0",
                    "34210d0dcc735f66eb7c346fb252c8122e90af740db8ebd857c7fdbcb164d382"
            ),
            Map.entry(
                    "etherology:bubbles_1",
                    "f41a10f2dc9e5edb9e3750542f73048962a19c4dfe10e0128122cc287be83bda"
            ),
            Map.entry(
                    "etherology:bubbles_2",
                    "2843233c0989169c8af6b09e06189a9181a35b7a8cdd3dd34e4037c205b5d68f"
            ),
            Map.entry(
                    "etherology:deflect",
                    "c3cdcdb46c066df04a0fddbfca15d9fea7b60f40d198c4b0f7bce38fbf0cff98"
            ),
            Map.entry(
                    "etherology:electricity_1",
                    "3ec362b520648e51b98b215d2200616eb9ca7741a7f8a503c71f6b7451997556"
            ),
            Map.entry(
                    "etherology:electricity_2",
                    "1bfd8825827be7142525937ca296e8fbdca65b99f79d963e5f131c3a58477204"
            ),
            Map.entry(
                    "etherology:electricity_3",
                    "593089f352a465a4617eae8cc0668d704de89e250df8ebb417d2becbe88af3a8"
            ),
            Map.entry(
                    "etherology:pouf_0",
                    "94f207c1081e65455d28f8216ead3db863238e9376ef76f1a9c78465646f8f77"
            ),
            Map.entry(
                    "etherology:pouf_1",
                    "913da08dab4df72ccffe340d39ba5091f2e98dc2b2c8dfccce0e951ca553bc9f"
            ),
            Map.entry(
                    "etherology:ratchet_0",
                    "3caa954c31038c583cfdf7de71d1986fcf34e83e3ee0e9219b4bd9af03eaef5a"
            ),
            Map.entry(
                    "etherology:thunder_zap_0",
                    "a56afeaa1f69819cb7c0f5832dbbf8296a1383f3d292200af5fa53fc8f170d6c"
            ),
            Map.entry(
                    "etherology:thunder_zap_1",
                    "ba533f107c570914c6bdeefffd7ce69dd8507a7c28228808efcd7c63f5c23c8f"
            ),
            Map.entry(
                    "etherology:thunder_zap_2",
                    "775329eb774994bfa3142d654abf3e795e578f77ed14bf6015d7b9945a8c5dd2"
            ),
            Map.entry(
                    "etherology:tuning_fork_activate",
                    "9cbc0dc246a30fb047ebac9bb46cbbd3736a30e0f0ab15f3b7ade58e34354512"
            ),
            Map.entry(
                    "etherology:tuning_fork_resonance",
                    "697a10985cbac6b7e4bebebd021cf3c4fe47e75898f63a5b0d1fb22e9d62d9b3"
            ),
            Map.entry(
                    "etherology:tuning_fork_tuning",
                    "fcb4c2f663a6155df1f9df9510478efb3e5919e4d9fed66c6bf2bd908d163116"
            ),
            Map.entry(
                    "etherology:tuning_mace",
                    "00729d6dfaf1adba3ce342bb07fa7828ff1beb6e0ebf8271d3d3cbf86051eff7"
            ),
            Map.entry(
                    "etherology:warp_counter",
                    "425f3de114cae49ddd6916a8f63623421688429b52fd9f80b4088871abac0f6d"
            )
    );

    @Test
    void packagesTheExactSoundCatalogWithClosedEnglishSubtitles() throws IOException {
        JsonObject soundCatalog = readJson("/assets/etherology/sounds.json");
        JsonObject englishTranslations = readJson("/assets/etherology/lang/en_us.json");

        assertEquals(EXACT_SOUND_EVENTS.keySet(), soundCatalog.keySet());
        for (Map.Entry<String, SoundDefinition> entry : EXACT_SOUND_EVENTS.entrySet()) {
            String eventId = entry.getKey();
            SoundDefinition expectedEvent = entry.getValue();
            JsonObject event = soundCatalog.getAsJsonObject(eventId);
            assertEquals(Set.of("sounds", "subtitle"), event.keySet());
            String subtitle = event.get("subtitle").getAsString();
            assertEquals(expectedEvent.subtitle(), subtitle);
            assertTrue(
                    englishTranslations.has(subtitle),
                    "Missing English translation for " + subtitle
            );
            assertFalse(
                    englishTranslations.get(subtitle).getAsString().isBlank(),
                    "Blank English translation for " + subtitle
            );
            assertEquals(expectedEvent.sounds().size(), event.getAsJsonArray("sounds").size());
            for (int index = 0; index < expectedEvent.sounds().size(); index++) {
                SoundReference expectedSound = expectedEvent.sounds().get(index);
                JsonElement actualSound = event.getAsJsonArray("sounds").get(index);
                if (expectedSound.attenuationDistance() == null) {
                    assertTrue(actualSound.isJsonPrimitive());
                    assertTrue(actualSound.getAsJsonPrimitive().isString());
                    assertEquals(expectedSound.name(), actualSound.getAsString());
                } else {
                    assertTrue(actualSound.isJsonObject());
                    JsonObject soundObject = actualSound.getAsJsonObject();
                    assertEquals(
                            Set.of("attenuation_distance", "name"),
                            soundObject.keySet()
                    );
                    assertEquals(expectedSound.name(), soundObject.get("name").getAsString());
                    assertEquals(
                            expectedSound.attenuationDistance(),
                            soundObject.get("attenuation_distance").getAsInt()
                    );
                }
            }
        }

        assertFalse(englishTranslations.has("subtitles.etherology.armillary_matrix_work"));
        assertTrue(englishTranslations.has("subtitles.etherology.armillary_matrix_idle"));
    }

    @Test
    void packagesExactlyTheReferencedMonoFortyFourKilohertzVorbisFiles()
            throws IOException {
        assertEquals(EXACT_SOUND_FILES, packagedSoundFiles());
        assertEquals(EXACT_SOUND_FILES, EXACT_SOUND_SHA256.keySet());

        for (String soundId : EXACT_SOUND_FILES) {
            String soundPath = soundId.substring("etherology:".length());
            byte[] oggBytes = readResource(
                    "/assets/etherology/sounds/" + soundPath + ".ogg"
            );
            assertEquals(EXACT_SOUND_SHA256.get(soundId), sha256(oggBytes));
            assertVorbisIdentificationHeader(soundId, oggBytes);
        }
    }

    @Test
    void forgeClasspathPackagesTheSharedOwnerWithoutTheCanonicalFabricOwner()
            throws IOException {
        URL sharedSoundsResource = requiredResourceUrl("/" + SHARED_SOUNDS);
        assertEquals("jar", sharedSoundsResource.getProtocol());

        JarURLConnection connection = (JarURLConnection) sharedSoundsResource.openConnection();
        connection.setUseCaches(false);
        try (JarFile commonArtifact = connection.getJarFile()) {
            assertNotNull(commonArtifact.getJarEntry(SHARED_SOUNDS));
            assertNull(commonArtifact.getJarEntry(CANONICAL_ETHER_SOUNDS));
        }
        assertNull(
                SoundRegistryResourcesTest.class
                        .getClassLoader()
                        .getResource(CANONICAL_ETHER_SOUNDS),
                "Forge test inputs expose the canonical Fabric sound owner"
        );
    }

    private static SoundDefinition definition(
            String subtitle,
            SoundReference... sounds
    ) {
        return new SoundDefinition(subtitle, List.of(sounds));
    }

    private static SoundReference sound(String name) {
        return new SoundReference(name, null);
    }

    private static SoundReference sound(String name, int attenuationDistance) {
        return new SoundReference(name, attenuationDistance);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("Java runtime has no SHA-256 digest", exception);
        }
    }

    private static JsonObject readJson(String resourcePath) throws IOException {
        try (InputStream resource = requiredResource(resourcePath);
             InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static byte[] readResource(String resourcePath) throws IOException {
        try (InputStream resource = requiredResource(resourcePath)) {
            return resource.readAllBytes();
        }
    }

    private static InputStream requiredResource(String resourcePath) {
        InputStream resource = SoundRegistryResourcesTest.class.getResourceAsStream(resourcePath);
        assertNotNull(resource, "Missing Forge runtime resource " + resourcePath);
        return resource;
    }

    private static URL requiredResourceUrl(String resourcePath) {
        URL resource = SoundRegistryResourcesTest.class.getResource(resourcePath);
        assertNotNull(resource, "Missing Forge runtime resource " + resourcePath);
        return resource;
    }

    private static Set<String> packagedSoundFiles() throws IOException {
        URL soundCatalog = requiredResourceUrl("/assets/etherology/sounds.json");
        if (soundCatalog.getProtocol().equals("file")) {
            return packagedSoundFilesFromDirectory(soundCatalog);
        }
        if (soundCatalog.getProtocol().equals("jar")) {
            return packagedSoundFilesFromJar(soundCatalog);
        }
        throw new IOException("Unsupported sound resource protocol " + soundCatalog);
    }

    private static Set<String> packagedSoundFilesFromDirectory(URL soundCatalog)
            throws IOException {
        Path catalogPath;
        try {
            catalogPath = Path.of(soundCatalog.toURI());
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid sound catalog path " + soundCatalog, exception);
        }
        Path soundsDirectory = catalogPath.resolveSibling("sounds");
        Set<String> soundFiles = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(soundsDirectory)) {
            paths.filter(Files::isRegularFile)
                    .map(soundsDirectory::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(path -> path.endsWith(".ogg"))
                    .map(path -> "etherology:" + path.substring(0, path.length() - 4))
                    .forEach(soundFiles::add);
        }
        return soundFiles;
    }

    private static Set<String> packagedSoundFilesFromJar(URL soundCatalog)
            throws IOException {
        JarURLConnection connection = (JarURLConnection) soundCatalog.openConnection();
        connection.setUseCaches(false);
        Set<String> soundFiles = new TreeSet<>();
        try (JarFile artifact = connection.getJarFile()) {
            artifact.stream()
                    .map(entry -> entry.getName())
                    .filter(path -> path.startsWith("assets/etherology/sounds/"))
                    .filter(path -> path.endsWith(".ogg"))
                    .map(path -> path.substring("assets/etherology/sounds/".length()))
                    .map(path -> "etherology:" + path.substring(0, path.length() - 4))
                    .forEach(soundFiles::add);
        }
        return soundFiles;
    }

    private static void assertVorbisIdentificationHeader(String soundId, byte[] oggBytes) {
        byte[] firstPacket = firstOggPacket(soundId, oggBytes);
        assertTrue(firstPacket.length >= 30, soundId + " has a truncated Vorbis header");
        assertEquals(1, Byte.toUnsignedInt(firstPacket[0]), soundId + " is not Vorbis audio");
        assertEquals(
                "vorbis",
                new String(firstPacket, 1, 6, StandardCharsets.US_ASCII),
                soundId + " is missing the Vorbis identification signature"
        );
        assertEquals(0, littleEndianInt(firstPacket, 7), soundId + " uses an unknown version");
        assertEquals(1, Byte.toUnsignedInt(firstPacket[11]), soundId + " is not mono");
        assertEquals(44_100, littleEndianInt(firstPacket, 12), soundId + " is not 44.1 kHz");
        assertEquals(1, Byte.toUnsignedInt(firstPacket[29]) & 1, soundId + " lacks framing");
    }

    private static byte[] firstOggPacket(String soundId, byte[] oggBytes) {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        int pageOffset = 0;
        while (pageOffset < oggBytes.length) {
            assertTrue(
                    pageOffset + 27 <= oggBytes.length,
                    soundId + " has a truncated Ogg page header"
            );
            assertArrayEquals(
                    OGG_CAPTURE_PATTERN,
                    Arrays.copyOfRange(oggBytes, pageOffset, pageOffset + 4),
                    soundId + " is missing the Ogg capture pattern"
            );
            int segmentCount = Byte.toUnsignedInt(oggBytes[pageOffset + 26]);
            int segmentTableOffset = pageOffset + 27;
            int payloadOffset = segmentTableOffset + segmentCount;
            assertTrue(
                    payloadOffset <= oggBytes.length,
                    soundId + " has a truncated Ogg segment table"
            );

            int payloadLength = 0;
            for (int segment = 0; segment < segmentCount; segment++) {
                payloadLength += Byte.toUnsignedInt(oggBytes[segmentTableOffset + segment]);
            }
            assertTrue(
                    payloadOffset + payloadLength <= oggBytes.length,
                    soundId + " has a truncated Ogg page payload"
            );

            int payloadCursor = payloadOffset;
            for (int segment = 0; segment < segmentCount; segment++) {
                int segmentLength = Byte.toUnsignedInt(
                        oggBytes[segmentTableOffset + segment]
                );
                packet.write(oggBytes, payloadCursor, segmentLength);
                payloadCursor += segmentLength;
                if (segmentLength < 255) {
                    return packet.toByteArray();
                }
            }
            pageOffset = payloadOffset + payloadLength;
        }
        throw new AssertionError(soundId + " has no complete Ogg packet");
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset])
                | (Byte.toUnsignedInt(bytes[offset + 1]) << 8)
                | (Byte.toUnsignedInt(bytes[offset + 2]) << 16)
                | (Byte.toUnsignedInt(bytes[offset + 3]) << 24);
    }

    private record SoundDefinition(String subtitle, List<SoundReference> sounds) {
    }

    private record SoundReference(String name, Integer attenuationDistance) {
    }
}
