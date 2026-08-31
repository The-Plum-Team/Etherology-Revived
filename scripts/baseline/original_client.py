#!/usr/bin/env python3
"""Manage one repository-owned Etherology Fabric 1.21.1 reference runtime."""

from __future__ import annotations

import argparse
import base64
import csv
from contextlib import contextmanager
from dataclasses import dataclass
import fcntl
import hashlib
import importlib
import importlib.machinery
import importlib.metadata
import importlib.util
import io
import json
import multiprocessing
from multiprocessing.connection import Connection
import os
from pathlib import Path, PurePosixPath
import platform
import re
import selectors
import shutil
import signal
import stat
import struct
import subprocess
import sys
import tempfile
import time
import types
from typing import BinaryIO, Callable, Iterable, Iterator
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
import zlib


EXPECTED_LAUNCHER_LIBRARY_VERSION = "8.0"
PINNED_LAUNCHER_RECORD_SIZE = 6461
PINNED_LAUNCHER_RECORD_SHA256 = (
    "f1ff10aa214ca09739bd949c177562f05261f94d731101fc6e10ee934b9a6cd7"
)
PINNED_HTTP_DISTRIBUTIONS = (
    (
        "requests",
        "2.34.2",
        "requests-2.34.2.dist-info",
        3153,
        "80a07dc1ca3b1a0c981e49e8dfde15af08a07050a74107fa8d699181d46c2d5c",
        "requests",
    ),
    (
        "urllib3",
        "2.7.0",
        "urllib3-2.7.0.dist-info",
        5615,
        "f254d3adc53473ba7dcd88bda8602dc3bf2193858f95eebf952f077612726290",
        "urllib3",
    ),
    (
        "certifi",
        "2026.6.17",
        "certifi-2026.6.17.dist-info",
        1115,
        "3472ecda100873d7a0f591a37cd756b029afb3dab5542be6e0fcdd6ae94cdcbb",
        "certifi",
    ),
    (
        "idna",
        "3.18",
        "idna-3.18.dist-info",
        1869,
        "5ef552c0058464669e5c74cb698590607a86fe5e038c6f28b263b8dc71306f8a",
        "idna",
    ),
    (
        "charset-normalizer",
        "3.4.9",
        "charset_normalizer-3.4.9.dist-info",
        2971,
        "478ffdfc59d8a071fdf34931107af09988a9b2b684996ae359887d8b7d030962",
        "charset_normalizer",
    ),
)
UNPINNED_OPTIONAL_HTTP_MODULES = (
    "backports",
    "brotli",
    "brotlicffi",
    "chardet",
    "simplejson",
    "socks",
)
SCRIPT_DIRECTORY = Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPT_DIRECTORY.parents[1]
MANIFEST_PATH = (
    SCRIPT_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v2.json"
)
STATE_ROOT = SCRIPT_DIRECTORY / ".state"
RUNTIMES_ROOT = STATE_ROOT / "runtimes"
PINNED_FABRIC_LIBRARY_CACHE_DIRECTORY = "pinned-fabric-libraries"
JAVA_OVERRIDE_ENVIRONMENT_VARIABLE = "ETHERLOGY_ORIGINAL_JAVA_21"
SCENARIO_PROPERTY_NAME = "etherology.original.e2e.scenario"
OFFLINE_ACCESS_TOKEN = "offline-etherology-original-baseline"
OFFLINE_CLIENT_ID = "etherology-original-baseline-client"
OFFLINE_XUID = "0"
CAFFEINATE_PATH = Path("/usr/bin/caffeinate")
MAXIMUM_DOWNLOAD_SIZE = 128 * 1024 * 1024
MAXIMUM_NESTED_JAR_SIZE = 64 * 1024 * 1024
MAXIMUM_NESTED_JAR_DEPTH = 12
MAXIMUM_PROCESS_LOG_SIZE = 64 * 1024 * 1024
MAXIMUM_DECODED_PNG_SIZE = 64 * 1024 * 1024
MAXIMUM_PNG_DIMENSION = 8192
MAXIMUM_LAUNCHER_FILE_COUNT = 8192
MAXIMUM_LAUNCHER_SIZE = 4 * 1024 * 1024 * 1024
MAXIMUM_SKIN_CACHE_FILE_COUNT = 256
MAXIMUM_SKIN_CACHE_SIZE = 64 * 1024 * 1024
PROVISION_INSTALL_TIMEOUT_SECONDS = 3600
PROCESS_STOP_TIMEOUT_SECONDS = 20
MUTABLE_SKIN_CACHE_RELATIVE_PATH = PurePosixPath("assets/skins")
CONTROLLER_TERMINATION_SIGNALS = (
    signal.SIGHUP,
    signal.SIGINT,
    signal.SIGQUIT,
    signal.SIGTERM,
)
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
ALLOWED_BUNDLE_METADATA_FILES = {
    "modrinth.index.json",
    "overrides/options.txt",
    "overrides/profile.json",
}
FATAL_CLIENT_LOG_MARKERS = (
    "Minecraft game provider couldn't locate the game",
    'Uncaught exception in thread "main"',
    "Mixin apply failed",
    "MixinTransformerError",
    "InvalidMixinException",
    "NoClassDefFoundError",
    "NoSuchMethodError",
    "NoSuchFieldError",
    "ExceptionInInitializerError",
    "Could not execute entrypoint stage",
    "A mod crashed on startup!",
    "Could not find required mod",
    "Encountered an unexpected exception",
    "No value with id -1",
    "java.lang.OutOfMemoryError",
)
EXPECTED_NATIVE_CLASSIFIERS = (
    (41, "org.lwjgl:lwjgl-freetype:3.3.3:natives-macos-arm64"),
    (42, "org.lwjgl:lwjgl-freetype:3.3.3:natives-macos-patch"),
    (48, "org.lwjgl:lwjgl-glfw:3.3.3:natives-macos"),
    (49, "org.lwjgl:lwjgl-glfw:3.3.3:natives-macos-arm64"),
    (55, "org.lwjgl:lwjgl-jemalloc:3.3.3:natives-macos"),
    (56, "org.lwjgl:lwjgl-jemalloc:3.3.3:natives-macos-arm64"),
    (62, "org.lwjgl:lwjgl-openal:3.3.3:natives-macos"),
    (63, "org.lwjgl:lwjgl-openal:3.3.3:natives-macos-arm64"),
    (69, "org.lwjgl:lwjgl-opengl:3.3.3:natives-macos"),
    (70, "org.lwjgl:lwjgl-opengl:3.3.3:natives-macos-arm64"),
    (76, "org.lwjgl:lwjgl-stb:3.3.3:natives-macos"),
    (77, "org.lwjgl:lwjgl-stb:3.3.3:natives-macos-arm64"),
    (83, "org.lwjgl:lwjgl-tinyfd:3.3.3:natives-macos"),
    (84, "org.lwjgl:lwjgl-tinyfd:3.3.3:natives-macos-arm64"),
    (90, "org.lwjgl:lwjgl:3.3.3:natives-macos"),
    (91, "org.lwjgl:lwjgl:3.3.3:natives-macos-arm64"),
)
EXPECTED_NATIVE_CLASSPATH_INDEXES = (
    46,
    47,
    49,
    50,
    52,
    53,
    55,
    56,
    58,
    59,
    61,
    62,
    64,
    65,
    67,
    68,
)

_VERIFIED_LAUNCHER_DISTRIBUTION_SHA256: str | None = None
_VERIFIED_HTTP_DISTRIBUTIONS_SHA256: str | None = None

EXPECTED_ASSERTION_NAMES = (
    "fabric_mod_loaded:etherology",
    "forest_lantern_resources_exact",
    "registry:block:etherology:forest_lantern",
    "registry:item:etherology:forest_lantern",
    "registry:item:etherology:forest_lantern_crumb",
    "forest_lantern_properties_exact",
    "forest_lantern_default_state_exact",
    "forest_lantern_state_count_exact",
    "forest_lantern_state_network_ids_exact",
    "packaged_root_jar:etherology",
    "packaged_root_jar:etherology_original_baseline_harness",
    "native_framebuffer_dimensions",
    "completed_world_renders_before_capture",
    "capture_render_ready",
    "capture_camera_exact",
    "native_screenshot_written",
    "integrated_world_joined",
    "server_arena_chunk_loaded",
    "server_player_creative",
    "server_forest_lantern_states_exact",
    "client_forest_lantern_states_exact",
    "server_forest_lantern_state_network_ids_exact",
    "forest_lantern_shears_speed_exact",
    "forest_lantern_immature_loot_empty",
    "forest_lantern_mature_loot_exact",
    "recipe:etherology:forest_lantern_crumb",
    "recipe:etherology:forest_lantern_crumb_from_smoking",
    "recipe:etherology:forest_lantern_crumb_from_campfire",
    "recipe:etherology:leather",
    "forest_lantern_jump_seed_exact",
    "forest_lantern_jump_stepping_position_exact",
    "forest_lantern_jump_break_exact",
    "forest_lantern_jump_drop_exact",
    "live_world_identity",
    "forced_world_save",
    "isolated_save_directory_present",
)
PHASE_ZERO_EXPECTED_ASSERTION_NAMES = (
    "fabric_mod_loaded:etherology",
    "published_resources_loaded",
    "registry_preflight",
    "registry:block:etherology:brewing_cauldron",
    "registry:block:etherology:empowerment_table",
    "registry:block:etherology:ethereal_storage",
    "registry:block:etherology:armillary_sphere",
    "registry:block_entity_type:etherology:brewing_cauldron_block_entity",
    "registry:block_entity_type:etherology:empowerment_table_block_entity",
    "registry:block_entity_type:etherology:ethereal_storage_block_entity",
    "registry:block_entity_type:etherology:armillary_sphere_block_entity",
    "etherology_block_states_have_network_ids",
    "packaged_root_jar:etherology",
    "packaged_root_jar:etherology_original_baseline_harness",
    "native_framebuffer_dimensions",
    "completed_world_renders_before_capture",
    "capture_render_ready",
    "capture_camera_exact",
    "native_screenshot_written",
    "integrated_world_joined",
    "client_world_mirrors_server_fixture",
    "client_fixture_block_entity_types_exact",
    "server_arena_chunk_loaded",
    "server_player_creative",
    "server_fixture_blocks_placed",
    "server_fixture_block_entities_present",
    "server_fixture_block_entity_types_exact",
    "live_world_identity",
    "forced_world_save",
    "isolated_save_directory_present",
)
FOREST_LANTERN_RESOURCES = (
    "minecraft:texts/splashes.txt",
    "etherology:blockstates/forest_lantern.json",
    "etherology:models/block/forest_lantern.json",
    "etherology:models/block/forest_lantern_0.json",
    "etherology:models/block/forest_lantern_1.json",
    "etherology:models/block/forest_lantern_2.json",
    "etherology:models/block/forest_lantern_3.json",
    "etherology:models/item/forest_lantern.json",
    "etherology:textures/block/forest_lantern.png",
    "etherology:textures/block/forest_lantern_0.png",
    "etherology:textures/block/forest_lantern_1.png",
    "etherology:textures/block/forest_lantern_2.png",
    "etherology:textures/block/forest_lantern_3.png",
    "etherology:textures/item/forest_lantern.png",
)
FOREST_LANTERN_FACINGS = ("north", "east", "south", "west")
FOREST_LANTERN_X_COORDINATES = (-12, -4, 4, 12)
FOREST_LANTERN_RECIPE_RESULTS = {
    "recipe:etherology:forest_lantern_crumb": (
        "etherology:forest_lantern_crumb=minecraft:smelting"
        "->etherology:forest_lantern_crumbx1"
    ),
    "recipe:etherology:forest_lantern_crumb_from_smoking": (
        "etherology:forest_lantern_crumb_from_smoking=minecraft:smoking"
        "->etherology:forest_lantern_crumbx1"
    ),
    "recipe:etherology:forest_lantern_crumb_from_campfire": (
        "etherology:forest_lantern_crumb_from_campfire=minecraft:campfire_cooking"
        "->etherology:forest_lantern_crumbx1"
    ),
    "recipe:etherology:leather": (
        "etherology:leather=minecraft:crafting->minecraft:leatherx1"
    ),
}


class BaselineError(RuntimeError):
    """Reports a fail-closed original-baseline controller error."""


@dataclass(frozen=True)
class Configuration:
    """Contains a validated profile manifest and its repository-owned paths."""

    manifest: dict[str, object]
    manifest_path: Path
    repository_root: Path
    bundle_path: Path
    harness_path: Path
    fabric_profile_snapshot_path: Path


@dataclass(frozen=True)
class PngImage:
    """Stores decoded RGB pixels for a bounded nonblank-image probe."""

    width: int
    height: int
    pixels: bytes


def load_json_object(path: Path, description: str) -> dict[str, object]:
    if path.is_symlink() or not path.is_file():
        raise BaselineError(f"{description} is missing or linked: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise BaselineError(f"Cannot read {description} {path}: {exception}") from exception
    if not isinstance(value, dict):
        raise BaselineError(f"{description} must contain a JSON object: {path}")
    return value


def require_object(container: dict[str, object], name: str) -> dict[str, object]:
    value = container.get(name)
    if not isinstance(value, dict):
        raise BaselineError(f"The manifest {name} object is invalid")
    return value


def require_list(container: dict[str, object], name: str) -> list[object]:
    value = container.get(name)
    if not isinstance(value, list):
        raise BaselineError(f"The manifest {name} list is invalid")
    return value


def require_exact_fields(
    value: dict[str, object], expected: set[str], description: str
) -> None:
    actual = set(value)
    if actual != expected:
        raise BaselineError(
            f"{description} has unexpected fields: "
            f"missing={sorted(expected - actual)}, extra={sorted(actual - expected)}"
        )


def safe_leaf_name(raw_value: object, field_name: str) -> str:
    if not isinstance(raw_value, str):
        raise BaselineError(f"The manifest {field_name} field is invalid")
    path = Path(raw_value)
    if path.parts != (raw_value,) or raw_value in ("", ".", ".."):
        raise BaselineError(f"The manifest {field_name} field is unsafe")
    return raw_value


def validate_sha256(raw_value: object, field_name: str) -> str:
    if not isinstance(raw_value, str) or re.fullmatch(r"[0-9a-f]{64}", raw_value) is None:
        raise BaselineError(f"The {field_name} SHA-256 digest is invalid")
    return raw_value


def validate_sha1(raw_value: object, field_name: str) -> str:
    if not isinstance(raw_value, str) or re.fullmatch(r"[0-9a-f]{40}", raw_value) is None:
        raise BaselineError(f"The {field_name} SHA-1 digest is invalid")
    return raw_value


def validate_pinned_https_url(
    raw_value: object, host: str, field_name: str
) -> str:
    if not isinstance(raw_value, str):
        raise BaselineError(f"The {field_name} URL is invalid")
    parsed = urllib.parse.urlparse(raw_value)
    if (
        parsed.scheme != "https"
        or parsed.hostname != host
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise BaselineError(f"The {field_name} URL is unsafe")
    return raw_value


def is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def ensure_no_symlink_components(parent: Path, path: Path, description: str) -> None:
    absolute_parent = parent.absolute()
    absolute_path = path.absolute()
    if not is_relative_to(absolute_path, absolute_parent):
        raise BaselineError(f"{description} escapes its owned parent: {path}")
    current = absolute_parent
    if current.is_symlink():
        raise BaselineError(f"{description} parent must not be a symlink: {parent}")
    for part in absolute_path.relative_to(absolute_parent).parts:
        current /= part
        if current.is_symlink():
            raise BaselineError(f"{description} resolves through a symlink: {current}")
    resolved_parent = absolute_parent.resolve(strict=False)
    resolved_path = absolute_path.resolve(strict=False)
    if not is_relative_to(resolved_path, resolved_parent):
        raise BaselineError(f"{description} resolves outside its owned parent: {path}")


def safe_repository_path(
    repository_root: Path, raw_value: object, field_name: str
) -> Path:
    if not isinstance(raw_value, str):
        raise BaselineError(f"The manifest {field_name} field is invalid")
    relative_path = Path(raw_value)
    if relative_path.is_absolute() or not relative_path.parts or ".." in relative_path.parts:
        raise BaselineError(f"The manifest {field_name} field is unsafe")
    repository = repository_root.resolve(strict=True)
    path = repository / relative_path
    ensure_no_symlink_components(repository, path, f"Manifest {field_name}")
    return path


def parse_options(content: str) -> dict[str, str]:
    options: dict[str, str] = {}
    for line in content.splitlines():
        name, separator, value = line.partition(":")
        if not separator or not name or name in options:
            raise BaselineError("The isolated profile options are invalid")
        options[name] = value
    return options


def validate_manifest_shape(manifest: dict[str, object]) -> None:
    require_exact_fields(
        manifest,
        {
            "schema",
            "profile",
            "provenance",
            "reference_bundle",
            "runtime",
            "launch",
            "capture",
            "profile_directories",
            "options",
            "forbidden_mod_ids",
        },
        "The original-baseline manifest",
    )
    if manifest.get("schema") != 1:
        raise BaselineError("Unsupported original-baseline profile manifest schema")

    profile = require_object(manifest, "profile")
    require_exact_fields(
        profile,
        {
            "id",
            "runtime_directory",
            "game_directory",
            "launcher_directory",
            "evidence_directory",
            "logs_directory",
            "installer_directory",
            "home_directory",
            "temporary_directory",
            "marker_file",
            "artifact_lock_file",
            "runtime_lock_file",
            "lifecycle_lock_file",
            "process_state_file",
            "launch_attempt_file",
        },
        "The profile object",
    )
    profile_id = safe_leaf_name(profile.get("id"), "profile.id")
    if re.fullmatch(r"[a-z0-9][a-z0-9.-]+", profile_id) is None:
        raise BaselineError("The manifest profile.id is not a stable lowercase id")
    if profile.get("runtime_directory") != profile_id:
        raise BaselineError("The runtime directory must equal the exact profile id")
    profile_revisions = {
        "etherology-original-fabric-1.21.1-published-0.1.7-v1": "v1",
        "etherology-original-fabric-1.21.1-published-0.1.7-v2": "v2",
    }
    profile_revision = profile_revisions.get(profile_id)
    if profile_revision is None:
        raise BaselineError("The original-baseline profile revision is not allowlisted")
    for field_name in (
        "game_directory",
        "launcher_directory",
        "evidence_directory",
        "logs_directory",
        "installer_directory",
        "home_directory",
        "temporary_directory",
        "marker_file",
        "artifact_lock_file",
        "runtime_lock_file",
        "lifecycle_lock_file",
        "process_state_file",
        "launch_attempt_file",
    ):
        safe_leaf_name(profile.get(field_name), f"profile.{field_name}")
    profile_paths = [
        str(value) for name, value in profile.items() if name != "id"
    ]
    if len(set(profile_paths)) != len(profile_paths):
        raise BaselineError("The profile-owned paths must be distinct")

    provenance = require_object(manifest, "provenance")
    require_exact_fields(
        provenance,
        {"reference_id", "authority", "source_commit", "source_commit_binding"},
        "The provenance object",
    )
    if provenance != {
        "reference_id": "published-0.1.7",
        "authority": "published-binary",
        "source_commit": None,
        "source_commit_binding": "unavailable",
    }:
        raise BaselineError(
            "This controller is restricted to the published-0.1.7 binary stratum"
        )

    bundle = require_object(manifest, "reference_bundle")
    require_exact_fields(
        bundle, {"path", "size", "sha256", "members"}, "The reference_bundle object"
    )
    raw_bundle_path = bundle.get("path")
    if not isinstance(raw_bundle_path, str):
        raise BaselineError("The reference bundle path is invalid")
    bundle_relative_path = PurePosixPath(raw_bundle_path)
    if (
        bundle_relative_path.parent != PurePosixPath("scripts/baseline/.state")
        or bundle_relative_path.suffix != ".mrpack"
    ):
        raise BaselineError(
            "The reference bundle must be one fixed repository-owned baseline mrpack"
        )
    bundle_size = bundle.get("size")
    if type(bundle_size) is not int or not 0 < int(bundle_size) <= MAXIMUM_DOWNLOAD_SIZE:
        raise BaselineError("The reference bundle size is invalid")
    validate_sha256(bundle.get("sha256"), "reference bundle")

    raw_members = require_list(bundle, "members")
    if len(raw_members) != 8:
        raise BaselineError("The reference bundle must pin exactly eight JAR members")
    file_names: set[str] = set()
    archive_paths: set[str] = set()
    root_mod_ids: set[str] = set()
    for raw_member in raw_members:
        if not isinstance(raw_member, dict):
            raise BaselineError("The reference bundle contains an invalid member")
        require_exact_fields(
            raw_member,
            {"archive_path", "file_name", "mod_id", "size", "sha256"},
            "A reference bundle member",
        )
        file_name = safe_leaf_name(raw_member.get("file_name"), "member.file_name")
        if not file_name.endswith(".jar") or file_name in file_names:
            raise BaselineError(f"A member JAR name is invalid or duplicated: {file_name}")
        file_names.add(file_name)
        expected_archive_path = f"overrides/mods/{file_name}"
        archive_path = raw_member.get("archive_path")
        if archive_path != expected_archive_path or archive_path in archive_paths:
            raise BaselineError(f"A member archive path is invalid: {archive_path}")
        archive_paths.add(str(archive_path))
        mod_id = safe_leaf_name(raw_member.get("mod_id"), "member.mod_id")
        if (
            re.fullmatch(r"[a-z][a-z0-9_-]{1,63}", mod_id) is None
            or mod_id in root_mod_ids
        ):
            raise BaselineError(f"A member mod id is invalid or duplicated: {mod_id}")
        root_mod_ids.add(mod_id)
        size = raw_member.get("size")
        if type(size) is not int or not 0 < int(size) <= MAXIMUM_DOWNLOAD_SIZE:
            raise BaselineError(f"The member size is invalid for {file_name}")
        validate_sha256(raw_member.get("sha256"), f"member {file_name}")
    if "etherology" not in root_mod_ids:
        raise BaselineError("The published bundle has no Etherology root JAR")

    runtime = require_object(manifest, "runtime")
    require_exact_fields(
        runtime,
        {
            "minecraft_version",
            "loader",
            "loader_version",
            "java_major",
            "metadata",
            "fabric_profile",
        },
        "The runtime object",
    )
    if (
        runtime.get("minecraft_version") != "1.21.1"
        or runtime.get("loader") != "fabric"
        or runtime.get("loader_version") != "0.17.3"
        or runtime.get("java_major") != 21
    ):
        raise BaselineError(
            "The baseline must be Fabric 0.17.3 / Minecraft 1.21.1 / Java 21"
        )
    metadata = require_object(runtime, "metadata")
    require_exact_fields(
        metadata,
        {"url", "size", "sha1", "sha256", "asset_index", "client"},
        "The pinned Minecraft metadata object",
    )
    validate_pinned_https_url(
        metadata.get("url"), "piston-meta.mojang.com", "Minecraft metadata"
    )
    metadata_size = metadata.get("size")
    if type(metadata_size) is not int or not 0 < int(metadata_size) <= 1024 * 1024:
        raise BaselineError("The pinned Minecraft metadata size is invalid")
    validate_sha1(metadata.get("sha1"), "Minecraft metadata")
    validate_sha256(metadata.get("sha256"), "Minecraft metadata")
    asset_index = require_object(metadata, "asset_index")
    require_exact_fields(
        asset_index,
        {"id", "url", "size", "total_size", "sha1", "sha256"},
        "The pinned Minecraft asset-index object",
    )
    safe_leaf_name(asset_index.get("id"), "runtime.metadata.asset_index.id")
    validate_pinned_https_url(
        asset_index.get("url"), "piston-meta.mojang.com", "Minecraft asset index"
    )
    for field_name in ("size", "total_size"):
        value = asset_index.get(field_name)
        if type(value) is not int or int(value) <= 0:
            raise BaselineError(
                f"The pinned Minecraft asset-index {field_name} is invalid"
            )
    validate_sha1(asset_index.get("sha1"), "Minecraft asset index")
    validate_sha256(asset_index.get("sha256"), "Minecraft asset index")
    client = require_object(metadata, "client")
    require_exact_fields(
        client,
        {"url", "size", "sha1", "sha256"},
        "The pinned Minecraft client object",
    )
    validate_pinned_https_url(
        client.get("url"), "piston-data.mojang.com", "Minecraft client"
    )
    client_size = client.get("size")
    if type(client_size) is not int or not 0 < int(client_size) <= MAXIMUM_DOWNLOAD_SIZE:
        raise BaselineError("The pinned Minecraft client size is invalid")
    validate_sha1(client.get("sha1"), "Minecraft client")
    validate_sha256(client.get("sha256"), "Minecraft client")
    fabric_profile = require_object(runtime, "fabric_profile")
    require_exact_fields(
        fabric_profile,
        {"url", "size", "sha1", "sha256", "snapshot", "libraries"},
        "The pinned Fabric profile object",
    )
    fabric_profile_url = validate_pinned_https_url(
        fabric_profile.get("url"), "meta.fabricmc.net", "Fabric loader profile"
    )
    if (
        fabric_profile_url
        != "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.17.3/profile/json"
    ):
        raise BaselineError("The Fabric loader profile provenance URL is invalid")
    fabric_profile_size = fabric_profile.get("size")
    if (
        type(fabric_profile_size) is not int
        or not 0 < int(fabric_profile_size) <= 1024 * 1024
    ):
        raise BaselineError("The pinned Fabric profile size is invalid")
    validate_sha1(fabric_profile.get("sha1"), "Fabric loader profile")
    validate_sha256(fabric_profile.get("sha256"), "Fabric loader profile")
    fabric_snapshot = require_object(fabric_profile, "snapshot")
    require_exact_fields(
        fabric_snapshot,
        {"path", "size", "sha256"},
        "The tracked Fabric profile snapshot object",
    )
    if (
        fabric_snapshot.get("path")
        != "scripts/baseline/fixtures/fabric-loader-0.17.3-1.21.1-profile.json"
    ):
        raise BaselineError("The tracked Fabric profile snapshot path is invalid")
    fabric_snapshot_size = fabric_snapshot.get("size")
    if (
        type(fabric_snapshot_size) is not int
        or int(fabric_snapshot_size) != int(fabric_profile_size) + 1
    ):
        raise BaselineError(
            "The tracked Fabric profile snapshot must be the pinned response plus one newline"
        )
    validate_sha256(
        fabric_snapshot.get("sha256"), "tracked Fabric loader profile snapshot"
    )
    fabric_libraries = require_list(fabric_profile, "libraries")
    if len(fabric_libraries) != 8:
        raise BaselineError("The Fabric profile must pin exactly eight libraries")
    seen_fabric_coordinates: set[str] = set()
    seen_fabric_paths: set[str] = set()
    for raw_library in fabric_libraries:
        if not isinstance(raw_library, dict):
            raise BaselineError("The Fabric profile contains an invalid library pin")
        require_exact_fields(
            raw_library,
            {"coordinate", "path", "url", "size", "sha1", "sha256"},
            "A pinned Fabric library",
        )
        coordinate = str(raw_library.get("coordinate"))
        relative_path = maven_library_path(coordinate)
        if (
            raw_library.get("path") != relative_path.as_posix()
            or coordinate in seen_fabric_coordinates
            or relative_path.as_posix() in seen_fabric_paths
        ):
            raise BaselineError("A pinned Fabric library path is invalid or duplicated")
        seen_fabric_coordinates.add(coordinate)
        seen_fabric_paths.add(relative_path.as_posix())
        expected_url = f"https://maven.fabricmc.net/{relative_path.as_posix()}"
        if raw_library.get("url") != expected_url:
            raise BaselineError("A pinned Fabric library URL is invalid")
        size = raw_library.get("size")
        if type(size) is not int or not 0 < int(size) <= MAXIMUM_DOWNLOAD_SIZE:
            raise BaselineError("A pinned Fabric library size is invalid")
        validate_sha1(raw_library.get("sha1"), f"Fabric library {coordinate}")
        validate_sha256(raw_library.get("sha256"), f"Fabric library {coordinate}")

    launch = require_object(manifest, "launch")
    require_exact_fields(
        launch,
        {"offline_username", "resolution", "maximum_memory_mb", "timeout_seconds"},
        "The launch object",
    )
    username = safe_leaf_name(launch.get("offline_username"), "launch.offline_username")
    if re.fullmatch(r"[A-Za-z0-9_]{3,16}", username) is None:
        raise BaselineError("The offline username is invalid")
    resolution = require_object(launch, "resolution")
    require_exact_fields(resolution, {"width", "height"}, "The resolution object")
    for field_name in ("width", "height"):
        value = resolution.get(field_name)
        if type(value) is not int or not 320 <= int(value) <= 7680:
            raise BaselineError(f"The launch resolution {field_name} is invalid")
    if resolution != {"width": 960, "height": 540}:
        raise BaselineError("The logical launch resolution must be exactly 960x540")
    maximum_memory = launch.get("maximum_memory_mb")
    timeout_seconds = launch.get("timeout_seconds")
    if type(maximum_memory) is not int or not 1024 <= int(maximum_memory) <= 32768:
        raise BaselineError("The launch maximum memory is invalid")
    if type(timeout_seconds) is not int or not 60 <= int(timeout_seconds) <= 7200:
        raise BaselineError("The launch timeout is invalid")

    capture = require_object(manifest, "capture")
    require_exact_fields(capture, {"harness", "scenario"}, "The capture object")
    harness = require_object(capture, "harness")
    require_exact_fields(
        harness,
        {
            "status",
            "path",
            "file_name",
            "mod_id",
            "version",
            "size",
            "sha256",
            "client_entrypoint",
            "mixin_config",
        },
        "The capture harness object",
    )
    harness_file_name = safe_leaf_name(
        harness.get("file_name"), "capture.harness.file_name"
    )
    expected_harness_version = "1.0.0" if profile_revision == "v1" else "1.1.0"
    expected_harness_file_name = (
        "Etherology-Original-E2E-Harness-Fabric-1.21.1-"
        f"{expected_harness_version}.jar"
    )
    if (
        harness.get("status") != "implemented"
        or harness_file_name != expected_harness_file_name
        or harness.get("path")
        != f"baseline-harness/fabric/1.21.1/build/libs/{expected_harness_file_name}"
        or harness.get("mod_id") != "etherology_original_baseline_harness"
        or harness.get("version") != expected_harness_version
        or harness.get("client_entrypoint")
        != "dev.theplumteam.etherology.baseline.fabric.OriginalPhaseZeroHarness"
        or harness.get("mixin_config")
        != "etherology-original-baseline-harness.mixins.json"
    ):
        raise BaselineError("The capture harness identity is not the exact pinned harness")
    harness_size = harness.get("size")
    if type(harness_size) is not int or not 0 < int(harness_size) <= MAXIMUM_DOWNLOAD_SIZE:
        raise BaselineError("The capture harness size is invalid")
    validate_sha256(harness.get("sha256"), "capture harness")

    scenario = require_object(capture, "scenario")
    require_exact_fields(
        scenario,
        {
            "id",
            "report_file",
            "completion_marker_file",
            "screenshot_file",
            "world_directory_name",
            "world_display_name",
            "world_seed",
            "framebuffer",
        },
        "The capture scenario object",
    )
    exact_scenario = (
        {
            "id": "phase0-smoke",
            "report_file": "report.json",
            "completion_marker_file": "done.marker",
            "screenshot_file": "phase0-smoke.png",
            "world_directory_name": "etherology-original-phase0-smoke-world",
            "world_display_name": "Etherology Original 0.1.7 Phase 0",
            "world_seed": 19514442935972151,
        }
        if profile_revision == "v1"
        else {
            "id": "forest-lantern",
            "report_file": "report.json",
            "completion_marker_file": "done.marker",
            "screenshot_file": "forest-lantern.png",
            "world_directory_name": "etherology-original-forest-lantern-world",
            "world_display_name": "Etherology Original 0.1.7 Forest Lantern",
            "world_seed": 4995697353423860023,
        }
    )
    for field_name, expected_value in exact_scenario.items():
        if scenario.get(field_name) != expected_value:
            raise BaselineError(
                f"The capture scenario has an unexpected {field_name}"
            )
    for field_name in (
        "id",
        "report_file",
        "completion_marker_file",
        "screenshot_file",
        "world_directory_name",
    ):
        safe_leaf_name(scenario.get(field_name), f"capture.scenario.{field_name}")
    framebuffer = require_object(scenario, "framebuffer")
    require_exact_fields(framebuffer, {"width", "height"}, "The framebuffer object")
    if framebuffer != {"width": 1920, "height": 1080}:
        raise BaselineError("The capture framebuffer must be exactly 1920x1080")

    profile_directories = require_list(manifest, "profile_directories")
    if (
        not profile_directories
        or len(set(str(value) for value in profile_directories)) != len(profile_directories)
        or not all(isinstance(value, str) for value in profile_directories)
    ):
        raise BaselineError("The profile directory list is invalid or duplicated")
    for directory in profile_directories:
        safe_leaf_name(directory, "profile_directories entry")
    if not {"logs", "mods", "screenshots"}.issubset(profile_directories):
        raise BaselineError("The profile directory list lacks a required directory")

    options = require_list(manifest, "options")
    if not options or not all(
        isinstance(value, str) and "\n" not in value and "\r" not in value
        for value in options
    ):
        raise BaselineError("The profile options list is invalid")
    parse_options("\n".join(str(value) for value in options) + "\n")

    forbidden_mod_ids = require_list(manifest, "forbidden_mod_ids")
    if (
        not forbidden_mod_ids
        or len(set(str(value) for value in forbidden_mod_ids)) != len(forbidden_mod_ids)
        or not all(
            isinstance(value, str)
            and re.fullmatch(r"[a-z][a-z0-9_-]{1,63}", value) is not None
            for value in forbidden_mod_ids
        )
    ):
        raise BaselineError("The forbidden mod id list is invalid")
    overlap = root_mod_ids.intersection(str(value) for value in forbidden_mod_ids)
    if overlap:
        raise BaselineError(f"Required and forbidden mod ids overlap: {sorted(overlap)}")


def load_configuration(
    manifest_path: Path = MANIFEST_PATH,
    repository_root: Path = REPOSITORY_ROOT,
) -> Configuration:
    repository_input = repository_root.absolute()
    manifest_input = manifest_path.absolute()
    if is_relative_to(manifest_input, repository_input):
        ensure_no_symlink_components(
            repository_input, manifest_input, "Profile manifest"
        )
    if manifest_input.is_symlink():
        raise BaselineError(f"Profile manifest must not be a symlink: {manifest_input}")
    repository = repository_input.resolve(strict=True)
    manifest_file = manifest_input.resolve(strict=False)
    if not is_relative_to(manifest_file, repository):
        raise BaselineError(f"Profile manifest escapes the repository: {manifest_input}")
    manifest = load_json_object(manifest_file, "Original-baseline profile manifest")
    validate_manifest_shape(manifest)
    bundle = require_object(manifest, "reference_bundle")
    bundle_path = safe_repository_path(repository, bundle.get("path"), "reference_bundle.path")
    harness = require_object(require_object(manifest, "capture"), "harness")
    harness_path = safe_repository_path(
        repository, harness.get("path"), "capture.harness.path"
    )
    fabric_profile = require_object(
        require_object(manifest, "runtime"), "fabric_profile"
    )
    fabric_snapshot = require_object(fabric_profile, "snapshot")
    fabric_profile_snapshot_path = safe_repository_path(
        repository,
        fabric_snapshot.get("path"),
        "runtime.fabric_profile.snapshot.path",
    )
    return Configuration(
        manifest,
        manifest_file,
        repository,
        bundle_path,
        harness_path,
        fabric_profile_snapshot_path,
    )


def profile_spec(configuration: Configuration) -> dict[str, object]:
    return require_object(configuration.manifest, "profile")


def runtime_spec(configuration: Configuration) -> dict[str, object]:
    return require_object(configuration.manifest, "runtime")


def bundle_spec(configuration: Configuration) -> dict[str, object]:
    return require_object(configuration.manifest, "reference_bundle")


def capture_spec(configuration: Configuration) -> dict[str, object]:
    return require_object(configuration.manifest, "capture")


def harness_spec(configuration: Configuration) -> dict[str, object]:
    return require_object(capture_spec(configuration), "harness")


def scenario_spec(configuration: Configuration) -> dict[str, object]:
    return require_object(capture_spec(configuration), "scenario")


def member_specs(configuration: Configuration) -> list[dict[str, object]]:
    return [
        value
        for value in require_list(bundle_spec(configuration), "members")
        if isinstance(value, dict)
    ]


def scenario_ids(configuration: Configuration) -> list[str]:
    return [str(scenario_spec(configuration)["id"])]


def resolve_scenario_id(
    configuration: Configuration, configured_scenario_id: str | None
) -> str:
    if configured_scenario_id is None:
        raise BaselineError("run requires one explicit --scenario allowlist entry")
    if configured_scenario_id in scenario_ids(configuration):
        return configured_scenario_id
    raise BaselineError(
        f"Unsupported original-baseline scenario {configured_scenario_id!r}; "
        f"expected one of {scenario_ids(configuration)}"
    )


def runtime_root(
    configuration: Configuration, runtimes_root: Path = RUNTIMES_ROOT
) -> Path:
    return runtimes_root / str(profile_spec(configuration)["runtime_directory"])


def owned_child(
    configuration: Configuration, field_name: str, root: Path | None = None
) -> Path:
    profile_root = root or runtime_root(configuration)
    return profile_root / str(profile_spec(configuration)[field_name])


def game_directory(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "game_directory", root)


def launcher_directory(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "launcher_directory", root)


def evidence_directory(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "evidence_directory", root)


def logs_directory(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "logs_directory", root)


def installer_directory(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "installer_directory", root)


def home_directory(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "home_directory", root)


def temporary_directory(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "temporary_directory", root)


def marker_path(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "marker_file", root)


def artifact_lock_path(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "artifact_lock_file", root)


def runtime_lock_path(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "runtime_lock_file", root)


def lifecycle_lock_path(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "lifecycle_lock_file", root)


def process_state_path(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "process_state_file", root)


def launch_attempt_path(configuration: Configuration, root: Path | None = None) -> Path:
    return owned_child(configuration, "launch_attempt_file", root)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exception:
        raise BaselineError(f"Cannot hash file {path}: {exception}") from exception
    return digest.hexdigest()


def sha1_file(path: Path) -> str:
    digest = hashlib.sha1()
    try:
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exception:
        raise BaselineError(f"Cannot hash file {path}: {exception}") from exception
    return digest.hexdigest()


def verify_exact_file(
    path: Path,
    expected_sha256: str,
    expected_size: int | None,
    description: str,
) -> None:
    if path.is_symlink() or not path.is_file():
        raise BaselineError(f"{description} is missing or linked: {path}")
    try:
        actual_size = path.stat().st_size
    except OSError as exception:
        raise BaselineError(f"Cannot inspect {description} {path}: {exception}") from exception
    if expected_size is not None and actual_size != expected_size:
        raise BaselineError(
            f"{description} has size {actual_size}, expected {expected_size}: {path}"
        )
    if sha256_file(path) != expected_sha256:
        raise BaselineError(f"{description} failed SHA-256 validation: {path}")


def read_exact_file_no_follow(
    path: Path,
    expected_sha256: str,
    expected_size: int,
    description: str,
) -> bytes:
    """Reads one exact regular file through a descriptor that rejects final links."""

    no_follow = getattr(os, "O_NOFOLLOW", None)
    if not isinstance(no_follow, int):
        raise BaselineError("This platform cannot enforce link-free file reads")
    descriptor = -1
    try:
        descriptor = os.open(path, os.O_RDONLY | no_follow)
        file_status = os.fstat(descriptor)
        if not stat.S_ISREG(file_status.st_mode):
            raise BaselineError(f"{description} is not a regular file: {path}")
        if file_status.st_size != expected_size:
            raise BaselineError(
                f"{description} has size {file_status.st_size}, "
                f"expected {expected_size}: {path}"
            )
        with os.fdopen(descriptor, "rb") as handle:
            descriptor = -1
            content = handle.read()
    except BaselineError:
        raise
    except OSError as exception:
        raise BaselineError(f"Cannot read {description} {path}: {exception}") from exception
    finally:
        if descriptor >= 0:
            os.close(descriptor)
    if len(content) != expected_size:
        raise BaselineError(
            f"{description} changed size while being read: {path}"
        )
    if hashlib.sha256(content).hexdigest() != expected_sha256:
        raise BaselineError(f"{description} failed SHA-256 validation: {path}")
    return content


def zip_entry_is_symlink(entry: zipfile.ZipInfo) -> bool:
    return stat.S_IFMT(entry.external_attr >> 16) == stat.S_IFLNK


def validate_archive_entries(
    archive: zipfile.ZipFile, description: str
) -> dict[str, zipfile.ZipInfo]:
    entries: dict[str, zipfile.ZipInfo] = {}
    for entry in archive.infolist():
        path = PurePosixPath(entry.filename)
        if (
            not entry.filename
            or path.is_absolute()
            or ".." in path.parts
            or "\\" in entry.filename
        ):
            raise BaselineError(f"{description} has an unsafe entry: {entry.filename}")
        if entry.filename in entries:
            raise BaselineError(f"{description} has a duplicate entry: {entry.filename}")
        if entry.flag_bits & 0x1:
            raise BaselineError(f"{description} has an encrypted entry: {entry.filename}")
        if zip_entry_is_symlink(entry):
            raise BaselineError(f"{description} has a linked entry: {entry.filename}")
        entries[entry.filename] = entry
    return entries


def read_fabric_metadata(
    archive: zipfile.ZipFile, source_name: str, depth: int = 0
) -> list[dict[str, object]]:
    if depth > MAXIMUM_NESTED_JAR_DEPTH:
        raise BaselineError(f"Fabric nested-JAR depth is excessive in {source_name}")
    entries = validate_archive_entries(archive, f"Fabric JAR {source_name}")
    metadata_entry = entries.get("fabric.mod.json")
    if metadata_entry is None or metadata_entry.is_dir():
        raise BaselineError(f"Fabric metadata is missing in {source_name}")
    if metadata_entry.file_size > 1024 * 1024:
        raise BaselineError(f"Fabric metadata is too large in {source_name}")
    try:
        metadata = json.loads(archive.read(metadata_entry))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise BaselineError(f"Fabric metadata is invalid in {source_name}") from exception
    if (
        not isinstance(metadata, dict)
        or not isinstance(metadata.get("id"), str)
        or not isinstance(metadata.get("version"), str)
    ):
        raise BaselineError(f"Fabric metadata has no id or version in {source_name}")
    collected = [metadata]
    raw_nested_jars = metadata.get("jars", [])
    if not isinstance(raw_nested_jars, list):
        raise BaselineError(f"Fabric metadata has an invalid jars list in {source_name}")
    for raw_nested_jar in raw_nested_jars:
        if not isinstance(raw_nested_jar, dict) or not isinstance(
            raw_nested_jar.get("file"), str
        ):
            raise BaselineError(f"Fabric metadata has an invalid nested JAR in {source_name}")
        nested_name = str(raw_nested_jar["file"])
        nested_path = PurePosixPath(nested_name)
        if nested_path.is_absolute() or ".." in nested_path.parts:
            raise BaselineError(f"Fabric metadata has an unsafe nested JAR in {source_name}")
        nested_entry = entries.get(nested_name)
        if (
            nested_entry is None
            or nested_entry.is_dir()
            or nested_entry.file_size > MAXIMUM_NESTED_JAR_SIZE
        ):
            raise BaselineError(
                f"Fabric nested JAR is missing or too large in {source_name}: {nested_name}"
            )
        try:
            nested_bytes = archive.read(nested_entry)
            with zipfile.ZipFile(io.BytesIO(nested_bytes)) as nested_archive:
                collected.extend(
                    read_fabric_metadata(
                        nested_archive, f"{source_name}!/{nested_name}", depth + 1
                    )
                )
        except (OSError, zipfile.BadZipFile) as exception:
            raise BaselineError(
                f"Fabric nested JAR is unreadable in {source_name}: {nested_name}"
            ) from exception
    return collected


def fabric_metadata_from_bytes(content: bytes, source_name: str) -> list[dict[str, object]]:
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            return read_fabric_metadata(archive, source_name)
    except zipfile.BadZipFile as exception:
        raise BaselineError(f"Fabric mod JAR is unreadable: {source_name}") from exception


def validate_modrinth_index(raw_content: bytes, configuration: Configuration) -> None:
    try:
        index = json.loads(raw_content)
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise BaselineError("The reference bundle Modrinth index is invalid") from exception
    if not isinstance(index, dict):
        raise BaselineError("The reference bundle Modrinth index is not an object")
    dependencies = index.get("dependencies")
    runtime = runtime_spec(configuration)
    if (
        index.get("formatVersion") != 1
        or index.get("game") != "minecraft"
        or index.get("files") != []
        or not isinstance(dependencies, dict)
        or dependencies.get("minecraft") != runtime["minecraft_version"]
        or dependencies.get("fabric-loader") != runtime["loader_version"]
    ):
        raise BaselineError("The reference bundle Modrinth index selects another runtime")


def verify_reference_bundle(configuration: Configuration) -> dict[str, set[str]]:
    bundle = bundle_spec(configuration)
    verify_exact_file(
        configuration.bundle_path,
        str(bundle["sha256"]),
        int(bundle["size"]),
        "Published reference bundle",
    )
    try:
        with zipfile.ZipFile(configuration.bundle_path) as archive:
            entries = validate_archive_entries(archive, "Published reference bundle")
            expected_mod_paths = {
                str(member["archive_path"]) for member in member_specs(configuration)
            }
            actual_regular_paths = {
                name for name, entry in entries.items() if not entry.is_dir()
            }
            expected_regular_paths = expected_mod_paths | ALLOWED_BUNDLE_METADATA_FILES
            if actual_regular_paths != expected_regular_paths:
                raise BaselineError(
                    "The published reference bundle inventory differs from its allowlist: "
                    f"missing={sorted(expected_regular_paths - actual_regular_paths)}, "
                    f"extra={sorted(actual_regular_paths - expected_regular_paths)}"
                )
            validate_modrinth_index(
                archive.read(entries["modrinth.index.json"]), configuration
            )
            all_mod_ids: set[str] = set()
            member_mod_ids: dict[str, set[str]] = {}
            for member in member_specs(configuration):
                archive_path = str(member["archive_path"])
                entry = entries[archive_path]
                if entry.file_size != int(member["size"]):
                    raise BaselineError(
                        f"Published member has an unexpected size: {archive_path}"
                    )
                content = archive.read(entry)
                if hashlib.sha256(content).hexdigest() != member["sha256"]:
                    raise BaselineError(
                        f"Published member failed SHA-256 validation: {archive_path}"
                    )
                metadata = fabric_metadata_from_bytes(content, archive_path)
                if metadata[0]["id"] != member["mod_id"]:
                    raise BaselineError(
                        f"Published member {archive_path} has root mod id "
                        f"{metadata[0]['id']}, expected {member['mod_id']}"
                    )
                mod_ids = {str(value["id"]) for value in metadata}
                member_mod_ids[str(member["file_name"])] = mod_ids
                all_mod_ids.update(mod_ids)
    except (OSError, zipfile.BadZipFile, KeyError) as exception:
        raise BaselineError(
            f"Cannot inspect published reference bundle {configuration.bundle_path}: {exception}"
        ) from exception
    forbidden = {
        str(value)
        for value in require_list(configuration.manifest, "forbidden_mod_ids")
    }
    present_forbidden = sorted(all_mod_ids.intersection(forbidden))
    if present_forbidden:
        raise BaselineError(
            f"Published reference bundle contains forbidden mod ids: {present_forbidden}"
        )
    return member_mod_ids


def verify_harness_artifact(configuration: Configuration) -> None:
    harness = harness_spec(configuration)
    verify_exact_file(
        configuration.harness_path,
        str(harness["sha256"]),
        int(harness["size"]),
        "Original-baseline harness",
    )
    try:
        with zipfile.ZipFile(configuration.harness_path) as archive:
            entries = validate_archive_entries(archive, "Original-baseline harness")
            metadata = read_fabric_metadata(
                archive, str(configuration.harness_path.name)
            )
            if len(metadata) != 1:
                raise BaselineError("The original-baseline harness contains nested mods")
            root_metadata = metadata[0]
            expected_entrypoint = str(harness["client_entrypoint"])
            expected_mixin = str(harness["mixin_config"])
            if (
                root_metadata.get("id") != harness["mod_id"]
                or root_metadata.get("version") != harness["version"]
                or root_metadata.get("environment") != "client"
                or root_metadata.get("entrypoints")
                != {"client": [expected_entrypoint]}
                or root_metadata.get("mixins")
                != [{"config": expected_mixin, "environment": "client"}]
                or root_metadata.get("depends")
                != {
                    "fabricloader": "=0.17.3",
                    "fabric-api": "=0.110.0+1.21.1",
                    "minecraft": "=1.21.1",
                    "java": ">=21",
                    "etherology": "=1.21-0.1.7",
                }
            ):
                raise BaselineError(
                    "The original-baseline harness metadata does not match its exact contract"
                )

            expected_entrypoint_class = expected_entrypoint.replace(".", "/") + ".class"
            expected_mixin_class = (
                "dev/theplumteam/etherology/baseline/fabric/mixin/"
                "GameRendererMixin.class"
            )
            expected_jump_invoker_class = (
                "dev/theplumteam/etherology/baseline/fabric/mixin/"
                "PlayerEntityJumpInvoker.class"
            )
            expects_jump_invoker = harness["version"] == "1.1.0"
            class_entries = {
                name
                for name, entry in entries.items()
                if not entry.is_dir() and name.endswith(".class")
            }
            if (
                expected_entrypoint_class not in class_entries
                or expected_mixin_class not in class_entries
                or (
                    expects_jump_invoker
                    and expected_jump_invoker_class not in class_entries
                )
                or not class_entries
                or any(
                    not name.startswith(
                        "dev/theplumteam/etherology/baseline/fabric/"
                    )
                    for name in class_entries
                )
            ):
                raise BaselineError(
                    "The original-baseline harness class inventory is not isolated"
                )
            allowed_non_class_files = {
                "META-INF/MANIFEST.MF",
                "fabric.mod.json",
                expected_mixin,
                "Etherology-Original-E2E-Harness-Fabric-1.21.1-refmap.json",
            }
            unexpected_regular_files = sorted(
                name
                for name, entry in entries.items()
                if not entry.is_dir()
                and not name.endswith(".class")
                and name not in allowed_non_class_files
            )
            if unexpected_regular_files:
                raise BaselineError(
                    "The original-baseline harness contains unexpected files: "
                    f"{unexpected_regular_files}"
                )
            for class_entry in class_entries:
                if b"ru/feytox/etherology/" in archive.read(entries[class_entry]):
                    raise BaselineError(
                        "The original-baseline harness links to Etherology implementation "
                        f"code: {class_entry}"
                    )

            mixin_entry = entries.get(expected_mixin)
            if mixin_entry is None or mixin_entry.is_dir():
                raise BaselineError("The original-baseline harness mixin is missing")
            try:
                mixin = json.loads(archive.read(mixin_entry))
            except (UnicodeDecodeError, json.JSONDecodeError) as exception:
                raise BaselineError(
                    "The original-baseline harness mixin config is invalid"
                ) from exception
            if mixin != {
                "required": True,
                "package": "dev.theplumteam.etherology.baseline.fabric.mixin",
                "compatibilityLevel": "JAVA_21",
                "client": (
                    ["GameRendererMixin", "PlayerEntityJumpInvoker"]
                    if expects_jump_invoker
                    else ["GameRendererMixin"]
                ),
                "injectors": {"defaultRequire": 1},
            }:
                raise BaselineError(
                    "The original-baseline harness mixin contract changed"
                )
    except (OSError, zipfile.BadZipFile, KeyError) as exception:
        raise BaselineError(
            f"Cannot inspect original-baseline harness {configuration.harness_path}: "
            f"{exception}"
        ) from exception


def profile_descriptor(configuration: Configuration) -> dict[str, object]:
    relative_manifest = configuration.manifest_path.relative_to(configuration.repository_root)
    bundle = bundle_spec(configuration)
    runtime = runtime_spec(configuration)
    harness = harness_spec(configuration)
    scenario = scenario_spec(configuration)
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "managed_by": "scripts/baseline/original_client.py",
        "isolation": {
            "scope": "repository-owned-ignored-state",
            "source_profiles": [],
        },
        "manifest": {
            "path": relative_manifest.as_posix(),
            "size": configuration.manifest_path.stat().st_size,
            "sha256": sha256_file(configuration.manifest_path),
        },
        "reference": {
            "reference_id": require_object(configuration.manifest, "provenance")[
                "reference_id"
            ],
            "bundle_size": bundle["size"],
            "bundle_sha256": bundle["sha256"],
            "members": [
                {
                    "file_name": member["file_name"],
                    "mod_id": member["mod_id"],
                    "size": member["size"],
                    "sha256": member["sha256"],
                }
                for member in member_specs(configuration)
            ],
        },
        "runtime": {
            "minecraft_version": runtime["minecraft_version"],
            "loader": runtime["loader"],
            "loader_version": runtime["loader_version"],
            "java_major": runtime["java_major"],
        },
        "harness": {
            "file_name": harness["file_name"],
            "mod_id": harness["mod_id"],
            "version": harness["version"],
            "size": harness["size"],
            "sha256": harness["sha256"],
        },
        "capture": {
            "scenario_id": scenario["id"],
            "screenshot_file": scenario["screenshot_file"],
            "world_directory_name": scenario["world_directory_name"],
        },
    }


def evidence_marker_path(configuration: Configuration, root: Path) -> Path:
    return evidence_directory(configuration, root) / ".etherology-original-evidence.json"


def scenario_root(configuration: Configuration, root: Path) -> Path:
    return evidence_directory(configuration, root) / str(scenario_spec(configuration)["id"])


def reports_directory(configuration: Configuration, root: Path) -> Path:
    return scenario_root(configuration, root) / "reports"


def screenshots_directory(configuration: Configuration, root: Path) -> Path:
    return scenario_root(configuration, root) / "screenshots"


def report_path(configuration: Configuration, root: Path) -> Path:
    return reports_directory(configuration, root) / str(
        scenario_spec(configuration)["report_file"]
    )


def completion_marker_path(configuration: Configuration, root: Path) -> Path:
    return reports_directory(configuration, root) / str(
        scenario_spec(configuration)["completion_marker_file"]
    )


def screenshot_path(configuration: Configuration, root: Path) -> Path:
    return screenshots_directory(configuration, root) / str(
        scenario_spec(configuration)["screenshot_file"]
    )


def save_directory(configuration: Configuration, root: Path) -> Path:
    return game_directory(configuration, root) / "saves" / str(
        scenario_spec(configuration)["world_directory_name"]
    )


def evidence_descriptor(configuration: Configuration) -> dict[str, object]:
    scenario = scenario_spec(configuration)
    framebuffer = require_object(scenario, "framebuffer")
    harness = harness_spec(configuration)
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "reference_id": require_object(configuration.manifest, "provenance")[
            "reference_id"
        ],
        "harness": {
            "mod_id": harness["mod_id"],
            "version": harness["version"],
            "size": harness["size"],
            "sha256": harness["sha256"],
        },
        "scenario": {
            "id": scenario["id"],
            "report_file": scenario["report_file"],
            "completion_marker_file": scenario["completion_marker_file"],
            "screenshot_file": scenario["screenshot_file"],
            "world_directory_name": scenario["world_directory_name"],
            "world_display_name": scenario["world_display_name"],
            "world_seed": scenario["world_seed"],
        },
        "capture": {
            "kind": "composed-minecraft-framebuffer",
            "width": framebuffer["width"],
            "height": framebuffer["height"],
        },
    }


def verify_capture_layout(
    configuration: Configuration,
    root: Path,
    *,
    require_fresh: bool,
) -> None:
    evidence_root = evidence_directory(configuration, root)
    marker = load_json_object(
        evidence_marker_path(configuration, root), "Original-baseline evidence marker"
    )
    if marker != evidence_descriptor(configuration):
        raise BaselineError(
            "Original-baseline evidence marker does not match the pinned capture contract"
        )
    expected_evidence_entries = {
        ".etherology-original-evidence.json",
        str(scenario_spec(configuration)["id"]),
    }
    actual_evidence_entries = {path.name for path in evidence_root.iterdir()}
    if actual_evidence_entries != expected_evidence_entries:
        raise BaselineError(
            "Original-baseline evidence root has an unexpected inventory: "
            f"missing={sorted(expected_evidence_entries - actual_evidence_entries)}, "
            f"extra={sorted(actual_evidence_entries - expected_evidence_entries)}"
        )
    scenario_directory = scenario_root(configuration, root)
    expected_scenario_entries = {"reports", "screenshots"}
    if scenario_directory.is_symlink() or not scenario_directory.is_dir():
        raise BaselineError(
            f"Original-baseline scenario directory is missing or linked: {scenario_directory}"
        )
    actual_scenario_entries = {path.name for path in scenario_directory.iterdir()}
    if actual_scenario_entries != expected_scenario_entries:
        raise BaselineError(
            "Original-baseline scenario root has an unexpected inventory: "
            f"missing={sorted(expected_scenario_entries - actual_scenario_entries)}, "
            f"extra={sorted(actual_scenario_entries - expected_scenario_entries)}"
        )
    for directory in (
        reports_directory(configuration, root),
        screenshots_directory(configuration, root),
    ):
        ensure_no_symlink_components(root, directory, "Capture output directory")
        if directory.is_symlink() or not directory.is_dir():
            raise BaselineError(f"Capture output directory is missing or linked: {directory}")
    if not require_fresh:
        return
    attempt = launch_attempt_path(configuration, root)
    if attempt.exists() or attempt.is_symlink():
        raise BaselineError(
            "This original-baseline profile already has a launch-attempt seal; "
            "create a new profile revision instead of reusing it"
        )
    for directory in (
        reports_directory(configuration, root),
        screenshots_directory(configuration, root),
    ):
        entries = list(directory.iterdir())
        if entries:
            raise BaselineError(
                f"Refusing to overwrite existing original-baseline evidence: {entries}"
            )
    world = save_directory(configuration, root)
    ensure_no_symlink_components(root, world, "Original-baseline scenario world")
    if world.exists() or world.is_symlink():
        raise BaselineError(f"Refusing to reuse an original-baseline world: {world}")
    extraction_directory = native_extraction_directory(configuration, root)
    ensure_no_symlink_components(
        temporary_directory(configuration, root),
        extraction_directory,
        "Runtime native extraction directory",
    )
    if extraction_directory.exists() or extraction_directory.is_symlink():
        raise BaselineError(
            "Runtime native extraction directory must be absent before launch"
        )


def write_json_atomic(path: Path, value: dict[str, object]) -> None:
    if path.exists() and path.is_symlink():
        raise BaselineError(f"Refusing to replace a linked JSON file: {path}")
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary_path = path.parent / f".{path.name}.{os.getpid()}.{uuid.uuid4().hex}.tmp"
    descriptor = os.open(temporary_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(value, handle, indent=2, sort_keys=True)
            handle.write("\n")
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def write_text_exclusive(path: Path, content: str) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        handle.write(content)


def write_bytes_exclusive(path: Path, content: bytes) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as handle:
        handle.write(content)


def write_json_exclusive(path: Path, value: dict[str, object]) -> None:
    """Writes a durable create-once JSON seal without a replace race."""

    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(value, handle, indent=2, sort_keys=True)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
    except BaseException:
        # A partial file is intentionally retained: its existence is still a
        # permanent record that the one permitted launch was attempted.
        raise
    directory_descriptor = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory_descriptor)
    finally:
        os.close(directory_descriptor)


@contextmanager
def acquire_runtime_lifecycle_lock(
    configuration: Configuration, root: Path
) -> Iterator[None]:
    path = lifecycle_lock_path(configuration, root)
    ensure_no_symlink_components(root, path, "Original-baseline lifecycle lock")
    if path.is_symlink() or not path.is_file() or path.stat().st_size != 0:
        raise BaselineError(
            f"Original-baseline lifecycle lock is missing, linked, or contaminated: {path}"
        )
    descriptor = os.open(path, os.O_RDWR | os.O_NOFOLLOW)
    try:
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exception:
            raise BaselineError(
                "Another controller invocation owns this profile's lifecycle lock"
            ) from exception
        yield
    finally:
        try:
            fcntl.flock(descriptor, fcntl.LOCK_UN)
        finally:
            os.close(descriptor)


def verify_owned_runtime(
    configuration: Configuration,
    root: Path | None = None,
    runtimes_root: Path = RUNTIMES_ROOT,
) -> Path:
    target = root or runtime_root(configuration, runtimes_root)
    expected = runtime_root(configuration, runtimes_root)
    if target.absolute() != expected.absolute():
        raise BaselineError(f"Runtime path is not the exact owned target: {target}")
    ensure_no_symlink_components(runtimes_root, target, "Original baseline runtime")
    if not target.is_dir() or target.is_symlink():
        raise BaselineError(f"Owned original-baseline runtime is missing or linked: {target}")
    path = marker_path(configuration, target)
    marker = load_json_object(path, "Original-baseline ownership marker")
    if marker != profile_descriptor(configuration):
        raise BaselineError(
            f"Runtime ownership marker does not match this exact manifest: {path}"
        )
    for field_name, description in (
        ("game_directory", "game directory"),
        ("launcher_directory", "launcher directory"),
        ("evidence_directory", "evidence directory"),
        ("logs_directory", "controller logs directory"),
        ("installer_directory", "installer directory"),
        ("home_directory", "isolated home directory"),
        ("temporary_directory", "isolated temporary directory"),
    ):
        child = owned_child(configuration, field_name, target)
        ensure_no_symlink_components(target, child, description)
        if not child.is_dir() or child.is_symlink():
            raise BaselineError(f"Owned {description} is missing or linked: {child}")
    lock = lifecycle_lock_path(configuration, target)
    ensure_no_symlink_components(target, lock, "Original-baseline lifecycle lock")
    if lock.is_symlink() or not lock.is_file() or lock.stat().st_size != 0:
        raise BaselineError(
            f"Owned lifecycle lock is missing, linked, or contaminated: {lock}"
        )
    return target


def ensure_runtimes_root_for_write(runtimes_root: Path = RUNTIMES_ROOT) -> None:
    state_root = runtimes_root.parent
    for path, description in (
        (state_root, "Original-baseline state root"),
        (runtimes_root, "Original-baseline runtimes root"),
    ):
        if path.is_symlink():
            raise BaselineError(f"{description} must not be a symlink: {path}")
        if path.exists() and not path.is_dir():
            raise BaselineError(f"{description} must be a directory: {path}")
    state_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    runtimes_root.mkdir(mode=0o700, exist_ok=True)
    ensure_no_symlink_components(state_root, runtimes_root, "Runtime state")


def java_path_looks_like_game_profile(path: Path) -> bool:
    folded_parts = [part.casefold() for part in path.absolute().parts]
    return (
        "profiles" in folded_parts
        or "instances" in folded_parts
        or any("modrinthapp" in part for part in folded_parts)
    )


def java_major_version(java_path: Path) -> int | None:
    if java_path.is_symlink() or not java_path.is_file() or not os.access(java_path, os.X_OK):
        return None
    try:
        completed = subprocess.run(
            [str(java_path), "-XshowSettings:properties", "-version"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=20,
            check=False,
            env={
                "LANG": "C",
                "LC_ALL": "C",
                "PATH": "/usr/bin:/bin:/usr/sbin:/sbin",
            },
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    match = re.search(r"java\.specification\.version\s*=\s*(\d+)", completed.stdout)
    if completed.returncode != 0 or match is None:
        return None
    return int(match.group(1))


def select_java_21(
    candidates: Iterable[Path],
    probe: Callable[[Path], int | None] = java_major_version,
) -> Path:
    checked: set[Path] = set()
    for candidate in candidates:
        if java_path_looks_like_game_profile(candidate):
            continue
        resolved = candidate.resolve(strict=False)
        if resolved in checked or java_path_looks_like_game_profile(resolved):
            continue
        checked.add(resolved)
        if probe(resolved) == 21:
            return resolved
    raise BaselineError(
        "No safe Java 21 runtime was found outside game profiles; set "
        f"{JAVA_OVERRIDE_ENVIRONMENT_VARIABLE} to a Java 21 executable"
    )


def java_runtime_descriptor(java_path: Path) -> dict[str, object]:
    java = java_path.resolve(strict=True)
    java_home = java.parent.parent
    if java_path_looks_like_game_profile(java_home):
        raise BaselineError("Selected Java runtime must remain outside game profiles")
    if java_home.is_symlink() or not java_home.is_dir():
        raise BaselineError(f"Selected Java home is missing or linked: {java_home}")
    inventory: list[dict[str, object]] = []
    total_size = 0
    for path in sorted(java_home.rglob("*"), key=lambda value: value.as_posix()):
        ensure_no_symlink_components(java_home, path, "Selected Java runtime inventory")
        if path.is_symlink():
            raise BaselineError(f"Selected Java runtime contains a symlink: {path}")
        if path.is_dir():
            continue
        if not path.is_file():
            raise BaselineError(f"Selected Java runtime contains a non-file: {path}")
        total_size += path.stat().st_size
        if len(inventory) >= 8192 or total_size > 2 * 1024 * 1024 * 1024:
            raise BaselineError("Selected Java runtime exceeds its inventory bounds")
        inventory.append(
            {
                "path": path.relative_to(java_home).as_posix(),
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
            }
        )
    java_relative_path = java.relative_to(java_home).as_posix()
    if not inventory or java_relative_path not in {
        str(value["path"]) for value in inventory
    }:
        raise BaselineError("Selected Java runtime inventory lacks bin/java")
    return {
        "home": str(java_home),
        "java": java_relative_path,
        "file_count": len(inventory),
        "total_size": total_size,
        "files_sha256": canonical_json_sha256(inventory),
    }


def resolve_java_21() -> Path:
    candidates: list[Path] = []
    override = os.environ.get(JAVA_OVERRIDE_ENVIRONMENT_VARIABLE)
    if override:
        override_path = Path(override)
        if java_path_looks_like_game_profile(override_path):
            raise BaselineError(
                f"{JAVA_OVERRIDE_ENVIRONMENT_VARIABLE} must not point into a game profile"
            )
        candidates.append(override_path)
    java_home_tool = Path("/usr/libexec/java_home")
    if java_home_tool.is_file() and not java_home_tool.is_symlink():
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
        candidates.extend(sorted(gradle_jdks.glob("*/Contents/Home/bin/java")))
        candidates.extend(sorted(gradle_jdks.glob("*/bin/java")))
    return select_java_21(candidates)


def version_id(configuration: Configuration) -> str:
    runtime = runtime_spec(configuration)
    return f"fabric-loader-{runtime['loader_version']}-{runtime['minecraft_version']}"


def copy_response(response: BinaryIO, handle: BinaryIO, maximum_size: int) -> int:
    total_size = 0
    while True:
        chunk = response.read(1024 * 1024)
        if not chunk:
            return total_size
        total_size += len(chunk)
        if total_size > maximum_size:
            raise BaselineError("Download exceeded its maximum allowed size")
        handle.write(chunk)


def download_pinned_file(
    url: str,
    destination: Path,
    expected_sha256: str,
    description: str,
    expected_size: int | None = None,
) -> None:
    if destination.exists() or destination.is_symlink():
        raise BaselineError(f"Refusing to replace an existing download: {destination}")
    request = urllib.request.Request(
        url, headers={"User-Agent": "Etherology-Original-Baseline/1"}
    )
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            try:
                with urllib.request.urlopen(request, timeout=120) as response:
                    copy_response(response, handle, MAXIMUM_DOWNLOAD_SIZE)
            except (OSError, urllib.error.URLError) as exception:
                raise BaselineError(f"Cannot download {description}: {exception}") from exception
        verify_exact_file(destination, expected_sha256, expected_size, description)
    except Exception:
        destination.unlink(missing_ok=True)
        raise


def pinned_fabric_library_cache_root(configuration: Configuration) -> Path:
    return (
        configuration.manifest_path.parent
        / ".state"
        / PINNED_FABRIC_LIBRARY_CACHE_DIRECTORY
    )


def copy_pinned_cached_file(
    cache_root: Path,
    source: Path,
    destination: Path,
    expected_sha256: str,
    expected_sha1: str,
    expected_size: int,
    description: str,
) -> bool:
    if not cache_root.exists() and not cache_root.is_symlink():
        return False
    if cache_root.is_symlink() or not cache_root.is_dir():
        raise BaselineError(f"Pinned cache root is missing or linked: {cache_root}")
    ensure_no_symlink_components(cache_root, source, f"{description} cache source")
    if not source.exists() and not source.is_symlink():
        return False
    if source.is_symlink() or not source.is_file():
        raise BaselineError(f"{description} cache source is missing or linked: {source}")
    if destination.exists() or destination.is_symlink():
        raise BaselineError(f"Refusing to replace an existing download: {destination}")

    source_flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    try:
        source_descriptor = os.open(source, source_flags)
    except OSError as exception:
        raise BaselineError(
            f"Cannot open {description} cache source: {exception}"
        ) from exception
    destination_descriptor: int | None = None
    try:
        with os.fdopen(source_descriptor, "rb") as source_handle:
            source_status = os.fstat(source_handle.fileno())
            if not stat.S_ISREG(source_status.st_mode):
                raise BaselineError(f"{description} cache source is not a regular file")
            if source_status.st_size != expected_size:
                raise BaselineError(
                    f"{description} cache source has size {source_status.st_size}, "
                    f"expected {expected_size}"
                )
            destination_descriptor = os.open(
                destination,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                0o600,
            )
            with os.fdopen(destination_descriptor, "wb") as destination_handle:
                destination_descriptor = None
                copy_response(source_handle, destination_handle, MAXIMUM_DOWNLOAD_SIZE)
        verify_exact_file(
            destination,
            expected_sha256,
            expected_size,
            f"Cached {description}",
        )
        if sha1_file(destination) != expected_sha1:
            raise BaselineError(f"Cached {description} failed SHA-1 validation")
    except Exception:
        if destination_descriptor is not None:
            os.close(destination_descriptor)
        destination.unlink(missing_ok=True)
        raise
    return True


def pinned_http_distribution_identity() -> str:
    return hashlib.sha256(
        "\n".join(value[4] for value in PINNED_HTTP_DISTRIBUTIONS).encode("ascii")
    ).hexdigest()


def http_module_names() -> list[str]:
    top_level_names = tuple(value[5] for value in PINNED_HTTP_DISTRIBUTIONS)
    return sorted(
        name
        for name in sys.modules
        if any(name == prefix or name.startswith(prefix + ".") for prefix in top_level_names)
    )


def verify_pinned_http_distribution(
    distribution_name: str,
    expected_version: str,
    expected_dist_info_name: str,
    expected_record_size: int,
    expected_record_sha256: str,
    module_name: str,
) -> dict[str, object]:
    try:
        distribution = importlib.metadata.distribution(distribution_name)
    except importlib.metadata.PackageNotFoundError as exception:
        raise BaselineError(
            f"Pinned Python dependency is missing: {distribution_name}"
        ) from exception
    if distribution.version != expected_version:
        raise BaselineError(
            f"Pinned Python dependency {distribution_name} must be {expected_version}, "
            f"found {distribution.version}"
        )
    distribution_root = Path(distribution._path)
    if distribution_root.name != expected_dist_info_name:
        raise BaselineError(
            f"Pinned Python dependency has an unexpected distribution path: {distribution_name}"
        )
    site_packages = distribution_root.parent
    module_root = site_packages / module_name
    record_path = distribution_root / "RECORD"
    ensure_no_symlink_components(
        site_packages, record_path, f"{distribution_name} RECORD"
    )
    verify_exact_file(
        record_path,
        expected_record_sha256,
        expected_record_size,
        f"Pinned {distribution_name} RECORD",
    )
    try:
        rows = list(csv.reader(record_path.read_text(encoding="utf-8").splitlines()))
    except (OSError, csv.Error, UnicodeDecodeError) as exception:
        raise BaselineError(f"Cannot parse pinned {distribution_name} RECORD") from exception
    if not rows or any(len(row) != 3 for row in rows):
        raise BaselineError(f"Pinned {distribution_name} RECORD has an invalid shape")

    files: list[dict[str, object]] = []
    expected_inventory_paths: set[Path] = {record_path}
    seen_paths: set[str] = set()
    for raw_path, raw_digest, raw_size in rows:
        relative_path = PurePosixPath(raw_path)
        if not raw_path or raw_path in seen_paths or relative_path.is_absolute() or "\\" in raw_path:
            raise BaselineError(
                f"Pinned {distribution_name} RECORD contains an unsafe path: {raw_path}"
            )
        seen_paths.add(raw_path)
        path = Path(distribution.locate_file(raw_path))
        if not raw_digest and not raw_size:
            if raw_path.endswith(".pyc") and "__pycache__" in relative_path.parts:
                continue
            if path.absolute() != record_path.absolute():
                raise BaselineError(
                    f"Pinned {distribution_name} RECORD has an unpinned executable file: "
                    f"{raw_path}"
                )
            continue
        algorithm, separator, encoded_digest = raw_digest.partition("=")
        if algorithm != "sha256" or not separator or not raw_size.isdecimal():
            raise BaselineError(
                f"Pinned {distribution_name} RECORD has an invalid digest: {raw_path}"
            )
        try:
            digest_bytes = base64.urlsafe_b64decode(
                encoded_digest + "=" * (-len(encoded_digest) % 4)
            )
        except (ValueError, TypeError) as exception:
            raise BaselineError(
                f"Pinned {distribution_name} RECORD has invalid base64: {raw_path}"
            ) from exception
        if len(digest_bytes) != hashlib.sha256().digest_size:
            raise BaselineError(
                f"Pinned {distribution_name} RECORD digest has the wrong size: {raw_path}"
            )
        expected_sha256 = digest_bytes.hex()
        verify_exact_file(
            path,
            expected_sha256,
            int(raw_size),
            f"Pinned {distribution_name} file {raw_path}",
        )
        if is_relative_to(path.absolute(), module_root.absolute()) or is_relative_to(
            path.absolute(), distribution_root.absolute()
        ):
            expected_inventory_paths.add(path)
        files.append(
            {
                "path": raw_path,
                "size": int(raw_size),
                "sha256": expected_sha256,
            }
        )

    actual_inventory_paths: set[Path] = set()
    for root_path in (module_root, distribution_root):
        if root_path.is_symlink() or not root_path.is_dir():
            raise BaselineError(
                f"Pinned {distribution_name} package root is missing or linked"
            )
        for path in root_path.rglob("*"):
            ensure_no_symlink_components(
                root_path, path, f"Pinned {distribution_name} inventory"
            )
            if path.is_symlink():
                raise BaselineError(
                    f"Pinned {distribution_name} inventory contains a symlink: {path}"
                )
            if path.is_dir():
                continue
            if not path.is_file():
                raise BaselineError(
                    f"Pinned {distribution_name} inventory contains a non-file: {path}"
                )
            if "__pycache__" in path.parts and path.suffix == ".pyc":
                continue
            actual_inventory_paths.add(path)
    if actual_inventory_paths != expected_inventory_paths:
        raise BaselineError(
            f"Pinned {distribution_name} inventory differs from its RECORD"
        )
    return {
        "distribution": distribution_name,
        "version": expected_version,
        "module_name": module_name,
        "module_root": str(module_root),
        "record": {
            "path": record_path.relative_to(site_packages).as_posix(),
            "size": expected_record_size,
            "sha256": expected_record_sha256,
        },
        "files": files,
    }


def verify_loaded_http_module_origins(
    descriptors: list[dict[str, object]],
) -> None:
    roots = {
        str(descriptor["module_name"]): Path(str(descriptor["module_root"])).resolve(
            strict=True
        )
        for descriptor in descriptors
    }
    allowed_roots = list(roots.values())
    for module_name in http_module_names():
        module = sys.modules.get(module_name)
        if not isinstance(module, types.ModuleType):
            raise BaselineError(
                f"Loaded HTTP dependency module has an invalid object: {module_name}"
            )
        raw_file = getattr(module, "__file__", None)
        specification = getattr(module, "__spec__", None)
        if not isinstance(raw_file, str) or not isinstance(
            specification, importlib.machinery.ModuleSpec
        ):
            raise BaselineError(
                f"Loaded HTTP dependency module has no exact provenance: {module_name}"
            )
        raw_path = Path(raw_file)
        if raw_path.is_symlink() or not raw_path.is_file():
            raise BaselineError(
                f"Loaded HTTP dependency module is missing or linked: {module_name}"
            )
        resolved_path = raw_path.resolve(strict=True)
        owning_roots = [
            root for root in allowed_roots if is_relative_to(resolved_path, root)
        ]
        if len(owning_roots) != 1:
            raise BaselineError(
                f"Loaded HTTP dependency escaped its pinned distribution: {module_name}"
            )
        if (
            not isinstance(specification.origin, str)
            or Path(specification.origin).resolve(strict=True) != resolved_path
        ):
            raise BaselineError(
                f"Loaded HTTP dependency spec disagrees with its file: {module_name}"
            )
        loader = specification.loader
        if not isinstance(
            loader,
            (
                importlib.machinery.SourceFileLoader,
                importlib.machinery.ExtensionFileLoader,
            ),
        ):
            raise BaselineError(
                f"Loaded HTTP dependency used an unsupported loader: {module_name}"
            )
        loader_path = getattr(loader, "path", None)
        if not isinstance(loader_path, str) or Path(loader_path).resolve(
            strict=True
        ) != resolved_path:
            raise BaselineError(
                f"Loaded HTTP dependency loader disagrees with its file: {module_name}"
            )
        raw_search_locations = getattr(module, "__path__", None)
        if raw_search_locations is not None:
            if isinstance(raw_search_locations, (str, bytes)):
                raise BaselineError(
                    f"Loaded HTTP dependency has invalid search roots: {module_name}"
                )
            search_locations = [
                Path(str(value)).resolve(strict=True) for value in raw_search_locations
            ]
            if not search_locations or any(
                not any(is_relative_to(path, root) for root in allowed_roots)
                for path in search_locations
            ):
                raise BaselineError(
                    f"Loaded HTTP dependency searches outside pinned files: {module_name}"
                )
    for top_level_name, root in roots.items():
        module = sys.modules.get(top_level_name)
        if module is None:
            continue
        if not isinstance(module, types.ModuleType) or Path(
            str(getattr(module, "__file__", ""))
        ).resolve(strict=True) != (root / "__init__.py"):
            raise BaselineError(
                f"Loaded HTTP dependency has the wrong entry point: {top_level_name}"
            )


def verify_http_dependency_distributions() -> list[dict[str, object]]:
    global _VERIFIED_HTTP_DISTRIBUTIONS_SHA256

    loaded = bool(http_module_names())
    expected_identity = pinned_http_distribution_identity()
    if loaded and _VERIFIED_HTTP_DISTRIBUTIONS_SHA256 != expected_identity:
        raise BaselineError(
            "HTTP dependency code was imported before complete distribution validation"
        )
    descriptors = [
        verify_pinned_http_distribution(*specification)
        for specification in PINNED_HTTP_DISTRIBUTIONS
    ]
    _VERIFIED_HTTP_DISTRIBUTIONS_SHA256 = expected_identity
    if loaded:
        verify_loaded_http_module_origins(descriptors)
    return descriptors


def verify_launcher_distribution() -> dict[str, object]:
    global _VERIFIED_LAUNCHER_DISTRIBUTION_SHA256

    http_dependencies = verify_http_dependency_distributions()
    launcher_modules_loaded = any(
        name == "minecraft_launcher_lib" or name.startswith("minecraft_launcher_lib.")
        for name in sys.modules
    )
    if (
        launcher_modules_loaded
        and _VERIFIED_LAUNCHER_DISTRIBUTION_SHA256
        != PINNED_LAUNCHER_RECORD_SHA256
    ):
        raise BaselineError(
            "minecraft-launcher-lib code was imported before complete distribution validation"
        )
    try:
        distribution = importlib.metadata.distribution("minecraft-launcher-lib")
    except importlib.metadata.PackageNotFoundError as exception:
        raise BaselineError(
            "minecraft-launcher-lib is missing from the controller environment"
        ) from exception
    actual_version = distribution.version
    if actual_version != EXPECTED_LAUNCHER_LIBRARY_VERSION:
        raise BaselineError(
            "minecraft-launcher-lib must be exactly "
            f"{EXPECTED_LAUNCHER_LIBRARY_VERSION}, found {actual_version}"
        )

    distribution_root = Path(distribution._path)
    if distribution_root.name != "minecraft_launcher_lib-8.0.dist-info":
        raise BaselineError("minecraft-launcher-lib has an unexpected distribution path")
    site_packages = distribution_root.parent
    record_path = distribution_root / "RECORD"
    ensure_no_symlink_components(
        site_packages, record_path, "minecraft-launcher-lib RECORD"
    )
    verify_exact_file(
        record_path,
        PINNED_LAUNCHER_RECORD_SHA256,
        PINNED_LAUNCHER_RECORD_SIZE,
        "Pinned minecraft-launcher-lib RECORD",
    )
    try:
        rows = list(csv.reader(record_path.read_text(encoding="utf-8").splitlines()))
    except (OSError, csv.Error, UnicodeDecodeError) as exception:
        raise BaselineError(
            "Cannot parse the pinned minecraft-launcher-lib RECORD"
        ) from exception
    if not rows or any(len(row) != 3 for row in rows):
        raise BaselineError("minecraft-launcher-lib RECORD has an invalid row shape")

    files: list[dict[str, object]] = []
    expected_regular_paths = {record_path}
    seen_paths: set[str] = set()
    for raw_path, raw_digest, raw_size in rows:
        relative_path = PurePosixPath(raw_path)
        if (
            not raw_path
            or raw_path in seen_paths
            or relative_path.is_absolute()
            or ".." in relative_path.parts
            or "\\" in raw_path
        ):
            raise BaselineError(
                f"minecraft-launcher-lib RECORD contains an unsafe path: {raw_path}"
            )
        seen_paths.add(raw_path)
        path = site_packages.joinpath(*relative_path.parts)
        ensure_no_symlink_components(
            site_packages, path, "minecraft-launcher-lib distribution file"
        )
        if not raw_digest and not raw_size:
            if raw_path.endswith(".pyc") and "__pycache__" in relative_path.parts:
                continue
            if path != record_path:
                raise BaselineError(
                    "minecraft-launcher-lib RECORD has an unpinned executable file: "
                    f"{raw_path}"
                )
            continue
        algorithm, separator, encoded_digest = raw_digest.partition("=")
        if algorithm != "sha256" or not separator or not raw_size.isdecimal():
            raise BaselineError(
                f"minecraft-launcher-lib RECORD has an invalid digest: {raw_path}"
            )
        try:
            digest_bytes = base64.urlsafe_b64decode(
                encoded_digest + "=" * (-len(encoded_digest) % 4)
            )
        except (ValueError, TypeError) as exception:
            raise BaselineError(
                f"minecraft-launcher-lib RECORD has invalid base64: {raw_path}"
            ) from exception
        if len(digest_bytes) != hashlib.sha256().digest_size:
            raise BaselineError(
                f"minecraft-launcher-lib RECORD digest has the wrong size: {raw_path}"
            )
        expected_sha256 = digest_bytes.hex()
        verify_exact_file(
            path,
            expected_sha256,
            int(raw_size),
            f"Pinned minecraft-launcher-lib file {raw_path}",
        )
        expected_regular_paths.add(path)
        files.append(
            {
                "path": raw_path,
                "size": int(raw_size),
                "sha256": expected_sha256,
            }
        )

    package_root = site_packages / "minecraft_launcher_lib"
    actual_regular_paths: set[Path] = set()
    for root_path in (package_root, distribution_root):
        for path in root_path.rglob("*"):
            ensure_no_symlink_components(root_path, path, "Launcher distribution inventory")
            if path.is_symlink():
                raise BaselineError(
                    f"minecraft-launcher-lib distribution contains a symlink: {path}"
                )
            if path.is_dir():
                continue
            if not path.is_file():
                raise BaselineError(
                    f"minecraft-launcher-lib distribution contains a non-file: {path}"
                )
            if "__pycache__" in path.parts and path.suffix == ".pyc":
                continue
            actual_regular_paths.add(path)
    if actual_regular_paths != expected_regular_paths:
        raise BaselineError(
            "minecraft-launcher-lib regular-file inventory differs from its pinned RECORD"
        )

    _VERIFIED_LAUNCHER_DISTRIBUTION_SHA256 = PINNED_LAUNCHER_RECORD_SHA256
    if launcher_modules_loaded:
        verify_loaded_launcher_module_origins(package_root)

    python_executable = Path(sys.executable).resolve(strict=True)
    verify_exact_file(
        python_executable,
        sha256_file(python_executable),
        python_executable.stat().st_size,
        "Controller Python executable",
    )
    return {
        "distribution": "minecraft-launcher-lib",
        "version": EXPECTED_LAUNCHER_LIBRARY_VERSION,
        "package_root": str(package_root),
        "record": {
            "path": record_path.relative_to(site_packages).as_posix(),
            "size": PINNED_LAUNCHER_RECORD_SIZE,
            "sha256": PINNED_LAUNCHER_RECORD_SHA256,
        },
        "files": files,
        "http_dependencies": http_dependencies,
        "ambient_bytecode_cache_disabled": True,
        "python": {
            "path": str(python_executable),
            "version": platform.python_version(),
            "size": python_executable.stat().st_size,
            "sha256": sha256_file(python_executable),
        },
        "rule_platform": {
            "system": platform.system(),
            "machine": platform.machine(),
            "release": platform.release(),
        },
    }


def configure_launcher_bytecode_cache(bytecode_cache_root: Path) -> None:
    cache_parent = bytecode_cache_root.parent
    ensure_no_symlink_components(
        cache_parent, bytecode_cache_root, "Launcher bytecode cache"
    )
    if cache_parent.is_symlink() or not cache_parent.is_dir():
        raise BaselineError(
            f"Launcher bytecode-cache parent is missing or linked: {cache_parent}"
        )
    if bytecode_cache_root.exists() or bytecode_cache_root.is_symlink():
        raise BaselineError(
            "Launcher bytecode-cache prefix must remain absent so ambient bytecode "
            "cannot be read"
        )
    sys.pycache_prefix = str(bytecode_cache_root)
    sys.dont_write_bytecode = True


def verify_launcher_library(bytecode_cache_root: Path) -> dict[str, object]:
    descriptor = verify_launcher_distribution()
    configure_launcher_bytecode_cache(bytecode_cache_root)
    return descriptor


def launcher_module_names() -> list[str]:
    return sorted(
        name
        for name in sys.modules
        if name == "minecraft_launcher_lib"
        or name.startswith("minecraft_launcher_lib.")
    )


def resolved_regular_module_file(
    module: types.ModuleType, module_name: str, package_root: Path
) -> Path:
    raw_file = getattr(module, "__file__", None)
    specification = getattr(module, "__spec__", None)
    if not isinstance(raw_file, str) or not isinstance(
        specification, importlib.machinery.ModuleSpec
    ):
        raise BaselineError(
            f"Loaded launcher module has no exact file provenance: {module_name}"
        )
    raw_path = Path(raw_file)
    ensure_no_symlink_components(package_root, raw_path, "Loaded launcher module")
    if raw_path.is_symlink() or not raw_path.is_file():
        raise BaselineError(
            f"Loaded launcher module is missing or linked: {module_name}"
        )
    resolved_path = raw_path.resolve(strict=True)
    resolved_root = package_root.resolve(strict=True)
    if not is_relative_to(resolved_path, resolved_root):
        raise BaselineError(
            f"Loaded launcher module escaped the verified package: {module_name}"
        )
    raw_origin = specification.origin
    if not isinstance(raw_origin, str) or Path(raw_origin).resolve(
        strict=True
    ) != resolved_path:
        raise BaselineError(
            f"Loaded launcher module spec disagrees with its file: {module_name}"
        )
    loader = specification.loader
    if not isinstance(loader, importlib.machinery.SourceFileLoader):
        raise BaselineError(
            f"Loaded launcher module did not use the standard source loader: {module_name}"
        )
    if Path(loader.path).resolve(strict=True) != resolved_path:
        raise BaselineError(
            f"Loaded launcher module loader disagrees with its file: {module_name}"
        )
    return resolved_path


def verify_loaded_launcher_module_origins(package_root: Path) -> None:
    resolved_root = package_root.resolve(strict=True)
    names = launcher_module_names()
    if not names:
        return
    if "minecraft_launcher_lib" not in names:
        raise BaselineError("Launcher submodules were loaded without their verified package")
    for module_name in names:
        module = sys.modules.get(module_name)
        if not isinstance(module, types.ModuleType):
            raise BaselineError(
                f"Loaded launcher module has an invalid object: {module_name}"
            )
        module_file = resolved_regular_module_file(module, module_name, resolved_root)
        if module_name == "minecraft_launcher_lib" and module_file != (
            resolved_root / "__init__.py"
        ):
            raise BaselineError("Loaded launcher package has the wrong entry point")
        raw_search_locations = getattr(module, "__path__", None)
        if raw_search_locations is None:
            continue
        if isinstance(raw_search_locations, (str, bytes)):
            raise BaselineError(
                f"Loaded launcher package has invalid search locations: {module_name}"
            )
        try:
            search_locations = [
                Path(str(value)).resolve(strict=True) for value in raw_search_locations
            ]
        except (OSError, RuntimeError, TypeError, ValueError) as exception:
            raise BaselineError(
                f"Loaded launcher package has unreadable search locations: {module_name}"
            ) from exception
        if not search_locations or any(
            not is_relative_to(path, resolved_root) for path in search_locations
        ):
            raise BaselineError(
                f"Loaded launcher package searches outside verified files: {module_name}"
            )
        if module_name == "minecraft_launcher_lib" and search_locations != [
            resolved_root
        ]:
            raise BaselineError(
                "Loaded launcher package does not use its one verified search root"
            )


@contextmanager
def standard_import_path() -> Iterator[None]:
    original_meta_path = sys.meta_path[:]
    sys.meta_path[:] = [
        importlib.machinery.BuiltinImporter,
        importlib.machinery.FrozenImporter,
        importlib.machinery.PathFinder,
    ]
    try:
        yield
    finally:
        sys.meta_path[:] = original_meta_path


def verify_launcher_specification(
    specification: importlib.machinery.ModuleSpec | None, package_root: Path
) -> None:
    expected_entry_point = (package_root / "__init__.py").resolve(strict=True)
    if (
        specification is None
        or not isinstance(specification.origin, str)
        or Path(specification.origin).resolve(strict=True) != expected_entry_point
        or specification.submodule_search_locations is None
        or [
            Path(str(value)).resolve(strict=True)
            for value in specification.submodule_search_locations
        ]
        != [package_root.resolve(strict=True)]
    ):
        raise BaselineError(
            "Normal Python import resolution does not select the verified "
            "minecraft-launcher-lib package"
        )


def verify_http_import_specifications(
    descriptors: list[dict[str, object]],
) -> None:
    for descriptor in descriptors:
        module_name = str(descriptor["module_name"])
        module_root = Path(str(descriptor["module_root"])).resolve(strict=True)
        expected_entry_point = module_root / "__init__.py"
        for specification in (
            importlib.util.find_spec(module_name),
            importlib.machinery.PathFinder.find_spec(
                module_name, [str(module_root.parent)]
            ),
        ):
            if (
                specification is None
                or not isinstance(specification.origin, str)
                or Path(specification.origin).resolve(strict=True)
                != expected_entry_point
                or specification.submodule_search_locations is None
                or [
                    Path(str(value)).resolve(strict=True)
                    for value in specification.submodule_search_locations
                ]
                != [module_root]
            ):
                raise BaselineError(
                    f"Normal Python import resolution does not select pinned {module_name}"
                )


def verify_optional_http_modules_absent() -> None:
    loaded = [
        name
        for name in sys.modules
        if any(
            name == prefix or name.startswith(prefix + ".")
            for prefix in UNPINNED_OPTIONAL_HTTP_MODULES
        )
    ]
    if loaded:
        raise BaselineError(
            f"Unpinned optional HTTP modules are already loaded: {sorted(loaded)}"
        )
    discovered = [
        module_name
        for module_name in UNPINNED_OPTIONAL_HTTP_MODULES
        if importlib.machinery.PathFinder.find_spec(module_name) is not None
    ]
    if discovered:
        raise BaselineError(
            f"Unpinned optional HTTP modules are importable: {sorted(discovered)}"
        )


def preflight_launcher_import_resolution() -> dict[str, object]:
    descriptor = verify_launcher_distribution()
    package_root = Path(str(descriptor["package_root"])).resolve(strict=True)
    raw_http_dependencies = descriptor.get("http_dependencies")
    if not isinstance(raw_http_dependencies, list) or not all(
        isinstance(value, dict) for value in raw_http_dependencies
    ):
        raise BaselineError("Verified launcher descriptor lacks HTTP dependencies")
    http_dependencies = [dict(value) for value in raw_http_dependencies]
    with standard_import_path():
        verify_optional_http_modules_absent()
        verify_http_import_specifications(http_dependencies)
        verify_launcher_specification(
            importlib.util.find_spec("minecraft_launcher_lib"), package_root
        )
        verify_launcher_specification(
            importlib.machinery.PathFinder.find_spec(
                "minecraft_launcher_lib", [str(package_root.parent)]
            ),
            package_root,
        )
    return descriptor


def load_verified_launcher_module(
    module_name: str, bytecode_cache_root: Path
) -> types.ModuleType:
    if not (
        module_name == "minecraft_launcher_lib"
        or module_name.startswith("minecraft_launcher_lib.")
    ):
        raise BaselineError(f"Refusing to load an unrelated module: {module_name}")
    descriptor = verify_launcher_library(bytecode_cache_root)
    package_root = Path(str(descriptor["package_root"])).resolve(strict=True)
    raw_http_dependencies = descriptor.get("http_dependencies")
    if not isinstance(raw_http_dependencies, list) or not all(
        isinstance(value, dict) for value in raw_http_dependencies
    ):
        raise BaselineError("Verified launcher descriptor lacks HTTP dependencies")
    http_dependencies = [dict(value) for value in raw_http_dependencies]
    existing_names = launcher_module_names()
    existing_http_names = set(http_module_names())
    if existing_names:
        verify_loaded_launcher_module_origins(package_root)
        verify_loaded_http_module_origins(http_dependencies)
        with standard_import_path():
            verify_optional_http_modules_absent()
            verify_http_import_specifications(http_dependencies)
            try:
                module = importlib.import_module(module_name)
            except ImportError as exception:
                raise BaselineError(
                    f"Verified launcher module could not be imported: {module_name}"
                ) from exception
        verify_loaded_launcher_module_origins(package_root)
        verify_loaded_http_module_origins(http_dependencies)
        verify_optional_http_modules_absent()
        return module

    site_packages = package_root.parent
    with standard_import_path():
        verify_optional_http_modules_absent()
        verify_http_import_specifications(http_dependencies)
        verify_launcher_specification(
            importlib.util.find_spec("minecraft_launcher_lib"), package_root
        )
        verify_launcher_specification(
            importlib.machinery.PathFinder.find_spec(
                "minecraft_launcher_lib", [str(site_packages)]
            ),
            package_root,
        )
        specification = importlib.util.spec_from_file_location(
            "minecraft_launcher_lib",
            package_root / "__init__.py",
            submodule_search_locations=[str(package_root)],
        )
        verify_launcher_specification(specification, package_root)
        if specification is None or specification.loader is None:
            raise BaselineError("Cannot create the verified launcher package loader")
        package = importlib.util.module_from_spec(specification)
        sys.modules["minecraft_launcher_lib"] = package
        try:
            specification.loader.exec_module(package)
            module = importlib.import_module(module_name)
        except Exception as exception:
            for loaded_name in launcher_module_names():
                sys.modules.pop(loaded_name, None)
            for loaded_name in set(http_module_names()) - existing_http_names:
                sys.modules.pop(loaded_name, None)
            raise BaselineError(
                f"Verified launcher module could not be imported: {module_name}"
            ) from exception
    verify_loaded_launcher_module_origins(package_root)
    verify_loaded_http_module_origins(http_dependencies)
    verify_optional_http_modules_absent()
    return module


def fabric_metadata_path(configuration: Configuration, root: Path) -> Path:
    identifier = version_id(configuration)
    return launcher_directory(configuration, root) / "versions" / identifier / f"{identifier}.json"


def vanilla_metadata_path(configuration: Configuration, root: Path) -> Path:
    minecraft_version = str(runtime_spec(configuration)["minecraft_version"])
    return (
        launcher_directory(configuration, root)
        / "versions"
        / minecraft_version
        / f"{minecraft_version}.json"
    )


def pinned_vanilla_metadata_path(configuration: Configuration, root: Path) -> Path:
    minecraft_version = str(runtime_spec(configuration)["minecraft_version"])
    return installer_directory(configuration, root) / (
        f"minecraft-{minecraft_version}-official-metadata.json"
    )


def pinned_fabric_profile_path(configuration: Configuration, root: Path) -> Path:
    return installer_directory(configuration, root) / "fabric-loader-profile.json"


def native_extraction_directory(configuration: Configuration, root: Path) -> Path:
    return temporary_directory(configuration, root) / "minecraft-natives"


def skin_cache_directory(configuration: Configuration, root: Path) -> Path:
    return launcher_directory(configuration, root) / Path(
        *MUTABLE_SKIN_CACHE_RELATIVE_PATH.parts
    )


def expected_asset_index_contract(configuration: Configuration) -> dict[str, object]:
    pinned = require_object(
        require_object(runtime_spec(configuration), "metadata"), "asset_index"
    )
    return {
        "id": pinned["id"],
        "sha1": pinned["sha1"],
        "size": pinned["size"],
        "totalSize": pinned["total_size"],
        "url": pinned["url"],
    }


def expected_client_download_contract(configuration: Configuration) -> dict[str, object]:
    pinned = require_object(
        require_object(runtime_spec(configuration), "metadata"), "client"
    )
    return {
        "sha1": pinned["sha1"],
        "size": pinned["size"],
        "url": pinned["url"],
    }


@contextmanager
def controlled_termination_signals() -> Iterator[None]:
    """Turns terminal signals into exceptions so owned children are cleaned up."""

    def handle_signal(signum: int, _frame: object) -> None:
        raise BaselineError(
            f"Controller received {signal.Signals(signum).name}; stopping owned processes"
        )

    previous_handlers: dict[signal.Signals, object] = {}
    try:
        for termination_signal in CONTROLLER_TERMINATION_SIGNALS:
            previous_handlers[termination_signal] = signal.signal(
                termination_signal, handle_signal
            )
        yield
    finally:
        for termination_signal, previous_handler in reversed(
            tuple(previous_handlers.items())
        ):
            signal.signal(termination_signal, previous_handler)


@contextmanager
def blocked_termination_signals() -> Iterator[None]:
    """Defers terminal signals across child assignment and mandatory cleanup."""

    previous_mask = signal.pthread_sigmask(
        signal.SIG_BLOCK, CONTROLLER_TERMINATION_SIGNALS
    )
    try:
        yield
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)


def reset_owned_child_signal_state() -> None:
    for termination_signal in CONTROLLER_TERMINATION_SIGNALS:
        signal.signal(termination_signal, signal.SIG_DFL)
    signal.pthread_sigmask(signal.SIG_UNBLOCK, CONTROLLER_TERMINATION_SIGNALS)


def verify_official_vanilla_metadata(
    configuration: Configuration, root: Path
) -> dict[str, object]:
    pinned = require_object(runtime_spec(configuration), "metadata")
    source_path = pinned_vanilla_metadata_path(configuration, root)
    verify_exact_file(
        source_path,
        str(pinned["sha256"]),
        int(pinned["size"]),
        "Pinned official Minecraft metadata",
    )
    if sha1_file(source_path) != pinned["sha1"]:
        raise BaselineError("Pinned official Minecraft metadata failed SHA-1 validation")
    metadata = load_json_object(source_path, "Pinned official Minecraft metadata")
    minecraft_version = str(runtime_spec(configuration)["minecraft_version"])
    downloads = metadata.get("downloads")
    if (
        metadata.get("id") != minecraft_version
        or metadata.get("type") != "release"
        or metadata.get("assetIndex") != expected_asset_index_contract(configuration)
        or not isinstance(downloads, dict)
        or downloads.get("client")
        != expected_client_download_contract(configuration)
        or metadata.get("javaVersion")
        != {"component": "java-runtime-delta", "majorVersion": 21}
        or not isinstance(metadata.get("libraries"), list)
    ):
        raise BaselineError(
            "Pinned official Minecraft metadata has an unexpected runtime contract"
        )
    return metadata


def write_pinned_launcher_metadata(
    configuration: Configuration, root: Path
) -> None:
    metadata = verify_official_vanilla_metadata(configuration, root)
    normalized = dict(metadata)
    normalized.pop("javaVersion")
    destination = vanilla_metadata_path(configuration, root)
    destination.parent.mkdir(mode=0o700, parents=True)
    write_json_atomic(destination, normalized)


def verify_pinned_launcher_metadata(
    configuration: Configuration, root: Path
) -> None:
    expected = verify_official_vanilla_metadata(configuration, root)
    expected.pop("javaVersion")
    actual = load_json_object(
        vanilla_metadata_path(configuration, root), "Minecraft version metadata"
    )
    if actual != expected:
        raise BaselineError(
            "Installed Minecraft metadata is not the pinned no-bundled-runtime projection"
        )
    runtime_root = launcher_directory(configuration, root) / "runtime"
    if runtime_root.exists() or runtime_root.is_symlink():
        raise BaselineError(
            "The unused Mojang Java runtime must not be installed in this profile"
        )


def validate_fabric_profile_contract(
    configuration: Configuration, profile: dict[str, object]
) -> None:
    pinned = require_object(runtime_spec(configuration), "fabric_profile")
    minecraft_version = str(runtime_spec(configuration)["minecraft_version"])
    raw_libraries = profile.get("libraries")
    expected_coordinates = [
        value["coordinate"] for value in require_list(pinned, "libraries")
    ]
    if (
        profile.get("id") != version_id(configuration)
        or profile.get("inheritsFrom") != minecraft_version
        or profile.get("mainClass")
        != "net.fabricmc.loader.impl.launch.knot.KnotClient"
        or profile.get("type") != "release"
        or not isinstance(raw_libraries, list)
        or not all(isinstance(value, dict) for value in raw_libraries)
        or [value.get("name") for value in raw_libraries] != expected_coordinates
    ):
        raise BaselineError("Pinned Fabric loader profile has an unexpected contract")


def verify_tracked_fabric_profile_snapshot(configuration: Configuration) -> bytes:
    pinned = require_object(runtime_spec(configuration), "fabric_profile")
    snapshot = require_object(pinned, "snapshot")
    source_path = configuration.fabric_profile_snapshot_path
    ensure_no_symlink_components(
        configuration.repository_root,
        source_path,
        "Tracked Fabric loader profile snapshot",
    )
    snapshot_content = read_exact_file_no_follow(
        source_path,
        str(snapshot["sha256"]),
        int(snapshot["size"]),
        "Tracked Fabric loader profile snapshot",
    )
    if not snapshot_content.endswith(b"\n") or snapshot_content.endswith(b"\n\n"):
        raise BaselineError(
            "Tracked Fabric loader profile snapshot must end in exactly one newline"
        )
    response_content = snapshot_content[:-1]
    if len(response_content) != int(pinned["size"]):
        raise BaselineError(
            "Tracked Fabric loader profile response has an unexpected byte length"
        )
    if hashlib.sha1(response_content).hexdigest() != pinned["sha1"]:
        raise BaselineError(
            "Tracked Fabric loader profile response failed SHA-1 validation"
        )
    if hashlib.sha256(response_content).hexdigest() != pinned["sha256"]:
        raise BaselineError(
            "Tracked Fabric loader profile response failed SHA-256 validation"
        )
    try:
        raw_profile = json.loads(response_content.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise BaselineError(
            f"Tracked Fabric loader profile snapshot is invalid JSON: {exception}"
        ) from exception
    if not isinstance(raw_profile, dict):
        raise BaselineError(
            "Tracked Fabric loader profile snapshot must contain a JSON object"
        )
    validate_fabric_profile_contract(configuration, raw_profile)
    return response_content


def verify_official_fabric_profile(
    configuration: Configuration, root: Path
) -> dict[str, object]:
    pinned = require_object(runtime_spec(configuration), "fabric_profile")
    source_path = pinned_fabric_profile_path(configuration, root)
    verify_exact_file(
        source_path,
        str(pinned["sha256"]),
        int(pinned["size"]),
        "Pinned official Fabric loader profile",
    )
    if sha1_file(source_path) != pinned["sha1"]:
        raise BaselineError("Pinned Fabric loader profile failed SHA-1 validation")
    profile = load_json_object(source_path, "Pinned official Fabric loader profile")
    validate_fabric_profile_contract(configuration, profile)
    return profile


def write_pinned_fabric_metadata(
    configuration: Configuration, root: Path
) -> None:
    profile = verify_official_fabric_profile(configuration, root)
    normalized = dict(profile)
    normalized["jar"] = str(runtime_spec(configuration)["minecraft_version"])
    destination = fabric_metadata_path(configuration, root)
    destination.parent.mkdir(mode=0o700, parents=True)
    write_json_atomic(destination, normalized)


def verify_pinned_fabric_metadata(
    configuration: Configuration, root: Path
) -> None:
    expected = verify_official_fabric_profile(configuration, root)
    expected["jar"] = str(runtime_spec(configuration)["minecraft_version"])
    actual = load_json_object(
        fabric_metadata_path(configuration, root), "Fabric version metadata"
    )
    if actual != expected:
        raise BaselineError(
            "Installed Fabric metadata is not the exact pinned loader profile projection"
        )


def install_pinned_fabric_libraries(
    configuration: Configuration, root: Path
) -> None:
    launcher_root = launcher_directory(configuration, root)
    cache_root = pinned_fabric_library_cache_root(configuration)
    ensure_no_symlink_components(
        configuration.manifest_path.parent,
        cache_root,
        "Pinned Fabric library cache",
    )
    fabric_profile = require_object(runtime_spec(configuration), "fabric_profile")
    for raw_library in require_list(fabric_profile, "libraries"):
        if not isinstance(raw_library, dict):
            raise BaselineError("Pinned Fabric library is invalid")
        relative_path = PurePosixPath(str(raw_library["path"]))
        destination = launcher_root / "libraries" / Path(*relative_path.parts)
        ensure_no_symlink_components(
            launcher_root, destination, "Pinned Fabric library destination"
        )
        destination.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        cache_source = cache_root / Path(*relative_path.parts)
        copied_from_cache = copy_pinned_cached_file(
            cache_root,
            cache_source,
            destination,
            str(raw_library["sha256"]),
            str(raw_library["sha1"]),
            int(raw_library["size"]),
            f"Fabric library {raw_library['coordinate']}",
        )
        if not copied_from_cache:
            download_pinned_file(
                str(raw_library["url"]),
                destination,
                str(raw_library["sha256"]),
                f"Fabric library {raw_library['coordinate']}",
                int(raw_library["size"]),
            )
        if sha1_file(destination) != raw_library["sha1"]:
            raise BaselineError(
                f"Fabric library failed SHA-1 validation: {raw_library['coordinate']}"
            )


def fabric_library_inventory(
    configuration: Configuration, root: Path
) -> list[dict[str, object]]:
    launcher_root = launcher_directory(configuration, root)
    pinned = require_object(runtime_spec(configuration), "fabric_profile")
    inventory: list[dict[str, object]] = []
    for raw_library in require_list(pinned, "libraries"):
        if not isinstance(raw_library, dict):
            raise BaselineError("Pinned Fabric library is invalid")
        path = launcher_root / "libraries" / Path(
            *PurePosixPath(str(raw_library["path"])).parts
        )
        verify_exact_file(
            path,
            str(raw_library["sha256"]),
            int(raw_library["size"]),
            f"Pinned Fabric library {raw_library['coordinate']}",
        )
        if sha1_file(path) != raw_library["sha1"]:
            raise BaselineError(
                f"Pinned Fabric library failed SHA-1 validation: {raw_library['coordinate']}"
            )
        inventory.append(
            {
                "coordinate": raw_library["coordinate"],
                "path": path.relative_to(launcher_root).as_posix(),
                "size": raw_library["size"],
                "sha1": raw_library["sha1"],
                "sha256": raw_library["sha256"],
            }
        )
    return inventory


def version_metadata_inventory(
    configuration: Configuration, root: Path
) -> list[dict[str, object]]:
    launcher_root = launcher_directory(configuration, root)
    minecraft_version = str(runtime_spec(configuration)["minecraft_version"])
    child_path = fabric_metadata_path(configuration, root)
    parent_path = vanilla_metadata_path(configuration, root)
    child = load_json_object(child_path, "Fabric child version metadata")
    parent = load_json_object(parent_path, "Minecraft parent version metadata")
    if (
        child.get("id") != version_id(configuration)
        or child.get("inheritsFrom") != minecraft_version
        or child.get("jar") != minecraft_version
    ):
        raise BaselineError("Fabric child metadata has an unexpected inheritance contract")
    if parent.get("id") != minecraft_version or "inheritsFrom" in parent:
        raise BaselineError("Minecraft parent metadata is not the terminal inheritance node")
    result: list[dict[str, object]] = []
    for order, (path, metadata) in enumerate(
        ((child_path, child), (parent_path, parent))
    ):
        result.append(
            {
                "order": order,
                "id": metadata["id"],
                "inherits_from": metadata.get("inheritsFrom"),
                "jar": metadata.get("jar"),
                "path": path.relative_to(launcher_root).as_posix(),
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
                "canonical_json_sha256": canonical_json_sha256(metadata),
            }
        )
    return result


def minecraft_install_worker(
    configuration: Configuration, root: Path, result_connection: Connection
) -> None:
    reset_owned_child_signal_state()
    result: dict[str, object]
    try:
        launcher_install = load_verified_launcher_module(
            "minecraft_launcher_lib.install",
            temporary_directory(configuration, root) / "python-bytecode-cache",
        )
        launcher_root = launcher_directory(configuration, root)
        minecraft_version = str(runtime_spec(configuration)["minecraft_version"])
        install_function = getattr(launcher_install, "install_minecraft_version", None)
        if not callable(install_function):
            raise BaselineError(
                "Verified launcher install module has no install function"
            )
        install_function(minecraft_version, str(launcher_root))
        result = {"status": "passed"}
    except BaseException as exception:
        result = {
            "status": "failed",
            "error_type": type(exception).__name__,
            "error": str(exception)[:4096],
        }
    try:
        result_connection.send(result)
    finally:
        result_connection.close()


def stop_minecraft_install_worker(process: multiprocessing.Process) -> None:
    if not process.is_alive():
        return
    process.terminate()
    process.join(PROCESS_STOP_TIMEOUT_SECONDS)
    if process.is_alive():
        process.kill()
        process.join(PROCESS_STOP_TIMEOUT_SECONDS)
    if process.is_alive():
        raise BaselineError("Pinned Minecraft installation worker survived TERM and KILL")


def install_minecraft_in_owned_worker(
    configuration: Configuration, root: Path
) -> None:
    try:
        process_context = multiprocessing.get_context("spawn")
    except ValueError as exception:
        raise BaselineError(
            "This macOS baseline requires a killable spawned installation worker"
        ) from exception
    result_reader, result_writer = process_context.Pipe(duplex=False)
    process = process_context.Process(
        name="etherology-original-minecraft-installer",
        target=minecraft_install_worker,
        args=(configuration, root, result_writer),
        daemon=True,
    )
    started = False
    with controlled_termination_signals():
        try:
            with blocked_termination_signals():
                process.start()
                started = True
                result_writer.close()
            process.join(PROVISION_INSTALL_TIMEOUT_SECONDS)
            if process.is_alive():
                raise BaselineError(
                    "Pinned Minecraft installation exceeded the one-hour worker timeout"
                )
            if process.exitcode != 0:
                raise BaselineError(
                    "Pinned Minecraft installation worker exited without a valid result: "
                    f"{process.exitcode}"
                )
            if not result_reader.poll(1):
                raise BaselineError("Pinned Minecraft installation worker returned no result")
            try:
                result = result_reader.recv()
            except EOFError as exception:
                raise BaselineError(
                    "Pinned Minecraft installation worker result was truncated"
                ) from exception
            if result == {"status": "passed"}:
                return
            if (
                not isinstance(result, dict)
                or set(result) != {"status", "error_type", "error"}
                or result.get("status") != "failed"
                or not isinstance(result.get("error_type"), str)
                or not isinstance(result.get("error"), str)
            ):
                raise BaselineError("Pinned Minecraft installation worker result is invalid")
            raise BaselineError(
                "Pinned Minecraft installation worker failed: "
                f"{result['error_type']}: {result['error']}"
            )
        finally:
            with blocked_termination_signals():
                if started and process.is_alive():
                    stop_minecraft_install_worker(process)
                result_reader.close()
                result_writer.close()
                if started and not process.is_alive():
                    process.close()


def install_isolated_game(configuration: Configuration, root: Path) -> None:
    minecraft_version = str(runtime_spec(configuration)["minecraft_version"])
    try:
        install_minecraft_in_owned_worker(configuration, root)
    except Exception as exception:
        raise BaselineError(
            f"Cannot install Minecraft {minecraft_version} in the owned launcher root: "
            f"{exception}"
        ) from exception
    install_pinned_fabric_libraries(configuration, root)
    write_pinned_fabric_metadata(configuration, root)


def verify_installed_game(configuration: Configuration, root: Path) -> None:
    launcher_root = launcher_directory(configuration, root)
    if launcher_root.is_symlink() or not launcher_root.is_dir():
        raise BaselineError(f"Owned launcher root is missing or linked: {launcher_root}")
    runtime = runtime_spec(configuration)
    minecraft_version = str(runtime["minecraft_version"])
    verify_pinned_launcher_metadata(configuration, root)
    minecraft_root = launcher_root / "versions" / minecraft_version
    minecraft_json = minecraft_root / f"{minecraft_version}.json"
    minecraft_jar = minecraft_root / f"{minecraft_version}.jar"
    metadata = load_json_object(minecraft_json, "Minecraft version metadata")
    if metadata.get("id") != minecraft_version:
        raise BaselineError("Minecraft version metadata has the wrong id")
    if minecraft_jar.is_symlink() or not minecraft_jar.is_file():
        raise BaselineError(f"Minecraft client JAR is missing or linked: {minecraft_jar}")
    pinned_client = require_object(
        require_object(runtime_spec(configuration), "metadata"), "client"
    )
    if (
        minecraft_jar.stat().st_size != pinned_client["size"]
        or sha1_file(minecraft_jar) != pinned_client["sha1"]
        or sha256_file(minecraft_jar) != pinned_client["sha256"]
    ):
        raise BaselineError("Minecraft client JAR differs from its pinned metadata")
    fabric_metadata = load_json_object(
        fabric_metadata_path(configuration, root), "Fabric version metadata"
    )
    if (
        fabric_metadata.get("id") != version_id(configuration)
        or fabric_metadata.get("inheritsFrom") != minecraft_version
        or fabric_metadata.get("jar") != minecraft_version
    ):
        raise BaselineError("Installed Fabric metadata does not match the pinned runtime")
    verify_pinned_fabric_metadata(configuration, root)
    fabric_library_inventory(configuration, root)
    version_metadata_inventory(configuration, root)


def remove_owned_staging_directory(
    path: Path, runtimes_root: Path, profile_id: str
) -> None:
    if (
        path.parent.absolute() != runtimes_root.absolute()
        or not path.name.startswith(f".{profile_id}.")
        or path.is_symlink()
        or not path.is_dir()
    ):
        raise BaselineError(f"Refusing to remove an unowned staging directory: {path}")
    shutil.rmtree(path)


def provision_profile(
    configuration: Configuration, runtimes_root: Path = RUNTIMES_ROOT
) -> bool:
    fabric_profile_content = verify_tracked_fabric_profile_snapshot(configuration)
    verify_reference_bundle(configuration)
    verify_harness_artifact(configuration)
    preflight_launcher_import_resolution()
    target = runtime_root(configuration, runtimes_root)
    if target.exists() or target.is_symlink():
        ensure_runtimes_root_for_write(runtimes_root)
        verify_owned_runtime(configuration, target, runtimes_root)
        with acquire_runtime_lifecycle_lock(configuration, target):
            verify_installed_game(configuration, target)
            verify_runtime_lock(configuration, target)
            verify_capture_layout(configuration, target, require_fresh=False)
        return False
    resolve_java_21()
    ensure_runtimes_root_for_write(runtimes_root)
    profile_id = str(profile_spec(configuration)["id"])
    staging = Path(tempfile.mkdtemp(prefix=f".{profile_id}.", dir=runtimes_root))
    try:
        for directory in (
            game_directory(configuration, staging),
            launcher_directory(configuration, staging),
            evidence_directory(configuration, staging),
            logs_directory(configuration, staging),
            installer_directory(configuration, staging),
            home_directory(configuration, staging),
            temporary_directory(configuration, staging),
        ):
            directory.mkdir(mode=0o700)
        write_text_exclusive(lifecycle_lock_path(configuration, staging), "")
        game_root = game_directory(configuration, staging)
        for raw_directory in require_list(configuration.manifest, "profile_directories"):
            (game_root / str(raw_directory)).mkdir(mode=0o700, exist_ok=True)
        scenario_root(configuration, staging).mkdir(mode=0o700)
        reports_directory(configuration, staging).mkdir(mode=0o700)
        screenshots_directory(configuration, staging).mkdir(mode=0o700)
        write_json_atomic(
            evidence_marker_path(configuration, staging),
            evidence_descriptor(configuration),
        )
        options = require_list(configuration.manifest, "options")
        write_text_exclusive(
            game_root / "options.txt",
            "\n".join(str(value) for value in options) + "\n",
        )
        pinned_metadata = require_object(runtime_spec(configuration), "metadata")
        download_pinned_file(
            str(pinned_metadata["url"]),
            pinned_vanilla_metadata_path(configuration, staging),
            str(pinned_metadata["sha256"]),
            "Official Minecraft metadata",
            int(pinned_metadata["size"]),
        )
        write_bytes_exclusive(
            pinned_fabric_profile_path(configuration, staging),
            fabric_profile_content,
        )
        write_pinned_launcher_metadata(configuration, staging)
        install_isolated_game(configuration, staging)
        write_json_atomic(
            runtime_lock_path(configuration, staging),
            runtime_lock_descriptor(configuration, staging),
        )
        write_json_atomic(marker_path(configuration, staging), profile_descriptor(configuration))
        os.replace(staging, target)
    except Exception:
        if staging.exists() and not staging.is_symlink():
            remove_owned_staging_directory(staging, runtimes_root, profile_id)
        raise
    verify_owned_runtime(configuration, target, runtimes_root)
    with acquire_runtime_lifecycle_lock(configuration, target):
        verify_installed_game(configuration, target)
        verify_runtime_lock(configuration, target)
        verify_capture_layout(configuration, target, require_fresh=True)
    return True


def expected_options_content(configuration: Configuration) -> bytes:
    options = require_list(configuration.manifest, "options")
    return ("\n".join(str(value) for value in options) + "\n").encode("utf-8")


def require_empty_owned_directory(parent: Path, path: Path, description: str) -> None:
    ensure_no_symlink_components(parent, path, description)
    if path.is_symlink() or not path.is_dir():
        raise BaselineError(f"{description} is missing or linked: {path}")
    entries = list(path.iterdir())
    if entries:
        raise BaselineError(f"{description} is contaminated: {entries}")


def prelaunch_profile_descriptor(
    configuration: Configuration,
    root: Path,
    *,
    require_exact_mods: bool = True,
) -> dict[str, object]:
    game_root = game_directory(configuration, root)
    ensure_no_symlink_components(root, game_root, "Prelaunch game directory")
    if game_root.is_symlink() or not game_root.is_dir():
        raise BaselineError(f"Prelaunch game directory is missing or linked: {game_root}")
    profile_directories = [
        str(value) for value in require_list(configuration.manifest, "profile_directories")
    ]
    expected_game_entries = set(profile_directories) | {"options.txt"}
    actual_game_entries = {path.name for path in game_root.iterdir()}
    if actual_game_entries != expected_game_entries:
        raise BaselineError(
            "Prelaunch game inventory is not exact: "
            f"missing={sorted(expected_game_entries - actual_game_entries)}, "
            f"extra={sorted(actual_game_entries - expected_game_entries)}"
        )
    options_path = game_root / "options.txt"
    ensure_no_symlink_components(game_root, options_path, "Pinned options file")
    expected_options = expected_options_content(configuration)
    if options_path.is_symlink() or not options_path.is_file():
        raise BaselineError(f"Pinned options file is missing or linked: {options_path}")
    try:
        actual_options = options_path.read_bytes()
    except OSError as exception:
        raise BaselineError(f"Cannot read pinned options file: {options_path}") from exception
    if actual_options != expected_options:
        raise BaselineError("Prelaunch options.txt differs from the manifest")
    parse_options(actual_options.decode("utf-8"))

    mods_root = game_root / "mods"
    if require_exact_mods:
        verify_mod_inventory(configuration, mods_root)
    else:
        ensure_no_symlink_components(game_root, mods_root, "Prelaunch mods directory")
        if mods_root.is_symlink() or not mods_root.is_dir():
            raise BaselineError(
                f"Prelaunch mods directory is missing or linked: {mods_root}"
            )
        expected_mod_names = {
            str(member["file_name"]) for member in member_specs(configuration)
        } | {str(harness_spec(configuration)["file_name"])}
        for path in mods_root.iterdir():
            if (
                path.name not in expected_mod_names
                or path.is_symlink()
                or not path.is_file()
            ):
                raise BaselineError(
                    "Prelaunch mods directory contains an unexpected, linked, or "
                    f"non-file entry: {path}"
                )
    mod_files = [
        {
            "file_name": path.name,
            "size": path.stat().st_size,
            "sha256": sha256_file(path),
        }
        for path in sorted(mods_root.iterdir(), key=lambda value: value.name)
    ]
    empty_game_directories = [
        directory for directory in profile_directories if directory != "mods"
    ]
    for directory in empty_game_directories:
        require_empty_owned_directory(
            game_root, game_root / directory, f"Prelaunch game/{directory} directory"
        )
    for directory, description in (
        (home_directory(configuration, root), "Prelaunch isolated HOME"),
        (temporary_directory(configuration, root), "Prelaunch isolated TMP"),
        (logs_directory(configuration, root), "Prelaunch controller logs"),
    ):
        require_empty_owned_directory(root, directory, description)
    verify_fresh_skin_cache(configuration, root)

    installer_root = installer_directory(configuration, root)
    ensure_no_symlink_components(root, installer_root, "Provisioning metadata directory")
    if installer_root.is_symlink() or not installer_root.is_dir():
        raise BaselineError(
            f"Provisioning metadata directory is missing or linked: {installer_root}"
        )
    expected_installer_names = {
        pinned_vanilla_metadata_path(configuration, root).name,
        pinned_fabric_profile_path(configuration, root).name,
    }
    actual_installer_names = {path.name for path in installer_root.iterdir()}
    if actual_installer_names != expected_installer_names:
        raise BaselineError(
            "Provisioning metadata directory has an unexpected inventory"
        )
    provisioning_metadata = provisioning_metadata_descriptor(configuration, root)

    required_root_names = {
        str(profile_spec(configuration)[field_name])
        for field_name in (
            "game_directory",
            "launcher_directory",
            "evidence_directory",
            "logs_directory",
            "installer_directory",
            "home_directory",
            "temporary_directory",
            "marker_file",
            "runtime_lock_file",
            "lifecycle_lock_file",
        )
    }
    optional_root_names = {str(profile_spec(configuration)["artifact_lock_file"])}
    actual_root_names = {path.name for path in root.iterdir()}
    if not required_root_names.issubset(actual_root_names) or not actual_root_names.issubset(
        required_root_names | optional_root_names
    ):
        raise BaselineError("Prelaunch profile root has an unexpected inventory")
    return {
        "schema": 1,
        "options": {
            "path": options_path.relative_to(root).as_posix(),
            "size": len(actual_options),
            "sha256": hashlib.sha256(actual_options).hexdigest(),
        },
        "game_directories": profile_directories,
        "empty_game_directories": empty_game_directories,
        "mods": mod_files,
        "empty_profile_directories": [
            str(path.relative_to(root))
            for path in (
                home_directory(configuration, root),
                temporary_directory(configuration, root),
                logs_directory(configuration, root),
            )
        ],
        "mutable_launcher_outputs": {
            "skin_cache": {
                "path": skin_cache_directory(configuration, root)
                .relative_to(launcher_directory(configuration, root))
                .as_posix(),
                "prelaunch_state": "absent",
                "maximum_file_count": MAXIMUM_SKIN_CACHE_FILE_COUNT,
                "maximum_total_size": MAXIMUM_SKIN_CACHE_SIZE,
            }
        },
        "provisioning_metadata": provisioning_metadata,
    }


def artifact_lock_static_descriptor(
    configuration: Configuration,
) -> dict[str, object]:
    bundle = bundle_spec(configuration)
    harness = harness_spec(configuration)
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "manifest_sha256": sha256_file(configuration.manifest_path),
        "bundle_sha256": bundle["sha256"],
        "harness": {
            "file_name": harness["file_name"],
            "mod_id": harness["mod_id"],
            "version": harness["version"],
            "size": harness["size"],
            "sha256": harness["sha256"],
        },
        "members": [
            {
                "file_name": member["file_name"],
                "mod_id": member["mod_id"],
                "size": member["size"],
                "sha256": member["sha256"],
            }
            for member in member_specs(configuration)
        ],
    }


def artifact_lock_descriptor(
    configuration: Configuration, root: Path
) -> dict[str, object]:
    descriptor = artifact_lock_static_descriptor(configuration)
    descriptor["prelaunch_profile"] = prelaunch_profile_descriptor(
        configuration, root
    )
    return descriptor


def verify_mod_inventory(configuration: Configuration, mods_directory: Path) -> None:
    if mods_directory.is_symlink() or not mods_directory.is_dir():
        raise BaselineError(f"Owned mods directory is missing or linked: {mods_directory}")
    harness = harness_spec(configuration)
    expected_names = {
        str(member["file_name"]) for member in member_specs(configuration)
    } | {str(harness["file_name"])}
    actual_names: set[str] = set()
    for path in mods_directory.iterdir():
        if path.is_symlink() or not path.is_file():
            raise BaselineError(f"Owned mods inventory contains a linked/non-file entry: {path}")
        actual_names.add(path.name)
    if actual_names != expected_names:
        raise BaselineError(
            "Owned mods inventory differs from the exact published allowlist: "
            f"missing={sorted(expected_names - actual_names)}, "
            f"extra={sorted(actual_names - expected_names)}"
        )
    for member in member_specs(configuration):
        verify_exact_file(
            mods_directory / str(member["file_name"]),
            str(member["sha256"]),
            int(member["size"]),
            "Staged published member",
        )
    verify_exact_file(
        mods_directory / str(harness["file_name"]),
        str(harness["sha256"]),
        int(harness["size"]),
        "Staged original-baseline harness",
    )


def artifact_lock_matches(
    configuration: Configuration, root: Path, *, require_prelaunch: bool
) -> dict[str, object]:
    lock = load_json_object(
        artifact_lock_path(configuration, root), "Published artifact lock"
    )
    raw_prelaunch = lock.get("prelaunch_profile")
    without_prelaunch = dict(lock)
    without_prelaunch.pop("prelaunch_profile", None)
    if (
        not isinstance(raw_prelaunch, dict)
        or without_prelaunch != artifact_lock_static_descriptor(configuration)
    ):
        raise BaselineError("Published artifact lock does not match this exact manifest")
    if require_prelaunch and raw_prelaunch != prelaunch_profile_descriptor(
        configuration, root
    ):
        raise BaselineError("Published artifact lock prelaunch profile changed")
    return lock


def stage_reference_members(configuration: Configuration) -> bool:
    root = verify_owned_runtime(configuration)
    with acquire_runtime_lifecycle_lock(configuration, root):
        return _stage_reference_members_locked(configuration)


def _stage_reference_members_locked(configuration: Configuration) -> bool:
    verify_reference_bundle(configuration)
    verify_harness_artifact(configuration)
    root = verify_owned_runtime(configuration)
    verify_installed_game(configuration, root)
    verify_runtime_lock(configuration, root)
    verify_capture_layout(configuration, root, require_fresh=True)
    assert_runtime_not_running(configuration, root)
    prelaunch_profile_descriptor(
        configuration, root, require_exact_mods=False
    )
    mods_root = game_directory(configuration, root) / "mods"
    if mods_root.is_symlink() or not mods_root.is_dir():
        raise BaselineError(f"Owned mods directory is missing or linked: {mods_root}")
    harness = harness_spec(configuration)
    expected_names = {
        str(member["file_name"]) for member in member_specs(configuration)
    } | {str(harness["file_name"])}
    unexpected = sorted(
        path.name for path in mods_root.iterdir() if path.name not in expected_names
    )
    if unexpected:
        raise BaselineError(
            f"Refusing to stage over unexpected owned mods inventory: {unexpected}"
        )

    # Resolve every target and source before the first write. A bad later target
    # must never leave an earlier JAR replaced.
    targets: list[tuple[Path, bytes, int, str, str]] = []
    with zipfile.ZipFile(configuration.bundle_path) as archive:
        for member in member_specs(configuration):
            target = mods_root / str(member["file_name"])
            if target.is_symlink():
                raise BaselineError(f"Refusing to replace linked staged JAR: {target}")
            if target.exists() and not target.is_file():
                raise BaselineError(f"Refusing to replace non-file staged JAR: {target}")
            content = archive.read(str(member["archive_path"]))
            if (
                len(content) != int(member["size"])
                or hashlib.sha256(content).hexdigest() != member["sha256"]
            ):
                raise BaselineError(
                    f"Published source bytes changed during staging: {member['file_name']}"
                )
            targets.append(
                (
                    target,
                    content,
                    int(member["size"]),
                    str(member["sha256"]),
                    "Staged member temporary file",
                )
            )

    harness_target = mods_root / str(harness["file_name"])
    if harness_target.is_symlink():
        raise BaselineError(
            f"Refusing to replace linked staged harness: {harness_target}"
        )
    if harness_target.exists() and not harness_target.is_file():
        raise BaselineError(
            f"Refusing to replace non-file staged harness: {harness_target}"
        )
    harness_content = configuration.harness_path.read_bytes()
    if (
        len(harness_content) != int(harness["size"])
        or hashlib.sha256(harness_content).hexdigest() != harness["sha256"]
    ):
        raise BaselineError("Harness bytes changed during staging preflight")
    targets.append(
        (
            harness_target,
            harness_content,
            int(harness["size"]),
            str(harness["sha256"]),
            "Staged harness temporary file",
        )
    )

    lock_path = artifact_lock_path(configuration, root)
    if lock_path.is_symlink() or (lock_path.exists() and not lock_path.is_file()):
        raise BaselineError(f"Refusing to replace an unsafe artifact lock: {lock_path}")
    current_lock = None
    if lock_path.is_file():
        current_lock = load_json_object(lock_path, "Published artifact lock")

    changed = False
    for target, content, size, digest, description in targets:
        if (
            target.is_file()
            and target.stat().st_size == size
            and sha256_file(target) == digest
        ):
            continue
        temporary = mods_root / f".{target.name}.{uuid.uuid4().hex}.tmp"
        descriptor = os.open(
            temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600
        )
        try:
            with os.fdopen(descriptor, "wb") as destination:
                destination.write(content)
            verify_exact_file(
                temporary,
                digest,
                size,
                description,
            )
            os.replace(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)
        changed = True

    verify_mod_inventory(configuration, mods_root)
    expected_lock = artifact_lock_descriptor(configuration, root)
    if current_lock != expected_lock:
        write_json_atomic(lock_path, expected_lock)
        changed = True
    return changed


def verify_staged_reference(
    configuration: Configuration, root: Path, *, require_prelaunch: bool = True
) -> None:
    artifact_lock_matches(
        configuration, root, require_prelaunch=require_prelaunch
    )
    verify_mod_inventory(configuration, game_directory(configuration, root) / "mods")


def read_process_state(
    configuration: Configuration, root: Path
) -> dict[str, object] | None:
    path = process_state_path(configuration, root)
    if not path.exists() and not path.is_symlink():
        return None
    state = load_json_object(path, "Original-baseline process state")
    raw_log = state.get("log")
    raw_game_directory = state.get("game_directory")
    if (
        set(state)
        != {
            "schema",
            "profile_id",
            "pid",
            "process_group_id",
            "version_id",
            "game_directory",
            "scenario",
            "log",
            "launch_attempt_sha256",
        }
        or state.get("schema") != 2
        or state.get("profile_id") != profile_spec(configuration)["id"]
        or type(state.get("pid")) is not int
        or int(state["pid"]) <= 0
        or state.get("process_group_id") != state.get("pid")
        or state.get("version_id") != version_id(configuration)
        or raw_game_directory != str(game_directory(configuration, root))
        or state.get("scenario") not in scenario_ids(configuration)
        or not isinstance(raw_log, str)
        or not isinstance(state.get("launch_attempt_sha256"), str)
    ):
        raise BaselineError("Process state does not describe this exact owned runtime")
    validate_sha256(state["launch_attempt_sha256"], "process launch attempt")
    log_path = Path(str(raw_log))
    if (
        log_path.parent != logs_directory(configuration, root)
        or re.fullmatch(r"original-client-[0-9]{8}T[0-9]{6}Z\.log", log_path.name)
        is None
        or log_path.is_symlink()
    ):
        raise BaselineError("Process state points outside the owned controller log directory")
    return state


def assert_runtime_not_running(configuration: Configuration, root: Path) -> None:
    state = read_process_state(configuration, root)
    if state is None:
        return
    raise BaselineError(
        "Owned original-baseline process state already exists; this controller never "
        "adopts, signals, or clears a process from another invocation"
    )


def offline_uuid(username: str) -> str:
    digest = hashlib.md5(f"OfflinePlayer:{username}".encode("utf-8")).digest()
    return str(uuid.UUID(bytes=digest, version=3))


def resolve_offline_launcher_placeholders(command: list[str]) -> list[str]:
    """Resolves the two modern auth fields omitted by launcher-lib 8.0."""

    replacements = {
        "${clientid}": OFFLINE_CLIENT_ID,
        "${auth_xuid}": OFFLINE_XUID,
    }
    for placeholder in replacements:
        if command.count(placeholder) != 1:
            raise BaselineError(
                f"Generated command does not contain exactly one {placeholder} argument"
            )
    return [replacements.get(argument, argument) for argument in command]


def generate_launch_command(
    configuration: Configuration,
    java_path: Path,
    root: Path,
    scenario_id: str | None = None,
) -> list[str]:
    launcher_command = load_verified_launcher_module(
        "minecraft_launcher_lib.command",
        temporary_directory(configuration, root) / "python-bytecode-cache"
    )
    launch = require_object(configuration.manifest, "launch")
    resolution = require_object(launch, "resolution")
    username = str(launch["offline_username"])
    jvm_arguments = [
        f"-Xmx{launch['maximum_memory_mb']}M",
        f"-Duser.home={home_directory(configuration, root)}",
        f"-Djava.io.tmpdir={temporary_directory(configuration, root)}",
    ]
    if scenario_id is not None:
        jvm_arguments.append(f"-D{SCENARIO_PROPERTY_NAME}={scenario_id}")
    options = {
        "username": username,
        "uuid": offline_uuid(username),
        "token": OFFLINE_ACCESS_TOKEN,
        "executablePath": str(java_path),
        "defaultExecutablePath": str(java_path),
        "gameDirectory": str(game_directory(configuration, root)),
        "nativesDirectory": str(native_extraction_directory(configuration, root)),
        "customResolution": True,
        "resolutionWidth": str(resolution["width"]),
        "resolutionHeight": str(resolution["height"]),
        "jvmArguments": jvm_arguments,
    }
    try:
        command_function = getattr(launcher_command, "get_minecraft_command", None)
        if not callable(command_function):
            raise BaselineError(
                "Verified launcher command module has no command function"
            )
        command = command_function(
            version_id(configuration),
            str(launcher_directory(configuration, root)),
            options,
        )
        if not isinstance(command, list) or not all(
            isinstance(argument, str) for argument in command
        ):
            raise BaselineError("Verified launcher returned an invalid command")
        return resolve_offline_launcher_placeholders(command)
    except Exception as exception:
        raise BaselineError(
            f"Cannot generate the owned Fabric launch command: {exception}"
        ) from exception


def verify_launch_command(
    configuration: Configuration,
    command: list[str],
    java_path: Path,
    root: Path,
    scenario_id: str | None = None,
) -> None:
    if not command or Path(command[0]).resolve(strict=False) != java_path.resolve(strict=False):
        raise BaselineError("Generated command does not use the selected Java executable")
    if command.count("net.fabricmc.loader.impl.launch.knot.KnotClient") != 1:
        raise BaselineError("Generated command does not launch Fabric KnotClient")
    scenario_arguments = [
        value for value in command if value.startswith(f"-D{SCENARIO_PROPERTY_NAME}=")
    ]
    expected_scenarios = (
        [] if scenario_id is None else [f"-D{SCENARIO_PROPERTY_NAME}={scenario_id}"]
    )
    if scenario_arguments != expected_scenarios:
        raise BaselineError("Generated command does not select the exact requested scenario")
    launch = require_object(configuration.manifest, "launch")
    resolution = require_object(launch, "resolution")
    parent_metadata = load_json_object(
        vanilla_metadata_path(configuration, root), "Minecraft version metadata"
    )
    raw_asset_index = parent_metadata.get("assetIndex")
    if not isinstance(raw_asset_index, dict):
        raise BaselineError("Minecraft version metadata has no asset index")
    asset_index_id = safe_leaf_name(raw_asset_index.get("id"), "assetIndex.id")
    username = str(launch["offline_username"])
    expected_options = {
        "--gameDir": str(game_directory(configuration, root)),
        "--assetsDir": str(launcher_directory(configuration, root) / "assets"),
        "--assetIndex": asset_index_id,
        "--version": version_id(configuration),
        "--username": username,
        "--uuid": offline_uuid(username),
        "--accessToken": OFFLINE_ACCESS_TOKEN,
        "--clientId": OFFLINE_CLIENT_ID,
        "--xuid": OFFLINE_XUID,
        "--width": str(resolution["width"]),
        "--height": str(resolution["height"]),
    }
    for option, expected_value in expected_options.items():
        indexes = [index for index, value in enumerate(command) if value == option]
        if (
            len(indexes) != 1
            or indexes[0] + 1 >= len(command)
            or command[indexes[0] + 1] != expected_value
        ):
            raise BaselineError(
                f"Generated command does not contain exact {option}={expected_value}"
            )
    width_index = command.index("--width")
    if command.index("--height") != width_index + 2:
        raise BaselineError("Generated resolution arguments are not one adjacent exact pair")
    forbidden_prefixes = (
        "-javaagent",
        "-agentlib",
        "-agentpath",
        "-Xbootclasspath",
        "--patch-module",
        "--module-path",
        "--class-path",
    )
    if any(
        value.startswith(forbidden_prefixes)
        or value in ("-p", "-jar")
        or value.startswith("@")
        or "${" in value
        for value in command
    ):
        raise BaselineError("Generated command contains an unsupported code-injection option")
    expected_jvm_paths = {
        "-Duser.home=": home_directory(configuration, root),
        "-Djava.io.tmpdir=": temporary_directory(configuration, root),
        "-Djava.library.path=": native_extraction_directory(configuration, root),
        "-Djna.tmpdir=": native_extraction_directory(configuration, root),
        "-Dorg.lwjgl.system.SharedLibraryExtractPath=": native_extraction_directory(
            configuration, root
        ),
        "-Dio.netty.native.workdir=": native_extraction_directory(configuration, root),
    }
    for prefix, expected_path in expected_jvm_paths.items():
        values = [value for value in command if value.startswith(prefix)]
        if values != [prefix + str(expected_path)]:
            raise BaselineError(
                f"Generated command does not isolate the JVM path {prefix[:-1]}"
            )
    classpath_indexes = [
        index for index, value in enumerate(command) if value in ("-cp", "-classpath")
    ]
    if len(classpath_indexes) != 1 or classpath_indexes[0] + 1 >= len(command):
        raise BaselineError("Generated command does not contain exactly one classpath")
    raw_entries = command[classpath_indexes[0] + 1].split(os.pathsep)
    if not raw_entries or any(not value for value in raw_entries):
        raise BaselineError("Generated command contains an empty classpath entry")
    launcher_root = launcher_directory(configuration, root).absolute()
    classpath_entries: list[Path] = []
    for raw_entry in raw_entries:
        entry = Path(raw_entry).absolute()
        ensure_no_symlink_components(launcher_root, entry, "Generated classpath")
        if entry.is_symlink() or not entry.is_file():
            raise BaselineError(f"Generated classpath entry is missing or linked: {entry}")
        if entry in classpath_entries:
            raise BaselineError(f"Generated classpath duplicates an entry: {entry}")
        classpath_entries.append(entry)
    minecraft_version = str(runtime_spec(configuration)["minecraft_version"])
    expected_client_jar = (
        launcher_root / "versions" / minecraft_version / f"{minecraft_version}.jar"
    )
    if expected_client_jar not in classpath_entries:
        raise BaselineError("Generated classpath does not contain the vanilla client JAR")
    verify_exact_command_classpath(configuration, root, command)
    knot_index = command.index("net.fabricmc.loader.impl.launch.knot.KnotClient")
    if not classpath_indexes[0] < knot_index < command.index("--gameDir"):
        raise BaselineError("Generated command has an invalid launcher/main-class order")

    allowed_path_root = root.absolute()
    classpath_value_index = classpath_indexes[0] + 1
    for index, argument in enumerate(command):
        if index in (0, classpath_value_index):
            continue
        raw_path: str | None = None
        if argument.startswith("/"):
            raw_path = argument
        else:
            _prefix, separator, suffix = argument.partition("=/")
            if separator:
                raw_path = "/" + suffix
        if raw_path is None:
            continue
        argument_path = Path(raw_path).absolute()
        if not is_relative_to(argument_path, allowed_path_root):
            raise BaselineError(
                f"Generated command contains an external path argument: {argument}"
            )


def canonical_json_sha256(value: object) -> str:
    content = json.dumps(
        value,
        ensure_ascii=True,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return hashlib.sha256(content).hexdigest()


def controlled_launch_environment(
    configuration: Configuration, root: Path, java_path: Path
) -> dict[str, str]:
    """Returns the complete, deliberately small environment inherited by Minecraft."""

    owned_home = home_directory(configuration, root)
    owned_temporary = temporary_directory(configuration, root)
    return {
        "HOME": str(owned_home),
        "JAVA_HOME": str(java_path.parent.parent),
        "LANG": "en_US.UTF-8",
        "LC_ALL": "en_US.UTF-8",
        "PATH": "/usr/bin:/bin:/usr/sbin:/sbin",
        "TMPDIR": str(owned_temporary),
        "XDG_CACHE_HOME": str(owned_home / ".cache"),
    }


def verify_fresh_skin_cache(configuration: Configuration, root: Path) -> None:
    launcher_root = launcher_directory(configuration, root)
    cache_root = skin_cache_directory(configuration, root)
    ensure_no_symlink_components(
        launcher_root, cache_root, "Prelaunch Minecraft skin cache"
    )
    if cache_root.exists() or cache_root.is_symlink():
        raise BaselineError(
            "Minecraft's mutable assets/skins cache must be absent before launch"
        )


def runtime_skin_cache_descriptor(
    configuration: Configuration, root: Path
) -> dict[str, object]:
    launcher_root = launcher_directory(configuration, root)
    cache_root = skin_cache_directory(configuration, root)
    ensure_no_symlink_components(launcher_root, cache_root, "Minecraft skin cache")
    relative_cache_path = cache_root.relative_to(launcher_root).as_posix()
    if not cache_root.exists() and not cache_root.is_symlink():
        return {
            "path": relative_cache_path,
            "present": False,
            "file_count": 0,
            "total_size": 0,
            "files": [],
        }
    if cache_root.is_symlink() or not cache_root.is_dir():
        raise BaselineError(f"Minecraft skin cache is not a regular directory: {cache_root}")

    files: list[dict[str, object]] = []
    total_size = 0
    for path in sorted(cache_root.rglob("*"), key=lambda value: value.as_posix()):
        ensure_no_symlink_components(cache_root, path, "Minecraft skin-cache entry")
        relative_path = path.relative_to(cache_root)
        if path.is_symlink():
            raise BaselineError(f"Minecraft skin cache contains a symlink: {path}")
        if path.is_dir():
            if (
                len(relative_path.parts) != 1
                or re.fullmatch(r"[0-9a-f]{2}", path.name) is None
            ):
                raise BaselineError(
                    f"Minecraft skin cache contains an unexpected directory: {path}"
                )
            continue
        if (
            not path.is_file()
            or len(relative_path.parts) != 2
            or re.fullmatch(r"[0-9a-f]{2}", relative_path.parts[0]) is None
            or re.fullmatch(r"[0-9a-f]{40}", relative_path.parts[1]) is None
            or relative_path.parts[0] != relative_path.parts[1][:2]
        ):
            raise BaselineError(f"Minecraft skin cache contains an invalid entry: {path}")
        size = path.stat().st_size
        if size <= 0:
            raise BaselineError(f"Minecraft skin cache contains an empty file: {path}")
        total_size += size
        if len(files) >= MAXIMUM_SKIN_CACHE_FILE_COUNT:
            raise BaselineError("Minecraft skin cache exceeds the file-count bound")
        if total_size > MAXIMUM_SKIN_CACHE_SIZE:
            raise BaselineError("Minecraft skin cache exceeds the total-size bound")
        files.append(
            {
                "path": relative_path.as_posix(),
                "size": size,
                "sha256": sha256_file(path),
            }
        )
    return {
        "path": relative_cache_path,
        "present": True,
        "file_count": len(files),
        "total_size": total_size,
        "files": files,
    }


def launcher_file_inventory(
    configuration: Configuration, root: Path
) -> list[dict[str, object]]:
    launcher_root = launcher_directory(configuration, root)
    ensure_no_symlink_components(root, launcher_root, "Owned launcher inventory")
    if launcher_root.is_symlink() or not launcher_root.is_dir():
        raise BaselineError(f"Owned launcher root is missing or linked: {launcher_root}")
    files: list[dict[str, object]] = []
    total_size = 0
    try:
        paths = sorted(launcher_root.rglob("*"), key=lambda value: value.as_posix())
    except OSError as exception:
        raise BaselineError(
            f"Cannot enumerate the owned launcher inventory: {exception}"
        ) from exception
    mutable_skin_cache = skin_cache_directory(configuration, root)
    for path in paths:
        if path == mutable_skin_cache or is_relative_to(path, mutable_skin_cache):
            continue
        ensure_no_symlink_components(launcher_root, path, "Launcher inventory entry")
        if path.is_symlink():
            raise BaselineError(f"Launcher inventory contains a symlink: {path}")
        if path.is_dir():
            continue
        if not path.is_file():
            raise BaselineError(f"Launcher inventory contains a non-file entry: {path}")
        size = path.stat().st_size
        total_size += size
        if len(files) >= MAXIMUM_LAUNCHER_FILE_COUNT:
            raise BaselineError("Launcher inventory exceeds the file-count bound")
        if total_size > MAXIMUM_LAUNCHER_SIZE:
            raise BaselineError("Launcher inventory exceeds the total-size bound")
        files.append(
            {
                "path": path.relative_to(launcher_root).as_posix(),
                "size": size,
                "sha256": sha256_file(path),
            }
        )
    if not files:
        raise BaselineError("Launcher inventory is empty")
    return files


def command_classpath_inventory(
    configuration: Configuration, root: Path, command: list[str]
) -> list[dict[str, object]]:
    classpath_indexes = [
        index for index, value in enumerate(command) if value in ("-cp", "-classpath")
    ]
    if len(classpath_indexes) != 1 or classpath_indexes[0] + 1 >= len(command):
        raise BaselineError("Generated command does not contain one complete classpath")
    launcher_root = launcher_directory(configuration, root).absolute()
    raw_entries = command[classpath_indexes[0] + 1].split(os.pathsep)
    resolved_entries: list[Path] = []
    inventory: list[dict[str, object]] = []
    for index, raw_entry in enumerate(raw_entries):
        entry = Path(raw_entry).absolute()
        ensure_no_symlink_components(launcher_root, entry, "Generated classpath")
        if entry in resolved_entries:
            raise BaselineError(f"Generated classpath duplicates an entry: {entry}")
        if entry.is_symlink() or not entry.is_file():
            raise BaselineError(f"Generated classpath entry is missing or linked: {entry}")
        resolved_entries.append(entry)
        inventory.append(
            {
                "index": index,
                "path": entry.relative_to(launcher_root).as_posix(),
                "size": entry.stat().st_size,
                "sha256": sha256_file(entry),
            }
        )
    return inventory


def library_is_selected_on_macos(library: dict[str, object]) -> bool:
    rules = library.get("rules")
    if rules is None:
        return True
    return rules == [{"action": "allow", "os": {"name": "osx"}}]


def vanilla_library_inventory(
    configuration: Configuration, root: Path
) -> list[dict[str, object]]:
    launcher_root = launcher_directory(configuration, root)
    metadata = load_json_object(
        vanilla_metadata_path(configuration, root), "Minecraft version metadata"
    )
    raw_libraries = metadata.get("libraries")
    if not isinstance(raw_libraries, list) or not all(
        isinstance(value, dict) for value in raw_libraries
    ):
        raise BaselineError("Minecraft metadata has an invalid library list")
    inventory: list[dict[str, object]] = []
    for metadata_index, library in enumerate(raw_libraries):
        if not library_is_selected_on_macos(library):
            continue
        coordinate = library.get("name")
        if not isinstance(coordinate, str):
            raise BaselineError("Minecraft metadata has an invalid library coordinate")
        downloads = require_object(library, "downloads")
        artifact = require_object(downloads, "artifact")
        require_exact_fields(
            artifact,
            {"path", "sha1", "size", "url"},
            f"Minecraft library artifact {coordinate}",
        )
        relative_path = maven_library_path(coordinate)
        if artifact.get("path") != relative_path.as_posix():
            raise BaselineError(
                f"Minecraft library path disagrees with Maven coordinates: {coordinate}"
            )
        validate_sha1(artifact.get("sha1"), f"Minecraft library {coordinate}")
        size = artifact.get("size")
        if type(size) is not int or int(size) <= 0:
            raise BaselineError(f"Minecraft library size is invalid: {coordinate}")
        expected_url = f"https://libraries.minecraft.net/{relative_path.as_posix()}"
        if artifact.get("url") != expected_url:
            raise BaselineError(f"Minecraft library URL is invalid: {coordinate}")
        path = launcher_root / "libraries" / Path(*relative_path.parts)
        ensure_no_symlink_components(launcher_root, path, "Minecraft library JAR")
        if (
            path.is_symlink()
            or not path.is_file()
            or path.stat().st_size != size
            or sha1_file(path) != artifact["sha1"]
        ):
            raise BaselineError(f"Minecraft library JAR failed validation: {coordinate}")
        inventory.append(
            {
                "metadata_index": metadata_index,
                "coordinate": coordinate,
                "path": path.relative_to(launcher_root).as_posix(),
                "size": size,
                "sha1": artifact["sha1"],
                "sha256": sha256_file(path),
            }
        )
    if len(inventory) != 63:
        raise BaselineError(
            f"Minecraft metadata selected {len(inventory)} macOS libraries, expected 63"
        )
    return inventory


def expected_merged_classpath_paths(
    configuration: Configuration, root: Path
) -> list[str]:
    fabric_profile = verify_official_fabric_profile(configuration, root)
    vanilla_metadata = load_json_object(
        vanilla_metadata_path(configuration, root), "Minecraft version metadata"
    )
    raw_fabric_libraries = fabric_profile.get("libraries")
    raw_vanilla_libraries = vanilla_metadata.get("libraries")
    if not isinstance(raw_fabric_libraries, list) or not isinstance(
        raw_vanilla_libraries, list
    ):
        raise BaselineError("Pinned runtime metadata has an invalid library list")
    merged: list[dict[str, object]] = []
    fabric_keys: set[str] = set()
    for raw_library in raw_fabric_libraries:
        if not isinstance(raw_library, dict) or not isinstance(
            raw_library.get("name"), str
        ):
            raise BaselineError("Pinned Fabric profile has an invalid library")
        coordinate = str(raw_library["name"])
        fabric_keys.add(":".join(coordinate.split(":")[:-1]))
        merged.append(raw_library)
    for raw_library in raw_vanilla_libraries:
        if not isinstance(raw_library, dict) or not isinstance(
            raw_library.get("name"), str
        ):
            raise BaselineError("Pinned Minecraft metadata has an invalid library")
        coordinate = str(raw_library["name"])
        if ":".join(coordinate.split(":")[:-1]) not in fabric_keys:
            merged.append(raw_library)
    launcher_root = launcher_directory(configuration, root)
    paths = [
        (
            launcher_root
            / "libraries"
            / Path(*maven_library_path(str(library["name"])).parts)
        )
        .relative_to(launcher_root)
        .as_posix()
        for library in merged
        if library_is_selected_on_macos(library)
    ]
    minecraft_version = str(runtime_spec(configuration)["minecraft_version"])
    paths.append(f"versions/{minecraft_version}/{minecraft_version}.jar")
    if len(paths) != 72 or len(set(paths)) != len(paths):
        raise BaselineError("Pinned runtime did not derive the exact 72-entry classpath")
    return paths


def verify_exact_command_classpath(
    configuration: Configuration, root: Path, command: list[str]
) -> None:
    actual_paths = [
        str(entry["path"])
        for entry in command_classpath_inventory(configuration, root, command)
    ]
    if actual_paths != expected_merged_classpath_paths(configuration, root):
        raise BaselineError(
            "Generated classpath differs from the exact pinned Fabric/Minecraft merge"
        )


def maven_library_path(coordinate: str) -> PurePosixPath:
    raw_coordinate, separator, suffix = coordinate.partition("@")
    extension = suffix if separator else "jar"
    parts = raw_coordinate.split(":")
    if (
        len(parts) < 3
        or any(not value or "/" in value or "\\" in value for value in parts)
        or not extension
        or "/" in extension
        or "\\" in extension
    ):
        raise BaselineError(f"Minecraft metadata has an unsafe Maven coordinate: {coordinate}")
    group, artifact, version, *classifiers = parts
    file_name = f"{artifact}-{version}"
    if classifiers:
        file_name += "-" + "-".join(classifiers)
    file_name += f".{extension}"
    return PurePosixPath(*group.split("."), artifact, version, file_name)


def native_classifier_inventory(
    configuration: Configuration,
    root: Path,
    command: list[str] | None = None,
) -> list[dict[str, object]]:
    if platform.system() != "Darwin" or platform.machine() not in ("arm64", "aarch64"):
        raise BaselineError(
            "This original-baseline native contract requires Apple Silicon macOS"
        )
    launcher_root = launcher_directory(configuration, root)
    metadata = load_json_object(
        vanilla_metadata_path(configuration, root), "Minecraft version metadata"
    )
    raw_libraries = metadata.get("libraries")
    if not isinstance(raw_libraries, list) or any(
        not isinstance(value, dict) for value in raw_libraries
    ):
        raise BaselineError("Minecraft metadata has an invalid library list")
    if any("natives" in library for library in raw_libraries):
        raise BaselineError(
            "Minecraft 1.21.1 unexpectedly declares legacy extracted natives"
        )
    selected = [
        (index, str(library.get("name")))
        for index, library in enumerate(raw_libraries)
        if library.get("rules") == [{"action": "allow", "os": {"name": "osx"}}]
        and ":natives-macos" in str(library.get("name"))
    ]
    if selected != list(EXPECTED_NATIVE_CLASSIFIERS):
        raise BaselineError(
            "Minecraft metadata does not select the exact macOS native classifiers"
        )

    classpath_paths: list[str] | None = None
    if command is not None:
        classpath_paths = [
            str(entry["path"])
            for entry in command_classpath_inventory(configuration, root, command)
        ]

    files: list[dict[str, object]] = []
    for position, ((metadata_index, coordinate), classpath_index) in enumerate(
        zip(EXPECTED_NATIVE_CLASSIFIERS, EXPECTED_NATIVE_CLASSPATH_INDEXES, strict=True)
    ):
        library = raw_libraries[metadata_index]
        require_exact_fields(
            library,
            {"downloads", "name", "rules"},
            f"Native classifier library {coordinate}",
        )
        downloads = require_object(library, "downloads")
        require_exact_fields(
            downloads, {"artifact"}, f"Native classifier downloads {coordinate}"
        )
        artifact = require_object(downloads, "artifact")
        require_exact_fields(
            artifact,
            {"path", "sha1", "size", "url"},
            f"Native classifier artifact {coordinate}",
        )
        relative_path = maven_library_path(coordinate)
        if artifact.get("path") != relative_path.as_posix():
            raise BaselineError(
                f"Native classifier metadata path disagrees with Maven coordinates: {coordinate}"
            )
        validate_sha1(artifact.get("sha1"), f"native classifier {coordinate}")
        size = artifact.get("size")
        if type(size) is not int or int(size) <= 0:
            raise BaselineError(f"Native classifier has an invalid size: {coordinate}")
        expected_url = f"https://libraries.minecraft.net/{relative_path.as_posix()}"
        if artifact.get("url") != expected_url:
            raise BaselineError(f"Native classifier has an unexpected URL: {coordinate}")
        path = launcher_root / "libraries" / Path(*relative_path.parts)
        ensure_no_symlink_components(launcher_root, path, "Native classifier JAR")
        if (
            path.is_symlink()
            or not path.is_file()
            or path.stat().st_size != size
            or sha1_file(path) != artifact["sha1"]
        ):
            raise BaselineError(f"Native classifier JAR failed validation: {coordinate}")
        launcher_relative_path = path.relative_to(launcher_root).as_posix()
        if classpath_paths is not None and (
            classpath_index >= len(classpath_paths)
            or classpath_paths[classpath_index] != launcher_relative_path
            or classpath_paths.count(launcher_relative_path) != 1
        ):
            raise BaselineError(
                f"Native classifier has the wrong generated classpath position: {coordinate}"
            )
        files.append(
            {
                "metadata_index": metadata_index,
                "classpath_index": classpath_index,
                "coordinate": coordinate,
                "path": launcher_relative_path,
                "size": size,
                "sha1": artifact["sha1"],
                "sha256": sha256_file(path),
            }
        )
    return files


def asset_inventory_descriptor(
    configuration: Configuration, root: Path
) -> dict[str, object]:
    launcher_root = launcher_directory(configuration, root)
    metadata = load_json_object(
        vanilla_metadata_path(configuration, root), "Minecraft version metadata"
    )
    raw_asset_index = metadata.get("assetIndex")
    if not isinstance(raw_asset_index, dict):
        raise BaselineError("Minecraft metadata has no asset-index contract")
    require_exact_fields(
        raw_asset_index,
        {"id", "sha1", "size", "totalSize", "url"},
        "Minecraft asset-index contract",
    )
    if raw_asset_index != expected_asset_index_contract(configuration):
        raise BaselineError("Minecraft metadata selects another pinned asset index")
    asset_index_id = safe_leaf_name(raw_asset_index.get("id"), "assetIndex.id")
    expected_sha1 = raw_asset_index.get("sha1")
    expected_size = raw_asset_index.get("size")
    if (
        not isinstance(expected_sha1, str)
        or re.fullmatch(r"[0-9a-f]{40}", expected_sha1) is None
        or type(expected_size) is not int
        or int(expected_size) <= 0
    ):
        raise BaselineError("Minecraft asset-index digest contract is invalid")
    index_path = launcher_root / "assets" / "indexes" / f"{asset_index_id}.json"
    if index_path.is_symlink() or not index_path.is_file():
        raise BaselineError(f"Minecraft asset index is missing or linked: {index_path}")
    pinned_asset_index = require_object(
        require_object(runtime_spec(configuration), "metadata"), "asset_index"
    )
    if (
        index_path.stat().st_size != int(expected_size)
        or sha1_file(index_path) != expected_sha1
        or sha256_file(index_path) != pinned_asset_index["sha256"]
    ):
        raise BaselineError("Minecraft asset index differs from version metadata")
    index = load_json_object(index_path, "Minecraft asset index")
    raw_objects = index.get("objects")
    if set(index) != {"objects"} or not isinstance(raw_objects, dict):
        raise BaselineError("Minecraft asset index has an unexpected shape")
    expected_paths: set[Path] = set()
    object_inventory: list[dict[str, object]] = []
    for logical_name in sorted(raw_objects):
        raw_object = raw_objects[logical_name]
        if not isinstance(logical_name, str) or not isinstance(raw_object, dict):
            raise BaselineError("Minecraft asset index contains an invalid object")
        require_exact_fields(raw_object, {"hash", "size"}, "Minecraft asset object")
        object_sha1 = raw_object.get("hash")
        object_size = raw_object.get("size")
        if (
            not isinstance(object_sha1, str)
            or re.fullmatch(r"[0-9a-f]{40}", object_sha1) is None
            or type(object_size) is not int
            or int(object_size) < 0
        ):
            raise BaselineError("Minecraft asset index contains an invalid object pin")
        object_path = (
            launcher_root / "assets" / "objects" / object_sha1[:2] / object_sha1
        )
        ensure_no_symlink_components(
            launcher_root, object_path, "Minecraft asset object"
        )
        if (
            object_path.is_symlink()
            or not object_path.is_file()
            or object_path.stat().st_size != int(object_size)
            or sha1_file(object_path) != object_sha1
        ):
            raise BaselineError(f"Minecraft asset object failed validation: {logical_name}")
        expected_paths.add(object_path)
        object_inventory.append(
            {
                "logical_name": logical_name,
                "path": object_path.relative_to(launcher_root).as_posix(),
                "size": object_size,
                "sha1": object_sha1,
            }
        )
    objects_root = launcher_root / "assets" / "objects"
    actual_paths = {
        path
        for path in objects_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    }
    if actual_paths != expected_paths:
        raise BaselineError(
            "Minecraft asset-object inventory differs from the exact asset index"
        )
    return {
        "id": asset_index_id,
        "index": {
            "path": index_path.relative_to(launcher_root).as_posix(),
            "size": index_path.stat().st_size,
            "sha1": expected_sha1,
            "sha256": sha256_file(index_path),
        },
        "object_count": len(object_inventory),
        "objects_sha256": canonical_json_sha256(object_inventory),
    }


def provisioning_metadata_descriptor(
    configuration: Configuration, root: Path
) -> dict[str, object]:
    vanilla = verify_official_vanilla_metadata(configuration, root)
    fabric = verify_official_fabric_profile(configuration, root)
    vanilla_path = pinned_vanilla_metadata_path(configuration, root)
    fabric_path = pinned_fabric_profile_path(configuration, root)
    return {
        "minecraft": {
            "path": vanilla_path.relative_to(root).as_posix(),
            "size": vanilla_path.stat().st_size,
            "sha1": sha1_file(vanilla_path),
            "sha256": sha256_file(vanilla_path),
            "canonical_json_sha256": canonical_json_sha256(vanilla),
        },
        "fabric": {
            "path": fabric_path.relative_to(root).as_posix(),
            "size": fabric_path.stat().st_size,
            "sha1": sha1_file(fabric_path),
            "sha256": sha256_file(fabric_path),
            "canonical_json_sha256": canonical_json_sha256(fabric),
        },
    }


def runtime_lock_descriptor(
    configuration: Configuration, root: Path
) -> dict[str, object]:
    verify_fresh_skin_cache(configuration, root)
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "manifest_sha256": sha256_file(configuration.manifest_path),
        "generator": verify_launcher_library(
            temporary_directory(configuration, root) / "python-bytecode-cache"
        ),
        "provisioning_metadata": provisioning_metadata_descriptor(configuration, root),
        "version_metadata": version_metadata_inventory(configuration, root),
        "fabric_libraries": fabric_library_inventory(configuration, root),
        "vanilla_libraries": vanilla_library_inventory(configuration, root),
        "native_classifiers": native_classifier_inventory(configuration, root),
        "assets": asset_inventory_descriptor(configuration, root),
        "launcher_files": launcher_file_inventory(configuration, root),
    }


def verify_runtime_lock(configuration: Configuration, root: Path) -> None:
    lock = load_json_object(
        runtime_lock_path(configuration, root), "Original-baseline installed-runtime lock"
    )
    if lock != runtime_lock_descriptor(configuration, root):
        raise BaselineError(
            "Installed Minecraft/Fabric runtime differs from its provision-time lock"
        )


def launch_attempt_descriptor(
    configuration: Configuration,
    root: Path,
    scenario_id: str,
    java_path: Path,
    command: list[str],
) -> dict[str, object]:
    verify_launch_command(configuration, command, java_path, root, scenario_id)
    verify_staged_reference(configuration, root)
    java = java_path.resolve(strict=True)
    if java.is_symlink() or not java.is_file() or not os.access(java, os.X_OK):
        raise BaselineError(f"Selected Java executable is not a regular executable: {java}")
    artifact_lock = artifact_lock_path(configuration, root)
    verify_exact_file(
        artifact_lock,
        sha256_file(artifact_lock),
        artifact_lock.stat().st_size,
        "Published artifact lock",
    )
    environment = controlled_launch_environment(configuration, root, java)
    generator = verify_launcher_library(
        temporary_directory(configuration, root) / "python-bytecode-cache"
    )
    prelaunch_profile = prelaunch_profile_descriptor(configuration, root)
    verify_exact_file(
        CAFFEINATE_PATH,
        sha256_file(CAFFEINATE_PATH),
        CAFFEINATE_PATH.stat().st_size,
        "macOS caffeinate executable",
    )
    return {
        "schema": 1,
        "profile_id": profile_spec(configuration)["id"],
        "scenario": scenario_id,
        "created_at_unix_ns": time.time_ns(),
        "manifest_sha256": sha256_file(configuration.manifest_path),
        "artifact_lock": {
            "size": artifact_lock.stat().st_size,
            "sha256": sha256_file(artifact_lock),
        },
        "prelaunch_profile": prelaunch_profile,
        "generator": generator,
        "provisioning_metadata": provisioning_metadata_descriptor(configuration, root),
        "java": {
            "path": str(java),
            "major": java_major_version(java),
            "size": java.stat().st_size,
            "sha256": sha256_file(java),
            "runtime": java_runtime_descriptor(java),
        },
        "launch": {
            "wrapper": [str(CAFFEINATE_PATH), "-dimsu"],
            "wrapper_executable": {
                "path": str(CAFFEINATE_PATH),
                "size": CAFFEINATE_PATH.stat().st_size,
                "sha256": sha256_file(CAFFEINATE_PATH),
            },
            "arguments": command,
            "arguments_sha256": canonical_json_sha256(command),
            "classpath": command_classpath_inventory(configuration, root, command),
            "environment": environment,
            "environment_sha256": canonical_json_sha256(environment),
        },
        "version_metadata": version_metadata_inventory(configuration, root),
        "fabric_libraries": fabric_library_inventory(configuration, root),
        "vanilla_libraries": vanilla_library_inventory(configuration, root),
        "native_classifiers": native_classifier_inventory(configuration, root, command),
        "assets": asset_inventory_descriptor(configuration, root),
        "launcher_files": launcher_file_inventory(configuration, root),
    }


def verify_launch_attempt(
    configuration: Configuration,
    root: Path,
    expected: dict[str, object] | None = None,
) -> dict[str, object]:
    attempt_path = launch_attempt_path(configuration, root)
    attempt = load_json_object(attempt_path, "Original-baseline launch-attempt seal")
    require_exact_fields(
        attempt,
        {
            "schema",
            "profile_id",
            "scenario",
            "created_at_unix_ns",
            "manifest_sha256",
            "artifact_lock",
            "prelaunch_profile",
            "generator",
            "provisioning_metadata",
            "java",
            "launch",
            "version_metadata",
            "fabric_libraries",
            "vanilla_libraries",
            "native_classifiers",
            "assets",
            "launcher_files",
        },
        "The launch-attempt seal",
    )
    if expected is not None and attempt != expected:
        raise BaselineError("Launch-attempt seal differs from the in-memory launch contract")
    if (
        attempt.get("schema") != 1
        or attempt.get("profile_id") != profile_spec(configuration)["id"]
        or attempt.get("scenario") != scenario_spec(configuration)["id"]
        or type(attempt.get("created_at_unix_ns")) is not int
        or int(attempt["created_at_unix_ns"]) <= 0
        or attempt.get("manifest_sha256") != sha256_file(configuration.manifest_path)
    ):
        raise BaselineError("Launch-attempt seal does not describe this exact profile")

    raw_artifact_lock = attempt.get("artifact_lock")
    if not isinstance(raw_artifact_lock, dict):
        raise BaselineError("Launch-attempt artifact-lock descriptor is invalid")
    require_exact_fields(raw_artifact_lock, {"size", "sha256"}, "Artifact-lock seal")
    artifact_lock = artifact_lock_path(configuration, root)
    verify_exact_file(
        artifact_lock,
        validate_sha256(raw_artifact_lock.get("sha256"), "sealed artifact lock"),
        raw_artifact_lock.get("size")
        if type(raw_artifact_lock.get("size")) is int
        else None,
        "Sealed published artifact lock",
    )
    artifact_lock_data = artifact_lock_matches(
        configuration, root, require_prelaunch=False
    )
    if attempt.get("prelaunch_profile") != artifact_lock_data.get(
        "prelaunch_profile"
    ):
        raise BaselineError(
            "Launch-attempt prelaunch profile differs from the staged artifact lock"
        )

    raw_java = attempt.get("java")
    if not isinstance(raw_java, dict):
        raise BaselineError("Launch-attempt Java descriptor is invalid")
    require_exact_fields(
        raw_java, {"path", "major", "size", "sha256", "runtime"}, "Java seal"
    )
    java_path = Path(str(raw_java.get("path")))
    if raw_java.get("major") != 21:
        raise BaselineError("Launch-attempt seal did not use Java 21")
    verify_exact_file(
        java_path,
        validate_sha256(raw_java.get("sha256"), "sealed Java executable"),
        raw_java.get("size") if type(raw_java.get("size")) is int else None,
        "Sealed Java executable",
    )
    if raw_java.get("runtime") != java_runtime_descriptor(java_path):
        raise BaselineError("Launch-attempt Java runtime image changed")

    raw_launch = attempt.get("launch")
    if not isinstance(raw_launch, dict):
        raise BaselineError("Launch-attempt command descriptor is invalid")
    require_exact_fields(
        raw_launch,
        {
            "wrapper",
            "wrapper_executable",
            "arguments",
            "arguments_sha256",
            "classpath",
            "environment",
            "environment_sha256",
        },
        "Launch command seal",
    )
    raw_arguments = raw_launch.get("arguments")
    if not isinstance(raw_arguments, list) or not all(
        isinstance(value, str) for value in raw_arguments
    ):
        raise BaselineError("Launch-attempt arguments are invalid")
    arguments = [str(value) for value in raw_arguments]
    if (
        raw_launch.get("wrapper") != [str(CAFFEINATE_PATH), "-dimsu"]
        or raw_launch.get("arguments_sha256") != canonical_json_sha256(arguments)
    ):
        raise BaselineError("Launch-attempt command digest is invalid")
    raw_wrapper = raw_launch.get("wrapper_executable")
    if not isinstance(raw_wrapper, dict):
        raise BaselineError("Launch-attempt wrapper executable descriptor is invalid")
    require_exact_fields(
        raw_wrapper, {"path", "size", "sha256"}, "Launch wrapper executable seal"
    )
    if raw_wrapper.get("path") != str(CAFFEINATE_PATH):
        raise BaselineError("Launch-attempt wrapper path changed")
    verify_exact_file(
        CAFFEINATE_PATH,
        validate_sha256(raw_wrapper.get("sha256"), "sealed launch wrapper"),
        raw_wrapper.get("size") if type(raw_wrapper.get("size")) is int else None,
        "Sealed macOS caffeinate executable",
    )
    verify_launch_command(
        configuration,
        arguments,
        java_path,
        root,
        str(attempt["scenario"]),
    )
    if raw_launch.get("classpath") != command_classpath_inventory(
        configuration, root, arguments
    ):
        raise BaselineError("Launch-attempt classpath inventory changed")
    expected_environment = controlled_launch_environment(configuration, root, java_path)
    if (
        raw_launch.get("environment") != expected_environment
        or raw_launch.get("environment_sha256")
        != canonical_json_sha256(expected_environment)
    ):
        raise BaselineError("Launch-attempt environment is not exactly isolated")

    if attempt.get("generator") != verify_launcher_library(
        temporary_directory(configuration, root) / "python-bytecode-cache"
    ):
        raise BaselineError("Launch-attempt launcher generator changed")
    if attempt.get("provisioning_metadata") != provisioning_metadata_descriptor(
        configuration, root
    ):
        raise BaselineError("Launch-attempt provisioning metadata changed")
    if attempt.get("version_metadata") != version_metadata_inventory(configuration, root):
        raise BaselineError("Launch-attempt version metadata changed")
    if attempt.get("fabric_libraries") != fabric_library_inventory(
        configuration, root
    ):
        raise BaselineError("Launch-attempt Fabric library inventory changed")
    if attempt.get("vanilla_libraries") != vanilla_library_inventory(
        configuration, root
    ):
        raise BaselineError("Launch-attempt Minecraft library inventory changed")
    if attempt.get("native_classifiers") != native_classifier_inventory(
        configuration, root, arguments
    ):
        raise BaselineError("Launch-attempt native classifier inventory changed")
    if attempt.get("assets") != asset_inventory_descriptor(configuration, root):
        raise BaselineError("Launch-attempt asset inventory changed")

    current_launcher_files = launcher_file_inventory(configuration, root)
    if attempt.get("launcher_files") != current_launcher_files:
        raise BaselineError("Sealed launcher files changed after the launch attempt")
    return attempt


def check_environment(
    configuration: Configuration, scenario_id: str | None = None
) -> tuple[Path, list[str]]:
    root = verify_owned_runtime(configuration)
    with acquire_runtime_lifecycle_lock(configuration, root):
        return _check_environment_locked(configuration, scenario_id)


def _check_environment_locked(
    configuration: Configuration, scenario_id: str | None = None
) -> tuple[Path, list[str]]:
    verify_reference_bundle(configuration)
    verify_harness_artifact(configuration)
    root = verify_owned_runtime(configuration)
    verify_installed_game(configuration, root)
    verify_runtime_lock(configuration, root)
    verify_staged_reference(configuration, root)
    selected_scenario_id = resolve_scenario_id(
        configuration,
        scenario_id or str(scenario_spec(configuration)["id"]),
    )
    verify_capture_layout(configuration, root, require_fresh=True)
    assert_runtime_not_running(configuration, root)
    java_path = resolve_java_21()
    command = generate_launch_command(
        configuration, java_path, root, selected_scenario_id
    )
    verify_launch_command(
        configuration, command, java_path, root, selected_scenario_id
    )
    return java_path, command


def require_capture_harness(configuration: Configuration) -> None:
    harness = harness_spec(configuration)
    if harness.get("status") != "implemented":
        raise BaselineError(
            "The original Fabric 1.21.1 capture harness is not implemented"
        )
    verify_harness_artifact(configuration)


def paeth_predictor(left: int, above: int, upper_left: int) -> int:
    prediction = left + above - upper_left
    left_distance = abs(prediction - left)
    above_distance = abs(prediction - above)
    upper_left_distance = abs(prediction - upper_left)
    if left_distance <= above_distance and left_distance <= upper_left_distance:
        return left
    if above_distance <= upper_left_distance:
        return above
    return upper_left


def decode_png(path: Path) -> PngImage:
    if path.is_symlink() or not path.is_file():
        raise BaselineError(f"Original-baseline screenshot is missing or linked: {path}")
    try:
        content = path.read_bytes()
    except OSError as exception:
        raise BaselineError(f"Cannot read original-baseline screenshot {path}") from exception
    if len(content) < 45 or content[:8] != PNG_SIGNATURE:
        raise BaselineError("Original-baseline screenshot has no canonical PNG signature")
    if len(content) > MAXIMUM_DECODED_PNG_SIZE:
        raise BaselineError("Original-baseline screenshot exceeds the encoded-size bound")
    offset = len(PNG_SIGNATURE)
    chunk_types: list[bytes] = []
    header: tuple[int, int, int, int, int, int, int] | None = None
    compressed = bytearray()
    while offset < len(content):
        if offset + 12 > len(content):
            raise BaselineError("Original-baseline screenshot has a truncated PNG chunk")
        chunk_size = struct.unpack(">I", content[offset : offset + 4])[0]
        chunk_type = content[offset + 4 : offset + 8]
        chunk_end = offset + 12 + chunk_size
        if chunk_end > len(content):
            raise BaselineError("Original-baseline screenshot has a truncated PNG payload")
        payload = content[offset + 8 : offset + 8 + chunk_size]
        expected_crc = struct.unpack(">I", content[offset + 8 + chunk_size : chunk_end])[0]
        if zlib.crc32(chunk_type + payload) & 0xFFFFFFFF != expected_crc:
            raise BaselineError("Original-baseline screenshot failed PNG CRC validation")
        chunk_types.append(chunk_type)
        if len(chunk_types) == 1:
            if chunk_type != b"IHDR" or chunk_size != 13:
                raise BaselineError("Original-baseline screenshot has no canonical PNG IHDR")
            header = struct.unpack(">IIBBBBB", payload)
        elif chunk_type == b"IHDR":
            raise BaselineError("Original-baseline screenshot duplicates its PNG header")
        if chunk_type == b"IDAT":
            compressed.extend(payload)
        if chunk_type == b"IEND":
            if chunk_size != 0 or chunk_end != len(content):
                raise BaselineError("Original-baseline screenshot has an invalid PNG ending")
            break
        offset = chunk_end
    if (
        header is None
        or not compressed
        or not chunk_types
        or chunk_types[-1] != b"IEND"
    ):
        raise BaselineError("Original-baseline screenshot has an incomplete PNG structure")
    width, height, bit_depth, color_type, compression, filtering, interlace = header
    if (
        width <= 0
        or height <= 0
        or width > MAXIMUM_PNG_DIMENSION
        or height > MAXIMUM_PNG_DIMENSION
    ):
        raise BaselineError("Original-baseline screenshot has invalid PNG dimensions")
    channels_by_color_type = {0: 1, 2: 3, 4: 2, 6: 4}
    channels = channels_by_color_type.get(color_type)
    if (
        bit_depth != 8
        or channels is None
        or compression != 0
        or filtering != 0
        or interlace != 0
    ):
        raise BaselineError(
            "Original-baseline screenshot must be an 8-bit non-interlaced PNG"
        )

    stride = width * channels
    expected_size = height * (stride + 1)
    if expected_size > MAXIMUM_DECODED_PNG_SIZE:
        raise BaselineError("Original-baseline screenshot exceeds the decoded-size bound")
    try:
        decompressor = zlib.decompressobj()
        raw = decompressor.decompress(bytes(compressed), expected_size + 1)
        if len(raw) <= expected_size and not decompressor.unconsumed_tail:
            raw += decompressor.flush(expected_size + 1 - len(raw))
    except zlib.error as exception:
        raise BaselineError(
            "Original-baseline screenshot PNG payload cannot be decompressed"
        ) from exception
    if (
        len(raw) != expected_size
        or decompressor.unconsumed_tail
        or decompressor.unused_data
        or not decompressor.eof
    ):
        raise BaselineError(
            "Original-baseline screenshot has an unexpected decoded payload size"
        )

    previous = bytearray(stride)
    rgb = bytearray(width * height * 3)
    raw_offset = 0
    rgb_offset = 0
    for _row_index in range(height):
        filter_type = raw[raw_offset]
        raw_offset += 1
        filtered = raw[raw_offset : raw_offset + stride]
        raw_offset += stride
        reconstructed = bytearray(stride)
        for index, value in enumerate(filtered):
            left = reconstructed[index - channels] if index >= channels else 0
            above = previous[index]
            upper_left = previous[index - channels] if index >= channels else 0
            if filter_type == 0:
                predictor = 0
            elif filter_type == 1:
                predictor = left
            elif filter_type == 2:
                predictor = above
            elif filter_type == 3:
                predictor = (left + above) // 2
            elif filter_type == 4:
                predictor = paeth_predictor(left, above, upper_left)
            else:
                raise BaselineError(
                    f"Original-baseline screenshot uses unknown PNG filter {filter_type}"
                )
            reconstructed[index] = (value + predictor) & 0xFF

        for pixel_offset in range(0, stride, channels):
            if color_type == 0:
                red = green = blue = reconstructed[pixel_offset]
                alpha = 255
            elif color_type == 2:
                red, green, blue = reconstructed[pixel_offset : pixel_offset + 3]
                alpha = 255
            elif color_type == 4:
                red = green = blue = reconstructed[pixel_offset]
                alpha = reconstructed[pixel_offset + 1]
            else:
                red, green, blue, alpha = reconstructed[pixel_offset : pixel_offset + 4]
            if alpha == 0:
                red = green = blue = 0
            rgb[rgb_offset : rgb_offset + 3] = bytes((red, green, blue))
            rgb_offset += 3
        previous = reconstructed
    return PngImage(width=width, height=height, pixels=bytes(rgb))


def assert_image_is_not_blank(image: PngImage) -> None:
    pixel_count = image.width * image.height
    sample_interval = max(1, pixel_count // 100_000)
    unique_colors: set[bytes] = set()
    minimum = [255, 255, 255]
    maximum = [0, 0, 0]
    for pixel_index in range(0, pixel_count, sample_interval):
        offset = pixel_index * 3
        color = image.pixels[offset : offset + 3]
        unique_colors.add(color)
        for channel in range(3):
            minimum[channel] = min(minimum[channel], color[channel])
            maximum[channel] = max(maximum[channel], color[channel])
        if len(unique_colors) >= 32 and any(
            maximum[channel] - minimum[channel] >= 16 for channel in range(3)
        ):
            return
    raise BaselineError("Original-baseline screenshot is blank or near-uniform")


def png_dimensions(path: Path) -> tuple[int, int]:
    image = decode_png(path)
    return image.width, image.height


def verify_phase_zero_assertion_semantics(
    configuration: Configuration,
    root: Path,
    assertion: dict[str, object],
) -> None:
    name = str(assertion["name"])
    expected = str(assertion["expected"])
    actual = str(assertion["actual"])
    if (
        not expected
        or not actual
        or len(expected) > 4096
        or len(actual) > 4096
        or expected.casefold() == "expected"
        or actual.casefold() == "actual"
        or any(value in expected or value in actual for value in ("\x00", "\r", "\n"))
    ):
        raise BaselineError(
            f"Original-baseline assertion {name} has placeholder or unsafe evidence"
        )
    if name == "fabric_mod_loaded:etherology" and (expected, actual) != (
        "loaded",
        "loaded",
    ):
        raise BaselineError("Etherology loaded assertion is not exact")
    if name == "published_resources_loaded" and (
        expected
        != "[minecraft:texts/splashes.txt, etherology:models/item/oculus.json]"
        or actual != "present"
    ):
        raise BaselineError("Published-resource assertion is not exact")
    if name == "registry_preflight" and (
        expected != "all phase0 registry entries present" or actual != "present"
    ):
        raise BaselineError("Registry-preflight assertion is not exact")
    if name.startswith("registry:") and (expected, actual) != ("present", "present"):
        raise BaselineError(f"Registry assertion {name} is not exact")
    if name == "etherology_block_states_have_network_ids" and (
        expected != "every Etherology block state has a non-negative raw id"
        or re.fullmatch(r"[1-9][0-9]* inspected; missing=\[\]", actual) is None
    ):
        raise BaselineError("Block-state registry assertion is not meaningful")
    if name.startswith("packaged_root_jar:") and (expected, actual) != (
        "one regular root JAR",
        "one regular root JAR",
    ):
        raise BaselineError(f"Packaged-artifact assertion {name} is not exact")
    if name == "native_framebuffer_dimensions" and (
        expected != "1920x1080" or actual != expected
    ):
        raise BaselineError("Framebuffer-dimension assertion is not exact")
    if name == "completed_world_renders_before_capture":
        if expected != "120" or not actual.isdecimal() or int(actual) < int(expected):
            raise BaselineError("Completed-render assertion is not meaningful")
    if name == "capture_render_ready" and (expected, actual) != (
        "terrain complete and all four fixture positions rendering-ready",
        "ready",
    ):
        raise BaselineError("Capture render-readiness assertion is not exact")
    if name == "capture_camera_exact":
        expected_camera = (
            "first_person=true;x=0.5;y=121.0;z=-7.5;yaw=0.0;pitch=8.0;"
            "on_ground=true;tolerance=1.0E-4"
        )
        number = r"[-+]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[Ee][-+]?[0-9]+)?"
        actual_match = re.fullmatch(
            rf"first_person=true;x=({number});y=({number});z=({number});"
            rf"yaw=({number});pitch=({number});on_ground=true",
            actual,
        )
        if expected != expected_camera or actual_match is None:
            raise BaselineError("Capture camera-pose assertion is not exact")
        actual_values = tuple(float(value) for value in actual_match.groups())
        expected_values = (0.5, 121.0, -7.5, 0.0, 8.0)
        if any(
            abs(actual_value - expected_value) > 0.0001
            for actual_value, expected_value in zip(actual_values, expected_values)
        ):
            raise BaselineError("Capture camera pose exceeds its pinned tolerance")
    if name == "native_screenshot_written":
        screenshot = screenshot_path(configuration, root)
        if screenshot.is_symlink() or not screenshot.is_file():
            raise BaselineError("Native-screenshot assertion has no regular PNG")
        exact_actual = (
            f"{screenshot.stat().st_size} bytes, sha256={sha256_file(screenshot)}"
        )
        if (
            expected != "one non-empty unedited framebuffer PNG"
            or actual != exact_actual
        ):
            raise BaselineError("Native-screenshot assertion is not exact")
    exact_actuals = {
        "integrated_world_joined": ("running server and connected client", "joined"),
        "client_world_mirrors_server_fixture": (
            "all four blocks and exact block entity types mirrored",
            "mirrored",
        ),
        "server_arena_chunk_loaded": ("full chunk", "true"),
        "server_player_creative": ("creative", "true"),
        "server_fixture_block_entities_present": ("four block entities", "true"),
        "forced_world_save": ("true", "true"),
        "isolated_save_directory_present": (
            str(scenario_spec(configuration)["world_directory_name"]),
            str(scenario_spec(configuration)["world_directory_name"]),
        ),
    }
    if name in exact_actuals and (expected, actual) != exact_actuals[name]:
        raise BaselineError(f"Scenario assertion {name} is not exact")
    if name == "server_fixture_blocks_placed":
        exact_block_ids = (
            "[-3, 121, 2=etherology:brewing_cauldron, "
            "0, 121, 2=etherology:empowerment_table, "
            "3, 121, 2=etherology:ethereal_storage, "
            "0, 121, 5=etherology:armillary_sphere]"
        )
        if (expected, actual) != ("all expected identifiers", exact_block_ids):
            raise BaselineError("Server fixture-block assertion is not exact")
    if name in {
        "client_fixture_block_entity_types_exact",
        "server_fixture_block_entity_types_exact",
    }:
        required_type_ids = (
            "etherology:brewing_cauldron_block_entity",
            "etherology:empowerment_table_block_entity",
            "etherology:ethereal_storage_block_entity",
            "etherology:armillary_sphere_block_entity",
        )
        expected_ids = "[" + ", ".join(required_type_ids) + "]"
        exact_actual = (
            "[-3, 121, 2=etherology:brewing_cauldron_block_entity, "
            "0, 121, 2=etherology:empowerment_table_block_entity, "
            "3, 121, 2=etherology:ethereal_storage_block_entity, "
            "0, 121, 5=etherology:armillary_sphere_block_entity]"
        )
        if (expected, actual) != (expected_ids, exact_actual):
            raise BaselineError(f"Block-entity type assertion {name} is not exact")
    if name == "live_world_identity":
        identity = (
            "Etherology Original 0.1.7 Phase 0;"
            "19514442935972151;minecraft:overworld"
        )
        if (expected, actual) != (identity, identity):
            raise BaselineError("Live-world identity assertion is not exact")


def forest_lantern_state_descriptions() -> list[str]:
    descriptions: list[str] = []
    for facing, x_coordinate in zip(
        FOREST_LANTERN_FACINGS, FOREST_LANTERN_X_COORDINATES
    ):
        for age in range(5):
            descriptions.append(
                f"{x_coordinate}, 121, {age * 3}=age={age},facing={facing}"
            )
    return descriptions


def verify_forest_lantern_assertion_semantics(
    configuration: Configuration,
    root: Path,
    assertion: dict[str, object],
) -> None:
    name = str(assertion["name"])
    expected = str(assertion["expected"])
    actual = str(assertion["actual"])
    if (
        not expected
        or not actual
        or len(expected) > 4096
        or len(actual) > 4096
        or expected.casefold() == "expected"
        or actual.casefold() == "actual"
        or any(value in expected or value in actual for value in ("\x00", "\r", "\n"))
    ):
        raise BaselineError(
            f"Original-baseline assertion {name} has placeholder or unsafe evidence"
        )

    exact_values = {
        "fabric_mod_loaded:etherology": ("loaded", "loaded"),
        "forest_lantern_resources_exact": (
            "[" + ", ".join(FOREST_LANTERN_RESOURCES) + "]",
            "[" + ", ".join(FOREST_LANTERN_RESOURCES) + "]",
        ),
        "registry:block:etherology:forest_lantern": ("present", "present"),
        "registry:item:etherology:forest_lantern": ("present", "present"),
        "registry:item:etherology:forest_lantern_crumb": ("present", "present"),
        "forest_lantern_properties_exact": ("[age, facing]", "[age, facing]"),
        "forest_lantern_default_state_exact": (
            "age=4,facing=north",
            "age=4,facing=north",
        ),
        "forest_lantern_state_count_exact": ("20", "20"),
        "forest_lantern_state_network_ids_exact": (
            "20 unique non-negative raw ids",
            "20 unique non-negative raw ids",
        ),
        "native_framebuffer_dimensions": ("1920x1080", "1920x1080"),
        "capture_render_ready": (
            "terrain complete and all 20 Forest Lantern positions rendering-ready",
            "ready",
        ),
        "integrated_world_joined": (
            "running server and connected client",
            "joined",
        ),
        "server_arena_chunk_loaded": ("full chunk", "true"),
        "server_player_creative": ("creative", "true"),
        "client_forest_lantern_states_exact": (
            "all 20 exact age/facing states mirrored",
            "mirrored",
        ),
        "forest_lantern_immature_loot_empty": (
            "ages 0..3=[]",
            "[0=[], 1=[], 2=[], 3=[]]",
        ),
        "forest_lantern_mature_loot_exact": (
            "age 4=[etherology:forest_lanternx1]",
            "4=[etherology:forest_lanternx1]",
        ),
        "forest_lantern_jump_seed_exact": (
            "first vanilla world-random roll <= 0.4",
            "seed=4096,roll=0.09789288",
        ),
        "forest_lantern_jump_stepping_position_exact": (
            "player stepping position contains mature Forest Lantern",
            "14, 120, -12",
        ),
        "forest_lantern_jump_break_exact": (
            "mature Forest Lantern removed by one seeded vanilla jump",
            "removed",
        ),
        "forest_lantern_jump_drop_exact": (
            "[etherology:forest_lanternx1]",
            "[etherology:forest_lanternx1]",
        ),
        "forced_world_save": ("true", "true"),
        "isolated_save_directory_present": (
            str(scenario_spec(configuration)["world_directory_name"]),
            str(scenario_spec(configuration)["world_directory_name"]),
        ),
    }
    if name in exact_values:
        if (expected, actual) != exact_values[name]:
            raise BaselineError(f"Scenario assertion {name} is not exact")
        return

    if name.startswith("packaged_root_jar:"):
        if (expected, actual) != (
            "one regular root JAR",
            "one regular root JAR",
        ):
            raise BaselineError(f"Packaged-artifact assertion {name} is not exact")
        return

    if name == "completed_world_renders_before_capture":
        if expected != "120" or not actual.isdecimal() or int(actual) < 120:
            raise BaselineError("Completed-render assertion is not meaningful")
        return

    if name == "capture_camera_exact":
        expected_camera = (
            "first_person=true;x=0.5;y=128.0;z=-17.5;yaw=0.0;pitch=23.0;"
            "on_ground=true;tolerance=1.0E-4"
        )
        number = r"[-+]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[Ee][-+]?[0-9]+)?"
        actual_match = re.fullmatch(
            rf"first_person=true;x=({number});y=({number});z=({number});"
            rf"yaw=({number});pitch=({number});on_ground=true",
            actual,
        )
        if expected != expected_camera or actual_match is None:
            raise BaselineError("Capture camera-pose assertion is not exact")
        actual_values = tuple(float(value) for value in actual_match.groups())
        expected_values = (0.5, 128.0, -17.5, 0.0, 23.0)
        if any(
            abs(actual_value - expected_value) > 0.0001
            for actual_value, expected_value in zip(actual_values, expected_values)
        ):
            raise BaselineError("Capture camera pose exceeds its pinned tolerance")
        return

    if name == "native_screenshot_written":
        screenshot = screenshot_path(configuration, root)
        if screenshot.is_symlink() or not screenshot.is_file():
            raise BaselineError("Native-screenshot assertion has no regular PNG")
        exact_actual = (
            f"{screenshot.stat().st_size} bytes, sha256={sha256_file(screenshot)}"
        )
        if (
            expected != "one non-empty unedited framebuffer PNG"
            or actual != exact_actual
        ):
            raise BaselineError("Native-screenshot assertion is not exact")
        return

    state_descriptions = forest_lantern_state_descriptions()
    if name == "server_forest_lantern_states_exact":
        exact_states = "[" + ", ".join(state_descriptions) + "]"
        if (expected, actual) != (exact_states, exact_states):
            raise BaselineError("Server Forest Lantern state fixture is not exact")
        return

    if name == "server_forest_lantern_state_network_ids_exact":
        if expected != "20 placed states with non-negative raw ids":
            raise BaselineError("Placed-state network-id expectation changed")
        pattern = r"\[" + ", ".join(
            re.escape(description) + r"#[0-9]+" for description in state_descriptions
        ) + r"\]"
        if re.fullmatch(pattern, actual) is None:
            raise BaselineError("Placed-state network-id evidence is not exact")
        raw_ids = [int(value) for value in re.findall(r"#([0-9]+)", actual)]
        if len(raw_ids) != 20 or len(set(raw_ids)) != 20:
            raise BaselineError("Placed-state network ids are not unique")
        return

    if name == "forest_lantern_shears_speed_exact":
        exact_speeds = "[" + ", ".join(
            description + "=15.0" for description in state_descriptions
        ) + "]"
        if (expected, actual) != ("15.0 for all 20 states", exact_speeds):
            raise BaselineError("Forest Lantern shears-speed evidence is not exact")
        return

    if name in FOREST_LANTERN_RECIPE_RESULTS:
        exact_recipe = FOREST_LANTERN_RECIPE_RESULTS[name]
        if (expected, actual) != (exact_recipe, exact_recipe):
            raise BaselineError(f"Forest Lantern recipe assertion {name} is not exact")
        return

    if name == "live_world_identity":
        identity = (
            "Etherology Original 0.1.7 Forest Lantern;"
            "4995697353423860023;minecraft:overworld"
        )
        if (expected, actual) != (identity, identity):
            raise BaselineError("Live-world identity assertion is not exact")
        return

    raise BaselineError(f"Original-baseline assertion {name} has no exact semantics")


def verify_assertion_semantics(
    configuration: Configuration,
    root: Path,
    assertion: dict[str, object],
) -> None:
    scenario_id = str(scenario_spec(configuration)["id"])
    if scenario_id == "phase0-smoke":
        verify_phase_zero_assertion_semantics(configuration, root, assertion)
        return
    if scenario_id == "forest-lantern":
        verify_forest_lantern_assertion_semantics(configuration, root, assertion)
        return
    raise BaselineError(f"No assertion contract exists for scenario {scenario_id}")


def verify_scenario_evidence(
    configuration: Configuration, root: Path
) -> dict[str, object]:
    verify_capture_layout(configuration, root, require_fresh=False)
    attempt = verify_launch_attempt(configuration, root)
    scenario = scenario_spec(configuration)
    reports = reports_directory(configuration, root)
    screenshots = screenshots_directory(configuration, root)
    expected_report_inventory = {
        str(scenario["report_file"]),
        str(scenario["completion_marker_file"]),
    }
    expected_screenshot_inventory = {str(scenario["screenshot_file"])}
    actual_report_inventory = {path.name for path in reports.iterdir()}
    actual_screenshot_inventory = {path.name for path in screenshots.iterdir()}
    if actual_report_inventory != expected_report_inventory:
        raise BaselineError(
            "Original-baseline report inventory is incomplete or contaminated: "
            f"missing={sorted(expected_report_inventory - actual_report_inventory)}, "
            f"extra={sorted(actual_report_inventory - expected_report_inventory)}"
        )
    if actual_screenshot_inventory != expected_screenshot_inventory:
        raise BaselineError(
            "Original-baseline screenshot inventory is incomplete or contaminated: "
            f"missing={sorted(expected_screenshot_inventory - actual_screenshot_inventory)}, "
            f"extra={sorted(actual_screenshot_inventory - expected_screenshot_inventory)}"
        )

    report = load_json_object(
        report_path(configuration, root), "Original-baseline scenario report"
    )
    forest_lantern_scenario = scenario["id"] == "forest-lantern"
    expected_report_fields = {
        "schema",
        "reference_id",
        "scenario",
        "lane",
        "status",
        "client_ticks",
        "lifecycle_failure",
        "assertions",
        "world",
        "artifacts",
        "screenshots",
    }
    if forest_lantern_scenario:
        expected_report_fields.add("mechanics")
    require_exact_fields(
        report,
        expected_report_fields,
        "The original-baseline scenario report",
    )
    if (
        report.get("schema") != (2 if forest_lantern_scenario else 1)
        or report.get("reference_id") != "published-0.1.7"
        or report.get("scenario") != scenario["id"]
        or report.get("lane") != "fabric-1.21.1-original"
        or report.get("status") != "passed"
        or report.get("lifecycle_failure") != ""
        or type(report.get("client_ticks")) is not int
        or int(report["client_ticks"]) <= 0
    ):
        raise BaselineError("Original-baseline scenario report did not pass exactly")

    assertions = report.get("assertions")
    if not isinstance(assertions, list) or not assertions:
        raise BaselineError("Original-baseline scenario report has no assertions")
    assertion_names: list[str] = []
    for assertion in assertions:
        if (
            not isinstance(assertion, dict)
            or set(assertion) != {"name", "passed", "expected", "actual"}
            or not isinstance(assertion.get("name"), str)
            or assertion.get("passed") is not True
            or not isinstance(assertion.get("expected"), str)
            or not isinstance(assertion.get("actual"), str)
            or assertion["name"] in assertion_names
        ):
            raise BaselineError(
                "Original-baseline scenario report contains an invalid assertion"
            )
        assertion_names.append(str(assertion["name"]))
        verify_assertion_semantics(configuration, root, assertion)
    expected_assertion_names = (
        EXPECTED_ASSERTION_NAMES
        if forest_lantern_scenario
        else PHASE_ZERO_EXPECTED_ASSERTION_NAMES
    )
    if tuple(assertion_names) != expected_assertion_names:
        raise BaselineError(
            "Original-baseline scenario report assertion order/inventory changed: "
            f"expected={list(expected_assertion_names)}, actual={assertion_names}"
        )

    if forest_lantern_scenario and report.get("mechanics") != {
        "fixture_state_count": 20,
        "ages": "0,1,2,3,4",
        "facings": "north,east,south,west",
        "jump_probe": "seeded vanilla PlayerEntity.jump invoker",
        "limitations": [],
    }:
        raise BaselineError("Original-baseline Forest Lantern mechanics record changed")

    world = report.get("world")
    if world != {
        "save_directory": scenario["world_directory_name"],
        "display_name": scenario["world_display_name"],
        "seed": scenario["world_seed"],
        "dimension": "minecraft:overworld",
        "integrated": True,
    }:
        raise BaselineError("Original-baseline report describes another world")
    world_path = save_directory(configuration, root)
    ensure_no_symlink_components(root, world_path, "Original-baseline saved world")
    if world_path.is_symlink() or not world_path.is_dir():
        raise BaselineError(
            f"Original-baseline saved world is missing or linked: {world_path}"
        )
    required_save_files = (world_path / "level.dat", world_path / "session.lock")
    for required_save_file in required_save_files:
        ensure_no_symlink_components(
            world_path, required_save_file, "Original-baseline world proof"
        )
        if (
            required_save_file.is_symlink()
            or not required_save_file.is_file()
            or required_save_file.stat().st_size <= 0
        ):
            raise BaselineError(
                "Original-baseline saved world lacks a non-empty regular proof file: "
                f"{required_save_file}"
            )
    region_directory = world_path / "region"
    ensure_no_symlink_components(
        world_path, region_directory, "Original-baseline region directory"
    )
    if region_directory.is_symlink() or not region_directory.is_dir():
        raise BaselineError(
            f"Original-baseline region directory is missing or linked: {region_directory}"
        )
    region_files = list(region_directory.iterdir())
    if not region_files:
        raise BaselineError("Original-baseline saved world contains no region file")
    for region_file in region_files:
        if (
            region_file.is_symlink()
            or not region_file.is_file()
            or re.fullmatch(r"r\.-?[0-9]+\.-?[0-9]+\.mca", region_file.name) is None
            or region_file.stat().st_size <= 0
        ):
            raise BaselineError(
                f"Original-baseline region inventory is invalid: {region_file}"
            )
    required_region_file = region_directory / "r.0.0.mca"
    if (
        required_region_file.is_symlink()
        or not required_region_file.is_file()
        or required_region_file.stat().st_size <= 0
    ):
        raise BaselineError(
            "Original-baseline saved world lacks exact region/r.0.0.mca proof"
        )
    world_file_count = 0
    world_total_size = 0
    for path in world_path.rglob("*"):
        ensure_no_symlink_components(world_path, path, "Saved-world inventory")
        if path.is_symlink():
            raise BaselineError(f"Saved-world inventory contains a symlink: {path}")
        if path.is_dir():
            continue
        if not path.is_file():
            raise BaselineError(f"Saved-world inventory contains a non-file: {path}")
        world_file_count += 1
        world_total_size += path.stat().st_size
        if world_file_count > 8192 or world_total_size > 2 * 1024 * 1024 * 1024:
            raise BaselineError("Saved-world inventory exceeds its evidence bounds")

    etherology = next(
        member for member in member_specs(configuration) if member["mod_id"] == "etherology"
    )
    harness = harness_spec(configuration)
    expected_artifacts = {
        "etherology": {
            "mod_id": "etherology",
            "origin_kind": "PATH",
            "file_name": etherology["file_name"],
            "size": etherology["size"],
            "sha256": etherology["sha256"],
        },
        str(harness["mod_id"]): {
            "mod_id": harness["mod_id"],
            "origin_kind": "PATH",
            "file_name": harness["file_name"],
            "size": harness["size"],
            "sha256": harness["sha256"],
        },
    }
    artifacts = report.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) != 2:
        raise BaselineError("Original-baseline report has another artifact inventory")
    actual_artifacts: dict[str, object] = {}
    for artifact in artifacts:
        if not isinstance(artifact, dict) or not isinstance(artifact.get("mod_id"), str):
            raise BaselineError("Original-baseline report has an invalid artifact")
        mod_id = str(artifact["mod_id"])
        if mod_id in actual_artifacts:
            raise BaselineError("Original-baseline report duplicates an artifact")
        actual_artifacts[mod_id] = artifact
    if actual_artifacts != expected_artifacts:
        raise BaselineError("Original-baseline artifact digests do not match their pins")

    screenshot_file = screenshot_path(configuration, root)
    framebuffer = require_object(scenario, "framebuffer")
    decoded_screenshot = decode_png(screenshot_file)
    assert_image_is_not_blank(decoded_screenshot)
    width, height = decoded_screenshot.width, decoded_screenshot.height
    if (width, height) != (framebuffer["width"], framebuffer["height"]):
        raise BaselineError(
            f"Original-baseline screenshot is {width}x{height}, expected "
            f"{framebuffer['width']}x{framebuffer['height']}"
        )
    screenshot_size = screenshot_file.stat().st_size
    screenshot_sha256 = sha256_file(screenshot_file)
    screenshots_node = report.get("screenshots")
    expected_screenshot = {
        "step": (
            "forest-lantern-age-facing-gallery"
            if forest_lantern_scenario
            else "integrated-world-fixture"
        ),
        "file": f"screenshots/{scenario['screenshot_file']}",
        "width": framebuffer["width"],
        "height": framebuffer["height"],
        "size": screenshot_size,
        "sha256": screenshot_sha256,
        "source": "minecraft-framebuffer",
        "edited": False,
    }
    if not isinstance(screenshots_node, list) or len(screenshots_node) != 1:
        raise BaselineError("Original-baseline report must contain one screenshot")
    screenshot_node = screenshots_node[0]
    if not isinstance(screenshot_node, dict):
        raise BaselineError("Original-baseline screenshot report is invalid")
    completed_render_count = screenshot_node.get("completed_render_count")
    without_render_count = dict(screenshot_node)
    without_render_count.pop("completed_render_count", None)
    if (
        type(completed_render_count) is not int
        or int(completed_render_count) < 120
        or without_render_count != expected_screenshot
    ):
        raise BaselineError(
            "Original-baseline screenshot report does not match the native PNG"
        )

    marker = completion_marker_path(configuration, root)
    if marker.is_symlink() or not marker.is_file():
        raise BaselineError(
            f"Original-baseline completion marker is missing or linked: {marker}"
        )
    try:
        marker_content = marker.read_text(encoding="utf-8")
    except OSError as exception:
        raise BaselineError(
            f"Cannot read original-baseline completion marker: {marker}"
        ) from exception
    expected_marker_content = (
        f"{scenario['id']}:passed\n"
        f"report_sha256:{sha256_file(report_path(configuration, root))}\n"
    )
    if marker_content != expected_marker_content:
        raise BaselineError("Original-baseline completion marker did not report passed")

    attempt_file = launch_attempt_path(configuration, root)
    report_file = report_path(configuration, root)
    publication_times = (
        attempt_file.stat().st_mtime_ns,
        screenshot_file.stat().st_mtime_ns,
        report_file.stat().st_mtime_ns,
        marker.stat().st_mtime_ns,
    )
    if not (
        publication_times[0]
        < publication_times[1]
        < publication_times[2]
        < publication_times[3]
    ):
        raise BaselineError(
            "Original-baseline evidence was not published in seal -> screenshot -> "
            "report -> completion-marker order"
        )
    if attempt.get("created_at_unix_ns") > publication_times[0]:
        raise BaselineError("Launch-attempt timestamp is newer than its durable seal")
    return verify_game_lifecycle(configuration, root)


def find_fatal_log_marker(content: str) -> str | None:
    return next((value for value in FATAL_CLIENT_LOG_MARKERS if value in content), None)


def verify_runtime_native_extraction(
    configuration: Configuration, root: Path
) -> None:
    temporary_root = temporary_directory(configuration, root)
    extraction_root = native_extraction_directory(configuration, root)
    ensure_no_symlink_components(
        temporary_root, extraction_root, "Runtime native extraction directory"
    )
    if extraction_root.is_symlink() or not extraction_root.is_dir():
        raise BaselineError(
            f"Runtime native extraction directory is missing or linked: {extraction_root}"
        )
    file_count = 0
    total_size = 0
    for path in extraction_root.rglob("*"):
        ensure_no_symlink_components(
            extraction_root, path, "Runtime native extraction entry"
        )
        if path.is_symlink():
            raise BaselineError(
                f"Runtime native extraction contains a symlink: {path}"
            )
        if path.is_dir():
            continue
        if not path.is_file() or path.stat().st_size <= 0:
            raise BaselineError(
                f"Runtime native extraction contains an invalid file: {path}"
            )
        file_count += 1
        total_size += path.stat().st_size
        if file_count > 512 or total_size > 512 * 1024 * 1024:
            raise BaselineError("Runtime native extraction exceeds its bounds")
    if file_count == 0:
        raise BaselineError("Runtime native extraction contains no native files")


def verify_game_lifecycle(
    configuration: Configuration, root: Path
) -> dict[str, object]:
    verify_runtime_native_extraction(configuration, root)
    skin_cache = runtime_skin_cache_descriptor(configuration, root)
    game = game_directory(configuration, root)
    crash_reports = game / "crash-reports"
    if crash_reports.is_symlink() or not crash_reports.is_dir():
        raise BaselineError(
            f"Original-baseline crash-report directory is missing or linked: {crash_reports}"
        )
    crash_inventory = list(crash_reports.iterdir())
    if crash_inventory:
        raise BaselineError(
            f"Original-baseline runtime contains crash reports: {crash_inventory}"
        )
    latest_log = game / "logs" / "latest.log"
    if (
        latest_log.is_symlink()
        or not latest_log.is_file()
        or latest_log.stat().st_size <= 0
    ):
        raise BaselineError(f"Original-baseline game log is missing or linked: {latest_log}")
    if latest_log.stat().st_size > MAXIMUM_PROCESS_LOG_SIZE:
        raise BaselineError("Original-baseline game log exceeds the size bound")
    content = latest_log.read_text(encoding="utf-8", errors="replace")
    fatal_marker = find_fatal_log_marker(content)
    if fatal_marker is not None:
        raise BaselineError(
            f"Original-baseline game log contains fatal marker: {fatal_marker}"
        )
    if "Stopping!" not in content:
        raise BaselineError("Original-baseline client did not record a normal shutdown")
    success_marker = (
        "Original forest-lantern evidence published with status passed:"
        if scenario_spec(configuration)["id"] == "forest-lantern"
        else "Original phase0-smoke evidence published with status passed:"
    )
    if content.count(success_marker) != 1:
        raise BaselineError(
            "Original-baseline game log lacks one exact evidence-publication marker"
        )
    if latest_log.stat().st_mtime_ns <= launch_attempt_path(
        configuration, root
    ).stat().st_mtime_ns:
        raise BaselineError("Original-baseline game log predates the launch-attempt seal")
    return skin_cache


def file_descriptor(path: Path, root: Path) -> dict[str, object]:
    ensure_no_symlink_components(root, path, "Successful-run evidence file")
    if path.is_symlink() or not path.is_file() or path.stat().st_size <= 0:
        raise BaselineError(f"Successful-run evidence file is missing or linked: {path}")
    return {
        "path": path.relative_to(root).as_posix(),
        "size": path.stat().st_size,
        "sha256": sha256_file(path),
    }


def write_successful_run_verification(
    configuration: Configuration,
    root: Path,
    controller_log: Path,
    skin_cache: dict[str, object],
) -> Path:
    logs_root = logs_directory(configuration, root)
    ensure_no_symlink_components(logs_root, controller_log, "Controller process log")
    if (
        controller_log.is_symlink()
        or not controller_log.is_file()
        or controller_log.stat().st_size <= 0
    ):
        raise BaselineError(f"Controller process log is missing or linked: {controller_log}")
    verification_path = controller_log.with_name(
        f"{controller_log.stem}-verification.json"
    )
    attempt = launch_attempt_path(configuration, root)
    report = report_path(configuration, root)
    marker = completion_marker_path(configuration, root)
    screenshot = screenshot_path(configuration, root)
    descriptor = {
        "schema": 1,
        "status": "passed",
        "profile_id": profile_spec(configuration)["id"],
        "scenario": scenario_spec(configuration)["id"],
        "verified_at_unix_ns": time.time_ns(),
        "launch_attempt": file_descriptor(attempt, root),
        "scenario_report": file_descriptor(report, root),
        "completion_marker": file_descriptor(marker, root),
        "screenshot": file_descriptor(screenshot, root),
        "controller_log": file_descriptor(controller_log, root),
        "immutable_launcher_files_sha256": canonical_json_sha256(
            launcher_file_inventory(configuration, root)
        ),
        "mutable_launcher_outputs": {"skin_cache": skin_cache},
    }
    write_json_exclusive(verification_path, descriptor)
    if load_json_object(
        verification_path, "Original-baseline successful-run verification"
    ) != descriptor:
        raise BaselineError("Successful-run verification record changed while publishing")
    return verification_path


def process_group_exists(process_group_id: int) -> bool:
    try:
        os.killpg(process_group_id, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def stop_owned_process_group(process: subprocess.Popen[bytes]) -> None:
    process_group_id = process.pid
    try:
        actual_group_id = os.getpgid(process.pid)
    except ProcessLookupError:
        actual_group_id = process_group_id
    if process.poll() is None and actual_group_id != process_group_id:
        raise BaselineError(
            "Owned process is no longer the leader of its dedicated process group"
        )
    if process_group_exists(process_group_id):
        try:
            os.killpg(process_group_id, signal.SIGTERM)
        except ProcessLookupError:
            pass
    deadline = time.monotonic() + PROCESS_STOP_TIMEOUT_SECONDS
    while process_group_exists(process_group_id) and time.monotonic() < deadline:
        process.poll()
        time.sleep(0.25)
    if process_group_exists(process_group_id):
        try:
            os.killpg(process_group_id, signal.SIGKILL)
        except ProcessLookupError:
            pass
    kill_deadline = time.monotonic() + PROCESS_STOP_TIMEOUT_SECONDS
    while process_group_exists(process_group_id) and time.monotonic() < kill_deadline:
        process.poll()
        time.sleep(0.25)
    try:
        process.wait(timeout=1)
    except subprocess.TimeoutExpired:
        pass
    if process_group_exists(process_group_id):
        raise BaselineError(
            f"Owned process group {process_group_id} survived TERM and KILL"
        )


def stream_owned_process_output(
    process: subprocess.Popen[bytes],
    log_handle: BinaryIO,
    initial_size: int,
    timeout_seconds: int,
) -> int:
    if process.stdout is None:
        raise BaselineError("Owned client has no controller-managed output pipe")
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    total_size = initial_size
    deadline = time.monotonic() + timeout_seconds
    try:
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise BaselineError(
                    f"Original-baseline client exceeded its {timeout_seconds}-second timeout"
                )
            for key, _events in selector.select(timeout=min(remaining, 0.5)):
                chunk = os.read(key.fileobj.fileno(), 64 * 1024)
                if not chunk:
                    selector.unregister(key.fileobj)
                    continue
                if total_size + len(chunk) > MAXIMUM_PROCESS_LOG_SIZE:
                    raise BaselineError(
                        "Original-baseline client output exceeded the live log-size bound"
                    )
                log_handle.write(chunk)
                total_size += len(chunk)
            return_code = process.poll()
            if return_code is not None and not selector.get_map():
                return return_code
    finally:
        selector.close()


def run_owned_client(
    configuration: Configuration,
    scenario_id: str,
    java_path: Path,
    command: list[str],
) -> int:
    root = verify_owned_runtime(configuration)
    with acquire_runtime_lifecycle_lock(configuration, root):
        return _run_owned_client_locked(
            configuration,
            scenario_id,
            java_path,
            command,
        )


def _run_owned_client_locked(
    configuration: Configuration,
    scenario_id: str,
    java_path: Path,
    command: list[str],
) -> int:
    if not CAFFEINATE_PATH.is_file() or CAFFEINATE_PATH.is_symlink():
        raise BaselineError(f"macOS caffeinate is missing or linked: {CAFFEINATE_PATH}")
    root = verify_owned_runtime(configuration)
    verify_installed_game(configuration, root)
    verify_runtime_lock(configuration, root)
    verify_staged_reference(configuration, root)
    verify_capture_layout(configuration, root, require_fresh=True)
    assert_runtime_not_running(configuration, root)
    attempt = launch_attempt_descriptor(
        configuration,
        root,
        scenario_id,
        java_path,
        command,
    )
    try:
        write_json_exclusive(launch_attempt_path(configuration, root), attempt)
    except FileExistsError as exception:
        raise BaselineError(
            "This original-baseline profile has already consumed its one launch attempt"
        ) from exception
    verify_launch_attempt(configuration, root, attempt)

    timestamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    log_path = logs_directory(configuration, root) / f"original-client-{timestamp}.log"
    descriptor = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    log_handle = os.fdopen(descriptor, "wb", buffering=0)
    header = (
        "Etherology repository-owned original Fabric 1.21.1 client\n"
        f"profile_id={profile_spec(configuration)['id']}\n"
        f"scenario={scenario_id}\n"
        f"game_directory={game_directory(configuration, root)}\n"
        f"java={java_path}\n"
        "authentication=deterministic-offline-identity\n\n"
    ).encode("utf-8")
    log_handle.write(header)
    process: subprocess.Popen[bytes] | None = None
    state_written = False
    cleanup_complete = False
    with controlled_termination_signals():
        try:
            with blocked_termination_signals():
                process = subprocess.Popen(
                    [str(CAFFEINATE_PATH), "-dimsu", *command],
                    cwd=game_directory(configuration, root),
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    start_new_session=True,
                    close_fds=True,
                    env=controlled_launch_environment(configuration, root, java_path),
                    preexec_fn=reset_owned_child_signal_state,
                )
            if os.getpgid(process.pid) != process.pid:
                raise BaselineError(
                    "Owned client did not start in its dedicated process group"
                )
            state = {
                "schema": 2,
                "profile_id": profile_spec(configuration)["id"],
                "pid": process.pid,
                "process_group_id": process.pid,
                "version_id": version_id(configuration),
                "game_directory": str(game_directory(configuration, root)),
                "scenario": scenario_id,
                "log": str(log_path),
                "launch_attempt_sha256": sha256_file(
                    launch_attempt_path(configuration, root)
                ),
            }
            write_json_exclusive(process_state_path(configuration, root), state)
            state_written = True
            timeout_seconds = int(
                require_object(configuration.manifest, "launch")["timeout_seconds"]
            )
            return_code = stream_owned_process_output(
                process,
                log_handle,
                len(header),
                timeout_seconds,
            )
            if process_group_exists(process.pid):
                stop_owned_process_group(process)
                raise BaselineError(
                    "Original-baseline launcher exited while an owned child survived"
                )
        except BaseException:
            with blocked_termination_signals():
                if process is not None and process_group_exists(process.pid):
                    stop_owned_process_group(process)
                if process is not None and process.stdout is not None:
                    process.stdout.close()
                log_handle.close()
                if state_written and process is not None and not process_group_exists(
                    process.pid
                ):
                    process_state_path(configuration, root).unlink(missing_ok=True)
                cleanup_complete = True
            raise
        finally:
            if not cleanup_complete:
                with blocked_termination_signals():
                    if process is not None and process.stdout is not None:
                        process.stdout.close()
                    log_handle.close()
                    if (
                        state_written
                        and process is not None
                        and not process_group_exists(process.pid)
                    ):
                        process_state_path(configuration, root).unlink(missing_ok=True)

    content = log_path.read_text(encoding="utf-8", errors="replace")
    fatal_marker = find_fatal_log_marker(content)
    if fatal_marker is not None:
        raise BaselineError(f"Original-baseline client log contains: {fatal_marker}")
    if return_code != 0:
        raise BaselineError(
            f"Original-baseline client exited with {return_code}; log: {log_path}"
        )
    verify_reference_bundle(configuration)
    verify_harness_artifact(configuration)
    verify_staged_reference(configuration, root, require_prelaunch=False)
    verify_launch_attempt(configuration, root, attempt)
    skin_cache = verify_scenario_evidence(configuration, root)
    verification_path = write_successful_run_verification(
        configuration, root, log_path, skin_cache
    )
    print(f"Verified original-client evidence: {verification_path}")
    return 0


def validate_command() -> int:
    configuration = load_configuration()
    verify_tracked_fabric_profile_snapshot(configuration)
    member_mod_ids = verify_reference_bundle(configuration)
    verify_harness_artifact(configuration)
    preflight_launcher_import_resolution()
    print(f"Validated profile: {profile_spec(configuration)['id']}")
    print(f"Published bundle SHA-256: {bundle_spec(configuration)['sha256']}")
    print(f"Harness SHA-256: {harness_spec(configuration)['sha256']}")
    fabric_profile = require_object(runtime_spec(configuration), "fabric_profile")
    fabric_snapshot = require_object(fabric_profile, "snapshot")
    print(f"Fabric profile snapshot SHA-256: {fabric_snapshot['sha256']}")
    print(f"Pinned top-level JAR members: {len(member_mod_ids)}")
    print("External game profiles consulted: 0")
    return 0


def provision_command() -> int:
    configuration = load_configuration()
    created = provision_profile(configuration)
    qualifier = "Provisioned" if created else "Verified"
    print(f"{qualifier} repository-owned runtime: {runtime_root(configuration)}")
    print("Minecraft was not launched; external game profiles consulted: 0")
    return 0


def stage_command() -> int:
    configuration = load_configuration()
    changed = stage_reference_members(configuration)
    qualifier = "Staged" if changed else "Verified"
    print(f"{qualifier} eight published JARs plus the separately pinned harness")
    print("Minecraft was not launched; external game profiles consulted: 0")
    return 0


def check_command() -> int:
    configuration = load_configuration()
    scenario_id = str(scenario_spec(configuration)["id"])
    java_path, command = check_environment(configuration, scenario_id)
    runtime = runtime_spec(configuration)
    print(
        f"Ready: Minecraft {runtime['minecraft_version']} / "
        f"Fabric {runtime['loader_version']} / Java {runtime['java_major']}"
    )
    print(f"Runtime root: {runtime_root(configuration)}")
    print(f"Java: {java_path}")
    print(f"Scenario: {scenario_id} (fresh capture targets verified)")
    print(f"Generated argv entries: {len(command)} (command intentionally not displayed)")
    print("External game profiles consulted: 0")
    return 0


def run_command(configured_scenario_id: str | None) -> int:
    configuration = load_configuration()
    scenario_id = resolve_scenario_id(configuration, configured_scenario_id)
    require_capture_harness(configuration)
    java_path, command = check_environment(configuration, scenario_id)
    return run_owned_client(configuration, scenario_id, java_path, command)


def argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Manage the one repository-owned Etherology published-0.1.7 Fabric "
            "1.21.1 reference runtime."
        )
    )
    parser.add_argument(
        "action", choices=("validate", "provision", "stage", "check", "run")
    )
    parser.add_argument(
        "--scenario",
        help="Exact tracked scenario id for run; arbitrary scenario ids are rejected",
    )
    return parser


def parse_arguments(arguments: list[str] | None = None) -> argparse.Namespace:
    return argument_parser().parse_args(arguments)


def main(arguments: list[str] | None = None) -> int:
    parsed = parse_arguments(arguments)
    actions = {
        "validate": validate_command,
        "provision": provision_command,
        "stage": stage_command,
        "check": check_command,
    }
    try:
        if parsed.action == "run":
            return run_command(parsed.scenario)
        if parsed.scenario is not None:
            raise BaselineError("--scenario is supported only by run")
        return actions[parsed.action]()
    except BaselineError as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
