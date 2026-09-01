"""Freeze the Forge 1.20.1 Attrahite block report contract for profile v18."""

from __future__ import annotations

import copy

from forge_server_contract_v17 import *  # noqa: F403
import forge_server_contract_v17 as contract_v17


PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v18"
SCENARIO_ID = "attrahite-block-registry"
TASK_PATH = ":forge:1.20.1:runRegistryFoundationServerProbe"
PROFILE_MANIFEST_RELATIVE_PATH = "scripts/e2e/forge-server-1.20.1-profile.json"
PROFILE_SNAPSHOT_RELATIVE_PATH = (
    "scripts/e2e/forge-server-1.20.1-profile-v18.json"
)
PROFILE_MANIFEST_SIZE = 1204
PROFILE_MANIFEST_SHA256 = (
    "918a0af4b8794e07d0282e1913341abbad0908ba524714913267972c54481687"
)


class V18ContractError(RuntimeError):
    """Reports an exact profile-v18 report contract violation."""


def validate_probe_report(
    report: dict[str, object],
    required_mod_ids: object,
    forbidden_mod_ids: object,
) -> None:
    """Validates profile-v18 identity and the unchanged profile-v17 report body."""
    for name, expected in {
        "schema": REPORT_SCHEMA,  # noqa: F405
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
    }.items():
        if not exact_json_value(report.get(name), expected):  # noqa: F405
            raise V18ContractError(
                f"The server probe report {name} value changed"
            )

    baseline = copy.deepcopy(report)
    baseline["profile_id"] = contract_v17.PROFILE_ID
    try:
        contract_v17.validate_probe_report(
            baseline,
            required_mod_ids,
            forbidden_mod_ids,
        )
    except contract_v17.V17ContractError as exception:
        raise V18ContractError(str(exception)) from exception
