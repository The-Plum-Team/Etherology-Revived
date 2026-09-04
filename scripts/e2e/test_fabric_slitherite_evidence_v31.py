from __future__ import annotations

from pathlib import Path
import tempfile
import sys
import unittest


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import fabric_slitherite_evidence_v31 as fabric_slitherite_evidence
import slitherite_client_evidence_test_support_v1 as support


class FabricSlitheriteEvidenceV31Tests(
    support.SlitheriteClientEvidenceContractTests
):
    verifier = fabric_slitherite_evidence
    historical_profile_relative_path = (
        "scripts/e2e/fabric-1.20.1-profile-v30.json"
    )
    historical_profile_sha256 = (
        "f2131cb9190b17b42035604d26988a1aa8091cbc94541cb29c0e9d018bcf8000"
    )
    historical_verifier_relative_path = (
        "scripts/e2e/fabric_attrahite_evidence_v30.py"
    )
    historical_verifier_sha256 = (
        "5cd7c4a1ceaaaf2c01e88b8596b5fbf17306edb2a4fb1a2d43fd8db640e3f627"
    )

    def test_accepted_archive_manifest_is_byte_exact(self) -> None:
        archive_root = (
            SCRIPT_DIRECTORY.parent.parent
            / fabric_slitherite_evidence.verifier_contract().archive_parent_relative_path
            / fabric_slitherite_evidence.ARCHIVE_DIRECTORY_NAME
        )
        manifest_path = archive_root / fabric_slitherite_evidence.ARCHIVE_MANIFEST_NAME

        self.assertEqual(
            fabric_slitherite_evidence.ARCHIVE_MANIFEST_SIZE,
            manifest_path.stat().st_size,
        )
        self.assertEqual(
            fabric_slitherite_evidence.ARCHIVE_MANIFEST_SHA256,
            support.sha256_file(manifest_path),
        )
        fabric_slitherite_evidence.validate_archive_manifest_identity(archive_root)

    def test_rewritten_archive_manifest_is_rejected_even_when_coherent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            archive_root = (
                Path(temporary_directory)
                / fabric_slitherite_evidence.ARCHIVE_DIRECTORY_NAME
            )
            archive_root.mkdir()
            (archive_root / fabric_slitherite_evidence.ARCHIVE_MANIFEST_NAME).write_text(
                "{}\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                fabric_slitherite_evidence.client.E2EError,
                "manifest bytes changed",
            ):
                fabric_slitherite_evidence.validate_archive_manifest_identity(
                    archive_root
                )


if __name__ == "__main__":
    unittest.main()
