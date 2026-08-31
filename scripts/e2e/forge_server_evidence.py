#!/usr/bin/env python3
"""Validate live or archived Forge dedicated-server registry-foundation evidence."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import sys


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIRECTORY.parent.parent
STATE_ROOT = SCRIPT_DIRECTORY / ".state"
RUNTIMES_ROOT = STATE_ROOT / "runtimes"
PROFILE_MANIFEST_RELATIVE_PATH = Path(
    "scripts/e2e/forge-server-1.20.1-profile.json"
)
PROFILE_MANIFEST_PATH = REPOSITORY_ROOT / PROFILE_MANIFEST_RELATIVE_PATH
PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v4"
SCENARIO_ID = "registry-foundation"
TASK_PATH = ":forge:1.20.1:runRegistryFoundationServerProbe"
PROFILE_MARKER_NAME = ".etherology-forge-server-e2e-profile.json"
PROFILE_MANAGER = "scripts/e2e/forge_server.py"
RUNTIME_RELATIVE_PATH = (
    Path("scripts/e2e/.state/runtimes") / PROFILE_ID
)
ARCHIVE_RELATIVE_PATH = Path(
    "docs/evidence/forge-1.20.1/registry-foundation-server-v4"
)
DEFAULT_RUNTIME_ROOT = RUNTIMES_ROOT / PROFILE_ID
DEFAULT_ARCHIVE_ROOT = REPOSITORY_ROOT / ARCHIVE_RELATIVE_PATH
ARCHIVE_DIRECTORY_NAME = "registry-foundation-server-v4"
ARCHIVE_MANIFEST_NAME = "archive-manifest.json"
ARCHIVE_KIND = "etherology-forge-dedicated-server-e2e-evidence"
ARCHIVE_VERIFICATION_SCOPE = (
    "archive-integrity-only-current-sources-and-built-artifacts-not-compared"
)
REPORT_RUNTIME_KIND = "loom-userdev"
EXECUTION_KIND = "loom-userdev-dedicated-server"
MAXIMUM_EVIDENCE_SIZE = 64 * 1024 * 1024
MAXIMUM_LOG_SIZE = 48 * 1024 * 1024
EVIDENCE_DIRECTORIES = {"logs", "reports"}
EVIDENCE_PAYLOAD_PATHS = (
    "reports/report.json",
    "reports/launcher-result.json",
    "reports/done.marker",
    "logs/latest.log",
)
REPORT_FIELDS = {
    "schema",
    "profile_id",
    "scenario",
    "status",
    "minecraft",
    "loader",
    "loader_version",
    "java",
    "distribution",
    "runtime_kind",
    "loaded_mod_ids",
    "forbidden_mod_ids_loaded",
    "mods",
    "registry",
    "loot_condition",
    "tags",
    "lifecycle",
    "assertions",
}
REQUIRED_MOD_IDS = (
    "etherology",
    "etherology_e2e_server_probe",
)
FORBIDDEN_MOD_IDS = (
    "etherology_e2e_harness",
    "quickskin",
    "cpm",
    "ears",
    "modmenu",
    "roughlyenoughitems",
    "emi",
)
EXPECTED_MODS = {
    "etherology": {"loaded": True},
    "etherology_e2e_server_probe": {"loaded": True},
    "etherology_e2e_harness": {"loaded": False},
    "quickskin": {"loaded": False},
    "cpm": {"loaded": False},
    "ears": {"loaded": False},
    "modmenu": {"loaded": False},
    "roughlyenoughitems": {"loaded": False},
    "emi": {"loaded": False},
}
EXPECTED_REGISTRY = {
    "registry_id": "minecraft:game_event",
    "event_id": "etherology:etherology_resonance",
    "internal_id": "etherology_resonance",
    "range": 16,
    "etherology_event_ids": ["etherology:etherology_resonance"],
    "same_instance_at_server_started": True,
}
EXPECTED_LOOT_CONDITION = {
    "registry_id": "minecraft:loot_condition_type",
    "condition_id": "etherology:random_chance_with_fortune",
    "etherology_condition_ids": ["etherology:random_chance_with_fortune"],
    "serializer_class": (
        "ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer"
    ),
    "probe_table_id": "etherology_e2e_server_probe:registry_foundation",
    "empty_tool_items": ["minecraft:gold_ingot", "minecraft:stone"],
    "fortune_one_items": [
        "minecraft:diamond",
        "minecraft:gold_ingot",
        "minecraft:stone",
    ],
    "same_state_at_server_started": True,
}
EXPECTED_TAGS = {
    "update_cause": "SERVER_DATA_LOAD",
    "should_update_static_data": True,
    "update_count": 1,
    "vibrations": {
        "id": "minecraft:vibrations",
        "contains_event": True,
        "etherology_event_ids": ["etherology:etherology_resonance"],
    },
    "warden_can_listen": {
        "id": "minecraft:warden_can_listen",
        "contains_event": True,
        "etherology_event_ids": ["etherology:etherology_resonance"],
    },
    "etherology_tag_ids": [
        "minecraft:vibrations",
        "minecraft:warden_can_listen",
    ],
    "same_membership_at_server_started": True,
}
EXPECTED_LIFECYCLE = [
    "tags_updated",
    "server_started",
    "server_stopping",
    "server_stopped",
]
EXPECTED_ASSERTIONS = (
    ("distribution_dedicated_server", "DEDICATED_SERVER"),
    ("runtime_kind_loom_userdev", "loom-userdev"),
    ("mod_loaded:etherology", "loaded"),
    ("mod_loaded:etherology_e2e_server_probe", "loaded"),
    ("mod_absent:etherology_e2e_harness", "absent"),
    ("mod_absent:quickskin", "absent"),
    ("mod_absent:cpm", "absent"),
    ("mod_absent:ears", "absent"),
    ("mod_absent:modmenu", "absent"),
    ("mod_absent:roughlyenoughitems", "absent"),
    ("mod_absent:emi", "absent"),
    ("mods_forbidden_intersection_empty", "none"),
    (
        "registry:game_event:etherology:etherology_resonance",
        "present",
    ),
    (
        "registry:game_event_etherology_ids_exact",
        "etherology:etherology_resonance",
    ),
    ("registry_internal_id", "etherology_resonance"),
    ("registry_range", "16"),
    (
        "registry:loot_condition:etherology:random_chance_with_fortune",
        "present",
    ),
    (
        "registry:loot_condition_etherology_ids_exact",
        "etherology:random_chance_with_fortune",
    ),
    (
        "registry:loot_condition_serializer_class",
        "ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer",
    ),
    (
        "loot_table:probe_table_loaded",
        "etherology_e2e_server_probe:registry_foundation",
    ),
    (
        "loot_table:empty_tool_items_exact",
        "minecraft:gold_ingot,minecraft:stone",
    ),
    (
        "loot_table:fortune_one_items_exact",
        "minecraft:diamond,minecraft:gold_ingot,minecraft:stone",
    ),
    ("loot_condition_captured_after_server_data_load", "true"),
    ("tags_update_cause", "SERVER_DATA_LOAD"),
    ("tags_static_data", "true"),
    ("tags_update_count", "1"),
    ("tag:vibrations_contains_resonance", "true"),
    (
        "tag:vibrations_etherology_entries_exact",
        "etherology:etherology_resonance",
    ),
    ("tag:warden_can_listen_contains_resonance", "true"),
    (
        "tag:warden_can_listen_etherology_entries_exact",
        "etherology:etherology_resonance",
    ),
    (
        "tags:etherology_tag_ids_exact",
        "minecraft:vibrations,minecraft:warden_can_listen",
    ),
    ("tags_before_server_started", "true"),
    ("server_started_mods_rechecked", "true"),
    ("server_started_registry_rechecked", "true"),
    ("server_started_tags_rechecked", "true"),
    ("server_started_loot_condition_rechecked", "true"),
    ("server_stop_requested_without_restart", "stop(false)"),
    ("server_lifecycle_identity", "true"),
    (
        "lifecycle",
        "tags_updated>server_started>server_stopping>server_stopped",
    ),
)
PROBE_LOG_PHASES = (
    "tags_updated",
    "registry_foundation_checked",
    "server_started",
    "server_stopping",
    "server_stopped",
    "report_published",
    "loom_userdev_exit_scheduled",
)
TERMINATION_LOG_TOKEN = (
    "[EtherologyServerProbe] loom_userdev_exit_scheduled "
    "status=0 server_thread_join_timeout_ms=30000"
)
NORMAL_SERVER_LOG_MARKERS = (
    "Done (",
    "Stopping server",
    "Saving worlds",
    "All dimensions are saved",
)
FATAL_LOG_MARKERS = (
    "[FATAL]",
    "/ERROR]",
    "/FATAL]",
    "Mixin apply failed",
    "MixinTransformerError",
    "InvalidMixinException",
    "NoClassDefFoundError",
    "NoSuchMethodError",
    "NoSuchFieldError",
    "ExceptionInInitializerError",
    "Could not execute entrypoint stage",
    "Could not find required mod",
    "Missing or unsupported mandatory dependencies",
    "ModLoadingException",
    "Uncaught exception in thread",
    "A mod crashed on startup!",
    "Encountered an unexpected exception",
    "Exception in server tick loop",
    "Failed to start the minecraft server",
    "Crash report saved to",
    "java.lang.OutOfMemoryError",
    "dev.theplumteam.etherology.e2e.forge.ForgeE2eHarness",
    "The server-probe report could not be published",
)
CLIENT_LOG_MARKERS = (
    "Env=CLIENT",
    "[Render thread/",
    "net/minecraft/client/",
    "Attempted to load class net/minecraft/client",
    "MinecraftClient",
    "ClientModLoader",
    "LWJGL version",
    "OpenAL initialized",
    "Sound engine started",
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
EXPECTED_FORBIDDEN_MOD_IDS = list(FORBIDDEN_MOD_IDS)
PUBLICATION_ATTESTATION = {
    "report": "reports/report.json",
    "launcher_result": "reports/launcher-result.json",
    "completion_marker": "reports/done.marker",
    "server_log": "logs/latest.log",
    "verified_launcher_after_report_and_log_before_sealing": True,
    "verified_completion_marker_last_before_sealing": True,
}


class EvidenceError(RuntimeError):
    """Reports a fail-closed dedicated-server evidence contract violation."""


@dataclass(frozen=True)
class ProfileRecord:
    """Identifies the exact tracked server profile used by a live launch."""

    relative_path: str
    size: int
    sha256: str


@dataclass(frozen=True)
class ValidatedPayload:
    """Carries semantic results from one exact scenario payload inventory."""

    profile_record: ProfileRecord
    assertion_count: int
    log_sha256: str


@dataclass(frozen=True)
class EvidenceSummary:
    """Summarizes one verified native dedicated-server run."""

    profile_id: str
    assertion_count: int
    log_sha256: str


@dataclass(frozen=True)
class ArchiveEvidenceSummary:
    """Summarizes evidence verified solely from a frozen archive."""

    profile_id: str
    assertion_count: int
    log_sha256: str


@dataclass(frozen=True)
class ValidatedArchiveManifest:
    """Carries exact archive provenance into payload validation."""

    profile_record: ProfileRecord
    files: dict[str, dict[str, object]]


def sha256_file(path: Path) -> str:
    """Returns the SHA-256 digest of one regular evidence file."""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_hex_digest(value: object, description: str) -> str:
    """Requires one lowercase SHA-256 value."""
    if not isinstance(value, str) or re.fullmatch(r"[0-9a-f]{64}", value) is None:
        raise EvidenceError(f"The {description} SHA-256 digest is invalid")
    return value


def exact_json_value(actual: object, expected: object) -> bool:
    """Compares JSON values without treating booleans as integers."""
    if type(actual) is not type(expected):
        return False
    if isinstance(expected, dict):
        return set(actual) == set(expected) and all(
            exact_json_value(actual[key], expected[key]) for key in expected
        )
    if isinstance(expected, list):
        return len(actual) == len(expected) and all(
            exact_json_value(actual_value, expected_value)
            for actual_value, expected_value in zip(actual, expected, strict=True)
        )
    return actual == expected


def require_regular_file(path: Path, description: str) -> Path:
    """Requires a non-linked regular file."""
    if not path.is_file() or path.is_symlink():
        raise EvidenceError(f"{description} is missing or linked: {path}")
    return path


def require_directory(path: Path, description: str) -> Path:
    """Requires a non-linked directory."""
    if not path.is_dir() or path.is_symlink():
        raise EvidenceError(f"{description} is missing or linked: {path}")
    return path


def repository_anchor(path: Path, relative_path: Path, description: str) -> Path:
    """Finds the lexical repository root for one exact relative path."""
    absolute_path = path.absolute()
    anchor = absolute_path
    for _ in relative_path.parts:
        anchor = anchor.parent
    if anchor / relative_path != absolute_path:
        raise EvidenceError(
            f"The {description} does not use its exact repository path: {path}"
        )
    return anchor


def require_no_symlink_components(
    path: Path,
    anchor: Path,
    description: str,
) -> None:
    """Rejects a path whose repository-owned chain contains a symlink."""
    absolute_anchor = anchor.absolute()
    absolute_path = path.absolute()
    try:
        relative_path = absolute_path.relative_to(absolute_anchor)
    except ValueError as exception:
        raise EvidenceError(
            f"The {description} escapes its repository-owned root: {path}"
        ) from exception
    current_path = absolute_anchor
    if current_path.is_symlink():
        raise EvidenceError(
            f"The {description} resolves through a symlink: {current_path}"
        )
    for part in relative_path.parts:
        current_path /= part
        if current_path.is_symlink():
            raise EvidenceError(
                f"The {description} resolves through a symlink: {current_path}"
            )


def require_json_object(path: Path, description: str) -> dict[str, object]:
    """Reads a regular UTF-8 JSON file whose root is an object."""
    require_regular_file(path, description)
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise EvidenceError(f"Cannot read {description} {path}: {exception}") from exception
    if not isinstance(value, dict):
        raise EvidenceError(f"The {description} is not a JSON object: {path}")
    return value


def validate_profile_shape(profile: dict[str, object]) -> None:
    """Validates every field in the dedicated-server profile manifest."""
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
    if not isinstance(release, dict) or set(release) != {
        "matrix",
        "artifact_node",
        "minecraft",
        "loader",
        "loader_version",
        "java",
    }:
        raise EvidenceError("The Forge server release contract is invalid")
    if type(release.get("java")) is not int or release != {
        "matrix": "release/release-matrix.json",
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
    }:
        raise EvidenceError("The Forge server release identity is invalid")

    launch = profile.get("launch")
    if not isinstance(launch, dict) or set(launch) != {
        "kind",
        "task_path",
        "scenario",
        "maximum_memory_mb",
    }:
        raise EvidenceError("The Forge server launch contract is invalid")
    if type(launch.get("maximum_memory_mb")) is not int or launch != {
        "kind": REPORT_RUNTIME_KIND,
        "task_path": TASK_PATH,
        "scenario": SCENARIO_ID,
        "maximum_memory_mb": 2048,
    }:
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
    if profile.get("forbidden_mod_ids") != EXPECTED_FORBIDDEN_MOD_IDS:
        raise EvidenceError("The Forge server forbidden mod inventory is invalid")


def load_profile_manifest(path: Path = PROFILE_MANIFEST_PATH) -> ProfileRecord:
    """Loads and fingerprints the exact tracked dedicated-server profile."""
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
    return ProfileRecord(
        relative_path=PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
        size=path.stat().st_size,
        sha256=sha256_file(path),
    )


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


def validate_assertions(assertions: object) -> None:
    """Validates the exact ordered probe assertion contract."""
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTIONS
    ):
        raise EvidenceError("The Forge server assertion inventory changed")
    for index, ((expected_name, expected_value), assertion) in enumerate(
        zip(EXPECTED_ASSERTIONS, assertions, strict=True)
    ):
        if not isinstance(assertion, dict) or set(assertion) != {
            "name",
            "passed",
            "expected",
            "actual",
        }:
            raise EvidenceError(
                f"Forge server assertion {index} has invalid fields"
            )
        if (
            assertion.get("name") != expected_name
            or assertion.get("passed") is not True
            or assertion.get("expected") != expected_value
            or assertion.get("actual") != expected_value
        ):
            raise EvidenceError(
                f"Forge server assertion differs from its contract: {expected_name}"
            )


def validate_report(report: dict[str, object]) -> None:
    """Validates exact registry, tag, mod, lifecycle, and assertion evidence."""
    if set(report) != REPORT_FIELDS:
        raise EvidenceError("The Forge server report field inventory changed")
    if type(report.get("schema")) is not int or report.get("schema") != 2:
        raise EvidenceError("The Forge server report schema is invalid")
    if (
        report.get("profile_id") != PROFILE_ID
        or report.get("scenario") != SCENARIO_ID
        or report.get("status") != "passed"
        or report.get("minecraft") != "1.20.1"
        or report.get("loader") != "forge"
        or report.get("loader_version") != "47.4.9"
        or type(report.get("java")) is not int
        or report.get("java") != 17
        or report.get("distribution") != "DEDICATED_SERVER"
        or report.get("runtime_kind") != REPORT_RUNTIME_KIND
    ):
        raise EvidenceError("The Forge server report runtime identity is invalid")

    loaded_mod_ids = report.get("loaded_mod_ids")
    if (
        not isinstance(loaded_mod_ids, list)
        or not loaded_mod_ids
        or not all(
            type(mod_id) is str
            and re.fullmatch(r"[a-z][a-z0-9_-]{1,63}", mod_id) is not None
            for mod_id in loaded_mod_ids
        )
        or loaded_mod_ids != sorted(set(loaded_mod_ids))
    ):
        raise EvidenceError(
            "The Forge server loaded mod ID inventory is not sorted and unique"
        )
    loaded_mod_id_set = set(loaded_mod_ids)
    if not set(REQUIRED_MOD_IDS).issubset(loaded_mod_id_set):
        raise EvidenceError(
            "The Forge server loaded mod ID inventory lacks a required mod"
        )
    loaded_forbidden_ids = sorted(
        loaded_mod_id_set.intersection(FORBIDDEN_MOD_IDS)
    )
    if loaded_forbidden_ids:
        raise EvidenceError(
            "The Forge server loaded mod ID inventory contains a forbidden mod"
        )
    if not exact_json_value(report.get("forbidden_mod_ids_loaded"), []):
        raise EvidenceError(
            "The Forge server reported a non-empty forbidden mod intersection"
        )
    if not exact_json_value(report.get("mods"), EXPECTED_MODS):
        raise EvidenceError("The Forge server report mod inventory is invalid")

    registry = report.get("registry")
    if not isinstance(registry, dict) or set(registry) != set(EXPECTED_REGISTRY):
        raise EvidenceError("The Forge server registry evidence is malformed")
    if (
        type(registry.get("range")) is not int
        or registry.get("same_instance_at_server_started") is not True
        or not exact_json_value(registry, EXPECTED_REGISTRY)
    ):
        raise EvidenceError("The Forge server registry evidence is invalid")

    loot_condition = report.get("loot_condition")
    if (
        not isinstance(loot_condition, dict)
        or set(loot_condition) != set(EXPECTED_LOOT_CONDITION)
        or loot_condition.get("same_state_at_server_started") is not True
        or not exact_json_value(loot_condition, EXPECTED_LOOT_CONDITION)
    ):
        raise EvidenceError("The Forge server loot-condition evidence is invalid")

    tags = report.get("tags")
    if not isinstance(tags, dict) or set(tags) != set(EXPECTED_TAGS):
        raise EvidenceError("The Forge server tag evidence is malformed")
    if (
        type(tags.get("update_count")) is not int
        or tags.get("should_update_static_data") is not True
        or tags.get("same_membership_at_server_started") is not True
        or not exact_json_value(tags, EXPECTED_TAGS)
    ):
        raise EvidenceError("The Forge server tag evidence is invalid")
    for tag_name in ("vibrations", "warden_can_listen"):
        tag = tags.get(tag_name)
        if not isinstance(tag, dict) or tag.get("contains_event") is not True:
            raise EvidenceError(
                f"The Forge server {tag_name} membership evidence is invalid"
            )

    lifecycle = report.get("lifecycle")
    if lifecycle != EXPECTED_LIFECYCLE or not all(
        isinstance(phase, str) for phase in lifecycle
    ):
        raise EvidenceError("The Forge server lifecycle evidence is invalid")
    validate_assertions(report.get("assertions"))


def profile_record_from_launcher(value: object) -> ProfileRecord:
    """Validates profile provenance embedded in the launcher result."""
    if not isinstance(value, dict) or set(value) != {
        "relative_path",
        "size",
        "sha256",
    }:
        raise EvidenceError("The Forge server launcher profile provenance is malformed")
    if (
        value.get("relative_path")
        != PROFILE_MANIFEST_RELATIVE_PATH.as_posix()
        or type(value.get("size")) is not int
        or int(value["size"]) <= 0
    ):
        raise EvidenceError("The Forge server launcher profile provenance is invalid")
    return ProfileRecord(
        relative_path=str(value["relative_path"]),
        size=int(value["size"]),
        sha256=validate_hex_digest(
            value.get("sha256"),
            "Forge server launcher profile",
        ),
    )


def validate_launcher_result(
    launcher: dict[str, object],
    log_path: Path,
    expected_profile: ProfileRecord,
) -> None:
    """Validates zero-exit launcher and copied-log provenance."""
    if set(launcher) != {
        "schema",
        "profile_id",
        "scenario",
        "task_path",
        "exit_code",
        "timed_out",
        "profile_manifest",
        "server_log",
    }:
        raise EvidenceError("The Forge server launcher result field inventory changed")
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
    if profile_record_from_launcher(launcher.get("profile_manifest")) != expected_profile:
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
        or int(server_log["size"]) <= 0
        or server_log["size"] != log_path.stat().st_size
        or validate_hex_digest(
            server_log.get("sha256"),
            "Forge server launcher log",
        )
        != sha256_file(log_path)
    ):
        raise EvidenceError(
            "The Forge server copied log differs from launcher provenance"
        )


def validate_log(log_path: Path) -> str:
    """Validates exact probe ordering and absence of client or fatal markers."""
    require_regular_file(log_path, "Forge server copied log")
    if log_path.stat().st_size <= 0 or log_path.stat().st_size > MAXIMUM_LOG_SIZE:
        raise EvidenceError("The Forge server copied log has an invalid size")
    try:
        content = log_path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exception:
        raise EvidenceError(f"Cannot read Forge server copied log: {exception}") from exception
    fatal_marker = next(
        (marker for marker in FATAL_LOG_MARKERS if marker in content),
        None,
    )
    if fatal_marker is not None:
        raise EvidenceError(
            f"The Forge server log contains fatal marker: {fatal_marker}"
        )
    client_marker = next(
        (marker for marker in CLIENT_LOG_MARKERS if marker in content),
        None,
    )
    if client_marker is not None:
        raise EvidenceError(
            f"The Forge server log contains client marker: {client_marker}"
        )
    unexpected_client_class = next(
        (
            class_name
            for class_name in re.findall(
                r"net\.minecraft\.client\.[A-Za-z0-9_.$]+",
                content,
            )
            if class_name != "net.minecraft.client.network.LanServerPinger"
        ),
        None,
    )
    if unexpected_client_class is not None:
        raise EvidenceError(
            "The Forge server log contains an unexpected client class marker"
        )
    missing_normal_marker = next(
        (marker for marker in NORMAL_SERVER_LOG_MARKERS if marker not in content),
        None,
    )
    if missing_normal_marker is not None:
        raise EvidenceError(
            "The Forge server log lacks normal lifecycle marker: "
            f"{missing_normal_marker}"
        )
    phases = re.findall(r"\[EtherologyServerProbe\] ([a-z_]+)", content)
    if phases != list(PROBE_LOG_PHASES):
        raise EvidenceError(
            "The Forge server log has an invalid probe lifecycle: "
            f"expected={list(PROBE_LOG_PHASES)}, actual={phases}"
        )
    if content.count(TERMINATION_LOG_TOKEN) != 1:
        raise EvidenceError(
            "The Forge server log has an invalid userdev termination contract"
        )
    return sha256_file(log_path)


def validate_evidence_inventory(
    scenario_root: Path,
    include_manifest: bool,
) -> None:
    """Validates an exact non-linked evidence tree."""
    require_directory(scenario_root, "Forge server evidence root")
    files: set[str] = set()
    directories: set[str] = set()
    for path in scenario_root.rglob("*"):
        relative_path = path.relative_to(scenario_root).as_posix()
        if path.is_symlink():
            raise EvidenceError(
                f"Forge server evidence contains a linked entry: {relative_path}"
            )
        if path.is_file():
            files.add(relative_path)
        elif path.is_dir():
            directories.add(relative_path)
        else:
            raise EvidenceError(
                f"Forge server evidence contains a special entry: {relative_path}"
            )
    expected_files = set(EVIDENCE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    if files != expected_files or directories != EVIDENCE_DIRECTORIES:
        raise EvidenceError(
            "The Forge server evidence inventory changed: "
            f"missing_files={sorted(expected_files - files)}, "
            f"unexpected_files={sorted(files - expected_files)}, "
            f"missing_directories={sorted(EVIDENCE_DIRECTORIES - directories)}, "
            f"unexpected_directories={sorted(directories - EVIDENCE_DIRECTORIES)}"
        )
    total_size = sum(
        path.stat().st_size
        for path in scenario_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if total_size > MAXIMUM_EVIDENCE_SIZE:
        raise EvidenceError("The Forge server evidence exceeds the size bound")


def validate_publication_order(scenario_root: Path) -> None:
    """Requires report, copied log, launcher result, then completion."""
    report = scenario_root / "reports" / "report.json"
    launcher = scenario_root / "reports" / "launcher-result.json"
    done = scenario_root / "reports" / "done.marker"
    log = scenario_root / "logs" / "latest.log"
    if log.stat().st_mtime_ns <= report.stat().st_mtime_ns:
        raise EvidenceError("The Forge server copied log predates the report")
    if launcher.stat().st_mtime_ns <= log.stat().st_mtime_ns:
        raise EvidenceError(
            "The Forge server launcher result predates report or copied log"
        )
    if done.stat().st_mtime_ns <= launcher.stat().st_mtime_ns:
        raise EvidenceError(
            "The Forge server completion marker was not published last"
        )


def validate_payload(
    scenario_root: Path,
    expected_profile: ProfileRecord,
    enforce_publication_order: bool,
    include_manifest: bool = False,
) -> ValidatedPayload:
    """Validates all self-contained scenario payload semantics."""
    validate_evidence_inventory(scenario_root, include_manifest=include_manifest)
    done_path = scenario_root / "reports" / "done.marker"
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
    validate_launcher_result(launcher, log_path, expected_profile)
    if enforce_publication_order:
        validate_publication_order(scenario_root)
    return ValidatedPayload(
        profile_record=expected_profile,
        assertion_count=len(EXPECTED_ASSERTIONS),
        log_sha256=log_sha256,
    )


def validate_exact_runtime_path(runtime: Path, expected_runtime: Path) -> Path:
    """Requires the unique repository-owned dedicated-server runtime."""
    repository_root = repository_anchor(
        expected_runtime,
        RUNTIME_RELATIVE_PATH,
        "Forge server runtime",
    )
    require_no_symlink_components(
        runtime,
        repository_root,
        "Forge server runtime",
    )
    require_directory(runtime, "Forge server runtime")
    if (
        runtime.name != PROFILE_ID
        or runtime.absolute() != expected_runtime.absolute()
    ):
        raise EvidenceError(
            "The Forge server runtime differs from the unique isolated profile"
        )
    return repository_root


def validate_live_runtime(
    runtime: Path = DEFAULT_RUNTIME_ROOT,
    profile_manifest_path: Path = PROFILE_MANIFEST_PATH,
    expected_runtime: Path = DEFAULT_RUNTIME_ROOT,
) -> EvidenceSummary:
    """Validates one repository-owned native dedicated-server run."""
    repository_root = validate_exact_runtime_path(runtime, expected_runtime)
    profile_repository_root = repository_anchor(
        profile_manifest_path,
        PROFILE_MANIFEST_RELATIVE_PATH,
        "Forge server profile manifest",
    )
    if profile_repository_root.absolute() != repository_root.absolute():
        raise EvidenceError(
            "The Forge server runtime and profile use different repositories"
        )
    profile_record = load_profile_manifest(profile_manifest_path)
    validate_profile_marker(runtime, profile_record)
    expected_runtime_entries = {PROFILE_MARKER_NAME, "game", "evidence"}
    actual_runtime_entries = {entry.name for entry in runtime.iterdir()}
    if actual_runtime_entries != expected_runtime_entries:
        raise EvidenceError(
            "The Forge server runtime inventory changed: "
            f"missing={sorted(expected_runtime_entries - actual_runtime_entries)}, "
            f"unexpected={sorted(actual_runtime_entries - expected_runtime_entries)}"
        )
    game = require_directory(runtime / "game", "Forge server game directory")
    if (game / "evidence").exists() or (game / "evidence").is_symlink():
        raise EvidenceError(
            "The Forge server evidence must be rooted outside the game directory"
        )
    eula = require_regular_file(game / "eula.txt", "Forge server EULA")
    if eula.read_bytes() != b"eula=true\n":
        raise EvidenceError("The Forge server EULA differs from its profile")
    server_properties = require_regular_file(
        game / "server.properties",
        "Forge server properties",
    )
    if server_properties.stat().st_size <= 0:
        raise EvidenceError("The Forge server properties file is empty")
    for directory_name in ("config", "crash-reports", "logs", "mods", "world"):
        require_directory(
            game / directory_name,
            f"Forge server game {directory_name} directory",
        )
    crash_reports = game / "crash-reports"
    if any(crash_reports.iterdir()):
        raise EvidenceError("The Forge server runtime contains a crash report")
    if any((game / "mods").iterdir()):
        raise EvidenceError(
            "The Loom userdev dedicated server must not stage mod JARs"
        )
    level_dat = require_regular_file(
        game / "world" / "level.dat",
        "Forge server level.dat",
    )
    if level_dat.stat().st_size <= 0:
        raise EvidenceError("The Forge server level.dat is empty")

    evidence = require_directory(runtime / "evidence", "Forge server evidence directory")
    if {entry.name for entry in evidence.iterdir()} != {SCENARIO_ID}:
        raise EvidenceError("The Forge server evidence scenario inventory changed")
    scenario_root = evidence / SCENARIO_ID
    require_no_symlink_components(
        scenario_root,
        runtime,
        "Forge server evidence",
    )
    payload = validate_payload(
        scenario_root,
        profile_record,
        enforce_publication_order=True,
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
        raise EvidenceError(
            "The Forge server evidence log is not an exact game-log copy"
        )
    return EvidenceSummary(
        profile_id=PROFILE_ID,
        assertion_count=payload.assertion_count,
        log_sha256=payload.log_sha256,
    )


def validate_archive_root_identity(archive_root: Path) -> None:
    """Requires the immutable registry-foundation server-v4 directory identity."""
    if archive_root.name != ARCHIVE_DIRECTORY_NAME:
        raise EvidenceError(
            "The Forge server archive directory does not identify registry-foundation profile v4"
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
    require_no_symlink_components(
        archive_root,
        repository_root,
        "Forge server archive",
    )
    if archive_root.absolute() != expected_archive_root.absolute():
        raise EvidenceError(
            "The Forge server archive is outside its exact repository destination"
        )
    validate_archive_root_identity(archive_root)
    return repository_root


def archive_file_records(archive_root: Path) -> dict[str, dict[str, object]]:
    """Builds exact size and SHA-256 records for all archive payloads."""
    return {
        relative_path: {
            "size": (archive_root / relative_path).stat().st_size,
            "sha256": sha256_file(archive_root / relative_path),
        }
        for relative_path in EVIDENCE_PAYLOAD_PATHS
    }


def build_archive_manifest(
    profile_manifest_path: Path,
    archive_root: Path,
    expected_archive_root: Path = DEFAULT_ARCHIVE_ROOT,
) -> dict[str, object]:
    """Builds a one-time manifest from a complete copied payload."""
    repository_root = validate_archive_destination(
        archive_root,
        expected_archive_root,
    )
    profile_repository_root = repository_anchor(
        profile_manifest_path,
        PROFILE_MANIFEST_RELATIVE_PATH,
        "Forge server profile manifest",
    )
    if profile_repository_root.absolute() != repository_root.absolute():
        raise EvidenceError(
            "The Forge server archive and profile use different repositories"
        )
    profile_record = load_profile_manifest(profile_manifest_path)
    payload = validate_payload(
        archive_root,
        profile_record,
        enforce_publication_order=True,
    )
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
        "files": archive_file_records(archive_root),
    }


def write_archive_manifest(
    profile_manifest_path: Path,
    archive_root: Path,
    expected_archive_root: Path = DEFAULT_ARCHIVE_ROOT,
) -> Path:
    """Creates the exact repository archive manifest once."""
    validate_archive_destination(archive_root, expected_archive_root)
    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if manifest_path.exists() or manifest_path.is_symlink():
        raise EvidenceError(
            f"The Forge server archive manifest already exists: {manifest_path}"
        )
    manifest = build_archive_manifest(
        profile_manifest_path,
        archive_root,
        expected_archive_root,
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
    """Validates archive identity, provenance, and payload records."""
    if set(manifest) != {
        "schema",
        "kind",
        "verification_scope",
        "scenario",
        "profile",
        "runtime",
        "assertion_count",
        "publication",
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
        or not exact_json_value(
            manifest.get("publication"),
            PUBLICATION_ATTESTATION,
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
        or profile.get("manifest_path")
        != PROFILE_MANIFEST_RELATIVE_PATH.as_posix()
        or type(profile.get("manifest_size")) is not int
        or int(profile["manifest_size"]) <= 0
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

    runtime = manifest.get("runtime")
    if not isinstance(runtime, dict) or runtime != {
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "distribution": "DEDICATED_SERVER",
        "task_path": TASK_PATH,
        "execution": EXECUTION_KIND,
    }:
        raise EvidenceError("The Forge server archive runtime identity is invalid")
    if type(runtime.get("java")) is not int:
        raise EvidenceError("The Forge server archive Java identity is invalid")

    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(EVIDENCE_PAYLOAD_PATHS):
        raise EvidenceError("The Forge server archive payload inventory changed")
    validated_files: dict[str, dict[str, object]] = {}
    for relative_path in EVIDENCE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if not isinstance(record, dict) or set(record) != {"size", "sha256"}:
            raise EvidenceError(
                f"The Forge server archive record is malformed: {relative_path}"
            )
        if type(record.get("size")) is not int or int(record["size"]) <= 0:
            raise EvidenceError(
                f"The Forge server archive size is invalid: {relative_path}"
            )
        validate_hex_digest(
            record.get("sha256"),
            f"archived Forge server payload {relative_path}",
        )
        validated_files[relative_path] = record
    return ValidatedArchiveManifest(
        profile_record=profile_record,
        files=validated_files,
    )


def validate_archived_evidence(
    archive_root: Path,
    expected_archive_root: Path = DEFAULT_ARCHIVE_ROOT,
) -> ArchiveEvidenceSummary:
    """Validates a frozen server archive without consulting live state."""
    validate_archive_destination(archive_root, expected_archive_root)
    validate_evidence_inventory(archive_root, include_manifest=True)
    manifest = require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME,
        "Forge server archive manifest",
    )
    validated_manifest = validate_archive_manifest_shape(archive_root, manifest)
    for relative_path, record in validated_manifest.files.items():
        path = archive_root / relative_path
        if (
            path.stat().st_size != record["size"]
            or sha256_file(path) != record["sha256"]
        ):
            raise EvidenceError(
                f"Forge server archive payload differs: {relative_path}"
            )
    payload = validate_payload(
        archive_root,
        validated_manifest.profile_record,
        enforce_publication_order=False,
        include_manifest=True,
    )
    if payload.assertion_count != manifest["assertion_count"]:
        raise EvidenceError(
            "The Forge server archive assertion count differs from its manifest"
        )
    return ArchiveEvidenceSummary(
        profile_id=PROFILE_ID,
        assertion_count=payload.assertion_count,
        log_sha256=payload.log_sha256,
    )


def parse_arguments() -> argparse.Namespace:
    """Parses one mutually exclusive live or archive verification mode."""
    parser = argparse.ArgumentParser(
        description=(
            "Validate Forge 1.20.1 dedicated-server registry-foundation evidence."
        )
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--runtime", type=Path)
    mode.add_argument("--archive", type=Path)
    mode.add_argument("--create-archive-manifest", type=Path)
    return parser.parse_args()


def main() -> int:
    """Runs the selected fail-closed evidence operation."""
    arguments = parse_arguments()
    try:
        if arguments.create_archive_manifest is not None:
            manifest_path = write_archive_manifest(
                PROFILE_MANIFEST_PATH,
                arguments.create_archive_manifest,
            )
            print(f"Created Forge server archive manifest: {manifest_path}")
            print(
                "Archive integrity only: current sources and rebuilt artifacts "
                "were not compared."
            )
            return 0
        if arguments.archive is not None:
            summary = validate_archived_evidence(arguments.archive)
            print(
                f"Validated archived {SCENARIO_ID} for {summary.profile_id}: "
                f"{summary.assertion_count} assertions"
            )
            print(f"Server log SHA-256: {summary.log_sha256}")
            print(
                "Archive integrity only: current sources and rebuilt artifacts "
                "were not compared."
            )
            return 0
        summary = validate_live_runtime(arguments.runtime)
    except (EvidenceError, OSError) as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2
    print(
        f"Validated live {SCENARIO_ID} for {summary.profile_id}: "
        f"{summary.assertion_count} assertions"
    )
    print(f"Server log SHA-256: {summary.log_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
