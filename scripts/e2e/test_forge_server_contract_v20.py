from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_server
import forge_server_contract_v19 as contract_v19
import forge_server_contract_v20 as contract_v20
from test_forge_server import (
    load_temporary_configuration,
    temporary_repository,
    valid_report,
)


class ForgeServerContractV20Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.configuration = forge_server.load_configuration()

    def test_exact_schema_12_report_is_accepted(self) -> None:
        forge_server.validate_probe_report(valid_report(), self.configuration)

    def test_slitherite_inventory_raw_ids_and_assertion_slice_are_exact(self) -> None:
        report = valid_report()
        slitherite = report["slitherite_blocks"]
        entries = slitherite["entries"]
        raw_ids = [
            raw_id
            for identifier in contract_v20.SLITHERITE_BLOCK_IDS
            for raw_id in entries[identifier]["state_raw_ids"]
        ]
        insertion_index = (
            contract_v20.EXPECTED_ASSERTION_NAMES.index(
                "attrahite_block_placement_stable_after_reload"
            )
            + 1
        )

        self.assertEqual(17, len(contract_v20.SLITHERITE_BLOCK_IDS))
        self.assertEqual(
            list(contract_v20.SLITHERITE_BLOCK_IDS),
            slitherite["block_ids"],
        )
        self.assertEqual(contract_v20.SLITHERITE_AGGREGATE_STATE_COUNT, len(raw_ids))
        self.assertEqual(len(raw_ids), len(set(raw_ids)))
        self.assertTrue(all(type(raw_id) is int and raw_id >= 0 for raw_id in raw_ids))
        self.assertEqual(215, len(contract_v20.SLITHERITE_ASSERTION_NAMES))
        self.assertEqual(525, len(contract_v20.EXPECTED_ASSERTION_NAMES))
        self.assertEqual(
            contract_v20.SLITHERITE_ASSERTION_NAMES,
            contract_v20.EXPECTED_ASSERTION_NAMES[
                insertion_index:
                insertion_index + len(contract_v20.SLITHERITE_ASSERTION_NAMES)
            ],
        )
        self.assertEqual(
            contract_v20.SLITHERITE_ASSERTION_VALUES,
            contract_v20.EXPECTED_ASSERTION_VALUES[
                insertion_index:
                insertion_index + len(contract_v20.SLITHERITE_ASSERTION_VALUES)
            ],
        )

    def test_slitherite_report_rejects_each_new_contract_surface_drift(self) -> None:
        mutations = {
            "top-level section": lambda report: report.pop("slitherite_blocks"),
            "block registry": lambda report: report["slitherite_blocks"].__setitem__(
                "block_registry_id", "minecraft:item"
            ),
            "block inventory": lambda report: report["slitherite_blocks"][
                "block_ids"
            ].pop(),
            "native placement": lambda report: report["slitherite_blocks"][
                "native_placement"
            ].__setitem__("exact", False),
            "saved fixture": lambda report: report["slitherite_blocks"][
                "placement"
            ]["saved"].__setitem__("exact", False),
            "button timing": lambda report: report["slitherite_blocks"][
                "behavior"
            ].__setitem__("button_elapsed_ticks", 19),
            "owned recipe": lambda report: report["slitherite_blocks"][
                "loaded_data"
            ]["recipes"].pop(contract_v20.SLITHERITE_RECIPE_IDS[0]),
            "related recipe": lambda report: report["slitherite_blocks"][
                "loaded_data"
            ]["related_recipes"].pop(contract_v20.SLITHERITE_RELATED_RECIPE_IDS[-1]),
            "reload projection": lambda report: report["reload"].__setitem__(
                "slitherite_block_placement_stable", False
            ),
            "assertion": lambda report: report["assertions"][
                contract_v20.EXPECTED_ASSERTION_NAMES.index(
                    "slitherite_loaded_data_contract_exact"
                )
            ].__setitem__("passed", False),
        }

        for label, mutate in mutations.items():
            with self.subTest(label=label):
                report = valid_report()
                mutate(report)
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_probe_report(report, self.configuration)

    def test_slitherite_raw_ids_must_be_nonnegative_and_globally_unique(self) -> None:
        mutations = {
            "negative": lambda report: report["slitherite_blocks"]["entries"][
                contract_v20.SLITHERITE_BLOCK_IDS[0]
            ]["state_raw_ids"].__setitem__(0, -1),
            "cross-block duplicate": lambda report: report["slitherite_blocks"][
                "entries"
            ][contract_v20.SLITHERITE_BLOCK_IDS[1]]["state_raw_ids"].__setitem__(
                0,
                report["slitherite_blocks"]["entries"][
                    contract_v20.SLITHERITE_BLOCK_IDS[0]
                ]["state_raw_ids"][0],
            ),
            "shuffled": lambda report: report["slitherite_blocks"]["entries"][
                contract_v20.SLITHERITE_BLOCK_IDS[1]
            ].__setitem__(
                "state_raw_ids",
                list(
                    reversed(
                        report["slitherite_blocks"]["entries"][
                            contract_v20.SLITHERITE_BLOCK_IDS[1]
                        ]["state_raw_ids"]
                    )
                ),
            ),
        }

        for label, mutate in mutations.items():
            with self.subTest(label=label):
                report = valid_report()
                mutate(report)
                with self.assertRaisesRegex(forge_server.E2EError, "raw IDs"):
                    forge_server.validate_probe_report(report, self.configuration)

    def test_all_five_related_recipes_are_atomic_and_use_real_identifiers(self) -> None:
        self.assertEqual(
            (
                "etherology:comparator",
                "etherology:repeater",
                "etherology:stonecutter",
                "etherology:pedestal",
                "etherology:unadjusted_lens",
            ),
            contract_v20.SLITHERITE_RELATED_RECIPE_IDS,
        )
        self.assertEqual(5, len(contract_v20.SLITHERITE_RELATED_RECIPES))
        self.assertEqual(29, len(contract_v20.SLITHERITE_RECIPES))
        self.assertEqual(29, len(contract_v20.SLITHERITE_ADVANCEMENT_IDS))

        for recipe_id in contract_v20.SLITHERITE_RELATED_RECIPE_IDS:
            with self.subTest(recipe_id=recipe_id):
                report = valid_report()
                report["slitherite_blocks"]["loaded_data"][
                    "related_recipes"
                ].pop(recipe_id)
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_probe_report(report, self.configuration)

    def test_schema_11_projection_remains_valid_under_v19(self) -> None:
        projected = contract_v20._v19_baseline(valid_report())

        contract_v19.validate_probe_report(
            projected,
            self.configuration.manifest["required_mod_ids"],
            self.configuration.manifest["forbidden_mod_ids"],
        )
        self.assertEqual(contract_v19.REPORT_SCHEMA, projected["schema"])
        self.assertNotIn("slitherite_blocks", projected)
        self.assertEqual(310, len(projected["assertions"]))

    def test_v19_validator_receives_the_exact_schema_11_projection(self) -> None:
        report = valid_report()
        projected = contract_v20._v19_baseline(report)
        required_mod_ids = self.configuration.manifest["required_mod_ids"]
        forbidden_mod_ids = self.configuration.manifest["forbidden_mod_ids"]

        with mock.patch.object(contract_v19, "validate_probe_report") as validator:
            contract_v20.validate_probe_report(
                report,
                required_mod_ids,
                forbidden_mod_ids,
            )

        validator.assert_called_once_with(
            projected,
            required_mod_ids,
            forbidden_mod_ids,
        )

    def test_shared_slitherite_contract_bytes_are_pinned(self) -> None:
        contract_path = contract_v20.SLITHERITE_CONTRACT_PATH

        self.assertEqual(
            "scripts/e2e/slitherite_client_evidence_contract_v1.py",
            contract_v20.SLITHERITE_CONTRACT_RELATIVE_PATH,
        )
        self.assertEqual(54314, contract_v20.SLITHERITE_CONTRACT_SIZE)
        self.assertEqual(
            "4437912482c6276927758f43b0872c01421a482429225bed3f2dc2e838624773",
            contract_v20.SLITHERITE_CONTRACT_SHA256,
        )
        self.assertTrue(contract_path.is_file())
        self.assertFalse(contract_path.is_symlink())
        self.assertEqual(
            contract_v20.SLITHERITE_CONTRACT_SIZE,
            contract_path.stat().st_size,
        )
        self.assertEqual(
            contract_v20.SLITHERITE_CONTRACT_SHA256,
            contract_v20._sha256_file(contract_path),
        )

    def test_native_actions_are_postponed_before_state_or_process_creation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve() / ".state"
            process = mock.Mock()
            actions = (
                forge_server.provision_profile,
                forge_server.verify_environment,
                forge_server.execute_probe,
            )
            with mock.patch.object(forge_server.subprocess, "Popen", process):
                for action in actions:
                    with (
                        self.subTest(action=action.__name__),
                        self.assertRaisesRegex(
                            forge_server.E2EError,
                            "postponed until all five related recipes",
                        ),
                    ):
                        action(self.configuration, state_root)

            self.assertFalse(state_root.exists())
            process.assert_not_called()

    def test_gradle_native_run_postponement_guard_is_required(self) -> None:
        with temporary_repository() as (root, manifest_path):
            build_path = root / "forge/build.gradle.kts"
            content = build_path.read_text(encoding="utf-8")
            build_path.write_text(
                content.replace(
                    "blockPostponedSlitheriteV20NativeRun",
                    "removedPostponementGuard",
                ),
                encoding="utf-8",
            )
            configuration = load_temporary_configuration(root, manifest_path)

            with self.assertRaisesRegex(forge_server.E2EError, "incomplete"):
                forge_server.verify_gradle_probe_definition(configuration)


if __name__ == "__main__":
    unittest.main()
