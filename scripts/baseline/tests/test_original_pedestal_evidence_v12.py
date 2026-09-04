from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest import mock


BASELINE_DIRECTORY = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = BASELINE_DIRECTORY.parents[1]
VERIFIER_PATH = BASELINE_DIRECTORY / "original_pedestal_evidence_v12.py"
VERIFIER_SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_original_pedestal_evidence_v12_tested",
    VERIFIER_PATH,
)
if VERIFIER_SPECIFICATION is None or VERIFIER_SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load Pedestal verifier: {VERIFIER_PATH}")
verifier = importlib.util.module_from_spec(VERIFIER_SPECIFICATION)
sys.modules[VERIFIER_SPECIFICATION.name] = verifier
VERIFIER_SPECIFICATION.loader.exec_module(verifier)

CONTROLLER_PATH = BASELINE_DIRECTORY / "original_client.py"
CONTROLLER_SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_original_client_pedestal_tested",
    CONTROLLER_PATH,
)
if CONTROLLER_SPECIFICATION is None or CONTROLLER_SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load original controller: {CONTROLLER_PATH}")
client = importlib.util.module_from_spec(CONTROLLER_SPECIFICATION)
sys.modules[CONTROLLER_SPECIFICATION.name] = client
CONTROLLER_SPECIFICATION.loader.exec_module(client)


class BrightImage:
    width = 1920
    height = 1080
    pixels = bytes([200]) * (width * height * 3)


BRIGHT_IMAGE = BrightImage()
GOLDEN_ASSERTION_SEMANTICS_SHA256 = (
    "65f4d37c924b3c238e69020d0120f293e8ddca30860c98a40712552d8af43351"
)


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def state(
    *,
    shape: str,
    decoration: bool,
    color: str,
    facing: str = "north",
    waterlogged: bool = False,
) -> str:
    return (
        "etherology:pedestal["
        f"cloth_color={color},decoration={str(decoration).lower()},"
        f"facing={facing},shape={shape},"
        f"waterlogged={str(waterlogged).lower()}]"
    )


def snapshot(fixtures: tuple[dict[str, object], ...]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for fixture in fixtures:
        result.append(
            {
                "position": fixture["position"],
                "block_id": "etherology:pedestal",
                "state": state(
                    shape=str(fixture["shape"]),
                    decoration=bool(fixture["decoration"]),
                    color=str(fixture["color"]),
                    facing=str(fixture["facing"]),
                    waterlogged=bool(fixture["waterlogged"]),
                ),
                "shape": fixture["shape"],
                "decoration": fixture["decoration"],
                "color": fixture["color"],
                "facing": fixture["facing"],
                "waterlogged": fixture["waterlogged"],
                "block_entity_type": (
                    "etherology:pedestal_block_entity"
                    if fixture["block_entity"]
                    else "absent"
                ),
                "block_entity_removed": False,
                "item": fixture["item"],
                "carpet": fixture["carpet"],
            }
        )
    return result


def dispenser_observations() -> list[dict[str, object]]:
    values: list[dict[str, object]] = []
    for direction in ("down", "up", "north", "south", "west", "east"):
        values.append(
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
    opposite = {"north": "south", "south": "north", "west": "east", "east": "west"}
    for direction in ("north", "south", "west", "east"):
        values.append(
            {
                "kind": "carpet",
                "direction": direction,
                "dispenser_slot": "minecraft:purple_carpetx1",
                "pedestal_item_slot": "empty",
                "pedestal_carpet_slot": "minecraft:purple_carpetx1",
                "decoration": "true",
                "color": "purple",
                "facing": opposite[direction],
            }
        )
    values.extend(
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
    return values


def assertions(report: dict[str, object]) -> list[dict[str, object]]:
    semantics = verifier._expected_assertion_semantics(report)
    return [
        {
            "name": name,
            "passed": True,
            "expected": semantics[name][0],
            "actual": semantics[name][1],
        }
        for name in verifier.EXPECTED_ASSERTION_NAMES
    ]


def assertion_semantics_sha256(report: dict[str, object]) -> str:
    payload = json.dumps(
        verifier._expected_assertion_semantics(report),
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def report_fixture(
    scenario_root: Path,
    expected_artifacts: list[dict[str, object]],
) -> dict[str, object]:
    shapes = {
        name: dict(
            zip(
                ("min_x", "min_y", "min_z", "max_x", "max_y", "max_z", "volume"),
                dimensions,
                strict=True,
            )
        )
        for name, dimensions in verifier.EXPECTED_SHAPES.items()
    }
    persistence = snapshot(verifier.PERSISTENCE_FIXTURES)
    screenshots: list[dict[str, object]] = []
    for phase, relative in verifier.EXPECTED_SCREENSHOTS:
        path = scenario_root / relative
        screenshots.append(
            {
                "step": phase,
                "file": relative,
                "width": 1920,
                "height": 1080,
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
                "completed_render_count": 120,
                "source": "minecraft-framebuffer",
                "edited": False,
            }
        )
    report = {
        "schema": 4,
        "reference_id": "published-0.1.7",
        "scenario": "pedestal-baseline",
        "lane": "fabric-1.21.1-original",
        "status": "passed",
        "passed": True,
        "client_ticks": 500,
        "lifecycle_failure": "",
        "assertions": [],
        "world": copy.deepcopy(verifier.EXPECTED_WORLD),
        "artifacts": copy.deepcopy(expected_artifacts),
        "pedestal": {
            "resource_pins": verifier._load_resource_pins(REPOSITORY_ROOT),
            "multipart_clause_count": 68,
            "registry": {
                "block_present": True,
                "item_present": True,
                "block_entity_type_present": True,
                "block_class": "ru.feytox.etherology.block.pedestal.PedestalBlock",
                "item_class": "net.minecraft.class_1747",
                "block_item_mapping": True,
                "translation": "Pedestal",
                "default_properties": verifier.EXPECTED_DEFAULT_PROPERTIES,
                "state_count": 1024,
                "default_state_raw_id": 12345,
                "unique_state_raw_ids": 1024,
                "network_ids_exact": True,
                "horizontal_facing_values": ["east", "north", "south", "west"],
            },
            "data": {
                "pickaxe_mineable": True,
                "recipe": "etherology:pedestal=minecraft:crafting->etherology:pedestalx2",
                "advancement": "etherology:recipes/decorations/pedestal",
                "loot_table": "etherology:blocks/pedestal",
                "self_drop": "etherology:pedestalx1",
                "failure": "",
            },
            "placement": {
                "standalone": {
                    "action_result": "SUCCESS",
                    "accepted": True,
                    "before_count": 1,
                    "after_count": 0,
                    "block_item_mapping": True,
                    "placed_id": "etherology:pedestal",
                    "placed_state": state(shape="full", decoration=False, color="white"),
                },
                "waterlogged": {
                    "action_result": "SUCCESS",
                    "accepted": True,
                    "before_count": 1,
                    "after_count": 0,
                    "block_item_mapping": True,
                    "placed_id": "etherology:pedestal",
                    "placed_state": state(
                        shape="full",
                        decoration=False,
                        color="white",
                        waterlogged=True,
                    ),
                },
                "stack_shapes": ["full", "bottom", "top", "bottom", "middle", "top"],
                "block_entity_presence": {
                    "full": True,
                    "bottom": False,
                    "middle": False,
                    "top": True,
                },
            },
            "shapes": shapes,
            "interactions": verifier._expected_interactions(),
            "inventory": {
                "block_entity_type": "etherology:pedestal_block_entity",
                "block_entity_class": "ru.feytox.etherology.block.pedestal.PedestalBlockEntity",
                "size": 2,
                "max_count_per_stack": 1,
                "available_slot_counts": {
                    "down": 0,
                    "up": 0,
                    "north": 0,
                    "south": 0,
                    "west": 0,
                    "east": 0,
                },
                "insert_denied": True,
                "extract_denied": True,
                "nbt_keys": ["Items", "id", "removed", "x", "y", "z"],
                "nbt_item_count": 2,
                "nbt_removed_present": True,
                "nbt_removed": False,
            },
            "dispensers": {
                "observations": dispenser_observations(),
                "guarded_carpet_directions": ["down", "up"],
                "vertical_carpet_status": verifier.VERTICAL_CARPET_LIMITATION,
                "ejected_items": ["minecraft:arrowx1"],
                "arrow_projectiles": 0,
            },
            "transitions": {
                "lower_state": state(shape="bottom", decoration=False, color="white"),
                "upper_state": state(shape="top", decoration=False, color="white"),
                "stack_world_block_entity_absent": True,
                "top_block_entity_present": True,
                "stack_old_reference_removed": True,
                "replacement_air": True,
                "replacement_world_block_entity_absent": True,
                "replacement_old_reference_removed": True,
                "stack_client_lookup_absent": True,
                "stack_client_retained_reference_removed": True,
                "replacement_client_lookup_absent": True,
                "replacement_client_retained_reference_removed": True,
                "replacement_client_air": True,
                "stack_drops": {"minecraft:diamond": 1, "minecraft:red_carpet": 1},
                "replacement_drops": {"minecraft:emerald": 1, "minecraft:blue_carpet": 1},
                "combined_drops": {
                    "minecraft:diamond": 1,
                    "minecraft:red_carpet": 1,
                    "minecraft:emerald": 1,
                    "minecraft:blue_carpet": 1,
                },
            },
            "gallery_snapshot": snapshot(verifier.GALLERY_FIXTURES),
            "transition_snapshot": snapshot(verifier.TRANSITION_FIXTURES),
            "persistence_initial_snapshot": persistence,
            "persistence_reopened_snapshot": copy.deepcopy(persistence),
            "forced_save": True,
            "full_restart": True,
            "persistence_exact": True,
            "required_stable_renders": 120,
            "required_light_ready_client_ticks": 20,
            "limitations": [verifier.VERTICAL_CARPET_LIMITATION],
        },
        "screenshots": screenshots,
    }
    report["assertions"] = assertions(report)
    return report


def contract_fixture(root: Path) -> tuple[Path, Path, dict[str, object], list[dict[str, object]]]:
    scenario = root / "pedestal-baseline"
    reports = scenario / "reports"
    screenshots = scenario / "screenshots"
    reports.mkdir(parents=True)
    screenshots.mkdir()
    for file_name in verifier.SCREENSHOT_FILES:
        (screenshots / file_name).write_bytes(b"synthetic-png")
    (reports / "report.json").write_text("{}\n", encoding="utf-8")
    (reports / "done.marker").write_text("synthetic\n", encoding="utf-8")
    world = root / "world"
    (world / "region").mkdir(parents=True)
    for relative in ("level.dat", "session.lock", "region/r.0.0.mca"):
        (world / relative).write_bytes(b"proof")
    artifacts = [
        {"mod_id": "etherology", "origin_kind": "PATH", "file_name": "etherology.jar", "size": 1, "sha256": "a" * 64},
        {"mod_id": "etherology_original_baseline_harness", "origin_kind": "PATH", "file_name": "harness.jar", "size": 1, "sha256": "b" * 64},
    ]
    return scenario, world, report_fixture(scenario, artifacts), artifacts


class PedestalEvidenceV12Test(unittest.TestCase):

    @staticmethod
    def isolated_archive_configuration(
        repository: Path,
    ) -> tuple[object, Path]:
        source_configuration = client.load_configuration()
        configuration = client.Configuration(
            source_configuration.manifest,
            source_configuration.manifest_path,
            repository,
            source_configuration.bundle_path,
            source_configuration.harness_path,
            source_configuration.fabric_profile_snapshot_path,
        )
        archive = repository / verifier.FRESH_ARCHIVE_RELATIVE_PATH
        archive.mkdir(parents=True)
        shutil.copy2(
            REPOSITORY_ROOT
            / verifier.FRESH_ARCHIVE_RELATIVE_PATH
            / "README.md",
            archive / "README.md",
        )
        return configuration, archive

    def test_consumed_v11_diagnostic_archive_is_exactly_pinned(self) -> None:
        verifier._validate_consumed_v11_history(
            REPOSITORY_ROOT,
            verifier.PedestalEvidenceError,
        )

    def test_consumed_v11_diagnostic_tampering_fails_closed(self) -> None:
        source = (
            REPOSITORY_ROOT
            / "docs/evidence/original-1.21.1/pedestal-v11"
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository = Path(temporary_directory)
            destination = (
                repository
                / "docs/evidence/original-1.21.1/pedestal-v11"
            )
            destination.parent.mkdir(parents=True)
            shutil.copytree(source, destination)
            with (destination / "controller/original-client.log").open("ab") as handle:
                handle.write(b"tampered")
            with self.assertRaises(verifier.PedestalEvidenceError):
                verifier._validate_consumed_v11_history(
                    repository,
                    verifier.PedestalEvidenceError,
                )

    def test_fresh_archive_is_byte_exact_and_rejects_contamination(self) -> None:
        mutations = (
            lambda repository, archive: self.flip_readme_byte(archive),
            lambda repository, archive: (archive / "reports").mkdir(),
            lambda repository, archive: self.replace_readme_with_symlink(
                repository,
                archive,
            ),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation.__name__):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    _configuration, archive = self.isolated_archive_configuration(
                        repository
                    )
                    verifier.validate_fresh_archive(
                        repository_root=repository,
                        archive_path=archive,
                    )
                    mutation(repository, archive)
                    with self.assertRaises(verifier.PedestalEvidenceError):
                        verifier.validate_fresh_archive(
                            repository_root=repository,
                            archive_path=archive,
                        )

    @staticmethod
    def flip_readme_byte(archive: Path) -> None:
        readme = archive / "README.md"
        content = bytearray(readme.read_bytes())
        content[0] ^= 1
        readme.write_bytes(content)

    @staticmethod
    def replace_readme_with_symlink(repository: Path, archive: Path) -> None:
        readme = archive / "README.md"
        foreign = repository / "foreign-readme.md"
        foreign.write_bytes(readme.read_bytes())
        readme.unlink()
        readme.symlink_to(foreign)

    def test_v12_verifier_rejects_the_v11_configuration(self) -> None:
        configuration = client.load_configuration()
        v11_manifest = (
            BASELINE_DIRECTORY
            / "original-fabric-1.21.1-published-0.1.7-v11.json"
        )
        with self.assertRaises(verifier.PedestalEvidenceError):
            verifier.validate_pinned_contract(
                repository_root=REPOSITORY_ROOT,
                manifest_path=v11_manifest,
                harness_path=configuration.harness_path,
            )
        with self.assertRaisesRegex(
            client.BaselineError,
            "exact active v12 contract",
        ):
            client.verify_pedestal_evidence_verifier_binding(
                client.load_configuration(v11_manifest)
            )

    def test_controller_pins_and_describes_the_v12_adapter(self) -> None:
        configuration = client.load_configuration()
        descriptor = client.scenario_verifier_descriptor(
            configuration,
            "pedestal-baseline",
        )
        self.assertEqual(
            descriptor,
            {
                "path": "scripts/baseline/original_pedestal_evidence_v12.py",
                "size": client.PEDESTAL_EVIDENCE_VERIFIER_SIZE,
                "sha256": client.PEDESTAL_EVIDENCE_VERIFIER_SHA256,
            },
        )

        with tempfile.TemporaryDirectory() as temporary_directory:
            tampered = Path(temporary_directory) / VERIFIER_PATH.name
            content = bytearray(VERIFIER_PATH.read_bytes())
            content[-2] ^= 1
            tampered.write_bytes(content)
            with mock.patch.object(
                client,
                "PEDESTAL_EVIDENCE_VERIFIER_PATH",
                tampered,
            ):
                with self.assertRaisesRegex(client.BaselineError, "SHA-256"):
                    client.load_pedestal_evidence_verifier()

    def test_pinned_profile_is_unique_fresh_and_immutable(self) -> None:
        configuration = client.load_configuration()
        self.assertEqual(configuration.manifest_path, REPOSITORY_ROOT / verifier.PROFILE_RELATIVE_PATH)
        self.assertEqual(configuration.manifest_path.stat().st_size, verifier.PROFILE_SIZE)
        self.assertEqual(sha256_file(configuration.manifest_path), verifier.PROFILE_SHA256)
        verifier.validate_fresh_contract(
            repository_root=REPOSITORY_ROOT,
            manifest_path=configuration.manifest_path,
            harness_path=configuration.harness_path,
            runtime_path=client.runtime_root(configuration),
            archive_path=REPOSITORY_ROOT / "docs/evidence/original-1.21.1/pedestal-v12",
            sha256_file=sha256_file,
        )

    def test_implemented_harness_opens_the_lifecycle_gate(self) -> None:
        configuration = client.load_configuration()
        self.assertEqual(client.harness_spec(configuration)["status"], "implemented")
        client.require_capture_harness(configuration)

    def test_check_prelaunch_failure_stops_before_java_and_command_generation(
        self,
    ) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            with (
                mock.patch.object(client, "require_capture_harness"),
                mock.patch.object(client, "verify_reference_bundle"),
                mock.patch.object(client, "verify_harness_artifact"),
                mock.patch.object(
                    client,
                    "verify_owned_runtime",
                    return_value=root,
                ),
                mock.patch.object(client, "verify_installed_game"),
                mock.patch.object(client, "verify_runtime_lock"),
                mock.patch.object(client, "verify_staged_reference"),
                mock.patch.object(
                    client,
                    "resolve_scenario_id",
                    return_value="pedestal-baseline",
                ),
                mock.patch.object(
                    client,
                    "verify_scenario_prelaunch_contract",
                    side_effect=client.BaselineError("rejected verifier"),
                ) as prelaunch,
                mock.patch.object(client, "verify_capture_layout") as layout,
                mock.patch.object(client, "assert_runtime_not_running") as running,
                mock.patch.object(client, "resolve_java_21") as resolve_java,
                mock.patch.object(client, "generate_launch_command") as generate,
            ):
                with self.assertRaisesRegex(client.BaselineError, "rejected verifier"):
                    client._check_environment_locked(
                        configuration,
                        "pedestal-baseline",
                    )
            prelaunch.assert_called_once_with(configuration, "pedestal-baseline")
            layout.assert_not_called()
            running.assert_not_called()
            resolve_java.assert_not_called()
            generate.assert_not_called()

    def test_check_invokes_the_exact_fresh_archive_gate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            configuration, _archive = self.isolated_archive_configuration(
                Path(temporary_directory)
            )
            verifier_stub = mock.Mock()
            verifier_stub.FRESH_ARCHIVE_RELATIVE_PATH = (
                verifier.FRESH_ARCHIVE_RELATIVE_PATH
            )
            verifier_stub.validate_fresh_archive.side_effect = (
                verifier.validate_fresh_archive
            )
            java = Path("/fixture-java")
            command = [str(java)]
            with (
                mock.patch.object(client, "require_capture_harness"),
                mock.patch.object(client, "verify_reference_bundle"),
                mock.patch.object(client, "verify_harness_artifact"),
                mock.patch.object(
                    client,
                    "verify_owned_runtime",
                    return_value=Path(temporary_directory),
                ),
                mock.patch.object(client, "verify_installed_game"),
                mock.patch.object(client, "verify_runtime_lock"),
                mock.patch.object(client, "verify_staged_reference"),
                mock.patch.object(
                    client,
                    "verify_pedestal_evidence_verifier_binding",
                    return_value=verifier_stub,
                ),
                mock.patch.object(client, "verify_capture_layout"),
                mock.patch.object(client, "assert_runtime_not_running"),
                mock.patch.object(client, "resolve_java_21", return_value=java),
                mock.patch.object(
                    client,
                    "generate_launch_command",
                    return_value=command,
                ),
                mock.patch.object(client, "verify_launch_command"),
            ):
                self.assertEqual(
                    client._check_environment_locked(
                        configuration,
                        "pedestal-baseline",
                    ),
                    (java, command),
                )
            verifier_stub.validate_pinned_contract.assert_called_once()
            verifier_stub.validate_fresh_archive.assert_called_once()

    def test_prelaunch_verifier_failure_cannot_reach_the_launch_seal(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            caffeinate = root / "caffeinate"
            caffeinate.write_bytes(b"placeholder")
            with (
                mock.patch.object(client, "CAFFEINATE_PATH", caffeinate),
                mock.patch.object(
                    client, "verify_owned_runtime", return_value=root
                ),
                mock.patch.object(client, "verify_installed_game"),
                mock.patch.object(client, "verify_runtime_lock"),
                mock.patch.object(client, "verify_staged_reference"),
                mock.patch.object(
                    client,
                    "verify_scenario_prelaunch_contract",
                    side_effect=client.BaselineError("rejected verifier"),
                ),
                mock.patch.object(client, "verify_capture_layout") as layout,
                mock.patch.object(client, "launch_attempt_descriptor") as seal,
            ):
                with self.assertRaisesRegex(client.BaselineError, "rejected verifier"):
                    client._run_owned_client_locked(
                        configuration,
                        "pedestal-baseline",
                        Path("/java"),
                        ["java"],
                    )
            layout.assert_not_called()
            seal.assert_not_called()
            self.assertFalse(client.launch_attempt_path(configuration, root).exists())

    def test_late_prelaunch_verifier_failure_cannot_reach_seal_or_process(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            caffeinate = root / "caffeinate"
            caffeinate.write_bytes(b"placeholder")
            late_failure = client.BaselineError("late rejected verifier")
            with (
                mock.patch.object(client, "CAFFEINATE_PATH", caffeinate),
                mock.patch.object(
                    client, "verify_owned_runtime", return_value=root
                ),
                mock.patch.object(client, "verify_installed_game"),
                mock.patch.object(client, "verify_runtime_lock"),
                mock.patch.object(client, "verify_staged_reference"),
                mock.patch.object(client, "verify_capture_layout"),
                mock.patch.object(client, "assert_runtime_not_running"),
                mock.patch.object(
                    client,
                    "verify_scenario_prelaunch_contract",
                    side_effect=(None, late_failure),
                ) as prelaunch,
                mock.patch.object(
                    client, "launch_attempt_descriptor", return_value={}
                ) as descriptor,
                mock.patch.object(client, "write_json_exclusive") as seal,
                mock.patch.object(client.subprocess, "Popen") as process,
            ):
                with self.assertRaisesRegex(
                    client.BaselineError, "late rejected verifier"
                ):
                    client._run_owned_client_locked(
                        configuration,
                        "pedestal-baseline",
                        Path("/java"),
                        ["java"],
                    )
            self.assertEqual(prelaunch.call_count, 2)
            descriptor.assert_called_once()
            seal.assert_not_called()
            process.assert_not_called()
            self.assertFalse(client.launch_attempt_path(configuration, root).exists())

    def test_archive_contamination_at_second_gate_prevents_seal_and_process(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository = Path(temporary_directory)
            configuration, archive = self.isolated_archive_configuration(
                repository
            )
            caffeinate = repository / "caffeinate"
            caffeinate.write_bytes(b"placeholder")
            verifier_stub = mock.Mock()
            verifier_stub.FRESH_ARCHIVE_RELATIVE_PATH = (
                verifier.FRESH_ARCHIVE_RELATIVE_PATH
            )
            verifier_stub.validate_fresh_archive.side_effect = (
                verifier.validate_fresh_archive
            )

            def contaminate_archive(*_arguments: object) -> dict[str, object]:
                (archive / "unexpected.txt").write_text(
                    "contaminated\n",
                    encoding="utf-8",
                )
                return {}

            with (
                mock.patch.object(client, "CAFFEINATE_PATH", caffeinate),
                mock.patch.object(
                    client,
                    "verify_owned_runtime",
                    return_value=repository,
                ),
                mock.patch.object(client, "verify_installed_game"),
                mock.patch.object(client, "verify_runtime_lock"),
                mock.patch.object(client, "verify_staged_reference"),
                mock.patch.object(
                    client,
                    "verify_pedestal_evidence_verifier_binding",
                    return_value=verifier_stub,
                ),
                mock.patch.object(client, "verify_capture_layout"),
                mock.patch.object(client, "assert_runtime_not_running"),
                mock.patch.object(
                    client,
                    "launch_attempt_descriptor",
                    side_effect=contaminate_archive,
                ) as descriptor,
                mock.patch.object(client, "write_json_exclusive") as seal,
                mock.patch.object(client.subprocess, "Popen") as process,
            ):
                with self.assertRaisesRegex(
                    client.BaselineError,
                    "archive inventory changed",
                ):
                    client._run_owned_client_locked(
                        configuration,
                        "pedestal-baseline",
                        Path("/java"),
                        ["java"],
                    )
            self.assertEqual(verifier_stub.validate_pinned_contract.call_count, 2)
            self.assertEqual(verifier_stub.validate_fresh_archive.call_count, 2)
            descriptor.assert_called_once()
            seal.assert_not_called()
            process.assert_not_called()
            self.assertFalse(client.launch_attempt_path(configuration, repository).exists())

    def test_strict_verifier_accepts_the_exact_future_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            summary = verifier.validate_evidence(
                scenario_root=scenario,
                world_path=world,
                report=report,
                expected_artifacts=artifacts,
                decode_png=lambda _path: BRIGHT_IMAGE,
                assert_image_is_not_blank=lambda _image: None,
                sha256_file=sha256_file,
            )
            self.assertEqual(summary.assertion_count, 74)
            self.assertEqual(summary.screenshot_count, 4)
            self.assertEqual(summary.persistence_material_changed_pixel_ratio, 0.0)

    def test_all_74_assertion_semantics_match_the_independent_golden_digest(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            _scenario, _world, report, _artifacts = contract_fixture(
                Path(temporary_directory)
            )
            self.assertEqual(
                assertion_semantics_sha256(report),
                GOLDEN_ASSERTION_SEMANTICS_SHA256,
            )

    def test_map_assertion_text_uses_exact_java_key_order(self) -> None:
        cases = (
            (
                ("pedestal", "registry", "default_properties"),
                "pedestal_default_properties_exact",
                "{cloth_color=white, decoration=false, facing=north, "
                "shape=full, waterlogged=false}",
                "{waterlogged=false, shape=full, facing=north, "
                "decoration=false, cloth_color=white}",
                ("expected", "actual"),
            ),
            (
                ("pedestal", "placement", "block_entity_presence"),
                "pedestal_block_entity_presence_by_shape_exact",
                "{full=true, bottom=false, middle=false, top=true}",
                "{top=true, middle=false, bottom=false, full=true}",
                ("actual",),
            ),
            (
                ("pedestal", "inventory", "available_slot_counts"),
                "pedestal_sided_inventory_closed_exact",
                "{down=0, up=0, north=0, south=0, west=0, east=0};"
                "insert_denied=true;extract_denied=true",
                "{east=0, west=0, south=0, north=0, up=0, down=0};"
                "insert_denied=true;extract_denied=true",
                ("actual",),
            ),
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            _scenario, _world, report, _artifacts = contract_fixture(
                Path(temporary_directory)
            )
            for path, name, canonical, reordered, fields in cases:
                with self.subTest(name=name):
                    candidate = copy.deepcopy(report)
                    container = candidate
                    for component in path[:-1]:
                        container = container[component]
                    mapping = container[path[-1]]
                    container[path[-1]] = dict(reversed(tuple(mapping.items())))

                    semantics = verifier._expected_assertion_semantics(candidate)
                    for field in fields:
                        index = 0 if field == "expected" else 1
                        self.assertEqual(semantics[name][index], canonical)
                    verifier._validate_assertions(
                        candidate["assertions"],
                        candidate,
                        verifier.PedestalEvidenceError,
                    )

                    for field in fields:
                        with self.subTest(name=name, field=field):
                            forged = copy.deepcopy(candidate)
                            assertion = next(
                                value
                                for value in forged["assertions"]
                                if value["name"] == name
                            )
                            assertion[field] = reordered
                            with self.assertRaises(verifier.PedestalEvidenceError):
                                verifier._validate_assertions(
                                    forged["assertions"],
                                    forged,
                                    verifier.PedestalEvidenceError,
                                )

    def test_state_assertion_actuals_reject_non_java_property_order(self) -> None:
        def reversed_property_order(value: str) -> str:
            prefix, separator, properties = value.partition("[")
            self.assertEqual(separator, "[")
            self.assertTrue(properties.endswith("]"))
            entries = properties[:-1].split(",")
            return prefix + "[" + ",".join(reversed(entries)) + "]"

        cases = (
            (
                "pedestal_native_standalone_placement_exact",
                ("pedestal", "placement", "standalone", "placed_state"),
            ),
            (
                "pedestal_gallery_server_snapshot_exact",
                ("pedestal", "gallery_snapshot", 0, "state"),
            ),
        )
        for name, path in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as root:
                scenario, world, report, artifacts = contract_fixture(Path(root))
                container = report
                for component in path[:-1]:
                    container = container[component]
                field = path[-1]
                original = container[field]
                reordered = reversed_property_order(original)
                container[field] = reordered
                report["assertions"] = assertions(report)

                assertion = next(
                    value for value in report["assertions"] if value["name"] == name
                )
                self.assertIn(reordered, assertion["actual"])
                with self.assertRaisesRegex(
                    verifier.PedestalEvidenceError,
                    "Pedestal state property order/format changed",
                ):
                    verifier.validate_evidence(
                        scenario_root=scenario,
                        world_path=world,
                        report=report,
                        expected_artifacts=artifacts,
                        decode_png=lambda _path: BRIGHT_IMAGE,
                        assert_image_is_not_blank=lambda _image: None,
                        sha256_file=sha256_file,
                    )

    def test_camera_assertions_use_the_exact_java_pose_text(self) -> None:
        exact = (
            "first_person=true;x=0.5;y=121.0;z=-15.5;"
            "yaw=0.0;pitch=10.0;on_ground=true"
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            _scenario, _world, report, _artifacts = contract_fixture(
                Path(temporary_directory)
            )
            semantics = verifier._expected_assertion_semantics(report)
            for phase in verifier.PHASES:
                self.assertEqual(
                    semantics[f"capture_camera_exact:{phase}"],
                    (exact, exact),
                )

    def test_every_assertion_rejects_each_forged_semantic_field(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            _scenario, _world, report, _artifacts = contract_fixture(
                Path(temporary_directory)
            )
            for name in verifier.EXPECTED_ASSERTION_NAMES:
                for field in ("expected", "actual"):
                    with self.subTest(name=name, field=field):
                        candidate = copy.deepcopy(report)
                        assertion = next(
                            value
                            for value in candidate["assertions"]
                            if value["name"] == name
                        )
                        assertion[field] = "forged"
                        with self.assertRaises(verifier.PedestalEvidenceError):
                            verifier._validate_assertions(
                                candidate["assertions"],
                                candidate,
                                verifier.PedestalEvidenceError,
                            )

    def test_strict_verifier_rejects_canonical_missing_texture_magenta(self) -> None:
        pixels = bytearray(BRIGHT_IMAGE.pixels)
        pixels[:3] = bytes((248, 0, 248))
        missing_texture_image = BrightImage()
        missing_texture_image.pixels = bytes(pixels)
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            with self.assertRaises(verifier.PedestalEvidenceError):
                verifier.validate_evidence(
                    scenario_root=scenario,
                    world_path=world,
                    report=report,
                    expected_artifacts=artifacts,
                    decode_png=lambda _path: missing_texture_image,
                    assert_image_is_not_blank=lambda _image: None,
                    sha256_file=sha256_file,
                )

    def test_strict_verifier_rejects_empty_slot_vertical_carpet_execution(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            report["pedestal"]["dispensers"]["observations"].append(
                {
                    "kind": "carpet",
                    "direction": "up",
                }
            )
            with self.assertRaises(verifier.PedestalEvidenceError):
                verifier.validate_evidence(
                    scenario_root=scenario,
                    world_path=world,
                    report=report,
                    expected_artifacts=artifacts,
                    decode_png=lambda _path: BRIGHT_IMAGE,
                    assert_image_is_not_blank=lambda _image: None,
                    sha256_file=sha256_file,
                )

    def test_strict_verifier_rejects_occupied_fallback_and_client_removal_drift(
        self,
    ) -> None:
        raw_mutations = (
            lambda report: report["pedestal"]["dispensers"]["observations"][10]
            .update({"pedestal_item_slot": "empty"}),
            lambda report: report["pedestal"]["dispensers"]["ejected_items"].clear(),
            lambda report: report["pedestal"]["dispensers"].update(
                {"arrow_projectiles": 1}
            ),
            lambda report: report["pedestal"]["transitions"].update(
                {"stack_client_lookup_absent": False}
            ),
            lambda report: report["pedestal"]["transitions"].update(
                {"stack_client_retained_reference_removed": False}
            ),
            lambda report: report["pedestal"]["transitions"].update(
                {"replacement_client_lookup_absent": False}
            ),
            lambda report: report["pedestal"]["transitions"].update(
                {"replacement_client_retained_reference_removed": False}
            ),
            lambda report: report["pedestal"]["transitions"].update(
                {"replacement_client_air": False}
            ),
        )
        for mutate in raw_mutations:
            with self.subTest(mutation=mutate), tempfile.TemporaryDirectory() as root:
                scenario, world, report, artifacts = contract_fixture(Path(root))
                mutate(report)
                with self.assertRaises(verifier.PedestalEvidenceError):
                    verifier.validate_evidence(
                        scenario_root=scenario,
                        world_path=world,
                        report=report,
                        expected_artifacts=artifacts,
                        decode_png=lambda _path: BRIGHT_IMAGE,
                        assert_image_is_not_blank=lambda _image: None,
                        sha256_file=sha256_file,
                    )

        assertion_names = (
            "pedestal_occupied_carpet_falls_through_to_display_exact",
            "pedestal_full_target_falls_through_to_generic_item_ejection_exact",
            "pedestal_stack_transition_client_block_entity_removed",
            "pedestal_replacement_client_block_entity_removed",
        )
        for name in assertion_names:
            with self.subTest(assertion=name), tempfile.TemporaryDirectory() as root:
                scenario, world, report, artifacts = contract_fixture(Path(root))
                assertion = next(
                    value for value in report["assertions"] if value["name"] == name
                )
                assertion["actual"] = "forged"
                with self.assertRaises(verifier.PedestalEvidenceError):
                    verifier.validate_evidence(
                        scenario_root=scenario,
                        world_path=world,
                        report=report,
                        expected_artifacts=artifacts,
                        decode_png=lambda _path: BRIGHT_IMAGE,
                        assert_image_is_not_blank=lambda _image: None,
                        sha256_file=sha256_file,
                    )

    def test_strict_verifier_rejects_boolean_integer_substitution(self) -> None:
        mutations = (
            lambda report: report["pedestal"]["placement"]["standalone"].update(
                {"before_count": True}
            ),
            lambda report: report["pedestal"]["placement"]["standalone"].update(
                {"after_count": False}
            ),
            lambda report: report["pedestal"]["inventory"].update(
                {"max_count_per_stack": True}
            ),
            lambda report: report["pedestal"]["inventory"][
                "available_slot_counts"
            ].update({"down": False}),
            lambda report: report["pedestal"]["dispensers"].update(
                {"arrow_projectiles": False}
            ),
            lambda report: report["pedestal"]["transitions"][
                "stack_drops"
            ].update({"minecraft:diamond": True}),
        )
        for mutate in mutations:
            with self.subTest(mutation=mutate), tempfile.TemporaryDirectory() as root:
                scenario, world, report, artifacts = contract_fixture(Path(root))
                mutate(report)
                report["assertions"] = assertions(report)
                with self.assertRaises(verifier.PedestalEvidenceError):
                    verifier.validate_evidence(
                        scenario_root=scenario,
                        world_path=world,
                        report=report,
                        expected_artifacts=artifacts,
                        decode_png=lambda _path: BRIGHT_IMAGE,
                        assert_image_is_not_blank=lambda _image: None,
                        sha256_file=sha256_file,
                    )

    def test_strict_verifier_rejects_recursive_json_type_drift(self) -> None:
        def mutate_resource_pin(report: dict[str, object]) -> None:
            pin = next(iter(report["pedestal"]["resource_pins"].values()))
            pin["size"] = float(pin["size"])

        mutations = (
            lambda report: report.update({"schema": 4.0}),
            lambda report: report["world"].update({"integrated": 1}),
            lambda report: report["artifacts"][0].update({"size": True}),
            lambda report: report["pedestal"]["data"].update(
                {"pickaxe_mineable": 1}
            ),
            lambda report: report["pedestal"]["placement"][
                "block_entity_presence"
            ].update({"full": 1}),
            mutate_resource_pin,
            lambda report: report["screenshots"][0].update({"edited": 0}),
            lambda report: report["screenshots"][0].update({"width": 1920.0}),
        )
        for mutate in mutations:
            with self.subTest(mutation=mutate), tempfile.TemporaryDirectory() as root:
                scenario, world, report, artifacts = contract_fixture(Path(root))
                mutate(report)
                with self.assertRaises(verifier.PedestalEvidenceError):
                    verifier.validate_evidence(
                        scenario_root=scenario,
                        world_path=world,
                        report=report,
                        expected_artifacts=artifacts,
                        decode_png=lambda _path: BRIGHT_IMAGE,
                        assert_image_is_not_blank=lambda _image: None,
                        sha256_file=sha256_file,
                    )

    def test_strict_verifier_rejects_non_finite_shape_numbers(self) -> None:
        for value in (float("nan"), float("inf"), float("-inf")):
            with self.subTest(value=value), tempfile.TemporaryDirectory() as root:
                scenario, world, report, artifacts = contract_fixture(Path(root))
                report["pedestal"]["shapes"]["full"]["volume"] = value
                with self.assertRaises(verifier.PedestalEvidenceError):
                    verifier.validate_evidence(
                        scenario_root=scenario,
                        world_path=world,
                        report=report,
                        expected_artifacts=artifacts,
                        decode_png=lambda _path: BRIGHT_IMAGE,
                        assert_image_is_not_blank=lambda _image: None,
                        sha256_file=sha256_file,
                    )

    def test_shape_assertion_preserves_java_expected_vs_tolerated_actual(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            report["pedestal"]["shapes"]["bottom"]["min_x"] += 0.0000000009
            report["assertions"] = assertions(report)
            shape_assertion = next(
                value
                for value in report["assertions"]
                if value["name"] == "pedestal_outline_shapes_exact"
            )
            self.assertNotEqual(
                shape_assertion["expected"], shape_assertion["actual"]
            )
            verifier.validate_evidence(
                scenario_root=scenario,
                world_path=world,
                report=report,
                expected_artifacts=artifacts,
                decode_png=lambda _path: BRIGHT_IMAGE,
                assert_image_is_not_blank=lambda _image: None,
                sha256_file=sha256_file,
            )

    def test_strict_verifier_rejects_false_persistence_and_resource_claims(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            scenario, world, report, artifacts = contract_fixture(
                Path(temporary_directory)
            )
            report["pedestal"]["persistence_reopened_snapshot"][0]["item"] = "empty"
            with self.assertRaises(verifier.PedestalEvidenceError):
                verifier.validate_evidence(
                    scenario_root=scenario,
                    world_path=world,
                    report=report,
                    expected_artifacts=artifacts,
                    decode_png=lambda _path: BRIGHT_IMAGE,
                    assert_image_is_not_blank=lambda _image: None,
                    sha256_file=sha256_file,
                )

    def test_strict_verifier_rejects_placement_and_nbt_semantic_drift(self) -> None:
        mutations = (
            lambda report: report["pedestal"]["placement"]["standalone"].update(
                {
                    "placed_state": state(
                        shape="full",
                        decoration=True,
                        color="red",
                    )
                }
            ),
            lambda report: report["pedestal"]["inventory"]["nbt_keys"].append(
                "foreign"
            ),
        )
        for mutate in mutations:
            with self.subTest(mutation=mutate), tempfile.TemporaryDirectory() as root:
                scenario, world, report, artifacts = contract_fixture(Path(root))
                mutate(report)
                with self.assertRaises(verifier.PedestalEvidenceError):
                    verifier.validate_evidence(
                        scenario_root=scenario,
                        world_path=world,
                        report=report,
                        expected_artifacts=artifacts,
                        decode_png=lambda _path: BRIGHT_IMAGE,
                        assert_image_is_not_blank=lambda _image: None,
                        sha256_file=sha256_file,
                    )


if __name__ == "__main__":
    unittest.main()
