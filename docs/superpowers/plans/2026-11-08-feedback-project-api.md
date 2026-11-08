# Feedback Project API Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans task by task. The provided external project API is used for future approved feedback, not for implementing this APK.

**Goal:** Complete the single-family feedback/plan/approve/execute/release/update loop while retaining local pet saves.

**Architecture:** Native Android feedback screens call a small authenticated service on 139. The service plans with read-only Codex and dispatches approved revisions to the configured external project-session API, with authenticated progress callbacks and release validation.

**Tech Stack:** Android Java, Python standard library HTTP/SQLite, Codex CLI, existing Gradle/GitHub release tools, nginx/systemd.

## Task 1: Service and protocol

Files: backend/feedback_server.py, backend/tests/test_service.py, backend/tests/test_integrations.py, backend/README.md, backend/ai-pet-feedback.service.

- [x] Write tests for the documented HTTP contract using a temporary SQLite database, loopback server and injected planner/dispatcher/release verifier. Example invariant: two approvals of the same revision result in exactly one dispatch call; wrong revision returns 409.
- [x] Run `python -m unittest discover -s backend/tests -p 'test_*.py'` and confirm missing behavior fails.
- [x] Implement durable ownership, pair/device/run credentials, plan revision checks, one worker, no automatic ambiguous-dispatch retries, scoped callbacks, release verification and bounded errors according to the spec.
- [x] Run the same command; test no real Remote Agent tasks are submitted.

## Task 2: APK feedback and callback tooling

Files: app/src/main/java/com/string/aipet/FeedbackClient.java, FeedbackController.java, MainActivity.java; tools/report_feedback.py; app/src/androidTest/java/com/string/aipet/FeedbackInstrumentation.java.

- [x] Add a local HTTP fixture test for submit/detail/revise/approve and parent protection, using only debug loopback networking.
- [x] Implement the documented JSON contract with bounded HTTP reads, HTTPS production base, parent gating on every sensitive action, persistent request IDs for submit retries, busy/closed activity handling and install receipts.
- [x] Wire the entry under Settings and synchronize receipts only for paired devices on foreground entry. Preserve pet and family stores.
- [x] Add callback CLI accepting only run-scoped credentials and bounded status/evidence fields.

## Task 3: Update/download and publication

Files: ReleasePolicy.java, ManualUpdater.java, UpdateController.java, ReleasePolicyTest.java, app/build.gradle.kts, tools/build.ps1, tools/publish_release.py.

- [x] Add a failing JVM case requiring `https://gh-proxy.com/https://github.com/string1225/ai-pet/releases/download/v0.4.0/ai-pet.apk` to be allowed while foreign repositories, userinfo, ports, HTTP and traversal are rejected.
- [x] Implement a fixed-repository proxy URL plus existing mirror/origin fallbacks and preserve all APK checks.
- [x] Add parent opt-in foreground automatic checks/download; throttle requests and never open installer automatically. Keep ready APK metadata when changing preferences.
- [x] Publish update.json alongside fixed APK and SHA256SUMS. Set version 0.4.0 with monotonically higher code than current/published versions.

## Task 4: Integration, deployment and delivery

- [x] Run backend/JVM/lint/release builds and review spec compliance then implementation quality. Fix actionable issues.
- [x] Deploy service to 139 with restricted private configuration, only a loopback listen address, existing HTTPS nginx route, backups and health verification.
- [x] Use a disposable Android emulator for feedback fixture flow, updater opt-in and compatibility. No production feedback is approved in tests.
- [x] Update README/privacy/release notes, inspect git diff/status, commit only intended files and push. Publish the verified signed APK from a clean pushed main revision, verify mirror/gh-proxy assets, and report exact results and any device-only limits.
