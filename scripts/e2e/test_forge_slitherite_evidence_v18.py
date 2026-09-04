from __future__ import annotations

from pathlib import Path
import sys
import unittest


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
if str(SCRIPT_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIRECTORY))

import forge_slitherite_evidence_v18 as forge_slitherite_evidence
import slitherite_client_evidence_test_support_v1 as support


class ForgeSlitheriteEvidenceV18Tests(
    support.SlitheriteClientEvidenceContractTests
):
    verifier = forge_slitherite_evidence
    historical_profile_relative_path = (
        "scripts/e2e/forge-1.20.1-profile-v17.json"
    )
    historical_profile_sha256 = (
        "00475fd4af5741119b44b3ca70484e967ee0b7a8c51fdc222ebdde3e2bf0ba58"
    )
    historical_verifier_relative_path = (
        "scripts/e2e/forge_attrahite_evidence_v17.py"
    )
    historical_verifier_sha256 = (
        "8547b4a4a2b783c8a145ec717b8abc6d61aea7440b740056ba60c066159e7ebc"
    )


if __name__ == "__main__":
    unittest.main()
