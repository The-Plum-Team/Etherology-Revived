#!/usr/bin/env python3
"""Validate frozen evidence from one repository-owned Etherology E2E scenario."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import struct
import sys
import zlib


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import client


MAXIMUM_SCENARIO_EVIDENCE_SIZE = 64 * 1024 * 1024
MAXIMUM_DECODED_PNG_SIZE = 64 * 1024 * 1024
MAXIMUM_PNG_DIMENSION = 8192
MINIMUM_CHANGED_PIXEL_RATIO = 0.005
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PHASE_ZERO_SCENARIO_ID = "phase0-smoke"
ARCHIVE_MANIFEST_NAME = "archive-manifest.json"
ARCHIVE_KIND = "etherology-fabric-e2e-evidence"
ARCHIVE_VERIFICATION_SCOPE = (
    "archive-integrity-only-current-sources-and-built-artifacts-not-compared"
)
ARCHIVED_PROFILE_MANIFEST_PATH = "scripts/e2e/fabric-1.20.1-profile.json"
ARCHIVE_DIRECTORIES = {"reports", "screenshots"}
ARCHIVE_PAYLOAD_PATHS = (
    "reports/report.json",
    "reports/done.marker",
    "screenshots/phase0-smoke-title.png",
    "screenshots/phase0-smoke-world.png",
)
ARCHIVE_ARTIFACT_IDENTITIES = {
    "production": ("etherology", "etherology-under-test.jar"),
    "harness": ("etherology_e2e_harness", "etherology-e2e-harness.jar"),
}
EXPECTED_FRAMEBUFFER_DIMENSIONS = (1920, 1080)
EXPECTED_SCREENSHOTS = (
    (
        "resource-loaded-title-screen",
        "screenshots/phase0-smoke-title.png",
    ),
    (
        "integrated-world-fixture",
        "screenshots/phase0-smoke-world.png",
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
    "artifacts",
    "screenshots",
}
EXPECTED_WORLD = {
    "save_directory": "etherology-e2e-phase0-world",
    "display_name": "Etherology E2E Phase 0",
    "seed": 19514442770435916,
    "dimension": "minecraft:overworld",
    "integrated": True,
}
EXPECTED_READY_RESOURCES = [
    "minecraft:texts/splashes.txt",
    "etherology:models/item/oculus.json",
]
EXPECTED_ASSERTION_NAMES = (
    "fabric_mod_loaded:etherology",
    "registry:item:etherology:teldecore",
    "registry:item:etherology:oculus",
    "registry:item:etherology:staff",
    "registry:item:etherology:primoshard_keta",
    "registry:item:etherology:redstone_lens",
    "registry:block:etherology:brewing_cauldron",
    "registry:block:etherology:empowerment_table",
    "registry:block:etherology:armillary_sphere",
    "registry:block:etherology:ethereal_storage",
    "registry:block:etherology:levitator",
    "registry:item_group:etherology:etherology_items",
    "registry:block_entity_type:etherology:brewing_cauldron_block_entity",
    "registry:block_entity_type:etherology:armillary_sphere_block_entity",
    "registry:screen_handler:etherology:empower_table_screen_handler",
    "registry:screen_handler:etherology:ethereal_storage_screen_handler",
    "registry:recipe_serializer:etherology:alchemy_recipe",
    "registry:recipe_serializer:etherology:empower_recipe",
    "registry:recipe_serializer:etherology:matrix_recipe",
    "registry:entity_type:etherology:redstone_charge",
    "registry:status_effect:etherology:devastation",
    "registry:status_effect:etherology:vital_energy",
    "etherology_block_states_have_network_ids",
    "packaged_root_jar:etherology",
    "packaged_root_jar:etherology_e2e_harness",
    "title_framebuffer_dimensions",
    "world_framebuffer_dimensions",
    "completed_title_renders_before_capture",
    "completed_world_renders_before_capture",
    "native_screenshot_written:title",
    "native_screenshot_written:world",
    "integrated_world_joined",
    "client_world_mirrors_server_fixture",
    "server_arena_chunk_loaded",
    "server_player_creative",
    "server_fixture_blocks_placed",
    "server_fixture_block_entities_present",
    "block_entity_nbt_round_trip",
    "creative_inventory_population",
    "creative_inventory_expected_items",
    "forced_world_save",
    "isolated_save_directory_present",
)
ARCHIVE_PUBLICATION_ATTESTATION = {
    "completion_marker": "reports/done.marker",
    "verified_last_in_capture_runtime": True,
    "archive_payloads_match_capture_runtime": True,
}
ARCHIVE_CAPTURE_METADATA_PATH = "artifact-lock.json"
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
    "No value with id -1",
    "java.lang.OutOfMemoryError",
)


@dataclass(frozen=True)
class PngImage:
    """Stores decoded RGB pixels for deterministic visual probes."""

    width: int
    height: int
    pixels: bytes


@dataclass(frozen=True)
class EvidenceSummary:
    """Reports the verified scenario inventory."""

    scenario_id: str
    assertion_count: int
    screenshot_count: int
    changed_pixel_ratio: float | None
    production_sha256: str
    harness_sha256: str


@dataclass(frozen=True)
class ArchiveEvidenceSummary:
    """Reports one self-contained archived Phase 0 evidence inventory."""

    profile_id: str
    assertion_count: int
    screenshot_count: int
    changed_pixel_ratio: float
    production_sha256: str
    harness_sha256: str


@dataclass(frozen=True)
class ValidatedArchiveManifest:
    """Carries validated immutable provenance into archive payload checks."""

    profile_id: str
    runtime: dict[str, object]
    publication: dict[str, object]
    capture_metadata: dict[str, object]
    artifacts: dict[str, dict[str, object]]
    files: dict[str, dict[str, object]]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


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


def decode_png(
    path: Path,
    expected_dimensions: tuple[int, int] | None = None,
) -> PngImage:
    try:
        content = path.read_bytes()
    except OSError as exception:
        raise client.E2EError(f"Cannot read evidence PNG {path}: {exception}") from exception
    if not content.startswith(PNG_SIGNATURE):
        raise client.E2EError(f"Evidence file is not a PNG: {path}")

    offset = len(PNG_SIGNATURE)
    header: tuple[int, int, int, int, int, int, int] | None = None
    compressed = bytearray()
    saw_end = False
    while offset < len(content):
        if offset + 12 > len(content):
            raise client.E2EError(f"Evidence PNG has a truncated chunk: {path}")
        chunk_size = struct.unpack(">I", content[offset : offset + 4])[0]
        chunk_type = content[offset + 4 : offset + 8]
        data_start = offset + 8
        data_end = data_start + chunk_size
        crc_end = data_end + 4
        if crc_end > len(content):
            raise client.E2EError(f"Evidence PNG has a truncated payload: {path}")
        chunk_data = content[data_start:data_end]
        expected_crc = struct.unpack(">I", content[data_end:crc_end])[0]
        actual_crc = zlib.crc32(chunk_type)
        actual_crc = zlib.crc32(chunk_data, actual_crc) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            raise client.E2EError(f"Evidence PNG has a bad chunk CRC: {path}")
        if chunk_type == b"IHDR":
            if header is not None or len(chunk_data) != 13:
                raise client.E2EError(f"Evidence PNG has an invalid header: {path}")
            header = struct.unpack(">IIBBBBB", chunk_data)
        elif chunk_type == b"IDAT":
            compressed.extend(chunk_data)
        elif chunk_type == b"IEND":
            saw_end = True
            offset = crc_end
            break
        offset = crc_end

    if header is None or not compressed or not saw_end or offset != len(content):
        raise client.E2EError(f"Evidence PNG has an incomplete chunk inventory: {path}")
    width, height, bit_depth, color_type, compression, filtering, interlace = header
    if width <= 0 or height <= 0:
        raise client.E2EError(f"Evidence PNG has invalid dimensions: {path}")
    if width > MAXIMUM_PNG_DIMENSION or height > MAXIMUM_PNG_DIMENSION:
        raise client.E2EError(f"Evidence PNG exceeds the dimension bound: {path}")
    if expected_dimensions is not None and (width, height) != expected_dimensions:
        raise client.E2EError(f"Evidence PNG has the wrong dimensions: {path}")
    channels_by_color_type = {0: 1, 2: 3, 4: 2, 6: 4}
    channels = channels_by_color_type.get(color_type)
    if (
        bit_depth != 8
        or channels is None
        or compression != 0
        or filtering != 0
        or interlace != 0
    ):
        raise client.E2EError(
            f"Evidence PNG uses an unsupported encoding (8-bit non-interlaced required): {path}"
        )

    stride = width * channels
    expected_size = height * (stride + 1)
    if expected_size > MAXIMUM_DECODED_PNG_SIZE:
        raise client.E2EError(f"Evidence PNG exceeds the decoded size bound: {path}")
    try:
        decompressor = zlib.decompressobj()
        raw = decompressor.decompress(bytes(compressed), expected_size + 1)
        if len(raw) <= expected_size and not decompressor.unconsumed_tail:
            remaining_capacity = expected_size + 1 - len(raw)
            raw += decompressor.flush(remaining_capacity)
    except zlib.error as exception:
        raise client.E2EError(
            f"Evidence PNG payload cannot be decompressed: {path}"
        ) from exception
    if (
        len(raw) > expected_size
        or decompressor.unconsumed_tail
        or decompressor.unused_data
        or not decompressor.eof
    ):
        raise client.E2EError(
            f"Evidence PNG decompressed data exceeds its declared dimensions: {path}"
        )
    if len(raw) != expected_size:
        raise client.E2EError(f"Evidence PNG has an unexpected decoded size: {path}")

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
                raise client.E2EError(
                    f"Evidence PNG uses an unknown row filter {filter_type}: {path}"
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
    raise client.E2EError(f"Evidence screenshot is blank or near-uniform: {description}")


def changed_pixel_ratio(left: PngImage, right: PngImage) -> float:
    if (left.width, left.height) != (right.width, right.height):
        raise client.E2EError("Cannot compare evidence screenshots with different dimensions")
    changed = 0
    pixel_count = left.width * left.height
    for offset in range(0, len(left.pixels), 3):
        if any(
            abs(left.pixels[offset + channel] - right.pixels[offset + channel]) >= 8
            for channel in range(3)
        ):
            changed += 1
    return changed / pixel_count


def safe_screenshot_path(scenario_root: Path, raw_value: object) -> Path:
    if not isinstance(raw_value, str):
        raise client.E2EError("Evidence screenshot has no file path")
    relative = PurePosixPath(raw_value)
    if (
        relative.is_absolute()
        or ".." in relative.parts
        or len(relative.parts) != 2
        or relative.parts[0] != "screenshots"
        or not relative.name.endswith(".png")
    ):
        raise client.E2EError(f"Evidence screenshot path is unsafe: {raw_value}")
    path = scenario_root.joinpath(*relative.parts)
    if not path.is_file() or path.is_symlink():
        raise client.E2EError(f"Evidence screenshot is missing or linked: {path}")
    return path


def require_json_object(path: Path, description: str) -> dict[str, object]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise client.E2EError(f"Cannot read {description} {path}: {exception}") from exception
    if not isinstance(value, dict):
        raise client.E2EError(f"The {description} must contain a JSON object: {path}")
    return value


def validate_assertions(report: dict[str, object]) -> int:
    assertions = report.get("assertions")
    if not isinstance(assertions, list) or not assertions:
        raise client.E2EError("E2E report has no assertion inventory")
    names: set[str] = set()
    for assertion in assertions:
        if not isinstance(assertion, dict):
            raise client.E2EError("E2E report contains a non-object assertion")
        name = assertion.get("name")
        if not isinstance(name, str) or not name or name in names:
            raise client.E2EError("E2E report contains an invalid or duplicate assertion name")
        names.add(name)
        if assertion.get("passed") is not True:
            raise client.E2EError(f"E2E assertion did not pass: {name}")
        if "expected" not in assertion or "actual" not in assertion:
            raise client.E2EError(f"E2E assertion has no expected/actual evidence: {name}")
    return len(assertions)


def validate_artifacts(
    configuration: client.ResolvedConfiguration,
    runtime: Path,
    report: dict[str, object],
) -> tuple[str, str]:
    lock = client.load_artifact_lock(configuration, runtime)
    if lock is None or lock.get("schema") != 2:
        raise client.E2EError("Scenario evidence has no schema-2 artifact lock")
    locked_artifacts = lock.get("artifacts")
    report_artifacts = report.get("artifacts")
    if not isinstance(locked_artifacts, dict) or not isinstance(report_artifacts, list):
        raise client.E2EError("Scenario artifact provenance is invalid")
    report_by_mod_id = {
        value.get("mod_id"): value
        for value in report_artifacts
        if isinstance(value, dict) and isinstance(value.get("mod_id"), str)
    }
    if len(report_by_mod_id) != len(report_artifacts):
        raise client.E2EError("Scenario report artifact ids are invalid or duplicated")

    digests: dict[str, str] = {}
    for role in client.ARTIFACT_ROLES:
        locked = locked_artifacts.get(role)
        if not isinstance(locked, dict):
            raise client.E2EError(f"Scenario artifact lock has no {role} entry")
        mod_id = locked.get("mod_id")
        reported = report_by_mod_id.get(mod_id)
        if not isinstance(reported, dict):
            raise client.E2EError(f"Scenario report has no {role} artifact provenance")
        if reported.get("origin_kind") != "PATH":
            raise client.E2EError(f"Scenario report {role} artifact is not one path origin")
        for report_key, lock_key in (
            ("file_name", "target_file"),
            ("size", "size"),
            ("sha256", "sha256"),
        ):
            if reported.get(report_key) != locked.get(lock_key):
                raise client.E2EError(
                    f"Scenario report {role} {report_key} differs from its artifact lock"
                )
        digest = client.validate_hex_digest(locked.get("sha256"), f"{role} lock")
        artifact_path = (
            client.game_directory(configuration, runtime)
            / "mods"
            / str(locked["target_file"])
        )
        client.verify_exact_file(
            artifact_path,
            digest,
            int(locked["size"]),
            f"Scenario {role} artifact",
        )
        digests[role] = digest
    if len(report_by_mod_id) != len(client.ARTIFACT_ROLES):
        raise client.E2EError("Scenario report contains unexpected artifact provenance")
    return digests["production"], digests["harness"]


def validate_screenshots(
    configuration: client.ResolvedConfiguration,
    scenario_root: Path,
    report: dict[str, object],
) -> tuple[int, float | None, list[Path]]:
    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or not screenshots:
        raise client.E2EError("E2E report has no screenshot inventory")
    capture = client.require_object(client.evidence_spec(configuration), "capture")
    expected_dimensions = (int(capture["width"]), int(capture["height"]))
    images: list[PngImage] = []
    paths: list[Path] = []
    steps: set[str] = set()
    relative_files: set[str] = set()
    for screenshot in screenshots:
        if not isinstance(screenshot, dict):
            raise client.E2EError("E2E report contains a non-object screenshot")
        step = screenshot.get("step")
        if not isinstance(step, str) or not step or step in steps:
            raise client.E2EError("E2E report has an invalid or duplicate screenshot step")
        steps.add(step)
        raw_file = screenshot.get("file")
        if not isinstance(raw_file, str) or raw_file in relative_files:
            raise client.E2EError("E2E report has an invalid or duplicate screenshot file")
        relative_files.add(raw_file)
        path = safe_screenshot_path(scenario_root, raw_file)
        if screenshot.get("size") != path.stat().st_size:
            raise client.E2EError(f"Evidence screenshot size differs from report: {path}")
        digest = sha256_file(path)
        if screenshot.get("sha256") != digest:
            raise client.E2EError(f"Evidence screenshot digest differs from report: {path}")
        image = decode_png(path, expected_dimensions)
        if (image.width, image.height) != expected_dimensions:
            raise client.E2EError(f"Evidence screenshot has the wrong dimensions: {path}")
        if (screenshot.get("width"), screenshot.get("height")) != expected_dimensions:
            raise client.E2EError(f"Evidence screenshot report has the wrong dimensions: {path}")
        if type(screenshot.get("completed_render_count")) is not int or int(
            screenshot["completed_render_count"]
        ) < 2:
            raise client.E2EError(f"Evidence screenshot was captured before two renders: {path}")
        assert_image_is_not_blank(image, str(path))
        images.append(image)
        paths.append(path)

    screenshot_entries = list((scenario_root / "screenshots").iterdir())
    if any(not path.is_file() or path.is_symlink() for path in screenshot_entries):
        raise client.E2EError("Evidence screenshot directory contains a linked or non-file entry")
    actual_pngs = {path.name for path in screenshot_entries}
    expected_pngs = {PurePosixPath(value).name for value in relative_files}
    if actual_pngs != expected_pngs:
        raise client.E2EError(
            "Evidence screenshot inventory changed: "
            f"missing={sorted(expected_pngs - actual_pngs)}, "
            f"unexpected={sorted(actual_pngs - expected_pngs)}"
        )

    maximum_ratio: float | None = None
    if len(images) >= 2:
        maximum_ratio = max(
            changed_pixel_ratio(left, right)
            for left, right in zip(images, images[1:])
        )
        if maximum_ratio < MINIMUM_CHANGED_PIXEL_RATIO:
            raise client.E2EError(
                "Evidence screenshot sequence has no material visual change: "
                f"{maximum_ratio:.6f}"
            )
    return len(images), maximum_ratio, paths


def archive_profile_version(profile_id: str) -> str:
    match = re.fullmatch(r"etherology-e2e-fabric-1\.20\.1-v([1-9][0-9]*)", profile_id)
    if match is None:
        raise client.E2EError("Fabric archive profile id is invalid")
    return match.group(1)


def validate_archive_root_identity(archive_root: Path, profile_id: str) -> None:
    expected_name = f"{PHASE_ZERO_SCENARIO_ID}-v{archive_profile_version(profile_id)}"
    if archive_root.name != expected_name:
        raise client.E2EError(
            "Fabric archive directory does not identify its recorded profile: "
            f"expected {expected_name}, found {archive_root.name}"
        )


def expected_repository_archive_root(
    configuration: client.ResolvedConfiguration,
    profile_id: str,
) -> Path:
    relative_path = (
        f"docs/evidence/fabric-1.20.1/{PHASE_ZERO_SCENARIO_ID}"
        f"-v{archive_profile_version(profile_id)}"
    )
    return client.safe_repository_path(
        configuration.repository_root,
        relative_path,
        "Fabric evidence archive",
    )


def validate_archive_directory_inventory(
    archive_root: Path,
    include_manifest: bool,
) -> None:
    if not archive_root.is_dir() or archive_root.is_symlink():
        raise client.E2EError(
            f"Fabric evidence archive is missing or linked: {archive_root}"
        )
    files: set[str] = set()
    directories: set[str] = set()
    for path in archive_root.rglob("*"):
        relative_path = path.relative_to(archive_root).as_posix()
        if path.is_symlink():
            raise client.E2EError(
                f"Fabric evidence archive contains a linked entry: {relative_path}"
            )
        if path.is_file():
            files.add(relative_path)
        elif path.is_dir():
            directories.add(relative_path)
        else:
            raise client.E2EError(
                f"Fabric evidence archive contains a special entry: {relative_path}"
            )
    expected_files = set(ARCHIVE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    if files != expected_files or directories != ARCHIVE_DIRECTORIES:
        raise client.E2EError(
            "Fabric evidence archive inventory changed: "
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
        raise client.E2EError("Fabric evidence archive exceeds the size bound")


def validate_archive_report_contract(
    archive_root: Path,
    report: dict[str, object],
    expected_dimensions: tuple[int, int],
) -> tuple[
    int,
    float,
    list[Path],
    dict[str, dict[str, object]],
]:
    if set(report) != EXPECTED_REPORT_FIELDS:
        raise client.E2EError("Fabric archive report field inventory changed")
    if (
        report.get("schema") != 2
        or report.get("scenario") != PHASE_ZERO_SCENARIO_ID
        or report.get("lane") != "fabric-1.20.1"
        or report.get("role") != "host"
        or report.get("status") != "passed"
        or report.get("lifecycle_failure") != ""
        or type(report.get("client_ticks")) is not int
        or int(report["client_ticks"]) <= 0
        or report.get("world") != EXPECTED_WORLD
        or report.get("ready_resources") != EXPECTED_READY_RESOURCES
    ):
        raise client.E2EError("Fabric archive report lifecycle contract is invalid")

    assertions = report.get("assertions")
    assertion_count = validate_assertions(report)
    if not isinstance(assertions, list):
        raise client.E2EError("Fabric archive assertion inventory is invalid")
    if any(
        not isinstance(assertion, dict)
        or set(assertion) != {"name", "passed", "expected", "actual"}
        for assertion in assertions
    ):
        raise client.E2EError("Fabric archive assertion fields changed")
    assertion_names = tuple(str(assertion["name"]) for assertion in assertions)
    if assertion_names != EXPECTED_ASSERTION_NAMES:
        raise client.E2EError("Fabric archive assertion inventory changed")

    report_artifacts = report.get("artifacts")
    if not isinstance(report_artifacts, list) or len(report_artifacts) != len(
        ARCHIVE_ARTIFACT_IDENTITIES
    ):
        raise client.E2EError("Fabric archive artifact inventory changed")
    artifacts: dict[str, dict[str, object]] = {}
    for artifact, (role, (mod_id, file_name)) in zip(
        report_artifacts,
        ARCHIVE_ARTIFACT_IDENTITIES.items(),
    ):
        if not isinstance(artifact, dict) or set(artifact) != {
            "mod_id",
            "origin_kind",
            "file_name",
            "size",
            "sha256",
        }:
            raise client.E2EError(
                f"Fabric archive {role} artifact provenance is malformed"
            )
        if (
            artifact.get("mod_id") != mod_id
            or artifact.get("origin_kind") != "PATH"
            or artifact.get("file_name") != file_name
            or type(artifact.get("size")) is not int
            or int(artifact["size"]) <= 0
        ):
            raise client.E2EError(
                f"Fabric archive {role} artifact identity is invalid"
            )
        digest = client.validate_hex_digest(
            artifact.get("sha256"),
            f"archived Fabric {role} artifact",
        )
        artifacts[role] = {
            "mod_id": mod_id,
            "file_name": file_name,
            "size": artifact["size"],
            "sha256": digest,
        }

    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or len(screenshots) != len(
        EXPECTED_SCREENSHOTS
    ):
        raise client.E2EError("Fabric archive screenshot inventory changed")
    images: list[PngImage] = []
    screenshot_paths: list[Path] = []
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
        }:
            raise client.E2EError("Fabric archive screenshot provenance is malformed")
        if (
            screenshot.get("step") != expected_step
            or screenshot.get("role") != "host"
            or screenshot.get("file") != expected_file
            or (screenshot.get("width"), screenshot.get("height"))
            != expected_dimensions
            or type(screenshot.get("size")) is not int
            or int(screenshot["size"]) <= 0
            or type(screenshot.get("completed_render_count")) is not int
            or int(screenshot["completed_render_count"]) < 2
        ):
            raise client.E2EError("Fabric archive screenshot contract is invalid")
        path = safe_screenshot_path(archive_root, screenshot["file"])
        if path.stat().st_size != screenshot["size"]:
            raise client.E2EError(
                f"Fabric archive screenshot size differs from report: {path}"
            )
        digest = client.validate_hex_digest(
            screenshot.get("sha256"),
            f"archived Fabric screenshot {expected_file}",
        )
        if sha256_file(path) != digest:
            raise client.E2EError(
                f"Fabric archive screenshot digest differs from report: {path}"
            )
        image = decode_png(path, expected_dimensions)
        if (image.width, image.height) != expected_dimensions:
            raise client.E2EError(
                f"Fabric archive screenshot dimensions are invalid: {path}"
            )
        assert_image_is_not_blank(image, str(path))
        images.append(image)
        screenshot_paths.append(path)

    ratio = changed_pixel_ratio(images[0], images[1])
    if ratio < MINIMUM_CHANGED_PIXEL_RATIO:
        raise client.E2EError(
            "Fabric archive screenshot sequence has no material visual change: "
            f"{ratio:.6f}"
        )
    return assertion_count, ratio, screenshot_paths, artifacts


def archive_file_records(archive_root: Path) -> dict[str, dict[str, object]]:
    return {
        relative_path: {
            "size": (archive_root / relative_path).stat().st_size,
            "sha256": sha256_file(archive_root / relative_path),
        }
        for relative_path in ARCHIVE_PAYLOAD_PATHS
    }


def validate_archive_profile_configuration(
    configuration: client.ResolvedConfiguration,
    profile_manifest_path: Path,
) -> tuple[str, dict[str, object]]:
    expected_profile_path = client.safe_repository_path(
        configuration.repository_root,
        ARCHIVED_PROFILE_MANIFEST_PATH,
        "archived Fabric profile manifest",
    )
    if (
        profile_manifest_path.resolve() != expected_profile_path.resolve()
        or not profile_manifest_path.is_file()
        or profile_manifest_path.is_symlink()
    ):
        raise client.E2EError(
            "Fabric archive creation requires the tracked profile manifest: "
            f"{expected_profile_path}"
        )
    profile = client.profile_spec(configuration)
    profile_id = str(profile.get("id"))
    archive_profile_version(profile_id)
    if (
        profile.get("runtime_directory") != profile_id
        or configuration.artifact_lane.get("artifact_node") != "fabric-1.20.1"
        or configuration.runtime_lane.get("runtime_version") != "1.20.1"
        or configuration.runtime_lane.get("loader") != "fabric"
        or configuration.runtime_lane.get("loader_version") != "0.17.3"
        or configuration.runtime_lane.get("java") != 17
        or PHASE_ZERO_SCENARIO_ID not in client.scenario_ids(configuration)
    ):
        raise client.E2EError("The active Fabric archive profile identity is invalid")
    capture = client.require_object(client.evidence_spec(configuration), "capture")
    if (
        capture.get("kind") != "composed-minecraft-framebuffer"
        or (capture.get("width"), capture.get("height"))
        != EXPECTED_FRAMEBUFFER_DIMENSIONS
    ):
        raise client.E2EError("The active Fabric archive capture contract is invalid")
    for role, (mod_id, file_name) in ARCHIVE_ARTIFACT_IDENTITIES.items():
        artifact = client.artifact_spec(configuration, role)
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            raise client.E2EError(
                f"The active Fabric profile has the wrong {role} archive identity"
            )
    return profile_id, capture


def build_archive_manifest(
    configuration: client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> dict[str, object]:
    validate_archive_directory_inventory(archive_root, include_manifest=False)
    validate_archive_size(archive_root)
    profile_id, capture = validate_archive_profile_configuration(
        configuration,
        profile_manifest_path,
    )
    validate_archive_root_identity(archive_root, profile_id)
    expected_archive_root = expected_repository_archive_root(
        configuration,
        profile_id,
    )
    expected_capture_runtime = client.runtime_root(configuration)
    client.ensure_owned_state_roots()
    if (
        archive_root.resolve() != expected_archive_root.resolve()
        or capture_runtime.resolve() != expected_capture_runtime.resolve()
        or not capture_runtime.is_dir()
        or capture_runtime.is_symlink()
        or capture_runtime.name != profile_id
    ):
        raise client.E2EError(
            "Fabric archive creation requires the exact repository archive and "
            f"owned capture runtime: {expected_archive_root}, "
            f"{expected_capture_runtime}"
        )
    client.verify_runtime(
        configuration,
        capture_runtime,
        artifact_policy="optional",
    )

    live_summary = validate_scenario(
        configuration,
        PHASE_ZERO_SCENARIO_ID,
        capture_runtime,
    )
    report = require_json_object(
        archive_root / "reports" / "report.json",
        "Fabric archived scenario report",
    )
    assertion_count, ratio, screenshot_paths, artifacts = (
        validate_archive_report_contract(
            archive_root,
            report,
            EXPECTED_FRAMEBUFFER_DIMENSIONS,
        )
    )
    if (
        assertion_count != live_summary.assertion_count
        or len(screenshot_paths) != live_summary.screenshot_count
        or artifacts["production"]["sha256"] != live_summary.production_sha256
        or artifacts["harness"]["sha256"] != live_summary.harness_sha256
        or live_summary.changed_pixel_ratio is None
        or abs(ratio - live_summary.changed_pixel_ratio) > 1e-12
    ):
        raise client.E2EError(
            "Fabric archive payload contract differs from its validated capture runtime"
        )

    capture_scenario_root = (
        client.evidence_root(configuration, capture_runtime) / PHASE_ZERO_SCENARIO_ID
    )
    capture_mtime_ns: dict[str, int] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        archive_path = archive_root / relative_path
        capture_path = capture_scenario_root / relative_path
        if not capture_path.is_file() or capture_path.is_symlink():
            raise client.E2EError(
                f"Fabric capture payload is missing or linked: {capture_path}"
            )
        if (
            archive_path.stat().st_size != capture_path.stat().st_size
            or sha256_file(archive_path) != sha256_file(capture_path)
        ):
            raise client.E2EError(
                "Fabric archive payload differs from its explicit capture runtime: "
                f"{relative_path}"
            )
        capture_mtime_ns[relative_path] = capture_path.stat().st_mtime_ns
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[relative_path]
        for relative_path in ARCHIVE_PAYLOAD_PATHS
        if relative_path != "reports/done.marker"
    ):
        raise client.E2EError(
            "Fabric capture completion marker predates archived payload evidence"
        )

    capture_metadata_path = capture_runtime / ARCHIVE_CAPTURE_METADATA_PATH
    if not capture_metadata_path.is_file() or capture_metadata_path.is_symlink():
        raise client.E2EError(
            f"Fabric trusted capture metadata is missing or linked: "
            f"{capture_metadata_path}"
        )
    profile_manifest_size = profile_manifest_path.stat().st_size
    if profile_manifest_size <= 0:
        raise client.E2EError("The tracked Fabric profile manifest is empty")
    return {
        "schema": 1,
        "kind": ARCHIVE_KIND,
        "verification_scope": ARCHIVE_VERIFICATION_SCOPE,
        "scenario": PHASE_ZERO_SCENARIO_ID,
        "profile": {
            "id": profile_id,
            "manifest_path": ARCHIVED_PROFILE_MANIFEST_PATH,
            "manifest_size": profile_manifest_size,
            "manifest_sha256": sha256_file(profile_manifest_path),
        },
        "runtime": {
            "artifact_node": configuration.artifact_lane["artifact_node"],
            "minecraft": configuration.runtime_lane["runtime_version"],
            "loader": configuration.runtime_lane["loader"],
            "loader_version": configuration.runtime_lane["loader_version"],
            "java": configuration.runtime_lane["java"],
            "capture_kind": capture["kind"],
            "framebuffer_width": capture["width"],
            "framebuffer_height": capture["height"],
        },
        "publication": {
            **ARCHIVE_PUBLICATION_ATTESTATION,
            "capture_mtime_ns": capture_mtime_ns,
        },
        "capture_metadata": {
            "path": ARCHIVE_CAPTURE_METADATA_PATH,
            "size": capture_metadata_path.stat().st_size,
            "sha256": sha256_file(capture_metadata_path),
        },
        "assertion_count": assertion_count,
        "screenshot_count": len(screenshot_paths),
        "artifacts": artifacts,
        "files": archive_file_records(archive_root),
    }


def write_archive_manifest(
    configuration: client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> Path:
    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if manifest_path.exists() or manifest_path.is_symlink():
        raise client.E2EError(
            f"Fabric archive manifest already exists: {manifest_path}"
        )
    manifest = build_archive_manifest(
        configuration,
        profile_manifest_path,
        capture_runtime,
        archive_root,
    )
    try:
        with manifest_path.open("x", encoding="utf-8", newline="\n") as handle:
            json.dump(manifest, handle, indent=2)
            handle.write("\n")
    except FileExistsError as exception:
        raise client.E2EError(
            f"Fabric archive manifest already exists: {manifest_path}"
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
        "publication",
        "capture_metadata",
        "assertion_count",
        "screenshot_count",
        "artifacts",
        "files",
    }:
        raise client.E2EError("Fabric archive manifest field inventory changed")
    if (
        manifest.get("schema") != 1
        or manifest.get("kind") != ARCHIVE_KIND
        or manifest.get("verification_scope") != ARCHIVE_VERIFICATION_SCOPE
        or manifest.get("scenario") != PHASE_ZERO_SCENARIO_ID
        or manifest.get("assertion_count") != len(EXPECTED_ASSERTION_NAMES)
        or manifest.get("screenshot_count") != len(EXPECTED_SCREENSHOTS)
    ):
        raise client.E2EError("Fabric archive manifest identity is invalid")

    profile = manifest.get("profile")
    if not isinstance(profile, dict) or set(profile) != {
        "id",
        "manifest_path",
        "manifest_size",
        "manifest_sha256",
    }:
        raise client.E2EError("Fabric archive profile provenance is malformed")
    profile_id = profile.get("id")
    if not isinstance(profile_id, str):
        raise client.E2EError("Fabric archive profile id is malformed")
    validate_archive_root_identity(archive_root, profile_id)
    if (
        profile.get("manifest_path") != ARCHIVED_PROFILE_MANIFEST_PATH
        or type(profile.get("manifest_size")) is not int
        or int(profile["manifest_size"]) <= 0
    ):
        raise client.E2EError("Fabric archive profile provenance is invalid")
    client.validate_hex_digest(
        profile.get("manifest_sha256"),
        "archived Fabric profile manifest",
    )

    runtime = manifest.get("runtime")
    expected_runtime = {
        "artifact_node": "fabric-1.20.1",
        "minecraft": "1.20.1",
        "loader": "fabric",
        "loader_version": "0.17.3",
        "java": 17,
        "capture_kind": "composed-minecraft-framebuffer",
        "framebuffer_width": EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
        "framebuffer_height": EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
    }
    if not isinstance(runtime, dict) or runtime != expected_runtime:
        raise client.E2EError("Fabric archive runtime identity is invalid")

    publication = manifest.get("publication")
    if not isinstance(publication, dict) or set(publication) != {
        *ARCHIVE_PUBLICATION_ATTESTATION,
        "capture_mtime_ns",
    }:
        raise client.E2EError("Fabric archive publication provenance is malformed")
    for key, value in ARCHIVE_PUBLICATION_ATTESTATION.items():
        if publication.get(key) != value:
            raise client.E2EError("Fabric archive publication attestation is invalid")
    capture_mtime_ns = publication.get("capture_mtime_ns")
    if not isinstance(capture_mtime_ns, dict) or set(capture_mtime_ns) != set(
        ARCHIVE_PAYLOAD_PATHS
    ):
        raise client.E2EError("Fabric archive capture timestamp inventory changed")
    if any(type(value) is not int or value <= 0 for value in capture_mtime_ns.values()):
        raise client.E2EError("Fabric archive capture timestamp is invalid")
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[relative_path]
        for relative_path in ARCHIVE_PAYLOAD_PATHS
        if relative_path != "reports/done.marker"
    ):
        raise client.E2EError(
            "Fabric archive completion marker predates captured payload evidence"
        )

    capture_metadata = manifest.get("capture_metadata")
    if not isinstance(capture_metadata, dict) or set(capture_metadata) != {
        "path",
        "size",
        "sha256",
    }:
        raise client.E2EError("Fabric archive capture metadata is malformed")
    if (
        capture_metadata.get("path") != ARCHIVE_CAPTURE_METADATA_PATH
        or type(capture_metadata.get("size")) is not int
        or int(capture_metadata["size"]) <= 0
    ):
        raise client.E2EError("Fabric archive capture metadata identity is invalid")
    client.validate_hex_digest(
        capture_metadata.get("sha256"),
        "archived Fabric trusted capture metadata",
    )

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or set(artifacts) != set(
        ARCHIVE_ARTIFACT_IDENTITIES
    ):
        raise client.E2EError("Fabric archive artifact inventory changed")
    expected_artifacts: dict[str, dict[str, object]] = {}
    for role, (mod_id, file_name) in ARCHIVE_ARTIFACT_IDENTITIES.items():
        artifact = artifacts.get(role)
        if not isinstance(artifact, dict) or set(artifact) != {
            "mod_id",
            "file_name",
            "size",
            "sha256",
        }:
            raise client.E2EError(
                f"Fabric archive {role} artifact provenance is malformed"
            )
        if (
            artifact.get("mod_id") != mod_id
            or artifact.get("file_name") != file_name
            or type(artifact.get("size")) is not int
            or int(artifact["size"]) <= 0
        ):
            raise client.E2EError(
                f"Fabric archive {role} artifact identity is invalid"
            )
        client.validate_hex_digest(
            artifact.get("sha256"),
            f"archived Fabric {role} artifact",
        )
        expected_artifacts[role] = artifact

    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(ARCHIVE_PAYLOAD_PATHS):
        raise client.E2EError("Fabric archive payload inventory changed")
    expected_files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if not isinstance(record, dict) or set(record) != {"size", "sha256"}:
            raise client.E2EError(
                f"Fabric archive payload record is malformed: {relative_path}"
            )
        if type(record.get("size")) is not int or int(record["size"]) <= 0:
            raise client.E2EError(
                f"Fabric archive payload size is invalid: {relative_path}"
            )
        client.validate_hex_digest(
            record.get("sha256"),
            f"archived Fabric payload {relative_path}",
        )
        expected_files[relative_path] = record
    return ValidatedArchiveManifest(
        profile_id=profile_id,
        runtime=runtime,
        publication=publication,
        capture_metadata=capture_metadata,
        artifacts=expected_artifacts,
        files=expected_files,
    )


def validate_archived_scenario(archive_root: Path) -> ArchiveEvidenceSummary:
    validate_archive_directory_inventory(archive_root, include_manifest=True)
    validate_archive_size(archive_root)
    manifest = require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME,
        "Fabric evidence archive manifest",
    )
    validated_manifest = validate_archive_manifest_shape(archive_root, manifest)
    for relative_path, record in validated_manifest.files.items():
        path = archive_root / relative_path
        if path.stat().st_size != record["size"] or sha256_file(path) != record["sha256"]:
            raise client.E2EError(
                f"Fabric archive payload differs from its manifest: {relative_path}"
            )

    done_path = archive_root / "reports" / "done.marker"
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise client.E2EError(
            "Fabric archived completion marker has unexpected content"
        )
    report = require_json_object(
        archive_root / "reports" / "report.json",
        "Fabric archived scenario report",
    )
    assertion_count, ratio, screenshot_paths, artifacts = (
        validate_archive_report_contract(
            archive_root,
            report,
            (
                int(validated_manifest.runtime["framebuffer_width"]),
                int(validated_manifest.runtime["framebuffer_height"]),
            ),
        )
    )
    if (
        assertion_count != manifest["assertion_count"]
        or len(screenshot_paths) != manifest["screenshot_count"]
        or artifacts != validated_manifest.artifacts
    ):
        raise client.E2EError(
            "Fabric archive report differs from its manifest contract"
        )
    return ArchiveEvidenceSummary(
        profile_id=validated_manifest.profile_id,
        assertion_count=assertion_count,
        screenshot_count=len(screenshot_paths),
        changed_pixel_ratio=ratio,
        production_sha256=str(artifacts["production"]["sha256"]),
        harness_sha256=str(artifacts["harness"]["sha256"]),
    )


def validate_game_lifecycle(
    configuration: client.ResolvedConfiguration,
    runtime: Path,
    scenario_id: str,
    report: dict[str, object],
) -> None:
    game = client.game_directory(configuration, runtime)
    crash_reports = game / "crash-reports"
    if any(crash_reports.iterdir()):
        raise client.E2EError(f"E2E runtime contains a crash report: {crash_reports}")
    latest_log = game / "logs" / "latest.log"
    if not latest_log.is_file() or latest_log.is_symlink():
        raise client.E2EError(f"E2E game log is missing or linked: {latest_log}")
    if latest_log.stat().st_size > client.MAXIMUM_PROCESS_LOG_SIZE:
        raise client.E2EError("E2E game log exceeds the configured size bound")
    log_content = latest_log.read_text(encoding="utf-8", errors="replace")
    fatal_marker = next(
        (marker for marker in FATAL_GAME_LOG_MARKERS if marker in log_content),
        None,
    )
    if fatal_marker is not None:
        raise client.E2EError(f"E2E game log contains fatal marker: {fatal_marker}")
    if "Stopping!" not in log_content:
        raise client.E2EError("E2E client did not record a normal shutdown")
    if f"Etherology {scenario_id} evidence is complete" not in log_content:
        raise client.E2EError("E2E game log has no matching evidence-complete marker")

    world = report.get("world")
    if isinstance(world, dict) and isinstance(world.get("save_directory"), str):
        save = game / "saves" / str(world["save_directory"])
        if not save.is_dir() or save.is_symlink():
            raise client.E2EError(f"E2E world save is missing or linked: {save}")


def validate_scenario(
    configuration: client.ResolvedConfiguration,
    configured_scenario_id: str | None,
    runtime: Path | None = None,
) -> EvidenceSummary:
    scenario_id = client.resolve_scenario_id(configuration, configured_scenario_id)
    target_runtime = runtime or client.runtime_root(configuration)
    client.verify_evidence_layout(configuration, target_runtime)
    scenario_root = client.evidence_root(configuration, target_runtime) / scenario_id
    reports_directory = scenario_root / "reports"
    report_path = reports_directory / "report.json"
    done_path = reports_directory / "done.marker"
    if {path.name for path in reports_directory.iterdir()} != {
        "report.json",
        "done.marker",
    }:
        raise client.E2EError("E2E report directory has an incomplete or unexpected inventory")
    if report_path.is_symlink() or done_path.is_symlink():
        raise client.E2EError("E2E report or completion marker is linked")
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise client.E2EError("E2E completion marker has unexpected content")

    report = require_json_object(report_path, "E2E scenario report")
    if (
        report.get("schema") != 2
        or report.get("scenario") != scenario_id
        or report.get("lane") != configuration.artifact_lane["artifact_node"]
        or not isinstance(report.get("role"), str)
        or not report["role"]
        or report.get("status") != "passed"
        or report.get("lifecycle_failure") != ""
        or type(report.get("client_ticks")) is not int
        or int(report["client_ticks"]) <= 0
    ):
        raise client.E2EError("E2E scenario report did not record a clean passing lifecycle")

    assertion_count = validate_assertions(report)
    production_digest, harness_digest = validate_artifacts(
        configuration,
        target_runtime,
        report,
    )
    screenshot_count, maximum_ratio, screenshot_paths = validate_screenshots(
        configuration,
        scenario_root,
        report,
    )
    evidence_files = [report_path, *screenshot_paths]
    if any(done_path.stat().st_mtime_ns < path.stat().st_mtime_ns for path in evidence_files):
        raise client.E2EError("E2E completion marker was not written after all evidence")
    scenario_size = sum(
        path.stat().st_size
        for path in scenario_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if scenario_size > MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise client.E2EError("E2E scenario evidence exceeds the configured size bound")
    validate_game_lifecycle(configuration, target_runtime, scenario_id, report)
    return EvidenceSummary(
        scenario_id=scenario_id,
        assertion_count=assertion_count,
        screenshot_count=screenshot_count,
        changed_pixel_ratio=maximum_ratio,
        production_sha256=production_digest,
        harness_sha256=harness_digest,
    )


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Validate live or self-contained Etherology Fabric evidence, or seal "
            "one explicit repository archive."
        )
    )
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument(
        "--scenario",
        help="validate this scenario in the currently configured owned runtime",
    )
    operation.add_argument(
        "--archive",
        type=Path,
        help="validate a frozen archive without consulting current source or runtime",
    )
    operation.add_argument(
        "--create-archive-manifest",
        type=Path,
        metavar="ARCHIVE",
        help="seal an exact repository archive from an explicit owned runtime",
    )
    parser.add_argument(
        "--capture-runtime",
        type=Path,
        help="exact repository-owned capture runtime used only while sealing",
    )
    parser.add_argument(
        "--profile-manifest",
        type=Path,
        help="exact tracked Fabric profile manifest used only while sealing",
    )
    arguments = parser.parse_args()
    sealing = arguments.create_archive_manifest is not None
    auxiliary_arguments = (
        arguments.capture_runtime,
        arguments.profile_manifest,
    )
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


def print_evidence_summary(
    description: str,
    assertion_count: int,
    screenshot_count: int,
    changed_pixel_ratio_value: float | None,
    production_sha256: str,
    harness_sha256: str,
) -> None:
    ratio = (
        "n/a"
        if changed_pixel_ratio_value is None
        else f"{changed_pixel_ratio_value:.6f}"
    )
    print(
        f"Validated {description}: {assertion_count} assertions, "
        f"{screenshot_count} screenshots, changed-pixel ratio {ratio}"
    )
    print(f"Production SHA-256: {production_sha256}")
    print(f"Harness SHA-256: {harness_sha256}")


def main() -> int:
    arguments = parse_arguments()
    try:
        if arguments.archive is not None:
            archive_summary = validate_archived_scenario(arguments.archive)
            print_evidence_summary(
                f"archived {PHASE_ZERO_SCENARIO_ID} "
                f"({archive_summary.profile_id})",
                archive_summary.assertion_count,
                archive_summary.screenshot_count,
                archive_summary.changed_pixel_ratio,
                archive_summary.production_sha256,
                archive_summary.harness_sha256,
            )
            return 0
        if arguments.create_archive_manifest is not None:
            configuration = client.load_configuration(arguments.profile_manifest)
            manifest_path = write_archive_manifest(
                configuration,
                arguments.profile_manifest,
                arguments.capture_runtime,
                arguments.create_archive_manifest,
            )
            archive_summary = validate_archived_scenario(
                arguments.create_archive_manifest
            )
            print(f"Created and validated archive manifest: {manifest_path}")
            print_evidence_summary(
                f"archived {PHASE_ZERO_SCENARIO_ID} "
                f"({archive_summary.profile_id})",
                archive_summary.assertion_count,
                archive_summary.screenshot_count,
                archive_summary.changed_pixel_ratio,
                archive_summary.production_sha256,
                archive_summary.harness_sha256,
            )
            return 0

        configuration = client.load_configuration()
        live_summary = validate_scenario(configuration, arguments.scenario)
    except (client.E2EError, OSError) as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2
    print_evidence_summary(
        live_summary.scenario_id,
        live_summary.assertion_count,
        live_summary.screenshot_count,
        live_summary.changed_pixel_ratio,
        live_summary.production_sha256,
        live_summary.harness_sha256,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
