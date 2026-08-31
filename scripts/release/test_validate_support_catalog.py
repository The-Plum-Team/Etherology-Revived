#!/usr/bin/env python3

import copy
import unittest
from pathlib import Path

from scripts.release import validate_support_catalog as validator


class SupportCatalogValidatorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        repository_root = Path(__file__).resolve().parents[2]
        cls.catalog = validator.load_json(repository_root / "release" / "support-catalog.json")
        cls.release_matrix = validator.load_json(
            repository_root / "release" / "release-matrix.json"
        )
        cls.gradle_properties = validator.load_gradle_properties(
            repository_root / "gradle.properties"
        )

    def validate(self, catalog: dict) -> list[str]:
        return validator.validate(catalog, self.release_matrix, self.gradle_properties)

    def test_repository_catalog_is_valid(self) -> None:
        self.assertEqual([], self.validate(self.catalog))

    def test_duplicate_branch_is_rejected(self) -> None:
        catalog = copy.deepcopy(self.catalog)
        catalog["branches"].append(copy.deepcopy(catalog["branches"][0]))

        errors = self.validate(catalog)

        self.assertTrue(any("duplicate support branches" in error for error in errors))
        self.assertTrue(any("duplicate Minecraft versions" in error for error in errors))

    def test_wrong_era_loader_pair_is_rejected(self) -> None:
        catalog = copy.deepcopy(self.catalog)
        entry = next(
            item for item in catalog["branches"] if item["minecraft_version"] == "1.21.1"
        )
        entry["loaders"][1]["id"] = "forge"

        errors = self.validate(catalog)

        self.assertTrue(any("must use loaders" in error for error in errors))

    def test_current_fabric_pin_drift_is_rejected(self) -> None:
        catalog = copy.deepcopy(self.catalog)
        entry = next(
            item for item in catalog["branches"] if item["minecraft_version"] == "1.20.1"
        )
        entry["loaders"][0]["api_version"] = "invalid"

        errors = self.validate(catalog)

        self.assertIn("1.20.1 support pins do not match the audited target pins", errors)
        self.assertIn("current Fabric API pin does not match the support catalog", errors)

    def test_pack_format_values_are_rejected(self) -> None:
        catalog = copy.deepcopy(self.catalog)
        catalog["pack_format"] = 15

        errors = self.validate(catalog)

        self.assertTrue(any("must not contain pack-format values" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
