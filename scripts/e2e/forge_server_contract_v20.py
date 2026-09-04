"""Freeze the Forge 1.20.1 Slitherite server report contract for profile v20."""

from __future__ import annotations

import copy
import hashlib
import importlib.util
from pathlib import Path
import sys
from types import ModuleType

from forge_server_contract_v19 import *  # noqa: F403
import forge_server_contract_v19 as contract_v19


PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v20"
SCENARIO_ID = "slitherite-block-registry"
TASK_PATH = ":forge:1.20.1:runRegistryFoundationServerProbe"
PROFILE_MANIFEST_RELATIVE_PATH = "scripts/e2e/forge-server-1.20.1-profile.json"
PROFILE_SNAPSHOT_RELATIVE_PATH = (
    "scripts/e2e/forge-server-1.20.1-profile-v20.json"
)
PROFILE_MANIFEST_SIZE = 1206
PROFILE_MANIFEST_SHA256 = (
    "1a38dd4e88ee8960df96bcd9d4d074adc8f967c03534bee837b013badc7771be"
)
REPORT_SCHEMA = 12
SLITHERITE_CONTRACT_RELATIVE_PATH = "scripts/e2e/slitherite_client_evidence_contract_v1.py"
SLITHERITE_CONTRACT_PATH = Path(__file__).resolve().parent / (
    "slitherite_client_evidence_contract_v1.py"
)
SLITHERITE_CONTRACT_SIZE = 54314
SLITHERITE_CONTRACT_SHA256 = (
    "4437912482c6276927758f43b0872c01421a482429225bed3f2dc2e838624773"
)
NATIVE_RUN_POSTPONED = True
NATIVE_RUN_POSTPONED_REASON = (
    "Forge dedicated-server Slitherite v20 is postponed until all five related "
    "recipes are present with their real pedestal, alchemy, and lens dependencies"
)


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _load_slitherite_contract() -> ModuleType:
    path = SLITHERITE_CONTRACT_PATH
    if (
        not path.is_file()
        or path.is_symlink()
        or path.stat().st_size != SLITHERITE_CONTRACT_SIZE
        or _sha256_file(path) != SLITHERITE_CONTRACT_SHA256
    ):
        raise RuntimeError("The shared Slitherite evidence contract changed")
    specification = importlib.util.spec_from_file_location(
        "etherology_slitherite_client_evidence_contract_v1_server_v20",
        path,
    )
    if specification is None or specification.loader is None:
        raise RuntimeError("Cannot load the shared Slitherite evidence contract")
    module = importlib.util.module_from_spec(specification)
    sys.modules[specification.name] = module
    specification.loader.exec_module(module)
    return module


slitherite_contract = _load_slitherite_contract()

SLITHERITE_BLOCK_REGISTRY_ID = "minecraft:block"
SLITHERITE_ITEM_REGISTRY_ID = "minecraft:item"
SLITHERITE_BLOCK_ITEM_CLASS = "net.minecraft.item.BlockItem"
SLITHERITE_AGGREGATE_STATE_COUNT = 1262
SLITHERITE_BLOCK_IDS = tuple(slitherite_contract.BLOCK_IDS)
SLITHERITE_LOOT_TABLE_IDS = tuple(slitherite_contract.EXPECTED_LOOT_TABLES)
SLITHERITE_SELF_DROPS = dict(slitherite_contract.EXPECTED_SELF_DROPS)
SLITHERITE_DOUBLE_SLAB_DROPS = dict(
    slitherite_contract.EXPECTED_DOUBLE_SLAB_DROPS
)
SLITHERITE_RECIPE_DESCRIPTIONS = tuple(
    slitherite_contract.EXPECTED_OWNED_RECIPES
)
SLITHERITE_RECIPE_IDS = tuple(
    description.partition("=")[0]
    for description in SLITHERITE_RECIPE_DESCRIPTIONS
)
SLITHERITE_RECIPES = dict(
    zip(
        SLITHERITE_RECIPE_IDS,
        SLITHERITE_RECIPE_DESCRIPTIONS,
        strict=True,
    )
)
SLITHERITE_ADVANCEMENT_IDS = tuple(
    slitherite_contract.EXPECTED_OWNED_ADVANCEMENTS
)
SLITHERITE_RELATED_RECIPE_DESCRIPTIONS = tuple(
    slitherite_contract.EXPECTED_RELATED_RECIPES
)
SLITHERITE_RELATED_RECIPE_IDS = tuple(
    description.partition("=")[0]
    for description in SLITHERITE_RELATED_RECIPE_DESCRIPTIONS
)
SLITHERITE_RELATED_RECIPES = dict(
    zip(
        SLITHERITE_RELATED_RECIPE_IDS,
        SLITHERITE_RELATED_RECIPE_DESCRIPTIONS,
        strict=True,
    )
)

_SLAB_IDS = {
    "etherology:slitherite_slab",
    "etherology:polished_slitherite_slab",
    "etherology:polished_slitherite_brick_slab",
}
_STAIRS_IDS = {
    "etherology:slitherite_stairs",
    "etherology:polished_slitherite_stairs",
    "etherology:polished_slitherite_brick_stairs",
}
_WALL_IDS = {
    "etherology:slitherite_wall",
    "etherology:polished_slitherite_wall",
    "etherology:polished_slitherite_brick_wall",
}
_STONE_BRICK_IDS = {
    "etherology:polished_slitherite_bricks",
    "etherology:chiseled_polished_slitherite_bricks",
    "etherology:cracked_polished_slitherite_bricks",
}
_PRESSURE_PLATE_ID = "etherology:polished_slitherite_pressure_plate"
_BUTTON_ID = "etherology:polished_slitherite_button"
_STATE_COUNTS = {
    f"etherology:{path}": state_count
    for path, _class_name, _properties, state_count
    in slitherite_contract.BASELINE.BLOCK_SPECS
}
_DEFAULT_PROPERTIES = {
    identifier: (
        {"type": "bottom", "waterlogged": "false"}
        if identifier in _SLAB_IDS
        else {
            "facing": "north",
            "half": "bottom",
            "shape": "straight",
            "waterlogged": "false",
        }
        if identifier in _STAIRS_IDS
        else {
            "east": "none",
            "north": "none",
            "south": "none",
            "up": "true",
            "waterlogged": "false",
            "west": "none",
        }
        if identifier in _WALL_IDS
        else {"face": "wall", "facing": "north", "powered": "false"}
        if identifier == _BUTTON_ID
        else {"powered": "false"}
        if identifier == _PRESSURE_PLATE_ID
        else {}
    )
    for identifier in SLITHERITE_BLOCK_IDS
}


def _block_class(identifier: str) -> str:
    if identifier in _SLAB_IDS:
        return "net.minecraft.block.SlabBlock"
    if identifier in _STAIRS_IDS:
        return "net.minecraft.block.StairsBlock"
    if identifier in _WALL_IDS:
        return "net.minecraft.block.WallBlock"
    if identifier == _BUTTON_ID:
        return "net.minecraft.block.ButtonBlock"
    if identifier == _PRESSURE_PLATE_ID:
        return "net.minecraft.block.PressurePlateBlock"
    return "net.minecraft.block.Block"


def _canonical_map(values: dict[str, str]) -> str:
    return ",".join(f"{key}={value}" for key, value in values.items())


def _java_list(values: object) -> str:
    return "[" + ", ".join(str(value) for value in values) + "]"


def _java_map(values: dict[str, str]) -> str:
    return "{" + ", ".join(
        f"{key}={value}" for key, value in values.items()
    ) + "}"


def _default_state(identifier: str) -> str:
    properties = _DEFAULT_PROPERTIES[identifier]
    if not properties:
        return identifier
    return identifier + "[" + _canonical_map(properties) + "]"


def _property_summary(identifier: str) -> str:
    return (
        f"{_block_class(identifier)}|item_class={SLITHERITE_BLOCK_ITEM_CLASS}"
        f"|default={_canonical_map(_DEFAULT_PROPERTIES[identifier])}"
        f"|default_state={_default_state(identifier)}"
        f"|state_count={_STATE_COUNTS[identifier]}"
    )


def _tag_summary(identifier: str) -> str:
    return (
        "pickaxe=true|needs_stone=false"
        f"|block_slab={str(identifier in _SLAB_IDS).lower()}"
        f"|item_slab={str(identifier in _SLAB_IDS).lower()}"
        f"|block_stairs={str(identifier in _STAIRS_IDS).lower()}"
        f"|item_stairs={str(identifier in _STAIRS_IDS).lower()}"
        f"|block_wall={str(identifier in _WALL_IDS).lower()}"
        f"|item_wall={str(identifier in _WALL_IDS).lower()}"
        f"|stone_brick={str(identifier in _STONE_BRICK_IDS).lower()}"
        "|stone_pressure_plate="
        f"{str(identifier == _PRESSURE_PLATE_ID).lower()}|item_button=false"
    )


SLITHERITE_PROPERTIES = _canonical_map(
    {
        identifier: _property_summary(identifier)
        for identifier in SLITHERITE_BLOCK_IDS
    }
)
SLITHERITE_TAGS = _canonical_map(
    {identifier: _tag_summary(identifier) for identifier in SLITHERITE_BLOCK_IDS}
)
SLITHERITE_PLACEMENT_POSITIONS = {
    identifier: f"{64 + index * 2},200,24"
    for index, identifier in enumerate(SLITHERITE_BLOCK_IDS)
}
SLITHERITE_SUPPORT_POSITIONS = {
    identifier: (
        f"{64 + index * 2},200,25"
        if identifier == _BUTTON_ID
        else f"{64 + index * 2},199,24"
    )
    for index, identifier in enumerate(SLITHERITE_BLOCK_IDS)
}
SLITHERITE_PLACED_IDS = {
    identifier: identifier for identifier in SLITHERITE_BLOCK_IDS
}
SLITHERITE_PLACED_STATES = {
    identifier: _default_state(identifier)
    for identifier in SLITHERITE_BLOCK_IDS
}
SLITHERITE_SUPPORT_IDS = {
    identifier: (
        "minecraft:smooth_stone"
        if identifier == _BUTTON_ID
        else "minecraft:polished_andesite"
    )
    for identifier in SLITHERITE_BLOCK_IDS
}


def _native_placement_canonical() -> str:
    return ",".join(
        f"{identifier}=action=CONSUME|accepted=true|before=1|after=0"
        f"|mapping=true|placed={identifier}"
        f"|state={SLITHERITE_PLACED_STATES[identifier]}"
        for identifier in SLITHERITE_BLOCK_IDS
    )


SLITHERITE_NATIVE_PLACEMENT_CANONICAL = _native_placement_canonical()


def _placement() -> dict[str, object]:
    return {
        "capture_error": "",
        "positions": copy.deepcopy(SLITHERITE_PLACEMENT_POSITIONS),
        "support_positions": copy.deepcopy(SLITHERITE_SUPPORT_POSITIONS),
        "placed_block_ids": copy.deepcopy(SLITHERITE_PLACED_IDS),
        "placed_states": copy.deepcopy(SLITHERITE_PLACED_STATES),
        "support_block_ids": copy.deepcopy(SLITHERITE_SUPPORT_IDS),
        "exact": True,
    }


def build_slitherite_blocks(
    raw_id_start: int = 10_000,
    button_elapsed_ticks: int = 20,
) -> dict[str, object]:
    """Builds one valid schema-12 Slitherite result with synthetic raw IDs."""
    entries: dict[str, dict[str, object]] = {}
    next_raw_id = raw_id_start
    for identifier in SLITHERITE_BLOCK_IDS:
        state_count = _STATE_COUNTS[identifier]
        raw_ids = list(range(next_raw_id, next_raw_id + state_count))
        next_raw_id += state_count
        entries[identifier] = {
            "block_id": identifier,
            "item_id": identifier,
            "block_class": _block_class(identifier),
            "item_class": SLITHERITE_BLOCK_ITEM_CLASS,
            "block_item": True,
            "block_item_maps_to_block": True,
            "block_as_item_matches": True,
            "default_properties": copy.deepcopy(
                _DEFAULT_PROPERTIES[identifier]
            ),
            "default_state": _default_state(identifier),
            "state_count": state_count,
            "default_state_raw_id": raw_ids[0],
            "state_raw_ids": raw_ids,
            "pickaxe_mineable": True,
            "needs_stone_tool": False,
            "block_slab": identifier in _SLAB_IDS,
            "item_slab": identifier in _SLAB_IDS,
            "block_stairs": identifier in _STAIRS_IDS,
            "item_stairs": identifier in _STAIRS_IDS,
            "block_wall": identifier in _WALL_IDS,
            "item_wall": identifier in _WALL_IDS,
            "block_stone_brick": identifier in _STONE_BRICK_IDS,
            "block_stone_pressure_plate": identifier == _PRESSURE_PLATE_ID,
            "item_button": False,
        }

    registry = _canonical_map(
        {
            identifier: _property_summary(identifier)
            + f"|default_raw_id={entry['default_state_raw_id']}"
            + f"|raw_ids={len(entry['state_raw_ids'])}"
            for identifier, entry in entries.items()
        }
    )
    native_entries = {
        identifier: {
            "action_result": "CONSUME",
            "accepted": True,
            "before_count": 1,
            "after_count": 0,
            "block_item_mapping": True,
            "placed_id": identifier,
            "placed_state": SLITHERITE_PLACED_STATES[identifier],
            "exact": True,
        }
        for identifier in SLITHERITE_BLOCK_IDS
    }
    behavior = {
        "capture_error": "",
        "completed": True,
        "button_interaction_accepted": True,
        "button_activated": True,
        "button_reset_scheduled": True,
        "button_reset": True,
        "button_elapsed_ticks": button_elapsed_ticks,
        "item_entity_advanced": True,
        "item_ignored": True,
        "living_entity_advanced": True,
        "living_activated": True,
        "pressure_plate_reset": True,
        "fixture_reset_exact": True,
        "button_description": (
            "accepted=true|powered=true|scheduled=true"
            f"|elapsed={button_elapsed_ticks}|reset=true"
        ),
        "pressure_plate_description": (
            "item_advanced=true|item_powered=false|living_advanced=true"
            "|living_powered=true|reset=true"
        ),
        "exact": True,
    }
    return {
        "block_registry_id": SLITHERITE_BLOCK_REGISTRY_ID,
        "item_registry_id": SLITHERITE_ITEM_REGISTRY_ID,
        "capture_error": "",
        "block_ids": list(SLITHERITE_BLOCK_IDS),
        "block_item_ids": list(SLITHERITE_BLOCK_IDS),
        "aggregate_state_count": SLITHERITE_AGGREGATE_STATE_COUNT,
        "aggregate_unique_raw_id_count": SLITHERITE_AGGREGATE_STATE_COUNT,
        "registry": registry,
        "properties": SLITHERITE_PROPERTIES,
        "tags": SLITHERITE_TAGS,
        "entries": entries,
        "native_placement": {
            "capture_error": "",
            "canonical": SLITHERITE_NATIVE_PLACEMENT_CANONICAL,
            "entries": native_entries,
            "exact": True,
        },
        "placement": {
            "initial": _placement(),
            "saved": _placement(),
            "reloaded": _placement(),
            "world_save_failure": "",
            "world_saved_after_placement": True,
            "saved_after_force_save": True,
            "stable_after_reload": True,
        },
        "loaded_data": {
            "capture_error": "",
            "loot_table_ids": list(SLITHERITE_LOOT_TABLE_IDS),
            "self_drops": copy.deepcopy(SLITHERITE_SELF_DROPS),
            "double_slab_drops": copy.deepcopy(
                SLITHERITE_DOUBLE_SLAB_DROPS
            ),
            "recipe_ids": list(SLITHERITE_RECIPE_IDS),
            "recipes": copy.deepcopy(SLITHERITE_RECIPES),
            "advancement_ids": list(SLITHERITE_ADVANCEMENT_IDS),
            "related_recipe_ids": list(SLITHERITE_RELATED_RECIPE_IDS),
            "related_recipes": copy.deepcopy(SLITHERITE_RELATED_RECIPES),
            "exact": True,
            "stable_after_reload": True,
            "fresh_instances_after_reload": True,
        },
        "behavior": behavior,
        "captured_at_initial_tag_load": True,
        "captured_after_server_data_load": True,
        "same_state_at_server_started": True,
        "registry_stable_after_reload": True,
        "default_states_stable_after_reload": True,
        "tags_stable_after_reload": True,
        "exact": True,
    }


_TAG_ASSERTIONS = (
    ("tag:mineable/pickaxe", SLITHERITE_BLOCK_IDS),
    ("tag:needs_stone_tool", ()),
    ("tag:block/slabs", tuple(
        identifier for identifier in SLITHERITE_BLOCK_IDS
        if identifier in _SLAB_IDS
    )),
    ("tag:item/slabs", tuple(
        identifier for identifier in SLITHERITE_BLOCK_IDS
        if identifier in _SLAB_IDS
    )),
    ("tag:block/stairs", tuple(
        identifier for identifier in SLITHERITE_BLOCK_IDS
        if identifier in _STAIRS_IDS
    )),
    ("tag:item/stairs", tuple(
        identifier for identifier in SLITHERITE_BLOCK_IDS
        if identifier in _STAIRS_IDS
    )),
    ("tag:block/walls", tuple(
        identifier for identifier in SLITHERITE_BLOCK_IDS
        if identifier in _WALL_IDS
    )),
    ("tag:item/walls", tuple(
        identifier for identifier in SLITHERITE_BLOCK_IDS
        if identifier in _WALL_IDS
    )),
    ("tag:block/stone_bricks", tuple(
        identifier for identifier in SLITHERITE_BLOCK_IDS
        if identifier in _STONE_BRICK_IDS
    )),
    ("tag:block/stone_pressure_plates", (_PRESSURE_PLATE_ID,)),
    ("tag:item/buttons", ()),
)
_PER_BLOCK_ASSERTION_NAMES = tuple(
    name
    for identifier in SLITHERITE_BLOCK_IDS
    for name in (
        f"registry:block:{identifier}",
        f"registry:item:{identifier}",
        f"runtime:block_class:{identifier}",
        f"runtime:block_item_class:{identifier}",
        f"block_item_mapping:{identifier}",
        f"default_state:{identifier}",
        f"state_count:{identifier}",
        f"default_state_network_id:{identifier}",
        f"slitherite_state_raw_ids_exact:{identifier}",
    )
)
_PER_BLOCK_ASSERTION_VALUES = tuple(
    value
    for identifier in SLITHERITE_BLOCK_IDS
    for value in (
        "present",
        "present",
        _block_class(identifier),
        SLITHERITE_BLOCK_ITEM_CLASS,
        "true",
        _default_state(identifier),
        str(_STATE_COUNTS[identifier]),
        "non-negative",
        "true",
    )
)
SLITHERITE_ASSERTION_NAMES = _PER_BLOCK_ASSERTION_NAMES + (
    "registry:slitherite_block_ids_exact",
    "registry:slitherite_block_item_ids_exact",
    "slitherite_block_capture_error",
    "slitherite_block_runtime_classes_exact",
    "slitherite_block_item_mappings_exact",
    "slitherite_block_properties_exact",
    "slitherite_aggregate_state_count_exact",
    "slitherite_aggregate_unique_raw_id_count_exact",
    "slitherite_state_raw_ids_aggregate_exact",
    "slitherite_state_network_ids_exact",
    "slitherite_block_tags_exact",
    *(name for name, _expected in _TAG_ASSERTIONS),
    "slitherite_blocks_captured_at_initial_tag_load",
    "slitherite_blocks_captured_after_server_data_load",
    "server_started_slitherite_blocks_rechecked",
    "slitherite_native_placement_capture_error",
    "slitherite_native_block_item_placements_exact",
    "slitherite_native_block_item_placement_contract_exact",
    "direct_block_item_placements_exact",
    "slitherite_fixture_capture_error",
    "slitherite_fixture_positions_exact",
    "slitherite_fixture_support_positions_exact",
    "slitherite_fixture_placed_ids_exact",
    "slitherite_fixture_placed_states_exact",
    "slitherite_fixture_support_ids_exact",
    "slitherite_fixture_placement_exact",
    "initial_server_fixture_exact",
    "slitherite_behavior_capture_error",
    "slitherite_button_pulse_reset_exact",
    "slitherite_pressure_plate_entities_exact",
    "slitherite_behavior_fixture_reset_exact",
    "slitherite_behavior_contract_exact",
    "slitherite_world_save_failure",
    "slitherite_world_saved_after_placement",
    "forced_world_save",
    "slitherite_fixture_saved_after_force_save",
    "slitherite_loaded_data_capture_error",
    "slitherite_loot_tables_exact",
    "slitherite_self_drops_exact",
    "slitherite_double_slab_drops_x1_exact",
    "slitherite_owned_recipe_ids_exact",
    "slitherite_owned_recipes_exact",
    "slitherite_owned_advancements_exact",
    "slitherite_related_recipe_ids_exact",
    "slitherite_related_recipes_recorded_not_owned",
    "slitherite_loaded_data_contract_exact",
    "slitherite_block_registry_stable_after_reload",
    "slitherite_block_default_states_stable_after_reload",
    "slitherite_block_tags_stable_after_reload",
    "slitherite_loaded_data_stable_after_reload",
    "slitherite_loaded_data_fresh_after_reload",
    "slitherite_fixture_stable_after_reload",
)
SLITHERITE_ASSERTION_VALUES = _PER_BLOCK_ASSERTION_VALUES + (
    ",".join(SLITHERITE_BLOCK_IDS),
    ",".join(SLITHERITE_BLOCK_IDS),
    "none",
    "true",
    "true",
    SLITHERITE_PROPERTIES,
    str(SLITHERITE_AGGREGATE_STATE_COUNT),
    str(SLITHERITE_AGGREGATE_STATE_COUNT),
    "true",
    "true",
    SLITHERITE_TAGS,
    *(_java_list(expected) for _name, expected in _TAG_ASSERTIONS),
    "true",
    "true",
    "true",
    "none",
    SLITHERITE_NATIVE_PLACEMENT_CANONICAL,
    "true",
    "true",
    "none",
    _java_map(SLITHERITE_PLACEMENT_POSITIONS),
    _java_map(SLITHERITE_SUPPORT_POSITIONS),
    _java_map(SLITHERITE_PLACED_IDS),
    _java_map(SLITHERITE_PLACED_STATES),
    _java_map(SLITHERITE_SUPPORT_IDS),
    "true",
    "true",
    "none",
    "true",
    "true",
    "true",
    "true",
    "none",
    "true",
    "true",
    "true",
    "none",
    ",".join(SLITHERITE_LOOT_TABLE_IDS),
    _canonical_map(SLITHERITE_SELF_DROPS),
    _canonical_map(SLITHERITE_DOUBLE_SLAB_DROPS),
    ",".join(SLITHERITE_RECIPE_IDS),
    ",".join(SLITHERITE_RECIPE_DESCRIPTIONS),
    ",".join(SLITHERITE_ADVANCEMENT_IDS),
    ",".join(SLITHERITE_RELATED_RECIPE_IDS),
    ",".join(SLITHERITE_RELATED_RECIPE_DESCRIPTIONS),
    "true",
    "true",
    "true",
    "true",
    "true",
    "true",
    "true",
)
_SLITHERITE_ASSERTION_INSERTION_INDEX = (
    contract_v19.EXPECTED_ASSERTION_NAMES.index(
        "attrahite_block_placement_stable_after_reload"
    )
    + 1
)
EXPECTED_ASSERTION_NAMES = (
    contract_v19.EXPECTED_ASSERTION_NAMES[:_SLITHERITE_ASSERTION_INSERTION_INDEX]
    + SLITHERITE_ASSERTION_NAMES
    + contract_v19.EXPECTED_ASSERTION_NAMES[_SLITHERITE_ASSERTION_INSERTION_INDEX:]
)
EXPECTED_ASSERTION_VALUES = (
    contract_v19.EXPECTED_ASSERTION_VALUES[:_SLITHERITE_ASSERTION_INSERTION_INDEX]
    + SLITHERITE_ASSERTION_VALUES
    + contract_v19.EXPECTED_ASSERTION_VALUES[_SLITHERITE_ASSERTION_INSERTION_INDEX:]
)
_SLITHERITE_RELOAD_FIELDS = (
    "slitherite_block_registry_stable",
    "slitherite_block_default_states_stable",
    "slitherite_block_tags_stable",
    "slitherite_block_loaded_data_stable",
    "slitherite_block_loaded_data_fresh",
    "slitherite_block_placement_stable",
)


class V20ContractError(RuntimeError):
    """Reports an exact profile-v20 report contract violation."""


def _validate_slitherite_blocks(value: object) -> None:
    if not isinstance(value, dict):
        raise V20ContractError("The server probe Slitherite result changed")
    entries = value.get("entries")
    if not isinstance(entries, dict) or tuple(entries) != SLITHERITE_BLOCK_IDS:
        raise V20ContractError("The server probe Slitherite entry inventory changed")

    all_raw_ids: list[int] = []
    for identifier in SLITHERITE_BLOCK_IDS:
        entry = entries.get(identifier)
        if not isinstance(entry, dict):
            raise V20ContractError(
                f"The server probe Slitherite entry changed: {identifier}"
            )
        raw_ids = entry.get("state_raw_ids")
        default_raw_id = entry.get("default_state_raw_id")
        if (
            not isinstance(raw_ids, list)
            or len(raw_ids) != _STATE_COUNTS[identifier]
            or any(type(raw_id) is not int or raw_id < 0 for raw_id in raw_ids)
            or raw_ids != sorted(raw_ids)
            or len(set(raw_ids)) != len(raw_ids)
            or type(default_raw_id) is not int
            or default_raw_id not in raw_ids
        ):
            raise V20ContractError(
                f"The server probe Slitherite raw IDs changed: {identifier}"
            )
        all_raw_ids.extend(raw_ids)
    if (
        len(all_raw_ids) != SLITHERITE_AGGREGATE_STATE_COUNT
        or len(set(all_raw_ids)) != SLITHERITE_AGGREGATE_STATE_COUNT
    ):
        raise V20ContractError("The server probe Slitherite aggregate raw IDs changed")

    behavior = value.get("behavior")
    elapsed = behavior.get("button_elapsed_ticks") if isinstance(behavior, dict) else None
    if type(elapsed) is not int or elapsed < 20:
        raise V20ContractError("The server probe Slitherite button timing changed")

    expected = build_slitherite_blocks(button_elapsed_ticks=elapsed)
    expected_entries = expected["entries"]
    assert isinstance(expected_entries, dict)
    for identifier in SLITHERITE_BLOCK_IDS:
        actual_entry = entries[identifier]
        expected_entry = expected_entries[identifier]
        assert isinstance(actual_entry, dict)
        assert isinstance(expected_entry, dict)
        expected_entry["default_state_raw_id"] = actual_entry["default_state_raw_id"]
        expected_entry["state_raw_ids"] = copy.deepcopy(actual_entry["state_raw_ids"])
    expected["registry"] = _canonical_map(
        {
            identifier: _property_summary(identifier)
            + f"|default_raw_id={entries[identifier]['default_state_raw_id']}"
            + f"|raw_ids={len(entries[identifier]['state_raw_ids'])}"
            for identifier in SLITHERITE_BLOCK_IDS
        }
    )
    if not exact_json_value(value, expected):  # noqa: F405
        raise V20ContractError("The server probe Slitherite result changed")


def _validate_assertions(assertions: object) -> None:
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise V20ContractError("The server probe assertion inventory changed")
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
            raise V20ContractError(
                f"Server probe assertion failed or changed: {name}"
            )


def _v19_baseline(report: dict[str, object]) -> dict[str, object]:
    baseline = copy.deepcopy(report)
    baseline.pop("slitherite_blocks", None)
    baseline["schema"] = contract_v19.REPORT_SCHEMA
    baseline["profile_id"] = contract_v19.PROFILE_ID
    baseline["scenario"] = contract_v19.SCENARIO_ID
    assertions = baseline.get("assertions")
    if isinstance(assertions, list):
        baseline["assertions"] = (
            assertions[:_SLITHERITE_ASSERTION_INSERTION_INDEX]
            + assertions[
                _SLITHERITE_ASSERTION_INSERTION_INDEX
                + len(SLITHERITE_ASSERTION_NAMES):
            ]
        )
    reload = baseline.get("reload")
    if isinstance(reload, dict):
        for field in _SLITHERITE_RELOAD_FIELDS:
            reload.pop(field, None)
    return baseline


def validate_probe_report(
    report: dict[str, object],
    required_mod_ids: object,
    forbidden_mod_ids: object,
) -> None:
    """Validates exact schema 12 and its cumulative schema-11 projection."""
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
        "slitherite_blocks",
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
        raise V20ContractError("The server probe report field inventory changed")
    for name, expected in {
        "schema": REPORT_SCHEMA,
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
    }.items():
        if not exact_json_value(report.get(name), expected):  # noqa: F405
            raise V20ContractError(
                f"The server probe report {name} value changed"
            )
    _validate_slitherite_blocks(report.get("slitherite_blocks"))
    reload = report.get("reload")
    if not isinstance(reload, dict):
        raise V20ContractError("The server probe reload result changed")
    for field in _SLITHERITE_RELOAD_FIELDS:
        if not exact_json_value(reload.get(field), True):  # noqa: F405
            raise V20ContractError("The server probe reload result changed")
    _validate_assertions(report.get("assertions"))
    try:
        contract_v19.validate_probe_report(
            _v19_baseline(report),
            required_mod_ids,
            forbidden_mod_ids,
        )
    except contract_v19.V19ContractError as exception:
        raise V20ContractError(str(exception)) from exception
