#!/usr/bin/env python3
"""Run the bounded Forge 1.20.1 particle-registry probe in isolated state."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from typing import BinaryIO

import forge_server_contract_v8 as contract_v8


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIRECTORY.parents[1]
PROFILE_MANIFEST_RELATIVE_PATH = Path(
    "scripts/e2e/forge-server-1.20.1-profile.json"
)
PROBE_SOURCE_RELATIVE_PATH = Path(
    "e2e-harness/forge-server/1.20.1/src/main/java/"
    "dev/theplumteam/etherology/e2e/server/RegistryFoundationServerProbe.java"
)
MANIFEST_PATH = REPOSITORY_ROOT / PROFILE_MANIFEST_RELATIVE_PATH
STATE_ROOT = SCRIPT_DIRECTORY / ".state"
RUNTIMES_ROOT = STATE_ROOT / "runtimes"
PROFILE_ID = contract_v8.PROFILE_ID
SCENARIO_ID = contract_v8.SCENARIO_ID
TASK_PATH = contract_v8.TASK_PATH
PROFILE_MARKER_NAME = ".etherology-forge-server-e2e-profile.json"
MANAGED_BY = "scripts/e2e/forge_server.py"
CAFFEINATE_PATH = Path("/usr/bin/caffeinate")
GRADLE_JAVA_OVERRIDE_ENVIRONMENT_VARIABLE = "ETHERLOGY_E2E_GRADLE_JAVA"
RUN_TIMEOUT_SECONDS = 15 * 60
PROCESS_STOP_TIMEOUT_SECONDS = 15
PROCESS_POLL_INTERVAL_SECONDS = 0.1
MAXIMUM_PROCESS_LOG_SIZE = 64 * 1024 * 1024
MAXIMUM_SERVER_LOG_SIZE = 48 * 1024 * 1024
COMPLETION_MARKER_CONTENT = b"complete\n"
REQUIRED_MOD_IDS = contract_v8.REQUIRED_MOD_IDS
FORBIDDEN_MOD_IDS = contract_v8.FORBIDDEN_MOD_IDS
RELOAD_PACK_DIRECTORY = contract_v8.RELOAD_PACK_DIRECTORY
RELOAD_PACK_ENABLED_NAME = contract_v8.RELOAD_PACK_ENABLED_NAME
RELOAD_PACK_RESOURCES = contract_v8.RELOAD_PACK_RESOURCES
ETHER_SOURCE_LISTENER_CLASS = contract_v8.ETHER_SOURCE_LISTENER_CLASS
ENCHANTMENT_REGISTRY_ID = contract_v8.ENCHANTMENT_REGISTRY_ID
NON_TREASURE_TAG_ID = contract_v8.NON_TREASURE_TAG_ID
ENCHANTMENT_IDS = contract_v8.ENCHANTMENT_IDS
ENCHANTMENTS = contract_v8.ENCHANTMENTS
PARTICLE_REGISTRY_ID = contract_v8.PARTICLE_REGISTRY_ID
FEY_PARTICLE_TYPE_CLASS = contract_v8.FEY_PARTICLE_TYPE_CLASS
PARTICLE_IDS = contract_v8.PARTICLE_IDS
PARTICLE_PAYLOAD_FAMILIES = contract_v8.PARTICLE_PAYLOAD_FAMILIES
PARTICLES = contract_v8.PARTICLES
SEAL_TYPE_ORDER = contract_v8.SEAL_TYPE_ORDER
SEAL_TYPES = contract_v8.SEAL_TYPES
INITIAL_ETHER_SOURCE_ENTRIES = contract_v8.INITIAL_ETHER_SOURCE_ENTRIES
RELOADED_ETHER_SOURCE_ENTRIES = contract_v8.RELOADED_ETHER_SOURCE_ENTRIES
canonical_ether_source_entries = contract_v8.canonical_ether_source_entries
EXPECTED_LIFECYCLE = contract_v8.EXPECTED_LIFECYCLE
EXPECTED_ASSERTION_NAMES = contract_v8.EXPECTED_ASSERTION_NAMES
EXPECTED_ASSERTION_VALUES = contract_v8.EXPECTED_ASSERTION_VALUES
PROBE_LOG_PHASES = contract_v8.PROBE_LOG_PHASES
SERVER_LOG_TOKENS = contract_v8.SERVER_LOG_TOKENS
CLIENT_LOG_MARKERS = contract_v8.CLIENT_LOG_MARKERS
CLIENT_CLASS_PATTERN = contract_v8.CLIENT_CLASS_PATTERN
ALLOWED_DEDICATED_SERVER_CLIENT_CLASSES = (
    contract_v8.ALLOWED_DEDICATED_SERVER_CLIENT_CLASSES
)
FATAL_SERVER_LOG_MARKERS = (
    "A mod crashed on startup!",
    "Encountered an unexpected exception",
    "Exception in server tick loop",
    "Failed to start the minecraft server",
    "Missing or unsupported mandatory dependencies",
    "ModLoadingException",
    "Uncaught exception in thread",
    "Failed to execute reload",
    "dev.theplumteam.etherology.e2e.forge.ForgeE2eHarness",
    "[FATAL]",
    "/ERROR]",
    "/FATAL]",
)
SERVER_PROPERTIES_CONTENT = (
    "allow-flight=false\n"
    "difficulty=peaceful\n"
    "enable-command-block=false\n"
    "enable-query=false\n"
    "enable-rcon=false\n"
    "enable-status=false\n"
    "enforce-secure-profile=false\n"
    "force-gamemode=true\n"
    "gamemode=creative\n"
    "generate-structures=false\n"
    "level-name=world\n"
    "level-type=minecraft:normal\n"
    "max-players=1\n"
    "motd=Etherology dedicated-server E2E\n"
    "online-mode=false\n"
    "pvp=false\n"
    "server-ip=127.0.0.1\n"
    "server-port=0\n"
    "simulation-distance=4\n"
    "spawn-animals=false\n"
    "spawn-monsters=false\n"
    "spawn-npcs=false\n"
    "spawn-protection=0\n"
    "sync-chunk-writes=true\n"
    "view-distance=4\n"
    "white-list=false\n"
)


class E2EError(RuntimeError):
    """Reports a dedicated-server profile, isolation, or lifecycle failure."""


@dataclass(frozen=True)
class ResolvedConfiguration:
    """Holds the tracked server profile and its resolved release owners."""

    manifest: dict[str, object]
    properties: dict[str, str]
    artifact_lane: dict[str, object]
    runtime_lane: dict[str, object]
    repository_root: Path
    profile_manifest_path: Path


def load_json_object(path: Path, description: str) -> dict[str, object]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise E2EError(f"Cannot read {description} {path}: {exception}") from exception
    if not isinstance(value, dict):
        raise E2EError(f"The {description} must contain a JSON object: {path}")
    return value


def parse_gradle_properties(path: Path) -> dict[str, str]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exception:
        raise E2EError(f"Cannot read Gradle properties {path}: {exception}") from exception
    properties: dict[str, str] = {}
    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        name, separator, value = line.partition("=")
        name = name.strip()
        if not separator or not name or name in properties:
            raise E2EError(f"Invalid or duplicate Gradle property in {path}: {raw_line}")
        properties[name] = value.strip()
    return properties


def require_object(container: dict[str, object], name: str) -> dict[str, object]:
    value = container.get(name)
    if not isinstance(value, dict):
        raise E2EError(f"The manifest {name} object is invalid")
    return value


def require_list(container: dict[str, object], name: str) -> list[object]:
    value = container.get(name)
    if not isinstance(value, list):
        raise E2EError(f"The manifest {name} list is invalid")
    return value


def find_unique_row(
    rows: object, key: str, value: str, description: str
) -> dict[str, object]:
    if not isinstance(rows, list):
        raise E2EError(f"The release matrix {description} list is invalid")
    matches = [row for row in rows if isinstance(row, dict) and row.get(key) == value]
    if len(matches) != 1:
        raise E2EError(
            f"Expected one {description} row where {key}={value}, found {len(matches)}"
        )
    return matches[0]


def safe_leaf_name(raw_value: object, field_name: str) -> str:
    if not isinstance(raw_value, str):
        raise E2EError(f"The manifest {field_name} field is invalid")
    path = Path(raw_value)
    if path.parts != (raw_value,) or raw_value in ("", ".", ".."):
        raise E2EError(f"The manifest {field_name} field is unsafe")
    return raw_value


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


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            while chunk := handle.read(1024 * 1024):
                digest.update(chunk)
    except OSError as exception:
        raise E2EError(f"Cannot hash {path}: {exception}") from exception
    return digest.hexdigest()


def validate_manifest_shape(manifest: dict[str, object]) -> None:
    if set(manifest) != {
        "schema",
        "profile",
        "release",
        "launch",
        "evidence",
        "profile_directories",
        "required_mod_ids",
        "forbidden_mod_ids",
    } or type(manifest.get("schema")) is not int or manifest.get("schema") != 1:
        raise E2EError("Unsupported Forge dedicated-server profile schema")

    profile = require_object(manifest, "profile")
    if not exact_json_value(profile, {
        "id": PROFILE_ID,
        "runtime_directory": PROFILE_ID,
        "game_directory": "game",
    }):
        raise E2EError("The dedicated-server profile identity or directory changed")
    for name, value in profile.items():
        safe_leaf_name(value, f"profile.{name}")

    release = require_object(manifest, "release")
    if not exact_json_value(release, {
        "matrix": "release/release-matrix.json",
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
    }):
        raise E2EError("The profile must use the exact Forge 1.20.1 release lane")

    launch = require_object(manifest, "launch")
    if not exact_json_value(launch, {
        "kind": "loom-userdev",
        "task_path": TASK_PATH,
        "scenario": SCENARIO_ID,
        "maximum_memory_mb": 2048,
    }):
        raise E2EError("The dedicated-server launch contract changed")

    evidence = require_object(manifest, "evidence")
    if not exact_json_value(evidence, {
        "directory": "evidence",
        "scenario_directory": SCENARIO_ID,
        "report": "reports/report.json",
        "launcher_result": "reports/launcher-result.json",
        "completion_marker": "reports/done.marker",
        "server_log": "logs/latest.log",
    }):
        raise E2EError("The dedicated-server evidence contract changed")

    directories = require_list(manifest, "profile_directories")
    if directories != ["config", "crash-reports", "evidence", "logs", "mods", "world"]:
        raise E2EError("The dedicated-server directory inventory changed")
    for directory in directories:
        safe_leaf_name(directory, "profile_directories entry")

    if require_list(manifest, "required_mod_ids") != list(REQUIRED_MOD_IDS):
        raise E2EError("The dedicated-server required mod subset changed")
    if require_list(manifest, "forbidden_mod_ids") != list(FORBIDDEN_MOD_IDS):
        raise E2EError("The dedicated-server forbidden mod inventory changed")


def ensure_regular_unlinked_file(path: Path, description: str) -> None:
    if path.is_symlink() or not path.is_file():
        raise E2EError(f"{description} is missing or linked: {path}")


def ensure_no_symlink_components(path: Path, anchor: Path) -> None:
    absolute_anchor = anchor.absolute()
    absolute_path = path.absolute()
    try:
        relative_path = absolute_path.relative_to(absolute_anchor)
    except ValueError as exception:
        raise E2EError(f"Owned path escapes its repository state root: {path}") from exception
    current_path = absolute_anchor
    if current_path.is_symlink():
        raise E2EError(f"Owned path resolves through a symlink: {current_path}")
    for part in relative_path.parts:
        current_path /= part
        if current_path.is_symlink():
            raise E2EError(f"Owned path resolves through a symlink: {current_path}")


def load_configuration(
    manifest_path: Path = MANIFEST_PATH,
    repository_root: Path = REPOSITORY_ROOT,
) -> ResolvedConfiguration:
    root = repository_root.resolve()
    expected_manifest_path = root / PROFILE_MANIFEST_RELATIVE_PATH
    if manifest_path.absolute() != expected_manifest_path:
        raise E2EError(
            "The dedicated-server profile must be loaded from its tracked repository path"
        )
    ensure_regular_unlinked_file(expected_manifest_path, "Dedicated-server profile")
    manifest = load_json_object(expected_manifest_path, "dedicated-server profile")
    validate_manifest_shape(manifest)
    if (
        expected_manifest_path.stat().st_size != contract_v8.PROFILE_MANIFEST_SIZE
        or sha256_file(expected_manifest_path)
        != contract_v8.PROFILE_MANIFEST_SHA256
    ):
        raise E2EError(
            "The dedicated-server profile bytes differ from the immutable v8 contract"
        )
    properties = parse_gradle_properties(root / "gradle.properties")
    if properties.get("minecraft_version_1_20_1") != "1.20.1":
        raise E2EError("The Minecraft 1.20.1 Gradle property changed")
    if properties.get("forge_version_1_20_1") != "1.20.1-47.4.9":
        raise E2EError("The Forge 47.4.9 Gradle property changed")

    matrix_path = root / "release/release-matrix.json"
    ensure_regular_unlinked_file(matrix_path, "Release matrix")
    matrix = load_json_object(matrix_path, "release matrix")
    if matrix.get("schema_version") != 1:
        raise E2EError("Unsupported release matrix schema")
    artifact_lane = find_unique_row(
        matrix.get("artifacts"), "artifact_node", "forge-1.20.1", "artifact"
    )
    runtime_lane = find_unique_row(
        matrix.get("runtimes"), "artifact_node", "forge-1.20.1", "runtime"
    )
    if (
        artifact_lane.get("artifact_version") != "1.20.1"
        or artifact_lane.get("loader") != "forge"
        or artifact_lane.get("java") != 17
        or runtime_lane.get("runtime_version") != "1.20.1"
        or runtime_lane.get("loader") != "forge"
        or runtime_lane.get("loader_version") != "1.20.1-47.4.9"
        or runtime_lane.get("port") != 0
        or runtime_lane.get("java") != 17
    ):
        raise E2EError("The release matrix Forge 1.20.1 server lane changed")
    project = matrix.get("project")
    if not isinstance(project, dict) or project.get("mod_id") != "etherology":
        raise E2EError("The release matrix production mod identity changed")
    return ResolvedConfiguration(
        manifest=manifest,
        properties=properties,
        artifact_lane=artifact_lane,
        runtime_lane=runtime_lane,
        repository_root=root,
        profile_manifest_path=expected_manifest_path,
    )


def profile_spec(configuration: ResolvedConfiguration) -> dict[str, object]:
    return require_object(configuration.manifest, "profile")


def evidence_spec(configuration: ResolvedConfiguration) -> dict[str, object]:
    return require_object(configuration.manifest, "evidence")


def runtime_root(
    configuration: ResolvedConfiguration, state_root: Path = STATE_ROOT
) -> Path:
    return state_root / "runtimes" / str(profile_spec(configuration)["runtime_directory"])


def game_directory(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> Path:
    return (root or runtime_root(configuration)) / str(
        profile_spec(configuration)["game_directory"]
    )


def evidence_root(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> Path:
    evidence = evidence_spec(configuration)
    return (
        (root or runtime_root(configuration))
        / str(evidence["directory"])
        / str(evidence["scenario_directory"])
    )


def evidence_path(
    configuration: ResolvedConfiguration,
    field_name: str,
    root: Path | None = None,
) -> Path:
    raw_path = str(evidence_spec(configuration)[field_name])
    relative_path = Path(raw_path)
    if relative_path.is_absolute() or ".." in relative_path.parts:
        raise E2EError(f"The evidence {field_name} path is unsafe")
    return evidence_root(configuration, root) / relative_path


def profile_marker_path(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> Path:
    return (root or runtime_root(configuration)) / PROFILE_MARKER_NAME


def run_lock_path(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> Path:
    return state_root / f"{profile_spec(configuration)['id']}-run.lock"


def profile_descriptor(configuration: ResolvedConfiguration) -> dict[str, object]:
    manifest_path = configuration.profile_manifest_path
    ensure_regular_unlinked_file(manifest_path, "Dedicated-server profile")
    return {
        "schema": 1,
        "profile_id": PROFILE_ID,
        "managed_by": MANAGED_BY,
        "profile_manifest": {
            "relative_path": PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
            "size": manifest_path.stat().st_size,
            "sha256": sha256_file(manifest_path),
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


def ensure_owned_state_roots(state_root: Path = STATE_ROOT) -> None:
    ensure_no_symlink_components(state_root, state_root.parent)
    ensure_no_symlink_components(state_root / "runtimes", state_root.parent)
    for path, description in (
        (state_root, "Forge server E2E state root"),
        (state_root / "runtimes", "Forge server E2E runtimes root"),
    ):
        if path.exists() and not path.is_dir():
            raise E2EError(f"{description} must be a directory: {path}")


def write_bytes_exclusive(path: Path, content: bytes) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as handle:
        handle.write(content)


def publish_bytes_exclusive(path: Path, content: bytes) -> None:
    if path.exists() or path.is_symlink():
        raise E2EError(f"Refusing to replace existing evidence: {path}")
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
    )
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.link(temporary_path, path)
    except FileExistsError as exception:
        raise E2EError(f"Refusing to replace existing evidence: {path}") from exception
    finally:
        temporary_path.unlink(missing_ok=True)


def publish_json_exclusive(path: Path, value: dict[str, object]) -> None:
    try:
        content = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")
    except (TypeError, ValueError) as exception:
        raise E2EError(f"Cannot encode evidence JSON for {path}: {exception}") from exception
    publish_bytes_exclusive(path, content)


def verify_profile_marker(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> None:
    target_root = root or runtime_root(configuration)
    ensure_no_symlink_components(target_root, target_root.parent.parent)
    if not target_root.is_dir() or target_root.is_symlink():
        raise E2EError(f"Refusing to adopt unmarked server runtime: {target_root}")
    marker_path = profile_marker_path(configuration, target_root)
    if marker_path.is_symlink() or not marker_path.is_file():
        raise E2EError(f"Refusing to adopt unmarked server runtime: {target_root}")
    if not exact_json_value(
        load_json_object(marker_path, "server profile marker"),
        profile_descriptor(configuration),
    ):
        raise E2EError(f"Server profile marker does not match: {marker_path}")


def verify_controlled_server_files(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> None:
    game_root = game_directory(configuration, root)
    eula_path = game_root / "eula.txt"
    properties_path = game_root / "server.properties"
    for path, expected, description in (
        (eula_path, b"eula=true\n", "Dedicated-server EULA"),
        (
            properties_path,
            SERVER_PROPERTIES_CONTENT.encode("utf-8"),
            "Dedicated-server properties",
        ),
    ):
        ensure_regular_unlinked_file(path, description)
        try:
            actual = path.read_bytes()
        except OSError as exception:
            raise E2EError(f"Cannot read {description} {path}: {exception}") from exception
        if actual != expected:
            raise E2EError(f"{description} differs from its deterministic contract: {path}")


def verify_evidence_layout(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
    require_empty: bool = False,
) -> None:
    target_root = root or runtime_root(configuration)
    evidence_directory = target_root / str(evidence_spec(configuration)["directory"])
    if not evidence_directory.is_dir() or evidence_directory.is_symlink():
        raise E2EError(f"Server evidence root is missing or linked: {evidence_directory}")
    expected_scenario_name = str(evidence_spec(configuration)["scenario_directory"])
    if {entry.name for entry in evidence_directory.iterdir()} != {expected_scenario_name}:
        raise E2EError("Server evidence scenario inventory changed")
    scenario_root = evidence_root(configuration, target_root)
    ensure_no_symlink_components(scenario_root, target_root)
    if not scenario_root.is_dir() or scenario_root.is_symlink():
        raise E2EError(f"Server evidence scenario root is missing or linked: {scenario_root}")
    actual_directories = {entry.name for entry in scenario_root.iterdir()}
    if actual_directories != {"reports", "logs"}:
        raise E2EError("Server evidence scenario directory inventory changed")
    for name in ("reports", "logs"):
        directory = scenario_root / name
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"Server evidence directory is missing or linked: {directory}")
        if require_empty and any(directory.iterdir()):
            raise E2EError(f"Fresh server evidence directory is not empty: {directory}")


def verify_runtime(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
    require_fresh_evidence: bool = True,
) -> None:
    ensure_owned_state_roots(state_root)
    target_root = runtime_root(configuration, state_root)
    verify_profile_marker(configuration, target_root)
    expected_runtime_entries = {PROFILE_MARKER_NAME, "game", "evidence"}
    actual_runtime_entries = {entry.name for entry in target_root.iterdir()}
    if actual_runtime_entries != expected_runtime_entries:
        raise E2EError(
            "The pristine server runtime inventory changed: "
            f"missing={sorted(expected_runtime_entries - actual_runtime_entries)}, "
            f"unexpected={sorted(actual_runtime_entries - expected_runtime_entries)}"
        )
    target_game_root = game_directory(configuration, target_root)
    if not target_game_root.is_dir() or target_game_root.is_symlink():
        raise E2EError(f"Dedicated-server game directory is missing or linked: {target_game_root}")
    for name in ("config", "crash-reports", "logs", "mods", "world"):
        directory = target_game_root / name
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"Dedicated-server directory is missing or linked: {directory}")
    expected_game_entries = {
        "config",
        "crash-reports",
        "logs",
        "mods",
        "world",
        "eula.txt",
        "server.properties",
    }
    actual_game_entries = {entry.name for entry in target_game_root.iterdir()}
    if actual_game_entries != expected_game_entries:
        raise E2EError(
            "The pristine dedicated-server game inventory changed: "
            f"missing={sorted(expected_game_entries - actual_game_entries)}, "
            f"unexpected={sorted(actual_game_entries - expected_game_entries)}"
        )
    for name in ("config", "crash-reports", "logs", "mods", "world"):
        directory = target_game_root / name
        if any(directory.iterdir()):
            raise E2EError(f"The pristine dedicated-server directory is not empty: {directory}")
    verify_controlled_server_files(configuration, target_root)
    verify_evidence_layout(
        configuration,
        target_root,
        require_empty=require_fresh_evidence,
    )


def verify_generated_runtime(
    configuration: ResolvedConfiguration,
    root: Path,
) -> None:
    verify_profile_marker(configuration, root)
    target_game_root = game_directory(configuration, root)
    for name in ("config", "crash-reports", "logs", "mods", "world"):
        directory = target_game_root / name
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"Generated server directory is missing or linked: {directory}")
        ensure_no_symlink_components(directory, root)
        for descendant in directory.rglob("*"):
            if descendant.is_symlink():
                raise E2EError(f"Generated server state contains a symlink: {descendant}")
    mods_directory = target_game_root / "mods"
    if any(mods_directory.iterdir()):
        raise E2EError("The Loom dedicated server must not stage root mod JARs")
    verify_evidence_layout(configuration, root)


def provision_profile(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> None:
    ensure_owned_state_roots(state_root)
    state_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    runtimes_root = state_root / "runtimes"
    runtimes_root.mkdir(mode=0o700, exist_ok=True)
    target_root = runtime_root(configuration, state_root)
    ensure_no_symlink_components(target_root, state_root)
    if target_root.exists() or target_root.is_symlink():
        raise E2EError(
            "Refusing to reuse an existing dedicated-server runtime: "
            f"{target_root}"
        )

    staging_root = Path(
        tempfile.mkdtemp(prefix=f".{PROFILE_ID}.", dir=runtimes_root)
    )
    try:
        game_root = game_directory(configuration, staging_root)
        game_root.mkdir(mode=0o700)
        for name in ("config", "crash-reports", "logs", "mods", "world"):
            (game_root / name).mkdir(mode=0o700)
        write_bytes_exclusive(game_root / "eula.txt", b"eula=true\n")
        write_bytes_exclusive(
            game_root / "server.properties",
            SERVER_PROPERTIES_CONTENT.encode("utf-8"),
        )
        scenario_root = evidence_root(configuration, staging_root)
        (scenario_root / "reports").mkdir(mode=0o700, parents=True)
        (scenario_root / "logs").mkdir(mode=0o700)
        write_bytes_exclusive(
            profile_marker_path(configuration, staging_root),
            (json.dumps(profile_descriptor(configuration), indent=2, sort_keys=True) + "\n").encode(
                "utf-8"
            ),
        )
        try:
            target_root.mkdir(mode=0o700)
        except FileExistsError as exception:
            raise E2EError(
                "Refusing to reuse an existing dedicated-server runtime: "
                f"{target_root}"
            ) from exception
        for staging_entry in staging_root.iterdir():
            os.replace(staging_entry, target_root / staging_entry.name)
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root)
    verify_runtime(configuration, state_root)


def java_major_version(java_path: Path) -> int | None:
    if not java_path.is_file() or not os.access(java_path, os.X_OK):
        return None
    try:
        completed = subprocess.run(
            [str(java_path), "-XshowSettings:properties", "-version"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=20,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    match = re.search(r"java\.specification\.version\s*=\s*(\d+)", completed.stdout)
    if completed.returncode != 0 or match is None:
        return None
    return int(match.group(1))


def resolve_gradle_java() -> Path:
    candidates: list[Path] = []
    override = os.environ.get(GRADLE_JAVA_OVERRIDE_ENVIRONMENT_VARIABLE)
    if override:
        candidates.append(Path(override))
    configured_java_home = os.environ.get("JAVA_HOME")
    if configured_java_home:
        candidates.append(Path(configured_java_home) / "bin" / "java")
    java_home_tool = Path("/usr/libexec/java_home")
    if java_home_tool.is_file():
        try:
            completed = subprocess.run(
                [str(java_home_tool), "-v", "21"],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                timeout=15,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            completed = None
        if completed is not None and completed.returncode == 0 and completed.stdout.strip():
            candidates.append(Path(completed.stdout.strip()) / "bin" / "java")
    gradle_jdks = Path.home() / ".gradle" / "jdks"
    if gradle_jdks.is_dir() and not gradle_jdks.is_symlink():
        candidates.extend(sorted(gradle_jdks.glob("**/Contents/Home/bin/java")))
    path_java = shutil.which("java")
    if path_java is not None:
        candidates.append(Path(path_java))
    checked: set[Path] = set()
    for candidate in candidates:
        resolved = candidate.resolve(strict=False)
        if resolved in checked:
            continue
        checked.add(resolved)
        major_version = java_major_version(resolved)
        if major_version is not None and major_version >= 21:
            return resolved
    raise E2EError(
        "No JDK 21 or newer was found for Gradle; set "
        f"{GRADLE_JAVA_OVERRIDE_ENVIRONMENT_VARIABLE} to its Java executable"
    )


def verify_gradle_probe_definition(configuration: ResolvedConfiguration) -> None:
    build_file = configuration.repository_root / "forge/build.gradle.kts"
    ensure_regular_unlinked_file(build_file, "Forge build configuration")
    try:
        content = build_file.read_text(encoding="utf-8")
    except OSError as exception:
        raise E2EError(f"Cannot read Forge build configuration: {exception}") from exception
    required_fragments = (
        "runRegistryFoundationServerProbe",
        "etherology.serverProbe.profileId",
        "etherology.serverProbe.scenario",
        "etherology.serverProbe.evidenceRoot",
        "serverProbeJavaVersion = javaVersion",
        'tasks.named<JavaExec>("runRegistryFoundationServerProbe")',
        "javaLauncher.set(",
        "languageVersion.set(JavaLanguageVersion.of(serverProbeJavaVersion))",
        "serverProbeRunTask.configure",
        "dependsOn(verifyRegistryFoundationServerProbe)",
        PROFILE_ID,
        SCENARIO_ID,
    )
    missing = [fragment for fragment in required_fragments if fragment not in content]
    if missing:
        raise E2EError(f"The named Forge server probe task is incomplete: {missing}")
    verify_probe_source_lifecycle(configuration)


def verify_probe_source_lifecycle(configuration: ResolvedConfiguration) -> None:
    source_path = configuration.repository_root / PROBE_SOURCE_RELATIVE_PATH
    ensure_regular_unlinked_file(source_path, "Forge server probe source")
    try:
        content = source_path.read_text(encoding="utf-8")
    except OSError as exception:
        raise E2EError(f"Cannot read Forge server probe source: {exception}") from exception
    match = re.search(
        r"private static final List<String> EXPECTED_LIFECYCLE = List\.of\((.*?)\n    \);",
        content,
        flags=re.DOTALL,
    )
    if match is None:
        raise E2EError("The Forge server probe lifecycle declaration is missing")
    source_lifecycle = tuple(re.findall(r'"([a-z_]+)"', match.group(1)))
    if source_lifecycle != EXPECTED_LIFECYCLE:
        raise E2EError(
            "The Forge server probe and runner lifecycle contracts differ: "
            f"source={source_lifecycle}, runner={EXPECTED_LIFECYCLE}"
        )


def build_gradle_command(
    configuration: ResolvedConfiguration,
    java_path: Path,
    caffeinate_path: Path = CAFFEINATE_PATH,
) -> list[str]:
    gradle_wrapper = configuration.repository_root / "gradlew"
    ensure_regular_unlinked_file(gradle_wrapper, "Gradle wrapper")
    if not os.access(gradle_wrapper, os.X_OK):
        raise E2EError(f"Gradle wrapper is not executable: {gradle_wrapper}")
    ensure_regular_unlinked_file(caffeinate_path, "macOS caffeinate")
    java_version = java_major_version(java_path)
    if java_version is None or java_version < 21:
        raise E2EError(
            f"The selected Gradle Java executable is not JDK 21 or newer: {java_path}"
        )
    return [
        str(caffeinate_path),
        "-dimsu",
        str(gradle_wrapper),
        "--no-daemon",
        "--no-parallel",
        "--console=plain",
        TASK_PATH,
    ]


def verify_environment(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> tuple[Path, list[str]]:
    verify_runtime(configuration, state_root)
    lock_path = run_lock_path(configuration, state_root)
    if lock_path.exists() or lock_path.is_symlink():
        raise E2EError(f"A dedicated-server probe run is already owned: {lock_path}")
    verify_gradle_probe_definition(configuration)
    java_path = resolve_gradle_java()
    command = build_gradle_command(configuration, java_path)
    if any(argument.startswith("-P") for argument in command):
        raise E2EError("The server probe task must own its run directory without overrides")
    return java_path, command


def validate_probe_report(
    report: dict[str, object],
    configuration: ResolvedConfiguration,
) -> None:
    """Validates a probe report through the immutable profile-v8 contract."""
    required_mod_ids = require_list(configuration.manifest, "required_mod_ids")
    forbidden_mod_ids = require_list(configuration.manifest, "forbidden_mod_ids")
    try:
        contract_v8.validate_probe_report(
            report,
            required_mod_ids,
            forbidden_mod_ids,
        )
    except contract_v8.V8ContractError as exception:
        raise E2EError(str(exception)) from exception


def validate_server_log(path: Path) -> bytes:
    ensure_regular_unlinked_file(path, "Dedicated-server latest log")
    try:
        size = path.stat().st_size
        if size <= 0 or size > MAXIMUM_SERVER_LOG_SIZE:
            raise E2EError(f"Dedicated-server latest log has invalid size: {size}")
        content = path.read_bytes()
    except OSError as exception:
        raise E2EError(f"Cannot read dedicated-server latest log: {exception}") from exception
    text = content.decode("utf-8", errors="replace")
    fatal_marker = next(
        (marker for marker in FATAL_SERVER_LOG_MARKERS if marker in text), None
    )
    if fatal_marker is not None:
        raise E2EError(f"Dedicated-server latest log contains fatal marker: {fatal_marker}")
    client_marker = next(
        (marker for marker in CLIENT_LOG_MARKERS if marker in text), None
    )
    if client_marker is not None:
        raise E2EError(
            f"Dedicated-server latest log contains client marker: {client_marker}"
        )
    unexpected_client_class = next(
        (
            class_name
            for class_name in re.findall(CLIENT_CLASS_PATTERN, text)
            if class_name not in ALLOWED_DEDICATED_SERVER_CLIENT_CLASSES
        ),
        None,
    )
    if unexpected_client_class is not None:
        raise E2EError(
            "Dedicated-server latest log contains unexpected client class marker: "
            f"{unexpected_client_class}"
        )
    phases = re.findall(r"\[EtherologyServerProbe\] ([a-z_]+)", text)
    expected_phases = [*PROBE_LOG_PHASES, "loom_userdev_exit_scheduled"]
    if phases != expected_phases:
        raise E2EError(
            "Dedicated-server probe lifecycle changed: "
            f"expected={expected_phases}, actual={phases}"
        )
    positions: list[int] = []
    for token in SERVER_LOG_TOKENS:
        if text.count(token) != 1:
            raise E2EError(f"Dedicated-server lifecycle token count changed: {token}")
        positions.append(text.index(token))
    if positions != sorted(positions) or len(set(positions)) != len(positions):
        raise E2EError("Dedicated-server lifecycle tokens are out of order")
    for marker in ("Done (", "Stopping server", "Saving worlds", "All dimensions are saved"):
        if marker not in text:
            raise E2EError(f"Dedicated-server normal lifecycle marker is missing: {marker}")
    return content


def validate_world_save(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> Path:
    level_data = game_directory(configuration, root) / "world" / "level.dat"
    ensure_regular_unlinked_file(level_data, "Dedicated-server saved world level.dat")
    if level_data.stat().st_size <= 0:
        raise E2EError("Dedicated-server saved world level.dat is empty")
    crash_reports = game_directory(configuration, root) / "crash-reports"
    if any(crash_reports.iterdir()):
        raise E2EError("Dedicated-server runtime contains a crash report")
    return level_data


def launcher_result(
    configuration: ResolvedConfiguration,
    copied_server_log: Path,
) -> dict[str, object]:
    profile_manifest = configuration.profile_manifest_path
    return {
        "schema": 1,
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
        "task_path": TASK_PATH,
        "exit_code": 0,
        "timed_out": False,
        "profile_manifest": {
            "relative_path": PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
            "size": profile_manifest.stat().st_size,
            "sha256": sha256_file(profile_manifest),
        },
        "server_log": {
            "relative_path": "logs/latest.log",
            "size": copied_server_log.stat().st_size,
            "sha256": sha256_file(copied_server_log),
        },
    }


def stop_process_group(process: subprocess.Popen[bytes]) -> None:
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=PROCESS_STOP_TIMEOUT_SECONDS)
        return
    except subprocess.TimeoutExpired:
        pass
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=PROCESS_STOP_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired as exception:
        raise E2EError("The timed-out Gradle process group did not stop") from exception


def read_process_tail(path: Path) -> str:
    try:
        size = path.stat().st_size
        if size > MAXIMUM_PROCESS_LOG_SIZE:
            return f"process output exceeded {MAXIMUM_PROCESS_LOG_SIZE} bytes"
        with path.open("rb") as handle:
            if size > 8192:
                handle.seek(size - 8192)
            return handle.read().decode("utf-8", errors="replace").strip()
    except OSError as exception:
        return f"cannot read process output: {exception}"


def wait_for_bounded_process(
    process: subprocess.Popen[bytes],
    output_path: Path,
) -> int:
    deadline = time.monotonic() + RUN_TIMEOUT_SECONDS
    while True:
        try:
            output_size = output_path.stat().st_size
        except OSError as exception:
            raise E2EError(
                f"Cannot inspect named Forge server probe output: {exception}"
            ) from exception
        if output_size > MAXIMUM_PROCESS_LOG_SIZE:
            raise E2EError(
                "Named Forge server probe output exceeded "
                f"{MAXIMUM_PROCESS_LOG_SIZE} bytes during execution"
            )
        exit_code = process.poll()
        if exit_code is not None:
            try:
                final_output_size = output_path.stat().st_size
            except OSError as exception:
                raise E2EError(
                    f"Cannot inspect named Forge server probe output: {exception}"
                ) from exception
            if final_output_size > MAXIMUM_PROCESS_LOG_SIZE:
                raise E2EError(
                    "Named Forge server probe output exceeded "
                    f"{MAXIMUM_PROCESS_LOG_SIZE} bytes during execution"
                )
            return exit_code
        remaining_seconds = deadline - time.monotonic()
        if remaining_seconds <= 0:
            raise E2EError(
                "The named Forge server probe timed out after "
                f"{RUN_TIMEOUT_SECONDS} seconds"
            )
        try:
            exit_code = process.wait(
                timeout=min(PROCESS_POLL_INTERVAL_SECONDS, remaining_seconds)
            )
        except subprocess.TimeoutExpired:
            continue
        except KeyboardInterrupt as exception:
            raise E2EError("The named Forge server probe was interrupted") from exception
        try:
            final_output_size = output_path.stat().st_size
        except OSError as exception:
            raise E2EError(
                f"Cannot inspect named Forge server probe output: {exception}"
            ) from exception
        if final_output_size > MAXIMUM_PROCESS_LOG_SIZE:
            raise E2EError(
                "Named Forge server probe output exceeded "
                f"{MAXIMUM_PROCESS_LOG_SIZE} bytes during execution"
            )
        return exit_code


def execute_probe(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> dict[str, object]:
    java_path, command = verify_environment(configuration, state_root)
    target_root = runtime_root(configuration, state_root)
    lock_path = run_lock_path(configuration, state_root)
    try:
        write_bytes_exclusive(lock_path, f"pid={os.getpid()}\n".encode("utf-8"))
    except FileExistsError as exception:
        raise E2EError(
            f"A dedicated-server probe run is already owned: {lock_path}"
        ) from exception
    except OSError as exception:
        raise E2EError(
            f"Cannot acquire dedicated-server probe ownership: {exception}"
        ) from exception
    output_path: Path | None = None
    process: subprocess.Popen[bytes] | None = None
    try:
        output_descriptor, output_name = tempfile.mkstemp(
            prefix=".forge-server-gradle.", suffix=".log", dir=target_root
        )
        output_path = Path(output_name)
        environment = os.environ.copy()
        environment["JAVA_HOME"] = str(java_path.parent.parent)
        with os.fdopen(output_descriptor, "wb", buffering=0) as output_handle:
            try:
                process = subprocess.Popen(
                    command,
                    cwd=configuration.repository_root,
                    env=environment,
                    stdin=subprocess.DEVNULL,
                    stdout=output_handle,
                    stderr=subprocess.STDOUT,
                    start_new_session=True,
                    close_fds=True,
                )
            except OSError as exception:
                raise E2EError(
                    f"Cannot start the named Forge server probe: {exception}"
                ) from exception
            try:
                exit_code = wait_for_bounded_process(process, output_path)
            except E2EError:
                if process.poll() is None:
                    stop_process_group(process)
                raise
        if exit_code != 0:
            raise E2EError(
                f"The named Forge server probe exited with {exit_code}: "
                f"{read_process_tail(output_path)}"
            )

        verify_generated_runtime(configuration, target_root)
        report_path = evidence_path(configuration, "report", target_root)
        ensure_no_symlink_components(report_path, target_root)
        ensure_regular_unlinked_file(report_path, "Dedicated-server probe report")
        reports_directory = report_path.parent
        logs_directory = evidence_path(configuration, "server_log", target_root).parent
        if {entry.name for entry in reports_directory.iterdir()} != {report_path.name}:
            raise E2EError("The probe published an unexpected report payload")
        if any(logs_directory.iterdir()):
            raise E2EError("The probe published an unexpected log payload")
        report = load_json_object(report_path, "dedicated-server probe report")
        validate_probe_report(report, configuration)
        source_log = game_directory(configuration, target_root) / "logs" / "latest.log"
        log_content = validate_server_log(source_log)
        validate_world_save(configuration, target_root)

        copied_log = evidence_path(configuration, "server_log", target_root)
        publish_bytes_exclusive(copied_log, log_content)
        if copied_log.read_bytes() != log_content:
            raise E2EError("The copied dedicated-server log differs from latest.log")
        result = launcher_result(configuration, copied_log)
        publish_json_exclusive(
            evidence_path(configuration, "launcher_result", target_root), result
        )
        publish_bytes_exclusive(
            evidence_path(configuration, "completion_marker", target_root),
            COMPLETION_MARKER_CONTENT,
        )
        return result
    finally:
        if output_path is not None:
            output_path.unlink(missing_ok=True)
        lock_path.unlink(missing_ok=True)


def validate_command() -> int:
    configuration = load_configuration()
    verify_gradle_probe_definition(configuration)
    print(
        "Validated Forge dedicated-server profile: "
        f"{PROFILE_ID} / Minecraft 1.20.1 / Forge 47.4.9 / Java 17"
    )
    print(f"Named Loom task: {TASK_PATH}")
    print(f"Runtime root: {runtime_root(configuration)}")
    print("External game profiles consulted: 0")
    return 0


def provision_command() -> int:
    configuration = load_configuration()
    provision_profile(configuration)
    print(f"Provisioned repository-owned Forge server runtime: {runtime_root(configuration)}")
    print(f"Game directory: {game_directory(configuration)}")
    print(f"Evidence root: {evidence_root(configuration)}")
    print("Gradle and Minecraft were not launched; external game profiles consulted: 0")
    return 0


def check_command() -> int:
    configuration = load_configuration()
    java_path, command = verify_environment(configuration)
    print(
        "Ready: Minecraft 1.20.1 / Forge 47.4.9 / "
        "dedicated server Java 17 / Gradle host JDK 21+"
    )
    print(f"Runtime root: {runtime_root(configuration)}")
    print(f"Game directory: {game_directory(configuration)}")
    print(f"Scenario: {SCENARIO_ID}")
    print(f"Gradle host Java: {java_path}")
    print(f"Generated argv entries: {len(command)} (command intentionally not displayed)")
    print("External game profiles consulted: 0")
    return 0


def run_command() -> int:
    configuration = load_configuration()
    result = execute_probe(configuration)
    print(f"Completed isolated Forge dedicated-server scenario: {SCENARIO_ID}")
    print(f"Exit code: {result['exit_code']}; timed out: {result['timed_out']}")
    print(f"Evidence root: {evidence_root(configuration)}")
    print("Completion marker was published last")
    return 0


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Provision and run Etherology's repository-owned Forge 1.20.1 "
            "dedicated-server probe without consulting launcher profiles."
        )
    )
    parser.add_argument("action", choices=("validate", "provision", "check", "run"))
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    actions = {
        "validate": validate_command,
        "provision": provision_command,
        "check": check_command,
        "run": run_command,
    }
    try:
        return actions[arguments.action]()
    except E2EError as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
