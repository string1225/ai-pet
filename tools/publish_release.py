#!/usr/bin/env python3
"""Publish a verified APK using existing GitHub credentials; never print/persist tokens."""
import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = "string1225/ai-pet"


def apk_version(apk):
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or Path.home() / ("AppData/Local/Android/Sdk" if os.name == "nt" else "Android/Sdk"))
    candidates = sorted((sdk / "build-tools").glob("*/aapt2.exe" if os.name == "nt" else "*/aapt2"), reverse=True)
    if not candidates:
        raise SystemExit("Android build-tools aapt2 is required to verify APK identity")
    result = subprocess.run([str(candidates[0]), "dump", "badging", str(apk)], capture_output=True, text=True, timeout=30)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", result.stdout)
    if result.returncode or not match or match[1] != "com.string.aipet":
        raise SystemExit("APK does not have the expected package identity")
    if "application-debuggable" in result.stdout:
        raise SystemExit("Refusing to publish a debuggable APK")
    signer = candidates[0].with_name("apksigner.bat" if os.name == "nt" else "apksigner")
    signer_env = os.environ.copy()
    if os.name == "nt" and not signer_env.get("JAVA_HOME"):
        studio_jdk = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Android/Android Studio/jbr"
        if (studio_jdk / "bin/java.exe").exists():
            signer_env["JAVA_HOME"] = str(studio_jdk)
    signature = subprocess.run([str(signer), "verify", "--print-certs", str(apk)], env=signer_env, capture_output=True, text=True, timeout=30)
    expected = "05893ba88f9c0d9a744e9867f11ab46b5c2389a981a1ec316bcd030212080667"
    actual = re.findall(r"certificate SHA-256 digest: ([a-f0-9]+)", signature.stdout)
    if signature.returncode or actual != [expected]:
        raise SystemExit("APK signing certificate differs from the existing app")
    return int(match[2]), match[3]


def github_environment():
    env = os.environ.copy()
    if not (env.get("GH_TOKEN") or env.get("GITHUB_TOKEN")):
        options = dict(env, GIT_TERMINAL_PROMPT="0", GCM_INTERACTIVE="never")
        result = subprocess.run(["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n", text=True, capture_output=True, env=options, cwd=ROOT, timeout=20)
        fields = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
        token = fields.get("password")
        if result.returncode or not token:
            raise SystemExit("No cached GitHub credential. Set GH_TOKEN with Contents write permission.")
        env["GH_TOKEN"] = token
    return env


def gh(env, *args):
    result = subprocess.run(["gh", *args], env=env, cwd=ROOT, text=True, capture_output=True)
    if result.returncode:
        raise SystemExit("GitHub operation failed: " + result.stderr.strip())
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-auth", action="store_true")
    parser.add_argument("--tag")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--notes", type=Path)
    parser.add_argument("--plan-id", action="append", default=[], help="Feedback IDs implemented by this release")
    args = parser.parse_args()
    env = github_environment()
    if args.check_auth:
        print("GitHub credential ready for " + gh(env, "api", "user", "--jq", ".login"))
        return
    if not args.tag or not re.fullmatch(r"v\d+\.\d+\.\d+", args.tag) or not args.apk or not args.notes:
        raise SystemExit("Use --tag vX.Y.Z --apk SIGNED_APK --notes NOTES_FILE")
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    if subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip():
        raise SystemExit("Commit and push all intended changes before publishing.")
    remote = gh(env, "api", f"repos/{REPO}/git/ref/heads/main", "--jq", ".object.sha")
    if remote != sha:
        raise SystemExit("Remote main does not match local HEAD; push first.")
    apk = args.apk.resolve()
    if not apk.is_file() or apk.stat().st_size <= 0:
        raise SystemExit("APK missing or empty")
    version_code, version_name = apk_version(apk)
    if args.tag != "v" + version_name:
        raise SystemExit("Release tag and APK version differ")
    asset = ROOT / "artifacts" / "ai-pet.apk"
    asset.parent.mkdir(exist_ok=True)
    if apk != asset:
        import shutil
        shutil.copyfile(apk, asset)
    sums = asset.parent / "SHA256SUMS"
    sums.write_text(hashlib.sha256(asset.read_bytes()).hexdigest() + "  ai-pet.apk\n", encoding="utf-8")
    metadata = asset.parent / "update.json"
    metadata.write_text(json.dumps({"versionName": version_name, "versionCode": version_code,
        "apkUrl": f"https://github.com/{REPO}/releases/download/{args.tag}/ai-pet.apk",
        "sha256": hashlib.sha256(asset.read_bytes()).hexdigest(), "sourceCommit": sha,
        "planIds": sorted(set(args.plan_id + ([os.environ["AI_PET_FEEDBACK_ID"]] if os.environ.get("AI_PET_FEEDBACK_ID") else [])))}, indent=2) + "\n", encoding="utf-8")
    # A draft remains invisible to latest until both files are uploaded.
    gh(env, "release", "create", args.tag, str(asset), str(sums), str(metadata), "--repo", REPO, "--target", sha, "--title", "芽芽小屋 " + args.tag,
       "--notes-file", str(args.notes.resolve()), "--draft")
    gh(env, "release", "edit", args.tag, "--repo", REPO, "--draft=false", "--latest")
    print("https://github.com/" + REPO + "/releases/tag/" + args.tag)


if __name__ == "__main__":
    main()
