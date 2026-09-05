#!/usr/bin/env python3
"""Persistent fail-closed supervision for one Forge server Java process group."""

from __future__ import annotations

import argparse
from collections import deque
from dataclasses import dataclass
import errno
import json
import math
import os
from pathlib import Path
import re
import secrets
import select
import signal
import stat
import subprocess
import sys
import time
from typing import Callable, Mapping, Sequence


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
BASELINE_DIRECTORY = SCRIPT_DIRECTORY.parent / "baseline"
if str(BASELINE_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(BASELINE_DIRECTORY))

from macos_memory_guard import (  # noqa: E402
    GIBIBYTE_BYTES,
    MAXIMUM_TELEMETRY_SIZE_BYTES,
    MacOsProcessMemorySampler,
    MacOsRusage,
    MemorySample,
    OwnedJavaProcess,
    SampleSource,
    SampleStatus,
)


PGREP_PATH = Path("/usr/bin/pgrep")
READINESS_FILE_NAME = ".forge-server-launch-watchdog-ready.json"
TELEMETRY_FILE_NAME = "forge-server-launch-watchdog-telemetry.json"
MAXIMUM_READINESS_SIZE_BYTES = 16 * 1024
MAXIMUM_INVENTORY_SIZE_BYTES = 4 * 1024
MAXIMUM_REPORTED_JAVA_PROCESS_COUNT = 256
MAXIMUM_ERROR_SIZE_BYTES = 512
MAXIMUM_JAVA_PROCESS_COUNT = 3
MAXIMUM_TRACKED_JAVA_PROCESS_COUNT = 16
MAXIMUM_SIGNAL_ACTION_COUNT = 16
MAXIMUM_ARTIFACT_READ_ATTEMPTS = 4
MAXIMUM_PROCESS_ID = (1 << 31) - 1
PER_PROCESS_CEILING_BYTES = 5 * GIBIBYTE_BYTES
AGGREGATE_CEILING_BYTES = 6 * GIBIBYTE_BYTES
DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 5.0
DEFAULT_READINESS_TIMEOUT_SECONDS = 5.0
DEFAULT_REAP_TIMEOUT_SECONDS = 20.0
POLL_INTERVAL_SECONDS = 0.25
TERMINATION_GRACE_SECONDS = 2.0
MAXIMUM_TELEMETRY_AGE_NANOSECONDS = 3_000_000_000
MAXIMUM_TIMEOUT_SECONDS = 60 * 60
INTEGER_PATTERN = re.compile(rb"[1-9][0-9]*")
READINESS_FIELDS = frozenset(
    {
        "schema",
        "watchdog_pid",
        "controller_pid",
        "controller_process_group_id",
        "anchor",
        "owned_session_id",
        "heartbeat_timeout_nanoseconds",
        "ready_at_monotonic_ns",
        "enforcement_active",
    }
)
IDENTITY_FIELDS = frozenset(
    {
        "pid",
        "process_group_id",
        "proc_start_abstime",
        "expected_executable",
    }
)
TELEMETRY_FIELDS = frozenset(
    {
        "schema",
        "status",
        "decision",
        "reason",
        "observed_at_monotonic_ns",
        "anchor",
        "owned_session_id",
        "controller_pid",
        "controller_process_group_id",
        "controller_heartbeat",
        "limits",
        "java_inventory",
        "external_java_process_ids",
        "tracked_exact_identities",
        "samples",
        "aggregate_current_phys_footprint_bytes",
        "owned_group_absent",
        "signal_actions",
        "terminal_attestation",
    }
)
HEARTBEAT_FIELDS = frozenset(
    {
        "status",
        "last_received_at_monotonic_ns",
        "age_nanoseconds",
    }
)
LIMIT_FIELDS = frozenset(
    {
        "maximum_java_process_count",
        "per_process_current_phys_footprint_bytes",
        "aggregate_current_phys_footprint_bytes",
    }
)
SAMPLE_FIELDS = frozenset(
    {
        "pid",
        "source",
        "status",
        "current_phys_footprint_bytes",
        "detail",
    }
)
EXACT_IDENTITY_SAMPLE_FIELDS = frozenset(
    {
        "expected_identity",
        "source",
        "status",
        "current_phys_footprint_bytes",
        "detail",
    }
)
TERMINAL_ATTESTATION_FIELDS = frozenset(
    {
        "owned_group_absent",
        "tracked_identities_absent",
        "global_java_inventory",
        "global_java_inventory_error",
        "external_java_remained",
        "exact_identity_samples",
        "exact_identity_sample_error",
    }
)


class LaunchWatchdogError(RuntimeError):
    """Reports an invalid or unsafe launch-watchdog state."""


class LaunchWatchdogStartError(LaunchWatchdogError):
    """Preserves a spawned watchdog when bounded readiness cannot be proved."""

    def __init__(
        self,
        message: str,
        handle: LaunchWatchdogHandle,
    ) -> None:
        super().__init__(message)
        self.handle = handle


@dataclass(frozen=True)
class HeartbeatObservation:
    """Describes the controller heartbeat as observed by the watchdog."""

    status: str
    last_received_at_monotonic_ns: int
    age_nanoseconds: int


@dataclass(frozen=True)
class WatchdogPoll:
    """Carries one complete ownership, inventory, and memory observation."""

    reason: str | None
    owned_group_absent: bool
    inventory: tuple[int, ...]
    external_java_process_ids: tuple[int, ...]
    samples: tuple[MemorySample, ...]
    active_identities: tuple[OwnedJavaProcess, ...]
    aggregate_current_phys_footprint_bytes: int


class LaunchWatchdogHandle:
    """Owns the controller side of a persistent watchdog heartbeat pipe."""

    def __init__(
        self,
        process: subprocess.Popen[bytes],
        heartbeat_write_descriptor: int,
        anchor: OwnedJavaProcess,
        session_id: int,
        controller_pid: int,
        controller_process_group_id: int,
        heartbeat_timeout_nanoseconds: int,
        runtime_directory_descriptor: int,
        readiness_path: Path,
        telemetry_path: Path,
    ) -> None:
        self.process = process
        self.heartbeat_write_descriptor = heartbeat_write_descriptor
        self.anchor = anchor
        self.session_id = session_id
        self.controller_pid = controller_pid
        self.controller_process_group_id = controller_process_group_id
        self.heartbeat_timeout_nanoseconds = heartbeat_timeout_nanoseconds
        self.runtime_directory_descriptor = runtime_directory_descriptor
        self.readiness_path = readiness_path
        self.telemetry_path = telemetry_path
        self.verified_terminal_artifact_contents: dict[str, bytes] | None = None

    def close_heartbeat(self) -> None:
        """Closes the heartbeat exactly once, causing fail-closed supervision."""

        if self.heartbeat_write_descriptor < 0:
            return
        os.close(self.heartbeat_write_descriptor)
        self.heartbeat_write_descriptor = -1

    def close_runtime_directory(self) -> None:
        """Closes the pinned artifact directory exactly once."""

        if self.runtime_directory_descriptor < 0:
            return
        os.close(self.runtime_directory_descriptor)
        self.runtime_directory_descriptor = -1


def _bounded_detail(value: object) -> str:
    content = str(value).encode("utf-8", errors="replace")
    if len(content) <= MAXIMUM_ERROR_SIZE_BYTES:
        return content.decode("utf-8")
    return content[:MAXIMUM_ERROR_SIZE_BYTES].decode("utf-8", errors="ignore")


def _identity_payload(identity: OwnedJavaProcess) -> dict[str, object]:
    return {
        "pid": identity.pid,
        "process_group_id": identity.process_group_id,
        "proc_start_abstime": identity.proc_start_abstime,
        "expected_executable": identity.expected_executable,
    }


def _sample_payload(sample: MemorySample) -> dict[str, object]:
    return {
        "pid": (
            sample.observed_identity.pid
            if sample.observed_identity is not None
            else None
        ),
        "source": sample.source.value,
        "status": sample.status.value,
        "current_phys_footprint_bytes": sample.current_phys_footprint_bytes,
        "detail": sample.detail,
    }


def _exact_identity_sample_payload(
    identity: OwnedJavaProcess,
    sample: MemorySample,
) -> dict[str, object]:
    return {
        "expected_identity": _identity_payload(identity),
        "source": sample.source.value,
        "status": sample.status.value,
        "current_phys_footprint_bytes": sample.current_phys_footprint_bytes,
        "detail": sample.detail,
    }


def _require_positive_integer(value: int, name: str) -> None:
    if type(value) is not int or value <= 0:
        raise LaunchWatchdogError(f"{name} must be one positive integer")


def _require_positive_finite_seconds(value: float, name: str) -> None:
    if (
        type(value) not in (int, float)
        or not math.isfinite(value)
        or value <= 0
        or value > MAXIMUM_TIMEOUT_SECONDS
    ):
        raise LaunchWatchdogError(f"{name} must be one positive bounded duration")


def _validate_artifact_directory(
    runtime_directory: Path,
    descriptor: int,
) -> None:
    try:
        information = runtime_directory.lstat()
        descriptor_information = os.fstat(descriptor)
    except OSError as exception:
        raise LaunchWatchdogError(
            f"Cannot inspect the watchdog runtime directory: {exception}"
        ) from exception
    if (
        not stat.S_ISDIR(information.st_mode)
        or stat.S_ISLNK(information.st_mode)
        or not stat.S_ISDIR(descriptor_information.st_mode)
        or information.st_uid != os.getuid()
        or descriptor_information.st_uid != os.getuid()
        or stat.S_IMODE(information.st_mode) != 0o700
        or stat.S_IMODE(descriptor_information.st_mode) != 0o700
        or information.st_dev != descriptor_information.st_dev
        or information.st_ino != descriptor_information.st_ino
    ):
        raise LaunchWatchdogError(
            "The watchdog runtime directory must be an owner-only real directory"
        )


def _open_artifact_directory(runtime_directory: Path) -> int:
    flags = os.O_RDONLY
    flags |= getattr(os, "O_DIRECTORY", 0)
    flags |= getattr(os, "O_CLOEXEC", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(runtime_directory, flags)
    except OSError as exception:
        raise LaunchWatchdogError(
            f"Cannot pin the watchdog runtime directory: {exception}"
        ) from exception
    try:
        _validate_artifact_directory(runtime_directory, descriptor)
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def _validate_pgrep_binary(path: Path = PGREP_PATH) -> None:
    try:
        information = path.lstat()
    except OSError as exception:
        raise LaunchWatchdogError(f"Cannot inspect {path}: {exception}") from exception
    if (
        not stat.S_ISREG(information.st_mode)
        or stat.S_ISLNK(information.st_mode)
        or information.st_uid != 0
        or stat.S_IMODE(information.st_mode) & 0o022
        or not os.access(path, os.X_OK)
    ):
        raise LaunchWatchdogError(
            f"Java inventory command is not one root-owned protected file: {path}"
        )


def parse_java_process_inventory(content: bytes, return_code: int) -> tuple[int, ...]:
    """Strictly parses bounded output from ``/usr/bin/pgrep -x java``."""

    if type(return_code) is not int or return_code not in (0, 1):
        raise LaunchWatchdogError(
            f"Java inventory exited with unsupported status {return_code!r}"
        )
    if not isinstance(content, bytes) or len(content) > MAXIMUM_INVENTORY_SIZE_BYTES:
        raise LaunchWatchdogError("Java inventory output exceeded its byte bound")
    if return_code == 1:
        if content:
            raise LaunchWatchdogError("Empty Java inventory reported unexpected output")
        return ()
    if not content or not content.endswith(b"\n") or b"\r" in content:
        raise LaunchWatchdogError("Java inventory output is incomplete")
    lines = content.splitlines()
    if not lines or len(lines) > MAXIMUM_REPORTED_JAVA_PROCESS_COUNT:
        raise LaunchWatchdogError("Java inventory exceeds its reporting bound")
    process_ids: list[int] = []
    for line in lines:
        if INTEGER_PATTERN.fullmatch(line) is None:
            raise LaunchWatchdogError("Java inventory contains an invalid PID")
        process_id = int(line)
        _require_positive_integer(process_id, "Java inventory PID")
        if process_id > MAXIMUM_PROCESS_ID:
            raise LaunchWatchdogError("Java inventory PID exceeds pid_t")
        process_ids.append(process_id)
    if len(set(process_ids)) != len(process_ids):
        raise LaunchWatchdogError("Java inventory contains a duplicate PID")
    return tuple(sorted(process_ids))


def read_java_process_inventory(
    *,
    run_command: Callable[..., subprocess.CompletedProcess[bytes]] = subprocess.run,
    pgrep_path: Path = PGREP_PATH,
) -> tuple[int, ...]:
    """Returns all globally named Java PIDs through one protected native command."""

    _validate_pgrep_binary(pgrep_path)
    try:
        completed = run_command(
            [str(pgrep_path), "-x", "java"],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            timeout=2.0,
            env={
                "LANG": "C",
                "LC_ALL": "C",
                "PATH": "/usr/bin:/bin",
            },
        )
    except (OSError, subprocess.SubprocessError) as exception:
        raise LaunchWatchdogError(
            f"Cannot inventory Java processes: {_bounded_detail(exception)}"
        ) from exception
    stderr = completed.stderr
    if not isinstance(stderr, bytes) or len(stderr) > MAXIMUM_INVENTORY_SIZE_BYTES:
        raise LaunchWatchdogError("Java inventory diagnostics exceeded their byte bound")
    if stderr:
        raise LaunchWatchdogError("Java inventory wrote unexpected diagnostics")
    return parse_java_process_inventory(completed.stdout, completed.returncode)


def process_group_exists(
    process_group_id: int,
    *,
    signal_group: Callable[[int, int], None] = os.killpg,
) -> bool:
    """Observes group existence without sending a terminating signal."""

    _require_positive_integer(process_group_id, "process_group_id")
    try:
        signal_group(process_group_id, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    except OSError as exception:
        raise LaunchWatchdogError(
            f"Cannot attest process-group existence: {_bounded_detail(exception)}"
        ) from exception
    return True


class LaunchWatchdogEngine:
    """Tracks every Java identity belonging to one pinned launch group."""

    def __init__(
        self,
        anchor: OwnedJavaProcess,
        session_id: int,
        sampler: MacOsProcessMemorySampler,
        *,
        inventory_reader: Callable[[], tuple[int, ...]] = read_java_process_inventory,
        process_group_reader: Callable[[int], int] = os.getpgid,
        session_reader: Callable[[int], int] = os.getsid,
        group_exists_reader: Callable[[int], bool] = process_group_exists,
        monotonic_ns: Callable[[], int] = time.monotonic_ns,
    ) -> None:
        _require_positive_integer(session_id, "session_id")
        if anchor.pid != anchor.process_group_id:
            raise LaunchWatchdogError("The Java anchor must be its process-group leader")
        self.anchor = anchor
        self.session_id = session_id
        self.sampler = sampler
        self.inventory_reader = inventory_reader
        self.process_group_reader = process_group_reader
        self.session_reader = session_reader
        self.group_exists_reader = group_exists_reader
        self.monotonic_ns = monotonic_ns
        self.identities: dict[int, OwnedJavaProcess] = {anchor.pid: anchor}
        self.known_identities: dict[int, OwnedJavaProcess] = {anchor.pid: anchor}
        self.anchor_continuity_active = True
        self.final_loss_discovery_consumed = False

    def poll(self) -> WatchdogPoll:
        """Performs one fail-closed global inventory and authoritative sample pass."""

        return self._poll(MAXIMUM_TRACKED_JAVA_PROCESS_COUNT)

    def _poll(
        self,
        disappearance_retries: int,
        inventory_override: tuple[int, ...] | None = None,
    ) -> WatchdogPoll:
        """Runs one pass with bounded recovery for authenticated child exits."""

        if inventory_override is None:
            try:
                inventory = self.inventory_reader()
            except Exception as exception:
                return self._failed_poll(
                    f"java-inventory-error: {_bounded_detail(exception)}"
                )
        else:
            inventory = inventory_override
        if (
            not isinstance(inventory, tuple)
            or any(type(process_id) is not int or process_id <= 0 for process_id in inventory)
            or len(set(inventory)) != len(inventory)
        ):
            return self._failed_poll("java-inventory-invalid")
        samples: list[MemorySample] = []
        external: list[int] = []
        observed_at = self.monotonic_ns()
        anchor_missing = False
        if (
            not self.anchor_continuity_active
            or self.final_loss_discovery_consumed
        ):
            try:
                group_exists = self.group_exists_reader(
                    self.anchor.process_group_id
                )
            except Exception as exception:
                return self._failed_poll(
                    f"group-attestation-error: {_bounded_detail(exception)}",
                    inventory=inventory,
                )
            if not group_exists and not inventory and not self.identities:
                return WatchdogPoll(
                    reason=None,
                    owned_group_absent=True,
                    inventory=(),
                    external_java_process_ids=(),
                    samples=(),
                    active_identities=(),
                    aggregate_current_phys_footprint_bytes=0,
                )
            return self._failed_poll(
                "anchor-ownership-continuity-already-lost",
                inventory=inventory,
            )

        anchor_identity = self.identities.get(self.anchor.pid)
        if anchor_identity is None:
            return self._failed_poll(
                "anchor-identity-unexpectedly-untracked",
                inventory=inventory,
            )
        try:
            anchor_sample = self.sampler.sample(anchor_identity, observed_at)
        except Exception as exception:
            return self._failed_poll(
                f"authoritative-sample-error: {_bounded_detail(exception)}",
                inventory=inventory,
            )
        samples.append(anchor_sample)
        if anchor_sample.status is SampleStatus.MISSING:
            del self.identities[self.anchor.pid]
            anchor_missing = True
            self.anchor_continuity_active = False
            self.final_loss_discovery_consumed = True
            try:
                inventory = self.inventory_reader()
            except Exception as exception:
                return self._failed_poll(
                    (
                        "final-loss-java-inventory-error: "
                        f"{_bounded_detail(exception)}"
                    ),
                    samples=tuple(samples),
                )
            if (
                not isinstance(inventory, tuple)
                or any(
                    type(process_id) is not int or process_id <= 0
                    for process_id in inventory
                )
                or len(set(inventory)) != len(inventory)
            ):
                return self._failed_poll(
                    "final-loss-java-inventory-invalid",
                    samples=tuple(samples),
                )
        elif (
            anchor_sample.source is not SampleSource.PROC_PID_RUSAGE_V4
            or anchor_sample.status is not SampleStatus.AVAILABLE
            or anchor_sample.observed_identity != anchor_identity
            or anchor_sample.current_phys_footprint_bytes is None
        ):
            return self._failed_poll(
                f"authoritative-sample-{anchor_sample.status.value}",
                inventory=inventory,
                samples=tuple(samples),
            )
        elif self.anchor.pid not in inventory:
            return self._failed_poll(
                "java-inventory-omission",
                inventory=inventory,
                samples=tuple(samples),
            )

        process_count_exceeded = len(inventory) > MAXIMUM_JAVA_PROCESS_COUNT
        for process_id, identity in tuple(self.identities.items()):
            if process_id in inventory:
                continue
            try:
                sample = self.sampler.sample(identity, observed_at)
            except Exception as exception:
                return self._failed_poll(
                    f"authoritative-sample-error: {_bounded_detail(exception)}",
                    inventory=inventory,
                    samples=tuple(samples),
                )
            if sample.status is SampleStatus.MISSING:
                del self.identities[process_id]
                continue
            samples.append(sample)
            return self._failed_poll(
                (
                    "java-inventory-omission"
                    if sample.status is SampleStatus.AVAILABLE
                    else f"authoritative-sample-{sample.status.value}"
                ),
                inventory=inventory,
                samples=tuple(samples),
            )

        if anchor_missing and not inventory and not self.identities:
            try:
                group_exists = self.group_exists_reader(
                    self.anchor.process_group_id
                )
            except Exception as exception:
                return self._failed_poll(
                    f"group-attestation-error: {_bounded_detail(exception)}",
                    inventory=inventory,
                    samples=tuple(samples),
                )
            if not group_exists:
                return WatchdogPoll(
                    reason=None,
                    owned_group_absent=True,
                    inventory=(),
                    external_java_process_ids=(),
                    samples=tuple(samples),
                    active_identities=(),
                    aggregate_current_phys_footprint_bytes=0,
                )

        identity_bound_exceeded = False
        for process_id in inventory:
            identity = self.identities.get(process_id)
            try:
                process_group_id = self.process_group_reader(process_id)
                session_id = self.session_reader(process_id)
            except ProcessLookupError as exception:
                transition = self._retry_after_tracked_child_disappearance(
                    identity,
                    observed_at,
                    disappearance_retries,
                    inventory,
                    tuple(external),
                    tuple(samples),
                    identity_bound_exceeded,
                    process_count_exceeded,
                )
                if transition is not None:
                    return transition
                return self._failed_poll(
                    f"java-identity-read-error: {_bounded_detail(exception)}",
                    inventory=inventory,
                )
            except Exception as exception:
                return self._failed_poll(
                    f"java-identity-read-error: {_bounded_detail(exception)}",
                    inventory=inventory,
                )
            if (
                process_group_id != self.anchor.process_group_id
                or session_id != self.session_id
            ):
                external.append(process_id)
                continue
            if identity is None:
                if process_id in self.known_identities:
                    return self._failed_poll(
                        "java-known-process-id-reuse",
                        inventory=inventory,
                        external=tuple(external),
                        samples=tuple(samples),
                    )
                if len(self.known_identities) >= MAXIMUM_TRACKED_JAVA_PROCESS_COUNT:
                    identity_bound_exceeded = True
                    continue
                try:
                    identity = self.sampler.bind_observed(
                        process_id,
                        self.anchor.process_group_id,
                    )
                except Exception as exception:
                    return self._failed_poll(
                        f"java-bind-error: {_bounded_detail(exception)}",
                        inventory=inventory,
                    )
                if identity is None:
                    return self._failed_poll(
                        "java-bind-error: candidate identity disappeared or changed",
                        inventory=inventory,
                    )
                self.identities[process_id] = identity
                self.known_identities[process_id] = identity
                try:
                    rebound_group = self.process_group_reader(process_id)
                    rebound_session = self.session_reader(process_id)
                except Exception as exception:
                    return self._failed_poll(
                        f"java-bind-revalidation-error: {_bounded_detail(exception)}",
                        inventory=inventory,
                    )
                if (
                    rebound_group != self.anchor.process_group_id
                    or rebound_session != self.session_id
                ):
                    return self._failed_poll(
                        "java-bind-identity-drift",
                        inventory=inventory,
                    )
            elif process_id == self.anchor.pid and not anchor_missing:
                continue
            try:
                sample = self.sampler.sample(identity, observed_at)
            except Exception as exception:
                return self._failed_poll(
                    f"authoritative-sample-error: {_bounded_detail(exception)}",
                    inventory=inventory,
                    external=tuple(external),
                    samples=tuple(samples),
                )
            samples.append(sample)
            if sample.status is SampleStatus.MISSING:
                transition = self._retry_after_tracked_child_disappearance(
                    identity,
                    observed_at,
                    disappearance_retries,
                    inventory,
                    tuple(external),
                    tuple(samples),
                    identity_bound_exceeded,
                    process_count_exceeded,
                    sample=sample,
                )
                if transition is not None:
                    return transition
            if (
                sample.source is not SampleSource.PROC_PID_RUSAGE_V4
                or sample.status is not SampleStatus.AVAILABLE
                or sample.observed_identity != identity
                or sample.current_phys_footprint_bytes is None
            ):
                return self._failed_poll(
                    f"authoritative-sample-{sample.status.value}",
                    inventory=inventory,
                    external=tuple(external),
                    samples=tuple(samples),
                )

        if external:
            return self._failed_poll(
                "java-outside-owned-launch",
                inventory=inventory,
                external=tuple(external),
                samples=tuple(samples),
            )

        footprint_values = [
            sample.current_phys_footprint_bytes
            for sample in samples
            if sample.current_phys_footprint_bytes is not None
        ]
        aggregate = sum(footprint_values)
        if anchor_missing:
            return self._failed_poll(
                "anchor-ownership-continuity-lost",
                inventory=inventory,
                external=tuple(external),
                samples=tuple(samples),
                aggregate=aggregate,
            )
        if identity_bound_exceeded:
            return self._failed_poll(
                "tracked-java-identity-bound-exceeded",
                inventory=inventory,
                samples=tuple(samples),
                aggregate=aggregate,
            )
        if process_count_exceeded:
            return self._failed_poll(
                "too-many-java-processes",
                inventory=inventory,
                samples=tuple(samples),
                aggregate=aggregate,
            )
        if any(value > PER_PROCESS_CEILING_BYTES for value in footprint_values):
            return self._failed_poll(
                "per-process-memory-ceiling-exceeded",
                inventory=inventory,
                samples=tuple(samples),
                aggregate=aggregate,
            )
        if aggregate > AGGREGATE_CEILING_BYTES:
            return self._failed_poll(
                "aggregate-memory-ceiling-exceeded",
                inventory=inventory,
                samples=tuple(samples),
                aggregate=aggregate,
            )
        try:
            group_absent = not self.group_exists_reader(self.anchor.process_group_id)
        except Exception as exception:
            return self._failed_poll(
                f"group-attestation-error: {_bounded_detail(exception)}",
                inventory=inventory,
                samples=tuple(samples),
                aggregate=aggregate,
            )
        return WatchdogPoll(
            reason=None,
            owned_group_absent=group_absent,
            inventory=inventory,
            external_java_process_ids=(),
            samples=tuple(samples),
            active_identities=tuple(
                self.identities[process_id]
                for process_id in sorted(self.identities)
            ),
            aggregate_current_phys_footprint_bytes=aggregate,
        )

    def _retry_after_tracked_child_disappearance(
        self,
        identity: OwnedJavaProcess | None,
        observed_at: int,
        disappearance_retries: int,
        inventory: tuple[int, ...],
        external: tuple[int, ...],
        samples: tuple[MemorySample, ...],
        identity_bound_exceeded: bool,
        process_count_exceeded: bool,
        *,
        sample: MemorySample | None = None,
    ) -> WatchdogPoll | None:
        """Restarts a bounded pass after proving one tracked child disappeared."""

        if (
            identity is None
            or identity.pid == self.anchor.pid
            or disappearance_retries <= 0
            or self.identities.get(identity.pid) != identity
            or not self.anchor_continuity_active
            or self.final_loss_discovery_consumed
        ):
            return None
        observed_sample = sample
        if observed_sample is None:
            try:
                observed_sample = self.sampler.sample(identity, observed_at)
            except Exception:
                return None
        if (
            observed_sample.source is not SampleSource.PROC_PID_RUSAGE_V4
            or observed_sample.status is not SampleStatus.MISSING
        ):
            return None
        observed_failure = self._observed_poll_failure(
            inventory,
            external,
            samples,
            identity_bound_exceeded,
            process_count_exceeded,
        )
        if observed_failure is not None:
            return observed_failure
        try:
            refreshed_inventory = self.inventory_reader()
        except Exception as exception:
            return self._failed_poll(
                (
                    "child-exit-java-inventory-error: "
                    f"{_bounded_detail(exception)}"
                ),
                samples=(observed_sample,),
            )
        if (
            not isinstance(refreshed_inventory, tuple)
            or any(
                type(process_id) is not int or process_id <= 0
                for process_id in refreshed_inventory
            )
            or len(set(refreshed_inventory)) != len(refreshed_inventory)
        ):
            return self._failed_poll(
                "child-exit-java-inventory-invalid",
                samples=(observed_sample,),
            )
        if identity.pid in refreshed_inventory:
            return None
        del self.identities[identity.pid]
        return self._poll(
            disappearance_retries - 1,
            inventory_override=refreshed_inventory,
        )

    def _observed_poll_failure(
        self,
        inventory: tuple[int, ...],
        external: tuple[int, ...],
        samples: tuple[MemorySample, ...],
        identity_bound_exceeded: bool,
        process_count_exceeded: bool,
    ) -> WatchdogPoll | None:
        """Preserves every failure already observed before an exit rescan."""

        footprint_values = [
            sample.current_phys_footprint_bytes
            for sample in samples
            if sample.current_phys_footprint_bytes is not None
        ]
        aggregate = sum(footprint_values)
        if external:
            return self._failed_poll(
                "java-outside-owned-launch",
                inventory=inventory,
                external=external,
                samples=samples,
                aggregate=aggregate,
            )
        if identity_bound_exceeded:
            return self._failed_poll(
                "tracked-java-identity-bound-exceeded",
                inventory=inventory,
                samples=samples,
                aggregate=aggregate,
            )
        if process_count_exceeded:
            return self._failed_poll(
                "too-many-java-processes",
                inventory=inventory,
                samples=samples,
                aggregate=aggregate,
            )
        if any(value > PER_PROCESS_CEILING_BYTES for value in footprint_values):
            return self._failed_poll(
                "per-process-memory-ceiling-exceeded",
                inventory=inventory,
                samples=samples,
                aggregate=aggregate,
            )
        if aggregate > AGGREGATE_CEILING_BYTES:
            return self._failed_poll(
                "aggregate-memory-ceiling-exceeded",
                inventory=inventory,
                samples=samples,
                aggregate=aggregate,
            )
        return None

    def _failed_poll(
        self,
        reason: str,
        *,
        inventory: tuple[int, ...] = (),
        external: tuple[int, ...] = (),
        samples: tuple[MemorySample, ...] = (),
        aggregate: int = 0,
    ) -> WatchdogPoll:
        return WatchdogPoll(
            reason=_bounded_detail(reason),
            owned_group_absent=False,
            inventory=inventory,
            external_java_process_ids=external,
            samples=samples,
            active_identities=tuple(
                self.identities[process_id]
                for process_id in sorted(self.identities)
            ),
            aggregate_current_phys_footprint_bytes=aggregate,
        )


def _identity_is_intrinsically_signalable(
    identity: OwnedJavaProcess,
    sampler: MacOsProcessMemorySampler,
    *,
    current_pid: Callable[[], int],
) -> bool:
    if identity.pid == current_pid():
        return False
    return sampler.revalidate_intrinsic_identity(identity) == identity


def _identity_anchors_owned_group(
    identity: OwnedJavaProcess,
    session_id: int,
    sampler: MacOsProcessMemorySampler,
    *,
    process_group_reader: Callable[[int], int],
    session_reader: Callable[[int], int],
    current_pid: Callable[[], int],
) -> bool:
    if not _identity_is_intrinsically_signalable(
        identity,
        sampler,
        current_pid=current_pid,
    ):
        return False
    try:
        return (
            process_group_reader(identity.pid) == identity.process_group_id
            and session_reader(identity.pid) == session_id
        )
    except OSError:
        return False


def signal_owned_launch(
    identities: Sequence[OwnedJavaProcess],
    anchor: OwnedJavaProcess,
    session_id: int,
    sampler: MacOsProcessMemorySampler,
    signal_number: int,
    *,
    controller_pid: int,
    controller_process_group_id: int,
    process_group_reader: Callable[[int], int] = os.getpgid,
    session_reader: Callable[[int], int] = os.getsid,
    signal_process: Callable[[int, int], None] = os.kill,
    signal_group: Callable[[int, int], None] = os.killpg,
    current_pid: Callable[[], int] = os.getpid,
    current_process_group_id: Callable[[], int] = os.getpgrp,
) -> str:
    """Signals only identities revalidated inside the pinned session and group."""

    _require_positive_integer(session_id, "session_id")
    _require_positive_integer(controller_pid, "controller_pid")
    _require_positive_integer(
        controller_process_group_id,
        "controller_process_group_id",
    )
    if signal_number not in (signal.SIGTERM, signal.SIGKILL):
        raise LaunchWatchdogError("Only SIGTERM and SIGKILL may stop an owned launch")
    selected_identities = tuple(identities)
    if (
        len(selected_identities) > MAXIMUM_TRACKED_JAVA_PROCESS_COUNT
        or any(
            not isinstance(identity, OwnedJavaProcess)
            for identity in selected_identities
        )
        or len({identity.pid for identity in selected_identities})
        != len(selected_identities)
    ):
        raise LaunchWatchdogError("Signal targets are not one bounded identity set")

    group_anchor = next(
        (
            identity
            for identity in selected_identities
            if _identity_anchors_owned_group(
                identity,
                session_id,
                sampler,
                process_group_reader=process_group_reader,
                session_reader=session_reader,
                current_pid=current_pid,
            )
        ),
        None,
    )
    group_signalable = (
        anchor.pid == anchor.process_group_id
        and anchor.process_group_id != controller_process_group_id
        and anchor.process_group_id != current_process_group_id()
        and group_anchor is not None
    )
    group_action = "owned-group-not-signaled"
    if group_signalable:
        try:
            signal_group(anchor.process_group_id, signal_number)
        except ProcessLookupError:
            group_action = "owned-group-already-missing"
        except OSError as exception:
            group_action = f"owned-group-signal-error:{_bounded_detail(exception)}"
        else:
            group_action = "owned-group-signaled"

    signaled = 0
    signal_errors = 0
    for identity in selected_identities:
        identity_in_signaled_group = _identity_anchors_owned_group(
            identity,
            session_id,
            sampler,
            process_group_reader=process_group_reader,
            session_reader=session_reader,
            current_pid=current_pid,
        )
        if group_action == "owned-group-signaled" and identity_in_signaled_group:
            continue
        if not _identity_is_intrinsically_signalable(
            identity,
            sampler,
            current_pid=current_pid,
        ):
            continue
        try:
            signal_process(identity.pid, signal_number)
        except ProcessLookupError:
            continue
        except OSError:
            signal_errors += 1
            continue
        signaled += 1
    return (
        f"{group_action};exact-identities-signaled:{signaled};"
        f"errors:{signal_errors}"
    )


def _telemetry_payload(
    anchor: OwnedJavaProcess,
    session_id: int,
    controller_pid: int,
    controller_process_group_id: int,
    heartbeat: HeartbeatObservation,
    poll: WatchdogPoll,
    *,
    status: str,
    decision: str,
    reason: str | None,
    signal_actions: Sequence[str],
    terminal_attestation: Mapping[str, object] | None,
    observed_at_monotonic_ns: int,
) -> dict[str, object]:
    return {
        "schema": "etherology-forge-server-launch-watchdog-v1",
        "status": status,
        "decision": decision,
        "reason": reason,
        "observed_at_monotonic_ns": observed_at_monotonic_ns,
        "anchor": _identity_payload(anchor),
        "owned_session_id": session_id,
        "controller_pid": controller_pid,
        "controller_process_group_id": controller_process_group_id,
        "controller_heartbeat": {
            "status": heartbeat.status,
            "last_received_at_monotonic_ns": (
                heartbeat.last_received_at_monotonic_ns
            ),
            "age_nanoseconds": heartbeat.age_nanoseconds,
        },
        "limits": {
            "maximum_java_process_count": MAXIMUM_JAVA_PROCESS_COUNT,
            "per_process_current_phys_footprint_bytes": (
                PER_PROCESS_CEILING_BYTES
            ),
            "aggregate_current_phys_footprint_bytes": AGGREGATE_CEILING_BYTES,
        },
        "java_inventory": list(poll.inventory),
        "external_java_process_ids": list(poll.external_java_process_ids),
        "tracked_exact_identities": [
            _identity_payload(identity) for identity in poll.active_identities
        ],
        "samples": [_sample_payload(sample) for sample in poll.samples],
        "aggregate_current_phys_footprint_bytes": (
            poll.aggregate_current_phys_footprint_bytes
        ),
        "owned_group_absent": poll.owned_group_absent,
        "signal_actions": list(signal_actions),
        "terminal_attestation": terminal_attestation,
    }


def _json_bytes(payload: Mapping[str, object], maximum_size: int) -> bytes:
    content = (
        json.dumps(payload, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")
    if len(content) > maximum_size:
        raise LaunchWatchdogError("Watchdog artifact exceeded its byte bound")
    return content


def _artifact_exists(path: Path, directory_descriptor: int) -> bool:
    try:
        information = os.stat(
            path.name,
            dir_fd=directory_descriptor,
            follow_symlinks=False,
        )
    except FileNotFoundError:
        return False
    except OSError as exception:
        raise LaunchWatchdogError(
            f"Cannot inspect watchdog artifact {path}: {_bounded_detail(exception)}"
        ) from exception
    if stat.S_ISLNK(information.st_mode):
        raise LaunchWatchdogError(f"Refusing a linked watchdog artifact: {path}")
    return True


def _write_atomic(
    path: Path,
    content: bytes,
    directory_descriptor: int,
) -> None:
    if _artifact_exists(path, directory_descriptor):
        current_information = os.stat(
            path.name,
            dir_fd=directory_descriptor,
            follow_symlinks=False,
        )
        if (
            not stat.S_ISREG(current_information.st_mode)
            or current_information.st_uid != os.getuid()
            or stat.S_IMODE(current_information.st_mode) != 0o600
            or current_information.st_nlink != 1
        ):
            raise LaunchWatchdogError(f"Unsafe watchdog artifact: {path}")
    temporary_path = path.parent / f".{path.name}.{secrets.token_hex(16)}.tmp"
    descriptor = os.open(
        temporary_path.name,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o600,
        dir_fd=directory_descriptor,
    )
    try:
        with os.fdopen(descriptor, "wb") as handle:
            descriptor = -1
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(
            temporary_path.name,
            path.name,
            src_dir_fd=directory_descriptor,
            dst_dir_fd=directory_descriptor,
        )
        os.fsync(directory_descriptor)
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        try:
            os.unlink(temporary_path.name, dir_fd=directory_descriptor)
        except FileNotFoundError:
            pass


def _write_exclusive(
    path: Path,
    content: bytes,
    directory_descriptor: int,
) -> None:
    descriptor = os.open(
        path.name,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o600,
        dir_fd=directory_descriptor,
    )
    created_information = os.fstat(descriptor)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            descriptor = -1
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
    except Exception:
        if descriptor >= 0:
            os.close(descriptor)
        try:
            current_information = os.stat(
                path.name,
                dir_fd=directory_descriptor,
                follow_symlinks=False,
            )
            if (
                current_information.st_dev == created_information.st_dev
                and current_information.st_ino == created_information.st_ino
            ):
                os.unlink(path.name, dir_fd=directory_descriptor)
        except OSError:
            pass
        raise
    os.fsync(directory_descriptor)


def _read_json_artifact(
    path: Path,
    maximum_size: int,
    directory_descriptor: int,
) -> dict[str, object]:
    _validate_artifact_directory(path.parent, directory_descriptor)
    for _attempt in range(MAXIMUM_ARTIFACT_READ_ATTEMPTS):
        descriptor = -1
        try:
            descriptor = os.open(
                path.name,
                os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
                dir_fd=directory_descriptor,
            )
            information = os.fstat(descriptor)
            path_information = os.stat(
                path.name,
                dir_fd=directory_descriptor,
                follow_symlinks=False,
            )
            if (
                path_information.st_dev != information.st_dev
                or path_information.st_ino != information.st_ino
            ):
                continue
            if (
                not stat.S_ISREG(information.st_mode)
                or information.st_uid != os.getuid()
                or stat.S_IMODE(information.st_mode) != 0o600
                or information.st_nlink != 1
                or information.st_size <= 0
                or information.st_size > maximum_size
            ):
                raise LaunchWatchdogError(f"Unsafe watchdog artifact: {path}")
            content = os.pread(descriptor, maximum_size + 1, 0)
            final_path_information = os.stat(
                path.name,
                dir_fd=directory_descriptor,
                follow_symlinks=False,
            )
            if (
                final_path_information.st_dev != information.st_dev
                or final_path_information.st_ino != information.st_ino
            ):
                continue
            if len(content) != information.st_size or len(content) > maximum_size:
                raise LaunchWatchdogError(
                    f"Watchdog artifact changed while read: {path}"
                )
            payload = json.loads(content)
        except (OSError, json.JSONDecodeError) as exception:
            raise LaunchWatchdogError(
                f"Cannot read watchdog artifact {path}: {_bounded_detail(exception)}"
            ) from exception
        finally:
            if descriptor >= 0:
                os.close(descriptor)
        if not isinstance(payload, dict):
            raise LaunchWatchdogError(f"Watchdog artifact is not an object: {path}")
        return payload
    raise LaunchWatchdogError(f"Watchdog artifact changed repeatedly: {path}")


def _require_exact_fields(
    payload: Mapping[str, object],
    expected_fields: frozenset[str],
    description: str,
) -> None:
    if set(payload) != expected_fields:
        raise LaunchWatchdogError(f"The {description} field inventory changed")


def _require_bounded_string(
    value: object,
    description: str,
    *,
    allow_empty: bool = True,
) -> str:
    if (
        not isinstance(value, str)
        or (not allow_empty and not value)
        or len(value.encode("utf-8")) > MAXIMUM_ERROR_SIZE_BYTES
        or "\x00" in value
    ):
        raise LaunchWatchdogError(f"The {description} is invalid")
    return value


def _validated_identity_payload(
    payload: object,
    description: str,
) -> OwnedJavaProcess:
    if not isinstance(payload, dict):
        raise LaunchWatchdogError(f"The {description} is not an object")
    _require_exact_fields(payload, IDENTITY_FIELDS, description)
    try:
        return OwnedJavaProcess(
            pid=payload["pid"],
            process_group_id=payload["process_group_id"],
            proc_start_abstime=payload["proc_start_abstime"],
            expected_executable=payload["expected_executable"],
        )
    except (TypeError, ValueError) as exception:
        raise LaunchWatchdogError(f"The {description} is invalid") from exception


def _validated_process_id_inventory(
    payload: object,
    description: str,
    *,
    maximum_count: int,
) -> tuple[int, ...]:
    if not isinstance(payload, list) or len(payload) > maximum_count:
        raise LaunchWatchdogError(f"The {description} is not bounded")
    process_ids = tuple(payload)
    if (
        any(
            type(process_id) is not int
            or process_id <= 0
            or process_id > MAXIMUM_PROCESS_ID
            for process_id in process_ids
        )
        or tuple(sorted(process_ids)) != process_ids
        or len(set(process_ids)) != len(process_ids)
    ):
        raise LaunchWatchdogError(f"The {description} is invalid")
    return process_ids


def _validated_identity_inventory(
    payload: object,
    description: str,
) -> tuple[OwnedJavaProcess, ...]:
    if (
        not isinstance(payload, list)
        or len(payload) > MAXIMUM_REPORTED_JAVA_PROCESS_COUNT
    ):
        raise LaunchWatchdogError(f"The {description} is not bounded")
    identities = tuple(
        _validated_identity_payload(value, description) for value in payload
    )
    process_ids = tuple(identity.pid for identity in identities)
    if tuple(sorted(process_ids)) != process_ids or len(set(process_ids)) != len(
        process_ids
    ):
        raise LaunchWatchdogError(f"The {description} is invalid")
    return identities


def _validated_sample_payload(
    payload: object,
    description: str,
) -> tuple[int | None, str, str, int | None]:
    if not isinstance(payload, dict):
        raise LaunchWatchdogError(f"The {description} is not an object")
    _require_exact_fields(payload, SAMPLE_FIELDS, description)
    process_id = payload.get("pid")
    if process_id is not None and (
        type(process_id) is not int
        or process_id <= 0
        or process_id > MAXIMUM_PROCESS_ID
    ):
        raise LaunchWatchdogError(f"The {description} PID is invalid")
    source = payload.get("source")
    if source not in {value.value for value in SampleSource}:
        raise LaunchWatchdogError(f"The {description} source is invalid")
    status = payload.get("status")
    if status not in {value.value for value in SampleStatus}:
        raise LaunchWatchdogError(f"The {description} status is invalid")
    footprint = payload.get("current_phys_footprint_bytes")
    if footprint is not None and (type(footprint) is not int or footprint < 0):
        raise LaunchWatchdogError(f"The {description} footprint is invalid")
    _require_bounded_string(payload.get("detail"), f"{description} detail")
    return process_id, source, status, footprint


def _validated_exact_identity_sample_payload(
    payload: object,
    description: str,
) -> tuple[OwnedJavaProcess, str, str, int | None]:
    if not isinstance(payload, dict):
        raise LaunchWatchdogError(f"The {description} is not an object")
    _require_exact_fields(payload, EXACT_IDENTITY_SAMPLE_FIELDS, description)
    identity = _validated_identity_payload(
        payload.get("expected_identity"),
        f"{description} expected identity",
    )
    source = payload.get("source")
    if source not in {value.value for value in SampleSource}:
        raise LaunchWatchdogError(f"The {description} source is invalid")
    status = payload.get("status")
    if status not in {value.value for value in SampleStatus}:
        raise LaunchWatchdogError(f"The {description} status is invalid")
    footprint = payload.get("current_phys_footprint_bytes")
    if footprint is not None and (type(footprint) is not int or footprint < 0):
        raise LaunchWatchdogError(f"The {description} footprint is invalid")
    _require_bounded_string(payload.get("detail"), f"{description} detail")
    return identity, source, status, footprint


def _validate_readiness(
    handle: LaunchWatchdogHandle,
    *,
    monotonic_ns: Callable[[], int],
    require_enforcement_active: bool = True,
) -> dict[str, object]:
    if type(require_enforcement_active) is not bool:
        raise LaunchWatchdogError(
            "require_enforcement_active must be a boolean"
        )
    readiness = _read_json_artifact(
        handle.readiness_path,
        MAXIMUM_READINESS_SIZE_BYTES,
        handle.runtime_directory_descriptor,
    )
    _require_exact_fields(readiness, READINESS_FIELDS, "watchdog readiness")
    watchdog_pid = readiness.get("watchdog_pid")
    ready_at = readiness.get("ready_at_monotonic_ns")
    enforcement_active = readiness.get("enforcement_active")
    now = monotonic_ns()
    if (
        type(handle.process.pid) is not int
        or handle.process.pid <= 0
        or handle.process.pid > MAXIMUM_PROCESS_ID
        or type(watchdog_pid) is not int
        or watchdog_pid != handle.process.pid
        or type(readiness.get("controller_pid")) is not int
        or readiness.get("controller_pid") != handle.controller_pid
        or type(readiness.get("controller_process_group_id")) is not int
        or readiness.get("controller_process_group_id")
        != handle.controller_process_group_id
        or type(readiness.get("owned_session_id")) is not int
        or readiness.get("owned_session_id") != handle.session_id
        or type(readiness.get("heartbeat_timeout_nanoseconds")) is not int
        or readiness.get("heartbeat_timeout_nanoseconds")
        != handle.heartbeat_timeout_nanoseconds
        or type(ready_at) is not int
        or ready_at < 0
        or ready_at > now
        or readiness.get("schema")
        != "etherology-forge-server-launch-watchdog-ready-v1"
        or type(enforcement_active) is not bool
        or (require_enforcement_active and not enforcement_active)
    ):
        raise LaunchWatchdogError("The launch watchdog readiness is not enforceable")
    if (
        _validated_identity_payload(
            readiness.get("anchor"),
            "watchdog readiness anchor",
        )
        != handle.anchor
    ):
        raise LaunchWatchdogError("The launch watchdog readiness anchor changed")
    return readiness


def _validated_telemetry(
    handle: LaunchWatchdogHandle,
) -> tuple[
    dict[str, object],
    int,
    tuple[int, ...],
    tuple[int, ...],
    tuple[OwnedJavaProcess, ...],
    tuple[tuple[int | None, str, str, int | None], ...],
]:
    telemetry = _read_json_artifact(
        handle.telemetry_path,
        MAXIMUM_TELEMETRY_SIZE_BYTES,
        handle.runtime_directory_descriptor,
    )
    _require_exact_fields(telemetry, TELEMETRY_FIELDS, "watchdog telemetry")
    observed_at = telemetry.get("observed_at_monotonic_ns")
    if type(observed_at) is not int or observed_at < 0:
        raise LaunchWatchdogError("The watchdog telemetry timestamp is invalid")
    if (
        telemetry.get("schema") != "etherology-forge-server-launch-watchdog-v1"
        or type(telemetry.get("owned_session_id")) is not int
        or telemetry.get("owned_session_id") != handle.session_id
        or type(telemetry.get("controller_pid")) is not int
        or telemetry.get("controller_pid") != handle.controller_pid
        or type(telemetry.get("controller_process_group_id")) is not int
        or telemetry.get("controller_process_group_id")
        != handle.controller_process_group_id
    ):
        raise LaunchWatchdogError("The watchdog telemetry ownership changed")
    if (
        _validated_identity_payload(
            telemetry.get("anchor"),
            "watchdog telemetry anchor",
        )
        != handle.anchor
    ):
        raise LaunchWatchdogError("The watchdog telemetry anchor changed")

    limits = telemetry.get("limits")
    if not isinstance(limits, dict):
        raise LaunchWatchdogError("The watchdog telemetry limits are not an object")
    _require_exact_fields(limits, LIMIT_FIELDS, "watchdog telemetry limits")
    expected_limits = {
        "maximum_java_process_count": MAXIMUM_JAVA_PROCESS_COUNT,
        "per_process_current_phys_footprint_bytes": PER_PROCESS_CEILING_BYTES,
        "aggregate_current_phys_footprint_bytes": AGGREGATE_CEILING_BYTES,
    }
    if (
        any(type(value) is not int for value in limits.values())
        or limits != expected_limits
    ):
        raise LaunchWatchdogError("The watchdog telemetry limits changed")

    heartbeat = telemetry.get("controller_heartbeat")
    if not isinstance(heartbeat, dict):
        raise LaunchWatchdogError("The watchdog heartbeat is not an object")
    _require_exact_fields(heartbeat, HEARTBEAT_FIELDS, "watchdog heartbeat")
    heartbeat_status = _require_bounded_string(
        heartbeat.get("status"),
        "watchdog heartbeat status",
        allow_empty=False,
    )
    heartbeat_received_at = heartbeat.get("last_received_at_monotonic_ns")
    heartbeat_age = heartbeat.get("age_nanoseconds")
    if (
        type(heartbeat_received_at) is not int
        or heartbeat_received_at < 0
        or heartbeat_received_at > observed_at
        or type(heartbeat_age) is not int
        or heartbeat_age < 0
    ):
        raise LaunchWatchdogError("The watchdog heartbeat timing is invalid")
    if heartbeat_status not in {"starting", "healthy", "silent", "eof"} and not (
        heartbeat_status.startswith("read-error:")
        and len(heartbeat_status.encode("utf-8")) <= MAXIMUM_ERROR_SIZE_BYTES
    ):
        raise LaunchWatchdogError("The watchdog heartbeat status is invalid")

    inventory = _validated_process_id_inventory(
        telemetry.get("java_inventory"),
        "watchdog Java inventory",
        maximum_count=MAXIMUM_REPORTED_JAVA_PROCESS_COUNT,
    )
    external = _validated_process_id_inventory(
        telemetry.get("external_java_process_ids"),
        "watchdog external Java inventory",
        maximum_count=MAXIMUM_REPORTED_JAVA_PROCESS_COUNT,
    )
    if not set(external).issubset(inventory):
        raise LaunchWatchdogError("The external Java inventory is inconsistent")
    identities = _validated_identity_inventory(
        telemetry.get("tracked_exact_identities"),
        "watchdog exact identity inventory",
    )
    samples_payload = telemetry.get("samples")
    if (
        not isinstance(samples_payload, list)
        or len(samples_payload) > MAXIMUM_REPORTED_JAVA_PROCESS_COUNT
    ):
        raise LaunchWatchdogError("The watchdog sample inventory is not bounded")
    samples = tuple(
        _validated_sample_payload(sample, "watchdog memory sample")
        for sample in samples_payload
    )
    aggregate = telemetry.get("aggregate_current_phys_footprint_bytes")
    if type(aggregate) is not int or aggregate < 0:
        raise LaunchWatchdogError("The watchdog aggregate footprint is invalid")
    if type(telemetry.get("owned_group_absent")) is not bool:
        raise LaunchWatchdogError("The watchdog group attestation is invalid")
    signal_actions = telemetry.get("signal_actions")
    if (
        not isinstance(signal_actions, list)
        or len(signal_actions) > MAXIMUM_SIGNAL_ACTION_COUNT
    ):
        raise LaunchWatchdogError("The watchdog signal inventory is not bounded")
    for action in signal_actions:
        _require_bounded_string(
            action,
            "watchdog signal action",
            allow_empty=False,
        )
    return telemetry, observed_at, inventory, external, identities, samples


def _attest_owned_absence(
    engine: LaunchWatchdogEngine,
    *,
    observed_at: int,
) -> tuple[
    bool,
    tuple[tuple[OwnedJavaProcess, MemorySample], ...],
    str | None,
]:
    """Attests exact tracked identities and the historical group are absent."""

    samples: list[tuple[OwnedJavaProcess, MemorySample]] = []
    missing_process_ids: list[int] = []
    for process_id, identity in tuple(sorted(engine.known_identities.items())):
        try:
            sample = engine.sampler.sample(identity, observed_at)
        except Exception as exception:
            return (
                False,
                tuple(samples),
                f"terminal-sample-error: {_bounded_detail(exception)}",
            )
        samples.append((identity, sample))
        if sample.status is SampleStatus.MISSING:
            missing_process_ids.append(process_id)
        elif sample.status is not SampleStatus.AVAILABLE:
            return False, tuple(samples), f"terminal-sample-{sample.status.value}"
    try:
        group_absent = not engine.group_exists_reader(engine.anchor.process_group_id)
    except Exception as exception:
        return (
            False,
            tuple(samples),
            f"terminal-group-attestation-error: {_bounded_detail(exception)}",
        )
    if group_absent:
        for process_id in missing_process_ids:
            engine.identities.pop(process_id, None)
        if engine.anchor.pid in missing_process_ids:
            engine.anchor_continuity_active = False
            engine.final_loss_discovery_consumed = True
    return group_absent and not engine.identities, tuple(samples), None


def _poll_engine_safely(engine: LaunchWatchdogEngine) -> WatchdogPoll:
    try:
        return engine.poll()
    except Exception as exception:
        return WatchdogPoll(
            reason=f"watchdog-poll-error: {_bounded_detail(exception)}",
            owned_group_absent=False,
            inventory=(),
            external_java_process_ids=(),
            samples=(),
            active_identities=tuple(
                engine.identities[process_id]
                for process_id in sorted(engine.identities)
            ),
            aggregate_current_phys_footprint_bytes=0,
        )


def _observe_heartbeat(
    descriptor: int,
    last_received_at: int,
    heartbeat_timeout_ns: int,
    monotonic_ns: Callable[[], int],
) -> HeartbeatObservation:
    now = monotonic_ns()
    try:
        readable, _, _ = select.select([descriptor], [], [], 0)
    except (OSError, ValueError) as exception:
        return HeartbeatObservation(
            f"read-error:{_bounded_detail(exception)}",
            last_received_at,
            max(0, now - last_received_at),
        )
    if readable:
        try:
            content = os.read(descriptor, 4096)
        except OSError as exception:
            return HeartbeatObservation(
                f"read-error:{_bounded_detail(exception)}",
                last_received_at,
                max(0, now - last_received_at),
            )
        if not content:
            return HeartbeatObservation(
                "eof",
                last_received_at,
                max(0, now - last_received_at),
            )
        last_received_at = now
    age = max(0, now - last_received_at)
    status = "healthy" if age <= heartbeat_timeout_ns else "silent"
    return HeartbeatObservation(status, last_received_at, age)


def monitor_launch(
    anchor: OwnedJavaProcess,
    session_id: int,
    controller_pid: int,
    controller_process_group_id: int,
    heartbeat_read_descriptor: int,
    runtime_directory_descriptor: int,
    readiness_path: Path,
    telemetry_path: Path,
    *,
    heartbeat_timeout_seconds: float = DEFAULT_HEARTBEAT_TIMEOUT_SECONDS,
    sampler: MacOsProcessMemorySampler | None = None,
    engine_factory: Callable[..., LaunchWatchdogEngine] = LaunchWatchdogEngine,
    monotonic_ns: Callable[[], int] = time.monotonic_ns,
    sleep: Callable[[float], None] = time.sleep,
    signal_launch: Callable[..., str] = signal_owned_launch,
) -> int:
    """Runs persistent supervision until the launch is absent and attested."""

    _require_positive_finite_seconds(
        heartbeat_timeout_seconds,
        "heartbeat_timeout_seconds",
    )
    _require_positive_integer(controller_pid, "controller_pid")
    _require_positive_integer(
        controller_process_group_id,
        "controller_process_group_id",
    )
    if type(heartbeat_read_descriptor) is not int or heartbeat_read_descriptor < 3:
        raise LaunchWatchdogError("heartbeat_read_descriptor must be a dedicated FD")
    if (
        type(runtime_directory_descriptor) is not int
        or runtime_directory_descriptor < 3
        or runtime_directory_descriptor == heartbeat_read_descriptor
    ):
        raise LaunchWatchdogError(
            "runtime_directory_descriptor must be a distinct dedicated FD"
        )
    try:
        heartbeat_information = os.fstat(heartbeat_read_descriptor)
    except OSError as exception:
        raise LaunchWatchdogError(
            f"Cannot inspect the heartbeat descriptor: {_bounded_detail(exception)}"
        ) from exception
    if not stat.S_ISFIFO(heartbeat_information.st_mode):
        raise LaunchWatchdogError("The heartbeat descriptor must be one pipe")
    _validate_artifact_directory(
        readiness_path.parent,
        runtime_directory_descriptor,
    )
    if (
        telemetry_path.parent != readiness_path.parent
        or readiness_path.name != READINESS_FILE_NAME
        or telemetry_path.name != TELEMETRY_FILE_NAME
    ):
        raise LaunchWatchdogError("Watchdog artifacts must share one runtime directory")
    if _artifact_exists(readiness_path, runtime_directory_descriptor):
        raise LaunchWatchdogError("Refusing to replace watchdog readiness")
    if _artifact_exists(telemetry_path, runtime_directory_descriptor):
        raise LaunchWatchdogError("Refusing to replace watchdog telemetry")

    selected_sampler = sampler or MacOsProcessMemorySampler.native()
    engine = engine_factory(anchor, session_id, selected_sampler)
    heartbeat_timeout_ns = int(heartbeat_timeout_seconds * 1_000_000_000)
    started_at = monotonic_ns()
    heartbeat = HeartbeatObservation("healthy", started_at, 0)
    initial_poll = _poll_engine_safely(engine)
    initial_reason = initial_poll.reason
    if initial_reason is None and anchor.pid not in {
        identity.pid for identity in initial_poll.active_identities
    }:
        initial_reason = "anchor-missing-before-readiness"

    initial_telemetry_error: str | None = None
    initial_payload = _telemetry_payload(
        anchor,
        session_id,
        controller_pid,
        controller_process_group_id,
        heartbeat,
        initial_poll,
        status="terminating" if initial_reason is not None else "running",
        decision="terminate" if initial_reason is not None else "continue",
        reason=initial_reason,
        signal_actions=(),
        terminal_attestation=None,
        observed_at_monotonic_ns=monotonic_ns(),
    )
    try:
        _write_atomic(
            telemetry_path,
            _json_bytes(initial_payload, MAXIMUM_TELEMETRY_SIZE_BYTES),
            runtime_directory_descriptor,
        )
    except Exception as exception:
        initial_telemetry_error = (
            f"telemetry-write-error: {_bounded_detail(exception)}"
        )
    initial_reason = initial_reason or initial_telemetry_error

    readiness_payload = {
        "schema": "etherology-forge-server-launch-watchdog-ready-v1",
        "watchdog_pid": os.getpid(),
        "controller_pid": controller_pid,
        "controller_process_group_id": controller_process_group_id,
        "anchor": _identity_payload(anchor),
        "owned_session_id": session_id,
        "heartbeat_timeout_nanoseconds": heartbeat_timeout_ns,
        "ready_at_monotonic_ns": monotonic_ns(),
        "enforcement_active": initial_reason is None,
    }
    readiness_error: str | None = None
    try:
        _write_exclusive(
            readiness_path,
            _json_bytes(readiness_payload, MAXIMUM_READINESS_SIZE_BYTES),
            runtime_directory_descriptor,
        )
    except Exception as exception:
        readiness_error = f"readiness-write-error: {_bounded_detail(exception)}"

    failure_reason = initial_reason or readiness_error
    poll = initial_poll
    decision = "terminate" if failure_reason is not None else "continue"
    signal_actions: deque[str] = deque(maxlen=MAXIMUM_SIGNAL_ACTION_COUNT)
    termination_started_at: int | None = None
    last_kill_at: int | None = None
    while True:
        if failure_reason is None:
            heartbeat = _observe_heartbeat(
                heartbeat_read_descriptor,
                heartbeat.last_received_at_monotonic_ns,
                heartbeat_timeout_ns,
                monotonic_ns,
            )
            if heartbeat.status != "healthy":
                heartbeat_arrived_after_absence, _samples, _sample_error = (
                    _attest_owned_absence(
                        engine,
                        observed_at=monotonic_ns(),
                    )
                )
                if not heartbeat_arrived_after_absence:
                    poll = _poll_engine_safely(engine)
                    failure_reason = f"controller-heartbeat-{heartbeat.status}"
                    decision = "terminate"
            elif poll.reason is not None:
                failure_reason = poll.reason
                decision = "terminate"

        if failure_reason is not None:
            now = monotonic_ns()
            if termination_started_at is None:
                termination_started_at = now
                try:
                    signal_action = signal_launch(
                        poll.active_identities,
                        anchor,
                        session_id,
                        selected_sampler,
                        signal.SIGTERM,
                        controller_pid=controller_pid,
                        controller_process_group_id=controller_process_group_id,
                    )
                except Exception as exception:
                    signal_action = f"signal-error:{_bounded_detail(exception)}"
                signal_actions.append(signal_action)
            elif (
                (last_kill_at is None or now - last_kill_at >= heartbeat_timeout_ns)
                and now - termination_started_at
                >= int(TERMINATION_GRACE_SECONDS * 1_000_000_000)
            ):
                try:
                    signal_action = signal_launch(
                        poll.active_identities,
                        anchor,
                        session_id,
                        selected_sampler,
                        signal.SIGKILL,
                        controller_pid=controller_pid,
                        controller_process_group_id=controller_process_group_id,
                    )
                except Exception as exception:
                    signal_action = f"signal-error:{_bounded_detail(exception)}"
                signal_actions.append(signal_action)
                last_kill_at = now

        observed_at = monotonic_ns()
        payload = _telemetry_payload(
            anchor,
            session_id,
            controller_pid,
            controller_process_group_id,
            heartbeat,
            poll,
            status="terminating" if failure_reason is not None else "running",
            decision=decision,
            reason=failure_reason,
            signal_actions=signal_actions,
            terminal_attestation=None,
            observed_at_monotonic_ns=observed_at,
        )
        try:
            _write_atomic(
                telemetry_path,
                _json_bytes(payload, MAXIMUM_TELEMETRY_SIZE_BYTES),
                runtime_directory_descriptor,
            )
        except Exception as exception:
            if failure_reason is None:
                failure_reason = (
                    f"telemetry-write-error: {_bounded_detail(exception)}"
                )
                decision = "terminate"

        terminal_owned_absent, terminal_samples, terminal_sample_error = (
            _attest_owned_absence(engine, observed_at=monotonic_ns())
        )
        if terminal_owned_absent:
            terminal_inventory: tuple[int, ...]
            terminal_inventory_error: str | None = None
            try:
                terminal_inventory = engine.inventory_reader()
            except Exception as exception:
                terminal_inventory = ()
                terminal_inventory_error = _bounded_detail(exception)
            terminal = {
                "owned_group_absent": True,
                "tracked_identities_absent": True,
                "global_java_inventory": list(terminal_inventory),
                "global_java_inventory_error": terminal_inventory_error,
                "external_java_remained": (
                    None
                    if terminal_inventory_error is not None
                    else bool(terminal_inventory)
                ),
                "exact_identity_samples": [
                    _exact_identity_sample_payload(identity, sample)
                    for identity, sample in terminal_samples
                ],
                "exact_identity_sample_error": terminal_sample_error,
            }
            final_reason = failure_reason
            if final_reason is None and (
                terminal_inventory_error is not None or terminal_inventory
            ):
                final_reason = "terminal-global-java-inventory-not-empty"
            final_poll = WatchdogPoll(
                reason=None,
                owned_group_absent=True,
                inventory=terminal_inventory,
                external_java_process_ids=terminal_inventory,
                samples=tuple(sample for _identity, sample in terminal_samples),
                active_identities=(),
                aggregate_current_phys_footprint_bytes=0,
            )
            final_payload = _telemetry_payload(
                anchor,
                session_id,
                controller_pid,
                controller_process_group_id,
                heartbeat,
                final_poll,
                status="normal" if final_reason is None else "failed",
                decision="exit",
                reason=final_reason,
                signal_actions=signal_actions,
                terminal_attestation=terminal,
                observed_at_monotonic_ns=monotonic_ns(),
            )
            _write_atomic(
                telemetry_path,
                _json_bytes(final_payload, MAXIMUM_TELEMETRY_SIZE_BYTES),
                runtime_directory_descriptor,
            )
            return 0 if final_reason is None else 1

        sleep(POLL_INTERVAL_SECONDS)
        poll = _poll_engine_safely(engine)
        if failure_reason is None and poll.reason is not None:
            failure_reason = poll.reason
            decision = "terminate"


def _watchdog_command(
    anchor: OwnedJavaProcess,
    session_id: int,
    controller_pid: int,
    controller_process_group_id: int,
    heartbeat_read_descriptor: int,
    runtime_directory_descriptor: int,
    readiness_path: Path,
    telemetry_path: Path,
    heartbeat_timeout_seconds: float,
) -> list[str]:
    return [
        sys.executable,
        "-B",
        str(Path(__file__).resolve()),
        "monitor",
        "--anchor-pid",
        str(anchor.pid),
        "--anchor-pgid",
        str(anchor.process_group_id),
        "--anchor-start-abstime",
        str(anchor.proc_start_abstime),
        "--anchor-executable",
        anchor.expected_executable,
        "--session-id",
        str(session_id),
        "--controller-pid",
        str(controller_pid),
        "--controller-pgid",
        str(controller_process_group_id),
        "--heartbeat-read-fd",
        str(heartbeat_read_descriptor),
        "--runtime-directory-fd",
        str(runtime_directory_descriptor),
        "--heartbeat-timeout-seconds",
        str(heartbeat_timeout_seconds),
        "--readiness-path",
        str(readiness_path),
        "--telemetry-path",
        str(telemetry_path),
    ]


def start_launch_watchdog(
    anchor: OwnedJavaProcess,
    session_id: int,
    runtime_directory: Path,
    *,
    heartbeat_timeout_seconds: float = DEFAULT_HEARTBEAT_TIMEOUT_SECONDS,
    readiness_timeout_seconds: float = DEFAULT_READINESS_TIMEOUT_SECONDS,
    popen_factory: Callable[..., subprocess.Popen[bytes]] = subprocess.Popen,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> LaunchWatchdogHandle:
    """Spawns a detached watchdog and waits for bounded enforceable readiness."""

    _require_positive_integer(session_id, "session_id")
    _require_positive_finite_seconds(
        heartbeat_timeout_seconds,
        "heartbeat_timeout_seconds",
    )
    _require_positive_finite_seconds(
        readiness_timeout_seconds,
        "readiness_timeout_seconds",
    )
    runtime_directory_descriptor = _open_artifact_directory(runtime_directory)
    readiness_path = runtime_directory / READINESS_FILE_NAME
    telemetry_path = runtime_directory / TELEMETRY_FILE_NAME
    try:
        for path in (readiness_path, telemetry_path):
            if _artifact_exists(path, runtime_directory_descriptor):
                raise LaunchWatchdogError(
                    f"Refusing to replace watchdog artifact: {path}"
                )
        read_descriptor, write_descriptor = os.pipe()
    except BaseException:
        os.close(runtime_directory_descriptor)
        raise
    try:
        os.set_blocking(write_descriptor, False)
    except BaseException:
        os.close(read_descriptor)
        os.close(write_descriptor)
        os.close(runtime_directory_descriptor)
        raise
    process: subprocess.Popen[bytes] | None = None
    handle: LaunchWatchdogHandle | None = None
    controller_pid = os.getpid()
    controller_process_group_id = os.getpgrp()
    try:
        process = popen_factory(
            _watchdog_command(
                anchor,
                session_id,
                controller_pid,
                controller_process_group_id,
                read_descriptor,
                runtime_directory_descriptor,
                readiness_path,
                telemetry_path,
                heartbeat_timeout_seconds,
            ),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            close_fds=True,
            pass_fds=(read_descriptor, runtime_directory_descriptor),
            start_new_session=True,
        )
        handle = LaunchWatchdogHandle(
            process,
            write_descriptor,
            anchor,
            session_id,
            controller_pid,
            controller_process_group_id,
            int(heartbeat_timeout_seconds * 1_000_000_000),
            runtime_directory_descriptor,
            readiness_path,
            telemetry_path,
        )
        parent_read_descriptor = read_descriptor
        read_descriptor = -1
        os.close(parent_read_descriptor)
        send_launch_watchdog_heartbeat(handle)
        deadline = monotonic() + readiness_timeout_seconds
        while monotonic() < deadline:
            if _artifact_exists(readiness_path, runtime_directory_descriptor):
                verify_launch_watchdog(handle)
                return handle
            if process.poll() is not None:
                raise LaunchWatchdogError(
                    "The launch watchdog exited before readiness"
                )
            send_launch_watchdog_heartbeat(handle)
            sleep(0.05)
        raise LaunchWatchdogError("The launch watchdog readiness timed out")
    except BaseException as exception:
        if handle is not None:
            handle.close_heartbeat()
            write_descriptor = -1
        elif write_descriptor >= 0:
            try:
                os.close(write_descriptor)
            except OSError:
                pass
            write_descriptor = -1
        if process is not None:
            try:
                process.wait(timeout=DEFAULT_REAP_TIMEOUT_SECONDS)
            except BaseException:
                pass
        if handle is not None:
            raise LaunchWatchdogStartError(
                f"The launch watchdog failed before readiness: {_bounded_detail(exception)}",
                handle,
            ) from exception
        os.close(runtime_directory_descriptor)
        raise
    finally:
        if read_descriptor >= 0:
            os.close(read_descriptor)


def send_launch_watchdog_heartbeat(handle: LaunchWatchdogHandle) -> None:
    """Refreshes the controller lease held by one persistent watchdog."""

    if handle.heartbeat_write_descriptor < 0:
        raise LaunchWatchdogError("The launch watchdog heartbeat is closed")
    try:
        os.write(handle.heartbeat_write_descriptor, b"H")
    except BlockingIOError:
        return
    except OSError as exception:
        raise LaunchWatchdogError(
            f"Cannot send launch watchdog heartbeat: {_bounded_detail(exception)}"
        ) from exception


def _validate_open_watchdog_heartbeat(handle: LaunchWatchdogHandle) -> None:
    if handle.heartbeat_write_descriptor < 3:
        raise LaunchWatchdogError("The launch watchdog heartbeat is not open")
    try:
        heartbeat_information = os.fstat(handle.heartbeat_write_descriptor)
    except OSError as exception:
        raise LaunchWatchdogError(
            f"Cannot inspect the watchdog heartbeat: {_bounded_detail(exception)}"
        ) from exception
    if not stat.S_ISFIFO(heartbeat_information.st_mode):
        raise LaunchWatchdogError("The watchdog heartbeat is not one pipe")


def _verify_running_launch_watchdog(
    handle: LaunchWatchdogHandle,
    *,
    monotonic_ns: Callable[[], int],
) -> tuple[dict[str, object], dict[str, object]]:
    _validate_open_watchdog_heartbeat(handle)
    readiness = _validate_readiness(handle, monotonic_ns=monotonic_ns)
    (
        telemetry,
        observed_at,
        inventory,
        external,
        identities,
        samples,
    ) = _validated_telemetry(handle)
    age = monotonic_ns() - observed_at
    heartbeat = telemetry["controller_heartbeat"]
    heartbeat_age = heartbeat["age_nanoseconds"]
    identity_process_ids = tuple(identity.pid for identity in identities)
    sample_process_ids = tuple(sample[0] for sample in samples)
    sample_footprints = tuple(sample[3] for sample in samples)
    if (
        age < 0
        or age > MAXIMUM_TELEMETRY_AGE_NANOSECONDS
        or telemetry.get("status") != "running"
        or telemetry.get("decision") != "continue"
        or telemetry.get("reason") is not None
        or heartbeat["status"] != "healthy"
        or heartbeat_age > handle.heartbeat_timeout_nanoseconds
        or not inventory
        or len(inventory) > MAXIMUM_JAVA_PROCESS_COUNT
        or external
        or identity_process_ids != inventory
        or handle.anchor.pid not in identity_process_ids
        or any(
            identity.process_group_id != handle.anchor.process_group_id
            for identity in identities
        )
        or sample_process_ids != inventory
        or any(
            source != SampleSource.PROC_PID_RUSAGE_V4.value
            or status != SampleStatus.AVAILABLE.value
            or type(footprint) is not int
            or footprint > PER_PROCESS_CEILING_BYTES
            for _process_id, source, status, footprint in samples
        )
        or telemetry.get("aggregate_current_phys_footprint_bytes")
        != sum(sample_footprints)
        or telemetry.get("aggregate_current_phys_footprint_bytes")
        > AGGREGATE_CEILING_BYTES
        or telemetry.get("owned_group_absent") is not False
        or telemetry.get("signal_actions") != []
        or telemetry.get("terminal_attestation") is not None
    ):
        raise LaunchWatchdogError("The launch watchdog telemetry is not enforcing")
    return readiness, telemetry


def verify_launch_watchdog(
    handle: LaunchWatchdogHandle,
    *,
    monotonic_ns: Callable[[], int] = time.monotonic_ns,
) -> dict[str, object]:
    """Verifies bounded readiness and current enforcing telemetry."""

    if handle.process.poll() is not None:
        raise LaunchWatchdogError("The launch watchdog is not running")
    return _verify_running_launch_watchdog(
        handle,
        monotonic_ns=monotonic_ns,
    )[0]


def _signal_action_is_exact(action: str) -> bool:
    if action.startswith("signal-error:"):
        return len(action) > len("signal-error:")
    prefix, separator, counters = action.rpartition(";exact-identities-signaled:")
    if not separator:
        return False
    signaled_value, error_separator, error_value = counters.partition(";errors:")
    if (
        not error_separator
        or not signaled_value.isascii()
        or not signaled_value.isdecimal()
        or not error_value.isascii()
        or not error_value.isdecimal()
    ):
        return False
    signaled_count = int(signaled_value)
    error_count = int(error_value)
    if (
        signaled_value != str(signaled_count)
        or error_value != str(error_count)
        or signaled_count + error_count > MAXIMUM_TRACKED_JAVA_PROCESS_COUNT
    ):
        return False
    return prefix in {
        "owned-group-signaled",
        "owned-group-already-missing",
        "owned-group-not-signaled",
    } or (
        prefix.startswith("owned-group-signal-error:")
        and len(prefix) > len("owned-group-signal-error:")
    )


def _terminating_sample_is_exact(
    sample: tuple[int | None, str, str, int | None],
    identity_process_ids: frozenset[int],
) -> bool:
    process_id, source, status, footprint = sample
    if status == SampleStatus.AVAILABLE.value:
        return process_id in identity_process_ids and (
            (
                source == SampleSource.PROC_PID_RUSAGE_V4.value
                and type(footprint) is int
            )
            or (source == SampleSource.FALLBACK.value and footprint is None)
        )
    if process_id is not None:
        return False
    if status in {SampleStatus.MISSING.value, SampleStatus.ERROR.value}:
        return footprint is None
    return (
        status == SampleStatus.IDENTITY_DRIFT.value
        and (
            source == SampleSource.PROC_PID_RUSAGE_V4.value
            or footprint is None
        )
    )


def _verify_terminating_launch_watchdog(
    handle: LaunchWatchdogHandle,
    *,
    monotonic_ns: Callable[[], int],
) -> dict[str, object]:
    _validate_open_watchdog_heartbeat(handle)
    _validate_readiness(handle, monotonic_ns=monotonic_ns)
    (
        telemetry,
        observed_at,
        inventory,
        external,
        identities,
        samples,
    ) = _validated_telemetry(handle)
    age = monotonic_ns() - observed_at
    reason = _require_bounded_string(
        telemetry.get("reason"),
        "watchdog terminating reason",
        allow_empty=False,
    )
    heartbeat = telemetry["controller_heartbeat"]
    heartbeat_status = heartbeat["status"]
    heartbeat_age = heartbeat["age_nanoseconds"]
    signal_actions = telemetry["signal_actions"]
    group_absent = telemetry["owned_group_absent"]
    identity_process_ids = frozenset(identity.pid for identity in identities)
    sample_process_ids = tuple(
        process_id
        for process_id, _source, _status, _footprint in samples
        if process_id is not None
    )
    sample_footprint_sum = sum(
        footprint
        for _process_id, _source, _status, footprint in samples
        if footprint is not None
    )
    aggregate = telemetry["aggregate_current_phys_footprint_bytes"]
    if (
        age < 0
        or age > MAXIMUM_TELEMETRY_AGE_NANOSECONDS
        or telemetry.get("status") != "terminating"
        or telemetry.get("decision") != "terminate"
        or heartbeat_status == "starting"
        or (
            heartbeat_status == "healthy"
            and heartbeat_age > handle.heartbeat_timeout_nanoseconds
        )
        or (
            heartbeat_status == "silent"
            and heartbeat_age <= handle.heartbeat_timeout_nanoseconds
        )
        or (
            heartbeat_status != "healthy"
            and reason != f"controller-heartbeat-{heartbeat_status}"
        )
        or (
            heartbeat_status == "healthy"
            and reason.startswith("controller-heartbeat-")
        )
        or len(identities) > MAXIMUM_TRACKED_JAVA_PROCESS_COUNT
        or any(
            identity.process_group_id != handle.anchor.process_group_id
            for identity in identities
        )
        or len(set(sample_process_ids)) != len(sample_process_ids)
        or any(
            not _terminating_sample_is_exact(sample, identity_process_ids)
            for sample in samples
        )
        or aggregate not in {0, sample_footprint_sum}
        or (
            group_absent
            and (
                inventory
                or external
                or identities
                or samples
                or telemetry.get("aggregate_current_phys_footprint_bytes") != 0
            )
        )
        or not signal_actions
        or any(not _signal_action_is_exact(action) for action in signal_actions)
        or telemetry.get("terminal_attestation") is not None
    ):
        raise LaunchWatchdogError(
            "The launch watchdog terminating telemetry is not fail-closed"
        )
    return telemetry


def _verify_normal_transition_terminal(
    handle: LaunchWatchdogHandle,
    *,
    monotonic_ns: Callable[[], int],
) -> dict[str, object]:
    telemetry = verify_terminal_launch_watchdog(
        handle,
        require_normal_exit=False,
        monotonic_ns=monotonic_ns,
    )
    if telemetry.get("status") != "normal":
        raise LaunchWatchdogError(
            "The launch watchdog reached a failed terminal state: "
            f"{telemetry.get('reason')}"
        )
    return telemetry


def verify_launch_watchdog_transition(
    handle: LaunchWatchdogHandle,
    *,
    monotonic_ns: Callable[[], int] = time.monotonic_ns,
) -> dict[str, object]:
    """Validates live transition or normal terminal state after the leader exits.

    The controller remains responsible for sending heartbeats while this verifier
    reports a live state.
    """

    if handle.process.poll() is not None:
        return _verify_normal_transition_terminal(
            handle,
            monotonic_ns=monotonic_ns,
        )
    try:
        _readiness, telemetry = _verify_running_launch_watchdog(
            handle,
            monotonic_ns=monotonic_ns,
        )
    except LaunchWatchdogError as running_error:
        if handle.process.poll() is not None:
            return _verify_normal_transition_terminal(
                handle,
                monotonic_ns=monotonic_ns,
            )
        try:
            telemetry = _verify_terminating_launch_watchdog(
                handle,
                monotonic_ns=monotonic_ns,
            )
        except LaunchWatchdogError as terminating_error:
            if handle.process.poll() is not None:
                return _verify_normal_transition_terminal(
                    handle,
                    monotonic_ns=monotonic_ns,
                )
            raise LaunchWatchdogError(
                "The launch watchdog has no valid live transition state: "
                f"{_bounded_detail(terminating_error)}"
            ) from running_error
    if handle.process.poll() is not None:
        return _verify_normal_transition_terminal(
            handle,
            monotonic_ns=monotonic_ns,
        )
    return telemetry


def verify_terminal_launch_watchdog(
    handle: LaunchWatchdogHandle,
    *,
    require_normal_exit: bool,
    monotonic_ns: Callable[[], int] = time.monotonic_ns,
) -> dict[str, object]:
    """Requires a reaped watchdog and exact global Java absence attestation."""

    if type(require_normal_exit) is not bool:
        raise LaunchWatchdogError("require_normal_exit must be a boolean")
    return_code = handle.process.poll()
    if type(return_code) is not int or return_code not in (0, 1):
        raise LaunchWatchdogError("The launch watchdog is not safely reaped")
    if (
        type(handle.process.pid) is not int
        or handle.process.pid <= 0
        or handle.process.pid > MAXIMUM_PROCESS_ID
    ):
        raise LaunchWatchdogError("The launch watchdog PID is invalid")
    telemetry, observed_at, inventory, external, identities, samples = (
        _validated_telemetry(handle)
    )
    if observed_at > monotonic_ns():
        raise LaunchWatchdogError("The watchdog terminal timestamp is in the future")
    reason = telemetry.get("reason")
    failed_reason_valid = False
    if isinstance(reason, str):
        try:
            _require_bounded_string(
                reason,
                "watchdog terminal failure reason",
                allow_empty=False,
            )
        except LaunchWatchdogError:
            failed_reason_valid = False
        else:
            failed_reason_valid = True
    normal = (
        return_code == 0
        and telemetry.get("status") == "normal"
        and reason is None
    )
    failed = (
        return_code == 1
        and telemetry.get("status") == "failed"
        and failed_reason_valid
    )
    if (
        telemetry.get("decision") != "exit"
        or not (normal or failed)
        or (require_normal_exit and not normal)
    ):
        raise LaunchWatchdogError("The launch watchdog terminal status is invalid")
    readiness: dict[str, object] | None = None
    if normal:
        readiness = _validate_readiness(handle, monotonic_ns=monotonic_ns)
    elif _artifact_exists(
        handle.readiness_path,
        handle.runtime_directory_descriptor,
    ):
        readiness = _validate_readiness(
            handle,
            monotonic_ns=monotonic_ns,
            require_enforcement_active=False,
        )

    terminal = telemetry.get("terminal_attestation")
    if not isinstance(terminal, dict):
        raise LaunchWatchdogError("The watchdog terminal attestation is absent")
    _require_exact_fields(
        terminal,
        TERMINAL_ATTESTATION_FIELDS,
        "watchdog terminal attestation",
    )
    terminal_samples = terminal.get("exact_identity_samples")
    if (
        not isinstance(terminal_samples, list)
        or len(terminal_samples) > MAXIMUM_REPORTED_JAVA_PROCESS_COUNT
    ):
        raise LaunchWatchdogError("The terminal exact samples are not bounded")
    validated_terminal_samples = tuple(
        _validated_exact_identity_sample_payload(
            sample,
            "terminal exact identity sample",
        )
        for sample in terminal_samples
    )
    terminal_identity_process_ids = tuple(
        sample[0].pid for sample in validated_terminal_samples
    )
    terminal_identities = tuple(sample[0] for sample in validated_terminal_samples)
    if (
        inventory
        or external
        or identities
        or len(samples) != len(validated_terminal_samples)
        or telemetry.get("aggregate_current_phys_footprint_bytes") != 0
        or telemetry.get("owned_group_absent") is not True
        or terminal.get("owned_group_absent") is not True
        or terminal.get("tracked_identities_absent") is not True
        or terminal.get("global_java_inventory") != []
        or terminal.get("global_java_inventory_error") is not None
        or terminal.get("external_java_remained") is not False
        or terminal.get("exact_identity_sample_error") is not None
        or not validated_terminal_samples
        or tuple(sorted(terminal_identity_process_ids))
        != terminal_identity_process_ids
        or len(set(terminal_identity_process_ids))
        != len(terminal_identity_process_ids)
        or handle.anchor.pid not in terminal_identity_process_ids
        or handle.anchor not in terminal_identities
        or any(
            identity.process_group_id != handle.anchor.process_group_id
            for identity in terminal_identities
        )
        or any(
            source != SampleSource.PROC_PID_RUSAGE_V4.value
            or status != SampleStatus.MISSING.value
            or footprint is not None
            for _process_id, source, status, footprint in samples
        )
        or any(
            source != SampleSource.PROC_PID_RUSAGE_V4.value
            or status != SampleStatus.MISSING.value
            or footprint is not None
            for _identity, source, status, footprint in validated_terminal_samples
        )
    ):
        raise LaunchWatchdogError("The watchdog terminal absence proof is invalid")
    if normal and telemetry.get("signal_actions") != []:
        raise LaunchWatchdogError("A normal watchdog exit cannot contain stop actions")
    verified_contents = {
        TELEMETRY_FILE_NAME: _json_bytes(
            telemetry,
            MAXIMUM_TELEMETRY_SIZE_BYTES,
        ),
    }
    if readiness is not None:
        verified_contents[READINESS_FILE_NAME] = _json_bytes(
            readiness,
            MAXIMUM_READINESS_SIZE_BYTES,
        )
    handle.verified_terminal_artifact_contents = verified_contents
    return telemetry


def finish_launch_watchdog(
    handle: LaunchWatchdogHandle,
    *,
    require_normal_exit: bool,
    timeout_seconds: float = DEFAULT_REAP_TIMEOUT_SECONDS,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> int:
    """Maintains the lease until terminal proof is written, then verifies it."""

    if type(require_normal_exit) is not bool:
        raise LaunchWatchdogError("require_normal_exit must be a boolean")
    _require_positive_finite_seconds(timeout_seconds, "timeout_seconds")
    deadline = monotonic() + timeout_seconds
    while True:
        return_code = handle.process.poll()
        if return_code is not None:
            break
        if monotonic() >= deadline:
            handle.close_heartbeat()
            raise LaunchWatchdogError(
                "The launch watchdog did not publish terminal attestation"
            )
        try:
            send_launch_watchdog_heartbeat(handle)
        except LaunchWatchdogError:
            return_code = handle.process.poll()
            if return_code is None:
                handle.close_heartbeat()
                raise
            break
        sleep(0.05)
    handle.close_heartbeat()
    verify_terminal_launch_watchdog(
        handle,
        require_normal_exit=require_normal_exit,
    )
    return return_code


def reap_launch_watchdog(
    handle: LaunchWatchdogHandle,
    *,
    timeout_seconds: float = DEFAULT_REAP_TIMEOUT_SECONDS,
) -> int:
    """Waits a bounded interval for a watchdog that has reached terminal state."""

    _require_positive_finite_seconds(timeout_seconds, "timeout_seconds")
    try:
        return handle.process.wait(timeout=timeout_seconds)
    except subprocess.TimeoutExpired as exception:
        raise LaunchWatchdogError(
            "The launch watchdog did not reach terminal attestation"
        ) from exception


def stop_launch_watchdog(
    handle: LaunchWatchdogHandle,
    *,
    timeout_seconds: float = DEFAULT_REAP_TIMEOUT_SECONDS,
) -> int:
    """Ends the heartbeat lease and reaps only after fail-closed supervision."""

    handle.close_heartbeat()
    return reap_launch_watchdog(handle, timeout_seconds=timeout_seconds)


def _parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("monitor",))
    parser.add_argument("--anchor-pid", required=True, type=int)
    parser.add_argument("--anchor-pgid", required=True, type=int)
    parser.add_argument("--anchor-start-abstime", required=True, type=int)
    parser.add_argument("--anchor-executable", required=True)
    parser.add_argument("--session-id", required=True, type=int)
    parser.add_argument("--controller-pid", required=True, type=int)
    parser.add_argument("--controller-pgid", required=True, type=int)
    parser.add_argument("--heartbeat-read-fd", required=True, type=int)
    parser.add_argument("--runtime-directory-fd", required=True, type=int)
    parser.add_argument(
        "--heartbeat-timeout-seconds",
        required=True,
        type=float,
    )
    parser.add_argument("--readiness-path", required=True, type=Path)
    parser.add_argument("--telemetry-path", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    arguments = _parse_arguments()
    anchor = OwnedJavaProcess(
        pid=arguments.anchor_pid,
        process_group_id=arguments.anchor_pgid,
        proc_start_abstime=arguments.anchor_start_abstime,
        expected_executable=arguments.anchor_executable,
    )
    try:
        return monitor_launch(
            anchor,
            arguments.session_id,
            arguments.controller_pid,
            arguments.controller_pgid,
            arguments.heartbeat_read_fd,
            arguments.runtime_directory_fd,
            arguments.readiness_path,
            arguments.telemetry_path,
            heartbeat_timeout_seconds=arguments.heartbeat_timeout_seconds,
        )
    except (LaunchWatchdogError, ValueError) as exception:
        print(f"Forge server launch watchdog failed: {exception}", file=sys.stderr)
        return 2
    finally:
        try:
            os.close(arguments.heartbeat_read_fd)
        except OSError as exception:
            if exception.errno != errno.EBADF:
                raise
        try:
            os.close(arguments.runtime_directory_fd)
        except OSError as exception:
            if exception.errno != errno.EBADF:
                raise


if __name__ == "__main__":
    raise SystemExit(main())
