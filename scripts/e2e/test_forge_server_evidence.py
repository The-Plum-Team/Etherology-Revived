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

import forge_server_evidence as evidence


@dataclass(frozen=True)
class Fixture:
    root: Path
    profile: Path
    runtime: Path
    game: Path
    scenario: Path


def write_json(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def rewrite_json_preserving_time(path: Path, value: dict[str, object]) -> None:
    metadata = path.stat()
    write_json(path, value)
    os.utime(path, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))


def valid_profile() -> dict[str, object]:
    return {
        "schema": 1,
        "profile": {
            "id": evidence.PROFILE_ID,
            "runtime_directory": evidence.PROFILE_ID,
            "game_directory": "game",
        },
        "release": {
            "matrix": "release/release-matrix.json",
            "artifact_node": "forge-1.20.1",
            "minecraft": "1.20.1",
            "loader": "forge",
            "loader_version": "47.4.9",
            "java": 17,
        },
        "launch": {
            "kind": "loom-userdev",
            "task_path": evidence.TASK_PATH,
            "scenario": evidence.SCENARIO_ID,
            "maximum_memory_mb": 2048,
        },
        "evidence": {
            "directory": "evidence",
            "scenario_directory": evidence.SCENARIO_ID,
            "report": "reports/report.json",
            "launcher_result": "reports/launcher-result.json",
            "completion_marker": "reports/done.marker",
            "server_log": "logs/latest.log",
        },
        "profile_directories": [
            "config",
            "crash-reports",
            "evidence",
            "logs",
            "mods",
            "world",
        ],
        "required_mod_ids": [
            "etherology",
            "etherology_e2e_server_probe",
        ],
        "forbidden_mod_ids": [
            "etherology_e2e_harness",
            "quickskin",
            "cpm",
            "ears",
            "modmenu",
            "roughlyenoughitems",
            "emi",
        ],
    }


def valid_report() -> dict[str, object]:
    assertions = [
        {
            "name": name,
            "passed": True,
            "expected": value,
            "actual": value,
        }
        for name, value in evidence.EXPECTED_ASSERTIONS
    ]
    return {
        "schema": 1,
        "profile_id": evidence.PROFILE_ID,
        "scenario": evidence.SCENARIO_ID,
        "status": "passed",
        "minecraft": "1.20.1",
        "loader": "forge",
        "loader_version": "47.4.9",
        "java": 17,
        "distribution": "DEDICATED_SERVER",
        "runtime_kind": "loom-userdev",
        "loaded_mod_ids": [
            "etherology",
            "etherology_e2e_server_probe",
            "forge",
            "minecraft",
        ],
        "forbidden_mod_ids_loaded": [],
        "mods": copy.deepcopy(evidence.EXPECTED_MODS),
        "registry": copy.deepcopy(evidence.EXPECTED_REGISTRY),
        "tags": copy.deepcopy(evidence.EXPECTED_TAGS),
        "lifecycle": list(evidence.EXPECTED_LIFECYCLE),
        "assertions": assertions,
    }


def valid_server_log() -> bytes:
    lines = [
        "[Server thread/INFO] [EtherologyServerProbe] tags_updated",
        "[Server thread/INFO] Done (1.234s)! For help, type help",
        "[Server thread/INFO] [EtherologyServerProbe] server_started",
        "[LanServerPinger #1/WARN] "
        "[net.minecraft.client.network.LanServerPinger/] No route to host",
        "[Server thread/INFO] [EtherologyServerProbe] server_stopping",
        "[Server thread/INFO] Stopping server",
        "[Server thread/INFO] Saving worlds",
        "[Server thread/INFO] All dimensions are saved",
        "[Server thread/INFO] [EtherologyServerProbe] server_stopped",
        "[Server thread/INFO] [EtherologyServerProbe] report_published",
        "[etherology-e2e-server-probe-exit/INFO] "
        + evidence.TERMINATION_LOG_TOKEN,
    ]
    return ("\n".join(lines) + "\n").encode("utf-8")


def build_fixture(root: Path) -> Fixture:
    profile = root / "repository" / evidence.PROFILE_MANIFEST_RELATIVE_PATH
    write_json(profile, valid_profile())
    profile_record = evidence.ProfileRecord(
        relative_path=evidence.PROFILE_MANIFEST_RELATIVE_PATH.as_posix(),
        size=profile.stat().st_size,
        sha256=evidence.sha256_file(profile),
    )

    runtime = root / "repository" / evidence.RUNTIME_RELATIVE_PATH
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
    log_content = valid_server_log()
    (game / "logs" / "latest.log").write_bytes(log_content)
    write_json(
        runtime / evidence.PROFILE_MARKER_NAME,
        evidence.expected_profile_marker(profile_record),
    )

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
        },
    )
    done_path.write_bytes(b"complete\n")

    base_time = time.time_ns() - 10_000_000_000
    for index, path in enumerate(
        (report_path, log_path, launcher_path, done_path),
        start=1,
    ):
        timestamp = base_time + (index * 1_000_000_000)
        os.utime(path, ns=(timestamp, timestamp))
    return Fixture(root, profile, runtime, game, scenario)


def create_archive(fixture: Fixture) -> Path:
    archive = fixture.root / "repository" / evidence.ARCHIVE_RELATIVE_PATH
    archive.parent.mkdir(parents=True)
    shutil.copytree(fixture.scenario, archive, copy_function=shutil.copy2)
    evidence.write_archive_manifest(
        fixture.profile,
        archive,
        expected_archive_root=archive,
    )
    return archive


def mutate_json(path: Path, mutation) -> None:
    value = json.loads(path.read_text(encoding="utf-8"))
    mutation(value)
    rewrite_json_preserving_time(path, value)


def reseal_archive_record(archive: Path, relative_path: str) -> None:
    manifest_path = archive / evidence.ARCHIVE_MANIFEST_NAME
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    payload_path = archive / relative_path
    manifest["files"][relative_path] = {
        "size": payload_path.stat().st_size,
        "sha256": evidence.sha256_file(payload_path),
    }
    write_json(manifest_path, manifest)


class LiveEvidenceTests(unittest.TestCase):
    def test_exact_live_runtime_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))

            summary = evidence.validate_live_runtime(
                fixture.runtime,
                fixture.profile,
                expected_runtime=fixture.runtime,
            )

            self.assertEqual(evidence.PROFILE_ID, summary.profile_id)
            self.assertEqual(31, summary.assertion_count)
            self.assertEqual(
                evidence.sha256_file(fixture.scenario / "logs" / "latest.log"),
                summary.log_sha256,
            )

    def test_profile_kind_forbidden_inventory_and_marker_are_exact(self) -> None:
        cases = (
            (
                "profile kind",
                lambda fixture: mutate_json(
                    fixture.profile,
                    lambda profile: profile["launch"].__setitem__(
                        "kind", "packaged-jar"
                    ),
                ),
                "launch identity",
            ),
            (
                "forbidden inventory",
                lambda fixture: mutate_json(
                    fixture.profile,
                    lambda profile: profile["forbidden_mod_ids"].remove(
                        "etherology_e2e_harness"
                    ),
                ),
                "forbidden mod inventory",
            ),
            (
                "ownership marker",
                lambda fixture: mutate_json(
                    fixture.runtime / evidence.PROFILE_MARKER_NAME,
                    lambda marker: marker.__setitem__("profile_id", "foreign"),
                ),
                "runtime marker",
            ),
        )
        for name, mutation, message in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                mutation(fixture)

                with self.assertRaisesRegex(evidence.EvidenceError, message):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_registry_identity_and_singleton_namespace_are_exact(self) -> None:
        cases = (
            ("registry_id", "minecraft:sound_event"),
            ("event_id", "etherology:resonance"),
            ("internal_id", "resonance"),
            ("range", 15),
            (
                "etherology_event_ids",
                [
                    "etherology:etherology_resonance",
                    "etherology:unexpected",
                ],
            ),
            ("same_instance_at_server_started", False),
        )
        for field, replacement in cases:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: report["registry"].__setitem__(
                        field, replacement
                    ),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "registry evidence"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_tag_update_and_exact_singleton_memberships_are_required(self) -> None:
        cases = (
            (
                "update cause",
                lambda tags: tags.__setitem__("update_cause", "SERVER_STARTED"),
            ),
            (
                "static update",
                lambda tags: tags.__setitem__(
                    "should_update_static_data", False
                ),
            ),
            ("update count", lambda tags: tags.__setitem__("update_count", 2)),
            (
                "vibrations contains",
                lambda tags: tags["vibrations"].__setitem__(
                    "contains_event", False
                ),
            ),
            (
                "vibrations singleton",
                lambda tags: tags["vibrations"].__setitem__(
                    "etherology_event_ids", []
                ),
            ),
            (
                "warden singleton",
                lambda tags: tags["warden_can_listen"].__setitem__(
                    "etherology_event_ids",
                    [
                        "etherology:etherology_resonance",
                        "etherology:unexpected",
                    ],
                ),
            ),
            (
                "exact two tag ids",
                lambda tags: tags["etherology_tag_ids"].append(
                    "minecraft:allay_can_listen"
                ),
            ),
            (
                "same membership",
                lambda tags: tags.__setitem__(
                    "same_membership_at_server_started", False
                ),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(report_path, lambda report: mutation(report["tags"]))

                with self.assertRaisesRegex(evidence.EvidenceError, "tag evidence"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_dedicated_distribution_and_exact_mod_statuses_are_required(self) -> None:
        cases = (
            (
                "profile id",
                lambda report: report.__setitem__("profile_id", "foreign"),
            ),
            (
                "scenario",
                lambda report: report.__setitem__("scenario", "other"),
            ),
            (
                "distribution",
                lambda report: report.__setitem__("distribution", "CLIENT"),
            ),
            (
                "runtime kind",
                lambda report: report.__setitem__(
                    "runtime_kind", "packaged-jar"
                ),
            ),
            (
                "production mod",
                lambda report: report["mods"]["etherology"].__setitem__(
                    "loaded", False
                ),
            ),
            (
                "probe mod",
                lambda report: report["mods"][
                    "etherology_e2e_server_probe"
                ].__setitem__("loaded", False),
            ),
            (
                "extra mod",
                lambda report: report["mods"].__setitem__(
                    "unexpected", {"loaded": True}
                ),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(report_path, mutation)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_every_profile_forbidden_mod_has_an_explicit_false_status(self) -> None:
        for mod_id in evidence.FORBIDDEN_MOD_IDS:
            with self.subTest(mod_id=mod_id), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: report["mods"][mod_id].__setitem__(
                        "loaded", True
                    ),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "mod inventory"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_loaded_mod_inventory_is_sorted_unique_required_and_forbidden_free(
        self,
    ) -> None:
        cases = (
            (
                "unsorted",
                lambda report: report["loaded_mod_ids"].reverse(),
            ),
            (
                "duplicate",
                lambda report: report["loaded_mod_ids"].append("minecraft"),
            ),
            (
                "non-string",
                lambda report: report["loaded_mod_ids"].append(1),
            ),
            (
                "invalid id",
                lambda report: report["loaded_mod_ids"].append("Invalid.ID"),
            ),
            (
                "missing required",
                lambda report: report["loaded_mod_ids"].remove("etherology"),
            ),
            (
                "forbidden loaded",
                lambda report: report["loaded_mod_ids"].insert(2, "quickskin"),
            ),
            (
                "reported intersection",
                lambda report: report["forbidden_mod_ids_loaded"].append(
                    "quickskin"
                ),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(report_path, mutation)

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "mod ID inventory|required mod|forbidden mod|intersection",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_boolean_evidence_cannot_be_replaced_by_integer_one(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            report_path = fixture.scenario / "reports" / "report.json"
            mutate_json(
                report_path,
                lambda report: report["mods"]["etherology"].__setitem__(
                    "loaded", 1
                ),
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "mod inventory"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_all_31_assertions_must_pass_in_exact_order_and_value(self) -> None:
        mutations = (
            lambda assertions: assertions.pop(),
            lambda assertions: assertions.__setitem__(
                slice(0, 2), reversed(assertions[0:2])
            ),
            lambda assertions: assertions[0].__setitem__("passed", False),
            lambda assertions: assertions[0].__setitem__(
                "expected", "CLIENT"
            ),
            lambda assertions: assertions[0].__setitem__("actual", "CLIENT"),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                report_path = fixture.scenario / "reports" / "report.json"
                mutate_json(
                    report_path,
                    lambda report: mutation(report["assertions"]),
                )

                with self.assertRaisesRegex(evidence.EvidenceError, "assertion"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_report_and_log_lifecycle_order_are_exact(self) -> None:
        with self.subTest(source="report"), tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            report_path = fixture.scenario / "reports" / "report.json"
            mutate_json(
                report_path,
                lambda report: report["lifecycle"].reverse(),
            )
            with self.assertRaisesRegex(evidence.EvidenceError, "lifecycle evidence"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

        with self.subTest(source="log"), tempfile.TemporaryDirectory() as temporary:
            fixture = build_fixture(Path(temporary))
            log_path = fixture.scenario / "logs" / "latest.log"
            original = log_path.read_text(encoding="utf-8")
            changed = original.replace("server_started", "placeholder", 1)
            changed = changed.replace("server_stopping", "server_started", 1)
            changed = changed.replace("placeholder", "server_stopping", 1)
            metadata = log_path.stat()
            log_path.write_text(changed, encoding="utf-8")
            os.utime(log_path, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))
            with self.assertRaisesRegex(evidence.EvidenceError, "probe lifecycle"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_launcher_requires_zero_exit_no_timeout_and_exact_provenance(self) -> None:
        mutations = (
            lambda launcher: launcher.__setitem__("exit_code", 1),
            lambda launcher: launcher.__setitem__("timed_out", True),
            lambda launcher: launcher.__setitem__("scenario", "other"),
            lambda launcher: launcher.__setitem__("task_path", ":wrong"),
            lambda launcher: launcher["profile_manifest"].__setitem__(
                "sha256", "0" * 64
            ),
            lambda launcher: launcher["server_log"].__setitem__(
                "sha256", "0" * 64
            ),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                launcher_path = (
                    fixture.scenario / "reports" / "launcher-result.json"
                )
                mutate_json(launcher_path, mutation)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_log_rejects_fatal_client_missing_normal_and_duplicate_tokens(self) -> None:
        changes = (
            ("fatal", lambda log: log + "[FATAL] exploded\n", "fatal marker"),
            (
                "client",
                lambda log: log + "[Render thread/INFO] client loaded\n",
                "client marker",
            ),
            (
                "client class",
                lambda log: log
                + "[main/INFO] [net.minecraft.client.gui.screen.TitleScreen/] loaded\n",
                "unexpected client class marker",
            ),
            (
                "normal",
                lambda log: log.replace("Saving worlds\n", "", 1),
                "normal lifecycle marker",
            ),
            (
                "duplicate",
                lambda log: log
                + "[Server thread/INFO] [EtherologyServerProbe] tags_updated\n",
                "probe lifecycle",
            ),
            (
                "termination status",
                lambda log: log.replace("status=0", "status=1", 1),
                "termination contract",
            ),
        )
        for name, change, message in changes:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                log_path = fixture.scenario / "logs" / "latest.log"
                metadata = log_path.stat()
                log_path.write_text(
                    change(log_path.read_text(encoding="utf-8")),
                    encoding="utf-8",
                )
                os.utime(log_path, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))

                with self.assertRaisesRegex(evidence.EvidenceError, message):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_evidence_log_must_be_exact_copy_of_game_log(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            (fixture.game / "logs" / "latest.log").write_bytes(
                valid_server_log() + b"source-only\n"
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "exact game-log copy"):
                evidence.validate_live_runtime(
                    fixture.runtime,
                    fixture.profile,
                    expected_runtime=fixture.runtime,
                )

    def test_saved_world_is_required_and_cannot_be_linked(self) -> None:
        cases = ("missing", "empty", "linked")
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                level_data = fixture.game / "world" / "level.dat"
                if case == "missing":
                    level_data.unlink()
                elif case == "empty":
                    level_data.write_bytes(b"")
                else:
                    target = fixture.root / "foreign-level.dat"
                    target.write_bytes(b"foreign")
                    level_data.unlink()
                    level_data.symlink_to(target)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_evidence_inventory_rejects_extras_and_symlinks(self) -> None:
        cases = ("extra", "symlink")
        for case in cases:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                if case == "extra":
                    (fixture.scenario / "console.log").write_text(
                        "not contractual\n", encoding="utf-8"
                    )
                else:
                    target = fixture.root / "foreign.log"
                    target.write_text("foreign\n", encoding="utf-8")
                    linked = fixture.scenario / "logs" / "linked.log"
                    linked.symlink_to(target)

                with self.assertRaisesRegex(evidence.EvidenceError, "inventory|linked"):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_runtime_isolation_rejects_extra_roots_game_evidence_and_mod_jars(
        self,
    ) -> None:
        cases = (
            (
                "runtime root",
                lambda fixture: (fixture.runtime / "foreign.txt").write_text(
                    "foreign\n", encoding="utf-8"
                ),
            ),
            (
                "game evidence",
                lambda fixture: (fixture.game / "evidence").mkdir(),
            ),
            (
                "staged mod",
                lambda fixture: (fixture.game / "mods" / "foreign.jar").write_bytes(
                    b"foreign"
                ),
            ),
            (
                "extra scenario",
                lambda fixture: (
                    fixture.runtime / "evidence" / "other-scenario"
                ).mkdir(),
            ),
        )
        for name, mutation in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                mutation(fixture)

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )

    def test_live_paths_reject_symlinked_repository_components(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            linked_repository = fixture.root / "linked-repository"
            linked_repository.mkdir()
            (linked_repository / "scripts").symlink_to(
                fixture.root / "repository" / "scripts",
                target_is_directory=True,
            )
            linked_runtime = linked_repository / evidence.RUNTIME_RELATIVE_PATH
            linked_profile = (
                linked_repository / evidence.PROFILE_MANIFEST_RELATIVE_PATH
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "symlink"):
                evidence.validate_live_runtime(
                    linked_runtime,
                    linked_profile,
                    expected_runtime=linked_runtime,
                )

    def test_publication_order_requires_report_log_launcher_then_done(self) -> None:
        paths = (
            ("log", "report.json", "../logs/latest.log"),
            ("launcher", "../logs/latest.log", "launcher-result.json"),
            ("done", "launcher-result.json", "done.marker"),
        )
        for name, later_name, earlier_name in paths:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                reports = fixture.scenario / "reports"
                later = (reports / later_name).resolve()
                earlier = (reports / earlier_name).resolve()
                later_time = later.stat().st_mtime_ns
                os.utime(earlier, ns=(later_time - 1, later_time - 1))

                with self.assertRaisesRegex(
                    evidence.EvidenceError,
                    "predates|published last",
                ):
                    evidence.validate_live_runtime(
                        fixture.runtime,
                        fixture.profile,
                        expected_runtime=fixture.runtime,
                    )


class ArchiveEvidenceTests(unittest.TestCase):
    def test_archive_manifest_is_one_time_and_archive_validation_is_self_contained(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            archive = create_archive(fixture)

            with mock.patch.object(
                evidence,
                "load_profile_manifest",
                side_effect=AssertionError("archive consulted tracked profile"),
            ), mock.patch.object(
                evidence,
                "validate_live_runtime",
                side_effect=AssertionError("archive consulted live runtime"),
            ):
                summary = evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=archive,
                )

            self.assertEqual(evidence.PROFILE_ID, summary.profile_id)
            self.assertEqual(31, summary.assertion_count)
            with self.assertRaisesRegex(evidence.EvidenceError, "already exists"):
                evidence.write_archive_manifest(
                    fixture.profile,
                    archive,
                    expected_archive_root=archive,
                )

    def test_archive_hashes_reject_payload_tampering(self) -> None:
        for relative_path in evidence.EVIDENCE_PAYLOAD_PATHS:
            with self.subTest(
                relative_path=relative_path
            ), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                path = archive / relative_path
                path.write_bytes(path.read_bytes() + b"tamper")

                with self.assertRaises(evidence.EvidenceError):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_resealed_semantically_false_report_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            archive = create_archive(fixture)
            report_path = archive / "reports" / "report.json"
            mutate_json(
                report_path,
                lambda report: report["registry"].__setitem__("range", 15),
            )
            reseal_archive_record(archive, "reports/report.json")

            with self.assertRaisesRegex(evidence.EvidenceError, "registry evidence"):
                evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=archive,
                )

    def test_archive_inventory_rejects_extras_and_symlinks(self) -> None:
        for case in ("extra", "symlink"):
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temporary:
                fixture = build_fixture(Path(temporary))
                archive = create_archive(fixture)
                if case == "extra":
                    (archive / "console.log").write_text(
                        "unexpected\n", encoding="utf-8"
                    )
                else:
                    target = fixture.root / "outside.log"
                    target.write_text("outside\n", encoding="utf-8")
                    linked = archive / "linked.log"
                    linked.symlink_to(target)

                with self.assertRaisesRegex(evidence.EvidenceError, "inventory|linked"):
                    evidence.validate_archived_evidence(
                        archive,
                        expected_archive_root=archive,
                    )

    def test_archive_path_rejects_symlinked_repository_components(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            create_archive(fixture)
            linked_repository = fixture.root / "linked-repository"
            linked_repository.mkdir()
            (linked_repository / "docs").symlink_to(
                fixture.root / "repository" / "docs",
                target_is_directory=True,
            )
            linked_archive = linked_repository / evidence.ARCHIVE_RELATIVE_PATH

            with self.assertRaisesRegex(evidence.EvidenceError, "symlink"):
                evidence.validate_archived_evidence(
                    linked_archive,
                    expected_archive_root=linked_archive,
                )

    def test_archive_manifest_identity_and_provenance_are_fail_closed(self) -> None:
        mutations = (
            lambda manifest: manifest["profile"].__setitem__(
                "manifest_sha256", "0" * 64
            ),
            lambda manifest: manifest["runtime"].__setitem__(
                "execution", "packaged-jar"
            ),
            lambda manifest: manifest.__setitem__("assertion_count", 23),
            lambda manifest: manifest["publication"].__setitem__(
                "verified_completion_marker_last_before_sealing", False
            ),
            lambda manifest: manifest["files"][
                "reports/report.json"
            ].__setitem__("size", True),
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

    def test_archive_requires_exact_destination_and_server_v2_name(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            archive = create_archive(fixture)

            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "destination|repository-owned root",
            ):
                evidence.validate_archived_evidence(
                    archive,
                    expected_archive_root=(
                        fixture.root / "other-repository" / evidence.ARCHIVE_RELATIVE_PATH
                    ),
                )

            wrong_name = fixture.root / "wrong-name"
            shutil.copytree(archive, wrong_name, copy_function=shutil.copy2)
            with self.assertRaisesRegex(
                evidence.EvidenceError,
                "repository path|server profile v2",
            ):
                evidence.validate_archived_evidence(
                    wrong_name,
                    expected_archive_root=wrong_name,
                )

    def test_archive_validation_is_independent_of_checkout_mtimes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixture = build_fixture(Path(temporary_directory))
            archive = create_archive(fixture)
            timestamp = time.time_ns()
            for index, path in enumerate(
                sorted(path for path in archive.rglob("*") if path.is_file())
            ):
                reversed_time = timestamp - (index * 1_000_000_000)
                os.utime(path, ns=(reversed_time, reversed_time))

            summary = evidence.validate_archived_evidence(
                archive,
                expected_archive_root=archive,
            )

            self.assertEqual(31, summary.assertion_count)


if __name__ == "__main__":
    unittest.main()
