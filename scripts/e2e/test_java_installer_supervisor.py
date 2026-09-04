from __future__ import annotations

from collections import deque
import hashlib
import io
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import java_installer_supervisor as supervisor
import macos_guarded_java


class AnchorKilled(BaseException):
    pass


class FakeSocket:
    def __init__(self, received: list[bytes] | None = None) -> None:
        self.received = list(received or [])
        self.sent = bytearray()
        self.blocking: bool | None = None

    def setblocking(self, blocking: bool) -> None:
        self.blocking = blocking

    def recv(self, maximum_size: int) -> bytes:
        if not self.received:
            raise BlockingIOError
        content = self.received.pop(0)
        if len(content) <= maximum_size:
            return content
        self.received.insert(0, content[maximum_size:])
        return content[:maximum_size]

    def send(self, content: memoryview) -> int:
        payload = bytes(content)
        self.sent.extend(payload)
        return len(payload)


class JavaInstallerSupervisorTests(unittest.TestCase):
    def activation_fixture(
        self,
        root: Path,
    ) -> tuple[dict[str, object], supervisor.Activation]:
        java_home = root / "jdk-17"
        java_bin = java_home / "bin"
        java_bin.mkdir(parents=True)
        java_path = java_bin / "java"
        java_path.write_bytes(b"not executed")
        java_path.chmod(0o700)
        (java_home / "release").write_text(
            'JAVA_VERSION="17.0.12"\n',
            encoding="utf-8",
        )
        launcher_root = root / "launcher"
        installers = launcher_root / "installers"
        installers.mkdir(parents=True)
        installer_path = installers / "forge-installer.jar"
        installer_content = b"pinned forge installer"
        installer_path.write_bytes(installer_content)
        run_id = "a" * 64
        runtime_directory = root / "supervisor-runtime"
        runtime_directory.mkdir(mode=0o700)
        supervisor_pid = 123
        supervisor_proc_start_abstime = 987654321 + supervisor_pid
        supervisor_executable = "/test/python3"
        frame: dict[str, object] = {
            "schema": 1,
            "action": "ACTIVATE",
            "run_id": run_id,
            "controller_pid": 321,
            "supervisor_pid": supervisor_pid,
            "supervisor_process_group_id": supervisor_pid,
            "supervisor_session_id": supervisor_pid,
            "supervisor_proc_start_abstime": supervisor_proc_start_abstime,
            "supervisor_executable": supervisor_executable,
            "installer_kind": "forge-client",
            "java_path": str(java_path),
            "installer_path": str(installer_path),
            "installer_size": len(installer_content),
            "installer_sha256": hashlib.sha256(installer_content).hexdigest(),
            "launcher_root": str(launcher_root),
            "runtime_directory": str(runtime_directory),
            "maximum_memory_mb": 1024,
            "install_timeout_seconds": 900,
        }
        activation = supervisor.Activation(
            run_id=run_id,
            controller_pid=321,
            supervisor_pid=supervisor_pid,
            supervisor_process_group_id=supervisor_pid,
            supervisor_session_id=supervisor_pid,
            supervisor_proc_start_abstime=supervisor_proc_start_abstime,
            supervisor_executable=supervisor_executable,
            installer_kind="forge-client",
            java_path=java_path,
            installer_path=installer_path,
            installer_size=len(installer_content),
            installer_sha256=hashlib.sha256(installer_content).hexdigest(),
            launcher_root=launcher_root,
            runtime_directory=runtime_directory,
            maximum_memory_mb=1024,
            install_timeout_seconds=900,
        )
        return frame, activation

    def target(
        self,
        *,
        pid: int = 456,
        executable: str = "/test/java",
    ) -> macos_guarded_java.OwnedJavaProcess:
        return macos_guarded_java.OwnedJavaProcess(
            pid=pid,
            process_group_id=123,
            proc_start_abstime=987654321 + pid,
            expected_executable=executable,
        )

    def parse_activation_fixture(
        self,
        frame: dict[str, object],
        activation: supervisor.Activation,
    ) -> supervisor.Activation:
        supervisor_target = self.target(pid=123, executable="/test/python3")
        with (
            mock.patch.object(supervisor.os, "getppid", return_value=321),
            mock.patch.object(
                supervisor,
                "OWNED_STATE_ROOT",
                activation.launcher_root.parent,
            ),
        ):
            return supervisor.parse_activation(frame, supervisor_target, 123)

    def launch_fixture(
        self,
        root: Path,
        activation: supervisor.Activation,
    ) -> supervisor.InstallerLaunch:
        supervisor_target = self.target(pid=123, executable="/test/python3")
        java_target = self.target(pid=456, executable=str(activation.java_path))
        java_process = mock.Mock(pid=java_target.pid)
        monitor_process = mock.Mock(pid=789)
        monitor = macos_guarded_java.GuardedJavaMonitor(
            process=monitor_process,
            target=java_target,
            telemetry_path=root / "memory-guard-telemetry.json",
            readiness_path=root / ".memory-guard-ready.json",
            group_anchor=supervisor_target,
        )
        monitor_process_target = macos_guarded_java.OwnedJavaProcess(
            pid=monitor_process.pid,
            process_group_id=monitor_process.pid,
            proc_start_abstime=987654321 + monitor_process.pid,
            expected_executable="/test/python3",
        )
        installer_log = mock.Mock(spec=supervisor.BoundedLog)
        installer_log.path = root / supervisor.INSTALLER_LOG_NAME
        installer_log.tail = bytearray(b"bounded output")
        monitor_log = mock.Mock(spec=supervisor.BoundedLog)
        monitor_log.path = root / supervisor.MONITOR_LOG_NAME
        monitor_log.tail = bytearray()
        return supervisor.InstallerLaunch(
            activation=activation,
            supervisor_target=supervisor_target,
            supervisor_session_id=123,
            java_process=java_process,
            java_target=java_target,
            java_session_id=123,
            monitor=monitor,
            monitor_process_target=monitor_process_target,
            monitor_session_id=monitor_process.pid,
            sampler=mock.Mock(),
            runtime_directory=root,
            installer_log=installer_log,
            monitor_log=monitor_log,
            started_at=10.0,
            command_sha256="b" * 64,
        )

    def encode_frame(self, frame: dict[str, object]) -> bytes:
        payload = supervisor.canonical_json_bytes(frame)
        return len(payload).to_bytes(4, "big") + payload

    def terminal_payload(
        self,
        launch: supervisor.InstallerLaunch,
    ) -> dict[str, object]:
        return {
            "schema": 1,
            "target": supervisor.identity_payload(launch.java_target),
            "policy": macos_guarded_java.memory_policy_payload(1024),
            "state": {
                "enforcement_disarmed": False,
                "stop_callback_invoked": False,
                "sample_count": 2,
                "retained_record_count": 2,
                "dropped_record_count": 0,
                "last_stop_outcome": "not-required",
            },
            "records": [
                {
                    "observed_at_monotonic_ns": 1,
                    "source": "proc-pid-rusage-v4",
                    "status": "available",
                    "identity_matches_target": True,
                    "current_phys_footprint_bytes": 1,
                    "resident_size_bytes": 1,
                    "virtual_size_bytes": None,
                    "lifetime_max_phys_footprint_bytes": 1,
                    "detail": "",
                    "decision": "normal",
                    "stop_outcome": "not-required",
                },
                {
                    "observed_at_monotonic_ns": 2,
                    "source": "proc-pid-rusage-v4",
                    "status": "missing",
                    "identity_matches_target": None,
                    "current_phys_footprint_bytes": None,
                    "resident_size_bytes": None,
                    "virtual_size_bytes": None,
                    "lifetime_max_phys_footprint_bytes": None,
                    "detail": "target exited",
                    "decision": "not-enforceable",
                    "stop_outcome": "not-required",
                },
            ],
        }

    def test_length_prefixed_json_round_trip_is_canonical_and_bounded(self) -> None:
        frame = {"schema": 1, "action": "LEASE", "run_id": "a" * 64, "sequence": 1}
        encoded = self.encode_frame(frame)
        fake_socket = FakeSocket([encoded[:2], encoded[2:9], encoded[9:]])
        control = supervisor.FramedControl(fake_socket)  # type: ignore[arg-type]

        control.poll()
        decoded = control.frames.popleft()
        control.send(frame)

        self.assertEqual(frame, decoded)
        self.assertEqual(encoded, bytes(fake_socket.sent))
        self.assertFalse(fake_socket.blocking)

    def test_json_decoder_rejects_duplicates_nan_nonobjects_and_oversize(self) -> None:
        invalid_payloads = (
            b'{"schema":1,"schema":1}',
            b'{"value":NaN}',
            b'{"schema": 1}',
            b"[]",
            b"\xff",
        )
        for payload in invalid_payloads:
            with self.subTest(payload=payload):
                with self.assertRaises(supervisor.SupervisorError):
                    supervisor.decode_json_payload(payload)

        fake_socket = FakeSocket(
            [(supervisor.MAXIMUM_FRAME_SIZE + 1).to_bytes(4, "big")]
        )
        control = supervisor.FramedControl(fake_socket)  # type: ignore[arg-type]
        with self.assertRaisesRegex(supervisor.SupervisorError, "length"):
            control.poll()

        valid = self.encode_frame(
            {"action": "LEASE", "run_id": "a" * 64, "schema": 1, "sequence": 1}
        )
        control = supervisor.FramedControl(  # type: ignore[arg-type]
            FakeSocket([valid + b"\x00\x00", b""])
        )
        with self.assertRaisesRegex(supervisor.SupervisorError, "partial"):
            control.poll()

    def test_activation_accepts_only_exact_pinned_forge_client_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            frame, expected = self.activation_fixture(Path(temporary_directory).resolve())
            actual = self.parse_activation_fixture(frame, expected)

        self.assertEqual(expected, actual)
        self.assertEqual(
            [
                str(expected.java_path),
                "-Xmx1024M",
                "-jar",
                str(expected.installer_path),
                "--installClient",
                str(expected.launcher_root),
            ],
            supervisor.build_installer_command(actual),
        )

    def test_activation_rejects_every_open_or_unbounded_surface(self) -> None:
        mutations = {
            "extra field": lambda frame: frame.update({"argv": ["arbitrary"]}),
            "boolean schema": lambda frame: frame.update({"schema": True}),
            "future kind": lambda frame: frame.update({"installer_kind": "fabric-client"}),
            "wrong heap": lambda frame: frame.update({"maximum_memory_mb": 2048}),
            "wrong timeout": lambda frame: frame.update({"install_timeout_seconds": 901}),
            "bad run id": lambda frame: frame.update({"run_id": "A" * 64}),
            "bad size": lambda frame: frame.update({"installer_size": 0}),
            "bad digest": lambda frame: frame.update({"installer_sha256": "A" * 64}),
            "foreign controller": lambda frame: frame.update({"controller_pid": 999}),
            "foreign supervisor": lambda frame: frame.update({"supervisor_pid": 999}),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    frame, activation = self.activation_fixture(
                        Path(temporary_directory).resolve()
                    )
                    mutate(frame)
                    with self.assertRaises(supervisor.SupervisorError):
                        self.parse_activation_fixture(frame, activation)

    def test_activation_rejects_symlinks_tampering_and_non_java17_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            frame, activation = self.activation_fixture(root)
            installer_path = Path(str(frame["installer_path"]))
            installer_path.write_bytes(b"tampered")
            with self.assertRaisesRegex(supervisor.SupervisorError, "bytes"):
                self.parse_activation_fixture(frame, activation)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            frame, activation = self.activation_fixture(root)
            java_path = Path(str(frame["java_path"]))
            (java_path.parent.parent / "release").write_text(
                'JAVA_VERSION="21.0.4"\n',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(supervisor.SupervisorError, "Java 17"):
                self.parse_activation_fixture(frame, activation)

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            frame, activation = self.activation_fixture(root)
            java_path = Path(str(frame["java_path"]))
            linked_java = root / "linked-java"
            linked_java.symlink_to(java_path)
            frame["java_path"] = str(linked_java)
            with self.assertRaisesRegex(supervisor.SupervisorError, "linked"):
                self.parse_activation_fixture(frame, activation)

    def test_supervisor_identity_requires_pid_pgid_sid_and_native_binding(self) -> None:
        sampler = mock.Mock()
        target = self.target(pid=123, executable=str(Path(sys.executable).resolve()))
        sampler.bind.return_value = target
        with (
            mock.patch.object(supervisor.os, "getpid", return_value=123),
            mock.patch.object(supervisor.os, "getpgrp", return_value=123),
            mock.patch.object(supervisor.os, "getsid", return_value=123),
        ):
            actual, session_id = supervisor.bind_supervisor_identity(sampler)

        self.assertEqual(target, actual)
        self.assertEqual(123, session_id)
        with (
            mock.patch.object(supervisor.os, "getpid", return_value=123),
            mock.patch.object(supervisor.os, "getpgrp", return_value=124),
            mock.patch.object(supervisor.os, "getsid", return_value=123),
            self.assertRaisesRegex(supervisor.SupervisorError, "leader"),
        ):
            supervisor.bind_supervisor_identity(sampler)

    def test_start_constructs_internal_argv_and_attaches_guard_before_handoff(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            supervisor_target = self.target(pid=123, executable="/test/python3")
            java_target = self.target(pid=456, executable=str(activation.java_path))
            java_process = mock.Mock(pid=java_target.pid)
            java_process.poll.return_value = None
            monitor_process = mock.Mock(pid=789)
            monitor_process.poll.return_value = None
            runtime = activation.runtime_directory
            installer_log = mock.Mock(spec=supervisor.BoundedLog)
            monitor_log = mock.Mock(spec=supervisor.BoundedLog)
            installer_writer = mock.Mock()
            monitor_writer = mock.Mock()
            monitor = macos_guarded_java.GuardedJavaMonitor(
                process=monitor_process,
                target=java_target,
                telemetry_path=runtime / "memory-guard-telemetry.json",
                readiness_path=runtime / ".memory-guard-ready.json",
                group_anchor=supervisor_target,
            )
            monitor_target = macos_guarded_java.OwnedJavaProcess(
                pid=789,
                process_group_id=789,
                proc_start_abstime=987655110,
                expected_executable=str(Path(sys.executable).resolve()),
            )
            sampler = mock.Mock()
            ownership = supervisor.PartialInstallerOwnership()
            with (
                mock.patch.dict(supervisor.os.environ, {"PATH": "/usr/bin"}, clear=True),
                mock.patch.object(
                    supervisor,
                    "create_pipe_log",
                    side_effect=(
                        (installer_log, installer_writer),
                        (monitor_log, monitor_writer),
                    ),
                ),
                mock.patch.object(
                    supervisor.subprocess,
                    "Popen",
                    return_value=java_process,
                ) as popen,
                mock.patch.object(
                    supervisor,
                    "bind_process",
                    side_effect=(java_target, monitor_target),
                ) as bind,
                mock.patch.object(supervisor.os, "getsid", side_effect=(123, 789)),
                mock.patch.object(supervisor.signal, "signal") as set_signal,
                mock.patch.object(
                    supervisor,
                    "start_guarded_java_monitor",
                    return_value=monitor,
                ) as start_monitor,
            ):
                launch = supervisor.start_installer(
                    activation,
                    supervisor_target,
                    123,
                    sampler,
                    ownership,
                )

            popen.assert_called_once_with(
                supervisor.build_installer_command(activation),
                cwd=activation.launcher_root,
                env={"PATH": "/usr/bin"},
                stdin=subprocess.DEVNULL,
                stdout=installer_writer,
                stderr=subprocess.STDOUT,
                start_new_session=False,
                close_fds=True,
            )
            self.assertEqual(
                2,
                bind.call_count,
            )
            bind.assert_any_call(
                java_process,
                supervisor_target.process_group_id,
                activation.java_path,
                sampler,
            )
            set_signal.assert_called_once_with(
                signal.SIGTERM,
                supervisor.retain_anchor_during_group_term,
            )
            monitor_call = start_monitor.call_args
            self.assertEqual((java_target, 1024, runtime, monitor_writer), monitor_call.args)
            self.assertEqual(supervisor_target, monitor_call.kwargs["group_anchor"])
            self.assertTrue(callable(monitor_call.kwargs["process_started"]))
            self.assertIs(monitor, launch.monitor)

    def test_injected_java_options_fail_before_run_directory_or_spawn(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            _frame, activation = self.activation_fixture(
                Path(temporary_directory).resolve()
            )
            for variable_name in macos_guarded_java.JAVA_OPTION_ENVIRONMENT_VARIABLES:
                with self.subTest(variable_name=variable_name):
                    with (
                        mock.patch.dict(
                            supervisor.os.environ,
                            {variable_name: "-Xmx20G"},
                            clear=True,
                        ),
                        mock.patch.object(supervisor.subprocess, "Popen") as popen,
                        self.assertRaisesRegex(
                            supervisor.SupervisorError,
                            variable_name,
                        ),
                    ):
                        supervisor.start_installer(
                            activation,
                            self.target(pid=123),
                            123,
                            mock.Mock(),
                            supervisor.PartialInstallerOwnership(),
                        )

                    popen.assert_not_called()

    def test_partial_guard_is_stopped_when_handoff_cannot_complete(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            supervisor_target = self.target(pid=123, executable="/test/python3")
            java_target = self.target(pid=456, executable=str(activation.java_path))
            java_process = mock.Mock(pid=java_target.pid)
            java_process.poll.return_value = 1
            monitor_process = mock.Mock(pid=789)
            monitor_process.poll.return_value = None
            runtime = activation.runtime_directory
            monitor = macos_guarded_java.GuardedJavaMonitor(
                process=monitor_process,
                target=java_target,
                telemetry_path=runtime / "memory-guard-telemetry.json",
                readiness_path=runtime / ".memory-guard-ready.json",
                group_anchor=supervisor_target,
            )
            monitor_target = macos_guarded_java.OwnedJavaProcess(
                pid=789,
                process_group_id=789,
                proc_start_abstime=987655110,
                expected_executable=str(Path(sys.executable).resolve()),
            )
            pipe_logs = (
                (mock.Mock(spec=supervisor.BoundedLog), mock.Mock()),
                (mock.Mock(spec=supervisor.BoundedLog), mock.Mock()),
            )
            with (
                mock.patch.dict(supervisor.os.environ, {}, clear=True),
                mock.patch.object(
                    supervisor,
                    "create_pipe_log",
                    side_effect=pipe_logs,
                ),
                mock.patch.object(
                    supervisor.subprocess,
                    "Popen",
                    return_value=java_process,
                ),
                mock.patch.object(
                    supervisor,
                    "bind_process",
                    side_effect=(java_target, monitor_target),
                ),
                mock.patch.object(supervisor.os, "getsid", side_effect=(123, 789)),
                mock.patch.object(supervisor.signal, "signal"),
                mock.patch.object(
                    supervisor,
                    "start_guarded_java_monitor",
                    return_value=monitor,
                ),
                self.assertRaisesRegex(supervisor.SupervisorError, "before handoff"),
            ):
                ownership = supervisor.PartialInstallerOwnership()
                supervisor.start_installer(
                    activation,
                    supervisor_target,
                    123,
                    mock.Mock(),
                    ownership,
                )

            self.assertIs(java_process, ownership.java_process)
            self.assertIs(monitor, ownership.monitor)

    def test_handoff_and_armed_are_bound_to_exact_identity_and_digest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            launch = self.launch_fixture(root, activation)
            handoff = supervisor.handoff_frame(launch)
            digest = supervisor.frame_sha256(handoff)
            armed = {
                "schema": 1,
                "action": "ARMED",
                "run_id": activation.run_id,
                "handoff_sha256": digest,
            }
            control = mock.Mock()
            control.receive.return_value = armed
            with (
                mock.patch.object(supervisor, "drain_logs"),
                mock.patch.object(supervisor, "verify_live_guard"),
                mock.patch.object(supervisor.time, "monotonic", return_value=1.1),
            ):
                lease_state = supervisor.wait_for_armed(control, launch, digest)

            self.assertEqual(1.1, lease_state.last_lease_at)
            self.assertEqual(launch.monitor.process.pid, handoff["monitor_pid"])
            self.assertEqual(
                macos_guarded_java.memory_policy_payload(1024),
                handoff["memory_policy"],
            )
            armed["handoff_sha256"] = "0" * 64
            with (
                mock.patch.object(supervisor, "drain_logs"),
                mock.patch.object(supervisor, "verify_live_guard"),
                mock.patch.object(supervisor.time, "monotonic", return_value=2.1),
                self.assertRaisesRegex(supervisor.SupervisorError, "exact handoff"),
            ):
                supervisor.wait_for_armed(control, launch, digest)

    def test_running_loop_reaps_exact_signed_returncode(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            launch = self.launch_fixture(root, activation)
            launch.java_process.poll.return_value = -9
            launch.java_process.wait.return_value = -9
            control = mock.Mock()
            control.frames = deque()
            control.eof = False
            with (
                mock.patch.object(supervisor, "drain_logs"),
                mock.patch.object(supervisor, "verify_live_guard"),
                mock.patch.object(supervisor.time, "monotonic", return_value=10.1),
            ):
                returncode = supervisor.poll_running_installer(
                    control,
                    launch,
                    supervisor.ControlLease(activation.run_id, 10.0),
                )

        self.assertEqual(-9, returncode)
        launch.java_process.wait.assert_called_once_with(timeout=0)

    def test_running_loop_rejects_replayed_lease_and_lease_expiry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            launch = self.launch_fixture(root, activation)
            launch.java_process.poll.return_value = None
            launch.monitor.process.poll.return_value = None
            lease = {
                "schema": 1,
                "action": "LEASE",
                "run_id": activation.run_id,
                "sequence": 1,
            }
            control = mock.Mock()
            control.frames = deque((lease, dict(lease)))
            control.eof = False
            with (
                mock.patch.object(supervisor, "drain_logs"),
                mock.patch.object(supervisor, "verify_live_guard"),
                mock.patch.object(supervisor.time, "monotonic", return_value=10.1),
                self.assertRaisesRegex(supervisor.SupervisorError, "strictly increasing"),
            ):
                supervisor.poll_running_installer(
                    control,
                    launch,
                    supervisor.ControlLease(activation.run_id, 10.0),
                )

            control.frames = deque()
            with (
                mock.patch.object(supervisor, "drain_logs"),
                mock.patch.object(supervisor, "verify_live_guard"),
                mock.patch.object(supervisor.time, "monotonic", return_value=14.0),
                self.assertRaisesRegex(supervisor.SupervisorError, "lease expired"),
            ):
                supervisor.poll_running_installer(
                    control,
                    launch,
                    supervisor.ControlLease(activation.run_id, 10.0),
                )

    def test_eof_stop_and_install_timeout_are_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            launch = self.launch_fixture(root, activation)
            launch.java_process.poll.return_value = None
            launch.monitor.process.poll.return_value = None
            control = mock.Mock()
            control.frames = deque()
            control.eof = True
            with (
                mock.patch.object(supervisor, "verify_live_guard"),
                self.assertRaises(supervisor.ControlEof),
            ):
                supervisor.poll_running_installer(
                    control,
                    launch,
                    supervisor.ControlLease(activation.run_id, 10.0),
                )

            control.eof = False
            control.frames = deque(
                (
                    {
                        "schema": 1,
                        "action": "STOP",
                        "run_id": activation.run_id,
                    },
                )
            )
            with (
                mock.patch.object(supervisor, "verify_live_guard"),
                mock.patch.object(supervisor.time, "monotonic", return_value=10.1),
                self.assertRaisesRegex(supervisor.SupervisorError, "requested"),
            ):
                supervisor.poll_running_installer(
                    control,
                    launch,
                    supervisor.ControlLease(activation.run_id, 10.0),
                )

            control.frames = deque()
            with (
                mock.patch.object(supervisor, "drain_logs"),
                mock.patch.object(supervisor, "verify_live_guard"),
                mock.patch.object(supervisor.time, "monotonic", return_value=911.0),
                self.assertRaisesRegex(supervisor.SupervisorError, "900 seconds"),
            ):
                supervisor.poll_running_installer(
                    control,
                    launch,
                    supervisor.ControlLease(activation.run_id, 910.0),
                )

    def test_terminal_monitor_requires_natural_missing_sample(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            launch = self.launch_fixture(root, activation)
            launch.monitor.telemetry_path.write_text(
                json.dumps(self.terminal_payload(launch)),
                encoding="utf-8",
            )
            launch.monitor.telemetry_path.chmod(0o600)

            supervisor.verify_natural_terminal_telemetry(launch)

            payload = self.terminal_payload(launch)
            payload["records"][-1]["status"] = "identity-drift"  # type: ignore[index]
            launch.monitor.telemetry_path.write_text(
                json.dumps(payload),
                encoding="utf-8",
            )
            launch.monitor.telemetry_path.chmod(0o600)
            with self.assertRaisesRegex(supervisor.SupervisorError, "natural terminal"):
                supervisor.verify_natural_terminal_telemetry(launch)

    def test_terminal_monitor_rejects_oversized_and_linked_telemetry(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            launch = self.launch_fixture(root, activation)
            launch.monitor.telemetry_path.write_bytes(
                b"x" * (supervisor.MAXIMUM_TELEMETRY_SIZE_BYTES + 1)
            )
            launch.monitor.telemetry_path.chmod(0o600)
            with self.assertRaisesRegex(supervisor.SupervisorError, "bound"):
                supervisor.verify_natural_terminal_telemetry(launch)

            launch.monitor.telemetry_path.unlink()
            target = root / "linked-telemetry-target.json"
            target.write_text("{}", encoding="utf-8")
            target.chmod(0o600)
            launch.monitor.telemetry_path.symlink_to(target)
            with self.assertRaisesRegex(supervisor.SupervisorError, "Cannot open"):
                supervisor.verify_natural_terminal_telemetry(launch)

    def test_bounded_log_retains_and_persists_only_the_newest_tail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            content = b"discarded-prefix" + b"z" * supervisor.MAXIMUM_OUTPUT_TAIL_SIZE
            source = root / "source-output.bin"
            source.write_bytes(content)
            stream = source.open("rb", buffering=0)
            path = root / supervisor.INSTALLER_LOG_NAME
            bounded_log = supervisor.BoundedLog(stream, path, bytearray())
            try:
                bounded_log.drain()
            finally:
                bounded_log.close()

            expected = content[-supervisor.MAXIMUM_OUTPUT_TAIL_SIZE :]
            self.assertEqual(expected, bytes(bounded_log.tail))
            self.assertEqual(expected, path.read_bytes())
            self.assertEqual(0o600, path.stat().st_mode & 0o777)

    def test_completion_and_final_ack_bind_reaped_identity_and_output_tail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            launch = self.launch_fixture(root, activation)
            java_exited = supervisor.java_exited_frame(launch, -9)
            completion = supervisor.completion_frame(launch, -9)
            digest = supervisor.frame_sha256(completion)
            self.assertEqual("JAVA_EXITED", java_exited["action"])
            self.assertEqual(-9, java_exited["returncode"])
            self.assertEqual(
                "java-reaped-monitor-terminal-pending",
                java_exited["cleanup_disposition"],
            )
            self.assertEqual(
                int(supervisor.MONITOR_TERMINAL_TIMEOUT_SECONDS),
                java_exited["monitor_terminal_timeout_seconds"],
            )
            self.assertIs(completion["reaped"], True)
            self.assertEqual(-9, completion["returncode"])
            self.assertEqual(len(launch.installer_log.tail), completion["output_tail_length"])
            self.assertEqual(
                hashlib.sha256(bytes(launch.installer_log.tail)).hexdigest(),
                completion["output_tail_sha256"],
            )
            control = mock.Mock()
            control.receive.side_effect = (
                {
                    "schema": 1,
                    "action": "LEASE",
                    "run_id": activation.run_id,
                    "sequence": 1,
                },
                {
                    "schema": 1,
                    "action": "FINAL_ACK",
                    "run_id": activation.run_id,
                    "completion_sha256": digest,
                },
            )
            with mock.patch.object(supervisor.time, "monotonic", return_value=1.0):
                supervisor.wait_for_final_ack(
                    control,
                    launch,
                    digest,
                    supervisor.ControlLease(activation.run_id, 1.0),
                )

            control.receive.side_effect = None
            control.receive.return_value = {
                "schema": 1,
                "action": "FINAL_ACK",
                "run_id": activation.run_id,
                "completion_sha256": "0" * 64,
            }
            with (
                mock.patch.object(supervisor.time, "monotonic", return_value=1.0),
                self.assertRaisesRegex(supervisor.SupervisorError, "exact completion"),
            ):
                supervisor.wait_for_final_ack(
                    control,
                    launch,
                    digest,
                    supervisor.ControlLease(activation.run_id, 1.0),
                )

    def test_abort_reports_monitor_cleanup_failure_before_anchor_group_kill(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            _frame, activation = self.activation_fixture(root)
            launch = self.launch_fixture(root, activation)
            ownership = supervisor.PartialInstallerOwnership(
                java_process=launch.java_process,
                java_target=launch.java_target,
                monitor=launch.monitor,
                monitor_process=launch.monitor.process,
                monitor_process_target=launch.monitor_process_target,
                installer_log=launch.installer_log,
                monitor_log=launch.monitor_log,
            )
            control = mock.Mock()
            events: list[str] = []
            control.send.side_effect = lambda frame: events.append(
                f"send:{frame['detail']}"
            )
            with (
                mock.patch.object(
                    supervisor,
                    "stop_guarded_java_monitor",
                    side_effect=macos_guarded_java.GuardedJavaError("monitor stuck"),
                ),
                mock.patch.object(
                    supervisor,
                    "close_partial_ownership",
                    side_effect=lambda _ownership: events.append("close"),
                ),
                mock.patch.object(supervisor.os, "getpgrp", return_value=123),
                mock.patch.object(supervisor.os, "killpg") as terminate_group,
                mock.patch.object(
                    supervisor,
                    "kill_anchor_group",
                    side_effect=lambda: (events.append("kill"), (_ for _ in ()).throw(AnchorKilled()))[1],
                ),
                self.assertRaises(AnchorKilled),
            ):
                supervisor.abort_anchor(
                    control,
                    activation.run_id,
                    supervisor.SupervisorError("lease-expired", "lease expired"),
                    ownership,
                )

        terminate_group.assert_called_once_with(123, signal.SIGTERM)
        self.assertIn("monitor cleanup failed", events[0])
        self.assertEqual("close", events[1])
        self.assertEqual("kill", events[2])

    def test_full_supervisor_sequence_never_accepts_controller_argv(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            activation_frame, activation = self.activation_fixture(root)
            launch = self.launch_fixture(root, activation)
            supervisor_target = launch.supervisor_target
            control = mock.Mock()
            control.receive.return_value = activation_frame
            events: list[str] = []
            with (
                mock.patch.object(supervisor, "FramedControl", return_value=control),
                mock.patch.object(
                    supervisor.MacOsProcessMemorySampler,
                    "native",
                    return_value=launch.sampler,
                ),
                mock.patch.object(
                    supervisor,
                    "bind_supervisor_identity",
                    return_value=(supervisor_target, 123),
                ),
                mock.patch.object(
                    supervisor,
                    "parse_activation",
                    return_value=activation,
                ),
                mock.patch.object(
                    supervisor,
                    "start_installer",
                    return_value=launch,
                ),
                mock.patch.object(
                    supervisor,
                    "wait_for_armed",
                    side_effect=lambda *args: (
                        events.append("armed"),
                        supervisor.ControlLease(activation.run_id, 1.0),
                    )[1],
                ),
                mock.patch.object(
                    supervisor,
                    "poll_running_installer",
                    side_effect=lambda *args: (events.append("reaped"), 0)[1],
                ),
                mock.patch.object(
                    supervisor,
                    "wait_for_monitor_terminal",
                    side_effect=lambda *args: events.append("terminal"),
                ),
                mock.patch.object(
                    supervisor,
                    "wait_for_final_ack",
                    side_effect=lambda *args: events.append("ack"),
                ),
                mock.patch.object(
                    supervisor,
                    "freeze_launch_logs",
                    side_effect=lambda *args: events.append("freeze"),
                ),
                mock.patch.object(
                    supervisor,
                    "close_launch_logs",
                    side_effect=lambda *args: events.append("close"),
                ),
                mock.patch.object(
                    supervisor,
                    "kill_anchor_group",
                    side_effect=AnchorKilled,
                ),
                self.assertRaises(AnchorKilled),
            ):
                supervisor.supervise(mock.Mock(spec=socket.socket))

        self.assertEqual(
            ["armed", "reaped", "terminal", "freeze", "ack", "close"],
            events,
        )
        sent_actions = [call.args[0]["action"] for call in control.send.call_args_list]
        self.assertEqual(["HANDOFF", "JAVA_EXITED", "COMPLETION"], sent_actions)

    def test_cli_accepts_only_one_control_fd(self) -> None:
        self.assertEqual(7, supervisor.parse_arguments(["--control-fd", "7"]).control_fd)
        with (
            mock.patch("sys.stderr", new=io.StringIO()),
            self.assertRaises(SystemExit),
        ):
            supervisor.parse_arguments(["--control-fd", "7", "--", "java", "-jar"])


if __name__ == "__main__":
    unittest.main()
