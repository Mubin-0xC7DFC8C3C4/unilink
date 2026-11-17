#!/usr/bin/env python3
"""Recreate local MySQL dev/test state after a VM wipe.

The MySQL datadir lives under /tmp (ephemeral), so a VM replacement wipes
the unilink_dev / unilink_test databases and the unilink_app / unilink_mig
users. This script recreates them from the defaults in the Spring config
files (application.yml, application-test.yml).

It never prints credential values: it only reports whether the dev and test
passwords for unilink_mig agree, and whether each step succeeded.
Run as root (mysql root has an empty password on this box):
    python3 ops/recreate-dev-db.py
"""
import re
import subprocess
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parent.parent / "backend" / "src" / "main" / "resources"


def placeholder_default(text: str, name: str) -> str | None:
    m = re.search(r"\$\{" + re.escape(name) + r":([^}]*)\}", text)
    return m.group(1) if m else None


def main() -> int:
    main_yml = (BACKEND / "application.yml").read_text()
    test_yml = (BACKEND / "application-test.yml").read_text()

    app_user = placeholder_default(main_yml, "DB_USER") or "unilink_app"
    app_pw = placeholder_default(main_yml, "DB_PASSWORD")
    mig_user = placeholder_default(main_yml, "DB_MIGRATION_USER") or "unilink_mig"
    mig_pw = placeholder_default(main_yml, "DB_MIGRATION_PASSWORD")
    test_mig_pw = placeholder_default(test_yml, "DB_PASSWORD")
    test_mig_user = placeholder_default(test_yml, "DB_USER") or mig_user

    if not app_pw or not mig_pw or not test_mig_pw:
        print("ERROR: could not read a DB password default from the yml files")
        return 1
    if test_mig_user != mig_user:
        print(f"NOTE: test DB user ({test_mig_user}) differs from migration user ({mig_user})")
    print(f"dev/test passwords for {mig_user} agree: {mig_pw == test_mig_pw}")
    if mig_pw != test_mig_pw:
        print("ERROR: dev and test configs disagree on the migration password; "
              "refusing to guess. Fix the yml files first.")
        return 1

    sql = "\n".join([
        "CREATE DATABASE IF NOT EXISTS unilink_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
        "CREATE DATABASE IF NOT EXISTS unilink_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
        f"CREATE USER IF NOT EXISTS '{app_user}'@'localhost' IDENTIFIED BY '{app_pw}';",
        f"CREATE USER IF NOT EXISTS '{app_user}'@'127.0.0.1' IDENTIFIED BY '{app_pw}';",
        f"CREATE USER IF NOT EXISTS '{mig_user}'@'localhost' IDENTIFIED BY '{mig_pw}';",
        f"ALTER USER '{app_user}'@'localhost' IDENTIFIED BY '{app_pw}';",
        f"ALTER USER '{app_user}'@'127.0.0.1' IDENTIFIED BY '{app_pw}';",
        f"ALTER USER '{mig_user}'@'localhost' IDENTIFIED BY '{mig_pw}';",
        f"GRANT ALL PRIVILEGES ON unilink_dev.* TO '{app_user}'@'localhost';",
        f"GRANT ALL PRIVILEGES ON unilink_dev.* TO '{app_user}'@'127.0.0.1';",
        f"GRANT ALL PRIVILEGES ON unilink_dev.* TO '{mig_user}'@'localhost';",
        f"GRANT ALL PRIVILEGES ON unilink_test.* TO '{mig_user}'@'localhost';",
        "FLUSH PRIVILEGES;",
    ])
    r = subprocess.run(["mysql", "-u", "root"], input=sql, text=True,
                       capture_output=True)
    if r.returncode != 0:
        print("ERROR: mysql failed:")
        print(r.stderr)
        return 1
    print("OK: databases unilink_dev, unilink_test and users recreated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
