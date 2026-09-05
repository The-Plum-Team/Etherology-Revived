from __future__ import annotations

from contextlib import contextmanager
import hashlib
import os
from pathlib import Path
from types import SimpleNamespace
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_server


ANCHOR_PID = 43_210
WRAPPER_PID = 43_211
SERVER_PID = 43_212
JAVA_EXECUTABLE = "/test/jdk/bin/java"


class FakeProcess:
    def __init__(self, pid: int = ANCHOR_PID, exit_code: int | None = None) -> None:
        self.pid = pid
        self.exit_code = exit_code

    def poll(self) -> int | None:
        return self.exit_code


class FakeToolPath:
    def __init__(
        self,
        *,
        absolute: bool = True,
        owner_user_id: int = 0,
        mode: int = stat.S_IFREG | 0o755,
        symlink: bool = False,
    ) -> None:
        self.absolute = absolute
        self.owner_user_id = owner_user_id
        self.mode = mode
        self.symlink = symlink

    def lstat(self) -> SimpleNamespace:
        return SimpleNamespace(st_mode=self.mode, st_uid=self.owner_user_id)

    def is_absolute(self) -> bool:
        return self.absolute

    def is_symlink(self) -> bool:
        return self.symlink

    def __str__(self) -> str:
        return "/usr/bin/ps"


def owned_java(pid: int) -> forge_server.macos_guarded_java.OwnedJavaProcess:
    return forge_server.macos_guarded_java.OwnedJavaProcess(
        pid=pid,
        process_group_id=ANCHOR_PID,
        proc_start_abstime=pid * 100,
        expected_executable=JAVA_EXECUTABLE,
    )


def missing_sample() -> SimpleNamespace:
    return SimpleNamespace(
        status=forge_server.macos_guarded_java.SampleStatus.MISSING
    )


def anchored_guards(
    state_root: Path,
) -> tuple[
    FakeProcess,
    SimpleNamespace,
    SimpleNamespace,
    SimpleNamespace,
    object,
]:
    process = FakeProcess()
    anchor_target = owned_java(ANCHOR_PID)
    wrapper_target = owned_java(WRAPPER_PID)
    server_target = owned_java(SERVER_PID)
    anchor_handle = SimpleNamespace(
        process=process,
        anchor_pid=process.pid,
        arguments_sha256="a" * 64,
    )
    watchdog = object()
    runtime = SimpleNamespace(path=state_root / "launch-runtime")
    wrapper_guard = SimpleNamespace(
        target=wrapper_target,
        monitor=object(),
        spawned_monitor_process=object(),
        runtime=runtime,
        launch_watchdog=watchdog,
        launch_anchor=anchor_handle,
        anchor_target=anchor_target,
    )
    server_guard = SimpleNamespace(
        target=server_target,
        monitor=object(),
        spawned_monitor_process=object(),
        caffeinate_process=object(),
    )
    launch = forge_server.GradleLaunchAnchor(
        anchor_handle,
        anchor_target,
        runtime,
        watchdog,
    )
    return process, wrapper_guard, server_guard, launch, watchdog


class ProcessGroupInventoryTests(unittest.TestCase):
    def test_inventory_uses_fixed_ps_contract_and_parses_exact_group(self) -> None:
        tool = FakeToolPath()
        completed = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout=(
                b"  8   7\n"
                b"43212 43210\n"
                b"43210 43210\n"
                b"43211 43210\n"
            ),
            stderr=b"",
        )
        with (
            mock.patch.object(forge_server.os, "access", return_value=True),
            mock.patch.object(
                forge_server.subprocess,
                "run",
                return_value=completed,
            ) as run,
        ):
            members = forge_server.read_process_group_inventory(
                ANCHOR_PID,
                tool,
            )

        self.assertEqual((ANCHOR_PID, WRAPPER_PID, SERVER_PID), members)
        run.assert_called_once_with(
            ["/usr/bin/ps", "-axo", "pid=,pgid="],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env={"LANG": "C", "LC_ALL": "C", "PATH": "/usr/bin:/bin"},
            timeout=forge_server.PROCESS_GROUP_INVENTORY_TIMEOUT_SECONDS,
            check=False,
        )

    def test_inventory_rejects_unsafe_tool_before_execution(self) -> None:
        unsafe_tools = (
            FakeToolPath(absolute=False),
            FakeToolPath(owner_user_id=501),
            FakeToolPath(mode=stat.S_IFREG | 0o775),
            FakeToolPath(mode=stat.S_IFDIR | 0o755),
            FakeToolPath(symlink=True),
        )
        for tool in unsafe_tools:
            with self.subTest(tool=tool.__dict__):
                with (
                    mock.patch.object(forge_server.os, "access", return_value=True),
                    mock.patch.object(forge_server.subprocess, "run") as run,
                    self.assertRaisesRegex(forge_server.E2EError, "tool is unsafe"),
                ):
                    forge_server.read_process_group_inventory(ANCHOR_PID, tool)
                run.assert_not_called()

        with (
            mock.patch.object(forge_server.os, "access", return_value=False),
            mock.patch.object(forge_server.subprocess, "run") as run,
            self.assertRaisesRegex(forge_server.E2EError, "tool is unsafe"),
        ):
            forge_server.read_process_group_inventory(
                ANCHOR_PID,
                FakeToolPath(),
            )
        run.assert_not_called()

    def test_inventory_rejects_ambiguous_or_malformed_output(self) -> None:
        outputs = (
            b"43210 43210\n43210 43210\n",
            b"43210 43210 extra\n",
            b"0 43210\n",
            b"2147483648 43210\n",
            b"\n",
        )
        for output in outputs:
            with self.subTest(output=output):
                completed = subprocess.CompletedProcess(
                    args=[],
                    returncode=0,
                    stdout=output,
                    stderr=b"",
                )
                with (
                    mock.patch.object(forge_server.os, "access", return_value=True),
                    mock.patch.object(
                        forge_server.subprocess,
                        "run",
                        return_value=completed,
                    ),
                    self.assertRaisesRegex(
                        forge_server.E2EError,
                        "malformed output|invalid identity",
                    ),
                ):
                    forge_server.read_process_group_inventory(
                        ANCHOR_PID,
                        FakeToolPath(),
                    )

    def test_inventory_rejects_nonpositive_and_boolean_group_ids(self) -> None:
        for value in (0, -1, True):
            with self.subTest(value=value):
                with self.assertRaisesRegex(
                    forge_server.E2EError,
                    "inventory ID is invalid",
                ):
                    forge_server.read_process_group_inventory(value)


class GradleChildResultTests(unittest.TestCase):
    def guard_and_handle(
        self,
        result: object,
        *,
        anchor_exit_code: int | None = None,
    ) -> tuple[FakeProcess, SimpleNamespace, SimpleNamespace]:
        process = FakeProcess(exit_code=anchor_exit_code)
        handle = SimpleNamespace(
            process=process,
            anchor_pid=process.pid,
            arguments_sha256="a" * 64,
            poll_child_result=mock.Mock(return_value=result),
        )
        guard = SimpleNamespace(
            launch_anchor=handle,
            target=owned_java(WRAPPER_PID),
        )
        return process, guard, handle

    def test_authenticated_broker_result_returns_gradle_exit_code(self) -> None:
        result = forge_server.forge_server_launch_anchor.ChildResult(
            started=True,
            pid=WRAPPER_PID,
            exit_code=17,
            arguments_sha256="a" * 64,
        )
        process, guard, handle = self.guard_and_handle(result)

        self.assertEqual(
            17,
            forge_server.poll_gradle_child_exit_code(process, guard),
        )
        handle.poll_child_result.assert_called_once_with()

    def test_pending_broker_result_does_not_poll_anchor_as_gradle(self) -> None:
        process, guard, handle = self.guard_and_handle(None)

        self.assertIsNone(
            forge_server.poll_gradle_child_exit_code(process, guard)
        )
        handle.poll_child_result.assert_called_once_with()

    def test_result_identity_or_digest_drift_is_rejected(self) -> None:
        cases = (
            forge_server.forge_server_launch_anchor.ChildResult(
                started=False,
                pid=None,
                exit_code=1,
                arguments_sha256="a" * 64,
            ),
            forge_server.forge_server_launch_anchor.ChildResult(
                started=True,
                pid=WRAPPER_PID + 1,
                exit_code=0,
                arguments_sha256="a" * 64,
            ),
            forge_server.forge_server_launch_anchor.ChildResult(
                started=True,
                pid=WRAPPER_PID,
                exit_code=0,
                arguments_sha256="b" * 64,
            ),
        )
        for result in cases:
            with self.subTest(result=result):
                process, guard, _handle = self.guard_and_handle(result)
                with self.assertRaisesRegex(
                    forge_server.E2EError,
                    "child result changed identity",
                ):
                    forge_server.poll_gradle_child_exit_code(process, guard)

    def test_result_is_rejected_if_broker_exited_or_handle_is_substituted(self) -> None:
        result = forge_server.forge_server_launch_anchor.ChildResult(
            started=True,
            pid=WRAPPER_PID,
            exit_code=0,
            arguments_sha256="a" * 64,
        )
        process, guard, _handle = self.guard_and_handle(
            result,
            anchor_exit_code=0,
        )
        with self.assertRaisesRegex(
            forge_server.E2EError,
            "child result changed identity",
        ):
            forge_server.poll_gradle_child_exit_code(process, guard)

        live_process, live_guard, _handle = self.guard_and_handle(result)
        substitute = FakeProcess(pid=live_process.pid)
        with self.assertRaisesRegex(
            forge_server.E2EError,
            "not bound to its launch anchor",
        ):
            forge_server.poll_gradle_child_exit_code(substitute, live_guard)


class AnchoredChildResultTransitionTests(unittest.TestCase):
    def test_wrapper_absence_is_bounded_until_broker_publishes_result(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            process, wrapper, _server, _launch, _watchdog = anchored_guards(
                state_root
            )
            output_path = state_root / "gradle.log"
            output_path.write_bytes(b"")
            with (
                mock.patch.object(
                    forge_server,
                    "poll_gradle_child_exit_code",
                    side_effect=(None, 0),
                ),
                mock.patch.object(
                    forge_server,
                    "verify_active_launch_runtime_inventory",
                ),
                mock.patch.object(
                    forge_server,
                    "verify_wrapper_launch_supervision",
                ) as verify_supervision,
                mock.patch.object(
                    forge_server,
                    "verify_exact_java_memory_envelope",
                ) as verify_envelope,
                mock.patch.object(forge_server.time, "sleep"),
            ):
                exit_code = forge_server.wait_for_bounded_process(
                    process,
                    output_path,
                    wrapper_guard=wrapper,
                    sampler=mock.Mock(),
                )

            self.assertEqual(0, exit_code)
            self.assertEqual(2, verify_supervision.call_count)
            self.assertTrue(
                all(
                    call.kwargs["allow_missing"]
                    for call in verify_supervision.call_args_list
                )
            )
            self.assertEqual(2, verify_envelope.call_count)
            for call in verify_envelope.call_args_list:
                self.assertEqual((wrapper.anchor_target,), call.args[1])
                self.assertEqual((wrapper.target,), call.args[2])


class AnchoredServerCompletionTests(unittest.TestCase):
    def test_completed_server_and_wrapper_retain_only_exact_anchor(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            process, wrapper, server, _launch, _watchdog = anchored_guards(
                state_root
            )
            sampler = mock.Mock()
            sampler.sample.return_value = missing_sample()
            with (
                mock.patch.object(
                    forge_server,
                    "poll_gradle_child_exit_code",
                    return_value=0,
                ),
                mock.patch.object(
                    forge_server,
                    "verify_gradle_launch_anchor_guard",
                ) as verify_anchor,
                mock.patch.object(
                    forge_server,
                    "verify_java_process_inventory",
                ) as verify_java_inventory,
                mock.patch.object(
                    forge_server,
                    "read_process_group_inventory",
                    return_value=(ANCHOR_PID,),
                ) as read_group,
                mock.patch.object(
                    forge_server,
                    "verify_owned_gradle_process_group",
                ) as verify_group,
            ):
                forge_server.require_guarded_server_stopped(
                    process,
                    server,
                    sampler,
                    wrapper,
                )

            sampled_targets = [call.args[0] for call in sampler.sample.call_args_list]
            self.assertEqual([server.target, wrapper.target], sampled_targets)
            verify_java_inventory.assert_called_once_with(
                (wrapper.anchor_target,)
            )
            read_group.assert_called_once_with(ANCHOR_PID)
            self.assertEqual(2, verify_group.call_count)
            retained_launch = verify_anchor.call_args.args[0]
            self.assertIs(wrapper.launch_anchor, retained_launch.handle)
            self.assertEqual(wrapper.anchor_target, retained_launch.target)

    def test_native_or_java_group_survivor_blocks_anchor_release(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            _process, _wrapper, _server, launch, _watchdog = anchored_guards(
                state_root
            )
            with (
                mock.patch.object(
                    forge_server,
                    "verify_owned_gradle_process_group",
                ),
                mock.patch.object(
                    forge_server,
                    "read_process_group_inventory",
                    return_value=(ANCHOR_PID, 99_999),
                ),
                self.assertRaisesRegex(
                    forge_server.CleanupUncertainError,
                    "group was not quiescent",
                ),
            ):
                forge_server.verify_launch_group_contains_only_anchor(launch)


class AnchorCleanupOrderingTests(unittest.TestCase):
    def test_server_auxiliaries_stop_before_wrapper_and_anchor_teardown(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            process, wrapper, server, _launch, _watchdog = anchored_guards(
                state_root
            )
            events: list[str] = []
            pinned = object()
            with (
                mock.patch.object(
                    forge_server,
                    "require_guarded_server_stopped",
                    side_effect=lambda *_args: events.append("prove quiescence"),
                ),
                mock.patch.object(
                    forge_server,
                    "stop_server_guard_auxiliaries",
                    side_effect=lambda *_args: events.append(
                        "stop server auxiliaries"
                    ),
                ),
                mock.patch.object(
                    forge_server,
                    "cleanup_wrapper_java_guard",
                    side_effect=lambda *_args, **_kwargs: (
                        events.append("tear down wrapper and anchor") or pinned
                    ),
                ),
            ):
                result = forge_server.cleanup_server_launch(
                    process,
                    server,
                    mock.Mock(),
                    wrapper_guard=wrapper,
                    state_root=state_root,
                    require_normal_watchdog_exit=True,
                )

            self.assertIs(pinned, result)
            self.assertEqual(
                [
                    "prove quiescence",
                    "stop server auxiliaries",
                    "tear down wrapper and anchor",
                ],
                events,
            )

    def test_server_auxiliary_failure_preserves_wrapper_and_anchor_teardown(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            process, wrapper, server, _launch, _watchdog = anchored_guards(
                state_root
            )
            with (
                mock.patch.object(
                    forge_server,
                    "require_guarded_server_stopped",
                ),
                mock.patch.object(
                    forge_server,
                    "stop_server_guard_auxiliaries",
                    side_effect=forge_server.CleanupUncertainError(
                        "server monitor stuck"
                    ),
                ),
                mock.patch.object(
                    forge_server,
                    "cleanup_wrapper_java_guard",
                ) as cleanup_wrapper,
                self.assertRaisesRegex(
                    forge_server.CleanupUncertainError,
                    "server monitor stuck",
                ),
            ):
                forge_server.cleanup_server_launch(
                    process,
                    server,
                    mock.Mock(),
                    wrapper_guard=wrapper,
                    state_root=state_root,
                    require_normal_watchdog_exit=True,
                )

            cleanup_wrapper.assert_not_called()

    def test_failed_launch_group_cleanup_includes_exact_anchor_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            process, wrapper, server, _launch, _watchdog = anchored_guards(
                state_root
            )
            pinned = object()
            with (
                mock.patch.object(
                    forge_server,
                    "stop_owned_launch_group",
                ) as stop_group,
                mock.patch.object(
                    forge_server,
                    "wait_for_detached_java_absence",
                    return_value=True,
                ),
                mock.patch.object(
                    forge_server,
                    "require_guarded_server_stopped",
                ),
                mock.patch.object(
                    forge_server,
                    "stop_server_guard_auxiliaries",
                ),
                mock.patch.object(
                    forge_server,
                    "cleanup_wrapper_java_guard",
                    return_value=pinned,
                ),
            ):
                result = forge_server.cleanup_server_launch(
                    process,
                    server,
                    mock.Mock(),
                    wrapper_guard=wrapper,
                    state_root=state_root,
                    require_normal_watchdog_exit=False,
                )

            self.assertIs(pinned, result)
            stop_group.assert_called_once_with(
                process,
                mock.ANY,
                (wrapper.anchor_target, wrapper.target, server.target),
            )

    def test_unbound_anchor_cleanup_retains_exact_identity_until_absence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            process, _wrapper, _server, launch, watchdog = anchored_guards(
                state_root
            )
            launch.handle.close = mock.Mock()
            watchdog_handle = mock.Mock()
            launch = forge_server.GradleLaunchAnchor(
                launch.handle,
                launch.target,
                launch.runtime,
                watchdog_handle,
            )
            with (
                mock.patch.object(forge_server, "stop_process_group") as stop_group,
                mock.patch.object(
                    forge_server,
                    "wait_for_detached_java_absence",
                    return_value=True,
                ) as wait_absence,
                mock.patch.object(
                    forge_server.forge_server_launch_watchdog,
                    "finish_launch_watchdog",
                ) as finish_watchdog,
                mock.patch.object(
                    forge_server,
                    "retire_owned_runtime_directory",
                ) as retire_runtime,
            ):
                forge_server.cleanup_gradle_launch_anchor_without_wrapper(
                    launch,
                    mock.Mock(),
                    state_root,
                )

            stop_group.assert_called_once_with(process)
            observations = wait_absence.call_args.args[0]
            self.assertEqual(1, len(observations))
            self.assertEqual(launch.target, observations[0].target)
            self.assertEqual(ANCHOR_PID, observations[0].process_group_id)
            self.assertEqual(ANCHOR_PID, observations[0].session_id)
            finish_watchdog.assert_called_once_with(
                watchdog_handle,
                require_normal_exit=False,
            )
            watchdog_handle.close_runtime_directory.assert_called_once_with()
            launch.handle.close.assert_called_once_with()
            retire_runtime.assert_called_once_with(launch.runtime)

    def test_unbound_anchor_stop_failure_retains_all_teardown_owners(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            state_root = Path(temporary_directory).resolve()
            _process, _wrapper, _server, launch, _watchdog = anchored_guards(
                state_root
            )
            launch.handle.close = mock.Mock()
            watchdog_handle = mock.Mock()
            launch = forge_server.GradleLaunchAnchor(
                launch.handle,
                launch.target,
                launch.runtime,
                watchdog_handle,
            )
            with (
                mock.patch.object(
                    forge_server,
                    "stop_process_group",
                    side_effect=forge_server.E2EError("group identity drift"),
                ),
                mock.patch.object(
                    forge_server.forge_server_launch_watchdog,
                    "finish_launch_watchdog",
                ) as finish_watchdog,
                mock.patch.object(
                    forge_server,
                    "retire_owned_runtime_directory",
                ) as retire_runtime,
                self.assertRaisesRegex(
                    forge_server.CleanupUncertainError,
                    "group identity drift",
                ),
            ):
                forge_server.cleanup_gradle_launch_anchor_without_wrapper(
                    launch,
                    mock.Mock(),
                    state_root,
                )

            finish_watchdog.assert_not_called()
            watchdog_handle.close_runtime_directory.assert_not_called()
            launch.handle.close.assert_not_called()
            retire_runtime.assert_not_called()


class LaunchAnchorEvidenceFixture:
    def __init__(self, runtime: Path) -> None:
        self.runtime = runtime
        self.expected_descriptor = -1
        self.watchdog_descriptor = -1
        self.anchor_descriptor = -1
        self.watchdog = None
        self.anchor = None

    def __enter__(self) -> LaunchAnchorEvidenceFixture:
        self.runtime.chmod(0o700)
        readiness_name = (
            forge_server.forge_server_launch_watchdog.READINESS_FILE_NAME
        )
        telemetry_name = (
            forge_server.forge_server_launch_watchdog.TELEMETRY_FILE_NAME
        )
        watchdog_contents = {
            readiness_name: b'{"status":"ready"}\n',
            telemetry_name: b'{"status":"normal"}\n',
        }
        for name, content in watchdog_contents.items():
            path = self.runtime / name
            path.write_bytes(content)
            path.chmod(0o600)

        pinned_artifacts = {}
        for index, name in enumerate(
            forge_server.forge_server_launch_anchor.ARTIFACT_FILE_NAMES
        ):
            content = (
                f'{{"artifact":{index},"name":"{name}"}}\n'.encode("utf-8")
            )
            path = self.runtime / name
            path.write_bytes(content)
            path.chmod(0o600)
            information = path.stat()
            identity = forge_server.forge_server_launch_anchor.FileIdentity(
                device=information.st_dev,
                inode=information.st_ino,
                owner_user_id=information.st_uid,
                mode=stat.S_IMODE(information.st_mode),
                link_count=information.st_nlink,
                size=information.st_size,
            )
            pinned_artifacts[name] = (
                forge_server.forge_server_launch_anchor.PinnedArtifact(
                    file_name=name,
                    identity=identity,
                    content=content,
                    sha256=hashlib.sha256(content).hexdigest(),
                )
            )

        directory_flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
        self.expected_descriptor = os.open(self.runtime, directory_flags)
        self.watchdog_descriptor = os.dup(self.expected_descriptor)
        self.anchor_descriptor = os.dup(self.expected_descriptor)
        self.watchdog = SimpleNamespace(
            runtime_directory_descriptor=self.watchdog_descriptor,
            readiness_path=self.runtime / readiness_name,
            telemetry_path=self.runtime / telemetry_name,
            verified_terminal_artifact_contents=watchdog_contents,
        )
        self.anchor = SimpleNamespace(
            runtime_directory_descriptor=self.anchor_descriptor,
            runtime_directory=self.runtime,
            pinned_artifacts=pinned_artifacts,
            verify_pinned_artifacts=mock.Mock(),
        )
        return self

    def __exit__(self, *_exception: object) -> None:
        for descriptor in (
            self.anchor_descriptor,
            self.watchdog_descriptor,
            self.expected_descriptor,
        ):
            if descriptor >= 0:
                os.close(descriptor)


@contextmanager
def launch_anchor_evidence_fixture():
    with tempfile.TemporaryDirectory() as temporary_directory:
        runtime = Path(temporary_directory).resolve()
        with LaunchAnchorEvidenceFixture(runtime) as fixture:
            yield fixture


class LaunchAnchorEvidenceTests(unittest.TestCase):
    def test_pin_and_verify_preserve_complete_anchor_provenance(self) -> None:
        with launch_anchor_evidence_fixture() as fixture:
            pinned = forge_server.pin_terminal_watchdog_evidence(
                fixture.watchdog,
                fixture.expected_descriptor,
                fixture.anchor,
            )
            try:
                records = forge_server.verify_pinned_launch_anchor_evidence(
                    pinned,
                    fixture.expected_descriptor,
                )
                self.assertEqual(
                    forge_server.forge_server_launch_anchor.ARTIFACT_FILE_NAMES,
                    tuple(records),
                )
                self.assertEqual(
                    len(
                        forge_server.forge_server_launch_anchor.ARTIFACT_FILE_NAMES
                    ),
                    len(pinned.launch_anchor_artifacts),
                )
                fixture.anchor.verify_pinned_artifacts.assert_called_once_with()
            finally:
                forge_server.close_pinned_watchdog_evidence(pinned)

    def test_anchor_replacement_after_semantic_verification_is_rejected_at_pin(
        self,
    ) -> None:
        with launch_anchor_evidence_fixture() as fixture:
            name = forge_server.forge_server_launch_anchor.CHILD_RESULT_FILE_NAME
            path = fixture.runtime / name
            replacement = fixture.runtime / "replacement.tmp"
            replacement.write_bytes(path.read_bytes())
            replacement.chmod(0o600)
            os.replace(replacement, path)

            with self.assertRaisesRegex(
                forge_server.CleanupUncertainError,
                "launch-anchor artifact identity is unsafe",
            ):
                forge_server.pin_terminal_watchdog_evidence(
                    fixture.watchdog,
                    fixture.expected_descriptor,
                    fixture.anchor,
                )

    def test_anchor_path_replacement_invalidates_already_pinned_evidence(
        self,
    ) -> None:
        with launch_anchor_evidence_fixture() as fixture:
            pinned = forge_server.pin_terminal_watchdog_evidence(
                fixture.watchdog,
                fixture.expected_descriptor,
                fixture.anchor,
            )
            try:
                name = forge_server.forge_server_launch_anchor.FINISH_FILE_NAME
                path = fixture.runtime / name
                replacement = fixture.runtime / "replacement.tmp"
                replacement.write_bytes(path.read_bytes())
                replacement.chmod(0o600)
                os.replace(replacement, path)

                with self.assertRaisesRegex(
                    forge_server.CleanupUncertainError,
                    "pinned launch-anchor artifact changed",
                ):
                    forge_server.verify_pinned_launch_anchor_evidence(
                        pinned,
                        fixture.expected_descriptor,
                    )
            finally:
                forge_server.close_pinned_watchdog_evidence(pinned)


if __name__ == "__main__":
    unittest.main()
