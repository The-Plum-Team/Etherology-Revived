from __future__ import annotations

import copy
from dataclasses import replace
import json
from pathlib import Path
import sys
import tempfile
import unittest


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_client
import forge_evidence
import forge_attrahite_evidence_v14 as attrahite_evidence
from test_forge_evidence import rgb_png


REPOSITORY_ROOT = SCRIPT_DIRECTORY.parent.parent


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
    def test_v14_snapshot_remains_exact_and_rejects_v15_active_profile(self) -> None:
        configuration = forge_client.load_configuration()
        snapshot = SCRIPT_DIRECTORY / "forge-1.20.1-profile-v14.json"

        self.assertEqual(attrahite_evidence.PROFILE_SIZE, snapshot.stat().st_size)
        self.assertEqual(
            attrahite_evidence.PROFILE_SHA256,
            forge_client.sha256_file(snapshot),
        )
        with self.assertRaises(forge_client.E2EError):
            attrahite_evidence.validate_active_profile(configuration)

    def test_rejects_a_linked_v14_snapshot(self) -> None:
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
            active.write_bytes(
                (SCRIPT_DIRECTORY / "forge-1.20.1-profile-v14.json").read_bytes()
            )
            snapshot.symlink_to(active.name)
            linked_configuration = replace(
                configuration,
                repository_root=repository_root,
            )

            with self.assertRaises(forge_client.E2EError):
                attrahite_evidence.validate_active_profile(linked_configuration)

    def test_v11_through_v13_profiles_remain_byte_exact(self) -> None:
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
        }
        for version, (size, digest) in expected.items():
            path = SCRIPT_DIRECTORY / f"forge-1.20.1-profile-v{version}.json"
            self.assertEqual(size, path.stat().st_size)
            self.assertEqual(digest, forge_evidence.sha256_file(path))

    def test_canonical_resource_bytes_are_pinned(self) -> None:
        attrahite_evidence.validate_canonical_resources(REPOSITORY_ROOT)
        self.assertEqual(
            tuple(attrahite_evidence.EXPECTED_RESOURCE_SHA256),
            attrahite_evidence.EXPECTED_READY_RESOURCES,
        )

    def test_assertion_inventory_matches_harness_contract(self) -> None:
        self.assertEqual(87, len(attrahite_evidence.EXPECTED_ASSERTION_NAMES))
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

        self.assertEqual(87, summary.assertion_count)
        self.assertEqual(2, summary.screenshot_count)
        self.assertEqual(attrahite_evidence.HARNESS_SHA256, summary.harness_sha256)

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
