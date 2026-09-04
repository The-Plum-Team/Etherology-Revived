from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest import mock

from scripts.baseline import original_client
from scripts.baseline import original_pedestal_archive_v14 as verifier


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
ARCHIVE_PATH = REPOSITORY_ROOT / verifier.ARCHIVE_RELATIVE_PATH


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class PedestalArchiveV14Test(unittest.TestCase):

    @staticmethod
    def copy_archive(repository: Path) -> Path:
        archive = repository / verifier.ARCHIVE_RELATIVE_PATH
        archive.parent.mkdir(parents=True)
        shutil.copytree(ARCHIVE_PATH, archive, copy_function=shutil.copy2)
        return archive

    @staticmethod
    def validate_integrity(repository: Path, archive: Path) -> None:
        verifier._load_validated_archive(
            repository,
            archive,
            sha256_file,
            verifier.PedestalArchiveError,
        )

    def test_accepted_archive_delegates_all_mechanics_to_launch_verifier(self) -> None:
        self.assertEqual(
            sha256_file(REPOSITORY_ROOT / verifier.LAUNCH_VERIFIER_RELATIVE_PATH),
            verifier.LAUNCH_VERIFIER_SHA256,
        )
        self.assertEqual(len(verifier._launch.EXPECTED_ASSERTION_NAMES), 74)
        summary = verifier.validate_archive(
            repository_root=REPOSITORY_ROOT,
            archive_path=ARCHIVE_PATH,
            manifest_path=REPOSITORY_ROOT / verifier.PROFILE_RELATIVE_PATH,
            harness_path=(
                REPOSITORY_ROOT
                / "baseline-harness/fabric/1.21.1/build/libs"
                / verifier.HARNESS_FILE
            ),
            decode_png=original_client.decode_png,
            assert_image_is_not_blank=original_client.assert_image_is_not_blank,
            sha256_file=sha256_file,
            error_type=verifier.PedestalArchiveError,
        )
        self.assertEqual(summary.assertion_count, 74)
        self.assertEqual(summary.screenshot_count, 4)
        self.assertEqual(
            summary.persistence_material_changed_pixel_ratio,
            0.001283275462962963,
        )

    def test_archive_rejects_payload_bytes_and_capture_mtime_drift(self) -> None:
        for mutation in ("bytes", "mtime"):
            with self.subTest(mutation=mutation):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    archive = self.copy_archive(repository)
                    report = archive / "reports/report.json"
                    if mutation == "bytes":
                        report.write_bytes(report.read_bytes() + b"tampered")
                    else:
                        status = report.stat()
                        os.utime(
                            report,
                            ns=(status.st_atime_ns, status.st_mtime_ns + 1),
                        )
                    with self.assertRaises(verifier.PedestalArchiveError):
                        self.validate_integrity(repository, archive)

    def test_archive_rejects_extra_linked_and_special_entries(self) -> None:
        def add_extra(repository: Path, archive: Path) -> None:
            del repository
            (archive / "unexpected.txt").write_text("unexpected\n", encoding="utf-8")

        def link_readme(repository: Path, archive: Path) -> None:
            foreign = repository / "foreign-readme.md"
            readme = archive / "README.md"
            foreign.write_bytes(readme.read_bytes())
            readme.unlink()
            readme.symlink_to(foreign)

        def replace_with_fifo(repository: Path, archive: Path) -> None:
            del repository
            readme = archive / "README.md"
            readme.unlink()
            os.mkfifo(readme)

        for mutation in (add_extra, link_readme, replace_with_fifo):
            with self.subTest(mutation=mutation.__name__):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    repository = Path(temporary_directory)
                    archive = self.copy_archive(repository)
                    mutation(repository, archive)
                    with self.assertRaises(verifier.PedestalArchiveError):
                        self.validate_integrity(repository, archive)

    def test_repinned_manifest_cannot_reclassify_the_accepted_run(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            repository = Path(temporary_directory)
            archive = self.copy_archive(repository)
            manifest_path = archive / "archive-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["accepted"] = False
            manifest_path.write_text(
                json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
            with (
                mock.patch.object(
                    verifier,
                    "ARCHIVE_MANIFEST_SIZE",
                    manifest_path.stat().st_size,
                ),
                mock.patch.object(
                    verifier,
                    "ARCHIVE_MANIFEST_SHA256",
                    sha256_file(manifest_path),
                ),
                self.assertRaises(verifier.PedestalArchiveError),
            ):
                self.validate_integrity(repository, archive)

    @unittest.skipUnless(
        (REPOSITORY_ROOT / verifier.ACTIVE_RUNTIME_RELATIVE_PATH).is_dir(),
        "the preserved repository-owned v14 runtime is not present",
    )
    def test_preserved_consumed_runtime_matches_the_archive(self) -> None:
        verifier.validate_consumed_runtime(
            repository_root=REPOSITORY_ROOT,
            runtime_path=(
                REPOSITORY_ROOT / verifier.ACTIVE_RUNTIME_RELATIVE_PATH
            ),
            archive_path=ARCHIVE_PATH,
            sha256_file=sha256_file,
            error_type=verifier.PedestalArchiveError,
        )


if __name__ == "__main__":
    unittest.main()
