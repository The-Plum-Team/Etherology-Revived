#!/usr/bin/env python3
"""Strict verifier for the immutable original published-0.1.7 Slitherite v8 lane."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
from typing import Callable, Protocol


SCENARIO_ID = "slitherite-block-registry"
PROFILE_ID = "etherology-original-fabric-1.21.1-published-0.1.7-v8"
PROFILE_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v8.json"
)
HARNESS_VERSION = "1.3.3"
INITIAL_SCREENSHOT_FILE = "slitherite-block-registry-initial.png"
REOPENED_SCREENSHOT_FILE = "slitherite-block-registry-reopened.png"
SCREENSHOT_FILES = (INITIAL_SCREENSHOT_FILE, REOPENED_SCREENSHOT_FILE)
PHASES = ("initial", "reopened")
EXPECTED_SCREENSHOTS = tuple(
    (phase, f"screenshots/{file_name}")
    for phase, file_name in zip(PHASES, SCREENSHOT_FILES, strict=True)
)
EXPECTED_FRAMEBUFFER = (1920, 1080)
REQUIRED_STABLE_RENDERS = 120
REQUIRED_LIGHTING_READY_CLIENT_TICKS = 20
MINIMUM_MEAN_LUMINANCE = 110.0
MAXIMUM_DARK_PIXEL_RATIO = 0.20
DARK_PIXEL_LUMINANCE_THRESHOLD = 48.0
MAXIMUM_MISSING_TEXTURE_PIXELS = 0
MAXIMUM_REOPEN_CHANGED_PIXEL_RATIO = 0.35
BLOCK_ITEM_CLASS = "net.minecraft.class_1747"
EXPECTED_WORLD = {
    "save_directory": "etherology-original-slitherite-block-registry-world",
    "display_name": "Etherology Original 0.1.7 Slitherite Blocks",
    "seed": 4995697409260082224,
    "dimension": "minecraft:overworld",
    "integrated": True,
    "reopened": True,
}
BLOCK_SPECS = (
    ("slitherite", "net.minecraft.class_2248", "{}", 1),
    (
        "slitherite_stairs",
        "net.minecraft.class_2510",
        "{facing=north, half=bottom, shape=straight, waterlogged=false}",
        80,
    ),
    (
        "slitherite_slab",
        "net.minecraft.class_2482",
        "{type=bottom, waterlogged=false}",
        6,
    ),
    (
        "slitherite_wall",
        "net.minecraft.class_2544",
        "{east=none, north=none, south=none, up=true, waterlogged=false, west=none}",
        324,
    ),
    ("polished_slitherite", "net.minecraft.class_2248", "{}", 1),
    (
        "polished_slitherite_stairs",
        "net.minecraft.class_2510",
        "{facing=north, half=bottom, shape=straight, waterlogged=false}",
        80,
    ),
    (
        "polished_slitherite_slab",
        "net.minecraft.class_2482",
        "{type=bottom, waterlogged=false}",
        6,
    ),
    (
        "polished_slitherite_wall",
        "net.minecraft.class_2544",
        "{east=none, north=none, south=none, up=true, waterlogged=false, west=none}",
        324,
    ),
    (
        "polished_slitherite_button",
        "net.minecraft.class_2269",
        "{face=wall, facing=north, powered=false}",
        24,
    ),
    (
        "polished_slitherite_pressure_plate",
        "net.minecraft.class_2440",
        "{powered=false}",
        2,
    ),
    ("polished_slitherite_bricks", "net.minecraft.class_2248", "{}", 1),
    (
        "polished_slitherite_brick_stairs",
        "net.minecraft.class_2510",
        "{facing=north, half=bottom, shape=straight, waterlogged=false}",
        80,
    ),
    (
        "polished_slitherite_brick_slab",
        "net.minecraft.class_2482",
        "{type=bottom, waterlogged=false}",
        6,
    ),
    (
        "polished_slitherite_brick_wall",
        "net.minecraft.class_2544",
        "{east=none, north=none, south=none, up=true, waterlogged=false, west=none}",
        324,
    ),
    ("chiseled_polished_slitherite", "net.minecraft.class_2248", "{}", 1),
    (
        "chiseled_polished_slitherite_bricks",
        "net.minecraft.class_2248",
        "{}",
        1,
    ),
    (
        "cracked_polished_slitherite_bricks",
        "net.minecraft.class_2248",
        "{}",
        1,
    ),
)
BLOCK_IDS = tuple(f"etherology:{path}" for path, _class, _state, _count in BLOCK_SPECS)
SLAB_IDS = tuple(value for value in BLOCK_IDS if value.endswith("_slab"))
STAIR_IDS = tuple(value for value in BLOCK_IDS if value.endswith("_stairs"))
WALL_IDS = tuple(value for value in BLOCK_IDS if value.endswith("_wall"))
STONE_BRICK_IDS = (
    "etherology:polished_slitherite_bricks",
    "etherology:chiseled_polished_slitherite_bricks",
    "etherology:cracked_polished_slitherite_bricks",
)


def _resource_inventory() -> tuple[str, ...]:
    block_models = (
        "chiseled_polished_slitherite",
        "chiseled_polished_slitherite_bricks",
        "cracked_polished_slitherite_bricks",
        "polished_slitherite",
        "polished_slitherite_brick_slab",
        "polished_slitherite_brick_slab_top",
        "polished_slitherite_brick_stairs",
        "polished_slitherite_brick_stairs_inner",
        "polished_slitherite_brick_stairs_outer",
        "polished_slitherite_brick_wall_inventory",
        "polished_slitherite_brick_wall_post",
        "polished_slitherite_brick_wall_side",
        "polished_slitherite_brick_wall_side_tall",
        "polished_slitherite_bricks",
        "polished_slitherite_button",
        "polished_slitherite_button_inventory",
        "polished_slitherite_button_pressed",
        "polished_slitherite_pressure_plate",
        "polished_slitherite_pressure_plate_down",
        "polished_slitherite_slab",
        "polished_slitherite_slab_top",
        "polished_slitherite_stairs",
        "polished_slitherite_stairs_inner",
        "polished_slitherite_stairs_outer",
        "polished_slitherite_wall_inventory",
        "polished_slitherite_wall_post",
        "polished_slitherite_wall_side",
        "polished_slitherite_wall_side_tall",
        "slitherite",
        "slitherite_slab",
        "slitherite_slab_top",
        "slitherite_stairs",
        "slitherite_stairs_inner",
        "slitherite_stairs_outer",
        "slitherite_wall_inventory",
        "slitherite_wall_post",
        "slitherite_wall_side",
        "slitherite_wall_side_tall",
    )
    textures = (
        "chiseled_polished_slitherite",
        "chiseled_polished_slitherite_bricks_side",
        "chiseled_polished_slitherite_bricks_top",
        "cracked_polished_slitherite_bricks",
        "polished_slitherite",
        "polished_slitherite_bricks",
        "slitherite",
    )
    values = (
        "minecraft:texts/splashes.txt",
        *(f"etherology:blockstates/{value.removeprefix('etherology:')}.json" for value in BLOCK_IDS),
        *(f"etherology:models/block/{value}.json" for value in block_models),
        *(f"etherology:models/item/{value.removeprefix('etherology:')}.json" for value in BLOCK_IDS),
        *(f"etherology:textures/block/{value}.png" for value in textures),
    )
    if len(values) != 80 or len(set(values)) != 80:
        raise RuntimeError("Slitherite visual resource inventory drifted")
    return values


EXPECTED_RESOURCES = _resource_inventory()


def _recipe(
    path: str, recipe_type: str, result: str, count: int
) -> str:
    return f"etherology:{path}={recipe_type}->{result}x{count}"


EXPECTED_OWNED_RECIPES = (
    _recipe("chiseled_polished_slitherite", "minecraft:crafting", "etherology:chiseled_polished_slitherite", 1),
    _recipe("chiseled_polished_slitherite_bricks", "minecraft:crafting", "etherology:chiseled_polished_slitherite_bricks", 1),
    _recipe("chiseled_polished_slitherite_bricks_from_polished_slitherite_bricks_stonecutting", "minecraft:stonecutting", "etherology:chiseled_polished_slitherite_bricks", 1),
    _recipe("chiseled_polished_slitherite_from_polished_slitherite_stonecutting", "minecraft:stonecutting", "etherology:chiseled_polished_slitherite", 1),
    _recipe("cracked_polished_slitherite_bricks", "minecraft:smelting", "etherology:cracked_polished_slitherite_bricks", 1),
    _recipe("polished_slitherite", "minecraft:crafting", "etherology:polished_slitherite", 4),
    _recipe("polished_slitherite_brick_slab", "minecraft:crafting", "etherology:polished_slitherite_brick_slab", 6),
    _recipe("polished_slitherite_brick_slab_from_polished_slitherite_bricks_stonecutting", "minecraft:stonecutting", "etherology:polished_slitherite_brick_slab", 2),
    _recipe("polished_slitherite_brick_stairs", "minecraft:crafting", "etherology:polished_slitherite_brick_stairs", 4),
    _recipe("polished_slitherite_brick_stairs_from_polished_slitherite_bricks_stonecutting", "minecraft:stonecutting", "etherology:polished_slitherite_brick_stairs", 1),
    _recipe("polished_slitherite_brick_wall", "minecraft:crafting", "etherology:polished_slitherite_brick_wall", 6),
    _recipe("polished_slitherite_brick_wall_from_polished_slitherite_bricks_stonecutting", "minecraft:stonecutting", "etherology:polished_slitherite_brick_wall", 1),
    _recipe("polished_slitherite_bricks", "minecraft:crafting", "etherology:polished_slitherite_bricks", 4),
    _recipe("polished_slitherite_bricks_from_polished_slitherite_stonecutting", "minecraft:stonecutting", "etherology:polished_slitherite_bricks", 1),
    _recipe("polished_slitherite_button", "minecraft:crafting", "etherology:polished_slitherite_button", 1),
    _recipe("polished_slitherite_from_slitherite_stonecutting", "minecraft:stonecutting", "etherology:polished_slitherite", 1),
    _recipe("polished_slitherite_pressure_plate", "minecraft:crafting", "etherology:polished_slitherite_pressure_plate", 1),
    _recipe("polished_slitherite_slab", "minecraft:crafting", "etherology:polished_slitherite_slab", 6),
    _recipe("polished_slitherite_slab_from_polished_slitherite_stonecutting", "minecraft:stonecutting", "etherology:polished_slitherite_slab", 2),
    _recipe("polished_slitherite_stairs", "minecraft:crafting", "etherology:polished_slitherite_stairs", 4),
    _recipe("polished_slitherite_stairs_from_polished_slitherite_stonecutting", "minecraft:stonecutting", "etherology:polished_slitherite_stairs", 1),
    _recipe("polished_slitherite_wall", "minecraft:crafting", "etherology:polished_slitherite_wall", 6),
    _recipe("polished_slitherite_wall_from_polished_slitherite_stonecutting", "minecraft:stonecutting", "etherology:polished_slitherite_wall", 1),
    _recipe("slitherite_slab", "minecraft:crafting", "etherology:slitherite_slab", 6),
    _recipe("slitherite_slab_from_slitherite_stonecutting", "minecraft:stonecutting", "etherology:slitherite_slab", 2),
    _recipe("slitherite_stairs", "minecraft:crafting", "etherology:slitherite_stairs", 4),
    _recipe("slitherite_stairs_from_slitherite_stonecutting", "minecraft:stonecutting", "etherology:slitherite_stairs", 1),
    _recipe("slitherite_wall", "minecraft:crafting", "etherology:slitherite_wall", 6),
    _recipe("slitherite_wall_from_slitherite_stonecutting", "minecraft:stonecutting", "etherology:slitherite_wall", 1),
)
EXPECTED_RELATED_RECIPES = (
    _recipe("comparator", "minecraft:crafting", "minecraft:comparator", 1),
    _recipe("repeater", "minecraft:crafting", "minecraft:repeater", 1),
    _recipe("stonecutter", "minecraft:crafting", "minecraft:stonecutter", 1),
    _recipe("pedestal", "minecraft:crafting", "etherology:pedestal", 2),
    _recipe("unadjusted_lens", "etherology:alchemy_recipe", "etherology:unadjusted_lens", 1),
)

EXPECTED_OWNED_ADVANCEMENTS = (
    "etherology:recipes/building_blocks/chiseled_polished_slitherite",
    "etherology:recipes/building_blocks/chiseled_polished_slitherite_bricks",
    (
        "etherology:recipes/building_blocks/"
        "chiseled_polished_slitherite_bricks_from_"
        "polished_slitherite_bricks_stonecutting"
    ),
    (
        "etherology:recipes/building_blocks/"
        "chiseled_polished_slitherite_from_polished_slitherite_stonecutting"
    ),
    "etherology:recipes/building_blocks/cracked_polished_slitherite_bricks",
    "etherology:recipes/building_blocks/polished_slitherite",
    "etherology:recipes/building_blocks/polished_slitherite_brick_slab",
    (
        "etherology:recipes/building_blocks/polished_slitherite_brick_slab_from_"
        "polished_slitherite_bricks_stonecutting"
    ),
    "etherology:recipes/building_blocks/polished_slitherite_brick_stairs",
    (
        "etherology:recipes/building_blocks/polished_slitherite_brick_stairs_from_"
        "polished_slitherite_bricks_stonecutting"
    ),
    "etherology:recipes/building_blocks/polished_slitherite_bricks",
    (
        "etherology:recipes/building_blocks/polished_slitherite_bricks_from_"
        "polished_slitherite_stonecutting"
    ),
    (
        "etherology:recipes/building_blocks/"
        "polished_slitherite_from_slitherite_stonecutting"
    ),
    "etherology:recipes/building_blocks/polished_slitherite_slab",
    (
        "etherology:recipes/building_blocks/polished_slitherite_slab_from_"
        "polished_slitherite_stonecutting"
    ),
    "etherology:recipes/building_blocks/polished_slitherite_stairs",
    (
        "etherology:recipes/building_blocks/polished_slitherite_stairs_from_"
        "polished_slitherite_stonecutting"
    ),
    "etherology:recipes/building_blocks/slitherite_slab",
    (
        "etherology:recipes/building_blocks/"
        "slitherite_slab_from_slitherite_stonecutting"
    ),
    "etherology:recipes/building_blocks/slitherite_stairs",
    (
        "etherology:recipes/building_blocks/"
        "slitherite_stairs_from_slitherite_stonecutting"
    ),
    "etherology:recipes/decorations/polished_slitherite_brick_wall",
    (
        "etherology:recipes/decorations/polished_slitherite_brick_wall_from_"
        "polished_slitherite_bricks_stonecutting"
    ),
    "etherology:recipes/decorations/polished_slitherite_wall",
    (
        "etherology:recipes/decorations/polished_slitherite_wall_from_"
        "polished_slitherite_stonecutting"
    ),
    "etherology:recipes/decorations/slitherite_wall",
    (
        "etherology:recipes/decorations/"
        "slitherite_wall_from_slitherite_stonecutting"
    ),
    "etherology:recipes/redstone/polished_slitherite_button",
    "etherology:recipes/redstone/polished_slitherite_pressure_plate",
)
EXPECTED_LOOT_TABLES = tuple(
    sorted(f"etherology:blocks/{block_id.removeprefix('etherology:')}" for block_id in BLOCK_IDS)
)
EXPECTED_SELF_DROPS = {
    block_id: f"{block_id}x1" for block_id in sorted(BLOCK_IDS)
}
EXPECTED_DOUBLE_SLAB_DROPS = {
    block_id: f"{block_id}x1" for block_id in sorted(SLAB_IDS)
}


def _java_list(values: tuple[str, ...] | list[str]) -> str:
    return "[" + ", ".join(values) + "]"


def _java_map(values: dict[str, str]) -> str:
    return "{" + ", ".join(f"{key}={value}" for key, value in values.items()) + "}"


EXPECTED_TAGS = (
    f"mineable/pickaxe={_java_list(BLOCK_IDS)}",
    "needs_stone_tool=[]",
    f"block/slabs={_java_list(SLAB_IDS)}",
    f"item/slabs={_java_list(SLAB_IDS)}",
    f"block/stairs={_java_list(STAIR_IDS)}",
    f"item/stairs={_java_list(STAIR_IDS)}",
    f"block/walls={_java_list(WALL_IDS)}",
    f"item/walls={_java_list(WALL_IDS)}",
    f"block/stone_bricks={_java_list(STONE_BRICK_IDS)}",
    (
        "block/stone_pressure_plates="
        "[etherology:polished_slitherite_pressure_plate]"
    ),
    "item/buttons=[]",
)
EXPECTED_LOCAL_LIGHT_SAMPLES = (
    "sky=["
    "0,121,-15=15,"
    + ",".join(
        f"{x},123,{z}=15"
        for x, z in (
            *((x, 2) for x in range(-8, 9, 2)),
            *((x, 6) for x in range(-7, 8, 2)),
        )
    )
    + "];block=[0,121,-15=14,0,121,-8=14]"
)
EXPECTED_LIGHTING_DESCRIPTION = (
    "20 fresh paired local server/client samples;sky=15;block=14;"
    "globalPending=diagnostic-only"
)
EXPECTED_CAMERA = (
    "first_person=true;x=0.5;y=121.0;z=-14.5;"
    "yaw=0.0;pitch=8.0;on_ground=true;tolerance=1.0E-4"
)
EXPECTED_SLITHERITE_FIELDS = {
    "block_ids",
    "registry",
    "aggregate_state_count",
    "canonical_resources",
    "tags",
    "loot_tables",
    "self_drops",
    "double_slab_drops",
    "owned_recipes",
    "owned_advancements",
    "related_recipes_recorded_not_owned",
    "placements",
    "button_behavior",
    "pressure_plate_behavior",
    "initial_snapshot",
    "reopened_snapshot",
    "persistence_exact",
    "reopened_data_exact",
    "required_stable_renders",
    "required_lighting_ready_client_ticks",
}


def _assertion_names() -> tuple[str, ...]:
    names = ["fabric_mod_loaded:etherology"]
    for block_id in BLOCK_IDS:
        names.extend(
            (
                f"registry:block:{block_id}",
                f"registry:item:{block_id}",
                f"runtime:block_class:{block_id}",
                f"runtime:block_item_class:{block_id}",
                f"block_item_mapping:{block_id}",
                f"default_state:{block_id}",
                f"state_count:{block_id}",
                f"default_state_network_id:{block_id}",
            )
        )
    names.extend(
        (
            "slitherite_canonical_resources_exact",
            "slitherite_state_network_ids_exact",
            "packaged_root_jar:etherology",
            "packaged_root_jar:etherology_original_baseline_harness",
            "integrated_world_joined",
            "server_arena_chunks_loaded",
            "tag:mineable/pickaxe",
            "tag:needs_stone_tool",
            "tag:block/slabs",
            "tag:item/slabs",
            "tag:block/stairs",
            "tag:item/stairs",
            "tag:block/walls",
            "tag:item/walls",
            "tag:block/stone_bricks",
            "tag:block/stone_pressure_plates",
            "tag:item/buttons",
            "slitherite_loot_tables_exact",
            "slitherite_self_drops_exact",
            "slitherite_double_slab_drops_x1_exact",
            "slitherite_owned_recipes_exact",
            "slitherite_owned_advancements_exact",
            "slitherite_related_recipes_recorded_not_owned",
            "direct_block_item_placements_exact",
            "slitherite_button_pulse_reset_exact",
            "slitherite_pressure_plate_entities_exact",
            "initial_server_fixture_exact",
            "live_world_identity",
            "forced_world_save",
            "restart_fixture_persistence_exact",
            "restart_loaded_data_exact",
        )
    )
    for phase in PHASES:
        names.extend(
            (
                f"capture_mirror_exact:{phase}",
                f"capture_render_ready:{phase}",
                f"capture_lighting_ready:{phase}",
                f"capture_camera_exact:{phase}",
                f"capture_consecutive_stable_renders:{phase}",
                f"capture_framebuffer_dimensions:{phase}",
                f"native_screenshot_written:{phase}",
            )
        )
    names.append("isolated_save_directory_present")
    if len(names) != 183 or len(set(names)) != 183:
        raise RuntimeError("Slitherite assertion inventory drifted")
    return tuple(names)


EXPECTED_ASSERTION_NAMES = _assertion_names()


class PngLike(Protocol):
    """The bounded RGB image interface supplied by the baseline controller."""

    width: int
    height: int
    pixels: bytes


@dataclass(frozen=True)
class SlitheriteEvidenceSummary:
    """Strictly verified evidence values surfaced to the controller."""

    assertion_count: int
    screenshot_count: int
    initial_mean_luminance: float
    reopened_mean_luminance: float
    reopen_changed_pixel_ratio: float


class SlitheriteEvidenceError(RuntimeError):
    """Reports a fail-closed Slitherite v8 evidence-contract violation."""


def _fail(message: str, error_type: type[Exception]) -> None:
    raise error_type(f"Original Slitherite v8 evidence: {message}")


def _require_exact_fields(
    value: object,
    expected: set[str],
    description: str,
    error_type: type[Exception],
) -> dict[str, object]:
    if not isinstance(value, dict) or set(value) != expected:
        _fail(f"{description} has a non-exact field inventory", error_type)
    return value


def _parse_java_map(
    value: str, description: str, error_type: type[Exception]
) -> dict[str, str]:
    if value == "{}":
        return {}
    if not value.startswith("{") or not value.endswith("}"):
        _fail(f"{description} is not a Java map description", error_type)
    result: dict[str, str] = {}
    for entry in value[1:-1].split(", "):
        key, separator, item = entry.partition("=")
        if not separator or not key or not item or key in result:
            _fail(f"{description} contains an invalid map entry", error_type)
        result[key] = item
    return result


def _default_properties(value: str) -> dict[str, str]:
    if value == "{}":
        return {}
    return _parse_java_map(value, "default state", SlitheriteEvidenceError)


def _validate_registry(
    registry: object,
    error_type: type[Exception],
) -> tuple[int, ...]:
    if not isinstance(registry, list) or len(registry) != len(BLOCK_SPECS):
        _fail("registry inventory is not the ordered 17-member family", error_type)
    network_ids: list[int] = []
    pattern = re.compile(
        r"^(etherology:[a-z0-9_]+)=block_class:([^,]+),"
        r"item_class:([^,]+),default:(\{[^}]*\}),states:([0-9]+),"
        r"default_raw_id:([0-9]+),raw_ids:([0-9]+)$"
    )
    for value, (path, block_class, default_state, state_count) in zip(
        registry, BLOCK_SPECS, strict=True
    ):
        if not isinstance(value, str):
            _fail("registry entry is not text", error_type)
        match = pattern.fullmatch(value)
        if match is None:
            _fail(f"registry entry is malformed: {value!r}", error_type)
        expected_id = f"etherology:{path}"
        if (
            match.group(1) != expected_id
            or match.group(2) != block_class
            or match.group(3) != BLOCK_ITEM_CLASS
            or _parse_java_map(match.group(4), expected_id, error_type)
            != _default_properties(default_state)
            or int(match.group(5)) != state_count
            or int(match.group(7)) != state_count
        ):
            _fail(f"registry contract changed for {expected_id}", error_type)
        network_ids.append(int(match.group(6)))
    if len(set(network_ids)) != len(network_ids):
        _fail("default state network ids are not unique", error_type)
    return tuple(network_ids)


def _validate_placements(value: object, error_type: type[Exception]) -> None:
    if not isinstance(value, str):
        _fail("BlockItem placement evidence is missing", error_type)
    entries = value.split(";")
    if len(entries) != len(BLOCK_IDS):
        _fail("BlockItem placement evidence is not 17 entries", error_type)
    pattern = re.compile(
        r"^(etherology:[a-z0-9_]+)=PlacementEvidence\["
        r"actionResult=([A-Z_]+), accepted=true, beforeCount=1, afterCount=0, "
        r"blockItemMapping=true, placedId=(etherology:[a-z0-9_]+), "
        r"placedState=(etherology:[a-z0-9_]+)(?:\[[^\r\n]*\])?\]$"
    )
    for entry, block_id in zip(entries, BLOCK_IDS, strict=True):
        match = pattern.fullmatch(entry)
        if (
            match is None
            or match.group(1) != block_id
            or match.group(2) in {"FAIL", "PASS"}
            or match.group(3) != block_id
            or match.group(4) != block_id
        ):
            _fail(f"BlockItem placement is not exact for {block_id}", error_type)


def _validate_button(value: object, error_type: type[Exception]) -> None:
    if not isinstance(value, str):
        _fail("button behavior evidence is missing", error_type)
    match = re.fullmatch(
        r"powered=true;scheduled=true;elapsed=([0-9]+);reset=true", value
    )
    if match is None or int(match.group(1)) < 20:
        _fail("button did not pulse, schedule, wait, and reset", error_type)


def _validate_snapshot(value: object, error_type: type[Exception]) -> None:
    if not isinstance(value, str):
        _fail("fixture snapshot is missing", error_type)
    entries = value.split(";")
    if len(entries) != len(BLOCK_IDS):
        _fail("fixture snapshot is not the 17-member gallery", error_type)
    for entry, block_id in zip(entries, BLOCK_IDS, strict=True):
        prefix = f"{block_id}={block_id}"
        state, separator, support = entry.partition("|support=")
        if (
            not separator
            or not state.startswith(prefix)
            or support not in {"minecraft:polished_andesite", "minecraft:smooth_stone"}
        ):
            _fail(f"fixture snapshot changed for {block_id}", error_type)


def image_statistics(
    image: PngLike, description: str, error_type: type[Exception]
) -> tuple[float, float]:
    """Returns mean luminance and dark-pixel ratio after strict RGB validation."""

    expected_size = image.width * image.height * 3
    if len(image.pixels) != expected_size:
        _fail(f"{description} has an invalid RGB payload", error_type)
    luminance_total = 0.0
    dark_pixels = 0
    missing_texture_pixels = 0
    pixel_count = image.width * image.height
    for offset in range(0, len(image.pixels), 3):
        red, green, blue = image.pixels[offset : offset + 3]
        luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        luminance_total += luminance
        dark_pixels += luminance < DARK_PIXEL_LUMINANCE_THRESHOLD
        missing_texture_pixels += red >= 250 and green <= 5 and blue >= 250
    mean_luminance = luminance_total / pixel_count
    dark_ratio = dark_pixels / pixel_count
    if mean_luminance < MINIMUM_MEAN_LUMINANCE:
        _fail(
            f"{description} is too dark (mean luminance {mean_luminance:.3f})",
            error_type,
        )
    if dark_ratio > MAXIMUM_DARK_PIXEL_RATIO:
        _fail(
            f"{description} has too many dark pixels ({dark_ratio:.6f})",
            error_type,
        )
    if missing_texture_pixels > MAXIMUM_MISSING_TEXTURE_PIXELS:
        _fail(
            f"{description} contains missing-texture magenta pixels",
            error_type,
        )
    return mean_luminance, dark_ratio


def changed_pixel_ratio(
    initial: PngLike, reopened: PngLike, error_type: type[Exception]
) -> float:
    """Returns the exact RGB pixel-change ratio for the reopened comparison."""

    if (
        initial.width != reopened.width
        or initial.height != reopened.height
        or len(initial.pixels) != len(reopened.pixels)
    ):
        _fail("reopened screenshot dimensions do not match initial", error_type)
    changed = sum(
        initial.pixels[offset : offset + 3]
        != reopened.pixels[offset : offset + 3]
        for offset in range(0, len(initial.pixels), 3)
    )
    ratio = changed / (initial.width * initial.height)
    if ratio > MAXIMUM_REOPEN_CHANGED_PIXEL_RATIO:
        _fail(
            f"reopened screenshot changed too much ({ratio:.6f})",
            error_type,
        )
    return ratio


def _validate_world_files(world_path: Path, error_type: type[Exception]) -> None:
    if world_path.is_symlink() or not world_path.is_dir():
        _fail("saved/reopened world directory is missing or linked", error_type)
    for relative in ("level.dat", "session.lock", "region/r.0.0.mca"):
        path = world_path / relative
        if path.is_symlink() or not path.is_file() or path.stat().st_size <= 0:
            _fail(f"saved/reopened world proof is missing: {relative}", error_type)
    region_directory = world_path / "region"
    if region_directory.is_symlink() or not region_directory.is_dir():
        _fail("saved/reopened region directory is missing or linked", error_type)
    region_files = list(region_directory.iterdir())
    if not region_files:
        _fail("saved/reopened world has no region files", error_type)
    for path in region_files:
        if (
            path.is_symlink()
            or not path.is_file()
            or path.stat().st_size <= 0
            or re.fullmatch(r"r\.-?[0-9]+\.-?[0-9]+\.mca", path.name) is None
        ):
            _fail(f"saved-world region entry is invalid: {path.name}", error_type)
    file_count = 0
    total_size = 0
    for path in world_path.rglob("*"):
        if path.is_symlink():
            _fail(f"saved world contains a symlink: {path}", error_type)
        if path.is_dir():
            continue
        if not path.is_file():
            _fail(f"saved world contains a non-file: {path}", error_type)
        file_count += 1
        total_size += path.stat().st_size
        if file_count > 8192 or total_size > 2 * 1024 * 1024 * 1024:
            _fail("saved world exceeds evidence bounds", error_type)


def _assertion_map(
    assertions: object, error_type: type[Exception]
) -> dict[str, dict[str, object]]:
    if not isinstance(assertions, list) or len(assertions) != len(EXPECTED_ASSERTION_NAMES):
        _fail("assertion count is not exactly 183", error_type)
    result: dict[str, dict[str, object]] = {}
    names: list[str] = []
    for assertion in assertions:
        entry = _require_exact_fields(
            assertion,
            {"name", "passed", "expected", "actual"},
            "assertion",
            error_type,
        )
        name = entry.get("name")
        if (
            not isinstance(name, str)
            or entry.get("passed") is not True
            or not isinstance(entry.get("expected"), str)
            or not isinstance(entry.get("actual"), str)
            or name in result
        ):
            _fail("assertion entry is invalid, failed, or duplicated", error_type)
        names.append(name)
        result[name] = entry
    if tuple(names) != EXPECTED_ASSERTION_NAMES:
        _fail("assertion order/inventory changed", error_type)
    return result


def _require_assertion(
    assertions: dict[str, dict[str, object]],
    name: str,
    expected: str,
    actual: str,
    error_type: type[Exception],
) -> None:
    if assertions[name] != {
        "name": name,
        "passed": True,
        "expected": expected,
        "actual": actual,
    }:
        _fail(f"assertion semantics changed: {name}", error_type)


def _validate_camera_assertion(
    assertions: dict[str, dict[str, object]],
    phase: str,
    error_type: type[Exception],
) -> None:
    assertion = assertions[f"capture_camera_exact:{phase}"]
    if assertion["expected"] != EXPECTED_CAMERA:
        _fail(f"{phase} camera expectation changed", error_type)
    actual = str(assertion["actual"])
    match = re.fullmatch(
        r"first_person=true;x=([^;]+);y=([^;]+);z=([^;]+);"
        r"yaw=([^;]+);pitch=([^;]+);on_ground=true",
        actual,
    )
    if match is None:
        _fail(f"{phase} camera evidence is malformed", error_type)
    expected_values = (0.5, 121.0, -14.5, 0.0, 8.0)
    try:
        actual_values = tuple(float(value) for value in match.groups())
    except ValueError:
        _fail(f"{phase} camera evidence is not numeric", error_type)
    if any(
        abs(actual_value - expected_value) > 0.0001
        for actual_value, expected_value in zip(
            actual_values, expected_values, strict=True
        )
    ):
        _fail(f"{phase} camera evidence exceeds tolerance", error_type)


def _validate_lighting_assertion(
    assertions: dict[str, dict[str, object]],
    phase: str,
    error_type: type[Exception],
) -> None:
    assertion = assertions[f"capture_lighting_ready:{phase}"]
    if assertion["expected"] != EXPECTED_LIGHTING_DESCRIPTION:
        _fail(f"{phase} lighting expectation changed", error_type)
    pattern = re.compile(
        r"^stableClientTicks=20;clientPending=(?:true|false);client:"
        + re.escape(EXPECTED_LOCAL_LIGHT_SAMPLES)
        + r";serverGeneration=([1-9][0-9]*);server:"
        + re.escape(EXPECTED_LOCAL_LIGHT_SAMPLES)
        + r"$"
    )
    if pattern.fullmatch(str(assertion["actual"])) is None:
        _fail(
            f"{phase} lacks 20 stable exact local server/client light samples",
            error_type,
        )


def _validate_assertions(
    assertions_node: object,
    slitherite: dict[str, object],
    screenshots: dict[str, tuple[Path, dict[str, object]]],
    error_type: type[Exception],
) -> None:
    assertions = _assertion_map(assertions_node, error_type)
    _require_assertion(
        assertions,
        "fabric_mod_loaded:etherology",
        "loaded",
        "loaded",
        error_type,
    )
    registry = slitherite["registry"]
    if not isinstance(registry, list):
        _fail("registry mechanics node is invalid", error_type)
    for registry_entry, (path, block_class, default_state, state_count) in zip(
        registry, BLOCK_SPECS, strict=True
    ):
        block_id = f"etherology:{path}"
        _require_assertion(
            assertions, f"registry:block:{block_id}", "present", "present", error_type
        )
        _require_assertion(
            assertions, f"registry:item:{block_id}", "present", "present", error_type
        )
        _require_assertion(
            assertions,
            f"runtime:block_class:{block_id}",
            block_class,
            block_class,
            error_type,
        )
        _require_assertion(
            assertions,
            f"runtime:block_item_class:{block_id}",
            BLOCK_ITEM_CLASS,
            BLOCK_ITEM_CLASS,
            error_type,
        )
        _require_assertion(
            assertions,
            f"block_item_mapping:{block_id}",
            "true",
            "true",
            error_type,
        )
        default_assertion = assertions[f"default_state:{block_id}"]
        if (
            _parse_java_map(str(default_assertion["expected"]), block_id, error_type)
            != _default_properties(default_state)
            or _parse_java_map(str(default_assertion["actual"]), block_id, error_type)
            != _default_properties(default_state)
        ):
            _fail(f"default-state assertion changed for {block_id}", error_type)
        _require_assertion(
            assertions,
            f"state_count:{block_id}",
            str(state_count),
            str(state_count),
            error_type,
        )
        network_assertion = assertions[f"default_state_network_id:{block_id}"]
        if (
            network_assertion["expected"] != "non-negative"
            or re.fullmatch(r"[0-9]+", str(network_assertion["actual"])) is None
        ):
            _fail(f"network-id assertion changed for {block_id}", error_type)

    resources = _java_list(list(EXPECTED_RESOURCES))
    _require_assertion(
        assertions,
        "slitherite_canonical_resources_exact",
        resources,
        resources,
        error_type,
    )
    aggregate = "1262 unique non-negative raw ids"
    _require_assertion(
        assertions,
        "slitherite_state_network_ids_exact",
        aggregate,
        aggregate,
        error_type,
    )
    for mod_id in ("etherology", "etherology_original_baseline_harness"):
        _require_assertion(
            assertions,
            f"packaged_root_jar:{mod_id}",
            "one regular root JAR",
            "one regular root JAR",
            error_type,
        )
    _require_assertion(
        assertions,
        "integrated_world_joined",
        "running server and connected client",
        "joined",
        error_type,
    )
    _require_assertion(
        assertions,
        "server_arena_chunks_loaded",
        "twelve full chunks",
        "true",
        error_type,
    )
    tag_assertions = (
        "tag:mineable/pickaxe",
        "tag:needs_stone_tool",
        "tag:block/slabs",
        "tag:item/slabs",
        "tag:block/stairs",
        "tag:item/stairs",
        "tag:block/walls",
        "tag:item/walls",
        "tag:block/stone_bricks",
        "tag:block/stone_pressure_plates",
        "tag:item/buttons",
    )
    for name, description in zip(tag_assertions, EXPECTED_TAGS, strict=True):
        expected = description.partition("=")[2]
        _require_assertion(assertions, name, expected, expected, error_type)
    exact_string_assertions = {
        "slitherite_loot_tables_exact": _java_list(list(EXPECTED_LOOT_TABLES)),
        "slitherite_self_drops_exact": _java_map(EXPECTED_SELF_DROPS),
        "slitherite_double_slab_drops_x1_exact": _java_map(
            EXPECTED_DOUBLE_SLAB_DROPS
        ),
        "slitherite_owned_recipes_exact": _java_list(list(EXPECTED_OWNED_RECIPES)),
        "slitherite_owned_advancements_exact": _java_list(
            list(EXPECTED_OWNED_ADVANCEMENTS)
        ),
        "slitherite_related_recipes_recorded_not_owned": _java_list(
            list(EXPECTED_RELATED_RECIPES)
        ),
    }
    for name, value in exact_string_assertions.items():
        _require_assertion(assertions, name, value, value, error_type)
    _require_assertion(
        assertions,
        "direct_block_item_placements_exact",
        "17 accepted 1->0 BlockItem placements bound to registered blocks",
        str(slitherite["placements"]),
        error_type,
    )
    _require_assertion(
        assertions,
        "slitherite_button_pulse_reset_exact",
        "powered=true;scheduled=true;elapsed>=20;reset=true",
        str(slitherite["button_behavior"]),
        error_type,
    )
    _require_assertion(
        assertions,
        "slitherite_pressure_plate_entities_exact",
        "item=false;living=true;reset=true",
        "item=false;living=true;reset=true",
        error_type,
    )
    _require_assertion(
        assertions,
        "initial_server_fixture_exact",
        "17 exact Slitherite states on deterministic supports",
        str(slitherite["initial_snapshot"]),
        error_type,
    )
    identity = (
        "Etherology Original 0.1.7 Slitherite Blocks;"
        "4995697409260082224;minecraft:overworld"
    )
    _require_assertion(assertions, "live_world_identity", identity, identity, error_type)
    _require_assertion(assertions, "forced_world_save", "true", "true", error_type)
    _require_assertion(
        assertions,
        "restart_fixture_persistence_exact",
        "saved snapshot equals reopened snapshot",
        "exact",
        error_type,
    )
    _require_assertion(
        assertions,
        "restart_loaded_data_exact",
        "same registry, tags, loot, recipes, and advancements",
        "exact",
        error_type,
    )
    for phase in PHASES:
        _require_assertion(
            assertions,
            f"capture_mirror_exact:{phase}",
            "client snapshot equals server snapshot",
            "exact",
            error_type,
        )
        _require_assertion(
            assertions,
            f"capture_render_ready:{phase}",
            "terrain and all 17 fixtures rendering-ready",
            "ready",
            error_type,
        )
        _validate_lighting_assertion(assertions, phase, error_type)
        _validate_camera_assertion(assertions, phase, error_type)
        _require_assertion(
            assertions,
            f"capture_consecutive_stable_renders:{phase}",
            str(REQUIRED_STABLE_RENDERS),
            str(REQUIRED_STABLE_RENDERS),
            error_type,
        )
        _require_assertion(
            assertions,
            f"capture_framebuffer_dimensions:{phase}",
            "1920x1080",
            "1920x1080",
            error_type,
        )
        path, _node = screenshots[phase]
        _require_assertion(
            assertions,
            f"native_screenshot_written:{phase}",
            "one non-empty unedited 1920x1080 framebuffer PNG",
            f"{path.stat().st_size} bytes, sha256={_node['sha256']}",
            error_type,
        )
    _require_assertion(
        assertions,
        "isolated_save_directory_present",
        str(EXPECTED_WORLD["save_directory"]),
        str(EXPECTED_WORLD["save_directory"]),
        error_type,
    )


def validate_evidence(
    *,
    scenario_root: Path,
    world_path: Path,
    report: dict[str, object],
    expected_artifacts: list[dict[str, object]],
    decode_png: Callable[[Path], PngLike],
    assert_image_is_not_blank: Callable[[PngLike], None],
    sha256_file: Callable[[Path], str],
    error_type: type[Exception] = SlitheriteEvidenceError,
) -> SlitheriteEvidenceSummary:
    """Validates one completed immutable v8 capture without trusting the harness."""

    if scenario_root.is_symlink() or not scenario_root.is_dir():
        _fail("scenario root is missing or linked", error_type)
    if {path.name for path in scenario_root.iterdir()} != {"reports", "screenshots"}:
        _fail("scenario-root inventory is contaminated", error_type)
    reports = scenario_root / "reports"
    screenshots_root = scenario_root / "screenshots"
    if reports.is_symlink() or screenshots_root.is_symlink():
        _fail("report or screenshot directory is linked", error_type)
    if {path.name for path in reports.iterdir()} != {"report.json", "done.marker"}:
        _fail("report inventory is incomplete or contaminated", error_type)
    if {path.name for path in screenshots_root.iterdir()} != set(SCREENSHOT_FILES):
        _fail("screenshot inventory is incomplete or contaminated", error_type)

    _require_exact_fields(
        report,
        {
            "schema",
            "reference_id",
            "scenario",
            "lane",
            "status",
            "passed",
            "client_ticks",
            "lifecycle_failure",
            "assertions",
            "world",
            "artifacts",
            "screenshots",
            "slitherite",
        },
        "report",
        error_type,
    )
    if (
        report.get("schema") != 3
        or report.get("reference_id") != "published-0.1.7"
        or report.get("scenario") != SCENARIO_ID
        or report.get("lane") != "fabric-1.21.1-original"
        or report.get("status") != "passed"
        or report.get("passed") is not True
        or type(report.get("client_ticks")) is not int
        or int(report["client_ticks"]) <= 0
        or report.get("lifecycle_failure") != ""
    ):
        _fail("report did not pass the exact v8 lifecycle", error_type)
    if report.get("world") != EXPECTED_WORLD:
        _fail("report describes another world or lacks reopen proof", error_type)
    if report.get("artifacts") != expected_artifacts:
        _fail("packaged root JAR inventory/digests changed", error_type)

    slitherite = _require_exact_fields(
        report.get("slitherite"),
        EXPECTED_SLITHERITE_FIELDS,
        "slitherite mechanics",
        error_type,
    )
    _validate_registry(slitherite["registry"], error_type)
    if (
        slitherite["block_ids"] != list(BLOCK_IDS)
        or slitherite["aggregate_state_count"] != 1262
        or slitherite["canonical_resources"] != list(EXPECTED_RESOURCES)
        or len(EXPECTED_RESOURCES) - 1 != 79
        or slitherite["tags"] != list(EXPECTED_TAGS)
        or slitherite["loot_tables"] != list(EXPECTED_LOOT_TABLES)
        or slitherite["self_drops"] != EXPECTED_SELF_DROPS
        or slitherite["double_slab_drops"] != EXPECTED_DOUBLE_SLAB_DROPS
        or slitherite["owned_recipes"] != list(EXPECTED_OWNED_RECIPES)
        or slitherite["owned_advancements"] != list(EXPECTED_OWNED_ADVANCEMENTS)
        or slitherite["related_recipes_recorded_not_owned"]
        != list(EXPECTED_RELATED_RECIPES)
        or slitherite["pressure_plate_behavior"]
        != "item=false;living=true;reset=true"
        or slitherite["persistence_exact"] is not True
        or slitherite["reopened_data_exact"] is not True
        or slitherite["required_stable_renders"] != REQUIRED_STABLE_RENDERS
        or slitherite["required_lighting_ready_client_ticks"]
        != REQUIRED_LIGHTING_READY_CLIENT_TICKS
    ):
        _fail("Slitherite family/data contract changed", error_type)
    _validate_placements(slitherite["placements"], error_type)
    _validate_button(slitherite["button_behavior"], error_type)
    _validate_snapshot(slitherite["initial_snapshot"], error_type)
    _validate_snapshot(slitherite["reopened_snapshot"], error_type)
    if slitherite["initial_snapshot"] != slitherite["reopened_snapshot"]:
        _fail("reopened structural snapshot differs from initial", error_type)

    screenshots_node = report.get("screenshots")
    if not isinstance(screenshots_node, list) or len(screenshots_node) != 2:
        _fail("report must describe exactly two screenshots", error_type)
    decoded: dict[str, PngLike] = {}
    screenshot_records: dict[str, tuple[Path, dict[str, object]]] = {}
    means: dict[str, float] = {}
    for node, (phase, relative_file) in zip(
        screenshots_node, EXPECTED_SCREENSHOTS, strict=True
    ):
        entry = _require_exact_fields(
            node,
            {
                "step",
                "file",
                "width",
                "height",
                "size",
                "sha256",
                "completed_render_count",
                "source",
                "edited",
            },
            f"{phase} screenshot record",
            error_type,
        )
        path = scenario_root / relative_file
        if path.is_symlink() or not path.is_file() or path.stat().st_size <= 0:
            _fail(f"{phase} screenshot is missing, empty, or linked", error_type)
        if entry != {
            "step": phase,
            "file": relative_file,
            "width": EXPECTED_FRAMEBUFFER[0],
            "height": EXPECTED_FRAMEBUFFER[1],
            "size": path.stat().st_size,
            "sha256": sha256_file(path),
            "completed_render_count": REQUIRED_STABLE_RENDERS,
            "source": "minecraft-framebuffer",
            "edited": False,
        }:
            _fail(f"{phase} screenshot record does not match native PNG", error_type)
        image = decode_png(path)
        assert_image_is_not_blank(image)
        if (image.width, image.height) != EXPECTED_FRAMEBUFFER:
            _fail(f"{phase} screenshot is not 1920x1080", error_type)
        means[phase], _dark_ratio = image_statistics(image, phase, error_type)
        decoded[phase] = image
        screenshot_records[phase] = (path, entry)

    comparison = changed_pixel_ratio(
        decoded["initial"], decoded["reopened"], error_type
    )
    _validate_assertions(
        report.get("assertions"), slitherite, screenshot_records, error_type
    )
    _validate_world_files(world_path, error_type)
    return SlitheriteEvidenceSummary(
        assertion_count=len(EXPECTED_ASSERTION_NAMES),
        screenshot_count=2,
        initial_mean_luminance=means["initial"],
        reopened_mean_luminance=means["reopened"],
        reopen_changed_pixel_ratio=comparison,
    )
