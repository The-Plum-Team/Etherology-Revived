from __future__ import annotations

from collections import deque
from contextlib import contextmanager, redirect_stdout
from dataclasses import replace
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import textwrap
import types
import unittest
from unittest import mock
import zipfile


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_client
import macos_guarded_java


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

        self.assertEqual("etherology-e2e-forge-1.20.1-v18", profile["id"])
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

    def test_active_profile_exactly_matches_v18_snapshot_and_preserves_prior_versions(
        self,
    ) -> None:
        active_profile = forge_client.REPOSITORY_ROOT / "scripts/e2e/forge-1.20.1-profile.json"
        v18_snapshot = (
            forge_client.REPOSITORY_ROOT
            / "scripts/e2e/forge-1.20.1-profile-v18.json"
        )
        v17_snapshot = (
            forge_client.REPOSITORY_ROOT
            / "scripts/e2e/forge-1.20.1-profile-v17.json"
        )
        v16_snapshot = (
            forge_client.REPOSITORY_ROOT
            / "scripts/e2e/forge-1.20.1-profile-v16.json"
        )
        v15_snapshot = (
            forge_client.REPOSITORY_ROOT
            / "scripts/e2e/forge-1.20.1-profile-v15.json"
        )
        v14_snapshot = (
            forge_client.REPOSITORY_ROOT
            / "scripts/e2e/forge-1.20.1-profile-v14.json"
        )
        v13_snapshot = (
            forge_client.REPOSITORY_ROOT
            / "scripts/e2e/forge-1.20.1-profile-v13.json"
        )
        v12_snapshot = (
            forge_client.REPOSITORY_ROOT
            / "scripts/e2e/forge-1.20.1-profile-v12.json"
        )
        v11_snapshot = (
            forge_client.REPOSITORY_ROOT
            / "scripts/e2e/forge-1.20.1-profile-v11.json"
        )

        self.assertEqual(active_profile.read_bytes(), v18_snapshot.read_bytes())
        self.assertNotEqual(active_profile.read_bytes(), v17_snapshot.read_bytes())
        self.assertNotEqual(v17_snapshot.read_bytes(), v16_snapshot.read_bytes())
        self.assertNotEqual(active_profile.read_bytes(), v15_snapshot.read_bytes())
        self.assertNotEqual(active_profile.read_bytes(), v14_snapshot.read_bytes())
        self.assertNotEqual(active_profile.read_bytes(), v13_snapshot.read_bytes())
        self.assertNotEqual(active_profile.read_bytes(), v12_snapshot.read_bytes())
        self.assertNotEqual(active_profile.read_bytes(), v11_snapshot.read_bytes())
        self.assertEqual(3737, v18_snapshot.stat().st_size)
        self.assertEqual(
            "16473184a6f11c74c9a18013b3473b48ea50752c47f4908b7927a297381edb3f",
            forge_client.sha256_file(v18_snapshot),
        )
        self.assertEqual(3702, v17_snapshot.stat().st_size)
        self.assertEqual(
            "00475fd4af5741119b44b3ca70484e967ee0b7a8c51fdc222ebdde3e2bf0ba58",
            forge_client.sha256_file(v17_snapshot),
        )
        self.assertEqual(3702, v16_snapshot.stat().st_size)
        self.assertEqual(
            "05162c31a5effae1efc3b1191a4dadb7f0ba5333fa993508bb1ee3ffecde7535",
            forge_client.sha256_file(v16_snapshot),
        )
        self.assertEqual(3702, v15_snapshot.stat().st_size)
        self.assertEqual(
            "7744609bfdc40ca69e86fb4e2d6bb4e2755d9072097acde54b7d5dd9c0537e71",
            forge_client.sha256_file(v15_snapshot),
        )
        self.assertEqual(3702, v14_snapshot.stat().st_size)
        self.assertEqual(
            "d880c523c6987836cfad5dfe9d640b1d4ee807664f3fc335ae5b31b6fbfe1e44",
            forge_client.sha256_file(v14_snapshot),
        )
        self.assertEqual(3668, v13_snapshot.stat().st_size)
        self.assertEqual(
            "0e00a169d9e9387747b9cdf1d2d682b4646b731e2244775d676794f6cc2405c6",
            forge_client.sha256_file(v13_snapshot),
        )
        self.assertEqual(3668, v12_snapshot.stat().st_size)
        self.assertEqual(
            "c23a2a905e40c721cda1d45086064667aacd568489a319eef4ce30e153a2a8d7",
            forge_client.sha256_file(v12_snapshot),
        )
        self.assertEqual(3644, v11_snapshot.stat().st_size)
        self.assertEqual(
            "af21ba7cbf1ba71f06a1dc2594daa5aa4a790ee89df3ed560760ceb1b6aa8e6f",
            forge_client.sha256_file(v11_snapshot),
        )
        v13 = json.loads(v13_snapshot.read_text(encoding="utf-8"))
        self.assertEqual("etherology-e2e-forge-1.20.1-v13", v13["profile"]["id"])
        self.assertEqual(
            "etherology-e2e-forge-1.20.1-v13",
            v13["profile"]["runtime_directory"],
        )
        self.assertEqual(
            ["ethereal-storage", "ethereal-channel", "forest-lantern"],
            v13["evidence"]["scenarios"],
        )
        v12 = json.loads(v12_snapshot.read_text(encoding="utf-8"))
        self.assertEqual("etherology-e2e-forge-1.20.1-v12", v12["profile"]["id"])
        self.assertEqual(
            "etherology-e2e-forge-1.20.1-v12",
            v12["profile"]["runtime_directory"],
        )
        self.assertEqual(
            ["ethereal-storage", "ethereal-channel", "forest-lantern"],
            v12["evidence"]["scenarios"],
        )
        v11 = json.loads(v11_snapshot.read_text(encoding="utf-8"))
        self.assertEqual("etherology-e2e-forge-1.20.1-v11", v11["profile"]["id"])
        self.assertEqual(
            "etherology-e2e-forge-1.20.1-v11",
            v11["profile"]["runtime_directory"],
        )
        self.assertEqual(
            ["ethereal-storage", "ethereal-channel"],
            v11["evidence"]["scenarios"],
        )

    def test_rejects_a_profile_loaded_from_an_untracked_path(self) -> None:
        with temporary_repository() as (root, manifest_path):
            untracked_path = root / "profile-copy.json"
            untracked_path.write_bytes(manifest_path.read_bytes())

            with self.assertRaisesRegex(forge_client.E2EError, "tracked repository path"):
                forge_client.load_configuration(untracked_path, root)

    def test_evidence_scenarios_match_bounded_profile_contract(self) -> None:
        configuration = forge_client.load_configuration()
        self.assertEqual(
            [
                "ethereal-storage",
                "ethereal-channel",
                "forest-lantern",
                "attrahite-block-registry",
                "slitherite-block-registry",
            ],
            forge_client.scenario_ids(configuration),
        )

    def test_scenario_order_defaults_to_storage_and_selects_each_exactly(self) -> None:
        configuration = forge_client.load_configuration()

        self.assertEqual(
            [
                "ethereal-storage",
                "ethereal-channel",
                "forest-lantern",
                "attrahite-block-registry",
                "slitherite-block-registry",
            ],
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
        self.assertEqual(
            "forest-lantern",
            forge_client.resolve_scenario_id(configuration, "forest-lantern"),
        )
        self.assertEqual(
            "attrahite-block-registry",
            forge_client.resolve_scenario_id(
                configuration,
                "attrahite-block-registry",
            ),
        )
        self.assertEqual(
            "slitherite-block-registry",
            forge_client.resolve_scenario_id(
                configuration,
                "slitherite-block-registry",
            ),
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
    def test_production_data_inventory_is_immutable_disjoint_and_exact(self) -> None:
        groups = forge_client.EXPECTED_PRODUCTION_DATA_ENTRY_GROUPS

        self.assertIsInstance(groups, tuple)
        self.assertIsInstance(
            forge_client.EXPECTED_PRODUCTION_DATA_ENTRIES, frozenset
        )
        self.assertTrue(groups)
        self.assertTrue(all(isinstance(group, frozenset) for group in groups))
        for group_index, group in enumerate(groups):
            for other_group in groups[group_index + 1 :]:
                self.assertTrue(group.isdisjoint(other_group))
        self.assertEqual(152, sum(len(group) for group in groups))
        self.assertEqual(
            frozenset().union(*groups),
            forge_client.EXPECTED_PRODUCTION_DATA_ENTRIES,
        )
        self.assertEqual(152, len(forge_client.EXPECTED_PRODUCTION_DATA_ENTRIES))

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
        production_entries = [
            ("ru/feytox/etherology/forge/EtherologyForge.class", b"bytecode"),
            *(
                (entry, b"{}")
                for entry in sorted(forge_client.EXPECTED_PRODUCTION_DATA_ENTRIES)
            ),
        ]
        content = forge_jar_bytes(
            "etherology",
            version,
            ranges,
            production_entries,
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
                    production_entries[:-1],
                    {"Etherology-E2E-Only": "true"},
                )
            )
            with self.assertRaisesRegex(forge_client.E2EError, "server-data inventory"):
                forge_client.verify_production_artifact_metadata(configuration, path)

            path.write_bytes(
                forge_jar_bytes(
                    "etherology",
                    version,
                    ranges,
                    [
                        *production_entries,
                        ("data/etherology/unexpected.json", b"{}"),
                    ],
                    {"Etherology-E2E-Only": "true"},
                )
            )
            with self.assertRaisesRegex(forge_client.E2EError, "server-data inventory"):
                forge_client.verify_production_artifact_metadata(configuration, path)

            path.write_bytes(
                forge_jar_bytes(
                    "etherology",
                    version,
                    ranges,
                    production_entries,
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
                    [(entrypoint, b"ru.feytox.etherology.LinkedReflectively")],
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

    def test_harness_accepts_shared_scenario_classes(self) -> None:
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
                    [
                        (entrypoint, b"isolated"),
                        (
                            "dev/theplumteam/etherology/e2e/shared/"
                            "SlitheriteBlockRegistryScenario.class",
                            b"shared harness bytecode",
                        ),
                    ],
                )
            )

            inspection = forge_client.verify_harness_artifact_metadata(
                configuration,
                path,
            )

            self.assertEqual(forge_client.sha256_file(path), inspection[0])
            self.assertEqual(path.stat().st_size, inspection[1])
            self.assertEqual({"etherology_e2e_harness"}, inspection[2])

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


class ForgeInstallerMemorySafetyTests(unittest.TestCase):
    def installer_target(self) -> macos_guarded_java.OwnedJavaProcess:
        return macos_guarded_java.OwnedJavaProcess(
            pid=12345,
            process_group_id=12345,
            proc_start_abstime=987654321,
            expected_executable="/test/java",
        )

    def telemetry_record(
        self,
        *,
        status: str = "available",
        source: str = "proc-pid-rusage-v4",
        identity_matches_target: bool | None = True,
        decision: str = "normal",
        stop_outcome: str = "not-required",
    ) -> dict[str, object]:
        return {
            "observed_at_monotonic_ns": 123456789,
            "source": source,
            "status": status,
            "identity_matches_target": identity_matches_target,
            "current_phys_footprint_bytes": 256 * 1024 * 1024,
            "resident_size_bytes": 128 * 1024 * 1024,
            "virtual_size_bytes": 512 * 1024 * 1024,
            "lifetime_max_phys_footprint_bytes": 256 * 1024 * 1024,
            "detail": "",
            "decision": decision,
            "stop_outcome": stop_outcome,
        }

    def telemetry_payload(
        self,
        records: list[dict[str, object]] | None = None,
    ) -> dict[str, object]:
        selected_records = records or [self.telemetry_record()]
        target = self.installer_target()
        return {
            "schema": 1,
            "target": {
                "pid": target.pid,
                "process_group_id": target.process_group_id,
                "proc_start_abstime": target.proc_start_abstime,
                "expected_executable": target.expected_executable,
            },
            "policy": macos_guarded_java.memory_policy_payload(
                forge_client.installer_supervisor.MAXIMUM_MEMORY_MB
            ),
            "state": {
                "enforcement_disarmed": False,
                "stop_callback_invoked": False,
                "sample_count": len(selected_records),
                "retained_record_count": len(selected_records),
                "dropped_record_count": 0,
                "last_stop_outcome": "not-required",
            },
            "records": selected_records,
        }

    def test_java_version_probe_has_a_tiny_explicit_heap_cap(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            java_path = Path(temporary_directory).resolve() / "java"
            java_path.write_bytes(b"not executed")
            java_path.chmod(0o700)
            completed = subprocess.CompletedProcess(
                args=[],
                returncode=0,
                stdout="java.specification.version = 17\n",
            )
            with mock.patch.object(
                forge_client.subprocess,
                "run",
                return_value=completed,
            ) as run:
                self.assertEqual(17, forge_client.java_major_version(java_path))

            run.assert_called_once_with(
                [
                    str(java_path),
                    "-Xmx64M",
                    "-XshowSettings:properties",
                    "-version",
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=20,
                check=False,
            )

    def test_guard_history_accepts_only_authoritative_or_natural_terminal_samples(
        self,
    ) -> None:
        terminal = self.telemetry_record(
            status="missing",
            identity_matches_target=None,
            decision="not-enforceable",
        )
        payload = self.telemetry_payload(
            [
                self.telemetry_record(decision="warning"),
                terminal,
            ]
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            telemetry = Path(temporary_directory) / "telemetry.json"
            telemetry.write_text(json.dumps(payload), encoding="utf-8")
            telemetry.chmod(0o600)

            failure = forge_client.forge_installer_guard_history_failure(telemetry)

        self.assertIsNone(failure)

    def test_guard_history_rejects_every_non_enforcing_or_mismatched_surface(self) -> None:
        cases: dict[str, tuple[str, object]] = {
            "hard threshold": ("record.decision", "hard"),
            "emergency threshold": ("record.decision", "emergency"),
            "sampling error": ("record.status", "error"),
            "identity drift": ("record.status", "identity-drift"),
            "fallback source": ("record.source", "fallback"),
            "identity mismatch": ("record.identity_matches_target", False),
            "wrong policy": ("policy.heap_limit_bytes", 20 * 1024 * 1024 * 1024),
            "disarmed": ("state.enforcement_disarmed", True),
            "malformed record": ("remove.detail", None),
        }
        for name, (path, value) in cases.items():
            with self.subTest(name=name):
                payload = self.telemetry_payload()
                section, field = path.split(".", 1)
                if section == "record":
                    payload["records"][0][field] = value  # type: ignore[index]
                elif section == "remove":
                    del payload["records"][0][field]  # type: ignore[index]
                else:
                    payload[section][field] = value  # type: ignore[index]
                with tempfile.TemporaryDirectory() as temporary_directory:
                    telemetry = Path(temporary_directory) / "telemetry.json"
                    telemetry.write_text(json.dumps(payload), encoding="utf-8")
                    telemetry.chmod(0o600)

                    failure = forge_client.forge_installer_guard_history_failure(
                        telemetry
                    )

                self.assertIsNotNone(failure)

    def test_install_delegates_only_to_typed_supervisor_controller(self) -> None:
        configuration = forge_client.load_configuration()
        package = types.ModuleType("minecraft_launcher_lib")
        package.__path__ = []  # type: ignore[attr-defined]
        install_module = types.ModuleType("minecraft_launcher_lib.install")
        install_minecraft = mock.Mock()
        install_module.install_minecraft_version = install_minecraft  # type: ignore[attr-defined]
        package.install = install_module  # type: ignore[attr-defined]
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            launcher_root = forge_client.launcher_directory(configuration, root)
            (launcher_root / "installers").mkdir(parents=True)
            forge_installer = forge_client.installer_path(configuration, root)
            forge_installer.write_bytes(b"pinned installer")
            java_path = root / "java"
            java_path.write_bytes(b"not executed")
            java_path.chmod(0o700)
            operation = forge_client.InstallerOperation(
                run_id="a" * 64,
                profile_id="etherology-e2e-forge-1.20.1-v18",
                controller_pid=123,
                content=b"operation",
            )
            with (
                mock.patch.dict(forge_client.os.environ, {}, clear=True),
                mock.patch.dict(
                    sys.modules,
                    {
                        "minecraft_launcher_lib": package,
                        "minecraft_launcher_lib.install": install_module,
                    },
                ),
                mock.patch.object(forge_client, "verify_launcher_library"),
                mock.patch.object(forge_client, "verify_exact_file"),
                mock.patch.object(
                    forge_client,
                    "run_supervised_forge_installer",
                ) as run_supervised,
                mock.patch.object(
                    forge_client,
                    "materialize_inherited_client_jar",
                ) as materialize,
                mock.patch.object(forge_client.subprocess, "run") as subprocess_run,
            ):
                forge_client.install_isolated_game(
                    configuration,
                    root,
                    java_path,
                    operation,
                )

            install_minecraft.assert_called_once_with("1.20.1", str(launcher_root))
            run_supervised.assert_called_once_with(
                configuration,
                operation,
                java_path,
                root,
                launcher_root,
            )
            materialize.assert_called_once_with(configuration, root)
            subprocess_run.assert_not_called()

    def test_provision_retains_only_an_uncertain_installer_staging_root(self) -> None:
        configuration = forge_client.load_configuration()
        cases = (
            (forge_client.E2EError("ordinary failure"), False),
            (forge_client.InstallerCleanupUncertain("cleanup uncertain"), True),
        )
        for failure, should_retain in cases:
            with self.subTest(failure=type(failure).__name__):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    state_root = Path(temporary_directory).resolve() / ".state"
                    runtimes_root = state_root / "runtimes"
                    target_root = runtimes_root / "final-runtime"
                    with (
                        mock.patch.dict(forge_client.os.environ, {}, clear=True),
                        mock.patch.object(forge_client, "STATE_ROOT", state_root),
                        mock.patch.object(forge_client, "RUNTIMES_ROOT", runtimes_root),
                        mock.patch.object(
                            forge_client,
                            "runtime_root",
                            return_value=target_root,
                        ),
                        mock.patch.object(forge_client, "ensure_owned_state_roots"),
                        mock.patch.object(forge_client, "require_unattempted_profile"),
                        mock.patch.object(
                            forge_client,
                            "resolve_java_17",
                            return_value=Path("/test/java"),
                        ),
                        mock.patch.object(forge_client, "download_pinned_file"),
                        mock.patch.object(forge_client, "verify_dependency_jar"),
                        mock.patch.object(
                            forge_client,
                            "install_isolated_game",
                            side_effect=failure,
                        ),
                        self.assertRaises(type(failure)),
                    ):
                        forge_client.provision_profile(configuration)

                    staging_roots = [
                        path
                        for path in runtimes_root.iterdir()
                        if path.name != target_root.name
                    ]
                    self.assertEqual(should_retain, bool(staging_roots))
                    if should_retain:
                        self.assertTrue(
                            (staging_roots[0] / forge_client.PROFILE_MARKER_NAME).is_file()
                        )

    def test_provision_rejects_injected_options_before_state_mutation(self) -> None:
        configuration = forge_client.load_configuration()
        for variable_name in macos_guarded_java.JAVA_OPTION_ENVIRONMENT_VARIABLES:
            with self.subTest(variable_name=variable_name):
                with (
                    mock.patch.dict(
                        forge_client.os.environ,
                        {variable_name: "-Xmx20G"},
                        clear=True,
                    ),
                    mock.patch.object(
                        forge_client,
                        "ensure_owned_state_roots",
                    ) as ensure_roots,
                    self.assertRaisesRegex(forge_client.E2EError, variable_name),
                ):
                    forge_client.provision_profile(configuration)

                ensure_roots.assert_not_called()


class ForgeInstallerSupervisorControllerTests(unittest.TestCase):
    def supervisor_controller(
        self,
        *,
        activated: bool = False,
    ) -> forge_client.InstallerSupervisorController:
        operation = forge_client.InstallerOperation(
            run_id="a" * 64,
            profile_id="etherology-e2e-forge-1.20.1-v18",
            controller_pid=321,
            content=b"operation",
        )
        target = macos_guarded_java.OwnedJavaProcess(
            pid=500,
            process_group_id=500,
            proc_start_abstime=1000,
            expected_executable=str(Path(sys.executable).resolve()),
        )
        process = mock.Mock(pid=target.pid)
        process.poll.return_value = None
        process.wait.return_value = -signal.SIGKILL
        control = mock.Mock()
        control.frames = deque()
        control.eof = False
        controller = forge_client.InstallerSupervisorController(
            operation=operation,
            process=process,
            target=target,
            session_id=target.pid,
            sampler=mock.Mock(),
            control_socket=mock.Mock(),
            control=control,
            runtime_directory=Path("/test/supervisor-runtime"),
        )
        controller.activated = activated
        return controller

    def test_operation_interlock_is_exclusive_exact_and_durable(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve() / ".state"
            operation = forge_client.acquire_installer_operation(
                configuration,
                state_root,
            )
            path = forge_client.installer_operation_path(state_root)
            self.assertEqual(operation.content, path.read_bytes())
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            with self.assertRaises(forge_client.InstallerCleanupUncertain):
                forge_client.acquire_installer_operation(configuration, state_root)

            forge_client.release_installer_operation(operation, state_root)
            self.assertFalse(path.exists())

            operation = forge_client.acquire_installer_operation(
                configuration,
                state_root,
            )
            path.write_bytes(b"tampered\n")
            with self.assertRaisesRegex(
                forge_client.InstallerCleanupUncertain,
                "changed",
            ):
                forge_client.release_installer_operation(operation, state_root)

    def test_pending_operation_blocks_provision_before_java_or_download(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve() / ".state"
            runtimes_root = state_root / "runtimes"
            runtimes_root.mkdir(parents=True)
            forge_client.installer_operation_path(state_root).write_text(
                "pending\n",
                encoding="utf-8",
            )
            with (
                mock.patch.dict(forge_client.os.environ, {}, clear=True),
                mock.patch.object(forge_client, "STATE_ROOT", state_root),
                mock.patch.object(forge_client, "RUNTIMES_ROOT", runtimes_root),
                mock.patch.object(
                    forge_client,
                    "resolve_java_17",
                ) as resolve_java,
                mock.patch.object(
                    forge_client,
                    "download_pinned_file",
                ) as download,
                mock.patch.object(forge_client.subprocess, "Popen") as popen,
                self.assertRaises(forge_client.InstallerCleanupUncertain),
            ):
                forge_client.provision_profile(configuration)

            resolve_java.assert_not_called()
            download.assert_not_called()
            popen.assert_not_called()

    def test_supervisor_is_bound_before_any_activation_can_be_sent(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            state_root = root / ".state"
            operation = forge_client.acquire_installer_operation(
                configuration,
                state_root,
            )
            runtime = root / "supervisor-runtime"
            runtime.mkdir(mode=0o700)
            process = mock.Mock(pid=500)
            process.poll.return_value = None
            target = macos_guarded_java.OwnedJavaProcess(
                pid=500,
                process_group_id=500,
                proc_start_abstime=123456789,
                expected_executable=str(Path(sys.executable).resolve()),
            )
            sampler = mock.Mock()
            controller_socket = mock.Mock()
            child_socket = mock.Mock()
            child_socket.fileno.return_value = 17
            framed_control = mock.Mock()
            with (
                mock.patch.object(forge_client, "STATE_ROOT", state_root),
                mock.patch.object(
                    forge_client.socket,
                    "socketpair",
                    return_value=(controller_socket, child_socket),
                ),
                mock.patch.object(
                    forge_client.subprocess,
                    "Popen",
                    return_value=process,
                ) as popen,
                mock.patch.object(
                    forge_client.MacOsProcessMemorySampler,
                    "native",
                    return_value=sampler,
                ),
                mock.patch.object(
                    forge_client,
                    "bind_installer_supervisor",
                    return_value=(target, 500),
                ) as bind,
                mock.patch.object(
                    forge_client.installer_supervisor,
                    "FramedControl",
                    return_value=framed_control,
                ),
            ):
                controller = forge_client.spawn_installer_supervisor(
                    operation,
                    runtime,
                )

            bind.assert_called_once_with(process, sampler)
            self.assertIs(framed_control, controller.control)
            command = popen.call_args.args[0]
            self.assertEqual(sys.executable, command[0])
            self.assertNotIn("java", command)
            self.assertEqual((17,), popen.call_args.kwargs["pass_fds"])
            child_socket.close.assert_called_once()

    def test_post_spawn_socket_close_failure_still_proves_group_exit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve() / "supervisor-runtime"
            runtime.mkdir(mode=0o700)
            operation = self.supervisor_controller().operation
            process = mock.Mock(pid=500)
            process.wait.return_value = -signal.SIGKILL
            controller_socket = mock.Mock()
            child_socket = mock.Mock()
            child_socket.fileno.return_value = 17
            child_socket.close.side_effect = OSError("child socket close failed")
            controller_socket.close.side_effect = OSError(
                "controller socket close failed"
            )
            with (
                mock.patch.object(forge_client, "verify_installer_operation"),
                mock.patch.object(
                    forge_client.socket,
                    "socketpair",
                    return_value=(controller_socket, child_socket),
                ),
                mock.patch.object(
                    forge_client.subprocess,
                    "Popen",
                    return_value=process,
                ) as popen,
                mock.patch.object(
                    forge_client,
                    "wait_for_installer_process_group_absence",
                ) as wait_for_absence,
                self.assertRaisesRegex(
                    forge_client.InstallerCleanupUncertain,
                    "post-spawn cleanup",
                ),
            ):
                forge_client.spawn_installer_supervisor(operation, runtime)

            popen.assert_called_once()
            self.assertEqual(2, child_socket.close.call_count)
            controller_socket.close.assert_called_once()
            process.wait.assert_called_once_with(
                timeout=forge_client.SUPERVISOR_SHUTDOWN_TIMEOUT_SECONDS
            )
            wait_for_absence.assert_called_once_with(process.pid)

    def test_cleanup_protocol_and_socket_failures_still_attempt_group_proof(
        self,
    ) -> None:
        controller = self.supervisor_controller(activated=True)
        controller.control.poll.side_effect = OSError("protocol read failed")
        controller.control_socket.close.side_effect = OSError(
            "capability close failed"
        )
        with (
            mock.patch.object(
                forge_client,
                "wait_for_supervisor_group_exit",
            ) as wait_for_exit,
            self.assertRaises(forge_client.InstallerCleanupUncertain) as raised,
        ):
            forge_client.request_supervisor_cleanup(controller)

        wait_for_exit.assert_called_once_with(controller)
        self.assertNotIsInstance(raised.exception, OSError)
        self.assertIn("capability", str(raised.exception))

    def test_cleanup_authenticates_error_from_final_post_exit_poll(self) -> None:
        controller = self.supervisor_controller(activated=True)
        controller.process.poll.return_value = -signal.SIGKILL
        error_frame = {
            "schema": forge_client.installer_supervisor.SCHEMA,
            "action": "ERROR",
            "run_id": controller.operation.run_id,
            "code": "stop-requested",
            "detail": "controller requested cleanup",
            "out_of_group_cleanup_complete": True,
            "anchor_group_kill_pending": True,
        }
        poll_count = 0

        def expose_error_on_final_poll() -> None:
            nonlocal poll_count

            poll_count += 1
            if poll_count == 2:
                controller.control.frames.append(error_frame)

        controller.control.poll.side_effect = expose_error_on_final_poll
        with (
            mock.patch.object(forge_client.time, "monotonic", return_value=0.0),
            mock.patch.object(
                forge_client,
                "wait_for_supervisor_group_exit",
            ) as wait_for_exit,
        ):
            forge_client.request_supervisor_cleanup(controller)

        self.assertEqual(2, poll_count)
        controller.control.send.assert_not_called()
        wait_for_exit.assert_called_once_with(controller)

    def test_group_absence_allows_one_observed_signal_zero_race(self) -> None:
        with (
            mock.patch.object(
                forge_client,
                "installer_process_group_exists",
                side_effect=(True, False),
            ) as group_exists,
            mock.patch.object(
                forge_client.time,
                "monotonic",
                side_effect=(0.0, 0.1),
            ),
            mock.patch.object(forge_client.time, "sleep") as sleep,
        ):
            forge_client.wait_for_installer_process_group_absence(500)

        self.assertEqual([mock.call(500), mock.call(500)], group_exists.call_args_list)
        sleep.assert_called_once_with(0.05)

    def test_nonmissing_monitor_sample_never_counts_as_absence(self) -> None:
        for status in (
            macos_guarded_java.SampleStatus.ERROR,
            macos_guarded_java.SampleStatus.IDENTITY_DRIFT,
        ):
            with self.subTest(status=status.value):
                controller = self.supervisor_controller()
                controller.monitor_target = macos_guarded_java.OwnedJavaProcess(
                    pid=600,
                    process_group_id=600,
                    proc_start_abstime=1001,
                    expected_executable=str(Path(sys.executable).resolve()),
                )
                controller.sampler.revalidate.return_value = None
                controller.sampler.sample.return_value = mock.Mock(status=status)
                with (
                    mock.patch.object(
                        forge_client,
                        "installer_process_group_exists",
                        return_value=False,
                    ),
                    mock.patch.object(
                        forge_client.time,
                        "monotonic",
                        side_effect=(0.0, 3.0),
                    ),
                    self.assertRaisesRegex(
                        forge_client.InstallerCleanupUncertain,
                        status.value,
                    ),
                ):
                    forge_client.wait_for_supervisor_group_exit(controller)

                controller.sampler.sample.assert_called_once()
                controller.sampler.revalidate.assert_not_called()

    def test_installer_output_rejects_oversized_and_symlinked_logs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            oversized = root / "oversized-output.log"
            oversized.write_bytes(
                b"x"
                * (forge_client.installer_supervisor.MAXIMUM_OUTPUT_TAIL_SIZE + 1)
            )
            oversized.chmod(0o600)
            target = root / "target-output.log"
            target.write_bytes(b"bounded output")
            target.chmod(0o600)
            linked = root / "linked-output.log"
            linked.symlink_to(target)
            cases = (
                (oversized, "strict size bound"),
                (linked, "Cannot open"),
            )
            for path, pattern in cases:
                with self.subTest(path=path.name):
                    controller = self.supervisor_controller()
                    controller.handoff = {"installer_output_log": str(path)}
                    with self.assertRaisesRegex(forge_client.E2EError, pattern):
                        forge_client.installer_output_tail(controller)

    def test_installer_telemetry_rejects_oversized_and_symlinked_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            oversized = root / "oversized-telemetry.json"
            oversized.write_bytes(
                b"x" * (macos_guarded_java.MAXIMUM_TELEMETRY_SIZE_BYTES + 1)
            )
            oversized.chmod(0o600)
            target = root / "target-telemetry.json"
            target.write_text("{}", encoding="utf-8")
            target.chmod(0o600)
            linked = root / "linked-telemetry.json"
            linked.symlink_to(target)
            for path in (oversized, linked):
                with self.subTest(path=path.name):
                    failure = forge_client.forge_installer_guard_history_failure(path)

                    self.assertIsNotNone(failure)
                    self.assertIn("became unreadable", failure)

    def test_handoff_binds_all_three_process_identities_and_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            runtime = root / "supervisor-runtime"
            runtime.mkdir(mode=0o700)
            java_path = root / "java"
            installer_path = root / "forge-installer.jar"
            launcher_root = root / "launcher"
            operation = forge_client.InstallerOperation(
                "a" * 64,
                "etherology-e2e-forge-1.20.1-v18",
                321,
                b"operation",
            )
            supervisor_target = macos_guarded_java.OwnedJavaProcess(
                500,
                500,
                1000,
                str(Path(sys.executable).resolve()),
            )
            java_target = macos_guarded_java.OwnedJavaProcess(
                501,
                500,
                1001,
                str(java_path),
            )
            monitor_target = macos_guarded_java.OwnedJavaProcess(
                600,
                600,
                1002,
                str(Path(sys.executable).resolve()),
            )
            sampler = mock.Mock()
            sampler.revalidate.side_effect = lambda target: target
            controller = forge_client.InstallerSupervisorController(
                operation,
                mock.Mock(),
                supervisor_target,
                500,
                sampler,
                mock.Mock(),
                mock.Mock(),
                runtime,
            )
            command = [
                str(java_path),
                forge_client.installer_supervisor.EXACT_HEAP_ARGUMENT,
                "-jar",
                str(installer_path),
                "--installClient",
                str(launcher_root),
            ]
            frame = {
                "schema": 1,
                "action": "HANDOFF",
                "run_id": operation.run_id,
                **{
                    f"supervisor_{name}": value
                    for name, value in {
                        "pid": supervisor_target.pid,
                        "process_group_id": supervisor_target.process_group_id,
                        "proc_start_abstime": supervisor_target.proc_start_abstime,
                        "executable": supervisor_target.expected_executable,
                    }.items()
                },
                "supervisor_session_id": 500,
                **{
                    f"java_{name}": value
                    for name, value in {
                        "pid": java_target.pid,
                        "process_group_id": java_target.process_group_id,
                        "proc_start_abstime": java_target.proc_start_abstime,
                        "executable": java_target.expected_executable,
                    }.items()
                },
                "java_session_id": 500,
                **{
                    f"monitor_{name}": value
                    for name, value in {
                        "pid": monitor_target.pid,
                        "process_group_id": monitor_target.process_group_id,
                        "proc_start_abstime": monitor_target.proc_start_abstime,
                        "executable": monitor_target.expected_executable,
                    }.items()
                },
                "monitor_session_id": 600,
                "monitor_target": forge_client.installer_supervisor.identity_payload(
                    java_target
                ),
                "monitor_group_anchor": (
                    forge_client.installer_supervisor.identity_payload(
                        supervisor_target
                    )
                ),
                "monitor_readiness": str(
                    runtime / macos_guarded_java.READINESS_FILE_NAME
                ),
                "monitor_telemetry": str(
                    runtime / macos_guarded_java.TELEMETRY_FILE_NAME
                ),
                "memory_policy": macos_guarded_java.memory_policy_payload(1024),
                "installer_output_log": str(
                    runtime / forge_client.installer_supervisor.INSTALLER_LOG_NAME
                ),
                "monitor_output_log": str(
                    runtime / forge_client.installer_supervisor.MONITOR_LOG_NAME
                ),
                "installer_command_sha256": (
                    forge_client.installer_supervisor.installer_command_sha256(command)
                ),
                "lease_interval_seconds": 1,
                "lease_expiry_seconds": 3,
            }
            with (
                mock.patch.object(
                    forge_client.os,
                    "getsid",
                    side_effect=lambda pid: 500 if pid == 501 else 600,
                ),
                mock.patch.object(
                    forge_client,
                    "verify_supervised_installer_guard",
                ),
            ):
                forge_client.validate_supervisor_handoff(
                    controller,
                    frame,
                    java_path,
                    installer_path,
                    launcher_root,
                )

            self.assertEqual(java_target, controller.java_target)
            self.assertEqual(monitor_target, controller.monitor_target)
            changed = dict(frame)
            changed["java_process_group_id"] = 999
            with self.assertRaisesRegex(forge_client.E2EError, "identities"):
                forge_client.validate_supervisor_handoff(
                    controller,
                    changed,
                    java_path,
                    installer_path,
                    launcher_root,
                )

    def test_java_exited_requires_exact_phase_identities_and_process_samples(
        self,
    ) -> None:
        controller = self.supervisor_controller()
        java_target = macos_guarded_java.OwnedJavaProcess(
            pid=501,
            process_group_id=500,
            proc_start_abstime=1001,
            expected_executable="/test/java",
        )
        monitor_target = macos_guarded_java.OwnedJavaProcess(
            pid=600,
            process_group_id=600,
            proc_start_abstime=1002,
            expected_executable=str(Path(sys.executable).resolve()),
        )
        command_sha256 = "b" * 64
        controller.handoff = {"installer_command_sha256": command_sha256}
        controller.java_target = java_target
        controller.monitor_target = monitor_target
        frame = {
            "schema": forge_client.installer_supervisor.SCHEMA,
            "action": "JAVA_EXITED",
            "run_id": controller.operation.run_id,
            "java_pid": java_target.pid,
            "java_process_group_id": java_target.process_group_id,
            "java_session_id": controller.session_id,
            "java_proc_start_abstime": java_target.proc_start_abstime,
            "java_executable": java_target.expected_executable,
            "returncode": 7,
            "reaped": True,
            "cleanup_disposition": "java-reaped-monitor-terminal-pending",
            "installer_command_sha256": command_sha256,
            "monitor_pid": monitor_target.pid,
            "monitor_process_group_id": monitor_target.process_group_id,
            "monitor_session_id": monitor_target.pid,
            "monitor_proc_start_abstime": monitor_target.proc_start_abstime,
            "monitor_executable": monitor_target.expected_executable,
            "monitor_terminal_timeout_seconds": int(
                forge_client.installer_supervisor.MONITOR_TERMINAL_TIMEOUT_SECONDS
            ),
        }
        java_missing = mock.Mock(
            status=macos_guarded_java.SampleStatus.MISSING,
        )
        monitor_available = mock.Mock(
            status=macos_guarded_java.SampleStatus.AVAILABLE,
            observed_identity=monitor_target,
        )
        controller.sampler.sample.side_effect = (java_missing, monitor_available)
        with mock.patch.object(forge_client, "verify_installer_supervisor_live"):
            returncode = forge_client.validate_supervisor_java_exited(
                controller,
                frame,
            )

        self.assertEqual(7, returncode)
        self.assertEqual(7, controller.java_returncode)

        controller.java_returncode = None
        changed = dict(frame)
        changed["java_pid"] = 999
        with self.assertRaisesRegex(forge_client.E2EError, "identities"):
            forge_client.validate_supervisor_java_exited(controller, changed)

        controller.java_returncode = 7
        with self.assertRaisesRegex(forge_client.E2EError, "outside its exact phase"):
            forge_client.validate_supervisor_java_exited(controller, frame)

    def test_controller_sequence_arms_leases_acks_and_proves_group_exit(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            state_root = root / ".state"
            operation = forge_client.acquire_installer_operation(
                configuration,
                state_root,
            )
            launcher_root = forge_client.launcher_directory(configuration, root)
            (launcher_root / "installers").mkdir(parents=True)
            forge_installer = forge_client.installer_path(configuration, root)
            forge_installer.write_bytes(b"installer")
            forge_installer.chmod(0o600)
            java_path = root / "java"
            java_path.write_bytes(b"not executed")
            supervisor_target = macos_guarded_java.OwnedJavaProcess(
                500,
                500,
                1000,
                str(Path(sys.executable).resolve()),
            )
            control = mock.Mock()
            handoff = {"schema": 1, "action": "HANDOFF", "run_id": operation.run_id}
            java_exited = {
                "schema": 1,
                "action": "JAVA_EXITED",
                "run_id": operation.run_id,
            }
            completion = {
                "schema": 1,
                "action": "COMPLETION",
                "run_id": operation.run_id,
            }
            control.frames = deque((handoff, java_exited, completion))
            control.eof = False

            def spawn(
                selected_operation: forge_client.InstallerOperation,
                runtime: Path,
            ) -> forge_client.InstallerSupervisorController:
                self.assertEqual(operation, selected_operation)
                return forge_client.InstallerSupervisorController(
                    operation,
                    mock.Mock(),
                    supervisor_target,
                    500,
                    mock.Mock(),
                    mock.Mock(),
                    control,
                    runtime,
                )

            def validate_handoff(
                controller: forge_client.InstallerSupervisorController,
                frame: dict[str, object],
                *_arguments: object,
            ) -> None:
                controller.handoff = frame

            def validate_java_exited(
                controller: forge_client.InstallerSupervisorController,
                _frame: dict[str, object],
            ) -> int:
                controller.java_returncode = 0
                return 0

            with (
                mock.patch.object(forge_client, "STATE_ROOT", state_root),
                mock.patch.dict(forge_client.os.environ, {}, clear=True),
                mock.patch.object(
                    forge_client,
                    "spawn_installer_supervisor",
                    side_effect=spawn,
                ),
                mock.patch.object(
                    forge_client,
                    "supervisor_activation_frame",
                    return_value={
                        "schema": 1,
                        "action": "ACTIVATE",
                        "run_id": operation.run_id,
                    },
                ),
                mock.patch.object(
                    forge_client,
                    "validate_supervisor_handoff",
                    side_effect=validate_handoff,
                ),
                mock.patch.object(
                    forge_client,
                    "verify_installer_supervisor_live",
                ),
                mock.patch.object(
                    forge_client,
                    "validate_supervisor_java_exited",
                    side_effect=validate_java_exited,
                ),
                mock.patch.object(
                    forge_client,
                    "validate_supervisor_completion",
                    return_value=0,
                ),
                mock.patch.object(
                    forge_client,
                    "wait_for_supervisor_group_exit",
                ) as wait_for_exit,
            ):
                forge_client.run_supervised_forge_installer(
                    configuration,
                    operation,
                    java_path,
                    root,
                    launcher_root,
                )

            sent_actions = [call.args[0]["action"] for call in control.send.call_args_list]
            self.assertEqual(["ACTIVATE", "ARMED", "LEASE", "FINAL_ACK"], sent_actions)
            wait_for_exit.assert_called_once()
            self.assertEqual([], list(root.glob(".forge-installer-supervisor.*")))

    def test_delayed_terminal_transition_sends_contiguous_leases(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            launcher_root = forge_client.launcher_directory(configuration, root)
            java_path = root / "java"
            controller = self.supervisor_controller()
            operation = controller.operation
            control = controller.control
            handoff = {
                "schema": 1,
                "action": "HANDOFF",
                "run_id": operation.run_id,
            }
            java_exited = {
                "schema": 1,
                "action": "JAVA_EXITED",
                "run_id": operation.run_id,
            }
            completion = {
                "schema": 1,
                "action": "COMPLETION",
                "run_id": operation.run_id,
            }
            clock = 0.0
            poll_count = 0

            def poll_control() -> None:
                nonlocal poll_count

                poll_count += 1
                if poll_count == 3:
                    control.frames.append(java_exited)
                elif poll_count == 5:
                    control.frames.append(completion)

            def spawn(
                selected_operation: forge_client.InstallerOperation,
                runtime: Path,
            ) -> forge_client.InstallerSupervisorController:
                self.assertEqual(operation, selected_operation)
                controller.runtime_directory = runtime
                return controller

            def validate_handoff(
                selected_controller: forge_client.InstallerSupervisorController,
                frame: dict[str, object],
                *_arguments: object,
            ) -> None:
                selected_controller.handoff = frame

            def validate_java_exited(
                selected_controller: forge_client.InstallerSupervisorController,
                _frame: dict[str, object],
            ) -> int:
                selected_controller.java_returncode = 0
                return 0

            def monotonic() -> float:
                return clock

            def advance_clock(_seconds: float) -> None:
                nonlocal clock

                clock += 1.0

            control.poll.side_effect = poll_control
            with (
                mock.patch.dict(forge_client.os.environ, {}, clear=True),
                mock.patch.object(forge_client, "verify_installer_operation"),
                mock.patch.object(
                    forge_client,
                    "spawn_installer_supervisor",
                    side_effect=spawn,
                ),
                mock.patch.object(
                    forge_client,
                    "supervisor_activation_frame",
                    return_value={
                        "schema": 1,
                        "action": "ACTIVATE",
                        "run_id": operation.run_id,
                    },
                ),
                mock.patch.object(
                    forge_client,
                    "receive_supervisor_frame",
                    return_value=handoff,
                ),
                mock.patch.object(
                    forge_client,
                    "validate_supervisor_handoff",
                    side_effect=validate_handoff,
                ),
                mock.patch.object(
                    forge_client,
                    "validate_supervisor_java_exited",
                    side_effect=validate_java_exited,
                ),
                mock.patch.object(
                    forge_client,
                    "validate_supervisor_completion",
                    return_value=0,
                ),
                mock.patch.object(forge_client, "verify_installer_supervisor_live"),
                mock.patch.object(
                    forge_client,
                    "verify_supervised_installer_guard",
                ) as verify_guard,
                mock.patch.object(forge_client, "wait_for_supervisor_group_exit"),
                mock.patch.object(
                    forge_client.time,
                    "monotonic",
                    side_effect=monotonic,
                ),
                mock.patch.object(
                    forge_client.time,
                    "sleep",
                    side_effect=advance_clock,
                ),
            ):
                forge_client.run_supervised_forge_installer(
                    configuration,
                    operation,
                    java_path,
                    root,
                    launcher_root,
                )

            leases = [
                call.args[0]
                for call in control.send.call_args_list
                if call.args[0]["action"] == "LEASE"
            ]
            self.assertEqual([1, 2, 3, 4, 5], [lease["sequence"] for lease in leases])
            self.assertEqual(2, verify_guard.call_count)
            self.assertEqual(5, poll_count)
            self.assertEqual([], list(root.glob(".forge-installer-supervisor.*")))

    def test_nonzero_completion_reports_only_the_bounded_diagnostic_tail(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            launcher_root = forge_client.launcher_directory(configuration, root)
            java_path = root / "java"
            operation = forge_client.InstallerOperation(
                run_id="a" * 64,
                profile_id="etherology-e2e-forge-1.20.1-v18",
                controller_pid=321,
                content=b"operation",
            )
            supervisor_target = macos_guarded_java.OwnedJavaProcess(
                500,
                500,
                1000,
                str(Path(sys.executable).resolve()),
            )
            handoff = {
                "schema": 1,
                "action": "HANDOFF",
                "run_id": operation.run_id,
            }
            completion = {
                "schema": 1,
                "action": "COMPLETION",
                "run_id": operation.run_id,
            }
            java_exited = {
                "schema": 1,
                "action": "JAVA_EXITED",
                "run_id": operation.run_id,
            }
            control = mock.Mock()
            control.frames = deque((java_exited, completion))
            control.eof = False
            oldest_line = b"oldest diagnostic must be discarded\n"
            retained_suffix = (
                b"retained diagnostic\n" * 28
                + b"newest installer failure\n"
            )
            padding_size = (
                forge_client.installer_supervisor.MAXIMUM_OUTPUT_TAIL_SIZE
                - len(oldest_line)
                - len(retained_suffix)
                - 1
            )
            output_tail = (
                oldest_line
                + b"x" * padding_size
                + b"\n"
                + retained_suffix
            )

            def spawn(
                selected_operation: forge_client.InstallerOperation,
                runtime: Path,
            ) -> forge_client.InstallerSupervisorController:
                self.assertEqual(operation, selected_operation)
                output_path = (
                    runtime / forge_client.installer_supervisor.INSTALLER_LOG_NAME
                )
                output_path.write_bytes(output_tail)
                output_path.chmod(0o600)
                handoff["installer_output_log"] = str(output_path)
                return forge_client.InstallerSupervisorController(
                    operation,
                    mock.Mock(),
                    supervisor_target,
                    500,
                    mock.Mock(),
                    mock.Mock(),
                    control,
                    runtime,
                )

            def validate_handoff(
                controller: forge_client.InstallerSupervisorController,
                frame: dict[str, object],
                *_arguments: object,
            ) -> None:
                controller.handoff = frame

            def validate_java_exited(
                controller: forge_client.InstallerSupervisorController,
                _frame: dict[str, object],
            ) -> int:
                controller.java_returncode = 7
                return 7

            with (
                mock.patch.dict(forge_client.os.environ, {}, clear=True),
                mock.patch.object(forge_client, "verify_installer_operation"),
                mock.patch.object(
                    forge_client,
                    "spawn_installer_supervisor",
                    side_effect=spawn,
                ),
                mock.patch.object(
                    forge_client,
                    "supervisor_activation_frame",
                    return_value={
                        "schema": 1,
                        "action": "ACTIVATE",
                        "run_id": operation.run_id,
                    },
                ),
                mock.patch.object(
                    forge_client,
                    "receive_supervisor_frame",
                    return_value=handoff,
                ),
                mock.patch.object(
                    forge_client,
                    "validate_supervisor_handoff",
                    side_effect=validate_handoff,
                ),
                mock.patch.object(
                    forge_client,
                    "validate_supervisor_java_exited",
                    side_effect=validate_java_exited,
                ),
                mock.patch.object(
                    forge_client,
                    "validate_supervisor_completion",
                    return_value=7,
                ),
                mock.patch.object(forge_client, "wait_for_supervisor_group_exit"),
                self.assertRaisesRegex(
                    forge_client.E2EError,
                    "newest installer failure",
                ) as raised,
            ):
                forge_client.run_supervised_forge_installer(
                    configuration,
                    operation,
                    java_path,
                    root,
                    launcher_root,
                )

            message = str(raised.exception)
            self.assertNotIn("oldest diagnostic must be discarded", message)
            self.assertLessEqual(
                len(message.encode("utf-8")),
                forge_client.installer_supervisor.MAXIMUM_OUTPUT_TAIL_SIZE + 128,
            )
            self.assertEqual([], list(root.glob(".forge-installer-supervisor.*")))

    def test_cleanup_uncertainty_retains_supervisor_runtime(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            state_root = root / ".state"
            operation = forge_client.acquire_installer_operation(
                configuration,
                state_root,
            )
            launcher_root = forge_client.launcher_directory(configuration, root)
            (launcher_root / "installers").mkdir(parents=True)
            forge_installer = forge_client.installer_path(configuration, root)
            forge_installer.write_bytes(b"installer")
            forge_installer.chmod(0o600)
            java_path = root / "java"
            java_path.write_bytes(b"not executed")
            controller = forge_client.InstallerSupervisorController(
                operation,
                mock.Mock(),
                macos_guarded_java.OwnedJavaProcess(
                    500,
                    500,
                    1000,
                    str(Path(sys.executable).resolve()),
                ),
                500,
                mock.Mock(),
                mock.Mock(),
                mock.Mock(),
                root,
            )
            with (
                mock.patch.object(forge_client, "STATE_ROOT", state_root),
                mock.patch.dict(forge_client.os.environ, {}, clear=True),
                mock.patch.object(
                    forge_client,
                    "spawn_installer_supervisor",
                    return_value=controller,
                ),
                mock.patch.object(
                    forge_client,
                    "supervisor_activation_frame",
                    return_value={
                        "schema": 1,
                        "action": "ACTIVATE",
                        "run_id": operation.run_id,
                    },
                ),
                mock.patch.object(
                    controller.control,
                    "send",
                    side_effect=forge_client.installer_supervisor.SupervisorError(
                        "control-invalid",
                        "broken socket",
                    ),
                ),
                mock.patch.object(
                    forge_client,
                    "request_supervisor_cleanup",
                    side_effect=forge_client.InstallerCleanupUncertain(
                        "cleanup unproven"
                    ),
                ),
                self.assertRaisesRegex(
                    forge_client.InstallerCleanupUncertain,
                    "cleanup unproven",
                ),
            ):
                forge_client.run_supervised_forge_installer(
                    configuration,
                    operation,
                    java_path,
                    root,
                    launcher_root,
                )

            self.assertTrue(
                list(root.glob(".forge-installer-supervisor.*")),
            )
            self.assertTrue(forge_client.installer_operation_path(state_root).is_file())

    def test_activation_send_baseexception_requires_authenticated_cleanup(
        self,
    ) -> None:
        class ActivationMayHaveEscaped(BaseException):
            pass

        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            state_root = root / ".state"
            operation = forge_client.acquire_installer_operation(
                configuration,
                state_root,
            )
            launcher_root = forge_client.launcher_directory(configuration, root)
            (launcher_root / "installers").mkdir(parents=True)
            forge_installer = forge_client.installer_path(configuration, root)
            forge_installer.write_bytes(b"installer")
            forge_installer.chmod(0o600)
            java_path = root / "java"
            java_path.write_bytes(b"not executed")
            controller = self.supervisor_controller()
            controller.operation = operation
            controller.process.poll.return_value = -signal.SIGKILL
            controller.control.eof = True
            escaped_actions: list[str] = []

            def send_activation(frame: dict[str, object]) -> None:
                escaped_actions.append(str(frame["action"]))
                self.assertTrue(controller.activated)
                raise ActivationMayHaveEscaped("write outcome is unknowable")

            with (
                mock.patch.object(forge_client, "STATE_ROOT", state_root),
                mock.patch.dict(forge_client.os.environ, {}, clear=True),
                mock.patch.object(
                    forge_client,
                    "spawn_installer_supervisor",
                    return_value=controller,
                ),
                mock.patch.object(
                    forge_client,
                    "supervisor_activation_frame",
                    return_value={
                        "schema": 1,
                        "action": "ACTIVATE",
                        "run_id": operation.run_id,
                    },
                ),
                mock.patch.object(
                    controller.control,
                    "send",
                    side_effect=send_activation,
                ),
                mock.patch.object(
                    forge_client,
                    "wait_for_supervisor_group_exit",
                ) as wait_for_exit,
                self.assertRaisesRegex(
                    forge_client.InstallerCleanupUncertain,
                    "authenticated terminal ERROR",
                ),
            ):
                forge_client.run_supervised_forge_installer(
                    configuration,
                    operation,
                    java_path,
                    root,
                    launcher_root,
                )

            self.assertTrue(controller.activated)
            self.assertEqual(["ACTIVATE"], escaped_actions)
            wait_for_exit.assert_called_once_with(controller)
            self.assertTrue(list(root.glob(".forge-installer-supervisor.*")))
            self.assertTrue(forge_client.installer_operation_path(state_root).is_file())


class RuntimeIsolationTests(unittest.TestCase):
    def write_guarded_process_state(self, state_root: Path) -> Path:
        profile_id = "etherology-e2e-forge-1.20.1-v18"
        runtime = state_root / "runtimes" / profile_id
        game = runtime / "game"
        game.mkdir(parents=True)
        logs = state_root / "logs"
        logs.mkdir()
        log_path = logs / "client.log"
        log_path.write_text("", encoding="utf-8")
        marker = {
            "schema": 1,
            "profile_id": profile_id,
            "managed_by": forge_client.MANAGED_BY,
            "isolation": {
                "scope": "repository-owned-ignored-state",
                "source_profiles": [],
            },
            "release": {"loader": "forge", "java": 17},
        }
        (runtime / forge_client.PROFILE_MARKER_NAME).write_text(
            json.dumps(marker),
            encoding="utf-8",
        )
        target = {
            "pid": 12345,
            "process_group_id": 12345,
            "proc_start_abstime": 987654321,
            "expected_executable": "/test/java",
        }
        telemetry_path = runtime / "memory-guard-telemetry.json"
        readiness_path = runtime / ".memory-guard-ready.json"
        telemetry_path.write_text(
            json.dumps({"schema": 1, "target": target}),
            encoding="utf-8",
        )
        readiness_path.write_text(
            json.dumps(
                {
                    "schema": 1,
                    "status": "ready",
                    "monitor_pid": 12346,
                    "target": target,
                    "telemetry": str(telemetry_path),
                }
            ),
            encoding="utf-8",
        )
        state_path = state_root / f"{profile_id}-current.json"
        state_path.write_text(
            json.dumps(
                {
                    "schema": 2,
                    "managed_by": forge_client.MANAGED_BY,
                    "profile_id": profile_id,
                    "pid": 12345,
                    "process_group_id": 12345,
                    "proc_start_abstime": 987654321,
                    "expected_executable": "/test/java",
                    "memory_guard_pid": 12346,
                    "memory_guard_telemetry": str(telemetry_path),
                    "memory_guard_readiness": str(readiness_path),
                    "scenario": "ethereal-storage",
                    "version_id": "1.20.1-forge-47.4.9",
                    "game_directory": str(game),
                    "log": str(log_path),
                }
            ),
            encoding="utf-8",
        )
        return state_path

    def test_inherited_java_options_are_rejected_before_java_probe(self) -> None:
        configuration = forge_client.load_configuration()
        for variable_name in (
            "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "_JAVA_OPTIONS",
        ):
            with self.subTest(variable_name=variable_name):
                with (
                    mock.patch.dict(
                        forge_client.os.environ,
                        {variable_name: ""},
                        clear=True,
                    ),
                    mock.patch.object(
                        forge_client,
                        "resolve_java_17",
                    ) as resolve_java,
                    self.assertRaisesRegex(forge_client.E2EError, variable_name),
                ):
                    forge_client.verify_environment(configuration)

                resolve_java.assert_not_called()

    def test_schema_two_state_pins_direct_java_and_guard_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve() / ".state"
            state_root.mkdir()
            state_path = self.write_guarded_process_state(state_root)

            state = forge_client.read_owned_process_state(state_path, state_root)

        self.assertEqual(2, state["schema"])
        self.assertEqual(state["pid"], state["process_group_id"])
        self.assertNotEqual(state["pid"], state["memory_guard_pid"])

    def test_schema_two_state_rejects_non_dedicated_or_coerced_identity(self) -> None:
        for field_name, invalid_value in (
            ("process_group_id", 12344),
            ("process_group_id", "12345"),
            ("proc_start_abstime", True),
            ("memory_guard_pid", 12345),
        ):
            with self.subTest(field_name=field_name, invalid_value=invalid_value):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    state_root = Path(temporary_directory).resolve() / ".state"
                    state_root.mkdir()
                    state_path = self.write_guarded_process_state(state_root)
                    state = json.loads(state_path.read_text(encoding="utf-8"))
                    state[field_name] = invalid_value
                    state_path.write_text(json.dumps(state), encoding="utf-8")

                    with self.assertRaises(forge_client.E2EError):
                        forge_client.read_owned_process_state(state_path, state_root)

    def test_start_attempt_is_durable_exact_and_one_use(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve() / ".state"
            state_root.mkdir()
            with mock.patch.object(
                forge_client.os,
                "fsync",
                wraps=os.fsync,
            ) as sync:
                attempt = forge_client.reserve_launch_attempt(
                    configuration,
                    "slitherite-block-registry",
                    state_root,
                )

            self.assertEqual(
                (
                    "profile_id=etherology-e2e-forge-1.20.1-v18\n"
                    "scenario=slitherite-block-registry\n"
                    f"controller_pid={os.getpid()}\n"
                ),
                attempt.read_text(encoding="utf-8"),
            )
            self.assertEqual(2, sync.call_count)
            with self.assertRaisesRegex(forge_client.E2EError, "consumed"):
                forge_client.reserve_launch_attempt(
                    configuration,
                    "slitherite-block-registry",
                    state_root,
                )
            with self.assertRaisesRegex(forge_client.E2EError, "consumed"):
                forge_client.require_unattempted_profile(
                    configuration,
                    state_root,
                )

    def test_start_attempt_rejects_every_non_slitherite_scenario_before_mutation(
        self,
    ) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve() / ".state"
            state_root.mkdir()
            for scenario_id in (
                "ethereal-storage",
                "ethereal-channel",
                "forest-lantern",
                "attrahite-block-registry",
                "unknown-scenario",
            ):
                with self.subTest(scenario_id=scenario_id), self.assertRaisesRegex(
                    forge_client.E2EError,
                    "explicitly select",
                ):
                    forge_client.reserve_launch_attempt(
                        configuration,
                        scenario_id,
                        state_root,
                    )
                self.assertFalse(
                    forge_client.launch_attempt_path(configuration, state_root).exists()
                )

    def test_linked_start_attempt_fails_closed(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve() / ".state"
            state_root.mkdir()
            target = state_root / "attempt-target"
            target.write_text("not owned\n", encoding="utf-8")
            forge_client.launch_attempt_path(
                configuration,
                state_root,
            ).symlink_to(target)

            with self.assertRaisesRegex(forge_client.E2EError, "consumed"):
                forge_client.require_unattempted_profile(
                    configuration,
                    state_root,
                )

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
            profile_id = "etherology-e2e-forge-1.20.1-v17"
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

    def test_schema_two_startup_rejects_a_non_enforcing_guard(self) -> None:
        state = {"schema": 2, "pid": 123}
        with (
            mock.patch.object(
                forge_client.time,
                "monotonic",
                side_effect=(0.0, 0.0),
            ),
            mock.patch.object(forge_client, "client_failure_marker", return_value=None),
            mock.patch.object(forge_client, "process_exists", return_value=True),
            mock.patch.object(forge_client, "process_matches", return_value=True),
            mock.patch.object(
                forge_client,
                "memory_guard_process_matches",
                return_value=True,
            ),
            mock.patch.object(
                forge_client,
                "memory_guard_is_enforcing",
                return_value=False,
            ),
            self.assertRaisesRegex(forge_client.E2EError, "stopped enforcing"),
        ):
            forge_client.wait_for_stable_client_start(state)

    def test_status_never_reports_a_live_unmonitored_client_as_healthy(self) -> None:
        configuration = forge_client.load_configuration()
        state = {
            "schema": 2,
            "profile_id": forge_client.profile_spec(configuration)["id"],
            "pid": 12345,
            "scenario": "ethereal-storage",
            "version_id": forge_client.forge_version_id(configuration),
            "game_directory": str(forge_client.game_directory(configuration)),
            "log": str(forge_client.STATE_ROOT / "logs" / "forge-test.log"),
            "memory_guard_telemetry": "/owned/runtime/memory-guard-telemetry.json",
        }
        output = io.StringIO()
        with (
            mock.patch.object(
                forge_client,
                "load_configuration",
                return_value=configuration,
            ),
            mock.patch.object(
                forge_client,
                "read_process_state",
                return_value=state,
            ),
            mock.patch.object(forge_client, "process_exists", return_value=True),
            mock.patch.object(forge_client, "process_matches", return_value=True),
            mock.patch.object(
                forge_client,
                "memory_guard_process_matches",
                return_value=False,
            ),
            redirect_stdout(output),
        ):
            status = forge_client.status_command()

        self.assertEqual(2, status)
        self.assertIn("live but unmonitored", output.getvalue())
        self.assertNotIn("client is running", output.getvalue())

    def test_failed_guarded_cleanup_retains_the_only_process_state(self) -> None:
        configuration = forge_client.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve() / ".state"
            runtime = state_root / "runtimes" / "runtime"
            game = runtime / "game"
            game.mkdir(parents=True)
            state_path = state_root / "current.json"
            target = macos_guarded_java.OwnedJavaProcess(
                pid=12345,
                process_group_id=12345,
                proc_start_abstime=987654321,
                expected_executable="/test/java",
            )
            launch = macos_guarded_java.GuardedJavaLaunch(
                java_process=mock.Mock(pid=12345),
                monitor_process=mock.Mock(pid=12346),
                caffeinate_process=mock.Mock(pid=12347),
                target=target,
                telemetry_path=runtime / "memory-guard-telemetry.json",
                readiness_path=runtime / ".memory-guard-ready.json",
            )
            with (
                mock.patch.object(forge_client, "STATE_ROOT", state_root),
                mock.patch.object(forge_client, "ensure_owned_state_roots"),
                mock.patch.object(
                    forge_client,
                    "load_configuration",
                    return_value=configuration,
                ),
                mock.patch.object(
                    forge_client,
                    "require_slitherite_harness_pin",
                    return_value=(1, "a" * 64),
                ),
                mock.patch.object(
                    forge_client,
                    "clear_stale_and_reject_live_owned_clients",
                ),
                mock.patch.object(
                    forge_client,
                    "verify_environment",
                    return_value=(
                        Path("/test/java"),
                        ["/test/java", "-Xmx4096M", "Main"],
                    ),
                ),
                mock.patch.object(forge_client, "assert_runtime_not_running"),
                mock.patch.object(forge_client, "reserve_launch_attempt"),
                mock.patch.object(
                    forge_client,
                    "runtime_root",
                    return_value=runtime,
                ),
                mock.patch.object(
                    forge_client,
                    "game_directory",
                    return_value=game,
                ),
                mock.patch.object(
                    forge_client,
                    "process_state_path",
                    return_value=state_path,
                ),
                mock.patch.object(
                    forge_client,
                    "start_guarded_java",
                    return_value=launch,
                ),
                mock.patch.object(
                    forge_client,
                    "wait_for_stable_client_start",
                    side_effect=forge_client.E2EError("startup failed"),
                ),
                mock.patch.object(
                    forge_client,
                    "stop_guarded_java_launch",
                    side_effect=forge_client.GuardedJavaError("stop failed"),
                ),
                self.assertRaisesRegex(
                    forge_client.E2EError,
                    "state was retained",
                ),
            ):
                forge_client.start_command(
                    forge_client.slitherite_run_contract.SCENARIO_ID
                )

            self.assertTrue(state_path.is_file())
            self.assertEqual(2, json.loads(state_path.read_text())["schema"])

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

    def test_launch_command_accepts_each_exact_nondefault_selector(self) -> None:
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
            for scenario_id in (
                "ethereal-channel",
                "forest-lantern",
                "attrahite-block-registry",
                "slitherite-block-registry",
            ):
                command = [
                    "/java17",
                    "-cp",
                    os.pathsep.join((str(library), str(child_jar))),
                    f"-D{forge_client.SCENARIO_PROPERTY_NAME}={scenario_id}",
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
                    scenario_id,
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
