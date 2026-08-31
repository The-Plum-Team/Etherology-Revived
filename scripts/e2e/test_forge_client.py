from __future__ import annotations

from contextlib import contextmanager
from dataclasses import replace
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import textwrap
import unittest
from unittest import mock
import zipfile


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_client


def forge_jar_bytes(
    mod_id: str,
    version: str,
    dependencies: dict[str, tuple[str, str]] | None = None,
    entries: list[tuple[str, bytes]] | None = None,
    manifest_attributes: dict[str, str] | None = None,
) -> bytes:
    dependency_sections = []
    for dependency_mod_id, (version_range, side) in (dependencies or {}).items():
        dependency_sections.append(
            textwrap.dedent(
                f"""
                [[dependencies.{mod_id}]]
                modId="{dependency_mod_id}"
                mandatory=true
                versionRange="{version_range}"
                ordering="NONE"
                side="{side}"
                """
            ).strip()
        )
    metadata = textwrap.dedent(
        f"""
        modLoader="javafml"
        loaderVersion="[47,)"
        license="test"

        [[mods]]
        modId="{mod_id}"
        version="{version}"
        displayName="Test"
        description='''test'''
        """
    ).strip()
    if dependency_sections:
        metadata += "\n\n" + "\n\n".join(dependency_sections)
    manifest_lines = ["Manifest-Version: 1.0"]
    manifest_lines.extend(
        f"{name}: {value}" for name, value in (manifest_attributes or {}).items()
    )
    output = io.BytesIO()
    with zipfile.ZipFile(output, mode="w") as archive:
        archive.writestr("META-INF/mods.toml", metadata + "\n")
        archive.writestr("META-INF/MANIFEST.MF", "\r\n".join(manifest_lines) + "\r\n")
        for name, content in entries or []:
            archive.writestr(name, content)
    return output.getvalue()


@contextmanager
def temporary_repository():
    with tempfile.TemporaryDirectory() as temporary_directory:
        root = Path(temporary_directory).resolve()
        for relative_path in (
            "release/release-matrix.json",
            "gradle.properties",
            "forge/build.gradle.kts",
        ):
            source = forge_client.REPOSITORY_ROOT / relative_path
            target = root / relative_path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
        manifest_path = root / "scripts/e2e/forge-1.20.1-profile.json"
        manifest_path.parent.mkdir(parents=True)
        manifest_path.write_text(
            forge_client.MANIFEST_PATH.read_text(encoding="utf-8"), encoding="utf-8"
        )
        yield root, manifest_path


def create_inherited_version_fixture(
    configuration: forge_client.ResolvedConfiguration,
    root: Path,
) -> tuple[Path, Path]:
    launcher = forge_client.launcher_directory(configuration, root)
    minecraft_version = str(configuration.runtime_lane["runtime_version"])
    forge_id = forge_client.forge_version_id(configuration)
    parent_directory = launcher / "versions" / minecraft_version
    child_directory = launcher / "versions" / forge_id
    parent_directory.mkdir(parents=True)
    child_directory.mkdir(parents=True)
    client_content = b"minecraft-client"
    (parent_directory / f"{minecraft_version}.json").write_text(
        json.dumps(
            {
                "id": minecraft_version,
                "downloads": {
                    "client": {
                        "sha1": hashlib_sha1(client_content),
                        "size": len(client_content),
                    }
                },
            }
        ),
        encoding="utf-8",
    )
    (child_directory / f"{forge_id}.json").write_text(
        json.dumps(
            {
                "id": forge_id,
                "inheritsFrom": minecraft_version,
                "mainClass": forge_client.FORGE_MAIN_CLASS,
            }
        ),
        encoding="utf-8",
    )
    parent_jar = parent_directory / f"{minecraft_version}.jar"
    parent_jar.write_bytes(client_content)
    return parent_jar, child_directory / f"{forge_id}.jar"


class ConfigurationTests(unittest.TestCase):
    def test_manifest_resolves_exact_forge_lane_and_installer(self) -> None:
        configuration = forge_client.load_configuration()

        self.assertEqual("forge-1.20.1", configuration.artifact_lane["artifact_node"])
        self.assertEqual("forge", configuration.runtime_lane["loader"])
        self.assertEqual("1.20.1-47.4.9", configuration.runtime_lane["loader_version"])
        self.assertEqual(17, configuration.runtime_lane["java"])
        self.assertEqual(
            "58fc5db6e3dc47745475375be6fa275e68320563c05d29b4203e0d2ca57a50c4",
            configuration.installer["sha256"],
        )

    def test_manifest_has_only_the_two_pinned_forge_dependencies(self) -> None:
        configuration = forge_client.load_configuration()
        dependencies = forge_client.dependency_specs(configuration)

        self.assertEqual(
            {"architectury", "geckolib"},
            {str(dependency["mod_id"]) for dependency in dependencies},
        )
        self.assertEqual(
            {"9.2.14", "4.7.4"},
            {
                configuration.properties[str(dependency["version_property"])]
                for dependency in dependencies
            },
        )
        for dependency in dependencies:
            self.assertRegex(str(dependency["sha256"]), r"^[0-9a-f]{64}$")
            self.assertGreater(int(dependency["size"]), 0)

    def test_artifacts_resolve_from_exact_forge_remap_tasks(self) -> None:
        configuration = forge_client.load_configuration()

        self.assertEqual(
            "forge/versions/1.20.1/build/e2e-under-test/libs/"
            "Etherology-Forge-1.20.1-0.1.8-e2e-under-test.jar",
            forge_client.artifact_source_relative_path(configuration, "production"),
        )
        self.assertEqual(
            "forge/versions/1.20.1/build/e2e-harness/libs/"
            "Etherology-E2E-Harness-Forge-1.20.1-0.1.8.jar",
            forge_client.artifact_source_relative_path(configuration, "harness"),
        )
        for role, task_path in forge_client.EXPECTED_ARTIFACT_TASKS.items():
            source = forge_client.require_object(
                forge_client.artifact_spec(configuration, role), "source"
            )
            self.assertEqual(task_path, source["task_path"])

    def test_profile_runtime_is_distinct_and_repository_owned(self) -> None:
        configuration = forge_client.load_configuration()
        profile = forge_client.profile_spec(configuration)
        descriptor = forge_client.profile_descriptor(configuration)
        manifest_provenance = descriptor["profile_manifest"]

        self.assertEqual("etherology-e2e-forge-1.20.1-v11", profile["id"])
        self.assertNotEqual(
            "etherology-e2e-fabric-1.20.1-v23", profile["runtime_directory"]
        )
        self.assertEqual(
            forge_client.STATE_ROOT / "runtimes" / profile["runtime_directory"],
            forge_client.runtime_root(configuration),
        )
        self.assertEqual([], forge_client.profile_descriptor(configuration)["isolation"]["source_profiles"])
        self.assertEqual(
            forge_client.PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
            manifest_provenance["path"],
        )
        self.assertEqual(
            configuration.profile_manifest_path.stat().st_size,
            manifest_provenance["size"],
        )
        self.assertEqual(
            forge_client.sha256_file(configuration.profile_manifest_path),
            manifest_provenance["sha256"],
        )

    def test_rejects_a_profile_loaded_from_an_untracked_path(self) -> None:
        with temporary_repository() as (root, manifest_path):
            untracked_path = root / "profile-copy.json"
            untracked_path.write_bytes(manifest_path.read_bytes())

            with self.assertRaisesRegex(forge_client.E2EError, "tracked repository path"):
                forge_client.load_configuration(untracked_path, root)

    def test_evidence_scenarios_match_bounded_contract(self) -> None:
        configuration = forge_client.load_configuration()
        contract = (
            configuration.repository_root / "docs/testing/E2E-CONTRACT.md"
        ).read_text(encoding="utf-8")
        scenario_table = contract.split(
            "## Loader-specific bounded scenarios",
            1,
        )[1].split("### Headless dedicated-server bounded scenarios", 1)[0]
        contract_scenarios = [
            line.split("`", 2)[1]
            for line in scenario_table.splitlines()
            if line.startswith("| `")
        ]

        self.assertEqual(
            contract_scenarios,
            forge_client.scenario_ids(configuration),
        )

    def test_scenario_order_defaults_to_storage_and_selects_channel_exactly(self) -> None:
        configuration = forge_client.load_configuration()

        self.assertEqual(
            ["ethereal-storage", "ethereal-channel"],
            forge_client.scenario_ids(configuration),
        )
        self.assertEqual(
            "ethereal-storage",
            forge_client.resolve_scenario_id(configuration, None),
        )
        self.assertEqual(
            "ethereal-channel",
            forge_client.resolve_scenario_id(configuration, "ethereal-channel"),
        )
        with self.assertRaisesRegex(forge_client.E2EError, "Unsupported"):
            forge_client.resolve_scenario_id(configuration, " ethereal-channel")

    def test_dependency_inventory_drift_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["dependencies"].append(dict(manifest["dependencies"][0]))
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(forge_client.E2EError, "exactly two"):
                forge_client.load_configuration(manifest_path, root)

    def test_capture_dimensions_are_exactly_1920_by_1080(self) -> None:
        with temporary_repository() as (root, manifest_path):
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["evidence"]["capture"]["width"] = 1919
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(forge_client.E2EError, "capture contract"):
                forge_client.load_configuration(manifest_path, root)

    def test_release_installer_hash_drift_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            matrix_path = root / "release/release-matrix.json"
            matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
            matrix["installers"]["forge-1.20.1-47.4.9"]["sha256"] = "bad"
            matrix_path.write_text(json.dumps(matrix), encoding="utf-8")

            with self.assertRaisesRegex(forge_client.E2EError, "SHA-256"):
                forge_client.load_configuration(manifest_path, root)


class MetadataIntegrityTests(unittest.TestCase):
    def test_dependency_requires_exact_root_mod_id_version_hash_and_size(self) -> None:
        configuration = forge_client.load_configuration()
        dependency = forge_client.dependency_specs(configuration)[0]
        content = forge_jar_bytes(
            str(dependency["mod_id"]),
            configuration.properties[str(dependency["version_property"])],
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / str(dependency["file_name"])
            path.write_bytes(content)
            test_dependency = dict(dependency)
            test_dependency["size"] = len(content)
            test_dependency["sha256"] = hashlib_sha256(content)

            self.assertEqual(
                {str(dependency["mod_id"])},
                forge_client.verify_dependency_jar(configuration, path, test_dependency),
            )
            path.write_bytes(content + b"drift")
            with self.assertRaisesRegex(forge_client.E2EError, "size"):
                forge_client.verify_dependency_jar(configuration, path, test_dependency)

    def test_production_artifact_requires_e2e_mark_and_exact_dependencies(self) -> None:
        configuration = forge_client.load_configuration()
        version = configuration.properties["mod_version"]
        ranges = {
            "forge": ("[47,)", "BOTH"),
            "minecraft": ("[1.20.1,1.20.2)", "BOTH"),
            "architectury": ("[9.2.14,)", "BOTH"),
            "geckolib": ("[4.7.4,5)", "BOTH"),
        }
        content = forge_jar_bytes(
            "etherology",
            version,
            ranges,
            [
                ("ru/feytox/etherology/forge/EtherologyForge.class", b"bytecode"),
                (
                    "data/etherology/loot_tables/blocks/ethereal_storage.json",
                    b"{}",
                ),
                (
                    "data/minecraft/tags/game_events/vibrations.json",
                    b"{}",
                ),
                (
                    "data/minecraft/tags/game_events/warden_can_listen.json",
                    b"{}",
                ),
            ],
            {"Etherology-E2E-Only": "true"},
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "production.jar"
            path.write_bytes(content)

            inspection = forge_client.verify_production_artifact_metadata(
                configuration, path
            )

            self.assertEqual({"etherology"}, inspection[2])
            path.write_bytes(
                forge_jar_bytes(
                    "etherology",
                    version,
                    ranges,
                    [
                        ("ru/feytox/etherology/forge/EtherologyForge.class", b"bytecode"),
                        (
                            "data/etherology/loot_tables/blocks/ethereal_storage.json",
                            b"{}",
                        ),
                        (
                            "data/minecraft/tags/game_events/vibrations.json",
                            b"{}",
                        ),
                        (
                            "data/minecraft/tags/game_events/warden_can_listen.json",
                            b"{}",
                        ),
                    ],
                )
            )
            with self.assertRaisesRegex(forge_client.E2EError, "E2E-only"):
                forge_client.verify_production_artifact_metadata(configuration, path)

    def test_harness_rejects_production_links_and_nested_jars(self) -> None:
        configuration = forge_client.load_configuration()
        version = configuration.properties["mod_version"]
        harness = forge_client.artifact_spec(configuration, "harness")
        entrypoint = str(harness["entrypoint"]).replace(".", "/") + ".class"
        dependencies = {
            "forge": ("[47,)", "CLIENT"),
            "minecraft": ("[1.20.1,1.20.2)", "CLIENT"),
            "etherology": (f"[{version}]", "CLIENT"),
        }
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "harness.jar"
            path.write_bytes(
                forge_jar_bytes(
                    "etherology_e2e_harness",
                    version,
                    dependencies,
                    [(entrypoint, b"ru/feytox/etherology/linked")],
                )
            )
            with self.assertRaisesRegex(forge_client.E2EError, "links to production"):
                forge_client.verify_harness_artifact_metadata(configuration, path)

            path.write_bytes(
                forge_jar_bytes(
                    "etherology_e2e_harness",
                    version,
                    dependencies,
                    [(entrypoint, b"isolated"), ("META-INF/jarjar/extra.jar", b"jar")],
                )
            )
            with self.assertRaisesRegex(forge_client.E2EError, "nested JAR"):
                forge_client.verify_harness_artifact_metadata(configuration, path)

    def test_symlinked_dependency_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            target = root / "target.jar"
            target.write_bytes(b"jar")
            link = root / "dependency.jar"
            link.symlink_to(target)

            with self.assertRaisesRegex(forge_client.E2EError, "linked"):
                forge_client.verify_exact_file(
                    link, hashlib_sha256(b"jar"), 3, "Pinned dependency"
                )


class RuntimeIsolationTests(unittest.TestCase):
    def test_unmarked_existing_runtime_is_never_adopted(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)

            with self.assertRaisesRegex(forge_client.E2EError, "unmarked"):
                forge_client.verify_profile_marker(configuration, root)

    def test_foreign_profile_marker_is_rejected(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            marker = forge_client.profile_descriptor(configuration)
            marker["managed_by"] = "scripts/e2e/client.py"
            (root / forge_client.PROFILE_MARKER_NAME).write_text(
                json.dumps(marker), encoding="utf-8"
            )

            with self.assertRaisesRegex(forge_client.E2EError, "does not match"):
                forge_client.verify_profile_marker(configuration, root)

    def test_profile_marker_with_another_manifest_digest_is_rejected(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            marker = forge_client.profile_descriptor(configuration)
            marker["profile_manifest"]["sha256"] = "0" * 64
            (root / forge_client.PROFILE_MARKER_NAME).write_text(
                json.dumps(marker), encoding="utf-8"
            )

            with self.assertRaisesRegex(forge_client.E2EError, "does not match"):
                forge_client.verify_profile_marker(configuration, root)

    def test_process_state_with_wrong_manager_is_refused(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            profile_id = "etherology-e2e-forge-1.20.1-v11"
            runtime = state_root / "runtimes" / profile_id
            game = runtime / "game"
            game.mkdir(parents=True)
            logs = state_root / "logs"
            logs.mkdir()
            log = logs / "client.log"
            log.write_text("", encoding="utf-8")
            marker = {
                "schema": 1,
                "profile_id": profile_id,
                "managed_by": "scripts/e2e/client.py",
                "isolation": {
                    "scope": "repository-owned-ignored-state",
                    "source_profiles": [],
                },
                "release": {"loader": "forge", "java": 17},
            }
            (runtime / forge_client.PROFILE_MARKER_NAME).write_text(
                json.dumps(marker), encoding="utf-8"
            )
            state_path = state_root / f"{profile_id}-current.json"
            state_path.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "managed_by": "scripts/e2e/client.py",
                        "profile_id": profile_id,
                        "pid": 123,
                        "scenario": "ethereal-storage",
                        "version_id": "1.20.1-forge-47.4.9",
                        "game_directory": str(game),
                        "log": str(log),
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(forge_client.E2EError, "owned runtime"):
                forge_client.read_owned_process_state(state_path, state_root)

    def test_live_pid_with_wrong_command_blocks_lifecycle_access(self) -> None:
        state = {
            "scenario": "ethereal-storage",
            "version_id": "1.20.1-forge-47.4.9",
            "game_directory": "/isolated/game",
        }
        completed = mock.Mock(returncode=0, stdout="/usr/bin/unrelated --gameDir /isolated/game")
        with mock.patch.object(forge_client.subprocess, "run", return_value=completed):
            self.assertFalse(forge_client.process_matches(123, state))

    def test_startup_allows_the_caffeinate_exec_handoff(self) -> None:
        state = {"pid": 123}
        with (
            mock.patch.object(
                forge_client.time,
                "monotonic",
                side_effect=(0.0, 0.0, 0.05, 2.1),
            ),
            mock.patch.object(forge_client.time, "sleep"),
            mock.patch.object(forge_client, "client_failure_marker", return_value=None),
            mock.patch.object(forge_client, "process_exists", return_value=True),
            mock.patch.object(
                forge_client,
                "process_matches",
                side_effect=(False, True),
            ),
        ):
            forge_client.wait_for_stable_client_start(state)

    def test_startup_rejects_identity_missing_after_the_handoff_grace(self) -> None:
        state = {"pid": 123}
        with (
            mock.patch.object(
                forge_client.time,
                "monotonic",
                side_effect=(0.0, 0.6),
            ),
            mock.patch.object(forge_client, "client_failure_marker", return_value=None),
            mock.patch.object(forge_client, "process_exists", return_value=True),
            mock.patch.object(forge_client, "process_matches", return_value=False),
            self.assertRaisesRegex(forge_client.E2EError, "identity changed"),
        ):
            forge_client.wait_for_stable_client_start(state)

    def test_startup_rejects_identity_loss_after_java_is_observed(self) -> None:
        state = {"pid": 123}
        with (
            mock.patch.object(
                forge_client.time,
                "monotonic",
                side_effect=(0.0, 0.0, 0.1),
            ),
            mock.patch.object(forge_client.time, "sleep"),
            mock.patch.object(forge_client, "client_failure_marker", return_value=None),
            mock.patch.object(forge_client, "process_exists", return_value=True),
            mock.patch.object(
                forge_client,
                "process_matches",
                side_effect=(True, False),
            ),
            self.assertRaisesRegex(forge_client.E2EError, "identity changed"),
        ):
            forge_client.wait_for_stable_client_start(state)

    def test_launch_command_enforces_game_directory_version_and_classpath(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            launcher = forge_client.launcher_directory(configuration, root)
            _parent_jar, _child_jar = create_inherited_version_fixture(
                configuration,
                root,
            )
            forge_client.materialize_inherited_client_jar(configuration, root)
            _parent_jar, child_jar = forge_client.inherited_client_jar_paths(
                configuration,
                root,
            )
            library = launcher / "libraries/example.jar"
            library.parent.mkdir(parents=True)
            library.write_bytes(b"jar")
            command = [
                "/java17",
                "-cp",
                os.pathsep.join((str(library), str(child_jar))),
                f"-D{forge_client.SCENARIO_PROPERTY_NAME}=ethereal-storage",
                forge_client.FORGE_MAIN_CLASS,
                "--version",
                forge_client.forge_version_id(configuration),
                "--gameDir",
                str(forge_client.game_directory(configuration, root)),
            ]

            forge_client.verify_launch_command(
                configuration, command, root, "ethereal-storage"
            )
            command[command.index("--version") + 1] = "wrong"
            with self.assertRaisesRegex(forge_client.E2EError, "version id"):
                forge_client.verify_launch_command(
                    configuration, command, root, "ethereal-storage"
                )

    def test_launch_command_accepts_the_exact_channel_selector(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            launcher = forge_client.launcher_directory(configuration, root)
            _parent_jar, _child_jar = create_inherited_version_fixture(
                configuration,
                root,
            )
            forge_client.materialize_inherited_client_jar(configuration, root)
            _parent_jar, child_jar = forge_client.inherited_client_jar_paths(
                configuration,
                root,
            )
            library = launcher / "libraries/example.jar"
            library.parent.mkdir(parents=True)
            library.write_bytes(b"jar")
            command = [
                "/java17",
                "-cp",
                os.pathsep.join((str(library), str(child_jar))),
                f"-D{forge_client.SCENARIO_PROPERTY_NAME}=ethereal-channel",
                forge_client.FORGE_MAIN_CLASS,
                "--version",
                forge_client.forge_version_id(configuration),
                "--gameDir",
                str(forge_client.game_directory(configuration, root)),
            ]

            forge_client.verify_launch_command(
                configuration,
                command,
                root,
                "ethereal-channel",
            )

    def test_materializes_an_exact_inherited_child_copy(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            parent_jar, child_jar = create_inherited_version_fixture(configuration, root)

            forge_client.materialize_inherited_client_jar(configuration, root)

            self.assertEqual(parent_jar.read_bytes(), child_jar.read_bytes())
            self.assertFalse(child_jar.is_symlink())
            with self.assertRaisesRegex(forge_client.E2EError, "replace"):
                forge_client.materialize_inherited_client_jar(configuration, root)

    def test_inherited_jar_materialization_rejects_an_existing_child_jar(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _parent_jar, child_jar = create_inherited_version_fixture(configuration, root)
            child_jar.write_bytes(b"unexpected")
            with self.assertRaisesRegex(forge_client.E2EError, "replace"):
                forge_client.materialize_inherited_client_jar(configuration, root)

    def test_vanilla_client_verification_rejects_tampered_parent_bytes(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            parent_jar, _child_jar = create_inherited_version_fixture(configuration, root)
            parent_jar.write_bytes(b"tampered-client!")

            with self.assertRaisesRegex(forge_client.E2EError, "SHA-1"):
                forge_client.verify_vanilla_client_jar(configuration, root)


def hashlib_sha256(content: bytes) -> str:
    import hashlib

    return hashlib.sha256(content).hexdigest()


def hashlib_sha1(content: bytes) -> str:
    import hashlib

    return hashlib.sha1(content).hexdigest()


if __name__ == "__main__":
    unittest.main()
