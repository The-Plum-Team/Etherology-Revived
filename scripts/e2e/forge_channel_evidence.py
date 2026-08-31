#!/usr/bin/env python3
"""Validate live or archived Forge ethereal-channel evidence."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_client
import forge_evidence


SCENARIO_ID = "ethereal-channel"
PROFILE_ID = "etherology-e2e-forge-1.20.1-v11"
WORLD_DIRECTORY_NAME = "etherology-e2e-forge-channel-world"
MAXIMUM_SCENARIO_EVIDENCE_SIZE = 64 * 1024 * 1024
MINIMUM_TRANSFER_CHANGED_PIXEL_RATIO = 0.0005
MAXIMUM_TRANSFER_CHANGED_PIXEL_RATIO = 0.10
MAXIMUM_TRANSFER_STRUCTURAL_CHANGE_RATIO = 0.05
MAXIMUM_REOPENED_HIGH_DELTA_PIXEL_RATIO = 0.005
MAXIMUM_REOPENED_STRUCTURAL_CHANGE_RATIO = 0.01
MAXIMUM_REOPENED_LOCAL_STRUCTURAL_CHANGE_RATIO = 0.04
PIXEL_CHANGE_CHANNEL_THRESHOLD = 8
HIGH_DELTA_CHANNEL_THRESHOLD = 16
STRUCTURAL_LUMINANCE_DELTA_THRESHOLD = 12
STRUCTURAL_LUMINANCE_WEIGHTS = (77, 150, 29)
STRUCTURAL_WINDOW_SIZE = 128
STRUCTURAL_WINDOW_STEP = 16
CHANNEL_FIXTURE_REGION_PERCENT = (32, 25, 76, 76)
EXPECTED_FRAMEBUFFER_DIMENSIONS = (1920, 1080)
EXPECTED_LEVER_SUPPORT_TOPOLOGY = (
    "lever=minecraft:lever;survived=true;can_place=true;face=wall;"
    "facing=north;channel=etherology:ethereal_channel;"
    "channel_block_entity=etherology:ethereal_channel_block_entity;"
    "channel_facing=east;north=in;east=out;west=empty;cross=true"
)
EXPECTED_CAPTURE_RENDER_READY_EVIDENCE = (
    "gated=true;transferred=true;reopened=true"
)
SCREENSHOTS = {
    "gated": "ethereal-channel-gated.png",
    "transferred": "ethereal-channel-transferred.png",
    "reopened": "ethereal-channel-reopened.png",
}
REQUIRED_ASSERTIONS = {
    "lifecycle",
    "forge_loaded",
    "etherology_loaded",
    "chunk_loaded",
    "chunk_forced",
    "fixture_positions_distinct",
    "prepowered_placement",
    "channel_block_id",
    "channel_block_entity_id",
    "storage_block_id",
    "storage_block_entity_id",
    "fixture_topology",
    "lever_support_topology",
    "channel_nbt_reconstructed",
    "channel_nbt_sync_contract",
    "gated_activated",
    "gated_received_one",
    "gated_retained_without_forwarding",
    "release_deactivated",
    "transfer_fifth_tick",
    "transfer_exact_one",
    "transfer_total_conserved",
    "transfer_no_reverse",
    "evaporation_fifth_tick",
    "evaporation_exact_point_two",
    "evaporation_flags",
    "client_mirror",
    "gated_capture_mirror",
    "transferred_capture_mirror",
    "reopened_capture_mirror",
    "capture_render_ready",
    "capture_camera_exact",
    "forced_save",
    "saved_snapshot",
    "restart_exact_state",
    "restart_block_entity_types",
    "restart_client_mirror",
    "gated_screenshot",
    "transferred_screenshot",
    "reopened_screenshot",
    "artifact_etherology",
    "artifact_etherology_e2e_harness",
}
REPORT_FIELDS = {
    "schema",
    "scenario",
    "profile_id",
    "profile_manifest_size",
    "profile_manifest_sha256",
    "artifact_node",
    "minecraft",
    "loader",
    "loader_version",
    "java",
    "passed",
    "client_ticks",
    "framebuffer_width",
    "framebuffer_height",
    "assertions",
    "artifacts",
}
ARCHIVE_MANIFEST_NAME = "archive-manifest.json"
ARCHIVE_KIND = "etherology-forge-e2e-evidence"
ARCHIVE_VERIFICATION_SCOPE = (
    "archive-integrity-only-current-sources-and-built-artifacts-not-compared"
)
ARCHIVED_PROFILE_MANIFEST_PATH = "scripts/e2e/forge-1.20.1-profile.json"
ARCHIVE_ARTIFACT_IDENTITIES = {
    "production": ("etherology", "etherology-forge-under-test.jar"),
    "harness": (
        "etherology_e2e_harness",
        "etherology-forge-e2e-harness.jar",
    ),
}
ARCHIVE_PAYLOAD_PATHS = (
    "reports/report.json",
    "reports/done.marker",
    *(f"screenshots/{file_name}" for file_name in SCREENSHOTS.values()),
)
ARCHIVE_DIRECTORIES = {"reports", "screenshots"}
FATAL_GAME_LOG_MARKERS = (
    "Mixin apply failed",
    "MixinTransformerError",
    "InvalidMixinException",
    "NoClassDefFoundError",
    "NoSuchMethodError",
    "NoSuchFieldError",
    "ExceptionInInitializerError",
    "Could not execute entrypoint stage",
    "Could not find required mod",
    "A mod crashed on startup!",
    "Encountered an unexpected exception",
    "java.lang.OutOfMemoryError",
    "Forge ethereal-channel lifecycle failure:",
)


@dataclass(frozen=True)
class EvidenceSummary:
    """Reports the verified live channel evidence inventory."""

    assertion_count: int
    screenshot_count: int
    transfer_changed_pixel_ratio: float
    reopened_structural_change_ratio: float
    production_sha256: str
    harness_sha256: str


@dataclass(frozen=True)
class ArchiveEvidenceSummary:
    """Reports integrity established solely from one channel archive."""

    profile_id: str
    assertion_count: int
    screenshot_count: int
    transfer_changed_pixel_ratio: float
    reopened_structural_change_ratio: float
    production_sha256: str
    harness_sha256: str


@dataclass(frozen=True)
class ValidatedArchiveManifest:
    """Holds validated channel archive provenance and payload records."""

    profile_id: str
    profile_manifest_size: int
    profile_manifest_sha256: str
    runtime: dict[str, object]
    artifacts: dict[str, dict[str, object]]
    files: dict[str, dict[str, object]]


@dataclass(frozen=True)
class FrameRelationshipMetrics:
    """Reports the independently verified relationships between capture frames."""

    transfer_changed_pixel_ratio: float
    transfer_structural_change_ratio: float
    reopened_high_delta_pixel_ratio: float
    reopened_structural_change_ratio: float
    reopened_local_structural_change_ratio: float


def fixture_changed_pixel_ratio(
    left: forge_evidence.PngImage,
    right: forge_evidence.PngImage,
) -> float:
    """Returns the changed-pixel ratio inside the deterministic fixture ROI."""
    return _fixture_pixel_change_ratio(
        left,
        right,
        PIXEL_CHANGE_CHANNEL_THRESHOLD,
    )


def fixture_high_delta_pixel_ratio(
    left: forge_evidence.PngImage,
    right: forge_evidence.PngImage,
) -> float:
    """Returns the fixture ratio exceeding the restart-lighting delta band."""
    return _fixture_pixel_change_ratio(
        left,
        right,
        HIGH_DELTA_CHANNEL_THRESHOLD,
    )


def _fixture_pixel_change_ratio(
    left: forge_evidence.PngImage,
    right: forge_evidence.PngImage,
    channel_threshold: int,
) -> float:
    left_x, top_y, right_x, bottom_y = _fixture_region_bounds(left, right)

    changed = 0
    for y in range(top_y, bottom_y):
        for x in range(left_x, right_x):
            offset = (y * left.width + x) * 3
            if any(
                abs(left.pixels[offset + channel] - right.pixels[offset + channel])
                >= channel_threshold
                for channel in range(3)
            ):
                changed += 1
    return changed / ((right_x - left_x) * (bottom_y - top_y))


def fixture_structural_change_ratios(
    left: forge_evidence.PngImage,
    right: forge_evidence.PngImage,
) -> tuple[float, float]:
    """Returns whole-fixture and worst-window local-contrast drift ratios."""
    left_x, top_y, right_x, bottom_y = _fixture_region_bounds(left, right)
    if right_x - left_x < 3 or bottom_y - top_y < 3:
        raise forge_client.E2EError(
            "Forge channel screenshot is too small for structural fixture comparison"
        )

    left_luminance = _luminance_pixels(left)
    right_luminance = _luminance_pixels(right)
    comparison_width = right_x - left_x - 2
    comparison_height = bottom_y - top_y - 2
    changes = bytearray()
    for y in range(top_y + 1, bottom_y - 1):
        for x in range(left_x + 1, right_x - 1):
            pixel_index = y * left.width + x
            left_contrast = _local_luminance_contrast(
                left_luminance,
                left.width,
                pixel_index,
            )
            right_contrast = _local_luminance_contrast(
                right_luminance,
                right.width,
                pixel_index,
            )
            changes.append(
                abs(left_contrast - right_contrast)
                >= STRUCTURAL_LUMINANCE_DELTA_THRESHOLD
            )
    global_ratio = sum(changes) / len(changes)
    local_ratio = _maximum_local_structural_change_ratio(
        changes,
        comparison_width,
        comparison_height,
    )
    return global_ratio, local_ratio


def _maximum_local_structural_change_ratio(
    changes: bytearray,
    width: int,
    height: int,
) -> float:
    window_size = min(STRUCTURAL_WINDOW_SIZE, width, height)
    x_offsets = _window_offsets(width, window_size)
    y_offsets = _window_offsets(height, window_size)
    maximum_changed = 0
    for top_y in y_offsets:
        for left_x in x_offsets:
            changed = sum(
                sum(
                    changes[
                        y * width + left_x : y * width + left_x + window_size
                    ]
                )
                for y in range(top_y, top_y + window_size)
            )
            maximum_changed = max(maximum_changed, changed)
    return maximum_changed / (window_size * window_size)


def _window_offsets(length: int, window_size: int) -> list[int]:
    offsets = list(
        range(0, length - window_size + 1, STRUCTURAL_WINDOW_STEP)
    )
    final_offset = length - window_size
    if offsets[-1] != final_offset:
        offsets.append(final_offset)
    return offsets


def _fixture_region_bounds(
    left: forge_evidence.PngImage,
    right: forge_evidence.PngImage,
) -> tuple[int, int, int, int]:
    if (left.width, left.height) != (right.width, right.height):
        raise forge_client.E2EError(
            "Cannot compare Forge channel screenshots with different dimensions"
        )
    left_percent, top_percent, right_percent, bottom_percent = (
        CHANNEL_FIXTURE_REGION_PERCENT
    )
    bounds = (
        left.width * left_percent // 100,
        left.height * top_percent // 100,
        left.width * right_percent // 100,
        left.height * bottom_percent // 100,
    )
    left_x, top_y, right_x, bottom_y = bounds
    if right_x <= left_x or bottom_y <= top_y:
        raise forge_client.E2EError(
            "Forge channel screenshot is too small for the fixture region"
        )
    return bounds


def _luminance_pixels(image: forge_evidence.PngImage) -> bytearray:
    red_weight, green_weight, blue_weight = STRUCTURAL_LUMINANCE_WEIGHTS
    return bytearray(
        (
            red_weight * image.pixels[offset]
            + green_weight * image.pixels[offset + 1]
            + blue_weight * image.pixels[offset + 2]
            + 128
        )
        // 256
        for offset in range(0, len(image.pixels), 3)
    )


def _local_luminance_contrast(
    luminance: bytearray,
    width: int,
    pixel_index: int,
) -> int:
    return (
        4 * luminance[pixel_index]
        - luminance[pixel_index - 1]
        - luminance[pixel_index + 1]
        - luminance[pixel_index - width]
        - luminance[pixel_index + width]
    )


def validate_report_contract(
    expected_artifact_node: str,
    report: dict[str, object],
) -> dict[str, str]:
    """Validates the exact passing channel report and assertion inventory."""
    if set(report) != REPORT_FIELDS:
        raise forge_client.E2EError("Forge channel report field inventory changed")
    if (
        report.get("schema") != 1
        or report.get("scenario") != SCENARIO_ID
        or report.get("profile_id") != PROFILE_ID
        or type(report.get("profile_manifest_size")) is not int
        or int(report["profile_manifest_size"]) <= 0
        or report.get("artifact_node") != expected_artifact_node
        or report.get("minecraft") != "1.20.1"
        or report.get("loader") != "forge"
        or report.get("loader_version") != "47.4.9"
        or report.get("java") != 17
        or report.get("passed") is not True
        or type(report.get("client_ticks")) is not int
        or int(report["client_ticks"]) <= 0
        or report.get("framebuffer_width") != EXPECTED_FRAMEBUFFER_DIMENSIONS[0]
        or report.get("framebuffer_height") != EXPECTED_FRAMEBUFFER_DIMENSIONS[1]
    ):
        raise forge_client.E2EError(
            "Forge channel report did not record the exact passing 1.20.1 lifecycle"
        )
    forge_client.validate_hex_digest(
        report.get("profile_manifest_sha256"),
        "captured Forge channel profile manifest",
    )

    raw_assertions = report.get("assertions")
    if not isinstance(raw_assertions, list):
        raise forge_client.E2EError("Forge channel report has no assertion inventory")
    actual_by_name: dict[str, str] = {}
    for assertion in raw_assertions:
        if not isinstance(assertion, dict) or set(assertion) != {
            "name",
            "passed",
            "actual",
        }:
            raise forge_client.E2EError(
                "Forge channel report contains an invalid assertion"
            )
        name = assertion.get("name")
        actual = assertion.get("actual")
        if (
            not isinstance(name, str)
            or name in actual_by_name
            or not isinstance(actual, str)
        ):
            raise forge_client.E2EError(
                "Forge channel report contains a duplicated or malformed assertion"
            )
        if assertion.get("passed") is not True:
            raise forge_client.E2EError(
                f"Forge channel assertion did not pass: {name}"
            )
        actual_by_name[name] = actual
    if set(actual_by_name) != REQUIRED_ASSERTIONS:
        raise forge_client.E2EError(
            "Forge channel assertion inventory changed: "
            f"missing={sorted(REQUIRED_ASSERTIONS - set(actual_by_name))}, "
            f"unexpected={sorted(set(actual_by_name) - REQUIRED_ASSERTIONS)}"
        )
    if actual_by_name["forced_save"] != "true" or actual_by_name["lifecycle"] != "":
        raise forge_client.E2EError(
            "Forge channel save or lifecycle evidence is inconsistent"
        )
    if actual_by_name["lever_support_topology"] != EXPECTED_LEVER_SUPPORT_TOPOLOGY:
        raise forge_client.E2EError(
            "Forge channel lever support/topology evidence is not exact"
        )
    for name in (
        "gated_capture_mirror",
        "transferred_capture_mirror",
        "reopened_capture_mirror",
    ):
        if actual_by_name[name] != "true":
            raise forge_client.E2EError(
                f"Forge channel capture-time mirror evidence is not exact: {name}"
            )
    if (
        actual_by_name["capture_render_ready"]
        != EXPECTED_CAPTURE_RENDER_READY_EVIDENCE
    ):
        raise forge_client.E2EError(
            "Forge channel capture-time renderer evidence is not exact"
        )
    if (
        actual_by_name["capture_camera_exact"]
        != EXPECTED_CAPTURE_RENDER_READY_EVIDENCE
    ):
        raise forge_client.E2EError(
            "Forge channel capture-time camera evidence is not exact"
        )
    for name in (
        "saved_snapshot",
        "restart_exact_state",
        "restart_block_entity_types",
        "restart_client_mirror",
    ):
        if not actual_by_name[name] or actual_by_name[name] == "null":
            raise forge_client.E2EError(
                f"Forge channel assertion has no restart evidence: {name}"
            )
    return actual_by_name


def validate_report(
    configuration: forge_client.ResolvedConfiguration,
    report: dict[str, object],
) -> dict[str, str]:
    """Validates a channel report against the active release configuration."""
    assertions = validate_report_contract(
        str(configuration.artifact_lane["artifact_node"]),
        report,
    )
    descriptor = forge_client.profile_descriptor(configuration)
    profile_manifest = forge_client.require_object(descriptor, "profile_manifest")
    if (
        report.get("profile_id") != descriptor.get("profile_id")
        or report.get("profile_manifest_size") != profile_manifest.get("size")
        or report.get("profile_manifest_sha256") != profile_manifest.get("sha256")
    ):
        raise forge_client.E2EError(
            "Forge channel report differs from the active capture profile provenance"
        )
    return assertions


def validate_screenshots_contract(
    expected_dimensions: tuple[int, int],
    scenario_root: Path,
    assertions: dict[str, str],
) -> tuple[float, float, list[Path]]:
    """Validates exact PNG inventory, hashes, dimensions, and fixture deltas."""
    screenshots_directory = scenario_root / "screenshots"
    if not screenshots_directory.is_dir() or screenshots_directory.is_symlink():
        raise forge_client.E2EError(
            "Forge channel screenshot directory is missing or linked"
        )
    entries = list(screenshots_directory.iterdir())
    if any(not path.is_file() or path.is_symlink() for path in entries):
        raise forge_client.E2EError(
            "Forge channel screenshot directory contains a linked or non-file entry"
        )
    if {path.name for path in entries} != set(SCREENSHOTS.values()):
        raise forge_client.E2EError("Forge channel screenshot inventory changed")

    images: dict[str, forge_evidence.PngImage] = {}
    paths: list[Path] = []
    for role, file_name in SCREENSHOTS.items():
        path = screenshots_directory / file_name
        image = forge_evidence.decode_png(path)
        if (image.width, image.height) != expected_dimensions:
            raise forge_client.E2EError(
                f"Forge channel screenshot has the wrong dimensions: {path}"
            )
        forge_evidence.assert_image_is_not_blank(image, str(path))
        digest = forge_evidence.sha256_file(path)
        expected_actual = (
            f"ScreenshotResult[passed=true, size={path.stat().st_size}, "
            f"sha256={digest}, failure=]"
        )
        if assertions[f"{role}_screenshot"] != expected_actual:
            raise forge_client.E2EError(
                f"Forge {role} channel assertion differs from the frozen PNG"
            )
        images[role] = image
        paths.append(path)

    metrics = validate_frame_relationships(images)
    return (
        metrics.transfer_changed_pixel_ratio,
        metrics.reopened_structural_change_ratio,
        paths,
    )


def validate_frame_relationships(
    images: dict[str, forge_evidence.PngImage],
) -> FrameRelationshipMetrics:
    """Validates mechanic change, camera anchoring, and restart persistence."""
    transfer_ratio = fixture_changed_pixel_ratio(
        images["gated"],
        images["transferred"],
    )
    if transfer_ratio < MINIMUM_TRANSFER_CHANGED_PIXEL_RATIO:
        raise forge_client.E2EError(
            "Forge gated-to-transferred channel visual change is below threshold: "
            f"{transfer_ratio:.6f}"
        )
    if transfer_ratio > MAXIMUM_TRANSFER_CHANGED_PIXEL_RATIO:
        raise forge_client.E2EError(
            "Forge gated-to-transferred frame drift exceeds the camera anchor: "
            f"{transfer_ratio:.6f}"
        )
    transfer_structural_ratio, _transfer_local_ratio = (
        fixture_structural_change_ratios(
            images["gated"],
            images["transferred"],
        )
    )
    if transfer_structural_ratio > MAXIMUM_TRANSFER_STRUCTURAL_CHANGE_RATIO:
        raise forge_client.E2EError(
            "Forge gated-to-transferred structure exceeds the camera anchor: "
            f"{transfer_structural_ratio:.6f}"
        )

    reopened_high_delta_ratio = fixture_high_delta_pixel_ratio(
        images["transferred"],
        images["reopened"],
    )
    if reopened_high_delta_ratio > MAXIMUM_REOPENED_HIGH_DELTA_PIXEL_RATIO:
        raise forge_client.E2EError(
            "Forge reopened channel high-delta pixels exceed restart lighting: "
            f"{reopened_high_delta_ratio:.6f}"
        )
    reopened_ratio, reopened_local_ratio = fixture_structural_change_ratios(
        images["transferred"],
        images["reopened"],
    )
    if reopened_ratio > MAXIMUM_REOPENED_STRUCTURAL_CHANGE_RATIO:
        raise forge_client.E2EError(
            "Forge reopened channel structure drifted from transferred state: "
            f"{reopened_ratio:.6f}"
        )
    if reopened_local_ratio > MAXIMUM_REOPENED_LOCAL_STRUCTURAL_CHANGE_RATIO:
        raise forge_client.E2EError(
            "Forge reopened channel local structure drifted from transferred state: "
            f"{reopened_local_ratio:.6f}"
        )
    return FrameRelationshipMetrics(
        transfer_changed_pixel_ratio=transfer_ratio,
        transfer_structural_change_ratio=transfer_structural_ratio,
        reopened_high_delta_pixel_ratio=reopened_high_delta_ratio,
        reopened_structural_change_ratio=reopened_ratio,
        reopened_local_structural_change_ratio=reopened_local_ratio,
    )


def validate_screenshots(
    configuration: forge_client.ResolvedConfiguration,
    scenario_root: Path,
    assertions: dict[str, str],
) -> tuple[float, float, list[Path]]:
    """Validates screenshots using the active profile capture dimensions."""
    capture = forge_client.require_object(
        forge_client.require_object(configuration.manifest, "evidence"),
        "capture",
    )
    return validate_screenshots_contract(
        (int(capture["width"]), int(capture["height"])),
        scenario_root,
        assertions,
    )


def validate_archive_root_identity(archive_root: Path, profile_id: str) -> None:
    """Requires the channel archive directory to identify profile v11 exactly."""
    if profile_id != PROFILE_ID or archive_root.name != "ethereal-channel-v11":
        raise forge_client.E2EError(
            "Forge channel archive directory does not identify profile v11"
        )


def validate_archive_directory_inventory(
    archive_root: Path,
    include_manifest: bool,
) -> None:
    """Validates the closed archive file and directory inventory."""
    if not archive_root.is_dir() or archive_root.is_symlink():
        raise forge_client.E2EError(
            f"Forge channel evidence archive is missing or linked: {archive_root}"
        )
    files: set[str] = set()
    directories: set[str] = set()
    for path in archive_root.rglob("*"):
        relative_path = path.relative_to(archive_root).as_posix()
        if path.is_symlink():
            raise forge_client.E2EError(
                f"Forge channel archive contains a linked entry: {relative_path}"
            )
        if path.is_file():
            files.add(relative_path)
        elif path.is_dir():
            directories.add(relative_path)
        else:
            raise forge_client.E2EError(
                f"Forge channel archive contains a special entry: {relative_path}"
            )
    expected_files = set(ARCHIVE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    if files != expected_files or directories != ARCHIVE_DIRECTORIES:
        raise forge_client.E2EError(
            "Forge channel archive inventory changed: "
            f"missing_files={sorted(expected_files - files)}, "
            f"unexpected_files={sorted(files - expected_files)}, "
            f"missing_directories={sorted(ARCHIVE_DIRECTORIES - directories)}, "
            f"unexpected_directories={sorted(directories - ARCHIVE_DIRECTORIES)}"
        )


def validate_archive_size(archive_root: Path) -> None:
    """Rejects an archive whose regular payload exceeds the evidence bound."""
    size = sum(
        path.stat().st_size
        for path in archive_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if size > MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise forge_client.E2EError(
            "Forge channel evidence archive exceeds the size bound"
        )


def archive_artifacts_from_report(
    report: dict[str, object],
    assertions: dict[str, str],
) -> dict[str, dict[str, object]]:
    """Extracts and validates immutable artifact provenance for an archive."""
    by_mod_id = forge_evidence.report_artifacts_by_mod_id(report)
    expected_by_role: dict[str, dict[str, object]] = {}
    for role, (mod_id, file_name) in ARCHIVE_ARTIFACT_IDENTITIES.items():
        artifact = by_mod_id.get(mod_id)
        if artifact is None or artifact.get("file_name") != file_name:
            raise forge_client.E2EError(
                f"Forge channel archive has the wrong {role} artifact identity"
            )
        expected_by_role[role] = {
            "mod_id": mod_id,
            "file_name": file_name,
            "size": artifact.get("size"),
            "sha256": artifact.get("sha256"),
        }
    forge_evidence.validate_report_artifacts(
        report,
        assertions,
        expected_by_role,
    )
    return expected_by_role


def archive_file_records(archive_root: Path) -> dict[str, dict[str, object]]:
    """Builds exact size and SHA-256 records for every archive payload."""
    return {
        relative_path: {
            "size": (archive_root / relative_path).stat().st_size,
            "sha256": forge_evidence.sha256_file(archive_root / relative_path),
        }
        for relative_path in ARCHIVE_PAYLOAD_PATHS
    }


def build_archive_manifest(
    configuration: forge_client.ResolvedConfiguration,
    archive_root: Path,
) -> dict[str, object]:
    """Builds a capture-time channel archive manifest without live claims."""
    validate_archive_directory_inventory(archive_root, include_manifest=False)
    validate_archive_size(archive_root)
    profile_id = str(forge_client.profile_spec(configuration)["id"])
    validate_archive_root_identity(archive_root, profile_id)
    for role, (mod_id, file_name) in ARCHIVE_ARTIFACT_IDENTITIES.items():
        artifact = forge_client.artifact_spec(configuration, role)
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            raise forge_client.E2EError(
                f"The active Forge profile has the wrong {role} archive identity"
            )

    report = forge_evidence.require_json_object(
        archive_root / "reports" / "report.json",
        "Forge archived channel report",
    )
    assertions = validate_report_contract(
        str(configuration.artifact_lane["artifact_node"]),
        report,
    )
    artifacts = archive_artifacts_from_report(report, assertions)
    _transfer_ratio, _reopened_ratio, screenshot_paths = validate_screenshots(
        configuration,
        archive_root,
        assertions,
    )
    done_path = archive_root / "reports" / "done.marker"
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError(
            "Forge archived channel completion marker has unexpected content"
        )
    profile_manifest_path = configuration.profile_manifest_path
    if not profile_manifest_path.is_file() or profile_manifest_path.is_symlink():
        raise forge_client.E2EError(
            f"Tracked Forge profile manifest is missing or linked: {profile_manifest_path}"
        )
    captured_profile_id = str(report["profile_id"])
    captured_profile_manifest_size = int(report["profile_manifest_size"])
    captured_profile_manifest_sha256 = str(report["profile_manifest_sha256"])
    if (
        captured_profile_id != profile_id
        or profile_manifest_path.stat().st_size != captured_profile_manifest_size
        or forge_evidence.sha256_file(profile_manifest_path)
        != captured_profile_manifest_sha256
    ):
        raise forge_client.E2EError(
            "Forge channel capture profile provenance differs from the tracked profile"
        )
    return {
        "schema": 1,
        "kind": ARCHIVE_KIND,
        "verification_scope": ARCHIVE_VERIFICATION_SCOPE,
        "scenario": SCENARIO_ID,
        "profile": {
            "id": captured_profile_id,
            "manifest_path": ARCHIVED_PROFILE_MANIFEST_PATH,
            "manifest_size": captured_profile_manifest_size,
            "manifest_sha256": captured_profile_manifest_sha256,
        },
        "runtime": {
            "artifact_node": report["artifact_node"],
            "minecraft": report["minecraft"],
            "loader": report["loader"],
            "loader_version": report["loader_version"],
            "java": report["java"],
            "framebuffer_width": report["framebuffer_width"],
            "framebuffer_height": report["framebuffer_height"],
        },
        "assertion_count": len(assertions),
        "screenshot_count": len(screenshot_paths),
        "artifacts": artifacts,
        "files": archive_file_records(archive_root),
    }


def write_archive_manifest(
    configuration: forge_client.ResolvedConfiguration,
    archive_root: Path,
) -> Path:
    """Creates the channel archive manifest exactly once."""
    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if manifest_path.exists() or manifest_path.is_symlink():
        raise forge_client.E2EError(
            f"Forge channel archive manifest already exists: {manifest_path}"
        )
    manifest = build_archive_manifest(configuration, archive_root)
    try:
        with manifest_path.open("x", encoding="utf-8", newline="\n") as handle:
            json.dump(manifest, handle, indent=2)
            handle.write("\n")
    except FileExistsError as exception:
        raise forge_client.E2EError(
            f"Forge channel archive manifest already exists: {manifest_path}"
        ) from exception
    return manifest_path


def validate_archive_manifest_shape(
    archive_root: Path,
    manifest: dict[str, object],
) -> ValidatedArchiveManifest:
    """Validates all channel archive manifest identities and records."""
    if set(manifest) != {
        "schema",
        "kind",
        "verification_scope",
        "scenario",
        "profile",
        "runtime",
        "assertion_count",
        "screenshot_count",
        "artifacts",
        "files",
    }:
        raise forge_client.E2EError(
            "Forge channel archive manifest field inventory changed"
        )
    if (
        manifest.get("schema") != 1
        or manifest.get("kind") != ARCHIVE_KIND
        or manifest.get("verification_scope") != ARCHIVE_VERIFICATION_SCOPE
        or manifest.get("scenario") != SCENARIO_ID
        or manifest.get("assertion_count") != len(REQUIRED_ASSERTIONS)
        or manifest.get("screenshot_count") != len(SCREENSHOTS)
    ):
        raise forge_client.E2EError("Forge channel archive manifest identity is invalid")

    profile = manifest.get("profile")
    if not isinstance(profile, dict) or set(profile) != {
        "id",
        "manifest_path",
        "manifest_size",
        "manifest_sha256",
    }:
        raise forge_client.E2EError(
            "Forge channel archive profile provenance is malformed"
        )
    profile_id = profile.get("id")
    if not isinstance(profile_id, str):
        raise forge_client.E2EError("Forge channel archive profile id is malformed")
    validate_archive_root_identity(archive_root, profile_id)
    if profile.get("manifest_path") != ARCHIVED_PROFILE_MANIFEST_PATH:
        raise forge_client.E2EError("Forge channel archive profile path is invalid")
    if type(profile.get("manifest_size")) is not int or profile["manifest_size"] <= 0:
        raise forge_client.E2EError("Forge channel archive profile size is invalid")
    forge_client.validate_hex_digest(
        profile.get("manifest_sha256"),
        "archived Forge channel profile manifest",
    )

    runtime = manifest.get("runtime")
    expected_runtime = {
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "framebuffer_width": EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
        "framebuffer_height": EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
    }
    if not isinstance(runtime, dict) or runtime != expected_runtime:
        raise forge_client.E2EError(
            "Forge channel archive runtime identity is invalid"
        )

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or set(artifacts) != set(
        ARCHIVE_ARTIFACT_IDENTITIES
    ):
        raise forge_client.E2EError(
            "Forge channel archive artifact inventory changed"
        )
    expected_by_role: dict[str, dict[str, object]] = {}
    for role, (mod_id, file_name) in ARCHIVE_ARTIFACT_IDENTITIES.items():
        artifact = artifacts.get(role)
        if not isinstance(artifact, dict) or set(artifact) != {
            "mod_id",
            "file_name",
            "size",
            "sha256",
        }:
            raise forge_client.E2EError(
                f"Forge channel archive {role} provenance is malformed"
            )
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            raise forge_client.E2EError(
                f"Forge channel archive {role} identity is invalid"
            )
        if type(artifact.get("size")) is not int or int(artifact["size"]) <= 0:
            raise forge_client.E2EError(
                f"Forge channel archive {role} size is invalid"
            )
        forge_client.validate_hex_digest(
            artifact.get("sha256"),
            f"archived Forge channel {role} artifact",
        )
        expected_by_role[role] = artifact

    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(ARCHIVE_PAYLOAD_PATHS):
        raise forge_client.E2EError("Forge channel archive payload inventory changed")
    expected_files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if not isinstance(record, dict) or set(record) != {"size", "sha256"}:
            raise forge_client.E2EError(
                f"Forge channel archive payload record is malformed: {relative_path}"
            )
        if type(record.get("size")) is not int or int(record["size"]) <= 0:
            raise forge_client.E2EError(
                f"Forge channel archive payload size is invalid: {relative_path}"
            )
        forge_client.validate_hex_digest(
            record.get("sha256"),
            f"archived Forge channel payload {relative_path}",
        )
        expected_files[relative_path] = record
    return ValidatedArchiveManifest(
        profile_id=profile_id,
        profile_manifest_size=int(profile["manifest_size"]),
        profile_manifest_sha256=str(profile["manifest_sha256"]),
        runtime=runtime,
        artifacts=expected_by_role,
        files=expected_files,
    )


def validate_current_artifacts(
    expected_by_role: dict[str, dict[str, object]],
    production_path: Path,
    harness_path: Path,
) -> None:
    """Compares explicitly supplied remapped JARs to archived size and SHA-256."""
    paths = {
        "production": production_path,
        "harness": harness_path,
    }
    if set(expected_by_role) != set(paths):
        raise forge_client.E2EError(
            "Forge channel current-artifact role inventory changed"
        )
    for role, path in paths.items():
        if not path.is_file() or path.is_symlink():
            raise forge_client.E2EError(
                f"Current Forge channel {role} artifact is missing or linked: {path}"
            )
        expected = expected_by_role[role]
        if (
            path.stat().st_size != expected.get("size")
            or forge_evidence.sha256_file(path) != expected.get("sha256")
        ):
            raise forge_client.E2EError(
                f"Current Forge channel {role} artifact differs from archive provenance"
            )


def validate_current_profile(
    expected_profile_id: str,
    expected_size: int,
    expected_sha256: str,
    current_profile: Path,
) -> None:
    """Compares the active isolated profile with capture-time provenance."""
    if not current_profile.is_file() or current_profile.is_symlink():
        raise forge_client.E2EError(
            f"Current Forge channel profile is missing or linked: {current_profile}"
        )
    if (
        current_profile.stat().st_size != expected_size
        or forge_evidence.sha256_file(current_profile) != expected_sha256
    ):
        raise forge_client.E2EError(
            "Current Forge channel profile differs from archive provenance"
        )
    manifest = forge_evidence.require_json_object(
        current_profile,
        "Current Forge channel profile manifest",
    )
    profile = manifest.get("profile")
    if not isinstance(profile, dict) or profile.get("id") != expected_profile_id:
        raise forge_client.E2EError(
            "Current Forge channel profile has the wrong isolated identity"
        )


def validate_archived_scenario(
    archive_root: Path,
    current_production: Path | None = None,
    current_harness: Path | None = None,
    current_profile: Path | None = None,
) -> ArchiveEvidenceSummary:
    """Validates a frozen archive, optionally against current capture inputs."""
    current_inputs = (current_production, current_harness, current_profile)
    if any(path is not None for path in current_inputs) and not all(
        path is not None for path in current_inputs
    ):
        raise forge_client.E2EError(
            "Current profile, production, and harness must be supplied together"
        )
    validate_archive_directory_inventory(archive_root, include_manifest=True)
    validate_archive_size(archive_root)
    manifest = forge_evidence.require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME,
        "Forge channel evidence archive manifest",
    )
    validated = validate_archive_manifest_shape(archive_root, manifest)
    for relative_path, record in validated.files.items():
        path = archive_root / relative_path
        if (
            path.stat().st_size != record["size"]
            or forge_evidence.sha256_file(path) != record["sha256"]
        ):
            raise forge_client.E2EError(
                f"Forge channel archive payload differs: {relative_path}"
            )

    done_path = archive_root / "reports" / "done.marker"
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError(
            "Forge archived channel completion marker has unexpected content"
        )
    report = forge_evidence.require_json_object(
        archive_root / "reports" / "report.json",
        "Forge archived channel report",
    )
    assertions = validate_report_contract(
        str(validated.runtime["artifact_node"]),
        report,
    )
    if (
        len(assertions) != manifest["assertion_count"]
        or report.get("profile_id") != validated.profile_id
        or report.get("profile_manifest_size")
        != validated.profile_manifest_size
        or report.get("profile_manifest_sha256")
        != validated.profile_manifest_sha256
        or report.get("framebuffer_width")
        != validated.runtime["framebuffer_width"]
        or report.get("framebuffer_height")
        != validated.runtime["framebuffer_height"]
    ):
        raise forge_client.E2EError(
            "Forge channel archive report differs from its manifest contract"
        )
    production_digest, harness_digest = forge_evidence.validate_report_artifacts(
        report,
        assertions,
        validated.artifacts,
    )
    if (
        current_production is not None
        and current_harness is not None
        and current_profile is not None
    ):
        validate_current_profile(
            validated.profile_id,
            validated.profile_manifest_size,
            validated.profile_manifest_sha256,
            current_profile,
        )
        validate_current_artifacts(
            validated.artifacts,
            current_production,
            current_harness,
        )
    transfer_ratio, reopened_ratio, screenshot_paths = validate_screenshots_contract(
        (
            int(validated.runtime["framebuffer_width"]),
            int(validated.runtime["framebuffer_height"]),
        ),
        archive_root,
        assertions,
    )
    if len(screenshot_paths) != manifest["screenshot_count"]:
        raise forge_client.E2EError(
            "Forge channel archive screenshot count differs from its manifest"
        )
    return ArchiveEvidenceSummary(
        profile_id=validated.profile_id,
        assertion_count=len(assertions),
        screenshot_count=len(screenshot_paths),
        transfer_changed_pixel_ratio=transfer_ratio,
        reopened_structural_change_ratio=reopened_ratio,
        production_sha256=production_digest,
        harness_sha256=harness_digest,
    )


def validate_game_lifecycle(
    configuration: forge_client.ResolvedConfiguration,
    runtime: Path,
    report: dict[str, object],
) -> None:
    """Validates crash absence, normal exit, save, and owned process identity."""
    game = forge_client.game_directory(configuration, runtime)
    crash_reports = game / "crash-reports"
    if not crash_reports.is_dir() or crash_reports.is_symlink():
        raise forge_client.E2EError(
            "Forge channel crash-report directory is missing or linked"
        )
    if any(crash_reports.iterdir()):
        raise forge_client.E2EError(
            f"Forge channel runtime contains a crash report: {crash_reports}"
        )
    latest_log = game / "logs" / "latest.log"
    if not latest_log.is_file() or latest_log.is_symlink():
        raise forge_client.E2EError(
            f"Forge channel game log is missing or linked: {latest_log}"
        )
    if latest_log.stat().st_size > forge_client.MAXIMUM_PROCESS_LOG_SIZE:
        raise forge_client.E2EError("Forge channel game log exceeds the size bound")
    log_content = latest_log.read_text(encoding="utf-8", errors="replace")
    fatal_marker = next(
        (marker for marker in FATAL_GAME_LOG_MARKERS if marker in log_content),
        None,
    )
    if fatal_marker is not None:
        raise forge_client.E2EError(
            f"Forge channel game log contains fatal marker: {fatal_marker}"
        )
    if "Forge ethereal-channel evidence complete:" not in log_content:
        raise forge_client.E2EError(
            "Forge channel game log has no evidence-complete marker"
        )
    if "Stopping!" not in log_content:
        raise forge_client.E2EError(
            "Forge channel client did not record a normal shutdown"
        )
    save = game / "saves" / WORLD_DIRECTORY_NAME
    if not save.is_dir() or save.is_symlink():
        raise forge_client.E2EError(
            f"Forge channel world save is missing or linked: {save}"
        )
    if report.get("passed") is not True:
        raise forge_client.E2EError("Forge channel lifecycle did not pass")

    if runtime.parent.name != "runtimes":
        raise forge_client.E2EError("Forge channel runtime is outside the owned layout")
    state_root = runtime.parent.parent
    state_path = forge_client.process_state_path(configuration, state_root)
    if not state_path.exists():
        return
    state = forge_client.read_owned_process_state(state_path, state_root)
    if (
        state.get("profile_id") != PROFILE_ID
        or state.get("scenario") != SCENARIO_ID
        or state.get("version_id") != forge_client.forge_version_id(configuration)
        or state.get("game_directory") != str(game)
    ):
        raise forge_client.E2EError(
            "Forge channel process state has the wrong profile identity"
        )
    if forge_client.process_exists(int(state["pid"])):
        raise forge_client.E2EError("Forge channel client is still running")
    process_log = forge_client.process_log_path(state, state_root)
    if not process_log.is_file() or process_log.is_symlink():
        raise forge_client.E2EError("Forge channel process log is missing or linked")
    if process_log.stat().st_size > forge_client.MAXIMUM_PROCESS_LOG_SIZE:
        raise forge_client.E2EError("Forge channel process log exceeds the size bound")
    process_content = process_log.read_text(encoding="utf-8", errors="replace")
    for expected_line in (
        f"profile_id={PROFILE_ID}",
        f"scenario={SCENARIO_ID}",
        f"game_directory={game}",
    ):
        if expected_line not in process_content:
            raise forge_client.E2EError(
                "Forge channel process log has the wrong profile identity"
            )


def validate_scenario(
    configuration: forge_client.ResolvedConfiguration,
    configured_scenario_id: str | None,
    runtime: Path | None = None,
) -> EvidenceSummary:
    """Validates one stopped live channel scenario in the isolated profile."""
    scenario_id = forge_client.resolve_scenario_id(
        configuration,
        configured_scenario_id,
    )
    if scenario_id != SCENARIO_ID:
        raise forge_client.E2EError(
            f"Unsupported Forge channel evidence scenario: {scenario_id}"
        )
    target_runtime = runtime or forge_client.runtime_root(configuration)
    forge_client.verify_evidence_layout(configuration, target_runtime)
    scenario_root = forge_client.evidence_root(
        configuration,
        target_runtime,
    ) / scenario_id
    reports_directory = scenario_root / "reports"
    if {path.name for path in reports_directory.iterdir()} != {
        "report.json",
        "done.marker",
    }:
        raise forge_client.E2EError("Forge channel report inventory changed")
    report_path = reports_directory / "report.json"
    done_path = reports_directory / "done.marker"
    if report_path.is_symlink() or done_path.is_symlink():
        raise forge_client.E2EError(
            "Forge channel report or completion marker is linked"
        )
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError(
            "Forge channel completion marker has unexpected content"
        )

    report = forge_evidence.require_json_object(
        report_path,
        "Forge channel scenario report",
    )
    assertions = validate_report(configuration, report)
    production_digest, harness_digest = forge_evidence.validate_artifacts(
        configuration,
        target_runtime,
        report,
        assertions,
    )
    transfer_ratio, reopened_ratio, screenshot_paths = validate_screenshots(
        configuration,
        scenario_root,
        assertions,
    )
    evidence_files = [report_path, *screenshot_paths]
    if any(
        done_path.stat().st_mtime_ns < path.stat().st_mtime_ns
        for path in evidence_files
    ):
        raise forge_client.E2EError(
            "Forge channel completion marker predates report or screenshots"
        )
    scenario_size = sum(
        path.stat().st_size
        for path in scenario_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if scenario_size > MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise forge_client.E2EError(
            "Forge channel live evidence exceeds the size bound"
        )
    validate_game_lifecycle(configuration, target_runtime, report)
    return EvidenceSummary(
        assertion_count=len(assertions),
        screenshot_count=len(screenshot_paths),
        transfer_changed_pixel_ratio=transfer_ratio,
        reopened_structural_change_ratio=reopened_ratio,
        production_sha256=production_digest,
        harness_sha256=harness_digest,
    )


def parse_arguments() -> argparse.Namespace:
    """Parses one mutually exclusive live or archive validation mode."""
    parser = argparse.ArgumentParser(
        description="Validate Forge 1.20.1 ethereal-channel E2E evidence."
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--scenario")
    mode.add_argument("--archive", type=Path)
    mode.add_argument("--create-archive-manifest", type=Path)
    parser.add_argument("--current-production", type=Path)
    parser.add_argument("--current-harness", type=Path)
    parser.add_argument("--current-profile", type=Path)
    return parser.parse_args()


def main() -> int:
    """Runs the selected fail-closed evidence operation."""
    arguments = parse_arguments()
    try:
        current_inputs = (
            arguments.current_production,
            arguments.current_harness,
            arguments.current_profile,
        )
        current_paths_supplied = any(path is not None for path in current_inputs)
        if current_paths_supplied and not all(
            path is not None for path in current_inputs
        ):
            raise forge_client.E2EError(
                "Current profile, production, and harness must be supplied together"
            )
        if current_paths_supplied and arguments.archive is None:
            raise forge_client.E2EError(
                "Current artifact comparison requires --archive"
            )
        if arguments.create_archive_manifest is not None:
            configuration = forge_client.load_configuration()
            path = write_archive_manifest(
                configuration,
                arguments.create_archive_manifest,
            )
            print(f"Created Forge channel archive manifest: {path}")
            print(
                "Archive manifest records capture-time provenance only; current "
                "sources and rebuilt artifacts were not compared."
            )
            return 0
        if arguments.archive is not None:
            summary = validate_archived_scenario(
                arguments.archive,
                arguments.current_production,
                arguments.current_harness,
                arguments.current_profile,
            )
            print(
                f"Validated archived {SCENARIO_ID} for {summary.profile_id}: "
                f"{summary.assertion_count} assertions, "
                f"{summary.screenshot_count} screenshots, "
                f"transfer-change {summary.transfer_changed_pixel_ratio:.6f}, "
                "reopened-structure "
                f"{summary.reopened_structural_change_ratio:.6f}"
            )
            print(f"Production SHA-256: {summary.production_sha256}")
            print(f"Harness SHA-256: {summary.harness_sha256}")
            if current_paths_supplied:
                print(
                    "Current isolated profile plus remapped production and harness "
                    "artifacts match the archive SHA-256 and size records exactly; "
                    "current sources were not compared."
                )
            else:
                print(
                    "Archive integrity only: current sources and rebuilt artifacts "
                    "were not compared."
                )
            return 0
        configuration = forge_client.load_configuration()
        summary = validate_scenario(configuration, arguments.scenario)
        print(
            f"Validated live {SCENARIO_ID}: {summary.assertion_count} assertions, "
            f"{summary.screenshot_count} screenshots, "
            f"transfer-change {summary.transfer_changed_pixel_ratio:.6f}, "
            "reopened-structure "
            f"{summary.reopened_structural_change_ratio:.6f}"
        )
        print(f"Production SHA-256: {summary.production_sha256}")
        print(f"Harness SHA-256: {summary.harness_sha256}")
        return 0
    except forge_client.E2EError as exception:
        print(f"Forge channel evidence validation failed: {exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
