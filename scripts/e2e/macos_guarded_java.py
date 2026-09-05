#!/usr/bin/env python3
"""Launch and supervise one repository-owned macOS Java client."""

from __future__ import annotations

import argparse
from dataclasses import dataclass, replace
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
from typing import BinaryIO, Callable, Mapping, Sequence


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
BASELINE_DIRECTORY = SCRIPT_DIRECTORY.parent / "baseline"
if str(BASELINE_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(BASELINE_DIRECTORY))

from macos_memory_guard import (  # noqa: E402
    FOUR_GIB_CLIENT_MEMORY_POLICY,
    MAXIMUM_TELEMETRY_SIZE_BYTES,
    MacOsProcessMemorySampler,
    MemoryDecision,
    MemoryPolicy,
    MemorySample,
    MemorySamplingError,
    MemorySamplingUnavailable,
    OwnedJavaMemoryGuard,
    OwnedJavaProcess,
    SampleStatus,
)


EXPECTED_MAXIMUM_MEMORY_MB = 4096
MEBIBYTE_BYTES = 1024 * 1024
GIBIBYTE_BYTES = 1024 * MEBIBYTE_BYTES
STRICT_TWO_GIB_MEMORY_POLICY_V1_NAME = "strict-2g-v1"
STRICT_TWO_GIB_MAXIMUM_MEMORY_MB = 2048
STRICT_TWO_GIB_MEMORY_POLICY_V1 = replace(
    FOUR_GIB_CLIENT_MEMORY_POLICY,
    heap_limit_bytes=STRICT_TWO_GIB_MAXIMUM_MEMORY_MB * MEBIBYTE_BYTES,
    warning_phys_footprint_bytes=3 * GIBIBYTE_BYTES,
    hard_phys_footprint_bytes=4 * GIBIBYTE_BYTES,
    emergency_phys_footprint_bytes=5 * GIBIBYTE_BYTES,
    hard_window_sample_count=15,
    hard_required_high_sample_count=10,
    hard_final_high_sample_count=5,
    emergency_final_high_sample_count=1,
)
JAVA_OPTION_ENVIRONMENT_VARIABLES = (
    "JAVA_TOOL_OPTIONS",
    "JDK_JAVA_OPTIONS",
    "_JAVA_OPTIONS",
)
TELEMETRY_FILE_NAME = "memory-guard-telemetry.json"
READINESS_FILE_NAME = ".memory-guard-ready.json"
READINESS_TIMEOUT_SECONDS = 5.0
IDENTITY_BIND_TIMEOUT_SECONDS = 2.0
STOP_TIMEOUT_SECONDS = 20.0
MONITOR_LOOP_INTERVAL_SECONDS = 0.1
TELEMETRY_PERSIST_INTERVAL_NANOSECONDS = 1_000_000_000
MAXIMUM_TELEMETRY_AGE_NANOSECONDS = 3_000_000_000
MAXIMUM_READINESS_SIZE_BYTES = 16 * 1024
MAXIMUM_ERROR_DETAIL_BYTES = 512
MONITOR_ACTION = "monitor"
MEMORY_POLICY_COMMAND_OPTION = "--memory-policy"
MEMORY_POLICY_READINESS_FIELD = "memory_policy_name"
MEMORY_POLICY_STATE_FIELD = "memory_guard_policy_name"
CURRENT_TELEMETRY_STATE_FIELDS = frozenset(
    {
        "enforcement_disarmed",
        "stop_callback_invoked",
        "sample_count",
        "retained_record_count",
        "dropped_record_count",
        "last_stop_outcome",
    }
)
CURRENT_TELEMETRY_RECORD_FIELDS = frozenset(
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


class GuardedJavaError(RuntimeError):
    """Reports a launch, identity, or persistent-monitor failure."""


@dataclass(frozen=True)
class GuardedJavaLaunch:
    """Carries the three processes created for one guarded Java launch."""

    java_process: subprocess.Popen[bytes]
    monitor_process: subprocess.Popen[bytes]
    caffeinate_process: subprocess.Popen[bytes]
    target: OwnedJavaProcess
    telemetry_path: Path
    readiness_path: Path

    def state_fields(self) -> dict[str, object]:
        """Returns the identity and monitor fields persisted by a controller."""

        return {
            "process_group_id": self.target.process_group_id,
            "proc_start_abstime": self.target.proc_start_abstime,
            "expected_executable": self.target.expected_executable,
            "memory_guard_pid": self.monitor_process.pid,
            "memory_guard_telemetry": str(self.telemetry_path),
            "memory_guard_readiness": str(self.readiness_path),
        }


@dataclass(frozen=True)
class GuardedJavaMonitor:
    """Carries a monitor attached to an already-bound owned Java process."""

    process: subprocess.Popen[bytes]
    target: OwnedJavaProcess
    telemetry_path: Path
    readiness_path: Path
    group_anchor: OwnedJavaProcess | None = None
    policy_name: str | None = None


def verify_java_launch_contract(
    command: Sequence[str],
    java_path: Path,
    maximum_memory_mb: int,
    environment: Mapping[str, str],
) -> None:
    """Rejects any launch that can escape the exact four-GiB heap contract."""

    if maximum_memory_mb != EXPECTED_MAXIMUM_MEMORY_MB:
        raise GuardedJavaError(
            "The native E2E Java heap limit must remain exactly 4096 MiB"
        )
    if not command or any(not isinstance(argument, str) for argument in command):
        raise GuardedJavaError("The native E2E Java command is invalid")
    if command[0] != str(java_path):
        raise GuardedJavaError(
            "The native E2E command does not use the resolved Java executable"
        )
    verify_exact_java_heap_arguments(
        command,
        EXPECTED_MAXIMUM_MEMORY_MB,
        f"-Xmx{EXPECTED_MAXIMUM_MEMORY_MB}M",
    )
    verify_java_option_environment(environment)


def verify_exact_java_heap_arguments(
    arguments: Sequence[str],
    maximum_memory_mb: int,
    exact_argument: str,
) -> None:
    """Requires one exact ``-Xmx`` spelling for a declared MiB heap."""

    if type(maximum_memory_mb) is not int or maximum_memory_mb <= 0:
        raise GuardedJavaError("The Java maximum-memory value must be positive MiB")
    if (
        not isinstance(exact_argument, str)
        or exact_argument not in (
            f"-Xmx{maximum_memory_mb}M",
            f"-Xmx{maximum_memory_mb}m",
        )
    ):
        raise GuardedJavaError(
            "The exact Java heap argument does not match its declared MiB value"
        )
    if any(not isinstance(argument, str) for argument in arguments):
        raise GuardedJavaError("The Java argument inventory is invalid")
    maximum_heap_arguments = [
        argument for argument in arguments if argument.startswith("-Xmx")
    ]
    if maximum_heap_arguments != [exact_argument]:
        raise GuardedJavaError(
            "The Java arguments must contain exactly one "
            f"{exact_argument} argument"
        )


def memory_policy_for_maximum_heap(
    maximum_memory_mb: int,
    policy_name: str | None = None,
) -> MemoryPolicy:
    """Returns the shared physical-footprint policy for one exact heap limit."""

    if type(maximum_memory_mb) is not int or maximum_memory_mb <= 0:
        raise GuardedJavaError("The Java maximum-memory value must be positive MiB")
    if policy_name is not None:
        if policy_name != STRICT_TWO_GIB_MEMORY_POLICY_V1_NAME:
            raise GuardedJavaError(
                f"The Java memory policy is unknown: {policy_name!r}"
            )
        if maximum_memory_mb != STRICT_TWO_GIB_MAXIMUM_MEMORY_MB:
            raise GuardedJavaError(
                f"The {STRICT_TWO_GIB_MEMORY_POLICY_V1_NAME} memory policy requires "
                f"exactly {STRICT_TWO_GIB_MAXIMUM_MEMORY_MB} MiB"
            )
        return STRICT_TWO_GIB_MEMORY_POLICY_V1
    heap_limit_bytes = maximum_memory_mb * MEBIBYTE_BYTES
    if heap_limit_bytes >= FOUR_GIB_CLIENT_MEMORY_POLICY.warning_phys_footprint_bytes:
        raise GuardedJavaError(
            "The Java heap limit must remain below the physical-memory warning"
        )
    return replace(
        FOUR_GIB_CLIENT_MEMORY_POLICY,
        heap_limit_bytes=heap_limit_bytes,
    )


def memory_policy_payload(
    maximum_memory_mb: int,
    policy_name: str | None = None,
) -> dict[str, int]:
    """Returns the exact serialized policy expected from the monitor."""

    policy = memory_policy_for_maximum_heap(maximum_memory_mb, policy_name)
    return {
        "heap_limit_bytes": policy.heap_limit_bytes,
        "warning_phys_footprint_bytes": policy.warning_phys_footprint_bytes,
        "hard_phys_footprint_bytes": policy.hard_phys_footprint_bytes,
        "emergency_phys_footprint_bytes": policy.emergency_phys_footprint_bytes,
        "sample_interval_nanoseconds": policy.sample_interval_nanoseconds,
        "maximum_sample_gap_nanoseconds": policy.maximum_sample_gap_nanoseconds,
        "hard_window_sample_count": policy.hard_window_sample_count,
        "hard_required_high_sample_count": policy.hard_required_high_sample_count,
        "hard_final_high_sample_count": policy.hard_final_high_sample_count,
        "emergency_final_high_sample_count": (
            policy.emergency_final_high_sample_count
        ),
    }


def verify_java_option_environment(environment: Mapping[str, str]) -> None:
    """Rejects inherited JVM option variables before any Java process is probed."""

    injected_variables = [
        name for name in JAVA_OPTION_ENVIRONMENT_VARIABLES if name in environment
    ]
    if injected_variables:
        raise GuardedJavaError(
            "Refusing inherited Java option injection through: "
            + ", ".join(injected_variables)
        )


def guard_runtime_paths(runtime_directory: Path) -> tuple[Path, Path]:
    """Returns fixed telemetry and readiness paths inside one owned runtime."""

    if (
        not runtime_directory.is_absolute()
        or not runtime_directory.is_dir()
        or runtime_directory.is_symlink()
    ):
        raise GuardedJavaError(
            f"The memory guard runtime is missing, linked, or not absolute: "
            f"{runtime_directory}"
        )
    return (
        runtime_directory / TELEMETRY_FILE_NAME,
        runtime_directory / READINESS_FILE_NAME,
    )


def owned_java_process_from_state(state: Mapping[str, object]) -> OwnedJavaProcess:
    """Reconstructs the exact Java identity pinned in controller state."""

    pid = state.get("pid")
    process_group_id = state.get("process_group_id")
    proc_start_abstime = state.get("proc_start_abstime")
    expected_executable = state.get("expected_executable")
    if (
        type(pid) is not int
        or type(process_group_id) is not int
        or type(proc_start_abstime) is not int
        or not isinstance(expected_executable, str)
    ):
        raise GuardedJavaError(
            "The E2E process state has no valid guarded Java identity"
        )
    try:
        return OwnedJavaProcess(
            pid=pid,
            process_group_id=process_group_id,
            proc_start_abstime=proc_start_abstime,
            expected_executable=expected_executable,
        )
    except ValueError as exception:
        raise GuardedJavaError(
            "The E2E process state has no valid guarded Java identity"
        ) from exception


def memory_guard_group_anchor_from_state(
    state: Mapping[str, object],
) -> OwnedJavaProcess | None:
    """Returns the optional exact leader that owns a shared guarded process group."""

    raw_anchor = state.get("memory_guard_group_anchor")
    if raw_anchor is None:
        return None
    if not isinstance(raw_anchor, dict) or set(raw_anchor) != {
        "pid",
        "process_group_id",
        "proc_start_abstime",
        "expected_executable",
    }:
        raise GuardedJavaError("The memory guard group anchor is invalid")
    try:
        anchor = OwnedJavaProcess(
            pid=raw_anchor["pid"],
            process_group_id=raw_anchor["process_group_id"],
            proc_start_abstime=raw_anchor["proc_start_abstime"],
            expected_executable=raw_anchor["expected_executable"],
        )
    except (TypeError, ValueError) as exception:
        raise GuardedJavaError("The memory guard group anchor is invalid") from exception
    if anchor.pid != anchor.process_group_id:
        raise GuardedJavaError("The memory guard group anchor is not its PGID leader")
    return anchor


def _memory_policy_selection_from_state(
    state: Mapping[str, object],
) -> tuple[int | None, str | None]:
    maximum_memory_mb = state.get("memory_guard_maximum_memory_mb")
    raw_policy_name = state.get(MEMORY_POLICY_STATE_FIELD)
    if raw_policy_name is None:
        policy_name = None
    elif raw_policy_name == STRICT_TWO_GIB_MEMORY_POLICY_V1_NAME:
        policy_name = STRICT_TWO_GIB_MEMORY_POLICY_V1_NAME
    else:
        raise GuardedJavaError(
            f"The E2E process state has an unknown Java memory policy: "
            f"{raw_policy_name!r}"
        )
    if maximum_memory_mb is None:
        if policy_name is not None:
            raise GuardedJavaError(
                "A named Java memory policy requires an exact maximum-memory value"
            )
        return None, None
    if type(maximum_memory_mb) is not int:
        raise GuardedJavaError(
            "The E2E process state has an invalid Java maximum-memory value"
        )
    memory_policy_for_maximum_heap(maximum_memory_mb, policy_name)
    return maximum_memory_mb, policy_name


def verify_guard_state_paths(
    state: Mapping[str, object], runtime_directory: Path
) -> tuple[Path, Path]:
    """Validates that recorded guard artifacts remain in their owned runtime."""

    expected_telemetry, expected_readiness = guard_runtime_paths(runtime_directory)
    if state.get("memory_guard_telemetry") != str(expected_telemetry):
        raise GuardedJavaError("The E2E process state points at foreign telemetry")
    if state.get("memory_guard_readiness") != str(expected_readiness):
        raise GuardedJavaError("The E2E process state points at foreign guard readiness")
    for path in (expected_telemetry, expected_readiness):
        if not path.is_file() or path.is_symlink():
            raise GuardedJavaError(
                f"The memory guard artifact is missing or linked: {path}"
            )
    if expected_telemetry.stat().st_size > MAXIMUM_TELEMETRY_SIZE_BYTES:
        raise GuardedJavaError("The memory guard telemetry exceeded its bound")
    target = owned_java_process_from_state(state)
    try:
        telemetry = json.loads(expected_telemetry.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise GuardedJavaError(
            f"Cannot read the memory guard telemetry: {exception}"
        ) from exception
    if (
        not isinstance(telemetry, dict)
        or telemetry.get("schema") != 1
        or telemetry.get("target") != _target_payload(target)
    ):
        raise GuardedJavaError(
            "The memory guard telemetry does not match controller state"
        )
    readiness = _load_readiness(expected_readiness)
    _maximum_memory_mb, policy_name = _memory_policy_selection_from_state(state)
    expected_readiness_payload: dict[str, object] = {
        "schema": 1,
        "status": "ready",
        "monitor_pid": state.get("memory_guard_pid"),
        "target": _target_payload(target),
        "telemetry": str(expected_telemetry),
    }
    if policy_name is not None:
        expected_readiness_payload[MEMORY_POLICY_READINESS_FIELD] = policy_name
    group_anchor = memory_guard_group_anchor_from_state(state)
    if group_anchor is not None:
        expected_readiness_payload["group_anchor"] = _target_payload(group_anchor)
    if readiness != expected_readiness_payload:
        raise GuardedJavaError(
            "The memory guard readiness record does not match controller state"
        )
    return expected_telemetry, expected_readiness


def memory_guard_process_matches(state: Mapping[str, object]) -> bool:
    """Reports whether the recorded monitor PID still names this target monitor."""

    monitor_pid = state.get("memory_guard_pid")
    target_pid = state.get("pid")
    if (
        type(monitor_pid) is not int
        or int(monitor_pid) <= 0
        or type(target_pid) is not int
        or int(target_pid) <= 0
    ):
        return False
    try:
        maximum_memory_mb, policy_name = _memory_policy_selection_from_state(state)
    except GuardedJavaError:
        return False
    try:
        completed = subprocess.run(
            ["/bin/ps", "-ww", "-p", str(monitor_pid), "-o", "command="],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=5.0,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    command = completed.stdout
    base_matches = (
        completed.returncode == 0
        and str(Path(__file__).resolve()) in command
        and MONITOR_ACTION in command
        and f"--pid {target_pid}" in command
        and f"--process-group-id {state.get('process_group_id')}" in command
        and f"--proc-start-abstime {state.get('proc_start_abstime')}" in command
        and f"--expected-executable {state.get('expected_executable')}" in command
        and f"--telemetry {state.get('memory_guard_telemetry')}" in command
        and f"--readiness {state.get('memory_guard_readiness')}" in command
    )
    if not base_matches:
        return False
    if (
        maximum_memory_mb is not None
        and not _command_has_exact_option_value(
            command,
            "--maximum-memory-mb",
            str(maximum_memory_mb),
        )
    ):
        return False
    if not _command_matches_memory_policy(command, policy_name):
        return False
    try:
        group_anchor = memory_guard_group_anchor_from_state(state)
    except GuardedJavaError:
        return False
    if group_anchor is None:
        return "--group-anchor-pid" not in command
    return all(
        argument in command
        for argument in (
            f"--group-anchor-pid {group_anchor.pid}",
            f"--group-anchor-process-group-id {group_anchor.process_group_id}",
            f"--group-anchor-proc-start-abstime {group_anchor.proc_start_abstime}",
            f"--group-anchor-expected-executable {group_anchor.expected_executable}",
        )
    )


def memory_guard_is_enforcing(state: Mapping[str, object]) -> bool:
    """Reports whether persisted telemetry shows a current authoritative sample."""

    raw_path = state.get("memory_guard_telemetry")
    if not isinstance(raw_path, str):
        return False
    path = Path(raw_path)
    try:
        artifact_is_invalid = (
            not path.is_file()
            or path.is_symlink()
            or path.stat().st_size > MAXIMUM_TELEMETRY_SIZE_BYTES
        )
    except OSError:
        return False
    if artifact_is_invalid:
        return False
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return False
    try:
        target = owned_java_process_from_state(state)
    except GuardedJavaError:
        return False
    if (
        not isinstance(payload, dict)
        or payload.get("schema") != 1
        or payload.get("target") != _target_payload(target)
    ):
        return False
    try:
        expected_maximum_memory_mb, policy_name = (
            _memory_policy_selection_from_state(state)
        )
    except GuardedJavaError:
        return False
    if expected_maximum_memory_mb is not None:
        try:
            expected_policy = memory_policy_payload(
                expected_maximum_memory_mb,
                policy_name,
            )
        except GuardedJavaError:
            return False
        if payload.get("policy") != expected_policy:
            return False
    guard_state = payload.get("state")
    records = payload.get("records")
    if (
        not isinstance(guard_state, dict)
        or guard_state.get("enforcement_disarmed") is not False
        or not isinstance(records, list)
        or not records
        or not isinstance(records[-1], dict)
    ):
        return False
    latest = records[-1]
    if policy_name == STRICT_TWO_GIB_MEMORY_POLICY_V1_NAME:
        sample_count = guard_state.get("sample_count")
        retained_record_count = guard_state.get("retained_record_count")
        dropped_record_count = guard_state.get("dropped_record_count")
        current_phys_footprint_bytes = latest.get(
            "current_phys_footprint_bytes"
        )
        resident_size_bytes = latest.get("resident_size_bytes")
        lifetime_max_phys_footprint_bytes = latest.get(
            "lifetime_max_phys_footprint_bytes"
        )
        detail = latest.get("detail")
        if (
            set(guard_state) != CURRENT_TELEMETRY_STATE_FIELDS
            or set(latest) != CURRENT_TELEMETRY_RECORD_FIELDS
            or guard_state.get("enforcement_disarmed") is not False
            or guard_state.get("stop_callback_invoked") is not False
            or guard_state.get("last_stop_outcome") != "not-required"
            or type(sample_count) is not int
            or type(retained_record_count) is not int
            or type(dropped_record_count) is not int
            or retained_record_count != len(records)
            or dropped_record_count < 0
            or sample_count != retained_record_count + dropped_record_count
            or latest.get("decision") not in {"normal", "warning"}
            or latest.get("stop_outcome") != "not-required"
            or type(current_phys_footprint_bytes) is not int
            or current_phys_footprint_bytes < 0
            or current_phys_footprint_bytes
            > expected_policy["emergency_phys_footprint_bytes"]
            or type(resident_size_bytes) is not int
            or resident_size_bytes < 0
            or latest.get("virtual_size_bytes") is not None
            or type(lifetime_max_phys_footprint_bytes) is not int
            or lifetime_max_phys_footprint_bytes < current_phys_footprint_bytes
            or not isinstance(detail, str)
            or len(detail.encode("utf-8")) > 512
        ):
            return False
    else:
        if (
            "stop_callback_invoked" in guard_state
            and guard_state.get("stop_callback_invoked") is not False
        ):
            return False
        if (
            "last_stop_outcome" in guard_state
            and guard_state.get("last_stop_outcome") != "not-required"
        ):
            return False
        if (
            "decision" in latest
            and latest.get("decision") not in {"normal", "warning"}
        ):
            return False
        if (
            "stop_outcome" in latest
            and latest.get("stop_outcome") != "not-required"
        ):
            return False
    observed_at = latest.get("observed_at_monotonic_ns")
    if type(observed_at) is not int:
        return False
    age = time.monotonic_ns() - observed_at
    return (
        0 <= age <= MAXIMUM_TELEMETRY_AGE_NANOSECONDS
        and latest.get("source") == "proc-pid-rusage-v4"
        and latest.get("status") == "available"
        and latest.get("identity_matches_target") is True
    )


def _command_matches_memory_policy(
    command: str,
    policy_name: str | None,
) -> bool:
    option_pattern = rf"(?:^|\s){re.escape(MEMORY_POLICY_COMMAND_OPTION)}(?:=|\s|$)"
    option_is_present = re.search(option_pattern, command) is not None
    if policy_name is None:
        return not option_is_present
    return _command_has_exact_option_value(
        command,
        MEMORY_POLICY_COMMAND_OPTION,
        policy_name,
    )


def _command_has_exact_option_value(
    command: str,
    option: str,
    expected_value: str,
) -> bool:
    value_pattern = rf"(?:^|\s){re.escape(option)}(?:=|\s+)([^\s]+)"
    return re.findall(value_pattern, command) == [expected_value]


def start_guarded_java(
    command: Sequence[str],
    java_path: Path,
    maximum_memory_mb: int,
    environment: Mapping[str, str],
    runtime_directory: Path,
    working_directory: Path,
    log_handle: BinaryIO,
    caffeinate_path: Path,
) -> GuardedJavaLaunch:
    """Starts Java, confirms its guard, and starts PID-scoped sleep prevention."""

    verify_java_launch_contract(
        command,
        java_path,
        maximum_memory_mb,
        environment,
    )
    telemetry_path, readiness_path = guard_runtime_paths(runtime_directory)
    for path in (telemetry_path, readiness_path):
        if path.exists() or path.is_symlink():
            raise GuardedJavaError(
                f"Refusing to replace an existing memory guard artifact: {path}"
            )
    if not caffeinate_path.is_file() or caffeinate_path.is_symlink():
        raise GuardedJavaError(f"macOS caffeinate is missing or linked: {caffeinate_path}")

    sampler = MacOsProcessMemorySampler.native()
    java_process: subprocess.Popen[bytes] | None = None
    monitor_process: subprocess.Popen[bytes] | None = None
    caffeinate_process: subprocess.Popen[bytes] | None = None
    target: OwnedJavaProcess | None = None
    try:
        java_process = subprocess.Popen(
            list(command),
            cwd=working_directory,
            env=dict(environment),
            stdin=subprocess.DEVNULL,
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
        target = _bind_spawned_java(java_process, java_path, sampler)
        monitor = start_guarded_java_monitor(
            target,
            maximum_memory_mb,
            runtime_directory,
            log_handle,
        )
        monitor_process = monitor.process
        caffeinate_process = subprocess.Popen(
            [str(caffeinate_path), "-dimsu", "-w", str(target.pid)],
            cwd=working_directory,
            stdin=subprocess.DEVNULL,
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
        if monitor_process.poll() is not None:
            raise GuardedJavaError(
                "The Java memory guard exited before launch handoff completed"
            )
        if java_process.poll() is not None:
            raise GuardedJavaError(
                "The Java client exited before launch handoff completed"
            )
        if caffeinate_process.poll() is not None:
            raise GuardedJavaError(
                "macOS caffeinate exited before launch handoff completed"
            )
        return GuardedJavaLaunch(
            java_process=java_process,
            monitor_process=monitor_process,
            caffeinate_process=caffeinate_process,
            target=target,
            telemetry_path=telemetry_path,
            readiness_path=readiness_path,
        )
    except BaseException as launch_exception:
        try:
            _terminate_spawned_auxiliary(caffeinate_process)
            _terminate_spawned_auxiliary(monitor_process)
            if java_process is not None:
                _terminate_new_java_process_group(java_process)
        except (GuardedJavaError, OSError) as cleanup_exception:
            raise GuardedJavaError(
                "Guarded Java launch failed and its owned process did not stop: "
                f"{cleanup_exception}"
            ) from launch_exception
        raise


def start_guarded_java_monitor(
    target: OwnedJavaProcess,
    maximum_memory_mb: int,
    runtime_directory: Path,
    log_handle: BinaryIO,
    *,
    group_anchor: OwnedJavaProcess | None = None,
    policy_name: str | None = None,
    process_started: Callable[[subprocess.Popen[bytes]], None] | None = None,
) -> GuardedJavaMonitor:
    """Starts an authoritative monitor for one already-bound Java target."""

    memory_policy_for_maximum_heap(maximum_memory_mb, policy_name)
    telemetry_path, readiness_path = guard_runtime_paths(runtime_directory)
    for path in (telemetry_path, readiness_path):
        if path.exists() or path.is_symlink():
            raise GuardedJavaError(
                f"Refusing to replace an existing memory guard artifact: {path}"
            )
    current_pid = os.getpid()
    current_process_group_id = os.getpgrp()
    target_shares_current_group = (
        target.process_group_id == current_process_group_id
    )
    if target_shares_current_group:
        if (
            group_anchor is None
            or group_anchor.pid != current_pid
            or group_anchor.pid != group_anchor.process_group_id
            or group_anchor.process_group_id != current_process_group_id
            or target.pid == group_anchor.pid
        ):
            raise GuardedJavaError(
                "The guarded Java target shares the controller process group "
                "without its exact live leader anchor"
            )
    elif group_anchor is not None:
        raise GuardedJavaError(
            "A memory guard group anchor is valid only for a shared target group"
        )
    monitor_process: subprocess.Popen[bytes] | None = None
    try:
        monitor_process = subprocess.Popen(
            _monitor_command(
                target,
                maximum_memory_mb,
                telemetry_path,
                readiness_path,
                group_anchor,
                policy_name,
            ),
            cwd=runtime_directory,
            stdin=subprocess.DEVNULL,
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
        if process_started is not None:
            process_started(monitor_process)
        _wait_for_monitor_readiness(
            monitor_process,
            target,
            telemetry_path,
            readiness_path,
            group_anchor,
            policy_name,
        )
        if monitor_process.poll() is not None:
            raise GuardedJavaError(
                "The Java memory guard exited before monitor handoff completed"
            )
        return GuardedJavaMonitor(
            process=monitor_process,
            target=target,
            telemetry_path=telemetry_path,
            readiness_path=readiness_path,
            group_anchor=group_anchor,
            policy_name=policy_name,
        )
    except BaseException:
        _terminate_spawned_auxiliary(monitor_process)
        raise


def stop_guarded_java_monitor(monitor: GuardedJavaMonitor) -> None:
    """Stops one monitor created by :func:`start_guarded_java_monitor`."""

    _terminate_spawned_auxiliary(monitor.process)


def stop_spawned_auxiliary(process: subprocess.Popen[bytes] | None) -> None:
    """Stops one directly spawned auxiliary without signaling a process group."""

    _terminate_spawned_auxiliary(process)


def stop_guarded_java_launch(launch: GuardedJavaLaunch) -> bool:
    """Stops a just-created launch without signaling any unverified process."""

    forced = _terminate_new_java_process_group(launch.java_process)
    _terminate_spawned_auxiliary(launch.caffeinate_process)
    _terminate_spawned_auxiliary(launch.monitor_process)
    return forced


def stop_owned_java_process(
    target: OwnedJavaProcess,
    *,
    owned_process_group_id: int | None = None,
    timeout_seconds: float = STOP_TIMEOUT_SECONDS,
    sampler: MacOsProcessMemorySampler | None = None,
) -> bool:
    """Stops a revalidated Java target's explicitly owned dedicated group."""

    selected_sampler = sampler or MacOsProcessMemorySampler.native()
    process_group_id = (
        target.pid
        if owned_process_group_id is None
        else owned_process_group_id
    )
    return _stop_owned_java_process(
        target,
        selected_sampler,
        process_group_id,
        timeout_seconds,
    )


def _stop_owned_java_process(
    target: OwnedJavaProcess,
    sampler: MacOsProcessMemorySampler,
    owned_process_group_id: int,
    timeout_seconds: float,
) -> bool:
    if (
        type(owned_process_group_id) is not int
        or owned_process_group_id <= 0
        or target.process_group_id != owned_process_group_id
    ):
        raise GuardedJavaError(
            "The guarded Java target is not in the recorded dedicated PGID"
        )
    if (
        target.pid == os.getpid()
        or owned_process_group_id == os.getpgrp()
    ):
        raise GuardedJavaError(
            "Refusing to signal the memory guard's own process group"
        )
    if sampler.revalidate(target) != target:
        raise GuardedJavaError(
            "The guarded Java identity changed; refusing to signal its process group"
        )
    try:
        os.killpg(owned_process_group_id, signal.SIGTERM)
    except ProcessLookupError:
        return _confirm_target_and_group_missing(
            sampler,
            target,
            owned_process_group_id,
            False,
        )
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        status = sampler.sample(target, time.monotonic_ns()).status
        if status is SampleStatus.MISSING:
            if not _process_group_exists(owned_process_group_id):
                return False
            time.sleep(0.1)
            continue
        if status is not SampleStatus.AVAILABLE:
            raise GuardedJavaError(
                "The guarded Java identity could not be confirmed after SIGTERM"
            )
        time.sleep(0.1)
    if sampler.revalidate(target) != target:
        return _confirm_target_and_group_missing(
            sampler,
            target,
            owned_process_group_id,
            False,
        )
    try:
        os.killpg(owned_process_group_id, signal.SIGKILL)
    except ProcessLookupError:
        return _confirm_target_and_group_missing(
            sampler,
            target,
            owned_process_group_id,
            True,
        )
    kill_deadline = time.monotonic() + 2.0
    while time.monotonic() < kill_deadline:
        status = sampler.sample(target, time.monotonic_ns()).status
        if (
            status is SampleStatus.MISSING
            and not _process_group_exists(owned_process_group_id)
        ):
            return True
        if status not in (SampleStatus.AVAILABLE, SampleStatus.MISSING):
            raise GuardedJavaError(
                "The guarded Java identity could not be confirmed after SIGKILL"
            )
        time.sleep(0.05)
    raise GuardedJavaError(
        "The guarded Java launch group remained live after SIGKILL"
    )


def _confirm_target_and_group_missing(
    sampler: MacOsProcessMemorySampler,
    target: OwnedJavaProcess,
    owned_process_group_id: int,
    forced: bool,
) -> bool:
    status = sampler.sample(target, time.monotonic_ns()).status
    if (
        status is SampleStatus.MISSING
        and not _process_group_exists(owned_process_group_id)
    ):
        return forced
    raise GuardedJavaError(
        "The guarded Java launch group could not be confirmed absent after signaling"
    )


def _process_group_exists(process_group_id: int) -> bool:
    try:
        os.killpg(process_group_id, 0)
    except ProcessLookupError:
        return False
    except PermissionError as exception:
        raise GuardedJavaError(
            "Cannot verify the owned Java launch process group"
        ) from exception
    return True


def monitor_owned_java(
    target: OwnedJavaProcess,
    telemetry_path: Path,
    readiness_path: Path,
    maximum_memory_mb: int = EXPECTED_MAXIMUM_MEMORY_MB,
    group_anchor: OwnedJavaProcess | None = None,
    policy_name: str | None = None,
) -> int:
    """Monitors the exact Java identity until it exits or identity safety is lost."""

    policy = memory_policy_for_maximum_heap(maximum_memory_mb, policy_name)
    _validate_monitor_artifact_paths(telemetry_path, readiness_path)
    sampler = MacOsProcessMemorySampler.native()
    if sampler.revalidate(target) != target:
        raise GuardedJavaError(
            "The Java identity could not be revalidated before monitoring"
        )
    if group_anchor is not None:
        if (
            group_anchor.pid != group_anchor.process_group_id
            or group_anchor.process_group_id != target.process_group_id
            or group_anchor.pid == target.pid
            or sampler.revalidate(group_anchor) != group_anchor
        ):
            raise GuardedJavaError(
                "The Java memory guard group anchor could not be revalidated"
            )

    guard = OwnedJavaMemoryGuard(
        target,
        sampler.sample,
        sampler.revalidate,
        lambda guarded_target, decision: _stop_for_memory_decision(
            guarded_target,
            decision,
            sampler,
            target.process_group_id,
            group_anchor,
        ),
        policy=policy,
    )
    initial_result = guard.poll()
    _write_telemetry(telemetry_path, guard.telemetry_json_bytes())
    if initial_result.sample_status is not SampleStatus.AVAILABLE:
        raise GuardedJavaError(
            "The initial authoritative Java memory sample was unavailable"
        )
    readiness_payload: dict[str, object] = {
        "schema": 1,
        "status": "ready",
        "monitor_pid": os.getpid(),
        "target": _target_payload(target),
        "telemetry": str(telemetry_path),
    }
    if policy_name is not None:
        readiness_payload[MEMORY_POLICY_READINESS_FIELD] = policy_name
    if group_anchor is not None:
        readiness_payload["group_anchor"] = _target_payload(group_anchor)
    _write_json_exclusive(readiness_path, readiness_payload)

    last_persisted_at = time.monotonic_ns()
    previous_decision = initial_result.decision
    previous_status = initial_result.sample_status
    if initial_result.decision is not MemoryDecision.NORMAL:
        _print_decision_transition(initial_result.decision, policy)
    while True:
        result = guard.poll()
        if result.sampled:
            now = time.monotonic_ns()
            decision_changed = result.decision != previous_decision
            status_changed = result.sample_status != previous_status
            should_persist = (
                now - last_persisted_at >= TELEMETRY_PERSIST_INTERVAL_NANOSECONDS
                or decision_changed
                or status_changed
            )
            if should_persist:
                _write_telemetry(telemetry_path, guard.telemetry_json_bytes())
                last_persisted_at = now
            if decision_changed:
                _print_decision_transition(result.decision, policy)
                previous_decision = result.decision
            if status_changed:
                _print_sample_status_transition(result.sample_status)
                previous_status = result.sample_status
            if result.sample_status in (
                SampleStatus.MISSING,
                SampleStatus.IDENTITY_DRIFT,
            ):
                _write_telemetry(telemetry_path, guard.telemetry_json_bytes())
                return 0
        time.sleep(MONITOR_LOOP_INTERVAL_SECONDS)


def _bind_spawned_java(
    java_process: subprocess.Popen[bytes],
    java_path: Path,
    sampler: MacOsProcessMemorySampler,
) -> OwnedJavaProcess:
    deadline = time.monotonic() + IDENTITY_BIND_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if java_process.poll() is not None:
            raise GuardedJavaError(
                "Java exited before its authoritative memory identity could be bound"
            )
        try:
            process_group_id = os.getpgid(java_process.pid)
        except ProcessLookupError:
            process_group_id = -1
        if process_group_id == java_process.pid:
            target = sampler.bind(
                java_process.pid,
                process_group_id,
                str(java_path),
            )
            if target is not None:
                return target
        time.sleep(0.01)
    raise GuardedJavaError(
        "Java did not expose the exact executable, start time, and dedicated PGID"
    )


def _monitor_command(
    target: OwnedJavaProcess,
    maximum_memory_mb: int,
    telemetry_path: Path,
    readiness_path: Path,
    group_anchor: OwnedJavaProcess | None = None,
    policy_name: str | None = None,
) -> list[str]:
    memory_policy_for_maximum_heap(maximum_memory_mb, policy_name)
    command = [
        sys.executable,
        "-B",
        str(Path(__file__).resolve()),
        MONITOR_ACTION,
        "--pid",
        str(target.pid),
        "--process-group-id",
        str(target.process_group_id),
        "--proc-start-abstime",
        str(target.proc_start_abstime),
        "--expected-executable",
        target.expected_executable,
        "--maximum-memory-mb",
        str(maximum_memory_mb),
        "--telemetry",
        str(telemetry_path),
        "--readiness",
        str(readiness_path),
    ]
    if policy_name is not None:
        command.extend([MEMORY_POLICY_COMMAND_OPTION, policy_name])
    if group_anchor is not None:
        command.extend(
            [
                "--group-anchor-pid",
                str(group_anchor.pid),
                "--group-anchor-process-group-id",
                str(group_anchor.process_group_id),
                "--group-anchor-proc-start-abstime",
                str(group_anchor.proc_start_abstime),
                "--group-anchor-expected-executable",
                group_anchor.expected_executable,
            ]
        )
    return command


def _wait_for_monitor_readiness(
    monitor_process: subprocess.Popen[bytes],
    target: OwnedJavaProcess,
    telemetry_path: Path,
    readiness_path: Path,
    group_anchor: OwnedJavaProcess | None = None,
    policy_name: str | None = None,
) -> None:
    if (
        policy_name is not None
        and policy_name != STRICT_TWO_GIB_MEMORY_POLICY_V1_NAME
    ):
        raise GuardedJavaError(f"The Java memory policy is unknown: {policy_name!r}")
    deadline = time.monotonic() + READINESS_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if readiness_path.exists() or readiness_path.is_symlink():
            payload = _load_readiness(readiness_path)
            expected = {
                "schema": 1,
                "status": "ready",
                "monitor_pid": monitor_process.pid,
                "target": _target_payload(target),
                "telemetry": str(telemetry_path),
            }
            if policy_name is not None:
                expected[MEMORY_POLICY_READINESS_FIELD] = policy_name
            if group_anchor is not None:
                expected["group_anchor"] = _target_payload(group_anchor)
            if payload != expected:
                raise GuardedJavaError(
                    "The Java memory guard readiness record does not match its launch"
                )
            if not telemetry_path.is_file() or telemetry_path.is_symlink():
                raise GuardedJavaError(
                    "The Java memory guard became ready without telemetry"
                )
            if telemetry_path.stat().st_size > MAXIMUM_TELEMETRY_SIZE_BYTES:
                raise GuardedJavaError("The Java memory telemetry exceeded its bound")
            return
        if monitor_process.poll() is not None:
            raise GuardedJavaError(
                "The Java memory guard exited before publishing readiness"
            )
        time.sleep(0.01)
    raise GuardedJavaError("Timed out waiting for Java memory guard readiness")


def _load_readiness(path: Path) -> dict[str, object]:
    if not path.is_file() or path.is_symlink():
        raise GuardedJavaError(f"The memory guard readiness record is invalid: {path}")
    if path.stat().st_size > MAXIMUM_READINESS_SIZE_BYTES:
        raise GuardedJavaError("The memory guard readiness record exceeded its bound")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise GuardedJavaError(
            f"Cannot read the memory guard readiness record: {exception}"
        ) from exception
    if not isinstance(payload, dict):
        raise GuardedJavaError("The memory guard readiness record is not an object")
    return payload


def _validate_monitor_artifact_paths(
    telemetry_path: Path,
    readiness_path: Path,
) -> None:
    runtime_directory = telemetry_path.parent
    expected_telemetry, expected_readiness = guard_runtime_paths(runtime_directory)
    if telemetry_path != expected_telemetry or readiness_path != expected_readiness:
        raise GuardedJavaError("The monitor artifact paths do not name one owned runtime")
    for path in (telemetry_path, readiness_path):
        if path.exists() or path.is_symlink():
            raise GuardedJavaError(
                f"Refusing to replace an existing memory guard artifact: {path}"
            )


def _target_payload(target: OwnedJavaProcess) -> dict[str, object]:
    return {
        "pid": target.pid,
        "process_group_id": target.process_group_id,
        "proc_start_abstime": target.proc_start_abstime,
        "expected_executable": target.expected_executable,
    }


def _write_telemetry(path: Path, content: bytes) -> None:
    terminated_content = content if content.endswith(b"\n") else content + b"\n"
    if len(terminated_content) > MAXIMUM_TELEMETRY_SIZE_BYTES:
        raise GuardedJavaError("The memory guard telemetry exceeded its size bound")
    _write_bytes_atomic(path, terminated_content)


def _write_bytes_atomic(path: Path, content: bytes) -> None:
    if path.is_symlink():
        raise GuardedJavaError(f"Refusing a linked memory guard artifact: {path}")
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


def _write_json_exclusive(path: Path, payload: dict[str, object]) -> None:
    content = (
        json.dumps(payload, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as handle:
        handle.write(content)
        handle.flush()


def _stop_for_memory_decision(
    target: OwnedJavaProcess,
    decision: MemoryDecision,
    sampler: MacOsProcessMemorySampler,
    owned_process_group_id: int,
    group_anchor: OwnedJavaProcess | None = None,
) -> None:
    if decision not in (MemoryDecision.HARD, MemoryDecision.EMERGENCY):
        raise GuardedJavaError("The memory guard requested an invalid stop decision")
    if group_anchor is not None:
        _stop_anchored_java_process(
            target,
            group_anchor,
            decision,
            sampler,
        )
        return
    _stop_owned_java_process(
        target,
        sampler,
        owned_process_group_id,
        STOP_TIMEOUT_SECONDS,
    )


def _stop_anchored_java_process(
    target: OwnedJavaProcess,
    group_anchor: OwnedJavaProcess,
    decision: MemoryDecision,
    sampler: MacOsProcessMemorySampler,
) -> None:
    """Stops a shared group only while its exact leader anchor remains live."""

    process_group_id = group_anchor.process_group_id
    if (
        group_anchor.pid != process_group_id
        or target.process_group_id != process_group_id
        or target.pid == group_anchor.pid
    ):
        raise GuardedJavaError("The guarded Java process-group anchor is invalid")
    if (
        sampler.revalidate(group_anchor) != group_anchor
        or sampler.revalidate(target) != target
    ):
        raise GuardedJavaError(
            "The guarded Java process-group anchor or target changed before stopping"
        )
    selected_signal = (
        signal.SIGKILL
        if decision is MemoryDecision.EMERGENCY
        else signal.SIGTERM
    )
    try:
        os.killpg(process_group_id, selected_signal)
    except ProcessLookupError as exception:
        raise GuardedJavaError(
            "The anchored Java process group vanished before memory enforcement"
        ) from exception

    deadline = time.monotonic() + (
        2.0 if selected_signal == signal.SIGKILL else STOP_TIMEOUT_SECONDS
    )
    while time.monotonic() < deadline:
        target_status = sampler.sample(target, time.monotonic_ns()).status
        if selected_signal == signal.SIGTERM:
            if (
                target_status is SampleStatus.MISSING
                and sampler.revalidate(group_anchor) == group_anchor
            ):
                return
        elif (
            target_status is SampleStatus.MISSING
            and sampler.revalidate(group_anchor) is None
            and not _process_group_exists(process_group_id)
        ):
            return
        if target_status not in (SampleStatus.AVAILABLE, SampleStatus.MISSING):
            raise GuardedJavaError(
                "The anchored Java identity became unverifiable during enforcement"
            )
        time.sleep(0.05)

    if selected_signal == signal.SIGKILL:
        raise GuardedJavaError(
            "The emergency-stopped anchored Java group remained live after SIGKILL"
        )
    if (
        sampler.revalidate(group_anchor) != group_anchor
        or sampler.revalidate(target) != target
    ):
        raise GuardedJavaError(
            "The anchored Java target changed before memory-stop escalation"
        )
    try:
        os.killpg(process_group_id, signal.SIGKILL)
    except ProcessLookupError as exception:
        raise GuardedJavaError(
            "The anchored Java group vanished before memory-stop escalation"
        ) from exception
    kill_deadline = time.monotonic() + 2.0
    while time.monotonic() < kill_deadline:
        if (
            sampler.sample(target, time.monotonic_ns()).status
            is SampleStatus.MISSING
            and sampler.revalidate(group_anchor) is None
            and not _process_group_exists(process_group_id)
        ):
            return
        time.sleep(0.05)
    raise GuardedJavaError(
        "The anchored Java group remained live after memory-stop escalation"
    )


def _print_decision_transition(
    decision: MemoryDecision,
    policy: MemoryPolicy = FOUR_GIB_CLIENT_MEMORY_POLICY,
) -> None:
    thresholds = {
        MemoryDecision.WARNING: policy.warning_phys_footprint_bytes,
        MemoryDecision.HARD: policy.hard_phys_footprint_bytes,
        MemoryDecision.EMERGENCY: policy.emergency_phys_footprint_bytes,
    }
    threshold_bytes = thresholds.get(decision)
    if threshold_bytes is None:
        return
    threshold = _format_memory_threshold(threshold_bytes)
    if decision is MemoryDecision.WARNING:
        print(
            "Etherology memory guard warning: current physical footprint exceeded "
            f"{threshold}",
            flush=True,
        )
    elif decision in (MemoryDecision.HARD, MemoryDecision.EMERGENCY):
        if _uses_legacy_decision_wording(policy):
            decision_threshold = f"{decision.value} threshold"
        else:
            decision_threshold = f"{decision.value} {threshold} threshold"
        print(
            "Etherology memory guard stopped the owned Java process after a "
            f"{decision_threshold}",
            flush=True,
        )


def _format_memory_threshold(threshold_bytes: int) -> str:
    if threshold_bytes % GIBIBYTE_BYTES == 0:
        return f"{threshold_bytes // GIBIBYTE_BYTES} GiB"
    return f"{threshold_bytes} bytes"


def _uses_legacy_decision_wording(policy: MemoryPolicy) -> bool:
    return (
        policy.warning_phys_footprint_bytes
        == FOUR_GIB_CLIENT_MEMORY_POLICY.warning_phys_footprint_bytes
        and policy.hard_phys_footprint_bytes
        == FOUR_GIB_CLIENT_MEMORY_POLICY.hard_phys_footprint_bytes
        and policy.emergency_phys_footprint_bytes
        == FOUR_GIB_CLIENT_MEMORY_POLICY.emergency_phys_footprint_bytes
    )


def _print_sample_status_transition(status: SampleStatus | None) -> None:
    if status is SampleStatus.AVAILABLE:
        print(
            "Etherology memory guard resumed authoritative physical-footprint "
            "sampling",
            flush=True,
        )
    elif status is not None:
        print(
            "Etherology memory guard cannot enforce while authoritative sampling "
            f"is {status.value}",
            flush=True,
        )


def _terminate_spawned_auxiliary(
    process: subprocess.Popen[bytes] | None,
) -> None:
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=2.0)
    except subprocess.TimeoutExpired:
        process.kill()
        try:
            process.wait(timeout=2.0)
        except subprocess.TimeoutExpired as exception:
            raise GuardedJavaError(
                "A spawned Java lifecycle helper remained live after SIGKILL"
            ) from exception


def _terminate_new_java_process_group(
    process: subprocess.Popen[bytes],
) -> bool:
    if process.poll() is not None:
        return False
    try:
        owns_process_group = os.getpgid(process.pid) == process.pid
        if owns_process_group:
            os.killpg(process.pid, signal.SIGTERM)
        else:
            process.terminate()
    except ProcessLookupError:
        process.poll()
        return False
    try:
        process.wait(timeout=1.0)
        return False
    except subprocess.TimeoutExpired:
        pass
    if process.poll() is not None:
        return False
    if owns_process_group:
        try:
            if os.getpgid(process.pid) != process.pid:
                raise GuardedJavaError(
                    "The newly spawned Java process lost its dedicated PGID"
                )
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            process.poll()
            return False
    else:
        process.kill()
    try:
        process.wait(timeout=2.0)
    except subprocess.TimeoutExpired:
        raise GuardedJavaError(
            "The newly spawned Java process remained live after SIGKILL"
        )
    return True


def _bounded_error_detail(exception: BaseException) -> str:
    content = f"{type(exception).__name__}: {exception}".encode(
        "utf-8",
        errors="replace",
    )
    return content[:MAXIMUM_ERROR_DETAIL_BYTES].decode(
        "utf-8",
        errors="ignore",
    )


def _parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Monitor one exact repository-owned macOS Java process."
    )
    parser.add_argument("action", choices=(MONITOR_ACTION,))
    parser.add_argument("--pid", required=True, type=int)
    parser.add_argument("--process-group-id", required=True, type=int)
    parser.add_argument("--proc-start-abstime", required=True, type=int)
    parser.add_argument("--expected-executable", required=True)
    parser.add_argument(
        "--maximum-memory-mb",
        default=EXPECTED_MAXIMUM_MEMORY_MB,
        type=int,
    )
    parser.add_argument(
        MEMORY_POLICY_COMMAND_OPTION,
        choices=(STRICT_TWO_GIB_MEMORY_POLICY_V1_NAME,),
    )
    parser.add_argument("--telemetry", required=True, type=Path)
    parser.add_argument("--readiness", required=True, type=Path)
    parser.add_argument("--group-anchor-pid", type=int)
    parser.add_argument("--group-anchor-process-group-id", type=int)
    parser.add_argument("--group-anchor-proc-start-abstime", type=int)
    parser.add_argument("--group-anchor-expected-executable")
    return parser.parse_args()


def main() -> int:
    """Runs the persistent monitor subprocess entry point."""

    arguments = _parse_arguments()
    target = OwnedJavaProcess(
        pid=arguments.pid,
        process_group_id=arguments.process_group_id,
        proc_start_abstime=arguments.proc_start_abstime,
        expected_executable=arguments.expected_executable,
    )
    raw_group_anchor = (
        arguments.group_anchor_pid,
        arguments.group_anchor_process_group_id,
        arguments.group_anchor_proc_start_abstime,
        arguments.group_anchor_expected_executable,
    )
    if any(value is not None for value in raw_group_anchor) and not all(
        value is not None for value in raw_group_anchor
    ):
        print(
            "Etherology Java memory guard failed: incomplete group anchor",
            file=sys.stderr,
        )
        return 2
    group_anchor = (
        OwnedJavaProcess(
            pid=arguments.group_anchor_pid,
            process_group_id=arguments.group_anchor_process_group_id,
            proc_start_abstime=arguments.group_anchor_proc_start_abstime,
            expected_executable=arguments.group_anchor_expected_executable,
        )
        if all(value is not None for value in raw_group_anchor)
        else None
    )
    try:
        return monitor_owned_java(
            target,
            arguments.telemetry,
            arguments.readiness,
            arguments.maximum_memory_mb,
            group_anchor,
            arguments.memory_policy,
        )
    except (
        GuardedJavaError,
        MemorySamplingError,
        MemorySamplingUnavailable,
        OSError,
        ValueError,
    ) as exception:
        print(
            "Etherology Java memory guard failed: "
            f"{_bounded_error_detail(exception)}",
            file=sys.stderr,
            flush=True,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
