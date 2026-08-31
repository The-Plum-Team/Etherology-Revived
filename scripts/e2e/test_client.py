from __future__ import annotations

from contextlib import contextmanager, redirect_stdout
from dataclasses import replace
import io
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest import mock
import zipfile


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import client


def fabric_jar_bytes(
    mod_id: str,
    version: str,
    nested_jars: list[tuple[str, bytes]] | None = None,
    metadata_overrides: dict[str, object] | None = None,
    entries: list[tuple[str, bytes]] | None = None,
) -> bytes:
    nested_jars = nested_jars or []
    metadata_overrides = metadata_overrides or {}
    entries = entries or []
    metadata = {
        "schemaVersion": 1,
        "id": mod_id,
        "version": version,
        "jars": [{"file": name} for name, _ in nested_jars],
    }
    metadata.update(metadata_overrides)
    output = io.BytesIO()
    with zipfile.ZipFile(output, mode="w") as archive:
        archive.writestr("fabric.mod.json", json.dumps(metadata))
        for name, content in nested_jars:
            archive.writestr(name, content)
        for name, content in entries:
            archive.writestr(name, content)
    return output.getvalue()


def production_jar_bytes(configuration: client.ResolvedConfiguration) -> bytes:
    production = client.artifact_spec(configuration, "production")
    nested_jars = [
        (
            f"META-INF/jars/{mod_id}.jar",
            fabric_jar_bytes(mod_id, "test-nested-version"),
        )
        for mod_id in client.require_list(production, "required_nested_mod_ids")
    ]
    return fabric_jar_bytes(
        str(production["mod_id"]),
        configuration.properties[str(production["version_property"])],
        nested_jars,
        {
            "depends": {
                "minecraft": f"={configuration.runtime_lane['runtime_version']}"
            }
        },
    )


def harness_jar_bytes(
    configuration: client.ResolvedConfiguration,
    metadata_overrides: dict[str, object] | None = None,
    entries: list[tuple[str, bytes]] | None = None,
) -> bytes:
    production = client.artifact_spec(configuration, "production")
    harness = client.artifact_spec(configuration, "harness")
    entrypoint = str(harness["client_entrypoint"])
    metadata: dict[str, object] = {
        "environment": "client",
        "entrypoints": {"client": [entrypoint]},
        "mixins": [
            {
                "config": "etherology-e2e-harness.mixins.json",
                "environment": "client",
            }
        ],
        "depends": {
            "minecraft": f"={configuration.runtime_lane['runtime_version']}",
            str(production["mod_id"]): (
                f"={configuration.properties[str(production['version_property'])]}"
            ),
        },
    }
    metadata.update(metadata_overrides or {})
    class_entries = [
        (entrypoint.replace(".", "/") + ".class", b"isolated harness bytecode"),
        (
            "dev/theplumteam/etherology/e2e/fabric/mixin/"
            "GameRendererMixin.class",
            b"isolated mixin bytecode",
        ),
        (
            "etherology-e2e-harness.mixins.json",
            json.dumps(
                {
                    "required": True,
                    "package": "dev.theplumteam.etherology.e2e.fabric.mixin",
                    "compatibilityLevel": "JAVA_17",
                    "client": ["GameRendererMixin"],
                    "injectors": {"defaultRequire": 1},
                }
            ).encode("utf-8"),
        ),
    ]
    class_entries.extend(entries or [])
    return fabric_jar_bytes(
        str(harness["mod_id"]),
        configuration.properties[str(harness["version_property"])],
        metadata_overrides=metadata,
        entries=class_entries,
    )


@contextmanager
def temporary_build_configuration(build_file_content: str):
    configuration = client.load_configuration()
    with tempfile.TemporaryDirectory() as temporary_directory:
        repository_root = Path(temporary_directory).resolve()
        build_file = repository_root / "fabric/build.gradle.kts"
        build_file.parent.mkdir(parents=True)
        build_file.write_text(build_file_content, encoding="utf-8")
        yield replace(configuration, repository_root=repository_root.resolve())


@contextmanager
def temporary_artifact_stage():
    configuration = client.load_configuration()
    with tempfile.TemporaryDirectory() as temporary_directory:
        repository_root = Path(temporary_directory).resolve()
        metadata_template = repository_root / "src/main/resources/fabric.mod.json"
        metadata_template.parent.mkdir(parents=True)
        metadata_template.write_text(
            (client.REPOSITORY_ROOT / "src/main/resources/fabric.mod.json").read_text(
                encoding="utf-8"
            ),
            encoding="utf-8",
        )
        staged_configuration = replace(
            configuration, repository_root=repository_root.resolve()
        )
        source_root = repository_root / "build-output"
        source_root.mkdir()
        source_paths = {
            "production": source_root / "production.jar",
            "harness": source_root / "harness.jar",
        }
        source_paths["production"].write_bytes(
            production_jar_bytes(staged_configuration)
        )
        source_paths["harness"].write_bytes(harness_jar_bytes(staged_configuration))
        runtime_root = repository_root / "runtime"
        mods_directory = runtime_root / "game/mods"
        mods_directory.mkdir(parents=True)
        target_paths = {
            role: mods_directory
            / str(client.artifact_spec(staged_configuration, role)["file_name"])
            for role in client.ARTIFACT_ROLES
        }
        yield (
            staged_configuration,
            source_paths,
            target_paths,
            runtime_root / "artifact-lock.json",
        )


class ConfigurationTests(unittest.TestCase):
    def test_manifest_resolves_exact_fabric_release_lane(self) -> None:
        configuration = client.load_configuration()

        self.assertEqual("fabric-1.20.1", configuration.artifact_lane["artifact_node"])
        self.assertEqual("1.20.1", configuration.runtime_lane["runtime_version"])
        self.assertEqual("0.17.3", configuration.runtime_lane["loader_version"])
        self.assertEqual(17, configuration.runtime_lane["java"])
        self.assertEqual(10, len(client.dependency_specs(configuration)))
        launch = client.require_object(configuration.manifest, "launch")
        resolution = client.require_object(launch, "resolution")
        self.assertEqual({"width": 960, "height": 540}, resolution)

    def test_capture_dimensions_are_independent_from_logical_window_size(self) -> None:
        configuration = client.load_configuration()

        self.assertEqual(
            {
                "kind": "composed-minecraft-framebuffer",
                "width": 1920,
                "height": 1080,
            },
            client.evidence_descriptor(configuration)["capture"],
        )

    def test_invalid_capture_dimensions_are_rejected(self) -> None:
        configuration = client.load_configuration()
        changed_manifest = json.loads(json.dumps(configuration.manifest))
        changed_manifest["evidence"]["capture"]["width"] = 0

        with self.assertRaisesRegex(client.E2EError, "evidence.capture.width"):
            client.validate_manifest_shape(changed_manifest, configuration.properties)

    def test_trinkets_uses_remapped_runtime_distribution(self) -> None:
        configuration = client.load_configuration()
        trinkets = next(
            dependency
            for dependency in client.dependency_specs(configuration)
            if dependency["mod_id"] == "trinkets"
        )

        self.assertEqual(
            "https://cdn.modrinth.com/data/5aaWibi9/versions/"
            "AHxQGtuC/trinkets-3.7.2.jar",
            trinkets["url"],
        )
        self.assertEqual(238532, trinkets["size"])
        self.assertEqual(
            "a6f11a4206c1285cd260a60d9bfe9cefa9914d2190d08b3c52050f42044b53b3",
            trinkets["sha256"],
        )

    def test_artifact_sources_resolve_from_release_and_build_configuration(self) -> None:
        configuration = client.load_configuration()

        self.assertEqual(
            "fabric/versions/1.20.1/build/libs/"
            "Etherology - Fabric - 1.20.1-0.1.8.jar",
            client.artifact_source_relative_path(configuration, "production"),
        )
        self.assertEqual(
            "fabric/versions/1.20.1/build/e2e-harness/libs/"
            "Etherology-E2E-Harness-Fabric-1.20.1-0.1.8.jar",
            client.artifact_source_relative_path(configuration, "harness"),
        )

    def test_harness_source_rejects_a_literal_gradle_archive_version(self) -> None:
        build_file_content = (
            client.REPOSITORY_ROOT / "fabric/build.gradle.kts"
        ).read_text(encoding="utf-8")
        task_prefix, separator, task_content = build_file_content.partition(
            "val remapE2eHarnessJar"
        )
        self.assertTrue(separator)
        changed_task_content = task_content.replace(
            "archiveVersion.set(project.version.toString())",
            'archiveVersion.set("0.1.8")',
            1,
        )
        changed_content = task_prefix + separator + changed_task_content
        self.assertNotEqual(build_file_content, changed_content)

        with temporary_build_configuration(changed_content) as configuration:
            with self.assertRaisesRegex(client.E2EError, "derive archiveVersion"):
                client.artifact_source_path(configuration, "harness")

    def test_runtime_path_is_beneath_repository_owned_state(self) -> None:
        configuration = client.load_configuration()
        expected_parent = client.STATE_ROOT / "runtimes"

        self.assertEqual(expected_parent, client.runtime_root(configuration).parent)
        self.assertEqual(
            [], client.profile_descriptor(configuration)["isolation"]["source_profiles"]
        )

    def test_evidence_scenarios_match_packaged_contract(self) -> None:
        configuration = client.load_configuration()
        contract = (
            configuration.repository_root / "docs/testing/E2E-CONTRACT.md"
        ).read_text(encoding="utf-8")
        scenario_table = contract.split("## Standard scenarios", 1)[1].split(
            "## Loader-specific bounded scenarios", 1
        )[0]
        contract_scenarios = [
            line.split("`", 2)[1]
            for line in scenario_table.splitlines()
            if line.startswith("| `")
        ]

        self.assertEqual(contract_scenarios, client.scenario_ids(configuration))
        self.assertEqual(
            {
                "kind": "composed-minecraft-framebuffer",
                "width": 1920,
                "height": 1080,
            },
            client.evidence_descriptor(configuration)["capture"],
        )

    def test_scenario_selection_is_exact_and_defaults_to_phase_zero(self) -> None:
        configuration = client.load_configuration()

        self.assertEqual(
            "phase0-smoke",
            client.resolve_scenario_id(configuration, None),
        )
        self.assertEqual(
            "storage-utilities",
            client.resolve_scenario_id(configuration, "storage-utilities"),
        )
        with self.assertRaisesRegex(client.E2EError, "Unsupported E2E scenario"):
            client.resolve_scenario_id(configuration, "phase0-smoke ")
        with self.assertRaisesRegex(client.E2EError, "Unsupported E2E scenario"):
            client.resolve_scenario_id(configuration, "unknown-scenario")

    def test_visual_capture_controls_are_explicit(self) -> None:
        configuration = client.load_configuration()
        options = client.parse_options(client.options_text(configuration))

        self.assertEqual(
            {
                "fullscreen": "false",
                "enableVsync": "false",
                "maxFps": "60",
                "graphicsMode": "1",
                "ao": "true",
                "renderClouds": '"true"',
                "particles": "0",
                "entityShadows": "true",
                "mipmapLevels": "4",
                "guiScale": "2",
                "fov": "0.0",
                "gamma": "0.5",
                "bobView": "false",
                "rawMouseInput": "false",
                "pauseOnLostFocus": "false",
                "lang": "en_us",
            },
            {
                name: options[name]
                for name in (
                    "fullscreen",
                    "enableVsync",
                    "maxFps",
                    "graphicsMode",
                    "ao",
                    "renderClouds",
                    "particles",
                    "entityShadows",
                    "mipmapLevels",
                    "guiScale",
                    "fov",
                    "gamma",
                    "bobView",
                    "rawMouseInput",
                    "pauseOnLostFocus",
                    "lang",
                )
            },
        )

    def test_dependency_version_drift_is_rejected(self) -> None:
        configuration = client.load_configuration()
        changed_properties = dict(configuration.properties)
        changed_properties["fabric_api_version_1_20_1"] = "0.0.0-invalid"

        with self.assertRaisesRegex(client.E2EError, "does not encode"):
            client.validate_manifest_shape(configuration.manifest, changed_properties)


class OwnershipTests(unittest.TestCase):
    def write_owned_process_state(
        self,
        state_root: Path,
        profile_id: str,
        scenario: str | None = None,
    ) -> Path:
        runtime = state_root / "runtimes" / profile_id
        game_directory = runtime / "game"
        game_directory.mkdir(parents=True)
        marker = {
            "schema": 1,
            "profile_id": profile_id,
            "managed_by": "scripts/e2e/client.py",
            "isolation": {
                "scope": "repository-owned-ignored-state",
                "source_profiles": [],
            },
        }
        (runtime / client.PROFILE_MARKER_NAME).write_text(
            json.dumps(marker),
            encoding="utf-8",
        )
        logs_directory = state_root / "logs"
        logs_directory.mkdir(exist_ok=True)
        log_path = logs_directory / f"{profile_id}.log"
        log_path.write_text("owned test log", encoding="utf-8")
        state: dict[str, object] = {
            "schema": 1,
            "profile_id": profile_id,
            "pid": 12345,
            "version_id": "fabric-loader-test-1.20.1",
            "game_directory": str(game_directory),
            "log": str(log_path),
        }
        if scenario is not None:
            state["scenario"] = scenario
        state_path = state_root / f"{profile_id}-current.json"
        state_path.write_text(json.dumps(state), encoding="utf-8")
        return state_path

    def test_linked_state_root_is_rejected_before_lifecycle_access(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            target = temporary_root / "target"
            target.mkdir()
            linked_state = temporary_root / "state"
            linked_state.symlink_to(target, target_is_directory=True)

            with self.assertRaisesRegex(client.E2EError, "must not be a symlink"):
                client.ensure_owned_state_roots(linked_state)

    def test_unmarked_existing_directory_is_never_adopted(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            unmarked_root = Path(temporary_directory) / "already-exists"
            unmarked_root.mkdir()

            with self.assertRaisesRegex(client.E2EError, "Refusing to adopt"):
                client.verify_profile_marker(configuration, unmarked_root)

    def test_linked_runtime_directory_is_rejected(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            target = temporary_root / "target"
            target.mkdir()
            linked_root = temporary_root / "linked-runtime"
            linked_root.symlink_to(target, target_is_directory=True)

            with self.assertRaisesRegex(client.E2EError, "missing or linked"):
                client.verify_profile_marker(configuration, linked_root)

    def test_unmarked_evidence_directory_is_never_adopted(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime_root = Path(temporary_directory) / "runtime"
            runtime_root.mkdir()
            (runtime_root / client.PROFILE_MARKER_NAME).write_text(
                json.dumps(client.profile_descriptor(configuration)),
                encoding="utf-8",
            )
            client.evidence_root(configuration, runtime_root).mkdir()

            with self.assertRaisesRegex(client.E2EError, "Refusing to adopt"):
                client.verify_evidence_layout(configuration, runtime_root)

    def test_evidence_layout_has_one_isolated_directory_per_scenario(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime_root = Path(temporary_directory) / "runtime"
            runtime_root.mkdir()
            (runtime_root / client.PROFILE_MARKER_NAME).write_text(
                json.dumps(client.profile_descriptor(configuration)),
                encoding="utf-8",
            )

            self.assertTrue(client.ensure_evidence_layout(configuration, runtime_root))
            client.verify_evidence_layout(configuration, runtime_root)

            evidence_root = client.evidence_root(configuration, runtime_root)
            self.assertEqual(
                set(client.scenario_ids(configuration)),
                {
                    path.name
                    for path in evidence_root.iterdir()
                    if path.name != client.EVIDENCE_MARKER_NAME
                },
            )

    def test_owned_state_inventory_accepts_legacy_and_scenario_states(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory) / "state"
            state_root.mkdir()
            self.write_owned_process_state(state_root, "etherology-e2e-legacy")
            self.write_owned_process_state(
                state_root,
                "etherology-e2e-current",
                "phase0-smoke",
            )

            states = client.owned_process_states(state_root)

        self.assertEqual(
            ["etherology-e2e-current", "etherology-e2e-legacy"],
            [str(state["profile_id"]) for _path, state in states],
        )

    def test_stale_owned_states_are_cleared_before_a_new_launch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory) / "state"
            state_root.mkdir()
            state_path = self.write_owned_process_state(
                state_root,
                "etherology-e2e-stale",
                "phase0-smoke",
            )

            with mock.patch.object(client, "process_exists", return_value=False):
                client.clear_stale_and_reject_live_owned_clients(state_root)

            self.assertFalse(state_path.exists())

    def test_live_client_from_an_older_owned_profile_blocks_launch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory) / "state"
            state_root.mkdir()
            self.write_owned_process_state(
                state_root,
                "etherology-e2e-older-profile",
            )

            with (
                mock.patch.object(client, "process_exists", return_value=True),
                mock.patch.object(client, "process_matches", return_value=True),
                self.assertRaisesRegex(client.E2EError, "already running"),
            ):
                client.clear_stale_and_reject_live_owned_clients(state_root)


class IntegrityTests(unittest.TestCase):
    def test_sha256_drift_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "dependency.jar"
            path.write_bytes(b"unexpected bytes")

            with self.assertRaisesRegex(client.E2EError, "SHA-256"):
                client.verify_exact_file(path, "0" * 64, None, "Pinned dependency")

    def test_nested_fabric_mod_ids_are_collected(self) -> None:
        nested = fabric_jar_bytes("nested-library", "2.0.0")
        outer = fabric_jar_bytes(
            "etherology",
            "0.1.8",
            [("META-INF/jars/nested-library.jar", nested)],
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "etherology.jar"
            path.write_bytes(outer)

            metadata = client.fabric_metadata(path)

        self.assertEqual(["etherology", "nested-library"], [row["id"] for row in metadata])

    def test_unsafe_nested_jar_path_is_rejected(self) -> None:
        outer = fabric_jar_bytes(
            "etherology",
            "0.1.8",
            [("../escaped.jar", fabric_jar_bytes("escaped", "1"))],
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "etherology.jar"
            path.write_bytes(outer)

            with self.assertRaisesRegex(client.E2EError, "unsafe nested JAR"):
                client.fabric_metadata(path)

    def test_packaged_harness_metadata_is_accepted(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "harness.jar"
            path.write_bytes(harness_jar_bytes(configuration))

            _sha256, _size, mod_ids = client.verify_artifact_metadata(
                configuration, "harness", path
            )

        self.assertEqual({"etherology_e2e_harness"}, mod_ids)

    def test_harness_without_exact_etherology_dependency_is_rejected(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "harness.jar"
            path.write_bytes(
                harness_jar_bytes(
                    configuration,
                    {
                        "depends": {
                            "minecraft": "=1.20.1",
                            "etherology": ">=0.1.8",
                        }
                    },
                )
            )

            with self.assertRaisesRegex(client.E2EError, "exact production"):
                client.verify_artifact_metadata(configuration, "harness", path)

    def test_harness_with_production_class_is_rejected(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "harness.jar"
            path.write_bytes(
                harness_jar_bytes(
                    configuration,
                    entries=[
                        (
                            "ru/feytox/etherology/AccidentalProductionClass.class",
                            b"production bytecode",
                        )
                    ],
                )
            )

            with self.assertRaisesRegex(client.E2EError, "isolated package"):
                client.verify_artifact_metadata(configuration, "harness", path)

    def test_harness_with_unlisted_nested_jar_is_rejected(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "harness.jar"
            path.write_bytes(
                harness_jar_bytes(
                    configuration,
                    entries=[("hidden-library.jar", b"not allowed")],
                )
            )

            with self.assertRaisesRegex(client.E2EError, "nested JAR"):
                client.verify_artifact_metadata(configuration, "harness", path)

    def test_harness_without_render_callback_mixin_is_rejected(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "harness.jar"
            path.write_bytes(
                harness_jar_bytes(
                    configuration,
                    metadata_overrides={"mixins": []},
                )
            )

            with self.assertRaisesRegex(client.E2EError, "client mixin config"):
                client.verify_artifact_metadata(configuration, "harness", path)


class ArtifactStagingTests(unittest.TestCase):
    def test_stage_publishes_one_lock_after_copying_both_artifacts(self) -> None:
        with temporary_artifact_stage() as (
            configuration,
            source_paths,
            target_paths,
            lock_path,
        ):
            with (
                mock.patch.object(client, "assert_runtime_not_running"),
                mock.patch.object(client, "verify_runtime", return_value=set()),
                mock.patch.object(
                    client,
                    "artifact_source_path",
                    side_effect=lambda _configuration, role: source_paths[role],
                ),
                mock.patch.object(
                    client,
                    "artifact_target_path",
                    side_effect=lambda _configuration, role, root=None: target_paths[role],
                ),
                mock.patch.object(
                    client,
                    "artifact_lock_path",
                    side_effect=lambda _configuration, root=None: lock_path,
                ),
            ):
                staged, lock = client.stage_artifacts(configuration)

            self.assertTrue(staged)
            self.assertEqual(2, lock["schema"])
            self.assertEqual(set(client.ARTIFACT_ROLES), set(lock["artifacts"]))
            for role in client.ARTIFACT_ROLES:
                self.assertEqual(
                    source_paths[role].read_bytes(), target_paths[role].read_bytes()
                )
            self.assertEqual(
                lock,
                json.loads(lock_path.read_text(encoding="utf-8")),
            )

    def test_failed_second_copy_does_not_replace_either_artifact(self) -> None:
        with temporary_artifact_stage() as (
            configuration,
            source_paths,
            target_paths,
            lock_path,
        ):
            for target_path in target_paths.values():
                target_path.write_bytes(b"previous staged artifact")
            copyfile = shutil.copyfile

            def copy_with_corrupt_harness(source: Path, destination: Path) -> None:
                if Path(source) == source_paths["harness"]:
                    Path(destination).write_bytes(b"x" * Path(source).stat().st_size)
                    return
                copyfile(source, destination)

            with (
                mock.patch.object(client, "assert_runtime_not_running"),
                mock.patch.object(client, "verify_runtime", return_value=set()),
                mock.patch.object(
                    client,
                    "artifact_source_path",
                    side_effect=lambda _configuration, role: source_paths[role],
                ),
                mock.patch.object(
                    client,
                    "artifact_target_path",
                    side_effect=lambda _configuration, role, root=None: target_paths[role],
                ),
                mock.patch.object(
                    client,
                    "artifact_lock_path",
                    side_effect=lambda _configuration, root=None: lock_path,
                ),
                mock.patch.object(
                    client.shutil,
                    "copyfile",
                    side_effect=copy_with_corrupt_harness,
                ),
            ):
                with self.assertRaisesRegex(client.E2EError, "SHA-256"):
                    client.stage_artifacts(configuration)

            for target_path in target_paths.values():
                self.assertEqual(b"previous staged artifact", target_path.read_bytes())
            self.assertFalse(lock_path.exists())


class LaunchTests(unittest.TestCase):
    def test_fabric_metadata_selects_inherited_vanilla_jar(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime_root = Path(temporary_directory)
            minecraft_version = str(configuration.runtime_lane["runtime_version"])
            minecraft_jar = (
                client.launcher_directory(configuration, runtime_root)
                / "versions"
                / minecraft_version
                / f"{minecraft_version}.jar"
            )
            minecraft_jar.parent.mkdir(parents=True)
            minecraft_jar.write_bytes(b"installed vanilla client")
            metadata_path = client.fabric_version_metadata_path(
                configuration, runtime_root
            )
            metadata_path.parent.mkdir(parents=True)
            metadata_path.write_text(
                json.dumps(
                    {
                        "id": client.version_id(configuration),
                        "inheritsFrom": minecraft_version,
                        "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                    }
                ),
                encoding="utf-8",
            )

            changed = client.normalize_fabric_version_metadata(
                configuration, runtime_root
            )

            self.assertTrue(changed)
            self.assertEqual(
                minecraft_version,
                json.loads(metadata_path.read_text(encoding="utf-8"))["jar"],
            )
            self.assertFalse(
                client.normalize_fabric_version_metadata(configuration, runtime_root)
            )

    def test_missing_generated_classpath_entry_is_rejected(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime_root = Path(temporary_directory)
            launcher_root = client.launcher_directory(configuration, runtime_root)
            launcher_root.mkdir(parents=True)
            missing_entry = launcher_root / "versions" / "missing.jar"
            command = [
                "java",
                "-Detherology.e2e.scenario=phase0-smoke",
                "-cp",
                str(missing_entry),
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--gameDir",
                str(client.game_directory(configuration, runtime_root)),
            ]

            with self.assertRaisesRegex(client.E2EError, "classpath entry is missing"):
                client.verify_launch_command(configuration, command, runtime_root)

    def test_launch_command_rejects_the_wrong_scenario_property(self) -> None:
        configuration = client.load_configuration()
        command = [
            "java",
            "-Detherology.e2e.scenario=storage-utilities",
            "net.fabricmc.loader.impl.launch.knot.KnotClient",
        ]

        with self.assertRaisesRegex(client.E2EError, "requested E2E scenario"):
            client.verify_launch_command(
                configuration,
                command,
                configured_scenario_id="phase0-smoke",
            )

    def test_wrong_inherited_client_jar_is_rejected(self) -> None:
        configuration = client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime_root = Path(temporary_directory)
            minecraft_version = str(configuration.runtime_lane["runtime_version"])
            minecraft_jar = (
                client.launcher_directory(configuration, runtime_root)
                / "versions"
                / minecraft_version
                / f"{minecraft_version}.jar"
            )
            minecraft_jar.parent.mkdir(parents=True)
            minecraft_jar.write_bytes(b"installed vanilla client")
            metadata_path = client.fabric_version_metadata_path(
                configuration, runtime_root
            )
            metadata_path.parent.mkdir(parents=True)
            metadata_path.write_text(
                json.dumps(
                    {
                        "id": client.version_id(configuration),
                        "inheritsFrom": minecraft_version,
                        "jar": "1.21.1",
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(client.E2EError, "unexpected inherited"):
                client.normalize_fabric_version_metadata(
                    configuration, runtime_root
                )

    def test_fatal_startup_log_is_not_reported_as_healthy(self) -> None:
        log_content = (
            "[ERROR] [FabricLoader/GameProvider]: "
            "Minecraft game provider couldn't locate the game!"
        )

        self.assertEqual(
            "Minecraft game provider couldn't locate the game",
            client.find_client_failure_marker(log_content),
        )

    def test_status_does_not_report_live_pid_with_fatal_startup_log(self) -> None:
        configuration = client.load_configuration()
        state = {
            "schema": 1,
            "profile_id": client.profile_spec(configuration)["id"],
            "pid": 12345,
            "started_utc": "20260831T000000Z",
            "scenario": "phase0-smoke",
            "version_id": client.version_id(configuration),
            "game_directory": str(client.game_directory(configuration)),
            "log": str(client.STATE_ROOT / "logs" / "fabric-1.20.1-test.log"),
        }
        output = io.StringIO()
        with (
            mock.patch.object(
                client, "load_configuration", return_value=configuration
            ),
            mock.patch.object(client, "read_process_state", return_value=state),
            mock.patch.object(client, "process_exists", return_value=True),
            mock.patch.object(client, "process_matches", return_value=True),
            mock.patch.object(
                client,
                "client_failure_marker",
                return_value="Minecraft game provider couldn't locate the game",
            ),
            redirect_stdout(output),
        ):
            status = client.status_command()

        self.assertEqual(2, status)
        self.assertIn("client failed", output.getvalue())
        self.assertNotIn("client is running", output.getvalue())


if __name__ == "__main__":
    unittest.main()
