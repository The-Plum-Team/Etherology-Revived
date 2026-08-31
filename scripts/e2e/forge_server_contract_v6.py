"""Freeze the Forge 1.20.1 Ether-source reload report contract for profile v6."""

from __future__ import annotations

import re


PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v6"
SCENARIO_ID = "ether-source-reload"
TASK_PATH = ":forge:1.20.1:runRegistryFoundationServerProbe"
PROFILE_MANIFEST_RELATIVE_PATH = "scripts/e2e/forge-server-1.20.1-profile.json"
PROFILE_SNAPSHOT_RELATIVE_PATH = (
    "scripts/e2e/forge-server-1.20.1-profile-v6.json"
)
PROFILE_MANIFEST_SIZE = 1192
PROFILE_MANIFEST_SHA256 = (
    "2e6b937169d7bf8d765d181de93837371fb32940b31a480f5fde9620d96d21f0"
)
REQUIRED_MOD_IDS = (
    "etherology",
    "etherology_e2e_server_probe",
)
FORBIDDEN_MOD_IDS = (
    "etherology_e2e_harness",
    "quickskin",
    "cpm",
    "ears",
    "modmenu",
    "roughlyenoughitems",
    "emi",
)
RELOAD_PACK_DIRECTORY = "etherology-e2e-ether-source-reload"
RELOAD_PACK_ENABLED_NAME = f"file/{RELOAD_PACK_DIRECTORY}"
RELOAD_PACK_RESOURCES = (
    "pack.mcmeta",
    "data/etherology/ether_sources/default.json",
    "data/etherology/ether_sources/probe_addition.json",
)
STABLE_LOADED_MOD_IDS = (
    "architectury",
    *REQUIRED_MOD_IDS,
    "forge",
    "geckolib",
    "minecraft",
)
ETHER_SOURCE_LISTENER_CLASS = (
    "ru.feytox.etherology.data.ethersource.EtherSourceLoader"
)
INITIAL_ETHER_SOURCE_ENTRIES = {
    "etherology:primoshard_clos": 4.0,
    "etherology:primoshard_keta": 4.0,
    "etherology:primoshard_rella": 4.0,
    "etherology:primoshard_via": 4.0,
    "minecraft:ancient_debris": 4.0,
    "minecraft:blaze_powder": 2.0,
    "minecraft:chorus_fruit": 2.0,
    "minecraft:crying_obsidian": 6.0,
    "minecraft:echo_shard": 12.0,
    "minecraft:ender_eye": 6.0,
    "minecraft:ender_pearl": 4.0,
    "minecraft:experience_bottle": 8.0,
    "minecraft:ghast_tear": 4.0,
    "minecraft:glowstone_dust": 1.0,
    "minecraft:gunpowder": 1.0,
    "minecraft:heart_of_the_sea": 12.0,
    "minecraft:honeycomb": 1.0,
    "minecraft:lapis_lazuli": 1.0,
    "minecraft:magma_cream": 2.0,
    "minecraft:prismarine_crystals": 1.0,
    "minecraft:quartz": 1.0,
    "minecraft:redstone": 2.0,
    "minecraft:sculk": 12.0,
}
RELOADED_ETHER_SOURCE_ENTRIES = {
    **INITIAL_ETHER_SOURCE_ENTRIES,
    "minecraft:diamond": 13.0,
    "minecraft:redstone": 9.5,
}
EXPECTED_LIFECYCLE = (
    "tags_updated_initial",
    "server_started",
    "reload_requested",
    "tags_updated_reload",
    "reload_command_returned",
    "stop_requested",
    "server_stopping",
    "server_stopped",
)


def canonical_ether_source_entries(entries: dict[str, float]) -> str:
    """Serializes a sorted Ether-source map for the probe assertion contract."""
    return ",".join(
        f"{identifier}={entries[identifier]}" for identifier in sorted(entries)
    )


EXPECTED_ASSERTION_NAMES = (
    "distribution_dedicated_server",
    "runtime_kind_loom_userdev",
    *(f"mod_loaded:{mod_id}" for mod_id in REQUIRED_MOD_IDS),
    *(f"mod_absent:{mod_id}" for mod_id in FORBIDDEN_MOD_IDS),
    "mods_forbidden_intersection_empty",
    "registry:game_event:etherology:etherology_resonance",
    "registry:game_event_etherology_ids_exact",
    "registry_internal_id",
    "registry_range",
    "registry:loot_condition:etherology:random_chance_with_fortune",
    "registry:loot_condition_etherology_ids_exact",
    "registry:loot_condition_serializer_class",
    "loot_table:probe_table_loaded",
    "loot_table:empty_tool_items_exact",
    "loot_table:fortune_one_items_exact",
    "loot_condition_captured_after_server_data_load",
    "ether_source_listener_class",
    "ether_source_resource_directory",
    "ether_source_initial_capture_error",
    "ether_source_initial_entry_count",
    "ether_source_initial_entries_exact",
    "ether_source_initial_rella_value",
    "ether_source_initial_legacy_rela_absent",
    "ether_source_initial_redstone_value",
    "ether_source_captured_after_server_data_load",
    "server_started_ether_sources_rechecked",
    "reload_pack_directory",
    "reload_pack_resources_exact",
    "reload_pack_enabled",
    "enabled_data_packs_exact",
    "reload_failure",
    "reload_command",
    "reload_command_result",
    "reload_completed",
    "reload_update_cause",
    "reload_static_data",
    "ether_source_reloaded_capture_error",
    "ether_source_reloaded_entry_count",
    "ether_source_reloaded_entries_exact",
    "ether_source_reloaded_redstone_value",
    "ether_source_reloaded_diamond_value",
    "ether_source_reloaded_rella_value",
    "ether_source_reloaded_legacy_rela_absent",
    "ether_source_map_changed_after_reload",
    "registry_stable_after_reload",
    "tags_stable_after_reload",
    "loot_condition_registry_and_behavior_stable_after_reload",
    "loot_table_instance_replaced_after_reload",
    "server_stop_requested_after_reload",
    "tags_update_cause",
    "tags_static_data",
    "tags_update_count",
    "tag:vibrations_contains_resonance",
    "tag:vibrations_etherology_entries_exact",
    "tag:warden_can_listen_contains_resonance",
    "tag:warden_can_listen_etherology_entries_exact",
    "tags:etherology_tag_ids_exact",
    "tags_before_server_started",
    "server_started_mods_rechecked",
    "server_started_registry_rechecked",
    "server_started_tags_rechecked",
    "server_started_loot_condition_rechecked",
    "server_stop_requested_without_restart",
    "server_lifecycle_identity",
    "lifecycle",
)
EXPECTED_ASSERTION_VALUES = (
    "DEDICATED_SERVER",
    "loom-userdev",
    *("loaded" for _mod_id in REQUIRED_MOD_IDS),
    *("absent" for _mod_id in FORBIDDEN_MOD_IDS),
    "none",
    "present",
    "etherology:etherology_resonance",
    "etherology_resonance",
    "16",
    "present",
    "etherology:random_chance_with_fortune",
    "ru.feytox.etherology.util.misc.RandomChanceWithFortuneConditionSerializer",
    "etherology_e2e_server_probe:registry_foundation",
    "minecraft:gold_ingot,minecraft:stone",
    "minecraft:diamond,minecraft:gold_ingot,minecraft:stone",
    "true",
    ETHER_SOURCE_LISTENER_CLASS,
    "ether_sources",
    "none",
    "23",
    canonical_ether_source_entries(INITIAL_ETHER_SOURCE_ENTRIES),
    "4.0",
    "absent",
    "2.0",
    "true",
    "true",
    RELOAD_PACK_DIRECTORY,
    ",".join(RELOAD_PACK_RESOURCES),
    RELOAD_PACK_ENABLED_NAME,
    "true",
    "none",
    "reload",
    "0",
    "true",
    "SERVER_DATA_LOAD",
    "true",
    "none",
    "24",
    canonical_ether_source_entries(RELOADED_ETHER_SOURCE_ENTRIES),
    "9.5",
    "13.0",
    "4.0",
    "absent",
    "true",
    "true",
    "true",
    "true",
    "true",
    "true",
    "SERVER_DATA_LOAD",
    "true",
    "2",
    "true",
    "etherology:etherology_resonance",
    "true",
    "etherology:etherology_resonance",
    "minecraft:vibrations,minecraft:warden_can_listen",
    "true",
    "true",
    "true",
    "true",
    "true",
    "stop(false)",
    "true",
    ">".join(EXPECTED_LIFECYCLE),
)
PROBE_LOG_PHASES = (
    EXPECTED_LIFECYCLE[0],
    "registry_foundation_checked",
    *EXPECTED_LIFECYCLE[1:],
    "report_published",
)
SERVER_LOG_TOKENS = tuple(
    f"[EtherologyServerProbe] {phase}" for phase in PROBE_LOG_PHASES
) + (
    "[EtherologyServerProbe] loom_userdev_exit_scheduled "
    "status=0 server_thread_join_timeout_ms=30000",
)


class V6ContractError(RuntimeError):
    """Reports an exact profile-v6 report contract violation."""


def exact_json_value(actual: object, expected: object) -> bool:
    """Compares JSON values without treating booleans as integers."""
    if type(actual) is not type(expected):
        return False
    if isinstance(expected, dict):
        return set(actual) == set(expected) and all(
            exact_json_value(actual[key], expected[key]) for key in expected
        )
    if isinstance(expected, list):
        return len(actual) == len(expected) and all(
            exact_json_value(actual_value, expected_value)
            for actual_value, expected_value in zip(actual, expected, strict=True)
        )
    return actual == expected


def validate_probe_report(
    report: dict[str, object],
    required_mod_ids: object,
    forbidden_mod_ids: object,
) -> None:
    """Validates the exact profile-v6 report and its profile mod alignment."""
    expected_fields = {
        "schema",
        "profile_id",
        "scenario",
        "status",
        "minecraft",
        "loader",
        "loader_version",
        "java",
        "distribution",
        "runtime_kind",
        "loaded_mod_ids",
        "forbidden_mod_ids_loaded",
        "mods",
        "registry",
        "loot_condition",
        "ether_sources",
        "reload",
        "tags",
        "lifecycle",
        "assertions",
    }
    if set(report) != expected_fields:
        raise V6ContractError("The server probe report field inventory changed")
    expected_scalars = {
        "schema": 4,
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
        "status": "passed",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "distribution": "DEDICATED_SERVER",
        "runtime_kind": "loom-userdev",
    }
    for name, expected in expected_scalars.items():
        if not exact_json_value(report.get(name), expected):
            raise V6ContractError(
                f"The server probe report {name} value changed"
            )

    loaded_mod_ids = report.get("loaded_mod_ids")
    generated_mod_ids = (
        [
            mod_id
            for mod_id in loaded_mod_ids
            if type(mod_id) is str
            and re.fullmatch(r"generated_[0-9a-f]+", mod_id) is not None
        ]
        if isinstance(loaded_mod_ids, list)
        else []
    )
    if (
        not isinstance(loaded_mod_ids, list)
        or not all(
            type(mod_id) is str
            and re.fullmatch(r"[a-z][a-z0-9_-]{1,63}", mod_id) is not None
            for mod_id in loaded_mod_ids
        )
        or loaded_mod_ids != sorted(set(loaded_mod_ids))
        or len(generated_mod_ids) != 1
        or set(loaded_mod_ids)
        != set(STABLE_LOADED_MOD_IDS) | set(generated_mod_ids)
        or not set(REQUIRED_MOD_IDS).issubset(loaded_mod_ids)
        or set(FORBIDDEN_MOD_IDS).intersection(loaded_mod_ids)
    ):
        raise V6ContractError("The full loaded mod id inventory is invalid")
    if not exact_json_value(report.get("forbidden_mod_ids_loaded"), []):
        raise V6ContractError("The loaded forbidden mod intersection is not empty")
    expected_mods = {
        **{mod_id: {"loaded": True} for mod_id in REQUIRED_MOD_IDS},
        **{mod_id: {"loaded": False} for mod_id in FORBIDDEN_MOD_IDS},
    }
    if not exact_json_value(report.get("mods"), expected_mods):
        raise V6ContractError("The server probe mod subset changed")
    if not exact_json_value(report.get("registry"), {
        "registry_id": "minecraft:game_event",
        "event_id": "etherology:etherology_resonance",
        "internal_id": "etherology_resonance",
        "range": 16,
        "etherology_event_ids": ["etherology:etherology_resonance"],
        "same_instance_at_server_started": True,
        "stable_after_reload": True,
    }):
        raise V6ContractError("The server probe registry result changed")
    if not exact_json_value(report.get("loot_condition"), {
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
    }):
        raise V6ContractError("The server probe loot-condition result changed")

    expected_initial_capture = {
        "capture_error": "",
        "entries": INITIAL_ETHER_SOURCE_ENTRIES,
    }
    if not exact_json_value(report.get("ether_sources"), {
        "listener_class": ETHER_SOURCE_LISTENER_CLASS,
        "resource_directory": "ether_sources",
        "initial": expected_initial_capture,
        "server_started": expected_initial_capture,
        "reloaded": {
            "capture_error": "",
            "entries": RELOADED_ETHER_SOURCE_ENTRIES,
        },
        "same_at_server_started": True,
        "changed_after_reload": True,
    }):
        raise V6ContractError("The server probe Ether-source result changed")

    reload = report.get("reload")
    if not isinstance(reload, dict) or set(reload) != {
        "pack_directory",
        "pack_resources",
        "enabled_pack_name",
        "enabled_data_pack_names",
        "enabled_data_packs_exact",
        "command",
        "command_result",
        "failure",
        "completed",
        "update_cause",
        "should_update_static_data",
        "registry_stable",
        "tags_stable",
        "loot_condition_registry_and_behavior_stable",
        "loot_table_instance_replaced",
        "stop_requested_after_completion",
    }:
        raise V6ContractError("The server probe reload result changed")
    enabled_data_pack_names = reload.get("enabled_data_pack_names")
    expected_enabled_data_pack_names = sorted(
        [
            "vanilla" if mod_id == "minecraft" else f"mod:{mod_id}"
            for mod_id in loaded_mod_ids
        ]
        + [RELOAD_PACK_ENABLED_NAME]
    )
    if (
        not isinstance(enabled_data_pack_names, list)
        or not all(
            type(pack_name) is str and pack_name
            for pack_name in enabled_data_pack_names
        )
        or enabled_data_pack_names != expected_enabled_data_pack_names
    ):
        raise V6ContractError(
            "The server probe enabled data-pack inventory changed"
        )
    expected_reload = {
        "pack_directory": RELOAD_PACK_DIRECTORY,
        "pack_resources": list(RELOAD_PACK_RESOURCES),
        "enabled_pack_name": RELOAD_PACK_ENABLED_NAME,
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
        "stop_requested_after_completion": True,
    }
    if not exact_json_value(reload, expected_reload):
        raise V6ContractError("The server probe reload result changed")

    if not exact_json_value(report.get("tags"), {
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
    }):
        raise V6ContractError("The server probe tag result changed")
    if not exact_json_value(report.get("lifecycle"), list(EXPECTED_LIFECYCLE)):
        raise V6ContractError("The server probe lifecycle changed")

    assertions = report.get("assertions")
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise V6ContractError("The server probe assertion inventory changed")
    for index, (name, value) in enumerate(
        zip(EXPECTED_ASSERTION_NAMES, EXPECTED_ASSERTION_VALUES, strict=True)
    ):
        assertion = assertions[index]
        if not isinstance(assertion, dict) or set(assertion) != {
            "name",
            "passed",
            "expected",
            "actual",
        }:
            raise V6ContractError(
                f"Server probe assertion {index} has invalid fields"
            )
        if not exact_json_value(assertion, {
            "name": name,
            "passed": True,
            "expected": value,
            "actual": value,
        }):
            raise V6ContractError(
                f"Server probe assertion failed or changed: {name}"
            )

    if (
        required_mod_ids != list(REQUIRED_MOD_IDS)
        or forbidden_mod_ids != list(FORBIDDEN_MOD_IDS)
    ):
        raise V6ContractError(
            "The report mod subset differs from the tracked profile"
        )
