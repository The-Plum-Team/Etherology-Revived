from __future__ import annotations

import copy
from dataclasses import dataclass
import hashlib
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest


BASELINE_DIRECTORY = Path(__file__).resolve().parents[1]
MODULE_PATH = BASELINE_DIRECTORY / "original_slitherite_evidence_v7.py"
SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_original_slitherite_evidence_v7_tested", MODULE_PATH
)
if SPECIFICATION is None or SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load Slitherite verifier: {MODULE_PATH}")
verifier = importlib.util.module_from_spec(SPECIFICATION)
sys.modules[SPECIFICATION.name] = verifier
SPECIFICATION.loader.exec_module(verifier)


@dataclass(frozen=True)
class FakeImage:
    width: int
    height: int
    pixels: bytes


BRIGHT_PIXELS = b"\xc8\xc8\xc8" * (1920 * 1080)
BRIGHT_IMAGE = FakeImage(1920, 1080, BRIGHT_PIXELS)
DARK_IMAGE = FakeImage(1920, 1080, b"\0\0\0" * (1920 * 1080))
CHANGED_IMAGE = FakeImage(1920, 1080, b"\xc9\xc9\xc9" * (1920 * 1080))
MAGENTA_IMAGE = FakeImage(1920, 1080, b"\xff\0\xff" * (1920 * 1080))


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def assertion(name: str, expected: str, actual: str) -> dict[str, object]:
    return {
        "name": name,
        "passed": True,
        "expected": expected,
        "actual": actual,
    }


def contract_fixture(root: Path) -> tuple[Path, Path, dict[str, object], list[dict[str, object]]]:
    scenario_root = root / verifier.SCENARIO_ID
    reports = scenario_root / "reports"
    screenshots = scenario_root / "screenshots"
    reports.mkdir(parents=True)
    screenshots.mkdir()
    (reports / "report.json").write_text("{}\n", encoding="utf-8")
    (reports / "done.marker").write_text("pending\n", encoding="utf-8")
    for file_name in verifier.SCREENSHOT_FILES:
        (screenshots / file_name).write_bytes(file_name.encode("ascii"))

    world = root / "world"
    (world / "region").mkdir(parents=True)
    (world / "level.dat").write_bytes(b"level")
    (world / "session.lock").write_bytes(b"lock")
    (world / "region" / "r.0.0.mca").write_bytes(b"region")

    registry = []
    for index, (path, block_class, default_state, state_count) in enumerate(
        verifier.BLOCK_SPECS
    ):
        block_id = f"etherology:{path}"
        registry.append(
            f"{block_id}=block_class:{block_class},"
            f"item_class:{verifier.BLOCK_ITEM_CLASS},default:{default_state},"
            f"states:{state_count},default_raw_id:{1000 + index},"
            f"raw_ids:{state_count}"
        )
    placements = ";".join(
        f"{block_id}=PlacementEvidence[actionResult=SUCCESS, accepted=true, "
        "beforeCount=1, afterCount=0, blockItemMapping=true, "
        f"placedId={block_id}, placedState={block_id}]"
        for block_id in verifier.BLOCK_IDS
    )
    snapshot = ";".join(
        f"{block_id}={block_id}|support=minecraft:polished_andesite"
        for block_id in verifier.BLOCK_IDS
    )
    slitherite: dict[str, object] = {
        "block_ids": list(verifier.BLOCK_IDS),
        "registry": registry,
        "aggregate_state_count": 1262,
        "canonical_resources": list(verifier.EXPECTED_RESOURCES),
        "tags": list(verifier.EXPECTED_TAGS),
        "loot_tables": list(verifier.EXPECTED_LOOT_TABLES),
        "self_drops": dict(verifier.EXPECTED_SELF_DROPS),
        "double_slab_drops": dict(verifier.EXPECTED_DOUBLE_SLAB_DROPS),
        "owned_recipes": list(verifier.EXPECTED_OWNED_RECIPES),
        "owned_advancements": list(verifier.EXPECTED_OWNED_ADVANCEMENTS),
        "related_recipes_recorded_not_owned": list(
            verifier.EXPECTED_RELATED_RECIPES
        ),
        "placements": placements,
        "button_behavior": "powered=true;scheduled=true;elapsed=20;reset=true",
        "pressure_plate_behavior": "item=false;living=true;reset=true",
        "initial_snapshot": snapshot,
        "reopened_snapshot": snapshot,
        "persistence_exact": True,
        "reopened_data_exact": True,
        "required_stable_renders": 120,
        "required_lighting_ready_client_ticks": 20,
    }
    artifacts = [
        {
            "mod_id": "etherology",
            "origin_kind": "PATH",
            "file_name": "Etherology-1.21-0.1.7.jar",
            "size": 2743963,
            "sha256": "a" * 64,
        },
        {
            "mod_id": "etherology_original_baseline_harness",
            "origin_kind": "PATH",
            "file_name": (
                "Etherology-Original-E2E-Harness-Fabric-1.21.1-1.3.2.jar"
            ),
            "size": 210485,
            "sha256": "b" * 64,
        },
    ]
    screenshot_nodes = []
    for phase, relative_file in verifier.EXPECTED_SCREENSHOTS:
        path = scenario_root / relative_file
        screenshot_nodes.append(
            {
                "step": phase,
                "file": relative_file,
                "width": 1920,
                "height": 1080,
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
                "completed_render_count": 120,
                "source": "minecraft-framebuffer",
                "edited": False,
            }
        )

    assertions: dict[str, dict[str, object]] = {}
    assertions["fabric_mod_loaded:etherology"] = assertion(
        "fabric_mod_loaded:etherology", "loaded", "loaded"
    )
    for index, (path, block_class, default_state, state_count) in enumerate(
        verifier.BLOCK_SPECS
    ):
        block_id = f"etherology:{path}"
        exact = {
            f"registry:block:{block_id}": ("present", "present"),
            f"registry:item:{block_id}": ("present", "present"),
            f"runtime:block_class:{block_id}": (block_class, block_class),
            f"runtime:block_item_class:{block_id}": (
                verifier.BLOCK_ITEM_CLASS,
                verifier.BLOCK_ITEM_CLASS,
            ),
            f"block_item_mapping:{block_id}": ("true", "true"),
            f"default_state:{block_id}": (default_state, default_state),
            f"state_count:{block_id}": (str(state_count), str(state_count)),
            f"default_state_network_id:{block_id}": (
                "non-negative",
                str(1000 + index),
            ),
        }
        for name, (expected, actual) in exact.items():
            assertions[name] = assertion(name, expected, actual)
    resources = verifier._java_list(list(verifier.EXPECTED_RESOURCES))
    assertions["slitherite_canonical_resources_exact"] = assertion(
        "slitherite_canonical_resources_exact", resources, resources
    )
    aggregate = "1262 unique non-negative raw ids"
    assertions["slitherite_state_network_ids_exact"] = assertion(
        "slitherite_state_network_ids_exact", aggregate, aggregate
    )
    for mod_id in ("etherology", "etherology_original_baseline_harness"):
        name = f"packaged_root_jar:{mod_id}"
        assertions[name] = assertion(
            name, "one regular root JAR", "one regular root JAR"
        )
    assertions["integrated_world_joined"] = assertion(
        "integrated_world_joined",
        "running server and connected client",
        "joined",
    )
    assertions["server_arena_chunks_loaded"] = assertion(
        "server_arena_chunks_loaded", "twelve full chunks", "true"
    )
    tag_names = (
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
    for name, tag_description in zip(
        tag_names, verifier.EXPECTED_TAGS, strict=True
    ):
        value = tag_description.partition("=")[2]
        assertions[name] = assertion(name, value, value)
    exact_descriptions = {
        "slitherite_loot_tables_exact": verifier._java_list(
            list(verifier.EXPECTED_LOOT_TABLES)
        ),
        "slitherite_self_drops_exact": verifier._java_map(
            verifier.EXPECTED_SELF_DROPS
        ),
        "slitherite_double_slab_drops_x1_exact": verifier._java_map(
            verifier.EXPECTED_DOUBLE_SLAB_DROPS
        ),
        "slitherite_owned_recipes_exact": verifier._java_list(
            list(verifier.EXPECTED_OWNED_RECIPES)
        ),
        "slitherite_owned_advancements_exact": verifier._java_list(
            list(verifier.EXPECTED_OWNED_ADVANCEMENTS)
        ),
        "slitherite_related_recipes_recorded_not_owned": verifier._java_list(
            list(verifier.EXPECTED_RELATED_RECIPES)
        ),
    }
    for name, value in exact_descriptions.items():
        assertions[name] = assertion(name, value, value)
    assertions["direct_block_item_placements_exact"] = assertion(
        "direct_block_item_placements_exact",
        "17 accepted 1->0 BlockItem placements bound to registered blocks",
        placements,
    )
    assertions["slitherite_button_pulse_reset_exact"] = assertion(
        "slitherite_button_pulse_reset_exact",
        "powered=true;scheduled=true;elapsed>=20;reset=true",
        str(slitherite["button_behavior"]),
    )
    assertions["slitherite_pressure_plate_entities_exact"] = assertion(
        "slitherite_pressure_plate_entities_exact",
        "item=false;living=true;reset=true",
        "item=false;living=true;reset=true",
    )
    assertions["initial_server_fixture_exact"] = assertion(
        "initial_server_fixture_exact",
        "17 exact Slitherite states on deterministic supports",
        snapshot,
    )
    identity = (
        "Etherology Original 0.1.7 Slitherite Blocks;"
        "4995697409260082224;minecraft:overworld"
    )
    assertions["live_world_identity"] = assertion(
        "live_world_identity", identity, identity
    )
    assertions["forced_world_save"] = assertion(
        "forced_world_save", "true", "true"
    )
    assertions["restart_fixture_persistence_exact"] = assertion(
        "restart_fixture_persistence_exact",
        "saved snapshot equals reopened snapshot",
        "exact",
    )
    assertions["restart_loaded_data_exact"] = assertion(
        "restart_loaded_data_exact",
        "same registry, tags, loot, recipes, and advancements",
        "exact",
    )
    lighting = (
        "stableClientTicks=20;clientPending=false;client:"
        f"{verifier.EXPECTED_LOCAL_LIGHT_SAMPLES};serverGeneration=25;server:"
        f"{verifier.EXPECTED_LOCAL_LIGHT_SAMPLES}"
    )
    for phase, screenshot_node in zip(
        verifier.PHASES, screenshot_nodes, strict=True
    ):
        fixed = {
            f"capture_mirror_exact:{phase}": (
                "client snapshot equals server snapshot",
                "exact",
            ),
            f"capture_render_ready:{phase}": (
                "terrain and all 17 fixtures rendering-ready",
                "ready",
            ),
            f"capture_lighting_ready:{phase}": (
                verifier.EXPECTED_LIGHTING_DESCRIPTION,
                lighting,
            ),
            f"capture_camera_exact:{phase}": (
                verifier.EXPECTED_CAMERA,
                (
                    "first_person=true;x=0.5;y=121.0;z=-14.5;"
                    "yaw=0.0;pitch=8.0;on_ground=true"
                ),
            ),
            f"capture_consecutive_stable_renders:{phase}": ("120", "120"),
            f"capture_framebuffer_dimensions:{phase}": ("1920x1080", "1920x1080"),
            f"native_screenshot_written:{phase}": (
                "one non-empty unedited 1920x1080 framebuffer PNG",
                (
                    f"{screenshot_node['size']} bytes, "
                    f"sha256={screenshot_node['sha256']}"
                ),
            ),
        }
        for name, (expected, actual) in fixed.items():
            assertions[name] = assertion(name, expected, actual)
    save_name = str(verifier.EXPECTED_WORLD["save_directory"])
    assertions["isolated_save_directory_present"] = assertion(
        "isolated_save_directory_present", save_name, save_name
    )
    ordered_assertions = [
        assertions[name] for name in verifier.EXPECTED_ASSERTION_NAMES
    ]
    report: dict[str, object] = {
        "schema": 3,
        "reference_id": "published-0.1.7",
        "scenario": verifier.SCENARIO_ID,
        "lane": "fabric-1.21.1-original",
        "status": "passed",
        "passed": True,
        "client_ticks": 1000,
        "lifecycle_failure": "",
        "assertions": ordered_assertions,
        "world": dict(verifier.EXPECTED_WORLD),
        "artifacts": copy.deepcopy(artifacts),
        "screenshots": screenshot_nodes,
        "slitherite": slitherite,
    }
    return scenario_root, world, report, artifacts


class SlitheriteEvidenceV7Tests(unittest.TestCase):
    def validate(
        self,
        scenario_root: Path,
        world: Path,
        report: dict[str, object],
        artifacts: list[dict[str, object]],
        *,
        images: dict[str, FakeImage] | None = None,
    ) -> object:
        selected_images = images or {
            verifier.INITIAL_SCREENSHOT_FILE: BRIGHT_IMAGE,
            verifier.REOPENED_SCREENSHOT_FILE: BRIGHT_IMAGE,
        }
        return verifier.validate_evidence(
            scenario_root=scenario_root,
            world_path=world,
            report=report,
            expected_artifacts=artifacts,
            decode_png=lambda path: selected_images[path.name],
            assert_image_is_not_blank=lambda _image: None,
            sha256_file=sha256_file,
        )

    def test_complete_contract_passes_with_183_assertions_and_two_captures(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            summary = self.validate(scenario, world, report, artifacts)
            self.assertEqual(summary.assertion_count, 183)
            self.assertEqual(summary.screenshot_count, 2)
            self.assertEqual(summary.reopen_changed_pixel_ratio, 0.0)

    def test_global_pending_is_diagnostic_and_does_not_fail_local_light_proof(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            for value in report["assertions"]:
                if str(value["name"]).startswith("capture_lighting_ready:"):
                    value["actual"] = str(value["actual"]).replace(
                        "clientPending=false", "clientPending=true"
                    )
            self.validate(scenario, world, report, artifacts)

    def test_dark_first_frame_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            with self.assertRaises(verifier.SlitheriteEvidenceError):
                self.validate(
                    scenario,
                    world,
                    report,
                    artifacts,
                    images={
                        verifier.INITIAL_SCREENSHOT_FILE: DARK_IMAGE,
                        verifier.REOPENED_SCREENSHOT_FILE: BRIGHT_IMAGE,
                    },
                )

    def test_missing_canonical_visual_asset_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            report["slitherite"]["canonical_resources"].pop()
            with self.assertRaises(verifier.SlitheriteEvidenceError):
                self.validate(scenario, world, report, artifacts)

    def test_missing_texture_magenta_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            with self.assertRaises(verifier.SlitheriteEvidenceError):
                self.validate(
                    scenario,
                    world,
                    report,
                    artifacts,
                    images={
                        verifier.INITIAL_SCREENSHOT_FILE: MAGENTA_IMAGE,
                        verifier.REOPENED_SCREENSHOT_FILE: BRIGHT_IMAGE,
                    },
                )

    def test_reopened_structural_drift_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            report["slitherite"]["reopened_snapshot"] = str(
                report["slitherite"]["reopened_snapshot"]
            ).replace("minecraft:polished_andesite", "minecraft:stone", 1)
            with self.assertRaises(verifier.SlitheriteEvidenceError):
                self.validate(scenario, world, report, artifacts)

    def test_reopened_pixel_drift_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            with self.assertRaises(verifier.SlitheriteEvidenceError):
                self.validate(
                    scenario,
                    world,
                    report,
                    artifacts,
                    images={
                        verifier.INITIAL_SCREENSHOT_FILE: BRIGHT_IMAGE,
                        verifier.REOPENED_SCREENSHOT_FILE: CHANGED_IMAGE,
                    },
                )


if __name__ == "__main__":
    unittest.main()
