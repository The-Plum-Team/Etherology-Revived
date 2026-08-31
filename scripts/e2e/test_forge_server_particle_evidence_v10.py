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

import forge_server_contract_v10 as contract_v10
import forge_server_particle_evidence_v10 as evidence


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
        + [contract_v10.RELOAD_PACK_ENABLED_NAME]
    )
    return {
        "schema": 6,
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
            "registry_id": contract_v10.ENCHANTMENT_REGISTRY_ID,
            "non_treasure_tag_id": contract_v10.NON_TREASURE_TAG_ID,
            "etherology_enchantment_ids": list(contract_v10.ENCHANTMENT_IDS),
            "peal": copy.deepcopy(contract_v10.ENCHANTMENTS["peal"]),
            "reflection": copy.deepcopy(
                contract_v10.ENCHANTMENTS["reflection"]
            ),
            "non_treasure_etherology_enchantment_ids": list(
                contract_v10.ENCHANTMENT_IDS
            ),
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "properties_stable_after_reload": True,
            "tag_stable_after_reload": True,
        },
        "particles": {
            "registry_id": contract_v10.PARTICLE_REGISTRY_ID,
            "capture_error": "",
            "etherology_particle_ids": list(contract_v10.PARTICLE_IDS),
            "payload_families": list(
                contract_v10.PARTICLE_PAYLOAD_FAMILIES
            ),
            "entries": copy.deepcopy(contract_v10.PARTICLES),
            "seal_types": {
                "order": list(contract_v10.SEAL_TYPE_ORDER),
                "codec_round_trips_exact": True,
                "entries": copy.deepcopy(contract_v10.SEAL_TYPES),
            },
            "same_state_at_server_started": True,
            "registry_stable_after_reload": True,
            "type_contract_stable_after_reload": True,
            "wire_contract_stable_after_reload": True,
        },
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
            "listener_class": contract_v10.ETHER_SOURCE_LISTENER_CLASS,
            "resource_directory": "ether_sources",
            "initial": {
                "capture_error": "",
                "entries": copy.deepcopy(
                    contract_v10.INITIAL_ETHER_SOURCE_ENTRIES
                ),
            },
            "server_started": {
                "capture_error": "",
                "entries": copy.deepcopy(
                    contract_v10.INITIAL_ETHER_SOURCE_ENTRIES
                ),
            },
            "reloaded": {
                "capture_error": "",
                "entries": copy.deepcopy(
                    contract_v10.RELOADED_ETHER_SOURCE_ENTRIES
                ),
            },
            "same_at_server_started": True,
            "changed_after_reload": True,
        },
        "reload": {
            "pack_directory": contract_v10.RELOAD_PACK_DIRECTORY,
            "pack_resources": list(contract_v10.RELOAD_PACK_RESOURCES),
            "enabled_pack_name": contract_v10.RELOAD_PACK_ENABLED_NAME,
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
        "lifecycle": list(contract_v10.EXPECTED_LIFECYCLE),
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
        evidence.REPOSITORY_ROOT / contract_v10.PROFILE_SNAPSHOT_RELATIVE_PATH
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
    def test_v10_archive_contract_does_not_import_the_mutable_runner(self) -> None:
        evidence_source = Path(evidence.__file__).read_text(encoding="utf-8")
        contract_source = Path(contract_v10.__file__).read_text(encoding="utf-8")

        self.assertIn(
            "import forge_server_contract_v10 as contract_v10",
            evidence_source,
        )
        self.assertNotRegex(evidence_source, r"(?m)^import forge_server$")
        self.assertNotRegex(contract_source, r"(?m)^import forge_server$")
        self.assertEqual(138, len(contract_v10.EXPECTED_ASSERTION_NAMES))
        self.assertEqual(138, len(contract_v10.EXPECTED_ASSERTION_VALUES))
        snapshot = (
            evidence.REPOSITORY_ROOT / contract_v10.PROFILE_SNAPSHOT_RELATIVE_PATH
        )
        self.assertEqual(
            contract_v10.PROFILE_MANIFEST_SIZE,
            snapshot.stat().st_size,
        )
        self.assertEqual(
            contract_v10.PROFILE_MANIFEST_SHA256,
            evidence.sha256_file(snapshot),
        )
        self.assertEqual(
            {"enum_name", "as_string"},
            set(contract_v10.SEAL_TYPES["empty"]),
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
                set(contract_v10.SEAL_TYPES[seal_name]),
            )


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
            self.assertEqual(138, summary.assertion_count)
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

                with self.assertRaisesRegex(evidence.EvidenceError, "v10 contract"):
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

                with self.assertRaisesRegex(evidence.EvidenceError, "v10 contract"):
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

                with self.assertRaisesRegex(evidence.EvidenceError, "v10 contract"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

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
                    "v10 contract",
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
                    "v10 contract",
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
                list(reversed(contract_v10.RELOAD_PACK_RESOURCES)),
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
                    "v10 contract",
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

                with self.assertRaisesRegex(evidence.EvidenceError, "v10 contract"):
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

                with self.assertRaisesRegex(evidence.EvidenceError, "v10 contract"):
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
                    "v10 contract",
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

            with self.assertRaisesRegex(evidence.EvidenceError, "v10 contract"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_all_138_assertions_must_pass_in_exact_order_and_value(self) -> None:
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
            with self.assertRaisesRegex(evidence.EvidenceError, "v10 contract"):
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
            self.assertEqual(138, summary.assertion_count)
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

    def test_resealed_v10_reload_report_tampering_is_rejected(self) -> None:
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
                ].remove(contract_v10.RELOAD_PACK_ENABLED_NAME),
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
                    "v10 contract",
                ):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_resealed_v10_reload_log_tampering_is_rejected(self) -> None:
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

    def test_archive_requires_exact_destination_and_particle_registry_v10_name(
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
                "repository path|particle-registry profile v10",
            ):
                evidence.validate_archived_evidence(
                    wrong_name,
                    expected_archive_root=wrong_name,
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

            self.assertEqual(138, summary.assertion_count)


if __name__ == "__main__":
    unittest.main()
