#!/usr/bin/env python3
"""Validate live or archived Fabric 1.20.1 Slitherite block evidence."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import client
import evidence
import slitherite_client_evidence_contract_v1 as contract_core


SCENARIO_ID = contract_core.SCENARIO_ID
PROFILE_ID = "etherology-e2e-fabric-1.20.1-v31"
ACTIVE_PROFILE_RELATIVE_PATH = "scripts/e2e/fabric-1.20.1-profile.json"
SNAPSHOT_PROFILE_RELATIVE_PATH = "scripts/e2e/fabric-1.20.1-profile-v31.json"
PROFILE_SIZE = 7086
PROFILE_SHA256 = "924f1290991514e341f2bb176a85239a773ddc67ca6daa880ffa8876b7708e14"
HARNESS_SIZE = 405976
HARNESS_SHA256 = "8f702fe349f14bc8f1824fb0937b27bf3ea40a2b94a32da559390922a13905d0"
ARCHIVE_DIRECTORY_NAME = "slitherite-block-registry-v31"
ARCHIVE_KIND = evidence.ARCHIVE_KIND
ARCHIVE_VERIFICATION_SCOPE = evidence.ARCHIVE_VERIFICATION_SCOPE
EXPECTED_SCENARIOS = (
    "phase0-smoke",
    "progression-oculus",
    "seals-aspects",
    "golden-forest",
    "alchemy",
    "ether-network",
    "staff-lenses",
    "spiritual-energy",
    "armillary",
    "storage-utilities",
    "combat-equipment",
    "persistence",
    "multiplayer-sync",
    "metal-block-registry",
    "forest-lantern",
    "attrahite-block-registry",
    SCENARIO_ID,
)
EXPECTED_REPORT_FIELDS = (
    "schema",
    "reference_id",
    "scenario",
    "artifact_node",
    "minecraft",
    "loader",
    "loader_version",
    "java",
    "lane",
    "role",
    "profile_id",
    "status",
    "passed",
    "client_ticks",
    "lifecycle_failure",
    "assertions",
    "world",
    "artifacts",
    "screenshots",
    "slitherite",
)
BLOCK_CLASSES = tuple(spec[1] for spec in contract_core.BASELINE.BLOCK_SPECS)
BLOCK_ITEM_CLASS = contract_core.BASELINE.BLOCK_ITEM_CLASS
BLOCK_IDS = contract_core.BLOCK_IDS
EXPECTED_RESOURCES = contract_core.EXPECTED_RESOURCES
EXPECTED_TAGS = contract_core.EXPECTED_TAGS
EXPECTED_LOOT_TABLES = contract_core.EXPECTED_LOOT_TABLES
EXPECTED_OWNED_RECIPES = contract_core.EXPECTED_OWNED_RECIPES
EXPECTED_OWNED_ADVANCEMENTS = contract_core.EXPECTED_OWNED_ADVANCEMENTS
EXPECTED_RELATED_RECIPES = contract_core.EXPECTED_RELATED_RECIPES
EXPECTED_WORLD = contract_core.EXPECTED_WORLD
SCREENSHOT_FILES = contract_core.SCREENSHOT_FILES
EXPECTED_SCREENSHOTS = contract_core.EXPECTED_SCREENSHOTS
ARCHIVE_MANIFEST_NAME = contract_core.ARCHIVE_MANIFEST_NAME
ARCHIVE_MANIFEST_SIZE = 2487
ARCHIVE_MANIFEST_SHA256 = (
    "12c12e1d2772ae2a449a2c140e6af77e32f1f8eefb35aec06718eead1a4140c5"
)


def verifier_contract() -> contract_core.LoaderContract:
    """Builds the verifier contract so the final harness pin is read at call time."""

    return contract_core.LoaderContract(
        label="Fabric",
        profile_id=PROFILE_ID,
        active_profile_relative_path=ACTIVE_PROFILE_RELATIVE_PATH,
        snapshot_profile_relative_path=SNAPSHOT_PROFILE_RELATIVE_PATH,
        profile_size=PROFILE_SIZE,
        profile_sha256=PROFILE_SHA256,
        scenarios=EXPECTED_SCENARIOS,
        artifact_node="fabric-1.20.1",
        loader="fabric",
        loader_version="0.17.3",
        lane="fabric-1.20.1",
        report_fields=EXPECTED_REPORT_FIELDS,
        artifact_origin_kind="PATH",
        production_file_name="etherology-under-test.jar",
        harness_file_name="etherology-e2e-harness.jar",
        block_classes=BLOCK_CLASSES,
        block_item_class=BLOCK_ITEM_CLASS,
        archive_directory_name=ARCHIVE_DIRECTORY_NAME,
        archive_parent_relative_path="docs/evidence/fabric-1.20.1",
        artifact_lock_name="artifact-lock.json",
        artifact_lock_schema=2,
        archive_kind=ARCHIVE_KIND,
        archive_verification_scope=ARCHIVE_VERIFICATION_SCOPE,
        controller=client,
        evidence=evidence,
        harness_size=HARNESS_SIZE,
        harness_sha256=HARNESS_SHA256,
    )


EXPECTED_ASSERTION_NAMES = verifier_contract().assertion_names


def validate_active_profile(configuration: client.ResolvedConfiguration) -> None:
    """Requires active Fabric v31 profile bytes and runtime metadata."""

    contract_core.validate_active_profile(verifier_contract(), configuration)


def validate_report_contract(
    scenario_root: Path, report: dict[str, object]
) -> tuple[float, float, float, float, dict[str, dict[str, object]]]:
    """Validates all Fabric Slitherite report, mechanics, and screenshot fields."""

    return contract_core.validate_report_contract(
        verifier_contract(), scenario_root, report
    )


def validate_live_evidence(
    configuration: client.ResolvedConfiguration | None = None,
    runtime: Path | None = None,
) -> contract_core.SlitheriteEvidenceSummary:
    """Validates the exact fresh v31 runtime without mutating or launching it."""

    return contract_core.validate_live_evidence(
        verifier_contract(), configuration, runtime
    )


def build_archive_manifest(
    configuration: client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> dict[str, object]:
    """Builds an immutable seal from the exact live v31 capture."""

    return contract_core.build_archive_manifest(
        verifier_contract(),
        configuration,
        profile_manifest_path,
        capture_runtime,
        archive_root,
    )


def write_archive_manifest(
    configuration: client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> Path:
    """Atomically seals and revalidates the copied v31 payload."""

    return contract_core.write_archive_manifest(
        verifier_contract(),
        configuration,
        profile_manifest_path,
        capture_runtime,
        archive_root,
    )


def validate_archive_manifest(
    archive_root: Path, manifest: dict[str, object]
) -> dict[str, dict[str, object]]:
    """Validates the v31 archive seal and payload records."""

    return contract_core.validate_archive_manifest(
        verifier_contract(), archive_root, manifest
    )


def validate_archived_evidence(
    archive_root: Path,
) -> contract_core.SlitheriteEvidenceSummary:
    """Validates a sealed v31 archive without consulting live runtime state."""

    validate_archive_manifest_identity(archive_root)
    return contract_core.validate_archived_evidence(
        verifier_contract(), archive_root
    )


def validate_archive_manifest_identity(archive_root: Path) -> None:
    """Pins the accepted v31 seal independently of its self-described payload."""

    if ARCHIVE_MANIFEST_SIZE is None or ARCHIVE_MANIFEST_SHA256 is None:
        return
    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if not manifest_path.is_file() or manifest_path.is_symlink():
        raise client.E2EError(
            f"Fabric Slitherite evidence: archive manifest is missing or linked: "
            f"{manifest_path}"
        )
    if (
        manifest_path.stat().st_size != ARCHIVE_MANIFEST_SIZE
        or contract_core.sha256_file(manifest_path) != ARCHIVE_MANIFEST_SHA256
    ):
        raise client.E2EError(
            "Fabric Slitherite evidence: accepted v31 archive manifest bytes changed"
        )


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate exact Fabric 1.20.1 Slitherite evidence."
    )
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument(
        "--live", action="store_true", help="validate the owned fresh v31 runtime"
    )
    operation.add_argument(
        "--archive", type=Path, help="validate one frozen v31 archive"
    )
    operation.add_argument(
        "--create-archive-manifest",
        type=Path,
        metavar="ARCHIVE",
        help="seal copied v31 payload from one explicit owned runtime",
    )
    parser.add_argument(
        "--capture-runtime",
        type=Path,
        help="owned runtime used only while sealing",
    )
    parser.add_argument(
        "--profile-manifest",
        type=Path,
        help="v31 manifest used only while sealing",
    )
    arguments = parser.parse_args()
    sealing = arguments.create_archive_manifest is not None
    auxiliaries = (arguments.capture_runtime, arguments.profile_manifest)
    if sealing and any(value is None for value in auxiliaries):
        parser.error(
            "--create-archive-manifest requires --capture-runtime and "
            "--profile-manifest"
        )
    if not sealing and any(value is not None for value in auxiliaries):
        parser.error(
            "--capture-runtime and --profile-manifest require "
            "--create-archive-manifest"
        )
    return arguments


def main() -> int:
    arguments = parse_arguments()
    try:
        if arguments.create_archive_manifest is not None:
            configuration = client.load_configuration(arguments.profile_manifest)
            manifest_path = write_archive_manifest(
                configuration,
                arguments.profile_manifest,
                arguments.capture_runtime,
                arguments.create_archive_manifest,
            )
            summary = validate_archived_evidence(
                arguments.create_archive_manifest
            )
            print(f"Created and validated archive manifest: {manifest_path}")
        elif arguments.archive is not None:
            summary = validate_archived_evidence(arguments.archive)
        else:
            summary = validate_live_evidence()
    except (client.E2EError, OSError, json.JSONDecodeError) as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2
    print(
        f"Validated Fabric {SCENARIO_ID} ({summary.profile_id}): "
        f"{summary.assertion_count} assertions, "
        f"{summary.screenshot_count} screenshots, "
        "reopen material delta "
        f"{summary.reopen_material_changed_pixel_ratio:.6f}"
    )
    print(f"Production SHA-256: {summary.production_sha256}")
    print(f"Harness SHA-256: {summary.harness_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
