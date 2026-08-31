"""Freeze the Forge 1.20.1 Forest Lantern report contract for profile v16."""

from __future__ import annotations

import copy

from forge_server_contract_v14 import *  # noqa: F403
import forge_server_contract_v14 as contract_v14


PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v16"
SCENARIO_ID = "forest-lantern"
TASK_PATH = ":forge:1.20.1:runRegistryFoundationServerProbe"
PROFILE_MANIFEST_RELATIVE_PATH = "scripts/e2e/forge-server-1.20.1-profile.json"
PROFILE_SNAPSHOT_RELATIVE_PATH = (
    "scripts/e2e/forge-server-1.20.1-profile-v16.json"
)
PROFILE_MANIFEST_SIZE = 1184
PROFILE_MANIFEST_SHA256 = (
    "82419a84d0bca220b5032f45fec053265ed5701594af32fd3721d02a66862332"
)
REPORT_SCHEMA = 10
FOREST_LANTERN_BLOCK_REGISTRY_ID = "minecraft:block"
FOREST_LANTERN_ITEM_REGISTRY_ID = "minecraft:item"
FOREST_LANTERN_ID = "etherology:forest_lantern"
FOREST_LANTERN_BLOCK_CLASS = (
    "ru.feytox.etherology.block.forestLantern.ForestLanternBlock"
)
FOREST_LANTERN_ITEM_CLASS = "net.minecraft.item.BlockItem"
FOREST_LANTERN_DEFAULT_STATE = "age=4,facing=north"
FOREST_LANTERN_PROPERTIES = (
    f"{FOREST_LANTERN_BLOCK_CLASS}|hardness=0.2|blast=0.2"
    "|grass_sound=true|tool_required=false|luminance=8|opaque=true"
    "|full_cube=false|transparent=true|post_process=true|emissive=true"
    "|piston=DESTROY|mature_random_ticks=false|bud_random_ticks=true"
)
FOREST_LANTERN_STATES = tuple(
    f"age={age},facing={facing}"
    for age in range(5)
    for facing in ("east", "north", "south", "west")
)
FOREST_LANTERN_STATE_NETWORK_IDS_FIXTURE = list(range(20))
FOREST_LANTERN_OUTLINE_SHAPES = {
    "age=0,facing=north": "0.25,0.25,0.8125,0.75,0.75,1.0",
    "age=1,facing=north": "0.34375,0.3125,0.6875,0.65625,0.6875,1.0",
    "age=2,facing=north": "0.3125,0.3125,0.5625,0.6875,0.75,1.0",
    "age=3,facing=north": "0.25,0.3125,0.375,0.75,0.875,1.0",
    "age=4,facing=north": "0.125,0.25,0.25,0.875,1.0,1.0",
    "age=0,facing=south": "0.25,0.25,0.0,0.75,0.75,0.1875",
    "age=1,facing=south": "0.34375,0.3125,0.0,0.65625,0.6875,0.3125",
    "age=2,facing=south": "0.3125,0.3125,0.0,0.6875,0.75,0.4375",
    "age=3,facing=south": "0.25,0.3125,0.0,0.75,0.875,0.625",
    "age=4,facing=south": "0.125,0.25,0.0,0.875,1.0,0.75",
    "age=0,facing=west": "0.8125,0.25,0.25,1.0,0.75,0.75",
    "age=1,facing=west": "0.6875,0.3125,0.34375,1.0,0.6875,0.65625",
    "age=2,facing=west": "0.5625,0.3125,0.3125,1.0,0.75,0.6875",
    "age=3,facing=west": "0.375,0.3125,0.25,1.0,0.875,0.75",
    "age=4,facing=west": "0.25,0.25,0.125,1.0,1.0,0.875",
    "age=0,facing=east": "0.0,0.25,0.25,0.1875,0.75,0.75",
    "age=1,facing=east": "0.0,0.3125,0.34375,0.3125,0.6875,0.65625",
    "age=2,facing=east": "0.0,0.3125,0.3125,0.4375,0.75,0.6875",
    "age=3,facing=east": "0.0,0.3125,0.25,0.625,0.875,0.75",
    "age=4,facing=east": "0.0,0.25,0.125,0.75,1.0,0.875",
}
FOREST_LANTERN_LOOT_BY_AGE = {
    "0": "",
    "1": "",
    "2": "",
    "3": "",
    "4": f"{FOREST_LANTERN_ID}x1",
}
FOREST_LANTERN_RECIPE_IDS = (
    "etherology:forest_lantern_crumb",
    "etherology:forest_lantern_crumb_from_campfire",
    "etherology:forest_lantern_crumb_from_smoking",
    "etherology:leather",
)
FOREST_LANTERN_ADVANCEMENT_IDS = (
    "etherology:recipes/food/forest_lantern_crumb",
    "etherology:recipes/food/forest_lantern_crumb_from_campfire",
    "etherology:recipes/food/forest_lantern_crumb_from_smoking",
    "etherology:recipes/misc/leather",
)


def _cooking_recipe(
    identifier: str,
    class_name: str,
    recipe_type: str,
    cook_time: int,
) -> str:
    return (
        f"{identifier}|class={class_name}|type={recipe_type}"
        f"|serializer={recipe_type}|output=etherology:forest_lantern_crumbx1"
        "|group=|notification=true|input_exact=true|category=food"
        f"|cook_time={cook_time}|experience=0.35|matches=true"
        "|crafted=etherology:forest_lantern_crumbx1"
    )


FOREST_LANTERN_RECIPES = {
    "etherology:forest_lantern_crumb": _cooking_recipe(
        "etherology:forest_lantern_crumb",
        "net.minecraft.recipe.SmeltingRecipe",
        "minecraft:smelting",
        200,
    ),
    "etherology:forest_lantern_crumb_from_campfire": _cooking_recipe(
        "etherology:forest_lantern_crumb_from_campfire",
        "net.minecraft.recipe.CampfireCookingRecipe",
        "minecraft:campfire_cooking",
        600,
    ),
    "etherology:forest_lantern_crumb_from_smoking": _cooking_recipe(
        "etherology:forest_lantern_crumb_from_smoking",
        "net.minecraft.recipe.SmokingRecipe",
        "minecraft:smoking",
        100,
    ),
    "etherology:leather": (
        "etherology:leather|class=net.minecraft.recipe.ShapedRecipe"
        "|type=minecraft:crafting|serializer=minecraft:crafting_shaped"
        "|output=minecraft:leatherx1|group=|notification=true"
        "|input=string,forest_lantern,string,empty,forest_lantern,empty,"
        "string,forest_lantern,string|category=misc|width=3|height=3"
        "|matches=true|crafted=minecraft:leatherx1"
    ),
}
FOREST_LANTERN_ADVANCEMENTS = {
    advancement_id: (
        f"{advancement_id}|parent=minecraft:recipes/root"
        "|criteria=has_forest_lantern+has_the_recipe"
        "|requirements=has_forest_lantern+has_the_recipe"
        "|reward_recipes="
        + advancement_id.replace(
            "etherology:recipes/food/",
            "etherology:",
        ).replace(
            "etherology:recipes/misc/",
            "etherology:",
        )
        + "|telemetry=false"
    )
    for advancement_id in FOREST_LANTERN_ADVANCEMENT_IDS
}
FOREST_LANTERN_PLACEMENTS = {
    facing: (
        f"CONSUME|age=4,facing={facing}|stack=0|support_removed=true"
    )
    for facing in ("east", "north", "south", "west")
}
FOREST_LANTERN_SHEARS_SPEEDS = {
    state: "15.0" for state in FOREST_LANTERN_STATES
}
FOREST_LANTERN_SHEARS_DELTAS = {
    state: "1.0" if state.startswith("age=0,") else "2.5"
    for state in FOREST_LANTERN_STATES
}


def _loaded_data() -> dict[str, object]:
    return {
        "capture_error": "",
        "loot_table_id": "etherology:blocks/forest_lantern",
        "loot_by_age": copy.deepcopy(FOREST_LANTERN_LOOT_BY_AGE),
        "recipe_ids": list(FOREST_LANTERN_RECIPE_IDS),
        "recipes": copy.deepcopy(FOREST_LANTERN_RECIPES),
        "advancement_ids": list(FOREST_LANTERN_ADVANCEMENT_IDS),
        "advancements": copy.deepcopy(FOREST_LANTERN_ADVANCEMENTS),
        "recipe_matches_and_crafts_exact": True,
        "loot_exact": True,
        "recipes_exact": True,
        "advancements_exact": True,
        "contract_exact": True,
    }


_PHASE_PLAYERS = {
    "SERVER_STARTED": {
        "shears": (
            "00000000-0000-0000-0000-00000000e216",
            "LanternShearA",
        ),
        "retain": (
            "00000000-0000-0000-0000-00000000e217",
            "LanternKeepA",
        ),
        "break": (
            "00000000-0000-0000-0000-00000000e218",
            "LanternBreakA",
        ),
    },
    "RELOADED": {
        "shears": (
            "00000000-0000-0000-0000-00000000e219",
            "LanternShearB",
        ),
        "retain": (
            "00000000-0000-0000-0000-00000000e21a",
            "LanternKeepB",
        ),
        "break": (
            "00000000-0000-0000-0000-00000000e21b",
            "LanternBreakB",
        ),
    },
}


def _jump(
    player: tuple[str, str],
    kind: str,
) -> dict[str, object]:
    retaining = kind == "RETAIN"
    return {
        "capture_error": "",
        "player_uuid": player[0],
        "player_name": player[1],
        "kind": kind,
        "seed": 1 if retaining else 4096,
        "predicted_first_roll": "0.7308782" if retaining else "0.09789288",
        "predicted_second_roll": "0.100473166" if retaining else "0.87547785",
        "next_roll_after_jump": "0.100473166" if retaining else "NaN",
        "stepping_position_exact": True,
        "block_removed": not retaining,
        "new_item_entity_count": 0 if retaining else 1,
        "new_drops": [] if retaining else [f"{FOREST_LANTERN_ID}x1"],
        "single_callback_guard_exact": retaining,
        "exact": True,
    }


def _world_mechanics(phase: str) -> dict[str, object]:
    players = _PHASE_PLAYERS[phase]
    return {
        "phase": phase,
        "capture_error": "",
        "placement": {
            "capture_error": "",
            "facings": copy.deepcopy(FOREST_LANTERN_PLACEMENTS),
            "exact": True,
            "supports_removed": True,
        },
        "shears": {
            "capture_error": "",
            "player_uuid": players["shears"][0],
            "player_name": players["shears"][1],
            "tool_id": "minecraft:shears",
            "on_ground": True,
            "can_harvest": True,
            "speeds": copy.deepcopy(FOREST_LANTERN_SHEARS_SPEEDS),
            "deltas": copy.deepcopy(FOREST_LANTERN_SHEARS_DELTAS),
            "exact": True,
        },
        "retain_jump": _jump(players["retain"], "RETAIN"),
        "break_jump": _jump(players["break"], "BREAK"),
        "contract_exact": True,
    }


FOREST_LANTERN = {
    "block_registry_id": FOREST_LANTERN_BLOCK_REGISTRY_ID,
    "item_registry_id": FOREST_LANTERN_ITEM_REGISTRY_ID,
    "block_id": FOREST_LANTERN_ID,
    "item_id": FOREST_LANTERN_ID,
    "capture_error": "",
    "block_class": FOREST_LANTERN_BLOCK_CLASS,
    "item_class": FOREST_LANTERN_ITEM_CLASS,
    "block_item_maps_to_block": True,
    "block_as_item_matches": True,
    "item_stack": {
        "max_count": 64,
        "serialized_id": FOREST_LANTERN_ID,
        "serialized_count": 64,
        "serialized_keys": ["Count", "id"],
        "round_trip_exact": True,
    },
    "default_state": FOREST_LANTERN_DEFAULT_STATE,
    "state_count": 20,
    "states": list(FOREST_LANTERN_STATES),
    "state_network_ids": FOREST_LANTERN_STATE_NETWORK_IDS_FIXTURE,
    "outline_shapes": copy.deepcopy(FOREST_LANTERN_OUTLINE_SHAPES),
    "properties": FOREST_LANTERN_PROPERTIES,
    "tags": {
        "hoe_mineable": True,
        "peach_logs_tag_id": "etherology:peach_logs",
        "peach_log_ids": [],
    },
    "loaded_data": {
        "initial": _loaded_data(),
        "reloaded": _loaded_data(),
        "stable_after_reload": True,
        "fresh_instances_after_reload": True,
    },
    "mechanics": {
        "server_started": _world_mechanics("SERVER_STARTED"),
        "reloaded": _world_mechanics("RELOADED"),
        "fresh_players_after_reload": True,
        "stable_after_reload": True,
    },
    "same_state_at_server_started": True,
    "registry_stable_after_reload": True,
    "states_stable_after_reload": True,
    "tags_stable_after_reload": True,
    "contract_exact": True,
}


def _canonical_map(values: dict[str, str]) -> str:
    return ",".join(f"{key}={values[key]}" for key in sorted(values))


FOREST_LANTERN_ASSERTION_NAMES = (
    f"registry:block:{FOREST_LANTERN_ID}",
    f"registry:block_item:{FOREST_LANTERN_ID}",
    "forest_lantern_capture_error",
    "forest_lantern_block_class_exact",
    "forest_lantern_item_class_exact",
    "forest_lantern_block_item_mapping_exact",
    "forest_lantern_default_state_exact",
    "forest_lantern_state_count_exact",
    "forest_lantern_states_exact",
    "forest_lantern_state_network_ids_exact",
    "forest_lantern_outline_shapes_exact",
    "forest_lantern_properties_exact",
    "tag:hoe_mineable_contains_forest_lantern",
    "tag:peach_logs_entries_exact",
    "forest_lantern_registry_contract_exact",
    "forest_lantern_loaded_data_capture_error",
    "forest_lantern_loot_table_id_exact",
    "forest_lantern_loot_by_age_exact",
    "forest_lantern_loot_contract_exact",
    "forest_lantern_recipe_ids_exact",
    "forest_lantern_recipes_exact",
    "forest_lantern_recipes_match_and_craft_exact",
    "forest_lantern_advancement_ids_exact",
    "forest_lantern_advancements_exact",
    "forest_lantern_loaded_data_contract_exact",
    "forest_lantern_captured_after_server_data_load",
    "server_started_forest_lantern_rechecked",
    "forest_lantern_placement_exact",
    "forest_lantern_support_removal_exact",
    "forest_lantern_shears_speeds_exact",
    "forest_lantern_shears_deltas_exact",
    "forest_lantern_shears_contract_exact",
    "forest_lantern_jump_retain_exact",
    "forest_lantern_jump_single_callback_guard_exact",
    "forest_lantern_jump_break_exact",
    "forest_lantern_jump_break_drop_exact",
    "forest_lantern_server_mechanics_contract_exact",
    "reloaded_forest_lantern_capture_error",
    "reloaded_forest_lantern_mechanics_capture_error",
    "forest_lantern_registry_stable_after_reload",
    "forest_lantern_states_stable_after_reload",
    "forest_lantern_tags_stable_after_reload",
    "forest_lantern_loaded_data_stable_after_reload",
    "forest_lantern_loaded_data_fresh_after_reload",
    "forest_lantern_mechanics_fresh_players_after_reload",
    "forest_lantern_mechanics_stable_after_reload",
    "forest_lantern_contract_exact",
)
FOREST_LANTERN_ASSERTION_VALUES = (
    "present",
    "present",
    "none",
    FOREST_LANTERN_BLOCK_CLASS,
    FOREST_LANTERN_ITEM_CLASS,
    "true",
    FOREST_LANTERN_DEFAULT_STATE,
    "20",
    ",".join(FOREST_LANTERN_STATES),
    "20 unique non-negative raw IDs",
    _canonical_map(FOREST_LANTERN_OUTLINE_SHAPES),
    FOREST_LANTERN_PROPERTIES,
    "true",
    "none",
    "true",
    "none",
    "etherology:blocks/forest_lantern",
    _canonical_map(FOREST_LANTERN_LOOT_BY_AGE),
    "true",
    ",".join(FOREST_LANTERN_RECIPE_IDS),
    _canonical_map(FOREST_LANTERN_RECIPES),
    "true",
    ",".join(FOREST_LANTERN_ADVANCEMENT_IDS),
    _canonical_map(FOREST_LANTERN_ADVANCEMENTS),
    "true",
    "true",
    "true",
    _canonical_map(FOREST_LANTERN_PLACEMENTS),
    "true",
    _canonical_map(FOREST_LANTERN_SHEARS_SPEEDS),
    _canonical_map(FOREST_LANTERN_SHEARS_DELTAS),
    "true",
    "true",
    "true",
    "true",
    f"{FOREST_LANTERN_ID}x1",
    "true",
    "none",
    "none",
    "true",
    "true",
    "true",
    "true",
    "true",
    "true",
    "true",
    "true",
)
_FOREST_LANTERN_ASSERTION_INSERTION_INDEX = (
    contract_v14.EXPECTED_ASSERTION_NAMES.index(
        "food_consumption_stable_after_reload"
    )
    + 1
)
EXPECTED_ASSERTION_NAMES = (
    contract_v14.EXPECTED_ASSERTION_NAMES[:_FOREST_LANTERN_ASSERTION_INSERTION_INDEX]
    + FOREST_LANTERN_ASSERTION_NAMES
    + contract_v14.EXPECTED_ASSERTION_NAMES[_FOREST_LANTERN_ASSERTION_INSERTION_INDEX:]
)
EXPECTED_ASSERTION_VALUES = (
    contract_v14.EXPECTED_ASSERTION_VALUES[:_FOREST_LANTERN_ASSERTION_INSERTION_INDEX]
    + FOREST_LANTERN_ASSERTION_VALUES
    + contract_v14.EXPECTED_ASSERTION_VALUES[_FOREST_LANTERN_ASSERTION_INSERTION_INDEX:]
)


class V16ContractError(RuntimeError):
    """Reports an exact profile-v16 report contract violation."""


def _validate_assertions(assertions: object) -> None:
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise V16ContractError("The server probe assertion inventory changed")
    for index, (name, value) in enumerate(
        zip(EXPECTED_ASSERTION_NAMES, EXPECTED_ASSERTION_VALUES, strict=True)
    ):
        expected = {
            "name": name,
            "passed": True,
            "expected": value,
            "actual": value,
        }
        if not exact_json_value(assertions[index], expected):  # noqa: F405
            raise V16ContractError(
                f"Server probe assertion failed or changed: {name}"
            )


def _v14_baseline(report: dict[str, object]) -> dict[str, object]:
    baseline = copy.deepcopy(report)
    baseline.pop("forest_lantern", None)
    baseline["schema"] = contract_v14.REPORT_SCHEMA
    baseline["profile_id"] = contract_v14.PROFILE_ID
    baseline["scenario"] = contract_v14.SCENARIO_ID
    assertions = baseline.get("assertions")
    if isinstance(assertions, list):
        baseline["assertions"] = (
            assertions[:_FOREST_LANTERN_ASSERTION_INSERTION_INDEX]
            + assertions[
                _FOREST_LANTERN_ASSERTION_INSERTION_INDEX
                + len(FOREST_LANTERN_ASSERTION_NAMES) :
            ]
        )
    reload = baseline.get("reload")
    if isinstance(reload, dict):
        for field in (
            "forest_lantern_registry_stable",
            "forest_lantern_states_stable",
            "forest_lantern_tags_stable",
            "forest_lantern_loaded_data_stable",
            "forest_lantern_loaded_data_fresh",
            "forest_lantern_mechanics_stable",
        ):
            reload.pop(field, None)
    return baseline


def _validate_forest_lantern(value: object) -> None:
    if not isinstance(value, dict) or set(value) != set(FOREST_LANTERN):
        raise V16ContractError("The server probe Forest Lantern result changed")
    state_network_ids = value.get("state_network_ids")
    if (
        not isinstance(state_network_ids, list)
        or len(state_network_ids) != len(FOREST_LANTERN_STATES)
        or any(type(raw_id) is not int or raw_id < 0 for raw_id in state_network_ids)
        or len(set(state_network_ids)) != len(FOREST_LANTERN_STATES)
    ):
        raise V16ContractError(
            "The server probe Forest Lantern state network IDs changed"
        )
    normalized_actual = copy.deepcopy(value)
    normalized_expected = copy.deepcopy(FOREST_LANTERN)
    normalized_actual.pop("state_network_ids")
    normalized_expected.pop("state_network_ids")
    if not exact_json_value(normalized_actual, normalized_expected):  # noqa: F405
        raise V16ContractError("The server probe Forest Lantern result changed")


def validate_probe_report(
    report: dict[str, object],
    required_mod_ids: object,
    forbidden_mod_ids: object,
) -> None:
    """Validates the exact profile-v16 report and cumulative v14 projection."""
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
        "forest_lantern",
        "loot_condition",
        "ether_sources",
        "reload",
        "tags",
        "lifecycle",
        "assertions",
    }
    if set(report) != expected_fields:
        raise V16ContractError("The server probe report field inventory changed")
    for name, expected in {
        "schema": REPORT_SCHEMA,
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
    }.items():
        if not exact_json_value(report.get(name), expected):  # noqa: F405
            raise V16ContractError(
                f"The server probe report {name} value changed"
            )
    _validate_forest_lantern(report.get("forest_lantern"))
    reload = report.get("reload")
    if not isinstance(reload, dict):
        raise V16ContractError("The server probe reload result changed")
    for field in (
        "forest_lantern_registry_stable",
        "forest_lantern_states_stable",
        "forest_lantern_tags_stable",
        "forest_lantern_loaded_data_stable",
        "forest_lantern_loaded_data_fresh",
        "forest_lantern_mechanics_stable",
    ):
        if not exact_json_value(reload.get(field), True):  # noqa: F405
            raise V16ContractError("The server probe reload result changed")
    _validate_assertions(report.get("assertions"))
    try:
        contract_v14.validate_probe_report(
            _v14_baseline(report),
            required_mod_ids,
            forbidden_mod_ids,
        )
    except contract_v14.V14ContractError as exception:
        raise V16ContractError(str(exception)) from exception
