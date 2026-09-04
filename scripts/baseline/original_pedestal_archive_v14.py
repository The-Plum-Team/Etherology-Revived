#!/usr/bin/env python3
"""Read-only verifier for the accepted original Pedestal v14 archive."""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
import tempfile
from typing import Callable


LAUNCH_VERIFIER_RELATIVE_PATH = (
    "scripts/baseline/original_pedestal_evidence_v14.py"
)
LAUNCH_VERIFIER_SIZE = 42_963
LAUNCH_VERIFIER_SHA256 = (
    "8f4775e95f2eea7595c53197f2032d4379c22efbcb046a2bfc44d4148c92a819"
)
ARCHIVE_RELATIVE_PATH = "docs/evidence/original-1.21.1/pedestal-v14"
ARCHIVE_MANIFEST_SIZE = 9_561
ARCHIVE_MANIFEST_SHA256 = (
    "cd78d15e06603594786c8d3cddfde82b6358917fda83360dfc16e515c7f4ab60"
)
PROFILE_ID = "etherology-original-fabric-1.21.1-published-0.1.7-v14"
PROFILE_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v14.json"
)
PROFILE_SIZE = 10_307
PROFILE_SHA256 = "ddee58342b8a0e6c45bea375247243be5cc20bfa1680ef28f0d9ab5c72518962"
ACTIVE_RUNTIME_RELATIVE_PATH = "scripts/baseline/.state/runtimes/" + PROFILE_ID
SCENARIO_ID = "pedestal-baseline"
REFERENCE_ID = "published-0.1.7"
HARNESS_FILE = "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.4.3.jar"
HARNESS_SIZE = 340_723
HARNESS_SHA256 = "9a329ff219f4403c8880597ed851a73843c74adf81ac4b5561b6708cf82129b6"
RUNTIME_CONTROLLER_LOG_RELATIVE_PATH = (
    "logs/original-client-20260904T165916Z.log"
)
RUNTIME_VERIFICATION_RELATIVE_PATH = (
    "logs/original-client-20260904T165916Z-verification.json"
)

EXPECTED_ARCHIVE_RELATIVE_PATHS = (
    "README.md",
    "archive-manifest.json",
    "controller/original-client.log",
    "controller/verification.json",
    "reports/done.marker",
    "reports/report.json",
    "screenshots/pedestal-gallery.png",
    "screenshots/pedestal-persistence-initial.png",
    "screenshots/pedestal-persistence-reopened.png",
    "screenshots/pedestal-transition-drops.png",
    "world-proof/entities/r.-1.0.mca",
    "world-proof/entities/r.0.0.mca",
    "world-proof/level.dat",
    "world-proof/region/r.-1.0.mca",
    "world-proof/region/r.0.0.mca",
    "world-proof/session.lock",
)
EXPECTED_WORLD_RELATIVE_PATHS = (
    "entities/r.-1.0.mca",
    "entities/r.0.0.mca",
    "level.dat",
    "region/r.-1.0.mca",
    "region/r.0.0.mca",
    "session.lock",
)
EXPECTED_CAPTURE_MTIME_NS = {
    "controller/original-client.log": 1_788_541_284_714_884_677,
    "controller/verification.json": 1_788_541_297_192_314_680,
    "reports/done.marker": 1_788_541_284_504_385_985,
    "reports/report.json": 1_788_541_284_503_790_562,
    "screenshots/pedestal-gallery.png": 1_788_541_179_607_574_693,
    "screenshots/pedestal-persistence-initial.png": 1_788_541_189_595_644_992,
    "screenshots/pedestal-persistence-reopened.png": 1_788_541_284_476_320_832,
    "screenshots/pedestal-transition-drops.png": 1_788_541_186_403_334_596,
    "world-proof/entities/r.-1.0.mca": 1_788_541_284_696_224_465,
    "world-proof/entities/r.0.0.mca": 1_788_541_284_737_527_769,
    "world-proof/level.dat": 1_788_541_284_714_256_920,
    "world-proof/region/r.-1.0.mca": 1_788_541_284_729_668_388,
    "world-proof/region/r.0.0.mca": 1_788_541_284_718_742_471,
    "world-proof/session.lock": 1_788_541_190_954_804_970,
}
EXPECTED_TRANSITION_DROPS = {
    "minecraft:blue_carpet": 1,
    "minecraft:diamond": 1,
    "minecraft:emerald": 1,
    "minecraft:red_carpet": 1,
}
VERTICAL_CARPET_LIMITATION = (
    "hash-pinned-published-0.1.7-bytecode-not-executed-safety-guard: "
    "empty-carpet-slot vertical direction reaches the horizontal-only facing property"
)
WORLD_DESCRIPTOR_SHA256 = (
    "6f4b541d8024d4a3dbad41a980d65d94ab91dbaf3ea42224247e6f824b781f1a"
)
IMMUTABLE_LAUNCHER_FILES_SHA256 = (
    "99d997c41f549bd9750c61f0815346851a8664d01b7bd5c2a54a6252853ce4ea"
)
ARTIFACT_LOCK_SIZE = 5_389
ARTIFACT_LOCK_SHA256 = (
    "3db2fa827c525d0ececb4a48e58bc20f83f1c9411e451639183d257e233c74fa"
)
RUNTIME_LOCK_SIZE = 832_422
RUNTIME_LOCK_SHA256 = (
    "c5fb88d35b67b55a71f61981eb9efa1313028d00b529ec6c5a2a1efb0291e09a"
)
LAUNCH_ATTEMPT_SIZE = 872_152
LAUNCH_ATTEMPT_SHA256 = (
    "5c072d7dae820771451d821ba7cc72d20061101c5acb1d68c1c5365eece48e6e"
)
LAUNCH_ATTEMPT_MTIME_NS = 1_788_541_154_277_924_606

_SCRIPT_PATH = Path(__file__).resolve()
_LAUNCH_VERIFIER_PATH = _SCRIPT_PATH.with_name(
    PurePosixPath(LAUNCH_VERIFIER_RELATIVE_PATH).name
)
_LAUNCH_MODULE_NAME = "etherology_original_pedestal_v14_launch_for_archive"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _require_bootstrap_launch_verifier() -> None:
    if (
        _LAUNCH_VERIFIER_PATH.is_symlink()
        or not _LAUNCH_VERIFIER_PATH.is_file()
        or _LAUNCH_VERIFIER_PATH.stat().st_size != LAUNCH_VERIFIER_SIZE
        or _sha256(_LAUNCH_VERIFIER_PATH) != LAUNCH_VERIFIER_SHA256
    ):
        raise RuntimeError("The immutable Pedestal v14 launch verifier changed")


_require_bootstrap_launch_verifier()
_LAUNCH_SPECIFICATION = importlib.util.spec_from_file_location(
    _LAUNCH_MODULE_NAME,
    _LAUNCH_VERIFIER_PATH,
)
if _LAUNCH_SPECIFICATION is None or _LAUNCH_SPECIFICATION.loader is None:
    raise RuntimeError("Cannot load the immutable Pedestal v14 launch verifier")
_launch = importlib.util.module_from_spec(_LAUNCH_SPECIFICATION)
sys.modules[_LAUNCH_MODULE_NAME] = _launch
_LAUNCH_SPECIFICATION.loader.exec_module(_launch)


class PedestalArchiveError(RuntimeError):
    """Reports one fail-closed accepted Pedestal v14 archive violation."""


def _fail(error_type: type[Exception], message: str) -> None:
    raise error_type("Original Pedestal v14 archive: " + message)


def _is_exact_int(value: object, expected: int) -> bool:
    return type(value) is int and value == expected


def _is_exact_json(value: object, expected: object) -> bool:
    if type(value) is not type(expected):
        return False
    if isinstance(expected, dict):
        return set(value) == set(expected) and all(
            _is_exact_json(value[name], expected_value)
            for name, expected_value in expected.items()
        )
    if isinstance(expected, list):
        return len(value) == len(expected) and all(
            _is_exact_json(actual, expected_value)
            for actual, expected_value in zip(value, expected, strict=True)
        )
    return value == expected


def _require_exact_json(
    value: object,
    expected: object,
    description: str,
    error_type: type[Exception],
) -> None:
    if not _is_exact_json(value, expected):
        _fail(error_type, f"{description} changed")


def _require_exact_fields(
    value: object,
    expected: set[str],
    description: str,
    error_type: type[Exception],
) -> dict[str, object]:
    if not isinstance(value, dict) or set(value) != expected:
        _fail(error_type, f"{description} field inventory changed")
    return value


def _reject_duplicate_json_keys(
    pairs: list[tuple[str, object]],
) -> dict[str, object]:
    value: dict[str, object] = {}
    for name, entry in pairs:
        if name in value:
            raise ValueError(f"duplicate JSON key: {name}")
        value[name] = entry
    return value


def _load_json_object(
    path: Path,
    description: str,
    error_type: type[Exception],
) -> dict[str, object]:
    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=_reject_duplicate_json_keys,
        )
    except (OSError, UnicodeError, json.JSONDecodeError, ValueError) as error:
        raise error_type(
            f"Original Pedestal v14 archive: {description} is not readable JSON"
        ) from error
    if not isinstance(value, dict):
        _fail(error_type, f"{description} is not a JSON object")
    return value


def _require_regular_file(
    path: Path,
    description: str,
    error_type: type[Exception],
) -> None:
    try:
        mode = path.lstat().st_mode
    except OSError as error:
        raise error_type(
            f"Original Pedestal v14 archive: {description} is missing"
        ) from error
    if path.is_symlink() or not stat.S_ISREG(mode):
        _fail(error_type, f"{description} is linked or not a regular file")


def _require_pinned_file(
    path: Path,
    size: int,
    sha256: str,
    description: str,
    sha256_file: Callable[[Path], str],
    error_type: type[Exception],
) -> None:
    _require_regular_file(path, description, error_type)
    if path.stat().st_size != size or sha256_file(path) != sha256:
        _fail(error_type, f"{description} changed")


def _unlinked_relative_path(
    root: Path,
    relative_path: str,
    description: str,
    error_type: type[Exception],
) -> Path:
    current = root
    for component in PurePosixPath(relative_path).parts:
        current /= component
        if current.is_symlink():
            _fail(error_type, f"{description} contains a linked path")
    return current


def _require_expected_argument_path(
    repository_input: Path,
    actual: Path,
    relative_path: str,
    description: str,
    error_type: type[Exception],
) -> None:
    expected = repository_input / relative_path
    if actual.absolute() != expected.absolute():
        _fail(error_type, f"{description} selected another path")


def _expected_archive_directories() -> set[str]:
    directories: set[str] = set()
    for relative_path in EXPECTED_ARCHIVE_RELATIVE_PATHS:
        for parent in PurePosixPath(relative_path).parents:
            if parent != PurePosixPath("."):
                directories.add(parent.as_posix())
    return directories


def _validate_archive_inventory(
    archive: Path,
    error_type: type[Exception],
) -> None:
    try:
        archive_mode = archive.lstat().st_mode
    except OSError as error:
        raise error_type(
            "Original Pedestal v14 archive: accepted archive is missing"
        ) from error
    if archive.is_symlink() or not stat.S_ISDIR(archive_mode):
        _fail(error_type, "accepted archive is linked or not a directory")
    expected_directories = _expected_archive_directories()
    expected_files = set(EXPECTED_ARCHIVE_RELATIVE_PATHS)
    entries = tuple(archive.rglob("*"))
    actual_entries = {path.relative_to(archive).as_posix() for path in entries}
    if actual_entries != expected_directories | expected_files:
        _fail(error_type, "accepted archive inventory changed")
    for path in entries:
        relative_path = path.relative_to(archive).as_posix()
        try:
            mode = path.lstat().st_mode
        except OSError as error:
            raise error_type(
                "Original Pedestal v14 archive: archive inventory became unreadable"
            ) from error
        if path.is_symlink():
            _fail(error_type, f"archive entry is linked: {relative_path}")
        if relative_path in expected_directories:
            if not stat.S_ISDIR(mode):
                _fail(error_type, f"archive directory is not a directory: {relative_path}")
        elif not stat.S_ISREG(mode):
            _fail(error_type, f"archive payload is not a regular file: {relative_path}")


def _expected_profile() -> dict[str, object]:
    return {
        "id": PROFILE_ID,
        "manifest_path": PROFILE_RELATIVE_PATH,
        "manifest_size": PROFILE_SIZE,
        "manifest_sha256": PROFILE_SHA256,
    }


def _expected_artifacts() -> dict[str, object]:
    return {
        "reference_bundle": {
            "size": 7_838_299,
            "sha256": (
                "764bb85e398c3dca43f68a0d2c1eeecd0f3523da7a48adf48bcc5a967af4b91b"
            ),
        },
        "production": {
            "mod_id": "etherology",
            "file_name": "Etherology-1.21-0.1.7.jar",
            "size": 2_743_963,
            "sha256": (
                "38de3c1aad47fc715c2226266dec4c70c02d16370034a4e0350508131ac15c43"
            ),
        },
        "harness": {
            "mod_id": "etherology_original_baseline_harness",
            "file_name": HARNESS_FILE,
            "version": "1.4.3",
            "size": HARNESS_SIZE,
            "sha256": HARNESS_SHA256,
        },
    }


def _expected_result() -> dict[str, object]:
    return {
        "report_schema": 4,
        "report_status": "passed",
        "report_passed": True,
        "client_ticks": 2_297,
        "lifecycle_failure": "",
        "assertion_count": 74,
        "assertions_true": 74,
        "assertions_false": 0,
        "screenshot_count": 4,
        "completed_render_count_per_capture": 120,
        "forced_save": True,
        "full_restart": True,
        "persistence_exact": True,
        "authoritative_server_drops": EXPECTED_TRANSITION_DROPS,
        "client_drop_mirror_ready_recorded": True,
        "client_drop_mirror_ready_exact": True,
        "client_drop_capture_recorded": True,
        "client_drop_capture_exact": True,
        "vertical_carpet_safety_limitation": VERTICAL_CARPET_LIMITATION,
    }


def _validate_manifest_semantics(
    manifest: dict[str, object],
    error_type: type[Exception],
) -> dict[str, dict[str, object]]:
    _require_exact_fields(
        manifest,
        {
            "schema",
            "kind",
            "accepted",
            "verification_scope",
            "reference",
            "scenario",
            "archive_policy",
            "profile",
            "runtime",
            "controller",
            "launch_verifier",
            "publication",
            "artifacts",
            "runtime_bindings",
            "mutable_launcher_outputs",
            "result",
            "visual_comparison",
            "world_proof",
            "controller_log_provenance",
            "files",
        },
        "archive manifest",
        error_type,
    )
    if (
        not _is_exact_int(manifest.get("schema"), 1)
        or manifest.get("kind") != "etherology-original-fabric-baseline-evidence"
        or manifest.get("accepted") is not True
        or manifest.get("verification_scope")
        != (
            "archive-integrity-plus-captured-controller-verification-and-"
            "selected-world-proof"
        )
        or manifest.get("scenario") != SCENARIO_ID
    ):
        _fail(error_type, "archive acceptance identity changed")
    _require_exact_json(
        manifest.get("reference"),
        {
            "id": REFERENCE_ID,
            "authority": "published-binary",
            "source_commit_binding": "unavailable",
        },
        "published reference binding",
        error_type,
    )
    _require_exact_json(
        manifest.get("archive_policy"),
        {
            "exact_relative_paths": list(EXPECTED_ARCHIVE_RELATIVE_PATHS),
            "symlinks_allowed": False,
            "copied_runtime_artifacts_are_byte_for_byte": True,
        },
        "archive policy",
        error_type,
    )
    _require_exact_json(
        manifest.get("profile"),
        _expected_profile(),
        "profile binding",
        error_type,
    )
    _require_exact_json(
        manifest.get("runtime"),
        {
            "minecraft": "1.21.1",
            "loader": "fabric",
            "loader_version": "0.17.3",
            "java": 21,
            "capture_kind": "minecraft-framebuffer",
            "framebuffer_width": 1_920,
            "framebuffer_height": 1_080,
            "source_profiles": [],
            "preserved_path": ACTIVE_RUNTIME_RELATIVE_PATH,
            "must_never_run_again": True,
        },
        "consumed runtime binding",
        error_type,
    )
    _require_exact_json(
        manifest.get("controller"),
        {
            "commit": "b43bec345696e7d84db832bdd4a8ca2830ff1bd2",
            "path": "scripts/baseline/original_client.py",
            "size_at_capture": 303_990,
            "sha256_at_capture": (
                "3d9fcb2e06fb8e581122b9dda99fd0e2c6509df0b527eaff7e4feed11b108e3b"
            ),
            "timeout_seconds": 1_800,
            "native_process_exit_observed": True,
            "controller_process_exit_observed": True,
            "process_group_forcibly_terminated": False,
            "observed_exit_code": 0,
            "clean_shutdown": True,
            "controller_verification_published": True,
        },
        "controller provenance",
        error_type,
    )
    _require_exact_json(
        manifest.get("launch_verifier"),
        {
            "path": LAUNCH_VERIFIER_RELATIVE_PATH,
            "size": LAUNCH_VERIFIER_SIZE,
            "sha256": LAUNCH_VERIFIER_SHA256,
            "immutable_after_launch": True,
        },
        "launch-verifier provenance",
        error_type,
    )
    _require_exact_json(
        manifest.get("publication"),
        {
            "scenario_report": "reports/report.json",
            "completion_marker": "reports/done.marker",
            "controller_verification": "controller/verification.json",
            "verified_last_in_capture_runtime": True,
            "archive_payloads_match_capture_runtime": True,
            "capture_mtime_ns": EXPECTED_CAPTURE_MTIME_NS,
        },
        "publication contract",
        error_type,
    )
    _require_exact_json(
        manifest.get("artifacts"),
        _expected_artifacts(),
        "artifact provenance",
        error_type,
    )
    _require_exact_json(
        manifest.get("runtime_bindings"),
        {
            "artifact_lock": {
                "size": ARTIFACT_LOCK_SIZE,
                "sha256": ARTIFACT_LOCK_SHA256,
            },
            "runtime_lock": {
                "size": RUNTIME_LOCK_SIZE,
                "sha256": RUNTIME_LOCK_SHA256,
            },
            "launch_attempt": {
                "size": LAUNCH_ATTEMPT_SIZE,
                "sha256": LAUNCH_ATTEMPT_SHA256,
                "mtime_ns": LAUNCH_ATTEMPT_MTIME_NS,
            },
            "immutable_launcher_files_sha256": (
                IMMUTABLE_LAUNCHER_FILES_SHA256
            ),
        },
        "runtime seal bindings",
        error_type,
    )
    _require_exact_json(
        manifest.get("mutable_launcher_outputs"),
        {
            "skin_cache_present": False,
            "skin_cache_file_count": 0,
            "skin_cache_total_size": 0,
        },
        "mutable launcher outcome",
        error_type,
    )
    _require_exact_json(
        manifest.get("result"),
        _expected_result(),
        "accepted result",
        error_type,
    )
    _require_exact_json(
        manifest.get("visual_comparison"),
        {
            "material_pixel_delta_threshold": 24,
            "maximum_persistence_material_changed_pixel_ratio": 0.15,
            "persistence_material_changed_pixel_ratio": (
                0.001283275462962963
            ),
        },
        "visual comparison",
        error_type,
    )
    _require_exact_json(
        manifest.get("world_proof"),
        {
            "path": "world-proof",
            "source": (
                "game/saves/etherology-original-pedestal-baseline-world"
            ),
            "save_directory": "etherology-original-pedestal-baseline-world",
            "fixture_regions": ["region/r.-1.0.mca", "region/r.0.0.mca"],
            "exact_relative_paths": list(EXPECTED_WORLD_RELATIVE_PATHS),
            "file_count": 6,
            "total_size": 8_546_775,
            "descriptor_algorithm": (
                "sha256(sorted(path + NUL + decimal_size + NUL + sha256 + LF))"
            ),
            "descriptor_sha256": WORLD_DESCRIPTOR_SHA256,
        },
        "selected world proof",
        error_type,
    )
    _require_exact_json(
        manifest.get("controller_log_provenance"),
        {
            "native_latest_log_size": 27_550,
            "native_latest_log_sha256": (
                "98fe5f52140cee0ca9a0ac573c899abf1f5d0480cdb012b2c066174a38a0d790"
            ),
            "controller_prefix_size": 428,
            "controller_prefix_sha256": (
                "5139918d9e12a1ef07391204ad059a79226f8d5fa0f5ccd18469e74a2d1c3097"
            ),
            "native_latest_log_is_exact_controller_suffix": True,
        },
        "controller-log provenance",
        error_type,
    )

    expected_files = set(EXPECTED_ARCHIVE_RELATIVE_PATHS) - {
        "archive-manifest.json"
    }
    files = _require_exact_fields(
        manifest.get("files"),
        expected_files,
        "archive payload pins",
        error_type,
    )
    for relative_path in sorted(expected_files):
        entry = _require_exact_fields(
            files.get(relative_path),
            {"size", "sha256"},
            f"archive payload pin {relative_path}",
            error_type,
        )
        if (
            type(entry.get("size")) is not int
            or int(entry["size"]) <= 0
            or not isinstance(entry.get("sha256"), str)
            or re.fullmatch(r"[0-9a-f]{64}", str(entry["sha256"])) is None
        ):
            _fail(error_type, f"archive payload pin is invalid: {relative_path}")
    return files


def _world_descriptor(
    archive: Path,
    files: dict[str, dict[str, object]],
) -> tuple[int, str]:
    digest = hashlib.sha256()
    total_size = 0
    for relative_path in sorted(EXPECTED_WORLD_RELATIVE_PATHS):
        archive_relative_path = "world-proof/" + relative_path
        entry = files[archive_relative_path]
        size = int(entry["size"])
        total_size += size
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        digest.update(str(size).encode("ascii"))
        digest.update(b"\0")
        digest.update(str(entry["sha256"]).encode("ascii"))
        digest.update(b"\n")
        if (archive / archive_relative_path).stat().st_size != size:
            raise RuntimeError("world descriptor received an unvalidated archive")
    return total_size, digest.hexdigest()


def _validate_archive_payloads(
    archive: Path,
    manifest: dict[str, object],
    files: dict[str, dict[str, object]],
    sha256_file: Callable[[Path], str],
    error_type: type[Exception],
) -> None:
    for relative_path, entry in files.items():
        _require_pinned_file(
            archive / relative_path,
            int(entry["size"]),
            str(entry["sha256"]),
            f"archive payload {relative_path}",
            sha256_file,
            error_type,
        )
    for relative_path, expected_mtime_ns in EXPECTED_CAPTURE_MTIME_NS.items():
        if (archive / relative_path).stat().st_mtime_ns != expected_mtime_ns:
            _fail(error_type, f"capture mtime changed: {relative_path}")

    world_proof = manifest["world_proof"]
    if not isinstance(world_proof, dict):
        _fail(error_type, "world proof is not an object")
    total_size, descriptor = _world_descriptor(archive, files)
    if (
        total_size != world_proof["total_size"]
        or descriptor != world_proof["descriptor_sha256"]
    ):
        _fail(error_type, "selected world-proof descriptor changed")

    provenance = manifest["controller_log_provenance"]
    if not isinstance(provenance, dict):
        _fail(error_type, "controller-log provenance is not an object")
    controller_log = (archive / "controller/original-client.log").read_bytes()
    prefix_size = int(provenance["controller_prefix_size"])
    prefix = controller_log[:prefix_size]
    native_suffix = controller_log[prefix_size:]
    if (
        len(prefix) != prefix_size
        or hashlib.sha256(prefix).hexdigest()
        != provenance["controller_prefix_sha256"]
        or len(native_suffix) != provenance["native_latest_log_size"]
        or hashlib.sha256(native_suffix).hexdigest()
        != provenance["native_latest_log_sha256"]
    ):
        _fail(error_type, "controller log is not the pinned prefix plus native log")


def _load_validated_archive(
    repository_root: Path,
    archive_path: Path,
    sha256_file: Callable[[Path], str],
    error_type: type[Exception],
) -> tuple[Path, dict[str, object], dict[str, dict[str, object]]]:
    repository_input = repository_root.absolute()
    _require_expected_argument_path(
        repository_input,
        archive_path,
        ARCHIVE_RELATIVE_PATH,
        "accepted archive",
        error_type,
    )
    try:
        root = repository_input.resolve(strict=True)
    except OSError as error:
        raise error_type(
            "Original Pedestal v14 archive: repository root is missing"
        ) from error
    archive = _unlinked_relative_path(
        root,
        ARCHIVE_RELATIVE_PATH,
        "accepted archive",
        error_type,
    )
    _validate_archive_inventory(archive, error_type)
    archive_manifest_path = archive / "archive-manifest.json"
    _require_pinned_file(
        archive_manifest_path,
        ARCHIVE_MANIFEST_SIZE,
        ARCHIVE_MANIFEST_SHA256,
        "archive manifest",
        sha256_file,
        error_type,
    )
    manifest = _load_json_object(
        archive_manifest_path,
        "archive manifest",
        error_type,
    )
    files = _validate_manifest_semantics(manifest, error_type)
    _validate_archive_payloads(
        archive,
        manifest,
        files,
        sha256_file,
        error_type,
    )
    return archive, manifest, files


def _report_artifacts(manifest: dict[str, object]) -> list[dict[str, object]]:
    artifacts = manifest["artifacts"]
    if not isinstance(artifacts, dict):
        raise RuntimeError("validated manifest lost its artifacts")
    production = artifacts["production"]
    harness = artifacts["harness"]
    if not isinstance(production, dict) or not isinstance(harness, dict):
        raise RuntimeError("validated manifest contains invalid artifacts")
    return [
        {
            "mod_id": production["mod_id"],
            "origin_kind": "PATH",
            "file_name": production["file_name"],
            "size": production["size"],
            "sha256": production["sha256"],
        },
        {
            "mod_id": harness["mod_id"],
            "origin_kind": "PATH",
            "file_name": harness["file_name"],
            "size": harness["size"],
            "sha256": harness["sha256"],
        },
    ]


def _validate_report_and_marker(
    archive: Path,
    manifest: dict[str, object],
    sha256_file: Callable[[Path], str],
    error_type: type[Exception],
) -> dict[str, object]:
    report_path = archive / "reports/report.json"
    report = _load_json_object(report_path, "accepted report", error_type)
    assertions = report.get("assertions")
    if (
        not _is_exact_int(report.get("schema"), 4)
        or report.get("reference_id") != REFERENCE_ID
        or report.get("scenario") != SCENARIO_ID
        or report.get("lane") != "fabric-1.21.1-original"
        or report.get("status") != "passed"
        or report.get("passed") is not True
        or not _is_exact_int(report.get("client_ticks"), 2_297)
        or report.get("lifecycle_failure") != ""
        or not isinstance(assertions, list)
        or len(assertions) != 74
        or any(
            not isinstance(assertion, dict)
            or set(assertion) != {"name", "passed", "expected", "actual"}
            or assertion.get("passed") is not True
            for assertion in assertions
        )
        or tuple(assertion["name"] for assertion in assertions)
        != _launch.EXPECTED_ASSERTION_NAMES
        or not _is_exact_json(report.get("artifacts"), _report_artifacts(manifest))
    ):
        _fail(error_type, "accepted report outcome changed")
    expected_marker = (
        f"{SCENARIO_ID}:passed\n"
        f"report_sha256:{sha256_file(report_path)}\n"
    )
    try:
        marker = (archive / "reports/done.marker").read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        raise error_type(
            "Original Pedestal v14 archive: completion marker is unreadable"
        ) from error
    if marker != expected_marker:
        _fail(error_type, "completion marker does not authenticate the passed report")
    return report


def _runtime_file_descriptor(
    runtime_path: str,
    archive_path: str,
    files: dict[str, dict[str, object]],
) -> dict[str, object]:
    pin = files[archive_path]
    return {
        "path": runtime_path,
        "sha256": pin["sha256"],
        "size": pin["size"],
    }


def _validate_controller_verification(
    archive: Path,
    manifest: dict[str, object],
    files: dict[str, dict[str, object]],
    error_type: type[Exception],
) -> dict[str, object]:
    verification = _load_json_object(
        archive / "controller/verification.json",
        "controller verification",
        error_type,
    )
    _require_exact_fields(
        verification,
        {
            "completion_marker",
            "controller_log",
            "immutable_launcher_files_sha256",
            "launch_attempt",
            "mutable_launcher_outputs",
            "profile_id",
            "scenario",
            "scenario_report",
            "schema",
            "screenshots",
            "status",
            "verified_at_unix_ns",
        },
        "controller verification",
        error_type,
    )
    if (
        not _is_exact_int(verification.get("schema"), 1)
        or verification.get("status") != "passed"
        or verification.get("profile_id") != PROFILE_ID
        or verification.get("scenario") != SCENARIO_ID
        or type(verification.get("verified_at_unix_ns")) is not int
        or int(verification["verified_at_unix_ns"])
        <= EXPECTED_CAPTURE_MTIME_NS["controller/original-client.log"]
        or int(verification["verified_at_unix_ns"])
        > EXPECTED_CAPTURE_MTIME_NS["controller/verification.json"]
    ):
        _fail(error_type, "controller verification outcome changed")
    expected_screenshots = [
        _runtime_file_descriptor(
            "evidence/pedestal-baseline/" + relative_path,
            relative_path,
            files,
        )
        for _phase, relative_path in _launch.EXPECTED_SCREENSHOTS
    ]
    expected = {
        "launch_attempt": {
            "path": "launch-attempt.json",
            "sha256": LAUNCH_ATTEMPT_SHA256,
            "size": LAUNCH_ATTEMPT_SIZE,
        },
        "scenario_report": _runtime_file_descriptor(
            "evidence/pedestal-baseline/reports/report.json",
            "reports/report.json",
            files,
        ),
        "completion_marker": _runtime_file_descriptor(
            "evidence/pedestal-baseline/reports/done.marker",
            "reports/done.marker",
            files,
        ),
        "controller_log": _runtime_file_descriptor(
            RUNTIME_CONTROLLER_LOG_RELATIVE_PATH,
            "controller/original-client.log",
            files,
        ),
        "screenshots": expected_screenshots,
        "immutable_launcher_files_sha256": IMMUTABLE_LAUNCHER_FILES_SHA256,
        "mutable_launcher_outputs": {
            "skin_cache": {
                "file_count": 0,
                "files": [],
                "path": "assets/skins",
                "present": False,
                "total_size": 0,
            }
        },
    }
    for name, value in expected.items():
        _require_exact_json(
            verification.get(name),
            value,
            f"controller verification {name}",
            error_type,
        )
    publication_order = (
        LAUNCH_ATTEMPT_MTIME_NS,
        EXPECTED_CAPTURE_MTIME_NS["screenshots/pedestal-gallery.png"],
        EXPECTED_CAPTURE_MTIME_NS[
            "screenshots/pedestal-transition-drops.png"
        ],
        EXPECTED_CAPTURE_MTIME_NS[
            "screenshots/pedestal-persistence-initial.png"
        ],
        EXPECTED_CAPTURE_MTIME_NS[
            "screenshots/pedestal-persistence-reopened.png"
        ],
        EXPECTED_CAPTURE_MTIME_NS["reports/report.json"],
        EXPECTED_CAPTURE_MTIME_NS["reports/done.marker"],
        EXPECTED_CAPTURE_MTIME_NS["controller/original-client.log"],
        int(verification["verified_at_unix_ns"]),
        EXPECTED_CAPTURE_MTIME_NS["controller/verification.json"],
    )
    if not all(
        earlier < later
        for earlier, later in zip(publication_order, publication_order[1:])
    ):
        _fail(error_type, "capture publication order changed")
    return verification


def _validate_repository_launch_contract(
    root: Path,
    manifest_path: Path,
    harness_path: Path,
    sha256_file: Callable[[Path], str],
    error_type: type[Exception],
) -> None:
    _require_pinned_file(
        root / LAUNCH_VERIFIER_RELATIVE_PATH,
        LAUNCH_VERIFIER_SIZE,
        LAUNCH_VERIFIER_SHA256,
        "immutable launch verifier",
        sha256_file,
        error_type,
    )
    _launch.validate_pinned_contract(
        repository_root=root,
        manifest_path=manifest_path,
        harness_path=harness_path,
        sha256_file=sha256_file,
        error_type=error_type,
    )


def _validate_mechanics(
    archive: Path,
    report: dict[str, object],
    manifest: dict[str, object],
    decode_png: Callable[[Path], object],
    assert_image_is_not_blank: Callable[[object], None],
    sha256_file: Callable[[Path], str],
    error_type: type[Exception],
):
    with tempfile.TemporaryDirectory(
        prefix="etherology-pedestal-v14-archive-"
    ) as temporary_directory:
        scenario_root = Path(temporary_directory)
        for directory in ("reports", "screenshots"):
            (scenario_root / directory).mkdir()
        for relative_path in (
            "reports/report.json",
            "reports/done.marker",
            *(
                relative_path
                for _phase, relative_path in _launch.EXPECTED_SCREENSHOTS
            ),
        ):
            destination = scenario_root / relative_path
            shutil.copyfile(archive / relative_path, destination)
        return _launch.validate_evidence(
            scenario_root=scenario_root,
            world_path=archive / "world-proof",
            report=report,
            expected_artifacts=_report_artifacts(manifest),
            decode_png=decode_png,
            assert_image_is_not_blank=assert_image_is_not_blank,
            sha256_file=sha256_file,
            error_type=error_type,
        )


def validate_archive(
    *,
    repository_root: Path,
    archive_path: Path,
    manifest_path: Path,
    harness_path: Path,
    decode_png: Callable[[Path], object],
    assert_image_is_not_blank: Callable[[object], None],
    sha256_file: Callable[[Path], str] = _sha256,
    error_type: type[Exception] = PedestalArchiveError,
):
    """Validates the immutable accepted archive and returns its mechanic summary."""

    repository_input = repository_root.absolute()
    _require_expected_argument_path(
        repository_input,
        manifest_path,
        PROFILE_RELATIVE_PATH,
        "profile manifest",
        error_type,
    )
    archive, manifest, files = _load_validated_archive(
        repository_root,
        archive_path,
        sha256_file,
        error_type,
    )
    report = _validate_report_and_marker(
        archive,
        manifest,
        sha256_file,
        error_type,
    )
    _validate_controller_verification(
        archive,
        manifest,
        files,
        error_type,
    )
    root = repository_input.resolve(strict=True)
    expected_harness = root / (
        "baseline-harness/fabric/1.21.1/build/libs/" + HARNESS_FILE
    )
    if harness_path.absolute() != expected_harness.absolute():
        _fail(error_type, "harness selected another path")
    _validate_repository_launch_contract(
        root,
        manifest_path,
        harness_path,
        sha256_file,
        error_type,
    )
    summary = _validate_mechanics(
        archive,
        report,
        manifest,
        decode_png,
        assert_image_is_not_blank,
        sha256_file,
        error_type,
    )
    visual = manifest["visual_comparison"]
    if not isinstance(visual, dict) or (
        summary.assertion_count != 74
        or summary.screenshot_count != 4
        or summary.persistence_material_changed_pixel_ratio
        != visual["persistence_material_changed_pixel_ratio"]
    ):
        _fail(error_type, "delegated mechanic summary changed")
    return summary


def _canonical_json_sha256(value: object) -> str:
    content = json.dumps(
        value,
        ensure_ascii=True,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return hashlib.sha256(content).hexdigest()


def _files_are_equal(first: Path, second: Path) -> bool:
    if first.stat().st_size != second.stat().st_size:
        return False
    with first.open("rb") as first_handle, second.open("rb") as second_handle:
        while True:
            first_chunk = first_handle.read(1024 * 1024)
            second_chunk = second_handle.read(1024 * 1024)
            if first_chunk != second_chunk:
                return False
            if not first_chunk:
                return True


def _require_byte_equal(
    first: Path,
    second: Path,
    description: str,
    error_type: type[Exception],
) -> None:
    _require_regular_file(first, description + " archive copy", error_type)
    _require_regular_file(second, description + " runtime source", error_type)
    if not _files_are_equal(first, second):
        _fail(error_type, f"{description} differs between runtime and archive")


def _validate_consumed_runtime_seals(
    runtime: Path,
    manifest: dict[str, object],
    sha256_file: Callable[[Path], str],
    error_type: type[Exception],
) -> tuple[dict[str, object], dict[str, object]]:
    bindings = manifest["runtime_bindings"]
    if not isinstance(bindings, dict):
        _fail(error_type, "runtime bindings are not an object")
    artifact_path = runtime / "artifact-lock.json"
    runtime_lock_path = runtime / "runtime-lock.json"
    attempt_path = runtime / "launch-attempt.json"
    _require_pinned_file(
        artifact_path,
        ARTIFACT_LOCK_SIZE,
        ARTIFACT_LOCK_SHA256,
        "artifact lock",
        sha256_file,
        error_type,
    )
    _require_pinned_file(
        runtime_lock_path,
        RUNTIME_LOCK_SIZE,
        RUNTIME_LOCK_SHA256,
        "runtime lock",
        sha256_file,
        error_type,
    )
    _require_pinned_file(
        attempt_path,
        LAUNCH_ATTEMPT_SIZE,
        LAUNCH_ATTEMPT_SHA256,
        "launch-attempt seal",
        sha256_file,
        error_type,
    )
    if attempt_path.stat().st_mtime_ns != LAUNCH_ATTEMPT_MTIME_NS:
        _fail(error_type, "launch-attempt mtime changed")

    artifact = _load_json_object(artifact_path, "artifact lock", error_type)
    runtime_lock = _load_json_object(
        runtime_lock_path,
        "runtime lock",
        error_type,
    )
    attempt = _load_json_object(attempt_path, "launch-attempt seal", error_type)
    _require_exact_fields(
        artifact,
        {
            "bundle_sha256",
            "harness",
            "manifest_sha256",
            "members",
            "prelaunch_profile",
            "profile_id",
            "schema",
        },
        "artifact lock",
        error_type,
    )
    _require_exact_fields(
        runtime_lock,
        {
            "assets",
            "fabric_libraries",
            "generator",
            "launcher_files",
            "manifest_sha256",
            "native_classifiers",
            "profile_id",
            "provisioning_metadata",
            "schema",
            "vanilla_libraries",
            "version_metadata",
        },
        "runtime lock",
        error_type,
    )
    _require_exact_fields(
        attempt,
        {
            "artifact_lock",
            "assets",
            "created_at_unix_ns",
            "fabric_libraries",
            "generator",
            "java",
            "launch",
            "launcher_files",
            "manifest_sha256",
            "native_classifiers",
            "prelaunch_profile",
            "profile_id",
            "provisioning_metadata",
            "scenario",
            "scenario_verifier",
            "schema",
            "vanilla_libraries",
            "version_metadata",
        },
        "launch-attempt seal",
        error_type,
    )
    if (
        not _is_exact_int(artifact.get("schema"), 1)
        or artifact.get("profile_id") != PROFILE_ID
        or artifact.get("manifest_sha256") != PROFILE_SHA256
        or artifact.get("bundle_sha256")
        != _expected_artifacts()["reference_bundle"]["sha256"]
        or not _is_exact_int(runtime_lock.get("schema"), 1)
        or runtime_lock.get("profile_id") != PROFILE_ID
        or runtime_lock.get("manifest_sha256") != PROFILE_SHA256
        or not _is_exact_int(attempt.get("schema"), 1)
        or attempt.get("profile_id") != PROFILE_ID
        or attempt.get("manifest_sha256") != PROFILE_SHA256
        or attempt.get("scenario") != SCENARIO_ID
        or type(attempt.get("created_at_unix_ns")) is not int
        or int(attempt["created_at_unix_ns"]) <= 0
        or int(attempt["created_at_unix_ns"]) > LAUNCH_ATTEMPT_MTIME_NS
    ):
        _fail(error_type, "runtime seal identity changed")
    _require_exact_json(
        artifact.get("harness"),
        _expected_artifacts()["harness"],
        "artifact-lock harness",
        error_type,
    )
    _require_exact_json(
        attempt.get("scenario_verifier"),
        {
            "path": LAUNCH_VERIFIER_RELATIVE_PATH,
            "size": LAUNCH_VERIFIER_SIZE,
            "sha256": LAUNCH_VERIFIER_SHA256,
        },
        "launch-attempt verifier binding",
        error_type,
    )
    _require_exact_json(
        attempt.get("artifact_lock"),
        bindings["artifact_lock"],
        "launch-attempt artifact lock binding",
        error_type,
    )
    if attempt.get("prelaunch_profile") != artifact.get("prelaunch_profile"):
        _fail(error_type, "launch-attempt prelaunch profile changed")
    for name in (
        "assets",
        "fabric_libraries",
        "generator",
        "launcher_files",
        "native_classifiers",
        "provisioning_metadata",
        "vanilla_libraries",
        "version_metadata",
    ):
        if attempt.get(name) != runtime_lock.get(name):
            _fail(error_type, f"launch-attempt/runtime-lock cross-link changed: {name}")
    if (
        _canonical_json_sha256(attempt["launcher_files"])
        != IMMUTABLE_LAUNCHER_FILES_SHA256
    ):
        _fail(error_type, "sealed launcher-file inventory changed")

    production = _expected_artifacts()["production"]
    members = artifact.get("members")
    if not isinstance(members, list) or sum(
        member == production for member in members
    ) != 1:
        _fail(error_type, "artifact-lock production member changed")
    return artifact, attempt


def _validate_runtime_markers(
    runtime: Path,
    artifact: dict[str, object],
    error_type: type[Exception],
) -> None:
    profile_marker = _load_json_object(
        runtime / ".etherology-original-profile.json",
        "runtime profile marker",
        error_type,
    )
    _require_exact_fields(
        profile_marker,
        {
            "capture",
            "harness",
            "isolation",
            "managed_by",
            "manifest",
            "profile_id",
            "reference",
            "runtime",
            "schema",
        },
        "runtime profile marker",
        error_type,
    )
    if (
        not _is_exact_int(profile_marker.get("schema"), 1)
        or profile_marker.get("profile_id") != PROFILE_ID
        or profile_marker.get("managed_by")
        != "scripts/baseline/original_client.py"
    ):
        _fail(error_type, "runtime profile marker identity changed")
    _require_exact_json(
        profile_marker.get("isolation"),
        {
            "scope": "repository-owned-ignored-state",
            "source_profiles": [],
        },
        "runtime isolation marker",
        error_type,
    )
    _require_exact_json(
        profile_marker.get("manifest"),
        {
            "path": PROFILE_RELATIVE_PATH,
            "sha256": PROFILE_SHA256,
            "size": PROFILE_SIZE,
        },
        "runtime profile manifest marker",
        error_type,
    )
    _require_exact_json(
        profile_marker.get("harness"),
        _expected_artifacts()["harness"],
        "runtime profile harness marker",
        error_type,
    )
    reference = profile_marker.get("reference")
    if not isinstance(reference, dict) or (
        reference.get("reference_id") != REFERENCE_ID
        or reference.get("bundle_sha256") != artifact.get("bundle_sha256")
        or reference.get("members") != artifact.get("members")
    ):
        _fail(error_type, "runtime profile reference marker changed")

    evidence_marker = _load_json_object(
        runtime / "evidence/.etherology-original-evidence.json",
        "runtime evidence marker",
        error_type,
    )
    _require_exact_json(
        evidence_marker,
        {
            "capture": {
                "height": 1_080,
                "kind": "composed-minecraft-framebuffer",
                "width": 1_920,
            },
            "harness": {
                "mod_id": "etherology_original_baseline_harness",
                "sha256": HARNESS_SHA256,
                "size": HARNESS_SIZE,
                "version": "1.4.3",
            },
            "profile_id": PROFILE_ID,
            "reference_id": REFERENCE_ID,
            "scenario": {
                "completion_marker_file": "done.marker",
                "id": SCENARIO_ID,
                "report_file": "report.json",
                "screenshot_file": "pedestal-gallery.png",
                "screenshot_files": [
                    "pedestal-gallery.png",
                    "pedestal-transition-drops.png",
                    "pedestal-persistence-initial.png",
                    "pedestal-persistence-reopened.png",
                ],
                "world_directory_name": (
                    "etherology-original-pedestal-baseline-world"
                ),
                "world_display_name": "Etherology Original 0.1.7 Pedestal",
                "world_seed": 4_995_697_396_257_403_185,
            },
            "schema": 1,
        },
        "runtime evidence marker",
        error_type,
    )


def _require_exact_directory_inventory(
    directory: Path,
    expected: set[str],
    description: str,
    error_type: type[Exception],
) -> None:
    try:
        mode = directory.lstat().st_mode
    except OSError as error:
        raise error_type(
            f"Original Pedestal v14 archive: {description} is missing"
        ) from error
    if directory.is_symlink() or not stat.S_ISDIR(mode):
        _fail(error_type, f"{description} is linked or not a directory")
    actual = {path.name for path in directory.iterdir()}
    if actual != expected:
        _fail(error_type, f"{description} inventory changed")


def _validate_runtime_evidence_equality(
    runtime: Path,
    archive: Path,
    verification: dict[str, object],
    error_type: type[Exception],
) -> None:
    evidence = runtime / "evidence"
    scenario = evidence / SCENARIO_ID
    _require_exact_directory_inventory(
        evidence,
        {".etherology-original-evidence.json", SCENARIO_ID},
        "runtime evidence root",
        error_type,
    )
    _require_exact_directory_inventory(
        scenario,
        {"reports", "screenshots"},
        "runtime scenario evidence",
        error_type,
    )
    _require_exact_directory_inventory(
        scenario / "reports",
        {"report.json", "done.marker"},
        "runtime report directory",
        error_type,
    )
    _require_exact_directory_inventory(
        scenario / "screenshots",
        {
            "pedestal-gallery.png",
            "pedestal-transition-drops.png",
            "pedestal-persistence-initial.png",
            "pedestal-persistence-reopened.png",
        },
        "runtime screenshot directory",
        error_type,
    )
    for relative_path in (
        "reports/report.json",
        "reports/done.marker",
        *(
            relative_path
            for _phase, relative_path in _launch.EXPECTED_SCREENSHOTS
        ),
    ):
        _require_byte_equal(
            archive / relative_path,
            scenario / relative_path,
            relative_path,
            error_type,
        )
        if (
            scenario / relative_path
        ).stat().st_mtime_ns != EXPECTED_CAPTURE_MTIME_NS[relative_path]:
            _fail(error_type, f"runtime evidence mtime changed: {relative_path}")

    _require_exact_directory_inventory(
        runtime / "logs",
        {
            PurePosixPath(RUNTIME_CONTROLLER_LOG_RELATIVE_PATH).name,
            PurePosixPath(RUNTIME_VERIFICATION_RELATIVE_PATH).name,
        },
        "runtime controller-log directory",
        error_type,
    )
    _require_byte_equal(
        archive / "controller/original-client.log",
        runtime / RUNTIME_CONTROLLER_LOG_RELATIVE_PATH,
        "controller log",
        error_type,
    )
    _require_byte_equal(
        archive / "controller/verification.json",
        runtime / RUNTIME_VERIFICATION_RELATIVE_PATH,
        "controller verification",
        error_type,
    )
    if (
        runtime / RUNTIME_CONTROLLER_LOG_RELATIVE_PATH
    ).stat().st_mtime_ns != EXPECTED_CAPTURE_MTIME_NS[
        "controller/original-client.log"
    ]:
        _fail(error_type, "runtime controller-log mtime changed")
    if (
        runtime / RUNTIME_VERIFICATION_RELATIVE_PATH
    ).stat().st_mtime_ns != EXPECTED_CAPTURE_MTIME_NS[
        "controller/verification.json"
    ]:
        _fail(error_type, "runtime verification mtime changed")

    world = runtime / (
        "game/saves/etherology-original-pedestal-baseline-world"
    )
    if world.is_symlink() or not world.is_dir():
        _fail(error_type, "runtime saved world is missing or linked")
    for relative_path in EXPECTED_WORLD_RELATIVE_PATHS:
        archive_relative_path = "world-proof/" + relative_path
        _require_byte_equal(
            archive / archive_relative_path,
            world / relative_path,
            archive_relative_path,
            error_type,
        )
        if (
            world / relative_path
        ).stat().st_mtime_ns != EXPECTED_CAPTURE_MTIME_NS[archive_relative_path]:
            _fail(error_type, f"runtime world-proof mtime changed: {relative_path}")

    native_log = runtime / "game/logs/latest.log"
    _require_regular_file(native_log, "runtime native latest.log", error_type)
    controller_log = (archive / "controller/original-client.log").read_bytes()
    native_bytes = native_log.read_bytes()
    if controller_log[428:] != native_bytes:
        _fail(error_type, "runtime native log is not the controller-log suffix")
    controller_descriptor = verification.get("controller_log")
    if not isinstance(controller_descriptor, dict) or (
        controller_descriptor.get("path") != RUNTIME_CONTROLLER_LOG_RELATIVE_PATH
    ):
        _fail(error_type, "controller runtime-log path changed")


def validate_consumed_runtime(
    *,
    repository_root: Path,
    runtime_path: Path,
    archive_path: Path,
    sha256_file: Callable[[Path], str] = _sha256,
    error_type: type[Exception] = PedestalArchiveError,
) -> None:
    """Validates the preserved consumed runtime without making it launchable."""

    repository_input = repository_root.absolute()
    _require_expected_argument_path(
        repository_input,
        runtime_path,
        ACTIVE_RUNTIME_RELATIVE_PATH,
        "consumed runtime",
        error_type,
    )
    archive, manifest, files = _load_validated_archive(
        repository_root,
        archive_path,
        sha256_file,
        error_type,
    )
    _validate_report_and_marker(
        archive,
        manifest,
        sha256_file,
        error_type,
    )
    verification = _validate_controller_verification(
        archive,
        manifest,
        files,
        error_type,
    )
    root = repository_input.resolve(strict=True)
    runtime = _unlinked_relative_path(
        root,
        ACTIVE_RUNTIME_RELATIVE_PATH,
        "consumed runtime",
        error_type,
    )
    try:
        runtime_mode = runtime.lstat().st_mode
    except OSError as error:
        raise error_type(
            "Original Pedestal v14 archive: consumed runtime is missing"
        ) from error
    if runtime.is_symlink() or not stat.S_ISDIR(runtime_mode):
        _fail(error_type, "consumed runtime is linked or not a directory")
    process_state = runtime / "process.json"
    if process_state.exists() or process_state.is_symlink():
        _fail(error_type, "consumed runtime still has process.json")
    lifecycle_lock = runtime / "lifecycle.lock"
    _require_regular_file(lifecycle_lock, "runtime lifecycle lock", error_type)
    if lifecycle_lock.stat().st_size != 0:
        _fail(error_type, "runtime lifecycle lock is contaminated")
    _require_exact_directory_inventory(
        runtime / "game/crash-reports",
        set(),
        "runtime crash-report directory",
        error_type,
    )
    skins = runtime / "launcher/assets/skins"
    if skins.exists() or skins.is_symlink():
        _fail(error_type, "runtime mutable skin cache is present")
    artifact, _attempt = _validate_consumed_runtime_seals(
        runtime,
        manifest,
        sha256_file,
        error_type,
    )
    _validate_runtime_markers(runtime, artifact, error_type)
    _validate_runtime_evidence_equality(
        runtime,
        archive,
        verification,
        error_type,
    )
