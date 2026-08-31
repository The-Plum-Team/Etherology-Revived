#!/usr/bin/env python3
"""Validate live or archived evidence from the Forge ethereal-storage scenario."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zlib


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_client


SCENARIO_ID = "ethereal-storage"
MAXIMUM_SCENARIO_EVIDENCE_SIZE = 64 * 1024 * 1024
MAXIMUM_DECODED_PNG_SIZE = 32 * 1024 * 1024
MINIMUM_OPEN_CHANGED_PIXEL_RATIO = 0.1
MAXIMUM_CLOSED_RETURN_CHANGED_PIXEL_RATIO = 0.02
STORAGE_FIXTURE_REGION_PERCENT = (45, 36, 55, 62)
EXPECTED_FRAMEBUFFER_DIMENSIONS = (1920, 1080)
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SCREENSHOTS = {
    "closed": "ethereal-storage-closed.png",
    "open": "ethereal-storage-open.png",
    "closed_again": "ethereal-storage-closed-again.png",
    "menu": "ethereal-storage-menu.png",
    "reopened": "ethereal-storage-reopened.png",
}
REQUIRED_ASSERTIONS = {
    "lifecycle",
    "forge_loaded",
    "etherology_loaded",
    "chunk_loaded",
    "storage_nbt_reconstructed",
    "storage_block_entity_id",
    "inventory_size_four",
    "item_handler_all_sides",
    "item_handler_glint_valid",
    "item_handler_simulated_insert",
    "item_handler_live_insert",
    "item_handler_extraction_blocked",
    "item_handler_display_hidden",
    "glint_transfer_preserves_total",
    "glint_transfer_moved_ether",
    "viewer_open_invoked",
    "viewer_close_invoked",
    "menu_opened",
    "menu_client_type",
    "forced_save",
    "saved_snapshot",
    "restart_ether_distribution",
    "restart_input_inventory",
    "restart_block_entity_type",
    "menu_reopened",
    "reopened_menu_client_type",
    "display_slot_tracks_internal_only",
    "closed_screenshot",
    "open_screenshot",
    "closed_again_screenshot",
    "menu_screenshot",
    "reopened_screenshot",
    "artifact_etherology",
    "artifact_etherology_e2e_harness",
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
REPORT_FIELDS = {
    "schema",
    "scenario",
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
    "Forge ethereal-storage lifecycle failure:",
)


@dataclass(frozen=True)
class PngImage:
    """Stores decoded RGB pixels for deterministic visual comparisons."""

    width: int
    height: int
    pixels: bytes


@dataclass(frozen=True)
class EvidenceSummary:
    """Reports the verified Forge scenario inventory and visual deltas."""

    assertion_count: int
    screenshot_count: int
    open_changed_pixel_ratio: float
    closed_return_changed_pixel_ratio: float
    production_sha256: str
    harness_sha256: str


@dataclass(frozen=True)
class ArchiveEvidenceSummary:
    """Reports integrity established solely from one evidence archive."""

    profile_id: str
    assertion_count: int
    screenshot_count: int
    open_changed_pixel_ratio: float
    closed_return_changed_pixel_ratio: float
    production_sha256: str
    harness_sha256: str


@dataclass(frozen=True)
class ValidatedArchiveManifest:
    """Holds validated provenance and payload records from an archive manifest."""

    profile_id: str
    runtime: dict[str, object]
    artifacts: dict[str, dict[str, object]]
    files: dict[str, dict[str, object]]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_json_object(path: Path, description: str) -> dict[str, object]:
    if not path.is_file() or path.is_symlink():
        raise forge_client.E2EError(f"{description} is missing or linked: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise forge_client.E2EError(
            f"Cannot read {description} {path}: {exception}"
        ) from exception
    if not isinstance(value, dict):
        raise forge_client.E2EError(f"The {description} is not a JSON object: {path}")
    return value


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
    try:
        content = path.read_bytes()
    except OSError as exception:
        raise forge_client.E2EError(f"Cannot read evidence PNG {path}: {exception}") from exception
    if not content.startswith(PNG_SIGNATURE):
        raise forge_client.E2EError(f"Evidence file is not a PNG: {path}")

    offset = len(PNG_SIGNATURE)
    header: tuple[int, int, int, int, int, int, int] | None = None
    compressed = bytearray()
    saw_end = False
    while offset < len(content):
        if offset + 12 > len(content):
            raise forge_client.E2EError(f"Evidence PNG has a truncated chunk: {path}")
        chunk_size = struct.unpack(">I", content[offset : offset + 4])[0]
        chunk_type = content[offset + 4 : offset + 8]
        data_start = offset + 8
        data_end = data_start + chunk_size
        crc_end = data_end + 4
        if crc_end > len(content):
            raise forge_client.E2EError(f"Evidence PNG has a truncated payload: {path}")
        chunk_data = content[data_start:data_end]
        expected_crc = struct.unpack(">I", content[data_end:crc_end])[0]
        actual_crc = zlib.crc32(chunk_data, zlib.crc32(chunk_type)) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            raise forge_client.E2EError(f"Evidence PNG has a bad chunk CRC: {path}")
        if chunk_type == b"IHDR":
            if header is not None or len(chunk_data) != 13:
                raise forge_client.E2EError(f"Evidence PNG has an invalid header: {path}")
            header = struct.unpack(">IIBBBBB", chunk_data)
        elif chunk_type == b"IDAT":
            compressed.extend(chunk_data)
        elif chunk_type == b"IEND":
            saw_end = True
            offset = crc_end
            break
        offset = crc_end

    if header is None or not compressed or not saw_end or offset != len(content):
        raise forge_client.E2EError(f"Evidence PNG has an incomplete chunk inventory: {path}")
    width, height, bit_depth, color_type, compression, filtering, interlace = header
    channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(color_type)
    if (
        width <= 0
        or height <= 0
        or bit_depth != 8
        or channels is None
        or compression != 0
        or filtering != 0
        or interlace != 0
    ):
        raise forge_client.E2EError(
            f"Evidence PNG uses an unsupported encoding: {path}"
        )
    stride = width * channels
    expected_raw_size = height * (stride + 1)
    expected_rgb_size = width * height * 3
    if (
        expected_raw_size > MAXIMUM_DECODED_PNG_SIZE
        or expected_rgb_size > MAXIMUM_DECODED_PNG_SIZE
    ):
        raise forge_client.E2EError(
            f"Evidence PNG exceeds the decoded size bound: {path}"
        )
    try:
        decompressor = zlib.decompressobj()
        raw = decompressor.decompress(bytes(compressed), expected_raw_size + 1)
    except zlib.error as exception:
        raise forge_client.E2EError(
            f"Evidence PNG payload cannot be decompressed: {path}"
        ) from exception
    if (
        len(raw) != expected_raw_size
        or not decompressor.eof
        or decompressor.unconsumed_tail
        or decompressor.unused_data
    ):
        raise forge_client.E2EError(f"Evidence PNG has an unexpected decoded size: {path}")

    previous = bytearray(stride)
    rgb = bytearray(expected_rgb_size)
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
                raise forge_client.E2EError(
                    f"Evidence PNG uses unknown row filter {filter_type}: {path}"
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


def assert_image_is_not_blank(image: PngImage, description: str) -> None:
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
    raise forge_client.E2EError(
        f"Evidence screenshot is blank or near-uniform: {description}"
    )


def changed_pixel_ratio(left: PngImage, right: PngImage) -> float:
    if (left.width, left.height) != (right.width, right.height):
        raise forge_client.E2EError(
            "Cannot compare Forge evidence screenshots with different dimensions"
        )
    changed = 0
    for offset in range(0, len(left.pixels), 3):
        if any(
            abs(left.pixels[offset + channel] - right.pixels[offset + channel]) >= 8
            for channel in range(3)
        ):
            changed += 1
    return changed / (left.width * left.height)


def storage_fixture_changed_pixel_ratio(left: PngImage, right: PngImage) -> float:
    if (left.width, left.height) != (right.width, right.height):
        raise forge_client.E2EError(
            "Cannot compare Forge evidence screenshots with different dimensions"
        )
    left_percent, top_percent, right_percent, bottom_percent = (
        STORAGE_FIXTURE_REGION_PERCENT
    )
    left_x = left.width * left_percent // 100
    top_y = left.height * top_percent // 100
    right_x = left.width * right_percent // 100
    bottom_y = left.height * bottom_percent // 100
    if right_x <= left_x or bottom_y <= top_y:
        raise forge_client.E2EError(
            "Forge evidence screenshot is too small for the storage fixture region"
        )

    changed = 0
    for y in range(top_y, bottom_y):
        for x in range(left_x, right_x):
            offset = (y * left.width + x) * 3
            if any(
                abs(left.pixels[offset + channel] - right.pixels[offset + channel]) >= 8
                for channel in range(3)
            ):
                changed += 1
    return changed / ((right_x - left_x) * (bottom_y - top_y))


def validate_report_contract(
    expected_artifact_node: str,
    report: dict[str, object],
) -> dict[str, str]:
    if set(report) != REPORT_FIELDS:
        raise forge_client.E2EError("Forge E2E report field inventory changed")
    if (
        report.get("schema") != 1
        or report.get("scenario") != SCENARIO_ID
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
            "Forge E2E report did not record the exact passing 1.20.1 lifecycle"
        )

    assertions = report.get("assertions")
    if not isinstance(assertions, list):
        raise forge_client.E2EError("Forge E2E report has no assertion inventory")
    actual_by_name: dict[str, str] = {}
    for assertion in assertions:
        if not isinstance(assertion, dict) or set(assertion) != {"name", "passed", "actual"}:
            raise forge_client.E2EError("Forge E2E report contains an invalid assertion")
        name = assertion.get("name")
        actual = assertion.get("actual")
        if not isinstance(name, str) or name in actual_by_name or not isinstance(actual, str):
            raise forge_client.E2EError(
                "Forge E2E report contains a duplicated or malformed assertion"
            )
        if assertion.get("passed") is not True:
            raise forge_client.E2EError(f"Forge E2E assertion did not pass: {name}")
        actual_by_name[name] = actual
    if set(actual_by_name) != REQUIRED_ASSERTIONS:
        raise forge_client.E2EError(
            "Forge E2E assertion inventory changed: "
            f"missing={sorted(REQUIRED_ASSERTIONS - set(actual_by_name))}, "
            f"unexpected={sorted(set(actual_by_name) - REQUIRED_ASSERTIONS)}"
        )
    if actual_by_name["forced_save"] != "true" or actual_by_name["lifecycle"] != "":
        raise forge_client.E2EError("Forge E2E save or lifecycle evidence is inconsistent")
    for name in (
        "saved_snapshot",
        "restart_ether_distribution",
        "restart_input_inventory",
        "restart_block_entity_type",
    ):
        if not actual_by_name[name] or actual_by_name[name] == "null":
            raise forge_client.E2EError(f"Forge E2E assertion has no restart evidence: {name}")
    return actual_by_name


def validate_report(
    configuration: forge_client.ResolvedConfiguration,
    report: dict[str, object],
) -> dict[str, str]:
    return validate_report_contract(
        str(configuration.artifact_lane["artifact_node"]),
        report,
    )


def report_artifacts_by_mod_id(
    report: dict[str, object],
) -> dict[str, dict[str, object]]:
    reported = report.get("artifacts")
    if not isinstance(reported, list):
        raise forge_client.E2EError("Forge artifact provenance is malformed")
    by_mod_id: dict[str, dict[str, object]] = {}
    for artifact in reported:
        if not isinstance(artifact, dict) or set(artifact) != {
            "mod_id",
            "passed",
            "file_name",
            "size",
            "sha256",
            "failure",
        }:
            raise forge_client.E2EError("Forge report contains malformed artifact provenance")
        mod_id = artifact.get("mod_id")
        if not isinstance(mod_id, str) or mod_id in by_mod_id:
            raise forge_client.E2EError("Forge report artifact ids are invalid or duplicated")
        by_mod_id[mod_id] = artifact
    return by_mod_id


def validate_report_artifacts(
    report: dict[str, object],
    assertions: dict[str, str],
    expected_by_role: dict[str, dict[str, object]],
) -> tuple[str, str]:
    if set(expected_by_role) != set(forge_client.ARTIFACT_ROLES):
        raise forge_client.E2EError("Forge expected artifact role inventory changed")
    by_mod_id = report_artifacts_by_mod_id(report)

    digests: dict[str, str] = {}
    for role in forge_client.ARTIFACT_ROLES:
        expected = expected_by_role[role]
        if set(expected) != {"mod_id", "file_name", "size", "sha256"}:
            raise forge_client.E2EError(
                f"Forge expected {role} artifact provenance is malformed"
            )
        mod_id = expected.get("mod_id")
        if not isinstance(mod_id, str) or not mod_id:
            raise forge_client.E2EError(f"Forge expected {role} mod id is malformed")
        artifact = by_mod_id.get(str(mod_id))
        if artifact is None or artifact.get("passed") is not True or artifact.get("failure") != "":
            raise forge_client.E2EError(f"Forge report has no passing {role} artifact")
        for field_name in ("file_name", "size", "sha256"):
            if artifact.get(field_name) != expected.get(field_name):
                raise forge_client.E2EError(
                    f"Forge report {role} {field_name} differs from its provenance"
                )
        if type(expected.get("size")) is not int or int(expected["size"]) <= 0:
            raise forge_client.E2EError(f"Forge expected {role} artifact size is invalid")
        digest = forge_client.validate_hex_digest(
            expected.get("sha256"), f"{role} evidence provenance"
        )
        expected_actual = (
            f"ForgeArtifactDigest[modId={mod_id}, passed=true, "
            f"fileName={artifact['file_name']}, size={artifact['size']}, "
            f"sha256={digest}, failure=]"
        )
        if assertions[f"artifact_{mod_id}"] != expected_actual:
            raise forge_client.E2EError(
                f"Forge {role} artifact assertion differs from its provenance"
            )
        digests[role] = digest
    if set(by_mod_id) != {
        str(expected_by_role[role]["mod_id"])
        for role in forge_client.ARTIFACT_ROLES
    }:
        raise forge_client.E2EError("Forge report artifact inventory changed")
    return digests["production"], digests["harness"]


def validate_artifacts(
    configuration: forge_client.ResolvedConfiguration,
    runtime: Path,
    report: dict[str, object],
    assertions: dict[str, str],
) -> tuple[str, str]:
    forge_client.verify_locked_artifacts(configuration, runtime, verify_source=False)
    lock = forge_client.load_artifact_lock(configuration, runtime)
    if lock is None or set(lock) != {
        "schema",
        "profile_id",
        "managed_by",
        "artifact_node",
        "artifacts",
    }:
        raise forge_client.E2EError("Forge evidence has no exact artifact lock")
    locked = lock.get("artifacts")
    if not isinstance(locked, dict):
        raise forge_client.E2EError("Forge artifact lock inventory is malformed")
    expected_by_role: dict[str, dict[str, object]] = {}
    for role in forge_client.ARTIFACT_ROLES:
        locked_artifact = locked.get(role)
        if not isinstance(locked_artifact, dict):
            raise forge_client.E2EError(f"Forge artifact lock has no {role} entry")
        expected_by_role[role] = {
            "mod_id": locked_artifact.get("mod_id"),
            "file_name": locked_artifact.get("target_file"),
            "size": locked_artifact.get("size"),
            "sha256": locked_artifact.get("sha256"),
        }
    return validate_report_artifacts(report, assertions, expected_by_role)


def validate_screenshots_contract(
    expected_dimensions: tuple[int, int],
    scenario_root: Path,
    assertions: dict[str, str],
) -> tuple[float, float, list[Path]]:
    screenshots_directory = scenario_root / "screenshots"
    entries = list(screenshots_directory.iterdir())
    if any(not path.is_file() or path.is_symlink() for path in entries):
        raise forge_client.E2EError(
            "Forge screenshot directory contains a linked or non-file entry"
        )
    if {path.name for path in entries} != set(SCREENSHOTS.values()):
        raise forge_client.E2EError("Forge screenshot inventory changed")
    images: dict[str, PngImage] = {}
    paths: list[Path] = []
    for role, file_name in SCREENSHOTS.items():
        path = screenshots_directory / file_name
        image = decode_png(path)
        if (image.width, image.height) != expected_dimensions:
            raise forge_client.E2EError(
                f"Forge evidence screenshot has the wrong dimensions: {path}"
            )
        assert_image_is_not_blank(image, str(path))
        digest = sha256_file(path)
        expected_actual = (
            f"ScreenshotResult[passed=true, size={path.stat().st_size}, "
            f"sha256={digest}, failure=]"
        )
        if assertions[f"{role}_screenshot"] != expected_actual:
            raise forge_client.E2EError(
                f"Forge {role} screenshot assertion differs from the frozen PNG"
            )
        images[role] = image
        paths.append(path)

    open_ratio = storage_fixture_changed_pixel_ratio(images["closed"], images["open"])
    if open_ratio < MINIMUM_OPEN_CHANGED_PIXEL_RATIO:
        raise forge_client.E2EError(
            "Forge closed-to-open visual change is below threshold: "
            f"{open_ratio:.6f}"
        )
    closed_return_ratio = storage_fixture_changed_pixel_ratio(
        images["closed"], images["closed_again"]
    )
    if closed_return_ratio > MAXIMUM_CLOSED_RETURN_CHANGED_PIXEL_RATIO:
        raise forge_client.E2EError(
            "Forge closed-again frame did not return near the closed baseline: "
            f"{closed_return_ratio:.6f}"
        )
    return open_ratio, closed_return_ratio, paths


def validate_screenshots(
    configuration: forge_client.ResolvedConfiguration,
    scenario_root: Path,
    assertions: dict[str, str],
) -> tuple[float, float, list[Path]]:
    capture = forge_client.require_object(
        forge_client.require_object(configuration.manifest, "evidence"), "capture"
    )
    expected_dimensions = (int(capture["width"]), int(capture["height"]))
    return validate_screenshots_contract(
        expected_dimensions,
        scenario_root,
        assertions,
    )


def archive_profile_version(profile_id: str) -> str:
    match = re.fullmatch(
        r"etherology-e2e-forge-1\.20\.1-v([1-9][0-9]*)",
        profile_id,
    )
    if match is None:
        raise forge_client.E2EError("Forge archive profile id is invalid")
    return match.group(1)


def validate_archive_root_identity(archive_root: Path, profile_id: str) -> None:
    expected_name = f"{SCENARIO_ID}-v{archive_profile_version(profile_id)}"
    if archive_root.name != expected_name:
        raise forge_client.E2EError(
            "Forge archive directory does not identify its recorded profile: "
            f"expected {expected_name}, found {archive_root.name}"
        )


def validate_archive_directory_inventory(
    archive_root: Path,
    include_manifest: bool,
) -> None:
    if not archive_root.is_dir() or archive_root.is_symlink():
        raise forge_client.E2EError(
            f"Forge evidence archive is missing or linked: {archive_root}"
        )
    files: set[str] = set()
    directories: set[str] = set()
    for path in archive_root.rglob("*"):
        relative_path = path.relative_to(archive_root).as_posix()
        if path.is_symlink():
            raise forge_client.E2EError(
                f"Forge evidence archive contains a linked entry: {relative_path}"
            )
        if path.is_file():
            files.add(relative_path)
        elif path.is_dir():
            directories.add(relative_path)
        else:
            raise forge_client.E2EError(
                f"Forge evidence archive contains a special entry: {relative_path}"
            )
    expected_files = set(ARCHIVE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    if files != expected_files or directories != ARCHIVE_DIRECTORIES:
        raise forge_client.E2EError(
            "Forge evidence archive inventory changed: "
            f"missing_files={sorted(expected_files - files)}, "
            f"unexpected_files={sorted(files - expected_files)}, "
            f"missing_directories={sorted(ARCHIVE_DIRECTORIES - directories)}, "
            f"unexpected_directories={sorted(directories - ARCHIVE_DIRECTORIES)}"
        )


def validate_archive_size(archive_root: Path) -> None:
    archive_size = sum(
        path.stat().st_size
        for path in archive_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if archive_size > MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise forge_client.E2EError("Forge evidence archive exceeds the size bound")


def archive_artifacts_from_report(
    report: dict[str, object],
    assertions: dict[str, str],
) -> dict[str, dict[str, object]]:
    by_mod_id = report_artifacts_by_mod_id(report)
    expected_by_role: dict[str, dict[str, object]] = {}
    for role, (mod_id, file_name) in ARCHIVE_ARTIFACT_IDENTITIES.items():
        artifact = by_mod_id.get(mod_id)
        if artifact is None or artifact.get("file_name") != file_name:
            raise forge_client.E2EError(
                f"Forge archive report has the wrong {role} artifact identity"
            )
        expected_by_role[role] = {
            "mod_id": mod_id,
            "file_name": file_name,
            "size": artifact.get("size"),
            "sha256": artifact.get("sha256"),
        }
    validate_report_artifacts(report, assertions, expected_by_role)
    return expected_by_role


def archive_file_records(archive_root: Path) -> dict[str, dict[str, object]]:
    return {
        relative_path: {
            "size": (archive_root / relative_path).stat().st_size,
            "sha256": sha256_file(archive_root / relative_path),
        }
        for relative_path in ARCHIVE_PAYLOAD_PATHS
    }


def build_archive_manifest(
    configuration: forge_client.ResolvedConfiguration,
    archive_root: Path,
) -> dict[str, object]:
    validate_archive_directory_inventory(archive_root, include_manifest=False)
    validate_archive_size(archive_root)
    profile = forge_client.profile_spec(configuration)
    profile_id = str(profile["id"])
    validate_archive_root_identity(archive_root, profile_id)
    for role, (mod_id, file_name) in ARCHIVE_ARTIFACT_IDENTITIES.items():
        artifact = forge_client.artifact_spec(configuration, role)
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            raise forge_client.E2EError(
                f"The active Forge profile has the wrong {role} archive identity"
            )

    report = require_json_object(
        archive_root / "reports" / "report.json",
        "Forge archived scenario report",
    )
    assertions = validate_report(configuration, report)
    artifacts = archive_artifacts_from_report(report, assertions)
    _open_ratio, _closed_return_ratio, screenshot_paths = validate_screenshots(
        configuration,
        archive_root,
        assertions,
    )
    if len(screenshot_paths) != len(SCREENSHOTS):
        raise forge_client.E2EError("Forge archive screenshot count changed")
    done_path = archive_root / "reports" / "done.marker"
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError(
            "Forge archived completion marker has unexpected content"
        )
    profile_manifest_path = (
        configuration.repository_root / ARCHIVED_PROFILE_MANIFEST_PATH
    )
    if not profile_manifest_path.is_file() or profile_manifest_path.is_symlink():
        raise forge_client.E2EError(
            f"Tracked Forge profile manifest is missing or linked: {profile_manifest_path}"
        )
    return {
        "schema": 1,
        "kind": ARCHIVE_KIND,
        "verification_scope": ARCHIVE_VERIFICATION_SCOPE,
        "scenario": SCENARIO_ID,
        "profile": {
            "id": profile_id,
            "manifest_path": ARCHIVED_PROFILE_MANIFEST_PATH,
            "manifest_sha256": sha256_file(profile_manifest_path),
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
    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if manifest_path.exists() or manifest_path.is_symlink():
        raise forge_client.E2EError(
            f"Forge archive manifest already exists: {manifest_path}"
        )
    manifest = build_archive_manifest(configuration, archive_root)
    try:
        with manifest_path.open("x", encoding="utf-8", newline="\n") as handle:
            json.dump(manifest, handle, indent=2)
            handle.write("\n")
    except FileExistsError as exception:
        raise forge_client.E2EError(
            f"Forge archive manifest already exists: {manifest_path}"
        ) from exception
    return manifest_path


def validate_archive_manifest_shape(
    archive_root: Path,
    manifest: dict[str, object],
) -> ValidatedArchiveManifest:
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
        raise forge_client.E2EError("Forge archive manifest field inventory changed")
    if (
        manifest.get("schema") != 1
        or manifest.get("kind") != ARCHIVE_KIND
        or manifest.get("verification_scope") != ARCHIVE_VERIFICATION_SCOPE
        or manifest.get("scenario") != SCENARIO_ID
        or manifest.get("assertion_count") != len(REQUIRED_ASSERTIONS)
        or manifest.get("screenshot_count") != len(SCREENSHOTS)
    ):
        raise forge_client.E2EError("Forge archive manifest identity is invalid")

    profile = manifest.get("profile")
    if not isinstance(profile, dict) or set(profile) != {
        "id",
        "manifest_path",
        "manifest_sha256",
    }:
        raise forge_client.E2EError("Forge archive profile provenance is malformed")
    profile_id = profile.get("id")
    if not isinstance(profile_id, str):
        raise forge_client.E2EError("Forge archive profile id is malformed")
    validate_archive_root_identity(archive_root, profile_id)
    if profile.get("manifest_path") != ARCHIVED_PROFILE_MANIFEST_PATH:
        raise forge_client.E2EError("Forge archive profile path is invalid")
    forge_client.validate_hex_digest(
        profile.get("manifest_sha256"),
        "archived Forge profile manifest",
    )

    runtime = manifest.get("runtime")
    if not isinstance(runtime, dict) or runtime != {
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "framebuffer_width": EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
        "framebuffer_height": EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
    }:
        raise forge_client.E2EError("Forge archive runtime identity is invalid")

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or set(artifacts) != set(
        ARCHIVE_ARTIFACT_IDENTITIES
    ):
        raise forge_client.E2EError("Forge archive artifact inventory changed")
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
                f"Forge archive {role} artifact provenance is malformed"
            )
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            raise forge_client.E2EError(
                f"Forge archive {role} artifact identity is invalid"
            )
        if type(artifact.get("size")) is not int or int(artifact["size"]) <= 0:
            raise forge_client.E2EError(
                f"Forge archive {role} artifact size is invalid"
            )
        forge_client.validate_hex_digest(
            artifact.get("sha256"),
            f"archived Forge {role} artifact",
        )
        expected_by_role[role] = artifact

    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(ARCHIVE_PAYLOAD_PATHS):
        raise forge_client.E2EError("Forge archive payload inventory changed")
    expected_files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if not isinstance(record, dict) or set(record) != {"size", "sha256"}:
            raise forge_client.E2EError(
                f"Forge archive payload record is malformed: {relative_path}"
            )
        if type(record.get("size")) is not int or int(record["size"]) <= 0:
            raise forge_client.E2EError(
                f"Forge archive payload size is invalid: {relative_path}"
            )
        forge_client.validate_hex_digest(
            record.get("sha256"),
            f"archived Forge payload {relative_path}",
        )
        expected_files[relative_path] = record
    return ValidatedArchiveManifest(
        profile_id=profile_id,
        runtime=runtime,
        artifacts=expected_by_role,
        files=expected_files,
    )


def validate_archived_scenario(archive_root: Path) -> ArchiveEvidenceSummary:
    validate_archive_directory_inventory(archive_root, include_manifest=True)
    validate_archive_size(archive_root)
    manifest = require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME,
        "Forge evidence archive manifest",
    )
    validated_manifest = validate_archive_manifest_shape(archive_root, manifest)
    for relative_path, record in validated_manifest.files.items():
        path = archive_root / relative_path
        if path.stat().st_size != record["size"] or sha256_file(path) != record["sha256"]:
            raise forge_client.E2EError(
                f"Forge archive payload differs from its manifest: {relative_path}"
            )

    done_path = archive_root / "reports" / "done.marker"
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError(
            "Forge archived completion marker has unexpected content"
        )
    report = require_json_object(
        archive_root / "reports" / "report.json",
        "Forge archived scenario report",
    )
    assertions = validate_report_contract(
        str(validated_manifest.runtime["artifact_node"]),
        report,
    )
    if (
        len(assertions) != manifest["assertion_count"]
        or report.get("framebuffer_width")
        != validated_manifest.runtime["framebuffer_width"]
        or report.get("framebuffer_height")
        != validated_manifest.runtime["framebuffer_height"]
    ):
        raise forge_client.E2EError(
            "Forge archive report differs from its manifest contract"
        )
    production_digest, harness_digest = validate_report_artifacts(
        report,
        assertions,
        validated_manifest.artifacts,
    )
    open_ratio, closed_return_ratio, screenshot_paths = validate_screenshots_contract(
        (
            int(validated_manifest.runtime["framebuffer_width"]),
            int(validated_manifest.runtime["framebuffer_height"]),
        ),
        archive_root,
        assertions,
    )
    if len(screenshot_paths) != manifest["screenshot_count"]:
        raise forge_client.E2EError(
            "Forge archive screenshot count differs from its manifest"
        )
    return ArchiveEvidenceSummary(
        profile_id=validated_manifest.profile_id,
        assertion_count=len(assertions),
        screenshot_count=len(screenshot_paths),
        open_changed_pixel_ratio=open_ratio,
        closed_return_changed_pixel_ratio=closed_return_ratio,
        production_sha256=production_digest,
        harness_sha256=harness_digest,
    )


def validate_game_lifecycle(
    configuration: forge_client.ResolvedConfiguration,
    runtime: Path,
    report: dict[str, object],
) -> None:
    game = forge_client.game_directory(configuration, runtime)
    crash_reports = game / "crash-reports"
    if not crash_reports.is_dir() or crash_reports.is_symlink():
        raise forge_client.E2EError("Forge crash-report directory is missing or linked")
    if any(crash_reports.iterdir()):
        raise forge_client.E2EError(f"Forge E2E runtime contains a crash report: {crash_reports}")
    latest_log = game / "logs" / "latest.log"
    if not latest_log.is_file() or latest_log.is_symlink():
        raise forge_client.E2EError(f"Forge E2E game log is missing or linked: {latest_log}")
    if latest_log.stat().st_size > forge_client.MAXIMUM_PROCESS_LOG_SIZE:
        raise forge_client.E2EError("Forge E2E game log exceeds the size bound")
    log_content = latest_log.read_text(encoding="utf-8", errors="replace")
    fatal_marker = next(
        (marker for marker in FATAL_GAME_LOG_MARKERS if marker in log_content), None
    )
    if fatal_marker is not None:
        raise forge_client.E2EError(
            f"Forge E2E game log contains fatal marker: {fatal_marker}"
        )
    if "Stopping!" not in log_content:
        raise forge_client.E2EError("Forge E2E client did not record a normal shutdown")
    if "Forge ethereal-storage evidence complete:" not in log_content:
        raise forge_client.E2EError("Forge E2E game log has no evidence-complete marker")
    save = game / "saves" / "etherology-e2e-forge-storage-world"
    if not save.is_dir() or save.is_symlink():
        raise forge_client.E2EError(f"Forge E2E world save is missing or linked: {save}")
    if report.get("passed") is not True:
        raise forge_client.E2EError("Forge E2E lifecycle did not pass")


def validate_scenario(
    configuration: forge_client.ResolvedConfiguration,
    configured_scenario_id: str | None,
    runtime: Path | None = None,
) -> EvidenceSummary:
    scenario_id = forge_client.resolve_scenario_id(configuration, configured_scenario_id)
    if scenario_id != SCENARIO_ID:
        raise forge_client.E2EError(f"Unsupported Forge evidence scenario: {scenario_id}")
    target_runtime = runtime or forge_client.runtime_root(configuration)
    forge_client.verify_evidence_layout(configuration, target_runtime)
    scenario_root = forge_client.evidence_root(configuration, target_runtime) / scenario_id
    reports_directory = scenario_root / "reports"
    report_path = reports_directory / "report.json"
    done_path = reports_directory / "done.marker"
    if {path.name for path in reports_directory.iterdir()} != {"report.json", "done.marker"}:
        raise forge_client.E2EError("Forge E2E report inventory changed")
    if report_path.is_symlink() or done_path.is_symlink():
        raise forge_client.E2EError("Forge E2E report or completion marker is linked")
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError("Forge E2E completion marker has unexpected content")

    report = require_json_object(report_path, "Forge E2E scenario report")
    assertions = validate_report(configuration, report)
    production_digest, harness_digest = validate_artifacts(
        configuration, target_runtime, report, assertions
    )
    open_ratio, closed_return_ratio, screenshot_paths = validate_screenshots(
        configuration, scenario_root, assertions
    )
    evidence_files = [report_path, *screenshot_paths]
    if any(done_path.stat().st_mtime_ns < path.stat().st_mtime_ns for path in evidence_files):
        raise forge_client.E2EError(
            "Forge E2E completion marker predates report or screenshot evidence"
        )
    scenario_size = sum(
        path.stat().st_size
        for path in scenario_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if scenario_size > MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise forge_client.E2EError("Forge E2E evidence exceeds the size bound")
    validate_game_lifecycle(configuration, target_runtime, report)
    return EvidenceSummary(
        assertion_count=len(assertions),
        screenshot_count=len(screenshot_paths),
        open_changed_pixel_ratio=open_ratio,
        closed_return_changed_pixel_ratio=closed_return_ratio,
        production_sha256=production_digest,
        harness_sha256=harness_digest,
    )


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Validate live or repository-archived Forge 1.20.1 packaged E2E evidence."
        )
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--scenario")
    mode.add_argument("--archive", type=Path)
    mode.add_argument("--create-archive-manifest", type=Path)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        if arguments.create_archive_manifest is not None:
            configuration = forge_client.load_configuration()
            manifest_path = write_archive_manifest(
                configuration,
                arguments.create_archive_manifest,
            )
            print(f"Created Forge evidence archive manifest: {manifest_path}")
            print(
                "Archive manifest records capture-time provenance only; current "
                "sources and rebuilt artifacts were not compared."
            )
            return 0
        if arguments.archive is not None:
            archived_summary = validate_archived_scenario(arguments.archive)
            print(
                f"Validated archived {SCENARIO_ID} for "
                f"{archived_summary.profile_id}: "
                f"{archived_summary.assertion_count} assertions, "
                f"{archived_summary.screenshot_count} screenshots, "
                "closed-to-open "
                f"{archived_summary.open_changed_pixel_ratio:.6f}, "
                "closed-return "
                f"{archived_summary.closed_return_changed_pixel_ratio:.6f}"
            )
            print(f"Production SHA-256: {archived_summary.production_sha256}")
            print(f"Harness SHA-256: {archived_summary.harness_sha256}")
            print(
                "Archive integrity only: current sources and rebuilt artifacts "
                "were not compared."
            )
            return 0
        configuration = forge_client.load_configuration()
        summary = validate_scenario(configuration, arguments.scenario)
    except (forge_client.E2EError, OSError) as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2
    print(
        f"Validated {SCENARIO_ID}: {summary.assertion_count} assertions, "
        f"{summary.screenshot_count} screenshots, "
        f"closed-to-open {summary.open_changed_pixel_ratio:.6f}, "
        f"closed-return {summary.closed_return_changed_pixel_ratio:.6f}"
    )
    print(f"Production SHA-256: {summary.production_sha256}")
    print(f"Harness SHA-256: {summary.harness_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
