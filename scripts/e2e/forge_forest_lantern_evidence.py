#!/usr/bin/env python3
"""Validate live or archived Forge Forest Lantern evidence."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import sys
import tempfile


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_client
import forge_evidence


SCENARIO_ID = "forest-lantern"
PROFILE_ID = "etherology-e2e-forge-1.20.1-v12"
ACTIVE_PROFILE_RELATIVE_PATH = "scripts/e2e/forge-1.20.1-profile.json"
SNAPSHOT_PROFILE_RELATIVE_PATH = "scripts/e2e/forge-1.20.1-profile-v12.json"
PROFILE_SIZE = 3668
PROFILE_SHA256 = "c23a2a905e40c721cda1d45086064667aacd568489a319eef4ce30e153a2a8d7"
ARCHIVE_DIRECTORY_NAME = "forest-lantern-v12"
ARCHIVE_MANIFEST_NAME = forge_evidence.ARCHIVE_MANIFEST_NAME
ARCHIVE_KIND = forge_evidence.ARCHIVE_KIND
ARCHIVE_VERIFICATION_SCOPE = forge_evidence.ARCHIVE_VERIFICATION_SCOPE
ARCHIVE_CAPTURE_METADATA_PATH = "forge-artifact-lock.json"
ARCHIVE_DIRECTORIES = {"reports", "screenshots"}
MAXIMUM_SCENARIO_EVIDENCE_SIZE = 64 * 1024 * 1024
FATAL_GAME_LOG_MARKERS = (
    "Mixin apply failed",
    "MixinTransformerError",
    "InvalidMixinException",
    "NoClassDefFoundError",
    "NoSuchMethodError",
    "NoSuchFieldError",
    "ExceptionInInitializerError",
    "Could not execute entrypoint stage",
    "Could not find required mod",
    "A mod crashed on startup!",
    "Encountered an unexpected exception",
    "java.lang.OutOfMemoryError",
    "Etherology forest-lantern lifecycle failure:",
)
PHASES = (
    "empty",
    "stages",
    "facing-north",
    "facing-east",
    "facing-south",
    "facing-west",
    "reopened",
)
SCREENSHOT_FILES = tuple(f"forest-lantern-{phase}.png" for phase in PHASES)
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
EXPECTED_SCENARIOS = ("ethereal-storage", "ethereal-channel", SCENARIO_ID)
EXPECTED_WORLD = {
    "save_directory": "etherology-e2e-forest-lantern-world",
    "display_name": "Etherology E2E Forest Lantern",
    "seed": 77306496635732,
    "dimension": "minecraft:overworld",
    "integrated": True,
    "reopened": True,
}
EXPECTED_READY_RESOURCES = (
    "etherology:blockstates/forest_lantern.json",
    "etherology:models/block/forest_lantern_0.json",
    "etherology:models/block/forest_lantern_1.json",
    "etherology:models/block/forest_lantern_2.json",
    "etherology:models/block/forest_lantern_3.json",
    "etherology:models/block/forest_lantern.json",
    "etherology:models/item/forest_lantern.json",
    "etherology:textures/block/forest_lantern_0.png",
    "etherology:textures/block/forest_lantern_1.png",
    "etherology:textures/block/forest_lantern_2.png",
    "etherology:textures/block/forest_lantern_3.png",
    "etherology:textures/block/forest_lantern.png",
    "etherology:textures/item/forest_lantern.png",
)
EXPECTED_ASSET_SHA256 = dict(
    zip(
        EXPECTED_READY_RESOURCES,
        (
            "2e73ec0f61ca32b3e5331c2410362ae2df599d41a5d1499cf2fc84de5dcf74e3",
            "ff1afef383ebf725c102a11c36a4bd7671632841bfbdcd788a81824dcbb3af44",
            "026b26bfba8c8cec6de47c6dc6e3b097b66e07be30204ff3a7cad420cd9c349d",
            "a16895bea5f381c16c9454b349d172e527d3be2d639d2d8b05505d9a37a6e709",
            "6d57d90558f8c270a6a6f00ffb43e4f66f69d2042bc0bb18cd34de38285dde8d",
            "c45939dd725b11d2034406e3ec8b9040a97d6fa9c7c83d3782444a22e7cfd90c",
            "d57283548233724975a2f7d9aeeee41a00df0c0d73b02c314da8829aa6ab3e34",
            "c850de55787124203c0176cf43364ad7459418ee401523cb71b733e58eff97a2",
            "bc9ce2c1e3c310e81c324326b5828924319ebb5c7d23e64fbb9fbf883e930c7f",
            "5e5d5df51ad75e06b99d3dd84bca8516c6b17b90948217a7a9624321ad1a9d1c",
            "f4a76d42b1f7ca0106698ea56ee5045012b33c1a1aa1d433e202b8f1433756cd",
            "5ab9532f8b9090492a84479b404c4c3c4ac3733a6d867e50534f54fcc8f310b6",
            "38bd46a7cb5b35dd28ee2b6ff718c190596f1a2b95a3138f90b6fa19fce7d143",
        ),
        strict=True,
    )
)
EXPECTED_STATE_INVENTORY = tuple(
    sorted(
        f"age={age},facing={facing}"
        for age in range(5)
        for facing in ("east", "north", "south", "west")
    )
)
EXPECTED_ARTIFACTS = (
    ("production", "etherology", "etherology-forge-under-test.jar"),
    ("harness", "etherology_e2e_harness", "etherology-forge-e2e-harness.jar"),
)
STAGE_FIXTURES = tuple(
    {
        "age": age,
        "forced": True,
        "facing": facing,
        "position": f"{x},{y},2",
        "support_position": f"{x + support_x},{y},{2 + support_z}",
        "support_id": "minecraft:oak_log" if age % 2 == 0 else "minecraft:stripped_oak_log",
    }
    for facing, y, support_x, support_z in (
        ("north", 122, 0, 1),
        ("east", 124, -1, 0),
        ("south", 126, 0, -1),
        ("west", 128, 1, 0),
    )
    for age, x in enumerate((-6, -2, 2, 6))
)
PLACEMENT_FIXTURES = (
    {
        "facing": "north",
        "position": "-6,125,2",
        "support_position": "-6,125,3",
        "support_id": "minecraft:polished_andesite",
    },
    {
        "facing": "east",
        "position": "-2,125,2",
        "support_position": "-3,125,2",
        "support_id": "minecraft:polished_andesite",
    },
    {
        "facing": "south",
        "position": "2,125,2",
        "support_position": "2,125,1",
        "support_id": "minecraft:polished_andesite",
    },
    {
        "facing": "west",
        "position": "6,125,2",
        "support_position": "7,125,2",
        "support_id": "minecraft:polished_andesite",
    },
)
CAMERA = {
    "x": 9.5,
    "y": 121.0,
    "z": -10.5,
    "yaw": 38.0,
    "pitch": -6.0,
    "first_person": True,
    "on_ground": True,
}
CAPTURE_STATE_FIELDS = (
    "mirror_exact",
    "render_ready",
    "camera_exact",
    "stable_renders",
    "framebuffer",
    "server_snapshot",
    "client_snapshot",
)
OBSERVATION_FIELDS = ("position", "block_id", "age", "facing", "can_place_at")
SNAPSHOT_FIELDS = (
    "stages",
    "stage_support_ids",
    "placements",
    "placement_support_ids",
    "unsupported_target",
    "unsupported_support_id",
)
PLACEMENT_EVIDENCE_FIELDS = (
    "direction",
    "action_result",
    "accepted",
    "stack_before",
    "stack_after",
    "block_item_mapping",
    "support_valid",
    "observation",
    "support_id",
)
EXPECTED_SCREENSHOTS = tuple(
    (phase, f"screenshots/{file_name}")
    for phase, file_name in zip(PHASES, SCREENSHOT_FILES)
)
EXPECTED_REPORT_FIELDS = {
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
    "framebuffer_width",
    "framebuffer_height",
    "lifecycle_failure",
    "assertions",
    "world",
    "ready_resources",
    "artifacts",
    "screenshots",
    "forest_lantern",
}
EXPECTED_FOREST_LANTERN_FIELDS = (
    "registry_id",
    "item_id",
    "block_item_mapping",
    "default_state",
    "state_count",
    "state_inventory",
    "raw_state_ids",
    "render_layer",
    "models_baked",
    "renderable_state_inventory",
    "luminance",
    "asset_sha256",
    "forced_stage_ages",
    "stage_fixtures",
    "placement_fixtures",
    "unsupported_placement",
    "placements",
    "persistence_exact",
    "camera",
    "required_stable_renders",
    "captures",
)
BASE_ASSERTION_NAMES = (
    "forge_mod_loaded:etherology",
    "registry:block:etherology:forest_lantern",
    "registry:item:etherology:forest_lantern",
    "block_item_mapping",
    "default_state_exact",
    "state_inventory_exact",
    "state_network_ids_exact",
    "client_render_resources",
    "client_render_layer_cutout",
    "client_models_baked",
    "default_luminance",
    "packaged_root_jar:etherology",
    "packaged_root_jar:etherology_e2e_harness",
    "integrated_world_joined",
    "server_arena_chunks_loaded",
    "checker_backings_exact",
    "forced_stage_ages_exact",
    "forced_immature_support_contract",
    "unsupported_block_item_rejected",
    "real_block_item_placement:north",
    "real_block_item_placement:east",
    "real_block_item_placement:south",
    "real_block_item_placement:west",
    "native_twenty_state_matrix_exact",
    "forced_world_save",
    "restart_exact_state",
)
EXPECTED_ASSERTION_NAMES = (
    *BASE_ASSERTION_NAMES,
    *(
        name
        for phase in PHASES
        for name in (
            f"capture_mirror_exact:{phase}",
            f"capture_render_ready:{phase}",
            f"capture_camera_exact:{phase}",
            f"capture_consecutive_stable_renders:{phase}",
            f"capture_framebuffer_dimensions:{phase}",
            f"native_screenshot_written:{phase}",
        )
    ),
    "isolated_save_directory_present",
)
CAMERA_SUMMARY = (
    "first_person=true;x=9.5;y=121.0;z=-10.5;yaw=38.0;pitch=-6.0;"
    "on_ground=true;tolerance=1.0E-4"
)
MINIMUM_PLACEMENT_CHANGED_PIXEL_RATIO = 0.00005
MAXIMUM_PHASE_CHANGED_PIXEL_RATIO = 0.15
MAXIMUM_REOPENED_STRUCTURAL_CHANGE_RATIO = 0.06


@dataclass(frozen=True)
class ForestLanternEvidenceSummary:
    """Reports the exact evidence inventory accepted by this verifier."""

    profile_id: str
    assertion_count: int
    screenshot_count: int
    minimum_changed_pixel_ratio: float
    production_sha256: str
    harness_sha256: str


def validate_active_profile(configuration: forge_client.ResolvedConfiguration) -> None:
    """Requires the immutable v12 profile and exact Forge runtime contract."""

    active_profile = configuration.repository_root / ACTIVE_PROFILE_RELATIVE_PATH
    snapshot_profile = configuration.repository_root / SNAPSHOT_PROFILE_RELATIVE_PATH
    for path in (active_profile, snapshot_profile):
        if not path.is_file() or path.is_symlink():
            raise forge_client.E2EError(
                f"Forge Forest Lantern profile is missing or linked: {path}"
            )
        if path.stat().st_size != PROFILE_SIZE or forge_client.sha256_file(path) != PROFILE_SHA256:
            raise forge_client.E2EError(f"Forge Forest Lantern profile bytes changed: {path}")
    if active_profile.read_bytes() != snapshot_profile.read_bytes():
        raise forge_client.E2EError("The active Forge profile differs from its v12 snapshot")

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
        raise forge_client.E2EError("The Forge Forest Lantern v12 profile contract changed")
    for role, mod_id, file_name in EXPECTED_ARTIFACTS:
        artifact = forge_client.artifact_spec(configuration, role)
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            raise forge_client.E2EError(
                f"The Forge Forest Lantern profile has the wrong {role} artifact identity"
            )


def expected_observation(
    position: str,
    block_id: str,
    age: str = "",
    facing: str = "",
    can_place_at: bool = False,
) -> dict[str, object]:
    return {
        "position": position,
        "block_id": block_id,
        "age": age,
        "facing": facing,
        "can_place_at": can_place_at,
    }


def expected_snapshot(phase: str) -> dict[str, object]:
    if phase not in PHASES:
        raise forge_client.E2EError(f"Unsupported Forest Lantern capture phase: {phase}")
    has_stages = phase != "empty"
    placed_count = {
        "empty": 0,
        "stages": 0,
        "facing-north": 1,
        "facing-east": 2,
        "facing-south": 3,
        "facing-west": 4,
        "reopened": 4,
    }[phase]
    stages = [
        expected_observation(
            str(fixture["position"]),
            "etherology:forest_lantern" if has_stages else "minecraft:air",
            str(fixture["age"]) if has_stages else "",
            str(fixture["facing"]) if has_stages else "",
            False,
        )
        for fixture in STAGE_FIXTURES
    ]
    placements = [
        expected_observation(
            str(fixture["position"]),
            "etherology:forest_lantern" if index < placed_count else "minecraft:air",
            "4" if index < placed_count else "",
            str(fixture["facing"]) if index < placed_count else "",
            index < placed_count,
        )
        for index, fixture in enumerate(PLACEMENT_FIXTURES)
    ]
    return {
        "stages": stages,
        "stage_support_ids": [str(fixture["support_id"]) for fixture in STAGE_FIXTURES],
        "placements": placements,
        "placement_support_ids": [
            "minecraft:polished_andesite" for _fixture in PLACEMENT_FIXTURES
        ],
        "unsupported_target": expected_observation(
            "9,125,2",
            "minecraft:air",
        ),
        "unsupported_support_id": "minecraft:iron_bars",
    }


def validate_observation(
    actual: object,
    expected: dict[str, object],
    description: str,
) -> None:
    if (
        not isinstance(actual, dict)
        or tuple(actual) != OBSERVATION_FIELDS
        or actual != expected
        or type(actual.get("can_place_at")) is not bool
    ):
        raise forge_client.E2EError(f"Forge Forest Lantern {description} changed")


def validate_snapshot(actual: object, phase: str, description: str) -> None:
    expected = expected_snapshot(phase)
    if not isinstance(actual, dict) or tuple(actual) != SNAPSHOT_FIELDS:
        raise forge_client.E2EError(f"Forge Forest Lantern {description} fields changed")
    stages = actual.get("stages")
    placements = actual.get("placements")
    if (
        not isinstance(stages, list)
        or len(stages) != len(STAGE_FIXTURES)
        or not isinstance(placements, list)
        or len(placements) != len(PLACEMENT_FIXTURES)
    ):
        raise forge_client.E2EError(f"Forge Forest Lantern {description} inventory changed")
    for index, observation in enumerate(stages):
        validate_observation(
            observation,
            expected["stages"][index],
            f"{description} stage {index}",
        )
    for index, observation in enumerate(placements):
        validate_observation(
            observation,
            expected["placements"][index],
            f"{description} placement {index}",
        )
    for field_name in (
        "stage_support_ids",
        "placement_support_ids",
        "unsupported_support_id",
    ):
        if actual.get(field_name) != expected[field_name]:
            raise forge_client.E2EError(
                f"Forge Forest Lantern {description} {field_name} changed"
            )
    validate_observation(
        actual.get("unsupported_target"),
        expected["unsupported_target"],
        f"{description} unsupported target",
    )


def validate_placement_evidence(
    actual: object,
    direction: str,
    accepted: bool,
) -> None:
    if not isinstance(actual, dict) or tuple(actual) != PLACEMENT_EVIDENCE_FIELDS:
        raise forge_client.E2EError("Forge Forest Lantern placement evidence fields changed")
    if accepted:
        fixture = next(
            fixture for fixture in PLACEMENT_FIXTURES if fixture["facing"] == direction
        )
        expected = {
            "direction": direction,
            "action_result": "CONSUME",
            "accepted": True,
            "stack_before": 1,
            "stack_after": 0,
            "block_item_mapping": True,
            "support_valid": True,
            "observation": expected_observation(
                str(fixture["position"]),
                "etherology:forest_lantern",
                "4",
                direction,
                True,
            ),
            "support_id": "minecraft:polished_andesite",
        }
    else:
        expected = {
            "direction": "north",
            "action_result": "FAIL",
            "accepted": False,
            "stack_before": 1,
            "stack_after": 1,
            "block_item_mapping": True,
            "support_valid": False,
            "observation": expected_observation("9,125,2", "minecraft:air"),
            "support_id": "minecraft:iron_bars",
        }
    if actual != expected:
        raise forge_client.E2EError(
            f"Forge Forest Lantern {direction} placement evidence changed"
        )


def validate_forest_lantern(report: dict[str, object]) -> None:
    """Requires the exact registry, fixture, placement, and restart contract."""

    forest_lantern = report.get("forest_lantern")
    if (
        not isinstance(forest_lantern, dict)
        or tuple(forest_lantern) != EXPECTED_FOREST_LANTERN_FIELDS
    ):
        raise forge_client.E2EError("Forge Forest Lantern report field inventory changed")
    raw_state_ids = forest_lantern.get("raw_state_ids")
    if (
        forest_lantern.get("registry_id") != "etherology:forest_lantern"
        or forest_lantern.get("item_id") != "etherology:forest_lantern"
        or forest_lantern.get("block_item_mapping") is not True
        or forest_lantern.get("default_state") != "age=4,facing=north"
        or forest_lantern.get("state_count") != 20
        or forest_lantern.get("state_inventory") != list(EXPECTED_STATE_INVENTORY)
        or not isinstance(raw_state_ids, list)
        or len(raw_state_ids) != 20
        or any(type(raw_id) is not int or raw_id < 0 for raw_id in raw_state_ids)
        or raw_state_ids != sorted(raw_state_ids)
        or len(set(raw_state_ids)) != 20
        or forest_lantern.get("render_layer") != "cutout"
        or forest_lantern.get("models_baked") is not True
        or forest_lantern.get("renderable_state_inventory")
        != list(EXPECTED_STATE_INVENTORY)
        or forest_lantern.get("luminance") != 8
        or forest_lantern.get("asset_sha256") != EXPECTED_ASSET_SHA256
        or forest_lantern.get("forced_stage_ages") != [0, 1, 2, 3]
        or forest_lantern.get("stage_fixtures") != list(STAGE_FIXTURES)
        or forest_lantern.get("placement_fixtures") != list(PLACEMENT_FIXTURES)
        or forest_lantern.get("persistence_exact") is not True
        or forest_lantern.get("camera") != CAMERA
        or forest_lantern.get("required_stable_renders") != REQUIRED_STABLE_RENDERS
    ):
        raise forge_client.E2EError("Forge Forest Lantern registry/fixture contract changed")

    validate_placement_evidence(
        forest_lantern.get("unsupported_placement"),
        "unsupported",
        False,
    )
    placements = forest_lantern.get("placements")
    directions = ("north", "east", "south", "west")
    if not isinstance(placements, dict) or tuple(placements) != directions:
        raise forge_client.E2EError("Forge Forest Lantern placement direction order changed")
    for direction in directions:
        validate_placement_evidence(placements.get(direction), direction, True)

    captures = forest_lantern.get("captures")
    if not isinstance(captures, dict) or tuple(captures) != PHASES:
        raise forge_client.E2EError("Forge Forest Lantern capture phase order changed")
    for phase in PHASES:
        capture = captures.get(phase)
        if (
            not isinstance(capture, dict)
            or tuple(capture) != CAPTURE_STATE_FIELDS
            or capture.get("mirror_exact") is not True
            or capture.get("render_ready") is not True
            or capture.get("camera_exact") is not True
            or type(capture.get("stable_renders")) is not int
            or capture.get("stable_renders") != REQUIRED_STABLE_RENDERS
            or capture.get("framebuffer")
            != f"{EXPECTED_FRAMEBUFFER_DIMENSIONS[0]}x{EXPECTED_FRAMEBUFFER_DIMENSIONS[1]}"
        ):
            raise forge_client.E2EError(
                f"Forge Forest Lantern {phase} capture state changed"
            )
        validate_snapshot(capture.get("server_snapshot"), phase, f"{phase} server snapshot")
        validate_snapshot(capture.get("client_snapshot"), phase, f"{phase} client snapshot")
        if capture.get("server_snapshot") != capture.get("client_snapshot"):
            raise forge_client.E2EError(
                f"Forge Forest Lantern {phase} server/client mirrors differ"
            )
    for phase in ("facing-west", "reopened"):
        capture = captures[phase]
        for role in ("server", "client"):
            if native_state_inventory(capture[f"{role}_snapshot"]) != EXPECTED_STATE_INVENTORY:
                raise forge_client.E2EError(
                    "Forge Forest Lantern does not expose the exact native twenty-state "
                    f"matrix in the {phase} {role} snapshot"
                )


def validate_artifact_inventory(
    report: dict[str, object],
) -> dict[str, dict[str, object]]:
    artifacts = report.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) != len(EXPECTED_ARTIFACTS):
        raise forge_client.E2EError("Forge Forest Lantern artifact inventory changed")
    validated: dict[str, dict[str, object]] = {}
    for artifact, (role, mod_id, file_name) in zip(artifacts, EXPECTED_ARTIFACTS):
        if not isinstance(artifact, dict) or tuple(artifact) != (
            "mod_id",
            "passed",
            "file_name",
            "size",
            "sha256",
            "failure",
        ):
            raise forge_client.E2EError(
                f"Forge Forest Lantern {role} artifact provenance is malformed"
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
                f"Forge Forest Lantern {role} artifact identity is invalid"
            )
        digest = forge_client.validate_hex_digest(
            artifact.get("sha256"),
            f"Forge Forest Lantern {role} artifact",
        )
        validated[role] = {
            "mod_id": mod_id,
            "file_name": file_name,
            "size": artifact["size"],
            "sha256": digest,
        }
    return validated


def validate_screenshot_inventory(
    report: dict[str, object],
) -> list[dict[str, object]]:
    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or len(screenshots) != len(
        EXPECTED_SCREENSHOTS
    ):
        raise forge_client.E2EError("Forge Forest Lantern screenshot inventory changed")
    validated: list[dict[str, object]] = []
    for screenshot, (expected_step, expected_file) in zip(
        screenshots,
        EXPECTED_SCREENSHOTS,
    ):
        if not isinstance(screenshot, dict) or set(screenshot) != {
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
        }:
            raise forge_client.E2EError(
                "Forge Forest Lantern screenshot provenance is malformed"
            )
        if (
            screenshot.get("step") != expected_step
            or screenshot.get("role") != "host"
            or screenshot.get("file") != expected_file
            or (screenshot.get("width"), screenshot.get("height"))
            != EXPECTED_FRAMEBUFFER_DIMENSIONS
            or type(screenshot.get("size")) is not int
            or int(screenshot["size"]) <= 0
            or type(screenshot.get("completed_render_count")) is not int
            or screenshot.get("completed_render_count") != REQUIRED_STABLE_RENDERS
            or screenshot.get("source") != "minecraft-framebuffer"
            or screenshot.get("edited") is not False
        ):
            raise forge_client.E2EError("Forge Forest Lantern screenshot contract is invalid")
        forge_client.validate_hex_digest(
            screenshot.get("sha256"),
            f"Forge Forest Lantern screenshot {expected_file}",
        )
        validated.append(screenshot)
    return validated


def observation_summary(observation: dict[str, object]) -> str:
    if observation["block_id"] == "minecraft:air":
        return f"{observation['position']}=minecraft:air"
    can_place_at = str(observation["can_place_at"]).lower()
    return (
        f"{observation['position']}={observation['block_id']}"
        f"[age={observation['age']},facing={observation['facing']},"
        f"can_place_at={can_place_at}]"
    )


def snapshot_summary(snapshot: dict[str, object]) -> str:
    stages = ";".join(observation_summary(value) for value in snapshot["stages"])
    placements = ";".join(
        observation_summary(value) for value in snapshot["placements"]
    )
    unsupported = observation_summary(snapshot["unsupported_target"])
    return f"stages={stages}|placements={placements}|unsupported={unsupported}"


def stage_summary(snapshot: dict[str, object]) -> str:
    return ";".join(observation_summary(value) for value in snapshot["stages"])


def native_state_inventory(snapshot: dict[str, object]) -> tuple[str, ...]:
    observations = (*snapshot["stages"], *snapshot["placements"])
    return tuple(
        sorted(
            f"age={observation['age']},facing={observation['facing']}"
            for observation in observations
            if observation["block_id"] == "etherology:forest_lantern"
        )
    )


def phase_expected_summary(phase: str) -> str:
    has_stages = phase != "empty"
    placed_count = {
        "empty": 0,
        "stages": 0,
        "facing-north": 1,
        "facing-east": 2,
        "facing-south": 3,
        "facing-west": 4,
        "reopened": 4,
    }[phase]
    stages = "ages0-3:north,east,south,west" if has_stages else "air"
    return f"stages={stages};mature_placements={placed_count};unsupported=air"


def placement_summary(placement: dict[str, object]) -> str:
    return (
        f"accepted={str(placement['accepted']).lower()}"
        f";action={placement['action_result']}"
        f";stack={placement['stack_before']}->{placement['stack_after']}"
        f";block_item_mapping={str(placement['block_item_mapping']).lower()}"
        f";support_valid={str(placement['support_valid']).lower()}"
        f";state={observation_summary(placement['observation'])}"
        f";support={placement['support_id']}"
    )


def expected_assertion_evidence(
    report: dict[str, object],
    screenshots: list[dict[str, object]],
) -> dict[str, tuple[str, str]]:
    forest_lantern = report["forest_lantern"]
    captures = forest_lantern["captures"]
    screenshot_by_phase = {
        str(screenshot["step"]): screenshot for screenshot in screenshots
    }
    state_inventory_summary = "[" + ", ".join(EXPECTED_STATE_INVENTORY) + "]"
    raw_ids_summary = "[" + ", ".join(
        str(raw_id) for raw_id in forest_lantern["raw_state_ids"]
    ) + "]"
    stages_snapshot = captures["stages"]["server_snapshot"]
    expected: dict[str, tuple[str, str]] = {
        "forge_mod_loaded:etherology": ("loaded", "loaded"),
        "registry:block:etherology:forest_lantern": ("present", "present"),
        "registry:item:etherology:forest_lantern": ("present", "present"),
        "block_item_mapping": (
            "vanilla BlockItem mapped to etherology:forest_lantern",
            "vanilla BlockItem mapped to etherology:forest_lantern",
        ),
        "default_state_exact": ("age=4,facing=north", "age=4,facing=north"),
        "state_inventory_exact": (state_inventory_summary, state_inventory_summary),
        "state_network_ids_exact": ("20 unique non-negative raw IDs", raw_ids_summary),
        "client_render_resources": (
            "13 exact resources with canonical SHA-256 digests",
            "13 exact resources with canonical SHA-256 digests",
        ),
        "client_render_layer_cutout": ("cutout", "cutout"),
        "client_models_baked": (
            "20 block states with non-empty baked geometry and one item model",
            "20 block states with non-empty baked geometry and one item model",
        ),
        "default_luminance": ("8", "8"),
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
        "server_arena_chunks_loaded": ("four full chunks", "true"),
        "checker_backings_exact": (
            "alternating vanilla logs, four polished-andesite supports, "
            "one iron-bars rejection support",
            "exact",
        ),
        "forced_stage_ages_exact": (
            "forced ages 0,1,2,3 across north,east,south,west",
            stage_summary(stages_snapshot),
        ),
        "forced_immature_support_contract": (
            "all 16 forced age/facing states invalid without deferred peach logs",
            "true",
        ),
        "unsupported_block_item_rejected": (
            "not accepted; stack 1->1; target air; support invalid",
            placement_summary(forest_lantern["unsupported_placement"]),
        ),
        "forced_world_save": ("true", "true"),
        "restart_exact_state": (
            "saved snapshot equals reopened snapshot",
            "saved snapshot equals reopened snapshot",
        ),
        "isolated_save_directory_present": (
            "etherology-e2e-forest-lantern-world",
            "etherology-e2e-forest-lantern-world",
        ),
    }
    for direction in ("north", "east", "south", "west"):
        expected[f"real_block_item_placement:{direction}"] = (
            "accepted BlockItem; stack 1->0; mature exact facing; support valid",
            placement_summary(forest_lantern["placements"][direction]),
        )
    expected["native_twenty_state_matrix_exact"] = (
        "20 unique native server/client age/facing states before save and after reopen",
        "[" + ", ".join(EXPECTED_STATE_INVENTORY) + "]",
    )
    for phase in PHASES:
        capture = captures[phase]
        screenshot = screenshot_by_phase[phase]
        expected[f"capture_mirror_exact:{phase}"] = (
            phase_expected_summary(phase),
            snapshot_summary(capture["client_snapshot"]),
        )
        expected[f"capture_render_ready:{phase}"] = (
            "terrain complete and all fixture positions rendering-ready",
            "ready",
        )
        expected[f"capture_camera_exact:{phase}"] = (CAMERA_SUMMARY, CAMERA_SUMMARY)
        expected[f"capture_consecutive_stable_renders:{phase}"] = ("120", "120")
        expected[f"capture_framebuffer_dimensions:{phase}"] = (
            f"{EXPECTED_FRAMEBUFFER_DIMENSIONS[0]}x{EXPECTED_FRAMEBUFFER_DIMENSIONS[1]}",
            f"{EXPECTED_FRAMEBUFFER_DIMENSIONS[0]}x{EXPECTED_FRAMEBUFFER_DIMENSIONS[1]}",
        )
        expected[f"native_screenshot_written:{phase}"] = (
            "one non-empty unedited framebuffer PNG",
            f"{screenshot['size']} bytes, sha256={screenshot['sha256']}",
        )
    return expected


def validate_assertions(
    report: dict[str, object],
    screenshots: list[dict[str, object]],
) -> None:
    assertions = report.get("assertions")
    if not isinstance(assertions, list) or len(assertions) != len(
        EXPECTED_ASSERTION_NAMES
    ):
        raise forge_client.E2EError("Forge Forest Lantern assertion inventory changed")
    expected_evidence = expected_assertion_evidence(report, screenshots)
    for assertion, expected_name in zip(assertions, EXPECTED_ASSERTION_NAMES):
        if not isinstance(assertion, dict) or set(assertion) != {
            "name",
            "passed",
            "expected",
            "actual",
        }:
            raise forge_client.E2EError("Forge Forest Lantern assertion fields changed")
        if assertion.get("name") != expected_name or assertion.get("passed") is not True:
            raise forge_client.E2EError("Forge Forest Lantern assertion inventory changed")
        expected_pair = expected_evidence.get(expected_name)
        if expected_pair is None or (
            assertion.get("expected"),
            assertion.get("actual"),
        ) != expected_pair:
            raise forge_client.E2EError("Forge Forest Lantern assertion evidence changed")


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
    """Compares luminance edges while tolerating uniform restart lighting shifts."""

    if (left.width, left.height) != (right.width, right.height):
        raise forge_client.E2EError(
            "Forge Forest Lantern structural comparison dimensions differ"
        )
    left_x = left.width * 20 // 100
    right_x = left.width * 85 // 100
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
            "Forge Forest Lantern structural comparison region is empty"
        )
    return changed / compared


def validate_screenshot_files(
    scenario_root: Path,
    screenshots: list[dict[str, object]],
) -> float:
    screenshot_directory = scenario_root / "screenshots"
    if not screenshot_directory.is_dir() or screenshot_directory.is_symlink():
        raise forge_client.E2EError(
            "Forge Forest Lantern screenshot directory is missing or linked"
        )
    expected_names = set(SCREENSHOT_FILES)
    entries = list(screenshot_directory.iterdir())
    if (
        {path.name for path in entries} != expected_names
        or any(not path.is_file() or path.is_symlink() for path in entries)
    ):
        raise forge_client.E2EError("Forge Forest Lantern screenshot file inventory changed")

    images: list[forge_evidence.PngImage] = []
    for screenshot in screenshots:
        path = scenario_root / str(screenshot["file"])
        if not path.is_file() or path.is_symlink():
            raise forge_client.E2EError(
                f"Forge Forest Lantern screenshot is missing or linked: {path}"
            )
        if path.stat().st_size != screenshot["size"]:
            raise forge_client.E2EError(
                f"Forge Forest Lantern screenshot size differs from report: {path}"
            )
        if forge_evidence.sha256_file(path) != screenshot["sha256"]:
            raise forge_client.E2EError(
                f"Forge Forest Lantern screenshot digest differs from report: {path}"
            )
        image = forge_evidence.decode_png(path)
        if (image.width, image.height) != EXPECTED_FRAMEBUFFER_DIMENSIONS:
            raise forge_client.E2EError(
                f"Forge Forest Lantern screenshot dimensions changed: {path}"
            )
        forge_evidence.assert_image_is_not_blank(image, str(path))
        if count_missing_texture_pixels(image) != 0:
            raise forge_client.E2EError(
                f"Forge Forest Lantern screenshot contains missing-texture magenta: {path}"
            )
        images.append(image)

    changed_ratios = [
        forge_evidence.changed_pixel_ratio(left, right)
        for left, right in zip(images[:5], images[1:6])
    ]
    if len(changed_ratios) != 5 or any(
        ratio < MINIMUM_PLACEMENT_CHANGED_PIXEL_RATIO
        or ratio > MAXIMUM_PHASE_CHANGED_PIXEL_RATIO
        for ratio in changed_ratios
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern fixture captures lack a material visual transition"
        )
    reopened_ratio = structural_change_ratio(images[5], images[6])
    if reopened_ratio > MAXIMUM_REOPENED_STRUCTURAL_CHANGE_RATIO:
        raise forge_client.E2EError(
            "Forge Forest Lantern reopened frame does not preserve fixture structure: "
            f"{reopened_ratio:.6f}"
        )
    return min(changed_ratios)


def validate_report_contract(
    scenario_root: Path,
    report: dict[str, object],
) -> tuple[float, dict[str, dict[str, object]]]:
    """Validates every scenario-specific report and screenshot field."""

    if set(report) != EXPECTED_REPORT_FIELDS:
        raise forge_client.E2EError("Forge Forest Lantern report field inventory changed")
    if (
        report.get("schema") != 2
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
        or report.get("framebuffer_width") != EXPECTED_FRAMEBUFFER_DIMENSIONS[0]
        or report.get("framebuffer_height") != EXPECTED_FRAMEBUFFER_DIMENSIONS[1]
        or report.get("world") != EXPECTED_WORLD
        or report.get("ready_resources") != list(EXPECTED_READY_RESOURCES)
    ):
        raise forge_client.E2EError("Forge Forest Lantern report lifecycle contract is invalid")
    validate_forest_lantern(report)
    artifacts = validate_artifact_inventory(report)
    screenshots = validate_screenshot_inventory(report)
    validate_assertions(report, screenshots)
    ratio = validate_screenshot_files(scenario_root, screenshots)
    return ratio, artifacts


def validate_live_artifacts(
    configuration: forge_client.ResolvedConfiguration,
    runtime: Path,
    report_artifacts: dict[str, dict[str, object]],
) -> tuple[str, str]:
    """Binds report provenance to the exact staged JARs and their lock."""

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
            "Forge Forest Lantern evidence has no exact artifact lock"
        )
    locked_artifacts = lock.get("artifacts")
    if not isinstance(locked_artifacts, dict) or set(locked_artifacts) != {
        "production",
        "harness",
    }:
        raise forge_client.E2EError(
            "Forge Forest Lantern artifact lock inventory changed"
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
                f"Forge Forest Lantern {role} artifact differs from its lock"
            )
    return (
        str(report_artifacts["production"]["sha256"]),
        str(report_artifacts["harness"]["sha256"]),
    )


def validate_game_lifecycle(
    configuration: forge_client.ResolvedConfiguration,
    runtime: Path,
    report: dict[str, object],
) -> None:
    """Requires a stopped, non-crashed client and the exact isolated save."""

    game = forge_client.game_directory(configuration, runtime)
    crash_reports = game / "crash-reports"
    if not crash_reports.is_dir() or crash_reports.is_symlink():
        raise forge_client.E2EError(
            "Forge Forest Lantern crash-report directory is missing or linked"
        )
    if any(crash_reports.iterdir()):
        raise forge_client.E2EError(
            f"Forge Forest Lantern runtime contains a crash report: {crash_reports}"
        )
    latest_log = game / "logs" / "latest.log"
    if not latest_log.is_file() or latest_log.is_symlink():
        raise forge_client.E2EError(
            f"Forge Forest Lantern game log is missing or linked: {latest_log}"
        )
    if latest_log.stat().st_size > forge_client.MAXIMUM_PROCESS_LOG_SIZE:
        raise forge_client.E2EError(
            "Forge Forest Lantern game log exceeds the size bound"
        )
    log_content = latest_log.read_text(encoding="utf-8", errors="replace")
    fatal_marker = next(
        (marker for marker in FATAL_GAME_LOG_MARKERS if marker in log_content),
        None,
    )
    if fatal_marker is not None:
        raise forge_client.E2EError(
            f"Forge Forest Lantern game log contains fatal marker: {fatal_marker}"
        )
    if "Etherology forest-lantern evidence is complete:" not in log_content:
        raise forge_client.E2EError(
            "Forge Forest Lantern game log has no completion marker"
        )
    if "Stopping!" not in log_content:
        raise forge_client.E2EError(
            "Forge Forest Lantern client did not record a normal shutdown"
        )
    save = game / "saves" / str(EXPECTED_WORLD["save_directory"])
    if not save.is_dir() or save.is_symlink():
        raise forge_client.E2EError(
            f"Forge Forest Lantern world save is missing or linked: {save}"
        )
    if report.get("passed") is not True:
        raise forge_client.E2EError("Forge Forest Lantern lifecycle did not pass")


def validate_live_evidence(
    configuration: forge_client.ResolvedConfiguration | None = None,
    runtime: Path | None = None,
) -> ForestLanternEvidenceSummary:
    """Validates the exact live v12 capture without launching or changing it."""

    resolved_configuration = configuration or forge_client.load_configuration()
    validate_active_profile(resolved_configuration)
    target_runtime = runtime or forge_client.runtime_root(resolved_configuration)
    scenario_id = forge_client.resolve_scenario_id(
        resolved_configuration,
        SCENARIO_ID,
    )
    if scenario_id != SCENARIO_ID:
        raise forge_client.E2EError("Forge Forest Lantern scenario selection changed")
    forge_client.verify_evidence_layout(resolved_configuration, target_runtime)
    scenario_root = (
        forge_client.evidence_root(resolved_configuration, target_runtime) / SCENARIO_ID
    )
    reports_directory = scenario_root / "reports"
    if (
        not reports_directory.is_dir()
        or reports_directory.is_symlink()
        or {path.name for path in reports_directory.iterdir()}
        != {"report.json", "done.marker"}
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern report directory inventory changed"
        )
    report_path = reports_directory / "report.json"
    done_path = reports_directory / "done.marker"
    if report_path.is_symlink() or done_path.is_symlink():
        raise forge_client.E2EError("Forge Forest Lantern report or marker is linked")
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError("Forge Forest Lantern completion marker is invalid")

    report = forge_evidence.require_json_object(
        report_path,
        "Forge Forest Lantern scenario report",
    )
    ratio, artifacts = validate_report_contract(scenario_root, report)
    assertion_count = len(EXPECTED_ASSERTION_NAMES)
    production_digest, harness_digest = validate_live_artifacts(
        resolved_configuration,
        target_runtime,
        artifacts,
    )
    if (
        assertion_count != len(EXPECTED_ASSERTION_NAMES)
        or production_digest != artifacts["production"]["sha256"]
        or harness_digest != artifacts["harness"]["sha256"]
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern strict evidence differs from artifact validation"
        )

    screenshot_paths = [
        scenario_root / str(screenshot["file"])
        for screenshot in report["screenshots"]
    ]
    if any(
        done_path.stat().st_mtime_ns < path.stat().st_mtime_ns
        for path in (report_path, *screenshot_paths)
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern completion predates its evidence payload"
        )
    scenario_size = sum(
        path.stat().st_size
        for path in scenario_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    )
    if scenario_size > MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise forge_client.E2EError("Forge Forest Lantern evidence exceeds the size bound")
    validate_game_lifecycle(
        resolved_configuration,
        target_runtime,
        report,
    )
    return ForestLanternEvidenceSummary(
        profile_id=PROFILE_ID,
        assertion_count=assertion_count,
        screenshot_count=len(EXPECTED_SCREENSHOTS),
        minimum_changed_pixel_ratio=ratio,
        production_sha256=production_digest,
        harness_sha256=harness_digest,
    )


def validate_archive_inventory(
    archive_root: Path,
    include_manifest: bool = True,
) -> None:
    if not archive_root.is_dir() or archive_root.is_symlink():
        raise forge_client.E2EError(
            f"Forge Forest Lantern evidence archive is missing or linked: {archive_root}"
        )
    if archive_root.name != ARCHIVE_DIRECTORY_NAME:
        raise forge_client.E2EError(
            "Forge Forest Lantern archive directory does not identify profile v12"
        )
    files: set[str] = set()
    directories: set[str] = set()
    for path in archive_root.rglob("*"):
        relative_path = path.relative_to(archive_root).as_posix()
        if path.is_symlink():
            raise forge_client.E2EError(
                f"Forge Forest Lantern archive contains a linked entry: {relative_path}"
            )
        if path.is_file():
            files.add(relative_path)
        elif path.is_dir():
            directories.add(relative_path)
        else:
            raise forge_client.E2EError(
                f"Forge Forest Lantern archive contains a special entry: {relative_path}"
            )
    expected_files = set(ARCHIVE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    if files != expected_files or directories != ARCHIVE_DIRECTORIES:
        raise forge_client.E2EError("Forge Forest Lantern archive inventory changed")
    archive_size = sum(
        path.stat().st_size
        for path in archive_root.rglob("*")
        if path.is_file()
    )
    if archive_size > MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        raise forge_client.E2EError("Forge Forest Lantern archive exceeds the size bound")


def expected_repository_archive_root(
    configuration: forge_client.ResolvedConfiguration,
) -> Path:
    return forge_client.safe_repository_path(
        configuration.repository_root,
        f"docs/evidence/forge-1.20.1/{ARCHIVE_DIRECTORY_NAME}",
        "Forge Forest Lantern evidence archive",
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
        raise forge_client.E2EError(
            "Forge Forest Lantern capture has no exact artifact lock"
        )
    locked_artifacts = lock.get("artifacts")
    if not isinstance(locked_artifacts, dict) or set(locked_artifacts) != {
        "production",
        "harness",
    }:
        raise forge_client.E2EError(
            "Forge Forest Lantern capture artifact lock inventory changed"
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
                f"Forge Forest Lantern capture {role} artifact lock differs from report"
            )
    lock_path = forge_client.artifact_lock_path(configuration, capture_runtime)
    if not lock_path.is_file() or lock_path.is_symlink():
        raise forge_client.E2EError(
            f"Forge Forest Lantern artifact lock is missing or linked: {lock_path}"
        )
    return lock_path


def build_archive_manifest(
    configuration: forge_client.ResolvedConfiguration,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> dict[str, object]:
    """Builds a seal only from the exact live v12 capture and copied payload."""

    validate_active_profile(configuration)
    expected_profile_path = configuration.repository_root / ACTIVE_PROFILE_RELATIVE_PATH
    if (
        profile_manifest_path.resolve() != expected_profile_path.resolve()
        or not profile_manifest_path.is_file()
        or profile_manifest_path.is_symlink()
        or profile_manifest_path.stat().st_size != PROFILE_SIZE
        or forge_client.sha256_file(profile_manifest_path) != PROFILE_SHA256
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern sealing requires the exact active v12 profile"
        )
    validate_archive_inventory(archive_root, include_manifest=False)
    if archive_root.resolve() != expected_repository_archive_root(configuration).resolve():
        raise forge_client.E2EError(
            "Forge Forest Lantern sealing requires the repository archive destination"
        )

    expected_capture_runtime = forge_client.runtime_root(configuration)
    forge_client.ensure_owned_state_roots()
    if (
        capture_runtime.resolve() != expected_capture_runtime.resolve()
        or not capture_runtime.is_dir()
        or capture_runtime.is_symlink()
        or capture_runtime.name != PROFILE_ID
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern sealing requires the exact owned v12 runtime"
        )
    forge_client.verify_runtime(configuration, capture_runtime, artifact_policy="optional")
    live_summary = validate_live_evidence(configuration, capture_runtime)

    archive_report = forge_evidence.require_json_object(
        archive_root / "reports" / "report.json",
        "Forge Forest Lantern archived scenario report",
    )
    ratio, report_artifacts = validate_report_contract(archive_root, archive_report)
    if (
        live_summary.assertion_count != len(EXPECTED_ASSERTION_NAMES)
        or live_summary.screenshot_count != len(EXPECTED_SCREENSHOTS)
        or abs(live_summary.minimum_changed_pixel_ratio - ratio) > 1e-12
        or live_summary.production_sha256 != report_artifacts["production"]["sha256"]
        or live_summary.harness_sha256 != report_artifacts["harness"]["sha256"]
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern archive differs from strict live validation"
        )

    capture_scenario_root = (
        forge_client.evidence_root(configuration, capture_runtime) / SCENARIO_ID
    )
    capture_mtime_ns: dict[str, int] = {}
    files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        archive_path = archive_root / relative_path
        capture_path = capture_scenario_root / relative_path
        if not capture_path.is_file() or capture_path.is_symlink():
            raise forge_client.E2EError(
                f"Forge Forest Lantern capture payload is missing or linked: {capture_path}"
            )
        if (
            archive_path.stat().st_size != capture_path.stat().st_size
            or forge_evidence.sha256_file(archive_path) != forge_evidence.sha256_file(capture_path)
        ):
            raise forge_client.E2EError(
                "Forge Forest Lantern archive payload differs from capture: "
                f"{relative_path}"
            )
        capture_mtime_ns[relative_path] = capture_path.stat().st_mtime_ns
        files[relative_path] = {
            "size": archive_path.stat().st_size,
            "sha256": forge_evidence.sha256_file(archive_path),
        }
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[relative_path]
        for relative_path in ARCHIVE_PAYLOAD_PATHS
        if relative_path != "reports/done.marker"
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern capture completion predates copied evidence"
        )
    artifact_lock_path = validate_capture_artifact_lock(
        configuration,
        capture_runtime,
        report_artifacts,
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
            "framebuffer_width": EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
            "framebuffer_height": EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
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
        raise forge_client.E2EError(
            f"Forge Forest Lantern archive manifest already exists: {manifest_path}"
        )
    manifest = build_archive_manifest(
        configuration,
        profile_manifest_path,
        capture_runtime,
        archive_root,
    )
    temporary_path: Path | None = None
    try:
        descriptor, raw_temporary_path = tempfile.mkstemp(
            prefix=".forest-lantern-archive-manifest.",
            dir=archive_root,
        )
        temporary_path = Path(raw_temporary_path)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(manifest, handle, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        try:
            os.link(temporary_path, manifest_path, follow_symlinks=False)
        except FileExistsError as exception:
            raise forge_client.E2EError(
                f"Forge Forest Lantern archive manifest already exists: {manifest_path}"
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
    archive_root: Path,
    manifest: dict[str, object],
) -> tuple[dict[str, dict[str, object]], dict[str, dict[str, object]]]:
    if set(manifest) != {
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
    }:
        raise forge_client.E2EError("Forge Forest Lantern archive manifest fields changed")
    if (
        manifest.get("schema") != 1
        or manifest.get("kind") != ARCHIVE_KIND
        or manifest.get("verification_scope") != ARCHIVE_VERIFICATION_SCOPE
        or manifest.get("scenario") != SCENARIO_ID
        or manifest.get("assertion_count") != len(EXPECTED_ASSERTION_NAMES)
        or manifest.get("screenshot_count") != len(EXPECTED_SCREENSHOTS)
    ):
        raise forge_client.E2EError("Forge Forest Lantern archive identity is invalid")

    profile = manifest.get("profile")
    if not isinstance(profile, dict) or profile != {
        "id": PROFILE_ID,
        "manifest_path": ACTIVE_PROFILE_RELATIVE_PATH,
        "manifest_size": PROFILE_SIZE,
        "manifest_sha256": PROFILE_SHA256,
    }:
        raise forge_client.E2EError("Forge Forest Lantern archive profile is invalid")
    expected_runtime = {
        "artifact_node": "forge-1.20.1",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "capture_kind": "composed-minecraft-framebuffer",
        "framebuffer_width": EXPECTED_FRAMEBUFFER_DIMENSIONS[0],
        "framebuffer_height": EXPECTED_FRAMEBUFFER_DIMENSIONS[1],
    }
    if manifest.get("runtime") != expected_runtime:
        raise forge_client.E2EError("Forge Forest Lantern archive runtime is invalid")

    publication = manifest.get("publication")
    if not isinstance(publication, dict) or set(publication) != {
        *ARCHIVE_PUBLICATION_ATTESTATION,
        "capture_mtime_ns",
    }:
        raise forge_client.E2EError("Forge Forest Lantern archive publication is malformed")
    if any(
        publication.get(key) != value
        for key, value in ARCHIVE_PUBLICATION_ATTESTATION.items()
    ):
        raise forge_client.E2EError("Forge Forest Lantern archive publication is invalid")
    capture_mtime_ns = publication.get("capture_mtime_ns")
    if (
        not isinstance(capture_mtime_ns, dict)
        or set(capture_mtime_ns) != set(ARCHIVE_PAYLOAD_PATHS)
        or any(
            type(value) is not int or value <= 0
            for value in capture_mtime_ns.values()
        )
    ):
        raise forge_client.E2EError("Forge Forest Lantern archive timestamps are invalid")
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[path]
        for path in ARCHIVE_PAYLOAD_PATHS
        if path != "reports/done.marker"
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern archive completion predates its payload"
        )

    capture_metadata = manifest.get("capture_metadata")
    if (
        not isinstance(capture_metadata, dict)
        or set(capture_metadata) != {"path", "size", "sha256"}
        or capture_metadata.get("path") != ARCHIVE_CAPTURE_METADATA_PATH
        or type(capture_metadata.get("size")) is not int
        or int(capture_metadata["size"]) <= 0
    ):
        raise forge_client.E2EError(
            "Forge Forest Lantern archive capture metadata is invalid"
        )
    forge_client.validate_hex_digest(
        capture_metadata.get("sha256"),
        "Forge Forest Lantern archived capture metadata",
    )

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict) or list(artifacts) != [
        "production",
        "harness",
    ]:
        raise forge_client.E2EError("Forge Forest Lantern archive artifacts changed")
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
                f"Forge Forest Lantern archived {role} artifact is invalid"
            )
        forge_client.validate_hex_digest(
            artifact.get("sha256"),
            f"Forge Forest Lantern archived {role} artifact",
        )
        validated_artifacts[role] = artifact

    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(ARCHIVE_PAYLOAD_PATHS):
        raise forge_client.E2EError("Forge Forest Lantern archive payloads changed")
    validated_files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if (
            not isinstance(record, dict)
            or set(record) != {"size", "sha256"}
            or type(record.get("size")) is not int
            or int(record["size"]) <= 0
        ):
            raise forge_client.E2EError(
                f"Forge Forest Lantern payload record is invalid: {relative_path}"
            )
        forge_client.validate_hex_digest(
            record.get("sha256"),
            f"Forge Forest Lantern archived payload {relative_path}",
        )
        path = archive_root / relative_path
        if (
            path.stat().st_size != record["size"]
            or forge_evidence.sha256_file(path) != record["sha256"]
        ):
            raise forge_client.E2EError(
                "Forge Forest Lantern payload differs from manifest: "
                f"{relative_path}"
            )
        validated_files[relative_path] = record
    return validated_artifacts, validated_files


def validate_archived_evidence(
    archive_root: Path,
) -> ForestLanternEvidenceSummary:
    """Validates a self-contained v12 archive without consulting live state."""

    validate_archive_inventory(archive_root)
    manifest = forge_evidence.require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME,
        "Forge Forest Lantern archive manifest",
    )
    manifest_artifacts, _manifest_files = validate_archive_manifest(
        archive_root,
        manifest,
    )
    done_path = archive_root / "reports" / "done.marker"
    if done_path.read_text(encoding="utf-8") != "complete\n":
        raise forge_client.E2EError("Forge Forest Lantern completion marker is invalid")
    report = forge_evidence.require_json_object(
        archive_root / "reports" / "report.json",
        "Forge Forest Lantern archived scenario report",
    )
    ratio, report_artifacts = validate_report_contract(archive_root, report)
    if report_artifacts != manifest_artifacts:
        raise forge_client.E2EError(
            "Forge Forest Lantern report artifacts differ from manifest"
        )
    return ForestLanternEvidenceSummary(
        profile_id=PROFILE_ID,
        assertion_count=len(EXPECTED_ASSERTION_NAMES),
        screenshot_count=len(EXPECTED_SCREENSHOTS),
        minimum_changed_pixel_ratio=ratio,
        production_sha256=str(report_artifacts["production"]["sha256"]),
        harness_sha256=str(report_artifacts["harness"]["sha256"]),
    )


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate exact Forge 1.20.1 Forest Lantern evidence."
    )
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument(
        "--live",
        action="store_true",
        help="validate the repository-owned v12 capture runtime",
    )
    operation.add_argument(
        "--archive",
        type=Path,
        help="validate one frozen v12 archive without live state",
    )
    operation.add_argument(
        "--create-archive-manifest",
        type=Path,
        metavar="ARCHIVE",
        help="seal copied v12 payload from one explicit owned runtime",
    )
    parser.add_argument(
        "--capture-runtime",
        type=Path,
        help="exact repository-owned runtime used only while sealing",
    )
    parser.add_argument(
        "--profile-manifest",
        type=Path,
        help="exact active v12 manifest used only while sealing",
    )
    arguments = parser.parse_args()
    sealing = arguments.create_archive_manifest is not None
    auxiliary_arguments = (arguments.capture_runtime, arguments.profile_manifest)
    if sealing and any(value is None for value in auxiliary_arguments):
        parser.error(
            "--create-archive-manifest requires --capture-runtime and "
            "--profile-manifest"
        )
    if not sealing and any(value is not None for value in auxiliary_arguments):
        parser.error(
            "--capture-runtime and --profile-manifest are valid only with "
            "--create-archive-manifest"
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
        "minimum placement-transition ratio "
        f"{summary.minimum_changed_pixel_ratio:.6f}"
    )
    print(f"Production SHA-256: {summary.production_sha256}")
    print(f"Harness SHA-256: {summary.harness_sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
