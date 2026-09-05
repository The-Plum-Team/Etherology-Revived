from __future__ import annotations

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
MODULE_PATH = SCRIPT_DIRECTORY / "forge_server_launch_anchor.py"
JAVA_SOURCE_PATH = (
    SCRIPT_DIRECTORY.parent.parent
    / "e2e-harness/launch-anchor/1.20.1/src/ForgeServerLaunchAnchor.java"
)
SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_forge_server_launch_anchor",
    MODULE_PATH,
)
if SPECIFICATION is None or SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load launch-anchor module: {MODULE_PATH}")
anchor = importlib.util.module_from_spec(SPECIFICATION)
sys.modules[SPECIFICATION.name] = anchor
SPECIFICATION.loader.exec_module(anchor)


class FakeProcess:
    def __init__(self, pid: int = 44000) -> None:
        self.pid = pid
        self.return_code: int | None = None
        self.wait_result = 0
        self.wait_exception: BaseException | None = None
        self.wait_calls: list[float | None] = []

    def poll(self) -> int | None:
        return self.return_code

    def wait(self, timeout: float | None = None) -> int:
        self.wait_calls.append(timeout)
        if self.wait_exception is not None:
            raise self.wait_exception
        self.return_code = self.wait_result
        return self.wait_result


class LaunchAnchorTestCase(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.java_path = self.root / "jdk-21/bin/java"
        self.java_path.parent.mkdir(parents=True)
        self.java_path.write_bytes(b"pinned fake JDK 21 executable\n")
        self.java_path.chmod(0o700)
        self.source_path = self.root / "ForgeServerLaunchAnchor.java"
        self.source_content = JAVA_SOURCE_PATH.read_bytes()
        self.source_path.write_bytes(self.source_content)
        self.source_path.chmod(0o600)
        self.source_size = len(self.source_content)
        self.source_sha256 = hashlib.sha256(self.source_content).hexdigest()
        self.wrapper_jar_path = self.root / "gradle/wrapper/gradle-wrapper.jar"
        self.wrapper_jar_path.parent.mkdir(parents=True)
        self.wrapper_jar_content = b"pinned fake Gradle wrapper JAR\n"
        self.wrapper_jar_path.write_bytes(self.wrapper_jar_content)
        self.wrapper_jar_path.chmod(0o600)
        self.wrapper_jar_size = len(self.wrapper_jar_content)
        self.wrapper_jar_sha256 = hashlib.sha256(
            self.wrapper_jar_content
        ).hexdigest()
        self.wrapper_properties_path = (
            self.wrapper_jar_path.parent / "gradle-wrapper.properties"
        )
        self.wrapper_properties_content = (
            b"distributionUrl=https\\://services.gradle.org/distributions/"
            b"gradle-9.6.1-bin.zip\n"
        )
        self.wrapper_properties_path.write_bytes(self.wrapper_properties_content)
        self.wrapper_properties_path.chmod(0o600)
        self.wrapper_properties_size = len(self.wrapper_properties_content)
        self.wrapper_properties_sha256 = hashlib.sha256(
            self.wrapper_properties_content
        ).hexdigest()
        self.runtime = self.root / "runtime"
        self.runtime.mkdir(mode=0o700)
        self.working_directory = self.root / "working"
        self.working_directory.mkdir(mode=0o700)
        self.gradle_arguments = (
            "--no-daemon",
            "--no-parallel",
            "--max-workers=2",
            "--console=plain",
            "--offline",
            "-Dorg.gradle.jvmargs=-Xmx2G -Xms64m",
            "-Dorg.gradle.internal.instrumentation.agent=false",
            ":forge:1.20.1:runServerProbe",
        )
        self.environment = {
            "GRADLE_USER_HOME": str(self.root / "gradle-home"),
            "JAVA_HOME": str(self.java_path.parent.parent),
            "PATH": "/usr/bin:/bin",
        }
        self.process = FakeProcess()
        self.captured_command: list[str] | None = None
        self.captured_keywords: dict[str, object] | None = None

    def canonical_bytes(self, payload: dict[str, object]) -> bytes:
        return (
            json.dumps(payload, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
            + "\n"
        ).encode("utf-8")

    def raw_artifact(self, name: str, payload: dict[str, object]) -> Path:
        path = self.runtime / name
        path.write_bytes(self.canonical_bytes(payload))
        path.chmod(0o600)
        return path

    def readiness_payload(
        self,
        command: list[str],
        *,
        token: str | None = None,
    ) -> dict[str, object]:
        runtime_information = self.runtime.stat()
        selected_token = command[command.index("--token") + 1]
        return {
            "argv_sha256": command[command.index("--argv-sha256") + 1],
            "controller_pid": int(
                command[command.index("--controller-pid") + 1]
            ),
            "java_feature": 21,
            "pid": self.process.pid,
            "pre_start_timeout_seconds": anchor.PRE_START_TIMEOUT_SECONDS,
            "runtime_device": runtime_information.st_dev,
            "runtime_inode": runtime_information.st_ino,
            "schema": anchor.READINESS_SCHEMA,
            "token": selected_token if token is None else token,
        }

    def successful_popen(
        self,
        command: list[str],
        **keywords: object,
    ) -> FakeProcess:
        self.captured_command = command
        self.captured_keywords = keywords
        self.raw_artifact(
            anchor.READINESS_FILE_NAME,
            self.readiness_payload(command),
        )
        return self.process

    def start(self, **overrides: object) -> anchor.LaunchAnchorHandle:
        arguments: dict[str, object] = {
            "java_path": self.java_path,
            "java_feature": 21,
            "source_path": self.source_path,
            "expected_source_size": self.source_size,
            "expected_source_sha256": self.source_sha256,
            "runtime_directory": self.runtime,
            "working_directory": self.working_directory,
            "gradle_wrapper_jar_path": self.wrapper_jar_path,
            "expected_gradle_wrapper_jar_size": self.wrapper_jar_size,
            "expected_gradle_wrapper_jar_sha256": self.wrapper_jar_sha256,
            "gradle_wrapper_properties_path": self.wrapper_properties_path,
            "expected_gradle_wrapper_properties_size": (
                self.wrapper_properties_size
            ),
            "expected_gradle_wrapper_properties_sha256": (
                self.wrapper_properties_sha256
            ),
            "gradle_arguments": self.gradle_arguments,
            "child_release_guard": lambda _handle: None,
            "environment": self.environment,
            "popen_factory": self.successful_popen,
            "get_process_group_id": lambda process_id: process_id,
            "get_session_id": lambda process_id: process_id,
            "token_factory": lambda size: "a" * (size * 2),
            "allow_unprivileged_java_for_tests": True,
        }
        arguments.update(overrides)
        return anchor.start_launch_anchor(**arguments)

    def child_started_payload(
        self,
        handle: anchor.LaunchAnchorHandle,
        *,
        process_id: int = 44001,
        executable: str | None = None,
        token: str | None = None,
    ) -> dict[str, object]:
        return {
            "argv_sha256": handle.arguments_sha256,
            "executable": str(handle.java_path) if executable is None else executable,
            "pid": process_id,
            "schema": anchor.CHILD_STARTED_SCHEMA,
            "token": handle.token if token is None else token,
        }

    def child_result_payload(
        self,
        handle: anchor.LaunchAnchorHandle,
        *,
        started: bool = True,
        process_id: int | None = 44001,
        exit_code: int = 0,
    ) -> dict[str, object]:
        return {
            "argv_sha256": handle.arguments_sha256,
            "exit_code": exit_code,
            "pid": process_id,
            "schema": anchor.CHILD_RESULT_SCHEMA,
            "started": started,
            "token": handle.token,
        }

    def publish_successful_child(
        self,
        handle: anchor.LaunchAnchorHandle,
        *,
        exit_code: int = 0,
    ) -> None:
        self.raw_artifact(
            anchor.CHILD_STARTED_FILE_NAME,
            self.child_started_payload(handle),
        )
        self.raw_artifact(
            anchor.CHILD_RESULT_FILE_NAME,
            self.child_result_payload(handle, exit_code=exit_code),
        )

    def force_close(self, handle: anchor.LaunchAnchorHandle) -> None:
        for pinned_input in (
            handle.staged_source,
            handle.staged_wrapper_jar,
            handle.staged_wrapper_properties,
        ):
            if pinned_input.descriptor >= 0:
                os.close(pinned_input.descriptor)
                pinned_input.descriptor = -1
        if handle.runtime_directory_descriptor >= 0:
            os.close(handle.runtime_directory_descriptor)
            handle.runtime_directory_descriptor = -1


class LaunchCommandTests(LaunchAnchorTestCase):
    def test_anchor_uses_exact_bounded_jdk21_source_file_command(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)

        self.assertIsNotNone(self.captured_command)
        assert self.captured_command is not None
        self.assertEqual(str(self.java_path), self.captured_command[0])
        self.assertEqual(
            list(anchor.ANCHOR_JVM_ARGUMENTS),
            self.captured_command[1 : 1 + len(anchor.ANCHOR_JVM_ARGUMENTS)],
        )
        self.assertEqual(
            str(self.runtime / anchor.STAGED_SOURCE_FILE_NAME),
            self.captured_command[1 + len(anchor.ANCHOR_JVM_ARGUMENTS)],
        )
        staged_source = self.runtime / anchor.STAGED_SOURCE_FILE_NAME
        self.assertEqual(self.source_content, staged_source.read_bytes())
        self.assertEqual(0o400, staged_source.stat().st_mode & 0o777)
        staged_wrapper = self.runtime / anchor.STAGED_WRAPPER_JAR_FILE_NAME
        self.assertEqual(self.wrapper_jar_content, staged_wrapper.read_bytes())
        self.assertEqual(0o400, staged_wrapper.stat().st_mode & 0o777)
        staged_properties = (
            self.runtime / anchor.STAGED_WRAPPER_PROPERTIES_FILE_NAME
        )
        self.assertEqual(
            self.wrapper_properties_content,
            staged_properties.read_bytes(),
        )
        self.assertEqual(0o400, staged_properties.stat().st_mode & 0o777)
        delimiter = self.captured_command.index("--")
        child_command = self.captured_command[delimiter + 1 :]
        self.assertEqual(str(self.java_path), child_command[0])
        self.assertEqual("-classpath", child_command[4])
        self.assertEqual(str(staged_wrapper), child_command[5])
        self.assertEqual(
            str(self.java_path.stat().st_ino),
            self.captured_command[
                self.captured_command.index("--child-java-inode") + 1
            ],
        )
        self.assertEqual(
            hashlib.sha256(self.wrapper_jar_content).hexdigest(),
            self.captured_command[
                self.captured_command.index("--wrapper-jar-sha256") + 1
            ],
        )
        self.assertEqual(
            hashlib.sha256(self.wrapper_properties_content).hexdigest(),
            self.captured_command[
                self.captured_command.index("--wrapper-properties-sha256") + 1
            ],
        )
        self.assertEqual(
            str(os.getpid()),
            self.captured_command[
                self.captured_command.index("--controller-pid") + 1
            ],
        )
        self.assertIsNotNone(self.captured_keywords)
        assert self.captured_keywords is not None
        self.assertIs(self.captured_keywords["stdin"], subprocess.DEVNULL)
        self.assertEqual(str(self.working_directory), self.captured_keywords["cwd"])
        self.assertEqual(self.environment, self.captured_keywords["env"])
        self.assertTrue(self.captured_keywords["close_fds"])
        self.assertTrue(self.captured_keywords["start_new_session"])

    def test_direct_child_is_exact_java_gradle_wrapper_main_command(self) -> None:
        command = anchor.build_direct_gradle_wrapper_command(
            self.java_path,
            self.wrapper_jar_path,
            self.gradle_arguments,
        )

        self.assertEqual(str(self.java_path), command[0])
        self.assertEqual(
            (
                str(self.java_path),
                "-Xmx2G",
                "-Xms64m",
                "-Dorg.gradle.appname=gradlew",
                "-classpath",
                str(self.wrapper_jar_path),
                "org.gradle.wrapper.GradleWrapperMain",
            ),
            command[:7],
        )
        self.assertEqual(self.gradle_arguments, command[7:])
        self.assertNotIn("gradlew", command)
        self.assertNotIn("/bin/sh", command)

    def test_non_java_or_reordered_gradle_commands_are_rejected(self) -> None:
        direct = anchor.build_direct_gradle_wrapper_command(
            self.java_path,
            self.wrapper_jar_path,
            self.gradle_arguments,
        )
        shell_command = ("/bin/sh", *direct[1:])
        reordered = list(direct)
        reordered[1], reordered[2] = reordered[2], reordered[1]

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "exact direct JDK"):
            anchor._validate_direct_gradle_wrapper_command(
                shell_command,
                self.java_path,
                self.wrapper_jar_path,
                self.gradle_arguments,
            )
        with self.assertRaisesRegex(anchor.LaunchAnchorError, "exact direct JDK"):
            anchor._validate_direct_gradle_wrapper_command(
                reordered,
                self.java_path,
                self.wrapper_jar_path,
                self.gradle_arguments,
            )

    def test_gradle_safety_prefix_and_project_override_are_rejected(self) -> None:
        reordered = list(self.gradle_arguments)
        reordered[0], reordered[1] = reordered[1], reordered[0]
        with self.assertRaisesRegex(anchor.LaunchAnchorError, "missing or reordered"):
            anchor.build_direct_gradle_wrapper_command(
                self.java_path,
                self.wrapper_jar_path,
                reordered,
            )
        with self.assertRaisesRegex(anchor.LaunchAnchorError, "project-property"):
            anchor.build_direct_gradle_wrapper_command(
                self.java_path,
                self.wrapper_jar_path,
                (*self.gradle_arguments, "-Punsafe=true"),
            )

    def test_exact_jdk21_selection_is_required_without_probing_java(self) -> None:
        with self.assertRaisesRegex(anchor.LaunchAnchorError, "exact JDK 21"):
            self.start(java_feature=17)

    def test_production_default_requires_root_owned_protected_jdk_chain(self) -> None:
        with self.assertRaisesRegex(anchor.LaunchAnchorError, "root-owned"):
            self.start(allow_unprivileged_java_for_tests=False)

    def test_argv_digest_preserves_argument_boundaries(self) -> None:
        self.assertNotEqual(
            anchor.arguments_sha256(("ab", "c")),
            anchor.arguments_sha256(("a", "bc")),
        )


class StartAndReadinessTests(LaunchAnchorTestCase):
    def test_readiness_proves_anchor_is_session_and_group_leader(self) -> None:
        group_calls: list[int] = []
        session_calls: list[int] = []
        handle = self.start(
            get_process_group_id=lambda process_id: group_calls.append(process_id)
            or process_id,
            get_session_id=lambda process_id: session_calls.append(process_id)
            or process_id,
        )
        self.addCleanup(self.force_close, handle)

        self.assertEqual(self.process.pid, handle.anchor_pid)
        self.assertEqual(self.process.pid, handle.process_group_id)
        self.assertEqual([self.process.pid], group_calls)
        self.assertEqual([self.process.pid], session_calls)

    def test_no_child_control_artifact_exists_before_explicit_start(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)

        self.assertEqual(
            sorted(
                (
                    anchor.READINESS_FILE_NAME,
                    anchor.STAGED_SOURCE_FILE_NAME,
                    anchor.STAGED_WRAPPER_JAR_FILE_NAME,
                    anchor.STAGED_WRAPPER_PROPERTIES_FILE_NAME,
                )
            ),
            sorted(path.name for path in self.runtime.iterdir()),
        )
        with self.assertRaisesRegex(anchor.LaunchAnchorError, "not been released"):
            handle.poll_child_started()

    def test_start_artifact_is_exclusive_and_token_bound(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)

        handle.start_child()
        payload = json.loads((self.runtime / anchor.START_FILE_NAME).read_bytes())
        self.assertEqual(
            {
                "argv_sha256": handle.arguments_sha256,
                "schema": anchor.START_SCHEMA,
                "token": handle.token,
            },
            payload,
        )
        self.assertEqual(
            0o600,
            (self.runtime / anchor.START_FILE_NAME).stat().st_mode & 0o777,
        )
        with self.assertRaisesRegex(anchor.LaunchAnchorError, "already released"):
            handle.start_child()

    def test_watchdog_release_guard_must_pass_twice_before_start_publication(self) -> None:
        calls: list[bool] = []

        def release_guard(_handle: anchor.LaunchAnchorHandle) -> None:
            calls.append((self.runtime / anchor.START_FILE_NAME).exists())
            if len(calls) == 2:
                raise anchor.LaunchAnchorError("watchdog readiness lost")

        handle = self.start(child_release_guard=release_guard)
        self.addCleanup(self.force_close, handle)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "watchdog readiness lost"):
            handle.start_child()

        self.assertEqual([False, False], calls)
        self.assertFalse((self.runtime / anchor.START_FILE_NAME).exists())

    def test_watchdog_release_guard_runs_immediately_after_start_publication(self) -> None:
        calls: list[bool] = []
        handle = self.start(
            child_release_guard=lambda _handle: calls.append(
                (self.runtime / anchor.START_FILE_NAME).exists()
            )
        )
        self.addCleanup(self.force_close, handle)

        handle.start_child()

        self.assertEqual([False, False, True], calls)

    def test_post_publication_guard_failure_preserves_live_owned_anchor(self) -> None:
        calls: list[bool] = []

        def release_guard(_handle: anchor.LaunchAnchorHandle) -> None:
            calls.append((self.runtime / anchor.START_FILE_NAME).exists())
            if len(calls) == 3:
                raise anchor.LaunchAnchorError("watchdog failed after release")

        handle = self.start(child_release_guard=release_guard)
        self.addCleanup(self.force_close, handle)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "failed after release"):
            handle.start_child()

        self.assertEqual([False, False, True], calls)
        self.assertTrue((self.runtime / anchor.START_FILE_NAME).exists())
        self.assertIsNone(handle.process.poll())

    def test_wrong_readiness_token_preserves_spawned_handle(self) -> None:
        def invalid_popen(command: list[str], **_keywords: object) -> FakeProcess:
            self.raw_artifact(
                anchor.READINESS_FILE_NAME,
                self.readiness_payload(command, token="b" * 64),
            )
            return self.process

        with self.assertRaises(anchor.LaunchAnchorStartError) as raised:
            self.start(popen_factory=invalid_popen)

        handle = raised.exception.handle
        self.addCleanup(self.force_close, handle)
        self.assertIs(self.process, handle.process)
        self.assertIsNone(self.process.poll())
        os.fstat(handle.runtime_directory_descriptor)

    def test_bounded_readiness_timeout_preserves_live_anchor(self) -> None:
        clock = iter((0.0, 1.0))

        with self.assertRaises(anchor.LaunchAnchorStartError) as raised:
            self.start(
                popen_factory=lambda *_arguments, **_keywords: self.process,
                readiness_timeout_seconds=0.1,
                monotonic=lambda: next(clock),
                sleep=lambda _duration: None,
            )

        handle = raised.exception.handle
        self.addCleanup(self.force_close, handle)
        self.assertIsNone(self.process.poll())
        self.assertEqual([], self.process.wait_calls)
        self.assertFalse((self.runtime / anchor.START_FILE_NAME).exists())

    def test_source_replacement_during_spawn_preserves_anchor_for_cleanup(self) -> None:
        def replacing_popen(command: list[str], **_keywords: object) -> FakeProcess:
            self.raw_artifact(
                anchor.READINESS_FILE_NAME,
                self.readiness_payload(command),
            )
            replacement = self.source_path.with_suffix(".replacement")
            replacement.write_bytes(self.source_content)
            replacement.chmod(0o600)
            os.replace(replacement, self.source_path)
            return self.process

        with self.assertRaisesRegex(
            anchor.LaunchAnchorStartError,
            "source identity changed",
        ) as raised:
            self.start(popen_factory=replacing_popen)

        self.addCleanup(self.force_close, raised.exception.handle)
        self.assertIs(self.process, raised.exception.handle.process)

    def test_wrapper_jar_replacement_during_spawn_preserves_anchor_for_cleanup(
        self,
    ) -> None:
        def replacing_popen(command: list[str], **_keywords: object) -> FakeProcess:
            self.raw_artifact(
                anchor.READINESS_FILE_NAME,
                self.readiness_payload(command),
            )
            replacement = self.wrapper_jar_path.with_suffix(".replacement")
            replacement.write_bytes(self.wrapper_jar_content)
            replacement.chmod(0o600)
            os.replace(replacement, self.wrapper_jar_path)
            return self.process

        with self.assertRaisesRegex(
            anchor.LaunchAnchorStartError,
            "Gradle wrapper JAR identity changed",
        ) as raised:
            self.start(popen_factory=replacing_popen)

        self.addCleanup(self.force_close, raised.exception.handle)
        self.assertIs(self.process, raised.exception.handle.process)

    def test_popen_failure_closes_pinned_runtime_descriptor(self) -> None:
        captured_descriptor: list[int] = []
        original_open = anchor._open_runtime_directory

        def capturing_open(path: Path) -> tuple[int, anchor.FileIdentity]:
            descriptor, identity = original_open(path)
            captured_descriptor.append(descriptor)
            return descriptor, identity

        with mock.patch.object(anchor, "_open_runtime_directory", capturing_open):
            with self.assertRaisesRegex(OSError, "spawn failed"):
                self.start(
                    popen_factory=lambda *_arguments, **_keywords: (_ for _ in ()).throw(
                        OSError("spawn failed")
                    )
                )

        self.assertEqual(1, len(captured_descriptor))
        with self.assertRaises(OSError):
            os.fstat(captured_descriptor[0])

    def test_anchor_exiting_before_readiness_is_a_failed_start_with_handle(self) -> None:
        self.process.return_code = 9
        with self.assertRaisesRegex(
            anchor.LaunchAnchorStartError,
            "exited before readiness",
        ) as raised:
            self.start(popen_factory=lambda *_arguments, **_keywords: self.process)

        self.addCleanup(self.force_close, raised.exception.handle)
        self.assertEqual(9, raised.exception.handle.process.poll())

    def test_anchor_exiting_after_exact_readiness_is_a_failed_start(self) -> None:
        def exited_popen(command: list[str], **_keywords: object) -> FakeProcess:
            self.raw_artifact(
                anchor.READINESS_FILE_NAME,
                self.readiness_payload(command),
            )
            self.process.return_code = 70
            return self.process

        with self.assertRaisesRegex(
            anchor.LaunchAnchorStartError,
            "exited after publishing readiness",
        ) as raised:
            self.start(popen_factory=exited_popen)

        self.addCleanup(self.force_close, raised.exception.handle)
        self.assertIs(self.process, raised.exception.handle.process)


class ReplacementAndArtifactTests(LaunchAnchorTestCase):
    def test_staged_wrapper_jar_inode_replacement_blocks_start(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        staged_wrapper = self.runtime / anchor.STAGED_WRAPPER_JAR_FILE_NAME
        staged_wrapper.unlink()
        staged_wrapper.write_bytes(self.wrapper_jar_content)
        staged_wrapper.chmod(0o400)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "staged runtime input"):
            handle.start_child()

        self.assertFalse((self.runtime / anchor.START_FILE_NAME).exists())

    def test_staged_wrapper_properties_inode_replacement_blocks_start(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        staged_properties = (
            self.runtime / anchor.STAGED_WRAPPER_PROPERTIES_FILE_NAME
        )
        staged_properties.unlink()
        staged_properties.write_bytes(self.wrapper_properties_content)
        staged_properties.chmod(0o400)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "staged runtime input"):
            handle.start_child()

        self.assertFalse((self.runtime / anchor.START_FILE_NAME).exists())

    def test_java_executable_inode_replacement_blocks_start(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        replacement = self.java_path.with_suffix(".replacement")
        replacement.write_bytes(self.java_path.read_bytes())
        replacement.chmod(0o700)
        os.replace(replacement, self.java_path)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "JDK 21 executable"):
            handle.start_child()

        self.assertFalse((self.runtime / anchor.START_FILE_NAME).exists())

    def test_pinned_readiness_inode_replacement_is_rejected(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        readiness = self.runtime / anchor.READINESS_FILE_NAME
        content = readiness.read_bytes()
        readiness.unlink()
        readiness.write_bytes(content)
        readiness.chmod(0o600)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "was replaced"):
            handle.start_child()

    def test_runtime_path_replacement_is_rejected_against_pinned_descriptor(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        original_runtime = self.root / "original-runtime"
        self.runtime.rename(original_runtime)
        self.runtime.mkdir(mode=0o700)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "runtime identity changed"):
            handle.verify_pinned_artifacts()

    def test_symbolic_link_child_artifact_is_rejected(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        target = self.root / "target.json"
        target.write_bytes(b"{}\n")
        (self.runtime / anchor.CHILD_STARTED_FILE_NAME).symlink_to(target)

        with self.assertRaises(anchor.LaunchAnchorError):
            handle.poll_child_started()

    def test_non_owner_private_child_artifact_is_rejected(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        path = self.raw_artifact(
            anchor.CHILD_STARTED_FILE_NAME,
            self.child_started_payload(handle),
        )
        path.chmod(0o644)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "Unsafe"):
            handle.poll_child_started()

    def test_wrong_child_token_or_executable_is_rejected(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        self.raw_artifact(
            anchor.CHILD_STARTED_FILE_NAME,
            self.child_started_payload(
                handle,
                executable="/bin/sh",
                token="b" * 64,
            ),
        )

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "not authenticated"):
            handle.poll_child_started()

    def test_child_artifact_byte_replacement_after_pin_is_rejected(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        path = self.raw_artifact(
            anchor.CHILD_STARTED_FILE_NAME,
            self.child_started_payload(handle),
        )
        child = handle.poll_child_started()
        self.assertIsNotNone(child)
        original = path.read_bytes()
        path.unlink()
        path.write_bytes(original)
        path.chmod(0o600)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "was replaced"):
            handle.poll_child_started()


class ChildLifecycleTests(LaunchAnchorTestCase):
    def test_child_pid_is_exposed_only_after_started_artifact(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        self.assertIsNone(handle.poll_child_started())

        self.raw_artifact(
            anchor.CHILD_STARTED_FILE_NAME,
            self.child_started_payload(handle),
        )
        child = handle.poll_child_started()

        self.assertEqual(
            anchor.ChildStarted(
                pid=44001,
                executable=str(self.java_path),
                arguments_sha256=handle.arguments_sha256,
            ),
            child,
        )
        self.assertEqual(handle.anchor_pid, handle.process_group_id)

    def test_result_is_available_while_anchor_deliberately_remains_alive(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        self.publish_successful_child(handle, exit_code=7)

        result = handle.poll_child_result()

        self.assertEqual(
            anchor.ChildResult(
                started=True,
                pid=44001,
                exit_code=7,
                arguments_sha256=handle.arguments_sha256,
            ),
            result,
        )
        self.assertIsNone(handle.process.poll())
        self.assertFalse((self.runtime / anchor.FINISH_FILE_NAME).exists())

    def test_finish_is_refused_until_child_result_exists(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        self.raw_artifact(
            anchor.CHILD_STARTED_FILE_NAME,
            self.child_started_payload(handle),
        )

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "before its child result"):
            handle.finish_normally()

        self.assertIsNone(handle.process.poll())
        self.assertFalse((self.runtime / anchor.FINISH_FILE_NAME).exists())

    def test_normal_finish_binds_result_then_reaps_anchor_without_deleting_evidence(
        self,
    ) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        self.publish_successful_child(handle, exit_code=3)

        return_code = handle.finish_normally(timeout_seconds=4.0)

        self.assertEqual(0, return_code)
        self.assertEqual([4.0], self.process.wait_calls)
        finish = json.loads((self.runtime / anchor.FINISH_FILE_NAME).read_bytes())
        self.assertEqual(
            {
                "argv_sha256": handle.arguments_sha256,
                "child_exit_code": 3,
                "child_pid": 44001,
                "child_started": True,
                "schema": anchor.FINISH_SCHEMA,
                "token": handle.token,
            },
            finish,
        )
        self.assertTrue((self.runtime / anchor.READINESS_FILE_NAME).exists())
        self.assertTrue((self.runtime / anchor.CHILD_RESULT_FILE_NAME).exists())
        self.assertEqual(0, handle.finish_normally(timeout_seconds=2.0))
        self.assertEqual([4.0, 2.0], self.process.wait_calls)

    def test_failed_process_builder_result_has_no_child_pid_and_can_finish(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        self.raw_artifact(
            anchor.CHILD_RESULT_FILE_NAME,
            self.child_result_payload(
                handle,
                started=False,
                process_id=None,
                exit_code=127,
            ),
        )

        result = handle.poll_child_result()
        self.assertEqual(
            anchor.ChildResult(False, None, 127, handle.arguments_sha256),
            result,
        )
        with self.assertRaises(anchor.LaunchAnchorChildStartError):
            handle.wait_child_started(timeout_seconds=0.1)
        self.assertEqual(0, handle.finish_normally())

    def test_finish_timeout_never_terminates_or_closes_anchor_handle(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)
        handle.start_child()
        self.publish_successful_child(handle)
        self.process.wait_exception = subprocess.TimeoutExpired("anchor", 0.1)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "did not exit"):
            handle.finish_normally(timeout_seconds=0.1)

        os.fstat(handle.runtime_directory_descriptor)
        self.assertTrue((self.runtime / anchor.FINISH_FILE_NAME).exists())


class CloseSemanticsTests(LaunchAnchorTestCase):
    def test_close_refuses_live_anchor_and_preserves_descriptor(self) -> None:
        handle = self.start()
        self.addCleanup(self.force_close, handle)

        with self.assertRaisesRegex(anchor.LaunchAnchorError, "live"):
            handle.close()

        os.fstat(handle.runtime_directory_descriptor)
        self.assertTrue((self.runtime / anchor.READINESS_FILE_NAME).exists())

    def test_close_reaps_terminal_anchor_closes_fd_and_preserves_every_file(self) -> None:
        handle = self.start()
        handle.start_child()
        self.publish_successful_child(handle)
        handle.finish_normally()
        files_before = sorted(path.name for path in self.runtime.iterdir())

        handle.close()

        self.assertEqual(-1, handle.runtime_directory_descriptor)
        self.assertEqual(-1, handle.staged_source.descriptor)
        self.assertEqual(-1, handle.staged_wrapper_jar.descriptor)
        self.assertEqual(-1, handle.staged_wrapper_properties.descriptor)
        self.assertEqual(files_before, sorted(path.name for path in self.runtime.iterdir()))
        handle.close()


class JavaSourceContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.content = JAVA_SOURCE_PATH.read_text(encoding="utf-8")

    def test_source_has_no_package_and_is_launchable_in_source_file_mode(self) -> None:
        self.assertFalse(
            any(line.startswith("package ") for line in self.content.splitlines())
        )
        self.assertIn("public final class ForgeServerLaunchAnchor", self.content)
        self.assertIn("public static void main(String[] arguments)", self.content)

    def test_source_never_releases_any_child_before_exact_start_token(self) -> None:
        await_index = self.content.index("START_FILE_NAME,\n            startContent")
        process_builder_index = self.content.index("new ProcessBuilder")
        self.assertLess(await_index, process_builder_index)
        self.assertIn("validateArtifact(path, expectedContent)", self.content)
        self.assertIn("Gradle arguments do not match their digest", self.content)

    def test_source_exits_if_controller_vanishes_or_stalls_before_start(self) -> None:
        self.assertIn('"--controller-pid".equals(arguments[44])', self.content)
        self.assertIn("ProcessHandle.current().parent().orElseThrow", self.content)
        self.assertIn("parent.pid() != configuration.controllerProcessId()", self.content)
        self.assertIn("|| !parent.isAlive()", self.content)
        self.assertIn("PRE_START_TIMEOUT_NANOSECONDS", self.content)
        self.assertIn("System.exit(70);", self.content)
        self.assertIn("launchState.authorizeChildRelease();", self.content)
        self.assertIn("if (launchState.childReleaseAuthorized())", self.content)
        await_method = self.content[
            self.content.index("private static void awaitExactStartArtifact") :
            self.content.index("private static byte[] readinessContent")
        ]
        self.assertLess(
            await_method.index("validateArtifact(path, expectedContent)"),
            await_method.index("launchState.authorizeChildRelease()"),
        )
        self.assertLess(
            await_method.index("launchState.authorizeChildRelease()"),
            await_method.index("validateControllerParent(configuration)"),
        )
        self.assertLess(
            await_method.index("launchState.authorizeChildRelease()"),
            await_method.index("PRE_START_TIMEOUT_NANOSECONDS"),
        )

    def test_source_launches_direct_immutable_argv_without_shell_or_new_session(self) -> None:
        self.assertIn(
            "new ProcessBuilder(configuration.gradleArguments())",
            self.content,
        )
        self.assertIn("builder.inheritIO();", self.content)
        self.assertNotIn('new ProcessBuilder("/bin/sh"', self.content)
        self.assertNotIn("setsid", self.content)
        self.assertIn("org.gradle.wrapper.GradleWrapperMain", self.content)
        self.assertIn("The child must be the exact direct JDK", self.content)

    def test_source_revalidates_root_owned_jdk_and_staged_jar_at_spawn(self) -> None:
        process_builder_index = self.content.index("new ProcessBuilder")
        final_validation_index = self.content.rindex(
            "validatePinnedLaunchInputs(configuration)",
            0,
            process_builder_index,
        )
        self.assertLess(final_validation_index, process_builder_index)
        self.assertIn("configuration.childJavaOwnerUserId() != 0", self.content)
        self.assertIn("configuration.wrapperJarSha256()", self.content)
        self.assertIn("configuration.wrapperPropertiesSha256()", self.content)
        self.assertIn("MAXIMUM_WRAPPER_JAR_SIZE_BYTES", self.content)
        self.assertIn("wrapperJar.getParent()", self.content)

    def test_source_keeps_anchor_alive_until_result_bound_finish(self) -> None:
        wait_index = self.content.index("int childExitCode = waitForChildUninterruptibly")
        result_index = self.content.index(
            "CHILD_RESULT_FILE_NAME,\n                childResultContent",
            wait_index,
        )
        finish_index = self.content.index(
            "FINISH_FILE_NAME,\n                finishContent",
            result_index,
        )
        self.assertLess(wait_index, result_index)
        self.assertLess(result_index, finish_index)
        self.assertIn("parkForever();", self.content)

    def test_source_uses_exclusive_atomic_owner_private_artifacts(self) -> None:
        self.assertIn("StandardOpenOption.CREATE_NEW", self.content)
        self.assertIn("Files.createLink(destination, temporary)", self.content)
        self.assertIn('PosixFilePermissions.fromString("rw-------")', self.content)
        self.assertIn('|| (mode.intValue() & 0777) != 0700', self.content)
        self.assertIn('"unix:nlink"', self.content)


if __name__ == "__main__":
    unittest.main()
