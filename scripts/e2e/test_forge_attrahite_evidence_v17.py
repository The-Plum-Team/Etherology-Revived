from __future__ import annotations

import copy
from dataclasses import replace
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import consumed_history
import forge_client
import forge_evidence
import forge_attrahite_evidence_v16 as historical_attrahite_evidence
import forge_attrahite_evidence_v17 as attrahite_evidence
from test_forge_evidence import rgb_png


REPOSITORY_ROOT = SCRIPT_DIRECTORY.parent.parent
STATE_RELATIVE_PATH = Path("scripts/e2e/.state")
HISTORICAL_V16_RUNTIME_RELATIVE_PATH = (
    STATE_RELATIVE_PATH
    / "runtimes/etherology-e2e-forge-1.20.1-v16"
)
HISTORICAL_V16_HARNESS_SIZE = 239_915
HISTORICAL_V16_HARNESS_SHA256 = (
    "ffb21364959114d247dfe04cd90c47ba0e7c24572ec0245d02b3a196536dea15"
)
ACCEPTED_V17_RUNTIME_RELATIVE_PATH = (
    STATE_RELATIVE_PATH / "runtimes/etherology-e2e-forge-1.20.1-v17"
)
ACCEPTED_V17_ARCHIVE_RELATIVE_PATH = Path(
    "docs/evidence/forge-1.20.1/attrahite-block-registry-v17"
)
ACCEPTED_V17_ARCHIVE_MANIFEST_SIZE = 2_493
ACCEPTED_V17_ARCHIVE_MANIFEST_SHA256 = (
    "cc4450a4b29b26e87b0d287c623772e077cdff085e279798624f882ee3931752"
)
ACCEPTED_V17_PRODUCTION_SIZE = 1_311_266
ACCEPTED_V17_PRODUCTION_SHA256 = (
    "b2ac29597159c6089a12cfdebd8d8e1c19b9f528cb0b52a29ec829b1c35bc47b"
)
ACCEPTED_V17_RUNTIME_HISTORY = {
    STATE_RELATIVE_PATH / "etherology-e2e-forge-1.20.1-v17-start.attempted": (
        98,
        "e673dad567a2c5e720de60acf5d8ebca08ec5ffd7cb94e12b91680a76ef56baa",
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH / ".etherology-forge-e2e-profile.json": (
        1_478,
        "973fe7fa9185c5eebe1367d9320f3c5ffc127181a4d53629fc3a8c08c98f2b07",
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH / "forge-artifact-lock.json": (
        1_241,
        "cf6a5047d6fb881b828da902fde361c80969692249927c611db1932fa74ce3e0",
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH / "evidence/.etherology-e2e-evidence.json": (
        413,
        "3470fef727fbba0a5ecc9a20d3420ee507e006079fca47bc660ab093448fb881",
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH
    / "evidence/attrahite-block-registry/reports/report.json": (
        33_115,
        "d014611812aa19431339716f91dc963b0c53de04af1987d6ece5dac611c94eb1",
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH
    / "evidence/attrahite-block-registry/reports/done.marker": (
        9,
        "37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1",
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH
    / "evidence/attrahite-block-registry/screenshots/attrahite-block-registry-initial.png": (
        422_839,
        "1acb9186fa06979535066dafdaea5607458326c1f9414c6f285d5d5983b9753d",
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH
    / "evidence/attrahite-block-registry/screenshots/attrahite-block-registry-reopened.png": (
        334_984,
        "865818a3d23bdaf1a30ec54c5374ed6d70bc3171e99237d55ec021ad02feba76",
    ),
    STATE_RELATIVE_PATH / "logs/forge-1.20.1-20260904T071059Z.log": (
        27_871,
        "231128dcce946fb8e76bb498a74776d9827455edc5d2e2bf684c034acf905154",
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH / "game/logs/latest.log": (
        31_749,
        "024f4b29684808d1181b0ec2c3a9826966fdac8a1f04dfb9ee5f0865b7c3f7e8",
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH
    / "game/mods/etherology-forge-e2e-harness.jar": (
        attrahite_evidence.HARNESS_SIZE,
        attrahite_evidence.HARNESS_SHA256,
    ),
    ACCEPTED_V17_RUNTIME_RELATIVE_PATH
    / "game/mods/etherology-forge-under-test.jar": (
        ACCEPTED_V17_PRODUCTION_SIZE,
        ACCEPTED_V17_PRODUCTION_SHA256,
    ),
}
HISTORICAL_V16_FAILURE_HISTORY = {
    (
        STATE_RELATIVE_PATH
        / "etherology-e2e-forge-1.20.1-v16-start.attempted"
    ): (
        98,
        "6f0d8e327a166e5455b0e9a43c8f2e881b34de5509ce4b4c01b893999f1d84dc",
    ),
    (
        HISTORICAL_V16_RUNTIME_RELATIVE_PATH
        / "evidence/attrahite-block-registry/reports/report.json"
    ): (
        29_563,
        "3fd214d1b1a2d6ecf8a22cbcbbe7d69a8ce4155c6e7f349a5815627cf59ef7b0",
    ),
    (
        HISTORICAL_V16_RUNTIME_RELATIVE_PATH
        / "evidence/attrahite-block-registry/reports/done.marker"
    ): (
        9,
        "37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1",
    ),
    (
        STATE_RELATIVE_PATH
        / "logs/forge-1.20.1-20260901T021931Z.log"
    ): (
        24_016,
        "4872d8185aab5f4a0a0c3a7cbcb85ae6beda1fed5b51c2ca0f46c9246445e2ab",
    ),
    HISTORICAL_V16_RUNTIME_RELATIVE_PATH / "game/logs/latest.log": (
        26_830,
        "a38fe80ae969837c16c4ecd35db22d3eb4295864868859705ddea43084fb4b50",
    ),
    (
        HISTORICAL_V16_RUNTIME_RELATIVE_PATH
        / "game/mods/etherology-forge-e2e-harness.jar"
    ): (
        HISTORICAL_V16_HARNESS_SIZE,
        HISTORICAL_V16_HARNESS_SHA256,
    ),
}


def validate_consumed_v16_failure_history(repository_root: Path) -> None:
    consumed_history.validate_files(
        repository_root,
        label="v16 failure-history",
        state_relative_path=STATE_RELATIVE_PATH,
        runtime_relative_path=HISTORICAL_V16_RUNTIME_RELATIVE_PATH,
        history=HISTORICAL_V16_FAILURE_HISTORY,
    )


def validate_consumed_v17_runtime_history(repository_root: Path) -> None:
    validated_paths = consumed_history.validate_files(
        repository_root,
        label="v17 runtime-history",
        state_relative_path=STATE_RELATIVE_PATH,
        runtime_relative_path=ACCEPTED_V17_RUNTIME_RELATIVE_PATH,
        history=ACCEPTED_V17_RUNTIME_HISTORY,
    )
    if validated_paths is None:
        return
    state_root, runtime = validated_paths
    report = forge_evidence.require_json_object(
        runtime / "evidence/attrahite-block-registry/reports/report.json",
        "Consumed Forge v17 Attrahite report",
    )
    if report.get("status") != "passed" or report.get("passed") is not True:
        raise AssertionError("The consumed v17 report is not a passing report")
    if report.get("client_ticks") != 378:
        raise AssertionError("The consumed v17 report client-tick count changed")
    assertions = report.get("assertions")
    if (
        not isinstance(assertions, list)
        or len(assertions) != 91
        or any(
            not isinstance(item, dict) or item.get("passed") is not True
            for item in assertions
        )
    ):
        raise AssertionError("The consumed v17 assertion inventory changed")
    attrahite = report.get("attrahite")
    if (
        not isinstance(attrahite, dict)
        or attrahite.get("persistence_exact") is not True
        or attrahite.get("reopened_data_exact") is not True
    ):
        raise AssertionError("The consumed v17 persistence result changed")
    for path in (
        state_root / "logs/forge-1.20.1-20260904T071059Z.log",
        runtime / "game/logs/latest.log",
    ):
        if not path.read_text(encoding="utf-8").rstrip().endswith(
            "All dimensions are saved"
        ):
            raise AssertionError(
                f"The consumed v17 log did not shut down cleanly: {path}"
            )


def patterned_pixels(variant: int = 0) -> bytes:
    width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    pattern = b"".join(
        bytes(
            (
                24 + ((index + variant * 31) % 112),
                88 + ((index * 3 + variant * 17) % 88),
                32 + ((index * 5 + variant * 13) % 96),
            )
        )
        for index in range(256)
    )
    byte_count = width * height * 3
    return (pattern * ((byte_count + len(pattern) - 1) // len(pattern)))[:byte_count]


def write_patterned_png(path: Path, variant: int = 0) -> None:
    width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    path.write_bytes(rgb_png(width, height, patterned_pixels(variant)))


def fixed_gallery_pixels(gallery_luminance: int) -> bytes:
    """Keeps sky and geometry fixed while varying only flat gallery lighting."""

    width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    left = width * 20 // 100
    right = width * 80 // 100
    top = height * 35 // 100
    bottom = height * 85 // 100
    pixels = bytearray()
    for y in range(height):
        background = 80 + y % 64
        pixels.extend(bytes((background, background, background)) * width)
    for y in range(height * 15 // 100, top):
        for x in range(width):
            offset = (y * width + x) * 3
            shade = y % 64
            pixels[offset : offset + 3] = bytes((96, 144 + shade, 184 + shade))
    for y in range(top, bottom):
        for x in range(left, right):
            offset = (y * width + x) * 3
            value = gallery_luminance
            if (
                width * 30 // 100 <= x < width * 38 // 100
                or width * 46 // 100 <= x < width * 54 // 100
                or width * 62 // 100 <= x < width * 70 // 100
            ) and height * 48 // 100 <= y < height * 72 // 100:
                value = min(255, gallery_luminance + 40)
            pixels[offset : offset + 3] = bytes((value, value, value))
    return bytes(pixels)


def write_fixed_gallery_png(path: Path, gallery_luminance: int) -> None:
    width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    path.write_bytes(rgb_png(width, height, fixed_gallery_pixels(gallery_luminance)))


def screenshot_record(
    scenario_root: Path, index: int
) -> dict[str, object]:
    step, relative_path = attrahite_evidence.EXPECTED_SCREENSHOTS[index]
    path = scenario_root / relative_path
    return {
        "step": step,
        "role": "host",
        "file": relative_path,
        "width": 1920,
        "height": 1080,
        "size": path.stat().st_size,
        "sha256": forge_evidence.sha256_file(path),
        "completed_render_count": attrahite_evidence.REQUIRED_STABLE_RENDERS,
        "source": "minecraft-framebuffer",
        "edited": False,
    }


def artifact_records() -> list[dict[str, object]]:
    return [
        {
            "mod_id": "etherology",
            "passed": True,
            "file_name": "etherology-forge-under-test.jar",
            "size": 2_881_337,
            "sha256": "1" * 64,
            "failure": "",
        },
        {
            "mod_id": "etherology_e2e_harness",
            "passed": True,
            "file_name": "etherology-forge-e2e-harness.jar",
            "size": attrahite_evidence.HARNESS_SIZE,
            "sha256": attrahite_evidence.HARNESS_SHA256,
            "failure": "",
        },
    ]


def build_report(scenario_root: Path) -> dict[str, object]:
    screenshots_directory = scenario_root / "screenshots"
    reports_directory = scenario_root / "reports"
    screenshots_directory.mkdir(parents=True, exist_ok=True)
    reports_directory.mkdir(parents=True, exist_ok=True)
    for file_name in attrahite_evidence.SCREENSHOT_FILES:
        write_patterned_png(screenshots_directory / file_name)
    screenshots = [
        screenshot_record(scenario_root, index)
        for index in range(len(attrahite_evidence.EXPECTED_SCREENSHOTS))
    ]
    report: dict[str, object] = {
        "schema": 3,
        "scenario": attrahite_evidence.SCENARIO_ID,
        "profile_id": attrahite_evidence.PROFILE_ID,
        "profile_manifest_size": attrahite_evidence.PROFILE_SIZE,
        "profile_manifest_sha256": attrahite_evidence.PROFILE_SHA256,
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "lane": "forge-1.20.1",
        "role": "host",
        "status": "passed",
        "passed": True,
        "client_ticks": 1_024,
        "lifecycle_failure": "",
        "assertions": [],
        "world": copy.deepcopy(attrahite_evidence.EXPECTED_WORLD),
        "ready_resources": list(attrahite_evidence.EXPECTED_READY_RESOURCES),
        "artifacts": artifact_records(),
        "screenshots": screenshots,
        "attrahite": {
            "fixtures": list(attrahite_evidence.EXPECTED_FIXTURES),
            "loot_tables": list(attrahite_evidence.EXPECTED_LOOT_TABLE_IDS),
            "standard_loot": attrahite_evidence.STANDARD_LOOT,
            "raw_plain_loot": attrahite_evidence.RAW_PLAIN_LOOT,
            "raw_silk_touch_loot": attrahite_evidence.RAW_SILK_LOOT,
            "raw_fortune_loot": attrahite_evidence.RAW_FORTUNE_LOOT,
            "recipes": list(attrahite_evidence.EXPECTED_RECIPE_IDS),
            "advancements": list(attrahite_evidence.EXPECTED_ADVANCEMENT_IDS),
            "placements": attrahite_evidence.EXPECTED_PLACEMENTS,
            "persistence_exact": True,
            "reopened_data_exact": True,
            "required_stable_renders": 120,
            "pre_setup_lighting": {
                "stable_client_ticks": 20,
                "arena_chunks_loaded": True,
                "chunk_updaters_empty": True,
                "pending": False,
                "enabled_columns": copy.deepcopy(
                    attrahite_evidence.EXPECTED_ENABLED_LIGHT_COLUMNS
                ),
                "sky": copy.deepcopy(
                    attrahite_evidence.EXPECTED_PRE_SETUP_SKY_LIGHT
                ),
            },
        },
    }
    assertion_evidence = attrahite_evidence.expected_assertion_evidence(screenshots)
    assertions: list[dict[str, object]] = []
    next_network_id = 500
    for name in attrahite_evidence.EXPECTED_ASSERTION_NAMES:
        if name.startswith("default_state_network_id:"):
            expected, actual = "non-negative", str(next_network_id)
            next_network_id += 1
        else:
            expected, actual = assertion_evidence[name]
        assertions.append(
            {
                "name": name,
                "passed": True,
                "expected": expected,
                "actual": actual,
            }
        )
    report["assertions"] = assertions
    return report


def write_report_and_marker(
    scenario_root: Path, report: dict[str, object]
) -> None:
    reports = scenario_root / "reports"
    (reports / "report.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )
    (reports / "done.marker").write_text("complete\n", encoding="utf-8")


def assertion(
    report: dict[str, object], name: str
) -> dict[str, object]:
    assertions = report["assertions"]
    if not isinstance(assertions, list):
        raise AssertionError("fixture assertions changed")
    return next(value for value in assertions if value["name"] == name)


def refresh_screenshot(
    scenario_root: Path, report: dict[str, object], index: int
) -> None:
    screenshots = report["screenshots"]
    if not isinstance(screenshots, list):
        raise AssertionError("fixture screenshots changed")
    screenshots[index] = screenshot_record(scenario_root, index)
    phase = attrahite_evidence.PHASES[index]
    screenshot_assertion = assertion(report, f"native_screenshot_written:{phase}")
    screenshot_assertion["actual"] = (
        f"{screenshots[index]['size']} bytes, "
        f"sha256={screenshots[index]['sha256']}"
    )


def archived_artifacts(report: dict[str, object]) -> dict[str, dict[str, object]]:
    return {
        role: {
            "mod_id": artifact["mod_id"],
            "file_name": artifact["file_name"],
            "size": artifact["size"],
            "sha256": artifact["sha256"],
        }
        for artifact, (role, _mod_id, _file_name) in zip(
            report["artifacts"],
            attrahite_evidence.EXPECTED_ARTIFACTS,
            strict=True,
        )
    }


def reversed_artifact_lock(
    report_artifacts: dict[str, dict[str, object]],
) -> dict[str, object]:
    return {
        "schema": 1,
        "profile_id": attrahite_evidence.PROFILE_ID,
        "artifact_node": "forge-1.20.1",
        "artifacts": {
            role: {
                "mod_id": record["mod_id"],
                "target_file": record["file_name"],
                "size": record["size"],
                "sha256": record["sha256"],
            }
            for role, record in reversed(tuple(report_artifacts.items()))
        },
    }


def build_archive_manifest(
    archive_root: Path, report: dict[str, object]
) -> dict[str, object]:
    capture_mtime_ns = {
        relative_path: 1_000_000_000 + index
        for index, relative_path in enumerate(attrahite_evidence.ARCHIVE_PAYLOAD_PATHS)
    }
    capture_mtime_ns["reports/done.marker"] = 2_000_000_000
    return {
        "schema": 1,
        "kind": attrahite_evidence.ARCHIVE_KIND,
        "verification_scope": attrahite_evidence.ARCHIVE_VERIFICATION_SCOPE,
        "scenario": attrahite_evidence.SCENARIO_ID,
        "profile": {
            "id": attrahite_evidence.PROFILE_ID,
            "manifest_path": attrahite_evidence.ACTIVE_PROFILE_RELATIVE_PATH,
            "manifest_size": attrahite_evidence.PROFILE_SIZE,
            "manifest_sha256": attrahite_evidence.PROFILE_SHA256,
        },
        "runtime": {
            "artifact_node": "forge-1.20.1",
            "minecraft": "1.20.1",
            "loader": "forge",
            "loader_version": "47.4.9",
            "java": 17,
            "capture_kind": "composed-minecraft-framebuffer",
            "framebuffer_width": 1920,
            "framebuffer_height": 1080,
        },
        "publication": {
            **attrahite_evidence.ARCHIVE_PUBLICATION_ATTESTATION,
            "capture_mtime_ns": capture_mtime_ns,
        },
        "capture_metadata": {
            "path": attrahite_evidence.ARCHIVE_CAPTURE_METADATA_PATH,
            "size": 1_337,
            "sha256": "3" * 64,
        },
        "assertion_count": len(attrahite_evidence.EXPECTED_ASSERTION_NAMES),
        "screenshot_count": len(attrahite_evidence.EXPECTED_SCREENSHOTS),
        "artifacts": archived_artifacts(report),
        "files": {
            relative_path: {
                "size": (archive_root / relative_path).stat().st_size,
                "sha256": forge_evidence.sha256_file(archive_root / relative_path),
            }
            for relative_path in attrahite_evidence.ARCHIVE_PAYLOAD_PATHS
        },
    }


class ActiveProfileTests(unittest.TestCase):
    def test_v17_profile_snapshot_is_byte_exact(self) -> None:
        snapshot = SCRIPT_DIRECTORY / "forge-1.20.1-profile-v17.json"
        self.assertEqual(
            snapshot.stat().st_size,
            attrahite_evidence.PROFILE_SIZE,
        )
        self.assertEqual(
            attrahite_evidence.PROFILE_SHA256,
            forge_client.sha256_file(snapshot),
        )

    def test_accepted_v17_archive_is_byte_exact_and_self_contained(self) -> None:
        archive = REPOSITORY_ROOT / ACCEPTED_V17_ARCHIVE_RELATIVE_PATH
        manifest = archive / attrahite_evidence.ARCHIVE_MANIFEST_NAME
        self.assertEqual(
            manifest.stat().st_size,
            ACCEPTED_V17_ARCHIVE_MANIFEST_SIZE,
        )
        self.assertEqual(
            forge_evidence.sha256_file(manifest),
            ACCEPTED_V17_ARCHIVE_MANIFEST_SHA256,
        )
        summary = attrahite_evidence.validate_archived_evidence(archive)
        self.assertEqual(summary.profile_id, attrahite_evidence.PROFILE_ID)
        self.assertEqual(summary.assertion_count, 91)
        self.assertEqual(summary.screenshot_count, 2)
        self.assertEqual(
            summary.reopen_changed_pixel_ratio,
            0.2507860725308642,
        )
        self.assertEqual(
            summary.production_sha256,
            ACCEPTED_V17_PRODUCTION_SHA256,
        )
        self.assertEqual(
            summary.harness_sha256,
            attrahite_evidence.HARNESS_SHA256,
        )

    def test_consumed_v17_runtime_history_remains_exact_when_present(self) -> None:
        validate_consumed_v17_runtime_history(REPOSITORY_ROOT)

    def test_capture_lock_inventory_is_exact_without_object_order_semantics(
        self,
    ) -> None:
        reported = archived_artifacts({"artifacts": artifact_records()})
        lock = reversed_artifact_lock(reported)
        with tempfile.TemporaryDirectory() as temporary_directory:
            lock_path = Path(temporary_directory) / "artifact-lock.json"
            lock_path.write_text("{}\n", encoding="utf-8")
            with mock.patch.object(
                forge_client,
                "load_artifact_lock",
                return_value=lock,
            ):
                with mock.patch.object(
                    forge_client,
                    "artifact_lock_path",
                    return_value=lock_path,
                ):
                    self.assertEqual(
                        attrahite_evidence.validate_capture_artifact_lock(
                            object(), Path(temporary_directory), reported
                        ),
                        lock_path,
                    )
                    lock["artifacts"]["foreign"] = {}
                    with self.assertRaises(forge_client.E2EError):
                        attrahite_evidence.validate_capture_artifact_lock(
                            object(), Path(temporary_directory), reported
                        )

    def test_live_lock_inventory_is_exact_without_object_order_semantics(
        self,
    ) -> None:
        reported = archived_artifacts({"artifacts": artifact_records()})
        lock = reversed_artifact_lock(reported)
        configuration = object()
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory)
            with mock.patch.object(
                forge_client,
                "verify_locked_artifacts",
            ) as verify_locked_artifacts:
                with mock.patch.object(
                    forge_client,
                    "load_artifact_lock",
                    return_value=lock,
                ):
                    self.assertEqual(
                        attrahite_evidence.validate_live_artifacts(
                            configuration,
                            runtime,
                            reported,
                        ),
                        (
                            str(reported["production"]["sha256"]),
                            str(reported["harness"]["sha256"]),
                        ),
                    )
                    verify_locked_artifacts.assert_called_once_with(
                        configuration,
                        runtime,
                        verify_source=False,
                    )
                    lock["artifacts"]["foreign"] = {}
                    with self.assertRaises(forge_client.E2EError):
                        attrahite_evidence.validate_live_artifacts(
                            configuration,
                            runtime,
                            reported,
                        )

    def test_rejects_a_linked_v17_snapshot(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            active = (
                repository_root / attrahite_evidence.ACTIVE_PROFILE_RELATIVE_PATH
            )
            snapshot = (
                repository_root / attrahite_evidence.SNAPSHOT_PROFILE_RELATIVE_PATH
            )
            active.parent.mkdir(parents=True)
            active.write_bytes(configuration.profile_manifest_path.read_bytes())
            snapshot.symlink_to(active.name)
            linked_configuration = replace(
                configuration,
                repository_root=repository_root,
            )

            with self.assertRaises(forge_client.E2EError):
                attrahite_evidence.validate_active_profile(linked_configuration)

    def test_v11_through_v16_profiles_remain_byte_exact(self) -> None:
        expected = {
            11: (
                3644,
                "af21ba7cbf1ba71f06a1dc2594daa5aa4a790ee89df3ed560760ceb1b6aa8e6f",
            ),
            12: (
                3668,
                "c23a2a905e40c721cda1d45086064667aacd568489a319eef4ce30e153a2a8d7",
            ),
            13: (
                3668,
                "0e00a169d9e9387747b9cdf1d2d682b4646b731e2244775d676794f6cc2405c6",
            ),
            14: (
                3702,
                "d880c523c6987836cfad5dfe9d640b1d4ee807664f3fc335ae5b31b6fbfe1e44",
            ),
            15: (
                3702,
                "7744609bfdc40ca69e86fb4e2d6bb4e2755d9072097acde54b7d5dd9c0537e71",
            ),
            16: (
                3702,
                "05162c31a5effae1efc3b1191a4dadb7f0ba5333fa993508bb1ee3ffecde7535",
            ),
        }
        for version, (size, digest) in expected.items():
            path = SCRIPT_DIRECTORY / f"forge-1.20.1-profile-v{version}.json"
            self.assertTrue(path.is_file())
            self.assertFalse(path.is_symlink())
            self.assertEqual(size, path.stat().st_size)
            self.assertEqual(digest, forge_evidence.sha256_file(path))

    def test_v14_prepared_history_verifier_and_tests_remain_byte_exact(self) -> None:
        expected = {
            "forge_attrahite_evidence_v14.py": (
                62748,
                "17a09e70b3044bfd1db4602bc82eb30cb96bac46713f6646dc234e8ea97da073",
            ),
            "test_forge_attrahite_evidence_v14.py": (
                24655,
                "96cbf93a079267802c93d5e7c3e92676a10d070d63f7e9a2cf16776a7945bc09",
            ),
        }
        for file_name, (size, digest) in expected.items():
            path = SCRIPT_DIRECTORY / file_name
            self.assertTrue(path.is_file())
            self.assertFalse(path.is_symlink())
            self.assertEqual(size, path.stat().st_size)
            self.assertEqual(digest, forge_evidence.sha256_file(path))

    def test_historical_v16_verifier_rejects_active_v17_profile(self) -> None:
        configuration = forge_client.load_configuration()

        with self.assertRaisesRegex(forge_client.E2EError, "profile bytes changed"):
            historical_attrahite_evidence.validate_active_profile(configuration)

    def test_v15_consumed_history_verifier_and_tests_remain_byte_exact(self) -> None:
        expected = {
            "forge_attrahite_evidence_v15.py": (
                67_883,
                "8ac1e8f9ddb0d86bc8c08ac1ed23b340b49afb20b5d9cf1ce7d75c47072f05b1",
            ),
            "test_forge_attrahite_evidence_v15.py": (
                29_526,
                "a5426c2bfa2e84e3d3a397e122040f8a3ab2a4fedc5dc46e85c4ab096e9a672a",
            ),
        }
        for file_name, (size, digest) in expected.items():
            path = SCRIPT_DIRECTORY / file_name
            self.assertTrue(path.is_file())
            self.assertFalse(path.is_symlink())
            self.assertEqual(size, path.stat().st_size)
            self.assertEqual(digest, forge_evidence.sha256_file(path))

    def test_consumed_v15_startup_failure_remains_byte_exact_when_present(
        self,
    ) -> None:
        runtime_relative_path = (
            STATE_RELATIVE_PATH
            / "runtimes/etherology-e2e-forge-1.20.1-v15"
        )
        expected = {
            STATE_RELATIVE_PATH
            / "etherology-e2e-forge-1.20.1-v15-start.attempted": (
                98,
                "4158b59e30bd99e6d82e845e1d0bd7e73c29b97f895c0cc1dd25f37be1f03394",
            ),
            STATE_RELATIVE_PATH / "logs/forge-1.20.1-20260901T014800Z.log": (
                22_197,
                "28d8a09b311286cb7234aae931c37d317798db946a95286babdd0269151be769",
            ),
            runtime_relative_path / "game/logs/latest.log": (
                22_405,
                "719885a86267fcb80fb7f8217a15cb3e78d564f06738c3448f22bfd5044c36b2",
            ),
        }
        validated_paths = consumed_history.validate_files(
            REPOSITORY_ROOT,
            label="v15 startup-failure history",
            state_relative_path=STATE_RELATIVE_PATH,
            runtime_relative_path=runtime_relative_path,
            history=expected,
        )
        if validated_paths is None:
            return
        _, runtime = validated_paths
        scenario_root = runtime / "evidence/attrahite-block-registry"
        for directory_name in ("reports", "screenshots"):
            directory = scenario_root / directory_name
            with self.subTest(directory=directory):
                self.assertTrue(directory.is_dir())
                self.assertFalse(directory.is_symlink())
                self.assertEqual([], list(directory.iterdir()))

    def test_v16_consumed_history_verifier_and_tests_remain_byte_exact(self) -> None:
        expected = {
            "forge_attrahite_evidence_v16.py": (
                67_899,
                "5cf6a02795916e92013254e89baac4655fe64497c61ce3605408e2babf1d8163",
            ),
            "test_forge_attrahite_evidence_v16.py": (
                33_066,
                "ea29391102e3681729b226e10c5e57b58153b12fad1c52c10a16c7d80c2800a8",
            ),
        }
        for file_name, (size, digest) in expected.items():
            path = SCRIPT_DIRECTORY / file_name
            self.assertTrue(path.is_file())
            self.assertFalse(path.is_symlink())
            self.assertEqual(size, path.stat().st_size)
            self.assertEqual(digest, forge_evidence.sha256_file(path))

    def test_historical_v16_harness_pin_remains_explicit(self) -> None:
        self.assertEqual(
            HISTORICAL_V16_HARNESS_SIZE,
            historical_attrahite_evidence.HARNESS_SIZE,
        )
        self.assertEqual(
            HISTORICAL_V16_HARNESS_SHA256,
            historical_attrahite_evidence.HARNESS_SHA256,
        )

    def test_consumed_v16_failure_history_remains_byte_exact_when_present(
        self,
    ) -> None:
        validate_consumed_v16_failure_history(REPOSITORY_ROOT)

    def test_consumed_histories_reject_dangling_leaf_symlinks(self) -> None:
        cases = (
            (
                "v16",
                HISTORICAL_V16_FAILURE_HISTORY,
                validate_consumed_v16_failure_history,
            ),
            (
                "v17",
                ACCEPTED_V17_RUNTIME_HISTORY,
                validate_consumed_v17_runtime_history,
            ),
        )
        for label, history, validator in cases:
            with self.subTest(label=label):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository_root = Path(temporary_directory)
                    missing_targets = repository_root / "missing-targets"
                    for index, relative_path in enumerate(history):
                        artifact = repository_root / relative_path
                        artifact.parent.mkdir(parents=True, exist_ok=True)
                        artifact.symlink_to(missing_targets / str(index))

                    with self.assertRaisesRegex(AssertionError, "missing or linked"):
                        validator(repository_root)

    def test_consumed_histories_reject_linked_parent_components(self) -> None:
        validators = (
            ("v16", validate_consumed_v16_failure_history),
            ("v17", validate_consumed_v17_runtime_history),
        )
        for label, validator in validators:
            for parent_name in ("runtimes", "logs"):
                with self.subTest(label=label, parent_name=parent_name):
                    with tempfile.TemporaryDirectory() as temporary_directory:
                        repository_root = Path(temporary_directory)
                        state_root = (
                            repository_root / STATE_RELATIVE_PATH
                        )
                        state_root.mkdir(parents=True)
                        linked_target = repository_root / f"linked-{parent_name}"
                        linked_target.mkdir()
                        (state_root / parent_name).symlink_to(linked_target)

                        with self.assertRaisesRegex(
                            AssertionError,
                            "parent is linked",
                        ):
                            validator(repository_root)

    def test_consumed_history_rejects_artifacts_outside_state_root(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            with self.assertRaisesRegex(AssertionError, "escapes its state root"):
                consumed_history.validate_files(
                    repository_root,
                    label="fixture",
                    state_relative_path=STATE_RELATIVE_PATH,
                    runtime_relative_path=ACCEPTED_V17_RUNTIME_RELATIVE_PATH,
                    history={Path("outside.log"): (0, "0" * 64)},
                )

    def test_consumed_v16_failure_fingerprint_is_preserved_when_present(
        self,
    ) -> None:
        report_path = (
            REPOSITORY_ROOT
            / HISTORICAL_V16_RUNTIME_RELATIVE_PATH
            / "evidence/attrahite-block-registry/reports/report.json"
        )
        if not report_path.exists():
            return

        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual("failed", report["status"])
        self.assertFalse(report["passed"])
        self.assertEqual(6_086, report["client_ticks"])
        self.assertEqual(
            "Timed out in WAITING_FOR_CLIENT_MIRROR after 6000 client ticks",
            report["lifecycle_failure"],
        )
        self.assertEqual(89, len(report["assertions"]))
        self.assertEqual(72, sum(row["passed"] for row in report["assertions"]))
        self.assertEqual([], report["screenshots"])
        lighting = next(
            row
            for row in report["assertions"]
            if row["name"] == "capture_lighting_ready:initial"
        )["actual"]
        self.assertIn("stableServerTicks=20;stableClientTicks=0", lighting)
        self.assertIn("serverPending=false;clientPending=false", lighting)
        self.assertIn("0,121,-8=15/14", lighting)
        self.assertIn("0,121,-8=0/0", lighting)

    def test_canonical_resource_bytes_are_pinned(self) -> None:
        attrahite_evidence.validate_canonical_resources(REPOSITORY_ROOT)
        self.assertEqual(
            tuple(attrahite_evidence.EXPECTED_RESOURCE_SHA256),
            attrahite_evidence.EXPECTED_READY_RESOURCES,
        )

    def test_assertion_inventory_matches_harness_contract(self) -> None:
        self.assertEqual(91, len(attrahite_evidence.EXPECTED_ASSERTION_NAMES))
        self.assertEqual(
            "isolated_save_directory_present",
            attrahite_evidence.EXPECTED_ASSERTION_NAMES[-1],
        )


class ReportContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.scenario_root = Path(self.temporary_directory.name)
        self.report = build_report(self.scenario_root)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def validate(self) -> float:
        ratio, _artifacts = attrahite_evidence.validate_report_contract(
            self.scenario_root, self.report
        )
        return ratio

    def test_accepts_exact_registry_data_restart_and_visual_contract(self) -> None:
        self.assertEqual(0.0, self.validate())

    def test_rejects_pre_setup_light_payload_readiness_drift(self) -> None:
        def reorder_enabled_columns(report: dict[str, object]) -> None:
            pre_setup = report["attrahite"]["pre_setup_lighting"]
            pre_setup["enabled_columns"] = dict(
                reversed(tuple(pre_setup["enabled_columns"].items()))
            )

        def reorder_pre_setup_fields(report: dict[str, object]) -> None:
            pre_setup = report["attrahite"]["pre_setup_lighting"]
            report["attrahite"]["pre_setup_lighting"] = dict(
                reversed(tuple(pre_setup.items()))
            )

        mutations = (
            lambda report: report["attrahite"]["pre_setup_lighting"].__setitem__(
                "stable_client_ticks", 19
            ),
            lambda report: report["attrahite"]["pre_setup_lighting"].__setitem__(
                "arena_chunks_loaded", False
            ),
            lambda report: report["attrahite"]["pre_setup_lighting"].__setitem__(
                "chunk_updaters_empty", False
            ),
            lambda report: report["attrahite"]["pre_setup_lighting"].__setitem__(
                "pending", True
            ),
            lambda report: report["attrahite"]["pre_setup_lighting"][
                "enabled_columns"
            ].__setitem__("0,-1", False),
            lambda report: report["attrahite"]["pre_setup_lighting"][
                "sky"
            ].__setitem__("3,123,1", 14),
            reorder_enabled_columns,
            reorder_pre_setup_fields,
            lambda report: report["attrahite"]["pre_setup_lighting"].__setitem__(
                "unexpected", True
            ),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                changed = copy.deepcopy(self.report)
                mutate(changed)
                with self.assertRaises(forge_client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )

    def test_pending_lighting_status_is_diagnostic_but_samples_are_strict(self) -> None:
        for phase in attrahite_evidence.PHASES:
            assertion(
                self.report,
                f"capture_lighting_ready:{phase}",
            )["actual"] = attrahite_evidence.lighting_assertion_actual(True, True)
        self.assertEqual(0.0, self.validate())

        assertion(
            self.report,
            "capture_lighting_ready:initial",
        )["actual"] = attrahite_evidence.lighting_assertion_actual(
            True,
            True,
        ).replace("15/14", "15/13", 1)
        with self.assertRaisesRegex(forge_client.E2EError, "lighting evidence"):
            self.validate()

    def test_rejects_top_level_schema_identity_and_field_drift(self) -> None:
        mutations = (
            lambda report: report.__setitem__("schema", 2),
            lambda report: report.__setitem__("artifact_node", "forge-1.20.2"),
            lambda report: report.__setitem__("profile_id", "wrong"),
            lambda report: report.__setitem__("profile_manifest_size", 1),
            lambda report: report.__setitem__("profile_manifest_sha256", "0" * 64),
            lambda report: report.__setitem__("loader_version", "47.4.8"),
            lambda report: report.__setitem__("passed", False),
            lambda report: report.__setitem__("status", "failed"),
            lambda report: report.__setitem__("unexpected", True),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                changed = copy.deepcopy(self.report)
                mutate(changed)
                with self.assertRaises(forge_client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )

    def test_rejects_world_ready_resource_and_mechanics_drift(self) -> None:
        mutations = (
            lambda report: report["world"].__setitem__("reopened", False),
            lambda report: report["ready_resources"].reverse(),
            lambda report: report["attrahite"].__setitem__(
                "raw_fortune_loot", "wrong"
            ),
            lambda report: report["attrahite"]["recipes"].pop(),
            lambda report: report["attrahite"]["advancements"].reverse(),
            lambda report: report["attrahite"]["loot_tables"].pop(),
            lambda report: report["attrahite"].__setitem__(
                "placements", "accepted=true"
            ),
            lambda report: report["attrahite"].__setitem__(
                "persistence_exact", False
            ),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                changed = copy.deepcopy(self.report)
                mutate(changed)
                with self.assertRaises(forge_client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )

    def test_rejects_assertion_order_failure_and_semantic_drift(self) -> None:
        mutations = (
            lambda report: report["assertions"].reverse(),
            lambda report: report["assertions"][0].__setitem__("passed", False),
            lambda report: assertion(
                report, "raw_silk_touch_drop_exact"
            ).__setitem__("actual", "none"),
            lambda report: assertion(
                report, "runtime:block_class:etherology:attrahite"
            ).__setitem__("actual", "net.minecraft.world.level.block.SlabBlock"),
            lambda report: assertion(
                report, "capture_camera_exact:reopened"
            ).__setitem__("actual", "wrong"),
            lambda report: assertion(
                report, "client_arena_chunks_loaded_before_setup"
            ).__setitem__("actual", "false"),
            lambda report: assertion(
                report, "client_arena_light_payloads_applied_before_setup"
            ).__setitem__("actual", "stableClientTicks=19"),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                changed = copy.deepcopy(self.report)
                mutate(changed)
                with self.assertRaises(forge_client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )

    def test_rejects_duplicate_negative_and_malformed_network_ids(self) -> None:
        names = [
            name
            for name in attrahite_evidence.EXPECTED_ASSERTION_NAMES
            if name.startswith("default_state_network_id:")
        ]
        for actual in ("-1", "+1", "01", "one"):
            with self.subTest(actual=actual):
                changed = copy.deepcopy(self.report)
                assertion(changed, names[0])["actual"] = actual
                with self.assertRaises(forge_client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )
        changed = copy.deepcopy(self.report)
        assertion(changed, names[1])["actual"] = assertion(changed, names[0])["actual"]
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_report_contract(self.scenario_root, changed)

    def test_rejects_unpinned_or_reordered_harness_artifact(self) -> None:
        mutations = (
            lambda report: report["artifacts"][1].__setitem__("size", 1),
            lambda report: report["artifacts"][1].__setitem__("sha256", "2" * 64),
            lambda report: report["artifacts"].reverse(),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                changed = copy.deepcopy(self.report)
                mutate(changed)
                with self.assertRaises(forge_client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )

    def test_rejects_screenshot_provenance_and_payload_tampering(self) -> None:
        mutations = (
            lambda report: report["screenshots"][0].__setitem__(
                "completed_render_count", 119
            ),
            lambda report: report["screenshots"][0].__setitem__("edited", True),
            lambda report: report["screenshots"][0].__setitem__(
                "file", "screenshots/../escape.png"
            ),
            lambda report: report["screenshots"].reverse(),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                changed = copy.deepcopy(self.report)
                mutate(changed)
                with self.assertRaises(forge_client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )
        path = self.scenario_root / "screenshots" / attrahite_evidence.SCREENSHOT_FILES[0]
        path.write_bytes(path.read_bytes() + b"tamper")
        with self.assertRaises(forge_client.E2EError):
            self.validate()

    def test_rejects_a_linked_screenshot_payload(self) -> None:
        screenshot = (
            self.scenario_root
            / "screenshots"
            / attrahite_evidence.SCREENSHOT_FILES[0]
        )
        target = self.scenario_root / "linked-target.png"
        target.write_bytes(screenshot.read_bytes())
        screenshot.unlink()
        screenshot.symlink_to(target)

        with self.assertRaisesRegex(forge_client.E2EError, "linked"):
            self.validate()

    def test_rejects_blank_missing_texture_and_materially_changed_reopen(self) -> None:
        width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
        reopened = (
            self.scenario_root
            / "screenshots"
            / attrahite_evidence.SCREENSHOT_FILES[1]
        )
        reopened.write_bytes(rgb_png(width, height, b"\x20\x20\x20" * width * height))
        refresh_screenshot(self.scenario_root, self.report, 1)
        with self.assertRaisesRegex(forge_client.E2EError, "blank"):
            self.validate()

        write_patterned_png(reopened)
        missing_texture_pixels = bytearray(patterned_pixels())
        missing_texture_pixels[0:3] = b"\xff\x00\xff"
        reopened.write_bytes(rgb_png(width, height, bytes(missing_texture_pixels)))
        refresh_screenshot(self.scenario_root, self.report, 1)
        with self.assertRaisesRegex(forge_client.E2EError, "missing-texture"):
            self.validate()

        write_patterned_png(reopened, variant=1)
        refresh_screenshot(self.scenario_root, self.report, 1)
        with self.assertRaisesRegex(forge_client.E2EError, "differs materially"):
            self.validate()

    def test_rejects_dark_gallery_with_bright_sky_and_unchanged_structure(self) -> None:
        initial = (
            self.scenario_root
            / "screenshots"
            / attrahite_evidence.SCREENSHOT_FILES[0]
        )
        reopened = (
            self.scenario_root
            / "screenshots"
            / attrahite_evidence.SCREENSHOT_FILES[1]
        )
        write_fixed_gallery_png(initial, 8)
        write_fixed_gallery_png(reopened, 104)
        refresh_screenshot(self.scenario_root, self.report, 0)
        refresh_screenshot(self.scenario_root, self.report, 1)
        left = forge_evidence.decode_png(initial)
        right = forge_evidence.decode_png(reopened)
        self.assertLessEqual(
            attrahite_evidence.structural_change_ratio(left, right),
            attrahite_evidence.MAXIMUM_REOPEN_STRUCTURAL_CHANGE_RATIO,
        )
        self.assertLessEqual(
            forge_evidence.changed_pixel_ratio(left, right),
            attrahite_evidence.MAXIMUM_REOPEN_CHANGED_PIXEL_RATIO,
        )

        with self.assertRaisesRegex(forge_client.E2EError, "gallery lighting.*dark"):
            self.validate()

    def test_rejects_cross_phase_gallery_brightness_drift(self) -> None:
        initial = (
            self.scenario_root
            / "screenshots"
            / attrahite_evidence.SCREENSHOT_FILES[0]
        )
        reopened = (
            self.scenario_root
            / "screenshots"
            / attrahite_evidence.SCREENSHOT_FILES[1]
        )
        write_fixed_gallery_png(initial, 60)
        write_fixed_gallery_png(reopened, 100)
        refresh_screenshot(self.scenario_root, self.report, 0)
        refresh_screenshot(self.scenario_root, self.report, 1)

        with self.assertRaisesRegex(forge_client.E2EError, "lighting changed materially"):
            self.validate()


class ArchiveContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.parent = Path(self.temporary_directory.name)
        self.archive_root = self.parent / attrahite_evidence.ARCHIVE_DIRECTORY_NAME
        self.report = build_report(self.archive_root)
        write_report_and_marker(self.archive_root, self.report)
        manifest = build_archive_manifest(self.archive_root, self.report)
        (self.archive_root / attrahite_evidence.ARCHIVE_MANIFEST_NAME).write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_accepts_exact_self_contained_archive(self) -> None:
        summary = attrahite_evidence.validate_archived_evidence(self.archive_root)

        self.assertEqual(91, summary.assertion_count)
        self.assertEqual(2, summary.screenshot_count)
        self.assertEqual(attrahite_evidence.HARNESS_SHA256, summary.harness_sha256)

    def test_archive_artifact_inventory_ignores_object_order_only(self) -> None:
        manifest_path = self.archive_root / attrahite_evidence.ARCHIVE_MANIFEST_NAME
        manifest = build_archive_manifest(self.archive_root, self.report)
        artifacts = manifest["artifacts"]
        manifest["artifacts"] = {
            role: artifacts[role]
            for role in reversed(tuple(artifacts))
        }
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        attrahite_evidence.validate_archived_evidence(self.archive_root)

        manifest["artifacts"]["foreign"] = {}
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

    def test_rejects_payload_manifest_and_harness_pin_tampering(self) -> None:
        manifest_path = self.archive_root / attrahite_evidence.ARCHIVE_MANIFEST_NAME
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["profile"]["manifest_sha256"] = "0" * 64
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

        manifest = build_archive_manifest(self.archive_root, self.report)
        manifest["artifacts"]["harness"]["size"] = 1
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

    def test_rejects_publication_attestation_and_payload_digest_tampering(self) -> None:
        manifest_path = self.archive_root / attrahite_evidence.ARCHIVE_MANIFEST_NAME
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["publication"]["verified_last_in_capture_runtime"] = False
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

        manifest = build_archive_manifest(self.archive_root, self.report)
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        report_path = self.archive_root / "reports/report.json"
        report_path.write_bytes(report_path.read_bytes() + b" ")
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

    def test_rejects_a_linked_archive_payload(self) -> None:
        screenshot = (
            self.archive_root
            / "screenshots"
            / attrahite_evidence.SCREENSHOT_FILES[0]
        )
        target = self.parent / "linked-target.png"
        target.write_bytes(screenshot.read_bytes())
        screenshot.unlink()
        screenshot.symlink_to(target)

        with self.assertRaisesRegex(forge_client.E2EError, "linked"):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

    def test_rejects_extra_payload_and_competing_versioned_archive(self) -> None:
        extra = self.archive_root / "screenshots/extra.png"
        extra.write_bytes(b"extra")
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)
        extra.unlink()

        competing = self.parent / "attrahite-block-registry-v26"
        competing.mkdir()
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

    def test_rejects_missing_archive_fail_closed(self) -> None:
        missing = self.parent / attrahite_evidence.ARCHIVE_DIRECTORY_NAME
        missing.rename(self.parent / "held-aside")
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_archived_evidence(missing)


if __name__ == "__main__":
    unittest.main()
