#!/usr/bin/env python3

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class VerificationResult:
    recipe_count: int
    advancement_count: int
    reference_count: int
    errors: tuple[str, ...]


def load_json_object(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        raise ValueError(f"JSON root must be an object: {path}")
    return value


def recipe_id(data_root: Path, path: Path) -> str:
    relative_path = path.relative_to(data_root)
    namespace = relative_path.parts[0]
    recipe_path = Path(*relative_path.parts[2:]).with_suffix("").as_posix()
    return f"{namespace}:{recipe_path}"


def criterion_recipe_ids(advancement: dict[str, Any]) -> list[str]:
    criteria = advancement.get("criteria", {})
    if not isinstance(criteria, dict):
        return []

    recipe_ids: list[str] = []
    for criterion in criteria.values():
        if not isinstance(criterion, dict):
            continue
        if criterion.get("trigger") != "minecraft:recipe_unlocked":
            continue
        conditions = criterion.get("conditions", {})
        if not isinstance(conditions, dict):
            continue
        value = conditions.get("recipe")
        if isinstance(value, str):
            recipe_ids.append(value)
    return recipe_ids


def reward_recipe_ids(advancement: dict[str, Any]) -> list[str]:
    rewards = advancement.get("rewards", {})
    if not isinstance(rewards, dict):
        return []
    values = rewards.get("recipes", [])
    if not isinstance(values, list):
        return []
    return [value for value in values if isinstance(value, str)]


def verify(output_root: Path) -> VerificationResult:
    data_root = output_root / "data"
    recipe_paths = sorted(data_root.glob("*/recipes/**/*.json"))
    advancement_paths = sorted(data_root.glob("*/advancements/recipes/**/*.json"))
    recipe_ids = {recipe_id(data_root, path) for path in recipe_paths}
    errors: list[str] = []
    reference_count = 0

    if not recipe_paths:
        errors.append(f"no generated recipes found below {output_root}")
    if not advancement_paths:
        errors.append(f"no generated recipe advancements found below {output_root}")

    for path in advancement_paths:
        relative_path = path.relative_to(output_root)
        try:
            advancement = load_json_object(path)
        except (json.JSONDecodeError, OSError, ValueError) as error:
            errors.append(f"{relative_path}: {error}")
            continue

        criterion_ids = criterion_recipe_ids(advancement)
        reward_ids = reward_recipe_ids(advancement)
        reference_count += len(criterion_ids) + len(reward_ids)
        if not criterion_ids:
            errors.append(f"{relative_path}: missing recipe-unlocked criterion")
        if not reward_ids:
            errors.append(f"{relative_path}: missing recipe reward")
        if set(criterion_ids) != set(reward_ids):
            errors.append(
                f"{relative_path}: criterion recipes {criterion_ids} do not match rewards {reward_ids}"
            )

        for referenced_recipe_id in criterion_ids + reward_ids:
            if referenced_recipe_id not in recipe_ids:
                errors.append(
                    f"{relative_path}: references missing recipe {referenced_recipe_id}"
                )

    return VerificationResult(
        recipe_count=len(recipe_paths),
        advancement_count=len(advancement_paths),
        reference_count=reference_count,
        errors=tuple(errors),
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify that generated recipe advancements reference generated recipe IDs."
    )
    parser.add_argument("output", type=Path, help="Fabric datagen output root")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = verify(args.output)
    print(
        f"recipes={result.recipe_count} advancements={result.advancement_count} "
        f"references={result.reference_count} errors={len(result.errors)}"
    )
    for error in result.errors:
        print(error, file=sys.stderr)
    return 1 if result.errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
