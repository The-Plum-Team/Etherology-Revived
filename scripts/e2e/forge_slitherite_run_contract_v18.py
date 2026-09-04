#!/usr/bin/env python3
"""Fail-closed controller contract for the one-shot Forge Slitherite v18 run."""

from __future__ import annotations

import re


PROFILE_ID = "etherology-e2e-forge-1.20.1-v18"
SCENARIO_ID = "slitherite-block-registry"
# Final remapped harness accepted by the one-shot stage/check/start boundary.
HARNESS_SIZE: int | None = 350530
HARNESS_SHA256: str | None = "ed9e4a068a3d3bbf0fed3a1bad856f1b6b1c5a033d2250f7badc4f124ca883e8"


class RunContractError(ValueError):
    """Reports an unsafe or incomplete one-shot Slitherite run contract."""


def require_explicit_scenario(configured_scenario_id: str | None) -> str:
    """Requires callers to opt in to the sole scenario authorized for v18."""

    if configured_scenario_id != SCENARIO_ID:
        raise RunContractError(
            "Forge Slitherite v18 check/start must explicitly select "
            f"{SCENARIO_ID!r}"
        )
    return SCENARIO_ID


def require_harness_pin() -> tuple[int, str]:
    """Returns the final harness pin or refuses staging and native preflight."""

    size = HARNESS_SIZE
    digest = HARNESS_SHA256
    if size is None and digest is None:
        raise RunContractError(
            "Build and pin the final Forge Slitherite v18 harness before "
            "stage/check/start"
        )
    if size is None or digest is None:
        raise RunContractError(
            "Forge Slitherite v18 HARNESS_SIZE and HARNESS_SHA256 must be set together"
        )
    if type(size) is not int or size <= 0:
        raise RunContractError(
            "Forge Slitherite v18 HARNESS_SIZE must be one positive integer"
        )
    if not isinstance(digest, str) or re.fullmatch(r"[0-9a-f]{64}", digest) is None:
        raise RunContractError(
            "Forge Slitherite v18 HARNESS_SHA256 must be 64 lowercase hexadecimal characters"
        )
    return size, digest
