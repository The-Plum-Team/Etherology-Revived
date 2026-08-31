#!/usr/bin/env python3

import json
import tempfile
import unittest
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


if __name__ == "__main__":
    unittest.main()
