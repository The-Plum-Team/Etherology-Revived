from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest import mock


BASELINE_DIRECTORY = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = BASELINE_DIRECTORY.parents[1]
VERIFIER_PATH = BASELINE_DIRECTORY / "original_pedestal_evidence_v14.py"
VERIFIER_SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_original_pedestal_evidence_v14_tested",
    VERIFIER_PATH,
)
if VERIFIER_SPECIFICATION is None or VERIFIER_SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load Pedestal v14 verifier: {VERIFIER_PATH}")
verifier = importlib.util.module_from_spec(VERIFIER_SPECIFICATION)
sys.modules[VERIFIER_SPECIFICATION.name] = verifier
VERIFIER_SPECIFICATION.loader.exec_module(verifier)


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value: dict[str, object]) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def client_drop_diagnostics(
    mirror_ready: dict[str, int] | None = None,
    capture: dict[str, int] | None = None,
) -> dict[str, object]:
    expected = copy.deepcopy(verifier.CANONICAL_TRANSITION_DROPS)
    mirror = expected if mirror_ready is None else mirror_ready
    captured = expected if capture is None else capture
    return {
        "expected": expected,
        "mirror_ready": copy.deepcopy(mirror),
        "mirror_ready_recorded": True,
        "mirror_ready_exact": mirror == expected,
        "capture": copy.deepcopy(captured),
        "capture_recorded": True,
        "capture_exact": captured == expected,
    }


def diagnostic_report(
    diagnostics: dict[str, object] | None = None,
) -> dict[str, object]:
    return {
        "pedestal": {
            "transitions": {
                "client_drop_diagnostics": (
                    client_drop_diagnostics()
                    if diagnostics is None
                    else diagnostics
                ),
                "retained": "delegated",
            }
        },
        "retained": "delegated",
    }


class PedestalEvidenceV14Test(unittest.TestCase):

    @staticmethod
    def copy_consumed_histories(repository: Path) -> None:
        for relative_path in (
            verifier._V11_PROFILE_RELATIVE_PATH,
            verifier._V12_PROFILE_RELATIVE_PATH,
            verifier._V12_VERIFIER_RELATIVE_PATH,
            verifier._V13_PROFILE_RELATIVE_PATH,
            verifier._V13_VERIFIER_RELATIVE_PATH,
        ):
            source = REPOSITORY_ROOT / relative_path
            destination = repository / relative_path
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
        for relative_path in (
            verifier._V11_DIAGNOSTIC_RELATIVE_PATH,
            verifier._V12_DIAGNOSTIC_RELATIVE_PATH,
            verifier._V13_DIAGNOSTIC_RELATIVE_PATH,
        ):
            source = REPOSITORY_ROOT / relative_path
            destination = repository / relative_path
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(source, destination)

    @staticmethod
    def copy_fresh_archive(repository: Path) -> Path:
        source = REPOSITORY_ROOT / verifier.FRESH_ARCHIVE_RELATIVE_PATH
        destination = repository / verifier.FRESH_ARCHIVE_RELATIVE_PATH
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(source, destination)
        return destination

    @staticmethod
    def repin_v13_manifest(archive: Path) -> dict[str, tuple[int, str]]:
        pins = dict(verifier._V13_DIAGNOSTIC_FILES)
        manifest = archive / "diagnostic-manifest.json"
        pins["diagnostic-manifest.json"] = (
            manifest.stat().st_size,
            sha256_file(manifest),
        )
        return pins

    @staticmethod
    def synchronize_changed_v13_report(
        archive: Path,
    ) -> tuple[dict[str, tuple[int, str]], str]:
        report = archive / "reports/report.json"
        report_sha256 = sha256_file(report)
        marker = archive / "reports/done.marker"
        marker.write_text(
            "pedestal-baseline:failed\n"
            f"report_sha256:{report_sha256}\n",
            encoding="utf-8",
        )
        manifest_path = archive / "diagnostic-manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for relative_path in (
            "reports/report.json",
            "reports/done.marker",
        ):
            path = archive / relative_path
            manifest["files"][relative_path]["size"] = path.stat().st_size
            manifest["files"][relative_path]["sha256"] = sha256_file(path)
        write_json(manifest_path, manifest)
        pins = {
            relative_path: (
                (archive / relative_path).stat().st_size,
                sha256_file(archive / relative_path),
            )
            for relative_path in verifier._V13_DIAGNOSTIC_FILES
        }
        return pins, report_sha256

    def test_v14_imports_the_immutable_v11_contract_directly(self) -> None:
        self.assertEqual(
            verifier._BASE_VERIFIER_PATH.name,
            "original_pedestal_evidence_v11.py",
        )
        self.assertEqual(
            verifier._BASE_MODULE_NAME,
            "etherology_original_pedestal_v11_contract_for_v14",
        )
        self.assertNotIn("_validate_assertions", verifier.__dict__)
        self.assertIs(
            verifier.EXPECTED_ASSERTION_NAMES,
            verifier._base.EXPECTED_ASSERTION_NAMES,
        )
        self.assertEqual(len(verifier.EXPECTED_ASSERTION_NAMES), 74)

    def test_all_consumed_histories_are_exactly_pinned(self) -> None:
        for validator in (
            verifier._validate_consumed_v11_history,
            verifier._validate_consumed_v12_history,
            verifier._validate_consumed_v13_history,
        ):
            with self.subTest(validator=validator.__name__):
                validator(REPOSITORY_ROOT, verifier.PedestalEvidenceError)

    def test_every_consumed_v13_file_and_input_rejects_byte_drift(self) -> None:
        relative_paths = (
            verifier._V13_PROFILE_RELATIVE_PATH,
            verifier._V13_VERIFIER_RELATIVE_PATH,
            *(
                verifier._V13_DIAGNOSTIC_RELATIVE_PATH + "/" + relative_path
                for relative_path in verifier._V13_DIAGNOSTIC_FILES
            ),
        )
        for relative_path in relative_paths:
            with self.subTest(relative_path=relative_path):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    self.copy_consumed_histories(repository)
                    path = repository / relative_path
                    path.write_bytes(path.read_bytes() + b"tampered")
                    with self.assertRaises(verifier.PedestalEvidenceError):
                        verifier._validate_consumed_v13_history(
                            repository,
                            verifier.PedestalEvidenceError,
                        )

    def test_consumed_v13_archive_rejects_inventory_and_link_drift(self) -> None:
        def add_file(repository: Path, archive: Path) -> None:
            del repository
            (archive / "unexpected.txt").write_text(
                "unexpected\n",
                encoding="utf-8",
            )

        def remove_file(repository: Path, archive: Path) -> None:
            del repository
            (archive / verifier._V13_SCREENSHOT_RELATIVE_PATH).unlink()

        def link_file(repository: Path, archive: Path) -> None:
            foreign = repository / "foreign-readme.md"
            readme = archive / "README.md"
            foreign.write_bytes(readme.read_bytes())
            readme.unlink()
            readme.symlink_to(foreign)

        for mutation in (add_file, remove_file, link_file):
            with self.subTest(mutation=mutation.__name__):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    self.copy_consumed_histories(repository)
                    archive = (
                        repository / verifier._V13_DIAGNOSTIC_RELATIVE_PATH
                    )
                    mutation(repository, archive)
                    with self.assertRaises(verifier.PedestalEvidenceError):
                        verifier._validate_consumed_v13_history(
                            repository,
                            verifier.PedestalEvidenceError,
                        )

    def test_v13_manifest_semantics_survive_repinned_bytes(self) -> None:
        def set_accepted(manifest: dict[str, object]) -> None:
            manifest["accepted"] = True

        def set_verification_scope(manifest: dict[str, object]) -> None:
            manifest["verification_scope"] = "accepted-mechanic-evidence"

        def permit_runtime_reuse(manifest: dict[str, object]) -> None:
            manifest["runtime"]["must_never_run_again"] = False

        def publish_controller_verification(manifest: dict[str, object]) -> None:
            manifest["controller"]["controller_verification_published"] = True

        def pass_report(manifest: dict[str, object]) -> None:
            manifest["outcome"]["report_status"] = "passed"

        def claim_four_screenshots(manifest: dict[str, object]) -> None:
            manifest["outcome"]["screenshot_count"] = 4

        def change_lifecycle_stage(manifest: dict[str, object]) -> None:
            manifest["outcome"]["lifecycle_failure"]["stage"] = "CAPTURING"

        def accept_native_evidence(manifest: dict[str, object]) -> None:
            manifest["outcome"]["diagnosis"]["limitation"] = (
                "The v13 result is accepted."
            )

        def accept_screenshot(manifest: dict[str, object]) -> None:
            screenshot = manifest["files"][verifier._V13_SCREENSHOT_RELATIVE_PATH]
            screenshot["accepted_as_mechanic_evidence"] = True

        mutations = (
            set_accepted,
            set_verification_scope,
            permit_runtime_reuse,
            publish_controller_verification,
            pass_report,
            claim_four_screenshots,
            change_lifecycle_stage,
            accept_native_evidence,
            accept_screenshot,
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation.__name__):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    self.copy_consumed_histories(repository)
                    archive = (
                        repository / verifier._V13_DIAGNOSTIC_RELATIVE_PATH
                    )
                    manifest_path = archive / "diagnostic-manifest.json"
                    manifest = json.loads(
                        manifest_path.read_text(encoding="utf-8")
                    )
                    mutation(manifest)
                    write_json(manifest_path, manifest)
                    pins = self.repin_v13_manifest(archive)
                    with (
                        mock.patch.object(
                            verifier,
                            "_V13_DIAGNOSTIC_FILES",
                            pins,
                        ),
                        self.assertRaises(verifier.PedestalEvidenceError),
                    ):
                        verifier._validate_consumed_v13_history(
                            repository,
                            verifier.PedestalEvidenceError,
                        )

    def test_v13_report_semantics_survive_a_fully_repinned_chain(self) -> None:
        def pass_report(report: dict[str, object]) -> None:
            report["status"] = "passed"

        def pass_result(report: dict[str, object]) -> None:
            report["passed"] = True

        def change_tick(report: dict[str, object]) -> None:
            report["client_ticks"] = 7_995

        def change_lifecycle(report: dict[str, object]) -> None:
            report["lifecycle_failure"] = "another failure"

        def forge_assertion_name(report: dict[str, object]) -> None:
            report["assertions"][0]["name"] = "forged"

        def forge_assertion_outcome(report: dict[str, object]) -> None:
            report["assertions"][0]["passed"] = False

        def claim_edited_screenshot(report: dict[str, object]) -> None:
            report["screenshots"][0]["edited"] = True

        def claim_restart(report: dict[str, object]) -> None:
            report["pedestal"]["full_restart"] = True

        mutations = (
            pass_report,
            pass_result,
            change_tick,
            change_lifecycle,
            forge_assertion_name,
            forge_assertion_outcome,
            claim_edited_screenshot,
            claim_restart,
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation.__name__):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    self.copy_consumed_histories(repository)
                    archive = (
                        repository / verifier._V13_DIAGNOSTIC_RELATIVE_PATH
                    )
                    report_path = archive / "reports/report.json"
                    report = json.loads(report_path.read_text(encoding="utf-8"))
                    mutation(report)
                    write_json(report_path, report)
                    pins, report_sha256 = self.synchronize_changed_v13_report(
                        archive
                    )
                    with (
                        mock.patch.object(
                            verifier,
                            "_V13_DIAGNOSTIC_FILES",
                            pins,
                        ),
                        mock.patch.object(
                            verifier,
                            "_V13_REPORT_SHA256",
                            report_sha256,
                        ),
                        self.assertRaises(verifier.PedestalEvidenceError),
                    ):
                        verifier._validate_consumed_v13_history(
                            repository,
                            verifier.PedestalEvidenceError,
                        )

    def test_v13_marker_semantics_survive_a_repinned_chain(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository = Path(temporary_directory)
            self.copy_consumed_histories(repository)
            archive = repository / verifier._V13_DIAGNOSTIC_RELATIVE_PATH
            marker = archive / "reports/done.marker"
            marker.write_text(
                "pedestal-baseline:passed\n"
                f"report_sha256:{verifier._V13_REPORT_SHA256}\n",
                encoding="utf-8",
            )
            manifest_path = archive / "diagnostic-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["files"]["reports/done.marker"]["size"] = (
                marker.stat().st_size
            )
            manifest["files"]["reports/done.marker"]["sha256"] = sha256_file(
                marker
            )
            write_json(manifest_path, manifest)
            pins = {
                relative_path: (
                    (archive / relative_path).stat().st_size,
                    sha256_file(archive / relative_path),
                )
                for relative_path in verifier._V13_DIAGNOSTIC_FILES
            }
            with (
                mock.patch.object(
                    verifier,
                    "_V13_DIAGNOSTIC_FILES",
                    pins,
                ),
                self.assertRaises(verifier.PedestalEvidenceError),
            ):
                verifier._validate_consumed_v13_history(
                    repository,
                    verifier.PedestalEvidenceError,
                )

    def test_v13_png_dimensions_survive_a_fully_repinned_chain(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository = Path(temporary_directory)
            self.copy_consumed_histories(repository)
            archive = repository / verifier._V13_DIAGNOSTIC_RELATIVE_PATH
            screenshot_path = archive / verifier._V13_SCREENSHOT_RELATIVE_PATH
            screenshot_bytes = bytearray(screenshot_path.read_bytes())
            screenshot_bytes[16:20] = (1_919).to_bytes(4, "big")
            screenshot_path.write_bytes(screenshot_bytes)
            screenshot_sha256 = sha256_file(screenshot_path)

            report_path = archive / "reports/report.json"
            report = json.loads(report_path.read_text(encoding="utf-8"))
            report["screenshots"][0]["sha256"] = screenshot_sha256
            write_json(report_path, report)
            manifest_path = archive / "diagnostic-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["files"][verifier._V13_SCREENSHOT_RELATIVE_PATH][
                "sha256"
            ] = screenshot_sha256
            write_json(manifest_path, manifest)
            pins, report_sha256 = self.synchronize_changed_v13_report(archive)
            with (
                mock.patch.object(
                    verifier,
                    "_V13_DIAGNOSTIC_FILES",
                    pins,
                ),
                mock.patch.object(
                    verifier,
                    "_V13_REPORT_SHA256",
                    report_sha256,
                ),
                mock.patch.object(
                    verifier,
                    "_V13_SCREENSHOT_SHA256",
                    screenshot_sha256,
                ),
                self.assertRaisesRegex(
                    verifier.PedestalEvidenceError,
                    "screenshot dimensions changed",
                ),
            ):
                verifier._validate_consumed_v13_history(
                    repository,
                    verifier.PedestalEvidenceError,
                )

    def test_fresh_v14_archive_is_exact_and_rejects_drift(self) -> None:
        def change_readme(repository: Path, archive: Path) -> None:
            del repository
            readme = archive / "README.md"
            readme.write_bytes(readme.read_bytes() + b"tampered")

        def add_payload(repository: Path, archive: Path) -> None:
            del repository
            (archive / "reports").mkdir()

        def link_readme(repository: Path, archive: Path) -> None:
            readme = archive / "README.md"
            foreign = repository / "foreign-readme.md"
            foreign.write_bytes(readme.read_bytes())
            readme.unlink()
            readme.symlink_to(foreign)

        for mutation in (None, change_readme, add_payload, link_readme):
            with self.subTest(
                mutation="exact" if mutation is None else mutation.__name__
            ):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    archive = self.copy_fresh_archive(repository)
                    if mutation is None:
                        verifier.validate_fresh_archive(
                            repository_root=repository,
                            archive_path=archive,
                        )
                    else:
                        mutation(repository, archive)
                        with self.assertRaises(verifier.PedestalEvidenceError):
                            verifier.validate_fresh_archive(
                                repository_root=repository,
                                archive_path=archive,
                            )

    def test_v14_pinned_contract_accepts_only_the_v14_profile(self) -> None:
        harness = (
            REPOSITORY_ROOT
            / "baseline-harness/fabric/1.21.1/build/libs"
            / verifier.HARNESS_FILE
        )
        manifest = REPOSITORY_ROOT / verifier.PROFILE_RELATIVE_PATH
        verifier.validate_pinned_contract(
            repository_root=REPOSITORY_ROOT,
            manifest_path=manifest,
            harness_path=harness,
            sha256_file=sha256_file,
        )
        with self.assertRaises(verifier.PedestalEvidenceError):
            verifier.validate_pinned_contract(
                repository_root=REPOSITORY_ROOT,
                manifest_path=(
                    REPOSITORY_ROOT / verifier._V13_PROFILE_RELATIVE_PATH
                ),
                harness_path=harness,
                sha256_file=sha256_file,
            )

    def test_fresh_contract_rejects_reusing_the_consumed_v13_runtime(self) -> None:
        harness = (
            REPOSITORY_ROOT
            / "baseline-harness/fabric/1.21.1/build/libs"
            / verifier.HARNESS_FILE
        )
        manifest = REPOSITORY_ROOT / verifier.PROFILE_RELATIVE_PATH
        archive = REPOSITORY_ROOT / verifier.FRESH_ARCHIVE_RELATIVE_PATH
        active_runtime = REPOSITORY_ROOT / verifier.ACTIVE_RUNTIME_RELATIVE_PATH
        self.assertFalse(active_runtime.exists() or active_runtime.is_symlink())
        verifier.validate_fresh_contract(
            repository_root=REPOSITORY_ROOT,
            manifest_path=manifest,
            harness_path=harness,
            runtime_path=active_runtime,
            archive_path=archive,
            sha256_file=sha256_file,
        )
        consumed_runtime = (
            REPOSITORY_ROOT
            / "scripts/baseline/.state/runtimes"
            / verifier._V13_PROFILE_ID
        )
        with self.assertRaisesRegex(
            verifier.PedestalEvidenceError,
            "active runtime path is not the fresh Pedestal v14 lane",
        ):
            verifier.validate_fresh_contract(
                repository_root=REPOSITORY_ROOT,
                manifest_path=manifest,
                harness_path=harness,
                runtime_path=consumed_runtime,
                archive_path=archive,
                sha256_file=sha256_file,
            )

    def test_client_drop_diagnostics_accept_exact_and_mismatched_maps(self) -> None:
        exact = client_drop_diagnostics()
        verifier._validate_client_drop_diagnostics(
            diagnostic_report(exact),
            verifier.PedestalEvidenceError,
        )
        mismatch = client_drop_diagnostics(
            mirror_ready={},
            capture={"minecraft:diamond": 1},
        )
        self.assertFalse(mismatch["mirror_ready_exact"])
        self.assertFalse(mismatch["capture_exact"])
        verifier._validate_client_drop_diagnostics(
            diagnostic_report(mismatch),
            verifier.PedestalEvidenceError,
        )

    def test_client_drop_diagnostics_reject_schema_map_and_equality_drift(
        self,
    ) -> None:
        def remove_field(value: dict[str, object]) -> None:
            del value["capture"]

        def add_field(value: dict[str, object]) -> None:
            value["extra"] = {}

        def remove_recorded_field(value: dict[str, object]) -> None:
            del value["mirror_ready_recorded"]

        def remove_capture_recorded_field(value: dict[str, object]) -> None:
            del value["capture_recorded"]

        def change_expected(value: dict[str, object]) -> None:
            del value["expected"]["minecraft:diamond"]

        def boolean_count(value: dict[str, object]) -> None:
            value["mirror_ready"]["minecraft:diamond"] = True

        def zero_count(value: dict[str, object]) -> None:
            value["capture"]["minecraft:diamond"] = 0

        def non_mapping(value: dict[str, object]) -> None:
            value["mirror_ready"] = []

        def non_boolean_equality(value: dict[str, object]) -> None:
            value["mirror_ready_exact"] = 1

        def non_boolean_recorded(value: dict[str, object]) -> None:
            value["mirror_ready_recorded"] = 1

        def non_boolean_capture_recorded(value: dict[str, object]) -> None:
            value["capture_recorded"] = 1

        def unrecorded_mirror(value: dict[str, object]) -> None:
            value["mirror_ready_recorded"] = False
            value["mirror_ready_exact"] = False

        def unrecorded_capture(value: dict[str, object]) -> None:
            value["capture_recorded"] = False
            value["capture_exact"] = False

        def false_equality_for_equal_map(value: dict[str, object]) -> None:
            value["capture_exact"] = False

        def true_equality_for_different_map(value: dict[str, object]) -> None:
            value["mirror_ready"] = {}
            value["mirror_ready_exact"] = True

        mutations = (
            remove_field,
            add_field,
            remove_recorded_field,
            remove_capture_recorded_field,
            change_expected,
            boolean_count,
            zero_count,
            non_mapping,
            non_boolean_equality,
            non_boolean_recorded,
            non_boolean_capture_recorded,
            unrecorded_mirror,
            unrecorded_capture,
            false_equality_for_equal_map,
            true_equality_for_different_map,
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation.__name__):
                diagnostics = client_drop_diagnostics()
                mutation(diagnostics)
                with self.assertRaises(verifier.PedestalEvidenceError):
                    verifier._validate_client_drop_diagnostics(
                        diagnostic_report(diagnostics),
                        verifier.PedestalEvidenceError,
                    )

    def test_v14_source_gate_requires_checkpoint_provenance_fragments(
        self,
    ) -> None:
        scenario_path = REPOSITORY_ROOT / (
            "baseline-harness/fabric/1.21.1/src/main/java/dev/theplumteam/"
            "etherology/baseline/fabric/PedestalBaselineScenario.java"
        )
        writer_path = scenario_path.with_name("PedestalEvidenceWriter.java")
        scenario_text = scenario_path.read_text(encoding="utf-8")
        writer_text = writer_path.read_text(encoding="utf-8")
        verifier._validate_v14_source_text(
            scenario_text,
            writer_text,
            verifier.PedestalEvidenceError,
        )
        for fragment in verifier._V14_CLIENT_DROP_PROVENANCE_SOURCE_FRAGMENTS:
            with self.subTest(fragment=fragment):
                self.assertIn(fragment, scenario_text)
                mutated = scenario_text.replace(fragment, "removed", 1)
                with self.assertRaises(verifier.PedestalEvidenceError):
                    verifier._validate_v14_source_text(
                        mutated,
                        writer_text,
                        verifier.PedestalEvidenceError,
                    )

    def test_validate_evidence_strips_only_diagnostics_and_delegates(self) -> None:
        report = diagnostic_report(
            client_drop_diagnostics(
                mirror_ready={},
                capture={"minecraft:red_carpet": 1},
            )
        )
        original = copy.deepcopy(report)
        sentinel = object()
        with (
            mock.patch.object(verifier, "_validate_consumed_v11_history") as v11,
            mock.patch.object(verifier, "_validate_consumed_v12_history") as v12,
            mock.patch.object(verifier, "_validate_consumed_v13_history") as v13,
            mock.patch.object(
                verifier._base,
                "validate_evidence",
                return_value=sentinel,
            ) as delegate,
        ):
            result = verifier.validate_evidence(
                report=report,
                scenario_root=Path("/synthetic-scenario"),
            )
        self.assertIs(result, sentinel)
        self.assertEqual(report, original)
        delegated = delegate.call_args.kwargs["report"]
        self.assertEqual(delegated["retained"], "delegated")
        self.assertEqual(
            delegated["pedestal"]["transitions"],
            {"retained": "delegated"},
        )
        self.assertNotIn(
            "client_drop_diagnostics",
            delegated["pedestal"]["transitions"],
        )
        for history in (v11, v12, v13):
            history.assert_called_once_with(
                REPOSITORY_ROOT,
                verifier.PedestalEvidenceError,
            )

    def test_history_failure_prevents_diagnostic_and_evidence_delegation(self) -> None:
        history_error = verifier.PedestalEvidenceError("history rejected")
        with (
            mock.patch.object(verifier, "_validate_consumed_v11_history"),
            mock.patch.object(verifier, "_validate_consumed_v12_history"),
            mock.patch.object(
                verifier,
                "_validate_consumed_v13_history",
                side_effect=history_error,
            ),
            mock.patch.object(
                verifier,
                "_validate_client_drop_diagnostics",
            ) as diagnostics,
            mock.patch.object(verifier._base, "validate_evidence") as delegate,
            self.assertRaises(verifier.PedestalEvidenceError),
        ):
            verifier.validate_evidence(report=diagnostic_report())
        diagnostics.assert_not_called()
        delegate.assert_not_called()


if __name__ == "__main__":
    unittest.main()
