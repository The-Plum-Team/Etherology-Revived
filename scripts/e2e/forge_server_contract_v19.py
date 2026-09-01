"""Freeze the Forge 1.20.1 Attrahite block report contract for profile v19."""

from __future__ import annotations

import copy

from forge_server_contract_v18 import *  # noqa: F403
import forge_server_contract_v18 as contract_v18


PROFILE_ID = "etherology-e2e-forge-server-1.20.1-v19"
SCENARIO_ID = "attrahite-block-registry"
TASK_PATH = ":forge:1.20.1:runRegistryFoundationServerProbe"
PROFILE_MANIFEST_RELATIVE_PATH = "scripts/e2e/forge-server-1.20.1-profile.json"
PROFILE_SNAPSHOT_RELATIVE_PATH = (
    "scripts/e2e/forge-server-1.20.1-profile-v19.json"
)
PROFILE_MANIFEST_SIZE = 1204
PROFILE_MANIFEST_SHA256 = (
    "626cd5354057da6afe426d88de6849f6daef1a95a56ad3e5e4bb7afad2ceceec"
)


class V19ContractError(RuntimeError):
    """Reports an exact profile-v19 report contract violation."""


def validate_probe_report(
    report: dict[str, object],
    required_mod_ids: object,
    forbidden_mod_ids: object,
) -> None:
    """Validates profile-v19 identity and the unchanged profile-v18 report body."""
    for name, expected in {
        "schema": REPORT_SCHEMA,  # noqa: F405
        "profile_id": PROFILE_ID,
        "scenario": SCENARIO_ID,
    }.items():
        if not exact_json_value(report.get(name), expected):  # noqa: F405
            raise V19ContractError(
                f"The server probe report {name} value changed"
            )

    baseline = copy.deepcopy(report)
    baseline["profile_id"] = contract_v18.PROFILE_ID
    try:
        contract_v18.validate_probe_report(
            baseline,
            required_mod_ids,
            forbidden_mod_ids,
        )
    except contract_v18.V18ContractError as exception:
        raise V19ContractError(str(exception)) from exception
