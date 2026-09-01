from __future__ import annotations

import copy
from dataclasses import dataclass
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import time
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_server_contract_v18 as contract_v18
import forge_server_attrahite_evidence_v18 as evidence


@dataclass(frozen=True)
class Fixture:
    root: Path
    profile: Path
    runtime: Path
    game: Path
    scenario: Path


def write_json(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def rewrite_json_preserving_time(path: Path, value: dict[str, object]) -> None:
    metadata = path.stat()
    write_json(path, value)
    os.utime(path, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))


def valid_report() -> dict[str, object]:
    assertions = [
        {
            "name": name,
            "passed": True,
            "expected": value,
            "actual": value,
        }
        for name, value in evidence.EXPECTED_ASSERTIONS
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
        + [contract_v18.RELOAD_PACK_ENABLED_NAME]
    )
    return {
        "schema": contract_v18.REPORT_SCHEMA,
        "profile_id": evidence.PROFILE_ID,
        "scenario": evidence.SCENARIO_ID,
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
                for mod_id in evidence.REQUIRED_MOD_IDS
            },
            **{
                mod_id: {"loaded": False}
                for mod_id in evidence.FORBIDDEN_MOD_IDS
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
            "registry_id": contract_v18.ENCHANTMENT_REGISTRY_ID,
            "non_treasure_tag_id": contract_v18.NON_TREASURE_TAG_ID,
            "etherology_enchantment_ids": list(contract_v18.ENCHANTMENT_IDS),
            "peal": copy.deepcopy(contract_v18.ENCHANTMENTS["peal"]),
            "reflection": copy.deepcopy(
                contract_v18.ENCHANTMENTS["reflection"]
            ),
            "non_treasure_etherology_enchantment_ids": list(
                contract_v18.ENCHANTMENT_IDS
            ),
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "properties_stable_after_reload": True,
            "tag_stable_after_reload": True,
        },
        "particles": {
            "registry_id": contract_v18.PARTICLE_REGISTRY_ID,
            "capture_error": "",
            "etherology_particle_ids": list(contract_v18.PARTICLE_IDS),
            "payload_families": list(
                contract_v18.PARTICLE_PAYLOAD_FAMILIES
            ),
            "entries": copy.deepcopy(contract_v18.PARTICLES),
            "seal_types": {
                "order": list(contract_v18.SEAL_TYPE_ORDER),
                "codec_round_trips_exact": True,
                "entries": copy.deepcopy(contract_v18.SEAL_TYPES),
            },
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "type_contract_stable_after_reload": True,
            "wire_contract_stable_after_reload": True,
        },
        "material_items": {
            "registry_id": contract_v18.MATERIAL_ITEM_REGISTRY_ID,
            "capture_error": "",
            "material_item_ids": list(contract_v18.MATERIAL_ITEM_IDS),
            "vanilla_item_class": contract_v18.MATERIAL_ITEM_CLASS,
            "max_counts": contract_v18.MATERIAL_ITEM_CANONICAL_MAX_COUNTS,
            "save_representations": (
                contract_v18.MATERIAL_ITEM_CANONICAL_SAVE_REPRESENTATIONS
            ),
            "entries": copy.deepcopy(contract_v18.MATERIAL_ITEMS),
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "properties_stable_after_reload": True,
            "stack_nbt_stable_after_reload": True,
        },
        "metal_blocks": {
            "block_registry_id": contract_v18.METAL_BLOCK_REGISTRY_ID,
            "item_registry_id": contract_v18.METAL_BLOCK_ITEM_REGISTRY_ID,
            "capture_error": "",
            "metal_block_ids": list(contract_v18.METAL_BLOCK_IDS),
            "metal_block_item_ids": list(contract_v18.METAL_BLOCK_IDS),
            "vanilla_block_class": contract_v18.METAL_BLOCK_CLASS,
            "block_item_class": contract_v18.BLOCK_ITEM_CLASS,
            "properties": contract_v18.METAL_BLOCK_CANONICAL_PROPERTIES,
            "save_representations": (
                contract_v18.METAL_BLOCK_CANONICAL_SAVE_REPRESENTATIONS
            ),
            "entries": copy.deepcopy(contract_v18.METAL_BLOCKS),
            "placement": {
                "capture_error": "",
                "positions": copy.deepcopy(
                    contract_v18.METAL_BLOCK_PLACEMENT_POSITIONS
                ),
                "placed_block_ids": {
                    identifier: identifier
                    for identifier in contract_v18.METAL_BLOCK_IDS
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
        "attrahite_blocks": copy.deepcopy(contract_v18.ATTRAHITE_BLOCKS),
        "food_items": {
            "registry_id": contract_v18.FOOD_ITEM_REGISTRY_ID,
            "capture_error": "",
            "food_item_ids": list(contract_v18.FOOD_ITEM_IDS),
            "vanilla_item_class": contract_v18.FOOD_ITEM_CLASS,
            "properties": contract_v18.FOOD_ITEM_PROPERTIES,
            "save_representations": (
                contract_v18.FOOD_ITEM_SAVE_REPRESENTATIONS
            ),
            "entries": copy.deepcopy(contract_v18.FOOD_ITEMS),
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "properties_stable_after_reload": True,
            "stack_nbt_stable_after_reload": True,
        },
        "food_consumption": {
            "server_started": copy.deepcopy(
                contract_v18.SERVER_STARTED_FOOD_CONSUMPTION
            ),
            "reloaded": copy.deepcopy(
                contract_v18.RELOADED_FOOD_CONSUMPTION
            ),
            "fresh_player_after_reload": True,
            "stable_after_reload": True,
        },
        "forest_lantern": copy.deepcopy(contract_v18.FOREST_LANTERN),
        "loot_condition": {
            "registry_id": "minecraft:loot_condition_type",
            "condition_id": "etherology:random_chance_with_fortune",
            "etherology_condition_ids": [
                "etherology:random_chance_with_fortune"
            ],
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
            "listener_class": contract_v18.ETHER_SOURCE_LISTENER_CLASS,
            "resource_directory": "ether_sources",
            "initial": {
                "capture_error": "",
                "entries": copy.deepcopy(
                    contract_v18.INITIAL_ETHER_SOURCE_ENTRIES
                ),
            },
            "server_started": {
                "capture_error": "",
                "entries": copy.deepcopy(
                    contract_v18.INITIAL_ETHER_SOURCE_ENTRIES
                ),
            },
            "reloaded": {
                "capture_error": "",
                "entries": copy.deepcopy(
                    contract_v18.RELOADED_ETHER_SOURCE_ENTRIES
                ),
            },
            "same_at_server_started": True,
            "changed_after_reload": True,
        },
        "reload": {
            "pack_directory": contract_v18.RELOAD_PACK_DIRECTORY,
            "pack_resources": list(contract_v18.RELOAD_PACK_RESOURCES),
            "enabled_pack_name": contract_v18.RELOAD_PACK_ENABLED_NAME,
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
        "lifecycle": list(contract_v18.EXPECTED_LIFECYCLE),
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
        "[LanServerPinger #1/WARN] "
        "[net.minecraft.client.network.LanServerPinger/] No route to host",
        "[Server thread/INFO] [EtherologyServerProbe] server_stopping",
        "[Server thread/INFO] Stopping server",
        "[Server thread/INFO] Saving worlds",
        "[Server thread/INFO] All dimensions are saved",
        "[Server thread/INFO] [EtherologyServerProbe] server_stopped",
        "[Server thread/INFO] [EtherologyServerProbe] report_published",
        "[etherology-e2e-server-probe-exit/INFO] "
        + evidence.TERMINATION_LOG_TOKEN,
    ]
    return ("\n".join(lines) + "\n").encode("utf-8")


def build_fixture(root: Path) -> Fixture:
    profile = root / "repository" / evidence.PROFILE_MANIFEST_RELATIVE_PATH
    profile.parent.mkdir(parents=True, exist_ok=True)
    profile_snapshot = (
        evidence.REPOSITORY_ROOT / contract_v18.PROFILE_SNAPSHOT_RELATIVE_PATH
    )
    profile.write_bytes(profile_snapshot.read_bytes())
    profile_record = evidence.ProfileRecord(
        relative_path=evidence.PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
        size=profile.stat().st_size,
        sha256=evidence.sha256_file(profile),
    )

    runtime = root / "repository" / evidence.RUNTIME_RELATIVE_PATH
    game = runtime / "game"
    for relative_directory in (
        "config",
        "crash-reports",
        "logs",
        "mods",
        "world",
    ):
        (game / relative_directory).mkdir(parents=True, exist_ok=True)
    (game / "eula.txt").write_bytes(b"eula=true\n")
    (game / "server.properties").write_bytes(b"level-name=world\n")
    (game / "world" / "level.dat").write_bytes(b"synthetic-level-data")
    log_content = valid_server_log()
    (game / "logs" / "latest.log").write_bytes(log_content)
    write_json(
        runtime / evidence.PROFILE_MARKER_NAME,
        evidence.expected_profile_marker(profile_record),
    )

    scenario = runtime / "evidence" / evidence.SCENARIO_ID
    reports = scenario / "reports"
    logs = scenario / "logs"
    reports.mkdir(parents=True)
    logs.mkdir()
    report_path = reports / "report.json"
    log_path = logs / "latest.log"
    launcher_path = reports / "launcher-result.json"
    done_path = reports / "done.marker"
    write_json(report_path, valid_report())
    log_path.write_bytes(log_content)
    write_json(
        launcher_path,
        {
            "schema": 1,
            "profile_id": evidence.PROFILE_ID,
            "scenario": evidence.SCENARIO_ID,
            "task_path": evidence.TASK_PATH,
            "exit_code": 0,
            "timed_out": False,
            "profile_manifest": {
                "relative_path": profile_record.relative_path,
                "size": profile_record.size,
                "sha256": profile_record.sha256,
            },
            "server_log": {
                "relative_path": "logs/latest.log",
                "size": log_path.stat().st_size,
                "sha256": evidence.sha256_file(log_path),
            },
        },
    )
    done_path.write_bytes(b"complete\n")

    base_time = time.time_ns() - 10_000_000_000
    for index, path in enumerate(
        (report_path, log_path, launcher_path, done_path),
        start=1,
    ):
        timestamp = base_time + (index * 1_000_000_000)
        os.utime(path, ns=(timestamp, timestamp))
    return Fixture(root, profile, runtime, game, scenario)


def create_archive(fixture: Fixture) -> Path:
    archive = fixture.root / "repository" / evidence.ARCHIVE_RELATIVE_PATH
    archive.parent.mkdir(parents=True)
    shutil.copytree(fixture.scenario, archive, copy_function=shutil.copy2)
    evidence.write_archive_manifest(
        fixture.profile,
        archive,
        expected_archive_root=archive,
    )
    return archive


def mutate_json(path: Path, mutation) -> None:
    value = json.loads(path.read_text(encoding="utf-8"))
    mutation(value)
    rewrite_json_preserving_time(path, value)


def reseal_archive_record(archive: Path, relative_path: str) -> None:
    manifest_path = archive / evidence.ARCHIVE_MANIFEST_NAME
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    payload_path = archive / relative_path
    manifest["files"][relative_path] = {
        "size": payload_path.stat().st_size,
        "sha256": evidence.sha256_file(payload_path),
    }
    write_json(manifest_path, manifest)


def reseal_archive_log_and_launcher(archive: Path) -> None:
    log_path = archive / "logs" / "latest.log"
    launcher_path = archive / "reports" / "launcher-result.json"
    launcher = json.loads(launcher_path.read_text(encoding="utf-8"))
    launcher["server_log"] = {
        "relative_path": "logs/latest.log",
        "size": log_path.stat().st_size,
        "sha256": evidence.sha256_file(log_path),
    }
    write_json(launcher_path, launcher)
    reseal_archive_record(archive, "logs/latest.log")
    reseal_archive_record(archive, "reports/launcher-result.json")


class ContractOwnershipTests(unittest.TestCase):
    def test_v18_archive_contract_does_not_import_the_mutable_runner(self) -> None:
        evidence_source = Path(evidence.__file__).read_text(encoding="utf-8")
        contract_source = Path(contract_v18.__file__).read_text(encoding="utf-8")

        self.assertIn(
            "import forge_server_contract_v18 as contract_v18",
            evidence_source,
        )
        self.assertNotRegex(evidence_source, r"(?m)^import forge_server$")
        self.assertNotRegex(contract_source, r"(?m)^import forge_server$")
        self.assertEqual(310, len(contract_v18.EXPECTED_ASSERTION_NAMES))
        self.assertEqual(310, len(contract_v18.EXPECTED_ASSERTION_VALUES))
        self.assertEqual(
            Path(
                "docs/evidence/forge-1.20.1/"
                "attrahite-block-registry-server-v18"
            ),
            evidence.ARCHIVE_RELATIVE_PATH,
        )
        self.assertEqual(
            "attrahite-block-registry-server-v18",
            evidence.ARCHIVE_DIRECTORY_NAME,
        )
        snapshot = (
            evidence.REPOSITORY_ROOT / contract_v18.PROFILE_SNAPSHOT_RELATIVE_PATH
        )
        self.assertEqual(
            contract_v18.PROFILE_MANIFEST_SIZE,
            snapshot.stat().st_size,
        )
        self.assertEqual(
            contract_v18.PROFILE_MANIFEST_SHA256,
            evidence.sha256_file(snapshot),
        )
        self.assertEqual(
            {"enum_name", "as_string"},
            set(contract_v18.SEAL_TYPES["empty"]),
        )
        for seal_name in ("keta", "rella", "via", "clos"):
            self.assertEqual(
                {
                    "enum_name",
                    "as_string",
                    "start_color",
                    "end_color",
                    "texture_id",
                    "texture_light_id",
                },
                set(contract_v18.SEAL_TYPES[seal_name]),
            )
        self.assertEqual(14, len(contract_v18.MATERIAL_ITEM_IDS))
        self.assertEqual(
            16,
            contract_v18.MATERIAL_ITEM_MAX_COUNTS[
                "etherology:enriched_attrahite"
            ],
        )
        self.assertTrue(
            all(
                max_count == 64
                for identifier, max_count in (
                    contract_v18.MATERIAL_ITEM_MAX_COUNTS.items()
                )
                if identifier != "etherology:enriched_attrahite"
            )
        )
        self.assertEqual(
            (
                "etherology:azel_block",
                "etherology:ebony_block",
                "etherology:ethril_block",
            ),
            contract_v18.METAL_BLOCK_IDS,
        )
        self.assertEqual(
            {
                "hardness": 5.0,
                "blast_resistance": 6.0,
                "map_color_id": 32,
                "beacon_base": False,
            },
            contract_v18.METAL_BLOCK_SPECS["etherology:azel_block"],
        )
        self.assertEqual(
            {
                "hardness": 5.0,
                "blast_resistance": 6.0,
                "map_color_id": 15,
                "beacon_base": True,
            },
            contract_v18.METAL_BLOCK_SPECS["etherology:ebony_block"],
        )
        self.assertEqual(
            {
                "hardness": 3.0,
                "blast_resistance": 6.0,
                "map_color_id": 30,
                "beacon_base": True,
            },
            contract_v18.METAL_BLOCK_SPECS["etherology:ethril_block"],
        )
        self.assertEqual(
            {
                "etherology:azel_block": "8,200,8",
                "etherology:ebony_block": "9,200,8",
                "etherology:ethril_block": "10,200,8",
            },
            contract_v18.METAL_BLOCK_PLACEMENT_POSITIONS,
        )
        self.assertEqual(
            ("etherology:forest_lantern_crumb",),
            contract_v18.FOOD_ITEM_IDS,
        )
        self.assertEqual(
            {
                "id": "etherology:forest_lantern_crumb",
                "runtime_class": "net.minecraft.item.Item",
                "max_count": 64,
                "is_food": True,
                "hunger": 3,
                "saturation_modifier": 2.0,
                "always_edible": False,
                "status_effect_count": 0,
                "has_recipe_remainder": False,
                "recipe_remainder_id": "",
                "serialized_id": "etherology:forest_lantern_crumb",
                "serialized_count": 64,
                "serialized_keys": ["Count", "id"],
                "round_trip_exact": True,
                "save_representation": (
                    "etherology:forest_lantern_crumb"
                    "|class=net.minecraft.item.Item|max=64"
                    "|nbt_id=etherology:forest_lantern_crumb"
                    "|nbt_count=64|nbt_keys=Count+id"
                ),
            },
            contract_v18.FOOD_ITEMS["etherology:forest_lantern_crumb"],
        )
        self.assertEqual(
            "EtherFoodStart",
            contract_v18.SERVER_STARTED_FOOD_PLAYER_NAME,
        )
        self.assertEqual(
            "EtherFoodReload",
            contract_v18.RELOADED_FOOD_PLAYER_NAME,
        )
        self.assertEqual(4, len(contract_v18.ATTRAHITE_BLOCK_IDS))
        self.assertEqual(4, len(contract_v18.ATTRAHITE_LOOT_TABLE_IDS))
        self.assertEqual(9, len(contract_v18.ATTRAHITE_RECIPE_IDS))
        self.assertEqual(9, len(contract_v18.ATTRAHITE_ADVANCEMENT_IDS))
        self.assertEqual(44, len(contract_v18.ATTRAHITE_ASSERTION_NAMES))
        self.assertEqual(44, len(contract_v18.ATTRAHITE_ASSERTION_VALUES))
        self.assertEqual(
            contract_v18.ATTRAHITE_BLOCKS,
            valid_report()["attrahite_blocks"],
        )

    def test_v18_projects_the_exact_v16_cumulative_baseline(self) -> None:
        report = valid_report()

        with mock.patch.object(
            contract_v18.contract_v16,
            "validate_probe_report",
        ) as validate_v16:
            contract_v18.validate_probe_report(
                report,
                list(evidence.REQUIRED_MOD_IDS),
                list(evidence.FORBIDDEN_MOD_IDS),
            )

        validate_v16.assert_called_once()
        baseline = validate_v16.call_args.args[0]
        self.assertEqual(contract_v18.contract_v16.REPORT_SCHEMA, baseline["schema"])
        self.assertEqual(contract_v18.contract_v16.PROFILE_ID, baseline["profile_id"])
        self.assertEqual(contract_v18.contract_v16.SCENARIO_ID, baseline["scenario"])
        self.assertNotIn("attrahite_blocks", baseline)
        self.assertEqual(
            list(contract_v18.contract_v16.EXPECTED_ASSERTION_NAMES),
            [assertion["name"] for assertion in baseline["assertions"]],
        )
        for field in (
            "attrahite_block_registry_stable",
            "attrahite_block_properties_stable",
            "attrahite_block_tags_stable",
            "attrahite_block_stack_nbt_stable",
            "attrahite_block_loaded_data_stable",
            "attrahite_block_loaded_data_fresh",
            "attrahite_block_placement_stable",
        ):
            self.assertNotIn(field, baseline["reload"])


class LiveEvidenceTests(unittest.TestCase):
    def test_exact_live_runtime_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))

            summary = evidence.validate_live_runtime(
                fixture.runtime,
                fixture.profile,
                expected_runtime=fixture.runtime,
            )

            self.assertEqual(evidence.PROFILE_ID, summary.profile_id)
            self.assertEqual(310, summary.assertion_count)
            self.assertEqual(
                evidence.sha256_file(fixture.scenario / "logs" / "latest.log"),
                summary.log_sha256,
            )

    def test_profile_kind_forbidden_inventory_and_marker_are_exact(self) -> None:
        cases = (
            (
                "profile kind",
                lambda fixture: mutate_json(
                    fixture.profile,
                    lambda profile: profile["launch"].__setitem__(
                        "kind", "packaged-jar"
                    ),
                ),
                "launch identity",
            ),
            (
                "forbidden inventory",
                lambda fixture: mutate_json(
                    fixture.profile,
                    lambda profile: profile["forbidden_mod_ids"].remove(
                        "etherology_e2e_harness"
                    ),
                ),
                "forbidden mod inventory",
            ),
            (
                "ownership marker",
                lambda fixture: mutate_json(
                    fixture.runtime / evidence.PROFILE_MARKER_NAME,
                    lambda marker: marker.__setitem__("profile_id", "foreign"),
                ),
                "runtime marker",
            ),
        )
        for name, mutation, message in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                mutation(fixture)

                with self.assertRaisesRegex(evidence.EvidenceError, message):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_registry_enchantment_and_particle_contracts_are_exact(self) -> None:
        cases = (
            ("registry_id", "minecraft:sound_event"),
            ("event_id", "etherology:resonance"),
            ("internal_id", "resonance"),
            ("range", 15),
            (
                "etherology_event_ids",
                [
                    "etherology:etherology_resonance",
                    "etherology:unexpected",
                ],
            ),
            ("same_instance_at_server_started", False),
            ("stable_after_reload", False),
        )
        for field, replacement in cases:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: report["registry"].__setitem__(
                        field, replacement
                    ),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        particle_cases = (
            (
                "registry id",
                lambda particles: particles.__setitem__(
                    "registry_id", "minecraft:item"
                ),
            ),
            (
                "exact namespace",
                lambda particles: particles["etherology_particle_ids"].append(
                    "etherology:unexpected"
                ),
            ),
            (
                "payload families",
                lambda particles: particles["payload_families"].remove(
                    "electricity"
                ),
            ),
            (
                "type class",
                lambda particles: particles["entries"]["alchemy"].__setitem__(
                    "type_class", "java.lang.Object"
                ),
            ),
            (
                "spawn policy",
                lambda particles: particles["entries"]["alchemy"].__setitem__(
                    "should_always_spawn", True
                ),
            ),
            (
                "parameters factory",
                lambda particles: particles["entries"]["item"].__setitem__(
                    "parameters_factory_present", False
                ),
            ),
            (
                "effect class",
                lambda particles: particles["entries"]["light"].__setitem__(
                    "factory_sample_effect_class", "java.lang.Object"
                ),
            ),
            (
                "canonical sample",
                lambda particles: particles["entries"]["spark"].__setitem__(
                    "factory_sample_as_string", "etherology:spark wrong"
                ),
            ),
            (
                "packet round trip",
                lambda particles: particles["entries"]["seal"].__setitem__(
                    "packet_round_trip_exact", False
                ),
            ),
            (
                "codec round trip",
                lambda particles: particles["entries"]["electricity2"].__setitem__(
                    "codec_round_trip_exact", False
                ),
            ),
            (
                "SealType order",
                lambda particles: particles["seal_types"]["order"].reverse(),
            ),
            (
                "SealType codec",
                lambda particles: particles["seal_types"].__setitem__(
                    "codec_round_trips_exact", False
                ),
            ),
            (
                "SealType color",
                lambda particles: particles["seal_types"]["entries"][
                    "keta"
                ].__setitem__("start_color", "0,0,0"),
            ),
            (
                "SealType texture",
                lambda particles: particles["seal_types"]["entries"][
                    "clos"
                ].__setitem__("texture_id", None),
            ),
            (
                "server-start identity",
                lambda particles: particles.__setitem__(
                    "same_state_at_server_started", False
                ),
            ),
            (
                "reload registry stability",
                lambda particles: particles.__setitem__(
                    "registry_stable_after_reload", False
                ),
            ),
            (
                "reload type stability",
                lambda particles: particles.__setitem__(
                    "type_contract_stable_after_reload", False
                ),
            ),
            (
                "reload wire stability",
                lambda particles: particles.__setitem__(
                    "wire_contract_stable_after_reload", False
                ),
            ),
        )
        for name, mutation in particle_cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: mutation(report["particles"]),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        enchantment_cases = (
            (
                "registry id",
                lambda enchantments: enchantments.__setitem__(
                    "registry_id", "minecraft:item"
                ),
            ),
            (
                "singular tag id",
                lambda enchantments: enchantments.__setitem__(
                    "non_treasure_tag_id", "minecraft:enchantments/non_treasure"
                ),
            ),
            (
                "exact namespace",
                lambda enchantments: enchantments[
                    "etherology_enchantment_ids"
                ].append("etherology:unexpected"),
            ),
            (
                "peal class",
                lambda enchantments: enchantments["peal"].__setitem__(
                    "class", "java.lang.Object"
                ),
            ),
            (
                "peal levels",
                lambda enchantments: enchantments["peal"][
                    "max_powers"
                ].__setitem__(2, 42),
            ),
            (
                "reflection levels",
                lambda enchantments: enchantments["reflection"].__setitem__(
                    "min_powers", [11]
                ),
            ),
            (
                "tag membership",
                lambda enchantments: enchantments["reflection"].__setitem__(
                    "in_non_treasure", False
                ),
            ),
            (
                "exact tag namespace",
                lambda enchantments: enchantments[
                    "non_treasure_etherology_enchantment_ids"
                ].remove("etherology:reflection"),
            ),
            (
                "server-start identity",
                lambda enchantments: enchantments.__setitem__(
                    "same_state_at_server_started", False
                ),
            ),
            (
                "reload registry stability",
                lambda enchantments: enchantments.__setitem__(
                    "registry_stable_after_reload", False
                ),
            ),
            (
                "reload property stability",
                lambda enchantments: enchantments.__setitem__(
                    "properties_stable_after_reload", False
                ),
            ),
            (
                "reload tag stability",
                lambda enchantments: enchantments.__setitem__(
                    "tag_stable_after_reload", False
                ),
            ),
        )
        for name, mutation in enchantment_cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: mutation(report["enchantments"]),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_material_item_registry_stack_and_reload_contracts_are_exact(
        self,
    ) -> None:
        cases = (
            (
                "registry id",
                lambda material_items: material_items.__setitem__(
                    "registry_id", "minecraft:block"
                ),
            ),
            (
                "capture error",
                lambda material_items: material_items.__setitem__(
                    "capture_error", "java.lang.IllegalStateException"
                ),
            ),
            (
                "exact id order",
                lambda material_items: material_items[
                    "material_item_ids"
                ].reverse(),
            ),
            (
                "vanilla runtime class",
                lambda material_items: material_items.__setitem__(
                    "vanilla_item_class", "java.lang.Object"
                ),
            ),
            (
                "canonical max counts",
                lambda material_items: material_items.__setitem__(
                    "max_counts", "etherology:enriched_attrahite=64"
                ),
            ),
            (
                "canonical save representations",
                lambda material_items: material_items.__setitem__(
                    "save_representations", "tampered"
                ),
            ),
            (
                "entry runtime class",
                lambda material_items: material_items["entries"][
                    "etherology:etheroscope"
                ].__setitem__("runtime_class", "java.lang.Object"),
            ),
            (
                "enriched stack maximum",
                lambda material_items: material_items["entries"][
                    "etherology:enriched_attrahite"
                ].__setitem__("max_count", 64),
            ),
            (
                "ordinary stack maximum",
                lambda material_items: material_items["entries"][
                    "etherology:azel_ingot"
                ].__setitem__("max_count", 16),
            ),
            (
                "serialized registry id",
                lambda material_items: material_items["entries"][
                    "etherology:binder"
                ].__setitem__("serialized_id", "minecraft:air"),
            ),
            (
                "serialized stack count",
                lambda material_items: material_items["entries"][
                    "etherology:ebony"
                ].__setitem__("serialized_count", 1),
            ),
            (
                "serialized NBT keys",
                lambda material_items: material_items["entries"][
                    "etherology:raw_azel"
                ]["serialized_keys"].reverse(),
            ),
            (
                "NBT round trip",
                lambda material_items: material_items["entries"][
                    "etherology:resonating_wand"
                ].__setitem__("round_trip_exact", False),
            ),
            (
                "deterministic save representation",
                lambda material_items: material_items["entries"][
                    "etherology:thuja_oil"
                ].__setitem__("save_representation", "tampered"),
            ),
            (
                "server-start registry identity",
                lambda material_items: material_items.__setitem__(
                    "same_state_at_server_started", False
                ),
            ),
            (
                "reload registry identity",
                lambda material_items: material_items.__setitem__(
                    "registry_stable_after_reload", False
                ),
            ),
            (
                "reload properties",
                lambda material_items: material_items.__setitem__(
                    "properties_stable_after_reload", False
                ),
            ),
            (
                "reload stack NBT",
                lambda material_items: material_items.__setitem__(
                    "stack_nbt_stable_after_reload", False
                ),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: mutation(report["material_items"]),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            report_path = fixture.scenario / "reports" / "report.json"
            assertion_index = contract_v18.EXPECTED_ASSERTION_NAMES.index(
                "material_item_stack_nbt_round_trips_exact"
            )
            mutate_json(
                report_path,
                lambda report: report["assertions"][assertion_index].__setitem__(
                    "actual", "false"
                ),
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "assertion"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_metal_block_registry_mapping_properties_tags_nbt_and_placement_are_exact(
        self,
    ) -> None:
        cases = (
            (
                "block registry id",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "block_registry_id", "minecraft:item"
                ),
            ),
            (
                "item registry id",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "item_registry_id", "minecraft:block"
                ),
            ),
            (
                "capture error",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "capture_error", "java.lang.IllegalStateException"
                ),
            ),
            (
                "exact block id order",
                lambda metal_blocks: metal_blocks["metal_block_ids"].reverse(),
            ),
            (
                "exact BlockItem id order",
                lambda metal_blocks: metal_blocks[
                    "metal_block_item_ids"
                ].reverse(),
            ),
            (
                "vanilla Block class",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "vanilla_block_class", "java.lang.Object"
                ),
            ),
            (
                "BlockItem class",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "block_item_class", "net.minecraft.item.Item"
                ),
            ),
            (
                "canonical properties",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "properties", "tampered"
                ),
            ),
            (
                "canonical save representations",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "save_representations", "tampered"
                ),
            ),
            (
                "entry block id",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:azel_block"
                ].__setitem__("block_id", "etherology:ebony_block"),
            ),
            (
                "entry item id",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:azel_block"
                ].__setitem__("item_id", "minecraft:air"),
            ),
            (
                "entry runtime class",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ethril_block"
                ].__setitem__("block_class", "java.lang.Object"),
            ),
            (
                "entry BlockItem class",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ethril_block"
                ].__setitem__("item_class", "net.minecraft.item.Item"),
            ),
            (
                "BlockItem mapping",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:azel_block"
                ].__setitem__("block_item_maps_to_block", False),
            ),
            (
                "Block asItem mapping",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:azel_block"
                ].__setitem__("block_as_item_matches", False),
            ),
            (
                "hardness",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ethril_block"
                ].__setitem__("hardness", 5.0),
            ),
            (
                "blast resistance",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ebony_block"
                ].__setitem__("blast_resistance", 5.0),
            ),
            (
                "map color",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:azel_block"
                ].__setitem__("map_color_id", 30),
            ),
            (
                "metal sound",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ebony_block"
                ].__setitem__("metal_sound_group", False),
            ),
            (
                "tool required",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ethril_block"
                ].__setitem__("tool_required", False),
            ),
            (
                "pickaxe tag",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:azel_block"
                ].__setitem__("pickaxe_mineable", False),
            ),
            (
                "iron-tool tag",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ebony_block"
                ].__setitem__("needs_iron_tool", False),
            ),
            (
                "beacon tag",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:azel_block"
                ].__setitem__("beacon_base", True),
            ),
            (
                "serialized id",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ebony_block"
                ].__setitem__("serialized_id", "minecraft:air"),
            ),
            (
                "serialized count",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ebony_block"
                ].__setitem__("serialized_count", 1),
            ),
            (
                "serialized NBT keys",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ethril_block"
                ]["serialized_keys"].reverse(),
            ),
            (
                "NBT round trip",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:azel_block"
                ].__setitem__("round_trip_exact", False),
            ),
            (
                "deterministic save representation",
                lambda metal_blocks: metal_blocks["entries"][
                    "etherology:ethril_block"
                ].__setitem__("save_representation", "tampered"),
            ),
            (
                "placement position",
                lambda metal_blocks: metal_blocks["placement"][
                    "positions"
                ].__setitem__("etherology:azel_block", "0,0,0"),
            ),
            (
                "placed id",
                lambda metal_blocks: metal_blocks["placement"][
                    "placed_block_ids"
                ].__setitem__("etherology:ebony_block", "minecraft:air"),
            ),
            (
                "placement exact",
                lambda metal_blocks: metal_blocks["placement"].__setitem__(
                    "exact", False
                ),
            ),
            (
                "placement reload stability",
                lambda metal_blocks: metal_blocks["placement"].__setitem__(
                    "stable_after_reload", False
                ),
            ),
            (
                "server-start identity",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "same_state_at_server_started", False
                ),
            ),
            (
                "reload registry",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "registry_stable_after_reload", False
                ),
            ),
            (
                "reload properties",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "properties_stable_after_reload", False
                ),
            ),
            (
                "reload tags",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "tags_stable_after_reload", False
                ),
            ),
            (
                "reload stack NBT",
                lambda metal_blocks: metal_blocks.__setitem__(
                    "stack_nbt_stable_after_reload", False
                ),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: mutation(report["metal_blocks"]),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        reload_fields = (
            "metal_block_registry_stable",
            "metal_block_properties_stable",
            "metal_block_tags_stable",
            "metal_block_stack_nbt_stable",
            "metal_block_placement_stable",
        )
        for field in reload_fields:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: report["reload"].__setitem__(field, False),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            report_path = fixture.scenario / "reports" / "report.json"
            assertion_index = contract_v18.EXPECTED_ASSERTION_NAMES.index(
                "metal_block_placement_stable_after_reload"
            )
            mutate_json(
                report_path,
                lambda report: report["assertions"][assertion_index].__setitem__(
                    "actual", "false"
                ),
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "assertion"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_food_item_registry_properties_nbt_and_reload_are_exact(
        self,
    ) -> None:
        item_id = contract_v18.FOOD_ITEM_ID
        cases = (
            (
                "registry id",
                lambda food_items: food_items.__setitem__(
                    "registry_id", "minecraft:block"
                ),
            ),
            (
                "capture error",
                lambda food_items: food_items.__setitem__(
                    "capture_error", "java.lang.IllegalStateException"
                ),
            ),
            (
                "exact id inventory",
                lambda food_items: food_items["food_item_ids"].append(
                    "etherology:unexpected"
                ),
            ),
            (
                "vanilla runtime class",
                lambda food_items: food_items.__setitem__(
                    "vanilla_item_class", "java.lang.Object"
                ),
            ),
            (
                "canonical properties",
                lambda food_items: food_items.__setitem__(
                    "properties", "tampered"
                ),
            ),
            (
                "canonical save representations",
                lambda food_items: food_items.__setitem__(
                    "save_representations", "tampered"
                ),
            ),
            (
                "entry id",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "id", "minecraft:air"
                ),
            ),
            (
                "entry runtime class",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "runtime_class", "java.lang.Object"
                ),
            ),
            (
                "stack maximum",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "max_count", 16
                ),
            ),
            (
                "food flag exact boolean",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "is_food", 1
                ),
            ),
            (
                "hunger",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "hunger", 4
                ),
            ),
            (
                "saturation modifier",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "saturation_modifier", 1.0
                ),
            ),
            (
                "always edible",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "always_edible", True
                ),
            ),
            (
                "status effects",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "status_effect_count", 1
                ),
            ),
            (
                "recipe remainder presence",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "has_recipe_remainder", True
                ),
            ),
            (
                "recipe remainder id",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "recipe_remainder_id", "minecraft:bowl"
                ),
            ),
            (
                "serialized registry id",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "serialized_id", "minecraft:air"
                ),
            ),
            (
                "serialized stack count",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "serialized_count", 1
                ),
            ),
            (
                "serialized NBT keys",
                lambda food_items: food_items["entries"][item_id][
                    "serialized_keys"
                ].reverse(),
            ),
            (
                "NBT round trip",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "round_trip_exact", False
                ),
            ),
            (
                "deterministic save representation",
                lambda food_items: food_items["entries"][item_id].__setitem__(
                    "save_representation", "tampered"
                ),
            ),
            (
                "server-start identity",
                lambda food_items: food_items.__setitem__(
                    "same_state_at_server_started", False
                ),
            ),
            (
                "reload registry",
                lambda food_items: food_items.__setitem__(
                    "registry_stable_after_reload", False
                ),
            ),
            (
                "reload properties",
                lambda food_items: food_items.__setitem__(
                    "properties_stable_after_reload", False
                ),
            ),
            (
                "reload stack NBT",
                lambda food_items: food_items.__setitem__(
                    "stack_nbt_stable_after_reload", False
                ),
            ),
        )
        for name, mutation in cases:
            with (
                self.subTest(name=name),
                tempfile.TemporaryDirectory() as temporary,
            ):
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: mutation(report["food_items"]),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        reload_fields = (
            "food_item_registry_stable",
            "food_item_properties_stable",
            "food_item_stack_nbt_stable",
        )
        for field in reload_fields:
            with (
                self.subTest(reload_field=field),
                tempfile.TemporaryDirectory() as temporary,
            ):
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: report["reload"].__setitem__(field, False),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            report_path = fixture.scenario / "reports" / "report.json"
            assertion_index = contract_v18.EXPECTED_ASSERTION_NAMES.index(
                "food_item_properties_exact"
            )
            mutate_json(
                report_path,
                lambda report: report["assertions"][assertion_index].__setitem__(
                    "actual", "tampered"
                ),
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "assertion"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_food_consumption_native_state_and_reload_are_exact(self) -> None:
        started_cases = (
            ("capture error", "capture_error", "java.lang.IllegalStateException"),
            ("player class", "player_class", "java.lang.Object"),
            ("player uuid", "player_uuid", "00000000-0000-0000-0000-000000000000"),
            ("player name", "player_name", "WrongFoodStart"),
            ("item id", "item_id", "minecraft:air"),
            ("result item id", "result_item_id", "minecraft:air"),
            ("initial hunger", "initial_hunger", 11),
            ("initial saturation", "initial_saturation", 1.0),
            ("initial stack count", "initial_stack_count", 1),
            ("result hunger", "result_hunger", 12),
            ("result saturation", "result_saturation", 11.0),
            ("result stack count", "result_stack_count", 2),
            ("same stack instance", "same_stack_instance", False),
            ("exact result", "exact", False),
        )
        reloaded_cases = (
            ("capture error", "capture_error", "java.lang.IllegalStateException"),
            ("player class", "player_class", "java.lang.Object"),
            (
                "fresh player uuid",
                "player_uuid",
                contract_v18.SERVER_STARTED_FOOD_PLAYER_UUID,
            ),
            (
                "fresh player name",
                "player_name",
                contract_v18.SERVER_STARTED_FOOD_PLAYER_NAME,
            ),
            ("item id", "item_id", "minecraft:air"),
            ("result item id", "result_item_id", "minecraft:air"),
            ("initial hunger", "initial_hunger", 11),
            ("initial saturation", "initial_saturation", 1.0),
            ("initial stack count", "initial_stack_count", 1),
            ("result hunger", "result_hunger", 12),
            ("result saturation", "result_saturation", 11.0),
            ("result stack count", "result_stack_count", 2),
            ("same stack instance", "same_stack_instance", False),
            ("exact result", "exact", False),
        )
        for phase, cases in (
            ("server_started", started_cases),
            ("reloaded", reloaded_cases),
        ):
            for name, field, value in cases:
                with (
                    self.subTest(phase=phase, name=name),
                    tempfile.TemporaryDirectory() as temporary,
                ):
                    fixture = build_fixture(Path(temporary))
                    report_path = fixture.scenario / "reports" / "report.json"
                    mutate_json(
                        report_path,
                        lambda report: report["food_consumption"][
                            phase
                        ].__setitem__(field, value),
                    )

                    with self.assertRaisesRegex(
                        evidence.EvidenceError,
                        "v18 contract",
                    ):
                        evidence.validate_live_runtime(
                            fixture.runtime,
                            fixture.profile,
                            expected_runtime=fixture.runtime,
                        )

        for name, field in (
            ("fresh player flag", "fresh_player_after_reload"),
            ("stable after reload", "stable_after_reload"),
        ):
            with (
                self.subTest(name=name),
                tempfile.TemporaryDirectory() as temporary,
            ):
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: report["food_consumption"].__setitem__(
                        field, False
                    ),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            report_path = fixture.scenario / "reports" / "report.json"
            mutate_json(
                report_path,
                lambda report: report["reload"].__setitem__(
                    "food_consumption_stable", False
                ),
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            report_path = fixture.scenario / "reports" / "report.json"
            assertion_index = contract_v18.EXPECTED_ASSERTION_NAMES.index(
                "reloaded_food_consumption_exact"
            )
            mutate_json(
                report_path,
                lambda report: report["assertions"][assertion_index].__setitem__(
                    "actual", "false"
                ),
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "assertion"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_forest_lantern_full_runtime_contract_is_exact(self) -> None:
        recipe_id = "etherology:forest_lantern_crumb"
        advancement_id = "etherology:recipes/food/forest_lantern_crumb"
        assertion_index = contract_v18.EXPECTED_ASSERTION_NAMES.index(
            "forest_lantern_contract_exact"
        )
        cases = (
            (
                "block registry",
                ("forest_lantern", "block_registry_id"),
                "minecraft:item",
            ),
            (
                "item registry",
                ("forest_lantern", "item_registry_id"),
                "minecraft:block",
            ),
            (
                "block identifier",
                ("forest_lantern", "block_id"),
                "minecraft:air",
            ),
            (
                "item identifier",
                ("forest_lantern", "item_id"),
                "minecraft:air",
            ),
            (
                "capture error",
                ("forest_lantern", "capture_error"),
                "java.lang.IllegalStateException",
            ),
            (
                "block class",
                ("forest_lantern", "block_class"),
                "net.minecraft.block.Block",
            ),
            (
                "item class",
                ("forest_lantern", "item_class"),
                "net.minecraft.item.Item",
            ),
            (
                "BlockItem mapping",
                ("forest_lantern", "block_item_maps_to_block"),
                False,
            ),
            (
                "block as item mapping",
                ("forest_lantern", "block_as_item_matches"),
                False,
            ),
            (
                "item stack NBT",
                ("forest_lantern", "item_stack", "serialized_count"),
                1,
            ),
            (
                "default state",
                ("forest_lantern", "default_state"),
                "age=0,facing=north",
            ),
            (
                "state count",
                ("forest_lantern", "state_count"),
                19,
            ),
            (
                "state inventory",
                ("forest_lantern", "states", 0),
                "age=4,facing=north",
            ),
            (
                "duplicate state network ID",
                ("forest_lantern", "state_network_ids", 1),
                0,
            ),
            (
                "negative state network ID",
                ("forest_lantern", "state_network_ids", 0),
                -1,
            ),
            (
                "boolean state network ID",
                ("forest_lantern", "state_network_ids", 0),
                True,
            ),
            (
                "outline shape",
                (
                    "forest_lantern",
                    "outline_shapes",
                    "age=4,facing=north",
                ),
                "0.0,0.0,0.0,1.0,1.0,1.0",
            ),
            (
                "properties",
                ("forest_lantern", "properties"),
                "net.minecraft.block.Block",
            ),
            (
                "hoe tag",
                ("forest_lantern", "tags", "hoe_mineable"),
                False,
            ),
            (
                "peach logs",
                ("forest_lantern", "tags", "peach_log_ids"),
                ["etherology:peach_log"],
            ),
            (
                "initial loot age zero",
                (
                    "forest_lantern",
                    "loaded_data",
                    "initial",
                    "loot_by_age",
                    "0",
                ),
                "etherology:forest_lanternx1",
            ),
            (
                "reloaded mature loot",
                (
                    "forest_lantern",
                    "loaded_data",
                    "reloaded",
                    "loot_by_age",
                    "4",
                ),
                "",
            ),
            (
                "recipe inventory",
                (
                    "forest_lantern",
                    "loaded_data",
                    "initial",
                    "recipe_ids",
                ),
                [recipe_id],
            ),
            (
                "recipe real match and craft",
                (
                    "forest_lantern",
                    "loaded_data",
                    "initial",
                    "recipe_matches_and_crafts_exact",
                ),
                False,
            ),
            (
                "recipe description",
                (
                    "forest_lantern",
                    "loaded_data",
                    "reloaded",
                    "recipes",
                    recipe_id,
                ),
                "synthetic",
            ),
            (
                "advancement inventory",
                (
                    "forest_lantern",
                    "loaded_data",
                    "initial",
                    "advancement_ids",
                ),
                [advancement_id],
            ),
            (
                "advancement description",
                (
                    "forest_lantern",
                    "loaded_data",
                    "reloaded",
                    "advancements",
                    advancement_id,
                ),
                "synthetic",
            ),
            (
                "loaded data stability",
                ("forest_lantern", "loaded_data", "stable_after_reload"),
                False,
            ),
            (
                "loaded data freshness",
                (
                    "forest_lantern",
                    "loaded_data",
                    "fresh_instances_after_reload",
                ),
                False,
            ),
            (
                "actual BlockItem placement",
                (
                    "forest_lantern",
                    "mechanics",
                    "server_started",
                    "placement",
                    "facings",
                    "east",
                ),
                "SUCCESS|age=4,facing=east|stack=0|support_removed=true",
            ),
            (
                "support removal",
                (
                    "forest_lantern",
                    "mechanics",
                    "server_started",
                    "placement",
                    "supports_removed",
                ),
                False,
            ),
            (
                "real shears delta",
                (
                    "forest_lantern",
                    "mechanics",
                    "reloaded",
                    "shears",
                    "deltas",
                    "age=4,facing=west",
                ),
                "1.0",
            ),
            (
                "shears tool",
                (
                    "forest_lantern",
                    "mechanics",
                    "server_started",
                    "shears",
                    "tool_id",
                ),
                "minecraft:iron_pickaxe",
            ),
            (
                "retain jump RNG",
                (
                    "forest_lantern",
                    "mechanics",
                    "server_started",
                    "retain_jump",
                    "predicted_first_roll",
                ),
                "0.100473166",
            ),
            (
                "retain duplicate callback guard",
                (
                    "forest_lantern",
                    "mechanics",
                    "server_started",
                    "retain_jump",
                    "single_callback_guard_exact",
                ),
                False,
            ),
            (
                "retain next RNG",
                (
                    "forest_lantern",
                    "mechanics",
                    "reloaded",
                    "retain_jump",
                    "next_roll_after_jump",
                ),
                "0.87547785",
            ),
            (
                "break removal",
                (
                    "forest_lantern",
                    "mechanics",
                    "server_started",
                    "break_jump",
                    "block_removed",
                ),
                False,
            ),
            (
                "exact one drop",
                (
                    "forest_lantern",
                    "mechanics",
                    "reloaded",
                    "break_jump",
                    "new_item_entity_count",
                ),
                2,
            ),
            (
                "no duplicate drop callback",
                (
                    "forest_lantern",
                    "mechanics",
                    "reloaded",
                    "break_jump",
                    "new_drops",
                ),
                [
                    "etherology:forest_lanternx1",
                    "etherology:forest_lanternx1",
                ],
            ),
            (
                "fresh mechanics players",
                (
                    "forest_lantern",
                    "mechanics",
                    "fresh_players_after_reload",
                ),
                False,
            ),
            (
                "mechanics stability",
                ("forest_lantern", "mechanics", "stable_after_reload"),
                False,
            ),
            (
                "same state at server started",
                ("forest_lantern", "same_state_at_server_started"),
                False,
            ),
            (
                "registry stability",
                ("forest_lantern", "registry_stable_after_reload"),
                False,
            ),
            (
                "state stability",
                ("forest_lantern", "states_stable_after_reload"),
                False,
            ),
            (
                "tag stability",
                ("forest_lantern", "tags_stable_after_reload"),
                False,
            ),
            (
                "top-level exactness",
                ("forest_lantern", "contract_exact"),
                False,
            ),
            (
                "reload loaded freshness",
                ("reload", "forest_lantern_loaded_data_fresh"),
                False,
            ),
            (
                "reload mechanics stability",
                ("reload", "forest_lantern_mechanics_stable"),
                False,
            ),
            (
                "assertion projection",
                ("assertions", assertion_index, "actual"),
                "false",
            ),
        )
        for name, path, replacement in cases:
            with self.subTest(name=name):
                report = valid_report()
                cursor = report
                for component in path[:-1]:
                    cursor = cursor[component]
                cursor[path[-1]] = replacement

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_report(report)

    def test_loot_condition_registry_and_evaluation_are_exact(self) -> None:
        cases = (
            ("registry_id", "minecraft:item"),
            ("condition_id", "etherology:wrong"),
            (
                "etherology_condition_ids",
                [
                    "etherology:random_chance_with_fortune",
                    "etherology:unexpected",
                ],
            ),
            ("serializer_class", "java.lang.Object"),
            ("probe_table_id", "etherology_e2e_server_probe:wrong"),
            ("empty_tool_items", ["minecraft:stone"]),
            ("fortune_one_items", ["minecraft:diamond"]),
            ("same_state_at_server_started", 1),
            ("registry_and_behavior_stable_after_reload", False),
            ("probe_table_instance_replaced_after_reload", False),
        )
        for field, replacement in cases:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: report["loot_condition"].__setitem__(
                        field, replacement
                    ),
                )

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "v18 contract",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_initial_and_reloaded_ether_source_maps_are_exact(self) -> None:
        cases = (
            (
                "listener class",
                lambda sources: sources.__setitem__(
                    "listener_class", "java.lang.Object"
                ),
            ),
            (
                "resource directory",
                lambda sources: sources.__setitem__(
                    "resource_directory", "ether_source"
                ),
            ),
            (
                "initial capture failure",
                lambda sources: sources["initial"].__setitem__(
                    "capture_error", "reflection failed"
                ),
            ),
            (
                "initial legacy typo",
                lambda sources: sources["initial"]["entries"].__setitem__(
                    "etherology:primoshard_rela", 4.0
                ),
            ),
            (
                "initial rella missing",
                lambda sources: sources["initial"]["entries"].pop(
                    "etherology:primoshard_rella"
                ),
            ),
            (
                "initial redstone changed early",
                lambda sources: sources["initial"]["entries"].__setitem__(
                    "minecraft:redstone", 9.5
                ),
            ),
            (
                "server-start map drift",
                lambda sources: sources["server_started"]["entries"].pop(
                    "minecraft:sculk"
                ),
            ),
            (
                "reloaded capture failure",
                lambda sources: sources["reloaded"].__setitem__(
                    "capture_error", "reflection failed"
                ),
            ),
            (
                "reload override absent",
                lambda sources: sources["reloaded"]["entries"].__setitem__(
                    "minecraft:redstone", 2.0
                ),
            ),
            (
                "reload addition absent",
                lambda sources: sources["reloaded"]["entries"].pop(
                    "minecraft:diamond"
                ),
            ),
            (
                "server-start equality false",
                lambda sources: sources.__setitem__(
                    "same_at_server_started", False
                ),
            ),
            (
                "reload change false",
                lambda sources: sources.__setitem__("changed_after_reload", False),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: mutation(report["ether_sources"]),
                )

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "v18 contract",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_reload_command_pack_and_stability_contract_are_exact(self) -> None:
        cases = (
            ("pack directory", "pack_directory", "foreign-pack"),
            (
                "pack resources",
                "pack_resources",
                list(reversed(contract_v18.RELOAD_PACK_RESOURCES)),
            ),
            ("enabled pack name", "enabled_pack_name", "file/foreign"),
            (
                "enabled pack inventory",
                "enabled_data_pack_names",
                ["vanilla"],
            ),
            (
                "enabled pack equality",
                "enabled_data_packs_exact",
                False,
            ),
            ("command", "command", "reload confirm"),
            ("command result", "command_result", 1),
            ("failure", "failure", "reload failed"),
            ("completion", "completed", False),
            ("update cause", "update_cause", "SERVER_STARTED"),
            ("static data", "should_update_static_data", False),
            ("registry stability", "registry_stable", False),
            ("tag stability", "tags_stable", False),
            (
                "loot stability",
                "loot_condition_registry_and_behavior_stable",
                False,
            ),
            (
                "loot table replacement",
                "loot_table_instance_replaced",
                False,
            ),
            (
                "enchantment registry stability",
                "enchantment_registry_stable",
                False,
            ),
            (
                "enchantment property stability",
                "enchantment_properties_stable",
                False,
            ),
            (
                "enchantment tag stability",
                "enchantment_tag_stable",
                False,
            ),
            (
                "particle registry stability",
                "particle_registry_stable",
                False,
            ),
            (
                "particle type stability",
                "particle_type_contract_stable",
                False,
            ),
            (
                "particle wire stability",
                "particle_wire_contract_stable",
                False,
            ),
            ("stop after completion", "stop_requested_after_completion", False),
        )
        for name, field, replacement in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: report["reload"].__setitem__(
                        field, replacement
                    ),
                )

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "v18 contract",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_tag_update_and_exact_singleton_memberships_are_required(self) -> None:
        cases = (
            (
                "update cause",
                lambda tags: tags.__setitem__("update_cause", "SERVER_STARTED"),
            ),
            (
                "static update",
                lambda tags: tags.__setitem__(
                    "should_update_static_data", False
                ),
            ),
            ("update count", lambda tags: tags.__setitem__("update_count", 1)),
            (
                "reload update cause",
                lambda tags: tags.__setitem__(
                    "reload_update_cause", "SERVER_STARTED"
                ),
            ),
            (
                "reload static update",
                lambda tags: tags.__setitem__(
                    "reload_should_update_static_data", False
                ),
            ),
            (
                "vibrations contains",
                lambda tags: tags["vibrations"].__setitem__(
                    "contains_event", False
                ),
            ),
            (
                "vibrations singleton",
                lambda tags: tags["vibrations"].__setitem__(
                    "etherology_event_ids", []
                ),
            ),
            (
                "warden singleton",
                lambda tags: tags["warden_can_listen"].__setitem__(
                    "etherology_event_ids",
                    [
                        "etherology:etherology_resonance",
                        "etherology:unexpected",
                    ],
                ),
            ),
            (
                "exact two tag ids",
                lambda tags: tags["etherology_tag_ids"].append(
                    "minecraft:allay_can_listen"
                ),
            ),
            (
                "same membership",
                lambda tags: tags.__setitem__(
                    "same_membership_at_server_started", False
                ),
            ),
            (
                "reload membership stability",
                lambda tags: tags.__setitem__("stable_after_reload", False),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(report_path, lambda report: mutation(report["tags"]))

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_dedicated_distribution_and_exact_mod_statuses_are_required(self) -> None:
        cases = (
            (
                "report schema",
                lambda report: report.__setitem__("schema", 2),
            ),
            (
                "boolean report schema",
                lambda report: report.__setitem__("schema", True),
            ),
            (
                "profile id",
                lambda report: report.__setitem__("profile_id", "foreign"),
            ),
            (
                "scenario",
                lambda report: report.__setitem__("scenario", "other"),
            ),
            (
                "distribution",
                lambda report: report.__setitem__("distribution", "CLIENT"),
            ),
            (
                "runtime kind",
                lambda report: report.__setitem__(
                    "runtime_kind", "packaged-jar"
                ),
            ),
            (
                "production mod",
                lambda report: report["mods"]["etherology"].__setitem__(
                    "loaded", False
                ),
            ),
            (
                "probe mod",
                lambda report: report["mods"][
                    "etherology_e2e_server_probe"
                ].__setitem__("loaded", False),
            ),
            (
                "extra mod",
                lambda report: report["mods"].__setitem__(
                    "unexpected", {"loaded": True}
                ),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(report_path, mutation)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_every_profile_forbidden_mod_has_an_explicit_false_status(self) -> None:
        for mod_id in evidence.FORBIDDEN_MOD_IDS:
            with self.subTest(mod_id=mod_id), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: report["mods"][mod_id].__setitem__(
                        "loaded", True
                    ),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_loaded_mod_inventory_is_sorted_unique_required_and_forbidden_free(
        self,
    ) -> None:
        cases = (
            (
                "unsorted",
                lambda report: report["loaded_mod_ids"].reverse(),
            ),
            (
                "duplicate",
                lambda report: report["loaded_mod_ids"].append("minecraft"),
            ),
            (
                "non-string",
                lambda report: report["loaded_mod_ids"].append(1),
            ),
            (
                "invalid id",
                lambda report: report["loaded_mod_ids"].append("Invalid.ID"),
            ),
            (
                "missing required",
                lambda report: report["loaded_mod_ids"].remove("etherology"),
            ),
            (
                "missing generated id",
                lambda report: report["loaded_mod_ids"].remove(
                    "generated_1234567"
                ),
            ),
            (
                "unexpected loaded id",
                lambda report: report["loaded_mod_ids"].append("othermod"),
            ),
            (
                "forbidden loaded",
                lambda report: report["loaded_mod_ids"].insert(2, "quickskin"),
            ),
            (
                "reported intersection",
                lambda report: report["forbidden_mod_ids_loaded"].append(
                    "quickskin"
                ),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(report_path, mutation)

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "v18 contract",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_boolean_evidence_cannot_be_replaced_by_integer_one(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            report_path = fixture.scenario / "reports" / "report.json"
            mutate_json(
                report_path,
                lambda report: report["mods"]["etherology"].__setitem__(
                    "loaded", 1
                ),
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_all_310_assertions_must_pass_in_exact_order_and_value(self) -> None:
        mutations = (
            lambda assertions: assertions.pop(),
            lambda assertions: assertions.__setitem__(
                slice(0, 2), reversed(assertions[0:2])
            ),
            lambda assertions: assertions[0].__setitem__("passed", False),
            lambda assertions: assertions[0].__setitem__(
                "expected", "CLIENT"
            ),
            lambda assertions: assertions[0].__setitem__("actual", "CLIENT"),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: mutation(report["assertions"]),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "assertion"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_report_and_log_lifecycle_order_are_exact(self) -> None:
        with self.subTest(source="report"), tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            report_path = fixture.scenario / "reports" / "report.json"
            mutate_json(
                report_path,
                lambda report: report["lifecycle"].reverse(),
            )
            with self.assertRaisesRegex(evidence.EvidenceError, "v18 contract"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

        with self.subTest(source="log"), tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            log_path = fixture.scenario / "logs" / "latest.log"
            original = log_path.read_text(encoding="utf-8")
            changed = original.replace("server_started", "placeholder", 1)
            changed = changed.replace("server_stopping", "server_started", 1)
            changed = changed.replace("placeholder", "server_stopping", 1)
            metadata = log_path.stat()
            log_path.write_text(changed, encoding="utf-8")
            os.utime(log_path, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))
            with self.assertRaisesRegex(evidence.EvidenceError, "probe lifecycle"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_launcher_requires_zero_exit_no_timeout_and_exact_provenance(self) -> None:
        mutations = (
            lambda launcher: launcher.__setitem__("exit_code", 1),
            lambda launcher: launcher.__setitem__("timed_out", True),
            lambda launcher: launcher.__setitem__("scenario", "other"),
            lambda launcher: launcher.__setitem__("task_path", ":wrong"),
            lambda launcher: launcher["profile_manifest"].__setitem__(
                "sha256", "0" * 64
            ),
            lambda launcher: launcher["server_log"].__setitem__(
                "sha256", "0" * 64
            ),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                launcher_path = (
                    fixture.scenario / "reports" / "launcher-result.json"
                )
                mutate_json(launcher_path, mutation)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_log_rejects_fatal_client_missing_normal_and_duplicate_tokens(self) -> None:
        changes = (
            ("fatal", lambda log: log + "[FATAL] exploded\n", "fatal marker"),
            (
                "error level",
                lambda log: log + "[main/ERROR] exploded\n",
                "fatal marker",
            ),
            (
                "client",
                lambda log: log + "[Render thread/INFO] client loaded\n",
                "client marker",
            ),
            (
                "client class",
                lambda log: log
                + "[main/INFO] [net.minecraft.client.gui.screen.TitleScreen/] loaded\n",
                "unexpected client class marker",
            ),
            (
                "normal",
                lambda log: log.replace("Saving worlds\n", "", 1),
                "normal lifecycle marker",
            ),
            (
                "duplicate",
                lambda log: log
                + "[Server thread/INFO] [EtherologyServerProbe] tags_updated_reload\n",
                "probe lifecycle",
            ),
            (
                "termination status",
                lambda log: log.replace("status=0", "status=1", 1),
                "termination contract",
            ),
        )
        for name, change, message in changes:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                log_path = fixture.scenario / "logs" / "latest.log"
                metadata = log_path.stat()
                log_path.write_text(
                    change(log_path.read_text(encoding="utf-8")),
                    encoding="utf-8",
                )
                os.utime(log_path, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))

                with self.assertRaisesRegex(evidence.EvidenceError, message):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_evidence_log_must_be_exact_copy_of_game_log(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            (fixture.game / "logs" / "latest.log").write_bytes(
                valid_server_log() + b"source-only\n"
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "exact game-log copy"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_saved_world_is_required_and_cannot_be_linked(self) -> None:
        cases = ("missing", "empty", "linked")
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                level_data = fixture.game / "world" / "level.dat"
                if case == "missing":
                    level_data.unlink()
                elif case == "empty":
                    level_data.write_bytes(b"")
                else:
                    target = fixture.root / "foreign-level.dat"
                    target.write_bytes(b"foreign")
                    level_data.unlink()
                    level_data.symlink_to(target)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_evidence_inventory_rejects_extras_and_symlinks(self) -> None:
        cases = ("extra", "symlink")
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                if case == "extra":
                    (fixture.scenario / "console.log").write_text(
                        "not contractual\n", encoding="utf-8"
                    )
                else:
                    target = fixture.root / "foreign.log"
                    target.write_text("foreign\n", encoding="utf-8")
                    linked = fixture.scenario / "logs" / "linked.log"
                    linked.symlink_to(target)

                with self.assertRaisesRegex(evidence.EvidenceError, "inventory|linked"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_runtime_isolation_rejects_extra_roots_game_evidence_and_mod_jars(
        self,
    ) -> None:
        cases = (
            (
                "runtime root",
                lambda fixture: (fixture.runtime / "foreign.txt").write_text(
                    "foreign\n", encoding="utf-8"
                ),
            ),
            (
                "game evidence",
                lambda fixture: (fixture.game / "evidence").mkdir(),
            ),
            (
                "staged mod",
                lambda fixture: (fixture.game / "mods" / "foreign.jar").write_bytes(
                    b"foreign"
                ),
            ),
            (
                "extra scenario",
                lambda fixture: (
                    fixture.runtime / "evidence" / "other-scenario"
                ).mkdir(),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                mutation(fixture)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_live_paths_reject_symlinked_repository_components(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            linked_repository = fixture.root / "linked-repository"
            linked_repository.mkdir()
            (linked_repository / "scripts").symlink_to(
                fixture.root / "repository" / "scripts",
                target_is_directory=True,
            )
            linked_runtime = linked_repository / evidence.RUNTIME_RELATIVE_PATH
            linked_profile = (
                linked_repository / evidence.PROFILE_MANIFEST_RELATIVE_PATH
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "symlink"):
                evidence.validate_live_runtime(
                    linked_runtime,
                    linked_profile,
                    expected_runtime=linked_runtime,
                )

    def test_publication_order_requires_report_log_launcher_then_done(self) -> None:
        paths = (
            ("log", "report.json", "../logs/latest.log"),
            ("launcher", "../logs/latest.log", "launcher-result.json"),
            ("done", "launcher-result.json", "done.marker"),
        )
        for name, later_name, earlier_name in paths:
            for offset in (-1, 0):
                with (
                    self.subTest(name=name, offset=offset),
                    tempfile.TemporaryDirectory() as temporary,
                ):
                    fixture = build_fixture(Path(temporary))
                    reports = fixture.scenario / "reports"
                    later = (reports / later_name).resolve()
                    earlier = (reports / earlier_name).resolve()
                    invalid_time = later.stat().st_mtime_ns + offset
                    os.utime(earlier, ns=(invalid_time, invalid_time))

                    with self.assertRaisesRegex(
                        evidence.EvidenceError,
                        "predates|published last",
                    ):
                        evidence.validate_live_runtime(
                            fixture.runtime,
                            fixture.profile,
                            expected_runtime=fixture.runtime,
                        )


class ArchiveEvidenceTests(unittest.TestCase):
    def test_archive_manifest_is_one_time_and_archive_validation_is_self_contained(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            archive = create_archive(fixture)

            with mock.patch.object(
                evidence,
                "load_profile_manifest",
                side_effect=AssertionError("archive consulted tracked profile"),
            ), mock.patch.object(
                evidence,
                "validate_live_runtime",
                side_effect=AssertionError("archive consulted live runtime"),
            ):
                summary = evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=archive,
                )

            self.assertEqual(evidence.PROFILE_ID, summary.profile_id)
            self.assertEqual(310, summary.assertion_count)
            with self.assertRaisesRegex(evidence.EvidenceError, "already exists"):
                evidence.write_archive_manifest(
                    fixture.profile,
                    archive,
                    expected_archive_root=archive,
                )

    def test_archive_hashes_reject_payload_tampering(self) -> None:
        for relative_path in evidence.EVIDENCE_PAYLOAD_PATHS:
            with self.subTest(
                relative_path=relative_path
            ), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                path = archive / relative_path
                path.write_bytes(path.read_bytes() + b"tamper")

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_resealed_v18_reload_report_tampering_is_rejected(self) -> None:
        cases = (
            (
                "schema downgrade",
                lambda report: report.__setitem__("schema", 2),
            ),
            (
                "registry stability",
                lambda report: report["registry"].__setitem__(
                    "stable_after_reload", False
                ),
            ),
            (
                "enchantment class",
                lambda report: report["enchantments"]["peal"].__setitem__(
                    "class", "java.lang.Object"
                ),
            ),
            (
                "enchantment tag membership",
                lambda report: report["enchantments"][
                    "non_treasure_etherology_enchantment_ids"
                ].remove("etherology:reflection"),
            ),
            (
                "enchantment reload stability",
                lambda report: report["reload"].__setitem__(
                    "enchantment_properties_stable", False
                ),
            ),
            (
                "particle type class",
                lambda report: report["particles"]["entries"][
                    "alchemy"
                ].__setitem__("type_class", "java.lang.Object"),
            ),
            (
                "particle wire sample",
                lambda report: report["particles"]["entries"][
                    "spark"
                ].__setitem__("factory_sample_as_string", "wrong"),
            ),
            (
                "SealType texture",
                lambda report: report["particles"]["seal_types"]["entries"][
                    "clos"
                ].__setitem__("texture_light_id", None),
            ),
            (
                "particle reload stability",
                lambda report: report["reload"].__setitem__(
                    "particle_wire_contract_stable", False
                ),
            ),
            (
                "material-item runtime class",
                lambda report: report["material_items"]["entries"][
                    "etherology:etheroscope"
                ].__setitem__("runtime_class", "java.lang.Object"),
            ),
            (
                "material-item enriched stack maximum",
                lambda report: report["material_items"]["entries"][
                    "etherology:enriched_attrahite"
                ].__setitem__("max_count", 64),
            ),
            (
                "material-item NBT keys",
                lambda report: report["material_items"]["entries"][
                    "etherology:resonating_wand"
                ]["serialized_keys"].reverse(),
            ),
            (
                "material-item reload stability",
                lambda report: report["reload"].__setitem__(
                    "material_item_stack_nbt_stable", False
                ),
            ),
            (
                "metal-block class",
                lambda report: report["metal_blocks"]["entries"][
                    "etherology:ethril_block"
                ].__setitem__("block_class", "java.lang.Object"),
            ),
            (
                "metal-block mapping",
                lambda report: report["metal_blocks"]["entries"][
                    "etherology:azel_block"
                ].__setitem__("block_as_item_matches", False),
            ),
            (
                "metal-block property",
                lambda report: report["metal_blocks"]["entries"][
                    "etherology:ethril_block"
                ].__setitem__("hardness", 5.0),
            ),
            (
                "metal-block tag",
                lambda report: report["metal_blocks"]["entries"][
                    "etherology:azel_block"
                ].__setitem__("beacon_base", True),
            ),
            (
                "metal BlockItem NBT",
                lambda report: report["metal_blocks"]["entries"][
                    "etherology:ebony_block"
                ].__setitem__("serialized_count", 1),
            ),
            (
                "metal-block placement",
                lambda report: report["metal_blocks"]["placement"][
                    "placed_block_ids"
                ].__setitem__("etherology:azel_block", "minecraft:air"),
            ),
            (
                "metal-block reload stability",
                lambda report: report["reload"].__setitem__(
                    "metal_block_placement_stable", False
                ),
            ),
            (
                "food-item property",
                lambda report: report["food_items"]["entries"][
                    contract_v18.FOOD_ITEM_ID
                ].__setitem__("hunger", 4),
            ),
            (
                "food-item stack NBT",
                lambda report: report["food_items"]["entries"][
                    contract_v18.FOOD_ITEM_ID
                ]["serialized_keys"].reverse(),
            ),
            (
                "food consumption player identity",
                lambda report: report["food_consumption"][
                    "reloaded"
                ].__setitem__("player_name", "EtherFoodStart"),
            ),
            (
                "food consumption result",
                lambda report: report["food_consumption"][
                    "server_started"
                ].__setitem__("result_hunger", 12),
            ),
            (
                "food reload stability",
                lambda report: report["reload"].__setitem__(
                    "food_consumption_stable", False
                ),
            ),
            (
                "food assertion actual",
                lambda report: report["assertions"][
                    contract_v18.EXPECTED_ASSERTION_NAMES.index(
                        "food_item_contract_exact"
                    )
                ].__setitem__("actual", "false"),
            ),
            (
                "Forest Lantern state",
                lambda report: report["forest_lantern"]["states"].pop(),
            ),
            (
                "Forest Lantern loaded loot",
                lambda report: report["forest_lantern"]["loaded_data"][
                    "reloaded"
                ]["loot_by_age"].__setitem__("4", ""),
            ),
            (
                "Forest Lantern real recipe match",
                lambda report: report["forest_lantern"]["loaded_data"][
                    "initial"
                ].__setitem__("recipe_matches_and_crafts_exact", False),
            ),
            (
                "Forest Lantern advancement",
                lambda report: report["forest_lantern"]["loaded_data"][
                    "reloaded"
                ]["advancements"].pop(
                    "etherology:recipes/food/forest_lantern_crumb"
                ),
            ),
            (
                "Forest Lantern BlockItem placement",
                lambda report: report["forest_lantern"]["mechanics"][
                    "server_started"
                ]["placement"]["facings"].__setitem__("north", "PASS"),
            ),
            (
                "Forest Lantern shears",
                lambda report: report["forest_lantern"]["mechanics"]["reloaded"][
                    "shears"
                ]["speeds"].__setitem__("age=4,facing=west", "1.0"),
            ),
            (
                "Forest Lantern duplicate callback guard",
                lambda report: report["forest_lantern"]["mechanics"][
                    "server_started"
                ]["retain_jump"].__setitem__(
                    "single_callback_guard_exact", False
                ),
            ),
            (
                "Forest Lantern exact drop",
                lambda report: report["forest_lantern"]["mechanics"]["reloaded"][
                    "break_jump"
                ].__setitem__("new_item_entity_count", 2),
            ),
            (
                "Forest Lantern fresh loaded instances",
                lambda report: report["forest_lantern"]["loaded_data"].__setitem__(
                    "fresh_instances_after_reload", False
                ),
            ),
            (
                "Forest Lantern reload stability",
                lambda report: report["reload"].__setitem__(
                    "forest_lantern_mechanics_stable", False
                ),
            ),
            (
                "Attrahite block registry",
                lambda report: report["attrahite_blocks"]["block_ids"].pop(),
            ),
            (
                "Attrahite block property",
                lambda report: report["attrahite_blocks"]["entries"][
                    "etherology:attrahite_brick_slab"
                ].__setitem__("hardness", 1.5),
            ),
            (
                "Attrahite block tag",
                lambda report: report["attrahite_blocks"]["entries"][
                    "etherology:attrahite"
                ].__setitem__("needs_stone_tool", False),
            ),
            (
                "Attrahite BlockItem NBT",
                lambda report: report["attrahite_blocks"]["entries"][
                    "etherology:attrahite_bricks"
                ]["serialized_keys"].reverse(),
            ),
            (
                "Attrahite placement state",
                lambda report: report["attrahite_blocks"]["placement"][
                    "placed_states"
                ].__setitem__("etherology:attrahite", "minecraft:air"),
            ),
            (
                "Attrahite world save",
                lambda report: report["attrahite_blocks"]["placement"].__setitem__(
                    "world_saved_after_placement", False
                ),
            ),
            (
                "Attrahite Silk Touch loot",
                lambda report: report["attrahite_blocks"]["loaded_data"].__setitem__(
                    "raw_silk_touch_loot", "none"
                ),
            ),
            (
                "Attrahite Fortune loot",
                lambda report: report["attrahite_blocks"]["loaded_data"][
                    "raw_fortune_loot"
                ].__setitem__("3", "wrong"),
            ),
            (
                "Attrahite recipe",
                lambda report: report["attrahite_blocks"]["loaded_data"][
                    "recipes"
                ].pop("etherology:raw_azel"),
            ),
            (
                "Attrahite advancement",
                lambda report: report["attrahite_blocks"]["loaded_data"][
                    "advancements"
                ].pop("etherology:recipes/misc/raw_azel"),
            ),
            (
                "Attrahite fresh loaded instances",
                lambda report: report["attrahite_blocks"]["loaded_data"].__setitem__(
                    "fresh_instances_after_reload", False
                ),
            ),
            (
                "Attrahite reload stability",
                lambda report: report["reload"].__setitem__(
                    "attrahite_block_placement_stable", False
                ),
            ),
            (
                "unexpected loaded mod",
                lambda report: report["loaded_mod_ids"].append("othermod"),
            ),
            (
                "initial override applied early",
                lambda report: report["ether_sources"]["initial"][
                    "entries"
                ].__setitem__("minecraft:redstone", 9.5),
            ),
            (
                "reload override absent",
                lambda report: report["ether_sources"]["reloaded"][
                    "entries"
                ].__setitem__("minecraft:redstone", 2.0),
            ),
            (
                "reload addition absent",
                lambda report: report["ether_sources"]["reloaded"][
                    "entries"
                ].pop("minecraft:diamond"),
            ),
            (
                "reload command failure",
                lambda report: report["reload"].__setitem__(
                    "command_result", 1
                ),
            ),
            (
                "enabled pack missing",
                lambda report: report["reload"][
                    "enabled_data_pack_names"
                ].remove(contract_v18.RELOAD_PACK_ENABLED_NAME),
            ),
            (
                "enabled pack equality false",
                lambda report: report["reload"].__setitem__(
                    "enabled_data_packs_exact", False
                ),
            ),
            (
                "reload incomplete",
                lambda report: report["reload"].__setitem__(
                    "completed", False
                ),
            ),
            (
                "one tag update",
                lambda report: report["tags"].__setitem__("update_count", 1),
            ),
            (
                "reload lifecycle omitted",
                lambda report: report["lifecycle"].remove(
                    "tags_updated_reload"
                ),
            ),
            (
                "assertion actual changed",
                lambda report: report["assertions"][43].__setitem__(
                    "actual", "tampered"
                ),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                report_path = archive / "reports" / "report.json"
                mutate_json(report_path, mutation)
                reseal_archive_record(archive, "reports/report.json")

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "v18 contract",
                ):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_resealed_v18_reload_log_tampering_is_rejected(self) -> None:
        cases = (
            (
                "reload phase reordered",
                lambda log: log.replace(
                    "tags_updated_reload", "phase_placeholder", 1
                )
                .replace(
                    "reload_command_returned", "tags_updated_reload", 1
                )
                .replace(
                    "phase_placeholder", "reload_command_returned", 1
                ),
                "probe lifecycle",
            ),
            (
                "reload request omitted",
                lambda log: log.replace(
                    "[Server thread/INFO] [EtherologyServerProbe] "
                    "reload_requested\n",
                    "",
                    1,
                ),
                "probe lifecycle",
            ),
            (
                "fatal reload error",
                lambda log: log + "[Server thread/ERROR] reload failed\n",
                "fatal marker",
            ),
            (
                "nonzero termination",
                lambda log: log.replace("status=0", "status=1", 1),
                "termination contract",
            ),
        )
        for name, mutation, message in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                log_path = archive / "logs" / "latest.log"
                log_path.write_text(
                    mutation(log_path.read_text(encoding="utf-8")),
                    encoding="utf-8",
                )
                reseal_archive_log_and_launcher(archive)

                with self.assertRaisesRegex(evidence.EvidenceError, message):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_archive_inventory_rejects_extras_and_symlinks(self) -> None:
        for case in ("extra", "symlink"):
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                if case == "extra":
                    (archive / "console.log").write_text(
                        "unexpected\n", encoding="utf-8"
                    )
                else:
                    target = fixture.root / "outside.log"
                    target.write_text("outside\n", encoding="utf-8")
                    linked = archive / "linked.log"
                    linked.symlink_to(target)

                with self.assertRaisesRegex(evidence.EvidenceError, "inventory|linked"):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_archive_path_rejects_symlinked_repository_components(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            create_archive(fixture)
            linked_repository = fixture.root / "linked-repository"
            linked_repository.mkdir()
            (linked_repository / "docs").symlink_to(
                fixture.root / "repository" / "docs",
                target_is_directory=True,
            )
            linked_archive = linked_repository / evidence.ARCHIVE_RELATIVE_PATH

            with self.assertRaisesRegex(evidence.EvidenceError, "symlink"):
                evidence.validate_archived_evidence(
                    linked_archive,
                    expected_archive_root=linked_archive,
                )

    def test_archive_manifest_identity_and_provenance_are_fail_closed(self) -> None:
        mutations = (
            lambda manifest: manifest["profile"].__setitem__(
                "manifest_sha256", "0" * 64
            ),
            lambda manifest: manifest["runtime"].__setitem__(
                "execution", "packaged-jar"
            ),
            lambda manifest: manifest.__setitem__("assertion_count", 23),
            lambda manifest: manifest["publication"].__setitem__(
                "verified_completion_marker_last_before_sealing", False
            ),
            lambda manifest: manifest["files"][
                "reports/report.json"
            ].__setitem__("size", True),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                manifest_path = archive / evidence.ARCHIVE_MANIFEST_NAME
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
                mutation(manifest)
                write_json(manifest_path, manifest)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_archive_requires_exact_destination_and_attrahite_v18_name(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            archive = create_archive(fixture)

            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "destination|repository-owned root",
            ):
                evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=(
                        fixture.root / "other-repository" / evidence.ARCHIVE_RELATIVE_PATH
                    ),
                )

            wrong_name = fixture.root / "wrong-name"
            shutil.copytree(archive, wrong_name, copy_function=shutil.copy2)
            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "repository path|Attrahite block registry profile v18",
            ):
                evidence.validate_archived_evidence(
                    wrong_name,
                    expected_archive_root=wrong_name,
                )

    def test_archive_rejects_older_and_newer_attrahite_directories(
        self,
    ) -> None:
        for competing_name in (
            "attrahite-block-registry-server-v17",
            "attrahite-block-registry-server-v19",
        ):
            for operation in ("create", "validate"):
                with (
                    self.subTest(
                        competing_name=competing_name,
                        operation=operation,
                    ),
                    tempfile.TemporaryDirectory() as temporary_directory,
                ):
                    fixture = build_fixture(Path(temporary_directory))
                    archive = (
                        fixture.root
                        / "repository"
                        / evidence.ARCHIVE_RELATIVE_PATH
                    )
                    archive.parent.mkdir(parents=True)
                    shutil.copytree(
                        fixture.scenario,
                        archive,
                        copy_function=shutil.copy2,
                    )
                    if operation == "validate":
                        evidence.write_archive_manifest(
                            fixture.profile,
                            archive,
                            expected_archive_root=archive,
                        )
                    (archive.parent / competing_name).mkdir()

                    with self.assertRaisesRegex(
                        evidence.EvidenceError,
                        "competing Attrahite block registry evidence",
                    ):
                        if operation == "create":
                            evidence.write_archive_manifest(
                                fixture.profile,
                                archive,
                                expected_archive_root=archive,
                            )
                        else:
                            evidence.validate_archived_evidence(
                                archive,
                                expected_archive_root=archive,
                            )

    def test_archive_validation_is_independent_of_checkout_mtimes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            archive = create_archive(fixture)
            timestamp = time.time_ns()
            for index, path in enumerate(
                sorted(path for path in archive.rglob("*") if path.is_file())
            ):
                reversed_time = timestamp - (index * 1_000_000_000)
                os.utime(path, ns=(reversed_time, reversed_time))

            summary = evidence.validate_archived_evidence(
                archive,
                expected_archive_root=archive,
            )

            self.assertEqual(310, summary.assertion_count)


if __name__ == "__main__":
    unittest.main()
