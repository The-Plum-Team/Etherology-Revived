#!/usr/bin/env python3
"""Validate frozen evidence from one repository-owned Etherology E2E scenario."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path, PurePosixPath
import struct
import sys
import zlib


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import client


MAXIMUM_SCENARIO_EVIDENCE_SIZE = 64 * 1024 * 1024
MINIMUM_CHANGED_PIXEL_RATIO = 0.005
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
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


def decode_png(path: Path) -> PngImage:
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

    try:
        raw = zlib.decompress(bytes(compressed))
    except zlib.error as exception:
        raise client.E2EError(
            f"Evidence PNG payload cannot be decompressed: {path}"
        ) from exception
    stride = width * channels
    expected_size = height * (stride + 1)
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
        image = decode_png(path)
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
        description="Validate one completed Etherology packaged E2E scenario."
    )
    parser.add_argument("--scenario", required=True)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        configuration = client.load_configuration()
        summary = validate_scenario(configuration, arguments.scenario)
    except (client.E2EError, OSError) as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2
    ratio = (
        "n/a"
        if summary.changed_pixel_ratio is None
        else f"{summary.changed_pixel_ratio:.6f}"
    )
    print(
        f"Validated {summary.scenario_id}: {summary.assertion_count} assertions, "
        f"{summary.screenshot_count} screenshots, changed-pixel ratio {ratio}"
    )
    print(f"Production SHA-256: {summary.production_sha256}")
    print(f"Harness SHA-256: {summary.harness_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
