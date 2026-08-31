#!/usr/bin/env python3
"""Validate live or archived Fabric metal-block-registry evidence."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import sys
import tempfile


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import client
import evidence


SCENARIO_ID = "metal-block-registry"
PROFILE_ID = "etherology-e2e-fabric-1.20.1-v23"
ACTIVE_PROFILE_RELATIVE_PATH = "scripts/e2e/fabric-1.20.1-profile.json"
SNAPSHOT_PROFILE_RELATIVE_PATH = "scripts/e2e/fabric-1.20.1-profile-v23.json"
PROFILE_SIZE = 6993
PROFILE_SHA256 = "36e7ccb7556aaaf0edb01b066de6d5263f3dde3545ac016e84cf07f795403f84"
ARCHIVE_DIRECTORY_NAME = "metal-block-registry-v23"
ARCHIVE_MANIFEST_NAME = evidence.ARCHIVE_MANIFEST_NAME
ARCHIVE_KIND = evidence.ARCHIVE_KIND
ARCHIVE_VERIFICATION_SCOPE = evidence.ARCHIVE_VERIFICATION_SCOPE
ARCHIVE_CAPTURE_METADATA_PATH = evidence.ARCHIVE_CAPTURE_METADATA_PATH
ARCHIVE_DIRECTORIES = {"reports", "screenshots"}
ARCHIVE_PAYLOAD_PATHS = (
    "reports/report.json",
    "reports/done.marker",
    "screenshots/metal-block-registry-before.png",
    "screenshots/metal-block-registry-after.png",
)
ARCHIVE_PUBLICATION_ATTESTATION = {
    "completion_marker": "reports/done.marker",
    "verified_last_in_capture_runtime": True,
    "archive_payloads_match_capture_runtime": True,
}
EXPECTED_FRAMEBUFFER_DIMENSIONS = (1920, 1080)
REQUIRED_STABLE_RENDERS = 120
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
    SCENARIO_ID,
)
METAL_BLOCK_IDS = (
    "etherology:azel_block",
    "etherology:ethril_block",
    "etherology:ebony_block",
)
DISPLAY_POSITIONS = {
    "etherology:azel_block": "-2,122,1",
    "etherology:ethril_block": "0,122,1",
    "etherology:ebony_block": "2,122,1",
}
PEDESTAL_POSITIONS = {
    "etherology:azel_block": "-2,121,1",
    "etherology:ethril_block": "0,121,1",
    "etherology:ebony_block": "2,121,1",
}
BEFORE_IDS = {identifier: "minecraft:air" for identifier in METAL_BLOCK_IDS}
AFTER_IDS = {identifier: identifier for identifier in METAL_BLOCK_IDS}
CAMERA = {
    "x": 0.5,
    "y": 121.0,
    "z": -5.5,
    "yaw": 0.0,
    "pitch": 2.0,
    "first_person": True,
    "on_ground": True,
}
CAPTURE_STATE = {
    "render_ready": True,
    "camera_exact": True,
    "stable_renders": REQUIRED_STABLE_RENDERS,
    "framebuffer": "1920x1080",
}
EXPECTED_WORLD = {
    "save_directory": "etherology-e2e-metal-block-registry-world",
    "display_name": "Etherology E2E Metal Blocks",
    "seed": 331875631436,
    "dimension": "minecraft:overworld",
    "integrated": True,
}
EXPECTED_READY_RESOURCES = (
    "etherology:blockstates/azel_block.json",
    "etherology:models/block/azel_block.json",
    "etherology:textures/block/azel_block.png",
    "etherology:blockstates/ethril_block.json",
    "etherology:models/block/ethril_block.json",
    "etherology:textures/block/ethril_block.png",
    "etherology:blockstates/ebony_block.json",
    "etherology:models/block/ebony_block.json",
    "etherology:textures/block/ebony_block.png",
)
EXPECTED_ASSERTION_NAMES = (
    "fabric_mod_loaded:etherology",
    "registry:block:etherology:azel_block",
    "registry:block:etherology:ethril_block",
    "registry:block:etherology:ebony_block",
    "default_state_network_ids",
    "client_render_resources",
    "packaged_root_jar:etherology",
    "packaged_root_jar:etherology_e2e_harness",
    "integrated_world_joined",
    "server_arena_chunk_loaded",
    "before_fixture_exact",
    "before_capture_render_ready",
    "before_capture_camera_exact",
    "before_consecutive_stable_renders",
    "before_framebuffer_dimensions",
    "native_screenshot_written:before",
    "server_fixture_ids_exact",
    "after_capture_client_fixture_ids_exact",
    "after_capture_render_ready",
    "after_capture_camera_exact",
    "after_consecutive_stable_renders",
    "after_framebuffer_dimensions",
    "native_screenshot_written:after",
    "forced_world_save",
    "isolated_save_directory_present",
)
EXPECTED_SCREENSHOTS = (
    (
        "empty-display-fixture",
        "screenshots/metal-block-registry-before.png",
    ),
    (
        "placed-metal-blocks",
        "screenshots/metal-block-registry-after.png",
    ),
)
EXPECTED_REPORT_FIELDS = {
    "schema",
    "scenario",
    "lane",
    "role",
    "status",
    "client_ticks",
    "lifecycle_failure",
    "assertions",
    "world",
    "ready_resources",
    "metal_blocks",
    "artifacts",
    "screenshots",
}
EXPECTED_ARTIFACTS = (
    ("production", "etherology", "etherology-under-test.jar"),
    ("harness", "etherology_e2e_harness", "etherology-e2e-harness.jar"),
)
CAMERA_SUMMARY = (
    "first_person=true;x=0.5;y=121.0;z=-5.5;yaw=0.0;pitch=2.0;"
    "on_ground=true;tolerance=1.0E-4"
)
EMPTY_POSITION_SUMMARY = (
    "-2,122,1=minecraft:air;0,122,1=minecraft:air;2,122,1=minecraft:air"
)
PLACED_POSITION_SUMMARY = (
    "-2,122,1=etherology:azel_block;"
    "0,122,1=etherology:ethril_block;"
    "2,122,1=etherology:ebony_block"
)
PEDESTAL_SUMMARY = (
    "-2,121,1=minecraft:polished_andesite;"
    "0,121,1=minecraft:polished_andesite;"
    "2,121,1=minecraft:polished_andesite"
)
BEFORE_FIXTURE_SUMMARY = (
    f"server={EMPTY_POSITION_SUMMARY}|client={EMPTY_POSITION_SUMMARY}|"
    f"pedestals={PEDESTAL_SUMMARY}"
)
READY_RESOURCES_SUMMARY = "[" + ", ".join(EXPECTED_READY_RESOURCES) + "]"


@dataclass(frozen=True)
class MetalBlockEvidenceSummary:
    """Reports the exact evidence inventory accepted by this verifier."""

    profile_id: str
    assertion_count: int
    screenshot_count: int
    changed_pixel_ratio: float
    production_sha256: str
    harness_sha256: str


def validate_active_profile(configuration: client.ResolvedConfiguration) -> None:
    """Requires the immutable v23 profile and exact Fabric runtime contract."""

    active_profile = configuration.repository_root / ACTIVE_PROFILE_RELATIVE_PATH
    snapshot_profile = configuration.repository_root / SNAPSHOT_PROFILE_RELATIVE_PATH
    for path in (active_profile, snapshot_profile):
        if not path.is_file() or path.is_symlink():
            raise client.E2EError(f"Fabric metal-block profile is missing or linked: {path}")
        if path.stat().st_size != PROFILE_SIZE or client.sha256_file(path) != PROFILE_SHA256:
            raise client.E2EError(f"Fabric metal-block profile bytes changed: {path}")
    if active_profile.read_bytes() != snapshot_profile.read_bytes():
        raise client.E2EError("The active Fabric profile differs from its v23 snapshot")

    profile = client.profile_spec(configuration)
    capture = client.require_object(client.evidence_spec(configuration), "capture")
    if (
        profile.get("id") != PROFILE_ID
        or profile.get("runtime_directory") != PROFILE_ID
        or tuple(client.scenario_ids(configuration)) != EXPECTED_SCENARIOS
        or configuration.artifact_lane.get("artifact_node") != "fabric-1.20.1"
        or configuration.runtime_lane.get("runtime_version") != "1.20.1"
        or configuration.runtime_lane.get("loader") != "fabric"
        or configuration.runtime_lane.get("loader_version") != "0.17.3"
        or configuration.runtime_lane.get("java") != 17
        or capture.get("kind") != "composed-minecraft-framebuffer"
        or (capture.get("width"), capture.get("height"))
        != EXPECTED_FRAMEBUFFER_DIMENSIONS
    ):
        raise client.E2EError("The Fabric metal-block v23 profile contract changed")
    for role, mod_id, file_name in EXPECTED_ARTIFACTS:
        artifact = client.artifact_spec(configuration, role)
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            raise client.E2EError(
                f"The Fabric metal-block profile has the wrong {role} artifact identity"
            )


def require_ordered_mapping(
    container: dict[str, object],
    field_name: str,
    expected: dict[str, object],
) -> None:
    value = container.get(field_name)
    if (
        not isinstance(value, dict)
        or list(value) != list(expected)
        or value != expected
    ):
        raise client.E2EError(f"Fabric metal-block {field_name} inventory changed")


def validate_metal_blocks(report: dict[str, object]) -> None:
    """Requires the exact server/client fixture, camera, and capture state."""

    metal_blocks = report.get("metal_blocks")
    if not isinstance(metal_blocks, dict) or list(metal_blocks) != [
        "registry_ids",
        "display_positions",
        "pedestal_positions",
        "before_server_ids",
        "before_client_ids",
        "after_server_ids",
        "after_client_ids",
        "camera",
        "required_stable_renders",
        "before",
        "after",
    ]:
        raise client.E2EError("Fabric metal-block report field inventory changed")
    if metal_blocks.get("registry_ids") != list(METAL_BLOCK_IDS):
        raise client.E2EError("Fabric metal-block registry id order changed")
    require_ordered_mapping(metal_blocks, "display_positions", DISPLAY_POSITIONS)
    require_ordered_mapping(metal_blocks, "pedestal_positions", PEDESTAL_POSITIONS)
    require_ordered_mapping(metal_blocks, "before_server_ids", BEFORE_IDS)
    require_ordered_mapping(metal_blocks, "before_client_ids", BEFORE_IDS)
    require_ordered_mapping(metal_blocks, "after_server_ids", AFTER_IDS)
    require_ordered_mapping(metal_blocks, "after_client_ids", AFTER_IDS)

    camera = metal_blocks.get("camera")
    if (
        not isinstance(camera, dict)
        or list(camera) != list(CAMERA)
        or camera != CAMERA
        or any(type(camera[key]) is not float for key in ("x", "y", "z", "yaw", "pitch"))
        or any(type(camera[key]) is not bool for key in ("first_person", "on_ground"))
    ):
        raise client.E2EError("Fabric metal-block camera contract changed")
    if (
        type(metal_blocks.get("required_stable_renders")) is not int
        or metal_blocks.get("required_stable_renders") != REQUIRED_STABLE_RENDERS
    ):
        raise client.E2EError("Fabric metal-block stable-render requirement changed")
    for phase in ("before", "after"):
        value = metal_blocks.get(phase)
        if (
            not isinstance(value, dict)
            or list(value) != list(CAPTURE_STATE)
            or value != CAPTURE_STATE
            or type(value.get("stable_renders")) is not int
        ):
            raise client.E2EError(
                f"Fabric metal-block {phase} capture state changed"
            )


def validate_artifact_inventory(
    report: dict[str, object],
) -> dict[str, dict[str, object]]:
    artifacts = report.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) != len(EXPECTED_ARTIFACTS):
        raise client.E2EError("Fabric metal-block artifact inventory changed")
    validated: dict[str, dict[str, object]] = {}
    for artifact, (role, mod_id, file_name) in zip(artifacts, EXPECTED_ARTIFACTS):
        if not isinstance(artifact, dict) or set(artifact) != {
            "mod_id",
            "origin_kind",
            "file_name",
            "size",
            "sha256",
        }:
            raise client.E2EError(
                f"Fabric metal-block {role} artifact provenance is malformed"
            )
        if (
            artifact.get("mod_id") != mod_id
            or artifact.get("origin_kind") != "PATH"
            or artifact.get("file_name") != file_name
            or type(artifact.get("size")) is not int
            or int(artifact["size"]) <= 0
        ):
            raise client.E2EError(
                f"Fabric metal-block {role} artifact identity is invalid"
            )
        digest = client.validate_hex_digest(
            artifact.get("sha256"),
            f"Fabric metal-block {role} artifact",
        )
        validated[role] = {
            "mod_id": mod_id,
            "file_name": file_name,
            "size": artifact["size"],
            "sha256": digest,
        }
    return validated


def validate_screenshot_inventory(
    report: dict[str, object],
) -> list[dict[str, object]]:
    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or len(screenshots) != len(
        EXPECTED_SCREENSHOTS
    ):
        raise client.E2EError("Fabric metal-block screenshot inventory changed")
    validated: list[dict[str, object]] = []
    for screenshot, (expected_step, expected_file) in zip(
        screenshots,
        EXPECTED_SCREENSHOTS,
    ):
        if not isinstance(screenshot, dict) or set(screenshot) != {
            "step",
            "role",
            "file",
            "width",
            "height",
            "size",
            "sha256",
            "completed_render_count",
            "source",
            "edited",
        }:
            raise client.E2EError("Fabric metal-block screenshot provenance is malformed")
        if (
            screenshot.get("step") != expected_step
            or screenshot.get("role") != "host"
            or screenshot.get("file") != expected_file
            or (screenshot.get("width"), screenshot.get("height"))
            != EXPECTED_FRAMEBUFFER_DIMENSIONS
            or type(screenshot.get("size")) is not int
            or int(screenshot["size"]) <= 0
            or type(screenshot.get("completed_render_count")) is not int
            or screenshot.get("completed_render_count") != REQUIRED_STABLE_RENDERS
            or screenshot.get("source") != "minecraft-framebuffer"
            or screenshot.get("edited") is not False
        ):
            raise client.E2EError("Fabric metal-block screenshot contract is invalid")
        client.validate_hex_digest(
            screenshot.get("sha256"),
            f"Fabric metal-block screenshot {expected_file}",
        )
        validated.append(screenshot)
    return validated


def validate_default_state_network_ids(actual: object) -> None:
    if not isinstance(actual, str):
        raise client.E2EError("Fabric metal-block default-state network ids are invalid")
    parts = actual.split(";")
    if len(parts) != len(METAL_BLOCK_IDS):
        raise client.E2EError("Fabric metal-block default-state network id inventory changed")
    for part, identifier in zip(parts, METAL_BLOCK_IDS):
        prefix = identifier + "="
        raw_id = part.removeprefix(prefix)
        if not part.startswith(prefix) or not raw_id.isascii() or not raw_id.isdecimal():
            raise client.E2EError("Fabric metal-block default-state network ids are invalid")


def expected_assertion_evidence(
    screenshots: list[dict[str, object]],
) -> dict[str, tuple[str, str] | None]:
    before_screenshot, after_screenshot = screenshots
    return {
        "fabric_mod_loaded:etherology": ("loaded", "loaded"),
        "registry:block:etherology:azel_block": ("present", "present"),
        "registry:block:etherology:ethril_block": ("present", "present"),
        "registry:block:etherology:ebony_block": ("present", "present"),
        "default_state_network_ids": ("three non-negative raw IDs", ""),
        "client_render_resources": (
            READY_RESOURCES_SUMMARY,
            READY_RESOURCES_SUMMARY,
        ),
        "packaged_root_jar:etherology": (
            "one regular root JAR",
            "one regular root JAR",
        ),
        "packaged_root_jar:etherology_e2e_harness": (
            "one regular root JAR",
            "one regular root JAR",
        ),
        "integrated_world_joined": (
            "running server and connected client",
            "joined",
        ),
        "server_arena_chunk_loaded": ("full chunk", "true"),
        "before_fixture_exact": (BEFORE_FIXTURE_SUMMARY, BEFORE_FIXTURE_SUMMARY),
        "before_capture_render_ready": (
            "terrain complete and six fixture positions rendering-ready",
            "ready",
        ),
        "before_capture_camera_exact": (CAMERA_SUMMARY, CAMERA_SUMMARY),
        "before_consecutive_stable_renders": ("120", "120"),
        "before_framebuffer_dimensions": ("1920x1080", "1920x1080"),
        "native_screenshot_written:before": (
            "one non-empty unedited framebuffer PNG",
            f"{before_screenshot['size']} bytes, sha256={before_screenshot['sha256']}",
        ),
        "server_fixture_ids_exact": (
            PLACED_POSITION_SUMMARY,
            PLACED_POSITION_SUMMARY,
        ),
        "after_capture_client_fixture_ids_exact": (
            PLACED_POSITION_SUMMARY,
            PLACED_POSITION_SUMMARY,
        ),
        "after_capture_render_ready": (
            "terrain complete and six fixture positions rendering-ready",
            "ready",
        ),
        "after_capture_camera_exact": (CAMERA_SUMMARY, CAMERA_SUMMARY),
        "after_consecutive_stable_renders": ("120", "120"),
        "after_framebuffer_dimensions": ("1920x1080", "1920x1080"),
        "native_screenshot_written:after": (
            "one non-empty unedited framebuffer PNG",
            f"{after_screenshot['size']} bytes, sha256={after_screenshot['sha256']}",
        ),
        "forced_world_save": ("true", "true"),
        "isolated_save_directory_present": (
            "etherology-e2e-metal-block-registry-world",
            "etherology-e2e-metal-block-registry-world",
        ),
    }


def validate_assertions(
    report: dict[str, object],
    screenshots: list[dict[str, object]],
) -> None:
    assertions = report.get("assertions")
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise client.E2EError("Fabric metal-block assertion inventory changed")
    expected_evidence = expected_assertion_evidence(screenshots)
    for assertion, expected_name in zip(assertions, EXPECTED_ASSERTION_NAMES):
        if not isinstance(assertion, dict) or set(assertion) != {
            "name",
            "passed",
            "expected",
            "actual",
        }:
            raise client.E2EError("Fabric metal-block assertion fields changed")
        if assertion.get("name") != expected_name or assertion.get("passed") is not True:
            raise client.E2EError("Fabric metal-block assertion inventory changed")
        expected, actual = expected_evidence[expected_name]
        if expected_name == "default_state_network_ids":
            if assertion.get("expected") != expected:
                raise client.E2EError("Fabric metal-block assertion evidence changed")
            validate_default_state_network_ids(assertion.get("actual"))
        elif assertion.get("expected") != expected or assertion.get("actual") != actual:
            raise client.E2EError("Fabric metal-block assertion evidence changed")


def validate_screenshot_files(
    scenario_root: Path,
    screenshots: list[dict[str, object]],
) -> float:
    screenshot_directory = scenario_root / "screenshots"
    if not screenshot_directory.is_dir() or screenshot_directory.is_symlink():
        raise client.E2EError("Fabric metal-block screenshot directory is missing or linked")
    expected_names = {
        Path(expected_file).name for _expected_step, expected_file in EXPECTED_SCREENSHOTS
    }
    entries = list(screenshot_directory.iterdir())
    if (
        {path.name for path in entries} != expected_names
        or any(not path.is_file() or path.is_symlink() for path in entries)
    ):
        raise client.E2EError("Fabric metal-block screenshot file inventory changed")

    images: list[evidence.PngImage] = []
    for screenshot in screenshots:
        path = evidence.safe_screenshot_path(scenario_root, screenshot["file"])
        if path.stat().st_size != screenshot["size"]:
            raise client.E2EError(
                f"Fabric metal-block screenshot size differs from report: {path}"
            )
        if evidence.sha256_file(path) != screenshot["sha256"]:
            raise client.E2EError(
                f"Fabric metal-block screenshot digest differs from report: {path}"
            )
        image = evidence.decode_png(path, EXPECTED_FRAMEBUFFER_DIMENSIONS)
        evidence.assert_image_is_not_blank(image, str(path))
        images.append(image)
    ratio = evidence.changed_pixel_ratio(images[0], images[1])
    if ratio < evidence.MINIMUM_CHANGED_PIXEL_RATIO:
        raise client.E2EError(
            "Fabric metal-block screenshot sequence has no material visual change: "
            f"{ratio:.6f}"
        )
    return ratio


def validate_report_contract(
    scenario_root: Path,
    report: dict[str, object],
) -> tuple[float, dict[str, dict[str, object]]]:
    """Validates every scenario-specific report and screenshot field."""

    if set(report) != EXPECTED_REPORT_FIELDS:
        raise client.E2EError("Fabric metal-block report field inventory changed")
    if (
        report.get("schema") != 2
        or report.get("scenario") != SCENARIO_ID
        or report.get("lane") != "fabric-1.20.1"
        or report.get("role") != "host"
        or report.get("status") != "passed"
        or report.get("lifecycle_failure") != ""
        or type(report.get("client_ticks")) is not int
        or int(report["client_ticks"]) <= 0
        or report.get("world") != EXPECTED_WORLD
        or report.get("ready_resources") != list(EXPECTED_READY_RESOURCES)
    ):
        raise client.E2EError("Fabric metal-block report lifecycle contract is invalid")
    validate_metal_blocks(report)
    artifacts = validate_artifact_inventory(report)
    screenshots = validate_screenshot_inventory(report)
    validate_assertions(report, screenshots)
    ratio = validate_screenshot_files(scenario_root, screenshots)
    return ratio, artifacts


def validate_live_evidence(
    configuration: client.ResolvedConfiguration | None = None,
    runtime: Path | None = None,
) -> MetalBlockEvidenceSummary:
    """Layers the exact metal contract over the general live evidence validator."""

    resolved_configuration = configuration or client.load_configuration()
    validate_active_profile(resolved_configuration)
    target_runtime = runtime or client.runtime_root(resolved_configuration)
    general_summary = evidence.validate_scenario(
        resolved_configuration,
        SCENARIO_ID,
        target_runtime,
    )
    scenario_root = (
        client.evidence_root(resolved_configuration, target_runtime) / SCENARIO_ID
    )
    report = evidence.require_json_object(
        scenario_root / "reports" / "report.json",
        "Fabric metal-block scenario report",
    )
    ratio, artifacts = validate_report_contract(scenario_root, report)
    if (
        general_summary.scenario_id != SCENARIO_ID
        or general_summary.assertion_count != len(EXPECTED_ASSERTION_NAMES)
        or general_summary.screenshot_count != len(EXPECTED_SCREENSHOTS)
        or general_summary.changed_pixel_ratio is None
        or abs(general_summary.changed_pixel_ratio - ratio) > 1e-12
        or general_summary.production_sha256 != artifacts["production"]["sha256"]
        or general_summary.harness_sha256 != artifacts["harness"]["sha256"]
    ):
        raise client.E2EError(
            "Fabric metal-block exact evidence differs from general live validation"
        )
    return MetalBlockEvidenceSummary(
        profile_id=PROFILE_ID,
        assertion_count=general_summary.assertion_count,
        screenshot_count=general_summary.screenshot_count,
        changed_pixel_ratio=ratio,
        production_sha256=general_summary.production_sha256,
        harness_sha256=general_summary.harness_sha256,
    )


def validate_archive_inventory(
    archive_root: Path,
    include_manifest: bool = True,
) -> None:
    if not archive_root.is_dir() or archive_root.is_symlink():
        raise client.E2EError(
            f"Fabric metal-block evidence archive is missing or linked: {archive_root}"
        )
    if archive_root.name != ARCHIVE_DIRECTORY_NAME:
        raise client.E2EError(
            "Fabric metal-block archive directory does not identify profile v23"
        )
    files: set[str] = set()
    directories: set[str] = set()
    for path in archive_root.rglob("*"):
        relative_path = path.relative_to(archive_root).as_posix()
        if path.is_symlink():
            raise client.E2EError(
                f"Fabric metal-block archive contains a linked entry: {relative_path}"
            )
        if path.is_file():
            files.add(relative_path)
        elif path.is_dir():
            directories.add(relative_path)
        else:
            raise client.E2EError(
                f"Fabric metal-block archive contains a special entry: {relative_path}"
            )
    expected_files = set(ARCHIVE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    if files != expected_files or directories != ARCHIVE_DIRECTORIES:
        raise client.E2EError("Fabric metal-block archive inventory changed")
    archive_size = sum(
        path.stat().st_size for path in archive_root.rglob("*") if path.is_file()
    )
    if archive_size > evidence.MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise client.E2EError("Fabric metal-block archive exceeds the size bound")


def expected_repository_archive_root(
    configuration: client.ResolvedConfiguration,
) -> Path:
    return client.safe_repository_path(
        configuration.repository_root,
        f"docs/evidence/fabric-1.20.1/{ARCHIVE_DIRECTORY_NAME}",
        "Fabric metal-block evidence archive",
    )


def validate_capture_artifact_lock(
    configuration: client.ResolvedConfiguration,
    capture_runtime: Path,
    report_artifacts: dict[str, dict[str, object]],
) -> Path:
    lock = client.load_artifact_lock(configuration, capture_runtime)
    if (
        not isinstance(lock, dict)
        or lock.get("schema") != 2
        or lock.get("profile_id") != PROFILE_ID
        or lock.get("artifact_node") != "fabric-1.20.1"
    ):
        raise client.E2EError("Fabric metal-block capture has no exact schema-2 artifact lock")
    locked_artifacts = lock.get("artifacts")
    if not isinstance(locked_artifacts, dict) or set(locked_artifacts) != {
        "production",
        "harness",
    }:
        raise client.E2EError("Fabric metal-block capture artifact lock inventory changed")
    for role, mod_id, file_name in EXPECTED_ARTIFACTS:
        locked = locked_artifacts.get(role)
        reported = report_artifacts[role]
        if (
            not isinstance(locked, dict)
            or locked.get("mod_id") != mod_id
            or locked.get("target_file") != file_name
            or locked.get("size") != reported["size"]
            or locked.get("sha256") != reported["sha256"]
        ):
            raise client.E2EError(
                f"Fabric metal-block capture {role} artifact lock differs from report"
            )
    lock_path = client.artifact_lock_path(configuration, capture_runtime)
    if not lock_path.is_file() or lock_path.is_symlink():
        raise client.E2EError(
            f"Fabric metal-block capture artifact lock is missing or linked: {lock_path}"
        )
    return lock_path


def build_archive_manifest(
    configuration: client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> dict[str, object]:
    """Builds a manifest only from the exact live v23 capture and copied payload."""

    validate_active_profile(configuration)
    expected_profile_path = configuration.repository_root / ACTIVE_PROFILE_RELATIVE_PATH
    if (
        profile_manifest_path.resolve() != expected_profile_path.resolve()
        or not profile_manifest_path.is_file()
        or profile_manifest_path.is_symlink()
        or profile_manifest_path.stat().st_size != PROFILE_SIZE
        or client.sha256_file(profile_manifest_path) != PROFILE_SHA256
    ):
        raise client.E2EError(
            f"Fabric metal-block sealing requires the exact active v23 profile: "
            f"{expected_profile_path}"
        )
    validate_archive_inventory(archive_root, include_manifest=False)
    if archive_root.resolve() != expected_repository_archive_root(configuration).resolve():
        raise client.E2EError(
            "Fabric metal-block sealing requires the exact repository archive destination"
        )

    expected_capture_runtime = client.runtime_root(configuration)
    client.ensure_owned_state_roots()
    if (
        capture_runtime.resolve() != expected_capture_runtime.resolve()
        or not capture_runtime.is_dir()
        or capture_runtime.is_symlink()
        or capture_runtime.name != PROFILE_ID
    ):
        raise client.E2EError(
            "Fabric metal-block sealing requires the exact repository-owned v23 runtime"
        )
    client.verify_runtime(configuration, capture_runtime, artifact_policy="optional")
    live_summary = validate_live_evidence(configuration, capture_runtime)

    archive_report = evidence.require_json_object(
        archive_root / "reports" / "report.json",
        "Fabric metal-block archived scenario report",
    )
    ratio, report_artifacts = validate_report_contract(archive_root, archive_report)
    if (
        live_summary.assertion_count != len(EXPECTED_ASSERTION_NAMES)
        or live_summary.screenshot_count != len(EXPECTED_SCREENSHOTS)
        or abs(live_summary.changed_pixel_ratio - ratio) > 1e-12
        or live_summary.production_sha256 != report_artifacts["production"]["sha256"]
        or live_summary.harness_sha256 != report_artifacts["harness"]["sha256"]
    ):
        raise client.E2EError(
            "Fabric metal-block archive report differs from strict live validation"
        )

    capture_scenario_root = (
        client.evidence_root(configuration, capture_runtime) / SCENARIO_ID
    )
    capture_mtime_ns: dict[str, int] = {}
    files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        archive_path = archive_root / relative_path
        capture_path = capture_scenario_root / relative_path
        if not capture_path.is_file() or capture_path.is_symlink():
            raise client.E2EError(
                f"Fabric metal-block capture payload is missing or linked: {capture_path}"
            )
        if (
            archive_path.stat().st_size != capture_path.stat().st_size
            or evidence.sha256_file(archive_path) != evidence.sha256_file(capture_path)
        ):
            raise client.E2EError(
                "Fabric metal-block archive payload differs from its capture runtime: "
                f"{relative_path}"
            )
        capture_mtime_ns[relative_path] = capture_path.stat().st_mtime_ns
        files[relative_path] = {
            "size": archive_path.stat().st_size,
            "sha256": evidence.sha256_file(archive_path),
        }
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[relative_path]
        for relative_path in ARCHIVE_PAYLOAD_PATHS
        if relative_path != "reports/done.marker"
    ):
        raise client.E2EError(
            "Fabric metal-block capture completion predates copied payload evidence"
        )
    artifact_lock_path = validate_capture_artifact_lock(
        configuration,
        capture_runtime,
        report_artifacts,
    )
    return {
        "schema": 1,
        "kind": ARCHIVE_KIND,
        "verification_scope": ARCHIVE_VERIFICATION_SCOPE,
        "scenario": SCENARIO_ID,
        "profile": {
            "id": PROFILE_ID,
            "manifest_path": ACTIVE_PROFILE_RELATIVE_PATH,
            "manifest_size": PROFILE_SIZE,
            "manifest_sha256": PROFILE_SHA256,
        },
        "runtime": {
            "artifact_node": "fabric-1.20.1",
            "minecraft": "1.20.1",
            "loader": "fabric",
            "loader_version": "0.17.3",
            "java": 17,
            "capture_kind": "composed-minecraft-framebuffer",
            "framebuffer_width": 1920,
            "framebuffer_height": 1080,
        },
        "publication": {
            **ARCHIVE_PUBLICATION_ATTESTATION,
            "capture_mtime_ns": capture_mtime_ns,
        },
        "capture_metadata": {
            "path": ARCHIVE_CAPTURE_METADATA_PATH,
            "size": artifact_lock_path.stat().st_size,
            "sha256": evidence.sha256_file(artifact_lock_path),
        },
        "assertion_count": len(EXPECTED_ASSERTION_NAMES),
        "screenshot_count": len(EXPECTED_SCREENSHOTS),
        "artifacts": report_artifacts,
        "files": files,
    }


def write_archive_manifest(
    configuration: client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> Path:
    """Atomically creates and immediately validates the one-shot archive seal."""

    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if manifest_path.exists() or manifest_path.is_symlink():
        raise client.E2EError(
            f"Fabric metal-block archive manifest already exists: {manifest_path}"
        )
    manifest = build_archive_manifest(
        configuration,
        profile_manifest_path,
        capture_runtime,
        archive_root,
    )
    temporary_path: Path | None = None
    try:
        descriptor, raw_temporary_path = tempfile.mkstemp(
            prefix=".metal-block-archive-manifest.",
            dir=archive_root,
        )
        temporary_path = Path(raw_temporary_path)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(manifest, handle, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        try:
            os.link(temporary_path, manifest_path, follow_symlinks=False)
        except FileExistsError as exception:
            raise client.E2EError(
                f"Fabric metal-block archive manifest already exists: {manifest_path}"
            ) from exception
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
    try:
        validate_archived_evidence(archive_root)
    except (client.E2EError, OSError, json.JSONDecodeError):
        manifest_path.unlink(missing_ok=True)
        raise
    return manifest_path


def validate_archive_manifest(
    archive_root: Path,
    manifest: dict[str, object],
) -> tuple[dict[str, dict[str, object]], dict[str, dict[str, object]]]:
    if set(manifest) != {
        "schema",
        "kind",
        "verification_scope",
        "scenario",
        "profile",
        "runtime",
        "publication",
        "capture_metadata",
        "assertion_count",
        "screenshot_count",
        "artifacts",
        "files",
    }:
        raise client.E2EError("Fabric metal-block archive manifest fields changed")
    if (
        manifest.get("schema") != 1
        or manifest.get("kind") != ARCHIVE_KIND
        or manifest.get("verification_scope") != ARCHIVE_VERIFICATION_SCOPE
        or manifest.get("scenario") != SCENARIO_ID
        or manifest.get("assertion_count") != len(EXPECTED_ASSERTION_NAMES)
        or manifest.get("screenshot_count") != len(EXPECTED_SCREENSHOTS)
    ):
        raise client.E2EError("Fabric metal-block archive manifest identity is invalid")

    profile = manifest.get("profile")
    if not isinstance(profile, dict) or profile != {
        "id": PROFILE_ID,
        "manifest_path": ACTIVE_PROFILE_RELATIVE_PATH,
        "manifest_size": PROFILE_SIZE,
        "manifest_sha256": PROFILE_SHA256,
    }:
        raise client.E2EError("Fabric metal-block archive profile provenance is invalid")
    expected_runtime = {
        "artifact_node": "fabric-1.20.1",
        "minecraft": "1.20.1",
        "loader": "fabric",
        "loader_version": "0.17.3",
        "java": 17,
        "capture_kind": "composed-minecraft-framebuffer",
        "framebuffer_width": 1920,
        "framebuffer_height": 1080,
    }
    if manifest.get("runtime") != expected_runtime:
        raise client.E2EError("Fabric metal-block archive runtime identity is invalid")

    publication = manifest.get("publication")
    if not isinstance(publication, dict) or set(publication) != {
        *ARCHIVE_PUBLICATION_ATTESTATION,
        "capture_mtime_ns",
    }:
        raise client.E2EError("Fabric metal-block archive publication is malformed")
    if any(
        publication.get(key) != value
        for key, value in ARCHIVE_PUBLICATION_ATTESTATION.items()
    ):
        raise client.E2EError("Fabric metal-block archive publication is invalid")
    capture_mtime_ns = publication.get("capture_mtime_ns")
    if (
        not isinstance(capture_mtime_ns, dict)
        or set(capture_mtime_ns) != set(ARCHIVE_PAYLOAD_PATHS)
        or any(type(value) is not int or value <= 0 for value in capture_mtime_ns.values())
    ):
        raise client.E2EError("Fabric metal-block archive timestamps are invalid")
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[path]
        for path in ARCHIVE_PAYLOAD_PATHS
        if path != "reports/done.marker"
    ):
        raise client.E2EError(
            "Fabric metal-block archive completion predates payload evidence"
        )

    capture_metadata = manifest.get("capture_metadata")
    if (
        not isinstance(capture_metadata, dict)
        or set(capture_metadata) != {"path", "size", "sha256"}
        or capture_metadata.get("path") != ARCHIVE_CAPTURE_METADATA_PATH
        or type(capture_metadata.get("size")) is not int
        or int(capture_metadata["size"]) <= 0
    ):
        raise client.E2EError("Fabric metal-block archive capture metadata is invalid")
    client.validate_hex_digest(
        capture_metadata.get("sha256"),
        "Fabric metal-block archived capture metadata",
    )

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or list(artifacts) != ["production", "harness"]:
        raise client.E2EError("Fabric metal-block archive artifact inventory changed")
    validated_artifacts: dict[str, dict[str, object]] = {}
    for role, mod_id, file_name in EXPECTED_ARTIFACTS:
        artifact = artifacts.get(role)
        if (
            not isinstance(artifact, dict)
            or set(artifact) != {"mod_id", "file_name", "size", "sha256"}
            or artifact.get("mod_id") != mod_id
            or artifact.get("file_name") != file_name
            or type(artifact.get("size")) is not int
            or int(artifact["size"]) <= 0
        ):
            raise client.E2EError(
                f"Fabric metal-block archived {role} artifact is invalid"
            )
        client.validate_hex_digest(
            artifact.get("sha256"),
            f"Fabric metal-block archived {role} artifact",
        )
        validated_artifacts[role] = artifact

    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(ARCHIVE_PAYLOAD_PATHS):
        raise client.E2EError("Fabric metal-block archive payload inventory changed")
    validated_files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if (
            not isinstance(record, dict)
            or set(record) != {"size", "sha256"}
            or type(record.get("size")) is not int
            or int(record["size"]) <= 0
        ):
            raise client.E2EError(
                f"Fabric metal-block archive payload record is invalid: {relative_path}"
            )
        client.validate_hex_digest(
            record.get("sha256"),
            f"Fabric metal-block archived payload {relative_path}",
        )
        path = archive_root / relative_path
        if path.stat().st_size != record["size"] or evidence.sha256_file(path) != record["sha256"]:
            raise client.E2EError(
                f"Fabric metal-block archive payload differs from manifest: {relative_path}"
            )
        validated_files[relative_path] = record
    return validated_artifacts, validated_files


def validate_archived_evidence(archive_root: Path) -> MetalBlockEvidenceSummary:
    """Validates a self-contained capture-time archive without live profile state."""

    validate_archive_inventory(archive_root)
    manifest = evidence.require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME,
        "Fabric metal-block archive manifest",
    )
    manifest_artifacts, _manifest_files = validate_archive_manifest(
        archive_root,
        manifest,
    )
    done_path = archive_root / "reports" / "done.marker"
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise client.E2EError("Fabric metal-block completion marker is invalid")
    report = evidence.require_json_object(
        archive_root / "reports" / "report.json",
        "Fabric metal-block archived scenario report",
    )
    ratio, report_artifacts = validate_report_contract(archive_root, report)
    if report_artifacts != manifest_artifacts:
        raise client.E2EError(
            "Fabric metal-block archived report artifact digests differ from manifest"
        )
    return MetalBlockEvidenceSummary(
        profile_id=PROFILE_ID,
        assertion_count=len(EXPECTED_ASSERTION_NAMES),
        screenshot_count=len(EXPECTED_SCREENSHOTS),
        changed_pixel_ratio=ratio,
        production_sha256=str(report_artifacts["production"]["sha256"]),
        harness_sha256=str(report_artifacts["harness"]["sha256"]),
    )


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate exact Fabric 1.20.1 metal-block-registry evidence."
    )
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument(
        "--live",
        action="store_true",
        help="validate the current repository-owned v23 capture runtime",
    )
    operation.add_argument(
        "--archive",
        type=Path,
        help="validate one frozen v23 archive without consulting live state",
    )
    operation.add_argument(
        "--create-archive-manifest",
        type=Path,
        metavar="ARCHIVE",
        help="seal copied v23 payload from one explicit owned capture runtime",
    )
    parser.add_argument(
        "--capture-runtime",
        type=Path,
        help="exact repository-owned capture runtime used only while sealing",
    )
    parser.add_argument(
        "--profile-manifest",
        type=Path,
        help="exact active v23 profile manifest used only while sealing",
    )
    arguments = parser.parse_args()
    sealing = arguments.create_archive_manifest is not None
    auxiliary_arguments = (arguments.capture_runtime, arguments.profile_manifest)
    if sealing and any(value is None for value in auxiliary_arguments):
        parser.error(
            "--create-archive-manifest requires --capture-runtime and "
            "--profile-manifest"
        )
    if not sealing and any(value is not None for value in auxiliary_arguments):
        parser.error(
            "--capture-runtime and --profile-manifest are valid only with "
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
            summary = validate_archived_evidence(arguments.create_archive_manifest)
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
        f"changed-pixel ratio {summary.changed_pixel_ratio:.6f}"
    )
    print(f"Production SHA-256: {summary.production_sha256}")
    print(f"Harness SHA-256: {summary.harness_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
