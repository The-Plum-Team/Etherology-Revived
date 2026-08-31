from __future__ import annotations

import binascii
from dataclasses import replace
import functools
import hashlib
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest import mock
import zlib


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_channel_evidence
import forge_client
import forge_evidence


FULL_CAPTURE_WIDTH = 1920
FULL_CAPTURE_HEIGHT = 1080
FIXTURE_OBJECT_LEFT = 800
FIXTURE_OBJECT_TOP = 420
FIXTURE_OBJECT_SIZE = 100


def png_chunk(chunk_type: bytes, content: bytes) -> bytes:
    crc = binascii.crc32(content, binascii.crc32(chunk_type)) & 0xFFFFFFFF
    return struct.pack(">I", len(content)) + chunk_type + content + struct.pack(">I", crc)


def rgb_png(width: int, height: int, pixels: bytes) -> bytes:
    rows = b"".join(
        b"\x00" + pixels[row * width * 3 : (row + 1) * width * 3]
        for row in range(height)
    )
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"".join(
        (
            forge_evidence.PNG_SIGNATURE,
            png_chunk(b"IHDR", header),
            png_chunk(b"IDAT", zlib.compress(rows)),
            png_chunk(b"IEND", b""),
        )
    )


def small_capture_configuration() -> forge_client.ResolvedConfiguration:
    configuration = forge_client.load_configuration()
    manifest = json.loads(json.dumps(configuration.manifest))
    manifest["evidence"]["capture"]["width"] = 64
    manifest["evidence"]["capture"]["height"] = 64
    return replace(configuration, manifest=manifest)


def valid_report(
    configuration: forge_client.ResolvedConfiguration,
) -> dict[str, object]:
    profile_descriptor = forge_client.profile_descriptor(configuration)
    profile_manifest = profile_descriptor["profile_manifest"]
    assertions = [
        {
            "name": name,
            "passed": True,
            "actual": (
                forge_channel_evidence.EXPECTED_LEVER_SUPPORT_TOPOLOGY
                if name == "lever_support_topology"
                else (
                    "true"
                    if name
                    in {
                        "forced_save",
                        "restart_client_mirror",
                        "gated_capture_mirror",
                        "transferred_capture_mirror",
                        "reopened_capture_mirror",
                    }
                    else (
                        forge_channel_evidence.EXPECTED_CAPTURE_RENDER_READY_EVIDENCE
                        if name in {"capture_render_ready", "capture_camera_exact"}
                        else ("" if name == "lifecycle" else "channel-evidence")
                    )
                )
            ),
        }
        for name in sorted(forge_channel_evidence.REQUIRED_ASSERTIONS)
    ]
    return {
        "schema": 1,
        "scenario": forge_channel_evidence.SCENARIO_ID,
        "profile_id": profile_descriptor["profile_id"],
        "profile_manifest_size": profile_manifest["size"],
        "profile_manifest_sha256": profile_manifest["sha256"],
        "artifact_node": configuration.artifact_lane["artifact_node"],
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "passed": True,
        "client_ticks": 100,
        "framebuffer_width": forge_channel_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
        "framebuffer_height": forge_channel_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
        "assertions": assertions,
        "artifacts": [],
    }


def replace_assertion_actual(
    report: dict[str, object],
    assertion_name: str,
    actual: str,
) -> None:
    assertions = report["assertions"]
    if not isinstance(assertions, list):
        raise AssertionError("Test report assertion fixture is malformed")
    matches = [
        assertion
        for assertion in assertions
        if isinstance(assertion, dict) and assertion.get("name") == assertion_name
    ]
    if len(matches) != 1:
        raise AssertionError(f"Expected one assertion named {assertion_name}")
    matches[0]["actual"] = actual


def add_report_artifacts(
    configuration: forge_client.ResolvedConfiguration,
    report: dict[str, object],
) -> dict[str, str]:
    digests = {
        "production": hashlib.sha256(b"production-artifact").hexdigest(),
        "harness": hashlib.sha256(b"harness-artifact").hexdigest(),
    }
    sizes = {
        "production": len(b"production-artifact"),
        "harness": len(b"harness-artifact"),
    }
    artifacts: list[dict[str, object]] = []
    for role in forge_client.ARTIFACT_ROLES:
        spec = forge_client.artifact_spec(configuration, role)
        mod_id = str(spec["mod_id"])
        size = sizes[role]
        digest = digests[role]
        artifacts.append(
            {
                "mod_id": mod_id,
                "passed": True,
                "file_name": spec["file_name"],
                "size": size,
                "sha256": digest,
                "failure": "",
            }
        )
        replace_assertion_actual(
            report,
            f"artifact_{mod_id}",
            f"ForgeArtifactDigest[modId={mod_id}, passed=true, "
            f"fileName={spec['file_name']}, size={size}, "
            f"sha256={digest}, failure=]",
        )
    report["artifacts"] = artifacts
    return digests


def create_screenshot_fixture(root: Path) -> dict[str, str]:
    width = 64
    height = 64
    baseline = bytearray(
        channel
        for y in range(height)
        for x in range(width)
        for channel in (x * 4 % 256, y * 4 % 256, (x + y) * 2 % 256)
    )
    left_percent, top_percent, right_percent, bottom_percent = (
        forge_channel_evidence.CHANNEL_FIXTURE_REGION_PERCENT
    )
    left = width * left_percent // 100
    top = height * top_percent // 100
    right = width * right_percent // 100
    bottom = height * bottom_percent // 100
    fixture_pixels = [
        y * width + x
        for y in range(top, bottom)
        for x in range(left, right)
    ]
    variants: dict[str, bytes] = {"gated": bytes(baseline)}
    transferred = bytearray(baseline)
    for pixel_index in fixture_pixels[:8]:
        transferred[pixel_index * 3] ^= 0xFF
    variants["transferred"] = bytes(transferred)
    reopened = bytearray(transferred)
    reopened[fixture_pixels[8] * 3 + 1] ^= 0xFF
    variants["reopened"] = bytes(reopened)

    screenshots = root / "screenshots"
    screenshots.mkdir()
    assertions: dict[str, str] = {}
    for role, file_name in forge_channel_evidence.SCREENSHOTS.items():
        path = screenshots / file_name
        path.write_bytes(rgb_png(width, height, variants[role]))
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assertions[f"{role}_screenshot"] = (
            f"ScreenshotResult[passed=true, size={path.stat().st_size}, "
            f"sha256={digest}, failure=]"
        )
    return assertions


def replace_screenshot(
    root: Path,
    assertions: dict[str, str],
    role: str,
    pixels: bytes,
) -> None:
    path = root / "screenshots" / forge_channel_evidence.SCREENSHOTS[role]
    image = forge_evidence.decode_png(path)
    path.write_bytes(rgb_png(image.width, image.height, pixels))
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    assertions[f"{role}_screenshot"] = (
        f"ScreenshotResult[passed=true, size={path.stat().st_size}, "
        f"sha256={digest}, failure=]"
    )


@functools.cache
def representative_full_resolution_background() -> bytes:
    row = bytes(
        channel
        for x in range(FULL_CAPTURE_WIDTH)
        for channel in (
            64 + (x // 16) % 64,
            80 + (x // 20) % 64,
            96 + (x // 24) % 64,
        )
    )
    return row * FULL_CAPTURE_HEIGHT


def pixels_with_flat_fixture_object(
    baseline: bytes,
    left: int,
) -> bytes:
    pixels = bytearray(baseline)
    for y in range(FIXTURE_OBJECT_TOP, FIXTURE_OBJECT_TOP + FIXTURE_OBJECT_SIZE):
        row_start = (y * FULL_CAPTURE_WIDTH + left) * 3
        row_end = row_start + FIXTURE_OBJECT_SIZE * 3
        pixels[row_start:row_end] = bytes((250, 250, 250)) * FIXTURE_OBJECT_SIZE
    return bytes(pixels)


def pixels_with_fixture_outline(
    baseline: bytes,
    left: int,
) -> bytes:
    pixels = bytearray(baseline)
    right = left + FIXTURE_OBJECT_SIZE
    bottom = FIXTURE_OBJECT_TOP + FIXTURE_OBJECT_SIZE
    for y in range(FIXTURE_OBJECT_TOP, bottom):
        for x in range(left, right):
            if (
                x == left
                or x == right - 1
                or y == FIXTURE_OBJECT_TOP
                or y == bottom - 1
            ):
                offset = (y * FULL_CAPTURE_WIDTH + x) * 3
                pixels[offset : offset + 3] = bytes((250, 250, 250))
    return bytes(pixels)


def pixels_with_transfer_marker(transferred: bytes) -> bytes:
    pixels = bytearray(transferred)
    for y in range(300, 320):
        row_start = (y * FULL_CAPTURE_WIDTH + 650) * 3
        row_end = row_start + 20 * 3
        pixels[row_start:row_end] = bytes((10, 10, 10)) * 20
    return bytes(pixels)


def pixels_with_structural_camera_drift(baseline: bytes) -> bytes:
    pixels = bytearray(baseline)
    for x in range(620, 1450, 20):
        for y in range(270, 820):
            offset = (y * FULL_CAPTURE_WIDTH + x) * 3
            pixels[offset : offset + 3] = bytes((250, 250, 250))
    return bytes(pixels)


def full_resolution_relationship_images(
    transferred: bytes,
    reopened: bytes,
) -> dict[str, forge_evidence.PngImage]:
    return {
        "gated": forge_evidence.PngImage(
            FULL_CAPTURE_WIDTH,
            FULL_CAPTURE_HEIGHT,
            pixels_with_transfer_marker(transferred),
        ),
        "transferred": forge_evidence.PngImage(
            FULL_CAPTURE_WIDTH,
            FULL_CAPTURE_HEIGHT,
            transferred,
        ),
        "reopened": forge_evidence.PngImage(
            FULL_CAPTURE_WIDTH,
            FULL_CAPTURE_HEIGHT,
            reopened,
        ),
    }


def create_archive_fixture(
    parent: Path,
) -> tuple[
    forge_client.ResolvedConfiguration,
    Path,
    dict[str, str],
]:
    configuration = small_capture_configuration()
    archive_root = parent / "ethereal-channel-v11"
    reports = archive_root / "reports"
    reports.mkdir(parents=True)
    screenshot_assertions = create_screenshot_fixture(archive_root)
    report = valid_report(configuration)
    for assertion_name, actual in screenshot_assertions.items():
        replace_assertion_actual(report, assertion_name, actual)
    artifact_digests = add_report_artifacts(configuration, report)
    (reports / "report.json").write_text(
        json.dumps(report, indent=2) + "\n",
        encoding="utf-8",
    )
    (reports / "done.marker").write_text("complete\n", encoding="utf-8")
    return configuration, archive_root, artifact_digests


class ForgeChannelReportTests(unittest.TestCase):
    def test_accepts_exact_channel_assertion_inventory(self) -> None:
        configuration = forge_client.load_configuration()

        assertions = forge_channel_evidence.validate_report(
            configuration,
            valid_report(configuration),
        )

        self.assertEqual(forge_channel_evidence.REQUIRED_ASSERTIONS, set(assertions))
        self.assertEqual("true", assertions["forced_save"])
        self.assertEqual(
            forge_channel_evidence.EXPECTED_LEVER_SUPPORT_TOPOLOGY,
            assertions["lever_support_topology"],
        )

    def test_requires_exact_capture_time_profile_provenance(self) -> None:
        configuration = forge_client.load_configuration()
        report = valid_report(configuration)
        report["profile_manifest_sha256"] = "0" * 64

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "active capture profile provenance",
        ):
            forge_channel_evidence.validate_report(configuration, report)

    def test_rejects_a_missing_capture_time_profile_field(self) -> None:
        configuration = forge_client.load_configuration()
        report = valid_report(configuration)
        del report["profile_id"]

        with self.assertRaisesRegex(forge_client.E2EError, "field inventory"):
            forge_channel_evidence.validate_report(configuration, report)

    def test_rejects_missing_conservation_assertion(self) -> None:
        configuration = forge_client.load_configuration()
        report = valid_report(configuration)
        report["assertions"] = [
            assertion
            for assertion in report["assertions"]
            if assertion["name"] != "transfer_total_conserved"
        ]

        with self.assertRaisesRegex(forge_client.E2EError, "inventory changed"):
            forge_channel_evidence.validate_report(configuration, report)

    def test_rejects_missing_prepowered_placement_assertion(self) -> None:
        configuration = forge_client.load_configuration()
        report = valid_report(configuration)
        report["assertions"] = [
            assertion
            for assertion in report["assertions"]
            if assertion["name"] != "prepowered_placement"
        ]

        with self.assertRaisesRegex(forge_client.E2EError, "inventory changed"):
            forge_channel_evidence.validate_report(configuration, report)

    def test_rejects_missing_distinct_fixture_positions_assertion(self) -> None:
        configuration = forge_client.load_configuration()
        report = valid_report(configuration)
        report["assertions"] = [
            assertion
            for assertion in report["assertions"]
            if assertion["name"] != "fixture_positions_distinct"
        ]

        with self.assertRaisesRegex(forge_client.E2EError, "inventory changed"):
            forge_channel_evidence.validate_report(configuration, report)

    def test_rejects_inexact_native_lever_support_topology(self) -> None:
        configuration = forge_client.load_configuration()
        report = valid_report(configuration)
        replace_assertion_actual(
            report,
            "lever_support_topology",
            forge_channel_evidence.EXPECTED_LEVER_SUPPORT_TOPOLOGY.replace(
                "north=in",
                "north=empty",
            ),
        )

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "lever support/topology evidence is not exact",
        ):
            forge_channel_evidence.validate_report(configuration, report)

    def test_rejects_inexact_capture_time_renderer_evidence(self) -> None:
        configuration = forge_client.load_configuration()
        report = valid_report(configuration)
        replace_assertion_actual(
            report,
            "capture_render_ready",
            "gated=true;transferred=false;reopened=true",
        )

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "capture-time renderer evidence is not exact",
        ):
            forge_channel_evidence.validate_report(configuration, report)

    def test_rejects_inexact_capture_time_camera_evidence(self) -> None:
        configuration = forge_client.load_configuration()
        report = valid_report(configuration)
        replace_assertion_actual(
            report,
            "capture_camera_exact",
            "gated=true;transferred=false;reopened=true",
        )

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "capture-time camera evidence is not exact",
        ):
            forge_channel_evidence.validate_report(configuration, report)


class ForgeChannelScreenshotTests(unittest.TestCase):
    def test_validates_exact_png_inventory_hashes_crc_dimensions_and_roi(self) -> None:
        configuration = small_capture_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            assertions = create_screenshot_fixture(root)

            transfer_ratio, reopened_ratio, paths = (
                forge_channel_evidence.validate_screenshots(
                    configuration,
                    root,
                    assertions,
                )
            )

        self.assertEqual(3, len(paths))
        self.assertGreaterEqual(
            transfer_ratio,
            forge_channel_evidence.MINIMUM_TRANSFER_CHANGED_PIXEL_RATIO,
        )
        self.assertLessEqual(
            reopened_ratio,
            forge_channel_evidence.MAXIMUM_REOPENED_STRUCTURAL_CHANGE_RATIO,
        )

    def test_accepts_uniform_reopened_brightness_shift(self) -> None:
        configuration = small_capture_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            assertions = create_screenshot_fixture(root)
            transferred_path = (
                root
                / "screenshots"
                / forge_channel_evidence.SCREENSHOTS["transferred"]
            )
            transferred = forge_evidence.decode_png(transferred_path)
            brighter_pixels = bytes(
                min(channel + 10, 255) for channel in transferred.pixels
            )
            replace_screenshot(root, assertions, "reopened", brighter_pixels)
            reopened = forge_evidence.decode_png(
                root
                / "screenshots"
                / forge_channel_evidence.SCREENSHOTS["reopened"]
            )

            raw_ratio = forge_channel_evidence.fixture_changed_pixel_ratio(
                transferred,
                reopened,
            )
            _transfer_ratio, structural_ratio, _paths = (
                forge_channel_evidence.validate_screenshots(
                    configuration,
                    root,
                    assertions,
                )
            )

        self.assertEqual(1.0, raw_ratio)
        self.assertEqual(0.0, structural_ratio)

    def test_keeps_gated_to_transferred_meaningful_change_gate(self) -> None:
        configuration = small_capture_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            assertions = create_screenshot_fixture(root)
            gated = forge_evidence.decode_png(
                root
                / "screenshots"
                / forge_channel_evidence.SCREENSHOTS["gated"]
            )
            replace_screenshot(root, assertions, "transferred", gated.pixels)
            replace_screenshot(root, assertions, "reopened", gated.pixels)

            with self.assertRaisesRegex(
                forge_client.E2EError,
                "gated-to-transferred channel visual change is below threshold",
            ):
                forge_channel_evidence.validate_screenshots(
                    configuration,
                    root,
                    assertions,
                )

    def test_rejects_full_resolution_flat_fixture_removal(self) -> None:
        baseline = representative_full_resolution_background()
        transferred = pixels_with_flat_fixture_object(
            baseline,
            FIXTURE_OBJECT_LEFT,
        )
        images = full_resolution_relationship_images(transferred, baseline)

        global_ratio, _local_ratio = (
            forge_channel_evidence.fixture_structural_change_ratios(
                images["transferred"],
                images["reopened"],
            )
        )
        high_delta_ratio = forge_channel_evidence.fixture_high_delta_pixel_ratio(
            images["transferred"],
            images["reopened"],
        )

        self.assertLess(
            global_ratio,
            forge_channel_evidence.MAXIMUM_REOPENED_STRUCTURAL_CHANGE_RATIO,
        )
        self.assertGreater(
            high_delta_ratio,
            forge_channel_evidence.MAXIMUM_REOPENED_HIGH_DELTA_PIXEL_RATIO,
        )
        with self.assertRaisesRegex(
            forge_client.E2EError,
            "high-delta pixels exceed restart lighting",
        ):
            forge_channel_evidence.validate_frame_relationships(images)

    def test_rejects_full_resolution_edge_only_fixture_removal(self) -> None:
        baseline = representative_full_resolution_background()
        transferred = pixels_with_fixture_outline(
            baseline,
            FIXTURE_OBJECT_LEFT,
        )
        images = full_resolution_relationship_images(transferred, baseline)

        global_ratio, local_ratio = (
            forge_channel_evidence.fixture_structural_change_ratios(
                images["transferred"],
                images["reopened"],
            )
        )
        high_delta_ratio = forge_channel_evidence.fixture_high_delta_pixel_ratio(
            images["transferred"],
            images["reopened"],
        )

        self.assertLess(
            global_ratio,
            forge_channel_evidence.MAXIMUM_REOPENED_STRUCTURAL_CHANGE_RATIO,
        )
        self.assertLess(
            high_delta_ratio,
            forge_channel_evidence.MAXIMUM_REOPENED_HIGH_DELTA_PIXEL_RATIO,
        )
        self.assertGreater(
            local_ratio,
            forge_channel_evidence.MAXIMUM_REOPENED_LOCAL_STRUCTURAL_CHANGE_RATIO,
        )
        with self.assertRaisesRegex(
            forge_client.E2EError,
            "local structure drifted",
        ):
            forge_channel_evidence.validate_frame_relationships(images)

    def test_rejects_full_resolution_edge_only_fixture_move(self) -> None:
        baseline = representative_full_resolution_background()
        transferred = pixels_with_fixture_outline(
            baseline,
            FIXTURE_OBJECT_LEFT,
        )
        reopened = pixels_with_fixture_outline(
            baseline,
            FIXTURE_OBJECT_LEFT + 250,
        )
        images = full_resolution_relationship_images(transferred, reopened)

        global_ratio, local_ratio = (
            forge_channel_evidence.fixture_structural_change_ratios(
                images["transferred"],
                images["reopened"],
            )
        )

        self.assertLess(
            global_ratio,
            forge_channel_evidence.MAXIMUM_REOPENED_STRUCTURAL_CHANGE_RATIO,
        )
        self.assertGreater(
            local_ratio,
            forge_channel_evidence.MAXIMUM_REOPENED_LOCAL_STRUCTURAL_CHANGE_RATIO,
        )
        with self.assertRaisesRegex(
            forge_client.E2EError,
            "local structure drifted",
        ):
            forge_channel_evidence.validate_frame_relationships(images)

    def test_rejects_unrelated_nonblank_transferred_and_reopened_frames(self) -> None:
        baseline = representative_full_resolution_background()
        unrelated = bytes(255 - channel for channel in baseline)
        images = {
            "gated": forge_evidence.PngImage(
                FULL_CAPTURE_WIDTH,
                FULL_CAPTURE_HEIGHT,
                baseline,
            ),
            "transferred": forge_evidence.PngImage(
                FULL_CAPTURE_WIDTH,
                FULL_CAPTURE_HEIGHT,
                unrelated,
            ),
            "reopened": forge_evidence.PngImage(
                FULL_CAPTURE_WIDTH,
                FULL_CAPTURE_HEIGHT,
                unrelated,
            ),
        }
        for role, image in images.items():
            forge_evidence.assert_image_is_not_blank(image, role)

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "frame drift exceeds the camera anchor",
        ):
            forge_channel_evidence.validate_frame_relationships(images)

    def test_rejects_transfer_structure_that_evades_raw_camera_bound(self) -> None:
        baseline = representative_full_resolution_background()
        structurally_drifted = pixels_with_structural_camera_drift(baseline)
        images = {
            "gated": forge_evidence.PngImage(
                FULL_CAPTURE_WIDTH,
                FULL_CAPTURE_HEIGHT,
                baseline,
            ),
            "transferred": forge_evidence.PngImage(
                FULL_CAPTURE_WIDTH,
                FULL_CAPTURE_HEIGHT,
                structurally_drifted,
            ),
            "reopened": forge_evidence.PngImage(
                FULL_CAPTURE_WIDTH,
                FULL_CAPTURE_HEIGHT,
                structurally_drifted,
            ),
        }
        transfer_ratio = forge_channel_evidence.fixture_changed_pixel_ratio(
            images["gated"],
            images["transferred"],
        )

        self.assertLess(
            transfer_ratio,
            forge_channel_evidence.MAXIMUM_TRANSFER_CHANGED_PIXEL_RATIO,
        )
        with self.assertRaisesRegex(
            forge_client.E2EError,
            "structure exceeds the camera anchor",
        ):
            forge_channel_evidence.validate_frame_relationships(images)

    def test_rejects_corrupt_png_crc(self) -> None:
        configuration = small_capture_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            assertions = create_screenshot_fixture(root)
            path = (
                root
                / "screenshots"
                / forge_channel_evidence.SCREENSHOTS["transferred"]
            )
            content = bytearray(path.read_bytes())
            content[-20] ^= 0xFF
            path.write_bytes(content)

            with self.assertRaisesRegex(forge_client.E2EError, "bad chunk CRC"):
                forge_channel_evidence.validate_screenshots(
                    configuration,
                    root,
                    assertions,
                )

    def test_rejects_png_header_over_decoded_size_bound(self) -> None:
        header = struct.pack(">IIBBBBB", 1_000_000, 1_000_000, 8, 2, 0, 0, 0)
        content = b"".join(
            (
                forge_evidence.PNG_SIGNATURE,
                png_chunk(b"IHDR", header),
                png_chunk(b"IDAT", zlib.compress(b"")),
                png_chunk(b"IEND", b""),
            )
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "oversized.png"
            path.write_bytes(content)

            with self.assertRaisesRegex(forge_client.E2EError, "decoded size bound"):
                forge_evidence.decode_png(path)


class ForgeChannelArchiveTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        dimensions_patch = mock.patch.object(
            forge_channel_evidence,
            "EXPECTED_FRAMEBUFFER_DIMENSIONS",
            (64, 64),
        )
        dimensions_patch.start()
        self.addCleanup(dimensions_patch.stop)
        self.configuration, self.archive_root, self.digests = create_archive_fixture(
            Path(self.temporary_directory.name)
        )
        self.manifest_path = forge_channel_evidence.write_archive_manifest(
            self.configuration,
            self.archive_root,
        )

    def read_manifest(self) -> dict[str, object]:
        return json.loads(self.manifest_path.read_text(encoding="utf-8"))

    def write_manifest(self, manifest: dict[str, object]) -> None:
        self.manifest_path.write_text(
            json.dumps(manifest, indent=2) + "\n",
            encoding="utf-8",
        )

    def test_validates_archive_without_loading_current_configuration(self) -> None:
        with mock.patch.object(
            forge_client,
            "load_configuration",
            side_effect=AssertionError("Archive validation loaded live configuration"),
        ):
            summary = forge_channel_evidence.validate_archived_scenario(
                self.archive_root
            )

        manifest = self.read_manifest()
        self.assertEqual(
            len(forge_channel_evidence.REQUIRED_ASSERTIONS),
            summary.assertion_count,
        )
        self.assertEqual(3, summary.screenshot_count)
        self.assertEqual(self.digests["production"], summary.production_sha256)
        self.assertEqual(self.digests["harness"], summary.harness_sha256)
        self.assertEqual(
            forge_channel_evidence.ARCHIVE_VERIFICATION_SCOPE,
            manifest["verification_scope"],
        )

    def test_rejects_payload_tampering_even_if_json_remains_valid(self) -> None:
        report_path = self.archive_root / "reports" / "report.json"
        report_path.write_text(
            report_path.read_text(encoding="utf-8") + " ",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(forge_client.E2EError, "payload differs"):
            forge_channel_evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_profile_other_than_exact_v11(self) -> None:
        manifest = self.read_manifest()
        manifest["profile"]["id"] = "etherology-e2e-forge-1.20.1-v12"
        self.write_manifest(manifest)

        with self.assertRaisesRegex(forge_client.E2EError, "profile v11"):
            forge_channel_evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_report_profile_provenance_different_from_manifest(self) -> None:
        report_path = self.archive_root / "reports" / "report.json"
        report = json.loads(report_path.read_text(encoding="utf-8"))
        report["profile_manifest_sha256"] = "0" * 64
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        manifest = self.read_manifest()
        manifest["files"]["reports/report.json"] = {
            "size": report_path.stat().st_size,
            "sha256": forge_evidence.sha256_file(report_path),
        }
        self.write_manifest(manifest)

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "report differs from its manifest contract",
        ):
            forge_channel_evidence.validate_archived_scenario(self.archive_root)

    def test_compares_explicit_current_remapped_artifacts_by_hash_and_size(self) -> None:
        production = Path(self.temporary_directory.name) / "production.jar"
        harness = Path(self.temporary_directory.name) / "harness.jar"
        profile = Path(self.temporary_directory.name) / "forge-1.20.1-profile.json"
        production.write_bytes(b"production-artifact")
        harness.write_bytes(b"harness-artifact")
        profile.write_bytes(
            (
                self.configuration.repository_root
                / forge_channel_evidence.ARCHIVED_PROFILE_MANIFEST_PATH
            ).read_bytes()
        )

        summary = forge_channel_evidence.validate_archived_scenario(
            self.archive_root,
            production,
            harness,
            profile,
        )

        self.assertEqual(self.digests["production"], summary.production_sha256)
        production.write_bytes(b"different-production-artifact")
        with self.assertRaisesRegex(
            forge_client.E2EError,
            "production artifact differs",
        ):
            forge_channel_evidence.validate_archived_scenario(
                self.archive_root,
                production,
                harness,
                profile,
            )

    def test_current_profile_mutation_invalidates_only_current_input_mode(self) -> None:
        production = Path(self.temporary_directory.name) / "production.jar"
        harness = Path(self.temporary_directory.name) / "harness.jar"
        profile = Path(self.temporary_directory.name) / "forge-1.20.1-profile.json"
        production.write_bytes(b"production-artifact")
        harness.write_bytes(b"harness-artifact")
        tracked_profile = (
            self.configuration.repository_root
            / forge_channel_evidence.ARCHIVED_PROFILE_MANIFEST_PATH
        )
        profile.write_bytes(tracked_profile.read_bytes())

        forge_channel_evidence.validate_archived_scenario(self.archive_root)
        profile.write_bytes(profile.read_bytes() + b"\n")

        forge_channel_evidence.validate_archived_scenario(self.archive_root)
        with self.assertRaisesRegex(
            forge_client.E2EError,
            "profile differs",
        ):
            forge_channel_evidence.validate_archived_scenario(
                self.archive_root,
                production,
                harness,
                profile,
            )


class ForgeChannelArchiveCreationTests(unittest.TestCase):
    def test_rejects_capture_provenance_from_another_profile_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory, mock.patch.object(
            forge_channel_evidence,
            "EXPECTED_FRAMEBUFFER_DIMENSIONS",
            (64, 64),
        ):
            configuration, archive_root, _digests = create_archive_fixture(
                Path(temporary_directory)
            )
            report_path = archive_root / "reports" / "report.json"
            report = json.loads(report_path.read_text(encoding="utf-8"))
            report["profile_manifest_sha256"] = "0" * 64
            report_path.write_text(
                json.dumps(report, indent=2) + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                forge_client.E2EError,
                "capture profile provenance differs from the tracked profile",
            ):
                forge_channel_evidence.write_archive_manifest(
                    configuration,
                    archive_root,
                )


class ForgeChannelLifecycleTests(unittest.TestCase):
    def test_accepts_normal_stopped_game_log_and_restarted_save(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory) / ".state"
            runtime = state_root / "runtimes" / forge_channel_evidence.PROFILE_ID
            game = runtime / "game"
            (game / "crash-reports").mkdir(parents=True)
            (game / "logs").mkdir()
            (game / "saves" / forge_channel_evidence.WORLD_DIRECTORY_NAME).mkdir(
                parents=True
            )
            (game / "logs" / "latest.log").write_text(
                "Forge ethereal-channel evidence complete: reports\nStopping!\n",
                encoding="utf-8",
            )

            forge_channel_evidence.validate_game_lifecycle(
                configuration,
                runtime,
                {"passed": True},
            )

    def test_rejects_missing_normal_shutdown_marker(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory) / ".state"
            runtime = state_root / "runtimes" / forge_channel_evidence.PROFILE_ID
            game = runtime / "game"
            (game / "crash-reports").mkdir(parents=True)
            (game / "logs").mkdir()
            (game / "saves" / forge_channel_evidence.WORLD_DIRECTORY_NAME).mkdir(
                parents=True
            )
            (game / "logs" / "latest.log").write_text(
                "Forge ethereal-channel evidence complete: reports\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(forge_client.E2EError, "normal shutdown"):
                forge_channel_evidence.validate_game_lifecycle(
                    configuration,
                    runtime,
                    {"passed": True},
                )


if __name__ == "__main__":
    unittest.main()
