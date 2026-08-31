#!/usr/bin/env python3
"""Provision, launch, and coordinate the isolated Etherology reference client."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import importlib.metadata
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
import uuid
import zipfile


EXPECTED_LAUNCHER_LIBRARY_VERSION = "8.0"
MANIFEST_PATH = Path(__file__).with_name("reference-profile.json")
STATE_ROOT = Path(__file__).with_name(".state")
CURRENT_STATE_PATH = STATE_ROOT / "current.json"
MRPACK_PATH = STATE_ROOT / "Etherology E2E 1.21.1.mrpack"
CAFFEINATE_PATH = Path("/usr/bin/caffeinate")
STOP_TIMEOUT_SECONDS = 20
EXPECTED_WORLD_PACKAGES = {
    "nbtlib": "2.0.4",
    "numpy": "2.4.6",
}
WORLD_REQUIRED_DIRECTORIES = (
    "DIM-1",
    "DIM1",
    "data",
    "datapacks",
    "entities",
    "playerdata",
    "region",
)
GALLERY_DATAPACK_ID = "file/etherology_baseline"


class BaselineError(RuntimeError):
    """Reports a baseline setup or process-lifecycle failure."""


def load_manifest() -> dict[str, object]:
    try:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise BaselineError(f"Cannot read {MANIFEST_PATH}: {exception}") from exception
    if manifest.get("schema") != 2:
        raise BaselineError("Unsupported reference profile manifest schema")
    return manifest


def safe_relative_path(raw_path: object, field_name: str) -> Path:
    if not isinstance(raw_path, str):
        raise BaselineError(f"The manifest {field_name} is invalid")
    path = Path(raw_path)
    if path.is_absolute() or not path.parts or ".." in path.parts:
        raise BaselineError(f"The manifest {field_name} is unsafe")
    return path


def app_root(manifest: dict[str, object]) -> Path:
    return Path.home() / safe_relative_path(manifest.get("app_root"), "app_root")


def profile_path(manifest: dict[str, object]) -> Path:
    return app_root(manifest) / safe_relative_path(manifest.get("profile"), "profile")


def source_profile_path(manifest: dict[str, object]) -> Path:
    source = app_root(manifest) / safe_relative_path(
        manifest.get("source_profile"), "source_profile"
    )
    if source == profile_path(manifest):
        raise BaselineError("The source and isolated profiles must be different directories")
    return source


def metadata_root(manifest: dict[str, object]) -> Path:
    return app_root(manifest) / safe_relative_path(
        manifest.get("metadata_root"), "metadata_root"
    )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_file(path: Path, expected_sha256: str) -> None:
    if not path.is_file():
        raise BaselineError(f"Required baseline file is missing: {path}")
    actual_sha256 = sha256(path)
    if actual_sha256 != expected_sha256:
        raise BaselineError(f"Baseline file changed: {path}")


def mod_specs(manifest: dict[str, object]) -> dict[str, dict[str, str]]:
    raw_specs = manifest.get("mods")
    if not isinstance(raw_specs, dict):
        raise BaselineError("The manifest mods table is invalid")
    specs: dict[str, dict[str, str]] = {}
    for file_name, raw_spec in raw_specs.items():
        if (
            not isinstance(file_name, str)
            or Path(file_name).parts != (file_name,)
            or not file_name.endswith(".jar")
            or not isinstance(raw_spec, dict)
        ):
            raise BaselineError("The manifest contains an invalid mod entry")
        mod_id = raw_spec.get("mod_id")
        expected_sha256 = raw_spec.get("sha256")
        if not isinstance(mod_id, str) or not isinstance(expected_sha256, str):
            raise BaselineError("The manifest contains an invalid mod specification")
        specs[file_name] = {"mod_id": mod_id, "sha256": expected_sha256}
    return specs


def excluded_mods(manifest: dict[str, object]) -> dict[str, str]:
    raw_exclusions = manifest.get("excluded_mods")
    if not isinstance(raw_exclusions, dict):
        raise BaselineError("The manifest excluded_mods table is invalid")
    exclusions: dict[str, str] = {}
    for display_name, mod_id in raw_exclusions.items():
        if not isinstance(display_name, str) or not isinstance(mod_id, str):
            raise BaselineError("The manifest contains an invalid excluded mod entry")
        exclusions[display_name] = mod_id
    return exclusions


def profile_directories(manifest: dict[str, object]) -> list[str]:
    raw_directories = manifest.get("profile_directories")
    if not isinstance(raw_directories, list):
        raise BaselineError("The manifest profile_directories list is invalid")
    directories: list[str] = []
    for directory in raw_directories:
        if (
            not isinstance(directory, str)
            or Path(directory).parts != (directory,)
            or directory in ("", ".", "..")
        ):
            raise BaselineError("The manifest contains an unsafe profile directory")
        directories.append(directory)
    return directories


def profile_options_text(manifest: dict[str, object]) -> str:
    raw_options = manifest.get("options")
    if not isinstance(raw_options, list) or not all(
        isinstance(option, str) and "\n" not in option and "\r" not in option
        for option in raw_options
    ):
        raise BaselineError("The manifest options list is invalid")
    return "\n".join(raw_options) + "\n"


def parse_options(options_text: str) -> dict[str, str]:
    options: dict[str, str] = {}
    for line in options_text.splitlines():
        name, separator, value = line.partition(":")
        if not separator or not name or name in options:
            raise BaselineError("The isolated profile options file is invalid")
        options[name] = value
    return options


def world_spec(manifest: dict[str, object]) -> dict[str, object]:
    raw_spec = manifest.get("world")
    if not isinstance(raw_spec, dict):
        raise BaselineError("The manifest world specification is invalid")

    for field_name in (
        "source_path",
        "source_tree_sha256",
        "save_name",
        "level_name",
        "datapack_tree_sha256",
        "pristine_tree_sha256",
        "provenance_file",
    ):
        if not isinstance(raw_spec.get(field_name), str):
            raise BaselineError(f"The manifest world.{field_name} field is invalid")
    for field_name in (
        "source_file_count",
        "game_type",
        "allow_commands",
        "hardcore",
        "datapack_file_count",
        "pristine_file_count",
    ):
        if type(raw_spec.get(field_name)) is not int:
            raise BaselineError(f"The manifest world.{field_name} field is invalid")

    source_path = Path(str(raw_spec["source_path"]))
    if not source_path.is_absolute() or ".." in source_path.parts:
        raise BaselineError("The manifest world.source_path field is unsafe")
    for field_name in ("save_name", "provenance_file"):
        value = str(raw_spec[field_name])
        if Path(value).parts != (value,) or value in ("", ".", ".."):
            raise BaselineError(f"The manifest world.{field_name} field is unsafe")
    for field_name in (
        "source_tree_sha256",
        "datapack_tree_sha256",
        "pristine_tree_sha256",
    ):
        if re.fullmatch(r"[0-9a-f]{64}", str(raw_spec[field_name])) is None:
            raise BaselineError(f"The manifest world.{field_name} digest is invalid")

    raw_exclusions = raw_spec.get("source_excluded_files")
    if not isinstance(raw_exclusions, list) or not all(
        isinstance(file_name, str)
        and Path(file_name).parts == (file_name,)
        and file_name not in ("", ".", "..")
        for file_name in raw_exclusions
    ):
        raise BaselineError("The manifest world source exclusions are invalid")

    spawn = raw_spec.get("spawn")
    if not isinstance(spawn, dict):
        raise BaselineError("The manifest world spawn specification is invalid")
    for field_name in ("x", "y", "z", "radius"):
        if type(spawn.get(field_name)) is not int:
            raise BaselineError(f"The manifest world spawn.{field_name} field is invalid")
    if type(spawn.get("angle")) not in (int, float):
        raise BaselineError("The manifest world spawn.angle field is invalid")
    return raw_spec


def world_source_path(manifest: dict[str, object]) -> Path:
    return Path(str(world_spec(manifest)["source_path"]))


def world_save_path(manifest: dict[str, object]) -> Path:
    return profile_path(manifest) / "saves" / str(world_spec(manifest)["save_name"])


def tree_fingerprint(root: Path, excluded_files: set[str] | None = None) -> tuple[int, str]:
    if not root.is_dir() or root.is_symlink():
        raise BaselineError(f"World tree is missing or linked: {root}")
    exclusions = excluded_files or set()
    files: list[tuple[str, Path]] = []
    for path in root.rglob("*"):
        relative_path = path.relative_to(root).as_posix()
        if path.is_symlink():
            raise BaselineError(f"World tree must not contain symlinks: {path}")
        if relative_path in exclusions:
            if not path.is_file():
                raise BaselineError(f"Excluded world path is not a regular file: {path}")
            continue
        if path.is_file():
            files.append((relative_path, path))
        elif not path.is_dir():
            raise BaselineError(f"World tree contains an unsupported entry: {path}")

    digest = hashlib.sha256()
    for relative_path, path in sorted(files):
        size = path.stat().st_size
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        digest.update(str(size).encode("ascii"))
        digest.update(b"\0")
        digest.update(bytes.fromhex(sha256(path)))
        digest.update(b"\n")
    return len(files), digest.hexdigest()


def verify_tree_fingerprint(
    root: Path,
    expected_file_count: int,
    expected_sha256: str,
    excluded_files: set[str] | None = None,
) -> None:
    actual_file_count, actual_sha256 = tree_fingerprint(root, excluded_files)
    if actual_file_count != expected_file_count or actual_sha256 != expected_sha256:
        raise BaselineError(
            f"World tree drifted: {root} "
            f"(files={actual_file_count}, sha256={actual_sha256})"
        )


def load_world_nbt_library():
    local_packages = STATE_ROOT / "python"
    if local_packages.is_dir() and str(local_packages) not in sys.path:
        sys.path.insert(0, str(local_packages))
    try:
        import nbtlib
    except ImportError as exception:
        raise BaselineError(
            "World NBT support is missing; install scripts/baseline/world-requirements.txt "
            "under scripts/baseline/.state/python"
        ) from exception
    for package_name, expected_version in EXPECTED_WORLD_PACKAGES.items():
        try:
            actual_version = importlib.metadata.version(package_name)
        except importlib.metadata.PackageNotFoundError as exception:
            raise BaselineError(f"World package is missing: {package_name}") from exception
        if actual_version != expected_version:
            raise BaselineError(
                f"World package {package_name} must be {expected_version}, found {actual_version}"
            )
    return nbtlib


def expected_level_settings(manifest: dict[str, object]) -> dict[str, object]:
    spec = world_spec(manifest)
    spawn = spec["spawn"]
    if not isinstance(spawn, dict):
        raise BaselineError("The verified world spawn specification changed")
    return {
        "LevelName": spec["level_name"],
        "GameType": spec["game_type"],
        "allowCommands": spec["allow_commands"],
        "hardcore": spec["hardcore"],
        "SpawnX": spawn["x"],
        "SpawnY": spawn["y"],
        "SpawnZ": spawn["z"],
        "SpawnAngle": float(spawn["angle"]),
        "spawnRadius": str(spawn["radius"]),
    }


def write_level_dat(document, path: Path) -> None:
    payload = io.BytesIO()
    with gzip.GzipFile(
        filename="",
        mode="wb",
        compresslevel=9,
        fileobj=payload,
        mtime=0,
    ) as compressed:
        document.write(compressed)
    temporary_path = path.with_name(f"{path.name}.{os.getpid()}.tmp")
    descriptor = os.open(temporary_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(payload.getvalue())
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def patch_world_level_files(manifest: dict[str, object], world: Path) -> None:
    nbtlib = load_world_nbt_library()
    settings = expected_level_settings(manifest)
    for file_name in ("level.dat", "level.dat_old"):
        path = world / file_name
        if not path.is_file() or path.is_symlink():
            raise BaselineError(f"World level data is missing or linked: {path}")
        document = nbtlib.load(path)
        data = document.get("Data")
        if not isinstance(data, nbtlib.Compound) or not isinstance(
            data.get("GameRules"), nbtlib.Compound
        ) or not isinstance(
            data.get("DataPacks"), nbtlib.Compound
        ):
            raise BaselineError(f"World level data has an invalid structure: {path}")
        data["LevelName"] = nbtlib.String(settings["LevelName"])
        data["GameType"] = nbtlib.Int(settings["GameType"])
        data["allowCommands"] = nbtlib.Byte(settings["allowCommands"])
        data["hardcore"] = nbtlib.Byte(settings["hardcore"])
        data["SpawnX"] = nbtlib.Int(settings["SpawnX"])
        data["SpawnY"] = nbtlib.Int(settings["SpawnY"])
        data["SpawnZ"] = nbtlib.Int(settings["SpawnZ"])
        data["SpawnAngle"] = nbtlib.Float(settings["SpawnAngle"])
        data["GameRules"]["spawnRadius"] = nbtlib.String(settings["spawnRadius"])
        write_level_dat(document, path)


def verify_world_level_files(manifest: dict[str, object], world: Path) -> None:
    nbtlib = load_world_nbt_library()
    expected = expected_level_settings(manifest)
    for file_name in ("level.dat", "level.dat_old"):
        path = world / file_name
        if not path.is_file() or path.is_symlink():
            raise BaselineError(f"World level data is missing or linked: {path}")
        data = nbtlib.load(path).get("Data")
        if not isinstance(data, nbtlib.Compound) or not isinstance(
            data.get("GameRules"), nbtlib.Compound
        ) or not isinstance(
            data.get("DataPacks"), nbtlib.Compound
        ):
            raise BaselineError(f"World level data has an invalid structure: {path}")
        enabled_datapacks = {
            str(datapack) for datapack in data["DataPacks"].get("Enabled", [])
        }
        disabled_datapacks = {
            str(datapack) for datapack in data["DataPacks"].get("Disabled", [])
        }
        if (
            GALLERY_DATAPACK_ID not in enabled_datapacks
            or GALLERY_DATAPACK_ID in disabled_datapacks
        ):
            raise BaselineError(f"Gallery datapack is not enabled in {path}")
        actual = {
            "LevelName": str(data.get("LevelName")),
            "GameType": int(data.get("GameType")),
            "allowCommands": int(data.get("allowCommands")),
            "hardcore": int(data.get("hardcore")),
            "SpawnX": int(data.get("SpawnX")),
            "SpawnY": int(data.get("SpawnY")),
            "SpawnZ": int(data.get("SpawnZ")),
            "SpawnAngle": float(data.get("SpawnAngle")),
            "spawnRadius": str(data["GameRules"].get("spawnRadius")),
        }
        if actual != expected:
            raise BaselineError(f"World level settings drifted in {path}: {actual}")


def world_provenance(manifest: dict[str, object]) -> dict[str, object]:
    return {
        "schema": 1,
        "managed_by": "scripts/baseline/original_client.py",
        "world": world_spec(manifest),
    }


def verify_required_world_directories(world: Path) -> None:
    for relative_path in WORLD_REQUIRED_DIRECTORIES:
        directory = world / relative_path
        if not directory.is_dir() or directory.is_symlink():
            raise BaselineError(f"World directory is missing or linked: {directory}")


def verify_world_identity(manifest: dict[str, object], world: Path | None = None) -> None:
    spec = world_spec(manifest)
    target = world or world_save_path(manifest)
    if not target.is_dir() or target.is_symlink():
        raise BaselineError(f"Baseline world is missing or linked: {target}")
    verify_required_world_directories(target)

    provenance_path = target / str(spec["provenance_file"])
    if not provenance_path.is_file() or provenance_path.is_symlink():
        raise BaselineError(f"World provenance is missing or linked: {provenance_path}")
    try:
        actual_provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise BaselineError(f"Cannot read world provenance: {exception}") from exception
    if actual_provenance != world_provenance(manifest):
        raise BaselineError(f"World provenance changed: {provenance_path}")

    datapack = target / "datapacks" / "etherology_baseline"
    verify_tree_fingerprint(
        datapack,
        int(spec["datapack_file_count"]),
        str(spec["datapack_tree_sha256"]),
    )
    verify_world_level_files(manifest, target)


def verify_pristine_world(manifest: dict[str, object], world: Path | None = None) -> None:
    spec = world_spec(manifest)
    target = world or world_save_path(manifest)
    verify_world_identity(manifest, target)
    verify_tree_fingerprint(
        target,
        int(spec["pristine_file_count"]),
        str(spec["pristine_tree_sha256"]),
        {str(spec["provenance_file"])},
    )


def verify_source_world(manifest: dict[str, object]) -> None:
    spec = world_spec(manifest)
    source = world_source_path(manifest)
    verify_required_world_directories(source)
    exclusions = {str(file_name) for file_name in spec["source_excluded_files"]}
    for relative_path in exclusions:
        excluded_path = source / relative_path
        if not excluded_path.is_file() or excluded_path.is_symlink():
            raise BaselineError(f"Expected transient source file is missing: {excluded_path}")
    verify_tree_fingerprint(
        source,
        int(spec["source_file_count"]),
        str(spec["source_tree_sha256"]),
        exclusions,
    )


def profile_metadata(manifest: dict[str, object]) -> dict[str, object]:
    profile_name = manifest.get("profile_name")
    minecraft_version = manifest.get("minecraft_version")
    loader = manifest.get("loader")
    loader_version = manifest.get("loader_version")
    version_id = manifest.get("version_id")
    resolution = manifest.get("resolution")
    offline_username = manifest.get("offline_username")
    source_profile = manifest.get("source_profile")
    if not all(
        isinstance(value, str)
        for value in (
            profile_name,
            minecraft_version,
            loader,
            loader_version,
            version_id,
            offline_username,
            source_profile,
        )
    ) or not isinstance(resolution, dict):
        raise BaselineError("The manifest cannot produce profile metadata")
    return {
        "schema": 1,
        "name": profile_name,
        "managed_by": "scripts/baseline/original_client.py",
        "game": {
            "minecraft_version": minecraft_version,
            "loader": loader,
            "loader_version": loader_version,
            "version_id": version_id,
        },
        "launch": {
            "authentication": "deterministic-offline-identity",
            "offline_username": offline_username,
            "resolution": resolution,
        },
        "source": {
            "access": "read-only-copy",
            "profile": source_profile,
        },
        "mods": mod_specs(manifest),
        "excluded_mods": excluded_mods(manifest),
    }


def collect_fabric_mod_ids(archive: zipfile.ZipFile) -> set[str]:
    try:
        metadata = json.loads(archive.read("fabric.mod.json"))
    except (KeyError, json.JSONDecodeError) as exception:
        raise BaselineError("A pinned mod has invalid Fabric metadata") from exception
    mod_id = metadata.get("id")
    if not isinstance(mod_id, str):
        raise BaselineError("A pinned mod has no Fabric mod id")
    mod_ids = {mod_id}
    raw_nested_jars = metadata.get("jars", [])
    if not isinstance(raw_nested_jars, list):
        raise BaselineError(f"Fabric mod {mod_id} has an invalid nested JAR list")
    for raw_entry in raw_nested_jars:
        if not isinstance(raw_entry, dict) or not isinstance(raw_entry.get("file"), str):
            raise BaselineError(f"Fabric mod {mod_id} has an invalid nested JAR entry")
        nested_path = raw_entry["file"]
        try:
            nested_bytes = archive.read(nested_path)
            with zipfile.ZipFile(io.BytesIO(nested_bytes)) as nested_archive:
                mod_ids.update(collect_fabric_mod_ids(nested_archive))
        except (KeyError, zipfile.BadZipFile) as exception:
            raise BaselineError(
                f"Fabric mod {mod_id} has an unreadable nested JAR: {nested_path}"
            ) from exception
    return mod_ids


def fabric_mod_ids(path: Path) -> set[str]:
    try:
        with zipfile.ZipFile(path) as archive:
            return collect_fabric_mod_ids(archive)
    except (OSError, zipfile.BadZipFile) as exception:
        raise BaselineError(f"Cannot inspect Fabric mod metadata: {path}") from exception


def verify_mod_file(
    path: Path, spec: dict[str, str], require_independent_file: bool
) -> set[str]:
    if require_independent_file and path.is_symlink():
        raise BaselineError(f"Isolated profile mod must not be a symlink: {path}")
    verify_file(path, spec["sha256"])
    mod_ids = fabric_mod_ids(path)
    if spec["mod_id"] not in mod_ids:
        raise BaselineError(f"Pinned mod id is missing from {path}: {spec['mod_id']}")
    return mod_ids


def verify_metadata(manifest: dict[str, object]) -> None:
    root = app_root(manifest)
    metadata_files = manifest.get("metadata_files")
    if not isinstance(metadata_files, dict):
        raise BaselineError("The manifest metadata_files table is invalid")
    for relative_path, expected_sha256 in metadata_files.items():
        if not isinstance(relative_path, str) or not isinstance(expected_sha256, str):
            raise BaselineError("The manifest contains an invalid metadata file entry")
        verified_path = root / safe_relative_path(relative_path, "metadata_files path")
        verify_file(verified_path, expected_sha256)

def verify_source_profile(manifest: dict[str, object]) -> None:
    source_profile = source_profile_path(manifest)
    if not source_profile.is_dir():
        raise BaselineError(f"Read-only source profile is missing: {source_profile}")
    source_mods = source_profile / "mods"
    for file_name, spec in mod_specs(manifest).items():
        verify_mod_file(source_mods / file_name, spec, require_independent_file=False)


def verify_profile_directory(manifest: dict[str, object], profile: Path) -> None:
    if not profile.is_dir() or profile.is_symlink():
        raise BaselineError(f"Isolated baseline profile is missing or linked: {profile}")
    for relative_directory in profile_directories(manifest):
        directory = profile / relative_directory
        if not directory.is_dir() or directory.is_symlink():
            raise BaselineError(f"Isolated profile directory is missing or linked: {directory}")

    profile_json = profile / "profile.json"
    options_txt = profile / "options.txt"
    if profile_json.is_symlink() or options_txt.is_symlink():
        raise BaselineError("Isolated profile control files must not be symlinks")
    try:
        actual_profile_metadata = json.loads(profile_json.read_text(encoding="utf-8"))
        actual_options = options_txt.read_text(encoding="utf-8")
    except (OSError, json.JSONDecodeError) as exception:
        raise BaselineError(
            f"Cannot read isolated profile control files: {exception}"
        ) from exception
    if actual_profile_metadata != profile_metadata(manifest):
        raise BaselineError(f"Isolated profile metadata changed: {profile_json}")
    expected_options = parse_options(profile_options_text(manifest))
    parsed_actual_options = parse_options(actual_options)
    changed_options = {
        name: {"expected": value, "actual": parsed_actual_options.get(name)}
        for name, value in expected_options.items()
        if parsed_actual_options.get(name) != value
    }
    if changed_options:
        raise BaselineError(
            f"Isolated profile controlled options changed in {options_txt}: {changed_options}"
        )

    expected_mods = mod_specs(manifest)
    mods_directory = profile / "mods"
    actual_mod_names = {path.name for path in mods_directory.glob("*.jar") if path.is_file()}
    expected_mod_names = set(expected_mods)
    if actual_mod_names != expected_mod_names:
        missing = sorted(expected_mod_names - actual_mod_names)
        unexpected = sorted(actual_mod_names - expected_mod_names)
        details = []
        if missing:
            details.append(f"missing={missing}")
        if unexpected:
            details.append(f"unexpected={unexpected}")
        raise BaselineError("Baseline mod inventory changed: " + ", ".join(details))
    discovered_mod_ids: set[str] = set()
    for file_name, spec in expected_mods.items():
        discovered_mod_ids.update(
            verify_mod_file(mods_directory / file_name, spec, require_independent_file=True)
        )
    forbidden = {
        display_name: mod_id
        for display_name, mod_id in excluded_mods(manifest).items()
        if mod_id in discovered_mod_ids
    }
    if forbidden:
        raise BaselineError(f"Excluded mods are present in the isolated profile: {forbidden}")


def verify_profile(manifest: dict[str, object]) -> None:
    verify_metadata(manifest)
    verify_profile_directory(manifest, profile_path(manifest))


def write_private_text(path: Path, content: str) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        handle.write(content)


def provision_profile(manifest: dict[str, object]) -> bool:
    target_profile = profile_path(manifest)
    verify_metadata(manifest)
    if target_profile.exists():
        verify_profile_directory(manifest, target_profile)
        return False

    verify_source_profile(manifest)
    target_profile.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    staging_profile = Path(
        tempfile.mkdtemp(prefix=f".{target_profile.name}.", dir=target_profile.parent)
    )
    try:
        for relative_directory in profile_directories(manifest):
            (staging_profile / relative_directory).mkdir(mode=0o700)
        write_private_text(
            staging_profile / "profile.json",
            json.dumps(profile_metadata(manifest), indent=2, sort_keys=True) + "\n",
        )
        write_private_text(staging_profile / "options.txt", profile_options_text(manifest))

        source_mods = source_profile_path(manifest) / "mods"
        target_mods = staging_profile / "mods"
        for file_name in mod_specs(manifest):
            copied_path = Path(
                shutil.copyfile(source_mods / file_name, target_mods / file_name)
            )
            copied_path.chmod(0o600)

        verify_profile_directory(manifest, staging_profile)
        os.replace(staging_profile, target_profile)
    finally:
        if staging_profile.exists():
            shutil.rmtree(staging_profile)
    verify_profile_directory(manifest, target_profile)
    return True


def provision_world(manifest: dict[str, object]) -> bool:
    verify_profile(manifest)
    spec = world_spec(manifest)
    target_world = world_save_path(manifest)
    if target_world.exists() or target_world.is_symlink():
        verify_pristine_world(manifest, target_world)
        return False

    verify_source_world(manifest)
    source_world = world_source_path(manifest)
    excluded_source_files = {
        str(file_name) for file_name in spec["source_excluded_files"]
    }
    staging_world = Path(
        tempfile.mkdtemp(prefix=f".{target_world.name}.", dir=target_world.parent)
    )

    def ignored_source_entries(directory: str, names: list[str]) -> set[str]:
        if Path(directory) == source_world:
            return excluded_source_files.intersection(names)
        return set()

    try:
        shutil.copytree(
            source_world,
            staging_world,
            dirs_exist_ok=True,
            ignore=ignored_source_entries,
        )
        verify_tree_fingerprint(
            staging_world,
            int(spec["source_file_count"]),
            str(spec["source_tree_sha256"]),
        )
        patch_world_level_files(manifest, staging_world)
        write_private_text(
            staging_world / str(spec["provenance_file"]),
            json.dumps(world_provenance(manifest), indent=2, sort_keys=True) + "\n",
        )
        verify_pristine_world(manifest, staging_world)
        os.replace(staging_world, target_world)
    finally:
        if staging_world.exists():
            shutil.rmtree(staging_world)
    verify_pristine_world(manifest, target_world)
    return True


def mrpack_index(manifest: dict[str, object]) -> dict[str, object]:
    profile_name = manifest.get("profile_name")
    minecraft_version = manifest.get("minecraft_version")
    loader_version = manifest.get("loader_version")
    if not all(
        isinstance(value, str) for value in (profile_name, minecraft_version, loader_version)
    ):
        raise BaselineError("The manifest cannot produce Modrinth pack metadata")
    return {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": "etherology-e2e-1.21.1-0.1.7",
        "name": profile_name,
        "summary": "Isolated Etherology 0.1.7 reference profile for E2E capture",
        "files": [],
        "dependencies": {
            "minecraft": minecraft_version,
            "fabric-loader": loader_version,
        },
    }


def write_zip_entry(archive: zipfile.ZipFile, entry_name: str, content: bytes | None) -> None:
    is_directory = content is None
    normalized_name = entry_name.rstrip("/") + "/" if is_directory else entry_name
    entry = zipfile.ZipInfo(normalized_name, date_time=(1980, 1, 1, 0, 0, 0))
    entry.create_system = 3
    entry.external_attr = ((0o40700 if is_directory else 0o100600) & 0xFFFF) << 16
    entry.compress_type = zipfile.ZIP_STORED if is_directory else zipfile.ZIP_DEFLATED
    archive.writestr(entry, b"" if content is None else content, compresslevel=9)


def expected_mrpack_entries(manifest: dict[str, object]) -> set[str]:
    entries = {
        "modrinth.index.json",
        "overrides/",
        "overrides/profile.json",
        "overrides/options.txt",
    }
    entries.update(f"overrides/{directory}/" for directory in profile_directories(manifest))
    entries.update(f"overrides/mods/{file_name}" for file_name in mod_specs(manifest))
    return entries


def verify_mrpack(manifest: dict[str, object], pack_path: Path) -> None:
    try:
        with zipfile.ZipFile(pack_path) as archive:
            actual_entries = set(archive.namelist())
            expected_entries = expected_mrpack_entries(manifest)
            if actual_entries != expected_entries:
                raise BaselineError("The generated Modrinth pack has an unexpected inventory")
            index = json.loads(archive.read("modrinth.index.json"))
            if index != mrpack_index(manifest):
                raise BaselineError("The generated Modrinth pack index changed")
            if json.loads(archive.read("overrides/profile.json")) != profile_metadata(manifest):
                raise BaselineError("The generated Modrinth profile metadata changed")
            if archive.read("overrides/options.txt").decode("utf-8") != profile_options_text(
                manifest
            ):
                raise BaselineError("The generated Modrinth profile options changed")
            for file_name, spec in mod_specs(manifest).items():
                archived_bytes = archive.read(f"overrides/mods/{file_name}")
                actual_sha256 = hashlib.sha256(archived_bytes).hexdigest()
                if actual_sha256 != spec["sha256"]:
                    raise BaselineError(f"The generated Modrinth pack changed {file_name}")
    except (OSError, zipfile.BadZipFile, json.JSONDecodeError, UnicodeDecodeError) as exception:
        raise BaselineError(f"Cannot verify generated Modrinth pack: {pack_path}") from exception


def build_mrpack(manifest: dict[str, object]) -> Path:
    verify_profile(manifest)
    profile = profile_path(manifest)
    STATE_ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary_path = STATE_ROOT / f"Etherology-E2E-{os.getpid()}.mrpack.tmp"
    try:
        with zipfile.ZipFile(temporary_path, mode="x") as archive:
            write_zip_entry(
                archive,
                "modrinth.index.json",
                (json.dumps(mrpack_index(manifest), indent=2, sort_keys=True) + "\n").encode(
                    "utf-8"
                ),
            )
            write_zip_entry(archive, "overrides/", None)
            for relative_directory in profile_directories(manifest):
                write_zip_entry(archive, f"overrides/{relative_directory}/", None)
            write_zip_entry(
                archive, "overrides/profile.json", (profile / "profile.json").read_bytes()
            )
            write_zip_entry(
                archive,
                "overrides/options.txt",
                profile_options_text(manifest).encode(),
            )
            for file_name in mod_specs(manifest):
                write_zip_entry(
                    archive,
                    f"overrides/mods/{file_name}",
                    (profile / "mods" / file_name).read_bytes(),
                )
        verify_mrpack(manifest, temporary_path)
        os.replace(temporary_path, MRPACK_PATH)
    finally:
        temporary_path.unlink(missing_ok=True)
    verify_mrpack(manifest, MRPACK_PATH)
    return MRPACK_PATH


def resolve_java(manifest: dict[str, object]) -> Path:
    java_spec = manifest.get("java")
    if not isinstance(java_spec, dict):
        raise BaselineError("The manifest Java specification is invalid")
    relative_path = java_spec.get("relative_path")
    expected_version = java_spec.get("version")
    if not isinstance(relative_path, str) or not isinstance(expected_version, str):
        raise BaselineError("The manifest Java fields are invalid")
    java = app_root(manifest) / safe_relative_path(relative_path, "java.relative_path")
    if not java.is_file() or not os.access(java, os.X_OK):
        raise BaselineError(f"Pinned Modrinth Java executable is missing: {java}")
    completed = subprocess.run(
        [str(java), "-version"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=15,
        check=False,
    )
    match = re.search(r'version "([^"+]+)', completed.stdout)
    if completed.returncode != 0 or match is None or match.group(1) != expected_version:
        raise BaselineError(f"Pinned Java is not the expected {expected_version} runtime")
    return java


def verify_launcher_library() -> None:
    try:
        actual_version = importlib.metadata.version("minecraft-launcher-lib")
    except importlib.metadata.PackageNotFoundError as exception:
        raise BaselineError(
            "minecraft-launcher-lib is missing; install the pinned baseline dependency first"
        ) from exception
    if actual_version != EXPECTED_LAUNCHER_LIBRARY_VERSION:
        raise BaselineError(
            "minecraft-launcher-lib must be exactly "
            f"{EXPECTED_LAUNCHER_LIBRARY_VERSION}, found {actual_version}"
        )


def offline_uuid(username: str) -> str:
    digest = hashlib.md5(f"OfflinePlayer:{username}".encode("utf-8")).digest()
    return str(uuid.UUID(bytes=digest, version=3))


def generate_command(manifest: dict[str, object], java: Path) -> list[str]:
    verify_launcher_library()
    try:
        import minecraft_launcher_lib.command
        import minecraft_launcher_lib.utils
    except ImportError as exception:
        raise BaselineError("minecraft-launcher-lib could not be imported") from exception

    username = manifest.get("offline_username")
    version_id = manifest.get("version_id")
    resolution = manifest.get("resolution")
    if not isinstance(username, str) or not isinstance(version_id, str):
        raise BaselineError("The manifest launch identity or version is invalid")
    if not isinstance(resolution, dict):
        raise BaselineError("The manifest resolution is invalid")
    width = resolution.get("width")
    height = resolution.get("height")
    if not isinstance(width, int) or not isinstance(height, int):
        raise BaselineError("The manifest resolution fields are invalid")

    options = minecraft_launcher_lib.utils.generate_test_options()
    options.update(
        {
            "username": username,
            "uuid": offline_uuid(username),
            "token": "offline-baseline",
            "executablePath": str(java),
            "defaultExecutablePath": str(java),
            "gameDirectory": str(profile_path(manifest)),
            "customResolution": True,
            "resolutionWidth": str(width),
            "resolutionHeight": str(height),
            "quickPlaySingleplayer": str(world_spec(manifest)["save_name"]),
        }
    )
    return minecraft_launcher_lib.command.get_minecraft_command(
        version_id,
        str(metadata_root(manifest)),
        options,
    )


def verify_environment() -> tuple[dict[str, object], Path, list[str]]:
    manifest = load_manifest()
    verify_profile(manifest)
    verify_world_identity(manifest)
    java = resolve_java(manifest)
    command = generate_command(manifest, java)
    if "net.fabricmc.loader.impl.launch.knot.KnotClient" not in command:
        raise BaselineError("Generated command does not launch Fabric KnotClient")
    quick_play_world = str(world_spec(manifest)["save_name"])
    if not any(
        argument == "--quickPlaySingleplayer"
        and index + 1 < len(command)
        and command[index + 1] == quick_play_world
        for index, argument in enumerate(command)
    ):
        raise BaselineError("Generated command does not quick-play the baseline world")
    if not CAFFEINATE_PATH.is_file():
        raise BaselineError(f"macOS caffeinate is missing: {CAFFEINATE_PATH}")
    return manifest, java, command


def read_state() -> dict[str, object] | None:
    if not CURRENT_STATE_PATH.is_file():
        return None
    try:
        state = json.loads(CURRENT_STATE_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise BaselineError(f"Cannot read launcher state: {exception}") from exception
    if state.get("schema") != 1 or not isinstance(state.get("pid"), int):
        raise BaselineError("Launcher state is invalid")
    return state


def write_state(state: dict[str, object]) -> None:
    STATE_ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary_path = STATE_ROOT / f"current.{os.getpid()}.tmp"
    descriptor = os.open(temporary_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(state, handle, indent=2)
            handle.write("\n")
        os.replace(temporary_path, CURRENT_STATE_PATH)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()


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
    version_id = state.get("version_id")
    profile = state.get("profile")
    if not isinstance(version_id, str) or not isinstance(profile, str):
        return False
    completed = subprocess.run(
        ["/bin/ps", "-ww", "-p", str(pid), "-o", "command="],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    command = completed.stdout
    return (
        completed.returncode == 0
        and version_id in command
        and profile in command
        and "net.fabricmc.loader.impl.launch.knot.KnotClient" in command
    )


def assert_profile_not_running(manifest: dict[str, object]) -> None:
    completed = subprocess.run(
        ["/bin/ps", "-ww", "-axo", "command="],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    profile = str(profile_path(manifest))
    if completed.returncode == 0 and any(
        "--gameDir" in line and profile in line for line in completed.stdout.splitlines()
    ):
        raise BaselineError("The isolated reference profile already appears to be running")


def provision_command() -> int:
    manifest = load_manifest()
    created = provision_profile(manifest)
    java = resolve_java(manifest)
    if created:
        print(f"Provisioned isolated profile: {profile_path(manifest)}")
    else:
        print(f"Isolated profile already matches the manifest: {profile_path(manifest)}")
    print(f"Java: {java}")
    print(f"Copied/verified mod jars: {len(mod_specs(manifest))}")
    print("Existing Modrinth profile access: read-only dependency source")
    return 0


def pack_command() -> int:
    manifest = load_manifest()
    pack_path = build_mrpack(manifest)
    print(f"Prepared supported Modrinth import: {pack_path}")
    print(f"SHA-256: {sha256(pack_path)}")
    print("This command did not open Modrinth App or launch Minecraft")
    return 0


def world_command() -> int:
    manifest = load_manifest()
    created = provision_world(manifest)
    if created:
        print(f"Provisioned pristine baseline world: {world_save_path(manifest)}")
    else:
        print(f"Pristine baseline world already matches: {world_save_path(manifest)}")
    print("Verified creative mode, commands, fixed gallery spawn, and gallery datapack")
    print("This command did not launch Minecraft")
    return 0


def check_command() -> int:
    manifest, java, command = verify_environment()
    resolution = manifest["resolution"]
    mods = manifest["mods"]
    if not isinstance(resolution, dict) or not isinstance(mods, dict):
        raise BaselineError("The verified manifest has an invalid shape")
    print(
        "Ready: "
        f"Minecraft {manifest['minecraft_version']} / Fabric {manifest['loader_version']} / "
        f"{resolution['width']}x{resolution['height']}"
    )
    print(f"Profile: {profile_path(manifest)}")
    print(f"Quick Play world: {world_save_path(manifest)}")
    print(f"Java: {java}")
    print(f"Verified mod jars: {len(mods)}")
    print("Verified absent: Quick Skin, Customizable Player Models, Ears, Architectury")
    print(f"Generated argv entries: {len(command)} (command intentionally not displayed)")
    return 0


def start_command() -> int:
    existing_state = read_state()
    if existing_state is not None:
        existing_pid = int(existing_state["pid"])
        if process_exists(existing_pid):
            raise BaselineError(f"Baseline client is already running as PID {existing_pid}")
        CURRENT_STATE_PATH.unlink()

    manifest, java, command = verify_environment()
    assert_profile_not_running(manifest)
    STATE_ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    logs_directory = STATE_ROOT / "logs"
    logs_directory.mkdir(mode=0o700, exist_ok=True)
    timestamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    log_path = logs_directory / f"original-client-{timestamp}.log"
    log_descriptor = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    log_handle = os.fdopen(log_descriptor, "wb", buffering=0)
    header = (
        "Etherology isolated original baseline client\n"
        f"started_utc={timestamp}\n"
        f"version_id={manifest['version_id']}\n"
        f"profile={profile_path(manifest)}\n"
        f"java={java}\n"
        "authentication=deterministic-offline-identity\n\n"
    ).encode("utf-8")
    log_handle.write(header)
    try:
        process = subprocess.Popen(
            [str(CAFFEINATE_PATH), "-dimsu", *command],
            cwd=profile_path(manifest),
            stdin=subprocess.DEVNULL,
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
    except Exception:
        log_handle.close()
        raise
    log_handle.close()
    state = {
        "schema": 1,
        "pid": process.pid,
        "started_utc": timestamp,
        "version_id": manifest["version_id"],
        "profile": str(profile_path(manifest)),
        "log": str(log_path),
    }
    try:
        write_state(state)
    except Exception:
        os.killpg(process.pid, signal.SIGTERM)
        raise
    print(f"Started isolated original baseline client as PID {process.pid}")
    print(f"Log: {log_path}")
    return 0


def status_command() -> int:
    state = read_state()
    if state is None:
        print("Baseline client is stopped")
        return 1
    pid = int(state["pid"])
    if not process_exists(pid):
        CURRENT_STATE_PATH.unlink(missing_ok=True)
        print(f"Baseline client is stopped; cleared stale state for PID {pid}")
        print(f"Last log: {state.get('log', 'unknown')}")
        return 1
    if not process_matches(pid, state):
        raise BaselineError("State PID belongs to an unexpected process; refusing to manage it")
    print(f"Baseline client is running as PID {pid}")
    print(f"Log: {state.get('log', 'unknown')}")
    return 0


def stop_command() -> int:
    state = read_state()
    if state is None:
        print("Baseline client is already stopped")
        return 0
    pid = int(state["pid"])
    if not process_exists(pid):
        CURRENT_STATE_PATH.unlink()
        print(f"Removed stale state for PID {pid}")
        return 0
    if not process_matches(pid, state):
        raise BaselineError("State PID belongs to an unexpected process; refusing to stop it")

    os.killpg(pid, signal.SIGTERM)
    deadline = time.monotonic() + STOP_TIMEOUT_SECONDS
    while process_exists(pid) and time.monotonic() < deadline:
        time.sleep(0.25)
    if process_exists(pid):
        os.killpg(pid, signal.SIGKILL)
        print(f"Baseline client PID {pid} required a forced stop")
    else:
        print(f"Stopped baseline client PID {pid}")
    CURRENT_STATE_PATH.unlink(missing_ok=True)
    return 0


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Provision and manage an isolated Etherology 1.21.1 client without credentials."
        )
    )
    parser.add_argument(
        "action",
        choices=("provision", "world", "pack", "check", "start", "status", "stop"),
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    actions = {
        "provision": provision_command,
        "world": world_command,
        "pack": pack_command,
        "check": check_command,
        "start": start_command,
        "status": status_command,
        "stop": stop_command,
    }
    try:
        return actions[arguments.action]()
    except BaselineError as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
