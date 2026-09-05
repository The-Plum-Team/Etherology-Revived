from __future__ import annotations

import copy
from dataclasses import dataclass
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import time
import unittest
from unittest import mock


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_server_contract_v21 as contract_v21
import forge_server_slitherite_evidence_v21 as evidence
import test_forge_server_attrahite_evidence_v19 as v19_fixture


@dataclass(frozen=True)
class Fixture:
    root: Path
    profile: Path
    runtime: Path
    game: Path
    scenario: Path


JAVA_PID = 54_321
LAUNCH_PROCESS_GROUP_ID = 43_210
WRAPPER_PID = 43_211
MONITOR_PID = 60_001
WATCHDOG_PID = 60_002
CONTROLLER_PID = 50_000
CONTROLLER_PROCESS_GROUP_ID = 50_001
PROCESS_START_ABSTIME = 987_654_321
JAVA_EXECUTABLE = "/test/jdk17/bin/java"
GRADLE_JAVA_EXECUTABLE = "/test/jdk21/bin/java"
WRAPPER_PROCESS_START_ABSTIME = 876_543_210
ANCHOR_PROCESS_START_ABSTIME = 765_432_109
RUN_TOKEN = "a" * 64
ANCHOR_TOKEN = "b" * 64
ANCHOR_ARGUMENTS_SHA256 = "c" * 64


def write_json(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2) + "\n",
        encoding="utf-8",
    )


def write_canonical_json(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(
        (
            json.dumps(
                value,
                ensure_ascii=True,
                separators=(",", ":"),
                sort_keys=True,
            )
            + "\n"
        ).encode("utf-8")
    )


def rewrite_json_preserving_time(path: Path, value: dict[str, object]) -> None:
    metadata = path.stat()
    write_json(path, value)
    os.utime(path, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))


def valid_report() -> dict[str, object]:
    report = v19_fixture.valid_report()
    report["schema"] = contract_v21.REPORT_SCHEMA
    report["profile_id"] = evidence.PROFILE_ID
    report["scenario"] = evidence.SCENARIO_ID
    report["slitherite_blocks"] = contract_v21.build_slitherite_blocks()
    reload_result = report["reload"]
    assert isinstance(reload_result, dict)
    for field_name in contract_v21._SLITHERITE_RELOAD_FIELDS:
        reload_result[field_name] = True
    report["assertions"] = [
        {
            "name": name,
            "passed": True,
            "expected": value,
            "actual": value,
        }
        for name, value in evidence.EXPECTED_ASSERTIONS
    ]
    return report


def valid_target() -> dict[str, object]:
    return {
        "pid": JAVA_PID,
        "process_group_id": LAUNCH_PROCESS_GROUP_ID,
        "proc_start_abstime": PROCESS_START_ABSTIME,
        "expected_executable": JAVA_EXECUTABLE,
    }


def available_record(timestamp: int, footprint: int = 256 * 1024 * 1024) -> dict[str, object]:
    decision = (
        "warning"
        if footprint > evidence.WARNING_PHYS_FOOTPRINT_BYTES
        else "normal"
    )
    return {
        "observed_at_monotonic_ns": timestamp,
        "source": "proc-pid-rusage-v4",
        "status": "available",
        "identity_matches_target": True,
        "current_phys_footprint_bytes": footprint,
        "resident_size_bytes": footprint // 2,
        "virtual_size_bytes": None,
        "lifetime_max_phys_footprint_bytes": footprint,
        "detail": "",
        "decision": decision,
        "stop_outcome": "not-required",
    }


def missing_record(timestamp: int) -> dict[str, object]:
    return {
        "observed_at_monotonic_ns": timestamp,
        "source": "proc-pid-rusage-v4",
        "status": "missing",
        "identity_matches_target": None,
        "current_phys_footprint_bytes": None,
        "resident_size_bytes": None,
        "virtual_size_bytes": None,
        "lifetime_max_phys_footprint_bytes": None,
        "detail": "target process is missing: [Errno 3] No such process",
        "decision": "not-enforceable",
        "stop_outcome": "not-required",
    }


def telemetry_for_records(
    records: list[dict[str, object]],
    *,
    dropped_count: int = 0,
) -> dict[str, object]:
    sample_count = len(records) + dropped_count
    return {
        "schema": 1,
        "target": valid_target(),
        "policy": copy.deepcopy(evidence.MEMORY_POLICY),
        "state": {
            "enforcement_disarmed": False,
            "stop_callback_invoked": False,
            "sample_count": sample_count,
            "retained_record_count": len(records),
            "dropped_record_count": dropped_count,
            "last_stop_outcome": "not-required",
        },
        "records": records,
    }


def valid_telemetry(terminal_status: str = "missing") -> dict[str, object]:
    records = [
        available_record(1_000_000_000),
        available_record(2_000_000_000),
    ]
    if terminal_status == "missing":
        records.append(missing_record(3_000_000_000))
    elif terminal_status != "available":
        raise ValueError(f"Unsupported terminal status: {terminal_status}")
    return telemetry_for_records(records)


def write_guard_artifacts(
    runtime: Path,
    terminal_status: str = "missing",
) -> None:
    write_json(
        runtime / evidence.HANDOFF_FILE_NAME,
        {
            "schema": 1,
            "run_token": RUN_TOKEN,
            "pid": JAVA_PID,
            "executable": JAVA_EXECUTABLE,
            "java_feature": 17,
            "maximum_heap_bytes": evidence.SERVER_MAXIMUM_HEAP_BYTES,
            "maximum_heap_arguments": [evidence.SERVER_MAXIMUM_HEAP_ARGUMENT],
        },
    )
    (runtime / evidence.ACKNOWLEDGEMENT_FILE_NAME).write_bytes(
        f"token={RUN_TOKEN}\n".encode("ascii")
    )
    write_json(
        runtime / evidence.READINESS_FILE_NAME,
        {
            "schema": 1,
            "status": "ready",
            "memory_policy_name": evidence.MEMORY_POLICY_PROFILE,
            "monitor_pid": MONITOR_PID,
            "target": valid_target(),
            "telemetry": str(runtime / evidence.TELEMETRY_FILE_NAME),
        },
    )
    telemetry = valid_telemetry(terminal_status)
    content = json.dumps(
        telemetry,
        ensure_ascii=True,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    (runtime / evidence.TELEMETRY_FILE_NAME).write_bytes(content + b"\n")


def watchdog_identity(
    pid: int,
    process_start_abstime: int,
    executable: str = JAVA_EXECUTABLE,
) -> dict[str, object]:
    return {
        "pid": pid,
        "process_group_id": LAUNCH_PROCESS_GROUP_ID,
        "proc_start_abstime": process_start_abstime,
        "expected_executable": executable,
    }


def watchdog_missing_sample(
    identity: dict[str, object] | None = None,
) -> dict[str, object]:
    sample = {
        "source": "proc-pid-rusage-v4",
        "status": "missing",
        "current_phys_footprint_bytes": None,
        "detail": "target process is missing: [Errno 3] No such process",
    }
    if identity is None:
        return {"pid": None, **sample}
    return {"expected_identity": identity, **sample}


def write_watchdog_artifacts(runtime: Path) -> None:
    anchor = watchdog_identity(
        LAUNCH_PROCESS_GROUP_ID,
        ANCHOR_PROCESS_START_ABSTIME,
        GRADLE_JAVA_EXECUTABLE,
    )
    wrapper = watchdog_identity(
        WRAPPER_PID,
        WRAPPER_PROCESS_START_ABSTIME,
        GRADLE_JAVA_EXECUTABLE,
    )
    server = valid_target()
    ready_at = 2_500_000_000
    observed_at = 1_000_000_000_000_000
    write_json(
        runtime / evidence.WATCHDOG_READINESS_FILE_NAME,
        {
            "schema": evidence.WATCHDOG_READINESS_SCHEMA,
            "watchdog_pid": WATCHDOG_PID,
            "controller_pid": CONTROLLER_PID,
            "controller_process_group_id": CONTROLLER_PROCESS_GROUP_ID,
            "anchor": anchor,
            "owned_session_id": LAUNCH_PROCESS_GROUP_ID,
            "heartbeat_timeout_nanoseconds": (
                evidence.WATCHDOG_HEARTBEAT_TIMEOUT_NANOSECONDS
            ),
            "ready_at_monotonic_ns": ready_at,
            "enforcement_active": True,
        },
    )
    write_json(
        runtime / evidence.WATCHDOG_TELEMETRY_FILE_NAME,
        {
            "schema": evidence.WATCHDOG_SCHEMA,
            "status": "normal",
            "decision": "exit",
            "reason": None,
            "observed_at_monotonic_ns": observed_at,
            "anchor": anchor,
            "owned_session_id": LAUNCH_PROCESS_GROUP_ID,
            "controller_pid": CONTROLLER_PID,
            "controller_process_group_id": CONTROLLER_PROCESS_GROUP_ID,
            "controller_heartbeat": {
                "status": "healthy",
                "last_received_at_monotonic_ns": observed_at - 100_000_000,
                "age_nanoseconds": 100_000_000,
            },
            "limits": {
                "maximum_java_process_count": (
                    evidence.MAXIMUM_WATCHDOG_IDENTITY_COUNT
                ),
                "per_process_current_phys_footprint_bytes": (
                    evidence.WATCHDOG_PER_PROCESS_CEILING_BYTES
                ),
                "aggregate_current_phys_footprint_bytes": (
                    evidence.WATCHDOG_AGGREGATE_CEILING_BYTES
                ),
            },
            "java_inventory": [],
            "external_java_process_ids": [],
            "tracked_exact_identities": [],
            "samples": [
                watchdog_missing_sample(),
                watchdog_missing_sample(),
                watchdog_missing_sample(),
            ],
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
                    watchdog_missing_sample(anchor),
                    watchdog_missing_sample(wrapper),
                    watchdog_missing_sample(server),
                ],
                "exact_identity_sample_error": None,
            },
        },
    )


def write_anchor_artifacts(runtime: Path) -> None:
    runtime_information = runtime.stat()
    shared = {
        "argv_sha256": ANCHOR_ARGUMENTS_SHA256,
        "token": ANCHOR_TOKEN,
    }
    write_canonical_json(
        runtime / evidence.ANCHOR_READINESS_FILE_NAME,
        {
            **shared,
            "controller_pid": CONTROLLER_PID,
            "java_feature": evidence.ANCHOR_JAVA_FEATURE,
            "pid": LAUNCH_PROCESS_GROUP_ID,
            "pre_start_timeout_seconds": 30,
            "runtime_device": runtime_information.st_dev,
            "runtime_inode": runtime_information.st_ino,
            "schema": evidence.ANCHOR_READINESS_SCHEMA,
        },
    )
    write_canonical_json(
        runtime / evidence.ANCHOR_START_FILE_NAME,
        {
            **shared,
            "schema": evidence.ANCHOR_START_SCHEMA,
        },
    )
    write_canonical_json(
        runtime / evidence.ANCHOR_CHILD_STARTED_FILE_NAME,
        {
            **shared,
            "executable": GRADLE_JAVA_EXECUTABLE,
            "pid": WRAPPER_PID,
            "schema": evidence.ANCHOR_CHILD_STARTED_SCHEMA,
        },
    )
    write_canonical_json(
        runtime / evidence.ANCHOR_CHILD_RESULT_FILE_NAME,
        {
            **shared,
            "exit_code": 0,
            "pid": WRAPPER_PID,
            "schema": evidence.ANCHOR_CHILD_RESULT_SCHEMA,
            "started": True,
        },
    )
    write_canonical_json(
        runtime / evidence.ANCHOR_FINISH_FILE_NAME,
        {
            **shared,
            "child_exit_code": 0,
            "child_pid": WRAPPER_PID,
            "child_started": True,
            "schema": evidence.ANCHOR_FINISH_SCHEMA,
        },
    )


def set_watchdog_history_size(runtime: Path, identity_count: int) -> None:
    if identity_count < 3:
        raise ValueError("The synthetic history must retain its three core JVMs")
    telemetry_path = runtime / evidence.WATCHDOG_TELEMETRY_FILE_NAME
    telemetry = json.loads(telemetry_path.read_text(encoding="utf-8"))
    terminal = telemetry["terminal_attestation"]
    exact_samples = terminal["exact_identity_samples"]
    extra_samples = []
    for offset in range(identity_count - 3):
        identity = watchdog_identity(
            WRAPPER_PID + offset + 1,
            WRAPPER_PROCESS_START_ABSTIME + offset + 1,
            GRADLE_JAVA_EXECUTABLE,
        )
        extra_samples.append(watchdog_missing_sample(identity))
    exact_samples[2:2] = extra_samples
    telemetry["samples"] = [
        watchdog_missing_sample() for _sample in exact_samples
    ]
    write_json(telemetry_path, telemetry)


def build_fixture(
    root: Path,
    terminal_status: str = "missing",
) -> Fixture:
    repository = root / "repository"
    profile = repository / evidence.PROFILE_MANIFEST_RELATIVE_PATH
    profile.parent.mkdir(parents=True, exist_ok=True)
    snapshot = (
        evidence.REPOSITORY_ROOT / contract_v21.PROFILE_SNAPSHOT_RELATIVE_PATH
    )
    profile.write_bytes(snapshot.read_bytes())
    profile_record = evidence.ProfileRecord(
        relative_path=evidence.PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
        size=profile.stat().st_size,
        sha256=evidence.sha256_file(profile),
    )

    runtime = repository / evidence.RUNTIME_RELATIVE_PATH
    game = runtime / "game"
    for relative_directory in (
        "config",
        "crash-reports",
        "logs",
        "mods",
        "world",
    ):
        (game / relative_directory).mkdir(parents=True, exist_ok=True)
    (game / "eula.txt").write_bytes(b"eula=true\n")
    (game / "server.properties").write_bytes(b"level-name=world\n")
    (game / "world" / "level.dat").write_bytes(b"synthetic-level-data")
    log_content = v19_fixture.valid_server_log()
    (game / "logs" / "latest.log").write_bytes(log_content)
    write_json(
        runtime / evidence.PROFILE_MARKER_NAME,
        evidence.expected_profile_marker(profile_record),
    )
    write_guard_artifacts(runtime, terminal_status)
    write_watchdog_artifacts(runtime)
    write_anchor_artifacts(runtime)
    for file_name, specification in evidence.STAGED_LAUNCH_INPUTS.items():
        source = (
            evidence.REPOSITORY_ROOT
            / specification["repository_relative_path"]
        )
        staged = runtime / file_name
        shutil.copyfile(source, staged)
        staged.chmod(0o400)
    watchdog_provenance = {
        file_name: {
            "relative_path": file_name,
            "size": (runtime / file_name).stat().st_size,
            "sha256": evidence.sha256_file(runtime / file_name),
        }
        for file_name in evidence.RUNTIME_WATCHDOG_FILE_NAMES
    }
    anchor_provenance = {
        file_name: {
            "relative_path": file_name,
            "size": (runtime / file_name).stat().st_size,
            "sha256": evidence.sha256_file(runtime / file_name),
        }
        for file_name in evidence.RUNTIME_ANCHOR_FILE_NAMES
    }

    scenario = runtime / "evidence" / evidence.SCENARIO_ID
    reports = scenario / "reports"
    logs = scenario / "logs"
    reports.mkdir(parents=True)
    logs.mkdir()
    report_path = reports / "report.json"
    log_path = logs / "latest.log"
    launcher_path = reports / "launcher-result.json"
    done_path = reports / "done.marker"
    write_json(report_path, valid_report())
    log_path.write_bytes(log_content)
    write_json(
        launcher_path,
        {
            "schema": 1,
            "profile_id": evidence.PROFILE_ID,
            "scenario": evidence.SCENARIO_ID,
            "task_path": evidence.TASK_PATH,
            "exit_code": 0,
            "timed_out": False,
            "profile_manifest": {
                "relative_path": profile_record.relative_path,
                "size": profile_record.size,
                "sha256": profile_record.sha256,
            },
            "server_log": {
                "relative_path": "logs/latest.log",
                "size": log_path.stat().st_size,
                "sha256": evidence.sha256_file(log_path),
            },
            "launch_watchdog": watchdog_provenance,
            "launch_anchor": anchor_provenance,
        },
    )
    done_path.write_bytes(b"complete\n")
    base_time = time.time_ns() - 10_000_000_000
    for index, path in enumerate(
        (report_path, log_path, launcher_path, done_path),
        start=1,
    ):
        timestamp = base_time + index * 1_000_000_000
        os.utime(path, ns=(timestamp, timestamp))
    return Fixture(root, profile, runtime, game, scenario)


def refresh_launcher_provenance(
    fixture: Fixture,
    mapping_name: str,
    file_names: tuple[str, ...],
) -> None:
    launcher_path = fixture.scenario / "reports" / "launcher-result.json"
    launcher = json.loads(launcher_path.read_text(encoding="utf-8"))
    provenance = launcher[mapping_name]
    for file_name in file_names:
        artifact_path = fixture.runtime / file_name
        provenance[file_name] = {
            "relative_path": file_name,
            "size": artifact_path.stat().st_size,
            "sha256": evidence.sha256_file(artifact_path),
        }
    rewrite_json_preserving_time(launcher_path, launcher)


def copy_capture(fixture: Fixture) -> Path:
    archive = fixture.root / "repository" / evidence.ARCHIVE_RELATIVE_PATH
    archive.parent.mkdir(parents=True)
    shutil.copytree(fixture.scenario, archive, copy_function=shutil.copy2)
    guard_root = archive / evidence.MEMORY_GUARD_DIRECTORY_NAME
    guard_root.mkdir()
    for file_name in evidence.RUNTIME_GUARD_FILE_NAMES:
        shutil.copy2(fixture.runtime / file_name, guard_root / file_name)
    watchdog_root = archive / evidence.LAUNCH_WATCHDOG_DIRECTORY_NAME
    watchdog_root.mkdir()
    for file_name in evidence.RUNTIME_WATCHDOG_FILE_NAMES:
        shutil.copy2(fixture.runtime / file_name, watchdog_root / file_name)
    anchor_root = archive / evidence.LAUNCH_ANCHOR_DIRECTORY_NAME
    anchor_root.mkdir()
    for file_name in evidence.RUNTIME_ANCHOR_FILE_NAMES:
        shutil.copy2(fixture.runtime / file_name, anchor_root / file_name)
    for file_name in evidence.RUNTIME_STAGED_LAUNCH_INPUT_FILE_NAMES:
        shutil.copy2(fixture.runtime / file_name, anchor_root / file_name)
    return archive


def create_archive(fixture: Fixture) -> Path:
    archive = copy_capture(fixture)
    evidence.write_archive_manifest(
        fixture.profile,
        archive,
        fixture.runtime,
        expected_archive_root=archive,
        expected_runtime=fixture.runtime,
    )
    return archive


def mutate_json(path: Path, mutation) -> None:
    value = json.loads(path.read_text(encoding="utf-8"))
    mutation(value)
    rewrite_json_preserving_time(path, value)


def mutate_canonical_json(path: Path, mutation) -> None:
    metadata = path.stat()
    value = json.loads(path.read_text(encoding="utf-8"))
    mutation(value)
    write_canonical_json(path, value)
    os.utime(path, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))


def reseal_archive_record(archive: Path, relative_path: str) -> None:
    manifest_path = archive / evidence.ARCHIVE_MANIFEST_NAME
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    payload = archive / relative_path
    manifest["files"][relative_path] = {
        "size": payload.stat().st_size,
        "sha256": evidence.sha256_file(payload),
    }
    write_json(manifest_path, manifest)


def refresh_archived_launcher_provenance(
    archive: Path,
    mapping_name: str,
    directory_name: str,
    file_name: str,
) -> None:
    launcher_path = archive / "reports" / "launcher-result.json"
    launcher = json.loads(launcher_path.read_text(encoding="utf-8"))
    artifact_path = archive / directory_name / file_name
    launcher[mapping_name][file_name] = {
        "relative_path": file_name,
        "size": artifact_path.stat().st_size,
        "sha256": evidence.sha256_file(artifact_path),
    }
    write_json(launcher_path, launcher)
    reseal_archive_record(archive, f"{directory_name}/{file_name}")
    reseal_archive_record(archive, "reports/launcher-result.json")


class ContractTests(unittest.TestCase):
    def test_v21_verifier_owns_the_exact_immutable_contract(self) -> None:
        source = Path(evidence.__file__).read_text(encoding="utf-8")

        self.assertIn("import forge_server_contract_v21 as contract_v21", source)
        self.assertNotRegex(source, r"(?m)^import forge_server$")
        self.assertNotIn("import forge_server_launch_watchdog", source)
        self.assertNotIn("import forge_server_launch_anchor", source)
        self.assertNotIn("import macos_guarded_java", source)
        self.assertNotIn("import macos_memory_guard", source)
        self.assertEqual(215, len(contract_v21.SLITHERITE_ASSERTION_NAMES))
        self.assertEqual(310, len(contract_v21.contract_v19.EXPECTED_ASSERTION_NAMES))
        self.assertEqual(525, len(evidence.EXPECTED_ASSERTIONS))
        self.assertEqual(
            Path(
                "docs/evidence/forge-1.20.1/"
                "slitherite-block-registry-server-v21"
            ),
            evidence.ARCHIVE_RELATIVE_PATH,
        )
        self.assertEqual(4478, contract_v21.PROFILE_MANIFEST_SIZE)
        self.assertEqual(
            "8df3f9c15f03c1ea9d5b6adea71ee352e9a2735ecdd22e26d03033d62f49f764",
            contract_v21.PROFILE_MANIFEST_SHA256,
        )
        self.assertEqual(3, evidence.MAXIMUM_WATCHDOG_IDENTITY_COUNT)
        self.assertEqual(
            16,
            evidence.MAXIMUM_WATCHDOG_HISTORICAL_IDENTITY_COUNT,
        )
        self.assertEqual(
            tuple(contract_v21.LAUNCH_ANCHOR_POLICY["artifact_file_names"]),
            evidence.RUNTIME_ANCHOR_FILE_NAMES,
        )
        anchor_policy = contract_v21.LAUNCH_ANCHOR_POLICY
        wrapper_policy = anchor_policy["gradle_wrapper_child"]
        expected_staged_inputs = {}
        for file_name, policy in (
            (
                evidence.STAGED_ANCHOR_SOURCE_FILE_NAME,
                anchor_policy["java_source"],
            ),
            (evidence.STAGED_WRAPPER_JAR_FILE_NAME, wrapper_policy["jar"]),
            (
                evidence.STAGED_WRAPPER_PROPERTIES_FILE_NAME,
                wrapper_policy["properties"],
            ),
        ):
            expected_staged_inputs[file_name] = {
                "repository_relative_path": policy["relative_path"],
                "size": policy["size"],
                "sha256": policy["sha256"],
            }
        self.assertEqual(expected_staged_inputs, evidence.STAGED_LAUNCH_INPUTS)

    def test_strict_two_gibibyte_memory_policy_is_exact(self) -> None:
        self.assertEqual("strict-2g-v1", evidence.MEMORY_POLICY_PROFILE)
        self.assertEqual(
            {
                "heap_limit_bytes": 2 * 1024 * 1024 * 1024,
                "warning_phys_footprint_bytes": 3 * 1024 * 1024 * 1024,
                "hard_phys_footprint_bytes": 4 * 1024 * 1024 * 1024,
                "emergency_phys_footprint_bytes": 5 * 1024 * 1024 * 1024,
                "sample_interval_nanoseconds": 1_000_000_000,
                "maximum_sample_gap_nanoseconds": 2_000_000_000,
                "hard_window_sample_count": 15,
                "hard_required_high_sample_count": 10,
                "hard_final_high_sample_count": 5,
                "emergency_final_high_sample_count": 1,
            },
            evidence.MEMORY_POLICY,
        )
        self.assertEqual(
            evidence.MEMORY_POLICY_PROFILE,
            evidence.MEMORY_GUARD_ATTESTATION["policy_profile"],
        )

    def test_exact_schema_12_report_and_v19_projection_are_accepted(self) -> None:
        report = valid_report()

        evidence.validate_report(report)
        projected = contract_v21._v19_baseline(report)

        self.assertEqual(525, len(report["assertions"]))
        self.assertEqual(310, len(projected["assertions"]))
        self.assertNotIn("slitherite_blocks", projected)

    def test_focused_slitherite_report_drift_is_rejected(self) -> None:
        mutations = (
            lambda report: report["slitherite_blocks"]["block_ids"].pop(),
            lambda report: report["slitherite_blocks"]["entries"][
                contract_v21.SLITHERITE_BLOCK_IDS[0]
            ]["state_raw_ids"].__setitem__(0, -1),
            lambda report: report["slitherite_blocks"]["loaded_data"][
                "related_recipes"
            ].pop(contract_v21.SLITHERITE_RELATED_RECIPE_IDS[-1]),
            lambda report: report["reload"].__setitem__(
                "slitherite_block_placement_stable",
                False,
            ),
            lambda report: report["assertions"].pop(),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index):
                report = valid_report()
                mutation(report)
                with self.assertRaisesRegex(evidence.EvidenceError, "v21 contract"):
                    evidence.validate_report(report)


class LiveEvidenceTests(unittest.TestCase):
    def test_terminal_authoritative_missing_telemetry_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary), "missing")

            summary = evidence.validate_live_runtime(
                fixture.runtime,
                fixture.profile,
                expected_runtime=fixture.runtime,
            )

            self.assertEqual(evidence.PROFILE_ID, summary.profile_id)
            self.assertEqual(525, summary.assertion_count)

    def test_terminal_available_telemetry_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary), "available")

            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "terminal authoritative missing sample",
            ):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_runtime_root_inventory_is_exact(self) -> None:
        cases = ("missing", "extra", "linked")
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                if case == "missing":
                    (fixture.runtime / evidence.ACKNOWLEDGEMENT_FILE_NAME).unlink()
                elif case == "extra":
                    (fixture.runtime / "unexpected.txt").write_text("extra\n")
                else:
                    target = fixture.root / "foreign.json"
                    target.write_text("{}\n")
                    readiness = fixture.runtime / evidence.READINESS_FILE_NAME
                    readiness.unlink()
                    readiness.symlink_to(target)

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "inventory|linked|symlink",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_live_staged_launch_inputs_require_exact_bytes_and_permissions(self) -> None:
        cases = ("missing", "tampered", "writable", "multiply-linked")
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                staged = (
                    fixture.runtime / evidence.STAGED_ANCHOR_SOURCE_FILE_NAME
                )
                if case == "missing":
                    staged.unlink()
                elif case == "tampered":
                    staged.chmod(0o600)
                    staged.write_bytes(staged.read_bytes() + b"\n")
                    staged.chmod(0o400)
                elif case == "writable":
                    staged.chmod(0o600)
                else:
                    linked = fixture.root / "linked-anchor.java"
                    staged.chmod(0o600)
                    linked.hardlink_to(staged)
                    staged.chmod(0o400)

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "inventory|staged launch input|multiply linked",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_handoff_identity_heap_token_and_fields_are_exact(self) -> None:
        mutations = (
            lambda value: value.__setitem__("schema", True),
            lambda value: value.__setitem__("run_token", "A" * 64),
            lambda value: value.__setitem__("pid", True),
            lambda value: value.__setitem__("executable", "bin/java"),
            lambda value: value.__setitem__("java_feature", 21),
            lambda value: value.__setitem__("maximum_heap_bytes", 4096 * 1024 * 1024),
            lambda value: value.__setitem__("maximum_heap_arguments", ["-Xmx2048M"]),
            lambda value: value.__setitem__(
                "maximum_heap_arguments",
                ["-Xmx2048m", "-Xmx2048m"],
            ),
            lambda value: value.__setitem__("unexpected", True),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                mutate_json(fixture.runtime / evidence.HANDOFF_FILE_NAME, mutation)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_acknowledgement_and_readiness_cross_links_are_exact(self) -> None:
        readiness_mutations = (
            lambda value: value.__setitem__("schema", True),
            lambda value: value.__setitem__("status", "starting"),
            lambda value: value.__setitem__("memory_policy_name", "strict-2g-v2"),
            lambda value: value.__setitem__("memory_policy_name", True),
            lambda value: value.pop("memory_policy_name"),
            lambda value: value.__setitem__("monitor_pid", JAVA_PID),
            lambda value: value["target"].__setitem__("pid", JAVA_PID + 1),
            lambda value: value["target"].__setitem__(
                "process_group_id",
                JAVA_PID,
            ),
            lambda value: value["target"].__setitem__(
                "proc_start_abstime",
                True,
            ),
            lambda value: value["target"].__setitem__(
                "expected_executable",
                "/other/bin/java",
            ),
            lambda value: value.__setitem__("telemetry", "/foreign/telemetry.json"),
            lambda value: value.__setitem__("unexpected", True),
        )
        for index, mutation in enumerate(readiness_mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                mutate_json(fixture.runtime / evidence.READINESS_FILE_NAME, mutation)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            (fixture.runtime / evidence.ACKNOWLEDGEMENT_FILE_NAME).write_text(
                f"token={'b' * 64}\n",
                encoding="ascii",
            )
            with self.assertRaisesRegex(evidence.EvidenceError, "acknowledgement"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_guard_json_requires_newline_and_size_bounds(self) -> None:
        for file_name in (
            evidence.HANDOFF_FILE_NAME,
            evidence.READINESS_FILE_NAME,
            evidence.TELEMETRY_FILE_NAME,
            evidence.WATCHDOG_READINESS_FILE_NAME,
            evidence.WATCHDOG_TELEMETRY_FILE_NAME,
            *evidence.RUNTIME_ANCHOR_FILE_NAMES,
        ):
            with self.subTest(file_name=file_name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                path = fixture.runtime / file_name
                path.write_bytes(path.read_bytes().rstrip(b"\n"))
                with self.assertRaisesRegex(evidence.EvidenceError, "newline"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            (fixture.runtime / evidence.TELEMETRY_FILE_NAME).write_bytes(
                b"x" * (evidence.MAXIMUM_TELEMETRY_SIZE + 1)
            )
            with self.assertRaisesRegex(evidence.EvidenceError, "oversized"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_launch_watchdog_terminal_contract_is_exact(self) -> None:
        cases = (
            (
                evidence.WATCHDOG_READINESS_FILE_NAME,
                lambda value: value.__setitem__("schema", "watchdog-v2"),
            ),
            (
                evidence.WATCHDOG_READINESS_FILE_NAME,
                lambda value: value.__setitem__("enforcement_active", False),
            ),
            (
                evidence.WATCHDOG_READINESS_FILE_NAME,
                lambda value: value.__setitem__(
                    "heartbeat_timeout_nanoseconds",
                    evidence.WATCHDOG_HEARTBEAT_TIMEOUT_NANOSECONDS + 1,
                ),
            ),
            (
                evidence.WATCHDOG_READINESS_FILE_NAME,
                lambda value: value["anchor"].__setitem__(
                    "process_group_id",
                    LAUNCH_PROCESS_GROUP_ID + 1,
                ),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value.__setitem__("status", "failed"),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value["limits"].__setitem__(
                    "maximum_java_process_count",
                    evidence.MAXIMUM_WATCHDOG_HISTORICAL_IDENTITY_COUNT,
                ),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value["limits"].__setitem__(
                    "aggregate_current_phys_footprint_bytes",
                    evidence.WATCHDOG_AGGREGATE_CEILING_BYTES + 1,
                ),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value.__setitem__(
                    "java_inventory",
                    [JAVA_PID],
                ),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value.__setitem__("owned_group_absent", False),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value.__setitem__(
                    "signal_actions",
                    ["owned-group-signaled"],
                ),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value["terminal_attestation"].__setitem__(
                    "global_java_inventory",
                    [JAVA_PID],
                ),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value["terminal_attestation"][
                    "exact_identity_samples"
                ].pop(),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value["terminal_attestation"][
                    "exact_identity_samples"
                ][0].__setitem__("source", "fallback"),
            ),
        )
        for file_name, mutation in cases:
            with self.subTest(file_name=file_name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                mutate_json(fixture.runtime / file_name, mutation)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_launch_watchdog_terminal_timestamp_follows_memory_guard(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            mutate_json(
                fixture.runtime / evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value.__setitem__(
                    "observed_at_monotonic_ns",
                    2_000_000_000,
                ),
            )

            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "terminal identity",
            ):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_launcher_binds_the_exact_terminal_watchdog_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            readiness = fixture.runtime / evidence.WATCHDOG_READINESS_FILE_NAME
            readiness.write_bytes(readiness.read_bytes()[:-1] + b" \n")

            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "watchdog artifact differs from launcher provenance",
            ):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_launcher_watchdog_provenance_shape_is_exact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            launcher = fixture.scenario / "reports" / "launcher-result.json"
            mutate_json(
                launcher,
                lambda value: value["launch_watchdog"][
                    evidence.WATCHDOG_TELEMETRY_FILE_NAME
                ].__setitem__("sha256", "0" * 64),
            )

            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "watchdog artifact differs from launcher provenance",
            ):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_launch_anchor_artifact_contract_and_cross_links_are_exact(self) -> None:
        cases = (
            (
                evidence.ANCHOR_READINESS_FILE_NAME,
                lambda value: value.__setitem__("schema", "anchor-ready-v2"),
            ),
            (
                evidence.ANCHOR_READINESS_FILE_NAME,
                lambda value: value.__setitem__(
                    "pid",
                    LAUNCH_PROCESS_GROUP_ID + 1,
                ),
            ),
            (
                evidence.ANCHOR_READINESS_FILE_NAME,
                lambda value: value.__setitem__("java_feature", 17),
            ),
            (
                evidence.ANCHOR_READINESS_FILE_NAME,
                lambda value: value.__setitem__(
                    "controller_pid",
                    CONTROLLER_PID + 1,
                ),
            ),
            (
                evidence.ANCHOR_READINESS_FILE_NAME,
                lambda value: value.__setitem__(
                    "pre_start_timeout_seconds",
                    31,
                ),
            ),
            (
                evidence.ANCHOR_READINESS_FILE_NAME,
                lambda value: value.__setitem__("runtime_device", True),
            ),
            (
                evidence.ANCHOR_READINESS_FILE_NAME,
                lambda value: value.__setitem__(
                    "runtime_inode",
                    value["runtime_inode"] + 1,
                ),
            ),
            (
                evidence.ANCHOR_START_FILE_NAME,
                lambda value: value.__setitem__("token", "d" * 64),
            ),
            (
                evidence.ANCHOR_CHILD_STARTED_FILE_NAME,
                lambda value: value.__setitem__("argv_sha256", "d" * 64),
            ),
            (
                evidence.ANCHOR_CHILD_STARTED_FILE_NAME,
                lambda value: value.__setitem__("pid", JAVA_PID),
            ),
            (
                evidence.ANCHOR_CHILD_STARTED_FILE_NAME,
                lambda value: value.__setitem__(
                    "executable",
                    JAVA_EXECUTABLE,
                ),
            ),
            (
                evidence.ANCHOR_CHILD_RESULT_FILE_NAME,
                lambda value: value.__setitem__("exit_code", 1),
            ),
            (
                evidence.ANCHOR_CHILD_RESULT_FILE_NAME,
                lambda value: value.__setitem__("started", False),
            ),
            (
                evidence.ANCHOR_FINISH_FILE_NAME,
                lambda value: value.__setitem__("child_pid", WRAPPER_PID + 1),
            ),
            (
                evidence.ANCHOR_FINISH_FILE_NAME,
                lambda value: value.__setitem__("unexpected", True),
            ),
        )
        for file_name, mutation in cases:
            with self.subTest(file_name=file_name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                mutate_canonical_json(fixture.runtime / file_name, mutation)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_launch_anchor_artifacts_require_canonical_bytes(self) -> None:
        for file_name in evidence.RUNTIME_ANCHOR_FILE_NAMES:
            with self.subTest(file_name=file_name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                path = fixture.runtime / file_name
                value = json.loads(path.read_text(encoding="utf-8"))
                write_json(path, value)

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "canonical JSON",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_anchor_child_must_be_in_terminal_watchdog_history(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            telemetry_path = (
                fixture.runtime / evidence.WATCHDOG_TELEMETRY_FILE_NAME
            )
            mutate_json(
                telemetry_path,
                lambda value: value["terminal_attestation"][
                    "exact_identity_samples"
                ][1]["expected_identity"].__setitem__(
                    "pid",
                    WRAPPER_PID + 1,
                ),
            )
            refresh_launcher_provenance(
                fixture,
                "launch_watchdog",
                evidence.RUNTIME_WATCHDOG_FILE_NAMES,
            )

            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "absent from watchdog identity history",
            ):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_watchdog_history_uses_sixteen_identity_lifetime_bound(self) -> None:
        for identity_count in (
            4,
            evidence.MAXIMUM_WATCHDOG_HISTORICAL_IDENTITY_COUNT,
        ):
            with self.subTest(identity_count=identity_count), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                set_watchdog_history_size(fixture.runtime, identity_count)
                refresh_launcher_provenance(
                    fixture,
                    "launch_watchdog",
                    evidence.RUNTIME_WATCHDOG_FILE_NAMES,
                )

                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            set_watchdog_history_size(
                fixture.runtime,
                evidence.MAXIMUM_WATCHDOG_HISTORICAL_IDENTITY_COUNT + 1,
            )
            refresh_launcher_provenance(
                fixture,
                "launch_watchdog",
                evidence.RUNTIME_WATCHDOG_FILE_NAMES,
            )
            with self.assertRaisesRegex(evidence.EvidenceError, "terminal state"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_launcher_anchor_provenance_shape_and_bytes_are_exact(self) -> None:
        mutations = (
            lambda value: value["launch_anchor"][
                evidence.ANCHOR_READINESS_FILE_NAME
            ].__setitem__("sha256", "0" * 64),
            lambda value: value["launch_anchor"][
                evidence.ANCHOR_CHILD_STARTED_FILE_NAME
            ].__setitem__("relative_path", "foreign/child.json"),
            lambda value: value["launch_anchor"].pop(
                evidence.ANCHOR_FINISH_FILE_NAME
            ),
            lambda value: value["launch_anchor"][
                evidence.ANCHOR_START_FILE_NAME
            ].__setitem__("unexpected", True),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                launcher = fixture.scenario / "reports" / "launcher-result.json"
                mutate_json(launcher, mutation)

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "anchor (artifact differs|provenance is malformed)",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_telemetry_policy_target_and_state_are_exact(self) -> None:
        structural_mutations = (
            lambda value: value.__setitem__("schema", True),
            lambda value: value["target"].__setitem__("pid", JAVA_PID + 1),
            lambda value: value["state"].__setitem__("enforcement_disarmed", True),
            lambda value: value["state"].__setitem__("stop_callback_invoked", True),
            lambda value: value["state"].__setitem__("last_stop_outcome", "requested"),
            lambda value: value["state"].__setitem__("retained_record_count", 1),
            lambda value: value["state"].__setitem__("dropped_record_count", 1),
            lambda value: value.__setitem__("unexpected", True),
        )
        for index, mutation in enumerate(structural_mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                mutate_json(fixture.runtime / evidence.TELEMETRY_FILE_NAME, mutation)
                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        for field_name, expected in evidence.MEMORY_POLICY.items():
            with self.subTest(policy_field=field_name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))

                def mutate_policy(value: dict[str, object]) -> None:
                    value["policy"][field_name] = expected + 1

                mutate_json(
                    fixture.runtime / evidence.TELEMETRY_FILE_NAME,
                    mutate_policy,
                )
                with self.assertRaisesRegex(evidence.EvidenceError, "policy"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

        for name, mutation in (
            ("missing", lambda policy: policy.pop("heap_limit_bytes")),
            ("extra", lambda policy: policy.__setitem__("unexpected", 1)),
            ("boolean", lambda policy: policy.__setitem__("hard_window_sample_count", True)),
        ):
            with self.subTest(policy_shape=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))

                def mutate_policy_shape(value: dict[str, object]) -> None:
                    mutation(value["policy"])

                mutate_json(
                    fixture.runtime / evidence.TELEMETRY_FILE_NAME,
                    mutate_policy_shape,
                )
                with self.assertRaisesRegex(evidence.EvidenceError, "policy"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_telemetry_rejects_unavailable_foreign_or_stopping_records(self) -> None:
        mutations = (
            lambda record: record.__setitem__("source", "fallback"),
            lambda record: record.__setitem__("status", "error"),
            lambda record: record.__setitem__("status", "identity-drift"),
            lambda record: record.__setitem__("identity_matches_target", False),
            lambda record: record.__setitem__("decision", "hard"),
            lambda record: record.__setitem__("stop_outcome", "requested"),
            lambda record: record.__setitem__("virtual_size_bytes", 1),
            lambda record: record.__setitem__("detail", "sample error"),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))

                def mutate(value: dict[str, object]) -> None:
                    mutation(value["records"][0])

                mutate_json(fixture.runtime / evidence.TELEMETRY_FILE_NAME, mutate)
                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_warning_samples_and_dropped_record_accounting_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            records = [
                available_record(
                    1_000_000_000,
                    evidence.WARNING_PHYS_FOOTPRINT_BYTES + 1,
                ),
                missing_record(2_000_000_000),
            ]
            telemetry = telemetry_for_records(records, dropped_count=37)
            write_json(fixture.runtime / evidence.TELEMETRY_FILE_NAME, telemetry)

            summary = evidence.validate_live_runtime(
                fixture.runtime,
                fixture.profile,
                expected_runtime=fixture.runtime,
            )

            self.assertEqual(525, summary.assertion_count)

    def test_hard_and_emergency_sample_windows_are_rejected(self) -> None:
        hard_footprints = [256 * 1024 * 1024] * 5 + [
            evidence.HARD_PHYS_FOOTPRINT_BYTES + 1
        ] * 10
        emergency_footprints = [evidence.EMERGENCY_PHYS_FOOTPRINT_BYTES + 1]
        for name, footprints in (
            ("hard", hard_footprints),
            ("emergency", emergency_footprints),
        ):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                records = [
                    available_record((index + 1) * 1_000_000_000, footprint)
                    for index, footprint in enumerate(footprints)
                ]
                records.append(missing_record((len(records) + 1) * 1_000_000_000))
                write_json(
                    fixture.runtime / evidence.TELEMETRY_FILE_NAME,
                    telemetry_for_records(records),
                )
                with self.assertRaisesRegex(evidence.EvidenceError, "threshold"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_strict_stop_threshold_boundaries_are_not_off_by_one(self) -> None:
        high = evidence.HARD_PHYS_FOOTPRINT_BYTES + 1
        low = 256 * 1024 * 1024
        cases = (
            (
                "nine-hard-samples",
                [low] * 6 + [high] * 9,
            ),
            (
                "only-four-final-hard-samples",
                [high] * 6 + [low] * 5 + [high] * 4,
            ),
            (
                "exact-emergency-boundary",
                [evidence.EMERGENCY_PHYS_FOOTPRINT_BYTES],
            ),
        )
        for name, footprints in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                records = [
                    available_record((index + 1) * 1_000_000_000, footprint)
                    for index, footprint in enumerate(footprints)
                ]
                records.append(missing_record((len(records) + 1) * 1_000_000_000))
                write_json(
                    fixture.runtime / evidence.TELEMETRY_FILE_NAME,
                    telemetry_for_records(records),
                )

                summary = evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

                self.assertEqual(525, summary.assertion_count)

    def test_timestamp_order_and_terminal_missing_shape_are_exact(self) -> None:
        mutations = (
            lambda records: records[1].__setitem__(
                "observed_at_monotonic_ns",
                records[0]["observed_at_monotonic_ns"],
            ),
            lambda records: records[0].__setitem__("status", "missing"),
            lambda records: records[-1].__setitem__(
                "current_phys_footprint_bytes",
                1,
            ),
            lambda records: records[-1].__setitem__("detail", ""),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))

                def mutate(value: dict[str, object]) -> None:
                    mutation(value["records"])

                mutate_json(fixture.runtime / evidence.TELEMETRY_FILE_NAME, mutate)
                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_scenario_publication_and_source_log_remain_exact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            report = fixture.scenario / "reports" / "report.json"
            done = fixture.scenario / "reports" / "done.marker"
            invalid_time = done.stat().st_mtime_ns
            os.utime(report, ns=(invalid_time, invalid_time))
            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "predates|publication order",
            ):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            (fixture.game / "logs" / "latest.log").write_bytes(b"different\n")
            with self.assertRaisesRegex(evidence.EvidenceError, "exact game-log copy"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )


class ArchiveEvidenceTests(unittest.TestCase):
    def test_nineteen_file_archive_is_one_time_and_self_contained(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            archive = create_archive(fixture)

            with mock.patch.object(
                evidence,
                "load_profile_manifest",
                side_effect=AssertionError("archive consulted current profile"),
            ), mock.patch.object(
                evidence,
                "validate_live_runtime",
                side_effect=AssertionError("archive consulted live runtime"),
            ):
                summary = evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=archive,
                )

            self.assertEqual(525, summary.assertion_count)
            files = {path for path in archive.rglob("*") if path.is_file()}
            self.assertEqual(19, len(files))
            with self.assertRaisesRegex(evidence.EvidenceError, "already exists"):
                evidence.write_archive_manifest(
                    fixture.profile,
                    archive,
                    fixture.runtime,
                    expected_archive_root=archive,
                    expected_runtime=fixture.runtime,
                )

    def test_archive_hashes_cover_all_eighteen_payloads(self) -> None:
        for relative_path in evidence.ARCHIVE_PAYLOAD_PATHS:
            with self.subTest(relative_path=relative_path), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                path = archive / relative_path
                path.chmod(0o600)
                path.write_bytes(path.read_bytes() + b"tamper")
                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_manifest_creation_requires_byte_exact_capture_copy(self) -> None:
        for relative_path in evidence.ARCHIVE_PAYLOAD_PATHS:
            with self.subTest(relative_path=relative_path), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = copy_capture(fixture)
                path = archive / relative_path
                path.chmod(0o600)
                path.write_bytes(path.read_bytes() + b"tamper")
                with self.assertRaises(evidence.EvidenceError):
                    evidence.write_archive_manifest(
                        fixture.profile,
                        archive,
                        fixture.runtime,
                        expected_archive_root=archive,
                        expected_runtime=fixture.runtime,
                    )

    def test_archive_inventory_rejects_missing_extra_and_symlink_entries(self) -> None:
        cases = ("missing", "extra", "symlink")
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                if case == "missing":
                    (archive / evidence.ARCHIVED_GUARD_PAYLOAD_PATHS[0]).unlink()
                elif case == "extra":
                    (archive / "unexpected.txt").write_text("extra\n")
                else:
                    target = fixture.root / "foreign.txt"
                    target.write_text("foreign\n")
                    (archive / "linked.txt").symlink_to(target)
                with self.assertRaisesRegex(evidence.EvidenceError, "inventory|linked"):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_resealed_guard_semantic_tampering_is_rejected(self) -> None:
        cases = (
            (
                evidence.HANDOFF_FILE_NAME,
                lambda value: value.__setitem__("maximum_heap_bytes", 1),
            ),
            (
                evidence.READINESS_FILE_NAME,
                lambda value: value["target"].__setitem__("pid", JAVA_PID + 1),
            ),
            (
                evidence.READINESS_FILE_NAME,
                lambda value: value.__setitem__(
                    "telemetry",
                    "/tmp/foreign/memory-guard-telemetry.json",
                ),
            ),
            (
                evidence.READINESS_FILE_NAME,
                lambda value: value.__setitem__(
                    "memory_policy_name",
                    "strict-2g-v2",
                ),
            ),
            (
                evidence.TELEMETRY_FILE_NAME,
                lambda value: value["state"].__setitem__(
                    "enforcement_disarmed",
                    True,
                ),
            ),
        )
        for file_name, mutation in cases:
            with self.subTest(file_name=file_name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                relative_path = f"{evidence.MEMORY_GUARD_DIRECTORY_NAME}/{file_name}"
                mutate_json(archive / relative_path, mutation)
                reseal_archive_record(archive, relative_path)
                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_resealed_watchdog_semantic_tampering_is_rejected(self) -> None:
        cases = (
            (
                evidence.WATCHDOG_READINESS_FILE_NAME,
                lambda value: value.__setitem__("enforcement_active", False),
            ),
            (
                evidence.WATCHDOG_TELEMETRY_FILE_NAME,
                lambda value: value["terminal_attestation"].__setitem__(
                    "tracked_identities_absent",
                    False,
                ),
            ),
        )
        for file_name, mutation in cases:
            with self.subTest(file_name=file_name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                relative_path = (
                    f"{evidence.LAUNCH_WATCHDOG_DIRECTORY_NAME}/{file_name}"
                )
                mutate_json(archive / relative_path, mutation)
                reseal_archive_record(archive, relative_path)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_resealed_watchdog_bytes_remain_bound_to_launcher_provenance(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            archive = create_archive(fixture)
            relative_path = (
                f"{evidence.LAUNCH_WATCHDOG_DIRECTORY_NAME}/"
                f"{evidence.WATCHDOG_READINESS_FILE_NAME}"
            )
            watchdog_path = archive / relative_path
            watchdog_path.write_bytes(watchdog_path.read_bytes()[:-1] + b" \n")
            reseal_archive_record(archive, relative_path)

            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "watchdog artifact differs from launcher provenance",
            ):
                evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=archive,
                )

    def test_resealed_anchor_semantic_tampering_is_rejected(self) -> None:
        cases = (
            (
                evidence.ANCHOR_READINESS_FILE_NAME,
                lambda value: value.__setitem__("pid", LAUNCH_PROCESS_GROUP_ID + 1),
            ),
            (
                evidence.ANCHOR_CHILD_STARTED_FILE_NAME,
                lambda value: value.__setitem__("pid", WRAPPER_PID + 1),
            ),
            (
                evidence.ANCHOR_CHILD_RESULT_FILE_NAME,
                lambda value: value.__setitem__("exit_code", 1),
            ),
            (
                evidence.ANCHOR_FINISH_FILE_NAME,
                lambda value: value.__setitem__("child_exit_code", 1),
            ),
        )
        for file_name, mutation in cases:
            with self.subTest(file_name=file_name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                artifact_path = (
                    archive / evidence.LAUNCH_ANCHOR_DIRECTORY_NAME / file_name
                )
                mutate_canonical_json(artifact_path, mutation)
                refresh_archived_launcher_provenance(
                    archive,
                    "launch_anchor",
                    evidence.LAUNCH_ANCHOR_DIRECTORY_NAME,
                    file_name,
                )

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_resealed_anchor_noncanonical_bytes_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            archive = create_archive(fixture)
            file_name = evidence.ANCHOR_START_FILE_NAME
            artifact_path = (
                archive / evidence.LAUNCH_ANCHOR_DIRECTORY_NAME / file_name
            )
            value = json.loads(artifact_path.read_text(encoding="utf-8"))
            write_json(artifact_path, value)
            refresh_archived_launcher_provenance(
                archive,
                "launch_anchor",
                evidence.LAUNCH_ANCHOR_DIRECTORY_NAME,
                file_name,
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "canonical JSON"):
                evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=archive,
                )

    def test_resealed_report_tampering_still_uses_v21_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            archive = create_archive(fixture)
            report_path = archive / "reports" / "report.json"
            mutate_json(
                report_path,
                lambda report: report["slitherite_blocks"]["behavior"].__setitem__(
                    "button_elapsed_ticks",
                    19,
                ),
            )
            reseal_archive_record(archive, "reports/report.json")
            with self.assertRaisesRegex(evidence.EvidenceError, "v21 contract"):
                evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=archive,
                )

    def test_manifest_identity_and_guard_attestation_are_exact(self) -> None:
        mutations = (
            lambda manifest: manifest.__setitem__("assertion_count", 524),
            lambda manifest: manifest["profile"].__setitem__(
                "manifest_sha256",
                "0" * 64,
            ),
            lambda manifest: manifest["runtime"].__setitem__(
                "execution",
                "packaged-jar",
            ),
            lambda manifest: manifest["memory_guard"].__setitem__(
                "sample_source",
                "fallback",
            ),
            lambda manifest: manifest["launch_watchdog"].__setitem__(
                "terminal_global_java_absence_required",
                False,
            ),
            lambda manifest: manifest["launch_anchor"].__setitem__(
                "terminal_child_exit_code",
                1,
            ),
            lambda manifest: manifest["publication"].__setitem__(
                "verified_memory_guard_against_stopped_runtime_before_sealing",
                False,
            ),
            lambda manifest: manifest["files"].pop(
                evidence.ARCHIVED_GUARD_PAYLOAD_PATHS[0]
            ),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                manifest_path = archive / evidence.ARCHIVE_MANIFEST_NAME
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
                mutation(manifest)
                write_json(manifest_path, manifest)
                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_archive_requires_exact_destination_and_no_competing_versions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            archive = create_archive(fixture)
            wrong = fixture.root / "wrong-name"
            shutil.copytree(archive, wrong, copy_function=shutil.copy2)
            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "destination|v21|repository path",
            ):
                evidence.validate_archived_evidence(
                    wrong,
                    expected_archive_root=wrong,
                )

        for competing_name in (
            "slitherite-block-registry-server-v19",
            "slitherite-block-registry-server-v20",
            "slitherite-block-registry-server-v22",
        ):
            with self.subTest(competing_name=competing_name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                (archive.parent / competing_name).mkdir()
                with self.assertRaisesRegex(evidence.EvidenceError, "competing"):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_archive_validation_is_independent_of_all_mtimes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            archive = create_archive(fixture)
            timestamp = time.time_ns()
            for index, path in enumerate(
                sorted(path for path in archive.rglob("*") if path.is_file())
            ):
                reversed_time = timestamp - index * 1_000_000_000
                os.utime(path, ns=(reversed_time, reversed_time))

            summary = evidence.validate_archived_evidence(
                archive,
                expected_archive_root=archive,
            )

            self.assertEqual(525, summary.assertion_count)

    def test_archive_requires_terminal_missing_without_current_time(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary), "missing")
            archive = create_archive(fixture)

            summary = evidence.validate_archived_evidence(
                archive,
                expected_archive_root=archive,
            )

            self.assertEqual(525, summary.assertion_count)

        with tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary), "missing")
            archive = create_archive(fixture)
            telemetry_path = (
                archive
                / evidence.MEMORY_GUARD_DIRECTORY_NAME
                / evidence.TELEMETRY_FILE_NAME
            )
            write_json(telemetry_path, valid_telemetry("available"))
            reseal_archive_record(
                archive,
                (
                    f"{evidence.MEMORY_GUARD_DIRECTORY_NAME}/"
                    f"{evidence.TELEMETRY_FILE_NAME}"
                ),
            )

            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "terminal authoritative missing sample",
            ):
                evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=archive,
                )


class CommandLineTests(unittest.TestCase):
    def test_manifest_creation_and_capture_runtime_are_an_atomic_cli_pair(self) -> None:
        cases = (
            ["verifier", "--create-archive-manifest", "/archive"],
            ["verifier", "--archive", "/archive", "--capture-runtime", "/runtime"],
        )
        for arguments in cases:
            with self.subTest(arguments=arguments), mock.patch.object(
                sys,
                "argv",
                arguments,
            ), self.assertRaises(SystemExit):
                evidence.parse_arguments()

        with mock.patch.object(
            sys,
            "argv",
            [
                "verifier",
                "--create-archive-manifest",
                "/archive",
                "--capture-runtime",
                "/runtime",
            ],
        ):
            parsed = evidence.parse_arguments()

        self.assertEqual(Path("/archive"), parsed.create_archive_manifest)
        self.assertEqual(Path("/runtime"), parsed.capture_runtime)


if __name__ == "__main__":
    unittest.main()
