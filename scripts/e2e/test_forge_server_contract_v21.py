from __future__ import annotations

import json
from pathlib import Path
import sys
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_server
import forge_server_contract_v19 as contract_v19
import forge_server_contract_v21 as contract_v21
from test_forge_server import (
    load_temporary_configuration,
    temporary_repository,
    valid_report,
)


class ForgeServerContractV21Tests(unittest.TestCase):
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
            for identifier in contract_v21.SLITHERITE_BLOCK_IDS
            for raw_id in entries[identifier]["state_raw_ids"]
        ]
        insertion_index = (
            contract_v21.EXPECTED_ASSERTION_NAMES.index(
                "attrahite_block_placement_stable_after_reload"
            )
            + 1
        )

        self.assertEqual(17, len(contract_v21.SLITHERITE_BLOCK_IDS))
        self.assertEqual(
            list(contract_v21.SLITHERITE_BLOCK_IDS),
            slitherite["block_ids"],
        )
        self.assertEqual(contract_v21.SLITHERITE_AGGREGATE_STATE_COUNT, len(raw_ids))
        self.assertEqual(len(raw_ids), len(set(raw_ids)))
        self.assertTrue(all(type(raw_id) is int and raw_id >= 0 for raw_id in raw_ids))
        self.assertEqual(215, len(contract_v21.SLITHERITE_ASSERTION_NAMES))
        self.assertEqual(525, len(contract_v21.EXPECTED_ASSERTION_NAMES))
        self.assertEqual(
            contract_v21.SLITHERITE_ASSERTION_NAMES,
            contract_v21.EXPECTED_ASSERTION_NAMES[
                insertion_index:
                insertion_index + len(contract_v21.SLITHERITE_ASSERTION_NAMES)
            ],
        )
        self.assertEqual(
            contract_v21.SLITHERITE_ASSERTION_VALUES,
            contract_v21.EXPECTED_ASSERTION_VALUES[
                insertion_index:
                insertion_index + len(contract_v21.SLITHERITE_ASSERTION_VALUES)
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
            ]["recipes"].pop(contract_v21.SLITHERITE_RECIPE_IDS[0]),
            "related recipe": lambda report: report["slitherite_blocks"][
                "loaded_data"
            ]["related_recipes"].pop(contract_v21.SLITHERITE_RELATED_RECIPE_IDS[-1]),
            "reload projection": lambda report: report["reload"].__setitem__(
                "slitherite_block_placement_stable", False
            ),
            "assertion": lambda report: report["assertions"][
                contract_v21.EXPECTED_ASSERTION_NAMES.index(
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
                contract_v21.SLITHERITE_BLOCK_IDS[0]
            ]["state_raw_ids"].__setitem__(0, -1),
            "cross-block duplicate": lambda report: report["slitherite_blocks"][
                "entries"
            ][contract_v21.SLITHERITE_BLOCK_IDS[1]]["state_raw_ids"].__setitem__(
                0,
                report["slitherite_blocks"]["entries"][
                    contract_v21.SLITHERITE_BLOCK_IDS[0]
                ]["state_raw_ids"][0],
            ),
            "shuffled": lambda report: report["slitherite_blocks"]["entries"][
                contract_v21.SLITHERITE_BLOCK_IDS[1]
            ].__setitem__(
                "state_raw_ids",
                list(
                    reversed(
                        report["slitherite_blocks"]["entries"][
                            contract_v21.SLITHERITE_BLOCK_IDS[1]
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
            contract_v21.SLITHERITE_RELATED_RECIPE_IDS,
        )
        self.assertEqual(5, len(contract_v21.SLITHERITE_RELATED_RECIPES))
        self.assertEqual(29, len(contract_v21.SLITHERITE_RECIPES))
        self.assertEqual(29, len(contract_v21.SLITHERITE_ADVANCEMENT_IDS))

        for recipe_id in contract_v21.SLITHERITE_RELATED_RECIPE_IDS:
            with self.subTest(recipe_id=recipe_id):
                report = valid_report()
                report["slitherite_blocks"]["loaded_data"][
                    "related_recipes"
                ].pop(recipe_id)
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_probe_report(report, self.configuration)

    def test_schema_11_projection_remains_valid_under_v19(self) -> None:
        projected = contract_v21._v19_baseline(valid_report())

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
        projected = contract_v21._v19_baseline(report)
        required_mod_ids = self.configuration.manifest["required_mod_ids"]
        forbidden_mod_ids = self.configuration.manifest["forbidden_mod_ids"]

        with mock.patch.object(contract_v19, "validate_probe_report") as validator:
            contract_v21.validate_probe_report(
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
        contract_path = contract_v21.SLITHERITE_CONTRACT_PATH

        self.assertEqual(
            "scripts/e2e/slitherite_client_evidence_contract_v1.py",
            contract_v21.SLITHERITE_CONTRACT_RELATIVE_PATH,
        )
        self.assertEqual(54314, contract_v21.SLITHERITE_CONTRACT_SIZE)
        self.assertEqual(
            "4437912482c6276927758f43b0872c01421a482429225bed3f2dc2e838624773",
            contract_v21.SLITHERITE_CONTRACT_SHA256,
        )
        self.assertTrue(contract_path.is_file())
        self.assertFalse(contract_path.is_symlink())
        self.assertEqual(
            contract_v21.SLITHERITE_CONTRACT_SIZE,
            contract_path.stat().st_size,
        )
        self.assertEqual(
            contract_v21.SLITHERITE_CONTRACT_SHA256,
            contract_v21._sha256_file(contract_path),
        )

    def test_profile_manifest_and_snapshot_bytes_are_pinned(self) -> None:
        manifest_path = (
            forge_server.REPOSITORY_ROOT
            / contract_v21.PROFILE_MANIFEST_RELATIVE_PATH
        )
        snapshot_path = (
            forge_server.REPOSITORY_ROOT
            / contract_v21.PROFILE_SNAPSHOT_RELATIVE_PATH
        )

        self.assertTrue(manifest_path.is_file())
        self.assertFalse(manifest_path.is_symlink())
        self.assertTrue(snapshot_path.is_file())
        self.assertFalse(snapshot_path.is_symlink())
        self.assertEqual(manifest_path.read_bytes(), snapshot_path.read_bytes())
        self.assertEqual(
            contract_v21.PROFILE_MANIFEST_SIZE,
            manifest_path.stat().st_size,
        )
        self.assertEqual(
            contract_v21.PROFILE_MANIFEST_SHA256,
            contract_v21._sha256_file(manifest_path),
        )

    def test_launch_anchor_policy_and_input_bytes_are_pinned(self) -> None:
        manifest_path = (
            forge_server.REPOSITORY_ROOT
            / contract_v21.PROFILE_MANIFEST_RELATIVE_PATH
        )
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        expected = {
            "schema": "etherology-forge-server-launch-anchor-v1",
            "process_kind": "jdk-source-file-broker",
            "controller_source": {
                "relative_path": "scripts/e2e/forge_server_launch_anchor.py",
                "size": 63659,
                "sha256": (
                    "8cb41e0ce36d1fa91fd2472b73e54e7e9b44974e5c13c27b692f35ce404699a5"
                ),
            },
            "java_source": {
                "relative_path": (
                    "e2e-harness/launch-anchor/1.20.1/src/"
                    "ForgeServerLaunchAnchor.java"
                ),
                "size": 41275,
                "sha256": (
                    "baca2862e9df7dd6c3da1f41583cbc4b013c45423ec77914883961dcfd202d2b"
                ),
            },
            "java_feature": 21,
            "jvm_arguments": [
                "-Xms16m",
                "-Xmx64m",
                "-XX:MaxDirectMemorySize=64m",
                "-XX:MaxMetaspaceSize=128m",
                "-XX:ReservedCodeCacheSize=64m",
                "-XX:ActiveProcessorCount=2",
            ],
            "gradle_wrapper_child": {
                "jar": {
                    "relative_path": "gradle/wrapper/gradle-wrapper.jar",
                    "size": 59821,
                    "sha256": (
                        "575098db54a998ff1c6770b352c3b16766c09848bee7555dab09afc34e8cf590"
                    ),
                },
                "properties": {
                    "relative_path": "gradle/wrapper/gradle-wrapper.properties",
                    "size": 339,
                    "sha256": (
                        "ef9f8775fd21a165a249ded98afc533818d3f6ac050f0f2f437d5285576b2257"
                    ),
                },
                "jvm_arguments": ["-Xmx2G", "-Xms64m"],
                "application_argument": "-Dorg.gradle.appname=gradlew",
                "main_class": "org.gradle.wrapper.GradleWrapperMain",
                "required_argument_prefix": [
                    "--no-daemon",
                    "--no-parallel",
                    "--max-workers=2",
                    "--console=plain",
                    "--offline",
                ],
            },
            "artifact_file_names": [
                ".forge-server-launch-anchor-ready.json",
                ".forge-server-launch-anchor-start.json",
                "forge-server-launch-anchor-child-started.json",
                "forge-server-launch-anchor-child-result.json",
                ".forge-server-launch-anchor-finish.json",
            ],
            "owns_process_group_and_session": True,
            "watchdog_ready_before_child_release": True,
            "controller_parent_required_while_awaiting_start": True,
            "pre_start_timeout_seconds": 30,
            "pre_start_failure_exits": True,
            "post_start_failure_retains_process_group": True,
            "retained_until_terminal_launch_quiescence": True,
        }

        self.assertEqual(expected, contract_v21.LAUNCH_ANCHOR_POLICY)
        self.assertEqual(expected, manifest["launch"]["launch_anchor"])
        for source_field in ("controller_source", "java_source"):
            with self.subTest(source_field=source_field):
                source = expected[source_field]
                source_path = (
                    forge_server.REPOSITORY_ROOT / source["relative_path"]
                )
                self.assertTrue(source_path.is_file())
                self.assertFalse(source_path.is_symlink())
                self.assertEqual(source["size"], source_path.stat().st_size)
                self.assertEqual(
                    source["sha256"],
                    contract_v21._sha256_file(source_path),
                )

        jar = expected["gradle_wrapper_child"]["jar"]
        jar_path = forge_server.REPOSITORY_ROOT / jar["relative_path"]
        self.assertTrue(jar_path.is_file())
        self.assertFalse(jar_path.is_symlink())
        self.assertEqual(jar["size"], jar_path.stat().st_size)
        self.assertEqual(jar["sha256"], contract_v21._sha256_file(jar_path))
        properties = expected["gradle_wrapper_child"]["properties"]
        properties_path = (
            forge_server.REPOSITORY_ROOT / properties["relative_path"]
        )
        self.assertTrue(properties_path.is_file())
        self.assertFalse(properties_path.is_symlink())
        self.assertEqual(properties["size"], properties_path.stat().st_size)
        self.assertEqual(
            properties["sha256"],
            contract_v21._sha256_file(properties_path),
        )

    def test_pre_acknowledgement_policy_is_exact(self) -> None:
        manifest_path = (
            forge_server.REPOSITORY_ROOT
            / contract_v21.PROFILE_MANIFEST_RELATIVE_PATH
        )
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        expected = {
            "maximum_untracked_java_process_count": 0,
            "child_maximum_heap_bytes": 2147483648,
            "child_acknowledgement_timeout_seconds": 15,
            "controller_bind_timeout_seconds": 2,
            "watchdog_poll_interval_milliseconds": 250,
        }

        self.assertEqual(expected, contract_v21.PRE_ACKNOWLEDGEMENT_POLICY)
        self.assertEqual(expected, manifest["launch"]["pre_acknowledgement"])

    def test_persistent_watchdog_policy_and_source_bytes_are_pinned(self) -> None:
        manifest_path = (
            forge_server.REPOSITORY_ROOT
            / contract_v21.PROFILE_MANIFEST_RELATIVE_PATH
        )
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        source = contract_v21.PERSISTENT_WATCHDOG_POLICY["source"]
        source_path = forge_server.REPOSITORY_ROOT / source["relative_path"]

        self.assertEqual(
            {
                "schema": "etherology-forge-server-launch-watchdog-v1",
                "source": {
                    "relative_path": (
                        "scripts/e2e/forge_server_launch_watchdog.py"
                    ),
                    "size": 102143,
                    "sha256": (
                        "a0d69fd1a3477fe13e7379d9088273c331e461ece2c6a8e6ebb868309e9c02e5"
                    ),
                },
                "readiness_file": ".forge-server-launch-watchdog-ready.json",
                "telemetry_file": "forge-server-launch-watchdog-telemetry.json",
                "heartbeat_timeout_seconds": 10,
                "maximum_java_process_count": 3,
                "per_process_current_phys_footprint_bytes": 5368709120,
                "aggregate_current_phys_footprint_bytes": 6442450944,
                "terminal_global_java_absence_required": True,
            },
            contract_v21.PERSISTENT_WATCHDOG_POLICY,
        )
        self.assertEqual(
            contract_v21.PERSISTENT_WATCHDOG_POLICY,
            manifest["launch"]["persistent_watchdog"],
        )
        self.assertTrue(source_path.is_file())
        self.assertFalse(source_path.is_symlink())
        self.assertEqual(source["size"], source_path.stat().st_size)
        self.assertEqual(source["sha256"], contract_v21._sha256_file(source_path))

    def test_native_actions_are_enabled_by_the_v21_contract(self) -> None:
        self.assertFalse(contract_v21.NATIVE_RUN_POSTPONED)
        self.assertEqual("", contract_v21.NATIVE_RUN_POSTPONED_REASON)
        self.assertFalse(forge_server.NATIVE_RUN_POSTPONED)
        self.assertEqual("", forge_server.NATIVE_RUN_POSTPONED_REASON)
        self.assertIsNone(forge_server.require_native_run_ready())

    def test_gradle_native_run_dependencies_are_required(self) -> None:
        dependencies = (
            "validateForgeAlchemyRecipeFoundationStaticMilestone",
            "validateForgeSlitheriteBlockRegistryClientEvidenceArchiveIntegrity",
        )
        for dependency in dependencies:
            with self.subTest(dependency=dependency), temporary_repository() as (
                root,
                manifest_path,
            ):
                build_path = root / "forge/build.gradle.kts"
                content = build_path.read_text(encoding="utf-8")
                build_path.write_text(
                    content.replace(
                        f"                {dependency},\n",
                        f"                removed{dependency},\n",
                    ),
                    encoding="utf-8",
                )
                configuration = load_temporary_configuration(root, manifest_path)

                with self.assertRaisesRegex(forge_server.E2EError, "incomplete"):
                    forge_server.verify_gradle_probe_definition(configuration)

    def test_warp_counter_must_depend_on_the_static_slitherite_gate(self) -> None:
        with temporary_repository() as (root, manifest_path):
            build_path = root / "forge/build.gradle.kts"
            content = build_path.read_text(encoding="utf-8")
            build_path.write_text(
                content.replace(
                    "            validateForgeSlitheriteStaticMilestone,\n"
                    "            validateForgeAcceptedDataSet,\n",
                    "            validateForgeSlitheriteMilestone,\n"
                    "            validateForgeAcceptedDataSet,\n",
                ),
                encoding="utf-8",
            )
            configuration = load_temporary_configuration(root, manifest_path)

            with self.assertRaisesRegex(forge_server.E2EError, "stale"):
                forge_server.verify_gradle_probe_definition(configuration)

    def test_gradle_native_run_postponement_guard_is_rejected(self) -> None:
        stale_fragments = (
            "blockPostponedSlitheriteV21NativeRun",
            "forgeServerNativeRunPostponedReason",
        )
        for stale_fragment in stale_fragments:
            with self.subTest(stale_fragment=stale_fragment), temporary_repository() as (
                root,
                manifest_path,
            ):
                build_path = root / "forge/build.gradle.kts"
                content = build_path.read_text(encoding="utf-8")
                build_path.write_text(
                    f"{content}\n// {stale_fragment}\n",
                    encoding="utf-8",
                )
                configuration = load_temporary_configuration(root, manifest_path)

                with self.assertRaisesRegex(forge_server.E2EError, "stale"):
                    forge_server.verify_gradle_probe_definition(configuration)


if __name__ == "__main__":
    unittest.main()
