#!/usr/bin/env python3
"""Validate live or archived Forge dedicated-server Slitherite v21 evidence."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
from typing import Mapping

import forge_server_attrahite_evidence_v19 as v19_evidence
import forge_server_contract_v21 as contract_v21


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIRECTORY.parent.parent
STATE_ROOT = SCRIPT_DIRECTORY / ".state"
RUNTIMES_ROOT = STATE_ROOT / "runtimes"
PROFILE_MANIFEST_RELATIVE_PATH = Path(
    contract_v21.PROFILE_MANIFEST_RELATIVE_PATH
)
PROFILE_MANIFEST_PATH = REPOSITORY_ROOT / PROFILE_MANIFEST_RELATIVE_PATH
PROFILE_ID = contract_v21.PROFILE_ID
SCENARIO_ID = contract_v21.SCENARIO_ID
TASK_PATH = contract_v21.TASK_PATH
PROFILE_MARKER_NAME = ".etherology-forge-server-e2e-profile.json"
PROFILE_MANAGER = "scripts/e2e/forge_server.py"
RUNTIME_RELATIVE_PATH = Path("scripts/e2e/.state/runtimes") / PROFILE_ID
ARCHIVE_RELATIVE_PATH = Path(
    "docs/evidence/forge-1.20.1/slitherite-block-registry-server-v21"
)
DEFAULT_RUNTIME_ROOT = RUNTIMES_ROOT / PROFILE_ID
DEFAULT_ARCHIVE_ROOT = REPOSITORY_ROOT / ARCHIVE_RELATIVE_PATH
ARCHIVE_DIRECTORY_NAME = "slitherite-block-registry-server-v21"
SLITHERITE_ARCHIVE_DIRECTORY_PATTERN = re.compile(
    r"slitherite-block-registry-server-v[0-9]+"
)
ARCHIVE_MANIFEST_NAME = "archive-manifest.json"
ARCHIVE_KIND = "etherology-forge-dedicated-server-e2e-evidence"
ARCHIVE_VERIFICATION_SCOPE = (
    "archive-integrity-only-current-sources-and-built-artifacts-not-compared"
)
REPORT_RUNTIME_KIND = "loom-userdev"
EXECUTION_KIND = "loom-userdev-dedicated-server"
MAXIMUM_EVIDENCE_SIZE = 64 * 1024 * 1024
MAXIMUM_LOG_SIZE = 48 * 1024 * 1024
MAXIMUM_HANDOFF_SIZE = 16 * 1024
MAXIMUM_READINESS_SIZE = 16 * 1024
MAXIMUM_TELEMETRY_SIZE = 64 * 1024
MAXIMUM_TELEMETRY_RECORD_COUNT = 120
# The watchdog admits no more than three simultaneous Java processes, but it
# retains every exact identity seen over the launch lifetime.  Gradle workers
# can therefore make the terminal history larger than the concurrent limit.
MAXIMUM_WATCHDOG_IDENTITY_COUNT = 3
MAXIMUM_TRACKED_JAVA_PROCESS_COUNT = 16
MAXIMUM_WATCHDOG_HISTORICAL_IDENTITY_COUNT = (
    MAXIMUM_TRACKED_JAVA_PROCESS_COUNT
)
MAXIMUM_WATCHDOG_SIGNAL_ACTION_COUNT = 16
MAXIMUM_ANCHOR_ARTIFACT_SIZE = 16 * 1024
MAXIMUM_DETAIL_SIZE = 512
MAXIMUM_EXECUTABLE_SIZE = 4096
MAXIMUM_PID = (1 << 31) - 1
MAXIMUM_UNSIGNED_64_BIT_INTEGER = (1 << 64) - 1
SERVER_MAXIMUM_HEAP_ARGUMENT = "-Xmx2048m"
SERVER_MAXIMUM_HEAP_BYTES = 2 * 1024 * 1024 * 1024
MEMORY_POLICY_PROFILE = "strict-2g-v1"
WARNING_PHYS_FOOTPRINT_BYTES = 3 * 1024 * 1024 * 1024
HARD_PHYS_FOOTPRINT_BYTES = 4 * 1024 * 1024 * 1024
EMERGENCY_PHYS_FOOTPRINT_BYTES = 5 * 1024 * 1024 * 1024
MEMORY_POLICY = {
    "heap_limit_bytes": SERVER_MAXIMUM_HEAP_BYTES,
    "warning_phys_footprint_bytes": WARNING_PHYS_FOOTPRINT_BYTES,
    "hard_phys_footprint_bytes": HARD_PHYS_FOOTPRINT_BYTES,
    "emergency_phys_footprint_bytes": EMERGENCY_PHYS_FOOTPRINT_BYTES,
    "sample_interval_nanoseconds": 1_000_000_000,
    "maximum_sample_gap_nanoseconds": 2_000_000_000,
    "hard_window_sample_count": 15,
    "hard_required_high_sample_count": 10,
    "hard_final_high_sample_count": 5,
    "emergency_final_high_sample_count": 1,
}
HANDOFF_FILE_NAME = ".forge-server-java-memory-handoff.json"
ACKNOWLEDGEMENT_FILE_NAME = ".forge-server-java-memory-ready"
READINESS_FILE_NAME = ".memory-guard-ready.json"
TELEMETRY_FILE_NAME = "memory-guard-telemetry.json"
MEMORY_GUARD_DIRECTORY_NAME = "memory-guard"
WATCHDOG_READINESS_FILE_NAME = ".forge-server-launch-watchdog-ready.json"
WATCHDOG_TELEMETRY_FILE_NAME = "forge-server-launch-watchdog-telemetry.json"
LAUNCH_WATCHDOG_DIRECTORY_NAME = "launch-watchdog"
ANCHOR_READINESS_FILE_NAME = ".forge-server-launch-anchor-ready.json"
ANCHOR_START_FILE_NAME = ".forge-server-launch-anchor-start.json"
ANCHOR_CHILD_STARTED_FILE_NAME = (
    "forge-server-launch-anchor-child-started.json"
)
ANCHOR_CHILD_RESULT_FILE_NAME = "forge-server-launch-anchor-child-result.json"
ANCHOR_FINISH_FILE_NAME = ".forge-server-launch-anchor-finish.json"
STAGED_ANCHOR_SOURCE_FILE_NAME = "ForgeServerLaunchAnchor.java"
STAGED_WRAPPER_JAR_FILE_NAME = "gradle-wrapper.jar"
STAGED_WRAPPER_PROPERTIES_FILE_NAME = "gradle-wrapper.properties"
LAUNCH_ANCHOR_DIRECTORY_NAME = "launch-anchor"
ANCHOR_READINESS_SCHEMA = "etherology-forge-server-launch-anchor-ready-v1"
ANCHOR_START_SCHEMA = "etherology-forge-server-launch-anchor-start-v1"
ANCHOR_CHILD_STARTED_SCHEMA = (
    "etherology-forge-server-launch-anchor-child-started-v1"
)
ANCHOR_CHILD_RESULT_SCHEMA = (
    "etherology-forge-server-launch-anchor-child-result-v1"
)
ANCHOR_FINISH_SCHEMA = "etherology-forge-server-launch-anchor-finish-v1"
ANCHOR_JAVA_FEATURE = 21
WATCHDOG_SCHEMA = "etherology-forge-server-launch-watchdog-v1"
WATCHDOG_READINESS_SCHEMA = (
    "etherology-forge-server-launch-watchdog-ready-v1"
)
WATCHDOG_HEARTBEAT_TIMEOUT_NANOSECONDS = 10_000_000_000
WATCHDOG_PER_PROCESS_CEILING_BYTES = 5 * 1024 * 1024 * 1024
WATCHDOG_AGGREGATE_CEILING_BYTES = 6 * 1024 * 1024 * 1024
WATCHDOG_SOURCE_RELATIVE_PATH = "scripts/e2e/forge_server_launch_watchdog.py"
WATCHDOG_SOURCE_SIZE = 102_143
WATCHDOG_SOURCE_SHA256 = (
    "a0d69fd1a3477fe13e7379d9088273c331e461ece2c6a8e6ebb868309e9c02e5"
)
RUNTIME_GUARD_FILE_NAMES = (
    HANDOFF_FILE_NAME,
    ACKNOWLEDGEMENT_FILE_NAME,
    READINESS_FILE_NAME,
    TELEMETRY_FILE_NAME,
)
RUNTIME_WATCHDOG_FILE_NAMES = (
    WATCHDOG_READINESS_FILE_NAME,
    WATCHDOG_TELEMETRY_FILE_NAME,
)
RUNTIME_ANCHOR_FILE_NAMES = (
    ANCHOR_READINESS_FILE_NAME,
    ANCHOR_START_FILE_NAME,
    ANCHOR_CHILD_STARTED_FILE_NAME,
    ANCHOR_CHILD_RESULT_FILE_NAME,
    ANCHOR_FINISH_FILE_NAME,
)
STAGED_LAUNCH_INPUTS = {
    STAGED_ANCHOR_SOURCE_FILE_NAME: {
        "repository_relative_path": (
            "e2e-harness/launch-anchor/1.20.1/src/"
            "ForgeServerLaunchAnchor.java"
        ),
        "size": 41_275,
        "sha256": (
            "baca2862e9df7dd6c3da1f41583cbc4b013c45423ec77914883961dcfd202d2b"
        ),
    },
    STAGED_WRAPPER_JAR_FILE_NAME: {
        "repository_relative_path": "gradle/wrapper/gradle-wrapper.jar",
        "size": 59_821,
        "sha256": (
            "575098db54a998ff1c6770b352c3b16766c09848bee7555dab09afc34e8cf590"
        ),
    },
    STAGED_WRAPPER_PROPERTIES_FILE_NAME: {
        "repository_relative_path": "gradle/wrapper/gradle-wrapper.properties",
        "size": 339,
        "sha256": (
            "ef9f8775fd21a165a249ded98afc533818d3f6ac050f0f2f437d5285576b2257"
        ),
    },
}
RUNTIME_STAGED_LAUNCH_INPUT_FILE_NAMES = tuple(STAGED_LAUNCH_INPUTS)
RUNTIME_SUPERVISION_FILE_NAMES = (
    *RUNTIME_GUARD_FILE_NAMES,
    *RUNTIME_WATCHDOG_FILE_NAMES,
    *RUNTIME_ANCHOR_FILE_NAMES,
    *RUNTIME_STAGED_LAUNCH_INPUT_FILE_NAMES,
)
SCENARIO_PAYLOAD_PATHS = (
    "reports/report.json",
    "reports/launcher-result.json",
    "reports/done.marker",
    "logs/latest.log",
)
ARCHIVED_GUARD_PAYLOAD_PATHS = tuple(
    f"{MEMORY_GUARD_DIRECTORY_NAME}/{file_name}"
    for file_name in RUNTIME_GUARD_FILE_NAMES
)
ARCHIVED_WATCHDOG_PAYLOAD_PATHS = tuple(
    f"{LAUNCH_WATCHDOG_DIRECTORY_NAME}/{file_name}"
    for file_name in RUNTIME_WATCHDOG_FILE_NAMES
)
ARCHIVED_ANCHOR_PAYLOAD_PATHS = tuple(
    f"{LAUNCH_ANCHOR_DIRECTORY_NAME}/{file_name}"
    for file_name in (
        *RUNTIME_ANCHOR_FILE_NAMES,
        *RUNTIME_STAGED_LAUNCH_INPUT_FILE_NAMES,
    )
)
ARCHIVE_PAYLOAD_PATHS = (
    SCENARIO_PAYLOAD_PATHS
    + ARCHIVED_GUARD_PAYLOAD_PATHS
    + ARCHIVED_WATCHDOG_PAYLOAD_PATHS
    + ARCHIVED_ANCHOR_PAYLOAD_PATHS
)
SCENARIO_DIRECTORIES = {"logs", "reports"}
ARCHIVE_DIRECTORIES = {
    "logs",
    "reports",
    MEMORY_GUARD_DIRECTORY_NAME,
    LAUNCH_WATCHDOG_DIRECTORY_NAME,
    LAUNCH_ANCHOR_DIRECTORY_NAME,
}
REQUIRED_MOD_IDS = contract_v21.REQUIRED_MOD_IDS
FORBIDDEN_MOD_IDS = contract_v21.FORBIDDEN_MOD_IDS
EXPECTED_ASSERTIONS = tuple(
    zip(
        contract_v21.EXPECTED_ASSERTION_NAMES,
        contract_v21.EXPECTED_ASSERTION_VALUES,
        strict=True,
    )
)
PROFILE_FIELDS = {
    "schema",
    "profile",
    "release",
    "launch",
    "evidence",
    "profile_directories",
    "required_mod_ids",
    "forbidden_mod_ids",
}
EXPECTED_PROFILE_DIRECTORIES = [
    "config",
    "crash-reports",
    "evidence",
    "logs",
    "mods",
    "world",
]
PUBLICATION_ATTESTATION = {
    "report": "reports/report.json",
    "launcher_result": "reports/launcher-result.json",
    "completion_marker": "reports/done.marker",
    "server_log": "logs/latest.log",
    "verified_launcher_after_report_and_log_before_sealing": True,
    "verified_completion_marker_last_before_sealing": True,
    "verified_memory_guard_against_stopped_runtime_before_sealing": True,
    "verified_launch_watchdog_terminal_absence_before_sealing": True,
    "verified_launch_anchor_against_watchdog_history_before_sealing": True,
}
MEMORY_GUARD_ATTESTATION = {
    "source_runtime": RUNTIME_RELATIVE_PATH.as_posix(),
    "archive_directory": MEMORY_GUARD_DIRECTORY_NAME,
    "sample_source": "proc-pid-rusage-v4",
    "maximum_heap_argument": SERVER_MAXIMUM_HEAP_ARGUMENT,
    "policy_profile": MEMORY_POLICY_PROFILE,
    "policy": MEMORY_POLICY,
}
LAUNCH_WATCHDOG_ATTESTATION = {
    "source_runtime": RUNTIME_RELATIVE_PATH.as_posix(),
    "archive_directory": LAUNCH_WATCHDOG_DIRECTORY_NAME,
    "schema": WATCHDOG_SCHEMA,
    "readiness_schema": WATCHDOG_READINESS_SCHEMA,
    "heartbeat_timeout_nanoseconds": WATCHDOG_HEARTBEAT_TIMEOUT_NANOSECONDS,
    "maximum_java_process_count": MAXIMUM_WATCHDOG_IDENTITY_COUNT,
    "maximum_tracked_java_process_count": (
        MAXIMUM_WATCHDOG_HISTORICAL_IDENTITY_COUNT
    ),
    "per_process_current_phys_footprint_bytes": (
        WATCHDOG_PER_PROCESS_CEILING_BYTES
    ),
    "aggregate_current_phys_footprint_bytes": (
        WATCHDOG_AGGREGATE_CEILING_BYTES
    ),
    "terminal_global_java_absence_required": True,
}
LAUNCH_ANCHOR_ATTESTATION = {
    "source_runtime": RUNTIME_RELATIVE_PATH.as_posix(),
    "archive_directory": LAUNCH_ANCHOR_DIRECTORY_NAME,
    "artifact_file_names": list(RUNTIME_ANCHOR_FILE_NAMES),
    "staged_inputs": STAGED_LAUNCH_INPUTS,
    "readiness_schema": ANCHOR_READINESS_SCHEMA,
    "start_schema": ANCHOR_START_SCHEMA,
    "child_started_schema": ANCHOR_CHILD_STARTED_SCHEMA,
    "child_result_schema": ANCHOR_CHILD_RESULT_SCHEMA,
    "finish_schema": ANCHOR_FINISH_SCHEMA,
    "java_feature": ANCHOR_JAVA_FEATURE,
    "controller_parent_required_while_awaiting_start": True,
    "pre_start_timeout_seconds": 30,
    "pre_start_failure_exits": True,
    "post_start_failure_retains_process_group": True,
    "terminal_child_exit_code": 0,
    "watchdog_identity_cross_link_required": True,
}
TELEMETRY_FIELDS = {"schema", "target", "policy", "state", "records"}
TARGET_FIELDS = {
    "pid",
    "process_group_id",
    "proc_start_abstime",
    "expected_executable",
}
TELEMETRY_STATE_FIELDS = {
    "enforcement_disarmed",
    "stop_callback_invoked",
    "sample_count",
    "retained_record_count",
    "dropped_record_count",
    "last_stop_outcome",
}
TELEMETRY_RECORD_FIELDS = {
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
WATCHDOG_READINESS_FIELDS = {
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
WATCHDOG_TELEMETRY_FIELDS = {
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
WATCHDOG_HEARTBEAT_FIELDS = {
    "status",
    "last_received_at_monotonic_ns",
    "age_nanoseconds",
}
WATCHDOG_LIMIT_FIELDS = {
    "maximum_java_process_count",
    "per_process_current_phys_footprint_bytes",
    "aggregate_current_phys_footprint_bytes",
}
WATCHDOG_SAMPLE_FIELDS = {
    "pid",
    "source",
    "status",
    "current_phys_footprint_bytes",
    "detail",
}
WATCHDOG_EXACT_SAMPLE_FIELDS = {
    "expected_identity",
    "source",
    "status",
    "current_phys_footprint_bytes",
    "detail",
}
WATCHDOG_TERMINAL_FIELDS = {
    "owned_group_absent",
    "tracked_identities_absent",
    "global_java_inventory",
    "global_java_inventory_error",
    "external_java_remained",
    "exact_identity_samples",
    "exact_identity_sample_error",
}
ANCHOR_READINESS_FIELDS = {
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
ANCHOR_START_FIELDS = {"argv_sha256", "schema", "token"}
ANCHOR_CHILD_STARTED_FIELDS = {
    "argv_sha256",
    "executable",
    "pid",
    "schema",
    "token",
}
ANCHOR_CHILD_RESULT_FIELDS = {
    "argv_sha256",
    "exit_code",
    "pid",
    "schema",
    "started",
    "token",
}
ANCHOR_FINISH_FIELDS = {
    "argv_sha256",
    "child_exit_code",
    "child_pid",
    "child_started",
    "schema",
    "token",
}


class EvidenceError(RuntimeError):
    """Reports a fail-closed Slitherite v21 evidence-contract violation."""


@dataclass(frozen=True)
class ProfileRecord:
    """Identifies the exact tracked profile used by one native launch."""

    relative_path: str
    size: int
    sha256: str


@dataclass(frozen=True)
class EvidenceSummary:
    """Summarizes one verified live or archived native run."""

    profile_id: str
    assertion_count: int
    log_sha256: str


@dataclass(frozen=True)
class ValidatedPayload:
    """Carries validated scenario payload facts into archive sealing."""

    profile_record: ProfileRecord
    assertion_count: int
    log_sha256: str


@dataclass(frozen=True)
class GuardIdentity:
    """Carries the exact Java target recorded by the external memory guard."""

    pid: int
    process_group_id: int
    proc_start_abstime: int
    expected_executable: str


@dataclass(frozen=True)
class GuardEvidence:
    """Carries cross-file memory-guard identity after strict validation."""

    run_token: str
    identity: GuardIdentity
    terminal_observed_at_monotonic_ns: int


@dataclass(frozen=True)
class LaunchWatchdogEvidence:
    """Carries exact launch identities retained by terminal supervision."""

    anchor: GuardIdentity
    server: GuardIdentity
    controller_pid: int
    terminal_identities: tuple[GuardIdentity, ...]


@dataclass(frozen=True)
class LaunchAnchorEvidence:
    """Carries authenticated broker artifacts and their direct child."""

    anchor: GuardIdentity
    child_pid: int
    child_executable: str
    arguments_sha256: str
    token: str
    artifact_contents: dict[str, bytes]


@dataclass(frozen=True)
class ValidatedArchiveManifest:
    """Carries exact frozen provenance records into archive validation."""

    profile_record: ProfileRecord
    files: dict[str, dict[str, object]]


def sha256_file(path: Path) -> str:
    """Returns the SHA-256 digest of one evidence file."""

    return v19_evidence.sha256_file(path)


def exact_json_value(actual: object, expected: object) -> bool:
    """Compares JSON values without treating booleans as integers."""

    return v19_evidence.exact_json_value(actual, expected)


def require_regular_file(path: Path, description: str) -> Path:
    """Requires a non-linked regular file."""

    try:
        return v19_evidence.require_regular_file(path, description)
    except v19_evidence.EvidenceError as exception:
        raise EvidenceError(str(exception)) from exception


def require_directory(path: Path, description: str) -> Path:
    """Requires a non-linked directory."""

    try:
        return v19_evidence.require_directory(path, description)
    except v19_evidence.EvidenceError as exception:
        raise EvidenceError(str(exception)) from exception


def repository_anchor(path: Path, relative_path: Path, description: str) -> Path:
    """Finds the lexical repository root for one exact relative path."""

    try:
        return v19_evidence.repository_anchor(path, relative_path, description)
    except v19_evidence.EvidenceError as exception:
        raise EvidenceError(str(exception)) from exception


def require_no_symlink_components(
    path: Path,
    anchor: Path,
    description: str,
) -> None:
    """Rejects a path whose repository-owned chain contains a symlink."""

    try:
        v19_evidence.require_no_symlink_components(path, anchor, description)
    except v19_evidence.EvidenceError as exception:
        raise EvidenceError(str(exception)) from exception


def require_json_object(path: Path, description: str) -> dict[str, object]:
    """Reads a regular UTF-8 JSON file whose root is an object."""

    try:
        return v19_evidence.require_json_object(path, description)
    except v19_evidence.EvidenceError as exception:
        raise EvidenceError(str(exception)) from exception


def validate_hex_digest(value: object, description: str) -> str:
    """Requires one lowercase SHA-256 value."""

    try:
        return v19_evidence.validate_hex_digest(value, description)
    except v19_evidence.EvidenceError as exception:
        raise EvidenceError(str(exception)) from exception


def _positive_integer(value: object, description: str, maximum: int) -> int:
    if type(value) is not int or value <= 0 or value > maximum:
        raise EvidenceError(f"The {description} is not a positive bounded integer")
    return value


def _nonnegative_integer(value: object, description: str) -> int:
    if (
        type(value) is not int
        or value < 0
        or value > MAXIMUM_UNSIGNED_64_BIT_INTEGER
    ):
        raise EvidenceError(f"The {description} is not a non-negative integer")
    return value


def _bounded_text(value: object, description: str, maximum_size: int) -> str:
    if (
        not isinstance(value, str)
        or len(value.encode("utf-8")) > maximum_size
        or "\x00" in value
        or "\r" in value
    ):
        raise EvidenceError(f"The {description} is invalid or unbounded")
    return value


def _read_bounded_newline_json(
    path: Path,
    description: str,
    maximum_size: int,
) -> dict[str, object]:
    require_regular_file(path, description)
    try:
        content = path.read_bytes()
    except OSError as exception:
        raise EvidenceError(f"Cannot read {description}: {exception}") from exception
    if not content or len(content) > maximum_size or not content.endswith(b"\n"):
        raise EvidenceError(
            f"The {description} is empty, oversized, or not newline-terminated"
        )
    try:
        value = json.loads(content.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise EvidenceError(f"Cannot decode {description}: {exception}") from exception
    if not isinstance(value, dict):
        raise EvidenceError(f"The {description} is not a JSON object")
    return value


def _read_canonical_anchor_json(
    path: Path,
    description: str,
) -> tuple[dict[str, object], bytes]:
    """Reads one anchor artifact and requires its exact canonical encoding."""

    value = _read_bounded_newline_json(
        path,
        description,
        MAXIMUM_ANCHOR_ARTIFACT_SIZE,
    )
    try:
        content = path.read_bytes()
    except OSError as exception:
        raise EvidenceError(f"Cannot reread {description}: {exception}") from exception
    canonical = (
        json.dumps(
            value,
            ensure_ascii=True,
            separators=(",", ":"),
            sort_keys=True,
        )
        + "\n"
    ).encode("utf-8")
    if content != canonical:
        raise EvidenceError(f"The {description} is not canonical JSON")
    return value, content


def validate_profile_shape(profile: dict[str, object]) -> None:
    """Validates every field in the v21 dedicated-server profile manifest."""

    if set(profile) != PROFILE_FIELDS or type(profile.get("schema")) is not int:
        raise EvidenceError("The Forge server profile field inventory is invalid")
    if profile["schema"] != 1:
        raise EvidenceError("The Forge server profile schema is unsupported")
    if profile.get("profile") != {
        "id": PROFILE_ID,
        "runtime_directory": PROFILE_ID,
        "game_directory": "game",
    }:
        raise EvidenceError("The Forge server profile identity is invalid")
    release = profile.get("release")
    if not isinstance(release, dict) or release != {
        "matrix": "release/release-matrix.json",
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
    } or type(release.get("java")) is not int:
        raise EvidenceError("The Forge server release identity is invalid")
    launch = profile.get("launch")
    expected_launch = {
        "kind": REPORT_RUNTIME_KIND,
        "task_path": TASK_PATH,
        "scenario": SCENARIO_ID,
        "maximum_memory_mb": 2048,
        "persistent_watchdog": {
            "schema": WATCHDOG_SCHEMA,
            "source": {
                "relative_path": WATCHDOG_SOURCE_RELATIVE_PATH,
                "size": WATCHDOG_SOURCE_SIZE,
                "sha256": WATCHDOG_SOURCE_SHA256,
            },
            "readiness_file": WATCHDOG_READINESS_FILE_NAME,
            "telemetry_file": WATCHDOG_TELEMETRY_FILE_NAME,
            "heartbeat_timeout_seconds": 10,
            "maximum_java_process_count": MAXIMUM_WATCHDOG_IDENTITY_COUNT,
            "per_process_current_phys_footprint_bytes": (
                WATCHDOG_PER_PROCESS_CEILING_BYTES
            ),
            "aggregate_current_phys_footprint_bytes": (
                WATCHDOG_AGGREGATE_CEILING_BYTES
            ),
            "terminal_global_java_absence_required": True,
        },
        "launch_anchor": contract_v21.LAUNCH_ANCHOR_POLICY,
        "pre_acknowledgement": contract_v21.PRE_ACKNOWLEDGEMENT_POLICY,
    }
    if (
        not isinstance(launch, dict)
        or not exact_json_value(launch, expected_launch)
        or type(launch.get("maximum_memory_mb")) is not int
    ):
        raise EvidenceError("The Forge server launch identity is invalid")
    if profile.get("evidence") != {
        "directory": "evidence",
        "scenario_directory": SCENARIO_ID,
        "report": "reports/report.json",
        "launcher_result": "reports/launcher-result.json",
        "completion_marker": "reports/done.marker",
        "server_log": "logs/latest.log",
    }:
        raise EvidenceError("The Forge server evidence layout is invalid")
    if profile.get("profile_directories") != EXPECTED_PROFILE_DIRECTORIES:
        raise EvidenceError("The Forge server directory inventory is invalid")
    if profile.get("required_mod_ids") != list(REQUIRED_MOD_IDS):
        raise EvidenceError("The Forge server required mod inventory is invalid")
    if profile.get("forbidden_mod_ids") != list(FORBIDDEN_MOD_IDS):
        raise EvidenceError("The Forge server forbidden mod inventory is invalid")


def load_profile_manifest(path: Path = PROFILE_MANIFEST_PATH) -> ProfileRecord:
    """Loads and fingerprints the exact tracked v21 profile."""

    repository_root = repository_anchor(
        path,
        PROFILE_MANIFEST_RELATIVE_PATH,
        "Forge server profile manifest",
    )
    require_no_symlink_components(
        path,
        repository_root,
        "Forge server profile manifest",
    )
    profile = require_json_object(path, "Forge server profile manifest")
    validate_profile_shape(profile)
    record = ProfileRecord(
        relative_path=PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
        size=path.stat().st_size,
        sha256=sha256_file(path),
    )
    if (
        record.size != contract_v21.PROFILE_MANIFEST_SIZE
        or record.sha256 != contract_v21.PROFILE_MANIFEST_SHA256
    ):
        raise EvidenceError(
            "The Forge server profile bytes differ from the immutable v21 contract"
        )
    return record


def expected_profile_marker(profile_record: ProfileRecord) -> dict[str, object]:
    """Builds the exact repository-owned runtime descriptor."""

    return {
        "schema": 1,
        "profile_id": PROFILE_ID,
        "managed_by": PROFILE_MANAGER,
        "profile_manifest": {
            "relative_path": profile_record.relative_path,
            "size": profile_record.size,
            "sha256": profile_record.sha256,
        },
        "isolation": {
            "scope": "repository-owned-ignored-state",
            "source_profiles": [],
        },
        "release": {
            "artifact_node": "forge-1.20.1",
            "minecraft": "1.20.1",
            "loader": "forge",
            "loader_version": "47.4.9",
            "java": 17,
        },
        "launch": {
            "task_path": TASK_PATH,
            "scenario": SCENARIO_ID,
        },
    }


def validate_profile_marker(runtime: Path, profile_record: ProfileRecord) -> None:
    """Requires the runner's exact non-linked ownership marker."""

    marker = require_json_object(
        runtime / PROFILE_MARKER_NAME,
        "Forge server runtime profile marker",
    )
    if not exact_json_value(marker, expected_profile_marker(profile_record)):
        raise EvidenceError(
            "The Forge server runtime marker differs from its tracked profile"
        )


def validate_report(report: dict[str, object]) -> None:
    """Validates the report through the immutable profile-v21 contract."""

    try:
        contract_v21.validate_probe_report(
            report,
            list(REQUIRED_MOD_IDS),
            list(FORBIDDEN_MOD_IDS),
        )
    except contract_v21.V21ContractError as exception:
        raise EvidenceError(
            f"The Forge server report violates the v21 contract: {exception}"
        ) from exception


def validate_launcher_result(
    launcher: dict[str, object],
    log_path: Path,
    expected_profile: ProfileRecord,
    watchdog_paths: Mapping[str, Path],
    anchor_paths: Mapping[str, Path],
    anchor_evidence: LaunchAnchorEvidence,
) -> None:
    """Validates zero-exit launcher and pinned-artifact provenance."""

    if set(launcher) != {
        "schema",
        "profile_id",
        "scenario",
        "task_path",
        "exit_code",
        "timed_out",
        "profile_manifest",
        "server_log",
        "launch_watchdog",
        "launch_anchor",
    }:
        raise EvidenceError("The Forge server launcher result fields changed")
    if (
        type(launcher.get("schema")) is not int
        or launcher.get("schema") != 1
        or launcher.get("profile_id") != PROFILE_ID
        or launcher.get("scenario") != SCENARIO_ID
        or launcher.get("task_path") != TASK_PATH
        or type(launcher.get("exit_code")) is not int
        or launcher.get("exit_code") != 0
        or launcher.get("timed_out") is not False
    ):
        raise EvidenceError("The Forge server launcher did not exit normally")
    profile = launcher.get("profile_manifest")
    if not isinstance(profile, dict) or set(profile) != {
        "relative_path",
        "size",
        "sha256",
    }:
        raise EvidenceError("The Forge server launcher profile provenance is malformed")
    launcher_profile = ProfileRecord(
        relative_path=str(profile.get("relative_path")),
        size=_positive_integer(
            profile.get("size"),
            "launcher profile size",
            MAXIMUM_UNSIGNED_64_BIT_INTEGER,
        ),
        sha256=validate_hex_digest(
            profile.get("sha256"),
            "Forge server launcher profile",
        ),
    )
    if launcher_profile != expected_profile:
        raise EvidenceError(
            "The Forge server launcher profile differs from expected provenance"
        )
    server_log = launcher.get("server_log")
    if not isinstance(server_log, dict) or set(server_log) != {
        "relative_path",
        "size",
        "sha256",
    }:
        raise EvidenceError("The Forge server launcher log provenance is malformed")
    if (
        server_log.get("relative_path") != "logs/latest.log"
        or type(server_log.get("size")) is not int
        or server_log.get("size") != log_path.stat().st_size
        or validate_hex_digest(
            server_log.get("sha256"),
            "Forge server launcher log",
        )
        != sha256_file(log_path)
    ):
        raise EvidenceError(
            "The Forge server copied log differs from launcher provenance"
        )
    launch_watchdog = launcher.get("launch_watchdog")
    expected_watchdog_names = set(RUNTIME_WATCHDOG_FILE_NAMES)
    if (
        not isinstance(launch_watchdog, dict)
        or set(launch_watchdog) != expected_watchdog_names
        or set(watchdog_paths) != expected_watchdog_names
    ):
        raise EvidenceError(
            "The Forge server launcher watchdog provenance is malformed"
        )
    for file_name in RUNTIME_WATCHDOG_FILE_NAMES:
        record = launch_watchdog.get(file_name)
        if not isinstance(record, dict) or set(record) != {
            "relative_path",
            "size",
            "sha256",
        }:
            raise EvidenceError(
                "The Forge server launcher watchdog provenance is malformed: "
                f"{file_name}"
            )
        artifact_path = require_regular_file(
            watchdog_paths[file_name],
            f"Forge server launch watchdog artifact {file_name}",
        )
        if (
            record.get("relative_path") != file_name
            or type(record.get("size")) is not int
            or record.get("size") != artifact_path.stat().st_size
            or validate_hex_digest(
                record.get("sha256"),
                f"Forge server launcher watchdog artifact {file_name}",
            )
            != sha256_file(artifact_path)
        ):
            raise EvidenceError(
                "The Forge server launch watchdog artifact differs from "
                f"launcher provenance: {file_name}"
            )
    launch_anchor = launcher.get("launch_anchor")
    expected_anchor_names = set(RUNTIME_ANCHOR_FILE_NAMES)
    if (
        not isinstance(launch_anchor, dict)
        or set(launch_anchor) != expected_anchor_names
        or set(anchor_paths) != expected_anchor_names
        or set(anchor_evidence.artifact_contents) != expected_anchor_names
    ):
        raise EvidenceError(
            "The Forge server launcher anchor provenance is malformed"
        )
    for file_name in RUNTIME_ANCHOR_FILE_NAMES:
        record = launch_anchor.get(file_name)
        if not isinstance(record, dict) or set(record) != {
            "relative_path",
            "size",
            "sha256",
        }:
            raise EvidenceError(
                "The Forge server launcher anchor provenance is malformed: "
                f"{file_name}"
            )
        artifact_path = require_regular_file(
            anchor_paths[file_name],
            f"Forge server launch anchor artifact {file_name}",
        )
        try:
            current_content = artifact_path.read_bytes()
        except OSError as exception:
            raise EvidenceError(
                f"Cannot reread Forge server launch anchor artifact {file_name}: "
                f"{exception}"
            ) from exception
        verified_content = anchor_evidence.artifact_contents[file_name]
        if (
            current_content != verified_content
            or record.get("relative_path") != file_name
            or type(record.get("size")) is not int
            or record.get("size") != len(verified_content)
            or validate_hex_digest(
                record.get("sha256"),
                f"Forge server launcher anchor artifact {file_name}",
            )
            != hashlib.sha256(verified_content).hexdigest()
        ):
            raise EvidenceError(
                "The Forge server launch anchor artifact differs from "
                f"launcher provenance: {file_name}"
            )


def validate_log(log_path: Path) -> str:
    """Validates the cumulative probe lifecycle and fatal-marker absence."""

    try:
        return v19_evidence.validate_log(log_path)
    except v19_evidence.EvidenceError as exception:
        raise EvidenceError(str(exception)) from exception


def validate_publication_order(scenario_root: Path) -> None:
    """Requires report, copied log, launcher result, then completion."""

    try:
        v19_evidence.validate_publication_order(scenario_root)
    except v19_evidence.EvidenceError as exception:
        raise EvidenceError(str(exception)) from exception


def validate_inventory(
    root: Path,
    expected_files: set[str],
    expected_directories: set[str],
    description: str,
) -> None:
    """Validates one exact, bounded, non-linked evidence tree."""

    require_directory(root, description)
    files: set[str] = set()
    directories: set[str] = set()
    for path in root.rglob("*"):
        relative_path = path.relative_to(root).as_posix()
        if path.is_symlink():
            raise EvidenceError(f"{description} contains a linked entry: {relative_path}")
        if path.is_file():
            files.add(relative_path)
        elif path.is_dir():
            directories.add(relative_path)
        else:
            raise EvidenceError(f"{description} contains a special entry: {relative_path}")
    if files != expected_files or directories != expected_directories:
        raise EvidenceError(
            f"The {description} inventory changed: "
            f"missing_files={sorted(expected_files - files)}, "
            f"unexpected_files={sorted(files - expected_files)}, "
            f"missing_directories={sorted(expected_directories - directories)}, "
            f"unexpected_directories={sorted(directories - expected_directories)}"
        )
    total_size = sum(
        path.stat().st_size
        for path in root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if total_size > MAXIMUM_EVIDENCE_SIZE:
        raise EvidenceError(f"The {description} exceeds the size bound")


def validate_scenario_inventory(scenario_root: Path) -> None:
    """Requires exactly the four native scenario payloads."""

    validate_inventory(
        scenario_root,
        set(SCENARIO_PAYLOAD_PATHS),
        SCENARIO_DIRECTORIES,
        "Forge server scenario evidence",
    )


def validate_archive_inventory(archive_root: Path, include_manifest: bool) -> None:
    """Requires the exact eighteen- or nineteen-file v21 archive tree."""

    expected_files = set(ARCHIVE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    validate_inventory(
        archive_root,
        expected_files,
        ARCHIVE_DIRECTORIES,
        "Forge server archive",
    )


def validate_payload(
    scenario_root: Path,
    expected_profile: ProfileRecord,
    enforce_publication_order: bool,
    watchdog_paths: Mapping[str, Path],
    anchor_paths: Mapping[str, Path],
    anchor_evidence: LaunchAnchorEvidence,
) -> ValidatedPayload:
    """Validates all self-contained Slitherite scenario payload semantics."""

    done_path = require_regular_file(
        scenario_root / "reports" / "done.marker",
        "Forge server completion marker",
    )
    if done_path.read_bytes() != b"complete\n":
        raise EvidenceError("The Forge server completion marker has invalid content")
    report = require_json_object(
        scenario_root / "reports" / "report.json",
        "Forge server probe report",
    )
    validate_report(report)
    log_path = scenario_root / "logs" / "latest.log"
    log_sha256 = validate_log(log_path)
    launcher = require_json_object(
        scenario_root / "reports" / "launcher-result.json",
        "Forge server launcher result",
    )
    validate_launcher_result(
        launcher,
        log_path,
        expected_profile,
        watchdog_paths,
        anchor_paths,
        anchor_evidence,
    )
    if enforce_publication_order:
        validate_publication_order(scenario_root)
    return ValidatedPayload(
        profile_record=expected_profile,
        assertion_count=len(EXPECTED_ASSERTIONS),
        log_sha256=log_sha256,
    )


def runtime_guard_paths(runtime: Path) -> dict[str, Path]:
    """Returns the four fixed guard artifacts inside one owned runtime."""

    return {file_name: runtime / file_name for file_name in RUNTIME_GUARD_FILE_NAMES}


def runtime_watchdog_paths(runtime: Path) -> dict[str, Path]:
    """Returns the two fixed watchdog artifacts inside one owned runtime."""

    return {
        file_name: runtime / file_name
        for file_name in RUNTIME_WATCHDOG_FILE_NAMES
    }


def runtime_anchor_paths(runtime: Path) -> dict[str, Path]:
    """Returns the five fixed broker artifacts inside one owned runtime."""

    return {file_name: runtime / file_name for file_name in RUNTIME_ANCHOR_FILE_NAMES}


def runtime_staged_launch_input_paths(runtime: Path) -> dict[str, Path]:
    """Returns the three immutable launch inputs inside one owned runtime."""

    return {
        file_name: runtime / file_name
        for file_name in RUNTIME_STAGED_LAUNCH_INPUT_FILE_NAMES
    }


def archived_guard_paths(archive_root: Path) -> dict[str, Path]:
    """Returns the four fixed guard artifacts inside one frozen archive."""

    guard_root = archive_root / MEMORY_GUARD_DIRECTORY_NAME
    return {
        file_name: guard_root / file_name for file_name in RUNTIME_GUARD_FILE_NAMES
    }


def archived_watchdog_paths(archive_root: Path) -> dict[str, Path]:
    """Returns the two fixed watchdog artifacts inside one frozen archive."""

    watchdog_root = archive_root / LAUNCH_WATCHDOG_DIRECTORY_NAME
    return {
        file_name: watchdog_root / file_name
        for file_name in RUNTIME_WATCHDOG_FILE_NAMES
    }


def archived_anchor_paths(archive_root: Path) -> dict[str, Path]:
    """Returns the five fixed broker artifacts inside one frozen archive."""

    anchor_root = archive_root / LAUNCH_ANCHOR_DIRECTORY_NAME
    return {
        file_name: anchor_root / file_name
        for file_name in RUNTIME_ANCHOR_FILE_NAMES
    }


def archived_staged_launch_input_paths(
    archive_root: Path,
) -> dict[str, Path]:
    """Returns the three immutable launch inputs inside one frozen archive."""

    anchor_root = archive_root / LAUNCH_ANCHOR_DIRECTORY_NAME
    return {
        file_name: anchor_root / file_name
        for file_name in RUNTIME_STAGED_LAUNCH_INPUT_FILE_NAMES
    }


def validate_staged_launch_inputs(
    paths: Mapping[str, Path],
    require_private_permissions: bool,
) -> None:
    """Requires all staged launch inputs to match their immutable pins."""

    expected_names = set(RUNTIME_STAGED_LAUNCH_INPUT_FILE_NAMES)
    if set(paths) != expected_names:
        raise EvidenceError("The staged launch input inventory changed")
    for file_name, specification in STAGED_LAUNCH_INPUTS.items():
        path = require_regular_file(
            paths[file_name],
            f"Forge server staged launch input {file_name}",
        )
        metadata = path.stat(follow_symlinks=False)
        if metadata.st_nlink != 1:
            raise EvidenceError(
                f"The staged launch input is multiply linked: {file_name}"
            )
        if require_private_permissions and (
            metadata.st_uid != os.getuid()
            or stat.S_IMODE(metadata.st_mode) != 0o400
        ):
            raise EvidenceError(
                "The live staged launch input is not owner-read-only: "
                f"{file_name}"
            )
        if (
            metadata.st_size != specification["size"]
            or sha256_file(path) != specification["sha256"]
        ):
            raise EvidenceError(
                f"The staged launch input differs from its immutable pin: {file_name}"
            )


def _validate_executable(value: object) -> str:
    executable = _bounded_text(
        value,
        "server Java executable",
        MAXIMUM_EXECUTABLE_SIZE,
    )
    path = Path(executable)
    if (
        not path.is_absolute()
        or path.name != "java"
        or ".." in path.parts
        or "\n" in executable
    ):
        raise EvidenceError("The server Java executable is not one safe absolute path")
    return executable


def _validate_target(value: object) -> GuardIdentity:
    if not isinstance(value, dict) or set(value) != TARGET_FIELDS:
        raise EvidenceError("The memory guard target field inventory changed")
    return GuardIdentity(
        pid=_positive_integer(value.get("pid"), "server Java PID", MAXIMUM_PID),
        process_group_id=_positive_integer(
            value.get("process_group_id"),
            "server launch process-group ID",
            MAXIMUM_PID,
        ),
        proc_start_abstime=_positive_integer(
            value.get("proc_start_abstime"),
            "server Java process start time",
            MAXIMUM_UNSIGNED_64_BIT_INTEGER,
        ),
        expected_executable=_validate_executable(value.get("expected_executable")),
    )


def _target_payload(identity: GuardIdentity) -> dict[str, object]:
    return {
        "pid": identity.pid,
        "process_group_id": identity.process_group_id,
        "proc_start_abstime": identity.proc_start_abstime,
        "expected_executable": identity.expected_executable,
    }


def _validate_telemetry_reference(
    value: object,
    expected_live_runtime: Path | None,
) -> str:
    reference = _bounded_text(
        value,
        "memory guard telemetry reference",
        MAXIMUM_EXECUTABLE_SIZE,
    )
    path = Path(reference)
    if not path.is_absolute() or ".." in path.parts or "\n" in reference:
        raise EvidenceError("The memory guard telemetry reference is unsafe")
    if expected_live_runtime is not None:
        expected = expected_live_runtime / TELEMETRY_FILE_NAME
        if reference != str(expected):
            raise EvidenceError("The memory guard telemetry reference is foreign")
        return reference
    expected_suffix = (*RUNTIME_RELATIVE_PATH.parts, TELEMETRY_FILE_NAME)
    if len(path.parts) <= len(expected_suffix) or path.parts[-len(expected_suffix):] != expected_suffix:
        raise EvidenceError("The archived memory guard telemetry reference changed")
    return reference


def _expected_memory_decision(
    footprints: list[tuple[int, int]],
) -> str:
    emergency_count = MEMORY_POLICY["emergency_final_high_sample_count"]
    hard_window_count = MEMORY_POLICY["hard_window_sample_count"]
    hard_required_count = MEMORY_POLICY["hard_required_high_sample_count"]
    hard_final_count = MEMORY_POLICY["hard_final_high_sample_count"]
    if len(footprints) >= emergency_count and all(
        footprint > EMERGENCY_PHYS_FOOTPRINT_BYTES
        for _timestamp, footprint in footprints[-emergency_count:]
    ):
        return "emergency"
    hard_window = footprints[-hard_window_count:]
    if (
        len(hard_window) == hard_window_count
        and sum(
            footprint > HARD_PHYS_FOOTPRINT_BYTES
            for _timestamp, footprint in hard_window
        )
        >= hard_required_count
        and all(
            footprint > HARD_PHYS_FOOTPRINT_BYTES
            for _timestamp, footprint in hard_window[-hard_final_count:]
        )
    ):
        return "hard"
    if footprints[-1][1] > WARNING_PHYS_FOOTPRINT_BYTES:
        return "warning"
    return "normal"


def _validate_telemetry_records(
    records: object,
    state: dict[str, object],
) -> int:
    if (
        not isinstance(records, list)
        or not records
        or len(records) > MAXIMUM_TELEMETRY_RECORD_COUNT
    ):
        raise EvidenceError("The memory guard telemetry record inventory is invalid")
    sample_count = _positive_integer(
        state.get("sample_count"),
        "memory guard sample count",
        (1 << 63) - 1,
    )
    retained_count = _nonnegative_integer(
        state.get("retained_record_count"),
        "memory guard retained record count",
    )
    dropped_count = _nonnegative_integer(
        state.get("dropped_record_count"),
        "memory guard dropped record count",
    )
    if (
        retained_count != len(records)
        or sample_count < retained_count
        or dropped_count != sample_count - retained_count
    ):
        raise EvidenceError("The memory guard telemetry record accounting changed")

    previous_timestamp: int | None = None
    policy_footprints: list[tuple[int, int]] = []
    available_count = 0
    for index, record in enumerate(records):
        if not isinstance(record, dict) or set(record) != TELEMETRY_RECORD_FIELDS:
            raise EvidenceError("The memory guard telemetry record fields changed")
        timestamp = _nonnegative_integer(
            record.get("observed_at_monotonic_ns"),
            "memory guard observation timestamp",
        )
        if previous_timestamp is not None:
            if timestamp <= previous_timestamp:
                raise EvidenceError("The memory guard timestamps are not increasing")
            if (
                timestamp - previous_timestamp
                >= MEMORY_POLICY["maximum_sample_gap_nanoseconds"]
            ):
                policy_footprints.clear()
        previous_timestamp = timestamp
        if record.get("source") != "proc-pid-rusage-v4":
            raise EvidenceError("The memory guard used a non-authoritative source")
        status = record.get("status")
        if status == "available":
            available_count += 1
            if record.get("identity_matches_target") is not True:
                raise EvidenceError("A memory sample did not match the guarded target")
            current = _nonnegative_integer(
                record.get("current_phys_footprint_bytes"),
                "current physical footprint",
            )
            resident = _nonnegative_integer(
                record.get("resident_size_bytes"),
                "resident memory size",
            )
            lifetime = _nonnegative_integer(
                record.get("lifetime_max_phys_footprint_bytes"),
                "lifetime maximum physical footprint",
            )
            if lifetime < current:
                raise EvidenceError("The lifetime footprint is below the current footprint")
            if record.get("virtual_size_bytes") is not None:
                raise EvidenceError("Authoritative telemetry unexpectedly records virtual size")
            if record.get("detail") != "":
                raise EvidenceError("An available memory sample contains error detail")
            policy_footprints.append((timestamp, current))
            expected_decision = _expected_memory_decision(policy_footprints)
            if expected_decision in {"hard", "emergency"}:
                raise EvidenceError("The accepted run crossed a memory stop threshold")
            if record.get("decision") != expected_decision:
                raise EvidenceError("The memory guard decision does not match its sample")
            if record.get("stop_outcome") != "not-required":
                raise EvidenceError("The accepted run requested a memory stop")
            continue
        if status != "missing" or index != len(records) - 1:
            raise EvidenceError("The accepted run contains unavailable memory telemetry")
        if (
            record.get("identity_matches_target") is not None
            or record.get("current_phys_footprint_bytes") is not None
            or record.get("resident_size_bytes") is not None
            or record.get("virtual_size_bytes") is not None
            or record.get("lifetime_max_phys_footprint_bytes") is not None
            or record.get("decision") != "not-enforceable"
            or record.get("stop_outcome") != "not-required"
        ):
            raise EvidenceError("The terminal missing memory sample is malformed")
        detail = _bounded_text(
            record.get("detail"),
            "terminal missing-sample detail",
            MAXIMUM_DETAIL_SIZE,
        )
        if not detail.startswith("target process is missing:"):
            raise EvidenceError("The terminal missing-sample detail changed")
        policy_footprints.clear()
    if available_count == 0:
        raise EvidenceError("The memory guard recorded no authoritative live sample")
    if records[-1].get("status") != "missing":
        raise EvidenceError(
            "The memory guard did not retain a terminal authoritative missing sample"
        )
    if previous_timestamp is None:
        raise EvidenceError("The memory guard has no terminal timestamp")
    return previous_timestamp


def validate_memory_guard(
    paths: Mapping[str, Path],
    artifact_anchor: Path,
    expected_live_runtime: Path | None,
) -> GuardEvidence:
    """Validates all four retained guard artifacts and their cross-links."""

    if set(paths) != set(RUNTIME_GUARD_FILE_NAMES):
        raise EvidenceError("The memory guard artifact mapping changed")
    for file_name, path in paths.items():
        require_no_symlink_components(path, artifact_anchor, f"memory guard {file_name}")

    handoff = _read_bounded_newline_json(
        paths[HANDOFF_FILE_NAME],
        "server Java memory handoff",
        MAXIMUM_HANDOFF_SIZE,
    )
    if set(handoff) != {
        "schema",
        "run_token",
        "pid",
        "executable",
        "java_feature",
        "maximum_heap_bytes",
        "maximum_heap_arguments",
    }:
        raise EvidenceError("The server Java memory handoff fields changed")
    run_token = handoff.get("run_token")
    executable = _validate_executable(handoff.get("executable"))
    java_pid = _positive_integer(handoff.get("pid"), "server Java PID", MAXIMUM_PID)
    if (
        type(handoff.get("schema")) is not int
        or handoff.get("schema") != 1
        or not isinstance(run_token, str)
        or re.fullmatch(r"[0-9a-f]{64}", run_token) is None
        or type(handoff.get("java_feature")) is not int
        or handoff.get("java_feature") != 17
        or type(handoff.get("maximum_heap_bytes")) is not int
        or handoff.get("maximum_heap_bytes") != SERVER_MAXIMUM_HEAP_BYTES
        or not exact_json_value(
            handoff.get("maximum_heap_arguments"),
            [SERVER_MAXIMUM_HEAP_ARGUMENT],
        )
    ):
        raise EvidenceError("The server Java memory handoff contract changed")

    acknowledgement = require_regular_file(
        paths[ACKNOWLEDGEMENT_FILE_NAME],
        "server Java memory acknowledgement",
    )
    expected_acknowledgement = f"token={run_token}\n".encode("ascii")
    if acknowledgement.read_bytes() != expected_acknowledgement:
        raise EvidenceError("The server Java memory acknowledgement token changed")

    readiness = _read_bounded_newline_json(
        paths[READINESS_FILE_NAME],
        "memory guard readiness record",
        MAXIMUM_READINESS_SIZE,
    )
    if set(readiness) != {
        "schema",
        "status",
        "monitor_pid",
        "target",
        "telemetry",
        "memory_policy_name",
    }:
        raise EvidenceError("The memory guard readiness fields changed")
    identity = _validate_target(readiness.get("target"))
    monitor_pid = _positive_integer(
        readiness.get("monitor_pid"),
        "memory guard monitor PID",
        MAXIMUM_PID,
    )
    if (
        type(readiness.get("schema")) is not int
        or readiness.get("schema") != 1
        or readiness.get("status") != "ready"
        or readiness.get("memory_policy_name") != MEMORY_POLICY_PROFILE
        or identity.pid != java_pid
        or identity.expected_executable != executable
        or identity.process_group_id == identity.pid
        or monitor_pid in {identity.pid, identity.process_group_id}
    ):
        raise EvidenceError("The memory guard readiness identity changed")
    _validate_telemetry_reference(
        readiness.get("telemetry"),
        expected_live_runtime,
    )

    telemetry = _read_bounded_newline_json(
        paths[TELEMETRY_FILE_NAME],
        "memory guard telemetry",
        MAXIMUM_TELEMETRY_SIZE,
    )
    if set(telemetry) != TELEMETRY_FIELDS:
        raise EvidenceError("The memory guard telemetry fields changed")
    if type(telemetry.get("schema")) is not int or telemetry.get("schema") != 1:
        raise EvidenceError("The memory guard telemetry schema changed")
    telemetry_identity = _validate_target(telemetry.get("target"))
    if telemetry_identity != identity:
        raise EvidenceError("The memory guard target changed across artifacts")
    if not exact_json_value(telemetry.get("policy"), MEMORY_POLICY):
        raise EvidenceError("The memory guard policy changed")
    state = telemetry.get("state")
    if not isinstance(state, dict) or set(state) != TELEMETRY_STATE_FIELDS:
        raise EvidenceError("The memory guard state fields changed")
    if (
        state.get("enforcement_disarmed") is not False
        or state.get("stop_callback_invoked") is not False
        or state.get("last_stop_outcome") != "not-required"
    ):
        raise EvidenceError("The accepted run did not retain active memory enforcement")
    terminal_observed_at = _validate_telemetry_records(
        telemetry.get("records"),
        state,
    )
    return GuardEvidence(
        run_token=run_token,
        identity=identity,
        terminal_observed_at_monotonic_ns=terminal_observed_at,
    )


def _validate_watchdog_missing_sample(
    value: object,
    description: str,
    *,
    require_identity: bool,
) -> GuardIdentity | None:
    """Validates one terminal authoritative missing sample."""

    expected_fields = (
        WATCHDOG_EXACT_SAMPLE_FIELDS
        if require_identity
        else WATCHDOG_SAMPLE_FIELDS
    )
    if not isinstance(value, dict) or set(value) != expected_fields:
        raise EvidenceError(f"The {description} field inventory changed")
    identity: GuardIdentity | None = None
    if require_identity:
        identity = _validate_target(value.get("expected_identity"))
    elif value.get("pid") is not None:
        raise EvidenceError(f"The {description} unexpectedly claims a live PID")
    detail = _bounded_text(
        value.get("detail"),
        f"{description} detail",
        MAXIMUM_DETAIL_SIZE,
    )
    if (
        value.get("source") != "proc-pid-rusage-v4"
        or value.get("status") != "missing"
        or value.get("current_phys_footprint_bytes") is not None
        or not detail.startswith("target process is missing:")
    ):
        raise EvidenceError(
            f"The {description} is not an authoritative missing sample"
        )
    return identity


def validate_launch_watchdog(
    paths: Mapping[str, Path],
    artifact_anchor: Path,
    memory_guard: GuardEvidence,
) -> LaunchWatchdogEvidence:
    """Validates persistent wrapper supervision and terminal global absence."""

    if set(paths) != set(RUNTIME_WATCHDOG_FILE_NAMES):
        raise EvidenceError("The launch watchdog artifact mapping changed")
    for file_name, path in paths.items():
        require_no_symlink_components(
            path,
            artifact_anchor,
            f"launch watchdog {file_name}",
        )

    readiness = _read_bounded_newline_json(
        paths[WATCHDOG_READINESS_FILE_NAME],
        "launch watchdog readiness",
        MAXIMUM_READINESS_SIZE,
    )
    if set(readiness) != WATCHDOG_READINESS_FIELDS:
        raise EvidenceError("The launch watchdog readiness fields changed")
    anchor = _validate_target(readiness.get("anchor"))
    watchdog_pid = _positive_integer(
        readiness.get("watchdog_pid"),
        "launch watchdog PID",
        MAXIMUM_PID,
    )
    controller_pid = _positive_integer(
        readiness.get("controller_pid"),
        "launch watchdog controller PID",
        MAXIMUM_PID,
    )
    controller_process_group_id = _positive_integer(
        readiness.get("controller_process_group_id"),
        "launch watchdog controller process-group ID",
        MAXIMUM_PID,
    )
    owned_session_id = _positive_integer(
        readiness.get("owned_session_id"),
        "launch watchdog session ID",
        MAXIMUM_PID,
    )
    ready_at = _nonnegative_integer(
        readiness.get("ready_at_monotonic_ns"),
        "launch watchdog readiness timestamp",
    )
    if (
        readiness.get("schema") != WATCHDOG_READINESS_SCHEMA
        or readiness.get("heartbeat_timeout_nanoseconds")
        != WATCHDOG_HEARTBEAT_TIMEOUT_NANOSECONDS
        or readiness.get("enforcement_active") is not True
        or anchor.pid != anchor.process_group_id
        or anchor.process_group_id != owned_session_id
        or anchor.process_group_id != memory_guard.identity.process_group_id
        or anchor.pid == memory_guard.identity.pid
        or watchdog_pid
        in {
            anchor.pid,
            memory_guard.identity.pid,
            controller_pid,
            controller_process_group_id,
        }
        or controller_pid in {anchor.pid, memory_guard.identity.pid}
        or controller_process_group_id
        in {anchor.pid, memory_guard.identity.pid}
    ):
        raise EvidenceError("The launch watchdog readiness identity changed")

    telemetry = _read_bounded_newline_json(
        paths[WATCHDOG_TELEMETRY_FILE_NAME],
        "launch watchdog terminal telemetry",
        MAXIMUM_TELEMETRY_SIZE,
    )
    if set(telemetry) != WATCHDOG_TELEMETRY_FIELDS:
        raise EvidenceError("The launch watchdog telemetry fields changed")
    observed_at = _nonnegative_integer(
        telemetry.get("observed_at_monotonic_ns"),
        "launch watchdog terminal timestamp",
    )
    if (
        telemetry.get("schema") != WATCHDOG_SCHEMA
        or telemetry.get("status") != "normal"
        or telemetry.get("decision") != "exit"
        or telemetry.get("reason") is not None
        or _validate_target(telemetry.get("anchor")) != anchor
        or telemetry.get("owned_session_id") != owned_session_id
        or type(telemetry.get("controller_pid")) is not int
        or telemetry.get("controller_pid") != controller_pid
        or type(telemetry.get("controller_process_group_id")) is not int
        or telemetry.get("controller_process_group_id")
        != controller_process_group_id
        or observed_at < ready_at
        or observed_at < memory_guard.terminal_observed_at_monotonic_ns
    ):
        raise EvidenceError("The launch watchdog terminal identity changed")

    heartbeat = telemetry.get("controller_heartbeat")
    if not isinstance(heartbeat, dict) or set(heartbeat) != WATCHDOG_HEARTBEAT_FIELDS:
        raise EvidenceError("The launch watchdog heartbeat fields changed")
    heartbeat_received_at = _nonnegative_integer(
        heartbeat.get("last_received_at_monotonic_ns"),
        "launch watchdog heartbeat timestamp",
    )
    heartbeat_age = _nonnegative_integer(
        heartbeat.get("age_nanoseconds"),
        "launch watchdog heartbeat age",
    )
    if (
        heartbeat.get("status") != "healthy"
        or heartbeat_received_at > observed_at
        or heartbeat_age > WATCHDOG_HEARTBEAT_TIMEOUT_NANOSECONDS
        or heartbeat_received_at + heartbeat_age > observed_at
    ):
        raise EvidenceError("The launch watchdog heartbeat was not healthy")

    expected_limits = {
        "maximum_java_process_count": MAXIMUM_WATCHDOG_IDENTITY_COUNT,
        "per_process_current_phys_footprint_bytes": (
            WATCHDOG_PER_PROCESS_CEILING_BYTES
        ),
        "aggregate_current_phys_footprint_bytes": (
            WATCHDOG_AGGREGATE_CEILING_BYTES
        ),
    }
    limits = telemetry.get("limits")
    if (
        not isinstance(limits, dict)
        or set(limits) != WATCHDOG_LIMIT_FIELDS
        or not exact_json_value(limits, expected_limits)
    ):
        raise EvidenceError("The launch watchdog memory limits changed")

    samples = telemetry.get("samples")
    if (
        telemetry.get("java_inventory") != []
        or telemetry.get("external_java_process_ids") != []
        or telemetry.get("tracked_exact_identities") != []
        or not isinstance(samples, list)
        or not 2
        <= len(samples)
        <= MAXIMUM_WATCHDOG_HISTORICAL_IDENTITY_COUNT
        or telemetry.get("aggregate_current_phys_footprint_bytes") != 0
        or telemetry.get("owned_group_absent") is not True
        or telemetry.get("signal_actions") != []
    ):
        raise EvidenceError("The launch watchdog terminal state is not clean")
    for sample in samples:
        _validate_watchdog_missing_sample(
            sample,
            "launch watchdog terminal sample",
            require_identity=False,
        )

    terminal = telemetry.get("terminal_attestation")
    if not isinstance(terminal, dict) or set(terminal) != WATCHDOG_TERMINAL_FIELDS:
        raise EvidenceError("The launch watchdog terminal attestation fields changed")
    exact_samples = terminal.get("exact_identity_samples")
    if (
        terminal.get("owned_group_absent") is not True
        or terminal.get("tracked_identities_absent") is not True
        or terminal.get("global_java_inventory") != []
        or terminal.get("global_java_inventory_error") is not None
        or terminal.get("external_java_remained") is not False
        or terminal.get("exact_identity_sample_error") is not None
        or not isinstance(exact_samples, list)
        or len(exact_samples) != len(samples)
    ):
        raise EvidenceError("The launch watchdog terminal absence proof changed")
    identities = tuple(
        _validate_watchdog_missing_sample(
            sample,
            "launch watchdog exact terminal sample",
            require_identity=True,
        )
        for sample in exact_samples
    )
    if any(identity is None for identity in identities):
        raise EvidenceError("The launch watchdog terminal identity is absent")
    exact_identities = tuple(
        identity for identity in identities if identity is not None
    )
    process_ids = tuple(identity.pid for identity in exact_identities)
    if (
        process_ids != tuple(sorted(process_ids))
        or len(set(process_ids)) != len(process_ids)
        or anchor not in exact_identities
        or memory_guard.identity not in exact_identities
        or any(
            identity.process_group_id != anchor.process_group_id
            for identity in exact_identities
        )
    ):
        raise EvidenceError("The launch watchdog exact identity history changed")
    return LaunchWatchdogEvidence(
        anchor=anchor,
        server=memory_guard.identity,
        controller_pid=controller_pid,
        terminal_identities=exact_identities,
    )


def validate_launch_anchor(
    paths: Mapping[str, Path],
    artifact_anchor: Path,
    watchdog: LaunchWatchdogEvidence,
    expected_live_runtime: Path | None,
) -> LaunchAnchorEvidence:
    """Validates canonical broker artifacts and watchdog identity cross-links."""

    if set(paths) != set(RUNTIME_ANCHOR_FILE_NAMES):
        raise EvidenceError("The launch anchor artifact mapping changed")
    payloads: dict[str, dict[str, object]] = {}
    contents: dict[str, bytes] = {}
    for file_name in RUNTIME_ANCHOR_FILE_NAMES:
        path = paths[file_name]
        require_no_symlink_components(
            path,
            artifact_anchor,
            f"launch anchor {file_name}",
        )
        payload, content = _read_canonical_anchor_json(
            path,
            f"launch anchor artifact {file_name}",
        )
        payloads[file_name] = payload
        contents[file_name] = content

    readiness = payloads[ANCHOR_READINESS_FILE_NAME]
    if set(readiness) != ANCHOR_READINESS_FIELDS:
        raise EvidenceError("The launch anchor readiness fields changed")
    token = validate_hex_digest(
        readiness.get("token"),
        "launch anchor token",
    )
    arguments_sha256 = validate_hex_digest(
        readiness.get("argv_sha256"),
        "launch anchor argument digest",
    )
    anchor_pid = _positive_integer(
        readiness.get("pid"),
        "launch anchor PID",
        MAXIMUM_PID,
    )
    runtime_device = _positive_integer(
        readiness.get("runtime_device"),
        "launch anchor runtime device",
        MAXIMUM_UNSIGNED_64_BIT_INTEGER,
    )
    runtime_inode = _positive_integer(
        readiness.get("runtime_inode"),
        "launch anchor runtime inode",
        MAXIMUM_UNSIGNED_64_BIT_INTEGER,
    )
    controller_pid = _positive_integer(
        readiness.get("controller_pid"),
        "launch anchor controller PID",
        MAXIMUM_PID,
    )
    if (
        readiness.get("schema") != ANCHOR_READINESS_SCHEMA
        or type(readiness.get("java_feature")) is not int
        or readiness.get("java_feature") != ANCHOR_JAVA_FEATURE
        or type(readiness.get("pre_start_timeout_seconds")) is not int
        or readiness.get("pre_start_timeout_seconds") != 30
        or controller_pid != watchdog.controller_pid
        or anchor_pid != watchdog.anchor.pid
        or watchdog.anchor.process_group_id != anchor_pid
    ):
        raise EvidenceError(
            "The launch anchor readiness identity differs from its watchdog"
        )
    if expected_live_runtime is not None:
        try:
            runtime_information = expected_live_runtime.stat()
        except OSError as exception:
            raise EvidenceError(
                f"Cannot inspect the launch anchor runtime identity: {exception}"
            ) from exception
        if (
            runtime_device != runtime_information.st_dev
            or runtime_inode != runtime_information.st_ino
        ):
            raise EvidenceError(
                "The launch anchor readiness differs from its runtime inode"
            )

    start = payloads[ANCHOR_START_FILE_NAME]
    if (
        set(start) != ANCHOR_START_FIELDS
        or start.get("schema") != ANCHOR_START_SCHEMA
        or start.get("token") != token
        or start.get("argv_sha256") != arguments_sha256
    ):
        raise EvidenceError("The launch anchor start token or arguments changed")

    child_started = payloads[ANCHOR_CHILD_STARTED_FILE_NAME]
    if set(child_started) != ANCHOR_CHILD_STARTED_FIELDS:
        raise EvidenceError("The launch anchor child-started fields changed")
    child_pid = _positive_integer(
        child_started.get("pid"),
        "launch anchor child PID",
        MAXIMUM_PID,
    )
    child_executable = _validate_executable(child_started.get("executable"))
    if (
        child_started.get("schema") != ANCHOR_CHILD_STARTED_SCHEMA
        or child_started.get("token") != token
        or child_started.get("argv_sha256") != arguments_sha256
        or child_pid in {watchdog.anchor.pid, watchdog.server.pid}
        or child_executable != watchdog.anchor.expected_executable
    ):
        raise EvidenceError("The launch anchor child identity is not authenticated")
    matching_children = tuple(
        identity
        for identity in watchdog.terminal_identities
        if identity.pid == child_pid
        and identity.process_group_id == watchdog.anchor.process_group_id
        and identity.expected_executable == child_executable
    )
    if len(matching_children) != 1:
        raise EvidenceError(
            "The launch anchor child is absent from watchdog identity history"
        )

    child_result = payloads[ANCHOR_CHILD_RESULT_FILE_NAME]
    if (
        set(child_result) != ANCHOR_CHILD_RESULT_FIELDS
        or child_result.get("schema") != ANCHOR_CHILD_RESULT_SCHEMA
        or child_result.get("token") != token
        or child_result.get("argv_sha256") != arguments_sha256
        or child_result.get("started") is not True
        or type(child_result.get("pid")) is not int
        or child_result.get("pid") != child_pid
        or type(child_result.get("exit_code")) is not int
        or child_result.get("exit_code") != 0
    ):
        raise EvidenceError("The launch anchor child did not exit successfully")

    finish = payloads[ANCHOR_FINISH_FILE_NAME]
    if (
        set(finish) != ANCHOR_FINISH_FIELDS
        or finish.get("schema") != ANCHOR_FINISH_SCHEMA
        or finish.get("token") != token
        or finish.get("argv_sha256") != arguments_sha256
        or finish.get("child_started") is not child_result.get("started")
        or type(finish.get("child_pid")) is not int
        or finish.get("child_pid") != child_result.get("pid")
        or type(finish.get("child_exit_code")) is not int
        or finish.get("child_exit_code") != child_result.get("exit_code")
    ):
        raise EvidenceError("The launch anchor finish is not result-bound")
    return LaunchAnchorEvidence(
        anchor=watchdog.anchor,
        child_pid=child_pid,
        child_executable=child_executable,
        arguments_sha256=arguments_sha256,
        token=token,
        artifact_contents=contents,
    )


def validate_exact_runtime_path(runtime: Path, expected_runtime: Path) -> Path:
    """Requires the unique repository-owned v21 runtime."""

    repository_root = repository_anchor(
        expected_runtime,
        RUNTIME_RELATIVE_PATH,
        "Forge server runtime",
    )
    require_no_symlink_components(runtime, repository_root, "Forge server runtime")
    require_directory(runtime, "Forge server runtime")
    if runtime.name != PROFILE_ID or runtime.absolute() != expected_runtime.absolute():
        raise EvidenceError(
            "The Forge server runtime differs from the unique isolated profile"
        )
    return repository_root


def validate_live_runtime(
    runtime: Path = DEFAULT_RUNTIME_ROOT,
    profile_manifest_path: Path = PROFILE_MANIFEST_PATH,
    expected_runtime: Path = DEFAULT_RUNTIME_ROOT,
) -> EvidenceSummary:
    """Validates one stopped, repository-owned native v21 server run."""

    repository_root = validate_exact_runtime_path(runtime, expected_runtime)
    profile_repository_root = repository_anchor(
        profile_manifest_path,
        PROFILE_MANIFEST_RELATIVE_PATH,
        "Forge server profile manifest",
    )
    if profile_repository_root.absolute() != repository_root.absolute():
        raise EvidenceError("The Forge server runtime and profile use different repositories")
    profile_record = load_profile_manifest(profile_manifest_path)
    validate_profile_marker(runtime, profile_record)
    expected_runtime_entries = {
        PROFILE_MARKER_NAME,
        "game",
        "evidence",
        *RUNTIME_SUPERVISION_FILE_NAMES,
    }
    actual_runtime_entries = {entry.name for entry in runtime.iterdir()}
    if actual_runtime_entries != expected_runtime_entries:
        raise EvidenceError(
            "The Forge server runtime inventory changed: "
            f"missing={sorted(expected_runtime_entries - actual_runtime_entries)}, "
            f"unexpected={sorted(actual_runtime_entries - expected_runtime_entries)}"
        )
    validate_staged_launch_inputs(
        runtime_staged_launch_input_paths(runtime),
        require_private_permissions=True,
    )
    memory_guard = validate_memory_guard(
        runtime_guard_paths(runtime),
        runtime,
        runtime,
    )
    watchdog_evidence = validate_launch_watchdog(
        runtime_watchdog_paths(runtime),
        runtime,
        memory_guard,
    )
    anchor_evidence = validate_launch_anchor(
        runtime_anchor_paths(runtime),
        runtime,
        watchdog_evidence,
        expected_live_runtime=runtime,
    )

    game = require_directory(runtime / "game", "Forge server game directory")
    if (game / "evidence").exists() or (game / "evidence").is_symlink():
        raise EvidenceError("The Forge server evidence is inside the game directory")
    eula = require_regular_file(game / "eula.txt", "Forge server EULA")
    if eula.read_bytes() != b"eula=true\n":
        raise EvidenceError("The Forge server EULA differs from its profile")
    properties = require_regular_file(
        game / "server.properties",
        "Forge server properties",
    )
    if properties.stat().st_size <= 0:
        raise EvidenceError("The Forge server properties file is empty")
    for directory_name in ("config", "crash-reports", "logs", "mods", "world"):
        require_directory(game / directory_name, f"Forge server game {directory_name}")
    if any((game / "crash-reports").iterdir()):
        raise EvidenceError("The Forge server runtime contains a crash report")
    if any((game / "mods").iterdir()):
        raise EvidenceError("The Loom userdev server must not stage mod JARs")
    level_dat = require_regular_file(
        game / "world" / "level.dat",
        "Forge server level.dat",
    )
    if level_dat.stat().st_size <= 0:
        raise EvidenceError("The Forge server level.dat is empty")

    evidence_root = require_directory(
        runtime / "evidence",
        "Forge server evidence directory",
    )
    if {entry.name for entry in evidence_root.iterdir()} != {SCENARIO_ID}:
        raise EvidenceError("The Forge server evidence scenario inventory changed")
    scenario_root = evidence_root / SCENARIO_ID
    require_no_symlink_components(scenario_root, runtime, "Forge server evidence")
    validate_scenario_inventory(scenario_root)
    payload = validate_payload(
        scenario_root,
        profile_record,
        True,
        runtime_watchdog_paths(runtime),
        runtime_anchor_paths(runtime),
        anchor_evidence,
    )
    game_log = require_regular_file(
        game / "logs" / "latest.log",
        "Forge server source log",
    )
    copied_log = scenario_root / "logs" / "latest.log"
    if (
        game_log.stat().st_size != copied_log.stat().st_size
        or sha256_file(game_log) != payload.log_sha256
    ):
        raise EvidenceError("The Forge server evidence log is not an exact game-log copy")
    return EvidenceSummary(PROFILE_ID, payload.assertion_count, payload.log_sha256)


def validate_archive_root_identity(archive_root: Path) -> None:
    """Requires the immutable Slitherite block-registry server-v21 identity."""

    if archive_root.name != ARCHIVE_DIRECTORY_NAME:
        raise EvidenceError(
            "The Forge server archive does not identify Slitherite profile v21"
        )


def validate_no_competing_slitherite_archives(repository_root: Path) -> None:
    """Rejects older or newer Slitherite server evidence beside v21."""

    archive_parent = require_directory(
        repository_root / ARCHIVE_RELATIVE_PATH.parent,
        "Forge server archive parent",
    )
    competing = sorted(
        entry.name
        for entry in archive_parent.iterdir()
        if entry.name != ARCHIVE_DIRECTORY_NAME
        and SLITHERITE_ARCHIVE_DIRECTORY_PATTERN.fullmatch(entry.name)
        and (entry.is_dir() or entry.is_symlink())
    )
    if competing:
        raise EvidenceError(
            "The Forge server archive has competing Slitherite evidence: "
            f"{competing}"
        )


def validate_archive_destination(
    archive_root: Path,
    expected_archive_root: Path,
) -> Path:
    """Requires the exact non-linked repository archive destination."""

    repository_root = repository_anchor(
        expected_archive_root,
        ARCHIVE_RELATIVE_PATH,
        "Forge server archive",
    )
    require_no_symlink_components(archive_root, repository_root, "Forge server archive")
    if archive_root.absolute() != expected_archive_root.absolute():
        raise EvidenceError("The Forge server archive is outside its exact destination")
    validate_archive_root_identity(archive_root)
    validate_no_competing_slitherite_archives(repository_root)
    return repository_root


def archive_file_records(archive_root: Path) -> dict[str, dict[str, object]]:
    """Builds exact size and SHA-256 records for all eighteen payloads."""

    return {
        relative_path: {
            "size": (archive_root / relative_path).stat().st_size,
            "sha256": sha256_file(archive_root / relative_path),
        }
        for relative_path in ARCHIVE_PAYLOAD_PATHS
    }


def _validate_capture_copy(capture_runtime: Path, archive_root: Path) -> None:
    for relative_path in SCENARIO_PAYLOAD_PATHS:
        source = capture_runtime / "evidence" / SCENARIO_ID / relative_path
        archived = archive_root / relative_path
        if source.read_bytes() != archived.read_bytes():
            raise EvidenceError(f"The archived scenario payload differs: {relative_path}")
    source_guard = runtime_guard_paths(capture_runtime)
    archived_guard = archived_guard_paths(archive_root)
    for file_name in RUNTIME_GUARD_FILE_NAMES:
        if source_guard[file_name].read_bytes() != archived_guard[file_name].read_bytes():
            raise EvidenceError(f"The archived memory guard payload differs: {file_name}")
    source_watchdog = runtime_watchdog_paths(capture_runtime)
    archived_watchdog = archived_watchdog_paths(archive_root)
    for file_name in RUNTIME_WATCHDOG_FILE_NAMES:
        if (
            source_watchdog[file_name].read_bytes()
            != archived_watchdog[file_name].read_bytes()
        ):
            raise EvidenceError(
                f"The archived launch watchdog payload differs: {file_name}"
            )
    source_anchor = runtime_anchor_paths(capture_runtime)
    archived_anchor = archived_anchor_paths(archive_root)
    for file_name in RUNTIME_ANCHOR_FILE_NAMES:
        if (
            source_anchor[file_name].read_bytes()
            != archived_anchor[file_name].read_bytes()
        ):
            raise EvidenceError(
                f"The archived launch anchor payload differs: {file_name}"
            )
    source_staged = runtime_staged_launch_input_paths(capture_runtime)
    archived_staged = archived_staged_launch_input_paths(archive_root)
    for file_name in RUNTIME_STAGED_LAUNCH_INPUT_FILE_NAMES:
        if (
            source_staged[file_name].read_bytes()
            != archived_staged[file_name].read_bytes()
        ):
            raise EvidenceError(
                f"The archived staged launch input differs: {file_name}"
            )


def build_archive_manifest(
    profile_manifest_path: Path,
    archive_root: Path,
    capture_runtime: Path,
    expected_archive_root: Path = DEFAULT_ARCHIVE_ROOT,
    expected_runtime: Path = DEFAULT_RUNTIME_ROOT,
) -> dict[str, object]:
    """Builds a one-time manifest from one verified stopped runtime."""

    repository_root = validate_archive_destination(archive_root, expected_archive_root)
    validate_live_runtime(capture_runtime, profile_manifest_path, expected_runtime)
    profile_repository_root = repository_anchor(
        profile_manifest_path,
        PROFILE_MANIFEST_RELATIVE_PATH,
        "Forge server profile manifest",
    )
    if profile_repository_root.absolute() != repository_root.absolute():
        raise EvidenceError("The Forge server archive and profile use different repositories")
    profile_record = load_profile_manifest(profile_manifest_path)
    validate_archive_inventory(archive_root, include_manifest=False)
    validate_staged_launch_inputs(
        archived_staged_launch_input_paths(archive_root),
        require_private_permissions=False,
    )
    memory_guard = validate_memory_guard(
        archived_guard_paths(archive_root),
        archive_root,
        expected_live_runtime=None,
    )
    watchdog_evidence = validate_launch_watchdog(
        archived_watchdog_paths(archive_root),
        archive_root,
        memory_guard,
    )
    anchor_evidence = validate_launch_anchor(
        archived_anchor_paths(archive_root),
        archive_root,
        watchdog_evidence,
        expected_live_runtime=None,
    )
    payload = validate_payload(
        archive_root,
        profile_record,
        False,
        archived_watchdog_paths(archive_root),
        archived_anchor_paths(archive_root),
        anchor_evidence,
    )
    _validate_capture_copy(capture_runtime, archive_root)
    return {
        "schema": 1,
        "kind": ARCHIVE_KIND,
        "verification_scope": ARCHIVE_VERIFICATION_SCOPE,
        "scenario": SCENARIO_ID,
        "profile": {
            "id": PROFILE_ID,
            "manifest_path": profile_record.relative_path,
            "manifest_size": profile_record.size,
            "manifest_sha256": profile_record.sha256,
        },
        "runtime": {
            "artifact_node": "forge-1.20.1",
            "minecraft": "1.20.1",
            "loader": "forge",
            "loader_version": "47.4.9",
            "java": 17,
            "distribution": "DEDICATED_SERVER",
            "task_path": TASK_PATH,
            "execution": EXECUTION_KIND,
        },
        "assertion_count": payload.assertion_count,
        "publication": PUBLICATION_ATTESTATION,
        "memory_guard": MEMORY_GUARD_ATTESTATION,
        "launch_watchdog": LAUNCH_WATCHDOG_ATTESTATION,
        "launch_anchor": LAUNCH_ANCHOR_ATTESTATION,
        "files": archive_file_records(archive_root),
    }


def write_archive_manifest(
    profile_manifest_path: Path,
    archive_root: Path,
    capture_runtime: Path,
    expected_archive_root: Path = DEFAULT_ARCHIVE_ROOT,
    expected_runtime: Path = DEFAULT_RUNTIME_ROOT,
) -> Path:
    """Creates the exact repository archive manifest once."""

    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if manifest_path.exists() or manifest_path.is_symlink():
        raise EvidenceError(f"The Forge server archive manifest already exists: {manifest_path}")
    manifest = build_archive_manifest(
        profile_manifest_path,
        archive_root,
        capture_runtime,
        expected_archive_root,
        expected_runtime,
    )
    try:
        with manifest_path.open("x", encoding="utf-8", newline="\n") as handle:
            json.dump(manifest, handle, indent=2)
            handle.write("\n")
    except FileExistsError as exception:
        raise EvidenceError(
            f"The Forge server archive manifest already exists: {manifest_path}"
        ) from exception
    return manifest_path


def validate_archive_manifest_shape(
    archive_root: Path,
    manifest: dict[str, object],
) -> ValidatedArchiveManifest:
    """Validates archive identity, provenance, and all payload records."""

    if set(manifest) != {
        "schema",
        "kind",
        "verification_scope",
        "scenario",
        "profile",
        "runtime",
        "assertion_count",
        "publication",
        "memory_guard",
        "launch_watchdog",
        "launch_anchor",
        "files",
    }:
        raise EvidenceError("The Forge server archive manifest fields changed")
    if (
        type(manifest.get("schema")) is not int
        or manifest.get("schema") != 1
        or manifest.get("kind") != ARCHIVE_KIND
        or manifest.get("verification_scope") != ARCHIVE_VERIFICATION_SCOPE
        or manifest.get("scenario") != SCENARIO_ID
        or type(manifest.get("assertion_count")) is not int
        or manifest.get("assertion_count") != len(EXPECTED_ASSERTIONS)
        or not exact_json_value(manifest.get("publication"), PUBLICATION_ATTESTATION)
        or not exact_json_value(manifest.get("memory_guard"), MEMORY_GUARD_ATTESTATION)
        or not exact_json_value(
            manifest.get("launch_watchdog"),
            LAUNCH_WATCHDOG_ATTESTATION,
        )
        or not exact_json_value(
            manifest.get("launch_anchor"),
            LAUNCH_ANCHOR_ATTESTATION,
        )
    ):
        raise EvidenceError("The Forge server archive manifest identity is invalid")
    validate_archive_root_identity(archive_root)
    profile = manifest.get("profile")
    if not isinstance(profile, dict) or set(profile) != {
        "id",
        "manifest_path",
        "manifest_size",
        "manifest_sha256",
    }:
        raise EvidenceError("The Forge server archive profile provenance is malformed")
    if (
        profile.get("id") != PROFILE_ID
        or profile.get("manifest_path") != PROFILE_MANIFEST_RELATIVE_PATH.as_posix()
        or type(profile.get("manifest_size")) is not int
        or profile.get("manifest_size") != contract_v21.PROFILE_MANIFEST_SIZE
    ):
        raise EvidenceError("The Forge server archive profile identity is invalid")
    profile_record = ProfileRecord(
        relative_path=str(profile["manifest_path"]),
        size=int(profile["manifest_size"]),
        sha256=validate_hex_digest(
            profile.get("manifest_sha256"),
            "archived Forge server profile",
        ),
    )
    if profile_record.sha256 != contract_v21.PROFILE_MANIFEST_SHA256:
        raise EvidenceError("The archived profile differs from the immutable v21 contract")
    runtime = manifest.get("runtime")
    expected_runtime = {
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "distribution": "DEDICATED_SERVER",
        "task_path": TASK_PATH,
        "execution": EXECUTION_KIND,
    }
    if not exact_json_value(runtime, expected_runtime):
        raise EvidenceError("The Forge server archive runtime identity is invalid")
    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(ARCHIVE_PAYLOAD_PATHS):
        raise EvidenceError("The Forge server archive payload inventory changed")
    validated_files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if not isinstance(record, dict) or set(record) != {"size", "sha256"}:
            raise EvidenceError(f"Malformed archive record: {relative_path}")
        _positive_integer(
            record.get("size"),
            f"archive size for {relative_path}",
            MAXIMUM_EVIDENCE_SIZE,
        )
        validate_hex_digest(record.get("sha256"), f"archive payload {relative_path}")
        validated_files[relative_path] = record
    return ValidatedArchiveManifest(profile_record, validated_files)


def validate_archived_evidence(
    archive_root: Path,
    expected_archive_root: Path = DEFAULT_ARCHIVE_ROOT,
) -> EvidenceSummary:
    """Validates a frozen v21 server archive without live state or current time."""

    validate_archive_destination(archive_root, expected_archive_root)
    validate_archive_inventory(archive_root, include_manifest=True)
    manifest = require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME,
        "Forge server archive manifest",
    )
    validated_manifest = validate_archive_manifest_shape(archive_root, manifest)
    for relative_path, record in validated_manifest.files.items():
        path = archive_root / relative_path
        if path.stat().st_size != record["size"] or sha256_file(path) != record["sha256"]:
            raise EvidenceError(f"Forge server archive payload differs: {relative_path}")
    validate_staged_launch_inputs(
        archived_staged_launch_input_paths(archive_root),
        require_private_permissions=False,
    )
    memory_guard = validate_memory_guard(
        archived_guard_paths(archive_root),
        archive_root,
        expected_live_runtime=None,
    )
    watchdog_evidence = validate_launch_watchdog(
        archived_watchdog_paths(archive_root),
        archive_root,
        memory_guard,
    )
    anchor_evidence = validate_launch_anchor(
        archived_anchor_paths(archive_root),
        archive_root,
        watchdog_evidence,
        expected_live_runtime=None,
    )
    payload = validate_payload(
        archive_root,
        validated_manifest.profile_record,
        False,
        archived_watchdog_paths(archive_root),
        archived_anchor_paths(archive_root),
        anchor_evidence,
    )
    if payload.assertion_count != manifest["assertion_count"]:
        raise EvidenceError("The archive assertion count differs from its manifest")
    return EvidenceSummary(PROFILE_ID, payload.assertion_count, payload.log_sha256)


def parse_arguments() -> argparse.Namespace:
    """Parses one mutually exclusive live, archive, or sealing operation."""

    parser = argparse.ArgumentParser(
        description="Validate Forge 1.20.1 dedicated-server Slitherite v21 evidence."
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--runtime", type=Path)
    mode.add_argument("--archive", type=Path)
    mode.add_argument("--create-archive-manifest", type=Path)
    parser.add_argument("--capture-runtime", type=Path)
    arguments = parser.parse_args()
    creating = arguments.create_archive_manifest is not None
    if creating != (arguments.capture_runtime is not None):
        parser.error(
            "--create-archive-manifest and --capture-runtime must be supplied together"
        )
    return arguments


def main() -> int:
    """Runs the selected fail-closed evidence operation."""

    arguments = parse_arguments()
    try:
        if arguments.create_archive_manifest is not None:
            manifest_path = write_archive_manifest(
                PROFILE_MANIFEST_PATH,
                arguments.create_archive_manifest,
                arguments.capture_runtime,
            )
            print(f"Created Forge server archive manifest: {manifest_path}")
            print(
                "Archive integrity only: current sources and rebuilt artifacts "
                "were not compared."
            )
            return 0
        if arguments.archive is not None:
            summary = validate_archived_evidence(arguments.archive)
            mode = "archived"
        else:
            summary = validate_live_runtime(arguments.runtime)
            mode = "live"
    except (EvidenceError, OSError) as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2
    print(
        f"Validated {mode} {SCENARIO_ID} for {summary.profile_id}: "
        f"{summary.assertion_count} assertions"
    )
    print(f"Server log SHA-256: {summary.log_sha256}")
    if mode == "archived":
        print(
            "Archive integrity only: current sources and rebuilt artifacts "
            "were not compared."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
