from __future__ import annotations

import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_client
import forge_slitherite_evidence_v18 as forge_slitherite_evidence
import forge_slitherite_run_contract_v18 as run_contract
import slitherite_client_evidence_contract_v1 as contract_core
import slitherite_client_evidence_test_support_v1 as support


class ForgeSlitheriteEvidenceV18Tests(
    support.SlitheriteClientEvidenceContractTests
):
    verifier = forge_slitherite_evidence
    historical_profile_relative_path = (
        "scripts/e2e/forge-1.20.1-profile-v17.json"
    )
    historical_profile_sha256 = (
        "00475fd4af5741119b44b3ca70484e967ee0b7a8c51fdc222ebdde3e2bf0ba58"
    )
    historical_verifier_relative_path = (
        "scripts/e2e/forge_attrahite_evidence_v17.py"
    )
    historical_verifier_sha256 = (
        "8547b4a4a2b783c8a145ec717b8abc6d61aea7440b740056ba60c066159e7ebc"
    )


class ForgeSlitheriteRunContractV18Tests(unittest.TestCase):
    def setUp(self) -> None:
        self.configuration = forge_client.load_configuration()

    def test_evidence_and_controller_share_v18_contract_constants(self) -> None:
        self.assertEqual(contract_core.SCENARIO_ID, run_contract.SCENARIO_ID)
        self.assertEqual(run_contract.SCENARIO_ID, forge_slitherite_evidence.SCENARIO_ID)
        self.assertEqual(run_contract.PROFILE_ID, forge_slitherite_evidence.PROFILE_ID)
        self.assertEqual(
            run_contract.HARNESS_SIZE,
            forge_slitherite_evidence.HARNESS_SIZE,
        )
        self.assertEqual(
            run_contract.HARNESS_SHA256,
            forge_slitherite_evidence.HARNESS_SHA256,
        )

    def test_explicit_scenario_rejects_default_and_every_other_profile_scenario(
        self,
    ) -> None:
        self.assertEqual(
            run_contract.SCENARIO_ID,
            run_contract.require_explicit_scenario(run_contract.SCENARIO_ID),
        )
        rejected = [
            None,
            *(
                scenario
                for scenario in forge_client.scenario_ids(self.configuration)
                if scenario != run_contract.SCENARIO_ID
            ),
            "unknown-scenario",
        ]
        for scenario in rejected:
            with self.subTest(scenario=scenario), self.assertRaisesRegex(
                run_contract.RunContractError,
                "explicitly select",
            ):
                run_contract.require_explicit_scenario(scenario)

    def test_harness_pin_is_paired_strict_and_lowercase(self) -> None:
        with (
            mock.patch.object(run_contract, "HARNESS_SIZE", None),
            mock.patch.object(run_contract, "HARNESS_SHA256", None),
            self.assertRaisesRegex(run_contract.RunContractError, "Build and pin"),
        ):
            run_contract.require_harness_pin()

        for size, digest in ((1, None), (None, "a" * 64)):
            with (
                self.subTest(size=size, digest=digest),
                mock.patch.object(run_contract, "HARNESS_SIZE", size),
                mock.patch.object(run_contract, "HARNESS_SHA256", digest),
                self.assertRaisesRegex(run_contract.RunContractError, "set together"),
            ):
                run_contract.require_harness_pin()

        invalid_pins = (
            (False, "a" * 64),
            (0, "a" * 64),
            (-1, "a" * 64),
            (1.0, "a" * 64),
            (1, "A" * 64),
            (1, "g" * 64),
            (1, "a" * 63),
        )
        for size, digest in invalid_pins:
            with (
                self.subTest(size=size, digest=digest),
                mock.patch.object(run_contract, "HARNESS_SIZE", size),
                mock.patch.object(run_contract, "HARNESS_SHA256", digest),
                self.assertRaises(run_contract.RunContractError),
            ):
                run_contract.require_harness_pin()

        with (
            mock.patch.object(run_contract, "HARNESS_SIZE", 123),
            mock.patch.object(run_contract, "HARNESS_SHA256", "a" * 64),
        ):
            self.assertEqual((123, "a" * 64), run_contract.require_harness_pin())

    def test_controller_translates_run_contract_failures(self) -> None:
        with (
            mock.patch.object(run_contract, "HARNESS_SIZE", None),
            mock.patch.object(run_contract, "HARNESS_SHA256", None),
            self.assertRaisesRegex(forge_client.E2EError, "Build and pin") as caught,
        ):
            forge_client.require_slitherite_harness_pin()
        self.assertIsInstance(caught.exception.__cause__, run_contract.RunContractError)

        with self.assertRaisesRegex(forge_client.E2EError, "explicitly select"):
            forge_client.resolve_slitherite_run_scenario_id(
                self.configuration,
                None,
            )

    def test_stage_rejects_unpinned_contract_before_state_or_runtime_work(self) -> None:
        with (
            mock.patch.object(run_contract, "HARNESS_SIZE", None),
            mock.patch.object(run_contract, "HARNESS_SHA256", None),
            mock.patch.object(
                forge_client,
                "load_configuration",
                return_value=self.configuration,
            ),
            mock.patch.object(forge_client, "ensure_owned_state_roots") as state_roots,
            mock.patch.object(forge_client, "assert_runtime_not_running") as runtime,
            self.assertRaisesRegex(forge_client.E2EError, "Build and pin"),
        ):
            forge_client.stage_command()
        state_roots.assert_not_called()
        runtime.assert_not_called()

        with (
            mock.patch.object(run_contract, "HARNESS_SIZE", None),
            mock.patch.object(run_contract, "HARNESS_SHA256", None),
            mock.patch.object(forge_client, "assert_runtime_not_running") as runtime,
            self.assertRaisesRegex(forge_client.E2EError, "Build and pin"),
        ):
            forge_client.stage_artifacts(self.configuration)
        runtime.assert_not_called()

    def test_check_and_start_reject_implicit_or_wrong_scenarios_before_state_work(
        self,
    ) -> None:
        for command in (forge_client.check_command, forge_client.start_command):
            for scenario in (None, "ethereal-storage"):
                with (
                    self.subTest(command=command.__name__, scenario=scenario),
                    mock.patch.object(
                        forge_client,
                        "load_configuration",
                        return_value=self.configuration,
                    ),
                    mock.patch.object(
                        forge_client,
                        "ensure_owned_state_roots",
                    ) as state_roots,
                    mock.patch.object(
                        forge_client,
                        "clear_stale_and_reject_live_owned_clients",
                    ) as cleanup,
                    mock.patch.object(forge_client, "verify_environment") as verify,
                    self.assertRaisesRegex(forge_client.E2EError, "explicitly select"),
                ):
                    command(scenario)
                state_roots.assert_not_called()
                cleanup.assert_not_called()
                verify.assert_not_called()

    def test_check_and_start_reject_unpinned_harness_before_state_work(self) -> None:
        for command in (forge_client.check_command, forge_client.start_command):
            with (
                self.subTest(command=command.__name__),
                mock.patch.object(run_contract, "HARNESS_SIZE", None),
                mock.patch.object(run_contract, "HARNESS_SHA256", None),
                mock.patch.object(
                    forge_client,
                    "load_configuration",
                    return_value=self.configuration,
                ),
                mock.patch.object(
                    forge_client,
                    "ensure_owned_state_roots",
                ) as state_roots,
                mock.patch.object(
                    forge_client,
                    "clear_stale_and_reject_live_owned_clients",
                ) as cleanup,
                mock.patch.object(forge_client, "verify_environment") as verify,
                self.assertRaisesRegex(forge_client.E2EError, "Build and pin"),
            ):
                command(run_contract.SCENARIO_ID)
            state_roots.assert_not_called()
            cleanup.assert_not_called()
            verify.assert_not_called()

    def test_exact_harness_pin_checks_size_and_sha256(self) -> None:
        content = b"final-remapped-forge-slitherite-v18-harness"
        digest = hashlib.sha256(content).hexdigest()
        with tempfile.TemporaryDirectory() as temporary_directory:
            harness = Path(temporary_directory) / "harness.jar"
            harness.write_bytes(content)
            with (
                mock.patch.object(run_contract, "HARNESS_SIZE", len(content)),
                mock.patch.object(run_contract, "HARNESS_SHA256", digest),
            ):
                forge_client.verify_slitherite_harness_artifact(
                    harness,
                    "Test harness",
                )
                harness.write_bytes(b"x" * len(content))
                with self.assertRaisesRegex(forge_client.E2EError, "SHA-256"):
                    forge_client.verify_slitherite_harness_artifact(
                        harness,
                        "Test harness",
                    )
                harness.write_bytes(content + b"x")
                with self.assertRaisesRegex(forge_client.E2EError, "unexpected size"):
                    forge_client.verify_slitherite_harness_artifact(
                        harness,
                        "Test harness",
                    )

    def test_environment_checks_staged_harness_pin_before_java_probe(self) -> None:
        content = b"not-the-final-harness"
        with tempfile.TemporaryDirectory() as temporary_directory:
            harness = Path(temporary_directory) / "harness.jar"
            harness.write_bytes(content)
            with (
                mock.patch.object(run_contract, "HARNESS_SIZE", len(content)),
                mock.patch.object(run_contract, "HARNESS_SHA256", "a" * 64),
                mock.patch.object(forge_client, "require_safe_java_option_environment"),
                mock.patch.object(forge_client, "require_unattempted_profile"),
                mock.patch.object(forge_client, "verify_runtime") as runtime,
                mock.patch.object(
                    forge_client,
                    "artifact_target_path",
                    return_value=harness,
                ),
                mock.patch.object(forge_client, "verify_evidence_layout") as evidence,
                mock.patch.object(forge_client, "resolve_java_17") as java_probe,
                self.assertRaisesRegex(forge_client.E2EError, "SHA-256"),
            ):
                forge_client.verify_environment(
                    self.configuration,
                    run_contract.SCENARIO_ID,
                )
            runtime.assert_called_once_with(
                self.configuration,
                artifact_policy="required",
            )
            evidence.assert_not_called()
            java_probe.assert_not_called()


if __name__ == "__main__":
    unittest.main()
