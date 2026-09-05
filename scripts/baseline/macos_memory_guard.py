#!/usr/bin/env python3
"""Bound memory telemetry and stop decisions for one owned macOS Java process."""

from __future__ import annotations

from collections import deque
import ctypes
from dataclasses import dataclass
from enum import Enum
import errno
import json
import os
import sys
import time
from typing import Callable, Sequence


MEBIBYTE_BYTES = 1024 * 1024
GIBIBYTE_BYTES = 1024 * MEBIBYTE_BYTES
FOUR_GIB_HEAP_LIMIT_BYTES = 4 * GIBIBYTE_BYTES
WARNING_PHYS_FOOTPRINT_BYTES = 8 * GIBIBYTE_BYTES
HARD_PHYS_FOOTPRINT_BYTES = 12 * GIBIBYTE_BYTES
EMERGENCY_PHYS_FOOTPRINT_BYTES = 16 * GIBIBYTE_BYTES
SAMPLE_INTERVAL_NANOSECONDS = 1_000_000_000
MAXIMUM_SAMPLE_GAP_NANOSECONDS = 2 * SAMPLE_INTERVAL_NANOSECONDS
HARD_WINDOW_SAMPLE_COUNT = 60
HARD_REQUIRED_HIGH_SAMPLE_COUNT = 45
HARD_FINAL_HIGH_SAMPLE_COUNT = 10
EMERGENCY_FINAL_HIGH_SAMPLE_COUNT = 10
MAXIMUM_TELEMETRY_RECORD_COUNT = 120
MAXIMUM_TELEMETRY_SIZE_BYTES = 64 * 1024
MAXIMUM_DETAIL_SIZE_BYTES = 512
MAXIMUM_EXECUTABLE_SIZE_BYTES = 4096
RUSAGE_INFO_V4 = 4
PROC_PIDPATHINFO_MAXSIZE = 4096
MAXIMUM_UNSIGNED_64_BIT_INTEGER = (1 << 64) - 1


class MemorySamplingError(RuntimeError):
    """Reports an expected failure at the external process-sampling boundary."""


class MemorySamplingUnavailable(MemorySamplingError):
    """Reports that the native macOS sampling API is unavailable."""


class SampleSource(str, Enum):
    """Identifies whether a sample has enforceable macOS footprint semantics."""

    PROC_PID_RUSAGE_V4 = "proc-pid-rusage-v4"
    FALLBACK = "fallback"


class SampleStatus(str, Enum):
    """Describes whether a sample is usable and identity-bound."""

    AVAILABLE = "available"
    MISSING = "missing"
    ERROR = "error"
    IDENTITY_DRIFT = "identity-drift"


class MemoryDecision(str, Enum):
    """Describes the strongest policy result for the latest sample window."""

    NOT_ENFORCEABLE = "not-enforceable"
    NORMAL = "normal"
    WARNING = "warning"
    HARD = "hard"
    EMERGENCY = "emergency"


class StopOutcome(str, Enum):
    """Describes whether the injected one-shot stop callback was used."""

    NOT_REQUIRED = "not-required"
    REQUESTED = "requested"
    ALREADY_REQUESTED = "already-requested"
    REVALIDATION_FAILED = "revalidation-failed"
    SELF_PROTECTED = "self-protected"
    ENFORCEMENT_DISARMED = "enforcement-disarmed"


def _require_nonnegative_integer(value: int, name: str) -> None:
    if (
        type(value) is not int
        or value < 0
        or value > MAXIMUM_UNSIGNED_64_BIT_INTEGER
    ):
        raise ValueError(f"{name} must be one non-negative 64-bit integer")


def _require_positive_integer(value: int, name: str) -> None:
    if (
        type(value) is not int
        or value <= 0
        or value > MAXIMUM_UNSIGNED_64_BIT_INTEGER
    ):
        raise ValueError(f"{name} must be one positive 64-bit integer")


def _bounded_utf8_text(value: object, maximum_size: int) -> str:
    content = str(value).encode("utf-8", errors="replace")
    if len(content) <= maximum_size:
        return content.decode("utf-8")
    return content[:maximum_size].decode("utf-8", errors="ignore")


@dataclass(frozen=True)
class OwnedJavaProcess:
    """Pins the process facts that must still match before enforcement."""

    pid: int
    process_group_id: int
    proc_start_abstime: int
    expected_executable: str

    def __post_init__(self) -> None:
        _require_positive_integer(self.pid, "pid")
        _require_positive_integer(self.process_group_id, "process_group_id")
        _require_positive_integer(self.proc_start_abstime, "proc_start_abstime")
        if (
            not isinstance(self.expected_executable, str)
            or not os.path.isabs(self.expected_executable)
            or "\x00" in self.expected_executable
            or "\n" in self.expected_executable
            or "\r" in self.expected_executable
            or len(self.expected_executable.encode("utf-8"))
            > MAXIMUM_EXECUTABLE_SIZE_BYTES
        ):
            raise ValueError("expected_executable must be one bounded absolute path")


@dataclass(frozen=True)
class MacOsRusage:
    """Carries the distinct current and lifetime fields returned by rusage v4."""

    resident_size_bytes: int
    current_phys_footprint_bytes: int
    proc_start_abstime: int
    lifetime_max_phys_footprint_bytes: int

    def __post_init__(self) -> None:
        _require_nonnegative_integer(
            self.resident_size_bytes,
            "resident_size_bytes",
        )
        _require_nonnegative_integer(
            self.current_phys_footprint_bytes,
            "current_phys_footprint_bytes",
        )
        _require_positive_integer(self.proc_start_abstime, "proc_start_abstime")
        _require_nonnegative_integer(
            self.lifetime_max_phys_footprint_bytes,
            "lifetime_max_phys_footprint_bytes",
        )


@dataclass(frozen=True)
class MemorySample:
    """Carries one timestamped observation without conflating memory metrics."""

    observed_at_monotonic_ns: int
    source: SampleSource
    status: SampleStatus
    observed_identity: OwnedJavaProcess | None = None
    current_phys_footprint_bytes: int | None = None
    resident_size_bytes: int | None = None
    virtual_size_bytes: int | None = None
    lifetime_max_phys_footprint_bytes: int | None = None
    detail: str = ""

    def __post_init__(self) -> None:
        _require_nonnegative_integer(
            self.observed_at_monotonic_ns,
            "observed_at_monotonic_ns",
        )
        if not isinstance(self.source, SampleSource):
            raise ValueError("source must be a SampleSource")
        if not isinstance(self.status, SampleStatus):
            raise ValueError("status must be a SampleStatus")
        if self.observed_identity is not None and not isinstance(
            self.observed_identity,
            OwnedJavaProcess,
        ):
            raise ValueError("observed_identity must be an OwnedJavaProcess or None")
        for field_name in (
            "current_phys_footprint_bytes",
            "resident_size_bytes",
            "virtual_size_bytes",
            "lifetime_max_phys_footprint_bytes",
        ):
            field_value = getattr(self, field_name)
            if field_value is not None:
                _require_nonnegative_integer(field_value, field_name)
        if not isinstance(self.detail, str):
            raise ValueError("detail must be a string")
        if len(self.detail.encode("utf-8")) > MAXIMUM_DETAIL_SIZE_BYTES:
            raise ValueError("detail exceeds its telemetry bound")
        if self.source is SampleSource.FALLBACK and (
            self.current_phys_footprint_bytes is not None
            or self.lifetime_max_phys_footprint_bytes is not None
        ):
            raise ValueError("fallback samples cannot claim physical-footprint fields")
        if self.status is SampleStatus.AVAILABLE:
            if self.observed_identity is None:
                raise ValueError("available samples require an observed identity")
            if (
                self.source is SampleSource.PROC_PID_RUSAGE_V4
                and self.current_phys_footprint_bytes is None
            ):
                raise ValueError("rusage v4 samples require a current physical footprint")

    @classmethod
    def proc_pid_rusage_v4(
        cls,
        observed_at_monotonic_ns: int,
        identity: OwnedJavaProcess,
        rusage: MacOsRusage,
    ) -> MemorySample:
        """Creates an identity-bound sample with enforceable current footprint data."""

        return cls(
            observed_at_monotonic_ns=observed_at_monotonic_ns,
            source=SampleSource.PROC_PID_RUSAGE_V4,
            status=SampleStatus.AVAILABLE,
            observed_identity=identity,
            current_phys_footprint_bytes=rusage.current_phys_footprint_bytes,
            resident_size_bytes=rusage.resident_size_bytes,
            lifetime_max_phys_footprint_bytes=(
                rusage.lifetime_max_phys_footprint_bytes
            ),
        )

    @classmethod
    def fallback(
        cls,
        observed_at_monotonic_ns: int,
        identity: OwnedJavaProcess,
        *,
        resident_size_bytes: int | None = None,
        virtual_size_bytes: int | None = None,
        detail: str = "",
    ) -> MemorySample:
        """Creates diagnostic-only RSS or VSZ telemetry that cannot enforce."""

        return cls(
            observed_at_monotonic_ns=observed_at_monotonic_ns,
            source=SampleSource.FALLBACK,
            status=SampleStatus.AVAILABLE,
            observed_identity=identity,
            resident_size_bytes=resident_size_bytes,
            virtual_size_bytes=virtual_size_bytes,
            detail=_bounded_utf8_text(detail, MAXIMUM_DETAIL_SIZE_BYTES),
        )

    @classmethod
    def missing(
        cls,
        observed_at_monotonic_ns: int,
        source: SampleSource,
        detail: str,
    ) -> MemorySample:
        """Creates a non-enforceable observation for a process that disappeared."""

        return cls(
            observed_at_monotonic_ns=observed_at_monotonic_ns,
            source=source,
            status=SampleStatus.MISSING,
            detail=_bounded_utf8_text(detail, MAXIMUM_DETAIL_SIZE_BYTES),
        )

    @classmethod
    def error(
        cls,
        observed_at_monotonic_ns: int,
        source: SampleSource,
        detail: str,
    ) -> MemorySample:
        """Creates a non-enforceable observation for a sampling error."""

        return cls(
            observed_at_monotonic_ns=observed_at_monotonic_ns,
            source=source,
            status=SampleStatus.ERROR,
            detail=_bounded_utf8_text(detail, MAXIMUM_DETAIL_SIZE_BYTES),
        )

    @classmethod
    def identity_drift(
        cls,
        observed_at_monotonic_ns: int,
        detail: str,
        *,
        source: SampleSource = SampleSource.PROC_PID_RUSAGE_V4,
        current_phys_footprint_bytes: int | None = None,
        resident_size_bytes: int | None = None,
        lifetime_max_phys_footprint_bytes: int | None = None,
    ) -> MemorySample:
        """Creates a non-enforceable observation after any identity mismatch."""

        return cls(
            observed_at_monotonic_ns=observed_at_monotonic_ns,
            source=source,
            status=SampleStatus.IDENTITY_DRIFT,
            current_phys_footprint_bytes=current_phys_footprint_bytes,
            resident_size_bytes=resident_size_bytes,
            lifetime_max_phys_footprint_bytes=(
                lifetime_max_phys_footprint_bytes
            ),
            detail=_bounded_utf8_text(detail, MAXIMUM_DETAIL_SIZE_BYTES),
        )

    def enforceable_current_phys_footprint(
        self,
        expected_identity: OwnedJavaProcess,
    ) -> int | None:
        """Returns only current rusage-v4 footprint bound to the exact target."""

        if (
            self.source is not SampleSource.PROC_PID_RUSAGE_V4
            or self.status is not SampleStatus.AVAILABLE
            or self.observed_identity != expected_identity
        ):
            return None
        return self.current_phys_footprint_bytes


@dataclass(frozen=True)
class MemoryPolicy:
    """Defines strict current-footprint thresholds and sustained sample windows."""

    heap_limit_bytes: int
    warning_phys_footprint_bytes: int
    hard_phys_footprint_bytes: int
    emergency_phys_footprint_bytes: int
    sample_interval_nanoseconds: int
    maximum_sample_gap_nanoseconds: int
    hard_window_sample_count: int
    hard_required_high_sample_count: int
    hard_final_high_sample_count: int
    emergency_final_high_sample_count: int

    def __post_init__(self) -> None:
        for field_name in (
            "heap_limit_bytes",
            "warning_phys_footprint_bytes",
            "hard_phys_footprint_bytes",
            "emergency_phys_footprint_bytes",
            "sample_interval_nanoseconds",
            "maximum_sample_gap_nanoseconds",
            "hard_window_sample_count",
            "hard_required_high_sample_count",
            "hard_final_high_sample_count",
            "emergency_final_high_sample_count",
        ):
            _require_positive_integer(getattr(self, field_name), field_name)
        if not (
            self.heap_limit_bytes < self.warning_phys_footprint_bytes
            < self.hard_phys_footprint_bytes
            < self.emergency_phys_footprint_bytes
        ):
            raise ValueError("memory policy thresholds must be strictly increasing")
        if self.maximum_sample_gap_nanoseconds <= self.sample_interval_nanoseconds:
            raise ValueError("maximum sample gap must exceed the sample interval")
        if not (
            self.hard_final_high_sample_count
            <= self.hard_required_high_sample_count
            <= self.hard_window_sample_count
        ):
            raise ValueError("hard sample counts are inconsistent")
        if self.emergency_final_high_sample_count > self.hard_window_sample_count:
            raise ValueError("emergency sample count exceeds the retained policy window")

    def evaluate(
        self,
        samples: Sequence[MemorySample],
        expected_identity: OwnedJavaProcess,
    ) -> MemoryDecision:
        """Evaluates only current, exact-source, exact-identity footprint samples."""

        if not samples:
            return MemoryDecision.NOT_ENFORCEABLE
        current_footprint = samples[-1].enforceable_current_phys_footprint(
            expected_identity
        )
        if current_footprint is None:
            return MemoryDecision.NOT_ENFORCEABLE

        emergency_samples = samples[-self.emergency_final_high_sample_count :]
        if len(emergency_samples) == self.emergency_final_high_sample_count and all(
            (footprint := sample.enforceable_current_phys_footprint(expected_identity))
            is not None
            and footprint > self.emergency_phys_footprint_bytes
            for sample in emergency_samples
        ):
            return MemoryDecision.EMERGENCY

        hard_samples = samples[-self.hard_window_sample_count :]
        final_hard_samples = hard_samples[-self.hard_final_high_sample_count :]
        hard_footprints = [
            sample.enforceable_current_phys_footprint(expected_identity)
            for sample in hard_samples
        ]
        hard_high_count = sum(
            1
            for footprint in hard_footprints
            if footprint is not None
            and footprint > self.hard_phys_footprint_bytes
        )
        if (
            len(hard_samples) == self.hard_window_sample_count
            and all(footprint is not None for footprint in hard_footprints)
            and hard_high_count >= self.hard_required_high_sample_count
            and len(final_hard_samples) == self.hard_final_high_sample_count
            and all(
                (
                    footprint := sample.enforceable_current_phys_footprint(
                        expected_identity
                    )
                )
                is not None
                and footprint > self.hard_phys_footprint_bytes
                for sample in final_hard_samples
            )
        ):
            return MemoryDecision.HARD
        if current_footprint > self.warning_phys_footprint_bytes:
            return MemoryDecision.WARNING
        return MemoryDecision.NORMAL


FOUR_GIB_CLIENT_MEMORY_POLICY = MemoryPolicy(
    heap_limit_bytes=FOUR_GIB_HEAP_LIMIT_BYTES,
    warning_phys_footprint_bytes=WARNING_PHYS_FOOTPRINT_BYTES,
    hard_phys_footprint_bytes=HARD_PHYS_FOOTPRINT_BYTES,
    emergency_phys_footprint_bytes=EMERGENCY_PHYS_FOOTPRINT_BYTES,
    sample_interval_nanoseconds=SAMPLE_INTERVAL_NANOSECONDS,
    maximum_sample_gap_nanoseconds=MAXIMUM_SAMPLE_GAP_NANOSECONDS,
    hard_window_sample_count=HARD_WINDOW_SAMPLE_COUNT,
    hard_required_high_sample_count=HARD_REQUIRED_HIGH_SAMPLE_COUNT,
    hard_final_high_sample_count=HARD_FINAL_HIGH_SAMPLE_COUNT,
    emergency_final_high_sample_count=EMERGENCY_FINAL_HIGH_SAMPLE_COUNT,
)


@dataclass(frozen=True)
class GuardPollResult:
    """Reports one non-blocking poll without exposing mutable guard state."""

    sampled: bool
    decision: MemoryDecision
    stop_outcome: StopOutcome
    sample_status: SampleStatus | None


@dataclass(frozen=True)
class _TelemetryRecord:
    sample: MemorySample
    decision: MemoryDecision
    stop_outcome: StopOutcome


class _RusageInfoV4(ctypes.Structure):
    _fields_ = [
        ("ri_uuid", ctypes.c_uint8 * 16),
        *[
            (field_name, ctypes.c_uint64)
            for field_name in (
                "ri_user_time",
                "ri_system_time",
                "ri_pkg_idle_wkups",
                "ri_interrupt_wkups",
                "ri_pageins",
                "ri_wired_size",
                "ri_resident_size",
                "ri_phys_footprint",
                "ri_proc_start_abstime",
                "ri_proc_exit_abstime",
                "ri_child_user_time",
                "ri_child_system_time",
                "ri_child_pkg_idle_wkups",
                "ri_child_interrupt_wkups",
                "ri_child_pageins",
                "ri_child_elapsed_abstime",
                "ri_diskio_bytesread",
                "ri_diskio_byteswritten",
                "ri_cpu_time_qos_default",
                "ri_cpu_time_qos_maintenance",
                "ri_cpu_time_qos_background",
                "ri_cpu_time_qos_utility",
                "ri_cpu_time_qos_legacy",
                "ri_cpu_time_qos_user_initiated",
                "ri_cpu_time_qos_user_interactive",
                "ri_billed_system_time",
                "ri_serviced_system_time",
                "ri_logical_writes",
                "ri_lifetime_max_phys_footprint",
                "ri_instructions",
                "ri_cycles",
                "ri_billed_energy",
                "ri_serviced_energy",
                "ri_interval_max_phys_footprint",
                "ri_runnable_time",
            )
        ],
    ]


class _LibprocAdapter:
    def __init__(self, library: object) -> None:
        try:
            self._proc_pid_rusage = getattr(library, "proc_pid_rusage")
            self._proc_pidpath = getattr(library, "proc_pidpath")
        except AttributeError as exception:
            raise MemorySamplingUnavailable(
                "libproc does not export the required process APIs"
            ) from exception
        self._proc_pid_rusage.argtypes = (
            ctypes.c_int,
            ctypes.c_int,
            ctypes.c_void_p,
        )
        self._proc_pid_rusage.restype = ctypes.c_int
        self._proc_pidpath.argtypes = (
            ctypes.c_int,
            ctypes.c_void_p,
            ctypes.c_uint32,
        )
        self._proc_pidpath.restype = ctypes.c_int

    @classmethod
    def native(cls) -> _LibprocAdapter:
        if sys.platform != "darwin":
            raise MemorySamplingUnavailable(
                "proc_pid_rusage memory sampling requires macOS"
            )
        try:
            library = ctypes.CDLL("/usr/lib/libproc.dylib", use_errno=True)
        except OSError as exception:
            raise MemorySamplingUnavailable(
                f"cannot load macOS libproc: {exception}"
            ) from exception
        return cls(library)

    def read_rusage(self, pid: int) -> MacOsRusage:
        information = _RusageInfoV4()
        ctypes.set_errno(0)
        result = self._proc_pid_rusage(
            pid,
            RUSAGE_INFO_V4,
            ctypes.byref(information),
        )
        if result != 0:
            error_number = ctypes.get_errno()
            if error_number == errno.ESRCH:
                raise ProcessLookupError(error_number, os.strerror(error_number))
            raise OSError(error_number, os.strerror(error_number))
        try:
            return MacOsRusage(
                resident_size_bytes=int(information.ri_resident_size),
                current_phys_footprint_bytes=int(information.ri_phys_footprint),
                proc_start_abstime=int(information.ri_proc_start_abstime),
                lifetime_max_phys_footprint_bytes=int(
                    information.ri_lifetime_max_phys_footprint
                ),
            )
        except ValueError as exception:
            raise MemorySamplingError(
                f"proc_pid_rusage returned invalid values: {exception}"
            ) from exception

    def read_executable(self, pid: int) -> str:
        buffer = ctypes.create_string_buffer(PROC_PIDPATHINFO_MAXSIZE)
        ctypes.set_errno(0)
        result = self._proc_pidpath(pid, buffer, len(buffer))
        if result <= 0:
            error_number = ctypes.get_errno()
            if error_number == errno.ESRCH:
                raise ProcessLookupError(error_number, os.strerror(error_number))
            raise OSError(error_number, os.strerror(error_number))
        executable = os.fsdecode(buffer.raw[:result].split(b"\x00", 1)[0])
        if not executable:
            raise MemorySamplingError("proc_pidpath returned an empty executable path")
        return executable


class MacOsProcessMemorySampler:
    """Samples one target with injectable readers or the native libproc adapter."""

    def __init__(
        self,
        rusage_reader: Callable[[int], MacOsRusage],
        executable_reader: Callable[[int], str],
        process_group_reader: Callable[[int], int],
    ) -> None:
        self._rusage_reader = rusage_reader
        self._executable_reader = executable_reader
        self._process_group_reader = process_group_reader

    @classmethod
    def native(cls) -> MacOsProcessMemorySampler:
        """Creates a sampler backed by macOS libproc and ``os.getpgid``."""

        adapter = _LibprocAdapter.native()
        return cls(
            adapter.read_rusage,
            adapter.read_executable,
            os.getpgid,
        )

    def sample(
        self,
        target: OwnedJavaProcess,
        observed_at_monotonic_ns: int,
    ) -> MemorySample:
        """Returns one exact-source sample or a non-enforceable failure record."""

        try:
            rusage = self._rusage_reader(target.pid)
            process_group_id = self._process_group_reader(target.pid)
            executable = self._executable_reader(target.pid)
        except ProcessLookupError as exception:
            return MemorySample.missing(
                observed_at_monotonic_ns,
                SampleSource.PROC_PID_RUSAGE_V4,
                f"target process is missing: {exception}",
            )
        except (MemorySamplingError, PermissionError, OSError) as exception:
            return MemorySample.error(
                observed_at_monotonic_ns,
                SampleSource.PROC_PID_RUSAGE_V4,
                f"cannot sample target process: {exception}",
            )
        if not isinstance(rusage, MacOsRusage):
            raise TypeError("rusage_reader must return MacOsRusage")
        if type(process_group_id) is not int or not isinstance(executable, str):
            raise TypeError("identity readers returned an invalid value")
        mismatches: list[str] = []
        if process_group_id != target.process_group_id:
            mismatches.append(
                f"pgid expected {target.process_group_id}, observed {process_group_id}"
            )
        if rusage.proc_start_abstime != target.proc_start_abstime:
            mismatches.append(
                "start abstime expected "
                f"{target.proc_start_abstime}, observed {rusage.proc_start_abstime}"
            )
        if executable != target.expected_executable:
            mismatches.append(
                f"executable expected {target.expected_executable}, observed {executable}"
            )
        if mismatches:
            return MemorySample.identity_drift(
                observed_at_monotonic_ns,
                "; ".join(mismatches),
                current_phys_footprint_bytes=(
                    rusage.current_phys_footprint_bytes
                ),
                resident_size_bytes=rusage.resident_size_bytes,
                lifetime_max_phys_footprint_bytes=(
                    rusage.lifetime_max_phys_footprint_bytes
                ),
            )
        return MemorySample.proc_pid_rusage_v4(
            observed_at_monotonic_ns,
            target,
            rusage,
        )

    def bind(
        self,
        pid: int,
        process_group_id: int,
        expected_executable: str,
    ) -> OwnedJavaProcess | None:
        """Captures start abstime only when PID, PGID, and executable all match."""

        _require_positive_integer(pid, "pid")
        _require_positive_integer(process_group_id, "process_group_id")
        try:
            rusage = self._rusage_reader(pid)
            observed_process_group_id = self._process_group_reader(pid)
            observed_executable = self._executable_reader(pid)
        except (MemorySamplingError, OSError):
            return None
        if not isinstance(rusage, MacOsRusage):
            raise TypeError("rusage_reader must return MacOsRusage")
        if (
            type(observed_process_group_id) is not int
            or not isinstance(observed_executable, str)
            or observed_process_group_id != process_group_id
            or observed_executable != expected_executable
        ):
            return None
        return OwnedJavaProcess(
            pid=pid,
            process_group_id=process_group_id,
            proc_start_abstime=rusage.proc_start_abstime,
            expected_executable=expected_executable,
        )

    def bind_observed(
        self,
        pid: int,
        expected_process_group_id: int,
    ) -> OwnedJavaProcess | None:
        """Pins a process using only identity facts observed from the kernel."""

        _require_positive_integer(pid, "pid")
        _require_positive_integer(
            expected_process_group_id,
            "expected_process_group_id",
        )
        try:
            first_rusage = self._rusage_reader(pid)
            process_group_id = self._process_group_reader(pid)
            executable = self._executable_reader(pid)
            second_rusage = self._rusage_reader(pid)
            second_process_group_id = self._process_group_reader(pid)
            second_executable = self._executable_reader(pid)
        except (MemorySamplingError, OSError):
            return None
        if not isinstance(first_rusage, MacOsRusage) or not isinstance(
            second_rusage,
            MacOsRusage,
        ):
            raise TypeError("rusage_reader must return MacOsRusage")
        if (
            type(process_group_id) is not int
            or type(second_process_group_id) is not int
            or not isinstance(executable, str)
            or not isinstance(second_executable, str)
            or process_group_id != expected_process_group_id
            or second_process_group_id != process_group_id
            or second_executable != executable
            or second_rusage.proc_start_abstime != first_rusage.proc_start_abstime
        ):
            return None
        try:
            return OwnedJavaProcess(
                pid=pid,
                process_group_id=process_group_id,
                proc_start_abstime=first_rusage.proc_start_abstime,
                expected_executable=executable,
            )
        except ValueError as exception:
            raise MemorySamplingError(
                f"cannot bind observed process identity: {exception}"
            ) from exception

    def bind_current_process(self) -> OwnedJavaProcess:
        """Binds this process to the executable identity reported by the kernel."""

        pid = os.getpid()
        try:
            rusage = self._rusage_reader(pid)
            process_group_id = self._process_group_reader(pid)
            executable = self._executable_reader(pid)
        except (MemorySamplingError, OSError) as exception:
            raise MemorySamplingError(
                f"cannot bind the current process identity: {exception}"
            ) from exception
        if not isinstance(rusage, MacOsRusage):
            raise TypeError("rusage_reader must return MacOsRusage")
        if type(process_group_id) is not int or not isinstance(executable, str):
            raise TypeError("identity readers returned an invalid value")
        try:
            return OwnedJavaProcess(
                pid=pid,
                process_group_id=process_group_id,
                proc_start_abstime=rusage.proc_start_abstime,
                expected_executable=executable,
            )
        except ValueError as exception:
            raise MemorySamplingError(
                f"cannot bind the current process identity: {exception}"
            ) from exception

    def revalidate(self, target: OwnedJavaProcess) -> OwnedJavaProcess | None:
        """Returns the unchanged target only while every pinned identity fact matches."""

        try:
            rusage = self._rusage_reader(target.pid)
            process_group_id = self._process_group_reader(target.pid)
            executable = self._executable_reader(target.pid)
        except (MemorySamplingError, OSError):
            return None
        if (
            isinstance(rusage, MacOsRusage)
            and type(process_group_id) is int
            and isinstance(executable, str)
            and process_group_id == target.process_group_id
            and rusage.proc_start_abstime == target.proc_start_abstime
            and executable == target.expected_executable
        ):
            return target
        return None

    def revalidate_intrinsic_identity(
        self,
        target: OwnedJavaProcess,
    ) -> OwnedJavaProcess | None:
        """Revalidates immutable process identity while allowing group migration."""

        try:
            first_rusage = self._rusage_reader(target.pid)
            first_executable = self._executable_reader(target.pid)
            second_rusage = self._rusage_reader(target.pid)
            second_executable = self._executable_reader(target.pid)
        except (MemorySamplingError, OSError):
            return None
        if (
            isinstance(first_rusage, MacOsRusage)
            and isinstance(second_rusage, MacOsRusage)
            and isinstance(first_executable, str)
            and isinstance(second_executable, str)
            and first_rusage.proc_start_abstime == target.proc_start_abstime
            and second_rusage.proc_start_abstime == target.proc_start_abstime
            and first_executable == target.expected_executable
            and second_executable == target.expected_executable
        ):
            return target
        return None


class OwnedJavaMemoryGuard:
    """Polls one owned Java process and delegates at most one verified stop request."""

    def __init__(
        self,
        target: OwnedJavaProcess,
        sample_process: Callable[[OwnedJavaProcess, int], MemorySample],
        revalidate_identity: Callable[
            [OwnedJavaProcess], OwnedJavaProcess | None
        ],
        stop_owned_process: Callable[[OwnedJavaProcess, MemoryDecision], None],
        *,
        policy: MemoryPolicy = FOUR_GIB_CLIENT_MEMORY_POLICY,
        monotonic_ns: Callable[[], int] = time.monotonic_ns,
        current_pid: Callable[[], int] = os.getpid,
        current_process_group_id: Callable[[], int] = os.getpgrp,
    ) -> None:
        self._target = target
        self._sample_process = sample_process
        self._revalidate_identity = revalidate_identity
        self._stop_owned_process = stop_owned_process
        self._policy = policy
        self._monotonic_ns = monotonic_ns
        self._current_pid = current_pid
        self._current_process_group_id = current_process_group_id
        self._policy_samples: deque[MemorySample] = deque(
            maxlen=policy.hard_window_sample_count
        )
        self._telemetry: deque[_TelemetryRecord] = deque(
            maxlen=MAXIMUM_TELEMETRY_RECORD_COUNT
        )
        self._last_sample_at_monotonic_ns: int | None = None
        self._sample_count = 0
        self._enforcement_disarmed = False
        self._stop_callback_invoked = False
        self._last_stop_outcome = StopOutcome.NOT_REQUIRED

    @property
    def enforcement_disarmed(self) -> bool:
        """Reports whether an identity or self-protection failure disabled stopping."""

        return self._enforcement_disarmed

    @property
    def stop_callback_invoked(self) -> bool:
        """Reports whether the injected callback has consumed its one invocation."""

        return self._stop_callback_invoked

    def poll(self) -> GuardPollResult:
        """Takes at most one due sample and applies the sustained-footprint policy."""

        observed_at = self._monotonic_ns()
        _require_nonnegative_integer(observed_at, "monotonic_ns result")
        if self._last_sample_at_monotonic_ns is not None:
            elapsed = observed_at - self._last_sample_at_monotonic_ns
            if elapsed < self._policy.sample_interval_nanoseconds:
                return GuardPollResult(
                    sampled=False,
                    decision=MemoryDecision.NOT_ENFORCEABLE,
                    stop_outcome=StopOutcome.NOT_REQUIRED,
                    sample_status=None,
                )
            if elapsed >= self._policy.maximum_sample_gap_nanoseconds:
                self._policy_samples.clear()
        self._last_sample_at_monotonic_ns = observed_at
        sample = self._take_sample(observed_at)
        if sample.status is SampleStatus.IDENTITY_DRIFT:
            self._enforcement_disarmed = True
        self._policy_samples.append(sample)
        self._sample_count = min(self._sample_count + 1, (1 << 63) - 1)
        decision = self._policy.evaluate(tuple(self._policy_samples), self._target)
        try:
            stop_outcome = self._request_stop(decision)
        finally:
            self._telemetry.append(
                _TelemetryRecord(
                    sample=sample,
                    decision=decision,
                    stop_outcome=self._last_stop_outcome,
                )
            )
        return GuardPollResult(
            sampled=True,
            decision=decision,
            stop_outcome=stop_outcome,
            sample_status=sample.status,
        )

    def telemetry_json_bytes(self) -> bytes:
        """Serializes a canonical, size-bounded in-memory telemetry snapshot."""

        records = [
            self._telemetry_record_payload(record) for record in self._telemetry
        ]
        while True:
            payload = {
                "schema": 1,
                "target": self._identity_payload(self._target),
                "policy": self._policy_payload(),
                "state": {
                    "enforcement_disarmed": self._enforcement_disarmed,
                    "stop_callback_invoked": self._stop_callback_invoked,
                    "sample_count": self._sample_count,
                    "retained_record_count": len(records),
                    "dropped_record_count": max(
                        self._sample_count - len(records),
                        0,
                    ),
                    "last_stop_outcome": self._last_stop_outcome.value,
                },
                "records": records,
            }
            content = json.dumps(
                payload,
                ensure_ascii=True,
                separators=(",", ":"),
                sort_keys=True,
            ).encode("utf-8")
            if len(content) <= MAXIMUM_TELEMETRY_SIZE_BYTES:
                return content
            if not records:
                raise RuntimeError("bounded telemetry header exceeds its size limit")
            records.pop(0)

    def _take_sample(self, observed_at: int) -> MemorySample:
        try:
            sample = self._sample_process(self._target, observed_at)
        except ProcessLookupError as exception:
            return MemorySample.missing(
                observed_at,
                SampleSource.PROC_PID_RUSAGE_V4,
                f"target process is missing: {exception}",
            )
        except (MemorySamplingError, PermissionError, OSError) as exception:
            return MemorySample.error(
                observed_at,
                SampleSource.PROC_PID_RUSAGE_V4,
                f"cannot sample target process: {exception}",
            )
        if not isinstance(sample, MemorySample):
            raise TypeError("sample_process must return MemorySample")
        if (
            sample.status is SampleStatus.IDENTITY_DRIFT
            or (
                sample.status is SampleStatus.AVAILABLE
                and sample.observed_identity != self._target
            )
        ):
            return MemorySample.identity_drift(
                observed_at,
                sample.detail
                or "sampler returned an available sample for another identity",
                source=sample.source,
                current_phys_footprint_bytes=(
                    sample.current_phys_footprint_bytes
                    if sample.source is SampleSource.PROC_PID_RUSAGE_V4
                    else None
                ),
                resident_size_bytes=sample.resident_size_bytes,
                lifetime_max_phys_footprint_bytes=(
                    sample.lifetime_max_phys_footprint_bytes
                    if sample.source is SampleSource.PROC_PID_RUSAGE_V4
                    else None
                ),
            )
        if sample.observed_at_monotonic_ns != observed_at:
            return MemorySample.error(
                observed_at,
                sample.source,
                "sampler returned a stale or future timestamp",
            )
        return sample

    def _request_stop(self, decision: MemoryDecision) -> StopOutcome:
        if decision not in (MemoryDecision.HARD, MemoryDecision.EMERGENCY):
            self._last_stop_outcome = StopOutcome.NOT_REQUIRED
            return self._last_stop_outcome
        if self._stop_callback_invoked:
            self._last_stop_outcome = StopOutcome.ALREADY_REQUESTED
            return self._last_stop_outcome
        if self._enforcement_disarmed:
            self._last_stop_outcome = StopOutcome.ENFORCEMENT_DISARMED
            return self._last_stop_outcome
        try:
            guard_pid = self._current_pid()
            guard_process_group_id = self._current_process_group_id()
        except OSError:
            self._enforcement_disarmed = True
            self._last_stop_outcome = StopOutcome.REVALIDATION_FAILED
            return self._last_stop_outcome
        if (
            type(guard_pid) is not int
            or guard_pid <= 0
            or type(guard_process_group_id) is not int
            or guard_process_group_id <= 0
        ):
            self._enforcement_disarmed = True
            self._last_stop_outcome = StopOutcome.REVALIDATION_FAILED
            return self._last_stop_outcome
        if (
            self._target.pid == guard_pid
            or self._target.process_group_id == guard_process_group_id
        ):
            self._enforcement_disarmed = True
            self._last_stop_outcome = StopOutcome.SELF_PROTECTED
            return self._last_stop_outcome
        try:
            revalidated_identity = self._revalidate_identity(self._target)
        except (MemorySamplingError, OSError):
            self._enforcement_disarmed = True
            self._last_stop_outcome = StopOutcome.REVALIDATION_FAILED
            return self._last_stop_outcome
        if revalidated_identity != self._target:
            self._enforcement_disarmed = True
            self._last_stop_outcome = StopOutcome.REVALIDATION_FAILED
            return self._last_stop_outcome
        self._stop_callback_invoked = True
        self._last_stop_outcome = StopOutcome.REQUESTED
        self._stop_owned_process(self._target, decision)
        return self._last_stop_outcome

    @staticmethod
    def _identity_payload(identity: OwnedJavaProcess) -> dict[str, object]:
        return {
            "pid": identity.pid,
            "process_group_id": identity.process_group_id,
            "proc_start_abstime": identity.proc_start_abstime,
            "expected_executable": identity.expected_executable,
        }

    def _policy_payload(self) -> dict[str, int]:
        return {
            "heap_limit_bytes": self._policy.heap_limit_bytes,
            "warning_phys_footprint_bytes": (
                self._policy.warning_phys_footprint_bytes
            ),
            "hard_phys_footprint_bytes": self._policy.hard_phys_footprint_bytes,
            "emergency_phys_footprint_bytes": (
                self._policy.emergency_phys_footprint_bytes
            ),
            "sample_interval_nanoseconds": self._policy.sample_interval_nanoseconds,
            "maximum_sample_gap_nanoseconds": (
                self._policy.maximum_sample_gap_nanoseconds
            ),
            "hard_window_sample_count": self._policy.hard_window_sample_count,
            "hard_required_high_sample_count": (
                self._policy.hard_required_high_sample_count
            ),
            "hard_final_high_sample_count": (
                self._policy.hard_final_high_sample_count
            ),
            "emergency_final_high_sample_count": (
                self._policy.emergency_final_high_sample_count
            ),
        }

    def _telemetry_record_payload(
        self,
        record: _TelemetryRecord,
    ) -> dict[str, object]:
        sample = record.sample
        identity_matches_target = (
            None
            if sample.observed_identity is None
            else sample.observed_identity == self._target
        )
        return {
            "observed_at_monotonic_ns": sample.observed_at_monotonic_ns,
            "source": sample.source.value,
            "status": sample.status.value,
            "identity_matches_target": identity_matches_target,
            "current_phys_footprint_bytes": sample.current_phys_footprint_bytes,
            "resident_size_bytes": sample.resident_size_bytes,
            "virtual_size_bytes": sample.virtual_size_bytes,
            "lifetime_max_phys_footprint_bytes": (
                sample.lifetime_max_phys_footprint_bytes
            ),
            "detail": sample.detail,
            "decision": record.decision.value,
            "stop_outcome": record.stop_outcome.value,
        }
