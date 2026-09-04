#!/usr/bin/env python3
"""Launch and supervise one repository-owned macOS Java client."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
from typing import BinaryIO, Mapping, Sequence


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
BASELINE_DIRECTORY = SCRIPT_DIRECTORY.parent / "baseline"
if str(BASELINE_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(BASELINE_DIRECTORY))

from macos_memory_guard import (  # noqa: E402
    MAXIMUM_TELEMETRY_SIZE_BYTES,
    MacOsProcessMemorySampler,
    MemoryDecision,
    MemorySamplingError,
    MemorySamplingUnavailable,
    OwnedJavaMemoryGuard,
    OwnedJavaProcess,
    SampleStatus,
)


EXPECTED_MAXIMUM_MEMORY_MB = 4096
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
    maximum_heap_arguments = [
        argument for argument in command if argument.startswith("-Xmx")
    ]
    if maximum_heap_arguments != [f"-Xmx{EXPECTED_MAXIMUM_MEMORY_MB}M"]:
        raise GuardedJavaError(
            "The native E2E command must contain exactly one -Xmx4096M argument"
        )
    verify_java_option_environment(environment)


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
    expected_readiness_payload = {
        "schema": 1,
        "status": "ready",
        "monitor_pid": state.get("memory_guard_pid"),
        "target": _target_payload(target),
        "telemetry": str(expected_telemetry),
    }
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
    return (
        completed.returncode == 0
        and str(Path(__file__).resolve()) in command
        and MONITOR_ACTION in command
        and f"--pid {target_pid}" in command
        and f"--proc-start-abstime {state.get('proc_start_abstime')}" in command
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
        monitor_process = subprocess.Popen(
            _monitor_command(target, telemetry_path, readiness_path),
            cwd=runtime_directory,
            stdin=subprocess.DEVNULL,
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
        _wait_for_monitor_readiness(
            monitor_process,
            target,
            telemetry_path,
            readiness_path,
        )
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


def stop_guarded_java_launch(launch: GuardedJavaLaunch) -> bool:
    """Stops a just-created launch without signaling any unverified process."""

    forced = _terminate_new_java_process_group(launch.java_process)
    _terminate_spawned_auxiliary(launch.caffeinate_process)
    _terminate_spawned_auxiliary(launch.monitor_process)
    return forced


def stop_owned_java_process(
    target: OwnedJavaProcess,
    *,
    timeout_seconds: float = STOP_TIMEOUT_SECONDS,
) -> bool:
    """Stops only a revalidated Java process whose PID owns its dedicated group."""

    sampler = MacOsProcessMemorySampler.native()
    return _stop_owned_java_process(target, sampler, timeout_seconds)


def _stop_owned_java_process(
    target: OwnedJavaProcess,
    sampler: MacOsProcessMemorySampler,
    timeout_seconds: float,
) -> bool:
    if target.process_group_id != target.pid:
        raise GuardedJavaError("The guarded Java target does not own a dedicated PGID")
    if sampler.revalidate(target) != target:
        raise GuardedJavaError(
            "The guarded Java identity changed; refusing to signal its process group"
        )
    try:
        os.killpg(target.process_group_id, signal.SIGTERM)
    except ProcessLookupError:
        return _confirm_target_missing(sampler, target, False)
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        status = sampler.sample(target, time.monotonic_ns()).status
        if status is SampleStatus.MISSING:
            return False
        if status is not SampleStatus.AVAILABLE:
            raise GuardedJavaError(
                "The guarded Java identity could not be confirmed after SIGTERM"
            )
        time.sleep(0.1)
    if sampler.revalidate(target) != target:
        return _confirm_target_missing(sampler, target, False)
    try:
        os.killpg(target.process_group_id, signal.SIGKILL)
    except ProcessLookupError:
        return _confirm_target_missing(sampler, target, True)
    kill_deadline = time.monotonic() + 2.0
    while time.monotonic() < kill_deadline:
        status = sampler.sample(target, time.monotonic_ns()).status
        if status is SampleStatus.MISSING:
            return True
        if status is not SampleStatus.AVAILABLE:
            raise GuardedJavaError(
                "The guarded Java identity could not be confirmed after SIGKILL"
            )
        time.sleep(0.05)
    raise GuardedJavaError("The guarded Java process remained live after SIGKILL")


def _confirm_target_missing(
    sampler: MacOsProcessMemorySampler,
    target: OwnedJavaProcess,
    forced: bool,
) -> bool:
    status = sampler.sample(target, time.monotonic_ns()).status
    if status is SampleStatus.MISSING:
        return forced
    raise GuardedJavaError(
        "The guarded Java process could not be confirmed absent after signaling"
    )


def monitor_owned_java(
    target: OwnedJavaProcess,
    telemetry_path: Path,
    readiness_path: Path,
) -> int:
    """Monitors the exact Java identity until it exits or identity safety is lost."""

    _validate_monitor_artifact_paths(telemetry_path, readiness_path)
    sampler = MacOsProcessMemorySampler.native()
    if sampler.revalidate(target) != target:
        raise GuardedJavaError(
            "The Java identity could not be revalidated before monitoring"
        )

    guard = OwnedJavaMemoryGuard(
        target,
        sampler.sample,
        sampler.revalidate,
        lambda guarded_target, decision: _stop_for_memory_decision(
            guarded_target,
            decision,
            sampler,
        ),
    )
    initial_result = guard.poll()
    _write_telemetry(telemetry_path, guard.telemetry_json_bytes())
    if initial_result.sample_status is not SampleStatus.AVAILABLE:
        raise GuardedJavaError(
            "The initial authoritative Java memory sample was unavailable"
        )
    _write_json_exclusive(
        readiness_path,
        {
            "schema": 1,
            "status": "ready",
            "monitor_pid": os.getpid(),
            "target": _target_payload(target),
            "telemetry": str(telemetry_path),
        },
    )

    last_persisted_at = time.monotonic_ns()
    previous_decision = initial_result.decision
    previous_status = initial_result.sample_status
    if initial_result.decision is not MemoryDecision.NORMAL:
        _print_decision_transition(initial_result.decision)
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
                _print_decision_transition(result.decision)
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
    telemetry_path: Path,
    readiness_path: Path,
) -> list[str]:
    return [
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
        "--telemetry",
        str(telemetry_path),
        "--readiness",
        str(readiness_path),
    ]


def _wait_for_monitor_readiness(
    monitor_process: subprocess.Popen[bytes],
    target: OwnedJavaProcess,
    telemetry_path: Path,
    readiness_path: Path,
) -> None:
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
    if len(content) > MAXIMUM_TELEMETRY_SIZE_BYTES:
        raise GuardedJavaError("The memory guard telemetry exceeded its size bound")
    _write_bytes_atomic(path, content)


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
) -> None:
    if decision not in (MemoryDecision.HARD, MemoryDecision.EMERGENCY):
        raise GuardedJavaError("The memory guard requested an invalid stop decision")
    _stop_owned_java_process(target, sampler, STOP_TIMEOUT_SECONDS)


def _print_decision_transition(decision: MemoryDecision) -> None:
    if decision is MemoryDecision.WARNING:
        print(
            "Etherology memory guard warning: current physical footprint exceeded "
            "8 GiB",
            flush=True,
        )
    elif decision in (MemoryDecision.HARD, MemoryDecision.EMERGENCY):
        print(
            "Etherology memory guard stopped the owned Java process after a "
            f"{decision.value} threshold",
            flush=True,
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
        process.wait(timeout=2.0)


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
    parser.add_argument("--telemetry", required=True, type=Path)
    parser.add_argument("--readiness", required=True, type=Path)
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
    try:
        return monitor_owned_java(
            target,
            arguments.telemetry,
            arguments.readiness,
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
