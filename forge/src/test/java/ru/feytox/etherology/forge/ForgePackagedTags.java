package ru.feytox.etherology.forge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mirrors the Forge {@code processResources} tag filter: canonical vanilla block and item tags
 * are packaged byte-for-byte, except that entries listed in {@code forge/unported-tag-entries.txt}
 * become optional so an unregistered Etherology block cannot void the whole vanilla tag.
 */
final class ForgePackagedTags {

    private static final Pattern FILTERED_TAG =
            Pattern.compile("data/minecraft/tags/(blocks|items)/.+\\.json");
    private static final Pattern ENTRY_LINE =
            Pattern.compile("^(\\s*)\"(etherology:[a-z0-9_./-]+)\"(,?)$");

    private ForgePackagedTags() {
    }

    static byte[] expectedForgeBytes(
            Path repositoryRoot,
            String jarEntry,
            byte[] canonical
    ) throws IOException {
        if (!FILTERED_TAG.matcher(jarEntry).matches()) {
            return canonical;
        }
        Set<String> unported = unportedEntries(repositoryRoot);
        String text = new String(canonical, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            Matcher match = ENTRY_LINE.matcher(line);
            if (match.matches() && unported.contains(match.group(2))) {
                line = match.group(1) + "{ \"id\": \"" + match.group(2)
                        + "\", \"required\": false }" + match.group(3);
            }
            out.append(line).append('\n');
        }
        out.setLength(out.length() - 1);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Set<String> unportedEntries(Path repositoryRoot) throws IOException {
        return Files.readAllLines(repositoryRoot.resolve("forge/unported-tag-entries.txt"))
                .stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toUnmodifiableSet());
    }
}
