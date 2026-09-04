#!/usr/bin/env python3
"""Strict contract/verifier for the fresh original published-0.1.7 Pedestal v11 lane."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import re
from typing import Callable, Protocol


SCENARIO_ID = "pedestal-baseline"
PROFILE_ID = "etherology-original-fabric-1.21.1-published-0.1.7-v11"
PROFILE_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v11.json"
)
PROFILE_SIZE = 10_307
PROFILE_SHA256 = "34974855dd861c220915dd77ce694d3e5175c97e1c8f6edea0806601947e0cfc"
LEGACY_V10_RELATIVE_PATH = (
    "scripts/baseline/original-fabric-1.21.1-published-0.1.7-v10.json"
)
LEGACY_V10_SIZE = 10_349
LEGACY_V10_SHA256 = (
    "32a96831e39034b704b92a0768639c8d776e6c7612ff2cefa2603cf19eec77d7"
)
CONTRACT_SOURCE_SIZE = 17_454
CONTRACT_SOURCE_SHA256 = (
    "9bf244347a1b0a1d640efc762385f83fb3e713714aee55ef0547030a35d53a84"
)
HARNESS_VERSION = "1.4.0"
HARNESS_STATUS = "implemented"
HARNESS_FILE = (
    "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.4.0.jar"
)
HARNESS_SIZE = 339_617
HARNESS_SHA256 = "09272e04b122b20da33d1964b4e1ca9f67af768fb0db0c0fa1f74f0579799e57"
SCENARIO_SOURCE_SIZE = 163_134
SCENARIO_SOURCE_SHA256 = (
    "8fc7e3afe3e8d3c5218200e05aa113ace3bfcb69ba2f871c634be43896b9386e"
)
WRITER_SOURCE_SIZE = 6_284
WRITER_SOURCE_SHA256 = (
    "bd1cb0271420bc9fd277dfc026efb15118343ac1ee876e54a784c68f93398e5a"
)
INITIAL_SCREENSHOT_FILE = "pedestal-gallery.png"
SCREENSHOT_FILES = (
    INITIAL_SCREENSHOT_FILE,
    "pedestal-transition-drops.png",
    "pedestal-persistence-initial.png",
    "pedestal-persistence-reopened.png",
)
PHASES = (
    "gallery",
    "transition-drops",
    "persistence-initial",
    "persistence-reopened",
)
EXPECTED_SCREENSHOTS = tuple(
    (phase, f"screenshots/{file_name}")
    for phase, file_name in zip(PHASES, SCREENSHOT_FILES, strict=True)
)
EXPECTED_FRAMEBUFFER = (1920, 1080)
REQUIRED_STABLE_RENDERS = 120
REQUIRED_LIGHT_READY_CLIENT_TICKS = 20
EXPECTED_STATE_COUNT = 1024
EXPECTED_MULTIPART_CLAUSE_COUNT = 68
VERTICAL_CARPET_LIMITATION = (
    "hash-pinned-published-0.1.7-bytecode-not-executed-safety-guard: "
    "empty-carpet-slot vertical direction reaches the horizontal-only facing property"
)
EXPECTED_WORLD = {
    "save_directory": "etherology-original-pedestal-baseline-world",
    "display_name": "Etherology Original 0.1.7 Pedestal",
    "seed": 4995697396257403185,
    "dimension": "minecraft:overworld",
    "integrated": True,
    "reopened": True,
}
DEFAULT_PROPERTY_ORDER = (
    "cloth_color",
    "decoration",
    "facing",
    "shape",
    "waterlogged",
)
EXPECTED_DEFAULT_PROPERTIES = {
    "cloth_color": "white",
    "decoration": "false",
    "facing": "north",
    "shape": "full",
    "waterlogged": "false",
}
BLOCK_ENTITY_PRESENCE_ORDER = ("full", "bottom", "middle", "top")
SIDED_DIRECTION_ORDER = ("down", "up", "north", "south", "west", "east")
EXPECTED_SHAPES = {
    "bottom": (0.1875, 0.0, 0.1875, 0.8125, 1.0, 0.8125, 0.2763671875),
    "middle": (0.25, 0.0, 0.25, 0.75, 1.0, 0.75, 0.25),
    "top": (0.125, 0.0, 0.125, 0.875, 1.0, 0.875, 0.34765625),
    "full": (0.125, 0.0, 0.125, 0.875, 1.0, 0.875, 0.3740234375),
}
EXPECTED_MECHANICS_FIELDS = {
    "resource_pins",
    "multipart_clause_count",
    "registry",
    "data",
    "placement",
    "shapes",
    "interactions",
    "inventory",
    "dispensers",
    "transitions",
    "gallery_snapshot",
    "transition_snapshot",
    "persistence_initial_snapshot",
    "persistence_reopened_snapshot",
    "forced_save",
    "full_restart",
    "persistence_exact",
    "required_stable_renders",
    "required_light_ready_client_ticks",
    "limitations",
}
BASE_ASSERTION_NAMES = (
    "fabric_mod_loaded:etherology",
    "pedestal_resources_exact",
    "pedestal_blockstate_multipart_count_exact",
    "registry:block:etherology:pedestal",
    "registry:item:etherology:pedestal",
    "registry:block_entity_type:etherology:pedestal_block_entity",
    "pedestal_runtime_block_class_exact",
    "pedestal_runtime_block_entity_class_exact",
    "pedestal_block_item_mapping_exact",
    "pedestal_translation_exact",
    "pedestal_default_properties_exact",
    "pedestal_state_count_exact",
    "pedestal_state_network_ids_exact",
    "pedestal_horizontal_facing_values_exact",
    "pedestal_pickaxe_tag_exact",
    "pedestal_recipe_exact",
    "pedestal_advancement_exact",
    "pedestal_loot_table_exact",
    "pedestal_self_drop_exact",
    "pedestal_native_standalone_placement_exact",
    "pedestal_native_waterlogged_placement_exact",
    "pedestal_outline_shapes_exact",
    "pedestal_stack_shape_transitions_exact",
    "pedestal_block_entity_presence_by_shape_exact",
    "pedestal_interaction_sequence_exact",
    "pedestal_inventory_two_max_one_slots_exact",
    "pedestal_sided_inventory_closed_exact",
    "pedestal_nbt_items_exact",
    "pedestal_nbt_removed_flag_exact",
    "pedestal_item_dispenser_all_six_directions_exact",
    "pedestal_carpet_dispenser_horizontal_directions_exact",
    "pedestal_occupied_carpet_falls_through_to_display_exact",
    "pedestal_full_target_falls_through_to_generic_item_ejection_exact",
    "pedestal_stack_transition_drops_exact",
    "pedestal_stack_transition_stale_block_entity_removed",
    "pedestal_stack_transition_client_block_entity_removed",
    "pedestal_replacement_drops_exact",
    "pedestal_replacement_stale_block_entity_removed",
    "pedestal_replacement_client_block_entity_removed",
    "pedestal_gallery_server_snapshot_exact",
    "pedestal_transition_server_snapshot_exact",
    "pedestal_persistence_initial_server_snapshot_exact",
    "pedestal_forced_world_save_exact",
    "pedestal_full_restart_completed",
    "pedestal_reopened_server_snapshot_exact",
    "pedestal_restart_persistence_exact",
    "packaged_root_jar:etherology",
    "packaged_root_jar:etherology_original_baseline_harness",
    "live_world_identity",
    "isolated_save_directory_present",
)


def _assertion_names() -> tuple[str, ...]:
    names = list(BASE_ASSERTION_NAMES)
    for phase in PHASES:
        names.extend(
            (
                f"client_snapshot_exact:{phase}",
                f"native_framebuffer_dimensions:{phase}",
                f"completed_world_renders_before_capture:{phase}",
                f"capture_render_ready:{phase}",
                f"capture_camera_exact:{phase}",
                f"native_screenshot_written:{phase}",
            )
        )
    if len(names) != 74 or len(set(names)) != 74:
        raise RuntimeError("Pedestal assertion inventory drifted")
    return tuple(names)


EXPECTED_ASSERTION_NAMES = _assertion_names()


class PngLike(Protocol):
    width: int
    height: int
    pixels: bytes


@dataclass(frozen=True)
class PedestalEvidenceSummary:
    assertion_count: int
    screenshot_count: int
    persistence_material_changed_pixel_ratio: float


class PedestalEvidenceError(RuntimeError):
    """Reports one fail-closed Pedestal v11 contract violation."""


def _fail(message: str, error_type: type[Exception]) -> None:
    raise error_type(f"Original Pedestal v11 evidence: {message}")


def _require_exact_fields(
    value: object,
    expected: set[str],
    description: str,
    error_type: type[Exception],
) -> dict[str, object]:
    if not isinstance(value, dict) or set(value) != expected:
        _fail(f"{description} has a non-exact field inventory", error_type)
    return value


def _require_regular(path: Path, description: str, error_type: type[Exception]) -> None:
    if path.is_symlink() or not path.is_file() or path.stat().st_size <= 0:
        _fail(f"{description} is missing, empty, or linked", error_type)


def _is_exact_int(value: object, expected: int) -> bool:
    return type(value) is int and value == expected


def _is_exact_int_mapping(value: object, expected: dict[str, int]) -> bool:
    return (
        isinstance(value, dict)
        and set(value) == set(expected)
        and all(
            _is_exact_int(value[name], count)
            for name, count in expected.items()
        )
    )


def _is_exact_json(value: object, expected: object) -> bool:
    if type(value) is not type(expected):
        return False
    if isinstance(expected, dict):
        return set(value) == set(expected) and all(
            _is_exact_json(value[name], expected_value)
            for name, expected_value in expected.items()
        )
    if isinstance(expected, list):
        return len(value) == len(expected) and all(
            _is_exact_json(actual, expected_value)
            for actual, expected_value in zip(value, expected, strict=True)
        )
    return value == expected


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _load_resource_pins(repository_root: Path) -> dict[str, dict[str, object]]:
    contract = repository_root / (
        "baseline-harness/fabric/1.21.1/src/main/java/dev/theplumteam/"
        "etherology/baseline/fabric/PedestalBaselineContract.java"
    )
    if contract.is_symlink() or not contract.is_file():
        raise RuntimeError("Pedestal Java contract is missing or linked")
    if (
        contract.stat().st_size != CONTRACT_SOURCE_SIZE
        or _sha256(contract) != CONTRACT_SOURCE_SHA256
    ):
        raise RuntimeError("Pedestal Java contract bytes differ from their verifier pin")
    content = contract.read_text(encoding="utf-8")
    entries = re.findall(
        r'add\(pins, "([^"]+)", (\d+),\s*"([0-9a-f]{64})"\);',
        content,
    )
    pins = {
        path: {"size": int(size), "sha256": sha256}
        for path, size, sha256 in entries
    }
    if (
        len(entries) != 64
        or len(pins) != 64
        or "if (pins.size() != 64)" not in content
    ):
        raise RuntimeError("Pedestal resource pin contract drifted")
    return pins


def validate_pinned_contract(
    *,
    repository_root: Path,
    manifest_path: Path,
    harness_path: Path,
    sha256_file: Callable[[Path], str] = _sha256,
    error_type: type[Exception] = PedestalEvidenceError,
) -> None:
    """Proves the v11 profile, harness, sources, and history are byte-pinned."""

    root = repository_root.resolve(strict=True)
    expected_manifest = root / PROFILE_RELATIVE_PATH
    if manifest_path.absolute() != expected_manifest.absolute():
        _fail("controller selected another profile manifest", error_type)
    _require_regular(expected_manifest, "v11 profile manifest", error_type)
    if (
        expected_manifest.stat().st_size != PROFILE_SIZE
        or sha256_file(expected_manifest) != PROFILE_SHA256
    ):
        _fail("v11 profile bytes differ from their immutable pin", error_type)
    manifest = json.loads(expected_manifest.read_text(encoding="utf-8"))
    profile = manifest.get("profile")
    capture = manifest.get("capture")
    if not isinstance(profile, dict) or not isinstance(capture, dict):
        _fail("v11 profile has no profile/capture objects", error_type)
    harness = capture.get("harness")
    scenario = capture.get("scenario")
    if (
        profile.get("id") != PROFILE_ID
        or profile.get("runtime_directory") != PROFILE_ID
        or not isinstance(harness, dict)
        or not _is_exact_json(harness, {
            "status": HARNESS_STATUS,
            "path": (
                "baseline-harness/fabric/1.21.1/build/libs/" + HARNESS_FILE
            ),
            "file_name": HARNESS_FILE,
            "mod_id": "etherology_original_baseline_harness",
            "version": HARNESS_VERSION,
            "size": HARNESS_SIZE,
            "sha256": HARNESS_SHA256,
            "client_entrypoint": (
                "dev.theplumteam.etherology.baseline.fabric.OriginalPhaseZeroHarness"
            ),
            "mixin_config": "etherology-original-baseline-harness.mixins.json",
        })
        or not _is_exact_json(scenario, {
            "id": SCENARIO_ID,
            "report_file": "report.json",
            "completion_marker_file": "done.marker",
            "screenshot_file": INITIAL_SCREENSHOT_FILE,
            "world_directory_name": EXPECTED_WORLD["save_directory"],
            "world_display_name": EXPECTED_WORLD["display_name"],
            "world_seed": EXPECTED_WORLD["seed"],
            "framebuffer": {"width": 1920, "height": 1080},
        })
    ):
        _fail("v11 manifest does not describe the exact pinned contract", error_type)

    identities: list[tuple[str, str]] = []
    for candidate in sorted(
        (root / "scripts/baseline").glob(
            "original-fabric-1.21.1-published-0.1.7-v*.json"
        )
    ):
        if candidate.is_symlink() or not candidate.is_file():
            _fail("profile inventory contains a linked/non-file manifest", error_type)
        candidate_manifest = json.loads(candidate.read_text(encoding="utf-8"))
        candidate_profile = candidate_manifest.get("profile")
        if not isinstance(candidate_profile, dict):
            _fail("profile inventory contains an invalid profile", error_type)
        identities.append(
            (
                str(candidate_profile.get("id")),
                str(candidate_profile.get("runtime_directory")),
            )
        )
    if (
        identities.count((PROFILE_ID, PROFILE_ID)) != 1
        or len({identity[0] for identity in identities}) != len(identities)
        or len({identity[1] for identity in identities}) != len(identities)
    ):
        _fail("v11 profile id/runtime is not globally unique", error_type)

    legacy = root / LEGACY_V10_RELATIVE_PATH
    _require_regular(legacy, "immutable v10 profile", error_type)
    if (
        legacy.stat().st_size != LEGACY_V10_SIZE
        or sha256_file(legacy) != LEGACY_V10_SHA256
    ):
        _fail("accepted v10 profile history changed", error_type)
    _require_regular(harness_path, "pinned v11 harness artifact", error_type)
    if (
        harness_path.stat().st_size != HARNESS_SIZE
        or sha256_file(harness_path) != HARNESS_SHA256
    ):
        _fail("v11 harness bytes differ from their immutable pin", error_type)
    _load_resource_pins(root)
    scenario_source = root / (
        "baseline-harness/fabric/1.21.1/src/main/java/dev/theplumteam/"
        "etherology/baseline/fabric/PedestalBaselineScenario.java"
    )
    writer_source = scenario_source.with_name("PedestalEvidenceWriter.java")
    for source, description in (
        (scenario_source, "Pedestal scenario source"),
        (writer_source, "Pedestal evidence-writer source"),
    ):
        _require_regular(source, description, error_type)
    if (
        scenario_source.stat().st_size != SCENARIO_SOURCE_SIZE
        or _sha256(scenario_source) != SCENARIO_SOURCE_SHA256
        or writer_source.stat().st_size != WRITER_SOURCE_SIZE
        or _sha256(writer_source) != WRITER_SOURCE_SHA256
    ):
        _fail("Pedestal scenario/writer bytes differ from their verifier pins", error_type)
    scenario_text = scenario_source.read_text(encoding="utf-8")
    writer_text = writer_source.read_text(encoding="utf-8")
    if (
        "import ru.feytox.etherology" in scenario_text
        or "Direction.DOWN,\n                Direction.UP" not in scenario_text
        or "List<Direction> carpetDirections = List.of(\n                Direction.NORTH"
        not in scenario_text
        or '"minecraft:purple_carpet"' not in scenario_text
        or '"occupied-carpet-display"' not in scenario_text
        or '"occupied-carpet-display",\n                Direction.UP' not in scenario_text
        or '"full-target-fallback"' not in scenario_text
        or "tickWaitingForTransitionPrecondition" not in scenario_text
        or "refreshTransitionClientEvidence" not in scenario_text
        or "stage != Stage.CAPTURING" not in scenario_text
        or "server.saveAll(false, true, true)" not in scenario_text
        or "createIntegratedServerLoader().start(" not in scenario_text
        or "ScreenshotRecorder.saveScreenshot(" not in scenario_text
        or "Files.createLink(target, source)" not in writer_text
        or "FileAlreadyExistsException" not in writer_text
    ):
        _fail("pinned harness source lost a fail-closed Pedestal vertical", error_type)
    properties = (
        root / "baseline-harness/fabric/1.21.1/gradle.properties"
    ).read_text(encoding="utf-8")
    if f"harness_version={HARNESS_VERSION}\n" not in properties:
        _fail("harness source version does not select v1.4.0", error_type)


def validate_fresh_contract(
    *,
    repository_root: Path,
    manifest_path: Path,
    harness_path: Path,
    runtime_path: Path,
    archive_path: Path,
    sha256_file: Callable[[Path], str] = _sha256,
    error_type: type[Exception] = PedestalEvidenceError,
) -> None:
    """Proves the pinned v11 contract has not been provisioned or consumed."""

    validate_pinned_contract(
        repository_root=repository_root,
        manifest_path=manifest_path,
        harness_path=harness_path,
        sha256_file=sha256_file,
        error_type=error_type,
    )
    if runtime_path.exists() or runtime_path.is_symlink():
        _fail("unprovisioned v11 runtime must remain absent", error_type)
    if archive_path.is_symlink() or not archive_path.is_dir():
        _fail("fresh evidence-contract directory is missing or linked", error_type)
    if {path.name for path in archive_path.iterdir()} != {"README.md"}:
        _fail("fresh evidence directory contains runtime/archive payload", error_type)
    _require_regular(
        archive_path / "README.md", "fresh evidence README", error_type
    )
    for forbidden in (
        "archive-manifest.json",
        "controller",
        "reports",
        "screenshots",
        "launch-attempt.json",
    ):
        path = archive_path / forbidden
        if path.exists() or path.is_symlink():
            _fail(f"fresh evidence unexpectedly contains {forbidden}", error_type)


def _parse_state(value: object, error_type: type[Exception]) -> dict[str, str]:
    if not isinstance(value, str):
        _fail("Pedestal state is not text", error_type)
    match = re.fullmatch(r"etherology:pedestal\[([^]]+)]", value)
    if match is None:
        _fail("Pedestal state has another block id/shape", error_type)
    properties: dict[str, str] = {}
    for entry in match.group(1).split(","):
        name, separator, setting = entry.partition("=")
        if not separator or name in properties:
            _fail("Pedestal state properties are invalid", error_type)
        properties[name] = setting
    if set(properties) != set(EXPECTED_DEFAULT_PROPERTIES):
        _fail("Pedestal state property inventory changed", error_type)
    canonical = "etherology:pedestal[" + ",".join(
        f"{name}={properties[name]}" for name in DEFAULT_PROPERTY_ORDER
    ) + "]"
    if value != canonical:
        _fail("Pedestal state property order/format changed", error_type)
    return properties


def _validate_registry(value: object, error_type: type[Exception]) -> None:
    registry = _require_exact_fields(
        value,
        {
            "block_present",
            "item_present",
            "block_entity_type_present",
            "block_class",
            "item_class",
            "block_item_mapping",
            "translation",
            "default_properties",
            "state_count",
            "default_state_raw_id",
            "unique_state_raw_ids",
            "network_ids_exact",
            "horizontal_facing_values",
        },
        "registry",
        error_type,
    )
    if (
        registry["block_present"] is not True
        or registry["item_present"] is not True
        or registry["block_entity_type_present"] is not True
        or registry["block_class"]
        != "ru.feytox.etherology.block.pedestal.PedestalBlock"
        or registry["item_class"] != "net.minecraft.class_1747"
        or registry["block_item_mapping"] is not True
        or registry["translation"] != "Pedestal"
        or not _is_exact_json(
            registry["default_properties"], EXPECTED_DEFAULT_PROPERTIES
        )
        or not _is_exact_int(registry["state_count"], EXPECTED_STATE_COUNT)
        or type(registry["default_state_raw_id"]) is not int
        or int(registry["default_state_raw_id"]) < 0
        or not _is_exact_int(
            registry["unique_state_raw_ids"], EXPECTED_STATE_COUNT
        )
        or registry["network_ids_exact"] is not True
        or not _is_exact_json(
            registry["horizontal_facing_values"],
            ["east", "north", "south", "west"],
        )
    ):
        _fail("Pedestal registry contract changed", error_type)


def _validate_data(value: object, error_type: type[Exception]) -> None:
    data = _require_exact_fields(
        value,
        {"pickaxe_mineable", "recipe", "advancement", "loot_table", "self_drop", "failure"},
        "data probe",
        error_type,
    )
    if not _is_exact_json(data, {
        "pickaxe_mineable": True,
        "recipe": "etherology:pedestal=minecraft:crafting->etherology:pedestalx2",
        "advancement": "etherology:recipes/decorations/pedestal",
        "loot_table": "etherology:blocks/pedestal",
        "self_drop": "etherology:pedestalx1",
        "failure": "",
    }):
        _fail("Pedestal data/loot contract changed", error_type)


def _validate_placement(value: object, error_type: type[Exception]) -> None:
    placement = _require_exact_fields(
        value,
        {"standalone", "waterlogged", "stack_shapes", "block_entity_presence"},
        "placement probe",
        error_type,
    )
    for name, waterlogged in (("standalone", False), ("waterlogged", True)):
        observation = _require_exact_fields(
            placement[name],
            {
                "action_result",
                "accepted",
                "before_count",
                "after_count",
                "block_item_mapping",
                "placed_id",
                "placed_state",
            },
            f"{name} placement",
            error_type,
        )
        state = _parse_state(observation["placed_state"], error_type)
        if (
            observation["action_result"] not in {"SUCCESS", "CONSUME"}
            or observation["accepted"] is not True
            or not _is_exact_int(observation["before_count"], 1)
            or not _is_exact_int(observation["after_count"], 0)
            or observation["block_item_mapping"] is not True
            or observation["placed_id"] != "etherology:pedestal"
            or state
            != {
                **EXPECTED_DEFAULT_PROPERTIES,
                "waterlogged": str(waterlogged).lower(),
            }
        ):
            _fail(f"{name} native placement changed", error_type)
    if (
        not _is_exact_json(
            placement["stack_shapes"],
            ["full", "bottom", "top", "bottom", "middle", "top"],
        )
        or not _is_exact_json(
            placement["block_entity_presence"],
            {"full": True, "bottom": False, "middle": False, "top": True},
        )
    ):
        _fail("stack shape/block-entity transition contract changed", error_type)


def _validate_shapes(value: object, error_type: type[Exception]) -> None:
    shapes = _require_exact_fields(value, set(EXPECTED_SHAPES), "shapes", error_type)
    fields = ("min_x", "min_y", "min_z", "max_x", "max_y", "max_z", "volume")
    for name, expected in EXPECTED_SHAPES.items():
        shape = _require_exact_fields(
            shapes[name], set(fields), f"{name} shape", error_type
        )
        if any(
            type(shape[field]) not in (int, float)
            or not math.isfinite(float(shape[field]))
            or abs(float(shape[field]) - expected[index]) > 1e-9
            for index, field in enumerate(fields)
        ):
            _fail(f"{name} outline shape changed", error_type)


def _expected_interactions() -> list[dict[str, object]]:
    values = (
        ("red-carpet-stack-place", "minecraft:red_carpetx2", "minecraft:red_carpetx1", "empty", "minecraft:red_carpetx1", "true", "red"),
        ("different-carpet-stack-noop", "minecraft:blue_carpetx2", "minecraft:blue_carpetx2", "empty", "minecraft:red_carpetx1", "true", "red"),
        ("single-carpet-swap", "minecraft:blue_carpetx1", "minecraft:red_carpetx1", "empty", "minecraft:blue_carpetx1", "true", "blue"),
        ("same-carpet-retrieve", "minecraft:blue_carpetx1", "minecraft:blue_carpetx2", "empty", "empty", "false", "white"),
        ("diamond-place", "minecraft:diamondx2", "minecraft:diamondx1", "minecraft:diamondx1", "empty", "false", "white"),
        ("different-item-noop", "minecraft:emeraldx2", "minecraft:emeraldx2", "minecraft:diamondx1", "empty", "false", "white"),
        ("full-same-item-noop", "minecraft:diamondx64", "minecraft:diamondx64", "minecraft:diamondx1", "empty", "false", "white"),
        ("same-item-retrieve", "minecraft:diamondx1", "minecraft:diamondx2", "empty", "empty", "false", "white"),
        ("empty-hand-item-first", "empty", "minecraft:diamondx1", "empty", "minecraft:red_carpetx1", "true", "red"),
        ("empty-hand-carpet-second", "empty", "minecraft:red_carpetx1", "empty", "empty", "false", "white"),
    )
    return [
        {
            "id": identifier,
            "action_result": "CONSUME",
            "before_hand": before,
            "after_hand": after,
            "item_slot": item,
            "carpet_slot": carpet,
            "decoration": decoration,
            "color": color,
            "facing": "north",
        }
        for identifier, before, after, item, carpet, decoration, color in values
    ]


def _validate_inventory(value: object, error_type: type[Exception]) -> None:
    inventory = _require_exact_fields(
        value,
        {
            "block_entity_type",
            "block_entity_class",
            "size",
            "max_count_per_stack",
            "available_slot_counts",
            "insert_denied",
            "extract_denied",
            "nbt_keys",
            "nbt_item_count",
            "nbt_removed_present",
            "nbt_removed",
        },
        "inventory probe",
        error_type,
    )
    keys = inventory["nbt_keys"]
    if (
        inventory["block_entity_type"] != "etherology:pedestal_block_entity"
        or inventory["block_entity_class"]
        != "ru.feytox.etherology.block.pedestal.PedestalBlockEntity"
        or not _is_exact_int(inventory["size"], 2)
        or not _is_exact_int(inventory["max_count_per_stack"], 1)
        or not _is_exact_int_mapping(
            inventory["available_slot_counts"],
            {
                "down": 0,
                "up": 0,
                "north": 0,
                "south": 0,
                "west": 0,
                "east": 0,
            },
        )
        or inventory["insert_denied"] is not True
        or inventory["extract_denied"] is not True
        or keys != ["Items", "id", "removed", "x", "y", "z"]
        or not _is_exact_int(inventory["nbt_item_count"], 2)
        or inventory["nbt_removed_present"] is not True
        or inventory["nbt_removed"] is not False
    ):
        _fail("Pedestal inventory/NBT/sided contract changed", error_type)


def _validate_dispensers(value: object, error_type: type[Exception]) -> None:
    dispensers = _require_exact_fields(
        value,
        {
            "observations",
            "guarded_carpet_directions",
            "vertical_carpet_status",
            "ejected_items",
            "arrow_projectiles",
        },
        "dispenser probe",
        error_type,
    )
    expected: list[dict[str, object]] = []
    for direction in ("down", "up", "north", "south", "west", "east"):
        expected.append(
            {
                "kind": "item",
                "direction": direction,
                "dispenser_slot": "minecraft:amethyst_shardx1",
                "pedestal_item_slot": "minecraft:amethyst_shardx1",
                "pedestal_carpet_slot": "empty",
                "decoration": "false",
                "color": "white",
                "facing": "north",
            }
        )
    opposites = {"north": "south", "south": "north", "west": "east", "east": "west"}
    for direction in ("north", "south", "west", "east"):
        expected.append(
            {
                "kind": "carpet",
                "direction": direction,
                "dispenser_slot": "minecraft:purple_carpetx1",
                "pedestal_item_slot": "empty",
                "pedestal_carpet_slot": "minecraft:purple_carpetx1",
                "decoration": "true",
                "color": "purple",
                "facing": opposites[direction],
            }
        )
    expected.extend(
        (
            {
                "kind": "occupied-carpet-display",
                "direction": "up",
                "dispenser_slot": "minecraft:purple_carpetx1",
                "pedestal_item_slot": "minecraft:purple_carpetx1",
                "pedestal_carpet_slot": "minecraft:red_carpetx1",
                "decoration": "true",
                "color": "red",
                "facing": "north",
            },
            {
                "kind": "full-target-fallback",
                "direction": "west",
                "dispenser_slot": "minecraft:arrowx1",
                "pedestal_item_slot": "minecraft:diamondx1",
                "pedestal_carpet_slot": "minecraft:red_carpetx1",
                "decoration": "true",
                "color": "red",
                "facing": "north",
            },
        )
    )
    if (
        not _is_exact_json(dispensers["observations"], expected)
        or not _is_exact_json(
            dispensers["guarded_carpet_directions"], ["down", "up"]
        )
        or dispensers["vertical_carpet_status"] != VERTICAL_CARPET_LIMITATION
        or not _is_exact_json(
            dispensers["ejected_items"], ["minecraft:arrowx1"]
        )
        or not _is_exact_int(dispensers["arrow_projectiles"], 0)
    ):
        _fail("directional dispenser/crash-guard contract changed", error_type)


def _validate_transitions(value: object, error_type: type[Exception]) -> None:
    transitions = _require_exact_fields(
        value,
        {
            "lower_state",
            "upper_state",
            "stack_world_block_entity_absent",
            "top_block_entity_present",
            "stack_old_reference_removed",
            "replacement_air",
            "replacement_world_block_entity_absent",
            "replacement_old_reference_removed",
            "stack_client_lookup_absent",
            "stack_client_retained_reference_removed",
            "replacement_client_lookup_absent",
            "replacement_client_retained_reference_removed",
            "replacement_client_air",
            "stack_drops",
            "replacement_drops",
            "combined_drops",
        },
        "transition probe",
        error_type,
    )
    lower = _parse_state(transitions["lower_state"], error_type)
    upper = _parse_state(transitions["upper_state"], error_type)
    if (
        lower["shape"] != "bottom"
        or upper["shape"] != "top"
        or any(
            transitions[name] is not True
            for name in (
                "stack_world_block_entity_absent",
                "top_block_entity_present",
                "stack_old_reference_removed",
                "replacement_air",
                "replacement_world_block_entity_absent",
                "replacement_old_reference_removed",
                "stack_client_lookup_absent",
                "stack_client_retained_reference_removed",
                "replacement_client_lookup_absent",
                "replacement_client_retained_reference_removed",
                "replacement_client_air",
            )
        )
        or not _is_exact_int_mapping(
            transitions["stack_drops"],
            {"minecraft:diamond": 1, "minecraft:red_carpet": 1},
        )
        or not _is_exact_int_mapping(
            transitions["replacement_drops"],
            {"minecraft:emerald": 1, "minecraft:blue_carpet": 1},
        )
        or not _is_exact_int_mapping(
            transitions["combined_drops"],
            {
                "minecraft:diamond": 1,
                "minecraft:red_carpet": 1,
                "minecraft:emerald": 1,
                "minecraft:blue_carpet": 1,
            },
        )
    ):
        _fail("drop/stale-block-entity transition contract changed", error_type)


def _fixture(
    x: int,
    y: int,
    z: int,
    shape: str,
    decoration: bool,
    color: str,
    item: str,
    carpet: str,
    block_entity: bool,
    *,
    waterlogged: bool = False,
) -> dict[str, object]:
    return {
        "position": f"{x}, {y}, {z}",
        "shape": shape,
        "decoration": decoration,
        "color": color,
        "facing": "north",
        "waterlogged": waterlogged,
        "item": item,
        "carpet": carpet,
        "block_entity": block_entity,
    }


GALLERY_FIXTURES = (
    _fixture(-8, 121, 4, "full", True, "red", "minecraft:diamondx1", "minecraft:red_carpetx1", True),
    _fixture(-4, 121, 4, "bottom", False, "white", "empty", "empty", False),
    _fixture(-4, 122, 4, "top", True, "blue", "minecraft:emeraldx1", "minecraft:blue_carpetx1", True),
    _fixture(0, 121, 4, "bottom", False, "white", "empty", "empty", False),
    _fixture(0, 122, 4, "middle", False, "white", "empty", "empty", False),
    _fixture(0, 123, 4, "top", True, "yellow", "minecraft:gold_ingotx1", "minecraft:yellow_carpetx1", True),
    _fixture(4, 121, 4, "full", True, "lime", "minecraft:amethyst_shardx1", "minecraft:lime_carpetx1", True),
    _fixture(8, 121, 4, "full", False, "white", "empty", "empty", True, waterlogged=True),
)
TRANSITION_FIXTURES = (
    _fixture(-4, 121, 3, "bottom", False, "white", "empty", "empty", False),
    _fixture(-4, 122, 3, "top", False, "white", "empty", "empty", True),
)
PERSISTENCE_FIXTURES = (
    _fixture(-6, 121, 4, "full", True, "red", "minecraft:diamondx1", "minecraft:red_carpetx1", True),
    _fixture(0, 121, 4, "bottom", False, "white", "empty", "empty", False),
    _fixture(0, 122, 4, "top", True, "blue", "minecraft:emeraldx1", "minecraft:blue_carpetx1", True),
    _fixture(6, 121, 4, "bottom", False, "white", "empty", "empty", False),
    _fixture(6, 122, 4, "middle", False, "white", "empty", "empty", False),
    _fixture(6, 123, 4, "top", True, "yellow", "minecraft:gold_ingotx1", "minecraft:yellow_carpetx1", True),
)


def _validate_snapshot(
    value: object,
    fixtures: tuple[dict[str, object], ...],
    description: str,
    error_type: type[Exception],
) -> None:
    if not isinstance(value, list) or len(value) != len(fixtures):
        _fail(f"{description} fixture cardinality changed", error_type)
    fields = {
        "position",
        "block_id",
        "state",
        "shape",
        "decoration",
        "color",
        "facing",
        "waterlogged",
        "block_entity_type",
        "block_entity_removed",
        "item",
        "carpet",
    }
    for index, (raw, fixture) in enumerate(zip(value, fixtures, strict=True)):
        observation = _require_exact_fields(
            raw, fields, f"{description} fixture {index}", error_type
        )
        properties = _parse_state(observation["state"], error_type)
        expected_type = (
            "etherology:pedestal_block_entity"
            if fixture["block_entity"]
            else "absent"
        )
        if (
            observation["position"] != fixture["position"]
            or observation["block_id"] != "etherology:pedestal"
            or observation["shape"] != fixture["shape"]
            or observation["decoration"] is not fixture["decoration"]
            or observation["color"] != fixture["color"]
            or observation["facing"] != fixture["facing"]
            or observation["waterlogged"] is not fixture["waterlogged"]
            or observation["block_entity_type"] != expected_type
            or observation["block_entity_removed"] is not False
            or observation["item"] != fixture["item"]
            or observation["carpet"] != fixture["carpet"]
            or properties
            != {
                "shape": fixture["shape"],
                "decoration": str(fixture["decoration"]).lower(),
                "cloth_color": fixture["color"],
                "facing": fixture["facing"],
                "waterlogged": str(fixture["waterlogged"]).lower(),
            }
        ):
            _fail(f"{description} fixture {index} changed", error_type)


def _java_bool(value: object) -> str:
    return "true" if value is True else "false"


def _java_list(values: object) -> str:
    return "[" + ", ".join(str(value) for value in values) + "]"


def _java_map(values: dict[str, object], key_order: tuple[str, ...]) -> str:
    entries: list[str] = []
    for name in key_order:
        value = values[name]
        rendered = _java_bool(value) if isinstance(value, bool) else str(value)
        entries.append(f"{name}={rendered}")
    return "{" + ", ".join(entries) + "}"


def _placement_description(value: dict[str, object]) -> str:
    return (
        f"{value['action_result']};accepted={_java_bool(value['accepted'])};"
        f"count={value['before_count']}->{value['after_count']};"
        f"mapping={_java_bool(value['block_item_mapping'])};"
        f"state={value['placed_state']}"
    )


def _shape_description(value: dict[str, object]) -> str:
    entries: list[str] = []
    for name in ("bottom", "middle", "top", "full"):
        shape = value[name]
        entries.append(
            f"{name}={shape['min_x']:.9f},{shape['min_y']:.9f},"
            f"{shape['min_z']:.9f}->{shape['max_x']:.9f},"
            f"{shape['max_y']:.9f},{shape['max_z']:.9f};"
            f"volume={shape['volume']:.9f}"
        )
    return ";".join(entries)


def _interaction_description(values: list[dict[str, object]]) -> str:
    return "|".join(
        f"{value['id']}={value['action_result']};"
        f"hand={value['before_hand']}->{value['after_hand']};"
        f"slots={value['item_slot']},{value['carpet_slot']};"
        f"decoration={value['decoration']};color={value['color']};"
        f"facing={value['facing']}"
        for value in values
    )


def _dispenser_description(value: dict[str, object]) -> str:
    return (
        f"{value['kind']}:{value['direction']}={value['dispenser_slot']}->"
        f"{value['pedestal_item_slot']},{value['pedestal_carpet_slot']};"
        f"decoration={value['decoration']};color={value['color']};"
        f"facing={value['facing']}"
    )


def _snapshot_description(values: list[dict[str, object]]) -> str:
    return "|".join(
        f"{value['position']}={value['state']};be={value['block_entity_type']};"
        f"removed={_java_bool(value['block_entity_removed'])};"
        f"slots={value['item']},{value['carpet']}"
        for value in values
    )


def _expected_assertion_semantics(
    report: dict[str, object],
) -> dict[str, tuple[str, str]]:
    pedestal = report["pedestal"]
    registry = pedestal["registry"]
    data = pedestal["data"]
    placement = pedestal["placement"]
    shapes = pedestal["shapes"]
    interactions = pedestal["interactions"]
    inventory = pedestal["inventory"]
    dispensers = pedestal["dispensers"]
    transitions = pedestal["transitions"]
    expected_default_properties = _java_map(
        EXPECTED_DEFAULT_PROPERTIES,
        DEFAULT_PROPERTY_ORDER,
    )
    actual_default_properties = _java_map(
        registry["default_properties"],
        DEFAULT_PROPERTY_ORDER,
    )
    shape_fields = (
        "min_x",
        "min_y",
        "min_z",
        "max_x",
        "max_y",
        "max_z",
        "volume",
    )
    expected_shapes = {
        name: dict(zip(shape_fields, dimensions, strict=True))
        for name, dimensions in EXPECTED_SHAPES.items()
    }
    expected_shape_description = _shape_description(expected_shapes)
    actual_shape_description = _shape_description(shapes)
    occupied_carpet = next(
        value
        for value in dispensers["observations"]
        if value["kind"] == "occupied-carpet-display"
    )
    full_target = next(
        value
        for value in dispensers["observations"]
        if value["kind"] == "full-target-fallback"
    )
    semantics: dict[str, tuple[str, str]] = {
        "fabric_mod_loaded:etherology": ("loaded", "loaded"),
        "pedestal_resources_exact": (
            "64 byte-pinned resources",
            f"{len(pedestal['resource_pins'])} resources;"
            f"multipart={pedestal['multipart_clause_count']};"
            f"failure={data['failure']}",
        ),
        "pedestal_blockstate_multipart_count_exact": ("68", "68"),
        "registry:block:etherology:pedestal": ("present", "present"),
        "registry:item:etherology:pedestal": ("present", "present"),
        "registry:block_entity_type:etherology:pedestal_block_entity": (
            "present",
            "present",
        ),
        "pedestal_runtime_block_class_exact": (
            "ru.feytox.etherology.block.pedestal.PedestalBlock",
            registry["block_class"],
        ),
        "pedestal_runtime_block_entity_class_exact": (
            "ru.feytox.etherology.block.pedestal.PedestalBlockEntity",
            inventory["block_entity_class"],
        ),
        "pedestal_block_item_mapping_exact": ("true", "true"),
        "pedestal_translation_exact": ("Pedestal", registry["translation"]),
        "pedestal_default_properties_exact": (
            expected_default_properties,
            actual_default_properties,
        ),
        "pedestal_state_count_exact": ("1024", str(registry["state_count"])),
        "pedestal_state_network_ids_exact": (
            "1024 unique non-negative raw ids",
            f"{registry['unique_state_raw_ids']} unique;"
            f"default={registry['default_state_raw_id']}",
        ),
        "pedestal_horizontal_facing_values_exact": (
            "[east, north, south, west]",
            _java_list(registry["horizontal_facing_values"]),
        ),
        "pedestal_pickaxe_tag_exact": ("true", _java_bool(data["pickaxe_mineable"])),
        "pedestal_recipe_exact": (data["recipe"], data["recipe"]),
        "pedestal_advancement_exact": (data["advancement"], data["advancement"]),
        "pedestal_loot_table_exact": (data["loot_table"], data["loot_table"]),
        "pedestal_self_drop_exact": (data["self_drop"], data["self_drop"]),
        "pedestal_native_standalone_placement_exact": (
            "accepted;count=1->0;shape=full",
            _placement_description(placement["standalone"]),
        ),
        "pedestal_native_waterlogged_placement_exact": (
            "accepted;count=1->0;shape=full;waterlogged=true",
            _placement_description(placement["waterlogged"]),
        ),
        "pedestal_outline_shapes_exact": (
            expected_shape_description,
            actual_shape_description,
        ),
        "pedestal_stack_shape_transitions_exact": (
            "standalone=full;two=bottom,top;three=bottom,middle,top",
            _java_list(placement["stack_shapes"]),
        ),
        "pedestal_block_entity_presence_by_shape_exact": (
            "full=true;bottom=false;middle=false;top=true",
            _java_map(
                placement["block_entity_presence"],
                BLOCK_ENTITY_PRESENCE_ORDER,
            ),
        ),
        "pedestal_interaction_sequence_exact": (
            _java_list(value["id"] for value in interactions),
            _interaction_description(interactions),
        ),
        "pedestal_inventory_two_max_one_slots_exact": (
            "size=2;max=1",
            f"size={inventory['size']};max={inventory['max_count_per_stack']}",
        ),
        "pedestal_sided_inventory_closed_exact": (
            "all six sides expose zero slots and deny IO",
            _java_map(inventory["available_slot_counts"], SIDED_DIRECTION_ORDER)
            + f";insert_denied={_java_bool(inventory['insert_denied'])}"
            + f";extract_denied={_java_bool(inventory['extract_denied'])}",
        ),
        "pedestal_nbt_items_exact": (
            "Items=2",
            f"Items={inventory['nbt_item_count']};"
            f"keys={_java_list(inventory['nbt_keys'])}",
        ),
        "pedestal_nbt_removed_flag_exact": (
            "present=true;value=false",
            f"present={_java_bool(inventory['nbt_removed_present'])};"
            f"value={_java_bool(inventory['nbt_removed'])}",
        ),
        "pedestal_item_dispenser_all_six_directions_exact": (
            "[down, up, north, south, west, east]",
            "[down, up, north, south, west, east]",
        ),
        "pedestal_carpet_dispenser_horizontal_directions_exact": (
            "[north, south, west, east]",
            "[north, south, west, east]",
        ),
        "pedestal_occupied_carpet_falls_through_to_display_exact": (
            _dispenser_description(occupied_carpet),
            _dispenser_description(occupied_carpet),
        ),
        "pedestal_full_target_falls_through_to_generic_item_ejection_exact": (
            _dispenser_description(full_target)
            + ";ejected=[minecraft:arrowx1];arrow_projectiles=0",
            _dispenser_description(full_target)
            + f";ejected={_java_list(dispensers['ejected_items'])};"
            f"arrow_projectiles={dispensers['arrow_projectiles']}",
        ),
        "pedestal_stack_transition_drops_exact": (
            "minecraft:diamondx1+minecraft:red_carpetx1",
            "minecraft:diamondx1+minecraft:red_carpetx1",
        ),
        "pedestal_stack_transition_stale_block_entity_removed": (
            "old_removed=true;world_absent=true",
            "old_removed=true;world_absent=true",
        ),
        "pedestal_stack_transition_client_block_entity_removed": (
            "lookup_absent=true;retained_removed=true",
            "lookup_absent=true;retained_removed=true",
        ),
        "pedestal_replacement_drops_exact": (
            "minecraft:blue_carpetx1+minecraft:emeraldx1",
            "minecraft:blue_carpetx1+minecraft:emeraldx1",
        ),
        "pedestal_replacement_stale_block_entity_removed": (
            "old_removed=true;world_absent=true",
            "old_removed=true;world_absent=true",
        ),
        "pedestal_replacement_client_block_entity_removed": (
            "air=true;lookup_absent=true;retained_removed=true",
            "air=true;lookup_absent=true;retained_removed=true",
        ),
        "pedestal_gallery_server_snapshot_exact": (
            "exact",
            _snapshot_description(pedestal["gallery_snapshot"]),
        ),
        "pedestal_transition_server_snapshot_exact": (
            "exact",
            _snapshot_description(pedestal["transition_snapshot"]),
        ),
        "pedestal_persistence_initial_server_snapshot_exact": (
            "exact",
            _snapshot_description(pedestal["persistence_initial_snapshot"]),
        ),
        "pedestal_forced_world_save_exact": ("true", "true"),
        "pedestal_full_restart_completed": ("true", "true"),
        "pedestal_reopened_server_snapshot_exact": (
            "exact",
            _snapshot_description(pedestal["persistence_reopened_snapshot"]),
        ),
        "pedestal_restart_persistence_exact": ("true", "true"),
        "live_world_identity": (
            "Etherology Original 0.1.7 Pedestal;"
            "4995697396257403185;minecraft:overworld",
            "Etherology Original 0.1.7 Pedestal;"
            "4995697396257403185;minecraft:overworld",
        ),
        "isolated_save_directory_present": (
            "etherology-original-pedestal-baseline-world",
            "etherology-original-pedestal-baseline-world",
        ),
    }
    for artifact in report["artifacts"]:
        semantics[f"packaged_root_jar:{artifact['mod_id']}"] = (
            "regular path JAR with SHA-256",
            f"{artifact['file_name']}:{artifact['sha256']}",
        )
    camera_exact = (
        "first_person=true;x=0.5;y=121.0;z=-15.5;"
        "yaw=0.0;pitch=10.0;on_ground=true"
    )
    screenshots = {value["step"]: value for value in report["screenshots"]}
    for phase, file_name in zip(PHASES, SCREENSHOT_FILES, strict=True):
        screenshot = screenshots[phase]
        semantics.update(
            {
                f"client_snapshot_exact:{phase}": ("true", "true"),
                f"native_framebuffer_dimensions:{phase}": (
                    "1920x1080",
                    f"{screenshot['width']}x{screenshot['height']}",
                ),
                f"completed_world_renders_before_capture:{phase}": (
                    "120",
                    str(screenshot["completed_render_count"]),
                ),
                f"capture_render_ready:{phase}": ("true", "true"),
                f"capture_camera_exact:{phase}": (camera_exact, camera_exact),
                f"native_screenshot_written:{phase}": (file_name, file_name),
            }
        )
    return semantics


def _validate_assertions(
    value: object,
    report: dict[str, object],
    error_type: type[Exception],
) -> None:
    if not isinstance(value, list) or len(value) != len(EXPECTED_ASSERTION_NAMES):
        _fail("assertion count changed", error_type)
    names: list[str] = []
    by_name: dict[str, dict[str, object]] = {}
    for assertion in value:
        node = _require_exact_fields(
            assertion,
            {"name", "passed", "expected", "actual"},
            "assertion",
            error_type,
        )
        if (
            not isinstance(node["name"], str)
            or node["passed"] is not True
            or not isinstance(node["expected"], str)
            or not isinstance(node["actual"], str)
        ):
            _fail("assertion did not pass with textual evidence", error_type)
        name = str(node["name"])
        names.append(name)
        by_name[name] = node
    if tuple(names) != EXPECTED_ASSERTION_NAMES:
        _fail("assertion order/inventory changed", error_type)
    semantics = _expected_assertion_semantics(report)
    if set(semantics) != set(EXPECTED_ASSERTION_NAMES):
        _fail("assertion semantics inventory is incomplete", error_type)
    for name in EXPECTED_ASSERTION_NAMES:
        expected, actual = semantics[name]
        if (
            by_name[name]["expected"] != expected
            or by_name[name]["actual"] != actual
        ):
            _fail(f"assertion semantics changed for {name}", error_type)


def _validate_world_files(world_path: Path, error_type: type[Exception]) -> None:
    if world_path.is_symlink() or not world_path.is_dir():
        _fail("saved/reopened world directory is missing or linked", error_type)
    for relative in ("level.dat", "session.lock", "region/r.0.0.mca"):
        path = world_path / relative
        _require_regular(path, f"saved/reopened world proof {relative}", error_type)


def _visual_statistics(image: PngLike, description: str, error_type: type[Exception]) -> None:
    expected_size = image.width * image.height * 3
    if len(image.pixels) != expected_size:
        _fail(f"{description} has an invalid RGB payload", error_type)
    pixel_count = image.width * image.height
    luminance = 0.0
    dark = 0
    missing = 0
    for offset in range(0, len(image.pixels), 3):
        red, green, blue = image.pixels[offset : offset + 3]
        level = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        luminance += level
        dark += level < 48.0
        missing += red >= 240 and green <= 16 and blue >= 240
    if luminance / pixel_count < 100.0 or dark / pixel_count > 0.25 or missing:
        _fail(f"{description} is dark/blank or contains missing textures", error_type)


def _material_changed_ratio(
    initial: PngLike, reopened: PngLike, error_type: type[Exception]
) -> float:
    if (
        initial.width != reopened.width
        or initial.height != reopened.height
        or len(initial.pixels) != len(reopened.pixels)
    ):
        _fail("persistence screenshot dimensions changed", error_type)
    changed = 0
    for offset in range(0, len(initial.pixels), 3):
        delta = max(
            abs(initial.pixels[offset + channel] - reopened.pixels[offset + channel])
            for channel in range(3)
        )
        changed += delta > 24
    ratio = changed / (initial.width * initial.height)
    if ratio > 0.15:
        _fail("reopened Pedestal scene has excessive visual drift", error_type)
    return ratio


def validate_evidence(
    *,
    scenario_root: Path,
    world_path: Path,
    report: dict[str, object],
    expected_artifacts: list[dict[str, object]],
    decode_png: Callable[[Path], PngLike],
    assert_image_is_not_blank: Callable[[PngLike], None],
    sha256_file: Callable[[Path], str],
    error_type: type[Exception] = PedestalEvidenceError,
) -> PedestalEvidenceSummary:
    """Validates a future completed v11 capture without trusting report booleans."""

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
            "pedestal",
            "screenshots",
        },
        "report",
        error_type,
    )
    if (
        not _is_exact_int(report.get("schema"), 4)
        or report.get("reference_id") != "published-0.1.7"
        or report.get("scenario") != SCENARIO_ID
        or report.get("lane") != "fabric-1.21.1-original"
        or report.get("status") != "passed"
        or report.get("passed") is not True
        or type(report.get("client_ticks")) is not int
        or int(report["client_ticks"]) <= 0
        or report.get("lifecycle_failure") != ""
        or not _is_exact_json(report.get("world"), EXPECTED_WORLD)
        or not _is_exact_json(report.get("artifacts"), expected_artifacts)
    ):
        _fail("report did not pass the exact v11 lifecycle", error_type)

    pedestal = _require_exact_fields(
        report.get("pedestal"),
        EXPECTED_MECHANICS_FIELDS,
        "Pedestal mechanics",
        error_type,
    )
    repository_root = Path(__file__).resolve().parents[2]
    if (
        not _is_exact_json(
            pedestal["resource_pins"], _load_resource_pins(repository_root)
        )
        or not _is_exact_int(
            pedestal["multipart_clause_count"], EXPECTED_MULTIPART_CLAUSE_COUNT
        )
        or pedestal["forced_save"] is not True
        or pedestal["full_restart"] is not True
        or pedestal["persistence_exact"] is not True
        or not _is_exact_int(
            pedestal["required_stable_renders"], REQUIRED_STABLE_RENDERS
        )
        or not _is_exact_int(
            pedestal["required_light_ready_client_ticks"],
            REQUIRED_LIGHT_READY_CLIENT_TICKS,
        )
        or not _is_exact_json(
            pedestal["limitations"], [VERTICAL_CARPET_LIMITATION]
        )
    ):
        _fail("Pedestal resource/lifecycle contract changed", error_type)
    _validate_registry(pedestal["registry"], error_type)
    _validate_data(pedestal["data"], error_type)
    _validate_placement(pedestal["placement"], error_type)
    _validate_shapes(pedestal["shapes"], error_type)
    if not _is_exact_json(pedestal["interactions"], _expected_interactions()):
        _fail("Pedestal player interaction sequence changed", error_type)
    _validate_inventory(pedestal["inventory"], error_type)
    _validate_dispensers(pedestal["dispensers"], error_type)
    _validate_transitions(pedestal["transitions"], error_type)
    _validate_snapshot(
        pedestal["gallery_snapshot"],
        GALLERY_FIXTURES,
        "gallery snapshot",
        error_type,
    )
    _validate_snapshot(
        pedestal["transition_snapshot"],
        TRANSITION_FIXTURES,
        "transition snapshot",
        error_type,
    )
    _validate_snapshot(
        pedestal["persistence_initial_snapshot"],
        PERSISTENCE_FIXTURES,
        "initial persistence snapshot",
        error_type,
    )
    _validate_snapshot(
        pedestal["persistence_reopened_snapshot"],
        PERSISTENCE_FIXTURES,
        "reopened persistence snapshot",
        error_type,
    )
    if not _is_exact_json(
        pedestal["persistence_initial_snapshot"],
        pedestal["persistence_reopened_snapshot"],
    ):
        _fail("Pedestal inventory/state changed across full restart", error_type)

    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or len(screenshots) != 4:
        _fail("report must describe exactly four screenshots", error_type)
    decoded: dict[str, PngLike] = {}
    for node, (phase, relative_file) in zip(
        screenshots, EXPECTED_SCREENSHOTS, strict=True
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
        _require_regular(path, f"{phase} screenshot", error_type)
        if (
            not _is_exact_int(entry["width"], 1920)
            or not _is_exact_int(entry["height"], 1080)
            or not _is_exact_int(entry["size"], path.stat().st_size)
            or not _is_exact_int(
                entry["completed_render_count"], REQUIRED_STABLE_RENDERS
            )
            or not _is_exact_json(entry, {
                "step": phase,
                "file": relative_file,
                "width": 1920,
                "height": 1080,
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
                "completed_render_count": REQUIRED_STABLE_RENDERS,
                "source": "minecraft-framebuffer",
                "edited": False,
            })
        ):
            _fail(f"{phase} screenshot record does not match its native PNG", error_type)
        image = decode_png(path)
        assert_image_is_not_blank(image)
        if (image.width, image.height) != EXPECTED_FRAMEBUFFER:
            _fail(f"{phase} screenshot is not 1920x1080", error_type)
        _visual_statistics(image, phase, error_type)
        decoded[phase] = image
    ratio = _material_changed_ratio(
        decoded["persistence-initial"],
        decoded["persistence-reopened"],
        error_type,
    )
    _validate_assertions(report.get("assertions"), report, error_type)
    _validate_world_files(world_path, error_type)
    return PedestalEvidenceSummary(
        assertion_count=len(EXPECTED_ASSERTION_NAMES),
        screenshot_count=len(SCREENSHOT_FILES),
        persistence_material_changed_pixel_ratio=ratio,
    )
