#!/usr/bin/env python3
"""Provision and manage the repository-owned Etherology Fabric 1.20.1 client."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import importlib.metadata
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from typing import BinaryIO
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

from macos_guarded_java import (
    GuardedJavaError,
    memory_guard_is_enforcing,
    memory_guard_process_matches,
    owned_java_process_from_state,
    start_guarded_java,
    stop_guarded_java_launch,
    stop_owned_java_process,
    verify_guard_state_paths,
    verify_java_launch_contract,
    verify_java_option_environment,
)


EXPECTED_LAUNCHER_LIBRARY_VERSION = "8.0"
SCRIPT_DIRECTORY = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIRECTORY.parents[1]
MANIFEST_PATH = SCRIPT_DIRECTORY / "fabric-1.20.1-profile.json"
STATE_ROOT = SCRIPT_DIRECTORY / ".state"
RUNTIMES_ROOT = STATE_ROOT / "runtimes"
PROFILE_MARKER_NAME = ".etherology-e2e-profile.json"
EVIDENCE_MARKER_NAME = ".etherology-e2e-evidence.json"
CAFFEINATE_PATH = Path("/usr/bin/caffeinate")
STOP_TIMEOUT_SECONDS = 20
START_STABILITY_SECONDS = 2.0
MAXIMUM_DOWNLOAD_SIZE = 128 * 1024 * 1024
MAXIMUM_NESTED_JAR_SIZE = 64 * 1024 * 1024
MAXIMUM_NESTED_JAR_DEPTH = 12
MAXIMUM_PROCESS_LOG_SIZE = 64 * 1024 * 1024
JAVA_OVERRIDE_ENVIRONMENT_VARIABLE = "ETHERLOGY_E2E_JAVA_17"
JAVA_VERSION_PROBE_EXACT_HEAP_ARGUMENT = "-Xmx64M"
FABRIC_INSTALLER_EXACT_HEAP_ARGUMENT = "-Xmx1024M"
SCENARIO_PROPERTY_NAME = "etherology.e2e.scenario"
ARTIFACT_ROLES = ("production", "harness")
FATAL_CLIENT_LOG_MARKERS = (
    "Minecraft game provider couldn't locate the game",
    "Uncaught exception in thread \"main\"",
    "A mod crashed on startup!",
    "Could not find required mod",
)


class E2EError(RuntimeError):
    """Reports an isolated-profile configuration or lifecycle failure."""


@dataclass(frozen=True)
class ResolvedConfiguration:
    """Holds the release lane and manifest values resolved from repository sources."""

    manifest: dict[str, object]
    properties: dict[str, str]
    artifact_lane: dict[str, object]
    runtime_lane: dict[str, object]
    installer: dict[str, object]
    repository_root: Path


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


def safe_leaf_name(raw_value: object, field_name: str) -> str:
    if not isinstance(raw_value, str):
        raise E2EError(f"The manifest {field_name} field is invalid")
    path = Path(raw_value)
    if path.parts != (raw_value,) or raw_value in ("", ".", ".."):
        raise E2EError(f"The manifest {field_name} field is unsafe")
    return raw_value


def safe_repository_path(
    repository_root: Path, raw_value: object, field_name: str
) -> Path:
    if not isinstance(raw_value, str):
        raise E2EError(f"The manifest {field_name} field is invalid")
    relative_path = Path(raw_value)
    if relative_path.is_absolute() or not relative_path.parts or ".." in relative_path.parts:
        raise E2EError(f"The manifest {field_name} field is unsafe")
    root = repository_root.resolve()
    path = root / relative_path
    current_path = root
    for path_part in relative_path.parts:
        current_path /= path_part
        if current_path.is_symlink():
            raise E2EError(f"The manifest {field_name} resolves through a symlink")
    return path


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


def validate_hex_digest(raw_value: object, field_name: str) -> str:
    if not isinstance(raw_value, str) or re.fullmatch(r"[0-9a-f]{64}", raw_value) is None:
        raise E2EError(f"The {field_name} SHA-256 digest is invalid")
    return raw_value


def validate_manifest_shape(
    manifest: dict[str, object], properties: dict[str, str]
) -> None:
    if manifest.get("schema") != 2:
        raise E2EError("Unsupported E2E profile manifest schema")

    profile = require_object(manifest, "profile")
    profile_id = safe_leaf_name(profile.get("id"), "profile.id")
    if re.fullmatch(r"[a-z0-9][a-z0-9.-]+", profile_id) is None:
        raise E2EError("The manifest profile.id is not a stable lowercase identifier")
    safe_leaf_name(profile.get("runtime_directory"), "profile.runtime_directory")
    safe_leaf_name(profile.get("game_directory"), "profile.game_directory")
    safe_leaf_name(profile.get("launcher_directory"), "profile.launcher_directory")

    release = require_object(manifest, "release")
    safe_leaf_name(release.get("artifact_node"), "release.artifact_node")
    matrix_path = release.get("matrix")
    if matrix_path != "release/release-matrix.json":
        raise E2EError("The E2E lane must use release/release-matrix.json")

    launch = require_object(manifest, "launch")
    safe_leaf_name(launch.get("offline_username"), "launch.offline_username")
    resolution = require_object(launch, "resolution")
    for field_name in ("width", "height"):
        if type(resolution.get(field_name)) is not int or int(resolution[field_name]) <= 0:
            raise E2EError(f"The launch.resolution.{field_name} field is invalid")
    maximum_memory_mb = launch.get("maximum_memory_mb")
    if type(maximum_memory_mb) is not int or maximum_memory_mb != 4096:
        raise E2EError("The launch.maximum_memory_mb field must remain 4096")

    artifacts = require_object(manifest, "artifacts")
    if set(artifacts) != {"lock_file", "production", "harness"}:
        raise E2EError("The manifest artifacts object has unexpected fields")
    safe_leaf_name(artifacts.get("lock_file"), "artifacts.lock_file")
    production = require_object(artifacts, "production")
    harness = require_object(artifacts, "harness")
    if set(production) != {
        "file_name",
        "mod_id",
        "version_property",
        "source",
        "required_nested_mod_ids",
    }:
        raise E2EError("The production artifact object has unexpected fields")
    if set(harness) != {
        "file_name",
        "mod_id",
        "version_property",
        "client_entrypoint",
        "source",
    }:
        raise E2EError("The harness artifact object has unexpected fields")
    artifact_file_names: set[str] = set()
    artifact_mod_ids: set[str] = set()
    for role, artifact in (("production", production), ("harness", harness)):
        file_name = safe_leaf_name(
            artifact.get("file_name"), f"artifacts.{role}.file_name"
        )
        if not file_name.endswith(".jar") or file_name in artifact_file_names:
            raise E2EError(f"The {role} artifact file name is invalid or duplicated")
        artifact_file_names.add(file_name)
        mod_id = safe_leaf_name(
            artifact.get("mod_id"), f"artifacts.{role}.mod_id"
        )
        if re.fullmatch(r"[a-z][a-z0-9_-]{1,63}", mod_id) is None:
            raise E2EError(f"The {role} artifact mod id is invalid")
        if mod_id in artifact_mod_ids:
            raise E2EError(f"The {role} artifact mod id is duplicated")
        artifact_mod_ids.add(mod_id)
        version_property = safe_leaf_name(
            artifact.get("version_property"),
            f"artifacts.{role}.version_property",
        )
        if version_property not in properties:
            raise E2EError(
                f"The {role} artifact version property is missing: {version_property}"
            )

    production_source = require_object(production, "source")
    if production_source != {"kind": "release-matrix"}:
        raise E2EError("The production artifact source must be the release matrix")
    nested_mod_ids = require_list(production, "required_nested_mod_ids")
    if not nested_mod_ids or not all(
        isinstance(mod_id, str) and mod_id for mod_id in nested_mod_ids
    ) or len(set(nested_mod_ids)) != len(nested_mod_ids):
        raise E2EError(
            "The artifacts.production.required_nested_mod_ids list is invalid"
        )

    harness_entrypoint = harness.get("client_entrypoint")
    if (
        not isinstance(harness_entrypoint, str)
        or re.fullmatch(
            r"[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+",
            harness_entrypoint,
        )
        is None
    ):
        raise E2EError("The harness client entrypoint is invalid")
    harness_source = require_object(harness, "source")
    if harness_source.get("kind") != "gradle-remap-task" or set(harness_source) != {
        "kind",
        "build_file",
        "task_name",
    }:
        raise E2EError("The harness artifact source must name one Gradle remap task")
    build_file = harness_source.get("build_file")
    if build_file != "fabric/build.gradle.kts":
        raise E2EError("The harness artifact must resolve from fabric/build.gradle.kts")
    task_name = safe_leaf_name(
        harness_source.get("task_name"), "artifacts.harness.source.task_name"
    )
    if re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", task_name) is None:
        raise E2EError("The harness Gradle task name is invalid")

    evidence = require_object(manifest, "evidence")
    safe_leaf_name(evidence.get("directory"), "evidence.directory")
    capture = require_object(evidence, "capture")
    if set(capture) != {"kind", "width", "height"}:
        raise E2EError("The evidence.capture object has unexpected fields")
    if capture.get("kind") != "composed-minecraft-framebuffer":
        raise E2EError("The evidence.capture.kind field is invalid")
    for field_name in ("width", "height"):
        if type(capture.get(field_name)) is not int or int(capture[field_name]) <= 0:
            raise E2EError(f"The evidence.capture.{field_name} field is invalid")
    scenarios = require_list(evidence, "scenarios")
    scenarios_are_safe = all(
        isinstance(scenario, str)
        and re.fullmatch(r"[a-z0-9][a-z0-9-]*", scenario) is not None
        for scenario in scenarios
    )
    if (
        not scenarios
        or not scenarios_are_safe
        or len(set(scenarios)) != len(scenarios)
    ):
        raise E2EError("The evidence.scenarios list contains an unsafe id")

    profile_directories = require_list(manifest, "profile_directories")
    profile_directories_are_strings = all(
        isinstance(directory, str) for directory in profile_directories
    )
    if (
        not profile_directories
        or not profile_directories_are_strings
        or len(set(profile_directories)) != len(profile_directories)
    ):
        raise E2EError("The manifest profile_directories list is empty or duplicated")
    for directory in profile_directories:
        safe_leaf_name(directory, "profile_directories entry")

    raw_options = require_list(manifest, "options")
    if not raw_options or not all(
        isinstance(option, str) and "\n" not in option and "\r" not in option
        for option in raw_options
    ):
        raise E2EError("The manifest options list is invalid")
    parse_options("\n".join(str(option) for option in raw_options) + "\n")

    raw_dependencies = require_list(manifest, "dependencies")
    if not raw_dependencies:
        raise E2EError("The manifest dependencies list is empty")
    file_names: set[str] = set()
    root_mod_ids: set[str] = set()
    for raw_dependency in raw_dependencies:
        if not isinstance(raw_dependency, dict):
            raise E2EError("The manifest contains an invalid dependency entry")
        file_name = safe_leaf_name(raw_dependency.get("file_name"), "dependency.file_name")
        if not file_name.endswith(".jar") or file_name in file_names:
            raise E2EError(f"The dependency file name is invalid or duplicated: {file_name}")
        file_names.add(file_name)
        mod_id = safe_leaf_name(raw_dependency.get("mod_id"), "dependency.mod_id")
        if mod_id in root_mod_ids:
            raise E2EError(f"The dependency root mod id is duplicated: {mod_id}")
        root_mod_ids.add(mod_id)
        dependency_property = safe_leaf_name(
            raw_dependency.get("version_property"), "dependency.version_property"
        )
        if dependency_property not in properties:
            raise E2EError(f"The dependency version property is missing: {dependency_property}")
        expected_version = properties[dependency_property]
        url = raw_dependency.get("url")
        if not isinstance(url, str):
            raise E2EError(f"The dependency URL is invalid for {file_name}")
        parsed_url = urllib.parse.urlparse(url)
        if (
            parsed_url.scheme != "https"
            or not parsed_url.hostname
            or parsed_url.username is not None
            or parsed_url.password is not None
            or parsed_url.fragment
        ):
            raise E2EError(f"The dependency URL is unsafe for {file_name}")
        if expected_version not in file_name or expected_version not in url:
            raise E2EError(
                f"The dependency {file_name} does not encode "
                f"{dependency_property}={expected_version}"
            )
        size = raw_dependency.get("size")
        if type(size) is not int or int(size) <= 0 or int(size) > MAXIMUM_DOWNLOAD_SIZE:
            raise E2EError(f"The dependency size is invalid for {file_name}")
        validate_hex_digest(raw_dependency.get("sha256"), f"dependency {file_name}")

    forbidden_mod_ids = require_list(manifest, "forbidden_mod_ids")
    if not all(isinstance(mod_id, str) and mod_id for mod_id in forbidden_mod_ids):
        raise E2EError("The manifest forbidden_mod_ids list is invalid")
    if len(set(forbidden_mod_ids)) != len(forbidden_mod_ids):
        raise E2EError("The manifest forbidden_mod_ids list contains duplicates")
    duplicate_root_mod_ids = root_mod_ids.intersection(artifact_mod_ids)
    if duplicate_root_mod_ids:
        raise E2EError(
            f"Dependency and local artifact mod ids overlap: {duplicate_root_mod_ids}"
        )
    impossible_exclusions = (root_mod_ids | artifact_mod_ids).intersection(
        str(value) for value in forbidden_mod_ids
    )
    if impossible_exclusions:
        raise E2EError(f"Required dependencies are also forbidden: {impossible_exclusions}")


def load_configuration(
    manifest_path: Path = MANIFEST_PATH,
    repository_root: Path = REPOSITORY_ROOT,
) -> ResolvedConfiguration:
    manifest = load_json_object(manifest_path, "E2E profile manifest")
    properties = parse_gradle_properties(repository_root / "gradle.properties")
    validate_manifest_shape(manifest, properties)

    release = require_object(manifest, "release")
    matrix_path = safe_repository_path(
        repository_root, release.get("matrix"), "release.matrix"
    )
    matrix = load_json_object(matrix_path, "release matrix")
    if matrix.get("schema_version") != 1:
        raise E2EError("Unsupported release matrix schema")
    artifact_node = str(release["artifact_node"])
    artifact_lane = find_unique_row(
        matrix.get("artifacts"), "artifact_node", artifact_node, "artifact"
    )
    runtime_lane = find_unique_row(
        matrix.get("runtimes"), "artifact_node", artifact_node, "runtime"
    )
    if artifact_lane.get("loader") != "fabric" or runtime_lane.get("loader") != "fabric":
        raise E2EError("The E2E profile is restricted to the Fabric release lane")
    if artifact_lane.get("artifact_version") != runtime_lane.get("runtime_version"):
        raise E2EError("The Fabric artifact and runtime Minecraft versions differ")
    if artifact_lane.get("java") != 17 or runtime_lane.get("java") != 17:
        raise E2EError("The Fabric 1.20.1 E2E profile requires Java 17")
    if runtime_lane.get("runtime_version") != properties.get("minecraft_version_1_20_1"):
        raise E2EError("The release runtime differs from minecraft_version_1_20_1")
    if runtime_lane.get("loader_version") != properties.get(
        "fabric_loader_version_1_20_1"
    ):
        raise E2EError("The release runtime differs from fabric_loader_version_1_20_1")
    if runtime_lane.get("jar_sha256") != "from:artifact-manifest":
        raise E2EError("The release runtime must acquire its JAR hash from artifact staging")
    if runtime_lane.get("fabric_api") != properties.get("fabric_api_version_1_20_1"):
        raise E2EError("The release runtime differs from fabric_api_version_1_20_1")
    architectury = runtime_lane.get("architectury")
    if not isinstance(architectury, dict) or architectury.get("version") != properties.get(
        "architectury_api_version_1_20_1"
    ):
        raise E2EError("The release runtime differs from architectury_api_version_1_20_1")
    project = matrix.get("project")
    tracked_production = artifact_spec_from_manifest(manifest, "production")
    tracked_harness = artifact_spec_from_manifest(manifest, "harness")
    if not isinstance(project, dict) or project.get(
        "mod_version_property"
    ) != tracked_production.get("version_property"):
        raise E2EError("The artifact version property differs from the release matrix")
    if project.get("mod_id") != tracked_production.get("mod_id"):
        raise E2EError("The artifact mod id differs from the release matrix")
    if tracked_harness.get("version_property") != tracked_production.get(
        "version_property"
    ):
        raise E2EError("The production and harness version properties differ")
    installer_key = runtime_lane.get("installer")
    installers = matrix.get("installers")
    if not isinstance(installer_key, str) or not isinstance(installers, dict):
        raise E2EError("The release runtime installer reference is invalid")
    installer = installers.get(installer_key)
    if not isinstance(installer, dict):
        raise E2EError(f"The release installer is missing: {installer_key}")
    installer_url = installer.get("url")
    if not isinstance(installer_url, str) or urllib.parse.urlparse(installer_url).scheme != "https":
        raise E2EError("The release installer URL is unsafe")
    validate_hex_digest(installer.get("sha256"), "release installer")

    fabric_metadata_template = load_json_object(
        repository_root / "src/main/resources/fabric.mod.json",
        "Fabric metadata template",
    )
    raw_fabric_dependencies = fabric_metadata_template.get("depends")
    if not isinstance(raw_fabric_dependencies, dict):
        raise E2EError("The Fabric metadata template dependency table is invalid")
    platform_mod_ids = {"fabricloader", "minecraft", "java"}
    nested_mod_ids = {
        str(value)
        for value in require_list(tracked_production, "required_nested_mod_ids")
    }
    expected_root_mod_ids = (
        set(raw_fabric_dependencies) - platform_mod_ids - nested_mod_ids
    )
    declared_root_mod_ids = {
        str(dependency["mod_id"])
        for dependency in require_list(manifest, "dependencies")
        if isinstance(dependency, dict)
    }
    if declared_root_mod_ids != expected_root_mod_ids:
        missing_mod_ids = sorted(expected_root_mod_ids - declared_root_mod_ids)
        extra_mod_ids = sorted(declared_root_mod_ids - expected_root_mod_ids)
        raise E2EError(
            "The pinned dependency inventory differs from fabric.mod.json: "
            f"missing={missing_mod_ids}, extra={extra_mod_ids}"
        )

    configuration = ResolvedConfiguration(
        manifest=manifest,
        properties=properties,
        artifact_lane=artifact_lane,
        runtime_lane=runtime_lane,
        installer=installer,
        repository_root=repository_root.resolve(),
    )
    source_paths = {
        role: artifact_source_path(configuration, role) for role in ARTIFACT_ROLES
    }
    if len(set(source_paths.values())) != len(ARTIFACT_ROLES):
        raise E2EError("The production and harness tasks resolve to the same JAR")
    return configuration


def profile_spec(configuration: ResolvedConfiguration) -> dict[str, object]:
    return require_object(configuration.manifest, "profile")


def artifacts_spec(configuration: ResolvedConfiguration) -> dict[str, object]:
    return require_object(configuration.manifest, "artifacts")


def artifact_spec_from_manifest(
    manifest: dict[str, object], role: str
) -> dict[str, object]:
    if role not in ARTIFACT_ROLES:
        raise E2EError(f"Unsupported artifact role: {role}")
    return require_object(require_object(manifest, "artifacts"), role)


def artifact_spec(
    configuration: ResolvedConfiguration, role: str
) -> dict[str, object]:
    return artifact_spec_from_manifest(configuration.manifest, role)


def artifact_specs(
    configuration: ResolvedConfiguration,
) -> list[tuple[str, dict[str, object]]]:
    return [(role, artifact_spec(configuration, role)) for role in ARTIFACT_ROLES]


def evidence_spec(configuration: ResolvedConfiguration) -> dict[str, object]:
    return require_object(configuration.manifest, "evidence")


def scenario_ids(configuration: ResolvedConfiguration) -> list[str]:
    return [
        str(value)
        for value in require_list(evidence_spec(configuration), "scenarios")
    ]


def resolve_scenario_id(
    configuration: ResolvedConfiguration, configured_scenario_id: str | None
) -> str:
    scenarios = scenario_ids(configuration)
    scenario_id = scenarios[0] if configured_scenario_id is None else configured_scenario_id
    if scenario_id in scenarios:
        return scenario_id
    raise E2EError(
        f"Unsupported E2E scenario {scenario_id!r}; expected one of {scenarios}"
    )


def dependency_specs(configuration: ResolvedConfiguration) -> list[dict[str, object]]:
    raw_dependencies = require_list(configuration.manifest, "dependencies")
    return [value for value in raw_dependencies if isinstance(value, dict)]


def profile_directories(configuration: ResolvedConfiguration) -> list[str]:
    return [
        str(value)
        for value in require_list(configuration.manifest, "profile_directories")
    ]


def runtime_root(
    configuration: ResolvedConfiguration, state_root: Path = STATE_ROOT
) -> Path:
    runtime_directory = str(profile_spec(configuration)["runtime_directory"])
    return state_root / "runtimes" / runtime_directory


def game_directory(configuration: ResolvedConfiguration, root: Path | None = None) -> Path:
    profile_root = root or runtime_root(configuration)
    return profile_root / str(profile_spec(configuration)["game_directory"])


def launcher_directory(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> Path:
    profile_root = root or runtime_root(configuration)
    return profile_root / str(profile_spec(configuration)["launcher_directory"])


def evidence_root(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> Path:
    profile_root = root or runtime_root(configuration)
    return profile_root / str(evidence_spec(configuration)["directory"])


def process_state_path(
    configuration: ResolvedConfiguration, state_root: Path = STATE_ROOT
) -> Path:
    profile_id = str(profile_spec(configuration)["id"])
    return state_root / f"{profile_id}-current.json"


def start_attempt_path(
    configuration: ResolvedConfiguration, state_root: Path = STATE_ROOT
) -> Path:
    profile_id = str(profile_spec(configuration)["id"])
    return state_root / f"{profile_id}-start.attempted"


def ensure_directory_is_not_linked(path: Path, description: str) -> None:
    if path.is_symlink():
        raise E2EError(f"{description} must not be a symlink: {path}")
    if path.exists() and not path.is_dir():
        raise E2EError(f"{description} must be a directory: {path}")


def ensure_owned_state_roots(state_root: Path = STATE_ROOT) -> None:
    ensure_directory_is_not_linked(state_root, "E2E state root")
    ensure_directory_is_not_linked(state_root / "runtimes", "E2E runtimes root")


def require_unattempted_profile(
    configuration: ResolvedConfiguration, state_root: Path = STATE_ROOT
) -> None:
    attempt_path = start_attempt_path(configuration, state_root)
    if attempt_path.exists() or attempt_path.is_symlink():
        raise E2EError(
            "The Fabric client profile already has a start attempt and is consumed: "
            f"{attempt_path}"
        )


def profile_descriptor(configuration: ResolvedConfiguration) -> dict[str, object]:
    dependencies = [
        {
            "file_name": dependency["file_name"],
            "mod_id": dependency["mod_id"],
            "sha256": dependency["sha256"],
            "size": dependency["size"],
            "url": dependency["url"],
            "version_property": dependency["version_property"],
        }
        for dependency in dependency_specs(configuration)
    ]
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "managed_by": "scripts/e2e/client.py",
        "isolation": {
            "scope": "repository-owned-ignored-state",
            "source_profiles": [],
        },
        "release": {
            "artifact_node": configuration.artifact_lane["artifact_node"],
            "minecraft_version": configuration.runtime_lane["runtime_version"],
            "loader": configuration.runtime_lane["loader"],
            "loader_version": configuration.runtime_lane["loader_version"],
            "java": configuration.runtime_lane["java"],
        },
        "dependencies": dependencies,
    }


def evidence_descriptor(configuration: ResolvedConfiguration) -> dict[str, object]:
    capture = require_object(evidence_spec(configuration), "capture")
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "artifact_node": configuration.artifact_lane["artifact_node"],
        "scenarios": scenario_ids(configuration),
        "capture": {
            "kind": capture["kind"],
            "width": capture["width"],
            "height": capture["height"],
        },
    }


def options_text(configuration: ResolvedConfiguration) -> str:
    options = require_list(configuration.manifest, "options")
    return "\n".join(str(option) for option in options) + "\n"


def parse_options(content: str) -> dict[str, str]:
    options: dict[str, str] = {}
    for line in content.splitlines():
        name, separator, value = line.partition(":")
        if not separator or not name or name in options:
            raise E2EError("The isolated profile options file is invalid")
        options[name] = value
    return options


def write_private_text_exclusive(path: Path, content: str) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        handle.write(content)


def write_bytes_exclusive(path: Path, content: bytes) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as handle:
        handle.write(content)
        handle.flush()
        os.fsync(handle.fileno())
    directory_descriptor = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory_descriptor)
    finally:
        os.close(directory_descriptor)


def reserve_start_attempt(
    configuration: ResolvedConfiguration,
    scenario_id: str,
    state_root: Path = STATE_ROOT,
) -> Path:
    resolved_scenario_id = resolve_scenario_id(configuration, scenario_id)
    attempt_path = start_attempt_path(configuration, state_root)
    attempt_content = (
        f"profile_id={profile_spec(configuration)['id']}\n"
        f"scenario={resolved_scenario_id}\n"
        f"pid={os.getpid()}\n"
    ).encode("utf-8")
    try:
        write_bytes_exclusive(attempt_path, attempt_content)
    except FileExistsError as exception:
        raise E2EError(
            "The Fabric client profile already has a start attempt and is consumed: "
            f"{attempt_path}"
        ) from exception
    except OSError as exception:
        raise E2EError(
            f"Cannot durably record the Fabric client start attempt: {exception}"
        ) from exception
    return attempt_path


def write_json_atomic(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary_path = path.parent / f".{path.name}.{os.getpid()}.tmp"
    descriptor = os.open(
        temporary_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(value, handle, indent=2, sort_keys=True)
            handle.write("\n")
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_exact_file(
    path: Path, expected_sha256: str, expected_size: int | None, description: str
) -> None:
    if not path.is_file() or path.is_symlink():
        raise E2EError(f"{description} is missing or linked: {path}")
    if expected_size is not None and path.stat().st_size != expected_size:
        raise E2EError(f"{description} has an unexpected size: {path}")
    if sha256_file(path) != expected_sha256:
        raise E2EError(f"{description} failed SHA-256 validation: {path}")


def read_archive_metadata(
    archive: zipfile.ZipFile, source_name: str, depth: int = 0
) -> list[dict[str, object]]:
    if depth > MAXIMUM_NESTED_JAR_DEPTH:
        raise E2EError(f"Fabric nested-JAR depth is excessive in {source_name}")
    try:
        metadata = json.loads(archive.read("fabric.mod.json"))
    except (KeyError, json.JSONDecodeError) as exception:
        raise E2EError(f"Fabric metadata is missing or invalid in {source_name}") from exception
    if not isinstance(metadata, dict):
        raise E2EError(f"Fabric metadata is not an object in {source_name}")
    if not isinstance(metadata.get("id"), str) or not isinstance(
        metadata.get("version"), str
    ):
        raise E2EError(f"Fabric metadata has no id or version in {source_name}")
    collected = [metadata]
    raw_nested_jars = metadata.get("jars", [])
    if not isinstance(raw_nested_jars, list):
        raise E2EError(f"Fabric metadata has an invalid jars list in {source_name}")
    for raw_entry in raw_nested_jars:
        if not isinstance(raw_entry, dict) or not isinstance(raw_entry.get("file"), str):
            raise E2EError(f"Fabric metadata has an invalid nested JAR in {source_name}")
        nested_name = str(raw_entry["file"])
        nested_path = PurePosixPath(nested_name)
        if nested_path.is_absolute() or ".." in nested_path.parts:
            raise E2EError(f"Fabric metadata has an unsafe nested JAR in {source_name}")
        try:
            nested_info = archive.getinfo(nested_name)
            if nested_info.file_size > MAXIMUM_NESTED_JAR_SIZE:
                raise E2EError(f"Nested Fabric JAR is too large in {source_name}")
            nested_bytes = archive.read(nested_info)
            with zipfile.ZipFile(io.BytesIO(nested_bytes)) as nested_archive:
                collected.extend(
                    read_archive_metadata(
                        nested_archive, f"{source_name}!/{nested_name}", depth + 1
                    )
                )
        except (KeyError, zipfile.BadZipFile) as exception:
            raise E2EError(
                f"Fabric nested JAR is missing or unreadable in {source_name}: {nested_name}"
            ) from exception
    return collected


def fabric_metadata(path: Path) -> list[dict[str, object]]:
    try:
        with zipfile.ZipFile(path) as archive:
            return read_archive_metadata(archive, str(path))
    except (OSError, zipfile.BadZipFile) as exception:
        raise E2EError(f"Cannot inspect Fabric mod JAR {path}: {exception}") from exception


def verify_dependency_jar(
    configuration: ResolvedConfiguration,
    path: Path,
    dependency: dict[str, object],
) -> set[str]:
    expected_sha256 = str(dependency["sha256"])
    expected_size = int(dependency["size"])
    verify_exact_file(path, expected_sha256, expected_size, "Pinned dependency")
    metadata = fabric_metadata(path)
    root_metadata = metadata[0]
    if root_metadata["id"] != dependency["mod_id"]:
        raise E2EError(
            f"Pinned dependency has mod id {root_metadata['id']}, expected {dependency['mod_id']}"
        )
    expected_version = configuration.properties[str(dependency["version_property"])]
    if root_metadata["version"] != expected_version:
        raise E2EError(
            f"Pinned dependency {dependency['mod_id']} has version "
            f"{root_metadata['version']}, expected {expected_version}"
        )
    return {str(value["id"]) for value in metadata}


def copy_response(
    response: BinaryIO, handle: BinaryIO, maximum_size: int
) -> int:
    total_size = 0
    while True:
        chunk = response.read(1024 * 1024)
        if not chunk:
            return total_size
        total_size += len(chunk)
        if total_size > maximum_size:
            raise E2EError("Download exceeded its declared maximum size")
        handle.write(chunk)


def download_pinned_file(
    url: str,
    destination: Path,
    expected_sha256: str,
    expected_size: int | None,
    description: str,
) -> None:
    if destination.exists() or destination.is_symlink():
        raise E2EError(f"Refusing to replace an existing download target: {destination}")
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Etherology-Revived-E2E/1"},
    )
    maximum_size = expected_size if expected_size is not None else MAXIMUM_DOWNLOAD_SIZE
    descriptor = os.open(
        destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600
    )
    try:
        with os.fdopen(descriptor, "wb") as handle:
            try:
                with urllib.request.urlopen(request, timeout=120) as response:
                    actual_size = copy_response(response, handle, maximum_size)
            except (OSError, urllib.error.URLError) as exception:
                raise E2EError(f"Cannot download {description}: {exception}") from exception
        if expected_size is not None and actual_size != expected_size:
            raise E2EError(
                f"Downloaded {description} has size {actual_size}, expected {expected_size}"
            )
        verify_exact_file(destination, expected_sha256, expected_size, description)
    except Exception:
        destination.unlink(missing_ok=True)
        raise


def verify_launcher_library() -> None:
    local_python_root = STATE_ROOT / "python"
    if local_python_root.is_symlink():
        raise E2EError(
            f"Local E2E Python dependency root must not be a symlink: {local_python_root}"
        )
    if local_python_root.is_dir() and str(local_python_root) not in sys.path:
        sys.path.insert(0, str(local_python_root))
    try:
        actual_version = importlib.metadata.version("minecraft-launcher-lib")
    except importlib.metadata.PackageNotFoundError as exception:
        raise E2EError(
            "minecraft-launcher-lib is missing; install scripts/e2e/requirements.txt"
        ) from exception
    if actual_version != EXPECTED_LAUNCHER_LIBRARY_VERSION:
        raise E2EError(
            "minecraft-launcher-lib must be exactly "
            f"{EXPECTED_LAUNCHER_LIBRARY_VERSION}, found {actual_version}"
        )


def java_major_version(java_path: Path) -> int | None:
    if not java_path.is_file() or not os.access(java_path, os.X_OK):
        return None
    try:
        completed = subprocess.run(
            [
                str(java_path),
                JAVA_VERSION_PROBE_EXACT_HEAP_ARGUMENT,
                "-XshowSettings:properties",
                "-version",
            ],
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


def resolve_java_17() -> Path:
    try:
        verify_java_option_environment(os.environ)
    except GuardedJavaError as exception:
        raise E2EError(str(exception)) from exception
    candidates: list[Path] = []
    override = os.environ.get(JAVA_OVERRIDE_ENVIRONMENT_VARIABLE)
    if override:
        candidates.append(Path(override))
    java_home_tool = Path("/usr/libexec/java_home")
    if java_home_tool.is_file():
        completed = subprocess.run(
            [str(java_home_tool), "-v", "17"],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=15,
            check=False,
        )
        if completed.returncode == 0 and completed.stdout.strip():
            candidates.append(Path(completed.stdout.strip()) / "bin" / "java")
    gradle_jdks = Path.home() / ".gradle" / "jdks"
    if gradle_jdks.is_dir() and not gradle_jdks.is_symlink():
        candidates.extend(sorted(gradle_jdks.glob("**/Contents/Home/bin/java")))

    checked: set[Path] = set()
    for candidate in candidates:
        resolved_candidate = candidate.resolve(strict=False)
        if resolved_candidate in checked:
            continue
        checked.add(resolved_candidate)
        if java_major_version(resolved_candidate) == 17:
            return resolved_candidate
    raise E2EError(
        "No Java 17 runtime was found outside game profiles; set "
        f"{JAVA_OVERRIDE_ENVIRONMENT_VARIABLE} to a Java 17 executable"
    )


def version_id(configuration: ResolvedConfiguration) -> str:
    loader_version = configuration.runtime_lane["loader_version"]
    minecraft_version = configuration.runtime_lane["runtime_version"]
    return f"fabric-loader-{loader_version}-{minecraft_version}"


def installer_file_name(configuration: ResolvedConfiguration) -> str:
    url = str(configuration.installer["url"])
    name = PurePosixPath(urllib.parse.urlparse(url).path).name
    return safe_leaf_name(name, "release installer file name")


def install_isolated_game(
    configuration: ResolvedConfiguration,
    root: Path,
    java_path: Path,
) -> None:
    verify_launcher_library()
    try:
        import minecraft_launcher_lib.install
    except ImportError as exception:
        raise E2EError("minecraft-launcher-lib could not be imported") from exception

    launcher_root = launcher_directory(configuration, root)
    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    try:
        minecraft_launcher_lib.install.install_minecraft_version(
            minecraft_version,
            str(launcher_root),
        )
    except Exception as exception:
        raise E2EError(
            f"Cannot install Minecraft {minecraft_version} in the isolated launcher root: "
            f"{exception}"
        ) from exception

    installer_path = root / "installer" / installer_file_name(configuration)
    loader_version = str(configuration.runtime_lane["loader_version"])
    command = [
        str(java_path),
        FABRIC_INSTALLER_EXACT_HEAP_ARGUMENT,
        "-jar",
        str(installer_path),
        "client",
        "-dir",
        str(launcher_root),
        "-mcversion",
        minecraft_version,
        "-loader",
        loader_version,
        "-noprofile",
    ]
    try:
        completed = subprocess.run(
            command,
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=600,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exception:
        raise E2EError(f"Fabric installer did not complete: {exception}") from exception
    if completed.returncode != 0:
        output_tail = "\n".join(completed.stdout.splitlines()[-30:])
        raise E2EError(
            f"Fabric installer exited with {completed.returncode}:\n{output_tail}"
        )


def fabric_version_metadata_path(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> Path:
    fabric_version = version_id(configuration)
    return (
        launcher_directory(configuration, root)
        / "versions"
        / fabric_version
        / f"{fabric_version}.json"
    )


def normalize_fabric_version_metadata(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> bool:
    """Point launcher-library inheritance at the installed vanilla client JAR."""
    launcher_root = launcher_directory(configuration, root)
    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    minecraft_jar = (
        launcher_root
        / "versions"
        / minecraft_version
        / f"{minecraft_version}.jar"
    )
    if not minecraft_jar.is_file() or minecraft_jar.is_symlink():
        raise E2EError(f"Minecraft client JAR is missing or linked: {minecraft_jar}")

    metadata_path = fabric_version_metadata_path(configuration, root)
    if not metadata_path.is_file() or metadata_path.is_symlink():
        raise E2EError(f"Fabric version metadata is missing or linked: {metadata_path}")
    metadata = load_json_object(metadata_path, "Fabric version metadata")
    fabric_version = version_id(configuration)
    if metadata.get("id") != fabric_version:
        raise E2EError(f"Fabric version metadata has the wrong id: {metadata_path}")
    if metadata.get("inheritsFrom") != minecraft_version:
        raise E2EError("Fabric version metadata inherits the wrong Minecraft version")

    inherited_jar = metadata.get("jar")
    if inherited_jar is None:
        normalized_metadata = dict(metadata)
        normalized_metadata["jar"] = minecraft_version
        write_json_atomic(metadata_path, normalized_metadata)
        return True
    if inherited_jar != minecraft_version:
        raise E2EError(
            "Fabric version metadata points at an unexpected inherited client JAR"
        )
    return False


def verify_installed_game(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> None:
    launcher_root = launcher_directory(configuration, root)
    if not launcher_root.is_dir() or launcher_root.is_symlink():
        raise E2EError(f"Isolated launcher root is missing or linked: {launcher_root}")
    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    minecraft_version_root = launcher_root / "versions" / minecraft_version
    minecraft_json = minecraft_version_root / f"{minecraft_version}.json"
    minecraft_jar = minecraft_version_root / f"{minecraft_version}.jar"
    if not minecraft_json.is_file() or minecraft_json.is_symlink():
        raise E2EError(f"Minecraft version metadata is missing or linked: {minecraft_json}")
    minecraft_metadata = load_json_object(minecraft_json, "Minecraft version metadata")
    if minecraft_metadata.get("id") != minecraft_version:
        raise E2EError(f"Minecraft version metadata has the wrong id: {minecraft_json}")
    if not minecraft_jar.is_file() or minecraft_jar.is_symlink():
        raise E2EError(f"Minecraft client JAR is missing or linked: {minecraft_jar}")

    fabric_version = version_id(configuration)
    fabric_json = fabric_version_metadata_path(configuration, root)
    if not fabric_json.is_file() or fabric_json.is_symlink():
        raise E2EError(f"Fabric version metadata is missing or linked: {fabric_json}")
    fabric_metadata_value = load_json_object(fabric_json, "Fabric version metadata")
    if fabric_metadata_value.get("id") != fabric_version:
        raise E2EError(f"Fabric version metadata has the wrong id: {fabric_json}")
    if fabric_metadata_value.get("inheritsFrom") != minecraft_version:
        raise E2EError(f"Fabric version metadata inherits the wrong Minecraft version")
    if fabric_metadata_value.get("jar") != minecraft_version:
        raise E2EError(
            "Fabric version metadata does not select the installed vanilla client JAR"
        )
    main_class = fabric_metadata_value.get("mainClass")
    if main_class != "net.fabricmc.loader.impl.launch.knot.KnotClient":
        raise E2EError(f"Fabric version metadata has an unexpected client main class")


def verify_profile_marker(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> None:
    target_root = root or runtime_root(configuration)
    if not target_root.is_dir() or target_root.is_symlink():
        raise E2EError(f"Isolated E2E runtime is missing or linked: {target_root}")
    marker_path = target_root / PROFILE_MARKER_NAME
    if not marker_path.is_file() or marker_path.is_symlink():
        raise E2EError(
            "Refusing to adopt an unmarked or linked runtime directory: "
            f"{target_root}"
        )
    actual_marker = load_json_object(marker_path, "E2E profile marker")
    if actual_marker != profile_descriptor(configuration):
        raise E2EError(
            "The existing runtime marker does not match this exact Etherology profile: "
            f"{marker_path}"
        )


def verify_evidence_layout(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> None:
    target_root = root or runtime_root(configuration)
    verify_profile_marker(configuration, target_root)
    target_evidence_root = evidence_root(configuration, target_root)
    if not target_evidence_root.is_dir() or target_evidence_root.is_symlink():
        raise E2EError(
            f"Isolated E2E evidence root is missing or linked: {target_evidence_root}"
        )
    marker_path = target_evidence_root / EVIDENCE_MARKER_NAME
    if not marker_path.is_file() or marker_path.is_symlink():
        raise E2EError(
            f"Refusing to adopt unmarked or linked E2E evidence: {target_evidence_root}"
        )
    marker = load_json_object(marker_path, "E2E evidence marker")
    if marker != evidence_descriptor(configuration):
        raise E2EError(f"E2E evidence marker does not match: {marker_path}")

    scenarios = scenario_ids(configuration)
    expected_entries = {EVIDENCE_MARKER_NAME, *scenarios}
    actual_entries = {entry.name for entry in target_evidence_root.iterdir()}
    if actual_entries != expected_entries:
        raise E2EError(
            "E2E evidence scenario inventory changed: "
            f"missing={sorted(expected_entries - actual_entries)}, "
            f"unexpected={sorted(actual_entries - expected_entries)}"
        )
    for scenario_id in scenarios:
        scenario_root = target_evidence_root / scenario_id
        if not scenario_root.is_dir() or scenario_root.is_symlink():
            raise E2EError(f"E2E scenario directory is missing or linked: {scenario_root}")
        expected_scenario_entries = {"reports", "screenshots"}
        actual_scenario_entries = {entry.name for entry in scenario_root.iterdir()}
        if actual_scenario_entries != expected_scenario_entries:
            raise E2EError(
                f"E2E scenario layout changed for {scenario_id}: "
                f"missing={sorted(expected_scenario_entries - actual_scenario_entries)}, "
                f"unexpected={sorted(actual_scenario_entries - expected_scenario_entries)}"
            )
        for directory_name in expected_scenario_entries:
            directory = scenario_root / directory_name
            if not directory.is_dir() or directory.is_symlink():
                raise E2EError(
                    f"E2E scenario directory is missing or linked: {directory}"
                )


def ensure_evidence_layout(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> bool:
    target_root = root or runtime_root(configuration)
    verify_profile_marker(configuration, target_root)
    target_evidence_root = evidence_root(configuration, target_root)
    if target_evidence_root.exists() or target_evidence_root.is_symlink():
        verify_evidence_layout(configuration, target_root)
        return False

    staging_root = Path(
        tempfile.mkdtemp(
            prefix=f".{evidence_spec(configuration)['directory']}.",
            dir=target_root,
        )
    )
    try:
        write_private_text_exclusive(
            staging_root / EVIDENCE_MARKER_NAME,
            json.dumps(evidence_descriptor(configuration), indent=2, sort_keys=True)
            + "\n",
        )
        for scenario_id in scenario_ids(configuration):
            scenario_root = staging_root / scenario_id
            scenario_root.mkdir(mode=0o700)
            (scenario_root / "reports").mkdir(mode=0o700)
            (scenario_root / "screenshots").mkdir(mode=0o700)
        os.replace(staging_root, target_evidence_root)
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root)
    verify_evidence_layout(configuration, target_root)
    return True


def production_artifact_source_path(configuration: ResolvedConfiguration) -> Path:
    raw_template = configuration.artifact_lane.get("jar")
    if not isinstance(raw_template, str):
        raise E2EError("The release artifact path template is invalid")
    production = artifact_spec(configuration, "production")
    version_property = str(production["version_property"])
    try:
        raw_path = raw_template.format(
            mod_version=configuration.properties[version_property]
        )
    except (KeyError, ValueError) as exception:
        raise E2EError(
            f"Cannot resolve the release artifact path: {exception}"
        ) from exception
    return safe_repository_path(
        configuration.repository_root, raw_path, "production artifact JAR"
    )


def unique_gradle_string_setting(body: str, setting: str, task_name: str) -> str:
    matches = re.findall(
        rf'(?m)^\s*{re.escape(setting)}\.set\("([^"\r\n]*)"\)\s*$', body
    )
    if len(matches) != 1:
        raise E2EError(
            f"Gradle task {task_name} must set {setting} to one literal string"
        )
    return matches[0]


def gradle_remap_task_body(
    build_file: Path, task_name: str, version_property: str
) -> str:
    try:
        content = build_file.read_text(encoding="utf-8")
    except OSError as exception:
        raise E2EError(f"Cannot read harness build configuration {build_file}") from exception
    pattern = re.compile(
        rf'tasks\.register<RemapJarTask>\("{re.escape(task_name)}"\)\s*\{{'
        rf'(?P<body>.*?)\n    \}}',
        re.DOTALL,
    )
    matches = [match.group("body") for match in pattern.finditer(content)]
    if len(matches) != 1:
        raise E2EError(
            f"Expected one RemapJarTask named {task_name} in {build_file}"
        )
    version_assignments = re.findall(
        r'(?m)^version = rootProject\.property\("([^"\r\n]+)"\) as String\s*$',
        content,
    )
    if version_assignments != [version_property]:
        raise E2EError(
            "The harness build must derive project.version from its manifest version property"
        )
    minecraft_assignments = re.findall(
        r"(?m)^val minecraftVersion = stonecutter\.current\.version\s*$", content
    )
    if len(minecraft_assignments) != 1:
        raise E2EError("The harness build must derive Minecraft from the Stonecutter node")
    return matches[0]


def harness_artifact_source_path(configuration: ResolvedConfiguration) -> Path:
    harness = artifact_spec(configuration, "harness")
    source = require_object(harness, "source")
    build_file = safe_repository_path(
        configuration.repository_root,
        source.get("build_file"),
        "harness source build_file",
    )
    task_name = str(source["task_name"])
    body = gradle_remap_task_body(
        build_file, task_name, str(harness["version_property"])
    )
    archive_base_name = unique_gradle_string_setting(
        body, "archiveBaseName", task_name
    ).replace("$minecraftVersion", str(configuration.runtime_lane["runtime_version"]))
    archive_classifier = unique_gradle_string_setting(
        body, "archiveClassifier", task_name
    )
    destination_matches = re.findall(
        r'(?m)^\s*destinationDirectory\.set\('
        r'layout\.buildDirectory\.dir\("([^"\r\n]+)"\)\)\s*$',
        body,
    )
    if len(destination_matches) != 1:
        raise E2EError(
            f"Gradle task {task_name} must use one build-relative destination"
        )
    destination = destination_matches[0]
    if len(
        re.findall(
            r"(?m)^\s*archiveVersion\.set\(project\.version\.toString\(\)\)\s*$",
            body,
        )
    ) != 1:
        raise E2EError(
            f"Gradle task {task_name} must derive archiveVersion from project.version"
        )
    if (
        not archive_base_name
        or "$" in archive_base_name
        or "{" in archive_base_name
        or "}" in archive_base_name
        or re.fullmatch(r"[A-Za-z0-9 ._+\-]+", archive_base_name) is None
    ):
        raise E2EError(f"Gradle task {task_name} has an unsafe archiveBaseName")
    if re.fullmatch(r"[A-Za-z0-9._+\-]*", archive_classifier) is None:
        raise E2EError(f"Gradle task {task_name} has an unsafe archiveClassifier")
    destination_path = Path(destination)
    if (
        destination_path.is_absolute()
        or not destination_path.parts
        or ".." in destination_path.parts
    ):
        raise E2EError(f"Gradle task {task_name} has an unsafe destinationDirectory")

    production_source = production_artifact_source_path(configuration)
    if production_source.parent.name != "libs":
        raise E2EError("The release matrix artifact is not in a Gradle libs directory")
    build_root = production_source.parent.parent
    expected_build_root = (
        build_file.parent
        / "versions"
        / str(configuration.runtime_lane["runtime_version"])
        / "build"
    )
    if build_root != expected_build_root:
        raise E2EError(
            "The release matrix and harness build configuration resolve different build roots"
        )
    archive_version = configuration.properties[str(harness["version_property"])]
    classifier_suffix = f"-{archive_classifier}" if archive_classifier else ""
    file_name = f"{archive_base_name}-{archive_version}{classifier_suffix}.jar"
    relative_output = (build_root / destination_path / file_name).relative_to(
        configuration.repository_root
    )
    return safe_repository_path(
        configuration.repository_root,
        relative_output.as_posix(),
        "harness artifact JAR",
    )


def artifact_source_path(configuration: ResolvedConfiguration, role: str) -> Path:
    source_kind = require_object(artifact_spec(configuration, role), "source").get(
        "kind"
    )
    if role == "production" and source_kind == "release-matrix":
        return production_artifact_source_path(configuration)
    if role == "harness" and source_kind == "gradle-remap-task":
        return harness_artifact_source_path(configuration)
    raise E2EError(f"Unsupported {role} artifact source: {source_kind}")


def artifact_source_relative_path(
    configuration: ResolvedConfiguration, role: str
) -> str:
    return artifact_source_path(configuration, role).relative_to(
        configuration.repository_root
    ).as_posix()


def artifact_lock_path(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> Path:
    target_root = root or runtime_root(configuration)
    return target_root / str(artifacts_spec(configuration)["lock_file"])


def artifact_target_path(
    configuration: ResolvedConfiguration,
    role: str,
    root: Path | None = None,
) -> Path:
    return game_directory(configuration, root) / "mods" / str(
        artifact_spec(configuration, role)["file_name"]
    )


def production_class_prefix(configuration: ResolvedConfiguration) -> str:
    metadata_template = load_json_object(
        configuration.repository_root / "src/main/resources/fabric.mod.json",
        "Fabric metadata template",
    )
    entrypoints = metadata_template.get("entrypoints")
    if not isinstance(entrypoints, dict):
        raise E2EError("The production Fabric entrypoint table is invalid")
    class_names = [
        value
        for raw_values in entrypoints.values()
        if isinstance(raw_values, list)
        for value in raw_values
        if isinstance(value, str)
    ]
    if not class_names:
        raise E2EError("The production Fabric metadata has no class entrypoints")
    common_segments = class_names[0].split(".")[:-1]
    for class_name in class_names[1:]:
        segments = class_name.split(".")[:-1]
        common_count = 0
        for left, right in zip(common_segments, segments):
            if left != right:
                break
            common_count += 1
        common_segments = common_segments[:common_count]
    if len(common_segments) < 2:
        raise E2EError("The production Fabric entrypoints have no stable package prefix")
    return "/".join(common_segments) + "/"


def verify_production_artifact_metadata(
    configuration: ResolvedConfiguration, path: Path
) -> tuple[str, int, set[str]]:
    metadata = fabric_metadata(path)
    root_metadata = metadata[0]
    production = artifact_spec(configuration, "production")
    expected_version = configuration.properties[str(production["version_property"])]
    if root_metadata["id"] != production["mod_id"]:
        raise E2EError(
            f"Production JAR has mod id {root_metadata['id']}, "
            f"expected {production['mod_id']}"
        )
    if root_metadata["version"] != expected_version:
        raise E2EError(
            f"Production JAR has version {root_metadata['version']}, "
            f"expected {expected_version}"
        )
    depends = root_metadata.get("depends")
    if not isinstance(depends, dict):
        raise E2EError("Production Etherology metadata has no dependency table")
    expected_minecraft = f"={configuration.runtime_lane['runtime_version']}"
    if depends.get("minecraft") != expected_minecraft:
        raise E2EError(
            f"Production JAR targets {depends.get('minecraft')}, "
            f"expected {expected_minecraft}"
        )
    discovered_mod_ids = {str(value["id"]) for value in metadata}
    required_nested_mod_ids = {
        str(value)
        for value in require_list(production, "required_nested_mod_ids")
    }
    expected_mod_ids = required_nested_mod_ids | {str(production["mod_id"])}
    if discovered_mod_ids != expected_mod_ids:
        raise E2EError(
            "Production JAR has an unexpected embedded mod inventory: "
            f"expected={sorted(expected_mod_ids)}, "
            f"actual={sorted(discovered_mod_ids)}"
        )
    harness_mod_id = artifact_spec(configuration, "harness")["mod_id"]
    if harness_mod_id in discovered_mod_ids:
        raise E2EError("Production JAR contains the packaged E2E harness")
    return sha256_file(path), path.stat().st_size, discovered_mod_ids


def verify_harness_artifact_metadata(
    configuration: ResolvedConfiguration, path: Path
) -> tuple[str, int, set[str]]:
    metadata = fabric_metadata(path)
    if len(metadata) != 1:
        raise E2EError("E2E harness JAR contains nested Fabric mods")
    root_metadata = metadata[0]
    if root_metadata.get("schemaVersion") != 1:
        raise E2EError("E2E harness JAR has an unsupported metadata schema")
    harness = artifact_spec(configuration, "harness")
    expected_version = configuration.properties[str(harness["version_property"])]
    if root_metadata["id"] != harness["mod_id"]:
        raise E2EError(
            f"E2E harness JAR has mod id {root_metadata['id']}, "
            f"expected {harness['mod_id']}"
        )
    if root_metadata["version"] != expected_version:
        raise E2EError(
            f"E2E harness JAR has version {root_metadata['version']}, "
            f"expected {expected_version}"
        )
    if root_metadata.get("environment") != "client":
        raise E2EError("E2E harness JAR is not client-only")
    depends = root_metadata.get("depends")
    if not isinstance(depends, dict):
        raise E2EError("E2E harness metadata has no dependency table")
    production = artifact_spec(configuration, "production")
    production_version = configuration.properties[str(production["version_property"])]
    if depends.get(str(production["mod_id"])) != f"={production_version}":
        raise E2EError(
            "E2E harness does not require the exact production Etherology version"
        )
    expected_minecraft = f"={configuration.runtime_lane['runtime_version']}"
    if depends.get("minecraft") != expected_minecraft:
        raise E2EError(
            f"E2E harness targets {depends.get('minecraft')}, "
            f"expected {expected_minecraft}"
        )
    expected_entrypoint = str(harness["client_entrypoint"])
    if root_metadata.get("entrypoints") != {"client": [expected_entrypoint]}:
        raise E2EError("E2E harness metadata has an unexpected client entrypoint")
    mixin_config_name = "etherology-e2e-harness.mixins.json"
    expected_mixin_declaration = [
        {"config": mixin_config_name, "environment": "client"}
    ]
    if root_metadata.get("mixins") != expected_mixin_declaration:
        raise E2EError("E2E harness metadata has an unexpected client mixin config")

    try:
        with zipfile.ZipFile(path) as archive:
            entries = set(archive.namelist())
            if any(name.lower().endswith(".jar") for name in entries):
                raise E2EError("E2E harness JAR contains a nested JAR")
            class_entries = {name for name in entries if name.endswith(".class")}
            expected_entry = expected_entrypoint.replace(".", "/") + ".class"
            if expected_entry not in class_entries:
                raise E2EError("E2E harness JAR has no packaged client entrypoint class")
            expected_mixin_class = (
                "dev/theplumteam/etherology/e2e/fabric/mixin/"
                "GameRendererMixin.class"
            )
            if expected_mixin_class not in class_entries:
                raise E2EError("E2E harness JAR has no completed-render callback mixin")
            try:
                mixin_config = json.loads(archive.read(mixin_config_name))
            except (KeyError, json.JSONDecodeError) as exception:
                raise E2EError(
                    "E2E harness JAR has no valid completed-render mixin config"
                ) from exception
            if mixin_config != {
                "required": True,
                "package": "dev.theplumteam.etherology.e2e.fabric.mixin",
                "compatibilityLevel": "JAVA_17",
                "client": ["GameRendererMixin"],
                "injectors": {"defaultRequire": 1},
            }:
                raise E2EError(
                    "E2E harness completed-render mixin config changed unexpectedly"
                )
            harness_prefix = expected_entry.rsplit("/", 1)[0] + "/"
            shared_harness_prefix = "dev/theplumteam/etherology/e2e/shared/"
            outside_classes = sorted(
                name
                for name in class_entries
                if not name.startswith(harness_prefix)
                and not name.startswith(shared_harness_prefix)
            )
            if outside_classes:
                raise E2EError(
                    "E2E harness JAR contains classes outside its isolated package: "
                    f"{outside_classes}"
                )
            production_prefix = production_class_prefix(configuration)
            encoded_production_prefixes = (
                production_prefix.encode("utf-8"),
                production_prefix.rstrip("/").replace("/", ".").encode("utf-8"),
            )
            for class_entry in class_entries:
                if class_entry.startswith(production_prefix):
                    raise E2EError("E2E harness JAR contains production classes")
                class_bytes = archive.read(class_entry)
                if any(prefix in class_bytes for prefix in encoded_production_prefixes):
                    raise E2EError(
                        f"E2E harness class {class_entry} links to production classes"
                    )
    except (OSError, zipfile.BadZipFile) as exception:
        raise E2EError(f"Cannot inspect E2E harness JAR {path}: {exception}") from exception
    return sha256_file(path), path.stat().st_size, {str(root_metadata["id"])}


def verify_artifact_metadata(
    configuration: ResolvedConfiguration, role: str, path: Path
) -> tuple[str, int, set[str]]:
    if not path.is_file() or path.is_symlink():
        raise E2EError(f"{role.title()} artifact JAR is missing or linked: {path}")
    if role == "production":
        return verify_production_artifact_metadata(configuration, path)
    if role == "harness":
        return verify_harness_artifact_metadata(configuration, path)
    raise E2EError(f"Unsupported artifact role: {role}")


def expected_artifact_entry_lock(
    configuration: ResolvedConfiguration,
    role: str,
    sha256: str,
    size: int,
    discovered_mod_ids: set[str],
) -> dict[str, object]:
    artifact = artifact_spec(configuration, role)
    source = require_object(artifact, "source")
    return {
        "source_kind": source["kind"],
        "source_relative_path": artifact_source_relative_path(configuration, role),
        "target_file": artifact["file_name"],
        "mod_id": artifact["mod_id"],
        "version": configuration.properties[str(artifact["version_property"])],
        "size": size,
        "sha256": sha256,
        "contained_mod_ids": sorted(discovered_mod_ids),
    }


def expected_artifact_lock(
    configuration: ResolvedConfiguration,
    inspections: dict[str, tuple[str, int, set[str]]],
) -> dict[str, object]:
    if set(inspections) != set(ARTIFACT_ROLES):
        raise E2EError("Cannot lock an incomplete artifact set")
    return {
        "schema": 2,
        "profile_id": profile_spec(configuration)["id"],
        "artifact_node": configuration.artifact_lane["artifact_node"],
        "artifacts": {
            role: expected_artifact_entry_lock(
                configuration, role, sha256, size, discovered_mod_ids
            )
            for role, (sha256, size, discovered_mod_ids) in inspections.items()
        },
    }


def expected_legacy_artifact_lock(
    configuration: ResolvedConfiguration,
    inspection: tuple[str, int, set[str]],
) -> dict[str, object]:
    sha256, size, discovered_mod_ids = inspection
    production = artifact_spec(configuration, "production")
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "artifact_node": configuration.artifact_lane["artifact_node"],
        "source_relative_path": artifact_source_relative_path(
            configuration, "production"
        ),
        "target_file": production["file_name"],
        "mod_id": production["mod_id"],
        "version": configuration.properties[str(production["version_property"])],
        "size": size,
        "sha256": sha256,
        "contained_mod_ids": sorted(discovered_mod_ids),
    }


def load_artifact_lock(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> dict[str, object] | None:
    path = artifact_lock_path(configuration, root)
    if path.is_symlink():
        raise E2EError(f"Artifact lock must not be a symlink: {path}")
    if not path.exists():
        return None
    lock = load_json_object(path, "artifact lock")
    if lock.get("schema") not in (1, 2):
        raise E2EError(f"Unsupported artifact lock schema: {path}")
    return lock


def verify_legacy_locked_production(
    configuration: ResolvedConfiguration,
    lock: dict[str, object],
    root: Path | None = None,
) -> set[str]:
    expected_sha256 = validate_hex_digest(lock.get("sha256"), "legacy artifact lock")
    size = lock.get("size")
    if type(size) is not int or int(size) <= 0:
        raise E2EError("The legacy artifact lock size is invalid")
    target_path = artifact_target_path(configuration, "production", root)
    verify_exact_file(
        target_path,
        expected_sha256,
        int(size),
        "Legacy staged production Etherology JAR",
    )
    inspection = verify_artifact_metadata(
        configuration, "production", target_path
    )
    if lock != expected_legacy_artifact_lock(configuration, inspection):
        raise E2EError("The legacy artifact lock does not describe its staged JAR")
    return inspection[2]


def verify_locked_artifacts(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
    verify_source: bool = True,
) -> set[str]:
    lock = load_artifact_lock(configuration, root)
    if lock is None or lock.get("schema") != 2:
        raise E2EError(
            "The production and E2E harness JARs are not both staged; "
            "build both artifacts and run the stage action"
        )
    locked_artifacts = lock.get("artifacts")
    if not isinstance(locked_artifacts, dict) or set(locked_artifacts) != set(
        ARTIFACT_ROLES
    ):
        raise E2EError("The artifact lock does not name the exact artifact set")

    inspections: dict[str, tuple[str, int, set[str]]] = {}
    discovered_mod_ids: set[str] = set()
    for role, _artifact in artifact_specs(configuration):
        locked_entry = locked_artifacts.get(role)
        if not isinstance(locked_entry, dict):
            raise E2EError(f"The artifact lock has no {role} entry")
        expected_sha256 = validate_hex_digest(
            locked_entry.get("sha256"), f"{role} artifact lock"
        )
        size = locked_entry.get("size")
        if type(size) is not int or int(size) <= 0:
            raise E2EError(f"The {role} artifact lock size is invalid")
        target_path = artifact_target_path(configuration, role, root)
        verify_exact_file(
            target_path,
            expected_sha256,
            int(size),
            f"Staged {role} artifact JAR",
        )
        inspection = verify_artifact_metadata(configuration, role, target_path)
        inspections[role] = inspection
        discovered_mod_ids.update(inspection[2])
        if verify_source:
            verify_exact_file(
                artifact_source_path(configuration, role),
                expected_sha256,
                int(size),
                f"Current {role} build output",
            )

    if lock != expected_artifact_lock(configuration, inspections):
        raise E2EError("The artifact lock does not describe the staged JARs exactly")
    return discovered_mod_ids


def verify_runtime(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
    artifact_policy: str = "required",
) -> set[str]:
    if artifact_policy not in ("ignore", "optional", "required"):
        raise E2EError(f"Unsupported artifact verification policy: {artifact_policy}")
    target_root = root or runtime_root(configuration)
    verify_profile_marker(configuration, target_root)
    target_game_directory = game_directory(configuration, target_root)
    if not target_game_directory.is_dir() or target_game_directory.is_symlink():
        raise E2EError(
            f"Isolated E2E game directory is missing or linked: {target_game_directory}"
        )
    for relative_directory in profile_directories(configuration):
        directory = target_game_directory / relative_directory
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"Isolated game directory is missing or linked: {directory}")

    options_path = target_game_directory / "options.txt"
    if not options_path.is_file() or options_path.is_symlink():
        raise E2EError(f"Isolated options file is missing or linked: {options_path}")
    try:
        actual_options = parse_options(options_path.read_text(encoding="utf-8"))
    except OSError as exception:
        raise E2EError(f"Cannot read isolated options: {exception}") from exception
    expected_options = parse_options(options_text(configuration))
    changed_options = {
        name: {"expected": value, "actual": actual_options.get(name)}
        for name, value in expected_options.items()
        if actual_options.get(name) != value
    }
    if changed_options:
        raise E2EError(f"Controlled E2E options changed: {changed_options}")

    verify_installed_game(configuration, target_root)
    mods_directory = target_game_directory / "mods"
    dependencies = dependency_specs(configuration)
    expected_file_names = {str(value["file_name"]) for value in dependencies}
    artifact_file_names = {
        role: str(artifact["file_name"])
        for role, artifact in artifact_specs(configuration)
    }
    lock = load_artifact_lock(configuration, target_root)
    lock_schema = lock.get("schema") if lock is not None else None
    if artifact_policy == "required" and lock_schema != 2:
        raise E2EError(
            "The production and E2E harness JARs are not both staged; "
            "build both artifacts and run the stage action"
        )
    actual_entries = {entry.name for entry in mods_directory.iterdir()}
    if artifact_policy == "ignore":
        expected_file_names.update(
            set(artifact_file_names.values()).intersection(actual_entries)
        )
    elif lock_schema == 2:
        expected_file_names.update(artifact_file_names.values())
    elif artifact_policy == "optional" and lock_schema == 1:
        expected_file_names.add(artifact_file_names["production"])
    if actual_entries != expected_file_names:
        missing = sorted(expected_file_names - actual_entries)
        unexpected = sorted(actual_entries - expected_file_names)
        raise E2EError(
            f"Isolated mod inventory changed: missing={missing}, unexpected={unexpected}"
        )
    for file_name in set(artifact_file_names.values()).intersection(actual_entries):
        path = mods_directory / file_name
        if path.is_symlink() or not path.is_file():
            raise E2EError(f"Staged artifact is not one regular file: {path}")

    discovered_mod_ids: set[str] = set()
    for dependency in dependencies:
        discovered_mod_ids.update(
            verify_dependency_jar(
                configuration,
                mods_directory / str(dependency["file_name"]),
                dependency,
            )
        )
    if artifact_policy != "ignore" and lock_schema == 2:
        discovered_mod_ids.update(
            verify_locked_artifacts(
                configuration,
                target_root,
                verify_source=artifact_policy == "required",
            )
        )
    elif artifact_policy == "optional" and lock is not None and lock_schema == 1:
        discovered_mod_ids.update(
            verify_legacy_locked_production(configuration, lock, target_root)
        )
    forbidden_mod_ids = {
        str(value)
        for value in require_list(configuration.manifest, "forbidden_mod_ids")
    }
    present_forbidden_mod_ids = forbidden_mod_ids.intersection(discovered_mod_ids)
    if present_forbidden_mod_ids:
        raise E2EError(
            f"Forbidden mods are present in the isolated runtime: "
            f"{sorted(present_forbidden_mod_ids)}"
        )
    return discovered_mod_ids


def provision_profile(configuration: ResolvedConfiguration) -> bool:
    try:
        verify_java_option_environment(os.environ)
    except GuardedJavaError as exception:
        raise E2EError(str(exception)) from exception
    ensure_owned_state_roots()
    require_unattempted_profile(configuration)
    target_root = runtime_root(configuration)
    if target_root.exists() or target_root.is_symlink():
        verify_profile_marker(configuration, target_root)
        normalize_fabric_version_metadata(configuration, target_root)
        verify_runtime(configuration, target_root, artifact_policy="optional")
        ensure_evidence_layout(configuration, target_root)
        return False

    java_path = resolve_java_17()
    STATE_ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    RUNTIMES_ROOT.mkdir(mode=0o700, exist_ok=True)
    staging_root = Path(
        tempfile.mkdtemp(
            prefix=f".{profile_spec(configuration)['runtime_directory']}.",
            dir=RUNTIMES_ROOT,
        )
    )
    try:
        target_game_directory = game_directory(configuration, staging_root)
        target_launcher_directory = launcher_directory(configuration, staging_root)
        target_game_directory.mkdir(mode=0o700)
        target_launcher_directory.mkdir(mode=0o700)
        (staging_root / "installer").mkdir(mode=0o700)
        for relative_directory in profile_directories(configuration):
            (target_game_directory / relative_directory).mkdir(mode=0o700)
        write_private_text_exclusive(
            staging_root / PROFILE_MARKER_NAME,
            json.dumps(profile_descriptor(configuration), indent=2, sort_keys=True) + "\n",
        )
        write_private_text_exclusive(
            target_game_directory / "options.txt",
            options_text(configuration),
        )

        mods_directory = target_game_directory / "mods"
        for dependency in dependency_specs(configuration):
            destination = mods_directory / str(dependency["file_name"])
            download_pinned_file(
                str(dependency["url"]),
                destination,
                str(dependency["sha256"]),
                int(dependency["size"]),
                f"dependency {dependency['mod_id']}",
            )
            verify_dependency_jar(configuration, destination, dependency)

        installer_destination = (
            staging_root / "installer" / installer_file_name(configuration)
        )
        download_pinned_file(
            str(configuration.installer["url"]),
            installer_destination,
            str(configuration.installer["sha256"]),
            None,
            "Fabric installer",
        )
        install_isolated_game(configuration, staging_root, java_path)
        normalize_fabric_version_metadata(configuration, staging_root)
        verify_runtime(configuration, staging_root, artifact_policy="optional")
        ensure_evidence_layout(configuration, staging_root)
        os.replace(staging_root, target_root)
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root)
    verify_runtime(configuration, target_root, artifact_policy="optional")
    verify_evidence_layout(configuration, target_root)
    return True


def assert_runtime_not_running(configuration: ResolvedConfiguration) -> None:
    completed = subprocess.run(
        ["/bin/ps", "-ww", "-axo", "command="],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    target_game_directory = str(game_directory(configuration))
    if completed.returncode == 0 and any(
        "--gameDir" in line and target_game_directory in line
        for line in completed.stdout.splitlines()
    ):
        raise E2EError("The isolated Etherology E2E runtime already appears to be running")


def stage_artifacts(
    configuration: ResolvedConfiguration,
) -> tuple[bool, dict[str, object]]:
    require_unattempted_profile(configuration)
    assert_runtime_not_running(configuration)
    verify_runtime(configuration, artifact_policy="ignore")
    source_paths = {
        role: artifact_source_path(configuration, role) for role in ARTIFACT_ROLES
    }
    if len(set(source_paths.values())) != len(ARTIFACT_ROLES):
        raise E2EError("The production and harness tasks resolved to the same JAR")
    inspections = {
        role: verify_artifact_metadata(configuration, role, source_paths[role])
        for role in ARTIFACT_ROLES
    }
    desired_lock = expected_artifact_lock(configuration, inspections)
    current_lock = load_artifact_lock(configuration)
    if current_lock == desired_lock:
        verify_locked_artifacts(configuration)
        return False, desired_lock

    target_paths = {
        role: artifact_target_path(configuration, role) for role in ARTIFACT_ROLES
    }
    temporary_paths = {
        role: target_path.parent / f".{target_path.name}.{os.getpid()}.tmp"
        for role, target_path in target_paths.items()
    }
    for temporary_path in temporary_paths.values():
        if temporary_path.exists() or temporary_path.is_symlink():
            raise E2EError(f"Artifact staging path already exists: {temporary_path}")
    try:
        for role in ARTIFACT_ROLES:
            source_path = source_paths[role]
            temporary_path = temporary_paths[role]
            source_sha256, source_size, _mod_ids = inspections[role]
            shutil.copyfile(source_path, temporary_path)
            temporary_path.chmod(0o600)
            verify_exact_file(
                temporary_path,
                source_sha256,
                source_size,
                f"Copied {role} artifact JAR",
            )
            if verify_artifact_metadata(
                configuration, role, temporary_path
            ) != inspections[role]:
                raise E2EError(
                    f"Copied {role} artifact metadata differs from its source"
                )
        for role in ARTIFACT_ROLES:
            source_sha256, source_size, _mod_ids = inspections[role]
            verify_exact_file(
                source_paths[role],
                source_sha256,
                source_size,
                f"Current {role} build output",
            )
        for role in ARTIFACT_ROLES:
            os.replace(temporary_paths[role], target_paths[role])
        write_json_atomic(artifact_lock_path(configuration), desired_lock)
    finally:
        for temporary_path in temporary_paths.values():
            temporary_path.unlink(missing_ok=True)
    verify_runtime(configuration, artifact_policy="required")
    return True, desired_lock


def offline_uuid(username: str) -> str:
    digest = hashlib.md5(f"OfflinePlayer:{username}".encode("utf-8")).digest()
    return str(uuid.UUID(bytes=digest, version=3))


def generate_command(
    configuration: ResolvedConfiguration,
    java_path: Path,
    configured_scenario_id: str | None = None,
) -> list[str]:
    verify_launcher_library()
    try:
        import minecraft_launcher_lib.command
        import minecraft_launcher_lib.utils
    except ImportError as exception:
        raise E2EError("minecraft-launcher-lib could not be imported") from exception
    launch = require_object(configuration.manifest, "launch")
    resolution = require_object(launch, "resolution")
    username = str(launch["offline_username"])
    scenario_id = resolve_scenario_id(configuration, configured_scenario_id)
    options = minecraft_launcher_lib.utils.generate_test_options()
    options.update(
        {
            "username": username,
            "uuid": offline_uuid(username),
            "token": "offline-etherology-e2e",
            "executablePath": str(java_path),
            "defaultExecutablePath": str(java_path),
            "gameDirectory": str(game_directory(configuration)),
            "customResolution": True,
            "resolutionWidth": str(resolution["width"]),
            "resolutionHeight": str(resolution["height"]),
            "jvmArguments": [
                f"-Xmx{launch['maximum_memory_mb']}M",
                f"-D{SCENARIO_PROPERTY_NAME}={scenario_id}",
            ],
        }
    )
    try:
        return minecraft_launcher_lib.command.get_minecraft_command(
            version_id(configuration),
            str(launcher_directory(configuration)),
            options,
        )
    except Exception as exception:
        raise E2EError(
            f"Cannot generate the isolated Fabric launch command: {exception}"
        ) from exception


def verify_launch_command(
    configuration: ResolvedConfiguration,
    command: list[str],
    root: Path | None = None,
    configured_scenario_id: str | None = None,
) -> None:
    if "net.fabricmc.loader.impl.launch.knot.KnotClient" not in command:
        raise E2EError("Generated command does not launch Fabric KnotClient")
    scenario_id = resolve_scenario_id(configuration, configured_scenario_id)
    expected_scenario_argument = f"-D{SCENARIO_PROPERTY_NAME}={scenario_id}"
    scenario_arguments = [
        argument
        for argument in command
        if argument.startswith(f"-D{SCENARIO_PROPERTY_NAME}=")
    ]
    if scenario_arguments != [expected_scenario_argument]:
        raise E2EError(
            "Generated command does not select exactly the requested E2E scenario"
        )
    target_game_directory = str(game_directory(configuration, root))
    has_game_directory = any(
        argument == "--gameDir"
        and index + 1 < len(command)
        and command[index + 1] == target_game_directory
        for index, argument in enumerate(command)
    )
    if not has_game_directory:
        raise E2EError("Generated command does not target the isolated E2E game directory")

    classpath_indexes = [
        index
        for index, argument in enumerate(command)
        if argument in ("-cp", "-classpath")
    ]
    if len(classpath_indexes) != 1:
        raise E2EError("Generated command does not contain exactly one classpath")
    classpath_index = classpath_indexes[0]
    if classpath_index + 1 >= len(command):
        raise E2EError("Generated command has no classpath value")
    raw_classpath_entries = command[classpath_index + 1].split(os.pathsep)
    if not raw_classpath_entries or any(not value for value in raw_classpath_entries):
        raise E2EError("Generated command contains an empty classpath entry")

    launcher_root = launcher_directory(configuration, root)
    if not launcher_root.is_dir() or launcher_root.is_symlink():
        raise E2EError(f"Isolated launcher root is missing or linked: {launcher_root}")
    classpath_entries: list[Path] = []
    for raw_entry in raw_classpath_entries:
        entry = Path(raw_entry)
        try:
            relative_entry = entry.relative_to(launcher_root)
        except ValueError as exception:
            raise E2EError(
                f"Generated classpath escapes the isolated launcher root: {entry}"
            ) from exception
        current_entry = launcher_root
        for path_part in relative_entry.parts:
            current_entry /= path_part
            if current_entry.is_symlink():
                raise E2EError(f"Generated classpath resolves through a symlink: {entry}")
        if not entry.is_file():
            raise E2EError(f"Generated classpath entry is missing: {entry}")
        classpath_entries.append(entry)

    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    expected_client_jar = (
        launcher_root
        / "versions"
        / minecraft_version
        / f"{minecraft_version}.jar"
    )
    if expected_client_jar not in classpath_entries:
        raise E2EError(
            "Generated classpath does not contain the installed vanilla client JAR"
        )


def verify_environment(
    configuration: ResolvedConfiguration,
    configured_scenario_id: str | None = None,
) -> tuple[Path, list[str]]:
    try:
        verify_java_option_environment(os.environ)
    except GuardedJavaError as exception:
        raise E2EError(str(exception)) from exception
    verify_runtime(configuration, artifact_policy="required")
    verify_evidence_layout(configuration)
    java_path = resolve_java_17()
    command = generate_command(configuration, java_path, configured_scenario_id)
    verify_launch_command(
        configuration,
        command,
        configured_scenario_id=configured_scenario_id,
    )
    launch = require_object(configuration.manifest, "launch")
    try:
        verify_java_launch_contract(
            command,
            java_path,
            int(launch["maximum_memory_mb"]),
            os.environ,
        )
    except GuardedJavaError as exception:
        raise E2EError(str(exception)) from exception
    if not CAFFEINATE_PATH.is_file():
        raise E2EError(f"macOS caffeinate is missing: {CAFFEINATE_PATH}")
    return java_path, command


def read_process_state(
    configuration: ResolvedConfiguration,
) -> dict[str, object] | None:
    path = process_state_path(configuration)
    if not path.exists():
        return None
    state = read_owned_process_state(path)
    if (
        state.get("profile_id") != profile_spec(configuration)["id"]
        or state.get("version_id") != version_id(configuration)
        or state.get("game_directory") != str(game_directory(configuration))
        or state.get("scenario") not in scenario_ids(configuration)
    ):
        raise E2EError("E2E process state does not describe this exact isolated profile")
    return state


def read_owned_process_state(
    path: Path, state_root: Path = STATE_ROOT
) -> dict[str, object]:
    if path.parent != state_root:
        raise E2EError(f"E2E process state is outside the owned state root: {path}")
    if path.is_symlink() or not path.is_file():
        raise E2EError(f"E2E process state must be one regular file: {path}")
    match = re.fullmatch(
        r"(?P<profile_id>[a-z0-9][a-z0-9.-]+)-current\.json",
        path.name,
    )
    if match is None:
        raise E2EError(f"E2E process state has an unsafe file name: {path}")

    state = load_json_object(path, "E2E process state")
    profile_id = match.group("profile_id")
    game_directory_value = state.get("game_directory")
    if not isinstance(game_directory_value, str):
        raise E2EError("E2E process state has no game directory")
    target_game_directory = Path(game_directory_value)
    runtime = target_game_directory.parent
    expected_runtimes_root = state_root / "runtimes"
    if (
        state.get("schema") not in (1, 2)
        or state.get("profile_id") != profile_id
        or type(state.get("pid")) is not int
        or int(state["pid"]) <= 0
        or not isinstance(state.get("version_id"), str)
        or not state["version_id"]
        or not target_game_directory.is_absolute()
        or target_game_directory.name != "game"
        or runtime.name != profile_id
        or runtime.parent != expected_runtimes_root
    ):
        raise E2EError("E2E process state does not describe one owned runtime")
    for directory, description in (
        (state_root, "E2E state root"),
        (expected_runtimes_root, "E2E runtimes root"),
        (runtime, "E2E runtime"),
        (target_game_directory, "E2E game directory"),
    ):
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"{description} is missing or linked: {directory}")

    marker_path = runtime / PROFILE_MARKER_NAME
    if not marker_path.is_file() or marker_path.is_symlink():
        raise E2EError(f"Owned E2E runtime marker is missing or linked: {marker_path}")
    marker = load_json_object(marker_path, "E2E profile marker")
    isolation = marker.get("isolation")
    if (
        marker.get("schema") != 1
        or marker.get("profile_id") != profile_id
        or marker.get("managed_by") != "scripts/e2e/client.py"
        or not isinstance(isolation, dict)
        or isolation.get("scope") != "repository-owned-ignored-state"
        or isolation.get("source_profiles") != []
    ):
        raise E2EError(f"Owned E2E runtime marker is invalid: {marker_path}")

    scenario = state.get("scenario")
    if scenario is not None and (
        not isinstance(scenario, str)
        or re.fullmatch(r"[a-z0-9][a-z0-9-]*", scenario) is None
    ):
        raise E2EError("E2E process state has an unsafe scenario id")
    if state["schema"] == 2:
        try:
            target = owned_java_process_from_state(state)
            verify_guard_state_paths(state, runtime)
        except GuardedJavaError as exception:
            raise E2EError(str(exception)) from exception
        if target.pid != int(state["pid"]) or target.process_group_id != target.pid:
            raise E2EError(
                "E2E process state does not pin one dedicated Java process group"
            )
        if (
            type(state.get("memory_guard_pid")) is not int
            or int(state["memory_guard_pid"]) <= 0
            or int(state["memory_guard_pid"]) == target.pid
        ):
            raise E2EError("E2E process state has no valid memory guard PID")
    process_log_path(state, state_root)
    return state


def owned_process_states(
    state_root: Path = STATE_ROOT,
) -> list[tuple[Path, dict[str, object]]]:
    ensure_owned_state_roots(state_root)
    if not state_root.exists():
        return []
    return [
        (path, read_owned_process_state(path, state_root))
        for path in sorted(state_root.glob("*-current.json"))
    ]


def clear_stale_and_reject_live_owned_clients(
    state_root: Path = STATE_ROOT,
) -> None:
    stale_paths: list[Path] = []
    live_states: list[dict[str, object]] = []
    for path, state in owned_process_states(state_root):
        pid = int(state["pid"])
        if not process_exists(pid):
            stale_paths.append(path)
            continue
        if not process_matches(pid, state):
            raise E2EError(
                f"Owned state PID {pid} belongs to another process; refusing lifecycle access"
            )
        if state.get("schema") == 2 and (
            not memory_guard_process_matches(state)
            or not memory_guard_is_enforcing(state)
        ):
            raise E2EError(
                f"Owned state PID {pid} is live without its exact memory guard"
            )
        live_states.append(state)

    for path in stale_paths:
        path.unlink()
    if live_states:
        descriptions = [
            f"{state['profile_id']} (PID {state['pid']})" for state in live_states
        ]
        raise E2EError(
            "A repository-owned Etherology E2E client is already running: "
            + ", ".join(descriptions)
        )


def process_log_path(
    state: dict[str, object], state_root: Path = STATE_ROOT
) -> Path:
    raw_log_path = state.get("log")
    if not isinstance(raw_log_path, str):
        raise E2EError("E2E process state has no client log path")
    log_path = Path(raw_log_path)
    logs_directory = state_root / "logs"
    if log_path.parent != logs_directory or log_path.name != safe_leaf_name(
        log_path.name, "process log"
    ):
        raise E2EError("E2E process state points outside its isolated log directory")
    ensure_directory_is_not_linked(logs_directory, "E2E process logs directory")
    if log_path.is_symlink():
        raise E2EError(f"E2E process log must not be a symlink: {log_path}")
    return log_path


def find_client_failure_marker(log_content: str) -> str | None:
    return next(
        (marker for marker in FATAL_CLIENT_LOG_MARKERS if marker in log_content),
        None,
    )


def client_failure_marker(state: dict[str, object]) -> str | None:
    log_path = process_log_path(state)
    if not log_path.is_file():
        raise E2EError(f"E2E process log is missing: {log_path}")
    try:
        log_size = log_path.stat().st_size
        if log_size > MAXIMUM_PROCESS_LOG_SIZE:
            return f"client log exceeded {MAXIMUM_PROCESS_LOG_SIZE} bytes"
        log_content = log_path.read_text(encoding="utf-8", errors="replace")
    except OSError as exception:
        raise E2EError(f"Cannot inspect E2E process log {log_path}: {exception}") from exception
    return find_client_failure_marker(log_content)


def process_exists(pid: int) -> bool:
    completed = subprocess.run(
        ["/bin/ps", "-p", str(pid), "-o", "stat="],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    process_state = completed.stdout.strip()
    return completed.returncode == 0 and bool(process_state) and not process_state.startswith("Z")


def process_matches(pid: int, state: dict[str, object]) -> bool:
    completed = subprocess.run(
        ["/bin/ps", "-ww", "-p", str(pid), "-o", "command="],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    command = completed.stdout
    scenario = state.get("scenario")
    scenario_matches = scenario is None or (
        f"-D{SCENARIO_PROPERTY_NAME}={scenario}" in command
    )
    return (
        completed.returncode == 0
        and str(state["version_id"]) in command
        and str(state["game_directory"]) in command
        and scenario_matches
        and "net.fabricmc.loader.impl.launch.knot.KnotClient" in command
    )


def wait_for_stable_client_start(state: dict[str, object]) -> None:
    pid = int(state["pid"])
    deadline = time.monotonic() + START_STABILITY_SECONDS
    while time.monotonic() < deadline:
        failure_marker = client_failure_marker(state)
        if failure_marker is not None:
            raise E2EError(f"Client failed during startup: {failure_marker}")
        if not process_exists(pid):
            raise E2EError("Client exited during startup")
        if not process_matches(pid, state):
            raise E2EError("Client process identity changed during startup")
        if state.get("schema") == 2 and (
            not memory_guard_process_matches(state)
            or not memory_guard_is_enforcing(state)
        ):
            raise E2EError("Client memory guard stopped enforcing during startup")
        time.sleep(0.1)


def stop_owned_process_group(state: dict[str, object]) -> bool:
    pid = int(state["pid"])
    if state.get("schema") == 2:
        try:
            return stop_owned_java_process(owned_java_process_from_state(state))
        except GuardedJavaError as exception:
            raise E2EError(str(exception)) from exception
    try:
        os.killpg(pid, signal.SIGTERM)
    except ProcessLookupError:
        return False
    deadline = time.monotonic() + STOP_TIMEOUT_SECONDS
    while process_exists(pid) and time.monotonic() < deadline:
        time.sleep(0.25)
    if not process_exists(pid):
        return False
    try:
        os.killpg(pid, signal.SIGKILL)
    except ProcessLookupError:
        return False
    return True


def clear_stale_process_state(configuration: ResolvedConfiguration) -> None:
    process_state_path(configuration).unlink(missing_ok=True)


def validate_command() -> int:
    configuration = load_configuration()
    print(
        "Validated profile manifest: "
        f"{profile_spec(configuration)['id']} / "
        f"Minecraft {configuration.runtime_lane['runtime_version']} / "
        f"Fabric {configuration.runtime_lane['loader_version']}"
    )
    print(f"Pinned required dependency JARs: {len(dependency_specs(configuration))}")
    for role in ARTIFACT_ROLES:
        print(
            f"Resolved {role} artifact: "
            f"{artifact_source_relative_path(configuration, role)}"
        )
    print(f"Runtime root: {runtime_root(configuration)}")
    print("External game profiles consulted: 0")
    return 0


def provision_command() -> int:
    configuration = load_configuration()
    created = provision_profile(configuration)
    if created:
        print(f"Provisioned new repository-owned runtime: {runtime_root(configuration)}")
    else:
        print(f"Repository-owned runtime already matches: {runtime_root(configuration)}")
    print(f"Verified required dependency JARs: {len(dependency_specs(configuration))}")
    print(
        f"Prepared scenario evidence directories: {len(scenario_ids(configuration))} "
        f"under {evidence_root(configuration)}"
    )
    print("Minecraft was not launched; no external game profile was inspected or changed")
    return 0


def stage_command() -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    staged, lock = stage_artifacts(configuration)
    locked_artifacts = require_object(lock, "artifacts")
    if staged:
        print("Staged production and packaged E2E harness JARs:")
        for role in ARTIFACT_ROLES:
            print(f"  {role}: {artifact_target_path(configuration, role)}")
    else:
        print("Both staged JARs already match their current build outputs")
    for role in ARTIFACT_ROLES:
        locked_entry = require_object(locked_artifacts, role)
        print(f"{role.title()} SHA-256: {locked_entry['sha256']}")
    print("Minecraft was not launched")
    return 0


def check_command(configured_scenario_id: str | None = None) -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    require_unattempted_profile(configuration)
    scenario_id = resolve_scenario_id(configuration, configured_scenario_id)
    java_path, command = verify_environment(configuration, scenario_id)
    lock = load_artifact_lock(configuration)
    if lock is None or lock.get("schema") != 2:
        raise E2EError("Artifact-set lock disappeared during validation")
    locked_artifacts = require_object(lock, "artifacts")
    print(
        "Ready: "
        f"Minecraft {configuration.runtime_lane['runtime_version']} / "
        f"Fabric {configuration.runtime_lane['loader_version']} / Java 17"
    )
    print(f"Runtime root: {runtime_root(configuration)}")
    print(f"Game directory: {game_directory(configuration)}")
    print(f"Java: {java_path}")
    print(f"Scenario: {scenario_id}")
    for role in ARTIFACT_ROLES:
        locked_entry = require_object(locked_artifacts, role)
        print(f"{role.title()} JAR SHA-256: {locked_entry['sha256']}")
    print(f"Generated argv entries: {len(command)} (command intentionally not displayed)")
    print("External game profiles consulted: 0")
    return 0


def start_command(configured_scenario_id: str | None = None) -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    require_unattempted_profile(configuration)
    scenario_id = resolve_scenario_id(configuration, configured_scenario_id)
    clear_stale_and_reject_live_owned_clients()

    java_path, command = verify_environment(configuration, scenario_id)
    assert_runtime_not_running(configuration)
    logs_directory = STATE_ROOT / "logs"
    ensure_directory_is_not_linked(logs_directory, "E2E process logs directory")
    reserve_start_attempt(configuration, scenario_id)
    logs_directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    timestamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    log_path = logs_directory / f"fabric-1.20.1-{timestamp}.log"
    log_descriptor = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    log_handle = os.fdopen(log_descriptor, "wb", buffering=0)
    header = (
        "Etherology repository-owned Fabric 1.20.1 E2E client\n"
        f"started_utc={timestamp}\n"
        f"profile_id={profile_spec(configuration)['id']}\n"
        f"scenario={scenario_id}\n"
        f"game_directory={game_directory(configuration)}\n"
        f"java={java_path}\n"
        "authentication=deterministic-offline-identity\n\n"
    ).encode("utf-8")
    log_handle.write(header)
    launch_environment = dict(os.environ)
    launch_specification = require_object(configuration.manifest, "launch")
    try:
        guarded_launch = start_guarded_java(
            command,
            java_path,
            int(launch_specification["maximum_memory_mb"]),
            launch_environment,
            runtime_root(configuration),
            game_directory(configuration),
            log_handle,
            CAFFEINATE_PATH,
        )
    except (GuardedJavaError, OSError) as exception:
        log_handle.close()
        raise E2EError(f"Cannot start guarded Java client: {exception}") from exception
    log_handle.close()
    state = {
        "schema": 2,
        "profile_id": profile_spec(configuration)["id"],
        "pid": guarded_launch.target.pid,
        "started_utc": timestamp,
        "scenario": scenario_id,
        "version_id": version_id(configuration),
        "game_directory": str(game_directory(configuration)),
        "log": str(log_path),
        **guarded_launch.state_fields(),
    }
    try:
        write_json_atomic(process_state_path(configuration), state)
    except (OSError, TypeError, ValueError):
        stop_guarded_java_launch(guarded_launch)
        raise
    try:
        wait_for_stable_client_start(state)
    except E2EError as exception:
        try:
            stop_guarded_java_launch(guarded_launch)
        except (GuardedJavaError, OSError) as cleanup_exception:
            raise E2EError(
                f"{exception}; guarded cleanup failed and process state was retained: "
                f"{cleanup_exception}; log: {log_path}"
            ) from cleanup_exception
        clear_stale_process_state(configuration)
        raise E2EError(f"{exception}; log: {log_path}") from exception
    print(
        f"Started isolated Etherology Fabric 1.20.1 {scenario_id} client "
        f"as PID {guarded_launch.target.pid}"
    )
    print(f"Log: {log_path}")
    print(f"Memory telemetry: {guarded_launch.telemetry_path}")
    return 0


def status_command() -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    state = read_process_state(configuration)
    if state is None:
        print("Etherology Fabric 1.20.1 E2E client is stopped")
        return 1
    pid = int(state["pid"])
    if not process_exists(pid):
        failure_marker = client_failure_marker(state)
        clear_stale_process_state(configuration)
        if failure_marker is None:
            print(f"Client is stopped; cleared stale state for PID {pid}")
        else:
            print(
                f"Client failed and stopped; cleared state for PID {pid}: "
                f"{failure_marker}"
            )
        print(f"Last log: {state.get('log', 'unknown')}")
        return 1
    if not process_matches(pid, state):
        raise E2EError("State PID belongs to another process; refusing to manage it")
    if state.get("schema") == 2 and (
        not memory_guard_process_matches(state)
        or not memory_guard_is_enforcing(state)
    ):
        print("Etherology Fabric 1.20.1 E2E client is live but unmonitored")
        print(f"Process PID {pid} remains owned; run stop before another launch")
        print(f"Memory telemetry: {state['memory_guard_telemetry']}")
        return 2
    failure_marker = client_failure_marker(state)
    if failure_marker is not None:
        print(f"Etherology Fabric 1.20.1 E2E client failed: {failure_marker}")
        print(f"Process PID {pid} remains owned; run stop before another launch")
        print(f"Log: {state['log']}")
        return 2
    print(f"Etherology Fabric 1.20.1 E2E client is running as PID {pid}")
    print(f"Scenario: {state['scenario']}")
    print(f"Log: {state.get('log', 'unknown')}")
    if state.get("schema") == 2:
        print(f"Memory telemetry: {state['memory_guard_telemetry']}")
    return 0


def stop_command() -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    state = read_process_state(configuration)
    if state is None:
        print("Etherology Fabric 1.20.1 E2E client is already stopped")
        return 0
    pid = int(state["pid"])
    if not process_exists(pid):
        clear_stale_process_state(configuration)
        print(f"Removed stale E2E process state for PID {pid}")
        return 0
    if not process_matches(pid, state):
        raise E2EError("State PID belongs to another process; refusing to stop it")

    if stop_owned_process_group(state):
        print(f"Etherology E2E client PID {pid} required a forced stop")
    else:
        print(f"Stopped Etherology E2E client PID {pid}")
    clear_stale_process_state(configuration)
    return 0


def stop_all_owned_command() -> int:
    ensure_owned_state_roots()
    states = owned_process_states()
    live_states: list[tuple[Path, dict[str, object]]] = []
    for path, state in states:
        pid = int(state["pid"])
        if not process_exists(pid):
            continue
        if not process_matches(pid, state):
            raise E2EError(
                f"Owned state PID {pid} belongs to another process; refusing to stop anything"
            )
        live_states.append((path, state))

    for path, state in live_states:
        pid = int(state["pid"])
        forced = stop_owned_process_group(state)
        qualifier = "required a forced stop" if forced else "stopped normally"
        print(f"Owned E2E client {state['profile_id']} PID {pid} {qualifier}")
    for path, _state in states:
        path.unlink()

    print(
        f"Stopped {len(live_states)} repository-owned client(s) and cleared "
        f"{len(states)} owned state file(s)"
    )
    return 0


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Provision and manage the repository-owned Etherology Fabric 1.20.1 "
            "E2E runtime without consulting launcher profiles."
        )
    )
    parser.add_argument(
        "action",
        choices=(
            "validate",
            "provision",
            "stage",
            "check",
            "start",
            "status",
            "stop",
            "stop-all-owned",
        ),
    )
    parser.add_argument(
        "--scenario",
        help=(
            "Exact packaged E2E scenario id for check/start; defaults to the first "
            "scenario in the tracked profile"
        ),
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    actions = {
        "validate": validate_command,
        "provision": provision_command,
        "stage": stage_command,
        "status": status_command,
        "stop": stop_command,
        "stop-all-owned": stop_all_owned_command,
    }
    try:
        if arguments.action == "check":
            return check_command(arguments.scenario)
        if arguments.action == "start":
            return start_command(arguments.scenario)
        if arguments.scenario is not None:
            raise E2EError("--scenario is supported only by check and start")
        return actions[arguments.action]()
    except E2EError as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
