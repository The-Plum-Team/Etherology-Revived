from __future__ import annotations

from contextlib import ExitStack
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import signal
import struct
import sys
import tempfile
import time
import unittest
from unittest import mock
import zipfile
import zlib


BASELINE_DIRECTORY = Path(__file__).resolve().parents[1]
MODULE_PATH = BASELINE_DIRECTORY / "original_client.py"
SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_original_client", MODULE_PATH
)
if SPECIFICATION is None or SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load controller module: {MODULE_PATH}")
client = importlib.util.module_from_spec(SPECIFICATION)
sys.modules[SPECIFICATION.name] = client
SPECIFICATION.loader.exec_module(client)

SLITHERITE_EVIDENCE_TEST_PATH = (
    BASELINE_DIRECTORY / "tests" / "test_original_slitherite_evidence_v9.py"
)
SLITHERITE_EVIDENCE_TEST_SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_original_slitherite_evidence_v9_fixture",
    SLITHERITE_EVIDENCE_TEST_PATH,
)
if (
    SLITHERITE_EVIDENCE_TEST_SPECIFICATION is None
    or SLITHERITE_EVIDENCE_TEST_SPECIFICATION.loader is None
):
    raise RuntimeError(
        f"Cannot load Slitherite evidence fixture: {SLITHERITE_EVIDENCE_TEST_PATH}"
    )
slitherite_evidence_fixture = importlib.util.module_from_spec(
    SLITHERITE_EVIDENCE_TEST_SPECIFICATION
)
sys.modules[SLITHERITE_EVIDENCE_TEST_SPECIFICATION.name] = slitherite_evidence_fixture
SLITHERITE_EVIDENCE_TEST_SPECIFICATION.loader.exec_module(
    slitherite_evidence_fixture
)

TRACKED_MANIFEST_PATH = (
    BASELINE_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v4.json"
)
ACTIVE_MANIFEST_PATH = (
    BASELINE_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v9.json"
)
LEGACY_SLITHERITE_MANIFEST_PATH = (
    BASELINE_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v5.json"
)
LEGACY_SLITHERITE_V6_MANIFEST_PATH = (
    BASELINE_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v6.json"
)
LEGACY_SLITHERITE_V7_MANIFEST_PATH = (
    BASELINE_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v7.json"
)
LEGACY_SLITHERITE_V8_MANIFEST_PATH = (
    BASELINE_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v8.json"
)
LEGACY_MANIFEST_PATH = (
    BASELINE_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v1.json"
)
LEGACY_FOREST_LANTERN_MANIFEST_PATH = (
    BASELINE_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v2.json"
)
LEGACY_ATTRAHITE_MANIFEST_PATH = (
    BASELINE_DIRECTORY / "original-fabric-1.21.1-published-0.1.7-v3.json"
)
TRACKED_EVIDENCE_ARCHIVE = (
    BASELINE_DIRECTORY.parents[1]
    / "docs"
    / "evidence"
    / "original-1.21.1"
    / "phase0-smoke-v1"
)
TRACKED_FOREST_LANTERN_EVIDENCE_ARCHIVE = (
    BASELINE_DIRECTORY.parents[1]
    / "docs"
    / "evidence"
    / "original-1.21.1"
    / "forest-lantern-v2"
)
TRACKED_ATTRAHITE_EVIDENCE_ARCHIVE = (
    BASELINE_DIRECTORY.parents[1]
    / "docs"
    / "evidence"
    / "original-1.21.1"
    / "attrahite-block-registry-v4"
)
ATTRAHITE_EVIDENCE_ARCHIVE_DIRECTORY_NAME = "attrahite-block-registry-v4"


def fabric_jar_bytes(mod_id: str, nested_mod_id: str | None = None) -> bytes:
    nested_path = "META-INF/jars/nested.jar"
    metadata: dict[str, object] = {
        "schemaVersion": 1,
        "id": mod_id,
        "version": "test-version",
    }
    if nested_mod_id is not None:
        metadata["jars"] = [{"file": nested_path}]
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata))
        if nested_mod_id is not None:
            archive.writestr(nested_path, fabric_jar_bytes(nested_mod_id))
    return output.getvalue()


def harness_jar_bytes(
    *,
    harness_version: str = "1.2.0",
    production_link: bool = False,
) -> bytes:
    jump_invoker_enabled = harness_version in {
        "1.1.0",
        "1.2.0",
        "1.3.0",
        "1.3.1",
        "1.3.2",
        "1.3.3",
        "1.3.4",
    }
    metadata = {
        "schemaVersion": 1,
        "id": "etherology_original_baseline_harness",
        "version": harness_version,
        "environment": "client",
        "entrypoints": {
            "client": [
                "dev.theplumteam.etherology.baseline.fabric.OriginalPhaseZeroHarness"
            ]
        },
        "mixins": [
            {
                "config": "etherology-original-baseline-harness.mixins.json",
                "environment": "client",
            }
        ],
        "depends": {
            "fabricloader": "=0.17.3",
            "fabric-api": "=0.110.0+1.21.1",
            "minecraft": "=1.21.1",
            "java": ">=21",
            "etherology": "=1.21-0.1.7",
        },
    }
    mixin = {
        "required": True,
        "package": "dev.theplumteam.etherology.baseline.fabric.mixin",
        "compatibilityLevel": "JAVA_21",
        "client": (
            ["GameRendererMixin", "PlayerEntityJumpInvoker"]
            if jump_invoker_enabled
            else ["GameRendererMixin"]
        ),
        "injectors": {"defaultRequire": 1},
    }
    class_content = (
        b"ru/feytox/etherology/implementation"
        if production_link
        else b"synthetic-harness-class"
    )
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        archive.writestr("fabric.mod.json", json.dumps(metadata))
        archive.writestr(
            "etherology-original-baseline-harness.mixins.json", json.dumps(mixin)
        )
        archive.writestr(
            "Etherology-Original-E2E-Harness-Fabric-1.21.1-refmap.json", "{}\n"
        )
        archive.writestr(
            "dev/theplumteam/etherology/baseline/fabric/"
            "OriginalPhaseZeroHarness.class",
            class_content,
        )
        archive.writestr(
            "dev/theplumteam/etherology/baseline/fabric/mixin/"
            "GameRendererMixin.class",
            b"synthetic-mixin-class",
        )
        if jump_invoker_enabled:
            archive.writestr(
                "dev/theplumteam/etherology/baseline/fabric/mixin/"
                "PlayerEntityJumpInvoker.class",
                b"synthetic-jump-invoker-class",
            )
    return output.getvalue()


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def hanging_minecraft_install_worker_fixture(
    _configuration: object,
    _root: Path,
    _result_connection: object,
) -> None:
    for termination_signal in client.CONTROLLER_TERMINATION_SIGNALS:
        signal.signal(termination_signal, signal.SIG_DFL)
    time.sleep(60)


def failing_minecraft_install_worker_fixture(
    _configuration: object,
    _root: Path,
    result_connection: object,
) -> None:
    result_connection.send(
        {
            "status": "failed",
            "error_type": "FixtureError",
            "error": "fixture worker failure",
        }
    )
    result_connection.close()


FIXTURE_ASSET_CONTENT = b"fixture-asset"
FIXTURE_CLIENT_CONTENT = b"fixture"


def compact_json_bytes(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")


def fixture_library_content(coordinate: str) -> bytes:
    return f"fixture-library:{coordinate}".encode("utf-8")


def fixture_asset_index() -> dict[str, object]:
    asset_sha1 = hashlib.sha1(FIXTURE_ASSET_CONTENT).hexdigest()
    return {
        "objects": {
            "fixture/object": {
                "hash": asset_sha1,
                "size": len(FIXTURE_ASSET_CONTENT),
            }
        }
    }


def fixture_vanilla_libraries() -> list[dict[str, object]]:
    native_coordinates = dict(client.EXPECTED_NATIVE_CLASSIFIERS)
    unconditional_indexes = {
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10,
        11,
        12,
        13,
        14,
        15,
        16,
        17,
        18,
        19,
        20,
        21,
        24,
        25,
        26,
        27,
        28,
        29,
        30,
        31,
        32,
        33,
        34,
        35,
        36,
        37,
        38,
        39,
        46,
        53,
        60,
        67,
        74,
        81,
        88,
        95,
        96,
    }
    libraries: list[dict[str, object]] = []
    for index in range(97):
        coordinate = native_coordinates.get(
            index, f"fixture.minecraft:library-{index}:1.0"
        )
        relative_path = client.maven_library_path(coordinate).as_posix()
        content = fixture_library_content(coordinate)
        library: dict[str, object] = {
            "downloads": {
                "artifact": {
                    "path": relative_path,
                    "sha1": hashlib.sha1(content).hexdigest(),
                    "size": len(content),
                    "url": f"https://libraries.minecraft.net/{relative_path}",
                }
            },
            "name": coordinate,
        }
        if index in native_coordinates or index == 0:
            library["rules"] = [{"action": "allow", "os": {"name": "osx"}}]
        elif index not in unconditional_indexes:
            library["rules"] = [
                {"action": "allow", "os": {"name": "windows"}}
            ]
        libraries.append(library)
    return libraries


def fixture_vanilla_metadata(manifest: dict[str, object]) -> dict[str, object]:
    runtime = manifest["runtime"]
    metadata = runtime["metadata"]
    asset_index = metadata["asset_index"]
    minecraft_client = metadata["client"]
    return {
        "id": "1.21.1",
        "type": "release",
        "assetIndex": {
            "id": asset_index["id"],
            "sha1": asset_index["sha1"],
            "size": asset_index["size"],
            "totalSize": asset_index["total_size"],
            "url": asset_index["url"],
        },
        "downloads": {
            "client": {
                "sha1": minecraft_client["sha1"],
                "size": minecraft_client["size"],
                "url": minecraft_client["url"],
            }
        },
        "javaVersion": {"component": "java-runtime-delta", "majorVersion": 21},
        "libraries": fixture_vanilla_libraries(),
    }


def fixture_fabric_profile(manifest: dict[str, object]) -> dict[str, object]:
    runtime = manifest["runtime"]
    return {
        "arguments": {
            "game": [],
            "jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "],
        },
        "id": "fabric-loader-0.17.3-1.21.1",
        "inheritsFrom": "1.21.1",
        "libraries": [
            {"name": library["coordinate"], "url": "https://maven.fabricmc.net/"}
            for library in runtime["fabric_profile"]["libraries"]
        ],
        "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
        "type": "release",
    }


def configure_synthetic_runtime_manifest(manifest: dict[str, object]) -> None:
    runtime = manifest["runtime"]
    metadata = runtime["metadata"]
    asset_index_content = compact_json_bytes(fixture_asset_index())
    metadata["asset_index"].update(
        {
            "size": len(asset_index_content),
            "total_size": len(FIXTURE_ASSET_CONTENT),
            "sha1": hashlib.sha1(asset_index_content).hexdigest(),
            "sha256": hashlib.sha256(asset_index_content).hexdigest(),
        }
    )
    metadata["client"].update(
        {
            "size": len(FIXTURE_CLIENT_CONTENT),
            "sha1": hashlib.sha1(FIXTURE_CLIENT_CONTENT).hexdigest(),
            "sha256": hashlib.sha256(FIXTURE_CLIENT_CONTENT).hexdigest(),
        }
    )
    vanilla_content = compact_json_bytes(fixture_vanilla_metadata(manifest))
    metadata.update(
        {
            "size": len(vanilla_content),
            "sha1": hashlib.sha1(vanilla_content).hexdigest(),
            "sha256": hashlib.sha256(vanilla_content).hexdigest(),
        }
    )
    fabric_profile = runtime["fabric_profile"]
    for library in fabric_profile["libraries"]:
        content = fixture_library_content(library["coordinate"])
        library.update(
            {
                "size": len(content),
                "sha1": hashlib.sha1(content).hexdigest(),
                "sha256": hashlib.sha256(content).hexdigest(),
            }
        )
    fabric_content = compact_json_bytes(fixture_fabric_profile(manifest))
    fabric_snapshot_content = fabric_content + b"\n"
    fabric_profile.update(
        {
            "size": len(fabric_content),
            "sha1": hashlib.sha1(fabric_content).hexdigest(),
            "sha256": hashlib.sha256(fabric_content).hexdigest(),
            "snapshot": {
                "path": "scripts/baseline/fixtures/"
                "fabric-loader-0.17.3-1.21.1-profile.json",
                "size": len(fabric_snapshot_content),
                "sha256": hashlib.sha256(fabric_snapshot_content).hexdigest(),
            },
        }
    )


def populate_runtime_launcher(configuration: object, root: Path) -> None:
    launcher = client.launcher_directory(configuration, root)
    installer = client.installer_directory(configuration, root)
    installer.mkdir(parents=True, exist_ok=True)
    launcher.mkdir(parents=True, exist_ok=True)
    manifest = configuration.manifest
    vanilla_source = fixture_vanilla_metadata(manifest)
    fabric_source = fixture_fabric_profile(manifest)
    client.pinned_vanilla_metadata_path(configuration, root).write_bytes(
        compact_json_bytes(vanilla_source)
    )
    client.pinned_fabric_profile_path(configuration, root).write_bytes(
        compact_json_bytes(fabric_source)
    )
    vanilla_projection = dict(vanilla_source)
    vanilla_projection.pop("javaVersion")
    write_json(client.vanilla_metadata_path(configuration, root), vanilla_projection)
    fabric_projection = dict(fabric_source)
    fabric_projection["jar"] = "1.21.1"
    write_json(client.fabric_metadata_path(configuration, root), fabric_projection)

    vanilla_jar = launcher / "versions" / "1.21.1" / "1.21.1.jar"
    vanilla_jar.parent.mkdir(parents=True, exist_ok=True)
    vanilla_jar.write_bytes(FIXTURE_CLIENT_CONTENT)
    for library in fixture_vanilla_libraries():
        if not client.library_is_selected_on_macos(library):
            continue
        coordinate = str(library["name"])
        path = launcher / "libraries" / Path(
            *client.maven_library_path(coordinate).parts
        )
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(fixture_library_content(coordinate))
    for library in client.require_list(
        client.require_object(client.runtime_spec(configuration), "fabric_profile"),
        "libraries",
    ):
        coordinate = str(library["coordinate"])
        path = launcher / "libraries" / Path(
            *client.maven_library_path(coordinate).parts
        )
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(fixture_library_content(coordinate))

    asset_index = fixture_asset_index()
    asset_index_path = launcher / "assets" / "indexes" / "17.json"
    asset_index_path.parent.mkdir(parents=True, exist_ok=True)
    asset_index_path.write_bytes(compact_json_bytes(asset_index))
    asset_sha1 = hashlib.sha1(FIXTURE_ASSET_CONTENT).hexdigest()
    asset_path = launcher / "assets" / "objects" / asset_sha1[:2] / asset_sha1
    asset_path.parent.mkdir(parents=True, exist_ok=True)
    asset_path.write_bytes(FIXTURE_ASSET_CONTENT)


def png_bytes(width: int, height: int, *, blank: bool = False) -> bytes:
    def chunk(chunk_type: bytes, payload: bytes) -> bytes:
        checksum = zlib.crc32(chunk_type + payload) & 0xFFFFFFFF
        return (
            struct.pack(">I", len(payload))
            + chunk_type
            + payload
            + struct.pack(">I", checksum)
        )

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    if blank:
        row = b"\0" * (width * 3)
    else:
        row_content = bytearray()
        for x_coordinate in range(width):
            row_content.extend(
                (
                    x_coordinate % 256,
                    (x_coordinate * 3) % 256,
                    (x_coordinate * 7) % 256,
                )
            )
        row = bytes(row_content)
    scanlines = (b"\0" + row) * height
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(scanlines))
        + chunk(b"IEND", b"")
    )


def reference_fixture(
    temporary_root: Path,
    *,
    nested_forbidden_mod_id: str | None = None,
    extra_entry: tuple[str, bytes] | None = None,
    harness_production_link: bool = False,
    source_manifest_path: Path = TRACKED_MANIFEST_PATH,
    fixture_manifest_name: str = "fixture.json",
) -> tuple[object, Path, dict[str, bytes]]:
    repository = temporary_root / "repository"
    baseline = repository / "scripts" / "baseline"
    state = baseline / ".state"
    state.mkdir(parents=True)
    manifest = json.loads(source_manifest_path.read_text(encoding="utf-8"))
    configure_synthetic_runtime_manifest(manifest)
    fabric_snapshot = manifest["runtime"]["fabric_profile"]["snapshot"]
    fabric_snapshot_path = repository / fabric_snapshot["path"]
    fabric_snapshot_path.parent.mkdir(parents=True)
    fabric_snapshot_path.write_bytes(
        compact_json_bytes(fixture_fabric_profile(manifest)) + b"\n"
    )
    bundle = manifest["reference_bundle"]
    bundle["path"] = "scripts/baseline/.state/reference.mrpack"
    member_contents: dict[str, bytes] = {}
    for index, member in enumerate(bundle["members"]):
        nested = nested_forbidden_mod_id if index == 0 else None
        content = fabric_jar_bytes(member["mod_id"], nested)
        member["size"] = len(content)
        member["sha256"] = hashlib.sha256(content).hexdigest()
        member_contents[member["archive_path"]] = content

    bundle_path = state / "reference.mrpack"
    index = {
        "dependencies": {
            "fabric-loader": manifest["runtime"]["loader_version"],
            "minecraft": manifest["runtime"]["minecraft_version"],
        },
        "files": [],
        "formatVersion": 1,
        "game": "minecraft",
        "name": "Synthetic Etherology reference",
        "versionId": "synthetic-reference",
    }
    with zipfile.ZipFile(bundle_path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("modrinth.index.json", json.dumps(index))
        archive.writestr("overrides/profile.json", "{}\n")
        archive.writestr("overrides/options.txt", "fullscreen:false\n")
        for archive_path, content in member_contents.items():
            archive.writestr(archive_path, content)
        if extra_entry is not None:
            archive.writestr(extra_entry[0], extra_entry[1])
    bundle["size"] = bundle_path.stat().st_size
    bundle["sha256"] = hashlib.sha256(bundle_path.read_bytes()).hexdigest()
    harness = manifest["capture"]["harness"]
    harness_content = harness_jar_bytes(
        harness_version=str(harness["version"]),
        production_link=harness_production_link,
    )
    harness_path = repository / harness["path"]
    harness_path.parent.mkdir(parents=True)
    harness_path.write_bytes(harness_content)
    harness["size"] = len(harness_content)
    harness["sha256"] = hashlib.sha256(harness_content).hexdigest()
    member_contents[str(harness["file_name"])] = harness_content
    manifest_path = baseline / fixture_manifest_name
    write_json(manifest_path, manifest)
    configuration = client.load_configuration(manifest_path, repository)
    return configuration, manifest_path, member_contents


def owned_runtime_fixture(
    configuration: object, temporary_root: Path
) -> tuple[Path, Path]:
    runtimes_root = temporary_root / "state" / "runtimes"
    runtimes_root.mkdir(parents=True)
    root = client.runtime_root(configuration, runtimes_root)
    root.mkdir()
    for field_name in (
        "game_directory",
        "launcher_directory",
        "evidence_directory",
        "logs_directory",
        "installer_directory",
        "home_directory",
        "temporary_directory",
    ):
        client.owned_child(configuration, field_name, root).mkdir()
    populate_runtime_launcher(configuration, root)
    game = client.game_directory(configuration, root)
    for directory in client.require_list(configuration.manifest, "profile_directories"):
        (game / str(directory)).mkdir(exist_ok=True)
    (game / "options.txt").write_bytes(client.expected_options_content(configuration))
    with zipfile.ZipFile(configuration.bundle_path) as archive:
        for member in client.member_specs(configuration):
            (game / "mods" / str(member["file_name"])).write_bytes(
                archive.read(str(member["archive_path"]))
            )
    harness = client.harness_spec(configuration)
    (game / "mods" / str(harness["file_name"])).write_bytes(
        configuration.harness_path.read_bytes()
    )
    client.scenario_root(configuration, root).mkdir()
    client.reports_directory(configuration, root).mkdir()
    client.screenshots_directory(configuration, root).mkdir()
    write_json(
        client.evidence_marker_path(configuration, root),
        client.evidence_descriptor(configuration),
    )
    client.lifecycle_lock_path(configuration, root).write_bytes(b"")
    write_json(client.marker_path(configuration, root), client.profile_descriptor(configuration))
    write_json(
        client.runtime_lock_path(configuration, root),
        client.runtime_lock_descriptor(configuration, root),
    )
    return runtimes_root, root


def write_launch_attempt_fixture(configuration: object, root: Path) -> Path:
    launcher = client.launcher_directory(configuration, root)
    game = client.game_directory(configuration, root)
    java = root.parent / "fixture-jdk" / "bin" / "java"
    library = launcher / "libraries" / "fixture.jar"
    for path in (java, library):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"fixture")
    artifact_lock = client.artifact_lock_path(configuration, root)
    write_json(artifact_lock, client.artifact_lock_descriptor(configuration, root))
    extraction_directory = client.native_extraction_directory(configuration, root)
    classpath = [
        str(launcher / Path(*PurePosixPath(relative_path).parts))
        for relative_path in client.expected_merged_classpath_paths(configuration, root)
    ]
    scenario_id = str(client.scenario_spec(configuration)["id"])
    command = [
        str(java),
        "-Duser.home=" + str(client.home_directory(configuration, root)),
        "-Djava.io.tmpdir=" + str(client.temporary_directory(configuration, root)),
        "-Djava.library.path=" + str(extraction_directory),
        "-Djna.tmpdir=" + str(extraction_directory),
        "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + str(extraction_directory),
        "-Dio.netty.native.workdir=" + str(extraction_directory),
        f"-D{client.SCENARIO_PROPERTY_NAME}={scenario_id}",
        "-cp",
        os.pathsep.join(classpath),
        "net.fabricmc.loader.impl.launch.knot.KnotClient",
        "--gameDir",
        str(game),
        "--assetsDir",
        str(launcher / "assets"),
        "--assetIndex",
        "17",
        "--version",
        client.version_id(configuration),
        "--username",
        "EtherologyE2E",
        "--uuid",
        client.offline_uuid("EtherologyE2E"),
        "--accessToken",
        client.OFFLINE_ACCESS_TOKEN,
        "--clientId",
        client.OFFLINE_CLIENT_ID,
        "--xuid",
        client.OFFLINE_XUID,
        "--width",
        "960",
        "--height",
        "540",
    ]
    environment = client.controlled_launch_environment(configuration, root, java)
    attempt = {
        "schema": 1,
        "profile_id": client.profile_spec(configuration)["id"],
        "scenario": scenario_id,
        "created_at_unix_ns": time.time_ns() - 10_000_000_000,
        "manifest_sha256": client.sha256_file(configuration.manifest_path),
        "artifact_lock": {
            "size": artifact_lock.stat().st_size,
            "sha256": client.sha256_file(artifact_lock),
        },
        "prelaunch_profile": client.prelaunch_profile_descriptor(
            configuration, root
        ),
        "generator": client.verify_launcher_library(
            client.temporary_directory(configuration, root) / "python-bytecode-cache"
        ),
        "provisioning_metadata": client.provisioning_metadata_descriptor(
            configuration, root
        ),
        "java": {
            "path": str(java),
            "major": 21,
            "size": java.stat().st_size,
            "sha256": client.sha256_file(java),
            "runtime": client.java_runtime_descriptor(java),
        },
        "launch": {
            "wrapper": [str(client.CAFFEINATE_PATH), "-dimsu"],
            "wrapper_executable": {
                "path": str(client.CAFFEINATE_PATH),
                "size": client.CAFFEINATE_PATH.stat().st_size,
                "sha256": client.sha256_file(client.CAFFEINATE_PATH),
            },
            "arguments": command,
            "arguments_sha256": client.canonical_json_sha256(command),
            "classpath": client.command_classpath_inventory(
                configuration, root, command
            ),
            "environment": environment,
            "environment_sha256": client.canonical_json_sha256(environment),
        },
        "version_metadata": client.version_metadata_inventory(configuration, root),
        "fabric_libraries": client.fabric_library_inventory(configuration, root),
        "vanilla_libraries": client.vanilla_library_inventory(configuration, root),
        "native_classifiers": client.native_classifier_inventory(
            configuration, root, command
        ),
        "assets": client.asset_inventory_descriptor(configuration, root),
        "launcher_files": client.launcher_file_inventory(configuration, root),
    }
    attempt_path = client.launch_attempt_path(configuration, root)
    write_json(attempt_path, attempt)
    return attempt_path


def write_passing_evidence(configuration: object, root: Path) -> None:
    scenario = client.scenario_spec(configuration)
    framebuffer = scenario["framebuffer"]
    attempt_path = write_launch_attempt_fixture(configuration, root)
    extracted_native = (
        client.native_extraction_directory(configuration, root) / "libfixture.dylib"
    )
    extracted_native.parent.mkdir()
    extracted_native.write_bytes(b"runtime-native")
    screenshot_content = png_bytes(framebuffer["width"], framebuffer["height"])
    screenshot = client.screenshot_path(configuration, root)
    screenshot.write_bytes(screenshot_content)
    world = client.save_directory(configuration, root)
    world.mkdir(parents=True)
    (world / "level.dat").write_bytes(b"level-data")
    (world / "session.lock").write_bytes(b"session-lock")
    region = world / "region"
    region.mkdir()
    (region / "r.0.0.mca").write_bytes(b"region-data")

    registry_descriptions = (
        "etherology:attrahite=block_class:net.minecraft.class_2248,"
        "item_class:net.minecraft.class_1747,default:{},states:1,raw_ids:1",
        "etherology:attrahite_bricks=block_class:net.minecraft.class_2248,"
        "item_class:net.minecraft.class_1747,default:{},states:1,raw_ids:1",
        "etherology:attrahite_brick_slab=block_class:net.minecraft.class_2482,"
        "item_class:net.minecraft.class_1747,"
        "default:{type=bottom, waterlogged=false},states:6,raw_ids:6",
        "etherology:attrahite_brick_stairs="
        "block_class:net.minecraft.class_2510,"
        "item_class:net.minecraft.class_1747,"
        "default:{facing=north, half=bottom, shape=straight, waterlogged=false},"
        "states:80,raw_ids:80",
    )
    registry_description = "[" + ", ".join(registry_descriptions) + "]"
    placed_states = (
        "etherology:attrahite={}",
        "etherology:attrahite_bricks={}",
        "etherology:attrahite_brick_slab={type=bottom, waterlogged=false}",
        "etherology:attrahite_brick_stairs="
        "{facing=north, half=bottom, shape=straight, waterlogged=false}",
    )
    exact_states = "[" + ", ".join(placed_states) + "]"
    placed_state_network_ids = "[" + ", ".join(
        f"{description}#{100 + index}"
        for index, description in enumerate(placed_states)
    ) + "]"
    exact_resources = "[" + ", ".join(client.ATTRAHITE_RESOURCES) + "]"
    tag_description = client.attrahite_tag_description()
    assertion_values = {
        "fabric_mod_loaded:etherology": ("loaded", "loaded"),
        "attrahite_canonical_resources_exact": (exact_resources, exact_resources),
        "registry:block:etherology:attrahite": ("present", "present"),
        "registry:item:etherology:attrahite": ("present", "present"),
        "registry:block:etherology:attrahite_bricks": ("present", "present"),
        "registry:item:etherology:attrahite_bricks": ("present", "present"),
        "registry:block:etherology:attrahite_brick_slab": ("present", "present"),
        "registry:item:etherology:attrahite_brick_slab": ("present", "present"),
        "registry:block:etherology:attrahite_brick_stairs": ("present", "present"),
        "registry:item:etherology:attrahite_brick_stairs": ("present", "present"),
        "attrahite_block_classes_exact": (
            "[Block, Block, SlabBlock, StairsBlock]",
            registry_description,
        ),
        "attrahite_block_items_exact": (
            "four exact BlockItem instances bound to their registered blocks",
            registry_description,
        ),
        "attrahite_default_states_exact": (
            "raw/bricks={}, slab={type=bottom,waterlogged=false}, "
            "stairs={facing=north,half=bottom,shape=straight,waterlogged=false}",
            registry_description,
        ),
        "attrahite_state_counts_exact": (
            "[1, 1, 6, 80]",
            registry_description,
        ),
        "attrahite_state_network_ids_exact": (
            "88 unique non-negative raw ids",
            registry_description,
        ),
        "packaged_root_jar:etherology": (
            "one regular root JAR",
            "one regular root JAR",
        ),
        "packaged_root_jar:etherology_original_baseline_harness": (
            "one regular root JAR",
            "one regular root JAR",
        ),
        "native_framebuffer_dimensions": ("1920x1080", "1920x1080"),
        "completed_world_renders_before_capture": ("120", "120"),
        "capture_render_ready": (
            "terrain complete and all four Attrahite positions rendering-ready",
            "ready",
        ),
        "capture_camera_exact": (
            "first_person=true;x=0.5;y=128.0;z=-15.5;yaw=0.0;pitch=23.0;"
            "on_ground=true;tolerance=1.0E-4",
            "first_person=true;x=0.5;y=128.0;z=-15.5;yaw=0.0;pitch=23.0;"
            "on_ground=true",
        ),
        "native_screenshot_written": (
            "one non-empty unedited 1920x1080 framebuffer PNG",
            f"{len(screenshot_content)} bytes, sha256="
            f"{hashlib.sha256(screenshot_content).hexdigest()}",
        ),
        "integrated_world_joined": (
            "running server and connected client",
            "joined",
        ),
        "server_arena_chunk_loaded": ("full chunk", "true"),
        "server_player_creative": ("creative", "true"),
        "server_attrahite_default_states_exact": (exact_states, exact_states),
        "client_attrahite_default_states_exact": (
            "all four exact default states mirrored",
            "mirrored",
        ),
        "server_attrahite_state_network_ids_exact": (
            "four placed states with non-negative raw ids",
            placed_state_network_ids,
        ),
        "attrahite_block_tags_exact": (
            "pickaxe=all four; needs_stone=raw; slabs=slab; stairs=stairs",
            tag_description,
        ),
        "attrahite_item_tags_exact": (
            "slabs=slab item; stairs=stairs item",
            tag_description,
        ),
        "attrahite_loot_shared_seed_roll_exact": (
            "0.05 <= first roll < 0.20",
            "seed=4096,roll=0.09789288",
        ),
        "loot:etherology:attrahite:silk_touch": (
            "[etherology:attrahitex1]",
            "[etherology:attrahitex1]",
        ),
        "loot:etherology:attrahite:no_silk_no_fortune": ("[]", "[]"),
        "loot:etherology:attrahite:fortune_iii": (
            "[etherology:enriched_attrahitex1]",
            "[etherology:enriched_attrahitex1]",
        ),
        "loot:etherology:attrahite_bricks": (
            "[etherology:attrahite_bricksx1]",
            "[etherology:attrahite_bricksx1]",
        ),
        "loot:etherology:attrahite_brick_slab": (
            "[etherology:attrahite_brick_slabx1]",
            "[etherology:attrahite_brick_slabx1]",
        ),
        "loot:etherology:attrahite_brick_stairs": (
            "[etherology:attrahite_brick_stairsx1]",
            "[etherology:attrahite_brick_stairsx1]",
        ),
        "live_world_identity": (
            "Etherology Original 0.1.7 Attrahite Blocks;"
            "4995697332085600305;minecraft:overworld",
            "Etherology Original 0.1.7 Attrahite Blocks;"
            "4995697332085600305;minecraft:overworld",
        ),
        "forced_world_save": ("true", "true"),
        "isolated_save_directory_present": (
            str(scenario["world_directory_name"]),
            str(scenario["world_directory_name"]),
        ),
    }
    for name, exact_recipe in client.ATTRAHITE_RECIPE_RESULTS.items():
        assertion_values[name] = (exact_recipe, exact_recipe)
    etherology = next(
        member
        for member in client.member_specs(configuration)
        if member["mod_id"] == "etherology"
    )
    harness = client.harness_spec(configuration)
    report = {
        "schema": 2,
        "reference_id": "published-0.1.7",
        "scenario": scenario["id"],
        "lane": "fabric-1.21.1-original",
        "status": "passed",
        "client_ticks": 120,
        "lifecycle_failure": "",
        "assertions": [
            {
                "name": name,
                "passed": True,
                "expected": assertion_values[name][0],
                "actual": assertion_values[name][1],
            }
            for name in client.EXPECTED_ASSERTION_NAMES
        ],
        "world": {
            "save_directory": scenario["world_directory_name"],
            "display_name": scenario["world_display_name"],
            "seed": scenario["world_seed"],
            "dimension": "minecraft:overworld",
            "integrated": True,
        },
        "artifacts": [
            {
                "mod_id": "etherology",
                "origin_kind": "PATH",
                "file_name": etherology["file_name"],
                "size": etherology["size"],
                "sha256": etherology["sha256"],
            },
            {
                "mod_id": harness["mod_id"],
                "origin_kind": "PATH",
                "file_name": harness["file_name"],
                "size": harness["size"],
                "sha256": harness["sha256"],
            },
        ],
        "mechanics": {
            "gallery_block_count": 4,
            "recipe_count": 9,
            "loot_table_count": 4,
            "fortune_level": 3,
            "base_drop_chance": 0.05,
            "fortune_multiplier": 0.05,
            "loot_probe": (
                "same seeded first roll for plain and Fortune III diamond pickaxes"
            ),
            "limitations": [],
        },
        "screenshots": [
            {
                "step": "attrahite-four-block-gallery",
                "file": f"screenshots/{scenario['screenshot_file']}",
                "width": framebuffer["width"],
                "height": framebuffer["height"],
                "size": len(screenshot_content),
                "sha256": hashlib.sha256(screenshot_content).hexdigest(),
                "completed_render_count": 120,
                "source": "minecraft-framebuffer",
                "edited": False,
            }
        ],
    }
    report_file = client.report_path(configuration, root)
    write_json(report_file, report)
    client.completion_marker_path(configuration, root).write_text(
        f"{scenario['id']}:passed\n"
        f"report_sha256:{client.sha256_file(report_file)}\n",
        encoding="utf-8",
    )
    latest_log = client.game_directory(configuration, root) / "logs" / "latest.log"
    latest_log.write_text(
        "Original Attrahite evidence published with status passed: fixture\n"
        "Stopping!\n",
        encoding="utf-8",
    )
    base_time = time.time_ns() - 5_000_000_000
    os.utime(attempt_path, ns=(base_time, base_time))
    for path, offset in (
        (world / "level.dat", 1_000_000),
        (world / "session.lock", 1_000_000),
        (screenshot, 2_000_000),
        (report_file, 3_000_000),
        (client.completion_marker_path(configuration, root), 4_000_000),
        (latest_log, 5_000_000),
    ):
        os.utime(path, ns=(base_time + offset, base_time + offset))


def rewrite_report_and_marker(
    configuration: object, root: Path, report: dict[str, object]
) -> None:
    report_file = client.report_path(configuration, root)
    marker = client.completion_marker_path(configuration, root)
    write_json(report_file, report)
    scenario = client.scenario_spec(configuration)
    marker.write_text(
        f"{scenario['id']}:passed\n"
        f"report_sha256:{client.sha256_file(report_file)}\n",
        encoding="utf-8",
    )
    now = time.time_ns()
    screenshot = client.screenshot_path(configuration, root)
    os.utime(screenshot, ns=(now - 2_000_000, now - 2_000_000))
    os.utime(report_file, ns=(now - 1_000_000, now - 1_000_000))
    os.utime(marker, ns=(now, now))


class TrackedManifestTests(unittest.TestCase):
    def test_consumed_v1_manifest_remains_byte_exact(self) -> None:
        self.assertEqual(
            hashlib.sha256(LEGACY_MANIFEST_PATH.read_bytes()).hexdigest(),
            "89fb643d68614b977e62560f2265a1cd05407aaa37a9f47e7a6eefe47e10125f",
        )
        manifest = json.loads(LEGACY_MANIFEST_PATH.read_text(encoding="utf-8"))
        self.assertEqual(
            manifest["profile"]["id"],
            "etherology-original-fabric-1.21.1-published-0.1.7-v1",
        )
        self.assertEqual(manifest["capture"]["scenario"]["id"], "phase0-smoke")

    def test_consumed_v2_manifest_remains_byte_exact(self) -> None:
        self.assertEqual(
            hashlib.sha256(
                LEGACY_FOREST_LANTERN_MANIFEST_PATH.read_bytes()
            ).hexdigest(),
            "0fed21fae4a00522407069e8ee97bda3fa5714245ea4c750d491b507de8e1361",
        )
        manifest = json.loads(
            LEGACY_FOREST_LANTERN_MANIFEST_PATH.read_text(encoding="utf-8")
        )
        self.assertEqual(
            manifest["profile"]["id"],
            "etherology-original-fabric-1.21.1-published-0.1.7-v2",
        )
        self.assertEqual(manifest["capture"]["scenario"]["id"], "forest-lantern")

    def test_consumed_v3_and_v4_manifests_remain_byte_exact(self) -> None:
        self.assertEqual(
            len(LEGACY_ATTRAHITE_MANIFEST_PATH.read_bytes()),
            10309,
        )
        self.assertEqual(
            hashlib.sha256(LEGACY_ATTRAHITE_MANIFEST_PATH.read_bytes()).hexdigest(),
            "457b069d3aecc9f83667026544e8734aadbb7285510cbd3279a0d0435a898aa3",
        )
        consumed_manifest = json.loads(
            LEGACY_ATTRAHITE_MANIFEST_PATH.read_text(encoding="utf-8")
        )
        fresh_manifest = json.loads(
            TRACKED_MANIFEST_PATH.read_text(encoding="utf-8")
        )
        self.assertEqual(
            consumed_manifest["profile"]["id"],
            "etherology-original-fabric-1.21.1-published-0.1.7-v3",
        )
        self.assertEqual(
            fresh_manifest["profile"]["id"],
            "etherology-original-fabric-1.21.1-published-0.1.7-v4",
        )
        self.assertNotEqual(
            consumed_manifest["profile"]["runtime_directory"],
            fresh_manifest["profile"]["runtime_directory"],
        )
        self.assertEqual(
            consumed_manifest["capture"],
            fresh_manifest["capture"],
        )
        self.assertEqual(len(TRACKED_MANIFEST_PATH.read_bytes()), 10309)
        self.assertEqual(
            hashlib.sha256(TRACKED_MANIFEST_PATH.read_bytes()).hexdigest(),
            "6f6f84c0c33f4f269dd37d5876f22423cef188b1d49fa873dc1a953870f5bdb0",
        )

    def test_consumed_v5_contract_remains_byte_exact(self) -> None:
        expected = {
            LEGACY_SLITHERITE_MANIFEST_PATH: (
                10_321,
                "ff1a5d17607878fdb77f6c3daa3da69185ae6243e853ac26bff446ad2478593b",
            ),
            BASELINE_DIRECTORY / "original_slitherite_evidence_v5.py": (
                46_366,
                "78c19ef6ce1f944d2eebd2024e8031b05ba1866bb394315c0e359de98366aed9",
            ),
            (
                BASELINE_DIRECTORY
                / "tests"
                / "test_original_slitherite_evidence_v5.py"
            ): (
                18_003,
                "a2f218240cf7272b7f6f657debe2a9b4e058719fd479d90c262ced5835850369",
            ),
        }
        for path, (size, sha256) in expected.items():
            with self.subTest(path=path):
                self.assertTrue(path.is_file())
                self.assertFalse(path.is_symlink())
                self.assertEqual(size, path.stat().st_size)
                self.assertEqual(sha256, client.sha256_file(path))
        archive = (
            BASELINE_DIRECTORY.parents[1]
            / "docs"
            / "evidence"
            / "original-1.21.1"
            / "slitherite-block-registry-v5"
        )
        self.assertFalse(archive.exists())
        self.assertFalse(archive.is_symlink())

    def test_consumed_v5_failure_history_remains_byte_exact_when_present(
        self,
    ) -> None:
        runtime = (
            BASELINE_DIRECTORY
            / ".state"
            / "runtimes"
            / "etherology-original-fabric-1.21.1-published-0.1.7-v5"
        )
        expected = {
            runtime / "launch-attempt.json": (
                871_888,
                "22daf507aa9791fdfc93296bf33c5e9d82a45f3ee96926c38789294d54298fe3",
            ),
            runtime / "evidence" / ".etherology-original-evidence.json": (
                954,
                "ea8d93ff93b942d60950b2010db9d8fab3bddbacff710f79f97bc6d91e3af263",
            ),
            (
                runtime
                / "evidence"
                / "slitherite-block-registry"
                / "reports"
                / "report.json"
            ): (
                63_151,
                "0c1238d974e3b4b3bddc26aa854b63b682688423b7b10386dd8aff7bc30d20a9",
            ),
            (
                runtime
                / "evidence"
                / "slitherite-block-registry"
                / "reports"
                / "done.marker"
            ): (
                112,
                "32a05a0069c5f897fb0a1236a0e485da16b2741d39f4439b32b7b2acca4df07b",
            ),
            runtime / "logs" / "original-client-20260901T020354Z.log": (
                12_869,
                "4ae116837bc4fd40f9d0a34151a890da004e706662fe22700e36e534b1b1450e",
            ),
            runtime / "game" / "logs" / "latest.log": (
                12_435,
                "9f2cb3114248a41242928e3173c611e595b85bc5a53111ef38fccdfda1f5df54",
            ),
        }
        if not runtime.exists() and not runtime.is_symlink():
            return
        intermediate_directories = {
            runtime.parents[1],
            runtime.parent,
            runtime,
        }
        for artifact in expected:
            directory = artifact.parent
            while directory != runtime:
                intermediate_directories.add(directory)
                directory = directory.parent
        for directory in sorted(
            intermediate_directories,
            key=lambda candidate: (len(candidate.parts), str(candidate)),
        ):
            self.assertTrue(directory.is_dir())
            self.assertFalse(directory.is_symlink())
        present = tuple(path.exists() or path.is_symlink() for path in expected)
        self.assertTrue(all(present), "The consumed v5 failure history is partial")
        for path, (size, sha256) in expected.items():
            with self.subTest(path=path):
                self.assertTrue(path.is_file())
                self.assertFalse(path.is_symlink())
                self.assertEqual(size, path.stat().st_size)
                self.assertEqual(sha256, client.sha256_file(path))

    def test_consumed_v6_contract_remains_byte_exact(self) -> None:
        expected = {
            LEGACY_SLITHERITE_V6_MANIFEST_PATH: (
                10_321,
                "a8a6521e2402433cc1ccf56319eb6e26142f1dc2bc42a1f6be56ce07a9d7a399",
            ),
            BASELINE_DIRECTORY / "original_slitherite_evidence_v6.py": (
                46_366,
                "dc8da7ed6f54e366066c69792ea4b09d148c472430f76068c3a6ebbe3e05d675",
            ),
            (
                BASELINE_DIRECTORY
                / "tests"
                / "test_original_slitherite_evidence_v6.py"
            ): (
                18_003,
                "da2545d8a33351fc95414dced48781fc9c00aa4287a194a9ce6c3d3d57d5c654",
            ),
        }
        for path, (size, sha256) in expected.items():
            with self.subTest(path=path):
                self.assertTrue(path.is_file())
                self.assertFalse(path.is_symlink())
                self.assertEqual(size, path.stat().st_size)
                self.assertEqual(sha256, client.sha256_file(path))
        archive = (
            BASELINE_DIRECTORY.parents[1]
            / "docs"
            / "evidence"
            / "original-1.21.1"
            / "slitherite-block-registry-v6"
        )
        self.assertFalse(archive.exists())
        self.assertFalse(archive.is_symlink())

    def test_consumed_v6_failure_history_remains_byte_exact_when_present(
        self,
    ) -> None:
        runtime = (
            BASELINE_DIRECTORY
            / ".state"
            / "runtimes"
            / "etherology-original-fabric-1.21.1-published-0.1.7-v6"
        )
        expected = {
            runtime / "launch-attempt.json": (
                871_888,
                "7f7e35af37688ab9ac487ada4fd8030c22b87ad42b63656a34c3c20dfd4679f0",
            ),
            runtime / "evidence" / ".etherology-original-evidence.json": (
                954,
                "ccc3bbef121811ba3dd102f9a9ce41af17569ff27e71aeb30719ab42f45b99de",
            ),
            (
                runtime
                / "evidence"
                / "slitherite-block-registry"
                / "reports"
                / "report.json"
            ): (
                98_188,
                "c00ff0a700564d327872a2ef700bcea870f719d0a049246947fc8141f4e93ac6",
            ),
            (
                runtime
                / "evidence"
                / "slitherite-block-registry"
                / "reports"
                / "done.marker"
            ): (
                112,
                "8fa9139d0c06d9bcb0b8ce24b1e22786a6aa1b79e68b19896a28292c2c6e4875",
            ),
            runtime / "logs" / "original-client-20260901T022923Z.log": (
                15_707,
                "6085fb55fd4ddbe7567f40f3af9eddaeb21e74d182cb9a6dc340f9fd0b5a9a93",
            ),
            runtime / "game" / "logs" / "latest.log": (
                15_273,
                "1a03e60e918acf482099b15e6dcc07bcc0761ca17cefff819091a9c99ddbd27c",
            ),
        }
        if not runtime.exists() and not runtime.is_symlink():
            return
        intermediate_directories = {
            runtime.parents[1],
            runtime.parent,
            runtime,
        }
        for artifact in expected:
            directory = artifact.parent
            while directory != runtime:
                intermediate_directories.add(directory)
                directory = directory.parent
        for directory in sorted(
            intermediate_directories,
            key=lambda candidate: (len(candidate.parts), str(candidate)),
        ):
            self.assertTrue(directory.is_dir())
            self.assertFalse(directory.is_symlink())
        present = tuple(path.exists() or path.is_symlink() for path in expected)
        self.assertTrue(all(present), "The consumed v6 failure history is partial")
        for path, (size, sha256) in expected.items():
            with self.subTest(path=path):
                self.assertTrue(path.is_file())
                self.assertFalse(path.is_symlink())
                self.assertEqual(size, path.stat().st_size)
                self.assertEqual(sha256, client.sha256_file(path))
        screenshots = (
            runtime
            / "evidence"
            / "slitherite-block-registry"
            / "screenshots"
        )
        self.assertTrue(screenshots.is_dir())
        self.assertFalse(screenshots.is_symlink())
        self.assertEqual(list(screenshots.iterdir()), [])

    def test_consumed_v7_contract_remains_byte_exact(self) -> None:
        expected = {
            LEGACY_SLITHERITE_V7_MANIFEST_PATH: (
                10_321,
                "d8c798abe4622b786876193d55bd83d91ed7a6f3686c66c2e2d4d03a3eb7c5b1",
            ),
            BASELINE_DIRECTORY / "original_slitherite_evidence_v7.py": (
                46_366,
                "bf9598f1e42df35b1d8cc33af59c3c9494424cb95494f26f505beb7693b9cdf6",
            ),
            (
                BASELINE_DIRECTORY
                / "tests"
                / "test_original_slitherite_evidence_v7.py"
            ): (
                18_003,
                "5f9b00d287064f74c2587990aa5da076b022bbdaa3870f8b6bab1748d1662b75",
            ),
        }
        for path, (size, sha256) in expected.items():
            with self.subTest(path=path):
                self.assertTrue(path.is_file())
                self.assertFalse(path.is_symlink())
                self.assertEqual(size, path.stat().st_size)
                self.assertEqual(sha256, client.sha256_file(path))

        manifest = json.loads(
            LEGACY_SLITHERITE_V7_MANIFEST_PATH.read_text(encoding="utf-8")
        )
        self.assertEqual(
            manifest["capture"]["harness"],
            {
                "status": "implemented",
                "path": (
                    "baseline-harness/fabric/1.21.1/build/libs/"
                    "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.3.2.jar"
                ),
                "file_name": (
                    "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.3.2.jar"
                ),
                "mod_id": "etherology_original_baseline_harness",
                "version": "1.3.2",
                "size": 210_485,
                "sha256": (
                    "e86df68418ace4b17ff7e1fdd2c8b023dfe2c6d2f82ab6a47498e70d65e42353"
                ),
                "client_entrypoint": (
                    "dev.theplumteam.etherology.baseline.fabric."
                    "OriginalPhaseZeroHarness"
                ),
                "mixin_config": (
                    "etherology-original-baseline-harness.mixins.json"
                ),
            },
        )
        archive = (
            BASELINE_DIRECTORY.parents[1]
            / "docs"
            / "evidence"
            / "original-1.21.1"
            / "slitherite-block-registry-v7"
        )
        self.assertFalse(archive.exists())
        self.assertFalse(archive.is_symlink())

    def test_consumed_v7_failure_history_remains_exact_when_present(self) -> None:
        runtime = (
            BASELINE_DIRECTORY
            / ".state"
            / "runtimes"
            / "etherology-original-fabric-1.21.1-published-0.1.7-v7"
        )
        report_path = (
            runtime
            / "evidence"
            / "slitherite-block-registry"
            / "reports"
            / "report.json"
        )
        controller_log = runtime / "logs" / "original-client-20260901T030159Z.log"
        game_log = runtime / "game" / "logs" / "latest.log"
        expected = {
            runtime / "launch-attempt.json": (
                871_888,
                "d51a036beeadbded708c81d92f11957d287d62f0f5bcbf5b708aeba58e0ab06e",
            ),
            runtime / "evidence" / ".etherology-original-evidence.json": (
                954,
                "35304d80c5337209cf2c80131726b0af3c04488fd9687b3834c6d947596fda86",
            ),
            report_path: (
                98_188,
                "3e1798080d252c42068a900d81c9fab12d19a450cc853b40497b9f8d7fdd7d65",
            ),
            (
                runtime
                / "evidence"
                / "slitherite-block-registry"
                / "reports"
                / "done.marker"
            ): (
                112,
                "009a0877300e26d6f40f51f49fd8151a8ed8ffd716242eee1a14558d1ca7a972",
            ),
            controller_log: (
                14_821,
                "db026d831dc9136f0c53bf7c806e83e6c58369c315f86236c48188fe0f63cc44",
            ),
            game_log: (
                14_387,
                "17332d7c780ca49e798f62b4091f3457e03cc1aeab64e5577ef579e6ed2e9676",
            ),
            (
                runtime
                / "game"
                / "mods"
                / "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.3.2.jar"
            ): (
                210_485,
                "e86df68418ace4b17ff7e1fdd2c8b023dfe2c6d2f82ab6a47498e70d65e42353",
            ),
        }
        if not runtime.exists() and not runtime.is_symlink():
            return
        intermediate_directories = {
            runtime.parents[1],
            runtime.parent,
            runtime,
        }
        for artifact in expected:
            directory = artifact.parent
            while directory != runtime:
                intermediate_directories.add(directory)
                directory = directory.parent
        for directory in sorted(
            intermediate_directories,
            key=lambda candidate: (len(candidate.parts), str(candidate)),
        ):
            self.assertTrue(directory.is_dir())
            self.assertFalse(directory.is_symlink())
        present = tuple(path.exists() or path.is_symlink() for path in expected)
        self.assertTrue(all(present), "The consumed v7 failure history is partial")
        for path, (size, sha256) in expected.items():
            with self.subTest(path=path):
                self.assertTrue(path.is_file())
                self.assertFalse(path.is_symlink())
                self.assertEqual(size, path.stat().st_size)
                self.assertEqual(sha256, client.sha256_file(path))

        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual(report["status"], "failed")
        self.assertIs(report["passed"], False)
        self.assertEqual(report["client_ticks"], 160)
        self.assertEqual(
            report["lifecycle_failure"],
            "The native button/pressure-plate behavior changed",
        )
        self.assertEqual(
            report["slitherite"]["button_behavior"],
            "powered=true;scheduled=true;elapsed=31;reset=true",
        )
        self.assertEqual(
            report["slitherite"]["pressure_plate_behavior"],
            "item=false;living=false;reset=true",
        )
        self.assertEqual(len(report["assertions"]), 183)
        self.assertEqual(
            sum(assertion["passed"] is True for assertion in report["assertions"]),
            165,
        )
        failed_assertions = [
            assertion for assertion in report["assertions"]
            if assertion["passed"] is False
        ]
        self.assertEqual(
            failed_assertions[0],
            {
                "name": "slitherite_pressure_plate_entities_exact",
                "passed": False,
                "expected": "item=false;living=true;reset=true",
                "actual": "item=false;living=false;reset=true",
            },
        )
        self.assertEqual(
            [assertion["name"] for assertion in failed_assertions[1:]],
            [
                "forced_world_save",
                "restart_fixture_persistence_exact",
                "restart_loaded_data_exact",
                "capture_mirror_exact:initial",
                "capture_render_ready:initial",
                "capture_lighting_ready:initial",
                "capture_camera_exact:initial",
                "capture_consecutive_stable_renders:initial",
                "capture_framebuffer_dimensions:initial",
                "native_screenshot_written:initial",
                "capture_mirror_exact:reopened",
                "capture_render_ready:reopened",
                "capture_lighting_ready:reopened",
                "capture_camera_exact:reopened",
                "capture_consecutive_stable_renders:reopened",
                "capture_framebuffer_dimensions:reopened",
                "native_screenshot_written:reopened",
            ],
        )
        self.assertEqual(
            report["world"],
            {
                "save_directory": (
                    "etherology-original-slitherite-block-registry-world"
                ),
                "display_name": "Etherology Original 0.1.7 Slitherite Blocks",
                "seed": 4995697409260082224,
                "dimension": "minecraft:overworld",
                "integrated": True,
                "reopened": False,
            },
        )
        screenshots = (
            runtime
            / "evidence"
            / "slitherite-block-registry"
            / "screenshots"
        )
        self.assertTrue(screenshots.is_dir())
        self.assertFalse(screenshots.is_symlink())
        self.assertEqual(list(screenshots.iterdir()), [])

        for shutdown_log in (controller_log, game_log):
            content = shutdown_log.read_text(encoding="utf-8")
            self.assertTrue(content.rstrip().endswith("Saving worlds"))
            self.assertNotIn("All dimensions are saved", content)

    def test_consumed_v8_contract_remains_byte_exact(self) -> None:
        expected = {
            LEGACY_SLITHERITE_V8_MANIFEST_PATH: (
                10_321,
                "bcbb0f6b8edfcba08f4afdadaa61e58bf7bd94808e0cd1de7773e1f5d2f5937a",
            ),
            BASELINE_DIRECTORY / "original_slitherite_evidence_v8.py": (
                46_366,
                "f0e0ad0540920d7f4a81c3ae2604f0569e9fbf1b69d7d238a675431f447ea701",
            ),
            (
                BASELINE_DIRECTORY
                / "tests"
                / "test_original_slitherite_evidence_v8.py"
            ): (
                18_003,
                "a905d8476383dd07fb39cfbd4137c251a963766059ef8ee273404242e6473911",
            ),
        }
        for path, (size, sha256) in expected.items():
            with self.subTest(path=path):
                self.assertTrue(path.is_file())
                self.assertFalse(path.is_symlink())
                self.assertEqual(size, path.stat().st_size)
                self.assertEqual(sha256, client.sha256_file(path))

        manifest = json.loads(
            LEGACY_SLITHERITE_V8_MANIFEST_PATH.read_text(encoding="utf-8")
        )
        self.assertEqual(
            manifest["capture"]["harness"],
            {
                "status": "implemented",
                "path": (
                    "baseline-harness/fabric/1.21.1/build/libs/"
                    "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.3.3.jar"
                ),
                "file_name": (
                    "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.3.3.jar"
                ),
                "mod_id": "etherology_original_baseline_harness",
                "version": "1.3.3",
                "size": 212_059,
                "sha256": (
                    "8e273d156f1b6014b5d206fa3bfa2682594d36dae00c7aeaac8eb56928f88c3a"
                ),
                "client_entrypoint": (
                    "dev.theplumteam.etherology.baseline.fabric."
                    "OriginalPhaseZeroHarness"
                ),
                "mixin_config": (
                    "etherology-original-baseline-harness.mixins.json"
                ),
            },
        )
        archive = (
            BASELINE_DIRECTORY.parents[1]
            / "docs"
            / "evidence"
            / "original-1.21.1"
            / "slitherite-block-registry-v8"
        )
        self.assertFalse(archive.exists())
        self.assertFalse(archive.is_symlink())

    def test_consumed_v8_light_race_history_remains_exact_when_present(self) -> None:
        runtime = (
            BASELINE_DIRECTORY
            / ".state"
            / "runtimes"
            / "etherology-original-fabric-1.21.1-published-0.1.7-v8"
        )
        report_path = (
            runtime
            / "evidence"
            / "slitherite-block-registry"
            / "reports"
            / "report.json"
        )
        controller_log = runtime / "logs" / "original-client-20260901T040501Z.log"
        game_log = runtime / "game" / "logs" / "latest.log"
        expected = {
            runtime / ".etherology-original-profile.json": (
                3_082,
                "904884d938d254b7f3c7003482da5af3cb991e0388a3213ce562875e6b981640",
            ),
            runtime / "launch-attempt.json": (
                871_888,
                "8a7b5b6f5410c40ad99218083944121ecd4ed8ee25df8511b568cbff15cd164b",
            ),
            runtime / "evidence" / ".etherology-original-evidence.json": (
                954,
                "a75d2779da699d9aa3feede07bba035fcfe4ef45cd551f41332af29bd649db9d",
            ),
            report_path: (
                99_016,
                "8f226e38825b66b8a657913e24e1875195fc0a09645d1e3e47dcda8c9d54a5e4",
            ),
            (
                runtime
                / "evidence"
                / "slitherite-block-registry"
                / "reports"
                / "done.marker"
            ): (
                112,
                "587b1f278aad7578a869cce3dc5f5fbac9becef60b21d112a1dfc75223f43cb1",
            ),
            controller_log: (
                16_293,
                "f34104c6c41f8fe4ab8445ace542a23bd8684811cce3436b89f1ceaac56273df",
            ),
            game_log: (
                15_859,
                "16cdad10750a86c28d2bcc4f7e909c39a2d784aaee65b625db2747f971de766f",
            ),
            (
                runtime
                / "game"
                / "mods"
                / "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.3.3.jar"
            ): (
                212_059,
                "8e273d156f1b6014b5d206fa3bfa2682594d36dae00c7aeaac8eb56928f88c3a",
            ),
        }
        if not runtime.exists() and not runtime.is_symlink():
            return
        intermediate_directories = {
            runtime.parents[1],
            runtime.parent,
            runtime,
        }
        for artifact in expected:
            directory = artifact.parent
            while directory != runtime:
                intermediate_directories.add(directory)
                directory = directory.parent
        for directory in sorted(
            intermediate_directories,
            key=lambda candidate: (len(candidate.parts), str(candidate)),
        ):
            self.assertTrue(directory.is_dir())
            self.assertFalse(directory.is_symlink())
        present = tuple(path.exists() or path.is_symlink() for path in expected)
        self.assertTrue(all(present), "The consumed v8 failure history is partial")
        for path, (size, sha256) in expected.items():
            with self.subTest(path=path):
                self.assertTrue(path.is_file())
                self.assertFalse(path.is_symlink())
                self.assertEqual(size, path.stat().st_size)
                self.assertEqual(sha256, client.sha256_file(path))

        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual(report["status"], "failed")
        self.assertIs(report["passed"], False)
        self.assertEqual(report["client_ticks"], 6_188)
        lifecycle_failure = report["lifecycle_failure"]
        self.assertTrue(
            lifecycle_failure.startswith(
                "Timed out in WAITING_FOR_CLIENT_MIRROR after 6000 client ticks;"
            )
        )
        self.assertIn("stableClientTicks=0;clientPending=false", lifecycle_failure)
        self.assertIn("-8,123,2=0,-6,123,2=0", lifecycle_failure)
        self.assertIn("-7,123,6=0,-5,123,6=0", lifecycle_failure)
        self.assertIn("serverGeneration=5997", lifecycle_failure)
        self.assertEqual(
            report["slitherite"]["button_behavior"],
            "powered=true;scheduled=true;elapsed=27;reset=true",
        )
        self.assertEqual(
            report["slitherite"]["pressure_plate_behavior"],
            "item=false;living=true;reset=true",
        )
        self.assertEqual(len(report["assertions"]), 183)
        self.assertEqual(
            sum(assertion["passed"] is True for assertion in report["assertions"]),
            166,
        )
        self.assertEqual(
            [
                assertion["name"]
                for assertion in report["assertions"]
                if assertion["passed"] is False
            ],
            [
                "forced_world_save",
                "restart_fixture_persistence_exact",
                "restart_loaded_data_exact",
                "capture_mirror_exact:initial",
                "capture_render_ready:initial",
                "capture_lighting_ready:initial",
                "capture_camera_exact:initial",
                "capture_consecutive_stable_renders:initial",
                "capture_framebuffer_dimensions:initial",
                "native_screenshot_written:initial",
                "capture_mirror_exact:reopened",
                "capture_render_ready:reopened",
                "capture_lighting_ready:reopened",
                "capture_camera_exact:reopened",
                "capture_consecutive_stable_renders:reopened",
                "capture_framebuffer_dimensions:reopened",
                "native_screenshot_written:reopened",
            ],
        )
        self.assertEqual(report["screenshots"], [])
        screenshots = (
            runtime
            / "evidence"
            / "slitherite-block-registry"
            / "screenshots"
        )
        self.assertTrue(screenshots.is_dir())
        self.assertFalse(screenshots.is_symlink())
        self.assertEqual(list(screenshots.iterdir()), [])
        for shutdown_log in (controller_log, game_log):
            content = shutdown_log.read_text(encoding="utf-8")
            self.assertIn("All chunks are saved", content)
            self.assertTrue(content.rstrip().endswith("All dimensions are saved"))

    def test_active_v9_profile_is_fresh_and_has_no_runtime_or_archive(self) -> None:
        self.assertEqual(client.MANIFEST_PATH, ACTIVE_MANIFEST_PATH)
        self.assertEqual(len(ACTIVE_MANIFEST_PATH.read_bytes()), 10321)
        self.assertEqual(
            hashlib.sha256(ACTIVE_MANIFEST_PATH.read_bytes()).hexdigest(),
            "4655f6e0322555b57da2666333d2f846d4ebd137e37dcf6217970b709330e7dd",
        )
        manifest = json.loads(ACTIVE_MANIFEST_PATH.read_text(encoding="utf-8"))
        profile_id = "etherology-original-fabric-1.21.1-published-0.1.7-v9"
        self.assertEqual(manifest["profile"]["id"], profile_id)
        self.assertEqual(manifest["profile"]["runtime_directory"], profile_id)
        self.assertEqual(
            manifest["capture"]["scenario"]["id"],
            "slitherite-block-registry",
        )
        configuration = client.load_configuration()
        self.assertEqual(
            client.profile_descriptor(configuration)["capture"]["screenshot_files"],
            [
                "slitherite-block-registry-initial.png",
                "slitherite-block-registry-reopened.png",
            ],
        )
        runtime_root = BASELINE_DIRECTORY / ".state" / "runtimes" / profile_id
        for path in (
            runtime_root,
            runtime_root / "launch-attempt.json",
            runtime_root / "evidence",
            runtime_root / "game" / "saves" / (
                "etherology-original-slitherite-block-registry-world"
            ),
        ):
            self.assertFalse(path.exists())
            self.assertFalse(path.is_symlink())
        archive = (
            BASELINE_DIRECTORY.parents[1]
            / "docs"
            / "evidence"
            / "original-1.21.1"
            / "slitherite-block-registry-v9"
        )
        self.assertFalse(archive.exists())
        self.assertFalse(archive.is_symlink())

    def test_tracked_original_evidence_archive_is_exact(self) -> None:
        archive_manifest = client.load_json_object(
            TRACKED_EVIDENCE_ARCHIVE / "archive-manifest.json",
            "Tracked original-baseline evidence archive manifest",
        )
        self.assertEqual(
            archive_manifest["kind"],
            "etherology-original-fabric-baseline-evidence",
        )
        self.assertEqual(archive_manifest["scenario"], "phase0-smoke")
        self.assertEqual(archive_manifest["assertion_count"], 30)
        archived_files = archive_manifest["files"]
        self.assertEqual(
            set(archived_files),
            {
                "reports/report.json",
                "reports/done.marker",
                "screenshots/phase0-smoke.png",
                "controller/original-client.log",
                "controller/verification.json",
            },
        )
        for relative_name, pinned in archived_files.items():
            relative_path = PurePosixPath(relative_name)
            self.assertFalse(relative_path.is_absolute())
            self.assertNotIn("..", relative_path.parts)
            client.verify_exact_file(
                TRACKED_EVIDENCE_ARCHIVE / Path(*relative_path.parts),
                pinned["sha256"],
                pinned["size"],
                f"Tracked original-baseline archive file {relative_name}",
            )
        report = client.load_json_object(
            TRACKED_EVIDENCE_ARCHIVE / "reports" / "report.json",
            "Tracked original-baseline scenario report",
        )
        self.assertEqual(report["status"], "passed")
        self.assertEqual(report["reference_id"], "published-0.1.7")
        self.assertEqual(len(report["assertions"]), 30)
        self.assertTrue(all(value["passed"] is True for value in report["assertions"]))
        self.assertEqual(
            client.png_dimensions(
                TRACKED_EVIDENCE_ARCHIVE / "screenshots" / "phase0-smoke.png"
            ),
            (1920, 1080),
        )
        verification = client.load_json_object(
            TRACKED_EVIDENCE_ARCHIVE / "controller" / "verification.json",
            "Tracked original-baseline controller verification",
        )
        self.assertEqual(verification["status"], "passed")
        self.assertEqual(
            verification["screenshot"]["sha256"],
            archived_files["screenshots/phase0-smoke.png"]["sha256"],
        )

    def test_tracked_original_forest_lantern_archive_is_exact(self) -> None:
        archive_manifest = client.load_json_object(
            TRACKED_FOREST_LANTERN_EVIDENCE_ARCHIVE / "archive-manifest.json",
            "Tracked original Forest Lantern evidence archive manifest",
        )
        self.assertEqual(
            archive_manifest["kind"],
            "etherology-original-fabric-baseline-evidence",
        )
        self.assertEqual(archive_manifest["scenario"], "forest-lantern")
        self.assertEqual(archive_manifest["assertion_count"], 36)
        self.assertEqual(archive_manifest["screenshot_count"], 1)
        self.assertEqual(
            archive_manifest["profile"]["id"],
            "etherology-original-fabric-1.21.1-published-0.1.7-v2",
        )
        client.verify_exact_file(
            LEGACY_FOREST_LANTERN_MANIFEST_PATH,
            archive_manifest["profile"]["manifest_sha256"],
            archive_manifest["profile"]["manifest_size"],
            "Tracked original Forest Lantern profile manifest",
        )
        archived_files = archive_manifest["files"]
        self.assertEqual(
            set(archived_files),
            {
                "reports/report.json",
                "reports/done.marker",
                "screenshots/forest-lantern.png",
                "controller/original-client.log",
                "controller/verification.json",
            },
        )
        for relative_name, pinned in archived_files.items():
            relative_path = PurePosixPath(relative_name)
            self.assertFalse(relative_path.is_absolute())
            self.assertNotIn("..", relative_path.parts)
            client.verify_exact_file(
                TRACKED_FOREST_LANTERN_EVIDENCE_ARCHIVE
                / Path(*relative_path.parts),
                pinned["sha256"],
                pinned["size"],
                f"Tracked original Forest Lantern archive file {relative_name}",
            )
        report = client.load_json_object(
            TRACKED_FOREST_LANTERN_EVIDENCE_ARCHIVE
            / "reports"
            / "report.json",
            "Tracked original Forest Lantern scenario report",
        )
        self.assertEqual(report["status"], "passed")
        self.assertEqual(report["reference_id"], "published-0.1.7")
        self.assertEqual(report["scenario"], "forest-lantern")
        self.assertEqual(len(report["assertions"]), 36)
        self.assertTrue(all(value["passed"] is True for value in report["assertions"]))
        self.assertEqual(report["mechanics"]["fixture_state_count"], 20)
        self.assertEqual(
            client.png_dimensions(
                TRACKED_FOREST_LANTERN_EVIDENCE_ARCHIVE
                / "screenshots"
                / "forest-lantern.png"
            ),
            (1920, 1080),
        )
        marker = (
            TRACKED_FOREST_LANTERN_EVIDENCE_ARCHIVE
            / "reports"
            / "done.marker"
        ).read_text(encoding="ascii")
        self.assertEqual(
            marker,
            "forest-lantern:passed\n"
            f"report_sha256:{archived_files['reports/report.json']['sha256']}\n",
        )
        verification = client.load_json_object(
            TRACKED_FOREST_LANTERN_EVIDENCE_ARCHIVE
            / "controller"
            / "verification.json",
            "Tracked original Forest Lantern controller verification",
        )
        self.assertEqual(verification["status"], "passed")
        self.assertEqual(verification["scenario"], "forest-lantern")
        self.assertEqual(
            verification["screenshot"]["sha256"],
            archived_files["screenshots/forest-lantern.png"]["sha256"],
        )
        self.assertFalse(
            archive_manifest["mutable_launcher_outputs"]["skin_cache_present"]
        )

    def test_tracked_original_attrahite_archive_is_exact(self) -> None:
        archive_manifest = client.load_json_object(
            TRACKED_ATTRAHITE_EVIDENCE_ARCHIVE / "archive-manifest.json",
            "Tracked original Attrahite evidence archive manifest",
        )
        self.assertEqual(
            archive_manifest["kind"],
            "etherology-original-fabric-baseline-evidence",
        )
        self.assertEqual(
            archive_manifest["scenario"],
            "attrahite-block-registry",
        )
        self.assertEqual(archive_manifest["assertion_count"], 49)
        self.assertEqual(archive_manifest["screenshot_count"], 1)
        self.assertEqual(
            archive_manifest["profile"]["id"],
            "etherology-original-fabric-1.21.1-published-0.1.7-v4",
        )
        client.verify_exact_file(
            TRACKED_MANIFEST_PATH,
            archive_manifest["profile"]["manifest_sha256"],
            archive_manifest["profile"]["manifest_size"],
            "Tracked original Attrahite profile manifest",
        )
        archived_files = archive_manifest["files"]
        self.assertEqual(
            set(archived_files),
            {
                "reports/report.json",
                "reports/done.marker",
                "screenshots/attrahite-block-registry.png",
                "controller/original-client.log",
                "controller/verification.json",
            },
        )
        for relative_name, pinned in archived_files.items():
            relative_path = PurePosixPath(relative_name)
            self.assertFalse(relative_path.is_absolute())
            self.assertNotIn("..", relative_path.parts)
            client.verify_exact_file(
                TRACKED_ATTRAHITE_EVIDENCE_ARCHIVE
                / Path(*relative_path.parts),
                pinned["sha256"],
                pinned["size"],
                f"Tracked original Attrahite archive file {relative_name}",
            )
        report = client.load_json_object(
            TRACKED_ATTRAHITE_EVIDENCE_ARCHIVE
            / "reports"
            / "report.json",
            "Tracked original Attrahite scenario report",
        )
        self.assertEqual(report["status"], "passed")
        self.assertEqual(report["reference_id"], "published-0.1.7")
        self.assertEqual(report["scenario"], "attrahite-block-registry")
        self.assertEqual(len(report["assertions"]), 49)
        self.assertTrue(all(value["passed"] is True for value in report["assertions"]))
        self.assertEqual(report["mechanics"]["gallery_block_count"], 4)
        self.assertEqual(report["mechanics"]["recipe_count"], 9)
        self.assertEqual(report["mechanics"]["loot_table_count"], 4)
        self.assertEqual(report["mechanics"]["fortune_level"], 3)
        self.assertEqual(
            client.png_dimensions(
                TRACKED_ATTRAHITE_EVIDENCE_ARCHIVE
                / "screenshots"
                / "attrahite-block-registry.png"
            ),
            (1920, 1080),
        )
        marker = (
            TRACKED_ATTRAHITE_EVIDENCE_ARCHIVE
            / "reports"
            / "done.marker"
        ).read_text(encoding="ascii")
        self.assertEqual(
            marker,
            "attrahite-block-registry:passed\n"
            f"report_sha256:{archived_files['reports/report.json']['sha256']}\n",
        )
        verification = client.load_json_object(
            TRACKED_ATTRAHITE_EVIDENCE_ARCHIVE
            / "controller"
            / "verification.json",
            "Tracked original Attrahite controller verification",
        )
        self.assertEqual(verification["status"], "passed")
        self.assertEqual(verification["scenario"], "attrahite-block-registry")
        self.assertEqual(
            verification["screenshot"]["sha256"],
            archived_files["screenshots/attrahite-block-registry.png"]["sha256"],
        )
        self.assertFalse(
            archive_manifest["mutable_launcher_outputs"]["skin_cache_present"]
        )

    def test_tracked_manifest_and_bundle_validate(self) -> None:
        configuration = client.load_configuration()
        inventory = client.verify_reference_bundle(configuration)
        client.verify_harness_artifact(configuration)
        self.assertEqual(
            client.profile_spec(configuration)["id"],
            "etherology-original-fabric-1.21.1-published-0.1.7-v9",
        )
        self.assertEqual(
            client.scenario_spec(configuration)["id"],
            "slitherite-block-registry",
        )
        verifier = client.load_slitherite_evidence_verifier()
        self.assertEqual(len(verifier.EXPECTED_ASSERTION_NAMES), 185)
        bound_verifier = client.verify_slitherite_evidence_verifier_binding(
            configuration
        )
        self.assertEqual(
            bound_verifier.PROFILE_ID,
            "etherology-original-fabric-1.21.1-published-0.1.7-v9",
        )
        self.assertEqual(
            ATTRAHITE_EVIDENCE_ARCHIVE_DIRECTORY_NAME,
            "attrahite-block-registry-v4",
        )
        self.assertEqual(len(inventory), 8)
        self.assertEqual(
            client.bundle_spec(configuration)["sha256"],
            "764bb85e398c3dca43f68a0d2c1eeecd0f3523da7a48adf48bcc5a967af4b91b",
        )

    def test_tracked_runtime_authority_pins_are_exact(self) -> None:
        configuration = client.load_configuration()
        runtime = client.runtime_spec(configuration)
        metadata = client.require_object(runtime, "metadata")
        self.assertEqual(
            {
                "url": metadata["url"],
                "size": metadata["size"],
                "sha1": metadata["sha1"],
                "sha256": metadata["sha256"],
            },
            {
                "url": "https://piston-meta.mojang.com/v1/packages/"
                "7d538f57bd7f3d0638755fa605a432494e736db3/1.21.1.json",
                "size": 38408,
                "sha1": "7d538f57bd7f3d0638755fa605a432494e736db3",
                "sha256": "06ba60da1f8c6fe469bed1b348a064648bfdda57fc2fb56920e3f177f844fdfd",
            },
        )
        self.assertEqual(
            metadata["asset_index"],
            {
                "id": "17",
                "url": "https://piston-meta.mojang.com/v1/packages/"
                "1f22f8f4b4575831ce8d1d74b5656eb1bc04732c/17.json",
                "size": 449557,
                "total_size": 824474118,
                "sha1": "1f22f8f4b4575831ce8d1d74b5656eb1bc04732c",
                "sha256": "8779cb0b10b5edbe494b69a3c5aaf823e1489b22497f40a985f5c35688eba635",
            },
        )
        self.assertEqual(
            metadata["client"],
            {
                "url": "https://piston-data.mojang.com/v1/objects/"
                "30c73b1c5da787909b2f73340419fdf13b9def88/client.jar",
                "size": 26836906,
                "sha1": "30c73b1c5da787909b2f73340419fdf13b9def88",
                "sha256": "499f6897d1837516680f3114072d8106e11c9adcd933fe5cf051b551089b0c99",
            },
        )
        fabric_profile = client.require_object(runtime, "fabric_profile")
        self.assertEqual(
            fabric_profile["url"],
            "https://meta.fabricmc.net/v2/versions/loader/"
            "1.21.1/0.17.3/profile/json",
        )
        self.assertEqual(fabric_profile["size"], 2847)
        self.assertEqual(
            fabric_profile["sha256"],
            "95904f86cb85064223216f6ff5cd14517bd1a30169f93d88a2857654979561bf",
        )
        self.assertEqual(
            fabric_profile["snapshot"],
            {
                "path": "scripts/baseline/fixtures/"
                "fabric-loader-0.17.3-1.21.1-profile.json",
                "size": 2848,
                "sha256": "089b5998a295e3ba5ab6119354f76d2cb57fca4050806cc10329523fb480b9cb",
            },
        )
        fabric_response = client.verify_tracked_fabric_profile_snapshot(configuration)
        self.assertEqual(len(fabric_response), 2847)
        self.assertEqual(
            hashlib.sha256(fabric_response).hexdigest(),
            "95904f86cb85064223216f6ff5cd14517bd1a30169f93d88a2857654979561bf",
        )
        self.assertEqual(
            [library["coordinate"] for library in fabric_profile["libraries"]],
            [
                "org.ow2.asm:asm:9.9",
                "org.ow2.asm:asm-analysis:9.9",
                "org.ow2.asm:asm-commons:9.9",
                "org.ow2.asm:asm-tree:9.9",
                "org.ow2.asm:asm-util:9.9",
                "net.fabricmc:sponge-mixin:0.16.5+mixin.0.8.7",
                "net.fabricmc:intermediary:1.21.1",
                "net.fabricmc:fabric-loader:0.17.3",
            ],
        )

    def test_manifest_has_no_external_profile_contract(self) -> None:
        content = TRACKED_MANIFEST_PATH.read_text(encoding="utf-8").casefold()
        self.assertNotIn("modrinthapp", content)
        self.assertNotIn("source_profile", content)
        self.assertNotIn("app_root", content)
        self.assertNotIn("quick-skin", content)

    def test_tracked_fabric_snapshot_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            snapshot_path = configuration.fabric_profile_snapshot_path
            external = temporary_root / "external-fabric-profile.json"
            snapshot_path.replace(external)
            snapshot_path.symlink_to(external)
            with self.assertRaises(client.BaselineError):
                client.verify_tracked_fabric_profile_snapshot(configuration)

    def test_tracked_fabric_snapshot_requires_one_repository_newline(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            snapshot_path = configuration.fabric_profile_snapshot_path
            content = snapshot_path.read_bytes() + b"\n"
            snapshot_path.write_bytes(content)
            fabric_profile = client.require_object(
                client.runtime_spec(configuration), "fabric_profile"
            )
            snapshot = client.require_object(fabric_profile, "snapshot")
            snapshot["size"] = len(content)
            snapshot["sha256"] = hashlib.sha256(content).hexdigest()
            with self.assertRaisesRegex(client.BaselineError, "exactly one newline"):
                client.verify_tracked_fabric_profile_snapshot(configuration)

    def test_parser_has_no_profile_adoption_or_deletion_actions(self) -> None:
        with mock.patch("sys.stderr", io.StringIO()):
            for arguments in (
                ["adopt"],
                ["reset"],
                ["delete"],
                ["validate", "--profile", "/tmp/foreign"],
            ):
                with self.subTest(arguments=arguments):
                    with self.assertRaises(SystemExit):
                        client.parse_arguments(arguments)


class ManifestAndPathSafetyTests(unittest.TestCase):
    def test_runtime_traversal_is_rejected(self) -> None:
        manifest = json.loads(TRACKED_MANIFEST_PATH.read_text(encoding="utf-8"))
        manifest["profile"]["runtime_directory"] = "../foreign"
        with self.assertRaises(client.BaselineError):
            client.validate_manifest_shape(manifest)

    def test_bundle_symlink_is_rejected_before_read(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, manifest_path, _ = reference_fixture(temporary_root)
            bundle_path = configuration.bundle_path
            external = temporary_root / "external.mrpack"
            bundle_path.replace(external)
            bundle_path.symlink_to(external)
            with self.assertRaises(client.BaselineError):
                client.load_configuration(manifest_path, configuration.repository_root)

    def test_repository_path_traversal_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            with self.assertRaises(client.BaselineError):
                client.safe_repository_path(root, "../outside", "fixture")

    def test_owned_runtime_rejects_foreign_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            runtimes_root, root = owned_runtime_fixture(configuration, temporary_root)
            client.verify_owned_runtime(configuration, root, runtimes_root)
            marker = client.load_json_object(
                client.marker_path(configuration, root), "fixture marker"
            )
            marker["managed_by"] = "foreign-controller"
            write_json(client.marker_path(configuration, root), marker)
            with self.assertRaises(client.BaselineError):
                client.verify_owned_runtime(configuration, root, runtimes_root)

    def test_owned_runtime_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            runtimes_root = temporary_root / "state" / "runtimes"
            runtimes_root.mkdir(parents=True)
            external = temporary_root / "foreign"
            external.mkdir()
            root = client.runtime_root(configuration, runtimes_root)
            root.symlink_to(external, target_is_directory=True)
            with self.assertRaises(client.BaselineError):
                client.verify_owned_runtime(configuration, root, runtimes_root)


class BundleSafetyTests(unittest.TestCase):
    def test_outer_hash_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, manifest_path, _ = reference_fixture(temporary_root)
            manifest = client.load_json_object(manifest_path, "fixture manifest")
            manifest["reference_bundle"]["sha256"] = "0" * 64
            write_json(manifest_path, manifest)
            configuration = client.load_configuration(
                manifest_path, configuration.repository_root
            )
            with self.assertRaises(client.BaselineError):
                client.verify_reference_bundle(configuration)

    def test_member_hash_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, manifest_path, _ = reference_fixture(temporary_root)
            manifest = client.load_json_object(manifest_path, "fixture manifest")
            manifest["reference_bundle"]["members"][0]["sha256"] = "f" * 64
            write_json(manifest_path, manifest)
            configuration = client.load_configuration(
                manifest_path, configuration.repository_root
            )
            with self.assertRaises(client.BaselineError):
                client.verify_reference_bundle(configuration)

    def test_extra_archive_member_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            configuration, _, _ = reference_fixture(
                Path(temporary_directory),
                extra_entry=("overrides/mods/foreign.jar", fabric_jar_bytes("foreign")),
            )
            with self.assertRaises(client.BaselineError):
                client.verify_reference_bundle(configuration)

    def test_unsafe_archive_path_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            configuration, _, _ = reference_fixture(
                Path(temporary_directory), extra_entry=("../escape.txt", b"unsafe")
            )
            with self.assertRaises(client.BaselineError):
                client.verify_reference_bundle(configuration)

    def test_nested_forbidden_mod_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            configuration, _, _ = reference_fixture(
                Path(temporary_directory), nested_forbidden_mod_id="quickskin"
            )
            with self.assertRaises(client.BaselineError):
                client.verify_reference_bundle(configuration)

    def test_staged_inventory_rejects_extra_and_linked_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, contents = reference_fixture(temporary_root)
            mods = temporary_root / "mods"
            mods.mkdir()
            for member in client.member_specs(configuration):
                archive_path = str(member["archive_path"])
                (mods / str(member["file_name"])).write_bytes(contents[archive_path])
            harness = client.harness_spec(configuration)
            (mods / str(harness["file_name"])).write_bytes(
                contents[str(harness["file_name"])]
            )
            client.verify_mod_inventory(configuration, mods)
            extra = mods / "foreign.jar"
            extra.write_bytes(b"foreign")
            with self.assertRaises(client.BaselineError):
                client.verify_mod_inventory(configuration, mods)
            extra.unlink()
            target = mods / str(client.member_specs(configuration)[0]["file_name"])
            target.unlink()
            target.symlink_to(mods / str(client.member_specs(configuration)[1]["file_name"]))
            with self.assertRaises(client.BaselineError):
                client.verify_mod_inventory(configuration, mods)

    def test_staging_preflights_every_target_before_replacing_any_jar(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            members = client.member_specs(configuration)
            mods = client.game_directory(configuration, root) / "mods"
            first_target = mods / str(members[0]["file_name"])
            first_target.write_bytes(b"keep-this-content")
            later_bad_target = mods / str(members[1]["file_name"])
            later_bad_target.unlink()
            later_bad_target.mkdir()

            with mock.patch.object(client, "verify_owned_runtime", return_value=root):
                with mock.patch.object(client, "verify_installed_game"):
                    with mock.patch.object(client, "verify_runtime_lock"):
                        with self.assertRaisesRegex(client.BaselineError, "non-file"):
                            client.stage_reference_members(configuration)

            self.assertEqual(first_target.read_bytes(), b"keep-this-content")


class HarnessSafetyTests(unittest.TestCase):
    def test_harness_hash_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            configuration, _, _ = reference_fixture(Path(temporary_directory))
            configuration.harness_path.write_bytes(b"tampered")

            with self.assertRaises(client.BaselineError):
                client.verify_harness_artifact(configuration)

    def test_harness_production_link_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            configuration, _, _ = reference_fixture(
                Path(temporary_directory), harness_production_link=True
            )

            with self.assertRaisesRegex(client.BaselineError, "implementation code"):
                client.verify_harness_artifact(configuration)

    def test_harness_symlink_is_rejected_before_read(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, manifest_path, _ = reference_fixture(temporary_root)
            external = temporary_root / "foreign-harness.jar"
            configuration.harness_path.replace(external)
            configuration.harness_path.symlink_to(external)

            with self.assertRaises(client.BaselineError):
                client.load_configuration(
                    manifest_path, configuration.repository_root
                )


class CaptureContractTests(unittest.TestCase):
    @staticmethod
    def prerequisite_patches(root: Path) -> ExitStack:
        stack = ExitStack()
        stack.enter_context(mock.patch.object(client, "verify_reference_bundle"))
        stack.enter_context(mock.patch.object(client, "verify_harness_artifact"))
        stack.enter_context(
            mock.patch.object(client, "verify_owned_runtime", return_value=root)
        )
        stack.enter_context(mock.patch.object(client, "verify_installed_game"))
        stack.enter_context(mock.patch.object(client, "verify_runtime_lock"))
        stack.enter_context(mock.patch.object(client, "assert_runtime_not_running"))
        return stack

    def test_fresh_capture_layout_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)

            client.verify_capture_layout(configuration, root, require_fresh=True)

    def test_preexisting_evidence_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            client.report_path(configuration, root).write_text(
                "existing\n", encoding="utf-8"
            )

            with self.assertRaisesRegex(client.BaselineError, "overwrite"):
                client.verify_capture_layout(configuration, root, require_fresh=True)

    def test_stage_fails_before_mutation_when_evidence_exists(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            client.report_path(configuration, root).write_text(
                "existing\n", encoding="utf-8"
            )

            with self.prerequisite_patches(root):
                with self.assertRaisesRegex(client.BaselineError, "overwrite"):
                    client.stage_reference_members(configuration)

    def test_check_fails_before_java_resolution_when_evidence_exists(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            client.report_path(configuration, root).write_text(
                "existing\n", encoding="utf-8"
            )

            with self.prerequisite_patches(root):
                with mock.patch.object(client, "verify_staged_reference"):
                    with mock.patch.object(client, "resolve_java_21") as resolve_java:
                        with self.assertRaisesRegex(client.BaselineError, "overwrite"):
                            client.check_environment(
                                configuration, "attrahite-block-registry"
                            )
                        resolve_java.assert_not_called()

    def test_preexisting_world_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            client.save_directory(configuration, root).mkdir(parents=True)

            with self.assertRaisesRegex(client.BaselineError, "reuse"):
                client.verify_capture_layout(configuration, root, require_fresh=True)

    def test_preexisting_launch_attempt_seal_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_json(client.launch_attempt_path(configuration, root), {"attempted": True})

            with self.assertRaisesRegex(client.BaselineError, "launch-attempt seal"):
                client.verify_capture_layout(configuration, root, require_fresh=True)

    def test_linked_capture_directory_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            reports = client.reports_directory(configuration, root)
            external = temporary_root / "external-reports"
            external.mkdir()
            reports.rmdir()
            reports.symlink_to(external, target_is_directory=True)

            with self.assertRaises(client.BaselineError):
                client.verify_capture_layout(configuration, root, require_fresh=True)

    def test_machine_verifier_accepts_exact_passing_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)

            self.assertEqual(
                client.verify_scenario_evidence(configuration, root),
                {
                    "path": "assets/skins",
                    "present": False,
                    "file_count": 0,
                    "total_size": 0,
                    "files": [],
                },
            )

    def test_machine_verifier_accepts_ordered_slitherite_v9_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(
                temporary_root,
                source_manifest_path=ACTIVE_MANIFEST_PATH,
                fixture_manifest_name=ACTIVE_MANIFEST_PATH.name,
            )
            _, root = owned_runtime_fixture(configuration, temporary_root)
            attempt_path = write_launch_attempt_fixture(configuration, root)
            fixture_scenario, fixture_world, report, _ = (
                slitherite_evidence_fixture.contract_fixture(
                    temporary_root / "slitherite-contract"
                )
            )
            scenario = client.scenario_root(configuration, root)
            for screenshot in (fixture_scenario / "screenshots").iterdir():
                shutil.copy2(
                    screenshot,
                    client.screenshots_directory(configuration, root)
                    / screenshot.name,
                )
            shutil.copytree(
                fixture_world,
                client.save_directory(configuration, root),
            )

            etherology = next(
                member
                for member in client.member_specs(configuration)
                if member["mod_id"] == "etherology"
            )
            harness = client.harness_spec(configuration)
            report["artifacts"] = [
                {
                    "mod_id": "etherology",
                    "origin_kind": "PATH",
                    "file_name": etherology["file_name"],
                    "size": etherology["size"],
                    "sha256": etherology["sha256"],
                },
                {
                    "mod_id": harness["mod_id"],
                    "origin_kind": "PATH",
                    "file_name": harness["file_name"],
                    "size": harness["size"],
                    "sha256": harness["sha256"],
                },
            ]
            report_file = client.report_path(configuration, root)
            marker = client.completion_marker_path(configuration, root)
            write_json(report_file, report)
            marker.write_text(
                "slitherite-block-registry:passed\n"
                f"report_sha256:{client.sha256_file(report_file)}\n",
                encoding="utf-8",
            )
            initial, reopened = client.screenshot_paths(configuration, root)
            base_time = time.time_ns() - 5_000_000_000
            for path, offset in (
                (attempt_path, 0),
                (initial, 1_000_000),
                (reopened, 2_000_000),
                (report_file, 3_000_000),
                (marker, 4_000_000),
            ):
                os.utime(path, ns=(base_time + offset, base_time + offset))

            lifecycle = {"status": "passed", "scenario": scenario.name}
            with mock.patch.object(
                client,
                "decode_png",
                return_value=slitherite_evidence_fixture.BRIGHT_IMAGE,
            ):
                with mock.patch.object(client, "assert_image_is_not_blank"):
                    with mock.patch.object(
                        client,
                        "verify_game_lifecycle",
                        return_value=lifecycle,
                    ):
                        self.assertEqual(
                            client.verify_scenario_evidence(configuration, root),
                            lifecycle,
                        )

    def test_machine_verifier_rejects_named_attrahite_class_aliases(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            report = client.load_json_object(
                client.report_path(configuration, root), "fixture report"
            )
            assertion = next(
                value
                for value in report["assertions"]
                if value["name"] == "attrahite_block_classes_exact"
            )
            assertion["actual"] = assertion["actual"].replace(
                "net.minecraft.class_2248",
                "net.minecraft.block.Block",
            )
            rewrite_report_and_marker(configuration, root, report)

            with self.assertRaisesRegex(
                client.BaselineError, "registry description changed"
            ):
                client.verify_scenario_evidence(configuration, root)

    def test_successful_run_record_binds_mutable_skin_cache(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            skin_cache = client.verify_scenario_evidence(configuration, root)
            controller_log = (
                client.logs_directory(configuration, root) / "original-client-fixture.log"
            )
            controller_log.write_bytes(b"fixture controller log\n")

            verification = client.write_successful_run_verification(
                configuration, root, controller_log, skin_cache
            )
            descriptor = client.load_json_object(
                verification, "fixture successful-run verification"
            )
            self.assertEqual(descriptor["status"], "passed")
            self.assertEqual(
                descriptor["mutable_launcher_outputs"],
                {"skin_cache": skin_cache},
            )

    def test_machine_verifier_rejects_tampered_screenshot(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            screenshot = client.screenshot_path(configuration, root)
            screenshot.write_bytes(screenshot.read_bytes() + b"tampered")

            with self.assertRaises(client.BaselineError):
                client.verify_scenario_evidence(configuration, root)

    def test_machine_verifier_rejects_failed_report(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            report = client.load_json_object(
                client.report_path(configuration, root), "fixture report"
            )
            report["status"] = "failed"
            write_json(client.report_path(configuration, root), report)

            with self.assertRaisesRegex(client.BaselineError, "did not pass"):
                client.verify_scenario_evidence(configuration, root)

    def test_machine_verifier_rejects_blank_screenshot(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            scenario = client.scenario_spec(configuration)
            framebuffer = scenario["framebuffer"]
            screenshot = client.screenshot_path(configuration, root)
            blank_content = png_bytes(
                framebuffer["width"], framebuffer["height"], blank=True
            )
            screenshot.write_bytes(blank_content)
            report = client.load_json_object(
                client.report_path(configuration, root), "fixture report"
            )
            screenshot_node = report["screenshots"][0]
            screenshot_node["size"] = len(blank_content)
            screenshot_node["sha256"] = hashlib.sha256(blank_content).hexdigest()
            for assertion in report["assertions"]:
                if assertion["name"] == "native_screenshot_written":
                    assertion["actual"] = (
                        f"{len(blank_content)} bytes, sha256="
                        f"{hashlib.sha256(blank_content).hexdigest()}"
                    )
            rewrite_report_and_marker(configuration, root, report)

            with self.assertRaisesRegex(client.BaselineError, "blank or near-uniform"):
                client.verify_scenario_evidence(configuration, root)

    def test_machine_verifier_rejects_empty_world_proof(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            (client.save_directory(configuration, root) / "level.dat").write_bytes(b"")

            with self.assertRaisesRegex(client.BaselineError, "proof file"):
                client.verify_scenario_evidence(configuration, root)

    def test_machine_verifier_rejects_reordered_assertions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            report = client.load_json_object(
                client.report_path(configuration, root), "fixture report"
            )
            report["assertions"][0], report["assertions"][1] = (
                report["assertions"][1],
                report["assertions"][0],
            )
            rewrite_report_and_marker(configuration, root, report)

            with self.assertRaisesRegex(client.BaselineError, "order/inventory"):
                client.verify_scenario_evidence(configuration, root)

    def test_machine_verifier_rejects_marker_published_early(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            marker = client.completion_marker_path(configuration, root)
            screenshot_time = client.screenshot_path(configuration, root).stat().st_mtime_ns
            os.utime(marker, ns=(screenshot_time - 1, screenshot_time - 1))

            with self.assertRaisesRegex(client.BaselineError, "published in"):
                client.verify_scenario_evidence(configuration, root)

    def test_machine_verifier_rejects_tampered_launcher_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            library = client.launcher_directory(configuration, root) / "libraries" / (
                "fixture.jar"
            )
            library.write_bytes(b"tampered")

            with self.assertRaises(client.BaselineError):
                client.verify_scenario_evidence(configuration, root)

    def test_provision_time_runtime_lock_rejects_later_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_launch_attempt_fixture(configuration, root)
            lock_path = client.runtime_lock_path(configuration, root)
            write_json(lock_path, client.runtime_lock_descriptor(configuration, root))
            client.verify_runtime_lock(configuration, root)
            first_native = client.native_classifier_inventory(configuration, root)[0]
            native = client.launcher_directory(configuration, root) / Path(
                *PurePosixPath(str(first_native["path"])).parts
            )
            native.write_bytes(b"tampered")

            with self.assertRaises(client.BaselineError):
                client.verify_runtime_lock(configuration, root)

    def test_machine_verifier_requires_exact_origin_region_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            write_passing_evidence(configuration, root)
            region = client.save_directory(configuration, root) / "region"
            (region / "r.0.0.mca").rename(region / "r.1.0.mca")

            with self.assertRaisesRegex(client.BaselineError, "region/r.0.0.mca"):
                client.verify_scenario_evidence(configuration, root)


class RuntimeAuthorityTests(unittest.TestCase):
    def test_skin_cache_is_bounded_mutable_output_not_an_immutable_input(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            immutable_before = client.launcher_file_inventory(configuration, root)
            cache_file = (
                client.skin_cache_directory(configuration, root)
                / "ab"
                / ("ab" + "0" * 38)
            )
            cache_file.parent.mkdir(parents=True)
            cache_file.write_bytes(b"network-populated-skin-cache")

            self.assertEqual(
                client.launcher_file_inventory(configuration, root), immutable_before
            )
            self.assertEqual(
                client.runtime_skin_cache_descriptor(configuration, root),
                {
                    "path": "assets/skins",
                    "present": True,
                    "file_count": 1,
                    "total_size": len(b"network-populated-skin-cache"),
                    "files": [
                        {
                            "path": "ab/" + "ab" + "0" * 38,
                            "size": len(b"network-populated-skin-cache"),
                            "sha256": hashlib.sha256(
                                b"network-populated-skin-cache"
                            ).hexdigest(),
                        }
                    ],
                },
            )
            with self.assertRaisesRegex(client.BaselineError, "assets/skins"):
                client.prelaunch_profile_descriptor(configuration, root)

    def test_runtime_skin_cache_rejects_a_linked_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            external = temporary_root / "foreign-skin-cache"
            external.mkdir()
            cache_root = client.skin_cache_directory(configuration, root)
            cache_root.symlink_to(external, target_is_directory=True)

            with self.assertRaises(client.BaselineError):
                client.runtime_skin_cache_descriptor(configuration, root)

    def test_download_hash_failure_removes_partial_destination(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            destination = Path(temporary_directory) / "download.bin"
            with mock.patch.object(
                client.urllib.request,
                "urlopen",
                return_value=io.BytesIO(b"wrong bytes"),
            ):
                with self.assertRaises(client.BaselineError):
                    client.download_pinned_file(
                        "https://example.invalid/download.bin",
                        destination,
                        hashlib.sha256(b"expected bytes").hexdigest(),
                        "fixture download",
                        len(b"expected bytes"),
                    )
            self.assertFalse(destination.exists())

    def test_fabric_libraries_use_only_exact_repository_cache_entries(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            root = temporary_root / "runtime"
            root.mkdir()
            client.launcher_directory(configuration, root).mkdir()
            cache_root = client.pinned_fabric_library_cache_root(configuration)
            libraries = client.require_list(
                client.require_object(
                    client.runtime_spec(configuration), "fabric_profile"
                ),
                "libraries",
            )
            for library in libraries:
                relative_path = PurePosixPath(str(library["path"]))
                cache_path = cache_root / Path(*relative_path.parts)
                cache_path.parent.mkdir(parents=True, exist_ok=True)
                cache_path.write_bytes(
                    fixture_library_content(str(library["coordinate"]))
                )

            with mock.patch.object(client, "download_pinned_file") as download:
                client.install_pinned_fabric_libraries(configuration, root)

            download.assert_not_called()
            self.assertEqual(
                len(client.fabric_library_inventory(configuration, root)),
                len(libraries),
            )

    def test_tampered_fabric_library_cache_entry_fails_and_cleans_destination(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            root = temporary_root / "runtime"
            root.mkdir()
            launcher = client.launcher_directory(configuration, root)
            launcher.mkdir()
            library = client.require_list(
                client.require_object(
                    client.runtime_spec(configuration), "fabric_profile"
                ),
                "libraries",
            )[0]
            relative_path = PurePosixPath(str(library["path"]))
            cache_path = client.pinned_fabric_library_cache_root(
                configuration
            ) / Path(*relative_path.parts)
            cache_path.parent.mkdir(parents=True, exist_ok=True)
            cache_path.write_bytes(b"x" * int(library["size"]))

            with self.assertRaisesRegex(client.BaselineError, "SHA-256"):
                client.install_pinned_fabric_libraries(configuration, root)

            self.assertFalse(
                (launcher / "libraries" / Path(*relative_path.parts)).exists()
            )

    def test_linked_fabric_library_cache_root_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            root = temporary_root / "runtime"
            root.mkdir()
            client.launcher_directory(configuration, root).mkdir()
            external = temporary_root / "foreign-cache"
            external.mkdir()
            client.pinned_fabric_library_cache_root(configuration).symlink_to(
                external,
                target_is_directory=True,
            )

            with self.assertRaisesRegex(client.BaselineError, "library cache"):
                client.install_pinned_fabric_libraries(configuration, root)

    def test_fabric_library_inventory_rejects_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            first_library = client.fabric_library_inventory(configuration, root)[0]
            path = client.launcher_directory(configuration, root) / Path(
                *PurePosixPath(str(first_library["path"])).parts
            )
            path.write_bytes(b"tampered")

            with self.assertRaises(client.BaselineError):
                client.fabric_library_inventory(configuration, root)

    def test_native_classifier_inventory_rejects_missing_jar(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            first_native = client.native_classifier_inventory(configuration, root)[0]
            path = client.launcher_directory(configuration, root) / Path(
                *PurePosixPath(str(first_native["path"])).parts
            )
            path.unlink()

            with self.assertRaisesRegex(client.BaselineError, "Native classifier"):
                client.native_classifier_inventory(configuration, root)

    def test_prelaunch_profile_rejects_options_and_config_contamination(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            client.prelaunch_profile_descriptor(configuration, root)
            options_path = client.game_directory(configuration, root) / "options.txt"
            options_path.write_text("fullscreen:true\n", encoding="utf-8")
            with self.assertRaisesRegex(client.BaselineError, "options.txt"):
                client.prelaunch_profile_descriptor(configuration, root)
            options_path.write_bytes(client.expected_options_content(configuration))
            (client.game_directory(configuration, root) / "config" / "foreign.json").write_text(
                "{}\n", encoding="utf-8"
            )
            with self.assertRaisesRegex(client.BaselineError, "config"):
                client.prelaunch_profile_descriptor(configuration, root)

    def test_pinned_metadata_projections_reject_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            vanilla_path = client.vanilla_metadata_path(configuration, root)
            vanilla = client.load_json_object(vanilla_path, "fixture vanilla metadata")
            vanilla["foreign"] = True
            write_json(vanilla_path, vanilla)
            with self.assertRaisesRegex(client.BaselineError, "projection"):
                client.verify_pinned_launcher_metadata(configuration, root)
            vanilla.pop("foreign")
            write_json(vanilla_path, vanilla)
            fabric_path = client.fabric_metadata_path(configuration, root)
            fabric = client.load_json_object(fabric_path, "fixture Fabric metadata")
            fabric["mainClass"] = "foreign.Main"
            write_json(fabric_path, fabric)
            with self.assertRaisesRegex(client.BaselineError, "projection"):
                client.verify_pinned_fabric_metadata(configuration, root)

    def test_stage_rejects_profile_contamination_before_repairing_mod(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            first_member = client.member_specs(configuration)[0]
            first_target = (
                client.game_directory(configuration, root)
                / "mods"
                / str(first_member["file_name"])
            )
            first_target.write_bytes(b"keep-this-tamper")
            (client.game_directory(configuration, root) / "config" / "foreign.json").write_text(
                "{}\n", encoding="utf-8"
            )
            with mock.patch.object(client, "verify_owned_runtime", return_value=root):
                with self.assertRaisesRegex(client.BaselineError, "config"):
                    client.stage_reference_members(configuration)
            self.assertEqual(first_target.read_bytes(), b"keep-this-tamper")


class JavaAndScenarioSafetyTests(unittest.TestCase):
    def test_controlled_termination_signal_raises_and_restores(self) -> None:
        previous_handler = signal.getsignal(signal.SIGTERM)
        with self.assertRaisesRegex(client.BaselineError, "SIGTERM"):
            with client.controlled_termination_signals():
                signal.raise_signal(signal.SIGTERM)
        self.assertEqual(signal.getsignal(signal.SIGTERM), previous_handler)

    def test_termination_signal_is_deferred_across_spawn_assignment(self) -> None:
        assignment_completed = False
        previous_handler = signal.getsignal(signal.SIGTERM)
        with self.assertRaisesRegex(client.BaselineError, "SIGTERM"):
            with client.controlled_termination_signals():
                with client.blocked_termination_signals():
                    signal.raise_signal(signal.SIGTERM)
                    assignment_completed = True
        self.assertTrue(assignment_completed)
        self.assertEqual(signal.getsignal(signal.SIGTERM), previous_handler)

    def test_minecraft_installation_worker_has_a_hard_timeout(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            root = temporary_root / "owned-runtime"
            root.mkdir()
            started = time.monotonic()
            with (
                mock.patch.object(
                    client.multiprocessing,
                    "get_context",
                    wraps=client.multiprocessing.get_context,
                ) as get_context,
                mock.patch.object(
                    client, "PROVISION_INSTALL_TIMEOUT_SECONDS", 0.05
                ),
                mock.patch.object(client, "PROCESS_STOP_TIMEOUT_SECONDS", 0.25),
                mock.patch.object(
                    client,
                    "minecraft_install_worker",
                    hanging_minecraft_install_worker_fixture,
                ),
            ):
                with self.assertRaisesRegex(client.BaselineError, "one-hour worker"):
                    client.install_minecraft_in_owned_worker(configuration, root)
            get_context.assert_called_once_with("spawn")
            self.assertLess(time.monotonic() - started, 3)

    def test_minecraft_installation_worker_propagates_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            root = temporary_root / "owned-runtime"
            root.mkdir()
            with mock.patch.object(
                client,
                "minecraft_install_worker",
                failing_minecraft_install_worker_fixture,
            ):
                with self.assertRaisesRegex(
                    client.BaselineError, "FixtureError: fixture worker failure"
                ):
                    client.install_minecraft_in_owned_worker(configuration, root)

    def test_http_dependency_shadow_is_rejected_before_launcher_execution(self) -> None:
        existing_launcher_modules = {
            name: module for name, module in sys.modules.items()
            if name == "minecraft_launcher_lib"
            or name.startswith("minecraft_launcher_lib.")
        }
        for name in existing_launcher_modules:
            sys.modules.pop(name, None)
        try:
            with tempfile.TemporaryDirectory() as temporary_directory:
                temporary_root = Path(temporary_directory)
                cache_parent = temporary_root / "tmp"
                cache_parent.mkdir()
                shadow_root = temporary_root / "shadow" / "requests"
                shadow_root.mkdir(parents=True)
                shadow_entry = shadow_root / "__init__.py"
                shadow_entry.write_text("raise RuntimeError('must not execute')\n")
                shadow_specification = importlib.util.spec_from_file_location(
                    "requests",
                    shadow_entry,
                    submodule_search_locations=[str(shadow_root)],
                )
                real_find_spec = client.importlib.util.find_spec

                def find_spec(module_name: str, package: str | None = None) -> object:
                    if module_name == "requests":
                        return shadow_specification
                    return real_find_spec(module_name, package)

                with mock.patch.object(
                    client.importlib.util, "find_spec", side_effect=find_spec
                ):
                    with self.assertRaisesRegex(
                        client.BaselineError, "does not select pinned requests"
                    ):
                        client.load_verified_launcher_module(
                            "minecraft_launcher_lib.command",
                            cache_parent / "python-bytecode-cache",
                        )
        finally:
            for name in client.launcher_module_names():
                sys.modules.pop(name, None)
            sys.modules.update(existing_launcher_modules)

    def test_launcher_distribution_record_covers_eager_modules(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            cache_parent = temporary_root / "tmp"
            cache_parent.mkdir()
            descriptor = client.verify_launcher_library(
                cache_parent / "python-bytecode-cache"
            )
        paths = {entry["path"] for entry in descriptor["files"]}
        self.assertEqual(
            descriptor["record"]["sha256"],
            client.PINNED_LAUNCHER_RECORD_SHA256,
        )
        self.assertTrue(
            {
                "minecraft_launcher_lib/__init__.py",
                "minecraft_launcher_lib/install.py",
                "minecraft_launcher_lib/command.py",
                "minecraft_launcher_lib/types.py",
                "minecraft_launcher_lib/_internal_types/install_types.py",
            }.issubset(paths)
        )
        self.assertEqual(
            {
                dependency["distribution"]: (
                    dependency["version"],
                    dependency["record"]["sha256"],
                )
                for dependency in descriptor["http_dependencies"]
            },
            {
                "requests": (
                    "2.34.2",
                    "80a07dc1ca3b1a0c981e49e8dfde15af08a07050a74107fa8d699181d46c2d5c",
                ),
                "urllib3": (
                    "2.7.0",
                    "f254d3adc53473ba7dcd88bda8602dc3bf2193858f95eebf952f077612726290",
                ),
                "certifi": (
                    "2026.6.17",
                    "3472ecda100873d7a0f591a37cd756b029afb3dab5542be6e0fcdd6ae94cdcbb",
                ),
                "idna": (
                    "3.18",
                    "5ef552c0058464669e5c74cb698590607a86fe5e038c6f28b263b8dc71306f8a",
                ),
                "charset-normalizer": (
                    "3.4.9",
                    "478ffdfc59d8a071fdf34931107af09988a9b2b684996ae359887d8b7d030962",
                ),
            },
        )

    def test_launcher_distribution_rejects_unverified_preimport(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            cache_parent = Path(temporary_directory) / "tmp"
            cache_parent.mkdir()
            with mock.patch.object(
                client, "_VERIFIED_LAUNCHER_DISTRIBUTION_SHA256", None
            ):
                with mock.patch.dict(
                    sys.modules, {"minecraft_launcher_lib": mock.Mock()}
                ):
                    with self.assertRaisesRegex(client.BaselineError, "before complete"):
                        client.verify_launcher_library(
                            cache_parent / "python-bytecode-cache"
                        )

    def test_verified_launcher_module_load_uses_pinned_package(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            cache_parent = Path(temporary_directory) / "tmp"
            cache_parent.mkdir()
            command_module = client.load_verified_launcher_module(
                "minecraft_launcher_lib.command",
                cache_parent / "python-bytecode-cache",
            )
            descriptor = client.verify_launcher_distribution()
            package_root = Path(descriptor["package_root"]).resolve(strict=True)
            client.verify_loaded_launcher_module_origins(package_root)
            self.assertTrue(
                client.is_relative_to(
                    Path(command_module.__file__).resolve(strict=True), package_root
                )
            )

    def test_verified_launcher_module_load_rejects_shadow_resolution(self) -> None:
        existing_modules = {
            name: module
            for name, module in sys.modules.items()
            if name == "minecraft_launcher_lib"
            or name.startswith("minecraft_launcher_lib.")
        }
        for name in existing_modules:
            sys.modules.pop(name, None)
        try:
            with tempfile.TemporaryDirectory() as temporary_directory:
                temporary_root = Path(temporary_directory)
                cache_parent = temporary_root / "tmp"
                cache_parent.mkdir()
                shadow_root = temporary_root / "shadow" / "minecraft_launcher_lib"
                shadow_root.mkdir(parents=True)
                shadow_entry = shadow_root / "__init__.py"
                shadow_entry.write_text("raise RuntimeError('must not execute')\n")
                shadow_specification = importlib.util.spec_from_file_location(
                    "minecraft_launcher_lib",
                    shadow_entry,
                    submodule_search_locations=[str(shadow_root)],
                )
                real_find_spec = client.importlib.util.find_spec

                def find_spec(module_name: str, package: str | None = None) -> object:
                    if module_name == "minecraft_launcher_lib":
                        return shadow_specification
                    return real_find_spec(module_name, package)

                with mock.patch.object(
                    client.importlib.util,
                    "find_spec",
                    side_effect=find_spec,
                ):
                    with self.assertRaisesRegex(
                        client.BaselineError,
                        "does not select the verified minecraft-launcher-lib",
                    ):
                        client.load_verified_launcher_module(
                            "minecraft_launcher_lib.command",
                            cache_parent / "python-bytecode-cache",
                        )
        finally:
            for name in client.launcher_module_names():
                sys.modules.pop(name, None)
            sys.modules.update(existing_modules)

    def test_provision_preflights_java_before_creating_runtime_roots(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            runtimes_root = temporary_root / "new-state" / "runtimes"
            with mock.patch.object(
                client,
                "resolve_java_21",
                side_effect=client.BaselineError("no java fixture"),
            ):
                with self.assertRaisesRegex(client.BaselineError, "no java fixture"):
                    client.provision_profile(configuration, runtimes_root)
            self.assertFalse(runtimes_root.exists())
            self.assertFalse(runtimes_root.parent.exists())

    def test_provision_rejects_a_tampered_fabric_snapshot_before_runtime_mutation(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            configuration.fabric_profile_snapshot_path.write_bytes(b"tampered\n")
            runtimes_root = temporary_root / "new-state" / "runtimes"
            with mock.patch.object(client, "resolve_java_21") as resolve_java:
                with self.assertRaisesRegex(client.BaselineError, "Fabric.*snapshot"):
                    client.provision_profile(configuration, runtimes_root)
            resolve_java.assert_not_called()
            self.assertFalse(runtimes_root.exists())
            self.assertFalse(runtimes_root.parent.exists())

    def test_provision_preflights_import_resolution_before_runtime_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            runtimes_root = temporary_root / "new-state" / "runtimes"
            with (
                mock.patch.object(
                    client,
                    "preflight_launcher_import_resolution",
                    side_effect=client.BaselineError("shadowed dependency fixture"),
                ),
                mock.patch.object(client, "resolve_java_21") as resolve_java,
            ):
                with self.assertRaisesRegex(
                    client.BaselineError, "shadowed dependency fixture"
                ):
                    client.provision_profile(configuration, runtimes_root)
            resolve_java.assert_not_called()
            self.assertFalse(runtimes_root.exists())
            self.assertFalse(runtimes_root.parent.exists())

    def test_java_selection_skips_profile_and_non_21_candidates(self) -> None:
        profile_java = Path("/tmp/ModrinthApp/profiles/reference/java")
        java_17 = Path("/tmp/jdks/java-17/bin/java")
        java_21 = Path("/tmp/jdks/java-21/bin/java")
        versions = {
            java_17.resolve(strict=False): 17,
            java_21.resolve(strict=False): 21,
        }
        selected = client.select_java_21(
            [profile_java, java_17, java_21], lambda path: versions.get(path)
        )
        self.assertEqual(selected, java_21.resolve(strict=False))

    def test_java_selection_fails_without_safe_java_21(self) -> None:
        with self.assertRaises(client.BaselineError):
            client.select_java_21(
                [Path("/tmp/instances/reference/java"), Path("/tmp/java17")],
                lambda _path: 17,
            )

    def test_scenario_requires_exact_allowlist_entry(self) -> None:
        configuration = client.load_configuration()
        self.assertEqual(
            client.resolve_scenario_id(configuration, "slitherite-block-registry"),
            "slitherite-block-registry",
        )
        for scenario in (
            None,
            "",
            "slitherite-block-registry ",
            "../slitherite-block-registry",
            "attrahite-block-registry ",
            "../attrahite-block-registry",
            "attrahite-block-registry",
            "forest-lantern",
            "other",
        ):
            with self.subTest(scenario=scenario):
                with self.assertRaises(client.BaselineError):
                    client.resolve_scenario_id(configuration, scenario)

    def test_capture_harness_is_exactly_pinned(self) -> None:
        configuration = client.load_configuration()
        client.require_capture_harness(configuration)
        self.assertEqual(client.harness_spec(configuration)["version"], "1.3.4")
        self.assertEqual(client.harness_spec(configuration)["size"], 218_402)
        self.assertEqual(
            client.harness_spec(configuration)["sha256"],
            "65835ee5a44dc0461c2de701992a69ed3d6465cd37c39bc87c91cef5625953f6",
        )

    def test_manifest_cannot_select_an_unpinned_harness_path(self) -> None:
        manifest = json.loads(TRACKED_MANIFEST_PATH.read_text(encoding="utf-8"))
        manifest["capture"]["harness"]["path"] = "/tmp/foreign.jar"
        with self.assertRaises(client.BaselineError):
            client.validate_manifest_shape(manifest)

    def test_optional_http_dependency_is_rejected_before_launcher_execution(self) -> None:
        existing_launcher_modules = {
            name: module for name, module in sys.modules.items()
            if name == "minecraft_launcher_lib"
            or name.startswith("minecraft_launcher_lib.")
        }
        for name in existing_launcher_modules:
            sys.modules.pop(name, None)
        try:
            with tempfile.TemporaryDirectory() as temporary_directory:
                temporary_root = Path(temporary_directory)
                cache_parent = temporary_root / "tmp"
                cache_parent.mkdir()
                optional_module = temporary_root / "chardet.py"
                optional_module.write_text("raise RuntimeError('must not execute')\n")
                optional_specification = importlib.util.spec_from_file_location(
                    "chardet", optional_module
                )
                real_pathfinder = client.importlib.machinery.PathFinder.find_spec

                def find_spec(
                    module_name: str,
                    path: object = None,
                    target: object = None,
                ) -> object:
                    if module_name == "chardet":
                        return optional_specification
                    return real_pathfinder(module_name, path, target)

                with mock.patch.object(
                    client.importlib.machinery.PathFinder,
                    "find_spec",
                    side_effect=find_spec,
                ):
                    with self.assertRaisesRegex(
                        client.BaselineError, "optional HTTP modules are importable"
                    ):
                        client.load_verified_launcher_module(
                            "minecraft_launcher_lib.command",
                            cache_parent / "python-bytecode-cache",
                        )
        finally:
            for name in client.launcher_module_names():
                sys.modules.pop(name, None)
            sys.modules.update(existing_launcher_modules)


class CommandAndProcessSafetyTests(unittest.TestCase):
    def command_fixture(
        self, configuration: object, temporary_root: Path, scenario: str | None
    ) -> tuple[list[str], Path, Path]:
        root = temporary_root / "runtime"
        launcher = client.launcher_directory(configuration, root)
        game = client.game_directory(configuration, root)
        java = temporary_root / "jdk" / "bin" / "java"
        java.parent.mkdir(parents=True, exist_ok=True)
        java.write_bytes(b"fixture")
        game.mkdir(parents=True)
        client.home_directory(configuration, root).mkdir()
        client.temporary_directory(configuration, root).mkdir()
        populate_runtime_launcher(configuration, root)
        extraction_directory = client.native_extraction_directory(configuration, root)
        classpath = [
            str(launcher / Path(*PurePosixPath(relative_path).parts))
            for relative_path in client.expected_merged_classpath_paths(
                configuration, root
            )
        ]
        command = [
            str(java),
            "-Duser.home=" + str(client.home_directory(configuration, root)),
            "-Djava.io.tmpdir=" + str(client.temporary_directory(configuration, root)),
            "-Djava.library.path=" + str(extraction_directory),
            "-Djna.tmpdir=" + str(extraction_directory),
            "-Dorg.lwjgl.system.SharedLibraryExtractPath="
            + str(extraction_directory),
            "-Dio.netty.native.workdir=" + str(extraction_directory),
            "-cp",
            os.pathsep.join(classpath),
        ]
        if scenario is not None:
            command.append(f"-D{client.SCENARIO_PROPERTY_NAME}={scenario}")
        command.extend(
            [
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--gameDir",
                str(game),
                "--assetsDir",
                str(launcher / "assets"),
                "--assetIndex",
                "17",
                "--version",
                client.version_id(configuration),
                "--username",
                "EtherologyE2E",
                "--uuid",
                client.offline_uuid("EtherologyE2E"),
                "--accessToken",
                client.OFFLINE_ACCESS_TOKEN,
                "--clientId",
                client.OFFLINE_CLIENT_ID,
                "--xuid",
                client.OFFLINE_XUID,
                "--width",
                "960",
                "--height",
                "540",
            ]
        )
        return command, java, root

    def test_launcher_auth_placeholders_are_resolved_to_exact_offline_values(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            java = temporary_root / "jdk" / "bin" / "java"
            root = temporary_root / "runtime"
            generated = [
                str(java),
                "${clientid}",
                "${auth_xuid}",
            ]
            launcher_command = mock.Mock()
            launcher_command.get_minecraft_command.return_value = generated
            with mock.patch.object(
                client,
                "load_verified_launcher_module",
                return_value=launcher_command,
            ):
                command = client.generate_launch_command(
                    configuration,
                    java,
                    root,
                    "attrahite-block-registry",
                )
            self.assertEqual(
                command,
                [str(java), client.OFFLINE_CLIENT_ID, client.OFFLINE_XUID],
            )

    def test_launch_command_is_contained_and_scenario_exact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            command, java, root = self.command_fixture(
                configuration, temporary_root, "attrahite-block-registry"
            )
            client.verify_launch_command(
                configuration, command, java, root, "attrahite-block-registry"
            )
            command.append(f"-D{client.SCENARIO_PROPERTY_NAME}=other")
            with self.assertRaises(client.BaselineError):
                client.verify_launch_command(
                    configuration, command, java, root, "attrahite-block-registry"
                )

    def test_launch_command_rejects_external_classpath(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            command, java, root = self.command_fixture(configuration, temporary_root, None)
            external = temporary_root / "foreign.jar"
            external.write_bytes(b"foreign")
            classpath_index = command.index("-cp") + 1
            command[classpath_index] += os.pathsep + str(external)
            with self.assertRaises(client.BaselineError):
                client.verify_launch_command(configuration, command, java, root)

    def test_launch_command_rejects_a_second_game_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            command, java, root = self.command_fixture(
                configuration, temporary_root, "attrahite-block-registry"
            )
            command.extend(("--gameDir", "/tmp/foreign"))
            with self.assertRaises(client.BaselineError):
                client.verify_launch_command(
                    configuration, command, java, root, "attrahite-block-registry"
                )

    def test_process_state_rejects_external_log(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            state = {
                "schema": 2,
                "profile_id": client.profile_spec(configuration)["id"],
                "pid": 123,
                "process_group_id": 123,
                "version_id": client.version_id(configuration),
                "game_directory": str(client.game_directory(configuration, root)),
                "scenario": "attrahite-block-registry",
                "log": "/tmp/foreign.log",
                "launch_attempt_sha256": "0" * 64,
            }
            write_json(client.process_state_path(configuration, root), state)
            with self.assertRaises(client.BaselineError):
                client.read_process_state(configuration, root)

    def test_stale_process_state_is_never_adopted_or_unlinked(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            configuration, _, _ = reference_fixture(temporary_root)
            _, root = owned_runtime_fixture(configuration, temporary_root)
            log_path = client.logs_directory(configuration, root) / (
                "original-client-20260831T000000Z.log"
            )
            state = {
                "schema": 2,
                "profile_id": client.profile_spec(configuration)["id"],
                "pid": 999999,
                "process_group_id": 999999,
                "version_id": client.version_id(configuration),
                "game_directory": str(client.game_directory(configuration, root)),
                "scenario": "attrahite-block-registry",
                "log": str(log_path),
                "launch_attempt_sha256": "0" * 64,
            }
            state_path = client.process_state_path(configuration, root)
            write_json(state_path, state)

            with self.assertRaisesRegex(client.BaselineError, "never adopts"):
                client.assert_runtime_not_running(configuration, root)
            self.assertTrue(state_path.is_file())

    def test_fatal_log_marker_is_exact(self) -> None:
        self.assertEqual(
            client.find_fatal_log_marker("prefix A mod crashed on startup! suffix"),
            "A mod crashed on startup!",
        )
        self.assertIsNone(client.find_fatal_log_marker("normal shutdown"))


if __name__ == "__main__":
    unittest.main()
