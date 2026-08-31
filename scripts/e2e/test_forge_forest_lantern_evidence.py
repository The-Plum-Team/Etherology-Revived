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
import forge_forest_lantern_evidence as forest_evidence
from test_evidence import rgb_png


def patterned_pixels(phase: int) -> bytes:
    width, height = forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    pixel_count = width * height
    pattern = b"".join(
        bytes(
            (
                20 + (index % 128),
                100 + ((index * 3) % 64),
                20 + ((index * 5) % 64),
            )
        )
        for index in range(256)
    )
    byte_count = pixel_count * 3
    pixels = bytearray(
        (pattern * ((byte_count + len(pattern) - 1) // len(pattern)))[:byte_count]
    )
    for pixel_index in range(phase * 5_000):
        pixels[pixel_index * 3] ^= 0x7F
    return bytes(pixels)


def write_patterned_png(path: Path, phase: int) -> None:
    width, height = forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    path.write_bytes(rgb_png(width, height, patterned_pixels(phase)))


def screenshot_record(
    scenario_root: Path,
    index: int,
) -> dict[str, object]:
    step, relative_path = forest_evidence.EXPECTED_SCREENSHOTS[index]
    path = scenario_root / relative_path
    return {
        "step": step,
        "role": "host",
        "file": relative_path,
        "width": forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
        "height": forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
        "size": path.stat().st_size,
        "sha256": forge_evidence.sha256_file(path),
        "completed_render_count": forest_evidence.REQUIRED_STABLE_RENDERS,
        "source": "minecraft-framebuffer",
        "edited": False,
    }


def placement_evidence(
    fixture: dict[str, object] | None,
) -> dict[str, object]:
    if fixture is None:
        return {
            "direction": "north",
            "action_result": "FAIL",
            "accepted": False,
            "stack_before": 1,
            "stack_after": 1,
            "block_item_mapping": True,
            "support_valid": False,
            "observation": forest_evidence.expected_observation(
                "9,125,2",
                "minecraft:air",
            ),
            "support_id": "minecraft:iron_bars",
        }
    direction = str(fixture["facing"])
    return {
        "direction": direction,
        "action_result": "CONSUME",
        "accepted": True,
        "stack_before": 1,
        "stack_after": 0,
        "block_item_mapping": True,
        "support_valid": True,
        "observation": forest_evidence.expected_observation(
            str(fixture["position"]),
            "etherology:forest_lantern",
            "4",
            direction,
            True,
        ),
        "support_id": "minecraft:polished_andesite",
    }


def build_report(scenario_root: Path) -> dict[str, object]:
    screenshots_directory = scenario_root / "screenshots"
    reports_directory = scenario_root / "reports"
    screenshots_directory.mkdir(parents=True, exist_ok=True)
    reports_directory.mkdir(parents=True, exist_ok=True)
    for index, file_name in enumerate(forest_evidence.SCREENSHOT_FILES):
        write_patterned_png(screenshots_directory / file_name, min(index, 5))
    screenshots = [
        screenshot_record(scenario_root, index)
        for index in range(len(forest_evidence.EXPECTED_SCREENSHOTS))
    ]
    captures = {
        phase: {
            "mirror_exact": True,
            "render_ready": True,
            "camera_exact": True,
            "stable_renders": forest_evidence.REQUIRED_STABLE_RENDERS,
            "framebuffer": (
                f"{forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[0]}x"
                f"{forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[1]}"
            ),
            "server_snapshot": forest_evidence.expected_snapshot(phase),
            "client_snapshot": forest_evidence.expected_snapshot(phase),
        }
        for phase in forest_evidence.PHASES
    }
    placements = {
        str(fixture["facing"]): placement_evidence(fixture)
        for fixture in forest_evidence.PLACEMENT_FIXTURES
    }
    report: dict[str, object] = {
        "schema": 2,
        "scenario": forest_evidence.SCENARIO_ID,
        "profile_id": forest_evidence.PROFILE_ID,
        "profile_manifest_size": forest_evidence.PROFILE_SIZE,
        "profile_manifest_sha256": forest_evidence.PROFILE_SHA256,
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
        "framebuffer_width": forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
        "framebuffer_height": forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
        "lifecycle_failure": "",
        "assertions": [],
        "world": copy.deepcopy(forest_evidence.EXPECTED_WORLD),
        "ready_resources": list(forest_evidence.EXPECTED_READY_RESOURCES),
        "artifacts": [
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
                "size": 151_337,
                "sha256": "2" * 64,
                "failure": "",
            },
        ],
        "screenshots": screenshots,
        "forest_lantern": {
            "registry_id": "etherology:forest_lantern",
            "item_id": "etherology:forest_lantern",
            "block_item_mapping": True,
            "default_state": "age=4,facing=north",
            "state_count": 20,
            "state_inventory": list(forest_evidence.EXPECTED_STATE_INVENTORY),
            "raw_state_ids": list(range(400, 420)),
            "render_layer": "cutout",
            "models_baked": True,
            "renderable_state_inventory": list(
                forest_evidence.EXPECTED_STATE_INVENTORY
            ),
            "luminance": 8,
            "asset_sha256": copy.deepcopy(forest_evidence.EXPECTED_ASSET_SHA256),
            "forced_stage_ages": [0, 1, 2, 3],
            "stage_fixtures": copy.deepcopy(list(forest_evidence.STAGE_FIXTURES)),
            "placement_fixtures": copy.deepcopy(
                list(forest_evidence.PLACEMENT_FIXTURES)
            ),
            "unsupported_placement": placement_evidence(None),
            "placements": placements,
            "persistence_exact": True,
            "camera": copy.deepcopy(forest_evidence.CAMERA),
            "required_stable_renders": forest_evidence.REQUIRED_STABLE_RENDERS,
            "captures": captures,
        },
    }
    assertion_evidence = forest_evidence.expected_assertion_evidence(
        report,
        screenshots,
    )
    report["assertions"] = [
        {
            "name": name,
            "passed": True,
            "expected": assertion_evidence[name][0],
            "actual": assertion_evidence[name][1],
        }
        for name in forest_evidence.EXPECTED_ASSERTION_NAMES
    ]
    return report


def write_report_and_marker(
    scenario_root: Path,
    report: dict[str, object],
) -> None:
    reports_directory = scenario_root / "reports"
    (reports_directory / "report.json").write_text(
        json.dumps(report, indent=2) + "\n",
        encoding="utf-8",
    )
    (reports_directory / "done.marker").write_text(
        "complete\n",
        encoding="utf-8",
    )


def refresh_screenshot(
    scenario_root: Path,
    report: dict[str, object],
    index: int,
) -> None:
    screenshots = report["screenshots"]
    if not isinstance(screenshots, list):
        raise AssertionError("fixture screenshots changed")
    screenshots[index] = screenshot_record(scenario_root, index)
    phase = forest_evidence.PHASES[index]
    assertion = next(
        value
        for value in report["assertions"]
        if value["name"] == f"native_screenshot_written:{phase}"
    )
    assertion["actual"] = (
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
            forest_evidence.EXPECTED_ARTIFACTS,
        )
    }


def build_archive_manifest(
    archive_root: Path,
    report: dict[str, object],
) -> dict[str, object]:
    capture_mtime_ns = {
        relative_path: 1_000_000_000 + index
        for index, relative_path in enumerate(forest_evidence.ARCHIVE_PAYLOAD_PATHS)
    }
    capture_mtime_ns["reports/done.marker"] = 2_000_000_000
    return {
        "schema": 1,
        "kind": forest_evidence.ARCHIVE_KIND,
        "verification_scope": forest_evidence.ARCHIVE_VERIFICATION_SCOPE,
        "scenario": forest_evidence.SCENARIO_ID,
        "profile": {
            "id": forest_evidence.PROFILE_ID,
            "manifest_path": forest_evidence.ACTIVE_PROFILE_RELATIVE_PATH,
            "manifest_size": forest_evidence.PROFILE_SIZE,
            "manifest_sha256": forest_evidence.PROFILE_SHA256,
        },
        "runtime": {
            "artifact_node": "forge-1.20.1",
            "minecraft": "1.20.1",
            "loader": "forge",
            "loader_version": "47.4.9",
            "java": 17,
            "capture_kind": "composed-minecraft-framebuffer",
            "framebuffer_width": forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
            "framebuffer_height": forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
        },
        "publication": {
            **forest_evidence.ARCHIVE_PUBLICATION_ATTESTATION,
            "capture_mtime_ns": capture_mtime_ns,
        },
        "capture_metadata": {
            "path": forest_evidence.ARCHIVE_CAPTURE_METADATA_PATH,
            "size": 1_337,
            "sha256": "3" * 64,
        },
        "assertion_count": len(forest_evidence.EXPECTED_ASSERTION_NAMES),
        "screenshot_count": len(forest_evidence.EXPECTED_SCREENSHOTS),
        "artifacts": archived_artifacts(report),
        "files": {
            relative_path: {
                "size": (archive_root / relative_path).stat().st_size,
                "sha256": forge_evidence.sha256_file(archive_root / relative_path),
            }
            for relative_path in forest_evidence.ARCHIVE_PAYLOAD_PATHS
        },
    }


class ActiveProfileTests(unittest.TestCase):
    def test_active_profile_is_the_exact_v13_snapshot(self) -> None:
        configuration = forge_client.load_configuration()
        forest_evidence.validate_active_profile(configuration)

    def test_rejects_a_linked_v13_snapshot(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository_root = Path(temporary_directory)
            active = (
                repository_root / forest_evidence.ACTIVE_PROFILE_RELATIVE_PATH
            )
            snapshot = (
                repository_root / forest_evidence.SNAPSHOT_PROFILE_RELATIVE_PATH
            )
            active.parent.mkdir(parents=True)
            active.write_bytes(configuration.profile_manifest_path.read_bytes())
            snapshot.symlink_to(active.name)
            linked_configuration = replace(
                configuration,
                repository_root=repository_root,
            )
            with self.assertRaises(forge_client.E2EError):
                forest_evidence.validate_active_profile(linked_configuration)


class ReportContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.scenario_root = Path(self.temporary_directory.name)
        self.report = build_report(self.scenario_root)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def validate(self) -> float:
        ratio, _artifacts = forest_evidence.validate_report_contract(
            self.scenario_root,
            self.report,
        )
        return ratio

    def test_accepts_exact_fixture_placement_mirrors_and_visuals(self) -> None:
        self.assertGreaterEqual(
            self.validate(),
            forest_evidence.MINIMUM_PLACEMENT_CHANGED_PIXEL_RATIO,
        )

    def test_fixture_covers_every_native_age_facing_state_exactly_once(self) -> None:
        fixture_states = tuple(
            sorted(
                (
                    *(
                        f"age={fixture['age']},facing={fixture['facing']}"
                        for fixture in forest_evidence.STAGE_FIXTURES
                    ),
                    *(
                        f"age=4,facing={fixture['facing']}"
                        for fixture in forest_evidence.PLACEMENT_FIXTURES
                    ),
                )
            )
        )

        self.assertEqual(forest_evidence.EXPECTED_STATE_INVENTORY, fixture_states)
        self.assertEqual(20, len(set(fixture_states)))

    def test_rejects_119_completed_renders_in_capture_or_screenshot(self) -> None:
        self.report["forest_lantern"]["captures"]["stages"]["stable_renders"] = 119
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["screenshots"][1]["completed_render_count"] = 119
        with self.assertRaises(forge_client.E2EError):
            self.validate()

    def test_rejects_mutated_property_inventory_and_server_client_mirror(self) -> None:
        self.report["forest_lantern"]["raw_state_ids"][1] = 400
        with self.assertRaises(forge_client.E2EError):
            self.validate()

        self.report = build_report(self.scenario_root)
        stage_capture = self.report["forest_lantern"]["captures"]["stages"]
        stage_capture["server_snapshot"]["stages"][0]["can_place_at"] = True
        stage_capture["client_snapshot"]["stages"][0]["can_place_at"] = True
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        snapshot = self.report["forest_lantern"]["captures"]["stages"]
        snapshot["client_snapshot"]["stages"][0]["age"] = "4"
        with self.assertRaises(forge_client.E2EError):
            self.validate()

        self.report = build_report(self.scenario_root)
        for phase in ("facing-west", "reopened"):
            capture = self.report["forest_lantern"]["captures"][phase]
            for role in ("server_snapshot", "client_snapshot"):
                capture[role]["stages"][4]["facing"] = "north"
        with self.assertRaises(forge_client.E2EError):
            self.validate()

    def test_rejects_faked_block_item_success_or_unsupported_acceptance(self) -> None:
        north = self.report["forest_lantern"]["placements"]["north"]
        north["action_result"] = "SUCCESS"
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        unsupported = self.report["forest_lantern"]["unsupported_placement"]
        unsupported["accepted"] = True
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        unsupported = self.report["forest_lantern"]["unsupported_placement"]
        unsupported["action_result"] = "PASS"
        with self.assertRaises(forge_client.E2EError):
            self.validate()

    def test_rejects_resource_render_layer_and_assertion_tampering(self) -> None:
        self.report["ready_resources"].pop()
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["forest_lantern"]["render_layer"] = "solid"
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["forest_lantern"]["renderable_state_inventory"].pop()
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["forest_lantern"]["asset_sha256"][
            forest_evidence.EXPECTED_READY_RESOURCES[0]
        ] = "0" * 64
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["assertions"][0]["actual"] = "forge-ish"
        with self.assertRaises(forge_client.E2EError):
            self.validate()

    def test_rejects_profile_runtime_and_artifact_provenance_drift(self) -> None:
        self.report["profile_manifest_sha256"] = "0" * 64
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["loader_version"] = "47.4.8"
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["artifacts"][0]["failure"] = "origin ambiguous"
        with self.assertRaises(forge_client.E2EError):
            self.validate()

    def test_rejects_screenshot_reorder_or_digest_mismatch(self) -> None:
        screenshots = self.report["screenshots"]
        screenshots[0], screenshots[1] = screenshots[1], screenshots[0]
        with self.assertRaises(forge_client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["screenshots"][0]["sha256"] = "f" * 64
        with self.assertRaises(forge_client.E2EError):
            self.validate()

        self.report = build_report(self.scenario_root)
        screenshot_path = (
            self.scenario_root / "screenshots" / forest_evidence.SCREENSHOT_FILES[0]
        )
        wrong_width = forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[0] - 1
        height = forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[1]
        wrong_pixels = bytes((20, 100, 20)) * (wrong_width * height)
        screenshot_path.write_bytes(rgb_png(wrong_width, height, wrong_pixels))
        refresh_screenshot(self.scenario_root, self.report, 0)
        with self.assertRaises(forge_client.E2EError):
            self.validate()

        self.report = build_report(self.scenario_root)
        screenshot_path.unlink()
        screenshot_path.symlink_to(forest_evidence.SCREENSHOT_FILES[1])
        with self.assertRaises(forge_client.E2EError):
            self.validate()

    def test_rejects_missing_texture_magenta_and_unchanged_transitions(self) -> None:
        screenshot_path = (
            self.scenario_root / "screenshots" / forest_evidence.SCREENSHOT_FILES[2]
        )
        width, height = forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
        magenta = bytes((220, 20, 220)) * (width * height)
        screenshot_path.write_bytes(rgb_png(width, height, magenta))
        refresh_screenshot(self.scenario_root, self.report, 2)
        with self.assertRaises(forge_client.E2EError):
            self.validate()

        self.report = build_report(self.scenario_root)
        reopened_path = (
            self.scenario_root / "screenshots" / forest_evidence.SCREENSHOT_FILES[6]
        )
        unrelated_pattern = bytes((240, 240, 240, 10, 10, 10))
        byte_count = width * height * 3
        unrelated = (
            unrelated_pattern
            * ((byte_count + len(unrelated_pattern) - 1) // len(unrelated_pattern))
        )[:byte_count]
        reopened_path.write_bytes(rgb_png(width, height, unrelated))
        refresh_screenshot(self.scenario_root, self.report, 6)
        with self.assertRaises(forge_client.E2EError):
            self.validate()

        self.report = build_report(self.scenario_root)
        first = self.scenario_root / "screenshots" / forest_evidence.SCREENSHOT_FILES[0]
        for index, file_name in enumerate(forest_evidence.SCREENSHOT_FILES[1:6], 1):
            (self.scenario_root / "screenshots" / file_name).write_bytes(
                first.read_bytes()
            )
            refresh_screenshot(self.scenario_root, self.report, index)
        with self.assertRaises(forge_client.E2EError):
            self.validate()


class ArchiveContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        temporary_root = Path(self.temporary_directory.name)
        self.archive_root = temporary_root / forest_evidence.ARCHIVE_DIRECTORY_NAME
        self.report = build_report(self.archive_root)
        write_report_and_marker(self.archive_root, self.report)
        manifest = build_archive_manifest(self.archive_root, self.report)
        (self.archive_root / forest_evidence.ARCHIVE_MANIFEST_NAME).write_text(
            json.dumps(manifest, indent=2) + "\n",
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_validates_frozen_archive_without_live_state(self) -> None:
        summary = forest_evidence.validate_archived_evidence(self.archive_root)
        self.assertEqual(forest_evidence.PROFILE_ID, summary.profile_id)
        self.assertEqual(69, summary.assertion_count)
        self.assertEqual(7, summary.screenshot_count)

    def test_rejects_payload_tamper_and_wrong_archive_identity(self) -> None:
        report_path = self.archive_root / "reports" / "report.json"
        report_path.write_text(report_path.read_text(encoding="utf-8") + " ")
        with self.assertRaises(forge_client.E2EError):
            forest_evidence.validate_archived_evidence(self.archive_root)

        wrong_root = self.archive_root.with_name("forest-lantern-v25")
        self.archive_root.rename(wrong_root)
        with self.assertRaises(forge_client.E2EError):
            forest_evidence.validate_archived_evidence(wrong_root)

    def test_rejects_archive_profile_artifact_and_publication_drift(self) -> None:
        manifest_path = self.archive_root / forest_evidence.ARCHIVE_MANIFEST_NAME
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["profile"]["id"] = "etherology-e2e-forge-1.20.1-v11"
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(forge_client.E2EError):
            forest_evidence.validate_archived_evidence(self.archive_root)

        manifest = build_archive_manifest(self.archive_root, self.report)
        manifest["artifacts"]["production"]["sha256"] = "0" * 64
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(forge_client.E2EError):
            forest_evidence.validate_archived_evidence(self.archive_root)

        manifest = build_archive_manifest(self.archive_root, self.report)
        manifest["publication"]["capture_mtime_ns"][
            "reports/done.marker"
        ] = 1
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(forge_client.E2EError):
            forest_evidence.validate_archived_evidence(self.archive_root)


if __name__ == "__main__":
    unittest.main()
