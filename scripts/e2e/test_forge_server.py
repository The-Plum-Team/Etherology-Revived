from __future__ import annotations

from contextlib import contextmanager
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_server


@contextmanager
def temporary_repository():
    with tempfile.TemporaryDirectory() as temporary_directory:
        root = Path(temporary_directory).resolve()
        for relative_path in (
            "release/release-matrix.json",
            "gradle.properties",
            "forge/build.gradle.kts",
            forge_server.PROBE_SOURCE_RELATIVE_PATH.as_posix(),
            forge_server.MEMORY_HANDOFF_SOURCE_RELATIVE_PATH.as_posix(),
            "scripts/e2e/forge-server-1.20.1-profile.json",
            "gradlew",
        ):
            source = forge_server.REPOSITORY_ROOT / relative_path
            target = root / relative_path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read_bytes())
        (root / "gradlew").chmod(0o700)
        manifest_path = root / forge_server.PROFILE_MANIFEST_RELATIVE_PATH
        yield root, manifest_path


def load_temporary_configuration(
    root: Path, manifest_path: Path
) -> forge_server.ResolvedConfiguration:
    return forge_server.load_configuration(manifest_path, root)


def valid_report() -> dict[str, object]:
    assertions = [
        {
            "name": name,
            "passed": True,
            "expected": value,
            "actual": value,
        }
        for name, value in zip(
            forge_server.EXPECTED_ASSERTION_NAMES,
            forge_server.EXPECTED_ASSERTION_VALUES,
            strict=True,
        )
    ]
    loaded_mod_ids = [
        "architectury",
        "etherology",
        "etherology_e2e_server_probe",
        "forge",
        "geckolib",
        "generated_1234567",
        "minecraft",
    ]
    enabled_data_pack_names = sorted(
        [
            "vanilla" if mod_id == "minecraft" else f"mod:{mod_id}"
            for mod_id in loaded_mod_ids
        ]
        + [forge_server.RELOAD_PACK_ENABLED_NAME]
    )
    return {
        "schema": forge_server.REPORT_SCHEMA,
        "profile_id": forge_server.PROFILE_ID,
        "scenario": forge_server.SCENARIO_ID,
        "status": "passed",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "distribution": "DEDICATED_SERVER",
        "runtime_kind": "loom-userdev",
        "loaded_mod_ids": loaded_mod_ids,
        "forbidden_mod_ids_loaded": [],
        "mods": {
            **{
                mod_id: {"loaded": True}
                for mod_id in forge_server.REQUIRED_MOD_IDS
            },
            **{
                mod_id: {"loaded": False}
                for mod_id in forge_server.FORBIDDEN_MOD_IDS
            },
        },
        "registry": {
            "registry_id": "minecraft:game_event",
            "event_id": "etherology:etherology_resonance",
            "internal_id": "etherology_resonance",
            "range": 16,
            "etherology_event_ids": ["etherology:etherology_resonance"],
            "same_instance_at_server_started": True,
            "stable_after_reload": True,
        },
        "enchantments": {
            "registry_id": forge_server.ENCHANTMENT_REGISTRY_ID,
            "non_treasure_tag_id": forge_server.NON_TREASURE_TAG_ID,
            "etherology_enchantment_ids": list(forge_server.ENCHANTMENT_IDS),
            "peal": copy.deepcopy(forge_server.ENCHANTMENTS["peal"]),
            "reflection": copy.deepcopy(
                forge_server.ENCHANTMENTS["reflection"]
            ),
            "non_treasure_etherology_enchantment_ids": list(
                forge_server.ENCHANTMENT_IDS
            ),
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "properties_stable_after_reload": True,
            "tag_stable_after_reload": True,
        },
        "particles": {
            "registry_id": forge_server.PARTICLE_REGISTRY_ID,
            "capture_error": "",
            "etherology_particle_ids": list(forge_server.PARTICLE_IDS),
            "payload_families": list(
                forge_server.PARTICLE_PAYLOAD_FAMILIES
            ),
            "entries": copy.deepcopy(forge_server.PARTICLES),
            "seal_types": {
                "order": list(forge_server.SEAL_TYPE_ORDER),
                "codec_round_trips_exact": True,
                "entries": copy.deepcopy(forge_server.SEAL_TYPES),
            },
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "type_contract_stable_after_reload": True,
            "wire_contract_stable_after_reload": True,
        },
        "material_items": {
            "registry_id": forge_server.MATERIAL_ITEM_REGISTRY_ID,
            "capture_error": "",
            "material_item_ids": list(forge_server.MATERIAL_ITEM_IDS),
            "vanilla_item_class": forge_server.MATERIAL_ITEM_CLASS,
            "max_counts": forge_server.MATERIAL_ITEM_CANONICAL_MAX_COUNTS,
            "save_representations": (
                forge_server.MATERIAL_ITEM_CANONICAL_SAVE_REPRESENTATIONS
            ),
            "entries": copy.deepcopy(forge_server.MATERIAL_ITEMS),
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "properties_stable_after_reload": True,
            "stack_nbt_stable_after_reload": True,
        },
        "metal_blocks": {
            "block_registry_id": forge_server.METAL_BLOCK_REGISTRY_ID,
            "item_registry_id": forge_server.METAL_BLOCK_ITEM_REGISTRY_ID,
            "capture_error": "",
            "metal_block_ids": list(forge_server.METAL_BLOCK_IDS),
            "metal_block_item_ids": list(forge_server.METAL_BLOCK_IDS),
            "vanilla_block_class": forge_server.METAL_BLOCK_CLASS,
            "block_item_class": forge_server.BLOCK_ITEM_CLASS,
            "properties": forge_server.METAL_BLOCK_CANONICAL_PROPERTIES,
            "save_representations": (
                forge_server.METAL_BLOCK_CANONICAL_SAVE_REPRESENTATIONS
            ),
            "entries": copy.deepcopy(forge_server.METAL_BLOCKS),
            "placement": {
                "capture_error": "",
                "positions": copy.deepcopy(
                    forge_server.METAL_BLOCK_PLACEMENT_POSITIONS
                ),
                "placed_block_ids": {
                    identifier: identifier
                    for identifier in forge_server.METAL_BLOCK_IDS
                },
                "exact": True,
                "stable_after_reload": True,
            },
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "properties_stable_after_reload": True,
            "tags_stable_after_reload": True,
            "stack_nbt_stable_after_reload": True,
        },
        "attrahite_blocks": copy.deepcopy(forge_server.ATTRAHITE_BLOCKS),
        "slitherite_blocks": forge_server.build_slitherite_blocks(),
        "food_items": {
            "registry_id": forge_server.FOOD_ITEM_REGISTRY_ID,
            "capture_error": "",
            "food_item_ids": list(forge_server.FOOD_ITEM_IDS),
            "vanilla_item_class": forge_server.FOOD_ITEM_CLASS,
            "properties": forge_server.FOOD_ITEM_PROPERTIES,
            "save_representations": (
                forge_server.FOOD_ITEM_SAVE_REPRESENTATIONS
            ),
            "entries": copy.deepcopy(forge_server.FOOD_ITEMS),
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "properties_stable_after_reload": True,
            "stack_nbt_stable_after_reload": True,
        },
        "food_consumption": {
            "server_started": copy.deepcopy(
                forge_server.SERVER_STARTED_FOOD_CONSUMPTION
            ),
            "reloaded": copy.deepcopy(
                forge_server.RELOADED_FOOD_CONSUMPTION
            ),
            "fresh_player_after_reload": True,
            "stable_after_reload": True,
        },
        "forest_lantern": copy.deepcopy(forge_server.FOREST_LANTERN),
        "loot_condition": {
            "registry_id": "minecraft:loot_condition_type",
            "condition_id": "etherology:random_chance_with_fortune",
            "etherology_condition_ids": ["etherology:random_chance_with_fortune"],
            "serializer_class": (
                "ru.feytox.etherology.util.misc."
                "RandomChanceWithFortuneConditionSerializer"
            ),
            "probe_table_id": "etherology_e2e_server_probe:registry_foundation",
            "empty_tool_items": ["minecraft:gold_ingot", "minecraft:stone"],
            "fortune_one_items": [
                "minecraft:diamond",
                "minecraft:gold_ingot",
                "minecraft:stone",
            ],
            "same_state_at_server_started": True,
            "registry_and_behavior_stable_after_reload": True,
            "probe_table_instance_replaced_after_reload": True,
        },
        "ether_sources": {
            "listener_class": forge_server.ETHER_SOURCE_LISTENER_CLASS,
            "resource_directory": "ether_sources",
            "initial": {
                "capture_error": "",
                "entries": copy.deepcopy(forge_server.INITIAL_ETHER_SOURCE_ENTRIES),
            },
            "server_started": {
                "capture_error": "",
                "entries": copy.deepcopy(forge_server.INITIAL_ETHER_SOURCE_ENTRIES),
            },
            "reloaded": {
                "capture_error": "",
                "entries": copy.deepcopy(forge_server.RELOADED_ETHER_SOURCE_ENTRIES),
            },
            "same_at_server_started": True,
            "changed_after_reload": True,
        },
        "reload": {
            "pack_directory": forge_server.RELOAD_PACK_DIRECTORY,
            "pack_resources": list(forge_server.RELOAD_PACK_RESOURCES),
            "enabled_pack_name": forge_server.RELOAD_PACK_ENABLED_NAME,
            "enabled_data_pack_names": enabled_data_pack_names,
            "enabled_data_packs_exact": True,
            "command": "reload",
            "command_result": 0,
            "failure": "",
            "completed": True,
            "update_cause": "SERVER_DATA_LOAD",
            "should_update_static_data": True,
            "registry_stable": True,
            "tags_stable": True,
            "loot_condition_registry_and_behavior_stable": True,
            "loot_table_instance_replaced": True,
            "enchantment_registry_stable": True,
            "enchantment_properties_stable": True,
            "enchantment_tag_stable": True,
            "particle_registry_stable": True,
            "particle_type_contract_stable": True,
            "particle_wire_contract_stable": True,
            "material_item_registry_stable": True,
            "material_item_properties_stable": True,
            "material_item_stack_nbt_stable": True,
            "metal_block_registry_stable": True,
            "metal_block_properties_stable": True,
            "metal_block_tags_stable": True,
            "metal_block_stack_nbt_stable": True,
            "metal_block_placement_stable": True,
            "attrahite_block_registry_stable": True,
            "attrahite_block_properties_stable": True,
            "attrahite_block_tags_stable": True,
            "attrahite_block_stack_nbt_stable": True,
            "attrahite_block_loaded_data_stable": True,
            "attrahite_block_loaded_data_fresh": True,
            "attrahite_block_placement_stable": True,
            "slitherite_block_registry_stable": True,
            "slitherite_block_default_states_stable": True,
            "slitherite_block_tags_stable": True,
            "slitherite_block_loaded_data_stable": True,
            "slitherite_block_loaded_data_fresh": True,
            "slitherite_block_placement_stable": True,
            "food_item_registry_stable": True,
            "food_item_properties_stable": True,
            "food_item_stack_nbt_stable": True,
            "food_consumption_stable": True,
            "forest_lantern_registry_stable": True,
            "forest_lantern_states_stable": True,
            "forest_lantern_tags_stable": True,
            "forest_lantern_loaded_data_stable": True,
            "forest_lantern_loaded_data_fresh": True,
            "forest_lantern_mechanics_stable": True,
            "stop_requested_after_completion": True,
        },
        "tags": {
            "update_cause": "SERVER_DATA_LOAD",
            "should_update_static_data": True,
            "update_count": 2,
            "reload_update_cause": "SERVER_DATA_LOAD",
            "reload_should_update_static_data": True,
            "vibrations": {
                "id": "minecraft:vibrations",
                "contains_event": True,
                "etherology_event_ids": ["etherology:etherology_resonance"],
            },
            "warden_can_listen": {
                "id": "minecraft:warden_can_listen",
                "contains_event": True,
                "etherology_event_ids": ["etherology:etherology_resonance"],
            },
            "etherology_tag_ids": [
                "minecraft:vibrations",
                "minecraft:warden_can_listen",
            ],
            "same_membership_at_server_started": True,
            "stable_after_reload": True,
        },
        "lifecycle": list(forge_server.EXPECTED_LIFECYCLE),
        "assertions": assertions,
    }


def valid_server_log() -> bytes:
    lines = [
        "[Server thread/INFO] [EtherologyServerProbe] tags_updated_initial",
        "[Server thread/INFO] [EtherologyServerProbe] registry_foundation_checked",
        "[Server thread/INFO] Done (1.234s)! For help, type help",
        "[Server thread/INFO] [EtherologyServerProbe] server_started",
        "[Server thread/INFO] [EtherologyServerProbe] reload_requested",
        "[Server thread/INFO] [EtherologyServerProbe] tags_updated_reload",
        "[Server thread/INFO] [EtherologyServerProbe] reload_command_returned",
        "[Server thread/INFO] [EtherologyServerProbe] stop_requested",
        "[Server thread/INFO] [EtherologyServerProbe] server_stopping",
        "[Server thread/INFO] Stopping server",
        "[Server thread/INFO] Saving worlds",
        "[Server thread/INFO] All dimensions are saved",
        "[Server thread/INFO] [EtherologyServerProbe] server_stopped",
        "[Server thread/INFO] [EtherologyServerProbe] report_published",
        "[Server thread/INFO] [EtherologyServerProbe] "
        "loom_userdev_exit_scheduled status=0 server_thread_join_timeout_ms=30000",
    ]
    return ("\n".join(lines) + "\n").encode("utf-8")


def failed_v18_report() -> dict[str, object]:
    report = forge_server.contract_v20._v19_baseline(valid_report())
    report["profile_id"] = forge_server.HISTORICAL_V18_PROFILE_ID
    report["status"] = "failed"
    failures = {
        str(assertion["name"]): assertion
        for assertion in forge_server.HISTORICAL_V18_FAILURES
    }
    for assertion in report["assertions"]:
        replacement = failures.get(str(assertion["name"]))
        if replacement is not None:
            assertion.update(copy.deepcopy(replacement))
    return report


class ConfigurationTests(unittest.TestCase):
    def test_profile_resolves_exact_forge_server_lane(self) -> None:
        configuration = forge_server.load_configuration()

        self.assertEqual(
            "etherology-e2e-forge-server-1.20.1-v20",
            forge_server.PROFILE_ID,
        )
        self.assertEqual("forge-1.20.1", configuration.artifact_lane["artifact_node"])
        self.assertEqual("1.20.1", configuration.runtime_lane["runtime_version"])
        self.assertEqual("1.20.1-47.4.9", configuration.runtime_lane["loader_version"])
        self.assertEqual(0, configuration.runtime_lane["port"])
        self.assertEqual(17, configuration.runtime_lane["java"])
        self.assertEqual(
            forge_server.PROFILE_ID,
            forge_server.profile_spec(configuration)["runtime_directory"],
        )

    def test_profile_has_one_exact_named_probe_and_scenario(self) -> None:
        configuration = forge_server.load_configuration()
        launch = forge_server.require_object(configuration.manifest, "launch")

        self.assertEqual("loom-userdev", launch["kind"])
        self.assertEqual(forge_server.TASK_PATH, launch["task_path"])
        self.assertEqual(forge_server.SCENARIO_ID, launch["scenario"])
        self.assertEqual(2048, launch["maximum_memory_mb"])
        self.assertNotIn("quickskin", configuration.manifest["required_mod_ids"])
        self.assertIn(
            "etherology_e2e_harness", configuration.manifest["forbidden_mod_ids"]
        )

    def test_active_profile_matches_v20_snapshot_and_preserves_prior_versions(self) -> None:
        active = forge_server.MANIFEST_PATH.read_bytes()
        v20_snapshot_path = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v20.json"
        )
        v20_snapshot = v20_snapshot_path.read_bytes()
        v19_snapshot_path = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v19.json"
        )
        v19_snapshot = v19_snapshot_path.read_bytes()
        v18_snapshot_path = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v18.json"
        )
        v18_snapshot = v18_snapshot_path.read_bytes()
        v17_snapshot_path = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v17.json"
        )
        v17_snapshot = v17_snapshot_path.read_bytes()
        v16_snapshot = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v16.json"
        ).read_bytes()
        v15_snapshot_path = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v15.json"
        )
        v15_snapshot = v15_snapshot_path.read_bytes()
        v14_snapshot = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v14.json"
        ).read_bytes()
        v13_snapshot = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v13.json"
        ).read_bytes()
        v12_snapshot_path = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v12.json"
        )
        v12_snapshot = v12_snapshot_path.read_bytes()

        self.assertEqual(v20_snapshot, active)
        self.assertNotEqual(v19_snapshot, active)
        self.assertNotEqual(v18_snapshot, active)
        self.assertNotEqual(v17_snapshot, active)
        self.assertNotEqual(v16_snapshot, active)
        self.assertNotEqual(v15_snapshot, active)
        self.assertNotEqual(v14_snapshot, active)
        self.assertNotEqual(v13_snapshot, active)
        self.assertNotEqual(v12_snapshot, active)
        self.assertEqual(
            forge_server.contract_v20.PROFILE_MANIFEST_SIZE,
            len(v20_snapshot),
        )
        self.assertEqual(
            forge_server.contract_v20.PROFILE_MANIFEST_SHA256,
            forge_server.sha256_file(v20_snapshot_path),
        )
        self.assertEqual(
            forge_server.contract_v19.PROFILE_MANIFEST_SIZE,
            len(v19_snapshot),
        )
        self.assertEqual(
            forge_server.contract_v19.PROFILE_MANIFEST_SHA256,
            forge_server.sha256_file(v19_snapshot_path),
        )
        self.assertEqual(
            "etherology-e2e-forge-server-1.20.1-v18",
            json.loads(v18_snapshot)["profile"]["id"],
        )
        self.assertEqual(1204, len(v18_snapshot))
        self.assertEqual(
            "918a0af4b8794e07d0282e1913341abbad0908ba524714913267972c54481687",
            forge_server.sha256_file(v18_snapshot_path),
        )
        self.assertEqual(
            "etherology-e2e-forge-server-1.20.1-v17",
            json.loads(v17_snapshot)["profile"]["id"],
        )
        self.assertEqual(1204, len(v17_snapshot))
        self.assertEqual(
            "58eef8f07f1457d5a806a53fe0f864019902e1e76eb9d5d0b60ea817388d0042",
            forge_server.sha256_file(v17_snapshot_path),
        )
        self.assertEqual(
            "etherology-e2e-forge-server-1.20.1-v16",
            json.loads(v16_snapshot)["profile"]["id"],
        )
        self.assertEqual(
            "etherology-e2e-forge-server-1.20.1-v15",
            json.loads(v15_snapshot)["profile"]["id"],
        )
        self.assertEqual(
            "b0ddfd9ac8ac9073d055a492bd71250995b42c69a6c54a30eef0f379319cf58c",
            forge_server.sha256_file(v15_snapshot_path),
        )
        self.assertEqual(
            "etherology-e2e-forge-server-1.20.1-v14",
            json.loads(v14_snapshot)["profile"]["id"],
        )
        self.assertEqual(
            "etherology-e2e-forge-server-1.20.1-v13",
            json.loads(v13_snapshot)["profile"]["id"],
        )
        self.assertEqual(
            "etherology-e2e-forge-server-1.20.1-v12",
            json.loads(v12_snapshot)["profile"]["id"],
        )

    def test_consumed_v17_contract_files_remain_byte_exact(self) -> None:
        expected_files = {
            "scripts/e2e/forge_server_contract_v17.py": (
                22851,
                "feff4d6ca72ce32b874031b3bfc448e618c5295342d8bc156825afcd56074a78",
            ),
            "scripts/e2e/forge_server_attrahite_evidence_v17.py": (
                40317,
                "fee7cb0da615853955f7a2423c594b3f94004c67afbd4b9f5bcb992c238fd763",
            ),
            "scripts/e2e/test_forge_server_attrahite_evidence_v17.py": (
                133658,
                "41b3fcbb27927968606c0d7b409d25bdf05015c317a05c77b3997cfe4fe992ec",
            ),
        }

        for relative_path, (expected_size, expected_sha256) in expected_files.items():
            with self.subTest(relative_path=relative_path):
                path = forge_server.REPOSITORY_ROOT / relative_path
                self.assertEqual(expected_size, path.stat().st_size)
                self.assertEqual(expected_sha256, forge_server.sha256_file(path))

    def test_consumed_v18_contract_files_remain_byte_exact(self) -> None:
        expected_files = {
            "scripts/e2e/forge_server_contract_v18.py": (
                1714,
                "baccf604378d003e0d452be2988f036e5175da069d1f5fe03d42a71e635f2532",
            ),
            "scripts/e2e/forge_server_attrahite_evidence_v18.py": (
                40317,
                "83b0f025ea533d5ec2bade58a6b80b0e9dc7f1141dd80adef18d3f9853c99a03",
            ),
            "scripts/e2e/test_forge_server_attrahite_evidence_v18.py": (
                133658,
                "9460210108ec4bbd49c4d9edd3f6235d6d0db1430474c96af93ab7db7ae41dd1",
            ),
        }

        for relative_path, (expected_size, expected_sha256) in expected_files.items():
            with self.subTest(relative_path=relative_path):
                path = forge_server.REPOSITORY_ROOT / relative_path
                self.assertEqual(expected_size, path.stat().st_size)
                self.assertEqual(expected_sha256, forge_server.sha256_file(path))

    def test_consumed_v19_contract_files_remain_byte_exact(self) -> None:
        expected_files = {
            "scripts/e2e/forge_server_contract_v19.py": (
                1714,
                "4c54999e8a50b9eb56afe248f0dc694533e47abfe233b88c1b2d112994b5306c",
            ),
            "scripts/e2e/forge_server_attrahite_evidence_v19.py": (
                40317,
                "9a0402f867f2988be3751b4fbe5aac5eca7e64612544e6cc80775777c341ef60",
            ),
            "scripts/e2e/test_forge_server_attrahite_evidence_v19.py": (
                132924,
                "e4d50515cf3eb81b2976b87196710ce7b5f444918d47bd25025369e72e2d7cc1",
            ),
            "scripts/e2e/forge-server-1.20.1-profile-v19.json": (
                1204,
                "626cd5354057da6afe426d88de6849f6daef1a95a56ad3e5e4bb7afad2ceceec",
            ),
        }

        for relative_path, (expected_size, expected_sha256) in expected_files.items():
            with self.subTest(relative_path=relative_path):
                path = forge_server.REPOSITORY_ROOT / relative_path
                self.assertEqual(expected_size, path.stat().st_size)
                self.assertEqual(expected_sha256, forge_server.sha256_file(path))

    def test_profile_must_be_loaded_from_tracked_path(self) -> None:
        with temporary_repository() as (root, manifest_path):
            copied_path = root / "copied-profile.json"
            copied_path.write_bytes(manifest_path.read_bytes())

            with self.assertRaisesRegex(forge_server.E2EError, "tracked repository path"):
                forge_server.load_configuration(copied_path, root)

    def test_profile_identity_drift_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["profile"]["id"] = "another-profile"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(forge_server.E2EError, "identity"):
                load_temporary_configuration(root, manifest_path)

    def test_release_matrix_java_loader_and_port_drift_are_rejected(self) -> None:
        for field, value in (("java", 21), ("loader_version", "wrong"), ("port", 25565)):
            with self.subTest(field=field), temporary_repository() as (
                root,
                manifest_path,
            ):
                matrix_path = root / "release/release-matrix.json"
                matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
                runtime = next(
                    row
                    for row in matrix["runtimes"]
                    if row["artifact_node"] == "forge-1.20.1"
                )
                runtime[field] = value
                matrix_path.write_text(json.dumps(matrix), encoding="utf-8")

                with self.assertRaisesRegex(forge_server.E2EError, "server lane"):
                    load_temporary_configuration(root, manifest_path)

    def test_named_gradle_probe_definition_is_present(self) -> None:
        configuration = forge_server.load_configuration()

        forge_server.verify_gradle_probe_definition(configuration)

    def test_incomplete_named_gradle_probe_definition_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            build_path = root / "forge/build.gradle.kts"
            content = build_path.read_text(encoding="utf-8")
            content = content.replace("etherology.serverProbe.evidenceRoot", "removed")
            build_path.write_text(content, encoding="utf-8")
            configuration = load_temporary_configuration(root, manifest_path)

            with self.assertRaisesRegex(forge_server.E2EError, "incomplete"):
                forge_server.verify_gradle_probe_definition(configuration)

    def test_probe_source_lifecycle_drift_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            source_path = root / forge_server.PROBE_SOURCE_RELATIVE_PATH
            content = source_path.read_text(encoding="utf-8")
            source_path.write_text(
                content.replace('            "reload_command_returned",\n', ""),
                encoding="utf-8",
            )
            configuration = load_temporary_configuration(root, manifest_path)

            with self.assertRaisesRegex(forge_server.E2EError, "contracts differ"):
                forge_server.verify_probe_source_lifecycle(configuration)

    def test_server_probe_memory_handoff_source_is_pinned(self) -> None:
        configuration = forge_server.load_configuration()

        forge_server.verify_memory_handoff_source(configuration)

    def test_incomplete_server_memory_handoff_source_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            handoff_path = root / forge_server.MEMORY_HANDOFF_SOURCE_RELATIVE_PATH
            content = handoff_path.read_text(encoding="utf-8")
            handoff_path.write_text(
                content.replace("ProcessHandle.current()", "ProcessHandle.allProcesses()"),
                encoding="utf-8",
            )
            configuration = load_temporary_configuration(root, manifest_path)

            with self.assertRaisesRegex(forge_server.E2EError, "incomplete"):
                forge_server.verify_memory_handoff_source(configuration)

    def test_gradle_heap_spelling_drift_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            build_path = root / "forge/build.gradle.kts"
            content = build_path.read_text(encoding="utf-8")
            build_path.write_text(
                content.replace(
                    'jvmArguments.add("-Xmx${serverProbeLaunch.getValue('
                    '"maximum_memory_mb")}m")',
                    'jvmArguments.add("-Xmx${serverProbeLaunch.getValue('
                    '"maximum_memory_mb")}M")',
                ),
                encoding="utf-8",
            )
            configuration = load_temporary_configuration(root, manifest_path)

            with self.assertRaisesRegex(forge_server.E2EError, "incomplete"):
                forge_server.verify_gradle_probe_definition(configuration)


class ConsumedV18HistoryTests(unittest.TestCase):
    def test_exact_failed_report_retains_305_of_310_and_clean_shutdown(self) -> None:
        report = failed_v18_report()

        forge_server.validate_consumed_v18_report(report)
        forge_server.validate_consumed_v18_clean_shutdown_log(
            valid_server_log().replace(b"status=0", b"status=1")
        )

        self.assertEqual(
            305,
            sum(assertion["passed"] is True for assertion in report["assertions"]),
        )
        self.assertEqual(
            list(forge_server.HISTORICAL_V18_FAILURES),
            [
                assertion
                for assertion in report["assertions"]
                if assertion["passed"] is False
            ],
        )

    def test_failed_report_rejects_every_history_semantic_drift(self) -> None:
        mutations = {
            "status": lambda report: report.__setitem__("status", "passed"),
            "lifecycle": lambda report: report["lifecycle"].pop(),
            "pass count": lambda report: report["assertions"][0].__setitem__(
                "passed",
                False,
            ),
            "failure evidence": lambda report: next(
                assertion
                for assertion in report["assertions"]
                if assertion["name"] == "forest_lantern_jump_break_drop_exact"
            ).__setitem__("actual", "etherology:forest_lanternx1"),
        }
        for description, mutate in mutations.items():
            with self.subTest(description=description):
                report = failed_v18_report()
                mutate(report)
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_consumed_v18_report(report)

    def test_failed_server_log_rejects_success_status(self) -> None:
        with self.assertRaisesRegex(forge_server.E2EError, "token count"):
            forge_server.validate_consumed_v18_clean_shutdown_log(valid_server_log())

    def test_no_v18_history_is_allowed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            forge_server.validate_consumed_v18_history(
                Path(temporary_directory).resolve()
            )

    def test_partial_v18_history_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            attempt_path = root / forge_server.HISTORICAL_V18_ATTEMPT_RELATIVE_PATH
            attempt_path.parent.mkdir(parents=True)
            attempt_path.write_bytes(b"partial\n")

            with self.assertRaisesRegex(forge_server.E2EError, "runtime"):
                forge_server.validate_consumed_v18_history(root)

    def test_v18_post_validation_artifacts_remain_absent(self) -> None:
        forbidden_paths = (
            forge_server.HISTORICAL_V18_RUNTIME_RELATIVE_PATH
            / "evidence/attrahite-block-registry/reports/done.marker",
            forge_server.HISTORICAL_V18_RUNTIME_RELATIVE_PATH
            / "evidence/attrahite-block-registry/reports/launcher-result.json",
            forge_server.HISTORICAL_V18_RUNTIME_RELATIVE_PATH
            / "evidence/attrahite-block-registry/logs/latest.log",
            forge_server.HISTORICAL_V18_RUNTIME_RELATIVE_PATH
            / ".forge-server-gradle.recovered.log",
            forge_server.HISTORICAL_V18_ARCHIVE_RELATIVE_PATH,
        )
        for forbidden_path in forbidden_paths:
            with self.subTest(forbidden_path=forbidden_path):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory).resolve()
                    runtime = (
                        root / forge_server.HISTORICAL_V18_RUNTIME_RELATIVE_PATH
                    )
                    runtime.mkdir(parents=True)
                    target = root / forbidden_path
                    if forbidden_path == forge_server.HISTORICAL_V18_ARCHIVE_RELATIVE_PATH:
                        target.mkdir(parents=True)
                    else:
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_bytes(b"forbidden\n")

                    with self.assertRaises(forge_server.E2EError):
                        forge_server.validate_consumed_v18_history(root)


class RuntimeIsolationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.native_run_guard = mock.patch.object(
            forge_server,
            "require_native_run_ready",
        )
        self.native_run_guard.start()
        self.repository_context = temporary_repository()
        self.repository_root, self.manifest_path = self.repository_context.__enter__()
        self.configuration = load_temporary_configuration(
            self.repository_root, self.manifest_path
        )
        self.state_context = tempfile.TemporaryDirectory()
        self.state_root = Path(self.state_context.name).resolve() / ".state"

    def tearDown(self) -> None:
        self.state_context.cleanup()
        self.repository_context.__exit__(None, None, None)
        self.native_run_guard.stop()

    def test_provision_creates_only_the_new_repository_owned_runtime(self) -> None:
        self.assertIsNone(
            forge_server.provision_profile(self.configuration, self.state_root)
        )
        runtime = forge_server.runtime_root(self.configuration, self.state_root)

        self.assertEqual(
            self.state_root / "runtimes" / forge_server.PROFILE_ID,
            runtime,
        )
        self.assertEqual(
            {forge_server.PROFILE_MARKER_NAME, "game", "evidence"},
            {entry.name for entry in runtime.iterdir()},
        )
        marker = forge_server.load_json_object(
            runtime / forge_server.PROFILE_MARKER_NAME,
            "profile marker",
        )
        self.assertEqual([], marker["isolation"]["source_profiles"])

    def test_consumed_v18_attempt_isolated_from_fresh_v20_before_provision(
        self,
    ) -> None:
        self.state_root.mkdir(parents=True)
        consumed_attempt = (
            self.state_root
            / "etherology-e2e-forge-server-1.20.1-v18-run.attempted"
        )
        consumed_attempt_bytes = (
            "profile_id=etherology-e2e-forge-server-1.20.1-v18\n"
            "scenario=attrahite-block-registry\n"
            "pid=12345\n"
        ).encode("utf-8")
        consumed_attempt.write_bytes(consumed_attempt_bytes)
        fresh_attempt = forge_server.run_attempt_path(
            self.configuration,
            self.state_root,
        )
        fresh_runtime = forge_server.runtime_root(
            self.configuration,
            self.state_root,
        )
        fresh_evidence = forge_server.evidence_root(
            self.configuration,
            fresh_runtime,
        )
        fresh_archive = forge_server.sealed_archive_path(self.configuration)

        self.assertTrue(consumed_attempt.is_file())
        self.assertFalse(fresh_attempt.exists())
        self.assertFalse(fresh_runtime.exists())
        self.assertFalse(fresh_evidence.exists())
        self.assertFalse(fresh_archive.exists())

        forge_server.provision_profile(self.configuration, self.state_root)

        self.assertEqual(consumed_attempt_bytes, consumed_attempt.read_bytes())
        self.assertTrue(fresh_runtime.is_dir())
        self.assertTrue(fresh_evidence.is_dir())
        self.assertFalse(fresh_attempt.exists())
        self.assertFalse(fresh_archive.exists())

    def test_existing_exact_runtime_is_never_reused(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        marker_path = runtime / forge_server.PROFILE_MARKER_NAME
        marker_content = marker_path.read_bytes()

        with self.assertRaisesRegex(forge_server.E2EError, "Refusing to reuse"):
            forge_server.provision_profile(self.configuration, self.state_root)

        self.assertEqual(marker_content, marker_path.read_bytes())

    def test_sealed_archive_permanently_consumes_profile_without_runtime(self) -> None:
        archive = forge_server.sealed_archive_path(self.configuration)
        archive.mkdir(parents=True)

        with self.assertRaisesRegex(forge_server.E2EError, "sealed evidence.*consumed"):
            forge_server.provision_profile(self.configuration, self.state_root)

        self.assertFalse(self.state_root.exists())

    def test_recorded_launch_attempt_permanently_consumes_profile_without_runtime(
        self,
    ) -> None:
        self.state_root.mkdir(parents=True)
        forge_server.run_attempt_path(
            self.configuration,
            self.state_root,
        ).write_text("attempted\n", encoding="utf-8")

        with self.assertRaisesRegex(
            forge_server.E2EError,
            "launch attempt.*consumed",
        ):
            forge_server.provision_profile(self.configuration, self.state_root)

        self.assertFalse(
            forge_server.runtime_root(self.configuration, self.state_root).exists()
        )

    def test_sealed_archive_blocks_check_after_an_existing_runtime(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        forge_server.sealed_archive_path(self.configuration).mkdir(parents=True)

        with self.assertRaisesRegex(forge_server.E2EError, "sealed evidence.*consumed"):
            forge_server.require_unsealed_profile(self.configuration)

    def test_linked_archive_destination_is_rejected_before_provision(self) -> None:
        archive = forge_server.sealed_archive_path(self.configuration)
        archive.parent.mkdir(parents=True)
        target = self.repository_root / "foreign-archive"
        target.mkdir()
        archive.symlink_to(target, target_is_directory=True)

        with self.assertRaisesRegex(forge_server.E2EError, "symlink"):
            forge_server.provision_profile(self.configuration, self.state_root)

        self.assertFalse(self.state_root.exists())

    def test_unmarked_existing_runtime_is_never_adopted(self) -> None:
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        runtime.mkdir(parents=True)

        with self.assertRaisesRegex(forge_server.E2EError, "Refusing to reuse"):
            forge_server.provision_profile(self.configuration, self.state_root)

    def test_wrong_profile_marker_is_rejected(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        marker_path = forge_server.profile_marker_path(
            self.configuration,
            forge_server.runtime_root(self.configuration, self.state_root),
        )
        marker = json.loads(marker_path.read_text(encoding="utf-8"))
        marker["managed_by"] = "another-runner"
        marker_path.write_text(json.dumps(marker), encoding="utf-8")

        with self.assertRaisesRegex(forge_server.E2EError, "does not match"):
            forge_server.verify_runtime(self.configuration, self.state_root)

    def test_manifest_digest_drift_invalidates_an_existing_runtime(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        manifest = json.loads(self.manifest_path.read_text(encoding="utf-8"))
        self.manifest_path.write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(forge_server.E2EError, "does not match"):
            forge_server.verify_runtime(self.configuration, self.state_root)

    def test_symlinked_runtimes_root_is_rejected(self) -> None:
        self.state_root.mkdir(parents=True)
        target = self.state_root.parent / "foreign-runtimes"
        target.mkdir()
        (self.state_root / "runtimes").symlink_to(target, target_is_directory=True)

        with self.assertRaisesRegex(forge_server.E2EError, "symlink"):
            forge_server.provision_profile(self.configuration, self.state_root)

    def test_symlinked_game_directory_is_rejected(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        config = forge_server.game_directory(self.configuration, runtime) / "config"
        config.rmdir()
        config.symlink_to(runtime / "evidence", target_is_directory=True)

        with self.assertRaisesRegex(forge_server.E2EError, "linked"):
            forge_server.verify_runtime(self.configuration, self.state_root)

    def test_server_files_bind_loopback_ephemeral_port_and_eula(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        game = forge_server.game_directory(self.configuration, runtime)

        self.assertEqual("eula=true\n", (game / "eula.txt").read_text(encoding="utf-8"))
        properties = (game / "server.properties").read_text(encoding="utf-8")
        self.assertIn("server-ip=127.0.0.1\n", properties)
        self.assertIn("server-port=0\n", properties)
        self.assertIn("online-mode=false\n", properties)

    def test_evidence_layout_is_two_empty_scenario_directories(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        scenario = forge_server.evidence_root(
            self.configuration,
            forge_server.runtime_root(self.configuration, self.state_root),
        )

        self.assertEqual({"reports", "logs"}, {entry.name for entry in scenario.iterdir()})
        self.assertFalse(any((scenario / "reports").iterdir()))
        self.assertFalse(any((scenario / "logs").iterdir()))

    def test_existing_evidence_is_never_reset_or_reused(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        report = forge_server.evidence_path(self.configuration, "report", runtime)
        report.write_text("{}\n", encoding="utf-8")

        with self.assertRaisesRegex(forge_server.E2EError, "not empty"):
            forge_server.verify_runtime(self.configuration, self.state_root)
        self.assertEqual("{}\n", report.read_text(encoding="utf-8"))

    def test_injected_mod_or_generated_game_file_blocks_the_only_run(self) -> None:
        for relative_path in ("mods/client-harness.jar", "usercache.json"):
            with self.subTest(relative_path=relative_path):
                if forge_server.runtime_root(
                    self.configuration, self.state_root
                ).exists():
                    self.state_context.cleanup()
                    self.state_context = tempfile.TemporaryDirectory()
                    self.state_root = Path(self.state_context.name).resolve() / ".state"
                forge_server.provision_profile(self.configuration, self.state_root)
                runtime = forge_server.runtime_root(self.configuration, self.state_root)
                injected = forge_server.game_directory(
                    self.configuration, runtime
                ) / relative_path
                injected.parent.mkdir(parents=True, exist_ok=True)
                injected.write_bytes(b"foreign")

                with self.assertRaisesRegex(
                    forge_server.E2EError, "pristine dedicated-server"
                ):
                    forge_server.verify_runtime(self.configuration, self.state_root)


class CommandTests(unittest.TestCase):
    def test_java_version_probe_has_a_tiny_explicit_heap_cap(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            java_path = Path(temporary_directory).resolve() / "java"
            java_path.write_bytes(b"not executed")
            java_path.chmod(0o700)
            completed = subprocess.CompletedProcess(
                args=[],
                returncode=0,
                stdout="java.specification.version = 21\n",
            )
            with mock.patch.object(
                forge_server.subprocess,
                "run",
                return_value=completed,
            ) as run:
                self.assertEqual(21, forge_server.java_major_version(java_path))

            run.assert_called_once_with(
                [
                    str(java_path),
                    "-Xmx64M",
                    "-XshowSettings:properties",
                    "-version",
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=20,
                check=False,
            )

    def test_command_uses_host_jdk_and_exact_task_without_override(self) -> None:
        configuration = forge_server.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            java = root / "java"
            caffeinate = root / "caffeinate"
            java.write_bytes(b"java")
            caffeinate.write_bytes(b"caffeinate")
            java.chmod(0o700)
            caffeinate.chmod(0o700)
            with mock.patch.object(forge_server, "java_major_version", return_value=21):
                command = forge_server.build_gradle_command(
                    configuration, java, caffeinate
                )

        self.assertEqual(forge_server.TASK_PATH, command[-1])
        self.assertEqual(1, command.count(forge_server.TASK_PATH))
        self.assertEqual(str(configuration.repository_root / "gradlew"), command[0])
        self.assertIn("--no-parallel", command)
        self.assertNotIn("-dimsu", command)
        self.assertNotIn(str(caffeinate), command)
        self.assertFalse(any(argument.startswith("-P") for argument in command))
        self.assertNotIn("Quick-Skin", " ".join(command))

    def test_gradle_host_older_than_java_21_is_rejected(self) -> None:
        configuration = forge_server.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            java = root / "java"
            caffeinate = root / "caffeinate"
            java.write_bytes(b"java")
            caffeinate.write_bytes(b"caffeinate")
            java.chmod(0o700)
            caffeinate.chmod(0o700)
            with (
                mock.patch.object(forge_server, "java_major_version", return_value=17),
                self.assertRaisesRegex(forge_server.E2EError, "JDK 21"),
            ):
                forge_server.build_gradle_command(configuration, java, caffeinate)

    def test_java_option_injection_is_rejected_before_any_java_probe(self) -> None:
        configuration = forge_server.load_configuration()
        for variable_name in (
            "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "_JAVA_OPTIONS",
        ):
            with (
                self.subTest(variable_name=variable_name),
                mock.patch.dict(os.environ, {variable_name: ""}, clear=True),
                mock.patch.object(forge_server, "resolve_gradle_java") as resolve_java,
                self.assertRaisesRegex(forge_server.E2EError, variable_name),
            ):
                forge_server.verify_environment(configuration)

            resolve_java.assert_not_called()


class ProbeReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.configuration = forge_server.load_configuration()

    def test_exact_probe_report_is_accepted(self) -> None:
        forge_server.validate_probe_report(valid_report(), self.configuration)

    def test_generated_mod_suffix_and_enabled_pack_inventory_stay_bound(self) -> None:
        report = valid_report()
        generated_mod_index = report["loaded_mod_ids"].index(
            "generated_1234567"
        )
        report["loaded_mod_ids"][generated_mod_index] = "generated_a"
        generated_pack_index = report["reload"][
            "enabled_data_pack_names"
        ].index("mod:generated_1234567")
        report["reload"]["enabled_data_pack_names"][
            generated_pack_index
        ] = "mod:generated_a"
        forge_server.validate_probe_report(report, self.configuration)

        report["reload"]["enabled_data_pack_names"][
            generated_pack_index
        ] = "mod:generated_1234567"
        with self.assertRaisesRegex(
            forge_server.E2EError,
            "enabled data-pack inventory",
        ):
            forge_server.validate_probe_report(report, self.configuration)

    def test_forbidden_mod_assertions_are_complete_and_profile_ordered(self) -> None:
        expected_prefix = (
            "distribution_dedicated_server",
            "runtime_kind_loom_userdev",
            "mod_loaded:etherology",
            "mod_loaded:etherology_e2e_server_probe",
            "mod_absent:etherology_e2e_harness",
            "mod_absent:quickskin",
            "mod_absent:cpm",
            "mod_absent:ears",
            "mod_absent:modmenu",
            "mod_absent:roughlyenoughitems",
            "mod_absent:emi",
            "mods_forbidden_intersection_empty",
        )

        self.assertEqual(525, len(forge_server.EXPECTED_ASSERTION_NAMES))
        self.assertEqual(expected_prefix, forge_server.EXPECTED_ASSERTION_NAMES[:12])
        self.assertEqual(
            ("DEDICATED_SERVER", "loom-userdev", "loaded", "loaded")
            + ("absent",) * 7
            + ("none",),
            forge_server.EXPECTED_ASSERTION_VALUES[:12],
        )
        self.assertEqual(
            ">".join(forge_server.EXPECTED_LIFECYCLE),
            forge_server.EXPECTED_ASSERTION_VALUES[-1],
        )

    def test_material_item_assertions_are_exact_and_probe_ordered(self) -> None:
        expected_names = (
            *(f"registry:item:{identifier}" for identifier in forge_server.MATERIAL_ITEM_IDS),
            "registry:material_item_ids_exact",
            "material_item_capture_error",
            "material_item_runtime_class_exact",
            "material_item_max_counts_exact",
            "material_item_stack_nbt_round_trips_exact",
            "material_item_save_representations_exact",
            "material_items_captured_after_server_data_load",
            "server_started_material_items_rechecked",
            "material_item_registry_stable_after_reload",
            "material_item_properties_stable_after_reload",
            "material_item_stack_nbt_stable_after_reload",
        )
        expected_values = (
            *("present" for _identifier in forge_server.MATERIAL_ITEM_IDS),
            ",".join(forge_server.MATERIAL_ITEM_IDS),
            "none",
            forge_server.MATERIAL_ITEM_CLASS,
            forge_server.MATERIAL_ITEM_CANONICAL_MAX_COUNTS,
            "true",
            forge_server.MATERIAL_ITEM_CANONICAL_SAVE_REPRESENTATIONS,
            *("true" for _check in range(5)),
        )
        insertion_index = (
            forge_server.EXPECTED_ASSERTION_NAMES.index(
                "particle_wire_contract_stable_after_reload"
            )
            + 1
        )

        self.assertEqual(25, len(expected_names))
        self.assertEqual(
            expected_names,
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index:insertion_index + len(expected_names)
            ],
        )
        self.assertEqual(
            expected_values,
            forge_server.EXPECTED_ASSERTION_VALUES[
                insertion_index:insertion_index + len(expected_values)
            ],
        )
        self.assertEqual(
            "registry:block:etherology:azel_block",
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index + len(expected_names)
            ],
        )

    def test_metal_block_assertions_are_exact_and_probe_ordered(self) -> None:
        expected_names = (
            *(
                assertion_name
                for identifier in forge_server.METAL_BLOCK_IDS
                for assertion_name in (
                    f"registry:block:{identifier}",
                    f"registry:block_item:{identifier}",
                )
            ),
            "registry:metal_block_ids_exact",
            "registry:metal_block_item_ids_exact",
            "metal_block_capture_error",
            "metal_block_runtime_classes_exact",
            "metal_block_item_mappings_exact",
            "metal_block_properties_exact",
            "metal_block_tags_exact",
            "metal_block_stack_nbt_round_trips_exact",
            "metal_block_save_representations_exact",
            "metal_blocks_captured_after_server_data_load",
            "server_started_metal_blocks_rechecked",
            "metal_block_placement_positions_exact",
            "metal_block_placed_ids_exact",
            "metal_block_placement_exact",
            "metal_block_registry_stable_after_reload",
            "metal_block_properties_stable_after_reload",
            "metal_block_tags_stable_after_reload",
            "metal_block_stack_nbt_stable_after_reload",
            "metal_block_placement_stable_after_reload",
        )
        expected_values = (
            *("present" for _entry in range(len(forge_server.METAL_BLOCK_IDS) * 2)),
            ",".join(forge_server.METAL_BLOCK_IDS),
            ",".join(forge_server.METAL_BLOCK_IDS),
            "none",
            "true",
            "true",
            forge_server.METAL_BLOCK_CANONICAL_PROPERTIES,
            "true",
            "true",
            forge_server.METAL_BLOCK_CANONICAL_SAVE_REPRESENTATIONS,
            "true",
            "true",
            forge_server.METAL_BLOCK_CANONICAL_PLACEMENT_POSITIONS,
            forge_server.METAL_BLOCK_CANONICAL_PLACED_IDS,
            *("true" for _check in range(6)),
        )
        insertion_index = (
            forge_server.EXPECTED_ASSERTION_NAMES.index(
                "material_item_stack_nbt_stable_after_reload"
            )
            + 1
        )

        self.assertEqual(25, len(expected_names))
        self.assertEqual(
            expected_names,
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index:insertion_index + len(expected_names)
            ],
        )
        self.assertEqual(
            expected_values,
            forge_server.EXPECTED_ASSERTION_VALUES[
                insertion_index:insertion_index + len(expected_values)
            ],
        )
        self.assertEqual(
            f"registry:block:{forge_server.ATTRAHITE_BLOCK_IDS[0]}",
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index + len(expected_names)
            ],
        )

    def test_attrahite_assertions_are_exact_and_probe_ordered(self) -> None:
        insertion_index = (
            forge_server.EXPECTED_ASSERTION_NAMES.index(
                "metal_block_placement_stable_after_reload"
            )
            + 1
        )

        self.assertEqual(44, len(forge_server.ATTRAHITE_ASSERTION_NAMES))
        self.assertEqual(44, len(forge_server.ATTRAHITE_ASSERTION_VALUES))
        self.assertEqual(
            forge_server.ATTRAHITE_ASSERTION_NAMES,
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index:
                insertion_index + len(forge_server.ATTRAHITE_ASSERTION_NAMES)
            ],
        )
        self.assertEqual(
            forge_server.ATTRAHITE_ASSERTION_VALUES,
            forge_server.EXPECTED_ASSERTION_VALUES[
                insertion_index:
                insertion_index + len(forge_server.ATTRAHITE_ASSERTION_VALUES)
            ],
        )
        self.assertEqual(
            forge_server.SLITHERITE_ASSERTION_NAMES[0],
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index + len(forge_server.ATTRAHITE_ASSERTION_NAMES)
            ],
        )

    def test_attrahite_report_rejects_every_contract_surface_drift(self) -> None:
        mutations = {
            "block registry": lambda report: report["attrahite_blocks"].__setitem__(
                "block_registry_id", "minecraft:item"
            ),
            "block ids": lambda report: report["attrahite_blocks"][
                "block_ids"
            ].pop(),
            "properties": lambda report: report["attrahite_blocks"].__setitem__(
                "properties", "wrong"
            ),
            "tags": lambda report: report["attrahite_blocks"].__setitem__(
                "tags", "wrong"
            ),
            "NBT": lambda report: report["attrahite_blocks"]["entries"][
                "etherology:attrahite"
            ].__setitem__("serialized_keys", ["id"]),
            "placement": lambda report: report["attrahite_blocks"][
                "placement"
            ]["positions"].__setitem__("etherology:attrahite", "0,0,0"),
            "world save": lambda report: report["attrahite_blocks"][
                "placement"
            ].__setitem__("world_saved_after_placement", False),
            "loot ids": lambda report: report["attrahite_blocks"][
                "loaded_data"
            ]["loot_table_ids"].pop(),
            "Silk Touch": lambda report: report["attrahite_blocks"][
                "loaded_data"
            ].__setitem__("raw_silk_touch_loot", "none"),
            "Fortune": lambda report: report["attrahite_blocks"][
                "loaded_data"
            ]["raw_fortune_loot"].__setitem__("3", "wrong"),
            "recipes": lambda report: report["attrahite_blocks"][
                "loaded_data"
            ]["recipes"].pop("etherology:raw_azel"),
            "advancements": lambda report: report["attrahite_blocks"][
                "loaded_data"
            ]["advancements"].pop(
                "etherology:recipes/misc/raw_azel"
            ),
            "loaded-data freshness": lambda report: report["attrahite_blocks"][
                "loaded_data"
            ].__setitem__("fresh_instances_after_reload", False),
            "reload projection": lambda report: report["reload"].__setitem__(
                "attrahite_block_placement_stable", False
            ),
        }

        for label, mutate in mutations.items():
            with self.subTest(label=label):
                report = valid_report()
                mutate(report)
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_probe_report(report, self.configuration)

    def test_food_assertions_are_exact_and_probe_ordered(self) -> None:
        server_started = forge_server.SERVER_STARTED_FOOD_CONSUMPTION
        expected_names = (
            f"registry:item:{forge_server.FOOD_ITEM_ID}",
            "registry:food_item_ids_exact",
            "food_item_capture_error",
            "food_item_runtime_class_exact",
            "food_item_properties_exact",
            "food_item_stack_nbt_round_trip_exact",
            "food_item_save_representation_exact",
            "food_item_contract_exact",
            "food_items_captured_after_server_data_load",
            "server_started_food_items_rechecked",
            "food_item_registry_stable_after_reload",
            "food_item_properties_stable_after_reload",
            "food_item_stack_nbt_stable_after_reload",
            "server_started_food_consumption_capture_error",
            "server_started_food_consumption_player_class",
            "server_started_food_consumption_player_uuid",
            "server_started_food_consumption_player_name",
            "server_started_food_consumption_item_id",
            "server_started_food_consumption_result_item_id",
            "server_started_food_consumption_initial_hunger",
            "server_started_food_consumption_initial_saturation",
            "server_started_food_consumption_initial_stack_count",
            "server_started_food_consumption_result_hunger",
            "server_started_food_consumption_result_saturation",
            "server_started_food_consumption_result_stack_count",
            "server_started_food_consumption_same_stack_instance",
            "server_started_food_consumption_exact",
            "reloaded_food_consumption_capture_error",
            "reloaded_food_consumption_exact",
            "food_consumption_fresh_player_after_reload",
            "food_consumption_stable_after_reload",
        )
        expected_values = (
            "present",
            forge_server.FOOD_ITEM_ID,
            "none",
            forge_server.FOOD_ITEM_CLASS,
            forge_server.FOOD_ITEM_PROPERTIES,
            "true",
            forge_server.FOOD_ITEM_SAVE_REPRESENTATIONS,
            *("true" for _check in range(6)),
            "none",
            forge_server.FOOD_CONSUMPTION_PLAYER_CLASS,
            str(server_started["player_uuid"]),
            str(server_started["player_name"]),
            forge_server.FOOD_ITEM_ID,
            forge_server.FOOD_ITEM_ID,
            str(server_started["initial_hunger"]),
            str(server_started["initial_saturation"]),
            str(server_started["initial_stack_count"]),
            str(server_started["result_hunger"]),
            str(server_started["result_saturation"]),
            str(server_started["result_stack_count"]),
            "true",
            "true",
            "none",
            "true",
            "true",
            "true",
        )
        insertion_index = (
            forge_server.EXPECTED_ASSERTION_NAMES.index(
                forge_server.SLITHERITE_ASSERTION_NAMES[-1]
            )
            + 1
        )

        self.assertEqual(31, len(expected_names))
        self.assertEqual(31, len(expected_values))
        self.assertEqual(
            expected_names,
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index:insertion_index + len(expected_names)
            ],
        )
        self.assertEqual(
            expected_values,
            forge_server.EXPECTED_ASSERTION_VALUES[
                insertion_index:insertion_index + len(expected_values)
            ],
        )
        self.assertEqual(
            f"registry:block:{forge_server.FOREST_LANTERN_ID}",
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index + len(expected_names)
            ],
        )

    def test_food_report_rejects_focused_contract_drift(self) -> None:
        food_assertion_index = forge_server.EXPECTED_ASSERTION_NAMES.index(
            "food_item_contract_exact"
        )
        mutations = {
            "food registry": lambda report: report["food_items"].__setitem__(
                "registry_id", "minecraft:block"
            ),
            "food properties": lambda report: report["food_items"]["entries"][
                forge_server.FOOD_ITEM_ID
            ].__setitem__("hunger", 4),
            "server-started consumption": lambda report: report[
                "food_consumption"
            ]["server_started"].__setitem__("result_hunger", 12),
            "reloaded consumption": lambda report: report["food_consumption"][
                "reloaded"
            ].__setitem__("result_stack_count", 2),
            "fresh player after reload": lambda report: report[
                "food_consumption"
            ].__setitem__("fresh_player_after_reload", False),
            "food registry reload stability": lambda report: report[
                "reload"
            ].__setitem__("food_item_registry_stable", False),
            "food properties reload stability": lambda report: report[
                "reload"
            ].__setitem__("food_item_properties_stable", False),
            "food stack NBT reload stability": lambda report: report[
                "reload"
            ].__setitem__("food_item_stack_nbt_stable", False),
            "food consumption reload stability": lambda report: report[
                "reload"
            ].__setitem__("food_consumption_stable", False),
            "food assertion inventory": lambda report: report["assertions"].pop(
                food_assertion_index
            ),
        }
        for description, mutate in mutations.items():
            with self.subTest(description=description):
                report = valid_report()
                mutate(report)
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_probe_report(report, self.configuration)

    def test_forest_lantern_assertions_are_exact_and_probe_ordered(self) -> None:
        insertion_index = (
            forge_server.EXPECTED_ASSERTION_NAMES.index(
                "food_consumption_stable_after_reload"
            )
            + 1
        )

        self.assertEqual(47, len(forge_server.FOREST_LANTERN_ASSERTION_NAMES))
        self.assertEqual(
            forge_server.FOREST_LANTERN_ASSERTION_NAMES,
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index : insertion_index
                + len(forge_server.FOREST_LANTERN_ASSERTION_NAMES)
            ],
        )
        self.assertEqual(
            forge_server.FOREST_LANTERN_ASSERTION_VALUES,
            forge_server.EXPECTED_ASSERTION_VALUES[
                insertion_index : insertion_index
                + len(forge_server.FOREST_LANTERN_ASSERTION_VALUES)
            ],
        )
        self.assertEqual(
            "registry:loot_condition:etherology:random_chance_with_fortune",
            forge_server.EXPECTED_ASSERTION_NAMES[
                insertion_index + len(forge_server.FOREST_LANTERN_ASSERTION_NAMES)
            ],
        )

    def test_forest_lantern_report_rejects_focused_contract_drift(self) -> None:
        assertion_index = forge_server.EXPECTED_ASSERTION_NAMES.index(
            "forest_lantern_contract_exact"
        )
        mutations = {
            "registry": lambda report: report["forest_lantern"].__setitem__(
                "block_id", "minecraft:air"
            ),
            "twenty states": lambda report: report["forest_lantern"][
                "states"
            ].pop(),
            "default": lambda report: report["forest_lantern"].__setitem__(
                "default_state", "age=0,facing=north"
            ),
            "shape": lambda report: report["forest_lantern"][
                "outline_shapes"
            ].__setitem__("age=4,facing=north", "0,0,0,1,1,1"),
            "tag": lambda report: report["forest_lantern"]["tags"].__setitem__(
                "hoe_mineable", False
            ),
            "loot": lambda report: report["forest_lantern"]["loaded_data"][
                "initial"
            ]["loot_by_age"].__setitem__("4", ""),
            "recipe craft": lambda report: report["forest_lantern"]["loaded_data"][
                "reloaded"
            ].__setitem__("recipe_matches_and_crafts_exact", False),
            "advancement": lambda report: report["forest_lantern"][
                "loaded_data"
            ]["initial"]["advancements"].pop(
                "etherology:recipes/food/forest_lantern_crumb"
            ),
            "placement": lambda report: report["forest_lantern"]["mechanics"][
                "server_started"
            ]["placement"]["facings"].__setitem__("east", "PASS"),
            "shears": lambda report: report["forest_lantern"]["mechanics"][
                "reloaded"
            ]["shears"]["deltas"].__setitem__("4", "1.0"),
            "retain callback": lambda report: report["forest_lantern"][
                "mechanics"
            ]["server_started"]["retain_jump"].__setitem__(
                "single_callback_guard_exact", False
            ),
            "break drop": lambda report: report["forest_lantern"]["mechanics"][
                "reloaded"
            ]["break_jump"].__setitem__("new_item_entity_count", 2),
            "reload freshness": lambda report: report["reload"].__setitem__(
                "forest_lantern_loaded_data_fresh", False
            ),
            "assertion": lambda report: report["assertions"][
                assertion_index
            ].__setitem__("actual", "false"),
        }
        for description, mutate in mutations.items():
            with self.subTest(description=description):
                report = valid_report()
                mutate(report)
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_probe_report(report, self.configuration)

    def test_each_forbidden_mod_requires_an_explicit_false_result(self) -> None:
        for mod_id in forge_server.FORBIDDEN_MOD_IDS:
            with self.subTest(mod_id=mod_id):
                report = valid_report()
                report["mods"][mod_id]["loaded"] = True

                with self.assertRaisesRegex(forge_server.E2EError, "mod subset"):
                    forge_server.validate_probe_report(report, self.configuration)

    def test_report_rejects_every_major_contract_drift(self) -> None:
        mutations = {
            "boolean schema": lambda report: report.__setitem__("schema", True),
            "failed status": lambda report: report.__setitem__("status", "failed"),
            "client distribution": lambda report: report.__setitem__(
                "distribution", "CLIENT"
            ),
            "packaged runtime claim": lambda report: report.__setitem__(
                "runtime_kind", "packaged"
            ),
            "extra mod": lambda report: report["mods"].__setitem__(
                "etherology_e2e_harness", {"loaded": True}
            ),
            "loaded forbidden id": lambda report: report["loaded_mod_ids"].append(
                "quickskin"
            ),
            "reported forbidden intersection": lambda report: report.__setitem__(
                "forbidden_mod_ids_loaded", ["quickskin"]
            ),
            "unsorted loaded ids": lambda report: report["loaded_mod_ids"].reverse(),
            "duplicate loaded id": lambda report: report["loaded_mod_ids"].append(
                "minecraft"
            ),
            "missing generated mod id": lambda report: report[
                "loaded_mod_ids"
            ].remove("generated_1234567"),
            "unexpected loaded mod id": lambda report: report[
                "loaded_mod_ids"
            ].append("othermod"),
            "missing forbidden proof": lambda report: report["mods"].pop("emi"),
            "wrong registry id": lambda report: report["registry"].__setitem__(
                "event_id", "etherology:wrong"
            ),
            "wrong internal id": lambda report: report["registry"].__setitem__(
                "internal_id", "wrong"
            ),
            "wrong range": lambda report: report["registry"].__setitem__("range", 15),
            "wrong enchantment class": lambda report: report["enchantments"][
                "peal"
            ].__setitem__("class", "wrong.PealEnchantment"),
            "wrong enchantment power": lambda report: report["enchantments"][
                "peal"
            ]["max_powers"].__setitem__(2, 42),
            "missing non-treasure enchantment": lambda report: report[
                "enchantments"
            ]["non_treasure_etherology_enchantment_ids"].remove(
                "etherology:reflection"
            ),
            "enchantment reload instability": lambda report: report["reload"].__setitem__(
                "enchantment_registry_stable", False
            ),
            "wrong particle type class": lambda report: report["particles"][
                "entries"
            ]["alchemy"].__setitem__("type_class", "wrong.FeyParticleType"),
            "wrong particle spawn policy": lambda report: report["particles"][
                "entries"
            ]["alchemy"].__setitem__("should_always_spawn", True),
            "wrong particle factory sample": lambda report: report["particles"][
                "entries"
            ]["spark"].__setitem__("factory_sample_as_string", "wrong"),
            "failed particle packet round trip": lambda report: report[
                "particles"
            ]["entries"]["seal"].__setitem__("packet_round_trip_exact", False),
            "wrong SealType color": lambda report: report["particles"][
                "seal_types"
            ]["entries"]["keta"].__setitem__("start_color", "0,0,0"),
            "particle reload instability": lambda report: report[
                "reload"
            ].__setitem__("particle_wire_contract_stable", False),
            "wrong material item registry": lambda report: report[
                "material_items"
            ].__setitem__("registry_id", "minecraft:block"),
            "missing material item id": lambda report: report[
                "material_items"
            ]["material_item_ids"].remove("etherology:thuja_oil"),
            "wrong material item class": lambda report: report[
                "material_items"
            ]["entries"]["etherology:etheroscope"].__setitem__(
                "runtime_class",
                "wrong.Item",
            ),
            "wrong enriched attrahite count": lambda report: report[
                "material_items"
            ]["entries"]["etherology:enriched_attrahite"].__setitem__(
                "max_count",
                64,
            ),
            "wrong material stack NBT keys": lambda report: report[
                "material_items"
            ]["entries"]["etherology:binder"].__setitem__(
                "serialized_keys",
                ["id"],
            ),
            "failed material stack round trip": lambda report: report[
                "material_items"
            ]["entries"]["etherology:ebony"].__setitem__(
                "round_trip_exact",
                False,
            ),
            "material item reload instability": lambda report: report[
                "reload"
            ].__setitem__("material_item_stack_nbt_stable", False),
            "wrong metal block registry": lambda report: report[
                "metal_blocks"
            ].__setitem__("block_registry_id", "minecraft:item"),
            "missing metal block id": lambda report: report[
                "metal_blocks"
            ]["metal_block_ids"].remove("etherology:azel_block"),
            "wrong metal block class": lambda report: report[
                "metal_blocks"
            ]["entries"]["etherology:ethril_block"].__setitem__(
                "block_class",
                "wrong.Block",
            ),
            "wrong metal block mapping": lambda report: report[
                "metal_blocks"
            ]["entries"]["etherology:azel_block"].__setitem__(
                "block_item_maps_to_block",
                False,
            ),
            "wrong metal block hardness": lambda report: report[
                "metal_blocks"
            ]["entries"]["etherology:ethril_block"].__setitem__(
                "hardness",
                5.0,
            ),
            "wrong metal block beacon tag": lambda report: report[
                "metal_blocks"
            ]["entries"]["etherology:azel_block"].__setitem__(
                "beacon_base",
                True,
            ),
            "wrong metal BlockItem NBT": lambda report: report[
                "metal_blocks"
            ]["entries"]["etherology:ebony_block"].__setitem__(
                "serialized_count",
                1,
            ),
            "wrong metal block placement": lambda report: report[
                "metal_blocks"
            ]["placement"]["placed_block_ids"].__setitem__(
                "etherology:azel_block",
                "minecraft:air",
            ),
            "metal block reload instability": lambda report: report[
                "reload"
            ].__setitem__("metal_block_placement_stable", False),
            "integer loot identity": lambda report: report["loot_condition"].__setitem__(
                "same_state_at_server_started", 1
            ),
            "wrong initial Ether source": lambda report: report["ether_sources"][
                "initial"
            ]["entries"].__setitem__("minecraft:redstone", 3.0),
            "legacy Ether source typo": lambda report: report["ether_sources"][
                "reloaded"
            ]["entries"].__setitem__("etherology:primoshard_rela", 4.0),
            "reload command missing": lambda report: report["reload"].__setitem__(
                "command", ""
            ),
            "wrong enabled pack name": lambda report: report[
                "reload"
            ].__setitem__("enabled_pack_name", "file/foreign"),
            "enabled pack missing": lambda report: report["reload"][
                "enabled_data_pack_names"
            ].remove(forge_server.RELOAD_PACK_ENABLED_NAME),
            "enabled packs unsorted": lambda report: report["reload"][
                "enabled_data_pack_names"
            ].reverse(),
            "enabled packs exact false": lambda report: report[
                "reload"
            ].__setitem__("enabled_data_packs_exact", False),
            "reload instability": lambda report: report["reload"].__setitem__(
                "registry_stable", False
            ),
            "wrong update cause": lambda report: report["tags"].__setitem__(
                "update_cause", "OTHER"
            ),
            "missing reload tag update": lambda report: report["tags"].__setitem__(
                "update_count", 1
            ),
            "missing vibration": lambda report: report["tags"][
                "vibrations"
            ].__setitem__("contains_event", False),
            "wrong lifecycle": lambda report: report["lifecycle"].reverse(),
            "false assertion": lambda report: report["assertions"][0].__setitem__(
                "passed", False
            ),
            "integer assertion status": lambda report: report["assertions"][0].__setitem__(
                "passed", 1
            ),
            "mismatched assertion actual": lambda report: report["assertions"][
                0
            ].__setitem__("actual", "wrong"),
            "extra field": lambda report: report.__setitem__("unexpected", True),
        }
        for description, mutate in mutations.items():
            with self.subTest(description=description):
                report = valid_report()
                mutate(report)
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_probe_report(report, self.configuration)


class LifecycleEvidenceTests(unittest.TestCase):
    def test_exact_server_log_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "latest.log"
            content = valid_server_log()
            path.write_bytes(content)

            self.assertEqual(content, forge_server.validate_server_log(path))

            allowed_client_class = (
                content
                + b"[LanServerPinger #1/WARN] "
                + b"[net.minecraft.client.network.LanServerPinger/] No route\n"
            )
            path.write_bytes(allowed_client_class)
            self.assertEqual(
                allowed_client_class,
                forge_server.validate_server_log(path),
            )

    def test_server_log_uses_the_distinct_48_mib_boundary(self) -> None:
        self.assertEqual(48 * 1024 * 1024, forge_server.MAXIMUM_SERVER_LOG_SIZE)
        self.assertEqual(64 * 1024 * 1024, forge_server.MAXIMUM_PROCESS_LOG_SIZE)
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "latest.log"
            content = valid_server_log()
            path.write_bytes(content)
            with mock.patch.object(
                forge_server, "MAXIMUM_SERVER_LOG_SIZE", len(content)
            ):
                self.assertEqual(content, forge_server.validate_server_log(path))
            with (
                mock.patch.object(
                    forge_server, "MAXIMUM_SERVER_LOG_SIZE", len(content) - 1
                ),
                self.assertRaisesRegex(forge_server.E2EError, "invalid size"),
            ):
                forge_server.validate_server_log(path)

    def test_server_log_rejects_fatal_client_missing_duplicate_and_reordered_markers(
        self,
    ) -> None:
        base = valid_server_log().decode("utf-8")
        mutations = {
            "fatal": base + "[FATAL] failure\n",
            "error level": base + "[main/ERROR] failure\n",
            "reload failure": base + "Failed to execute reload\n",
            "unexpected client class": base
            + "[main/INFO] "
            + "[net.minecraft.client.gui.screen.TitleScreen/] loaded\n",
            "missing save": base.replace("Saving worlds\n", ""),
            "duplicate lifecycle": base
            + "[Server thread/INFO] [EtherologyServerProbe] server_started\n",
            "unexpected lifecycle": base
            + "[Server thread/INFO] [EtherologyServerProbe] unexpected_phase\n",
            "failed userdev exit": base.replace(
                "loom_userdev_exit_scheduled status=0 "
                "server_thread_join_timeout_ms=30000",
                "loom_userdev_exit_scheduled status=1 "
                "server_thread_join_timeout_ms=30000",
            ),
            "reordered lifecycle": base.replace(
                "[EtherologyServerProbe] tags_updated_initial",
                "[EtherologyServerProbe] temporary",
            ).replace(
                "[EtherologyServerProbe] server_started",
                "[EtherologyServerProbe] tags_updated_initial",
            ).replace(
                "[EtherologyServerProbe] temporary",
                "[EtherologyServerProbe] server_started",
            ),
            **{
                f"client marker {marker}": base + marker + "\n"
                for marker in forge_server.CLIENT_LOG_MARKERS
            },
        }
        for description, content in mutations.items():
            with self.subTest(description=description), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "latest.log"
                path.write_text(content, encoding="utf-8")
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_server_log(path)

    def test_symlinked_server_log_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            target = root / "target.log"
            target.write_bytes(valid_server_log())
            linked = root / "latest.log"
            linked.symlink_to(target)

            with self.assertRaisesRegex(forge_server.E2EError, "linked"):
                forge_server.validate_server_log(linked)

    def test_world_save_requires_nonempty_level_data_and_no_crash_report(self) -> None:
        with temporary_repository() as (root, manifest_path):
            configuration = load_temporary_configuration(root, manifest_path)
            with tempfile.TemporaryDirectory() as temporary_directory:
                state_root = Path(temporary_directory) / ".state"
                with mock.patch.object(forge_server, "require_native_run_ready"):
                    forge_server.provision_profile(configuration, state_root)
                runtime = forge_server.runtime_root(configuration, state_root)
                level_data = (
                    forge_server.game_directory(configuration, runtime)
                    / "world"
                    / "level.dat"
                )
                level_data.write_bytes(b"world")

                self.assertEqual(
                    level_data,
                    forge_server.validate_world_save(configuration, runtime),
                )
                crash = (
                    forge_server.game_directory(configuration, runtime)
                    / "crash-reports"
                    / "crash.txt"
                )
                crash.write_text("crash", encoding="utf-8")
                with self.assertRaisesRegex(forge_server.E2EError, "crash report"):
                    forge_server.validate_world_save(configuration, runtime)


class FakeProcess:
    def __init__(self, exit_code: int = 0, timeout: bool = False) -> None:
        self.exit_code = exit_code
        self.timeout = timeout
        self.pid = 43210

    def wait(self, timeout: int | None = None) -> int:
        if self.timeout:
            raise subprocess.TimeoutExpired("gradle", timeout)
        return self.exit_code

    def poll(self) -> int | None:
        if self.timeout:
            return None
        return self.exit_code


SERVER_LAUNCH_PROCESS_ID = 43210
SERVER_JAVA_PROCESS_ID = 54321
SERVER_RUN_TOKEN = "a" * 64
SERVER_TARGET = forge_server.macos_guarded_java.OwnedJavaProcess(
    pid=SERVER_JAVA_PROCESS_ID,
    process_group_id=SERVER_LAUNCH_PROCESS_ID,
    proc_start_abstime=987654321,
    expected_executable="/test/jdk17/bin/java",
)


def valid_memory_handoff() -> dict[str, object]:
    return {
        "schema": 1,
        "run_token": SERVER_RUN_TOKEN,
        "pid": SERVER_JAVA_PROCESS_ID,
        "executable": SERVER_TARGET.expected_executable,
        "java_feature": 17,
        "maximum_heap_bytes": 2048 * 1024 * 1024,
        "maximum_heap_arguments": ["-Xmx2048m"],
    }


def write_memory_handoff(
    runtime: Path,
    payload: dict[str, object] | None = None,
) -> Path:
    handoff_path, _acknowledgement_path = forge_server.server_memory_handoff_paths(
        runtime
    )
    handoff_path.write_text(
        json.dumps(valid_memory_handoff() if payload is None else payload) + "\n",
        encoding="utf-8",
    )
    return handoff_path


def fake_server_guard(
    runtime: Path,
    *,
    monitor_alive: bool = True,
) -> forge_server.ServerJavaGuard:
    telemetry_path = runtime / "memory-guard-telemetry.json"
    readiness_path = runtime / ".memory-guard-ready.json"
    monitor_process = FakeProcess(0, timeout=monitor_alive)
    monitor_process.pid = 60001
    monitor = forge_server.macos_guarded_java.GuardedJavaMonitor(
        process=monitor_process,
        target=SERVER_TARGET,
        telemetry_path=telemetry_path,
        readiness_path=readiness_path,
    )
    caffeinate_process = FakeProcess(0, timeout=True)
    caffeinate_process.pid = 60002
    handoff_path, acknowledgement_path = forge_server.server_memory_handoff_paths(
        runtime
    )
    return forge_server.ServerJavaGuard(
        target=SERVER_TARGET,
        monitor=monitor,
        caffeinate_process=caffeinate_process,
        handoff_path=handoff_path,
        acknowledgement_path=acknowledgement_path,
    )


class ServerJavaHandoffTests(unittest.TestCase):
    def test_exact_handoff_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            handoff_path = write_memory_handoff(runtime)

            self.assertEqual(
                (SERVER_JAVA_PROCESS_ID, SERVER_TARGET.expected_executable),
                forge_server.validate_server_java_handoff(
                    handoff_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    SERVER_LAUNCH_PROCESS_ID,
                ),
            )

    def test_identity_heap_and_field_drift_are_rejected(self) -> None:
        mutations = {
            "boolean schema": lambda value: value.__setitem__("schema", True),
            "wrong token": lambda value: value.__setitem__("run_token", "b" * 64),
            "boolean pid": lambda value: value.__setitem__("pid", True),
            "wrapper pid": lambda value: value.__setitem__(
                "pid", SERVER_LAUNCH_PROCESS_ID
            ),
            "relative executable": lambda value: value.__setitem__(
                "executable", "bin/java"
            ),
            "wrong Java": lambda value: value.__setitem__("java_feature", 21),
            "boolean Java": lambda value: value.__setitem__("java_feature", True),
            "wrong heap": lambda value: value.__setitem__(
                "maximum_heap_bytes", 4096 * 1024 * 1024
            ),
            "boolean heap": lambda value: value.__setitem__(
                "maximum_heap_bytes", True
            ),
            "normalized heap spelling": lambda value: value.__setitem__(
                "maximum_heap_arguments", ["-Xmx2048M"]
            ),
            "duplicate heap": lambda value: value.__setitem__(
                "maximum_heap_arguments", ["-Xmx2048m", "-Xmx2048m"]
            ),
            "extra field": lambda value: value.__setitem__("other", 1),
        }
        for description, mutation in mutations.items():
            with self.subTest(description=description), tempfile.TemporaryDirectory() as temporary:
                runtime = Path(temporary).resolve()
                handoff = valid_memory_handoff()
                mutation(handoff)
                handoff_path = write_memory_handoff(runtime, handoff)

                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_server_java_handoff(
                        handoff_path,
                        runtime,
                        SERVER_RUN_TOKEN,
                        SERVER_LAUNCH_PROCESS_ID,
                    )

    def test_symlinked_and_oversized_handoffs_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            runtime = root / "runtime"
            runtime.mkdir()
            foreign = root / "foreign.json"
            foreign.write_text(json.dumps(valid_memory_handoff()), encoding="utf-8")
            handoff_path, _acknowledgement = forge_server.server_memory_handoff_paths(
                runtime
            )
            handoff_path.symlink_to(foreign)

            with self.assertRaisesRegex(forge_server.E2EError, "symlink"):
                forge_server.validate_server_java_handoff(
                    handoff_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    SERVER_LAUNCH_PROCESS_ID,
                )

        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            handoff_path, _acknowledgement = forge_server.server_memory_handoff_paths(
                runtime
            )
            handoff_path.write_bytes(
                b"x" * (forge_server.MAXIMUM_MEMORY_HANDOFF_SIZE + 1)
            )
            with self.assertRaisesRegex(forge_server.E2EError, "invalid size"):
                forge_server.validate_server_java_handoff(
                    handoff_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    SERVER_LAUNCH_PROCESS_ID,
                )

    def test_wait_binds_only_the_java_child_in_the_owned_launch_group(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            write_memory_handoff(runtime)
            process = FakeProcess(timeout=True)
            sampler = mock.Mock()
            sampler.bind.return_value = SERVER_TARGET
            with mock.patch.object(
                forge_server.os,
                "getpgid",
                return_value=SERVER_LAUNCH_PROCESS_ID,
            ):
                target = forge_server.wait_for_server_java_handoff(
                    process,
                    output_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    sampler,
                    time.monotonic() + 1.0,
                )

            self.assertEqual(SERVER_TARGET, target)
            sampler.bind.assert_called_once_with(
                SERVER_JAVA_PROCESS_ID,
                SERVER_LAUNCH_PROCESS_ID,
                SERVER_TARGET.expected_executable,
            )

    def test_wait_rejects_a_java_pid_outside_the_owned_launch_group(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            write_memory_handoff(runtime)
            process = FakeProcess(timeout=True)
            sampler = mock.Mock()
            with (
                mock.patch.object(
                    forge_server.os,
                    "getpgid",
                    return_value=SERVER_LAUNCH_PROCESS_ID + 1,
                ),
                self.assertRaisesRegex(forge_server.E2EError, "outside"),
            ):
                forge_server.wait_for_server_java_handoff(
                    process,
                    output_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    sampler,
                    time.monotonic() + 1.0,
                )

            sampler.bind.assert_not_called()

    def test_wait_fails_closed_when_no_handoff_arrives(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            process = FakeProcess(timeout=True)
            with (
                mock.patch.object(
                    forge_server,
                    "MEMORY_HANDOFF_TIMEOUT_SECONDS",
                    0.0,
                ),
                self.assertRaisesRegex(forge_server.E2EError, "Timed out"),
            ):
                forge_server.wait_for_server_java_handoff(
                    process,
                    output_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    mock.Mock(),
                    time.monotonic() + 1.0,
                )


class ServerJavaGuardStartTests(unittest.TestCase):
    def test_monitor_and_caffeinate_are_live_before_acknowledgement(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            caffeinate_path = runtime / "caffeinate"
            caffeinate_path.write_bytes(b"")
            process = FakeProcess(timeout=True)
            monitor_guard = fake_server_guard(runtime).monitor
            assert monitor_guard is not None
            caffeinate_process = FakeProcess(timeout=True)
            events: list[str] = []

            def start_monitor(*_args: object, **_kwargs: object) -> object:
                events.append("monitor")
                return monitor_guard

            def start_caffeinate(*_args: object, **_kwargs: object) -> object:
                events.append("caffeinate")
                return caffeinate_process

            real_write = forge_server.write_bytes_exclusive

            def write_acknowledgement(path: Path, content: bytes) -> None:
                events.append("acknowledgement")
                real_write(path, content)

            with (
                mock.patch.object(
                    forge_server,
                    "wait_for_server_java_handoff",
                    return_value=SERVER_TARGET,
                ),
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "start_guarded_java_monitor",
                    side_effect=start_monitor,
                ),
                mock.patch.object(
                    forge_server.subprocess,
                    "Popen",
                    side_effect=start_caffeinate,
                ) as popen,
                mock.patch.object(
                    forge_server,
                    "write_bytes_exclusive",
                    side_effect=write_acknowledgement,
                ),
            ):
                guard = forge_server.start_server_java_guard(
                    process,
                    output_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    mock.Mock(),
                    time.monotonic() + 1.0,
                    runtime,
                    mock.Mock(),
                    caffeinate_path,
                )

            self.assertEqual(
                ["monitor", "caffeinate", "acknowledgement"],
                events,
            )
            self.assertEqual(SERVER_TARGET, guard.target)
            self.assertEqual(
                f"token={SERVER_RUN_TOKEN}\n",
                guard.acknowledgement_path.read_text(encoding="ascii"),
            )
            self.assertEqual(
                [
                    str(caffeinate_path),
                    "-dimsu",
                    "-w",
                    str(SERVER_JAVA_PROCESS_ID),
                ],
                popen.call_args.args[0],
            )

    def test_failed_post_monitor_handoff_retains_partial_guard(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            caffeinate_path = runtime / "caffeinate"
            caffeinate_path.write_bytes(b"")
            process = FakeProcess(timeout=True)
            monitor_guard = fake_server_guard(runtime).monitor
            assert monitor_guard is not None
            with (
                mock.patch.object(
                    forge_server,
                    "wait_for_server_java_handoff",
                    return_value=SERVER_TARGET,
                ),
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "start_guarded_java_monitor",
                    return_value=monitor_guard,
                ),
                mock.patch.object(
                    forge_server.subprocess,
                    "Popen",
                    side_effect=OSError("caffeinate failed"),
                ),
                self.assertRaises(forge_server.ServerGuardStartError) as raised,
            ):
                forge_server.start_server_java_guard(
                    process,
                    output_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    mock.Mock(),
                    time.monotonic() + 1.0,
                    runtime,
                    mock.Mock(),
                    caffeinate_path,
                )

            self.assertEqual(SERVER_TARGET, raised.exception.server_guard.target)
            self.assertIs(monitor_guard, raised.exception.server_guard.monitor)
            self.assertIsNone(raised.exception.server_guard.caffeinate_process)
            self.assertIsNone(monitor_guard.process.poll())


class ServerGuardHealthTests(unittest.TestCase):
    def test_current_live_monitor_needs_no_secondary_sample(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            guard = fake_server_guard(Path(temporary_directory).resolve())
            sampler = mock.Mock()
            with mock.patch.object(
                forge_server.macos_guarded_java,
                "memory_guard_is_enforcing",
                return_value=True,
            ) as guard_health:
                forge_server.verify_server_guard_is_enforcing(guard, sampler)

            sampler.sample.assert_not_called()
            state = guard_health.call_args.args[0]
            self.assertEqual(
                forge_server.SERVER_MAXIMUM_MEMORY_MB,
                state["memory_guard_maximum_memory_mb"],
            )

    def test_live_java_with_dead_or_stale_monitor_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            guard = fake_server_guard(
                Path(temporary_directory).resolve(),
                monitor_alive=False,
            )
            sampler = mock.Mock()
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.AVAILABLE
            )
            with (
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "memory_guard_is_enforcing",
                    return_value=False,
                ),
                self.assertRaisesRegex(forge_server.E2EError, "lost"),
            ):
                forge_server.verify_server_guard_is_enforcing(guard, sampler)

    def test_monitor_exit_after_exact_target_disappears_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            guard = fake_server_guard(
                Path(temporary_directory).resolve(),
                monitor_alive=False,
            )
            sampler = mock.Mock()
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.MISSING
            )
            with mock.patch.object(
                forge_server.macos_guarded_java,
                "memory_guard_is_enforcing",
                return_value=False,
            ):
                forge_server.verify_server_guard_is_enforcing(guard, sampler)

    def test_identity_drift_during_monitor_failure_is_not_treated_as_exit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            guard = fake_server_guard(
                Path(temporary_directory).resolve(),
                monitor_alive=False,
            )
            sampler = mock.Mock()
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.IDENTITY_DRIFT
            )
            with (
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "memory_guard_is_enforcing",
                    return_value=False,
                ),
                self.assertRaisesRegex(forge_server.E2EError, "identity-drift"),
            ):
                forge_server.verify_server_guard_is_enforcing(guard, sampler)

    def test_target_exit_allows_gradle_to_finish_its_clean_wait(self) -> None:
        class FinishingGradleProcess:
            pid = SERVER_LAUNCH_PROCESS_ID

            def __init__(self) -> None:
                self.wait_count = 0

            def poll(self) -> int | None:
                return None if self.wait_count == 0 else 0

            def wait(self, timeout: float | None = None) -> int:
                self.wait_count += 1
                return 0

        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            guard = fake_server_guard(runtime, monitor_alive=False)
            sampler = mock.Mock()
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.MISSING
            )
            process = FinishingGradleProcess()
            with mock.patch.object(
                forge_server.macos_guarded_java,
                "memory_guard_is_enforcing",
                return_value=False,
            ):
                exit_code = forge_server.wait_for_bounded_process(
                    process,
                    output_path,
                    run_deadline=time.monotonic() + 1.0,
                    server_guard=guard,
                    sampler=sampler,
                )

            self.assertEqual(0, exit_code)
            self.assertEqual(1, process.wait_count)

    def test_clean_exit_requires_target_and_launch_group_both_absent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            guard = fake_server_guard(
                Path(temporary_directory).resolve(),
                monitor_alive=False,
            )
            process = FakeProcess(exit_code=0)
            sampler = mock.Mock()
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.MISSING
            )
            with mock.patch.object(
                forge_server,
                "process_group_exists",
                return_value=False,
            ):
                forge_server.require_guarded_server_stopped(
                    process,
                    guard,
                    sampler,
                )

    def test_exact_server_heap_bytes_match_the_xmx2048m_semantics(self) -> None:
        self.assertEqual(2_147_483_648, forge_server.SERVER_MAXIMUM_HEAP_BYTES)
        self.assertEqual("-Xmx2048m", forge_server.SERVER_MAXIMUM_HEAP_ARGUMENT)
        source = (
            forge_server.REPOSITORY_ROOT
            / forge_server.MEMORY_HANDOFF_SOURCE_RELATIVE_PATH
        ).read_text(encoding="utf-8")
        self.assertIn(
            "maximumHeapBytes != EXACT_MAXIMUM_HEAP_BYTES",
            source,
        )


class ServerLaunchCleanupTests(unittest.TestCase):
    def test_live_exact_server_stops_only_its_recorded_launch_group(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            guard = fake_server_guard(runtime)
            process = FakeProcess(timeout=True)
            sampler = mock.Mock()
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.AVAILABLE
            )
            process.wait = mock.Mock(return_value=0)
            with (
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "stop_owned_java_process",
                ) as stop_java,
                mock.patch.object(forge_server, "require_guarded_server_stopped"),
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "stop_spawned_auxiliary",
                ) as stop_auxiliary,
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "stop_guarded_java_monitor",
                ) as stop_monitor,
            ):
                forge_server.cleanup_server_launch(process, guard, sampler)

            stop_java.assert_called_once_with(
                SERVER_TARGET,
                owned_process_group_id=SERVER_LAUNCH_PROCESS_ID,
                timeout_seconds=forge_server.PROCESS_STOP_TIMEOUT_SECONDS,
                sampler=sampler,
            )
            stop_auxiliary.assert_called_once_with(guard.caffeinate_process)
            stop_monitor.assert_called_once_with(guard.monitor)

    def test_identity_uncertainty_never_signals_or_stops_the_monitor(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            guard = fake_server_guard(Path(temporary_directory).resolve())
            process = FakeProcess(timeout=True)
            sampler = mock.Mock()
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.IDENTITY_DRIFT
            )
            with (
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "stop_owned_java_process",
                ) as stop_java,
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "stop_spawned_auxiliary",
                ) as stop_auxiliary,
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "stop_guarded_java_monitor",
                ) as stop_monitor,
                self.assertRaisesRegex(forge_server.E2EError, "uncertain"),
            ):
                forge_server.cleanup_server_launch(process, guard, sampler)

            stop_java.assert_not_called()
            stop_auxiliary.assert_not_called()
            stop_monitor.assert_not_called()

    def test_wrong_gradle_group_never_receives_a_signal(self) -> None:
        process = FakeProcess(timeout=True)
        with (
            mock.patch.object(
                forge_server.os,
                "getpgid",
                return_value=process.pid + 1,
            ),
            mock.patch.object(forge_server.os, "killpg") as kill_group,
            self.assertRaisesRegex(forge_server.E2EError, "no longer owns"),
        ):
            forge_server.stop_process_group(process)

        kill_group.assert_not_called()


class ExecutionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.native_run_guard = mock.patch.object(
            forge_server,
            "require_native_run_ready",
        )
        self.native_run_guard.start()
        self.repository_context = temporary_repository()
        self.repository_root, self.manifest_path = self.repository_context.__enter__()
        self.configuration = load_temporary_configuration(
            self.repository_root, self.manifest_path
        )
        self.state_context = tempfile.TemporaryDirectory()
        self.state_root = Path(self.state_context.name).resolve() / ".state"
        forge_server.provision_profile(self.configuration, self.state_root)
        self.sampler = mock.Mock()
        self.server_guard = mock.Mock()
        self.execution_patchers = (
            mock.patch.object(
                forge_server.macos_guarded_java.MacOsProcessMemorySampler,
                "native",
                return_value=self.sampler,
            ),
            mock.patch.object(forge_server, "verify_owned_gradle_process_group"),
            mock.patch.object(
                forge_server,
                "start_server_java_guard",
                return_value=self.server_guard,
            ),
            mock.patch.object(forge_server, "verify_server_guard_is_enforcing"),
            mock.patch.object(forge_server, "require_guarded_server_stopped"),
            mock.patch.object(forge_server, "cleanup_server_launch"),
        )
        (
            self.native_sampler,
            self.verify_launch_group,
            self.start_server_guard,
            self.verify_guard_health,
            self.require_server_stopped,
            self.cleanup_launch,
        ) = tuple(patcher.start() for patcher in self.execution_patchers)

    def tearDown(self) -> None:
        for patcher in reversed(self.execution_patchers):
            patcher.stop()
        self.state_context.cleanup()
        self.repository_context.__exit__(None, None, None)
        self.native_run_guard.stop()

    def publish_probe_outputs(self, output_handle: object) -> None:
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        report_path = forge_server.evidence_path(
            self.configuration, "report", runtime
        )
        report_path.write_text(
            json.dumps(valid_report(), indent=2) + "\n", encoding="utf-8"
        )
        game = forge_server.game_directory(self.configuration, runtime)
        (game / "logs" / "latest.log").write_bytes(valid_server_log())
        (game / "world" / "level.dat").write_bytes(b"saved-world")
        output_handle.write(b"BUILD SUCCESSFUL\n")

    def assert_launch_attempt_is_exact(self) -> None:
        attempt = forge_server.run_attempt_path(
            self.configuration,
            self.state_root,
        )
        self.assertEqual(
            (
                f"profile_id={forge_server.PROFILE_ID}\n"
                f"scenario={forge_server.SCENARIO_ID}\n"
                f"pid={os.getpid()}\n"
            ),
            attempt.read_text(encoding="utf-8"),
        )

    def test_exclusive_control_file_is_file_and_directory_synced(self) -> None:
        path = self.state_root / "durability-fixture"
        with mock.patch.object(
            forge_server.os,
            "fsync",
            wraps=os.fsync,
        ) as sync:
            forge_server.write_bytes_exclusive(path, b"durable\n")

        self.assertEqual(b"durable\n", path.read_bytes())
        self.assertEqual(2, sync.call_count)

    def test_zero_exit_validates_and_publishes_done_last(self) -> None:
        launch: dict[str, object] = {}
        launch_events: list[str] = []

        def create_sampler() -> object:
            launch_events.append("sampler")
            return self.sampler

        def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
            launch_events.append("gradle")
            launch.update(kwargs)
            launch["run_lock"] = forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).read_text(encoding="utf-8")
            self.publish_probe_outputs(kwargs["stdout"])
            return FakeProcess()

        self.native_sampler.side_effect = create_sampler

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
        ):
            result = forge_server.execute_probe(self.configuration, self.state_root)

        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        scenario = forge_server.evidence_root(self.configuration, runtime)
        self.assertEqual(0, result["exit_code"])
        self.assertFalse(result["timed_out"])
        self.assertEqual(self.repository_root, launch["cwd"])
        self.assertTrue(launch["start_new_session"])
        self.assertEqual(subprocess.DEVNULL, launch["stdin"])
        self.assertEqual(subprocess.STDOUT, launch["stderr"])
        self.assertEqual("/jdk21", launch["env"]["JAVA_HOME"])
        self.assertEqual(["sampler", "gradle"], launch_events)
        run_token = launch["env"][forge_server.RUN_TOKEN_ENVIRONMENT_VARIABLE]
        self.assertRegex(run_token, r"^[0-9a-f]{64}$")
        self.assertRegex(launch["run_lock"], r"^pid=[1-9][0-9]*\n")
        self.assertIn(f"token={run_token}\n", launch["run_lock"])
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        handoff_path, acknowledgement_path = forge_server.server_memory_handoff_paths(
            runtime
        )
        self.assertEqual(
            str(handoff_path),
            launch["env"][forge_server.MEMORY_HANDOFF_ENVIRONMENT_VARIABLE],
        )
        self.assertEqual(
            str(acknowledgement_path),
            launch["env"][
                forge_server.MEMORY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE
            ],
        )
        self.start_server_guard.assert_called_once()
        self.cleanup_launch.assert_called_once_with(
            mock.ANY,
            self.server_guard,
            self.sampler,
        )
        self.assertEqual(
            forge_server.COMPLETION_MARKER_CONTENT,
            (scenario / "reports/done.marker").read_bytes(),
        )
        self.assertEqual(
            valid_server_log(),
            (scenario / "logs/latest.log").read_bytes(),
        )
        launcher = json.loads(
            (scenario / "reports/launcher-result.json").read_text(encoding="utf-8")
        )
        self.assertEqual(
            forge_server.sha256_file(scenario / "logs/latest.log"),
            launcher["server_log"]["sha256"],
        )
        self.assertGreaterEqual(
            (scenario / "reports/done.marker").stat().st_mtime_ns,
            (scenario / "reports/launcher-result.json").stat().st_mtime_ns,
        )
        self.assertFalse(forge_server.run_lock_path(self.configuration, self.state_root).exists())
        self.assert_launch_attempt_is_exact()

    def test_nonzero_exit_publishes_no_runner_evidence(self) -> None:
        def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
            kwargs["stdout"].write(b"BUILD FAILED\n")
            return FakeProcess(exit_code=1)

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
            self.assertRaisesRegex(forge_server.E2EError, "exited with 1"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "launcher_result", runtime
            ).exists()
        )
        self.assert_launch_attempt_is_exact()
        with self.assertRaisesRegex(
            forge_server.E2EError,
            "launch attempt.*consumed",
        ):
            forge_server.require_unattempted_profile(
                self.configuration,
                self.state_root,
            )

    def test_timeout_contains_process_group_and_publishes_no_done_marker(self) -> None:
        process = FakeProcess(timeout=True)
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", return_value=process),
            mock.patch.object(forge_server, "RUN_TIMEOUT_SECONDS", 0),
            self.assertRaisesRegex(forge_server.E2EError, "timed out"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        self.cleanup_launch.assert_called_once_with(
            process,
            self.server_guard,
            self.sampler,
        )
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )
        self.assert_launch_attempt_is_exact()

    def test_keyboard_interrupt_stops_the_owned_process_group(self) -> None:
        process = mock.Mock(pid=43210)
        process.poll.return_value = None
        process.wait.side_effect = KeyboardInterrupt
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", return_value=process),
            self.assertRaisesRegex(forge_server.E2EError, "interrupted"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        self.cleanup_launch.assert_called_once_with(
            process,
            self.server_guard,
            self.sampler,
        )
        self.assert_launch_attempt_is_exact()

    def test_process_output_limit_is_enforced_while_process_is_running(self) -> None:
        process = FakeProcess(timeout=True)

        def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
            kwargs["stdout"].write(b"123456789")
            return process

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
            mock.patch.object(forge_server, "MAXIMUM_PROCESS_LOG_SIZE", 8),
            self.assertRaisesRegex(forge_server.E2EError, "during execution"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        self.cleanup_launch.assert_called_once_with(
            process,
            self.server_guard,
            self.sampler,
        )
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )
        self.assert_launch_attempt_is_exact()

    def test_poll_exit_cannot_race_past_the_process_output_limit(self) -> None:
        class PollAppendingProcess:
            def __init__(self, output_handle: object) -> None:
                self.output_handle = output_handle
                self.appended = False
                self.pid = 43210

            def poll(self) -> int:
                if not self.appended:
                    self.output_handle.write(b"123456789")
                    self.appended = True
                return 0

            def wait(self, timeout: float | None = None) -> int:
                return 0

        def fake_popen(*_args: object, **kwargs: object) -> PollAppendingProcess:
            return PollAppendingProcess(kwargs["stdout"])

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
            mock.patch.object(forge_server, "MAXIMUM_PROCESS_LOG_SIZE", 8),
            self.assertRaisesRegex(forge_server.E2EError, "during execution"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )
        self.assert_launch_attempt_is_exact()

    def test_invalid_report_never_publishes_completion(self) -> None:
        def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
            self.publish_probe_outputs(kwargs["stdout"])
            runtime = forge_server.runtime_root(self.configuration, self.state_root)
            report_path = forge_server.evidence_path(
                self.configuration, "report", runtime
            )
            report = json.loads(report_path.read_text(encoding="utf-8"))
            report["registry"]["range"] = 15
            report_path.write_text(json.dumps(report), encoding="utf-8")
            return FakeProcess()

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
            self.assertRaisesRegex(forge_server.E2EError, "registry result"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )
        self.assert_launch_attempt_is_exact()

    def test_process_spawn_failure_still_consumes_the_profile(self) -> None:
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(
                forge_server.subprocess,
                "Popen",
                side_effect=OSError("spawn failed"),
            ),
            self.assertRaisesRegex(forge_server.E2EError, "Cannot start"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        self.assert_launch_attempt_is_exact()
        self.assertFalse(
            forge_server.run_lock_path(self.configuration, self.state_root).exists()
        )

    def test_sampler_preflight_failure_spawns_nothing_and_consumes_no_attempt(
        self,
    ) -> None:
        self.native_sampler.side_effect = (
            forge_server.macos_guarded_java.MemorySamplingUnavailable(
                "libproc unavailable"
            )
        )
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen") as popen,
            self.assertRaisesRegex(forge_server.E2EError, "sampling is unavailable"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        popen.assert_not_called()
        self.cleanup_launch.assert_not_called()
        self.assertFalse(
            forge_server.run_attempt_path(
                self.configuration,
                self.state_root,
            ).exists()
        )
        self.assertFalse(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).exists()
        )

    def test_cleanup_uncertainty_retains_lock_and_bounded_process_log(self) -> None:
        process = FakeProcess(timeout=True)
        self.cleanup_launch.side_effect = forge_server.E2EError(
            "identity drift"
        )
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", return_value=process),
            mock.patch.object(forge_server, "RUN_TIMEOUT_SECONDS", 0),
            self.assertRaisesRegex(forge_server.E2EError, "lock.*retained"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        lock_path = forge_server.run_lock_path(
            self.configuration,
            self.state_root,
        )
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        retained_logs = list(runtime.glob(".forge-server-gradle.*.log"))
        self.assertTrue(lock_path.is_file())
        self.assertEqual(1, len(retained_logs))
        self.assertLessEqual(
            retained_logs[0].stat().st_size,
            forge_server.MAXIMUM_PROCESS_LOG_SIZE,
        )
        self.assert_launch_attempt_is_exact()

    def test_partial_guard_identity_is_used_for_failure_cleanup(self) -> None:
        process = FakeProcess(timeout=True)
        partial_guard = mock.Mock()
        self.start_server_guard.side_effect = forge_server.ServerGuardStartError(
            "caffeinate failed",
            partial_guard,
        )
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", return_value=process),
            self.assertRaisesRegex(
                forge_server.ServerGuardStartError,
                "caffeinate failed",
            ),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        self.cleanup_launch.assert_called_once_with(
            process,
            partial_guard,
            self.sampler,
        )
        self.assertFalse(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).exists()
        )
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertEqual([], list(runtime.glob(".forge-server-gradle.*.log")))
        self.assert_launch_attempt_is_exact()


if __name__ == "__main__":
    unittest.main()
