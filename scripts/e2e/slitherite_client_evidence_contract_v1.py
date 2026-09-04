#!/usr/bin/env python3
"""Shared immutable verifier core for the first 1.20.1 Slitherite client lanes."""

from __future__ import annotations

import copy
from dataclasses import dataclass
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from types import ModuleType


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
BASELINE_VERIFIER_PATH = (
    SCRIPT_DIRECTORY.parent / "baseline" / "original_slitherite_evidence_v10.py"
)
BASELINE_VERIFIER_SIZE = 49900
BASELINE_VERIFIER_SHA256 = (
    "268eb1ec8b1dc128f5909cd2525bb874c2175f035d43bc90784e205b8e681f39"
)
SCENARIO_ID = "slitherite-block-registry"
REFERENCE_ID = "published-0.1.7"
PHASES = ("initial", "reopened")
SCREENSHOT_FILES = tuple(f"{SCENARIO_ID}-{phase}.png" for phase in PHASES)
EXPECTED_SCREENSHOTS = tuple(
    (phase, f"screenshots/{file_name}")
    for phase, file_name in zip(PHASES, SCREENSHOT_FILES, strict=True)
)
EXPECTED_FRAMEBUFFER_DIMENSIONS = (1920, 1080)
REQUIRED_STABLE_RENDERS = 120
EXPECTED_WORLD = {
    "save_directory": "etherology-slitherite-block-registry-world",
    "display_name": "Etherology Slitherite Blocks",
    "seed": 4995697409260082224,
    "dimension": "minecraft:overworld",
    "integrated": True,
    "reopened": True,
}
EXPECTED_ARTIFACT_ROLES = ("production", "harness")
ARCHIVE_MANIFEST_NAME = "archive-manifest.json"
ARCHIVE_DIRECTORIES = {"reports", "screenshots"}
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
ARCHIVE_DIRECTORY_PATTERN = re.compile(
    rf"{re.escape(SCENARIO_ID)}-v[1-9][0-9]*"
)
REGISTRY_PATTERN = re.compile(
    r"^(etherology:[a-z0-9_]+)=block_class:([^,]+),"
    r"item_class:([^,]+),default:(\{[^}]*\}),states:([0-9]+),"
    r"default_raw_id:([0-9]+),raw_ids:([0-9]+)$"
)
ASSERTION_FIELDS = ("name", "passed", "expected", "actual")
SCREENSHOT_FIELDS = (
    "step",
    "file",
    "width",
    "height",
    "size",
    "sha256",
    "completed_render_count",
    "source",
    "edited",
)
ARTIFACT_FIELDS = (
    "mod_id",
    "origin_kind",
    "file_name",
    "size",
    "sha256",
)
FATAL_SLITHERITE_LOG_MARKERS = (
    "Slitherite lifecycle failure:",
    "Slitherite scenario failed",
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_baseline_verifier() -> ModuleType:
    """Loads the exact accepted v10 semantics used as the porting oracle."""

    if (
        not BASELINE_VERIFIER_PATH.is_file()
        or BASELINE_VERIFIER_PATH.is_symlink()
        or BASELINE_VERIFIER_PATH.stat().st_size != BASELINE_VERIFIER_SIZE
        or sha256_file(BASELINE_VERIFIER_PATH) != BASELINE_VERIFIER_SHA256
    ):
        raise RuntimeError("The accepted original Slitherite v10 verifier changed")
    specification = importlib.util.spec_from_file_location(
        "etherology_original_slitherite_evidence_v10_contract_v1",
        BASELINE_VERIFIER_PATH,
    )
    if specification is None or specification.loader is None:
        raise RuntimeError(
            f"Cannot load the accepted Slitherite verifier: {BASELINE_VERIFIER_PATH}"
        )
    module = importlib.util.module_from_spec(specification)
    sys.modules[specification.name] = module
    specification.loader.exec_module(module)
    return module


BASELINE = load_baseline_verifier()
BLOCK_IDS = tuple(BASELINE.BLOCK_IDS)
EXPECTED_RESOURCES = tuple(BASELINE.EXPECTED_RESOURCES)
EXPECTED_TAGS = tuple(BASELINE.EXPECTED_TAGS)
EXPECTED_LOOT_TABLES = tuple(BASELINE.EXPECTED_LOOT_TABLES)
EXPECTED_SELF_DROPS = dict(BASELINE.EXPECTED_SELF_DROPS)
EXPECTED_DOUBLE_SLAB_DROPS = dict(BASELINE.EXPECTED_DOUBLE_SLAB_DROPS)
EXPECTED_OWNED_RECIPES = tuple(BASELINE.EXPECTED_OWNED_RECIPES)
EXPECTED_OWNED_ADVANCEMENTS = tuple(BASELINE.EXPECTED_OWNED_ADVANCEMENTS)
EXPECTED_RELATED_RECIPES = tuple(BASELINE.EXPECTED_RELATED_RECIPES)
EXPECTED_SLITHERITE_FIELDS = set(BASELINE.EXPECTED_SLITHERITE_FIELDS)


@dataclass(frozen=True)
class LoaderContract:
    """Carries the immutable differences between the Fabric and Forge captures."""

    label: str
    profile_id: str
    active_profile_relative_path: str
    snapshot_profile_relative_path: str
    profile_size: int
    profile_sha256: str
    scenarios: tuple[str, ...]
    artifact_node: str
    loader: str
    loader_version: str
    lane: str
    report_fields: tuple[str, ...]
    artifact_origin_kind: str
    production_file_name: str
    harness_file_name: str
    block_classes: tuple[str, ...]
    block_item_class: str
    archive_directory_name: str
    archive_parent_relative_path: str
    artifact_lock_name: str
    artifact_lock_schema: int
    archive_kind: str
    archive_verification_scope: str
    controller: ModuleType
    evidence: ModuleType
    harness_size: int | None
    harness_sha256: str | None

    @property
    def artifacts(self) -> tuple[tuple[str, str, str], ...]:
        """Returns the ordered root-JAR identities authored by the profile."""

        return (
            ("production", "etherology", self.production_file_name),
            ("harness", "etherology_e2e_harness", self.harness_file_name),
        )

    @property
    def assertion_names(self) -> tuple[str, ...]:
        """Returns the shared 185 assertions with loader-owned names substituted."""

        names = list(BASELINE.EXPECTED_ASSERTION_NAMES)
        names[0] = f"{self.loader}_mod_loaded:etherology"
        baseline_harness = "packaged_root_jar:etherology_original_baseline_harness"
        names[names.index(baseline_harness)] = (
            "packaged_root_jar:etherology_e2e_harness"
        )
        return tuple(names)


@dataclass(frozen=True)
class SlitheriteEvidenceSummary:
    """Reports the exact semantic, visual, and artifact evidence accepted."""

    profile_id: str
    assertion_count: int
    screenshot_count: int
    initial_mean_luminance: float
    reopened_mean_luminance: float
    reopen_material_changed_pixel_ratio: float
    reopen_mean_max_channel_delta: float
    production_sha256: str
    harness_sha256: str


def fail(contract: LoaderContract, message: str) -> None:
    raise contract.controller.E2EError(
        f"{contract.label} Slitherite evidence: {message}"
    )


def require_harness_pin(contract: LoaderContract) -> tuple[int, str]:
    """Refuses native or archival verification until the built harness is pinned."""

    size = contract.harness_size
    digest = contract.harness_sha256
    if (
        type(size) is not int
        or size <= 0
        or not isinstance(digest, str)
        or re.fullmatch(r"[0-9a-f]{64}", digest) is None
    ):
        fail(
            contract,
            "set HARNESS_SIZE and HARNESS_SHA256 from the final packaged harness "
            "before native verification",
        )
    return size, digest


def validate_active_profile(contract: LoaderContract, configuration: object) -> None:
    """Requires the active profile to equal its immutable versioned snapshot."""

    repository_root = configuration.repository_root
    active_profile = repository_root / contract.active_profile_relative_path
    snapshot_profile = repository_root / contract.snapshot_profile_relative_path
    for path in (active_profile, snapshot_profile):
        if not path.is_file() or path.is_symlink():
            fail(contract, f"profile is missing or linked: {path}")
        if (
            path.stat().st_size != contract.profile_size
            or sha256_file(path) != contract.profile_sha256
        ):
            fail(contract, f"profile bytes changed: {path}")
    if active_profile.read_bytes() != snapshot_profile.read_bytes():
        fail(contract, "the active profile differs from its versioned snapshot")

    controller = contract.controller
    profile = controller.profile_spec(configuration)
    capture = controller.require_object(
        controller.evidence_descriptor(configuration), "capture"
    )
    expected_runtime_loader_version = (
        contract.loader_version
        if contract.loader == "fabric"
        else f"1.20.1-{contract.loader_version}"
    )
    if (
        profile.get("id") != contract.profile_id
        or profile.get("runtime_directory") != contract.profile_id
        or tuple(controller.scenario_ids(configuration)) != contract.scenarios
        or configuration.artifact_lane.get("artifact_node")
        != contract.artifact_node
        or configuration.runtime_lane.get("runtime_version") != "1.20.1"
        or configuration.runtime_lane.get("loader") != contract.loader
        or configuration.runtime_lane.get("loader_version")
        != expected_runtime_loader_version
        or configuration.runtime_lane.get("java") != 17
        or capture.get("kind") != "composed-minecraft-framebuffer"
        or (capture.get("width"), capture.get("height"))
        != EXPECTED_FRAMEBUFFER_DIMENSIONS
    ):
        fail(contract, "profile runtime contract changed")
    for role, mod_id, file_name in contract.artifacts:
        artifact = controller.artifact_spec(configuration, role)
        if artifact.get("mod_id") != mod_id or artifact.get("file_name") != file_name:
            fail(contract, f"profile has the wrong {role} artifact identity")
    validate_canonical_resource_sources(contract, repository_root)


def validate_canonical_resource_sources(
    contract: LoaderContract, repository_root: Path
) -> None:
    """Requires all 79 Etherology visual assets named by the report to be regular."""

    mod_resources = [
        identifier
        for identifier in EXPECTED_RESOURCES
        if identifier.startswith("etherology:")
    ]
    if len(mod_resources) != 79 or len(set(mod_resources)) != 79:
        fail(contract, "canonical resource inventory is not exactly 79 assets")
    for identifier in mod_resources:
        namespace, relative_path = identifier.split(":", 1)
        candidates = tuple(
            source_root / namespace / relative_path
            for source_root in (
                repository_root / "src/client/resources/assets",
                repository_root / "src/main/generated/assets",
            )
            if (source_root / namespace / relative_path).exists()
            or (source_root / namespace / relative_path).is_symlink()
        )
        if (
            len(candidates) != 1
            or not candidates[0].is_file()
            or candidates[0].is_symlink()
            or candidates[0].stat().st_size <= 0
        ):
            fail(
                contract,
                f"canonical resource must have one regular non-empty source: "
                f"{identifier}",
            )


def validate_artifact_inventory(
    contract: LoaderContract, report: dict[str, object]
) -> dict[str, dict[str, object]]:
    """Validates typed artifact identities and optional final harness pin."""

    artifacts = report.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) != len(contract.artifacts):
        fail(contract, "artifact inventory changed")
    validated: dict[str, dict[str, object]] = {}
    for artifact, (role, mod_id, file_name) in zip(
        artifacts, contract.artifacts, strict=True
    ):
        if not isinstance(artifact, dict) or tuple(artifact) != ARTIFACT_FIELDS:
            fail(contract, f"{role} artifact provenance is malformed")
        if (
            artifact.get("mod_id") != mod_id
            or artifact.get("origin_kind") != contract.artifact_origin_kind
            or artifact.get("file_name") != file_name
            or type(artifact.get("size")) is not int
            or int(artifact["size"]) <= 0
        ):
            fail(contract, f"{role} artifact identity is invalid")
        digest = contract.controller.validate_hex_digest(
            artifact.get("sha256"), f"{contract.label} Slitherite {role} artifact"
        )
        if role == "harness" and contract.harness_size is not None:
            expected_size, expected_digest = require_harness_pin(contract)
            if artifact["size"] != expected_size or digest != expected_digest:
                fail(contract, "harness artifact differs from its packaged pin")
        validated[role] = {
            "mod_id": mod_id,
            "file_name": file_name,
            "size": artifact["size"],
            "sha256": digest,
        }
    return validated


def validate_current_registry(
    contract: LoaderContract, registry: object
) -> list[str]:
    """Validates loader runtime class names and projects them to the v10 oracle."""

    if (
        not isinstance(registry, list)
        or len(registry) != len(BLOCK_IDS)
        or len(contract.block_classes) != len(BLOCK_IDS)
    ):
        fail(contract, "registry inventory is not the ordered 17-member family")
    projected: list[str] = []
    network_ids: list[int] = []
    for value, baseline_spec, expected_class in zip(
        registry, BASELINE.BLOCK_SPECS, contract.block_classes, strict=True
    ):
        if not isinstance(value, str):
            fail(contract, "registry entry is not text")
        match = REGISTRY_PATTERN.fullmatch(value)
        path, baseline_class, default_state, state_count = baseline_spec
        block_id = f"etherology:{path}"
        if (
            match is None
            or match.group(1) != block_id
            or match.group(2) != expected_class
            or match.group(3) != contract.block_item_class
            or BASELINE._parse_java_map(
                match.group(4), block_id, contract.controller.E2EError
            )
            != BASELINE._default_properties(default_state)
            or int(match.group(5)) != state_count
            or int(match.group(7)) != state_count
        ):
            fail(contract, f"registry contract changed for {block_id}")
        network_ids.append(int(match.group(6)))
        projected.append(
            f"{block_id}=block_class:{baseline_class},"
            f"item_class:{BASELINE.BLOCK_ITEM_CLASS},default:{match.group(4)},"
            f"states:{match.group(5)},default_raw_id:{match.group(6)},"
            f"raw_ids:{match.group(7)}"
        )
    if len(set(network_ids)) != len(network_ids):
        fail(contract, "default-state network ids are not unique")
    return projected


def validate_current_assertions(
    contract: LoaderContract, assertions: object
) -> list[dict[str, object]]:
    """Validates the exact loader-owned assertion inventory before projection."""

    expected_names = contract.assertion_names
    if not isinstance(assertions, list) or len(assertions) != len(expected_names):
        fail(contract, "assertion inventory is not exactly 185 entries")
    validated: list[dict[str, object]] = []
    for assertion, expected_name in zip(assertions, expected_names, strict=True):
        if (
            not isinstance(assertion, dict)
            or tuple(assertion) != ASSERTION_FIELDS
            or assertion.get("name") != expected_name
            or assertion.get("passed") is not True
            or not isinstance(assertion.get("expected"), str)
            or not isinstance(assertion.get("actual"), str)
        ):
            fail(contract, "assertion fields, order, or pass state changed")
        validated.append(assertion)
    if len({str(assertion["name"]) for assertion in validated}) != len(validated):
        fail(contract, "assertion names are duplicated")

    world_identity = (
        f"{EXPECTED_WORLD['display_name']};{EXPECTED_WORLD['seed']};"
        f"{EXPECTED_WORLD['dimension']}"
    )
    exact_assertions = {
        f"{contract.loader}_mod_loaded:etherology": ("loaded", "loaded"),
        "packaged_root_jar:etherology": (
            "one regular root JAR",
            "one regular root JAR",
        ),
        "packaged_root_jar:etherology_e2e_harness": (
            "one regular root JAR",
            "one regular root JAR",
        ),
        "live_world_identity": (world_identity, world_identity),
        "isolated_save_directory_present": (
            str(EXPECTED_WORLD["save_directory"]),
            str(EXPECTED_WORLD["save_directory"]),
        ),
    }
    by_name = {str(assertion["name"]): assertion for assertion in validated}
    for name, pair in exact_assertions.items():
        assertion = by_name[name]
        if (assertion["expected"], assertion["actual"]) != pair:
            fail(contract, f"assertion semantics changed: {name}")
    for block_id, block_class in zip(
        BLOCK_IDS, contract.block_classes, strict=True
    ):
        class_assertion = by_name[f"runtime:block_class:{block_id}"]
        item_assertion = by_name[f"runtime:block_item_class:{block_id}"]
        if (
            (class_assertion["expected"], class_assertion["actual"])
            != (block_class, block_class)
            or (item_assertion["expected"], item_assertion["actual"])
            != (contract.block_item_class, contract.block_item_class)
        ):
            fail(contract, f"runtime class assertion changed for {block_id}")
    return validated


def project_for_baseline(
    contract: LoaderContract, report: dict[str, object]
) -> dict[str, object]:
    """Projects only pre-validated loader identity differences to the v10 oracle."""

    projected_slitherite = copy.deepcopy(report["slitherite"])
    projected_slitherite["registry"] = validate_current_registry(
        contract, projected_slitherite.get("registry")
    )
    current_assertions = validate_current_assertions(
        contract, report.get("assertions")
    )
    baseline_classes = {
        block_id: block_class
        for block_id, (_path, block_class, _state, _count) in zip(
            BLOCK_IDS, BASELINE.BLOCK_SPECS, strict=True
        )
    }
    projected_assertions: list[dict[str, object]] = []
    for current in current_assertions:
        assertion = copy.deepcopy(current)
        name = str(assertion["name"])
        if name == f"{contract.loader}_mod_loaded:etherology":
            assertion["name"] = "fabric_mod_loaded:etherology"
        elif name == "packaged_root_jar:etherology_e2e_harness":
            assertion["name"] = (
                "packaged_root_jar:etherology_original_baseline_harness"
            )
        elif name == "live_world_identity":
            baseline_identity = (
                "Etherology Original 0.1.7 Slitherite Blocks;"
                "4995697409260082224;minecraft:overworld"
            )
            assertion["expected"] = baseline_identity
            assertion["actual"] = baseline_identity
        elif name == "isolated_save_directory_present":
            baseline_save = str(BASELINE.EXPECTED_WORLD["save_directory"])
            assertion["expected"] = baseline_save
            assertion["actual"] = baseline_save
        elif name.startswith("runtime:block_class:"):
            block_id = name.removeprefix("runtime:block_class:")
            assertion["expected"] = baseline_classes[block_id]
            assertion["actual"] = baseline_classes[block_id]
        elif name.startswith("runtime:block_item_class:"):
            assertion["expected"] = BASELINE.BLOCK_ITEM_CLASS
            assertion["actual"] = BASELINE.BLOCK_ITEM_CLASS
        projected_assertions.append(assertion)

    return {
        "schema": report["schema"],
        "reference_id": report["reference_id"],
        "scenario": report["scenario"],
        "lane": "fabric-1.21.1-original",
        "status": report["status"],
        "passed": report["passed"],
        "client_ticks": report["client_ticks"],
        "lifecycle_failure": report["lifecycle_failure"],
        "assertions": projected_assertions,
        "world": copy.deepcopy(BASELINE.EXPECTED_WORLD),
        "artifacts": copy.deepcopy(report["artifacts"]),
        "screenshots": copy.deepcopy(report["screenshots"]),
        "slitherite": projected_slitherite,
    }


def validate_slitherite_mechanics(
    contract: LoaderContract, projected_report: dict[str, object]
) -> dict[str, object]:
    """Applies every non-visual original v10 Slitherite semantic gate."""

    slitherite = projected_report.get("slitherite")
    if not isinstance(slitherite, dict) or set(slitherite) != EXPECTED_SLITHERITE_FIELDS:
        fail(contract, "Slitherite mechanics field inventory changed")
    BASELINE._validate_registry(slitherite["registry"], contract.controller.E2EError)
    BASELINE._validate_pre_setup_lighting(
        slitherite["pre_setup_lighting"], contract.controller.E2EError
    )
    if (
        slitherite["block_ids"] != list(BLOCK_IDS)
        or slitherite["aggregate_state_count"] != 1262
        or slitherite["canonical_resources"] != list(EXPECTED_RESOURCES)
        or len(EXPECTED_RESOURCES) != 80
        or slitherite["tags"] != list(EXPECTED_TAGS)
        or slitherite["loot_tables"] != list(EXPECTED_LOOT_TABLES)
        or slitherite["self_drops"] != EXPECTED_SELF_DROPS
        or slitherite["double_slab_drops"] != EXPECTED_DOUBLE_SLAB_DROPS
        or slitherite["owned_recipes"] != list(EXPECTED_OWNED_RECIPES)
        or slitherite["owned_advancements"]
        != list(EXPECTED_OWNED_ADVANCEMENTS)
        or slitherite["related_recipes_recorded_not_owned"]
        != list(EXPECTED_RELATED_RECIPES)
        or slitherite["pressure_plate_behavior"]
        != "item=false;living=true;reset=true"
        or slitherite["persistence_exact"] is not True
        or slitherite["reopened_data_exact"] is not True
        or slitherite["required_stable_renders"] != REQUIRED_STABLE_RENDERS
        or slitherite["required_lighting_ready_client_ticks"]
        != BASELINE.REQUIRED_LIGHTING_READY_CLIENT_TICKS
    ):
        fail(contract, "Slitherite family/data contract changed")
    BASELINE._validate_placements(
        slitherite["placements"], contract.controller.E2EError
    )
    BASELINE._validate_button(
        slitherite["button_behavior"], contract.controller.E2EError
    )
    BASELINE._validate_snapshot(
        slitherite["initial_snapshot"], contract.controller.E2EError
    )
    BASELINE._validate_snapshot(
        slitherite["reopened_snapshot"], contract.controller.E2EError
    )
    if slitherite["initial_snapshot"] != slitherite["reopened_snapshot"]:
        fail(contract, "reopened structural snapshot differs from initial")
    return slitherite


def validate_screenshot_inventory(
    contract: LoaderContract, report: dict[str, object]
) -> list[dict[str, object]]:
    """Validates the exact two native framebuffer report records."""

    screenshots = report.get("screenshots")
    if not isinstance(screenshots, list) or len(screenshots) != 2:
        fail(contract, "screenshot inventory changed")
    validated: list[dict[str, object]] = []
    for screenshot, (phase, relative_file) in zip(
        screenshots, EXPECTED_SCREENSHOTS, strict=True
    ):
        if not isinstance(screenshot, dict) or tuple(screenshot) != SCREENSHOT_FIELDS:
            fail(contract, f"{phase} screenshot provenance is malformed")
        if (
            screenshot.get("step") != phase
            or screenshot.get("file") != relative_file
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
            fail(contract, f"{phase} screenshot contract is invalid")
        contract.controller.validate_hex_digest(
            screenshot.get("sha256"),
            f"{contract.label} Slitherite {phase} screenshot",
        )
        validated.append(screenshot)
    return validated


def validate_screenshot_files(
    contract: LoaderContract,
    scenario_root: Path,
    screenshots: list[dict[str, object]],
) -> tuple[dict[str, tuple[Path, dict[str, object]]], dict[str, float], float, float]:
    """Validates native PNG bytes, visibility, textures, and restart stability."""

    screenshot_directory = scenario_root / "screenshots"
    if not screenshot_directory.is_dir() or screenshot_directory.is_symlink():
        fail(contract, "screenshot directory is missing or linked")
    entries = list(screenshot_directory.iterdir())
    if (
        {path.name for path in entries} != set(SCREENSHOT_FILES)
        or any(not path.is_file() or path.is_symlink() for path in entries)
    ):
        fail(contract, "screenshot file inventory changed")

    images: dict[str, object] = {}
    means: dict[str, float] = {}
    records: dict[str, tuple[Path, dict[str, object]]] = {}
    for screenshot, (phase, relative_file) in zip(
        screenshots, EXPECTED_SCREENSHOTS, strict=True
    ):
        path = scenario_root / relative_file
        if (
            path.stat().st_size != screenshot["size"]
            or sha256_file(path) != screenshot["sha256"]
        ):
            fail(contract, f"{phase} screenshot differs from its report")
        image = (
            contract.evidence.decode_png(path, EXPECTED_FRAMEBUFFER_DIMENSIONS)
            if contract.loader == "fabric"
            else contract.evidence.decode_png(path)
        )
        contract.evidence.assert_image_is_not_blank(image, str(path))
        if (image.width, image.height) != EXPECTED_FRAMEBUFFER_DIMENSIONS:
            fail(contract, f"{phase} screenshot is not 1920x1080")
        means[phase], _dark_ratio = BASELINE.image_statistics(
            image, phase, contract.controller.E2EError
        )
        images[phase] = image
        records[phase] = (path, screenshot)
    material_ratio, mean_maximum_channel_delta = BASELINE.visual_drift_statistics(
        images["initial"], images["reopened"], contract.controller.E2EError
    )
    return records, means, material_ratio, mean_maximum_channel_delta


def validate_report_contract(
    contract: LoaderContract,
    scenario_root: Path,
    report: dict[str, object],
) -> tuple[
    float,
    float,
    float,
    float,
    dict[str, dict[str, object]],
]:
    """Validates every shared and loader-owned report field without a live world."""

    world = report.get("world")
    expected_identity = {
        "schema": 3,
        "reference_id": REFERENCE_ID,
        "scenario": SCENARIO_ID,
        "profile_id": contract.profile_id,
        "artifact_node": contract.artifact_node,
        "minecraft": "1.20.1",
        "loader": contract.loader,
        "loader_version": contract.loader_version,
        "java": 17,
        "lane": contract.lane,
        "role": "host",
        "status": "passed",
        "passed": True,
        "lifecycle_failure": "",
    }
    if (
        tuple(report) != contract.report_fields
        or any(report.get(key) != value for key, value in expected_identity.items())
        or type(report.get("client_ticks")) is not int
        or int(report["client_ticks"]) <= 0
        or not isinstance(world, dict)
        or tuple(world) != tuple(EXPECTED_WORLD)
        or world != EXPECTED_WORLD
        or type(world.get("seed")) is not int
        or type(world.get("integrated")) is not bool
        or type(world.get("reopened")) is not bool
    ):
        fail(contract, "report lifecycle contract is invalid")
    if contract.loader == "forge" and (
        report.get("profile_manifest_size") != contract.profile_size
        or report.get("profile_manifest_sha256") != contract.profile_sha256
    ):
        fail(contract, "Forge report profile-manifest provenance changed")

    artifacts = validate_artifact_inventory(contract, report)
    projected = project_for_baseline(contract, report)
    slitherite = validate_slitherite_mechanics(contract, projected)
    screenshots = validate_screenshot_inventory(contract, report)
    records, means, material_ratio, mean_delta = validate_screenshot_files(
        contract, scenario_root, screenshots
    )
    BASELINE._validate_assertions(
        projected.get("assertions"),
        slitherite,
        records,
        contract.controller.E2EError,
    )
    return (
        means["initial"],
        means["reopened"],
        material_ratio,
        mean_delta,
        artifacts,
    )


def validate_world_save(
    contract: LoaderContract, configuration: object, runtime: Path
) -> None:
    """Validates bounded regular saved-world proof after the reopen capture."""

    world_root = (
        contract.controller.game_directory(configuration, runtime)
        / "saves"
        / str(EXPECTED_WORLD["save_directory"])
    )
    BASELINE._validate_world_files(world_root, contract.controller.E2EError)


def validate_game_lifecycle(
    contract: LoaderContract, configuration: object, runtime: Path
) -> None:
    """Requires one clean client shutdown and exactly one publication marker."""

    game = contract.controller.game_directory(configuration, runtime)
    crash_reports = game / "crash-reports"
    if (
        not crash_reports.is_dir()
        or crash_reports.is_symlink()
        or any(crash_reports.iterdir())
    ):
        fail(contract, "runtime contains crash reports")
    latest_log = game / "logs" / "latest.log"
    if (
        not latest_log.is_file()
        or latest_log.is_symlink()
        or latest_log.stat().st_size <= 0
        or latest_log.stat().st_size > contract.controller.MAXIMUM_PROCESS_LOG_SIZE
    ):
        fail(contract, "game log is missing, linked, empty, or oversized")
    content = latest_log.read_text(encoding="utf-8", errors="replace")
    fatal_markers = (
        *contract.evidence.FATAL_GAME_LOG_MARKERS,
        *FATAL_SLITHERITE_LOG_MARKERS,
    )
    fatal_marker = next(
        (marker for marker in fatal_markers if marker in content), None
    )
    if fatal_marker is not None:
        fail(contract, f"game log contains fatal marker: {fatal_marker}")
    if "Stopping!" not in content:
        fail(contract, "client did not stop normally")
    publication_marker = "Slitherite evidence published with status passed:"
    if content.count(publication_marker) != 1:
        fail(contract, "game log lacks one exact successful publication marker")
    validate_world_save(contract, configuration, runtime)


def validate_live_artifacts(
    contract: LoaderContract,
    configuration: object,
    runtime: Path,
    report_artifacts: dict[str, dict[str, object]],
) -> tuple[str, str]:
    """Binds report provenance to staged JAR bytes and the controller lock."""

    expected_harness_size, expected_harness_digest = require_harness_pin(contract)
    contract.controller.verify_locked_artifacts(
        configuration, runtime, verify_source=False
    )
    lock = contract.controller.load_artifact_lock(configuration, runtime)
    if (
        not isinstance(lock, dict)
        or lock.get("schema") != contract.artifact_lock_schema
        or lock.get("profile_id") != contract.profile_id
        or lock.get("artifact_node") != contract.artifact_node
    ):
        fail(contract, "capture has no exact artifact lock")
    locked_artifacts = lock.get("artifacts")
    if (
        not isinstance(locked_artifacts, dict)
        or set(locked_artifacts) != set(EXPECTED_ARTIFACT_ROLES)
    ):
        fail(contract, "artifact-lock inventory changed")
    for role, mod_id, file_name in contract.artifacts:
        locked = locked_artifacts.get(role)
        reported = report_artifacts[role]
        if (
            not isinstance(locked, dict)
            or locked.get("mod_id") != mod_id
            or locked.get("target_file") != file_name
            or locked.get("size") != reported["size"]
            or locked.get("sha256") != reported["sha256"]
        ):
            fail(contract, f"{role} artifact differs from its lock")
    if (
        report_artifacts["harness"]["size"] != expected_harness_size
        or report_artifacts["harness"]["sha256"] != expected_harness_digest
    ):
        fail(contract, "live harness differs from the final packaged pin")
    return (
        str(report_artifacts["production"]["sha256"]),
        str(report_artifacts["harness"]["sha256"]),
    )


def validate_scenario_inventory(
    contract: LoaderContract,
    scenario_root: Path,
    *,
    include_manifest: bool,
) -> None:
    """Rejects links, special entries, extras, and oversized evidence trees."""

    if not scenario_root.is_dir() or scenario_root.is_symlink():
        fail(contract, f"evidence root is missing or linked: {scenario_root}")
    files: set[str] = set()
    directories: set[str] = set()
    for path in scenario_root.rglob("*"):
        relative_path = path.relative_to(scenario_root).as_posix()
        if path.is_symlink():
            fail(contract, f"evidence contains a linked entry: {relative_path}")
        if path.is_dir():
            directories.add(relative_path)
        elif path.is_file():
            files.add(relative_path)
        else:
            fail(contract, f"evidence contains a special entry: {relative_path}")
    expected_files = set(ARCHIVE_PAYLOAD_PATHS)
    if include_manifest:
        expected_files.add(ARCHIVE_MANIFEST_NAME)
    if files != expected_files or directories != ARCHIVE_DIRECTORIES:
        fail(contract, "evidence inventory is incomplete or contaminated")
    total_size = sum(
        path.stat().st_size for path in scenario_root.rglob("*") if path.is_file()
    )
    if total_size > contract.evidence.MAXIMUM_SCENARIO_EVIDENCE_SIZE:
        fail(contract, "evidence exceeds the size bound")


def load_completed_report(
    contract: LoaderContract, scenario_root: Path
) -> tuple[dict[str, object], Path, Path]:
    """Loads a report only after its exact completion marker is present."""

    report_path = scenario_root / "reports" / "report.json"
    done_path = scenario_root / "reports" / "done.marker"
    if (
        not report_path.is_file()
        or report_path.is_symlink()
        or not done_path.is_file()
        or done_path.is_symlink()
    ):
        fail(contract, "report or completion marker is missing or linked")
    if done_path.read_text(encoding="utf-8") != "complete\n":
        fail(contract, "completion marker is invalid")
    report = contract.evidence.require_json_object(
        report_path, f"{contract.label} Slitherite scenario report"
    )
    return report, report_path, done_path


def validate_live_evidence(
    contract: LoaderContract,
    configuration: object | None = None,
    runtime: Path | None = None,
) -> SlitheriteEvidenceSummary:
    """Validates one completed fresh capture without launching or changing it."""

    require_harness_pin(contract)
    resolved = configuration or contract.controller.load_configuration()
    validate_active_profile(contract, resolved)
    target_runtime = runtime or contract.controller.runtime_root(resolved)
    if (
        contract.controller.resolve_scenario_id(resolved, SCENARIO_ID)
        != SCENARIO_ID
    ):
        fail(contract, "scenario selection changed")
    contract.controller.verify_evidence_layout(resolved, target_runtime)
    scenario_root = (
        contract.controller.evidence_root(resolved, target_runtime) / SCENARIO_ID
    )
    validate_scenario_inventory(contract, scenario_root, include_manifest=False)
    report, report_path, done_path = load_completed_report(contract, scenario_root)
    (
        initial_mean,
        reopened_mean,
        material_ratio,
        mean_delta,
        artifacts,
    ) = validate_report_contract(contract, scenario_root, report)
    production_digest, harness_digest = validate_live_artifacts(
        contract, resolved, target_runtime, artifacts
    )
    screenshot_paths = [
        scenario_root / str(screenshot["file"])
        for screenshot in report["screenshots"]
    ]
    if any(
        done_path.stat().st_mtime_ns < path.stat().st_mtime_ns
        for path in (report_path, *screenshot_paths)
    ):
        fail(contract, "completion marker predates an evidence payload")
    validate_game_lifecycle(contract, resolved, target_runtime)
    return SlitheriteEvidenceSummary(
        profile_id=contract.profile_id,
        assertion_count=len(contract.assertion_names),
        screenshot_count=len(EXPECTED_SCREENSHOTS),
        initial_mean_luminance=initial_mean,
        reopened_mean_luminance=reopened_mean,
        reopen_material_changed_pixel_ratio=material_ratio,
        reopen_mean_max_channel_delta=mean_delta,
        production_sha256=production_digest,
        harness_sha256=harness_digest,
    )


def validate_no_competing_archives(
    contract: LoaderContract, archive_root: Path
) -> None:
    """Requires the current lane archive to be the sole versioned candidate."""

    if not archive_root.parent.is_dir() or archive_root.parent.is_symlink():
        fail(contract, "archive parent is missing or linked")
    candidates = sorted(
        (
            entry
            for entry in archive_root.parent.iterdir()
            if ARCHIVE_DIRECTORY_PATTERN.fullmatch(entry.name)
        ),
        key=lambda entry: entry.name,
    )
    if (
        candidates != [archive_root]
        or not archive_root.is_dir()
        or archive_root.is_symlink()
    ):
        fail(contract, "the exact sole versioned Slitherite archive is required")


def expected_repository_archive_root(
    contract: LoaderContract, configuration: object
) -> Path:
    """Returns the one repository destination authorized for the archive."""

    return contract.controller.safe_repository_path(
        configuration.repository_root,
        f"{contract.archive_parent_relative_path}/{contract.archive_directory_name}",
        f"{contract.label} Slitherite evidence archive",
    )


def validate_capture_artifact_lock(
    contract: LoaderContract,
    configuration: object,
    capture_runtime: Path,
    report_artifacts: dict[str, dict[str, object]],
) -> Path:
    """Returns the exact controller lock after binding it to report artifacts."""

    lock = contract.controller.load_artifact_lock(configuration, capture_runtime)
    if (
        not isinstance(lock, dict)
        or lock.get("schema") != contract.artifact_lock_schema
        or lock.get("profile_id") != contract.profile_id
        or lock.get("artifact_node") != contract.artifact_node
    ):
        fail(contract, "capture has no exact artifact lock")
    locked_artifacts = lock.get("artifacts")
    if (
        not isinstance(locked_artifacts, dict)
        or set(locked_artifacts) != set(EXPECTED_ARTIFACT_ROLES)
    ):
        fail(contract, "capture artifact-lock inventory changed")
    for role, mod_id, file_name in contract.artifacts:
        locked = locked_artifacts.get(role)
        reported = report_artifacts[role]
        if (
            not isinstance(locked, dict)
            or locked.get("mod_id") != mod_id
            or locked.get("target_file") != file_name
            or locked.get("size") != reported["size"]
            or locked.get("sha256") != reported["sha256"]
        ):
            fail(contract, f"capture {role} lock differs from report")
    lock_path = contract.controller.artifact_lock_path(
        configuration, capture_runtime
    )
    if not lock_path.is_file() or lock_path.is_symlink():
        fail(contract, "artifact lock is missing or linked")
    return lock_path


def build_archive_manifest(
    contract: LoaderContract,
    configuration: object,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> dict[str, object]:
    """Builds a seal only from the exact live capture and copied payload."""

    require_harness_pin(contract)
    validate_active_profile(contract, configuration)
    validate_no_competing_archives(contract, archive_root)
    expected_profile = (
        configuration.repository_root / contract.active_profile_relative_path
    )
    if (
        profile_manifest_path.resolve() != expected_profile.resolve()
        or not profile_manifest_path.is_file()
        or profile_manifest_path.is_symlink()
        or profile_manifest_path.stat().st_size != contract.profile_size
        or sha256_file(profile_manifest_path) != contract.profile_sha256
    ):
        fail(contract, "archive sealing requires the exact active profile")
    validate_scenario_inventory(contract, archive_root, include_manifest=False)
    if (
        archive_root.resolve()
        != expected_repository_archive_root(contract, configuration).resolve()
    ):
        fail(contract, "archive sealing requires the repository destination")
    expected_runtime = contract.controller.runtime_root(configuration)
    contract.controller.ensure_owned_state_roots()
    if (
        capture_runtime.resolve() != expected_runtime.resolve()
        or not capture_runtime.is_dir()
        or capture_runtime.is_symlink()
        or capture_runtime.name != contract.profile_id
    ):
        fail(contract, "archive sealing requires the exact owned fresh runtime")
    contract.controller.verify_runtime(
        configuration, capture_runtime, artifact_policy="optional"
    )
    live_summary = validate_live_evidence(
        contract, configuration, capture_runtime
    )
    archive_report = contract.evidence.require_json_object(
        archive_root / "reports/report.json",
        f"{contract.label} Slitherite archived report",
    )
    (
        _initial_mean,
        _reopened_mean,
        archive_material_ratio,
        archive_mean_delta,
        report_artifacts,
    ) = validate_report_contract(contract, archive_root, archive_report)
    if (
        abs(
            live_summary.reopen_material_changed_pixel_ratio
            - archive_material_ratio
        )
        > 1e-12
        or abs(live_summary.reopen_mean_max_channel_delta - archive_mean_delta)
        > 1e-12
        or live_summary.production_sha256
        != report_artifacts["production"]["sha256"]
        or live_summary.harness_sha256 != report_artifacts["harness"]["sha256"]
    ):
        fail(contract, "archive differs from live semantic validation")

    capture_root = (
        contract.controller.evidence_root(configuration, capture_runtime)
        / SCENARIO_ID
    )
    capture_mtime_ns: dict[str, int] = {}
    files: dict[str, dict[str, object]] = {}
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        archive_path = archive_root / relative_path
        capture_path = capture_root / relative_path
        if not capture_path.is_file() or capture_path.is_symlink():
            fail(contract, f"capture payload is missing or linked: {capture_path}")
        if (
            archive_path.stat().st_size != capture_path.stat().st_size
            or sha256_file(archive_path) != sha256_file(capture_path)
        ):
            fail(contract, f"archive payload differs from capture: {relative_path}")
        capture_mtime_ns[relative_path] = capture_path.stat().st_mtime_ns
        files[relative_path] = {
            "size": archive_path.stat().st_size,
            "sha256": sha256_file(archive_path),
        }
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[path]
        for path in ARCHIVE_PAYLOAD_PATHS
        if path != "reports/done.marker"
    ):
        fail(contract, "capture completion predates copied evidence")
    artifact_lock_path = validate_capture_artifact_lock(
        contract, configuration, capture_runtime, report_artifacts
    )
    return {
        "schema": 1,
        "kind": contract.archive_kind,
        "verification_scope": contract.archive_verification_scope,
        "scenario": SCENARIO_ID,
        "profile": {
            "id": contract.profile_id,
            "manifest_path": contract.active_profile_relative_path,
            "manifest_size": contract.profile_size,
            "manifest_sha256": contract.profile_sha256,
        },
        "runtime": {
            "artifact_node": contract.artifact_node,
            "minecraft": "1.20.1",
            "loader": contract.loader,
            "loader_version": contract.loader_version,
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
            "path": contract.artifact_lock_name,
            "size": artifact_lock_path.stat().st_size,
            "sha256": sha256_file(artifact_lock_path),
        },
        "assertion_count": len(contract.assertion_names),
        "screenshot_count": len(EXPECTED_SCREENSHOTS),
        "artifacts": report_artifacts,
        "files": files,
    }


def write_archive_manifest(
    contract: LoaderContract,
    configuration: object,
    profile_manifest_path: Path,
    capture_runtime: Path,
    archive_root: Path,
) -> Path:
    """Atomically creates and immediately validates a one-shot archive seal."""

    manifest_path = archive_root / ARCHIVE_MANIFEST_NAME
    if manifest_path.exists() or manifest_path.is_symlink():
        fail(contract, f"archive manifest already exists: {manifest_path}")
    manifest = build_archive_manifest(
        contract,
        configuration,
        profile_manifest_path,
        capture_runtime,
        archive_root,
    )
    temporary_path: Path | None = None
    try:
        descriptor, raw_path = tempfile.mkstemp(
            prefix=".slitherite-archive-manifest.", dir=archive_root
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
            raise contract.controller.E2EError(
                f"{contract.label} Slitherite evidence: archive manifest "
                f"already exists: {manifest_path}"
            ) from exception
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)
    try:
        validate_archived_evidence(contract, archive_root)
    except (contract.controller.E2EError, OSError, json.JSONDecodeError):
        manifest_path.unlink(missing_ok=True)
        raise
    return manifest_path


def validate_archive_manifest(
    contract: LoaderContract,
    archive_root: Path,
    manifest: dict[str, object],
) -> dict[str, dict[str, object]]:
    """Validates the immutable seal and every recorded payload digest."""

    expected_harness_size, expected_harness_digest = require_harness_pin(contract)
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
        fail(contract, "archive manifest fields changed")
    if (
        manifest.get("schema") != 1
        or manifest.get("kind") != contract.archive_kind
        or manifest.get("verification_scope")
        != contract.archive_verification_scope
        or manifest.get("scenario") != SCENARIO_ID
        or manifest.get("assertion_count") != len(contract.assertion_names)
        or manifest.get("screenshot_count") != len(EXPECTED_SCREENSHOTS)
    ):
        fail(contract, "archive identity is invalid")
    if manifest.get("profile") != {
        "id": contract.profile_id,
        "manifest_path": contract.active_profile_relative_path,
        "manifest_size": contract.profile_size,
        "manifest_sha256": contract.profile_sha256,
    }:
        fail(contract, "archive profile is invalid")
    if manifest.get("runtime") != {
        "artifact_node": contract.artifact_node,
        "minecraft": "1.20.1",
        "loader": contract.loader,
        "loader_version": contract.loader_version,
        "java": 17,
        "capture_kind": "composed-minecraft-framebuffer",
        "framebuffer_width": 1920,
        "framebuffer_height": 1080,
    }:
        fail(contract, "archive runtime is invalid")
    publication = manifest.get("publication")
    if not isinstance(publication, dict) or set(publication) != {
        *ARCHIVE_PUBLICATION_ATTESTATION,
        "capture_mtime_ns",
    }:
        fail(contract, "archive publication is malformed")
    if any(
        publication.get(key) != value
        for key, value in ARCHIVE_PUBLICATION_ATTESTATION.items()
    ):
        fail(contract, "archive publication attestation is invalid")
    capture_mtime_ns = publication.get("capture_mtime_ns")
    if (
        not isinstance(capture_mtime_ns, dict)
        or set(capture_mtime_ns) != set(ARCHIVE_PAYLOAD_PATHS)
        or any(
            type(value) is not int or value <= 0
            for value in capture_mtime_ns.values()
        )
    ):
        fail(contract, "archive capture timestamps are invalid")
    completion_mtime = capture_mtime_ns["reports/done.marker"]
    if any(
        completion_mtime < capture_mtime_ns[path]
        for path in ARCHIVE_PAYLOAD_PATHS
        if path != "reports/done.marker"
    ):
        fail(contract, "archive completion predates its payload")
    capture_metadata = manifest.get("capture_metadata")
    if (
        not isinstance(capture_metadata, dict)
        or set(capture_metadata) != {"path", "size", "sha256"}
        or capture_metadata.get("path") != contract.artifact_lock_name
        or type(capture_metadata.get("size")) is not int
        or int(capture_metadata["size"]) <= 0
    ):
        fail(contract, "archive capture metadata is invalid")
    contract.controller.validate_hex_digest(
        capture_metadata.get("sha256"),
        f"{contract.label} Slitherite capture metadata",
    )

    artifacts = manifest.get("artifacts")
    if (
        not isinstance(artifacts, dict)
        or set(artifacts) != set(EXPECTED_ARTIFACT_ROLES)
    ):
        fail(contract, "archive artifact inventory changed")
    validated_artifacts: dict[str, dict[str, object]] = {}
    for role, mod_id, file_name in contract.artifacts:
        artifact = artifacts.get(role)
        if (
            not isinstance(artifact, dict)
            or set(artifact) != {"mod_id", "file_name", "size", "sha256"}
            or artifact.get("mod_id") != mod_id
            or artifact.get("file_name") != file_name
            or type(artifact.get("size")) is not int
            or int(artifact["size"]) <= 0
        ):
            fail(contract, f"archived {role} artifact is invalid")
        digest = contract.controller.validate_hex_digest(
            artifact.get("sha256"),
            f"{contract.label} Slitherite archived {role} artifact",
        )
        if role == "harness" and (
            artifact["size"] != expected_harness_size
            or digest != expected_harness_digest
        ):
            fail(contract, "archived harness pin changed")
        validated_artifacts[role] = artifact

    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != set(ARCHIVE_PAYLOAD_PATHS):
        fail(contract, "archive payload inventory changed")
    for relative_path in ARCHIVE_PAYLOAD_PATHS:
        record = files.get(relative_path)
        if (
            not isinstance(record, dict)
            or set(record) != {"size", "sha256"}
            or type(record.get("size")) is not int
            or int(record["size"]) <= 0
        ):
            fail(contract, f"archive payload record is invalid: {relative_path}")
        contract.controller.validate_hex_digest(
            record.get("sha256"),
            f"{contract.label} Slitherite payload {relative_path}",
        )
        path = archive_root / relative_path
        if (
            path.stat().st_size != record["size"]
            or sha256_file(path) != record["sha256"]
        ):
            fail(contract, f"archive payload differs from manifest: {relative_path}")
    return validated_artifacts


def validate_archived_evidence(
    contract: LoaderContract, archive_root: Path
) -> SlitheriteEvidenceSummary:
    """Validates a sealed archive without consulting mutable live runtime state."""

    require_harness_pin(contract)
    validate_no_competing_archives(contract, archive_root)
    validate_scenario_inventory(contract, archive_root, include_manifest=True)
    manifest = contract.evidence.require_json_object(
        archive_root / ARCHIVE_MANIFEST_NAME,
        f"{contract.label} Slitherite archive manifest",
    )
    manifest_artifacts = validate_archive_manifest(
        contract, archive_root, manifest
    )
    report, _report_path, _done_path = load_completed_report(
        contract, archive_root
    )
    (
        initial_mean,
        reopened_mean,
        material_ratio,
        mean_delta,
        report_artifacts,
    ) = validate_report_contract(contract, archive_root, report)
    if report_artifacts != manifest_artifacts:
        fail(contract, "report artifacts differ from archive manifest")
    return SlitheriteEvidenceSummary(
        profile_id=contract.profile_id,
        assertion_count=len(contract.assertion_names),
        screenshot_count=len(EXPECTED_SCREENSHOTS),
        initial_mean_luminance=initial_mean,
        reopened_mean_luminance=reopened_mean,
        reopen_material_changed_pixel_ratio=material_ratio,
        reopen_mean_max_channel_delta=mean_delta,
        production_sha256=str(report_artifacts["production"]["sha256"]),
        harness_sha256=str(report_artifacts["harness"]["sha256"]),
    )
