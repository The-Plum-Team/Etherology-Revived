#!/usr/bin/env python3
"""Provision and manage the repository-owned Etherology Forge 1.20.1 client."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import importlib.metadata
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
import tomllib
from typing import BinaryIO
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile


EXPECTED_LAUNCHER_LIBRARY_VERSION = "8.0"
SCRIPT_DIRECTORY = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIRECTORY.parents[1]
MANIFEST_PATH = SCRIPT_DIRECTORY / "forge-1.20.1-profile.json"
PROFILE_MANIFEST_RELATIVE_PATH = Path("scripts/e2e/forge-1.20.1-profile.json")
STATE_ROOT = SCRIPT_DIRECTORY / ".state"
RUNTIMES_ROOT = STATE_ROOT / "runtimes"
PROFILE_MARKER_NAME = ".etherology-forge-e2e-profile.json"
EVIDENCE_MARKER_NAME = ".etherology-e2e-evidence.json"
MANAGED_BY = "scripts/e2e/forge_client.py"
CAFFEINATE_PATH = Path("/usr/bin/caffeinate")
JAVA_OVERRIDE_ENVIRONMENT_VARIABLE = "ETHERLOGY_E2E_JAVA_17"
SCENARIO_PROPERTY_NAME = "etherology.e2e.scenario"
ARTIFACT_ROLES = ("production", "harness")
EXPECTED_ARTIFACT_TASKS = {
    "production": ":forge:1.20.1:remapE2eUnderTestJar",
    "harness": ":forge:1.20.1:remapE2eHarnessJar",
}
EXPECTED_DEPENDENCY_MOD_IDS = {"architectury", "geckolib"}
EXPECTED_PRODUCTION_DATA_ENTRIES = {
    "data/etherology/advancements/recipes/building_blocks/attrahite_brick_slab.json",
    "data/etherology/advancements/recipes/building_blocks/attrahite_brick_slab_from_attrahite_bricks_stonecutting.json",
    "data/etherology/advancements/recipes/building_blocks/attrahite_brick_stairs.json",
    "data/etherology/advancements/recipes/building_blocks/attrahite_brick_stairs_from_attrahite_bricks_stonecutting.json",
    "data/etherology/advancements/recipes/building_blocks/attrahite_bricks.json",
    "data/etherology/advancements/recipes/food/forest_lantern_crumb.json",
    "data/etherology/advancements/recipes/food/forest_lantern_crumb_from_campfire.json",
    "data/etherology/advancements/recipes/food/forest_lantern_crumb_from_smoking.json",
    "data/etherology/advancements/recipes/misc/attrahite_brick.json",
    "data/etherology/advancements/recipes/misc/azel_ingot.json",
    "data/etherology/advancements/recipes/misc/azel_ingot_from_blasting.json",
    "data/etherology/advancements/recipes/misc/leather.json",
    "data/etherology/advancements/recipes/misc/raw_azel.json",
    "data/etherology/ether_sources/default.json",
    "data/etherology/loot_tables/blocks/attrahite.json",
    "data/etherology/loot_tables/blocks/attrahite_brick_slab.json",
    "data/etherology/loot_tables/blocks/attrahite_brick_stairs.json",
    "data/etherology/loot_tables/blocks/attrahite_bricks.json",
    "data/etherology/loot_tables/blocks/azel_block.json",
    "data/etherology/loot_tables/blocks/ebony_block.json",
    "data/etherology/loot_tables/blocks/ethereal_storage.json",
    "data/etherology/loot_tables/blocks/ethril_block.json",
    "data/etherology/loot_tables/blocks/forest_lantern.json",
    "data/etherology/recipes/attrahite_brick.json",
    "data/etherology/recipes/attrahite_brick_slab.json",
    "data/etherology/recipes/attrahite_brick_slab_from_attrahite_bricks_stonecutting.json",
    "data/etherology/recipes/attrahite_brick_stairs.json",
    "data/etherology/recipes/attrahite_brick_stairs_from_attrahite_bricks_stonecutting.json",
    "data/etherology/recipes/attrahite_bricks.json",
    "data/etherology/recipes/azel_block.json",
    "data/etherology/recipes/azel_ingot.json",
    "data/etherology/recipes/azel_ingot_from_azel_block.json",
    "data/etherology/recipes/azel_ingot_from_blasting.json",
    "data/etherology/recipes/ebony_block.json",
    "data/etherology/recipes/ebony_ingot_from_ebony_block.json",
    "data/etherology/recipes/ethril_block.json",
    "data/etherology/recipes/ethril_ingot_from_ethril_block.json",
    "data/etherology/recipes/forest_lantern_crumb.json",
    "data/etherology/recipes/forest_lantern_crumb_from_campfire.json",
    "data/etherology/recipes/forest_lantern_crumb_from_smoking.json",
    "data/etherology/recipes/leather.json",
    "data/etherology/recipes/raw_azel.json",
    "data/etherology/tags/blocks/peach_logs.json",
    "data/minecraft/tags/blocks/beacon_base_blocks.json",
    "data/minecraft/tags/blocks/mineable/hoe.json",
    "data/minecraft/tags/blocks/mineable/pickaxe.json",
    "data/minecraft/tags/blocks/needs_iron_tool.json",
    "data/minecraft/tags/blocks/needs_stone_tool.json",
    "data/minecraft/tags/blocks/slabs.json",
    "data/minecraft/tags/blocks/stairs.json",
    "data/minecraft/tags/enchantment/non_treasure.json",
    "data/minecraft/tags/game_events/vibrations.json",
    "data/minecraft/tags/game_events/warden_can_listen.json",
    "data/minecraft/tags/items/slabs.json",
    "data/minecraft/tags/items/stairs.json",
}
STOP_TIMEOUT_SECONDS = 20
START_STABILITY_SECONDS = 2.0
START_IDENTITY_GRACE_SECONDS = 0.5
MAXIMUM_DOWNLOAD_SIZE = 128 * 1024 * 1024
MAXIMUM_PROCESS_LOG_SIZE = 64 * 1024 * 1024
FORGE_MAIN_CLASS = "cpw.mods.bootstraplauncher.BootstrapLauncher"
FATAL_CLIENT_LOG_MARKERS = (
    "A mod crashed on startup!",
    "Could not find required mod",
    "Missing or unsupported mandatory dependencies",
    "Failed to load complete correctly",
    "Uncaught exception in thread \"main\"",
)


class E2EError(RuntimeError):
    """Reports a fail-closed Forge profile or lifecycle failure."""


@dataclass(frozen=True)
class ResolvedConfiguration:
    """Holds the tracked Forge manifest and its resolved release owners."""

    manifest: dict[str, object]
    properties: dict[str, str]
    artifact_lane: dict[str, object]
    runtime_lane: dict[str, object]
    installer: dict[str, object]
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
    current_path = root
    for part in relative_path.parts:
        current_path /= part
        if current_path.is_symlink():
            raise E2EError(f"The manifest {field_name} resolves through a symlink")
    return root / relative_path


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


def parse_options(content: str) -> dict[str, str]:
    options: dict[str, str] = {}
    for line in content.splitlines():
        name, separator, value = line.partition(":")
        if not separator or not name or name in options:
            raise E2EError("The isolated profile options file is invalid")
        options[name] = value
    return options


def validate_artifact_spec(
    manifest: dict[str, object], properties: dict[str, str], role: str
) -> None:
    artifact = require_object(require_object(manifest, "artifacts"), role)
    expected_fields = {
        "file_name",
        "mod_id",
        "version_property",
        "source",
    }
    if role == "harness":
        expected_fields.add("entrypoint")
    if set(artifact) != expected_fields:
        raise E2EError(f"The {role} artifact object has unexpected fields")
    file_name = safe_leaf_name(artifact.get("file_name"), f"artifacts.{role}.file_name")
    if not file_name.endswith(".jar"):
        raise E2EError(f"The {role} artifact file name is invalid")
    mod_id = safe_leaf_name(artifact.get("mod_id"), f"artifacts.{role}.mod_id")
    if re.fullmatch(r"[a-z][a-z0-9_-]{1,63}", mod_id) is None:
        raise E2EError(f"The {role} artifact mod id is invalid")
    version_property = safe_leaf_name(
        artifact.get("version_property"), f"artifacts.{role}.version_property"
    )
    if version_property not in properties:
        raise E2EError(f"The {role} version property is missing: {version_property}")
    source = require_object(artifact, "source")
    if set(source) != {"kind", "build_file", "task_path", "task_name"}:
        raise E2EError(f"The {role} source has unexpected fields")
    if source.get("kind") != "gradle-remap-task":
        raise E2EError(f"The {role} source must be one Gradle remap task")
    if source.get("build_file") != "forge/build.gradle.kts":
        raise E2EError(f"The {role} source must use forge/build.gradle.kts")
    expected_task_path = EXPECTED_ARTIFACT_TASKS[role]
    if source.get("task_path") != expected_task_path:
        raise E2EError(f"The {role} source must be {expected_task_path}")
    if source.get("task_name") != expected_task_path.rsplit(":", 1)[1]:
        raise E2EError(f"The {role} task name differs from its exact task path")
    if role == "harness":
        entrypoint = artifact.get("entrypoint")
        if not isinstance(entrypoint, str) or re.fullmatch(
            r"[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+",
            entrypoint,
        ) is None:
            raise E2EError("The harness entrypoint is invalid")


def validate_manifest_shape(
    manifest: dict[str, object], properties: dict[str, str]
) -> None:
    if manifest.get("schema") != 1:
        raise E2EError("Unsupported Forge E2E profile manifest schema")
    profile = require_object(manifest, "profile")
    if set(profile) != {"id", "runtime_directory", "game_directory", "launcher_directory"}:
        raise E2EError("The Forge E2E profile object has unexpected fields")
    profile_id = safe_leaf_name(profile.get("id"), "profile.id")
    if re.fullmatch(r"[a-z0-9][a-z0-9.-]+", profile_id) is None:
        raise E2EError("The Forge profile id is not stable lowercase text")
    if profile_id != "etherology-e2e-forge-1.20.1-v15":
        raise E2EError("The Forge profile id differs from the isolated profile contract")
    if profile.get("runtime_directory") != profile_id:
        raise E2EError("The Forge runtime directory must equal its unique profile id")
    if profile.get("game_directory") != "game" or profile.get("launcher_directory") != "launcher":
        raise E2EError("The Forge profile uses unexpected runtime subdirectories")

    release = require_object(manifest, "release")
    if release != {
        "matrix": "release/release-matrix.json",
        "artifact_node": "forge-1.20.1",
    }:
        raise E2EError("The E2E profile must use the exact Forge 1.20.1 release lane")

    launch = require_object(manifest, "launch")
    if set(launch) != {"offline_username", "resolution", "maximum_memory_mb"}:
        raise E2EError("The launch object has unexpected fields")
    if launch.get("offline_username") != "EtherologyForgeE2E":
        raise E2EError("The Forge offline identity changed")
    resolution = require_object(launch, "resolution")
    if resolution != {"width": 960, "height": 540}:
        raise E2EError("The Forge launch resolution must remain 960x540")
    if launch.get("maximum_memory_mb") != 4096:
        raise E2EError("The Forge launch memory must remain 4096 MiB")

    artifacts = require_object(manifest, "artifacts")
    if set(artifacts) != {"lock_file", *ARTIFACT_ROLES}:
        raise E2EError("The artifacts object has unexpected fields")
    if artifacts.get("lock_file") != "forge-artifact-lock.json":
        raise E2EError("The Forge artifact lock file name changed")
    for role in ARTIFACT_ROLES:
        validate_artifact_spec(manifest, properties, role)
    production = require_object(artifacts, "production")
    harness = require_object(artifacts, "harness")
    if production["file_name"] == harness["file_name"]:
        raise E2EError("The local artifacts have duplicate target names")
    if production["mod_id"] == harness["mod_id"]:
        raise E2EError("The local artifacts have duplicate root mod ids")

    evidence = require_object(manifest, "evidence")
    if set(evidence) != {"directory", "capture", "scenarios"}:
        raise E2EError("The evidence object has unexpected fields")
    if evidence.get("directory") != "evidence":
        raise E2EError("The Forge evidence directory must remain runtime-local evidence")
    capture = require_object(evidence, "capture")
    if capture != {
        "kind": "composed-minecraft-framebuffer",
        "width": 1920,
        "height": 1080,
    }:
        raise E2EError("The Forge evidence capture contract is invalid")
    scenarios = require_list(evidence, "scenarios")
    if scenarios != [
        "ethereal-storage",
        "ethereal-channel",
        "forest-lantern",
        "attrahite-block-registry",
    ]:
        raise E2EError(
            "The Forge harness must expose storage, channel, Forest Lantern, "
            "then Attrahite block registry"
        )

    directories = require_list(manifest, "profile_directories")
    if len(directories) != len(set(str(value) for value in directories)):
        raise E2EError("The profile directory inventory contains duplicates")
    for directory in directories:
        safe_leaf_name(directory, "profile_directories entry")
    if "mods" not in directories:
        raise E2EError("The Forge profile directory inventory has no mods directory")
    raw_options = require_list(manifest, "options")
    if not raw_options or not all(isinstance(value, str) for value in raw_options):
        raise E2EError("The controlled options list is invalid")
    parse_options("\n".join(str(value) for value in raw_options) + "\n")

    dependencies = require_list(manifest, "dependencies")
    if len(dependencies) != 2:
        raise E2EError("Forge runtime must have exactly two external mod dependencies")
    discovered_mod_ids: set[str] = set()
    discovered_file_names: set[str] = set()
    for raw_dependency in dependencies:
        if not isinstance(raw_dependency, dict) or set(raw_dependency) != {
            "file_name",
            "mod_id",
            "version_property",
            "url",
            "size",
            "sha256",
        }:
            raise E2EError("The manifest contains an invalid dependency entry")
        file_name = safe_leaf_name(raw_dependency.get("file_name"), "dependency.file_name")
        mod_id = safe_leaf_name(raw_dependency.get("mod_id"), "dependency.mod_id")
        version_property = safe_leaf_name(
            raw_dependency.get("version_property"), "dependency.version_property"
        )
        if not file_name.endswith(".jar") or file_name in discovered_file_names:
            raise E2EError("A dependency file name is invalid or duplicated")
        if mod_id in discovered_mod_ids:
            raise E2EError("A dependency root mod id is duplicated")
        if version_property not in properties:
            raise E2EError(f"The dependency version property is missing: {version_property}")
        expected_version = properties[version_property]
        url = raw_dependency.get("url")
        parsed_url = urllib.parse.urlparse(str(url))
        if (
            not isinstance(url, str)
            or parsed_url.scheme != "https"
            or not parsed_url.hostname
            or parsed_url.username is not None
            or parsed_url.password is not None
            or parsed_url.fragment
            or expected_version not in file_name
            or expected_version not in url
        ):
            raise E2EError(f"The dependency source is unsafe or unpinned: {file_name}")
        size = raw_dependency.get("size")
        if type(size) is not int or int(size) <= 0 or int(size) > MAXIMUM_DOWNLOAD_SIZE:
            raise E2EError(f"The dependency size is invalid: {file_name}")
        validate_hex_digest(raw_dependency.get("sha256"), f"dependency {file_name}")
        discovered_file_names.add(file_name)
        discovered_mod_ids.add(mod_id)
    if discovered_mod_ids != EXPECTED_DEPENDENCY_MOD_IDS:
        raise E2EError("Forge runtime dependency inventory must be Architectury and GeckoLib")
    forbidden_mod_ids = require_list(manifest, "forbidden_mod_ids")
    if not all(isinstance(value, str) and value for value in forbidden_mod_ids):
        raise E2EError("The forbidden mod id list is invalid")
    required_mod_ids = discovered_mod_ids | {str(production["mod_id"]), str(harness["mod_id"])}
    overlap = required_mod_ids.intersection(str(value) for value in forbidden_mod_ids)
    if overlap:
        raise E2EError(f"Required mods are forbidden: {sorted(overlap)}")


def load_configuration(
    manifest_path: Path = MANIFEST_PATH,
    repository_root: Path = REPOSITORY_ROOT,
) -> ResolvedConfiguration:
    resolved_repository_root = repository_root.resolve()
    expected_manifest_path = resolved_repository_root / PROFILE_MANIFEST_RELATIVE_PATH
    normalized_manifest_path = manifest_path.absolute()
    if normalized_manifest_path != expected_manifest_path:
        raise E2EError(
            "The Forge E2E profile must be loaded from its tracked repository path"
        )
    if normalized_manifest_path.is_symlink() or not normalized_manifest_path.is_file():
        raise E2EError(
            f"The Forge E2E profile manifest is missing or linked: {normalized_manifest_path}"
        )
    manifest = load_json_object(manifest_path, "Forge E2E profile manifest")
    properties = parse_gradle_properties(resolved_repository_root / "gradle.properties")
    validate_manifest_shape(manifest, properties)
    release = require_object(manifest, "release")
    matrix_path = safe_repository_path(
        resolved_repository_root,
        release["matrix"],
        "release.matrix",
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
    if artifact_lane.get("loader") != "forge" or runtime_lane.get("loader") != "forge":
        raise E2EError("The profile did not resolve the Forge release lane")
    if artifact_lane.get("artifact_version") != runtime_lane.get("runtime_version"):
        raise E2EError("The Forge artifact and runtime Minecraft versions differ")
    if artifact_lane.get("java") != 17 or runtime_lane.get("java") != 17:
        raise E2EError("Forge 1.20.1 requires Java 17")
    if runtime_lane.get("runtime_version") != properties.get("minecraft_version_1_20_1"):
        raise E2EError("The release runtime differs from minecraft_version_1_20_1")
    if runtime_lane.get("loader_version") != properties.get("forge_version_1_20_1"):
        raise E2EError("The release runtime differs from forge_version_1_20_1")
    if runtime_lane.get("jar_sha256") != "from:artifact-manifest":
        raise E2EError("The Forge runtime must acquire its JAR hash from artifact staging")
    architectury = runtime_lane.get("architectury")
    if not isinstance(architectury, dict) or architectury != {
        "kind": "maven",
        "version": properties.get("architectury_api_version_1_20_1"),
    }:
        raise E2EError("The Forge release lane differs from the Architectury pin")
    project = matrix.get("project")
    production = artifact_spec_from_manifest(manifest, "production")
    harness = artifact_spec_from_manifest(manifest, "harness")
    if not isinstance(project, dict):
        raise E2EError("The release matrix project object is invalid")
    if project.get("mod_id") != production.get("mod_id"):
        raise E2EError("The production mod id differs from the release matrix")
    if project.get("mod_version_property") != production.get("version_property"):
        raise E2EError("The production version property differs from the release matrix")
    if harness.get("version_property") != production.get("version_property"):
        raise E2EError("The production and harness version properties differ")
    installer_key = runtime_lane.get("installer")
    installers = matrix.get("installers")
    if not isinstance(installer_key, str) or not isinstance(installers, dict):
        raise E2EError("The Forge release installer reference is invalid")
    installer = installers.get(installer_key)
    if not isinstance(installer, dict) or set(installer) != {"url", "sha256"}:
        raise E2EError("The Forge release installer is missing or malformed")
    installer_url = installer.get("url")
    parsed_installer_url = urllib.parse.urlparse(str(installer_url))
    if (
        not isinstance(installer_url, str)
        or parsed_installer_url.scheme != "https"
        or parsed_installer_url.hostname != "maven.minecraftforge.net"
        or parsed_installer_url.username is not None
        or parsed_installer_url.password is not None
        or parsed_installer_url.fragment
        or str(runtime_lane["loader_version"]) not in installer_url
    ):
        raise E2EError("The Forge installer URL is unsafe or does not encode the lane")
    validate_hex_digest(installer.get("sha256"), "release Forge installer")
    return ResolvedConfiguration(
        manifest,
        properties,
        artifact_lane,
        runtime_lane,
        installer,
        resolved_repository_root,
        normalized_manifest_path,
    )


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


def artifact_spec(configuration: ResolvedConfiguration, role: str) -> dict[str, object]:
    return artifact_spec_from_manifest(configuration.manifest, role)


def dependency_specs(configuration: ResolvedConfiguration) -> list[dict[str, object]]:
    return [
        value
        for value in require_list(configuration.manifest, "dependencies")
        if isinstance(value, dict)
    ]


def scenario_ids(configuration: ResolvedConfiguration) -> list[str]:
    return [
        str(value)
        for value in require_list(require_object(configuration.manifest, "evidence"), "scenarios")
    ]


def resolve_scenario_id(
    configuration: ResolvedConfiguration, configured_scenario_id: str | None
) -> str:
    scenarios = scenario_ids(configuration)
    scenario_id = scenarios[0] if configured_scenario_id is None else configured_scenario_id
    if scenario_id in scenarios:
        return scenario_id
    raise E2EError(f"Unsupported Forge E2E scenario {scenario_id!r}; expected {scenarios}")


def runtime_root(
    configuration: ResolvedConfiguration, state_root: Path = STATE_ROOT
) -> Path:
    return state_root / "runtimes" / str(profile_spec(configuration)["runtime_directory"])


def game_directory(configuration: ResolvedConfiguration, root: Path | None = None) -> Path:
    return (root or runtime_root(configuration)) / str(profile_spec(configuration)["game_directory"])


def launcher_directory(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> Path:
    return (root or runtime_root(configuration)) / str(
        profile_spec(configuration)["launcher_directory"]
    )


def evidence_root(configuration: ResolvedConfiguration, root: Path | None = None) -> Path:
    evidence = require_object(configuration.manifest, "evidence")
    return (root or runtime_root(configuration)) / str(evidence["directory"])


def process_state_path(
    configuration: ResolvedConfiguration, state_root: Path = STATE_ROOT
) -> Path:
    return state_root / f"{profile_spec(configuration)['id']}-current.json"


def launch_attempt_path(
    configuration: ResolvedConfiguration, state_root: Path = STATE_ROOT
) -> Path:
    return state_root / f"{profile_spec(configuration)['id']}-start.attempted"


def profile_directories(configuration: ResolvedConfiguration) -> list[str]:
    return [str(value) for value in require_list(configuration.manifest, "profile_directories")]


def options_text(configuration: ResolvedConfiguration) -> str:
    return "\n".join(str(value) for value in require_list(configuration.manifest, "options")) + "\n"


def ensure_directory_is_not_linked(path: Path, description: str) -> None:
    if path.is_symlink():
        raise E2EError(f"{description} must not be a symlink: {path}")
    if path.exists() and not path.is_dir():
        raise E2EError(f"{description} must be a directory: {path}")


def ensure_owned_state_roots(state_root: Path = STATE_ROOT) -> None:
    ensure_directory_is_not_linked(state_root, "Forge E2E state root")
    ensure_directory_is_not_linked(state_root / "runtimes", "Forge E2E runtimes root")


def write_private_text_exclusive(path: Path, content: str) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        handle.write(content)
        handle.flush()
        os.fsync(handle.fileno())
    directory_descriptor = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory_descriptor)
    finally:
        os.close(directory_descriptor)


def require_unattempted_profile(
    configuration: ResolvedConfiguration,
    state_root: Path = STATE_ROOT,
) -> None:
    ensure_owned_state_roots(state_root)
    attempt = launch_attempt_path(configuration, state_root)
    if attempt.exists() or attempt.is_symlink():
        raise E2EError(
            "The Forge E2E profile already has a start attempt and is consumed: "
            f"{attempt}"
        )


def reserve_launch_attempt(
    configuration: ResolvedConfiguration,
    scenario_id: str,
    state_root: Path = STATE_ROOT,
) -> Path:
    require_unattempted_profile(configuration, state_root)
    attempt = launch_attempt_path(configuration, state_root)
    content = (
        f"profile_id={profile_spec(configuration)['id']}\n"
        f"scenario={scenario_id}\n"
        f"controller_pid={os.getpid()}\n"
    )
    try:
        write_private_text_exclusive(attempt, content)
    except FileExistsError as exception:
        raise E2EError(
            "The Forge E2E profile already has a start attempt and is consumed: "
            f"{attempt}"
        ) from exception
    except OSError as exception:
        raise E2EError(
            f"Cannot durably reserve the Forge E2E start attempt: {exception}"
        ) from exception
    return attempt


def write_json_atomic(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary_path = path.parent / f".{path.name}.{os.getpid()}.tmp"
    descriptor = os.open(temporary_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(value, handle, indent=2, sort_keys=True)
            handle.write("\n")
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def profile_descriptor(configuration: ResolvedConfiguration) -> dict[str, object]:
    manifest_path = configuration.profile_manifest_path
    if manifest_path.is_symlink() or not manifest_path.is_file():
        raise E2EError(
            f"The Forge E2E profile manifest is missing or linked: {manifest_path}"
        )
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "managed_by": MANAGED_BY,
        "profile_manifest": {
            "path": PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
            "size": manifest_path.stat().st_size,
            "sha256": sha256_file(manifest_path),
        },
        "isolation": {
            "scope": "repository-owned-ignored-state",
            "source_profiles": [],
        },
        "release": {
            "artifact_node": configuration.artifact_lane["artifact_node"],
            "minecraft_version": configuration.runtime_lane["runtime_version"],
            "loader": "forge",
            "loader_version": configuration.runtime_lane["loader_version"],
            "java": 17,
            "installer_sha256": configuration.installer["sha256"],
        },
        "dependencies": [
            {
                "file_name": dependency["file_name"],
                "mod_id": dependency["mod_id"],
                "version_property": dependency["version_property"],
                "url": dependency["url"],
                "size": dependency["size"],
                "sha256": dependency["sha256"],
            }
            for dependency in dependency_specs(configuration)
        ],
    }


def evidence_descriptor(configuration: ResolvedConfiguration) -> dict[str, object]:
    evidence = require_object(configuration.manifest, "evidence")
    capture = require_object(evidence, "capture")
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "managed_by": MANAGED_BY,
        "artifact_node": configuration.artifact_lane["artifact_node"],
        "loader": "forge",
        "java": 17,
        "scenarios": scenario_ids(configuration),
        "capture": {
            "kind": capture["kind"],
            "width": capture["width"],
            "height": capture["height"],
        },
    }


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha1_file(path: Path) -> str:
    digest = hashlib.sha1()
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


def copy_response(response: BinaryIO, handle: BinaryIO, maximum_size: int) -> int:
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
    request = urllib.request.Request(url, headers={"User-Agent": "Etherology-Forge-E2E/1"})
    maximum_size = expected_size if expected_size is not None else MAXIMUM_DOWNLOAD_SIZE
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
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
    except (E2EError, OSError):
        destination.unlink(missing_ok=True)
        raise


def verify_launcher_library() -> None:
    local_python_root = STATE_ROOT / "python"
    if local_python_root.is_symlink():
        raise E2EError(f"Local E2E Python dependency root is linked: {local_python_root}")
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
            f"minecraft-launcher-lib must be {EXPECTED_LAUNCHER_LIBRARY_VERSION}, "
            f"found {actual_version}"
        )


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


def resolve_java_17() -> Path:
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
        resolved = candidate.resolve(strict=False)
        if resolved in checked:
            continue
        checked.add(resolved)
        if java_major_version(resolved) == 17:
            return resolved
    raise E2EError(
        "No Java 17 runtime was found outside game profiles; set "
        f"{JAVA_OVERRIDE_ENVIRONMENT_VARIABLE} to a Java 17 executable"
    )


def forge_version_id(configuration: ResolvedConfiguration) -> str:
    loader_version = str(configuration.runtime_lane["loader_version"])
    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    prefix = f"{minecraft_version}-"
    if not loader_version.startswith(prefix):
        raise E2EError("The Forge loader coordinate does not begin with Minecraft version")
    forge_version = loader_version.removeprefix(prefix)
    if re.fullmatch(r"[0-9]+(?:\.[0-9]+)+", forge_version) is None:
        raise E2EError("The Forge loader coordinate has an unsafe Forge version")
    return f"{minecraft_version}-forge-{forge_version}"


def installer_file_name(configuration: ResolvedConfiguration) -> str:
    url = str(configuration.installer["url"])
    return safe_leaf_name(
        PurePosixPath(urllib.parse.urlparse(url).path).name,
        "release installer file name",
    )


def installer_path(configuration: ResolvedConfiguration, root: Path | None = None) -> Path:
    return launcher_directory(configuration, root) / "installers" / installer_file_name(
        configuration
    )


def forge_version_metadata_path(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> Path:
    version = forge_version_id(configuration)
    return launcher_directory(configuration, root) / "versions" / version / f"{version}.json"


def read_forge_metadata(path: Path) -> dict[str, object]:
    if not path.is_file() or path.is_symlink():
        raise E2EError(f"Forge mod JAR is missing or linked: {path}")
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                raise E2EError(f"Forge mod JAR contains duplicate archive entries: {path}")
            raw_metadata = archive.read("META-INF/mods.toml").decode("utf-8")
    except (OSError, KeyError, UnicodeDecodeError, zipfile.BadZipFile) as exception:
        raise E2EError(f"Cannot inspect Forge mod metadata in {path}: {exception}") from exception
    try:
        metadata = tomllib.loads(raw_metadata)
    except tomllib.TOMLDecodeError as exception:
        raise E2EError(f"Forge mod metadata is invalid in {path}: {exception}") from exception
    if not isinstance(metadata, dict):
        raise E2EError(f"Forge mod metadata is not an object in {path}")
    mods = metadata.get("mods")
    if not isinstance(mods, list) or not mods:
        raise E2EError(f"Forge mod metadata has no root mods in {path}")
    for mod in mods:
        if (
            not isinstance(mod, dict)
            or not isinstance(mod.get("modId"), str)
            or not isinstance(mod.get("version"), str)
        ):
            raise E2EError(f"Forge root mod metadata is malformed in {path}")
    return metadata


def root_mods(metadata: dict[str, object]) -> dict[str, dict[str, object]]:
    raw_mods = metadata.get("mods")
    if not isinstance(raw_mods, list):
        raise E2EError("Forge metadata has no root mod inventory")
    mods: dict[str, dict[str, object]] = {}
    for raw_mod in raw_mods:
        if not isinstance(raw_mod, dict):
            raise E2EError("Forge metadata contains an invalid root mod")
        mod_id = str(raw_mod.get("modId"))
        if mod_id in mods:
            raise E2EError(f"Forge metadata contains duplicate root mod id {mod_id}")
        mods[mod_id] = raw_mod
    return mods


def mod_dependencies(
    metadata: dict[str, object], owner_mod_id: str
) -> dict[str, dict[str, object]]:
    raw_dependencies = metadata.get("dependencies")
    if not isinstance(raw_dependencies, dict):
        raise E2EError(f"Forge metadata has no dependency table for {owner_mod_id}")
    owner_dependencies = raw_dependencies.get(owner_mod_id)
    if not isinstance(owner_dependencies, list):
        raise E2EError(f"Forge metadata has no dependencies for {owner_mod_id}")
    dependencies: dict[str, dict[str, object]] = {}
    for raw_dependency in owner_dependencies:
        if not isinstance(raw_dependency, dict) or not isinstance(
            raw_dependency.get("modId"), str
        ):
            raise E2EError(f"Forge metadata has an invalid dependency for {owner_mod_id}")
        mod_id = str(raw_dependency["modId"])
        if mod_id in dependencies:
            raise E2EError(f"Forge metadata has duplicate dependency {mod_id}")
        dependencies[mod_id] = raw_dependency
    return dependencies


def verify_dependency_jar(
    configuration: ResolvedConfiguration,
    path: Path,
    dependency: dict[str, object],
) -> set[str]:
    verify_exact_file(
        path,
        str(dependency["sha256"]),
        int(dependency["size"]),
        f"Pinned dependency {dependency['mod_id']}",
    )
    metadata = read_forge_metadata(path)
    mods = root_mods(metadata)
    expected_mod_id = str(dependency["mod_id"])
    if set(mods) != {expected_mod_id}:
        raise E2EError(
            f"Pinned dependency has unexpected root mod inventory: {sorted(mods)}"
        )
    expected_version = configuration.properties[str(dependency["version_property"])]
    if mods[expected_mod_id]["version"] != expected_version:
        raise E2EError(
            f"Pinned dependency {expected_mod_id} has version "
            f"{mods[expected_mod_id]['version']}, expected {expected_version}"
        )
    return set(mods)


def unique_gradle_string_setting(body: str, setting: str, task_name: str) -> str:
    matches = re.findall(
        rf'(?m)^\s*{re.escape(setting)}\.set\("([^"\r\n]*)"\)\s*$', body
    )
    if len(matches) != 1:
        raise E2EError(f"Gradle task {task_name} must set {setting} once to literal text")
    return matches[0]


def gradle_remap_task_body(build_file: Path, task_name: str) -> str:
    try:
        content = build_file.read_text(encoding="utf-8")
    except OSError as exception:
        raise E2EError(f"Cannot read Forge build configuration {build_file}") from exception
    pattern = re.compile(
        rf'tasks\.register<RemapJarTask>\("{re.escape(task_name)}"\)\s*\{{'
        rf'(?P<body>.*?)\n    \}}',
        re.DOTALL,
    )
    matches = [match.group("body") for match in pattern.finditer(content)]
    if len(matches) != 1:
        raise E2EError(f"Expected one RemapJarTask named {task_name} in {build_file}")
    return matches[0]


def artifact_source_path(configuration: ResolvedConfiguration, role: str) -> Path:
    artifact = artifact_spec(configuration, role)
    source = require_object(artifact, "source")
    build_file = safe_repository_path(
        configuration.repository_root, source["build_file"], f"{role} source build_file"
    )
    task_name = str(source["task_name"])
    if source["task_path"] != EXPECTED_ARTIFACT_TASKS[role]:
        raise E2EError(f"The {role} task path changed after configuration validation")
    body = gradle_remap_task_body(build_file, task_name)
    archive_base_name = unique_gradle_string_setting(
        body, "archiveBaseName", task_name
    ).replace("$minecraftVersion", str(configuration.runtime_lane["runtime_version"]))
    archive_classifier = unique_gradle_string_setting(body, "archiveClassifier", task_name)
    if len(
        re.findall(
            r"(?m)^\s*archiveVersion\.set\(project\.version\.toString\(\)\)\s*$",
            body,
        )
    ) != 1:
        raise E2EError(f"Gradle task {task_name} must derive archiveVersion from project.version")
    destination_matches = re.findall(
        r'(?m)^\s*destinationDirectory\.set\('
        r'layout\.buildDirectory\.dir\("([^"\r\n]+)"\)\)\s*$',
        body,
    )
    if len(destination_matches) != 1:
        raise E2EError(f"Gradle task {task_name} must use one build-relative destination")
    destination = Path(destination_matches[0])
    if destination.is_absolute() or not destination.parts or ".." in destination.parts:
        raise E2EError(f"Gradle task {task_name} has an unsafe destination")
    if not archive_base_name or re.fullmatch(r"[A-Za-z0-9 ._+\-]+", archive_base_name) is None:
        raise E2EError(f"Gradle task {task_name} has an unsafe archive base name")
    if re.fullmatch(r"[A-Za-z0-9._+\-]*", archive_classifier) is None:
        raise E2EError(f"Gradle task {task_name} has an unsafe archive classifier")
    if role == "production":
        if archive_classifier != "e2e-under-test" or (
            '"Etherology-E2E-Only"' not in body or '"true"' not in body
        ):
            raise E2EError("The production-under-test task lost its E2E-only identity")
    elif archive_classifier or "addNestedDependencies.set(false)" not in body:
        raise E2EError("The Forge harness task must be unclassified and unnested")
    version = configuration.properties[str(artifact["version_property"])]
    classifier_suffix = f"-{archive_classifier}" if archive_classifier else ""
    file_name = f"{archive_base_name}-{version}{classifier_suffix}.jar"
    raw_path = (
        build_file.parent
        / "versions"
        / str(configuration.runtime_lane["runtime_version"])
        / "build"
        / destination
        / file_name
    )
    relative_path = raw_path.relative_to(configuration.repository_root)
    return safe_repository_path(
        configuration.repository_root, relative_path.as_posix(), f"{role} artifact JAR"
    )


def artifact_source_relative_path(configuration: ResolvedConfiguration, role: str) -> str:
    return artifact_source_path(configuration, role).relative_to(
        configuration.repository_root
    ).as_posix()


def artifact_target_path(
    configuration: ResolvedConfiguration, role: str, root: Path | None = None
) -> Path:
    return game_directory(configuration, root) / "mods" / str(
        artifact_spec(configuration, role)["file_name"]
    )


def artifact_lock_path(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> Path:
    return (root or runtime_root(configuration)) / str(artifacts_spec(configuration)["lock_file"])


def archive_entries(path: Path) -> set[str]:
    try:
        with zipfile.ZipFile(path) as archive:
            entries = archive.namelist()
    except (OSError, zipfile.BadZipFile) as exception:
        raise E2EError(f"Cannot inspect Forge artifact {path}: {exception}") from exception
    if len(entries) != len(set(entries)):
        raise E2EError(f"Forge artifact contains duplicate archive entries: {path}")
    return set(entries)


def verify_production_artifact_metadata(
    configuration: ResolvedConfiguration, path: Path
) -> tuple[str, int, set[str]]:
    metadata = read_forge_metadata(path)
    mods = root_mods(metadata)
    production = artifact_spec(configuration, "production")
    expected_mod_id = str(production["mod_id"])
    if set(mods) != {expected_mod_id}:
        raise E2EError(f"Production JAR root mod inventory changed: {sorted(mods)}")
    expected_version = configuration.properties[str(production["version_property"])]
    if mods[expected_mod_id]["version"] != expected_version:
        raise E2EError("Production JAR version differs from the tracked mod version")
    dependencies = mod_dependencies(metadata, expected_mod_id)
    if set(dependencies) != {"forge", "minecraft", "architectury", "geckolib"}:
        raise E2EError(
            f"Production Forge dependency inventory changed: {sorted(dependencies)}"
        )
    expected_ranges = {
        "forge": configuration.artifact_lane["metadata"]["loader"],
        "minecraft": configuration.artifact_lane["metadata_range"],
        "architectury": configuration.artifact_lane["metadata"]["architectury"],
        "geckolib": f"[{configuration.properties['geckolib_forge_version_1_20_1']},5)",
    }
    for mod_id, expected_range in expected_ranges.items():
        dependency = dependencies[mod_id]
        if dependency.get("mandatory") is not True:
            raise E2EError(f"Production dependency {mod_id} is not mandatory")
        if dependency.get("versionRange") != expected_range:
            raise E2EError(f"Production dependency {mod_id} has the wrong version range")
        if dependency.get("side") != "BOTH":
            raise E2EError(f"Production dependency {mod_id} is not required on both sides")
    entries = archive_entries(path)
    if "ru/feytox/etherology/forge/EtherologyForge.class" not in entries:
        raise E2EError("Production JAR has no Forge entrypoint class")
    if any(name.startswith("dev/theplumteam/etherology/e2e/") for name in entries):
        raise E2EError("Production JAR contains E2E harness classes")
    data_entries = {
        name for name in entries if name.startswith("data/") and not name.endswith("/")
    }
    if data_entries != EXPECTED_PRODUCTION_DATA_ENTRIES:
        raise E2EError(
            "Production Forge server-data inventory changed: "
            f"expected={sorted(EXPECTED_PRODUCTION_DATA_ENTRIES)}, "
            f"actual={sorted(data_entries)}"
        )
    try:
        with zipfile.ZipFile(path) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    except (OSError, KeyError, UnicodeDecodeError, zipfile.BadZipFile) as exception:
        raise E2EError("Production JAR has no readable manifest") from exception
    normalized_manifest = manifest.replace("\r\n", "\n")
    if "Etherology-E2E-Only: true\n" not in normalized_manifest:
        raise E2EError("Production JAR is not marked E2E-only")
    return sha256_file(path), path.stat().st_size, set(mods)


def verify_harness_artifact_metadata(
    configuration: ResolvedConfiguration, path: Path
) -> tuple[str, int, set[str]]:
    metadata = read_forge_metadata(path)
    mods = root_mods(metadata)
    harness = artifact_spec(configuration, "harness")
    expected_mod_id = str(harness["mod_id"])
    if set(mods) != {expected_mod_id}:
        raise E2EError(f"Harness JAR root mod inventory changed: {sorted(mods)}")
    expected_version = configuration.properties[str(harness["version_property"])]
    if mods[expected_mod_id]["version"] != expected_version:
        raise E2EError("Harness JAR version differs from the tracked mod version")
    dependencies = mod_dependencies(metadata, expected_mod_id)
    if set(dependencies) != {"forge", "minecraft", "etherology"}:
        raise E2EError(f"Harness dependency inventory changed: {sorted(dependencies)}")
    expected_ranges = {
        "forge": configuration.artifact_lane["metadata"]["loader"],
        "minecraft": configuration.artifact_lane["metadata_range"],
        "etherology": f"[{expected_version}]",
    }
    for mod_id, expected_range in expected_ranges.items():
        dependency = dependencies[mod_id]
        if dependency.get("mandatory") is not True:
            raise E2EError(f"Harness dependency {mod_id} is not mandatory")
        if dependency.get("versionRange") != expected_range:
            raise E2EError(f"Harness dependency {mod_id} has the wrong version range")
        if dependency.get("side") != "CLIENT":
            raise E2EError(f"Harness dependency {mod_id} is not client-only")
    entries = archive_entries(path)
    if any(name.lower().endswith(".jar") for name in entries):
        raise E2EError("Harness JAR contains a nested JAR")
    expected_entry = str(harness["entrypoint"]).replace(".", "/") + ".class"
    if expected_entry not in entries:
        raise E2EError("Harness JAR has no configured Forge entrypoint class")
    class_entries = {name for name in entries if name.endswith(".class")}
    harness_prefix = expected_entry.rsplit("/", 1)[0] + "/"
    outside_classes = sorted(
        name for name in class_entries if not name.startswith(harness_prefix)
    )
    if outside_classes:
        raise E2EError(f"Harness JAR contains classes outside its package: {outside_classes}")
    production_prefix = b"ru/feytox/etherology/"
    try:
        with zipfile.ZipFile(path) as archive:
            for class_entry in class_entries:
                if production_prefix in archive.read(class_entry):
                    raise E2EError(
                        f"Harness class {class_entry} links to production Etherology code"
                    )
    except (OSError, zipfile.BadZipFile) as exception:
        raise E2EError(f"Cannot inspect harness classes in {path}") from exception
    return sha256_file(path), path.stat().st_size, set(mods)


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


def expected_artifact_lock(
    configuration: ResolvedConfiguration,
    inspections: dict[str, tuple[str, int, set[str]]],
) -> dict[str, object]:
    if set(inspections) != set(ARTIFACT_ROLES):
        raise E2EError("Cannot lock an incomplete Forge artifact set")
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "managed_by": MANAGED_BY,
        "artifact_node": configuration.artifact_lane["artifact_node"],
        "artifacts": {
            role: {
                "source_kind": "gradle-remap-task",
                "task_path": require_object(artifact_spec(configuration, role), "source")[
                    "task_path"
                ],
                "source_relative_path": artifact_source_relative_path(configuration, role),
                "target_file": artifact_spec(configuration, role)["file_name"],
                "mod_id": artifact_spec(configuration, role)["mod_id"],
                "version": configuration.properties[
                    str(artifact_spec(configuration, role)["version_property"])
                ],
                "size": inspection[1],
                "sha256": inspection[0],
                "root_mod_ids": sorted(inspection[2]),
            }
            for role, inspection in inspections.items()
        },
    }


def load_artifact_lock(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> dict[str, object] | None:
    path = artifact_lock_path(configuration, root)
    if path.is_symlink():
        raise E2EError(f"Forge artifact lock must not be a symlink: {path}")
    if not path.exists():
        return None
    lock = load_json_object(path, "Forge artifact lock")
    if lock.get("schema") != 1 or lock.get("managed_by") != MANAGED_BY:
        raise E2EError(f"Unsupported or foreign Forge artifact lock: {path}")
    return lock


def verify_locked_artifacts(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
    verify_source: bool = True,
) -> set[str]:
    lock = load_artifact_lock(configuration, root)
    if lock is None:
        raise E2EError("The Forge production and harness JARs are not both staged")
    locked_artifacts = lock.get("artifacts")
    if not isinstance(locked_artifacts, dict) or set(locked_artifacts) != set(
        ARTIFACT_ROLES
    ):
        raise E2EError("The Forge artifact lock does not name the exact artifact set")
    inspections: dict[str, tuple[str, int, set[str]]] = {}
    discovered_mod_ids: set[str] = set()
    for role in ARTIFACT_ROLES:
        locked_entry = locked_artifacts.get(role)
        if not isinstance(locked_entry, dict):
            raise E2EError(f"The Forge artifact lock has no {role} entry")
        expected_sha256 = validate_hex_digest(
            locked_entry.get("sha256"), f"{role} artifact lock"
        )
        size = locked_entry.get("size")
        if type(size) is not int or int(size) <= 0:
            raise E2EError(f"The {role} artifact lock size is invalid")
        target = artifact_target_path(configuration, role, root)
        verify_exact_file(target, expected_sha256, int(size), f"Staged {role} artifact")
        inspection = verify_artifact_metadata(configuration, role, target)
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
        raise E2EError("The Forge artifact lock does not describe its staged JARs exactly")
    return discovered_mod_ids


def verify_profile_marker(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> None:
    target_root = root or runtime_root(configuration)
    if not target_root.is_dir() or target_root.is_symlink():
        raise E2EError(f"Isolated Forge E2E runtime is missing or linked: {target_root}")
    marker_path = target_root / PROFILE_MARKER_NAME
    if not marker_path.is_file() or marker_path.is_symlink():
        raise E2EError(f"Refusing to adopt an unmarked Forge runtime: {target_root}")
    if load_json_object(marker_path, "Forge profile marker") != profile_descriptor(
        configuration
    ):
        raise E2EError(f"Forge runtime marker does not match this profile: {marker_path}")


def verify_evidence_layout(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> None:
    target_root = root or runtime_root(configuration)
    verify_profile_marker(configuration, target_root)
    target_evidence_root = evidence_root(configuration, target_root)
    if not target_evidence_root.is_dir() or target_evidence_root.is_symlink():
        raise E2EError(f"Forge evidence root is missing or linked: {target_evidence_root}")
    marker_path = target_evidence_root / EVIDENCE_MARKER_NAME
    if not marker_path.is_file() or marker_path.is_symlink():
        raise E2EError(f"Refusing to adopt unmarked Forge evidence: {target_evidence_root}")
    if load_json_object(marker_path, "Forge evidence marker") != evidence_descriptor(
        configuration
    ):
        raise E2EError(f"Forge evidence marker does not match: {marker_path}")
    scenarios = scenario_ids(configuration)
    expected_entries = {EVIDENCE_MARKER_NAME, *scenarios}
    actual_entries = {entry.name for entry in target_evidence_root.iterdir()}
    if actual_entries != expected_entries:
        raise E2EError(
            "Forge evidence scenario inventory changed: "
            f"missing={sorted(expected_entries - actual_entries)}, "
            f"unexpected={sorted(actual_entries - expected_entries)}"
        )
    for scenario_id in scenarios:
        scenario_root = target_evidence_root / scenario_id
        if not scenario_root.is_dir() or scenario_root.is_symlink():
            raise E2EError(f"Forge scenario root is missing or linked: {scenario_root}")
        expected_scenario_entries = {"reports", "screenshots"}
        actual_scenario_entries = {entry.name for entry in scenario_root.iterdir()}
        if actual_scenario_entries != expected_scenario_entries:
            raise E2EError(f"Forge evidence layout changed for {scenario_id}")
        for directory_name in expected_scenario_entries:
            directory = scenario_root / directory_name
            if not directory.is_dir() or directory.is_symlink():
                raise E2EError(f"Forge evidence directory is missing or linked: {directory}")


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
            prefix=f".{require_object(configuration.manifest, 'evidence')['directory']}.",
            dir=target_root,
        )
    )
    try:
        write_private_text_exclusive(
            staging_root / EVIDENCE_MARKER_NAME,
            json.dumps(evidence_descriptor(configuration), indent=2, sort_keys=True) + "\n",
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


def inherited_client_jar_paths(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> tuple[Path, Path]:
    launcher_root = launcher_directory(configuration, root)
    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    forge_id = forge_version_id(configuration)
    parent_jar = (
        launcher_root / "versions" / minecraft_version / f"{minecraft_version}.jar"
    )
    child_jar = launcher_root / "versions" / forge_id / f"{forge_id}.jar"
    return parent_jar, child_jar


def verify_vanilla_client_jar(
    configuration: ResolvedConfiguration,
    root: Path | None = None,
) -> tuple[Path, str, int]:
    launcher_root = launcher_directory(configuration, root)
    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    metadata_path = (
        launcher_root / "versions" / minecraft_version / f"{minecraft_version}.json"
    )
    if not metadata_path.is_file() or metadata_path.is_symlink():
        raise E2EError(f"Minecraft version metadata is missing or linked: {metadata_path}")
    metadata = load_json_object(metadata_path, "Minecraft version metadata")
    if metadata.get("id") != minecraft_version:
        raise E2EError("Minecraft version metadata has the wrong id")
    downloads = metadata.get("downloads")
    client = downloads.get("client") if isinstance(downloads, dict) else None
    if not isinstance(client, dict):
        raise E2EError("Minecraft version metadata has no client download descriptor")
    expected_sha1 = client.get("sha1")
    expected_size = client.get("size")
    if (
        not isinstance(expected_sha1, str)
        or re.fullmatch(r"[0-9a-f]{40}", expected_sha1) is None
    ):
        raise E2EError("Minecraft client download SHA-1 is invalid")
    if type(expected_size) is not int or expected_size <= 0:
        raise E2EError("Minecraft client download size is invalid")

    parent_jar, _child_jar = inherited_client_jar_paths(configuration, root)
    if not parent_jar.is_file() or parent_jar.is_symlink():
        raise E2EError(f"Minecraft inherited client JAR is missing or linked: {parent_jar}")
    if parent_jar.stat().st_size != expected_size:
        raise E2EError(f"Minecraft inherited client JAR has an unexpected size: {parent_jar}")
    if sha1_file(parent_jar) != expected_sha1:
        raise E2EError(f"Minecraft inherited client JAR failed SHA-1 validation: {parent_jar}")
    return parent_jar, sha256_file(parent_jar), expected_size


def materialize_inherited_client_jar(
    configuration: ResolvedConfiguration,
    root: Path,
) -> None:
    """Creates the child-named client copy required by Forge's classpath ignore list."""
    parent_jar, parent_sha256, parent_size = verify_vanilla_client_jar(
        configuration,
        root,
    )
    _parent_jar, child_jar = inherited_client_jar_paths(configuration, root)
    if not child_jar.parent.is_dir() or child_jar.parent.is_symlink():
        raise E2EError(f"Forge version directory is missing or linked: {child_jar.parent}")
    if child_jar.exists() or child_jar.is_symlink():
        raise E2EError(f"Refusing to replace a Forge child version JAR: {child_jar}")

    descriptor = os.open(child_jar, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as target:
            with parent_jar.open("rb") as source:
                shutil.copyfileobj(source, target)
        verify_exact_file(
            child_jar,
            parent_sha256,
            parent_size,
            "Forge inherited child version JAR",
        )
    except (E2EError, OSError):
        child_jar.unlink(missing_ok=True)
        raise


def verify_installed_game(
    configuration: ResolvedConfiguration, root: Path | None = None
) -> None:
    launcher_root = launcher_directory(configuration, root)
    if not launcher_root.is_dir() or launcher_root.is_symlink():
        raise E2EError(f"Isolated Forge launcher root is missing or linked: {launcher_root}")
    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    vanilla_root = launcher_root / "versions" / minecraft_version
    vanilla_json = vanilla_root / f"{minecraft_version}.json"
    vanilla_jar = vanilla_root / f"{minecraft_version}.jar"
    for path, description in (
        (vanilla_json, "Minecraft version metadata"),
        (vanilla_jar, "Minecraft client JAR"),
    ):
        if not path.is_file() or path.is_symlink():
            raise E2EError(f"{description} is missing or linked: {path}")
    forge_json = forge_version_metadata_path(configuration, root)
    if not forge_json.is_file() or forge_json.is_symlink():
        raise E2EError(f"Forge version metadata is missing or linked: {forge_json}")
    forge_metadata = load_json_object(forge_json, "Forge version metadata")
    if forge_metadata.get("id") != forge_version_id(configuration):
        raise E2EError("Forge version metadata has the wrong id")
    if forge_metadata.get("inheritsFrom") != minecraft_version:
        raise E2EError("Forge version metadata inherits the wrong Minecraft version")
    if forge_metadata.get("mainClass") != FORGE_MAIN_CLASS:
        raise E2EError("Forge version metadata has an unexpected main class")
    _parent_jar, parent_sha256, parent_size = verify_vanilla_client_jar(
        configuration,
        root,
    )
    _parent_jar, child_jar = inherited_client_jar_paths(configuration, root)
    verify_exact_file(
        child_jar,
        parent_sha256,
        parent_size,
        "Forge inherited child version JAR",
    )
    verify_exact_file(
        installer_path(configuration, root),
        str(configuration.installer["sha256"]),
        None,
        "Pinned Forge installer",
    )


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
        raise E2EError(f"Isolated Forge game directory is missing or linked: {target_game_directory}")
    for relative_directory in profile_directories(configuration):
        directory = target_game_directory / relative_directory
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"Isolated Forge game directory is missing or linked: {directory}")
    options_path = target_game_directory / "options.txt"
    if not options_path.is_file() or options_path.is_symlink():
        raise E2EError(f"Controlled Forge options file is missing or linked: {options_path}")
    try:
        actual_options = parse_options(options_path.read_text(encoding="utf-8"))
    except OSError as exception:
        raise E2EError(f"Cannot read Forge options: {exception}") from exception
    expected_options = parse_options(options_text(configuration))
    changed_options = {
        name: {"expected": value, "actual": actual_options.get(name)}
        for name, value in expected_options.items()
        if actual_options.get(name) != value
    }
    if changed_options:
        raise E2EError(f"Controlled Forge options changed: {changed_options}")
    verify_installed_game(configuration, target_root)

    mods_directory = target_game_directory / "mods"
    dependency_file_names = {str(value["file_name"]) for value in dependency_specs(configuration)}
    artifact_file_names = {
        role: str(artifact_spec(configuration, role)["file_name"])
        for role in ARTIFACT_ROLES
    }
    lock = load_artifact_lock(configuration, target_root)
    if artifact_policy == "required" and lock is None:
        raise E2EError("The Forge local artifacts have not been staged")
    expected_file_names = set(dependency_file_names)
    if lock is not None:
        expected_file_names.update(artifact_file_names.values())
    elif artifact_policy == "ignore":
        actual_names = {entry.name for entry in mods_directory.iterdir()}
        expected_file_names.update(set(artifact_file_names.values()).intersection(actual_names))
    actual_entries = {entry.name for entry in mods_directory.iterdir()}
    if actual_entries != expected_file_names:
        raise E2EError(
            "Isolated Forge root mod file inventory changed: "
            f"missing={sorted(expected_file_names - actual_entries)}, "
            f"unexpected={sorted(actual_entries - expected_file_names)}"
        )
    discovered_mod_ids: set[str] = set()
    for dependency in dependency_specs(configuration):
        discovered_mod_ids.update(
            verify_dependency_jar(
                configuration,
                mods_directory / str(dependency["file_name"]),
                dependency,
            )
        )
    if lock is not None and artifact_policy != "ignore":
        discovered_mod_ids.update(
            verify_locked_artifacts(
                configuration,
                target_root,
                verify_source=artifact_policy == "required",
            )
        )
    forbidden = {
        str(value) for value in require_list(configuration.manifest, "forbidden_mod_ids")
    }
    present_forbidden = forbidden.intersection(discovered_mod_ids)
    if present_forbidden:
        raise E2EError(f"Forbidden mods are present: {sorted(present_forbidden)}")
    expected_root_mod_ids = set(EXPECTED_DEPENDENCY_MOD_IDS)
    if lock is not None and artifact_policy != "ignore":
        expected_root_mod_ids.update(
            str(artifact_spec(configuration, role)["mod_id"]) for role in ARTIFACT_ROLES
        )
    if discovered_mod_ids != expected_root_mod_ids:
        raise E2EError(
            "Forge root mod inventory changed: "
            f"expected={sorted(expected_root_mod_ids)}, actual={sorted(discovered_mod_ids)}"
        )
    return discovered_mod_ids


def install_isolated_game(
    configuration: ResolvedConfiguration, root: Path, java_path: Path
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
            minecraft_version, str(launcher_root)
        )
    except (OSError, RuntimeError, ValueError) as exception:
        raise E2EError(
            f"Cannot install Minecraft {minecraft_version} in the isolated launcher root: "
            f"{exception}"
        ) from exception
    launcher_profiles = launcher_root / "launcher_profiles.json"
    if not launcher_profiles.exists():
        write_private_text_exclusive(launcher_profiles, '{"profiles":{}}\n')
    elif launcher_profiles.is_symlink() or not launcher_profiles.is_file():
        raise E2EError("The isolated Forge launcher profile registry is linked or invalid")
    forge_installer = installer_path(configuration, root)
    verify_exact_file(
        forge_installer,
        str(configuration.installer["sha256"]),
        None,
        "Pinned Forge installer",
    )
    command = [
        str(java_path),
        "-jar",
        str(forge_installer),
        "--installClient",
        str(launcher_root),
    ]
    try:
        completed = subprocess.run(
            command,
            cwd=launcher_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=900,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exception:
        raise E2EError(f"Forge installer did not complete: {exception}") from exception
    if completed.returncode != 0:
        output_tail = "\n".join(completed.stdout.splitlines()[-30:])
        raise E2EError(
            f"Forge installer exited with {completed.returncode}:\n{output_tail}"
        )
    materialize_inherited_client_jar(configuration, root)


def provision_profile(configuration: ResolvedConfiguration) -> bool:
    ensure_owned_state_roots()
    require_unattempted_profile(configuration)
    target_root = runtime_root(configuration)
    if target_root.exists() or target_root.is_symlink():
        verify_profile_marker(configuration, target_root)
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
        (target_launcher_directory / "installers").mkdir(mode=0o700)
        for relative_directory in profile_directories(configuration):
            (target_game_directory / relative_directory).mkdir(mode=0o700)
        write_private_text_exclusive(
            staging_root / PROFILE_MARKER_NAME,
            json.dumps(profile_descriptor(configuration), indent=2, sort_keys=True) + "\n",
        )
        write_private_text_exclusive(
            target_game_directory / "options.txt", options_text(configuration)
        )
        mods_directory = target_game_directory / "mods"
        for dependency in dependency_specs(configuration):
            destination = mods_directory / str(dependency["file_name"])
            download_pinned_file(
                str(dependency["url"]),
                destination,
                str(dependency["sha256"]),
                int(dependency["size"]),
                f"Forge dependency {dependency['mod_id']}",
            )
            verify_dependency_jar(configuration, destination, dependency)
        forge_installer = installer_path(configuration, staging_root)
        download_pinned_file(
            str(configuration.installer["url"]),
            forge_installer,
            str(configuration.installer["sha256"]),
            None,
            "Forge installer",
        )
        install_isolated_game(configuration, staging_root, java_path)
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
        raise E2EError("The isolated Forge runtime already appears to be running")


def stage_artifacts(
    configuration: ResolvedConfiguration,
) -> tuple[bool, dict[str, object]]:
    assert_runtime_not_running(configuration)
    verify_runtime(configuration, artifact_policy="ignore")
    source_paths = {
        role: artifact_source_path(configuration, role) for role in ARTIFACT_ROLES
    }
    if len(set(source_paths.values())) != len(ARTIFACT_ROLES):
        raise E2EError("The Forge production and harness tasks resolve to the same JAR")
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
        role: target.parent / f".{target.name}.{os.getpid()}.tmp"
        for role, target in target_paths.items()
    }
    for temporary_path in temporary_paths.values():
        if temporary_path.exists() or temporary_path.is_symlink():
            raise E2EError(f"Artifact staging path already exists: {temporary_path}")
    try:
        for role in ARTIFACT_ROLES:
            source_sha256, source_size, _root_mod_ids = inspections[role]
            shutil.copyfile(source_paths[role], temporary_paths[role])
            temporary_paths[role].chmod(0o600)
            verify_exact_file(
                temporary_paths[role],
                source_sha256,
                source_size,
                f"Copied Forge {role} artifact",
            )
            if verify_artifact_metadata(
                configuration, role, temporary_paths[role]
            ) != inspections[role]:
                raise E2EError(f"Copied Forge {role} metadata differs from its source")
        for role in ARTIFACT_ROLES:
            source_sha256, source_size, _root_mod_ids = inspections[role]
            verify_exact_file(
                source_paths[role],
                source_sha256,
                source_size,
                f"Current Forge {role} build output",
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
            "token": "offline-etherology-forge-e2e",
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
            forge_version_id(configuration),
            str(launcher_directory(configuration)),
            options,
        )
    except (OSError, RuntimeError, ValueError) as exception:
        raise E2EError(f"Cannot generate the isolated Forge command: {exception}") from exception


def command_option_value(command: list[str], option: str) -> str:
    indexes = [index for index, value in enumerate(command) if value == option]
    if len(indexes) != 1 or indexes[0] + 1 >= len(command):
        raise E2EError(f"Generated command does not contain exactly one {option}")
    return command[indexes[0] + 1]


def verify_launch_command(
    configuration: ResolvedConfiguration,
    command: list[str],
    root: Path | None = None,
    configured_scenario_id: str | None = None,
) -> None:
    if command.count(FORGE_MAIN_CLASS) != 1:
        raise E2EError("Generated command does not launch Forge BootstrapLauncher exactly once")
    expected_scenario = resolve_scenario_id(configuration, configured_scenario_id)
    expected_scenario_argument = f"-D{SCENARIO_PROPERTY_NAME}={expected_scenario}"
    scenario_arguments = [
        value for value in command if value.startswith(f"-D{SCENARIO_PROPERTY_NAME}=")
    ]
    if scenario_arguments != [expected_scenario_argument]:
        raise E2EError("Generated command does not select the exact Forge scenario")
    expected_game_directory = str(game_directory(configuration, root))
    if command_option_value(command, "--gameDir") != expected_game_directory:
        raise E2EError("Generated command does not target the isolated Forge game directory")
    if command_option_value(command, "--version") != forge_version_id(configuration):
        raise E2EError("Generated command does not select the exact Forge version id")
    classpath_indexes = [
        index for index, value in enumerate(command) if value in ("-cp", "-classpath")
    ]
    if len(classpath_indexes) != 1 or classpath_indexes[0] + 1 >= len(command):
        raise E2EError("Generated Forge command does not contain exactly one classpath")
    raw_entries = command[classpath_indexes[0] + 1].split(os.pathsep)
    if not raw_entries or any(not value for value in raw_entries):
        raise E2EError("Generated Forge classpath contains an empty entry")
    launcher_root = launcher_directory(configuration, root)
    if not launcher_root.is_dir() or launcher_root.is_symlink():
        raise E2EError(f"Isolated Forge launcher root is missing or linked: {launcher_root}")
    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    inherited_parent_jar = (
        launcher_root / "versions" / minecraft_version / f"{minecraft_version}.jar"
    )
    inherited_child_jar = (
        launcher_root
        / "versions"
        / forge_version_id(configuration)
        / f"{forge_version_id(configuration)}.jar"
    )
    if raw_entries.count(str(inherited_child_jar)) != 1:
        raise E2EError("Generated Forge classpath does not contain one inherited child JAR")
    if str(inherited_parent_jar) in raw_entries:
        raise E2EError("Generated Forge classpath unexpectedly contains the parent JAR")
    for raw_entry in raw_entries:
        entry = Path(raw_entry)
        try:
            relative_entry = entry.relative_to(launcher_root)
        except ValueError as exception:
            raise E2EError(f"Generated Forge classpath escapes launcher root: {entry}") from exception
        current_path = launcher_root
        for part in relative_entry.parts:
            current_path /= part
            if current_path.is_symlink():
                raise E2EError(f"Generated Forge classpath resolves through a symlink: {entry}")
        if not entry.is_file():
            raise E2EError(f"Generated Forge classpath entry is missing: {entry}")


def verify_environment(
    configuration: ResolvedConfiguration,
    configured_scenario_id: str | None = None,
) -> tuple[Path, list[str]]:
    require_unattempted_profile(configuration)
    verify_runtime(configuration, artifact_policy="required")
    verify_evidence_layout(configuration)
    java_path = resolve_java_17()
    command = generate_command(configuration, java_path, configured_scenario_id)
    verify_launch_command(
        configuration, command, configured_scenario_id=configured_scenario_id
    )
    if not CAFFEINATE_PATH.is_file():
        raise E2EError(f"macOS caffeinate is missing: {CAFFEINATE_PATH}")
    return java_path, command


def process_log_path(state: dict[str, object], state_root: Path = STATE_ROOT) -> Path:
    raw_log_path = state.get("log")
    if not isinstance(raw_log_path, str):
        raise E2EError("Forge process state has no client log path")
    log_path = Path(raw_log_path)
    logs_directory = state_root / "logs"
    if log_path.parent != logs_directory or log_path.name != safe_leaf_name(
        log_path.name, "process log"
    ):
        raise E2EError("Forge process state points outside its isolated log directory")
    ensure_directory_is_not_linked(logs_directory, "Forge E2E logs directory")
    if log_path.is_symlink():
        raise E2EError(f"Forge E2E process log must not be a symlink: {log_path}")
    return log_path


def read_owned_process_state(
    path: Path, state_root: Path = STATE_ROOT
) -> dict[str, object]:
    if path.parent != state_root:
        raise E2EError(f"Forge process state is outside the owned state root: {path}")
    if path.is_symlink() or not path.is_file():
        raise E2EError(f"Forge process state must be one regular file: {path}")
    match = re.fullmatch(
        r"(?P<profile_id>etherology-e2e-forge-[a-z0-9.-]+)-current\.json",
        path.name,
    )
    if match is None:
        raise E2EError(f"Forge process state has an unsafe file name: {path}")
    state = load_json_object(path, "Forge E2E process state")
    profile_id = match.group("profile_id")
    raw_game_directory = state.get("game_directory")
    if not isinstance(raw_game_directory, str):
        raise E2EError("Forge process state has no game directory")
    target_game_directory = Path(raw_game_directory)
    runtime = target_game_directory.parent
    expected_runtimes_root = state_root / "runtimes"
    if (
        state.get("schema") != 1
        or state.get("managed_by") != MANAGED_BY
        or state.get("profile_id") != profile_id
        or type(state.get("pid")) is not int
        or int(state["pid"]) <= 0
        or not isinstance(state.get("version_id"), str)
        or not target_game_directory.is_absolute()
        or target_game_directory.name != "game"
        or runtime.name != profile_id
        or runtime.parent != expected_runtimes_root
    ):
        raise E2EError("Forge process state does not describe one owned runtime")
    for directory, description in (
        (state_root, "Forge E2E state root"),
        (expected_runtimes_root, "Forge E2E runtimes root"),
        (runtime, "Forge E2E runtime"),
        (target_game_directory, "Forge E2E game directory"),
    ):
        if not directory.is_dir() or directory.is_symlink():
            raise E2EError(f"{description} is missing or linked: {directory}")
    marker_path = runtime / PROFILE_MARKER_NAME
    if not marker_path.is_file() or marker_path.is_symlink():
        raise E2EError(f"Forge runtime marker is missing or linked: {marker_path}")
    marker = load_json_object(marker_path, "Forge profile marker")
    isolation = marker.get("isolation")
    if (
        marker.get("schema") != 1
        or marker.get("profile_id") != profile_id
        or marker.get("managed_by") != MANAGED_BY
        or not isinstance(isolation, dict)
        or isolation.get("scope") != "repository-owned-ignored-state"
        or isolation.get("source_profiles") != []
        or not isinstance(marker.get("release"), dict)
        or marker["release"].get("loader") != "forge"
        or marker["release"].get("java") != 17
    ):
        raise E2EError(f"Owned Forge runtime marker is invalid: {marker_path}")
    scenario = state.get("scenario")
    if not isinstance(scenario, str) or re.fullmatch(r"[a-z0-9][a-z0-9-]*", scenario) is None:
        raise E2EError("Forge process state has an unsafe scenario id")
    process_log_path(state, state_root)
    return state


def read_process_state(
    configuration: ResolvedConfiguration,
) -> dict[str, object] | None:
    path = process_state_path(configuration)
    if not path.exists():
        return None
    state = read_owned_process_state(path)
    if (
        state.get("profile_id") != profile_spec(configuration)["id"]
        or state.get("version_id") != forge_version_id(configuration)
        or state.get("game_directory") != str(game_directory(configuration))
        or state.get("scenario") not in scenario_ids(configuration)
    ):
        raise E2EError("Forge process state does not describe this exact profile")
    return state


def owned_process_states(
    state_root: Path = STATE_ROOT,
) -> list[tuple[Path, dict[str, object]]]:
    ensure_owned_state_roots(state_root)
    if not state_root.exists():
        return []
    return [
        (path, read_owned_process_state(path, state_root))
        for path in sorted(state_root.glob("etherology-e2e-forge-*-current.json"))
    ]


def process_exists(pid: int) -> bool:
    completed = subprocess.run(
        ["/bin/ps", "-p", str(pid), "-o", "stat="],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    state = completed.stdout.strip()
    return completed.returncode == 0 and bool(state) and not state.startswith("Z")


def process_matches(pid: int, state: dict[str, object]) -> bool:
    completed = subprocess.run(
        ["/bin/ps", "-ww", "-p", str(pid), "-o", "command="],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    scenario = str(state["scenario"])
    return (
        completed.returncode == 0
        and FORGE_MAIN_CLASS in completed.stdout
        and str(state["version_id"]) in completed.stdout
        and str(state["game_directory"]) in completed.stdout
        and f"-D{SCENARIO_PROPERTY_NAME}={scenario}" in completed.stdout
    )


def clear_stale_and_reject_live_owned_clients(
    state_root: Path = STATE_ROOT,
) -> None:
    stale_paths: list[Path] = []
    live_descriptions: list[str] = []
    for path, state in owned_process_states(state_root):
        pid = int(state["pid"])
        if not process_exists(pid):
            stale_paths.append(path)
            continue
        if not process_matches(pid, state):
            raise E2EError(
                f"Owned Forge state PID {pid} belongs to another process; refusing access"
            )
        live_descriptions.append(f"{state['profile_id']} (PID {pid})")
    for path in stale_paths:
        path.unlink()
    if live_descriptions:
        raise E2EError(
            "A repository-owned Forge E2E client is already running: "
            + ", ".join(live_descriptions)
        )


def find_client_failure_marker(log_content: str) -> str | None:
    return next((value for value in FATAL_CLIENT_LOG_MARKERS if value in log_content), None)


def client_failure_marker(state: dict[str, object]) -> str | None:
    log_path = process_log_path(state)
    if not log_path.is_file():
        raise E2EError(f"Forge E2E process log is missing: {log_path}")
    try:
        if log_path.stat().st_size > MAXIMUM_PROCESS_LOG_SIZE:
            return f"client log exceeded {MAXIMUM_PROCESS_LOG_SIZE} bytes"
        content = log_path.read_text(encoding="utf-8", errors="replace")
    except OSError as exception:
        raise E2EError(f"Cannot inspect Forge E2E log {log_path}: {exception}") from exception
    return find_client_failure_marker(content)


def wait_for_stable_client_start(state: dict[str, object]) -> None:
    pid = int(state["pid"])
    started = time.monotonic()
    deadline = started + START_STABILITY_SECONDS
    identity_deadline = started + START_IDENTITY_GRACE_SECONDS
    identity_observed = False
    while True:
        now = time.monotonic()
        if now >= deadline:
            break
        marker = client_failure_marker(state)
        if marker is not None:
            raise E2EError(f"Forge client failed during startup: {marker}")
        if not process_exists(pid):
            raise E2EError("Forge client exited during startup")
        if process_matches(pid, state):
            identity_observed = True
        elif identity_observed or now >= identity_deadline:
            raise E2EError("Forge client process identity changed during startup")
        time.sleep(0.1)
    if not identity_observed:
        raise E2EError("Forge client identity was not observed during startup")


def stop_owned_process_group(pid: int) -> bool:
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
        "Validated Forge profile: "
        f"{profile_spec(configuration)['id']} / "
        f"Minecraft {configuration.runtime_lane['runtime_version']} / "
        f"Forge {configuration.runtime_lane['loader_version']}"
    )
    print(f"Pinned external Forge mod JARs: {len(dependency_specs(configuration))}")
    print(f"Pinned installer SHA-256: {configuration.installer['sha256']}")
    for role in ARTIFACT_ROLES:
        print(f"Resolved {role}: {artifact_source_relative_path(configuration, role)}")
    print(f"Runtime root: {runtime_root(configuration)}")
    print("External game profiles consulted: 0")
    return 0


def provision_command() -> int:
    configuration = load_configuration()
    created = provision_profile(configuration)
    verb = "Provisioned" if created else "Verified"
    print(f"{verb} repository-owned Forge runtime: {runtime_root(configuration)}")
    print(f"Verified pinned Forge dependencies: {len(dependency_specs(configuration))}")
    print(f"Evidence root: {evidence_root(configuration)}")
    print("Minecraft was not launched; no external game profile was inspected or changed")
    return 0


def stage_command() -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    require_unattempted_profile(configuration)
    staged, lock = stage_artifacts(configuration)
    message = "Staged" if staged else "Verified already-staged"
    print(f"{message} Forge production and harness JARs")
    locked_artifacts = require_object(lock, "artifacts")
    for role in ARTIFACT_ROLES:
        entry = require_object(locked_artifacts, role)
        print(f"{role.title()} SHA-256: {entry['sha256']}")
    print("Minecraft was not launched")
    return 0


def check_command(configured_scenario_id: str | None = None) -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    scenario_id = resolve_scenario_id(configuration, configured_scenario_id)
    java_path, command = verify_environment(configuration, scenario_id)
    print(
        "Ready: "
        f"Minecraft {configuration.runtime_lane['runtime_version']} / "
        f"Forge {configuration.runtime_lane['loader_version']} / Java 17"
    )
    print(f"Runtime root: {runtime_root(configuration)}")
    print(f"Game directory: {game_directory(configuration)}")
    print(f"Version id: {forge_version_id(configuration)}")
    print(f"Java: {java_path}")
    print(f"Scenario: {scenario_id}")
    print(f"Generated argv entries: {len(command)} (command intentionally not displayed)")
    print("External game profiles consulted: 0")
    return 0


def start_command(configured_scenario_id: str | None = None) -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    scenario_id = resolve_scenario_id(configuration, configured_scenario_id)
    clear_stale_and_reject_live_owned_clients()
    java_path, command = verify_environment(configuration, scenario_id)
    assert_runtime_not_running(configuration)
    reserve_launch_attempt(configuration, scenario_id)
    logs_directory = STATE_ROOT / "logs"
    ensure_directory_is_not_linked(logs_directory, "Forge E2E logs directory")
    logs_directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    timestamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    log_path = logs_directory / f"forge-1.20.1-{timestamp}.log"
    log_descriptor = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    log_handle = os.fdopen(log_descriptor, "wb", buffering=0)
    log_handle.write(
        (
            "Etherology repository-owned Forge 1.20.1 E2E client\n"
            f"started_utc={timestamp}\n"
            f"profile_id={profile_spec(configuration)['id']}\n"
            f"scenario={scenario_id}\n"
            f"version_id={forge_version_id(configuration)}\n"
            f"game_directory={game_directory(configuration)}\n"
            f"java={java_path}\n"
            "authentication=deterministic-offline-identity\n\n"
        ).encode("utf-8")
    )
    try:
        process = subprocess.Popen(
            [str(CAFFEINATE_PATH), "-dimsu", *command],
            cwd=game_directory(configuration),
            stdin=subprocess.DEVNULL,
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
    except OSError:
        log_handle.close()
        raise
    log_handle.close()
    state = {
        "schema": 1,
        "managed_by": MANAGED_BY,
        "profile_id": profile_spec(configuration)["id"],
        "pid": process.pid,
        "started_utc": timestamp,
        "scenario": scenario_id,
        "version_id": forge_version_id(configuration),
        "game_directory": str(game_directory(configuration)),
        "log": str(log_path),
    }
    try:
        write_json_atomic(process_state_path(configuration), state)
    except (OSError, TypeError, ValueError):
        stop_owned_process_group(process.pid)
        raise
    try:
        wait_for_stable_client_start(state)
    except E2EError as exception:
        if process_exists(process.pid) and process_matches(process.pid, state):
            stop_owned_process_group(process.pid)
        clear_stale_process_state(configuration)
        raise E2EError(f"{exception}; log: {log_path}") from exception
    print(f"Started isolated Forge {scenario_id} client as PID {process.pid}")
    print(f"Log: {log_path}")
    return 0


def status_command() -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    state = read_process_state(configuration)
    if state is None:
        print("Etherology Forge 1.20.1 E2E client is stopped")
        return 1
    pid = int(state["pid"])
    if not process_exists(pid):
        marker = client_failure_marker(state)
        clear_stale_process_state(configuration)
        description = "stopped" if marker is None else f"failed: {marker}"
        print(f"Forge client {description}; cleared stale state for PID {pid}")
        print(f"Last log: {state['log']}")
        return 1
    if not process_matches(pid, state):
        raise E2EError("Forge state PID belongs to another process; refusing to manage it")
    marker = client_failure_marker(state)
    if marker is not None:
        print(f"Etherology Forge E2E client failed: {marker}")
        print(f"Process PID {pid} remains owned; run stop before another launch")
        return 2
    print(f"Etherology Forge E2E client is running as PID {pid}")
    print(f"Scenario: {state['scenario']}")
    print(f"Log: {state['log']}")
    return 0


def stop_command() -> int:
    ensure_owned_state_roots()
    configuration = load_configuration()
    state = read_process_state(configuration)
    if state is None:
        print("Etherology Forge 1.20.1 E2E client is already stopped")
        return 0
    pid = int(state["pid"])
    if not process_exists(pid):
        clear_stale_process_state(configuration)
        print(f"Removed stale Forge E2E state for PID {pid}")
        return 0
    if not process_matches(pid, state):
        raise E2EError("Forge state PID belongs to another process; refusing to stop it")
    forced = stop_owned_process_group(pid)
    qualifier = "required a forced stop" if forced else "stopped normally"
    print(f"Forge E2E client PID {pid} {qualifier}")
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
                f"Forge owned state PID {pid} belongs to another process; refusing all stops"
            )
        live_states.append((path, state))
    for _path, state in live_states:
        pid = int(state["pid"])
        forced = stop_owned_process_group(pid)
        qualifier = "required a forced stop" if forced else "stopped normally"
        print(f"Owned Forge client {state['profile_id']} PID {pid} {qualifier}")
    for path, _state in states:
        path.unlink()
    print(
        f"Stopped {len(live_states)} Forge client(s) and cleared "
        f"{len(states)} Forge-owned state file(s)"
    )
    return 0


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Provision and manage the repository-owned Etherology Forge 1.20.1 "
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
        help="Exact packaged Forge scenario id for check/start",
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
