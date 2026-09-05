#!/usr/bin/env python3

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.datagen import verify_recipe_advancements as verifier


class RecipeAdvancementVerifierTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.output_root = Path(self.temporary_directory.name)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_dataset(self, referenced_recipe_id: str) -> None:
        recipe_path = self.output_root / "data/etherology/recipes/widget.json"
        recipe_path.parent.mkdir(parents=True)
        recipe_path.write_text("{}\n", encoding="utf-8")

        advancement_path = (
            self.output_root
            / "data/etherology/advancements/recipes/misc/widget.json"
        )
        advancement_path.parent.mkdir(parents=True)
        advancement_path.write_text(
            json.dumps(
                {
                    "criteria": {
                        "has_the_recipe": {
                            "conditions": {"recipe": referenced_recipe_id},
                            "trigger": "minecraft:recipe_unlocked",
                        }
                    },
                    "rewards": {"recipes": [referenced_recipe_id]},
                }
            ),
            encoding="utf-8",
        )

    def test_matching_recipe_reference_is_accepted(self) -> None:
        self.write_dataset("etherology:widget")

        result = verifier.verify(self.output_root)

        self.assertEqual(1, result.recipe_count)
        self.assertEqual(1, result.advancement_count)
        self.assertEqual(2, result.reference_count)
        self.assertEqual((), result.errors)

    def test_missing_recipe_reference_is_rejected(self) -> None:
        self.write_dataset("minecraft:widget")

        result = verifier.verify(self.output_root)

        self.assertEqual(2, len(result.errors))
        self.assertTrue(
            all("references missing recipe minecraft:widget" in error for error in result.errors)
        )

    def write_dependency(self, entry: str, contents: str) -> Path:
        path = self.output_root / "minecraft.jar"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(entry, contents)
        return path

    def test_reference_to_a_real_dependency_recipe_is_accepted(self) -> None:
        self.write_dataset("minecraft:widget")
        dependency = self.write_dependency(
            "data/minecraft/recipes/widget.json", '{"type":"minecraft:crafting_shaped"}'
        )

        result = verifier.verify(self.output_root, (dependency,))

        self.assertEqual(1, result.recipe_count)
        self.assertEqual(2, result.reference_count)
        self.assertEqual((), result.errors)

    def test_dependency_does_not_allow_arbitrary_vanilla_recipe_ids(self) -> None:
        self.write_dataset("minecraft:widget")
        dependency = self.write_dependency(
            "data/minecraft/recipes/another.json", '{"type":"minecraft:crafting_shaped"}'
        )

        result = verifier.verify(self.output_root, (dependency,))

        self.assertEqual(2, len(result.errors))
        self.assertTrue(all("minecraft:widget" in error for error in result.errors))

    def test_empty_or_invalid_recipe_jars_are_rejected(self) -> None:
        self.write_dataset("etherology:widget")
        for entry, contents in (
            ("data/minecraft/loot_tables/widget.json", "{}"),
            ("data/minecraft/recipes/widget.json", "{}"),
            ("data/minecraft/recipes/widget.json", "[]"),
            ("data/minecraft/recipes/widget.json", "not JSON"),
        ):
            with self.subTest(entry=entry, contents=contents):
                dependency = self.write_dependency(entry, contents)
                self.assertEqual(1, len(verifier.verify(self.output_root, (dependency,)).errors))

    def test_missing_dependency_is_rejected_even_for_generated_references(self) -> None:
        self.write_dataset("etherology:widget")

        result = verifier.verify(self.output_root, (self.output_root / "absent.jar",))

        self.assertEqual(1, len(result.errors))


if __name__ == "__main__":
    unittest.main()
