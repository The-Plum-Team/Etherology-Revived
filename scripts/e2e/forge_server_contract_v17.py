"""Freeze the Forge 1.20.1 Attrahite block report contract for profile v17."""

from __future__ import annotations

import copy

from forge_server_contract_v16 import *  # noqa: F403
import forge_server_contract_v16 as contract_v16


PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v17"
SCENARIO_ID = "attrahite-block-registry"
TASK_PATH = ":forge:1.20.1:runRegistryFoundationServerProbe"
PROFILE_MANIFEST_RELATIVE_PATH = "scripts/e2e/forge-server-1.20.1-profile.json"
PROFILE_SNAPSHOT_RELATIVE_PATH = (
    "scripts/e2e/forge-server-1.20.1-profile-v17.json"
)
PROFILE_MANIFEST_SIZE = 1204
PROFILE_MANIFEST_SHA256 = (
    "58eef8f07f1457d5a806a53fe0f864019902e1e76eb9d5d0b60ea817388d0042"
)
REPORT_SCHEMA = 11
ATTRAHITE_BLOCK_REGISTRY_ID = "minecraft:block"
ATTRAHITE_ITEM_REGISTRY_ID = "minecraft:item"
ATTRAHITE_BLOCK_ITEM_CLASS = "net.minecraft.item.BlockItem"
ATTRAHITE_BLOCK_IDS = (
    "etherology:attrahite",
    "etherology:attrahite_brick_slab",
    "etherology:attrahite_brick_stairs",
    "etherology:attrahite_bricks",
)
ATTRAHITE_LOOT_TABLE_IDS = (
    "etherology:blocks/attrahite",
    "etherology:blocks/attrahite_brick_slab",
    "etherology:blocks/attrahite_brick_stairs",
    "etherology:blocks/attrahite_bricks",
)
ATTRAHITE_RECIPE_IDS = (
    "etherology:attrahite_brick",
    "etherology:attrahite_brick_slab",
    "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting",
    "etherology:attrahite_brick_stairs",
    "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
    "etherology:attrahite_bricks",
    "etherology:azel_ingot",
    "etherology:azel_ingot_from_blasting",
    "etherology:raw_azel",
)
ATTRAHITE_ADVANCEMENT_IDS = (
    "etherology:recipes/building_blocks/attrahite_brick_slab",
    "etherology:recipes/building_blocks/"
    "attrahite_brick_slab_from_attrahite_bricks_stonecutting",
    "etherology:recipes/building_blocks/attrahite_brick_stairs",
    "etherology:recipes/building_blocks/"
    "attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
    "etherology:recipes/building_blocks/attrahite_bricks",
    "etherology:recipes/misc/attrahite_brick",
    "etherology:recipes/misc/azel_ingot",
    "etherology:recipes/misc/azel_ingot_from_blasting",
    "etherology:recipes/misc/raw_azel",
)


def _canonical_map(values: dict[str, str]) -> str:
    return ",".join(f"{key}={values[key]}" for key in sorted(values))


def _save_representation(identifier: str) -> str:
    return (
        f"{identifier}|item_class={ATTRAHITE_BLOCK_ITEM_CLASS}|max=64"
        f"|nbt_id={identifier}|nbt_count=64|nbt_keys=Count+id"
    )


_BLOCK_SPECS = {
    "etherology:attrahite": {
        "block_class": "net.minecraft.block.Block",
        "hardness": 1.5,
        "blast_resistance": 6.0,
        "map_color_id": 11,
        "sound_group": "gilded_blackstone",
        "full_cube": True,
        "transparent": False,
        "state_count": 1,
        "default_state": "etherology:attrahite",
        "needs_stone_tool": True,
        "block_slab": False,
        "item_slab": False,
        "block_stairs": False,
        "item_stairs": False,
    },
    "etherology:attrahite_brick_slab": {
        "block_class": "net.minecraft.block.SlabBlock",
        "hardness": 2.0,
        "blast_resistance": 6.0,
        "map_color_id": 11,
        "sound_group": "stone",
        "full_cube": False,
        "transparent": True,
        "state_count": 6,
        "default_state": (
            "etherology:attrahite_brick_slab[type=bottom,waterlogged=false]"
        ),
        "needs_stone_tool": False,
        "block_slab": True,
        "item_slab": True,
        "block_stairs": False,
        "item_stairs": False,
    },
    "etherology:attrahite_brick_stairs": {
        "block_class": "net.minecraft.block.StairsBlock",
        "hardness": 1.5,
        "blast_resistance": 6.0,
        "map_color_id": 11,
        "sound_group": "stone",
        "full_cube": False,
        "transparent": True,
        "state_count": 80,
        "default_state": (
            "etherology:attrahite_brick_stairs"
            "[facing=north,half=bottom,shape=straight,waterlogged=false]"
        ),
        "needs_stone_tool": False,
        "block_slab": False,
        "item_slab": False,
        "block_stairs": True,
        "item_stairs": True,
    },
    "etherology:attrahite_bricks": {
        "block_class": "net.minecraft.block.Block",
        "hardness": 1.5,
        "blast_resistance": 6.0,
        "map_color_id": 11,
        "sound_group": "stone",
        "full_cube": True,
        "transparent": False,
        "state_count": 1,
        "default_state": "etherology:attrahite_bricks",
        "needs_stone_tool": False,
        "block_slab": False,
        "item_slab": False,
        "block_stairs": False,
        "item_stairs": False,
    },
}


def _property_summary(spec: dict[str, object]) -> str:
    return (
        f"{spec['block_class']}|item_class={ATTRAHITE_BLOCK_ITEM_CLASS}"
        f"|hardness={spec['hardness']}|blast={spec['blast_resistance']}"
        f"|map_color={spec['map_color_id']}|sound={spec['sound_group']}"
        "|tool_required=true|luminance=0|opaque=true"
        f"|full_cube={str(spec['full_cube']).lower()}"
        f"|transparent={str(spec['transparent']).lower()}|piston=NORMAL"
        f"|state_count={spec['state_count']}|default_state={spec['default_state']}"
        "|max=64"
    )


def _tag_summary(spec: dict[str, object]) -> str:
    return (
        "pickaxe=true"
        f"|needs_stone={str(spec['needs_stone_tool']).lower()}"
        f"|block_slab={str(spec['block_slab']).lower()}"
        f"|item_slab={str(spec['item_slab']).lower()}"
        f"|block_stairs={str(spec['block_stairs']).lower()}"
        f"|item_stairs={str(spec['item_stairs']).lower()}"
    )


ATTRAHITE_PROPERTIES = _canonical_map(
    {identifier: _property_summary(spec) for identifier, spec in _BLOCK_SPECS.items()}
)
ATTRAHITE_TAGS = _canonical_map(
    {identifier: _tag_summary(spec) for identifier, spec in _BLOCK_SPECS.items()}
)
ATTRAHITE_SAVE_REPRESENTATIONS = _canonical_map(
    {identifier: _save_representation(identifier) for identifier in ATTRAHITE_BLOCK_IDS}
)


def _block_entry(identifier: str, spec: dict[str, object]) -> dict[str, object]:
    return {
        "block_id": identifier,
        "item_id": identifier,
        "block_class": spec["block_class"],
        "item_class": ATTRAHITE_BLOCK_ITEM_CLASS,
        "block_item": True,
        "block_item_maps_to_block": True,
        "block_as_item_matches": True,
        "hardness": spec["hardness"],
        "blast_resistance": spec["blast_resistance"],
        "map_color_id": spec["map_color_id"],
        "sound_group": spec["sound_group"],
        "tool_required": True,
        "luminance": 0,
        "opaque": True,
        "full_cube": spec["full_cube"],
        "transparent": spec["transparent"],
        "piston_behavior": "NORMAL",
        "state_count": spec["state_count"],
        "default_state": spec["default_state"],
        "pickaxe_mineable": True,
        "needs_stone_tool": spec["needs_stone_tool"],
        "block_slab": spec["block_slab"],
        "item_slab": spec["item_slab"],
        "block_stairs": spec["block_stairs"],
        "item_stairs": spec["item_stairs"],
        "max_count": 64,
        "serialized_id": identifier,
        "serialized_count": 64,
        "serialized_keys": ["Count", "id"],
        "round_trip_exact": True,
        "save_representation": _save_representation(identifier),
    }


ATTRAHITE_PLACEMENT_POSITIONS = {
    identifier: f"{40 + index},200,16"
    for index, identifier in enumerate(ATTRAHITE_BLOCK_IDS)
}
ATTRAHITE_PLACED_STATES = {
    identifier: str(_BLOCK_SPECS[identifier]["default_state"])
    for identifier in ATTRAHITE_BLOCK_IDS
}
ATTRAHITE_STANDARD_LOOT = {
    identifier: f"{identifier}x1"
    for identifier in ATTRAHITE_BLOCK_IDS
    if identifier != "etherology:attrahite"
}
ATTRAHITE_RAW_FORTUNE_LOOT = {
    "0": (
        "1=none,4096=none,4224=none,"
        "4640=etherology:enriched_attrahitex1,7168=none"
    ),
    "1": (
        "1=none,4096=etherology:enriched_attrahitex1,4224=none,"
        "4640=etherology:enriched_attrahitex1,7168=none"
    ),
    "2": (
        "1=none,4096=etherology:enriched_attrahitex1,"
        "4224=etherology:enriched_attrahitex1,"
        "4640=etherology:enriched_attrahitex1,7168=none"
    ),
    "3": (
        "1=none,4096=etherology:enriched_attrahitex1,"
        "4224=etherology:enriched_attrahitex1,"
        "4640=etherology:enriched_attrahitex1,"
        "7168=etherology:enriched_attrahitex1"
    ),
}


def _cooking_recipe(
    identifier: str,
    class_name: str,
    recipe_type: str,
    input_id: str,
    output: str,
    cook_time: int,
    experience: str,
) -> str:
    return (
        f"{identifier}|class={class_name}|type={recipe_type}"
        f"|serializer={recipe_type}|output={output}|group=|notification=true"
        f"|input={input_id}|input_exact=true|category=misc"
        f"|cook_time={cook_time}|experience={experience}"
        f"|matches=true|crafted={output}"
    )


def _shaped_recipe(
    identifier: str,
    output: str,
    ingredients: str,
    category: str,
    width: int,
    height: int,
) -> str:
    return (
        f"{identifier}|class=net.minecraft.recipe.ShapedRecipe"
        "|type=minecraft:crafting|serializer=minecraft:crafting_shaped"
        f"|output={output}|group=|notification=true|input={ingredients}"
        f"|input_exact=true|category={category}|width={width}|height={height}"
        f"|matches=true|crafted={output}"
    )


def _stonecutting_recipe(identifier: str, output: str) -> str:
    return (
        f"{identifier}|class=net.minecraft.recipe.StonecuttingRecipe"
        "|type=minecraft:stonecutting|serializer=minecraft:stonecutting"
        f"|output={output}|group=|notification=true"
        "|input=etherology:attrahite_bricks|input_exact=true"
        f"|matches=true|crafted={output}"
    )


ATTRAHITE_RECIPES = {
    "etherology:attrahite_brick": _cooking_recipe(
        "etherology:attrahite_brick",
        "net.minecraft.recipe.SmeltingRecipe",
        "minecraft:smelting",
        "etherology:attrahite",
        "etherology:attrahite_brickx1",
        200,
        "0.1",
    ),
    "etherology:attrahite_brick_slab": _shaped_recipe(
        "etherology:attrahite_brick_slab",
        "etherology:attrahite_brick_slabx6",
        ",".join(["etherology:attrahite_bricks"] * 3),
        "building",
        3,
        1,
    ),
    "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting": (
        _stonecutting_recipe(
            "etherology:attrahite_brick_slab_from_attrahite_bricks_stonecutting",
            "etherology:attrahite_brick_slabx2",
        )
    ),
    "etherology:attrahite_brick_stairs": _shaped_recipe(
        "etherology:attrahite_brick_stairs",
        "etherology:attrahite_brick_stairsx4",
        "etherology:attrahite_bricks,empty,empty,"
        "etherology:attrahite_bricks,etherology:attrahite_bricks,empty,"
        "etherology:attrahite_bricks,etherology:attrahite_bricks,"
        "etherology:attrahite_bricks",
        "building",
        3,
        3,
    ),
    "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting": (
        _stonecutting_recipe(
            "etherology:attrahite_brick_stairs_from_attrahite_bricks_stonecutting",
            "etherology:attrahite_brick_stairsx1",
        )
    ),
    "etherology:attrahite_bricks": _shaped_recipe(
        "etherology:attrahite_bricks",
        "etherology:attrahite_bricksx1",
        ",".join(["etherology:attrahite_brick"] * 4),
        "building",
        2,
        2,
    ),
    "etherology:azel_ingot": _cooking_recipe(
        "etherology:azel_ingot",
        "net.minecraft.recipe.SmeltingRecipe",
        "minecraft:smelting",
        "etherology:raw_azel",
        "etherology:azel_ingotx1",
        200,
        "0.3",
    ),
    "etherology:azel_ingot_from_blasting": _cooking_recipe(
        "etherology:azel_ingot_from_blasting",
        "net.minecraft.recipe.BlastingRecipe",
        "minecraft:blasting",
        "etherology:raw_azel",
        "etherology:azel_ingotx1",
        100,
        "0.3",
    ),
    "etherology:raw_azel": _shaped_recipe(
        "etherology:raw_azel",
        "etherology:raw_azelx1",
        "etherology:enriched_attrahite,minecraft:calcite,"
        "minecraft:calcite,etherology:enriched_attrahite",
        "misc",
        2,
        2,
    ),
}
def _advancement_description(advancement_id: str) -> str:
    criterion = (
        "has_attrahite_bricks"
        if "brick_slab" in advancement_id or "brick_stairs" in advancement_id
        else "has_attrahite"
    )
    recipe_id = advancement_id.replace(
        "etherology:recipes/building_blocks/",
        "etherology:",
    ).replace("etherology:recipes/misc/", "etherology:")
    return (
        f"{advancement_id}|parent=minecraft:recipes/root"
        f"|criteria={criterion}+has_the_recipe"
        f"|requirements={criterion}+has_the_recipe"
        f"|reward_recipes={recipe_id}|telemetry=false"
    )


ATTRAHITE_ADVANCEMENTS = {
    advancement_id: _advancement_description(advancement_id)
    for advancement_id in ATTRAHITE_ADVANCEMENT_IDS
}


ATTRAHITE_BLOCKS = {
    "block_registry_id": ATTRAHITE_BLOCK_REGISTRY_ID,
    "item_registry_id": ATTRAHITE_ITEM_REGISTRY_ID,
    "capture_error": "",
    "block_ids": list(ATTRAHITE_BLOCK_IDS),
    "block_item_ids": list(ATTRAHITE_BLOCK_IDS),
    "properties": ATTRAHITE_PROPERTIES,
    "tags": ATTRAHITE_TAGS,
    "save_representations": ATTRAHITE_SAVE_REPRESENTATIONS,
    "entries": {
        identifier: _block_entry(identifier, _BLOCK_SPECS[identifier])
        for identifier in ATTRAHITE_BLOCK_IDS
    },
    "placement": {
        "capture_error": "",
        "positions": copy.deepcopy(ATTRAHITE_PLACEMENT_POSITIONS),
        "placed_block_ids": {
            identifier: identifier for identifier in ATTRAHITE_BLOCK_IDS
        },
        "placed_states": copy.deepcopy(ATTRAHITE_PLACED_STATES),
        "exact": True,
        "world_save_failure": "",
        "world_saved_after_placement": True,
        "stable_after_reload": True,
    },
    "loaded_data": {
        "capture_error": "",
        "loot_table_ids": list(ATTRAHITE_LOOT_TABLE_IDS),
        "standard_loot": copy.deepcopy(ATTRAHITE_STANDARD_LOOT),
        "raw_silk_touch_loot": "etherology:attrahitex1",
        "raw_fortune_loot": copy.deepcopy(ATTRAHITE_RAW_FORTUNE_LOOT),
        "recipe_ids": list(ATTRAHITE_RECIPE_IDS),
        "recipes": copy.deepcopy(ATTRAHITE_RECIPES),
        "recipes_match_and_craft_exact": True,
        "advancement_ids": list(ATTRAHITE_ADVANCEMENT_IDS),
        "advancements": copy.deepcopy(ATTRAHITE_ADVANCEMENTS),
        "exact": True,
        "stable_after_reload": True,
        "fresh_instances_after_reload": True,
    },
    "captured_at_initial_tag_load": True,
    "captured_after_server_data_load": True,
    "same_state_at_server_started": True,
    "registry_stable_after_reload": True,
    "properties_stable_after_reload": True,
    "tags_stable_after_reload": True,
    "stack_nbt_stable_after_reload": True,
    "exact": True,
}


ATTRAHITE_ASSERTION_NAMES = tuple(
    name
    for identifier in ATTRAHITE_BLOCK_IDS
    for name in (f"registry:block:{identifier}", f"registry:block_item:{identifier}")
) + (
    "registry:attrahite_block_ids_exact",
    "registry:attrahite_block_item_ids_exact",
    "attrahite_block_capture_error",
    "attrahite_block_runtime_classes_exact",
    "attrahite_block_item_mappings_exact",
    "attrahite_block_properties_exact",
    "attrahite_block_tags_exact",
    "attrahite_block_stack_nbt_round_trips_exact",
    "attrahite_block_save_representations_exact",
    "attrahite_blocks_captured_at_initial_tag_load",
    "attrahite_blocks_captured_after_server_data_load",
    "server_started_attrahite_blocks_rechecked",
    "attrahite_block_placement_positions_exact",
    "attrahite_block_placed_ids_exact",
    "attrahite_block_placed_states_exact",
    "attrahite_block_placement_exact",
    "attrahite_world_save_failure",
    "attrahite_world_saved_after_placement",
    "attrahite_loaded_data_capture_error",
    "attrahite_loot_table_ids_exact",
    "attrahite_standard_loot_exact",
    "attrahite_raw_silk_touch_loot_exact",
    "attrahite_raw_fortune_scaled_loot_exact",
    "attrahite_recipe_ids_exact",
    "attrahite_recipes_exact",
    "attrahite_recipes_match_and_craft_exact",
    "attrahite_advancement_ids_exact",
    "attrahite_advancements_exact",
    "attrahite_loaded_data_contract_exact",
    "attrahite_block_registry_stable_after_reload",
    "attrahite_block_properties_stable_after_reload",
    "attrahite_block_tags_stable_after_reload",
    "attrahite_block_stack_nbt_stable_after_reload",
    "attrahite_loaded_data_stable_after_reload",
    "attrahite_loaded_data_fresh_after_reload",
    "attrahite_block_placement_stable_after_reload",
)
ATTRAHITE_ASSERTION_VALUES = (
    ("present",) * (len(ATTRAHITE_BLOCK_IDS) * 2)
    + (
        ",".join(ATTRAHITE_BLOCK_IDS),
        ",".join(ATTRAHITE_BLOCK_IDS),
        "none",
        "true",
        "true",
        ATTRAHITE_PROPERTIES,
        ATTRAHITE_TAGS,
        "true",
        ATTRAHITE_SAVE_REPRESENTATIONS,
        "true",
        "true",
        "true",
        _canonical_map(ATTRAHITE_PLACEMENT_POSITIONS),
        _canonical_map({identifier: identifier for identifier in ATTRAHITE_BLOCK_IDS}),
        _canonical_map(ATTRAHITE_PLACED_STATES),
        "true",
        "none",
        "true",
        "none",
        ",".join(ATTRAHITE_LOOT_TABLE_IDS),
        _canonical_map(ATTRAHITE_STANDARD_LOOT),
        "etherology:attrahitex1",
        _canonical_map(ATTRAHITE_RAW_FORTUNE_LOOT),
        ",".join(ATTRAHITE_RECIPE_IDS),
        _canonical_map(ATTRAHITE_RECIPES),
        "true",
        ",".join(ATTRAHITE_ADVANCEMENT_IDS),
        _canonical_map(ATTRAHITE_ADVANCEMENTS),
        "true",
        "true",
        "true",
        "true",
        "true",
        "true",
        "true",
        "true",
    )
)
_ATTRAHITE_ASSERTION_INSERTION_INDEX = (
    contract_v16.EXPECTED_ASSERTION_NAMES.index(
        "metal_block_placement_stable_after_reload"
    )
    + 1
)
EXPECTED_ASSERTION_NAMES = (
    contract_v16.EXPECTED_ASSERTION_NAMES[:_ATTRAHITE_ASSERTION_INSERTION_INDEX]
    + ATTRAHITE_ASSERTION_NAMES
    + contract_v16.EXPECTED_ASSERTION_NAMES[_ATTRAHITE_ASSERTION_INSERTION_INDEX:]
)
EXPECTED_ASSERTION_VALUES = (
    contract_v16.EXPECTED_ASSERTION_VALUES[:_ATTRAHITE_ASSERTION_INSERTION_INDEX]
    + ATTRAHITE_ASSERTION_VALUES
    + contract_v16.EXPECTED_ASSERTION_VALUES[_ATTRAHITE_ASSERTION_INSERTION_INDEX:]
)


class V17ContractError(RuntimeError):
    """Reports an exact profile-v17 report contract violation."""


def _validate_assertions(assertions: object) -> None:
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise V17ContractError("The server probe assertion inventory changed")
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
            raise V17ContractError(
                f"Server probe assertion failed or changed: {name}"
            )


_RELOAD_FIELDS = (
    "attrahite_block_registry_stable",
    "attrahite_block_properties_stable",
    "attrahite_block_tags_stable",
    "attrahite_block_stack_nbt_stable",
    "attrahite_block_loaded_data_stable",
    "attrahite_block_loaded_data_fresh",
    "attrahite_block_placement_stable",
)


def _v16_baseline(report: dict[str, object]) -> dict[str, object]:
    baseline = copy.deepcopy(report)
    baseline.pop("attrahite_blocks", None)
    baseline["schema"] = contract_v16.REPORT_SCHEMA
    baseline["profile_id"] = contract_v16.PROFILE_ID
    baseline["scenario"] = contract_v16.SCENARIO_ID
    assertions = baseline.get("assertions")
    if isinstance(assertions, list):
        baseline["assertions"] = (
            assertions[:_ATTRAHITE_ASSERTION_INSERTION_INDEX]
            + assertions[
                _ATTRAHITE_ASSERTION_INSERTION_INDEX
                + len(ATTRAHITE_ASSERTION_NAMES) :
            ]
        )
    reload = baseline.get("reload")
    if isinstance(reload, dict):
        for field in _RELOAD_FIELDS:
            reload.pop(field, None)
    return baseline


def validate_probe_report(
    report: dict[str, object],
    required_mod_ids: object,
    forbidden_mod_ids: object,
) -> None:
    """Validates the exact profile-v17 report and cumulative v16 projection."""
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
        "attrahite_blocks",
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
        raise V17ContractError("The server probe report field inventory changed")
    for name, expected in {
        "schema": REPORT_SCHEMA,
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
    }.items():
        if not exact_json_value(report.get(name), expected):  # noqa: F405
            raise V17ContractError(
                f"The server probe report {name} value changed"
            )
    if not exact_json_value(report.get("attrahite_blocks"), ATTRAHITE_BLOCKS):  # noqa: F405
        raise V17ContractError("The server probe Attrahite block result changed")
    reload = report.get("reload")
    if not isinstance(reload, dict):
        raise V17ContractError("The server probe reload result changed")
    for field in _RELOAD_FIELDS:
        if not exact_json_value(reload.get(field), True):  # noqa: F405
            raise V17ContractError("The server probe reload result changed")
    _validate_assertions(report.get("assertions"))
    try:
        contract_v16.validate_probe_report(
            _v16_baseline(report),
            required_mod_ids,
            forbidden_mod_ids,
        )
    except contract_v16.V16ContractError as exception:
        raise V17ContractError(str(exception)) from exception
