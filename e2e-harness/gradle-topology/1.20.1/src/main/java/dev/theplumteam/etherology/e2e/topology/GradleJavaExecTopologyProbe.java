package dev.theplumteam.etherology.e2e.topology;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Proves that a loader-free JavaExec child remains inside its Gradle launch.
 */
public final class GradleJavaExecTopologyProbe {

    private static final String ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE =
            "ETHERLOGY_E2E_FORGE_SERVER_GRADLE_TOPOLOGY_ACKNOWLEDGEMENT";
    private static final String HANDOFF_ENVIRONMENT_VARIABLE =
            "ETHERLOGY_E2E_FORGE_SERVER_GRADLE_TOPOLOGY_HANDOFF";
    private static final String TOKEN_ENVIRONMENT_VARIABLE =
            "ETHERLOGY_E2E_FORGE_SERVER_GRADLE_TOPOLOGY_TOKEN";
    private static final String ACKNOWLEDGEMENT_FILE_NAME =
            ".forge-server-gradle-topology-ready";
    private static final String HANDOFF_FILE_NAME =
            ".forge-server-gradle-topology-handoff";
    private static final String EXACT_MAXIMUM_HEAP_ARGUMENT = "-Xmx2048m";
    private static final long EXACT_MAXIMUM_HEAP_BYTES =
            2L * 1024L * 1024L * 1024L;
    private static final int MAXIMUM_HANDOFF_SIZE_BYTES = 16 * 1024;
    private static final long ACKNOWLEDGEMENT_TIMEOUT_NANOSECONDS =
            TimeUnit.SECONDS.toNanos(15L);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final List<String> JAVA_OPTION_ENVIRONMENT_VARIABLES = List.of(
            "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "_JAVA_OPTIONS"
    );

    private GradleJavaExecTopologyProbe() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("The topology probe accepts no arguments");
        }
        String token = requireToken();
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
                    "The topology artifacts do not share one runtime"
            );
        }
        List<String> inheritedJavaOptionVariables = JAVA_OPTION_ENVIRONMENT_VARIABLES
                .stream()
                .filter(name -> System.getenv(name) != null)
                .toList();
        if (!inheritedJavaOptionVariables.isEmpty()) {
            throw new IllegalStateException(
                    "The topology JavaExec inherited Java option injection through "
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
                    "The topology JavaExec requires exactly one "
                            + EXACT_MAXIMUM_HEAP_ARGUMENT
            );
        }
        if (Runtime.version().feature() != 17) {
            throw new IllegalStateException("The topology JavaExec requires Java 17");
        }
        long maximumHeapBytes = Runtime.getRuntime().maxMemory();
        if (maximumHeapBytes != EXACT_MAXIMUM_HEAP_BYTES) {
            throw new IllegalStateException(
                    "The topology JavaExec maximum heap is not exactly 2048 MiB"
            );
        }

        ProcessHandle currentProcess = ProcessHandle.current();
        ProcessHandle parentProcess = currentProcess.parent().orElseThrow(
                () -> new IllegalStateException(
                        "The topology JavaExec parent is unavailable"
                )
        );
        String executable = requireExecutable(currentProcess, "JavaExec");
        String parentExecutable = requireExecutable(parentProcess, "parent");
        Base64.Encoder encoder = Base64.getEncoder();
        String handoff = String.join(
                "\n",
                "schema=1",
                "run_token=" + token,
                "pid=" + currentProcess.pid(),
                "parent_pid=" + parentProcess.pid(),
                "executable_base64=" + encode(encoder, executable),
                "parent_executable_base64=" + encode(encoder, parentExecutable),
                "java_feature=" + Runtime.version().feature(),
                "maximum_heap_bytes=" + maximumHeapBytes,
                "maximum_heap_argument=" + EXACT_MAXIMUM_HEAP_ARGUMENT,
                ""
        );
        try {
            publishExclusive(handoffPath, handoff);
            awaitAcknowledgement(acknowledgementPath, token);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "The topology JavaExec handoff failed",
                    exception
            );
        }
    }

    private static String requireToken() {
        String token = System.getenv(TOKEN_ENVIRONMENT_VARIABLE);
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
            throw new IllegalStateException("The topology token is missing or malformed");
        }
        return token;
    }

    private static Path requireOwnedArtifactPath(
            String environmentVariable,
            String expectedFileName
    ) {
        String rawPath = System.getenv(environmentVariable);
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalStateException("The topology artifact path is missing");
        }
        Path path = Path.of(rawPath).normalize();
        if (!path.isAbsolute()
                || path.getFileName() == null
                || !path.getFileName().toString().equals(expectedFileName)
                || !Files.isDirectory(path.getParent(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path.getParent())
                || Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(
                    "The topology artifact path is unsafe: " + path
            );
        }
        return path;
    }

    private static String requireExecutable(
            ProcessHandle process,
            String description
    ) {
        String executable = process.info().command().orElseThrow(
                () -> new IllegalStateException(
                        "The topology " + description + " executable is unavailable"
                )
        );
        Path path = Path.of(executable).normalize();
        if (!path.isAbsolute()) {
            throw new IllegalStateException(
                    "The topology " + description + " executable is not absolute"
            );
        }
        return path.toString();
    }

    private static String encode(Base64.Encoder encoder, String value) {
        return encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void publishExclusive(Path target, String content) throws IOException {
        byte[] encoded = content.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAXIMUM_HANDOFF_SIZE_BYTES) {
            throw new IOException("The topology handoff exceeded its size bound");
        }
        Path temporaryPath = Files.createTempFile(
                target.getParent(),
                ".gradle-topology-handoff.",
                ".tmp"
        );
        try {
            Files.write(temporaryPath, encoded);
            try {
                Files.createLink(target, temporaryPath);
            } catch (FileAlreadyExistsException exception) {
                throw new IOException(
                        "The topology handoff already exists: " + target,
                        exception
                );
            } catch (UnsupportedOperationException exception) {
                throw new IOException(
                        "Atomic exclusive topology publication is unsupported",
                        exception
                );
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void awaitAcknowledgement(
            Path acknowledgementPath,
            String token
    ) throws IOException {
        String expectedContent = "token=" + token + "\n";
        long deadline = System.nanoTime() + ACKNOWLEDGEMENT_TIMEOUT_NANOSECONDS;
        while (System.nanoTime() < deadline) {
            if (Files.exists(acknowledgementPath, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(
                        acknowledgementPath,
                        LinkOption.NOFOLLOW_LINKS
                ) || Files.isSymbolicLink(acknowledgementPath)) {
                    throw new IOException(
                            "The topology acknowledgement is linked or irregular"
                    );
                }
                long size = Files.size(acknowledgementPath);
                if (size <= 0 || size > expectedContent.length()) {
                    throw new IOException(
                            "The topology acknowledgement has an invalid size"
                    );
                }
                if (!Files.readString(
                        acknowledgementPath,
                        StandardCharsets.UTF_8
                ).equals(expectedContent)) {
                    throw new IOException(
                            "The topology acknowledgement token does not match"
                    );
                }
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(
                        "The topology acknowledgement wait was interrupted",
                        exception
                );
            }
        }
        throw new IOException("The topology acknowledgement timed out");
    }
}
