from __future__ import annotations

import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import client
import evidence
import fabric_forest_lantern_evidence as forest_evidence
from test_evidence import rgb_png


REPOSITORY_ROOT = SCRIPT_DIRECTORY.parent.parent


def patterned_pixels(phase: int) -> bytes:
    width, height = forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    pixel_count = width * height
    pattern = b"".join(
        bytes(
            (
                20 + ((index + phase * 17) % 128),
                100 + ((index * 3) % 64),
                20 + ((index * 5) % 64),
            )
        )
        for index in range(256)
    )
    byte_count = pixel_count * 3
    return (pattern * ((byte_count + len(pattern) - 1) // len(pattern)))[:byte_count]


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
        "width": 1920,
        "height": 1080,
        "size": path.stat().st_size,
        "sha256": evidence.sha256_file(path),
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
            "framebuffer": "1920x1080",
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
        "lane": "fabric-1.20.1",
        "role": "host",
        "status": "passed",
        "client_ticks": 1_024,
        "lifecycle_failure": "",
        "assertions": [],
        "world": copy.deepcopy(forest_evidence.EXPECTED_WORLD),
        "ready_resources": list(forest_evidence.EXPECTED_READY_RESOURCES),
        "artifacts": [
            {
                "mod_id": "etherology",
                "origin_kind": "PATH",
                "file_name": "etherology-under-test.jar",
                "size": 2_881_337,
                "sha256": "1" * 64,
            },
            {
                "mod_id": "etherology_e2e_harness",
                "origin_kind": "PATH",
                "file_name": "etherology-e2e-harness.jar",
                "size": 151_337,
                "sha256": "2" * 64,
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
            "luminance": 8,
            "asset_sha256": copy.deepcopy(forest_evidence.EXPECTED_ASSET_SHA256),
            "forced_stage_ages": [0, 1, 2, 3],
            "forced_state_inventory": list(
                forest_evidence.EXPECTED_FORCED_STATE_INVENTORY
            ),
            "rendered_state_inventory": list(
                forest_evidence.EXPECTED_RENDERED_STATE_INVENTORY
            ),
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
            "artifact_node": "fabric-1.20.1",
            "minecraft": "1.20.1",
            "loader": "fabric",
            "loader_version": "0.17.3",
            "java": 17,
            "capture_kind": "composed-minecraft-framebuffer",
            "framebuffer_width": 1920,
            "framebuffer_height": 1080,
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
                "sha256": evidence.sha256_file(archive_root / relative_path),
            }
            for relative_path in forest_evidence.ARCHIVE_PAYLOAD_PATHS
        },
    }


class ActiveProfileTests(unittest.TestCase):
    def test_active_profile_is_the_exact_v24_snapshot(self) -> None:
        configuration = client.load_configuration(
            SCRIPT_DIRECTORY / "fabric-1.20.1-profile.json"
        )
        forest_evidence.validate_active_profile(configuration)

    def test_canonical_asset_bytes_match_the_runtime_digest_contract(self) -> None:
        for identifier, expected_sha256 in forest_evidence.EXPECTED_ASSET_SHA256.items():
            namespace, relative_path = identifier.split(":", 1)
            asset_root = (
                REPOSITORY_ROOT / "src" / "main" / "generated" / "assets"
                if relative_path == "models/item/forest_lantern.json"
                else REPOSITORY_ROOT / "src" / "client" / "resources" / "assets"
            )
            path = asset_root / namespace / relative_path
            self.assertTrue(path.is_file())
            self.assertFalse(path.is_symlink())
            self.assertEqual(expected_sha256, evidence.sha256_file(path))

    def test_fixture_inventory_covers_all_twenty_unique_age_facing_states(self) -> None:
        fixtures = forest_evidence.STAGE_FIXTURES
        rendered_states = tuple(
            f"age={fixture['age']},facing={fixture['facing']}"
            for fixture in fixtures
        )
        forced_states = tuple(
            state
            for state, fixture in zip(rendered_states, fixtures)
            if fixture["forced"]
        )

        self.assertEqual(20, len(fixtures))
        self.assertEqual(20, len(set(rendered_states)))
        self.assertEqual(
            forest_evidence.EXPECTED_RENDERED_STATE_INVENTORY,
            rendered_states,
        )
        self.assertEqual(
            forest_evidence.EXPECTED_FORCED_STATE_INVENTORY,
            forced_states,
        )
        self.assertEqual(20, len({fixture["position"] for fixture in fixtures}))
        self.assertEqual(
            20,
            len({fixture["support_position"] for fixture in fixtures}),
        )
        self.assertTrue(
            {fixture["position"] for fixture in fixtures}.isdisjoint(
                {fixture["support_position"] for fixture in fixtures}
            )
        )


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

    def test_rejects_119_completed_renders_in_capture_or_screenshot(self) -> None:
        self.report["forest_lantern"]["captures"]["stages"]["stable_renders"] = 119
        with self.assertRaises(client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["screenshots"][1]["completed_render_count"] = 119
        with self.assertRaises(client.E2EError):
            self.validate()

    def test_rejects_property_matrix_and_server_client_mirror_tampering(self) -> None:
        self.report["forest_lantern"]["raw_state_ids"][1] = 400
        with self.assertRaises(client.E2EError):
            self.validate()
        self.report["forest_lantern"]["raw_state_ids"][1] = 401
        rendered_states = self.report["forest_lantern"]["rendered_state_inventory"]
        rendered_states[-1] = rendered_states[0]
        with self.assertRaises(client.E2EError):
            self.validate()
        rendered_states[-1] = forest_evidence.EXPECTED_RENDERED_STATE_INVENTORY[-1]
        snapshot = self.report["forest_lantern"]["captures"]["stages"]
        snapshot["client_snapshot"]["stages"][7]["facing"] = "north"
        with self.assertRaises(client.E2EError):
            self.validate()

    def test_rejects_faked_block_item_success_or_unsupported_acceptance(self) -> None:
        north = self.report["forest_lantern"]["placements"]["north"]
        north["action_result"] = "SUCCESS"
        with self.assertRaises(client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        unsupported = self.report["forest_lantern"]["unsupported_placement"]
        unsupported["accepted"] = True
        with self.assertRaises(client.E2EError):
            self.validate()

    def test_rejects_resource_render_layer_and_assertion_tampering(self) -> None:
        self.report["ready_resources"].pop()
        with self.assertRaises(client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        asset_sha256 = self.report["forest_lantern"]["asset_sha256"]
        asset_sha256[forest_evidence.EXPECTED_READY_RESOURCES[0]] = "f" * 64
        with self.assertRaises(client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["forest_lantern"]["render_layer"] = "solid"
        with self.assertRaises(client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["assertions"][0]["actual"] = "fabric-ish"
        with self.assertRaises(client.E2EError):
            self.validate()

    def test_rejects_screenshot_reorder_or_digest_mismatch(self) -> None:
        screenshots = self.report["screenshots"]
        screenshots[0], screenshots[1] = screenshots[1], screenshots[0]
        with self.assertRaises(client.E2EError):
            self.validate()
        self.report = build_report(self.scenario_root)
        self.report["screenshots"][0]["sha256"] = "f" * 64
        with self.assertRaises(client.E2EError):
            self.validate()

    def test_rejects_missing_texture_magenta_and_unchanged_transitions(self) -> None:
        screenshot_path = (
            self.scenario_root / "screenshots" / forest_evidence.SCREENSHOT_FILES[2]
        )
        width, height = forest_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
        magenta = bytes((220, 20, 220)) * (width * height)
        screenshot_path.write_bytes(rgb_png(width, height, magenta))
        refresh_screenshot(self.scenario_root, self.report, 2)
        with self.assertRaises(client.E2EError):
            self.validate()

        self.report = build_report(self.scenario_root)
        first = self.scenario_root / "screenshots" / forest_evidence.SCREENSHOT_FILES[0]
        for index, file_name in enumerate(forest_evidence.SCREENSHOT_FILES[1:6], 1):
            (self.scenario_root / "screenshots" / file_name).write_bytes(
                first.read_bytes()
            )
            refresh_screenshot(self.scenario_root, self.report, index)
        with self.assertRaises(client.E2EError):
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
        self.assertEqual(68, summary.assertion_count)
        self.assertEqual(7, summary.screenshot_count)

    def test_rejects_payload_tamper_and_wrong_archive_identity(self) -> None:
        report_path = self.archive_root / "reports" / "report.json"
        report_path.write_text(report_path.read_text(encoding="utf-8") + " ")
        with self.assertRaises(client.E2EError):
            forest_evidence.validate_archived_evidence(self.archive_root)

        wrong_root = self.archive_root.with_name("forest-lantern-v25")
        self.archive_root.rename(wrong_root)
        with self.assertRaises(client.E2EError):
            forest_evidence.validate_archived_evidence(wrong_root)

    def test_rejects_competing_forest_lantern_archive_version(self) -> None:
        competing = self.archive_root.with_name("forest-lantern-v25")
        competing.mkdir()

        with self.assertRaisesRegex(client.E2EError, "exact sole"):
            forest_evidence.validate_archived_evidence(self.archive_root)


if __name__ == "__main__":
    unittest.main()
