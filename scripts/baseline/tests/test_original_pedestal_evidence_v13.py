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
VERIFIER_PATH = BASELINE_DIRECTORY / "original_pedestal_evidence_v13.py"
VERIFIER_SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_original_pedestal_evidence_v13_tested",
    VERIFIER_PATH,
)
if VERIFIER_SPECIFICATION is None or VERIFIER_SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load Pedestal v13 verifier: {VERIFIER_PATH}")
verifier = importlib.util.module_from_spec(VERIFIER_SPECIFICATION)
sys.modules[VERIFIER_SPECIFICATION.name] = verifier
VERIFIER_SPECIFICATION.loader.exec_module(verifier)

CONTROLLER_PATH = BASELINE_DIRECTORY / "original_client.py"
CONTROLLER_SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_original_client_pedestal_v14_tested",
    CONTROLLER_PATH,
)
if CONTROLLER_SPECIFICATION is None or CONTROLLER_SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load original controller: {CONTROLLER_PATH}")
client = importlib.util.module_from_spec(CONTROLLER_SPECIFICATION)
sys.modules[CONTROLLER_SPECIFICATION.name] = client
CONTROLLER_SPECIFICATION.loader.exec_module(client)
active_verifier = client.load_pedestal_evidence_verifier()


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value: dict[str, object]) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


class PedestalEvidenceV13Test(unittest.TestCase):

    @staticmethod
    def copy_consumed_histories(repository: Path) -> None:
        for relative_path in (
            verifier._V11_PROFILE_RELATIVE_PATH,
            verifier._V12_PROFILE_RELATIVE_PATH,
            verifier._V12_VERIFIER_RELATIVE_PATH,
        ):
            source = REPOSITORY_ROOT / relative_path
            destination = repository / relative_path
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
        for relative_path in (
            verifier._V11_DIAGNOSTIC_RELATIVE_PATH,
            verifier._V12_DIAGNOSTIC_RELATIVE_PATH,
        ):
            source = REPOSITORY_ROOT / relative_path
            destination = repository / relative_path
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(source, destination)

    @staticmethod
    def copy_fresh_archive(repository: Path) -> Path:
        source = REPOSITORY_ROOT / active_verifier.FRESH_ARCHIVE_RELATIVE_PATH
        destination = repository / active_verifier.FRESH_ARCHIVE_RELATIVE_PATH
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(source, destination)
        return destination

    @staticmethod
    def repin_manifest(archive: Path) -> dict[str, tuple[int, str]]:
        pins = dict(verifier._V12_DIAGNOSTIC_FILES)
        manifest = archive / "diagnostic-manifest.json"
        pins["diagnostic-manifest.json"] = (
            manifest.stat().st_size,
            sha256_file(manifest),
        )
        return pins

    @staticmethod
    def synchronize_changed_report(
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
            for relative_path in verifier._V12_DIAGNOSTIC_FILES
        }
        return pins, report_sha256

    def test_v13_imports_the_immutable_v11_contract_directly(self) -> None:
        self.assertEqual(
            verifier._BASE_VERIFIER_PATH.name,
            "original_pedestal_evidence_v11.py",
        )
        self.assertEqual(
            verifier._BASE_MODULE_NAME,
            "etherology_original_pedestal_v11_contract_for_v13",
        )
        self.assertNotIn("_validate_assertions", verifier.__dict__)
        self.assertIs(
            verifier.EXPECTED_ASSERTION_NAMES,
            verifier._base.EXPECTED_ASSERTION_NAMES,
        )
        self.assertEqual(len(verifier.EXPECTED_ASSERTION_NAMES), 74)

    def test_consumed_v11_history_is_exactly_pinned(self) -> None:
        verifier._validate_consumed_v11_history(
            REPOSITORY_ROOT,
            verifier.PedestalEvidenceError,
        )

    def test_consumed_v11_history_tampering_fails_closed(self) -> None:
        mutations = (
            verifier._V11_PROFILE_RELATIVE_PATH,
            (
                verifier._V11_DIAGNOSTIC_RELATIVE_PATH
                + "/controller/original-client.log"
            ),
        )
        for relative_path in mutations:
            with self.subTest(relative_path=relative_path):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    self.copy_consumed_histories(repository)
                    path = repository / relative_path
                    path.write_bytes(path.read_bytes() + b"tampered")
                    with self.assertRaises(verifier.PedestalEvidenceError):
                        verifier._validate_consumed_v11_history(
                            repository,
                            verifier.PedestalEvidenceError,
                        )

    def test_consumed_v12_history_is_exactly_pinned_and_diagnostic_only(
        self,
    ) -> None:
        verifier._validate_consumed_v12_history(
            REPOSITORY_ROOT,
            verifier.PedestalEvidenceError,
        )

    def test_every_consumed_v12_file_and_input_rejects_byte_drift(self) -> None:
        relative_paths = (
            verifier._V12_PROFILE_RELATIVE_PATH,
            verifier._V12_VERIFIER_RELATIVE_PATH,
            *(
                verifier._V12_DIAGNOSTIC_RELATIVE_PATH + "/" + relative_path
                for relative_path in verifier._V12_DIAGNOSTIC_FILES
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
                        verifier._validate_consumed_v12_history(
                            repository,
                            verifier.PedestalEvidenceError,
                        )

    def test_consumed_v12_archive_rejects_inventory_and_link_drift(self) -> None:
        def add_file(repository: Path, archive: Path) -> None:
            del repository
            (archive / "unexpected.txt").write_text(
                "unexpected\n",
                encoding="utf-8",
            )

        def remove_file(repository: Path, archive: Path) -> None:
            del repository
            (archive / "README.md").unlink()

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
                        repository / verifier._V12_DIAGNOSTIC_RELATIVE_PATH
                    )
                    mutation(repository, archive)
                    with self.assertRaises(verifier.PedestalEvidenceError):
                        verifier._validate_consumed_v12_history(
                            repository,
                            verifier.PedestalEvidenceError,
                        )

    def test_v12_diagnostic_manifest_semantics_survive_repinned_bytes(
        self,
    ) -> None:
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

        def pass_result(manifest: dict[str, object]) -> None:
            manifest["outcome"]["report_passed"] = True

        def claim_screenshots(manifest: dict[str, object]) -> None:
            manifest["outcome"]["screenshot_count"] = 4

        def claim_clean_shutdown(manifest: dict[str, object]) -> None:
            manifest["outcome"]["clean_shutdown"] = True

        def accept_native_evidence(manifest: dict[str, object]) -> None:
            manifest["outcome"]["diagnosis"]["limitation"] = (
                "The v12 result is accepted."
            )

        mutations = (
            set_accepted,
            set_verification_scope,
            permit_runtime_reuse,
            publish_controller_verification,
            pass_report,
            pass_result,
            claim_screenshots,
            claim_clean_shutdown,
            accept_native_evidence,
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation.__name__):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    self.copy_consumed_histories(repository)
                    archive = (
                        repository / verifier._V12_DIAGNOSTIC_RELATIVE_PATH
                    )
                    manifest_path = archive / "diagnostic-manifest.json"
                    manifest = json.loads(
                        manifest_path.read_text(encoding="utf-8")
                    )
                    mutation(manifest)
                    write_json(manifest_path, manifest)
                    pins = self.repin_manifest(archive)
                    with (
                        mock.patch.object(
                            verifier,
                            "_V12_DIAGNOSTIC_FILES",
                            pins,
                        ),
                        self.assertRaises(verifier.PedestalEvidenceError),
                    ):
                        verifier._validate_consumed_v12_history(
                            repository,
                            verifier.PedestalEvidenceError,
                        )

    def test_v12_failed_report_semantics_survive_a_fully_repinned_chain(
        self,
    ) -> None:
        def pass_report(report: dict[str, object]) -> None:
            report["status"] = "passed"

        def pass_result(report: dict[str, object]) -> None:
            report["passed"] = True

        def change_tick(report: dict[str, object]) -> None:
            report["client_ticks"] = 156

        def forge_assertion_name(report: dict[str, object]) -> None:
            report["assertions"][0]["name"] = "forged"

        def forge_assertion_outcome(report: dict[str, object]) -> None:
            report["assertions"][0]["passed"] = False

        def claim_screenshot(report: dict[str, object]) -> None:
            report["screenshots"][0]["width"] = 1_920

        def claim_restart(report: dict[str, object]) -> None:
            report["pedestal"]["full_restart"] = True

        mutations = (
            pass_report,
            pass_result,
            change_tick,
            forge_assertion_name,
            forge_assertion_outcome,
            claim_screenshot,
            claim_restart,
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation.__name__):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    self.copy_consumed_histories(repository)
                    archive = (
                        repository / verifier._V12_DIAGNOSTIC_RELATIVE_PATH
                    )
                    report_path = archive / "reports/report.json"
                    report = json.loads(report_path.read_text(encoding="utf-8"))
                    mutation(report)
                    write_json(report_path, report)
                    pins, report_sha256 = self.synchronize_changed_report(archive)
                    with (
                        mock.patch.object(
                            verifier,
                            "_V12_DIAGNOSTIC_FILES",
                            pins,
                        ),
                        mock.patch.object(
                            verifier,
                            "_V12_REPORT_SHA256",
                            report_sha256,
                        ),
                        self.assertRaises(verifier.PedestalEvidenceError),
                    ):
                        verifier._validate_consumed_v12_history(
                            repository,
                            verifier.PedestalEvidenceError,
                        )

    def test_v12_failed_marker_semantics_survive_a_repinned_chain(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository = Path(temporary_directory)
            self.copy_consumed_histories(repository)
            archive = repository / verifier._V12_DIAGNOSTIC_RELATIVE_PATH
            marker = archive / "reports/done.marker"
            marker.write_text(
                "pedestal-baseline:passed\n"
                f"report_sha256:{verifier._V12_REPORT_SHA256}\n",
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
                for relative_path in verifier._V12_DIAGNOSTIC_FILES
            }
            with (
                mock.patch.object(
                    verifier,
                    "_V12_DIAGNOSTIC_FILES",
                    pins,
                ),
                self.assertRaises(verifier.PedestalEvidenceError),
            ):
                verifier._validate_consumed_v12_history(
                    repository,
                    verifier.PedestalEvidenceError,
                )

    def test_active_fresh_v14_archive_is_exact_and_rejects_drift(self) -> None:
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
                        active_verifier.validate_fresh_archive(
                            repository_root=repository,
                            archive_path=archive,
                        )
                    else:
                        mutation(repository, archive)
                        with self.assertRaises(
                            active_verifier.PedestalEvidenceError
                        ):
                            active_verifier.validate_fresh_archive(
                                repository_root=repository,
                                archive_path=archive,
                            )

    def test_historical_v13_contract_and_active_controller_reject_old_profiles(
        self,
    ) -> None:
        configuration = client.load_configuration()
        with self.assertRaises(verifier.PedestalEvidenceError):
            verifier.validate_pinned_contract(
                repository_root=REPOSITORY_ROOT,
                manifest_path=(
                    REPOSITORY_ROOT / verifier._V12_PROFILE_RELATIVE_PATH
                ),
                harness_path=configuration.harness_path,
                sha256_file=sha256_file,
            )

        for manifest_path in (
            REPOSITORY_ROOT / verifier._V12_PROFILE_RELATIVE_PATH,
            REPOSITORY_ROOT / verifier.PROFILE_RELATIVE_PATH,
        ):
            with self.subTest(manifest_path=manifest_path.name):
                with self.assertRaisesRegex(
                    client.BaselineError,
                    "exact active v14 contract",
                ):
                    client.verify_pedestal_evidence_verifier_binding(
                        client.load_configuration(manifest_path)
                    )

    def test_controller_pins_and_describes_the_active_v14_adapter(self) -> None:
        configuration = client.load_configuration()
        descriptor = client.scenario_verifier_descriptor(
            configuration,
            "pedestal-baseline",
        )
        self.assertEqual(
            descriptor,
            {
                "path": "scripts/baseline/original_pedestal_evidence_v14.py",
                "size": client.PEDESTAL_EVIDENCE_VERIFIER_SIZE,
                "sha256": client.PEDESTAL_EVIDENCE_VERIFIER_SHA256,
            },
        )

        with tempfile.TemporaryDirectory() as temporary_directory:
            tampered = (
                Path(temporary_directory)
                / client.PEDESTAL_EVIDENCE_VERIFIER_PATH.name
            )
            content = bytearray(
                client.PEDESTAL_EVIDENCE_VERIFIER_PATH.read_bytes()
            )
            content[-2] ^= 1
            tampered.write_bytes(content)
            with mock.patch.object(
                client,
                "PEDESTAL_EVIDENCE_VERIFIER_PATH",
                tampered,
            ):
                with self.assertRaisesRegex(client.BaselineError, "SHA-256"):
                    client.load_pedestal_evidence_verifier()

    def test_active_v14_profile_is_fresh_and_v13_history_is_immutable(
        self,
    ) -> None:
        configuration = client.load_configuration()
        self.assertEqual(
            configuration.manifest_path,
            REPOSITORY_ROOT / active_verifier.PROFILE_RELATIVE_PATH,
        )
        self.assertEqual(
            configuration.manifest_path.stat().st_size,
            active_verifier.PROFILE_SIZE,
        )
        self.assertEqual(
            sha256_file(configuration.manifest_path),
            active_verifier.PROFILE_SHA256,
        )
        active_verifier._validate_consumed_v13_history(
            REPOSITORY_ROOT,
            active_verifier.PedestalEvidenceError,
        )
        active_verifier.validate_fresh_contract(
            repository_root=REPOSITORY_ROOT,
            manifest_path=configuration.manifest_path,
            harness_path=configuration.harness_path,
            runtime_path=client.runtime_root(configuration),
            archive_path=(
                REPOSITORY_ROOT / active_verifier.FRESH_ARCHIVE_RELATIVE_PATH
            ),
            sha256_file=sha256_file,
        )

    def test_fresh_contract_rejects_reusing_the_consumed_v13_runtime(self) -> None:
        harness = (
            REPOSITORY_ROOT
            / "baseline-harness/fabric/1.21.1/build/libs"
            / active_verifier.HARNESS_FILE
        )
        manifest = REPOSITORY_ROOT / active_verifier.PROFILE_RELATIVE_PATH
        archive = REPOSITORY_ROOT / active_verifier.FRESH_ARCHIVE_RELATIVE_PATH
        active_runtime = (
            REPOSITORY_ROOT / active_verifier.ACTIVE_RUNTIME_RELATIVE_PATH
        )
        self.assertFalse(active_runtime.exists() or active_runtime.is_symlink())
        active_verifier.validate_fresh_contract(
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
            / active_verifier._V13_PROFILE_ID
        )
        with self.assertRaisesRegex(
            active_verifier.PedestalEvidenceError,
            "active runtime path is not the fresh Pedestal v14 lane",
        ):
            active_verifier.validate_fresh_contract(
                repository_root=REPOSITORY_ROOT,
                manifest_path=manifest,
                harness_path=harness,
                runtime_path=consumed_runtime,
                archive_path=archive,
                sha256_file=sha256_file,
            )

    def test_validate_evidence_delegates_all_semantics_after_history_gates(
        self,
    ) -> None:
        sentinel = object()
        arguments = {"scenario_root": Path("/synthetic-scenario")}
        with (
            mock.patch.object(verifier, "_validate_consumed_v11_history") as v11,
            mock.patch.object(verifier, "_validate_consumed_v12_history") as v12,
            mock.patch.object(
                verifier._base,
                "validate_evidence",
                return_value=sentinel,
            ) as delegate,
        ):
            self.assertIs(verifier.validate_evidence(**arguments), sentinel)
        v11.assert_called_once_with(
            REPOSITORY_ROOT,
            verifier.PedestalEvidenceError,
        )
        v12.assert_called_once_with(
            REPOSITORY_ROOT,
            verifier.PedestalEvidenceError,
        )
        delegate.assert_called_once_with(**arguments)

        history_error = verifier.PedestalEvidenceError("history rejected")
        with (
            mock.patch.object(verifier, "_validate_consumed_v11_history"),
            mock.patch.object(
                verifier,
                "_validate_consumed_v12_history",
                side_effect=history_error,
            ),
            mock.patch.object(verifier._base, "validate_evidence") as delegate,
            self.assertRaises(verifier.PedestalEvidenceError),
        ):
            verifier.validate_evidence(**arguments)
        delegate.assert_not_called()


if __name__ == "__main__":
    unittest.main()
