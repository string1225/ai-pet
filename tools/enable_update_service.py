#!/usr/bin/env python3
"""Run with sudo on 139 to enable ai-pet in the existing shared Release service."""
import json
import shlex
import shutil
import subprocess
import time
from pathlib import Path
from urllib.request import urlopen


def enable():
    service = "github-release.service"
    current = subprocess.check_output(["systemctl", "show", service, "--property=Environment", "--value"], text=True)
    fields = dict(item.split("=", 1) for item in shlex.split(current) if "=" in item)
    repositories = set(filter(None, fields.get("GITHUB_RELEASE_REPOSITORIES", "").split(",")))
    if not repositories:
        raise SystemExit("Existing repository allowlist is missing; no service changes made")
    repositories.add("string1225/ai-pet")
    dropin = Path("/etc/systemd/system/github-release.service.d/40-ai-pet.conf")
    previous = dropin.read_bytes() if dropin.exists() else None
    content = '[Service]\nEnvironment="GITHUB_RELEASE_REPOSITORIES=' + ",".join(sorted(repositories)) + '"\n'
    if previous == content.encode():
        print("ai-pet is already enabled")
        return
    backup = Path("/opt/github-release/backups") / ("ai-pet-" + time.strftime("%Y%m%dT%H%M%SZ", time.gmtime()))
    backup.mkdir(parents=True, exist_ok=True)
    if previous is not None:
        shutil.copyfile(dropin, backup / dropin.name)
    dropin.parent.mkdir(parents=True, exist_ok=True)
    dropin.write_text(content)
    try:
        subprocess.run(["systemctl", "daemon-reload"], check=True)
        subprocess.run(["systemctl", "restart", service], check=True)
        for attempt in range(10):
            try:
                with urlopen("http://127.0.0.1:3043/github-release/health", timeout=3) as response:
                    if json.load(response).get("status") != "ok":
                        raise RuntimeError("Release health check failed")
                break
            except OSError:
                if attempt == 9:
                    raise
                time.sleep(.5)
    except Exception:
        if previous is None:
            dropin.unlink()
        else:
            dropin.write_bytes(previous)
        subprocess.run(["systemctl", "daemon-reload"], check=True)
        subprocess.run(["systemctl", "restart", service], check=True)
        raise
    print("Enabled repositories: " + ", ".join(sorted(repositories)))
    print("Existing nginx, proxy, cache and other repositories preserved")


if __name__ == "__main__":
    enable()
