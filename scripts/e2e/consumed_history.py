"""Validate immutable files retained from consumed repository-owned E2E runs."""

from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Mapping


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_files(
    repository_root: Path,
    *,
    label: str,
    state_relative_path: Path,
    runtime_relative_path: Path,
    history: Mapping[Path, tuple[int, str]],
) -> tuple[Path, Path] | None:
    """Checks an optional consumed-run inventory without following links."""

    state_root = repository_root / state_relative_path
    runtime = repository_root / runtime_relative_path
    controller_logs = state_root / "logs"
    runtime_parent = state_root / "runtimes"
    resolved = {
        repository_root / relative_path: contract
        for relative_path, contract in history.items()
    }

    for artifact in resolved:
        if state_root not in artifact.parents:
            raise AssertionError(
                f"The consumed {label} artifact escapes its state root: {artifact}"
            )
    for directory in (state_root, runtime_parent, controller_logs):
        if directory.is_symlink():
            raise AssertionError(
                f"The consumed {label} parent is linked: {directory}"
            )
        if directory.exists() and not directory.is_dir():
            raise AssertionError(
                f"The consumed {label} parent is not a directory: {directory}"
            )

    present = tuple(path.exists() or path.is_symlink() for path in resolved)
    runtime_present = runtime.exists() or runtime.is_symlink()
    if not runtime_present and not any(present):
        return None

    intermediate_directories = {state_root, runtime_parent, runtime, controller_logs}
    history_boundary = state_root.parent
    for artifact in resolved:
        directory = artifact.parent
        while directory != history_boundary:
            intermediate_directories.add(directory)
            directory = directory.parent
    for directory in sorted(
        intermediate_directories,
        key=lambda candidate: (len(candidate.parts), str(candidate)),
    ):
        if not directory.is_dir():
            raise AssertionError(
                f"The consumed {label} parent is missing: {directory}"
            )
        if directory.is_symlink():
            raise AssertionError(
                f"The consumed {label} parent is linked: {directory}"
            )

    if not all(present):
        raise AssertionError(f"The consumed {label} is partial")
    for path, (expected_size, expected_sha256) in resolved.items():
        if not path.is_file() or path.is_symlink():
            raise AssertionError(
                f"The consumed {label} artifact is missing or linked: {path}"
            )
        if path.stat().st_size != expected_size:
            raise AssertionError(
                f"The consumed {label} artifact size changed: {path}"
            )
        if _sha256_file(path) != expected_sha256:
            raise AssertionError(
                f"The consumed {label} artifact bytes changed: {path}"
            )
    return state_root, runtime
