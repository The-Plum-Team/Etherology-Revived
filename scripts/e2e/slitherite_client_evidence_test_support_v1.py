"""Shared adversarial fixture support for the first 1.20.1 Slitherite lanes."""

from __future__ import annotations

import copy
from contextlib import contextmanager
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
from types import ModuleType
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
BASELINE_TEST_PATH = (
    SCRIPT_DIRECTORY.parent
    / "baseline"
    / "tests"
    / "test_original_slitherite_evidence_v10.py"
)
BASELINE_TEST_SIZE = 23756
BASELINE_TEST_SHA256 = (
    "c62bbf0ebb7eab75371ca7149d69bfd40cc5aa263c58a48d4e33b9b9d390584b"
)
TEST_HARNESS_SIZE = 218402
TEST_HARNESS_SHA256 = "b" * 64


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_baseline_test_fixture() -> ModuleType:
    if (
        not BASELINE_TEST_PATH.is_file()
        or BASELINE_TEST_PATH.is_symlink()
        or BASELINE_TEST_PATH.stat().st_size != BASELINE_TEST_SIZE
        or sha256_file(BASELINE_TEST_PATH) != BASELINE_TEST_SHA256
    ):
        raise RuntimeError("The accepted original Slitherite v10 test fixture changed")
    specification = importlib.util.spec_from_file_location(
        "etherology_original_slitherite_evidence_v10_fixture_for_port",
        BASELINE_TEST_PATH,
    )
    if specification is None or specification.loader is None:
        raise RuntimeError(
            f"Cannot load the accepted Slitherite fixture: {BASELINE_TEST_PATH}"
        )
    module = importlib.util.module_from_spec(specification)
    sys.modules[specification.name] = module
    specification.loader.exec_module(module)
    return module


BASELINE_FIXTURE = load_baseline_test_fixture()
BRIGHT_IMAGE = BASELINE_FIXTURE.BRIGHT_IMAGE
DARK_IMAGE = BASELINE_FIXTURE.DARK_IMAGE
MAGENTA_IMAGE = BASELINE_FIXTURE.MAGENTA_IMAGE


def current_contract_fixture(
    root: Path, verifier: ModuleType
) -> tuple[Path, Path, dict[str, object]]:
    """Projects the accepted original fixture into one current loader report."""

    scenario_root, world, report, _artifacts = BASELINE_FIXTURE.contract_fixture(root)
    current = copy.deepcopy(report)
    current["world"] = dict(verifier.EXPECTED_WORLD)

    production, harness = current["artifacts"]
    production["origin_kind"] = verifier.verifier_contract().artifact_origin_kind
    production["file_name"] = verifier.verifier_contract().production_file_name
    harness["mod_id"] = "etherology_e2e_harness"
    harness["origin_kind"] = verifier.verifier_contract().artifact_origin_kind
    harness["file_name"] = verifier.verifier_contract().harness_file_name
    harness["size"] = TEST_HARNESS_SIZE
    harness["sha256"] = TEST_HARNESS_SHA256

    baseline_item_class = verifier.contract_core.BASELINE.BLOCK_ITEM_CLASS
    projected_registry: list[str] = []
    for value, baseline_spec, current_class in zip(
        current["slitherite"]["registry"],
        verifier.contract_core.BASELINE.BLOCK_SPECS,
        verifier.BLOCK_CLASSES,
        strict=True,
    ):
        baseline_class = baseline_spec[1]
        projected_registry.append(
            str(value).replace(
                f"block_class:{baseline_class},item_class:{baseline_item_class}",
                f"block_class:{current_class},item_class:{verifier.BLOCK_ITEM_CLASS}",
                1,
            )
        )
    current["slitherite"]["registry"] = projected_registry

    current_identity = (
        f"{verifier.EXPECTED_WORLD['display_name']};"
        f"{verifier.EXPECTED_WORLD['seed']};"
        f"{verifier.EXPECTED_WORLD['dimension']}"
    )
    current_assertions: list[dict[str, object]] = []
    for assertion in current["assertions"]:
        value = copy.deepcopy(assertion)
        name = str(value["name"])
        if name == "fabric_mod_loaded:etherology":
            value["name"] = (
                f"{verifier.verifier_contract().loader}_mod_loaded:etherology"
            )
        elif name == "packaged_root_jar:etherology_original_baseline_harness":
            value["name"] = "packaged_root_jar:etherology_e2e_harness"
        elif name == "live_world_identity":
            value["expected"] = current_identity
            value["actual"] = current_identity
        elif name == "isolated_save_directory_present":
            save_directory = str(verifier.EXPECTED_WORLD["save_directory"])
            value["expected"] = save_directory
            value["actual"] = save_directory
        elif name.startswith("runtime:block_class:"):
            block_id = name.removeprefix("runtime:block_class:")
            index = verifier.BLOCK_IDS.index(block_id)
            value["expected"] = verifier.BLOCK_CLASSES[index]
            value["actual"] = verifier.BLOCK_CLASSES[index]
        elif name.startswith("runtime:block_item_class:"):
            value["expected"] = verifier.BLOCK_ITEM_CLASS
            value["actual"] = verifier.BLOCK_ITEM_CLASS
        current_assertions.append(value)
    current["assertions"] = current_assertions

    identity_values: dict[str, object] = {
        "schema": 3,
        "reference_id": verifier.contract_core.REFERENCE_ID,
        "scenario": verifier.SCENARIO_ID,
        "artifact_node": verifier.verifier_contract().artifact_node,
        "minecraft": "1.20.1",
        "loader": verifier.verifier_contract().loader,
        "loader_version": verifier.verifier_contract().loader_version,
        "java": 17,
        "lane": verifier.verifier_contract().lane,
        "role": "host",
        "profile_id": verifier.PROFILE_ID,
        "profile_manifest_size": verifier.PROFILE_SIZE,
        "profile_manifest_sha256": verifier.PROFILE_SHA256,
        "status": "passed",
        "passed": True,
        "client_ticks": current["client_ticks"],
        "lifecycle_failure": "",
        "assertions": current["assertions"],
        "world": current["world"],
        "artifacts": current["artifacts"],
        "screenshots": current["screenshots"],
        "slitherite": current["slitherite"],
    }
    ordered_report = {
        field: identity_values[field] for field in verifier.EXPECTED_REPORT_FIELDS
    }
    return scenario_root, world, ordered_report


def validate_report(
    verifier: ModuleType,
    scenario_root: Path,
    report: dict[str, object],
    *,
    image: object = BRIGHT_IMAGE,
) -> object:
    """Runs report validation with deterministic decoded native-framebuffer data."""

    with (
        pinned_harness(verifier),
        mock.patch.object(verifier.evidence, "decode_png", return_value=image),
        mock.patch.object(
            verifier.evidence, "assert_image_is_not_blank", return_value=None
        ),
    ):
        return verifier.validate_report_contract(scenario_root, report)


def create_archive_fixture(
    root: Path, verifier: ModuleType
) -> tuple[Path, dict[str, object]]:
    """Creates a fully sealed synthetic archive for fail-closed manifest tests."""

    staging = root / "staging"
    scenario_root, _world, report = current_contract_fixture(staging, verifier)
    archive_root = root / verifier.ARCHIVE_DIRECTORY_NAME
    scenario_root.rename(archive_root)
    report_path = archive_root / "reports" / "report.json"
    done_path = archive_root / "reports" / "done.marker"
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    done_path.write_text("complete\n", encoding="utf-8")

    files = {
        relative_path: {
            "size": (archive_root / relative_path).stat().st_size,
            "sha256": sha256_file(archive_root / relative_path),
        }
        for relative_path in verifier.contract_core.ARCHIVE_PAYLOAD_PATHS
    }
    artifacts = {
        role: {
            "mod_id": artifact["mod_id"],
            "file_name": artifact["file_name"],
            "size": artifact["size"],
            "sha256": artifact["sha256"],
        }
        for role, artifact in zip(
            verifier.contract_core.EXPECTED_ARTIFACT_ROLES,
            report["artifacts"],
            strict=True,
        )
    }
    capture_mtime_ns = dict.fromkeys(
        verifier.contract_core.ARCHIVE_PAYLOAD_PATHS, 10
    )
    manifest: dict[str, object] = {
        "schema": 1,
        "kind": verifier.ARCHIVE_KIND,
        "verification_scope": verifier.ARCHIVE_VERIFICATION_SCOPE,
        "scenario": verifier.SCENARIO_ID,
        "profile": {
            "id": verifier.PROFILE_ID,
            "manifest_path": verifier.ACTIVE_PROFILE_RELATIVE_PATH,
            "manifest_size": verifier.PROFILE_SIZE,
            "manifest_sha256": verifier.PROFILE_SHA256,
        },
        "runtime": {
            "artifact_node": verifier.verifier_contract().artifact_node,
            "minecraft": "1.20.1",
            "loader": verifier.verifier_contract().loader,
            "loader_version": verifier.verifier_contract().loader_version,
            "java": 17,
            "capture_kind": "composed-minecraft-framebuffer",
            "framebuffer_width": 1920,
            "framebuffer_height": 1080,
        },
        "publication": {
            **verifier.contract_core.ARCHIVE_PUBLICATION_ATTESTATION,
            "capture_mtime_ns": capture_mtime_ns,
        },
        "capture_metadata": {
            "path": verifier.verifier_contract().artifact_lock_name,
            "size": 100,
            "sha256": "c" * 64,
        },
        "assertion_count": 185,
        "screenshot_count": 2,
        "artifacts": artifacts,
        "files": files,
    }
    manifest_path = archive_root / verifier.ARCHIVE_MANIFEST_NAME
    manifest_path.write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return archive_root, manifest


@contextmanager
def pinned_harness(verifier: ModuleType):
    """Temporarily binds the synthetic archive to its harness digest."""

    patches = [
        mock.patch.object(verifier, "HARNESS_SIZE", TEST_HARNESS_SIZE),
        mock.patch.object(verifier, "HARNESS_SHA256", TEST_HARNESS_SHA256),
    ]
    if hasattr(verifier, "ARCHIVE_MANIFEST_SIZE"):
        patches.extend(
            [
                mock.patch.object(verifier, "ARCHIVE_MANIFEST_SIZE", None),
                mock.patch.object(verifier, "ARCHIVE_MANIFEST_SHA256", None),
            ]
        )
    with patches[0], patches[1]:
        if len(patches) == 2:
            yield
        else:
            with patches[2], patches[3]:
                yield


class SlitheriteClientEvidenceContractTests(unittest.TestCase):
    """Runs the common semantic and adversarial contract against one loader."""

    verifier: ModuleType
    historical_profile_relative_path: str
    historical_profile_sha256: str
    historical_verifier_relative_path: str
    historical_verifier_sha256: str

    def test_profile_is_fresh_exact_and_etherology_only(self) -> None:
        active = (
            self.verifier.SCRIPT_DIRECTORY.parent.parent
            / self.verifier.ACTIVE_PROFILE_RELATIVE_PATH
        )
        snapshot = (
            self.verifier.SCRIPT_DIRECTORY.parent.parent
            / self.verifier.SNAPSHOT_PROFILE_RELATIVE_PATH
        )
        self.assertEqual(active.read_bytes(), snapshot.read_bytes())
        self.assertEqual(self.verifier.PROFILE_SIZE, snapshot.stat().st_size)
        self.assertEqual(self.verifier.PROFILE_SHA256, sha256_file(snapshot))
        profile = json.loads(snapshot.read_text(encoding="utf-8"))
        self.assertEqual(self.verifier.PROFILE_ID, profile["profile"]["id"])
        self.assertEqual(
            self.verifier.PROFILE_ID, profile["profile"]["runtime_directory"]
        )
        self.assertEqual(
            list(self.verifier.EXPECTED_SCENARIOS),
            profile["evidence"]["scenarios"],
        )
        self.assertEqual(
            self.verifier.SCENARIO_ID, profile["evidence"]["scenarios"][-1]
        )
        self.assertIn("quickskin", profile["forbidden_mod_ids"])
        self.assertNotIn(
            "quickskin",
            {
                profile["artifacts"][role]["mod_id"]
                for role in ("production", "harness")
            },
        )
        configuration = self.verifier.verifier_contract().controller.load_configuration()
        self.verifier.validate_active_profile(configuration)

    def test_historical_contract_files_remain_byte_exact(self) -> None:
        repository_root = self.verifier.SCRIPT_DIRECTORY.parent.parent
        historical_profile = repository_root / self.historical_profile_relative_path
        historical_verifier = repository_root / self.historical_verifier_relative_path
        self.assertEqual(
            self.historical_profile_sha256, sha256_file(historical_profile)
        )
        self.assertEqual(
            self.historical_verifier_sha256, sha256_file(historical_verifier)
        )

    def test_contract_enumerates_the_full_original_mechanic(self) -> None:
        self.assertEqual(17, len(self.verifier.BLOCK_IDS))
        self.assertEqual(17, len(set(self.verifier.BLOCK_IDS)))
        self.assertEqual(80, len(self.verifier.EXPECTED_RESOURCES))
        self.assertEqual(79, len(self.verifier.EXPECTED_RESOURCES) - 1)
        self.assertEqual(11, len(self.verifier.EXPECTED_TAGS))
        self.assertEqual(17, len(self.verifier.EXPECTED_LOOT_TABLES))
        self.assertEqual(29, len(self.verifier.EXPECTED_OWNED_RECIPES))
        self.assertEqual(29, len(self.verifier.EXPECTED_OWNED_ADVANCEMENTS))
        self.assertEqual(5, len(self.verifier.EXPECTED_RELATED_RECIPES))
        self.assertEqual(185, len(self.verifier.EXPECTED_ASSERTION_NAMES))
        self.assertEqual(185, len(set(self.verifier.EXPECTED_ASSERTION_NAMES)))

    def test_complete_semantic_contract_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, _world, report = current_contract_fixture(
                Path(temporary_directory), self.verifier
            )
            result = validate_report(self.verifier, scenario, report)
            self.assertEqual(5, len(result))
            self.assertEqual(200.0, result[0])
            self.assertEqual(200.0, result[1])
            self.assertEqual(0.0, result[2])
            self.assertEqual(0.0, result[3])
            self.assertEqual(
                {"production", "harness"}, set(result[4])
            )

    def test_semantic_tampering_fails_closed(self) -> None:
        def mutate_assertion(report: dict[str, object]) -> None:
            report["assertions"][10]["passed"] = False

        def reorder_assertions(report: dict[str, object]) -> None:
            report["assertions"][0], report["assertions"][1] = (
                report["assertions"][1],
                report["assertions"][0],
            )

        mutations = {
            "reference": lambda report: report.__setitem__(
                "reference_id", "untrusted"
            ),
            "profile": lambda report: report.__setitem__(
                "profile_id", "reused-profile"
            ),
            "world": lambda report: report["world"].__setitem__(
                "display_name", "Another World"
            ),
            "artifact origin": lambda report: report["artifacts"][0].__setitem__(
                "origin_kind", "UNKNOWN"
            ),
            "registry class": lambda report: report["slitherite"][
                "registry"
            ].__setitem__(
                0,
                str(report["slitherite"]["registry"][0]).replace(
                    self.verifier.BLOCK_CLASSES[0], "java.lang.Object", 1
                ),
            ),
            "state total": lambda report: report["slitherite"].__setitem__(
                "aggregate_state_count", 1261
            ),
            "visual resource": lambda report: report["slitherite"][
                "canonical_resources"
            ].pop(),
            "tag": lambda report: report["slitherite"]["tags"].reverse(),
            "loot": lambda report: report["slitherite"]["loot_tables"].pop(),
            "recipe": lambda report: report["slitherite"]["owned_recipes"].pop(),
            "advancement": lambda report: report["slitherite"][
                "owned_advancements"
            ].pop(),
            "button": lambda report: report["slitherite"].__setitem__(
                "button_behavior",
                "powered=true;scheduled=true;elapsed=19;reset=true",
            ),
            "pressure plate": lambda report: report["slitherite"].__setitem__(
                "pressure_plate_behavior", "item=true;living=true;reset=true"
            ),
            "reopened snapshot": lambda report: report["slitherite"].__setitem__(
                "reopened_snapshot",
                str(report["slitherite"]["reopened_snapshot"]).replace(
                    "minecraft:polished_andesite", "minecraft:stone", 1
                ),
            ),
            "pre-setup light": lambda report: report["slitherite"][
                "pre_setup_lighting"
            ]["sky"].__setitem__(0, 0),
            "failed assertion": mutate_assertion,
            "assertion order": reorder_assertions,
            "screenshot provenance": lambda report: report["screenshots"][
                0
            ].__setitem__("role", "host"),
            "screenshot digest": lambda report: report["screenshots"][
                0
            ].__setitem__("sha256", "0" * 64),
        }
        for description, mutate in mutations.items():
            with self.subTest(description=description):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    scenario, _world, report = current_contract_fixture(
                        Path(temporary_directory), self.verifier
                    )
                    mutate(report)
                    with self.assertRaises(
                        self.verifier.verifier_contract().controller.E2EError
                    ):
                        validate_report(self.verifier, scenario, report)

    def test_visual_darkness_and_missing_texture_fail_closed(self) -> None:
        for description, image in (
            ("dark", DARK_IMAGE),
            ("missing texture", MAGENTA_IMAGE),
        ):
            with self.subTest(description=description):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    scenario, _world, report = current_contract_fixture(
                        Path(temporary_directory), self.verifier
                    )
                    with self.assertRaises(
                        self.verifier.verifier_contract().controller.E2EError
                    ):
                        validate_report(
                            self.verifier, scenario, report, image=image
                        )

    def test_native_and_archive_validation_require_or_return_the_final_harness_pin(
        self,
    ) -> None:
        contract = self.verifier.verifier_contract()
        if self.verifier.HARNESS_SIZE is None:
            with self.assertRaises(contract.controller.E2EError):
                self.verifier.contract_core.require_harness_pin(contract)
        else:
            self.assertEqual(
                (self.verifier.HARNESS_SIZE, self.verifier.HARNESS_SHA256),
                self.verifier.contract_core.require_harness_pin(contract),
            )

    def test_complete_archive_contract_passes_when_pinned(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            archive_root, _manifest = create_archive_fixture(
                Path(temporary_directory), self.verifier
            )
            with (
                pinned_harness(self.verifier),
                mock.patch.object(
                    self.verifier.evidence,
                    "decode_png",
                    return_value=BRIGHT_IMAGE,
                ),
                mock.patch.object(
                    self.verifier.evidence,
                    "assert_image_is_not_blank",
                    return_value=None,
                ),
            ):
                summary = self.verifier.validate_archived_evidence(archive_root)
            self.assertEqual(185, summary.assertion_count)
            self.assertEqual(2, summary.screenshot_count)
            self.assertEqual(TEST_HARNESS_SHA256, summary.harness_sha256)

    def test_archive_manifest_tampering_fails_closed(self) -> None:
        mutations = {
            "profile digest": lambda manifest: manifest["profile"].__setitem__(
                "manifest_sha256", "0" * 64
            ),
            "runtime loader": lambda manifest: manifest["runtime"].__setitem__(
                "loader", "unknown"
            ),
            "publication": lambda manifest: manifest["publication"].__setitem__(
                "archive_payloads_match_capture_runtime", False
            ),
            "completion order": lambda manifest: manifest["publication"][
                "capture_mtime_ns"
            ].__setitem__("reports/done.marker", 1),
            "harness digest": lambda manifest: manifest["artifacts"][
                "harness"
            ].__setitem__("sha256", "0" * 64),
            "payload digest": lambda manifest: manifest["files"][
                "reports/report.json"
            ].__setitem__("sha256", "0" * 64),
        }
        for description, mutate in mutations.items():
            with self.subTest(description=description):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    archive_root, manifest = create_archive_fixture(
                        Path(temporary_directory), self.verifier
                    )
                    mutate(manifest)
                    with pinned_harness(self.verifier), self.assertRaises(
                        self.verifier.verifier_contract().controller.E2EError
                    ):
                        self.verifier.validate_archive_manifest(
                            archive_root, manifest
                        )

    def test_archive_inventory_rejects_extras_and_competing_versions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            archive_root, _manifest = create_archive_fixture(root, self.verifier)
            (archive_root / "unexpected.txt").write_text(
                "contamination\n", encoding="utf-8"
            )
            with self.assertRaises(
                self.verifier.verifier_contract().controller.E2EError
            ):
                self.verifier.contract_core.validate_scenario_inventory(
                    self.verifier.verifier_contract(),
                    archive_root,
                    include_manifest=True,
                )
            (archive_root / "unexpected.txt").unlink()
            competing = root / f"{self.verifier.SCENARIO_ID}-v999"
            competing.mkdir()
            with self.assertRaises(
                self.verifier.verifier_contract().controller.E2EError
            ):
                self.verifier.contract_core.validate_no_competing_archives(
                    self.verifier.verifier_contract(), archive_root
                )

    def test_archive_manifest_creation_never_overwrites(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            archive_root, _manifest = create_archive_fixture(
                Path(temporary_directory), self.verifier
            )
            with pinned_harness(self.verifier), self.assertRaises(
                self.verifier.verifier_contract().controller.E2EError
            ):
                self.verifier.write_archive_manifest(
                    None,
                    Path("unused-profile"),
                    Path("unused-runtime"),
                    archive_root,
                )
