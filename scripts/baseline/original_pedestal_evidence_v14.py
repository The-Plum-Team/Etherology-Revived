#!/usr/bin/env python3
"""Strict contract/verifier for the fresh original Pedestal v14 lane."""

from __future__ import annotations

import copy
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
_BASE_MODULE_NAME = "etherology_original_pedestal_v11_contract_for_v14"


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
PROFILE_ID = "etherology-original-fabric-1.21.1-published-0.1.7-v14"
PROFILE_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v14.json"
)
PROFILE_SIZE = 10_307
PROFILE_SHA256 = "ddee58342b8a0e6c45bea375247243be5cc20bfa1680ef28f0d9ab5c72518962"
ACTIVE_RUNTIME_RELATIVE_PATH = "scripts/baseline/.state/runtimes/" + PROFILE_ID
HARNESS_VERSION = "1.4.3"
HARNESS_STATUS = "implemented"
HARNESS_FILE = (
    "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.4.3.jar"
)
HARNESS_SIZE = 340_723
HARNESS_SHA256 = "9a329ff219f4403c8880597ed851a73843c74adf81ac4b5561b6708cf82129b6"
CONTRACT_SOURCE_SIZE = 17_454
CONTRACT_SOURCE_SHA256 = (
    "9bf244347a1b0a1d640efc762385f83fb3e713714aee55ef0547030a35d53a84"
)
SCENARIO_SOURCE_SIZE = 167_523
SCENARIO_SOURCE_SHA256 = (
    "137f6b993f7cddd7487288790da6bfe1d779f10083ca16af98da86b030cd46da"
)
WRITER_SOURCE_SIZE = 6_284
WRITER_SOURCE_SHA256 = (
    "bd1cb0271420bc9fd277dfc026efb15118343ac1ee876e54a784c68f93398e5a"
)
FRESH_ARCHIVE_RELATIVE_PATH = "docs/evidence/original-1.21.1/pedestal-v14"
FRESH_ARCHIVE_README_SIZE = 1_943
FRESH_ARCHIVE_README_SHA256 = (
    "4701354e7005ad5f4d3dd0d91b69c878935e3985a9045109bb6067b5f6673f58"
)

_V11_PROFILE_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v11.json"
)
_V11_PROFILE_ID = "etherology-original-fabric-1.21.1-published-0.1.7-v11"
_V11_PROFILE_SIZE = 10_307
_V11_PROFILE_SHA256 = (
    "34974855dd861c220915dd77ce694d3e5175c97e1c8f6edea0806601947e0cfc"
)
_V11_DIAGNOSTIC_RELATIVE_PATH = "docs/evidence/original-1.21.1/pedestal-v11"
_V11_DIAGNOSTIC_DIRECTORIES = {"controller", "diagnostics"}
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

_V12_PROFILE_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v12.json"
)
_V12_PROFILE_ID = "etherology-original-fabric-1.21.1-published-0.1.7-v12"
_V12_PROFILE_SIZE = 10_307
_V12_PROFILE_SHA256 = (
    "bcf54994a6245284292adb4056a22b24c29fdaaec60a90579d2c1eac95c10c6a"
)
_V12_VERIFIER_RELATIVE_PATH = "scripts/baseline/original_pedestal_evidence_v12.py"
_V12_VERIFIER_SIZE = 10_044
_V12_VERIFIER_SHA256 = (
    "4b6ceced8ac0e406b43b5cc6514857f0b6a59a084d07117750632238b0266fb4"
)
_V12_DIAGNOSTIC_RELATIVE_PATH = "docs/evidence/original-1.21.1/pedestal-v12"
_V12_DIAGNOSTIC_DIRECTORIES = {"controller", "reports"}
_V12_REPORT_SHA256 = (
    "319fc02bc194678484ae158569d950f4d4a6e0c26d3e21af48479c631fba055a"
)
_V12_DIAGNOSTIC_FILES = {
    "README.md": (
        2_875,
        "e243e33846e0c172a8cb44c4349f041eca1b5f7c6b5ba329f8dc4bbdfeff9f38",
    ),
    "diagnostic-manifest.json": (
        7_454,
        "6b54b6d8f50aadda16e83368d0f673c6cc918868ca4612d5146bc74ed936fe87",
    ),
    "controller/original-client.log": (
        19_386,
        "1610faaa566f9c974c89764792d24ede6ac1ee8dafd54a5efac2d5f8403deb42",
    ),
    "reports/report.json": (41_416, _V12_REPORT_SHA256),
    "reports/done.marker": (
        104,
        "6708c57df4494db0ae9ab75fd250d52ea8dce9ffd97aab5248922468c71e6939",
    ),
}

_V13_PROFILE_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v13.json"
)
_V13_PROFILE_ID = "etherology-original-fabric-1.21.1-published-0.1.7-v13"
_V13_PROFILE_SIZE = 10_307
_V13_PROFILE_SHA256 = (
    "61e9d189041a826bfc8375884e559c26a38bbb5e109eff5279b315374c91fe9c"
)
_V13_VERIFIER_RELATIVE_PATH = "scripts/baseline/original_pedestal_evidence_v13.py"
_V13_VERIFIER_SIZE = 26_963
_V13_VERIFIER_SHA256 = (
    "221afc8f88d11a60d94dc4bd94f1dd54b93e57cb93a387b336a8987d341afabe"
)
_V13_DIAGNOSTIC_RELATIVE_PATH = "docs/evidence/original-1.21.1/pedestal-v13"
_V13_DIAGNOSTIC_DIRECTORIES = {"controller", "reports", "screenshots"}
_V13_REPORT_SHA256 = (
    "fc840f34231676167834d453fcb73ab3bc7289c3a56f0cbeb6455ac96c29a563"
)
_V13_SCREENSHOT_RELATIVE_PATH = "screenshots/pedestal-gallery.png"
_V13_SCREENSHOT_SHA256 = (
    "fc0e6c832d7a26103555fbb61aaf2fd3ca9964db40adbb0f200e1fab831703f0"
)
_V13_DIAGNOSTIC_FILES = {
    "README.md": (
        3_126,
        "b91e2b380fee5bda51fa4b6b405b0f86c5e31a904efb282928fcce60dfa239bc",
    ),
    "diagnostic-manifest.json": (
        9_968,
        "ac311a0331b4aecad8eecd0aec803ac70d7af5572203996c096bbb7453b3c5b6",
    ),
    "controller/original-client.log": (
        21_738,
        "d85109c8a3a4eade11a67398a4a6deabecd0b2319a96d768cf6c63a44b915928",
    ),
    "reports/report.json": (51_222, _V13_REPORT_SHA256),
    "reports/done.marker": (
        104,
        "51846087b089ed0d9a0200246a0212ef550d1f949ff1f0c37d6250a551bd21aa",
    ),
    _V13_SCREENSHOT_RELATIVE_PATH: (284_835, _V13_SCREENSHOT_SHA256),
}

_V13_LIFECYCLE_FAILURE = (
    "Timed out in WAITING_FOR_CLIENT_MIRROR after 6000 client ticks; "
    "capture_phase=transition-drops; camera=first_person=true;x=0.5;y=121.0;"
    "z=-15.5;yaw=0.0;pitch=10.0;on_ground=true"
)
CANONICAL_TRANSITION_DROPS = {
    "minecraft:blue_carpet": 1,
    "minecraft:diamond": 1,
    "minecraft:emerald": 1,
    "minecraft:red_carpet": 1,
}
CLIENT_DROP_DIAGNOSTIC_FIELDS = {
    "expected",
    "mirror_ready",
    "mirror_ready_recorded",
    "mirror_ready_exact",
    "capture",
    "capture_recorded",
    "capture_exact",
}
_V14_CLIENT_DROP_PROVENANCE_SOURCE_FRAGMENTS = (
    "private boolean transitionClientDropsAtMirrorRecorded;",
    "private boolean transitionClientDropsAtCaptureRecorded;",
    "transitionClientDropsAtMirrorRecorded = true;",
    "transitionClientDropsAtCaptureRecorded = true;",
    '"mirror_ready_recorded",',
    '"capture_recorded",',
    "transitionClientDropsAtMirrorRecorded\n"
    "                        && transitionClientDropsAtMirror.equals(",
    "transitionClientDropsAtCaptureRecorded\n"
    "                        && transitionClientDropsAtCapture.equals(",
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


def _raise(error_type: type[Exception], message: str) -> None:
    raise error_type("Original Pedestal v14 evidence: " + message)


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


def _load_json(
    path: Path,
    description: str,
    error_type: type[Exception],
) -> dict[str, object]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise error_type(
            f"Original Pedestal v14 evidence: {description} is not readable JSON"
        ) from error
    if not isinstance(value, dict):
        _raise(error_type, f"{description} is not a JSON object")
    return value


def _require_pinned_file(
    path: Path,
    size: int,
    sha256: str,
    description: str,
    error_type: type[Exception],
) -> None:
    if (
        path.is_symlink()
        or not path.is_file()
        or path.stat().st_size != size
        or _sha256(path) != sha256
    ):
        _raise(error_type, f"{description} changed")


def _unlinked_relative_path(
    repository_root: Path,
    relative_path: str,
    description: str,
    error_type: type[Exception],
) -> Path:
    current = repository_root
    for component in Path(relative_path).parts:
        current /= component
        if current.is_symlink():
            _raise(error_type, f"{description} contains a linked path")
    return current


def _validate_archive_bytes(
    *,
    repository_root: Path,
    relative_path: str,
    expected_directories: set[str],
    expected_files: dict[str, tuple[int, str]],
    description: str,
    error_type: type[Exception],
) -> Path:
    archive = _unlinked_relative_path(
        repository_root,
        relative_path,
        description,
        error_type,
    )
    if not archive.is_dir():
        _raise(error_type, f"{description} is missing")
    entries = tuple(archive.rglob("*"))
    if any(path.is_symlink() for path in entries):
        _raise(error_type, f"{description} contains a linked entry")
    actual_entries = {path.relative_to(archive).as_posix() for path in entries}
    expected_entries = expected_directories | set(expected_files)
    if actual_entries != expected_entries:
        _raise(error_type, f"{description} inventory changed")
    actual_directories = {
        path.relative_to(archive).as_posix()
        for path in entries
        if path.is_dir()
    }
    if actual_directories != expected_directories:
        _raise(error_type, f"{description} directory inventory changed")
    for relative_file, (size, sha256) in expected_files.items():
        _require_pinned_file(
            archive / relative_file,
            size,
            sha256,
            f"{description} file {relative_file}",
            error_type,
        )
    return archive


def _validate_assertion_outcome(
    report: dict[str, object],
    *,
    expected_true: int,
    expected_false: int,
    description: str,
    error_type: type[Exception],
) -> None:
    assertions = report.get("assertions")
    if not isinstance(assertions, list) or len(assertions) != 74:
        _raise(error_type, f"{description} assertion count changed")
    if any(
        not isinstance(assertion, dict)
        or set(assertion) != {"name", "passed", "expected", "actual"}
        or type(assertion.get("passed")) is not bool
        for assertion in assertions
    ):
        _raise(error_type, f"{description} assertion records changed")
    if tuple(assertion["name"] for assertion in assertions) != (
        _base.EXPECTED_ASSERTION_NAMES
    ):
        _raise(error_type, f"{description} assertion inventory changed")
    if (
        sum(assertion["passed"] is True for assertion in assertions)
        != expected_true
        or sum(assertion["passed"] is False for assertion in assertions)
        != expected_false
    ):
        _raise(error_type, f"{description} assertion outcome changed")


def _validate_consumed_v11_history(
    repository_root: Path,
    error_type: type[Exception],
) -> None:
    root = repository_root.resolve(strict=True)
    _require_pinned_file(
        root / _V11_PROFILE_RELATIVE_PATH,
        _V11_PROFILE_SIZE,
        _V11_PROFILE_SHA256,
        "consumed Pedestal v11 profile",
        error_type,
    )
    archive = _validate_archive_bytes(
        repository_root=root,
        relative_path=_V11_DIAGNOSTIC_RELATIVE_PATH,
        expected_directories=_V11_DIAGNOSTIC_DIRECTORIES,
        expected_files=_V11_DIAGNOSTIC_FILES,
        description="consumed Pedestal v11 diagnostic archive",
        error_type=error_type,
    )
    manifest = _load_json(
        archive / "diagnostic-manifest.json",
        "consumed Pedestal v11 diagnostic manifest",
        error_type,
    )
    profile = manifest.get("profile")
    runtime = manifest.get("runtime")
    outcome = manifest.get("outcome")
    if (
        not isinstance(profile, dict)
        or not isinstance(runtime, dict)
        or not isinstance(outcome, dict)
        or manifest.get("accepted") is not False
        or manifest.get("scenario") != SCENARIO_ID
        or profile.get("id") != _V11_PROFILE_ID
        or runtime.get("source_profiles") != []
        or outcome.get("screenshot_count") != 0
        or outcome.get("report_published") is not False
    ):
        _raise(error_type, "consumed Pedestal v11 diagnostic meaning changed")


def _validate_v12_manifest_semantics(
    manifest: dict[str, object],
    error_type: type[Exception],
) -> None:
    if (
        not _is_exact_int(manifest.get("schema"), 1)
        or manifest.get("kind")
        != "etherology-original-fabric-consumed-run-diagnostic"
        or manifest.get("accepted") is not False
        or manifest.get("verification_scope")
        != "archived-failed-report-and-log-integrity-without-mechanic-acceptance"
        or manifest.get("scenario") != SCENARIO_ID
        or not _is_exact_json(
            manifest.get("profile"),
            {
                "id": _V12_PROFILE_ID,
                "manifest_path": _V12_PROFILE_RELATIVE_PATH,
                "manifest_size": _V12_PROFILE_SIZE,
                "manifest_sha256": _V12_PROFILE_SHA256,
            },
        )
    ):
        _raise(error_type, "consumed Pedestal v12 diagnostic identity changed")
    runtime = manifest.get("runtime")
    controller = manifest.get("controller")
    outcome = manifest.get("outcome")
    if not isinstance(runtime, dict) or (
        runtime.get("source_profiles") != []
        or runtime.get("preserved_path")
        != "scripts/baseline/.state/runtimes/" + _V12_PROFILE_ID
        or runtime.get("must_never_run_again") is not True
    ):
        _raise(error_type, "consumed Pedestal v12 runtime meaning changed")
    if not isinstance(controller, dict) or (
        not _is_exact_int(controller.get("observed_exit_code"), 2)
        or controller.get("clean_shutdown") is not False
        or controller.get("controller_verification_published") is not False
    ):
        _raise(error_type, "consumed Pedestal v12 terminal outcome changed")
    if not isinstance(outcome, dict) or (
        outcome.get("report_published") is not True
        or outcome.get("completion_marker_published") is not True
        or outcome.get("report_status") != "failed"
        or outcome.get("report_passed") is not False
        or not _is_exact_int(outcome.get("client_ticks"), 155)
        or not _is_exact_int(outcome.get("assertion_count"), 74)
        or not _is_exact_int(outcome.get("assertions_true_at_publication"), 32)
        or not _is_exact_int(outcome.get("assertions_false_at_publication"), 42)
        or not _is_exact_int(outcome.get("screenshot_count"), 0)
        or outcome.get("clean_shutdown") is not False
    ):
        _raise(error_type, "consumed Pedestal v12 failed outcome changed")
    diagnosis = outcome.get("diagnosis")
    if not isinstance(diagnosis, dict) or (
        diagnosis.get("limitation")
        != "No v12 result is accepted as native Pedestal evidence."
    ):
        _raise(error_type, "consumed Pedestal v12 diagnostic limitation changed")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or not _is_exact_json(
        artifacts.get("scenario_verifier"),
        {
            "path": _V12_VERIFIER_RELATIVE_PATH,
            "size": _V12_VERIFIER_SIZE,
            "sha256": _V12_VERIFIER_SHA256,
        },
    ):
        _raise(error_type, "consumed Pedestal v12 verifier provenance changed")
    files = manifest.get("files")
    expected_payloads = {
        name: {"size": size, "sha256": sha256}
        for name, (size, sha256) in _V12_DIAGNOSTIC_FILES.items()
        if name not in {"README.md", "diagnostic-manifest.json"}
    }
    if not isinstance(files, dict) or set(files) != set(expected_payloads):
        _raise(error_type, "consumed Pedestal v12 payload manifest changed")
    for name, expected in expected_payloads.items():
        entry = files.get(name)
        if not isinstance(entry, dict) or (
            not _is_exact_int(entry.get("size"), expected["size"])
            or entry.get("sha256") != expected["sha256"]
        ):
            _raise(error_type, f"consumed Pedestal v12 payload pin changed: {name}")


def _validate_consumed_v12_history(
    repository_root: Path,
    error_type: type[Exception],
) -> None:
    root = repository_root.resolve(strict=True)
    _require_pinned_file(
        root / _V12_PROFILE_RELATIVE_PATH,
        _V12_PROFILE_SIZE,
        _V12_PROFILE_SHA256,
        "consumed Pedestal v12 profile",
        error_type,
    )
    _require_pinned_file(
        root / _V12_VERIFIER_RELATIVE_PATH,
        _V12_VERIFIER_SIZE,
        _V12_VERIFIER_SHA256,
        "consumed Pedestal v12 verifier",
        error_type,
    )
    archive = _validate_archive_bytes(
        repository_root=root,
        relative_path=_V12_DIAGNOSTIC_RELATIVE_PATH,
        expected_directories=_V12_DIAGNOSTIC_DIRECTORIES,
        expected_files=_V12_DIAGNOSTIC_FILES,
        description="consumed Pedestal v12 diagnostic archive",
        error_type=error_type,
    )
    manifest = _load_json(
        archive / "diagnostic-manifest.json",
        "consumed Pedestal v12 diagnostic manifest",
        error_type,
    )
    _validate_v12_manifest_semantics(manifest, error_type)
    report = _load_json(
        archive / "reports/report.json",
        "consumed Pedestal v12 failed report",
        error_type,
    )
    if (
        report.get("status") != "failed"
        or report.get("passed") is not False
        or not _is_exact_int(report.get("client_ticks"), 155)
        or not str(report.get("lifecycle_failure", "")).startswith(
            "Pedestal server operation failed: IllegalStateException: "
            "Directional dispenser probe did not match the contract"
        )
    ):
        _raise(error_type, "consumed Pedestal v12 failed report meaning changed")
    _validate_assertion_outcome(
        report,
        expected_true=32,
        expected_false=42,
        description="consumed Pedestal v12 report",
        error_type=error_type,
    )
    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or any(
        not isinstance(screenshot, dict)
        or not _is_exact_int(screenshot.get("width"), 0)
        or not _is_exact_int(screenshot.get("height"), 0)
        or not _is_exact_int(screenshot.get("size"), 0)
        or screenshot.get("sha256") != ""
        for screenshot in screenshots
    ):
        _raise(error_type, "consumed Pedestal v12 no-screenshot outcome changed")
    marker = (archive / "reports/done.marker").read_text(encoding="utf-8")
    expected_marker = (
        f"{SCENARIO_ID}:failed\nreport_sha256:{_V12_REPORT_SHA256}\n"
    )
    if marker != expected_marker:
        _raise(error_type, "consumed Pedestal v12 failed marker meaning changed")


def _validate_v13_manifest_semantics(
    manifest: dict[str, object],
    error_type: type[Exception],
) -> None:
    expected_archive_paths = [
        "README.md",
        "controller/original-client.log",
        "diagnostic-manifest.json",
        "reports/done.marker",
        "reports/report.json",
        _V13_SCREENSHOT_RELATIVE_PATH,
    ]
    if (
        not _is_exact_int(manifest.get("schema"), 1)
        or manifest.get("kind")
        != "etherology-original-fabric-consumed-run-diagnostic"
        or manifest.get("accepted") is not False
        or manifest.get("verification_scope")
        != (
            "archived-failed-report-log-and-sole-screenshot-integrity-"
            "without-mechanic-acceptance"
        )
        or manifest.get("scenario") != SCENARIO_ID
        or not _is_exact_json(
            manifest.get("archive_policy"),
            {
                "exact_relative_paths": expected_archive_paths,
                "symlinks_allowed": False,
                "copied_runtime_artifacts_are_byte_for_byte": True,
            },
        )
        or not _is_exact_json(
            manifest.get("profile"),
            {
                "id": _V13_PROFILE_ID,
                "manifest_path": _V13_PROFILE_RELATIVE_PATH,
                "manifest_size": _V13_PROFILE_SIZE,
                "manifest_sha256": _V13_PROFILE_SHA256,
            },
        )
    ):
        _raise(error_type, "consumed Pedestal v13 diagnostic identity changed")

    runtime = manifest.get("runtime")
    controller = manifest.get("controller")
    outcome = manifest.get("outcome")
    if not isinstance(runtime, dict) or (
        runtime.get("source_profiles") != []
        or runtime.get("preserved_path")
        != "scripts/baseline/.state/runtimes/" + _V13_PROFILE_ID
        or runtime.get("must_never_run_again") is not True
    ):
        _raise(error_type, "consumed Pedestal v13 runtime meaning changed")
    if not isinstance(controller, dict) or (
        not _is_exact_int(controller.get("timeout_seconds"), 1_800)
        or not _is_exact_int(
            controller.get("failed_completion_shutdown_grace_seconds"),
            15,
        )
        or controller.get("native_process_exit_observed") is not True
        or controller.get("controller_process_exit_observed") is not True
        or controller.get("process_group_forcibly_terminated") is not False
        or not _is_exact_int(controller.get("observed_exit_code"), 2)
        or controller.get("clean_shutdown") is not True
        or controller.get("controller_verification_published") is not False
    ):
        _raise(error_type, "consumed Pedestal v13 terminal outcome changed")
    if not isinstance(outcome, dict) or (
        outcome.get("report_published") is not True
        or outcome.get("completion_marker_published") is not True
        or outcome.get("report_status") != "failed"
        or outcome.get("report_passed") is not False
        or not _is_exact_int(outcome.get("client_ticks"), 7_996)
        or not _is_exact_int(outcome.get("assertion_count"), 74)
        or not _is_exact_int(outcome.get("assertions_true_at_publication"), 49)
        or not _is_exact_int(outcome.get("assertions_false_at_publication"), 25)
        or not _is_exact_int(outcome.get("expected_screenshot_count"), 4)
        or not _is_exact_int(outcome.get("screenshot_count"), 1)
        or not _is_exact_json(
            outcome.get("missing_screenshots"),
            [
                "pedestal-persistence-initial.png",
                "pedestal-persistence-reopened.png",
                "pedestal-transition-drops.png",
            ],
        )
        or not _is_exact_json(outcome.get("extra_screenshots"), [])
        or outcome.get("clean_shutdown") is not True
        or not _is_exact_json(
            outcome.get("lifecycle_failure"),
            {
                "stage": "WAITING_FOR_CLIENT_MIRROR",
                "capture_phase": "transition-drops",
                "stage_tick_limit": 6_000,
                "stage_tick_unit": "client ticks elapsed in the current stage",
                "stage_started_at_client_tick": 1_996,
                "failed_at_client_tick": 7_996,
                "message": _V13_LIFECYCLE_FAILURE,
            },
        )
    ):
        _raise(error_type, "consumed Pedestal v13 failed outcome changed")
    diagnosis = outcome.get("diagnosis")
    if not isinstance(diagnosis, dict) or (
        diagnosis.get("classification")
        != "direct-observation-without-mechanic-acceptance"
        or diagnosis.get("underlying_cause")
        != "not proven by the retained diagnostic artifacts"
        or diagnosis.get("limitation")
        != (
            "No v13 result, including the sole gallery screenshot, is accepted "
            "as native Pedestal behavior evidence."
        )
    ):
        _raise(error_type, "consumed Pedestal v13 diagnostic limitation changed")

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or not _is_exact_json(
        artifacts.get("scenario_verifier"),
        {
            "path": _V13_VERIFIER_RELATIVE_PATH,
            "size": _V13_VERIFIER_SIZE,
            "sha256": _V13_VERIFIER_SHA256,
        },
    ):
        _raise(error_type, "consumed Pedestal v13 verifier provenance changed")
    files = manifest.get("files")
    expected_payloads = {
        name: {"size": size, "sha256": sha256}
        for name, (size, sha256) in _V13_DIAGNOSTIC_FILES.items()
        if name != "diagnostic-manifest.json"
    }
    if not isinstance(files, dict) or set(files) != set(expected_payloads):
        _raise(error_type, "consumed Pedestal v13 payload manifest changed")
    for name, expected in expected_payloads.items():
        entry = files.get(name)
        if not isinstance(entry, dict) or (
            not _is_exact_int(entry.get("size"), expected["size"])
            or entry.get("sha256") != expected["sha256"]
        ):
            _raise(error_type, f"consumed Pedestal v13 payload pin changed: {name}")
    screenshot = files.get(_V13_SCREENSHOT_RELATIVE_PATH)
    if not isinstance(screenshot, dict) or (
        not _is_exact_int(screenshot.get("width"), 1_920)
        or not _is_exact_int(screenshot.get("height"), 1_080)
        or screenshot.get("source") != "minecraft-framebuffer"
        or screenshot.get("edited") is not False
        or screenshot.get("accepted_as_mechanic_evidence") is not False
    ):
        _raise(error_type, "consumed Pedestal v13 screenshot meaning changed")


def _validate_v13_report_semantics(
    report: dict[str, object],
    error_type: type[Exception],
) -> None:
    if (
        not _is_exact_int(report.get("schema"), 4)
        or report.get("reference_id") != "published-0.1.7"
        or report.get("scenario") != SCENARIO_ID
        or report.get("lane") != "fabric-1.21.1-original"
        or report.get("status") != "failed"
        or report.get("passed") is not False
        or not _is_exact_int(report.get("client_ticks"), 7_996)
        or report.get("lifecycle_failure") != _V13_LIFECYCLE_FAILURE
    ):
        _raise(error_type, "consumed Pedestal v13 failed report meaning changed")
    _validate_assertion_outcome(
        report,
        expected_true=49,
        expected_false=25,
        description="consumed Pedestal v13 report",
        error_type=error_type,
    )
    expected_screenshots: list[dict[str, object]] = []
    for phase, relative_path in _base.EXPECTED_SCREENSHOTS:
        if phase == "gallery":
            expected_screenshots.append(
                {
                    "step": phase,
                    "file": relative_path,
                    "width": 1_920,
                    "height": 1_080,
                    "size": 284_835,
                    "sha256": _V13_SCREENSHOT_SHA256,
                    "completed_render_count": 120,
                    "source": "minecraft-framebuffer",
                    "edited": False,
                }
            )
        else:
            expected_screenshots.append(
                {
                    "step": phase,
                    "file": relative_path,
                    "width": 0,
                    "height": 0,
                    "size": 0,
                    "sha256": "",
                    "completed_render_count": 0,
                    "source": "minecraft-framebuffer",
                    "edited": False,
                }
            )
    if not _is_exact_json(report.get("screenshots"), expected_screenshots):
        _raise(error_type, "consumed Pedestal v13 screenshot report changed")
    world = report.get("world")
    pedestal = report.get("pedestal")
    if not isinstance(world, dict) or not isinstance(pedestal, dict) or (
        world.get("integrated") is not True
        or world.get("reopened") is not False
        or pedestal.get("forced_save") is not False
        or pedestal.get("full_restart") is not False
        or pedestal.get("persistence_exact") is not False
    ):
        _raise(error_type, "consumed Pedestal v13 lifecycle stopping point changed")


def _validate_png_dimensions(
    path: Path,
    error_type: type[Exception],
) -> None:
    try:
        header = path.read_bytes()[:24]
    except OSError as error:
        raise error_type(
            "Original Pedestal v14 evidence: consumed v13 screenshot is unreadable"
        ) from error
    if (
        len(header) != 24
        or header[:8] != b"\x89PNG\r\n\x1a\n"
        or header[12:16] != b"IHDR"
        or int.from_bytes(header[16:20], "big") != 1_920
        or int.from_bytes(header[20:24], "big") != 1_080
    ):
        _raise(error_type, "consumed Pedestal v13 screenshot dimensions changed")


def _validate_consumed_v13_history(
    repository_root: Path,
    error_type: type[Exception],
) -> None:
    root = repository_root.resolve(strict=True)
    _require_pinned_file(
        root / _V13_PROFILE_RELATIVE_PATH,
        _V13_PROFILE_SIZE,
        _V13_PROFILE_SHA256,
        "consumed Pedestal v13 profile",
        error_type,
    )
    _require_pinned_file(
        root / _V13_VERIFIER_RELATIVE_PATH,
        _V13_VERIFIER_SIZE,
        _V13_VERIFIER_SHA256,
        "consumed Pedestal v13 verifier",
        error_type,
    )
    archive = _validate_archive_bytes(
        repository_root=root,
        relative_path=_V13_DIAGNOSTIC_RELATIVE_PATH,
        expected_directories=_V13_DIAGNOSTIC_DIRECTORIES,
        expected_files=_V13_DIAGNOSTIC_FILES,
        description="consumed Pedestal v13 diagnostic archive",
        error_type=error_type,
    )
    manifest = _load_json(
        archive / "diagnostic-manifest.json",
        "consumed Pedestal v13 diagnostic manifest",
        error_type,
    )
    _validate_v13_manifest_semantics(manifest, error_type)
    report = _load_json(
        archive / "reports/report.json",
        "consumed Pedestal v13 failed report",
        error_type,
    )
    _validate_v13_report_semantics(report, error_type)
    marker = (archive / "reports/done.marker").read_text(encoding="utf-8")
    expected_marker = (
        f"{SCENARIO_ID}:failed\nreport_sha256:{_V13_REPORT_SHA256}\n"
    )
    if marker != expected_marker:
        _raise(error_type, "consumed Pedestal v13 failed marker meaning changed")
    _validate_png_dimensions(archive / _V13_SCREENSHOT_RELATIVE_PATH, error_type)


def _extract_method(
    source: str,
    start: str,
    end: str,
    error_type: type[Exception],
) -> str:
    start_index = source.find(start)
    end_index = source.find(end, start_index + len(start))
    if start_index < 0 or end_index < 0:
        _raise(error_type, f"cannot locate the pinned v14 source method {start}")
    return source[start_index:end_index]


def _validate_v14_source_text(
    scenario_text: str,
    writer_text: str,
    error_type: type[Exception],
) -> None:
    refresh_method = _extract_method(
        scenario_text,
        "private boolean refreshTransitionClientEvidence(",
        "private DropSnapshot recordTransitionClientDrops(",
        error_type,
    )
    required_scenario_fragments = (
        "Direction.DOWN,\n                Direction.UP",
        '"occupied-carpet-display",\n                Direction.UP',
        '"full-target-fallback"',
        "if (stage != Stage.COMPLETE\n"
        "                && stageClientTicks >= MAXIMUM_STAGE_CLIENT_TICKS)",
        "placedDispenserState.scheduledTick(",
        "private DropSnapshot recordTransitionClientDrops(",
        "Transition client drop diagnostic: checkpoint={}; expected={}; ",
        "private JsonObject createTransitionsReport()",
        'clientDrops.add("expected", transitionProbe.combinedDrops().toJson())',
        'clientDrops.add("mirror_ready", transitionClientDropsAtMirror.toJson())',
        'clientDrops.add("capture", transitionClientDropsAtCapture.toJson())',
        'transitions.add("client_drop_diagnostics", clientDrops)',
        "server.saveAll(false, true, true)",
        "createIntegratedServerLoader().start(",
        "ScreenshotRecorder.saveScreenshot(",
        *_V14_CLIENT_DROP_PROVENANCE_SOURCE_FRAGMENTS,
    )
    if (
        "import ru.feytox.etherology" in scenario_text
        or any(fragment not in scenario_text for fragment in required_scenario_fragments)
        or "DropSnapshot.capture" in refresh_method
        or ".equals(transitionProbe.combinedDrops())" in refresh_method
        or scenario_text.count("placedDispenserState.scheduledTick(") != 1
        or "Blocks.REDSTONE_BLOCK" in scenario_text
        or "dispenserReadyTick" in scenario_text
        or "Files.createLink(target, source)" not in writer_text
        or "FileAlreadyExistsException" not in writer_text
    ):
        _raise(error_type, "pinned v14 harness source lost a fail-closed invariant")


def _validate_v14_source_semantics(
    repository_root: Path,
    error_type: type[Exception],
) -> None:
    scenario = repository_root / (
        "baseline-harness/fabric/1.21.1/src/main/java/dev/theplumteam/"
        "etherology/baseline/fabric/PedestalBaselineScenario.java"
    )
    writer = scenario.with_name("PedestalEvidenceWriter.java")
    _validate_v14_source_text(
        scenario.read_text(encoding="utf-8"),
        writer.read_text(encoding="utf-8"),
        error_type,
    )
    properties = (
        repository_root / "baseline-harness/fabric/1.21.1/gradle.properties"
    ).read_text(encoding="utf-8")
    if f"harness_version={HARNESS_VERSION}\n" not in properties:
        _raise(error_type, "harness source version does not select v1.4.3")


def _validate_base_pinned_contract(
    *,
    repository_root: Path,
    manifest_path: Path,
    harness_path: Path,
    sha256_file: Callable[[Path], str],
    error_type: type[Exception],
) -> None:
    try:
        _base.validate_pinned_contract(
            repository_root=repository_root,
            manifest_path=manifest_path,
            harness_path=harness_path,
            sha256_file=sha256_file,
            error_type=error_type,
        )
    except error_type as error:
        if not str(error).endswith(
            "pinned harness source lost a fail-closed Pedestal vertical"
        ):
            raise
    _validate_v14_source_semantics(repository_root.resolve(strict=True), error_type)


def _is_string_positive_int_map(value: object) -> bool:
    return type(value) is dict and all(
        type(name) is str
        and bool(name)
        and type(count) is int
        and count > 0
        for name, count in value.items()
    )


def _validate_client_drop_diagnostics(
    report: object,
    error_type: type[Exception],
) -> None:
    if not isinstance(report, dict):
        _raise(error_type, "v14 report is not a JSON object")
    pedestal = report.get("pedestal")
    if not isinstance(pedestal, dict):
        _raise(error_type, "v14 report has no Pedestal object")
    transitions = pedestal.get("transitions")
    if not isinstance(transitions, dict):
        _raise(error_type, "v14 report has no transition object")
    diagnostics = transitions.get("client_drop_diagnostics")
    if not isinstance(diagnostics, dict) or set(diagnostics) != (
        CLIENT_DROP_DIAGNOSTIC_FIELDS
    ):
        _raise(error_type, "v14 client-drop diagnostic fields changed")
    expected = diagnostics["expected"]
    mirror_ready = diagnostics["mirror_ready"]
    capture = diagnostics["capture"]
    if (
        not _is_string_positive_int_map(expected)
        or not _is_string_positive_int_map(mirror_ready)
        or not _is_string_positive_int_map(capture)
        or not _is_exact_json(expected, CANONICAL_TRANSITION_DROPS)
    ):
        _raise(error_type, "v14 client-drop diagnostic maps changed")
    mirror_ready_recorded = diagnostics["mirror_ready_recorded"]
    capture_recorded = diagnostics["capture_recorded"]
    mirror_ready_exact = diagnostics["mirror_ready_exact"]
    capture_exact = diagnostics["capture_exact"]
    if (
        type(mirror_ready_recorded) is not bool
        or type(capture_recorded) is not bool
        or mirror_ready_recorded is not True
        or capture_recorded is not True
        or type(mirror_ready_exact) is not bool
        or type(capture_exact) is not bool
        or mirror_ready_exact
        != (mirror_ready_recorded and mirror_ready == expected)
        or capture_exact != (capture_recorded and capture == expected)
    ):
        _raise(error_type, "v14 client-drop diagnostic equality changed")


def validate_fresh_archive(
    *,
    repository_root: Path,
    archive_path: Path,
    sha256_file: Callable[[Path], str] = _sha256,
    error_type: type[Exception] = _base.PedestalEvidenceError,
) -> None:
    """Proves the v14 documentation target is the exact fresh placeholder."""

    repository_input = repository_root.absolute()
    expected_input = repository_input / FRESH_ARCHIVE_RELATIVE_PATH
    if archive_path.absolute() != expected_input.absolute():
        _raise(error_type, "fresh Pedestal v14 archive path changed")
    root = repository_input.resolve(strict=True)
    expected_archive = _unlinked_relative_path(
        root,
        FRESH_ARCHIVE_RELATIVE_PATH,
        "fresh Pedestal v14 archive",
        error_type,
    )
    if not expected_archive.is_dir():
        _raise(error_type, "fresh Pedestal v14 archive is missing")
    entries = tuple(expected_archive.iterdir())
    if tuple(sorted(path.name for path in entries)) != ("README.md",):
        _raise(error_type, "fresh Pedestal v14 archive inventory changed")
    readme = expected_archive / "README.md"
    if (
        readme.is_symlink()
        or not readme.is_file()
        or readme.stat().st_size != FRESH_ARCHIVE_README_SIZE
        or sha256_file(readme) != FRESH_ARCHIVE_README_SHA256
    ):
        _raise(error_type, "fresh Pedestal v14 archive README changed")


def validate_pinned_contract(
    *,
    repository_root: Path,
    manifest_path: Path,
    harness_path: Path,
    sha256_file: Callable[[Path], str] = _sha256,
    error_type: type[Exception] = _base.PedestalEvidenceError,
) -> None:
    """Proves the v14 inputs and all consumed Pedestal histories are pinned."""

    _validate_base_pinned_contract(
        repository_root=repository_root,
        manifest_path=manifest_path,
        harness_path=harness_path,
        sha256_file=sha256_file,
        error_type=error_type,
    )
    _validate_consumed_v11_history(repository_root, error_type)
    _validate_consumed_v12_history(repository_root, error_type)
    _validate_consumed_v13_history(repository_root, error_type)


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
    """Proves the v14 profile and archive have never been consumed."""

    repository_input = repository_root.absolute()
    expected_runtime = repository_input / ACTIVE_RUNTIME_RELATIVE_PATH
    if runtime_path.absolute() != expected_runtime.absolute():
        _raise(error_type, "active runtime path is not the fresh Pedestal v14 lane")
    _validate_consumed_v11_history(repository_root, error_type)
    _validate_consumed_v12_history(repository_root, error_type)
    _validate_consumed_v13_history(repository_root, error_type)
    _validate_base_pinned_contract(
        repository_root=repository_root,
        manifest_path=manifest_path,
        harness_path=harness_path,
        sha256_file=sha256_file,
        error_type=error_type,
    )
    if runtime_path.exists() or runtime_path.is_symlink():
        _raise(error_type, "unprovisioned v14 runtime must remain absent")
    validate_fresh_archive(
        repository_root=repository_root,
        archive_path=archive_path,
        sha256_file=sha256_file,
        error_type=error_type,
    )


def validate_evidence(**arguments: object):
    """Validates v14 diagnostics before delegating the 74 mechanic assertions."""

    error_type = arguments.get("error_type", _base.PedestalEvidenceError)
    repository_root = Path(__file__).resolve().parents[2]
    _validate_consumed_v11_history(repository_root, error_type)
    _validate_consumed_v12_history(repository_root, error_type)
    _validate_consumed_v13_history(repository_root, error_type)
    report = arguments.get("report")
    _validate_client_drop_diagnostics(report, error_type)
    delegated_report = copy.deepcopy(report)
    del delegated_report["pedestal"]["transitions"]["client_drop_diagnostics"]
    delegated_arguments = dict(arguments)
    delegated_arguments["report"] = delegated_report
    return _base.validate_evidence(**delegated_arguments)


def __getattr__(name: str):
    return getattr(_base, name)
