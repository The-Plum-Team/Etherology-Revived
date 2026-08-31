#!/usr/bin/env python3

import argparse
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


EXPECTED_VERSIONS = (
    "1.20.1",
    "1.21.1",
    "1.21.2",
    "1.21.3",
    "1.21.4",
    "1.21.5",
    "1.21.6",
    "1.21.7",
    "1.21.8",
    "1.21.9",
    "1.21.10",
    "1.21.11",
    "26.1",
    "26.1.1",
    "26.1.2",
    "26.2",
)

EXPECTED_1_20_1_SIGNATURE = (
    "forge-and-fabric-1.20.1",
    17,
    False,
    {
        "fabric": ("0.17.3", "0.92.6+1.20.1", "9.2.14"),
        "forge": ("1.20.1-47.4.9", "1.20.1-47.4.9", "9.2.14"),
    },
)


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        raise ValueError(f"JSON root must be an object: {path}")
    return value


def load_gradle_properties(path: Path) -> dict[str, str]:
    properties: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    return properties


def branch_policy(version: str) -> tuple[str, set[str], int, bool]:
    if version == "1.20.1":
        return "forge-and-fabric-1.20.1", {"fabric", "forge"}, 17, False
    if version.startswith("1.21."):
        return f"fabric-and-neoforge-{version}", {"fabric", "neoforge"}, 21, False
    return f"fabric-and-neoforge-{version}", {"fabric", "neoforge"}, 25, True


def loader_map(entry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    loaders = entry.get("loaders", [])
    if not isinstance(loaders, list):
        return {}
    return {
        loader["id"]: loader
        for loader in loaders
        if isinstance(loader, dict) and isinstance(loader.get("id"), str)
    }


def entry_signature(entry: dict[str, Any]) -> tuple[Any, ...]:
    pins = {
        loader_id: (
            loader.get("loader_version"),
            loader.get("api_version"),
            loader.get("architectury_api_version"),
        )
        for loader_id, loader in loader_map(entry).items()
    }
    return entry.get("branch"), entry.get("java"), entry.get("no_remap"), pins


def forbidden_pack_paths(value: Any, path: str = "catalog") -> list[str]:
    if isinstance(value, dict):
        findings: list[str] = []
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key in {"pack_format", "server_data_pack_format"}:
                findings.append(child_path)
            findings.extend(forbidden_pack_paths(child, child_path))
        return findings
    if isinstance(value, list):
        return [
            finding
            for index, child in enumerate(value)
            for finding in forbidden_pack_paths(child, f"{path}[{index}]")
        ]
    return []


def validate_catalog(catalog: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if catalog.get("schema_version") != 1:
        errors.append("support catalog schema_version must be 1")
    if catalog.get("architecture") != "branch-per-minecraft-version":
        errors.append("support catalog architecture must be branch-per-minecraft-version")
    pack_paths = forbidden_pack_paths(catalog)
    if pack_paths:
        errors.append(
            "support catalog must not contain pack-format values: " + ", ".join(pack_paths)
        )

    entries = catalog.get("branches")
    if not isinstance(entries, list):
        return errors + ["support catalog branches must be an array"]
    if len(entries) != len(EXPECTED_VERSIONS):
        errors.append(f"support catalog must contain {len(EXPECTED_VERSIONS)} branches")

    valid_entries = [entry for entry in entries if isinstance(entry, dict)]
    if len(valid_entries) != len(entries):
        errors.append("every support branch must be an object")
    branch_counts = Counter(entry.get("branch") for entry in valid_entries)
    version_counts = Counter(entry.get("minecraft_version") for entry in valid_entries)
    duplicates = sorted(str(branch) for branch, count in branch_counts.items() if count > 1)
    if duplicates:
        errors.append("duplicate support branches: " + ", ".join(duplicates))
    duplicates = sorted(str(version) for version, count in version_counts.items() if count > 1)
    if duplicates:
        errors.append("duplicate Minecraft versions: " + ", ".join(duplicates))

    versions = {version for version in version_counts if isinstance(version, str)}
    if versions != set(EXPECTED_VERSIONS):
        missing = sorted(set(EXPECTED_VERSIONS) - versions)
        extra = sorted(versions - set(EXPECTED_VERSIONS))
        errors.append(f"support version inventory mismatch: missing={missing}, extra={extra}")

    for entry in valid_entries:
        version = entry.get("minecraft_version")
        if not isinstance(version, str):
            errors.append("every support branch must declare a string minecraft_version")
            continue
        branch, loader_ids, java, no_remap = branch_policy(version)
        if entry.get("branch") != branch:
            errors.append(f"{version} must use branch {branch}")
        if entry.get("java") != java:
            errors.append(f"{branch} must target Java {java}")
        if entry.get("no_remap") is not no_remap:
            errors.append(f"{branch} has the wrong no_remap policy")

        loaders = entry.get("loaders")
        loader_by_id = loader_map(entry)
        if not isinstance(loaders, list) or len(loaders) != 2 or len(loader_by_id) != 2:
            errors.append(f"{branch} must contain exactly two unique loader entries")
        if set(loader_by_id) != loader_ids:
            errors.append(f"{branch} must use loaders {sorted(loader_ids)}")
        architectury_versions: set[str] = set()
        for loader_id, loader in loader_by_id.items():
            for key in ("loader_version", "api_version", "architectury_api_version"):
                if not isinstance(loader.get(key), str) or not loader[key]:
                    errors.append(f"{branch}/{loader_id}.{key} must be a non-empty string")
            if isinstance(loader.get("architectury_api_version"), str):
                architectury_versions.add(loader["architectury_api_version"])
            if loader_id in {"forge", "neoforge"} and (
                loader.get("api_version") != loader.get("loader_version")
            ):
                errors.append(f"{branch}/{loader_id} API and loader pins must match")
        if len(architectury_versions) != 1:
            errors.append(f"{branch} loaders must use one Architectury API version")

    entry_1_20_1 = next(
        (entry for entry in valid_entries if entry.get("minecraft_version") == "1.20.1"),
        None,
    )
    if entry_1_20_1 and entry_signature(entry_1_20_1) != EXPECTED_1_20_1_SIGNATURE:
        errors.append("1.20.1 support pins do not match the audited target pins")
    return errors


def validate_release(
    catalog: dict[str, Any],
    release_matrix: dict[str, Any],
    gradle_properties: dict[str, str],
) -> list[str]:
    errors: list[str] = []
    project = release_matrix.get("project", {})
    release_branch = project.get("release_branch") if isinstance(project, dict) else None
    entries = catalog.get("branches", [])
    entry = next(
        (
            item
            for item in entries
            if isinstance(item, dict) and item.get("branch") == release_branch
        ),
        None,
    )
    if not entry:
        return [f"current release branch {release_branch!r} is absent from the support catalog"]

    version = entry["minecraft_version"]
    catalog_loaders = loader_map(entry)
    artifacts = release_matrix.get("artifacts", [])
    runtimes = release_matrix.get("runtimes", [])
    artifact_by_loader = {
        artifact.get("loader"): artifact for artifact in artifacts if isinstance(artifact, dict)
    }
    runtime_by_loader = {
        runtime.get("loader"): runtime for runtime in runtimes if isinstance(runtime, dict)
    }
    if release_matrix.get("lane_count") != 2 or len(artifacts) != 2 or len(runtimes) != 2:
        errors.append("current release matrix must contain exactly two artifact/runtime lanes")
    if len(artifact_by_loader) != len(artifacts) or len(runtime_by_loader) != len(runtimes):
        errors.append("current release matrix loader lanes must be unique")
    if set(artifact_by_loader) != set(catalog_loaders):
        errors.append("current release artifact loaders do not match the support catalog")
    if set(runtime_by_loader) != set(catalog_loaders):
        errors.append("current release runtime loaders do not match the support catalog")

    for loader_id, loader in catalog_loaders.items():
        artifact = artifact_by_loader.get(loader_id, {})
        runtime = runtime_by_loader.get(loader_id, {})
        expected_artifact = (version, entry["java"], entry["no_remap"])
        actual_artifact = (
            artifact.get("artifact_version"),
            artifact.get("java"),
            artifact.get("no_remap"),
        )
        if actual_artifact != expected_artifact:
            errors.append(f"current {loader_id} artifact policy does not match the support catalog")
        expected_runtime = (version, entry["java"], loader["loader_version"])
        actual_runtime = (
            runtime.get("runtime_version"),
            runtime.get("java"),
            runtime.get("loader_version"),
        )
        if actual_runtime != expected_runtime:
            errors.append(f"current {loader_id} runtime policy does not match the support catalog")
        if loader_id == "fabric" and runtime.get("fabric_api") != loader["api_version"]:
            errors.append("current Fabric API pin does not match the support catalog")
        architectury = runtime.get("architectury", {})
        if architectury.get("version") != loader["architectury_api_version"]:
            errors.append(
                f"current {loader_id} Architectury pin does not match the support catalog"
            )

    suffix = version.replace(".", "_")
    fabric = catalog_loaders.get("fabric", {})
    expected_properties = {
        f"minecraft_version_{suffix}": version,
        f"java_version_{suffix}": str(entry["java"]),
        f"fabric_loader_version_{suffix}": str(fabric.get("loader_version")),
        f"fabric_api_version_{suffix}": str(fabric.get("api_version")),
        f"architectury_api_version_{suffix}": str(fabric.get("architectury_api_version")),
    }
    for loader_id in ("forge", "neoforge"):
        if loader_id in catalog_loaders:
            expected_properties[f"{loader_id}_version_{suffix}"] = catalog_loaders[loader_id][
                "loader_version"
            ]
    for key, expected in expected_properties.items():
        if gradle_properties.get(key) != expected:
            errors.append(f"Gradle property {key} must be {expected}")
    return errors


def validate(
    catalog: dict[str, Any],
    release_matrix: dict[str, Any],
    gradle_properties: dict[str, str],
) -> list[str]:
    return validate_catalog(catalog) + validate_release(catalog, release_matrix, gradle_properties)


def parse_args(argv: list[str]) -> argparse.Namespace:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description="Validate the Etherology support catalog.")
    parser.add_argument("--catalog", type=Path, default=root / "release/support-catalog.json")
    parser.add_argument("--release-matrix", type=Path, default=root / "release/release-matrix.json")
    parser.add_argument("--gradle-properties", type=Path, default=root / "gradle.properties")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    errors = validate(
        load_json(args.catalog),
        load_json(args.release_matrix),
        load_gradle_properties(args.gradle_properties),
    )
    if errors:
        print("Support catalog validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Validated {len(EXPECTED_VERSIONS)} support branches and the current release matrix.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
