#!/usr/bin/env python3
"""Report one approved feedback run; the project-session credential is never used here."""
import argparse
import json
import os
import re
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


def payload(args):
    result = {"status": args.status}
    if args.message:
        result["message"] = args.message[:1000]
    if args.status == "published":
        required = (args.version_name, args.version_code, args.release_url, args.commit, args.sha256, args.test_summary)
        if not all(required) or not re.fullmatch(r"\d+\.\d+\.\d+", args.version_name) or not re.fullmatch(r"[a-fA-F0-9]{40}", args.commit) or not re.fullmatch(r"[a-fA-F0-9]{64}", args.sha256):
            raise ValueError("Published status requires version, code, release URL, full commit, APK SHA-256 and test summary")
        if args.release_url != f"https://github.com/string1225/ai-pet/releases/tag/v{args.version_name}":
            raise ValueError("Release must belong to string1225/ai-pet and match the version")
        result.update(versionName=args.version_name, versionCode=args.version_code, releaseUrl=args.release_url,
                      commit=args.commit.lower(), sha256=args.sha256.lower(), testSummary=args.test_summary[:4000])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--status", choices=["executing", "testing", "failed", "published"], required=True)
    parser.add_argument("--message", default="")
    parser.add_argument("--version-name")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--release-url")
    parser.add_argument("--commit")
    parser.add_argument("--sha256")
    parser.add_argument("--test-summary")
    args = parser.parse_args()
    base = urlparse(args.base_url)
    if base.scheme != "https" or base.username or base.password or base.query or base.fragment:
        raise SystemExit("Callback base URL must be HTTPS without credentials, query or fragment")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", args.run_id):
        raise SystemExit("Invalid run ID")
    token = os.environ.get("AI_PET_RUN_TOKEN", "")
    if not token:
        raise SystemExit("Set the run-scoped AI_PET_RUN_TOKEN environment variable")
    try:
        body = json.dumps(payload(args)).encode()
        request = Request(args.base_url.rstrip("/") + "/runs/" + args.run_id + "/events", data=body,
                          headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"}, method="POST")
        # Never print server response bodies: they can contain operational context.
        with urlopen(request, timeout=30) as response:
            if response.status not in (200, 201, 202):
                raise SystemExit("Callback was not accepted")
        print("Feedback run status accepted: " + args.status)
    except HTTPError as error:
        raise SystemExit(f"Callback returned HTTP {error.code}; retain evidence and retry this callback only") from None
    except (URLError, TimeoutError):
        raise SystemExit("Callback delivery uncertain; retain evidence and retry this callback only") from None
    except ValueError as error:
        raise SystemExit(str(error)) from None


if __name__ == "__main__":
    main()
