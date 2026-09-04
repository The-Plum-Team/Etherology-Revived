from __future__ import annotations

import ctypes
import errno
import importlib.util
import json
from pathlib import Path
import sys
import unittest
from unittest import mock


BASELINE_DIRECTORY = Path(__file__).resolve().parents[1]
MODULE_PATH = BASELINE_DIRECTORY / "macos_memory_guard.py"
SPECIFICATION = importlib.util.spec_from_file_location(
    "etherology_macos_memory_guard",
    MODULE_PATH,
)
if SPECIFICATION is None or SPECIFICATION.loader is None:
    raise RuntimeError(f"Cannot load memory guard module: {MODULE_PATH}")
guard_module = importlib.util.module_from_spec(SPECIFICATION)
sys.modules[SPECIFICATION.name] = guard_module
SPECIFICATION.loader.exec_module(guard_module)


TARGET = guard_module.OwnedJavaProcess(
    pid=41001,
    process_group_id=41000,
    proc_start_abstime=987654321,
    expected_executable="/Library/Java/TestJdk/Contents/Home/bin/java",
)
LOW_FOOTPRINT = 7 * guard_module.GIBIBYTE_BYTES
WARNING_FOOTPRINT = 9 * guard_module.GIBIBYTE_BYTES
HARD_FOOTPRINT = 13 * guard_module.GIBIBYTE_BYTES
EMERGENCY_FOOTPRINT = 17 * guard_module.GIBIBYTE_BYTES


def make_rusage(
    footprint: int,
    *,
    resident: int | None = None,
    start_abstime: int = TARGET.proc_start_abstime,
    lifetime: int | None = None,
) -> guard_module.MacOsRusage:
    if resident is None:
        resident = footprint // 2
    if lifetime is None:
        lifetime = footprint
    return guard_module.MacOsRusage(
        resident_size_bytes=resident,
        current_phys_footprint_bytes=footprint,
        proc_start_abstime=start_abstime,
        lifetime_max_phys_footprint_bytes=lifetime,
    )


def exact_sample(
    observed_at: int,
    footprint: int,
    *,
    identity: guard_module.OwnedJavaProcess = TARGET,
    resident: int | None = None,
    lifetime: int | None = None,
) -> guard_module.MemorySample:
    return guard_module.MemorySample.proc_pid_rusage_v4(
        observed_at,
        identity,
        make_rusage(
            footprint,
            resident=resident,
            start_abstime=identity.proc_start_abstime,
            lifetime=lifetime,
        ),
    )


def samples_for_footprints(
    footprints: list[int],
) -> list[guard_module.MemorySample]:
    return [
        exact_sample(index * guard_module.SAMPLE_INTERVAL_NANOSECONDS, footprint)
        for index, footprint in enumerate(footprints)
    ]


class FakeClock:
    def __init__(self) -> None:
        self.now = 0

    def __call__(self) -> int:
        return self.now

    def advance(self, nanoseconds: int = guard_module.SAMPLE_INTERVAL_NANOSECONDS) -> None:
        self.now += nanoseconds


class SequenceSampler:
    def __init__(self, entries: list[object]) -> None:
        self.entries = list(entries)
        self.call_count = 0

    def __call__(
        self,
        target: guard_module.OwnedJavaProcess,
        observed_at: int,
    ) -> guard_module.MemorySample:
        self.call_count += 1
        if not self.entries:
            raise AssertionError("sequence sampler was called too many times")
        entry = self.entries.pop(0)
        if isinstance(entry, BaseException):
            raise entry
        if type(entry) is int:
            return exact_sample(observed_at, entry, identity=target)
        if callable(entry):
            return entry(target, observed_at)
        if isinstance(entry, guard_module.MemorySample):
            return entry
        raise TypeError(f"Unsupported test sample entry: {entry!r}")


class GuardFixture:
    def __init__(
        self,
        entries: list[object],
        *,
        revalidated_identity: guard_module.OwnedJavaProcess | None = TARGET,
        revalidation_exception: BaseException | None = None,
        stop_exception: BaseException | None = None,
        current_pid: object = 51001,
        current_process_group_id: object = 51000,
    ) -> None:
        self.clock = FakeClock()
        self.sampler = SequenceSampler(entries)
        self.events: list[str] = []
        self.stops: list[
            tuple[guard_module.OwnedJavaProcess, guard_module.MemoryDecision]
        ] = []

        def revalidate(
            target: guard_module.OwnedJavaProcess,
        ) -> guard_module.OwnedJavaProcess | None:
            self.events.append("revalidate")
            if revalidation_exception is not None:
                raise revalidation_exception
            return revalidated_identity

        def stop(
            target: guard_module.OwnedJavaProcess,
            decision: guard_module.MemoryDecision,
        ) -> None:
            self.events.append("stop")
            self.stops.append((target, decision))
            if stop_exception is not None:
                raise stop_exception

        self.guard = guard_module.OwnedJavaMemoryGuard(
            TARGET,
            self.sampler,
            revalidate,
            stop,
            monotonic_ns=self.clock,
            current_pid=lambda: current_pid,
            current_process_group_id=lambda: current_process_group_id,
        )

    def poll(self, count: int) -> list[guard_module.GuardPollResult]:
        results: list[guard_module.GuardPollResult] = []
        for _index in range(count):
            results.append(self.guard.poll())
            self.clock.advance()
        return results


class IdentityAndSampleValidationTests(unittest.TestCase):
    def test_owned_identity_rejects_nonpositive_and_boolean_identifiers(self) -> None:
        for field_name, field_value in (
            ("pid", 0),
            ("pid", True),
            ("process_group_id", -1),
            ("proc_start_abstime", 0),
            (
                "proc_start_abstime",
                guard_module.MAXIMUM_UNSIGNED_64_BIT_INTEGER + 1,
            ),
        ):
            values = {
                "pid": TARGET.pid,
                "process_group_id": TARGET.process_group_id,
                "proc_start_abstime": TARGET.proc_start_abstime,
                "expected_executable": TARGET.expected_executable,
            }
            values[field_name] = field_value
            with self.subTest(field_name=field_name, field_value=field_value):
                with self.assertRaises(ValueError):
                    guard_module.OwnedJavaProcess(**values)

    def test_owned_identity_requires_one_bounded_absolute_executable(self) -> None:
        for executable in (
            "java",
            "/java\nother",
            "/java\x00other",
            "/" + "x" * guard_module.MAXIMUM_EXECUTABLE_SIZE_BYTES,
        ):
            with self.subTest(executable=executable[:20]):
                with self.assertRaises(ValueError):
                    guard_module.OwnedJavaProcess(
                        TARGET.pid,
                        TARGET.process_group_id,
                        TARGET.proc_start_abstime,
                        executable,
                    )

    def test_rusage_keeps_current_resident_and_lifetime_values_distinct(self) -> None:
        rusage = guard_module.MacOsRusage(
            resident_size_bytes=11,
            current_phys_footprint_bytes=22,
            proc_start_abstime=33,
            lifetime_max_phys_footprint_bytes=44,
        )

        self.assertEqual(11, rusage.resident_size_bytes)
        self.assertEqual(22, rusage.current_phys_footprint_bytes)
        self.assertEqual(33, rusage.proc_start_abstime)
        self.assertEqual(44, rusage.lifetime_max_phys_footprint_bytes)

    def test_memory_metrics_reject_negative_values_and_booleans(self) -> None:
        for value in (-1, True):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    guard_module.MacOsRusage(
                        resident_size_bytes=value,
                        current_phys_footprint_bytes=1,
                        proc_start_abstime=1,
                        lifetime_max_phys_footprint_bytes=1,
                    )

    def test_fallback_cannot_claim_current_or_lifetime_physical_footprint(self) -> None:
        for field_name in (
            "current_phys_footprint_bytes",
            "lifetime_max_phys_footprint_bytes",
        ):
            arguments = {
                "observed_at_monotonic_ns": 1,
                "source": guard_module.SampleSource.FALLBACK,
                "status": guard_module.SampleStatus.AVAILABLE,
                "observed_identity": TARGET,
                field_name: EMERGENCY_FOOTPRINT,
            }
            with self.subTest(field_name=field_name):
                with self.assertRaises(ValueError):
                    guard_module.MemorySample(**arguments)

    def test_available_rusage_sample_requires_identity_and_current_footprint(self) -> None:
        base_arguments = {
            "observed_at_monotonic_ns": 1,
            "source": guard_module.SampleSource.PROC_PID_RUSAGE_V4,
            "status": guard_module.SampleStatus.AVAILABLE,
        }
        for extra_arguments in (
            {"current_phys_footprint_bytes": 1},
            {"observed_identity": TARGET},
        ):
            with self.subTest(extra_arguments=extra_arguments):
                with self.assertRaises(ValueError):
                    guard_module.MemorySample(**base_arguments, **extra_arguments)


class MemoryPolicyTests(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = guard_module.FOUR_GIB_CLIENT_MEMORY_POLICY

    def evaluate(self, samples: list[guard_module.MemorySample]) -> guard_module.MemoryDecision:
        return self.policy.evaluate(samples, TARGET)

    def test_four_gib_policy_has_the_required_exact_thresholds_and_windows(self) -> None:
        self.assertEqual(4 * guard_module.GIBIBYTE_BYTES, self.policy.heap_limit_bytes)
        self.assertEqual(8 * guard_module.GIBIBYTE_BYTES, self.policy.warning_phys_footprint_bytes)
        self.assertEqual(12 * guard_module.GIBIBYTE_BYTES, self.policy.hard_phys_footprint_bytes)
        self.assertEqual(
            16 * guard_module.GIBIBYTE_BYTES,
            self.policy.emergency_phys_footprint_bytes,
        )
        self.assertEqual(60, self.policy.hard_window_sample_count)
        self.assertEqual(45, self.policy.hard_required_high_sample_count)
        self.assertEqual(10, self.policy.hard_final_high_sample_count)
        self.assertEqual(10, self.policy.emergency_final_high_sample_count)

    def test_empty_or_current_unavailable_sample_is_not_enforceable(self) -> None:
        self.assertIs(
            guard_module.MemoryDecision.NOT_ENFORCEABLE,
            self.evaluate([]),
        )
        missing = guard_module.MemorySample.missing(
            0,
            guard_module.SampleSource.PROC_PID_RUSAGE_V4,
            "missing",
        )
        self.assertIs(
            guard_module.MemoryDecision.NOT_ENFORCEABLE,
            self.evaluate([missing]),
        )

    def test_warning_is_strictly_above_eight_gibibytes(self) -> None:
        at_threshold = exact_sample(0, guard_module.WARNING_PHYS_FOOTPRINT_BYTES)
        above_threshold = exact_sample(
            guard_module.SAMPLE_INTERVAL_NANOSECONDS,
            guard_module.WARNING_PHYS_FOOTPRINT_BYTES + 1,
        )

        self.assertIs(guard_module.MemoryDecision.NORMAL, self.evaluate([at_threshold]))
        self.assertIs(
            guard_module.MemoryDecision.WARNING,
            self.evaluate([above_threshold]),
        )

    def test_large_resident_virtual_and_lifetime_values_do_not_drive_policy(self) -> None:
        current_is_low = exact_sample(
            0,
            LOW_FOOTPRINT,
            resident=100 * guard_module.GIBIBYTE_BYTES,
            lifetime=100 * guard_module.GIBIBYTE_BYTES,
        )
        fallback = guard_module.MemorySample.fallback(
            guard_module.SAMPLE_INTERVAL_NANOSECONDS,
            TARGET,
            resident_size_bytes=100 * guard_module.GIBIBYTE_BYTES,
            virtual_size_bytes=1000 * guard_module.GIBIBYTE_BYTES,
        )

        self.assertIs(
            guard_module.MemoryDecision.NORMAL,
            self.evaluate([current_is_low]),
        )
        self.assertIs(
            guard_module.MemoryDecision.NOT_ENFORCEABLE,
            self.evaluate([fallback]),
        )

    def test_mismatched_identity_is_not_enforceable(self) -> None:
        other_identity = guard_module.OwnedJavaProcess(
            TARGET.pid,
            TARGET.process_group_id,
            TARGET.proc_start_abstime + 1,
            TARGET.expected_executable,
        )

        self.assertIs(
            guard_module.MemoryDecision.NOT_ENFORCEABLE,
            self.evaluate([exact_sample(0, EMERGENCY_FOOTPRINT, identity=other_identity)]),
        )

    def test_emergency_requires_ten_consecutive_exact_samples_above_sixteen_gib(self) -> None:
        nine_high = samples_for_footprints([EMERGENCY_FOOTPRINT] * 9)
        ten_high = samples_for_footprints([EMERGENCY_FOOTPRINT] * 10)

        self.assertIs(guard_module.MemoryDecision.WARNING, self.evaluate(nine_high))
        self.assertIs(guard_module.MemoryDecision.EMERGENCY, self.evaluate(ten_high))

    def test_emergency_threshold_equality_and_one_invalid_sample_break_the_run(self) -> None:
        at_threshold = samples_for_footprints(
            [guard_module.EMERGENCY_PHYS_FOOTPRINT_BYTES] * 10
        )
        interrupted = samples_for_footprints([EMERGENCY_FOOTPRINT] * 9)
        interrupted.insert(
            5,
            guard_module.MemorySample.error(
                5 * guard_module.SAMPLE_INTERVAL_NANOSECONDS,
                guard_module.SampleSource.PROC_PID_RUSAGE_V4,
                "temporary error",
            ),
        )

        self.assertIs(guard_module.MemoryDecision.WARNING, self.evaluate(at_threshold))
        self.assertIs(
            guard_module.MemoryDecision.WARNING,
            self.evaluate(interrupted),
        )

    def test_hard_requires_forty_five_high_samples_and_final_ten_high(self) -> None:
        footprints = [HARD_FOOTPRINT] * 35
        footprints.extend([LOW_FOOTPRINT] * 15)
        footprints.extend([HARD_FOOTPRINT] * 10)

        self.assertIs(
            guard_module.MemoryDecision.HARD,
            self.evaluate(samples_for_footprints(footprints)),
        )

    def test_hard_rejects_only_forty_four_high_samples(self) -> None:
        footprints = [HARD_FOOTPRINT] * 34
        footprints.extend([LOW_FOOTPRINT] * 16)
        footprints.extend([HARD_FOOTPRINT] * 10)

        self.assertIs(
            guard_module.MemoryDecision.WARNING,
            self.evaluate(samples_for_footprints(footprints)),
        )

    def test_hard_rejects_a_low_sample_in_the_final_ten(self) -> None:
        footprints = [HARD_FOOTPRINT] * 36
        footprints.extend([LOW_FOOTPRINT] * 14)
        footprints.extend([HARD_FOOTPRINT] * 9)
        footprints.append(LOW_FOOTPRINT)

        self.assertIs(
            guard_module.MemoryDecision.NORMAL,
            self.evaluate(samples_for_footprints(footprints)),
        )

    def test_hard_rejects_any_non_enforceable_sample_in_the_sixty_sample_window(self) -> None:
        observed_at = 20 * guard_module.SAMPLE_INTERVAL_NANOSECONDS
        invalid_samples = (
            guard_module.MemorySample.fallback(
                observed_at,
                TARGET,
                resident_size_bytes=HARD_FOOTPRINT,
                virtual_size_bytes=100 * guard_module.GIBIBYTE_BYTES,
            ),
            guard_module.MemorySample.missing(
                observed_at,
                guard_module.SampleSource.PROC_PID_RUSAGE_V4,
                "missing",
            ),
            guard_module.MemorySample.error(
                observed_at,
                guard_module.SampleSource.PROC_PID_RUSAGE_V4,
                "error",
            ),
            guard_module.MemorySample.identity_drift(
                observed_at,
                "identity changed",
                current_phys_footprint_bytes=HARD_FOOTPRINT,
            ),
        )
        for invalid_sample in invalid_samples:
            with self.subTest(status=invalid_sample.status, source=invalid_sample.source):
                samples = samples_for_footprints([HARD_FOOTPRINT] * 60)
                samples[20] = invalid_sample

                self.assertIs(
                    guard_module.MemoryDecision.WARNING,
                    self.evaluate(samples),
                )

    def test_hard_requires_a_complete_sixty_sample_window(self) -> None:
        samples = samples_for_footprints([HARD_FOOTPRINT] * 59)

        self.assertIs(guard_module.MemoryDecision.WARNING, self.evaluate(samples))


class MacOsProcessMemorySamplerTests(unittest.TestCase):
    def make_sampler(
        self,
        *,
        rusage: guard_module.MacOsRusage | None = None,
        process_group_id: int = TARGET.process_group_id,
        executable: str = TARGET.expected_executable,
    ) -> guard_module.MacOsProcessMemorySampler:
        selected_rusage = rusage or make_rusage(WARNING_FOOTPRINT)
        return guard_module.MacOsProcessMemorySampler(
            lambda _pid: selected_rusage,
            lambda _pid: executable,
            lambda _pid: process_group_id,
        )

    def test_exact_identity_produces_enforceable_current_footprint_sample(self) -> None:
        sample = self.make_sampler().sample(TARGET, 123)

        self.assertIs(guard_module.SampleSource.PROC_PID_RUSAGE_V4, sample.source)
        self.assertIs(guard_module.SampleStatus.AVAILABLE, sample.status)
        self.assertEqual(TARGET, sample.observed_identity)
        self.assertEqual(WARNING_FOOTPRINT, sample.current_phys_footprint_bytes)
        self.assertIsNone(sample.virtual_size_bytes)

    def test_each_pinned_identity_fact_is_required(self) -> None:
        changed_path = "/Library/Java/OtherJdk/Contents/Home/bin/java"
        cases = {
            "process-group": self.make_sampler(process_group_id=TARGET.process_group_id + 1),
            "start-time": self.make_sampler(
                rusage=make_rusage(
                    WARNING_FOOTPRINT,
                    start_abstime=TARGET.proc_start_abstime + 1,
                )
            ),
            "executable": self.make_sampler(executable=changed_path),
        }
        for name, sampler in cases.items():
            with self.subTest(name=name):
                sample = sampler.sample(TARGET, 123)
                self.assertIs(guard_module.SampleStatus.IDENTITY_DRIFT, sample.status)
                self.assertIsNone(
                    sample.enforceable_current_phys_footprint(TARGET)
                )

    def test_missing_process_is_recorded_without_memory_values(self) -> None:
        def missing(_pid: int) -> guard_module.MacOsRusage:
            raise ProcessLookupError(errno.ESRCH, "gone")

        sampler = guard_module.MacOsProcessMemorySampler(
            missing,
            lambda _pid: TARGET.expected_executable,
            lambda _pid: TARGET.process_group_id,
        )

        sample = sampler.sample(TARGET, 123)

        self.assertIs(guard_module.SampleStatus.MISSING, sample.status)
        self.assertIsNone(sample.current_phys_footprint_bytes)

    def test_permission_failure_is_recorded_as_non_enforceable_error(self) -> None:
        def denied(_pid: int) -> guard_module.MacOsRusage:
            raise PermissionError(errno.EPERM, "denied")

        sampler = guard_module.MacOsProcessMemorySampler(
            denied,
            lambda _pid: TARGET.expected_executable,
            lambda _pid: TARGET.process_group_id,
        )

        sample = sampler.sample(TARGET, 123)

        self.assertIs(guard_module.SampleStatus.ERROR, sample.status)
        self.assertIsNone(sample.enforceable_current_phys_footprint(TARGET))

    def test_revalidation_returns_only_an_exact_unchanged_identity(self) -> None:
        self.assertEqual(TARGET, self.make_sampler().revalidate(TARGET))
        self.assertIsNone(
            self.make_sampler(
                process_group_id=TARGET.process_group_id + 1
            ).revalidate(TARGET)
        )
        self.assertIsNone(
            self.make_sampler(
                executable="/Library/Java/Other/Contents/Home/bin/java"
            ).revalidate(TARGET)
        )

    def test_revalidation_failure_returns_none(self) -> None:
        def unavailable(_pid: int) -> guard_module.MacOsRusage:
            raise guard_module.MemorySamplingError("unavailable")

        sampler = guard_module.MacOsProcessMemorySampler(
            unavailable,
            lambda _pid: TARGET.expected_executable,
            lambda _pid: TARGET.process_group_id,
        )

        self.assertIsNone(sampler.revalidate(TARGET))

    def test_bind_captures_start_time_only_for_expected_group_and_executable(self) -> None:
        sampler = self.make_sampler()

        bound = sampler.bind(
            TARGET.pid,
            TARGET.process_group_id,
            TARGET.expected_executable,
        )

        self.assertEqual(TARGET, bound)
        self.assertIsNone(
            sampler.bind(
                TARGET.pid,
                TARGET.process_group_id + 1,
                TARGET.expected_executable,
            )
        )
        self.assertIsNone(
            sampler.bind(
                TARGET.pid,
                TARGET.process_group_id,
                "/Library/Java/Other/Contents/Home/bin/java",
            )
        )


class OwnedJavaMemoryGuardTests(unittest.TestCase):
    def test_poll_samples_no_more_than_once_per_second(self) -> None:
        fixture = GuardFixture([LOW_FOOTPRINT, LOW_FOOTPRINT])

        first = fixture.guard.poll()
        second = fixture.guard.poll()
        fixture.clock.advance()
        third = fixture.guard.poll()

        self.assertTrue(first.sampled)
        self.assertFalse(second.sampled)
        self.assertTrue(third.sampled)
        self.assertEqual(2, fixture.sampler.call_count)

    def test_long_sample_gap_resets_the_sustained_emergency_run(self) -> None:
        fixture = GuardFixture([EMERGENCY_FOOTPRINT] * 10)
        fixture.poll(9)
        fixture.clock.advance(guard_module.MAXIMUM_SAMPLE_GAP_NANOSECONDS)

        result = fixture.guard.poll()

        self.assertIs(guard_module.MemoryDecision.WARNING, result.decision)
        self.assertEqual([], fixture.stops)

    def test_warning_never_revalidates_or_stops(self) -> None:
        fixture = GuardFixture([WARNING_FOOTPRINT])

        result = fixture.guard.poll()

        self.assertIs(guard_module.MemoryDecision.WARNING, result.decision)
        self.assertIs(guard_module.StopOutcome.NOT_REQUIRED, result.stop_outcome)
        self.assertEqual([], fixture.events)

    def test_emergency_invokes_stop_once_after_immediate_exact_revalidation(self) -> None:
        fixture = GuardFixture([EMERGENCY_FOOTPRINT] * 12)

        results = fixture.poll(12)

        self.assertIs(guard_module.MemoryDecision.EMERGENCY, results[9].decision)
        self.assertIs(guard_module.StopOutcome.REQUESTED, results[9].stop_outcome)
        self.assertEqual(["revalidate", "stop"], fixture.events)
        self.assertEqual(
            [(TARGET, guard_module.MemoryDecision.EMERGENCY)],
            fixture.stops,
        )
        self.assertTrue(fixture.guard.stop_callback_invoked)
        self.assertIs(
            guard_module.StopOutcome.ALREADY_REQUESTED,
            results[-1].stop_outcome,
        )

    def test_hard_window_invokes_stop_with_hard_decision(self) -> None:
        entries = [HARD_FOOTPRINT] * 35
        entries.extend([LOW_FOOTPRINT] * 15)
        entries.extend([HARD_FOOTPRINT] * 10)
        fixture = GuardFixture(entries)

        result = fixture.poll(60)[-1]

        self.assertIs(guard_module.MemoryDecision.HARD, result.decision)
        self.assertEqual([(TARGET, guard_module.MemoryDecision.HARD)], fixture.stops)

    def test_fallback_rss_and_vsz_never_stop(self) -> None:
        def fallback(
            target: guard_module.OwnedJavaProcess,
            observed_at: int,
        ) -> guard_module.MemorySample:
            return guard_module.MemorySample.fallback(
                observed_at,
                target,
                resident_size_bytes=100 * guard_module.GIBIBYTE_BYTES,
                virtual_size_bytes=1000 * guard_module.GIBIBYTE_BYTES,
            )

        fixture = GuardFixture([fallback] * 70)

        results = fixture.poll(70)

        self.assertTrue(
            all(
                result.decision is guard_module.MemoryDecision.NOT_ENFORCEABLE
                for result in results
            )
        )
        self.assertEqual([], fixture.stops)

    def test_missing_and_error_samples_never_stop(self) -> None:
        entries: list[object] = []
        for _index in range(20):
            entries.extend(
                [
                    ProcessLookupError(errno.ESRCH, "gone"),
                    OSError(errno.EIO, "sampling failed"),
                    EMERGENCY_FOOTPRINT,
                ]
            )
        fixture = GuardFixture(entries)

        results = fixture.poll(len(entries))

        self.assertEqual([], fixture.stops)
        self.assertNotIn(
            guard_module.MemoryDecision.EMERGENCY,
            [result.decision for result in results],
        )

    def test_sampler_identity_drift_permanently_disarms_enforcement(self) -> None:
        def drift(
            _target: guard_module.OwnedJavaProcess,
            observed_at: int,
        ) -> guard_module.MemorySample:
            return guard_module.MemorySample.identity_drift(
                observed_at,
                "start time changed",
                current_phys_footprint_bytes=EMERGENCY_FOOTPRINT,
            )

        fixture = GuardFixture([drift] + [EMERGENCY_FOOTPRINT] * 10)

        results = fixture.poll(11)

        self.assertTrue(fixture.guard.enforcement_disarmed)
        self.assertEqual([], fixture.stops)
        self.assertIs(
            guard_module.StopOutcome.ENFORCEMENT_DISARMED,
            results[-1].stop_outcome,
        )

    def test_available_sample_for_another_identity_disarms_enforcement(self) -> None:
        other_identity = guard_module.OwnedJavaProcess(
            TARGET.pid,
            TARGET.process_group_id,
            TARGET.proc_start_abstime + 1,
            TARGET.expected_executable,
        )

        def wrong_identity(
            _target: guard_module.OwnedJavaProcess,
            observed_at: int,
        ) -> guard_module.MemorySample:
            return exact_sample(
                observed_at,
                EMERGENCY_FOOTPRINT,
                identity=other_identity,
            )

        fixture = GuardFixture([wrong_identity] + [EMERGENCY_FOOTPRINT] * 10)

        results = fixture.poll(11)

        self.assertIs(guard_module.SampleStatus.IDENTITY_DRIFT, results[0].sample_status)
        self.assertTrue(fixture.guard.enforcement_disarmed)
        self.assertEqual([], fixture.stops)

    def test_fallback_identity_drift_remains_labeled_as_fallback(self) -> None:
        other_identity = guard_module.OwnedJavaProcess(
            TARGET.pid,
            TARGET.process_group_id,
            TARGET.proc_start_abstime + 1,
            TARGET.expected_executable,
        )

        def wrong_identity(
            _target: guard_module.OwnedJavaProcess,
            observed_at: int,
        ) -> guard_module.MemorySample:
            return guard_module.MemorySample.fallback(
                observed_at,
                other_identity,
                resident_size_bytes=EMERGENCY_FOOTPRINT,
                virtual_size_bytes=100 * guard_module.GIBIBYTE_BYTES,
            )

        fixture = GuardFixture([wrong_identity] + [EMERGENCY_FOOTPRINT] * 10)
        fixture.poll(11)
        telemetry = json.loads(fixture.guard.telemetry_json_bytes())

        self.assertEqual("fallback", telemetry["records"][0]["source"])
        self.assertEqual("identity-drift", telemetry["records"][0]["status"])
        self.assertEqual([], fixture.stops)

    def test_revalidation_mismatch_disarms_without_calling_stop(self) -> None:
        changed_identity = guard_module.OwnedJavaProcess(
            TARGET.pid,
            TARGET.process_group_id,
            TARGET.proc_start_abstime + 1,
            TARGET.expected_executable,
        )
        fixture = GuardFixture(
            [EMERGENCY_FOOTPRINT] * 20,
            revalidated_identity=changed_identity,
        )

        results = fixture.poll(20)

        self.assertEqual(["revalidate"], fixture.events)
        self.assertEqual([], fixture.stops)
        self.assertTrue(fixture.guard.enforcement_disarmed)
        self.assertIs(
            guard_module.StopOutcome.REVALIDATION_FAILED,
            results[9].stop_outcome,
        )
        self.assertIs(
            guard_module.StopOutcome.ENFORCEMENT_DISARMED,
            results[-1].stop_outcome,
        )

    def test_revalidation_error_fails_closed_without_calling_stop(self) -> None:
        fixture = GuardFixture(
            [EMERGENCY_FOOTPRINT] * 10,
            revalidation_exception=guard_module.MemorySamplingError("lost identity"),
        )

        result = fixture.poll(10)[-1]

        self.assertIs(guard_module.StopOutcome.REVALIDATION_FAILED, result.stop_outcome)
        self.assertEqual(["revalidate"], fixture.events)
        self.assertEqual([], fixture.stops)

    def test_guard_never_stops_its_own_pid(self) -> None:
        fixture = GuardFixture(
            [EMERGENCY_FOOTPRINT] * 10,
            current_pid=TARGET.pid,
        )

        result = fixture.poll(10)[-1]

        self.assertIs(guard_module.StopOutcome.SELF_PROTECTED, result.stop_outcome)
        self.assertEqual([], fixture.events)
        self.assertEqual([], fixture.stops)

    def test_guard_never_stops_its_own_process_group(self) -> None:
        fixture = GuardFixture(
            [EMERGENCY_FOOTPRINT] * 10,
            current_process_group_id=TARGET.process_group_id,
        )

        result = fixture.poll(10)[-1]

        self.assertIs(guard_module.StopOutcome.SELF_PROTECTED, result.stop_outcome)
        self.assertEqual([], fixture.events)
        self.assertEqual([], fixture.stops)

    def test_invalid_current_process_identity_fails_closed(self) -> None:
        for current_pid, current_process_group_id in (
            (True, 51000),
            (51001, -1),
        ):
            with self.subTest(
                current_pid=current_pid,
                current_process_group_id=current_process_group_id,
            ):
                fixture = GuardFixture(
                    [EMERGENCY_FOOTPRINT] * 10,
                    current_pid=current_pid,
                    current_process_group_id=current_process_group_id,
                )

                result = fixture.poll(10)[-1]

                self.assertIs(
                    guard_module.StopOutcome.REVALIDATION_FAILED,
                    result.stop_outcome,
                )
                self.assertEqual([], fixture.events)

    def test_stale_sampler_timestamp_is_an_error_and_never_stops(self) -> None:
        def stale(
            target: guard_module.OwnedJavaProcess,
            observed_at: int,
        ) -> guard_module.MemorySample:
            return exact_sample(
                observed_at + guard_module.SAMPLE_INTERVAL_NANOSECONDS,
                EMERGENCY_FOOTPRINT,
                identity=target,
            )

        fixture = GuardFixture([stale] * 20)

        results = fixture.poll(20)

        self.assertTrue(
            all(result.sample_status is guard_module.SampleStatus.ERROR for result in results)
        )
        self.assertEqual([], fixture.stops)

    def test_stale_identity_drift_still_disarms_enforcement(self) -> None:
        def stale_drift(
            _target: guard_module.OwnedJavaProcess,
            observed_at: int,
        ) -> guard_module.MemorySample:
            return guard_module.MemorySample.identity_drift(
                observed_at + guard_module.SAMPLE_INTERVAL_NANOSECONDS,
                "identity changed",
                current_phys_footprint_bytes=EMERGENCY_FOOTPRINT,
            )

        fixture = GuardFixture([stale_drift] + [EMERGENCY_FOOTPRINT] * 10)

        results = fixture.poll(11)

        self.assertIs(guard_module.SampleStatus.IDENTITY_DRIFT, results[0].sample_status)
        self.assertTrue(fixture.guard.enforcement_disarmed)
        self.assertEqual([], fixture.stops)

    def test_stop_callback_exception_is_not_retried(self) -> None:
        fixture = GuardFixture(
            [EMERGENCY_FOOTPRINT] * 11,
            stop_exception=RuntimeError("stop failed"),
        )
        fixture.poll(9)
        with self.assertRaisesRegex(RuntimeError, "stop failed"):
            fixture.guard.poll()
        fixture.clock.advance()

        result = fixture.guard.poll()

        self.assertTrue(fixture.guard.stop_callback_invoked)
        self.assertIs(guard_module.StopOutcome.ALREADY_REQUESTED, result.stop_outcome)
        self.assertEqual(1, len(fixture.stops))


class TelemetryTests(unittest.TestCase):
    def test_serialization_is_canonical_bounded_and_distinguishes_metrics(self) -> None:
        def fallback(
            target: guard_module.OwnedJavaProcess,
            observed_at: int,
        ) -> guard_module.MemorySample:
            return guard_module.MemorySample.fallback(
                observed_at,
                target,
                resident_size_bytes=123,
                virtual_size_bytes=456,
                detail="diagnostic only",
            )

        fixture = GuardFixture([fallback])
        fixture.poll(1)

        first = fixture.guard.telemetry_json_bytes()
        second = fixture.guard.telemetry_json_bytes()
        payload = json.loads(first)
        record = payload["records"][0]

        self.assertEqual(first, second)
        self.assertLessEqual(len(first), guard_module.MAXIMUM_TELEMETRY_SIZE_BYTES)
        self.assertEqual("fallback", record["source"])
        self.assertIsNone(record["current_phys_footprint_bytes"])
        self.assertEqual(123, record["resident_size_bytes"])
        self.assertEqual(456, record["virtual_size_bytes"])
        self.assertEqual("not-enforceable", record["decision"])

    def test_telemetry_retains_a_bounded_number_of_records(self) -> None:
        sample_count = guard_module.MAXIMUM_TELEMETRY_RECORD_COUNT + 25
        fixture = GuardFixture([LOW_FOOTPRINT] * sample_count)
        fixture.poll(sample_count)

        content = fixture.guard.telemetry_json_bytes()
        payload = json.loads(content)

        self.assertLessEqual(len(content), guard_module.MAXIMUM_TELEMETRY_SIZE_BYTES)
        self.assertLessEqual(
            payload["state"]["retained_record_count"],
            guard_module.MAXIMUM_TELEMETRY_RECORD_COUNT,
        )
        self.assertEqual(sample_count, payload["state"]["sample_count"])
        self.assertEqual(
            sample_count - payload["state"]["retained_record_count"],
            payload["state"]["dropped_record_count"],
        )

    def test_large_sampling_errors_are_truncated_before_serialization(self) -> None:
        sample_count = guard_module.MAXIMUM_TELEMETRY_RECORD_COUNT
        fixture = GuardFixture(
            [OSError(errno.EIO, "☃" * 10_000)] * sample_count
        )
        fixture.poll(sample_count)

        content = fixture.guard.telemetry_json_bytes()
        payload = json.loads(content)

        self.assertLessEqual(len(content), guard_module.MAXIMUM_TELEMETRY_SIZE_BYTES)
        self.assertGreater(len(payload["records"]), 0)
        self.assertTrue(
            all(
                len(record["detail"].encode("utf-8"))
                <= guard_module.MAXIMUM_DETAIL_SIZE_BYTES
                for record in payload["records"]
            )
        )

    def test_module_has_no_process_signaling_or_profile_io_mechanism(self) -> None:
        source = MODULE_PATH.read_text(encoding="utf-8")

        self.assertNotIn("killpg", source)
        self.assertNotIn("os.kill", source)
        self.assertNotIn("import signal", source)
        self.assertNotIn("import subprocess", source)
        self.assertNotIn("Path(", source)
        self.assertNotIn("open(", source)


class FakeCFunction:
    def __init__(self, callback: object) -> None:
        self.callback = callback
        self.argtypes: object = None
        self.restype: object = None

    def __call__(self, *arguments: object) -> int:
        if not callable(self.callback):
            raise TypeError("fake C callback is not callable")
        return self.callback(*arguments)


class FakeLibproc:
    def __init__(
        self,
        rusage_result: int = 0,
        path_result: int | None = None,
        start_abstime: int = 333,
    ) -> None:
        self.rusage_result = rusage_result
        self.path_result = path_result
        self.start_abstime = start_abstime
        self.proc_pid_rusage = FakeCFunction(self._read_rusage)
        self.proc_pidpath = FakeCFunction(self._read_path)

    def _read_rusage(
        self,
        _pid: object,
        flavor: object,
        output: object,
    ) -> int:
        if self.rusage_result != 0:
            ctypes.set_errno(errno.ESRCH)
            return self.rusage_result
        if flavor != guard_module.RUSAGE_INFO_V4:
            raise AssertionError("wrong rusage flavor")
        information = ctypes.cast(
            output,
            ctypes.POINTER(guard_module._RusageInfoV4),
        ).contents
        information.ri_resident_size = 111
        information.ri_phys_footprint = 222
        information.ri_proc_start_abstime = self.start_abstime
        information.ri_lifetime_max_phys_footprint = 444
        return 0

    def _read_path(
        self,
        _pid: object,
        output: object,
        output_size: object,
    ) -> int:
        if self.path_result is not None:
            ctypes.set_errno(errno.ESRCH)
            return self.path_result
        content = TARGET.expected_executable.encode("utf-8")
        if len(content) + 1 > int(output_size):
            raise AssertionError("fake executable does not fit")
        ctypes.memmove(output, content + b"\x00", len(content) + 1)
        return len(content)


class LibprocAdapterTests(unittest.TestCase):
    def test_rusage_v4_layout_matches_the_macos_header_shape(self) -> None:
        self.assertEqual(36, len(guard_module._RusageInfoV4._fields_))
        self.assertEqual(16 + 35 * 8, ctypes.sizeof(guard_module._RusageInfoV4))

    def test_adapter_reads_exact_rusage_v4_fields_and_executable(self) -> None:
        adapter = guard_module._LibprocAdapter(FakeLibproc())

        rusage = adapter.read_rusage(TARGET.pid)
        executable = adapter.read_executable(TARGET.pid)

        self.assertEqual(111, rusage.resident_size_bytes)
        self.assertEqual(222, rusage.current_phys_footprint_bytes)
        self.assertEqual(333, rusage.proc_start_abstime)
        self.assertEqual(444, rusage.lifetime_max_phys_footprint_bytes)
        self.assertEqual(TARGET.expected_executable, executable)

    def test_adapter_maps_esrch_from_each_native_call_to_missing_process(self) -> None:
        for library in (
            FakeLibproc(rusage_result=-1),
            FakeLibproc(path_result=-1),
        ):
            adapter = guard_module._LibprocAdapter(library)
            with self.subTest(library=library):
                with self.assertRaises(ProcessLookupError):
                    if library.rusage_result != 0:
                        adapter.read_rusage(TARGET.pid)
                    else:
                        adapter.read_executable(TARGET.pid)

    def test_adapter_rejects_invalid_native_rusage_values(self) -> None:
        adapter = guard_module._LibprocAdapter(FakeLibproc(start_abstime=0))

        with self.assertRaises(guard_module.MemorySamplingError):
            adapter.read_rusage(TARGET.pid)

    def test_native_factory_rejects_non_macos_without_loading_a_library(self) -> None:
        with mock.patch.object(guard_module.sys, "platform", "linux"):
            with mock.patch.object(guard_module.ctypes, "CDLL") as load_library:
                with self.assertRaises(guard_module.MemorySamplingUnavailable):
                    guard_module.MacOsProcessMemorySampler.native()

        load_library.assert_not_called()


if __name__ == "__main__":
    unittest.main()
