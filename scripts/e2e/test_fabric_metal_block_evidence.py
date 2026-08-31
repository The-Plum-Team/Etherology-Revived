from __future__ import annotations

import copy
from contextlib import contextmanager
from dataclasses import replace
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import client
import evidence
import fabric_metal_block_evidence as metal_evidence
from test_evidence import rgb_png


REPOSITORY_ROOT = SCRIPT_DIRECTORY.parent.parent
REPOSITORY_ARCHIVE = (
    REPOSITORY_ROOT
    / "docs"
    / "evidence"
    / "fabric-1.20.1"
    / metal_evidence.ARCHIVE_DIRECTORY_NAME
)
REPOSITORY_ARCHIVE_SHA256 = {
    "archive-manifest.json": (
        "69717273eac7b543378aa1a804573e27805e33b771601abba7c49923a5a42f44"
    ),
    "reports/report.json": (
        "938f0f73c1104d82d9ede1dd3852ee31871f9b9479017811957102209ff54e73"
    ),
    "reports/done.marker": (
        "37a40f08d8548dba289b9b0bb35bcf63b359f6d37ee86044ebc6b6da080b9ec1"
    ),
    "screenshots/metal-block-registry-before.png": (
        "445d1482e8ead2b81aaecbd970c9eb9bd557b77666cd0b29fdee98b76d46eadb"
    ),
    "screenshots/metal-block-registry-after.png": (
        "053679247db8215e604f294efd3817349b08767b68e4ae3efb9ffbb2ca0dcdb4"
    ),
}


def patterned_pixels(offset: int) -> bytes:
    byte_count = (
        metal_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[0]
        * metal_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS[1]
        * 3
    )
    pattern = bytes((value + offset) % 256 for value in range(256))
    return (pattern * ((byte_count + len(pattern) - 1) // len(pattern)))[:byte_count]


def write_patterned_png(path: Path, offset: int) -> None:
    width, height = metal_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
    path.write_bytes(rgb_png(width, height, patterned_pixels(offset)))


def screenshot_record(
    scenario_root: Path,
    index: int,
) -> dict[str, object]:
    step, relative_path = metal_evidence.EXPECTED_SCREENSHOTS[index]
    path = scenario_root / relative_path
    return {
        "step": step,
        "role": "host",
        "file": relative_path,
        "width": 1920,
        "height": 1080,
        "size": path.stat().st_size,
        "sha256": evidence.sha256_file(path),
        "completed_render_count": metal_evidence.REQUIRED_STABLE_RENDERS,
        "source": "minecraft-framebuffer",
        "edited": False,
    }


def build_report(scenario_root: Path) -> dict[str, object]:
    screenshots_directory = scenario_root / "screenshots"
    reports_directory = scenario_root / "reports"
    screenshots_directory.mkdir(parents=True)
    reports_directory.mkdir(parents=True)
    write_patterned_png(
        screenshots_directory / "metal-block-registry-before.png",
        0,
    )
    write_patterned_png(
        screenshots_directory / "metal-block-registry-after.png",
        64,
    )
    screenshots = [
        screenshot_record(scenario_root, 0),
        screenshot_record(scenario_root, 1),
    ]
    assertion_evidence = metal_evidence.expected_assertion_evidence(screenshots)
    assertions: list[dict[str, object]] = []
    for name in metal_evidence.EXPECTED_ASSERTION_NAMES:
        expected, actual = assertion_evidence[name]
        if name == "default_state_network_ids":
            actual = ";".join(
                f"{identifier}={400 + identifier_index}"
                for identifier_index, identifier in enumerate(
                    metal_evidence.METAL_BLOCK_IDS
                )
            )
        assertions.append(
            {
                "name": name,
                "passed": True,
                "expected": expected,
                "actual": actual,
            }
        )

    report: dict[str, object] = {
        "schema": 2,
        "scenario": metal_evidence.SCENARIO_ID,
        "lane": "fabric-1.20.1",
        "role": "host",
        "status": "passed",
        "client_ticks": 512,
        "lifecycle_failure": "",
        "assertions": assertions,
        "world": copy.deepcopy(metal_evidence.EXPECTED_WORLD),
        "ready_resources": list(metal_evidence.EXPECTED_READY_RESOURCES),
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
        "metal_blocks": {
            "registry_ids": list(metal_evidence.METAL_BLOCK_IDS),
            "display_positions": copy.deepcopy(metal_evidence.DISPLAY_POSITIONS),
            "pedestal_positions": copy.deepcopy(metal_evidence.PEDESTAL_POSITIONS),
            "before_server_ids": copy.deepcopy(metal_evidence.BEFORE_IDS),
            "before_client_ids": copy.deepcopy(metal_evidence.BEFORE_IDS),
            "after_server_ids": copy.deepcopy(metal_evidence.AFTER_IDS),
            "after_client_ids": copy.deepcopy(metal_evidence.AFTER_IDS),
            "camera": copy.deepcopy(metal_evidence.CAMERA),
            "required_stable_renders": metal_evidence.REQUIRED_STABLE_RENDERS,
            "before": copy.deepcopy(metal_evidence.CAPTURE_STATE),
            "after": copy.deepcopy(metal_evidence.CAPTURE_STATE),
        },
    }
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
    (reports_directory / "done.marker").write_text("complete\n", encoding="utf-8")


def refresh_screenshot_record(
    scenario_root: Path,
    report: dict[str, object],
    index: int,
) -> None:
    screenshots = report["screenshots"]
    if not isinstance(screenshots, list):
        raise AssertionError("fixture screenshot inventory changed")
    refreshed = screenshot_record(scenario_root, index)
    screenshots[index] = refreshed
    assertions = report["assertions"]
    if not isinstance(assertions, list):
        raise AssertionError("fixture assertion inventory changed")
    assertion_name = (
        "native_screenshot_written:before"
        if index == 0
        else "native_screenshot_written:after"
    )
    assertion = next(
        value
        for value in assertions
        if isinstance(value, dict) and value.get("name") == assertion_name
    )
    assertion["actual"] = (
        f"{refreshed['size']} bytes, sha256={refreshed['sha256']}"
    )


def archived_artifacts(report: dict[str, object]) -> dict[str, dict[str, object]]:
    artifacts = report["artifacts"]
    if not isinstance(artifacts, list):
        raise AssertionError("fixture artifact inventory changed")
    result: dict[str, dict[str, object]] = {}
    for artifact, (role, _mod_id, _file_name) in zip(
        artifacts,
        metal_evidence.EXPECTED_ARTIFACTS,
    ):
        if not isinstance(artifact, dict):
            raise AssertionError("fixture artifact is not an object")
        result[role] = {
            "mod_id": artifact["mod_id"],
            "file_name": artifact["file_name"],
            "size": artifact["size"],
            "sha256": artifact["sha256"],
        }
    return result


def build_archive_manifest_fixture(
    archive_root: Path,
    report: dict[str, object],
) -> dict[str, object]:
    capture_mtime_ns = {
        relative_path: 1_000_000_000 + index
        for index, relative_path in enumerate(metal_evidence.ARCHIVE_PAYLOAD_PATHS)
    }
    capture_mtime_ns["reports/done.marker"] = 2_000_000_000
    return {
        "schema": 1,
        "kind": metal_evidence.ARCHIVE_KIND,
        "verification_scope": metal_evidence.ARCHIVE_VERIFICATION_SCOPE,
        "scenario": metal_evidence.SCENARIO_ID,
        "profile": {
            "id": metal_evidence.PROFILE_ID,
            "manifest_path": metal_evidence.ACTIVE_PROFILE_RELATIVE_PATH,
            "manifest_size": metal_evidence.PROFILE_SIZE,
            "manifest_sha256": metal_evidence.PROFILE_SHA256,
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
            **metal_evidence.ARCHIVE_PUBLICATION_ATTESTATION,
            "capture_mtime_ns": capture_mtime_ns,
        },
        "capture_metadata": {
            "path": metal_evidence.ARCHIVE_CAPTURE_METADATA_PATH,
            "size": 777,
            "sha256": "3" * 64,
        },
        "assertion_count": len(metal_evidence.EXPECTED_ASSERTION_NAMES),
        "screenshot_count": len(metal_evidence.EXPECTED_SCREENSHOTS),
        "artifacts": archived_artifacts(report),
        "files": {
            relative_path: {
                "size": (archive_root / relative_path).stat().st_size,
                "sha256": evidence.sha256_file(archive_root / relative_path),
            }
            for relative_path in metal_evidence.ARCHIVE_PAYLOAD_PATHS
        },
    }


class ActiveProfileTests(unittest.TestCase):
    def test_v23_snapshot_remains_immutable_history(self) -> None:
        snapshot_profile = SCRIPT_DIRECTORY / "fabric-1.20.1-profile-v23.json"
        configuration = client.load_configuration(snapshot_profile)

        self.assertEqual(
            metal_evidence.PROFILE_ID,
            client.profile_spec(configuration)["id"],
        )
        self.assertEqual(metal_evidence.PROFILE_SIZE, snapshot_profile.stat().st_size)
        self.assertEqual(
            metal_evidence.PROFILE_SHA256,
            client.sha256_file(snapshot_profile),
        )


class RepositoryArchiveTests(unittest.TestCase):
    def test_validates_the_frozen_v23_archive_without_live_state(self) -> None:
        forbidden = AssertionError("archive validation consulted live state")
        with (
            mock.patch.object(client, "load_configuration", side_effect=forbidden),
            mock.patch.object(
                metal_evidence,
                "validate_live_evidence",
                side_effect=forbidden,
            ),
        ):
            summary = metal_evidence.validate_archived_evidence(REPOSITORY_ARCHIVE)

        self.assertEqual(metal_evidence.PROFILE_ID, summary.profile_id)
        self.assertEqual(25, summary.assertion_count)
        self.assertEqual(2, summary.screenshot_count)
        self.assertEqual(0.0875096450617284, summary.changed_pixel_ratio)
        self.assertEqual(
            "5da646a56d326b5ad5492e5ba936758f3c7723f73d6be314cd79d6881fedc1dd",
            summary.production_sha256,
        )
        self.assertEqual(
            "0cc892f41399eec903af57c3270f19db027b0e7611e392a0fc817876e373b111",
            summary.harness_sha256,
        )
        for relative_path, expected_sha256 in REPOSITORY_ARCHIVE_SHA256.items():
            self.assertEqual(
                expected_sha256,
                evidence.sha256_file(REPOSITORY_ARCHIVE / relative_path),
            )


class ReportContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.scenario_root = Path(self.temporary_directory.name) / "scenario"
        self.report = build_report(self.scenario_root)

    def validate(self, report: dict[str, object] | None = None) -> float:
        ratio, artifacts = metal_evidence.validate_report_contract(
            self.scenario_root,
            report or self.report,
        )
        self.assertEqual("1" * 64, artifacts["production"]["sha256"])
        self.assertEqual("2" * 64, artifacts["harness"]["sha256"])
        return ratio

    def test_accepts_exact_server_client_fixture_and_visual_contract(self) -> None:
        ratio = self.validate()

        self.assertGreaterEqual(ratio, evidence.MINIMUM_CHANGED_PIXEL_RATIO)
        self.assertEqual(
            list(metal_evidence.EXPECTED_ASSERTION_NAMES),
            [assertion["name"] for assertion in self.report["assertions"]],
        )

    def test_rejects_119_completed_renders_in_report_or_screenshot(self) -> None:
        mutations = (
            lambda report: report["metal_blocks"]["before"].__setitem__(
                "stable_renders",
                119,
            ),
            lambda report: report["screenshots"][0].__setitem__(
                "completed_render_count",
                119,
            ),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                changed = copy.deepcopy(self.report)
                mutation(changed)
                with self.assertRaisesRegex(client.E2EError, "capture state|screenshot"):
                    self.validate(changed)

    def test_rejects_mutated_or_reordered_registry_and_position_ids(self) -> None:
        changed_registry = copy.deepcopy(self.report)
        changed_registry["metal_blocks"]["registry_ids"].reverse()
        changed_positions = copy.deepcopy(self.report)
        reversed_positions = list(
            changed_positions["metal_blocks"]["after_client_ids"].items()
        )[::-1]
        changed_positions["metal_blocks"]["after_client_ids"] = dict(
            reversed_positions
        )
        changed_id = copy.deepcopy(self.report)
        changed_id["metal_blocks"]["after_server_ids"][
            "etherology:azel_block"
        ] = "etherology:ebony_block"

        for changed in (changed_registry, changed_positions, changed_id):
            with self.subTest(changed=changed["metal_blocks"]):
                with self.assertRaisesRegex(client.E2EError, "registry id|inventory"):
                    self.validate(changed)

    def test_rejects_swapped_screenshot_steps_and_files(self) -> None:
        changed = copy.deepcopy(self.report)
        changed["screenshots"].reverse()

        with self.assertRaisesRegex(client.E2EError, "screenshot contract"):
            self.validate(changed)

    def test_rejects_assertion_order_and_expected_actual_tampering(self) -> None:
        changed_order = copy.deepcopy(self.report)
        changed_order["assertions"][1], changed_order["assertions"][2] = (
            changed_order["assertions"][2],
            changed_order["assertions"][1],
        )
        changed_actual = copy.deepcopy(self.report)
        changed_actual["assertions"][10]["actual"] += ";tampered"
        changed_network_ids = copy.deepcopy(self.report)
        changed_network_ids["assertions"][4]["actual"] = (
            "etherology:azel_block=1;etherology:ebony_block=2;"
            "etherology:ethril_block=3"
        )

        for changed in (changed_order, changed_actual, changed_network_ids):
            with self.subTest(assertions=changed["assertions"]):
                with self.assertRaisesRegex(client.E2EError, "assertion|network"):
                    self.validate(changed)

    def test_rejects_screenshot_digest_mismatch(self) -> None:
        changed = copy.deepcopy(self.report)
        changed["screenshots"][0]["sha256"] = "f" * 64
        changed["assertions"][15]["actual"] = (
            f"{changed['screenshots'][0]['size']} bytes, sha256={'f' * 64}"
        )

        with self.assertRaisesRegex(client.E2EError, "digest differs"):
            self.validate(changed)

    def test_rejects_blank_or_visually_unchanged_screenshots(self) -> None:
        width, height = metal_evidence.EXPECTED_FRAMEBUFFER_DIMENSIONS
        before_path = (
            self.scenario_root / "screenshots" / "metal-block-registry-before.png"
        )
        after_path = (
            self.scenario_root / "screenshots" / "metal-block-registry-after.png"
        )

        before_path.write_bytes(rgb_png(width, height, bytes((20, 20, 20)) * (width * height)))
        blank_report = copy.deepcopy(self.report)
        refresh_screenshot_record(self.scenario_root, blank_report, 0)
        with self.assertRaisesRegex(client.E2EError, "blank or near-uniform"):
            self.validate(blank_report)

        write_patterned_png(before_path, 0)
        after_path.write_bytes(before_path.read_bytes())
        unchanged_report = copy.deepcopy(self.report)
        refresh_screenshot_record(self.scenario_root, unchanged_report, 0)
        refresh_screenshot_record(self.scenario_root, unchanged_report, 1)
        with self.assertRaisesRegex(client.E2EError, "no material visual change"):
            self.validate(unchanged_report)


class ArchiveContractTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.archive_root = (
            Path(self.temporary_directory.name) / metal_evidence.ARCHIVE_DIRECTORY_NAME
        )
        self.report = build_report(self.archive_root)
        write_report_and_marker(self.archive_root, self.report)
        self.manifest = build_archive_manifest_fixture(self.archive_root, self.report)
        self.manifest_path = self.archive_root / metal_evidence.ARCHIVE_MANIFEST_NAME
        self.write_manifest()

    def write_manifest(self) -> None:
        self.manifest_path.write_text(
            json.dumps(self.manifest, indent=2) + "\n",
            encoding="utf-8",
        )

    def test_validates_archive_without_consulting_live_profile_or_runtime(self) -> None:
        forbidden = AssertionError("archive validation consulted live state")
        with (
            mock.patch.object(client, "load_configuration", side_effect=forbidden),
            mock.patch.object(
                metal_evidence,
                "validate_live_evidence",
                side_effect=forbidden,
            ),
        ):
            summary = metal_evidence.validate_archived_evidence(self.archive_root)

        self.assertEqual(metal_evidence.PROFILE_ID, summary.profile_id)
        self.assertEqual(25, summary.assertion_count)
        self.assertEqual(2, summary.screenshot_count)
        self.assertGreaterEqual(
            summary.changed_pixel_ratio,
            evidence.MINIMUM_CHANGED_PIXEL_RATIO,
        )

    def test_rejects_mutated_profile_and_artifact_provenance(self) -> None:
        changed_profile = copy.deepcopy(self.manifest)
        changed_profile["profile"]["manifest_sha256"] = "a" * 64
        changed_artifact = copy.deepcopy(self.manifest)
        changed_artifact["artifacts"]["production"]["sha256"] = "b" * 64

        for changed, message in (
            (changed_profile, "profile provenance"),
            (changed_artifact, "artifact digests"),
        ):
            with self.subTest(message=message):
                self.manifest = changed
                self.write_manifest()
                with self.assertRaisesRegex(client.E2EError, message):
                    metal_evidence.validate_archived_evidence(self.archive_root)

    def test_rejects_payload_digest_mismatch_and_wrong_archive_identity(self) -> None:
        changed_manifest = copy.deepcopy(self.manifest)
        changed_manifest["files"]["reports/report.json"]["sha256"] = "c" * 64
        self.manifest = changed_manifest
        self.write_manifest()
        with self.assertRaisesRegex(client.E2EError, "payload differs"):
            metal_evidence.validate_archived_evidence(self.archive_root)

        wrong_root = Path(self.temporary_directory.name) / "metal-block-registry-v22"
        self.archive_root.rename(wrong_root)
        with self.assertRaisesRegex(client.E2EError, "profile v23"):
            metal_evidence.validate_archived_evidence(wrong_root)


class ArchiveSealingTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.repository_root = Path(self.temporary_directory.name) / "repository"
        scripts_directory = self.repository_root / "scripts" / "e2e"
        scripts_directory.mkdir(parents=True)
        shutil.copy2(
            SCRIPT_DIRECTORY / "fabric-1.20.1-profile-v23.json",
            scripts_directory / "fabric-1.20.1-profile.json",
        )
        shutil.copy2(
            SCRIPT_DIRECTORY / "fabric-1.20.1-profile-v23.json",
            scripts_directory / "fabric-1.20.1-profile-v23.json",
        )
        self.configuration = replace(
            client.load_configuration(
                SCRIPT_DIRECTORY / "fabric-1.20.1-profile-v23.json"
            ),
            repository_root=self.repository_root,
        )
        self.profile_manifest_path = (
            scripts_directory / "fabric-1.20.1-profile.json"
        )
        self.archive_root = (
            self.repository_root
            / "docs"
            / "evidence"
            / "fabric-1.20.1"
            / metal_evidence.ARCHIVE_DIRECTORY_NAME
        )
        self.report = build_report(self.archive_root)
        write_report_and_marker(self.archive_root, self.report)

        self.capture_runtime = (
            Path(self.temporary_directory.name)
            / "runtimes"
            / metal_evidence.PROFILE_ID
        )
        capture_scenario_root = (
            self.capture_runtime / "evidence" / metal_evidence.SCENARIO_ID
        )
        shutil.copytree(self.archive_root / "reports", capture_scenario_root / "reports")
        shutil.copytree(
            self.archive_root / "screenshots",
            capture_scenario_root / "screenshots",
        )
        for index, relative_path in enumerate(metal_evidence.ARCHIVE_PAYLOAD_PATHS):
            timestamp = 1_000_000_000 + index
            if relative_path == "reports/done.marker":
                timestamp = 2_000_000_000
            os.utime(
                capture_scenario_root / relative_path,
                ns=(timestamp, timestamp),
            )

        artifact_lock = {
            "schema": 2,
            "profile_id": metal_evidence.PROFILE_ID,
            "artifact_node": "fabric-1.20.1",
            "artifacts": {},
        }
        for artifact, (role, mod_id, file_name) in zip(
            self.report["artifacts"],
            metal_evidence.EXPECTED_ARTIFACTS,
        ):
            artifact_lock["artifacts"][role] = {
                "mod_id": mod_id,
                "target_file": file_name,
                "size": artifact["size"],
                "sha256": artifact["sha256"],
            }
        artifact_lock["artifacts"] = dict(
            sorted(artifact_lock["artifacts"].items())
        )
        self.artifact_lock_path = self.capture_runtime / "artifact-lock.json"
        self.artifact_lock_path.parent.mkdir(parents=True, exist_ok=True)
        self.write_artifact_lock(artifact_lock)

        ratio, _artifacts = metal_evidence.validate_report_contract(
            self.archive_root,
            self.report,
        )
        self.live_summary = metal_evidence.MetalBlockEvidenceSummary(
            profile_id=metal_evidence.PROFILE_ID,
            assertion_count=25,
            screenshot_count=2,
            changed_pixel_ratio=ratio,
            production_sha256="1" * 64,
            harness_sha256="2" * 64,
        )

    def write_artifact_lock(self, artifact_lock: dict[str, object]) -> None:
        self.artifact_lock_path.write_text(
            json.dumps(artifact_lock, indent=2) + "\n",
            encoding="utf-8",
        )

    @contextmanager
    def capture_environment(self):
        with (
            mock.patch.object(
                client,
                "runtime_root",
                return_value=self.capture_runtime,
            ),
            mock.patch.object(client, "ensure_owned_state_roots"),
            mock.patch.object(client, "verify_runtime"),
            mock.patch.object(
                metal_evidence,
                "validate_live_evidence",
                return_value=self.live_summary,
            ),
        ):
            yield

    def seal(
        self,
        profile_manifest_path: Path | None = None,
        capture_runtime: Path | None = None,
        archive_root: Path | None = None,
    ) -> Path:
        with self.capture_environment():
            return metal_evidence.write_archive_manifest(
                self.configuration,
                profile_manifest_path or self.profile_manifest_path,
                capture_runtime or self.capture_runtime,
                archive_root or self.archive_root,
            )

    def test_seals_once_without_replacing_and_immediately_validates(self) -> None:
        manifest_path = self.seal()

        self.assertTrue(manifest_path.is_file())
        summary = metal_evidence.validate_archived_evidence(self.archive_root)
        self.assertEqual(25, summary.assertion_count)
        original_bytes = manifest_path.read_bytes()
        with self.assertRaisesRegex(client.E2EError, "already exists"):
            self.seal()
        self.assertEqual(original_bytes, manifest_path.read_bytes())

    def test_rejects_wrong_runtime_profile_and_destination(self) -> None:
        wrong_runtime = Path(self.temporary_directory.name) / metal_evidence.PROFILE_ID
        wrong_runtime.mkdir()
        wrong_profile = (
            self.repository_root
            / metal_evidence.SNAPSHOT_PROFILE_RELATIVE_PATH
        )
        wrong_destination = (
            self.repository_root
            / "elsewhere"
            / metal_evidence.ARCHIVE_DIRECTORY_NAME
        )
        shutil.copytree(self.archive_root, wrong_destination)

        cases = (
            (
                {"capture_runtime": wrong_runtime},
                "repository-owned v23 runtime",
            ),
            (
                {"profile_manifest_path": wrong_profile},
                "exact active v23 profile",
            ),
            (
                {"archive_root": wrong_destination},
                "repository archive destination",
            ),
        )
        for arguments, message in cases:
            with self.subTest(message=message):
                with self.assertRaisesRegex(client.E2EError, message):
                    self.seal(**arguments)

    def test_rejects_copied_payload_or_artifact_lock_mismatch(self) -> None:
        capture_report = (
            self.capture_runtime
            / "evidence"
            / metal_evidence.SCENARIO_ID
            / "reports"
            / "report.json"
        )
        original_capture_report = capture_report.read_bytes()
        capture_report.write_bytes(original_capture_report + b" ")
        with self.assertRaisesRegex(client.E2EError, "differs from its capture runtime"):
            self.seal()
        capture_report.write_bytes(original_capture_report)
        os.utime(capture_report, ns=(1_000_000_000, 1_000_000_000))

        artifact_lock = json.loads(self.artifact_lock_path.read_text(encoding="utf-8"))
        artifact_lock["artifacts"]["production"]["sha256"] = "f" * 64
        self.write_artifact_lock(artifact_lock)
        with self.assertRaisesRegex(client.E2EError, "artifact lock differs"):
            self.seal()


if __name__ == "__main__":
    unittest.main()
