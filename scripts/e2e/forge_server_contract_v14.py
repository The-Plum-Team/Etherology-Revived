"""Freeze the Forge 1.20.1 food-item report contract for profile v14."""

from __future__ import annotations

import copy

from forge_server_contract_v13 import *  # noqa: F403
import forge_server_contract_v13 as contract_v13


PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v14"
SCENARIO_ID = "food-item-registry"
TASK_PATH = ":forge:1.20.1:runRegistryFoundationServerProbe"
PROFILE_MANIFEST_RELATIVE_PATH = "scripts/e2e/forge-server-1.20.1-profile.json"
PROFILE_SNAPSHOT_RELATIVE_PATH = (
    "scripts/e2e/forge-server-1.20.1-profile-v14.json"
)
PROFILE_MANIFEST_SIZE = 1192
PROFILE_MANIFEST_SHA256 = (
    "442d11e6a5072c8ec418bced406529dc15caa6d4f4d4c5c68edc8a79ce2e493d"
)
REPORT_SCHEMA = 9
FOOD_ITEM_REGISTRY_ID = "minecraft:item"
FOOD_ITEM_ID = "etherology:forest_lantern_crumb"
FOOD_ITEM_IDS = (FOOD_ITEM_ID,)
FOOD_ITEM_CLASS = "net.minecraft.item.Item"
FOOD_ITEM_NBT_KEYS = (
    "Count",
    "id",
)
FOOD_ITEM_PROPERTIES = (
    f"{FOOD_ITEM_ID}={FOOD_ITEM_CLASS}|max=64|is_food=true|hunger=3"
    "|saturation=2.0|always_edible=false|status_effects=0"
    "|recipe_remainder=false|recipe_remainder_id="
)
FOOD_ITEM_SAVE_REPRESENTATION = (
    f"{FOOD_ITEM_ID}|class={FOOD_ITEM_CLASS}|max=64"
    f"|nbt_id={FOOD_ITEM_ID}|nbt_count=64"
    f"|nbt_keys={'+'.join(FOOD_ITEM_NBT_KEYS)}"
)
FOOD_ITEM_SAVE_REPRESENTATIONS = (
    f"{FOOD_ITEM_ID}={FOOD_ITEM_SAVE_REPRESENTATION}"
)
FOOD_ITEMS = {
    FOOD_ITEM_ID: {
        "id": FOOD_ITEM_ID,
        "runtime_class": FOOD_ITEM_CLASS,
        "max_count": 64,
        "is_food": True,
        "hunger": 3,
        "saturation_modifier": 2.0,
        "always_edible": False,
        "status_effect_count": 0,
        "has_recipe_remainder": False,
        "recipe_remainder_id": "",
        "serialized_id": FOOD_ITEM_ID,
        "serialized_count": 64,
        "serialized_keys": list(FOOD_ITEM_NBT_KEYS),
        "round_trip_exact": True,
        "save_representation": FOOD_ITEM_SAVE_REPRESENTATION,
    },
}
FOOD_CONSUMPTION_PLAYER_CLASS = (
    "net.minecraft.server.network.ServerPlayerEntity"
)
SERVER_STARTED_FOOD_PLAYER_UUID = "00000000-0000-0000-0000-00000000e214"
SERVER_STARTED_FOOD_PLAYER_NAME = "EtherFoodStart"
RELOADED_FOOD_PLAYER_UUID = "00000000-0000-0000-0000-00000000e215"
RELOADED_FOOD_PLAYER_NAME = "EtherFoodReload"


def food_consumption_capture(player_uuid: str, player_name: str) -> dict[str, object]:
    """Builds one exact native food-consumption capture."""
    return {
        "capture_error": "",
        "player_class": FOOD_CONSUMPTION_PLAYER_CLASS,
        "player_uuid": player_uuid,
        "player_name": player_name,
        "item_id": FOOD_ITEM_ID,
        "result_item_id": FOOD_ITEM_ID,
        "initial_hunger": 10,
        "initial_saturation": 0.0,
        "initial_stack_count": 2,
        "result_hunger": 13,
        "result_saturation": 12.0,
        "result_stack_count": 1,
        "same_stack_instance": True,
        "exact": True,
    }


SERVER_STARTED_FOOD_CONSUMPTION = food_consumption_capture(
    SERVER_STARTED_FOOD_PLAYER_UUID,
    SERVER_STARTED_FOOD_PLAYER_NAME,
)
RELOADED_FOOD_CONSUMPTION = food_consumption_capture(
    RELOADED_FOOD_PLAYER_UUID,
    RELOADED_FOOD_PLAYER_NAME,
)
FOOD_ASSERTION_NAMES = (
    f"registry:item:{FOOD_ITEM_ID}",
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
FOOD_ASSERTION_VALUES = (
    "present",
    FOOD_ITEM_ID,
    "none",
    FOOD_ITEM_CLASS,
    FOOD_ITEM_PROPERTIES,
    "true",
    FOOD_ITEM_SAVE_REPRESENTATIONS,
    "true",
    "true",
    "true",
    "true",
    "true",
    "true",
    "none",
    FOOD_CONSUMPTION_PLAYER_CLASS,
    SERVER_STARTED_FOOD_PLAYER_UUID,
    SERVER_STARTED_FOOD_PLAYER_NAME,
    FOOD_ITEM_ID,
    FOOD_ITEM_ID,
    "10",
    "0.0",
    "2",
    "13",
    "12.0",
    "1",
    "true",
    "true",
    "none",
    "true",
    "true",
    "true",
)
_FOOD_ASSERTION_INSERTION_INDEX = (
    contract_v13.EXPECTED_ASSERTION_NAMES.index(
        "metal_block_placement_stable_after_reload"
    )
    + 1
)
EXPECTED_ASSERTION_NAMES = (
    contract_v13.EXPECTED_ASSERTION_NAMES[:_FOOD_ASSERTION_INSERTION_INDEX]
    + FOOD_ASSERTION_NAMES
    + contract_v13.EXPECTED_ASSERTION_NAMES[_FOOD_ASSERTION_INSERTION_INDEX:]
)
EXPECTED_ASSERTION_VALUES = (
    contract_v13.EXPECTED_ASSERTION_VALUES[:_FOOD_ASSERTION_INSERTION_INDEX]
    + FOOD_ASSERTION_VALUES
    + contract_v13.EXPECTED_ASSERTION_VALUES[_FOOD_ASSERTION_INSERTION_INDEX:]
)


class V14ContractError(RuntimeError):
    """Reports an exact profile-v14 report contract violation."""


def _validate_food_assertions(assertions: object) -> None:
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise V14ContractError("The server probe assertion inventory changed")
    for index, (name, value) in enumerate(
        zip(EXPECTED_ASSERTION_NAMES, EXPECTED_ASSERTION_VALUES, strict=True)
    ):
        assertion = assertions[index]
        expected = {
            "name": name,
            "passed": True,
            "expected": value,
            "actual": value,
        }
        if not exact_json_value(assertion, expected):  # noqa: F405
            raise V14ContractError(
                f"Server probe assertion failed or changed: {name}"
            )


def _v13_baseline(report: dict[str, object]) -> dict[str, object]:
    baseline = copy.deepcopy(report)
    baseline.pop("food_items", None)
    baseline.pop("food_consumption", None)
    baseline["schema"] = 8
    baseline["profile_id"] = contract_v13.PROFILE_ID
    baseline["scenario"] = contract_v13.SCENARIO_ID
    assertions = baseline.get("assertions")
    if isinstance(assertions, list):
        baseline["assertions"] = (
            assertions[:_FOOD_ASSERTION_INSERTION_INDEX]
            + assertions[
                _FOOD_ASSERTION_INSERTION_INDEX + len(FOOD_ASSERTION_NAMES) :
            ]
        )
    reload = baseline.get("reload")
    if isinstance(reload, dict):
        for field in (
            "food_item_registry_stable",
            "food_item_properties_stable",
            "food_item_stack_nbt_stable",
            "food_consumption_stable",
        ):
            reload.pop(field, None)
    return baseline


def validate_probe_report(
    report: dict[str, object],
    required_mod_ids: object,
    forbidden_mod_ids: object,
) -> None:
    """Validates the exact profile-v14 report and its cumulative v13 baseline."""
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
        "enchantments",
        "particles",
        "material_items",
        "metal_blocks",
        "food_items",
        "food_consumption",
        "loot_condition",
        "ether_sources",
        "reload",
        "tags",
        "lifecycle",
        "assertions",
    }
    if set(report) != expected_fields:
        raise V14ContractError("The server probe report field inventory changed")
    for name, expected in {
        "schema": REPORT_SCHEMA,
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
    }.items():
        if not exact_json_value(report.get(name), expected):  # noqa: F405
            raise V14ContractError(
                f"The server probe report {name} value changed"
            )

    expected_food_items = {
        "registry_id": FOOD_ITEM_REGISTRY_ID,
        "capture_error": "",
        "food_item_ids": list(FOOD_ITEM_IDS),
        "vanilla_item_class": FOOD_ITEM_CLASS,
        "properties": FOOD_ITEM_PROPERTIES,
        "save_representations": FOOD_ITEM_SAVE_REPRESENTATIONS,
        "entries": FOOD_ITEMS,
        "same_state_at_server_started": True,
        "registry_stable_after_reload": True,
        "properties_stable_after_reload": True,
        "stack_nbt_stable_after_reload": True,
    }
    if not exact_json_value(report.get("food_items"), expected_food_items):  # noqa: F405
        raise V14ContractError("The server probe food-item result changed")
    expected_consumption = {
        "server_started": SERVER_STARTED_FOOD_CONSUMPTION,
        "reloaded": RELOADED_FOOD_CONSUMPTION,
        "fresh_player_after_reload": True,
        "stable_after_reload": True,
    }
    if not exact_json_value(  # noqa: F405
        report.get("food_consumption"),
        expected_consumption,
    ):
        raise V14ContractError("The server probe food-consumption result changed")

    reload = report.get("reload")
    if not isinstance(reload, dict):
        raise V14ContractError("The server probe reload result changed")
    for field in (
        "food_item_registry_stable",
        "food_item_properties_stable",
        "food_item_stack_nbt_stable",
        "food_consumption_stable",
    ):
        if not exact_json_value(reload.get(field), True):  # noqa: F405
            raise V14ContractError("The server probe reload result changed")

    _validate_food_assertions(report.get("assertions"))
    try:
        contract_v13.validate_probe_report(
            _v13_baseline(report),
            required_mod_ids,
            forbidden_mod_ids,
        )
    except contract_v13.V13ContractError as exception:
        raise V14ContractError(str(exception)) from exception
