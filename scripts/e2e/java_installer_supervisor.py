#!/usr/bin/env python3
"""Supervise one bounded Java installer behind an inherited socket capability."""

from __future__ import annotations

import argparse
from collections import deque
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import stat
import subprocess
import sys
import time
from typing import BinaryIO, Callable, NoReturn

from macos_guarded_java import (
    GuardedJavaError,
    GuardedJavaMonitor,
    MAXIMUM_TELEMETRY_SIZE_BYTES,
    MacOsProcessMemorySampler,
    MemorySamplingError,
    OwnedJavaProcess,
    memory_guard_is_enforcing,
    memory_guard_process_matches,
    memory_policy_payload,
    start_guarded_java_monitor,
    stop_spawned_auxiliary,
    stop_guarded_java_monitor,
    verify_guard_state_paths,
    verify_java_option_environment,
)


SCHEMA = 1
INSTALLER_KIND_FORGE_CLIENT = "forge-client"
SUPPORTED_INSTALLER_KINDS = frozenset({INSTALLER_KIND_FORGE_CLIENT})
MAXIMUM_MEMORY_MB = 1024
EXACT_HEAP_ARGUMENT = "-Xmx1024M"
INSTALL_TIMEOUT_SECONDS = 900
LEASE_INTERVAL_SECONDS = 1.0
LEASE_EXPIRY_SECONDS = 3.0
ACTIVATION_TIMEOUT_SECONDS = 10.0
ARM_TIMEOUT_SECONDS = 3.0
FINAL_ACK_TIMEOUT_SECONDS = 3.0
MONITOR_TERMINAL_TIMEOUT_SECONDS = 5.0
IDENTITY_BIND_TIMEOUT_SECONDS = 2.0
POLL_INTERVAL_SECONDS = 0.05
GUARD_VERIFICATION_INTERVAL_SECONDS = 0.5
MAXIMUM_FRAME_SIZE = 16 * 1024
MAXIMUM_QUEUED_FRAMES = 8
MAXIMUM_SOCKET_READ_PER_POLL = 64 * 1024
MAXIMUM_OUTPUT_TAIL_SIZE = 64 * 1024
MAXIMUM_OUTPUT_DRAIN_PER_POLL = 256 * 1024
MAXIMUM_ERROR_DETAIL_BYTES = 512
MAXIMUM_INSTALLER_SIZE = 128 * 1024 * 1024
MAXIMUM_JAVA_RELEASE_METADATA_SIZE = 64 * 1024
INSTALLER_LOG_NAME = "installer-output.log"
MONITOR_LOG_NAME = "memory-guard-output.log"
UNKNOWN_RUN_ID = "0" * 64
OWNED_STATE_ROOT = (Path(__file__).resolve().parent / ".state").resolve()
RUN_ID_PATTERN = re.compile(r"[0-9a-f]{64}")
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")
ERROR_CODES = frozenset(
    {
        "activation-invalid",
        "armed-invalid",
        "control-eof",
        "control-invalid",
        "final-ack-invalid",
        "install-timeout",
        "internal-error",
        "java-identity-invalid",
        "java-spawn-failed",
        "lease-expired",
        "memory-guard-failed",
        "monitor-terminal-invalid",
        "stop-requested",
        "supervisor-identity-invalid",
    }
)
TELEMETRY_STATE_FIELDS = frozenset(
    {
        "enforcement_disarmed",
        "stop_callback_invoked",
        "sample_count",
        "retained_record_count",
        "dropped_record_count",
        "last_stop_outcome",
    }
)
TELEMETRY_RECORD_FIELDS = frozenset(
    {
        "observed_at_monotonic_ns",
        "source",
        "status",
        "identity_matches_target",
        "current_phys_footprint_bytes",
        "resident_size_bytes",
        "virtual_size_bytes",
        "lifetime_max_phys_footprint_bytes",
        "detail",
        "decision",
        "stop_outcome",
    }
)


class SupervisorError(RuntimeError):
    """Reports one bounded protocol or ownership failure."""

    def __init__(self, code: str, detail: str) -> None:
        if code not in ERROR_CODES:
            raise ValueError(f"Unknown supervisor error code: {code}")
        super().__init__(detail)
        self.code = code
        self.detail = detail


class ControlEof(SupervisorError):
    """Reports loss of the controller's inherited socket capability."""

    def __init__(self) -> None:
        super().__init__("control-eof", "Controller capability closed")


@dataclass(frozen=True)
class Activation:
    run_id: str
    controller_pid: int
    supervisor_pid: int
    supervisor_process_group_id: int
    supervisor_session_id: int
    supervisor_proc_start_abstime: int
    supervisor_executable: str
    installer_kind: str
    java_path: Path
    installer_path: Path
    installer_size: int
    installer_sha256: str
    launcher_root: Path
    runtime_directory: Path
    maximum_memory_mb: int
    install_timeout_seconds: int


@dataclass
class BoundedLog:
    """Drains one non-blocking pipe into a durable bounded tail."""

    stream: BinaryIO
    path: Path
    tail: bytearray
    closed: bool = False

    def drain(self) -> None:
        if self.closed:
            return
        changed = False
        drained_size = 0
        while drained_size < MAXIMUM_OUTPUT_DRAIN_PER_POLL:
            requested_size = min(
                64 * 1024,
                MAXIMUM_OUTPUT_DRAIN_PER_POLL - drained_size,
            )
            try:
                content = os.read(self.stream.fileno(), requested_size)
            except BlockingIOError:
                break
            except InterruptedError:
                continue
            if not content:
                break
            append_bounded_tail(self.tail, content)
            drained_size += len(content)
            changed = True
        if changed:
            write_bounded_log(self.path, bytes(self.tail))

    def close(self) -> None:
        if not self.closed:
            self.stream.close()
            self.closed = True


@dataclass(frozen=True)
class InstallerLaunch:
    activation: Activation
    supervisor_target: OwnedJavaProcess
    supervisor_session_id: int
    java_process: subprocess.Popen[bytes]
    java_target: OwnedJavaProcess
    java_session_id: int
    monitor: GuardedJavaMonitor
    monitor_process_target: OwnedJavaProcess
    monitor_session_id: int
    sampler: MacOsProcessMemorySampler
    runtime_directory: Path
    installer_log: BoundedLog
    monitor_log: BoundedLog
    started_at: float
    command_sha256: str

    def guard_state(self) -> dict[str, object]:
        return {
            "pid": self.java_target.pid,
            "process_group_id": self.java_target.process_group_id,
            "proc_start_abstime": self.java_target.proc_start_abstime,
            "expected_executable": self.java_target.expected_executable,
            "memory_guard_pid": self.monitor.process.pid,
            "memory_guard_telemetry": str(self.monitor.telemetry_path),
            "memory_guard_readiness": str(self.monitor.readiness_path),
            "memory_guard_maximum_memory_mb": MAXIMUM_MEMORY_MB,
            "memory_guard_group_anchor": identity_payload(self.supervisor_target),
        }


@dataclass
class PartialInstallerOwnership:
    """Retains every resource as soon as the supervisor creates it."""

    java_process: subprocess.Popen[bytes] | None = None
    java_target: OwnedJavaProcess | None = None
    monitor: GuardedJavaMonitor | None = None
    monitor_process: subprocess.Popen[bytes] | None = None
    monitor_process_target: OwnedJavaProcess | None = None
    installer_log: BoundedLog | None = None
    monitor_log: BoundedLog | None = None
    installer_writer: BinaryIO | None = None
    monitor_writer: BinaryIO | None = None


class FramedControl:
    """Reads and writes strict bounded JSON frames on one socket capability."""

    def __init__(self, control_socket: socket.socket) -> None:
        self.socket = control_socket
        self.socket.setblocking(False)
        self.buffer = bytearray()
        self.frames: deque[dict[str, object]] = deque()
        self.eof = False

    def poll(self) -> None:
        received_size = 0
        while received_size < MAXIMUM_SOCKET_READ_PER_POLL:
            try:
                content = self.socket.recv(
                    min(
                        64 * 1024,
                        MAXIMUM_SOCKET_READ_PER_POLL - received_size,
                    )
                )
            except BlockingIOError:
                break
            except InterruptedError:
                continue
            if not content:
                self.eof = True
                break
            self.buffer.extend(content)
            received_size += len(content)
            self._decode_frames()
        self._decode_frames()
        if self.eof and self.buffer:
            raise SupervisorError(
                "control-invalid",
                "Controller capability closed with a partial control frame",
            )

    def _decode_frames(self) -> None:
        while len(self.buffer) >= 4:
            payload_size = int.from_bytes(self.buffer[:4], "big")
            if payload_size <= 0 or payload_size > MAXIMUM_FRAME_SIZE:
                raise SupervisorError(
                    "control-invalid",
                    "Control frame length is outside the strict bound",
                )
            if len(self.buffer) < 4 + payload_size:
                if len(self.buffer) > MAXIMUM_FRAME_SIZE + 4:
                    raise SupervisorError(
                        "control-invalid",
                        "Incomplete control frame exceeded its strict bound",
                    )
                return
            payload = bytes(self.buffer[4 : 4 + payload_size])
            del self.buffer[: 4 + payload_size]
            if len(self.frames) >= MAXIMUM_QUEUED_FRAMES:
                raise SupervisorError(
                    "control-invalid",
                    "Controller queued too many frames",
                )
            self.frames.append(decode_json_payload(payload))

    def receive(self, deadline: float, tick: Callable[[], None]) -> dict[str, object]:
        while True:
            self.poll()
            if self.eof:
                raise ControlEof()
            if self.frames:
                return self.frames.popleft()
            tick()
            if time.monotonic() >= deadline:
                raise SupervisorError(
                    "control-invalid",
                    "Timed out waiting for the next controller frame",
                )
            time.sleep(POLL_INTERVAL_SECONDS)

    def send(self, frame: dict[str, object]) -> None:
        payload = canonical_json_bytes(frame)
        if not payload or len(payload) > MAXIMUM_FRAME_SIZE:
            raise SupervisorError(
                "control-invalid",
                "Outbound control frame exceeded its strict bound",
            )
        framed = len(payload).to_bytes(4, "big") + payload
        view = memoryview(framed)
        deadline = time.monotonic() + 1.0
        while view:
            try:
                sent_size = self.socket.send(view)
            except BlockingIOError:
                sent_size = 0
            except InterruptedError:
                continue
            except (BrokenPipeError, ConnectionResetError) as exception:
                raise ControlEof() from exception
            if sent_size:
                view = view[sent_size:]
                continue
            if time.monotonic() >= deadline:
                raise ControlEof()
            time.sleep(POLL_INTERVAL_SECONDS)


@dataclass
class ControlLease:
    """Validates one contiguous post-ARMED controller lease stream."""

    run_id: str
    last_lease_at: float
    last_sequence: int = 0

    def consume(
        self,
        frame: dict[str, object],
        expected_action: str | None = None,
        expected_digest: tuple[str, str] | None = None,
    ) -> bool:
        action = frame.get("action")
        if action == "STOP":
            validate_bound_frame(frame, "STOP", self.run_id, set())
            raise SupervisorError("stop-requested", "Controller requested installer stop")
        if action == "LEASE":
            validate_bound_frame(frame, "LEASE", self.run_id, {"sequence"})
            sequence = frame["sequence"]
            if (
                type(sequence) is not int
                or sequence <= 0
                or sequence >= 1 << 63
                or sequence != self.last_sequence + 1
            ):
                raise SupervisorError(
                    "control-invalid",
                    "LEASE sequence is not contiguous and strictly increasing",
                )
            self.last_sequence = sequence
            self.last_lease_at = time.monotonic()
            return False
        if expected_action is None:
            raise SupervisorError(
                "control-invalid",
                "Unexpected controller frame while installer is running",
            )
        extra_fields = {expected_digest[0]} if expected_digest is not None else set()
        validate_bound_frame(frame, expected_action, self.run_id, extra_fields)
        if expected_digest is not None and frame[expected_digest[0]] != expected_digest[1]:
            raise SupervisorError(
                "control-invalid",
                f"{expected_action} does not bind the exact supervisor frame",
            )
        return True

    def require_current(self) -> None:
        if time.monotonic() - self.last_lease_at > LEASE_EXPIRY_SECONDS:
            raise SupervisorError("lease-expired", "Controller lease expired")


def reject_json_constant(value: str) -> NoReturn:
    raise ValueError(f"Unsupported JSON constant: {value}")


def reject_duplicate_json_keys(
    pairs: list[tuple[str, object]],
) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def decode_json_payload(payload: bytes) -> dict[str, object]:
    try:
        text = payload.decode("utf-8", errors="strict")
        value = json.loads(
            text,
            object_pairs_hook=reject_duplicate_json_keys,
            parse_constant=reject_json_constant,
        )
    except (UnicodeDecodeError, ValueError, json.JSONDecodeError) as exception:
        raise SupervisorError(
            "control-invalid",
            f"Control frame is not strict JSON: {exception}",
        ) from exception
    if not isinstance(value, dict):
        raise SupervisorError("control-invalid", "Control frame is not an object")
    if canonical_json_bytes(value) != payload:
        raise SupervisorError("control-invalid", "Control frame is not canonical JSON")
    return value


def canonical_json_bytes(frame: dict[str, object]) -> bytes:
    try:
        return json.dumps(
            frame,
            ensure_ascii=True,
            allow_nan=False,
            separators=(",", ":"),
            sort_keys=True,
        ).encode("utf-8")
    except (TypeError, ValueError) as exception:
        raise SupervisorError(
            "control-invalid",
            f"Control frame is not canonical JSON: {exception}",
        ) from exception


def frame_sha256(frame: dict[str, object]) -> str:
    return hashlib.sha256(canonical_json_bytes(frame)).hexdigest()


def append_bounded_tail(tail: bytearray, content: bytes) -> None:
    if not content:
        return
    if len(content) >= MAXIMUM_OUTPUT_TAIL_SIZE:
        tail[:] = content[-MAXIMUM_OUTPUT_TAIL_SIZE:]
        return
    discarded_size = max(len(tail) + len(content) - MAXIMUM_OUTPUT_TAIL_SIZE, 0)
    if discarded_size:
        del tail[:discarded_size]
    tail.extend(content)


def write_bounded_log(path: Path, content: bytes) -> None:
    if len(content) > MAXIMUM_OUTPUT_TAIL_SIZE or path.is_symlink():
        raise SupervisorError("internal-error", f"Unsafe bounded log path: {path}")
    temporary_path = path.parent / f".{path.name}.{os.getpid()}.tmp"
    descriptor = os.open(
        temporary_path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
        0o600,
    )
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(content)
            handle.flush()
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def require_exact_fields(
    frame: dict[str, object],
    expected_fields: set[str],
    action: str,
) -> None:
    if (
        set(frame) != expected_fields
        or type(frame.get("schema")) is not int
        or frame.get("schema") != SCHEMA
    ):
        raise SupervisorError(
            "control-invalid",
            f"{action} frame has an unexpected schema or field inventory",
        )
    if frame.get("action") != action:
        raise SupervisorError(
            "control-invalid",
            f"Expected {action} control frame",
        )
    run_id = frame.get("run_id")
    if not isinstance(run_id, str) or RUN_ID_PATTERN.fullmatch(run_id) is None:
        raise SupervisorError("control-invalid", f"{action} run_id is invalid")


def require_canonical_path(raw_path: object, description: str) -> Path:
    if not isinstance(raw_path, str):
        raise SupervisorError("activation-invalid", f"{description} path is not text")
    path = Path(raw_path)
    if not path.is_absolute():
        raise SupervisorError("activation-invalid", f"{description} path is not absolute")
    try:
        resolved = path.resolve(strict=True)
    except OSError as exception:
        raise SupervisorError(
            "activation-invalid",
            f"{description} path cannot be resolved: {exception}",
        ) from exception
    if path != resolved:
        raise SupervisorError(
            "activation-invalid",
            f"{description} path is linked or non-canonical",
        )
    return path


def require_regular_file(path: Path, description: str) -> None:
    try:
        mode = path.stat(follow_symlinks=False).st_mode
    except OSError as exception:
        raise SupervisorError(
            "activation-invalid",
            f"{description} is unavailable: {exception}",
        ) from exception
    if path.is_symlink() or not stat.S_ISREG(mode):
        raise SupervisorError(
            "activation-invalid",
            f"{description} is linked or irregular",
        )


def stable_file_fields(file_stat: os.stat_result) -> tuple[int, ...]:
    """Returns fields that must remain unchanged across an authenticated read."""

    return (
        file_stat.st_dev,
        file_stat.st_ino,
        file_stat.st_mode,
        file_stat.st_nlink,
        file_stat.st_uid,
        file_stat.st_size,
        file_stat.st_mtime_ns,
        file_stat.st_ctime_ns,
    )


def open_regular_file_no_follow(
    path: Path,
    error_code: str,
    description: str,
) -> tuple[int, os.stat_result]:
    """Opens and snapshots one regular file without following a final link."""

    flags = os.O_RDONLY | os.O_NOFOLLOW
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC
    descriptor: int | None = None
    try:
        descriptor = os.open(path, flags)
        file_stat = os.fstat(descriptor)
    except OSError as exception:
        if descriptor is not None:
            try:
                os.close(descriptor)
            except OSError:
                pass
        raise SupervisorError(
            error_code,
            f"Cannot open {description}: {exception}",
        ) from exception
    if not stat.S_ISREG(file_stat.st_mode) or file_stat.st_nlink != 1:
        os.close(descriptor)
        raise SupervisorError(
            error_code,
            f"{description} is linked or irregular",
        )
    return descriptor, file_stat


def verify_open_file_unchanged(
    path: Path,
    descriptor: int,
    before: os.stat_result,
    content_size: int,
    error_code: str,
    description: str,
) -> None:
    """Proves an open regular file and its current name stayed stable."""

    try:
        after = os.fstat(descriptor)
        named = path.stat(follow_symlinks=False)
    except OSError as exception:
        raise SupervisorError(
            error_code,
            f"Cannot revalidate {description}: {exception}",
        ) from exception
    if (
        stable_file_fields(before) != stable_file_fields(after)
        or before.st_dev != named.st_dev
        or before.st_ino != named.st_ino
        or content_size != after.st_size
    ):
        raise SupervisorError(
            error_code,
            f"{description} changed while it was read",
        )


def read_bounded_stable_regular_file(
    path: Path,
    maximum_size: int,
    error_code: str,
    description: str,
    *,
    require_private_owner: bool = False,
) -> bytes:
    """Reads one bounded regular file through a stable no-follow descriptor."""

    descriptor, before = open_regular_file_no_follow(path, error_code, description)
    try:
        if (
            before.st_size < 0
            or before.st_size > maximum_size
            or (
                require_private_owner
                and (
                    before.st_uid != os.getuid()
                    or stat.S_IMODE(before.st_mode) != 0o600
                )
            )
        ):
            raise SupervisorError(
                error_code,
                f"{description} exceeded its bound or is not owner-private",
            )
        content = bytearray()
        while len(content) <= maximum_size:
            try:
                chunk = os.read(
                    descriptor,
                    min(64 * 1024, maximum_size + 1 - len(content)),
                )
            except InterruptedError:
                continue
            if not chunk:
                break
            content.extend(chunk)
        if len(content) > maximum_size:
            raise SupervisorError(
                error_code,
                f"{description} exceeded its strict size bound",
            )
        verify_open_file_unchanged(
            path,
            descriptor,
            before,
            len(content),
            error_code,
            description,
        )
        return bytes(content)
    except OSError as exception:
        raise SupervisorError(
            error_code,
            f"Cannot read {description}: {exception}",
        ) from exception
    finally:
        os.close(descriptor)


def sha256_file(path: Path, maximum_size: int) -> str:
    descriptor, before = open_regular_file_no_follow(
        path,
        "activation-invalid",
        "pinned installer",
    )
    digest = hashlib.sha256()
    read_size = 0
    try:
        if before.st_size < 0 or before.st_size > maximum_size:
            raise SupervisorError(
                "activation-invalid",
                "Pinned installer grew beyond its strict size bound",
            )
        while True:
            try:
                content = os.read(descriptor, 1024 * 1024)
            except InterruptedError:
                continue
            if not content:
                break
            read_size += len(content)
            if read_size > maximum_size:
                raise SupervisorError(
                    "activation-invalid",
                    "Pinned installer grew beyond its strict size bound",
                )
            digest.update(content)
        verify_open_file_unchanged(
            path,
            descriptor,
            before,
            read_size,
            "activation-invalid",
            "pinned installer",
        )
        return digest.hexdigest()
    except OSError as exception:
        raise SupervisorError(
            "activation-invalid",
            f"Cannot read pinned installer: {exception}",
        ) from exception
    finally:
        os.close(descriptor)


def require_java_17(java_path: Path) -> None:
    require_regular_file(java_path, "Java executable")
    if java_path.name != "java" or not os.access(java_path, os.X_OK):
        raise SupervisorError(
            "activation-invalid",
            "Java 17 path does not name an executable java binary",
        )
    release_path = require_canonical_path(
        str(java_path.parent.parent / "release"),
        "Java release metadata",
    )
    try:
        release_text = read_bounded_stable_regular_file(
            release_path,
            MAXIMUM_JAVA_RELEASE_METADATA_SIZE,
            "activation-invalid",
            "Java release metadata",
        ).decode("utf-8")
    except UnicodeDecodeError as exception:
        raise SupervisorError(
            "activation-invalid",
            f"Cannot read Java release metadata: {exception}",
        ) from exception
    versions = re.findall(r'^JAVA_VERSION="([^"]+)"$', release_text, re.MULTILINE)
    if len(versions) != 1 or re.fullmatch(r"17(?:\.[0-9]+)*(?:[-+].*)?", versions[0]) is None:
        raise SupervisorError(
            "activation-invalid",
            "Java release metadata does not identify exactly Java 17",
        )


def parse_activation(
    frame: dict[str, object],
    supervisor_target: OwnedJavaProcess,
    supervisor_session_id: int,
) -> Activation:
    require_exact_fields(
        frame,
        {
            "schema",
            "action",
            "run_id",
            "controller_pid",
            "supervisor_pid",
            "supervisor_process_group_id",
            "supervisor_session_id",
            "supervisor_proc_start_abstime",
            "supervisor_executable",
            "installer_kind",
            "java_path",
            "installer_path",
            "installer_size",
            "installer_sha256",
            "launcher_root",
            "runtime_directory",
            "maximum_memory_mb",
            "install_timeout_seconds",
        },
        "ACTIVATE",
    )
    controller_pid = frame["controller_pid"]
    if type(controller_pid) is not int or controller_pid <= 0:
        raise SupervisorError("activation-invalid", "Controller PID is invalid")
    if controller_pid != os.getppid():
        raise SupervisorError(
            "activation-invalid",
            "Activation controller PID does not own this supervisor",
        )
    expected_supervisor_fields = {
        "supervisor_pid": supervisor_target.pid,
        "supervisor_process_group_id": supervisor_target.process_group_id,
        "supervisor_session_id": supervisor_session_id,
        "supervisor_proc_start_abstime": supervisor_target.proc_start_abstime,
        "supervisor_executable": supervisor_target.expected_executable,
    }
    if any(frame[name] != value for name, value in expected_supervisor_fields.items()):
        raise SupervisorError(
            "activation-invalid",
            "Activation does not bind the exact supervisor identity",
        )
    installer_kind = frame["installer_kind"]
    if not isinstance(installer_kind, str) or installer_kind not in SUPPORTED_INSTALLER_KINDS:
        raise SupervisorError("activation-invalid", "Installer kind is unsupported")
    maximum_memory_mb = frame["maximum_memory_mb"]
    install_timeout_seconds = frame["install_timeout_seconds"]
    if maximum_memory_mb != MAXIMUM_MEMORY_MB or type(maximum_memory_mb) is not int:
        raise SupervisorError(
            "activation-invalid",
            "Installer heap policy must remain exactly 1024 MiB",
        )
    if (
        install_timeout_seconds != INSTALL_TIMEOUT_SECONDS
        or type(install_timeout_seconds) is not int
    ):
        raise SupervisorError(
            "activation-invalid",
            "Installer timeout must remain exactly 900 seconds",
        )
    installer_size = frame["installer_size"]
    installer_sha256 = frame["installer_sha256"]
    if (
        type(installer_size) is not int
        or installer_size <= 0
        or installer_size > MAXIMUM_INSTALLER_SIZE
    ):
        raise SupervisorError("activation-invalid", "Installer size pin is invalid")
    if (
        not isinstance(installer_sha256, str)
        or SHA256_PATTERN.fullmatch(installer_sha256) is None
    ):
        raise SupervisorError("activation-invalid", "Installer SHA-256 pin is invalid")
    java_path = require_canonical_path(frame["java_path"], "Java executable")
    launcher_root = require_canonical_path(frame["launcher_root"], "Launcher root")
    runtime_directory = require_canonical_path(
        frame["runtime_directory"],
        "Supervisor runtime",
    )
    installer_path = require_canonical_path(frame["installer_path"], "Installer")
    require_java_17(java_path)
    if not launcher_root.is_dir() or launcher_root.is_symlink():
        raise SupervisorError("activation-invalid", "Launcher root is linked or irregular")
    try:
        launcher_root.relative_to(OWNED_STATE_ROOT)
    except ValueError as exception:
        raise SupervisorError(
            "activation-invalid",
            "Launcher root is outside the repository-owned E2E state",
        ) from exception
    runtime_stat = runtime_directory.stat(follow_symlinks=False)
    if (
        not runtime_directory.is_dir()
        or runtime_directory.is_symlink()
        or runtime_directory.parent != launcher_root.parent
        or runtime_stat.st_uid != os.getuid()
        or stat.S_IMODE(runtime_stat.st_mode) != 0o700
        or any(runtime_directory.iterdir())
    ):
        raise SupervisorError(
            "activation-invalid",
            "Supervisor runtime is not a fresh owned 0700 staging directory",
        )
    require_regular_file(installer_path, "Pinned installer")
    if (
        installer_path.parent != launcher_root / "installers"
        or installer_path.suffix != ".jar"
    ):
        raise SupervisorError(
            "activation-invalid",
            "Pinned installer is outside the isolated launcher installers directory",
        )
    installer_stat_before = installer_path.stat(follow_symlinks=False)
    installer_digest = sha256_file(installer_path, MAXIMUM_INSTALLER_SIZE)
    installer_stat_after = installer_path.stat(follow_symlinks=False)
    stable_fields_before = (
        installer_stat_before.st_dev,
        installer_stat_before.st_ino,
        installer_stat_before.st_size,
        installer_stat_before.st_mtime_ns,
    )
    stable_fields_after = (
        installer_stat_after.st_dev,
        installer_stat_after.st_ino,
        installer_stat_after.st_size,
        installer_stat_after.st_mtime_ns,
    )
    if (
        installer_stat_before.st_size != installer_size
        or installer_digest != installer_sha256
        or stable_fields_before != stable_fields_after
    ):
        raise SupervisorError(
            "activation-invalid",
            "Pinned installer bytes do not match activation",
        )
    return Activation(
        run_id=frame["run_id"],
        controller_pid=controller_pid,
        supervisor_pid=supervisor_target.pid,
        supervisor_process_group_id=supervisor_target.process_group_id,
        supervisor_session_id=supervisor_session_id,
        supervisor_proc_start_abstime=supervisor_target.proc_start_abstime,
        supervisor_executable=supervisor_target.expected_executable,
        installer_kind=installer_kind,
        java_path=java_path,
        installer_path=installer_path,
        installer_size=installer_size,
        installer_sha256=installer_sha256,
        launcher_root=launcher_root,
        runtime_directory=runtime_directory,
        maximum_memory_mb=maximum_memory_mb,
        install_timeout_seconds=install_timeout_seconds,
    )


def build_installer_command(activation: Activation) -> list[str]:
    if activation.installer_kind == INSTALLER_KIND_FORGE_CLIENT:
        return [
            str(activation.java_path),
            EXACT_HEAP_ARGUMENT,
            "-jar",
            str(activation.installer_path),
            "--installClient",
            str(activation.launcher_root),
        ]
    raise SupervisorError("activation-invalid", "Installer kind is unsupported")


def installer_command_sha256(command: list[str]) -> str:
    if not command or any(not isinstance(argument, str) for argument in command):
        raise SupervisorError("internal-error", "Installer command is invalid")
    payload = json.dumps(
        command,
        ensure_ascii=True,
        allow_nan=False,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def retain_anchor_during_group_term(
    _signal_number: int,
    _frame: object,
) -> None:
    """Keeps the live group leader available while Java handles SIGTERM."""


def bind_process(
    process: subprocess.Popen[bytes],
    process_group_id: int,
    expected_executable: Path,
    sampler: MacOsProcessMemorySampler,
) -> OwnedJavaProcess:
    deadline = time.monotonic() + IDENTITY_BIND_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if process.poll() is not None:
            break
        try:
            actual_group = os.getpgid(process.pid)
        except ProcessLookupError:
            actual_group = -1
        if actual_group == process_group_id:
            target = sampler.bind(
                process.pid,
                process_group_id,
                str(expected_executable),
            )
            if target is not None:
                return target
        time.sleep(0.01)
    raise SupervisorError(
        "java-identity-invalid",
        "Spawned process identity could not be bound to its expected group",
    )


def bind_supervisor_identity(
    sampler: MacOsProcessMemorySampler,
) -> tuple[OwnedJavaProcess, int]:
    pid = os.getpid()
    process_group_id = os.getpgrp()
    session_id = os.getsid(0)
    if pid != process_group_id or pid != session_id:
        raise SupervisorError(
            "supervisor-identity-invalid",
            "Supervisor must be the leader of its dedicated process group and session",
        )
    try:
        target = sampler.bind_current_process()
    except (MemorySamplingError, TypeError, ValueError) as exception:
        raise SupervisorError(
            "supervisor-identity-invalid",
            f"Supervisor identity could not be bound: {exception}",
        ) from exception
    if target.pid != pid or target.process_group_id != process_group_id:
        raise SupervisorError(
            "supervisor-identity-invalid",
            "Supervisor identity could not be bound",
        )
    return target, session_id


def create_pipe_log(path: Path) -> tuple[BoundedLog, BinaryIO]:
    write_bounded_log(path, b"")
    read_descriptor, write_descriptor = os.pipe()
    os.set_inheritable(read_descriptor, False)
    os.set_inheritable(write_descriptor, False)
    os.set_blocking(read_descriptor, False)
    reader = os.fdopen(read_descriptor, "rb", buffering=0)
    writer = os.fdopen(write_descriptor, "wb", buffering=0)
    return BoundedLog(reader, path, bytearray()), writer


def start_installer(
    activation: Activation,
    supervisor_target: OwnedJavaProcess,
    supervisor_session_id: int,
    sampler: MacOsProcessMemorySampler,
    ownership: PartialInstallerOwnership,
) -> InstallerLaunch:
    try:
        verify_java_option_environment(os.environ)
    except GuardedJavaError as exception:
        raise SupervisorError("activation-invalid", str(exception)) from exception
    run_directory = activation.runtime_directory
    installer_log, installer_writer = create_pipe_log(run_directory / INSTALLER_LOG_NAME)
    ownership.installer_log = installer_log
    ownership.installer_writer = installer_writer
    try:
        signal.signal(signal.SIGTERM, retain_anchor_during_group_term)
        command = build_installer_command(activation)
        java_process = subprocess.Popen(
            command,
            cwd=activation.launcher_root,
            env=dict(os.environ),
            stdin=subprocess.DEVNULL,
            stdout=installer_writer,
            stderr=subprocess.STDOUT,
            start_new_session=False,
            close_fds=True,
        )
        ownership.java_process = java_process
        installer_writer.close()
        ownership.installer_writer = None
        java_target = bind_process(
            java_process,
            supervisor_target.process_group_id,
            activation.java_path,
            sampler,
        )
        ownership.java_target = java_target
        java_session_id = os.getsid(java_process.pid)
        if java_session_id != supervisor_session_id:
            raise SupervisorError(
                "java-identity-invalid",
                "Installer Java escaped the supervisor session",
            )
        monitor_log, monitor_writer = create_pipe_log(
            run_directory / MONITOR_LOG_NAME
        )
        ownership.monitor_log = monitor_log
        ownership.monitor_writer = monitor_writer
        monitor = start_guarded_java_monitor(
            java_target,
            MAXIMUM_MEMORY_MB,
            run_directory,
            monitor_writer,
            group_anchor=supervisor_target,
            process_started=lambda process: setattr(
                ownership,
                "monitor_process",
                process,
            ),
        )
        ownership.monitor = monitor
        ownership.monitor_process = monitor.process
        monitor_process_target = bind_process(
            monitor.process,
            monitor.process.pid,
            Path(supervisor_target.expected_executable),
            sampler,
        )
        ownership.monitor_process_target = monitor_process_target
        monitor_session_id = os.getsid(monitor.process.pid)
        if monitor_session_id != monitor.process.pid:
            raise SupervisorError(
                "memory-guard-failed",
                "Memory guard did not retain its dedicated session",
            )
        monitor_writer.close()
        ownership.monitor_writer = None
        if java_process.poll() is not None or monitor.process.poll() is not None:
            raise SupervisorError(
                "java-spawn-failed",
                "Installer or memory guard exited before handoff",
            )
        return InstallerLaunch(
            activation=activation,
            supervisor_target=supervisor_target,
            supervisor_session_id=supervisor_session_id,
            java_process=java_process,
            java_target=java_target,
            java_session_id=java_session_id,
            monitor=monitor,
            monitor_process_target=monitor_process_target,
            monitor_session_id=monitor_session_id,
            sampler=sampler,
            runtime_directory=run_directory,
            installer_log=installer_log,
            monitor_log=monitor_log,
            started_at=time.monotonic(),
            command_sha256=installer_command_sha256(command),
        )
    except BaseException:
        for writer_name in ("installer_writer", "monitor_writer"):
            writer = getattr(ownership, writer_name)
            if writer is not None:
                try:
                    writer.close()
                except OSError:
                    pass
                setattr(ownership, writer_name, None)
        raise


def identity_payload(target: OwnedJavaProcess) -> dict[str, object]:
    return {
        "pid": target.pid,
        "process_group_id": target.process_group_id,
        "proc_start_abstime": target.proc_start_abstime,
        "expected_executable": target.expected_executable,
    }


def handoff_frame(launch: InstallerLaunch) -> dict[str, object]:
    return {
        "schema": SCHEMA,
        "action": "HANDOFF",
        "run_id": launch.activation.run_id,
        "supervisor_pid": launch.supervisor_target.pid,
        "supervisor_process_group_id": launch.supervisor_target.process_group_id,
        "supervisor_session_id": launch.supervisor_session_id,
        "supervisor_proc_start_abstime": launch.supervisor_target.proc_start_abstime,
        "supervisor_executable": launch.supervisor_target.expected_executable,
        "java_pid": launch.java_target.pid,
        "java_process_group_id": launch.java_target.process_group_id,
        "java_session_id": launch.java_session_id,
        "java_proc_start_abstime": launch.java_target.proc_start_abstime,
        "java_executable": launch.java_target.expected_executable,
        "monitor_pid": launch.monitor.process.pid,
        "monitor_process_group_id": launch.monitor_process_target.process_group_id,
        "monitor_session_id": launch.monitor_session_id,
        "monitor_proc_start_abstime": (
            launch.monitor_process_target.proc_start_abstime
        ),
        "monitor_executable": launch.monitor_process_target.expected_executable,
        "monitor_target": identity_payload(launch.monitor.target),
        "monitor_group_anchor": identity_payload(launch.supervisor_target),
        "monitor_readiness": str(launch.monitor.readiness_path),
        "monitor_telemetry": str(launch.monitor.telemetry_path),
        "memory_policy": memory_policy_payload(MAXIMUM_MEMORY_MB),
        "installer_output_log": str(launch.installer_log.path),
        "monitor_output_log": str(launch.monitor_log.path),
        "installer_command_sha256": launch.command_sha256,
        "lease_interval_seconds": int(LEASE_INTERVAL_SECONDS),
        "lease_expiry_seconds": int(LEASE_EXPIRY_SECONDS),
    }


def validate_bound_frame(
    frame: dict[str, object],
    action: str,
    run_id: str,
    extra_fields: set[str],
) -> None:
    require_exact_fields(
        frame,
        {"schema", "action", "run_id", *extra_fields},
        action,
    )
    if frame["run_id"] != run_id:
        raise SupervisorError("control-invalid", f"{action} run_id changed")


def drain_logs(launch: InstallerLaunch) -> None:
    launch.installer_log.drain()
    launch.monitor_log.drain()


def verify_live_guard(launch: InstallerLaunch) -> None:
    if (
        launch.monitor.target != launch.java_target
        or launch.monitor.group_anchor != launch.supervisor_target
    ):
        raise SupervisorError(
            "memory-guard-failed",
            "Memory guard target or group anchor changed",
        )
    state = launch.guard_state()
    try:
        verify_guard_state_paths(state, launch.runtime_directory)
    except GuardedJavaError as exception:
        raise SupervisorError("memory-guard-failed", str(exception)) from exception
    if launch.monitor.process.poll() is not None:
        raise SupervisorError(
            "memory-guard-failed",
            "Memory guard exited while installer Java remained live",
        )
    if launch.sampler.revalidate(launch.monitor_process_target) != launch.monitor_process_target:
        raise SupervisorError(
            "memory-guard-failed",
            "Memory guard process identity changed",
        )
    if not memory_guard_process_matches(state) or not memory_guard_is_enforcing(state):
        raise SupervisorError(
            "memory-guard-failed",
            "Memory guard identity or authoritative telemetry was lost",
        )


def wait_for_armed(
    control: FramedControl,
    launch: InstallerLaunch,
    handoff_sha256: str,
) -> ControlLease:
    verify_live_guard(launch)
    last_guard_verification = time.monotonic()

    def tick() -> None:
        nonlocal last_guard_verification
        drain_logs(launch)
        if launch.java_process.poll() is not None:
            raise SupervisorError(
                "armed-invalid",
                "Installer Java exited before controller armed the handoff",
            )
        now = time.monotonic()
        if now - last_guard_verification >= GUARD_VERIFICATION_INTERVAL_SECONDS:
            verify_live_guard(launch)
            last_guard_verification = time.monotonic()

    frame = control.receive(time.monotonic() + ARM_TIMEOUT_SECONDS, tick)
    if frame.get("action") == "STOP":
        validate_bound_frame(frame, "STOP", launch.activation.run_id, set())
        raise SupervisorError("stop-requested", "Controller requested installer stop")
    validate_bound_frame(
        frame,
        "ARMED",
        launch.activation.run_id,
        {"handoff_sha256"},
    )
    if frame["handoff_sha256"] != handoff_sha256:
        raise SupervisorError("armed-invalid", "ARMED does not bind the exact handoff")
    return ControlLease(launch.activation.run_id, time.monotonic())


def poll_running_installer(
    control: FramedControl,
    launch: InstallerLaunch,
    lease: ControlLease,
) -> int:
    verify_live_guard(launch)
    last_guard_verification = time.monotonic()
    while True:
        control.poll()
        if control.eof:
            raise ControlEof()
        while control.frames:
            lease.consume(control.frames.popleft())
        drain_logs(launch)
        returncode = launch.java_process.poll()
        if returncode is not None:
            reaped_returncode = launch.java_process.wait(timeout=0)
            if type(reaped_returncode) is not int or reaped_returncode != returncode:
                raise SupervisorError(
                    "internal-error",
                    "Installer Java return code was not reaped exactly",
                )
            drain_logs(launch)
            return reaped_returncode
        now = time.monotonic()
        if now - last_guard_verification >= GUARD_VERIFICATION_INTERVAL_SECONDS:
            verify_live_guard(launch)
            last_guard_verification = time.monotonic()
        lease.require_current()
        now = time.monotonic()
        if now - launch.started_at > launch.activation.install_timeout_seconds:
            raise SupervisorError("install-timeout", "Installer exceeded 900 seconds")
        time.sleep(POLL_INTERVAL_SECONDS)


def verify_natural_terminal_telemetry(launch: InstallerLaunch) -> None:
    telemetry_path = launch.monitor.telemetry_path
    try:
        content = read_bounded_stable_regular_file(
            telemetry_path,
            MAXIMUM_TELEMETRY_SIZE_BYTES,
            "monitor-terminal-invalid",
            "terminal memory telemetry",
            require_private_owner=True,
        )
        payload = json.loads(content.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise SupervisorError(
            "monitor-terminal-invalid",
            f"Cannot read terminal memory telemetry: {exception}",
        ) from exception
    records = payload.get("records") if isinstance(payload, dict) else None
    state = payload.get("state") if isinstance(payload, dict) else None
    if (
        not isinstance(payload, dict)
        or set(payload) != {"schema", "target", "policy", "state", "records"}
        or type(payload.get("schema")) is not int
        or payload.get("schema") != SCHEMA
        or payload.get("target") != identity_payload(launch.java_target)
        or payload.get("policy") != memory_policy_payload(MAXIMUM_MEMORY_MB)
        or not isinstance(state, dict)
        or set(state) != TELEMETRY_STATE_FIELDS
        or state.get("enforcement_disarmed") is not False
        or state.get("stop_callback_invoked") is not False
        or state.get("last_stop_outcome") != "not-required"
        or not isinstance(records, list)
        or not records
        or not isinstance(records[-1], dict)
    ):
        raise SupervisorError(
            "monitor-terminal-invalid",
            "Terminal memory telemetry does not match the guarded installer",
        )
    sample_count = state["sample_count"]
    retained_record_count = state["retained_record_count"]
    dropped_record_count = state["dropped_record_count"]
    if (
        type(sample_count) is not int
        or type(retained_record_count) is not int
        or type(dropped_record_count) is not int
        or retained_record_count != len(records)
        or dropped_record_count < 0
        or sample_count != retained_record_count + dropped_record_count
    ):
        raise SupervisorError(
            "monitor-terminal-invalid",
            "Terminal memory telemetry counters are inconsistent",
        )
    for record in records[:-1]:
        if (
            not isinstance(record, dict)
            or set(record) != TELEMETRY_RECORD_FIELDS
            or record.get("source") != "proc-pid-rusage-v4"
            or record.get("status") != "available"
            or record.get("identity_matches_target") is not True
            or record.get("decision") not in ("normal", "warning")
            or record.get("stop_outcome") != "not-required"
        ):
            raise SupervisorError(
                "monitor-terminal-invalid",
                "Memory telemetry retained a non-authoritative running sample",
            )
    terminal = records[-1]
    if not (
        set(terminal) == TELEMETRY_RECORD_FIELDS
        and terminal.get("source") == "proc-pid-rusage-v4"
        and terminal.get("status") == "missing"
        and terminal.get("identity_matches_target") is None
        and terminal.get("decision") == "not-enforceable"
        and terminal.get("stop_outcome") == "not-required"
    ):
        raise SupervisorError(
            "monitor-terminal-invalid",
            "Memory guard did not publish a natural terminal sample",
        )


def wait_for_monitor_terminal(
    control: FramedControl,
    launch: InstallerLaunch,
    lease: ControlLease,
) -> None:
    deadline = time.monotonic() + MONITOR_TERMINAL_TIMEOUT_SECONDS
    while launch.monitor.process.poll() is None:
        control.poll()
        if control.eof:
            raise ControlEof()
        while control.frames:
            lease.consume(control.frames.popleft())
        drain_logs(launch)
        lease.require_current()
        if time.monotonic() >= deadline:
            raise SupervisorError(
                "monitor-terminal-invalid",
                "Memory guard did not publish terminal telemetry in time",
            )
        time.sleep(POLL_INTERVAL_SECONDS)
    drain_logs(launch)
    if launch.monitor.process.wait(timeout=0) != 0:
        raise SupervisorError(
            "monitor-terminal-invalid",
            "Memory guard exited unsuccessfully after installer completion",
        )
    if launch.sampler.revalidate(launch.monitor_process_target) is not None:
        raise SupervisorError(
            "monitor-terminal-invalid",
            "Memory guard process remained live after being reaped",
        )
    verify_natural_terminal_telemetry(launch)


def freeze_launch_logs(launch: InstallerLaunch) -> None:
    """Closes both drained pipes before authenticating their final tails."""

    drain_logs(launch)
    launch.installer_log.close()
    launch.monitor_log.close()


def java_exited_frame(
    launch: InstallerLaunch,
    returncode: int,
) -> dict[str, object]:
    """Authenticates the bounded transition into monitor-terminal handling."""

    if type(returncode) is not int or not -(1 << 31) <= returncode < (1 << 31):
        raise SupervisorError("internal-error", "Installer return code is invalid")
    return {
        "schema": SCHEMA,
        "action": "JAVA_EXITED",
        "run_id": launch.activation.run_id,
        "java_pid": launch.java_target.pid,
        "java_process_group_id": launch.java_target.process_group_id,
        "java_session_id": launch.java_session_id,
        "java_proc_start_abstime": launch.java_target.proc_start_abstime,
        "java_executable": launch.java_target.expected_executable,
        "returncode": returncode,
        "reaped": True,
        "cleanup_disposition": "java-reaped-monitor-terminal-pending",
        "installer_command_sha256": launch.command_sha256,
        "monitor_pid": launch.monitor_process_target.pid,
        "monitor_process_group_id": launch.monitor_process_target.process_group_id,
        "monitor_session_id": launch.monitor_session_id,
        "monitor_proc_start_abstime": launch.monitor_process_target.proc_start_abstime,
        "monitor_executable": launch.monitor_process_target.expected_executable,
        "monitor_terminal_timeout_seconds": int(MONITOR_TERMINAL_TIMEOUT_SECONDS),
    }


def completion_frame(launch: InstallerLaunch, returncode: int) -> dict[str, object]:
    if type(returncode) is not int or not -(1 << 31) <= returncode < (1 << 31):
        raise SupervisorError("internal-error", "Installer return code is invalid")
    tail = bytes(launch.installer_log.tail)
    return {
        "schema": SCHEMA,
        "action": "COMPLETION",
        "run_id": launch.activation.run_id,
        "java_pid": launch.java_target.pid,
        "java_process_group_id": launch.java_target.process_group_id,
        "java_session_id": launch.java_session_id,
        "java_proc_start_abstime": launch.java_target.proc_start_abstime,
        "java_executable": launch.java_target.expected_executable,
        "returncode": returncode,
        "reaped": True,
        "cleanup_disposition": "java-and-monitor-reaped-anchor-kill-pending",
        "installer_command_sha256": launch.command_sha256,
        "monitor_pid": launch.monitor_process_target.pid,
        "monitor_process_group_id": launch.monitor_process_target.process_group_id,
        "monitor_session_id": launch.monitor_session_id,
        "monitor_proc_start_abstime": (
            launch.monitor_process_target.proc_start_abstime
        ),
        "monitor_executable": launch.monitor_process_target.expected_executable,
        "monitor_reaped": True,
        "output_tail_length": len(tail),
        "output_tail_sha256": hashlib.sha256(tail).hexdigest(),
    }


def wait_for_final_ack(
    control: FramedControl,
    launch: InstallerLaunch,
    completion_sha256: str,
    lease: ControlLease,
) -> None:
    deadline = time.monotonic() + FINAL_ACK_TIMEOUT_SECONDS

    def tick() -> None:
        lease.require_current()

    while True:
        frame = control.receive(deadline, tick)
        if frame.get("action") == "LEASE":
            lease.consume(frame)
            continue
        if frame.get("action") == "STOP":
            lease.consume(frame)
        validate_bound_frame(
            frame,
            "FINAL_ACK",
            launch.activation.run_id,
            {"completion_sha256"},
        )
        if frame["completion_sha256"] != completion_sha256:
            raise SupervisorError(
                "final-ack-invalid",
                "FINAL_ACK does not bind the exact completion",
            )
        return


def bounded_detail(detail: str) -> str:
    content = detail.encode("utf-8", errors="replace")[:MAXIMUM_ERROR_DETAIL_BYTES]
    return content.decode("utf-8", errors="ignore")


def error_frame(
    run_id: str,
    error: SupervisorError,
    out_of_group_cleanup_complete: bool,
) -> dict[str, object]:
    selected_run_id = run_id if RUN_ID_PATTERN.fullmatch(run_id) else UNKNOWN_RUN_ID
    return {
        "schema": SCHEMA,
        "action": "ERROR",
        "run_id": selected_run_id,
        "code": error.code,
        "detail": bounded_detail(error.detail),
        "out_of_group_cleanup_complete": out_of_group_cleanup_complete,
        "anchor_group_kill_pending": True,
    }


def close_launch_logs(launch: InstallerLaunch | None) -> None:
    if launch is None:
        return
    for bounded_log in (launch.installer_log, launch.monitor_log):
        try:
            bounded_log.drain()
        except (OSError, ValueError, SupervisorError):
            pass
        try:
            bounded_log.close()
        except (OSError, ValueError):
            pass


def close_partial_ownership(ownership: PartialInstallerOwnership) -> None:
    for writer_name in ("installer_writer", "monitor_writer"):
        writer = getattr(ownership, writer_name)
        if writer is not None:
            try:
                writer.close()
            except OSError:
                pass
            setattr(ownership, writer_name, None)
    for log_name in ("installer_log", "monitor_log"):
        bounded_log = getattr(ownership, log_name)
        if bounded_log is not None:
            try:
                bounded_log.drain()
            except (OSError, ValueError, SupervisorError):
                pass
            try:
                bounded_log.close()
            except (OSError, ValueError):
                pass


def kill_anchor_group() -> NoReturn:
    pid = os.getpid()
    process_group_id = os.getpgrp()
    session_id = os.getsid(0)
    if pid != process_group_id or pid != session_id:
        os.kill(pid, signal.SIGKILL)
        raise RuntimeError("Direct supervisor SIGKILL unexpectedly returned")
    os.killpg(process_group_id, signal.SIGKILL)
    raise RuntimeError("Supervisor process-group SIGKILL unexpectedly returned")


def abort_anchor(
    control: FramedControl,
    run_id: str,
    error: SupervisorError,
    ownership: PartialInstallerOwnership,
) -> NoReturn:
    cleanup_failures: list[str] = []
    java_process = ownership.java_process
    if java_process is not None:
        try:
            os.killpg(os.getpgrp(), signal.SIGTERM)
            java_process.wait(timeout=2.0)
        except ProcessLookupError:
            try:
                java_process.wait(timeout=0)
            except BaseException as exception:
                cleanup_failures.append(f"Java reap failed: {exception}")
        except BaseException as exception:
            cleanup_failures.append(f"anchored Java cleanup pending: {exception}")
    out_of_group_cleanup_complete = True
    if ownership.monitor is not None or ownership.monitor_process is not None:
        try:
            if ownership.monitor is not None:
                stop_guarded_java_monitor(ownership.monitor)
            else:
                stop_spawned_auxiliary(ownership.monitor_process)
        except BaseException as exception:
            out_of_group_cleanup_complete = False
            cleanup_failures.append(f"monitor cleanup failed: {exception}")
    if cleanup_failures:
        error = SupervisorError(
            error.code,
            bounded_detail(error.detail + "; " + "; ".join(cleanup_failures)),
        )
    try:
        control.send(
            error_frame(
                run_id,
                error,
                out_of_group_cleanup_complete,
            )
        )
    except BaseException:
        pass
    finally:
        try:
            close_partial_ownership(ownership)
        finally:
            kill_anchor_group()


def supervise(control_socket: socket.socket) -> NoReturn:
    control = FramedControl(control_socket)
    run_id = UNKNOWN_RUN_ID
    launch: InstallerLaunch | None = None
    ownership = PartialInstallerOwnership()
    try:
        activation_frame = control.receive(
            time.monotonic() + ACTIVATION_TIMEOUT_SECONDS,
            lambda: None,
        )
        candidate_run_id = activation_frame.get("run_id")
        if (
            isinstance(candidate_run_id, str)
            and RUN_ID_PATTERN.fullmatch(candidate_run_id) is not None
        ):
            run_id = candidate_run_id
        sampler = MacOsProcessMemorySampler.native()
        supervisor_target, supervisor_session_id = bind_supervisor_identity(sampler)
        activation = parse_activation(
            activation_frame,
            supervisor_target,
            supervisor_session_id,
        )
        run_id = activation.run_id
        launch = start_installer(
            activation,
            supervisor_target,
            supervisor_session_id,
            sampler,
            ownership,
        )
        handoff = handoff_frame(launch)
        handoff_sha256 = frame_sha256(handoff)
        control.send(handoff)
        lease = wait_for_armed(control, launch, handoff_sha256)
        returncode = poll_running_installer(control, launch, lease)
        control.send(java_exited_frame(launch, returncode))
        wait_for_monitor_terminal(control, launch, lease)
        freeze_launch_logs(launch)
        completion = completion_frame(launch, returncode)
        completion_sha256 = frame_sha256(completion)
        control.send(completion)
        wait_for_final_ack(control, launch, completion_sha256, lease)
    except BaseException as exception:
        selected_error = (
            exception
            if isinstance(exception, SupervisorError)
            else SupervisorError(
                "internal-error",
                f"{type(exception).__name__}: {exception}",
            )
        )
        abort_anchor(
            control,
            run_id,
            selected_error,
            ownership,
        )
    try:
        close_launch_logs(launch)
    finally:
        kill_anchor_group()


def inherited_control_socket(control_fd: int) -> socket.socket:
    if type(control_fd) is not int or control_fd < 3:
        raise SupervisorError("control-invalid", "Control FD is invalid")
    try:
        control_socket = socket.socket(fileno=control_fd)
    except OSError as exception:
        raise SupervisorError(
            "control-invalid",
            f"Control FD is not a socket: {exception}",
        ) from exception
    try:
        if (
            control_socket.family != socket.AF_UNIX
            or control_socket.getsockopt(socket.SOL_SOCKET, socket.SO_TYPE)
            != socket.SOCK_STREAM
            or control_socket.getsockname() not in ("", None)
            or control_socket.getpeername() not in ("", None)
        ):
            raise SupervisorError(
                "control-invalid",
                "Control FD is not a private unnamed Unix stream socket",
            )
        os.set_inheritable(control_fd, False)
        if os.get_inheritable(control_fd):
            raise SupervisorError(
                "control-invalid",
                "Control socket did not become close-on-exec",
            )
        return control_socket
    except Exception:
        control_socket.close()
        raise


def parse_arguments(arguments: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Supervise one typed, bounded Java installer",
    )
    parser.add_argument("--control-fd", required=True, type=int)
    return parser.parse_args(arguments)


def main(arguments: list[str] | None = None) -> int:
    options = parse_arguments(sys.argv[1:] if arguments is None else arguments)
    try:
        control_socket = inherited_control_socket(options.control_fd)
    except SupervisorError as exception:
        print(f"Java installer supervisor refused control socket: {exception}", file=sys.stderr)
        return 2
    with control_socket:
        supervise(control_socket)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
