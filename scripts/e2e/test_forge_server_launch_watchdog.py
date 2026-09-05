from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
import importlib.util
import json
import os
from pathlib import Path
import signal
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
MODULE_PATH = SCRIPT_DIRECTORY / "forge_server_launch_watchdog.py"
SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_forge_server_launch_watchdog",
    MODULE_PATH,
)
if SPECIFICATION is None or SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load launch watchdog module: {MODULE_PATH}")
watchdog = importlib.util.module_from_spec(SPECIFICATION)
sys.modules[SPECIFICATION.name] = watchdog
SPECIFICATION.loader.exec_module(watchdog)


JAVA_PATH = "/Library/Java/TestJdk/Contents/Home/bin/java"
ANCHOR = watchdog.OwnedJavaProcess(41000, 41000, 101, JAVA_PATH)
CHILD = watchdog.OwnedJavaProcess(41001, 41000, 102, JAVA_PATH)
SESSION_ID = 40000
LOW_FOOTPRINT = 1024 * 1024


def available_sample(
    identity: watchdog.OwnedJavaProcess,
    footprint: int = LOW_FOOTPRINT,
) -> watchdog.MemorySample:
    return watchdog.MemorySample.proc_pid_rusage_v4(
        1,
        identity,
        watchdog.MacOsRusage(
            resident_size_bytes=footprint,
            current_phys_footprint_bytes=footprint,
            proc_start_abstime=identity.proc_start_abstime,
            lifetime_max_phys_footprint_bytes=footprint,
        ),
    )


def readiness_payload(
    watchdog_pid: int,
    controller_pid: int,
    controller_process_group_id: int,
) -> dict[str, object]:
    return {
        "schema": "etherology-forge-server-launch-watchdog-ready-v1",
        "watchdog_pid": watchdog_pid,
        "controller_pid": controller_pid,
        "controller_process_group_id": controller_process_group_id,
        "anchor": watchdog._identity_payload(ANCHOR),
        "owned_session_id": SESSION_ID,
        "heartbeat_timeout_nanoseconds": int(
            watchdog.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS * 1_000_000_000
        ),
        "ready_at_monotonic_ns": watchdog.time.monotonic_ns(),
        "enforcement_active": True,
    }


def running_telemetry_payload(
    controller_pid: int,
    controller_process_group_id: int,
) -> dict[str, object]:
    observed_at = watchdog.time.monotonic_ns()
    return {
        "schema": "etherology-forge-server-launch-watchdog-v1",
        "status": "running",
        "decision": "continue",
        "reason": None,
        "observed_at_monotonic_ns": observed_at,
        "anchor": watchdog._identity_payload(ANCHOR),
        "owned_session_id": SESSION_ID,
        "controller_pid": controller_pid,
        "controller_process_group_id": controller_process_group_id,
        "controller_heartbeat": {
            "status": "healthy",
            "last_received_at_monotonic_ns": observed_at,
            "age_nanoseconds": 0,
        },
        "limits": {
            "maximum_java_process_count": watchdog.MAXIMUM_JAVA_PROCESS_COUNT,
            "per_process_current_phys_footprint_bytes": (
                watchdog.PER_PROCESS_CEILING_BYTES
            ),
            "aggregate_current_phys_footprint_bytes": (
                watchdog.AGGREGATE_CEILING_BYTES
            ),
        },
        "java_inventory": [ANCHOR.pid],
        "external_java_process_ids": [],
        "tracked_exact_identities": [watchdog._identity_payload(ANCHOR)],
        "samples": [watchdog._sample_payload(available_sample(ANCHOR))],
        "aggregate_current_phys_footprint_bytes": LOW_FOOTPRINT,
        "owned_group_absent": False,
        "signal_actions": [],
        "terminal_attestation": None,
    }


def terminating_telemetry_payload(
    controller_pid: int,
    controller_process_group_id: int,
) -> dict[str, object]:
    payload = running_telemetry_payload(
        controller_pid,
        controller_process_group_id,
    )
    payload["status"] = "terminating"
    payload["decision"] = "terminate"
    payload["reason"] = "per-process-memory-ceiling-exceeded"
    sample = available_sample(ANCHOR, watchdog.PER_PROCESS_CEILING_BYTES + 1)
    payload["samples"] = [watchdog._sample_payload(sample)]
    payload["aggregate_current_phys_footprint_bytes"] = (
        watchdog.PER_PROCESS_CEILING_BYTES + 1
    )
    payload["signal_actions"] = [
        "owned-group-signaled;exact-identities-signaled:0;errors:0"
    ]
    return payload


def terminal_telemetry_payload(
    controller_pid: int,
    controller_process_group_id: int,
    *,
    status: str = "normal",
    reason: str | None = None,
) -> dict[str, object]:
    observed_at = watchdog.time.monotonic_ns()
    missing = watchdog.MemorySample.missing(
        observed_at,
        watchdog.SampleSource.PROC_PID_RUSAGE_V4,
        "missing",
    )
    return {
        "schema": "etherology-forge-server-launch-watchdog-v1",
        "status": status,
        "decision": "exit",
        "reason": reason,
        "observed_at_monotonic_ns": observed_at,
        "anchor": watchdog._identity_payload(ANCHOR),
        "owned_session_id": SESSION_ID,
        "controller_pid": controller_pid,
        "controller_process_group_id": controller_process_group_id,
        "controller_heartbeat": {
            "status": "healthy",
            "last_received_at_monotonic_ns": observed_at,
            "age_nanoseconds": 0,
        },
        "limits": {
            "maximum_java_process_count": watchdog.MAXIMUM_JAVA_PROCESS_COUNT,
            "per_process_current_phys_footprint_bytes": (
                watchdog.PER_PROCESS_CEILING_BYTES
            ),
            "aggregate_current_phys_footprint_bytes": (
                watchdog.AGGREGATE_CEILING_BYTES
            ),
        },
        "java_inventory": [],
        "external_java_process_ids": [],
        "tracked_exact_identities": [],
        "samples": [watchdog._sample_payload(missing)],
        "aggregate_current_phys_footprint_bytes": 0,
        "owned_group_absent": True,
        "signal_actions": [],
        "terminal_attestation": {
            "owned_group_absent": True,
            "tracked_identities_absent": True,
            "global_java_inventory": [],
            "global_java_inventory_error": None,
            "external_java_remained": False,
            "exact_identity_samples": [
                watchdog._exact_identity_sample_payload(ANCHOR, missing)
            ],
            "exact_identity_sample_error": None,
        },
    }


class TransitionProcess:
    pid = 70000

    def __init__(self, return_code: int | None) -> None:
        self.return_code = return_code

    def poll(self) -> int | None:
        return self.return_code


@contextmanager
def transition_handle(
    telemetry_payload: dict[str, object],
    *,
    return_code: int | None,
    readiness: dict[str, object] | None = None,
) -> Iterator[watchdog.LaunchWatchdogHandle]:
    with tempfile.TemporaryDirectory() as directory_name:
        runtime = Path(directory_name)
        runtime.chmod(0o700)
        readiness_path = runtime / watchdog.READINESS_FILE_NAME
        telemetry_path = runtime / watchdog.TELEMETRY_FILE_NAME
        selected_readiness = (
            readiness_payload(70000, 50000, 50001)
            if readiness is None
            else readiness
        )
        readiness_path.write_text(
            json.dumps(selected_readiness),
            encoding="utf-8",
        )
        telemetry_path.write_text(
            json.dumps(telemetry_payload),
            encoding="utf-8",
        )
        readiness_path.chmod(0o600)
        telemetry_path.chmod(0o600)
        read_descriptor, write_descriptor = os.pipe()
        runtime_descriptor = watchdog._open_artifact_directory(runtime)
        handle = watchdog.LaunchWatchdogHandle(
            TransitionProcess(return_code),
            write_descriptor,
            ANCHOR,
            SESSION_ID,
            50000,
            50001,
            int(watchdog.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS * 1_000_000_000),
            runtime_descriptor,
            readiness_path,
            telemetry_path,
        )
        try:
            yield handle
        finally:
            handle.close_heartbeat()
            handle.close_runtime_directory()
            os.close(read_descriptor)


class FakeSampler:
    def __init__(self) -> None:
        self.identities = {ANCHOR.pid: ANCHOR, CHILD.pid: CHILD}
        self.samples = {
            ANCHOR.pid: available_sample(ANCHOR),
            CHILD.pid: available_sample(CHILD),
        }

    def bind_observed(
        self,
        process_id: int,
        process_group_id: int,
    ) -> watchdog.OwnedJavaProcess | None:
        identity = self.identities.get(process_id)
        if identity is None or identity.process_group_id != process_group_id:
            return None
        return identity

    def sample(
        self,
        identity: watchdog.OwnedJavaProcess,
        _observed_at: int,
    ) -> watchdog.MemorySample:
        return self.samples.get(
            identity.pid,
            watchdog.MemorySample.missing(
                1,
                watchdog.SampleSource.PROC_PID_RUSAGE_V4,
                "missing",
            ),
        )

    def revalidate(
        self,
        identity: watchdog.OwnedJavaProcess,
    ) -> watchdog.OwnedJavaProcess | None:
        sample = self.samples.get(identity.pid)
        if sample is not None and sample.status is watchdog.SampleStatus.AVAILABLE:
            return identity
        return None

    def revalidate_intrinsic_identity(
        self,
        identity: watchdog.OwnedJavaProcess,
    ) -> watchdog.OwnedJavaProcess | None:
        sample = self.samples.get(identity.pid)
        if sample is not None and sample.status is watchdog.SampleStatus.AVAILABLE:
            return identity
        return None


def engine_for(
    inventory: tuple[int, ...],
    sampler: FakeSampler,
    *,
    group_exists: bool = True,
    process_groups: dict[int, int] | None = None,
    sessions: dict[int, int] | None = None,
) -> watchdog.LaunchWatchdogEngine:
    selected_groups = process_groups or {
        ANCHOR.pid: ANCHOR.process_group_id,
        CHILD.pid: CHILD.process_group_id,
    }
    selected_sessions = sessions or {
        ANCHOR.pid: SESSION_ID,
        CHILD.pid: SESSION_ID,
    }
    return watchdog.LaunchWatchdogEngine(
        ANCHOR,
        SESSION_ID,
        sampler,
        inventory_reader=lambda: inventory,
        process_group_reader=selected_groups.__getitem__,
        session_reader=selected_sessions.__getitem__,
        group_exists_reader=lambda _group: group_exists,
        monotonic_ns=lambda: 1,
    )


class JavaInventoryTests(unittest.TestCase):
    def test_inventory_parser_accepts_at_most_three_unique_positive_pids(self) -> None:
        self.assertEqual(
            (7, 41, 900),
            watchdog.parse_java_process_inventory(b"900\n7\n41\n", 0),
        )
        self.assertEqual((), watchdog.parse_java_process_inventory(b"", 1))

    def test_inventory_parser_rejects_malformed_or_oversized_results(self) -> None:
        cases = (
            (b"1", 0),
            (b"0\n", 0),
            (b"1\n1\n", 0),
            (b"1\n", 1),
            (b"", 2),
            (b"1\r\n", 0),
            (f"{watchdog.MAXIMUM_PROCESS_ID + 1}\n".encode("ascii"), 0),
            (b"9" * (watchdog.MAXIMUM_INVENTORY_SIZE_BYTES + 1), 0),
        )
        for content, return_code in cases:
            with self.subTest(content=content[:20], return_code=return_code):
                with self.assertRaises(watchdog.LaunchWatchdogError):
                    watchdog.parse_java_process_inventory(content, return_code)

    def test_inventory_parser_preserves_more_than_three_pids_for_failure_evidence(self) -> None:
        self.assertEqual(
            (1, 2, 3, 4),
            watchdog.parse_java_process_inventory(b"1\n2\n3\n4\n", 0),
        )

    def test_inventory_uses_the_exact_protected_pgrep_contract(self) -> None:
        completed = subprocess.CompletedProcess(
            [str(watchdog.PGREP_PATH), "-x", "java"],
            0,
            stdout=b"44\n",
            stderr=b"",
        )
        calls: list[tuple[object, dict[str, object]]] = []

        def run(command: object, **arguments: object) -> object:
            calls.append((command, arguments))
            return completed

        with mock.patch.object(watchdog, "_validate_pgrep_binary"):
            self.assertEqual(
                (44,),
                watchdog.read_java_process_inventory(run_command=run),
            )

        self.assertEqual([str(watchdog.PGREP_PATH), "-x", "java"], calls[0][0])
        self.assertEqual(2.0, calls[0][1]["timeout"])
        self.assertIs(subprocess.PIPE, calls[0][1]["stdout"])
        self.assertEqual(
            {"LANG": "C", "LC_ALL": "C", "PATH": "/usr/bin:/bin"},
            calls[0][1]["env"],
        )

    def test_inventory_rejects_any_stderr(self) -> None:
        completed = subprocess.CompletedProcess(
            [],
            1,
            stdout=b"",
            stderr=b"unexpected",
        )
        with (
            mock.patch.object(watchdog, "_validate_pgrep_binary"),
            self.assertRaises(watchdog.LaunchWatchdogError),
        ):
            watchdog.read_java_process_inventory(
                run_command=lambda *_args, **_kwargs: completed
            )


class LaunchWatchdogEngineTests(unittest.TestCase):
    def test_exact_group_and_session_member_is_dynamically_bound_and_sampled(self) -> None:
        sampler = FakeSampler()
        engine = engine_for((ANCHOR.pid, CHILD.pid), sampler)

        result = engine.poll()

        self.assertIsNone(result.reason)
        self.assertEqual((ANCHOR, CHILD), result.active_identities)
        self.assertEqual(2 * LOW_FOOTPRINT, result.aggregate_current_phys_footprint_bytes)
        self.assertTrue(
            all(
                sample.source is watchdog.SampleSource.PROC_PID_RUSAGE_V4
                and sample.status is watchdog.SampleStatus.AVAILABLE
                for sample in result.samples
            )
        )

    def test_java_outside_the_owned_group_or_session_fails_closed(self) -> None:
        for groups, sessions in (
            ({ANCHOR.pid: ANCHOR.process_group_id, 42000: 42000}, {ANCHOR.pid: SESSION_ID, 42000: SESSION_ID}),
            ({ANCHOR.pid: ANCHOR.process_group_id, 42000: ANCHOR.process_group_id}, {ANCHOR.pid: SESSION_ID, 42000: SESSION_ID + 1}),
        ):
            sampler = FakeSampler()
            result = engine_for(
                (ANCHOR.pid, 42000),
                sampler,
                process_groups=groups,
                sessions=sessions,
            ).poll()

            self.assertEqual("java-outside-owned-launch", result.reason)
            self.assertEqual((42000,), result.external_java_process_ids)

    def test_any_sampling_error_or_identity_drift_fails_closed(self) -> None:
        for status in (
            watchdog.SampleStatus.ERROR,
            watchdog.SampleStatus.IDENTITY_DRIFT,
        ):
            sampler = FakeSampler()
            sampler.samples[ANCHOR.pid] = watchdog.MemorySample(
                observed_at_monotonic_ns=1,
                source=watchdog.SampleSource.PROC_PID_RUSAGE_V4,
                status=status,
                detail="failure",
            )

            result = engine_for((ANCHOR.pid,), sampler).poll()

            self.assertEqual(f"authoritative-sample-{status.value}", result.reason)

    def test_inventory_omission_of_a_live_exact_identity_fails_closed(self) -> None:
        sampler = FakeSampler()
        result = engine_for((), sampler).poll()

        self.assertEqual("java-inventory-omission", result.reason)

    def test_tracked_child_exit_after_inventory_restarts_a_complete_poll(self) -> None:
        sampler = FakeSampler()
        engine = engine_for((ANCHOR.pid, CHILD.pid), sampler)
        self.assertIsNone(engine.poll().reason)
        sampler.samples.pop(CHILD.pid)
        inventories = iter(
            (
                (ANCHOR.pid, CHILD.pid),
                (ANCHOR.pid,),
                (ANCHOR.pid,),
            )
        )
        engine.inventory_reader = lambda: next(inventories)

        result = engine.poll()

        self.assertIsNone(result.reason)
        self.assertEqual((ANCHOR,), result.active_identities)
        self.assertNotIn(CHILD.pid, engine.identities)
        self.assertEqual(CHILD, engine.known_identities[CHILD.pid])

    def test_tracked_child_process_lookup_race_uses_the_same_bounded_restart(
        self,
    ) -> None:
        sampler = FakeSampler()
        engine = engine_for((ANCHOR.pid, CHILD.pid), sampler)
        self.assertIsNone(engine.poll().reason)
        sampler.samples.pop(CHILD.pid)
        inventories = iter(
            (
                (ANCHOR.pid, CHILD.pid),
                (ANCHOR.pid,),
                (ANCHOR.pid,),
            )
        )
        engine.inventory_reader = lambda: next(inventories)

        def process_group(process_id: int) -> int:
            if process_id == CHILD.pid:
                raise ProcessLookupError("child exited")
            return ANCHOR.process_group_id

        engine.process_group_reader = process_group

        result = engine.poll()

        self.assertIsNone(result.reason)
        self.assertEqual((ANCHOR,), result.active_identities)
        self.assertEqual(CHILD, engine.known_identities[CHILD.pid])

    def test_missing_child_still_present_in_fresh_inventory_fails_closed(self) -> None:
        sampler = FakeSampler()
        engine = engine_for((ANCHOR.pid, CHILD.pid), sampler)
        self.assertIsNone(engine.poll().reason)
        sampler.samples.pop(CHILD.pid)
        engine.inventory_reader = lambda: (ANCHOR.pid, CHILD.pid)

        result = engine.poll()

        self.assertEqual("authoritative-sample-missing", result.reason)
        self.assertIn(CHILD.pid, engine.identities)

    def test_child_exit_refresh_cannot_hide_a_new_external_java_process(self) -> None:
        external_process_id = 42_000
        sampler = FakeSampler()
        engine = engine_for((ANCHOR.pid, CHILD.pid), sampler)
        self.assertIsNone(engine.poll().reason)
        sampler.samples.pop(CHILD.pid)
        inventories = iter(
            (
                (ANCHOR.pid, CHILD.pid),
                (ANCHOR.pid, external_process_id),
                (ANCHOR.pid,),
            )
        )
        engine.inventory_reader = lambda: next(inventories)
        engine.process_group_reader = lambda process_id: (
            ANCHOR.process_group_id
            if process_id != external_process_id
            else external_process_id
        )
        engine.session_reader = lambda process_id: (
            SESSION_ID if process_id != external_process_id else external_process_id
        )

        result = engine.poll()

        self.assertEqual("java-outside-owned-launch", result.reason)
        self.assertEqual((external_process_id,), result.external_java_process_ids)

    def test_child_exit_recovery_preserves_a_prior_memory_ceiling_breach(
        self,
    ) -> None:
        exiting_child = watchdog.OwnedJavaProcess(
            41_002,
            ANCHOR.process_group_id,
            103,
            JAVA_PATH,
        )
        sampler = FakeSampler()
        sampler.identities[exiting_child.pid] = exiting_child
        sampler.samples[exiting_child.pid] = available_sample(exiting_child)
        inventory = (ANCHOR.pid, CHILD.pid, exiting_child.pid)
        engine = engine_for(
            inventory,
            sampler,
            process_groups={process_id: ANCHOR.process_group_id for process_id in inventory},
            sessions={process_id: SESSION_ID for process_id in inventory},
        )
        self.assertIsNone(engine.poll().reason)
        sampler.samples.pop(exiting_child.pid)
        original_sample = sampler.sample
        high_sample_pending = True

        def sample(
            identity: watchdog.OwnedJavaProcess,
            observed_at: int,
        ) -> watchdog.MemorySample:
            nonlocal high_sample_pending
            if identity == CHILD and high_sample_pending:
                high_sample_pending = False
                return available_sample(
                    CHILD,
                    watchdog.PER_PROCESS_CEILING_BYTES + 1,
                )
            return original_sample(identity, observed_at)

        sampler.sample = sample
        inventories = iter((inventory, (ANCHOR.pid, CHILD.pid)))
        engine.inventory_reader = lambda: next(inventories)

        result = engine.poll()

        self.assertEqual("per-process-memory-ceiling-exceeded", result.reason)
        self.assertIn(exiting_child.pid, engine.identities)

    def test_child_exit_recovery_preserves_a_prior_aggregate_breach(self) -> None:
        exiting_child = watchdog.OwnedJavaProcess(
            41_002,
            ANCHOR.process_group_id,
            103,
            JAVA_PATH,
        )
        sampler = FakeSampler()
        sampler.identities[exiting_child.pid] = exiting_child
        sampler.samples[exiting_child.pid] = available_sample(exiting_child)
        inventory = (ANCHOR.pid, CHILD.pid, exiting_child.pid)
        engine = engine_for(
            inventory,
            sampler,
            process_groups={process_id: ANCHOR.process_group_id for process_id in inventory},
            sessions={process_id: SESSION_ID for process_id in inventory},
        )
        self.assertIsNone(engine.poll().reason)
        sampler.samples.pop(exiting_child.pid)
        original_sample = sampler.sample
        high_samples_pending = {ANCHOR.pid, CHILD.pid}

        def sample(
            identity: watchdog.OwnedJavaProcess,
            observed_at: int,
        ) -> watchdog.MemorySample:
            if identity.pid in high_samples_pending:
                high_samples_pending.remove(identity.pid)
                footprint = 3 * watchdog.GIBIBYTE_BYTES
                if identity == ANCHOR:
                    footprint += 1
                return available_sample(identity, footprint)
            return original_sample(identity, observed_at)

        sampler.sample = sample
        inventories = iter((inventory, (ANCHOR.pid, CHILD.pid)))
        engine.inventory_reader = lambda: next(inventories)

        result = engine.poll()

        self.assertEqual("aggregate-memory-ceiling-exceeded", result.reason)
        self.assertIn(exiting_child.pid, engine.identities)

    def test_child_exit_recovery_preserves_an_observed_process_count_breach(
        self,
    ) -> None:
        exiting_child = watchdog.OwnedJavaProcess(
            41_002,
            ANCHOR.process_group_id,
            103,
            JAVA_PATH,
        )
        new_child = watchdog.OwnedJavaProcess(
            41_003,
            ANCHOR.process_group_id,
            104,
            JAVA_PATH,
        )
        sampler = FakeSampler()
        sampler.identities[exiting_child.pid] = exiting_child
        sampler.samples[exiting_child.pid] = available_sample(exiting_child)
        base_inventory = (ANCHOR.pid, CHILD.pid, exiting_child.pid)
        all_process_ids = (*base_inventory, new_child.pid)
        engine = engine_for(
            base_inventory,
            sampler,
            process_groups={
                process_id: ANCHOR.process_group_id
                for process_id in all_process_ids
            },
            sessions={process_id: SESSION_ID for process_id in all_process_ids},
        )
        self.assertIsNone(engine.poll().reason)
        sampler.samples.pop(exiting_child.pid)
        engine.inventory_reader = lambda: all_process_ids

        result = engine.poll()

        self.assertEqual("too-many-java-processes", result.reason)
        self.assertIn(exiting_child.pid, engine.identities)

    def test_child_disappearance_recovery_is_retry_bounded(self) -> None:
        sampler = FakeSampler()
        engine = engine_for((ANCHOR.pid, CHILD.pid), sampler)
        self.assertIsNone(engine.poll().reason)
        sampler.samples.pop(CHILD.pid)

        result = engine._poll(0)

        self.assertEqual("authoritative-sample-missing", result.reason)
        self.assertIn(CHILD.pid, engine.identities)

    def test_absent_exact_identities_and_absent_group_are_a_normal_terminal_state(self) -> None:
        sampler = FakeSampler()
        sampler.samples.clear()
        result = engine_for((), sampler, group_exists=False).poll()

        self.assertIsNone(result.reason)
        self.assertTrue(result.owned_group_absent)
        self.assertEqual((), result.active_identities)

    def test_per_process_five_gib_ceiling_fails_on_the_first_byte_above(self) -> None:
        sampler = FakeSampler()
        sampler.samples[ANCHOR.pid] = available_sample(
            ANCHOR,
            watchdog.PER_PROCESS_CEILING_BYTES + 1,
        )

        result = engine_for((ANCHOR.pid,), sampler).poll()

        self.assertEqual("per-process-memory-ceiling-exceeded", result.reason)

    def test_aggregate_six_gib_ceiling_includes_every_bound_java_identity(self) -> None:
        sampler = FakeSampler()
        sampler.samples[ANCHOR.pid] = available_sample(
            ANCHOR,
            3 * watchdog.GIBIBYTE_BYTES + 1,
        )
        sampler.samples[CHILD.pid] = available_sample(
            CHILD,
            3 * watchdog.GIBIBYTE_BYTES,
        )

        result = engine_for((ANCHOR.pid, CHILD.pid), sampler).poll()

        self.assertEqual("aggregate-memory-ceiling-exceeded", result.reason)

    def test_inventory_reader_failure_is_a_launch_failure(self) -> None:
        sampler = FakeSampler()
        engine = engine_for((ANCHOR.pid,), sampler)
        engine.inventory_reader = lambda: (_ for _ in ()).throw(OSError("failed"))

        result = engine.poll()

        self.assertTrue(result.reason.startswith("java-inventory-error:"))

    def test_unexpected_sampler_exception_is_a_launch_failure(self) -> None:
        sampler = FakeSampler()
        sampler.sample = lambda *_arguments: (_ for _ in ()).throw(
            TypeError("invalid native sample")
        )

        result = engine_for((ANCHOR.pid,), sampler).poll()

        self.assertTrue(result.reason.startswith("authoritative-sample-error:"))

    def test_more_than_three_java_pids_are_recorded_and_fail_closed(self) -> None:
        sampler = FakeSampler()
        extra_identities = tuple(
            watchdog.OwnedJavaProcess(
                process_id,
                ANCHOR.process_group_id,
                process_id,
                JAVA_PATH,
            )
            for process_id in (42000, 42001, 42002)
        )
        for identity in extra_identities:
            sampler.identities[identity.pid] = identity
            sampler.samples[identity.pid] = available_sample(identity)
        inventory = (ANCHOR.pid, *(identity.pid for identity in extra_identities))
        process_groups = {
            process_id: ANCHOR.process_group_id for process_id in inventory
        }
        sessions = {process_id: SESSION_ID for process_id in inventory}
        engine = engine_for(
            inventory,
            sampler,
            process_groups=process_groups,
            sessions=sessions,
        )

        result = engine.poll()

        self.assertEqual("too-many-java-processes", result.reason)
        self.assertEqual(inventory, result.inventory)
        self.assertEqual(
            inventory,
            tuple(identity.pid for identity in result.active_identities),
        )

    def test_lost_anchor_continuity_never_adopts_a_reused_numeric_group(self) -> None:
        sampler = FakeSampler()
        engine = engine_for((ANCHOR.pid,), sampler)
        self.assertIsNone(engine.poll().reason)
        sampler.samples.clear()
        engine.inventory_reader = lambda: ()

        lost = engine.poll()

        self.assertEqual("anchor-ownership-continuity-lost", lost.reason)
        replacement = watchdog.OwnedJavaProcess(
            43000,
            ANCHOR.process_group_id,
            43000,
            JAVA_PATH,
        )
        sampler.identities[replacement.pid] = replacement
        sampler.samples[replacement.pid] = available_sample(replacement)
        engine.inventory_reader = lambda: (replacement.pid,)
        engine.process_group_reader = lambda _pid: ANCHOR.process_group_id
        engine.session_reader = lambda _pid: SESSION_ID

        reused = engine.poll()

        self.assertEqual(
            "anchor-ownership-continuity-already-lost",
            reused.reason,
        )
        self.assertNotIn(replacement.pid, engine.known_identities)

    def test_anchor_loss_uses_one_fresh_inventory_to_bind_a_surviving_child(
        self,
    ) -> None:
        sampler = FakeSampler()
        sampler.samples.pop(ANCHOR.pid)
        inventories = iter(((), (CHILD.pid,)))
        engine = engine_for((), sampler)
        engine.inventory_reader = lambda: next(inventories)

        result = engine.poll()

        self.assertEqual("anchor-ownership-continuity-lost", result.reason)
        self.assertEqual((CHILD,), result.active_identities)
        self.assertEqual((CHILD.pid,), result.inventory)
        self.assertEqual(
            (watchdog.SampleStatus.MISSING, watchdog.SampleStatus.AVAILABLE),
            tuple(sample.status for sample in result.samples),
        )
        self.assertTrue(engine.final_loss_discovery_consumed)

        group_signal = mock.Mock()
        action = watchdog.signal_owned_launch(
            result.active_identities,
            ANCHOR,
            SESSION_ID,
            sampler,
            signal.SIGTERM,
            controller_pid=50000,
            controller_process_group_id=50000,
            process_group_reader=lambda _pid: ANCHOR.process_group_id,
            session_reader=lambda _pid: SESSION_ID,
            signal_group=group_signal,
            current_pid=lambda: 60000,
            current_process_group_id=lambda: 60000,
        )
        self.assertEqual(
            "owned-group-signaled;exact-identities-signaled:0;errors:0",
            action,
        )
        group_signal.assert_called_once_with(
            ANCHOR.process_group_id,
            signal.SIGTERM,
        )

    def test_stale_inventory_anchor_entry_cannot_skip_the_fresh_loss_scan(
        self,
    ) -> None:
        sampler = FakeSampler()
        sampler.samples.pop(ANCHOR.pid)
        inventories = iter(((ANCHOR.pid,), (CHILD.pid,)))
        engine = engine_for((ANCHOR.pid,), sampler)
        engine.inventory_reader = lambda: next(inventories)

        result = engine.poll()

        self.assertEqual("anchor-ownership-continuity-lost", result.reason)
        self.assertEqual((CHILD,), result.active_identities)
        self.assertEqual((CHILD.pid,), result.inventory)
        self.assertNotIn(ANCHOR.pid, engine.identities)

    def test_anchor_loss_final_inventory_rejects_a_wrong_session_candidate(
        self,
    ) -> None:
        sampler = FakeSampler()
        sampler.samples.pop(ANCHOR.pid)
        inventories = iter(((), (CHILD.pid,)))
        engine = engine_for(
            (),
            sampler,
            sessions={ANCHOR.pid: SESSION_ID, CHILD.pid: SESSION_ID + 1},
        )
        engine.inventory_reader = lambda: next(inventories)

        result = engine.poll()

        self.assertEqual("java-outside-owned-launch", result.reason)
        self.assertEqual((CHILD.pid,), result.external_java_process_ids)
        self.assertNotIn(CHILD.pid, engine.known_identities)

    def test_anchor_loss_final_bind_revalidation_drift_never_authenticates_group(
        self,
    ) -> None:
        sampler = FakeSampler()
        sampler.samples.pop(ANCHOR.pid)
        inventories = iter(((), (CHILD.pid,)))
        group_reads = iter(
            (ANCHOR.process_group_id, ANCHOR.process_group_id + 1)
        )
        engine = engine_for((), sampler)
        engine.inventory_reader = lambda: next(inventories)
        engine.process_group_reader = lambda _pid: next(group_reads)
        engine.session_reader = lambda _pid: SESSION_ID

        result = engine.poll()

        self.assertEqual("java-bind-identity-drift", result.reason)
        self.assertEqual((CHILD,), result.active_identities)
        self.assertEqual(CHILD, engine.known_identities[CHILD.pid])

        process_signal = mock.Mock()
        group_signal = mock.Mock()
        action = watchdog.signal_owned_launch(
            result.active_identities,
            ANCHOR,
            SESSION_ID,
            sampler,
            signal.SIGTERM,
            controller_pid=50000,
            controller_process_group_id=50000,
            process_group_reader=lambda _pid: CHILD.pid,
            session_reader=lambda _pid: CHILD.pid,
            signal_process=process_signal,
            signal_group=group_signal,
            current_pid=lambda: 60000,
            current_process_group_id=lambda: 60000,
        )
        self.assertEqual(
            "owned-group-not-signaled;exact-identities-signaled:1;errors:0",
            action,
        )
        group_signal.assert_not_called()
        process_signal.assert_called_once_with(CHILD.pid, signal.SIGTERM)

    def test_anchor_loss_samples_an_oversized_child_before_termination(self) -> None:
        sampler = FakeSampler()
        sampler.samples.pop(ANCHOR.pid)
        sampler.samples[CHILD.pid] = available_sample(
            CHILD,
            watchdog.PER_PROCESS_CEILING_BYTES + 1,
        )
        inventories = iter(((), (CHILD.pid,)))
        engine = engine_for((), sampler)
        engine.inventory_reader = lambda: next(inventories)

        result = engine.poll()

        self.assertEqual("anchor-ownership-continuity-lost", result.reason)
        self.assertEqual((CHILD,), result.active_identities)
        self.assertEqual(
            watchdog.PER_PROCESS_CEILING_BYTES + 1,
            result.aggregate_current_phys_footprint_bytes,
        )
        self.assertEqual(
            watchdog.PER_PROCESS_CEILING_BYTES + 1,
            result.samples[-1].current_phys_footprint_bytes,
        )

    def test_anchor_loss_final_inventory_failure_is_never_retried(self) -> None:
        sampler = FakeSampler()
        sampler.samples.pop(ANCHOR.pid)
        inventory_calls = 0

        def inventory_reader() -> tuple[int, ...]:
            nonlocal inventory_calls
            inventory_calls += 1
            if inventory_calls == 1:
                return ()
            if inventory_calls == 2:
                raise OSError("final scan failed")
            return (CHILD.pid,)

        engine = engine_for((), sampler)
        engine.inventory_reader = inventory_reader

        failed = engine.poll()
        retry = engine.poll()

        self.assertTrue(failed.reason.startswith("final-loss-java-inventory-error:"))
        self.assertEqual("anchor-ownership-continuity-already-lost", retry.reason)
        self.assertEqual(3, inventory_calls)
        self.assertNotIn(CHILD.pid, engine.known_identities)

    def test_anchor_numeric_pid_reuse_in_final_inventory_is_never_rebound(
        self,
    ) -> None:
        sampler = FakeSampler()
        sampler.samples.pop(ANCHOR.pid)
        replacement = watchdog.OwnedJavaProcess(
            ANCHOR.pid,
            ANCHOR.process_group_id,
            ANCHOR.proc_start_abstime + 1,
            JAVA_PATH,
        )
        sampler.identities[replacement.pid] = replacement
        sampler.samples[replacement.pid] = available_sample(replacement)
        sampler.sample = lambda identity, _observed_at: (
            watchdog.MemorySample.missing(
                1,
                watchdog.SampleSource.PROC_PID_RUSAGE_V4,
                "missing",
            )
            if identity == ANCHOR
            else available_sample(replacement)
        )
        inventories = iter(((), (replacement.pid,)))
        engine = engine_for((), sampler)
        engine.inventory_reader = lambda: next(inventories)

        result = engine.poll()

        self.assertEqual("java-known-process-id-reuse", result.reason)
        self.assertEqual((), result.active_identities)
        self.assertEqual(ANCHOR, engine.known_identities[ANCHOR.pid])

    def test_terminal_attestation_cannot_bypass_anchor_continuity_freeze(self) -> None:
        sampler = FakeSampler()
        engine = engine_for((ANCHOR.pid,), sampler)
        sampler.samples.clear()

        absent, _samples, _error = watchdog._attest_owned_absence(
            engine,
            observed_at=2,
        )

        self.assertFalse(absent)
        self.assertTrue(engine.anchor_continuity_active)
        self.assertFalse(engine.final_loss_discovery_consumed)
        engine.inventory_reader = lambda: ()
        lost = engine.poll()
        self.assertEqual("anchor-ownership-continuity-lost", lost.reason)
        self.assertTrue(engine.final_loss_discovery_consumed)
        replacement = watchdog.OwnedJavaProcess(
            43000,
            ANCHOR.process_group_id,
            43000,
            JAVA_PATH,
        )
        sampler.identities[replacement.pid] = replacement
        sampler.samples[replacement.pid] = available_sample(replacement)
        engine.inventory_reader = lambda: (replacement.pid,)
        engine.process_group_reader = lambda _pid: ANCHOR.process_group_id
        engine.session_reader = lambda _pid: SESSION_ID

        result = engine.poll()

        self.assertEqual(
            "anchor-ownership-continuity-already-lost",
            result.reason,
        )
        self.assertNotIn(replacement.pid, engine.known_identities)

    def test_terminal_absence_consumes_loss_discovery_before_numeric_reuse(
        self,
    ) -> None:
        sampler = FakeSampler()
        sampler.samples.clear()
        engine = engine_for((), sampler, group_exists=False)

        absent, _samples, _error = watchdog._attest_owned_absence(
            engine,
            observed_at=2,
        )

        self.assertTrue(absent)
        self.assertFalse(engine.anchor_continuity_active)
        self.assertTrue(engine.final_loss_discovery_consumed)
        replacement = watchdog.OwnedJavaProcess(
            43000,
            ANCHOR.process_group_id,
            43000,
            JAVA_PATH,
        )
        sampler.identities[replacement.pid] = replacement
        sampler.samples[replacement.pid] = available_sample(replacement)
        engine.inventory_reader = lambda: (replacement.pid,)
        engine.group_exists_reader = lambda _group: True
        engine.process_group_reader = lambda _pid: ANCHOR.process_group_id
        engine.session_reader = lambda _pid: SESSION_ID

        result = engine.poll()

        self.assertEqual(
            "anchor-ownership-continuity-already-lost",
            result.reason,
        )
        self.assertNotIn(replacement.pid, engine.known_identities)


class SafeSignalTests(unittest.TestCase):
    def test_signal_contract_rejects_unsupported_or_duplicate_targets(self) -> None:
        sampler = FakeSampler()
        for targets, signal_number in (
            ((ANCHOR,), signal.SIGINT),
            ((ANCHOR, ANCHOR), signal.SIGTERM),
        ):
            with self.subTest(targets=targets, signal_number=signal_number):
                with self.assertRaises(watchdog.LaunchWatchdogError):
                    watchdog.signal_owned_launch(
                        targets,
                        ANCHOR,
                        SESSION_ID,
                        sampler,
                        signal_number,
                        controller_pid=50000,
                        controller_process_group_id=50000,
                    )

    def test_exact_live_anchor_signals_the_group(self) -> None:
        sampler = FakeSampler()
        groups: list[tuple[int, int]] = []

        action = watchdog.signal_owned_launch(
            (ANCHOR, CHILD),
            ANCHOR,
            SESSION_ID,
            sampler,
            signal.SIGTERM,
            controller_pid=50000,
            controller_process_group_id=50000,
            process_group_reader=lambda _pid: ANCHOR.process_group_id,
            session_reader=lambda _pid: SESSION_ID,
            signal_group=lambda group, sent_signal: groups.append((group, sent_signal)),
            current_pid=lambda: 60000,
            current_process_group_id=lambda: 60000,
        )

        self.assertEqual(
            "owned-group-signaled;exact-identities-signaled:0;errors:0",
            action,
        )
        self.assertEqual([(ANCHOR.process_group_id, signal.SIGTERM)], groups)

    def test_controller_or_watchdog_group_is_never_signaled(self) -> None:
        sampler = FakeSampler()
        signal_group = mock.Mock()
        signal_process = mock.Mock()

        for controller_group, current_group in (
            (ANCHOR.process_group_id, 60000),
            (50000, ANCHOR.process_group_id),
        ):
            watchdog.signal_owned_launch(
                (ANCHOR,),
                ANCHOR,
                SESSION_ID,
                sampler,
                signal.SIGKILL,
                controller_pid=50000,
                controller_process_group_id=controller_group,
                process_group_reader=lambda _pid: ANCHOR.process_group_id,
                session_reader=lambda _pid: SESSION_ID,
                signal_process=signal_process,
                signal_group=signal_group,
                current_pid=lambda: 60000,
                current_process_group_id=lambda: current_group,
            )

        signal_group.assert_not_called()
        self.assertEqual(2, signal_process.call_count)

    def test_surviving_exact_child_can_anchor_the_original_group(self) -> None:
        sampler = FakeSampler()
        sampler.samples.pop(ANCHOR.pid)
        process_signals: list[tuple[int, int]] = []
        group_signal = mock.Mock()

        action = watchdog.signal_owned_launch(
            (ANCHOR, CHILD),
            ANCHOR,
            SESSION_ID,
            sampler,
            signal.SIGTERM,
            controller_pid=50000,
            controller_process_group_id=50000,
            process_group_reader=lambda _pid: ANCHOR.process_group_id,
            session_reader=lambda _pid: SESSION_ID,
            signal_process=lambda pid, sent_signal: process_signals.append(
                (pid, sent_signal)
            ),
            signal_group=group_signal,
            current_pid=lambda: 60000,
            current_process_group_id=lambda: 60000,
        )

        self.assertEqual(
            "owned-group-signaled;exact-identities-signaled:0;errors:0",
            action,
        )
        self.assertEqual([], process_signals)
        group_signal.assert_called_once_with(
            ANCHOR.process_group_id,
            signal.SIGTERM,
        )

    def test_session_drift_still_signals_the_intrinsically_owned_process(self) -> None:
        sampler = FakeSampler()
        sampler.samples.pop(ANCHOR.pid)
        signal_process = mock.Mock()

        watchdog.signal_owned_launch(
            (CHILD,),
            ANCHOR,
            SESSION_ID,
            sampler,
            signal.SIGTERM,
            controller_pid=50000,
            controller_process_group_id=50000,
            process_group_reader=lambda _pid: ANCHOR.process_group_id,
            session_reader=lambda _pid: SESSION_ID + 1,
            signal_process=signal_process,
            current_pid=lambda: 60000,
            current_process_group_id=lambda: 60000,
        )

        signal_process.assert_called_once_with(CHILD.pid, signal.SIGTERM)

    def test_migrated_child_is_signaled_exactly_after_the_group(self) -> None:
        sampler = FakeSampler()
        group_signal = mock.Mock()
        process_signal = mock.Mock()

        action = watchdog.signal_owned_launch(
            (ANCHOR, CHILD),
            ANCHOR,
            SESSION_ID,
            sampler,
            signal.SIGKILL,
            controller_pid=50000,
            controller_process_group_id=50000,
            process_group_reader=lambda pid: (
                ANCHOR.process_group_id if pid == ANCHOR.pid else CHILD.pid
            ),
            session_reader=lambda pid: (
                SESSION_ID if pid == ANCHOR.pid else CHILD.pid
            ),
            signal_process=process_signal,
            signal_group=group_signal,
            current_pid=lambda: 60000,
            current_process_group_id=lambda: 60000,
        )

        self.assertEqual(
            "owned-group-signaled;exact-identities-signaled:1;errors:0",
            action,
        )
        group_signal.assert_called_once_with(
            ANCHOR.process_group_id,
            signal.SIGKILL,
        )
        process_signal.assert_called_once_with(CHILD.pid, signal.SIGKILL)


class HeartbeatAndMonitorTests(unittest.TestCase):
    def test_heartbeat_eof_and_bounded_silence_are_failures(self) -> None:
        read_descriptor, write_descriptor = os.pipe()
        try:
            os.close(write_descriptor)
            eof = watchdog._observe_heartbeat(
                read_descriptor,
                0,
                10,
                lambda: 5,
            )
        finally:
            os.close(read_descriptor)
        self.assertEqual("eof", eof.status)

        read_descriptor, write_descriptor = os.pipe()
        try:
            silent = watchdog._observe_heartbeat(
                read_descriptor,
                0,
                10,
                lambda: 11,
            )
        finally:
            os.close(read_descriptor)
            os.close(write_descriptor)
        self.assertEqual("silent", silent.status)

    def test_monitor_records_normal_terminal_attestation_without_launching_java(self) -> None:
        sampler = FakeSampler()
        polls = [
            watchdog.WatchdogPoll(
                None,
                False,
                (ANCHOR.pid,),
                (),
                (available_sample(ANCHOR),),
                (ANCHOR,),
                LOW_FOOTPRINT,
            ),
            watchdog.WatchdogPoll(None, True, (), (), (), (), 0),
        ]

        class FakeEngine:
            def __init__(self, *_arguments: object) -> None:
                self.anchor = ANCHOR
                self.sampler = sampler
                self.identities = {ANCHOR.pid: ANCHOR}
                self.known_identities = {ANCHOR.pid: ANCHOR}
                self.group_exists_reader = lambda _group: not not self.identities
                self.inventory_reader = lambda: tuple(self.identities)

            def poll(self) -> watchdog.WatchdogPoll:
                result = polls.pop(0)
                if not result.active_identities:
                    self.identities.clear()
                    sampler.samples.clear()
                return result

        read_descriptor, write_descriptor = os.pipe()
        os.write(write_descriptor, b"H")
        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            readiness = runtime / watchdog.READINESS_FILE_NAME
            telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
            clock = iter(range(1, 100))
            runtime_descriptor = watchdog._open_artifact_directory(runtime)
            try:
                result = watchdog.monitor_launch(
                    ANCHOR,
                    SESSION_ID,
                    50000,
                    50000,
                    read_descriptor,
                    runtime_descriptor,
                    readiness,
                    telemetry,
                    sampler=sampler,
                    engine_factory=FakeEngine,
                    monotonic_ns=lambda: next(clock),
                    sleep=lambda _seconds: None,
                )
            finally:
                os.close(read_descriptor)
                os.close(write_descriptor)
                os.close(runtime_descriptor)

            payload = json.loads(telemetry.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual("normal", payload["status"])
            self.assertTrue(payload["terminal_attestation"]["owned_group_absent"])
            self.assertEqual(
                [watchdog._identity_payload(ANCHOR)],
                [
                    entry["expected_identity"]
                    for entry in payload["terminal_attestation"][
                        "exact_identity_samples"
                    ]
                ],
            )
            self.assertEqual(0o600, telemetry.stat().st_mode & 0o777)
            self.assertEqual(0o600, readiness.stat().st_mode & 0o777)

    def test_heartbeat_eof_after_exact_exit_remains_a_normal_terminal_state(self) -> None:
        sampler = FakeSampler()

        class FakeEngine:
            def __init__(self, *_arguments: object) -> None:
                self.anchor = ANCHOR
                self.sampler = sampler
                self.identities = {ANCHOR.pid: ANCHOR}
                self.known_identities = {ANCHOR.pid: ANCHOR}
                self.group_exists_reader = lambda _group: bool(sampler.samples)
                self.inventory_reader = lambda: tuple(self.identities)

            def poll(self) -> watchdog.WatchdogPoll:
                result = watchdog.WatchdogPoll(
                    None,
                    False,
                    (ANCHOR.pid,),
                    (),
                    (available_sample(ANCHOR),),
                    (ANCHOR,),
                    LOW_FOOTPRINT,
                )
                sampler.samples.clear()
                return result

        read_descriptor, write_descriptor = os.pipe()
        os.close(write_descriptor)
        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            runtime_descriptor = watchdog._open_artifact_directory(runtime)
            telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
            try:
                result = watchdog.monitor_launch(
                    ANCHOR,
                    SESSION_ID,
                    50000,
                    50000,
                    read_descriptor,
                    runtime_descriptor,
                    runtime / watchdog.READINESS_FILE_NAME,
                    telemetry,
                    sampler=sampler,
                    engine_factory=FakeEngine,
                    monotonic_ns=iter(range(1, 100)).__next__,
                    sleep=lambda _seconds: None,
                )
            finally:
                os.close(read_descriptor)
                os.close(runtime_descriptor)

            payload = json.loads(telemetry.read_text(encoding="utf-8"))
            self.assertEqual(0, result)
            self.assertEqual("normal", payload["status"])
            self.assertIsNone(payload["reason"])

    def test_controller_heartbeat_eof_terminates_and_records_failure(self) -> None:
        sampler = FakeSampler()

        class FakeEngine:
            def __init__(self, *_arguments: object) -> None:
                self.anchor = ANCHOR
                self.sampler = sampler
                self.identities = {ANCHOR.pid: ANCHOR}
                self.known_identities = {ANCHOR.pid: ANCHOR}
                self.group_exists_reader = lambda _group: bool(self.identities)
                self.inventory_reader = lambda: tuple(self.identities)

            def poll(self) -> watchdog.WatchdogPoll:
                return watchdog.WatchdogPoll(
                    None,
                    not self.identities,
                    tuple(self.identities),
                    (),
                    (),
                    tuple(self.identities.values()),
                    0,
                )

        engine_holder: list[FakeEngine] = []

        def make_engine(*arguments: object) -> FakeEngine:
            engine = FakeEngine(*arguments)
            engine_holder.append(engine)
            return engine

        def signal_launch(*_arguments: object, **_kwargs: object) -> str:
            sampler.samples.clear()
            engine_holder[0].identities.clear()
            return "test-owned-group-signaled"

        read_descriptor, write_descriptor = os.pipe()
        os.close(write_descriptor)
        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
            runtime_descriptor = watchdog._open_artifact_directory(runtime)
            try:
                result = watchdog.monitor_launch(
                    ANCHOR,
                    SESSION_ID,
                    50000,
                    50000,
                    read_descriptor,
                    runtime_descriptor,
                    runtime / watchdog.READINESS_FILE_NAME,
                    telemetry,
                    sampler=sampler,
                    engine_factory=make_engine,
                    monotonic_ns=iter(range(1, 100)).__next__,
                    sleep=lambda _seconds: None,
                    signal_launch=signal_launch,
                )
            finally:
                os.close(read_descriptor)
                os.close(runtime_descriptor)

            payload = json.loads(telemetry.read_text(encoding="utf-8"))
            self.assertEqual(1, result)
            self.assertEqual("failed", payload["status"])
            self.assertEqual("controller-heartbeat-eof", payload["reason"])
            self.assertEqual(
                ["test-owned-group-signaled"],
                payload["signal_actions"],
            )

    def test_external_java_is_recorded_and_makes_terminal_exit_fail(self) -> None:
        sampler = FakeSampler()
        polls = [
            watchdog.WatchdogPoll(
                None,
                False,
                (ANCHOR.pid,),
                (),
                (available_sample(ANCHOR),),
                (ANCHOR,),
                LOW_FOOTPRINT,
            ),
            watchdog.WatchdogPoll(None, True, (), (), (), (), 0),
        ]

        class FakeEngine:
            def __init__(self, *_arguments: object) -> None:
                self.anchor = ANCHOR
                self.sampler = sampler
                self.identities = {ANCHOR.pid: ANCHOR}
                self.known_identities = {ANCHOR.pid: ANCHOR}
                self.group_exists_reader = lambda _group: bool(self.identities)
                self.inventory_reader = lambda: (42000,)

            def poll(self) -> watchdog.WatchdogPoll:
                result = polls.pop(0)
                if not result.active_identities:
                    self.identities.clear()
                    sampler.samples.clear()
                return result

        read_descriptor, write_descriptor = os.pipe()
        os.write(write_descriptor, b"H")
        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
            runtime_descriptor = watchdog._open_artifact_directory(runtime)
            try:
                result = watchdog.monitor_launch(
                    ANCHOR,
                    SESSION_ID,
                    50000,
                    50000,
                    read_descriptor,
                    runtime_descriptor,
                    runtime / watchdog.READINESS_FILE_NAME,
                    telemetry,
                    sampler=sampler,
                    engine_factory=FakeEngine,
                    monotonic_ns=iter(range(1, 100)).__next__,
                    sleep=lambda _seconds: None,
                )
            finally:
                os.close(read_descriptor)
                os.close(write_descriptor)
                os.close(runtime_descriptor)

            payload = json.loads(telemetry.read_text(encoding="utf-8"))
            self.assertEqual(1, result)
            self.assertEqual(
                "terminal-global-java-inventory-not-empty",
                payload["reason"],
            )
            self.assertEqual(
                [42000],
                payload["terminal_attestation"]["global_java_inventory"],
            )
            self.assertTrue(
                payload["terminal_attestation"]["external_java_remained"]
            )


class ApiTests(unittest.TestCase):
    def test_pinned_directory_keeps_writes_out_of_a_replacement_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            root = Path(directory_name)
            runtime = root / "runtime"
            runtime.mkdir(mode=0o700)
            descriptor = watchdog._open_artifact_directory(runtime)
            pinned_runtime = root / "pinned-runtime"
            redirected_runtime = root / "redirected-runtime"
            redirected_runtime.mkdir(mode=0o700)
            runtime.rename(pinned_runtime)
            runtime.symlink_to(redirected_runtime, target_is_directory=True)
            try:
                watchdog._write_atomic(
                    runtime / watchdog.TELEMETRY_FILE_NAME,
                    b"{}\n",
                    descriptor,
                )

                self.assertTrue(
                    (pinned_runtime / watchdog.TELEMETRY_FILE_NAME).is_file()
                )
                self.assertFalse(
                    (redirected_runtime / watchdog.TELEMETRY_FILE_NAME).exists()
                )
                with self.assertRaises(watchdog.LaunchWatchdogError):
                    watchdog._read_json_artifact(
                        runtime / watchdog.TELEMETRY_FILE_NAME,
                        watchdog.MAXIMUM_TELEMETRY_SIZE_BYTES,
                        descriptor,
                    )
            finally:
                os.close(descriptor)

    def test_artifact_reader_retries_one_legitimate_atomic_replacement(self) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            artifact = runtime / watchdog.TELEMETRY_FILE_NAME
            artifact.write_text('{"generation":1}\n', encoding="utf-8")
            artifact.chmod(0o600)
            descriptor = watchdog._open_artifact_directory(runtime)
            real_stat = os.stat
            replacement_performed = False

            def racing_stat(*arguments: object, **keywords: object) -> os.stat_result:
                nonlocal replacement_performed
                if (
                    not replacement_performed
                    and arguments
                    and arguments[0] == artifact.name
                    and keywords.get("dir_fd") == descriptor
                ):
                    replacement = runtime / ".replacement"
                    replacement.write_text(
                        '{"generation":2}\n',
                        encoding="utf-8",
                    )
                    replacement.chmod(0o600)
                    os.replace(replacement, artifact)
                    replacement_performed = True
                return real_stat(*arguments, **keywords)

            try:
                with mock.patch.object(watchdog.os, "stat", side_effect=racing_stat):
                    payload = watchdog._read_json_artifact(
                        artifact,
                        watchdog.MAXIMUM_TELEMETRY_SIZE_BYTES,
                        descriptor,
                    )
            finally:
                os.close(descriptor)

            self.assertTrue(replacement_performed)
            self.assertEqual({"generation": 2}, payload)

    def test_artifact_reader_retries_replacement_between_open_and_fstat(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            artifact = runtime / watchdog.TELEMETRY_FILE_NAME
            artifact.write_text('{"generation":1}\n', encoding="utf-8")
            artifact.chmod(0o600)
            directory_descriptor = watchdog._open_artifact_directory(runtime)
            real_fstat = os.fstat
            replacement_performed = False

            def racing_fstat(descriptor: int) -> os.stat_result:
                nonlocal replacement_performed
                if not replacement_performed and descriptor != directory_descriptor:
                    replacement = runtime / ".replacement"
                    replacement.write_text(
                        '{"generation":2}\n',
                        encoding="utf-8",
                    )
                    replacement.chmod(0o600)
                    os.replace(replacement, artifact)
                    replacement_performed = True
                return real_fstat(descriptor)

            try:
                with mock.patch.object(
                    watchdog.os,
                    "fstat",
                    side_effect=racing_fstat,
                ):
                    payload = watchdog._read_json_artifact(
                        artifact,
                        watchdog.MAXIMUM_TELEMETRY_SIZE_BYTES,
                        directory_descriptor,
                    )
            finally:
                os.close(directory_descriptor)

            self.assertTrue(replacement_performed)
            self.assertEqual({"generation": 2}, payload)

    def test_start_uses_a_detached_process_and_inherited_read_descriptor(self) -> None:
        captured: dict[str, object] = {}
        inherited_descriptors: list[int] = []

        class FakeProcess:
            pid = 70000

            def poll(self) -> None:
                return None

            def wait(self, timeout: float) -> int:
                captured["wait_timeout"] = timeout
                return 0

        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)

            def popen(command: list[str], **arguments: object) -> FakeProcess:
                captured["command"] = command
                captured["arguments"] = arguments
                inherited_descriptors.append(os.dup(arguments["pass_fds"][0]))
                readiness = readiness_payload(
                    70000,
                    os.getpid(),
                    os.getpgrp(),
                )
                path = runtime / watchdog.READINESS_FILE_NAME
                path.write_text(json.dumps(readiness), encoding="utf-8")
                path.chmod(0o600)
                telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
                telemetry.write_text(
                    json.dumps(
                        running_telemetry_payload(os.getpid(), os.getpgrp())
                    ),
                    encoding="utf-8",
                )
                telemetry.chmod(0o600)
                return FakeProcess()

            handle = watchdog.start_launch_watchdog(
                ANCHOR,
                SESSION_ID,
                runtime,
                popen_factory=popen,
                monotonic=iter((0.0, 0.1)).__next__,
                sleep=lambda _seconds: None,
            )
            try:
                arguments = captured["arguments"]
                self.assertTrue(arguments["start_new_session"])
                self.assertEqual(2, len(arguments["pass_fds"]))
                self.assertIn("--controller-pgid", captured["command"])
                self.assertIn("--runtime-directory-fd", captured["command"])
            finally:
                handle.close_heartbeat()
                handle.close_runtime_directory()
                os.close(inherited_descriptors[0])

    def test_verify_rejects_stale_or_non_enforcing_telemetry(self) -> None:
        class FakeProcess:
            pid = 70000

            def poll(self) -> None:
                return None

        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            readiness = runtime / watchdog.READINESS_FILE_NAME
            telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
            readiness.write_text(
                json.dumps(
                    readiness_payload(70000, 50000, 50001)
                ),
                encoding="utf-8",
            )
            telemetry.write_text(
                json.dumps(
                    {
                        **running_telemetry_payload(50000, 50001),
                        "observed_at_monotonic_ns": 1,
                        "controller_heartbeat": {
                            "status": "healthy",
                            "last_received_at_monotonic_ns": 1,
                            "age_nanoseconds": 0,
                        },
                    }
                ),
                encoding="utf-8",
            )
            readiness.chmod(0o600)
            telemetry.chmod(0o600)
            read_descriptor, write_descriptor = os.pipe()
            os.close(read_descriptor)
            runtime_descriptor = watchdog._open_artifact_directory(runtime)
            handle = watchdog.LaunchWatchdogHandle(
                FakeProcess(),
                write_descriptor,
                ANCHOR,
                SESSION_ID,
                50000,
                50001,
                int(watchdog.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS * 1_000_000_000),
                runtime_descriptor,
                readiness,
                telemetry,
            )
            try:
                with self.assertRaises(watchdog.LaunchWatchdogError):
                    watchdog.verify_launch_watchdog(
                        handle,
                        monotonic_ns=lambda: (
                            watchdog.MAXIMUM_TELEMETRY_AGE_NANOSECONDS + 2
                        ),
                    )
            finally:
                handle.close_heartbeat()
                handle.close_runtime_directory()

    def test_transition_verifier_accepts_healthy_live_watchdog(self) -> None:
        telemetry = running_telemetry_payload(50000, 50001)
        with transition_handle(telemetry, return_code=None) as handle, mock.patch.object(
            watchdog,
            "send_launch_watchdog_heartbeat",
        ) as send_heartbeat:
            verified = watchdog.verify_launch_watchdog_transition(handle)

        self.assertEqual("running", verified["status"])
        send_heartbeat.assert_not_called()

    def test_transition_verifier_accepts_exact_live_termination(self) -> None:
        telemetry = terminating_telemetry_payload(50000, 50001)
        with transition_handle(telemetry, return_code=None) as handle:
            verified = watchdog.verify_launch_watchdog_transition(handle)

        self.assertEqual("terminating", verified["status"])
        self.assertEqual("terminate", verified["decision"])
        self.assertEqual(
            ["owned-group-signaled;exact-identities-signaled:0;errors:0"],
            verified["signal_actions"],
        )

        fallback = terminating_telemetry_payload(50000, 50001)
        fallback["reason"] = "authoritative-sample-available"
        fallback["samples"][0]["source"] = watchdog.SampleSource.FALLBACK.value
        fallback["samples"][0]["current_phys_footprint_bytes"] = None
        fallback["aggregate_current_phys_footprint_bytes"] = 0
        with transition_handle(fallback, return_code=None) as handle:
            verified_fallback = watchdog.verify_launch_watchdog_transition(handle)

        self.assertEqual("terminating", verified_fallback["status"])

    def test_transition_verifier_preserves_live_ownership_and_policy_contracts(
        self,
    ) -> None:
        invalid_payloads: list[tuple[str, dict[str, object]]] = []
        wrong_schema = terminating_telemetry_payload(50000, 50001)
        wrong_schema["schema"] = "other"
        invalid_payloads.append(("schema", wrong_schema))
        wrong_limit = terminating_telemetry_payload(50000, 50001)
        wrong_limit["limits"]["maximum_java_process_count"] += 1
        invalid_payloads.append(("limits", wrong_limit))
        wrong_group = terminating_telemetry_payload(50000, 50001)
        wrong_group["tracked_exact_identities"][0]["process_group_id"] += 1
        invalid_payloads.append(("ownership", wrong_group))
        no_signal = terminating_telemetry_payload(50000, 50001)
        no_signal["signal_actions"] = []
        invalid_payloads.append(("stop action", no_signal))
        noncanonical_signal = terminating_telemetry_payload(50000, 50001)
        noncanonical_signal["signal_actions"] = [
            "owned-group-signaled;exact-identities-signaled:00;errors:0"
        ]
        invalid_payloads.append(("canonical stop action", noncanonical_signal))
        wrong_heartbeat_reason = terminating_telemetry_payload(50000, 50001)
        wrong_heartbeat_reason["controller_heartbeat"]["status"] = "eof"
        invalid_payloads.append(("heartbeat reason", wrong_heartbeat_reason))
        unowned_sample = terminating_telemetry_payload(50000, 50001)
        unowned_sample["samples"][0]["pid"] = CHILD.pid
        invalid_payloads.append(("sample ownership", unowned_sample))

        for description, telemetry in invalid_payloads:
            with self.subTest(description=description), transition_handle(
                telemetry,
                return_code=None,
            ) as handle, self.assertRaises(watchdog.LaunchWatchdogError):
                watchdog.verify_launch_watchdog_transition(handle)

        inactive_readiness = readiness_payload(70000, 50000, 50001)
        inactive_readiness["enforcement_active"] = False
        with transition_handle(
            terminating_telemetry_payload(50000, 50001),
            return_code=None,
            readiness=inactive_readiness,
        ) as handle, self.assertRaises(watchdog.LaunchWatchdogError):
            watchdog.verify_launch_watchdog_transition(handle)

    def test_transition_verifier_accepts_reaped_normal_terminal(self) -> None:
        telemetry = terminal_telemetry_payload(50000, 50001)
        with transition_handle(telemetry, return_code=0) as handle:
            verified = watchdog.verify_launch_watchdog_transition(handle)

        self.assertEqual("normal", verified["status"])
        self.assertEqual("exit", verified["decision"])

    def test_transition_verifier_rejects_failed_or_unverifiable_terminal(
        self,
    ) -> None:
        failed = terminal_telemetry_payload(
            50000,
            50001,
            status="failed",
            reason="per-process-memory-ceiling-exceeded",
        )
        with transition_handle(
            failed,
            return_code=1,
        ) as handle, self.assertRaisesRegex(
            watchdog.LaunchWatchdogError,
            "failed terminal state",
        ):
            watchdog.verify_launch_watchdog_transition(handle)

        unverifiable_cases = (
            ("invalid exit code", terminal_telemetry_payload(50000, 50001), 2),
            ("nonterminal telemetry", running_telemetry_payload(50000, 50001), 0),
        )
        for description, telemetry, return_code in unverifiable_cases:
            with self.subTest(description=description), transition_handle(
                telemetry,
                return_code=return_code,
            ) as handle, self.assertRaises(watchdog.LaunchWatchdogError):
                watchdog.verify_launch_watchdog_transition(handle)

    def test_terminal_verifier_requires_exact_global_absence(self) -> None:
        class FakeProcess:
            pid = 70000

            def poll(self) -> int:
                return 0

        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            readiness = runtime / watchdog.READINESS_FILE_NAME
            telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
            expected_readiness = readiness_payload(70000, 50000, 50001)
            expected_telemetry = terminal_telemetry_payload(50000, 50001)
            readiness.write_text(
                json.dumps(expected_readiness),
                encoding="utf-8",
            )
            telemetry.write_text(
                json.dumps(expected_telemetry),
                encoding="utf-8",
            )
            readiness.chmod(0o600)
            telemetry.chmod(0o600)
            read_descriptor, write_descriptor = os.pipe()
            runtime_descriptor = watchdog._open_artifact_directory(runtime)
            handle = watchdog.LaunchWatchdogHandle(
                FakeProcess(),
                write_descriptor,
                ANCHOR,
                SESSION_ID,
                50000,
                50001,
                int(watchdog.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS * 1_000_000_000),
                runtime_descriptor,
                readiness,
                telemetry,
            )
            try:
                verified = watchdog.verify_terminal_launch_watchdog(
                    handle,
                    require_normal_exit=True,
                )
                self.assertEqual("normal", verified["status"])
                self.assertEqual(
                    {
                        watchdog.READINESS_FILE_NAME: watchdog._json_bytes(
                            expected_readiness,
                            watchdog.MAXIMUM_READINESS_SIZE_BYTES,
                        ),
                        watchdog.TELEMETRY_FILE_NAME: watchdog._json_bytes(
                            expected_telemetry,
                            watchdog.MAXIMUM_TELEMETRY_SIZE_BYTES,
                        ),
                    },
                    handle.verified_terminal_artifact_contents,
                )

                tampered_payloads: list[tuple[str, dict[str, object]]] = []
                wrong_identity = terminal_telemetry_payload(50000, 50001)
                wrong_identity["terminal_attestation"]["exact_identity_samples"][
                    0
                ]["expected_identity"]["proc_start_abstime"] += 1
                tampered_payloads.append(("wrong exact identity", wrong_identity))
                contradictory_inventory = terminal_telemetry_payload(50000, 50001)
                contradictory_inventory["java_inventory"] = [ANCHOR.pid]
                tampered_payloads.append(
                    ("contradictory live inventory", contradictory_inventory)
                )
                future_timestamp = terminal_telemetry_payload(50000, 50001)
                future_timestamp["observed_at_monotonic_ns"] = (
                    watchdog.time.monotonic_ns() + 10_000_000_000
                )
                tampered_payloads.append(("future timestamp", future_timestamp))
                signaled_normal = terminal_telemetry_payload(50000, 50001)
                signaled_normal["signal_actions"] = ["owned-group-signaled"]
                tampered_payloads.append(("signaled normal exit", signaled_normal))
                global_java = terminal_telemetry_payload(50000, 50001)
                global_java["terminal_attestation"][
                    "global_java_inventory"
                ] = [9]
                tampered_payloads.append(("global Java remains", global_java))

                for description, tampered in tampered_payloads:
                    with self.subTest(description=description):
                        telemetry.write_text(json.dumps(tampered), encoding="utf-8")
                        telemetry.chmod(0o600)
                        with self.assertRaises(watchdog.LaunchWatchdogError):
                            watchdog.verify_terminal_launch_watchdog(
                                handle,
                                require_normal_exit=True,
                            )
            finally:
                handle.close_heartbeat()
                handle.close_runtime_directory()
                os.close(read_descriptor)

    def test_failed_terminal_allows_only_missing_or_valid_inactive_readiness(
        self,
    ) -> None:
        class FakeProcess:
            pid = 70000

            def __init__(self) -> None:
                self.return_code = 1

            def poll(self) -> int:
                return self.return_code

        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            readiness = runtime / watchdog.READINESS_FILE_NAME
            telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
            inactive_readiness = readiness_payload(70000, 50000, 50001)
            inactive_readiness["enforcement_active"] = False
            readiness.write_text(
                json.dumps(inactive_readiness),
                encoding="utf-8",
            )
            telemetry.write_text(
                json.dumps(
                    terminal_telemetry_payload(
                        50000,
                        50001,
                        status="failed",
                        reason="readiness-write-error: simulated",
                    )
                ),
                encoding="utf-8",
            )
            readiness.chmod(0o600)
            telemetry.chmod(0o600)
            read_descriptor, write_descriptor = os.pipe()
            runtime_descriptor = watchdog._open_artifact_directory(runtime)
            process = FakeProcess()
            handle = watchdog.LaunchWatchdogHandle(
                process,
                write_descriptor,
                ANCHOR,
                SESSION_ID,
                50000,
                50001,
                int(watchdog.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS * 1_000_000_000),
                runtime_descriptor,
                readiness,
                telemetry,
            )
            try:
                verified = watchdog.verify_terminal_launch_watchdog(
                    handle,
                    require_normal_exit=False,
                )
                self.assertEqual("failed", verified["status"])

                readiness.unlink()
                self.assertEqual(
                    1,
                    watchdog.finish_launch_watchdog(
                        handle,
                        require_normal_exit=False,
                    ),
                )

                with self.assertRaises(watchdog.LaunchWatchdogError):
                    watchdog.verify_terminal_launch_watchdog(
                        handle,
                        require_normal_exit=True,
                    )

                invalid_readiness = dict(inactive_readiness)
                invalid_readiness["controller_pid"] = 50002
                readiness.write_text(
                    json.dumps(invalid_readiness),
                    encoding="utf-8",
                )
                readiness.chmod(0o600)
                with self.assertRaises(watchdog.LaunchWatchdogError):
                    watchdog.verify_terminal_launch_watchdog(
                        handle,
                        require_normal_exit=False,
                    )
            finally:
                handle.close_heartbeat()
                handle.close_runtime_directory()
                os.close(read_descriptor)

    def test_normal_terminal_requires_active_readiness_even_when_optional(
        self,
    ) -> None:
        class FakeProcess:
            pid = 70000

            def poll(self) -> int:
                return 0

        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            readiness = runtime / watchdog.READINESS_FILE_NAME
            telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
            inactive_readiness = readiness_payload(70000, 50000, 50001)
            inactive_readiness["enforcement_active"] = False
            readiness.write_text(
                json.dumps(inactive_readiness),
                encoding="utf-8",
            )
            telemetry.write_text(
                json.dumps(terminal_telemetry_payload(50000, 50001)),
                encoding="utf-8",
            )
            readiness.chmod(0o600)
            telemetry.chmod(0o600)
            read_descriptor, write_descriptor = os.pipe()
            runtime_descriptor = watchdog._open_artifact_directory(runtime)
            handle = watchdog.LaunchWatchdogHandle(
                FakeProcess(),
                write_descriptor,
                ANCHOR,
                SESSION_ID,
                50000,
                50001,
                int(watchdog.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS * 1_000_000_000),
                runtime_descriptor,
                readiness,
                telemetry,
            )
            try:
                with self.assertRaises(watchdog.LaunchWatchdogError):
                    watchdog.verify_terminal_launch_watchdog(
                        handle,
                        require_normal_exit=False,
                    )

                readiness.unlink()
                with self.assertRaises(watchdog.LaunchWatchdogError):
                    watchdog.verify_terminal_launch_watchdog(
                        handle,
                        require_normal_exit=False,
                    )
            finally:
                handle.close_heartbeat()
                handle.close_runtime_directory()
                os.close(read_descriptor)

    def test_finish_renews_heartbeat_until_normal_terminal_proof(self) -> None:
        class FakeProcess:
            pid = 70000

            def __init__(self) -> None:
                self.poll_count = 0

            def poll(self) -> int | None:
                self.poll_count += 1
                return 0 if self.poll_count >= 3 else None

        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)
            readiness = runtime / watchdog.READINESS_FILE_NAME
            telemetry = runtime / watchdog.TELEMETRY_FILE_NAME
            readiness.write_text(
                json.dumps(readiness_payload(70000, 50000, 50001)),
                encoding="utf-8",
            )
            telemetry.write_text(
                json.dumps(terminal_telemetry_payload(50000, 50001)),
                encoding="utf-8",
            )
            readiness.chmod(0o600)
            telemetry.chmod(0o600)
            read_descriptor, write_descriptor = os.pipe()
            runtime_descriptor = watchdog._open_artifact_directory(runtime)
            process = FakeProcess()
            handle = watchdog.LaunchWatchdogHandle(
                process,
                write_descriptor,
                ANCHOR,
                SESSION_ID,
                50000,
                50001,
                int(watchdog.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS * 1_000_000_000),
                runtime_descriptor,
                readiness,
                telemetry,
            )
            try:
                result = watchdog.finish_launch_watchdog(
                    handle,
                    require_normal_exit=True,
                    monotonic=iter((0.0, 0.1, 0.2, 0.3)).__next__,
                    sleep=lambda _seconds: None,
                )

                self.assertEqual(0, result)
                self.assertEqual(-1, handle.heartbeat_write_descriptor)
                self.assertGreaterEqual(len(os.read(read_descriptor, 64)), 1)
            finally:
                handle.close_heartbeat()
                handle.close_runtime_directory()
                os.close(read_descriptor)

    def test_start_failure_preserves_the_spawned_watchdog_handle(self) -> None:
        inherited_read_descriptors: list[int] = []

        class FakeProcess:
            pid = 70000

            def poll(self) -> None:
                return None

            def wait(self, timeout: float) -> int:
                self.timeout = timeout
                return 1

        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)

            def popen(_command: list[str], **arguments: object) -> FakeProcess:
                inherited_read_descriptors.append(
                    os.dup(arguments["pass_fds"][0])
                )
                readiness = readiness_payload(70000, os.getpid(), os.getpgrp())
                readiness["enforcement_active"] = False
                readiness_path = runtime / watchdog.READINESS_FILE_NAME
                readiness_path.write_text(json.dumps(readiness), encoding="utf-8")
                readiness_path.chmod(0o600)
                telemetry_path = runtime / watchdog.TELEMETRY_FILE_NAME
                telemetry_path.write_text(
                    json.dumps(
                        running_telemetry_payload(os.getpid(), os.getpgrp())
                    ),
                    encoding="utf-8",
                )
                telemetry_path.chmod(0o600)
                return FakeProcess()

            with self.assertRaises(watchdog.LaunchWatchdogStartError) as raised:
                watchdog.start_launch_watchdog(
                    ANCHOR,
                    SESSION_ID,
                    runtime,
                    popen_factory=popen,
                    monotonic=iter((0.0, 0.1)).__next__,
                    sleep=lambda _seconds: None,
                )

            handle = raised.exception.handle
            try:
                self.assertEqual(-1, handle.heartbeat_write_descriptor)
                self.assertEqual(70000, handle.process.pid)
                self.assertTrue(
                    stat.S_ISDIR(os.fstat(handle.runtime_directory_descriptor).st_mode)
                )
            finally:
                handle.close_runtime_directory()
                os.close(inherited_read_descriptors[0])

    def test_parent_read_close_failure_preserves_spawned_watchdog_handle(
        self,
    ) -> None:
        captured_read_descriptors: list[int] = []

        class FakeProcess:
            pid = 70000

            def poll(self) -> None:
                return None

            def wait(self, timeout: float) -> int:
                self.timeout = timeout
                return 1

        with tempfile.TemporaryDirectory() as directory_name:
            runtime = Path(directory_name)
            runtime.chmod(0o700)

            def popen(_command: list[str], **arguments: object) -> FakeProcess:
                captured_read_descriptors.append(arguments["pass_fds"][0])
                return FakeProcess()

            real_close = os.close
            close_failed = False

            def failing_close(descriptor: int) -> None:
                nonlocal close_failed
                if (
                    not close_failed
                    and captured_read_descriptors
                    and descriptor == captured_read_descriptors[0]
                ):
                    close_failed = True
                    raise OSError("simulated parent read close failure")
                real_close(descriptor)

            with mock.patch.object(
                watchdog.os,
                "close",
                side_effect=failing_close,
            ):
                with self.assertRaises(
                    watchdog.LaunchWatchdogStartError
                ) as raised:
                    watchdog.start_launch_watchdog(
                        ANCHOR,
                        SESSION_ID,
                        runtime,
                        popen_factory=popen,
                    )

            handle = raised.exception.handle
            try:
                self.assertTrue(close_failed)
                self.assertEqual(-1, handle.heartbeat_write_descriptor)
                self.assertEqual(70000, handle.process.pid)
                self.assertTrue(
                    stat.S_ISDIR(os.fstat(handle.runtime_directory_descriptor).st_mode)
                )
            finally:
                handle.close_runtime_directory()
                real_close(captured_read_descriptors[0])

    def test_stop_closes_the_heartbeat_and_reaps_without_signaling_a_process(self) -> None:
        class FakeProcess:
            pid = 70000

            def wait(self, timeout: float) -> int:
                self.timeout = timeout
                return 1

        read_descriptor, write_descriptor = os.pipe()
        runtime_descriptor = os.open(".", os.O_RDONLY)
        process = FakeProcess()
        handle = watchdog.LaunchWatchdogHandle(
            process,
            write_descriptor,
            ANCHOR,
            SESSION_ID,
            50000,
            50001,
            int(watchdog.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS * 1_000_000_000),
            runtime_descriptor,
            Path("ready"),
            Path("telemetry"),
        )
        try:
            self.assertEqual(
                1,
                watchdog.stop_launch_watchdog(handle, timeout_seconds=3.0),
            )
            self.assertEqual(-1, handle.heartbeat_write_descriptor)
            self.assertEqual(3.0, process.timeout)
        finally:
            os.close(read_descriptor)
            handle.close_runtime_directory()


if __name__ == "__main__":
    unittest.main()
