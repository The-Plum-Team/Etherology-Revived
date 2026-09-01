#!/usr/bin/env python3
"""Validate live or archived Fabric Attrahite block evidence."""

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

import client
import evidence


SCENARIO_ID = "attrahite-block-registry"
PROFILE_ID = "etherology-e2e-fabric-1.20.1-v29"
ACTIVE_PROFILE_RELATIVE_PATH = "scripts/e2e/fabric-1.20.1-profile.json"
SNAPSHOT_PROFILE_RELATIVE_PATH = "scripts/e2e/fabric-1.20.1-profile-v29.json"
PROFILE_SIZE = 7051
PROFILE_SHA256 = "71c6296a6959689dfcce50d3f3965107bd8e6cf9a447c3de9d06f2c7da80de12"
HARNESS_SIZE = 296850
HARNESS_SHA256 = "1082487f1a29935b70524d47d6c5e3ac28d78070ea499eb7062e42f2c1fc77da"
ARCHIVE_DIRECTORY_NAME = "attrahite-block-registry-v29"
ARCHIVE_DIRECTORY_PATTERN = re.compile(r"attrahite-block-registry-v[1-9][0-9]*")
ARCHIVE_MANIFEST_NAME = evidence.ARCHIVE_MANIFEST_NAME
ARCHIVE_KIND = evidence.ARCHIVE_KIND
ARCHIVE_VERIFICATION_SCOPE = evidence.ARCHIVE_VERIFICATION_SCOPE
ARCHIVE_CAPTURE_METADATA_PATH = evidence.ARCHIVE_CAPTURE_METADATA_PATH
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
REQUIRED_LIGHTING_READY_SERVER_TICKS = 20
REQUIRED_LIGHTING_READY_CLIENT_TICKS = 20
MAXIMUM_REOPEN_CHANGED_PIXEL_RATIO = 0.35
MINIMUM_MEAN_LUMINANCE = 110.0
MAXIMUM_DARK_PIXEL_RATIO = 0.20
DARK_PIXEL_LUMINANCE_THRESHOLD = 48.0
EXPECTED_SCENARIOS = (
    "phase0-smoke",
    "progression-oculus",
    "seals-aspects",
    "golden-forest",
    "alchemy",
    "ether-network",
    "staff-lenses",
    "spiritual-energy",
    "armillary",
    "storage-utilities",
    "combat-equipment",
    "persistence",
    "multiplayer-sync",
    "metal-block-registry",
    "forest-lantern",
    SCENARIO_ID,
)
EXPECTED_ARTIFACTS = (
    ("production", "etherology", "etherology-under-test.jar"),
    ("harness", "etherology_e2e_harness", "etherology-e2e-harness.jar"),
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
        "class": "net.minecraft.class_2248",
        "default_state": "etherology:attrahite",
        "state_count": 1,
        "needs_stone_tool": True,
        "slab": False,
        "stairs": False,
    },
    {
        "id": "etherology:attrahite_bricks",
        "x": -1,
        "class": "net.minecraft.class_2248",
        "default_state": "etherology:attrahite_bricks",
        "state_count": 1,
        "needs_stone_tool": False,
        "slab": False,
        "stairs": False,
    },
    {
        "id": "etherology:attrahite_brick_slab",
        "x": 1,
        "class": "net.minecraft.class_2482",
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
        "class": "net.minecraft.class_2510",
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
BLOCK_ITEM_CLASS = "net.minecraft.class_1747"
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
LIGHT_SAMPLE_POSITIONS = (
    "0,121,-8",
    "0,121,0",
    "-3,123,1",
    "-1,123,1",
    "1,123,1",
    "3,123,1",
)
EXPECTED_SKY_LIGHT = dict.fromkeys(LIGHT_SAMPLE_POSITIONS, 15)
EXPECTED_BLOCK_LIGHT = dict(
    zip(LIGHT_SAMPLE_POSITIONS, (14, 14, 10, 10, 10, 8), strict=True)
)


def light_sample_description(samples: dict[str, int]) -> str:
    entries = (f"{position}={level}" for position, level in samples.items())
    return "[" + ",".join(entries) + "]"


EXPECTED_LIGHTING_DESCRIPTION = (
    f"stableServerTicks={REQUIRED_LIGHTING_READY_SERVER_TICKS};"
    f"stableClientTicks={REQUIRED_LIGHTING_READY_CLIENT_TICKS};"
    f"serverSky={light_sample_description(EXPECTED_SKY_LIGHT)};"
    f"serverBlock={light_sample_description(EXPECTED_BLOCK_LIGHT)};"
    f"clientSky={light_sample_description(EXPECTED_SKY_LIGHT)};"
    f"clientBlock={light_sample_description(EXPECTED_BLOCK_LIGHT)}"
)
EXPECTED_REPORT_FIELDS = (
    "schema",
    "scenario",
    "artifact_node",
    "minecraft",
    "loader",
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
    "lighting",
    "latest_client_fixtures",
)
EXPECTED_LIGHTING_FIELDS = (
    "server_stable_ticks",
    "client_stable_ticks",
    "server_observed",
    "client_observed",
    "server_pending",
    "client_pending",
    "server_sky",
    "server_block",
    "client_sky",
    "client_block",
)


def create_assertion_names() -> tuple[str, ...]:
    names = ["fabric_mod_loaded:etherology"]
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
    """Reports the evidence inventory accepted by the v29 verifier."""

    profile_id: str
    assertion_count: int
    screenshot_count: int
    reopen_changed_pixel_ratio: float
    production_sha256: str
    harness_sha256: str


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
            or evidence.sha256_file(path) != expected_sha256
        ):
            raise client.E2EError(
                f"Fabric Attrahite canonical resource bytes changed: {path}"
            )


def validate_active_profile(configuration: client.ResolvedConfiguration) -> None:
    """Requires the immutable v29 profile and exact runtime contract."""

    active_profile = configuration.repository_root / ACTIVE_PROFILE_RELATIVE_PATH
    snapshot_profile = configuration.repository_root / SNAPSHOT_PROFILE_RELATIVE_PATH
    for path in (active_profile, snapshot_profile):
        if not path.is_file() or path.is_symlink():
            raise client.E2EError(f"Fabric Attrahite profile is missing or linked: {path}")
        if path.stat().st_size != PROFILE_SIZE or client.sha256_file(path) != PROFILE_SHA256:
            raise client.E2EError(f"Fabric Attrahite profile bytes changed: {path}")
    if active_profile.read_bytes() != snapshot_profile.read_bytes():
        raise client.E2EError("The active Fabric profile differs from its v29 snapshot")

    profile = client.profile_spec(configuration)
    capture = client.require_object(client.evidence_spec(configuration), "capture")
    if (
        profile.get("id") != PROFILE_ID
        or profile.get("runtime_directory") != PROFILE_ID
        or tuple(client.scenario_ids(configuration)) != EXPECTED_SCENARIOS
        or configuration.artifact_lane.get("artifact_node") != "fabric-1.20.1"
        or configuration.runtime_lane.get("runtime_version") != "1.20.1"
        or configuration.runtime_lane.get("loader") != "fabric"
        or configuration.runtime_lane.get("loader_version") != "0.17.3"
        or configuration.runtime_lane.get("java") != 17
        or capture.get("kind") != "composed-minecraft-framebuffer"
        or (capture.get("width"), capture.get("height"))
        != EXPECTED_FRAMEBUFFER_DIMENSIONS
    ):
        raise client.E2EError("The Fabric Attrahite v29 profile contract changed")
    for role, mod_id, file_name in EXPECTED_ARTIFACTS:
        artifact = client.artifact_spec(configuration, role)
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            raise client.E2EError(
                f"The Fabric Attrahite profile has the wrong {role} artifact identity"
            )
    validate_canonical_resources(configuration.repository_root)


def validate_artifact_inventory(
    report: dict[str, object],
) -> dict[str, dict[str, object]]:
    artifacts = report.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) != len(EXPECTED_ARTIFACTS):
        raise client.E2EError("Fabric Attrahite artifact inventory changed")
    validated: dict[str, dict[str, object]] = {}
    for artifact, (role, mod_id, file_name) in zip(
        artifacts, EXPECTED_ARTIFACTS, strict=True
    ):
        if not isinstance(artifact, dict) or tuple(artifact) != (
            "mod_id",
            "origin_kind",
            "file_name",
            "size",
            "sha256",
        ):
            raise client.E2EError(
                f"Fabric Attrahite {role} artifact provenance is malformed"
            )
        if (
            artifact.get("mod_id") != mod_id
            or artifact.get("origin_kind") != "PATH"
            or artifact.get("file_name") != file_name
            or type(artifact.get("size")) is not int
            or int(artifact["size"]) <= 0
        ):
            raise client.E2EError(
                f"Fabric Attrahite {role} artifact identity is invalid"
            )
        digest = client.validate_hex_digest(
            artifact.get("sha256"), f"Fabric Attrahite {role} artifact"
        )
        if role == "harness" and (
            artifact["size"] != HARNESS_SIZE or digest != HARNESS_SHA256
        ):
            raise client.E2EError(
                "Fabric Attrahite harness artifact differs from its packaged pin"
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
        raise client.E2EError("Fabric Attrahite mechanics field inventory changed")
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
        raise client.E2EError("Fabric Attrahite mechanics contract changed")
    validate_lighting_diagnostics(attrahite.get("lighting"))
    validate_latest_client_fixtures(attrahite.get("latest_client_fixtures"))


def validate_latest_client_fixtures(fixtures: object) -> None:
    if (
        not isinstance(fixtures, dict)
        or tuple(fixtures) != PHASES
        or fixtures != dict.fromkeys(PHASES, EXPECTED_SNAPSHOT)
    ):
        raise client.E2EError("Fabric Attrahite latest client fixtures changed")


def validate_lighting_diagnostics(lighting: object) -> None:
    if not isinstance(lighting, dict) or tuple(lighting) != PHASES:
        raise client.E2EError("Fabric Attrahite lighting phase inventory changed")
    for phase in PHASES:
        diagnostic = lighting.get(phase)
        if not isinstance(diagnostic, dict) or tuple(diagnostic) != EXPECTED_LIGHTING_FIELDS:
            raise client.E2EError(
                f"Fabric Attrahite {phase} lighting fields changed"
            )
        if (
            type(diagnostic.get("server_stable_ticks")) is not int
            or diagnostic.get("server_stable_ticks")
            != REQUIRED_LIGHTING_READY_SERVER_TICKS
            or type(diagnostic.get("client_stable_ticks")) is not int
            or diagnostic.get("client_stable_ticks")
            != REQUIRED_LIGHTING_READY_CLIENT_TICKS
            or diagnostic.get("server_observed") is not True
            or diagnostic.get("client_observed") is not True
            or type(diagnostic.get("server_pending")) is not bool
            or type(diagnostic.get("client_pending")) is not bool
            or not exact_light_map(diagnostic.get("server_sky"), EXPECTED_SKY_LIGHT)
            or not exact_light_map(
                diagnostic.get("server_block"), EXPECTED_BLOCK_LIGHT
            )
            or not exact_light_map(diagnostic.get("client_sky"), EXPECTED_SKY_LIGHT)
            or not exact_light_map(
                diagnostic.get("client_block"), EXPECTED_BLOCK_LIGHT
            )
        ):
            raise client.E2EError(
                f"Fabric Attrahite {phase} lighting diagnostics changed"
            )


def exact_light_map(value: object, expected: dict[str, int]) -> bool:
    return (
        isinstance(value, dict)
        and tuple(value) == tuple(expected)
        and value == expected
        and all(type(level) is int for level in value.values())
    )


def expected_assertion_evidence(
    screenshots: list[dict[str, object]],
) -> dict[str, tuple[str, str]]:
    expected: dict[str, tuple[str, str]] = {
        "fabric_mod_loaded:etherology": ("loaded", "loaded"),
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
                    EXPECTED_LIGHTING_DESCRIPTION,
                    EXPECTED_LIGHTING_DESCRIPTION,
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


def validate_assertions(
    report: dict[str, object], screenshots: list[dict[str, object]]
) -> None:
    assertions = report.get("assertions")
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise client.E2EError("Fabric Attrahite assertion inventory changed")
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
            raise client.E2EError("Fabric Attrahite assertion fields changed")
        if assertion.get("name") != expected_name or assertion.get("passed") is not True:
            raise client.E2EError("Fabric Attrahite assertion inventory changed")
        if expected_name.startswith("default_state_network_id:"):
            actual = assertion.get("actual")
            if (
                assertion.get("expected") != "non-negative"
                or not isinstance(actual, str)
                or re.fullmatch(r"0|[1-9][0-9]*", actual) is None
            ):
                raise client.E2EError("Fabric Attrahite network-id evidence changed")
            network_ids.append(int(actual))
            continue
        pair = expected_evidence.get(expected_name)
        if pair is None or (
            assertion.get("expected"),
            assertion.get("actual"),
        ) != pair:
            raise client.E2EError(
                f"Fabric Attrahite assertion evidence changed: {expected_name}"
            )
    if len(network_ids) != len(BLOCK_SPECS) or len(set(network_ids)) != len(network_ids):
        raise client.E2EError("Fabric Attrahite default-state network ids are not unique")


def validate_screenshot_inventory(
    report: dict[str, object],
) -> list[dict[str, object]]:
    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or len(screenshots) != len(
        EXPECTED_SCREENSHOTS
    ):
        raise client.E2EError("Fabric Attrahite screenshot inventory changed")
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
            raise client.E2EError("Fabric Attrahite screenshot provenance is malformed")
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
            raise client.E2EError("Fabric Attrahite screenshot contract is invalid")
        client.validate_hex_digest(
            screenshot.get("sha256"), f"Fabric Attrahite screenshot {expected_file}"
        )
        validated.append(screenshot)
    return validated


def count_missing_texture_pixels(image: evidence.PngImage) -> int:
    count = 0
    for offset in range(0, len(image.pixels), 3):
        red, green, blue = image.pixels[offset : offset + 3]
        if red >= 180 and blue >= 180 and green <= 80:
            count += 1
    return count


def image_lighting_metrics(image: evidence.PngImage) -> tuple[float, float]:
    pixel_count = len(image.pixels) // 3
    luminance_sum = 0.0
    dark_pixel_count = 0
    for offset in range(0, len(image.pixels), 3):
        red, green, blue = image.pixels[offset : offset + 3]
        luminance = (54 * red + 183 * green + 19 * blue) / 256.0
        luminance_sum += luminance
        if luminance <= DARK_PIXEL_LUMINANCE_THRESHOLD:
            dark_pixel_count += 1
    return (
        luminance_sum / pixel_count,
        dark_pixel_count / pixel_count,
    )


def validate_screenshot_files(
    scenario_root: Path, screenshots: list[dict[str, object]]
) -> float:
    screenshot_directory = scenario_root / "screenshots"
    if not screenshot_directory.is_dir() or screenshot_directory.is_symlink():
        raise client.E2EError("Fabric Attrahite screenshot directory is missing or linked")
    entries = list(screenshot_directory.iterdir())
    if (
        {path.name for path in entries} != set(SCREENSHOT_FILES)
        or any(not path.is_file() or path.is_symlink() for path in entries)
    ):
        raise client.E2EError("Fabric Attrahite screenshot file inventory changed")
    images: list[evidence.PngImage] = []
    for screenshot in screenshots:
        path = evidence.safe_screenshot_path(scenario_root, screenshot["file"])
        if (
            path.stat().st_size != screenshot["size"]
            or evidence.sha256_file(path) != screenshot["sha256"]
        ):
            raise client.E2EError(
                f"Fabric Attrahite screenshot differs from its report: {path}"
            )
        image = evidence.decode_png(path, EXPECTED_FRAMEBUFFER_DIMENSIONS)
        evidence.assert_image_is_not_blank(image, str(path))
        if count_missing_texture_pixels(image) != 0:
            raise client.E2EError(
                f"Fabric Attrahite screenshot contains missing-texture magenta: {path}"
            )
        mean_luminance, dark_pixel_ratio = image_lighting_metrics(image)
        if mean_luminance < MINIMUM_MEAN_LUMINANCE:
            raise client.E2EError(
                f"Fabric Attrahite screenshot mean luminance is too dark: {path}"
            )
        if dark_pixel_ratio > MAXIMUM_DARK_PIXEL_RATIO:
            raise client.E2EError(
                f"Fabric Attrahite screenshot dark-pixel ratio is too high: {path}"
            )
        images.append(image)
    ratio = evidence.changed_pixel_ratio(images[0], images[1])
    if ratio > MAXIMUM_REOPEN_CHANGED_PIXEL_RATIO:
        raise client.E2EError(
            "Fabric Attrahite reopened capture differs materially from its saved fixture"
        )
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
        or report.get("artifact_node") != "fabric-1.20.1"
        or report.get("minecraft") != "1.20.1"
        or report.get("loader") != "fabric"
        or report.get("java") != 17
        or report.get("lane") != "fabric-1.20.1"
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
        raise client.E2EError("Fabric Attrahite report lifecycle contract is invalid")
    validate_attrahite(report)
    artifacts = validate_artifact_inventory(report)
    screenshots = validate_screenshot_inventory(report)
    validate_assertions(report, screenshots)
    ratio = validate_screenshot_files(scenario_root, screenshots)
    return ratio, artifacts


def validate_world_save(
    configuration: client.ResolvedConfiguration, runtime: Path
) -> None:
    world_root = (
        client.game_directory(configuration, runtime)
        / "saves"
        / str(EXPECTED_WORLD["save_directory"])
    )
    if not world_root.is_dir() or world_root.is_symlink():
        raise client.E2EError(f"Fabric Attrahite saved world is missing or linked: {world_root}")
    for relative_path in ("level.dat", "session.lock", "region/r.0.0.mca"):
        path = world_root / relative_path
        if not path.is_file() or path.is_symlink() or path.stat().st_size <= 0:
            raise client.E2EError(
                f"Fabric Attrahite saved-world proof is missing or linked: {path}"
            )


def validate_game_lifecycle(
    configuration: client.ResolvedConfiguration,
    runtime: Path,
) -> None:
    game = client.game_directory(configuration, runtime)
    crash_reports = game / "crash-reports"
    if not crash_reports.is_dir() or crash_reports.is_symlink() or any(
        crash_reports.iterdir()
    ):
        raise client.E2EError("Fabric Attrahite runtime contains crash reports")
    latest_log = game / "logs" / "latest.log"
    if (
        not latest_log.is_file()
        or latest_log.is_symlink()
        or latest_log.stat().st_size <= 0
        or latest_log.stat().st_size > client.MAXIMUM_PROCESS_LOG_SIZE
    ):
        raise client.E2EError("Fabric Attrahite game log is missing, linked, or oversized")
    content = latest_log.read_text(encoding="utf-8", errors="replace")
    fatal_marker = next(
        (marker for marker in evidence.FATAL_GAME_LOG_MARKERS if marker in content),
        None,
    )
    if fatal_marker is not None:
        raise client.E2EError(
            f"Fabric Attrahite game log contains fatal marker: {fatal_marker}"
        )
    if "Stopping!" not in content:
        raise client.E2EError("Fabric Attrahite client did not stop normally")
    if content.count("Attrahite E2E evidence is complete:") != 1:
        raise client.E2EError(
            "Fabric Attrahite game log lacks one exact publication marker"
        )
    validate_world_save(configuration, runtime)


def validate_live_evidence(
    configuration: client.ResolvedConfiguration | None = None,
    runtime: Path | None = None,
) -> AttrahiteEvidenceSummary:
    """Validates the exact live v29 capture without launching or changing it."""

    resolved = configuration or client.load_configuration()
    validate_active_profile(resolved)
    target_runtime = runtime or client.runtime_root(resolved)
    if client.resolve_scenario_id(resolved, SCENARIO_ID) != SCENARIO_ID:
        raise client.E2EError("Fabric Attrahite scenario selection changed")
    client.verify_evidence_layout(resolved, target_runtime)
    scenario_root = client.evidence_root(resolved, target_runtime) / SCENARIO_ID
    reports = scenario_root / "reports"
    if (
        not reports.is_dir()
        or reports.is_symlink()
        or {path.name for path in reports.iterdir()} != {"report.json", "done.marker"}
    ):
        raise client.E2EError("Fabric Attrahite report directory inventory changed")
    report_path = reports / "report.json"
    done_path = reports / "done.marker"
    if report_path.is_symlink() or done_path.is_symlink():
        raise client.E2EError("Fabric Attrahite report or marker is linked")
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise client.E2EError("Fabric Attrahite completion marker is invalid")
    report = evidence.require_json_object(report_path, "Fabric Attrahite scenario report")
    ratio, artifacts = validate_report_contract(scenario_root, report)
    assertion_count = evidence.validate_assertions(report)
    production_digest, harness_digest = evidence.validate_artifacts(
        resolved, target_runtime, report
    )
    if (
        assertion_count != len(EXPECTED_ASSERTION_NAMES)
        or production_digest != artifacts["production"]["sha256"]
        or harness_digest != artifacts["harness"]["sha256"]
        or harness_digest != HARNESS_SHA256
    ):
        raise client.E2EError(
            "Fabric Attrahite strict evidence differs from artifact validation"
        )
    screenshot_paths = [
        evidence.safe_screenshot_path(scenario_root, screenshot["file"])
        for screenshot in report["screenshots"]
    ]
    if any(
        done_path.stat().st_mtime_ns < path.stat().st_mtime_ns
        for path in (report_path, *screenshot_paths)
    ):
        raise client.E2EError("Fabric Attrahite completion predates its evidence payload")
    scenario_size = sum(
        path.stat().st_size
        for path in scenario_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if scenario_size > evidence.MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise client.E2EError("Fabric Attrahite evidence exceeds the size bound")
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
        raise client.E2EError(
            f"Fabric Attrahite evidence archive is missing or linked: {archive_root}"
        )
    if archive_root.name != ARCHIVE_DIRECTORY_NAME:
        raise client.E2EError("Fabric Attrahite archive directory does not identify v29")
    files: set[str] = set()
    directories: set[str] = set()
    for path in archive_root.rglob("*"):
        relative_path = path.relative_to(archive_root).as_posix()
        if path.is_symlink():
            raise client.E2EError(
                f"Fabric Attrahite archive contains a linked entry: {relative_path}"
            )
        if path.is_file():
            files.add(relative_path)
        elif path.is_dir():
            directories.add(relative_path)
        else:
            raise client.E2EError(
                f"Fabric Attrahite archive contains a special entry: {relative_path}"
            )
    expected_files = set(ARCHIVE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    if files != expected_files or directories != ARCHIVE_DIRECTORIES:
        raise client.E2EError("Fabric Attrahite archive inventory changed")
    archive_size = sum(
        path.stat().st_size for path in archive_root.rglob("*") if path.is_file()
    )
    if archive_size > evidence.MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise client.E2EError("Fabric Attrahite archive exceeds the size bound")


def validate_no_competing_archives(archive_root: Path) -> None:
    """Requires v29 to be the sole versioned Fabric Attrahite archive."""

    if not archive_root.parent.is_dir() or archive_root.parent.is_symlink():
        raise client.E2EError("Fabric Attrahite archive parent is missing or linked")
    candidates = sorted(
        (
            entry
            for entry in archive_root.parent.iterdir()
            if ARCHIVE_DIRECTORY_PATTERN.fullmatch(entry.name)
        ),
        key=lambda entry: entry.name,
    )
    if candidates != [archive_root] or not archive_root.is_dir() or archive_root.is_symlink():
        raise client.E2EError("The exact sole Fabric Attrahite v29 archive is required")


def expected_repository_archive_root(
    configuration: client.ResolvedConfiguration,
) -> Path:
    return client.safe_repository_path(
        configuration.repository_root,
        f"docs/evidence/fabric-1.20.1/{ARCHIVE_DIRECTORY_NAME}",
        "Fabric Attrahite evidence archive",
    )


def validate_capture_artifact_lock(
    configuration: client.ResolvedConfiguration,
    capture_runtime: Path,
    report_artifacts: dict[str, dict[str, object]],
) -> Path:
    lock = client.load_artifact_lock(configuration, capture_runtime)
    if (
        not isinstance(lock, dict)
        or lock.get("schema") != 2
        or lock.get("profile_id") != PROFILE_ID
        or lock.get("artifact_node") != "fabric-1.20.1"
    ):
        raise client.E2EError("Fabric Attrahite capture has no exact artifact lock")
    locked_artifacts = lock.get("artifacts")
    if not isinstance(locked_artifacts, dict) or tuple(locked_artifacts) != (
        "production",
        "harness",
    ):
        raise client.E2EError("Fabric Attrahite capture artifact lock inventory changed")
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
            raise client.E2EError(
                f"Fabric Attrahite capture {role} lock differs from report"
            )
    lock_path = client.artifact_lock_path(configuration, capture_runtime)
    if not lock_path.is_file() or lock_path.is_symlink():
        raise client.E2EError("Fabric Attrahite artifact lock is missing or linked")
    return lock_path


def build_archive_manifest(
    configuration: client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> dict[str, object]:
    """Builds a seal only from the exact live v29 capture and copied payload."""

    validate_active_profile(configuration)
    validate_no_competing_archives(archive_root)
    expected_profile = configuration.repository_root / ACTIVE_PROFILE_RELATIVE_PATH
    if (
        profile_manifest_path.resolve() != expected_profile.resolve()
        or not profile_manifest_path.is_file()
        or profile_manifest_path.is_symlink()
        or profile_manifest_path.stat().st_size != PROFILE_SIZE
        or client.sha256_file(profile_manifest_path) != PROFILE_SHA256
    ):
        raise client.E2EError("Fabric Attrahite sealing requires the exact v29 profile")
    validate_archive_inventory(archive_root, include_manifest=False)
    if archive_root.resolve() != expected_repository_archive_root(configuration).resolve():
        raise client.E2EError(
            "Fabric Attrahite sealing requires the repository archive destination"
        )
    expected_runtime = client.runtime_root(configuration)
    client.ensure_owned_state_roots()
    if (
        capture_runtime.resolve() != expected_runtime.resolve()
        or not capture_runtime.is_dir()
        or capture_runtime.is_symlink()
        or capture_runtime.name != PROFILE_ID
    ):
        raise client.E2EError("Fabric Attrahite sealing requires the exact owned v29 runtime")
    client.verify_runtime(configuration, capture_runtime, artifact_policy="optional")
    live_summary = validate_live_evidence(configuration, capture_runtime)
    archive_report = evidence.require_json_object(
        archive_root / "reports/report.json", "Fabric Attrahite archived report"
    )
    ratio, report_artifacts = validate_report_contract(archive_root, archive_report)
    if (
        live_summary.assertion_count != len(EXPECTED_ASSERTION_NAMES)
        or live_summary.screenshot_count != len(EXPECTED_SCREENSHOTS)
        or abs(live_summary.reopen_changed_pixel_ratio - ratio) > 1e-12
        or live_summary.production_sha256 != report_artifacts["production"]["sha256"]
        or live_summary.harness_sha256 != report_artifacts["harness"]["sha256"]
    ):
        raise client.E2EError("Fabric Attrahite archive differs from live validation")
    capture_root = client.evidence_root(configuration, capture_runtime) / SCENARIO_ID
    capture_mtime_ns: dict[str, int] = {}
    files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        archive_path = archive_root / relative_path
        capture_path = capture_root / relative_path
        if not capture_path.is_file() or capture_path.is_symlink():
            raise client.E2EError(
                f"Fabric Attrahite capture payload is missing or linked: {capture_path}"
            )
        if (
            archive_path.stat().st_size != capture_path.stat().st_size
            or evidence.sha256_file(archive_path) != evidence.sha256_file(capture_path)
        ):
            raise client.E2EError(
                f"Fabric Attrahite archive payload differs from capture: {relative_path}"
            )
        capture_mtime_ns[relative_path] = capture_path.stat().st_mtime_ns
        files[relative_path] = {
            "size": archive_path.stat().st_size,
            "sha256": evidence.sha256_file(archive_path),
        }
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[path]
        for path in ARCHIVE_PAYLOAD_PATHS
        if path != "reports/done.marker"
    ):
        raise client.E2EError("Fabric Attrahite capture completion predates copied evidence")
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
            "artifact_node": "fabric-1.20.1",
            "minecraft": "1.20.1",
            "loader": "fabric",
            "loader_version": "0.17.3",
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
            "sha256": evidence.sha256_file(artifact_lock_path),
        },
        "assertion_count": len(EXPECTED_ASSERTION_NAMES),
        "screenshot_count": len(EXPECTED_SCREENSHOTS),
        "artifacts": report_artifacts,
        "files": files,
    }


def write_archive_manifest(
    configuration: client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> Path:
    """Atomically creates and immediately validates the one-shot archive seal."""

    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if manifest_path.exists() or manifest_path.is_symlink():
        raise client.E2EError(f"Fabric Attrahite archive manifest exists: {manifest_path}")
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
            raise client.E2EError(
                f"Fabric Attrahite archive manifest exists: {manifest_path}"
            ) from exception
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
    try:
        validate_archived_evidence(archive_root)
    except (client.E2EError, OSError, json.JSONDecodeError):
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
        raise client.E2EError("Fabric Attrahite archive manifest fields changed")
    if (
        manifest.get("schema") != 1
        or manifest.get("kind") != ARCHIVE_KIND
        or manifest.get("verification_scope") != ARCHIVE_VERIFICATION_SCOPE
        or manifest.get("scenario") != SCENARIO_ID
        or manifest.get("assertion_count") != len(EXPECTED_ASSERTION_NAMES)
        or manifest.get("screenshot_count") != len(EXPECTED_SCREENSHOTS)
    ):
        raise client.E2EError("Fabric Attrahite archive identity is invalid")
    if manifest.get("profile") != {
        "id": PROFILE_ID,
        "manifest_path": ACTIVE_PROFILE_RELATIVE_PATH,
        "manifest_size": PROFILE_SIZE,
        "manifest_sha256": PROFILE_SHA256,
    }:
        raise client.E2EError("Fabric Attrahite archive profile is invalid")
    if manifest.get("runtime") != {
        "artifact_node": "fabric-1.20.1",
        "minecraft": "1.20.1",
        "loader": "fabric",
        "loader_version": "0.17.3",
        "java": 17,
        "capture_kind": "composed-minecraft-framebuffer",
        "framebuffer_width": 1920,
        "framebuffer_height": 1080,
    }:
        raise client.E2EError("Fabric Attrahite archive runtime is invalid")
    publication = manifest.get("publication")
    if not isinstance(publication, dict) or set(publication) != {
        *ARCHIVE_PUBLICATION_ATTESTATION,
        "capture_mtime_ns",
    }:
        raise client.E2EError("Fabric Attrahite archive publication is malformed")
    if any(
        publication.get(key) != value
        for key, value in ARCHIVE_PUBLICATION_ATTESTATION.items()
    ):
        raise client.E2EError("Fabric Attrahite archive publication is invalid")
    capture_mtime_ns = publication.get("capture_mtime_ns")
    if (
        not isinstance(capture_mtime_ns, dict)
        or set(capture_mtime_ns) != set(ARCHIVE_PAYLOAD_PATHS)
        or any(type(value) is not int or value <= 0 for value in capture_mtime_ns.values())
    ):
        raise client.E2EError("Fabric Attrahite archive timestamps are invalid")
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[path]
        for path in ARCHIVE_PAYLOAD_PATHS
        if path != "reports/done.marker"
    ):
        raise client.E2EError("Fabric Attrahite archive completion predates its payload")
    capture_metadata = manifest.get("capture_metadata")
    if (
        not isinstance(capture_metadata, dict)
        or set(capture_metadata) != {"path", "size", "sha256"}
        or capture_metadata.get("path") != ARCHIVE_CAPTURE_METADATA_PATH
        or type(capture_metadata.get("size")) is not int
        or int(capture_metadata["size"]) <= 0
    ):
        raise client.E2EError("Fabric Attrahite archive capture metadata is invalid")
    client.validate_hex_digest(
        capture_metadata.get("sha256"), "Fabric Attrahite capture metadata"
    )
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or tuple(artifacts) != ("production", "harness"):
        raise client.E2EError("Fabric Attrahite archive artifacts changed")
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
            raise client.E2EError(
                f"Fabric Attrahite archived {role} artifact is invalid"
            )
        digest = client.validate_hex_digest(
            artifact.get("sha256"), f"Fabric Attrahite archived {role} artifact"
        )
        if role == "harness" and (
            artifact["size"] != HARNESS_SIZE or digest != HARNESS_SHA256
        ):
            raise client.E2EError("Fabric Attrahite archived harness pin changed")
        validated_artifacts[role] = artifact
    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(ARCHIVE_PAYLOAD_PATHS):
        raise client.E2EError("Fabric Attrahite archive payloads changed")
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if (
            not isinstance(record, dict)
            or set(record) != {"size", "sha256"}
            or type(record.get("size")) is not int
            or int(record["size"]) <= 0
        ):
            raise client.E2EError(
                f"Fabric Attrahite payload record is invalid: {relative_path}"
            )
        client.validate_hex_digest(
            record.get("sha256"), f"Fabric Attrahite payload {relative_path}"
        )
        path = archive_root / relative_path
        if (
            path.stat().st_size != record["size"]
            or evidence.sha256_file(path) != record["sha256"]
        ):
            raise client.E2EError(
                f"Fabric Attrahite payload differs from manifest: {relative_path}"
            )
    return validated_artifacts


def validate_archived_evidence(archive_root: Path) -> AttrahiteEvidenceSummary:
    """Validates a self-contained v29 archive without consulting live state."""

    validate_no_competing_archives(archive_root)
    validate_archive_inventory(archive_root)
    manifest = evidence.require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME, "Fabric Attrahite archive manifest"
    )
    manifest_artifacts = validate_archive_manifest(archive_root, manifest)
    done_path = archive_root / "reports/done.marker"
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise client.E2EError("Fabric Attrahite completion marker is invalid")
    report = evidence.require_json_object(
        archive_root / "reports/report.json", "Fabric Attrahite archived report"
    )
    ratio, report_artifacts = validate_report_contract(archive_root, report)
    if report_artifacts != manifest_artifacts:
        raise client.E2EError("Fabric Attrahite report artifacts differ from manifest")
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
        description="Validate exact Fabric 1.20.1 Attrahite evidence."
    )
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument(
        "--live", action="store_true", help="validate the owned v29 capture runtime"
    )
    operation.add_argument(
        "--archive", type=Path, help="validate one frozen v29 archive"
    )
    operation.add_argument(
        "--create-archive-manifest",
        type=Path,
        metavar="ARCHIVE",
        help="seal copied v29 payload from one explicit owned runtime",
    )
    parser.add_argument(
        "--capture-runtime", type=Path, help="owned runtime used only while sealing"
    )
    parser.add_argument(
        "--profile-manifest", type=Path, help="v29 manifest used only while sealing"
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
            configuration = client.load_configuration(arguments.profile_manifest)
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
    except (client.E2EError, OSError, json.JSONDecodeError) as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 2
    print(
        f"Validated Fabric {SCENARIO_ID} ({summary.profile_id}): "
        f"{summary.assertion_count} assertions, "
        f"{summary.screenshot_count} screenshots, "
        f"reopen delta {summary.reopen_changed_pixel_ratio:.6f}"
    )
    print(f"Production SHA-256: {summary.production_sha256}")
    print(f"Harness SHA-256: {summary.harness_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
