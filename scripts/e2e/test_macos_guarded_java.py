from __future__ import annotations

import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import macos_guarded_java
import macos_memory_guard as guard_module


TARGET = macos_guarded_java.OwnedJavaProcess(
    pid=41000,
    process_group_id=41000,
    proc_start_abstime=123456789,
    expected_executable="/test/java",
)


class FakeProcess:
    def __init__(self, pid: int) -> None:
        self.pid = pid
        self.returncode: int | None = None
        self.terminate_count = 0
        self.kill_count = 0
        self.wait_count = 0

    def poll(self) -> int | None:
        return self.returncode

    def terminate(self) -> None:
        self.terminate_count += 1

    def kill(self) -> None:
        self.kill_count += 1

    def wait(self, timeout: float | None = None) -> int:
        del timeout
        self.wait_count += 1
        self.returncode = 0
        return 0


def write_guard_artifacts(
    runtime: Path,
    target: macos_guarded_java.OwnedJavaProcess = TARGET,
    monitor_pid: int = 42000,
) -> tuple[Path, Path]:
    telemetry_path, readiness_path = macos_guarded_java.guard_runtime_paths(runtime)
    target_payload = {
        "pid": target.pid,
        "process_group_id": target.process_group_id,
        "proc_start_abstime": target.proc_start_abstime,
        "expected_executable": target.expected_executable,
    }
    telemetry_path.write_text(
        json.dumps({"schema": 1, "target": target_payload}),
        encoding="utf-8",
    )
    readiness_path.write_text(
        json.dumps(
            {
                "schema": 1,
                "status": "ready",
                "monitor_pid": monitor_pid,
                "target": target_payload,
                "telemetry": str(telemetry_path),
            }
        ),
        encoding="utf-8",
    )
    return telemetry_path, readiness_path


class LaunchContractTests(unittest.TestCase):
    def test_exact_launch_contract_is_accepted(self) -> None:
        macos_guarded_java.verify_java_launch_contract(
            ["/test/java", "-Xmx4096M", "Main"],
            Path("/test/java"),
            4096,
            {},
        )

    def test_each_inherited_java_option_variable_is_rejected_even_when_empty(
        self,
    ) -> None:
        for variable_name in macos_guarded_java.JAVA_OPTION_ENVIRONMENT_VARIABLES:
            with self.subTest(variable_name=variable_name):
                with self.assertRaisesRegex(
                    macos_guarded_java.GuardedJavaError,
                    variable_name,
                ):
                    macos_guarded_java.verify_java_option_environment(
                        {variable_name: ""}
                    )

    def test_heap_argument_must_be_one_literal_xmx4096m(self) -> None:
        for heap_arguments in (
            (),
            ("-Xmx4G",),
            ("-Xmx2048M",),
            ("-Xmx4096M", "-Xmx4096M"),
        ):
            with self.subTest(heap_arguments=heap_arguments):
                command = ["/test/java", *heap_arguments, "Main"]
                with self.assertRaisesRegex(
                    macos_guarded_java.GuardedJavaError,
                    "exactly one -Xmx4096M",
                ):
                    macos_guarded_java.verify_java_launch_contract(
                        command,
                        Path("/test/java"),
                        4096,
                        {},
                    )

    def test_manifest_heap_value_must_be_exact(self) -> None:
        with self.assertRaisesRegex(
            macos_guarded_java.GuardedJavaError,
            "exactly 4096 MiB",
        ):
            macos_guarded_java.verify_java_launch_contract(
                ["/test/java", "-Xmx4096M", "Main"],
                Path("/test/java"),
                8192,
                {},
            )

    def test_command_must_start_with_resolved_java(self) -> None:
        with self.assertRaisesRegex(
            macos_guarded_java.GuardedJavaError,
            "resolved Java executable",
        ):
            macos_guarded_java.verify_java_launch_contract(
                ["/other/java", "-Xmx4096M", "Main"],
                Path("/test/java"),
                4096,
                {},
            )

    def test_generic_heap_contract_accepts_exact_server_spelling(self) -> None:
        macos_guarded_java.verify_exact_java_heap_arguments(
            ["-Dprobe=true", "-Xmx2048m"],
            2048,
            "-Xmx2048m",
        )

    def test_generic_heap_contract_rejects_duplicate_or_normalized_spelling(
        self,
    ) -> None:
        for arguments in (
            ["-Xmx2048M"],
            ["-Xmx2G"],
            ["-Xmx2048m", "-Xmx2048m"],
            ["-Xmx4096M", "-Xmx2048m"],
        ):
            with self.subTest(arguments=arguments), self.assertRaisesRegex(
                macos_guarded_java.GuardedJavaError,
                "exactly one -Xmx2048m",
            ):
                macos_guarded_java.verify_exact_java_heap_arguments(
                    arguments,
                    2048,
                    "-Xmx2048m",
                )

    def test_server_policy_changes_only_the_declared_heap_boundary(self) -> None:
        policy = macos_guarded_java.memory_policy_for_maximum_heap(2048)

        self.assertEqual(2048 * 1024 * 1024, policy.heap_limit_bytes)
        self.assertEqual(
            macos_guarded_java.FOUR_GIB_CLIENT_MEMORY_POLICY.warning_phys_footprint_bytes,
            policy.warning_phys_footprint_bytes,
        )
        self.assertEqual(
            macos_guarded_java.FOUR_GIB_CLIENT_MEMORY_POLICY.hard_phys_footprint_bytes,
            policy.hard_phys_footprint_bytes,
        )
        self.assertEqual(
            macos_guarded_java.FOUR_GIB_CLIENT_MEMORY_POLICY.emergency_phys_footprint_bytes,
            policy.emergency_phys_footprint_bytes,
        )


class StateArtifactTests(unittest.TestCase):
    def test_exact_state_and_runtime_artifacts_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            telemetry_path, readiness_path = write_guard_artifacts(runtime)
            state = {
                "pid": TARGET.pid,
                "process_group_id": TARGET.process_group_id,
                "proc_start_abstime": TARGET.proc_start_abstime,
                "expected_executable": TARGET.expected_executable,
                "memory_guard_pid": 42000,
                "memory_guard_telemetry": str(telemetry_path),
                "memory_guard_readiness": str(readiness_path),
            }

            self.assertEqual(
                TARGET,
                macos_guarded_java.owned_java_process_from_state(state),
            )
            self.assertEqual(
                (telemetry_path, readiness_path),
                macos_guarded_java.verify_guard_state_paths(state, runtime),
            )

    def test_state_identity_rejects_coerced_integer_fields(self) -> None:
        state: dict[str, object] = {
            "pid": TARGET.pid,
            "process_group_id": TARGET.process_group_id,
            "proc_start_abstime": TARGET.proc_start_abstime,
            "expected_executable": TARGET.expected_executable,
        }
        for field_name, invalid_value in (
            ("pid", True),
            ("pid", str(TARGET.pid)),
            ("process_group_id", True),
            ("process_group_id", str(TARGET.process_group_id)),
            ("proc_start_abstime", True),
            ("proc_start_abstime", str(TARGET.proc_start_abstime)),
        ):
            with self.subTest(field_name=field_name, invalid_value=invalid_value):
                changed_state = dict(state)
                changed_state[field_name] = invalid_value
                with self.assertRaisesRegex(
                    macos_guarded_java.GuardedJavaError,
                    "no valid guarded Java identity",
                ):
                    macos_guarded_java.owned_java_process_from_state(changed_state)

    def test_guard_artifacts_must_exist_at_fixed_runtime_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            telemetry_path, readiness_path = write_guard_artifacts(runtime)
            state = {
                "pid": TARGET.pid,
                "process_group_id": TARGET.process_group_id,
                "proc_start_abstime": TARGET.proc_start_abstime,
                "expected_executable": TARGET.expected_executable,
                "memory_guard_pid": 42000,
                "memory_guard_telemetry": str(telemetry_path),
                "memory_guard_readiness": str(readiness_path),
            }
            readiness_path.unlink()

            with self.assertRaisesRegex(
                macos_guarded_java.GuardedJavaError,
                "missing or linked",
            ):
                macos_guarded_java.verify_guard_state_paths(state, runtime)

    def test_guard_artifact_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            runtime = root / "runtime"
            runtime.mkdir()
            telemetry_path, readiness_path = write_guard_artifacts(runtime)
            readiness_path.unlink()
            target = root / "foreign-readiness.json"
            target.write_text("{}", encoding="utf-8")
            readiness_path.symlink_to(target)
            state = {
                "pid": TARGET.pid,
                "process_group_id": TARGET.process_group_id,
                "proc_start_abstime": TARGET.proc_start_abstime,
                "expected_executable": TARGET.expected_executable,
                "memory_guard_pid": 42000,
                "memory_guard_telemetry": str(telemetry_path),
                "memory_guard_readiness": str(readiness_path),
            }

            with self.assertRaisesRegex(
                macos_guarded_java.GuardedJavaError,
                "missing or linked",
            ):
                macos_guarded_java.verify_guard_state_paths(state, runtime)

    def test_telemetry_target_must_match_controller_state(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            telemetry_path, readiness_path = write_guard_artifacts(runtime)
            telemetry_path.write_text(
                json.dumps({"schema": 1, "target": {}}),
                encoding="utf-8",
            )
            state = {
                "pid": TARGET.pid,
                "process_group_id": TARGET.process_group_id,
                "proc_start_abstime": TARGET.proc_start_abstime,
                "expected_executable": TARGET.expected_executable,
                "memory_guard_pid": 42000,
                "memory_guard_telemetry": str(telemetry_path),
                "memory_guard_readiness": str(readiness_path),
            }

            with self.assertRaisesRegex(
                macos_guarded_java.GuardedJavaError,
                "telemetry does not match",
            ):
                macos_guarded_java.verify_guard_state_paths(state, runtime)

    def test_guard_health_requires_a_current_authoritative_exact_sample(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            telemetry_path, readiness_path = write_guard_artifacts(runtime)
            observed_at = 10_000_000_000
            telemetry_path.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "target": {
                            "pid": TARGET.pid,
                            "process_group_id": TARGET.process_group_id,
                            "proc_start_abstime": TARGET.proc_start_abstime,
                            "expected_executable": TARGET.expected_executable,
                        },
                        "policy": macos_guarded_java.memory_policy_payload(2048),
                        "state": {"enforcement_disarmed": False},
                        "records": [
                            {
                                "observed_at_monotonic_ns": observed_at,
                                "source": "proc-pid-rusage-v4",
                                "status": "available",
                                "identity_matches_target": True,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            state = {
                "pid": TARGET.pid,
                "process_group_id": TARGET.process_group_id,
                "proc_start_abstime": TARGET.proc_start_abstime,
                "expected_executable": TARGET.expected_executable,
                "memory_guard_telemetry": str(telemetry_path),
                "memory_guard_readiness": str(readiness_path),
                "memory_guard_maximum_memory_mb": 2048,
            }

            with mock.patch.object(
                macos_guarded_java.time,
                "monotonic_ns",
                return_value=observed_at + 1_000_000_000,
            ):
                self.assertTrue(
                    macos_guarded_java.memory_guard_is_enforcing(state)
                )

    def test_guard_health_rejects_a_wrong_serialized_heap_policy(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            telemetry_path, _readiness_path = write_guard_artifacts(runtime)
            observed_at = 10_000_000_000
            telemetry_path.write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "target": {
                            "pid": TARGET.pid,
                            "process_group_id": TARGET.process_group_id,
                            "proc_start_abstime": TARGET.proc_start_abstime,
                            "expected_executable": TARGET.expected_executable,
                        },
                        "policy": macos_guarded_java.memory_policy_payload(4096),
                        "state": {"enforcement_disarmed": False},
                        "records": [
                            {
                                "observed_at_monotonic_ns": observed_at,
                                "source": "proc-pid-rusage-v4",
                                "status": "available",
                                "identity_matches_target": True,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            state = {
                "pid": TARGET.pid,
                "process_group_id": TARGET.process_group_id,
                "proc_start_abstime": TARGET.proc_start_abstime,
                "expected_executable": TARGET.expected_executable,
                "memory_guard_telemetry": str(telemetry_path),
                "memory_guard_maximum_memory_mb": 2048,
            }

            with mock.patch.object(
                macos_guarded_java.time,
                "monotonic_ns",
                return_value=observed_at + 1_000_000_000,
            ):
                self.assertFalse(
                    macos_guarded_java.memory_guard_is_enforcing(state)
                )

    def test_guard_health_rejects_error_drift_fallback_and_stale_samples(self) -> None:
        for source, status, identity_matches, age in (
            ("proc-pid-rusage-v4", "error", None, 1_000_000_000),
            ("proc-pid-rusage-v4", "identity-drift", False, 1_000_000_000),
            ("fallback", "available", True, 1_000_000_000),
            ("proc-pid-rusage-v4", "available", True, 4_000_000_000),
        ):
            with self.subTest(source=source, status=status, age=age):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    runtime = Path(temporary_directory).resolve()
                    telemetry_path, _readiness_path = write_guard_artifacts(runtime)
                    observed_at = 10_000_000_000
                    telemetry_path.write_text(
                        json.dumps(
                            {
                                "schema": 1,
                                "target": {
                                    "pid": TARGET.pid,
                                    "process_group_id": TARGET.process_group_id,
                                    "proc_start_abstime": TARGET.proc_start_abstime,
                                    "expected_executable": TARGET.expected_executable,
                                },
                                "state": {"enforcement_disarmed": False},
                                "records": [
                                    {
                                        "observed_at_monotonic_ns": observed_at,
                                        "source": source,
                                        "status": status,
                                        "identity_matches_target": identity_matches,
                                    }
                                ],
                            }
                        ),
                        encoding="utf-8",
                    )
                    with mock.patch.object(
                        macos_guarded_java.time,
                        "monotonic_ns",
                        return_value=observed_at + age,
                    ):
                        self.assertFalse(
                            macos_guarded_java.memory_guard_is_enforcing(
                                {
                                    "pid": TARGET.pid,
                                    "process_group_id": TARGET.process_group_id,
                                    "proc_start_abstime": TARGET.proc_start_abstime,
                                    "expected_executable": (
                                        TARGET.expected_executable
                                    ),
                                    "memory_guard_telemetry": str(telemetry_path),
                                }
                            )
                        )


class LaunchOrchestrationTests(unittest.TestCase):
    def test_native_sampler_failure_occurs_before_java_spawn(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            caffeinate_path = root / "caffeinate"
            caffeinate_path.write_bytes(b"")
            with (
                mock.patch.object(
                    macos_guarded_java.MacOsProcessMemorySampler,
                    "native",
                    side_effect=macos_guarded_java.MemorySamplingUnavailable(
                        "unavailable"
                    ),
                ),
                mock.patch.object(
                    macos_guarded_java.subprocess,
                    "Popen",
                ) as popen,
                self.assertRaises(
                    macos_guarded_java.MemorySamplingUnavailable
                ),
            ):
                macos_guarded_java.start_guarded_java(
                    ["/test/java", "-Xmx4096M", "Main"],
                    Path("/test/java"),
                    4096,
                    {},
                    root,
                    root,
                    mock.Mock(),
                    caffeinate_path,
                )

            popen.assert_not_called()

    def test_java_is_direct_and_guard_is_ready_before_caffeinate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            caffeinate_path = root / "caffeinate"
            caffeinate_path.write_bytes(b"")
            java_process = FakeProcess(TARGET.pid)
            monitor_process = FakeProcess(42000)
            caffeinate_process = FakeProcess(43000)
            sampler = mock.Mock()
            sampler.bind.return_value = TARGET
            with (
                mock.patch.object(
                    macos_guarded_java.MacOsProcessMemorySampler,
                    "native",
                    return_value=sampler,
                ),
                mock.patch.object(
                    macos_guarded_java.subprocess,
                    "Popen",
                    side_effect=(java_process, monitor_process, caffeinate_process),
                ) as popen,
                mock.patch.object(
                    macos_guarded_java.os,
                    "getpgid",
                    return_value=TARGET.pid,
                ),
                mock.patch.object(
                    macos_guarded_java,
                    "_wait_for_monitor_readiness",
                ) as wait_for_readiness,
            ):
                launch = macos_guarded_java.start_guarded_java(
                    ["/test/java", "-Xmx4096M", "Main"],
                    Path("/test/java"),
                    4096,
                    {},
                    root,
                    root,
                    mock.Mock(),
                    caffeinate_path,
                )

            self.assertEqual(TARGET, launch.target)
            self.assertEqual(3, popen.call_count)
            self.assertEqual(
                ["/test/java", "-Xmx4096M", "Main"],
                popen.call_args_list[0].args[0],
            )
            self.assertTrue(popen.call_args_list[0].kwargs["start_new_session"])
            wait_for_readiness.assert_called_once()
            self.assertEqual(
                [str(caffeinate_path), "-dimsu", "-w", str(TARGET.pid)],
                popen.call_args_list[2].args[0],
            )

    def test_bound_server_monitor_receives_exact_heap_policy_and_identity(self) -> None:
        server_target = macos_guarded_java.OwnedJavaProcess(
            pid=41001,
            process_group_id=41000,
            proc_start_abstime=TARGET.proc_start_abstime,
            expected_executable=TARGET.expected_executable,
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            monitor_process = FakeProcess(42000)
            with (
                mock.patch.object(
                    macos_guarded_java.subprocess,
                    "Popen",
                    return_value=monitor_process,
                ) as popen,
                mock.patch.object(
                    macos_guarded_java,
                    "_wait_for_monitor_readiness",
                ),
                mock.patch.object(macos_guarded_java.os, "getpgrp", return_value=1),
            ):
                monitor = macos_guarded_java.start_guarded_java_monitor(
                    server_target,
                    2048,
                    runtime,
                    mock.Mock(),
                )

            self.assertEqual(server_target, monitor.target)
            command = popen.call_args.args[0]
            maximum_memory_index = command.index("--maximum-memory-mb")
            self.assertEqual("2048", command[maximum_memory_index + 1])
            process_group_index = command.index("--process-group-id")
            self.assertEqual("41000", command[process_group_index + 1])
            self.assertTrue(popen.call_args.kwargs["start_new_session"])

    def test_monitor_refuses_a_target_in_the_controller_group_before_spawn(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            with (
                mock.patch.object(
                    macos_guarded_java.os,
                    "getpgrp",
                    return_value=TARGET.process_group_id,
                ),
                mock.patch.object(macos_guarded_java.subprocess, "Popen") as popen,
                self.assertRaisesRegex(
                    macos_guarded_java.GuardedJavaError,
                    "shares the controller",
                ),
            ):
                macos_guarded_java.start_guarded_java_monitor(
                    TARGET,
                    4096,
                    runtime,
                    mock.Mock(),
                )

            popen.assert_not_called()

    def test_monitor_accepts_shared_group_only_with_exact_live_anchor(self) -> None:
        anchored_target = macos_guarded_java.OwnedJavaProcess(
            pid=TARGET.pid + 1,
            process_group_id=TARGET.process_group_id,
            proc_start_abstime=TARGET.proc_start_abstime + 1,
            expected_executable=TARGET.expected_executable,
        )
        anchor = macos_guarded_java.OwnedJavaProcess(
            pid=TARGET.process_group_id,
            process_group_id=TARGET.process_group_id,
            proc_start_abstime=TARGET.proc_start_abstime - 1,
            expected_executable="/test/python3",
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            runtime = Path(temporary_directory).resolve()
            monitor_process = FakeProcess(42000)
            with (
                mock.patch.object(
                    macos_guarded_java.subprocess,
                    "Popen",
                    return_value=monitor_process,
                ) as popen,
                mock.patch.object(
                    macos_guarded_java,
                    "_wait_for_monitor_readiness",
                ),
                mock.patch.object(
                    macos_guarded_java.os,
                    "getpid",
                    return_value=anchor.pid,
                ),
                mock.patch.object(
                    macos_guarded_java.os,
                    "getpgrp",
                    return_value=anchor.process_group_id,
                ),
            ):
                monitor = macos_guarded_java.start_guarded_java_monitor(
                    anchored_target,
                    1024,
                    runtime,
                    mock.Mock(),
                    group_anchor=anchor,
                )

            self.assertEqual(anchor, monitor.group_anchor)
            command = popen.call_args.args[0]
            anchor_index = command.index("--group-anchor-pid")
            self.assertEqual(str(anchor.pid), command[anchor_index + 1])

    def test_anchored_memory_stop_is_term_for_hard_and_kill_for_emergency(self) -> None:
        anchored_target = macos_guarded_java.OwnedJavaProcess(
            pid=TARGET.pid + 1,
            process_group_id=TARGET.process_group_id,
            proc_start_abstime=TARGET.proc_start_abstime + 1,
            expected_executable=TARGET.expected_executable,
        )
        anchor = macos_guarded_java.OwnedJavaProcess(
            pid=TARGET.process_group_id,
            process_group_id=TARGET.process_group_id,
            proc_start_abstime=TARGET.proc_start_abstime - 1,
            expected_executable="/test/python3",
        )
        missing_sample = guard_module.MemorySample.missing(
            1,
            guard_module.SampleSource.PROC_PID_RUSAGE_V4,
            "gone",
        )
        hard_sampler = mock.Mock()
        hard_sampler.revalidate.side_effect = lambda target: target
        hard_sampler.sample.return_value = missing_sample
        with mock.patch.object(macos_guarded_java.os, "killpg") as kill_group:
            macos_guarded_java._stop_anchored_java_process(
                anchored_target,
                anchor,
                guard_module.MemoryDecision.HARD,
                hard_sampler,
            )
        kill_group.assert_called_once_with(
            anchored_target.process_group_id,
            signal.SIGTERM,
        )

        emergency_sampler = mock.Mock()
        emergency_sampler.revalidate.side_effect = (anchor, anchored_target, None)
        emergency_sampler.sample.return_value = missing_sample
        with (
            mock.patch.object(macos_guarded_java.os, "killpg") as kill_group,
            mock.patch.object(
                macos_guarded_java,
                "_process_group_exists",
                return_value=False,
            ),
        ):
            macos_guarded_java._stop_anchored_java_process(
                anchored_target,
                anchor,
                guard_module.MemoryDecision.EMERGENCY,
                emergency_sampler,
            )
        kill_group.assert_called_once_with(
            anchored_target.process_group_id,
            signal.SIGKILL,
        )

    def test_readiness_failure_stops_monitor_and_owned_java_before_return(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory).resolve()
            caffeinate_path = root / "caffeinate"
            caffeinate_path.write_bytes(b"")
            java_process = FakeProcess(TARGET.pid)
            monitor_process = FakeProcess(42000)
            sampler = mock.Mock()
            sampler.bind.return_value = TARGET
            with (
                mock.patch.object(
                    macos_guarded_java.MacOsProcessMemorySampler,
                    "native",
                    return_value=sampler,
                ),
                mock.patch.object(
                    macos_guarded_java.subprocess,
                    "Popen",
                    side_effect=(java_process, monitor_process),
                ),
                mock.patch.object(
                    macos_guarded_java.os,
                    "getpgid",
                    return_value=TARGET.pid,
                ),
                mock.patch.object(
                    macos_guarded_java,
                    "_wait_for_monitor_readiness",
                    side_effect=macos_guarded_java.GuardedJavaError(
                        "not ready"
                    ),
                ),
                mock.patch.object(
                    macos_guarded_java,
                    "_terminate_new_java_process_group",
                ) as terminate_java,
                self.assertRaisesRegex(
                    macos_guarded_java.GuardedJavaError,
                    "not ready",
                ),
            ):
                macos_guarded_java.start_guarded_java(
                    ["/test/java", "-Xmx4096M", "Main"],
                    Path("/test/java"),
                    4096,
                    {},
                    root,
                    root,
                    mock.Mock(),
                    caffeinate_path,
                )

            self.assertEqual(1, monitor_process.terminate_count)
            terminate_java.assert_called_once_with(java_process)

    def test_spawned_java_cleanup_escalates_only_its_confirmed_group(self) -> None:
        process = FakeProcess(TARGET.pid)
        process.wait = mock.Mock(
            side_effect=(subprocess.TimeoutExpired("wait", 1.0), 0)
        )
        with (
            mock.patch.object(
                macos_guarded_java.os,
                "getpgid",
                return_value=TARGET.pid,
            ),
            mock.patch.object(macos_guarded_java.os, "killpg") as kill_group,
        ):
            forced = macos_guarded_java._terminate_new_java_process_group(process)

        self.assertTrue(forced)
        self.assertEqual(
            [
                mock.call(TARGET.pid, signal.SIGTERM),
                mock.call(TARGET.pid, signal.SIGKILL),
            ],
            kill_group.call_args_list,
        )

    def test_auxiliary_cleanup_reports_an_unreaped_process_after_kill(self) -> None:
        process = FakeProcess(42000)
        process.wait = mock.Mock(
            side_effect=(
                subprocess.TimeoutExpired("wait", 2.0),
                subprocess.TimeoutExpired("wait", 2.0),
            )
        )

        with self.assertRaisesRegex(
            macos_guarded_java.GuardedJavaError,
            "remained live after SIGKILL",
        ):
            macos_guarded_java.stop_spawned_auxiliary(process)

        self.assertEqual(1, process.terminate_count)
        self.assertEqual(1, process.kill_count)
        self.assertEqual(2, process.wait.call_count)


class StopSafetyTests(unittest.TestCase):
    def test_identity_mismatch_never_signals_a_process_group(self) -> None:
        sampler = mock.Mock()
        sampler.revalidate.return_value = None
        with (
            mock.patch.object(
                macos_guarded_java.MacOsProcessMemorySampler,
                "native",
                return_value=sampler,
            ),
            mock.patch.object(macos_guarded_java.os, "killpg") as kill_group,
            self.assertRaisesRegex(
                macos_guarded_java.GuardedJavaError,
                "identity changed",
            ),
        ):
            macos_guarded_java.stop_owned_java_process(TARGET)

        kill_group.assert_not_called()

    def test_verified_target_is_confirmed_missing_after_sigterm(self) -> None:
        sampler = mock.Mock()
        sampler.revalidate.return_value = TARGET
        sampler.sample.return_value = mock.Mock(
            status=macos_guarded_java.SampleStatus.MISSING
        )
        with (
            mock.patch.object(
                macos_guarded_java.MacOsProcessMemorySampler,
                "native",
                return_value=sampler,
            ),
            mock.patch.object(macos_guarded_java.os, "killpg") as kill_group,
            mock.patch.object(
                macos_guarded_java,
                "_process_group_exists",
                return_value=False,
            ),
            mock.patch.object(
                macos_guarded_java.time,
                "monotonic",
                side_effect=(0.0, 1.0),
            ),
        ):
            forced = macos_guarded_java.stop_owned_java_process(TARGET)

        self.assertFalse(forced)
        kill_group.assert_called_once_with(TARGET.pid, signal.SIGTERM)

    def test_verified_group_member_can_stop_its_owned_launch_group(self) -> None:
        target = macos_guarded_java.OwnedJavaProcess(
            pid=41001,
            process_group_id=41000,
            proc_start_abstime=TARGET.proc_start_abstime,
            expected_executable=TARGET.expected_executable,
        )
        sampler = mock.Mock()
        sampler.revalidate.return_value = target
        sampler.sample.return_value = mock.Mock(
            status=macos_guarded_java.SampleStatus.MISSING
        )
        with (
            mock.patch.object(macos_guarded_java.os, "killpg") as kill_group,
            mock.patch.object(
                macos_guarded_java,
                "_process_group_exists",
                return_value=False,
            ),
            mock.patch.object(
                macos_guarded_java.time,
                "monotonic",
                side_effect=(0.0, 1.0),
            ),
        ):
            forced = macos_guarded_java.stop_owned_java_process(
                target,
                owned_process_group_id=41000,
                sampler=sampler,
            )

        self.assertFalse(forced)
        kill_group.assert_called_once_with(41000, signal.SIGTERM)

    def test_owned_group_mismatch_never_signals(self) -> None:
        sampler = mock.Mock()
        with (
            mock.patch.object(macos_guarded_java.os, "killpg") as kill_group,
            self.assertRaisesRegex(
                macos_guarded_java.GuardedJavaError,
                "recorded dedicated PGID",
            ),
        ):
            macos_guarded_java.stop_owned_java_process(
                TARGET,
                owned_process_group_id=TARGET.pid + 1,
                sampler=sampler,
            )

        sampler.revalidate.assert_not_called()
        kill_group.assert_not_called()

    def test_verified_group_member_escalates_when_its_exact_identity_survives(self) -> None:
        target = macos_guarded_java.OwnedJavaProcess(
            pid=41001,
            process_group_id=41000,
            proc_start_abstime=TARGET.proc_start_abstime,
            expected_executable=TARGET.expected_executable,
        )
        sampler = mock.Mock()
        sampler.revalidate.side_effect = (target, target)
        sampler.sample.return_value = mock.Mock(
            status=macos_guarded_java.SampleStatus.MISSING
        )
        with (
            mock.patch.object(macos_guarded_java.os, "getpgrp", return_value=1),
            mock.patch.object(macos_guarded_java.os, "killpg") as kill_group,
            mock.patch.object(
                macos_guarded_java,
                "_process_group_exists",
                return_value=False,
            ),
            mock.patch.object(
                macos_guarded_java.time,
                "monotonic",
                side_effect=(0.0, 0.0, 1.0, 1.0),
            ),
        ):
            forced = macos_guarded_java.stop_owned_java_process(
                target,
                owned_process_group_id=41000,
                timeout_seconds=0.0,
                sampler=sampler,
            )

        self.assertTrue(forced)
        self.assertEqual(
            [
                mock.call(41000, signal.SIGTERM),
                mock.call(41000, signal.SIGKILL),
            ],
            kill_group.call_args_list,
        )

    def test_missing_identity_refuses_escalation_while_group_remains(self) -> None:
        target = macos_guarded_java.OwnedJavaProcess(
            pid=41001,
            process_group_id=41000,
            proc_start_abstime=TARGET.proc_start_abstime,
            expected_executable=TARGET.expected_executable,
        )
        sampler = mock.Mock()
        sampler.revalidate.side_effect = (target, None)
        sampler.sample.return_value = mock.Mock(
            status=macos_guarded_java.SampleStatus.MISSING
        )
        with (
            mock.patch.object(macos_guarded_java.os, "getpgrp", return_value=1),
            mock.patch.object(macos_guarded_java.os, "killpg") as kill_group,
            mock.patch.object(
                macos_guarded_java,
                "_process_group_exists",
                return_value=True,
            ),
            mock.patch.object(
                macos_guarded_java.time,
                "monotonic",
                side_effect=(0.0, 0.0),
            ),
            self.assertRaisesRegex(
                macos_guarded_java.GuardedJavaError,
                "could not be confirmed absent",
            ),
        ):
            macos_guarded_java.stop_owned_java_process(
                target,
                owned_process_group_id=41000,
                timeout_seconds=0.0,
                sampler=sampler,
            )

        kill_group.assert_called_once_with(41000, signal.SIGTERM)


class TelemetryPersistenceTests(unittest.TestCase):
    def test_largest_unterminated_payload_gets_one_bounded_newline(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "telemetry.json"
            content = b"x" * (
                macos_guarded_java.MAXIMUM_TELEMETRY_SIZE_BYTES - 1
            )

            macos_guarded_java._write_telemetry(path, content)

            self.assertEqual(
                macos_guarded_java.MAXIMUM_TELEMETRY_SIZE_BYTES,
                path.stat().st_size,
            )
            self.assertEqual(content + b"\n", path.read_bytes())

    def test_unterminated_maximum_payload_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "telemetry.json"
            content = b"x" * macos_guarded_java.MAXIMUM_TELEMETRY_SIZE_BYTES

            with self.assertRaisesRegex(
                macos_guarded_java.GuardedJavaError,
                "size bound",
            ):
                macos_guarded_java._write_telemetry(path, content)

            self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
