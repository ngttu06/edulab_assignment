"""Compose configuration regression; never starts containers or connects to a DB."""
import json
import os
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]


class CloudComposeTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory(prefix="edulab-compose-test-")
        self.addCleanup(self.folder.cleanup)
        self.env_file = pathlib.Path(self.folder.name) / "fixture.env"
        # Deliberately stale local settings: cloud Compose must override them.
        self.env_file.write_text(
            "EDULAB_DB_URL=jdbc:mysql://localhost:3306/wrong_database\n"
            "EDULAB_DB_USERNAME=local_user\n"
            "EDULAB_DB_PASSWORD=fixture-only-password\n"
            "EDULAB_JWT_SECRET=fixture-only-jwt\n"
            "EDULAB_AI_ENCRYPTION_KEY=fixture-only-ai\n"
            "EDULAB_STORAGE_ENCRYPTION_KEY=fixture-only-storage\n"
            "SPRING_PROFILES_ACTIVE=local\n"
            "SPRING_FLYWAY_ENABLED=true\n"
            "EDULAB_COOKIE_SECURE=false\n"
            "EDULAB_DEV_SCAN_BYPASS=true\n"
        )
        self.env = {key: value for key, value in os.environ.items()
                    if not key.startswith(("EDULAB_", "COMPOSE_"))}
        self.env["EDULAB_ENV_FILE"] = str(self.env_file)

    def config(self, file="docker-compose.yml"):
        result = subprocess.run(
            ["docker", "compose", "-f", str(ROOT / file), "config", "--format", "json"],
            cwd=ROOT, env=self.env, check=True, capture_output=True, text=True,
        )
        return json.loads(result.stdout)

    def test_default_and_explicit_cloud_have_no_database_service(self):
        for file in ["docker-compose.yml", "compose.cloud.yml"]:
            with self.subTest(file=file):
                config = self.config(file)
                self.assertEqual(config["name"], "edulab")
                self.assertEqual(set(config["services"]), {"app", "migrate"})
                self.assertTrue(all(service["image"] == "edulab-khtn8:latest"
                                    for service in config["services"].values()))

    def test_cloud_overrides_local_datasource_and_insecure_settings(self):
        for name, service in self.config()["services"].items():
            with self.subTest(service=name):
                env = service["environment"]
                self.assertTrue(env["EDULAB_DB_URL"].startswith(
                    "jdbc:mysql://mysql.ngttu.com:3306/edulab_assignment?"))
                self.assertIn("sslMode=VERIFY_IDENTITY", env["EDULAB_DB_URL"])
                self.assertNotIn("allowPublicKeyRetrieval", env["EDULAB_DB_URL"])
                self.assertEqual(env["EDULAB_DB_USERNAME"], "ngttu")
                self.assertEqual(env["SPRING_PROFILES_ACTIVE"], "remote")
                self.assertEqual(env["SPRING_FLYWAY_ENABLED"], "false")
                self.assertEqual(env["EDULAB_COOKIE_SECURE"], "true")
                self.assertEqual(env["EDULAB_DEV_SCAN_BYPASS"], "false")

    def test_runtime_secrets_are_forwarded_from_env_file(self):
        env = self.config()["services"]["app"]["environment"]
        self.assertEqual(env["EDULAB_DB_PASSWORD"], "fixture-only-password")
        self.assertEqual(env["EDULAB_JWT_SECRET"], "fixture-only-jwt")
        self.assertEqual(env["EDULAB_AI_ENCRYPTION_KEY"], "fixture-only-ai")
        self.assertEqual(env["EDULAB_STORAGE_ENCRYPTION_KEY"], "fixture-only-storage")

    def test_named_container_port_and_migration_dependency(self):
        config = self.config()
        self.assertEqual(config["name"], "edulab")
        app = config["services"]["app"]
        self.assertEqual(app["container_name"], "edulab")
        self.assertEqual(app["ports"][0]["host_ip"], "127.0.0.1")
        self.assertEqual(str(app["ports"][0]["published"]), "8088")
        self.assertEqual(app["ports"][0]["target"], 8080)
        self.assertEqual(app["depends_on"]["migrate"]["condition"], "service_completed_successfully")
        self.assertEqual(config["services"]["migrate"]["command"], ["migrate"])

    def test_no_local_mysql_deployment_fallback(self):
        self.assertFalse((ROOT / "compose.local.yml").exists())
        self.assertFalse((ROOT / "run-local.sh").exists())
        for name in ("application.yml", "application-remote.yml"):
            source = (ROOT / "src/main/resources" / name).read_text()
            self.assertIn("url: ${EDULAB_DB_URL}", source)
            self.assertIn("username: ${EDULAB_DB_USERNAME}", source)
            self.assertIn("password: ${EDULAB_DB_PASSWORD}", source)
            self.assertNotIn("jdbc:mysql://localhost", source)


if __name__ == "__main__":
    unittest.main()