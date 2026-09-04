#!/usr/bin/env python3
"""Strict contract/verifier for the fresh original Pedestal v12 lane."""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import sys
from typing import Callable


_BASE_VERIFIER_SIZE = 60_158
_BASE_VERIFIER_SHA256 = (
    "f62dacd22d255ee667162b39b0cc2b861c964f486548426daaffb3b436578dcd"
)
_BASE_VERIFIER_PATH = Path(__file__).with_name(
    "original_pedestal_evidence_v11.py"
)
_BASE_MODULE_NAME = "etherology_original_pedestal_v11_contract_for_v12"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


if (
    _BASE_VERIFIER_PATH.is_symlink()
    or not _BASE_VERIFIER_PATH.is_file()
    or _BASE_VERIFIER_PATH.stat().st_size != _BASE_VERIFIER_SIZE
    or _sha256(_BASE_VERIFIER_PATH) != _BASE_VERIFIER_SHA256
):
    raise RuntimeError("The immutable Pedestal v11 verifier changed")

_BASE_SPECIFICATION = importlib.util.spec_from_file_location(
    _BASE_MODULE_NAME,
    _BASE_VERIFIER_PATH,
)
if _BASE_SPECIFICATION is None or _BASE_SPECIFICATION.loader is None:
    raise RuntimeError("Cannot load the immutable Pedestal v11 verifier")
_base = importlib.util.module_from_spec(_BASE_SPECIFICATION)
sys.modules[_BASE_MODULE_NAME] = _base
_BASE_SPECIFICATION.loader.exec_module(_base)

SCENARIO_ID = "pedestal-baseline"
PROFILE_ID = "etherology-original-fabric-1.21.1-published-0.1.7-v12"
PROFILE_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v12.json"
)
PROFILE_SIZE = 10_307
PROFILE_SHA256 = "bcf54994a6245284292adb4056a22b24c29fdaaec60a90579d2c1eac95c10c6a"
PREVIOUS_PROFILE_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v11.json"
)
PREVIOUS_PROFILE_SIZE = 10_307
PREVIOUS_PROFILE_SHA256 = (
    "34974855dd861c220915dd77ce694d3e5175c97e1c8f6edea0806601947e0cfc"
)
HARNESS_VERSION = "1.4.1"
HARNESS_STATUS = "implemented"
HARNESS_FILE = (
    "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.4.1.jar"
)
HARNESS_SIZE = 340_250
HARNESS_SHA256 = "a99809d6443a4757c860e98d2f09e1d5775667a69e331a7e631930eb5728c7eb"
CONTRACT_SOURCE_SIZE = 17_454
CONTRACT_SOURCE_SHA256 = (
    "9bf244347a1b0a1d640efc762385f83fb3e713714aee55ef0547030a35d53a84"
)
SCENARIO_SOURCE_SIZE = 164_465
SCENARIO_SOURCE_SHA256 = (
    "b9080e5cfa22d6b320defae2e5306061fe4e97d05da7c2f0bdb3b3474a4f077f"
)
WRITER_SOURCE_SIZE = 6_284
WRITER_SOURCE_SHA256 = (
    "bd1cb0271420bc9fd277dfc026efb15118343ac1ee876e54a784c68f93398e5a"
)
FRESH_ARCHIVE_RELATIVE_PATH = (
    "docs/evidence/original-1.21.1/pedestal-v12"
)
FRESH_ARCHIVE_README_SIZE = 1_343
FRESH_ARCHIVE_README_SHA256 = (
    "e070b018228c59d5a00318aa7f3536386c1cd717ec2338335cdc55daec16e38a"
)

for _name, _value in {
    "SCENARIO_ID": SCENARIO_ID,
    "PROFILE_ID": PROFILE_ID,
    "PROFILE_RELATIVE_PATH": PROFILE_RELATIVE_PATH,
    "PROFILE_SIZE": PROFILE_SIZE,
    "PROFILE_SHA256": PROFILE_SHA256,
    "HARNESS_VERSION": HARNESS_VERSION,
    "HARNESS_STATUS": HARNESS_STATUS,
    "HARNESS_FILE": HARNESS_FILE,
    "HARNESS_SIZE": HARNESS_SIZE,
    "HARNESS_SHA256": HARNESS_SHA256,
    "CONTRACT_SOURCE_SIZE": CONTRACT_SOURCE_SIZE,
    "CONTRACT_SOURCE_SHA256": CONTRACT_SOURCE_SHA256,
    "SCENARIO_SOURCE_SIZE": SCENARIO_SOURCE_SIZE,
    "SCENARIO_SOURCE_SHA256": SCENARIO_SOURCE_SHA256,
    "WRITER_SOURCE_SIZE": WRITER_SOURCE_SIZE,
    "WRITER_SOURCE_SHA256": WRITER_SOURCE_SHA256,
}.items():
    setattr(_base, _name, _value)

_V11_DIAGNOSTIC_FILES = {
    "README.md": (
        2_665,
        "61928fe5d500db12bb0922dc8010da40ac375272305a75f8f2944e6371c6cc93",
    ),
    "diagnostic-manifest.json": (
        6_407,
        "7e4d3d357ba43644de7dcd43414645780f7f3cd6e567de60636fdb2e185818fa",
    ),
    "controller/original-client.log": (
        14_007,
        "d544929d2bcc91e4c4f761ef6893d96521164903bae9c3a317402bf66f5e46c1",
    ),
    "diagnostics/playerdata.dat": (
        1_211,
        "73b5800ae3ac59d9ffabe2194686b4cc06a9e620c0a28ae215796741a2b929fc",
    ),
    "diagnostics/playerdata.dat_old": (
        1_211,
        "2f0a1410867b864b2ae414bd0e3141de2a5ee6bca4e0bddbb661eb9dfd8dd9b0",
    ),
}


def _validate_consumed_v11_history(
    repository_root: Path,
    error_type: type[Exception],
) -> None:
    previous_profile = repository_root / PREVIOUS_PROFILE_RELATIVE_PATH
    if (
        previous_profile.is_symlink()
        or not previous_profile.is_file()
        or previous_profile.stat().st_size != PREVIOUS_PROFILE_SIZE
        or _sha256(previous_profile) != PREVIOUS_PROFILE_SHA256
    ):
        raise error_type("The consumed Pedestal v11 profile changed")
    archive = repository_root / "docs/evidence/original-1.21.1/pedestal-v11"
    if archive.is_symlink() or not archive.is_dir():
        raise error_type("The consumed Pedestal v11 diagnostic archive is missing")
    actual_directories = {
        path.relative_to(archive).as_posix()
        for path in archive.rglob("*")
        if path.is_dir()
    }
    if (
        actual_directories != {"controller", "diagnostics"}
        or any((archive / name).is_symlink() for name in actual_directories)
    ):
        raise error_type("The consumed Pedestal v11 diagnostic directories changed")
    actual_files = {
        path.relative_to(archive).as_posix()
        for path in archive.rglob("*")
        if path.is_file()
    }
    if actual_files != set(_V11_DIAGNOSTIC_FILES):
        raise error_type("The consumed Pedestal v11 diagnostic inventory changed")
    for relative_path, (size, sha256) in _V11_DIAGNOSTIC_FILES.items():
        path = archive / relative_path
        if (
            path.is_symlink()
            or not path.is_file()
            or path.stat().st_size != size
            or _sha256(path) != sha256
        ):
            raise error_type(
                "The consumed Pedestal v11 diagnostic bytes changed: "
                + relative_path
            )
    manifest = json.loads(
        (archive / "diagnostic-manifest.json").read_text(encoding="utf-8")
    )
    if (
        manifest.get("accepted") is not False
        or manifest.get("scenario") != SCENARIO_ID
        or manifest.get("profile", {}).get("id")
        != "etherology-original-fabric-1.21.1-published-0.1.7-v11"
        or manifest.get("runtime", {}).get("source_profiles") != []
        or manifest.get("outcome", {}).get("screenshot_count") != 0
        or manifest.get("outcome", {}).get("report_published") is not False
    ):
        raise error_type("The consumed Pedestal v11 diagnostic meaning changed")


def validate_fresh_archive(
    *,
    repository_root: Path,
    archive_path: Path,
    sha256_file: Callable[[Path], str] = _sha256,
    error_type: type[Exception] = _base.PedestalEvidenceError,
) -> None:
    """Proves the v12 documentation target is the exact fresh placeholder."""

    repository_input = repository_root.absolute()
    expected_input = repository_input / FRESH_ARCHIVE_RELATIVE_PATH
    if archive_path.absolute() != expected_input.absolute():
        raise error_type("The fresh Pedestal v12 archive path changed")
    root = repository_input.resolve(strict=True)
    expected_archive = root / FRESH_ARCHIVE_RELATIVE_PATH
    current = root
    for component in Path(FRESH_ARCHIVE_RELATIVE_PATH).parts:
        current /= component
        if current.is_symlink():
            raise error_type("The fresh Pedestal v12 archive path is linked")
    if not expected_archive.is_dir():
        raise error_type("The fresh Pedestal v12 archive is missing")
    inventory = tuple(sorted(path.name for path in expected_archive.iterdir()))
    if inventory != ("README.md",):
        raise error_type("The fresh Pedestal v12 archive inventory changed")
    readme = expected_archive / "README.md"
    if (
        readme.is_symlink()
        or not readme.is_file()
        or readme.stat().st_size != FRESH_ARCHIVE_README_SIZE
        or sha256_file(readme) != FRESH_ARCHIVE_README_SHA256
    ):
        raise error_type("The fresh Pedestal v12 archive README changed")


def validate_pinned_contract(
    *,
    repository_root: Path,
    manifest_path: Path,
    harness_path: Path,
    sha256_file: Callable[[Path], str] = _sha256,
    error_type: type[Exception] = _base.PedestalEvidenceError,
) -> None:
    """Proves the fresh v12 inputs and the consumed v11 history are pinned."""

    _base.validate_pinned_contract(
        repository_root=repository_root,
        manifest_path=manifest_path,
        harness_path=harness_path,
        sha256_file=sha256_file,
        error_type=error_type,
    )
    _validate_consumed_v11_history(repository_root, error_type)


def validate_fresh_contract(
    *,
    repository_root: Path,
    manifest_path: Path,
    harness_path: Path,
    runtime_path: Path,
    archive_path: Path,
    sha256_file: Callable[[Path], str] = _sha256,
    error_type: type[Exception] = _base.PedestalEvidenceError,
) -> None:
    """Proves the v12 profile and archive have never been consumed."""

    _validate_consumed_v11_history(repository_root, error_type)
    _base.validate_fresh_contract(
        repository_root=repository_root,
        manifest_path=manifest_path,
        harness_path=harness_path,
        runtime_path=runtime_path,
        archive_path=archive_path,
        sha256_file=sha256_file,
        error_type=error_type,
    )
    validate_fresh_archive(
        repository_root=repository_root,
        archive_path=archive_path,
        sha256_file=sha256_file,
        error_type=error_type,
    )


def validate_evidence(**arguments: object):
    """Validates v12 evidence while keeping v11 diagnostic history immutable."""

    error_type = arguments.get("error_type", _base.PedestalEvidenceError)
    _validate_consumed_v11_history(Path(__file__).resolve().parents[2], error_type)
    return _base.validate_evidence(**arguments)


def __getattr__(name: str):
    return getattr(_base, name)
