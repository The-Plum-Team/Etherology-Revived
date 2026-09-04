package dev.theplumteam.etherology.e2e.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Authenticates the actual Loom JavaExec JVM to its external memory guard.
 */
final class ServerProbeMemoryHandoff {

    static final String ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE =
            "ETHERLOGY_E2E_FORGE_SERVER_MEMORY_ACKNOWLEDGEMENT";
    static final String HANDOFF_ENVIRONMENT_VARIABLE =
            "ETHERLOGY_E2E_FORGE_SERVER_MEMORY_HANDOFF";
    static final String RUN_TOKEN_ENVIRONMENT_VARIABLE =
            "ETHERLOGY_E2E_FORGE_SERVER_RUN_TOKEN";
    static final String EXACT_MAXIMUM_HEAP_ARGUMENT = "-Xmx2048m";
    static final long EXACT_MAXIMUM_HEAP_BYTES = 2L * 1024L * 1024L * 1024L;

    private static final String ACKNOWLEDGEMENT_FILE_NAME =
            ".forge-server-java-memory-ready";
    private static final String HANDOFF_FILE_NAME =
            ".forge-server-java-memory-handoff.json";
    private static final long ACKNOWLEDGEMENT_TIMEOUT_NANOSECONDS =
            TimeUnit.SECONDS.toNanos(15L);
    private static final int MAXIMUM_HANDOFF_SIZE_BYTES = 16 * 1024;
    private static final Pattern RUN_TOKEN_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final List<String> JAVA_OPTION_ENVIRONMENT_VARIABLES = List.of(
            "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "_JAVA_OPTIONS"
    );
    private static final Gson GSON = new Gson();

    private ServerProbeMemoryHandoff() {
    }

    static void publishAndAwaitAcknowledgement() {
        String runToken = requireRunToken();
        Path handoffPath = requireOwnedArtifactPath(
                HANDOFF_ENVIRONMENT_VARIABLE,
                HANDOFF_FILE_NAME
        );
        Path acknowledgementPath = requireOwnedArtifactPath(
                ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE,
                ACKNOWLEDGEMENT_FILE_NAME
        );
        if (!handoffPath.getParent().equals(acknowledgementPath.getParent())) {
            throw new IllegalStateException(
                    "The server memory handoff artifacts do not share one runtime"
            );
        }
        List<String> inheritedJavaOptionVariables = JAVA_OPTION_ENVIRONMENT_VARIABLES
                .stream()
                .filter(name -> System.getenv(name) != null)
                .toList();
        if (!inheritedJavaOptionVariables.isEmpty()) {
            throw new IllegalStateException(
                    "The dedicated server inherited Java option injection through "
                            + inheritedJavaOptionVariables
            );
        }

        List<String> maximumHeapArguments = ManagementFactory
                .getRuntimeMXBean()
                .getInputArguments()
                .stream()
                .filter(argument -> argument.startsWith("-Xmx"))
                .toList();
        if (!maximumHeapArguments.equals(List.of(EXACT_MAXIMUM_HEAP_ARGUMENT))) {
            throw new IllegalStateException(
                    "The dedicated server requires exactly one "
                            + EXACT_MAXIMUM_HEAP_ARGUMENT
            );
        }
        if (Runtime.version().feature() != 17) {
            throw new IllegalStateException(
                    "The dedicated server memory handoff requires Java 17"
            );
        }
        long maximumHeapBytes = Runtime.getRuntime().maxMemory();
        if (maximumHeapBytes != EXACT_MAXIMUM_HEAP_BYTES) {
            throw new IllegalStateException(
                    "The dedicated server maximum heap is not exactly 2048 MiB"
            );
        }

        ProcessHandle currentProcess = ProcessHandle.current();
        String executable = currentProcess.info().command().orElseThrow(
                () -> new IllegalStateException(
                        "The dedicated server Java executable is unavailable"
                )
        );
        Path executablePath = Path.of(executable).normalize();
        if (!executablePath.isAbsolute()) {
            throw new IllegalStateException(
                    "The dedicated server Java executable is not absolute"
            );
        }

        JsonObject handoff = new JsonObject();
        handoff.addProperty("schema", 1);
        handoff.addProperty("run_token", runToken);
        handoff.addProperty("pid", currentProcess.pid());
        handoff.addProperty("executable", executablePath.toString());
        handoff.addProperty("java_feature", Runtime.version().feature());
        handoff.addProperty("maximum_heap_bytes", maximumHeapBytes);
        JsonArray heapArguments = new JsonArray();
        maximumHeapArguments.forEach(heapArguments::add);
        handoff.add("maximum_heap_arguments", heapArguments);

        try {
            publishExclusive(handoffPath, GSON.toJson(handoff) + "\n");
            awaitAcknowledgement(acknowledgementPath, runToken);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "The dedicated server memory-guard handoff failed",
                    exception
            );
        }
    }

    private static String requireRunToken() {
        String runToken = System.getenv(RUN_TOKEN_ENVIRONMENT_VARIABLE);
        if (runToken == null || !RUN_TOKEN_PATTERN.matcher(runToken).matches()) {
            throw new IllegalStateException(
                    "The dedicated server memory handoff token is missing or malformed"
            );
        }
        return runToken;
    }

    private static Path requireOwnedArtifactPath(
            String environmentVariable,
            String expectedFileName
    ) {
        String rawPath = System.getenv(environmentVariable);
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalStateException(
                    "The dedicated server memory handoff path is missing"
            );
        }
        Path path = Path.of(rawPath).normalize();
        if (!path.isAbsolute()
                || path.getFileName() == null
                || !path.getFileName().toString().equals(expectedFileName)
                || !Files.isDirectory(path.getParent(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path.getParent())
                || Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(
                    "The dedicated server memory handoff path is unsafe: " + path
            );
        }
        return path;
    }

    private static void publishExclusive(Path target, String content) throws IOException {
        byte[] encoded = content.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAXIMUM_HANDOFF_SIZE_BYTES) {
            throw new IOException("The server memory handoff exceeded its size bound");
        }
        Path temporaryPath = Files.createTempFile(
                target.getParent(),
                ".server-memory-handoff.",
                ".tmp"
        );
        try {
            Files.write(temporaryPath, encoded);
            try {
                Files.createLink(target, temporaryPath);
            } catch (FileAlreadyExistsException exception) {
                throw new IOException(
                        "The server memory handoff already exists: " + target,
                        exception
                );
            } catch (UnsupportedOperationException exception) {
                throw new IOException(
                        "Atomic exclusive memory handoff publication is unsupported",
                        exception
                );
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void awaitAcknowledgement(
            Path acknowledgementPath,
            String runToken
    ) throws IOException {
        String expectedContent = "token=" + runToken + "\n";
        long deadline = System.nanoTime() + ACKNOWLEDGEMENT_TIMEOUT_NANOSECONDS;
        while (System.nanoTime() < deadline) {
            if (Files.exists(acknowledgementPath, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(
                        acknowledgementPath,
                        LinkOption.NOFOLLOW_LINKS
                ) || Files.isSymbolicLink(acknowledgementPath)) {
                    throw new IOException(
                            "The server memory acknowledgement is linked or irregular"
                    );
                }
                long size = Files.size(acknowledgementPath);
                if (size <= 0 || size > expectedContent.length()) {
                    throw new IOException(
                            "The server memory acknowledgement has an invalid size"
                    );
                }
                String actualContent = Files.readString(
                        acknowledgementPath,
                        StandardCharsets.UTF_8
                );
                if (!actualContent.equals(expectedContent)) {
                    throw new IOException(
                            "The server memory acknowledgement token does not match"
                    );
                }
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(
                        "The server memory acknowledgement wait was interrupted",
                        exception
                );
            }
        }
        throw new IOException("The server memory acknowledgement timed out");
    }
}
