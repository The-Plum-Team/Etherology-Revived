#!/usr/bin/env python3
"""Validate live or archived Forge Attrahite block evidence."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import sys
import tempfile


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_client
import forge_evidence


SCENARIO_ID = "attrahite-block-registry"
PROFILE_ID = "etherology-e2e-forge-1.20.1-v17"
ACTIVE_PROFILE_RELATIVE_PATH = "scripts/e2e/forge-1.20.1-profile.json"
SNAPSHOT_PROFILE_RELATIVE_PATH = "scripts/e2e/forge-1.20.1-profile-v17.json"
PROFILE_SIZE = 3702
PROFILE_SHA256 = "00475fd4af5741119b44b3ca70484e967ee0b7a8c51fdc222ebdde3e2bf0ba58"
HARNESS_SIZE = 244324
HARNESS_SHA256 = "9921ec314c9aa411ca1c2f9632faa1a9e05a60b62589d12a416c228bc85170b8"
ARCHIVE_DIRECTORY_NAME = "attrahite-block-registry-v17"
ARCHIVE_DIRECTORY_PATTERN = re.compile(r"attrahite-block-registry-v[1-9][0-9]*")
ARCHIVE_MANIFEST_NAME = forge_evidence.ARCHIVE_MANIFEST_NAME
ARCHIVE_KIND = forge_evidence.ARCHIVE_KIND
ARCHIVE_VERIFICATION_SCOPE = forge_evidence.ARCHIVE_VERIFICATION_SCOPE
ARCHIVE_CAPTURE_METADATA_PATH = "forge-artifact-lock.json"
ARCHIVE_DIRECTORIES = {"reports", "screenshots"}
PHASES = ("initial", "reopened")
SCREENSHOT_FILES = tuple(f"attrahite-block-registry-{phase}.png" for phase in PHASES)
EXPECTED_SCREENSHOTS = tuple(
    (phase, f"screenshots/{file_name}")
    for phase, file_name in zip(PHASES, SCREENSHOT_FILES, strict=True)
)
ARCHIVE_PAYLOAD_PATHS = (
    "reports/report.json",
    "reports/done.marker",
    *(f"screenshots/{file_name}" for file_name in SCREENSHOT_FILES),
)
ARCHIVE_PUBLICATION_ATTESTATION = {
    "completion_marker": "reports/done.marker",
    "verified_last_in_capture_runtime": True,
    "archive_payloads_match_capture_runtime": True,
}
EXPECTED_FRAMEBUFFER_DIMENSIONS = (1920, 1080)
REQUIRED_STABLE_RENDERS = 120
REQUIRED_PRE_SETUP_LIGHT_READY_CLIENT_TICKS = 20
MAXIMUM_REOPEN_STRUCTURAL_CHANGE_RATIO = 0.06
MAXIMUM_REOPEN_CHANGED_PIXEL_RATIO = 0.35
GALLERY_ROI_PERCENT = (20, 80, 35, 85)
GALLERY_DARK_LUMINANCE_CUTOFF = 32
MINIMUM_GALLERY_MEAN_LUMINANCE = 55.0
MINIMUM_GALLERY_MEDIAN_LUMINANCE = 45
MAXIMUM_GALLERY_DARK_PIXEL_RATIO = 0.20
MAXIMUM_REOPEN_GALLERY_MEAN_DELTA = 24.0
MAXIMUM_REOPEN_GALLERY_MEDIAN_DELTA = 24
MAXIMUM_REOPEN_GALLERY_DARK_PIXEL_RATIO_DELTA = 0.12
LIGHTING_ASSERTION_DESCRIPTION = (
    "stableServerTicks=20;stableClientTicks=20;serverPending=diagnostic;"
    "clientPending=diagnostic;serverClientExact=true;server=expected;client=server"
)
LIGHTING_SAMPLE_DESCRIPTION = (
    "[0,121,-8=15/14,0,121,0=15/14,-3,123,1=15/10,"
    "-1,123,1=15/10,1,123,1=15/10,3,123,1=15/8]"
)
LIGHT_SAMPLE_POSITIONS = (
    "0,121,-8",
    "0,121,0",
    "-3,123,1",
    "-1,123,1",
    "1,123,1",
    "3,123,1",
)
ARENA_CHUNKS = ("-1,-1", "-1,0", "0,-1", "0,0")
EXPECTED_ENABLED_LIGHT_COLUMNS = dict.fromkeys(ARENA_CHUNKS, True)
EXPECTED_PRE_SETUP_SKY_LIGHT = dict.fromkeys(LIGHT_SAMPLE_POSITIONS, 15)
EXPECTED_PRE_SETUP_LIGHTING_DESCRIPTION = (
    "20 consecutive ticks: four chunks loaded; chunk update queue empty; "
    "lighting provider idle; four enabled columns; sky=[15, 15, 15, 15, 15, 15]"
)
FATAL_GAME_LOG_MARKERS = (
    *forge_evidence.FATAL_GAME_LOG_MARKERS,
    "Attrahite lifecycle failure:",
)
EXPECTED_SCENARIOS = (
    "ethereal-storage",
    "ethereal-channel",
    "forest-lantern",
    SCENARIO_ID,
)
EXPECTED_ARTIFACTS = (
    ("production", "etherology", "etherology-forge-under-test.jar"),
    ("harness", "etherology_e2e_harness", "etherology-forge-e2e-harness.jar"),
)
EXPECTED_WORLD = {
    "save_directory": "etherology-e2e-attrahite-block-registry-world",
    "display_name": "Etherology E2E Attrahite Blocks",
    "seed": 4707480222768318804,
    "dimension": "minecraft:overworld",
    "integrated": True,
    "reopened": True,
}
BLOCK_SPECS = (
    {
        "id": "etherology:attrahite",
        "x": -3,
        "class": "net.minecraft.world.level.block.Block",
        "default_state": "etherology:attrahite",
        "state_count": 1,
        "needs_stone_tool": True,
        "slab": False,
        "stairs": False,
    },
    {
        "id": "etherology:attrahite_bricks",
        "x": -1,
        "class": "net.minecraft.world.level.block.Block",
        "default_state": "etherology:attrahite_bricks",
        "state_count": 1,
        "needs_stone_tool": False,
        "slab": False,
        "stairs": False,
    },
    {
        "id": "etherology:attrahite_brick_slab",
        "x": 1,
        "class": "net.minecraft.world.level.block.SlabBlock",
        "default_state": (
            "etherology:attrahite_brick_slab[type=bottom,waterlogged=false]"
        ),
        "state_count": 6,
        "needs_stone_tool": False,
        "slab": True,
        "stairs": False,
    },
    {
        "id": "etherology:attrahite_brick_stairs",
        "x": 3,
        "class": "net.minecraft.world.level.block.StairBlock",
        "default_state": (
            "etherology:attrahite_brick_stairs"
            "[facing=north,half=bottom,shape=straight,waterlogged=false]"
        ),
        "state_count": 80,
        "needs_stone_tool": False,
        "slab": False,
        "stairs": True,
    },
)
BLOCK_ITEM_CLASS = "net.minecraft.world.item.BlockItem"
EXPECTED_READY_RESOURCES = (
    "etherology:blockstates/attrahite.json",
    "etherology:blockstates/attrahite_bricks.json",
    "etherology:blockstates/attrahite_brick_slab.json",
    "etherology:blockstates/attrahite_brick_stairs.json",
    "etherology:models/block/attrahite.json",
    "etherology:models/block/attrahite_bricks.json",
    "etherology:models/block/attrahite_brick_slab.json",
    "etherology:models/block/attrahite_brick_slab_top.json",
    "etherology:models/block/attrahite_brick_stairs.json",
    "etherology:models/block/attrahite_brick_stairs_inner.json",
    "etherology:models/block/attrahite_brick_stairs_outer.json",
    "etherology:models/item/attrahite.json",
    "etherology:models/item/attrahite_bricks.json",
    "etherology:models/item/attrahite_brick_slab.json",
    "etherology:models/item/attrahite_brick_stairs.json",
    "etherology:textures/block/attrahite.png",
    "etherology:textures/block/attrahite_bricks.png",
)
EXPECTED_RESOURCE_SHA256 = {
    "etherology:blockstates/attrahite.json": "714d5913c7743e6fb9d7a5309d91e375c09686bc9e7563594ef90266fedb8467",
    "etherology:blockstates/attrahite_bricks.json": "28d7fe3a1fd137e338a210878a7a0e811d570a063201f4a8556f827f4d3cee54",
    "etherology:blockstates/attrahite_brick_slab.json": "527e541bf9cd76b8d03615157930ea1f6d02e944cfaf210541a042ecc7730d70",
    "etherology:blockstates/attrahite_brick_stairs.json": "a318e997a883e7cc3b1e1193f654f6f9cf350a9d8c4968d6777558120f6b2c85",
    "etherology:models/block/attrahite.json": "fa2ef32e5c8a05e97b11f2b7e165420b8c58c37a4a8980acb09eb20567f0cfe4",
    "etherology:models/block/attrahite_bricks.json": "a3ca96e4d64a795741e6d64edd3633ae0de8fe8e358e40a7c5c083607f4997cb",
    "etherology:models/block/attrahite_brick_slab.json": "6911228282c04ae043603142b6c0f8aa36510fcef2a16981cac99e87f6f9591c",
    "etherology:models/block/attrahite_brick_slab_top.json": "df34597225adffecc923a226230f9d21375a6991966d6365ca3a0dd73c494514",
    "etherology:models/block/attrahite_brick_stairs.json": "d450badc33ce469cf88952432be54744c86205c4dc0d14e35ce8bcd7259d7697",
    "etherology:models/block/attrahite_brick_stairs_inner.json": "b2824d3f7e59a3cfea4bdabc98f479a5e4d635fc277f3dcc0ec61f02e44ef47a",
    "etherology:models/block/attrahite_brick_stairs_outer.json": "5ccb32dece7c1b4dc11be5b33dfe68f4f8cbb5eb066603afa43fba331aa9f0f3",
    "etherology:models/item/attrahite.json": "694825d82227bad82c5fe813b72b0539ca5b25001957d086861d3b723a21c6cf",
    "etherology:models/item/attrahite_bricks.json": "eacdf3ece38696ceb7d78db1aca1f666b12363c9b50e0972e57afc78c6532d4a",
    "etherology:models/item/attrahite_brick_slab.json": "420600e00f5848cd9f2759cebc73b4062b603cf1647ee5e00cbce16b32fe21ac",
    "etherology:models/item/attrahite_brick_stairs.json": "08efcd6a8313310b2102ce53d236e456d1e19f17732fba4ea2481702ddebb16e",
    "etherology:textures/block/attrahite.png": "e206aa66882b20816250a6fbfc7080a66dbca55a885020f2bed09d8087e02825",
    "etherology:textures/block/attrahite_bricks.png": "a92bc03adc772da001d8f5eafbe1aaaad4a498776e3913294c640aafa9172be1",
}
EXPECTED_LOOT_TABLE_IDS = (
    "etherology:blocks/attrahite",
    "etherology:blocks/attrahite_brick_slab",
    "etherology:blocks/attrahite_brick_stairs",
    "etherology:blocks/attrahite_bricks",
)
EXPECTED_RECIPE_IDS = (
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
EXPECTED_ADVANCEMENT_IDS = (
    "etherology:recipes/building_blocks/attrahite_brick_slab",
    (
        "etherology:recipes/building_blocks/"
        "attrahite_brick_slab_from_attrahite_bricks_stonecutting"
    ),
    "etherology:recipes/building_blocks/attrahite_brick_stairs",
    (
        "etherology:recipes/building_blocks/"
        "attrahite_brick_stairs_from_attrahite_bricks_stonecutting"
    ),
    "etherology:recipes/building_blocks/attrahite_bricks",
    "etherology:recipes/misc/attrahite_brick",
    "etherology:recipes/misc/azel_ingot",
    "etherology:recipes/misc/azel_ingot_from_blasting",
    "etherology:recipes/misc/raw_azel",
)
STANDARD_LOOT = (
    "{etherology:attrahite_brick_slab=etherology:attrahite_brick_slabx1, "
    "etherology:attrahite_brick_stairs=etherology:attrahite_brick_stairsx1, "
    "etherology:attrahite_bricks=etherology:attrahite_bricksx1}"
)
RAW_PLAIN_LOOT = (
    "1=none,4096=none,4224=none,"
    "4640=etherology:enriched_attrahitex1,7168=none"
)
RAW_SILK_LOOT = "etherology:attrahitex1"
RAW_FORTUNE_LOOT = (
    "{1=1=none,4096=etherology:enriched_attrahitex1,4224=none,"
    "4640=etherology:enriched_attrahitex1,7168=none, "
    "2=1=none,4096=etherology:enriched_attrahitex1,"
    "4224=etherology:enriched_attrahitex1,"
    "4640=etherology:enriched_attrahitex1,7168=none, "
    "3=1=none,4096=etherology:enriched_attrahitex1,"
    "4224=etherology:enriched_attrahitex1,"
    "4640=etherology:enriched_attrahitex1,"
    "7168=etherology:enriched_attrahitex1}"
)
CAMERA_SUMMARY = (
    "first_person=true;x=0.5;y=121.0;z=-7.5;yaw=0.0;pitch=3.0;"
    "on_ground=true;tolerance=1.0E-4"
)
EXPECTED_REPORT_FIELDS = (
    "schema",
    "scenario",
    "profile_id",
    "profile_manifest_size",
    "profile_manifest_sha256",
    "artifact_node",
    "minecraft",
    "loader",
    "loader_version",
    "java",
    "lane",
    "role",
    "status",
    "passed",
    "client_ticks",
    "lifecycle_failure",
    "assertions",
    "world",
    "ready_resources",
    "artifacts",
    "screenshots",
    "attrahite",
)
EXPECTED_ATTRAHITE_FIELDS = (
    "fixtures",
    "loot_tables",
    "standard_loot",
    "raw_plain_loot",
    "raw_silk_touch_loot",
    "raw_fortune_loot",
    "recipes",
    "advancements",
    "placements",
    "persistence_exact",
    "reopened_data_exact",
    "required_stable_renders",
    "pre_setup_lighting",
)
EXPECTED_PRE_SETUP_LIGHTING_FIELDS = (
    "stable_client_ticks",
    "arena_chunks_loaded",
    "chunk_updaters_empty",
    "pending",
    "enabled_columns",
    "sky",
)


def create_assertion_names() -> tuple[str, ...]:
    names = ["forge_mod_loaded:etherology"]
    for block in BLOCK_SPECS:
        block_id = block["id"]
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
                f"tag:mineable/pickaxe:{block_id}",
                f"tag:needs_stone_tool:{block_id}",
                f"tag:block/slabs:{block_id}",
                f"tag:item/slabs:{block_id}",
                f"tag:block/stairs:{block_id}",
                f"tag:item/stairs:{block_id}",
            )
        )
    names.extend(
        (
            "client_render_resources",
            "packaged_root_jar:etherology",
            "packaged_root_jar:etherology_e2e_harness",
            "integrated_world_joined",
            "client_arena_chunks_loaded_before_setup",
            "client_arena_light_payloads_applied_before_setup",
            "server_arena_chunks_loaded",
            "loot_tables_exact",
            "standard_block_drops_exact",
            "raw_plain_drops_deterministic",
            "raw_silk_touch_drop_exact",
            "raw_fortune_drops_deterministic",
            "recipes_exact_and_craftable",
            "advancements_exact",
            "direct_block_item_placements_exact",
            "initial_server_fixture_exact",
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
    return tuple(names)


EXPECTED_ASSERTION_NAMES = create_assertion_names()


@dataclass(frozen=True)
class AttrahiteEvidenceSummary:
    """Reports the evidence inventory accepted by the v17 verifier."""

    profile_id: str
    assertion_count: int
    screenshot_count: int
    reopen_changed_pixel_ratio: float
    production_sha256: str
    harness_sha256: str


@dataclass(frozen=True)
class GalleryLightingMetrics:
    """Summarizes absolute gallery lighting without including sky or HUD."""

    mean_luminance: float
    median_luminance: int
    dark_pixel_ratio: float


def java_list(values: tuple[str, ...]) -> str:
    return "[" + ", ".join(values) + "]"


def fixture_descriptions() -> tuple[str, ...]:
    return tuple(f"{block['id']}@{block['x']},122,1" for block in BLOCK_SPECS)


def snapshot_description() -> str:
    return ";".join(
        f"{block['id']}={block['default_state']}|pedestal=minecraft:polished_andesite"
        for block in BLOCK_SPECS
    )


def placement_description() -> str:
    rows = []
    for block in BLOCK_SPECS:
        evidence_value = (
            "PlacementEvidence[actionResult=CONSUME, accepted=true, "
            "beforeCount=1, afterCount=0, blockItemMapping=true, "
            f"placedId={block['id']}, placedState={block['default_state']}]"
        )
        rows.append(f"{block['id']}={evidence_value}")
    return ";".join(rows)


EXPECTED_FIXTURES = fixture_descriptions()
EXPECTED_SNAPSHOT = snapshot_description()
EXPECTED_PLACEMENTS = placement_description()


def resource_path(repository_root: Path, identifier: str) -> Path:
    namespace, relative_path = identifier.split(":", 1)
    source_root = (
        repository_root / "src/client/resources/assets"
        if relative_path.startswith("textures/")
        else repository_root / "src/main/generated/assets"
    )
    return source_root / namespace / relative_path


def validate_canonical_resources(repository_root: Path) -> None:
    for identifier, expected_sha256 in EXPECTED_RESOURCE_SHA256.items():
        path = resource_path(repository_root, identifier)
        if (
            not path.is_file()
            or path.is_symlink()
            or forge_evidence.sha256_file(path) != expected_sha256
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite canonical resource bytes changed: {path}"
            )


def validate_active_profile(configuration: forge_client.ResolvedConfiguration) -> None:
    """Requires the immutable v17 profile and exact runtime contract."""

    active_profile = configuration.repository_root / ACTIVE_PROFILE_RELATIVE_PATH
    snapshot_profile = configuration.repository_root / SNAPSHOT_PROFILE_RELATIVE_PATH
    for path in (active_profile, snapshot_profile):
        if not path.is_file() or path.is_symlink():
            raise forge_client.E2EError(f"Forge Attrahite profile is missing or linked: {path}")
        if path.stat().st_size != PROFILE_SIZE or forge_client.sha256_file(path) != PROFILE_SHA256:
            raise forge_client.E2EError(f"Forge Attrahite profile bytes changed: {path}")
    if active_profile.read_bytes() != snapshot_profile.read_bytes():
        raise forge_client.E2EError("The active Forge profile differs from its v17 snapshot")

    profile = forge_client.profile_spec(configuration)
    capture = forge_client.require_object(
        forge_client.require_object(configuration.manifest, "evidence"),
        "capture",
    )
    if (
        profile.get("id") != PROFILE_ID
        or profile.get("runtime_directory") != PROFILE_ID
        or tuple(forge_client.scenario_ids(configuration)) != EXPECTED_SCENARIOS
        or configuration.artifact_lane.get("artifact_node") != "forge-1.20.1"
        or configuration.runtime_lane.get("runtime_version") != "1.20.1"
        or configuration.runtime_lane.get("loader") != "forge"
        or configuration.runtime_lane.get("loader_version") != "1.20.1-47.4.9"
        or configuration.runtime_lane.get("java") != 17
        or capture.get("kind") != "composed-minecraft-framebuffer"
        or (capture.get("width"), capture.get("height"))
        != EXPECTED_FRAMEBUFFER_DIMENSIONS
    ):
        raise forge_client.E2EError("The Forge Attrahite v17 profile contract changed")
    for role, mod_id, file_name in EXPECTED_ARTIFACTS:
        artifact = forge_client.artifact_spec(configuration, role)
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            raise forge_client.E2EError(
                f"The Forge Attrahite profile has the wrong {role} artifact identity"
            )
    validate_canonical_resources(configuration.repository_root)


def validate_artifact_inventory(
    report: dict[str, object],
) -> dict[str, dict[str, object]]:
    artifacts = report.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) != len(EXPECTED_ARTIFACTS):
        raise forge_client.E2EError("Forge Attrahite artifact inventory changed")
    validated: dict[str, dict[str, object]] = {}
    for artifact, (role, mod_id, file_name) in zip(
        artifacts, EXPECTED_ARTIFACTS, strict=True
    ):
        if not isinstance(artifact, dict) or tuple(artifact) != (
            "mod_id",
            "passed",
            "file_name",
            "size",
            "sha256",
            "failure",
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite {role} artifact provenance is malformed"
            )
        if (
            artifact.get("mod_id") != mod_id
            or artifact.get("passed") is not True
            or artifact.get("file_name") != file_name
            or type(artifact.get("size")) is not int
            or int(artifact["size"]) <= 0
            or artifact.get("failure") != ""
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite {role} artifact identity is invalid"
            )
        digest = forge_client.validate_hex_digest(
            artifact.get("sha256"), f"Forge Attrahite {role} artifact"
        )
        if role == "harness" and (
            artifact["size"] != HARNESS_SIZE or digest != HARNESS_SHA256
        ):
            raise forge_client.E2EError(
                "Forge Attrahite harness artifact differs from its packaged pin"
            )
        validated[role] = {
            "mod_id": mod_id,
            "file_name": file_name,
            "size": artifact["size"],
            "sha256": digest,
        }
    return validated


def validate_attrahite(report: dict[str, object]) -> None:
    attrahite = report.get("attrahite")
    if not isinstance(attrahite, dict) or tuple(attrahite) != EXPECTED_ATTRAHITE_FIELDS:
        raise forge_client.E2EError("Forge Attrahite mechanics field inventory changed")
    expected = {
        "fixtures": list(EXPECTED_FIXTURES),
        "loot_tables": list(EXPECTED_LOOT_TABLE_IDS),
        "standard_loot": STANDARD_LOOT,
        "raw_plain_loot": RAW_PLAIN_LOOT,
        "raw_silk_touch_loot": RAW_SILK_LOOT,
        "raw_fortune_loot": RAW_FORTUNE_LOOT,
        "recipes": list(EXPECTED_RECIPE_IDS),
        "advancements": list(EXPECTED_ADVANCEMENT_IDS),
        "placements": EXPECTED_PLACEMENTS,
        "persistence_exact": True,
        "reopened_data_exact": True,
        "required_stable_renders": REQUIRED_STABLE_RENDERS,
    }
    mechanics = {key: attrahite[key] for key in expected}
    if mechanics != expected:
        raise forge_client.E2EError("Forge Attrahite mechanics contract changed")
    validate_pre_setup_lighting(attrahite.get("pre_setup_lighting"))


def validate_pre_setup_lighting(pre_setup: object) -> None:
    if (
        not isinstance(pre_setup, dict)
        or tuple(pre_setup) != EXPECTED_PRE_SETUP_LIGHTING_FIELDS
    ):
        raise forge_client.E2EError(
            "Forge Attrahite pre-setup lighting fields changed"
        )
    enabled_columns = pre_setup.get("enabled_columns")
    sky = pre_setup.get("sky")
    if (
        type(pre_setup.get("stable_client_ticks")) is not int
        or pre_setup.get("stable_client_ticks")
        != REQUIRED_PRE_SETUP_LIGHT_READY_CLIENT_TICKS
        or pre_setup.get("arena_chunks_loaded") is not True
        or pre_setup.get("chunk_updaters_empty") is not True
        or pre_setup.get("pending") is not False
        or not isinstance(enabled_columns, dict)
        or tuple(enabled_columns) != ARENA_CHUNKS
        or enabled_columns != EXPECTED_ENABLED_LIGHT_COLUMNS
        or any(type(value) is not bool for value in enabled_columns.values())
        or not isinstance(sky, dict)
        or tuple(sky) != LIGHT_SAMPLE_POSITIONS
        or sky != EXPECTED_PRE_SETUP_SKY_LIGHT
        or any(type(value) is not int for value in sky.values())
    ):
        raise forge_client.E2EError("Forge Attrahite pre-setup lighting changed")


def expected_assertion_evidence(
    screenshots: list[dict[str, object]],
) -> dict[str, tuple[str, str]]:
    expected: dict[str, tuple[str, str]] = {
        "forge_mod_loaded:etherology": ("loaded", "loaded"),
        "client_render_resources": (
            java_list(EXPECTED_READY_RESOURCES),
            java_list(EXPECTED_READY_RESOURCES),
        ),
        "packaged_root_jar:etherology": (
            "one regular root JAR",
            "one regular root JAR",
        ),
        "packaged_root_jar:etherology_e2e_harness": (
            "one regular root JAR",
            "one regular root JAR",
        ),
        "integrated_world_joined": (
            "running server and connected client",
            "joined",
        ),
        "client_arena_chunks_loaded_before_setup": ("true", "true"),
        "client_arena_light_payloads_applied_before_setup": (
            EXPECTED_PRE_SETUP_LIGHTING_DESCRIPTION,
            EXPECTED_PRE_SETUP_LIGHTING_DESCRIPTION,
        ),
        "server_arena_chunks_loaded": ("four full chunks", "true"),
        "loot_tables_exact": (
            java_list(EXPECTED_LOOT_TABLE_IDS),
            java_list(EXPECTED_LOOT_TABLE_IDS),
        ),
        "standard_block_drops_exact": (STANDARD_LOOT, STANDARD_LOOT),
        "raw_plain_drops_deterministic": (RAW_PLAIN_LOOT, RAW_PLAIN_LOOT),
        "raw_silk_touch_drop_exact": (RAW_SILK_LOOT, RAW_SILK_LOOT),
        "raw_fortune_drops_deterministic": (
            RAW_FORTUNE_LOOT,
            RAW_FORTUNE_LOOT,
        ),
        "recipes_exact_and_craftable": (
            java_list(EXPECTED_RECIPE_IDS),
            java_list(EXPECTED_RECIPE_IDS),
        ),
        "advancements_exact": (
            java_list(EXPECTED_ADVANCEMENT_IDS),
            java_list(EXPECTED_ADVANCEMENT_IDS),
        ),
        "direct_block_item_placements_exact": (
            "four accepted 1->0 default-state placements",
            EXPECTED_PLACEMENTS,
        ),
        "initial_server_fixture_exact": (EXPECTED_SNAPSHOT, EXPECTED_SNAPSHOT),
        "forced_world_save": ("true", "true"),
        "restart_fixture_persistence_exact": (
            "saved snapshot equals reopened snapshot",
            "exact",
        ),
        "restart_loaded_data_exact": (
            "same exact loaded-data outcome",
            "exact",
        ),
        "isolated_save_directory_present": (
            EXPECTED_WORLD["save_directory"],
            EXPECTED_WORLD["save_directory"],
        ),
    }
    for block in BLOCK_SPECS:
        block_id = str(block["id"])
        exact_values = {
            f"registry:block:{block_id}": ("present", "true"),
            f"registry:item:{block_id}": ("present", "true"),
            f"runtime:block_class:{block_id}": (
                str(block["class"]),
                str(block["class"]),
            ),
            f"runtime:block_item_class:{block_id}": (
                BLOCK_ITEM_CLASS,
                BLOCK_ITEM_CLASS,
            ),
            f"block_item_mapping:{block_id}": ("true", "true"),
            f"default_state:{block_id}": (
                str(block["default_state"]),
                str(block["default_state"]),
            ),
            f"state_count:{block_id}": (
                str(block["state_count"]),
                str(block["state_count"]),
            ),
            f"tag:mineable/pickaxe:{block_id}": ("true", "true"),
            f"tag:needs_stone_tool:{block_id}": (
                str(block["needs_stone_tool"]).lower(),
                str(block["needs_stone_tool"]).lower(),
            ),
            f"tag:block/slabs:{block_id}": (
                str(block["slab"]).lower(),
                str(block["slab"]).lower(),
            ),
            f"tag:item/slabs:{block_id}": (
                str(block["slab"]).lower(),
                str(block["slab"]).lower(),
            ),
            f"tag:block/stairs:{block_id}": (
                str(block["stairs"]).lower(),
                str(block["stairs"]).lower(),
            ),
            f"tag:item/stairs:{block_id}": (
                str(block["stairs"]).lower(),
                str(block["stairs"]).lower(),
            ),
        }
        expected.update(exact_values)
    screenshot_by_phase = {
        str(screenshot["step"]): screenshot for screenshot in screenshots
    }
    for phase in PHASES:
        screenshot = screenshot_by_phase[phase]
        expected.update(
            {
                f"capture_mirror_exact:{phase}": (
                    EXPECTED_SNAPSHOT,
                    EXPECTED_SNAPSHOT,
                ),
                f"capture_render_ready:{phase}": ("true", "true"),
                f"capture_lighting_ready:{phase}": (
                    LIGHTING_ASSERTION_DESCRIPTION,
                    lighting_assertion_actual(False, False),
                ),
                f"capture_camera_exact:{phase}": (
                    CAMERA_SUMMARY,
                    CAMERA_SUMMARY,
                ),
                f"capture_consecutive_stable_renders:{phase}": ("120", "120"),
                f"capture_framebuffer_dimensions:{phase}": (
                    "1920x1080",
                    "1920x1080",
                ),
                f"native_screenshot_written:{phase}": (
                    "one non-empty unedited framebuffer PNG",
                    f"{screenshot['size']} bytes, sha256={screenshot['sha256']}",
                ),
            }
        )
    return expected


def lighting_assertion_actual(
    server_pending: bool,
    client_pending: bool,
) -> str:
    return (
        "stableServerTicks=20;stableClientTicks=20;serverPending="
        f"{str(server_pending).lower()};clientPending={str(client_pending).lower()};"
        "serverClientExact=true;"
        f"server={LIGHTING_SAMPLE_DESCRIPTION};client={LIGHTING_SAMPLE_DESCRIPTION}"
    )


def validate_assertions(
    report: dict[str, object], screenshots: list[dict[str, object]]
) -> None:
    assertions = report.get("assertions")
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise forge_client.E2EError("Forge Attrahite assertion inventory changed")
    expected_evidence = expected_assertion_evidence(screenshots)
    network_ids: list[int] = []
    for assertion, expected_name in zip(
        assertions, EXPECTED_ASSERTION_NAMES, strict=True
    ):
        if not isinstance(assertion, dict) or tuple(assertion) != (
            "name",
            "passed",
            "expected",
            "actual",
        ):
            raise forge_client.E2EError("Forge Attrahite assertion fields changed")
        if assertion.get("name") != expected_name or assertion.get("passed") is not True:
            raise forge_client.E2EError("Forge Attrahite assertion inventory changed")
        if expected_name.startswith("capture_lighting_ready:"):
            valid_actuals = {
                lighting_assertion_actual(server_pending, client_pending)
                for server_pending in (False, True)
                for client_pending in (False, True)
            }
            if (
                assertion.get("expected") != LIGHTING_ASSERTION_DESCRIPTION
                or assertion.get("actual") not in valid_actuals
            ):
                raise forge_client.E2EError(
                    "Forge Attrahite lighting evidence changed"
                )
            continue
        if expected_name.startswith("default_state_network_id:"):
            actual = assertion.get("actual")
            if (
                assertion.get("expected") != "non-negative"
                or not isinstance(actual, str)
                or re.fullmatch(r"0|[1-9][0-9]*", actual) is None
            ):
                raise forge_client.E2EError("Forge Attrahite network-id evidence changed")
            network_ids.append(int(actual))
            continue
        pair = expected_evidence.get(expected_name)
        if pair is None or (
            assertion.get("expected"),
            assertion.get("actual"),
        ) != pair:
            raise forge_client.E2EError(
                f"Forge Attrahite assertion evidence changed: {expected_name}"
            )
    if len(network_ids) != len(BLOCK_SPECS) or len(set(network_ids)) != len(network_ids):
        raise forge_client.E2EError("Forge Attrahite default-state network ids are not unique")


def validate_screenshot_inventory(
    report: dict[str, object],
) -> list[dict[str, object]]:
    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or len(screenshots) != len(
        EXPECTED_SCREENSHOTS
    ):
        raise forge_client.E2EError("Forge Attrahite screenshot inventory changed")
    validated: list[dict[str, object]] = []
    expected_fields = (
        "step",
        "role",
        "file",
        "width",
        "height",
        "size",
        "sha256",
        "completed_render_count",
        "source",
        "edited",
    )
    for screenshot, (expected_step, expected_file) in zip(
        screenshots, EXPECTED_SCREENSHOTS, strict=True
    ):
        if not isinstance(screenshot, dict) or tuple(screenshot) != expected_fields:
            raise forge_client.E2EError("Forge Attrahite screenshot provenance is malformed")
        if (
            screenshot.get("step") != expected_step
            or screenshot.get("role") != "host"
            or screenshot.get("file") != expected_file
            or type(screenshot.get("width")) is not int
            or type(screenshot.get("height")) is not int
            or (screenshot.get("width"), screenshot.get("height"))
            != EXPECTED_FRAMEBUFFER_DIMENSIONS
            or type(screenshot.get("size")) is not int
            or int(screenshot["size"]) <= 0
            or type(screenshot.get("completed_render_count")) is not int
            or screenshot.get("completed_render_count") != REQUIRED_STABLE_RENDERS
            or screenshot.get("source") != "minecraft-framebuffer"
            or screenshot.get("edited") is not False
        ):
            raise forge_client.E2EError("Forge Attrahite screenshot contract is invalid")
        forge_client.validate_hex_digest(
            screenshot.get("sha256"), f"Forge Attrahite screenshot {expected_file}"
        )
        validated.append(screenshot)
    return validated


def count_missing_texture_pixels(image: forge_evidence.PngImage) -> int:
    count = 0
    for offset in range(0, len(image.pixels), 3):
        red, green, blue = image.pixels[offset : offset + 3]
        if red >= 180 and blue >= 180 and green <= 80:
            count += 1
    return count


def structural_change_ratio(
    left: forge_evidence.PngImage,
    right: forge_evidence.PngImage,
) -> float:
    """Compares gallery edges while tolerating uniform restart lighting shifts."""

    if (left.width, left.height) != (right.width, right.height):
        raise forge_client.E2EError(
            "Forge Attrahite structural comparison dimensions differ"
        )
    left_x = left.width * 20 // 100
    right_x = left.width * 80 // 100
    top_y = left.height * 15 // 100
    bottom_y = left.height * 85 // 100
    changed = 0
    compared = 0

    def luminance(pixels: bytes, offset: int) -> int:
        return (
            77 * pixels[offset]
            + 150 * pixels[offset + 1]
            + 29 * pixels[offset + 2]
        ) >> 8

    for y in range(top_y, bottom_y - 1):
        for x in range(left_x, right_x - 1):
            offset = (y * left.width + x) * 3
            right_offset = offset + 3
            below_offset = offset + left.width * 3
            left_edge = abs(
                luminance(left.pixels, right_offset)
                - luminance(left.pixels, offset)
            ) + abs(
                luminance(left.pixels, below_offset)
                - luminance(left.pixels, offset)
            )
            right_edge = abs(
                luminance(right.pixels, right_offset)
                - luminance(right.pixels, offset)
            ) + abs(
                luminance(right.pixels, below_offset)
                - luminance(right.pixels, offset)
            )
            if abs(left_edge - right_edge) >= 24:
                changed += 1
            compared += 1
    if compared == 0:
        raise forge_client.E2EError(
            "Forge Attrahite structural comparison region is empty"
        )
    return changed / compared


def gallery_lighting_metrics(image: forge_evidence.PngImage) -> GalleryLightingMetrics:
    """Measures the fixed gallery crop while excluding the bright sky and HUD."""

    left_percent, right_percent, top_percent, bottom_percent = GALLERY_ROI_PERCENT
    left_x = image.width * left_percent // 100
    right_x = image.width * right_percent // 100
    top_y = image.height * top_percent // 100
    bottom_y = image.height * bottom_percent // 100
    histogram = [0] * 256
    luminance_sum = 0
    dark_pixels = 0
    pixel_count = 0
    for y in range(top_y, bottom_y):
        for x in range(left_x, right_x):
            offset = (y * image.width + x) * 3
            luminance = (
                77 * image.pixels[offset]
                + 150 * image.pixels[offset + 1]
                + 29 * image.pixels[offset + 2]
            ) >> 8
            histogram[luminance] += 1
            luminance_sum += luminance
            dark_pixels += luminance <= GALLERY_DARK_LUMINANCE_CUTOFF
            pixel_count += 1
    if pixel_count == 0:
        raise forge_client.E2EError("Forge Attrahite gallery lighting region is empty")
    median_target = (pixel_count - 1) // 2
    cumulative = 0
    median = 0
    for luminance, count in enumerate(histogram):
        cumulative += count
        if cumulative > median_target:
            median = luminance
            break
    return GalleryLightingMetrics(
        mean_luminance=luminance_sum / pixel_count,
        median_luminance=median,
        dark_pixel_ratio=dark_pixels / pixel_count,
    )


def validate_gallery_lighting(
    images: list[forge_evidence.PngImage],
) -> None:
    metrics = [gallery_lighting_metrics(image) for image in images]
    for phase, phase_metrics in zip(PHASES, metrics, strict=True):
        if (
            phase_metrics.mean_luminance < MINIMUM_GALLERY_MEAN_LUMINANCE
            or phase_metrics.median_luminance < MINIMUM_GALLERY_MEDIAN_LUMINANCE
            or phase_metrics.dark_pixel_ratio > MAXIMUM_GALLERY_DARK_PIXEL_RATIO
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite {phase} gallery lighting is implausibly dark"
            )
    initial, reopened = metrics
    if (
        abs(initial.mean_luminance - reopened.mean_luminance)
        > MAXIMUM_REOPEN_GALLERY_MEAN_DELTA
        or abs(initial.median_luminance - reopened.median_luminance)
        > MAXIMUM_REOPEN_GALLERY_MEDIAN_DELTA
        or abs(initial.dark_pixel_ratio - reopened.dark_pixel_ratio)
        > MAXIMUM_REOPEN_GALLERY_DARK_PIXEL_RATIO_DELTA
    ):
        raise forge_client.E2EError(
            "Forge Attrahite gallery lighting changed materially across restart"
        )


def validate_screenshot_files(
    scenario_root: Path, screenshots: list[dict[str, object]]
) -> float:
    screenshot_directory = scenario_root / "screenshots"
    if not screenshot_directory.is_dir() or screenshot_directory.is_symlink():
        raise forge_client.E2EError("Forge Attrahite screenshot directory is missing or linked")
    entries = list(screenshot_directory.iterdir())
    if any(path.is_symlink() for path in entries):
        raise forge_client.E2EError(
            "Forge Attrahite screenshot inventory contains a linked payload"
        )
    if (
        {path.name for path in entries} != set(SCREENSHOT_FILES)
        or any(not path.is_file() for path in entries)
    ):
        raise forge_client.E2EError("Forge Attrahite screenshot file inventory changed")
    images: list[forge_evidence.PngImage] = []
    for screenshot in screenshots:
        path = scenario_root / str(screenshot["file"])
        if not path.is_file() or path.is_symlink():
            raise forge_client.E2EError(
                f"Forge Attrahite screenshot is missing or linked: {path}"
            )
        if (
            path.stat().st_size != screenshot["size"]
            or forge_evidence.sha256_file(path) != screenshot["sha256"]
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite screenshot differs from its report: {path}"
            )
        image = forge_evidence.decode_png(path)
        if (image.width, image.height) != EXPECTED_FRAMEBUFFER_DIMENSIONS:
            raise forge_client.E2EError(
                f"Forge Attrahite screenshot dimensions changed: {path}"
            )
        forge_evidence.assert_image_is_not_blank(image, str(path))
        if count_missing_texture_pixels(image) != 0:
            raise forge_client.E2EError(
                f"Forge Attrahite screenshot contains missing-texture magenta: {path}"
            )
        images.append(image)
    structural_ratio = structural_change_ratio(images[0], images[1])
    if structural_ratio > MAXIMUM_REOPEN_STRUCTURAL_CHANGE_RATIO:
        raise forge_client.E2EError(
            "Forge Attrahite reopened capture differs materially from its saved fixture"
        )
    ratio = forge_evidence.changed_pixel_ratio(images[0], images[1])
    if ratio > MAXIMUM_REOPEN_CHANGED_PIXEL_RATIO:
        raise forge_client.E2EError(
            "Forge Attrahite reopened capture differs materially from its saved fixture"
        )
    validate_gallery_lighting(images)
    return ratio


def validate_report_contract(
    scenario_root: Path, report: dict[str, object]
) -> tuple[float, dict[str, dict[str, object]]]:
    """Validates every scenario-specific report and screenshot field."""

    world = report.get("world")
    if (
        tuple(report) != EXPECTED_REPORT_FIELDS
        or report.get("schema") != 3
        or report.get("scenario") != SCENARIO_ID
        or report.get("profile_id") != PROFILE_ID
        or report.get("profile_manifest_size") != PROFILE_SIZE
        or report.get("profile_manifest_sha256") != PROFILE_SHA256
        or report.get("artifact_node") != "forge-1.20.1"
        or report.get("minecraft") != "1.20.1"
        or report.get("loader") != "forge"
        or report.get("loader_version") != "47.4.9"
        or report.get("java") != 17
        or report.get("lane") != "forge-1.20.1"
        or report.get("role") != "host"
        or report.get("status") != "passed"
        or report.get("passed") is not True
        or report.get("lifecycle_failure") != ""
        or type(report.get("client_ticks")) is not int
        or int(report["client_ticks"]) <= 0
        or not isinstance(world, dict)
        or tuple(world) != tuple(EXPECTED_WORLD)
        or world != EXPECTED_WORLD
        or type(world.get("seed")) is not int
        or type(world.get("integrated")) is not bool
        or type(world.get("reopened")) is not bool
        or report.get("ready_resources") != list(EXPECTED_READY_RESOURCES)
    ):
        raise forge_client.E2EError("Forge Attrahite report lifecycle contract is invalid")
    validate_attrahite(report)
    artifacts = validate_artifact_inventory(report)
    screenshots = validate_screenshot_inventory(report)
    validate_assertions(report, screenshots)
    ratio = validate_screenshot_files(scenario_root, screenshots)
    return ratio, artifacts


def validate_world_save(
    configuration: forge_client.ResolvedConfiguration, runtime: Path
) -> None:
    world_root = (
        forge_client.game_directory(configuration, runtime)
        / "saves"
        / str(EXPECTED_WORLD["save_directory"])
    )
    if not world_root.is_dir() or world_root.is_symlink():
        raise forge_client.E2EError(f"Forge Attrahite saved world is missing or linked: {world_root}")
    for relative_path in ("level.dat", "session.lock", "region/r.0.0.mca"):
        path = world_root / relative_path
        if not path.is_file() or path.is_symlink() or path.stat().st_size <= 0:
            raise forge_client.E2EError(
                f"Forge Attrahite saved-world proof is missing or linked: {path}"
            )


def validate_game_lifecycle(
    configuration: forge_client.ResolvedConfiguration,
    runtime: Path,
) -> None:
    game = forge_client.game_directory(configuration, runtime)
    crash_reports = game / "crash-reports"
    if not crash_reports.is_dir() or crash_reports.is_symlink() or any(
        crash_reports.iterdir()
    ):
        raise forge_client.E2EError("Forge Attrahite runtime contains crash reports")
    latest_log = game / "logs" / "latest.log"
    if (
        not latest_log.is_file()
        or latest_log.is_symlink()
        or latest_log.stat().st_size <= 0
        or latest_log.stat().st_size > forge_client.MAXIMUM_PROCESS_LOG_SIZE
    ):
        raise forge_client.E2EError("Forge Attrahite game log is missing, linked, or oversized")
    content = latest_log.read_text(encoding="utf-8", errors="replace")
    fatal_marker = next(
        (marker for marker in FATAL_GAME_LOG_MARKERS if marker in content),
        None,
    )
    if fatal_marker is not None:
        raise forge_client.E2EError(
            f"Forge Attrahite game log contains fatal marker: {fatal_marker}"
        )
    if "Stopping!" not in content:
        raise forge_client.E2EError("Forge Attrahite client did not stop normally")
    if content.count("Attrahite E2E evidence is complete:") != 1:
        raise forge_client.E2EError(
            "Forge Attrahite game log lacks one exact publication marker"
        )
    validate_world_save(configuration, runtime)


def validate_live_artifacts(
    configuration: forge_client.ResolvedConfiguration,
    runtime: Path,
    report_artifacts: dict[str, dict[str, object]],
) -> tuple[str, str]:
    """Binds report provenance to the exact staged Forge JARs and lock."""

    forge_client.verify_locked_artifacts(
        configuration,
        runtime,
        verify_source=False,
    )
    lock = forge_client.load_artifact_lock(configuration, runtime)
    if (
        not isinstance(lock, dict)
        or lock.get("schema") != 1
        or lock.get("profile_id") != PROFILE_ID
        or lock.get("artifact_node") != "forge-1.20.1"
    ):
        raise forge_client.E2EError(
            "Forge Attrahite evidence has no exact artifact lock"
        )
    locked_artifacts = lock.get("artifacts")
    if not isinstance(locked_artifacts, dict) or tuple(locked_artifacts) != (
        "production",
        "harness",
    ):
        raise forge_client.E2EError(
            "Forge Attrahite artifact lock inventory changed"
        )
    for role, mod_id, file_name in EXPECTED_ARTIFACTS:
        locked = locked_artifacts.get(role)
        reported = report_artifacts[role]
        if (
            not isinstance(locked, dict)
            or locked.get("mod_id") != mod_id
            or locked.get("target_file") != file_name
            or locked.get("size") != reported["size"]
            or locked.get("sha256") != reported["sha256"]
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite {role} artifact differs from its lock"
            )
    return (
        str(report_artifacts["production"]["sha256"]),
        str(report_artifacts["harness"]["sha256"]),
    )


def validate_live_evidence(
    configuration: forge_client.ResolvedConfiguration | None = None,
    runtime: Path | None = None,
) -> AttrahiteEvidenceSummary:
    """Validates the exact live v17 capture without launching or changing it."""

    resolved = configuration or forge_client.load_configuration()
    validate_active_profile(resolved)
    target_runtime = runtime or forge_client.runtime_root(resolved)
    if forge_client.resolve_scenario_id(resolved, SCENARIO_ID) != SCENARIO_ID:
        raise forge_client.E2EError("Forge Attrahite scenario selection changed")
    forge_client.verify_evidence_layout(resolved, target_runtime)
    scenario_root = forge_client.evidence_root(resolved, target_runtime) / SCENARIO_ID
    reports = scenario_root / "reports"
    if (
        not reports.is_dir()
        or reports.is_symlink()
        or {path.name for path in reports.iterdir()} != {"report.json", "done.marker"}
    ):
        raise forge_client.E2EError("Forge Attrahite report directory inventory changed")
    report_path = reports / "report.json"
    done_path = reports / "done.marker"
    if report_path.is_symlink() or done_path.is_symlink():
        raise forge_client.E2EError("Forge Attrahite report or marker is linked")
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError("Forge Attrahite completion marker is invalid")
    report = forge_evidence.require_json_object(report_path, "Forge Attrahite scenario report")
    ratio, artifacts = validate_report_contract(scenario_root, report)
    assertion_count = len(EXPECTED_ASSERTION_NAMES)
    production_digest, harness_digest = validate_live_artifacts(
        resolved,
        target_runtime,
        artifacts,
    )
    if (
        assertion_count != len(EXPECTED_ASSERTION_NAMES)
        or production_digest != artifacts["production"]["sha256"]
        or harness_digest != artifacts["harness"]["sha256"]
        or harness_digest != HARNESS_SHA256
    ):
        raise forge_client.E2EError(
            "Forge Attrahite strict evidence differs from artifact validation"
        )
    screenshot_paths = [
        scenario_root / str(screenshot["file"])
        for screenshot in report["screenshots"]
    ]
    if any(
        done_path.stat().st_mtime_ns < path.stat().st_mtime_ns
        for path in (report_path, *screenshot_paths)
    ):
        raise forge_client.E2EError("Forge Attrahite completion predates its evidence payload")
    scenario_size = sum(
        path.stat().st_size
        for path in scenario_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if scenario_size > forge_evidence.MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise forge_client.E2EError("Forge Attrahite evidence exceeds the size bound")
    validate_game_lifecycle(resolved, target_runtime)
    return AttrahiteEvidenceSummary(
        profile_id=PROFILE_ID,
        assertion_count=assertion_count,
        screenshot_count=len(EXPECTED_SCREENSHOTS),
        reopen_changed_pixel_ratio=ratio,
        production_sha256=production_digest,
        harness_sha256=harness_digest,
    )


def validate_archive_inventory(archive_root: Path, include_manifest: bool = True) -> None:
    if not archive_root.is_dir() or archive_root.is_symlink():
        raise forge_client.E2EError(
            f"Forge Attrahite evidence archive is missing or linked: {archive_root}"
        )
    if archive_root.name != ARCHIVE_DIRECTORY_NAME:
        raise forge_client.E2EError("Forge Attrahite archive directory does not identify v17")
    files: set[str] = set()
    directories: set[str] = set()
    for path in archive_root.rglob("*"):
        relative_path = path.relative_to(archive_root).as_posix()
        if path.is_symlink():
            raise forge_client.E2EError(
                f"Forge Attrahite archive contains a linked entry: {relative_path}"
            )
        if path.is_file():
            files.add(relative_path)
        elif path.is_dir():
            directories.add(relative_path)
        else:
            raise forge_client.E2EError(
                f"Forge Attrahite archive contains a special entry: {relative_path}"
            )
    expected_files = set(ARCHIVE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    if files != expected_files or directories != ARCHIVE_DIRECTORIES:
        raise forge_client.E2EError("Forge Attrahite archive inventory changed")
    archive_size = sum(
        path.stat().st_size for path in archive_root.rglob("*") if path.is_file()
    )
    if archive_size > forge_evidence.MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise forge_client.E2EError("Forge Attrahite archive exceeds the size bound")


def validate_no_competing_archives(archive_root: Path) -> None:
    """Requires v17 to be the sole versioned Forge Attrahite archive."""

    if not archive_root.parent.is_dir() or archive_root.parent.is_symlink():
        raise forge_client.E2EError("Forge Attrahite archive parent is missing or linked")
    candidates = sorted(
        (
            entry
            for entry in archive_root.parent.iterdir()
            if ARCHIVE_DIRECTORY_PATTERN.fullmatch(entry.name)
        ),
        key=lambda entry: entry.name,
    )
    if candidates != [archive_root] or not archive_root.is_dir() or archive_root.is_symlink():
        raise forge_client.E2EError("The exact sole Forge Attrahite v17 archive is required")


def expected_repository_archive_root(
    configuration: forge_client.ResolvedConfiguration,
) -> Path:
    return forge_client.safe_repository_path(
        configuration.repository_root,
        f"docs/evidence/forge-1.20.1/{ARCHIVE_DIRECTORY_NAME}",
        "Forge Attrahite evidence archive",
    )


def validate_capture_artifact_lock(
    configuration: forge_client.ResolvedConfiguration,
    capture_runtime: Path,
    report_artifacts: dict[str, dict[str, object]],
) -> Path:
    lock = forge_client.load_artifact_lock(configuration, capture_runtime)
    if (
        not isinstance(lock, dict)
        or lock.get("schema") != 1
        or lock.get("profile_id") != PROFILE_ID
        or lock.get("artifact_node") != "forge-1.20.1"
    ):
        raise forge_client.E2EError("Forge Attrahite capture has no exact artifact lock")
    locked_artifacts = lock.get("artifacts")
    if not isinstance(locked_artifacts, dict) or tuple(locked_artifacts) != (
        "production",
        "harness",
    ):
        raise forge_client.E2EError("Forge Attrahite capture artifact lock inventory changed")
    for role, mod_id, file_name in EXPECTED_ARTIFACTS:
        locked = locked_artifacts.get(role)
        reported = report_artifacts[role]
        if (
            not isinstance(locked, dict)
            or locked.get("mod_id") != mod_id
            or locked.get("target_file") != file_name
            or locked.get("size") != reported["size"]
            or locked.get("sha256") != reported["sha256"]
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite capture {role} lock differs from report"
            )
    lock_path = forge_client.artifact_lock_path(configuration, capture_runtime)
    if not lock_path.is_file() or lock_path.is_symlink():
        raise forge_client.E2EError("Forge Attrahite artifact lock is missing or linked")
    return lock_path


def build_archive_manifest(
    configuration: forge_client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> dict[str, object]:
    """Builds a seal only from the exact live v17 capture and copied payload."""

    validate_active_profile(configuration)
    validate_no_competing_archives(archive_root)
    expected_profile = configuration.repository_root / ACTIVE_PROFILE_RELATIVE_PATH
    if (
        profile_manifest_path.resolve() != expected_profile.resolve()
        or not profile_manifest_path.is_file()
        or profile_manifest_path.is_symlink()
        or profile_manifest_path.stat().st_size != PROFILE_SIZE
        or forge_client.sha256_file(profile_manifest_path) != PROFILE_SHA256
    ):
        raise forge_client.E2EError("Forge Attrahite sealing requires the exact v17 profile")
    validate_archive_inventory(archive_root, include_manifest=False)
    if archive_root.resolve() != expected_repository_archive_root(configuration).resolve():
        raise forge_client.E2EError(
            "Forge Attrahite sealing requires the repository archive destination"
        )
    expected_runtime = forge_client.runtime_root(configuration)
    forge_client.ensure_owned_state_roots()
    if (
        capture_runtime.resolve() != expected_runtime.resolve()
        or not capture_runtime.is_dir()
        or capture_runtime.is_symlink()
        or capture_runtime.name != PROFILE_ID
    ):
        raise forge_client.E2EError("Forge Attrahite sealing requires the exact owned v17 runtime")
    forge_client.verify_runtime(configuration, capture_runtime, artifact_policy="optional")
    live_summary = validate_live_evidence(configuration, capture_runtime)
    archive_report = forge_evidence.require_json_object(
        archive_root / "reports/report.json", "Forge Attrahite archived report"
    )
    ratio, report_artifacts = validate_report_contract(archive_root, archive_report)
    if (
        live_summary.assertion_count != len(EXPECTED_ASSERTION_NAMES)
        or live_summary.screenshot_count != len(EXPECTED_SCREENSHOTS)
        or abs(live_summary.reopen_changed_pixel_ratio - ratio) > 1e-12
        or live_summary.production_sha256 != report_artifacts["production"]["sha256"]
        or live_summary.harness_sha256 != report_artifacts["harness"]["sha256"]
    ):
        raise forge_client.E2EError("Forge Attrahite archive differs from live validation")
    capture_root = forge_client.evidence_root(configuration, capture_runtime) / SCENARIO_ID
    capture_mtime_ns: dict[str, int] = {}
    files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        archive_path = archive_root / relative_path
        capture_path = capture_root / relative_path
        if not capture_path.is_file() or capture_path.is_symlink():
            raise forge_client.E2EError(
                f"Forge Attrahite capture payload is missing or linked: {capture_path}"
            )
        if (
            archive_path.stat().st_size != capture_path.stat().st_size
            or forge_evidence.sha256_file(archive_path) != forge_evidence.sha256_file(capture_path)
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite archive payload differs from capture: {relative_path}"
            )
        capture_mtime_ns[relative_path] = capture_path.stat().st_mtime_ns
        files[relative_path] = {
            "size": archive_path.stat().st_size,
            "sha256": forge_evidence.sha256_file(archive_path),
        }
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[path]
        for path in ARCHIVE_PAYLOAD_PATHS
        if path != "reports/done.marker"
    ):
        raise forge_client.E2EError("Forge Attrahite capture completion predates copied evidence")
    artifact_lock_path = validate_capture_artifact_lock(
        configuration, capture_runtime, report_artifacts
    )
    return {
        "schema": 1,
        "kind": ARCHIVE_KIND,
        "verification_scope": ARCHIVE_VERIFICATION_SCOPE,
        "scenario": SCENARIO_ID,
        "profile": {
            "id": PROFILE_ID,
            "manifest_path": ACTIVE_PROFILE_RELATIVE_PATH,
            "manifest_size": PROFILE_SIZE,
            "manifest_sha256": PROFILE_SHA256,
        },
        "runtime": {
            "artifact_node": "forge-1.20.1",
            "minecraft": "1.20.1",
            "loader": "forge",
            "loader_version": "47.4.9",
            "java": 17,
            "capture_kind": "composed-minecraft-framebuffer",
            "framebuffer_width": 1920,
            "framebuffer_height": 1080,
        },
        "publication": {
            **ARCHIVE_PUBLICATION_ATTESTATION,
            "capture_mtime_ns": capture_mtime_ns,
        },
        "capture_metadata": {
            "path": ARCHIVE_CAPTURE_METADATA_PATH,
            "size": artifact_lock_path.stat().st_size,
            "sha256": forge_evidence.sha256_file(artifact_lock_path),
        },
        "assertion_count": len(EXPECTED_ASSERTION_NAMES),
        "screenshot_count": len(EXPECTED_SCREENSHOTS),
        "artifacts": report_artifacts,
        "files": files,
    }


def write_archive_manifest(
    configuration: forge_client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> Path:
    """Atomically creates and immediately validates the one-shot archive seal."""

    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if manifest_path.exists() or manifest_path.is_symlink():
        raise forge_client.E2EError(f"Forge Attrahite archive manifest exists: {manifest_path}")
    manifest = build_archive_manifest(
        configuration, profile_manifest_path, capture_runtime, archive_root
    )
    temporary_path: Path | None = None
    try:
        descriptor, raw_path = tempfile.mkstemp(
            prefix=".attrahite-archive-manifest.", dir=archive_root
        )
        temporary_path = Path(raw_path)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(manifest, handle, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        try:
            os.link(temporary_path, manifest_path, follow_symlinks=False)
        except FileExistsError as exception:
            raise forge_client.E2EError(
                f"Forge Attrahite archive manifest exists: {manifest_path}"
            ) from exception
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
    try:
        validate_archived_evidence(archive_root)
    except (forge_client.E2EError, OSError, json.JSONDecodeError):
        manifest_path.unlink(missing_ok=True)
        raise
    return manifest_path


def validate_archive_manifest(
    archive_root: Path, manifest: dict[str, object]
) -> dict[str, dict[str, object]]:
    expected_fields = {
        "schema",
        "kind",
        "verification_scope",
        "scenario",
        "profile",
        "runtime",
        "publication",
        "capture_metadata",
        "assertion_count",
        "screenshot_count",
        "artifacts",
        "files",
    }
    if set(manifest) != expected_fields:
        raise forge_client.E2EError("Forge Attrahite archive manifest fields changed")
    if (
        manifest.get("schema") != 1
        or manifest.get("kind") != ARCHIVE_KIND
        or manifest.get("verification_scope") != ARCHIVE_VERIFICATION_SCOPE
        or manifest.get("scenario") != SCENARIO_ID
        or manifest.get("assertion_count") != len(EXPECTED_ASSERTION_NAMES)
        or manifest.get("screenshot_count") != len(EXPECTED_SCREENSHOTS)
    ):
        raise forge_client.E2EError("Forge Attrahite archive identity is invalid")
    if manifest.get("profile") != {
        "id": PROFILE_ID,
        "manifest_path": ACTIVE_PROFILE_RELATIVE_PATH,
        "manifest_size": PROFILE_SIZE,
        "manifest_sha256": PROFILE_SHA256,
    }:
        raise forge_client.E2EError("Forge Attrahite archive profile is invalid")
    if manifest.get("runtime") != {
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "capture_kind": "composed-minecraft-framebuffer",
        "framebuffer_width": 1920,
        "framebuffer_height": 1080,
    }:
        raise forge_client.E2EError("Forge Attrahite archive runtime is invalid")
    publication = manifest.get("publication")
    if not isinstance(publication, dict) or set(publication) != {
        *ARCHIVE_PUBLICATION_ATTESTATION,
        "capture_mtime_ns",
    }:
        raise forge_client.E2EError("Forge Attrahite archive publication is malformed")
    if any(
        publication.get(key) != value
        for key, value in ARCHIVE_PUBLICATION_ATTESTATION.items()
    ):
        raise forge_client.E2EError("Forge Attrahite archive publication is invalid")
    capture_mtime_ns = publication.get("capture_mtime_ns")
    if (
        not isinstance(capture_mtime_ns, dict)
        or set(capture_mtime_ns) != set(ARCHIVE_PAYLOAD_PATHS)
        or any(type(value) is not int or value <= 0 for value in capture_mtime_ns.values())
    ):
        raise forge_client.E2EError("Forge Attrahite archive timestamps are invalid")
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[path]
        for path in ARCHIVE_PAYLOAD_PATHS
        if path != "reports/done.marker"
    ):
        raise forge_client.E2EError("Forge Attrahite archive completion predates its payload")
    capture_metadata = manifest.get("capture_metadata")
    if (
        not isinstance(capture_metadata, dict)
        or set(capture_metadata) != {"path", "size", "sha256"}
        or capture_metadata.get("path") != ARCHIVE_CAPTURE_METADATA_PATH
        or type(capture_metadata.get("size")) is not int
        or int(capture_metadata["size"]) <= 0
    ):
        raise forge_client.E2EError("Forge Attrahite archive capture metadata is invalid")
    forge_client.validate_hex_digest(
        capture_metadata.get("sha256"), "Forge Attrahite capture metadata"
    )
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or tuple(artifacts) != ("production", "harness"):
        raise forge_client.E2EError("Forge Attrahite archive artifacts changed")
    validated_artifacts: dict[str, dict[str, object]] = {}
    for role, mod_id, file_name in EXPECTED_ARTIFACTS:
        artifact = artifacts.get(role)
        if (
            not isinstance(artifact, dict)
            or set(artifact) != {"mod_id", "file_name", "size", "sha256"}
            or artifact.get("mod_id") != mod_id
            or artifact.get("file_name") != file_name
            or type(artifact.get("size")) is not int
            or int(artifact["size"]) <= 0
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite archived {role} artifact is invalid"
            )
        digest = forge_client.validate_hex_digest(
            artifact.get("sha256"), f"Forge Attrahite archived {role} artifact"
        )
        if role == "harness" and (
            artifact["size"] != HARNESS_SIZE or digest != HARNESS_SHA256
        ):
            raise forge_client.E2EError("Forge Attrahite archived harness pin changed")
        validated_artifacts[role] = artifact
    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(ARCHIVE_PAYLOAD_PATHS):
        raise forge_client.E2EError("Forge Attrahite archive payloads changed")
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if (
            not isinstance(record, dict)
            or set(record) != {"size", "sha256"}
            or type(record.get("size")) is not int
            or int(record["size"]) <= 0
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite payload record is invalid: {relative_path}"
            )
        forge_client.validate_hex_digest(
            record.get("sha256"), f"Forge Attrahite payload {relative_path}"
        )
        path = archive_root / relative_path
        if (
            path.stat().st_size != record["size"]
            or forge_evidence.sha256_file(path) != record["sha256"]
        ):
            raise forge_client.E2EError(
                f"Forge Attrahite payload differs from manifest: {relative_path}"
            )
    return validated_artifacts


def validate_archived_evidence(archive_root: Path) -> AttrahiteEvidenceSummary:
    """Validates a self-contained v17 archive without consulting live state."""

    validate_no_competing_archives(archive_root)
    validate_archive_inventory(archive_root)
    manifest = forge_evidence.require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME, "Forge Attrahite archive manifest"
    )
    manifest_artifacts = validate_archive_manifest(archive_root, manifest)
    done_path = archive_root / "reports/done.marker"
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError("Forge Attrahite completion marker is invalid")
    report = forge_evidence.require_json_object(
        archive_root / "reports/report.json", "Forge Attrahite archived report"
    )
    ratio, report_artifacts = validate_report_contract(archive_root, report)
    if report_artifacts != manifest_artifacts:
        raise forge_client.E2EError("Forge Attrahite report artifacts differ from manifest")
    return AttrahiteEvidenceSummary(
        profile_id=PROFILE_ID,
        assertion_count=len(EXPECTED_ASSERTION_NAMES),
        screenshot_count=len(EXPECTED_SCREENSHOTS),
        reopen_changed_pixel_ratio=ratio,
        production_sha256=str(report_artifacts["production"]["sha256"]),
        harness_sha256=str(report_artifacts["harness"]["sha256"]),
    )


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate exact Forge 1.20.1 Attrahite evidence."
    )
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument(
        "--live", action="store_true", help="validate the owned v17 capture runtime"
    )
    operation.add_argument(
        "--archive", type=Path, help="validate one frozen v17 archive"
    )
    operation.add_argument(
        "--create-archive-manifest",
        type=Path,
        metavar="ARCHIVE",
        help="seal copied v17 payload from one explicit owned runtime",
    )
    parser.add_argument(
        "--capture-runtime", type=Path, help="owned runtime used only while sealing"
    )
    parser.add_argument(
        "--profile-manifest", type=Path, help="v17 manifest used only while sealing"
    )
    arguments = parser.parse_args()
    sealing = arguments.create_archive_manifest is not None
    auxiliaries = (arguments.capture_runtime, arguments.profile_manifest)
    if sealing and any(value is None for value in auxiliaries):
        parser.error(
            "--create-archive-manifest requires --capture-runtime and --profile-manifest"
        )
    if not sealing and any(value is not None for value in auxiliaries):
        parser.error(
            "--capture-runtime and --profile-manifest require --create-archive-manifest"
        )
    return arguments


def main() -> int:
    arguments = parse_arguments()
    try:
        if arguments.create_archive_manifest is not None:
            configuration = forge_client.load_configuration(arguments.profile_manifest)
            manifest_path = write_archive_manifest(
                configuration,
                arguments.profile_manifest,
                arguments.capture_runtime,
                arguments.create_archive_manifest,
            )
            summary = validate_archived_evidence(arguments.create_archive_manifest)
            print(f"Created and validated archive manifest: {manifest_path}")
        elif arguments.archive is not None:
            summary = validate_archived_evidence(arguments.archive)
        else:
            summary = validate_live_evidence()
    except (forge_client.E2EError, OSError, json.JSONDecodeError) as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2
    print(
        f"Validated Forge {SCENARIO_ID} ({summary.profile_id}): "
        f"{summary.assertion_count} assertions, "
        f"{summary.screenshot_count} screenshots, "
        f"reopen delta {summary.reopen_changed_pixel_ratio:.6f}"
    )
    print(f"Production SHA-256: {summary.production_sha256}")
    print(f"Harness SHA-256: {summary.harness_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
