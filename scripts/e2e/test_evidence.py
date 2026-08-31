from __future__ import annotations

import binascii
import json
from pathlib import Path
import shutil
import struct
import sys
import tempfile
import unittest
from unittest import mock
import zlib


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import client
import evidence


REPOSITORY_ROOT = SCRIPT_DIRECTORY.parent.parent
ARCHIVED_PHASE_ZERO_FIXTURE = (
    REPOSITORY_ROOT
    / "docs"
    / "evidence"
    / "fabric-1.20.1"
    / "phase0-smoke-v20"
)
HISTORICAL_PHASE_ZERO_V21_FIXTURE = (
    REPOSITORY_ROOT
    / "docs"
    / "evidence"
    / "fabric-1.20.1"
    / "phase0-smoke-v21"
)
CURRENT_PHASE_ZERO_FIXTURE = (
    REPOSITORY_ROOT
    / "docs"
    / "evidence"
    / "fabric-1.20.1"
    / "phase0-smoke-v22"
)
HISTORICAL_PHASE_ZERO_V22_PROFILE = (
    SCRIPT_DIRECTORY / "fabric-1.20.1-profile-v22.json"
)


def png_chunk(chunk_type: bytes, content: bytes) -> bytes:
    crc = binascii.crc32(chunk_type)
    crc = binascii.crc32(content, crc) & 0xFFFFFFFF
    return struct.pack(">I", len(content)) + chunk_type + content + struct.pack(">I", crc)


def rgb_png(width: int, height: int, pixels: bytes) -> bytes:
    if len(pixels) != width * height * 3:
        raise ValueError("RGB fixture has the wrong pixel inventory")
    rows = b"".join(
        b"\x00" + pixels[row * width * 3 : (row + 1) * width * 3]
        for row in range(height)
    )
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"".join(
        (
            evidence.PNG_SIGNATURE,
            png_chunk(b"IHDR", header),
            png_chunk(b"IDAT", zlib.compress(rows)),
            png_chunk(b"IEND", b""),
        )
    )


class PngEvidenceTests(unittest.TestCase):
    def test_decodes_nonblank_rgb_png_and_preserves_pixels(self) -> None:
        width = 64
        height = 32
        pixels = bytes(
            channel
            for y in range(height)
            for x in range(width)
            for channel in (x * 4 % 256, y * 8 % 256, (x + y) * 3 % 256)
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "fixture.png"
            path.write_bytes(rgb_png(width, height, pixels))

            image = evidence.decode_png(path)
            evidence.assert_image_is_not_blank(image, "fixture")

        self.assertEqual((width, height), (image.width, image.height))
        self.assertEqual(pixels, image.pixels)

    def test_rejects_uniform_screenshot(self) -> None:
        image = evidence.PngImage(32, 32, bytes((20, 20, 20)) * (32 * 32))

        with self.assertRaisesRegex(client.E2EError, "blank or near-uniform"):
            evidence.assert_image_is_not_blank(image, "uniform")

    def test_rejects_png_payload_that_expands_beyond_declared_dimensions(
        self,
    ) -> None:
        header = struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)
        compressed_bomb = zlib.compress(b"\x00" * (2 * 1024 * 1024))
        content = b"".join(
            (
                evidence.PNG_SIGNATURE,
                png_chunk(b"IHDR", header),
                png_chunk(b"IDAT", compressed_bomb),
                png_chunk(b"IEND", b""),
            )
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "compression-bomb.png"
            path.write_bytes(content)

            with self.assertRaisesRegex(
                client.E2EError,
                "exceeds its declared dimensions",
            ):
                evidence.decode_png(path)

    def test_rejects_tall_narrow_png_before_decompression(self) -> None:
        header = struct.pack(
            ">IIBBBBB",
            1,
            evidence.MAXIMUM_PNG_DIMENSION + 1,
            8,
            0,
            0,
            0,
            0,
        )
        content = b"".join(
            (
                evidence.PNG_SIGNATURE,
                png_chunk(b"IHDR", header),
                png_chunk(b"IDAT", zlib.compress(b"\x00")),
                png_chunk(b"IEND", b""),
            )
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "tall-narrow.png"
            path.write_bytes(content)

            with self.assertRaisesRegex(client.E2EError, "dimension bound"):
                evidence.decode_png(path)

    def test_changed_pixel_ratio_has_a_declared_threshold(self) -> None:
        left = evidence.PngImage(10, 10, bytes((0, 0, 0)) * 100)
        right_pixels = bytearray(left.pixels)
        for pixel_index in range(10):
            right_pixels[pixel_index * 3] = 255
        right = evidence.PngImage(10, 10, bytes(right_pixels))

        self.assertEqual(0.1, evidence.changed_pixel_ratio(left, right))
        self.assertGreater(0.1, evidence.MINIMUM_CHANGED_PIXEL_RATIO)

    def test_rejects_escaped_screenshot_path(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario_root = Path(temporary_directory)

            with self.assertRaisesRegex(client.E2EError, "unsafe"):
                evidence.safe_screenshot_path(
                    scenario_root,
                    "screenshots/../outside.png",
                )


class ArchivedPhaseZeroEvidenceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.archive_root = (
            Path(self.temporary_directory.name) / ARCHIVED_PHASE_ZERO_FIXTURE.name
        )
        shutil.copytree(ARCHIVED_PHASE_ZERO_FIXTURE, self.archive_root)

    def manifest_path(self) -> Path:
        return self.archive_root / evidence.ARCHIVE_MANIFEST_NAME

    def read_manifest(self) -> dict[str, object]:
        return json.loads(self.manifest_path().read_text(encoding="utf-8"))

    def write_manifest(self, manifest: dict[str, object]) -> None:
        self.manifest_path().write_text(
            json.dumps(manifest, indent=2) + "\n",
            encoding="utf-8",
        )

    def test_validates_the_frozen_archive_without_live_profile_state(self) -> None:
        forbidden_live_call = AssertionError(
            "archive-only validation consulted live profile state"
        )
        with (
            mock.patch.object(
                client,
                "load_configuration",
                side_effect=forbidden_live_call,
            ),
            mock.patch.object(
                evidence,
                "validate_scenario",
                side_effect=forbidden_live_call,
            ),
        ):
            summary = evidence.validate_archived_scenario(self.archive_root)

        self.assertEqual("etherology-e2e-fabric-1.20.1-v20", summary.profile_id)
        self.assertEqual(42, summary.assertion_count)
        self.assertEqual(2, summary.screenshot_count)
        self.assertAlmostEqual(0.9796262538580247, summary.changed_pixel_ratio)
        self.assertEqual(
            "999bf12e166c7d4ea67376373171e486c80779e85e4b281d4bac1f37776a7d37",
            summary.production_sha256,
        )
        self.assertEqual(
            "b5b2542003866351e3e0cb18d5fa1380aa73c460b1ca6a9214b52952bab953ef",
            summary.harness_sha256,
        )

    def test_validates_the_historical_v21_archive(self) -> None:
        summary = evidence.validate_archived_scenario(
            HISTORICAL_PHASE_ZERO_V21_FIXTURE
        )

        self.assertEqual("etherology-e2e-fabric-1.20.1-v21", summary.profile_id)
        self.assertEqual(42, summary.assertion_count)
        self.assertEqual(2, summary.screenshot_count)
        self.assertAlmostEqual(0.9796272183641975, summary.changed_pixel_ratio)
        self.assertEqual(
            "287d0b67e09fadd116905a5588615fae38daf43e22af8cdf0e37546595c38d75",
            summary.production_sha256,
        )

    def test_validates_the_current_v22_archive(self) -> None:
        summary = evidence.validate_archived_scenario(CURRENT_PHASE_ZERO_FIXTURE)

        self.assertEqual("etherology-e2e-fabric-1.20.1-v22", summary.profile_id)
        self.assertEqual(42, summary.assertion_count)
        self.assertEqual(2, summary.screenshot_count)
        self.assertAlmostEqual(0.9796228780864198, summary.changed_pixel_ratio)
        self.assertEqual(
            "5da646a56d326b5ad5492e5ba936758f3c7723f73d6be314cd79d6881fedc1dd",
            summary.production_sha256,
        )
        self.assertEqual(
            "b5b2542003866351e3e0cb18d5fa1380aa73c460b1ca6a9214b52952bab953ef",
            summary.harness_sha256,
        )

    def test_sealing_rejects_linked_owned_state_ancestors_before_capture_access(
        self,
    ) -> None:
        self.manifest_path().unlink()
        active_archive_root = self.archive_root.with_name("phase0-smoke-v22")
        self.archive_root.rename(active_archive_root)
        self.archive_root = active_archive_root
        configuration = client.load_configuration(HISTORICAL_PHASE_ZERO_V22_PROFILE)
        linked_state_error = client.E2EError(
            "E2E state root must not be a symlink"
        )
        with (
            mock.patch.object(
                client,
                "ensure_owned_state_roots",
                side_effect=linked_state_error,
            ) as state_guard,
            mock.patch.object(
                evidence,
                "validate_scenario",
                side_effect=AssertionError("capture runtime was consulted"),
            ),
        ):
            with self.assertRaisesRegex(client.E2EError, "must not be a symlink"):
                evidence.build_archive_manifest(
                    configuration,
                    SCRIPT_DIRECTORY / "fabric-1.20.1-profile.json",
                    client.runtime_root(configuration),
                    self.archive_root,
                )

        state_guard.assert_called_once_with()

    def test_archive_destination_rejects_a_linked_repository_component(
        self,
    ) -> None:
        temporary_repository = Path(self.temporary_directory.name) / "repository"
        evidence_root = temporary_repository / "docs" / "evidence"
        evidence_root.mkdir(parents=True)
        outside = Path(self.temporary_directory.name) / "outside"
        outside.mkdir()
        try:
            (evidence_root / "fabric-1.20.1").symlink_to(
                outside,
                target_is_directory=True,
            )
        except OSError as exception:
            self.skipTest(f"Symbolic links are unavailable: {exception}")
        active_configuration = client.load_configuration()
        configuration = client.ResolvedConfiguration(
            manifest=active_configuration.manifest,
            properties=active_configuration.properties,
            artifact_lane=active_configuration.artifact_lane,
            runtime_lane=active_configuration.runtime_lane,
            installer=active_configuration.installer,
            repository_root=temporary_repository,
        )

        with self.assertRaisesRegex(client.E2EError, "resolves through a symlink"):
            evidence.expected_repository_archive_root(
                configuration,
                "etherology-e2e-fabric-1.20.1-v20",
            )

    def test_sealing_rejects_invalid_artifact_lock_before_scenario_access(
        self,
    ) -> None:
        self.manifest_path().unlink()
        temporary_repository = Path(self.temporary_directory.name) / "repository"
        archive_root = (
            temporary_repository
            / "docs"
            / "evidence"
            / "fabric-1.20.1"
            / "phase0-smoke-v22"
        )
        archive_root.parent.mkdir(parents=True)
        self.archive_root.rename(archive_root)
        profile_manifest = (
            temporary_repository
            / "scripts"
            / "e2e"
            / "fabric-1.20.1-profile.json"
        )
        profile_manifest.parent.mkdir(parents=True)
        shutil.copy2(HISTORICAL_PHASE_ZERO_V22_PROFILE, profile_manifest)
        active_configuration = client.load_configuration(
            HISTORICAL_PHASE_ZERO_V22_PROFILE
        )
        configuration = client.ResolvedConfiguration(
            manifest=active_configuration.manifest,
            properties=active_configuration.properties,
            artifact_lane=active_configuration.artifact_lane,
            runtime_lane=active_configuration.runtime_lane,
            installer=active_configuration.installer,
            repository_root=temporary_repository,
        )
        capture_runtime = (
            temporary_repository
            / "scripts"
            / "e2e"
            / ".state"
            / "runtimes"
            / "etherology-e2e-fabric-1.20.1-v22"
        )
        capture_runtime.mkdir(parents=True)
        lock_error = client.E2EError(
            "The artifact lock does not describe the staged JARs exactly"
        )
        with (
            mock.patch.object(client, "runtime_root", return_value=capture_runtime),
            mock.patch.object(client, "ensure_owned_state_roots"),
            mock.patch.object(
                client,
                "verify_runtime",
                side_effect=lock_error,
            ) as runtime_verifier,
            mock.patch.object(
                evidence,
                "validate_scenario",
                side_effect=AssertionError("scenario evidence was consulted"),
            ),
        ):
            with self.assertRaisesRegex(
                client.E2EError,
                "does not describe the staged JARs exactly",
            ):
                evidence.build_archive_manifest(
                    configuration,
                    profile_manifest,
                    capture_runtime,
                    archive_root,
                )

        runtime_verifier.assert_called_once_with(
            configuration,
            capture_runtime,
            artifact_policy="optional",
        )

    def test_rejects_an_extra_archive_file(self) -> None:
        (self.archive_root / "screenshots" / "unexpected.txt").write_text(
            "unexpected\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(client.E2EError, "archive inventory changed"):
            evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_a_missing_payload(self) -> None:
        (self.archive_root / "reports" / "done.marker").unlink()

        with self.assertRaisesRegex(client.E2EError, "archive inventory changed"):
            evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_a_linked_archive_entry(self) -> None:
        linked_report = self.archive_root / "linked-report.json"
        try:
            linked_report.symlink_to("reports/report.json")
        except OSError as exception:
            self.skipTest(f"Symbolic links are unavailable: {exception}")

        with self.assertRaisesRegex(client.E2EError, "contains a linked entry"):
            evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_an_altered_payload(self) -> None:
        screenshot = (
            self.archive_root
            / "screenshots"
            / "phase0-smoke-title.png"
        )
        content = bytearray(screenshot.read_bytes())
        content[-1] ^= 1
        screenshot.write_bytes(content)

        with self.assertRaisesRegex(
            client.E2EError,
            "payload differs from its manifest",
        ):
            evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_manifest_artifact_provenance_that_differs_from_report(
        self,
    ) -> None:
        manifest = self.read_manifest()
        manifest["artifacts"]["production"]["sha256"] = "0" * 64
        self.write_manifest(manifest)

        with self.assertRaisesRegex(
            client.E2EError,
            "report differs from its manifest contract",
        ):
            evidence.validate_archived_scenario(self.archive_root)

    def test_rejects_profile_provenance_that_differs_from_archive_name(self) -> None:
        manifest = self.read_manifest()
        manifest["profile"]["id"] = "etherology-e2e-fabric-1.20.1-v19"
        self.write_manifest(manifest)

        with self.assertRaisesRegex(
            client.E2EError,
            "directory does not identify its recorded profile",
        ):
            evidence.validate_archived_scenario(self.archive_root)


if __name__ == "__main__":
    unittest.main()
