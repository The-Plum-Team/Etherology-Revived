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
import fabric_attrahite_evidence_v26 as historical_attrahite_evidence
import fabric_attrahite_evidence_v27 as attrahite_evidence
from test_evidence import rgb_png


REPOSITORY_ROOT = SCRIPT_DIRECTORY.parent.parent


def patterned_pixels(variant: int = 0) -> bytes:
    width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    pattern = b"".join(
        bytes(
            (
                80 + ((index + variant * 31) % 112),
                112 + ((index * 3 + variant * 17) % 88),
                80 + ((index * 5 + variant * 13) % 96),
            )
        )
        for index in range(256)
    )
    byte_count = width * height * 3
    return (pattern * ((byte_count + len(pattern) - 1) // len(pattern)))[:byte_count]


def write_patterned_png(path: Path, variant: int = 0) -> None:
    width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    path.write_bytes(rgb_png(width, height, patterned_pixels(variant)))


def dark_patterned_pixels() -> bytes:
    return bytes(channel // 4 for channel in patterned_pixels())


def high_contrast_dark_pixels() -> bytes:
    pixels = bytearray(patterned_pixels())
    for pixel_index in range(len(pixels) // 3):
        offset = pixel_index * 3
        if pixel_index % 4 == 1:
            pixels[offset : offset + 3] = b"\x10\x10\x10"
        else:
            for channel in range(3):
                pixels[offset + channel] = min(255, pixels[offset + channel] + 80)
    return bytes(pixels)


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
        "sha256": evidence.sha256_file(path),
        "completed_render_count": attrahite_evidence.REQUIRED_STABLE_RENDERS,
        "source": "minecraft-framebuffer",
        "edited": False,
    }


def artifact_records() -> list[dict[str, object]]:
    return [
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
            "size": attrahite_evidence.HARNESS_SIZE,
            "sha256": attrahite_evidence.HARNESS_SHA256,
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
        "artifact_node": "fabric-1.20.1",
        "minecraft": "1.20.1",
        "loader": "fabric",
        "java": 17,
        "lane": "fabric-1.20.1",
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
            "lighting": {
                phase: {
                    "server_stable_ticks": 20,
                    "client_stable_ticks": 20,
                    "server_observed": True,
                    "client_observed": True,
                    "server_pending": False,
                    "client_pending": False,
                    "server_sky": copy.deepcopy(
                        attrahite_evidence.EXPECTED_SKY_LIGHT
                    ),
                    "server_block": copy.deepcopy(
                        attrahite_evidence.EXPECTED_BLOCK_LIGHT
                    ),
                    "client_sky": copy.deepcopy(
                        attrahite_evidence.EXPECTED_SKY_LIGHT
                    ),
                    "client_block": copy.deepcopy(
                        attrahite_evidence.EXPECTED_BLOCK_LIGHT
                    ),
                }
                for phase in attrahite_evidence.PHASES
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
                "sha256": evidence.sha256_file(archive_root / relative_path),
            }
            for relative_path in attrahite_evidence.ARCHIVE_PAYLOAD_PATHS
        },
    }


class ActiveProfileTests(unittest.TestCase):
    def test_active_profile_is_exact_v27_snapshot(self) -> None:
        configuration = client.load_configuration()

        attrahite_evidence.validate_active_profile(configuration)
        self.assertEqual(
            attrahite_evidence.PROFILE_SHA256,
            client.sha256_file(SCRIPT_DIRECTORY / "fabric-1.20.1-profile.json"),
        )
        self.assertEqual(
            (SCRIPT_DIRECTORY / "fabric-1.20.1-profile.json").read_bytes(),
            (SCRIPT_DIRECTORY / "fabric-1.20.1-profile-v27.json").read_bytes(),
        )

    def test_v20_through_v26_profiles_remain_byte_exact(self) -> None:
        expected = {
            20: "77e2319ce711aa6c62de5aba4107f62d29ab96411c3fe2fba557e08e52444a8b",
            21: "d6fa9ac08407128f34473add51c2f75da703c34c73ac985c7af11b024449d722",
            22: "289eb0c29066990f7ad967b4f141d08bd7823c0cb79bded85faa37907bd1328f",
            23: "36e7ccb7556aaaf0edb01b066de6d5263f3dde3545ac016e84cf07f795403f84",
            24: "77b9d33689d76e7b46d849f519337821744a81e3dd287bd4a1339a7c6a801a77",
            25: "cc28abb3a530fec7e4fa2e67f8825db371b07ad4774071a0d630a19edc702cc0",
            26: "4091f9627f79b4ba816aa7a5fa90cf6731ea938884bb9aee9be642c3eb896862",
        }
        for version, digest in expected.items():
            path = SCRIPT_DIRECTORY / f"fabric-1.20.1-profile-v{version}.json"
            self.assertEqual(digest, evidence.sha256_file(path))

    def test_historical_v26_verifier_rejects_active_v27_profile(self) -> None:
        configuration = client.load_configuration()

        with self.assertRaisesRegex(client.E2EError, "profile bytes changed"):
            historical_attrahite_evidence.validate_active_profile(configuration)

    def test_v25_and_v26_verifier_histories_remain_byte_exact(self) -> None:
        expected = {
            "fabric_attrahite_evidence_v25.py": (
                "dad00e66507976e909817302021a80f0c7c974338d42c6cef60f233bc22f1974"
            ),
            "test_fabric_attrahite_evidence_v25.py": (
                "b651ea2fdbe8d6b98ed2f2215743ff154594ce4152ca780bb93b440f2e290aae"
            ),
            "fabric_attrahite_evidence_v26.py": (
                "23cf576ce006418180624ae52cc0fcef0057772588ee226e99f6585d99467274"
            ),
            "test_fabric_attrahite_evidence_v26.py": (
                "cc82754aa4c148743567e5749ca47a754659f3698ceddb0cf9cd91364089c251"
            ),
        }
        for file_name, digest in expected.items():
            self.assertEqual(
                digest,
                evidence.sha256_file(SCRIPT_DIRECTORY / file_name),
            )

    def test_canonical_resource_bytes_are_pinned(self) -> None:
        attrahite_evidence.validate_canonical_resources(REPOSITORY_ROOT)
        self.assertEqual(
            tuple(attrahite_evidence.EXPECTED_RESOURCE_SHA256),
            attrahite_evidence.EXPECTED_READY_RESOURCES,
        )

    def test_assertion_inventory_matches_harness_contract(self) -> None:
        self.assertEqual(89, len(attrahite_evidence.EXPECTED_ASSERTION_NAMES))
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

    def test_accepts_pending_diagnostic_with_exact_stable_light_samples(self) -> None:
        for phase in attrahite_evidence.PHASES:
            lighting = self.report["attrahite"]["lighting"][phase]
            lighting["server_pending"] = True
            lighting["client_pending"] = True

        self.assertEqual(0.0, self.validate())

    def test_rejects_light_sample_counter_and_diagnostic_field_drift(self) -> None:
        def reorder_server_sky(report: dict[str, object]) -> None:
            lighting = report["attrahite"]["lighting"]["initial"]
            lighting["server_sky"] = dict(
                reversed(tuple(lighting["server_sky"].items()))
            )

        mutations = (
            lambda report: report["attrahite"]["lighting"]["initial"].__setitem__(
                "server_stable_ticks", 19
            ),
            lambda report: report["attrahite"]["lighting"]["initial"].__setitem__(
                "client_stable_ticks", 19
            ),
            lambda report: report["attrahite"]["lighting"]["reopened"][
                "client_block"
            ].__setitem__("3,123,1", 7),
            reorder_server_sky,
            lambda report: report["attrahite"]["lighting"]["initial"].__setitem__(
                "server_pending", "true"
            ),
            lambda report: report["attrahite"]["lighting"]["reopened"].pop(
                "client_observed"
            ),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                changed = copy.deepcopy(self.report)
                mutate(changed)
                with self.assertRaises(client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )

    def test_rejects_top_level_schema_identity_and_field_drift(self) -> None:
        mutations = (
            lambda report: report.__setitem__("schema", 2),
            lambda report: report.__setitem__("artifact_node", "fabric-1.20.2"),
            lambda report: report.__setitem__("passed", False),
            lambda report: report.__setitem__("status", "failed"),
            lambda report: report.__setitem__("unexpected", True),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                changed = copy.deepcopy(self.report)
                mutate(changed)
                with self.assertRaises(client.E2EError):
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
                with self.assertRaises(client.E2EError):
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
            ).__setitem__("actual", "net.minecraft.class_2482"),
            lambda report: assertion(
                report, "capture_camera_exact:reopened"
            ).__setitem__("actual", "wrong"),
            lambda report: assertion(
                report, "capture_lighting_ready:initial"
            ).__setitem__("actual", "pending=true"),
        )
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                changed = copy.deepcopy(self.report)
                mutate(changed)
                with self.assertRaises(client.E2EError):
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
                with self.assertRaises(client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )
        changed = copy.deepcopy(self.report)
        assertion(changed, names[1])["actual"] = assertion(changed, names[0])["actual"]
        with self.assertRaises(client.E2EError):
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
                with self.assertRaises(client.E2EError):
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
                with self.assertRaises(client.E2EError):
                    attrahite_evidence.validate_report_contract(
                        self.scenario_root, changed
                    )
        path = self.scenario_root / "screenshots" / attrahite_evidence.SCREENSHOT_FILES[0]
        path.write_bytes(path.read_bytes() + b"tamper")
        with self.assertRaises(client.E2EError):
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
        with self.assertRaisesRegex(client.E2EError, "blank"):
            self.validate()

        write_patterned_png(reopened)
        missing_texture_pixels = bytearray(patterned_pixels())
        missing_texture_pixels[0:3] = b"\xff\x00\xff"
        reopened.write_bytes(rgb_png(width, height, bytes(missing_texture_pixels)))
        refresh_screenshot(self.scenario_root, self.report, 1)
        with self.assertRaisesRegex(client.E2EError, "missing-texture"):
            self.validate()

        write_patterned_png(reopened, variant=1)
        refresh_screenshot(self.scenario_root, self.report, 1)
        with self.assertRaisesRegex(client.E2EError, "differs materially"):
            self.validate()

    def test_rejects_dark_initial_vs_lit_reopen(self) -> None:
        width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
        initial = (
            self.scenario_root
            / "screenshots"
            / attrahite_evidence.SCREENSHOT_FILES[0]
        )
        initial.write_bytes(rgb_png(width, height, dark_patterned_pixels()))
        refresh_screenshot(self.scenario_root, self.report, 0)

        with self.assertRaisesRegex(client.E2EError, "mean luminance"):
            self.validate()

    def test_rejects_matching_dark_pair_despite_zero_reopen_delta(self) -> None:
        width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
        for index, file_name in enumerate(attrahite_evidence.SCREENSHOT_FILES):
            path = self.scenario_root / "screenshots" / file_name
            path.write_bytes(rgb_png(width, height, dark_patterned_pixels()))
            refresh_screenshot(self.scenario_root, self.report, index)

        with self.assertRaisesRegex(client.E2EError, "mean luminance"):
            self.validate()

    def test_rejects_excess_dark_pixels_even_when_mean_is_bright(self) -> None:
        width, height = attrahite_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
        pixels = high_contrast_dark_pixels()
        image = evidence.PngImage(width=width, height=height, pixels=pixels)
        mean_luminance, dark_pixel_ratio = attrahite_evidence.image_lighting_metrics(
            image
        )
        self.assertGreater(mean_luminance, attrahite_evidence.MINIMUM_MEAN_LUMINANCE)
        self.assertGreater(
            dark_pixel_ratio,
            attrahite_evidence.MAXIMUM_DARK_PIXEL_RATIO,
        )
        initial = (
            self.scenario_root
            / "screenshots"
            / attrahite_evidence.SCREENSHOT_FILES[0]
        )
        initial.write_bytes(rgb_png(width, height, pixels))
        refresh_screenshot(self.scenario_root, self.report, 0)

        with self.assertRaisesRegex(client.E2EError, "dark-pixel ratio"):
            self.validate()

    def test_visual_thresholds_reject_observed_v25_lighting_race_with_margin(self) -> None:
        self.assertEqual(0.35, attrahite_evidence.MAXIMUM_REOPEN_CHANGED_PIXEL_RATIO)
        self.assertGreater(attrahite_evidence.MINIMUM_MEAN_LUMINANCE, 89.919762)
        self.assertLess(attrahite_evidence.MINIMUM_MEAN_LUMINANCE, 149.146071)
        self.assertLess(attrahite_evidence.MAXIMUM_DARK_PIXEL_RATIO, 0.418252)
        self.assertGreater(attrahite_evidence.MAXIMUM_DARK_PIXEL_RATIO, 0.007072)


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

        self.assertEqual(89, summary.assertion_count)
        self.assertEqual(2, summary.screenshot_count)
        self.assertEqual(attrahite_evidence.HARNESS_SHA256, summary.harness_sha256)

    def test_rejects_payload_manifest_and_harness_pin_tampering(self) -> None:
        manifest_path = self.archive_root / attrahite_evidence.ARCHIVE_MANIFEST_NAME
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["profile"]["manifest_sha256"] = "0" * 64
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

        manifest = build_archive_manifest(self.archive_root, self.report)
        manifest["artifacts"]["harness"]["size"] = 1
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

    def test_rejects_extra_payload_and_competing_versioned_archive(self) -> None:
        extra = self.archive_root / "screenshots/extra.png"
        extra.write_bytes(b"extra")
        with self.assertRaises(client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)
        extra.unlink()

        competing = self.parent / "attrahite-block-registry-v26"
        competing.mkdir()
        with self.assertRaises(client.E2EError):
            attrahite_evidence.validate_archived_evidence(self.archive_root)

    def test_rejects_missing_archive_fail_closed(self) -> None:
        missing = self.parent / attrahite_evidence.ARCHIVE_DIRECTORY_NAME
        missing.rename(self.parent / "held-aside")
        with self.assertRaises(client.E2EError):
            attrahite_evidence.validate_archived_evidence(missing)


if __name__ == "__main__":
    unittest.main()
