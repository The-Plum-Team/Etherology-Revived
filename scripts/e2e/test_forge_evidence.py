from __future__ import annotations

import binascii
from dataclasses import replace
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

import forge_client
import forge_evidence


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


def valid_report(configuration: forge_client.ResolvedConfiguration) -> dict[str, object]:
    assertions = [
        {
            "name": name,
            "passed": True,
            "actual": (
                "true"
                if name == "forced_save"
                else ("" if name == "lifecycle" else "evidence")
            ),
        }
        for name in sorted(forge_evidence.REQUIRED_ASSERTIONS)
    ]
    return {
        "schema": 1,
        "scenario": forge_evidence.SCENARIO_ID,
        "artifact_node": configuration.artifact_lane["artifact_node"],
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "passed": True,
        "client_ticks": 100,
        "framebuffer_width": forge_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
        "framebuffer_height": forge_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
        "assertions": assertions,
        "artifacts": [],
    }


def small_capture_configuration() -> forge_client.ResolvedConfiguration:
    configuration = forge_client.load_configuration()
    manifest = json.loads(json.dumps(configuration.manifest))
    manifest["evidence"]["capture"]["width"] = 64
    manifest["evidence"]["capture"]["height"] = 64
    return replace(configuration, manifest=manifest)


def create_screenshot_fixture(
    root: Path,
    open_changed_pixels: int,
    closed_again_changed_pixels: int,
) -> dict[str, str]:
    width = 64
    height = 64
    base = bytearray(
        channel
        for y in range(height)
        for x in range(width)
        for channel in (x * 4 % 256, y * 4 % 256, (x + y) * 2 % 256)
    )
    variants: dict[str, bytes] = {}
    left_percent, top_percent, right_percent, bottom_percent = (
        forge_evidence.STORAGE_FIXTURE_REGION_PERCENT
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
    for role in forge_evidence.SCREENSHOTS:
        pixels = bytearray(base)
        changed_pixels = 0
        if role == "open":
            changed_pixels = open_changed_pixels
        elif role == "closed_again":
            changed_pixels = closed_again_changed_pixels
        elif role in {"menu", "reopened"}:
            changed_pixels = 256
        for pixel_index in fixture_pixels[:changed_pixels]:
            pixels[pixel_index * 3] ^= 0xFF
        variants[role] = bytes(pixels)

    screenshots = root / "screenshots"
    screenshots.mkdir()
    assertions: dict[str, str] = {}
    for role, file_name in forge_evidence.SCREENSHOTS.items():
        path = screenshots / file_name
        path.write_bytes(rgb_png(width, height, variants[role]))
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assertions[f"{role}_screenshot"] = (
            f"ScreenshotResult[passed=true, size={path.stat().st_size}, "
            f"sha256={digest}, failure=]"
        )
    return assertions


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
        raise AssertionError(
            f"Expected one test report assertion named {assertion_name}"
        )
    matches[0]["actual"] = actual


def add_report_artifacts(
    configuration: forge_client.ResolvedConfiguration,
    report: dict[str, object],
) -> dict[str, str]:
    digests = {
        "production": "1" * 64,
        "harness": "2" * 64,
    }
    artifacts: list[dict[str, object]] = []
    for index, role in enumerate(forge_client.ARTIFACT_ROLES, start=1):
        spec = forge_client.artifact_spec(configuration, role)
        mod_id = str(spec["mod_id"])
        size = index * 123
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


def create_archive_fixture(
    parent: Path,
) -> tuple[
    forge_client.ResolvedConfiguration,
    Path,
    dict[str, str],
]:
    configuration = small_capture_configuration()
    profile_id = str(forge_client.profile_spec(configuration)["id"])
    archive_root = parent / (
        f"{forge_evidence.SCENARIO_ID}-v"
        f"{forge_evidence.archive_profile_version(profile_id)}"
    )
    reports = archive_root / "reports"
    reports.mkdir(parents=True)
    screenshot_assertions = create_screenshot_fixture(archive_root, 64, 1)
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


class ForgeReportTests(unittest.TestCase):
    def test_accepts_exact_storage_assertion_inventory(self) -> None:
        configuration = forge_client.load_configuration()

        assertions = forge_evidence.validate_report(
            configuration, valid_report(configuration)
        )

        self.assertEqual(forge_evidence.REQUIRED_ASSERTIONS, set(assertions))
        self.assertEqual("true", assertions["forced_save"])

    def test_rejects_missing_restart_assertion(self) -> None:
        configuration = forge_client.load_configuration()
        report = valid_report(configuration)
        report["assertions"] = [
            assertion
            for assertion in report["assertions"]
            if assertion["name"] != "restart_ether_distribution"
        ]

        with self.assertRaisesRegex(forge_client.E2EError, "inventory changed"):
            forge_evidence.validate_report(configuration, report)


class ForgeScreenshotTests(unittest.TestCase):
    def test_requires_open_change_and_closed_return_to_baseline(self) -> None:
        configuration = small_capture_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            assertions = create_screenshot_fixture(root, 64, 1)

            open_ratio, return_ratio, paths = forge_evidence.validate_screenshots(
                configuration, root, assertions
            )

        self.assertEqual(5, len(paths))
        self.assertGreaterEqual(open_ratio, forge_evidence.MINIMUM_OPEN_CHANGED_PIXEL_RATIO)
        self.assertLessEqual(
            return_ratio, forge_evidence.MAXIMUM_CLOSED_RETURN_CHANGED_PIXEL_RATIO
        )

    def test_rejects_closed_again_visual_drift(self) -> None:
        configuration = small_capture_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            assertions = create_screenshot_fixture(root, 64, 32)

            with self.assertRaisesRegex(forge_client.E2EError, "closed baseline"):
                forge_evidence.validate_screenshots(configuration, root, assertions)

    def test_fixture_ratio_ignores_dynamic_background(self) -> None:
        width = 100
        height = 100
        baseline = forge_evidence.PngImage(
            width,
            height,
            bytes((10, 20, 30)) * (width * height),
        )
        changed_pixels = bytearray(baseline.pixels)
        for pixel_index in range(width * 35):
            changed_pixels[pixel_index * 3] = 255
        background_changed = forge_evidence.PngImage(
            width,
            height,
            bytes(changed_pixels),
        )

        self.assertEqual(
            0.0,
            forge_evidence.storage_fixture_changed_pixel_ratio(
                baseline,
                background_changed,
            ),
        )

    def test_rejects_png_header_that_exceeds_decoded_size_bound(self) -> None:
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

            with self.assertRaisesRegex(
                forge_client.E2EError,
                "decoded size bound",
            ):
                forge_evidence.decode_png(path)


class ForgeArtifactEvidenceTests(unittest.TestCase):
    def test_report_artifacts_must_match_locked_hashes(self) -> None:
        configuration = forge_client.load_configuration()
        digest_by_role = {
            "production": "1" * 64,
            "harness": "2" * 64,
        }
        artifacts: dict[str, dict[str, object]] = {}
        report_artifacts: list[dict[str, object]] = []
        assertions: dict[str, str] = {}
        for role in forge_client.ARTIFACT_ROLES:
            spec = forge_client.artifact_spec(configuration, role)
            mod_id = str(spec["mod_id"])
            artifact = {
                "source_kind": "gradle-remap-task",
                "task_path": forge_client.EXPECTED_ARTIFACT_TASKS[role],
                "source_relative_path": "fixture.jar",
                "target_file": spec["file_name"],
                "mod_id": mod_id,
                "version": configuration.properties[str(spec["version_property"])],
                "size": 123,
                "sha256": digest_by_role[role],
                "root_mod_ids": [mod_id],
            }
            artifacts[role] = artifact
            report_artifacts.append(
                {
                    "mod_id": mod_id,
                    "passed": True,
                    "file_name": spec["file_name"],
                    "size": 123,
                    "sha256": digest_by_role[role],
                    "failure": "",
                }
            )
            assertions[f"artifact_{mod_id}"] = (
                f"ForgeArtifactDigest[modId={mod_id}, passed=true, "
                f"fileName={spec['file_name']}, size=123, "
                f"sha256={digest_by_role[role]}, failure=]"
            )
        lock = {
            "schema": 1,
            "profile_id": forge_client.profile_spec(configuration)["id"],
            "managed_by": forge_client.MANAGED_BY,
            "artifact_node": configuration.artifact_lane["artifact_node"],
            "artifacts": artifacts,
        }
        report = {"artifacts": report_artifacts}
        with tempfile.TemporaryDirectory() as temporary_directory, mock.patch.object(
            forge_client, "verify_locked_artifacts"
        ), mock.patch.object(forge_client, "load_artifact_lock", return_value=lock):
            production, harness = forge_evidence.validate_artifacts(
                configuration,
                Path(temporary_directory),
                report,
                assertions,
            )

        self.assertEqual("1" * 64, production)
        self.assertEqual("2" * 64, harness)


class ForgeArchiveEvidenceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        dimensions_patch = mock.patch.object(
            forge_evidence,
            "EXPECTED_FRAMEBUFFER_DIMENSIONS",
            (64, 64),
        )
        dimensions_patch.start()
        self.addCleanup(dimensions_patch.stop)
        self.configuration, self.archive_root, self.digests = create_archive_fixture(
            Path(self.temporary_directory.name)
        )
        self.manifest_path = forge_evidence.write_archive_manifest(
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

    def test_validates_tracked_archive_without_live_runtime(self) -> None:
        with mock.patch.object(
            forge_client,
            "load_configuration",
            side_effect=AssertionError("Archive validation loaded live configuration"),
        ):
            summary = forge_evidence.validate_archived_scenario(self.archive_root)

        manifest = self.read_manifest()

        self.assertEqual(len(forge_evidence.REQUIRED_ASSERTIONS), summary.assertion_count)
        self.assertEqual(len(forge_evidence.SCREENSHOTS), summary.screenshot_count)
        self.assertEqual(self.digests["production"], summary.production_sha256)
        self.assertEqual(self.digests["harness"], summary.harness_sha256)
        self.assertEqual(
            forge_evidence.ARCHIVE_VERIFICATION_SCOPE,
            manifest["verification_scope"],
        )
        self.assertEqual(
            set(forge_evidence.ARCHIVE_PAYLOAD_PATHS),
            set(manifest["files"]),
        )

    def test_rejects_payload_that_differs_from_archive_manifest(self) -> None:
        report_path = self.archive_root / "reports" / "report.json"
        report_path.write_text(
            report_path.read_text(encoding="utf-8") + " ",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "payload differs from its manifest",
        ):
            forge_evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_artifact_hash_that_differs_from_report(self) -> None:
        manifest = self.read_manifest()
        manifest["artifacts"]["production"]["sha256"] = "3" * 64
        self.write_manifest(manifest)

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "production sha256 differs from its provenance",
        ):
            forge_evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_completion_marker_with_wrong_content(self) -> None:
        done_path = self.archive_root / "reports" / "done.marker"
        done_path.write_text("incomplete\n", encoding="utf-8")
        manifest = self.read_manifest()
        manifest["files"]["reports/done.marker"] = {
            "size": done_path.stat().st_size,
            "sha256": forge_evidence.sha256_file(done_path),
        }
        self.write_manifest(manifest)

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "completion marker has unexpected content",
        ):
            forge_evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_profile_identity_that_differs_from_archive_directory(self) -> None:
        manifest = self.read_manifest()
        manifest["profile"]["id"] = "etherology-e2e-forge-1.20.1-v999"
        self.write_manifest(manifest)

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "directory does not identify its recorded profile",
        ):
            forge_evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_corrupt_png_even_when_inventory_hash_is_updated(self) -> None:
        open_path = (
            self.archive_root
            / "screenshots"
            / forge_evidence.SCREENSHOTS["open"]
        )
        content = bytearray(open_path.read_bytes())
        content[-20] ^= 0xFF
        open_path.write_bytes(content)
        manifest = self.read_manifest()
        relative_path = f"screenshots/{open_path.name}"
        manifest["files"][relative_path] = {
            "size": open_path.stat().st_size,
            "sha256": forge_evidence.sha256_file(open_path),
        }
        self.write_manifest(manifest)

        with self.assertRaisesRegex(
            forge_client.E2EError,
            "bad chunk CRC|cannot be decompressed",
        ):
            forge_evidence.validate_archived_scenario(self.archive_root)


if __name__ == "__main__":
    unittest.main()
