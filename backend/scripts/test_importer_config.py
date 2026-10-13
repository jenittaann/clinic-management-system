import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import import_hospitals


class ImporterDatabaseConfigTest(unittest.TestCase):
    def test_requires_explicit_nonblank_credentials_without_echoing_values(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "DB_URL, DB_USERNAME, DB_PASSWORD") as raised:
                import_hospitals.database_config()
        self.assertNotIn("test-only-secret", str(raised.exception))

        with patch.dict(os.environ, {
            "DB_URL": "jdbc:mysql://localhost:3306/camillus",
            "DB_USERNAME": "local-user",
            "DB_PASSWORD": "   ",
        }, clear=True):
            with self.assertRaisesRegex(ValueError, "DB_PASSWORD"):
                import_hospitals.database_config()

    def test_requires_target_to_remain_camillus(self):
        with patch.dict(os.environ, {
            "DB_URL": "jdbc:mysql://localhost:3306/clinic_management_db",
            "DB_USERNAME": "local-user",
            "DB_PASSWORD": "test-only-secret",
        }, clear=True):
            with self.assertRaisesRegex(ValueError, "exactly 'camillus'"):
                import_hospitals.database_config()

    def test_resolves_explicit_camillus_settings_without_connecting(self):
        with patch.dict(os.environ, {
            "DB_URL": "jdbc:mysql://127.0.0.1:3307/camillus?serverTimezone=UTC",
            "DB_USERNAME": "local-user",
            "DB_PASSWORD": "test-only-secret",
        }, clear=True):
            config = import_hospitals.database_config()

        self.assertEqual("127.0.0.1", config["host"])
        self.assertEqual(3307, config["port"])
        self.assertEqual("camillus", config["database"])
        self.assertEqual("local-user", config["user"])
        self.assertEqual("test-only-secret", config["password"])


if __name__ == "__main__":
    unittest.main()
