from __future__ import annotations

from contextlib import contextmanager
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_server


@contextmanager
def temporary_repository():
    with tempfile.TemporaryDirectory() as temporary_directory:
        root = Path(temporary_directory).resolve()
        for relative_path in (
            "release/release-matrix.json",
            "gradle.properties",
            "forge/build.gradle.kts",
            "scripts/e2e/forge-server-1.20.1-profile.json",
            "gradlew",
        ):
            source = forge_server.REPOSITORY_ROOT / relative_path
            target = root / relative_path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read_bytes())
        (root / "gradlew").chmod(0o700)
        manifest_path = root / forge_server.PROFILE_MANIFEST_RELATIVE_PATH
        yield root, manifest_path


def load_temporary_configuration(
    root: Path, manifest_path: Path
) -> forge_server.ResolvedConfiguration:
    return forge_server.load_configuration(manifest_path, root)


def valid_report() -> dict[str, object]:
    assertions = [
        {
            "name": name,
            "passed": True,
            "expected": value,
            "actual": value,
        }
        for name, value in zip(
            forge_server.EXPECTED_ASSERTION_NAMES,
            forge_server.EXPECTED_ASSERTION_VALUES,
            strict=True,
        )
    ]
    return {
        "schema": 1,
        "profile_id": forge_server.PROFILE_ID,
        "scenario": forge_server.SCENARIO_ID,
        "status": "passed",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "distribution": "DEDICATED_SERVER",
        "runtime_kind": "loom-userdev",
        "loaded_mod_ids": [
            "architectury",
            "etherology",
            "etherology_e2e_server_probe",
            "forge",
            "geckolib",
            "minecraft",
        ],
        "forbidden_mod_ids_loaded": [],
        "mods": {
            **{
                mod_id: {"loaded": True}
                for mod_id in forge_server.REQUIRED_MOD_IDS
            },
            **{
                mod_id: {"loaded": False}
                for mod_id in forge_server.FORBIDDEN_MOD_IDS
            },
        },
        "registry": {
            "registry_id": "minecraft:game_event",
            "event_id": "etherology:etherology_resonance",
            "internal_id": "etherology_resonance",
            "range": 16,
            "etherology_event_ids": ["etherology:etherology_resonance"],
            "same_instance_at_server_started": True,
        },
        "tags": {
            "update_cause": "SERVER_DATA_LOAD",
            "should_update_static_data": True,
            "update_count": 1,
            "vibrations": {
                "id": "minecraft:vibrations",
                "contains_event": True,
                "etherology_event_ids": ["etherology:etherology_resonance"],
            },
            "warden_can_listen": {
                "id": "minecraft:warden_can_listen",
                "contains_event": True,
                "etherology_event_ids": ["etherology:etherology_resonance"],
            },
            "etherology_tag_ids": [
                "minecraft:vibrations",
                "minecraft:warden_can_listen",
            ],
            "same_membership_at_server_started": True,
        },
        "lifecycle": list(forge_server.EXPECTED_LIFECYCLE),
        "assertions": assertions,
    }


def valid_server_log() -> bytes:
    lines = [
        "[Server thread/INFO] [EtherologyServerProbe] tags_updated",
        "[Server thread/INFO] Done (1.234s)! For help, type help",
        "[Server thread/INFO] [EtherologyServerProbe] server_started",
        "[Server thread/INFO] [EtherologyServerProbe] server_stopping",
        "[Server thread/INFO] Stopping server",
        "[Server thread/INFO] Saving worlds",
        "[Server thread/INFO] All dimensions are saved",
        "[Server thread/INFO] [EtherologyServerProbe] server_stopped",
        "[Server thread/INFO] [EtherologyServerProbe] report_published",
        "[Server thread/INFO] [EtherologyServerProbe] "
        "loom_userdev_exit_scheduled status=0 server_thread_join_timeout_ms=30000",
    ]
    return ("\n".join(lines) + "\n").encode("utf-8")


class ConfigurationTests(unittest.TestCase):
    def test_profile_resolves_exact_forge_server_lane(self) -> None:
        configuration = forge_server.load_configuration()

        self.assertEqual(
            "etherology-e2e-forge-server-1.20.1-v2",
            forge_server.PROFILE_ID,
        )
        self.assertEqual("forge-1.20.1", configuration.artifact_lane["artifact_node"])
        self.assertEqual("1.20.1", configuration.runtime_lane["runtime_version"])
        self.assertEqual("1.20.1-47.4.9", configuration.runtime_lane["loader_version"])
        self.assertEqual(0, configuration.runtime_lane["port"])
        self.assertEqual(17, configuration.runtime_lane["java"])
        self.assertEqual(
            forge_server.PROFILE_ID,
            forge_server.profile_spec(configuration)["runtime_directory"],
        )

    def test_profile_has_one_exact_named_probe_and_scenario(self) -> None:
        configuration = forge_server.load_configuration()
        launch = forge_server.require_object(configuration.manifest, "launch")

        self.assertEqual("loom-userdev", launch["kind"])
        self.assertEqual(forge_server.TASK_PATH, launch["task_path"])
        self.assertEqual(forge_server.SCENARIO_ID, launch["scenario"])
        self.assertEqual(2048, launch["maximum_memory_mb"])
        self.assertNotIn("quickskin", configuration.manifest["required_mod_ids"])
        self.assertIn(
            "etherology_e2e_harness", configuration.manifest["forbidden_mod_ids"]
        )

    def test_profile_must_be_loaded_from_tracked_path(self) -> None:
        with temporary_repository() as (root, manifest_path):
            copied_path = root / "copied-profile.json"
            copied_path.write_bytes(manifest_path.read_bytes())

            with self.assertRaisesRegex(forge_server.E2EError, "tracked repository path"):
                forge_server.load_configuration(copied_path, root)

    def test_profile_identity_drift_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["profile"]["id"] = "another-profile"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            with self.assertRaisesRegex(forge_server.E2EError, "identity"):
                load_temporary_configuration(root, manifest_path)

    def test_release_matrix_java_loader_and_port_drift_are_rejected(self) -> None:
        for field, value in (("java", 21), ("loader_version", "wrong"), ("port", 25565)):
            with self.subTest(field=field), temporary_repository() as (
                root,
                manifest_path,
            ):
                matrix_path = root / "release/release-matrix.json"
                matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
                runtime = next(
                    row
                    for row in matrix["runtimes"]
                    if row["artifact_node"] == "forge-1.20.1"
                )
                runtime[field] = value
                matrix_path.write_text(json.dumps(matrix), encoding="utf-8")

                with self.assertRaisesRegex(forge_server.E2EError, "server lane"):
                    load_temporary_configuration(root, manifest_path)

    def test_named_gradle_probe_definition_is_present(self) -> None:
        configuration = forge_server.load_configuration()

        forge_server.verify_gradle_probe_definition(configuration)

    def test_incomplete_named_gradle_probe_definition_is_rejected(self) -> None:
        with temporary_repository() as (root, manifest_path):
            build_path = root / "forge/build.gradle.kts"
            content = build_path.read_text(encoding="utf-8")
            content = content.replace("etherology.serverProbe.evidenceRoot", "removed")
            build_path.write_text(content, encoding="utf-8")
            configuration = load_temporary_configuration(root, manifest_path)

            with self.assertRaisesRegex(forge_server.E2EError, "incomplete"):
                forge_server.verify_gradle_probe_definition(configuration)


class RuntimeIsolationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository_context = temporary_repository()
        self.repository_root, self.manifest_path = self.repository_context.__enter__()
        self.configuration = load_temporary_configuration(
            self.repository_root, self.manifest_path
        )
        self.state_context = tempfile.TemporaryDirectory()
        self.state_root = Path(self.state_context.name).resolve() / ".state"

    def tearDown(self) -> None:
        self.state_context.cleanup()
        self.repository_context.__exit__(None, None, None)

    def test_provision_creates_only_the_new_repository_owned_runtime(self) -> None:
        self.assertIsNone(
            forge_server.provision_profile(self.configuration, self.state_root)
        )
        runtime = forge_server.runtime_root(self.configuration, self.state_root)

        self.assertEqual(
            self.state_root / "runtimes" / forge_server.PROFILE_ID,
            runtime,
        )
        self.assertEqual(
            {forge_server.PROFILE_MARKER_NAME, "game", "evidence"},
            {entry.name for entry in runtime.iterdir()},
        )
        marker = forge_server.load_json_object(
            runtime / forge_server.PROFILE_MARKER_NAME,
            "profile marker",
        )
        self.assertEqual([], marker["isolation"]["source_profiles"])

    def test_existing_exact_runtime_is_never_reused(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        marker_path = runtime / forge_server.PROFILE_MARKER_NAME
        marker_content = marker_path.read_bytes()

        with self.assertRaisesRegex(forge_server.E2EError, "Refusing to reuse"):
            forge_server.provision_profile(self.configuration, self.state_root)

        self.assertEqual(marker_content, marker_path.read_bytes())

    def test_unmarked_existing_runtime_is_never_adopted(self) -> None:
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        runtime.mkdir(parents=True)

        with self.assertRaisesRegex(forge_server.E2EError, "Refusing to reuse"):
            forge_server.provision_profile(self.configuration, self.state_root)

    def test_wrong_profile_marker_is_rejected(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        marker_path = forge_server.profile_marker_path(
            self.configuration,
            forge_server.runtime_root(self.configuration, self.state_root),
        )
        marker = json.loads(marker_path.read_text(encoding="utf-8"))
        marker["managed_by"] = "another-runner"
        marker_path.write_text(json.dumps(marker), encoding="utf-8")

        with self.assertRaisesRegex(forge_server.E2EError, "does not match"):
            forge_server.verify_runtime(self.configuration, self.state_root)

    def test_manifest_digest_drift_invalidates_an_existing_runtime(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        manifest = json.loads(self.manifest_path.read_text(encoding="utf-8"))
        self.manifest_path.write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(forge_server.E2EError, "does not match"):
            forge_server.verify_runtime(self.configuration, self.state_root)

    def test_symlinked_runtimes_root_is_rejected(self) -> None:
        self.state_root.mkdir(parents=True)
        target = self.state_root.parent / "foreign-runtimes"
        target.mkdir()
        (self.state_root / "runtimes").symlink_to(target, target_is_directory=True)

        with self.assertRaisesRegex(forge_server.E2EError, "symlink"):
            forge_server.provision_profile(self.configuration, self.state_root)

    def test_symlinked_game_directory_is_rejected(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        config = forge_server.game_directory(self.configuration, runtime) / "config"
        config.rmdir()
        config.symlink_to(runtime / "evidence", target_is_directory=True)

        with self.assertRaisesRegex(forge_server.E2EError, "linked"):
            forge_server.verify_runtime(self.configuration, self.state_root)

    def test_server_files_bind_loopback_ephemeral_port_and_eula(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        game = forge_server.game_directory(self.configuration, runtime)

        self.assertEqual("eula=true\n", (game / "eula.txt").read_text(encoding="utf-8"))
        properties = (game / "server.properties").read_text(encoding="utf-8")
        self.assertIn("server-ip=127.0.0.1\n", properties)
        self.assertIn("server-port=0\n", properties)
        self.assertIn("online-mode=false\n", properties)

    def test_evidence_layout_is_two_empty_scenario_directories(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        scenario = forge_server.evidence_root(
            self.configuration,
            forge_server.runtime_root(self.configuration, self.state_root),
        )

        self.assertEqual({"reports", "logs"}, {entry.name for entry in scenario.iterdir()})
        self.assertFalse(any((scenario / "reports").iterdir()))
        self.assertFalse(any((scenario / "logs").iterdir()))

    def test_existing_evidence_is_never_reset_or_reused(self) -> None:
        forge_server.provision_profile(self.configuration, self.state_root)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        report = forge_server.evidence_path(self.configuration, "report", runtime)
        report.write_text("{}\n", encoding="utf-8")

        with self.assertRaisesRegex(forge_server.E2EError, "not empty"):
            forge_server.verify_runtime(self.configuration, self.state_root)
        self.assertEqual("{}\n", report.read_text(encoding="utf-8"))

    def test_injected_mod_or_generated_game_file_blocks_the_only_run(self) -> None:
        for relative_path in ("mods/client-harness.jar", "usercache.json"):
            with self.subTest(relative_path=relative_path):
                if forge_server.runtime_root(
                    self.configuration, self.state_root
                ).exists():
                    self.state_context.cleanup()
                    self.state_context = tempfile.TemporaryDirectory()
                    self.state_root = Path(self.state_context.name).resolve() / ".state"
                forge_server.provision_profile(self.configuration, self.state_root)
                runtime = forge_server.runtime_root(self.configuration, self.state_root)
                injected = forge_server.game_directory(
                    self.configuration, runtime
                ) / relative_path
                injected.parent.mkdir(parents=True, exist_ok=True)
                injected.write_bytes(b"foreign")

                with self.assertRaisesRegex(
                    forge_server.E2EError, "pristine dedicated-server"
                ):
                    forge_server.verify_runtime(self.configuration, self.state_root)


class CommandTests(unittest.TestCase):
    def test_command_uses_host_jdk_and_exact_task_without_override(self) -> None:
        configuration = forge_server.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            java = root / "java"
            caffeinate = root / "caffeinate"
            java.write_bytes(b"java")
            caffeinate.write_bytes(b"caffeinate")
            java.chmod(0o700)
            caffeinate.chmod(0o700)
            with mock.patch.object(forge_server, "java_major_version", return_value=21):
                command = forge_server.build_gradle_command(
                    configuration, java, caffeinate
                )

        self.assertEqual(forge_server.TASK_PATH, command[-1])
        self.assertEqual(1, command.count(forge_server.TASK_PATH))
        self.assertEqual("-dimsu", command[1])
        self.assertIn("--no-parallel", command)
        self.assertFalse(any(argument.startswith("-P") for argument in command))
        self.assertNotIn("Quick-Skin", " ".join(command))

    def test_gradle_host_older_than_java_21_is_rejected(self) -> None:
        configuration = forge_server.load_configuration()
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            java = root / "java"
            caffeinate = root / "caffeinate"
            java.write_bytes(b"java")
            caffeinate.write_bytes(b"caffeinate")
            java.chmod(0o700)
            caffeinate.chmod(0o700)
            with (
                mock.patch.object(forge_server, "java_major_version", return_value=17),
                self.assertRaisesRegex(forge_server.E2EError, "JDK 21"),
            ):
                forge_server.build_gradle_command(configuration, java, caffeinate)


class ProbeReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.configuration = forge_server.load_configuration()

    def test_exact_probe_report_is_accepted(self) -> None:
        forge_server.validate_probe_report(valid_report(), self.configuration)

    def test_forbidden_mod_assertions_are_complete_and_profile_ordered(self) -> None:
        expected_prefix = (
            "distribution_dedicated_server",
            "runtime_kind_loom_userdev",
            "mod_loaded:etherology",
            "mod_loaded:etherology_e2e_server_probe",
            "mod_absent:etherology_e2e_harness",
            "mod_absent:quickskin",
            "mod_absent:cpm",
            "mod_absent:ears",
            "mod_absent:modmenu",
            "mod_absent:roughlyenoughitems",
            "mod_absent:emi",
            "mods_forbidden_intersection_empty",
        )

        self.assertEqual(31, len(forge_server.EXPECTED_ASSERTION_NAMES))
        self.assertEqual(expected_prefix, forge_server.EXPECTED_ASSERTION_NAMES[:12])
        self.assertEqual(
            ("DEDICATED_SERVER", "loom-userdev", "loaded", "loaded")
            + ("absent",) * 7
            + ("none",),
            forge_server.EXPECTED_ASSERTION_VALUES[:12],
        )

    def test_each_forbidden_mod_requires_an_explicit_false_result(self) -> None:
        for mod_id in forge_server.FORBIDDEN_MOD_IDS:
            with self.subTest(mod_id=mod_id):
                report = valid_report()
                report["mods"][mod_id]["loaded"] = True

                with self.assertRaisesRegex(forge_server.E2EError, "mod subset"):
                    forge_server.validate_probe_report(report, self.configuration)

    def test_report_rejects_every_major_contract_drift(self) -> None:
        mutations = {
            "failed status": lambda report: report.__setitem__("status", "failed"),
            "client distribution": lambda report: report.__setitem__(
                "distribution", "CLIENT"
            ),
            "packaged runtime claim": lambda report: report.__setitem__(
                "runtime_kind", "packaged"
            ),
            "extra mod": lambda report: report["mods"].__setitem__(
                "etherology_e2e_harness", {"loaded": True}
            ),
            "loaded forbidden id": lambda report: report["loaded_mod_ids"].append(
                "quickskin"
            ),
            "reported forbidden intersection": lambda report: report.__setitem__(
                "forbidden_mod_ids_loaded", ["quickskin"]
            ),
            "unsorted loaded ids": lambda report: report["loaded_mod_ids"].reverse(),
            "duplicate loaded id": lambda report: report["loaded_mod_ids"].append(
                "minecraft"
            ),
            "missing forbidden proof": lambda report: report["mods"].pop("emi"),
            "wrong registry id": lambda report: report["registry"].__setitem__(
                "event_id", "etherology:wrong"
            ),
            "wrong internal id": lambda report: report["registry"].__setitem__(
                "internal_id", "wrong"
            ),
            "wrong range": lambda report: report["registry"].__setitem__("range", 15),
            "wrong update cause": lambda report: report["tags"].__setitem__(
                "update_cause", "OTHER"
            ),
            "second tag update": lambda report: report["tags"].__setitem__(
                "update_count", 2
            ),
            "missing vibration": lambda report: report["tags"][
                "vibrations"
            ].__setitem__("contains_event", False),
            "wrong lifecycle": lambda report: report["lifecycle"].reverse(),
            "false assertion": lambda report: report["assertions"][0].__setitem__(
                "passed", False
            ),
            "mismatched assertion actual": lambda report: report["assertions"][
                0
            ].__setitem__("actual", "wrong"),
            "extra field": lambda report: report.__setitem__("unexpected", True),
        }
        for description, mutate in mutations.items():
            with self.subTest(description=description):
                report = valid_report()
                mutate(report)
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_probe_report(report, self.configuration)


class LifecycleEvidenceTests(unittest.TestCase):
    def test_exact_server_log_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "latest.log"
            content = valid_server_log()
            path.write_bytes(content)

            self.assertEqual(content, forge_server.validate_server_log(path))

    def test_server_log_uses_the_distinct_48_mib_boundary(self) -> None:
        self.assertEqual(48 * 1024 * 1024, forge_server.MAXIMUM_SERVER_LOG_SIZE)
        self.assertEqual(64 * 1024 * 1024, forge_server.MAXIMUM_PROCESS_LOG_SIZE)
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "latest.log"
            content = valid_server_log()
            path.write_bytes(content)
            with mock.patch.object(
                forge_server, "MAXIMUM_SERVER_LOG_SIZE", len(content)
            ):
                self.assertEqual(content, forge_server.validate_server_log(path))
            with (
                mock.patch.object(
                    forge_server, "MAXIMUM_SERVER_LOG_SIZE", len(content) - 1
                ),
                self.assertRaisesRegex(forge_server.E2EError, "invalid size"),
            ):
                forge_server.validate_server_log(path)

    def test_server_log_rejects_fatal_missing_duplicate_and_reordered_markers(self) -> None:
        base = valid_server_log().decode("utf-8")
        mutations = {
            "fatal": base + "[FATAL] failure\n",
            "missing save": base.replace("Saving worlds\n", ""),
            "duplicate lifecycle": base
            + "[Server thread/INFO] [EtherologyServerProbe] server_started\n",
            "failed userdev exit": base.replace(
                "loom_userdev_exit_scheduled status=0 "
                "server_thread_join_timeout_ms=30000",
                "loom_userdev_exit_scheduled status=1 "
                "server_thread_join_timeout_ms=30000",
            ),
            "reordered lifecycle": base.replace(
                "[EtherologyServerProbe] tags_updated",
                "[EtherologyServerProbe] temporary",
            ).replace(
                "[EtherologyServerProbe] server_started",
                "[EtherologyServerProbe] tags_updated",
            ).replace(
                "[EtherologyServerProbe] temporary",
                "[EtherologyServerProbe] server_started",
            ),
        }
        for description, content in mutations.items():
            with self.subTest(description=description), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "latest.log"
                path.write_text(content, encoding="utf-8")
                with self.assertRaises(forge_server.E2EError):
                    forge_server.validate_server_log(path)

    def test_symlinked_server_log_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            target = root / "target.log"
            target.write_bytes(valid_server_log())
            linked = root / "latest.log"
            linked.symlink_to(target)

            with self.assertRaisesRegex(forge_server.E2EError, "linked"):
                forge_server.validate_server_log(linked)

    def test_world_save_requires_nonempty_level_data_and_no_crash_report(self) -> None:
        with temporary_repository() as (root, manifest_path):
            configuration = load_temporary_configuration(root, manifest_path)
            with tempfile.TemporaryDirectory() as temporary_directory:
                state_root = Path(temporary_directory) / ".state"
                forge_server.provision_profile(configuration, state_root)
                runtime = forge_server.runtime_root(configuration, state_root)
                level_data = (
                    forge_server.game_directory(configuration, runtime)
                    / "world"
                    / "level.dat"
                )
                level_data.write_bytes(b"world")

                self.assertEqual(
                    level_data,
                    forge_server.validate_world_save(configuration, runtime),
                )
                crash = (
                    forge_server.game_directory(configuration, runtime)
                    / "crash-reports"
                    / "crash.txt"
                )
                crash.write_text("crash", encoding="utf-8")
                with self.assertRaisesRegex(forge_server.E2EError, "crash report"):
                    forge_server.validate_world_save(configuration, runtime)


class FakeProcess:
    def __init__(self, exit_code: int = 0, timeout: bool = False) -> None:
        self.exit_code = exit_code
        self.timeout = timeout
        self.pid = 43210

    def wait(self, timeout: int | None = None) -> int:
        if self.timeout:
            raise subprocess.TimeoutExpired("gradle", timeout)
        return self.exit_code

    def poll(self) -> int | None:
        if self.timeout:
            return None
        return self.exit_code


class ExecutionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository_context = temporary_repository()
        self.repository_root, self.manifest_path = self.repository_context.__enter__()
        self.configuration = load_temporary_configuration(
            self.repository_root, self.manifest_path
        )
        self.state_context = tempfile.TemporaryDirectory()
        self.state_root = Path(self.state_context.name).resolve() / ".state"
        forge_server.provision_profile(self.configuration, self.state_root)

    def tearDown(self) -> None:
        self.state_context.cleanup()
        self.repository_context.__exit__(None, None, None)

    def publish_probe_outputs(self, output_handle: object) -> None:
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        report_path = forge_server.evidence_path(
            self.configuration, "report", runtime
        )
        report_path.write_text(
            json.dumps(valid_report(), indent=2) + "\n", encoding="utf-8"
        )
        game = forge_server.game_directory(self.configuration, runtime)
        (game / "logs" / "latest.log").write_bytes(valid_server_log())
        (game / "world" / "level.dat").write_bytes(b"saved-world")
        output_handle.write(b"BUILD SUCCESSFUL\n")

    def test_zero_exit_validates_and_publishes_done_last(self) -> None:
        launch: dict[str, object] = {}

        def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
            launch.update(kwargs)
            self.publish_probe_outputs(kwargs["stdout"])
            return FakeProcess()

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
        ):
            result = forge_server.execute_probe(self.configuration, self.state_root)

        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        scenario = forge_server.evidence_root(self.configuration, runtime)
        self.assertEqual(0, result["exit_code"])
        self.assertFalse(result["timed_out"])
        self.assertEqual(self.repository_root, launch["cwd"])
        self.assertTrue(launch["start_new_session"])
        self.assertEqual(subprocess.DEVNULL, launch["stdin"])
        self.assertEqual(subprocess.STDOUT, launch["stderr"])
        self.assertEqual("/jdk21", launch["env"]["JAVA_HOME"])
        self.assertEqual(
            forge_server.COMPLETION_MARKER_CONTENT,
            (scenario / "reports/done.marker").read_bytes(),
        )
        self.assertEqual(
            valid_server_log(),
            (scenario / "logs/latest.log").read_bytes(),
        )
        launcher = json.loads(
            (scenario / "reports/launcher-result.json").read_text(encoding="utf-8")
        )
        self.assertEqual(
            forge_server.sha256_file(scenario / "logs/latest.log"),
            launcher["server_log"]["sha256"],
        )
        self.assertGreaterEqual(
            (scenario / "reports/done.marker").stat().st_mtime_ns,
            (scenario / "reports/launcher-result.json").stat().st_mtime_ns,
        )
        self.assertFalse(forge_server.run_lock_path(self.configuration, self.state_root).exists())

    def test_nonzero_exit_publishes_no_runner_evidence(self) -> None:
        def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
            kwargs["stdout"].write(b"BUILD FAILED\n")
            return FakeProcess(exit_code=1)

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
            self.assertRaisesRegex(forge_server.E2EError, "exited with 1"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "launcher_result", runtime
            ).exists()
        )

    def test_timeout_contains_process_group_and_publishes_no_done_marker(self) -> None:
        process = FakeProcess(timeout=True)
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", return_value=process),
            mock.patch.object(forge_server, "stop_process_group") as stop_process,
            mock.patch.object(forge_server, "RUN_TIMEOUT_SECONDS", 0),
            self.assertRaisesRegex(forge_server.E2EError, "timed out"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        stop_process.assert_called_with(process)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )

    def test_keyboard_interrupt_stops_the_owned_process_group(self) -> None:
        process = mock.Mock(pid=43210)
        process.poll.return_value = None
        process.wait.side_effect = KeyboardInterrupt
        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", return_value=process),
            mock.patch.object(forge_server, "stop_process_group") as stop_process,
            self.assertRaisesRegex(forge_server.E2EError, "interrupted"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        stop_process.assert_called_once_with(process)

    def test_process_output_limit_is_enforced_while_process_is_running(self) -> None:
        process = FakeProcess(timeout=True)

        def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
            kwargs["stdout"].write(b"123456789")
            return process

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
            mock.patch.object(forge_server, "stop_process_group") as stop_process,
            mock.patch.object(forge_server, "MAXIMUM_PROCESS_LOG_SIZE", 8),
            self.assertRaisesRegex(forge_server.E2EError, "during execution"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        stop_process.assert_called_once_with(process)
        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )

    def test_poll_exit_cannot_race_past_the_process_output_limit(self) -> None:
        class PollAppendingProcess:
            def __init__(self, output_handle: object) -> None:
                self.output_handle = output_handle
                self.appended = False
                self.pid = 43210

            def poll(self) -> int:
                if not self.appended:
                    self.output_handle.write(b"123456789")
                    self.appended = True
                return 0

            def wait(self, timeout: float | None = None) -> int:
                return 0

        def fake_popen(*_args: object, **kwargs: object) -> PollAppendingProcess:
            return PollAppendingProcess(kwargs["stdout"])

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
            mock.patch.object(forge_server, "MAXIMUM_PROCESS_LOG_SIZE", 8),
            self.assertRaisesRegex(forge_server.E2EError, "during execution"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )

    def test_invalid_report_never_publishes_completion(self) -> None:
        def fake_popen(*_args: object, **kwargs: object) -> FakeProcess:
            self.publish_probe_outputs(kwargs["stdout"])
            runtime = forge_server.runtime_root(self.configuration, self.state_root)
            report_path = forge_server.evidence_path(
                self.configuration, "report", runtime
            )
            report = json.loads(report_path.read_text(encoding="utf-8"))
            report["registry"]["range"] = 15
            report_path.write_text(json.dumps(report), encoding="utf-8")
            return FakeProcess()

        with (
            mock.patch.object(
                forge_server,
                "verify_environment",
                return_value=(Path("/jdk21/bin/java"), ["caffeinate", "gradle"]),
            ),
            mock.patch.object(forge_server.subprocess, "Popen", side_effect=fake_popen),
            self.assertRaisesRegex(forge_server.E2EError, "registry result"),
        ):
            forge_server.execute_probe(self.configuration, self.state_root)

        runtime = forge_server.runtime_root(self.configuration, self.state_root)
        self.assertFalse(
            forge_server.evidence_path(
                self.configuration, "completion_marker", runtime
            ).exists()
        )


if __name__ == "__main__":
    unittest.main()
