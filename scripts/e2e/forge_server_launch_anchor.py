#!/usr/bin/env python3
"""Stable JDK 21 process-group anchor for one direct Gradle-wrapper JVM."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess
import time
from typing import Callable, Mapping, Sequence


READINESS_FILE_NAME = ".forge-server-launch-anchor-ready.json"
START_FILE_NAME = ".forge-server-launch-anchor-start.json"
CHILD_STARTED_FILE_NAME = "forge-server-launch-anchor-child-started.json"
CHILD_RESULT_FILE_NAME = "forge-server-launch-anchor-child-result.json"
FINISH_FILE_NAME = ".forge-server-launch-anchor-finish.json"
STAGED_SOURCE_FILE_NAME = "ForgeServerLaunchAnchor.java"
STAGED_WRAPPER_JAR_FILE_NAME = "gradle-wrapper.jar"
STAGED_WRAPPER_PROPERTIES_FILE_NAME = "gradle-wrapper.properties"
READINESS_SCHEMA = "etherology-forge-server-launch-anchor-ready-v1"
START_SCHEMA = "etherology-forge-server-launch-anchor-start-v1"
CHILD_STARTED_SCHEMA = "etherology-forge-server-launch-anchor-child-started-v1"
CHILD_RESULT_SCHEMA = "etherology-forge-server-launch-anchor-child-result-v1"
FINISH_SCHEMA = "etherology-forge-server-launch-anchor-finish-v1"
EXPECTED_JAVA_FEATURE = 21
ANCHOR_JVM_ARGUMENTS = (
    "-Xms16m",
    "-Xmx64m",
    "-XX:MaxDirectMemorySize=64m",
    "-XX:MaxMetaspaceSize=128m",
    "-XX:ReservedCodeCacheSize=64m",
    "-XX:ActiveProcessorCount=2",
)
GRADLE_WRAPPER_JVM_ARGUMENTS = (
    "-Xmx2G",
    "-Xms64m",
)
GRADLE_WRAPPER_APPLICATION_ARGUMENT = "-Dorg.gradle.appname=gradlew"
GRADLE_WRAPPER_MAIN_CLASS = "org.gradle.wrapper.GradleWrapperMain"
REQUIRED_GRADLE_ARGUMENT_PREFIX = (
    "--no-daemon",
    "--no-parallel",
    "--max-workers=2",
    "--console=plain",
    "--offline",
)
MAXIMUM_ARTIFACT_SIZE_BYTES = 16 * 1024
MAXIMUM_SOURCE_SIZE_BYTES = 256 * 1024
MAXIMUM_WRAPPER_JAR_SIZE_BYTES = 4 * 1024 * 1024
MAXIMUM_WRAPPER_PROPERTIES_SIZE_BYTES = 64 * 1024
MAXIMUM_ARGUMENT_COUNT = 256
MAXIMUM_ARGUMENT_SIZE_BYTES = 8 * 1024
MAXIMUM_ARGUMENTS_SIZE_BYTES = 64 * 1024
MAXIMUM_TIMEOUT_SECONDS = 60 * 60
DEFAULT_READINESS_TIMEOUT_SECONDS = 20.0
DEFAULT_RESULT_TIMEOUT_SECONDS = 20 * 60.0
DEFAULT_FINISH_TIMEOUT_SECONDS = 20.0
PRE_START_TIMEOUT_SECONDS = 30
POLL_INTERVAL_SECONDS = 0.05
LOWER_SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")
ARTIFACT_FILE_NAMES = (
    READINESS_FILE_NAME,
    START_FILE_NAME,
    CHILD_STARTED_FILE_NAME,
    CHILD_RESULT_FILE_NAME,
    FINISH_FILE_NAME,
)


class LaunchAnchorError(RuntimeError):
    """Reports an invalid or unsafe stable launch-anchor state."""


class LaunchAnchorStartError(LaunchAnchorError):
    """Preserves a spawned anchor when bounded readiness cannot be proved."""

    def __init__(self, message: str, handle: LaunchAnchorHandle) -> None:
        super().__init__(message)
        self.handle = handle


class LaunchAnchorChildStartError(LaunchAnchorError):
    """Reports that the anchor could not create its direct Gradle JVM child."""


class _ArtifactPublicationPending(LaunchAnchorError):
    """Marks the bounded hard-link interval before an artifact becomes immutable."""


@dataclass(frozen=True)
class FileIdentity:
    """Pins one filesystem object independently of its mutable path."""

    device: int
    inode: int
    owner_user_id: int
    mode: int
    link_count: int
    size: int


@dataclass(frozen=True)
class PinnedArtifact:
    """Pins one authenticated immutable control artifact."""

    file_name: str
    identity: FileIdentity
    content: bytes
    sha256: str


@dataclass
class PinnedRuntimeInput:
    """Keeps one immutable source or classpath input pinned by descriptor."""

    file_name: str
    path: Path
    descriptor: int
    identity: FileIdentity
    content: bytes
    sha256: str


@dataclass(frozen=True)
class ChildStarted:
    """Identifies the direct Gradle-wrapper JVM released by the anchor."""

    pid: int
    executable: str
    arguments_sha256: str


@dataclass(frozen=True)
class ChildResult:
    """Carries the completed direct-child outcome while the anchor remains alive."""

    started: bool
    pid: int | None
    exit_code: int
    arguments_sha256: str


class LaunchAnchorHandle:
    """Owns a live anchor, its pinned runtime, and authenticated artifacts."""

    def __init__(
        self,
        process: subprocess.Popen[bytes],
        runtime_directory: Path,
        runtime_directory_descriptor: int,
        runtime_identity: FileIdentity,
        token: str,
        controller_process_id: int,
        java_path: Path,
        java_identity: FileIdentity,
        java_root_owned: bool,
        source_path: Path,
        staged_source: PinnedRuntimeInput,
        gradle_wrapper_jar_path: Path,
        staged_wrapper_jar: PinnedRuntimeInput,
        gradle_wrapper_properties_path: Path,
        staged_wrapper_properties: PinnedRuntimeInput,
        gradle_arguments: tuple[str, ...],
        child_command: tuple[str, ...],
        arguments_sha256: str,
        child_release_guard: Callable[[LaunchAnchorHandle], None],
    ) -> None:
        self.process = process
        self.runtime_directory = runtime_directory
        self.runtime_directory_descriptor = runtime_directory_descriptor
        self.runtime_identity = runtime_identity
        self.token = token
        self.controller_process_id = controller_process_id
        self.java_path = java_path
        self.java_identity = java_identity
        self.java_root_owned = java_root_owned
        self.source_path = source_path
        self.staged_source = staged_source
        self.launched_source_path = staged_source.path
        self.gradle_wrapper_jar_path = gradle_wrapper_jar_path
        self.staged_wrapper_jar = staged_wrapper_jar
        self.gradle_wrapper_properties_path = gradle_wrapper_properties_path
        self.staged_wrapper_properties = staged_wrapper_properties
        self.gradle_arguments = gradle_arguments
        self.child_command = child_command
        self.arguments_sha256 = arguments_sha256
        self.child_release_guard = child_release_guard
        self.pinned_artifacts: dict[str, PinnedArtifact] = {}

    @property
    def anchor_pid(self) -> int:
        """Returns the process id that is also the owned session and group id."""

        return self.process.pid

    @property
    def process_group_id(self) -> int:
        """Returns the stable process-group id used by the external watchdog."""

        return self.process.pid

    def start_child(self) -> None:
        """Releases the direct Gradle JVM only through an authenticated token."""

        start_launch_anchor_child(self)

    def poll_child_started(self) -> ChildStarted | None:
        """Returns the authenticated child identity without blocking."""

        return poll_launch_anchor_child_started(self)

    def wait_child_started(
        self,
        *,
        timeout_seconds: float = DEFAULT_READINESS_TIMEOUT_SECONDS,
        monotonic: Callable[[], float] = time.monotonic,
        sleep: Callable[[float], None] = time.sleep,
    ) -> ChildStarted:
        """Waits a bounded interval for the direct Gradle JVM identity."""

        return wait_for_launch_anchor_child_started(
            self,
            timeout_seconds=timeout_seconds,
            monotonic=monotonic,
            sleep=sleep,
        )

    def poll_child_result(self) -> ChildResult | None:
        """Returns a completed child result while the anchor stays alive."""

        return poll_launch_anchor_child_result(self)

    def wait_child_result(
        self,
        *,
        timeout_seconds: float = DEFAULT_RESULT_TIMEOUT_SECONDS,
        monotonic: Callable[[], float] = time.monotonic,
        sleep: Callable[[float], None] = time.sleep,
    ) -> ChildResult:
        """Waits a bounded interval for the direct child to finish."""

        return wait_for_launch_anchor_child_result(
            self,
            timeout_seconds=timeout_seconds,
            monotonic=monotonic,
            sleep=sleep,
        )

    def finish_normally(
        self,
        *,
        timeout_seconds: float = DEFAULT_FINISH_TIMEOUT_SECONDS,
    ) -> int:
        """Releases and reaps the anchor only after authenticated child completion."""

        return finish_launch_anchor(self, timeout_seconds=timeout_seconds)

    def verify_pinned_artifacts(self) -> None:
        """Revalidates every artifact already accepted by this handle."""

        verify_pinned_launch_anchor_artifacts(self)

    def close(self) -> None:
        """Closes the pinned directory only after the anchor has been reaped."""

        close_launch_anchor_handle(self)


def _bounded_detail(value: object, maximum_size: int = 512) -> str:
    encoded = str(value).encode("utf-8", errors="replace")
    if len(encoded) <= maximum_size:
        return encoded.decode("utf-8")
    return encoded[:maximum_size].decode("utf-8", errors="ignore")


def _require_positive_timeout(value: float, description: str) -> None:
    if (
        isinstance(value, bool)
        or not isinstance(value, (int, float))
        or value <= 0
        or value > MAXIMUM_TIMEOUT_SECONDS
    ):
        raise LaunchAnchorError(f"{description} must be positive and bounded")


def _require_lower_sha256(value: str, description: str) -> None:
    if not isinstance(value, str) or LOWER_SHA256_PATTERN.fullmatch(value) is None:
        raise LaunchAnchorError(f"{description} must be one lowercase SHA-256 digest")


def _validate_no_symlink_components(path: Path, description: str) -> None:
    if not path.is_absolute():
        raise LaunchAnchorError(f"{description} must be absolute: {path}")
    current = Path(path.anchor)
    try:
        for component in path.parts[1:]:
            current /= component
            information = current.lstat()
            if stat.S_ISLNK(information.st_mode):
                raise LaunchAnchorError(
                    f"{description} contains a symbolic link: {current}"
                )
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot inspect {description}: {_bounded_detail(exception)}"
        ) from exception


def _file_identity(information: os.stat_result) -> FileIdentity:
    return FileIdentity(
        device=information.st_dev,
        inode=information.st_ino,
        owner_user_id=information.st_uid,
        mode=stat.S_IMODE(information.st_mode),
        link_count=information.st_nlink,
        size=information.st_size,
    )


def _validate_protected_file(
    path: Path,
    description: str,
    *,
    executable: bool,
    maximum_size: int | None = None,
    required_owner_user_id: int | None = None,
) -> FileIdentity:
    _validate_no_symlink_components(path, description)
    try:
        information = path.lstat()
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot inspect {description}: {_bounded_detail(exception)}"
        ) from exception
    if (
        not stat.S_ISREG(information.st_mode)
        or stat.S_ISLNK(information.st_mode)
        or (
            required_owner_user_id is None
            and information.st_uid not in (0, os.getuid())
        )
        or (
            required_owner_user_id is not None
            and information.st_uid != required_owner_user_id
        )
        or stat.S_IMODE(information.st_mode) & 0o022
        or information.st_nlink != 1
        or information.st_size <= 0
        or (maximum_size is not None and information.st_size > maximum_size)
        or (executable and not os.access(path, os.X_OK))
    ):
        raise LaunchAnchorError(f"Unsafe {description}: {path}")
    return _file_identity(information)


def _validate_root_owned_parent_chain(path: Path) -> None:
    current = Path(path.anchor)
    for component in path.parent.parts[1:]:
        current /= component
        try:
            information = current.lstat()
        except OSError as exception:
            raise LaunchAnchorError(
                f"Cannot inspect JDK parent chain: {_bounded_detail(exception)}"
            ) from exception
        if (
            not stat.S_ISDIR(information.st_mode)
            or stat.S_ISLNK(information.st_mode)
            or information.st_uid != 0
            or stat.S_IMODE(information.st_mode) & 0o022
        ):
            raise LaunchAnchorError(
                f"JDK 21 parent chain is not root-owned and protected: {current}"
            )


def validate_root_owned_java_executable(java_path: Path) -> FileIdentity:
    """Requires one executable from an immutable root-owned JDK path."""

    java_path = Path(java_path)
    if not java_path.is_absolute() or java_path.name != "java":
        raise LaunchAnchorError("The selected JDK executable path is not exact")
    _validate_root_owned_parent_chain(java_path)
    return _validate_protected_file(
        java_path,
        "JDK 21 java executable",
        executable=True,
        required_owner_user_id=0,
    )


def _validate_working_directory(path: Path) -> None:
    _validate_no_symlink_components(path, "launch-anchor working directory")
    information = path.lstat()
    if (
        not stat.S_ISDIR(information.st_mode)
        or information.st_uid != os.getuid()
        or stat.S_IMODE(information.st_mode) & 0o022
    ):
        raise LaunchAnchorError(f"Unsafe launch-anchor working directory: {path}")


def _open_runtime_directory(runtime_directory: Path) -> tuple[int, FileIdentity]:
    _validate_no_symlink_components(
        runtime_directory,
        "launch-anchor runtime directory",
    )
    flags = os.O_RDONLY
    flags |= getattr(os, "O_DIRECTORY", 0)
    flags |= getattr(os, "O_CLOEXEC", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(runtime_directory, flags)
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot pin launch-anchor runtime: {_bounded_detail(exception)}"
        ) from exception
    try:
        if descriptor < 3:
            raise LaunchAnchorError(
                "Launch-anchor runtime must use one dedicated file descriptor"
            )
        descriptor_information = os.fstat(descriptor)
        path_information = runtime_directory.lstat()
        identity = _file_identity(descriptor_information)
        if (
            not stat.S_ISDIR(descriptor_information.st_mode)
            or stat.S_ISLNK(path_information.st_mode)
            or path_information.st_uid != os.getuid()
            or descriptor_information.st_uid != os.getuid()
            or stat.S_IMODE(path_information.st_mode) != 0o700
            or identity.mode != 0o700
            or path_information.st_dev != identity.device
            or path_information.st_ino != identity.inode
        ):
            raise LaunchAnchorError(
                "Launch-anchor runtime must be one owner-private 0700 directory"
            )
        return descriptor, identity
    except BaseException:
        os.close(descriptor)
        raise


def _validate_runtime(handle: LaunchAnchorHandle) -> None:
    if handle.runtime_directory_descriptor < 3:
        raise LaunchAnchorError("The launch-anchor runtime descriptor is closed")
    try:
        descriptor_information = os.fstat(handle.runtime_directory_descriptor)
        path_information = os.stat(
            handle.runtime_directory,
            follow_symlinks=False,
        )
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot revalidate launch-anchor runtime: {_bounded_detail(exception)}"
        ) from exception
    if (
        descriptor_information.st_dev != handle.runtime_identity.device
        or descriptor_information.st_ino != handle.runtime_identity.inode
        or descriptor_information.st_uid != handle.runtime_identity.owner_user_id
        or stat.S_IMODE(descriptor_information.st_mode) != handle.runtime_identity.mode
        or not stat.S_ISDIR(path_information.st_mode)
        or stat.S_ISLNK(path_information.st_mode)
        or path_information.st_uid != os.getuid()
        or stat.S_IMODE(path_information.st_mode) != 0o700
        or path_information.st_dev != handle.runtime_identity.device
        or path_information.st_ino != handle.runtime_identity.inode
    ):
        raise LaunchAnchorError("The pinned launch-anchor runtime identity changed")


def _pin_repository_input(
    path: Path,
    expected_size: int,
    expected_sha256: str,
    maximum_size: int,
    description: str,
) -> tuple[int, FileIdentity, bytes]:
    if type(expected_size) is not int or not 0 < expected_size <= maximum_size:
        raise LaunchAnchorError(f"The expected {description} size is invalid")
    _require_lower_sha256(expected_sha256, f"{description} digest")
    expected_identity = _validate_protected_file(
        path,
        description,
        executable=False,
        maximum_size=maximum_size,
    )
    flags = os.O_RDONLY
    flags |= getattr(os, "O_CLOEXEC", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(path, flags)
    try:
        descriptor_information = os.fstat(descriptor)
        if _file_identity(descriptor_information) != expected_identity:
            raise LaunchAnchorError(f"The {description} changed while opened")
        content = os.pread(descriptor, maximum_size + 1, 0)
        final_information = path.lstat()
        if (
            _file_identity(final_information) != expected_identity
            or len(content) != expected_size
            or hashlib.sha256(content).hexdigest() != expected_sha256
        ):
            raise LaunchAnchorError(f"The pinned {description} changed")
        return descriptor, expected_identity, content
    except BaseException:
        os.close(descriptor)
        raise


def _revalidate_repository_input(
    path: Path,
    descriptor: int,
    identity: FileIdentity,
    content: bytes,
    description: str,
) -> None:
    try:
        descriptor_information = os.fstat(descriptor)
        path_information = path.lstat()
        observed = os.pread(descriptor, len(content) + 1, 0)
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot revalidate {description}: {_bounded_detail(exception)}"
        ) from exception
    if (
        _file_identity(descriptor_information) != identity
        or _file_identity(path_information) != identity
        or observed != content
    ):
        raise LaunchAnchorError(f"The {description} identity changed at spawn")


def _publish_pinned_runtime_input(
    runtime_directory: Path,
    runtime_descriptor: int,
    file_name: str,
    content: bytes,
) -> PinnedRuntimeInput:
    staged_path = runtime_directory / file_name
    temporary_name = f".{file_name}.{secrets.token_hex(16)}.tmp"
    descriptor = -1
    keep_descriptor = False
    try:
        descriptor = os.open(
            temporary_name,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
            0o600,
            dir_fd=runtime_descriptor,
        )
        view = memoryview(content)
        while view:
            written = os.write(descriptor, view)
            if written <= 0:
                raise LaunchAnchorError("Pinned runtime input write made no progress")
            view = view[written:]
        os.fchmod(descriptor, 0o400)
        os.fsync(descriptor)
        os.close(descriptor)
        descriptor = -1
        os.link(
            temporary_name,
            file_name,
            src_dir_fd=runtime_descriptor,
            dst_dir_fd=runtime_descriptor,
            follow_symlinks=False,
        )
        os.unlink(temporary_name, dir_fd=runtime_descriptor)
        os.fsync(runtime_descriptor)
        descriptor = os.open(
            file_name,
            os.O_RDONLY
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
            dir_fd=runtime_descriptor,
        )
        information = os.fstat(descriptor)
        identity = _file_identity(information)
        path_information = os.stat(
            file_name,
            dir_fd=runtime_descriptor,
            follow_symlinks=False,
        )
        if (
            not stat.S_ISREG(information.st_mode)
            or identity.owner_user_id != os.getuid()
            or identity.mode != 0o400
            or identity.link_count != 1
            or identity.size != len(content)
            or _file_identity(path_information) != identity
            or os.pread(descriptor, len(content) + 1, 0) != content
        ):
            raise LaunchAnchorError(f"The staged runtime input is unsafe: {file_name}")
        keep_descriptor = True
        return PinnedRuntimeInput(
            file_name=file_name,
            path=staged_path,
            descriptor=descriptor,
            identity=identity,
            content=content,
            sha256=hashlib.sha256(content).hexdigest(),
        )
    except FileExistsError as exception:
        raise LaunchAnchorError(
            f"Refusing to replace staged runtime input: {file_name}"
        ) from exception
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot stage runtime input {file_name}: {_bounded_detail(exception)}"
        ) from exception
    finally:
        if descriptor >= 0 and not keep_descriptor:
            os.close(descriptor)
        try:
            os.unlink(temporary_name, dir_fd=runtime_descriptor)
        except OSError:
            pass


def _revalidate_pinned_runtime_input(
    runtime_descriptor: int,
    pinned: PinnedRuntimeInput,
) -> None:
    try:
        if pinned.descriptor < 3:
            raise LaunchAnchorError(
                f"Pinned runtime input descriptor is closed: {pinned.file_name}"
            )
        descriptor_information = os.fstat(pinned.descriptor)
        path_information = os.stat(
            pinned.file_name,
            dir_fd=runtime_descriptor,
            follow_symlinks=False,
        )
        observed = os.pread(pinned.descriptor, len(pinned.content) + 1, 0)
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot revalidate staged runtime input {pinned.file_name}: "
            f"{_bounded_detail(exception)}"
        ) from exception
    if (
        _file_identity(descriptor_information) != pinned.identity
        or _file_identity(path_information) != pinned.identity
        or observed != pinned.content
        or hashlib.sha256(observed).hexdigest() != pinned.sha256
    ):
        raise LaunchAnchorError(
            f"The staged runtime input changed: {pinned.file_name}"
        )


def _revalidate_java_executable(handle: LaunchAnchorHandle) -> None:
    if handle.java_root_owned:
        _validate_root_owned_parent_chain(handle.java_path)
    observed_identity = _validate_protected_file(
        handle.java_path,
        "JDK 21 java executable",
        executable=True,
        required_owner_user_id=0 if handle.java_root_owned else None,
    )
    if observed_identity != handle.java_identity:
        raise LaunchAnchorError("The pinned JDK 21 executable identity changed")


def _revalidate_launch_inputs(handle: LaunchAnchorHandle) -> None:
    _validate_runtime(handle)
    _revalidate_java_executable(handle)
    _revalidate_pinned_runtime_input(
        handle.runtime_directory_descriptor,
        handle.staged_source,
    )
    _revalidate_pinned_runtime_input(
        handle.runtime_directory_descriptor,
        handle.staged_wrapper_jar,
    )
    _revalidate_pinned_runtime_input(
        handle.runtime_directory_descriptor,
        handle.staged_wrapper_properties,
    )


def _pin_source(
    source_path: Path,
    expected_size: int,
    expected_sha256: str,
) -> tuple[int, FileIdentity, bytes]:
    if source_path.name != STAGED_SOURCE_FILE_NAME:
        raise LaunchAnchorError("The launch-anchor source file name is not exact")
    return _pin_repository_input(
        source_path,
        expected_size,
        expected_sha256,
        MAXIMUM_SOURCE_SIZE_BYTES,
        "launch-anchor source",
    )


def _pin_wrapper_jar(
    path: Path,
    expected_size: int,
    expected_sha256: str,
) -> tuple[int, FileIdentity, bytes]:
    if path.name != STAGED_WRAPPER_JAR_FILE_NAME:
        raise LaunchAnchorError("The Gradle wrapper JAR name is not exact")
    return _pin_repository_input(
        path,
        expected_size,
        expected_sha256,
        MAXIMUM_WRAPPER_JAR_SIZE_BYTES,
        "Gradle wrapper JAR",
    )


def _pin_wrapper_properties(
    path: Path,
    expected_size: int,
    expected_sha256: str,
) -> tuple[int, FileIdentity, bytes]:
    if path.name != STAGED_WRAPPER_PROPERTIES_FILE_NAME:
        raise LaunchAnchorError("The Gradle wrapper properties name is not exact")
    return _pin_repository_input(
        path,
        expected_size,
        expected_sha256,
        MAXIMUM_WRAPPER_PROPERTIES_SIZE_BYTES,
        "Gradle wrapper properties",
    )


def _validate_environment(environment: Mapping[str, str] | None) -> dict[str, str] | None:
    if environment is None:
        return None
    if not isinstance(environment, Mapping):
        raise LaunchAnchorError("Launch-anchor environment must be a mapping")
    validated: dict[str, str] = {}
    for key, value in environment.items():
        if (
            not isinstance(key, str)
            or not isinstance(value, str)
            or not key
            or "=" in key
            or "\0" in key
            or "\0" in value
        ):
            raise LaunchAnchorError("Launch-anchor environment is invalid")
        validated[key] = value
    return validated


def _validate_gradle_arguments(arguments: Sequence[str]) -> tuple[str, ...]:
    if isinstance(arguments, (str, bytes)) or not isinstance(arguments, Sequence):
        raise LaunchAnchorError("Gradle arguments must be one sequence of strings")
    result = tuple(arguments)
    if (
        len(result) < len(REQUIRED_GRADLE_ARGUMENT_PREFIX) + 1
        or len(result) > MAXIMUM_ARGUMENT_COUNT
        or result[: len(REQUIRED_GRADLE_ARGUMENT_PREFIX)]
        != REQUIRED_GRADLE_ARGUMENT_PREFIX
    ):
        raise LaunchAnchorError("Gradle safety arguments are missing or reordered")
    total_size = 0
    for argument in result:
        if not isinstance(argument, str) or not argument or "\0" in argument:
            raise LaunchAnchorError("Gradle contains one invalid argument")
        encoded_size = len(argument.encode("utf-8"))
        if encoded_size > MAXIMUM_ARGUMENT_SIZE_BYTES:
            raise LaunchAnchorError("One Gradle argument exceeded its byte bound")
        total_size += encoded_size
    if total_size > MAXIMUM_ARGUMENTS_SIZE_BYTES:
        raise LaunchAnchorError("Gradle arguments exceeded their aggregate byte bound")
    if any(argument.startswith("-P") for argument in result):
        raise LaunchAnchorError("Gradle project-property overrides are forbidden")
    return result


def build_direct_gradle_wrapper_command(
    java_path: Path,
    gradle_wrapper_jar_path: Path,
    gradle_arguments: Sequence[str],
) -> tuple[str, ...]:
    """Builds the only authorized child shape: an immediate JDK 21 JVM."""

    validated_gradle_arguments = _validate_gradle_arguments(gradle_arguments)
    if not java_path.is_absolute() or not gradle_wrapper_jar_path.is_absolute():
        raise LaunchAnchorError("Direct Gradle launcher paths must be absolute")
    command = (
        str(java_path),
        *GRADLE_WRAPPER_JVM_ARGUMENTS,
        GRADLE_WRAPPER_APPLICATION_ARGUMENT,
        "-classpath",
        str(gradle_wrapper_jar_path),
        GRADLE_WRAPPER_MAIN_CLASS,
        *validated_gradle_arguments,
    )
    _validate_direct_gradle_wrapper_command(
        command,
        java_path,
        gradle_wrapper_jar_path,
        validated_gradle_arguments,
    )
    return command


def _validate_direct_gradle_wrapper_command(
    command: Sequence[str],
    java_path: Path,
    gradle_wrapper_jar_path: Path,
    gradle_arguments: tuple[str, ...],
) -> None:
    expected = (
        str(java_path),
        *GRADLE_WRAPPER_JVM_ARGUMENTS,
        GRADLE_WRAPPER_APPLICATION_ARGUMENT,
        "-classpath",
        str(gradle_wrapper_jar_path),
        GRADLE_WRAPPER_MAIN_CLASS,
        *gradle_arguments,
    )
    if tuple(command) != expected or command[0] != str(java_path):
        raise LaunchAnchorError(
            "Launch anchor must release the exact direct JDK Gradle-wrapper command"
        )


def arguments_sha256(arguments: Sequence[str]) -> str:
    """Hashes exact argv boundaries without depending on a shell encoding."""

    digest = hashlib.sha256()
    for argument in arguments:
        if not isinstance(argument, str):
            raise LaunchAnchorError("Cannot hash one non-string process argument")
        encoded = argument.encode("utf-8")
        digest.update(len(encoded).to_bytes(8, byteorder="big", signed=False))
        digest.update(encoded)
    return digest.hexdigest()


def _json_bytes(payload: Mapping[str, object]) -> bytes:
    content = (
        json.dumps(payload, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")
    if not 0 < len(content) <= MAXIMUM_ARTIFACT_SIZE_BYTES:
        raise LaunchAnchorError("Launch-anchor artifact exceeded its byte bound")
    return content


def _artifact_exists(handle: LaunchAnchorHandle, file_name: str) -> bool:
    _validate_runtime(handle)
    try:
        os.stat(
            file_name,
            dir_fd=handle.runtime_directory_descriptor,
            follow_symlinks=False,
        )
        return True
    except FileNotFoundError:
        return False
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot inspect launch-anchor artifact {file_name}: "
            f"{_bounded_detail(exception)}"
        ) from exception


def _validate_artifact_information(
    information: os.stat_result,
    file_name: str,
) -> FileIdentity:
    identity = _file_identity(information)
    if (
        not stat.S_ISREG(information.st_mode)
        or stat.S_ISLNK(information.st_mode)
        or identity.owner_user_id != os.getuid()
        or identity.mode != 0o600
        or not 0 < identity.size <= MAXIMUM_ARTIFACT_SIZE_BYTES
    ):
        raise LaunchAnchorError(f"Unsafe launch-anchor artifact: {file_name}")
    if identity.link_count == 2:
        raise _ArtifactPublicationPending(
            f"Launch-anchor artifact publication is incomplete: {file_name}"
        )
    if identity.link_count != 1:
        raise LaunchAnchorError(f"Unsafe launch-anchor artifact: {file_name}")
    return identity


def _read_artifact(
    handle: LaunchAnchorHandle,
    file_name: str,
) -> tuple[dict[str, object], PinnedArtifact]:
    _validate_runtime(handle)
    descriptor = -1
    try:
        descriptor = os.open(
            file_name,
            os.O_RDONLY
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
            dir_fd=handle.runtime_directory_descriptor,
        )
        descriptor_identity = _validate_artifact_information(
            os.fstat(descriptor),
            file_name,
        )
        path_identity = _validate_artifact_information(
            os.stat(
                file_name,
                dir_fd=handle.runtime_directory_descriptor,
                follow_symlinks=False,
            ),
            file_name,
        )
        if path_identity != descriptor_identity:
            raise LaunchAnchorError(
                f"Launch-anchor artifact changed while opened: {file_name}"
            )
        content = os.pread(descriptor, MAXIMUM_ARTIFACT_SIZE_BYTES + 1, 0)
        final_identity = _validate_artifact_information(
            os.stat(
                file_name,
                dir_fd=handle.runtime_directory_descriptor,
                follow_symlinks=False,
            ),
            file_name,
        )
        if (
            final_identity != descriptor_identity
            or len(content) != descriptor_identity.size
        ):
            raise LaunchAnchorError(
                f"Launch-anchor artifact changed while read: {file_name}"
            )
        try:
            payload = json.loads(content.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exception:
            raise LaunchAnchorError(
                f"Cannot parse launch-anchor artifact {file_name}: "
                f"{_bounded_detail(exception)}"
            ) from exception
        if not isinstance(payload, dict) or content != _json_bytes(payload):
            raise LaunchAnchorError(
                f"Launch-anchor artifact is not canonical: {file_name}"
            )
        pinned = PinnedArtifact(
            file_name=file_name,
            identity=descriptor_identity,
            content=content,
            sha256=hashlib.sha256(content).hexdigest(),
        )
        previous = handle.pinned_artifacts.get(file_name)
        if previous is not None and previous != pinned:
            raise LaunchAnchorError(
                f"Pinned launch-anchor artifact was replaced: {file_name}"
            )
        handle.pinned_artifacts[file_name] = pinned
        return payload, pinned
    except FileNotFoundError:
        raise
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot read launch-anchor artifact {file_name}: "
            f"{_bounded_detail(exception)}"
        ) from exception
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def _poll_artifact(
    handle: LaunchAnchorHandle,
    file_name: str,
) -> dict[str, object] | None:
    if not _artifact_exists(handle, file_name):
        return None
    try:
        payload, _pinned = _read_artifact(handle, file_name)
    except _ArtifactPublicationPending:
        return None
    return payload


def _write_exclusive_atomic(
    handle: LaunchAnchorHandle,
    file_name: str,
    payload: Mapping[str, object],
) -> PinnedArtifact:
    _validate_runtime(handle)
    if _artifact_exists(handle, file_name):
        raise LaunchAnchorError(
            f"Refusing to replace launch-anchor artifact: {file_name}"
        )
    content = _json_bytes(payload)
    temporary_name = f".{file_name}.{secrets.token_hex(16)}.tmp"
    descriptor = -1
    linked = False
    try:
        descriptor = os.open(
            temporary_name,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
            0o600,
            dir_fd=handle.runtime_directory_descriptor,
        )
        information = os.fstat(descriptor)
        if (
            not stat.S_ISREG(information.st_mode)
            or information.st_uid != os.getuid()
            or stat.S_IMODE(information.st_mode) != 0o600
            or information.st_nlink != 1
        ):
            raise LaunchAnchorError("Unsafe launch-anchor temporary artifact")
        view = memoryview(content)
        while view:
            written = os.write(descriptor, view)
            if written <= 0:
                raise LaunchAnchorError("Launch-anchor artifact write made no progress")
            view = view[written:]
        os.fsync(descriptor)
        os.close(descriptor)
        descriptor = -1
        _validate_runtime(handle)
        os.link(
            temporary_name,
            file_name,
            src_dir_fd=handle.runtime_directory_descriptor,
            dst_dir_fd=handle.runtime_directory_descriptor,
            follow_symlinks=False,
        )
        linked = True
        os.unlink(temporary_name, dir_fd=handle.runtime_directory_descriptor)
        os.fsync(handle.runtime_directory_descriptor)
        payload_read, pinned = _read_artifact(handle, file_name)
        if payload_read != dict(payload) or pinned.content != content:
            raise LaunchAnchorError(
                f"Launch-anchor artifact changed during publication: {file_name}"
            )
        return pinned
    except FileExistsError as exception:
        raise LaunchAnchorError(
            f"Refusing to replace launch-anchor artifact: {file_name}"
        ) from exception
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot publish launch-anchor artifact {file_name}: "
            f"{_bounded_detail(exception)}"
        ) from exception
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        try:
            os.unlink(temporary_name, dir_fd=handle.runtime_directory_descriptor)
        except OSError:
            pass
        if linked:
            _validate_runtime(handle)


def _require_exact_fields(
    payload: Mapping[str, object],
    expected_fields: frozenset[str],
    description: str,
) -> None:
    if set(payload) != expected_fields:
        raise LaunchAnchorError(f"{description} fields are not exact")


def _validate_readiness(handle: LaunchAnchorHandle) -> None:
    payload, _pinned = _read_artifact(handle, READINESS_FILE_NAME)
    _require_exact_fields(
        payload,
        frozenset(
            {
                "argv_sha256",
                "controller_pid",
                "java_feature",
                "pid",
                "pre_start_timeout_seconds",
                "runtime_device",
                "runtime_inode",
                "schema",
                "token",
            }
        ),
        "Launch-anchor readiness",
    )
    if (
        payload.get("schema") != READINESS_SCHEMA
        or payload.get("token") != handle.token
        or payload.get("argv_sha256") != handle.arguments_sha256
        or type(payload.get("controller_pid")) is not int
        or payload.get("controller_pid") != handle.controller_process_id
        or payload.get("java_feature") != EXPECTED_JAVA_FEATURE
        or type(payload.get("pid")) is not int
        or payload.get("pid") != handle.anchor_pid
        or type(payload.get("pre_start_timeout_seconds")) is not int
        or payload.get("pre_start_timeout_seconds") != PRE_START_TIMEOUT_SECONDS
        or type(payload.get("runtime_device")) is not int
        or payload.get("runtime_device") != handle.runtime_identity.device
        or type(payload.get("runtime_inode")) is not int
        or payload.get("runtime_inode") != handle.runtime_identity.inode
    ):
        raise LaunchAnchorError("Launch-anchor readiness is not authenticated")


def _validate_anchor_topology(
    handle: LaunchAnchorHandle,
    *,
    get_process_group_id: Callable[[int], int] = os.getpgid,
    get_session_id: Callable[[int], int] = os.getsid,
) -> None:
    if type(handle.anchor_pid) is not int or handle.anchor_pid <= 1:
        raise LaunchAnchorError("Launch-anchor process id is invalid")
    try:
        process_group_id = get_process_group_id(handle.anchor_pid)
        session_id = get_session_id(handle.anchor_pid)
    except OSError as exception:
        raise LaunchAnchorError(
            f"Cannot inspect launch-anchor topology: {_bounded_detail(exception)}"
        ) from exception
    if process_group_id != handle.anchor_pid or session_id != handle.anchor_pid:
        raise LaunchAnchorError(
            "Launch anchor is not its own process-group and session leader"
        )


def _anchor_command(
    java_path: Path,
    java_identity: FileIdentity,
    source_path: Path,
    runtime_directory: Path,
    runtime_identity: FileIdentity,
    staged_wrapper_jar: PinnedRuntimeInput,
    staged_wrapper_properties: PinnedRuntimeInput,
    token: str,
    arguments_digest: str,
    controller_process_id: int,
    child_command: tuple[str, ...],
) -> list[str]:
    return [
        str(java_path),
        *ANCHOR_JVM_ARGUMENTS,
        str(source_path),
        "--runtime-directory",
        str(runtime_directory),
        "--runtime-device",
        str(runtime_identity.device),
        "--runtime-inode",
        str(runtime_identity.inode),
        "--runtime-owner-uid",
        str(runtime_identity.owner_user_id),
        "--token",
        token,
        "--argv-sha256",
        arguments_digest,
        "--child-java-device",
        str(java_identity.device),
        "--child-java-inode",
        str(java_identity.inode),
        "--child-java-owner-uid",
        str(java_identity.owner_user_id),
        "--child-java-mode",
        str(java_identity.mode),
        "--wrapper-jar-device",
        str(staged_wrapper_jar.identity.device),
        "--wrapper-jar-inode",
        str(staged_wrapper_jar.identity.inode),
        "--wrapper-jar-owner-uid",
        str(staged_wrapper_jar.identity.owner_user_id),
        "--wrapper-jar-mode",
        str(staged_wrapper_jar.identity.mode),
        "--wrapper-jar-size",
        str(staged_wrapper_jar.identity.size),
        "--wrapper-jar-sha256",
        staged_wrapper_jar.sha256,
        "--wrapper-properties-device",
        str(staged_wrapper_properties.identity.device),
        "--wrapper-properties-inode",
        str(staged_wrapper_properties.identity.inode),
        "--wrapper-properties-owner-uid",
        str(staged_wrapper_properties.identity.owner_user_id),
        "--wrapper-properties-mode",
        str(staged_wrapper_properties.identity.mode),
        "--wrapper-properties-size",
        str(staged_wrapper_properties.identity.size),
        "--wrapper-properties-sha256",
        staged_wrapper_properties.sha256,
        "--controller-pid",
        str(controller_process_id),
        "--",
        *child_command,
    ]


def start_launch_anchor(
    java_path: Path,
    java_feature: int,
    source_path: Path,
    expected_source_size: int,
    expected_source_sha256: str,
    runtime_directory: Path,
    working_directory: Path,
    gradle_wrapper_jar_path: Path,
    expected_gradle_wrapper_jar_size: int,
    expected_gradle_wrapper_jar_sha256: str,
    gradle_wrapper_properties_path: Path,
    expected_gradle_wrapper_properties_size: int,
    expected_gradle_wrapper_properties_sha256: str,
    gradle_arguments: Sequence[str],
    child_release_guard: Callable[[LaunchAnchorHandle], None],
    *,
    environment: Mapping[str, str] | None = None,
    stdin: object = subprocess.DEVNULL,
    stdout: object = None,
    stderr: object = None,
    readiness_timeout_seconds: float = DEFAULT_READINESS_TIMEOUT_SECONDS,
    popen_factory: Callable[..., subprocess.Popen[bytes]] = subprocess.Popen,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
    get_process_group_id: Callable[[int], int] = os.getpgid,
    get_session_id: Callable[[int], int] = os.getsid,
    token_factory: Callable[[int], str] = secrets.token_hex,
    allow_unprivileged_java_for_tests: bool = False,
) -> LaunchAnchorHandle:
    """Starts one bounded anchor and waits for authenticated JDK 21 readiness."""

    if type(java_feature) is not int or java_feature != EXPECTED_JAVA_FEATURE:
        raise LaunchAnchorError("Launch anchor requires an exact JDK 21 selection")
    _require_positive_timeout(readiness_timeout_seconds, "readiness timeout")
    java_path = Path(java_path)
    source_path = Path(source_path)
    runtime_directory = Path(runtime_directory)
    working_directory = Path(working_directory)
    gradle_wrapper_jar_path = Path(gradle_wrapper_jar_path)
    gradle_wrapper_properties_path = Path(gradle_wrapper_properties_path)
    if java_path.name != "java":
        raise LaunchAnchorError("The selected JDK executable name is not exact")
    if type(allow_unprivileged_java_for_tests) is not bool:
        raise LaunchAnchorError(
            "allow_unprivileged_java_for_tests must be a boolean"
        )
    require_root_owned_java = not allow_unprivileged_java_for_tests
    if require_root_owned_java:
        java_identity = validate_root_owned_java_executable(java_path)
    else:
        java_identity = _validate_protected_file(
            java_path,
            "JDK 21 java executable",
            executable=True,
        )
    _validate_working_directory(working_directory)
    validated_gradle_arguments = _validate_gradle_arguments(gradle_arguments)
    if not callable(child_release_guard):
        raise LaunchAnchorError("Launch-anchor child release guard is required")
    validated_environment = _validate_environment(environment)
    wrapper_descriptor, wrapper_identity, wrapper_content = _pin_wrapper_jar(
        gradle_wrapper_jar_path,
        expected_gradle_wrapper_jar_size,
        expected_gradle_wrapper_jar_sha256,
    )
    try:
        (
            properties_descriptor,
            properties_identity,
            properties_content,
        ) = _pin_wrapper_properties(
            gradle_wrapper_properties_path,
            expected_gradle_wrapper_properties_size,
            expected_gradle_wrapper_properties_sha256,
        )
    except BaseException:
        os.close(wrapper_descriptor)
        raise
    try:
        source_descriptor, source_identity, source_content = _pin_source(
            source_path,
            expected_source_size,
            expected_source_sha256,
        )
    except BaseException:
        os.close(wrapper_descriptor)
        os.close(properties_descriptor)
        raise
    runtime_descriptor = -1
    staged_source: PinnedRuntimeInput | None = None
    staged_wrapper_jar: PinnedRuntimeInput | None = None
    staged_wrapper_properties: PinnedRuntimeInput | None = None
    handle: LaunchAnchorHandle | None = None
    try:
        runtime_descriptor, runtime_identity = _open_runtime_directory(
            runtime_directory
        )
        token = token_factory(32)
        _require_lower_sha256(token, "launch-anchor token")
        for file_name in ARTIFACT_FILE_NAMES:
            try:
                os.stat(
                    file_name,
                    dir_fd=runtime_descriptor,
                    follow_symlinks=False,
                )
            except FileNotFoundError:
                continue
            except OSError as exception:
                raise LaunchAnchorError(
                    f"Cannot inspect launch-anchor artifact: {_bounded_detail(exception)}"
                ) from exception
            raise LaunchAnchorError(
                f"Refusing a pre-existing launch-anchor artifact: {file_name}"
            )
        for staged_file_name in (
            STAGED_SOURCE_FILE_NAME,
            STAGED_WRAPPER_JAR_FILE_NAME,
            STAGED_WRAPPER_PROPERTIES_FILE_NAME,
        ):
            if os.path.lexists(runtime_directory / staged_file_name):
                raise LaunchAnchorError(
                    f"Refusing pre-existing staged runtime input: {staged_file_name}"
                )
        staged_source = _publish_pinned_runtime_input(
            runtime_directory,
            runtime_descriptor,
            STAGED_SOURCE_FILE_NAME,
            source_content,
        )
        staged_wrapper_jar = _publish_pinned_runtime_input(
            runtime_directory,
            runtime_descriptor,
            STAGED_WRAPPER_JAR_FILE_NAME,
            wrapper_content,
        )
        staged_wrapper_properties = _publish_pinned_runtime_input(
            runtime_directory,
            runtime_descriptor,
            STAGED_WRAPPER_PROPERTIES_FILE_NAME,
            properties_content,
        )
        child_command = build_direct_gradle_wrapper_command(
            java_path,
            staged_wrapper_jar.path,
            validated_gradle_arguments,
        )
        arguments_digest = arguments_sha256(child_command)
        controller_process_id = os.getpid()
        if type(controller_process_id) is not int or controller_process_id <= 1:
            raise LaunchAnchorError("The launch controller process id is invalid")
        process = popen_factory(
            _anchor_command(
                java_path,
                java_identity,
                staged_source.path,
                runtime_directory,
                runtime_identity,
                staged_wrapper_jar,
                staged_wrapper_properties,
                token,
                arguments_digest,
                controller_process_id,
                child_command,
            ),
            stdin=stdin,
            stdout=stdout,
            stderr=stderr,
            cwd=str(working_directory),
            env=validated_environment,
            close_fds=True,
            start_new_session=True,
        )
        handle = LaunchAnchorHandle(
            process,
            runtime_directory,
            runtime_descriptor,
            runtime_identity,
            token,
            controller_process_id,
            java_path,
            java_identity,
            require_root_owned_java,
            source_path,
            staged_source,
            gradle_wrapper_jar_path,
            staged_wrapper_jar,
            gradle_wrapper_properties_path,
            staged_wrapper_properties,
            validated_gradle_arguments,
            child_command,
            arguments_digest,
            child_release_guard,
        )
        runtime_descriptor = -1
        _revalidate_repository_input(
            source_path,
            source_descriptor,
            source_identity,
            source_content,
            "launch-anchor source",
        )
        _revalidate_repository_input(
            gradle_wrapper_jar_path,
            wrapper_descriptor,
            wrapper_identity,
            wrapper_content,
            "Gradle wrapper JAR",
        )
        _revalidate_repository_input(
            gradle_wrapper_properties_path,
            properties_descriptor,
            properties_identity,
            properties_content,
            "Gradle wrapper properties",
        )
        _revalidate_pinned_runtime_input(
            handle.runtime_directory_descriptor,
            handle.staged_source,
        )
        _revalidate_pinned_runtime_input(
            handle.runtime_directory_descriptor,
            handle.staged_wrapper_jar,
        )
        _revalidate_pinned_runtime_input(
            handle.runtime_directory_descriptor,
            handle.staged_wrapper_properties,
        )
        _revalidate_java_executable(handle)
        deadline = monotonic() + readiness_timeout_seconds
        while True:
            if _artifact_exists(handle, READINESS_FILE_NAME):
                try:
                    _validate_readiness(handle)
                except _ArtifactPublicationPending:
                    pass
                else:
                    if process.poll() is not None:
                        raise LaunchAnchorError(
                            "Launch anchor exited after publishing readiness"
                        )
                    _validate_anchor_topology(
                        handle,
                        get_process_group_id=get_process_group_id,
                        get_session_id=get_session_id,
                    )
                    if process.poll() is not None:
                        raise LaunchAnchorError(
                            "Launch anchor exited during readiness validation"
                        )
                    return handle
            if process.poll() is not None:
                raise LaunchAnchorError("Launch anchor exited before readiness")
            if monotonic() >= deadline:
                raise LaunchAnchorError("Launch-anchor readiness timed out")
            sleep(POLL_INTERVAL_SECONDS)
    except BaseException as exception:
        if handle is not None:
            raise LaunchAnchorStartError(
                f"Launch anchor failed before readiness: {_bounded_detail(exception)}",
                handle,
            ) from exception
        if runtime_descriptor >= 0:
            os.close(runtime_descriptor)
        raise
    finally:
        if handle is None:
            for staged_input in (
                staged_source,
                staged_wrapper_jar,
                staged_wrapper_properties,
            ):
                if staged_input is not None and staged_input.descriptor >= 0:
                    os.close(staged_input.descriptor)
                    staged_input.descriptor = -1
        os.close(source_descriptor)
        os.close(wrapper_descriptor)
        os.close(properties_descriptor)


def start_launch_anchor_child(handle: LaunchAnchorHandle) -> None:
    """Publishes the only token that can release the direct Gradle JVM."""

    _revalidate_launch_inputs(handle)
    handle.child_release_guard(handle)
    _validate_runtime(handle)
    _validate_readiness(handle)
    if handle.process.poll() is not None:
        raise LaunchAnchorError("Launch anchor exited before child release")
    if START_FILE_NAME in handle.pinned_artifacts:
        raise LaunchAnchorError("The launch-anchor child was already released")
    for file_name in (
        START_FILE_NAME,
        CHILD_STARTED_FILE_NAME,
        CHILD_RESULT_FILE_NAME,
        FINISH_FILE_NAME,
    ):
        if _artifact_exists(handle, file_name):
            raise LaunchAnchorError(
                f"Unexpected launch-anchor artifact before release: {file_name}"
            )
    handle.child_release_guard(handle)
    _revalidate_launch_inputs(handle)
    _validate_readiness(handle)
    if handle.process.poll() is not None:
        raise LaunchAnchorError("Launch anchor exited before child release")
    for file_name in (
        START_FILE_NAME,
        CHILD_STARTED_FILE_NAME,
        CHILD_RESULT_FILE_NAME,
        FINISH_FILE_NAME,
    ):
        if _artifact_exists(handle, file_name):
            raise LaunchAnchorError(
                f"Unexpected launch-anchor artifact at release: {file_name}"
            )
    _write_exclusive_atomic(
        handle,
        START_FILE_NAME,
        {
            "argv_sha256": handle.arguments_sha256,
            "schema": START_SCHEMA,
            "token": handle.token,
        },
    )
    handle.child_release_guard(handle)
    _revalidate_launch_inputs(handle)


def _require_child_released(handle: LaunchAnchorHandle) -> None:
    if START_FILE_NAME not in handle.pinned_artifacts:
        raise LaunchAnchorError("The launch-anchor child has not been released")
    _read_artifact(handle, START_FILE_NAME)


def poll_launch_anchor_child_started(
    handle: LaunchAnchorHandle,
) -> ChildStarted | None:
    """Returns the token-bound direct-child identity when available."""

    _require_child_released(handle)
    payload = _poll_artifact(handle, CHILD_STARTED_FILE_NAME)
    if payload is None:
        result = _poll_artifact(handle, CHILD_RESULT_FILE_NAME)
        if result is not None:
            validated_result = _validate_child_result(handle, result)
            if not validated_result.started:
                raise LaunchAnchorChildStartError(
                    "The launch anchor could not start the direct Gradle JVM"
                )
            raise LaunchAnchorError(
                "Child result appeared without its child-started identity"
            )
        return None
    _require_exact_fields(
        payload,
        frozenset({"argv_sha256", "executable", "pid", "schema", "token"}),
        "Launch-anchor child-started artifact",
    )
    process_id = payload.get("pid")
    if (
        payload.get("schema") != CHILD_STARTED_SCHEMA
        or payload.get("token") != handle.token
        or payload.get("argv_sha256") != handle.arguments_sha256
        or payload.get("executable") != str(handle.java_path)
        or type(process_id) is not int
        or process_id <= 1
        or process_id == handle.anchor_pid
    ):
        raise LaunchAnchorError("Launch-anchor child identity is not authenticated")
    return ChildStarted(
        pid=process_id,
        executable=str(handle.java_path),
        arguments_sha256=handle.arguments_sha256,
    )


def _validate_child_result(
    handle: LaunchAnchorHandle,
    payload: Mapping[str, object],
) -> ChildResult:
    _require_exact_fields(
        payload,
        frozenset(
            {"argv_sha256", "exit_code", "pid", "schema", "started", "token"}
        ),
        "Launch-anchor child-result artifact",
    )
    started = payload.get("started")
    process_id = payload.get("pid")
    exit_code = payload.get("exit_code")
    if (
        payload.get("schema") != CHILD_RESULT_SCHEMA
        or payload.get("token") != handle.token
        or payload.get("argv_sha256") != handle.arguments_sha256
        or type(started) is not bool
        or type(exit_code) is not int
        or not -(1 << 31) <= exit_code < (1 << 31)
        or (
            started
            and (
                type(process_id) is not int
                or process_id <= 1
                or process_id == handle.anchor_pid
            )
        )
        or (not started and process_id is not None)
        or (not started and exit_code != 127)
    ):
        raise LaunchAnchorError("Launch-anchor child result is not authenticated")
    if started:
        if not _artifact_exists(handle, CHILD_STARTED_FILE_NAME):
            raise LaunchAnchorError(
                "Child result appeared without its child-started identity"
            )
        child_started = poll_launch_anchor_child_started(handle)
        if child_started is None or child_started.pid != process_id:
            raise LaunchAnchorError(
                "Launch-anchor child result does not match its child identity"
            )
    elif _artifact_exists(handle, CHILD_STARTED_FILE_NAME):
        raise LaunchAnchorError(
            "A failed child start cannot have a child-started artifact"
        )
    return ChildResult(
        started=started,
        pid=process_id,
        exit_code=exit_code,
        arguments_sha256=handle.arguments_sha256,
    )


def poll_launch_anchor_child_result(
    handle: LaunchAnchorHandle,
) -> ChildResult | None:
    """Returns the terminal child result without allowing the anchor to exit."""

    _require_child_released(handle)
    payload = _poll_artifact(handle, CHILD_RESULT_FILE_NAME)
    if payload is None:
        return None
    result = _validate_child_result(handle, payload)
    if handle.process.poll() is not None:
        raise LaunchAnchorError("Launch anchor exited before authenticated finish")
    return result


def _wait_for_value(
    handle: LaunchAnchorHandle,
    poll: Callable[[], ChildStarted | ChildResult | None],
    description: str,
    timeout_seconds: float,
    monotonic: Callable[[], float],
    sleep: Callable[[float], None],
) -> ChildStarted | ChildResult:
    _require_positive_timeout(timeout_seconds, description)
    deadline = monotonic() + timeout_seconds
    while True:
        value = poll()
        if value is not None:
            return value
        if handle.process.poll() is not None:
            raise LaunchAnchorError(
                f"Launch anchor exited before {description}"
            )
        if monotonic() >= deadline:
            raise LaunchAnchorError(f"Timed out waiting for {description}")
        sleep(POLL_INTERVAL_SECONDS)


def wait_for_launch_anchor_child_started(
    handle: LaunchAnchorHandle,
    *,
    timeout_seconds: float = DEFAULT_READINESS_TIMEOUT_SECONDS,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> ChildStarted:
    """Waits a bounded interval for the direct child identity."""

    value = _wait_for_value(
        handle,
        lambda: poll_launch_anchor_child_started(handle),
        "launch-anchor child identity",
        timeout_seconds,
        monotonic,
        sleep,
    )
    if not isinstance(value, ChildStarted):
        raise LaunchAnchorError("Launch-anchor child identity type changed")
    return value


def wait_for_launch_anchor_child_result(
    handle: LaunchAnchorHandle,
    *,
    timeout_seconds: float = DEFAULT_RESULT_TIMEOUT_SECONDS,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> ChildResult:
    """Waits a bounded interval for child completion while the anchor stays alive."""

    value = _wait_for_value(
        handle,
        lambda: poll_launch_anchor_child_result(handle),
        "launch-anchor child result",
        timeout_seconds,
        monotonic,
        sleep,
    )
    if not isinstance(value, ChildResult):
        raise LaunchAnchorError("Launch-anchor child result type changed")
    return value


def finish_launch_anchor(
    handle: LaunchAnchorHandle,
    *,
    timeout_seconds: float = DEFAULT_FINISH_TIMEOUT_SECONDS,
) -> int:
    """Allows a proven child-complete anchor to exit normally and reaps it."""

    _require_positive_timeout(timeout_seconds, "launch-anchor finish timeout")
    _validate_runtime(handle)
    _require_child_released(handle)
    result_payload = _poll_artifact(handle, CHILD_RESULT_FILE_NAME)
    if result_payload is None:
        raise LaunchAnchorError(
            "Cannot finish the launch anchor before its child result"
        )
    result = _validate_child_result(handle, result_payload)
    finish_payload = {
        "argv_sha256": handle.arguments_sha256,
        "child_exit_code": result.exit_code,
        "child_pid": result.pid,
        "child_started": result.started,
        "schema": FINISH_SCHEMA,
        "token": handle.token,
    }
    existing_finish = _poll_artifact(handle, FINISH_FILE_NAME)
    if existing_finish is None:
        if handle.process.poll() is not None:
            raise LaunchAnchorError("Launch anchor exited before finish publication")
        _write_exclusive_atomic(handle, FINISH_FILE_NAME, finish_payload)
    elif existing_finish != finish_payload:
        raise LaunchAnchorError("Launch-anchor finish is not result-bound")
    try:
        return_code = handle.process.wait(timeout=timeout_seconds)
    except subprocess.TimeoutExpired as exception:
        raise LaunchAnchorError(
            "Launch anchor did not exit after authenticated finish"
        ) from exception
    if return_code != 0:
        raise LaunchAnchorError(
            f"Launch anchor exited abnormally after finish: {return_code}"
        )
    verify_pinned_launch_anchor_artifacts(handle)
    return return_code


def verify_pinned_launch_anchor_artifacts(handle: LaunchAnchorHandle) -> None:
    """Rejects path, inode, byte, or runtime replacement after first acceptance."""

    _revalidate_launch_inputs(handle)
    for file_name in tuple(handle.pinned_artifacts):
        _payload, pinned = _read_artifact(handle, file_name)
        if pinned != handle.pinned_artifacts[file_name]:
            raise LaunchAnchorError(
                f"Pinned launch-anchor artifact changed: {file_name}"
            )


def close_launch_anchor_handle(handle: LaunchAnchorHandle) -> None:
    """Closes controller FDs without killing or deleting launch evidence."""

    if handle.runtime_directory_descriptor < 0:
        return
    if handle.process.poll() is None:
        raise LaunchAnchorError("Refusing to close a live launch-anchor handle")
    try:
        handle.process.wait(timeout=0)
        verify_pinned_launch_anchor_artifacts(handle)
    finally:
        for pinned_input in (
            handle.staged_source,
            handle.staged_wrapper_jar,
            handle.staged_wrapper_properties,
        ):
            if pinned_input.descriptor >= 0:
                os.close(pinned_input.descriptor)
                pinned_input.descriptor = -1
        os.close(handle.runtime_directory_descriptor)
        handle.runtime_directory_descriptor = -1
