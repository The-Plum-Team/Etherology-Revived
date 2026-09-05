from __future__ import annotations

from contextlib import contextmanager
import base64
import copy
import json
import os
from pathlib import Path
import signal
import stat
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
import macos_memory_guard


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
            forge_server.GRADLE_TOPOLOGY_SOURCE_RELATIVE_PATH.as_posix(),
            forge_server.LAUNCH_WATCHDOG_SOURCE_RELATIVE_PATH.as_posix(),
            forge_server.LAUNCH_ANCHOR_MODULE_RELATIVE_PATH.as_posix(),
            forge_server.LAUNCH_ANCHOR_JAVA_SOURCE_RELATIVE_PATH.as_posix(),
            "scripts/e2e/forge-server-1.20.1-profile.json",
            "gradle/wrapper/gradle-wrapper.properties",
            "gradle/wrapper/gradle-wrapper.jar",
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
    report = forge_server.contract_v21._v19_baseline(valid_report())
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
            "etherology-e2e-forge-server-1.20.1-v21",
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

    def test_active_profile_matches_v21_snapshot_and_preserves_prior_versions(self) -> None:
        active = forge_server.MANIFEST_PATH.read_bytes()
        v21_snapshot_path = (
            forge_server.REPOSITORY_ROOT
            / "scripts/e2e/forge-server-1.20.1-profile-v21.json"
        )
        v21_snapshot = v21_snapshot_path.read_bytes()
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

        self.assertEqual(v21_snapshot, active)
        self.assertNotEqual(v20_snapshot, active)
        self.assertNotEqual(v19_snapshot, active)
        self.assertNotEqual(v18_snapshot, active)
        self.assertNotEqual(v17_snapshot, active)
        self.assertNotEqual(v16_snapshot, active)
        self.assertNotEqual(v15_snapshot, active)
        self.assertNotEqual(v14_snapshot, active)
        self.assertNotEqual(v13_snapshot, active)
        self.assertNotEqual(v12_snapshot, active)
        self.assertEqual(
            forge_server.contract_v21.PROFILE_MANIFEST_SIZE,
            len(v21_snapshot),
        )
        self.assertEqual(
            forge_server.contract_v21.PROFILE_MANIFEST_SHA256,
            forge_server.sha256_file(v21_snapshot_path),
        )
        self.assertEqual(1206, len(v20_snapshot))
        self.assertEqual(
            "1a38dd4e88ee8960df96bcd9d4d074adc8f967c03534bee837b013badc7771be",
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

    def test_gradle_topology_probe_source_is_pinned(self) -> None:
        configuration = forge_server.load_configuration()

        forge_server.verify_gradle_topology_source(configuration)

    def test_incomplete_gradle_topology_probe_source_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            source_path = root / forge_server.GRADLE_TOPOLOGY_SOURCE_RELATIVE_PATH
            content = source_path.read_text(encoding="utf-8")
            source_path.write_text(
                content.replace(
                    "currentProcess.parent().orElseThrow(",
                    "ProcessHandle.allProcesses()",
                ),
                encoding="utf-8",
            )
            configuration = load_temporary_configuration(root, manifest_path)

            with self.assertRaisesRegex(forge_server.E2EError, "incomplete"):
                forge_server.verify_gradle_topology_source(configuration)

    def test_live_launch_watchdog_source_bytes_are_pinned(self) -> None:
        source_path = forge_server.verify_launch_watchdog_source()

        self.assertEqual(
            forge_server.REPOSITORY_ROOT
            / forge_server.LAUNCH_WATCHDOG_SOURCE_RELATIVE_PATH,
            source_path,
        )
        self.assertEqual(
            forge_server.LAUNCH_WATCHDOG_SOURCE_SIZE,
            source_path.stat().st_size,
        )
        self.assertEqual(
            forge_server.LAUNCH_WATCHDOG_SOURCE_SHA256,
            forge_server.sha256_file(source_path),
        )

    def test_launch_watchdog_source_drift_blocks_configuration(self) -> None:
        with temporary_repository() as (root, manifest_path):
            source_path = root / forge_server.LAUNCH_WATCHDOG_SOURCE_RELATIVE_PATH
            source_path.write_bytes(source_path.read_bytes() + b"# drift\n")

            with self.assertRaisesRegex(
                forge_server.E2EError,
                "watchdog source bytes",
            ):
                load_temporary_configuration(root, manifest_path)

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


class ConsumedV20HistoryTests(unittest.TestCase):
    def test_pgid_handoff_failure_archive_is_exact_and_not_acceptance(self) -> None:
        archive = (
            forge_server.REPOSITORY_ROOT
            / "docs/evidence/forge-1.20.1/"
            "slitherite-block-registry-server-v20-pgid-handoff-failure"
        )
        records = {
            "run.attempted": (
                95,
                "a9281b174856c8ff81edb5814b8e17c8ff971091ae32375c0c3775888c8939d7",
            ),
            "profile-marker.json": (
                699,
                "676c31e4dd079034bfa6867ebd04d382c76dc2e28633139ea9f742a632dbb7b8",
            ),
            "java-memory-handoff.json": (
                305,
                "1eea04d9a04c2344a542c9b36cde8ef44a6a9dde4bc9f6bff1e8251d8334a3b3",
            ),
            "latest.log": (
                6486,
                "746400ff46a5b04151813549084217fc11405cbe1903fedfd06cee6778acf56b",
            ),
            "debug.log": (
                3812114,
                "9aef63ef16a54aef23e5fb91f69336b8dec90d0277b7a43074de72b80dc86c65",
            ),
        }
        self.assertEqual(
            {"README.md", *records},
            {entry.name for entry in archive.iterdir()},
        )
        for name, (size, digest) in records.items():
            with self.subTest(name=name):
                path = archive / name
                self.assertTrue(path.is_file())
                self.assertFalse(path.is_symlink())
                self.assertEqual(size, path.stat().st_size)
                self.assertEqual(digest, forge_server.sha256_file(path))
        readme = (archive / "README.md").read_text(encoding="utf-8")
        self.assertIn("Never launch this\nprofile again", readme)
        self.assertIn("not accepted gameplay evidence", readme)
        self.assertIn("outside the owned Gradle launch PGID", readme)
        self.assertNotEqual(
            "etherology-e2e-forge-server-1.20.1-v20",
            forge_server.PROFILE_ID,
        )


class RuntimeIsolationTests(unittest.TestCase):
    def setUp(self) -> None:
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

    def test_consumed_v18_attempt_isolated_from_fresh_v21_before_provision(
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


class RunOwnershipTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository_context = temporary_repository()
        self.repository_root, self.manifest_path = self.repository_context.__enter__()
        self.configuration = load_temporary_configuration(
            self.repository_root,
            self.manifest_path,
        )
        self.state_context = tempfile.TemporaryDirectory()
        self.state_root = Path(self.state_context.name).resolve() / ".state"
        self.state_root.mkdir(mode=0o700)

    def tearDown(self) -> None:
        self.state_context.cleanup()
        self.repository_context.__exit__(None, None, None)

    def test_run_lock_holds_exact_file_and_directory_identities(self) -> None:
        run_token = "a" * 64
        lock = forge_server.acquire_run_lock(
            self.configuration,
            self.state_root,
            run_token,
        )
        try:
            forge_server.verify_owned_run_lock(lock)
            self.assertEqual(0o600, stat.S_IMODE(os.fstat(lock.descriptor).st_mode))
            self.assertEqual(
                self.state_root.stat().st_ino,
                os.fstat(lock.directory_descriptor).st_ino,
            )
            self.assertEqual(
                (
                    f"profile_id={forge_server.PROFILE_ID}\n"
                    f"scenario={forge_server.SCENARIO_ID}\n"
                    f"pid={os.getpid()}\n"
                    f"token={run_token}\n"
                ).encode("ascii"),
                lock.content,
            )
            forge_server.release_owned_run_lock(lock)
            self.assertFalse(lock.path.exists())
        finally:
            forge_server.close_owned_run_lock(lock)

    def test_native_run_lock_path_is_repository_global_across_profiles(self) -> None:
        other_configuration = mock.Mock(spec=forge_server.ResolvedConfiguration)

        self.assertEqual(
            self.state_root / forge_server.GLOBAL_NATIVE_RUN_LOCK_NAME,
            forge_server.run_lock_path(self.configuration, self.state_root),
        )
        self.assertEqual(
            forge_server.run_lock_path(self.configuration, self.state_root),
            forge_server.run_lock_path(other_configuration, self.state_root),
        )

    def test_concurrent_owner_cannot_replace_an_existing_run_lock(self) -> None:
        lock = forge_server.acquire_run_lock(
            self.configuration,
            self.state_root,
            "b" * 64,
        )
        try:
            with self.assertRaisesRegex(forge_server.E2EError, "already owned"):
                forge_server.acquire_run_lock(
                    self.configuration,
                    self.state_root,
                    "c" * 64,
                )
            forge_server.verify_owned_run_lock(lock)
        finally:
            forge_server.release_owned_run_lock(lock)
            forge_server.close_owned_run_lock(lock)

    def test_replaced_lock_path_is_retained_and_never_unlinked(self) -> None:
        lock = forge_server.acquire_run_lock(
            self.configuration,
            self.state_root,
            "d" * 64,
        )
        original_path = self.state_root / "original-owned-lock"
        lock.path.rename(original_path)
        lock.path.write_bytes(lock.content)
        lock.path.chmod(0o600)
        try:
            with self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "changed",
            ):
                forge_server.release_owned_run_lock(lock)
            self.assertEqual(lock.content, lock.path.read_bytes())
            self.assertEqual(lock.content, original_path.read_bytes())
        finally:
            forge_server.close_owned_run_lock(lock)

    def test_mode_or_link_count_drift_blocks_lock_release(self) -> None:
        for mutation in ("mode", "hard-link"):
            with self.subTest(mutation=mutation):
                lock = forge_server.acquire_run_lock(
                    self.configuration,
                    self.state_root,
                    ("e" if mutation == "mode" else "f") * 64,
                )
                linked_path = self.state_root / f"retained-{mutation}"
                if mutation == "mode":
                    lock.path.chmod(0o640)
                else:
                    os.link(lock.path, linked_path)
                try:
                    with self.assertRaises(forge_server.CleanupUncertainError):
                        forge_server.release_owned_run_lock(lock)
                    self.assertTrue(lock.path.exists())
                finally:
                    forge_server.close_owned_run_lock(lock)
                    lock.path.unlink(missing_ok=True)
                    linked_path.unlink(missing_ok=True)

    def test_every_transient_runtime_shape_blocks_another_launch(self) -> None:
        prefixes = (
            forge_server.GRADLE_TOPOLOGY_RUNTIME_PREFIX,
            forge_server.GRADLE_WRAPPER_GUARD_RUNTIME_PREFIX,
        )
        for prefix in prefixes:
            for shape in ("directory", "file", "symlink"):
                with self.subTest(prefix=prefix, shape=shape):
                    entry = self.state_root / f"{prefix}retained"
                    if shape == "directory":
                        entry.mkdir()
                    elif shape == "file":
                        entry.write_bytes(b"retained")
                    else:
                        target = self.state_root / "symlink-target"
                        target.mkdir()
                        entry.symlink_to(target, target_is_directory=True)
                    try:
                        with self.assertRaisesRegex(
                            forge_server.E2EError,
                            "retained.*blocks",
                        ):
                            forge_server.require_no_retained_launch_runtime(
                                self.state_root
                            )
                    finally:
                        if entry.is_symlink() or entry.is_file():
                            entry.unlink()
                        else:
                            entry.rmdir()
                        target = self.state_root / "symlink-target"
                        if target.exists():
                            target.rmdir()

    def test_transient_runtime_path_swap_never_moves_the_foreign_directory(
        self,
    ) -> None:
        runtime = forge_server.create_owned_runtime_directory(
            self.state_root,
            forge_server.GRADLE_TOPOLOGY_RUNTIME_PREFIX,
            forge_server.GRADLE_TOPOLOGY_COMPLETED_RUNTIME_PREFIX,
        )
        displaced = self.state_root / "displaced-owned-runtime"
        runtime.path.rename(displaced)
        runtime.path.mkdir(mode=0o700)
        foreign_sentinel = runtime.path / "foreign.txt"
        foreign_sentinel.write_text("foreign\n", encoding="utf-8")
        try:
            with self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "identity changed",
            ):
                forge_server.retire_owned_runtime_directory(runtime)

            self.assertEqual("foreign\n", foreign_sentinel.read_text(encoding="utf-8"))
            self.assertTrue(displaced.is_dir())
            self.assertFalse((self.state_root / runtime.completed_name).exists())
        finally:
            forge_server.close_owned_runtime_directory(runtime)

    def test_owned_launch_file_path_swap_and_content_tamper_fail_closed(
        self,
    ) -> None:
        launch_path = self.state_root / "owned-launch-control"
        owned = forge_server.create_owned_launch_file(launch_path, b"original\n")
        displaced = self.state_root / "displaced-owned-launch-control"
        launch_path.rename(displaced)
        launch_path.write_bytes(b"foreign\n")
        launch_path.chmod(0o600)
        try:
            with self.assertRaises(forge_server.CleanupUncertainError):
                forge_server.unlink_owned_launch_file(owned)
            self.assertEqual(b"foreign\n", launch_path.read_bytes())
            self.assertEqual(b"original\n", displaced.read_bytes())
        finally:
            forge_server.close_owned_launch_file(owned)

        tampered_path = self.state_root / "tampered-launch-control"
        tampered = forge_server.create_owned_launch_file(
            tampered_path,
            b"expected\n",
        )
        try:
            os.pwrite(tampered.descriptor, b"X", 0)
            os.fsync(tampered.descriptor)
            with self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "changed while held",
            ):
                forge_server.verify_owned_launch_file(tampered)
            self.assertEqual(b"Xxpected\n", tampered_path.read_bytes())
        finally:
            forge_server.close_owned_launch_file(tampered)

    def test_process_output_bound_follows_the_pinned_inode(self) -> None:
        output_path = self.state_root / "owned-process.log"
        owned = forge_server.create_owned_launch_file(output_path, b"")
        displaced = self.state_root / "displaced-process.log"
        output_path.rename(displaced)
        output_path.write_bytes(b"foreign\n")
        output_path.chmod(0o600)
        try:
            with self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "output inode changed",
            ):
                forge_server.verify_process_output_bound(
                    output_path,
                    owned.descriptor,
                )
            self.assertEqual(b"foreign\n", output_path.read_bytes())
            self.assertEqual(b"", displaced.read_bytes())
        finally:
            forge_server.close_owned_launch_file(owned)

    def test_process_output_bound_uses_the_held_descriptor_size(self) -> None:
        output_path = self.state_root / "bounded-process.log"
        owned = forge_server.create_owned_launch_file(output_path, b"")
        try:
            os.write(owned.descriptor, b"123456")
            with (
                mock.patch.object(
                    forge_server,
                    "MAXIMUM_PROCESS_LOG_SIZE",
                    5,
                ),
                self.assertRaisesRegex(forge_server.E2EError, "exceeded 5 bytes"),
            ):
                forge_server.verify_process_output_bound(
                    output_path,
                    owned.descriptor,
                )
        finally:
            forge_server.close_owned_launch_file(owned)


class IsolatedGradleHomeTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_context = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_context.name).resolve()
        self.state_root = self.root / ".state"
        self.state_root.mkdir(mode=0o700)
        self.shared_home = self.root / "shared-gradle"
        self.shared_home.mkdir(mode=0o700)
        for name in forge_server.GRADLE_CACHE_BRIDGE_NAMES:
            (self.shared_home / name).mkdir(mode=0o700)

    def tearDown(self) -> None:
        self.temporary_context.cleanup()

    def provision_distribution_initialization_fixture(self) -> Path:
        initialization_directory = (
            self.shared_home
            / forge_server.GRADLE_DISTRIBUTION_RELATIVE_PATH
            / "init.d"
        )
        initialization_directory.mkdir(parents=True, mode=0o700)
        readme = initialization_directory / "readme.txt"
        readme.write_bytes(
            b"You can add .gradle (e.g. test.gradle) init scripts to this "
            b"directory. Each one is executed at the start of the build.\n"
        )
        self.assertEqual(
            forge_server.GRADLE_DISTRIBUTION_INIT_README_SIZE,
            readme.stat().st_size,
        )
        self.assertEqual(
            forge_server.GRADLE_DISTRIBUTION_INIT_README_SHA256,
            forge_server.sha256_file(readme),
        )
        return initialization_directory

    def test_only_exact_cache_bridges_are_reused_without_copying(self) -> None:
        home = forge_server.provision_gradle_user_home(
            self.state_root,
            self.shared_home,
        )

        self.assertEqual(
            set(forge_server.GRADLE_CACHE_BRIDGE_NAMES),
            {entry.name for entry in home.iterdir()},
        )
        for name in forge_server.GRADLE_CACHE_BRIDGE_NAMES:
            bridge = home / name
            self.assertTrue(bridge.is_symlink())
            self.assertEqual(self.shared_home / name, Path(os.readlink(bridge)))
        forge_server.verify_gradle_user_home(home, self.shared_home)

    def test_init_scripts_and_user_properties_are_rejected(self) -> None:
        forbidden_paths = (
            Path("init.gradle"),
            Path("init.gradle.kts"),
            Path("gradle.properties"),
            Path("gradle.properties.kts"),
            Path("init.d/injected.gradle"),
        )
        for forbidden_relative_path in forbidden_paths:
            with self.subTest(path=forbidden_relative_path):
                home = forge_server.provision_gradle_user_home(
                    self.state_root,
                    self.shared_home,
                )
                forbidden_path = home / forbidden_relative_path
                forbidden_path.parent.mkdir(parents=True, exist_ok=True)
                forbidden_path.write_text("injected", encoding="utf-8")
                with self.assertRaisesRegex(
                    forge_server.E2EError,
                    "Gradle",
                ):
                    forge_server.verify_gradle_user_home(
                        home,
                        self.shared_home,
                    )
                forbidden_path.unlink()
                if forbidden_path.parent != home:
                    forbidden_path.parent.rmdir()

    def test_cache_bridge_retargeting_is_rejected(self) -> None:
        home = forge_server.provision_gradle_user_home(
            self.state_root,
            self.shared_home,
        )
        foreign_cache = self.root / "foreign-cache"
        foreign_cache.mkdir()
        bridge = home / forge_server.GRADLE_CACHE_BRIDGE_NAMES[0]
        bridge.unlink()
        bridge.symlink_to(foreign_cache, target_is_directory=True)

        with self.assertRaisesRegex(forge_server.E2EError, "bridge changed"):
            forge_server.verify_gradle_user_home(home, self.shared_home)

    def test_group_writable_shared_cache_home_is_rejected(self) -> None:
        self.shared_home.chmod(0o720)
        try:
            with self.assertRaisesRegex(
                forge_server.E2EError,
                "not exclusively owner-controlled",
            ):
                forge_server.verify_shared_gradle_cache_home(self.shared_home)
        finally:
            self.shared_home.chmod(0o700)

    def test_distribution_init_injection_is_rejected(self) -> None:
        initialization_directory = (
            self.provision_distribution_initialization_fixture()
        )
        forge_server.verify_gradle_distribution_initialization(self.shared_home)
        injected = initialization_directory / "injected.gradle"
        injected.write_text("throw new Error('injected')\n", encoding="utf-8")

        with self.assertRaisesRegex(
            forge_server.E2EError,
            "initialization inventory changed",
        ):
            forge_server.verify_gradle_distribution_initialization(
                self.shared_home
            )


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
            with (
                mock.patch.object(
                    forge_server,
                    "require_no_java_processes",
                ) as require_no_java,
                mock.patch.object(
                    forge_server.forge_server_launch_anchor,
                    "validate_root_owned_java_executable",
                ) as validate_java,
                mock.patch.object(
                    forge_server.subprocess,
                    "run",
                    return_value=completed,
                ) as run,
            ):
                self.assertEqual(21, forge_server.java_major_version(java_path))

            require_no_java.assert_called_once_with()
            validate_java.assert_called_once_with(java_path)
            run.assert_called_once_with(
                [
                    str(java_path),
                    *forge_server.JAVA_VERSION_PROBE_JVM_ARGUMENTS,
                    "-XshowSettings:properties",
                    "-version",
                ],
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                env={
                    "LANG": "C.UTF-8",
                    "LC_ALL": "C.UTF-8",
                    "PATH": forge_server.GRADLE_EXECUTABLE_SEARCH_PATH,
                },
                text=True,
                timeout=20,
                check=False,
            )

    def test_java_version_probe_never_executes_an_unprotected_candidate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            java_path = Path(temporary_directory).resolve() / "java"
            java_path.write_bytes(b"untrusted")
            java_path.chmod(0o700)
            with (
                mock.patch.object(forge_server, "require_no_java_processes"),
                mock.patch.object(forge_server.subprocess, "run") as run,
            ):
                self.assertIsNone(forge_server.java_major_version(java_path))

            run.assert_not_called()

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

        self.assertEqual(
            [
                str(java.resolve()),
                "-Xmx2G",
                "-Xms64m",
                "-Dorg.gradle.appname=gradlew",
                "-classpath",
                str(
                    (
                        configuration.repository_root
                        / forge_server.GRADLE_WRAPPER_JAR_RELATIVE_PATH
                    ).resolve()
                ),
                "org.gradle.wrapper.GradleWrapperMain",
                "--no-daemon",
                "--no-parallel",
                "--max-workers=2",
                "--console=plain",
                "--offline",
                "-Dorg.gradle.jvmargs=-Xmx2G -Xms64m",
                "-Dorg.gradle.internal.instrumentation.agent=false",
                forge_server.TASK_PATH,
            ],
            command,
        )
        self.assertNotIn("-dimsu", command)
        self.assertNotIn(str(caffeinate), command)
        self.assertFalse(any(argument.startswith("-P") for argument in command))
        self.assertNotIn("Quick-Skin", " ".join(command))

    def test_scoped_gradle_environment_overwrites_launcher_option_drift(self) -> None:
        java = Path("/jdk21/bin/java")
        inherited = {
            "PATH": "/usr/bin",
            "JAVA_HOME": "/wrong",
            "JAVA_OPTS": "-Xmx30G",
            "GRADLE_OPTS": "-Xmx30G",
            "UNRELATED": "not inherited",
        }

        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve() / ".state"
            state_root.mkdir()
            forge_server.provision_gradle_user_home(state_root)
            environment = forge_server.gradle_launch_environment(
                java,
                inherited,
                state_root,
            )

            self.assertEqual("/jdk21", environment["JAVA_HOME"])
            self.assertEqual("", environment["JAVA_OPTS"])
            self.assertEqual("-Xmx2G", environment["GRADLE_OPTS"])
            self.assertEqual(
                forge_server.GRADLE_EXECUTABLE_SEARCH_PATH,
                environment["PATH"],
            )
            self.assertEqual(
                str(forge_server.gradle_user_home(state_root)),
                environment["GRADLE_USER_HOME"],
            )
            self.assertNotIn("UNRELATED", environment)

    def test_pinned_wrapper_drift_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            wrapper = root / "gradlew"
            wrapper.write_bytes(wrapper.read_bytes() + b"\n")
            configuration = load_temporary_configuration(root, manifest_path)
            java = root / "java"
            caffeinate = root / "caffeinate"
            java.write_bytes(b"java")
            caffeinate.write_bytes(b"caffeinate")
            java.chmod(0o700)
            caffeinate.chmod(0o700)

            with self.assertRaisesRegex(forge_server.E2EError, "launcher script"):
                forge_server.build_gradle_command(
                    configuration,
                    java,
                    caffeinate,
                )

    def test_unlisted_gradle_task_is_rejected(self) -> None:
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
                mock.patch.object(forge_server, "java_major_version", return_value=21),
                self.assertRaisesRegex(forge_server.E2EError, "not authorized"),
            ):
                forge_server.build_gradle_command(
                    configuration,
                    java,
                    caffeinate,
                    task_path=":forge:1.20.1:runClient",
                )

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

    def test_environment_probe_requires_global_lock_before_java_resolution(self) -> None:
        configuration = forge_server.load_configuration()
        with (
            mock.patch.object(forge_server, "resolve_gradle_java") as resolve_java,
            self.assertRaisesRegex(forge_server.E2EError, "global run lock"),
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
TOPOLOGY_JAVA_PROCESS_ID = 54322
TOPOLOGY_PARENT_EXECUTABLE = "/test/jdk21/bin/java"
TOPOLOGY_JAVA_EXECUTABLE = "/test/jdk17/bin/java"
WRAPPER_TARGET = forge_server.macos_guarded_java.OwnedJavaProcess(
    pid=SERVER_LAUNCH_PROCESS_ID,
    process_group_id=SERVER_LAUNCH_PROCESS_ID,
    proc_start_abstime=876543210,
    expected_executable=TOPOLOGY_PARENT_EXECUTABLE,
)


def encoded_path(value: str) -> str:
    return base64.b64encode(value.encode("utf-8")).decode("ascii")


def valid_topology_handoff() -> bytes:
    return (
        "schema=1\n"
        f"run_token={SERVER_RUN_TOKEN}\n"
        f"pid={TOPOLOGY_JAVA_PROCESS_ID}\n"
        f"parent_pid={SERVER_LAUNCH_PROCESS_ID}\n"
        f"executable_base64={encoded_path(TOPOLOGY_JAVA_EXECUTABLE)}\n"
        f"parent_executable_base64={encoded_path(TOPOLOGY_PARENT_EXECUTABLE)}\n"
        "java_feature=17\n"
        "maximum_heap_bytes=2147483648\n"
        "maximum_heap_argument=-Xmx2048m\n"
    ).encode("ascii")


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
        policy_name=forge_server.STRICT_MEMORY_POLICY_NAME,
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


def fake_wrapper_guard(
    state_root: Path,
    *,
    monitor_alive: bool = True,
) -> forge_server.WrapperJavaGuard:
    runtime_owner = forge_server.create_owned_runtime_directory(
        state_root,
        forge_server.GRADLE_WRAPPER_GUARD_RUNTIME_PREFIX,
        forge_server.GRADLE_WRAPPER_GUARD_COMPLETED_RUNTIME_PREFIX,
    )
    runtime = runtime_owner.path
    monitor_process = FakeProcess(0, timeout=monitor_alive)
    monitor_process.pid = 60003
    monitor = forge_server.macos_guarded_java.GuardedJavaMonitor(
        process=monitor_process,
        target=WRAPPER_TARGET,
        telemetry_path=runtime / "memory-guard-telemetry.json",
        readiness_path=runtime / ".memory-guard-ready.json",
        policy_name=forge_server.STRICT_MEMORY_POLICY_NAME,
    )
    return forge_server.WrapperJavaGuard(
        target=WRAPPER_TARGET,
        monitor=monitor,
        spawned_monitor_process=monitor_process,
        runtime=runtime_owner,
    )


def memory_sample(
    target: forge_server.macos_guarded_java.OwnedJavaProcess,
    current_phys_footprint_bytes: int,
) -> forge_server.macos_guarded_java.MemorySample:
    return forge_server.macos_guarded_java.MemorySample(
        observed_at_monotonic_ns=1,
        source=(
            macos_memory_guard.SampleSource.PROC_PID_RUSAGE_V4
        ),
        status=forge_server.macos_guarded_java.SampleStatus.AVAILABLE,
        observed_identity=target,
        current_phys_footprint_bytes=current_phys_footprint_bytes,
    )


class GradleTopologyHandoffTests(unittest.TestCase):
    def test_exact_loader_free_javaexec_handoff_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            handoff_path, _acknowledgement = forge_server.gradle_topology_paths(
                runtime
            )
            handoff_path.write_bytes(valid_topology_handoff())

            handoff = forge_server.validate_gradle_topology_handoff(
                handoff_path,
                runtime,
                SERVER_RUN_TOKEN,
                SERVER_LAUNCH_PROCESS_ID,
                TOPOLOGY_PARENT_EXECUTABLE,
            )

        self.assertEqual(TOPOLOGY_JAVA_PROCESS_ID, handoff.pid)
        self.assertEqual(SERVER_LAUNCH_PROCESS_ID, handoff.parent_pid)
        self.assertEqual(TOPOLOGY_JAVA_EXECUTABLE, handoff.executable)
        self.assertEqual(TOPOLOGY_PARENT_EXECUTABLE, handoff.parent_executable)
        self.assertEqual(17, handoff.java_feature)
        self.assertEqual(2_147_483_648, handoff.maximum_heap_bytes)

    def test_topology_handoff_identity_and_heap_drift_are_rejected(self) -> None:
        replacements = {
            "wrong token": (SERVER_RUN_TOKEN, "b" * 64),
            "daemon parent": (
                f"parent_pid={SERVER_LAUNCH_PROCESS_ID}",
                f"parent_pid={SERVER_LAUNCH_PROCESS_ID + 1}",
            ),
            "wrong Java": ("java_feature=17", "java_feature=21"),
            "wrong heap": (
                "maximum_heap_bytes=2147483648",
                "maximum_heap_bytes=4294967296",
            ),
            "wrong heap argument": (
                "maximum_heap_argument=-Xmx2048m",
                "maximum_heap_argument=-Xmx2G",
            ),
            "relative executable": (
                encoded_path(TOPOLOGY_JAVA_EXECUTABLE),
                encoded_path("bin/java"),
            ),
            "wrong parent executable": (
                encoded_path(TOPOLOGY_PARENT_EXECUTABLE),
                encoded_path("/test/other/bin/java"),
            ),
        }
        for description, (old, new) in replacements.items():
            with self.subTest(description=description), tempfile.TemporaryDirectory() as temporary:
                runtime = Path(temporary).resolve()
                handoff_path, _acknowledgement = (
                    forge_server.gradle_topology_paths(runtime)
                )
                handoff_path.write_bytes(valid_topology_handoff().replace(
                    old.encode("ascii"),
                    new.encode("ascii"),
                ))

                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_gradle_topology_handoff(
                        handoff_path,
                        runtime,
                        SERVER_RUN_TOKEN,
                        SERVER_LAUNCH_PROCESS_ID,
                        TOPOLOGY_PARENT_EXECUTABLE,
                    )

    def test_topology_handoff_duplicate_symlink_and_size_drift_are_rejected(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            handoff_path, _acknowledgement = forge_server.gradle_topology_paths(
                runtime
            )
            handoff_path.write_bytes(valid_topology_handoff() + b"schema=1\n")
            with self.assertRaisesRegex(forge_server.E2EError, "field inventory"):
                forge_server.validate_gradle_topology_handoff(
                    handoff_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    SERVER_LAUNCH_PROCESS_ID,
                    TOPOLOGY_PARENT_EXECUTABLE,
                )

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            runtime = root / "runtime"
            runtime.mkdir()
            foreign = root / "foreign"
            foreign.write_bytes(valid_topology_handoff())
            handoff_path, _acknowledgement = forge_server.gradle_topology_paths(
                runtime
            )
            handoff_path.symlink_to(foreign)
            with self.assertRaisesRegex(forge_server.E2EError, "symlink"):
                forge_server.validate_gradle_topology_handoff(
                    handoff_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    SERVER_LAUNCH_PROCESS_ID,
                    TOPOLOGY_PARENT_EXECUTABLE,
                )

        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            handoff_path, _acknowledgement = forge_server.gradle_topology_paths(
                runtime
            )
            handoff_path.write_bytes(
                b"x" * (forge_server.GRADLE_TOPOLOGY_MAXIMUM_HANDOFF_SIZE + 1)
            )
            with self.assertRaisesRegex(forge_server.E2EError, "invalid size"):
                forge_server.validate_gradle_topology_handoff(
                    handoff_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    SERVER_LAUNCH_PROCESS_ID,
                    TOPOLOGY_PARENT_EXECUTABLE,
                )


class WrapperJavaGuardStartTests(unittest.TestCase):
    def test_exact_wrapper_gets_a_strict_monitor_before_return(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            process = FakeProcess(timeout=True)
            sampler = mock.Mock()
            events: list[str] = []

            def bind_wrapper(*_arguments: object) -> object:
                events.append("bind")
                return WRAPPER_TARGET

            sampler.bind.side_effect = bind_wrapper
            launch_watchdog = mock.sentinel.launch_watchdog
            monitor_process = FakeProcess(timeout=True)
            monitor_process.pid = 60003

            def start_monitor(
                target: object,
                _maximum_memory_mb: object,
                runtime: Path,
                _output_handle: object,
                **kwargs: object,
            ) -> forge_server.macos_guarded_java.GuardedJavaMonitor:
                events.append("monitor")
                process_started = kwargs["process_started"]
                process_started(monitor_process)
                return forge_server.macos_guarded_java.GuardedJavaMonitor(
                    process=monitor_process,
                    target=target,
                    telemetry_path=runtime / "memory-guard-telemetry.json",
                    readiness_path=runtime / ".memory-guard-ready.json",
                    policy_name=kwargs["policy_name"],
                )

            with (
                mock.patch.object(
                    forge_server.os,
                    "getpgid",
                    return_value=SERVER_LAUNCH_PROCESS_ID,
                ),
                mock.patch.object(
                    forge_server.os,
                    "getsid",
                    return_value=SERVER_LAUNCH_PROCESS_ID,
                ),
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "start_guarded_java_monitor",
                    side_effect=start_monitor,
                ) as start_guarded_monitor,
                mock.patch.object(
                    forge_server.forge_server_launch_watchdog,
                    "start_launch_watchdog",
                    side_effect=lambda *_args, **_kwargs: (
                        events.append("watchdog") or launch_watchdog
                    ),
                ) as start_watchdog,
                mock.patch.object(
                    forge_server,
                    "verify_launch_watchdog_source",
                    side_effect=lambda: events.append("watchdog source"),
                ) as verify_watchdog_source,
                mock.patch.object(
                    forge_server,
                    "verify_launch_watchdog_runtime_identity",
                ) as verify_watchdog_runtime,
                mock.patch.object(
                    forge_server,
                    "verify_wrapper_launch_supervision",
                    side_effect=lambda *_args, **_kwargs: events.append(
                        "supervision"
                    ),
                ) as verify_supervision,
                mock.patch.object(
                    forge_server,
                    "verify_exact_java_memory_envelope",
                    side_effect=lambda *_args, **_kwargs: events.append(
                        "envelope"
                    ),
                ) as verify_envelope,
                mock.patch.object(
                    forge_server,
                    "verify_java_process_inventory",
                    side_effect=lambda *_args, **_kwargs: events.append(
                        "inventory"
                    ),
                ) as verify_inventory,
            ):
                guard = forge_server.start_wrapper_java_guard(
                    process,
                    Path(TOPOLOGY_PARENT_EXECUTABLE),
                    sampler,
                    state_root,
                    time.monotonic() + 1.0,
                    mock.Mock(),
                )
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                guard.runtime,
            )

            sampler.bind.assert_called_once_with(
                SERVER_LAUNCH_PROCESS_ID,
                SERVER_LAUNCH_PROCESS_ID,
                TOPOLOGY_PARENT_EXECUTABLE,
            )
            self.assertEqual(WRAPPER_TARGET, guard.target)
            self.assertIs(launch_watchdog, guard.launch_watchdog)
            self.assertIs(monitor_process, guard.spawned_monitor_process)
            self.assertEqual(state_root, guard.runtime_directory.parent)
            self.assertTrue(
                guard.runtime_directory.name.startswith(
                    forge_server.GRADLE_WRAPPER_GUARD_RUNTIME_PREFIX
                )
            )
            self.assertEqual(0o700, stat.S_IMODE(guard.runtime_directory.stat().st_mode))
            self.assertEqual(
                forge_server.STRICT_MEMORY_POLICY_NAME,
                start_guarded_monitor.call_args.kwargs["policy_name"],
            )
            start_watchdog.assert_called_once_with(
                WRAPPER_TARGET,
                SERVER_LAUNCH_PROCESS_ID,
                guard.runtime_directory,
                heartbeat_timeout_seconds=(
                    forge_server.LAUNCH_WATCHDOG_HEARTBEAT_TIMEOUT_SECONDS
                ),
            )
            verify_watchdog_source.assert_called_once_with()
            verify_watchdog_runtime.assert_called_once_with(
                launch_watchdog,
                guard.runtime.descriptor,
            )
            verify_supervision.assert_called_once_with(
                guard,
                sampler,
                "Gradle wrapper JVM",
            )
            self.assertEqual(3, verify_inventory.call_count)
            self.assertEqual(
                [
                    "inventory",
                    "bind",
                    "watchdog source",
                    "watchdog",
                    "envelope",
                    "inventory",
                    "monitor",
                    "supervision",
                    "envelope",
                    "inventory",
                ],
                events,
            )
            self.assertEqual(
                [
                    mock.call(sampler, (WRAPPER_TARGET,)),
                    mock.call(sampler, (WRAPPER_TARGET,)),
                ],
                verify_envelope.call_args_list,
            )

    def test_monitor_spawn_failure_retains_the_partial_exact_guard(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            process = FakeProcess(timeout=True)
            sampler = mock.Mock()
            sampler.bind.return_value = WRAPPER_TARGET
            launch_watchdog = mock.sentinel.launch_watchdog
            spawned_monitor = FakeProcess(timeout=True)
            spawned_monitor.pid = 60003

            def fail_monitor(*_args: object, **kwargs: object) -> object:
                kwargs["process_started"](spawned_monitor)
                raise forge_server.macos_guarded_java.GuardedJavaError(
                    "readiness failed"
                )

            with (
                mock.patch.object(
                    forge_server.os,
                    "getpgid",
                    return_value=SERVER_LAUNCH_PROCESS_ID,
                ),
                mock.patch.object(
                    forge_server.os,
                    "getsid",
                    return_value=SERVER_LAUNCH_PROCESS_ID,
                ),
                mock.patch.object(
                    forge_server.forge_server_launch_watchdog,
                    "start_launch_watchdog",
                    return_value=launch_watchdog,
                ),
                mock.patch.object(
                    forge_server,
                    "verify_launch_watchdog_runtime_identity",
                ),
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "start_guarded_java_monitor",
                    side_effect=fail_monitor,
                ),
                mock.patch.object(
                    forge_server,
                    "verify_exact_java_memory_envelope",
                ),
                mock.patch.object(
                    forge_server,
                    "verify_java_process_inventory",
                ),
                self.assertRaises(forge_server.WrapperGuardStartError) as raised,
            ):
                forge_server.start_wrapper_java_guard(
                    process,
                    Path(TOPOLOGY_PARENT_EXECUTABLE),
                    sampler,
                    state_root,
                    time.monotonic() + 1.0,
                    mock.Mock(),
                )

            partial = raised.exception.wrapper_guard
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                partial.runtime,
            )
            self.assertEqual(WRAPPER_TARGET, partial.target)
            self.assertIs(launch_watchdog, partial.launch_watchdog)
            self.assertIsNone(partial.monitor)
            self.assertIs(spawned_monitor, partial.spawned_monitor_process)
            self.assertTrue(partial.runtime_directory.is_dir())

    def test_watchdog_readiness_failure_retains_its_spawned_handle(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            process = FakeProcess(timeout=True)
            sampler = mock.Mock()
            sampler.bind.return_value = WRAPPER_TARGET
            launch_watchdog = mock.sentinel.partial_launch_watchdog
            start_error = (
                forge_server.forge_server_launch_watchdog.LaunchWatchdogStartError(
                    "watchdog readiness failed",
                    launch_watchdog,
                )
            )

            with (
                mock.patch.object(
                    forge_server.os,
                    "getpgid",
                    return_value=SERVER_LAUNCH_PROCESS_ID,
                ),
                mock.patch.object(
                    forge_server.os,
                    "getsid",
                    return_value=SERVER_LAUNCH_PROCESS_ID,
                ),
                mock.patch.object(
                    forge_server,
                    "verify_java_process_inventory",
                ),
                mock.patch.object(
                    forge_server.forge_server_launch_watchdog,
                    "start_launch_watchdog",
                    side_effect=start_error,
                ),
                mock.patch.object(
                    forge_server,
                    "verify_exact_java_memory_envelope",
                ) as verify_envelope,
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "start_guarded_java_monitor",
                ) as start_monitor,
                self.assertRaises(forge_server.WrapperGuardStartError) as raised,
            ):
                forge_server.start_wrapper_java_guard(
                    process,
                    Path(TOPOLOGY_PARENT_EXECUTABLE),
                    sampler,
                    state_root,
                    time.monotonic() + 1.0,
                    mock.Mock(),
                )

            partial = raised.exception.wrapper_guard
            try:
                self.assertEqual(WRAPPER_TARGET, partial.target)
                self.assertIs(launch_watchdog, partial.launch_watchdog)
                self.assertIsNone(partial.monitor)
                self.assertIsNone(partial.spawned_monitor_process)
                verify_envelope.assert_not_called()
                start_monitor.assert_not_called()
            finally:
                forge_server.close_owned_runtime_directory(partial.runtime)

    def test_every_post_bind_validation_failure_carries_the_exact_wrapper(self) -> None:
        for failing_check in ("memory envelope", "Java inventory"):
            with self.subTest(failing_check=failing_check), tempfile.TemporaryDirectory() as temporary_directory:
                state_root = Path(temporary_directory).resolve()
                process = FakeProcess(timeout=True)
                sampler = mock.Mock()
                sampler.bind.return_value = WRAPPER_TARGET
                launch_watchdog = mock.sentinel.launch_watchdog
                inventory_side_effect: object = None
                envelope_side_effect: object = None
                if failing_check == "memory envelope":
                    envelope_side_effect = forge_server.E2EError("envelope failed")
                else:
                    inventory_side_effect = [None, forge_server.E2EError("inventory failed")]
                with (
                    mock.patch.object(
                        forge_server.os,
                        "getpgid",
                        return_value=SERVER_LAUNCH_PROCESS_ID,
                    ),
                    mock.patch.object(
                        forge_server.os,
                        "getsid",
                        return_value=SERVER_LAUNCH_PROCESS_ID,
                    ),
                    mock.patch.object(
                        forge_server.forge_server_launch_watchdog,
                        "start_launch_watchdog",
                        return_value=launch_watchdog,
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_launch_watchdog_runtime_identity",
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_exact_java_memory_envelope",
                        side_effect=envelope_side_effect,
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_java_process_inventory",
                        side_effect=inventory_side_effect,
                    ),
                    mock.patch.object(
                        forge_server.macos_guarded_java,
                        "start_guarded_java_monitor",
                    ) as start_monitor,
                    self.assertRaises(
                        forge_server.WrapperGuardStartError
                    ) as raised,
                ):
                    forge_server.start_wrapper_java_guard(
                        process,
                        Path(TOPOLOGY_PARENT_EXECUTABLE),
                        sampler,
                        state_root,
                        time.monotonic() + 1.0,
                        mock.Mock(),
                    )
                partial = raised.exception.wrapper_guard
                try:
                    self.assertEqual(WRAPPER_TARGET, partial.target)
                    self.assertIs(launch_watchdog, partial.launch_watchdog)
                    self.assertIsNone(partial.monitor)
                    self.assertTrue(partial.runtime_directory.is_dir())
                    start_monitor.assert_not_called()
                finally:
                    forge_server.close_owned_runtime_directory(partial.runtime)

    def test_every_post_monitor_failure_preserves_the_complete_monitor(self) -> None:
        for failing_check in ("supervision", "memory envelope", "Java inventory"):
            with self.subTest(failing_check=failing_check), tempfile.TemporaryDirectory() as temporary_directory:
                state_root = Path(temporary_directory).resolve()
                process = FakeProcess(timeout=True)
                sampler = mock.Mock()
                sampler.bind.return_value = WRAPPER_TARGET
                launch_watchdog = mock.sentinel.launch_watchdog
                monitor_process = FakeProcess(timeout=True)
                monitor_process.pid = 60003
                monitor = forge_server.macos_guarded_java.GuardedJavaMonitor(
                    process=monitor_process,
                    target=WRAPPER_TARGET,
                    telemetry_path=state_root / "telemetry.json",
                    readiness_path=state_root / "readiness.json",
                    policy_name=forge_server.STRICT_MEMORY_POLICY_NAME,
                )

                def start_monitor(
                    *_arguments: object,
                    **keyword_arguments: object,
                ) -> object:
                    keyword_arguments["process_started"](monitor_process)
                    return monitor

                supervision_failure: object = None
                envelope_failure: object = None
                inventory_failure: object = None
                if failing_check == "supervision":
                    supervision_failure = forge_server.E2EError(
                        "supervision failed"
                    )
                elif failing_check == "memory envelope":
                    envelope_failure = [
                        None,
                        forge_server.E2EError("envelope failed"),
                    ]
                else:
                    inventory_failure = [
                        None,
                        None,
                        forge_server.E2EError("inventory failed"),
                    ]

                with (
                    mock.patch.object(
                        forge_server.os,
                        "getpgid",
                        return_value=SERVER_LAUNCH_PROCESS_ID,
                    ),
                    mock.patch.object(
                        forge_server.os,
                        "getsid",
                        return_value=SERVER_LAUNCH_PROCESS_ID,
                    ),
                    mock.patch.object(
                        forge_server.forge_server_launch_watchdog,
                        "start_launch_watchdog",
                        return_value=launch_watchdog,
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_launch_watchdog_runtime_identity",
                    ),
                    mock.patch.object(
                        forge_server.macos_guarded_java,
                        "start_guarded_java_monitor",
                        side_effect=start_monitor,
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_wrapper_launch_supervision",
                        side_effect=supervision_failure,
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_exact_java_memory_envelope",
                        side_effect=envelope_failure,
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_java_process_inventory",
                        side_effect=inventory_failure,
                    ),
                    self.assertRaises(
                        forge_server.WrapperGuardStartError
                    ) as raised,
                ):
                    forge_server.start_wrapper_java_guard(
                        process,
                        Path(TOPOLOGY_PARENT_EXECUTABLE),
                        sampler,
                        state_root,
                        time.monotonic() + 1.0,
                        mock.Mock(),
                    )

                partial = raised.exception.wrapper_guard
                try:
                    self.assertIs(monitor, partial.monitor)
                    self.assertIs(
                        monitor_process,
                        partial.spawned_monitor_process,
                    )
                    self.assertIs(launch_watchdog, partial.launch_watchdog)
                finally:
                    forge_server.close_owned_runtime_directory(partial.runtime)

    def test_runtime_creation_fails_before_any_wrapper_identity_is_bound(self) -> None:
        process = FakeProcess(timeout=True)
        sampler = mock.Mock()
        with (
            mock.patch.object(
                forge_server,
                "create_owned_runtime_directory",
                side_effect=forge_server.E2EError("runtime unavailable"),
            ),
            self.assertRaisesRegex(forge_server.E2EError, "runtime unavailable"),
        ):
            forge_server.start_wrapper_java_guard(
                process,
                Path(TOPOLOGY_PARENT_EXECUTABLE),
                sampler,
                Path("/unused"),
                time.monotonic() + 1.0,
                mock.Mock(),
            )
        sampler.bind.assert_not_called()


class WrapperJavaGuardCleanupTests(unittest.TestCase):
    def test_terminal_watchdog_proof_precedes_auxiliary_and_runtime_cleanup(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            initial_guard = fake_wrapper_guard(state_root)
            launch_watchdog = mock.Mock()
            guard = forge_server.WrapperJavaGuard(
                target=initial_guard.target,
                monitor=initial_guard.monitor,
                spawned_monitor_process=initial_guard.spawned_monitor_process,
                runtime=initial_guard.runtime,
                launch_watchdog=launch_watchdog,
            )
            process = FakeProcess(timeout=False)
            sampler = mock.Mock()
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.MISSING
            )
            events: list[str] = []
            pinned_evidence = mock.sentinel.pinned_watchdog_evidence
            launch_watchdog.close_runtime_directory.side_effect = (
                lambda: events.append("close watchdog runtime")
            )
            real_retire = forge_server.retire_owned_runtime_directory

            def retire(runtime: object) -> None:
                events.append("retire wrapper runtime")
                real_retire(runtime)

            try:
                with (
                    mock.patch.object(
                        forge_server,
                        "process_group_exists",
                        return_value=False,
                    ),
                    mock.patch.object(
                        forge_server.forge_server_launch_watchdog,
                        "finish_launch_watchdog",
                        side_effect=lambda *_args, **_kwargs: events.append(
                            "finish watchdog"
                        ),
                    ) as finish_watchdog,
                    mock.patch.object(
                        forge_server,
                        "pin_terminal_watchdog_evidence",
                        side_effect=lambda *_args, **_kwargs: (
                            events.append("pin watchdog evidence")
                            or pinned_evidence
                        ),
                    ),
                    mock.patch.object(
                        forge_server,
                        "stop_java_guard_auxiliary",
                        side_effect=lambda *_args, **_kwargs: events.append(
                            "stop memory monitor"
                        ),
                    ),
                    mock.patch.object(
                        forge_server,
                        "retire_owned_runtime_directory",
                        side_effect=retire,
                    ),
                    mock.patch.object(
                        forge_server,
                        "close_pinned_watchdog_evidence",
                        side_effect=lambda *_args, **_kwargs: events.append(
                            "close pinned evidence"
                        ),
                    ),
                ):
                    forge_server.cleanup_wrapper_java_guard(
                        process,
                        guard,
                        sampler,
                        state_root,
                        require_normal_watchdog_exit=True,
                    )

                finish_watchdog.assert_called_once_with(
                    launch_watchdog,
                    require_normal_exit=True,
                )
                self.assertEqual(
                    [
                        "stop memory monitor",
                        "finish watchdog",
                        "pin watchdog evidence",
                        "close watchdog runtime",
                        "retire wrapper runtime",
                        "close pinned evidence",
                    ],
                    events,
                )
            finally:
                forge_server.close_owned_runtime_directory(guard.runtime)

    def test_terminal_watchdog_uncertainty_preserves_other_guard_state(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            initial_guard = fake_wrapper_guard(state_root)
            launch_watchdog = mock.Mock()
            guard = forge_server.WrapperJavaGuard(
                target=initial_guard.target,
                monitor=initial_guard.monitor,
                spawned_monitor_process=initial_guard.spawned_monitor_process,
                runtime=initial_guard.runtime,
                launch_watchdog=launch_watchdog,
            )
            process = FakeProcess(timeout=False)
            sampler = mock.Mock()
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.MISSING
            )

            try:
                with (
                    mock.patch.object(
                        forge_server,
                        "process_group_exists",
                        return_value=False,
                    ),
                    mock.patch.object(
                        forge_server.forge_server_launch_watchdog,
                        "finish_launch_watchdog",
                        side_effect=(
                            forge_server.forge_server_launch_watchdog.LaunchWatchdogError(
                                "terminal proof unavailable"
                            )
                        ),
                    ),
                    mock.patch.object(
                        forge_server,
                        "stop_java_guard_auxiliary",
                    ) as stop_monitor,
                    mock.patch.object(
                        forge_server,
                        "retire_owned_runtime_directory",
                    ) as retire_runtime,
                    self.assertRaisesRegex(
                        forge_server.CleanupUncertainError,
                        "terminal proof unavailable",
                    ),
                ):
                    forge_server.cleanup_wrapper_java_guard(
                        process,
                        guard,
                        sampler,
                        state_root,
                        require_normal_watchdog_exit=False,
                    )

                launch_watchdog.close_runtime_directory.assert_not_called()
                stop_monitor.assert_called_once()
                retire_runtime.assert_not_called()
                self.assertTrue(guard.runtime_directory.is_dir())
            finally:
                forge_server.close_owned_runtime_directory(guard.runtime)


class WrapperLaunchSupervisionTests(unittest.TestCase):
    def test_missing_wrapper_still_requires_a_valid_watchdog_transition(
        self,
    ) -> None:
        guard = mock.Mock(
            target=WRAPPER_TARGET,
            monitor=mock.sentinel.monitor,
            launch_watchdog=mock.Mock(),
            launch_anchor=None,
            anchor_target=None,
        )
        sampler = mock.Mock()
        guard.launch_watchdog.process.poll.return_value = None
        transition = {"status": "terminating"}
        with (
            mock.patch.object(
                forge_server.forge_server_launch_watchdog,
                "send_launch_watchdog_heartbeat",
            ) as heartbeat,
            mock.patch.object(
                forge_server.forge_server_launch_watchdog,
                "verify_launch_watchdog_transition",
                return_value=transition,
            ) as verify_transition,
            mock.patch.object(
                forge_server,
                "verify_java_guard_is_enforcing",
            ) as verify_memory_guard,
        ):
            forge_server.verify_wrapper_launch_supervision(
                guard,
                sampler,
                "Gradle wrapper JVM",
                allow_missing=True,
            )

        heartbeat.assert_called_once_with(guard.launch_watchdog)
        verify_transition.assert_called_once_with(guard.launch_watchdog)
        verify_memory_guard.assert_called_once_with(
            WRAPPER_TARGET,
            guard.monitor,
            sampler,
            "Gradle wrapper JVM",
            allow_missing=True,
        )

    def test_failed_watchdog_transition_is_not_hidden_by_wrapper_exit(self) -> None:
        guard = mock.Mock(
            target=WRAPPER_TARGET,
            monitor=mock.sentinel.monitor,
            launch_watchdog=mock.Mock(),
            launch_anchor=None,
            anchor_target=None,
        )
        sampler = mock.Mock()
        guard.launch_watchdog.process.poll.return_value = 1
        with (
            mock.patch.object(
                forge_server.forge_server_launch_watchdog,
                "send_launch_watchdog_heartbeat",
            ) as heartbeat,
            mock.patch.object(
                forge_server.forge_server_launch_watchdog,
                "verify_launch_watchdog_transition",
                side_effect=(
                    forge_server.forge_server_launch_watchdog.LaunchWatchdogError(
                        "failed terminal state"
                    )
                ),
            ),
            mock.patch.object(
                forge_server,
                "verify_java_guard_is_enforcing",
            ) as verify_memory_guard,
            self.assertRaisesRegex(
                forge_server.E2EError,
                "failed terminal state",
            ),
        ):
            forge_server.verify_wrapper_launch_supervision(
                guard,
                sampler,
                "Gradle wrapper JVM",
                allow_missing=True,
            )

        heartbeat.assert_not_called()
        verify_memory_guard.assert_not_called()

    def test_heartbeat_failure_requires_nonrunning_transition_state(self) -> None:
        for status, accepted in (("running", False), ("normal", True)):
            with self.subTest(status=status):
                guard = mock.Mock(
                    target=WRAPPER_TARGET,
                    monitor=mock.sentinel.monitor,
                    launch_watchdog=mock.Mock(),
                    launch_anchor=None,
                    anchor_target=None,
                )
                sampler = mock.Mock()
                guard.launch_watchdog.process.poll.return_value = None
                with (
                    mock.patch.object(
                        forge_server.forge_server_launch_watchdog,
                        "send_launch_watchdog_heartbeat",
                        side_effect=(
                            forge_server.forge_server_launch_watchdog.LaunchWatchdogError(
                                "broken heartbeat"
                            )
                        ),
                    ),
                    mock.patch.object(
                        forge_server.forge_server_launch_watchdog,
                        "verify_launch_watchdog_transition",
                        return_value={"status": status},
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_java_guard_is_enforcing",
                    ) as verify_memory_guard,
                ):
                    if accepted:
                        forge_server.verify_wrapper_launch_supervision(
                            guard,
                            sampler,
                            "Gradle wrapper JVM",
                            allow_missing=True,
                        )
                    else:
                        with self.assertRaisesRegex(
                            forge_server.E2EError,
                            "broken heartbeat",
                        ):
                            forge_server.verify_wrapper_launch_supervision(
                                guard,
                                sampler,
                                "Gradle wrapper JVM",
                                allow_missing=True,
                            )

                if accepted:
                    verify_memory_guard.assert_called_once()
                else:
                    verify_memory_guard.assert_not_called()


class PinnedWatchdogEvidenceTests(unittest.TestCase):
    def test_watchdog_runtime_path_replacement_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            runtime = root / "runtime"
            runtime.mkdir(mode=0o700)
            expected_descriptor = os.open(
                runtime,
                os.O_RDONLY | getattr(os, "O_DIRECTORY", 0),
            )
            watchdog_descriptor = os.dup(expected_descriptor)
            handle = mock.Mock(
                runtime_directory_descriptor=watchdog_descriptor,
                readiness_path=(
                    runtime
                    / forge_server.forge_server_launch_watchdog.READINESS_FILE_NAME
                ),
            )
            try:
                forge_server.verify_launch_watchdog_runtime_identity(
                    handle,
                    expected_descriptor,
                )
                runtime.rename(root / "displaced-runtime")
                runtime.mkdir(mode=0o700)

                with self.assertRaisesRegex(
                    forge_server.CleanupUncertainError,
                    "did not pin",
                ):
                    forge_server.verify_launch_watchdog_runtime_identity(
                        handle,
                        expected_descriptor,
                    )
            finally:
                os.close(watchdog_descriptor)
                os.close(expected_descriptor)

    def test_terminal_artifact_replacement_invalidates_pinned_provenance(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            runtime.chmod(0o700)
            readiness = (
                runtime
                / forge_server.forge_server_launch_watchdog.READINESS_FILE_NAME
            )
            telemetry = (
                runtime
                / forge_server.forge_server_launch_watchdog.TELEMETRY_FILE_NAME
            )
            for path, content in (
                (readiness, b'{"status":"ready"}\n'),
                (telemetry, b'{"status":"normal"}\n'),
            ):
                path.write_bytes(content)
                path.chmod(0o600)
            expected_descriptor = os.open(
                runtime,
                os.O_RDONLY | getattr(os, "O_DIRECTORY", 0),
            )
            watchdog_descriptor = os.dup(expected_descriptor)
            handle = mock.Mock(
                runtime_directory_descriptor=watchdog_descriptor,
                readiness_path=readiness,
                telemetry_path=telemetry,
                verified_terminal_artifact_contents={
                    readiness.name: readiness.read_bytes(),
                    telemetry.name: telemetry.read_bytes(),
                },
            )
            pinned = None
            try:
                pinned = forge_server.pin_terminal_watchdog_evidence(
                    handle,
                    expected_descriptor,
                )
                records = forge_server.verify_pinned_watchdog_evidence(
                    pinned,
                    expected_descriptor,
                )
                self.assertEqual(
                    {
                        forge_server.forge_server_launch_watchdog.READINESS_FILE_NAME,
                        forge_server.forge_server_launch_watchdog.TELEMETRY_FILE_NAME,
                    },
                    set(records),
                )
                replacement = runtime / "replacement.tmp"
                replacement.write_bytes(telemetry.read_bytes())
                replacement.chmod(0o600)
                os.replace(replacement, telemetry)

                with self.assertRaisesRegex(
                    forge_server.CleanupUncertainError,
                    "artifact changed",
                ):
                    forge_server.verify_pinned_watchdog_evidence(
                        pinned,
                        expected_descriptor,
                    )
            finally:
                if pinned is not None:
                    forge_server.close_pinned_watchdog_evidence(pinned)
                os.close(watchdog_descriptor)
                os.close(expected_descriptor)

    def test_replacement_after_semantic_verification_is_rejected_before_pin(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            runtime.chmod(0o700)
            readiness = (
                runtime
                / forge_server.forge_server_launch_watchdog.READINESS_FILE_NAME
            )
            telemetry = (
                runtime
                / forge_server.forge_server_launch_watchdog.TELEMETRY_FILE_NAME
            )
            readiness_content = b'{"status":"ready"}\n'
            telemetry_content = b'{"status":"normal"}\n'
            readiness.write_bytes(readiness_content)
            telemetry.write_bytes(telemetry_content)
            readiness.chmod(0o600)
            telemetry.chmod(0o600)
            expected_descriptor = os.open(
                runtime,
                os.O_RDONLY | getattr(os, "O_DIRECTORY", 0),
            )
            watchdog_descriptor = os.dup(expected_descriptor)
            handle = mock.Mock(
                runtime_directory_descriptor=watchdog_descriptor,
                readiness_path=readiness,
                telemetry_path=telemetry,
                verified_terminal_artifact_contents={
                    readiness.name: readiness_content,
                    telemetry.name: telemetry_content,
                },
            )
            replacement = runtime / "replacement.tmp"
            replacement.write_bytes(b'{"status": "normal"}\n')
            replacement.chmod(0o600)
            os.replace(replacement, telemetry)
            try:
                with self.assertRaisesRegex(
                    forge_server.CleanupUncertainError,
                    "identity is unsafe",
                ):
                    forge_server.pin_terminal_watchdog_evidence(
                        handle,
                        expected_descriptor,
                    )
            finally:
                os.close(watchdog_descriptor)
                os.close(expected_descriptor)


class JavaMemoryEnvelopeTests(unittest.TestCase):
    @staticmethod
    def target(pid: int) -> forge_server.macos_guarded_java.OwnedJavaProcess:
        return forge_server.macos_guarded_java.OwnedJavaProcess(
            pid=pid,
            process_group_id=SERVER_LAUNCH_PROCESS_ID,
            proc_start_abstime=pid * 100,
            expected_executable=f"/test/jdk/bin/java-{pid}",
        )

    @staticmethod
    def sampler_for(
        samples: dict[
            forge_server.macos_guarded_java.OwnedJavaProcess,
            forge_server.macos_guarded_java.MemorySample,
        ],
    ) -> mock.Mock:
        sampler = mock.Mock()
        sampler.sample.side_effect = lambda target, _observed_at: samples[target]
        return sampler

    def test_exact_six_gib_aggregate_is_allowed_and_one_byte_more_is_rejected(
        self,
    ) -> None:
        first = self.target(70001)
        second = self.target(70002)
        gibibyte = 1024 * 1024 * 1024
        allowed_sampler = self.sampler_for(
            {
                first: memory_sample(first, 3 * gibibyte),
                second: memory_sample(second, 3 * gibibyte),
            }
        )

        self.assertEqual(
            6 * gibibyte,
            forge_server.verify_exact_java_memory_envelope(
                allowed_sampler,
                (first, second),
            ),
        )

        rejected_sampler = self.sampler_for(
            {
                first: memory_sample(first, 3 * gibibyte),
                second: memory_sample(second, 3 * gibibyte + 1),
            }
        )
        with self.assertRaisesRegex(forge_server.E2EError, "six-GiB"):
            forge_server.verify_exact_java_memory_envelope(
                rejected_sampler,
                (first, second),
            )

    def test_five_gib_individual_boundary_is_exact(self) -> None:
        target = self.target(70001)
        gibibyte = 1024 * 1024 * 1024
        allowed_sampler = self.sampler_for(
            {target: memory_sample(target, 5 * gibibyte)}
        )
        self.assertEqual(
            5 * gibibyte,
            forge_server.verify_exact_java_memory_envelope(
                allowed_sampler,
                (target,),
            ),
        )

        rejected_sampler = self.sampler_for(
            {target: memory_sample(target, 5 * gibibyte + 1)}
        )
        with self.assertRaisesRegex(forge_server.E2EError, "five-GiB"):
            forge_server.verify_exact_java_memory_envelope(
                rejected_sampler,
                (target,),
            )

    def test_missing_required_fails_but_missing_optional_is_ignored(self) -> None:
        required = self.target(70001)
        optional = self.target(70002)
        missing = forge_server.macos_guarded_java.MemorySample.missing(
            1,
            macos_memory_guard.SampleSource.PROC_PID_RUSAGE_V4,
            "gone",
        )
        sampler = self.sampler_for(
            {
                required: memory_sample(required, 1),
                optional: missing,
            }
        )
        self.assertEqual(
            1,
            forge_server.verify_exact_java_memory_envelope(
                sampler,
                (required,),
                (optional,),
            ),
        )

        required_missing_sampler = self.sampler_for({required: missing})
        with self.assertRaisesRegex(forge_server.E2EError, "missing"):
            forge_server.verify_exact_java_memory_envelope(
                required_missing_sampler,
                (required,),
            )

    def test_source_observed_identity_and_identity_drift_fail_closed(self) -> None:
        target = self.target(70001)
        other = self.target(70002)
        invalid_samples = {
            "fallback source": forge_server.macos_guarded_java.MemorySample.fallback(
                1,
                target,
                resident_size_bytes=1,
            ),
            "foreign observed identity": memory_sample(other, 1),
            "identity drift": (
                forge_server.macos_guarded_java.MemorySample.identity_drift(
                    1,
                    "reused pid",
                )
            ),
        }
        for description, sample in invalid_samples.items():
            with self.subTest(description=description):
                sampler = self.sampler_for({target: sample})
                with self.assertRaisesRegex(
                    forge_server.E2EError,
                    "authoritative current physical-memory sample",
                ):
                    forge_server.verify_exact_java_memory_envelope(
                        sampler,
                        (target,),
                    )

    def test_deduplication_conflicting_identity_and_maximum_count(self) -> None:
        target = self.target(70001)
        sampler = self.sampler_for({target: memory_sample(target, 17)})
        self.assertEqual(
            17,
            forge_server.verify_exact_java_memory_envelope(
                sampler,
                (target, target),
                (target,),
            ),
        )
        sampler.sample.assert_called_once()

        conflicting = forge_server.macos_guarded_java.OwnedJavaProcess(
            pid=target.pid,
            process_group_id=target.process_group_id,
            proc_start_abstime=target.proc_start_abstime + 1,
            expected_executable=target.expected_executable,
        )
        with self.assertRaisesRegex(forge_server.E2EError, "conflicting identities"):
            forge_server.verify_exact_java_memory_envelope(
                mock.Mock(),
                (target, conflicting),
            )

        too_many = tuple(self.target(70001 + index) for index in range(4))
        bounded_sampler = mock.Mock()
        with self.assertRaisesRegex(forge_server.E2EError, "identity bound"):
            forge_server.verify_exact_java_memory_envelope(
                bounded_sampler,
                too_many,
            )
        bounded_sampler.sample.assert_not_called()


class JavaProcessInventoryTests(unittest.TestCase):
    def test_pgrep_inventory_is_ascii_bounded_unique_and_sorted(self) -> None:
        valid_results = (
            (subprocess.CompletedProcess([], 0, b"54321\n7\n", b""), (7, 54321)),
            (subprocess.CompletedProcess([], 1, b"", b""), ()),
        )
        for completed, expected in valid_results:
            with self.subTest(expected=expected), mock.patch.object(
                forge_server.subprocess,
                "run",
                return_value=completed,
            ) as run:
                self.assertEqual(expected, forge_server.read_java_process_inventory())
                self.assertEqual(
                    [str(forge_server.PGREP_PATH), "-x", "java"],
                    run.call_args.args[0],
                )

        invalid_results = (
            subprocess.CompletedProcess([], 0, b"1\n1\n", b""),
            subprocess.CompletedProcess([], 0, b"0\n", b""),
            subprocess.CompletedProcess([], 0, b"12", b""),
            subprocess.CompletedProcess([], 0, b"12\n", b"warning"),
            subprocess.CompletedProcess([], 1, b"12\n", b""),
            subprocess.CompletedProcess([], 0, b"\xff\n", b""),
        )
        for completed in invalid_results:
            with self.subTest(completed=completed), mock.patch.object(
                forge_server.subprocess,
                "run",
                return_value=completed,
            ), self.assertRaises(forge_server.E2EError):
                forge_server.read_java_process_inventory()

    def test_unknown_java_pid_is_rejected_but_named_unbound_pid_is_bounded(
        self,
    ) -> None:
        with mock.patch.object(
            forge_server,
            "read_java_process_inventory",
            return_value=(WRAPPER_TARGET.pid, SERVER_JAVA_PROCESS_ID, 77777),
        ):
            with self.assertRaisesRegex(
                forge_server.E2EError,
                "unowned Java process.*77777",
            ):
                forge_server.verify_java_process_inventory((WRAPPER_TARGET,))

            with self.assertRaisesRegex(
                forge_server.E2EError,
                "at most one unbound launch PID",
            ):
                forge_server.verify_java_process_inventory(
                    (WRAPPER_TARGET,),
                    (SERVER_JAVA_PROCESS_ID, 77777),
                )
        with mock.patch.object(
            forge_server,
            "read_java_process_inventory",
            return_value=(WRAPPER_TARGET.pid, SERVER_JAVA_PROCESS_ID),
        ):
            self.assertEqual(
                (WRAPPER_TARGET.pid, SERVER_JAVA_PROCESS_ID),
                forge_server.verify_java_process_inventory(
                    (WRAPPER_TARGET,),
                    (SERVER_JAVA_PROCESS_ID,),
                ),
            )


class InventoryAwareMemoryTests(unittest.TestCase):
    def test_bounded_wait_allows_exact_six_gib_and_rejects_one_more_byte(
        self,
    ) -> None:
        second_target = forge_server.macos_guarded_java.OwnedJavaProcess(
            pid=SERVER_JAVA_PROCESS_ID,
            process_group_id=SERVER_LAUNCH_PROCESS_ID,
            proc_start_abstime=SERVER_TARGET.proc_start_abstime,
            expected_executable=SERVER_TARGET.expected_executable,
        )
        wrapper_guard = mock.Mock(
            target=WRAPPER_TARGET,
            monitor=None,
            launch_anchor=None,
            anchor_target=None,
        )
        server_guard = mock.Mock(target=second_target)
        gibibyte = 1024 * 1024 * 1024
        with tempfile.TemporaryDirectory() as temporary_directory:
            output_path = Path(temporary_directory) / "gradle.log"
            output_path.write_bytes(b"")
            for second_footprint, accepted in (
                (3 * gibibyte, True),
                (3 * gibibyte + 1, False),
            ):
                with self.subTest(second_footprint=second_footprint):
                    samples = {
                        WRAPPER_TARGET: memory_sample(
                            WRAPPER_TARGET,
                            3 * gibibyte,
                        ),
                        second_target: memory_sample(
                            second_target,
                            second_footprint,
                        ),
                    }
                    sampler = mock.Mock()
                    sampler.sample.side_effect = (
                        lambda target, _observed_at: samples[target]
                    )
                    patches = (
                        mock.patch.object(
                            forge_server,
                            "verify_active_launch_runtime_inventory",
                        ),
                        mock.patch.object(
                            forge_server,
                            "verify_wrapper_launch_supervision",
                        ),
                        mock.patch.object(
                            forge_server,
                            "verify_server_guard_is_enforcing",
                        ),
                        mock.patch.object(
                            forge_server,
                            "read_java_process_inventory",
                            return_value=(WRAPPER_TARGET.pid, second_target.pid),
                        ),
                    )
                    with patches[0], patches[1], patches[2], patches[3]:
                        if accepted:
                            self.assertEqual(
                                0,
                                forge_server.wait_for_bounded_process(
                                    FakeProcess(exit_code=0),
                                    output_path,
                                    wrapper_guard=wrapper_guard,
                                    server_guard=server_guard,
                                    sampler=sampler,
                                ),
                            )
                        else:
                            with self.assertRaisesRegex(
                                forge_server.E2EError,
                                "six-GiB",
                            ):
                                forge_server.wait_for_bounded_process(
                                    FakeProcess(exit_code=0),
                                    output_path,
                                    wrapper_guard=wrapper_guard,
                                    server_guard=server_guard,
                                    sampler=sampler,
                                )


class DetachedJavaAbsenceTests(unittest.TestCase):
    @staticmethod
    def second_target() -> forge_server.macos_guarded_java.OwnedJavaProcess:
        return forge_server.macos_guarded_java.OwnedJavaProcess(
            pid=SERVER_JAVA_PROCESS_ID + 1,
            process_group_id=SERVER_LAUNCH_PROCESS_ID,
            proc_start_abstime=SERVER_TARGET.proc_start_abstime + 1,
            expected_executable=SERVER_TARGET.expected_executable,
        )

    @staticmethod
    def observations(
        *targets: forge_server.macos_guarded_java.OwnedJavaProcess,
    ) -> tuple[forge_server.DetachedJavaObservation, ...]:
        return tuple(
            forge_server.DetachedJavaObservation(
                target=target,
                process_group_id=target.process_group_id,
                session_id=SERVER_LAUNCH_PROCESS_ID,
            )
            for target in targets
        )

    def test_exact_six_gib_is_observed_then_continuous_absence_succeeds(
        self,
    ) -> None:
        second = self.second_target()
        targets = (SERVER_TARGET, second)
        gibibyte = 1024 * 1024 * 1024
        calls = {target: 0 for target in targets}
        missing = forge_server.macos_guarded_java.MemorySample.missing(
            1,
            macos_memory_guard.SampleSource.PROC_PID_RUSAGE_V4,
            "gone",
        )

        def sample(target: object, _observed_at: object) -> object:
            calls[target] += 1
            if calls[target] <= 2:
                return memory_sample(target, 3 * gibibyte)
            return missing

        sampler = mock.Mock()
        sampler.sample.side_effect = sample
        with (
            mock.patch.object(
                forge_server,
                "read_java_process_inventory",
                side_effect=[tuple(target.pid for target in targets), ()],
            ),
            mock.patch.object(
                forge_server,
                "process_group_exists",
                return_value=False,
            ),
            mock.patch.object(
                forge_server.os,
                "getpgid",
                return_value=SERVER_LAUNCH_PROCESS_ID,
            ),
            mock.patch.object(
                forge_server.os,
                "getsid",
                return_value=SERVER_LAUNCH_PROCESS_ID,
            ),
            mock.patch.object(forge_server.time, "sleep"),
        ):
            self.assertTrue(
                forge_server.wait_for_detached_java_absence(
                    self.observations(*targets),
                    sampler,
                    timeout_seconds=1.0,
                    stable_absence_seconds=0.0,
                )
            )

    def test_one_byte_over_six_gib_fails_closed(self) -> None:
        second = self.second_target()
        gibibyte = 1024 * 1024 * 1024
        footprints = {
            SERVER_TARGET: 3 * gibibyte,
            second: 3 * gibibyte + 1,
        }
        sampler = mock.Mock()
        sampler.sample.side_effect = lambda target, _observed_at: memory_sample(
            target,
            footprints[target],
        )
        with mock.patch.object(
            forge_server,
            "read_java_process_inventory",
            return_value=tuple(footprints),
        ):
            self.assertFalse(
                forge_server.wait_for_detached_java_absence(
                    self.observations(SERVER_TARGET, second),
                    sampler,
                    timeout_seconds=1.0,
                    stable_absence_seconds=0.0,
                )
            )

    def test_fallback_and_identity_drift_samples_fail_closed(self) -> None:
        invalid_samples = (
            forge_server.macos_guarded_java.MemorySample.fallback(
                1,
                SERVER_TARGET,
                resident_size_bytes=1,
            ),
            forge_server.macos_guarded_java.MemorySample.identity_drift(
                1,
                "identity changed",
            ),
        )
        for invalid_sample in invalid_samples:
            with self.subTest(status=invalid_sample.status):
                sampler = mock.Mock()
                sampler.sample.return_value = invalid_sample
                with mock.patch.object(
                    forge_server,
                    "read_java_process_inventory",
                    return_value=(SERVER_TARGET.pid,),
                ):
                    self.assertFalse(
                        forge_server.wait_for_detached_java_absence(
                            self.observations(SERVER_TARGET),
                            sampler,
                            timeout_seconds=1.0,
                            stable_absence_seconds=0.0,
                        )
                    )

    def test_session_identity_mismatch_fails_closed(self) -> None:
        sampler = mock.Mock()
        sampler.sample.return_value = memory_sample(SERVER_TARGET, 1)
        with (
            mock.patch.object(
                forge_server,
                "read_java_process_inventory",
                return_value=(SERVER_TARGET.pid,),
            ),
            mock.patch.object(
                forge_server.os,
                "getpgid",
                return_value=SERVER_LAUNCH_PROCESS_ID,
            ),
            mock.patch.object(
                forge_server.os,
                "getsid",
                return_value=SERVER_LAUNCH_PROCESS_ID + 1,
            ),
        ):
            self.assertFalse(
                forge_server.wait_for_detached_java_absence(
                    self.observations(SERVER_TARGET),
                    sampler,
                    timeout_seconds=1.0,
                    stable_absence_seconds=0.0,
                )
            )

    def test_absence_timer_restarts_when_a_known_java_pid_reappears(self) -> None:
        class Clock:
            def __init__(self) -> None:
                self.now = 0.0

            def monotonic(self) -> float:
                return self.now

            def monotonic_ns(self) -> int:
                return int(self.now * 1_000_000_000)

            def sleep(self, _seconds: float) -> None:
                self.now += 1.0

        clock = Clock()
        sampler = mock.Mock()
        sampler.sample.return_value = (
            forge_server.macos_guarded_java.MemorySample.missing(
                1,
                macos_memory_guard.SampleSource.PROC_PID_RUSAGE_V4,
                "gone",
            )
        )
        inventory = mock.Mock(
            side_effect=[
                (),
                (),
                (SERVER_TARGET.pid,),
                (),
                (),
                (),
            ]
        )
        with (
            mock.patch.object(
                forge_server,
                "read_java_process_inventory",
                inventory,
            ),
            mock.patch.object(
                forge_server,
                "process_group_exists",
                return_value=False,
            ),
            mock.patch.object(forge_server.time, "monotonic", clock.monotonic),
            mock.patch.object(
                forge_server.time,
                "monotonic_ns",
                clock.monotonic_ns,
            ),
            mock.patch.object(forge_server.time, "sleep", clock.sleep),
        ):
            self.assertTrue(
                forge_server.wait_for_detached_java_absence(
                    self.observations(SERVER_TARGET),
                    sampler,
                    timeout_seconds=20.0,
                    stable_absence_seconds=2.0,
                )
            )

        self.assertEqual(6, inventory.call_count)


class DetachedJavaActiveCleanupTests(unittest.TestCase):
    def test_dedicated_detached_group_is_stopped_through_its_exact_leader(self) -> None:
        target = forge_server.macos_guarded_java.OwnedJavaProcess(
            pid=65001,
            process_group_id=65001,
            proc_start_abstime=12345,
            expected_executable="/test/detached/bin/java",
        )
        observation = forge_server.DetachedJavaObservation(
            target=target,
            process_group_id=target.pid,
            session_id=target.pid,
        )
        sampler = mock.Mock()
        sampler.sample.return_value = memory_sample(target, 1)
        with (
            mock.patch.object(forge_server.os, "getpgid", return_value=target.pid),
            mock.patch.object(forge_server.os, "getsid", return_value=target.pid),
            mock.patch.object(
                forge_server.macos_guarded_java,
                "stop_owned_java_process",
            ) as stop_group,
        ):
            forge_server.stop_detached_java_observations((observation,), sampler)
        stop_group.assert_called_once_with(
            target,
            owned_process_group_id=target.pid,
            timeout_seconds=forge_server.PROCESS_STOP_TIMEOUT_SECONDS,
            sampler=sampler,
        )

    def test_detached_group_member_is_stopped_by_exact_pid_only(self) -> None:
        target = forge_server.macos_guarded_java.OwnedJavaProcess(
            pid=65002,
            process_group_id=65001,
            proc_start_abstime=12346,
            expected_executable="/test/detached/bin/java",
        )
        observation = forge_server.DetachedJavaObservation(
            target=target,
            process_group_id=65001,
            session_id=65001,
        )
        sampler = mock.Mock()
        sampler.sample.return_value = memory_sample(target, 1)
        with (
            mock.patch.object(forge_server.os, "getpgid", return_value=65001),
            mock.patch.object(forge_server.os, "getsid", return_value=65001),
            mock.patch.object(forge_server, "stop_exact_java_process") as stop_exact,
        ):
            forge_server.stop_detached_java_observations((observation,), sampler)
        stop_exact.assert_called_once_with(target, sampler)

    def test_exact_pid_stop_revalidates_before_signaling(self) -> None:
        target = forge_server.macos_guarded_java.OwnedJavaProcess(
            pid=65003,
            process_group_id=65001,
            proc_start_abstime=12347,
            expected_executable="/test/detached/bin/java",
        )
        sampler = mock.Mock()
        sampler.sample.side_effect = (
            memory_sample(target, 1),
            forge_server.macos_guarded_java.MemorySample.missing(
                2,
                macos_memory_guard.SampleSource.PROC_PID_RUSAGE_V4,
                "terminated",
            ),
        )
        sampler.revalidate.return_value = target
        with mock.patch.object(forge_server.os, "kill") as kill:
            self.assertFalse(
                forge_server.stop_exact_java_process(target, sampler)
            )
        kill.assert_called_once_with(target.pid, signal.SIGTERM)

    def test_unbound_detached_identity_is_never_signaled(self) -> None:
        observation = forge_server.DetachedJavaObservation(
            target=None,
            process_group_id=65001,
            session_id=65001,
        )
        with (
            mock.patch.object(forge_server.os, "kill") as kill,
            mock.patch.object(forge_server.os, "killpg") as kill_group,
            self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "no exact stoppable identity",
            ),
        ):
            forge_server.stop_detached_java_observations(
                (observation,),
                mock.Mock(),
            )
        kill.assert_not_called()
        kill_group.assert_not_called()

    def test_server_cleanup_stops_detached_identity_before_absence_wait(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            wrapper_guard = fake_wrapper_guard(runtime)
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                wrapper_guard.runtime,
            )
            target = forge_server.macos_guarded_java.OwnedJavaProcess(
                pid=65004,
                process_group_id=65004,
                proc_start_abstime=12348,
                expected_executable="/test/detached/bin/java",
            )
            observation = forge_server.DetachedJavaObservation(
                target=target,
                process_group_id=target.pid,
                session_id=target.pid,
            )
            events: list[str] = []
            with (
                mock.patch.object(
                    forge_server,
                    "stop_owned_launch_group",
                    side_effect=lambda *_arguments: events.append("owned-group"),
                ),
                mock.patch.object(
                    forge_server,
                    "stop_detached_java_observations",
                    side_effect=lambda *_arguments: events.append("detached"),
                ) as stop_detached,
                mock.patch.object(
                    forge_server,
                    "wait_for_detached_java_absence",
                    side_effect=lambda *_arguments: events.append("wait") or False,
                ),
                self.assertRaisesRegex(
                    forge_server.CleanupUncertainError,
                    "detached server Java",
                ),
            ):
                forge_server.cleanup_server_launch(
                    FakeProcess(timeout=True),
                    None,
                    mock.Mock(),
                    wrapper_guard=wrapper_guard,
                    state_root=runtime,
                    detached_observation=observation,
                )
            self.assertEqual(["owned-group", "detached", "wait"], events)
            stopped_observations = stop_detached.call_args.args[0]
            self.assertIn(observation, stopped_observations)


class GradleTopologyPreflightTests(unittest.TestCase):
    def test_setup_failure_preserves_the_exact_completed_runtime(self) -> None:
        with temporary_repository() as (root, manifest_path):
            configuration = load_temporary_configuration(root, manifest_path)
            with tempfile.TemporaryDirectory() as temporary_directory:
                state_root = Path(temporary_directory).resolve() / ".state"
                state_root.mkdir()
                run_lock = forge_server.acquire_run_lock(
                    configuration,
                    state_root,
                    SERVER_RUN_TOKEN,
                )
                try:
                    with (
                        mock.patch.object(
                            forge_server,
                            "gradle_launch_environment",
                            side_effect=forge_server.E2EError(
                                "environment drift"
                            ),
                        ),
                        self.assertRaisesRegex(
                            forge_server.E2EError,
                            "environment drift",
                        ),
                    ):
                        forge_server.verify_gradle_launcher_topology(
                            configuration,
                            Path(TOPOLOGY_PARENT_EXECUTABLE),
                            mock.Mock(),
                            state_root,
                            SERVER_RUN_TOKEN,
                            run_lock,
                        )
                finally:
                    forge_server.release_owned_run_lock(run_lock)
                    forge_server.close_owned_run_lock(run_lock)

                retained = list(state_root.iterdir())
                self.assertEqual(1, len(retained))
                self.assertTrue(
                    retained[0].name.startswith(
                        forge_server.GRADLE_TOPOLOGY_COMPLETED_RUNTIME_PREFIX
                    )
                )

    def test_loader_free_javaexec_is_bound_and_acknowledged_before_success(
        self,
    ) -> None:
        class TopologyProcess(FakeProcess):
            def __init__(self) -> None:
                super().__init__(timeout=True)
                self.acknowledgement_path: Path | None = None
                self.acknowledgement_content: bytes | None = None

            def poll(self) -> int | None:
                if (
                    self.acknowledgement_path is not None
                    and self.acknowledgement_path.exists()
                ):
                    self.acknowledgement_content = (
                        self.acknowledgement_path.read_bytes()
                    )
                    return 0
                return None

            def wait(self, timeout: float | None = None) -> int:
                if self.poll() == 0:
                    return 0
                raise subprocess.TimeoutExpired("topology", timeout)

        with temporary_repository() as (root, manifest_path):
            configuration = load_temporary_configuration(root, manifest_path)
            with tempfile.TemporaryDirectory() as temporary_directory:
                state_root = Path(temporary_directory).resolve() / ".state"
                state_root.mkdir()
                forge_server.provision_gradle_user_home(state_root)
                process = TopologyProcess()
                wrapper_target = WRAPPER_TARGET
                java_target = forge_server.macos_guarded_java.OwnedJavaProcess(
                    pid=TOPOLOGY_JAVA_PROCESS_ID,
                    process_group_id=SERVER_LAUNCH_PROCESS_ID,
                    proc_start_abstime=222,
                    expected_executable=TOPOLOGY_JAVA_EXECUTABLE,
                )
                wrapper_guard = fake_wrapper_guard(state_root)
                run_lock = forge_server.acquire_run_lock(
                    configuration,
                    state_root,
                    SERVER_RUN_TOKEN,
                )
                sampler = mock.Mock()
                sampler.bind.return_value = java_target
                sampler.revalidate.return_value = wrapper_target
                sampler.sample.return_value = mock.Mock(
                    status=forge_server.macos_guarded_java.SampleStatus.MISSING
                )
                launch: dict[str, object] = {}

                def fake_popen(*args: object, **kwargs: object) -> TopologyProcess:
                    launch["command"] = args[0]
                    launch.update(kwargs)
                    environment = kwargs["env"]
                    handoff_path = Path(
                        environment[
                            forge_server.GRADLE_TOPOLOGY_HANDOFF_ENVIRONMENT_VARIABLE
                        ]
                    )
                    handoff_path.write_bytes(valid_topology_handoff())
                    process.acknowledgement_path = Path(
                        environment[
                            forge_server.GRADLE_TOPOLOGY_ACKNOWLEDGEMENT_ENVIRONMENT_VARIABLE
                        ]
                    )
                    return process

                launch_anchor = mock.Mock(
                    process=process,
                    target=WRAPPER_TARGET,
                )

                def prepare_anchor(
                    *_arguments: object,
                    **keyword_arguments: object,
                ) -> object:
                    launch["task_path"] = keyword_arguments["task_path"]
                    environment = _arguments[6]
                    launch["env"] = environment
                    fake_popen(
                        [*forge_server.build_gradle_arguments(
                            forge_server.GRADLE_TOPOLOGY_TASK_PATH
                        )],
                        env=environment,
                    )
                    return launch_anchor

                def cleanup_wrapper(
                    *_arguments: object,
                    **_keyword_arguments: object,
                ) -> None:
                    forge_server.retire_owned_runtime_directory(
                        wrapper_guard.runtime
                    )

                try:
                    with (
                        mock.patch.object(
                            forge_server.secrets,
                            "token_hex",
                            return_value=SERVER_RUN_TOKEN,
                        ),
                    mock.patch.object(
                        forge_server,
                        "java_major_version",
                        return_value=21,
                    ),
                    mock.patch.object(
                        forge_server,
                        "prepare_gradle_launch_anchor",
                        side_effect=prepare_anchor,
                    ),
                    mock.patch.object(
                        forge_server.os,
                        "getpgid",
                        return_value=SERVER_LAUNCH_PROCESS_ID,
                    ),
                    mock.patch.object(
                        forge_server.os,
                        "getsid",
                        return_value=SERVER_LAUNCH_PROCESS_ID,
                    ),
                    mock.patch.object(
                        forge_server,
                        "process_group_exists",
                        return_value=False,
                    ),
                    mock.patch.object(
                        forge_server,
                        "start_anchored_wrapper_java_guard",
                        return_value=wrapper_guard,
                    ) as start_wrapper_guard,
                    mock.patch.object(
                        forge_server,
                        "verify_gradle_launch_anchor_guard",
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_launch_group_contains_only_anchor",
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_wrapper_launch_supervision",
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_exact_java_memory_envelope",
                    ),
                        mock.patch.object(
                            forge_server,
                            "verify_active_launch_runtime_inventory",
                        ),
                        mock.patch.object(
                            forge_server,
                            "verify_java_process_inventory",
                        ),
                        mock.patch.object(
                            forge_server,
                            "require_no_java_processes",
                        ),
                        mock.patch.object(
                            forge_server,
                            "wait_for_detached_java_absence",
                            return_value=True,
                        ),
                        mock.patch.object(
                            forge_server,
                            "verify_gradle_distribution_initialization",
                        ),
                        mock.patch.object(
                            forge_server,
                            "cleanup_wrapper_java_guard",
                            side_effect=cleanup_wrapper,
                        ) as cleanup_wrapper_guard,
                    ):
                        forge_server.verify_gradle_launcher_topology(
                            configuration,
                            Path(TOPOLOGY_PARENT_EXECUTABLE),
                            sampler,
                            state_root,
                            SERVER_RUN_TOKEN,
                            run_lock,
                        )
                finally:
                    forge_server.release_owned_run_lock(run_lock)
                    forge_server.close_owned_run_lock(run_lock)

                self.assertEqual(
                    forge_server.GRADLE_TOPOLOGY_TASK_PATH,
                    launch["task_path"],
                )
                environment = launch["env"]
                self.assertEqual("", environment["JAVA_OPTS"])
                self.assertEqual("-Xmx2G", environment["GRADLE_OPTS"])
                self.assertEqual(
                    f"token={SERVER_RUN_TOKEN}\n".encode("ascii"),
                    process.acknowledgement_content,
                )
                start_wrapper_guard.assert_called_once()
                sampler.bind.assert_called_once_with(
                    TOPOLOGY_JAVA_PROCESS_ID,
                    SERVER_LAUNCH_PROCESS_ID,
                    TOPOLOGY_JAVA_EXECUTABLE,
                )
                cleanup_wrapper_guard.assert_called_once_with(
                    process,
                    wrapper_guard,
                    sampler,
                    state_root,
                    require_normal_watchdog_exit=True,
                )
                self.assertEqual(
                    2,
                    len(
                        [
                            entry
                            for entry in state_root.iterdir()
                            if entry.name.startswith(
                                ".forge-server-completed-"
                            )
                        ]
                    ),
                )

    def test_javaexec_is_bound_before_an_escaped_group_is_rejected(
        self,
    ) -> None:
        with temporary_repository() as (root, manifest_path):
            configuration = load_temporary_configuration(root, manifest_path)
            with tempfile.TemporaryDirectory() as temporary_directory:
                state_root = Path(temporary_directory).resolve() / ".state"
                state_root.mkdir()
                forge_server.provision_gradle_user_home(state_root)
                process = FakeProcess(timeout=True)
                wrapper_target = WRAPPER_TARGET
                detached_group = SERVER_LAUNCH_PROCESS_ID + 1
                java_target = forge_server.macos_guarded_java.OwnedJavaProcess(
                    pid=TOPOLOGY_JAVA_PROCESS_ID,
                    process_group_id=detached_group,
                    proc_start_abstime=222,
                    expected_executable=TOPOLOGY_JAVA_EXECUTABLE,
                )
                wrapper_guard = fake_wrapper_guard(state_root)
                run_lock = forge_server.acquire_run_lock(
                    configuration,
                    state_root,
                    SERVER_RUN_TOKEN,
                )
                sampler = mock.Mock()
                sampler.bind.return_value = java_target
                sampler.revalidate.side_effect = lambda target: target
                sampler.sample.return_value = (
                    forge_server.macos_guarded_java.MemorySample.missing(
                        1,
                        macos_memory_guard.SampleSource.PROC_PID_RUSAGE_V4,
                        "gone during cleanup",
                    )
                )

                def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
                    environment = kwargs["env"]
                    handoff_path = Path(
                        environment[
                            forge_server.GRADLE_TOPOLOGY_HANDOFF_ENVIRONMENT_VARIABLE
                        ]
                    )
                    handoff_path.write_bytes(valid_topology_handoff())
                    return process

                launch_anchor = mock.Mock(
                    process=process,
                    target=WRAPPER_TARGET,
                )

                def prepare_anchor(
                    *_arguments: object,
                    **_keyword_arguments: object,
                ) -> object:
                    fake_popen(env=_arguments[6])
                    return launch_anchor

                def fake_getpgid(pid: int) -> int:
                    if pid == SERVER_LAUNCH_PROCESS_ID:
                        return SERVER_LAUNCH_PROCESS_ID
                    if pid != TOPOLOGY_JAVA_PROCESS_ID:
                        raise AssertionError(f"Unexpected PID: {pid}")
                    return detached_group

                def cleanup_wrapper(
                    *_arguments: object,
                    **_keyword_arguments: object,
                ) -> None:
                    forge_server.retire_owned_runtime_directory(
                        wrapper_guard.runtime
                    )

                try:
                    with (
                        mock.patch.object(
                            forge_server.secrets,
                            "token_hex",
                            return_value=SERVER_RUN_TOKEN,
                        ),
                    mock.patch.object(
                        forge_server,
                        "java_major_version",
                        return_value=21,
                    ),
                    mock.patch.object(
                        forge_server,
                        "prepare_gradle_launch_anchor",
                        side_effect=prepare_anchor,
                    ),
                    mock.patch.object(
                        forge_server.os,
                        "getpgid",
                        side_effect=fake_getpgid,
                    ),
                    mock.patch.object(
                        forge_server.os,
                        "getsid",
                        return_value=SERVER_LAUNCH_PROCESS_ID,
                    ),
                    mock.patch.object(
                        forge_server,
                        "stop_process_group",
                    ) as stop_group,
                    mock.patch.object(
                        forge_server,
                        "write_bytes_exclusive",
                    ) as acknowledge,
                    mock.patch.object(
                        forge_server,
                        "start_anchored_wrapper_java_guard",
                        return_value=wrapper_guard,
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_wrapper_launch_supervision",
                    ),
                    mock.patch.object(
                        forge_server,
                        "verify_exact_java_memory_envelope",
                    ),
                    mock.patch.object(
                        forge_server,
                        "wait_for_detached_java_absence",
                        return_value=True,
                    ) as wait_detached,
                        mock.patch.object(
                            forge_server,
                            "verify_active_launch_runtime_inventory",
                        ),
                        mock.patch.object(
                            forge_server,
                            "verify_java_process_inventory",
                        ),
                        mock.patch.object(
                            forge_server,
                            "require_no_java_processes",
                        ),
                        mock.patch.object(
                            forge_server,
                            "verify_gradle_distribution_initialization",
                        ),
                        mock.patch.object(
                            forge_server,
                            "cleanup_wrapper_java_guard",
                            side_effect=cleanup_wrapper,
                        ),
                        self.assertRaisesRegex(
                            forge_server.E2EError,
                            "escaped",
                        ),
                    ):
                        forge_server.verify_gradle_launcher_topology(
                            configuration,
                            Path(TOPOLOGY_PARENT_EXECUTABLE),
                            sampler,
                            state_root,
                            SERVER_RUN_TOKEN,
                            run_lock,
                        )
                finally:
                    forge_server.release_owned_run_lock(run_lock)
                    forge_server.close_owned_run_lock(run_lock)

                sampler.bind.assert_called_once_with(
                    TOPOLOGY_JAVA_PROCESS_ID,
                    detached_group,
                    TOPOLOGY_JAVA_EXECUTABLE,
                )
                acknowledge.assert_called_once()
                stop_group.assert_called_once_with(process)
                observations = wait_detached.call_args.args[0]
                self.assertIn(
                    java_target,
                    [observation.target for observation in observations],
                )
                detached = next(
                    observation
                    for observation in observations
                    if observation.target == java_target
                )
                self.assertEqual(detached_group, detached.process_group_id)
                self.assertEqual(
                    2,
                    len(
                        [
                            entry
                            for entry in state_root.iterdir()
                            if entry.name.startswith(
                                ".forge-server-completed-"
                            )
                        ]
                    ),
                )

    def test_wrapper_requires_its_own_process_group_and_session(self) -> None:
        process = FakeProcess(timeout=True)
        with (
            mock.patch.object(
                forge_server.os,
                "getpgid",
                return_value=process.pid,
            ),
            mock.patch.object(
                forge_server.os,
                "getsid",
                return_value=process.pid + 1,
            ),
            self.assertRaisesRegex(forge_server.E2EError, "isolated"),
        ):
            forge_server.verify_owned_gradle_process_group(process)


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
            wrapper_guard = fake_wrapper_guard(runtime)
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                wrapper_guard.runtime,
            )
            sampler = mock.Mock()
            sampler.bind.return_value = SERVER_TARGET
            with (
                mock.patch.object(
                    forge_server.os,
                    "getpgid",
                    return_value=SERVER_LAUNCH_PROCESS_ID,
                ),
                mock.patch.object(
                    forge_server.os,
                    "getsid",
                    return_value=SERVER_LAUNCH_PROCESS_ID,
                ),
                mock.patch.object(
                    forge_server,
                    "verify_wrapper_launch_supervision",
                ) as verify_supervision,
                mock.patch.object(
                    forge_server,
                    "verify_exact_java_memory_envelope",
                ) as verify_envelope,
                mock.patch.object(
                    forge_server,
                    "verify_java_process_inventory",
                ),
            ):
                target = forge_server.wait_for_server_java_handoff(
                    process,
                    output_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    sampler,
                    time.monotonic() + 1.0,
                    wrapper_guard,
                )

            self.assertEqual(SERVER_TARGET, target)
            sampler.bind.assert_called_once_with(
                SERVER_JAVA_PROCESS_ID,
                SERVER_LAUNCH_PROCESS_ID,
                SERVER_TARGET.expected_executable,
            )
            verify_supervision.assert_called()
            verify_envelope.assert_called_with(
                sampler,
                (WRAPPER_TARGET, SERVER_TARGET),
            )

    def test_wait_binds_before_rejecting_a_java_pid_outside_the_launch_group(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            write_memory_handoff(runtime)
            process = FakeProcess(timeout=True)
            wrapper_guard = fake_wrapper_guard(runtime)
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                wrapper_guard.runtime,
            )
            detached_group = SERVER_LAUNCH_PROCESS_ID + 1
            detached_target = forge_server.macos_guarded_java.OwnedJavaProcess(
                pid=SERVER_JAVA_PROCESS_ID,
                process_group_id=detached_group,
                proc_start_abstime=SERVER_TARGET.proc_start_abstime,
                expected_executable=SERVER_TARGET.expected_executable,
            )
            sampler = mock.Mock()
            sampler.bind.return_value = detached_target
            with (
                mock.patch.object(
                    forge_server.os,
                    "getpgid",
                    return_value=detached_group,
                ),
                mock.patch.object(
                    forge_server.os,
                    "getsid",
                    return_value=detached_group,
                ),
                mock.patch.object(
                    forge_server,
                    "verify_wrapper_launch_supervision",
                ),
                mock.patch.object(
                    forge_server,
                    "verify_exact_java_memory_envelope",
                ),
                mock.patch.object(
                    forge_server,
                    "verify_java_process_inventory",
                ),
                self.assertRaisesRegex(
                    forge_server.DetachedJavaLaunchError,
                    "outside",
                ) as raised,
            ):
                forge_server.wait_for_server_java_handoff(
                    process,
                    output_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    sampler,
                    time.monotonic() + 1.0,
                    wrapper_guard,
                )

            sampler.bind.assert_called_once_with(
                SERVER_JAVA_PROCESS_ID,
                detached_group,
                SERVER_TARGET.expected_executable,
            )
            self.assertEqual(detached_target, raised.exception.observation.target)
            self.assertEqual(
                detached_group,
                raised.exception.observation.process_group_id,
            )

    def test_wait_fails_closed_when_no_handoff_arrives(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            process = FakeProcess(timeout=True)
            wrapper_guard = fake_wrapper_guard(runtime)
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                wrapper_guard.runtime,
            )
            with (
                mock.patch.object(
                    forge_server,
                    "MEMORY_HANDOFF_TIMEOUT_SECONDS",
                    0.0,
                ),
                mock.patch.object(
                    forge_server,
                    "verify_java_process_inventory",
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
                    wrapper_guard,
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
            wrapper_guard = fake_wrapper_guard(runtime)
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                wrapper_guard.runtime,
            )
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
                ) as start_monitor_mock,
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
                mock.patch.object(
                    forge_server,
                    "verify_wrapper_launch_supervision",
                ),
                mock.patch.object(
                    forge_server,
                    "verify_java_guard_is_enforcing",
                ),
                mock.patch.object(
                    forge_server,
                    "verify_exact_java_memory_envelope",
                ),
                mock.patch.object(
                    forge_server,
                    "verify_java_process_inventory",
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
                    wrapper_guard,
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
            self.assertEqual(
                forge_server.STRICT_MEMORY_POLICY_NAME,
                start_monitor_mock.call_args.kwargs["policy_name"],
            )

    def test_caffeinate_validation_failure_preserves_bound_server_identity(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            wrapper_guard = fake_wrapper_guard(runtime)
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                wrapper_guard.runtime,
            )
            missing_caffeinate = runtime / "missing-caffeinate"

            with (
                mock.patch.object(
                    forge_server,
                    "wait_for_server_java_handoff",
                    return_value=SERVER_TARGET,
                ),
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "start_guarded_java_monitor",
                ) as start_monitor,
                mock.patch.object(forge_server.subprocess, "Popen") as popen,
                self.assertRaises(
                    forge_server.ServerGuardStartError
                ) as raised,
            ):
                forge_server.start_server_java_guard(
                    FakeProcess(timeout=True),
                    output_path,
                    runtime,
                    SERVER_RUN_TOKEN,
                    mock.Mock(),
                    time.monotonic() + 1.0,
                    runtime,
                    mock.Mock(),
                    wrapper_guard,
                    missing_caffeinate,
                )

            partial = raised.exception.server_guard
            self.assertEqual(SERVER_TARGET, partial.target)
            self.assertIsNone(partial.monitor)
            self.assertIsNone(partial.spawned_monitor_process)
            self.assertIsNone(partial.caffeinate_process)
            start_monitor.assert_not_called()
            popen.assert_not_called()

    def test_failed_post_monitor_handoff_retains_partial_guard(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            output_path = runtime / "gradle.log"
            output_path.write_bytes(b"")
            caffeinate_path = runtime / "caffeinate"
            caffeinate_path.write_bytes(b"")
            process = FakeProcess(timeout=True)
            wrapper_guard = fake_wrapper_guard(runtime)
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                wrapper_guard.runtime,
            )
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
                    wrapper_guard,
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
            with (
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "verify_guard_state_paths",
                ) as verify_paths,
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "memory_guard_is_enforcing",
                    return_value=True,
                ) as guard_health,
            ):
                forge_server.verify_server_guard_is_enforcing(guard, sampler)

            sampler.sample.assert_not_called()
            verify_paths.assert_called_once()
            state = guard_health.call_args.args[0]
            self.assertEqual(
                forge_server.SERVER_MAXIMUM_MEMORY_MB,
                state["memory_guard_maximum_memory_mb"],
            )
            self.assertEqual(
                forge_server.STRICT_MEMORY_POLICY_NAME,
                state[
                    forge_server.macos_guarded_java.MEMORY_POLICY_STATE_FIELD
                ],
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
            with (
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "memory_guard_is_enforcing",
                    return_value=False,
                ),
                mock.patch.object(
                    forge_server,
                    "verify_java_process_inventory",
                ) as verify_inventory,
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
            self.assertEqual(2, verify_inventory.call_count)
            verify_inventory.assert_called_with((SERVER_TARGET,))

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
            wrapper_guard = fake_wrapper_guard(runtime)
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                wrapper_guard.runtime,
            )
            process = FakeProcess(timeout=True)
            sampler = mock.Mock()
            sampler.revalidate.return_value = wrapper_guard.target
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.AVAILABLE
            )
            events: list[str] = []
            pinned_evidence = mock.sentinel.pinned_watchdog_evidence

            def stop_launch_group(*_arguments: object) -> None:
                process.timeout = False

            with (
                mock.patch.object(
                    forge_server,
                    "stop_owned_launch_group",
                    side_effect=stop_launch_group,
                ) as stop_group,
                mock.patch.object(forge_server, "require_guarded_server_stopped"),
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "stop_spawned_auxiliary",
                    side_effect=lambda *_args: events.append("stop caffeinate"),
                ) as stop_auxiliary,
                mock.patch.object(
                    forge_server,
                    "stop_java_guard_auxiliary",
                    side_effect=lambda *_args: events.append("stop server monitor"),
                ) as stop_monitor,
                mock.patch.object(
                    forge_server,
                    "wait_for_detached_java_absence",
                    return_value=True,
                ),
                mock.patch.object(
                    forge_server,
                    "cleanup_wrapper_java_guard",
                    side_effect=lambda *_args, **_kwargs: (
                        events.append("freeze watchdog") or pinned_evidence
                    ),
                ) as cleanup_wrapper,
            ):
                forge_server.cleanup_server_launch(
                    process,
                    guard,
                    sampler,
                    wrapper_guard=wrapper_guard,
                    state_root=runtime,
                )

            stop_group.assert_called_once_with(
                process,
                sampler,
                (wrapper_guard.target, SERVER_TARGET),
            )
            stop_auxiliary.assert_called_once_with(guard.caffeinate_process)
            stop_monitor.assert_called_once_with(
                guard.monitor,
                guard.spawned_monitor_process,
            )
            cleanup_wrapper.assert_called_once_with(
                process,
                wrapper_guard,
                sampler,
                runtime,
                require_normal_watchdog_exit=False,
                watchdog_runtime_directory_descriptor=None,
                retain_terminal_evidence=True,
            )
        self.assertEqual(
            [
                "stop caffeinate",
                "stop server monitor",
                "freeze watchdog",
            ],
                events,
            )

    def test_processless_cleanup_rejects_every_retained_launch_identity(self) -> None:
        cases = (
            (
                "wrapper guard",
                None,
                {"wrapper_guard": mock.sentinel.wrapper_guard},
            ),
            ("server guard", mock.sentinel.server_guard, {}),
            (
                "detached observation",
                None,
                {"detached_observation": mock.sentinel.detached_observation},
            ),
            ("unbound handoff", None, {"unbound_handoff_seen": True}),
        )
        for description, server_guard, keyword_arguments in cases:
            with self.subTest(description=description):
                sampler = mock.Mock()
                with self.assertRaisesRegex(
                    forge_server.CleanupUncertainError,
                    "without its direct process handle",
                ):
                    forge_server.cleanup_server_launch(
                        None,
                        server_guard,
                        sampler,
                        **keyword_arguments,
                    )
                sampler.assert_not_called()

    def test_identity_uncertainty_still_stops_the_live_owned_group(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            guard = fake_server_guard(runtime)
            wrapper_guard = fake_wrapper_guard(runtime)
            self.addCleanup(
                forge_server.close_owned_runtime_directory,
                wrapper_guard.runtime,
            )
            process = FakeProcess(timeout=True)
            sampler = mock.Mock()
            sampler.revalidate.return_value = wrapper_guard.target
            sampler.sample.return_value = mock.Mock(
                status=forge_server.macos_guarded_java.SampleStatus.IDENTITY_DRIFT
            )

            def stop_launch_group(*_arguments: object) -> None:
                process.timeout = False

            with (
                mock.patch.object(
                    forge_server,
                    "stop_owned_launch_group",
                    side_effect=stop_launch_group,
                ) as stop_group,
                mock.patch.object(
                    forge_server.macos_guarded_java,
                    "stop_spawned_auxiliary",
                ) as stop_auxiliary,
                mock.patch.object(
                    forge_server,
                    "stop_java_guard_auxiliary",
                ) as stop_monitor,
                self.assertRaisesRegex(forge_server.E2EError, "uncertain"),
            ):
                forge_server.cleanup_server_launch(
                    process,
                    guard,
                    sampler,
                    wrapper_guard=wrapper_guard,
                    state_root=runtime,
                )

            stop_group.assert_called_once_with(
                process,
                sampler,
                (wrapper_guard.target, SERVER_TARGET),
            )
            stop_auxiliary.assert_not_called()
            stop_monitor.assert_not_called()

    def test_unbound_live_wrapper_stops_only_its_revalidated_direct_group(self) -> None:
        process = FakeProcess(timeout=True)
        sampler = mock.Mock()
        with (
            mock.patch.object(
                forge_server,
                "stop_direct_spawned_process",
            ) as stop_direct,
            mock.patch.object(
                forge_server,
                "wait_for_global_java_absence",
                return_value=True,
            ) as wait_global,
            mock.patch.object(
                forge_server,
                "stop_process_group",
            ) as stop_group,
            mock.patch.object(forge_server.os, "killpg") as kill_group,
            self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "never bound.*global Java absence was observed",
            ),
        ):
            forge_server.cleanup_server_launch(
                process,
                None,
                sampler,
                wrapper_guard=None,
            )

        stop_direct.assert_not_called()
        wait_global.assert_called_once_with()
        stop_group.assert_called_once_with(process)
        kill_group.assert_not_called()
        sampler.assert_not_called()

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


class JavaGuardTerminalCleanupTests(unittest.TestCase):
    def monitor_with_terminal_status(
        self,
        runtime: Path,
        status: str,
    ) -> forge_server.macos_guarded_java.GuardedJavaMonitor:
        telemetry_path = runtime / "telemetry.json"
        telemetry_path.write_text(
            json.dumps(
                {
                    "records": [
                        {
                            "source": "proc-pid-rusage-v4",
                            "status": status,
                        }
                    ]
                }
            )
            + "\n",
            encoding="utf-8",
        )
        process = FakeProcess(exit_code=0)
        process.pid = 60004
        return forge_server.macos_guarded_java.GuardedJavaMonitor(
            process=process,
            target=SERVER_TARGET,
            telemetry_path=telemetry_path,
            readiness_path=runtime / "ready.json",
            policy_name=forge_server.STRICT_MEMORY_POLICY_NAME,
        )

    def test_terminal_monitor_requires_authoritative_missing_attestation(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            missing_monitor = self.monitor_with_terminal_status(runtime, "missing")
            forge_server.stop_java_guard_auxiliary(
                missing_monitor,
                missing_monitor.process,
            )

        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            available_monitor = self.monitor_with_terminal_status(
                runtime,
                "available",
            )
            with self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "did not attest terminal exact-process absence",
            ):
                forge_server.stop_java_guard_auxiliary(
                    available_monitor,
                    available_monitor.process,
                )

    def test_partial_monitor_uses_only_its_exact_spawned_process_handle(
        self,
    ) -> None:
        spawned = FakeProcess(timeout=True)
        with mock.patch.object(
            forge_server.macos_guarded_java,
            "stop_spawned_auxiliary",
        ) as stop_spawned:
            forge_server.stop_java_guard_auxiliary(None, spawned)

        stop_spawned.assert_called_once_with(spawned)


class ExecutionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository_context = temporary_repository()
        self.repository_root, self.manifest_path = self.repository_context.__enter__()
        self.configuration = load_temporary_configuration(
            self.repository_root, self.manifest_path
        )
        self.state_context = tempfile.TemporaryDirectory()
        self.state_root = Path(self.state_context.name).resolve() / ".state"
        forge_server.provision_profile(self.configuration, self.state_root)
        self.sampler = mock.Mock()
        self.wrapper_guard = mock.Mock(
            target=WRAPPER_TARGET,
            monitor=None,
            runtime=mock.Mock(),
            launch_anchor=None,
            anchor_target=None,
        )
        self.server_guard = mock.Mock()
        self.pinned_watchdog_evidence = mock.sentinel.pinned_watchdog_evidence
        self.watchdog_records = {
            forge_server.forge_server_launch_watchdog.READINESS_FILE_NAME: {
                "relative_path": (
                    forge_server.forge_server_launch_watchdog.READINESS_FILE_NAME
                ),
                "size": 1,
                "sha256": "1" * 64,
            },
            forge_server.forge_server_launch_watchdog.TELEMETRY_FILE_NAME: {
                "relative_path": (
                    forge_server.forge_server_launch_watchdog.TELEMETRY_FILE_NAME
                ),
                "size": 1,
                "sha256": "2" * 64,
            },
        }
        self.anchor_records = {
            name: {
                "relative_path": name,
                "size": 1,
                "sha256": "3" * 64,
            }
            for name in forge_server.forge_server_launch_anchor.ARTIFACT_FILE_NAMES
        }
        self.launch_anchor: object | None = None

        def prepare_anchor(
            configuration: object,
            _java_path: object,
            _sampler: object,
            _state_root: object,
            _deadline: object,
            output_handle: object,
            environment: object,
            **_keyword_arguments: object,
        ) -> object:
            process = subprocess.Popen(
                ["launch-anchor"],
                cwd=configuration.repository_root,
                env=environment,
                stdin=subprocess.DEVNULL,
                stdout=output_handle,
                stderr=subprocess.STDOUT,
                start_new_session=True,
                close_fds=True,
            )
            self.launch_anchor = mock.Mock(process=process)
            return self.launch_anchor

        self.execution_patchers = (
            mock.patch.object(
                forge_server.macos_guarded_java.MacOsProcessMemorySampler,
                "native",
                return_value=self.sampler,
            ),
            mock.patch.object(forge_server, "verify_gradle_launcher_topology"),
            mock.patch.object(
                forge_server,
                "revalidate_after_gradle_topology",
                side_effect=lambda configuration, java_path, command, *_arguments: (
                    configuration,
                    java_path,
                    command,
                ),
            ),
            mock.patch.object(forge_server, "verify_owned_gradle_process_group"),
            mock.patch.object(forge_server, "final_pre_spawn_revalidation"),
            mock.patch.object(
                forge_server,
                "prepare_gradle_launch_anchor",
                side_effect=prepare_anchor,
            ),
            mock.patch.object(
                forge_server,
                "start_anchored_wrapper_java_guard",
                return_value=self.wrapper_guard,
            ),
            mock.patch.object(
                forge_server,
                "start_server_java_guard",
                return_value=self.server_guard,
            ),
            mock.patch.object(forge_server, "verify_wrapper_launch_supervision"),
            mock.patch.object(forge_server, "verify_java_guard_is_enforcing"),
            mock.patch.object(forge_server, "verify_exact_java_memory_envelope"),
            mock.patch.object(forge_server, "verify_server_guard_is_enforcing"),
            mock.patch.object(
                forge_server,
                "verify_active_launch_runtime_inventory",
            ),
            mock.patch.object(forge_server, "verify_java_process_inventory"),
            mock.patch.object(forge_server, "require_guarded_server_stopped"),
            mock.patch.object(
                forge_server,
                "cleanup_server_launch",
                return_value=self.pinned_watchdog_evidence,
            ),
            mock.patch.object(
                forge_server,
                "verify_pinned_watchdog_evidence",
                return_value=self.watchdog_records,
            ),
            mock.patch.object(
                forge_server,
                "verify_pinned_launch_anchor_evidence",
                return_value=self.anchor_records,
            ),
            mock.patch.object(forge_server, "close_pinned_watchdog_evidence"),
            mock.patch.object(forge_server, "close_owned_runtime_directory"),
        )
        (
            self.native_sampler,
            self.verify_gradle_topology,
            self.revalidate_topology,
            self.verify_launch_group,
            self.final_pre_spawn,
            self.prepare_anchor,
            self.start_wrapper_guard,
            self.start_server_guard,
            self.verify_wrapper_supervision,
            self.verify_java_guard,
            self.verify_memory_envelope,
            self.verify_guard_health,
            self.verify_runtime_inventory,
            self.verify_java_inventory,
            self.require_server_stopped,
            self.cleanup_launch,
            self.verify_watchdog_evidence,
            self.verify_anchor_evidence,
            self.close_watchdog_evidence,
            self.close_wrapper_runtime,
        ) = tuple(patcher.start() for patcher in self.execution_patchers)

    def tearDown(self) -> None:
        for patcher in reversed(self.execution_patchers):
            patcher.stop()
        self.state_context.cleanup()
        self.repository_context.__exit__(None, None, None)

    def publish_probe_outputs(self, output_handle: object) -> None:
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        for name in (
            forge_server.MEMORY_HANDOFF_FILE_NAME,
            forge_server.MEMORY_ACKNOWLEDGEMENT_FILE_NAME,
            forge_server.macos_guarded_java.READINESS_FILE_NAME,
            forge_server.macos_guarded_java.TELEMETRY_FILE_NAME,
            forge_server.forge_server_launch_watchdog.READINESS_FILE_NAME,
            forge_server.forge_server_launch_watchdog.TELEMETRY_FILE_NAME,
            *forge_server.forge_server_launch_anchor.ARTIFACT_FILE_NAMES,
        ):
            (runtime / name).write_bytes(b"test fixture\n")
        for name, source in (
            (
                forge_server.forge_server_launch_anchor.STAGED_SOURCE_FILE_NAME,
                forge_server.REPOSITORY_ROOT
                / forge_server.LAUNCH_ANCHOR_JAVA_SOURCE_RELATIVE_PATH,
            ),
            (
                forge_server.forge_server_launch_anchor.STAGED_WRAPPER_JAR_FILE_NAME,
                forge_server.REPOSITORY_ROOT
                / forge_server.GRADLE_WRAPPER_JAR_RELATIVE_PATH,
            ),
            (
                forge_server.forge_server_launch_anchor.STAGED_WRAPPER_PROPERTIES_FILE_NAME,
                forge_server.REPOSITORY_ROOT
                / forge_server.GRADLE_WRAPPER_PROPERTIES_RELATIVE_PATH,
            ),
        ):
            staged = runtime / name
            staged.write_bytes(source.read_bytes())
            staged.chmod(0o400)
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

        def verify_topology(*_arguments: object) -> None:
            launch_events.append("topology")
            self.assertTrue(
                forge_server.run_lock_path(
                    self.configuration,
                    self.state_root,
                ).is_file()
            )
            self.assertFalse(
                forge_server.run_attempt_path(
                    self.configuration,
                    self.state_root,
                ).exists()
            )

        def revalidate_topology(
            configuration: object,
            java_path: object,
            command: object,
            *_arguments: object,
        ) -> tuple[object, object, object]:
            launch_events.append("revalidate")
            self.assertFalse(
                forge_server.run_attempt_path(
                    self.configuration,
                    self.state_root,
                ).exists()
            )
            return configuration, java_path, command

        self.verify_gradle_topology.side_effect = verify_topology
        self.revalidate_topology.side_effect = revalidate_topology

        def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
            launch_events.append("gradle")
            self.assertTrue(
                forge_server.run_attempt_path(
                    self.configuration,
                    self.state_root,
                ).is_file()
            )
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
        self.assertEqual("", launch["env"]["JAVA_OPTS"])
        self.assertEqual("-Xmx2G", launch["env"]["GRADLE_OPTS"])
        self.assertEqual(
            ["sampler", "topology", "revalidate", "gradle"],
            launch_events,
        )
        run_token = launch["env"][forge_server.RUN_TOKEN_ENVIRONMENT_VARIABLE]
        self.verify_gradle_topology.assert_called_once_with(
            self.configuration,
            Path("/jdk21/bin/java"),
            self.sampler,
            self.state_root,
            run_token,
            mock.ANY,
        )
        self.revalidate_topology.assert_called_once_with(
            self.configuration,
            Path("/jdk21/bin/java"),
            ["caffeinate", "gradle"],
            mock.ANY,
            self.state_root,
        )
        self.assertRegex(run_token, r"^[0-9a-f]{64}$")
        self.assertRegex(
            launch["run_lock"],
            rf"^profile_id={forge_server.PROFILE_ID}\n"
            rf"scenario={forge_server.SCENARIO_ID}\n"
            r"pid=[1-9][0-9]*\n",
        )
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
        self.start_wrapper_guard.assert_called_once_with(
            mock.ANY,
            Path("/jdk21/bin/java"),
            self.sampler,
            mock.ANY,
            mock.ANY,
        )
        self.start_server_guard.assert_called_once_with(
            mock.ANY,
            mock.ANY,
            runtime,
            run_token,
            self.sampler,
            mock.ANY,
            self.repository_root,
            mock.ANY,
            self.wrapper_guard,
            output_descriptor=mock.ANY,
        )
        self.cleanup_launch.assert_called_once_with(
            mock.ANY,
            self.server_guard,
            self.sampler,
            wrapper_guard=self.wrapper_guard,
            launch_anchor=mock.ANY,
            state_root=self.state_root,
            detached_observation=None,
            require_normal_watchdog_exit=True,
            watchdog_runtime_directory_descriptor=mock.ANY,
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
        self.assertEqual(
            self.watchdog_records,
            launcher["launch_watchdog"],
        )
        self.assertEqual(self.anchor_records, launcher["launch_anchor"])
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
            wrapper_guard=self.wrapper_guard,
            launch_anchor=mock.ANY,
            state_root=self.state_root,
            detached_observation=None,
            unbound_handoff_seen=False,
            watchdog_runtime_directory_descriptor=mock.ANY,
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
            wrapper_guard=self.wrapper_guard,
            launch_anchor=mock.ANY,
            state_root=self.state_root,
            detached_observation=None,
            unbound_handoff_seen=False,
            watchdog_runtime_directory_descriptor=mock.ANY,
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
            wrapper_guard=self.wrapper_guard,
            launch_anchor=mock.ANY,
            state_root=self.state_root,
            detached_observation=None,
            unbound_handoff_seen=False,
            watchdog_runtime_directory_descriptor=mock.ANY,
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

    def test_process_log_duplicate_is_closed_when_fdopen_fails(self) -> None:
        original_dup = os.dup
        original_fdopen = os.fdopen
        duplicated: dict[str, int] = {}

        def tracked_dup(descriptor: int) -> int:
            duplicate = original_dup(descriptor)
            duplicated["descriptor"] = duplicate
            return duplicate

        def fail_duplicate_fdopen(
            descriptor: int,
            *args: object,
            **kwargs: object,
        ) -> object:
            if descriptor == duplicated.get("descriptor"):
                raise OSError("fdopen failed")
            return original_fdopen(descriptor, *args, **kwargs)

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["gradle"]),
            ),
            mock.patch.object(forge_server.os, "dup", side_effect=tracked_dup),
            mock.patch.object(
                forge_server.os,
                "fdopen",
                side_effect=fail_duplicate_fdopen,
            ),
            self.assertRaisesRegex(OSError, "fdopen failed"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        duplicate = duplicated["descriptor"]
        with self.assertRaises(OSError):
            os.fstat(duplicate)
        self.assert_launch_attempt_is_exact()

    def test_final_static_revalidation_fails_before_popen_but_after_attempt(self) -> None:
        self.final_pre_spawn.side_effect = forge_server.E2EError(
            "final source drift"
        )
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen") as popen,
            self.assertRaisesRegex(forge_server.E2EError, "final source drift"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        popen.assert_not_called()
        self.final_pre_spawn.assert_called_once()
        self.assert_launch_attempt_is_exact()
        self.assertFalse(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).exists()
        )

    def test_environment_failure_releases_and_closes_run_lock(self) -> None:
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                side_effect=forge_server.E2EError("environment failed"),
            ),
            mock.patch.object(
                forge_server,
                "release_owned_run_lock",
                wraps=forge_server.release_owned_run_lock,
            ) as release_lock,
            mock.patch.object(
                forge_server,
                "close_owned_run_lock",
                wraps=forge_server.close_owned_run_lock,
            ) as close_lock,
            self.assertRaisesRegex(forge_server.E2EError, "environment failed"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        release_lock.assert_called_once()
        close_lock.assert_called_once()
        self.native_sampler.assert_not_called()
        self.assertFalse(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).exists()
        )

    def test_environment_cleanup_uncertainty_retains_lock_and_closes_descriptors(
        self,
    ) -> None:
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                side_effect=forge_server.CleanupUncertainError(
                    "environment cleanup uncertain"
                ),
            ),
            mock.patch.object(
                forge_server,
                "release_owned_run_lock",
                wraps=forge_server.release_owned_run_lock,
            ) as release_lock,
            mock.patch.object(
                forge_server,
                "close_owned_run_lock",
                wraps=forge_server.close_owned_run_lock,
            ) as close_lock,
            self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "environment cleanup uncertain",
            ),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        release_lock.assert_not_called()
        close_lock.assert_called_once()
        self.native_sampler.assert_not_called()
        self.assertTrue(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).is_file()
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
        self.verify_gradle_topology.assert_not_called()
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

    def test_gradle_topology_failure_consumes_no_attempt(self) -> None:
        self.verify_gradle_topology.side_effect = forge_server.E2EError(
            "daemon topology"
        )
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen") as popen,
            self.assertRaisesRegex(forge_server.E2EError, "daemon topology"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        popen.assert_not_called()
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

    def test_attempt_precreation_error_releases_and_closes_run_lock(self) -> None:
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["gradle"]),
            ),
            mock.patch.object(
                forge_server,
                "create_owned_launch_file",
                side_effect=forge_server.E2EError("attempt precreation failed"),
            ),
            mock.patch.object(
                forge_server,
                "release_owned_run_lock",
                wraps=forge_server.release_owned_run_lock,
            ) as release_lock,
            mock.patch.object(
                forge_server,
                "close_owned_run_lock",
                wraps=forge_server.close_owned_run_lock,
            ) as close_lock,
            self.assertRaisesRegex(
                forge_server.E2EError,
                "attempt precreation failed",
            ),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        release_lock.assert_called_once()
        close_lock.assert_called_once()
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

    def test_unpinned_attempt_result_retains_lock_and_closes_descriptors(
        self,
    ) -> None:
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["gradle"]),
            ),
            mock.patch.object(
                forge_server,
                "create_owned_launch_file",
                return_value=None,
            ),
            mock.patch.object(
                forge_server,
                "release_owned_run_lock",
                wraps=forge_server.release_owned_run_lock,
            ) as release_lock,
            mock.patch.object(
                forge_server,
                "close_owned_run_lock",
                wraps=forge_server.close_owned_run_lock,
            ) as close_lock,
            self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "attempt was not pinned",
            ),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        release_lock.assert_not_called()
        close_lock.assert_called_once()
        self.assertTrue(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).is_file()
        )
        self.assertFalse(
            forge_server.run_attempt_path(
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
            wrapper_guard=self.wrapper_guard,
            launch_anchor=mock.ANY,
            state_root=self.state_root,
            detached_observation=None,
            unbound_handoff_seen=False,
            watchdog_runtime_directory_descriptor=mock.ANY,
        )
        self.assertFalse(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).exists()
        )
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        retained_logs = list(runtime.glob(".forge-server-gradle.*.log"))
        self.assertEqual(1, len(retained_logs))
        self.assertEqual(0, retained_logs[0].stat().st_size)
        self.assert_launch_attempt_is_exact()


class CheckCommandTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository_context = temporary_repository()
        self.repository_root, self.manifest_path = self.repository_context.__enter__()
        self.configuration = load_temporary_configuration(
            self.repository_root,
            self.manifest_path,
        )
        self.state_context = tempfile.TemporaryDirectory()
        self.state_root = Path(self.state_context.name).resolve() / ".state"
        forge_server.provision_profile(self.configuration, self.state_root)

    def tearDown(self) -> None:
        self.state_context.cleanup()
        self.repository_context.__exit__(None, None, None)

    def test_success_releases_and_closes_run_lock(self) -> None:
        with (
            mock.patch.object(
                forge_server,
                "load_configuration",
                return_value=self.configuration,
            ),
            mock.patch.object(forge_server, "STATE_ROOT", self.state_root),
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["gradle"]),
            ),
            mock.patch.object(
                forge_server,
                "release_owned_run_lock",
                wraps=forge_server.release_owned_run_lock,
            ) as release_lock,
            mock.patch.object(
                forge_server,
                "close_owned_run_lock",
                wraps=forge_server.close_owned_run_lock,
            ) as close_lock,
        ):
            self.assertEqual(0, forge_server.check_command())

        release_lock.assert_called_once()
        close_lock.assert_called_once()
        self.assertFalse(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).exists()
        )

    def test_ordinary_failure_releases_and_closes_run_lock(self) -> None:
        with (
            mock.patch.object(
                forge_server,
                "load_configuration",
                return_value=self.configuration,
            ),
            mock.patch.object(forge_server, "STATE_ROOT", self.state_root),
            mock.patch.object(
                forge_server,
                "verify_environment",
                side_effect=forge_server.E2EError("check failed"),
            ),
            mock.patch.object(
                forge_server,
                "release_owned_run_lock",
                wraps=forge_server.release_owned_run_lock,
            ) as release_lock,
            mock.patch.object(
                forge_server,
                "close_owned_run_lock",
                wraps=forge_server.close_owned_run_lock,
            ) as close_lock,
            self.assertRaisesRegex(forge_server.E2EError, "check failed"),
        ):
            forge_server.check_command()

        release_lock.assert_called_once()
        close_lock.assert_called_once()
        self.assertFalse(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).exists()
        )

    def test_cleanup_uncertainty_retains_lock_and_closes_descriptors(self) -> None:
        with (
            mock.patch.object(
                forge_server,
                "load_configuration",
                return_value=self.configuration,
            ),
            mock.patch.object(forge_server, "STATE_ROOT", self.state_root),
            mock.patch.object(
                forge_server,
                "verify_environment",
                side_effect=forge_server.CleanupUncertainError(
                    "check cleanup uncertain"
                ),
            ),
            mock.patch.object(
                forge_server,
                "release_owned_run_lock",
                wraps=forge_server.release_owned_run_lock,
            ) as release_lock,
            mock.patch.object(
                forge_server,
                "close_owned_run_lock",
                wraps=forge_server.close_owned_run_lock,
            ) as close_lock,
            self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "check cleanup uncertain",
            ),
        ):
            forge_server.check_command()

        release_lock.assert_not_called()
        close_lock.assert_called_once()
        self.assertTrue(
            forge_server.run_lock_path(
                self.configuration,
                self.state_root,
            ).is_file()
        )


if __name__ == "__main__":
    unittest.main()
