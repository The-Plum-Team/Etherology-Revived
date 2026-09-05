import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/**
 * Keeps one stable process-group leader alive while a direct Gradle child runs.
 *
 * <p>This class intentionally has no package so JDK source-file mode can launch this
 * exact repository file without compiling or installing another artifact first.</p>
 */
public final class ForgeServerLaunchAnchor {
    private static final String READINESS_FILE_NAME =
        ".forge-server-launch-anchor-ready.json";
    private static final String START_FILE_NAME =
        ".forge-server-launch-anchor-start.json";
    private static final String CHILD_STARTED_FILE_NAME =
        "forge-server-launch-anchor-child-started.json";
    private static final String CHILD_RESULT_FILE_NAME =
        "forge-server-launch-anchor-child-result.json";
    private static final String FINISH_FILE_NAME =
        ".forge-server-launch-anchor-finish.json";
    private static final String READINESS_SCHEMA =
        "etherology-forge-server-launch-anchor-ready-v1";
    private static final String START_SCHEMA =
        "etherology-forge-server-launch-anchor-start-v1";
    private static final String CHILD_STARTED_SCHEMA =
        "etherology-forge-server-launch-anchor-child-started-v1";
    private static final String CHILD_RESULT_SCHEMA =
        "etherology-forge-server-launch-anchor-child-result-v1";
    private static final String FINISH_SCHEMA =
        "etherology-forge-server-launch-anchor-finish-v1";
    private static final int EXPECTED_JAVA_FEATURE = 21;
    private static final List<String> GRADLE_WRAPPER_COMMAND_PREFIX = List.of(
        "-Xmx2G",
        "-Xms64m",
        "-Dorg.gradle.appname=gradlew",
        "-classpath"
    );
    private static final String GRADLE_WRAPPER_JAR_NAME = "gradle-wrapper.jar";
    private static final String GRADLE_WRAPPER_MAIN_CLASS =
        "org.gradle.wrapper.GradleWrapperMain";
    private static final List<String> REQUIRED_GRADLE_ARGUMENT_PREFIX = List.of(
        "--no-daemon",
        "--no-parallel",
        "--max-workers=2",
        "--console=plain",
        "--offline"
    );
    private static final int MAXIMUM_ARTIFACT_SIZE_BYTES = 16 * 1024;
    private static final int MAXIMUM_WRAPPER_JAR_SIZE_BYTES = 4 * 1024 * 1024;
    private static final int MAXIMUM_WRAPPER_PROPERTIES_SIZE_BYTES = 64 * 1024;
    private static final long POLL_INTERVAL_NANOSECONDS = 50_000_000L;
    private static final long PRE_START_TIMEOUT_SECONDS = 30L;
    private static final long PRE_START_TIMEOUT_NANOSECONDS =
        PRE_START_TIMEOUT_SECONDS * 1_000_000_000L;
    private static final Set<PosixFilePermission> OWNER_READ_WRITE =
        PosixFilePermissions.fromString("rw-------");

    private ForgeServerLaunchAnchor() {
    }

    public static void main(String[] arguments) {
        LaunchState launchState = new LaunchState();
        try {
            run(parseArguments(arguments), launchState);
        } catch (Throwable exception) {
            if (launchState.childReleaseAuthorized()) {
                parkForever();
            }
            System.err.println("Launch anchor failed before child release");
            System.exit(70);
        }
    }

    private static void run(
        Configuration configuration,
        LaunchState launchState
    ) throws IOException {
        if (Runtime.version().feature() != EXPECTED_JAVA_FEATURE) {
            throw new SecurityException("The launch anchor requires exact JDK 21");
        }
        validateRuntimeDirectory(configuration);
        validatePinnedLaunchInputs(configuration);
        for (String fileName : artifactFileNames()) {
            if (artifactExists(configuration.runtimeDirectory().resolve(fileName))) {
                throw new FileAlreadyExistsException(fileName);
            }
        }

        validateControllerParent(configuration);
        long startWaitStartedAt = System.nanoTime();
        long anchorProcessId = ProcessHandle.current().pid();
        writeExclusiveAtomic(
            configuration,
            READINESS_FILE_NAME,
            readinessContent(configuration, anchorProcessId)
        );
        awaitExactStartArtifact(
            configuration,
            START_FILE_NAME,
            startContent(configuration),
            startWaitStartedAt,
            launchState
        );
        validateRuntimeDirectory(configuration);
        if (
            artifactExists(
                configuration.runtimeDirectory().resolve(CHILD_STARTED_FILE_NAME)
            )
            || artifactExists(
                configuration.runtimeDirectory().resolve(CHILD_RESULT_FILE_NAME)
            )
            || artifactExists(configuration.runtimeDirectory().resolve(FINISH_FILE_NAME))
        ) {
            throw new SecurityException("A launch result artifact appeared before start");
        }

        Process child;
        try {
            // This identity check is immediately adjacent to the OS spawn. The JDK
            // path and every parent are root-owned in production, so the controller
            // uid cannot exploit the remaining check-to-exec instruction window.
            validatePinnedLaunchInputs(configuration);
            ProcessBuilder builder = new ProcessBuilder(configuration.gradleArguments());
            builder.inheritIO();
            child = builder.start();
        } catch (IOException | SecurityException exception) {
            writeExclusiveAtomic(
                configuration,
                CHILD_RESULT_FILE_NAME,
                childResultContent(configuration, false, null, 127)
            );
            awaitExactArtifact(
                configuration,
                FINISH_FILE_NAME,
                finishContent(configuration, false, null, 127)
            );
            return;
        }

        long childProcessId = child.pid();
        try {
            writeExclusiveAtomic(
                configuration,
                CHILD_STARTED_FILE_NAME,
                childStartedContent(configuration, childProcessId)
            );
        } catch (Throwable exception) {
            waitForChildUninterruptibly(child);
            parkForever();
            return;
        }

        int childExitCode = waitForChildUninterruptibly(child);
        try {
            writeExclusiveAtomic(
                configuration,
                CHILD_RESULT_FILE_NAME,
                childResultContent(
                    configuration,
                    true,
                    childProcessId,
                    childExitCode
                )
            );
            awaitExactArtifact(
                configuration,
                FINISH_FILE_NAME,
                finishContent(
                    configuration,
                    true,
                    childProcessId,
                    childExitCode
                )
            );
        } catch (Throwable exception) {
            parkForever();
        }
    }

    private static Configuration parseArguments(String[] arguments) {
        if (
            arguments.length < 48
            || !"--runtime-directory".equals(arguments[0])
            || !"--runtime-device".equals(arguments[2])
            || !"--runtime-inode".equals(arguments[4])
            || !"--runtime-owner-uid".equals(arguments[6])
            || !"--token".equals(arguments[8])
            || !"--argv-sha256".equals(arguments[10])
            || !"--child-java-device".equals(arguments[12])
            || !"--child-java-inode".equals(arguments[14])
            || !"--child-java-owner-uid".equals(arguments[16])
            || !"--child-java-mode".equals(arguments[18])
            || !"--wrapper-jar-device".equals(arguments[20])
            || !"--wrapper-jar-inode".equals(arguments[22])
            || !"--wrapper-jar-owner-uid".equals(arguments[24])
            || !"--wrapper-jar-mode".equals(arguments[26])
            || !"--wrapper-jar-size".equals(arguments[28])
            || !"--wrapper-jar-sha256".equals(arguments[30])
            || !"--wrapper-properties-device".equals(arguments[32])
            || !"--wrapper-properties-inode".equals(arguments[34])
            || !"--wrapper-properties-owner-uid".equals(arguments[36])
            || !"--wrapper-properties-mode".equals(arguments[38])
            || !"--wrapper-properties-size".equals(arguments[40])
            || !"--wrapper-properties-sha256".equals(arguments[42])
            || !"--controller-pid".equals(arguments[44])
            || !"--".equals(arguments[46])
        ) {
            throw new IllegalArgumentException("Invalid launch-anchor arguments");
        }
        Path runtimeDirectory = Path.of(arguments[1]).normalize();
        if (!runtimeDirectory.isAbsolute()) {
            throw new IllegalArgumentException("Runtime directory must be absolute");
        }
        long runtimeDevice = parsePositiveLong(arguments[3], "runtime device");
        long runtimeInode = parsePositiveLong(arguments[5], "runtime inode");
        int runtimeOwnerUserId = parseNonNegativeInteger(
            arguments[7],
            "runtime owner uid"
        );
        String token = requireLowerHex(arguments[9], "token");
        String argumentsSha256 = requireLowerHex(
            arguments[11],
            "argv sha256"
        );
        long childJavaDevice = parsePositiveLong(
            arguments[13],
            "child Java device"
        );
        long childJavaInode = parsePositiveLong(
            arguments[15],
            "child Java inode"
        );
        int childJavaOwnerUserId = parseNonNegativeInteger(
            arguments[17],
            "child Java owner uid"
        );
        int childJavaMode = parseNonNegativeInteger(
            arguments[19],
            "child Java mode"
        );
        long wrapperJarDevice = parsePositiveLong(
            arguments[21],
            "wrapper JAR device"
        );
        long wrapperJarInode = parsePositiveLong(
            arguments[23],
            "wrapper JAR inode"
        );
        int wrapperJarOwnerUserId = parseNonNegativeInteger(
            arguments[25],
            "wrapper JAR owner uid"
        );
        int wrapperJarMode = parseNonNegativeInteger(
            arguments[27],
            "wrapper JAR mode"
        );
        long wrapperJarSize = parsePositiveLong(
            arguments[29],
            "wrapper JAR size"
        );
        String wrapperJarSha256 = requireLowerHex(
            arguments[31],
            "wrapper JAR sha256"
        );
        long wrapperPropertiesDevice = parsePositiveLong(
            arguments[33],
            "wrapper properties device"
        );
        long wrapperPropertiesInode = parsePositiveLong(
            arguments[35],
            "wrapper properties inode"
        );
        int wrapperPropertiesOwnerUserId = parseNonNegativeInteger(
            arguments[37],
            "wrapper properties owner uid"
        );
        int wrapperPropertiesMode = parseNonNegativeInteger(
            arguments[39],
            "wrapper properties mode"
        );
        long wrapperPropertiesSize = parsePositiveLong(
            arguments[41],
            "wrapper properties size"
        );
        String wrapperPropertiesSha256 = requireLowerHex(
            arguments[43],
            "wrapper properties sha256"
        );
        long controllerProcessId = parsePositiveLong(
            arguments[45],
            "controller pid"
        );
        List<String> gradleArguments = new ArrayList<>();
        for (int index = 47; index < arguments.length; index++) {
            if (arguments[index].isEmpty() || arguments[index].indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Invalid empty Gradle argument");
            }
            gradleArguments.add(arguments[index]);
        }
        if (gradleArguments.isEmpty()) {
            throw new IllegalArgumentException("Gradle arguments are missing");
        }
        validateDirectGradleWrapperCommand(gradleArguments);
        String calculatedArgumentsSha256 = argumentsSha256(gradleArguments);
        if (!calculatedArgumentsSha256.equals(argumentsSha256)) {
            throw new SecurityException("Gradle arguments do not match their digest");
        }
        return new Configuration(
            runtimeDirectory,
            runtimeDevice,
            runtimeInode,
            runtimeOwnerUserId,
            token,
            argumentsSha256,
            childJavaDevice,
            childJavaInode,
            childJavaOwnerUserId,
            childJavaMode,
            wrapperJarDevice,
            wrapperJarInode,
            wrapperJarOwnerUserId,
            wrapperJarMode,
            wrapperJarSize,
            wrapperJarSha256,
            wrapperPropertiesDevice,
            wrapperPropertiesInode,
            wrapperPropertiesOwnerUserId,
            wrapperPropertiesMode,
            wrapperPropertiesSize,
            wrapperPropertiesSha256,
            controllerProcessId,
            List.copyOf(gradleArguments)
        );
    }

    private static void validateDirectGradleWrapperCommand(List<String> arguments) {
        int wrapperJarIndex = 5;
        int mainClassIndex = 6;
        int gradleArgumentsIndex = 7;
        if (
            arguments.size()
                < gradleArgumentsIndex + REQUIRED_GRADLE_ARGUMENT_PREFIX.size() + 1
            || !Path.of(arguments.get(0)).isAbsolute()
            || !"java".equals(Path.of(arguments.get(0)).getFileName().toString())
            || !arguments.subList(1, wrapperJarIndex).equals(
                GRADLE_WRAPPER_COMMAND_PREFIX
            )
            || !Path.of(arguments.get(wrapperJarIndex)).isAbsolute()
            || !GRADLE_WRAPPER_JAR_NAME.equals(
                Path.of(arguments.get(wrapperJarIndex)).getFileName().toString()
            )
            || !GRADLE_WRAPPER_MAIN_CLASS.equals(arguments.get(mainClassIndex))
            || !arguments.subList(
                gradleArgumentsIndex,
                gradleArgumentsIndex + REQUIRED_GRADLE_ARGUMENT_PREFIX.size()
            ).equals(REQUIRED_GRADLE_ARGUMENT_PREFIX)
        ) {
            throw new SecurityException(
                "The child must be the exact direct JDK Gradle-wrapper command"
            );
        }
        for (int index = gradleArgumentsIndex; index < arguments.size(); index++) {
            if (arguments.get(index).startsWith("-P")) {
                throw new SecurityException("Gradle project properties are forbidden");
            }
        }
    }

    private static void validatePinnedLaunchInputs(Configuration configuration)
        throws IOException {
        Path childJava = Path.of(configuration.gradleArguments().get(0)).normalize();
        if (
            configuration.childJavaOwnerUserId() != 0
            || !childJava.isAbsolute()
        ) {
            throw new SecurityException(
                "The child JDK must be root-owned in production"
            );
        }
        validateRootOwnedParentChain(childJava);
        validatePinnedFile(
            childJava,
            configuration.childJavaDevice(),
            configuration.childJavaInode(),
            configuration.childJavaOwnerUserId(),
            configuration.childJavaMode(),
            -1,
            true
        );

        Path wrapperJar = Path.of(configuration.gradleArguments().get(5)).normalize();
        if (
            !wrapperJar.isAbsolute()
            || !configuration.runtimeDirectory().equals(wrapperJar.getParent())
            || configuration.wrapperJarOwnerUserId()
                != configuration.runtimeOwnerUserId()
            || configuration.wrapperJarMode() != 0400
            || configuration.wrapperJarSize() > MAXIMUM_WRAPPER_JAR_SIZE_BYTES
        ) {
            throw new SecurityException("The staged Gradle wrapper JAR is unsafe");
        }
        validatePinnedFile(
            wrapperJar,
            configuration.wrapperJarDevice(),
            configuration.wrapperJarInode(),
            configuration.wrapperJarOwnerUserId(),
            configuration.wrapperJarMode(),
            configuration.wrapperJarSize(),
            false
        );
        byte[] wrapperContent = Files.readAllBytes(wrapperJar);
        validatePinnedFile(
            wrapperJar,
            configuration.wrapperJarDevice(),
            configuration.wrapperJarInode(),
            configuration.wrapperJarOwnerUserId(),
            configuration.wrapperJarMode(),
            configuration.wrapperJarSize(),
            false
        );
        if (
            wrapperContent.length != configuration.wrapperJarSize()
            || !sha256(wrapperContent).equals(configuration.wrapperJarSha256())
        ) {
            throw new SecurityException("The staged Gradle wrapper JAR changed");
        }

        Path wrapperProperties = wrapperJar.resolveSibling(
            "gradle-wrapper.properties"
        );
        if (
            configuration.wrapperPropertiesOwnerUserId()
                != configuration.runtimeOwnerUserId()
            || configuration.wrapperPropertiesMode() != 0400
            || configuration.wrapperPropertiesSize()
                > MAXIMUM_WRAPPER_PROPERTIES_SIZE_BYTES
        ) {
            throw new SecurityException(
                "The staged Gradle wrapper properties are unsafe"
            );
        }
        validatePinnedFile(
            wrapperProperties,
            configuration.wrapperPropertiesDevice(),
            configuration.wrapperPropertiesInode(),
            configuration.wrapperPropertiesOwnerUserId(),
            configuration.wrapperPropertiesMode(),
            configuration.wrapperPropertiesSize(),
            false
        );
        byte[] propertiesContent = Files.readAllBytes(wrapperProperties);
        validatePinnedFile(
            wrapperProperties,
            configuration.wrapperPropertiesDevice(),
            configuration.wrapperPropertiesInode(),
            configuration.wrapperPropertiesOwnerUserId(),
            configuration.wrapperPropertiesMode(),
            configuration.wrapperPropertiesSize(),
            false
        );
        if (
            propertiesContent.length != configuration.wrapperPropertiesSize()
            || !sha256(propertiesContent).equals(
                configuration.wrapperPropertiesSha256()
            )
        ) {
            throw new SecurityException(
                "The staged Gradle wrapper properties changed"
            );
        }
        validateRuntimeDirectory(configuration);
    }

    private static void validatePinnedFile(
        Path path,
        long expectedDevice,
        long expectedInode,
        int expectedOwnerUserId,
        int expectedMode,
        long expectedSize,
        boolean executable
    ) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
            path,
            BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS
        );
        Number device = (Number) Files.getAttribute(
            path,
            "unix:dev",
            LinkOption.NOFOLLOW_LINKS
        );
        Number inode = (Number) Files.getAttribute(
            path,
            "unix:ino",
            LinkOption.NOFOLLOW_LINKS
        );
        Number ownerUserId = (Number) Files.getAttribute(
            path,
            "unix:uid",
            LinkOption.NOFOLLOW_LINKS
        );
        Number mode = (Number) Files.getAttribute(
            path,
            "unix:mode",
            LinkOption.NOFOLLOW_LINKS
        );
        Number linkCount = (Number) Files.getAttribute(
            path,
            "unix:nlink",
            LinkOption.NOFOLLOW_LINKS
        );
        int permissions = mode.intValue() & 07777;
        if (
            !attributes.isRegularFile()
            || attributes.isSymbolicLink()
            || device.longValue() != expectedDevice
            || inode.longValue() != expectedInode
            || ownerUserId.intValue() != expectedOwnerUserId
            || permissions != expectedMode
            || linkCount.longValue() != 1
            || (expectedSize >= 0 && attributes.size() != expectedSize)
            || (executable && (permissions & 0111) == 0)
            || (permissions & 0022) != 0
        ) {
            throw new SecurityException("A pinned launch input changed: " + path);
        }
    }

    private static void validateRootOwnedParentChain(Path path) throws IOException {
        Path current = path.getRoot();
        if (current == null) {
            throw new SecurityException("The child JDK path has no root");
        }
        validateRootOwnedDirectory(current);
        for (Path component : path.getParent()) {
            current = current.resolve(component);
            validateRootOwnedDirectory(current);
        }
    }

    private static void validateRootOwnedDirectory(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
            path,
            BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS
        );
        Number ownerUserId = (Number) Files.getAttribute(
            path,
            "unix:uid",
            LinkOption.NOFOLLOW_LINKS
        );
        Number mode = (Number) Files.getAttribute(
            path,
            "unix:mode",
            LinkOption.NOFOLLOW_LINKS
        );
        if (
            !attributes.isDirectory()
            || attributes.isSymbolicLink()
            || ownerUserId.intValue() != 0
            || (mode.intValue() & 0022) != 0
        ) {
            throw new SecurityException(
                "The child JDK parent chain is not root-owned: " + path
            );
        }
    }

    private static String sha256(byte[] content) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        return lowerHex(digest.digest(content));
    }

    private static List<String> artifactFileNames() {
        return List.of(
            READINESS_FILE_NAME,
            START_FILE_NAME,
            CHILD_STARTED_FILE_NAME,
            CHILD_RESULT_FILE_NAME,
            FINISH_FILE_NAME
        );
    }

    private static void validateRuntimeDirectory(Configuration configuration)
        throws IOException {
        Path runtimeDirectory = configuration.runtimeDirectory();
        BasicFileAttributes basicAttributes = Files.readAttributes(
            runtimeDirectory,
            BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS
        );
        Number device = (Number) Files.getAttribute(
            runtimeDirectory,
            "unix:dev",
            LinkOption.NOFOLLOW_LINKS
        );
        Number inode = (Number) Files.getAttribute(
            runtimeDirectory,
            "unix:ino",
            LinkOption.NOFOLLOW_LINKS
        );
        Number ownerUserId = (Number) Files.getAttribute(
            runtimeDirectory,
            "unix:uid",
            LinkOption.NOFOLLOW_LINKS
        );
        Number mode = (Number) Files.getAttribute(
            runtimeDirectory,
            "unix:mode",
            LinkOption.NOFOLLOW_LINKS
        );
        if (
            !basicAttributes.isDirectory()
            || basicAttributes.isSymbolicLink()
            || device.longValue() != configuration.runtimeDevice()
            || inode.longValue() != configuration.runtimeInode()
            || ownerUserId.intValue() != configuration.runtimeOwnerUserId()
            || (mode.intValue() & 0777) != 0700
        ) {
            throw new SecurityException("Launch-anchor runtime identity changed");
        }
    }

    private static void validateControllerParent(Configuration configuration) {
        ProcessHandle parent = ProcessHandle.current().parent().orElseThrow(
            () -> new SecurityException("The launch controller parent is absent")
        );
        if (
            configuration.controllerProcessId() <= 1
            || parent.pid() != configuration.controllerProcessId()
            || !parent.isAlive()
        ) {
            throw new SecurityException("The launch controller parent changed");
        }
    }

    private static boolean artifactExists(Path path) throws IOException {
        try {
            Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
            );
            return true;
        } catch (NoSuchFileException exception) {
            return false;
        }
    }

    private static void writeExclusiveAtomic(
        Configuration configuration,
        String fileName,
        byte[] content
    ) throws IOException {
        if (content.length == 0 || content.length > MAXIMUM_ARTIFACT_SIZE_BYTES) {
            throw new SecurityException("Launch-anchor artifact exceeded its byte bound");
        }
        validateRuntimeDirectory(configuration);
        Path destination = configuration.runtimeDirectory().resolve(fileName);
        if (artifactExists(destination)) {
            throw new FileAlreadyExistsException(fileName);
        }
        String temporaryName = "." + fileName + "." + configuration.token()
            + "." + Long.toUnsignedString(System.nanoTime(), 16) + ".tmp";
        Path temporary = configuration.runtimeDirectory().resolve(temporaryName);
        Set<OpenOption> options = new HashSet<>();
        options.add(StandardOpenOption.CREATE_NEW);
        options.add(StandardOpenOption.WRITE);
        options.add(LinkOption.NOFOLLOW_LINKS);
        FileAttribute<Set<PosixFilePermission>> permissions =
            PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE);
        boolean destinationCreated = false;
        try {
            try (
                SeekableByteChannel channel = Files.newByteChannel(
                    temporary,
                    options,
                    permissions
                )
            ) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                if (!(channel instanceof FileChannel fileChannel)) {
                    throw new IOException("Artifact channel cannot be synchronized");
                }
                fileChannel.force(true);
            }
            Files.setPosixFilePermissions(temporary, OWNER_READ_WRITE);
            validateArtifact(temporary, content);
            validateRuntimeDirectory(configuration);
            Files.createLink(destination, temporary);
            destinationCreated = true;
            Files.delete(temporary);
            validateRuntimeDirectory(configuration);
            validateArtifact(destination, content);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The final artifact remains fail-closed if temporary cleanup fails.
            }
            if (destinationCreated) {
                validateRuntimeDirectory(configuration);
            }
        }
    }

    private static void validateArtifact(Path path, byte[] expectedContent)
        throws IOException {
        BasicFileAttributes before = Files.readAttributes(
            path,
            BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS
        );
        Number beforeDevice = (Number) Files.getAttribute(
            path,
            "unix:dev",
            LinkOption.NOFOLLOW_LINKS
        );
        Number beforeInode = (Number) Files.getAttribute(
            path,
            "unix:ino",
            LinkOption.NOFOLLOW_LINKS
        );
        Number ownerUserId = (Number) Files.getAttribute(
            path,
            "unix:uid",
            LinkOption.NOFOLLOW_LINKS
        );
        Number mode = (Number) Files.getAttribute(
            path,
            "unix:mode",
            LinkOption.NOFOLLOW_LINKS
        );
        Number linkCount = (Number) Files.getAttribute(
            path,
            "unix:nlink",
            LinkOption.NOFOLLOW_LINKS
        );
        if (
            !before.isRegularFile()
            || before.isSymbolicLink()
            || ownerUserId.intValue() != currentOwnerUserId(path.getParent())
            || (mode.intValue() & 0777) != 0600
            || before.size() != expectedContent.length
        ) {
            throw new SecurityException("Unsafe launch-anchor artifact");
        }
        if (linkCount.longValue() == 2) {
            throw new ArtifactPublicationPendingException();
        }
        if (linkCount.longValue() != 1) {
            throw new SecurityException("Unsafe launch-anchor artifact");
        }
        byte[] observedContent = Files.readAllBytes(path);
        BasicFileAttributes after = Files.readAttributes(
            path,
            BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS
        );
        Number afterDevice = (Number) Files.getAttribute(
            path,
            "unix:dev",
            LinkOption.NOFOLLOW_LINKS
        );
        Number afterInode = (Number) Files.getAttribute(
            path,
            "unix:ino",
            LinkOption.NOFOLLOW_LINKS
        );
        if (
            beforeDevice.longValue() != afterDevice.longValue()
            || beforeInode.longValue() != afterInode.longValue()
            || before.size() != after.size()
            || !MessageDigest.isEqual(observedContent, expectedContent)
        ) {
            throw new SecurityException("Launch-anchor artifact changed while read");
        }
    }

    private static int currentOwnerUserId(Path runtimeDirectory) throws IOException {
        return ((Number) Files.getAttribute(
            runtimeDirectory,
            "unix:uid",
            LinkOption.NOFOLLOW_LINKS
        )).intValue();
    }

    private static void awaitExactArtifact(
        Configuration configuration,
        String fileName,
        byte[] expectedContent
    ) throws IOException {
        Path path = configuration.runtimeDirectory().resolve(fileName);
        while (true) {
            validateRuntimeDirectory(configuration);
            if (artifactExists(path)) {
                try {
                    validateArtifact(path, expectedContent);
                    return;
                } catch (ArtifactPublicationPendingException exception) {
                    // The publishing hard link is complete only after its temp is gone.
                }
            }
            clearInterruptAndPause();
        }
    }

    private static void awaitExactStartArtifact(
        Configuration configuration,
        String fileName,
        byte[] expectedContent,
        long waitStartedAt,
        LaunchState launchState
    ) throws IOException {
        Path path = configuration.runtimeDirectory().resolve(fileName);
        while (true) {
            validateRuntimeDirectory(configuration);
            if (artifactExists(path)) {
                try {
                    validateArtifact(path, expectedContent);
                    launchState.authorizeChildRelease();
                    return;
                } catch (ArtifactPublicationPendingException exception) {
                    // The publishing hard link is complete only after its temp is gone.
                }
            }
            validateControllerParent(configuration);
            if (
                System.nanoTime() - waitStartedAt
                    >= PRE_START_TIMEOUT_NANOSECONDS
            ) {
                throw new SecurityException(
                    "The launch controller did not authorize a child in time"
                );
            }
            clearInterruptAndPause();
        }
    }

    private static byte[] readinessContent(
        Configuration configuration,
        long anchorProcessId
    ) {
        String content = "{\"argv_sha256\":\"" + configuration.argumentsSha256()
            + "\",\"controller_pid\":" + configuration.controllerProcessId()
            + ",\"java_feature\":" + EXPECTED_JAVA_FEATURE
            + ",\"pid\":" + anchorProcessId
            + ",\"pre_start_timeout_seconds\":" + PRE_START_TIMEOUT_SECONDS
            + ",\"runtime_device\":" + configuration.runtimeDevice()
            + ",\"runtime_inode\":" + configuration.runtimeInode()
            + ",\"schema\":\"" + READINESS_SCHEMA
            + "\",\"token\":\"" + configuration.token() + "\"}\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] startContent(Configuration configuration) {
        String content = "{\"argv_sha256\":\"" + configuration.argumentsSha256()
            + "\",\"schema\":\"" + START_SCHEMA
            + "\",\"token\":\"" + configuration.token() + "\"}\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] childStartedContent(
        Configuration configuration,
        long childProcessId
    ) {
        String executable = jsonString(configuration.gradleArguments().get(0));
        String content = "{\"argv_sha256\":\"" + configuration.argumentsSha256()
            + "\",\"executable\":" + executable
            + ",\"pid\":" + childProcessId
            + ",\"schema\":\"" + CHILD_STARTED_SCHEMA
            + "\",\"token\":\"" + configuration.token() + "\"}\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] childResultContent(
        Configuration configuration,
        boolean started,
        Long childProcessId,
        int childExitCode
    ) {
        String processId = childProcessId == null
            ? "null"
            : Long.toString(childProcessId);
        String content = "{\"argv_sha256\":\"" + configuration.argumentsSha256()
            + "\",\"exit_code\":" + childExitCode
            + ",\"pid\":" + processId
            + ",\"schema\":\"" + CHILD_RESULT_SCHEMA
            + "\",\"started\":" + started
            + ",\"token\":\"" + configuration.token() + "\"}\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] finishContent(
        Configuration configuration,
        boolean started,
        Long childProcessId,
        int childExitCode
    ) {
        String processId = childProcessId == null
            ? "null"
            : Long.toString(childProcessId);
        String content = "{\"argv_sha256\":\"" + configuration.argumentsSha256()
            + "\",\"child_exit_code\":" + childExitCode
            + ",\"child_pid\":" + processId
            + ",\"child_started\":" + started
            + ",\"schema\":\"" + FINISH_SCHEMA
            + "\",\"token\":\"" + configuration.token() + "\"}\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static String jsonString(String value) {
        StringBuilder output = new StringBuilder(value.length() + 2);
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20 || character > 0x7e) {
                        output.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
        return output.toString();
    }

    private static String argumentsSha256(List<String> arguments) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        for (String argument : arguments) {
            byte[] encoded = argument.getBytes(StandardCharsets.UTF_8);
            ByteBuffer length = ByteBuffer.allocate(Long.BYTES);
            length.putLong(encoded.length);
            digest.update(length.array());
            digest.update(encoded);
        }
        return lowerHex(digest.digest());
    }

    private static String lowerHex(byte[] value) {
        StringBuilder output = new StringBuilder(value.length * 2);
        for (byte part : value) {
            output.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        }
        return output.toString();
    }

    private static String requireLowerHex(String value, String description) {
        if (value.length() != 64) {
            throw new IllegalArgumentException(description + " is not SHA-256 sized");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (
                !((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))
            ) {
                throw new IllegalArgumentException(description + " is not lower hex");
            }
        }
        return value;
    }

    private static long parsePositiveLong(String value, String description) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(description + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(description + " is invalid", exception);
        }
    }

    private static int parseNonNegativeInteger(String value, String description) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(description + " must be non-negative");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(description + " is invalid", exception);
        }
    }

    private static int waitForChildUninterruptibly(Process child) {
        while (true) {
            try {
                return child.waitFor();
            } catch (InterruptedException exception) {
                Thread.interrupted();
            }
        }
    }

    private static void clearInterruptAndPause() {
        Thread.interrupted();
        LockSupport.parkNanos(POLL_INTERVAL_NANOSECONDS);
    }

    private static void parkForever() {
        while (true) {
            clearInterruptAndPause();
        }
    }

    private record Configuration(
        Path runtimeDirectory,
        long runtimeDevice,
        long runtimeInode,
        int runtimeOwnerUserId,
        String token,
        String argumentsSha256,
        long childJavaDevice,
        long childJavaInode,
        int childJavaOwnerUserId,
        int childJavaMode,
        long wrapperJarDevice,
        long wrapperJarInode,
        int wrapperJarOwnerUserId,
        int wrapperJarMode,
        long wrapperJarSize,
        String wrapperJarSha256,
        long wrapperPropertiesDevice,
        long wrapperPropertiesInode,
        int wrapperPropertiesOwnerUserId,
        int wrapperPropertiesMode,
        long wrapperPropertiesSize,
        String wrapperPropertiesSha256,
        long controllerProcessId,
        List<String> gradleArguments
    ) {
    }

    private static final class LaunchState {
        private boolean childReleaseAuthorized;

        private void authorizeChildRelease() {
            childReleaseAuthorized = true;
        }

        private boolean childReleaseAuthorized() {
            return childReleaseAuthorized;
        }
    }

    private static final class ArtifactPublicationPendingException
        extends IOException {
        private ArtifactPublicationPendingException() {
            super("Launch-anchor artifact publication is incomplete");
        }
    }
}
